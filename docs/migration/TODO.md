# qjs4j TODO

This document tracks remaining features, known bugs, and planned enhancements for qjs4j.

**Last Updated**: 2026-02-09

## Not Yet Implemented

### Medium Priority - Built-in Objects

#### Internationalization (Intl)
- **Status**: Not implemented
- **Scope**: Large undertaking - requires multiple constructors and prototypes
- **Objects needed**:
  - Intl.DateTimeFormat
  - Intl.NumberFormat
  - Intl.Collator
  - Intl.PluralRules
  - Intl.RelativeTimeFormat
  - Intl.ListFormat
  - Intl.Locale
- **Reference**: [FEATURES.md](FEATURES.md#not-yet-implemented-)

### Planned Features (ES2024+)

#### Explicit Resource Management
- **Status**: Planned - see [ASYNC_DISPOSABLE_STACK_PLAN.md](ASYNC_DISPOSABLE_STACK_PLAN.md)
- **Components needed**:
  - `Symbol.dispose` - Sync disposal symbol
  - `Symbol.asyncDispose` - Async disposal symbol
  - `DisposableStack` - Sync resource management
  - `AsyncDisposableStack` - Async resource management
  - `using` declaration syntax
  - `using await` declaration syntax
- **Estimated effort**: 14-21 hours

---

## Opcodes Not Yet Implemented

**Summary**: 144/262 opcodes fully implemented (55%), 118 defined but need VM handlers

### High Priority Opcodes (Core Functionality)

| Opcode | qjs4j # | Description |
|--------|---------|-------------|
| DUP1 | 139 | Stack: a b → a a b |
| INIT_CTOR | 140 | Initialize constructor |
| GET_VAR_UNDEF | 141 | Get var, return undefined if not exists |
| PUT_VAR_INIT | 142 | Initialize global lexical variable |
| SET_PROTO | 76 | Set prototype |
| SET_HOME_OBJECT | 77 | Set home object for super |
| GET_LOC | 85 | Get local variable |
| PUT_LOC | 86 | Set local variable |
| SET_LOC | 87 | Set local, keep value |
| GET_VAR_REF | 91 | Get var ref (closure) |
| PUT_VAR_REF | 92 | Set var ref |
| SET_VAR_REF | 93 | Set var ref, keep value |
| CLOSE_LOC | 103 | Close over local (create closure) |
| TO_STRING | 112 | Convert to string |
| ITERATOR_CHECK_OBJECT | 129 | Check iterator result |
| ITERATOR_GET_VALUE_DONE | 130 | Extract value and done |
| ITERATOR_CLOSE | 131 | Close iterator |
| ITERATOR_NEXT | 132 | Call iterator.next() |
| ITERATOR_CALL | 133 | Call iterator method |
| LNOT | 149 | Logical NOT (!) |
| POW | 158 | Exponentiation (**) |

### Medium Priority Opcodes (Enhanced Functionality)

- GET_REF_VALUE, PUT_REF_VALUE (59-60) - Reference operations
- GET_ARRAY_EL2 (68) - Array access variant
- SET_NAME, SET_NAME_COMPUTED (74-75) - Function naming
- DEFINE_ARRAY_EL, APPEND (78-79) - Array construction
- COPY_DATA_PROPERTIES (80) - Object spread
- DEFINE_METHOD_COMPUTED, DEFINE_CLASS_COMPUTED (82, 84) - Computed names
- Local TDZ check operations (94-102)
- Reference operations (119-122)
- GET_LENGTH (232) - Array length optimization
- NIP_CATCH (110) - Exception handling

### Low Priority Opcodes (Optimizations)

All SHORT_OPCODES (179-244) for performance optimization:
- PUSH_0 through PUSH_7
- PUSH_I8, PUSH_I16, PUSH_CONST8
- GET_LOC0-3, PUT_LOC0-3, SET_LOC0-3
- GET_ARG0-3, PUT_ARG0-3, SET_ARG0-3
- GET_VAR_REF0-3, PUT_VAR_REF0-3, SET_VAR_REF0-3
- IF_FALSE8, IF_TRUE8, GOTO8, GOTO16
- CALL0-3
- IS_UNDEFINED, IS_NULL, TYPEOF_IS_UNDEFINED, TYPEOF_IS_FUNCTION

**Reference**: [OPCODE_IMPLEMENTATION_STATUS.md](OPCODE_IMPLEMENTATION_STATUS.md)

---

## Known Bugs

### JavaScript-Java Bridge Issues

#### 1. JavaScript Native Iterables Not Accessible from Java
- **Description**: JSIteratorHelper cannot properly detect or work with JavaScript native objects (Set, Map, custom iterables with Symbol.iterator) because these objects exist in the JavaScript runtime and don't properly bridge to the Java layer
- **Impact**: Tests using `new Set()`, `new Map()`, or custom iterables in JavaScript eval() fail with "TypeError: Object is not iterable"
- **Workaround**: Use Java-created iterables (JSIterator, JSGenerator) instead
- **Reference**: [TESTING_NOTES.md](TESTING_NOTES.md#issues-encountered)

#### 2. JavaScript Evaluation Type Conversion
- **Description**: When evaluating JavaScript code that returns strings (e.g., `JSON.stringify(obj)`), the `result.toJavaObject()` method returns "[object Object]" instead of the actual string value
- **Impact**: Tests expecting string results fail
- **Affected**: `JSON.stringify()`, property descriptor checks, revoked proxy behavior
- **Reference**: [TESTING_NOTES.md](TESTING_NOTES.md#2-javascript-evaluation-type-conversion-issues)

#### 3. Proxy.revocable Tests Failing
- **Description**: Accessing revoked proxies doesn't throw expected TypeError; type conversions fail when checking proxy values
- **Status**: Tests disabled (ProxyConstructorTest.java.disabled)
- **Reference**: [TESTING_NOTES.md](TESTING_NOTES.md#3-proxyrevocable-tests-not-working)

---

## Test Coverage Gaps

### Disabled Test Files
- `ProxyConstructorTest.java.disabled` - Proxy.revocable tests
- `ReflectObjectTest.java.disabled` - Reflect object method tests

### Tests Needing Implementation
From [TEST262.md](TEST262.md):
- Full Test262 conformance suite integration
- Async iteration integration tests (for-await-of with various iterables)
- Break/continue in nested for-await-of loops
- Error handling in async iteration

From [ASYNC_AWAIT_ENHANCEMENTS.md](ASYNC_AWAIT_ENHANCEMENTS.md):
- Destructuring patterns in for-of loops (`for (const {x, y} of items)`)
- Full promise awaiting in async loops

---

## Documentation TODOs

- [ ] Update Test262 baseline after conformance run
- [ ] Document JavaScript-Java value conversion patterns
- [ ] Add troubleshooting guide for common issues
- [ ] Create performance benchmarking guide

---

## Implementation Recommendations

### Phase 1: Bug Fixes (High Value)
1. Fix JavaScript-Java bridge for native iterables
2. Fix JSValue.toJavaObject() string conversion
3. Fix Proxy.revocable behavior

### Phase 2: Core Opcodes (Essential)
1. Implement HIGH priority opcodes (see above)
2. Focus on iterator operations for better for-of support
3. Add closure variable opcodes (GET/PUT/SET_VAR_REF)

### Phase 3: Language Features
1. Tagged template literals
2. Private methods

### Phase 4: Advanced Features
1. Intl namespace (large undertaking)
2. AsyncDisposableStack and explicit resource management

### Phase 5: Optimizations
1. SHORT_OPCODES implementation
2. Performance profiling and optimization
3. Test262 conformance improvements

---

## References

- [FEATURES.md](FEATURES.md) - Complete feature implementation status
- [MIGRATION_STATUS.md](MIGRATION_STATUS.md) - Migration progress from QuickJS C
- [OPCODE_IMPLEMENTATION_STATUS.md](OPCODE_IMPLEMENTATION_STATUS.md) - Bytecode instruction coverage
- [TESTING_NOTES.md](TESTING_NOTES.md) - Known test issues
- [TEST262.md](TEST262.md) - ECMAScript conformance test runner plan
- [ASYNC_AWAIT_ENHANCEMENTS.md](ASYNC_AWAIT_ENHANCEMENTS.md) - Async/await implementation details
- [PRIVATE_FIELDS_IMPLEMENTATION.md](PRIVATE_FIELDS_IMPLEMENTATION.md) - Class features status
- [ASYNC_DISPOSABLE_STACK_PLAN.md](ASYNC_DISPOSABLE_STACK_PLAN.md) - Planned ES2024 feature
