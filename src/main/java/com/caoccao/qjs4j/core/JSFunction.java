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

/**
 * Abstract base class for JavaScript function types.
 * In JavaScript, functions are objects, so JSFunction extends JSObject.
 */
public abstract sealed class JSFunction extends JSObject
        permits JSBytecodeFunction, JSNativeFunction, JSBoundFunction, JSClass {
    public static final String NAME = "Function";
    private JSContext homeContext;

    /**
     * Call this function with the given context, this value, and arguments.
     */
    public abstract JSValue call(JSContext context, JSValue thisArg, JSValue[] args);

    protected JSContext getHomeContext() {
        return homeContext;
    }

    /**
     * Get the number of formal parameters.
     */
    public abstract int getLength();

    /**
     * Get the function name.
     */
    public abstract String getName();

    /**
     * Initialize the function's prototype chain to inherit from Function.prototype.
     * This should be called after creation when the context is available.
     */
    public void initializePrototypeChain(JSContext context) {
        if (homeContext == null) {
            homeContext = context;
        }
        // For async functions, use AsyncFunction.prototype if available
        if (this instanceof JSBytecodeFunction bytecodeFunc && bytecodeFunc.isAsync()) {
            if (context.transferPrototype(this, context.getAsyncFunctionConstructor())) {
                return;
            }
        }
        context.transferPrototype(this, NAME);
    }

    @Override
    public Object toJavaObject() {
        return null;
    }
}
