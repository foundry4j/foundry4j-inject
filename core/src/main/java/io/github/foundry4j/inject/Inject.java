package io.github.foundry4j.inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks the constructor that the compile-time injector must call to create a component.
///
/// A type becomes a component when exactly one of its constructors is annotated with
/// `@Inject`. The annotation processor reads the constructor parameters, resolves every
/// parameter as a dependency, and emits plain Java code that performs the same call.
///
/// Example:
///
/// ```java
/// public final class UserService {
///     private final UserRepository repository;
///
///     @Inject
///     public UserService(UserRepository repository) {
///         this.repository = repository;
///     }
/// }
/// ```
///
/// Rules enforced at compile time:
///
/// - the annotated element must be a constructor;
/// - a component may declare only one `@Inject` constructor;
/// - the constructor must not be `private`;
/// - the declaring type must be a concrete class or record;
/// - the declaring type must not be generic and must not be a non-static inner class.
///
/// Only constructor injection is supported. Field and method injection are intentionally
/// absent: immutable objects constructed in one place are easier to reason about, test, and
/// use with virtual threads.
///
/// @see Injector
/// @see Singleton
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface Inject {
}
