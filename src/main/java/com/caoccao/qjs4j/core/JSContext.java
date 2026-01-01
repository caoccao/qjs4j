/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.builtins.GlobalObject;
import com.caoccao.qjs4j.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import com.caoccao.qjs4j.types.JSModule;

import java.util.*;

/**
 * Represents a JavaScript execution context.
 * Based on QuickJS JSContext structure.
 * <p>
 * A context is an independent JavaScript execution environment with:
 * - Its own global object and built-in objects
 * - Its own module cache
 * - Its own call stack and exception state
 * - Shared runtime resources (atoms, GC, job queue)
 * <p>
 * Multiple contexts can exist in a single runtime, each isolated
 * from the others (separate globals, separate module namespaces).
 */
public final class JSContext implements AutoCloseable {
    private static final int DEFAULT_MAX_STACK_DEPTH = 1000;
    // Call stack management
    private final Deque<StackFrame> callStack;
    // Stack trace capture
    private final List<StackTraceElement> errorStackTrace;
    private final JSObject globalObject;
    // Microtask queue for promise resolution and async operations
    private final JSMicrotaskQueue microtaskQueue;
    private final Map<String, JSModule> moduleCache;
    private final JSRuntime runtime;
    private final com.caoccao.qjs4j.vm.VirtualMachine virtualMachine;
    private JSValue currentThis;
    private boolean inCatchHandler;
    private int maxStackDepth;
    // Exception state
    private JSValue pendingException;
    private int stackDepth;
    // Execution state
    private boolean strictMode;

    /**
     * Create a new execution context.
     */
    public JSContext(JSRuntime runtime) {
        this.runtime = runtime;
        this.globalObject = new JSObject();
        this.moduleCache = new HashMap<>();
        this.callStack = new ArrayDeque<>();
        this.stackDepth = 0;
        this.maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
        this.pendingException = null;
        this.inCatchHandler = false;
        this.strictMode = false;
        this.currentThis = globalObject;
        this.errorStackTrace = new ArrayList<>();
        this.microtaskQueue = new JSMicrotaskQueue();
        this.virtualMachine = new com.caoccao.qjs4j.vm.VirtualMachine(this);

        initializeGlobalObject();
    }

    /**
     * Capture stack trace when exception is thrown.
     */
    private void captureErrorStackTrace() {
        errorStackTrace.clear();
        for (StackFrame frame : callStack) {
            errorStackTrace.add(new StackTraceElement(
                    "JavaScript",
                    frame.functionName,
                    frame.filename,
                    frame.lineNumber
            ));
        }
    }

    // Evaluation

    /**
     * Capture stack trace and attach to error object.
     */
    private void captureStackTrace(JSObject error) {
        StringBuilder stackTrace = new StringBuilder();

        for (StackFrame frame : callStack) {
            stackTrace.append("    at ")
                    .append(frame.functionName)
                    .append(" (")
                    .append(frame.filename)
                    .append(":")
                    .append(frame.lineNumber)
                    .append(")\n");
        }

        error.set("stack", new JSString(stackTrace.toString()));
    }

    /**
     * Clear the pending exception in both context and VM.
     * This is needed when an async function catches an exception.
     */
    public void clearAllPendingExceptions() {
        clearPendingException();
        virtualMachine.clearPendingException();
    }

    // Module system

    /**
     * Clear the module cache.
     */
    public void clearModuleCache() {
        moduleCache.clear();
    }

    /**
     * Clear the pending exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
        this.errorStackTrace.clear();
    }

    /**
     * Close this context and release resources.
     * Called when the context is no longer needed.
     */
    @Override
    public void close() {
        // Clear all caches
        moduleCache.clear();
        callStack.clear();
        errorStackTrace.clear();

        // Clear exception state
        pendingException = null;

        // Remove from runtime
        runtime.destroyContext(this);
    }

    /**
     * Create a new JSArray with proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray() {
        return createJSArray(0);
    }

    /**
     * Create a new JSArray with specified length and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param length Initial length of the array
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray(long length) {
        return createJSArray(length, (int) length);
    }

    /**
     * Create a new JSArray with specified length, capacity, and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param length   Initial length of the array
     * @param capacity Initial capacity of the array
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray(long length, int capacity) {
        JSArray jsArray = new JSArray(length, capacity);
        JSValue arrayCtor = globalObject.get("Array");
        if (arrayCtor instanceof JSObject) {
            JSValue arrayProto = ((JSObject) arrayCtor).get("prototype");
            if (arrayProto instanceof JSObject) {
                jsArray.setPrototype((JSObject) arrayProto);
            }
        }
        return jsArray;
    }

    /**
     * Create a new JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength The length in bytes
     * @return A new JSArrayBuffer instance with prototype set
     */
    public JSArrayBuffer createJSArrayBuffer(int byteLength) {
        JSArrayBuffer jsArrayBuffer = new JSArrayBuffer(byteLength);
        JSValue arrayBufferCtor = globalObject.get("ArrayBuffer");
        if (arrayBufferCtor instanceof JSObject) {
            JSValue arrayBufferProto = ((JSObject) arrayBufferCtor).get("prototype");
            if (arrayBufferProto instanceof JSObject) {
                jsArrayBuffer.setPrototype((JSObject) arrayBufferProto);
            }
        }
        return jsArrayBuffer;
    }

    /**
     * Create a new JSDataView with proper prototype chain.
     *
     * @param buffer     The ArrayBuffer to view
     * @param byteOffset The offset in bytes
     * @param byteLength The length in bytes
     * @return A new JSDataView instance with prototype set
     */
    public JSDataView createJSDataView(JSArrayBuffer buffer, int byteOffset, int byteLength) {
        JSDataView jsDataView = new JSDataView(buffer, byteOffset, byteLength);
        JSValue dataViewCtor = globalObject.get("DataView");
        if (dataViewCtor instanceof JSObject) {
            JSValue dataViewProto = ((JSObject) dataViewCtor).get("prototype");
            if (dataViewProto instanceof JSObject) {
                jsDataView.setPrototype((JSObject) dataViewProto);
            }
        }
        return jsDataView;
    }

    /**
     * Create a new JSDate with proper prototype chain.
     *
     * @param timeValue The time value in milliseconds
     * @return A new JSDate instance with prototype set
     */
    public JSDate createJSDate(long timeValue) {
        JSDate jsDate = new JSDate(timeValue);
        JSValue dateCtor = globalObject.get("Date");
        if (dateCtor instanceof JSObject) {
            JSValue dateProto = ((JSObject) dateCtor).get("prototype");
            if (dateProto instanceof JSObject) {
                jsDate.setPrototype((JSObject) dateProto);
            }
        }
        return jsDate;
    }

    /**
     * Create a new JSFloat32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat32Array instance with prototype set
     */
    public JSFloat32Array createJSFloat32Array(int length) {
        JSFloat32Array jsFloat32Array = new JSFloat32Array(length);
        JSValue float32ArrayCtor = globalObject.get("Float32Array");
        if (float32ArrayCtor instanceof JSObject) {
            JSValue float32ArrayProto = ((JSObject) float32ArrayCtor).get("prototype");
            if (float32ArrayProto instanceof JSObject) {
                jsFloat32Array.setPrototype((JSObject) float32ArrayProto);
            }
        }
        return jsFloat32Array;
    }

    /**
     * Create a new JSFloat64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat64Array instance with prototype set
     */
    public JSFloat64Array createJSFloat64Array(int length) {
        JSFloat64Array jsFloat64Array = new JSFloat64Array(length);
        JSValue float64ArrayCtor = globalObject.get("Float64Array");
        if (float64ArrayCtor instanceof JSObject) {
            JSValue float64ArrayProto = ((JSObject) float64ArrayCtor).get("prototype");
            if (float64ArrayProto instanceof JSObject) {
                jsFloat64Array.setPrototype((JSObject) float64ArrayProto);
            }
        }
        return jsFloat64Array;
    }

    /**
     * Create a new JSInt16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt16Array instance with prototype set
     */
    public JSInt16Array createJSInt16Array(int length) {
        JSInt16Array jsInt16Array = new JSInt16Array(length);
        JSValue int16ArrayCtor = globalObject.get("Int16Array");
        if (int16ArrayCtor instanceof JSObject) {
            JSValue int16ArrayProto = ((JSObject) int16ArrayCtor).get("prototype");
            if (int16ArrayProto instanceof JSObject) {
                jsInt16Array.setPrototype((JSObject) int16ArrayProto);
            }
        }
        return jsInt16Array;
    }

    /**
     * Create a new JSInt32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt32Array instance with prototype set
     */
    public JSInt32Array createJSInt32Array(int length) {
        JSInt32Array jsInt32Array = new JSInt32Array(length);
        JSValue int32ArrayCtor = globalObject.get("Int32Array");
        if (int32ArrayCtor instanceof JSObject) {
            JSValue int32ArrayProto = ((JSObject) int32ArrayCtor).get("prototype");
            if (int32ArrayProto instanceof JSObject) {
                jsInt32Array.setPrototype((JSObject) int32ArrayProto);
            }
        }
        return jsInt32Array;
    }

    /**
     * Create a new JSInt8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt8Array instance with prototype set
     */
    public JSInt8Array createJSInt8Array(int length) {
        JSInt8Array jsInt8Array = new JSInt8Array(length);
        JSValue int8ArrayCtor = globalObject.get("Int8Array");
        if (int8ArrayCtor instanceof JSObject) {
            JSValue int8ArrayProto = ((JSObject) int8ArrayCtor).get("prototype");
            if (int8ArrayProto instanceof JSObject) {
                jsInt8Array.setPrototype((JSObject) int8ArrayProto);
            }
        }
        return jsInt8Array;
    }

    /**
     * Create a new JSMap with proper prototype chain.
     *
     * @return A new JSMap instance with prototype set
     */
    public JSMap createJSMap() {
        JSMap jsMap = new JSMap();
        JSValue mapCtor = globalObject.get("Map");
        if (mapCtor instanceof JSObject) {
            JSValue mapProto = ((JSObject) mapCtor).get("prototype");
            if (mapProto instanceof JSObject) {
                jsMap.setPrototype((JSObject) mapProto);
            }
        }
        return jsMap;
    }

    /**
     * Create a new JSObject with proper prototype chain.
     * Sets the object's prototype to Object.prototype from the global object.
     *
     * @return A new JSObject instance with prototype set
     */
    public JSObject createJSObject() {
        JSObject jsObject = new JSObject();
        JSValue objectCtor = globalObject.get("Object");
        if (objectCtor instanceof JSObject) {
            JSValue objectProto = ((JSObject) objectCtor).get("prototype");
            if (objectProto instanceof JSObject) {
                jsObject.setPrototype((JSObject) objectProto);
            }
        }
        return jsObject;
    }

    /**
     * Create a new JSPromise with proper prototype chain.
     *
     * @return A new JSPromise instance with prototype set
     */
    public JSPromise createJSPromise() {
        JSPromise jsPromise = new JSPromise();
        JSValue promiseCtor = globalObject.get("Promise");
        if (promiseCtor instanceof JSObject) {
            JSValue promiseProto = ((JSObject) promiseCtor).get("prototype");
            if (promiseProto instanceof JSObject) {
                jsPromise.setPrototype((JSObject) promiseProto);
            }
        }
        return jsPromise;
    }

    /**
     * Create a new JSRegExp with proper prototype chain.
     *
     * @param pattern The regular expression pattern
     * @param flags   The regular expression flags
     * @return A new JSRegExp instance with prototype set
     */
    public JSRegExp createJSRegExp(String pattern, String flags) {
        JSRegExp jsRegExp = new JSRegExp(pattern, flags);
        JSValue regExpCtor = globalObject.get("RegExp");
        if (regExpCtor instanceof JSObject) {
            JSValue regExpProto = ((JSObject) regExpCtor).get("prototype");
            if (regExpProto instanceof JSObject) {
                jsRegExp.setPrototype((JSObject) regExpProto);
            }
        }
        return jsRegExp;
    }

    /**
     * Create a new JSSet with proper prototype chain.
     *
     * @return A new JSSet instance with prototype set
     */
    public JSSet createJSSet() {
        JSSet jsSet = new JSSet();
        JSValue setCtor = globalObject.get("Set");
        if (setCtor instanceof JSObject) {
            JSValue setProto = ((JSObject) setCtor).get("prototype");
            if (setProto instanceof JSObject) {
                jsSet.setPrototype((JSObject) setProto);
            }
        }
        return jsSet;
    }

    /**
     * Create a new JSUint16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint16Array instance with prototype set
     */
    public JSUint16Array createJSUint16Array(int length) {
        JSUint16Array jsUint16Array = new JSUint16Array(length);
        JSValue uint16ArrayCtor = globalObject.get("Uint16Array");
        if (uint16ArrayCtor instanceof JSObject) {
            JSValue uint16ArrayProto = ((JSObject) uint16ArrayCtor).get("prototype");
            if (uint16ArrayProto instanceof JSObject) {
                jsUint16Array.setPrototype((JSObject) uint16ArrayProto);
            }
        }
        return jsUint16Array;
    }

    /**
     * Create a new JSUint32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint32Array instance with prototype set
     */
    public JSUint32Array createJSUint32Array(int length) {
        JSUint32Array jsUint32Array = new JSUint32Array(length);
        JSValue uint32ArrayCtor = globalObject.get("Uint32Array");
        if (uint32ArrayCtor instanceof JSObject) {
            JSValue uint32ArrayProto = ((JSObject) uint32ArrayCtor).get("prototype");
            if (uint32ArrayProto instanceof JSObject) {
                jsUint32Array.setPrototype((JSObject) uint32ArrayProto);
            }
        }
        return jsUint32Array;
    }

    /**
     * Create a new JSUint8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8Array instance with prototype set
     */
    public JSUint8Array createJSUint8Array(int length) {
        JSUint8Array jsUint8Array = new JSUint8Array(length);
        JSValue uint8ArrayCtor = globalObject.get("Uint8Array");
        if (uint8ArrayCtor instanceof JSObject) {
            JSValue uint8ArrayProto = ((JSObject) uint8ArrayCtor).get("prototype");
            if (uint8ArrayProto instanceof JSObject) {
                jsUint8Array.setPrototype((JSObject) uint8ArrayProto);
            }
        }
        return jsUint8Array;
    }

    /**
     * Create a new JSUint8ClampedArray with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8ClampedArray instance with prototype set
     */
    public JSUint8ClampedArray createJSUint8ClampedArray(int length) {
        JSUint8ClampedArray jsUint8ClampedArray = new JSUint8ClampedArray(length);
        JSValue uint8ClampedArrayCtor = globalObject.get("Uint8ClampedArray");
        if (uint8ClampedArrayCtor instanceof JSObject) {
            JSValue uint8ClampedArrayProto = ((JSObject) uint8ClampedArrayCtor).get("prototype");
            if (uint8ClampedArrayProto instanceof JSObject) {
                jsUint8ClampedArray.setPrototype((JSObject) uint8ClampedArrayProto);
            }
        }
        return jsUint8ClampedArray;
    }

    /**
     * Create a new JSBigInt64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigInt64Array instance with prototype set
     */
    public JSBigInt64Array createJSBigInt64Array(int length) {
        JSBigInt64Array jsBigInt64Array = new JSBigInt64Array(length);
        JSValue bigInt64ArrayCtor = globalObject.get("BigInt64Array");
        if (bigInt64ArrayCtor instanceof JSObject) {
            JSValue bigInt64ArrayProto = ((JSObject) bigInt64ArrayCtor).get("prototype");
            if (bigInt64ArrayProto instanceof JSObject) {
                jsBigInt64Array.setPrototype((JSObject) bigInt64ArrayProto);
            }
        }
        return jsBigInt64Array;
    }

    /**
     * Create a new JSBigUint64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigUint64Array instance with prototype set
     */
    public JSBigUint64Array createJSBigUint64Array(int length) {
        JSBigUint64Array jsBigUint64Array = new JSBigUint64Array(length);
        JSValue bigUint64ArrayCtor = globalObject.get("BigUint64Array");
        if (bigUint64ArrayCtor instanceof JSObject) {
            JSValue bigUint64ArrayProto = ((JSObject) bigUint64ArrayCtor).get("prototype");
            if (bigUint64ArrayProto instanceof JSObject) {
                jsBigUint64Array.setPrototype((JSObject) bigUint64ArrayProto);
            }
        }
        return jsBigUint64Array;
    }

    /**
     * Create a new JSWeakMap with proper prototype chain.
     *
     * @return A new JSWeakMap instance with prototype set
     */
    public JSWeakMap createJSWeakMap() {
        JSWeakMap jsWeakMap = new JSWeakMap();
        JSValue weakMapCtor = globalObject.get("WeakMap");
        if (weakMapCtor instanceof JSObject) {
            JSValue weakMapProto = ((JSObject) weakMapCtor).get("prototype");
            if (weakMapProto instanceof JSObject) {
                jsWeakMap.setPrototype((JSObject) weakMapProto);
            }
        }
        return jsWeakMap;
    }

    /**
     * Create a new JSWeakSet with proper prototype chain.
     *
     * @return A new JSWeakSet instance with prototype set
     */
    public JSWeakSet createJSWeakSet() {
        JSWeakSet jsWeakSet = new JSWeakSet();
        JSValue weakSetCtor = globalObject.get("WeakSet");
        if (weakSetCtor instanceof JSObject) {
            JSValue weakSetProto = ((JSObject) weakSetCtor).get("prototype");
            if (weakSetProto instanceof JSObject) {
                jsWeakSet.setPrototype((JSObject) weakSetProto);
            }
        }
        return jsWeakSet;
    }

    /**
     * Enqueue a microtask to be executed.
     *
     * @param microtask The microtask to enqueue
     */
    public void enqueueMicrotask(JSMicrotaskQueue.Microtask microtask) {
        microtaskQueue.enqueue(microtask);
    }

    /**
     * Execute code in a try-catch context.
     */
    public void enterCatchHandler() {
        this.inCatchHandler = true;
    }

    // Exception handling

    /**
     * Enter strict mode.
     */
    public void enterStrictMode() {
        this.strictMode = true;
    }

    /**
     * Evaluate JavaScript code in this context.
     * <p>
     * In full implementation, this would:
     * 1. Parse the source code
     * 2. Compile to bytecode
     * 3. Execute the bytecode
     * 4. Return the completion value
     *
     * @param code JavaScript source code
     * @return The completion value, or exception if eval throws
     */
    public JSValue eval(String code) {
        return eval(code, "<eval>", false);
    }

    /**
     * Evaluate code with source location information.
     *
     * @param code     JavaScript source code
     * @param filename Source filename for stack traces
     * @param isModule Whether to evaluate as module (vs script)
     * @return The completion value
     */
    public JSValue eval(String code, String filename, boolean isModule) {
        if (code == null || code.isEmpty()) {
            return JSUndefined.INSTANCE;
        }

        // Check for recursion limit
        if (!pushStackFrame(new StackFrame("<eval>", filename, 1))) {
            return throwError("RangeError", "Maximum call stack size exceeded");
        }

        try {
            // Phase 1-3: Lexer → Parser → Compiler (compile to bytecode)
            JSBytecodeFunction func = com.caoccao.qjs4j.compiler.Compiler.compile(code, filename);

            // Initialize the function's prototype chain so it inherits from Function.prototype
            func.initializePrototypeChain(this);

            // Phase 4: Execute bytecode in the virtual machine
            JSValue result = virtualMachine.execute(func, globalObject, new JSValue[0]);

            // Check if there's a pending exception
            if (hasPendingException()) {
                JSValue exception = getPendingException();
                clearPendingException();
                throw new JSException(exception);
            }

            // Process all pending microtasks before returning
            processMicrotasks();

            return result != null ? result : JSUndefined.INSTANCE;
        } catch (JSException e) {
            // JavaScript exception thrown during execution
            throw e;
        } catch (Compiler.CompilerException e) {
            JSValue error = throwError("SyntaxError", e.getMessage());
            throw new JSException(error);
        } catch (JSVirtualMachineException e) {
            // VM exception - check if it has a JSError, otherwise check pending exception
            if (e.getJsError() != null) {
                throw new JSException(e.getJsError());
            }
            if (hasPendingException()) {
                JSValue exception = getPendingException();
                clearPendingException();
                throw new JSException(exception);
            }
            JSValue error = throwError("Error", "VM error: " + e.getMessage());
            throw new JSException(error);
        } catch (Exception e) {
            JSValue error = throwError("Error", "Execution error: " + e.getMessage());
            throw new JSException(error);
        } finally {
            popStackFrame();
        }
    }

    /**
     * Exit try-catch context.
     */
    public void exitCatchHandler() {
        this.inCatchHandler = false;
    }

    /**
     * Exit strict mode.
     */
    public void exitStrictMode() {
        this.strictMode = false;
    }

    /**
     * Get the full call stack.
     */
    public List<StackFrame> getCallStack() {
        return new ArrayList<>(callStack);
    }

    /**
     * Get the current stack frame.
     */
    public StackFrame getCurrentStackFrame() {
        return callStack.peek();
    }

    /**
     * Get the current 'this' binding.
     */
    public JSValue getCurrentThis() {
        return currentThis;
    }

    // Stack management

    /**
     * Get the error stack trace.
     */
    public List<StackTraceElement> getErrorStackTrace() {
        return new ArrayList<>(errorStackTrace);
    }

    public JSObject getGlobalObject() {
        return globalObject;
    }

    /**
     * Get the microtask queue for this context.
     */
    public JSMicrotaskQueue getMicrotaskQueue() {
        return microtaskQueue;
    }

    /**
     * Get a cached module.
     */
    public JSModule getModule(String specifier) {
        return moduleCache.get(specifier);
    }

    /**
     * Get the pending exception.
     */
    public JSValue getPendingException() {
        return pendingException;
    }

    public JSRuntime getRuntime() {
        return runtime;
    }

    /**
     * Get the current call stack depth.
     */
    public int getStackDepth() {
        return stackDepth;
    }

    // Stack trace capture

    /**
     * Get the virtual machine for this context.
     */
    public com.caoccao.qjs4j.vm.VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    /**
     * Check if there's a pending exception.
     */
    public boolean hasPendingException() {
        return pendingException != null;
    }

    /**
     * Initialize the global object with built-in properties.
     * Delegates to GlobalObject to set up all global functions and properties.
     */
    private void initializeGlobalObject() {
        GlobalObject.initialize(this, globalObject);

        // In full implementation, this would also add:
        // - Built-in constructors (Object, Array, Function, String, Number, Boolean, etc.)
        // - Error constructors (Error, TypeError, ReferenceError, etc.)
        // - Advanced built-ins (Promise, Symbol, Map, Set, etc.)
    }

    // Execution state

    /**
     * Check if in strict mode.
     */
    public boolean isStrictMode() {
        return strictMode;
    }

    /**
     * Load and cache a JavaScript module.
     *
     * @param specifier Module specifier (file path or URL)
     * @return The loaded module
     * @throws JSModule.ModuleLinkingException    if module cannot be loaded or linked
     * @throws JSModule.ModuleEvaluationException if module evaluation fails
     */
    public JSModule loadModule(String specifier) throws JSModule.ModuleLinkingException, JSModule.ModuleEvaluationException {
        // Check cache first
        JSModule cached = moduleCache.get(specifier);
        return cached;

        // In full implementation:
        // 1. Resolve the module specifier to absolute path
        // 2. Load the module source code
        // 3. Parse and compile as module
        // 4. Link imported/exported bindings
        // 5. Execute module code (if not already executed)
        // 6. Cache and return the module

        // For now, return null to indicate module not found
        // A full implementation would load from filesystem or URL
    }

    /**
     * Pop the current stack frame.
     */
    public StackFrame popStackFrame() {
        if (callStack.isEmpty()) {
            return null;
        }
        stackDepth--;
        return callStack.pop();
    }

    /**
     * Process all pending microtasks.
     * This should be called at the end of each task in the event loop.
     */
    public void processMicrotasks() {
        microtaskQueue.processMicrotasks();
    }

    /**
     * Push a new stack frame.
     * Returns false if stack limit exceeded.
     */
    public boolean pushStackFrame(StackFrame frame) {
        if (stackDepth >= maxStackDepth) {
            return false;
        }
        callStack.push(frame);
        stackDepth++;
        return true;
    }

    // Accessors

    /**
     * Register a module in the cache.
     */
    public void registerModule(String specifier, JSModule module) {
        moduleCache.put(specifier, module);
    }

    /**
     * Set the current 'this' binding.
     */
    public void setCurrentThis(JSValue thisValue) {
        this.currentThis = thisValue != null ? thisValue : globalObject;
    }

    // Microtask queue management

    /**
     * Set the maximum stack depth.
     */
    public void setMaxStackDepth(int depth) {
        this.maxStackDepth = depth;
    }

    /**
     * Set the pending exception.
     */
    public void setPendingException(JSValue exception) {
        if (!inCatchHandler) {
            this.pendingException = exception;
            captureErrorStackTrace();
        }
    }

    /**
     * Throw a AggregateError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwAggregateError(String message) {
        return throwError(JSAggregateError.NAME, message);
    }

    /**
     * Throw a JavaScript error.
     * Creates an Error object and sets it as the pending exception.
     *
     * @param message Error message
     * @return The error value (for convenience in return statements)
     */
    public JSError throwError(String message) {
        return throwError(JSError.NAME, message);
    }

    /**
     * Throw a JavaScript error of a specific type.
     *
     * @param errorType Error constructor name (Error, TypeError, RangeError, etc.)
     * @param message   Error message
     * @return The error value
     */
    public JSError throwError(String errorType, String message) {
        // Create error object using the proper error class
        JSError jsError = switch (errorType) {
            case JSAggregateError.NAME -> new JSAggregateError(this, message);
            case JSEvalError.NAME -> new JSEvalError(this, message);
            case JSRangeError.NAME -> new JSRangeError(this, message);
            case JSReferenceError.NAME -> new JSReferenceError(this, message);
            case JSSyntaxError.NAME -> new JSSyntaxError(this, message);
            case JSTypeError.NAME -> new JSTypeError(this, message);
            case JSURIError.NAME -> new JSURIError(this, message);
            default -> new JSError(this, message);
        };
        return throwError(jsError);
    }

    public JSError throwError(JSError jsError) {
        // Set prototype from the global error constructor
        JSValue errorCtor = globalObject.get(jsError.getName().value());
        if (errorCtor instanceof JSObject ctorObj) {
            JSValue prototypeValue = ctorObj.get("prototype");
            if (prototypeValue instanceof JSObject prototype) {
                jsError.setPrototype(prototype);
            }
        }
        // Capture stack trace
        captureStackTrace(jsError);
        // Set as pending exception
        setPendingException(jsError);
        return jsError;
    }

    /**
     * Throw a EvalError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwEvalError(String message) {
        return throwError(JSEvalError.NAME, message);
    }

    /**
     * Throw a RangeError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwRangeError(String message) {
        return throwError(JSRangeError.NAME, message);
    }

    /**
     * Throw a ReferenceError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwReferenceError(String message) {
        return throwError(JSReferenceError.NAME, message);
    }

    /**
     * Throw a SyntaxError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwSyntaxError(String message) {
        return throwError(JSSyntaxError.NAME, message);
    }

    /**
     * Throw a TypeError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwTypeError(String message) {
        return throwError(JSTypeError.NAME, message);
    }

    /**
     * Throw a URIError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwURIError(String message) {
        return throwError(JSURIError.NAME, message);
    }

    /**
     * Represents a stack frame in the call stack.
     */
    public record StackFrame(String functionName, String filename, int lineNumber, int columnNumber) {
        public StackFrame(String functionName, String filename, int lineNumber) {
            this(functionName, filename, lineNumber, 0);
        }

        public StackFrame(String functionName, String filename, int lineNumber, int columnNumber) {
            this.functionName = functionName != null ? functionName : "<anonymous>";
            this.filename = filename != null ? filename : "<unknown>";
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }

        @Override
        public String toString() {
            return functionName + " (" + filename + ":" + lineNumber + ":" + columnNumber + ")";
        }
    }
}
