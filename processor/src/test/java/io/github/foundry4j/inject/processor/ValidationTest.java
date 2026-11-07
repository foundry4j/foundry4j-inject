package io.github.foundry4j.inject.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {

    private static final String EMPTY_COMPONENT = """
            package com.example;
            
            import io.github.foundry4j.inject.Component;
            
            @Component
            public interface App {
            }
            """;

    @Test
    void reportsMissingBinding() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    Service service();
                }
                """);
        sources.put("com.example.Service", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class Service {
                    @Inject
                    public Service(Repository repository) {
                    }
                }
                """);
        sources.put("com.example.Repository", """
                package com.example;
                
                public interface Repository {
                }
                """);
        failsWith("no binding for com.example.Repository", sources);
    }

    @Test
    void reportsDuplicateBinding() {
        Map<String, String> sources = componentReturningString();
        sources.put("com.example.ModuleWithDuplicates", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class ModuleWithDuplicates {
                    @Provides
                    public static String first() {
                        return "first";
                    }
                
                    @Provides
                    public static String second() {
                        return "second";
                    }
                }
                """);
        failsWith("duplicate binding for java.lang.String", sources);
    }

    @Test
    void reportsCircularDependency() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    A a();
                }
                """);
        sources.put("com.example.A", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class A {
                    @Inject
                    public A(B b) {
                    }
                }
                """);
        sources.put("com.example.B", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class B {
                    @Inject
                    public B(A a) {
                    }
                }
                """);
        failsWith("circular dependency", sources);
    }

    @Test
    void rejectsMultipleInjectConstructors() {
        failsWith("multiple @Inject constructors", "com.example.A", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class A {
                    @Inject
                    public A() {
                    }
                
                    @Inject
                    public A(String value) {
                    }
                }
                """);
    }

    @Test
    void rejectsAbstractComponent() {
        failsWith("must not be abstract", "com.example.A", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public abstract class A {
                    @Inject
                    protected A() {
                    }
                }
                """);
    }

    @Test
    void rejectsGenericComponent() {
        failsWith("must not declare type parameters in compile-time injection", "com.example.Box", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class Box<T> {
                    @Inject
                    public Box() {
                    }
                }
                """);
    }

    @Test
    void rejectsPrivateInjectConstructor() {
        failsWith("must not be private", "com.example.A", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class A {
                    @Inject
                    private A() {
                    }
                }
                """);
    }

    @Test
    void rejectsInnerComponent() {
        failsWith("must be a top-level or static nested type", "com.example.Outer", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class Outer {
                    public final class Inner {
                        @Inject
                        public Inner() {
                        }
                    }
                }
                """);
    }

    @Test
    void rejectsEnumComponent() {
        failsWith("@Inject is only supported on constructors of classes and records", "com.example.E", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                
                public enum E {
                    VALUE;
                
                    @Inject
                    E() {
                    }
                }
                """);
    }

    @Test
    void rejectsNestedComponentInterface() {
        failsWith("must be a top-level interface", "com.example.Outer", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                public final class Outer {
                    @Component
                    public interface App {
                    }
                }
                """);
    }

    @Test
    void rejectsGenericComponentInterface() {
        failsWith("component com.example.App must not declare type parameters", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App<T> {
                }
                """);
    }

    @Test
    void rejectsComponentMethodWithParameters() {
        failsWith("must not declare parameters", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    String value(String input);
                }
                """);
    }

    @Test
    void rejectsVoidComponentMethod() {
        failsWith("must return a type", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    void run();
                }
                """);
    }

    @Test
    void rejectsGetDeclaration() {
        failsWith("must not declare a get(Class) method", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    <T> T get(Class<T> type);
                }
                """);
    }

    @Test
    void rejectsGenericComponentMethod() {
        failsWith("must not declare type parameters", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    <T> T value();
                }
                """);
    }

    @Test
    void rejectsWildcardComponentMethod() {
        failsWith("must not return a wildcard", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                import java.util.List;
                
                @Component
                public interface App {
                    List<? extends String> values();
                }
                """);
    }

    @Test
    void rejectsRawProviderComponentMethod() {
        failsWith("exactly one type argument", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                import io.github.foundry4j.inject.Provider;
                
                @Component
                public interface App {
                    Provider value();
                }
                """);
    }

    @Test
    void rejectsRawProviderDependency() {
        failsWith("Provider must declare exactly one type argument", "com.example.A", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                import io.github.foundry4j.inject.Provider;
                
                public final class A {
                    @Inject
                    public A(Provider provider) {
                    }
                }
                """);
    }

    @Test
    void rejectsWildcardDependency() {
        failsWith("must not be a wildcard", "com.example.A", """
                package com.example;
                
                import io.github.foundry4j.inject.Inject;
                import java.util.List;
                
                public final class A {
                    @Inject
                    public A(List<? extends String> values) {
                    }
                }
                """);
    }

    @Test
    void rejectsBlankNamedValue() {
        Map<String, String> sources = componentReturningString();
        sources.put("com.example.BlankModule", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Named;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class BlankModule {
                    @Provides
                    @Named("  ")
                    public static String value() {
                        return "";
                    }
                }
                """);
        failsWith("@Named value must not be blank", sources);
    }

    @Test
    void rejectsGeneratedNameCollision() {
        failsWith("already exists", "com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                }
                
                final class AppImpl {
                }
                """);
    }

    @Test
    void rejectsPrivateModuleConstructor() {
        failsWith("must not be private", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class M {
                    private M() {
                    }
                
                    @Provides
                    public String value() {
                        return "";
                    }
                }
                """);
    }

    @Test
    void rejectsModuleWithoutNoArgumentConstructor() {
        failsWith("no no-argument constructor", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class M {
                    public M(String prefix) {
                    }
                
                    @Provides
                    public String value() {
                        return "";
                    }
                }
                """);
    }

    @Test
    void rejectsAbstractModuleWithInstanceProviders() {
        failsWith("cannot be instantiated", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public abstract class M {
                    @Provides
                    public String value() {
                        return "";
                    }
                }
                """);
    }

    @Test
    void rejectsGenericModuleWithInstanceProviders() {
        failsWith("must not declare type parameters", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class M<T> {
                    @Provides
                    public String value() {
                        return "";
                    }
                }
                """);
    }

    @Test
    void rejectsInnerModuleWithInstanceProviders() {
        failsWith("must be a top-level or static nested type", "com.example.Outer", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                public final class Outer {
                    @Module
                    public final class M {
                        @Provides
                        public String value() {
                            return "";
                        }
                    }
                }
                """);
    }

    @Test
    void rejectsProviderMethodOutsideModule() {
        failsWith("must be declared in a @Module type", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Provides;
                
                public final class M {
                    @Provides
                    public static String value() {
                        return "";
                    }
                }
                """);
    }

    @Test
    void rejectsAbstractProviderMethod() {
        failsWith("must have a body", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public abstract class M {
                    @Provides
                    public abstract String value();
                }
                """);
    }

    @Test
    void rejectsPrivateProviderMethod() {
        failsWith("must not be private", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class M {
                    @Provides
                    private static String value() {
                        return "";
                    }
                }
                """);
    }

    @Test
    void rejectsGenericProviderMethod() {
        failsWith("must not declare type parameters", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class M {
                    @Provides
                    public static <T> String value() {
                        return "";
                    }
                }
                """);
    }

    @Test
    void rejectsVoidProviderMethod() {
        failsWith("must return a value", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class M {
                    @Provides
                    public static void value() {
                    }
                }
                """);
    }

    @Test
    void rejectsBindsMethodWithBody() {
        failsWith("must be abstract", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Binds;
                import io.github.foundry4j.inject.Module;
                
                @Module
                public interface M {
                    @Binds
                    default Object bind(String value) {
                        return value;
                    }
                }
                """);
    }

    @Test
    void rejectsBindsWithWrongParameterCount() {
        failsWith("exactly one parameter", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Binds;
                import io.github.foundry4j.inject.Module;
                
                @Module
                public interface M {
                    @Binds
                    Object bind(String first, String second);
                }
                """);
    }

    @Test
    void rejectsBindsWithIncompatibleParameter() {
        failsWith("is not assignable", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Binds;
                import io.github.foundry4j.inject.Module;
                
                @Module
                public interface M {
                    @Binds
                    String bind(Object value);
                }
                """);
    }

    @Test
    void rejectsGenericBindsMethod() {
        failsWith("must not declare type parameters", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Binds;
                import io.github.foundry4j.inject.Module;
                
                @Module
                public interface M {
                    @Binds
                    <T> Object bind(String value);
                }
                """);
    }

    @Test
    void rejectsVoidBindsMethod() {
        failsWith("must return a value", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Binds;
                import io.github.foundry4j.inject.Module;
                
                @Module
                public interface M {
                    @Binds
                    void bind(String value);
                }
                """);
    }

    @Test
    void rejectsBindsOutsideModule() {
        failsWith("must be declared in a @Module type", "com.example.M", """
                package com.example;
                
                import io.github.foundry4j.inject.Binds;
                
                public interface M {
                    @Binds
                    Object bind(String value);
                }
                """);
    }

    private static Map<String, String> componentReturningString() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    String value();
                }
                """);
        return sources;
    }

    private static void failsWith(String expected, String className, String source) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.App", EMPTY_COMPONENT);
        sources.put(className, source);
        failsWith(expected, sources);
    }

    private static void failsWith(String expected, Map<String, String> sources) {
        TestCompiler.Result result = TestCompiler.compile(sources);
        assertFalse(result.success(), "compilation unexpectedly succeeded");
        assertTrue(result.errors().contains(expected),
                () -> "expected [" + expected + "] in:\n" + result.errors());
    }
}
