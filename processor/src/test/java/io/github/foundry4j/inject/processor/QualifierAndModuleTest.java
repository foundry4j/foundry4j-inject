package io.github.foundry4j.inject.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualifierAndModuleTest {

    @Test
    void resolvesQualifiedBindingsFromInstanceModule() throws Exception {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        import io.github.foundry4j.inject.Named;
                        
                        @Component
                        public interface App {
                            @Named("formal")
                            String formal();
                        
                            @Named("casual")
                            String casual();
                        
                            int count();
                        
                            Consumer consumer();
                        }
                        """,
                "com.example.GreetingModule", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Named;
                        import io.github.foundry4j.inject.Provides;
                        
                        @Module
                        public final class GreetingModule {
                            private final Prefix prefix;
                        
                            @Inject
                            public GreetingModule(Prefix prefix) {
                                this.prefix = prefix;
                            }
                        
                            @Provides
                            @Named("formal")
                            public String formal() {
                                return prefix.value() + "sir";
                            }
                        
                            @Provides
                            @Named("casual")
                            public String casual() {
                                return prefix.value() + "mate";
                            }
                        
                            @Provides
                            public static int count() {
                                return 7;
                            }
                        }
                        """,
                "com.example.Prefix", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Prefix {
                            @Inject
                            public Prefix() {
                            }
                        
                            public String value() {
                                return "hi ";
                            }
                        }
                        """,
                "com.example.Consumer", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        import io.github.foundry4j.inject.Named;
                        
                        public final class Consumer {
                            public final String value;
                        
                            @Inject
                            public Consumer(@Named("formal") String value) {
                                this.value = value;
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        assertEquals("hi sir", TestCompiler.invoke(component, "formal"));
        assertEquals("hi mate", TestCompiler.invoke(component, "casual"));
        assertEquals(7, TestCompiler.invoke(component, "count"));
        assertEquals("hi sir", TestCompiler.field(TestCompiler.invoke(component, "consumer"), "value"));
    }

    @Test
    void bindsInterfacesToImplementations() throws Exception {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            Repository repository();
                            Repository again();
                        }
                        """,
                "com.example.Repository", """
                        package com.example;
                        
                        public interface Repository {
                            String name();
                        }
                        """,
                "com.example.JdbcRepository", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class JdbcRepository implements Repository {
                            public static int created;
                        
                            @Inject
                            public JdbcRepository() {
                                created++;
                            }
                        
                            @Override
                            public String name() {
                                return "jdbc";
                            }
                        }
                        """,
                "com.example.RepositoryModule", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Binds;
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Singleton;
                        
                        @Module
                        public interface RepositoryModule {
                            @Binds
                            @Singleton
                            Repository repository(JdbcRepository implementation);
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        Object repository = TestCompiler.invoke(component, "repository");
        assertSame(repository, TestCompiler.invoke(component, "again"));
        assertEquals("jdbc", TestCompiler.invoke(repository, "name"));
        assertEquals(1, result.load("com.example.JdbcRepository").getField("created").getInt(null));
    }

    @Test
    void instantiatesModulesWithImplicitConstructor() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            String now();
                        }
                        """,
                "com.example.TimeModule", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        
                        @Module
                        public final class TimeModule {
                            @Provides
                            public String now() {
                                return "now";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        assertEquals("now", TestCompiler.invoke(result.instantiate("com.example.AppImpl"), "now"));
    }
}
