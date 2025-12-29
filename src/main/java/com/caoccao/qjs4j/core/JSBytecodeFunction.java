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

import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.VirtualMachine;

/**
 * Represents a JavaScript function compiled to bytecode.
 * Based on QuickJS JSFunctionBytecode structure.
 * <p>
 * Bytecode functions are created by:
 * - Function declarations
 * - Function expressions
 * - Arrow functions
 * - Method definitions
 * <p>
 * They contain:
 * - Compiled bytecode for execution
 * - Closure variables (captured from outer scopes)
 * - Prototype object (for constructors)
 * - Function metadata (name, length)
 */
public final class JSBytecodeFunction extends JSFunction {
    private final Bytecode bytecode;
    private final JSValue[] closureVars;
    private final boolean isAsync;
    private final boolean isConstructor;
    private final boolean isGenerator;
    private final int length;
    private final String name;
    private final JSObject prototype;

    /**
     * Create a bytecode function.
     *
     * @param bytecode The compiled bytecode
     * @param name     Function name (empty string for anonymous)
     * @param length   Number of formal parameters
     */
    public JSBytecodeFunction(Bytecode bytecode, String name, int length) {
        this(bytecode, name, length, new JSValue[0], null, true, false, false);
    }

    /**
     * Create a bytecode function with full configuration.
     */
    public JSBytecodeFunction(Bytecode bytecode, String name, int length,
                              JSValue[] closureVars, JSObject prototype,
                              boolean isConstructor, boolean isAsync, boolean isGenerator) {
        super(); // Initialize as JSObject
        this.bytecode = bytecode;
        this.name = name != null ? name : "";
        this.length = length;
        this.closureVars = closureVars != null ? closureVars : new JSValue[0];
        this.prototype = prototype;
        this.isConstructor = isConstructor;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;

        // Set up function properties on the object
        // Functions are objects in JavaScript and have these standard properties
        this.set("name", new JSString(this.name));
        this.set("length", new JSNumber(this.length));

        // Every function (except arrow functions) has a prototype property
        if (prototype != null) {
            this.set("prototype", prototype);
        } else if (isConstructor) {
            JSObject funcPrototype = new JSObject();
            funcPrototype.set("constructor", this);
            this.set("prototype", funcPrototype);
        }
    }

    @Override
    public JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // If this is an async function, wrap execution in a promise
        if (isAsync) {
            JSPromise promise = new JSPromise();
            try {
                // Execute bytecode in the VM
                JSValue result = ctx.getVirtualMachine().execute(this, thisArg, args);

                // If result is already a promise, use it directly
                if (result instanceof JSPromise) {
                    return result;
                }

                // Otherwise, wrap the result in a fulfilled promise
                promise.fulfill(result);
            } catch (VirtualMachine.VMException e) {
                // VM exception during async function execution
                // Check if there's a pending exception in the context
                if (ctx.hasPendingException()) {
                    JSValue exception = ctx.getPendingException();
                    ctx.clearAllPendingExceptions(); // Clear BOTH context and VM pending exceptions
                    promise.reject(exception);
                } else {
                    // Create error object from exception message
                    String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                    JSObject errorObj = new JSObject();
                    errorObj.set("message", new JSString(errorMessage));
                    promise.reject(errorObj);
                }
            } catch (Exception e) {
                // Any other exception in an async function should be caught
                // and wrapped in a rejected promise
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                JSObject errorObj = new JSObject();
                errorObj.set("message", new JSString(errorMessage));
                promise.reject(errorObj);
            }
            return promise;
        }

        // For non-async functions, execute normally and let exceptions propagate
        return ctx.getVirtualMachine().execute(this, thisArg, args);
    }

    /**
     * Get the bytecode for this function.
     */
    public Bytecode getBytecode() {
        return bytecode;
    }

    /**
     * Get the closure variables (captured from outer scopes).
     */
    public JSValue[] getClosureVars() {
        return closureVars;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the prototype object (for constructor calls).
     */
    public JSObject getPrototype() {
        return prototype;
    }

    /**
     * Check if this is an async function.
     */
    public boolean isAsync() {
        return isAsync;
    }

    /**
     * Check if this function can be used as a constructor.
     */
    public boolean isConstructor() {
        return isConstructor;
    }

    /**
     * Check if this is a generator function.
     */
    public boolean isGenerator() {
        return isGenerator;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isAsync) sb.append("async ");
        sb.append("function");
        if (isGenerator) sb.append("*");
        sb.append(" ");
        if (!name.isEmpty()) sb.append(name);
        sb.append("() { [bytecode] }");
        return sb.toString();
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }
}
