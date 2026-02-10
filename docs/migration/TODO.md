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

## Opcode Migration Status

**Summary**: High, medium, and low-priority opcode migration is complete.

### High Priority Opcodes (Core Functionality)
- Completed on 2026-02-10.

### Medium Priority Opcodes (Enhanced Functionality)
- Completed on 2026-02-10.

### Low Priority Opcodes (Optimizations)
- Completed on 2026-02-10 (includes SHORT_OPCODES 179-244, `DEC_LOC`/`INC_LOC`/`ADD_LOC`, `NOP`, and `GET_ARRAY_EL3`).

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
1. Expand iterator opcode coverage for advanced iterator control flow
2. Improve closure and var-ref fidelity for full QuickJS parity

### Phase 3: Language Features
1. Tagged template literals
2. Private methods

### Phase 4: Advanced Features
1. Intl namespace (large undertaking)
2. AsyncDisposableStack and explicit resource management

### Phase 5: Optimizations
1. Performance profiling and optimization
2. Test262 conformance improvements

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
