package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.Binds;
import io.github.foundry4j.inject.Inject;
import io.github.foundry4j.inject.Module;
import io.github.foundry4j.inject.Named;
import io.github.foundry4j.inject.Provides;
import io.github.foundry4j.inject.Singleton;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Discovers and validates bindings declared in the current round.
///
/// Discovery has three sources:
///
/// - constructors annotated with `@Inject` (component bindings);
/// - methods annotated with `@Provides` (factory bindings);
/// - abstract methods annotated with `@Binds` (forwarding bindings).
///
/// Instance provider methods additionally require a module instance. When the module has no
/// binding of its own, a singleton constructor binding is synthesized for it.
final class BindingCollector {

    private final ProcessingContext context;
    private final Set<TypeElement> instanceModules = new LinkedHashSet<>();

    BindingCollector(ProcessingContext context) {
        this.context = context;
    }

    void collect(RoundEnvironment roundEnv) {
        collectConstructors(roundEnv);
        collectProviders(roundEnv);
        collectBinds(roundEnv);
        synthesizeModuleInstances();
        context.seal();
    }

    private void collectConstructors(RoundEnvironment roundEnv) {
        Map<TypeElement, List<ExecutableElement>> constructors = new LinkedHashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Inject.class)) {
            if (element.getKind() != ElementKind.CONSTRUCTOR) {
                context.error("@Inject may only annotate a constructor", element);
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) element;
            TypeElement owner = (TypeElement) constructor.getEnclosingElement();
            constructors.computeIfAbsent(owner, _ -> new ArrayList<>()).add(constructor);
        }

        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : constructors.entrySet()) {
            TypeElement owner = entry.getKey();
            List<ExecutableElement> declared = entry.getValue();
            if (declared.size() > 1) {
                context.error("type " + owner.getQualifiedName()
                        + " declares multiple @Inject constructors; keep exactly one", owner);
                continue;
            }
            ExecutableElement constructor = declared.getFirst();
            if (!validateComponent(owner, constructor)) {
                continue;
            }
            List<Dependency> dependencies = dependencies(constructor);
            if (dependencies == null) {
                continue;
            }
            Key key = Key.of(TypeUtils.render(owner.asType()));
            context.register(Binding.constructor(key, owner.getAnnotation(Singleton.class) != null,
                    dependencies, constructor, constructor, owner.asType(),
                    owner.getQualifiedName().toString(), false));
        }
    }

    private boolean validateComponent(TypeElement owner, ExecutableElement constructor) {
        if (owner.getKind() != ElementKind.CLASS && owner.getKind() != ElementKind.RECORD) {
            context.error("@Inject is only supported on constructors of classes and records", constructor);
            return false;
        }
        if (owner.getModifiers().contains(Modifier.ABSTRACT)) {
            context.error("component " + owner.getQualifiedName() + " must not be abstract", owner);
            return false;
        }
        if (!owner.getTypeParameters().isEmpty()) {
            context.error("component " + owner.getQualifiedName()
                    + " must not declare type parameters in compile-time injection", owner);
            return false;
        }
        if (isNonStaticNested(owner)) {
            context.error("component " + owner.getQualifiedName()
                    + " must be a top-level or static nested type", owner);
            return false;
        }
        if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
            context.error("@Inject constructor of " + owner.getQualifiedName() + " must not be private",
                    constructor);
            return false;
        }
        return true;
    }

    private void collectProviders(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Provides.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                context.error("@Provides may only annotate a method", element);
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            TypeElement module = moduleOf(method);
            if (module == null) {
                continue;
            }
            if (method.getModifiers().contains(Modifier.ABSTRACT)) {
                context.error("@Provides method " + method.getSimpleName() + " must have a body", method);
                continue;
            }
            if (method.getModifiers().contains(Modifier.PRIVATE)) {
                context.error("@Provides method " + method.getSimpleName() + " must not be private", method);
                continue;
            }
            if (!method.getTypeParameters().isEmpty()) {
                context.error("@Provides method " + method.getSimpleName()
                        + " must not declare type parameters", method);
                continue;
            }
            TypeMirror returnType = method.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                context.error("@Provides method " + method.getSimpleName() + " must return a value", method);
                continue;
            }
            if (!validateType(returnType, method, "return type of @Provides method")) {
                continue;
            }
            List<Dependency> dependencies = dependencies(method);
            if (dependencies == null) {
                continue;
            }
            boolean staticProvider = method.getModifiers().contains(Modifier.STATIC);
            Key key = new Key(TypeUtils.render(returnType), qualifier(method));
            context.register(Binding.provides(key, method.getAnnotation(Singleton.class) != null,
                    dependencies, method, returnType, module.getQualifiedName().toString(), staticProvider));
            if (!staticProvider) {
                instanceModules.add(module);
            }
        }
    }

    private void collectBinds(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Binds.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                context.error("@Binds may only annotate a method", element);
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            TypeElement module = moduleOf(method);
            if (module == null) {
                continue;
            }
            if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
                context.error("@Binds method " + method.getSimpleName() + " must be abstract", method);
                continue;
            }
            if (!method.getTypeParameters().isEmpty()) {
                context.error("@Binds method " + method.getSimpleName() + " must not declare type parameters",
                        method);
                continue;
            }
            if (method.getParameters().size() != 1) {
                context.error("@Binds method " + method.getSimpleName()
                        + " must declare exactly one parameter", method);
                continue;
            }
            TypeMirror returnType = method.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                context.error("@Binds method " + method.getSimpleName() + " must return a value", method);
                continue;
            }
            if (!validateType(returnType, method, "return type of @Binds method")) {
                continue;
            }
            VariableElement parameter = method.getParameters().getFirst();
            if (!validateType(parameter.asType(), parameter, "parameter type of @Binds method")) {
                continue;
            }
            if (!context.types().isAssignable(parameter.asType(), returnType)) {
                context.error("@Binds parameter type " + TypeUtils.render(parameter.asType())
                        + " is not assignable to " + TypeUtils.render(returnType), method);
                continue;
            }
            Dependency dependency = new Dependency(new Key(TypeUtils.render(parameter.asType()),
                    qualifier(parameter)), false, parameter, describe(parameter));
            Key key = new Key(TypeUtils.render(returnType), qualifier(method));
            context.register(Binding.binds(key, method.getAnnotation(Singleton.class) != null, dependency,
                    method, returnType, module.getQualifiedName().toString()));
        }
    }

    /// Creates a singleton binding for modules that declare instance provider methods but are
    /// not injectable on their own.
    private void synthesizeModuleInstances() {
        for (TypeElement module : instanceModules) {
            Key key = Key.of(module.getQualifiedName().toString());
            if (context.hasBinding(key)) {
                continue;
            }
            String name = module.getQualifiedName().toString();
            if (module.getKind() != ElementKind.CLASS || module.getModifiers().contains(Modifier.ABSTRACT)) {
                context.error("module " + name + " declares instance @Provides methods but cannot be instantiated",
                        module);
                continue;
            }
            if (!module.getTypeParameters().isEmpty()) {
                context.error("module " + name + " must not declare type parameters when it has instance"
                        + " @Provides methods", module);
                continue;
            }
            if (isNonStaticNested(module)) {
                context.error("module " + name + " must be a top-level or static nested type", module);
                continue;
            }
            List<ExecutableElement> constructors = ElementFilter.constructorsIn(module.getEnclosedElements());
            if (constructors.isEmpty()) {
                context.register(Binding.constructor(key, true, List.of(), null, module, module.asType(),
                        name, true));
                continue;
            }
            ExecutableElement noArguments = constructors.stream()
                    .filter(constructor -> constructor.getParameters().isEmpty())
                    .findFirst()
                    .orElse(null);
            if (noArguments == null) {
                context.error("module " + name + " declares instance @Provides methods but has no"
                        + " no-argument constructor", module);
                continue;
            }
            if (noArguments.getModifiers().contains(Modifier.PRIVATE)) {
                context.error("no-argument constructor of module " + name + " must not be private",
                        noArguments);
                continue;
            }
            context.register(Binding.constructor(key, true, List.of(), noArguments, module, module.asType(),
                    name, true));
        }
    }

    private TypeElement moduleOf(ExecutableElement method) {
        if (method.getEnclosingElement() instanceof TypeElement type
                && type.getAnnotation(Module.class) != null) {
            return type;
        }
        context.error("@Provides and @Binds methods must be declared in a @Module type", method);
        return null;
    }

    private List<Dependency> dependencies(ExecutableElement executable) {
        List<Dependency> dependencies = new ArrayList<>();
        for (VariableElement parameter : executable.getParameters()) {
            Dependency dependency = dependency(parameter, true);
            if (dependency == null) {
                return null;
            }
            dependencies.add(dependency);
        }
        return dependencies;
    }

    private Dependency dependency(VariableElement parameter, boolean unwrapProvider) {
        TypeMirror type = parameter.asType();
        boolean provider = false;
        if (unwrapProvider && TypeUtils.isProvider(type)) {
            TypeMirror argument = TypeUtils.providerArgument(type);
            if (argument == null) {
                context.error("Provider must declare exactly one type argument", parameter);
                return null;
            }
            type = argument;
            provider = true;
        }
        if (!validateType(type, parameter, "parameter type")) {
            return null;
        }
        return new Dependency(new Key(TypeUtils.render(type), qualifier(parameter)), provider, parameter,
                describe(parameter));
    }

    private boolean validateType(TypeMirror type, Element origin, String role) {
        return switch (type.getKind()) {
            case VOID -> {
                context.error(role + " must not be void", origin);
                yield false;
            }
            case TYPEVAR -> {
                context.error(role + " must be a concrete type but is a type variable", origin);
                yield false;
            }
            case WILDCARD -> {
                context.error(role + " must not be a wildcard", origin);
                yield false;
            }
            case ERROR -> {
                context.error(role + " references an unresolved type " + type, origin);
                yield false;
            }
            case DECLARED -> validateDeclared((DeclaredType) type, origin, role);
            case ARRAY -> validateType(((ArrayType) type).getComponentType(), origin, role);
            default -> true;
        };
    }

    private boolean validateDeclared(DeclaredType type, Element origin, String role) {
        TypeMirror enclosing = type.getEnclosingType();
        if (enclosing.getKind() == TypeKind.DECLARED
                && !((DeclaredType) enclosing).getTypeArguments().isEmpty()) {
            context.error(role + " must not use a generic enclosing type", origin);
            return false;
        }
        for (TypeMirror argument : type.getTypeArguments()) {
            if (!validateType(argument, origin, role)) {
                return false;
            }
        }
        return true;
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

    private static String describe(VariableElement parameter) {
        Element owner = parameter.getEnclosingElement();
        String location = owner instanceof ExecutableElement executable
                ? executable.getEnclosingElement() + "." + executable.getSimpleName() + "()"
                : String.valueOf(owner);
        return "parameter '" + parameter.getSimpleName() + "' of " + location;
    }

    private static boolean isNonStaticNested(TypeElement type) {
        TypeElement current = type;
        while (current != null) {
            NestingKind nesting = current.getNestingKind();
            if (nesting == NestingKind.LOCAL || nesting == NestingKind.ANONYMOUS) {
                return true;
            }
            if (nesting == NestingKind.MEMBER && !current.getModifiers().contains(Modifier.STATIC)) {
                return true;
            }
            current = current.getEnclosingElement() instanceof TypeElement enclosing ? enclosing : null;
        }
        return false;
    }
}
