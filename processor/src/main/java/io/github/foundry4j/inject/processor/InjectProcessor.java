package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.Binds;
import io.github.foundry4j.inject.Component;
import io.github.foundry4j.inject.Inject;
import io.github.foundry4j.inject.Module;
import io.github.foundry4j.inject.Named;
import io.github.foundry4j.inject.Provides;
import io.github.foundry4j.inject.Singleton;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.Set;

/// Compile-time dependency injection processor.
///
/// The processor is registered as a service and runs automatically during compilation. For
/// every interface annotated with [Component] it collects the bindings declared in the same
/// compilation, validates the reachable graph, and writes a `<Name>Impl` source file.
///
/// All validation happens before any source is written. A single invalid declaration fails
/// compilation with a diagnostic and prevents generated files from producing secondary
/// errors.
///
/// @see Component
public final class InjectProcessor extends AbstractProcessor {

    private final Set<String> generated = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                Component.class.getCanonicalName(),
                Inject.class.getCanonicalName(),
                Module.class.getCanonicalName(),
                Provides.class.getCanonicalName(),
                Binds.class.getCanonicalName(),
                Named.class.getCanonicalName(),
                Singleton.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> components = roundEnv.getElementsAnnotatedWith(Component.class);
        if (components.isEmpty()) {
            return false;
        }

        ProcessingContext context = new ProcessingContext(processingEnv);
        new BindingCollector(context).collect(roundEnv);
        for (Element element : components) {
            if (!(element instanceof TypeElement component)) {
                context.error("@Component may only annotate an interface", element);
                continue;
            }
            if (generated.add(component.getQualifiedName().toString())) {
                new InjectorGenerator(context).generate(component);
            }
        }
        return false;
    }
}
