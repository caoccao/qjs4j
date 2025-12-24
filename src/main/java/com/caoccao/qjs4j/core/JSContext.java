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
import com.caoccao.qjs4j.types.JSModule;

import java.util.*;

/**
 * Represents a JavaScript execution context.
 * Based on QuickJS JSContext structure.
 *
 * A context is an independent JavaScript execution environment with:
 * - Its own global object and built-in objects
 * - Its own module cache
 * - Its own call stack and exception state
 * - Shared runtime resources (atoms, GC, job queue)
 *
 * Multiple contexts can exist in a single runtime, each isolated
 * from the others (separate globals, separate module namespaces).
 */
public final class JSContext {
    private final JSRuntime runtime;
    private final JSObject globalObject;
    private final Map<String, JSModule> moduleCache;

    // Call stack management
    private final Deque<StackFrame> callStack;
    private int stackDepth;
    private static final int DEFAULT_MAX_STACK_DEPTH = 1000;
    private int maxStackDepth;

    // Exception state
    private JSValue pendingException;
    private boolean inCatchHandler;

    // Execution state
    private boolean strictMode;
    private JSValue currentThis;

    // Stack trace capture
    private final List<StackTraceElement> errorStackTrace;

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

        initializeGlobalObject();
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

    // Evaluation

    /**
     * Evaluate JavaScript code in this context.
     *
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
     * @param code JavaScript source code
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
            // In full implementation:
            // 1. Lexer: tokenize the source
            // 2. Parser: build AST
            // 3. Compiler: generate bytecode
            // 4. VM: execute bytecode
            //
            // For now, return undefined as placeholder
            return JSUndefined.INSTANCE;
        } finally {
            popStackFrame();
        }
    }

    // Module system

    /**
     * Load and cache a JavaScript module.
     *
     * @param specifier Module specifier (file path or URL)
     * @return The loaded module
     */
    public JSModule loadModule(String specifier) {
        // Check cache first
        JSModule cached = moduleCache.get(specifier);
        if (cached != null) {
            return cached;
        }

        // In full implementation:
        // 1. Resolve the module specifier to absolute path
        // 2. Load the module source code
        // 3. Parse and compile as module
        // 4. Link imported/exported bindings
        // 5. Execute module code (if not already executed)
        // 6. Cache and return the module

        // For now, return null
        return null;
    }

    /**
     * Register a module in the cache.
     */
    public void registerModule(String specifier, JSModule module) {
        moduleCache.put(specifier, module);
    }

    /**
     * Get a cached module.
     */
    public JSModule getModule(String specifier) {
        return moduleCache.get(specifier);
    }

    /**
     * Clear the module cache.
     */
    public void clearModuleCache() {
        moduleCache.clear();
    }

    // Exception handling

    /**
     * Throw a JavaScript error.
     * Creates an Error object and sets it as the pending exception.
     *
     * @param message Error message
     * @return The error value (for convenience in return statements)
     */
    public JSValue throwError(String message) {
        return throwError("Error", message);
    }

    /**
     * Throw a JavaScript error of a specific type.
     *
     * @param errorType Error constructor name (Error, TypeError, RangeError, etc.)
     * @param message Error message
     * @return The error value
     */
    public JSValue throwError(String errorType, String message) {
        // Create error object
        JSObject error = new JSObject();
        error.set("name", new JSString(errorType));
        error.set("message", new JSString(message));

        // Capture stack trace
        captureStackTrace(error);

        // Set as pending exception
        setPendingException(error);

        return error;
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
     * Get the pending exception.
     */
    public JSValue getPendingException() {
        return pendingException;
    }

    /**
     * Check if there's a pending exception.
     */
    public boolean hasPendingException() {
        return pendingException != null;
    }

    /**
     * Clear the pending exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
        this.errorStackTrace.clear();
    }

    /**
     * Execute code in a try-catch context.
     */
    public void enterCatchHandler() {
        this.inCatchHandler = true;
    }

    /**
     * Exit try-catch context.
     */
    public void exitCatchHandler() {
        this.inCatchHandler = false;
    }

    // Stack management

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
     * Get the current stack frame.
     */
    public StackFrame getCurrentStackFrame() {
        return callStack.peek();
    }

    /**
     * Get the current call stack depth.
     */
    public int getStackDepth() {
        return stackDepth;
    }

    /**
     * Set the maximum stack depth.
     */
    public void setMaxStackDepth(int depth) {
        this.maxStackDepth = depth;
    }

    /**
     * Get the full call stack.
     */
    public List<StackFrame> getCallStack() {
        return new ArrayList<>(callStack);
    }

    // Stack trace capture

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

    /**
     * Get the error stack trace.
     */
    public List<StackTraceElement> getErrorStackTrace() {
        return new ArrayList<>(errorStackTrace);
    }

    // Execution state

    /**
     * Check if in strict mode.
     */
    public boolean isStrictMode() {
        return strictMode;
    }

    /**
     * Enter strict mode.
     */
    public void enterStrictMode() {
        this.strictMode = true;
    }

    /**
     * Exit strict mode.
     */
    public void exitStrictMode() {
        this.strictMode = false;
    }

    /**
     * Get the current 'this' binding.
     */
    public JSValue getCurrentThis() {
        return currentThis;
    }

    /**
     * Set the current 'this' binding.
     */
    public void setCurrentThis(JSValue thisValue) {
        this.currentThis = thisValue != null ? thisValue : globalObject;
    }

    // Accessors

    public JSRuntime getRuntime() {
        return runtime;
    }

    public JSObject getGlobalObject() {
        return globalObject;
    }

    // Cleanup

    /**
     * Close this context and release resources.
     * Called when the context is no longer needed.
     */
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
     * Represents a stack frame in the call stack.
     */
    public static class StackFrame {
        public final String functionName;
        public final String filename;
        public final int lineNumber;
        public final int columnNumber;

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
