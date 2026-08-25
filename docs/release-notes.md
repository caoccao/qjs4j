# Release Notes

## 0.2.0

### Compiler Diagnostics

- `JSCompilerException` now carries the readonly offending AST node via `getAst()` when available, and `JSSyntaxErrorException` carries a readonly source location. Lexer, parser, and compiler locations are preserved on the internal JavaScript error value, so every `JSException` wrapper exposes the readonly line, column, start offset, and end offset via `getSourceLocation()` without exposing the AST, including across `JSContext.eval()`, nested evaluation, function construction, ShadowRealm evaluation, and module paths.

### Error Handling

- Errors raised by the engine — temporal dead zone, `ArrayBuffer`/`TypedArray`/`DataView` range errors, revoked-proxy and private-field type errors — are now catchable by the script's own `try`/`catch`.
- Engine-internal failures report a diagnosable message instead of `VM error: null`.
- Error messages match V8: `Cannot access 'x' before initialization`, `Cannot read properties of null (reading 'foo')`.
- Building an error message no longer runs user getters or proxy traps — including for a thrown `Proxy`, and including the stack the CLI prints.
- Exceptions thrown from a microtask are recorded instead of silently discarded.
- Host termination — an execution deadline or `JSRuntime.requestInterrupt()` — now escapes promise executors, `.then()` handlers, async functions, async generators and disposal, as `JSTerminationException`. Previously it could be demoted to a rejected promise and execution continued.

### Runtime Correctness

- Fixed the Octane v7 failures from [issue #7](https://github.com/caoccao/qjs4j/issues/7): abstract equality no longer coerces ordinary objects when comparing them with `null` or `undefined`, and RegExp literals and species operations keep using the realm intrinsics when the global `RegExp` binding is replaced.
- Object spread and rest create data properties, so a setter inherited from `Object.prototype` no longer intercepts `{...src}`.
- `Object.getOwnPropertyNames()` works on a Proxy wrapping a frozen or sealed array.
- `getOwnPropertyDescriptor()` returns a copy, so callers can no longer mutate an object's stored attributes.
- `break` and `continue` accept contextual-keyword labels such as `of`, `as` and `from`.
- Cyclic and very deep prototype chains raise `RangeError` instead of exhausting the stack.
- `Object.setPrototypeOf()` rejects a cycle at any chain depth, not only within the first 1,000 links.
- An indexed array write runs an inherited setter at any prototype depth.
- `'xx'.repeat(1e20)` raises a catchable `RangeError` instead of failing inside the engine.

### Resource Limits

- Catastrophic RegExp backtracking raises `RangeError` instead of hanging. Tunable via `JSRuntimeOptions.setRegExpBacktrackLimit(long)`.
- Oversized strings raise `RangeError` instead of `OutOfMemoryError`.
- `JSRuntime.requestInterrupt()` stops a runaway script from another thread.
- The RegExp backtracking stack has a memory ceiling, not just a step count, so a capture-heavy pattern cannot claim the heap.
- A pattern may declare at most 254 capture groups, matching QuickJS. Beyond that is a `SyntaxError`.

### Embedder API

- `JSObject.set(int, JSValue)` honours frozen, sealed and non-extensible objects at every index.
- Array creation, `toArray()` and `setLength()` raise `JSRangeErrorException` instead of Java runtime exceptions.
- `JSContext.close()` releases the realm — including the global object, declaration tables and host callbacks — and is idempotent; every `eval()` overload on a closed context fails fast, before running anything.
- `createJSArray()` rejects a length outside `[0, 2^32 - 1]` instead of building an array that reports it.
- `JSArrayBuffer.resize()`/`transfer()` raise `TypeError` for a detached or non-resizable buffer and `RangeError` for an invalid length, and their `@throws` declarations now match.
- Exotic `JSObject` subclasses override `getOwnPropertyDescriptorRaw` and `has(key, depth)`; the public views over them are `final`, so an out-of-date override is a compile error rather than a silent behaviour change.
- `JSRuntime` documents its threading contract: one context per thread.

### Performance

- Compiling large functions is no longer quadratic — an 8,000-branch function compiles 5x faster.
- Array, RegExp and tagged-template literals are no longer retained for the lifetime of the VM.

### CLI

- `QuickJSInterpreter` gains `--module`, `--eval` and `--help`, exposes `scriptArgs`, and reports uncaught errors with a stack trace and a non-zero exit code.

### Build and Tooling

- A Gradle toolchain pins compilation to JDK 17. The wrapper is Gradle 9.4.1, so the build can be launched from any JDK up to 25.
- Compiler lint is enforced with `-Werror`; Javadoc lint enabled; JaCoCo now verifies coverage rather than only reporting it.
- CI runs Test262 with a failure-preserving pipeline, and runs the test suite on JDK 17 and 21 rather than compiling for 17 in both.

## 0.1.1

Initial release of qjs4j — a native Java implementation of QuickJS (JDK 17+, zero runtime dependencies).

### Core Language

- Primitive types: String, Number, Boolean, null, undefined, Symbol, BigInt
- Objects, Arrays, Functions (regular, arrow, rest/default parameters)
- All operators including optional chaining (`?.`), nullish coalescing (`??`), logical assignment (`&&=`, `||=`, `??=`)
- Control flow: if/else, switch, for, while, do-while, for-in, for-of, labels
- Destructuring, spread operator, template literals (including tagged templates)
- ES6 classes with inheritance, static members, getters/setters, private fields, static blocks, public fields

### Built-in Objects

- Object, Array, String, Number, Boolean, Date, Math, JSON, RegExp
- Map, Set, WeakMap, WeakSet
- ArrayBuffer, DataView, TypedArrays (Int8 through Float64), Float16Array
- SharedArrayBuffer, Atomics
- Error hierarchy: Error, TypeError, RangeError, SyntaxError, ReferenceError, URIError
- Intl: DateTimeFormat, NumberFormat, Collator, PluralRules, RelativeTimeFormat, ListFormat, Locale
- Proxy, Reflect

### Async Infrastructure

- Promises with all static methods (all, race, allSettled, any, resolve, reject, withResolvers)
- async/await, async generators, for-await-of
- Microtask queue with re-entrancy protection
- Iterator and async iterator protocols with helper methods

### ES6 Module System

- Named/default/namespace imports and exports, re-exports
- Dynamic `import()`, circular dependency handling, module caching

### ES2020–ES2024

- BigInt, optional chaining, nullish coalescing (ES2020)
- String.prototype.replaceAll, Promise.any, WeakRef, FinalizationRegistry (ES2021)
- Array/String.prototype.at, Object.hasOwn (ES2022)
- Array findLast/findLastIndex, toReversed/toSorted/toSpliced/with (ES2023)
- Promise.withResolvers, Object.groupBy, Map.groupBy, ArrayBuffer.detach, `using`/`await using` with DisposableStack (ES2024)
- ShadowRealm (TC39 proposal, opt-in via JSRuntimeOptions)
- Iterator helpers: drop, filter, flatMap, map, take, every, find, forEach, some, reduce, toArray
- Iterator.from, Iterator.concat

### RegExp

- Flags: g, i, m, s, u, y, d, v
- Named capture groups, match indices, Unicode property escapes, lookbehind assertions

### Tooling

- CLI REPL via `QuickJSInterpreter`
- Test262 ECMAScript conformance runner
