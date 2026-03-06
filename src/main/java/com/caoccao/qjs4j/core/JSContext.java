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
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VirtualMachine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN =
            Pattern.compile("^class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN =
            Pattern.compile("^(?:async\\s+)?function(?:\\s*\\*)?\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern MODULE_NAMESPACE_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*\\*\\s*as\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+from\\s+(['\"])([^'\"\\r\\n]+)\\2\\s*;?\\s*$");
    private static final Pattern MODULE_SIDE_EFFECT_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(['\"])([^'\"\\r\\n]+)\\1\\s*;?\\s*$");
    private static final Pattern MODULE_STATIC_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+(?:[^;]*?\\s+from\\s+)?['\"]([^'\"\\r\\n]+)['\"]\\s*;?\\s*$");
    private static final Pattern MODULE_TOP_LEVEL_AWAIT_PATTERN =
            Pattern.compile("(?m)^\\s*await\\b");
    // Call stack management
    private final Deque<JSStackFrame> callStack;
    private final Map<String, DynamicImportModuleRecord> dynamicImportModuleCache;
    // Stack trace capture
    private final List<StackTraceElement> errorStackTrace;
    private final Deque<EvalOverlayFrame> evalOverlayFrames;
    // Global declaration tracking for cross-script collision detection
    // Following QuickJS global_var_obj pattern (GlobalDeclarationInstantiation)
    private final Set<String> globalConstDeclarations;
    private final Set<String> globalLexDeclarations;
    private final Set<String> globalVarDeclarations;
    // Shared iterator prototypes by toStringTag (e.g., "Array Iterator" → %ArrayIteratorPrototype%)
    private final Map<String, JSObject> iteratorPrototypes;
    private final JSGlobalObject jsGlobalObject;
    // Microtask queue for promise resolution and async operations
    private final JSMicrotaskQueue microtaskQueue;
    private final Map<String, JSModule> moduleCache;
    private final JSRuntime runtime;
    private final VirtualMachine virtualMachine;
    // Internal constructor references (not exposed in global scope)
    private JSObject asyncFunctionConstructor;
    private JSObject asyncGeneratorFunctionPrototype;
    // Async generator prototype chain (not exposed in global scope)
    private JSObject asyncGeneratorPrototype;
    // Cached Object.prototype for fast internal object creation
    private JSObject cachedObjectPrototype;
    private JSObject cachedPromisePrototype;
    // Temporarily holds new.target during native constructor calls
    // so native constructors can check if called directly vs from subclass
    private JSValue constructorNewTarget;
    private JSValue currentThis;
    // Generator prototype chain (not exposed in global scope)
    private JSObject generatorFunctionPrototype;
    private boolean inCatchHandler;
    private int maxStackDepth;
    private JSValue nativeConstructorNewTarget;
    private boolean pendingClassFieldEval;
    private int pendingDirectEvalCalls;
    // Exception state
    private JSValue pendingException;
    // Promise rejection callback
    private IJSPromiseRejectCallback promiseRejectCallback;
    private int stackDepth;
    // Execution state
    private boolean strictMode;
    // The %ThrowTypeError% intrinsic (shared across Function.prototype and strict arguments)
    private JSNativeFunction throwTypeErrorIntrinsic;
    private boolean waitable;

    /**
     * Create a new execution context.
     */
    public JSContext(JSRuntime runtime) {
        this.callStack = new ArrayDeque<>();
        this.errorStackTrace = new ArrayList<>();
        this.globalConstDeclarations = new HashSet<>();
        this.globalLexDeclarations = new HashSet<>();
        this.globalVarDeclarations = new HashSet<>();
        this.waitable = true;
        this.inCatchHandler = false;
        this.evalOverlayFrames = new ArrayDeque<>();
        this.iteratorPrototypes = new HashMap<>();
        this.jsGlobalObject = new JSGlobalObject(this);
        this.maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
        this.microtaskQueue = new JSMicrotaskQueue(this);
        this.dynamicImportModuleCache = new HashMap<>();
        this.moduleCache = new HashMap<>();
        this.pendingException = null;
        this.runtime = runtime;
        this.pendingDirectEvalCalls = 0;
        this.stackDepth = 0;
        this.strictMode = false;
        this.virtualMachine = new VirtualMachine(this);
        this.nativeConstructorNewTarget = null;

        this.currentThis = jsGlobalObject.getGlobalObject();
        initializeGlobalObject();
    }

    private void appendDynamicImportDefaultExportNameFixup(
            StringBuilder transformedSourceBuilder,
            String localName) {
        transformedSourceBuilder.append("if (typeof ")
                .append(localName)
                .append(" === \"function\" && (!Object.prototype.hasOwnProperty.call(")
                .append(localName)
                .append(", \"name\") || ")
                .append(localName)
                .append(".name === \"\" || ")
                .append(localName)
                .append(".name === \"")
                .append(localName)
                .append("\")) {\n")
                .append("  Object.defineProperty(")
                .append(localName)
                .append(", \"name\", { value: \"default\", configurable: true });\n")
                .append("}\n");
    }

    private void appendDynamicImportExportAssignments(
            StringBuilder transformedSourceBuilder,
            String exportBindingName,
            List<LocalExportBinding> localExportBindings) {
        transformedSourceBuilder.append("const __qjs4jModuleExports = globalThis[\"")
                .append(escapeJavaScriptString(exportBindingName))
                .append("\"];\n");
        for (LocalExportBinding localExportBinding : localExportBindings) {
            transformedSourceBuilder.append("Object.defineProperty(__qjs4jModuleExports, \"")
                    .append(escapeJavaScriptString(localExportBinding.exportedName()))
                    .append("\", { enumerable: true, configurable: false, get() { return ")
                    .append(localExportBinding.localName())
                    .append("; } });\n");
        }
    }

    /**
     * Capture stack trace when exception is thrown.
     */
    private void captureErrorStackTrace() {
        clearErrorStackTrace();
        for (JSStackFrame frame : callStack) {
            errorStackTrace.add(new StackTraceElement(
                    "JavaScript",
                    frame.functionName(),
                    frame.filename(),
                    frame.lineNumber()
            ));
        }
    }

    /**
     * Capture stack trace and attach to error object.
     */
    private void captureStackTrace(JSObject error) {
        StringBuilder stackTrace = new StringBuilder();

        for (JSStackFrame frame : callStack) {
            stackTrace.append("    at ")
                    .append(frame.functionName())
                    .append(" (")
                    .append(frame.filename())
                    .append(":")
                    .append(frame.lineNumber())
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
        dynamicImportModuleCache.clear();
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

    public boolean consumeScheduledClassFieldEvalCall() {
        boolean result = pendingClassFieldEval;
        pendingClassFieldEval = false;
        return result;
    }

    public boolean consumeScheduledDirectEvalCall() {
        if (pendingDirectEvalCalls > 0) {
            pendingDirectEvalCalls--;
            return true;
        }
        return false;
    }

    public JSObject createImportMetaObject(String filename) {
        JSObject importMetaObject = new JSObject();
        importMetaObject.setPrototype(null);
        if (filename != null && !filename.isEmpty() && !filename.startsWith("<")) {
            importMetaObject.set(PropertyKey.fromString("url"), new JSString(filename));
        }
        return importMetaObject;
    }

    public JSAggregateError createJSAggregateError(String message) {
        JSAggregateError jsError = new JSAggregateError(this, message);
        transferPrototype(jsError, JSAggregateError.NAME);
        return jsError;
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
     * Create a new JSArray taking ownership of a freshly allocated values array.
     * Internal fast path to avoid an extra defensive copy in hot built-in paths.
     */
    public JSArray createJSArray(JSValue[] values, boolean takeOwnership) {
        JSArray jsArray = new JSArray(values, takeOwnership);
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
     * Create a new resizable JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength    The initial length in bytes
     * @param maxByteLength The maximum length in bytes, or -1 for non-resizable
     * @return A new JSArrayBuffer instance with prototype set
     */
    public JSArrayBuffer createJSArrayBuffer(int byteLength, int maxByteLength) {
        JSArrayBuffer jsArrayBuffer = new JSArrayBuffer(byteLength, maxByteLength);
        transferPrototype(jsArrayBuffer, JSArrayBuffer.NAME);
        return jsArrayBuffer;
    }

    /**
     * ES2024 7.3.34 ArraySpeciesCreate(originalArray, length).
     * Following QuickJS JS_ArraySpeciesCreate.
     *
     * @return the new array-like object, or null if an exception was set on context
     */
    public JSValue createJSArraySpecies(JSObject originalArray, long length) {
        // Step 3: If IsArray(originalArray) is false, return ArrayCreate(length)
        int isArr = JSTypeChecking.isArray(this, originalArray);
        if (isArr < 0) {
            return null;
        }
        if (isArr == 0) {
            // ArrayCreate(length): throw RangeError if length > 2^32 - 1
            if (length > 0xFFFFFFFFL) {
                return throwRangeError("Invalid array length");
            }
            return createJSArray(length, 0);
        }

        // Step 4: Let C be ? Get(originalArray, "constructor")
        JSValue ctor = originalArray.get(this, PropertyKey.CONSTRUCTOR);
        if (hasPendingException()) {
            return null;
        }

        // Step 5: If IsConstructor(C) is true, cross-realm check.
        // ES2024 10.4.2.3 ArraySpeciesCreate step 6.a-c:
        // if constructor realm differs and C is that realm's intrinsic %Array%,
        // treat C as undefined.
        if (JSTypeChecking.isConstructor(ctor) && ctor instanceof JSObject ctorObject) {
            JSContext constructorRealm = getFunctionRealm(ctorObject);
            if (hasPendingException()) {
                return null;
            }
            if (constructorRealm != this) {
                JSValue realmArrayConstructor = constructorRealm.getGlobalObject().get(JSArray.NAME);
                if (ctor == realmArrayConstructor) {
                    ctor = JSUndefined.INSTANCE;
                }
            }
        }

        // Step 6: If Type(C) is Object, get Symbol.species
        if (ctor instanceof JSObject ctorObj) {
            ctor = ctorObj.get(this, PropertyKey.SYMBOL_SPECIES);
            if (hasPendingException()) {
                return null;
            }
            if (ctor instanceof JSNull) {
                ctor = JSUndefined.INSTANCE;
            }
        }

        // Step 7: If C is undefined, return ArrayCreate(length)
        if (ctor instanceof JSUndefined) {
            // ArrayCreate(length): throw RangeError if length > 2^32 - 1
            if (length > 0xFFFFFFFFL) {
                return throwRangeError("Invalid array length");
            }
            return createJSArray(length, 0);
        }

        // Step 8: If IsConstructor(C) is false, throw a TypeError exception
        if (!JSTypeChecking.isConstructor(ctor)) {
            return throwTypeError("Species constructor is not a constructor");
        }

        // Step 9: Return ? Construct(C, « length »)
        JSValue result = JSReflectObject.constructSimple(this, ctor, new JSValue[]{JSNumber.of(length)});
        if (hasPendingException()) {
            return null;
        }
        return result;
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

    public JSBigInt64Array createJSBigInt64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSBigUint64Array createJSBigUint64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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
    public JSDataView createJSDataView(IJSArrayBuffer buffer, int byteOffset, int byteLength) {
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

    public JSEvalError createJSEvalError(String message) {
        JSEvalError jsError = new JSEvalError(this, message);
        transferPrototype(jsError, JSEvalError.NAME);
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

    public JSFloat16Array createJSFloat16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSFloat32Array createJSFloat32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSFloat64Array createJSFloat64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSInt16Array createJSInt16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSInt32Array createJSInt32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSInt8Array createJSInt8Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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
        if (cachedObjectPrototype != null) {
            jsObject.setPrototype(cachedObjectPrototype);
        } else {
            transferPrototype(jsObject, JSObject.NAME);
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
        if (cachedPromisePrototype != null) {
            jsPromise.setPrototype(cachedPromisePrototype);
        } else {
            transferPrototype(jsPromise, JSPromise.NAME);
        }
        return jsPromise;
    }

    public JSRangeError createJSRangeError(String message) {
        JSRangeError jsError = new JSRangeError(this, message);
        transferPrototype(jsError, JSRangeError.NAME);
        return jsError;
    }

    public JSReferenceError createJSReferenceError(String message) {
        JSReferenceError jsError = new JSReferenceError(this, message);
        transferPrototype(jsError, JSReferenceError.NAME);
        return jsError;
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

    public JSStringObject createJSStringObject() {
        JSStringObject wrapper = new JSStringObject();
        transferPrototype(wrapper, JSStringObject.NAME);
        return wrapper;
    }

    public JSStringObject createJSStringObject(JSString value) {
        JSStringObject wrapper = new JSStringObject(value);
        transferPrototype(wrapper, JSStringObject.NAME);
        return wrapper;
    }

    public JSSuppressedError createJSSuppressedError(String message) {
        JSSuppressedError jsError = new JSSuppressedError(this, message);
        transferPrototype(jsError, JSSuppressedError.NAME);
        return jsError;
    }

    public JSSymbolObject createJSSymbolObject(JSSymbol value) {
        JSSymbolObject wrapper = new JSSymbolObject(value);
        transferPrototype(wrapper, JSSymbolObject.NAME);
        return wrapper;
    }

    public JSSyntaxError createJSSyntaxError(String message) {
        JSSyntaxError jsError = new JSSyntaxError(this, message);
        transferPrototype(jsError, JSSyntaxError.NAME);
        return jsError;
    }

    public JSTypeError createJSTypeError(String message) {
        JSTypeError jsError = new JSTypeError(this, message);
        transferPrototype(jsError, JSTypeError.NAME);
        return jsError;
    }

    public JSURIError createJSURIError(String message) {
        JSURIError jsError = new JSURIError(this, message);
        transferPrototype(jsError, JSURIError.NAME);
        return jsError;
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

    public JSUint16Array createJSUint16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSUint32Array createJSUint32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSUint8Array createJSUint8Array(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    public JSUint8ClampedArray createJSUint8ClampedArray(IJSArrayBuffer buffer, int byteOffset, int length) {
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

    private String createModuleExportBindingName(String resolvedSpecifier) {
        return "__qjs4jDynamicImportExports$" + Math.abs(resolvedSpecifier.hashCode()) + "$"
                + dynamicImportModuleCache.size();
    }

    private JSImportNamespaceObject createModuleNamespaceObject() {
        return new JSImportNamespaceObject(this);
    }

    private String decodeIdentifierEscapes(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder decodedTextBuilder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch != '\\' || index + 1 >= text.length() || text.charAt(index + 1) != 'u') {
                decodedTextBuilder.append(ch);
                continue;
            }
            int escapeStart = index;
            index += 2;
            if (index < text.length() && text.charAt(index) == '{') {
                int braceEnd = text.indexOf('}', index + 1);
                if (braceEnd < 0) {
                    decodedTextBuilder.append(text, escapeStart, index + 1);
                    index = escapeStart;
                    continue;
                }
                String codePointText = text.substring(index + 1, braceEnd);
                try {
                    int codePoint = Integer.parseInt(codePointText, 16);
                    decodedTextBuilder.appendCodePoint(codePoint);
                    index = braceEnd;
                } catch (NumberFormatException numberFormatException) {
                    decodedTextBuilder.append(text, escapeStart, braceEnd + 1);
                    index = braceEnd;
                }
                continue;
            }
            if (index + 3 >= text.length()) {
                decodedTextBuilder.append(text, escapeStart, text.length());
                break;
            }
            String hexText = text.substring(index, index + 4);
            try {
                int codePoint = Integer.parseInt(hexText, 16);
                decodedTextBuilder.append((char) codePoint);
                index += 3;
            } catch (NumberFormatException numberFormatException) {
                decodedTextBuilder.append(text, escapeStart, index + 4);
                index += 3;
            }
        }
        return decodedTextBuilder.toString();
    }

    private void defineDynamicImportNamespaceValue(
            DynamicImportModuleRecord moduleRecord,
            String exportName,
            JSValue exportValue) {
        moduleRecord.namespace().defineProperty(
                PropertyKey.fromString(exportName),
                exportValue,
                PropertyDescriptor.DataState.EnumerableWritable);
        moduleRecord.namespace().registerExportName(exportName);
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

    private String escapeJavaScriptString(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
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
        return eval(code, filename, isModule, isDirectEval, false, false, false, false);
    }

    private JSValue eval(String code, String filename, boolean isModule, boolean isDirectEval,
                         boolean predeclareProgramLexicalsAsLocals,
                         boolean skipGlobalDeclarationTracking,
                         boolean inheritedStrictModeForDirectEval,
                         boolean useDirectEvalCallerFrame) {
        if (code == null || code.isEmpty()) {
            return JSUndefined.INSTANCE;
        }

        // Check for recursion limit
        if (!pushStackFrame(new JSStackFrame("<eval>", filename, 1))) {
            return throwError("RangeError", "Maximum call stack size exceeded");
        }

        DynamicImportModuleRecord dynamicImportEvalModuleRecord = null;
        EvalOverlayFrame moduleNamespaceImportOverlay = null;
        boolean skipEvaluatedDynamicImportModule = false;
        if (isModule
                && !isDirectEval
                && filename != null
                && !filename.isEmpty()
                && !filename.startsWith("<")
                && (code.contains("import(") || code.contains("import.defer("))) {
            String resolvedModuleSpecifier = resolveDynamicImportSpecifier(filename, null, filename);
            dynamicImportEvalModuleRecord = dynamicImportModuleCache.get(resolvedModuleSpecifier);
            if (dynamicImportEvalModuleRecord == null) {
                dynamicImportEvalModuleRecord =
                        new DynamicImportModuleRecord(resolvedModuleSpecifier, createModuleNamespaceObject());
                dynamicImportEvalModuleRecord.setStatus(DynamicImportModuleStatus.LOADING);
                dynamicImportEvalModuleRecord.setRawSource(code);
                parseDynamicImportModuleSource(dynamicImportEvalModuleRecord);
                dynamicImportModuleCache.put(resolvedModuleSpecifier, dynamicImportEvalModuleRecord);
            } else if (dynamicImportEvalModuleRecord.status() == DynamicImportModuleStatus.EVALUATED) {
                skipEvaluatedDynamicImportModule = true;
            }
        }

        Compiler compiler = new Compiler(code, filename);
        // Per QuickJS, eval code has is_eval=true which prevents top-level return.
        // Only syntactic direct eval should inherit caller frame semantics.
        StackFrame directEvalCallerFrame = isDirectEval && useDirectEvalCallerFrame
                ? virtualMachine.getCurrentFrame()
                : null;
        boolean allowNewTargetInEval = false;
        boolean allowSuperPropertyInEval = false;
        boolean isClassFieldEval = consumeScheduledClassFieldEvalCall();
        if (isDirectEval) {
            compiler.setEval(true);
            if (directEvalCallerFrame != null
                    && directEvalCallerFrame.getFunction() instanceof JSBytecodeFunction callerBytecodeFunction) {
                allowNewTargetInEval = !callerBytecodeFunction.isArrow() && directEvalCallerFrame.getCaller() != null;
                allowSuperPropertyInEval = !callerBytecodeFunction.isArrow()
                        && callerBytecodeFunction.getHomeObject() != null;
            }
            // ES2024: class field initializer eval forbids arguments, new.target resolves to undefined
            if (isClassFieldEval) {
                compiler.setClassFieldEval(true);
            }
            compiler.setEvalContextFlags(allowSuperPropertyInEval, allowNewTargetInEval);
            // Direct eval creates a fresh lexical environment whose bindings do not leak.
            compiler.setPredeclareProgramLexicalsAsLocals(true);
            if (directEvalCallerFrame != null && (strictMode || inheritedStrictModeForDirectEval)) {
                compiler.setInheritedStrictMode(true);
            }
        }
        if (predeclareProgramLexicalsAsLocals) {
            compiler.setPredeclareProgramLexicalsAsLocals(true);
        }
        try {
            if (skipEvaluatedDynamicImportModule) {
                processMicrotasks();
                return JSUndefined.INSTANCE;
            }

            if (dynamicImportEvalModuleRecord != null && dynamicImportEvalModuleRecord.hasExportSyntax()) {
                evaluateDynamicImportModule(dynamicImportEvalModuleRecord);
                resolveDynamicImportReExports(dynamicImportEvalModuleRecord, new HashSet<>());
                dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                dynamicImportEvalModuleRecord.setStatus(DynamicImportModuleStatus.EVALUATED);
                processMicrotasks();
                return JSUndefined.INSTANCE;
            }

            // Phase 1-3: Lexer → Parser → Compiler (compile to bytecode)
            JSBytecodeFunction func;
            Compiler.CompileResult compileResult = compiler.compile(isModule);
            func = compileResult.function();
            if (!isModule && !isDirectEval && !skipGlobalDeclarationTracking) {
                // Top-level script: check GlobalDeclarationInstantiation per ES2024 16.1.7
                func = compileResult.function();

                // Collect new declarations from this script
                Set<String> newConstDecls = new HashSet<>();
                Set<String> newVarDecls = new HashSet<>();
                Set<String> newLexDecls = new HashSet<>();
                AstUtils.collectGlobalConstDeclarations(compileResult.ast(), newConstDecls);
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
                    PropertyDescriptor desc = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
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
                globalConstDeclarations.addAll(newConstDecls);
                globalLexDeclarations.addAll(newLexDecls);
                globalVarDeclarations.addAll(newVarDecls);

                // CreateGlobalVarDeclaration: define var bindings as non-configurable
                // properties on the global object (per ES2024 9.1.1.4.17 / QuickJS
                // js_closure_define_global_var with is_direct_or_indirect_eval=FALSE).
                // This must happen BEFORE execution so bindings exist at script start.
                for (String name : newVarDecls) {
                    PropertyKey key = PropertyKey.fromString(name);
                    PropertyDescriptor existing = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (existing == null) {
                        // Property doesn't exist: create {writable, enumerable, NOT configurable}
                        jsGlobalObject.getGlobalObject().defineProperty(key,
                                PropertyDescriptor.dataDescriptor(
                                        JSUndefined.INSTANCE,
                                        PropertyDescriptor.DataState.EnumerableWritable
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
                Set<String> globalEvalFunctionNames = new HashSet<>();
                for (int i = evalBody.size() - 1; i >= 0; i--) {
                    if (evalBody.get(i) instanceof FunctionDeclaration funcDecl && funcDecl.id() != null) {
                        String funcName = funcDecl.id().name();
                        if (checkedFuncNames.add(funcName)) {
                            globalEvalFunctionNames.add(funcName);
                            PropertyKey key = PropertyKey.fromString(funcName);
                            PropertyDescriptor desc = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                            if (desc != null && !desc.isConfigurable()) {
                                if (desc.isAccessorDescriptor()
                                        || !(desc.isWritable() && desc.isEnumerable())) {
                                    throw new JSException(throwTypeError("cannot define variable '" + funcName + "'"));
                                }
                            }
                            if (desc == null && !jsGlobalObject.getGlobalObject().isExtensible()) {
                                throw new JSException(throwTypeError("cannot define variable '" + funcName + "'"));
                            }
                            if (directEvalCallerFrame != null && directEvalCallerFrame.getCaller() == null) {
                                if (desc == null || desc.isConfigurable()) {
                                    JSValue initialValue = desc != null && desc.hasValue()
                                            ? desc.getValue()
                                            : JSUndefined.INSTANCE;
                                    jsGlobalObject.getGlobalObject().defineProperty(
                                            key,
                                            PropertyDescriptor.dataDescriptor(initialValue, PropertyDescriptor.DataState.All));
                                }
                            }
                        }
                    }
                }
                if (directEvalCallerFrame != null && directEvalCallerFrame.getCaller() == null) {
                    Set<String> evalVarDeclarations = new HashSet<>();
                    Set<String> evalLexDeclarations = new HashSet<>();
                    AstUtils.collectGlobalDeclarations(compileResult.ast(), evalVarDeclarations, evalLexDeclarations);
                    for (String declarationName : evalVarDeclarations) {
                        if (globalEvalFunctionNames.contains(declarationName)) {
                            continue;
                        }
                        PropertyKey key = PropertyKey.fromString(declarationName);
                        if (!jsGlobalObject.getGlobalObject().has(key)
                                && !jsGlobalObject.getGlobalObject().isExtensible()) {
                            throw new JSException(throwTypeError("cannot define variable '" + declarationName + "'"));
                        }
                    }
                }
            }

            // Initialize the function's prototype chain so it inherits from Function.prototype
            func.initializePrototypeChain(this);

            if (isModule && !isDirectEval) {
                moduleNamespaceImportOverlay = evaluateModuleNamespaceImports(code, filename);
                evaluateModuleSideEffectImports(code, filename);
            }

            // Phase 4: Execute bytecode in the virtual machine
            // For direct eval, inherit the caller's 'this' binding per ES2024 PerformEval.
            // In strict mode functions called without receiver, 'this' is undefined, and
            // eval('this') must see that same undefined value, not the global object.
            JSValue evalThisArg = jsGlobalObject.getGlobalObject();
            JSValue evalNewTarget = JSUndefined.INSTANCE;
            if (isDirectEval && directEvalCallerFrame != null) {
                evalThisArg = directEvalCallerFrame.getThisArg();
                if (allowNewTargetInEval) {
                    evalNewTarget = directEvalCallerFrame.getNewTarget();
                }
                if (allowSuperPropertyInEval) {
                    func.setHomeObject(directEvalCallerFrame.getFunction().getHomeObject());
                }
            }
            JSValue result = virtualMachine.execute(func, evalThisArg, JSValue.NO_ARGS, evalNewTarget);

            if (!isModule
                    && isDirectEval
                    && directEvalCallerFrame != null
                    && directEvalCallerFrame.getCaller() == null) {
                List<Statement> evalBody = compileResult.ast().body();
                Set<String> functionNames = new HashSet<>();
                for (int i = evalBody.size() - 1; i >= 0; i--) {
                    if (evalBody.get(i) instanceof FunctionDeclaration functionDeclaration && functionDeclaration.id() != null) {
                        functionNames.add(functionDeclaration.id().name());
                    }
                }
                for (String functionName : functionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    if (!jsGlobalObject.getGlobalObject().has(key)) {
                        continue;
                    }
                    JSValue functionValue = jsGlobalObject.getGlobalObject().get(key);
                    PropertyDescriptor existingDescriptor = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    PropertyDescriptor descriptor = new PropertyDescriptor();
                    descriptor.setValue(functionValue);
                    if (existingDescriptor == null || existingDescriptor.isConfigurable()) {
                        descriptor.setWritable(true);
                        descriptor.setEnumerable(true);
                        descriptor.setConfigurable(true);
                    } else {
                        descriptor.setWritable(existingDescriptor.isWritable());
                        descriptor.setEnumerable(existingDescriptor.isEnumerable());
                        descriptor.setConfigurable(false);
                    }
                    jsGlobalObject.getGlobalObject().defineProperty(key, descriptor);
                }
            }

            // Check if there's a pending exception
            if (hasPendingException()) {
                JSValue exception = getPendingException();
                throw new JSException(exception);
            }

            // Process all pending microtasks before returning
            processMicrotasks();

            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == DynamicImportModuleStatus.LOADING) {
                dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                dynamicImportEvalModuleRecord.setStatus(DynamicImportModuleStatus.EVALUATED);
            }

            return result != null ? result : JSUndefined.INSTANCE;
        } catch (JSException e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == DynamicImportModuleStatus.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            throw e;
        } catch (JSCompilerException e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == DynamicImportModuleStatus.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            JSValue error = throwError("SyntaxError", e.getMessage());
            throw new JSException(error);
        } catch (JSVirtualMachineException e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == DynamicImportModuleStatus.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            // VM exception - check if it has a JSError, otherwise check pending exception
            if (e.getJsError() != null) {
                throw new JSException(e.getJsError());
            }
            if (e.getJsValue() != null) {
                throw new JSException(e.getJsValue());
            }
            if (hasPendingException()) {
                JSValue exception = getPendingException();
                throw new JSException(exception);
            }
            JSValue error = throwError("Error", "VM error: " + e.getMessage());
            throw new JSException(error);
        } catch (JSErrorException e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == DynamicImportModuleStatus.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            JSValue error = throwError(e.getErrorType().name(), e.getMessage());
            throw new JSException(error);
        } catch (Exception e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == DynamicImportModuleStatus.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            JSValue error = throwError("Error", "Execution error: " + e.getMessage());
            throw new JSException(error);
        } finally {
            restoreEvalOverlayFrame(moduleNamespaceImportOverlay);
            popStackFrame();
            // Clear ALL possible dirty state to ensure clean slate for next eval()
            stackDepth = callStack.size();
            inCatchHandler = false;
            currentThis = jsGlobalObject.getGlobalObject();
            clearPendingException();
            clearErrorStackTrace();
        }
    }

    public JSValue evalDirect(String code, String filename, boolean inheritedStrictMode) {
        return eval(code, filename, false, true, false, false, inheritedStrictMode, true);
    }

    public JSValue evalIndirect(String code, String filename) {
        return eval(code, filename, false, true, false, false, false, false);
    }

    public JSValue evalWithProgramLexicalsAsLocals(String code, String filename, boolean isModule) {
        return eval(code, filename, isModule, false, true, true, false, false);
    }

    private void evaluateDynamicImportModule(DynamicImportModuleRecord moduleRecord) {
        if (!moduleRecord.hasExportSyntax()) {
            validateModuleScriptEarlyErrors(moduleRecord.rawSource());
            eval(moduleRecord.rawSource(), moduleRecord.resolvedSpecifier(), true);
            return;
        }
        String exportBindingName = moduleRecord.exportBindingName();
        JSObject globalObject = getGlobalObject();
        JSObject moduleNamespace = moduleRecord.namespace();
        globalObject.set(this, PropertyKey.fromString(exportBindingName), moduleNamespace);
        try {
            String transformedSource = moduleRecord.transformedSource();
            eval(transformedSource, moduleRecord.resolvedSpecifier(), false);
        } finally {
            globalObject.delete(this, PropertyKey.fromString(exportBindingName));
        }
    }

    private EvalOverlayFrame evaluateModuleNamespaceImports(String code, String filename) {
        Matcher matcher = MODULE_NAMESPACE_IMPORT_PATTERN.matcher(code);
        JSObject globalObject = getGlobalObject();
        Map<String, JSValue> savedGlobals = new HashMap<>();
        Set<String> absentKeys = new HashSet<>();

        while (matcher.find()) {
            String localName = matcher.group(1);
            String specifier = matcher.group(3);
            JSObject namespaceObject = loadDynamicImportModule(specifier, filename);
            PropertyKey key = PropertyKey.fromString(localName);
            if (globalObject.has(key)) {
                if (!savedGlobals.containsKey(localName)) {
                    savedGlobals.put(localName, globalObject.get(key));
                }
            } else {
                absentKeys.add(localName);
            }
            globalObject.set(this, key, namespaceObject);
        }

        if (savedGlobals.isEmpty() && absentKeys.isEmpty()) {
            return null;
        }
        return new EvalOverlayFrame(savedGlobals, absentKeys);
    }

    private void evaluateModuleSideEffectImports(String code, String filename) {
        Matcher matcher = MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(code);
        while (matcher.find()) {
            String specifier = matcher.group(2);
            loadDynamicImportModule(specifier, filename);
        }
    }

    /**
     * Exit strict mode.
     */
    public void exitStrictMode() {
        this.strictMode = false;
    }

    private String extractExportedFunctionOrClassName(String exportClause) {
        Matcher functionMatcher = DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN.matcher(exportClause);
        if (functionMatcher.find()) {
            return functionMatcher.group(1);
        }
        Matcher classMatcher = DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN.matcher(exportClause);
        if (classMatcher.find()) {
            return classMatcher.group(1);
        }
        return null;
    }

    private List<String> extractSimpleDeclarationNames(String declarationSource) {
        String declarationText = declarationSource.trim();
        int firstSpaceIndex = declarationText.indexOf(' ');
        if (firstSpaceIndex < 0 || firstSpaceIndex >= declarationText.length() - 1) {
            return List.of();
        }
        String declaratorsText = declarationText.substring(firstSpaceIndex + 1).trim();
        if (declaratorsText.endsWith(";")) {
            declaratorsText = declaratorsText.substring(0, declaratorsText.length() - 1).trim();
        }
        if (declaratorsText.isEmpty()) {
            return List.of();
        }
        String[] declarators = declaratorsText.split(",");
        List<String> declarationNames = new ArrayList<>(declarators.length);
        for (String declarator : declarators) {
            String declaratorText = declarator.trim();
            int assignmentIndex = declaratorText.indexOf('=');
            if (assignmentIndex >= 0) {
                declaratorText = declaratorText.substring(0, assignmentIndex).trim();
            }
            if (!declaratorText.isEmpty()) {
                declarationNames.add(declaratorText);
            }
        }
        return declarationNames;
    }

    private void gatherDeferredAsyncDependencySpecifiers(
            String resolvedSpecifier,
            String sourceCode,
            Set<String> visitedSpecifiers,
            Set<String> asyncDependencySpecifiers) {
        if (!visitedSpecifiers.add(resolvedSpecifier)) {
            return;
        }
        if (MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(sourceCode).find()) {
            asyncDependencySpecifiers.add(resolvedSpecifier);
            return;
        }
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String childSpecifier = matcher.group(1);
            String resolvedChildSpecifier = resolveDynamicImportSpecifier(
                    childSpecifier,
                    resolvedSpecifier,
                    childSpecifier);
            String childSourceCode;
            try {
                DynamicImportModuleRecord childRecord = dynamicImportModuleCache.get(resolvedChildSpecifier);
                if (childRecord != null && childRecord.rawSource() != null && !childRecord.rawSource().isEmpty()) {
                    childSourceCode = childRecord.rawSource();
                } else {
                    childSourceCode = Files.readString(Path.of(resolvedChildSpecifier));
                }
            } catch (IOException ioException) {
                throw new JSException(throwTypeError("Cannot find module '" + childSpecifier + "'"));
            }
            gatherDeferredAsyncDependencySpecifiers(
                    resolvedChildSpecifier,
                    childSourceCode,
                    visitedSpecifiers,
                    asyncDependencySpecifiers);
        }
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

    public JSObject getAsyncGeneratorPrototype() {
        return asyncGeneratorPrototype;
    }

    /**
     * Get the full call stack.
     */
    public List<JSStackFrame> getCallStack() {
        return new ArrayList<>(callStack);
    }

    /**
     * Get the pending exception.
     */
    public JSValue getConstructorNewTarget() {
        return constructorNewTarget;
    }

    /**
     * Get the current stack frame.
     */
    public JSStackFrame getCurrentStackFrame() {
        return callStack.peek();
    }

    /**
     * Get the current 'this' binding.
     */
    public JSValue getCurrentThis() {
        return currentThis;
    }

    private String getDynamicImportModuleExport(
            DynamicImportModuleRecord moduleRecord,
            String exportName,
            String targetSpecifier) {
        if (moduleRecord.ambiguousExportNames().contains(exportName)) {
            throw new JSException(throwSyntaxError("ambiguous indirect export: " + exportName));
        }
        PropertyKey exportKey = PropertyKey.fromString(exportName);
        if (!moduleRecord.namespace().hasOwnProperty(exportKey)) {
            throw new JSException(throwSyntaxError(
                    "module '" + targetSpecifier + "' does not provide export '" + exportName + "'"));
        }
        return exportName;
    }

    /**
     * Get the error stack trace.
     */
    public List<StackTraceElement> getErrorStackTrace() {
        return new ArrayList<>(errorStackTrace);
    }

    public JSContext getFunctionRealm(JSObject constructor) {
        return getFunctionRealmInternal(constructor, 0);
    }

    private JSContext getFunctionRealmInternal(JSValue value, int depth) {
        if (depth > 1000) {
            throwTypeError("too much recursion");
            return this;
        }
        if (value instanceof JSBoundFunction boundFunction) {
            return getFunctionRealmInternal(boundFunction.getTarget(), depth + 1);
        }
        if (value instanceof JSProxy proxy) {
            if (proxy.isRevoked()) {
                throwTypeError("Cannot perform 'get' on a proxy that has been revoked");
                return this;
            }
            return getFunctionRealmInternal(proxy.getTarget(), depth + 1);
        }
        if (value instanceof JSFunction function) {
            JSContext functionContext = function.getHomeContext();
            if (functionContext != null) {
                return functionContext;
            }
        }
        return this;
    }

    /**
     * Get the GeneratorFunction prototype (internal use only).
     * Used for setting up prototype chains for generator functions.
     */
    public JSObject getGeneratorFunctionPrototype() {
        return generatorFunctionPrototype;
    }

    public JSObject getGlobalObject() {
        return jsGlobalObject.getGlobalObject();
    }

    public String getIntrinsicDefaultPrototypeName(JSFunction function) {
        JSConstructorType constructorType = function.getConstructorType();
        if (constructorType != null) {
            return switch (constructorType) {
                case AGGREGATE_ERROR -> JSAggregateError.NAME;
                case ARRAY -> JSArray.NAME;
                case ARRAY_BUFFER -> JSArrayBuffer.NAME;
                case ASYNC_DISPOSABLE_STACK -> JSAsyncDisposableStack.NAME;
                case BIG_INT_OBJECT -> JSBigIntObject.NAME;
                case BOOLEAN_OBJECT -> JSBooleanObject.NAME;
                case DATA_VIEW -> JSDataView.NAME;
                case DATE -> JSDate.NAME;
                case DISPOSABLE_STACK -> JSDisposableStack.NAME;
                case ERROR -> JSError.NAME;
                case EVAL_ERROR -> JSEvalError.NAME;
                case FINALIZATION_REGISTRY -> JSFinalizationRegistry.NAME;
                case MAP -> JSMap.NAME;
                case NUMBER_OBJECT -> JSNumberObject.NAME;
                case PROMISE -> JSPromise.NAME;
                case PROXY -> JSObject.NAME;
                case RANGE_ERROR -> JSRangeError.NAME;
                case REFERENCE_ERROR -> JSReferenceError.NAME;
                case REGEXP -> JSRegExp.NAME;
                case SET -> JSSet.NAME;
                case SHARED_ARRAY_BUFFER -> JSSharedArrayBuffer.NAME;
                case STRING_OBJECT -> JSStringObject.NAME;
                case SUPPRESSED_ERROR -> JSSuppressedError.NAME;
                case SYMBOL_OBJECT -> JSSymbolObject.NAME;
                case SYNTAX_ERROR -> JSSyntaxError.NAME;
                case TYPED_ARRAY_BIGINT64 -> JSBigInt64Array.NAME;
                case TYPED_ARRAY_BIGUINT64 -> JSBigUint64Array.NAME;
                case TYPED_ARRAY_FLOAT16 -> JSFloat16Array.NAME;
                case TYPED_ARRAY_FLOAT32 -> JSFloat32Array.NAME;
                case TYPED_ARRAY_FLOAT64 -> JSFloat64Array.NAME;
                case TYPED_ARRAY_INT16 -> JSInt16Array.NAME;
                case TYPED_ARRAY_INT32 -> JSInt32Array.NAME;
                case TYPED_ARRAY_INT8 -> JSInt8Array.NAME;
                case TYPED_ARRAY_UINT16 -> JSUint16Array.NAME;
                case TYPED_ARRAY_UINT32 -> JSUint32Array.NAME;
                case TYPED_ARRAY_UINT8 -> JSUint8Array.NAME;
                case TYPED_ARRAY_UINT8_CLAMPED -> JSUint8ClampedArray.NAME;
                case TYPE_ERROR -> JSTypeError.NAME;
                case URI_ERROR -> JSURIError.NAME;
                case WEAK_MAP -> JSWeakMap.NAME;
                case WEAK_REF -> JSWeakRef.NAME;
                case WEAK_SET -> JSWeakSet.NAME;
            };
        }
        if (function instanceof JSClass) {
            return JSObject.NAME;
        }
        String functionName = function.getName();
        if (JSFunction.NAME.equals(functionName)) {
            return JSFunction.NAME;
        }
        if ("GeneratorFunction".equals(functionName)) {
            return "GeneratorFunction";
        }
        if ("AsyncFunction".equals(functionName)) {
            return "AsyncFunction";
        }
        if ("AsyncGeneratorFunction".equals(functionName)) {
            return "AsyncGeneratorFunction";
        }
        if (JSIterator.NAME.equals(functionName)) {
            return JSIterator.NAME;
        }
        return JSObject.NAME;
    }

    private JSObject getIntrinsicPrototype(JSContext realmContext, String intrinsicDefaultPrototypeName) {
        if (JSObject.NAME.equals(intrinsicDefaultPrototypeName)) {
            return realmContext.getObjectPrototype();
        }
        if ("GeneratorFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject generatorFunctionPrototype = realmContext.getGeneratorFunctionPrototype();
            if (generatorFunctionPrototype != null) {
                return generatorFunctionPrototype;
            }
            return realmContext.getObjectPrototype();
        }
        if ("AsyncGeneratorFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject asyncGeneratorFunctionPrototype = realmContext.getAsyncGeneratorFunctionPrototype();
            if (asyncGeneratorFunctionPrototype != null) {
                return asyncGeneratorFunctionPrototype;
            }
            return realmContext.getObjectPrototype();
        }
        if ("AsyncFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject asyncFunctionConstructor = realmContext.getAsyncFunctionConstructor();
            if (asyncFunctionConstructor != null) {
                JSValue asyncFunctionPrototype = asyncFunctionConstructor.get(PropertyKey.PROTOTYPE);
                if (asyncFunctionPrototype instanceof JSObject asyncFunctionPrototypeObject) {
                    return asyncFunctionPrototypeObject;
                }
            }
            JSValue fallbackFunctionConstructor = realmContext.getGlobalObject().get(JSFunction.NAME);
            if (fallbackFunctionConstructor instanceof JSObject fallbackFunctionObject) {
                JSValue fallbackFunctionPrototype = fallbackFunctionObject.get(PropertyKey.PROTOTYPE);
                if (fallbackFunctionPrototype instanceof JSObject fallbackFunctionPrototypeObject) {
                    return fallbackFunctionPrototypeObject;
                }
            }
            return realmContext.getObjectPrototype();
        }

        JSValue intrinsicConstructor = realmContext.getGlobalObject().get(intrinsicDefaultPrototypeName);
        if (intrinsicConstructor instanceof JSObject intrinsicObject) {
            JSValue intrinsicPrototype = intrinsicObject.get(PropertyKey.PROTOTYPE);
            if (intrinsicPrototype instanceof JSObject intrinsicPrototypeObject) {
                return intrinsicPrototypeObject;
            }
        }
        return realmContext.getObjectPrototype();
    }

    public JSObject getIteratorPrototype(String tag) {
        return iteratorPrototypes.get(tag);
    }

    public java.util.Collection<JSObject> getIteratorPrototypes() {
        return iteratorPrototypes.values();
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

    public JSValue getNativeConstructorNewTarget() {
        return nativeConstructorNewTarget;
    }

    public JSObject getObjectPrototype() {
        return cachedObjectPrototype;
    }

    public JSValue getPendingException() {
        return pendingException;
    }

    public IJSPromiseRejectCallback getPromiseRejectCallback() {
        return promiseRejectCallback;
    }

    public JSObject getPrototypeFromConstructor(JSObject constructor, String intrinsicDefaultPrototypeName) {
        JSValue prototype = constructor.get(this, PropertyKey.PROTOTYPE);
        if (hasPendingException()) {
            return null;
        }
        if (prototype instanceof JSObject prototypeObject) {
            return prototypeObject;
        }

        JSContext functionRealm = getFunctionRealm(constructor);
        if (hasPendingException()) {
            return null;
        }
        return getIntrinsicPrototype(functionRealm, intrinsicDefaultPrototypeName);
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
     * Get the %ThrowTypeError% intrinsic function.
     * This is the single shared function used for Function.prototype caller/arguments
     * and strict mode arguments.callee per ES spec.
     */
    public JSNativeFunction getThrowTypeErrorIntrinsic() {
        return throwTypeErrorIntrinsic;
    }

    /**
     * Get the virtual machine for this context.
     */
    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public boolean hasGlobalConstDeclaration(String name) {
        return globalConstDeclarations.contains(name);
    }

    public boolean hasGlobalLexDeclaration(String name) {
        return globalLexDeclarations.contains(name);
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
        jsGlobalObject.initialize();
        // Cache Object.prototype for fast access in hot paths (e.g., iteratorResult, createJSObject)
        JSValue objectCtor = jsGlobalObject.getGlobalObject().get(JSObject.NAME);
        if (objectCtor instanceof JSObject objCtorObj) {
            JSValue proto = objCtorObj.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                this.cachedObjectPrototype = protoObj;
            }
        }
        JSValue promiseCtor = jsGlobalObject.getGlobalObject().get(JSPromise.NAME);
        if (promiseCtor instanceof JSObject promiseCtorObject) {
            JSValue proto = promiseCtorObject.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                this.cachedPromisePrototype = protoObj;
            }
        }
    }

    private <T extends JSTypedArray> T initializeTypedArray(T typedArray, String constructorName) {
        transferPrototype(typedArray, constructorName);
        var buffer = typedArray.getBuffer();
        if (buffer instanceof JSObject jsObject && jsObject.getPrototype() == null) {
            transferPrototype(jsObject, buffer.isShared() ? JSSharedArrayBuffer.NAME : JSArrayBuffer.NAME);
        }
        return typedArray;
    }

    private boolean isDynamicImportDefaultDeclarationClause(String defaultClause) {
        return defaultClause.startsWith("function")
                || defaultClause.startsWith("async function")
                || defaultClause.startsWith("class");
    }

    /**
     * Check if in strict mode.
     */
    public boolean isStrictMode() {
        return strictMode;
    }

    public boolean isWaitable() {
        return waitable;
    }

    public JSObject loadDynamicImportModule(String specifier, String referrerFilename) {
        return loadDynamicImportModule(specifier, referrerFilename, null);
    }

    public JSObject loadDynamicImportModule(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes) {
        String resolvedSpecifier = resolveDynamicImportSpecifier(specifier, referrerFilename, specifier);
        DynamicImportModuleRecord moduleRecord =
                loadDynamicImportModuleRecord(resolvedSpecifier, new HashSet<>(), importAttributes);
        return moduleRecord.namespace();
    }

    public JSObject loadDynamicImportModuleDeferred(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes) {
        String resolvedSpecifier = resolveDynamicImportSpecifier(specifier, referrerFilename, specifier);
        DynamicImportModuleRecord moduleRecord = dynamicImportModuleCache.get(resolvedSpecifier);
        if (moduleRecord == null) {
            moduleRecord = new DynamicImportModuleRecord(resolvedSpecifier, createModuleNamespaceObject());
            moduleRecord.setStatus(DynamicImportModuleStatus.LOADING);
            dynamicImportModuleCache.put(resolvedSpecifier, moduleRecord);
            try {
                String sourceCode = Files.readString(Path.of(resolvedSpecifier));
                moduleRecord.setRawSource(sourceCode);
                if (resolvedSpecifier.endsWith(".json")) {
                    String importType = importAttributes != null ? importAttributes.get("type") : null;
                    if (!"json".equals(importType)) {
                        throw new JSException(throwTypeError("Import attribute type must be 'json'"));
                    }
                    JSValue jsonDefaultValue = parseJsonModuleSource(sourceCode);
                    defineDynamicImportNamespaceValue(moduleRecord, "default", jsonDefaultValue);
                    moduleRecord.explicitExportNames().add("default");
                    moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(DynamicImportModuleStatus.EVALUATED);
                    return moduleRecord.namespace();
                }
                parseDynamicImportModuleSource(moduleRecord);
            } catch (IOException ioException) {
                dynamicImportModuleCache.remove(resolvedSpecifier);
                throw new JSException(throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
            } catch (JSException jsException) {
                dynamicImportModuleCache.remove(resolvedSpecifier);
                throw jsException;
            }
        }

        if (moduleRecord.status() == DynamicImportModuleStatus.EVALUATED) {
            return moduleRecord.namespace();
        }

        LinkedHashSet<String> asyncDependencySpecifiers = new LinkedHashSet<>();
        gatherDeferredAsyncDependencySpecifiers(
                resolvedSpecifier,
                moduleRecord.rawSource(),
                new HashSet<>(),
                asyncDependencySpecifiers);
        for (String asyncDependencySpecifier : asyncDependencySpecifiers) {
            if (asyncDependencySpecifier.equals(resolvedSpecifier)
                    && moduleRecord.status() == DynamicImportModuleStatus.LOADING) {
                evaluateDynamicImportModule(moduleRecord);
                resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(DynamicImportModuleStatus.EVALUATED);
            } else {
                loadDynamicImportModuleRecord(asyncDependencySpecifier, new HashSet<>(), importAttributes);
            }
        }

        if (moduleRecord.status() == DynamicImportModuleStatus.EVALUATED) {
            return moduleRecord.namespace();
        }
        if (moduleRecord.deferredNamespace() == null) {
            moduleRecord.setDeferredNamespace(new JSDeferredModuleNamespace(this, moduleRecord));
        }
        return moduleRecord.deferredNamespace();
    }

    private DynamicImportModuleRecord loadDynamicImportModuleRecord(
            String resolvedSpecifier,
            Set<String> importResolutionStack,
            Map<String, String> importAttributes) {
        DynamicImportModuleRecord cachedRecord = dynamicImportModuleCache.get(resolvedSpecifier);
        if (cachedRecord != null) {
            if (cachedRecord.status() == DynamicImportModuleStatus.EVALUATED) {
                return cachedRecord;
            }
            if (cachedRecord.status() == DynamicImportModuleStatus.LOADING) {
                return cachedRecord;
            }
        }

        DynamicImportModuleRecord moduleRecord =
                new DynamicImportModuleRecord(resolvedSpecifier, createModuleNamespaceObject());
        moduleRecord.setStatus(DynamicImportModuleStatus.LOADING);
        dynamicImportModuleCache.put(resolvedSpecifier, moduleRecord);

        try {
            String sourceCode = Files.readString(Path.of(resolvedSpecifier));
            moduleRecord.setRawSource(sourceCode);
            if (resolvedSpecifier.endsWith(".json")) {
                String importType = importAttributes != null ? importAttributes.get("type") : null;
                if (!"json".equals(importType)) {
                    throw new JSException(throwTypeError("Import attribute type must be 'json'"));
                }
                JSValue jsonDefaultValue = parseJsonModuleSource(sourceCode);
                defineDynamicImportNamespaceValue(moduleRecord, "default", jsonDefaultValue);
                moduleRecord.explicitExportNames().add("default");
                moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(DynamicImportModuleStatus.EVALUATED);
                return moduleRecord;
            }
            parseDynamicImportModuleSource(moduleRecord);
            evaluateDynamicImportModule(moduleRecord);
            resolveDynamicImportReExports(moduleRecord, importResolutionStack);
            moduleRecord.namespace().finalizeNamespace();
            moduleRecord.setStatus(DynamicImportModuleStatus.EVALUATED);
            return moduleRecord;
        } catch (IOException ioException) {
            throw new JSException(throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
        } catch (JSException jsException) {
            dynamicImportModuleCache.remove(resolvedSpecifier);
            throw jsException;
        } catch (Exception exception) {
            dynamicImportModuleCache.remove(resolvedSpecifier);
            throw new JSException(throwError("Error", exception.getMessage() != null ? exception.getMessage() : "Module load error"));
        }
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

    private void mergeStarReExport(
            DynamicImportModuleRecord moduleRecord,
            DynamicImportModuleRecord targetModuleRecord,
            Map<String, String> exportOrigins,
            String targetSpecifier) {
        for (PropertyKey key : targetModuleRecord.namespace().getOwnPropertyKeys()) {
            if (!key.isString()) {
                continue;
            }
            String exportName = key.asString();
            if ("default".equals(exportName)) {
                continue;
            }
            if (targetModuleRecord.ambiguousExportNames().contains(exportName)) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().delete(this, PropertyKey.fromString(exportName));
                exportOrigins.remove(exportName);
                continue;
            }
            if (moduleRecord.explicitExportNames().contains(exportName)) {
                continue;
            }
            String existingOrigin = exportOrigins.get(exportName);
            if (existingOrigin == null) {
                JSValue value = targetModuleRecord.namespace().get(this, key);
                defineDynamicImportNamespaceValue(moduleRecord, exportName, value);
                exportOrigins.put(exportName, targetSpecifier);
                continue;
            }
            if (!existingOrigin.equals(targetSpecifier)) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().delete(this, PropertyKey.fromString(exportName));
                exportOrigins.remove(exportName);
            }
        }
    }

    private void parseDynamicImportExportList(
            String exportListText,
            String sourceSpecifier,
            List<LocalExportBinding> localExportBindings,
            List<ReExportBinding> reExportBindings) {
        String[] exportEntries = exportListText.split(",");
        for (String exportEntry : exportEntries) {
            String exportText = exportEntry.trim();
            if (exportText.isEmpty()) {
                continue;
            }
            String[] aliasParts = exportText.split("\\s+as\\s+");
            String localName = decodeIdentifierEscapes(aliasParts[0].trim());
            String exportedName = aliasParts.length > 1
                    ? decodeIdentifierEscapes(aliasParts[1].trim())
                    : localName;
            if (sourceSpecifier == null) {
                localExportBindings.add(new LocalExportBinding(localName, exportedName));
            } else {
                reExportBindings.add(new ReExportBinding(sourceSpecifier, localName, exportedName, false));
            }
        }
    }

    private void parseDynamicImportModuleSource(DynamicImportModuleRecord moduleRecord) {
        String sourceCode = moduleRecord.rawSource();
        StringBuilder transformedSourceBuilder = new StringBuilder(sourceCode.length() + 128);
        List<LocalExportBinding> localExportBindings = new ArrayList<>();
        List<ReExportBinding> reExportBindings = new ArrayList<>();
        boolean hasExportSyntax = false;
        int defaultExportIndex = 0;

        String[] lines = sourceCode.split("\n", -1);
        for (String line : lines) {
            String normalizedLine = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            String parseLine = normalizedLine;
            int lineCommentIndex = parseLine.indexOf("//");
            if (lineCommentIndex >= 0) {
                parseLine = parseLine.substring(0, lineCommentIndex);
            }
            String trimmedLine = parseLine.stripLeading();
            if (!trimmedLine.startsWith("export ")) {
                transformedSourceBuilder.append(normalizedLine).append('\n');
                continue;
            }

            hasExportSyntax = true;
            String exportClause = trimmedLine.substring("export ".length()).trim();
            if (exportClause.startsWith("default ")) {
                String defaultClause = exportClause.substring("default ".length()).trim();
                if (isDynamicImportDefaultDeclarationClause(defaultClause)) {
                    String declarationName = extractExportedFunctionOrClassName(defaultClause);
                    String declarationLine = normalizedLine.replaceFirst("^(\\s*)export\\s+default\\s+", "$1");
                    boolean anonymousDefaultDeclaration = false;
                    if (declarationName == null || declarationName.isEmpty()) {
                        String defaultLocalName = "__qjs4jDefaultExport$" + defaultExportIndex++;
                        declarationName = defaultLocalName;
                        declarationLine = renameAnonymousDefaultExportDeclaration(declarationLine, defaultLocalName);
                        anonymousDefaultDeclaration = true;
                    }
                    transformedSourceBuilder.append(declarationLine).append('\n');
                    if (anonymousDefaultDeclaration) {
                        appendDynamicImportDefaultExportNameFixup(transformedSourceBuilder, declarationName);
                    }
                    localExportBindings.add(new LocalExportBinding(declarationName, "default"));
                } else {
                    String defaultExpression = defaultClause;
                    while (defaultExpression.endsWith(";")) {
                        defaultExpression = defaultExpression.substring(0, defaultExpression.length() - 1).trim();
                    }
                    String defaultLocalName = "__qjs4jDefaultExport$" + defaultExportIndex++;
                    transformedSourceBuilder.append("const ")
                            .append(defaultLocalName)
                            .append(" = (0, ")
                            .append(defaultExpression)
                            .append(");\n");
                    appendDynamicImportDefaultExportNameFixup(transformedSourceBuilder, defaultLocalName);
                    localExportBindings.add(new LocalExportBinding(defaultLocalName, "default"));
                }
                continue;
            }

            if (exportClause.startsWith("var ")
                    || exportClause.startsWith("let ")
                    || exportClause.startsWith("const ")) {
                transformedSourceBuilder.append(normalizedLine.replaceFirst("export\\s+", "")).append('\n');
                for (String declarationName : extractSimpleDeclarationNames(exportClause)) {
                    localExportBindings.add(new LocalExportBinding(declarationName, declarationName));
                }
                continue;
            }

            if (exportClause.startsWith("function ")
                    || exportClause.startsWith("function*")
                    || exportClause.startsWith("async function ")
                    || exportClause.startsWith("async function*")
                    || exportClause.startsWith("class ")) {
                transformedSourceBuilder.append(normalizedLine.replaceFirst("export\\s+", "")).append('\n');
                String declarationName = extractExportedFunctionOrClassName(exportClause);
                if (declarationName == null || declarationName.isEmpty()) {
                    throw new JSException(throwSyntaxError("Invalid export statement"));
                }
                localExportBindings.add(new LocalExportBinding(declarationName, declarationName));
                continue;
            }

            if (exportClause.startsWith("{")) {
                int closeBraceIndex = exportClause.indexOf('}');
                if (closeBraceIndex < 0) {
                    throw new JSException(throwSyntaxError("Invalid export statement"));
                }
                String exportListText = exportClause.substring(1, closeBraceIndex).trim();
                String afterBraceText = exportClause.substring(closeBraceIndex + 1).trim();
                while (afterBraceText.endsWith(";")) {
                    afterBraceText = afterBraceText.substring(0, afterBraceText.length() - 1).trim();
                }
                String sourceSpecifier = null;
                if (!afterBraceText.isEmpty()) {
                    if (!afterBraceText.startsWith("from ")) {
                        throw new JSException(throwSyntaxError("Invalid export statement"));
                    }
                    String fromText = afterBraceText.substring("from ".length()).trim();
                    sourceSpecifier = stripQuotedSpecifier(fromText);
                }
                parseDynamicImportExportList(exportListText, sourceSpecifier, localExportBindings, reExportBindings);
                continue;
            }

            if (exportClause.startsWith("*")) {
                String afterStarText = exportClause.substring(1).trim();
                if (afterStarText.startsWith("as ")) {
                    Matcher namespaceExportMatcher = Pattern.compile("^as\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+from\\s+(.+)$")
                            .matcher(afterStarText);
                    if (!namespaceExportMatcher.find()) {
                        throw new JSException(throwSyntaxError("Invalid export statement"));
                    }
                    String exportedName = namespaceExportMatcher.group(1);
                    String fromText = namespaceExportMatcher.group(2).trim();
                    while (fromText.endsWith(";")) {
                        fromText = fromText.substring(0, fromText.length() - 1).trim();
                    }
                    String sourceSpecifier = stripQuotedSpecifier(fromText);
                    reExportBindings.add(new ReExportBinding(sourceSpecifier, "*namespace*", exportedName, false));
                    continue;
                }
                if (afterStarText.startsWith("from ")) {
                    String fromText = afterStarText.substring("from ".length()).trim();
                    while (fromText.endsWith(";")) {
                        fromText = fromText.substring(0, fromText.length() - 1).trim();
                    }
                    String sourceSpecifier = stripQuotedSpecifier(fromText);
                    reExportBindings.add(new ReExportBinding(sourceSpecifier, "*", "*", true));
                    continue;
                }
                throw new JSException(throwSyntaxError("Invalid export statement"));
            }

            throw new JSException(throwSyntaxError("Unexpected export syntax"));
        }

        moduleRecord.setHasExportSyntax(hasExportSyntax);
        moduleRecord.localExportBindings().addAll(localExportBindings);
        moduleRecord.reExportBindings().addAll(reExportBindings);
        for (LocalExportBinding localExportBinding : localExportBindings) {
            moduleRecord.explicitExportNames().add(localExportBinding.exportedName());
            moduleRecord.exportOrigins().put(localExportBinding.exportedName(), moduleRecord.resolvedSpecifier());
            moduleRecord.namespace().registerExportName(localExportBinding.exportedName());
        }

        if (hasExportSyntax) {
            String exportBindingName = createModuleExportBindingName(moduleRecord.resolvedSpecifier());
            appendDynamicImportExportAssignments(transformedSourceBuilder, exportBindingName, localExportBindings);
            String transformedSource = "(function () {\n"
                    + transformedSourceBuilder
                    + "})();\n";
            moduleRecord.setTransformedSource(transformedSource);
            moduleRecord.setExportBindingName(exportBindingName);
        } else {
            moduleRecord.setTransformedSource(sourceCode);
        }
    }

    private JSValue parseJsonModuleSource(String sourceCode) {
        JSValue jsonValue = getGlobalObject().get(PropertyKey.fromString("JSON"));
        if (hasPendingException()) {
            JSValue error = getPendingException();
            clearPendingException();
            throw new JSException(error);
        }
        if (!(jsonValue instanceof JSObject jsonObject)) {
            throw new JSException(throwTypeError("JSON is not an object"));
        }

        JSValue parseValue = jsonObject.get(this, PropertyKey.fromString("parse"));
        if (hasPendingException()) {
            JSValue error = getPendingException();
            clearPendingException();
            throw new JSException(error);
        }

        JSValue[] parseArguments = new JSValue[]{new JSString(sourceCode)};
        JSValue parsedValue;
        if (parseValue instanceof JSFunction parseFunction) {
            parsedValue = parseFunction.call(this, jsonObject, parseArguments);
        } else if (parseValue instanceof JSProxy parseProxy) {
            parsedValue = parseProxy.apply(this, jsonObject, parseArguments);
        } else {
            throw new JSException(throwTypeError("JSON.parse is not a function"));
        }
        if (hasPendingException()) {
            JSValue error = getPendingException();
            clearPendingException();
            throw new JSException(error);
        }
        return parsedValue;
    }

    public void popEvalOverlay() {
        if (!evalOverlayFrames.isEmpty()) {
            evalOverlayFrames.pop();
        }
    }

    /**
     * Pop the current stack frame.
     */
    public JSStackFrame popStackFrame() {
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

    public void pushEvalOverlay(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
        evalOverlayFrames.push(new EvalOverlayFrame(savedGlobals, absentKeys));
    }

    /**
     * Push a new stack frame.
     * Returns false if stack limit exceeded.
     */
    public boolean pushStackFrame(JSStackFrame frame) {
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
    public void registerIteratorPrototype(String tag, JSObject prototype) {
        iteratorPrototypes.put(tag, prototype);
    }

    public void registerModule(String specifier, JSModule module) {
        moduleCache.put(specifier, module);
    }

    private String renameAnonymousDefaultExportDeclaration(
            String declarationLine,
            String defaultLocalName) {
        String replacementName = Matcher.quoteReplacement(defaultLocalName);
        String renamedFunctionDeclaration = declarationLine.replaceFirst(
                "^(\\s*(?:async\\s+)?function(?:\\s*\\*)?)\\s*\\(",
                "$1 " + replacementName + "(");
        if (!renamedFunctionDeclaration.equals(declarationLine)) {
            return renamedFunctionDeclaration;
        }

        String renamedClassDeclaration = declarationLine.replaceFirst(
                "^(\\s*class)\\b",
                "$1 " + replacementName);
        if (!renamedClassDeclaration.equals(declarationLine)) {
            return renamedClassDeclaration;
        }

        throw new JSException(throwSyntaxError("Invalid default export declaration"));
    }

    private void resolveDynamicImportReExports(
            DynamicImportModuleRecord moduleRecord,
            Set<String> importResolutionStack) {
        if (moduleRecord.reExportBindings().isEmpty()) {
            return;
        }

        if (!importResolutionStack.add(moduleRecord.resolvedSpecifier())) {
            throw new JSException(throwSyntaxError("Circular module dependency"));
        }
        try {
            Map<String, String> exportOrigins = moduleRecord.exportOrigins();
            for (ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
                String targetSpecifier = resolveDynamicImportSpecifier(
                        reExportBinding.sourceSpecifier(),
                        moduleRecord.resolvedSpecifier(),
                        reExportBinding.sourceSpecifier());
                DynamicImportModuleRecord targetModuleRecord =
                        loadDynamicImportModuleRecord(targetSpecifier, importResolutionStack, null);
                if (reExportBinding.starExport()) {
                    mergeStarReExport(moduleRecord, targetModuleRecord, exportOrigins, targetSpecifier);
                    continue;
                }
                String importedName = reExportBinding.importedName();
                JSValue importedValue;
                if ("*namespace*".equals(importedName)) {
                    importedValue = targetModuleRecord.namespace();
                } else {
                    importedName = getDynamicImportModuleExport(targetModuleRecord, importedName, targetSpecifier);
                    importedValue = targetModuleRecord.namespace().get(this, PropertyKey.fromString(importedName));
                }
                defineDynamicImportNamespaceValue(moduleRecord, reExportBinding.exportedName(), importedValue);
                moduleRecord.explicitExportNames().add(reExportBinding.exportedName());
                exportOrigins.put(reExportBinding.exportedName(), targetSpecifier);
            }
        } finally {
            importResolutionStack.remove(moduleRecord.resolvedSpecifier());
        }
    }

    private String resolveDynamicImportSpecifier(
            String specifier,
            String referrerFilename,
            String errorSpecifier) {
        final Path rawSpecifierPath;
        try {
            rawSpecifierPath = Paths.get(specifier);
        } catch (InvalidPathException invalidPathException) {
            throw new JSException(throwTypeError("Cannot find module '" + errorSpecifier + "'"));
        }

        Path resolvedPath = rawSpecifierPath;
        if (!resolvedPath.isAbsolute()
                && referrerFilename != null
                && !referrerFilename.isEmpty()
                && !referrerFilename.startsWith("<")) {
            Path referrerPath = Paths.get(referrerFilename);
            Path parentPath = referrerPath.getParent();
            if (parentPath != null) {
                resolvedPath = parentPath.resolve(resolvedPath);
            }
        }
        resolvedPath = resolvedPath.normalize();
        if (!Files.exists(resolvedPath)) {
            throw new JSException(throwTypeError("Cannot find module '" + errorSpecifier + "'"));
        }
        return resolvedPath.toString();
    }

    private void restoreEvalOverlayFrame(EvalOverlayFrame evalOverlayFrame) {
        if (evalOverlayFrame == null) {
            return;
        }
        JSObject globalObject = getGlobalObject();
        for (var entry : evalOverlayFrame.savedGlobals().entrySet()) {
            globalObject.set(this, PropertyKey.fromString(entry.getKey()), entry.getValue());
        }
        for (String absentKey : evalOverlayFrame.absentKeys()) {
            globalObject.delete(this, PropertyKey.fromString(absentKey));
        }
    }

    public void resumeEvalOverlays(EvalOverlaySnapshot evalOverlaySnapshot) {
        if (evalOverlaySnapshot == null) {
            return;
        }
        JSObject globalObject = getGlobalObject();
        for (var entry : evalOverlaySnapshot.values().entrySet()) {
            globalObject.set(this, PropertyKey.fromString(entry.getKey()), entry.getValue());
        }
        for (String absentKey : evalOverlaySnapshot.absentKeys()) {
            globalObject.delete(this, PropertyKey.fromString(absentKey));
        }
    }

    public void scheduleClassFieldEvalCall() {
        pendingClassFieldEval = true;
    }

    public void scheduleDirectEvalCall() {
        pendingDirectEvalCalls++;
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

    public void setAsyncGeneratorPrototype(JSObject asyncGeneratorPrototype) {
        this.asyncGeneratorPrototype = asyncGeneratorPrototype;
    }

    public void setConstructorNewTarget(JSValue newTarget) {
        this.constructorNewTarget = newTarget;
    }

    /**
     * Set the current 'this' binding.
     */
    public void setCurrentThis(JSValue thisValue) {
        this.currentThis = thisValue != null ? thisValue : jsGlobalObject.getGlobalObject();
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

    public void setNativeConstructorNewTarget(JSValue newTarget) {
        this.nativeConstructorNewTarget = newTarget;
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
    public void setPromiseRejectCallback(IJSPromiseRejectCallback callback) {
        this.promiseRejectCallback = callback;
    }

    /**
     * Set the %ThrowTypeError% intrinsic function.
     * Called during global object initialization.
     */
    public void setThrowTypeErrorIntrinsic(JSNativeFunction throwTypeError) {
        this.throwTypeErrorIntrinsic = throwTypeError;
    }

    public void setWaitable(boolean waitable) {
        this.waitable = waitable;
    }

    private String stripQuotedSpecifier(String text) {
        String specifierText = text.trim();
        if (specifierText.length() < 2) {
            throw new JSException(throwSyntaxError("Invalid module specifier"));
        }
        char firstChar = specifierText.charAt(0);
        char lastChar = specifierText.charAt(specifierText.length() - 1);
        if (!((firstChar == '\'' || firstChar == '"') && firstChar == lastChar)) {
            throw new JSException(throwSyntaxError("Invalid module specifier"));
        }
        return specifierText.substring(1, specifierText.length() - 1);
    }

    public EvalOverlaySnapshot suspendEvalOverlays() {
        if (evalOverlayFrames.isEmpty()) {
            return null;
        }
        JSObject globalObject = getGlobalObject();
        Set<String> overlaidKeys = new HashSet<>();
        for (EvalOverlayFrame evalOverlayFrame : evalOverlayFrames) {
            overlaidKeys.addAll(evalOverlayFrame.savedGlobals().keySet());
            overlaidKeys.addAll(evalOverlayFrame.absentKeys());
        }

        Map<String, JSValue> suspendedValues = new HashMap<>();
        Set<String> suspendedAbsentKeys = new HashSet<>();
        for (String key : overlaidKeys) {
            PropertyKey propertyKey = PropertyKey.fromString(key);
            if (globalObject.has(propertyKey)) {
                suspendedValues.put(key, globalObject.get(propertyKey));
            } else {
                suspendedAbsentKeys.add(key);
            }
        }

        Iterator<EvalOverlayFrame> descendingIterator = evalOverlayFrames.descendingIterator();
        while (descendingIterator.hasNext()) {
            EvalOverlayFrame evalOverlayFrame = descendingIterator.next();
            for (var entry : evalOverlayFrame.savedGlobals().entrySet()) {
                globalObject.set(this, PropertyKey.fromString(entry.getKey()), entry.getValue());
            }
            for (String absentKey : evalOverlayFrame.absentKeys()) {
                globalObject.delete(this, PropertyKey.fromString(absentKey));
            }
        }
        return new EvalOverlaySnapshot(suspendedValues, suspendedAbsentKeys);
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
        transferPrototype(jsError, jsError.getErrorName());
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
        JSValue constructor = jsGlobalObject.getGlobalObject().get(constructorName);
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
     * Transfer prototype using Get(constructor, "prototype") with full JS semantics.
     * This is used by constructor paths that must observe accessors and propagate abrupt completions.
     */
    public boolean transferPrototypeFromConstructor(JSObject receiver, JSObject constructor) {
        JSObject prototype = getPrototypeFromConstructor(constructor, JSObject.NAME);
        if (prototype == null) {
            return false;
        }
        receiver.setPrototype(prototype);
        return true;
    }

    private void validateModuleScriptEarlyErrors(String sourceCode) {
        String sourceWithoutComments = sourceCode
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
        Matcher varMatcher = Pattern.compile("\\bvar\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(sourceWithoutComments);
        Set<String> varNames = new HashSet<>();
        while (varMatcher.find()) {
            varNames.add(varMatcher.group(1));
        }
        Matcher functionMatcher = Pattern.compile("\\bfunction\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(")
                .matcher(sourceWithoutComments);
        while (functionMatcher.find()) {
            String functionName = functionMatcher.group(1);
            if (varNames.contains(functionName)) {
                throw new JSException(throwSyntaxError("Identifier '" + functionName + "' has already been declared"));
            }
        }
    }

    private enum DynamicImportModuleStatus {
        LOADING,
        EVALUATED
    }

    private static final class DynamicImportModuleRecord {
        private final Set<String> ambiguousExportNames;
        private final Set<String> explicitExportNames;
        private final Map<String, String> exportOrigins;
        private final List<LocalExportBinding> localExportBindings;
        private final JSImportNamespaceObject namespace;
        private final List<ReExportBinding> reExportBindings;
        private final String resolvedSpecifier;
        private JSObject deferredNamespace;
        private String exportBindingName;
        private boolean hasExportSyntax;
        private String rawSource;
        private DynamicImportModuleStatus status;
        private String transformedSource;

        private DynamicImportModuleRecord(String resolvedSpecifier, JSImportNamespaceObject namespace) {
            this.ambiguousExportNames = new HashSet<>();
            this.explicitExportNames = new HashSet<>();
            this.exportOrigins = new HashMap<>();
            this.namespace = namespace;
            this.localExportBindings = new ArrayList<>();
            this.reExportBindings = new ArrayList<>();
            this.resolvedSpecifier = resolvedSpecifier;
            this.status = DynamicImportModuleStatus.LOADING;
            this.deferredNamespace = null;
            this.exportBindingName = null;
            this.hasExportSyntax = false;
            this.rawSource = "";
            this.transformedSource = "";
        }

        private Set<String> ambiguousExportNames() {
            return ambiguousExportNames;
        }

        private JSObject deferredNamespace() {
            return deferredNamespace;
        }

        private Set<String> explicitExportNames() {
            return explicitExportNames;
        }

        private String exportBindingName() {
            return exportBindingName;
        }

        private Map<String, String> exportOrigins() {
            return exportOrigins;
        }

        private boolean hasExportSyntax() {
            return hasExportSyntax;
        }

        private List<LocalExportBinding> localExportBindings() {
            return localExportBindings;
        }

        private JSImportNamespaceObject namespace() {
            return namespace;
        }

        private String rawSource() {
            return rawSource;
        }

        private List<ReExportBinding> reExportBindings() {
            return reExportBindings;
        }

        private String resolvedSpecifier() {
            return resolvedSpecifier;
        }

        private void setDeferredNamespace(JSObject deferredNamespace) {
            this.deferredNamespace = deferredNamespace;
        }

        private void setExportBindingName(String exportBindingName) {
            this.exportBindingName = exportBindingName;
        }

        private void setHasExportSyntax(boolean hasExportSyntax) {
            this.hasExportSyntax = hasExportSyntax;
        }

        private void setRawSource(String rawSource) {
            this.rawSource = rawSource;
        }

        private void setStatus(DynamicImportModuleStatus status) {
            this.status = status;
        }

        private void setTransformedSource(String transformedSource) {
            this.transformedSource = transformedSource;
        }

        private DynamicImportModuleStatus status() {
            return status;
        }

        private String transformedSource() {
            return transformedSource;
        }
    }

    private record EvalOverlayFrame(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
    }

    public record EvalOverlaySnapshot(Map<String, JSValue> values, Set<String> absentKeys) {
    }

    private record LocalExportBinding(String localName, String exportedName) {
    }

    private record ReExportBinding(
            String sourceSpecifier,
            String importedName,
            String exportedName,
            boolean starExport) {
    }

    private final class JSDeferredModuleNamespace extends JSObject {
        private final JSContext context;
        private final DynamicImportModuleRecord moduleRecord;

        private JSDeferredModuleNamespace(
                JSContext context,
                DynamicImportModuleRecord moduleRecord) {
            super();
            this.context = context;
            this.moduleRecord = moduleRecord;
            setPrototype(null);
        }

        private boolean canExportThenWithoutEvaluation() {
            if (moduleRecord.explicitExportNames().contains("then")) {
                return true;
            }
            for (ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
                if (reExportBinding.starExport()) {
                    return true;
                }
                if ("then".equals(reExportBinding.exportedName())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean defineProperty(JSContext context, PropertyKey key, PropertyDescriptor descriptor) {
            return ensureEvaluated().defineProperty(context, key, descriptor);
        }

        @Override
        public boolean delete(JSContext context, PropertyKey key) {
            return ensureEvaluated().delete(context, key);
        }

        private JSImportNamespaceObject ensureEvaluated() {
            if (moduleRecord.status() != DynamicImportModuleStatus.EVALUATED) {
                evaluateDynamicImportModule(moduleRecord);
                resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(DynamicImportModuleStatus.EVALUATED);
            }
            return moduleRecord.namespace();
        }

        @Override
        public JSValue get(JSContext context, PropertyKey key) {
            if (shouldBypassThenLookup(key)) {
                return JSUndefined.INSTANCE;
            }
            return ensureEvaluated().get(context != null ? context : this.context, key);
        }

        @Override
        public JSValue get(PropertyKey key) {
            if (shouldBypassThenLookup(key)) {
                return JSUndefined.INSTANCE;
            }
            return ensureEvaluated().get(context, key);
        }

        @Override
        public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
            if (shouldBypassThenLookup(key)) {
                return null;
            }
            return ensureEvaluated().getOwnPropertyDescriptor(key);
        }

        @Override
        public List<PropertyKey> getOwnPropertyKeys() {
            return ensureEvaluated().getOwnPropertyKeys();
        }

        @Override
        public boolean has(PropertyKey key) {
            if (shouldBypassThenLookup(key)) {
                return false;
            }
            return ensureEvaluated().has(key);
        }

        private boolean shouldBypassThenLookup(PropertyKey key) {
            if (moduleRecord.status() == DynamicImportModuleStatus.EVALUATED) {
                return false;
            }
            if (!PropertyKey.THEN.equals(key)) {
                return false;
            }
            return !canExportThenWithoutEvaluation();
        }
    }

}
