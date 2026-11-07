package io.github.foundry4j.inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Groups provider methods into a reusable module.
///
/// Every method annotated with [Provides] or [Binds] must be declared in a type annotated
/// with `@Module`. Modules are discovered automatically from the current compilation unit;
/// there is no registration list to maintain.
///
/// A module may be:
///
/// - a concrete class with `static` provider methods (no instance is created), or
/// - a concrete class with instance provider methods (the injector creates the module), or
/// - an abstract class or interface that only declares `@Binds` methods (no instance is needed).
///
/// A module that declares instance provider methods must expose either an `@Inject`
/// constructor or a non-private no-argument constructor.
///
/// ```java
/// @Module
/// public final class StorageModule {
///     @Provides
///     @Singleton
///     public Storage storage(Configuration configuration) {
///         return new FileStorage(configuration.root());
///     }
/// }
/// ```
///
/// @see Provides
/// @see Binds
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Module {
}
