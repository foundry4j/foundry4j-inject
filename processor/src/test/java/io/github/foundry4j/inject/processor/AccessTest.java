package io.github.foundry4j.inject.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTest {

    @Test
    void worksWithPackagePrivateConstructorInSamePackage() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "app.App", """
                        package app;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            Service service();
                        }
                        """,
                "app.Service", """
                        package app;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Service {
                            @Inject
                            Service() {
                            }
                        
                            public String text() {
                                return "local";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object service = TestCompiler.invoke(result.instantiate("app.AppImpl"), "service");
        assertEquals("local", TestCompiler.invoke(service, "text"));
    }

    @Test
    void rejectsInaccessibleConstructorFromAnotherPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("app.App", """
                package app;
                
                import io.github.foundry4j.inject.Component;
                import lib.Service;
                
                @Component
                public interface App {
                    Service service();
                }
                """);
        sources.put("lib.Service", """
                package lib;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class Service {
                    @Inject
                    Service() {
                    }
                }
                """);
        failsWith("constructor of lib.Service is not accessible from package app", sources);
    }

    @Test
    void rejectsInaccessibleTypeFromAnotherPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("app.App", """
                package app;
                
                import io.github.foundry4j.inject.Component;
                import lib.Service;
                
                @Component
                public interface App {
                    Service service();
                }
                """);
        sources.put("lib.Service", """
                package lib;
                
                import io.github.foundry4j.inject.Inject;
                
                public final class Service {
                    @Inject
                    public Service(Hidden hidden) {
                    }
                }
                """);
        sources.put("lib.Hidden", """
                package lib;
                
                import io.github.foundry4j.inject.Inject;
                
                final class Hidden {
                    @Inject
                    Hidden() {
                    }
                }
                """);
        failsWith("type lib.Hidden is not accessible from package app", sources);
    }

    @Test
    void rejectsInaccessibleProviderMethodFromAnotherPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("app.App", """
                package app;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    String value();
                }
                """);
        sources.put("lib.StringModule", """
                package lib;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                public final class StringModule {
                    @Provides
                    static String value() {
                        return "hidden";
                    }
                }
                """);
        failsWith("provider method lib.StringModule.value() is not accessible from package app", sources);
    }

    @Test
    void rejectsInaccessibleModuleFromAnotherPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("app.App", """
                package app;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                    String value();
                }
                """);
        sources.put("lib.HiddenModule", """
                package lib;
                
                import io.github.foundry4j.inject.Module;
                import io.github.foundry4j.inject.Provides;
                
                @Module
                final class HiddenModule {
                    @Provides
                    public static String value() {
                        return "hidden";
                    }
                }
                """);
        failsWith("module lib.HiddenModule is not accessible from package app", sources);
    }

    private static void failsWith(String expected, Map<String, String> sources) {
        TestCompiler.Result result = TestCompiler.compile(sources);
        assertFalse(result.success(), "compilation unexpectedly succeeded");
        assertTrue(result.errors().contains(expected),
                () -> "expected [" + expected + "] in:\n" + result.errors());
    }
}
