package io.github.foundry4j.inject.processor;

import javax.lang.model.element.Element;

/// A single request for a binding, together with the source element that produced it.
///
/// `provider` is `true` when the request was written as `Provider<T>`; the stored key then
/// refers to `T`. The flag is used for cycle detection and for emitting a lambda instead of a
/// direct call.
record Dependency(Key key, boolean provider, Element origin, String description) {
}
