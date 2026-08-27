# qjs4j

[![Build and Test](https://github.com/caoccao/qjs4j/workflows/Build/badge.svg)](https://github.com/caoccao/qjs4j/actions) [![Maven Central](https://img.shields.io/maven-central/v/com.caoccao.qjs4j/qjs4j)](https://central.sonatype.com/artifact/com.caoccao.qjs4j/qjs4j) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

qjs4j is a native Java implementation of QuickJS - a complete reimplementation of the QuickJS
JavaScript engine in pure Java, with zero external dependencies.

**Verified on JDK 17, 21 and 25**; the artifact is compiled for JDK 17. The CI matrix runs the
engine on 17 and 21 across Linux, macOS and Windows — 25 is verified by running the suite with
`-PtestJavaVersion=25`, and adding it to the matrix is recommended, because JDK 25 withdrew the
atomic access modes from byte-array view `VarHandle`s that `Atomics` depends on and a matrix
stopping at 21 did not notice.

## Project Status

qjs4j targets ES2024. Conformance is measured, not claimed: the Test262 quick partition runs
**97,325 interpretations** (every file that is not `noStrict`, `module` or `raw` is executed twice,
once sloppy and once strict, as `INTERPRETING.md` requires) and the long-running partition a further
**4,282**, and the runner exits nonzero on any failure. Reproduce with
`./gradlew test262Quick test262LongRunning` against a `../test262` checkout.

The suite is not the whole specification, and the [known limitations](#known-limitations) below list
what is understood not to work. See the [detailed feature list](docs/migration/FEATURES.md) for
per-feature status.

### Features Beyond QuickJS

qjs4j includes features not present in the original QuickJS:

- **Float16Array**: IEEE 754 half-precision (16-bit) floating point typed array support
- **ES2024 Features**: Promise.withResolvers, Object.groupBy, Map.groupBy
- **ShadowRealm (runtime-gated)**: Proposal feature support for test262 compatibility, enabled via `JSRuntimeOptions.setShadowRealmEnabled(true)`
- **Module System**: ES6 modules with dynamic `import()` — see the limitation below
- **Microtask Queue**: Full ES2020-compliant microtask infrastructure
- **Internationalization (Intl)**: `Collator`, `DateTimeFormat`, `DisplayNames`, `DurationFormat`,
  `ListFormat`, `Locale`, `NumberFormat`, `PluralRules`, `RelativeTimeFormat`, `Segmenter`
- **Temporal (runtime-gated)**: enabled via `JSRuntimeOptions.setTemporalEnabled(true)`
- **Top-level await**: `await` at module scope

### Known limitations

- **Module source is transformed textually, not parsed.** The parser validates `import`/`export`
  syntax and then discards it, so modules are implemented by rewriting module source into ordinary
  script source: imports become temporary accessors on the global object, and exports become
  generated bindings declared beside the author's own code. Three consequences are known to be
  wrong, and each is pinned as a `testKnownLimitation*` test in
  `src/test/java/com/caoccao/qjs4j/core/JSModuleKnownLimitationTest.java`, which is the
  authoritative list — those tests assert the wrong answer on purpose, so fixing a defect makes one
  fail:
  - an imported binding is not usable from a closure retained after `JSContext.eval()` returns, and
    is not live;
  - the transformer's generated bookkeeping bindings are visible to a direct `eval` inside the
    module;
  - an import attribute naming an unsupported module type is ignored rather than refused.

  Real module environment records, with indirect bindings the compiler can capture, are the fix for
  all three, and are a dedicated milestone. Loading and linking are otherwise separated from
  evaluation: a graph that names a module it cannot load, or a name nothing exports, fails before
  any module body runs.
- **Resource limits bound data blocks, not the heap.**
  `JSRuntimeOptions.setMaxMemoryUsage(long)` counts every byte allocated for an `ArrayBuffer` or
  `SharedArrayBuffer` and refuses an allocation past the ceiling with a catchable `RangeError`.
  Objects, arrays, strings and bytecode are ordinary Java allocations bounded by `-Xmx`.
  `setMaxStackSize(long)` bounds the interpreter's call depth, not the JVM's own stack.
- **`WeakMap`/`WeakSet` are ephemeron-correct but not enumerable**, as the specification requires;
  a collection that dies while its keys live leaves entries to be pruned lazily.

See [ASYNC_AWAIT_ENHANCEMENTS.md](docs/migration/ASYNC_AWAIT_ENHANCEMENTS.md) for async/await implementation details.

## Documentation

- **[Features](docs/migration/FEATURES.md)**: Complete list of implemented JavaScript features
- **[Migration Status](docs/migration/MIGRATION_STATUS.md)**: Migration progress from QuickJS C to Java
- **[Async/Await](docs/migration/ASYNC_AWAIT_ENHANCEMENTS.md)**: Async/await and iteration implementation

## Installation

The snippets below use **0.1.1**, the latest release on Maven Central. The version in
`build.gradle.kts` is the *next* version under development and is not published until it is tagged.

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

Test JVM: `./gradlew test -PtestJavaVersion=21` runs the suite on JDK 21 instead of 17, and
`-PtestJavaVersion=25` on JDK 25. Running the engine — not just launching Gradle — on each release
matters, because their behaviour differs: JDK 25 withdrew the atomic access modes from byte-array
view `VarHandle`s, which `Atomics` depends on.

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
- Ephemeron-correct `WeakMap`/`WeakSet`: entries live on the key, so a value cannot keep its own
  key alive

## License

Apache License 2.0 - see [LICENSE](LICENSE) file for details.
