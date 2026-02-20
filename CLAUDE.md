# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

qjs4j is a **native Java implementation of QuickJS** — a complete reimplementation of the QuickJS JavaScript engine in pure Java (JDK 17+, zero runtime dependencies). This is NOT a wrapper or binding to C code, but a faithful translation of the QuickJS C implementation into Java.

The project implements ES2024 features with full QuickJS specification compliance, including modern JavaScript features like async/await, for-of/for-await-of loops, promises, modules, generators, proxies, and typed arrays.

## Common Commands

### Build & Test
```bash
./gradlew build                   # Build
./gradlew test                    # All tests (excludes @Tag("performance"))
./gradlew clean test              # Clean + test
./gradlew compileJava             # Compile only (fast check)
./gradlew compileTestJava         # Compile tests
```

### Running a Single Test
```bash
./gradlew test --tests "com.caoccao.qjs4j.core.JSContextTest"                 # Specific class
./gradlew test --tests "com.caoccao.qjs4j.core.JSContextTest.testEvalSimpleExpression"  # Specific method
./gradlew test --tests "*AsyncTest"                                            # Pattern match
```

### Performance & Conformance Testing
```bash
./gradlew performanceTest         # JMH benchmarks (@Tag("performance"))
./gradlew test262Quick            # Quick Test262 subset
./gradlew test262                 # Full Test262 suite (requires ../test262, -Xmx2g)
./gradlew test262Language         # Language tests only
```

## Architecture Overview

### Package Structure

```
com.caoccao.qjs4j/
├── compilation/           ← Frontend pipeline
│   ├── ast/               Records implementing sealed interfaces (Expression, Statement, etc.)
│   ├── compiler/          Compiler (entry), BytecodeCompiler, CompilerDelegates, EmitHelpers
│   ├── lexer/             Lexer, LexerTemplateScanner
│   └── parser/            Parser, ParserDelegates, Expression*Parser, StatementParser
├── core/                  ← JSContext, JSRuntime, JSGlobalObject, JSValue hierarchy
├── vm/                    ← VirtualMachine, Opcode (262 opcodes), StackFrame, CallStack
├── builtins/              ← Constructor + Prototype pairs (ArrayConstructor/ArrayPrototype, etc.)
├── exceptions/            ← JSCompilerException, JSSyntaxErrorException, JSTypeErrorException,
│                            JSRangeErrorException, JSVirtualMachineException, JSErrorException
├── memory/                ← GarbageCollector, HeapManager, MemoryPool, ReferenceCounter
├── regexp/                ← RegExp engine
├── unicode/               ← Unicode data and normalization
├── utils/                 ← AtomTable, DtoaConverter, Float16
└── cli/                   ← QuickJSInterpreter (main class), REPL
```

### Key File Paths

| Component | Path |
|-----------|------|
| Compiler entry | `src/main/java/com/caoccao/qjs4j/compilation/compiler/Compiler.java` |
| Lexer | `src/main/java/com/caoccao/qjs4j/compilation/lexer/Lexer.java` |
| Parser | `src/main/java/com/caoccao/qjs4j/compilation/parser/Parser.java` |
| BytecodeCompiler | `src/main/java/com/caoccao/qjs4j/compilation/compiler/BytecodeCompiler.java` |
| BytecodeEmitter | `src/main/java/com/caoccao/qjs4j/compilation/compiler/BytecodeEmitter.java` |
| VirtualMachine | `src/main/java/com/caoccao/qjs4j/vm/VirtualMachine.java` |
| Opcode | `src/main/java/com/caoccao/qjs4j/vm/Opcode.java` |
| JSContext | `src/main/java/com/caoccao/qjs4j/core/JSContext.java` |
| JSRuntime | `src/main/java/com/caoccao/qjs4j/core/JSRuntime.java` |
| JSGlobalObject | `src/main/java/com/caoccao/qjs4j/core/JSGlobalObject.java` |

### Core Architecture Principles

1. **QuickJS Fidelity**: Follows QuickJS's design decisions closely — shape-based optimization, bytecode instruction set matching (with renumbering), stack-based VM, atom table for string interning, microtask queue for promises.

2. **Pure Java**: Zero native dependencies. All JavaScript semantics implemented in Java: IEEE 754 arithmetic, BigInt, SameValueZero equality, complete iterator/async iterator protocols.

3. **Modern JavaScript**: Full ES2015-ES2024 support — async/await, for-of/for-await-of, ES6 modules with dynamic import(), generators, Proxy/Reflect, typed arrays.

### Key Components

#### JSContext & JSRuntime
- **JSRuntime**: Shared runtime environment (atom table, module cache, job queue)
- **JSContext**: Independent execution context with its own global object, call stack, and exception state
- Multiple contexts can share a runtime (isolated globals, shared resources)
- Implements AutoCloseable for proper resource cleanup

#### Virtual Machine (vm/)
- **VirtualMachine**: Stack-based bytecode interpreter
- **Opcode**: Enumeration of 262 bytecode instructions
- **StackFrame**: Call stack frame with locals, arguments, and return address
- **CallStack**: Value stack for operand manipulation
- Critical opcodes: `RETURN_ASYNC` (async return), `FOR_OF_START/NEXT` (sync iteration), `FOR_AWAIT_OF_START/NEXT` (async iteration), `CALL`, `NEW`, `GET_FIELD`, `SET_FIELD`

#### Compiler (compilation/)
- **Parser**: Recursive descent parser producing AST
- **Lexer**: Tokenizer for JavaScript source
- **BytecodeCompiler**: AST → bytecode transformation
- **BytecodeEmitter**: Bytecode generation and jump patching
- Compiler tracks context (async function, strict mode, loop depth) to emit appropriate opcodes
- Delegate pattern splits logic: `ExpressionCompiler`, `StatementCompiler`, `FunctionClassCompiler`, `PatternCompiler`, etc.

#### Built-ins (builtins/)
Each built-in object has a Constructor + Prototype pair (e.g., `ArrayConstructor`/`ArrayPrototype`), following ES2024 specification.

#### Type System
- **JSValue**: Base class (sealed hierarchy) — `JSPrimitive` (null, undefined, boolean, number, string, symbol, bigint) and `JSObject` (objects, arrays, functions, typed arrays)
- **JSFunction**: `JSBytecodeFunction` (compiled), `JSNativeFunction` (Java bindings), `JSAsyncFunction` (promise-returning)

#### Exception Hierarchy
```
RuntimeException
├── JSException              (wraps JS error values thrown from eval)
├── JSErrorException         (base for spec-defined JS errors)
│   ├── JSSyntaxErrorException
│   ├── JSTypeErrorException
│   └── JSRangeErrorException
├── JSCompilerException      (compilation/parse errors)
└── JSVirtualMachineException (VM execution errors)
```

## Testing Guidelines

### Test Stack
JDK 17+. JUnit 6 (6.0.1) + AssertJ + Javet (V8 parity comparison) + JSON-unit-assertj.

### Test Base Classes

**`BaseJavetTest`** (preferred) — runs code in both V8/Javet and qjs4j, auto-tests strict mode:
```java
public class MyFeatureTest extends BaseJavetTest {
    @Test void testFeature() {
        assertStringWithJavet("'hello'.toUpperCase()");
    }
}
```
Methods: `assertStringWithJavet()`, `assertIntegerWithJavet()`, `assertBooleanWithJavet()`, `assertDoubleWithJavet()`, `assertErrorWithJavet()`, `assertUndefinedWithJavet()`, `assertObjectWithJavet()`, `assertBigIntegerWithJavet()`, `assertLongWithJavet()`. Set `moduleMode = true` for ES module tests.

**`BaseTest`** — for internal assertions not needing V8 parity:
```java
try (JSContext context = new JSContext(new JSRuntime())) {
    JSValue result = context.eval("2 + 2");
    assertThat(result.toString()).isEqualTo("4");
}
```
Provides `assertError()`, `assertSyntaxError()`, `assertTypeError()`, `awaitPromise()`.

For async code, always call `context.processMicrotasks()` to settle promises.

### Test Organization
| Area | Location |
|------|----------|
| Compiler/AST | `src/test/java/com/caoccao/qjs4j/compilation/ast/` |
| Lexer | `src/test/java/com/caoccao/qjs4j/compilation/lexer/` |
| Built-ins | `src/test/java/com/caoccao/qjs4j/builtins/` |
| Core/runtime | `src/test/java/com/caoccao/qjs4j/core/` |
| VM/opcodes | `src/test/java/com/caoccao/qjs4j/vm/` |
| RegExp | `src/test/java/com/caoccao/qjs4j/regexp/` |
| Test262 | `src/test/java/com/caoccao/qjs4j/test262/` |

## Migration from QuickJS C

When porting QuickJS C code to Java:

1. **Opcode Mapping**: QuickJS opcode numbers differ from qjs4j. Use semantic matching, not numeric matching.
2. **Stack Signatures**: Preserve the stack effect `(nargs, npop, npush)` from QuickJS.
3. **Type Conversion**: `JSValue*` → `JSValue`, `JSAtom` (int) → `String` or `Atom`, C unions → sealed classes / `instanceof`.
4. **Error Handling**: C return codes (`-1`) → throw `JSException` or `JSVirtualMachineException`. QuickJS `JS_ThrowXxx()` → set `pendingException` in context.
5. **Memory Management**: Java GC replaces manual memory. `JS_FreeValue()`/`JS_DupValue()` not needed.

## Important Implementation Details

- **Primitive Auto-Boxing**: VM auto-boxes primitives (strings, numbers) when accessing properties or iterating via `toObject()`.
- **Strict Mode Tracking**: Each function has a `strict` flag set by the compiler; the VM saves/restores it on function entry/exit.
- **Exception Propagation**: Exceptions stored in `context.pendingException`, checked after each opcode. Catch handlers clear the exception and jump.
- **Microtask Queue**: `processMicrotasks()` drains the queue with re-entrancy depth tracking to prevent infinite loops.

## Code Style

- **License header**: Apache 2.0 (`Copyright (c) 2025-2026. caoccao.com Sam Cao`) on `src/main/` files.
- **Java 17+ features**: Use records, sealed classes, pattern matching where appropriate.
- **4-space indentation**, no tabs. Prefer `final` classes where the project does.
- **AST nodes**: Records with `SourceLocation location` implementing sealed interfaces — see `Identifier.java`, `ForOfStatement.java`.
- **QuickJS alignment**: When in doubt, follow QuickJS's approach (check `quickjs.c`).
- **Null safety**: Use explicit null checks, avoid returning null (return `JSUndefined.INSTANCE` or throw).
- **Stack shapes**: Document `Stack: ...` in compiler/VM code.

## Documentation References

- `docs/migration/TODO.md` — Pending work items
- `docs/migration/FEATURES.md` — Complete feature matrix
- `docs/migration/MIGRATION_STATUS.md` — Overall migration progress
- `docs/migration/OPCODE_IMPLEMENTATION_STATUS.md` — Bytecode instruction coverage
- `docs/migration/TEST262.md` — ECMAScript conformance status

## Main Entry Point

`com.caoccao.qjs4j.cli.QuickJSInterpreter` (configured as mainClass in `build.gradle.kts`).
