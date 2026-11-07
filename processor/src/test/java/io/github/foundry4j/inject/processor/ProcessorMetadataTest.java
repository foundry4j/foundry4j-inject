package io.github.foundry4j.inject.processor;

import org.junit.jupiter.api.Test;

import javax.lang.model.SourceVersion;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorMetadataTest {

    @Test
    void declaresAllSupportedAnnotations() {
        Set<String> supported = new InjectProcessor().getSupportedAnnotationTypes();
        assertEquals(Set.of(
                "io.github.foundry4j.inject.Component",
                "io.github.foundry4j.inject.Inject",
                "io.github.foundry4j.inject.Module",
                "io.github.foundry4j.inject.Provides",
                "io.github.foundry4j.inject.Binds",
                "io.github.foundry4j.inject.Named",
                "io.github.foundry4j.inject.Singleton"), supported);
    }

    @Test
    void supportsLatestSourceVersion() {
        assertEquals(SourceVersion.latestSupported(), new InjectProcessor().getSupportedSourceVersion());
    }

    @Test
    void isRegisteredAsAService() throws Exception {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/services/javax.annotation.processing.Processor")) {
            assertNotNull(stream, "processor service file is missing");
            String registration = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .readLine();
            assertEquals("io.github.foundry4j.inject.processor.InjectProcessor", registration);
        }
    }

    @Test
    void generatedSourceUsesNoReflection() {
        TestCompiler.Result result = TestCompiler.compile("com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                }
                """);

        assertTrue(result.success(), result::errors);
        assertTrue(result.classes().containsKey("com.example.AppImpl"));
    }
}
