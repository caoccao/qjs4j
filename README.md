# qjs4j

[![Build and Test](https://github.com/caoccao/qjs4j/workflows/Build/badge.svg)](https://github.com/caoccao/qjs4j/actions) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

qjs4j is a native Java implementation of QuickJS - a complete reimplementation of the QuickJS JavaScript engine in pure Java (JDK 17+, zero external dependencies).

## Project Status

qjs4j implements ES2024 features with full QuickJS specification compliance. See [detailed feature list](docs/migration/FEATURES.md) for comprehensive implementation status.

### Features Beyond QuickJS

qjs4j includes features not present in the original QuickJS:

- **Float16Array**: IEEE 754 half-precision (16-bit) floating point typed array support
- **ES2024 Features**: Promise.withResolvers, Object.groupBy, Map.groupBy
- **Enhanced Module System**: Complete ES6 module implementation with dynamic import()
- **Microtask Queue**: Full ES2020-compliant microtask infrastructure

### Partially Implemented

The following features have infrastructure in place but require additional implementation:

- **ES2022 class features**: Classes with constructors, methods, fields, and static blocks
  - ✅ Lexer support for `class` keyword and private names (#field)
  - ✅ AST nodes (ClassDeclaration, MethodDefinition, PropertyDefinition, PrivateIdentifier, StaticBlock)
  - ✅ Opcodes (DEFINE_CLASS, DEFINE_METHOD, DEFINE_FIELD, PRIVATE_SYMBOL, GET/PUT/DEFINE_PRIVATE_FIELD)
  - ✅ Parser support (parseClassDeclaration, parseClassElement, fields, private field access, static blocks)
  - ✅ Parser tests (ClassParserTest with 8 test cases - all passing)
  - ✅ Compiler implementation (classes, constructors, instance methods, fields, static blocks)
  - ✅ Runtime support (all field-related opcodes implemented)
  - ✅ Compiler tests (ClassCompilerTest with 11 test cases - all passing)
  - ✅ **Private fields (#field)**: Fully working
    - Private field definition: `#count = 0;`
    - Private field access: `this.#count`, `this.#count = value`
    - Private field operations: `this.#count++`, `this.#count += 1`
  - ✅ **Static blocks**: Fully working
    - Static initialization: `static { this.x = 10; }`
    - Multiple blocks execute in order
    - Access to class constructor as 'this'
  - ⏳ Static methods (not yet implemented)

### Not Yet Implemented

The following QuickJS features are planned but not yet implemented:

- **Internationalization (Intl)**: i18n support for dates, numbers, and strings
- **Top-level await**: Module-level await expressions

See [ASYNC_AWAIT_ENHANCEMENTS.md](docs/migration/ASYNC_AWAIT_ENHANCEMENTS.md) for async/await implementation details.

## Documentation

- **[Features](docs/migration/FEATURES.md)**: Complete list of implemented JavaScript features
- **[Migration Status](docs/migration/MIGRATION_STATUS.md)**: Migration progress from QuickJS C to Java
- **[Async/Await](docs/migration/ASYNC_AWAIT_ENHANCEMENTS.md)**: Async/await and iteration implementation

## Quick Start

```java
import com.caoccao.qjs4j.core.*;

// Create a JavaScript runtime and context
try (JSContext context = new JSContext(new JSRuntime())) {
    // Evaluate JavaScript code
    JSValue result = context.eval("2 + 2");
    System.out.println(result); // 4

    // Work with objects
    JSValue obj = context.eval("({ name: 'qjs4j', version: '1.0' })");
    if (obj instanceof JSObject jsObj) {
        JSValue name = jsObj.get("name");
        System.out.println(name); // qjs4j
    }

    // Use modern JavaScript features
    JSValue promise = context.eval("Promise.resolve(42)");
    // Process microtasks to settle promises
    context.processMicrotasks();
}
```

## Architecture

qjs4j is organized into modular packages:

- **core**: Runtime components (JSValue types, JSContext, JSRuntime)
- **vm**: Virtual machine with bytecode execution and stack management
- **builtins**: JavaScript built-in objects and prototype methods
- **compiler**: Parser, lexer, bytecode compiler, and AST

Key technical features:
- Shape-based optimization with hidden classes
- Proper SameValueZero equality for Map/Set
- Complete iterator and async iterator protocols
- Full prototype-based inheritance
- Weak references using Java WeakHashMap

## License

Apache License 2.0 - see [LICENSE](LICENSE) file for details.
