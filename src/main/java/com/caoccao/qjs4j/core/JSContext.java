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

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.core.buffer.*;
import com.caoccao.qjs4j.core.collection.*;
import com.caoccao.qjs4j.core.date.JSDate;
import com.caoccao.qjs4j.core.disposable.JSDisposableStack;
import com.caoccao.qjs4j.core.error.*;
import com.caoccao.qjs4j.core.promise.IJSPromiseRejectCallback;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;
import com.caoccao.qjs4j.vm.VirtualMachine;

import java.util.*;

/**
 * Represents a JavaScript execution context. Based on QuickJS JSContext structure.
 * <p>
 * A context is an independent JavaScript execution environment with: - Its own global object and built-in objects - Its
 * own module cache - Its own call stack and exception state - Shared runtime resources (atoms, GC, job queue)
 * <p>
 * Multiple contexts can exist in a single runtime, each isolated from the others (separate globals, separate module
 * namespaces).
 */
public final class JSContext implements AutoCloseable {
    /**
     * Upper bound on the failures retained by {@link #recordMicrotaskFailure(Throwable)}, so a repeatedly failing
     * microtask cannot itself become a leak.
     */
    private static final int MAX_RECORDED_MICROTASK_FAILURES = 64;
    /**
     * Arrays whose {@code join} or {@code toLocaleString} is running, by identity: V8's cycle detection.
     */
    private final Set<JSObject> arrayJoinStack;
    // Call stack management
    private final Deque<JSStackFrame> callStack;
    private boolean closed;
    // Temporarily holds new.target during native constructor calls
    // so native constructors can check if called directly vs from subclass
    private JSValue constructorNewTarget;
    private JSValue currentThis;
    // Builds error values and their stack traces; see JSErrorReporter
    private final JSErrorReporter errorReporter;
    // The temporary global-object overlays a module's imports are installed as
    private final EvalOverlayManager evalOverlayManager;
    // Runs source: the eval pipeline; see EvalRunner
    private final EvalRunner evalRunner;
    /**
     * Whether the module body about to run was pulled in to satisfy an import.
     * <p>
     * See {@link #getImportedModuleBodyEvaluationCount()}.
     */
    private boolean evaluatingImportedModule;
    /**
     * How many module bodies threw instead of running to completion.
     * <p>
     * See {@link #getFailedModuleBodyEvaluationCount()}.
     */
    private int failedModuleBodyEvaluationCount;
    private final List<JSFinalizationRegistry> finalizationRegistries;
    // Global declaration tracking for cross-script collision detection
    // Following QuickJS global_var_obj pattern (GlobalDeclarationInstantiation)
    private final GlobalLexicalScope globalLexicalScope;
    // Installs a module's imports and exports where running code can see them
    private final ImportBindingInstaller importBindingInstaller;
    /**
     * How many of the module bodies that ran were pulled in to satisfy an import.
     * <p>
     * See {@link #getImportedModuleBodyEvaluationCount()}.
     */
    private int importedModuleBodyEvaluationCount;
    // Flag set by the VM's PUT_VAR handler before calling globalObject.set()
    // so that import overlay setters can distinguish bare variable assignment
    // (which should throw TypeError) from property-based writes (which should succeed).
    private boolean inBareVariableAssignment;
    private boolean inCatchHandler;
    private final JSGlobalObject jsGlobalObject;
    private int maxCallDepth;
    private IJSMicrotaskFailureCallback microtaskFailureCallback;
    // Failures that escaped a microtask, oldest first
    private final List<Throwable> microtaskFailures = new ArrayList<>();
    // Microtask queue for promise resolution and async operations
    private final JSMicrotaskQueue microtaskQueue;
    /**
     * How many module bodies have begun executing in this context.
     * <p>
     * See {@link #getModuleBodyEvaluationCount()}.
     */
    private int moduleBodyEvaluationCount;
    // Links a module graph before any of it is evaluated; see ModuleLinker
    private final ModuleLinker moduleLinker;
    // Loads, caches and orders the evaluation of modules; see ModuleLoader
    private final ModuleLoader moduleLoader;
    private JSValue nativeConstructorNewTarget;
    private JSEvalEnvironment pendingDirectEvalEnvironment;
    // Exception state
    private JSValue pendingException;
    // Promise rejection callback
    private IJSPromiseRejectCallback promiseRejectCallback;
    // The realm's intrinsic objects and prototype-resolution rules; see RealmIntrinsics
    private final RealmIntrinsics realmIntrinsics;
    // RegExp.input / .lastMatch / .lastParen / .leftContext / .rightContext / .$1-$9
    private final RegExpLegacyStatics regExpLegacyStatics;
    private final JSRuntime runtime;
    private int stackDepth;
    // Execution state
    private boolean strictMode;
    private boolean suppressEvalMicrotaskProcessing;
    private final UnicodePropertyResolver unicodePropertyResolver;
    // Allocates built-in objects with their prototypes attached; see JSValueFactory
    private final JSValueFactory valueFactory;
    private final VirtualMachine virtualMachine;
    private boolean waitable;

    /**
     * Create a new execution context.
     */
    JSContext(JSRuntime runtime) {
        this.callStack = new ArrayDeque<>();
        this.arrayJoinStack = Collections.newSetFromMap(new IdentityHashMap<>());
        this.errorReporter = new JSErrorReporter(this);
        this.evalOverlayManager = new EvalOverlayManager(this);
        this.globalLexicalScope = new GlobalLexicalScope();
        this.waitable = true;
        this.inCatchHandler = false;
        this.finalizationRegistries = new ArrayList<>();
        this.realmIntrinsics = new RealmIntrinsics(this);
        this.jsGlobalObject = new JSGlobalObject(this);
        // The runtime's configured call depth, so JSRuntimeOptions.setMaxCallDepth is a limit the
        // engine actually applies.
        this.maxCallDepth = runtime.getOptions().getMaxCallDepth();
        this.microtaskQueue = new JSMicrotaskQueue(this);
        // A local, not a field: the transformer is given to the three collaborators that use it and
        // the context itself never asks for it again. Keeping a reference would be realm state that
        // nothing reads.
        ModuleSourceTransformer moduleSourceTransformer = new ModuleSourceTransformer(this);
        this.evalRunner = new EvalRunner(this, moduleSourceTransformer);
        this.moduleLinker = new ModuleLinker(this, moduleSourceTransformer);
        this.importBindingInstaller = new ImportBindingInstaller(this, moduleSourceTransformer, moduleLinker);
        this.moduleLoader = new ModuleLoader(this, moduleSourceTransformer, moduleLinker);
        this.pendingException = null;
        this.runtime = runtime;
        this.unicodePropertyResolver = new UnicodePropertyResolver();
        this.inBareVariableAssignment = false;
        this.regExpLegacyStatics = new RegExpLegacyStatics();
        this.stackDepth = 0;
        this.strictMode = false;
        this.valueFactory = new JSValueFactory(this);
        this.virtualMachine = new VirtualMachine(this);
        this.nativeConstructorNewTarget = null;

        this.currentThis = jsGlobalObject.getGlobalObject();
        initializeGlobalObject();
    }

    /**
     * The live call stack, innermost frame first.
     * <p>
     * Package-private and not a copy: {@link JSErrorReporter} walks it once per captured trace, and
     * {@link #getCallStack()} is the public, defensive-copy view.
     *
     * @return the call stack itself
     */
    Deque<JSStackFrame> callStackFrames() {
        return callStack;
    }

    void captureErrorStackTrace() {
        errorReporter.captureErrorStackTrace();
    }

    void captureStackTrace(JSObject error) {
        errorReporter.captureStackTrace(error);
    }

    public boolean captureStackTrace(JSObject target, JSObject skipFunction) {
        return errorReporter.captureStackTrace(target, skipFunction);
    }

    /**
     * Clear the pending exception in both context and VM. This is needed when an async function catches an exception.
     */
    public void clearAllPendingExceptions() {
        clearPendingException();
        clearErrorStackTrace();
        virtualMachine.clearPendingException();
    }

    private void clearCallStack() {
        callStack.clear();
    }

    void clearErrorStackTrace() {
        errorReporter.clearErrorStackTrace();
    }

    /**
     * Discard the recorded microtask failures.
     */
    public void clearMicrotaskFailures() {
        microtaskFailures.clear();
    }

    /**
     * Clear the pending exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
    }

    /**
     * Reset the transient execution state an eval activation leaves behind.
     * <p>
     * The stack depth is re-derived from the call stack rather than decremented, so a frame the VM abandoned on an
     * abrupt exit cannot leave the counter drifting.
     */
    void clearTransientEvalState() {
        stackDepth = callStack.size();
        inCatchHandler = false;
        currentThis = jsGlobalObject.getGlobalObject();
    }

    /**
     * Close this context and release its realm.
     * <p>
     * The post-close contract is that this context owns nothing: every collection it holds is empty, every cached
     * intrinsic and host callback is dropped, and the global object is stripped of its properties and its prototype. An
     * embedder that keeps a reference to the context, to the global object, or to a value it read out of the realm
     * therefore retains that one object and not the realm graph behind it.
     * <p>
     * Clearing a selection of collections is not enough for that claim. The global object alone reaches every
     * intrinsic, every constructor and everything a script attached to {@code globalThis}, so leaving it populated left
     * the entire realm reachable through a closed context — as did the declaration tables, the cached prototypes and
     * the host callbacks.
     * <p>
     * Idempotent: a second call does nothing.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Clear all caches
        moduleLoader.clearModuleCache();
        clearCallStack();
        clearPendingException();
        clearErrorStackTrace();
        // State that close() used to leave behind: pending promise reactions were abandoned with
        // no callback, and the realm's binding tables, iterator prototypes, finalization
        // registries, eval overlays and import.meta cache all stayed reachable through the
        // context object.
        microtaskQueue.clear();
        globalLexicalScope.clear();
        finalizationRegistries.clear();
        evalOverlayManager.clear();
        microtaskFailures.clear();
        // Host callbacks: an embedder's listener can reach arbitrary application state.
        microtaskFailureCallback = null;
        promiseRejectCallback = null;
        // Cached intrinsics. Each one is a live handle on the realm.
        realmIntrinsics.release();
        // Transient execution values.
        constructorNewTarget = null;
        nativeConstructorNewTarget = null;
        currentThis = null;
        // RegExp legacy static state holds the last subject string, which can be arbitrarily large.
        regExpLegacyStatics.release();
        virtualMachine.reset();
        // Last: everything above may still need the realm while it runs.
        jsGlobalObject.getGlobalObject().releaseProperties();
        // Remove from runtime
        runtime.destroyContext(this);
    }

    public boolean consumeGlobalFunctionBindingInitialization(String name) {
        return globalLexicalScope.consumeFunctionBindingInitialization(name);
    }

    public boolean consumeScheduledClassFieldEvalCall() {
        return evalRunner.consumeScheduledClassFieldEvalCall();
    }

    public boolean consumeScheduledDirectEvalCall() {
        return evalRunner.consumeScheduledDirectEvalCall();
    }

    /**
     * The {@code import.meta} object for a module, created on first use and cached per file name.
     *
     * @param filename
     *            the module's file name
     * @return the module's {@code import.meta}
     */
    public JSObject createImportMetaObject(String filename) {
        return moduleLoader.createImportMetaObject(filename);
    }

    public JSAggregateError createJSAggregateError(String message) {
        return valueFactory.initializeError(new JSAggregateError(this, message));
    }

    /**
     * Create a new JSArray with proper prototype chain. Sets the array's prototype to Array.prototype from the global
     * object.
     *
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray() {
        return valueFactory.createJSArray();
    }

    /**
     * Create a new JSArray with specified values and proper prototype chain. Sets the array's prototype to
     * Array.prototype from the global object.
     *
     * @param values
     *            Initial values of the array
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray(JSValue... values) {
        return valueFactory.initializePrototype(new JSArray(this, values), JSArray.NAME);
    }

    /**
     * Create a new JSArray taking ownership of a freshly allocated values array. Internal fast path to avoid an extra
     * defensive copy in hot built-in paths.
     */
    public JSArray createJSArray(JSValue[] values, boolean takeOwnership) {
        return valueFactory.initializePrototype(new JSArray(this, values, takeOwnership), JSArray.NAME);
    }

    /**
     * Create a new JSArray with specified length and proper prototype chain. Sets the array's prototype to
     * Array.prototype from the global object.
     *
     * @param length
     *            Initial length of the array; must be in {@code [0, 2^32 - 1]}
     * @return A new JSArray instance with prototype set
     * @throws com.caoccao.qjs4j.exceptions.JSRangeErrorException
     *             when {@code length} is not a valid ECMAScript array length
     */
    public JSArray createJSArray(long length) {
        return valueFactory.createJSArray(length);
    }

    /**
     * Create a new JSArray with specified length, capacity, and proper prototype chain. Sets the array's prototype to
     * Array.prototype from the global object.
     *
     * @param length
     *            Initial length of the array; must be in {@code [0, 2^32 - 1]}
     * @param capacity
     *            Initial capacity of the array
     * @return A new JSArray instance with prototype set
     * @throws com.caoccao.qjs4j.exceptions.JSRangeErrorException
     *             when {@code length} is not a valid ECMAScript array length
     */
    public JSArray createJSArray(long length, int capacity) {
        return valueFactory.initializePrototype(new JSArray(this, length, capacity), JSArray.NAME);
    }

    /**
     * Create a new JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength
     *            The length in bytes
     * @return A new JSArrayBuffer instance with prototype set
     */
    public JSArrayBuffer createJSArrayBuffer(long byteLength) {
        return valueFactory.initializePrototype(new JSArrayBuffer(this, byteLength), JSArrayBuffer.NAME);
    }

    /**
     * Create a new resizable JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength
     *            The initial length in bytes
     * @param maxByteLength
     *            The maximum length in bytes, or -1 for non-resizable
     * @return A new JSArrayBuffer instance with prototype set
     */
    public JSArrayBuffer createJSArrayBuffer(long byteLength, long maxByteLength) {
        return valueFactory.initializePrototype(new JSArrayBuffer(this, byteLength, maxByteLength), JSArrayBuffer.NAME);
    }

    /**
     * ES2024 7.3.34 ArraySpeciesCreate(originalArray, length). Following QuickJS JS_ArraySpeciesCreate.
     *
     * @return the new array-like object, or null if an exception was set on context
     */
    public JSValue createJSArraySpecies(JSObject originalArray, long length) {
        return valueFactory.createJSArraySpecies(originalArray, length);
    }

    public JSBigInt64Array createJSBigInt64Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSBigInt64Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSBigInt64Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSBigInt64Array instance with prototype set
     */
    public JSBigInt64Array createJSBigInt64Array(long length) {
        return valueFactory.initializeTypedArray(new JSBigInt64Array(this, length));
    }

    public JSBigIntObject createJSBigIntObject(JSBigInt value) {
        return valueFactory.initializePrototype(new JSBigIntObject(this, value), JSBigIntObject.NAME);
    }

    public JSBigUint64Array createJSBigUint64Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSBigUint64Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSBigUint64Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSBigUint64Array instance with prototype set
     */
    public JSBigUint64Array createJSBigUint64Array(long length) {
        return valueFactory.initializeTypedArray(new JSBigUint64Array(this, length));
    }

    public JSBooleanObject createJSBooleanObject(JSBoolean value) {
        return valueFactory.initializePrototype(new JSBooleanObject(this, value), JSBooleanObject.NAME);
    }

    /**
     * Create a new JSDataView with proper prototype chain.
     *
     * @param buffer
     *            The ArrayBuffer to view
     * @param byteOffset
     *            The offset in bytes
     * @param byteLength
     *            The length in bytes
     * @return A new JSDataView instance with prototype set
     */
    public JSDataView createJSDataView(IJSArrayBuffer buffer, long byteOffset, long byteLength) {
        return valueFactory.initializePrototype(new JSDataView(this, buffer, byteOffset, byteLength), JSDataView.NAME);
    }

    /**
     * Create a new JSDate with proper prototype chain.
     *
     * @param timeValue
     *            The time value in milliseconds
     * @return A new JSDate instance with prototype set
     */
    public JSDate createJSDate(double timeValue) {
        return valueFactory.createJSDate(timeValue);
    }

    public JSDisposableStack createJSDisposableStack() {
        return valueFactory.initializePrototype(new JSDisposableStack(this), JSDisposableStack.NAME);
    }

    public JSError createJSError(String message) {
        return valueFactory.initializeError(new JSError(this, message));
    }

    public JSEvalError createJSEvalError(String message) {
        return valueFactory.initializeError(new JSEvalError(this, message));
    }

    public JSFloat16Array createJSFloat16Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSFloat16Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSFloat16Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSFloat16Array instance with prototype set
     */
    public JSFloat16Array createJSFloat16Array(long length) {
        return valueFactory.initializeTypedArray(new JSFloat16Array(this, length));
    }

    public JSFloat32Array createJSFloat32Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSFloat32Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSFloat32Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSFloat32Array instance with prototype set
     */
    public JSFloat32Array createJSFloat32Array(long length) {
        return valueFactory.initializeTypedArray(new JSFloat32Array(this, length));
    }

    public JSFloat64Array createJSFloat64Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSFloat64Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSFloat64Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSFloat64Array instance with prototype set
     */
    public JSFloat64Array createJSFloat64Array(long length) {
        return valueFactory.initializeTypedArray(new JSFloat64Array(this, length));
    }

    public JSInt16Array createJSInt16Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSInt16Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSInt16Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSInt16Array instance with prototype set
     */
    public JSInt16Array createJSInt16Array(long length) {
        return valueFactory.initializeTypedArray(new JSInt16Array(this, length));
    }

    public JSInt32Array createJSInt32Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSInt32Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSInt32Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSInt32Array instance with prototype set
     */
    public JSInt32Array createJSInt32Array(long length) {
        return valueFactory.initializeTypedArray(new JSInt32Array(this, length));
    }

    public JSInt8Array createJSInt8Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSInt8Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSInt8Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSInt8Array instance with prototype set
     */
    public JSInt8Array createJSInt8Array(long length) {
        return valueFactory.initializeTypedArray(new JSInt8Array(this, length));
    }

    /**
     * Create a new JSMap with proper prototype chain.
     *
     * @return A new JSMap instance with prototype set
     */
    public JSMap createJSMap() {
        return valueFactory.initializePrototype(new JSMap(this), JSMap.NAME);
    }

    public JSNumberObject createJSNumberObject(JSNumber value) {
        return valueFactory.initializePrototype(new JSNumberObject(this, value), JSNumberObject.NAME);
    }

    /**
     * Create a new JSObject with proper prototype chain. Sets the object's prototype to Object.prototype from the
     * global object.
     *
     * @return A new JSObject instance with prototype set
     */
    public JSObject createJSObject() {
        return valueFactory.createJSObject();
    }

    /**
     * Create a new JSPromise with proper prototype chain.
     *
     * @return A new JSPromise instance with prototype set
     */
    public JSPromise createJSPromise() {
        return valueFactory.createJSPromise();
    }

    public JSRangeError createJSRangeError(String message) {
        return valueFactory.initializeError(new JSRangeError(this, message));
    }

    public JSReferenceError createJSReferenceError(String message) {
        return valueFactory.initializeError(new JSReferenceError(this, message));
    }

    /**
     * Create a new JSRegExp with proper prototype chain.
     *
     * @param pattern
     *            The regular expression pattern
     * @param flags
     *            The regular expression flags
     * @return A new JSRegExp instance with prototype set
     */
    public JSRegExp createJSRegExp(String pattern, String flags) {
        return valueFactory.createJSRegExp(pattern, flags);
    }

    /**
     * Create a new JSSet with proper prototype chain.
     *
     * @return A new JSSet instance with prototype set
     */
    public JSSet createJSSet() {
        return valueFactory.initializePrototype(new JSSet(this), JSSet.NAME);
    }

    public JSStringObject createJSStringObject() {
        return valueFactory.initializePrototype(new JSStringObject(this), JSStringObject.NAME);
    }

    public JSStringObject createJSStringObject(JSString value) {
        return valueFactory.initializePrototype(new JSStringObject(this, value), JSStringObject.NAME);
    }

    public JSSuppressedError createJSSuppressedError(String message) {
        return valueFactory.initializeError(new JSSuppressedError(this, message));
    }

    public JSSymbolObject createJSSymbolObject(JSSymbol value) {
        return valueFactory.initializePrototype(new JSSymbolObject(this, value), JSSymbolObject.NAME);
    }

    public JSSyntaxError createJSSyntaxError(String message) {
        return valueFactory.initializeError(new JSSyntaxError(this, message));
    }

    public JSTypeError createJSTypeError(String message) {
        return valueFactory.initializeError(new JSTypeError(this, message));
    }

    public JSUint16Array createJSUint16Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSUint16Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSUint16Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSUint16Array instance with prototype set
     */
    public JSUint16Array createJSUint16Array(long length) {
        return valueFactory.initializeTypedArray(new JSUint16Array(this, length));
    }

    public JSUint32Array createJSUint32Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSUint32Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSUint32Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSUint32Array instance with prototype set
     */
    public JSUint32Array createJSUint32Array(long length) {
        return valueFactory.initializeTypedArray(new JSUint32Array(this, length));
    }

    public JSUint8Array createJSUint8Array(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSUint8Array(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSUint8Array with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSUint8Array instance with prototype set
     */
    public JSUint8Array createJSUint8Array(long length) {
        return valueFactory.initializeTypedArray(new JSUint8Array(this, length));
    }

    public JSUint8ClampedArray createJSUint8ClampedArray(IJSArrayBuffer buffer, long byteOffset, long length) {
        return valueFactory.initializeTypedArray(new JSUint8ClampedArray(this, buffer, byteOffset, length));
    }

    /**
     * Create a new JSUint8ClampedArray with proper prototype chain.
     *
     * @param length
     *            The length of the array
     * @return A new JSUint8ClampedArray instance with prototype set
     */
    public JSUint8ClampedArray createJSUint8ClampedArray(long length) {
        return valueFactory.initializeTypedArray(new JSUint8ClampedArray(this, length));
    }

    public JSURIError createJSURIError(String message) {
        return valueFactory.initializeError(new JSURIError(this, message));
    }

    /**
     * Create a new JSWeakMap with proper prototype chain.
     *
     * @return A new JSWeakMap instance with prototype set
     */
    public JSWeakMap createJSWeakMap() {
        return valueFactory.initializePrototype(new JSWeakMap(this), JSWeakMap.NAME);
    }

    /**
     * Create a new JSWeakSet with proper prototype chain.
     *
     * @return A new JSWeakSet instance with prototype set
     */
    public JSWeakSet createJSWeakSet() {
        return valueFactory.initializePrototype(new JSWeakSet(this), JSWeakSet.NAME);
    }

    /**
     * Where the code about to be evaluated by {@code eval} is evaluated, for its stack frames.
     *
     * @return {@code eval at caller (location)}, or null when no script frame is calling
     */
    String describeEvalOrigin() {
        return errorReporter.describeEvalOrigin();
    }

    /**
     * Enqueue a microtask to be executed.
     *
     * @param microtask
     *            The microtask to enqueue
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
     * In full implementation, this would: 1. Parse the source code 2. Compile to bytecode 3. Execute the bytecode 4.
     * Return the completion value
     *
     * @param code
     *            JavaScript source code
     * @return The completion value, or exception if eval throws
     */
    public JSValue eval(String code) {
        return evalRunner.evalOrThrow(evalRunner.eval(code, "<eval>", false, false, false, false, false, false));
    }

    /**
     * Evaluate code with source location information.
     *
     * @param code
     *            JavaScript source code
     * @param filename
     *            Source filename for stack traces
     * @param isModule
     *            Whether to evaluate as module (vs script)
     * @return The completion value
     */
    public JSValue eval(String code, String filename, boolean isModule) {
        return evalRunner.evalOrThrow(evalRunner.eval(code, filename, isModule, false, false, false, false, false));
    }

    /**
     * Eval js value.
     *
     * @param code
     *            the code
     * @param filename
     *            the filename
     * @param isModule
     *            the is module
     * @param isDirectEval
     *            the is direct eval
     * @return the js value
     */
    public JSValue eval(String code, String filename, boolean isModule, boolean isDirectEval) {
        return evalRunner
                .evalOrThrow(evalRunner.eval(code, filename, isModule, isDirectEval, false, false, false, false));
    }

    public JSValue evalDirect(String code, String filename, boolean inheritedStrictMode) {
        return evalRunner
                .evalOrThrow(evalRunner.eval(code, filename, false, true, false, false, inheritedStrictMode, true));
    }

    JSValue evalDirectInternal(String code, String filename, boolean inheritedStrictMode) {
        return evalRunner.eval(code, filename, false, true, false, false, inheritedStrictMode, true);
    }

    public JSValue evalIndirect(String code, String filename) {
        return evalRunner.evalOrThrow(evalRunner.eval(code, filename, false, true, false, false, false, false));
    }

    JSValue evalIndirectInternal(String code, String filename) {
        return evalRunner.eval(code, filename, false, true, false, false, false, false);
    }

    /**
     * The realm's eval-overlay stack.
     *
     * @return the eval overlay manager
     */
    EvalOverlayManager evalOverlayManager() {
        return evalOverlayManager;
    }

    /**
     * Evaluate one module's body, through its transformed source.
     * <p>
     * Kept on the context because {@link JSDeferredModuleNamespace} settles a deferred namespace through it; the work
     * itself belongs to {@link ModuleLoader}.
     *
     * @param moduleRecord
     *            the module to evaluate
     * @return the body's completion value, or its evaluation promise for a top-level-await module
     */
    JSValue evaluateDynamicImportModule(JSDynamicImportModule moduleRecord) {
        return moduleLoader.evaluateDynamicImportModule(moduleRecord);
    }

    public JSValue evalWithProgramLexicalsAsLocals(String code, String filename, boolean isModule) {
        return evalRunner.evalOrThrow(evalRunner.eval(code, filename, isModule, false, true, true, false, false));
    }

    /**
     * Exit strict mode.
     */
    public void exitStrictMode() {
        this.strictMode = false;
    }

    /**
     * The arrays whose {@code join} or {@code toLocaleString} is running in this realm. A join that meets an array
     * already on it renders that array as the empty string, as V8 does, instead of recursing until the stack overflows.
     *
     * @return the set, by identity
     */
    public Set<JSObject> getArrayJoinStack() {
        return arrayJoinStack;
    }

    public JSObject getAsyncFunctionConstructor() {
        return realmIntrinsics.getAsyncFunctionConstructor();
    }

    public JSObject getAsyncGeneratorFunctionPrototype() {
        return realmIntrinsics.getAsyncGeneratorFunctionPrototype();
    }

    public JSObject getAsyncGeneratorPrototype() {
        return realmIntrinsics.getAsyncGeneratorPrototype();
    }

    JSObject getCachedDatePrototype() {
        return realmIntrinsics.getCachedDatePrototype();
    }

    JSObject getCachedErrorConstructor() {
        return realmIntrinsics.getCachedErrorConstructor();
    }

    JSObject getCachedPromisePrototype() {
        return realmIntrinsics.getCachedPromisePrototype();
    }

    public JSObject getCachedRegExpConstructor() {
        return realmIntrinsics.getCachedRegExpConstructor();
    }

    public JSObject getCachedRegExpPrototype() {
        return realmIntrinsics.getCachedRegExpPrototype();
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

    public List<StackTraceElement> getErrorStackTrace() {
        return errorReporter.getErrorStackTrace();
    }

    public JSContext getFunctionRealm(JSObject constructor) {
        return realmIntrinsics.getFunctionRealm(constructor);
    }

    public JSObject getGeneratorFunctionPrototype() {
        return realmIntrinsics.getGeneratorFunctionPrototype();
    }

    /**
     * Get the names currently bound in the realm's global lexical scope.
     * <p>
     * Exposed so an embedder — and this project's own tests — can observe that {@link #close()} actually releases realm
     * state.
     *
     * @return a snapshot of the global lexical binding names
     */
    public Set<String> getGlobalLexicalBindingNames() {
        return globalLexicalScope.getBindingNames();
    }

    public JSObject getGlobalObject() {
        return jsGlobalObject.getGlobalObject();
    }

    /**
     * How many of the module bodies counted by {@link #getModuleBodyEvaluationCount()} belong to modules pulled in to
     * satisfy an import.
     * <p>
     * The difference between the two counts is what a host needs to distinguish a graph that failed to link from one
     * that linked and then threw. A module whose import cannot be resolved may still have evaluated the dependency it
     * imports <em>from</em> — so "something ran" does not mean the module the host asked for ran. When the two counts
     * differ, it did.
     *
     * @return the number of imported module bodies that have started executing
     */
    public int getImportedModuleBodyEvaluationCount() {
        return importedModuleBodyEvaluationCount;
    }

    public String getIntrinsicDefaultPrototypeName(JSFunction function) {
        return realmIntrinsics.getIntrinsicDefaultPrototypeName(function);
    }

    public JSObject getIteratorPrototype(String tag) {
        return realmIntrinsics.getIteratorPrototype(tag);
    }

    public Collection<JSObject> getIteratorPrototypes() {
        return realmIntrinsics.getIteratorPrototypes();
    }

    public JSGlobalObject getJSGlobalObject() {
        return jsGlobalObject;
    }

    /**
     * The maximum number of nested activations in this context.
     *
     * @return the maximum call depth
     */
    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    /**
     * The maximum call depth.
     *
     * @return the maximum number of nested activations
     * @deprecated use {@link #getMaxCallDepth()}
     */
    @Deprecated
    public int getMaxStackDepth() {
        return maxCallDepth;
    }

    /**
     * Get the callback that observes failures escaping a microtask.
     *
     * @return the callback, or {@code null} when none is installed
     */
    public IJSMicrotaskFailureCallback getMicrotaskFailureCallback() {
        return microtaskFailureCallback;
    }

    /**
     * Get the failures that escaped a microtask, oldest first.
     * <p>
     * The list is capped so a repeatedly failing microtask cannot itself become a leak; once it is full the oldest
     * entries are dropped.
     *
     * @return a snapshot of the recorded failures
     */
    public List<Throwable> getMicrotaskFailures() {
        return new ArrayList<>(microtaskFailures);
    }

    /**
     * Get the microtask queue for this context.
     */
    public JSMicrotaskQueue getMicrotaskQueue() {
        return microtaskQueue;
    }

    /**
     * How many module bodies have begun executing in this context.
     * <p>
     * ECMAScript loads, links and evaluates a module graph as three stages, and a conformance suite's negative module
     * tests declare which of the three they fail in. This engine still performs all three inside one {@code eval}, so a
     * host that has to tell them apart cannot do it by catching the error alone: a link failure and an evaluation
     * failure can carry the same constructor. The count moves exactly once per module body, at the point where that
     * module's imports have all been resolved and its own code is about to run, so a host can ask the one question the
     * stages differ on — whether anything was evaluated at all.
     * <p>
     * It counts every module body in the graph, dependencies included, and never decreases.
     *
     * @return the number of module bodies that have started executing
     */
    public int getModuleBodyEvaluationCount() {
        return moduleBodyEvaluationCount;
    }

    public JSValue getNativeConstructorNewTarget() {
        return nativeConstructorNewTarget;
    }

    public JSObject getObjectPrototype() {
        return realmIntrinsics.getObjectPrototype();
    }

    public JSValue getPendingException() {
        return pendingException;
    }

    public IJSPromiseRejectCallback getPromiseRejectCallback() {
        return promiseRejectCallback;
    }

    public JSObject getPrototypeFromConstructor(JSObject constructor, String intrinsicDefaultPrototypeName) {
        return realmIntrinsics.getPrototypeFromConstructor(constructor, intrinsicDefaultPrototypeName);
    }

    public String getRegExpLegacyCapture(int captureIndex) {
        return regExpLegacyStatics.getCapture(captureIndex);
    }

    public String getRegExpLegacyInput() {
        return regExpLegacyStatics.getInput();
    }

    public String getRegExpLegacyLastMatch() {
        return regExpLegacyStatics.getLastMatch();
    }

    public String getRegExpLegacyLastParen() {
        return regExpLegacyStatics.getLastParen();
    }

    public String getRegExpLegacyLeftContext() {
        return regExpLegacyStatics.getLeftContext();
    }

    public String getRegExpLegacyRightContext() {
        return regExpLegacyStatics.getRightContext();
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
     * The frames of the stack trace captured for an object, read without running any script.
     * <p>
     * {@code stack} is an accessor whose first read computes the {@code Name: message} line through
     * {@code Error.prototype.toString}, which can run script. A host reporting an uncaught error needs the frames
     * without that, and pairs them with a headline of its own.
     *
     * @param object
     *            an error, or an object {@code Error.captureStackTrace} was applied to
     * @return the frames, one {@code "    at ..."} line each, possibly empty; or null when none were captured
     */
    public String getStackFramesForDiagnostics(JSObject object) {
        JSStackTrace stackTrace = errorReporter.getStackTrace(object);
        return stackTrace != null ? stackTrace.getFrames() : null;
    }

    public JSNativeFunction getThrowTypeErrorIntrinsic() {
        return realmIntrinsics.getThrowTypeErrorIntrinsic();
    }

    public UnicodePropertyResolver getUnicodePropertyResolver() {
        return unicodePropertyResolver;
    }

    /**
     * Get the virtual machine for this context.
     */
    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    /**
     * The realm's global lexical environment.
     *
     * @return the global lexical scope
     */
    GlobalLexicalScope globalLexicalScope() {
        return globalLexicalScope;
    }

    public boolean hasEvalOverlayBinding(String name) {
        return evalOverlayManager.hasBinding(name);
    }

    /** Whether an eval overlay visible from {@code environment} binds {@code name}. */
    public boolean hasEvalOverlayBinding(String name, JSEvalEnvironment environment) {
        return evalOverlayManager.hasBinding(name, environment);
    }

    public boolean hasEvalOverlayFrames() {
        return evalOverlayManager.hasFrames();
    }

    public boolean hasGlobalConstDeclaration(String name) {
        return globalLexicalScope.hasConstDeclaration(name);
    }

    public boolean hasGlobalLexDeclaration(String name) {
        return globalLexicalScope.hasLexDeclaration(name);
    }

    public boolean hasGlobalLexicalBinding(String name) {
        return globalLexicalScope.hasLexicalBinding(name);
    }

    /**
     * Whether any module body failed, rather than the graph failing to link.
     * <p>
     * A module graph can fail in two quite different ways that carry the same error: an import that names an export
     * nothing provides fails while the graph is linked, and a dependency whose body throws fails while the graph is
     * evaluated. This engine pulls a dependency in and evaluates it in one step, so "a body ran" cannot tell them apart
     * on its own — a failed import may well have evaluated the module it was importing from. "A body failed" can,
     * because a link failure raises its error with every body it touched having run to completion.
     * <p>
     * Both shapes of failure count: a body that threw, and a top-level-await body that finished but whose evaluation
     * promise rejected. Only the body's own failure counts — a module marked as failed because something it imported
     * failed did not fail itself, and a graph that could not be linked marks records that way too.
     *
     * @return true when a module body failed
     */
    public boolean hasModuleBodyEvaluationFailed() {
        return failedModuleBodyEvaluationCount > 0;
    }

    /**
     * Check if there's a pending exception.
     */
    public boolean hasPendingException() {
        return pendingException != null;
    }

    /**
     * The realm's import-binding installer.
     * <p>
     * Package-private, and reached through the context rather than injected, because the linker is built before the
     * installer that it calls back into.
     *
     * @return the import-binding installer
     */
    ImportBindingInstaller importBindingInstaller() {
        return importBindingInstaller;
    }

    /**
     * Initialize the global object with built-in properties. Delegates to JSGlobalObject to set up all global functions
     * and properties.
     */
    private void initializeGlobalObject() {
        jsGlobalObject.initialize();
        // Cache the hot-path prototypes (e.g., iteratorResult, createJSObject) now that the
        // constructors they hang off exist.
        realmIntrinsics.cacheFromGlobalObject(jsGlobalObject.getGlobalObject());
    }

    public boolean isActiveGlobalFunctionBindingConfigurable() {
        return globalLexicalScope.isActiveFunctionBindingConfigurable();
    }

    /**
     * Whether {@link #close()} has been called on this context.
     *
     * @return true when the context is closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Whether the module body about to run was pulled in to satisfy an import.
     *
     * @return true when the current evaluation is of an imported module
     */
    boolean isEvaluatingImportedModule() {
        return evaluatingImportedModule;
    }

    public boolean isGlobalLexicalBindingInitialized(String name) {
        return globalLexicalScope.isBindingInitialized(name);
    }

    public boolean isInBareVariableAssignment() {
        return inBareVariableAssignment;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    /**
     * Check if in strict mode.
     */
    /**
     * Whether an {@code eval} that finishes now should leave the microtask queue alone.
     * <p>
     * The module loader raises this while it evaluates a graph's dependencies, so that a nested {@code eval} cannot
     * drain the queue before every import has been processed — which is what decides the order asynchronous module
     * completions run in.
     *
     * @return true while microtask processing is suppressed
     */
    boolean isSuppressingEvalMicrotasks() {
        return suppressEvalMicrotaskProcessing;
    }

    public boolean isWaitable() {
        return waitable;
    }

    public JSObject loadDynamicImportModule(String specifier, String referrerFilename) {
        return moduleLoader.loadDynamicImportModule(specifier, referrerFilename);
    }

    public JSObject loadDynamicImportModule(String specifier, String referrerFilename,
            Map<String, String> importAttributes) {
        return moduleLoader.loadDynamicImportModule(specifier, referrerFilename, importAttributes);
    }

    public JSObject loadDynamicImportModule(String specifier, String referrerFilename,
            Map<String, String> importAttributes, JSPromise importPromise, JSPromise.ResolveState resolveState) {
        return moduleLoader.loadDynamicImportModule(specifier, referrerFilename, importAttributes, importPromise,
                resolveState);
    }

    public JSObject loadDynamicImportModuleDeferred(String specifier, String referrerFilename,
            Map<String, String> importAttributes) {
        return moduleLoader.loadDynamicImportModuleDeferred(specifier, referrerFilename, importAttributes);
    }

    public JSObject loadDynamicImportModuleDeferred(String specifier, String referrerFilename,
            Map<String, String> importAttributes, JSPromise importPromise, JSPromise.ResolveState resolveState) {
        return moduleLoader.loadDynamicImportModuleDeferred(specifier, referrerFilename, importAttributes,
                importPromise, resolveState);
    }

    /**
     * The realm's module linker.
     *
     * @return the module linker
     */
    ModuleLinker moduleLinker() {
        return moduleLinker;
    }

    /**
     * The realm's module loader.
     * <p>
     * Package-private, and reached through the context rather than injected, because the loader is built after the
     * transformer and the linker that call back into it.
     *
     * @return the module loader
     */
    ModuleLoader moduleLoader() {
        return moduleLoader;
    }

    void pollFinalizationRegistries() {
        for (int registryIndex = 0; registryIndex < finalizationRegistries.size(); registryIndex++) {
            finalizationRegistries.get(registryIndex).pollCleanups();
        }
    }

    public void popEvalOverlay() {
        evalOverlayManager.pop();
    }

    public void popEvalOverlayLookupSuppression() {
        evalOverlayManager.popLookupSuppression();
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
     * Process all pending microtasks. This should be called at the end of each task in the event loop.
     */
    public void processMicrotasks() {
        requireOpen();
        microtaskQueue.processMicrotasks();
        pollFinalizationRegistries();
    }

    void pushEvalOverlay(Map<String, JSValue> savedGlobals, Map<String, PropertyDescriptor> savedDescriptors,
            Set<String> absentKeys, JSEvalEnvironment environment, Set<String> constantBindings) {
        evalOverlayManager.push(savedGlobals, savedDescriptors, absentKeys, environment, constantBindings);
    }

    public void pushEvalOverlay(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
        pushEvalOverlay(savedGlobals, absentKeys, null);
    }

    public void pushEvalOverlay(Map<String, JSValue> savedGlobals, Set<String> absentKeys,
            JSEvalEnvironment environment) {
        evalOverlayManager.push(savedGlobals, absentKeys, environment);
    }

    public void pushEvalOverlayLookupSuppression() {
        evalOverlayManager.pushLookupSuppression();
    }

    /**
     * Push a new stack frame. Returns false if stack limit exceeded.
     */
    public boolean pushStackFrame(JSStackFrame frame) {
        if (stackDepth >= maxCallDepth) {
            return false;
        }
        callStack.push(frame);
        stackDepth++;
        return true;
    }

    public JSValue readGlobalLexicalBinding(String name) {
        return globalLexicalScope.readBinding(name);
    }

    /**
     * Whether a module and everything it reaches can be evaluated synchronously.
     * <p>
     * Kept on the context because {@link JSDeferredModuleNamespace} asks it before forcing a deferred namespace; the
     * work itself belongs to {@link ModuleLoader}.
     *
     * @param resolvedSpecifier
     *            the module's resolved path
     * @param seen
     *            the specifiers already visited, so a cycle terminates
     * @return true when nothing in the graph needs to await
     */
    boolean readyForSyncExecution(String resolvedSpecifier, Set<String> seen) {
        return moduleLoader.readyForSyncExecution(resolvedSpecifier, seen);
    }

    /**
     * Count a module body that failed rather than running to completion.
     * <p>
     * See {@link #hasModuleBodyEvaluationFailed()}. A top-level-await body that finished and then rejected counts too,
     * which is why the module loader reports it rather than the eval pipeline.
     */
    void recordFailedModuleBodyEvaluation() {
        failedModuleBodyEvaluationCount++;
    }

    /**
     * Record a failure that escaped a microtask.
     * <p>
     * Draining the microtask queue has no caller to propagate to, so without this a throwing {@code .then()} handler —
     * or an engine defect surfacing as a {@link NullPointerException} — disappeared with no trace at all. Failures are
     * always recorded; an installed {@link IJSMicrotaskFailureCallback} sees them immediately, and with no callback
     * installed they are logged so they cannot pass unnoticed.
     *
     * @param failure
     *            the exception that escaped the microtask
     */
    public void recordMicrotaskFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        while (microtaskFailures.size() >= MAX_RECORDED_MICROTASK_FAILURES) {
            microtaskFailures.remove(0);
        }
        microtaskFailures.add(failure);
        IJSMicrotaskFailureCallback callback = microtaskFailureCallback;
        if (callback != null) {
            callback.onMicrotaskFailure(failure);
            return;
        }
        // Never silent. A JSException used to be left to the promise reject callback, but a failure
        // escaping a microtask is not a promise rejection, so that callback no longer sees it.
        System.getLogger(JSContext.class.getName()).log(System.Logger.Level.WARNING, "Unhandled failure in microtask",
                failure);
    }

    /**
     * Count a module body that is about to start executing.
     * <p>
     * See {@link #getModuleBodyEvaluationCount()} and {@link #getImportedModuleBodyEvaluationCount()}.
     */
    void recordModuleBodyEvaluation() {
        moduleBodyEvaluationCount++;
        if (evaluatingImportedModule) {
            importedModuleBodyEvaluationCount++;
        }
    }

    public void registerFinalizationRegistry(JSFinalizationRegistry registry) {
        finalizationRegistries.add(registry);
    }

    public void registerIteratorPrototype(String tag, JSObject prototype) {
        realmIntrinsics.registerIteratorPrototype(tag, prototype);
    }

    /** Record a RangeError and return the null sentinel used by native conversions. */
    public <T> T rejectRangeError(String message) {
        throwRangeError(message);
        return null;
    }

    /** Record a TypeError and return the null sentinel used by native conversions. */
    public <T> T rejectTypeError(String message) {
        throwTypeError(message);
        return null;
    }

    /**
     * Fail fast when an operation is attempted on a closed context.
     * <p>
     * {@code close()} used to set no flag at all, so {@code close()} followed by {@code eval()} silently worked and
     * masked lifecycle bugs in embedder code. This is one of the few places a raw Java exception is right: it is
     * embedder API misuse, not a JavaScript error.
     *
     * @throws IllegalStateException
     *             when this context is closed
     */
    void requireOpen() {
        if (closed) {
            throw new IllegalStateException("JSContext is closed");
        }
    }

    /**
     * Resolve a module's {@code export * from} and indirect re-exports into its namespace.
     * <p>
     * Kept on the context because {@link JSDeferredModuleNamespace} settles a deferred namespace through it; the work
     * itself belongs to {@link ModuleLinker}.
     *
     * @param moduleRecord
     *            the module whose re-exports are being resolved
     * @param importResolutionStack
     *            the specifiers already being resolved, so a cycle is detected
     */
    void resolveDynamicImportReExports(JSDynamicImportModule moduleRecord, Set<String> importResolutionStack) {
        moduleLinker.resolveDynamicImportReExports(moduleRecord, importResolutionStack);
    }

    /** The eval overlay binding of {@code name} visible from {@code environment}, or null. */
    public JSEvalOverlayBinding resolveEvalOverlayBinding(String name, JSEvalEnvironment environment) {
        EvalOverlayManager.Binding binding = evalOverlayManager.resolve(name, environment);
        if (binding == null) {
            return null;
        }
        return new JSEvalOverlayBinding(this, name,
                binding.displacer() != null ? binding.displacer().savedGlobals() : null,
                binding.displacer() != null ? binding.displacer().savedDescriptors().get(name) : null,
                binding.owner().constantBindings().contains(name));
    }

    /** Restore a guest exception at a boundary that reports failures through this context. */
    public void restoreVMException(JSVirtualMachineException exception) {
        JSValue value = exception.getThrownValue();
        if (value != null) {
            setPendingException(value);
        } else if (!hasPendingException()) {
            throwError(exception.getMessage() != null ? exception.getMessage() : "Unhandled exception");
        }
    }

    public void resumeEvalOverlays(JSGlobalObject.EvalOverlaySnapshot evalOverlaySnapshot) {
        evalOverlayManager.resume(evalOverlaySnapshot);
    }

    public void scheduleClassFieldEvalCall() {
        evalRunner.scheduleClassFieldEvalCall();
    }

    public void scheduleDirectEvalCall() {
        evalRunner.scheduleDirectEvalCall();
    }

    public void setAsyncFunctionConstructor(JSObject asyncFunctionConstructor) {
        realmIntrinsics.setAsyncFunctionConstructor(asyncFunctionConstructor);
    }

    public void setAsyncGeneratorFunctionPrototype(JSObject asyncGeneratorFunctionPrototype) {
        realmIntrinsics.setAsyncGeneratorFunctionPrototype(asyncGeneratorFunctionPrototype);
    }

    public void setAsyncGeneratorPrototype(JSObject asyncGeneratorPrototype) {
        realmIntrinsics.setAsyncGeneratorPrototype(asyncGeneratorPrototype);
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

    void setEvaluatingImportedModule(boolean value) {
        this.evaluatingImportedModule = value;
    }

    public void setGeneratorFunctionPrototype(JSObject generatorFunctionPrototype) {
        realmIntrinsics.setGeneratorFunctionPrototype(generatorFunctionPrototype);
    }

    public void setGlobalFunctionBindingInitializations(Set<String> functionNames, boolean configurable) {
        globalLexicalScope.setFunctionBindingInitializations(functionNames, configurable);
    }

    public void setInBareVariableAssignment(boolean value) {
        this.inBareVariableAssignment = value;
    }

    /**
     * Set the maximum number of nested activations in this context, overriding the runtime's
     * {@link JSRuntimeOptions#setMaxCallDepth(int)}.
     *
     * @param maxCallDepth
     *            the maximum call depth, at least 1
     * @throws IllegalArgumentException
     *             when the depth is below 1: zero or a negative depth refused every evaluation, the top-level code
     *             included, with a RangeError
     */
    public void setMaxCallDepth(int maxCallDepth) {
        if (maxCallDepth < 1) {
            throw new IllegalArgumentException("maxCallDepth must be at least 1: " + maxCallDepth);
        }
        this.maxCallDepth = maxCallDepth;
    }

    /**
     * Set the maximum call depth.
     *
     * @param depth
     *            the maximum number of nested activations, at least 1
     * @deprecated use {@link #setMaxCallDepth(int)}
     */
    @Deprecated
    public void setMaxStackDepth(int depth) {
        setMaxCallDepth(depth);
    }

    /**
     * Install a callback that observes failures escaping a microtask.
     *
     * @param callback
     *            the callback, or {@code null} to remove the current one
     */
    public void setMicrotaskFailureCallback(IJSMicrotaskFailureCallback callback) {
        this.microtaskFailureCallback = callback;
    }

    public void setNativeConstructorNewTarget(JSValue newTarget) {
        this.nativeConstructorNewTarget = newTarget;
    }

    /** The environment of the direct eval about to run, for the eval runner to hand to the compiled eval code. */
    void setPendingDirectEvalEnvironment(JSEvalEnvironment environment) {
        pendingDirectEvalEnvironment = environment;
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
     * Set the promise rejection callback. This callback is invoked when a promise rejection occurs in an await
     * expression. If the callback returns true, the rejection is considered handled and the catch clause will take
     * effect instead of throwing an exception.
     */
    public void setPromiseRejectCallback(IJSPromiseRejectCallback callback) {
        this.promiseRejectCallback = callback;
    }

    public void setRegExpLegacyInput(String inputValue) {
        regExpLegacyStatics.setInput(inputValue);
    }

    void setSuppressingEvalMicrotasks(boolean value) {
        this.suppressEvalMicrotaskProcessing = value;
    }

    public void setThrowTypeErrorIntrinsic(JSNativeFunction throwTypeError) {
        realmIntrinsics.setThrowTypeErrorIntrinsic(throwTypeError);
    }

    public void setWaitable(boolean waitable) {
        this.waitable = waitable;
    }

    public JSGlobalObject.EvalOverlaySnapshot suspendEvalOverlays() {
        return evalOverlayManager.suspend();
    }

    /** Take the pending exception and clear exception state from the context and VM. */
    public JSValue takeExecutionException() {
        JSValue exception = getPendingException();
        clearAllPendingExceptions();
        return exception;
    }

    JSEvalEnvironment takePendingDirectEvalEnvironment() {
        JSEvalEnvironment environment = pendingDirectEvalEnvironment;
        pendingDirectEvalEnvironment = null;
        return environment;
    }

    /** Read and clear this context's pending exception. */
    public JSValue takePendingException() {
        JSValue exception = pendingException;
        pendingException = null;
        return exception;
    }

    /** Recover the JavaScript rejection reason when asynchronous execution fails. */
    public JSValue takeRejectionReason(Exception e) {
        if (hasPendingException()) {
            return takeExecutionException();
        }
        if (e instanceof JSException jsException) {
            return jsException.getErrorValue();
        }
        if (e instanceof JSVirtualMachineException virtualMachineException) {
            if (virtualMachineException.getJsValue() != null) {
                return virtualMachineException.getJsValue();
            }
            if (virtualMachineException.getJsError() != null) {
                return virtualMachineException.getJsError();
            }
        }
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        JSValue error = throwError(message);
        clearPendingException();
        return error;
    }

    public JSError throwAggregateError(String message) {
        return errorReporter.throwError(JSAggregateError.NAME, message);
    }

    public JSError throwError(JSError jsError) {
        return errorReporter.throwError(jsError);
    }

    public JSError throwError(JSErrorException jsErrorException) {
        return errorReporter.throwError(jsErrorException);
    }

    public JSError throwError(String message) {
        return errorReporter.throwError(message);
    }

    public JSError throwError(String errorType, String message) {
        return errorReporter.throwError(errorType, message);
    }

    public JSError throwError(String errorType, String message, SourceLocation sourceLocation) {
        return errorReporter.throwError(errorType, message, sourceLocation);
    }

    public JSError throwEvalError(String message) {
        return errorReporter.throwError(JSEvalError.NAME, message);
    }

    public JSError throwRangeError(String message) {
        return errorReporter.throwError(JSRangeError.NAME, message);
    }

    public JSError throwReferenceError(String message) {
        return errorReporter.throwError(JSReferenceError.NAME, message);
    }

    public JSError throwSyntaxError(String message) {
        return errorReporter.throwError(JSSyntaxError.NAME, message);
    }

    public JSError throwSyntaxError(String message, SourceLocation sourceLocation) {
        return errorReporter.throwError(JSSyntaxError.NAME, message, sourceLocation);
    }

    public JSError throwTypeError(String message) {
        return errorReporter.throwError(JSTypeError.NAME, message);
    }

    public JSError throwTypeError(String message, SourceLocation sourceLocation) {
        return errorReporter.throwError(JSTypeError.NAME, message, sourceLocation);
    }

    public JSError throwURIError(String message) {
        return errorReporter.throwError(JSURIError.NAME, message);
    }

    public boolean transferPrototype(JSObject receiver, JSObject constructor) {
        return realmIntrinsics.transferPrototype(receiver, constructor);
    }

    public boolean transferPrototype(JSObject receiver, String constructorName) {
        return realmIntrinsics.transferPrototype(receiver, constructorName);
    }

    public boolean transferPrototypeFromConstructor(JSObject receiver, JSObject constructor) {
        return realmIntrinsics.transferPrototypeFromConstructor(receiver, constructor);
    }

    public void updateRegExpLegacyStatics(String inputValue, String[] captureValues, int[][] captureIndices,
            int fallbackStartIndex) {
        regExpLegacyStatics.update(inputValue, captureValues, captureIndices, fallbackStartIndex);
    }

    public void writeGlobalLexicalBinding(String name, JSValue value) {
        globalLexicalScope.writeBinding(name, value);
    }
}
