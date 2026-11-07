package io.github.foundry4j.inject.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Resolves the transitive dependency graph reachable from a component and validates it.
///
/// Validation has three parts:
///
/// - every requested key must have a binding;
/// - every reachable binding must be usable from the package of the generated component;
/// - constructor cycles must be broken by a `Provider`.
///
/// Provider edges are deliberately excluded from cycle detection. A `Provider<T>` defers
/// creation, so `A -> Provider<B> -> A` is legal and useful.
final class GraphResolver {

    /// One entry point of a component, resolved to its binding key.
    record Request(Key key, Element origin, String description) {
    }

    private final ProcessingContext context;
    private final PackageElement targetPackage;
    private final Map<Key, Binding> bindings;
    private final Map<Key, Binding> reachable = new LinkedHashMap<>();
    private final Set<Key> missing = new HashSet<>();
    private final Set<Key> checkedForCycles = new HashSet<>();

    GraphResolver(ProcessingContext context, PackageElement targetPackage) {
        this.context = context;
        this.targetPackage = targetPackage;
        this.bindings = context.bindings();
    }

    /// Resolves all requests and returns the reachable bindings in discovery order.
    Map<Key, Binding> resolve(List<Request> requests) {
        for (Request request : requests) {
            visit(request.key(), request.origin(), request.description());
        }
        for (Key key : List.copyOf(reachable.keySet())) {
            checkAccess(bindings.get(key));
        }
        for (Request request : requests) {
            detectCycle(request.key());
        }
        return Collections.unmodifiableMap(reachable);
    }

    private void visit(Key key, Element origin, String description) {
        if (reachable.containsKey(key)) {
            return;
        }
        Binding binding = bindings.get(key);
        if (binding == null) {
            if (missing.add(key)) {
                context.error("no binding for " + key.display() + "; required by " + description, origin);
            }
            return;
        }
        reachable.put(key, binding);
        for (Dependency dependency : binding.dependencies()) {
            visit(dependency.key(), dependency.origin(), dependency.description());
        }
        if (binding.kind() == Binding.Kind.PROVIDES && !binding.staticProvider()) {
            visit(Key.of(binding.declaringType()), binding.origin(),
                    "instance provider method " + binding.declaringType() + "." + binding.methodName() + "()");
        }
    }

    private void checkAccess(Binding binding) {
        String packageName = targetPackage.getQualifiedName().toString();
        String where = packageName.isEmpty() ? "the default package" : "package " + packageName;
        if (!Access.typeAccessible(binding.keyType(), targetPackage, context.types(), context.elements())) {
            context.error("type " + binding.key().type() + " is not accessible from " + where
                    + "; used by " + binding.originDescription(), binding.origin());
        }
        if (binding.kind() == Binding.Kind.CONSTRUCTOR) {
            Element constructor = binding.creationElement();
            if (constructor != null
                    && !Access.memberAccessible(constructor, targetPackage, context.elements())) {
                context.error("constructor of " + binding.declaringType() + " is not accessible from "
                        + where, binding.origin());
            }
        } else if (binding.kind() == Binding.Kind.PROVIDES) {
            if (!Access.memberAccessible(binding.creationElement(), targetPackage, context.elements())) {
                context.error("provider method " + binding.originDescription() + " is not accessible from "
                        + where, binding.origin());
            }
            TypeElement module = context.typeElement(binding.declaringType());
            if (module != null && !Access.typeElementAccessible(module, targetPackage, context.elements())) {
                context.error("module " + binding.declaringType() + " is not accessible from " + where,
                        binding.origin());
            }
        }
    }

    private void detectCycle(Key root) {
        if (checkedForCycles.contains(root)) {
            return;
        }
        dfs(root, new ArrayDeque<>(), new HashSet<>());
    }

    private void dfs(Key key, Deque<Key> path, Set<Key> visiting) {
        if (checkedForCycles.contains(key)) {
            return;
        }
        if (visiting.contains(key)) {
            List<Key> keys = new ArrayList<>(path);
            int start = keys.indexOf(key);
            List<Key> cycle = new ArrayList<>(keys.subList(start, keys.size()));
            cycle.add(key);
            StringBuilder signature = new StringBuilder();
            for (Key element : cycle) {
                if (!signature.isEmpty()) {
                    signature.append(" -> ");
                }
                signature.append(element.display());
            }
            Binding binding = bindings.get(key);
            Element origin = binding == null ? null : binding.origin();
            context.reportCycle(signature.toString(), "circular dependency: " + signature, origin);
            return;
        }
        Binding binding = bindings.get(key);
        if (binding == null) {
            return;
        }
        visiting.add(key);
        path.addLast(key);
        for (Dependency dependency : binding.dependencies()) {
            if (!dependency.provider()) {
                dfs(dependency.key(), path, visiting);
            }
        }
        path.removeLast();
        visiting.remove(key);
        checkedForCycles.add(key);
    }
}
