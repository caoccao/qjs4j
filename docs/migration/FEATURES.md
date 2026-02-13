# qjs4j Feature Implementation Status

This document provides a comprehensive list of all JavaScript features implemented in qjs4j.

## Core Language Features ✅

### Primitive Types
- **String**: Full Unicode support, string literals, template literals
- **Number**: IEEE 754 double-precision floating point
- **Boolean**: true/false values
- **null**: Null type
- **undefined**: Undefined type
- **Symbol**: Unique identifiers with well-known symbols
- **BigInt**: Arbitrary-precision integers

### Objects
- **Object creation**: Object literals, new Object(), Object.create()
- **Prototypes**: Prototype chain navigation and inheritance
- **Property descriptors**: configurable, enumerable, writable, value, get, set
- **Getters/Setters**: Accessor properties

### Arrays
- **Array creation**: Array literals, new Array(), Array.of(), Array.from()
- **Array.prototype**: All ES2023 methods including map, filter, reduce, forEach, find, findLast, etc.
- **Sparse arrays**: Proper handling of holes in arrays
- **Array-like objects**: arguments object, NodeList-style objects

### Functions
- **Regular functions**: Function declarations and expressions
- **Arrow functions**: ES2015 arrow function syntax with lexical this
- **Native function bindings**: Java methods callable from JavaScript
- **Function.prototype**: apply, call, bind
- **Rest parameters**: ...args syntax
- **Default parameters**: function(x = 10)
- **arguments object**: ✅ Array-like object containing all arguments passed to function
  - Available in regular functions (not arrow functions)
  - Supports indexed access (arguments[0], arguments[1], etc.)
  - Has length property
  - Array-like iteration support

### Operators
- **Arithmetic**: +, -, *, /, %, **
- **Logical**: &&, ||, !, ??
- **Bitwise**: &, |, ^, ~, <<, >>, >>>
- **Comparison**: ==, !=, ===, !==, <, >, <=, >=
- **Assignment**: =, +=, -=, *=, /=, %=, etc.
- **Logical assignment**: &&=, ||=, ??= (ES2021)
- **Unary**: typeof, delete, void, +, -, ++, --
- **Ternary**: condition ? true : false
- **Optional chaining**: ?. (ES2020)
- **Nullish coalescing**: ?? (ES2020)

### Control Flow
- **Conditional**: if/else, switch/case
- **Loops**: for, while, do-while, for-in, for-of
- **Jump statements**: break, continue, return, throw
- **Labels**: Labeled statements for break/continue

## ES2015+ Features ✅

### Symbol (ES2015)
- **Well-known symbols** (all 15):
  - Symbol.iterator - Default iterator
  - Symbol.asyncIterator - Async iterator (ES2018)
  - Symbol.toStringTag - Object.prototype.toString customization
  - Symbol.hasInstance - instanceof customization
  - Symbol.isConcatSpreadable - Array.prototype.concat behavior
  - Symbol.toPrimitive - Type conversion customization
  - Symbol.match - String.prototype.match behavior
  - Symbol.matchAll - String.prototype.matchAll behavior
  - Symbol.replace - String.prototype.replace behavior
  - Symbol.search - String.prototype.search behavior
  - Symbol.split - String.prototype.split behavior
  - Symbol.species - Constructor for derived objects
  - Symbol.unscopables - With statement exclusions
  - Symbol.dispose - Explicit resource cleanup hook (ES2024)
  - Symbol.asyncDispose - Async explicit resource cleanup hook (ES2024)
- **Symbol registry**: Symbol.for(), Symbol.keyFor()
- **Symbol.prototype**: toString(), valueOf(), description

### BigInt (ES2020)
- **Creation**: BigInt literals (123n), BigInt() constructor
- **Operations**: All arithmetic and bitwise operators
- **Radix conversion**: toString(2-36), binary/octal/hex parsing
- **Comparison**: Proper comparison with Number type
- **Type coercion**: Explicit conversion only (no implicit Number conversion)

### Promises (ES2015+)
- **Promise constructor**: new Promise((resolve, reject) => {...})
- **Instance methods**: then(), catch(), finally()
- **Static methods**:
  - Promise.all() - ES2015
  - Promise.race() - ES2015
  - Promise.allSettled() - ES2020
  - Promise.any() - ES2021
  - Promise.resolve() - ES2015
  - Promise.reject() - ES2015
  - Promise.try() - QuickJS extension
  - Promise.withResolvers() - ES2024
- **Microtask integration**: Proper microtask queue timing

### Iterators & Generators (ES2015)
- **Iterator protocol**: Symbol.iterator, next(), {value, done}
- **Iterator constructor**: global `Iterator`, abstract construction checks
- **Iterator static methods**: `Iterator.from()`, `Iterator.concat()`
- **Iterator helper methods**: `drop()`, `filter()`, `flatMap()`, `map()`, `take()`, `every()`, `find()`, `forEach()`, `some()`, `reduce()`, `toArray()`
- **Generator functions**: function* syntax
- **yield expressions**: yield, yield*
- **Generator methods**: next(), return(), throw()
- **Generator states**: SUSPENDED_START, SUSPENDED_YIELD, EXECUTING, COMPLETED
- **JSIterator**: Built-in iterator class with factory methods
- **Helper utilities**: toArray(), forOf(), isIterable()

### For...of loops (ES2015)
- **Sync iteration**: for (const item of iterable) ✅
- **Array iteration**: Works with arrays, strings, typed arrays
- **Collection iteration**: Works with Map, Set
- **Custom iterables**: Symbol.iterator support
- **Break/continue**: Full control flow support
- **Primitive auto-boxing**: Strings and other primitives automatically boxed

### Template Literals (ES2015)
- **String interpolation**: `Hello ${name}`
- **Multi-line strings**: Backtick strings with newlines
- **Tagged templates**: tag`template ${expr}` with per-call-site cached frozen template objects
- **Raw strings**: String.raw

### Destructuring (ES2015)
- **Array destructuring**: const [a, b] = arr
- **Object destructuring**: const {x, y} = obj
- **Rest elements**: const [first, ...rest] = arr
- **Default values**: const {x = 10} = obj
- **Nested destructuring**: const {a: {b}} = obj

### Spread Operator (ES2015)
- **Array spread**: [...arr1, ...arr2]
- **Object spread**: {...obj1, ...obj2}
- **Function calls**: fn(...args)

## Built-in Objects ✅

### Object
- **Creation**: keys(), values(), entries(), fromEntries() (ES2019), assign(), create()
- **Property definition**: defineProperty() (ES5.1), defineProperties() (ES5.1)
- **Prototype**: getPrototypeOf(), setPrototypeOf()
- **Property descriptors**: getOwnPropertyDescriptor(), getOwnPropertyDescriptors() (ES2017)
- **Property enumeration**: getOwnPropertyNames(), getOwnPropertySymbols()
- **Object testing**: is() (ES2015), isExtensible() (ES5.1), isFrozen(), isSealed()
- **Object modification**: freeze(), seal(), preventExtensions() (ES5.1)
- **ES2022+**: hasOwn() (ES2022), groupBy() (ES2024)

### Array
- **Mutating methods**: push, pop, shift, unshift, splice, reverse, sort, fill, copyWithin
- **Non-mutating methods**: slice, concat, join, flat (ES2019), flatMap (ES2019)
- **Iteration methods**: map, filter, reduce, reduceRight, forEach, every, some
- **Search methods**: find, findIndex, findLast (ES2023), findLastIndex (ES2023), indexOf, lastIndexOf, includes
- **ES2022+**: at() (ES2022)
- **ES2023 immutable methods**: toReversed(), toSorted(), toSpliced(), with()

### String
- **Character access**: charAt, charCodeAt, at() (ES2022), codePointAt
- **Substring**: substring, substr, slice
- **Search**: indexOf, lastIndexOf, search, match, matchAll
- **Modification**: replace, replaceAll (ES2021), split, concat
- **Case**: toUpperCase, toLowerCase, toLocaleUpperCase, toLocaleLowerCase
- **Trim**: trim, trimStart() (ES2019), trimEnd() (ES2019)
- **Padding**: padStart, padEnd
- **Repeat**: repeat
- **Test**: startsWith, endsWith, includes

### Number
- **Parsing**: parseInt, parseFloat
- **Numeric separators in literals**: `1_000_000`, `0b1010_1010`, `0o755`, `0xAB_CD`, `123_456n`
- **Testing**: isNaN, isFinite, isInteger, isSafeInteger
- **Formatting**: toFixed, toPrecision, toExponential, toString
- **Constants**: MAX_VALUE, MIN_VALUE, EPSILON, MAX_SAFE_INTEGER, MIN_SAFE_INTEGER

### Boolean
- **Methods**: toString(), valueOf()
- **Constructor**: Boolean() type conversion

### Date
- **Constructor**: `Date()` function behavior and `new Date(...)` argument handling aligned with QuickJS semantics
- **Static methods**: `Date.now()`, `Date.parse()`, `Date.UTC()`
- **String methods**: `toString()`, `toUTCString()`, `toGMTString()`, `toISOString()`, `toDateString()`, `toTimeString()`, `toLocaleString()`, `toLocaleDateString()`, `toLocaleTimeString()`
- **Get methods**: full local and UTC getter set (`getYear`, `getFullYear`, `getMonth`, `getDate`, `getDay`, `getHours`, `getMinutes`, `getSeconds`, `getMilliseconds`, `getTimezoneOffset` and UTC counterparts)
- **Set methods**: full local and UTC setter set (`setTime`, `setYear`, `setFullYear`, `setMonth`, `setDate`, `setHours`, `setMinutes`, `setSeconds`, `setMilliseconds` and UTC counterparts)

### Internationalization (Intl)
- **Namespace**: `Intl` global object with `getCanonicalLocales()`
- **Intl.DateTimeFormat**: constructor, `format()`, `resolvedOptions()`, `supportedLocalesOf()`
- **Intl.NumberFormat**: constructor, `format()`, `resolvedOptions()`, `supportedLocalesOf()`
- **Intl.Collator**: constructor, `compare()`, `resolvedOptions()`, `supportedLocalesOf()`
- **Intl.PluralRules**: constructor, `select()`, `resolvedOptions()`, `supportedLocalesOf()`
- **Intl.RelativeTimeFormat**: constructor, `format()`, `resolvedOptions()`, `supportedLocalesOf()`
- **Intl.ListFormat**: constructor, `format()`, `resolvedOptions()`, `supportedLocalesOf()`
- **Intl.Locale**: constructor (`new`), `toString()`, `baseName`, `language`, `script`, `region`

### RegExp
- **Construction**: /pattern/flags, new RegExp(pattern, flags)
- **Methods**: exec, test
- **String integration**: match, matchAll, search, replace, split
- **Flags**: g (global), i (ignoreCase), m (multiline), s (dotAll), u (unicode), y (sticky), d (hasIndices), v (unicodeSets)
- **Named capture groups**: `(?<name>...)`, `\\k<name>`, and `match.groups`
- **Match indices**: `d` flag with `match.indices` and `match.indices.groups`
- **Unicode property escapes**: `\\p{...}`, `\\P{...}` with `u` / `v` flags, including `gc=`, `sc=`, `Script_Extensions=`
- **Lookbehind assertions**: `(?<=...)`, `(?<!...)`

### Math
- **Constants**: E, PI, LN2, LN10, LOG2E, LOG10E, SQRT1_2, SQRT2
- **Basic**: abs, ceil, floor, round, trunc, sign
- **Exponential**: exp, log, log10, log2, log1p, expm1
- **Power**: pow, sqrt, cbrt, hypot
- **Trigonometric**: sin, cos, tan, asin, acos, atan, atan2, sinh, cosh, tanh, asinh, acosh, atanh
- **Utility**: min, max, random, clz32, imul, fround, f16round, sumPrecise

### JSON
- **Parsing**: JSON.parse() with reviver function
- **Stringification**: JSON.stringify() with replacer and space

## Collections ✅

### Map (ES2015)
- **Creation**: new Map(), new Map(iterable)
- **Methods**: set, get, has, delete, clear
- **QuickJS extensions**: getOrInsert(), getOrInsertComputed()
- **Size**: size property
- **Iteration**: keys(), values(), entries(), forEach()
- **Insertion order**: Maintains insertion order
- **SameValueZero**: Proper NaN and -0/+0 equality
- **Metadata**: `Map[Symbol.species]`, `Map.prototype[Symbol.toStringTag]`
- **ES2024**: Map.groupBy()

### Set (ES2015)
- **Creation**: new Set(), new Set(iterable)
- **Methods**: add, has, delete, clear
- **Set operations**: isDisjointFrom(), isSubsetOf(), isSupersetOf(), intersection(), difference(), symmetricDifference(), union()
- **Size**: size property
- **Iteration**: keys(), values(), entries(), forEach()
- **Insertion order**: Maintains insertion order
- **SameValueZero**: Proper NaN and -0/+0 equality
- **Metadata**: `Set[Symbol.species]`, `Set.prototype[Symbol.toStringTag]`
- **QuickJS extension**: Set.groupBy()

### WeakMap (ES2015)
- **Creation**: new WeakMap(), new WeakMap(iterable)
- **Methods**: set, get, has, delete
- **Weak references**: Automatic garbage collection
- **Object keys only**: Only accepts object keys
- **No iteration**: Cannot enumerate keys

### WeakSet (ES2015)
- **Creation**: new WeakSet(), new WeakSet(iterable)
- **Methods**: add, has, delete
- **Weak references**: Automatic garbage collection
- **Object values only**: Only accepts objects
- **No iteration**: Cannot enumerate values

## Meta-programming ✅

### Reflect (ES2015)
- **Property access**: get, set, has, deleteProperty
- **Prototype**: getPrototypeOf, setPrototypeOf
- **Property enumeration**: ownKeys
- **Function operations**: apply, construct
- **Object testing**: isExtensible, preventExtensions
- **Property definition**: defineProperty, getOwnPropertyDescriptor

### Proxy (ES2015)
- **Construction**: new Proxy(target, handler)
- **Handler traps**: get, set, has, deleteProperty, ownKeys
- **Apply trap**: Function call interception
- **Construct trap**: new operator interception
- **Revocable proxies**: Proxy.revocable()

## Binary Data ✅

### ArrayBuffer (ES2015)
- **Creation**: new ArrayBuffer(length)
- **Properties**: byteLength
- **Methods**: slice()
- **Detach**: Transfer ownership with detach() (ES2024)

### DataView (ES2015)
- **Creation**: new DataView(buffer, byteOffset, byteLength)
- **Properties**: buffer, byteLength, byteOffset
- **Methods**: getInt8, getUint8, getInt16, getUint16, getInt32, getUint32, getBigInt64, getBigUint64, getFloat16, getFloat32, getFloat64
- **Set methods**: setInt8, setUint8, setInt16, setUint16, setInt32, setUint32, setBigInt64, setBigUint64, setFloat16, setFloat32, setFloat64
- **Endianness**: Little-endian and big-endian support

### TypedArrays (ES2015)
- **Types**: Int8Array, Uint8Array, Uint8ClampedArray, Int16Array, Uint16Array, Int32Array, Uint32Array, Float32Array, Float64Array
- **Properties**: BYTES_PER_ELEMENT, buffer, byteLength, byteOffset, length
- **Methods**: All Array.prototype methods (map, filter, etc.)
- **Subarray**: subarray() for creating views

### Float16Array ⭐ (qjs4j exclusive)
- **IEEE 754 half-precision**: 16-bit floating point (1 sign, 5 exponent, 10 mantissa)
- **Range**: ±65504 (max), ±6.10×10⁻⁵ (min normal), ±5.96×10⁻⁸ (min subnormal)
- **Special values**: Infinity, NaN, signed zero
- **Methods**: All TypedArray methods
- **Conversion**: Automatic conversion to/from float32

## Error Handling ✅

### Error Types
- **Error**: Base error class
- **TypeError**: Type-related errors
- **RangeError**: Value out of range
- **SyntaxError**: Parse errors
- **ReferenceError**: Invalid reference
- **URIError**: URI handling errors

### Try-Catch
- **try blocks**: Exception handling
- **catch clauses**: Error capture with binding
- **finally blocks**: Cleanup code
- **throw statements**: Exception throwing
- **Stack traces**: Error.stack property

## Async Infrastructure ✅

### Microtask Queue
- **queueMicrotask()**: Global function to enqueue microtasks
- **Promise reactions**: Automatic microtask enqueuing for then/catch/finally
- **Re-entrancy protection**: Prevents infinite microtask loops
- **ES2020 timing**: Proper microtask processing order

### Async Iterator Protocol (ES2018)
- **Symbol.asyncIterator**: Async iteration symbol
- **JSAsyncIterator**: Promise-based iterator with next() returning Promise<{value, done}>
- **for-await-of loops**: Full support via JSAsyncIteratorHelper
- **Sync to async**: Automatic conversion of sync iterators to async
- **Factory methods**: fromArray(), fromIterator(), fromIterable(), fromPromise()
- **Utility functions**: toArray(), isAsyncIterable(), getAsyncIterator()
- **Error handling**: Promise rejection propagation

### Async Generators (ES2018)
- **JSAsyncGenerator**: Full async generator object
- **States**: SUSPENDED_START, SUSPENDED_YIELD, EXECUTING, AWAITING_RETURN, COMPLETED
- **Methods**: next(), return(), throw() all return promises
- **Symbol.asyncIterator**: Generators are async iterable
- **Factory functions**: create(), createFromValues(), createFromPromises(), createDelayedGenerator()
- **State management**: Prevents concurrent execution, proper completion tracking

### Async/Await (ES2017)
- **async functions**: async function declarations and expressions
- **await expressions**: Suspend execution until promise settles
- **RETURN_ASYNC opcode**: Dedicated return opcode for async functions
- **Promise wrapping**: Automatic wrapping of return values
- **Error handling**: try/catch works with async/await
- **for-await-of**: Async iteration in async functions

## ES6 Module System ✅

### Module Records
- **Module states**: unlinked, linking, linked, evaluating, evaluated
- **State machine**: Proper state transitions with error handling
- **Module identity**: URL-based module identity
- **Module caching**: Prevents re-evaluation of modules

### Import/Export
- **Named imports**: import { x, y } from 'module'
- **Default imports**: import x from 'module'
- **Namespace imports**: import * as ns from 'module'
- **Named exports**: export { x, y }
- **Default exports**: export default value
- **Re-exports**: export { x } from 'module', export * from 'module'

### Module Loader
- **File-based resolution**: Resolves modules from filesystem
- **Relative imports**: './module', '../module'
- **Module caching**: Single instance per module URL
- **Circular dependencies**: Proper handling through caching

### Dynamic import()
- **Promise-based**: import() returns a promise
- **Module namespace**: Resolves to module namespace object
- **Error handling**: Promise rejection on module errors

### Module Namespace
- **Sealed objects**: Module namespace objects are sealed
- **All exports**: Contains all named exports from module
- **Default export**: Available as 'default' property

## ES6 Class Syntax ✅

### Class Declarations
- **Class keyword**: class ClassName { }
- **Class expressions**: const C = class { }
- **Constructor**: Special constructor method
- **Hoisting**: Classes are not hoisted (temporal dead zone)

### Instance Members
- **Instance methods**: Added to class prototype
- **Instance fields**: Property initialization in constructor
- **Getters/Setters**: get/set accessor properties
- **this binding**: Proper this binding in methods

### Static Members
- **Static methods**: Class-level methods
- **Static fields**: Class-level properties
- **Static getters/setters**: Class-level accessor properties

### Inheritance
- **extends keyword**: class Child extends Parent
- **super()**: Call parent constructor
- **super.method()**: Call parent methods
- **Prototype chain**: Proper prototype linking
- **Constructor validation**: Classes must be called with 'new'

## SharedArrayBuffer & Atomics (ES2017) ✅

### SharedArrayBuffer
- **Shared memory**: Direct ByteBuffer for multi-threaded access
- **Fixed-length**: Cannot be detached
- **Methods**: slice() for copying byte ranges
- **Thread-safe**: Synchronization for concurrent access

### Atomics
- **Atomic operations**: add, sub, and, or, xor
- **Atomic access**: load, store
- **Atomic swap**: compareExchange, exchange
- **Lock-free query**: isLockFree
- **Typed array support**: Works on Int32Array and Uint32Array backed by SharedArrayBuffer

## ES2021-ES2024 Features ✅

### ES2021
- **String.prototype.replaceAll**: Replace all occurrences
- **Promise.any**: Fulfills when any promise fulfills
- **WeakRef**: Weak references using Java WeakReference
- **FinalizationRegistry**: Cleanup callbacks on object collection

### ES2022
- **Array.prototype.at / String.prototype.at**: Negative index support
- **Object.hasOwn**: Safer alternative to hasOwnProperty

### ES2023
- **Array.prototype.findLast / findLastIndex**: Search from end
- **Array.prototype.toReversed**: Immutable reverse
- **Array.prototype.toSorted**: Immutable sort
- **Array.prototype.toSpliced**: Immutable splice
- **Array.prototype.with**: Immutable element replacement

### ES2024
- **Promise.withResolvers**: Returns {promise, resolve, reject}
- **Object.groupBy**: Group array elements into object
- **Map.groupBy**: Group array elements into Map
- **ArrayBuffer.prototype.detach**: Transfer ownership
- **Explicit Resource Management**:
  - `DisposableStack` and `AsyncDisposableStack`
  - `Symbol.dispose` and `Symbol.asyncDispose`
  - `using` and `await using` declaration syntax

## Partially Implemented 🚧

### Syntax Features (Parser Complete, Needs Compiler/Runtime)
- **Private class fields**: #field
  - ✅ Lexer: PRIVATE_NAME token type for `#identifier`
  - ✅ AST: PrivateIdentifier node, PropertyDefinition with isPrivate flag
  - ✅ Opcodes: GET_PRIVATE_FIELD(131), PUT_PRIVATE_FIELD(132), DEFINE_PRIVATE_FIELD(133), DEFINE_FIELD(134), PRIVATE_IN(135)
  - ✅ Parser: Full support for parsing private fields, methods, getters/setters in classes
  - ✅ Parser Tests: ClassParserTest with comprehensive test coverage
  - ✅ Compiler: Symbol creation, field initialization, and access compilation implemented
  - ✅ Runtime: PropertyKey.fromSymbol() for symbol-based storage, all opcodes implemented in VM
  - ✅ Static private fields: `static #field` initialization and access
  - ✅ Private methods: `#method()` and `static #method()` with private symbol-based access
  - ✅ PRIVATE_IN operator: `#field in obj` for private brand / field presence checks
  - ✅ Tests: Private field tests passing for instance + static scenarios

- **Static class blocks**: static { }
  - ✅ AST: StaticBlock node as ClassElement
  - ✅ Parser: Full support for parsing `static { }` blocks
  - ✅ Parser Tests: Included in ClassParserTest
  - ✅ Compiler: compileStaticBlock() generates initialization functions
  - ✅ Runtime: Static blocks execute with class constructor as 'this'
  - ✅ Tests: All static block tests passing (testClassWithStaticBlock, testClassWithMultipleStaticBlocks)

- **Public class fields**:
  - ✅ AST: PropertyDefinition with isPrivate=false
  - ✅ Parser: Full support for parsing public instance and static fields
  - ✅ Parser Tests: Included in ClassParserTest
  - ✅ Compiler: Field initialization in constructors via compileFieldInitialization()
  - ✅ Runtime: DEFINE_FIELD opcode, fields stored as regular object properties
  - ✅ Tests: All public field tests passing (testClassWithPublicField, testClassWithPublicFieldInitializer)

## Not Yet Implemented ⏳

### Miscellaneous

## Summary Statistics

- **ES2015 (ES6) features**: 100% complete (all core features including for-of loops)
- **ES2016-ES2024 features**: 90%+ complete
- **Core language**: 100% complete
- **Built-in objects**: 95%+ complete
- **Async infrastructure**: 100% complete (async/await, for-await-of, promises, microtasks)
- **Iteration protocols**: 100% complete (sync and async)
- **Module system**: 100% complete

qjs4j provides comprehensive modern JavaScript support with excellent ES2024 compliance.
