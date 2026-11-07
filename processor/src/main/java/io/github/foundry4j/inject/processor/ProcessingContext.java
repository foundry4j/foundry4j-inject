package io.github.foundry4j.inject.processor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Shared state for one annotation-processing round: utilities, diagnostics, and the binding
/// registry.
///
/// Errors are de-duplicated so that a single invalid declaration does not produce the same
/// message several times while the graph is traversed. Once [ProcessingContext#failed()] is
/// `true` no code is generated for the round, which prevents cascading compiler errors inside
/// generated files.
final class ProcessingContext {

    private static final String PREFIX = "[foundry4j-inject] ";

    private final ProcessingEnvironment environment;
    private final Elements elements;
    private final Types types;
    private final Messager messager;
    private final Map<Key, List<Binding>> discovered = new LinkedHashMap<>();
    private final Set<String> reported = new HashSet<>();
    private final Set<String> reportedCycles = new HashSet<>();
    private Map<Key, Binding> bindings = Map.of();
    private boolean failed;

    ProcessingContext(ProcessingEnvironment environment) {
        this.environment = environment;
        this.elements = environment.getElementUtils();
        this.types = environment.getTypeUtils();
        this.messager = environment.getMessager();
    }

    ProcessingEnvironment environment() {
        return environment;
    }

    Filer filer() {
        return environment.getFiler();
    }

    Elements elements() {
        return elements;
    }

    Types types() {
        return types;
    }

    TypeElement typeElement(String qualifiedName) {
        return elements.getTypeElement(qualifiedName);
    }

    /// Reports an error and marks the round as failed.
    void error(String message, Element element) {
        failed = true;
        String identity = message + "|" + (element == null ? "" : element.toString());
        if (reported.add(identity)) {
            messager.printMessage(Diagnostic.Kind.ERROR, PREFIX + message, element);
        }
    }

    /// Reports an error that is not attached to a source element.
    void error(String message) {
        error(message, null);
    }

    /// Reports a cycle at most once per distinct path.
    void reportCycle(String signature, String message, Element element) {
        if (reportedCycles.add(signature)) {
            error(message, element);
        }
    }

    boolean failed() {
        return failed;
    }

    void register(Binding binding) {
        discovered.computeIfAbsent(binding.key(), _ -> new ArrayList<>()).add(binding);
    }

    boolean hasBinding(Key key) {
        return discovered.containsKey(key);
    }

    /// Detects duplicate keys and freezes the registry for graph resolution.
    void seal() {
        Map<Key, Binding> unique = new LinkedHashMap<>();
        for (Map.Entry<Key, List<Binding>> entry : discovered.entrySet()) {
            List<Binding> candidates = entry.getValue();
            if (candidates.size() > 1) {
                StringBuilder origins = new StringBuilder();
                for (Binding candidate : candidates) {
                    if (!origins.isEmpty()) {
                        origins.append(", ");
                    }
                    origins.append(candidate.originDescription());
                }
                error("duplicate binding for " + entry.getKey().display() + " declared by " + origins,
                        candidates.get(1).origin());
            } else {
                unique.put(entry.getKey(), candidates.getFirst());
            }
        }
        this.bindings = Collections.unmodifiableMap(unique);
    }

    Map<Key, Binding> bindings() {
        return bindings;
    }
}
