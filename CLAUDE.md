# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

qjs4j is a **native Java implementation of QuickJS** - a complete reimplementation of the QuickJS JavaScript engine in pure Java (JDK 17+, zero external dependencies). This is NOT a wrapper or binding to C code, but a faithful translation of the QuickJS C implementation into Java.

The project implements ES2024 features with full QuickJS specification compliance, including modern JavaScript features like async/await, for-of/for-await-of loops, promises, modules, generators, proxies, and typed arrays.

## Common Commands

### Build & Test
```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Compile Java source only
./gradlew compileJava

# Compile test source
./gradlew compileTestJava

# Run tests after clean
./gradlew clean test
```

### Running a Single Test
```bash
# Run a specific test class
./gradlew test --tests "com.caoccao.qjs4j.core.JSContextTest"

# Run a specific test method
./gradlew test --tests "com.caoccao.qjs4j.core.JSContextTest.testEvalSimpleExpression"

# Run tests matching a pattern
./gradlew test --tests "*AsyncTest"
```

### Code Quality
```bash
# Apply code formatting (if Spotless is configured)
./gradlew spotlessApply
```

### Performance & Conformance Testing
```bash
# Run JMH performance benchmarks (excluded from regular tests)
./gradlew performanceTest

# Run full Test262 ECMAScript conformance suite (requires ../test262)
./gradlew test262

# Run quick subset of Test262 for validation
./gradlew test262Quick

# Run Test262 language tests only
./gradlew test262Language
```

### Other Useful Commands
```bash
# Generate Javadoc
./gradlew javadoc

# Install to local Maven repository
./gradlew publishToMavenLocal

# View all available tasks
./gradlew tasks --all
```

## Architecture Overview

### High-Level Design

qjs4j follows the **QuickJS C implementation architecture** closely, translating C structures and algorithms into idiomatic Java. The codebase is organized into modular packages that mirror QuickJS's internal organization:

```
com.caoccao.qjs4j/
├── core/          - Runtime components (JSContext, JSRuntime, JSValue hierarchy)
├── vm/            - Virtual machine (bytecode interpreter, stack management, opcodes)
├── compiler/      - Frontend (parser, lexer, AST, bytecode compiler)
├── builtins/      - JavaScript built-in objects and prototype methods
├── types/         - Specialized types (JSModule, JSIterator, JSGenerator, etc.)
├── memory/        - Memory management (atom table, garbage collection helpers)
├── exceptions/    - Exception hierarchy
├── regexp/        - Regular expression engine
├── unicode/       - Unicode handling utilities
└── utils/         - General utilities
```

### Core Architecture Principles

1. **QuickJS Fidelity**: The implementation closely follows QuickJS's design decisions, including:
   - Shape-based optimization with hidden classes for property access
   - Bytecode instruction set matching QuickJS opcodes (with renumbering)
   - Stack-based virtual machine architecture
   - Atom table for string interning
   - Microtask queue for promise resolution

2. **Pure Java**: Zero native dependencies. All JavaScript semantics are implemented in Java, including:
   - IEEE 754 floating-point arithmetic (including Float16Array)
   - BigInt arbitrary-precision integers
   - Proper SameValueZero equality for Map/Set
   - Complete iterator and async iterator protocols

3. **Modern JavaScript**: Full ES2015-ES2024 support including:
   - Async/await with dedicated RETURN_ASYNC opcode
   - for-of and for-await-of loops
   - ES6 modules with dynamic import()
   - Promises with microtask queue
   - Generators and async generators
   - Proxy and Reflect
   - Typed arrays (including qjs4j-exclusive Float16Array)

### Key Components

#### JSContext & JSRuntime
- **JSRuntime**: Shared runtime environment (atom table, module cache, job queue)
- **JSContext**: Independent execution context with its own global object, call stack, and exception state
- Multiple contexts can share a runtime (isolated globals, shared resources)
- Implements AutoCloseable for proper resource cleanup

#### Virtual Machine (vm/)
- **VirtualMachine**: Stack-based bytecode interpreter
- **Opcode**: Enumeration of all bytecode instructions (130+ opcodes)
- **StackFrame**: Call stack frame with locals, arguments, and return address
- **CallStack**: Value stack for operand manipulation

The VM executes bytecode in a loop, dispatching on opcode type. Critical opcodes include:
- `RETURN_ASYNC` (126): Dedicated return for async functions
- `FOR_OF_START/NEXT` (129-130): Sync iteration
- `FOR_AWAIT_OF_START/NEXT` (127-128): Async iteration
- `CALL`, `NEW`, `GET_FIELD`, `SET_FIELD`: Object operations

#### Compiler (compiler/)
- **Parser**: Recursive descent parser producing AST
- **Lexer**: Tokenizer for JavaScript source
- **BytecodeCompiler**: AST → bytecode transformation
- **BytecodeEmitter**: Bytecode generation and jump patching

The compiler tracks context (async function, strict mode, loop depth) to emit appropriate opcodes.

#### Built-ins (builtins/)
Each built-in object (Array, Object, String, etc.) has:
- Constructor class (e.g., `ArrayConstructor`)
- Prototype class (e.g., `ArrayPrototype`) with all prototype methods
- Follows ES2024 specification for method behavior

#### Type System
- **JSValue**: Base class for all JavaScript values (sealed hierarchy)
  - **JSPrimitive**: null, undefined, boolean, number, string, symbol, bigint
  - **JSObject**: Objects, arrays, functions, typed arrays, etc.
- **JSFunction**: Abstract base for functions
  - **JSBytecodeFunction**: Compiled bytecode functions
  - **JSNativeFunction**: Java method bindings
  - **JSAsyncFunction**: Async functions (return promises)

### Async/Await Implementation

qjs4j implements async/await following the QuickJS model:

1. **Async functions** are compiled with `isInAsyncFunction` flag set
2. Return statements emit `RETURN_ASYNC` opcode instead of `RETURN`
3. `JSBytecodeFunction.call()` wraps return values in promises
4. Microtask queue (`JSMicrotaskQueue`) processes promise reactions
5. `context.processMicrotasks()` must be called to settle promises

**for-await-of loops**:
- Parser detects `for await (... of ...)` syntax
- Compiler emits `FOR_AWAIT_OF_START` to get async iterator
- `FOR_AWAIT_OF_NEXT` calls iterator.next() and returns promise
- Result object `{value, done}` is extracted with `GET_FIELD` opcodes
- Loop continues until `done === true`

### Module System

ES6 modules are fully supported:
- **JSModule**: Module record with state machine (unlinked → linking → linked → evaluating → evaluated)
- **Module loader**: File-based resolution with caching
- **import/export**: Named, default, namespace imports/exports
- **dynamic import()**: Returns promise resolving to module namespace

Module URLs are used for identity. Circular dependencies are handled through caching.

### Shape-Based Optimization

Following QuickJS, qjs4j uses "shapes" (hidden classes) for property access:
- Each object has a shape ID
- Shape transitions on property addition
- Inline caching for repeated property access (planned optimization)

### Memory Management

- **Atom Table**: String interning for property names and identifiers
- **WeakMap/WeakSet**: Use Java `WeakReference` and `WeakHashMap`
- **Garbage Collection**: Relies on Java GC (no manual reference counting needed)

## Testing Guidelines

### Test Organization
Tests are organized by component:
- `com.caoccao.qjs4j.core.*Test` - Core runtime tests
- `com.caoccao.qjs4j.builtins.*Test` - Built-in object tests
- `com.caoccao.qjs4j.vm.*Test` - VM and bytecode tests
- `com.caoccao.qjs4j.compilation.*Test` - Parser/compiler tests

### Test Utilities
- **BaseTest**: Base class with common test setup (extends JUnit 5)
- All tests use JUnit 5 (`@Test`, `@BeforeEach`, `@AfterEach`)
- AssertJ for fluent assertions
- JSON-unit-assertj for JSON comparison

### Testing JavaScript Code
Common pattern for testing JavaScript evaluation:
```java
try (JSContext context = new JSContext(new JSRuntime())) {
    JSValue result = context.eval("2 + 2");
    assertThat(result).isInstanceOf(JSNumber.class);
    assertThat(((JSNumber) result).getValue()).isEqualTo(4.0);
}
```

For async code, always call `context.processMicrotasks()`:
```java
try (JSContext context = new JSContext(new JSRuntime())) {
    JSValue promise = context.eval("Promise.resolve(42)");
    context.processMicrotasks();  // Settle the promise
    // Assert on promise state
}
```

## Migration from QuickJS C

When porting QuickJS C code to Java:

1. **Opcode Mapping**: QuickJS opcode numbers differ from qjs4j. Use semantic matching, not numeric matching.
   - Example: QuickJS `OP_return_async` (116) → qjs4j `RETURN_ASYNC` (126)

2. **Stack Signatures**: Preserve the stack effect `(nargs, npop, npush)` from QuickJS
   - Example: `RETURN_ASYNC(126, 1, 1, 0)` means 1 arg, pops 1, pushes 0

3. **Type Conversion**: C pointers → Java references
   - `JSValue*` → `JSValue` (reference type)
   - `JSAtom` (int) → `String` or `Atom` object
   - C unions → Java sealed classes / instanceof

4. **Error Handling**: C return codes → Java exceptions
   - QuickJS returns `-1` on error → throw `JSException` or `JSVirtualMachineException`
   - QuickJS sets `JS_ThrowXxx()` → set `pendingException` in context

5. **Memory Management**: C manual memory → Java GC
   - `JS_FreeValue()` → not needed (GC handles it)
   - `JS_DupValue()` → not needed (references are automatically managed)

## Important Implementation Details

### Primitive Auto-Boxing
JavaScript primitives (strings, numbers) are not `JSObject` instances but need to support methods like `"abc".length` or string iteration. The VM auto-boxes primitives when accessing properties or iterating:
```java
// In handleForOfStart() - auto-box primitives to access Symbol.iterator
if (iterable instanceof JSObject obj) {
    iterableObj = obj;
} else {
    iterableObj = toObject(iterable);  // Box primitive
}
```

### Strict Mode Tracking
Each function has a `strict` flag. The compiler sets this based on `"use strict"` directives. The VM saves/restores strict mode when entering/exiting functions.

### Exception Propagation
Exceptions are stored in `context.pendingException` and checked after each opcode. When caught, the VM clears the exception and jumps to the catch handler.

### Microtask Queue Re-entrancy
The microtask queue prevents infinite loops by tracking re-entrancy depth. `processMicrotasks()` drains the queue but prevents recursive queueing from running indefinitely.

## Code Style

- **Java 17+ features**: Use records, sealed classes, pattern matching where appropriate
- **Naming**: Follow Java conventions (camelCase for methods, PascalCase for classes)
- **QuickJS alignment**: When in doubt, follow QuickJS's approach (check quickjs.c for reference)
- **Null safety**: Use explicit null checks, avoid returning null (return JSUndefined.INSTANCE or throw)
- **Immutability**: Prefer immutable data structures where possible (e.g., AST nodes are immutable)

## Documentation References

For detailed feature status and implementation notes:
- **docs/migration/FEATURES.md**: Complete list of implemented JavaScript features
- **docs/migration/ASYNC_AWAIT_ENHANCEMENTS.md**: Async/await and iteration implementation details
- **docs/migration/MIGRATION_STATUS.md**: Overall migration progress from QuickJS C
- **docs/migration/OPCODE_IMPLEMENTATION_STATUS.md**: Bytecode instruction coverage
- **docs/migration/TEST262.md**: ECMAScript conformance test suite status

## Main Entry Point

The CLI interpreter is at `com.caoccao.qjs4j.cli.QuickJSInterpreter` (configured in build.gradle.kts as mainClass).
