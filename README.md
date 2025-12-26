# qjs4j

[![Build and Test](https://github.com/caoccao/qjs4j/workflows/Build/badge.svg)](https://github.com/caoccao/qjs4j/actions) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

qjs4j is a native Java implementation of QuickJS.

## Project Summary

qjs4j is a complete reimplementation of the QuickJS JavaScript engine in pure Java, targeting JDK 17 with no external dependencies. It provides ES2020 compliance with modern JavaScript features implemented in pure Java.

### Implementation Status

#### ✅ Core Language Features
- **Primitive Types**: String, Number, Boolean, null, undefined, Symbol, BigInt
- **Objects**: Object creation, prototypes, property descriptors, getters/setters
- **Arrays**: Full Array.prototype with map, filter, reduce, forEach, etc.
- **Functions**: Regular functions, arrow functions, native function bindings
- **Operators**: All arithmetic, logical, bitwise, comparison operators
- **Control Flow**: if/else, switch, loops (for, while, do-while), break/continue

#### ✅ ES2015+ Features
- **Symbol**: Well-known symbols (iterator, toStringTag, etc.), Symbol.for(), Symbol.keyFor()
- **BigInt**: Arbitrary-precision integers with radix conversion and bit operations
- **Promises**: Complete Promise implementation with then/catch/finally, Promise.all/race/allSettled/any
- **Iterators & Generators**: Iterator protocol, Symbol.iterator, generator functions with yield
- **For...of loops**: Full iteration protocol support with helper utilities
- **Template literals**: String interpolation and tagged templates
- **Destructuring**: Array and object destructuring
- **Spread operator**: Array and object spread

#### ✅ Built-in Objects
- **Object**: keys, values, entries, assign, create, freeze, seal, getPrototypeOf, setPrototypeOf
- **Array**: push, pop, shift, unshift, slice, splice, concat, join, reverse, sort, map, filter, reduce, find, every, some, includes
- **String**: charAt, charCodeAt, substring, substr, indexOf, lastIndexOf, split, replace, toUpperCase, toLowerCase, trim, repeat, startsWith, endsWith, includes, padStart, padEnd
- **Number**: parseInt, parseFloat, isNaN, isFinite, toFixed, toPrecision, toExponential
- **Boolean**: toString, valueOf
- **Date**: Date constructor with current time, date manipulation methods
- **RegExp**: Regular expression support with exec, test, match, search, replace
- **Math**: All standard Math functions (sin, cos, tan, sqrt, pow, abs, floor, ceil, round, random, min, max, etc.)
- **JSON**: parse, stringify

#### ✅ ES2020 Collections
- **Map**: Insertion-ordered key-value pairs with SameValueZero equality
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

#### 🚧 In Progress
- **Async/Await**: Await expression support (microtask infrastructure complete)
- **Modules**: ES6 import/export system
- **Class syntax**: ES6 class declarations with extends and super
- **Async iterators**: for-await-of loops

#### ⏳ Planned
- **WeakRef & FinalizationRegistry**: Advanced memory management
- **SharedArrayBuffer**: Shared memory for multi-threading
- **Atomics**: Atomic operations on shared memory

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
