package io.github.foundry4j.inject.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/// Accessibility checks performed relative to the package of a generated component.
///
/// Generated classes live in the same package as their component interface. A type or member
/// is therefore usable when it is `public`, or when it is not `private` and declared in the
/// same package. Protected members are only usable because of the same-package rule; the
/// generated class never extends user types.
final class Access {

    private Access() {
    }

    /// Returns `true` when the type and all of its type arguments can be named from `from`.
    static boolean typeAccessible(TypeMirror type, PackageElement from, Types types, Elements elements) {
        return switch (type.getKind()) {
            case DECLARED -> {
                DeclaredType declared = (DeclaredType) type;
                boolean accessible = types.asElement(type) instanceof TypeElement element
                        && typeElementAccessible(element, from, elements);
                if (accessible) {
                    for (TypeMirror argument : declared.getTypeArguments()) {
                        accessible = typeAccessible(argument, from, types, elements);
                        if (!accessible) {
                            break;
                        }
                    }
                }
                yield accessible;
            }
            case ARRAY -> typeAccessible(((ArrayType) type).getComponentType(), from, types, elements);
            default -> true;
        };
    }

    /// Returns `true` when the type and all enclosing member types can be named from `from`.
    static boolean typeElementAccessible(TypeElement type, PackageElement from, Elements elements) {
        if (type.getModifiers().contains(Modifier.PRIVATE)) {
            return false;
        }
        if (!type.getModifiers().contains(Modifier.PUBLIC) && !samePackage(type, from, elements)) {
            return false;
        }
        return !(type.getEnclosingElement() instanceof TypeElement enclosing)
                || typeElementAccessible(enclosing, from, elements);
    }

    /// Returns `true` when the constructor or method can be invoked from `from`.
    static boolean memberAccessible(Element member, PackageElement from, Elements elements) {
        if (member.getModifiers().contains(Modifier.PRIVATE)) {
            return false;
        }
        return member.getModifiers().contains(Modifier.PUBLIC) || samePackage(member, from, elements);
    }

    private static boolean samePackage(Element element, PackageElement from, Elements elements) {
        return elements.getPackageOf(element).getQualifiedName().contentEquals(from.getQualifiedName());
    }
}
