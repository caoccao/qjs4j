# Unimplemented Opcodes

qjs4j defines 244 opcodes matching QuickJS's `quickjs-opcode.h` numbering. Of these, 15 map to `handleInvalid` and are never executed. This document explains why each one is unnecessary.

## Stack Manipulation

### NIP1 (16) — `a b c -> b c`

Dead code in QuickJS itself. Both emission sites in `quickjs.c` (lines 25292 and 26723) are inside `#else` branches of `#if 1` blocks. The active `#if 1` branch uses `OP_append` for array spread operations. The `#else` branch is an alternative implementation using a manual `for_of_start`/`for_of_next` loop that cleans up the 3-slot iterator record with three `OP_nip1` calls. Since QuickJS never compiles the `#else` path, qjs4j does not implement this opcode.


### ROT5L (32) — `x a b c d -> a b c d x`

Used in QuickJS inside `#else` dead code blocks (lines 25277, 26708) alongside `OP_nip1` for the manual spread loop. Also used at line 25946 for destructuring. qjs4j's compiler does not emit this opcode. The `APPEND` opcode handles spread, and destructuring uses other stack manipulation opcodes.

## Function Calls

### CALL_METHOD (36) — `obj func args -> ret`

A combined method-call opcode in QuickJS that pops both the receiver object and the function, calls with the object as `this`, and pushes the result. qjs4j compiles method calls (`obj.method(args)`) as separate steps: `DUP` the object, `GET_FIELD` to load the method, then `CALL` with the receiver already on the stack. This achieves the same result using existing opcodes.

### TAIL_CALL_METHOD (37) — `obj func args ->`

The tail-call variant of `CALL_METHOD`. Not emitted by qjs4j's compiler. Method calls in tail position use `TAIL_CALL` instead, with the same DUP+GET_FIELD decomposition as `CALL_METHOD`.

## Constructor Checks

### CHECK_CTOR_RETURN (42) — check constructor return value

In QuickJS, emitted at the end of derived class constructors to verify the return value is either an object or `undefined`. qjs4j performs this validation in Java at the `VirtualMachine.constructFunction()` method (lines 486-513) after the constructor returns, rather than as a bytecode instruction. The VM checks: if the result is a `JSObject`, return it; if derived and not `undefined`, throw `TypeError`; if derived and `super()` was never called, throw `ReferenceError`.

### CHECK_CTOR (43) — check that function is called as constructor

In QuickJS, emitted at the start of class constructors to enforce they must be invoked with `new`. qjs4j handles this at the Java level. The `CALL_CONSTRUCTOR` opcode handler and `VirtualMachine.constructFunction()` validate constructibility before execution. `INIT_CTOR` handles derived constructor initialization. Class constructors that are called without `new` never reach the constructor body because the `CALL` path does not set up the constructor context.

## Private Class Members

### CHECK_BRAND (45) — `this_obj func -> this_obj func`

In QuickJS, verifies that an object has the private brand of a class before accessing private methods. This prevents accessing private methods on objects that are not instances of the class. qjs4j uses `JSSymbol`-based keys for private members. Access control is enforced by the compiler: only code inside the class body has access to the private symbol references. The symbols are not exposed to external code, so the brand check is unnecessary.

### ADD_BRAND (46) — `this_obj home_obj ->`

In QuickJS, adds a private brand symbol to a newly constructed object so that `CHECK_BRAND` can later verify it. Since qjs4j does not use the brand mechanism (relying on symbol-based encapsulation instead), this opcode is not needed.

## Regular Expressions

### REGEXP (52) — `pattern bytecode_string -> regexp`

In QuickJS C, the compiler compiles a regex literal into two constants: the pattern string and a regex bytecode string. At runtime, the `regexp` opcode pops both and assembles a `JSRegExp` object. qjs4j eliminates this two-phase approach. The compiler at `ExpressionCompiler.java:374` calls `new JSRegExp(pattern, flags)`, which runs `RegExpCompiler.compile()` to produce `RegExpBytecode` immediately. The fully constructed `JSRegExp` object is stored directly in the constant pool and pushed onto the stack via `PUSH_CONST`. Java's object system allows storing the complete regex object as a constant, unlike C which can only store raw byte sequences.

## Modules

### IMPORT (54) — `specifier options -> promise`

Implements ES6 dynamic `import()` expressions. qjs4j supports static `import`/`export` declarations for ES modules but does not support dynamic `import()` calls. The parser recognizes `import(` syntax and routes it to expression parsing, but the compiler has no emission path for the `IMPORT` opcode. This is a known unsupported feature.

## Property Access

### GET_FIELD2 (62) — `obj -> obj val`

Gets a named property while keeping the object on the stack. In QuickJS, this is an optimization used in property access chains, optional chaining, and compound member expressions. qjs4j achieves the same stack effect by emitting `DUP` before `GET_FIELD`: the object is duplicated first, then one copy is consumed by `GET_FIELD`, leaving the original object and the retrieved value on the stack.

## With Statement

### WITH_GET_VAR (113), WITH_PUT_VAR (114), WITH_DELETE_VAR (115), WITH_MAKE_REF (116), WITH_GET_REF (117)

In QuickJS, these five opcodes handle variable access inside `with` statement scopes. Each is a 10-byte instruction encoding an atom name, a label offset, and a flags byte. The VM checks whether the variable exists as a property of the with-object; if found, accesses it there; otherwise jumps to the label to fall back to normal scope lookup.

qjs4j implements `with` statement support entirely at compile time without dedicated opcodes. The compiler tracks active with-objects via `CompilerContext.withObjectLocalStack` and emits inline code for each identifier access within a `with` scope:

1. Load the with-object from its local variable (`GET_LOC`)
2. Check if the property exists (`IN` operator, respecting `Symbol.unscopables`)
3. If found: access the property on the with-object (`GET_FIELD` / `PUT_FIELD`)
4. If not found: branch to normal variable access (`GET_VAR` / `PUT_VAR`)

This generates more bytecode per identifier access but avoids the need for the complex 10-byte `WITH_*` opcodes. Nested `with` scopes are handled by recursive code generation that checks each with-object in order.
