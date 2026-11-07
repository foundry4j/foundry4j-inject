package io.github.foundry4j.inject;

/// Thrown when an injector cannot resolve a request at runtime.
///
/// Compile-time validation catches missing bindings, duplicate bindings, inaccessible types,
/// and constructor cycles before any code runs. This exception is reserved for the runtime
/// lookup API, for example when [Injector#get(Class)] is called with a type that was never
/// required by the component interface.
public class InjectException extends RuntimeException {

    /// Creates an exception with a message.
    ///
    /// @param message the detail message
    public InjectException(String message) {
        super(message);
    }

    /// Creates an exception with a message and a cause.
    ///
    /// @param message the detail message
    /// @param cause   the underlying cause
    public InjectException(String message, Throwable cause) {
        super(message, cause);
    }
}
