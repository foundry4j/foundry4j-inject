package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.Injector;
import io.github.foundry4j.inject.Provider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeAndProviderTest {

    @Test
    void cachesSingletonsAndCreatesPrototypes() throws Exception {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        import io.github.foundry4j.inject.Provider;
                        
                        @Component
                        public interface App {
                            Registry registry();
                            Session session();
                            int answer();
                            Provider<Registry> registryProvider();
                        }
                        """,
                "com.example.Registry", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        import io.github.foundry4j.inject.Singleton;
                        
                        @Singleton
                        public final class Registry {
                            public static int created;
                        
                            @Inject
                            public Registry() {
                                created++;
                            }
                        }
                        """,
                "com.example.Session", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Session {
                            public static int created;
                        
                            @Inject
                            public Session() {
                                created++;
                            }
                        }
                        """,
                "com.example.Values", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        import io.github.foundry4j.inject.Singleton;
                        
                        @Module
                        public final class Values {
                            public static int calls;
                        
                            @Provides
                            @Singleton
                            public static int answer() {
                                calls++;
                                return 42;
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        Class<?> registryType = result.load("com.example.Registry");
        Class<?> sessionType = result.load("com.example.Session");

        Object registry = TestCompiler.invoke(component, "registry");
        assertSame(registry, TestCompiler.invoke(component, "registry"));
        assertSame(registry, ((Provider<?>) TestCompiler.invoke(component, "registryProvider")).get());
        assertEquals(1, registryType.getField("created").getInt(null));

        Object session = TestCompiler.invoke(component, "session");
        assertNotSame(session, TestCompiler.invoke(component, "session"));
        assertEquals(2, sessionType.getField("created").getInt(null));

        assertEquals(42, TestCompiler.invoke(component, "answer"));
        assertEquals(42, TestCompiler.invoke(component, "answer"));
        assertEquals(1, result.load("com.example.Values").getField("calls").getInt(null));

        Injector injector = (Injector) component;
        assertSame(registry, injector.get(registryType));
        assertNotSame(session, injector.get(sessionType));
    }

    @Test
    void providersDeferCreationAndBreakCycles() throws Exception {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        import io.github.foundry4j.inject.Provider;
                        
                        @Component
                        public interface App {
                            A a();
                            Lazy lazy();
                            Provider<Session> sessions();
                        }
                        """,
                "com.example.A", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        import io.github.foundry4j.inject.Provider;
                        import io.github.foundry4j.inject.Singleton;
                        
                        @Singleton
                        public final class A {
                            public final Provider<B> b;
                        
                            @Inject
                            public A(Provider<B> b) {
                                this.b = b;
                            }
                        }
                        """,
                "com.example.B", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        import io.github.foundry4j.inject.Singleton;
                        
                        @Singleton
                        public final class B {
                            public final A a;
                        
                            @Inject
                            public B(A a) {
                                this.a = a;
                            }
                        }
                        """,
                "com.example.Lazy", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        import io.github.foundry4j.inject.Provider;
                        
                        public final class Lazy {
                            private final Provider<Session> sessions;
                        
                            @Inject
                            public Lazy(Provider<Session> sessions) {
                                this.sessions = sessions;
                            }
                        
                            public Session create() {
                                return sessions.get();
                            }
                        }
                        """,
                "com.example.Session", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Session {
                            public static int created;
                        
                            @Inject
                            public Session() {
                                created++;
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        Class<?> sessionType = result.load("com.example.Session");

        Object a = TestCompiler.invoke(component, "a");
        Object b = ((Provider<?>) field(a, "b")).get();
        assertSame(a, field(b, "a"));
        assertEquals(0, sessionType.getField("created").getInt(null));

        Object lazy = TestCompiler.invoke(component, "lazy");
        assertEquals(0, sessionType.getField("created").getInt(null));
        Object first = TestCompiler.invoke(lazy, "create");
        Object second = TestCompiler.invoke(lazy, "create");
        assertEquals(2, sessionType.getField("created").getInt(null));
        assertNotSame(first, second);

        Provider<?> sessions = (Provider<?>) TestCompiler.invoke(component, "sessions");
        assertNotSame(sessions.get(), sessions.get());
    }

    @Test
    void bindsParameterizedAndArrayTypes() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            Holder holder();
                            String[] values();
                        }
                        """,
                "com.example.Holder", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        import java.util.List;
                        
                        public final class Holder {
                            private final List<String> names;
                        
                            @Inject
                            public Holder(List<String> names) {
                                this.names = names;
                            }
                        
                            public List<String> names() {
                                return names;
                            }
                        }
                        """,
                "com.example.Names", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        import java.util.List;
                        
                        @Module
                        public final class Names {
                            @Provides
                            public static List<String> names() {
                                return List.of("alpha", "beta");
                            }
                        
                            @Provides
                            public static String[] values() {
                                return new String[] {"x", "y"};
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        Object holder = TestCompiler.invoke(component, "holder");
        assertEquals(2, ((List<?>) TestCompiler.invoke(holder, "names")).size());
        assertEquals(2, ((String[]) TestCompiler.invoke(component, "values")).length);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getField(name);
        return field.get(target);
    }
}
