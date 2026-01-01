# AsyncDisposableStack Implementation Plan

**Status**: Planned (Not Yet Implemented)  
**Date**: January 1, 2026  
**Target**: ES2024 Explicit Resource Management

## Overview

AsyncDisposableStack is part of the ECMAScript Explicit Resource Management proposal (ES2024), providing async resource cleanup capabilities. This document outlines the implementation plan for adding AsyncDisposableStack to qjs4j.

## Background

### What is AsyncDisposableStack?

AsyncDisposableStack provides a mechanism to track async disposable resources and dispose them in LIFO order. It works with the `using await` syntax and `Symbol.asyncDispose`.

**Key features:**
- Tracks multiple async disposable resources
- Disposes resources in LIFO (last-in-first-out) order
- Returns Promises for all disposal operations
- Aggregates multiple disposal errors into AggregateError
- Integrates with `Symbol.asyncDispose` protocol

### Specification

From the TC39 Explicit Resource Management proposal:

```javascript
const stack = new AsyncDisposableStack();

// Add resources
stack.use(asyncResource);           // Uses Symbol.asyncDispose
stack.adopt(value, async (v) => {}); // Custom async disposal
stack.defer(async () => {});         // Deferred async cleanup

// Dispose all (LIFO order)
await stack.disposeAsync();          // or stack[Symbol.asyncDispose]()

// Check state
console.log(stack.disposed);         // true
```

## Current State Analysis

### Existing Infrastructure (✅ Available)

qjs4j has strong foundation for AsyncDisposableStack:

1. **Promise Implementation** — Complete JSPromise, PromiseConstructor, PromisePrototype
2. **Async Infrastructure** — Microtask queue, async generators, async iterators
3. **Error Handling** — AggregateError support exists
4. **Built-in Patterns** — Clear constructor/prototype patterns for all built-ins

### Missing Components (❌ To Be Added)

1. **Symbols** — `Symbol.dispose` and `Symbol.asyncDispose` not defined
2. **Core Class** — JSAsyncDisposableStack class doesn't exist
3. **Builtins** — AsyncDisposableStackConstructor and AsyncDisposableStackPrototype
4. **VM Integration** — Constructor handling in VirtualMachine
5. **Tests** — Comprehensive test coverage

## Implementation Plan

### Phase 1: Symbol Support

**Files to modify:**
- `src/main/java/com/caoccao/qjs4j/core/JSSymbol.java`
- `src/main/java/com/caoccao/qjs4j/builtins/SymbolConstructor.java`

**Changes:**

Add two new well-known symbols to JSSymbol.java:
```java
public static final JSSymbol DISPOSE = new JSSymbol("Symbol.dispose", WELL_KNOWN_ID_START + 13);
public static final JSSymbol ASYNC_DISPOSE = new JSSymbol("Symbol.asyncDispose", WELL_KNOWN_ID_START + 14);
```

Update `getWellKnownSymbol()` switch statement to include the new symbols.

Expose symbols in SymbolConstructor.java:
```java
public static JSValue getDispose(JSContext context, JSValue thisArg, JSValue[] args) {
    return JSSymbol.DISPOSE;
}

public static JSValue getAsyncDispose(JSContext context, JSValue thisArg, JSValue[] args) {
    return JSSymbol.ASYNC_DISPOSE;
}
```

Register getters in GlobalObject initialization:
```java
symbolConstructor.defineProperty(PropertyKey.fromString("dispose"),
    PropertyDescriptor.accessorDescriptor(
        new JSNativeFunction("get dispose", 0, SymbolConstructor::getDispose),
        null, false, false));
symbolConstructor.defineProperty(PropertyKey.fromString("asyncDispose"),
    PropertyDescriptor.accessorDescriptor(
        new JSNativeFunction("get asyncDispose", 0, SymbolConstructor::getAsyncDispose),
        null, false, false));
```

### Phase 2: Core Class Implementation

**File to create:**
- `src/main/java/com/caoccao/qjs4j/core/JSAsyncDisposableStack.java`

**Class structure:**
```java
public final class JSAsyncDisposableStack extends JSObject {
    private final JSContext context;
    private final List<DisposableResource> stack;
    private boolean disposed;
    
    // Represents a tracked resource
    private static class DisposableResource {
        enum Type { USE, ADOPT, DEFER }
        Type type;
        JSValue value;
        JSFunction onDisposeAsync; // for ADOPT and DEFER
        
        DisposableResource(Type type, JSValue value, JSFunction onDisposeAsync) {
            this.type = type;
            this.value = value;
            this.onDisposeAsync = onDisposeAsync;
        }
    }
    
    public JSAsyncDisposableStack(JSContext context) {
        super();
        this.context = context;
        this.stack = new ArrayList<>();
        this.disposed = false;
    }
    
    public boolean isDisposed() { 
        return disposed; 
    }
    
    public void addResource(DisposableResource.Type type, JSValue value, JSFunction onDisposeAsync) {
        if (disposed) {
            throw new JSException("Cannot add to disposed stack");
        }
        stack.add(new DisposableResource(type, value, onDisposeAsync));
    }
    
    public JSPromise disposeAsync() {
        // Implementation details below
    }
    
    public JSAsyncDisposableStack move() {
        // Creates new stack, transfers resources, marks this disposed
    }
}
```

**Disposal Logic:**

The `disposeAsync()` method is the core:

1. If already disposed, return fulfilled promise
2. Mark `disposed = true`
3. Process resources in **reverse order** (LIFO)
4. For each resource:
   - **USE**: Get `Symbol.asyncDispose` or `Symbol.dispose` method, call it
   - **ADOPT/DEFER**: Call the stored `onDisposeAsync` function
5. Chain all disposal promises sequentially (await each)
6. Collect errors during disposal
7. If errors occurred:
   - Single error: reject with that error
   - Multiple errors: reject with AggregateError
8. Otherwise: fulfill with undefined

**Error Aggregation Pattern:**
```java
List<JSValue> errors = new ArrayList<>();

// Process each resource
for (int i = stack.size() - 1; i >= 0; i--) {
    DisposableResource resource = stack.get(i);
    try {
        // Perform disposal (await promise if async)
        JSPromise disposalPromise = getDisposalPromise(resource);
        // Wait for promise and catch errors
    } catch (Exception e) {
        errors.add(createErrorValue(e));
    }
}

// Final promise resolution
if (!errors.isEmpty()) {
    if (errors.size() == 1) {
        return JSPromise.reject(errors.get(0));
    } else {
        JSValue aggregateError = createAggregateError(errors);
        return JSPromise.reject(aggregateError);
    }
}
return JSPromise.resolve(JSUndefined.INSTANCE);
```

### Phase 3: Built-in Constructor and Prototype

**Files to create:**
- `src/main/java/com/caoccao/qjs4j/builtins/AsyncDisposableStackConstructor.java`
- `src/main/java/com/caoccao/qjs4j/builtins/AsyncDisposableStackPrototype.java`

**AsyncDisposableStackConstructor.java:**

Currently no static methods in the spec, so this will be minimal:
```java
public final class AsyncDisposableStackConstructor {
    private AsyncDisposableStackConstructor() {}
    
    // Future: Could add utility methods like AsyncDisposableStack.from()
}
```

**AsyncDisposableStackPrototype.java:**

All instance methods following standard signature pattern:
```java
public final class AsyncDisposableStackPrototype {
    private AsyncDisposableStackPrototype() {}
    
    public static JSValue use(JSContext context, JSValue thisArg, JSValue[] args) {
        // Validate thisArg is AsyncDisposableStack
        if (!(thisArg instanceof JSAsyncDisposableStack stack)) {
            return context.throwTypeError("Method called on non-AsyncDisposableStack");
        }
        
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        
        // Check for Symbol.asyncDispose or Symbol.dispose
        JSValue disposeMethod = value.get(PropertyKey.fromSymbol(JSSymbol.ASYNC_DISPOSE));
        if (disposeMethod.isNullOrUndefined()) {
            disposeMethod = value.get(PropertyKey.fromSymbol(JSSymbol.DISPOSE));
        }
        
        if (!disposeMethod.isCallable()) {
            return context.throwTypeError("Value is not disposable");
        }
        
        // Add to stack
        stack.addResource(DisposableResource.Type.USE, value, null);
        
        return value;
    }
    
    public static JSValue adopt(JSContext context, JSValue thisArg, JSValue[] args) {
        // Similar validation
        // Extract value and onDisposeAsync callback
        // Validate callback is function
        // Add to stack with ADOPT type
    }
    
    public static JSValue defer(JSContext context, JSValue thisArg, JSValue[] args) {
        // Validate thisArg
        // Extract callback
        // Add to stack with DEFER type
        // Return undefined
    }
    
    public static JSValue move(JSContext context, JSValue thisArg, JSValue[] args) {
        // Validate thisArg
        // Call stack.move()
        // Return new stack
    }
    
    public static JSValue disposeAsync(JSContext context, JSValue thisArg, JSValue[] args) {
        // Validate thisArg
        // Call stack.disposeAsync()
        // Return promise
    }
    
    public static JSValue getDisposed(JSContext context, JSValue thisArg, JSValue[] args) {
        // Getter for disposed property
        // Validate thisArg
        // Return JSBoolean.of(stack.isDisposed())
    }
}
```

### Phase 4: Global Registration

**Files to modify:**
- `src/main/java/com/caoccao/qjs4j/types/ConstructorType.java`
- `src/main/java/com/caoccao/qjs4j/builtins/GlobalObject.java`
- `src/main/java/com/caoccao/qjs4j/vm/VirtualMachine.java`

**ConstructorType.java:**
```java
public enum ConstructorType {
    // ... existing types
    ASYNC_DISPOSABLE_STACK,
    // ...
}
```

**GlobalObject.java:**

Add initialization method:
```java
private static void initializeAsyncDisposableStackConstructor(JSContext context, JSObject global) {
    // Create prototype object
    JSObject prototype = new JSObject();
    
    // Add instance methods
    prototype.set("use", 
        new JSNativeFunction("use", 1, AsyncDisposableStackPrototype::use));
    prototype.set("adopt", 
        new JSNativeFunction("adopt", 2, AsyncDisposableStackPrototype::adopt));
    prototype.set("defer", 
        new JSNativeFunction("defer", 1, AsyncDisposableStackPrototype::defer));
    prototype.set("move", 
        new JSNativeFunction("move", 0, AsyncDisposableStackPrototype::move));
    prototype.set("disposeAsync", 
        new JSNativeFunction("disposeAsync", 0, AsyncDisposableStackPrototype::disposeAsync));
    
    // Symbol.asyncDispose points to same method as disposeAsync
    prototype.set(PropertyKey.fromSymbol(JSSymbol.ASYNC_DISPOSE),
        new JSNativeFunction("[Symbol.asyncDispose]", 0, AsyncDisposableStackPrototype::disposeAsync));
    
    // Add disposed property (getter only)
    JSNativeFunction disposedGetter = new JSNativeFunction("get disposed", 0, 
        AsyncDisposableStackPrototype::getDisposed);
    prototype.defineProperty(PropertyKey.fromString("disposed"),
        PropertyDescriptor.accessorDescriptor(disposedGetter, null, false, true));
    
    // Create constructor object
    JSObject constructor = new JSObject();
    constructor.set("prototype", prototype);
    constructor.setConstructorType(ConstructorType.ASYNC_DISPOSABLE_STACK);
    prototype.set("constructor", constructor);
    
    // Register in global scope
    global.set("AsyncDisposableStack", constructor);
}
```

Call from `initialize()`:
```java
public static void initialize(JSContext context, JSObject global) {
    // ... existing initializations
    initializeAsyncDisposableStackConstructor(context, global);
    // ...
}
```

**VirtualMachine.java:**

Add constructor handling in `handleCallConstructor()` method:
```java
private void handleCallConstructor(int argCount) {
    // ... existing code to collect args and get constructor
    
    if (ctorObj.getConstructorType() == ConstructorType.ASYNC_DISPOSABLE_STACK) {
        // Create new AsyncDisposableStack instance
        JSAsyncDisposableStack stack = new JSAsyncDisposableStack(context);
        
        // Set prototype
        JSValue prototypeValue = ctorObj.get("prototype");
        if (prototypeValue instanceof JSObject prototype) {
            stack.setPrototype(prototype);
        }
        
        // No constructor arguments in spec - just create empty stack
        
        valueStack.push(stack);
        return;
    }
    
    // ... rest of constructor handling
}
```

### Phase 5: Testing

**File to create:**
- `src/test/java/com/caoccao/qjs4j/builtins/AsyncDisposableStackTest.java`

**Test coverage:**

```java
public class AsyncDisposableStackTest extends BaseTest {
    
    @Test
    void testConstructor() {
        // new AsyncDisposableStack() creates instance
        // Initial state: disposed === false
    }
    
    @Test
    void testUse() {
        // Adds resource with Symbol.asyncDispose
        // Adds resource with Symbol.dispose (fallback)
        // Returns the value
        // Throws when already disposed
        // Throws when value not disposable
    }
    
    @Test
    void testAdopt() {
        // Adds value with custom callback
        // Returns the value
        // Throws when already disposed
        // Throws when callback not a function
    }
    
    @Test
    void testDefer() {
        // Adds callback
        // Returns undefined
        // Throws when already disposed
        // Throws when argument not a function
    }
    
    @Test
    void testMove() {
        // Creates new stack with resources
        // Original becomes disposed
        // Returns new AsyncDisposableStack
        // Moved stack can be disposed
    }
    
    @Test
    void testDisposeAsync() {
        // Disposes in LIFO order
        // Returns Promise
        // Multiple resources disposed correctly
        // Second call returns resolved promise
    }
    
    @Test
    void testSymbolAsyncDispose() {
        // Same behavior as disposeAsync()
    }
    
    @Test
    void testDisposedGetter() {
        // Returns false initially
        // Returns true after disposal
    }
    
    @Test
    void testLifoOrder() {
        // Resources disposed in reverse order of addition
        // Track disposal order with side effects
    }
    
    @Test
    void testErrorAggregation() {
        // Single error: rejected with that error
        // Multiple errors: rejected with AggregateError
        // AggregateError.errors contains all errors
    }
    
    @Test
    void testAsyncDisposal() {
        // Async dispose methods are awaited
        // Disposal sequence is async
    }
    
    @Test
    void testMixedDisposeTypes() {
        // use() with Symbol.asyncDispose
        // use() with Symbol.dispose (sync fallback)
        // adopt() with async callback
        // defer() with async callback
        // All work together
    }
}
```

Test pattern example:
```java
@Test
void testLifoOrder() {
    String script = """
        const log = [];
        const stack = new AsyncDisposableStack();
        
        stack.defer(async () => { log.push(1); });
        stack.defer(async () => { log.push(2); });
        stack.defer(async () => { log.push(3); });
        
        await stack.disposeAsync();
        
        JSON.stringify(log); // Should be [3, 2, 1]
        """;
    
    JSValue result = context.eval(script);
    assertEquals("[3,2,1]", result.asString());
}
```

## Dependencies & Prerequisites

### Required Before Implementation

1. **Symbol Support** — Must be implemented first (Phase 1)
2. **Promise Infrastructure** — Already complete ✅
3. **Microtask Queue** — Already working ✅
4. **AggregateError** — Already exists ✅

### Related Features (Optional)

1. **DisposableStack** — Synchronous version using `Symbol.dispose`
   - Not required for AsyncDisposableStack to work
   - Could be implemented in parallel or separately
   - Shares similar patterns but simpler (no Promises)

2. **`using await` Syntax** — Language-level support
   - AsyncDisposableStack works without it
   - Requires parser/compiler changes
   - Can be added later as syntax sugar

3. **Complete await expression support** — Currently partial
   - AsyncDisposableStack doesn't require it
   - Migration docs indicate microtask infrastructure is complete
   - Await expressions in progress

## Implementation Estimates

### Files to Create (4-5 files)
- `core/JSAsyncDisposableStack.java` — ~300-350 lines
- `builtins/AsyncDisposableStackConstructor.java` — ~20-30 lines (minimal)
- `builtins/AsyncDisposableStackPrototype.java` — ~200-250 lines
- `test/builtins/AsyncDisposableStackTest.java` — ~400-500 lines

**Total new code: ~900-1130 lines**

### Files to Modify (5 files)
- `core/JSSymbol.java` — +3 lines (symbol definitions)
- `builtins/SymbolConstructor.java` — +8 lines (getters)
- `types/ConstructorType.java` — +1 line (enum entry)
- `builtins/GlobalObject.java` — +40-50 lines (initialization)
- `vm/VirtualMachine.java` — +15-20 lines (constructor handling)

**Total modifications: ~67-82 lines**

### Complexity Assessment

**Medium Complexity**

Challenges:
- Promise chaining for sequential async disposal
- Error aggregation with AggregateError
- LIFO ordering must be maintained correctly
- State management (disposed flag, preventing operations after disposal)

Advantages:
- Clear patterns exist for all built-in objects
- Promise infrastructure is robust
- Similar to existing async patterns (AsyncGenerator)
- Specification is well-defined

### Estimated Timeline

- **Phase 1** (Symbols): 1-2 hours
- **Phase 2** (Core class): 4-6 hours
- **Phase 3** (Builtins): 3-4 hours
- **Phase 4** (Registration): 2-3 hours
- **Phase 5** (Tests): 4-6 hours

**Total: ~14-21 hours of development time**

## Open Questions

1. **DisposableStack Implementation**
   - Implement alongside AsyncDisposableStack?
   - Or separate implementation task?
   - Shared utility code possible?

2. **Await Expression Support**
   - Does partial await support impact AsyncDisposableStack?
   - Can AsyncDisposableStack be fully tested without complete await?
   - Should await expressions be completed first?

3. **Spec Version**
   - Which version of the proposal to target?
   - Any proposed changes not yet finalized?

4. **Using Syntax**
   - Parser support for `using await` declarations?
   - Or just runtime support initially?

## References

- **TC39 Proposal**: [Explicit Resource Management](https://github.com/tc39/proposal-explicit-resource-management)
- **Related qjs4j Docs**:
  - [ASYNC_AWAIT_IMPLEMENTATION.md](ASYNC_AWAIT_IMPLEMENTATION.md)
  - [MIGRATION_STATUS.md](MIGRATION_STATUS.md)
  - [PROMISE_PROTOTYPE_FIX.md](PROMISE_PROTOTYPE_FIX.md)

## Next Steps

1. Review this plan for accuracy and completeness
2. Decide on DisposableStack (sync version) approach
3. Assess await expression support status
4. Proceed with Phase 1 (Symbol support) implementation
5. Iterative development through Phases 2-5
6. Update MIGRATION_STATUS.md upon completion

---

**Document Status**: ✅ Planning Complete — Ready for Implementation Review
