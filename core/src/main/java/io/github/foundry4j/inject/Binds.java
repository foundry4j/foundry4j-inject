package io.github.foundry4j.inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Binds an interface to its implementation without writing a factory method.
///
/// A `@Binds` method must be abstract, must declare exactly one parameter, and must return a
/// supertype of that parameter. The processor does not call the method; it simply forwards
/// the implementation instance to whoever requests the interface.
///
/// ```java
/// @Module
/// public interface RepositoryModule {
///     @Binds
///     UserRepository userRepository(JdbcUserRepository implementation);
/// }
/// ```
///
/// The implementation type must itself be injectable, either through an `@Inject`
/// constructor or through another provider method.
///
/// @see Module
/// @see Provides
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Binds {
}
