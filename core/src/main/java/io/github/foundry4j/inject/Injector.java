package io.github.foundry4j.inject;

/// Runtime entry point shared by every generated injector.
///
/// Generated components implement this interface in addition to the user-declared component
/// interface, which makes it possible to look up an unqualified binding through its class
/// token without reflective scanning:
///
/// ```java
/// Injector injector = new ApplicationComponentImpl();
/// UserService service = injector.get(UserService.class);
/// ```
///
/// `get` only resolves bindings that are reachable from the component and that have no
/// [Named] qualifier. Qualified bindings and primitive types are intentionally excluded,
/// because a `Class` token cannot express either of them.
///
/// @see Component
public interface Injector {

    /// Returns the unqualified binding for the given type.
    ///
    /// @param type the requested type, must not be `null`
    /// @param <T>  the requested type
    /// @return the resolved instance
    /// @throws InjectException      if no unqualified binding is reachable from this injector
    /// @throws NullPointerException if `type` is `null`
    <T> T get(Class<T> type);
}
