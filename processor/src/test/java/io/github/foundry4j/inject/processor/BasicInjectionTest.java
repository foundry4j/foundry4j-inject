package io.github.foundry4j.inject.processor;

import io.github.foundry4j.inject.InjectException;
import io.github.foundry4j.inject.Injector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicInjectionTest {

    @Test
    void generatesComponentWithConstructorChain() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            Greeter greeter();
                            Message message();
                        }
                        """,
                "com.example.Greeter", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Greeter {
                            private final Message message;
                        
                            @Inject
                            public Greeter(Message message) {
                                this.message = message;
                            }
                        
                            public String greet() {
                                return "hello " + message.text();
                            }
                        }
                        """,
                "com.example.Message", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Message {
                            @Inject
                            public Message() {
                            }
                        
                            public String text() {
                                return "world";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object component = result.instantiate("com.example.AppImpl");
        Object greeter = TestCompiler.invoke(component, "greeter");
        assertEquals("hello world", TestCompiler.invoke(greeter, "greet"));
        assertNotSame(greeter, TestCompiler.invoke(component, "greeter"));
    }

    @Test
    void exposesRuntimeLookupAndRejectsUnknownTypes() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
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
        Injector injector = assertInstanceOf(Injector.class, component);

        Class<?> service = result.load("com.example.Service");
        assertNotNull(injector.get(service));
        assertNotNull(injector.get(service), "prototype bindings return a new instance");

        InjectException unknown = assertThrows(InjectException.class, () -> injector.get(String.class));
        assertTrue(unknown.getMessage().contains("no binding"), unknown::getMessage);
        assertThrows(NullPointerException.class, () -> injector.get(null));
    }

    @Test
    void injectsRecordsThroughCanonicalConstructor() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "com.example.App", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            Settings settings();
                        }
                        """,
                "com.example.Settings", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Inject;
                        
                        public record Settings(String name) {
                            @Inject
                            public Settings {
                            }
                        }
                        """,
                "com.example.SettingsModule", """
                        package com.example;
                        
                        import io.github.foundry4j.inject.Module;
                        import io.github.foundry4j.inject.Provides;
                        
                        @Module
                        public final class SettingsModule {
                            @Provides
                            public static String name() {
                                return "default";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object settings = TestCompiler.invoke(result.instantiate("com.example.AppImpl"), "settings");
        assertNotNull(settings);
        assertEquals("Settings[name=default]", settings.toString());
    }

    @Test
    void supportsComponentsInTheDefaultPackage() {
        TestCompiler.Result result = TestCompiler.compile(Map.of(
                "App", """
                        import io.github.foundry4j.inject.Component;
                        
                        @Component
                        public interface App {
                            Value value();
                        }
                        """,
                "Value", """
                        import io.github.foundry4j.inject.Inject;
                        
                        public final class Value {
                            @Inject
                            public Value() {
                            }
                        
                            public String text() {
                                return "default";
                            }
                        }
                        """));

        assertTrue(result.success(), result::errors);
        Object value = TestCompiler.invoke(result.instantiate("AppImpl"), "value");
        assertEquals("default", TestCompiler.invoke(value, "text"));
    }
}
