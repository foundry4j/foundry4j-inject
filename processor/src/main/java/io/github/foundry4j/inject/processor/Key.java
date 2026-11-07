package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.Named;

/// Identifies a binding: a rendered type plus an optional [Named] qualifier.
///
/// The type is stored as fully qualified source text so that the same text can be written
/// into generated code without a symbol table lookup. Type arguments are part of the key,
/// which means `Repository<User>` and `Repository<Order>` are different bindings.
record Key(String type, String qualifier) implements Comparable<Key> {

    static Key of(String type) {
        return new Key(type, null);
    }

    /// Human-readable form used in diagnostics and cycle messages.
    String display() {
        return qualifier == null ? type : "@" + qualifier + " " + type;
    }

    @Override
    public int compareTo(Key other) {
        int byType = type.compareTo(other.type);
        if (byType != 0) {
            return byType;
        }
        if (qualifier == null) {
            return other.qualifier == null ? 0 : -1;
        }
        return other.qualifier == null ? 1 : qualifier.compareTo(other.qualifier);
    }
}
