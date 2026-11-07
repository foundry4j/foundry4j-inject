package io.github.foundry4j.inject;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreApiTest {

    @Test
    void annotationsAreRuntimeRetained() {
        for (Class<?> annotation : Set.of(Inject.class, Component.class, Module.class, Provides.class,
                Binds.class, Named.class, Singleton.class)) {
            assertEquals(RetentionPolicy.RUNTIME,
                    annotation.getAnnotation(Retention.class).value(), annotation.getName());
        }
    }

    @Test
    void annotationsDeclareExpectedTargets() {
        assertEquals(Set.of(ElementType.CONSTRUCTOR), targets(Inject.class));
        assertEquals(Set.of(ElementType.TYPE), targets(Component.class));
        assertEquals(Set.of(ElementType.TYPE), targets(Module.class));
        assertEquals(Set.of(ElementType.METHOD), targets(Provides.class));
        assertEquals(Set.of(ElementType.METHOD), targets(Binds.class));
        assertEquals(Set.of(ElementType.PARAMETER, ElementType.METHOD), targets(Named.class));
        assertEquals(Set.of(ElementType.TYPE, ElementType.METHOD), targets(Singleton.class));
    }

    @Test
    void providerDefersCreationUntilGet() {
        int[] calls = {0};
        Provider<Integer> provider = () -> ++calls[0];
        assertEquals(0, calls[0]);
        assertEquals(1, provider.get());
        assertEquals(2, provider.get());
    }

    @Test
    void injectorExposesGet() throws NoSuchMethodException {
        assertTrue(Injector.class.isInterface());
        assertEquals(Object.class, Injector.class.getMethod("get", Class.class).getReturnType());
    }

    @Test
    void injectExceptionCarriesMessageAndCause() {
        InjectException plain = new InjectException("missing");
        assertEquals("missing", plain.getMessage());
        assertNull(plain.getCause());

        IllegalStateException cause = new IllegalStateException("root");
        InjectException wrapped = new InjectException("failure", cause);
        assertEquals("failure", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
        assertThrows(InjectException.class, () -> {
            throw new InjectException("thrown");
        });
    }

    private static Set<ElementType> targets(Class<?> annotation) {
        return Arrays.stream(annotation.getAnnotation(Target.class).value()).collect(Collectors.toSet());
    }
}
