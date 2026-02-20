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

import com.caoccao.qjs4j.compilation.ast.AstUtils;
import com.caoccao.qjs4j.compilation.ast.FunctionDeclaration;
import com.caoccao.qjs4j.compilation.ast.Statement;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.*;
import com.caoccao.qjs4j.vm.VirtualMachine;

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
    // Global declaration tracking for cross-script collision detection
    // Following QuickJS global_var_obj pattern (GlobalDeclarationInstantiation)
    private final Set<String> globalLexDeclarations;
    private final JSObject globalObject;
    private final Set<String> globalVarDeclarations;
    private final JSGlobalObject jsGlobalObject;
    // Microtask queue for promise resolution and async operations
    private final JSMicrotaskQueue microtaskQueue;
    private final Map<String, JSModule> moduleCache;
    private final JSRuntime runtime;
    private final VirtualMachine virtualMachine;
    // Internal constructor references (not exposed in global scope)
    private JSObject asyncFunctionConstructor;
    // Async generator prototype chain (not exposed in global scope)
    private JSObject asyncGeneratorFunctionPrototype;
    private JSValue currentThis;
    // Generator prototype chain (not exposed in global scope)
    private JSObject generatorFunctionPrototype;
    private boolean inCatchHandler;
    private int maxStackDepth;
    // Exception state
    private JSValue pendingException;
    // Promise rejection callback
    private JSPromiseRejectCallback promiseRejectCallback;
    private int stackDepth;
    // Execution state
    private boolean strictMode;

    /**
     * Create a new execution context.
     */
    public JSContext(JSRuntime runtime) {
        this.callStack = new ArrayDeque<>();
        this.errorStackTrace = new ArrayList<>();
        this.globalObject = new JSObject();
        this.globalLexDeclarations = new HashSet<>();
        this.globalVarDeclarations = new HashSet<>();
        this.inCatchHandler = false;
        this.jsGlobalObject = new JSGlobalObject();
        this.maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
        this.microtaskQueue = new JSMicrotaskQueue(this);
        this.moduleCache = new HashMap<>();
        this.pendingException = null;
        this.runtime = runtime;
        this.stackDepth = 0;
        this.strictMode = false;
        this.virtualMachine = new VirtualMachine(this);

        this.currentThis = globalObject;
        initializeGlobalObject();
    }

    /**
     * Capture stack trace when exception is thrown.
     */
    private void captureErrorStackTrace() {
        clearErrorStackTrace();
        for (StackFrame frame : callStack) {
            errorStackTrace.add(new StackTraceElement(
                    "JavaScript",
                    frame.functionName,
                    frame.filename,
                    frame.lineNumber
            ));
        }
    }

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

        error.set(PropertyKey.STACK, new JSString(stackTrace.toString()));
    }

    /**
     * Clear the pending exception in both context and VM.
     * This is needed when an async function catches an exception.
     */
    public void clearAllPendingExceptions() {
        clearPendingException();
        clearErrorStackTrace();
        virtualMachine.clearPendingException();
    }

    private void clearCallStack() {
        callStack.clear();
    }

    private void clearErrorStackTrace() {
        this.errorStackTrace.clear();
    }

    /**
     * Clear the module cache.
     */
    private void clearModuleCache() {
        moduleCache.clear();
    }

    /**
     * Clear the pending exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
    }

    /**
     * Close this context and release resources.
     * Called when the context is no longer needed.
     */
    @Override
    public void close() {
        // Clear all caches
        clearModuleCache();
        clearCallStack();
        clearPendingException();
        clearErrorStackTrace();
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
        transferPrototype(jsArray, JSArray.NAME);
        return jsArray;
    }

    /**
     * Create a new JSArray with specified values and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param values Initial values of the array
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray(JSValue... values) {
        JSArray jsArray = new JSArray(values);
        transferPrototype(jsArray, JSArray.NAME);
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
        transferPrototype(jsArrayBuffer, JSArrayBuffer.NAME);
        return jsArrayBuffer;
    }

    /**
     * Create a new JSBigInt64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigInt64Array instance with prototype set
     */
    public JSBigInt64Array createJSBigInt64Array(int length) {
        return initializeTypedArray(new JSBigInt64Array(length), JSBigInt64Array.NAME);
    }

    public JSBigInt64Array createJSBigInt64Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSBigInt64Array(buffer, byteOffset, length), JSBigInt64Array.NAME);
    }

    public JSBigIntObject createJSBigIntObject(JSBigInt value) {
        JSBigIntObject wrapper = new JSBigIntObject(value);
        transferPrototype(wrapper, JSBigIntObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSBigUint64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigUint64Array instance with prototype set
     */
    public JSBigUint64Array createJSBigUint64Array(int length) {
        return initializeTypedArray(new JSBigUint64Array(length), JSBigUint64Array.NAME);
    }

    public JSBigUint64Array createJSBigUint64Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSBigUint64Array(buffer, byteOffset, length), JSBigUint64Array.NAME);
    }

    public JSBooleanObject createJSBooleanObject(JSBoolean value) {
        JSBooleanObject wrapper = new JSBooleanObject(value);
        transferPrototype(wrapper, JSBooleanObject.NAME);
        return wrapper;
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
        transferPrototype(jsDataView, JSDataView.NAME);
        return jsDataView;
    }

    /**
     * Create a new JSDate with proper prototype chain.
     *
     * @param timeValue The time value in milliseconds
     * @return A new JSDate instance with prototype set
     */
    public JSDate createJSDate(double timeValue) {
        JSDate jsDate = new JSDate(timeValue);
        transferPrototype(jsDate, JSDate.NAME);
        return jsDate;
    }

    public JSDisposableStack createJSDisposableStack() {
        JSDisposableStack stack = new JSDisposableStack();
        transferPrototype(stack, JSDisposableStack.NAME);
        return stack;
    }

    public JSError createJSError(String message) {
        JSError jsError = new JSError(this, message);
        transferPrototype(jsError, JSError.NAME);
        return jsError;
    }

    /**
     * Create a new JSFloat16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat16Array instance with prototype set
     */
    public JSFloat16Array createJSFloat16Array(int length) {
        return initializeTypedArray(new JSFloat16Array(length), JSFloat16Array.NAME);
    }

    public JSFloat16Array createJSFloat16Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat16Array(buffer, byteOffset, length), JSFloat16Array.NAME);
    }

    /**
     * Create a new JSFloat32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat32Array instance with prototype set
     */
    public JSFloat32Array createJSFloat32Array(int length) {
        return initializeTypedArray(new JSFloat32Array(length), JSFloat32Array.NAME);
    }

    public JSFloat32Array createJSFloat32Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat32Array(buffer, byteOffset, length), JSFloat32Array.NAME);
    }

    /**
     * Create a new JSFloat64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat64Array instance with prototype set
     */
    public JSFloat64Array createJSFloat64Array(int length) {
        return initializeTypedArray(new JSFloat64Array(length), JSFloat64Array.NAME);
    }

    public JSFloat64Array createJSFloat64Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat64Array(buffer, byteOffset, length), JSFloat64Array.NAME);
    }

    /**
     * Create a new JSInt16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt16Array instance with prototype set
     */
    public JSInt16Array createJSInt16Array(int length) {
        return initializeTypedArray(new JSInt16Array(length), JSInt16Array.NAME);
    }

    public JSInt16Array createJSInt16Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt16Array(buffer, byteOffset, length), JSInt16Array.NAME);
    }

    /**
     * Create a new JSInt32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt32Array instance with prototype set
     */
    public JSInt32Array createJSInt32Array(int length) {
        return initializeTypedArray(new JSInt32Array(length), JSInt32Array.NAME);
    }

    public JSInt32Array createJSInt32Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt32Array(buffer, byteOffset, length), JSInt32Array.NAME);
    }

    /**
     * Create a new JSInt8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt8Array instance with prototype set
     */
    public JSInt8Array createJSInt8Array(int length) {
        return initializeTypedArray(new JSInt8Array(length), JSInt8Array.NAME);
    }

    public JSInt8Array createJSInt8Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt8Array(buffer, byteOffset, length), JSInt8Array.NAME);
    }

    /**
     * Create a new JSMap with proper prototype chain.
     *
     * @return A new JSMap instance with prototype set
     */
    public JSMap createJSMap() {
        JSMap jsMap = new JSMap();
        transferPrototype(jsMap, JSMap.NAME);
        return jsMap;
    }

    public JSNumberObject createJSNumberObject(JSNumber value) {
        JSNumberObject wrapper = new JSNumberObject(value);
        transferPrototype(wrapper, JSNumberObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSObject with proper prototype chain.
     * Sets the object's prototype to Object.prototype from the global object.
     *
     * @return A new JSObject instance with prototype set
     */
    public JSObject createJSObject() {
        JSObject jsObject = new JSObject();
        transferPrototype(jsObject, JSObject.NAME);
        return jsObject;
    }

    /**
     * Create a new JSPromise with proper prototype chain.
     *
     * @return A new JSPromise instance with prototype set
     */
    public JSPromise createJSPromise() {
        JSPromise jsPromise = new JSPromise();
        transferPrototype(jsPromise, JSPromise.NAME);
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
        transferPrototype(jsRegExp, JSRegExp.NAME);
        return jsRegExp;
    }

    /**
     * Create a new JSSet with proper prototype chain.
     *
     * @return A new JSSet instance with prototype set
     */
    public JSSet createJSSet() {
        JSSet jsSet = new JSSet();
        transferPrototype(jsSet, JSSet.NAME);
        return jsSet;
    }

    public JSStringObject createJSStringObject(JSString value) {
        JSStringObject wrapper = new JSStringObject(value);
        transferPrototype(wrapper, JSStringObject.NAME);
        return wrapper;
    }

    public JSSuppressedError createJSSuppressedError(JSValue error, JSValue suppressed, String message) {
        JSSuppressedError jsError = new JSSuppressedError(this, error, suppressed, message);
        transferPrototype(jsError, JSSuppressedError.NAME);
        return jsError;
    }

    public JSSymbolObject createJSSymbolObject(JSSymbol value) {
        JSSymbolObject wrapper = new JSSymbolObject(value);
        transferPrototype(wrapper, JSSymbolObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSUint16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint16Array instance with prototype set
     */
    public JSUint16Array createJSUint16Array(int length) {
        return initializeTypedArray(new JSUint16Array(length), JSUint16Array.NAME);
    }

    public JSUint16Array createJSUint16Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint16Array(buffer, byteOffset, length), JSUint16Array.NAME);
    }

    /**
     * Create a new JSUint32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint32Array instance with prototype set
     */
    public JSUint32Array createJSUint32Array(int length) {
        return initializeTypedArray(new JSUint32Array(length), JSUint32Array.NAME);
    }

    public JSUint32Array createJSUint32Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint32Array(buffer, byteOffset, length), JSUint32Array.NAME);
    }

    /**
     * Create a new JSUint8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8Array instance with prototype set
     */
    public JSUint8Array createJSUint8Array(int length) {
        return initializeTypedArray(new JSUint8Array(length), JSUint8Array.NAME);
    }

    public JSUint8Array createJSUint8Array(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint8Array(buffer, byteOffset, length), JSUint8Array.NAME);
    }

    /**
     * Create a new JSUint8ClampedArray with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8ClampedArray instance with prototype set
     */
    public JSUint8ClampedArray createJSUint8ClampedArray(int length) {
        return initializeTypedArray(new JSUint8ClampedArray(length), JSUint8ClampedArray.NAME);
    }

    public JSUint8ClampedArray createJSUint8ClampedArray(JSArrayBufferable buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint8ClampedArray(buffer, byteOffset, length), JSUint8ClampedArray.NAME);
    }

    /**
     * Create a new JSWeakMap with proper prototype chain.
     *
     * @return A new JSWeakMap instance with prototype set
     */
    public JSWeakMap createJSWeakMap() {
        JSWeakMap jsWeakMap = new JSWeakMap();
        transferPrototype(jsWeakMap, JSWeakMap.NAME);
        return jsWeakMap;
    }

    /**
     * Create a new JSWeakSet with proper prototype chain.
     *
     * @return A new JSWeakSet instance with prototype set
     */
    public JSWeakSet createJSWeakSet() {
        JSWeakSet jsWeakSet = new JSWeakSet();
        transferPrototype(jsWeakSet, JSWeakSet.NAME);
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
        return eval(code, "<eval>", false, false);
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
        return eval(code, filename, isModule, false);
    }

    /**
     * Eval js value.
     *
     * @param code         the code
     * @param filename     the filename
     * @param isModule     the is module
     * @param isDirectEval the is direct eval
     * @return the js value
     */
    public JSValue eval(String code, String filename, boolean isModule, boolean isDirectEval) {
        if (code == null || code.isEmpty()) {
            return JSUndefined.INSTANCE;
        }

        // Check for recursion limit
        if (!pushStackFrame(new StackFrame("<eval>", filename, 1))) {
            return throwError("RangeError", "Maximum call stack size exceeded");
        }

        Compiler compiler = new Compiler(code, filename);
        // Per QuickJS, eval code has is_eval=true which prevents top-level return
        if (isDirectEval) {
            compiler.setEval(true);
        }
        try {
            // Phase 1-3: Lexer → Parser → Compiler (compile to bytecode)
            JSBytecodeFunction func;
            Compiler.CompileResult compileResult = compiler.compile(isModule);
            func = compileResult.function();
            if (!isModule && !isDirectEval) {
                // Top-level script: check GlobalDeclarationInstantiation per ES2024 16.1.7
                func = compileResult.function();

                // Collect new declarations from this script
                Set<String> newVarDecls = new HashSet<>();
                Set<String> newLexDecls = new HashSet<>();
                AstUtils.collectGlobalDeclarations(compileResult.ast(), newVarDecls, newLexDecls);

                // Check: let/const names must not collide with existing lex declarations
                // or restricted global properties (non-configurable or script-level var)
                for (String name : newLexDecls) {
                    if (globalLexDeclarations.contains(name)) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                    // Check for non-configurable property on global object
                    PropertyKey key = PropertyKey.fromString(name);
                    PropertyDescriptor desc = globalObject.getOwnPropertyDescriptor(key);
                    if (desc != null && !desc.isConfigurable()) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                    // Check against script-level var declarations (these should be
                    // non-configurable per spec, tracked separately)
                    if (globalVarDeclarations.contains(name)) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                }

                // Check: var/function names must not collide with existing lex declarations
                for (String name : newVarDecls) {
                    if (globalLexDeclarations.contains(name)) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                }

                // Register new declarations for future collision checks
                globalLexDeclarations.addAll(newLexDecls);
                globalVarDeclarations.addAll(newVarDecls);

                // CreateGlobalVarDeclaration: define var bindings as non-configurable
                // properties on the global object (per ES2024 9.1.1.4.17 / QuickJS
                // js_closure_define_global_var with is_direct_or_indirect_eval=FALSE).
                // This must happen BEFORE execution so bindings exist at script start.
                for (String name : newVarDecls) {
                    PropertyKey key = PropertyKey.fromString(name);
                    PropertyDescriptor existing = globalObject.getOwnPropertyDescriptor(key);
                    if (existing == null) {
                        // Property doesn't exist: create {writable, enumerable, NOT configurable}
                        globalObject.defineProperty(key,
                                PropertyDescriptor.dataDescriptor(
                                        JSUndefined.INSTANCE,
                                        true,   // writable
                                        true,   // enumerable
                                        false   // configurable
                                ));
                    }
                }
            }

            // For eval code: EvalDeclarationInstantiation step 8 (ES2024 19.2.1.3)
            // Check CanDeclareGlobalFunction for all function declarations before executing.
            // If any function declaration targets a non-configurable global property that is
            // not both writable and enumerable, throw TypeError before any code runs.
            // Following QuickJS js_closure2 first-pass check with JS_CheckDefineGlobalVar.
            if (!isModule && isDirectEval) {
                List<Statement> evalBody = compileResult.ast().body();
                Set<String> checkedFuncNames = new HashSet<>();
                for (int i = evalBody.size() - 1; i >= 0; i--) {
                    if (evalBody.get(i) instanceof FunctionDeclaration funcDecl && funcDecl.id() != null) {
                        String funcName = funcDecl.id().name();
                        if (checkedFuncNames.add(funcName)) {
                            PropertyKey key = PropertyKey.fromString(funcName);
                            PropertyDescriptor desc = globalObject.getOwnPropertyDescriptor(key);
                            if (desc != null && !desc.isConfigurable()) {
                                if (desc.isAccessorDescriptor()
                                        || !(desc.isWritable() && desc.isEnumerable())) {
                                    throw new JSException("TypeError",
                                            "cannot define variable '" + funcName + "'");
                                }
                            }
                            if (desc == null && !globalObject.isExtensible()) {
                                throw new JSException("TypeError",
                                        "cannot define variable '" + funcName + "'");
                            }
                        }
                    }
                }
            }

            // Initialize the function's prototype chain so it inherits from Function.prototype
            func.initializePrototypeChain(this);

            // Phase 4: Execute bytecode in the virtual machine
            // For direct eval, inherit the caller's 'this' binding per ES2024 PerformEval.
            // In strict mode functions called without receiver, 'this' is undefined, and
            // eval('this') must see that same undefined value, not the global object.
            JSValue evalThisArg = globalObject;
            if (isDirectEval) {
                com.caoccao.qjs4j.vm.StackFrame callerFrame = virtualMachine.getCurrentFrame();
                if (callerFrame != null) {
                    evalThisArg = callerFrame.getThisArg();
                }
            }
            JSValue result = virtualMachine.execute(func, evalThisArg, new JSValue[0]);

            // Check if there's a pending exception
            if (hasPendingException()) {
                JSValue exception = getPendingException();
                throw new JSException(exception);
            }

            // Process all pending microtasks before returning
            processMicrotasks();

            return result != null ? result : JSUndefined.INSTANCE;
        } catch (JSException e) {
            throw e;
        } catch (JSCompilerException e) {
            JSValue error = throwError("SyntaxError", e.getMessage());
            throw new JSException(error);
        } catch (JSVirtualMachineException e) {
            // VM exception - check if it has a JSError, otherwise check pending exception
            if (e.getJsError() != null) {
                throw new JSException(e.getJsError());
            }
            if (hasPendingException()) {
                JSValue exception = getPendingException();
                throw new JSException(exception);
            }
            JSValue error = throwError("Error", "VM error: " + e.getMessage());
            throw new JSException(error);
        } catch (JSErrorException e) {
            JSValue error = throwError(e.getErrorType().name(), e.getMessage());
            throw new JSException(error);
        } catch (Exception e) {
            JSValue error = throwError("Error", "Execution error: " + e.getMessage());
            throw new JSException(error);
        } finally {
            popStackFrame();
            // Clear ALL possible dirty state to ensure clean slate for next eval()
            stackDepth = callStack.size();
            inCatchHandler = false;
            currentThis = globalObject;
            clearPendingException();
            clearErrorStackTrace();
        }
    }

    /**
     * Exit strict mode.
     */
    public void exitStrictMode() {
        this.strictMode = false;
    }

    /**
     * Get the AsyncFunction constructor (internal use only).
     * Used for setting up prototype chains for async functions.
     */
    public JSObject getAsyncFunctionConstructor() {
        return asyncFunctionConstructor;
    }

    public JSObject getAsyncGeneratorFunctionPrototype() {
        return asyncGeneratorFunctionPrototype;
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

    /**
     * Get the error stack trace.
     */
    public List<StackTraceElement> getErrorStackTrace() {
        return new ArrayList<>(errorStackTrace);
    }

    /**
     * Get the GeneratorFunction prototype (internal use only).
     * Used for setting up prototype chains for generator functions.
     */
    public JSObject getGeneratorFunctionPrototype() {
        return generatorFunctionPrototype;
    }

    public JSObject getGlobalObject() {
        return globalObject;
    }

    public JSGlobalObject getJSGlobalObject() {
        return jsGlobalObject;
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

    public JSPromiseRejectCallback getPromiseRejectCallback() {
        return promiseRejectCallback;
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

    /**
     * Get the virtual machine for this context.
     */
    public VirtualMachine getVirtualMachine() {
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
     * Delegates to JSGlobalObject to set up all global functions and properties.
     */
    private void initializeGlobalObject() {
        jsGlobalObject.initialize(this, globalObject);
    }

    private <T extends JSTypedArray> T initializeTypedArray(T typedArray, String constructorName) {
        transferPrototype(typedArray, constructorName);
        var buffer = typedArray.getBuffer();
        if (buffer instanceof JSObject jsObject && jsObject.getPrototype() == null) {
            transferPrototype(jsObject, buffer.isShared() ? JSSharedArrayBuffer.NAME : JSArrayBuffer.NAME);
        }
        return typedArray;
    }

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

    /**
     * Register a module in the cache.
     */
    public void registerModule(String specifier, JSModule module) {
        moduleCache.put(specifier, module);
    }

    /**
     * Set the AsyncFunction constructor (internal use only).
     * Called during global object initialization.
     */
    public void setAsyncFunctionConstructor(JSObject asyncFunctionConstructor) {
        this.asyncFunctionConstructor = asyncFunctionConstructor;
    }

    public void setAsyncGeneratorFunctionPrototype(JSObject asyncGeneratorFunctionPrototype) {
        this.asyncGeneratorFunctionPrototype = asyncGeneratorFunctionPrototype;
    }

    /**
     * Set the current 'this' binding.
     */
    public void setCurrentThis(JSValue thisValue) {
        this.currentThis = thisValue != null ? thisValue : globalObject;
    }

    /**
     * Set the GeneratorFunction prototype (internal use only).
     * Called during global object initialization.
     */
    public void setGeneratorFunctionPrototype(JSObject generatorFunctionPrototype) {
        this.generatorFunctionPrototype = generatorFunctionPrototype;
    }

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
     * Set the promise rejection callback.
     * This callback is invoked when a promise rejection occurs in an await expression.
     * If the callback returns true, the rejection is considered handled and the catch
     * clause will take effect instead of throwing an exception.
     */
    public void setPromiseRejectCallback(JSPromiseRejectCallback callback) {
        this.promiseRejectCallback = callback;
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
        transferPrototype(jsError, jsError.getName().value());
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

    public boolean transferPrototype(JSObject receiver, String constructorName) {
        JSValue constructor = globalObject.get(constructorName);
        if (constructor instanceof JSObject jsObject) {
            return transferPrototype(receiver, jsObject);
        }
        return false;
    }

    public boolean transferPrototype(JSObject receiver, JSObject constructor) {
        JSValue prototype = constructor.get(PropertyKey.PROTOTYPE);
        if (prototype instanceof JSObject) {
            receiver.setPrototype((JSObject) prototype);
            return true;
        }
        return false;
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
