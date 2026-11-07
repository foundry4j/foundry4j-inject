package io.github.foundry4j.inject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Declares that a binding has exactly one instance per injector.
///
/// The annotation can be placed on:
///
/// - a component class annotated with an `@Inject` constructor, or
/// - a provider method annotated with [Provides] or [Binds].
///
/// The generated injector caches the instance in a `volatile` field and uses double-checked
/// locking, so concurrent callers observe a single fully constructed instance. Without this
/// annotation every lookup creates a new instance (prototype scope).
///
/// Each injector instance owns its own singleton cache. Two injectors generated from the same
/// compilation never share instances.
///
/// @see Inject
/// @see Provides
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Singleton {
}
