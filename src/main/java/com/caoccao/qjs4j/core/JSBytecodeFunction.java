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

/**
 * Represents a JavaScript function compiled to bytecode.
 * Based on QuickJS JSFunctionBytecode structure.
 *
 * Bytecode functions are created by:
 * - Function declarations
 * - Function expressions
 * - Arrow functions
 * - Method definitions
 *
 * They contain:
 * - Compiled bytecode for execution
 * - Closure variables (captured from outer scopes)
 * - Prototype object (for constructors)
 * - Function metadata (name, length)
 */
public final class JSBytecodeFunction implements JSFunction {
    private final Bytecode bytecode;
    private final JSValue[] closureVars;
    private final JSObject prototype;
    private final String name;
    private final int length;
    private final boolean isConstructor;
    private final boolean isAsync;
    private final boolean isGenerator;

    /**
     * Create a bytecode function.
     *
     * @param bytecode The compiled bytecode
     * @param name Function name (empty string for anonymous)
     * @param length Number of formal parameters
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
        this.bytecode = bytecode;
        this.name = name != null ? name : "";
        this.length = length;
        this.closureVars = closureVars != null ? closureVars : new JSValue[0];
        this.prototype = prototype;
        this.isConstructor = isConstructor;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;
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

    /**
     * Get the prototype object (for constructor calls).
     */
    public JSObject getPrototype() {
        return prototype;
    }

    /**
     * Check if this function can be used as a constructor.
     */
    public boolean isConstructor() {
        return isConstructor;
    }

    /**
     * Check if this is an async function.
     */
    public boolean isAsync() {
        return isAsync;
    }

    /**
     * Check if this is a generator function.
     */
    public boolean isGenerator() {
        return isGenerator;
    }

    @Override
    public JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // In full implementation, this would:
        // 1. Create a new stack frame
        // 2. Bind arguments to parameters
        // 3. Execute bytecode in the VM
        // 4. Return the result
        //
        // For now, return undefined as placeholder
        return JSUndefined.INSTANCE;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
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
}
