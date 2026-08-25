# qjs4j

[![Build and Test](https://github.com/caoccao/qjs4j/workflows/Build/badge.svg)](https://github.com/caoccao/qjs4j/actions) [![Maven Central](https://img.shields.io/maven-central/v/com.caoccao.qjs4j/qjs4j)](https://central.sonatype.com/artifact/com.caoccao.qjs4j/qjs4j) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

qjs4j is a native Java implementation of QuickJS - a complete reimplementation of the QuickJS JavaScript engine in pure Java (JDK 17+, zero external dependencies).

## Project Status

qjs4j implements ES2024 features with full QuickJS specification compliance. See [detailed feature list](docs/migration/FEATURES.md) for comprehensive implementation status.

### Features Beyond QuickJS

qjs4j includes features not present in the original QuickJS:

- **Float16Array**: IEEE 754 half-precision (16-bit) floating point typed array support
- **ES2024 Features**: Promise.withResolvers, Object.groupBy, Map.groupBy
- **ShadowRealm (runtime-gated)**: Proposal feature support for test262 compatibility, enabled via `JSRuntimeOptions.setShadowRealmEnabled(true)`
- **Enhanced Module System**: Complete ES6 module implementation with dynamic import()
- **Microtask Queue**: Full ES2020-compliant microtask infrastructure

### Not Yet Implemented

The following QuickJS features are planned but not yet implemented:

- **Internationalization (Intl)**: i18n support for dates, numbers, and strings
- **Top-level await**: Module-level await expressions

See [ASYNC_AWAIT_ENHANCEMENTS.md](docs/migration/ASYNC_AWAIT_ENHANCEMENTS.md) for async/await implementation details.

## Documentation

- **[Features](docs/migration/FEATURES.md)**: Complete list of implemented JavaScript features
- **[Migration Status](docs/migration/MIGRATION_STATUS.md)**: Migration progress from QuickJS C to Java
- **[Async/Await](docs/migration/ASYNC_AWAIT_ENHANCEMENTS.md)**: Async/await and iteration implementation

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.caoccao.qjs4j:qjs4j:0.1.1")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.caoccao.qjs4j:qjs4j:0.1.1'
}
```

### Maven

```xml
<dependency>
    <groupId>com.caoccao.qjs4j</groupId>
    <artifactId>qjs4j</artifactId>
    <version>0.1.1</version>
</dependency>
```

## Quick Start

```java
import com.caoccao.qjs4j.core.*;

// Create a JavaScript runtime and context
try (JSContext context = new JSContext(new JSRuntime())) {
    // Evaluate JavaScript code
    JSValue result = context.eval("2 + 2");
    System.out.println(result); // 4

    // Work with objects
    JSValue obj = context.eval("({ name: 'qjs4j', version: '1.0' })");
    if (obj instanceof JSObject jsObj) {
        JSValue name = jsObj.get("name");
        System.out.println(name); // qjs4j
    }

    // Use modern JavaScript features
    JSValue promise = context.eval("Promise.resolve(42)");
    // Process microtasks to settle promises
    context.processMicrotasks();
}
```

## Building from source

```bash
./gradlew build
```

The Gradle toolchain compiles and tests against JDK 17 wherever the build is launched from, so a
JDK 17 installation must be discoverable (Gradle will provision one if it is not). Gradle's Kotlin
DSL compiles the build script before any toolchain is selected, so the *launching* JDK also has to
be one the wrapper's Gradle release understands: Gradle 9.4.1 accepts JDK 17 through 25. On a newer
JDK the build aborts with a bare version number — upgrade the wrapper, or set `JAVA_HOME` to a
supported release.

Test JVM: `./gradlew test -PtestJavaVersion=21` runs the suite on JDK 21 instead of 17. CI uses this
to exercise both LTS releases.

## Architecture

qjs4j is organized into modular packages:

- **core**: Runtime components (JSValue types, JSContext, JSRuntime)
- **vm**: Virtual machine with bytecode execution and stack management
- **builtins**: JavaScript built-in objects and prototype methods
- **compiler**: Parser, lexer, bytecode compiler, and AST

Key technical features:
- Shape-based optimization with hidden classes
- Proper SameValueZero equality for Map/Set
- Complete iterator and async iterator protocols
- Full prototype-based inheritance
- Weak references using Java WeakHashMap

## License

Apache License 2.0 - see [LICENSE](LICENSE) file for details.
