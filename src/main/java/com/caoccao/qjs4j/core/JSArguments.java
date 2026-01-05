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
 * Represents the arguments object available in functions.
 * Based on QuickJS JS_CLASS_ARGUMENTS implementation.
 * <p>
 * The arguments object is an array-like object accessible inside functions that contains
 * the values of the arguments passed to that function.
 * <p>
 * Key characteristics:
 * - Array-like: Has indexed access and a length property
 * - Available in non-arrow functions
 * - In strict mode, has no callee property (throws TypeError if accessed)
 * - Has Symbol.iterator for iteration support
 * <p>
 * ECMAScript Specification: 10.2.11 FunctionDeclarationInstantiation
 */
public final class JSArguments extends JSObject {
    public static final String NAME = "arguments";
    private final JSValue[] argumentValues;
    private final boolean isStrict;

    /**
     * Create an arguments object.
     *
     * @param context  The execution context
     * @param args     The argument values
     * @param isStrict Whether the function is in strict mode
     */
    public JSArguments(JSContext context, JSValue[] args, boolean isStrict) {
        super();
        this.argumentValues = args != null ? args : new JSValue[0];
        this.isStrict = isStrict;

        // Set length property (writable, non-enumerable, configurable per ES spec)
        set(PropertyKey.fromString("length"), new JSNumber(argumentValues.length));

        // Set indexed properties for each argument
        for (int i = 0; i < argumentValues.length; i++) {
            set(PropertyKey.fromIndex(i), argumentValues[i]);
        }

        // In strict mode, 'callee' and 'caller' properties throw TypeError when accessed
        // In non-strict mode, 'callee' references the function itself
        // For now, we don't implement callee/caller as they're deprecated
        // and strict mode is the modern standard
    }

    /**
     * Get the argument values array.
     */
    public JSValue[] getArgumentValues() {
        return argumentValues;
    }

    /**
     * Check if this is a strict mode arguments object.
     */
    public boolean isStrict() {
        return isStrict;
    }

    /**
     * Convert to string for display.
     */
    @Override
    public String toJavaObject() {
        return "[Arguments(" + argumentValues.length + ")]";
    }

    @Override
    public String toString() {
        return "[object Arguments]";
    }
}
