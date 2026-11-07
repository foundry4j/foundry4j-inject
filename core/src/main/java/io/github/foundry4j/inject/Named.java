package io.github.foundry4j.inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Qualifies a binding so that multiple instances of the same type can coexist.
///
/// `@Named` may be placed on a [Provides] method to name the produced binding, on a
/// constructor parameter to request a named binding, and on a method of an injector
/// interface to select the binding exposed by that method.
///
/// ```java
/// @Module
/// public final class DataSourceModule {
///     @Provides
///     @Named("primary")
///     public DataSource primary() { ... }
///
///     @Provides
///     @Named("replica")
///     public DataSource replica() { ... }
/// }
///
/// public final class UserRepository {
///     @Inject
///     public UserRepository(@Named("replica") DataSource dataSource) { ... }
/// }
/// ```
///
/// The value must not be blank.
///
/// @see Provides
/// @see Injector
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface Named {

    /// The qualifier value. Must not be blank.
    ///
    /// @return the binding name
    String value();
}
