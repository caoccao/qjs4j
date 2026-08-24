# Release Notes

## 0.2.0

### Compiler Diagnostics

- `JSCompilerException` now carries the readonly offending AST node via `getAst()` when available, and `JSSyntaxErrorException` carries a readonly source location. Lexer, parser, and compiler locations are preserved on the internal JavaScript error value, so every `JSException` wrapper exposes the readonly line, column, start offset, and end offset via `getSourceLocation()` without exposing the AST, including across `JSContext.eval()`, nested evaluation, function construction, ShadowRealm evaluation, and module paths.

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
