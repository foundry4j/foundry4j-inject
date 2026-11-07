package io.github.foundry4j.inject.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import java.util.Locale;

/// Converts [TypeMirror] instances into source text and classifies provider requests.
///
/// All generated code uses fully qualified names; no imports are emitted. This keeps the
/// generator free of name-collision logic while remaining deterministic.
final class TypeUtils {

    private static final String PROVIDER = "io.github.foundry4j.inject.Provider";

    private TypeUtils() {
    }

    /// Renders a type as valid Java source.
    static String render(TypeMirror type) {
        return switch (type.getKind()) {
            case DECLARED -> renderDeclared((DeclaredType) type);
            case ARRAY -> render(((ArrayType) type).getComponentType()) + "[]";
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID ->
                    type.getKind().name().toLowerCase(Locale.ROOT);
            case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
            case WILDCARD -> renderWildcard((WildcardType) type);
            default -> type.toString();
        };
    }

    private static String renderDeclared(DeclaredType type) {
        TypeElement element = (TypeElement) type.asElement();
        String name = element.getQualifiedName().toString();
        if (type.getTypeArguments().isEmpty()) {
            return name;
        }
        StringBuilder result = new StringBuilder(name).append('<');
        for (int i = 0; i < type.getTypeArguments().size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(render(type.getTypeArguments().get(i)));
        }
        return result.append('>').toString();
    }

    private static String renderWildcard(WildcardType type) {
        if (type.getExtendsBound() != null) {
            return "? extends " + render(type.getExtendsBound());
        }
        if (type.getSuperBound() != null) {
            return "? super " + render(type.getSuperBound());
        }
        return "?";
    }

    /// Returns `true` when the type is exactly `Provider<...>`.
    static boolean isProvider(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
                && ((DeclaredType) type).asElement() instanceof TypeElement element
                && element.getQualifiedName().contentEquals(PROVIDER);
    }

    /// Returns the single type argument of a provider type, or `null` when it is not
    /// parameterized with exactly one argument.
    static TypeMirror providerArgument(TypeMirror type) {
        if (!isProvider(type)) {
            return null;
        }
        var arguments = ((DeclaredType) type).getTypeArguments();
        return arguments.size() == 1 ? arguments.getFirst() : null;
    }

    /// Extracts a short name suitable for building a method identifier.
    static String simpleName(String renderedType) {
        int generic = renderedType.indexOf('<');
        String raw = generic >= 0 ? renderedType.substring(0, generic) : renderedType;
        raw = raw.replace("[]", "Array");
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }

    /// Removes type arguments so the result is usable in a class literal.
    ///
    /// `java.util.List<java.lang.String>` becomes `java.util.List`, and
    /// `java.util.List<java.lang.String>[]` becomes `java.util.List[]`.
    static String erase(String renderedType) {
        StringBuilder result = new StringBuilder(renderedType.length());
        int depth = 0;
        for (int i = 0; i < renderedType.length(); i++) {
            char character = renderedType.charAt(i);
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth--;
            } else if (depth == 0) {
                result.append(character);
            }
        }
        return result.toString();
    }

    /// Builds a deterministic, valid method identifier for a binding.
    static String accessorName(Key key, int index) {
        String simple = simpleName(key.type());
        if (simple.isEmpty()) {
            return "$binding" + index;
        }
        String base = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
        base = base.replaceAll("[^A-Za-z0-9_$]", "");
        if (base.isEmpty() || !Character.isJavaIdentifierStart(base.charAt(0))) {
            base = "binding" + base.replaceAll("[^A-Za-z0-9_$]", "");
        }
        return "$" + base + index;
    }
}
