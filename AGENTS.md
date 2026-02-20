# AGENTS.md

Guidance for coding agents working in this repository.

## Project Context

- `qjs4j` is a **pure Java** reimplementation of QuickJS (JDK 17+).
- Migration work is tracked in `docs/migration/`.
- The QuickJS reference source is in `../quickjs` (especially `../quickjs/quickjs.c` and `../quickjs/quickjs-opcode.h`).
- Goal: preserve QuickJS semantics while staying consistent with existing qjs4j architecture and tests.

## Core Rule: Follow QuickJS

When implementing or fixing language/runtime features:

1. Confirm behavior from QuickJS source.
2. When useful, validate behavior with `../quickjs/qjs` or `../quickjs/qjs -m`.
3. Port semantics, not just syntax.
4. If current qjs4j behavior diverges from QuickJS, prefer fixing qjs4j.

## Where To Look First

- Feature status and pending work: `docs/migration/TODO.md`
- High-level feature matrix: `docs/migration/FEATURES.md`
- Detailed migration notes: `docs/migration/MIGRATION_STATUS.md`, feature-specific docs in `docs/migration/`
- Compiler pipeline:
  - Lexer: `src/main/java/com/caoccao/qjs4j/compiler/Lexer.java`
  - Parser: `src/main/java/com/caoccao/qjs4j/compiler/Parser.java`
  - AST: `src/main/java/com/caoccao/qjs4j/compiler/ast/`
  - Bytecode compiler: `src/main/java/com/caoccao/qjs4j/compiler/BytecodeCompiler.java`
  - VM: `src/main/java/com/caoccao/qjs4j/vm/VirtualMachine.java`

## Coding Style Conventions

Follow existing code style in `src/main/java`:

- Keep Apache 2.0 license header on Java files.
- Use 4-space indentation, no tabs.
- Prefer `final` classes where the project does.
- Use records for AST nodes where applicable (match existing AST patterns).
- Keep methods small and explicit; favor clear control flow over clever abstractions.
- Add concise comments for non-obvious stack/bytecode behavior.
- In compiler/VM code, document stack shapes (`Stack: ...`) when relevant.
- Use project exception types (`JSSyntaxErrorException`, `JSException`, `CompilerException`, etc.) consistently.

## Migration Workflow (Expected)

For each feature task:

1. Read `docs/migration/TODO.md` entry and related migration docs.
2. Confirm QuickJS behavior (source + local quickjs executable when helpful).
3. Implement in parser/compiler/vm/core as needed.
4. Add/adjust tests.
5. Run targeted tests, then full test suite.
6. Update docs:
   - remove completed item from `docs/migration/TODO.md`
   - update status in related docs (`FEATURES.md`, feature implementation docs, etc.)

## Testing Conventions

### Commands

- Run all tests: `./gradlew test`
- Run specific tests: `./gradlew test --tests "com.caoccao.qjs4j.compilation.ast.ClassCompilerTest"`
- Compile only: `./gradlew compileJava` / `./gradlew compileTestJava`

### Test Patterns

- Most behavior parity tests should use `BaseJavetTest` (`src/test/java/com/caoccao/qjs4j/BaseJavetTest.java`):
  - `assertBooleanWithJavet()`, `assertIntegerWithJavet()`, `assertStringWithJavet()`, etc.
  - `assertErrorWithJavet()` when expecting parity with V8/Javet error behavior.
- For parser/AST structure checks, instantiate `Parser` + `Lexer` directly and assert AST shape with AssertJ.
- For module-specific behavior in tests, set `moduleMode = true`.
- Ensure edge cases are covered (syntax errors, strict mode, async/module contexts, duplicate declarations, ordering effects).

## Change Scope and Safety

- Make minimal, targeted changes.
- Do not revert unrelated user changes.
- Keep behavior-compatible refactors separate from feature logic where possible.
- If behavior is uncertain, add a failing test first (or validate with QuickJS) before broad changes.

## Pre-Completion Checklist

- [ ] QuickJS behavior checked for the changed feature.
- [ ] Parser/compiler/vm changes are internally consistent.
- [ ] Tests added or updated under the relevant package (often `src/test/java/com/caoccao/qjs4j/compiler/ast`).
- [ ] `./gradlew test` passes.
- [ ] Migration docs updated to reflect the new status.
