package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.InjectException;
import io.github.foundry4j.inject.Injector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentShapeTest {

    @Test
    void includesInheritedEntryPoints() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.Base", """
                        package com.example;
                        
                        public interface Base {
                            String value();
                        }
                        """,
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App extends Base {
                        }
                        """,
                "com.example.Values", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        
                        @Module
                        public final class Values {
                            @Provides
                            public static String value() {
                                return "inherited";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        assertEquals("inherited", TestCompiler.invoke(result.instantiate("com.example.AppImpl"), "value"));
    }

    @Test
    void supportsComponentsThatExtendInjector() throws Exception {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        import io.github.foundry4j.inject.Injector;
                        
                        @Component
                        public interface App extends Injector {
                            Service service();
                        }
                        """,
                "com.example.Service", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Service {
                            @Inject
                            public Service() {
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        Injector injector = (Injector) component;
        assertNotNull(injector.get(result.load("com.example.Service")));
    }

    @Test
    void usesTheMostSpecificCovariantEntryPoint() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.Base", """
                        package com.example;
                        
                        public interface Base {
                            Object value();
                        }
                        """,
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App extends Base {
                            @Override
                            String value();
                        }
                        """,
                "com.example.Values", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        
                        @Module
                        public final class Values {
                            @Provides
                            public static String value() {
                                return "covariant";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        assertEquals("covariant", TestCompiler.invoke(result.instantiate("com.example.AppImpl"), "value"));
    }

    @Test
    void ignoresDefaultAndStaticMethods() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            String value();
                        
                            default String other() {
                                return "default";
                            }
                        
                            static String helper() {
                                return "static";
                            }
                        }
                        """,
                "com.example.Values", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        
                        @Module
                        public final class Values {
                            @Provides
                            public static String value() {
                                return "value";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        assertEquals("value", TestCompiler.invoke(component, "value"));
        assertEquals("default", TestCompiler.invoke(component, "other"));
    }

    @Test
    void generatesLookupForEmptyComponent() {
        TestCompiler.Result result = TestCompiler.compile("com.example.App", """
                package com.example;
                
                import io.github.foundry4j.inject.Component;
                
                @Component
                public interface App {
                }
                """);

        assertTrue(result.success(), result::errors);
        Injector injector = (Injector) result.instantiate("com.example.AppImpl");
        assertThrows(InjectException.class, () -> injector.get(String.class));
    }

    @Test
    void supportsPackagePrivateComponents() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        interface App {
                            String value();
                        }
                        """,
                "com.example.Values", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        
                        @Module
                        public final class Values {
                            @Provides
                            public static String value() {
                                return "package-private";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        assertEquals("package-private",
                TestCompiler.invoke(result.instantiate("com.example.AppImpl"), "value"));
    }
}
