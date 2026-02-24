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

import com.caoccao.qjs4j.vm.VarRef;

import java.util.HashSet;
import java.util.Set;

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
    private final Set<Integer> mappedIndices = new HashSet<>();
    private final VarRef[] parameterVarRefs;

    /**
     * Create an arguments object.
     *
     * @param context  The execution context
     * @param args     The argument values
     * @param isStrict Whether the function is in strict mode
     */
    public JSArguments(JSContext context, JSValue[] args, boolean isStrict) {
        this(context, args, isStrict, null, null);
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
        this(context, args, isStrict, callee, null);
    }

    /**
     * Create an arguments object with optional mapped parameter references.
     *
     * @param context       The execution context
     * @param args          The argument values
     * @param isStrict      Whether the function is in strict mode
     * @param callee        The function being called (for non-strict mode)
     * @param mappedVarRefs Optional per-index references for mapped arguments object
     */
    public JSArguments(
            JSContext context,
            JSValue[] args,
            boolean isStrict,
            JSFunction callee,
            VarRef[] mappedVarRefs) {
        super();
        this.argumentValues = args != null ? args : new JSValue[0];
        this.isStrict = isStrict;
        this.parameterVarRefs = mappedVarRefs;

        // Set length property (writable, non-enumerable, configurable per ES spec)
        definePropertyWritableConfigurable("length", JSNumber.of(argumentValues.length));

        // Set indexed properties for each argument
        for (int i = 0; i < argumentValues.length; i++) {
            if (mappedVarRefs != null && i < mappedVarRefs.length && mappedVarRefs[i] != null) {
                mappedIndices.add(i);
                final VarRef mappedVarRef = mappedVarRefs[i];
                final int mappedIndex = i;
                JSNativeFunction getter = new JSNativeFunction(
                        "get " + mappedIndex,
                        0,
                        (ctx, thisArg, argsArray) -> {
                            JSValue value = mappedVarRef.get();
                            return value != null ? value : JSUndefined.INSTANCE;
                        });
                JSNativeFunction setter = new JSNativeFunction(
                        "set " + mappedIndex,
                        1,
                        (ctx, thisArg, argsArray) -> {
                            JSValue value = argsArray.length > 0 ? argsArray[0] : JSUndefined.INSTANCE;
                            mappedVarRef.set(value);
                            return JSUndefined.INSTANCE;
                        });
                defineProperty(
                        PropertyKey.fromIndex(i),
                        PropertyDescriptor.accessorDescriptor(getter, setter, true, true));
            } else {
                PropertyDescriptor argDesc = PropertyDescriptor.dataDescriptor(
                        argumentValues[i],
                        true,  // writable
                        true,  // enumerable
                        true   // configurable
                );
                defineProperty(PropertyKey.fromIndex(i), argDesc);
            }
        }

        // Handle callee and caller properties based on strict mode
        if (isStrict) {
            // In strict mode, callee is an accessor property that throws TypeError
            // Use the shared %ThrowTypeError% intrinsic from the context so that
            // Function.prototype.caller/arguments getter === arguments.callee getter
            JSNativeFunction thrower = context.getThrowTypeErrorIntrinsic();
            if (thrower == null) {
                // Fallback if intrinsic not yet initialized
                thrower = new JSNativeFunction(
                        "ThrowTypeError",
                        0,
                        (ctx, thisArg, argsArray) -> ctx.throwTypeError(
                                "'caller', 'callee', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them"
                        )
                );
            }

            // Define callee as accessor with thrower for both get and set
            PropertyDescriptor calleeDesc = PropertyDescriptor.accessorDescriptor(
                    thrower,  // getter throws
                    thrower,  // setter throws
                    false,    // non-enumerable
                    false     // non-configurable
            );
            defineProperty(PropertyKey.CALLEE, calleeDesc);

            // Note: arguments.caller is NOT defined in strict mode (returns undefined when accessed)
            // This matches V8 behavior where arguments.caller is always undefined
        } else {
            // In non-strict mode, callee is a data property referencing the function
            if (callee != null) {
                definePropertyWritableConfigurable("callee", callee);
            }

            // Note: arguments.caller is NOT defined (returns undefined when accessed)
            // This matches V8 behavior where arguments.caller is deprecated and undefined
        }

        // Add Symbol.iterator property (points to Array.prototype[Symbol.iterator])
        // This enables for-of iteration over arguments
        try {
            JSValue symbolCtor = context.getGlobalObject().get(PropertyKey.SYMBOL);
            if (symbolCtor instanceof JSObject symbolObj) {
                JSValue iteratorSymbol = symbolObj.get(PropertyKey.ITERATOR);
                if (iteratorSymbol instanceof JSSymbol sym) {
                    // Get Array.prototype
                    JSValue arrayCtor = context.getGlobalObject().get(JSArray.NAME);
                    if (arrayCtor instanceof JSObject arrayCtorObj) {
                        JSValue arrayProto = arrayCtorObj.get(PropertyKey.PROTOTYPE);
                        if (arrayProto instanceof JSObject arrayProtoObj) {
                            JSValue arrayIterator = arrayProtoObj.get(PropertyKey.fromSymbol(sym));
                            if (arrayIterator != null && !(arrayIterator instanceof JSUndefined)) {
                                definePropertyWritableConfigurable(sym, arrayIterator);
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
     * Override [[DefineOwnProperty]] per ES2024 10.4.4.2.
     * Mapped parameters are stored as accessor properties for VarRef synchronization,
     * but the spec treats them as data properties. This override implements ES2024
     * 10.4.4.2 [[DefineOwnProperty]] for arguments objects:
     * - Before applying, unmap the accessor so OrdinaryDefineOwnProperty sees a data property
     * - After applying, sync VarRef if descriptor has [[Value]]
     * - Unmap on accessor descriptor or writable:false
     */
    @Override
    public boolean defineOwnProperty(PropertyKey key, PropertyDescriptor descriptor, JSContext context) {
        int index = getArgumentIndex(key);
        boolean isMapped = index >= 0 && mappedIndices.contains(index);

        if (isMapped) {
            // Check if the property still exists as a mapped accessor
            PropertyDescriptor current = getOwnPropertyDescriptor(key);
            if (current == null || !current.isAccessorDescriptor()) {
                // Property was deleted or already converted; clear stale mapping
                mappedIndices.remove(index);
                isMapped = false;
            } else {
                // Unmap: convert the stored accessor to a data property so that
                // OrdinaryDefineOwnProperty sees the correct property type.
                unmapParameter(key, index, context);
            }
        }

        boolean result = super.defineOwnProperty(key, descriptor, context);

        // ES2024 10.4.4.2 step 7: post-define mapping updates
        if (result && isMapped) {
            if (descriptor.isAccessorDescriptor()) {
                // Step 7a: accessor descriptor removes mapping (already unmapped above)
                // mappedIndices already cleared in unmapParameter
            } else {
                // Step 7b.i: sync VarRef with new value
                if (descriptor.hasValue() && parameterVarRefs != null
                        && index < parameterVarRefs.length && parameterVarRefs[index] != null) {
                    parameterVarRefs[index].set(descriptor.getValue());
                }
                // Step 7b.ii: writable:false removes mapping
                if (descriptor.hasWritable() && !descriptor.isWritable()) {
                    mappedIndices.remove(index);
                }
            }
        }

        return result;
    }

    private int getArgumentIndex(PropertyKey key) {
        if (key.isIndex()) {
            return key.asIndex();
        }
        if (key.isString()) {
            try {
                return Integer.parseInt(key.asString());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
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
    public void set(JSContext context, PropertyKey key, JSValue value) {
        // First, call the parent implementation to handle the property descriptor
        super.set(context, key, value);

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

    private void unmapParameter(PropertyKey key, int index, JSContext context) {
        // Get the current value from the mapped getter
        PropertyDescriptor current = getOwnPropertyDescriptor(key);
        JSValue currentValue = JSUndefined.INSTANCE;
        if (current != null && current.getGetter() != null) {
            currentValue = current.getGetter().call(context, this, new JSValue[0]);
        }

        // Replace the accessor with a data property, preserving enumerable/configurable
        boolean enumerable = current != null && current.isEnumerable();
        boolean configurable = current != null && current.isConfigurable();
        defineProperty(key, PropertyDescriptor.dataDescriptor(
                currentValue, true, enumerable, configurable));

        // Mark as unmapped
        mappedIndices.remove(index);
    }
}
