package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.Inject;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/// Compiles Java sources in memory with [InjectProcessor] attached.
///
/// The classpath is reduced to the location of the core annotations, which keeps each test
/// independent from the Surefire booter classpath. Generated classes are collected in memory
/// and loaded by a child class loader.
final class TestCompiler {

    /// Outcome of one compilation.
    static final class Result {

        private final boolean success;
        private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
        private final Map<String, byte[]> classes;
        private final MemoryClassLoader loader;

        Result(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics,
               Map<String, byte[]> classes) {
            this.success = success;
            this.diagnostics = diagnostics;
            this.classes = classes;
            this.loader = new MemoryClassLoader(classes, parentLoader());
        }

        boolean success() {
            return success;
        }

        /// Compiled classes keyed by binary name.
        Map<String, byte[]> classes() {
            return classes;
        }

        /// Concatenated error diagnostics, used in assertion messages.
        String errors() {
            return diagnostics.stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                    .collect(Collectors.joining("\n"));
        }

        /// Loads a compiled class, failing the lookup with a clear message.
        ///
        /// All lookups share one class loader so static state and generated types stay
        /// consistent across assertions.
        Class<?> load(String className) {
            try {
                return Class.forName(className, true, loader);
            } catch (ClassNotFoundException exception) {
                throw new AssertionError("class " + className + " was not generated; errors:\n" + errors(),
                        exception);
            }
        }

        /// Instantiates `className` through its public no-argument constructor.
        Object instantiate(String className) {
            try {
                return load(className).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("cannot instantiate " + className, exception);
            }
        }
    }

    private TestCompiler() {
    }

    /// Compiles one source file.
    static Result compile(String className, String source) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(className, source);
        return compile(sources);
    }

    /// Compiles several source files, keyed by fully qualified class name.
    static Result compile(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null,
                StandardCharsets.UTF_8);
        InMemoryFileManager fileManager = new InMemoryFileManager(standard);
        List<JavaFileObject> units = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            units.add(new SourceFile(source.getKey(), source.getValue()));
        }
        List<String> options = List.of("-classpath", coreClasspath(), "-proc:full");
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null,
                units);
        task.setProcessors(List.of(new InjectProcessor()));
        boolean success;
        try {
            success = task.call();
        } finally {
            close(fileManager);
        }
        return new Result(success, diagnostics.getDiagnostics(), Map.copyOf(fileManager.classes));
    }

    /// Invokes a public method, unwrapping reflective exceptions.
    static Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("method " + method + " threw", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("method " + method + " is not available", exception);
        }
    }

    /// Reads a public field, unwrapping reflective exceptions.
    static Object field(Object target, String name) {
        try {
            return target.getClass().getField(name).get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("field " + name + " is not available", exception);
        }
    }

    private static String coreClasspath() {
        try {
            return Path.of(Inject.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception exception) {
            throw new IllegalStateException("cannot locate the core classes", exception);
        }
    }

    private static ClassLoader parentLoader() {
        return TestCompiler.class.getClassLoader();
    }

    private static void close(InMemoryFileManager fileManager) {
        try {
            fileManager.close();
        } catch (IOException _) {
            // closing an in-memory file manager cannot fail in a way that affects the test
        }
    }

    private static final class SourceFile extends SimpleJavaFileObject {

        private final String content;

        SourceFile(String className, String content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    private static final class InMemoryFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Map<String, byte[]> classes = new LinkedHashMap<>();

        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            URI uri = URI.create("mem:///" + className.replace('.', '/') + kind.extension);
            return new SimpleJavaFileObject(uri, kind) {
                private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                @Override
                public OutputStream openOutputStream() {
                    return new java.io.FilterOutputStream(buffer) {
                        @Override
                        public void close() throws IOException {
                            flush();
                            super.close();
                            classes.put(className, buffer.toByteArray());
                        }
                    };
                }

                @Override
                public java.io.Writer openWriter() throws IOException {
                    return new java.io.OutputStreamWriter(openOutputStream(), StandardCharsets.UTF_8);
                }

                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return buffer.toString(StandardCharsets.UTF_8);
                }
            };
        }
    }

    private static final class MemoryClassLoader extends ClassLoader {

        private final Map<String, byte[]> classes;

        MemoryClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
            super(parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
