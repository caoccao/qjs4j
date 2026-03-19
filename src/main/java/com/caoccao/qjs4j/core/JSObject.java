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

import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import com.caoccao.qjs4j.vm.StackFrame;

import java.util.*;

/**
 * Represents a JavaScript object.
 * Based on QuickJS object implementation with mutable shape system.
 * <p>
 * Following QuickJS approach:
 * - Each object has its own mutable shape (no sharing)
 * - Shapes can have properties added and removed
 * - Property deletion compacts shape when threshold is reached
 * - Property values stored in parallel array indexed by offset
 * - Sparse properties (numeric indices) stored separately
 */
public non-sealed class JSObject implements JSValue {
    public static final String NAME = "Object";
    private static final int MAX_PROTOTYPE_DEPTH = 10000;
    protected final JSContext context;
    protected boolean arrayObject; // Equivalent to QuickJS class_id == JS_CLASS_ARRAY
    protected JSConstructorType constructorType; // Internal slot for [[Constructor]] type (not accessible from JS)
    protected boolean extensible = true;
    protected boolean frozen = false;
    protected boolean htmlDDA; // Internal slot for [IsHTMLDDA] (Annex B test262 host object)
    protected boolean immutablePrototype; // Internal slot for [[SetPrototypeOf]] immutable prototype exotic objects
    protected JSValue primitiveValue; // Internal slot for [[PrimitiveValue]] (not accessible from JS)
    protected JSValue[] propertyValues;
    protected JSObject prototype;
    protected boolean sealed = false;
    protected JSShape shape;
    protected Map<Integer, JSValue> sparseProperties; // For array indices
    private boolean superConstructorCalled; // Tracks whether super() has been called in derived constructor

    /**
     * Create an empty object with no prototype.
     * Each object gets its own shape copy (not shared).
     */
    public JSObject(JSContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.shape = new JSShape();  // Each object gets its own shape
        this.propertyValues = JSValue.NO_ARGS;
        this.sparseProperties = null;
        this.prototype = null;
    }

    /**
     * Create an object with a specific prototype.
     */
    public JSObject(JSContext context, JSObject prototype) {
        this(context);
        this.prototype = prototype;
    }

    private static boolean sameValue(JSValue x, JSValue y) {
        if (x == y) {
            return true;
        }
        if (x == null || y == null) {
            return false;
        }

        // SameValue compares by ECMAScript type first (not Java class).
        JSValueType xType = x.type();
        JSValueType yType = y.type();
        if (xType != yType) {
            return false;
        }

        return switch (xType) {
            case UNDEFINED, NULL -> true;
            case NUMBER -> {
                if (!(x instanceof JSNumber xNum) || !(y instanceof JSNumber yNum)) {
                    yield false;
                }
                double xVal = xNum.value();
                double yVal = yNum.value();
                if (Double.isNaN(xVal) && Double.isNaN(yVal)) {
                    yield true;
                }
                yield Double.doubleToRawLongBits(xVal) == Double.doubleToRawLongBits(yVal);
            }
            case STRING -> (x instanceof JSString xStr && y instanceof JSString yStr)
                    && xStr.value().equals(yStr.value());
            case BOOLEAN -> (x instanceof JSBoolean xBool && y instanceof JSBoolean yBool)
                    && xBool.value() == yBool.value();
            case BIGINT -> (x instanceof JSBigInt xBigInt && y instanceof JSBigInt yBigInt)
                    && xBigInt.value().equals(yBigInt.value());
            // Symbol, Object, and Function types are same-value only by identity.
            case SYMBOL, OBJECT, FUNCTION -> false;
        };
    }

    /**
     * Compact properties by removing deleted properties.
     * Following QuickJS compact_properties() logic.
     */
    protected void compactProperties() {
        if (shape.getDeletedPropCount() == 0) {
            return; // Nothing to compact
        }

        // Collect values for non-deleted properties before compacting the shape.
        // Deleted properties have null descriptors (shape key set to null).
        int propCount = shape.getPropertyCount();
        int kept = propCount - shape.getDeletedPropCount();
        JSValue[] newValues = new JSValue[kept];
        int j = 0;
        for (int i = 0; i < propCount; i++) {
            if (shape.getDescriptorAt(i) != null) {
                newValues[j++] = propertyValues[i];
            }
        }

        // Now compact the shape to match
        shape.compact();

        this.propertyValues = newValues;
    }

    // Property operations

    /**
     * [[DefineOwnProperty]] per ES spec.
     * Returns true if the property was successfully defined, false if the object
     * is not extensible and the property does not already exist.
     */
    public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        if (!extensible && !hasOwnProperty(key)) {
            return false;
        }

        // ValidateAndApplyPropertyDescriptor: check current property constraints
        PropertyDescriptor current = getOwnPropertyDescriptor(key);
        if (current != null && !current.isConfigurable()) {
            // Step 4a: Cannot make non-configurable property configurable
            if (descriptor.hasConfigurable() && descriptor.isConfigurable()) {
                return false;
            }
            // Step 4b: Cannot change enumerable of non-configurable property
            if (descriptor.hasEnumerable() && descriptor.isEnumerable() != current.isEnumerable()) {
                return false;
            }
            // Step 6: Cannot convert between data and accessor if non-configurable
            boolean descIsAccessor = descriptor.isAccessorDescriptor();
            boolean curIsAccessor = current.isAccessorDescriptor();
            if (descIsAccessor != curIsAccessor) {
                // Trying to change property type on non-configurable property
                if (descriptor.hasValue() || descriptor.hasWritable() || descriptor.hasGetter() || descriptor.hasSetter()) {
                    return false;
                }
            }
            // Step 7a: Data property specific checks when non-configurable
            if (!curIsAccessor && !descIsAccessor && !current.isWritable()) {
                // Cannot set writable to true when current is non-writable, non-configurable
                if (descriptor.hasWritable() && descriptor.isWritable()) {
                    return false;
                }
                // Cannot change value when non-writable, non-configurable
                if (descriptor.hasValue()) {
                    JSValue curVal = current.getValue();
                    JSValue newVal = descriptor.getValue();
                    if (!sameValue(curVal, newVal)) {
                        return false;
                    }
                }
            }
            // Step 7b (accessor): Cannot change getter/setter on non-configurable accessor
            if (curIsAccessor && descIsAccessor) {
                if (descriptor.hasGetter() && descriptor.getGetter() != current.getGetter()) {
                    return false;
                }
                if (descriptor.hasSetter() && descriptor.getSetter() != current.getSetter()) {
                    return false;
                }
            }
        }

        // ValidateAndApplyPropertyDescriptor step 9: "For each field of Desc that
        // is present, set the corresponding attribute of the property named P of
        // object O to the value of the field."  Absent fields keep their current
        // values.  Build a merged descriptor so defineProperty receives the full
        // picture (current attributes + overrides from descriptor).
        if (current != null) {
            PropertyDescriptor merged = new PropertyDescriptor();
            merged.mergeFrom(current);
            merged.mergeFrom(descriptor);
            definePropertyInternal(key, merged);
        } else {
            // New property: apply default attribute values per ES2024 10.1.6.3 step 5.
            // "If IsGenericDescriptor(Desc) or IsDataDescriptor(Desc), create an own data
            //  property [...] with default attribute values."
            // "Else, Desc must be an accessor Property Descriptor so, create an own accessor
            //  property [...] with default attribute values."
            PropertyDescriptor completed = new PropertyDescriptor();
            completed.mergeFrom(descriptor);
            if (completed.isAccessorDescriptor()) {
                completed.completeAsAccessor();
            } else {
                completed.completeAsData();
            }
            definePropertyInternal(key, completed);
        }
        return true;
    }

    /**
     * [[DefineOwnProperty]] for a data descriptor, delegating to the full spec-compliant overload.
     * Equivalent to defineProperty(key, PropertyDescriptor.dataDescriptor(value, state)).
     */
    public boolean defineProperty(PropertyKey key, JSValue value, PropertyDescriptor.DataState state) {
        return defineProperty(key, PropertyDescriptor.dataDescriptor(value, state));
    }

    /**
     * Define an accessor property with a getter and no setter.
     */
    public boolean defineProperty(PropertyKey key, JSFunction getter, PropertyDescriptor.AccessorState state) {
        return defineProperty(key, PropertyDescriptor.accessorDescriptor(getter, null, state));
    }

    /**
     * Define an accessor property with a getter and a setter.
     */
    public boolean defineProperty(PropertyKey key, JSFunction getter, JSFunction setter, PropertyDescriptor.AccessorState state) {
        return defineProperty(key, PropertyDescriptor.accessorDescriptor(getter, setter, state));
    }

    /**
     * Define a new property with a descriptor.
     * This is the internal method that always succeeds (used by freeze, seal, etc.).
     */
    protected void definePropertyInternal(PropertyKey key, PropertyDescriptor descriptor) {
        // When defining a property (especially accessor), remove any sparse entry
        // so the shape-based property takes precedence in get().
        if (sparseProperties != null) {
            long arrayIndex = key.toArrayIndex();
            if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE) {
                sparseProperties.remove((int) arrayIndex);
            }
        }

        // Use getOwnPropertyOffset to handle integer/string key equivalence (e.g., 0 vs "0")
        int existingOffset = getOwnPropertyOffset(key);
        if (existingOffset >= 0) {
            // Property exists, merge descriptor and update value
            shape.getDescriptorAt(existingOffset).mergeFrom(descriptor);
            if (descriptor.hasValue()) {
                propertyValues[existingOffset] = descriptor.getValue();
            }
            return;
        }

        // Add new property to shape
        shape.addProperty(key, descriptor);

        // Grow property values array
        int newCount = shape.getPropertyCount();
        JSValue[] newValues = Arrays.copyOf(propertyValues, newCount);

        // Set the new property value
        if (descriptor.hasValue()) {
            newValues[newCount - 1] = descriptor.getValue();
        } else {
            newValues[newCount - 1] = JSUndefined.INSTANCE;
        }

        this.propertyValues = newValues;
    }


    /**
     * Delete a property.
     * Returns true if deletion was successful.
     * Following QuickJS delete_property() logic.
     */
    public boolean delete(String propertyName) {
        return delete(PropertyKey.fromString(propertyName));
    }

    /**
     * Delete a property by key.
     * Following QuickJS delete_property() implementation.
     */
    public boolean delete(PropertyKey key) {
        boolean strictMode = context.isStrictMode();
        // Check sparse properties first.
        long arrayIndex = key.toArrayIndex();
        if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            int sparseIndex = (int) arrayIndex;
            if (sparseProperties.containsKey(sparseIndex)) {
                if (sealed || frozen) {
                    if (strictMode) {
                        context.throwTypeError(
                                "Cannot delete property '" + key.toPropertyString() + "' of " + getObjectDescriptionForError(true));
                    }
                    return false;
                }
                sparseProperties.remove(sparseIndex);
                return true;
            }
            // Not in sparse storage; continue checking shape-backed properties.
        }

        // Find property in shape and check if it exists
        PropertyKey shapeKey = getOwnShapeKey(key);
        if (shapeKey == null) {
            return true; // Property doesn't exist, deletion successful
        }

        // Cannot delete an existing own property from sealed or frozen objects.
        if (sealed || frozen) {
            if (strictMode) {
                context.throwTypeError(
                        "Cannot delete property '" + key.toPropertyString() + "' of " + getObjectDescriptionForError(true));
            }
            return false;
        }

        int offset = shape.getPropertyOffset(shapeKey);

        // Check if property is configurable before removing
        PropertyDescriptor desc = shape.getDescriptorAt(offset);
        if (!desc.isConfigurable()) {
            // In strict mode, throw TypeError when trying to delete non-configurable property
            if (strictMode) {
                context.throwTypeError(
                        "Cannot delete property '" + key.toPropertyString() + "' of " + getObjectDescriptionForError(true));
            }
            return false;
        }

        // Remove from shape (checks configurability internally)
        boolean removed = shape.removeProperty(shapeKey);
        if (!removed) {
            return false; // Not configurable or other error
        }

        // Set value to undefined (QuickJS does this)
        propertyValues[offset] = JSUndefined.INSTANCE;

        // Compact if threshold reached (QuickJS logic: deleted >= 8 AND >= prop_count/2)
        if (shape.shouldCompact()) {
            compactProperties();
        }

        return true;
    }

    /**
     * Get own enumerable property keys.
     */
    public PropertyKey[] enumerableKeys() {
        return getOrderedOwnKeys(true).toArray(new PropertyKey[0]);
    }

    /**
     * Fast path for own enumerable string property names as JS strings.
     * Returns null when generic property ordering or accessor semantics are required.
     */
    public JSValue[] enumerableStringKeyValuesFastPath() {
        if (getClass() != JSObject.class) {
            return null;
        }
        if (sparseProperties != null || shape.getDeletedPropCount() != 0) {
            return null;
        }
        int propertyCount = shape.getPropertyCount();
        JSValue[] keyValues = new JSValue[propertyCount];
        int keyCount = 0;
        for (int index = 0; index < propertyCount; index++) {
            PropertyKey propertyKey = shape.getPropertyKeyAt(index);
            if (propertyKey == null) {
                return null;
            }
            if (propertyKey.isSymbol() || propertyKey.toArrayIndex() >= 0) {
                return null;
            }
            PropertyDescriptor descriptor = shape.getDescriptorAt(index);
            if (descriptor == null) {
                return null;
            }
            if (descriptor.isAccessorDescriptor()) {
                return null;
            }
            if (descriptor.isEnumerable()) {
                keyValues[keyCount++] = new JSString(propertyKey.asString());
            }
        }
        if (keyCount == keyValues.length) {
            return keyValues;
        }
        return Arrays.copyOf(keyValues, keyCount);
    }

    /**
     * Fast path for own enumerable string property values.
     * Returns null when generic property ordering or accessor semantics are required.
     */
    public JSValue[] enumerableStringPropertyValuesFastPath() {
        if (getClass() != JSObject.class) {
            return null;
        }
        if (sparseProperties != null || shape.getDeletedPropCount() != 0) {
            return null;
        }
        int propertyCount = shape.getPropertyCount();
        JSValue[] values = new JSValue[propertyCount];
        int valueCount = 0;
        for (int index = 0; index < propertyCount; index++) {
            PropertyKey propertyKey = shape.getPropertyKeyAt(index);
            if (propertyKey == null) {
                return null;
            }
            if (propertyKey.isSymbol() || propertyKey.toArrayIndex() >= 0) {
                return null;
            }
            PropertyDescriptor descriptor = shape.getDescriptorAt(index);
            if (descriptor == null) {
                return null;
            }
            if (descriptor.isAccessorDescriptor()) {
                return null;
            }
            if (descriptor.isEnumerable()) {
                JSValue propertyValue = index < propertyValues.length ? propertyValues[index] : null;
                values[valueCount++] = propertyValue != null ? propertyValue : JSUndefined.INSTANCE;
            }
        }
        if (valueCount == values.length) {
            return values;
        }
        return Arrays.copyOf(values, valueCount);
    }

    private boolean failSet(PropertyKey key, boolean throwOnFailure) {
        if (throwOnFailure && context.isStrictMode()) {
            context.throwTypeError(
                    "Cannot assign to read only property '" + key.toPropertyString() + "' of " + getObjectDescriptionForError(false));
        }
        return false;
    }

    private StackFrame findInnermostFrameForFunction(JSContext propertyAccessContext, JSFunction currentFunction) {
        StackFrame currentFrame = propertyAccessContext.getVirtualMachine().getCurrentFrame();
        while (currentFrame != null) {
            if (currentFrame.getFunction() == currentFunction) {
                return currentFrame;
            }
            currentFrame = currentFrame.getCaller();
        }
        return null;
    }

    /**
     * Freeze this object.
     * Prevents adding new properties, deleting existing properties, and modifying existing properties.
     */
    public void freeze() {
        this.frozen = true;
        this.sealed = true; // Frozen objects are also sealed
        this.extensible = false; // Frozen objects are not extensible
    }

    /**
     * Get a property value by string name.
     */
    public JSValue get(String propertyName) {
        return get(PropertyKey.fromString(propertyName));
    }

    /**
     * Get a property value by integer index.
     */
    public JSValue get(int index) {
        if (index < 0) {
            return get(String.valueOf(index));
        }
        return get(PropertyKey.fromIndex(index));
    }

    /**
     * Get a property value by property key.
     */
    public JSValue get(PropertyKey key) {
        return getWithReceiver(key, this, 0);
    }

    /**
     * Get a property with an explicit receiver for getter invocation.
     * The receiver is used as 'this' when calling property getters,
     * allowing primitive receivers in strict mode.
     */
    public JSValue get(PropertyKey key, JSValue receiver) {
        return getWithReceiver(key, receiver, 0);
    }

    public JSConstructorType getConstructorType() {
        return constructorType;
    }

    /**
     * Get the constructor type internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public JSContext getContext() {
        return context;
    }

    /**
     * Get a V8-style object description for error messages.
     * Returns format that matches V8 error messages.
     *
     * @param forDelete if true, returns format for delete errors, otherwise for assignment errors
     */
    private String getObjectDescriptionForError(boolean forDelete) {
        // For functions, use format: "function 'functionString'"
        if (this instanceof JSFunction func) {
            return "function '" + func + "'";
        }

        // For objects, format depends on error type:
        // Delete errors: "#<ConstructorName>"
        // Assignment errors: "object '#<ConstructorName>'"
        String prefix = forDelete ? "" : "object '";
        String suffix = forDelete ? "" : "'";

        // Get constructor name if available
        JSValue constructor = get("constructor");
        if (constructor instanceof JSFunction constructorFunc) {
            JSValue name = constructorFunc.get(PropertyKey.NAME);
            if (name instanceof JSString nameStr && !nameStr.value().isEmpty()) {
                return prefix + "#<" + nameStr.value() + ">" + suffix;
            }
        }

        // Default to Object
        return prefix + "#<Object>" + suffix;
    }

    private List<PropertyKey> getOrderedOwnKeys(boolean enumerableOnly) {
        if (sparseProperties == null && shape.getDeletedPropCount() == 0) {
            List<PropertyKey> fastPathKeys = null;
            int propertyCount = shape.getPropertyCount();
            boolean fastPathEligible = true;
            for (int index = 0; index < propertyCount; index++) {
                PropertyKey propertyKey = shape.getPropertyKeyAt(index);
                if (propertyKey == null || propertyKey.isSymbol() || propertyKey.toArrayIndex() >= 0) {
                    fastPathEligible = false;
                    break;
                }
                if (enumerableOnly) {
                    PropertyDescriptor descriptor = shape.getDescriptorAt(index);
                    if (descriptor == null || !descriptor.isEnumerable()) {
                        fastPathEligible = false;
                        break;
                    }
                }
                if (fastPathKeys == null) {
                    fastPathKeys = new ArrayList<>(propertyCount);
                }
                fastPathKeys.add(propertyKey);
            }
            if (fastPathEligible) {
                return fastPathKeys != null ? fastPathKeys : new ArrayList<>(0);
            }
        }

        List<PropertyKey> stringKeys = new ArrayList<>();
        List<PropertyKey> symbolKeys = new ArrayList<>();
        List<Map.Entry<Long, PropertyKey>> numericKeys = new ArrayList<>();
        Set<Long> seenNumericIndices = new HashSet<>();
        Set<PropertyKey> seenPropertyKeys = new HashSet<>();

        int propCount = shape.getPropertyCount();
        for (int i = 0; i < propCount; i++) {
            PropertyKey shapeKey = shape.getPropertyKeyAt(i);
            if (shapeKey == null) {
                continue; // deleted property
            }
            if (isPrivateSymbolKey(shapeKey)) {
                continue;
            }
            if (enumerableOnly) {
                PropertyDescriptor descriptor = shape.getDescriptorAt(i);
                if (descriptor == null || !descriptor.isEnumerable()) {
                    continue;
                }
            }
            long index = shapeKey.toArrayIndex();
            if (index >= 0) {
                if (seenNumericIndices.add(index)) {
                    numericKeys.add(Map.entry(index, shapeKey));
                }
            } else if (shapeKey.isSymbol()) {
                if (seenPropertyKeys.add(shapeKey)) {
                    symbolKeys.add(shapeKey);
                }
            } else if (seenPropertyKeys.add(shapeKey)) {
                stringKeys.add(shapeKey);
            }
        }

        if (sparseProperties != null) {
            for (Integer index : sparseProperties.keySet()) {
                long unsignedIndex = Integer.toUnsignedLong(index);
                if (seenNumericIndices.add(unsignedIndex)) {
                    numericKeys.add(Map.entry(unsignedIndex, PropertyKey.fromIndex(index)));
                }
            }
        }

        numericKeys.sort(Comparator.comparingLong(Map.Entry::getKey));

        List<PropertyKey> ordered = new ArrayList<>(numericKeys.size() + stringKeys.size() + symbolKeys.size());
        for (Map.Entry<Long, PropertyKey> entry : numericKeys) {
            ordered.add(entry.getValue());
        }
        ordered.addAll(stringKeys);
        ordered.addAll(symbolKeys);
        return ordered;
    }

    /**
     * Get the property descriptor for a property.
     */
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        int offset = getOwnPropertyOffset(key);
        if (offset >= 0) {
            PropertyDescriptor desc = shape.getDescriptorAt(offset);
            // Sync descriptor value with current propertyValues for data properties.
            // propertyValues[offset] is the source of truth for current values,
            // while the descriptor may hold a stale value from initialization.
            if (desc != null && desc.isDataDescriptor()) {
                if (offset < propertyValues.length && propertyValues[offset] != null) {
                    desc.setValue(propertyValues[offset]);
                }
            }
            return desc;
        }

        long arrayIndex = key.toArrayIndex();
        if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            JSValue sparseValue = sparseProperties.get((int) arrayIndex);
            if (sparseValue != null) {
                return PropertyDescriptor.defaultData(sparseValue);
            }
        }

        return null;
    }

    /**
     * Get all own property keys (not including prototype chain).
     */
    public List<PropertyKey> getOwnPropertyKeys() {
        return getOrderedOwnKeys(false);
    }

    /**
     * Get the offset of an own property in the shape, handling integer/string key equivalence.
     * Returns -1 if the property is not found. This avoids the redundant scans
     * of getOwnShapeKey + getPropertyOffset by returning the offset directly.
     */
    protected int getOwnPropertyOffset(PropertyKey key) {
        int offset = shape.getPropertyOffset(key);
        if (offset >= 0) {
            return offset;
        }
        if (key.isSymbol()) {
            return -1;
        }
        if (key.isIndex()) {
            return shape.getPropertyOffsetByIndexKey(key.toPropertyString());
        }
        int index = key.toIndex();
        if (index < 0) {
            return -1;
        }
        return shape.getPropertyOffsetByIndexKey(index);
    }

    protected PropertyKey getOwnShapeKey(PropertyKey key) {
        if (shape.hasProperty(key)) {
            return key;
        }
        if (key.isSymbol()) {
            return null;
        }
        if (key.isIndex()) {
            String propertyName = key.toPropertyString();
            return shape.getPropertyOffsetByIndexKey(propertyName) >= 0
                    ? PropertyKey.fromString(propertyName)
                    : null;
        }
        int index = key.toIndex();
        if (index < 0) {
            return null;
        }
        return shape.getPropertyOffsetByIndexKey(index) >= 0 ? PropertyKey.fromIndex(index) : null;
    }

    /**
     * Get the [[PrimitiveValue]] internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public JSValue getPrimitiveValue() {
        return primitiveValue;
    }

    public JSObject getPrototype() {
        return prototype;
    }

    /**
     * Internal get method with receiver tracking for prototype chain getter invocation.
     * Protected to allow JSProxy to override with proper trap handling.
     */
    protected JSValue getWithReceiver(PropertyKey key, JSValue receiver, int depth) {
        long arrayIndex = key.toArrayIndex();
        if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            JSValue sparseValue = sparseProperties.get((int) arrayIndex);
            if (sparseValue != null) {
                return sparseValue;
            }
        }

        // String primitive wrapper: return character at numeric index
        if (primitiveValue instanceof JSString str && arrayIndex >= 0) {
            String s = str.value();
            if (arrayIndex < s.length()) {
                return new JSString(String.valueOf(s.charAt((int) arrayIndex)));
            }
        }

        // Look in own properties
        int offset = getOwnPropertyOffset(key);
        if (offset >= 0) {
            PropertyDescriptor desc = shape.getDescriptorAt(offset);
            if (desc != null && desc.isAccessorDescriptor()) {
                JSFunction getter = desc.getGetter();
                if (getter != null) {
                    JSContext propertyAccessContext = JSContext.getCurrentExecutionContext();
                    if (propertyAccessContext == null) {
                        propertyAccessContext = this.context;
                    }
                    try {
                        // Call the getter with the ORIGINAL receiver as 'this', not the prototype
                        JSValue result = getter.call(propertyAccessContext, receiver, JSValue.NO_ARGS);
                        // Check if getter threw an exception - return the error value or undefined
                        if (propertyAccessContext.hasPendingException()) {
                            return result != null ? result : propertyAccessContext.getPendingException();
                        }
                        return result;
                    } catch (JSVirtualMachineException e) {
                        // Getter threw - convert to pending exception so callers can handle it
                        JSValue exception = e.getJsError() != null ? e.getJsError()
                                : e.getJsValue() != null ? e.getJsValue()
                                : propertyAccessContext.throwError(e.getMessage());
                        propertyAccessContext.setPendingException(exception);
                        return JSUndefined.INSTANCE;
                    }
                }
                // Accessor property without getter (or without context) reads as undefined.
                return JSUndefined.INSTANCE;
            }
            // Regular property with value
            return propertyValues[offset];
        }

        // Legacy SpiderMonkey-style function.caller/arguments extension.
        // Function.prototype itself exposes %ThrowTypeError% accessors, but regular
        // non-strict functions still report dynamic caller/arguments information.
        if (key.isString()
                && this instanceof JSFunction currentFunction
                && isLegacyFunctionPropertyAccessible(currentFunction)) {
            String propertyName = key.asString();
            if (JSKeyword.ARGUMENTS.equals(propertyName)) {
                JSContext propertyAccessContext = JSContext.getCurrentExecutionContext();
                if (propertyAccessContext == null) {
                    propertyAccessContext = this.context;
                }
                StackFrame currentFunctionFrame = findInnermostFrameForFunction(propertyAccessContext, currentFunction);
                if (currentFunctionFrame == null) {
                    return JSNull.INSTANCE;
                }
                JSArguments argumentsObject = currentFunctionFrame.getArgumentsObject(false);
                if (argumentsObject == null) {
                    argumentsObject = new JSArguments(propertyAccessContext, currentFunctionFrame.getArguments(), true, null);
                    propertyAccessContext.transferPrototype(argumentsObject, JSObject.NAME);
                    currentFunctionFrame.setArgumentsObject(false, argumentsObject);
                }
                return argumentsObject;
            }
            if ("caller".equals(propertyName)) {
                JSContext propertyAccessContext = JSContext.getCurrentExecutionContext();
                if (propertyAccessContext == null) {
                    propertyAccessContext = this.context;
                }
                StackFrame currentFunctionFrame = findInnermostFrameForFunction(propertyAccessContext, currentFunction);
                if (currentFunctionFrame == null) {
                    return JSNull.INSTANCE;
                }
                StackFrame callerFrame = currentFunctionFrame.getCaller();
                while (callerFrame != null && shouldSkipLegacyCallerFrame(callerFrame)) {
                    callerFrame = callerFrame.getCaller();
                }
                if (callerFrame == null || callerFrame.getCaller() == null) {
                    return JSNull.INSTANCE;
                }
                JSFunction callerFunction = callerFrame.getFunction();
                if (!isLegacyFunctionPropertyAccessible(callerFunction)) {
                    return JSNull.INSTANCE;
                }
                return callerFunction;
            }
        }

        // Look in prototype chain with depth-based cycle detection
        if (prototype != null && depth < MAX_PROTOTYPE_DEPTH) {
            return prototype.getWithReceiver(key, receiver, depth + 1);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Get a property value with an explicit receiver for getter invocation.
     * Used by Reflect.get and super property access to pass a different receiver
     * than the target. Accepts JSValue to support primitive receivers (per ES spec,
     * super property access should not box the receiver).
     */
    public JSValue getWithReceiver(PropertyKey key, JSValue receiver) {
        return getWithReceiver(key, receiver, 0);
    }

    /**
     * Check if object has a property (including prototype chain).
     */
    public boolean has(String propertyName) {
        return has(PropertyKey.fromString(propertyName));
    }

    /**
     * Check if object has a property by key (including prototype chain).
     */
    public boolean has(PropertyKey key) {
        if (hasOwnProperty(key)) {
            return true;
        }
        if (prototype != null) {
            return prototype.has(key);
        }
        return false;
    }

    /**
     * Check if object has an own property.
     */
    public boolean hasOwnProperty(String propertyName) {
        return hasOwnProperty(PropertyKey.fromString(propertyName));
    }

    /**
     * Check if object has an own property by key.
     */
    public boolean hasOwnProperty(PropertyKey key) {
        if (getOwnPropertyOffset(key) >= 0) {
            return true;
        }
        long arrayIndex = key.toArrayIndex();
        if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            return sparseProperties.containsKey((int) arrayIndex);
        }
        return false;
    }

    /**
     * Check if a key has a property in the shape (handles integer/string key equivalence).
     */
    protected boolean hasOwnShapeProperty(PropertyKey key) {
        return getOwnPropertyOffset(key) >= 0;
    }

    /**
     * Initialize properties in bulk on a freshly created object with no existing properties.
     * This is more efficient than calling defineProperty repeatedly because it avoids
     * the O(N²) cost of incremental shape growth (linear scans + array copies per property).
     * The keys, descriptors, and values arrays must all have the same length.
     */
    public void initProperties(PropertyKey[] keys, PropertyDescriptor[] descriptors, JSValue[] values) {
        this.shape = new JSShape(keys, descriptors);
        this.propertyValues = values;
    }

    /**
     * Check if this object is extensible.
     * ES5.1 15.2.3.13
     */
    public boolean isArrayObject() {
        return arrayObject;
    }

    public boolean isExtensible() {
        return extensible;
    }

    /**
     * Check if this object is frozen.
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Check if this object has the [IsHTMLDDA] internal slot set.
     */
    public boolean isHTMLDDA() {
        return htmlDDA;
    }

    private boolean isLegacyFunctionPropertyAccessible(JSFunction currentFunction) {
        if (currentFunction instanceof JSBytecodeFunction bytecodeFunction) {
            return !bytecodeFunction.isStrict()
                    && bytecodeFunction.isConstructor()
                    && !bytecodeFunction.isArrow()
                    && !bytecodeFunction.isAsync()
                    && !bytecodeFunction.isGenerator();
        }
        return false;
    }

    private boolean isPrivateSymbolKey(PropertyKey key) {
        if (key == null || !key.isSymbol()) {
            return false;
        }
        JSSymbol symbol = key.asSymbol();
        if (symbol == null) {
            return false;
        }
        String description = symbol.getDescription();
        return description != null && description.startsWith("#");
    }

    /**
     * Check if this object is sealed.
     */
    public boolean isSealed() {
        return sealed;
    }

    public boolean isSuperConstructorCalled() {
        return superConstructorCalled;
    }

    public void markSuperConstructorCalled() {
        this.superConstructorCalled = true;
    }

    /**
     * Get all own property keys.
     */
    public PropertyKey[] ownPropertyKeys() {
        return getOrderedOwnKeys(false).toArray(new PropertyKey[0]);
    }

    /**
     * Prevent new properties from being added to this object.
     * ES5.1 15.2.3.10
     */
    public void preventExtensions() {
        extensible = false;
    }

    /**
     * Attempt to prevent extensions and report success.
     * Ordinary objects always succeed.
     */
    public boolean preventExtensionsWithResult() {
        preventExtensions();
        return true;
    }

    /**
     * Seal this object.
     * Prevents adding new properties and deleting existing properties.
     * Existing properties can still be modified.
     */
    public void seal() {
        sealed = true;
        extensible = false; // Sealed objects are not extensible
    }

    /**
     * Set a property value by string name.
     */
    public void set(String propertyName, JSValue value) {
        set(PropertyKey.fromString(propertyName), value);
    }

    /**
     * Set a property value by integer index.
     */
    public void set(int index, JSValue value) {
        // Use sparse storage for large indices
        if (index >= 100 || (sparseProperties != null && sparseProperties.containsKey(index))) {
            if (sparseProperties == null) {
                sparseProperties = new HashMap<>();
            }
            sparseProperties.put(index, value);
        } else {
            set(PropertyKey.fromIndex(index), value);
        }
    }

    /**
     * Set a property value by property key.
     */
    public void set(PropertyKey key, JSValue value) {
        setInternal(key, value, this, true);
    }

    // Prototype chain

    /**
     * Set the constructor type internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public void setConstructorType(JSConstructorType type) {
        constructorType = type;
    }

    /**
     * Set the [IsHTMLDDA] internal slot.
     */
    public void setHTMLDDA(boolean htmlDDA) {
        this.htmlDDA = htmlDDA;
    }

    // Object integrity levels (ES5)

    /**
     * Mark this object as an immutable prototype exotic object.
     * Per ES2024 9.4.7, [[SetPrototypeOf]] always returns false unless
     * the new prototype is the same as the current one.
     * Used for Object.prototype.
     */
    public void setImmutablePrototype() {
        immutablePrototype = true;
    }

    private boolean setInternal(PropertyKey key, JSValue value, JSObject receiver, boolean throwOnFailure) {
        // Check if property already exists
        int offset = getOwnPropertyOffset(key);
        if (offset >= 0) {
            PropertyDescriptor descriptor = shape.getDescriptorAt(offset);

            if (descriptor != null && descriptor.isAccessorDescriptor()) {
                JSFunction setter = descriptor.getSetter();
                if (setter != null) {
                    boolean hadPendingException = context.hasPendingException();
                    setter.call(context, receiver, new JSValue[]{value});
                    return hadPendingException || !context.hasPendingException();
                }
                return failSet(key, throwOnFailure);
            }

            if (descriptor == null || !descriptor.isWritable() || frozen) {
                return failSet(key, throwOnFailure);
            }

            if (receiver != this) {
                return setOnReceiver(key, value, receiver, throwOnFailure);
            }

            propertyValues[offset] = value;
            return true;
        }

        // Property doesn't exist on own object - walk prototype chain for setters/writability checks.
        // Per ES spec 9.1.9.1 OrdinarySet, if a prototype is an exotic object, delegate to its [[Set]].
        Set<JSObject> visited = new HashSet<>();
        visited.add(this);
        JSObject proto = prototype;
        while (proto != null && !visited.contains(proto)) {
            if (proto instanceof JSProxy proxy) {
                return proxy.setWithResult(key, value, receiver);
            }
            // TypedArray has exotic [[Set]] for canonical numeric index keys
            if (proto instanceof JSTypedArray typedArray) {
                boolean result = typedArray.setWithResult(key, value, (JSValue) receiver);
                if (!result && throwOnFailure && !context.hasPendingException()) {
                    return failSet(key, true);
                }
                return result;
            }
            visited.add(proto);
            int protoOffset = proto.getOwnPropertyOffset(key);
            if (protoOffset >= 0) {
                PropertyDescriptor protoDescriptor = proto.shape.getDescriptorAt(protoOffset);
                if (protoDescriptor != null && protoDescriptor.isAccessorDescriptor()) {
                    JSFunction setter = protoDescriptor.getSetter();
                    if (setter != null) {
                        boolean hadPendingException = context.hasPendingException();
                        setter.call(context, receiver, new JSValue[]{value});
                        return hadPendingException || !context.hasPendingException();
                    }
                    return failSet(key, throwOnFailure);
                }
                if (protoDescriptor != null && !protoDescriptor.isWritable()) {
                    return failSet(key, throwOnFailure);
                }
                break;
            }
            proto = proto.prototype;
        }

        return setOnReceiver(key, value, receiver, throwOnFailure);
    }

    private boolean setOnReceiver(PropertyKey key, JSValue value, JSObject receiver, boolean throwOnFailure) {
        // ES2024 OrdinarySetWithOwnDescriptor steps 2c-2e:
        // Use the virtual [[GetOwnPropertyDescriptor]] and [[DefineOwnProperty]]
        // methods on the receiver so that proxy traps are correctly invoked when
        // the receiver is a Proxy object.

        // Step 2c: Let existingDescriptor be ? Receiver.[[GetOwnPropertyDescriptor]](P).
        boolean hadPendingException = context.hasPendingException();
        PropertyDescriptor existingDescriptor = receiver.getOwnPropertyDescriptor(key);
        if (!hadPendingException && context.hasPendingException()) {
            return false;
        }

        if (existingDescriptor != null) {
            // Step 2d.i: If IsAccessorDescriptor(existingDescriptor), return false.
            if (existingDescriptor.isAccessorDescriptor()) {
                return failSet(key, throwOnFailure);
            }
            // Step 2d.ii: If existingDescriptor.[[Writable]] is false, return false.
            if (!existingDescriptor.isWritable()) {
                return failSet(key, throwOnFailure);
            }
            // Step 2d.iii-iv: Let valueDesc be { [[Value]]: V }.
            // Return ? Receiver.[[DefineOwnProperty]](P, valueDesc).
            PropertyDescriptor valueDescriptor = new PropertyDescriptor();
            valueDescriptor.setValue(value);
            return receiver.defineProperty(key, valueDescriptor);
        }

        // Step 2e: CreateDataProperty(Receiver, P, V).
        // Check the extensible field directly rather than calling the virtual
        // isExtensible() method, because for Proxy receivers that would
        // trigger an isExtensible trap not required by the spec here.
        // For normal objects the field is authoritative; for proxies the
        // wrapper field is always true so the defineProperty trap handles
        // extensibility validation instead.
        if (!receiver.extensible) {
            if (throwOnFailure && context.isStrictMode()) {
                context.throwTypeError("Cannot add property " + key.toPropertyString() + ", object is not extensible");
            }
            return false;
        }
        return receiver.defineProperty(key, value, PropertyDescriptor.DataState.All);
    }

    /**
     * Set the [[PrimitiveValue]] internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public void setPrimitiveValue(JSValue value) {
        this.primitiveValue = value;
    }

    /**
     * Directly set a private field value on this object, bypassing ordinary [[Set]]
     * extensibility/frozen checks. Private elements are internal slots and are not
     * affected by Object.freeze()/Object.seal().
     */
    public void setPrivatePropertyDirect(PropertyKey key, JSValue value) {
        int offset = getOwnPropertyOffset(key);
        if (offset >= 0) {
            propertyValues[offset] = value;
            PropertyDescriptor descriptor = shape.getDescriptorAt(offset);
            if (descriptor != null && descriptor.isDataDescriptor()) {
                descriptor.setValue(value);
            }
            return;
        }

        long arrayIndex = key.toArrayIndex();
        if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            int sparseIndex = (int) arrayIndex;
            if (sparseProperties.containsKey(sparseIndex)) {
                sparseProperties.put(sparseIndex, value);
                return;
            }
        }

        set(key, value);
    }

    public void setPrototype(JSObject prototype) {
        this.prototype = prototype;
    }

    /**
     * Set the prototype following ES spec invariants (QuickJS JS_SetPrototypeInternal).
     * Checks: same prototype (no-op), extensibility, and circular chain.
     *
     * @param proto The new prototype (null for Object.prototype = null)
     * @return SetPrototypeResult indicating success or failure reason
     */
    public SetPrototypeResult setPrototypeChecked(JSObject proto) {
        // Same prototype - no change needed
        if (this.prototype == proto) {
            return SetPrototypeResult.SUCCESS;
        }

        // Immutable prototype exotic objects (e.g., Object.prototype) always reject
        if (this.immutablePrototype) {
            return SetPrototypeResult.NOT_EXTENSIBLE;
        }

        // Non-extensible objects cannot have their prototype changed
        if (!this.extensible) {
            return SetPrototypeResult.NOT_EXTENSIBLE;
        }

        // Check for circular prototype chain
        // ES2024 10.1.2 OrdinarySetPrototypeOf step 8
        if (proto != null) {
            JSObject p = proto;
            while (p != null) {
                if (p == this) {
                    return SetPrototypeResult.CIRCULAR;
                }
                // Step 8.c: If p is not an ordinary object, set done to true.
                if (p instanceof JSProxy) {
                    break;
                }
                p = p.getPrototype();
            }
        }

        this.prototype = proto;
        return SetPrototypeResult.SUCCESS;
    }

    private boolean setWithPrimitiveReceiver(PropertyKey key, JSValue value, JSValue primitiveReceiver) {
        // Walk the prototype chain of this (the boxed object) looking for setters.
        // Per QuickJS JS_SetPropertyInternal: when this_obj is not an object,
        // traverse the prototype chain. If a Proxy is in the chain, delegate
        // to its set trap. If a setter is found, call it with the primitive receiver.
        // Otherwise return false (caller throws TypeError in strict mode).
        Set<JSObject> visited = new HashSet<>();
        JSObject current = this;
        while (current != null && !visited.contains(current)) {
            // Proxy in the chain: delegate to its set trap with primitive receiver
            if (current instanceof JSProxy proxy) {
                return proxy.proxySetWithReceiver(key, value, primitiveReceiver);
            }
            visited.add(current);
            int propertyOffset = current.getOwnPropertyOffset(key);
            if (propertyOffset >= 0) {
                PropertyDescriptor descriptor = current.shape.getDescriptorAt(propertyOffset);
                if (descriptor != null && descriptor.isAccessorDescriptor()) {
                    JSFunction setter = descriptor.getSetter();
                    if (setter != null) {
                        boolean hadPendingException = context != null && context.hasPendingException();
                        setter.call(context, primitiveReceiver, new JSValue[]{value});
                        return hadPendingException || (context != null && !context.hasPendingException());
                    }
                    // Accessor without setter
                    return false;
                }
                if (descriptor != null && !descriptor.isWritable()) {
                    return false;
                }
                // Writable data property found on prototype - would need to define on receiver,
                // but receiver is not an object, so return false
                return false;
            }
            current = current.prototype;
        }
        // No property found in prototype chain - would need to define on receiver,
        // but receiver is not an object, so return false
        return false;
    }

    public boolean setWithReceiverAndException(PropertyKey key, JSValue value, JSObject receiver) {
        return setInternal(key, value, receiver, true);
    }

    public boolean setWithResult(PropertyKey key, JSValue value) {
        return setInternal(key, value, this, false);
    }

    public boolean setWithResult(PropertyKey key, JSValue value, JSObject receiver) {
        return setInternal(key, value, receiver, false);
    }

    /**
     * Set with result, accepting any JSValue as receiver.
     * Per ES spec, [[Set]](P, V, Receiver) accepts any ECMAScript language value as Receiver.
     * When receiver is not an object, OrdinarySet returns false (cannot create properties on non-objects).
     * Subclasses (e.g. TypedArray) may override for spec-specific behavior.
     */
    public boolean setWithResult(PropertyKey key, JSValue value, JSValue receiver) {
        if (receiver instanceof JSObject objReceiver) {
            return setWithResult(key, value, objReceiver);
        }
        // Per QuickJS JS_SetPropertyInternal: when receiver (this_obj) is not an object,
        // traverse the prototype chain looking for setters. If a setter is found,
        // call it with the primitive receiver. Otherwise return false.
        return setWithPrimitiveReceiver(key, value, receiver);
    }

    private boolean shouldSkipLegacyCallerFrame(StackFrame stackFrame) {
        JSFunction stackFunction = stackFrame.getFunction();
        if (stackFunction instanceof JSNativeFunction nativeFunction) {
            String functionName = nativeFunction.getName();
            return JSKeyword.EVAL.equals(functionName);
        }
        if (stackFunction instanceof JSBytecodeFunction bytecodeFunction) {
            String functionName = bytecodeFunction.getName();
            if ("<eval>".equals(functionName)) {
                return true;
            } else {
                String importMetaFilename = bytecodeFunction.getImportMetaFilename();
                return importMetaFilename != null
                        && !importMetaFilename.isEmpty()
                        && importMetaFilename.equals(functionName);
            }
        }
        return false;
    }

    // JSValue implementation

    @Override
    public Object toJavaObject() {
        Map<String, Object> objMap = new LinkedHashMap<>();
        // Get all own property keys in order (shaped properties first, then sparse)
        List<PropertyKey> keys = getOwnPropertyKeys();
        for (PropertyKey key : keys) {
            Optional.of(get(key))
                    .map(JSValue::toJavaObject)
                    .ifPresent(valueObject -> objMap.put(key.toPropertyString(), valueObject));
        }
        return objMap;
    }

    @Override
    public String toString() {
        return "[object Object]";
    }

    @Override
    public JSValueType type() {
        return JSValueType.OBJECT;
    }

    public enum SetPrototypeResult {
        SUCCESS,
        NOT_EXTENSIBLE,
        CIRCULAR
    }
}
