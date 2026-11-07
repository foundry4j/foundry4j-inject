package io.github.foundry4j.inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Declares an injector interface whose implementation is generated at compile time.
///
/// Every abstract, parameterless method of the interface becomes an entry point. The return
/// type is resolved against the bindings discovered in the same compilation, and the
/// processor emits a class named `<SimpleName>Impl` in the same package.
///
/// ```java
/// @Component
/// public interface ApplicationComponent {
///     UserService userService();
///     Provider<Report> reportProvider();
/// }
/// ```
///
/// produces:
///
/// ```java
/// public final class ApplicationComponentImpl implements ApplicationComponent, Injector {
///     public ApplicationComponentImpl() { }
///     @Override public UserService userService() { ... }
///     @Override public Provider<Report> reportProvider() { ... }
///     @Override public <T> T get(Class<T> type) { ... }
/// }
/// ```
///
/// Entry points may be annotated with [Named] to select a qualified binding. An entry point
/// that returns `Provider<T>` defers creation until `Provider#get()` is called.
///
/// Requirements for the annotated type:
///
/// - it must be a top-level interface;
/// - it must not declare type parameters;
/// - it must not declare its own `get(Class)` method;
/// - every abstract method must be parameterless and return a concrete type.
///
/// A `@Component` may extend another interface; inherited abstract methods are entry points
/// as well.
///
/// @see Injector
/// @see Provider
/// @see Named
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
}
