package io.github.foundry4j.inject.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/// Immutable description of one binding discovered in the current compilation.
final class Binding {

    /// How the instance is created.
    enum Kind {
        /// `new Type(...)` using an `@Inject` constructor.
        CONSTRUCTOR,
        /// A call to a `@Provides` method.
        PROVIDES,
        /// A direct forward from an interface key to its implementation key.
        BINDS
    }

    private final Key key;
    private final Kind kind;
    private final boolean singleton;
    private final List<Dependency> dependencies;
    private final Element origin;
    private final TypeMirror keyType;
    private final String declaringType;
    private final String methodName;
    private final boolean staticProvider;
    private final Element creationElement;
    private final boolean synthetic;

    private Binding(Key key, Kind kind, boolean singleton, List<Dependency> dependencies, Element origin,
                    TypeMirror keyType, String declaringType, String methodName, boolean staticProvider,
                    Element creationElement, boolean synthetic) {
        this.key = key;
        this.kind = kind;
        this.singleton = singleton;
        this.dependencies = List.copyOf(dependencies);
        this.origin = origin;
        this.keyType = keyType;
        this.declaringType = declaringType;
        this.methodName = methodName;
        this.staticProvider = staticProvider;
        this.creationElement = creationElement;
        this.synthetic = synthetic;
    }

    static Binding constructor(Key key, boolean singleton, List<Dependency> dependencies, Element constructor,
                               Element origin, TypeMirror keyType, String declaringType, boolean synthetic) {
        return new Binding(key, Kind.CONSTRUCTOR, singleton, dependencies, origin, keyType, declaringType,
                null, false, constructor, synthetic);
    }

    static Binding provides(Key key, boolean singleton, List<Dependency> dependencies, ExecutableElement method,
                            TypeMirror keyType, String moduleType, boolean staticProvider) {
        return new Binding(key, Kind.PROVIDES, singleton, dependencies, method, keyType, moduleType,
                method.getSimpleName().toString(), staticProvider, method, false);
    }

    static Binding binds(Key key, boolean singleton, Dependency dependency, ExecutableElement method,
                         TypeMirror keyType, String moduleType) {
        return new Binding(key, Kind.BINDS, singleton, List.of(dependency), method, keyType, moduleType,
                method.getSimpleName().toString(), false, method, false);
    }

    Key key() {
        return key;
    }

    Kind kind() {
        return kind;
    }

    boolean singleton() {
        return singleton;
    }

    List<Dependency> dependencies() {
        return dependencies;
    }

    Element origin() {
        return origin;
    }

    TypeMirror keyType() {
        return keyType;
    }

    String declaringType() {
        return declaringType;
    }

    String methodName() {
        return methodName;
    }

    boolean staticProvider() {
        return staticProvider;
    }

    /// The constructor or provider method that generated code invokes, or `null` for an
    /// implicit module constructor.
    Element creationElement() {
        return creationElement;
    }

    boolean synthetic() {
        return synthetic;
    }

    /// Description used in "declared by" and "used by" diagnostics.
    String originDescription() {
        return switch (kind) {
            case CONSTRUCTOR -> declaringType + " constructor";
            case PROVIDES, BINDS -> declaringType + "." + methodName + "()";
        };
    }
}
