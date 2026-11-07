# Foundry4j Inject

<img src="logo.svg" alt="Foundry4j Inject logo" align="right" width="128" height="128" />

Compile-time dependency injection for Java with zero runtime reflection.

![Java 27+](https://img.shields.io/badge/Java-27%2B-ED8B00?logo=openjdk&logoColor=white)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.foundry4j/foundry4j-inject?logo=apachemaven&color=blue)](https://central.sonatype.com/artifact/io.github.foundry4j/foundry4j-inject)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=foundry4j_foundry4j-inject&metric=coverage)](https://sonarcloud.io/summary/new_code?id=foundry4j_foundry4j-inject)
[![CI](https://github.com/foundry4j/foundry4j-inject/actions/workflows/ci.yml/badge.svg)](https://github.com/foundry4j/foundry4j-inject/actions/workflows/ci.yml)

## Overview

Foundry4j Inject resolves object graphs during compilation and emits plain Java source for
every `@Component` interface. The generated class wires constructors and providers directly,
so at runtime there is no reflection, no bytecode manipulation, and no container to bootstrap.

The full dependency graph is validated by the annotation processor: missing bindings,
duplicate bindings, unbreakable cycles, and accessibility problems become compilation errors
with the exact parameter that caused them.

## Features

- **Zero reflection at runtime.** Generated code calls constructors and static/instance
  providers directly.
- **Compile-time graph validation.** Missing bindings, duplicate keys, dependency cycles, and
  inaccessible types fail the build with a precise diagnostic.
- **Constructor injection.** `@Inject` on a single constructor, including canonical record
  constructors.
- **Scopes.** `@Singleton` for one shared instance per component; everything else is a
  prototype.
- **Explicit bindings.** `@Module` + `@Provides` factories and `@Binds` interface forwarding.
- **Qualifiers.** `@Named` on parameters and provider methods.
- **Lazy injection.** Inject `Provider<T>` to defer creation or break constructor cycles.
- **Runtime lookup.** Every generated component also implements `Injector`, so framework code
  can ask for a type by `Class`.
- **No dependencies.** The core artifact depends only on the JDK; test dependencies are JUnit
  Jupiter.

## Requirements

- JDK 27 or newer
- Maven 3.9 or newer

## Installation

The project is a Maven reactor with two artifacts:

| Artifact | Purpose |
| --- | --- |
| `io.github.foundry4j:foundry4j-inject` | Annotations, `Provider`, `Injector`, `InjectException` |
| `io.github.foundry4j:foundry4j-inject-processor` | Annotation processor that generates component implementations |

Build and install both into the local repository:

```bash
mvn clean install
```

Then declare the API as a dependency and the processor on the annotation processor path:

```xml
<dependency>
    <groupId>io.github.foundry4j</groupId>
    <artifactId>foundry4j-inject</artifactId>
    <version>1.0.0</version>
</dependency>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.16.0</version>
    <configuration>
        <release>27</release>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.foundry4j</groupId>
                <artifactId>foundry4j-inject-processor</artifactId>
                <version>1.0.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Gradle users declare `annotationProcessor("io.github.foundry4j:foundry4j-inject-processor:1.0.0")`
alongside the core dependency.

## Quick start

Define the injectable types with `@Inject`.

```java
package com.example;

import io.github.foundry4j.inject.Inject;

public final class UserRepository {
    @Inject
    public UserRepository() {
    }

    public String find(long id) {
        return "user-" + id;
    }
}
```

```java
package com.example;

import io.github.foundry4j.inject.Inject;

public final class UserService {
    private final UserRepository repository;

    @Inject
    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public String describe(long id) {
        return "loaded " + repository.find(id);
    }
}
```

Declare a component interface. Each abstract, parameterless method is an entry point.

```java
package com.example;

import io.github.foundry4j.inject.Component;

@Component
public interface AppComponent {
    UserService userService();
}
```

The processor generates `com.example.AppComponentImpl` in the same package. Instantiate it
directly — there is no factory lookup.

```java
AppComponent component = new AppComponentImpl();
String description = component.userService().describe(42);
```

## Modules and providers

`@Provides` methods supply types that cannot be constructor-injected: primitives, third-party
classes, configuration values. They must be declared in a `@Module` type.

```java
package com.example;

import io.github.foundry4j.inject.Module;
import io.github.foundry4j.inject.Provides;
import io.github.foundry4j.inject.Singleton;
import java.time.Duration;

@Module
public final class StorageModule {
    @Provides
    @Singleton
    static Database database() {
        return new PostgresDatabase("jdbc:postgresql://localhost/app");
    }

    @Provides
    static Duration timeout() {
        return Duration.ofSeconds(5);
    }
}
```

Static providers need no module instance. An instance provider causes the processor to create
the module through a no-argument (or injectable) constructor, once per component.

`@Binds` forwards an interface key to an implementation that is itself injectable:

```java
package com.example;

import io.github.foundry4j.inject.Binds;
import io.github.foundry4j.inject.Module;

@Module
public interface BindingModule {
    @Binds
    Mailer mailer(SmtpMailer mailer);
}
```

Modules are discovered from the compilation and are available to every component compiled in
the same unit.

## Qualifiers

Use `@Named` when several bindings share a type:

```java
@Module
public final class CacheModule {
    @Provides
    @Named("cache.ttl")
    static Duration cacheTtl() {
        return Duration.ofMinutes(10);
    }
}
```

```java
public final class Cache {
    private final Duration ttl;

    @Inject
    public Cache(@Named("cache.ttl") Duration ttl) {
        this.ttl = ttl;
    }
}
```

An unqualified `Duration` and a `@Named("cache.ttl") Duration` are distinct keys. A component
method can also be annotated with `@Named` to select a qualified binding.

## Scopes

- `@Singleton` on the injectable class, `@Provides` method, or `@Binds` method caches one
  instance per component instance. Lazy double-checked locking with a `volatile` field makes
  access thread-safe, including for primitive keys.
- Without `@Singleton`, every request creates a new instance (prototype scope).

## Providers and cycles

`Provider<T>` parameters and entry points defer creation until `get()` is called:

```java
@Component
public interface AppComponent {
    OrderProcessor orderProcessor();
    Provider<Report> report();
}
```

Provider edges are excluded from cycle detection, so mutual dependencies are legal when at
least one side is lazy:

```java
@Singleton
public final class A {
    @Inject
    public A(Provider<B> b) {
    }
}

@Singleton
public final class B {
    @Inject
    public B(A a) {
    }
}
```

## Runtime lookup

Each generated component implements `io.github.foundry4j.inject.Injector`:

```java
Injector injector = (Injector) component;
UserService service = injector.get(UserService.class);
```

Lookup compares the supplied class token against the bindings reachable from the component's
entry points. Unknown types throw `InjectException`, and `null` throws
`NullPointerException`. This makes generated components usable from generic infrastructure
without giving up static typing for normal callers.

## Compile-time validation

The processor reports errors instead of generating invalid code. It rejects, among others:

- missing bindings, including through `Provider` edges;
- two bindings for the same key (for example a constructor plus a `@Provides` method);
- dependency cycles not broken by a `Provider`;
- multiple `@Inject` constructors, or `@Inject` on abstract, generic, private-constructor, or
  inner classes;
- `@Provides` or `@Binds` outside a `@Module`;
- `@Provides` methods that are abstract, private, generic, or return `void`;
- `@Binds` methods that are not abstract, do not declare exactly one parameter, or bind an
  incompatible type;
- component methods with parameters, type parameters, or `void`/wildcard returns;
- types, constructors, provider methods, or modules that are not accessible from the generated
  implementation's package;
- a component that is not a top-level, non-generic interface.

## How generation works

For a component named `AppComponent` the processor writes `AppComponentImpl` in the same
package. The emitted shape is intentionally boring:

```java
// Generated by foundry4j-inject. Do not edit.
package com.example;

public final class AppComponentImpl implements com.example.AppComponent, io.github.foundry4j.inject.Injector {

    public AppComponentImpl() {
    }

    @Override
    public com.example.UserService userService() {
        return $userService0();
    }

    @Override
    public <T> T get(java.lang.Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (type == com.example.UserService.class) {
            return type.cast($userService0());
        }
        if (type == com.example.UserRepository.class) {
            return type.cast($userRepository1());
        }
        throw new io.github.foundry4j.inject.InjectException("no binding for " + type.getName());
    }

    private volatile com.example.UserRepository $userRepository1;

    private com.example.UserService $userService0() {
        return new com.example.UserService($userRepository1());
    }

    private com.example.UserRepository $userRepository1() {
        com.example.UserRepository $value = $userRepository1;
        if ($value == null) {
            synchronized (this) {
                $value = $userRepository1;
                if ($value == null) {
                    $value = $userRepository1 = new com.example.UserRepository();
                }
            }
        }
        return $value;
    }
}
```

All names are fully qualified and accessors are indexed deterministically, so generated output
is stable across builds and free of import collisions.

## Limitations

- Injection is constructor-only; fields and methods are not injected.
- Injected classes must be concrete, non-generic, and top-level or static nested.
- Components must be top-level, non-generic interfaces.
- Modules are global to a compilation; there are no per-component module lists or
  subcomponents.
- Generated code is only as accessible as its package allows: a package-private constructor is
  reachable only when the injected type shares the component's package.
