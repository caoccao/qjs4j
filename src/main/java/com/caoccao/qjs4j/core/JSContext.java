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
        JSArray array = new JSArray(length, capacity);
        JSValue arrayCtor = globalObject.get("Array");
        if (arrayCtor instanceof JSObject) {
            JSValue arrayProto = ((JSObject) arrayCtor).get("prototype");
            if (arrayProto instanceof JSObject) {
                array.setPrototype((JSObject) arrayProto);
            }
        }
        return array;
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
