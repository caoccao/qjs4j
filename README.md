# qjs4j

[![Build and Test](https://github.com/caoccao/qjs4j/workflows/Build/badge.svg)](https://github.com/caoccao/qjs4j/actions) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

qjs4j is a native Java implementation of QuickJS.

## Project Summary

qjs4j is a complete reimplementation of the QuickJS JavaScript engine in pure Java, targeting JDK 17 with no external dependencies. It provides ES2024 compliance with modern JavaScript features implemented in pure Java.

### Implementation Status

#### ✅ Core Language Features
- **Primitive Types**: String, Number, Boolean, null, undefined, Symbol, BigInt
- **Objects**: Object creation, prototypes, property descriptors, getters/setters
- **Arrays**: Full Array.prototype with map, filter, reduce, forEach, etc.
- **Functions**: Regular functions, arrow functions, native function bindings
- **Operators**: All arithmetic, logical, bitwise, comparison operators
- **Control Flow**: if/else, switch, loops (for, while, do-while), break/continue

#### ✅ ES2015+ Features
- **Symbol**: All 13 well-known symbols (iterator, asyncIterator, toStringTag, hasInstance, isConcatSpreadable, toPrimitive, match, matchAll, replace, search, split, species, unscopables), Symbol.for(), Symbol.keyFor()
- **BigInt**: Arbitrary-precision integers with radix conversion and bit operations
- **Promises**: Complete Promise implementation with then/catch/finally, Promise.all/race/allSettled (ES2020), Promise.any (ES2021), Promise.withResolvers (ES2024)
- **Iterators & Generators**: Iterator protocol, Symbol.iterator, generator functions with yield
- **For...of loops**: Full iteration protocol support with helper utilities
- **Template literals**: String interpolation and tagged templates
- **Destructuring**: Array and object destructuring
- **Spread operator**: Array and object spread

#### ✅ Built-in Objects
- **Object**: keys, values, entries, fromEntries (ES2019), assign, create, defineProperty (ES5.1), defineProperties (ES5.1), freeze, seal, preventExtensions (ES5.1), getPrototypeOf, setPrototypeOf, getOwnPropertyDescriptor, getOwnPropertyDescriptors (ES2017), getOwnPropertyNames, getOwnPropertySymbols, is (ES2015), isExtensible (ES5.1), isFrozen, isSealed, hasOwn (ES2022), groupBy (ES2024)
- **Array**: push, pop, shift, unshift, slice, splice, concat, join, reverse, sort, map, filter, reduce, find, findIndex, findLast (ES2023), findLastIndex (ES2023), every, some, includes, flat (ES2019), flatMap (ES2019), at (ES2022), toReversed (ES2023), toSorted (ES2023), toSpliced (ES2023), with (ES2023)
- **String**: charAt, charCodeAt, at (ES2022), substring, substr, indexOf, lastIndexOf, split, replace, replaceAll (ES2021), match, matchAll, toUpperCase, toLowerCase, trim, trimStart (ES2019), trimEnd (ES2019), repeat, startsWith, endsWith, includes, padStart, padEnd
- **Number**: parseInt, parseFloat, isNaN, isFinite, toFixed, toPrecision, toExponential
- **Boolean**: toString, valueOf
- **Date**: Date constructor with current time, date manipulation methods
- **RegExp**: Regular expression support with exec, test, match, search, replace
- **Math**: All standard Math functions (sin, cos, tan, sqrt, pow, abs, floor, ceil, round, random, min, max, etc.)
- **JSON**: parse, stringify

#### ✅ Collections
- **Map**: Insertion-ordered key-value pairs with SameValueZero equality, Map.groupBy (ES2024)
- **Set**: Insertion-ordered unique values with SameValueZero equality
- **WeakMap**: Weak-referenced key-value pairs for object keys only
- **WeakSet**: Weak-referenced unique objects with automatic garbage collection

#### ✅ Meta-programming
- **Reflect**: get, set, has, deleteProperty, getPrototypeOf, setPrototypeOf, ownKeys, apply, construct, isExtensible, preventExtensions
- **Proxy**: Proxy constructor with handler traps (get, set, has, deleteProperty, ownKeys)

#### ✅ Binary Data
- **ArrayBuffer**: Raw binary data buffer with slice, detach, byteLength
- **DataView**: Byte-level access with configurable endianness for Int8, Uint8, Int16, Uint16, Int32, Uint32, Float32, Float64
- **TypedArrays**: Int8Array, Uint8Array, Uint8ClampedArray, Int16Array, Uint16Array, Int32Array, Uint32Array, Float32Array, Float64Array

#### ✅ Error Handling
- **Error Types**: Error, TypeError, RangeError, SyntaxError, ReferenceError, URIError
- **Try-catch**: Exception handling with throw, try, catch, finally
- **Stack traces**: Error stack information

#### ✅ Async Infrastructure
- **Microtask Queue**: Complete microtask queue implementation with re-entrancy protection
- **queueMicrotask()**: Global function to enqueue microtasks
- **Promise Microtasks**: Proper ES2020 timing for promise reactions

#### ✅ ES6 Module System
- **Module Records**: Complete module state machine (unlinked, linking, linked, evaluating, evaluated)
- **Import/Export**: Named imports, default imports, namespace imports, re-exports
- **Module Loader**: File-based module resolution with caching
- **Dynamic import()**: Promise-based import() function
- **Circular Dependencies**: Proper handling through caching and evaluation state
- **Module Namespace**: Sealed namespace objects with all exports

#### ✅ ES6 Class Syntax
- **Class Declarations**: Full class declaration and expression support
- **Constructors**: Constructor methods with automatic instance initialization
- **Instance Methods**: Methods added to class prototype
- **Static Methods**: Class-level methods and properties
- **Inheritance**: extends keyword with proper prototype chain
- **Super Keyword**: super() for parent constructor, super.method() for parent methods
- **Field Initialization**: Instance and static fields with property descriptors
- **Class Validation**: Classes cannot be called without 'new'

#### ✅ Async Iterators (ES2018)
- **Async Iterator Protocol**: Symbol.asyncIterator support
- **JSAsyncIterator**: Promise-based iterator with next() returning Promise<{value, done}>
- **for-await-of loops**: Full support via JSAsyncIteratorHelper
- **Sync to Async**: Automatic conversion of sync iterators to async
- **Factory Methods**: fromArray(), fromIterator(), fromIterable(), fromPromise()
- **Utility Functions**: toArray(), isAsyncIterable(), getAsyncIterator()
- **Error Handling**: Promise rejection propagation through iteration

#### ✅ Async Generators (ES2018)
- **JSAsyncGenerator**: Full async generator object implementation
- **Async Generator States**: SUSPENDED_START, SUSPENDED_YIELD, EXECUTING, AWAITING_RETURN, COMPLETED
- **Methods**: next(), return(), throw() all return promises
- **Symbol.asyncIterator**: Generators are async iterable
- **Factory Functions**: create(), createFromValues(), createFromPromises(), createDelayedGenerator()
- **State Management**: Prevents concurrent execution, proper completion tracking
- **Promise Integration**: All operations return promises with proper microtask timing

#### ✅ ES2021-ES2024 Features
- **ES2021**:
  - String.prototype.replaceAll: Replace all occurrences of a substring
  - Promise.any: Fulfills when any promise fulfills
  - WeakRef: Weak references to objects using Java WeakReference
  - FinalizationRegistry: Cleanup callbacks on object collection with microtask integration
- **ES2022**:
  - Array.prototype.at / String.prototype.at: Index access with negative indices support
  - Object.hasOwn: Safer alternative to hasOwnProperty
- **ES2023**:
  - Array.prototype.findLast / findLastIndex: Search arrays from the end
  - Array.prototype.toReversed: Immutable reverse
  - Array.prototype.toSorted: Immutable sort
  - Array.prototype.toSpliced: Immutable splice
  - Array.prototype.with: Immutable element replacement
- **ES2024**:
  - Promise.withResolvers: Returns {promise, resolve, reject} object
  - Object.groupBy: Group array elements into object by callback
  - Map.groupBy: Group array elements into Map by callback

#### ✅ SharedArrayBuffer & Atomics (ES2017)
- **SharedArrayBuffer**: Shared memory buffer for multi-threaded access
  - Direct ByteBuffer allocation for efficient sharing
  - Fixed-length buffer (cannot be detached)
  - slice() method for copying byte ranges
  - Thread-safe operations with synchronization
- **Atomics**: Atomic operations on shared memory
  - add, sub, and, or, xor: Atomic arithmetic and bitwise operations
  - load, store: Atomic read/write operations
  - compareExchange: Compare-and-swap operation
  - exchange: Atomic value exchange
  - isLockFree: Query lock-free operation support
  - Works on Int32Array and Uint32Array backed by SharedArrayBuffer

#### 🚧 In Progress
- **Async/Await**: Await expression support (microtask infrastructure complete)

#### ⏳ Planned
- **Internationalization (Intl)**: i18n support for dates, numbers, strings

### Architecture

The implementation is organized into several key packages:

- **core**: Runtime components (JSValue types, JSContext, JSRuntime, collections, binary data)
- **vm**: Virtual machine with bytecode execution, opcode handlers, stack management
- **builtins**: JavaScript built-in objects and prototype methods
- **compiler**: Parser, lexer, bytecode compiler, and AST (planned)
- **memory**: Garbage collection and memory management (planned)
- **regexp**: Regular expression engine (planned)

### Key Technical Details

- **Shape-based optimization**: Objects use hidden classes (shapes) for efficient property access
- **SameValueZero equality**: Proper NaN and -0/+0 handling for Map/Set
- **Little-endian buffers**: JavaScript-compliant binary data layout
- **Iterator protocol**: Complete implementation with Symbol.iterator
- **Weak references**: Java WeakHashMap-based WeakMap/WeakSet with automatic GC
- **Prototype chain**: Full prototype-based inheritance

This project represents a comprehensive implementation of modern JavaScript in pure Java.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
