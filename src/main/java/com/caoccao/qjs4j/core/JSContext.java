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
    private static final JSValue GLOBAL_LEXICAL_UNINITIALIZED = new JSObject();
    private static final Pattern MODULE_BINDING_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+([^;]*?)\\s+from\\s+(['\"])([^'\"\\r\\n]+)\\2(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_NAMESPACE_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+(?:(defer)\\s+)?\\*\\s*as\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+from\\s+(['\"])([^'\"\\r\\n]+)\\3(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_SIDE_EFFECT_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(['\"])([^'\"\\r\\n]+)\\1(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_STATIC_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+(?:defer\\s+)?(?:[^;]*?\\s+from\\s+)?['\"]([^'\"\\r\\n]+)['\"](?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_WITH_CLAUSE_PATTERN =
            Pattern.compile("with\\s*\\{([^}]*)\\}");
    private static final Pattern MODULE_TOP_LEVEL_AWAIT_PATTERN =
            Pattern.compile("(?m)^\\s*await\\b");
    // Call stack management
    private final Deque<JSStackFrame> callStack;
    private final Map<String, JSDynamicImportModule> dynamicImportModuleCache;
    // Stack trace capture
    private final List<StackTraceElement> errorStackTrace;
    private final Deque<EvalOverlayFrame> evalOverlayFrames;
    // Global declaration tracking for cross-script collision detection
    // Following QuickJS global_var_obj pattern (GlobalDeclarationInstantiation)
    private final Set<String> globalConstDeclarations;
    private final Set<String> globalLexDeclarations;
    private final Map<String, JSValue> globalLexicalBindings;
    private final Set<String> globalVarDeclarations;
    private final Map<String, JSObject> importMetaCache;
    // Shared iterator prototypes by toStringTag (e.g., "Array Iterator" → %ArrayIteratorPrototype%)
    private final Map<String, JSObject> iteratorPrototypes;
    private final JSGlobalObject jsGlobalObject;
    // Microtask queue for promise resolution and async operations
    private final JSMicrotaskQueue microtaskQueue;
    private final Map<String, JSModule> moduleCache;
    private final JSRuntime runtime;
    private final VirtualMachine virtualMachine;
    private boolean activeGlobalFunctionBindingConfigurable;
    private Set<String> activeGlobalFunctionBindingInitializations;
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
    private boolean suppressEvalMicrotaskProcessing;
    // The %ThrowTypeError% intrinsic (shared across Function.prototype and strict arguments)
    private JSNativeFunction throwTypeErrorIntrinsic;
    private boolean waitable;

    /**
     * Create a new execution context.
     */
    public JSContext(JSRuntime runtime) {
        this.callStack = new ArrayDeque<>();
        this.activeGlobalFunctionBindingConfigurable = false;
        this.activeGlobalFunctionBindingInitializations = null;
        this.errorStackTrace = new ArrayList<>();
        this.globalConstDeclarations = new HashSet<>();
        this.globalLexDeclarations = new HashSet<>();
        this.globalLexicalBindings = new HashMap<>();
        this.globalVarDeclarations = new HashSet<>();
        this.importMetaCache = new HashMap<>();
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
            List<JSDynamicImportModule.LocalExportBinding> localExportBindings,
            Set<String> importedBindingNames) {
        transformedSourceBuilder.append("const __qjs4jModuleExports = ")
                .append(exportBindingName)
                .append(";\n");
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            String escapedName = escapeJavaScriptString(localExportBinding.exportedName());
            if (importedBindingNames.contains(localExportBinding.localName())) {
                // Re-exported import bindings use direct value assignment since
                // the import overlay globals are temporary and will be cleaned up.
                transformedSourceBuilder.append("Object.defineProperty(__qjs4jModuleExports, \"")
                        .append(escapedName)
                        .append("\", { value: ")
                        .append(localExportBinding.localName())
                        .append(", writable: true, enumerable: true, configurable: false });\n");
            } else {
                // Locally declared exports use getters for live bindings.
                transformedSourceBuilder.append("Object.defineProperty(__qjs4jModuleExports, \"")
                        .append(escapedName)
                        .append("\", { enumerable: true, configurable: false, get() { return ")
                        .append(localExportBinding.localName())
                        .append("; } });\n");
            }
        }
    }

    private void applyImportClauseBindings(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            JSObject namespaceObject,
            String importClause) {
        String clause = importClause.trim();
        if (clause.isEmpty()) {
            return;
        }
        if (clause.startsWith("{")) {
            bindNamedImports(globalObject, savedGlobals, absentKeys, namespaceObject, clause);
            return;
        }

        int commaIndex = clause.indexOf(',');
        if (commaIndex < 0) {
            JSValue defaultValue = namespaceObject.get(this, PropertyKey.fromString("default"));
            bindImportOverlayValue(globalObject, savedGlobals, absentKeys, clause, defaultValue);
            return;
        }

        String defaultBinding = clause.substring(0, commaIndex).trim();
        if (!defaultBinding.isEmpty()) {
            JSValue defaultValue = namespaceObject.get(this, PropertyKey.fromString("default"));
            bindImportOverlayValue(globalObject, savedGlobals, absentKeys, defaultBinding, defaultValue);
        }

        String remainder = clause.substring(commaIndex + 1).trim();
        if (remainder.startsWith("*")) {
            String namespaceBinding = remainder.replaceFirst("^\\*\\s*as\\s+", "").trim();
            if (!namespaceBinding.isEmpty()) {
                bindImportOverlayValue(globalObject, savedGlobals, absentKeys, namespaceBinding, namespaceObject);
            }
        } else if (remainder.startsWith("{")) {
            bindNamedImports(globalObject, savedGlobals, absentKeys, namespaceObject, remainder);
        }
    }

    private void bindImportOverlayValue(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            String localName,
            JSValue value) {
        String bindingName = localName.trim();
        if (bindingName.isEmpty()) {
            return;
        }
        PropertyKey key = PropertyKey.fromString(bindingName);
        if (globalObject.has(key)) {
            if (!savedGlobals.containsKey(bindingName)) {
                savedGlobals.put(bindingName, globalObject.get(key));
            }
        } else {
            absentKeys.add(bindingName);
        }
        globalObject.set(this, key, value != null ? value : JSUndefined.INSTANCE);
    }

    private void bindNamedImports(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            JSObject namespaceObject,
            String namedClause) {
        String clause = namedClause.trim();
        if (!clause.startsWith("{") || !clause.endsWith("}")) {
            return;
        }
        String specifiersText = clause.substring(1, clause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : specifiersText.split(",")) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            String localName;
            String[] aliasParts = specifier.split("\\s+as\\s+");
            if (aliasParts.length == 2) {
                importedName = aliasParts[0].trim();
                localName = aliasParts[1].trim();
            } else {
                importedName = specifier;
                localName = specifier;
            }
            JSValue importedValue = namespaceObject.get(this, PropertyKey.fromString(importedName));
            bindImportOverlayValue(globalObject, savedGlobals, absentKeys, localName, importedValue);
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
        importMetaCache.clear();
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

    public boolean consumeGlobalFunctionBindingInitialization(String name) {
        return activeGlobalFunctionBindingInitializations != null
                && activeGlobalFunctionBindingInitializations.remove(name);
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
        String cacheKey = filename != null ? filename : "";
        JSObject importMetaObject = importMetaCache.get(cacheKey);
        if (importMetaObject == null) {
            importMetaObject = new JSObject();
            importMetaObject.setPrototype(null);
            if (filename != null && !filename.isEmpty() && !filename.startsWith("<")) {
                importMetaObject.set(PropertyKey.fromString("url"), new JSString(filename));
            }
            importMetaCache.put(cacheKey, importMetaObject);
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

    /**
     * Check if an import clause contains named bindings other than 'default'.
     * E.g., {@code {name}} returns true, {@code {default as x}} returns false.
     */
    private boolean hasNonDefaultNamedBindings(String importClause) {
        int braceStart = importClause.indexOf('{');
        if (braceStart < 0) {
            return false;
        }
        int braceEnd = importClause.indexOf('}', braceStart);
        if (braceEnd < 0) {
            return false;
        }
        String body = importClause.substring(braceStart + 1, braceEnd);
        for (String entry : body.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Get the imported name (before 'as')
            String[] parts = trimmed.split("\\s+as\\s+");
            String importedName = parts[0].trim();
            if (!"default".equals(importedName)) {
                return true;
            }
        }
        return false;
    }

    private void collectImportBindingNames(String importLine, Set<String> bindingNames) {
        // Extract local binding names from an import declaration line.
        // Handles: import X from '...', import * as X from '...', import defer * as X from '...',
        //          import { a, b as c } from '...'
        Matcher namespaceMatcher = MODULE_NAMESPACE_IMPORT_PATTERN.matcher(importLine);
        if (namespaceMatcher.find()) {
            bindingNames.add(namespaceMatcher.group(2));
            return;
        }
        Matcher bindingMatcher = MODULE_BINDING_IMPORT_PATTERN.matcher(importLine);
        if (bindingMatcher.find()) {
            String clause = bindingMatcher.group(1).trim();
            if (clause.startsWith("*") || clause.startsWith("defer *")) {
                return;
            }
            // Named imports: { a, b as c }
            if (clause.startsWith("{")) {
                collectNamedImportBindings(clause, bindingNames);
                return;
            }
            // Default import, possibly followed by named or namespace
            int commaIndex = clause.indexOf(',');
            if (commaIndex < 0) {
                bindingNames.add(clause.trim());
                return;
            }
            String defaultName = clause.substring(0, commaIndex).trim();
            if (!defaultName.isEmpty()) {
                bindingNames.add(defaultName);
            }
            String remainder = clause.substring(commaIndex + 1).trim();
            if (remainder.startsWith("{")) {
                collectNamedImportBindings(remainder, bindingNames);
            } else if (remainder.startsWith("*")) {
                String namespaceBinding = remainder.replaceFirst("^\\*\\s*as\\s+", "").trim();
                if (!namespaceBinding.isEmpty()) {
                    bindingNames.add(namespaceBinding);
                }
            }
        }
    }

    private void collectNamedImportBindings(String namedClause, Set<String> bindingNames) {
        String clause = namedClause.trim();
        if (!clause.startsWith("{") || !clause.endsWith("}")) {
            return;
        }
        String body = clause.substring(1, clause.length() - 1);
        for (String entry : body.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+as\\s+");
            String localName = parts.length > 1 ? parts[1].trim() : parts[0].trim();
            bindingNames.add(localName);
        }
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
            JSDynamicImportModule moduleRecord,
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

        JSDynamicImportModule dynamicImportEvalModuleRecord = null;
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
                        new JSDynamicImportModule(resolvedModuleSpecifier, createModuleNamespaceObject());
                dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.LOADING);
                dynamicImportEvalModuleRecord.setRawSource(code);
                parseDynamicImportModuleSource(dynamicImportEvalModuleRecord);
                dynamicImportModuleCache.put(resolvedModuleSpecifier, dynamicImportEvalModuleRecord);
            } else if (dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.EVALUATED) {
                skipEvaluatedDynamicImportModule = true;
            }
        }
        boolean evaluatingRawDynamicImportModule =
                dynamicImportEvalModuleRecord != null
                        && Objects.equals(dynamicImportEvalModuleRecord.rawSource(), code);

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

            if (evaluatingRawDynamicImportModule && dynamicImportEvalModuleRecord.hasExportSyntax()) {
                evaluateDynamicImportModule(dynamicImportEvalModuleRecord);
                resolveDynamicImportReExports(dynamicImportEvalModuleRecord, new HashSet<>());
                dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                processMicrotasks();
                return JSUndefined.INSTANCE;
            }

            // Phase 1-3: Lexer → Parser → Compiler (compile to bytecode)
            JSBytecodeFunction func;
            Compiler.CompileResult compileResult = compiler.compile(isModule);
            func = compileResult.function();
            Set<String> globalScriptFunctionNames = null;
            if (!isModule && !isDirectEval && !skipGlobalDeclarationTracking) {
                // Top-level script: check GlobalDeclarationInstantiation per ES2024 16.1.7
                func = compileResult.function();

                // Collect new declarations from this script
                Set<String> newConstDecls = new HashSet<>();
                Set<String> newVarDecls = new HashSet<>();
                Set<String> newLexDecls = new HashSet<>();
                globalScriptFunctionNames = new LinkedHashSet<>();
                AstUtils.collectGlobalDeclarations(
                        compileResult.ast(),
                        newVarDecls,
                        newLexDecls,
                        newConstDecls,
                        globalScriptFunctionNames);

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

                // Check CreateGlobalFunctionBinding preconditions before execution.
                for (String functionName : globalScriptFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    PropertyDescriptor desc = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (desc == null) {
                        if (!jsGlobalObject.getGlobalObject().isExtensible()) {
                            throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
                        }
                        continue;
                    }
                    if (!desc.isConfigurable()) {
                        if (desc.isAccessorDescriptor()
                                || !(desc.isWritable() && desc.isEnumerable())) {
                            throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
                        }
                    }
                }

                // Register new declarations for future collision checks
                globalConstDeclarations.addAll(newConstDecls);
                globalLexDeclarations.addAll(newLexDecls);
                globalVarDeclarations.addAll(newVarDecls);
                for (String lexicalName : newLexDecls) {
                    globalLexicalBindings.putIfAbsent(lexicalName, GLOBAL_LEXICAL_UNINITIALIZED);
                }

                // CreateGlobalVarDeclaration: define var bindings as non-configurable
                // properties on the global object (per ES2024 9.1.1.4.17 / QuickJS
                // js_closure_define_global_var with is_direct_or_indirect_eval=FALSE).
                // This must happen BEFORE execution so bindings exist at script start.
                for (String name : newVarDecls) {
                    if (globalScriptFunctionNames.contains(name)) {
                        continue;
                    }
                    PropertyKey key = PropertyKey.fromString(name);
                    PropertyDescriptor existing = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (existing == null && !jsGlobalObject.getGlobalObject().isExtensible()) {
                        throw new JSException(throwTypeError("cannot define variable '" + name + "'"));
                    }
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
            Set<String> globalEvalFunctionNames = null;
            if (!isModule && isDirectEval) {
                Set<String> evalVarDeclarations = new HashSet<>();
                Set<String> evalLexDeclarations = new HashSet<>();
                globalEvalFunctionNames = new LinkedHashSet<>();
                AstUtils.collectGlobalDeclarations(
                        compileResult.ast(),
                        evalVarDeclarations,
                        evalLexDeclarations,
                        null,
                        globalEvalFunctionNames);
                for (String functionName : globalEvalFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    PropertyDescriptor desc = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (desc != null && !desc.isConfigurable()) {
                        if (desc.isAccessorDescriptor()
                                || !(desc.isWritable() && desc.isEnumerable())) {
                            throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
                        }
                    }
                    if (desc == null && !jsGlobalObject.getGlobalObject().isExtensible()) {
                        throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
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
                if (directEvalCallerFrame != null && directEvalCallerFrame.getCaller() == null) {
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
            func.setImportMetaFilename(filename);

            if (isModule && !isDirectEval
                    && filename != null && !filename.isEmpty() && !filename.startsWith("<")) {
                // Register the current module as EVALUATING in the cache so that
                // self-imports (import defer * as self from './thisFile.js')
                // can detect re-entrancy and throw TypeError instead of recursing.
                String normalizedFilename = Paths.get(filename).normalize().toString();
                JSDynamicImportModule selfModuleRecord = null;
                JSDynamicImportModule.Status previousStatus = null;
                JSDynamicImportModule existingRecord = dynamicImportModuleCache.get(normalizedFilename);
                if (existingRecord != null) {
                    // Module already in cache (e.g. from loadJSDynamicImportModule).
                    // Mark it as EVALUATING so self-imports detect the re-entrancy.
                    previousStatus = existingRecord.status();
                    existingRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                    selfModuleRecord = existingRecord;
                } else {
                    selfModuleRecord = new JSDynamicImportModule(
                            normalizedFilename, createModuleNamespaceObject());
                    selfModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                    selfModuleRecord.setRawSource(code);
                    dynamicImportModuleCache.put(normalizedFilename, selfModuleRecord);
                }
                try {
                    moduleNamespaceImportOverlay = evaluateModuleImportsInOrder(code, filename);
                } finally {
                    if (existingRecord != null) {
                        // Restore the previous status if it's still EVALUATING
                        // (i.e. not changed by some other code path during import processing)
                        if (existingRecord.status() == JSDynamicImportModule.Status.EVALUATING
                                && previousStatus != null) {
                            existingRecord.setStatus(previousStatus);
                        }
                    } else if (dynamicImportModuleCache.get(normalizedFilename) == selfModuleRecord
                            && selfModuleRecord.status() == JSDynamicImportModule.Status.EVALUATING) {
                        dynamicImportModuleCache.remove(normalizedFilename);
                    }
                }
            }

            Set<String> globalFunctionBindingInitializations = null;
            boolean globalFunctionBindingsConfigurable = false;
            if (!isModule && !isDirectEval && globalScriptFunctionNames != null) {
                globalFunctionBindingInitializations = new HashSet<>(globalScriptFunctionNames);
            }
            if (!isModule
                    && isDirectEval
                    && directEvalCallerFrame != null
                    && directEvalCallerFrame.getCaller() == null
                    && globalEvalFunctionNames != null) {
                if (globalFunctionBindingInitializations == null) {
                    globalFunctionBindingInitializations = new HashSet<>();
                }
                globalFunctionBindingInitializations.addAll(globalEvalFunctionNames);
                globalFunctionBindingsConfigurable = true;
            }
            setGlobalFunctionBindingInitializations(
                    globalFunctionBindingInitializations,
                    globalFunctionBindingsConfigurable);

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
            JSValue result;
            try {
                result = virtualMachine.execute(func, evalThisArg, JSValue.NO_ARGS, evalNewTarget);
            } finally {
                setGlobalFunctionBindingInitializations(null, false);
            }

            if (!isModule
                    && isDirectEval
                    && directEvalCallerFrame != null
                    && directEvalCallerFrame.getCaller() == null) {
                if (globalEvalFunctionNames == null) {
                    globalEvalFunctionNames = new LinkedHashSet<>();
                }
                for (String functionName : globalEvalFunctionNames) {
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
            if (!isModule && !isDirectEval && globalScriptFunctionNames != null) {
                for (String functionName : globalScriptFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    if (!jsGlobalObject.getGlobalObject().has(key)) {
                        continue;
                    }
                    JSValue functionValue = jsGlobalObject.getGlobalObject().get(key);
                    PropertyDescriptor descriptor = new PropertyDescriptor();
                    descriptor.setValue(functionValue);
                    descriptor.setWritable(true);
                    descriptor.setEnumerable(true);
                    descriptor.setConfigurable(false);
                    jsGlobalObject.getGlobalObject().defineProperty(key, descriptor);
                }
            }

            // Check if there's a pending exception
            if (hasPendingException()) {
                JSValue exception = getPendingException();
                throw new JSException(exception);
            }

            // Process all pending microtasks before returning
            if (!suppressEvalMicrotaskProcessing) {
                processMicrotasks();
            }

            if (evaluatingRawDynamicImportModule
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
            }

            return result != null ? result : JSUndefined.INSTANCE;
        } catch (JSException e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            throw e;
        } catch (JSCompilerException e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            JSValue error = throwError("SyntaxError", e.getMessage());
            throw new JSException(error);
        } catch (JSVirtualMachineException e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
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
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            JSValue error = throwError(e.getErrorType().name(), e.getMessage());
            throw new JSException(error);
        } catch (Exception e) {
            if (dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
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

    JSValue evaluateDynamicImportModule(JSDynamicImportModule moduleRecord) {
        if (!moduleRecord.hasExportSyntax()) {
            validateModuleScriptEarlyErrors(moduleRecord.rawSource());
            return eval(moduleRecord.transformedSource(), moduleRecord.resolvedSpecifier(), true);
        }
        String exportBindingName = moduleRecord.exportBindingName();
        JSObject globalObject = getGlobalObject();
        JSObject moduleNamespace = moduleRecord.namespace();
        globalObject.set(this, PropertyKey.fromString(exportBindingName), moduleNamespace);
        try {
            String transformedSource = moduleRecord.transformedSource();
            return eval(transformedSource, moduleRecord.resolvedSpecifier(), true);
        } finally {
            globalObject.delete(this, PropertyKey.fromString(exportBindingName));
        }
    }

    /**
     * Process all module imports in source order, handling side-effect, namespace, and binding
     * imports together. This ensures deferred modules' async dependencies are pre-evaluated
     * in the correct position relative to other imports.
     */
    private EvalOverlayFrame evaluateModuleImportsInOrder(String code, String filename) {
        JSObject globalObject = getGlobalObject();
        Map<String, JSValue> savedGlobals = new HashMap<>();
        Set<String> absentKeys = new HashSet<>();

        // Collect all import matches with their positions for ordered processing
        List<int[]> importPositions = new ArrayList<>();
        // type 0=side-effect, 1=namespace, 2=binding
        Matcher sideEffectMatcher = MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(code);
        while (sideEffectMatcher.find()) {
            importPositions.add(new int[]{sideEffectMatcher.start(), 0, importPositions.size()});
        }
        Matcher namespaceMatcher = MODULE_NAMESPACE_IMPORT_PATTERN.matcher(code);
        while (namespaceMatcher.find()) {
            importPositions.add(new int[]{namespaceMatcher.start(), 1, importPositions.size()});
        }
        Matcher bindingMatcher = MODULE_BINDING_IMPORT_PATTERN.matcher(code);
        while (bindingMatcher.find()) {
            importPositions.add(new int[]{bindingMatcher.start(), 2, importPositions.size()});
        }
        importPositions.sort((a, b) -> Integer.compare(a[0], b[0]));

        // Suppress microtask processing during import evaluation to prevent
        // nested eval() calls from prematurely draining the microtask queue.
        // This ensures async TLA module completions happen after ALL imports
        // are processed, producing correct evaluation order.
        boolean prevSuppress = suppressEvalMicrotaskProcessing;
        suppressEvalMicrotaskProcessing = true;
        try {
            // Re-match each import in source order
            for (int[] pos : importPositions) {
                int type = pos[1];
                int start = pos[0];

                if (type == 0) {
                    // Side-effect import
                    sideEffectMatcher.reset();
                    if (sideEffectMatcher.find(start) && sideEffectMatcher.start() == start) {
                        String specifier = sideEffectMatcher.group(2);
                        Map<String, String> importAttributes = extractImportAttributes(sideEffectMatcher.group(0));
                        loadDynamicImportModule(specifier, filename, importAttributes);
                    }
                } else if (type == 1) {
                    // Namespace import
                    namespaceMatcher.reset();
                    if (namespaceMatcher.find(start) && namespaceMatcher.start() == start) {
                        String deferKeyword = namespaceMatcher.group(1);
                        String localName = namespaceMatcher.group(2);
                        String specifier = namespaceMatcher.group(4);
                        Map<String, String> importAttributes = extractImportAttributes(namespaceMatcher.group(0));
                        JSObject namespaceObject;
                        if ("defer".equals(deferKeyword)) {
                            namespaceObject = loadDynamicImportModuleDeferred(specifier, filename, importAttributes);
                        } else {
                            namespaceObject = loadDynamicImportModule(specifier, filename, importAttributes);
                        }
                        bindImportOverlayValue(globalObject, savedGlobals, absentKeys, localName, namespaceObject);
                    }
                } else {
                    // Binding import
                    bindingMatcher.reset();
                    if (bindingMatcher.find(start) && bindingMatcher.start() == start) {
                        String importClause = bindingMatcher.group(1).trim();
                        if (importClause.startsWith("*") || importClause.startsWith("defer *")) {
                            continue;
                        }
                        String specifier = bindingMatcher.group(3);
                        Map<String, String> importAttributes = extractImportAttributes(bindingMatcher.group(0));
                        if (specifier.endsWith(".json")
                                && importAttributes != null
                                && "json".equals(importAttributes.get("type"))) {
                            if (hasNonDefaultNamedBindings(importClause)) {
                                throw new JSSyntaxErrorException(
                                        "JSON modules do not support named exports");
                            }
                        }
                        JSObject namespaceObject = loadDynamicImportModule(specifier, filename, importAttributes);
                        applyImportClauseBindings(
                                globalObject,
                                savedGlobals,
                                absentKeys,
                                namespaceObject,
                                importClause);
                    }
                }
            }
        } finally {
            suppressEvalMicrotaskProcessing = prevSuppress;
        }

        // Drain microtasks to settle any EVALUATING_ASYNC modules before
        // the module body runs. This ensures TLA deps complete in the right order.
        if (!suppressEvalMicrotaskProcessing) {
            processMicrotasks();
        }

        if (savedGlobals.isEmpty() && absentKeys.isEmpty()) {
            return null;
        }
        return new EvalOverlayFrame(savedGlobals, absentKeys);
    }

    private Map<String, String> extractImportAttributes(String importStatement) {
        Matcher withMatcher = MODULE_WITH_CLAUSE_PATTERN.matcher(importStatement);
        if (!withMatcher.find()) {
            return null;
        }
        String withBody = withMatcher.group(1).trim();
        if (withBody.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> attributes = new HashMap<>();
        String[] pairs = withBody.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();
            // Remove surrounding quotes from value
            if (value.length() >= 2
                    && ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            attributes.put(key, value);
        }
        return attributes;
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
        // Split on commas at the top level only (not inside parens, brackets, braces, or strings)
        List<String> declarators = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < declaratorsText.length(); i++) {
            char ch = declaratorsText.charAt(i);
            if (inString) {
                if (ch == stringChar && (i == 0 || declaratorsText.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                inString = true;
                stringChar = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                declarators.add(declaratorsText.substring(start, i));
                start = i + 1;
            }
        }
        declarators.add(declaratorsText.substring(start));
        List<String> declarationNames = new ArrayList<>(declarators.size());
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
                JSDynamicImportModule childRecord = dynamicImportModuleCache.get(resolvedChildSpecifier);
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
     * Implements ReadyForSyncExecution(_module_, _seen_) from the import-defer spec.
     * Returns true if the module and all its transitive dependencies can be evaluated synchronously.
     */
    boolean readyForSyncExecution(String resolvedSpecifier, Set<String> seen) {
        if (!seen.add(resolvedSpecifier)) {
            return true;
        }
        JSDynamicImportModule record = dynamicImportModuleCache.get(resolvedSpecifier);
        if (record != null) {
            if (record.status() == JSDynamicImportModule.Status.EVALUATED
                    || record.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                return true;
            }
            if (record.status() == JSDynamicImportModule.Status.EVALUATING
                    || record.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                return false;
            }
        }
        // For LOADING status or no record, check the source for TLA and dependencies
        String sourceCode = null;
        if (record != null && record.rawSource() != null) {
            sourceCode = record.rawSource();
        } else {
            try {
                sourceCode = Files.readString(Path.of(resolvedSpecifier));
            } catch (IOException ioException) {
                return true;
            }
        }
        if (MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(sourceCode).find()) {
            return false;
        }
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String childSpecifier = matcher.group(1);
            String resolvedChildSpecifier;
            try {
                resolvedChildSpecifier = resolveDynamicImportSpecifier(
                        childSpecifier, resolvedSpecifier, childSpecifier);
            } catch (JSException jsException) {
                continue;
            }
            if (!readyForSyncExecution(resolvedChildSpecifier, seen)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasEvaluatingAsyncDependency(JSDynamicImportModule moduleRecord) {
        String source = moduleRecord.rawSource();
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            String specifier = matcher.group(1);
            try {
                String resolved = resolveDynamicImportSpecifier(
                        specifier, moduleRecord.resolvedSpecifier(), specifier);
                JSDynamicImportModule depRecord = dynamicImportModuleCache.get(resolved);
                if (depRecord != null
                        && depRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                    return true;
                }
            } catch (JSException ignored) {
                // Skip unresolvable specifiers
            }
        }
        return false;
    }

    private void registerPendingDependent(JSDynamicImportModule moduleRecord) {
        String source = moduleRecord.rawSource();
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            String specifier = matcher.group(1);
            try {
                String resolved = resolveDynamicImportSpecifier(
                        specifier, moduleRecord.resolvedSpecifier(), specifier);
                JSDynamicImportModule depRecord = dynamicImportModuleCache.get(resolved);
                if (depRecord != null
                        && depRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                    depRecord.pendingDependents().add(moduleRecord);
                }
            } catch (JSException ignored) {
                // Skip unresolvable specifiers
            }
        }
    }

    private void registerAsyncModuleCompletion(
            JSDynamicImportModule moduleRecord,
            JSPromise asyncPromise,
            Set<String> importResolutionStack) {
        JSNativeFunction onFulfill = new JSNativeFunction("onFulfill", 0,
                (ctx, thisArg, args) -> {
                    resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    triggerPendingDependents(moduleRecord);
                    return JSUndefined.INSTANCE;
                });
        onFulfill.initializePrototypeChain(this);
        JSNativeFunction onReject = new JSNativeFunction("onReject", 1,
                (ctx, thisArg, args) -> {
                    JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    moduleRecord.setEvaluationError(error);
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                    triggerPendingDependents(moduleRecord);
                    return JSUndefined.INSTANCE;
                });
        onReject.initializePrototypeChain(this);
        asyncPromise.addReactions(
                new JSPromise.ReactionRecord(onFulfill, this, null, null),
                new JSPromise.ReactionRecord(onReject, this, null, null));
    }

    private void triggerPendingDependents(JSDynamicImportModule moduleRecord) {
        List<JSDynamicImportModule> dependents = new ArrayList<>(moduleRecord.pendingDependents());
        moduleRecord.pendingDependents().clear();
        for (JSDynamicImportModule dependent : dependents) {
            if (hasEvaluatingAsyncDependency(dependent)) {
                continue; // Still has other async deps pending
            }
            try {
                dependent.setStatus(JSDynamicImportModule.Status.EVALUATING);
                JSValue evalResult = evaluateDynamicImportModule(dependent);
                if (dependent.hasTLA() && evalResult instanceof JSPromise asyncPromise) {
                    dependent.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                    dependent.setAsyncEvaluationPromise(asyncPromise);
                    registerAsyncModuleCompletion(dependent, asyncPromise, new HashSet<>());
                } else {
                    resolveDynamicImportReExports(dependent, new HashSet<>());
                    dependent.namespace().finalizeNamespace();
                    dependent.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    triggerPendingDependents(dependent);
                }
            } catch (JSException jsException) {
                dependent.setEvaluationError(jsException.getErrorValue());
                dependent.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                triggerPendingDependents(dependent);
            }
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
            JSDynamicImportModule moduleRecord,
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

    public boolean hasEvalOverlayBinding(String name) {
        for (EvalOverlayFrame evalOverlayFrame : evalOverlayFrames) {
            if (evalOverlayFrame.savedGlobals().containsKey(name)
                    || evalOverlayFrame.absentKeys().contains(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasGlobalConstDeclaration(String name) {
        return globalConstDeclarations.contains(name);
    }

    public boolean hasGlobalLexDeclaration(String name) {
        return globalLexDeclarations.contains(name);
    }

    public boolean hasGlobalLexicalBinding(String name) {
        return globalLexicalBindings.containsKey(name);
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

    public boolean isActiveGlobalFunctionBindingConfigurable() {
        return activeGlobalFunctionBindingConfigurable;
    }

    private boolean isDynamicImportDefaultDeclarationClause(String defaultClause) {
        return defaultClause.startsWith("function")
                || defaultClause.startsWith("async function")
                || defaultClause.startsWith("class");
    }

    public boolean isGlobalLexicalBindingInitialized(String name) {
        JSValue value = globalLexicalBindings.get(name);
        return value != null && value != GLOBAL_LEXICAL_UNINITIALIZED;
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
        // Check if the module was pre-loaded (deferred) but not yet evaluated.
        JSDynamicImportModule preloaded = dynamicImportModuleCache.get(resolvedSpecifier);
        if (preloaded != null && preloaded.status() == JSDynamicImportModule.Status.LOADING
                && preloaded.deferredPreload()) {
            try {
                evaluateDynamicImportModule(preloaded);
                resolveDynamicImportReExports(preloaded, new HashSet<>());
                preloaded.namespace().finalizeNamespace();
                preloaded.setStatus(JSDynamicImportModule.Status.EVALUATED);
            } catch (JSException jsException) {
                preloaded.setEvaluationError(jsException.getErrorValue());
                preloaded.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                throw jsException;
            }
            return preloaded.namespace();
        }
        JSDynamicImportModule moduleRecord =
                loadJSDynamicImportModule(resolvedSpecifier, new HashSet<>(), importAttributes);
        return moduleRecord.namespace();
    }

    public JSObject loadDynamicImportModuleDeferred(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes) {
        return loadDynamicImportModuleDeferred(specifier, referrerFilename, importAttributes, null, null);
    }

    /**
     * Load a module in deferred mode. When importPromise and resolveState are provided
     * (dynamic import.defer() case), the method handles resolving the import promise
     * internally — chaining it onto TLA evaluation promises if needed.
     * Returns null when the import promise is handled internally.
     */
    public JSObject loadDynamicImportModuleDeferred(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        String resolvedSpecifier = resolveDynamicImportSpecifier(specifier, referrerFilename, specifier);
        JSDynamicImportModule moduleRecord = dynamicImportModuleCache.get(resolvedSpecifier);
        if (moduleRecord == null) {
            moduleRecord = new JSDynamicImportModule(resolvedSpecifier, createModuleNamespaceObject());
            moduleRecord.setStatus(JSDynamicImportModule.Status.LOADING);
            moduleRecord.setDeferredPreload(true);
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
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    return moduleRecord.namespace();
                }
                parseDynamicImportModuleSource(moduleRecord);
                // Eagerly validate syntax of deferred modules per spec.
                // SyntaxErrors are not deferred — they must be detected at linking time.
                try {
                    new Compiler(sourceCode, resolvedSpecifier).parse(true);
                } catch (JSSyntaxErrorException syntaxError) {
                    dynamicImportModuleCache.remove(resolvedSpecifier);
                    throw new JSException(throwSyntaxError(syntaxError.getMessage()));
                } catch (JSCompilerException compilerError) {
                    dynamicImportModuleCache.remove(resolvedSpecifier);
                    throw new JSException(throwSyntaxError(compilerError.getMessage()));
                }
            } catch (IOException ioException) {
                dynamicImportModuleCache.remove(resolvedSpecifier);
                throw new JSException(throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
            } catch (JSException jsException) {
                dynamicImportModuleCache.remove(resolvedSpecifier);
                throw jsException;
            }
        }

        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED
                || moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
            // Even for already-evaluated (or error) modules, return the deferred namespace wrapper.
            // Deferred namespaces are distinct objects from eager namespaces per spec.
            // For EVALUATED_ERROR, ensureEvaluated() will rethrow the cached error.
            if (moduleRecord.deferredNamespace() == null) {
                moduleRecord.setDeferredNamespace(new JSDeferredModuleNamespace(this, moduleRecord));
            }
            return moduleRecord.deferredNamespace();
        }

        LinkedHashSet<String> asyncDependencySpecifiers = new LinkedHashSet<>();
        gatherDeferredAsyncDependencySpecifiers(
                resolvedSpecifier,
                moduleRecord.rawSource(),
                new HashSet<>(),
                asyncDependencySpecifiers);
        List<JSPromise> tlaEvaluationPromises = new ArrayList<>();
        boolean prevSuppress = suppressEvalMicrotaskProcessing;
        suppressEvalMicrotaskProcessing = true;
        try {
            for (String asyncDependencySpecifier : asyncDependencySpecifiers) {
                if (asyncDependencySpecifier.equals(resolvedSpecifier)
                        && moduleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                    // Self-module with TLA: set EVALUATING before eval so nested
                    // deferred imports of this module see the correct state.
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                    JSValue evalResult = evaluateDynamicImportModule(moduleRecord);
                    if (moduleRecord.hasTLA() && evalResult instanceof JSPromise asyncPromise) {
                        moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                        moduleRecord.setAsyncEvaluationPromise(asyncPromise);
                        registerAsyncModuleCompletion(moduleRecord, asyncPromise, new HashSet<>());
                        tlaEvaluationPromises.add(asyncPromise);
                    } else {
                        resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                        moduleRecord.namespace().finalizeNamespace();
                        moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    }
                } else {
                    JSDynamicImportModule depRecord =
                            loadJSDynamicImportModule(asyncDependencySpecifier, new HashSet<>(), importAttributes);
                    if (depRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC
                            && depRecord.asyncEvaluationPromise() != null) {
                        tlaEvaluationPromises.add(depRecord.asyncEvaluationPromise());
                    }
                }
            }
        } finally {
            suppressEvalMicrotaskProcessing = prevSuppress;
        }

        if (moduleRecord.deferredNamespace() == null) {
            moduleRecord.setDeferredNamespace(new JSDeferredModuleNamespace(this, moduleRecord));
        }

        // When called from dynamic import.defer() (importPromise != null) and there are
        // pending TLA evaluation promises, chain the import promise resolution onto them
        // using a Promise.all-like counter. This avoids relying on processMicrotasks()
        // which is a no-op when called re-entrantly from within a microtask.
        if (importPromise != null && !tlaEvaluationPromises.isEmpty()) {
            JSObject deferredNs = moduleRecord.deferredNamespace();
            int[] remaining = {tlaEvaluationPromises.size()};
            for (JSPromise tlaPromise : tlaEvaluationPromises) {
                JSNativeFunction onFulfill = new JSNativeFunction("", 0,
                        (ctx, thisArg, args) -> {
                            remaining[0]--;
                            if (remaining[0] == 0 && !resolveState.alreadyResolved) {
                                resolveState.alreadyResolved = true;
                                importPromise.resolve(ctx, deferredNs);
                            }
                            return JSUndefined.INSTANCE;
                        });
                onFulfill.initializePrototypeChain(this);
                JSNativeFunction onReject = new JSNativeFunction("", 1,
                        (ctx, thisArg, args) -> {
                            if (!resolveState.alreadyResolved) {
                                resolveState.alreadyResolved = true;
                                JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                importPromise.reject(reason);
                            }
                            return JSUndefined.INSTANCE;
                        });
                onReject.initializePrototypeChain(this);
                tlaPromise.addReactions(
                        new JSPromise.ReactionRecord(onFulfill, this, null, null),
                        new JSPromise.ReactionRecord(onReject, this, null, null));
            }
            return null; // Import promise will be resolved via TLA promise chain
        }

        // Static import defer case: drain microtasks to complete EVALUATING_ASYNC modules.
        // Only drain when not called from evaluateModuleImportsInOrder
        // (which has its own drain after all imports are processed).
        if (!suppressEvalMicrotaskProcessing && !asyncDependencySpecifiers.isEmpty()) {
            processMicrotasks();
        }

        return moduleRecord.deferredNamespace();
    }

    private JSDynamicImportModule loadJSDynamicImportModule(
            String resolvedSpecifier,
            Set<String> importResolutionStack,
            Map<String, String> importAttributes) {
        JSDynamicImportModule cachedRecord = dynamicImportModuleCache.get(resolvedSpecifier);
        if (cachedRecord != null) {
            if (cachedRecord.status() == JSDynamicImportModule.Status.EVALUATED) {
                return cachedRecord;
            }
            if (cachedRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                throw new JSException(cachedRecord.evaluationError());
            }
            if (cachedRecord.status() == JSDynamicImportModule.Status.LOADING
                    || cachedRecord.status() == JSDynamicImportModule.Status.EVALUATING
                    || cachedRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                return cachedRecord;
            }
        }

        JSDynamicImportModule moduleRecord =
                new JSDynamicImportModule(resolvedSpecifier, createModuleNamespaceObject());
        moduleRecord.setStatus(JSDynamicImportModule.Status.LOADING);
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
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                return moduleRecord;
            }
            parseDynamicImportModuleSource(moduleRecord);
            if (suppressEvalMicrotaskProcessing && moduleRecord.hasTLA()
                    && hasEvaluatingAsyncDependency(moduleRecord)) {
                // This TLA module depends on an EVALUATING_ASYNC module.
                // Don't evaluate yet; register as a pending dependent.
                registerPendingDependent(moduleRecord);
                return moduleRecord;
            }
            if (moduleRecord.hasTLA()) {
                // Set EVALUATING before eval so nested deferred imports see correct state.
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                // Suppress microtasks during eval so we can register the completion
                // callback before the microtask drain.
                boolean prevSuppress = suppressEvalMicrotaskProcessing;
                suppressEvalMicrotaskProcessing = true;
                JSValue evalResult;
                try {
                    evalResult = evaluateDynamicImportModule(moduleRecord);
                } finally {
                    suppressEvalMicrotaskProcessing = prevSuppress;
                }
                if (evalResult instanceof JSPromise asyncPromise) {
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                    moduleRecord.setAsyncEvaluationPromise(asyncPromise);
                    registerAsyncModuleCompletion(moduleRecord, asyncPromise, importResolutionStack);
                    if (!suppressEvalMicrotaskProcessing) {
                        // Not in a suppressed context — drain microtasks now to
                        // let the async module complete before returning.
                        processMicrotasks();
                    }
                    return moduleRecord;
                }
                // TLA module but eval didn't return a promise (e.g., no actual await hit).
                // Fall through to normal completion.
            } else {
                evaluateDynamicImportModule(moduleRecord);
            }
            resolveDynamicImportReExports(moduleRecord, importResolutionStack);
            moduleRecord.namespace().finalizeNamespace();
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
            return moduleRecord;
        } catch (IOException ioException) {
            throw new JSException(throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
        } catch (JSException jsException) {
            // Keep the module in cache with EVALUATED_ERROR status so that subsequent
            // deferred imports can rethrow the same error object (per spec).
            moduleRecord.setEvaluationError(jsException.getErrorValue());
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
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
            JSDynamicImportModule moduleRecord,
            JSDynamicImportModule targetModuleRecord,
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
            List<JSDynamicImportModule.LocalExportBinding> localExportBindings,
            List<JSDynamicImportModule.ReExportBinding> reExportBindings) {
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
                localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(localName, exportedName));
            } else {
                reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, localName, exportedName, false));
            }
        }
    }

    private void parseDynamicImportModuleSource(JSDynamicImportModule moduleRecord) {
        String sourceCode = moduleRecord.rawSource();
        StringBuilder importPreambleBuilder = new StringBuilder();
        StringBuilder transformedSourceBuilder = new StringBuilder(sourceCode.length() + 128);
        List<JSDynamicImportModule.LocalExportBinding> localExportBindings = new ArrayList<>();
        List<JSDynamicImportModule.ReExportBinding> reExportBindings = new ArrayList<>();
        Set<String> importedBindingNames = new HashSet<>();
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
            // Extract import lines to be placed before the IIFE wrapper
            if (trimmedLine.startsWith("import ") || trimmedLine.startsWith("import\t")) {
                importPreambleBuilder.append(normalizedLine).append('\n');
                collectImportBindingNames(trimmedLine, importedBindingNames);
                continue;
            }
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
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, "default"));
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
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(defaultLocalName, "default"));
                }
                continue;
            }

            if (exportClause.startsWith("var ")
                    || exportClause.startsWith("let ")
                    || exportClause.startsWith("const ")) {
                transformedSourceBuilder.append(normalizedLine.replaceFirst("export\\s+", "")).append('\n');
                for (String declarationName : extractSimpleDeclarationNames(exportClause)) {
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, declarationName));
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
                localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, declarationName));
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
                    reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, "*namespace*", exportedName, false));
                    continue;
                }
                if (afterStarText.startsWith("from ")) {
                    String fromText = afterStarText.substring("from ".length()).trim();
                    while (fromText.endsWith(";")) {
                        fromText = fromText.substring(0, fromText.length() - 1).trim();
                    }
                    String sourceSpecifier = stripQuotedSpecifier(fromText);
                    reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, "*", "*", true));
                    continue;
                }
                throw new JSException(throwSyntaxError("Invalid export statement"));
            }

            throw new JSException(throwSyntaxError("Unexpected export syntax"));
        }

        moduleRecord.setHasExportSyntax(hasExportSyntax);
        moduleRecord.localExportBindings().addAll(localExportBindings);
        moduleRecord.reExportBindings().addAll(reExportBindings);
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            moduleRecord.explicitExportNames().add(localExportBinding.exportedName());
            moduleRecord.exportOrigins().put(localExportBinding.exportedName(), moduleRecord.resolvedSpecifier());
            moduleRecord.namespace().registerExportName(localExportBinding.exportedName());
        }

        boolean hasTLA = MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(transformedSourceBuilder).find();
        moduleRecord.setHasTLA(hasTLA);

        if (hasExportSyntax) {
            String exportBindingName = createModuleExportBindingName(moduleRecord.resolvedSpecifier());
            appendDynamicImportExportAssignments(
                    transformedSourceBuilder, exportBindingName,
                    localExportBindings, importedBindingNames);
            String transformedSource;
            if (hasTLA) {
                // For TLA export modules, capture export binding name and imported bindings
                // as IIFE parameters so they survive overlay cleanup after eval() returns.
                List<String> paramNames = new ArrayList<>();
                paramNames.add(exportBindingName);
                paramNames.addAll(importedBindingNames);
                String paramList = String.join(", ", paramNames);
                transformedSource = importPreambleBuilder
                        + "(async function(" + paramList + ") {\n"
                        + transformedSourceBuilder
                        + "})(" + paramList + ");\n";
            } else {
                transformedSource = importPreambleBuilder
                        + "(function () {\n"
                        + transformedSourceBuilder
                        + "})();\n";
            }
            moduleRecord.setTransformedSource(transformedSource);
            moduleRecord.setExportBindingName(exportBindingName);
        } else if (!importedBindingNames.isEmpty() || hasTLA) {
            // Wrap non-export modules in an IIFE to capture imported bindings in closure.
            String paramList = String.join(", ", importedBindingNames);
            String transformedSource = importPreambleBuilder
                    + (hasTLA ? "(async function(" : "(function(")
                    + paramList + ") {\n"
                    + transformedSourceBuilder
                    + "})(" + paramList + ");\n";
            moduleRecord.setTransformedSource(transformedSource);
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

    public JSValue readGlobalLexicalBinding(String name) {
        return globalLexicalBindings.get(name);
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

    void resolveDynamicImportReExports(
            JSDynamicImportModule moduleRecord,
            Set<String> importResolutionStack) {
        if (moduleRecord.reExportBindings().isEmpty()) {
            return;
        }

        if (!importResolutionStack.add(moduleRecord.resolvedSpecifier())) {
            throw new JSException(throwSyntaxError("Circular module dependency"));
        }
        try {
            Map<String, String> exportOrigins = moduleRecord.exportOrigins();
            for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
                String targetSpecifier = resolveDynamicImportSpecifier(
                        reExportBinding.sourceSpecifier(),
                        moduleRecord.resolvedSpecifier(),
                        reExportBinding.sourceSpecifier());
                JSDynamicImportModule targetModuleRecord =
                        loadJSDynamicImportModule(targetSpecifier, importResolutionStack, null);
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

    public void resumeEvalOverlays(JSGlobalObject.EvalOverlaySnapshot evalOverlaySnapshot) {
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

    public void setGlobalFunctionBindingInitializations(Set<String> functionNames, boolean configurable) {
        activeGlobalFunctionBindingConfigurable = configurable;
        activeGlobalFunctionBindingInitializations = functionNames;
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

    public JSGlobalObject.EvalOverlaySnapshot suspendEvalOverlays() {
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
        return new JSGlobalObject.EvalOverlaySnapshot(suspendedValues, suspendedAbsentKeys);
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

    public void writeGlobalLexicalBinding(String name, JSValue value) {
        globalLexicalBindings.put(name, value);
    }


    private record EvalOverlayFrame(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
    }



}
