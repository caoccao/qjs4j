# qjs4j TODO

This document tracks remaining features, known bugs, and planned enhancements for qjs4j.

**Last Updated**: 2026-02-10

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

---

## Opcodes Not Yet Implemented

**Summary**: High and medium-priority opcode migration is completed. Remaining work is low-priority opcode coverage and optimizations.

### High Priority Opcodes (Core Functionality)
- Completed on 2026-02-10.

### Medium Priority Opcodes (Enhanced Functionality)
- Completed on 2026-02-10.

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
1. Implement MEDIUM priority opcodes (see above)
2. Expand iterator opcode coverage for advanced iterator control flow
3. Improve closure and var-ref fidelity for full QuickJS parity

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
