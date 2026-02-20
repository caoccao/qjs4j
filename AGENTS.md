# AGENTS.md

Guidance for coding agents working in this repository.

## Project Context

- `qjs4j` is a **pure Java** reimplementation of QuickJS (JDK 17+, zero runtime dependencies).
- Migration work is tracked in `docs/migration/` (`TODO.md`, `FEATURES.md`, `MIGRATION_STATUS.md`).
- QuickJS reference source: `../quickjs` (especially `quickjs.c` and `quickjs-opcode.h`).
- Test262 conformance suite: `../test262`.
- Goal: preserve QuickJS semantics while staying consistent with existing qjs4j architecture.

## Core Rule: Follow QuickJS

1. Confirm behavior from QuickJS source.
2. Validate with `../quickjs/qjs` or `../quickjs/qjs -m` when useful.
3. Port semantics, not just syntax.
4. If qjs4j diverges from QuickJS, prefer fixing qjs4j.
5. Do not introduce new opcodes unless explicitly required; use the existing 262-opcode set.

## Architecture

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
├── types/                 ← JSModule, JSPromise, ModuleLoader, DynamicImport
├── exceptions/            ← JSCompilerException, JSSyntaxErrorException, JSTypeErrorException,
│                            JSRangeErrorException, JSVirtualMachineException, JSErrorException
├── memory/                ← GarbageCollector, HeapManager, MemoryPool, ReferenceCounter
├── regexp/                ← RegExp engine
├── unicode/               ← Unicode data and normalization
├── utils/                 ← AtomTable, DtoaConverter, Float16
└── cli/                   ← QuickJSInterpreter (main class), REPL
```

Key entry points:

| Component | Path |
|-----------|------|
| Compiler entry | `src/main/java/com/caoccao/qjs4j/compilation/compiler/Compiler.java` |
| Lexer | `src/main/java/com/caoccao/qjs4j/compilation/lexer/Lexer.java` |
| Parser | `src/main/java/com/caoccao/qjs4j/compilation/parser/Parser.java` |
| BytecodeCompiler | `src/main/java/com/caoccao/qjs4j/compilation/compiler/BytecodeCompiler.java` |
| VirtualMachine | `src/main/java/com/caoccao/qjs4j/vm/VirtualMachine.java` |
| Opcode | `src/main/java/com/caoccao/qjs4j/vm/Opcode.java` |
| JSContext | `src/main/java/com/caoccao/qjs4j/core/JSContext.java` |

## Coding Style

- Apache 2.0 license header (`Copyright (c) 2025-2026. caoccao.com Sam Cao`) on `src/main/` files.
- 4-space indentation, no tabs. Prefer `final` classes where the project does.
- AST nodes are records with `SourceLocation location` implementing sealed interfaces — see `Identifier.java` or `ForOfStatement.java`.
- Document stack shapes (`Stack: ...`) in compiler/VM code.
- Use project exception types: `JSSyntaxErrorException`, `JSTypeErrorException`, `JSRangeErrorException`, `JSCompilerException`, `JSVirtualMachineException`.

## Build and Test

```bash
./gradlew test                    # All tests (excludes @Tag("performance"))
./gradlew test --tests "com.caoccao.qjs4j.compilation.ast.ClassCompilerTest"  # Single class
./gradlew test --tests "*.ForOfStatementTest.testArrayForOf"                  # Single method
./gradlew compileJava             # Compile only (fast check)
./gradlew compileTestJava         # Compile tests
./gradlew test262Quick            # Quick Test262 subset
./gradlew test262                 # Full Test262 (requires ../test262, -Xmx2g)
./gradlew performanceTest         # JMH benchmarks (@Tag("performance"))
```

JDK 17+. Test stack: JUnit 6 + AssertJ + Javet (V8 parity).

## Testing Conventions

**Prefer `BaseJavetTest`** for behavioral tests — runs code in both V8 and qjs4j, auto-tests strict mode:
```java
public class MyFeatureTest extends BaseJavetTest {
    @Test void testFeature() {
        assertStringWithJavet("'hello'.toUpperCase()");
    }
}
```
Methods: `assertStringWithJavet()`, `assertIntegerWithJavet()`, `assertBooleanWithJavet()`, `assertDoubleWithJavet()`, `assertErrorWithJavet()`, `assertUndefinedWithJavet()`, `assertObjectWithJavet()`. Set `moduleMode = true` for ES module tests.

**Use `BaseTest`** for internal assertions not needing V8 parity. Provides `assertError()`, `assertSyntaxError()`, `assertTypeError()`, `awaitPromise()`.

Test locations: `compilation/ast/` (compiler), `builtins/` (built-ins), `core/` (runtime), `vm/` (opcodes), `regexp/` (regex).

## Migration Workflow

1. Read `docs/migration/TODO.md` and related docs.
2. Confirm QuickJS behavior (source + executable).
3. Implement in parser/compiler/vm/core.
4. Add/adjust tests.
5. Run targeted tests, then `./gradlew test`.
6. Update `docs/migration/TODO.md` and `FEATURES.md`.

## Pre-Completion Checklist

- [ ] QuickJS behavior checked for the changed feature.
- [ ] Parser/compiler/vm changes are internally consistent.
- [ ] Tests added or updated under the relevant package.
- [ ] `./gradlew test` passes.
- [ ] Migration docs updated to reflect the new status.
