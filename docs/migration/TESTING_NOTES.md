# Testing Notes for Migration Phases 34-38

## Summary

Tests were added for migration phases 34-38 and later regression fixes:
- Phase 34-35: Object extensibility methods
- Phase 36: Proxy.revocable and Reflect methods
- Phase 37: Promise iterable handling
- Phase 38: Array.from()/Array.fromAsync() iterable handling
- Phase 1 bug-fix follow-up: constructor iterable bridge + Java string conversion parity

## Current Status

- `ProxyConstructorTest.java` is enabled.
- `ReflectObjectTest.java` is enabled.
- Native iterables are covered by constructor and Promise/Array tests.
- `JSValue.toJavaObject()` string conversion is covered by direct regression tests.

## Resolved Issues

### 1. JavaScript Native Iterables from Java Evaluation
**Previous issue:** constructor paths only accepted internal iterator types and skipped generic iterator objects.

**Resolution:** Set/Map/WeakMap/WeakSet constructors now:
- require iterability for non-null constructor input,
- use the generic iterator protocol,
- reject invalid iterator results,
- keep entry/value validation behavior aligned with QuickJS expectations.

### 2. JavaScript Evaluation String Conversion
**Previous issue:** string results from eval() were reported as object-like values in some test flows.

**Resolution:** regression coverage now directly asserts:
- primitive string eval conversion,
- `String(...)` conversion,
- `JSON.stringify(...)` conversion via `toJavaObject()`.

### 3. Proxy.revocable Regression Coverage
**Previous issue:** revocation behavior previously required disabled tests.

**Resolution:** Proxy revocation tests are active, including:
- access/set failures after revoke,
- repeated revoke calls.

## Recommendations

1. Keep adding constructor-edge iterable tests whenever iterator protocol code changes.
2. Keep using `assert*WithJavet()` for behavior parity and direct `toJavaObject()` assertions for bridge regressions.
3. Re-run targeted builtins tests before full-suite runs to isolate protocol regressions quickly.
