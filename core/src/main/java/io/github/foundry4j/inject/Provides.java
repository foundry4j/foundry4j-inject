package io.github.foundry4j.inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Declares a method that creates a binding.
///
/// The method return type is the bound key and every method parameter is a dependency that
/// the processor resolves at compile time. Provider methods may be `static` or instance
/// methods, must not be `private`, and must not declare type parameters.
///
/// ```java
/// @Module
/// public final class ClockModule {
///     @Provides
///     public Clock clock() {
///         return Clock.systemUTC();
///     }
/// }
/// ```
///
/// Use [Named] on the method to create a qualified binding, and [Singleton] to cache the
/// returned value per injector. Methods in a [Module] are discovered automatically.
///
/// @see Module
/// @see Binds
/// @see Named
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Provides {
}
