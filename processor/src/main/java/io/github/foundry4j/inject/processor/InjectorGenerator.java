package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.Injector;
import io.github.foundry4j.inject.Named;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Generates the implementation class for a `@Component` interface.
///
/// The emitted source contains one private accessor per reachable binding, a public method
/// for every entry point, and an implementation of [Injector#get(Class)] based on class-token
/// comparison. Singleton bindings use double-checked locking with a `volatile` field; no
/// reflection, proxies, or runtime containers are involved.
final class InjectorGenerator {

    private static final String INJECTOR = "io.github.foundry4j.inject.Injector";
    private static final String INJECT_EXCEPTION = "io.github.foundry4j.inject.InjectException";

    /// An abstract component method and the key it resolves to.
    private record EntryPoint(ExecutableElement method, Key key, boolean provider) {
    }

    private final ProcessingContext context;
    private final Elements elements;
    private final Types types;

    InjectorGenerator(ProcessingContext context) {
        this.context = context;
        this.elements = context.elements();
        this.types = context.types();
    }

    void generate(TypeElement component) {
        if (!validateComponent(component)) {
            return;
        }
        PackageElement targetPackage = elements.getPackageOf(component);
        String packageName = targetPackage.getQualifiedName().toString();
        String simpleName = component.getSimpleName() + "Impl";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

        if (elements.getTypeElement(qualifiedName) != null) {
            context.error("cannot generate " + qualifiedName + ": a type with that name already exists",
                    component);
            return;
        }

        List<EntryPoint> entryPoints = entryPoints(component);
        if (context.failed()) {
            return;
        }

        List<GraphResolver.Request> requests = new ArrayList<>();
        for (EntryPoint entryPoint : entryPoints) {
            requests.add(new GraphResolver.Request(entryPoint.key(), entryPoint.method(),
                    "component method " + entryPoint.method().getSimpleName() + "()"));
        }
        Map<Key, Binding> reachable = new GraphResolver(context, targetPackage).resolve(requests);
        if (context.failed()) {
            return;
        }

        String source = render(component, packageName, simpleName, entryPoints, reachable);
        if (context.failed()) {
            return;
        }
        try (Writer writer = context.filer().createSourceFile(qualifiedName, component).openWriter()) {
            writer.write(source);
        } catch (IOException exception) {
            context.error("failed to write " + qualifiedName + ": " + exception.getMessage(), component);
        }
    }

    private boolean validateComponent(TypeElement component) {
        if (component.getKind() != ElementKind.INTERFACE) {
            context.error("@Component may only annotate an interface", component);
            return false;
        }
        if (component.getNestingKind() != NestingKind.TOP_LEVEL) {
            context.error("component " + component.getQualifiedName() + " must be a top-level interface",
                    component);
            return false;
        }
        if (!component.getTypeParameters().isEmpty()) {
            context.error("component " + component.getQualifiedName()
                    + " must not declare type parameters", component);
            return false;
        }
        return true;
    }

    private List<EntryPoint> entryPoints(TypeElement component) {
        List<EntryPoint> result = new ArrayList<>();
        Map<String, EntryPoint> byName = new LinkedHashMap<>();
        for (Element member : elements.getAllMembers(component)) {
            if (!(member instanceof ExecutableElement method)
                    || !method.getModifiers().contains(Modifier.ABSTRACT)
                    || method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (method.getEnclosingElement() instanceof TypeElement declaring
                    && declaring.getQualifiedName().contentEquals(INJECTOR)) {
                continue;
            }
            if (method.getSimpleName().contentEquals("get") && method.getParameters().size() == 1) {
                context.error("component must not declare a get(Class) method; it is generated automatically",
                        method);
                return List.of();
            }
            if (!method.getParameters().isEmpty()) {
                context.error("component method " + method.getSimpleName() + " must not declare parameters",
                        method);
                continue;
            }
            if (!method.getTypeParameters().isEmpty()) {
                context.error("component method " + method.getSimpleName()
                        + " must not declare type parameters", method);
                continue;
            }
            TypeMirror returnType = method.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                context.error("component method " + method.getSimpleName() + " must return a type", method);
                continue;
            }
            boolean provider = TypeUtils.isProvider(returnType);
            TypeMirror requested = returnType;
            if (provider) {
                requested = TypeUtils.providerArgument(returnType);
                if (requested == null) {
                    context.error("Provider return type of " + method.getSimpleName()
                            + " must declare exactly one type argument", method);
                    continue;
                }
            }
            if (!validateType(requested, method)) {
                continue;
            }
            EntryPoint entryPoint = new EntryPoint(method,
                    new Key(TypeUtils.render(requested), qualifier(method)), provider);
            EntryPoint previous = byName.get(method.getSimpleName().toString());
            if (previous == null) {
                byName.put(method.getSimpleName().toString(), entryPoint);
                result.add(entryPoint);
            } else if (types.isAssignable(method.getReturnType(), previous.method().getReturnType())) {
                byName.put(method.getSimpleName().toString(), entryPoint);
                result.remove(previous);
                result.add(entryPoint);
            } else if (!types.isAssignable(previous.method().getReturnType(), method.getReturnType())) {
                context.error("component declares incompatible methods named '" + method.getSimpleName()
                        + "'", method);
            }
        }
        return result;
    }

    private boolean validateType(TypeMirror type, Element origin) {
        return switch (type.getKind()) {
            case VOID -> {
                context.error("component method must not return void", origin);
                yield false;
            }
            case TYPEVAR -> {
                context.error("component method must return a concrete type but returns a type variable",
                        origin);
                yield false;
            }
            case WILDCARD -> {
                context.error("component method must not return a wildcard", origin);
                yield false;
            }
            case ERROR -> {
                context.error("component method returns an unresolved type " + type, origin);
                yield false;
            }
            case DECLARED -> {
                boolean valid = true;
                for (TypeMirror argument : ((DeclaredType) type).getTypeArguments()) {
                    valid = validateType(argument, origin);
                    if (!valid) {
                        break;
                    }
                }
                yield valid;
            }
            case ARRAY -> validateType(((ArrayType) type).getComponentType(), origin);
            default -> true;
        };
    }

    private String qualifier(Element element) {
        Named named = element.getAnnotation(Named.class);
        if (named == null) {
            return null;
        }
        if (named.value().isBlank()) {
            context.error("@Named value must not be blank", element);
            return null;
        }
        return named.value();
    }

    private String render(TypeElement component, String packageName, String simpleName,
                          List<EntryPoint> entryPoints, Map<Key, Binding> reachable) {
        Map<Key, String> accessors = new LinkedHashMap<>();
        int index = 0;
        for (Key key : reachable.keySet()) {
            accessors.put(key, TypeUtils.accessorName(key, index++));
        }

        StringBuilder source = new StringBuilder(4096);
        source.append("// Generated by foundry4j-inject. Do not edit.\n");
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n");
        }
        source.append('\n');
        source.append("public final class ").append(simpleName).append(" implements ")
                .append(component.getQualifiedName()).append(", ").append(INJECTOR).append(" {\n\n");
        source.append("    public ").append(simpleName).append("() {\n    }\n\n");

        for (EntryPoint entryPoint : entryPoints) {
            source.append("    @Override\n");
            source.append("    public ").append(TypeUtils.render(entryPoint.method().getReturnType()))
                    .append(' ').append(entryPoint.method().getSimpleName()).append("() {\n");
            String accessor = accessors.get(entryPoint.key());
            if (entryPoint.provider()) {
                source.append("        return () -> ").append(accessor).append("();\n");
            } else {
                source.append("        return ").append(accessor).append("();\n");
            }
            source.append("    }\n\n");
        }

        source.append("    @Override\n");
        source.append("    public <T> T get(java.lang.Class<T> type) {\n");
        source.append("        java.util.Objects.requireNonNull(type, \"type\");\n");
        for (Binding binding : reachable.values()) {
            Key key = binding.key();
            if (key.qualifier() == null && !binding.keyType().getKind().isPrimitive()) {
                source.append("        if (type == ").append(TypeUtils.erase(key.type())).append(".class) {\n");
                source.append("            return type.cast(").append(accessors.get(key)).append("());\n");
                source.append("        }\n");
            }
        }
        source.append("        throw new ").append(INJECT_EXCEPTION)
                .append("(\"no binding for \" + type.getName());\n");
        source.append("    }\n\n");

        for (Map.Entry<Key, Binding> entry : reachable.entrySet()) {
            appendFields(source, entry.getValue(), accessors.get(entry.getKey()));
        }
        for (Map.Entry<Key, Binding> entry : reachable.entrySet()) {
            appendAccessor(source, entry.getValue(), accessors.get(entry.getKey()), accessors);
        }

        source.append("}\n");
        return source.toString();
    }

    private void appendFields(StringBuilder source, Binding binding, String accessor) {
        if (!binding.singleton()) {
            return;
        }
        if (binding.keyType().getKind().isPrimitive()) {
            source.append("    private volatile boolean ").append(accessor).append("$set;\n");
            source.append("    private ").append(binding.key().type()).append(' ').append(accessor)
                    .append(";\n\n");
        } else {
            source.append("    private volatile ").append(binding.key().type()).append(' ').append(accessor)
                    .append(";\n\n");
        }
    }

    private void appendAccessor(StringBuilder source, Binding binding, String accessor,
                                Map<Key, String> accessors) {
        String creation = creation(binding, accessors);
        source.append("    private ").append(binding.key().type()).append(' ').append(accessor)
                .append("() {\n");
        if (!binding.singleton()) {
            source.append("        return ").append(creation).append(";\n");
        } else if (binding.keyType().getKind().isPrimitive()) {
            source.append("        if (!").append(accessor).append("$set) {\n");
            source.append("            synchronized (this) {\n");
            source.append("                if (!").append(accessor).append("$set) {\n");
            source.append("                    ").append(accessor).append(" = ").append(creation)
                    .append(";\n");
            source.append("                    ").append(accessor).append("$set = true;\n");
            source.append("                }\n");
            source.append("            }\n");
            source.append("        }\n");
            source.append("        return ").append(accessor).append(";\n");
        } else {
            source.append("        ").append(binding.key().type()).append(" $value = ").append(accessor)
                    .append(";\n");
            source.append("        if ($value == null) {\n");
            source.append("            synchronized (this) {\n");
            source.append("                $value = ").append(accessor).append(";\n");
            source.append("                if ($value == null) {\n");
            source.append("                    $value = ").append(accessor).append(" = ").append(creation)
                    .append(";\n");
            source.append("                }\n");
            source.append("            }\n");
            source.append("        }\n");
            source.append("        return $value;\n");
        }
        source.append("    }\n\n");
    }

    private String creation(Binding binding, Map<Key, String> accessors) {
        return switch (binding.kind()) {
            case CONSTRUCTOR -> "new " + binding.declaringType() + "(" + arguments(binding, accessors) + ")";
            case PROVIDES -> {
                String arguments = arguments(binding, accessors);
                if (binding.staticProvider()) {
                    yield binding.declaringType() + "." + binding.methodName() + "(" + arguments + ")";
                }
                String module = accessors.get(Key.of(binding.declaringType()));
                if (module == null) {
                    context.error("internal error: module instance for " + binding.declaringType()
                            + " was not generated", binding.origin());
                    yield "null";
                }
                yield module + "()." + binding.methodName() + "(" + arguments + ")";
            }
            case BINDS -> {
                Dependency dependency = binding.dependencies().getFirst();
                yield accessors.get(dependency.key()) + "()";
            }
        };
    }

    private String arguments(Binding binding, Map<Key, String> accessors) {
        StringBuilder arguments = new StringBuilder();
        for (Dependency dependency : binding.dependencies()) {
            if (!arguments.isEmpty()) {
                arguments.append(", ");
            }
            String accessor = accessors.get(dependency.key());
            if (dependency.provider()) {
                arguments.append("() -> ").append(accessor).append("()");
            } else {
                arguments.append(accessor).append("()");
            }
        }
        return arguments.toString();
    }
}
