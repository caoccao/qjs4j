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
./gradlew slowRegressionTest      # Slow functional regressions: @Tag("performance") minus @Tag("benchmark")
./gradlew performanceTest         # The above *plus* the JMH benchmarks — every @Tag("performance") case
./gradlew test262Quick            # Quick Test262 subset
./gradlew test262                 # Full Test262 suite (requires ../test262, -Xmx2g)
./gradlew test262LongRunning      # RegExp / URI / staging-Date selection excluded from the quick subset
./gradlew test262Language         # Language tests only
```

`performance` covers two different kinds of case. `slowRegressionTest` is the correctness half —
the end-to-end Octane v7 regression for issue 7 and the Temporal hot-path assertions, which pass or
fail and are worth running anywhere; `check` depends on it, so `./gradlew build` runs it. The other
half is the JMH benchmarks, tagged `benchmark`, which report a number that only means something on a
quiet machine: `performanceTest` runs both and takes about ninety seconds, almost all of it JMH.

Every `test262*` task pins the two things a conformance count depends on, so the commands above
mean the same thing on every machine:

- **Suite revision.** `test262-revision.txt` holds the revision `../test262` must be at. The runner
  refuses to start against any other one; against a checkout whose revision it cannot read — an
  export without its Git metadata, for instance — because a count for a suite nothing can identify
  is not a count for the pinned suite; against a checkout at the pinned revision that has been
  edited since, because discovery walks the working tree rather than the commit, so one untracked
  `.js` file changes the total and one edited harness file changes thousands of outcomes; and
  against no pin at all, so the one file that defines what reproducible means here cannot turn the
  check off by going missing. Cleanliness is the one premise that asks `git` rather than reading a
  file, and not being able to ask is refused like any other premise that cannot be established.
  Pass `-Ptest262AllowAnyRevision=true` to run against upstream tip, an archive, or a checkout with
  local edits, deliberately.
- **Time zone.** The tasks run in `UTC`. The suite reads the host's zone, and the pinned harness
  rejects the identifier `Etc/UTC` that many Linux hosts use — so without this the same code gave
  different answers on different machines.

Every run prints both before it discovers anything, so a pass count copied out of a log or a CI
artifact carries its own provenance:

```text
Test262 revision: 5c8206929d81b2d3d727ca6aac56c18358c8d790 (pinned)
Test262 time zone: UTC
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
│                            plus the package-private collaborators JSContext delegates to
│                            (JSValueFactory, JSErrorReporter, RealmIntrinsics, EvalRunner,
│                            ModuleSourceTransformer/Linker/Loader, …)
├── vm/                    ← VirtualMachine, Opcode, StackFrame, CallStack
├── builtins/              ← Constructor + Prototype pairs (ArrayConstructor/ArrayPrototype, etc.)
├── exceptions/            ← JSCompilerException, JSSyntaxErrorException, JSTypeErrorException,
│                            JSRangeErrorException, JSVirtualMachineException, JSErrorException
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

`JSContext` itself holds only realm identity — runtime, global object, call stack, `this`,
`new.target`, pending exception, strict mode, lifecycle, microtask entry points — and delegates
everything else to package-private collaborators in the same package, the way
`BytecodeCompiler` delegates to `compilation/compiler`. Its public API is unchanged: every
`context.createJSXxx(...)` / `context.throwXxx(...)` / `context.eval(...)` is a one-line
delegation, so builtins, the VM and the tests are unaffected.

| Collaborator | Owns |
|---|---|
| `JSValueFactory` | the ~60 `createJSXxx` allocators, with prototypes attached |
| `JSErrorReporter` | the `throwXxx` family and stack-trace capture (QuickJS `JS_ThrowError2`) |
| `RealmIntrinsics` | cached/hidden prototypes, iterator prototypes, `%ThrowTypeError%`, `getPrototypeFromConstructor`, `getFunctionRealm` |
| `GlobalLexicalScope` | global `let`/`const` bindings and the declaration tables (QuickJS `global_var_obj`) |
| `EvalOverlayManager` | the temporary global-object overlays a module's imports are installed as |
| `RegExpLegacyStatics` | `RegExp.input` / `.lastMatch` / `.$1`–`.$9` |
| `EvalRunner` | the eval pipeline (QuickJS `JS_EvalInternal`); `EvalActivation` runs it phase by phase |
| `ModuleSourceTransformer` | the textual module rewrite, all `MODULE_*` patterns, identifier/string decoding |
| `ModuleLinker` | the link-before-evaluate pass, ResolveExport, import/export token readers |
| `ModuleLoader` | module cache, specifier resolution, JSON/text/bytes payloads, async evaluation ordering |
| `ImportBindingInstaller` | installing import bindings and namespace exports where running code sees them |

Collaborators hold a `JSContext` reference and are built in dependency order in the constructor;
where two of them need each other (the transformer and linker call back into the loader), the
later one is reached through a package-private accessor on the context rather than injected.

#### Virtual Machine (vm/)
- **VirtualMachine**: Stack-based bytecode interpreter
- **Opcode**: Enumeration of the bytecode instruction set
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

### Reference material

- QuickJS reference source: `../quickjs` (especially `quickjs.c` and `quickjs-opcode.h`).
- Test262 conformance suite: `../test262`.
- Migration work is tracked in `docs/migration/` (`TODO.md`, `FEATURES.md`, `MIGRATION_STATUS.md`).
- Goal: preserve QuickJS semantics while staying consistent with existing qjs4j architecture.

### Core rule: follow QuickJS

1. Confirm behavior from QuickJS source.
2. Validate with `../quickjs/qjs` or `../quickjs/qjs -m` when useful.
3. Port semantics, not just syntax.
4. If qjs4j diverges from QuickJS, prefer fixing qjs4j.
5. Do not introduce new opcodes unless explicitly required; use the existing set in `Opcode`.

### Translation notes

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
- **Microtask Queue**: `processMicrotasks()` drains the queue until it is empty. A nested call returns immediately and drains nothing — the outer drain still owns the queue (`isProcessing()` distinguishes the two). The host interrupt and execution deadline are polled during the drain so a microtask that re-enqueues itself cannot loop forever. A failure escaping a microtask is recorded via `JSContext.recordMicrotaskFailure`.

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
- `docs/migration/TEST262_PLAN.md` — Test262 runner design (a plan, not results). Conformance
  results are not checked in; run `./gradlew test262` or see the CI `test262-quick-log` artifact.

## Main Entry Point

`com.caoccao.qjs4j.cli.QuickJSInterpreter` (configured as mainClass in `build.gradle.kts`).
