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
        this(context, args, isStrict, null);
    }

    /**
     * Create an arguments object with function reference.
     *
     * @param context  The execution context
     * @param args     The argument values
     * @param isStrict Whether the function is in strict mode
     * @param callee   The function being called (for non-strict mode)
     */
    public JSArguments(JSContext context, JSValue[] args, boolean isStrict, JSFunction callee) {
        super();
        this.argumentValues = args != null ? args : new JSValue[0];
        this.isStrict = isStrict;

        // Set length property (writable, non-enumerable, configurable per ES spec)
        PropertyDescriptor lengthDesc = PropertyDescriptor.dataDescriptor(
                new JSNumber(argumentValues.length),
                true,   // writable
                false,  // enumerable
                true    // configurable
        );
        defineProperty(PropertyKey.fromString("length"), lengthDesc);

        // Set indexed properties for each argument
        for (int i = 0; i < argumentValues.length; i++) {
            PropertyDescriptor argDesc = PropertyDescriptor.dataDescriptor(
                    argumentValues[i],
                    true,  // writable
                    true,  // enumerable
                    true   // configurable
            );
            defineProperty(PropertyKey.fromIndex(i), argDesc);
        }

        // Handle callee and caller properties based on strict mode
        if (isStrict) {
            // In strict mode, callee is an accessor property that throws TypeError
            // Create a thrower function that returns TypeError when called
            JSNativeFunction thrower = new JSNativeFunction(
                    "ThrowTypeError",
                    0,
                    (ctx, thisArg, argsArray) -> ctx.throwTypeError(
                            "'caller', 'callee', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them"
                    )
            );

            // Define callee as accessor with thrower for both get and set
            PropertyDescriptor calleeDesc = PropertyDescriptor.accessorDescriptor(
                    thrower,  // getter throws
                    thrower,  // setter throws
                    false,    // non-enumerable
                    false     // non-configurable
            );
            defineProperty(PropertyKey.fromString("callee"), calleeDesc);

            // Note: arguments.caller is NOT defined in strict mode (returns undefined when accessed)
            // This matches V8 behavior where arguments.caller is always undefined
        } else {
            // In non-strict mode, callee is a data property referencing the function
            if (callee != null) {
                PropertyDescriptor calleeDesc = PropertyDescriptor.dataDescriptor(
                        callee,
                        true,   // writable
                        false,  // non-enumerable
                        true    // configurable
                );
                defineProperty(PropertyKey.fromString("callee"), calleeDesc);
            }

            // Note: arguments.caller is NOT defined (returns undefined when accessed)
            // This matches V8 behavior where arguments.caller is deprecated and undefined
        }

        // Add Symbol.iterator property (points to Array.prototype[Symbol.iterator])
        // This enables for-of iteration over arguments
        try {
            JSValue symbolCtor = context.getGlobalObject().get(PropertyKey.fromString("Symbol"));
            if (symbolCtor instanceof JSObject symbolObj) {
                JSValue iteratorSymbol = symbolObj.get(PropertyKey.fromString("iterator"));
                if (iteratorSymbol instanceof JSSymbol sym) {
                    // Get Array.prototype
                    JSValue arrayCtor = context.getGlobalObject().get(PropertyKey.fromString("Array"));
                    if (arrayCtor instanceof JSObject arrayCtorObj) {
                        JSValue arrayProto = arrayCtorObj.get(PropertyKey.fromString("prototype"));
                        if (arrayProto instanceof JSObject arrayProtoObj) {
                            JSValue arrayIterator = arrayProtoObj.get(PropertyKey.fromSymbol(sym));
                            if (arrayIterator != null && !(arrayIterator instanceof JSUndefined)) {
                                PropertyDescriptor iterDesc = PropertyDescriptor.dataDescriptor(
                                        arrayIterator,
                                        true,   // writable
                                        false,  // non-enumerable
                                        true    // configurable
                                );
                                defineProperty(PropertyKey.fromSymbol(sym), iterDesc);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors setting up Symbol.iterator - not critical
        }
    }

    /**
     * Override get to return from argumentValues for indexed properties.
     * This ensures we return the most up-to-date value.
     */
    @Override
    protected JSValue get(PropertyKey key, JSContext context, JSObject receiver) {
        // For indexed properties within bounds, return from argumentValues
        if (key.isIndex()) {
            int index = key.asIndex();
            if (index >= 0 && index < argumentValues.length) {
                return argumentValues[index];
            }
        }
        // For other properties, use parent implementation
        return super.get(key, context, receiver);
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
     * Override set to handle indexed properties.
     * When setting an indexed property, we need to update both the property descriptor
     * AND the underlying argumentValues array to keep them in sync.
     */
    @Override
    public void set(PropertyKey key, JSValue value, JSContext context) {
        // First, call the parent implementation to handle the property descriptor
        super.set(key, value, context);

        // If this is an indexed property within the arguments range, also update the array
        if (key.isIndex()) {
            int index = key.asIndex();
            if (index >= 0 && index < argumentValues.length) {
                argumentValues[index] = value;
            }
        }
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
