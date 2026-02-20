# QuickJS to Pure Java Conversion Plan

## Executive Summary

This document outlines a detailed plan to convert the QuickJS JavaScript engine from C to pure Java, using only JDK 17 APIs without any external dependencies.

**Repository:** QuickJS JavaScript Engine
**Original Language:** C (~73,000 lines of code)
**Target Language:** Java 17 (no external libraries)
**Complexity:** Very High - Full JavaScript engine implementation

---

## 1. Project Overview

### 1.1 What is QuickJS?

QuickJS is a small, embeddable JavaScript engine that supports:
- Full ES2020 specification compliance
- 244+ bytecode opcodes for execution
- Module system (import/export)
- BigInt, Symbol, and all modern JS features
- Regular expression engine
- Bytecode compilation and execution
- REPL and command-line tools

### 1.2 Current Architecture (C Implementation)

**Core Components:**
- `quickjs.c/h` (59,540 lines) - Main engine, parser, compiler, VM
- `libregexp.c/h` (3,448 lines) - Regular expression engine
- `libunicode.c/h` (2,123 lines) - Unicode support and normalization
- `dtoa.c/h` (1,620 lines) - Double-to-ASCII conversion
- `cutils.c/h` (641 lines) - Utility functions
- `quickjs-libc.c/h` - Standard library bindings (I/O, OS interface)
- `qjs.c` - Command-line interpreter
- `qjsc.c` - Bytecode compiler tool

**Key Data Structures:**
- `JSValue` - NaN-boxed value representation (64-bit)
- `JSRuntime` - Runtime environment
- `JSContext` - Execution context
- `JSObject` - Object representation with shapes
- `JSFunctionBytecode` - Compiled bytecode
- `JSAtom` - Interned strings/identifiers

---

## 2. Java Architecture Design

### 2.1 Package Structure

```
com.caoccao.qjs4j/
├── core/
│   ├── JSRuntime.java
│   ├── JSContext.java
│   ├── JSValue.java
│   ├── JSObject.java
│   ├── JSFunction.java
│   ├── JSArray.java
│   └── JSString.java
├── compiler/
│   ├── Parser.java
│   ├── Lexer.java
│   ├── BytecodeCompiler.java
│   ├── AST/ (node classes)
│   └── Scope.java
├── vm/
│   ├── VirtualMachine.java
│   ├── Bytecode.java
│   ├── Opcode.java (enum)
│   ├── Stack.java
│   └── ExecutionContext.java
├── memory/
│   ├── GarbageCollector.java
│   ├── HeapManager.java
│   ├── ReferenceCounter.java
│   └── MemoryPool.java
├── types/
│   ├── JSBigInt.java
│   ├── JSSymbol.java
│   ├── JSModule.java
│   └── JSPromise.java
├── builtins/
│   ├── GlobalObject.java
│   ├── ArrayPrototype.java
│   ├── ObjectPrototype.java
│   ├── StringPrototype.java
│   ├── NumberPrototype.java
│   ├── MathObject.java
│   ├── DateObject.java
│   ├── RegExpObject.java
│   ├── PromisePrototype.java
│   └── ...
├── regexp/
│   ├── RegExpEngine.java
│   ├── RegExpCompiler.java
│   ├── RegExpBytecode.java
│   └── CharacterClass.java
├── unicode/
│   ├── UnicodeData.java
│   ├── UnicodeNormalization.java
│   ├── CharacterProperties.java
│   └── CodePointIterator.java
├── util/
│   ├── DtoaConverter.java
│   ├── UTF8Decoder.java
│   ├── AtomTable.java
│   └── DynamicBuffer.java
└── cli/
    ├── REPL.java
    ├── QuickJSInterpreter.java
    └── BytecodeCompilerTool.java
```

### 2.2 Core Design Decisions

#### Value Representation Strategy

**Challenge:** C uses NaN-boxing to pack different types into 64-bit JSValue
**Java Solution:**

**Option A: Sealed Interface with Records (Recommended)**
```java
public sealed interface JSValue permits
    JSUndefined, JSNull, JSBoolean, JSNumber, JSString,
    JSObject, JSSymbol, JSBigInt, JSFunction {

    JSValueType type();
    Object toJavaObject();
}

public record JSNumber(double value) implements JSValue { }
public record JSBoolean(boolean value) implements JSValue { }
// etc.
```

**Option B: Tagged Union with Long**
```java
public final class JSValue {
    private final long bits;  // NaN-boxed representation

    // Tag in upper bits, payload in lower bits
    private static final long TAG_MASK = 0xFFFF_0000_0000_0000L;
    private static final long TAG_UNDEFINED = 0x0001_0000_0000_0000L;
    // ...
}
```

**Recommendation:** Use Option A for type safety and Java idioms, despite slightly higher memory usage.

#### Memory Management Strategy

**C Implementation:** Reference counting + cycle detection
**Java Implementation:**

1. **Primary:** Leverage JVM Garbage Collector
   - Simpler implementation
   - No manual ref counting needed
   - Natural Java approach

2. **Optional:** Implement reference counting for deterministic cleanup
   - Mimic C behavior more closely
   - Predictable memory usage
   - Required for resource handles (files, etc.)

3. **Hybrid Approach (Recommended):**
   - Use JVM GC for most objects
   - Reference counting only for external resources
   - Implement WeakReference for circular reference detection

#### Bytecode Representation

**C Implementation:** `uint8_t*` array with variable-length instructions
**Java Implementation:**

```java
public final class Bytecode {
    private final byte[] instructions;
    private final JSValue[] constantPool;
    private final String[] atomPool;

    public int readOpcode(int offset) { }
    public int readU8(int offset) { }
    public int readU32(int offset) { }
    // ...
}

public enum Opcode {
    INVALID(0, 1, 0, 0),
    PUSH_I32(1, 5, 0, 1),
    PUSH_CONST(2, 5, 0, 1),
    // ... 244 opcodes

    private final int code;
    private final int size;
    private final int nPop;
    private final int nPush;
}
```

---

## 3. Implementation Phases

### Phase 1: Foundation (Weeks 1-4)

#### 1.1 Unicode Support (Week 1)
- **Input:** `libunicode.c/h`, `libunicode-table.h`
- **Output:** `com.caoccao.qjs4j.unicode.*`

**Tasks:**
1. Port Unicode character property tables
   - Use `int[]` arrays for compact storage
   - Implement binary search for property lookup
   - Character categories (Lu, Ll, Nd, etc.)

2. Implement Unicode normalization (NFC, NFD, NFKC, NFKD)
   - Canonical decomposition
   - Canonical composition
   - Compatibility transformations

3. Case conversion (toUpperCase, toLowerCase, caseFold)

4. Character property queries
   - `isWhiteSpace()`
   - `isIdentifierStart()`
   - `isIdentifierPart()`
   - `isLineTerminator()`

**JDK 17 APIs to use:**
- `Character` class for basic properties
- `String` for storage
- `Arrays.binarySearch()` for table lookups
- Generate tables using `unicode_gen.c` and convert to Java

#### 1.2 Utilities (Week 1)
- **Input:** `cutils.c/h`, `dtoa.c/h`
- **Output:** `com.caoccao.qjs4j.utils.*`

**Tasks:**
1. Dynamic buffer implementation
   - Similar to C's DynBuf
   - Auto-growing byte/char buffers
   - Use `ByteBuffer` or custom class

2. Double-to-ASCII conversion
   - Port dtoa.c algorithm (Grisu3 or similar)
   - Handle special cases (NaN, Infinity, -0)
   - Proper rounding
   - Alternative: Use `Double.toString()` with custom formatting

3. String utilities
   - UTF-8 encoding/decoding
   - String interning (atom table)
   - String concatenation helpers

4. Memory utilities
   - Custom allocators (optional)
   - Buffer pooling

**JDK 17 APIs:**
- `ByteBuffer`, `CharBuffer`
- `StandardCharsets.UTF_8`
- `String.intern()` or custom `HashMap<String, Integer>` for atoms
- `DecimalFormat` as fallback for number formatting

#### 1.3 Regular Expression Engine (Weeks 2-3)
- **Input:** `libregexp.c/h`, `libregexp-opcode.h`
- **Output:** `com.caoccao.qjs4j.regexp.*`

**Tasks:**
1. RegExp bytecode compiler
   - Parse regex pattern to AST
   - Compile AST to bytecode
   - Support all ES2020 regex features:
     - Lookahead/lookbehind
     - Named capture groups
     - Unicode property escapes
     - Dotall (s) flag
     - Sticky (y) flag

2. RegExp bytecode executor
   - Stack-based matcher
   - Backtracking implementation
   - Capture group tracking
   - Unicode-aware matching

3. Character class matching
   - Character ranges
   - Unicode categories
   - Predefined classes (\d, \w, \s)

**Why not use java.util.regex.Pattern?**
- Need exact ES2020 semantics
- Different syntax (e.g., lookbehind)
- Must control matching behavior precisely
- Bytecode compilation needed for serialization

**Implementation approach:**
- Port the bytecode format from C
- Implement interpreter for regex opcodes
- Use recursion or explicit stack for backtracking

#### 1.4 Core Value System (Week 4)
- **Input:** `quickjs.h` (JSValue definitions)
- **Output:** `com.caoccao.qjs4j.core.JSValue` and subtypes

**Tasks:**
1. Define JSValue type hierarchy
```java
public sealed interface JSValue { }
public record JSUndefined() implements JSValue {
    public static final JSUndefined INSTANCE = new JSUndefined();
}
public record JSNull() implements JSValue {
    public static final JSNull INSTANCE = new JSNull();
}
public record JSBoolean(boolean value) implements JSValue {
    public static final JSBoolean TRUE = new JSBoolean(true);
    public static final JSBoolean FALSE = new JSBoolean(false);
}
public record JSNumber(double value) implements JSValue { }
public final class JSString implements JSValue {
    private final String value;
    private final int atomIndex; // for interned strings
}
public final class JSObject implements JSValue { /* ... */ }
public final class JSFunction implements JSValue { /* ... */ }
public record JSSymbol(String description, int id) implements JSValue { }
public final class JSBigInt implements JSValue {
    private final BigInteger value;
}
```

2. Type conversion operations
   - `ToBoolean()`
   - `ToNumber()`
   - `ToString()`
   - `ToObject()`
   - `ToPrimitive()`
   - `ToInteger()`
   - `ToInt32()`, `ToUint32()`

3. Type checking utilities
   - `isUndefined()`, `isNull()`, `isNullish()`
   - `isBoolean()`, `isNumber()`, `isString()`
   - `isObject()`, `isFunction()`, `isSymbol()`

4. Atom (interned string) system
```java
public final class AtomTable {
    private final Map<String, Integer> stringToAtom;
    private final List<String> atomToString;

    public int intern(String str) { }
    public String getString(int atom) { }
}
```

### Phase 2: Object System (Weeks 5-8)

#### 2.1 Object Representation (Week 5)
- **Input:** `quickjs.c` (JSObject, JSShape structures)
- **Output:** `com.caoccao.qjs4j.core.JSObject`

**Tasks:**
1. Shape-based property storage
```java
public final class JSShape {
    private final int[] propertyKeys;  // atom indices
    private final PropertyDescriptor[] descriptors;
    private final JSShape parent;

    // Transition maps for efficient shape evolution
    private final Map<PropertyKey, JSShape> transitions;
}

public final class JSObject implements JSValue {
    private JSShape shape;
    private JSValue[] propertyValues;  // parallel to shape.propertyKeys
    private Map<Integer, JSValue> sparseProperties;  // for arrays/non-shape props
    private JSObject prototype;
}
```

2. Property descriptor system
```java
public record PropertyDescriptor(
    boolean writable,
    boolean enumerable,
    boolean configurable,
    JSValue value,
    JSFunction getter,
    JSFunction setter
) { }
```

3. Property operations
   - `GetOwnProperty()`
   - `DefineOwnProperty()`
   - `Get()`, `Set()`
   - `Delete()`
   - `HasProperty()`
   - `OwnPropertyKeys()`

4. Prototype chain management
   - `GetPrototypeOf()`, `SetPrototypeOf()`
   - Prototype chain traversal
   - Circular prototype detection

#### 2.2 Array Implementation (Week 5)
```java
public final class JSArray extends JSObject {
    private JSValue[] denseArray;  // for indices 0..n
    private long length;

    // Fast path for dense arrays
    // Slow path falls back to JSObject

    public void setLength(long newLength) { }
    public JSValue get(long index) { }
    public void set(long index, JSValue value) { }
}
```

#### 2.3 Function Objects (Week 6)
```java
public sealed interface JSFunction implements JSValue
    permits JSBytecodeFunction, JSNativeFunction, JSBoundFunction {
    JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args);
}

public final class JSBytecodeFunction implements JSFunction {
    private final Bytecode bytecode;
    private final JSValue[] closureVars;  // captured variables
    private final JSObject prototype;
    private final String name;
    private final int length;  // parameter count
}

public final class JSNativeFunction implements JSFunction {
    private final NativeCallback callback;

    @FunctionalInterface
    interface NativeCallback {
        JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args);
    }
}

public final class JSBoundFunction implements JSFunction {
    private final JSFunction target;
    private final JSValue boundThis;
    private final JSValue[] boundArgs;
}
```

#### 2.4 Closures and Scopes (Week 6)
```java
public final class JSVarRef {
    private JSValue value;
    private boolean isConst;

    // Shared reference for closure variables
}

public final class ClosureVar {
    private final String name;
    private final JSVarRef ref;
}
```

#### 2.5 Class System (Week 7)
- Internal class IDs
- Class definitions
- Private field support
- Static initialization blocks

#### 2.6 Symbols and Well-Known Symbols (Week 7)
```java
public final class JSSymbol implements JSValue {
    private static final AtomicInteger nextId = new AtomicInteger(0);
    private final int id;
    private final String description;

    // Well-known symbols
    public static final JSSymbol ITERATOR =
        new JSSymbol("Symbol.iterator", WELL_KNOWN_ID);
    public static final JSSymbol TO_STRING_TAG =
        new JSSymbol("Symbol.toStringTag", WELL_KNOWN_ID + 1);
    // ... etc
}
```

#### 2.7 BigInt Implementation (Week 8)
```java
public final class JSBigInt implements JSValue {
    private final BigInteger value;  // JDK BigInteger

    // Arithmetic operations
    public JSBigInt add(JSBigInt other) { }
    public JSBigInt subtract(JSBigInt other) { }
    public JSBigInt multiply(JSBigInt other) { }
    public JSBigInt divide(JSBigInt other) { }
    public JSBigInt remainder(JSBigInt other) { }
    public JSBigInt power(JSBigInt exponent) { }

    // Bitwise operations
    public JSBigInt and(JSBigInt other) { }
    public JSBigInt or(JSBigInt other) { }
    public JSBigInt xor(JSBigInt other) { }
    public JSBigInt not() { }
    public JSBigInt shiftLeft(long bits) { }
    public JSBigInt shiftRight(long bits) { }
}
```

### Phase 3: Parser and Compiler (Weeks 9-14)

#### 3.1 Lexer (Weeks 9-10)
- **Input:** `quickjs.c` (parsing functions)
- **Output:** `com.caoccao.qjs4j.compilation.Lexer`

**Tasks:**
1. Token types enumeration
```java
public enum TokenType {
    EOF, IDENTIFIER, NUMBER, STRING, TEMPLATE,
    // Keywords
    FUNCTION, VAR, LET, CONST, IF, ELSE, FOR, WHILE, DO,
    RETURN, BREAK, CONTINUE, SWITCH, CASE, DEFAULT,
    TRY, CATCH, FINALLY, THROW,
    CLASS, EXTENDS, SUPER, NEW, THIS,
    IMPORT, EXPORT, FROM, AS,
    ASYNC, AWAIT, YIELD,
    // Operators
    PLUS, MINUS, MUL, DIV, MOD, EXP,
    EQ, NE, STRICT_EQ, STRICT_NE,
    LT, LE, GT, GE,
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, /* ... */
    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    SEMICOLON, COMMA, DOT, QUESTION, COLON,
    ARROW, ELLIPSIS,
    // etc.
}

public record Token(
    TokenType type,
    String value,
    int line,
    int column,
    int offset
) { }
```

2. Lexical analysis
   - Identifier recognition
   - Number literals (decimal, hex, octal, binary, scientific)
   - String literals (single/double quotes, escape sequences)
   - Template literals (with interpolation)
   - Regular expression literals
   - Comments (single-line, multi-line, HTML-style)
   - Automatic semicolon insertion preparation

3. Unicode handling
   - UTF-8 input
   - Unicode escape sequences (\uXXXX, \u{XXXX})
   - Unicode identifiers (XID_Start, XID_Continue)

4. Lookahead support for parser
   - Token buffering
   - Backtracking support

#### 3.2 Parser (Weeks 10-12)
- **Input:** `quickjs.c` (parsing logic)
- **Output:** `com.caoccao.qjs4j.compilation.Parser`, AST classes

**Tasks:**
1. AST node hierarchy
```java
public sealed interface ASTNode permits
    Expression, Statement, Declaration, ModuleItem { }

public sealed interface Expression extends ASTNode permits
    Literal, Identifier, BinaryExpression, UnaryExpression,
    AssignmentExpression, ConditionalExpression,
    CallExpression, MemberExpression, NewExpression,
    FunctionExpression, ArrowFunctionExpression,
    ArrayExpression, ObjectExpression, TemplateExpression,
    /* ... */ { }

public record BinaryExpression(
    BinaryOperator op,
    Expression left,
    Expression right,
    SourceLocation location
) implements Expression { }

// ... similar for all node types
```

2. Recursive descent parser
   - Operator precedence handling
   - Expression parsing
   - Statement parsing
   - Declaration parsing
   - Error recovery

3. ES2020+ features
   - Destructuring (arrays, objects)
   - Rest/spread operators
   - Arrow functions
   - Classes (fields, methods, private, static)
   - Async/await
   - Generators
   - Template literals
   - Optional chaining (?.)
   - Nullish coalescing (??)

4. Module syntax
   - Import declarations
   - Export declarations (named, default, re-export)

5. Strict mode handling

6. Automatic semicolon insertion

#### 3.3 Bytecode Compiler (Weeks 12-14)
- **Input:** `quickjs.c` (bytecode emission)
- **Output:** `com.caoccao.qjs4j.compilation.compiler.BytecodeCompiler`

**Tasks:**
1. Bytecode emission
```java
public final class BytecodeEmitter {
    private final ByteArrayOutputStream code;
    private final List<JSValue> constantPool;
    private final AtomTable atoms;

    public void emitOpcode(Opcode op) { }
    public void emitU8(int value) { }
    public void emitU16(int value) { }
    public void emitU32(int value) { }
    public void emitAtom(String str) { }
    public void emitConstant(JSValue value) { }

    public int currentOffset() { }
    public void patchJump(int offset, int target) { }
}
```

2. Scope analysis
   - Variable binding
   - Lexical scoping
   - Closure detection
   - Hoisting (var, function declarations)
   - TDZ for let/const

3. Expression compilation
   - All operators
   - Short-circuit evaluation (&&, ||, ??)
   - Function calls
   - Member access
   - Array/object literals

4. Statement compilation
   - Control flow (if, switch, loops)
   - Try/catch/finally
   - Return, break, continue
   - With statement

5. Function compilation
   - Parameter handling
   - Default parameters
   - Rest parameters
   - Arrow functions
   - Generators
   - Async functions

6. Class compilation
   - Constructor
   - Methods (instance, static)
   - Field initializers
   - Private members
   - Super calls

7. Optimizations
   - Constant folding
   - Dead code elimination
   - Peephole optimization
   - Jump threading

### Phase 4: Virtual Machine (Weeks 15-20)

#### 4.1 Stack and Execution Context (Week 15)
```java
public final class CallStack {
    private final JSValue[] stack;
    private int stackTop;

    public void push(JSValue value) { }
    public JSValue pop() { }
    public JSValue peek(int offset) { }
}

public final class StackFrame {
    private final JSFunction function;
    private final JSValue thisArg;
    private final JSValue[] locals;
    private final JSVarRef[] closureVars;
    private int programCounter;
    private final StackFrame caller;
}
```

#### 4.2 Bytecode Interpreter (Weeks 16-18)
- **Input:** `quickjs.c` (VM execution loop)
- **Output:** `com.caoccao.qjs4j.vm.VirtualMachine`

**Tasks:**
1. Main execution loop
```java
public final class VirtualMachine {
    private final CallStack stack;
    private StackFrame currentFrame;

    public JSValue execute(JSBytecodeFunction function,
                          JSValue thisArg,
                          JSValue[] args) {
        while (true) {
            int opcode = readOpcode();
            switch (Opcode.fromInt(opcode)) {
                case PUSH_I32 -> handlePushI32();
                case PUSH_CONST -> handlePushConst();
                case ADD -> handleAdd();
                // ... 244 opcodes
                case RETURN -> return handleReturn();
            }
        }
    }
}
```

2. Implement all 244 opcodes
   - Stack manipulation (push, pop, dup, swap, etc.)
   - Arithmetic operators
   - Comparison operators
   - Logical operators
   - Bitwise operators
   - Property access (get, set, delete)
   - Function calls
   - Object creation
   - Array operations
   - Control flow (jumps, conditional jumps)
   - Exception handling
   - Iterator protocol
   - Generator yield/resume

3. Type coercion in operations
   - Per ES2020 spec
   - Handle all edge cases

4. Exception handling
   - Try/catch/finally bytecodes
   - Exception propagation
   - Stack unwinding

#### 4.3 Garbage Collection (Week 19)
```java
public final class GarbageCollector {
    private final Set<JSObject> rootSet;
    private final Set<JSObject> allObjects;

    // Mark-and-sweep for cycles
    public void collectGarbage() {
        Set<JSObject> reachable = markPhase();
        sweepPhase(reachable);
    }

    private Set<JSObject> markPhase() {
        // BFS/DFS from roots
    }

    private void sweepPhase(Set<JSObject> reachable) {
        // Free unreachable objects
    }
}
```

#### 4.4 Module System (Week 20)
```java
public final class JSModule implements JSValue {
    private final String url;
    private final Map<String, JSValue> exports;
    private final Map<String, ImportBinding> imports;
    private boolean evaluated;

    public void link(ModuleResolver resolver) { }
    public JSValue evaluate(JSContext ctx) { }
}

public interface ModuleResolver {
    JSModule resolve(String specifier, JSModule referrer);
}
```

### Phase 5: Built-in Objects (Weeks 21-28)

#### 5.1 Global Object (Week 21)
- `undefined`, `null`, `NaN`, `Infinity`
- `parseInt()`, `parseFloat()`
- `isNaN()`, `isFinite()`
- `encodeURI()`, `decodeURI()`
- `eval()` (compile and execute string)

#### 5.2 Object Prototype (Week 21)
- `Object.create()`
- `Object.defineProperty()`, `Object.defineProperties()`
- `Object.getOwnPropertyDescriptor()`
- `Object.getOwnPropertyNames()`, `Object.getOwnPropertySymbols()`
- `Object.keys()`, `Object.values()`, `Object.entries()`
- `Object.assign()`
- `Object.freeze()`, `Object.seal()`, `Object.preventExtensions()`
- `Object.is()`
- `Object.fromEntries()`
- Prototype methods: `toString()`, `valueOf()`, `hasOwnProperty()`, etc.

#### 5.3 Function Prototype (Week 22)
- `Function.prototype.call()`
- `Function.prototype.apply()`
- `Function.prototype.bind()`
- `Function.prototype.toString()`

#### 5.4 Array Prototype (Week 22)
- Mutating: `push()`, `pop()`, `shift()`, `unshift()`, `splice()`, `reverse()`, `sort()`, `fill()`
- Non-mutating: `concat()`, `slice()`, `join()`, `toString()`
- Iteration: `forEach()`, `map()`, `filter()`, `reduce()`, `reduceRight()`, `find()`, `findIndex()`, `some()`, `every()`
- ES6+: `flat()`, `flatMap()`, `at()`, `includes()`
- `Array.from()`, `Array.of()`, `Array.isArray()`

#### 5.5 String Prototype (Week 23)
- `charAt()`, `charCodeAt()`, `codePointAt()`
- `concat()`, `substring()`, `substr()`, `slice()`
- `indexOf()`, `lastIndexOf()`, `includes()`, `startsWith()`, `endsWith()`
- `toLowerCase()`, `toUpperCase()`, `trim()`, `trimStart()`, `trimEnd()`
- `split()`, `match()`, `search()`, `replace()`, `replaceAll()`
- `repeat()`, `padStart()`, `padEnd()`
- Template literal tag functions
- `String.fromCharCode()`, `String.fromCodePoint()`

#### 5.6 Number and Math (Week 23)
- `Number.parseInt()`, `Number.parseFloat()`
- `Number.isNaN()`, `Number.isFinite()`, `Number.isInteger()`, `Number.isSafeInteger()`
- `Number.prototype.toFixed()`, `toPrecision()`, `toExponential()`
- `Math.*` - all math functions (sin, cos, sqrt, pow, etc.)

#### 5.7 Boolean (Week 23)
- `Boolean.prototype.toString()`, `valueOf()`

#### 5.8 Date (Week 24)
- Date parsing and formatting
- Timezone handling
- All Date.prototype methods
- **Challenge:** No external library for timezone data
- **Solution:** Use `java.time.*` classes for implementation
  ```java
  public final class JSDate extends JSObject {
      private final Instant instant;  // Use java.time.Instant

      // Convert to/from JavaScript time (ms since epoch)
      // Use ZoneId for timezone operations
  }
  ```

#### 5.9 RegExp (Week 25)
- Integration with regexp engine from Phase 1
- `RegExp.prototype.exec()`
- `RegExp.prototype.test()`
- `RegExp.prototype.toString()`
- Flag properties (global, ignoreCase, multiline, etc.)

#### 5.10 Error Objects (Week 25)
- `Error`, `TypeError`, `ReferenceError`, `SyntaxError`, `RangeError`
- Stack trace generation
  ```java
  public class JSError extends JSObject {
      private final String message;
      private final List<StackTraceElement> jsStackTrace;

      public String getStack() {
          // Format JavaScript stack trace
      }
  }
  ```

#### 5.11 Promise (Week 26)
```java
public final class JSPromise extends JSObject {
    private enum State { PENDING, FULFILLED, REJECTED }

    private State state;
    private JSValue result;
    private List<PromiseReaction> fulfillReactions;
    private List<PromiseReaction> rejectReactions;

    public void fulfill(JSValue value) { }
    public void reject(JSValue reason) { }

    public JSPromise then(JSFunction onFulfilled, JSFunction onRejected) { }
    public JSPromise catch_(JSFunction onRejected) { }
    public JSPromise finally_(JSFunction onFinally) { }

    public static JSPromise all(JSArray promises) { }
    public static JSPromise race(JSArray promises) { }
    public static JSPromise resolve(JSValue value) { }
    public static JSPromise reject(JSValue reason) { }
}
```

#### 5.12 Async/Await Support (Week 26)
- Async function state machine
- Promise integration
- Await bytecode implementation

#### 5.13 Generators and Iterators (Week 27)
```java
public final class JSGenerator extends JSObject {
    private final StackFrame suspendedFrame;
    private State state;

    public JSValue next(JSValue value) { }
    public JSValue return_(JSValue value) { }
    public JSValue throw_(JSValue exception) { }
}

// Iterator protocol
public interface JSIterable {
    JSIterator iterator(JSContext ctx);
}
```

#### 5.14 Map, Set, WeakMap, WeakSet (Week 27)
```java
public final class JSMap extends JSObject {
    // Use LinkedHashMap to preserve insertion order
    private final Map<JSValue, JSValue> map;

    public void set(JSValue key, JSValue value) { }
    public JSValue get(JSValue key) { }
    public boolean has(JSValue key) { }
    public boolean delete(JSValue key) { }
    public void clear() { }
    public int size() { }
}

public final class JSWeakMap extends JSObject {
    // Use WeakHashMap
    private final WeakHashMap<JSObject, JSValue> map;
}
```

#### 5.15 Typed Arrays and ArrayBuffer (Week 28)
```java
public final class JSArrayBuffer extends JSObject {
    private final ByteBuffer buffer;  // Direct buffer for performance

    public JSArrayBuffer(int byteLength) {
        buffer = ByteBuffer.allocateDirect(byteLength);
    }
}

public abstract class JSTypedArray extends JSObject {
    protected final JSArrayBuffer buffer;
    protected final int byteOffset;
    protected final int length;
}

public final class JSInt8Array extends JSTypedArray { }
public final class JSUint8Array extends JSTypedArray { }
public final class JSInt16Array extends JSTypedArray { }
// ... all typed array types
```

#### 5.16 Proxy and Reflect (Week 28)
```java
public final class JSProxy extends JSObject {
    private final JSObject target;
    private final JSObject handler;

    // Trap methods
    private JSValue get(JSValue property) {
        // Call handler.get if exists, else target.get
    }

    // Implement all 13 proxy traps
}

public final class Reflect {
    public static JSValue get(JSObject target, JSValue property) { }
    public static boolean set(JSObject target, JSValue property, JSValue value) { }
    // ... all Reflect methods
}
```

### Phase 6: Runtime Environment (Weeks 29-32)

#### 6.1 JSContext and JSRuntime (Week 29)
```java
public final class JSRuntime {
    private final List<JSContext> contexts;
    private final GarbageCollector gc;
    private final AtomTable atoms;
    private final List<Job> jobQueue;  // for Promises

    public JSContext createContext() { }
    public void runJobs() {
        while (!jobQueue.isEmpty()) {
            Job job = jobQueue.remove(0);
            job.run();
        }
    }
}

public final class JSContext {
    private final JSRuntime runtime;
    private final JSObject globalObject;
    private final Map<String, JSModule> moduleCache;
    private final StackTrace currentStackTrace;

    public JSValue eval(String code) { }
    public JSModule loadModule(String specifier) { }
    public void throwError(String message) { }
}
```

#### 6.2 Event Loop and Job Queue (Week 29)
```java
public final class EventLoop {
    private final Queue<Job> jobQueue;
    private final Queue<Microtask> microtaskQueue;

    public void run() {
        while (hasWork()) {
            // Process microtasks first (Promises)
            while (!microtaskQueue.isEmpty()) {
                microtaskQueue.poll().run();
            }

            // Then process next job
            if (!jobQueue.isEmpty()) {
                jobQueue.poll().run();
            }
        }
    }
}
```

#### 6.3 Standard Library Bindings (Weeks 30-31)
- **Input:** `quickjs-libc.c`
- **Output:** `com.caoccao.qjs4j.stdlib.*`

**File I/O:**
```java
// std.open(), std.close(), std.read(), std.write()
public final class FileIO {
    public static JSValue open(JSContext ctx, JSValue[] args) {
        String filename = args[0].toString();
        String mode = args[1].toString();

        // Use java.nio.file.Files
        try {
            Path path = Paths.get(filename);
            FileChannel channel = /* ... */;
            return new JSFileHandle(channel);
        } catch (IOException e) {
            ctx.throwError("Cannot open file");
        }
    }
}
```

**Operating System Interface:**
- Process execution (limited, Java security constraints)
- Environment variables
- Working directory
- File system operations
  ```java
  public final class OS {
      public static JSValue chdir(String path) { }
      public static JSValue getcwd() {
          return new JSString(System.getProperty("user.dir"));
      }
      public static JSValue getenv(String name) {
          return new JSString(System.getenv(name));
      }
      public static JSValue readdir(String path) {
          // Use Files.list()
      }
  }
  ```

**Challenges:**
- Java security model restrictions
- No direct system call access
- Limited process control

**Solutions:**
- Use `java.nio.file.*` for file operations
- Use `ProcessBuilder` for subprocess execution
- Document limitations clearly

#### 6.4 Console API (Week 31)
```java
public final class Console {
    public static void log(Object... args) {
        System.out.println(formatArgs(args));
    }

    public static void error(Object... args) {
        System.err.println(formatArgs(args));
    }

    // table(), time(), timeEnd(), etc.
}
```

#### 6.5 setTimeout/setInterval (Week 32)
```java
public final class Timers {
    private static final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(4);

    private static int nextTimerId = 1;
    private static Map<Integer, ScheduledFuture<?>> timers =
        new ConcurrentHashMap<>();

    public static int setTimeout(JSFunction callback, long delayMs) {
        int id = nextTimerId++;
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            callback.call(null, null, new JSValue[0]);
            timers.remove(id);
        }, delayMs, TimeUnit.MILLISECONDS);
        timers.put(id, future);
        return id;
    }

    public static void clearTimeout(int id) {
        ScheduledFuture<?> future = timers.remove(id);
        if (future != null) future.cancel(false);
    }
}
```

### Phase 7: Tools and Testing (Weeks 33-36)

#### 7.1 Command-Line Interpreter (Week 33)
- **Input:** `qjs.c`
- **Output:** `com.caoccao.qjs4j.cli.QuickJSInterpreter`

```java
public final class QuickJSInterpreter {
    public static void main(String[] args) {
        if (args.length == 0) {
            runREPL();
        } else {
            runScript(args[0]);
        }
    }

    private static void runScript(String filename) {
        JSRuntime runtime = new JSRuntime();
        JSContext ctx = runtime.createContext();

        String code = Files.readString(Path.of(filename));
        JSValue result = ctx.eval(code);

        runtime.runJobs();  // Process promises
    }
}
```

#### 7.2 REPL (Week 33)
- **Input:** `repl.js`, `qjs.c`
- **Output:** `com.caoccao.qjs4j.cli.REPL`

```java
public final class REPL {
    private final JSContext ctx;
    private final BufferedReader reader;

    public void run() {
        while (true) {
            System.out.print("qjs> ");
            String line = reader.readLine();
            if (line == null) break;

            try {
                JSValue result = ctx.eval(line);
                if (!result.isUndefined()) {
                    System.out.println(stringify(result));
                }
            } catch (JSException e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
```

Features:
- Multi-line input
- History (use JDK's alternative or simple history)
- Tab completion (basic)
- Syntax highlighting (optional, terminal colors)

#### 7.3 Bytecode Compiler Tool (Week 34)
- **Input:** `qjsc.c`
- **Output:** `com.caoccao.qjs4j.cli.BytecodeCompiler`

```java
public final class BytecodeCompilerTool {
    public static void main(String[] args) {
        String inputFile = args[0];
        String outputFile = args[1];

        String code = Files.readString(Path.of(inputFile));

        Lexer lexer = new Lexer(code);
        Parser parser = new Parser(lexer);
        ASTNode ast = parser.parse();

        BytecodeCompiler compiler = new BytecodeCompiler();
        Bytecode bytecode = compiler.compile(ast);

        // Serialize to file
        try (ObjectOutputStream out =
            new ObjectOutputStream(new FileOutputStream(outputFile))) {
            out.writeObject(bytecode);
        }
    }
}
```

#### 7.4 Test262 Runner (Week 35)
- **Input:** `run-test262.c`
- **Output:** `com.caoccao.qjs4j.test.Test262Runner`

```java
public final class Test262Runner {
    public static void main(String[] args) {
        Path test262Dir = Path.of(args[0]);

        List<TestCase> tests = loadTests(test262Dir);
        int passed = 0;
        int failed = 0;

        for (TestCase test : tests) {
            if (runTest(test)) {
                passed++;
            } else {
                failed++;
                System.err.println("FAILED: " + test.name);
            }
        }

        System.out.printf("Result: %d/%d passed\n", passed, tests.size());
    }
}
```

#### 7.5 Unit Tests (Week 36)
Using only JUnit from JDK (or manual test framework):

```java
// Manual test framework (no external deps)
public class TestRunner {
    public static void main(String[] args) {
        runTests(
            ValueTests.class,
            ObjectTests.class,
            ArrayTests.class,
            FunctionTests.class,
            ParserTests.class,
            CompilerTests.class,
            VMTests.class,
            BuiltinTests.class
        );
    }
}

public class ValueTests {
    @Test
    public void testNumberConversion() {
        JSNumber num = new JSNumber(42.0);
        assertEquals(42, ToInt32(num));
    }

    @Test
    public void testStringConcatenation() {
        // ...
    }
}
```

Test coverage:
- Core value operations
- Type conversions
- Object property access
- Array operations
- Function calls
- Closure capture
- Parsing edge cases
- Bytecode generation
- VM execution
- All built-in functions
- Promise/async behavior
- Module loading
- Error handling

### Phase 8: Optimization and Polish (Weeks 37-40)

#### 8.1 Performance Optimization (Weeks 37-38)

**Profiling targets:**
1. Bytecode execution (hot loop)
2. Property access (shape transitions)
3. Type conversions
4. String operations
5. Array operations

**Optimizations:**

1. **Inline caching for property access**
```java
public final class InlineCache {
    private JSShape expectedShape;
    private int propertyOffset;

    public JSValue get(JSObject obj, int propertyKey) {
        if (obj.shape == expectedShape) {
            return obj.propertyValues[propertyOffset];  // Fast path
        }

        // Slow path: lookup and update cache
        int offset = obj.shape.getPropertyOffset(propertyKey);
        expectedShape = obj.shape;
        propertyOffset = offset;
        return obj.propertyValues[offset];
    }
}
```

2. **Value caching**
```java
// Cache small integers
private static final JSNumber[] INTEGER_CACHE = new JSNumber[256];
static {
    for (int i = 0; i < 256; i++) {
        INTEGER_CACHE[i] = new JSNumber(i);
    }
}

public static JSNumber valueOf(int i) {
    if (i >= 0 && i < 256) {
        return INTEGER_CACHE[i];
    }
    return new JSNumber(i);
}
```

3. **String interning**
- Aggressive use of atom table
- Reduce String object allocations

4. **Array specialization**
- Fast path for dense integer arrays
- Specialized storage for different types

5. **Bytecode optimizations**
- Constant folding during compilation
- Dead code elimination
- Peephole optimization

6. **JVM optimizations**
- Use `final` aggressively for JIT
- Avoid megamorphic call sites
- Use primitives where possible (instead of boxed)
- Consider using `VarHandle` for low-level operations (JDK 9+)

#### 8.2 Memory Optimization (Week 38)

1. **Object pooling**
```java
public final class ObjectPool<T> {
    private final Queue<T> pool;
    private final Supplier<T> factory;

    public T acquire() {
        T obj = pool.poll();
        return obj != null ? obj : factory.get();
    }

    public void release(T obj) {
        pool.offer(obj);
    }
}
```

2. **Reduce object allocation**
- Reuse AST nodes during parsing
- Pool frequently allocated objects
- Use primitive arrays where possible

3. **Optimize shape transitions**
- Share shapes across objects
- Minimize shape tree depth

#### 8.3 Documentation (Week 39)

1. **API Documentation**
   - Javadoc for all public APIs
   - Usage examples
   - Migration guide from C QuickJS

2. **Architecture Documentation**
   - System design overview
   - Module interactions
   - Data flow diagrams
   - Memory management strategy

3. **Developer Guide**
   - How to extend with native functions
   - How to embed in Java applications
   - Performance tuning guide

4. **User Guide**
   - Command-line usage
   - REPL features
   - Bytecode compilation
   - Module system

#### 8.4 Compatibility Testing (Week 40)

1. **Test262 compliance**
   - Run full test262 suite
   - Target: >99% pass rate (same as C QuickJS)
   - Document known failures

2. **Benchmark suite**
   - Port C benchmarks
   - Compare performance
   - Identify bottlenecks

3. **Real-world testing**
   - Run popular JavaScript libraries
   - Test web frameworks (if applicable)
   - Compatibility reports

---

## 4. Technical Challenges and Solutions

### 4.1 Value Representation

**Challenge:** C uses NaN-boxing for compact 64-bit value representation

**Solution Options:**
1. **Sealed interfaces (Recommended)**
   - Type-safe
   - Natural Java idiom
   - Slightly higher memory usage
   - Better for debugging

2. **Long-based boxing**
   - More compact
   - Requires careful bit manipulation
   - Harder to debug
   - Potential for errors

**Recommendation:** Use sealed interfaces for correctness, optimize later if needed

### 4.2 Memory Management

**Challenge:** C uses manual reference counting

**Solution:**
- Primary: JVM GC (simpler, fewer bugs)
- Secondary: Reference counting for external resources
- Hybrid: Weak references for cycle detection

### 4.3 Regular Expressions

**Challenge:** Cannot use `java.util.regex.Pattern` (different semantics)

**Solution:**
- Port the entire regex engine (3,448 lines)
- Implement bytecode compiler and interpreter
- Ensure exact ES2020 compatibility

### 4.4 Double-to-String Conversion

**Challenge:** Must match JavaScript exact formatting rules

**Solution:**
1. Port dtoa.c algorithm
2. Or use `Double.toString()` with post-processing
3. Extensive testing against test262

### 4.5 Date and Timezone

**Challenge:** No external libraries for timezone data

**Solution:**
- Use `java.time.*` package (part of JDK)
- `ZoneId`, `ZonedDateTime`, `Instant`
- May have slight differences from C implementation
- Document any incompatibilities

### 4.6 File I/O and OS Interface

**Challenge:** Java security model restrictions

**Solution:**
- Use `java.nio.file.*` exclusively
- `ProcessBuilder` for subprocess
- Document security implications
- May require security policy configuration

### 4.7 Performance

**Challenge:** Java typically slower than optimized C

**Mitigation:**
1. Aggressive optimization
2. Use JVM JIT compiler features
3. Minimize allocations
4. Cache frequently used values
5. Consider GraalVM native-image for production (still no external deps)

**Expected:** 2-5x slower than C, acceptable for many use cases

---

## 5. Project Structure

### 5.1 Build System

**Use:** Pure `javac` commands or simple shell script

**No Maven/Gradle** (to avoid external dependencies)

**Build script:**
```bash
#!/bin/bash
# build.sh

# Compile all sources
find src -name "*.java" > sources.txt
javac -d bin @sources.txt -source 17 -target 17

# Create JAR
jar cfe quickjs.jar com.caoccao.qjs4j.cli.QuickJSInterpreter -C bin .

# Run tests
java -cp bin com.caoccao.qjs4j.test.TestRunner
```

### 5.2 Directory Layout

```
quickjs-java/
├── src/
│   └── com/
│       └── quickjs/
│           ├── core/
│           ├── compiler/
│           ├── vm/
│           ├── memory/
│           ├── types/
│           ├── builtins/
│           ├── regexp/
│           ├── unicode/
│           ├── util/
│           ├── stdlib/
│           ├── cli/
│           └── test/
├── test/
│   ├── test262/
│   └── unit/
├── doc/
│   ├── API.md
│   ├── Architecture.md
│   └── UserGuide.md
├── examples/
│   ├── Embedding.java
│   └── NativeFunction.java
├── build.sh
├── run-tests.sh
└── README.md
```

---

## 6. Deliverables

### 6.1 Core Engine
- Full ES2020+ JavaScript parser
- Bytecode compiler
- Virtual machine with 244 opcodes
- All built-in objects and prototypes
- Module system
- Promise/async/await
- Generators
- Proxy/Reflect
- Regular expressions
- BigInt support

### 6.2 Runtime
- Garbage collection
- Event loop
- Job queue
- Standard library (file I/O, OS interface)
- Timer support

### 6.3 Tools
- Command-line interpreter
- REPL
- Bytecode compiler
- Test262 runner

### 6.4 Testing
- Unit test suite
- Test262 compliance (target: >99%)
- Integration tests
- Performance benchmarks

### 6.5 Documentation
- API documentation (Javadoc)
- Architecture guide
- User manual
- Developer guide

---

## 7. Estimated Effort

**Total Timeline:** 40 weeks (10 months)

**Team Size:** 3-5 experienced Java developers

**Breakdown:**
- Phase 1 (Foundation): 4 weeks
- Phase 2 (Object System): 4 weeks
- Phase 3 (Parser/Compiler): 6 weeks
- Phase 4 (Virtual Machine): 6 weeks
- Phase 5 (Built-ins): 8 weeks
- Phase 6 (Runtime): 4 weeks
- Phase 7 (Tools/Testing): 4 weeks
- Phase 8 (Optimization): 4 weeks

**Lines of Code:** ~50,000-70,000 LOC (estimated)

**Complexity:** Very High
- JavaScript spec is complex and has many edge cases
- Requires deep understanding of language semantics
- Performance optimization is challenging
- Testing requires extensive test262 suite

---

## 8. Risks and Mitigation

### 8.1 Risks

1. **Specification Complexity**
   - Risk: JavaScript spec has subtle edge cases
   - Mitigation: Use test262 extensively, study spec carefully

2. **Performance**
   - Risk: Java implementation may be too slow
   - Mitigation: Profile early, optimize hot paths, consider GraalVM

3. **Memory Usage**
   - Risk: Higher memory footprint than C
   - Mitigation: Object pooling, value caching, efficient data structures

4. **Compatibility**
   - Risk: Subtle differences from C QuickJS
   - Mitigation: Extensive testing, document differences

5. **Timeline**
   - Risk: Underestimating complexity
   - Mitigation: Iterative development, early prototypes

### 8.2 Success Criteria

1. Pass >99% of test262 suite
2. Run real-world JavaScript code correctly
3. Performance within 5x of C QuickJS
4. Memory usage acceptable for target use cases
5. Clean, maintainable Java code
6. Comprehensive documentation

---

## 9. Alternative Approaches

### 9.1 Minimal Subset
Instead of full ES2020, implement ES5 subset:
- Simpler, faster to develop
- Sufficient for many use cases
- Can expand later

### 9.2 JNI Wrapper
Wrap C QuickJS with JNI:
- Much faster to implement
- Keep C performance
- But: not "pure Java", has native dependencies

### 9.3 Use GraalJS
Adopt existing GraalVM JavaScript:
- Production-ready
- High performance
- But: requires GraalVM, not pure JDK

**Recommendation:** Full implementation as planned, for learning and control

---

## 10. Post-Conversion Enhancements

After basic conversion is complete, consider:

1. **JIT Compilation**
   - Compile hot bytecode to Java bytecode
   - Use `MethodHandle` or bytecode generation

2. **Multi-threading**
   - Worker threads
   - SharedArrayBuffer

3. **Debugging Support**
   - Debugger protocol
   - Source maps
   - Profiler

4. **IDE Integration**
   - Eclipse/IntelliJ plugins
   - Language server protocol

5. **Web APIs**
   - DOM simulation
   - Fetch API
   - WebAssembly (!)

---

## 11. Conclusion

Converting QuickJS from C to pure Java is a significant undertaking requiring deep expertise in:
- JavaScript language specification
- Compiler design
- Virtual machine implementation
- Java performance optimization

The project is feasible with the right team and timeline. The resulting engine would be:
- Pure Java (JDK 17+, no external dependencies)
- Embeddable in Java applications
- Fully featured JavaScript engine
- Cross-platform
- Maintainable and extensible

Success depends on careful attention to specification compliance, extensive testing, and iterative optimization.

---

## Appendix A: JDK 17 APIs Used

- `java.lang.*` - Core classes
- `java.util.*` - Collections, utilities
- `java.util.concurrent.*` - Threading, timers
- `java.math.BigInteger` - BigInt support
- `java.nio.*` - Buffers, channels
- `java.nio.file.*` - File I/O
- `java.time.*` - Date/time handling
- `java.util.regex.*` - Fallback regex (if needed)
- `java.io.*` - Streams, serialization
- `java.lang.ref.*` - Weak references

**No external dependencies!**

---

## Appendix B: Resources

1. **ECMAScript Specification**
   - https://tc39.es/ecma262/

2. **Test262 Test Suite**
   - https://github.com/tc39/test262

3. **QuickJS Documentation**
   - Original C implementation
   - doc/quickjs.pdf

4. **Reference Implementations**
   - V8 (Google)
   - SpiderMonkey (Mozilla)
   - JavaScriptCore (Apple)

---

## Appendix C: Open Questions

1. Should we support all QuickJS extensions (e.g., BigDecimal)?
2. What level of Node.js compatibility is desired?
3. Should we include a bytecode serialization format?
4. Performance target: what's acceptable slowdown vs C?
5. Should we support dynamic bytecode generation at runtime?

---

*Document Version: 1.0*
*Date: December 24, 2025*
*Author: QuickJS-Java Conversion Planning Team*
