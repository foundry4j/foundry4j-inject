package io.github.foundry4j.inject;

/// Lazily supplies an instance of `T`.
///
/// A `Provider<T>` may be injected wherever `T` is requested. The generated injector passes a
/// lambda that calls the corresponding binding method, so the instance is created only when
/// `get()` is invoked.
///
/// Providers have two useful properties:
///
/// - they break constructor cycles, because the dependency is not created while the
///   requesting object is being constructed;
/// - they give each `get()` call a fresh instance for prototype-scoped bindings, while
///   singleton-scoped bindings stay cached.
///
/// ```java
/// public final class ReportService {
///     private final Provider<Report> reports;
///
///     @Inject
///     public ReportService(Provider<Report> reports) {
///         this.reports = reports;
///     }
/// }
/// ```
///
/// @param <T> the provided type
/// @see Component
@FunctionalInterface
public interface Provider<T> {

    /// Returns an instance.
    ///
    /// @return a new or cached instance, depending on the binding scope
    T get();
}
