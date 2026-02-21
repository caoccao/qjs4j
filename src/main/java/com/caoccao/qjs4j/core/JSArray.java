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

import java.util.*;

/**
 * Represents a JavaScript Array object.
 * Based on QuickJS array implementation.
 * <p>
 * Uses dual storage strategy:
 * - Dense array for consecutive indices [0, 1, 2, ...]
 * - Sparse map (inherited from JSObject) for gaps or large indices
 * <p>
 * Automatically switches between dense and sparse based on usage patterns.
 */
public final class JSArray extends JSObject {
    public static final int INITIAL_CAPACITY = 8;
    public static final String NAME = "Array";
    private static final long MAX_ARRAY_INDEX = 0xFFFF_FFFEL; // 2^32 - 2
    private static final long MAX_ARRAY_LENGTH = 0xFFFF_FFFFL; // 2^32 - 1
    private static final int MAX_DENSE_SIZE = 10000;
    private static final double UINT32_MAX_DOUBLE = 4_294_967_295d;
    private static final double UINT32_MODULO = 4_294_967_296d;
    private JSValue[] denseArray;
    private long length;

    /**
     * Create an empty array.
     */
    public JSArray() {
        this(0);
    }

    /**
     * Create an array with a specific initial length.
     */
    public JSArray(long length) {
        this(length, INITIAL_CAPACITY);
    }

    /**
     * Create an array with a specific initial length.
     */
    public JSArray(long length, int capacity) {
        super();
        this.length = length;
        capacity = Math.min(capacity, INITIAL_CAPACITY);
        this.denseArray = new JSValue[capacity];
        // Mark as array class (equivalent to QuickJS class_id == JS_CLASS_ARRAY)
        this.arrayObject = true;
        initializeLengthProperty();
    }

    /**
     * Create an array from values.
     */
    public JSArray(JSValue... values) {
        super();
        this.length = values.length;
        this.denseArray = Arrays.copyOf(values, Math.max(values.length, INITIAL_CAPACITY));
        // Mark as array class (equivalent to QuickJS class_id == JS_CLASS_ARRAY)
        this.arrayObject = true;
        initializeLengthProperty();
    }

    /**
     * Array constructor implementation.
     * new Array() - creates an empty array
     * new Array(len) - creates an array with specified length (if len is a number)
     * new Array(element0, element1, ..., elementN) - creates an array with the given elements
     * <p>
     * Based on ES2020 22.1.1.1
     */
    public static JSArray create(JSContext context, JSValue... args) {
        JSArray array = context.createJSArray();

        // Special case: single numeric argument sets array length
        if (args.length == 1 && args[0] instanceof JSNumber num) {
            Long length = toArrayLengthFromNumber(num.value());
            if (length == null) {
                context.throwRangeError("Invalid array length");
                return context.createJSArray();
            }
            array.setLength(length);
            return array;
        }

        // Multiple arguments or non-numeric single argument: create array with elements
        for (JSValue arg : args) {
            array.push(arg);
        }

        return array;
    }

    private static long getArrayIndex(PropertyKey key) {
        if (key.isIndex()) {
            int index = key.asIndex();
            return index >= 0 ? index : -1;
        }
        if (key.isString()) {
            return parseArrayIndex(key.asString());
        }
        return -1;
    }

    private static long parseArrayIndex(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        if ("0".equals(value)) {
            return 0;
        }
        if (value.charAt(0) == '0') {
            return -1;
        }

        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            result = result * 10 + (c - '0');
            if (result > MAX_ARRAY_INDEX) {
                return -1;
            }
        }
        return result;
    }

    private static Long toArrayLengthFromNumber(double value) {
        if (!(value >= 0 && value <= UINT32_MAX_DOUBLE)) {
            return null;
        }
        long length = (long) value;
        return ((double) length == value) ? length : null;
    }

    private static long toUint32(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0) {
            return 0;
        }
        double integer = value > 0 ? Math.floor(value) : Math.ceil(value);
        double modulo = integer % UINT32_MODULO;
        if (modulo < 0) {
            modulo += UINT32_MODULO;
        }
        return (long) modulo;
    }

    @Override
    public boolean defineOwnProperty(PropertyKey key, PropertyDescriptor descriptor, JSContext context) {
        // Per ES spec ArraySetLength / QuickJS JS_DefineProperty:
        // When defining "length" with a value, coerce BEFORE descriptor validation
        if (key.isString() && "length".equals(key.asString()) && descriptor.hasValue() && context != null) {
            Long newLength = toArrayLengthForLengthProperty(context, descriptor.getValue());
            if (newLength == null) {
                return false; // coercion error
            }
            // Replace raw value with coerced number; re-check property state
            // (coercion callback may have changed writable flag)
            descriptor.setValue(JSNumber.of(newLength));

            PropertyDescriptor oldLenDesc = getOwnPropertyDescriptor(key);
            if (oldLenDesc != null && !oldLenDesc.isWritable()) {
                // Current writable is false (may have been changed during coercion)
                if (descriptor.hasWritable() && descriptor.isWritable()) {
                    // Cannot change writable from false to true
                    return false;
                }
                long oldLen = this.length;
                if (newLength != oldLen) {
                    return false;
                }
                // Value is same, no writable change needed - just apply the descriptor
                super.defineProperty(key, descriptor);
                return true;
            }

            // Apply the length change
            setLength(newLength);
            // Apply any other descriptor attributes (e.g., writable: false)
            super.defineProperty(key, descriptor);
            return true;
        }
        // Per ES spec 10.4.2.1 [[DefineOwnProperty]] / QuickJS JS_CreateProperty:
        // When defining a property at an array index >= current length,
        // update the length to index + 1 (if length is writable).
        long index = getArrayIndex(key);
        if (index >= 0 && index >= this.length) {
            if (!isLengthWritable()) {
                if (context != null) {
                    context.throwTypeError("Cannot add property " + index + ", array length is not writable");
                }
                return false;
            }
            boolean result = super.defineOwnProperty(key, descriptor, context);
            if (result) {
                setLength(index + 1);
            }
            return result;
        }
        return super.defineOwnProperty(key, descriptor, context);
    }

    @Override
    public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        long index = getArrayIndex(key);
        if (index >= 0 && index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            // Clear dense array entry so shape-based property takes precedence
            if (intIndex < denseArray.length) {
                denseArray[intIndex] = null;
            }
        }
        super.defineProperty(key, descriptor);
    }

    @Override
    public boolean delete(PropertyKey key, JSContext context) {
        long index = getArrayIndex(key);
        if (index < 0) {
            return super.delete(key, context);
        }

        if (sealed || frozen) {
            PropertyKey stringKey = key.isString() ? key : PropertyKey.fromString(Long.toString(index));
            return super.delete(stringKey, context);
        }

        if (index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            if (intIndex < denseArray.length && denseArray[intIndex] != null) {
                denseArray[intIndex] = null;
                return true;
            }
            if (sparseProperties != null && sparseProperties.remove(intIndex) != null) {
                return true;
            }
        }

        return super.delete(key, context);
    }

    /**
     * Ensure dense array has sufficient capacity.
     */
    private void ensureDenseCapacity(int requiredCapacity) {
        if (requiredCapacity <= denseArray.length) {
            return;
        }

        int newCapacity = Math.max(denseArray.length * 2, requiredCapacity);
        newCapacity = Math.min(newCapacity, MAX_DENSE_SIZE);

        denseArray = Arrays.copyOf(denseArray, newCapacity);
    }

    @Override
    public PropertyKey[] enumerableKeys() {
        return getOwnPropertyKeysInternal(true).toArray(new PropertyKey[0]);
    }

    /**
     * Get element at index.
     */
    public JSValue get(long index) {
        if (index < 0) {
            return JSUndefined.INSTANCE;
        }

        // Own indexed elements cannot exist at or above length, but inherited
        // numeric properties must still be observable through prototype lookup.
        if (index < length && index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;

            // Try dense array first
            if (intIndex < denseArray.length && denseArray[intIndex] != null) {
                return denseArray[intIndex];
            }

            // Check sparse storage
            if (sparseProperties != null) {
                JSValue value = sparseProperties.get(intIndex);
                if (value != null) {
                    return value;
                }
            }
        }

        JSValue value = super.get(null, PropertyKey.fromString(Long.toString(index)));
        if (!(value instanceof JSUndefined)) {
            return value;
        }

        return JSUndefined.INSTANCE;
    }

    @Override
    public JSValue get(int index) {
        return get((long) index);
    }

    /**
     * Override get by PropertyKey to handle array indices.
     */
    @Override
    public JSValue get(PropertyKey key) {
        return get(null, key);
    }

    /**
     * Override get by PropertyKey with context to handle array indices.
     */
    @Override
    public JSValue get(JSContext context, PropertyKey key) {
        long index = getArrayIndex(key);
        if (index >= 0) {
            // Check shape for accessor properties first (e.g., Object.defineProperty with getter)
            PropertyDescriptor desc = super.getOwnPropertyDescriptor(key);
            if (desc != null && desc.hasGetter()) {
                return super.get(context, key);
            }
            // Try own dense/sparse storage
            if (index < length && index <= Integer.MAX_VALUE) {
                int intIndex = (int) index;
                if (intIndex < denseArray.length && denseArray[intIndex] != null) {
                    return denseArray[intIndex];
                }
                if (sparseProperties != null) {
                    JSValue value = sparseProperties.get(intIndex);
                    if (value != null) {
                        return value;
                    }
                }
            }
            // Not found in own storage — delegate to JSObject.get with context
            // so prototype chain getters are properly invoked
            return super.get(context, key);
        }

        // Otherwise, use the shape-based storage from JSObject
        return super.get(context, key);
    }

    /**
     * Override three-arg get so prototype chain lookups find dense array elements.
     * Without this, JSObject's three-arg get only checks shape/sparse properties,
     * missing JSArray's dense storage when this array is in a prototype chain.
     */
    @Override
    protected JSValue get(JSContext context, PropertyKey key, JSObject receiver) {
        long index = getArrayIndex(key);
        if (index >= 0 && index < length && index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            // Check dense array
            if (intIndex < denseArray.length && denseArray[intIndex] != null) {
                return denseArray[intIndex];
            }
            // Check sparse storage
            if (sparseProperties != null) {
                JSValue value = sparseProperties.get(intIndex);
                if (value != null) {
                    return value;
                }
            }
        }
        // Delegate to JSObject for shape properties, getters, and prototype chain
        return super.get(context, key, receiver);
    }

    /**
     * copyWithin helper:
     * resolves an indexed source value while honoring inherited indexed properties.
     * Mirrors QuickJS JS_TryGetPropertyInt64 lookup shape for this use case.
     */
    public JSValue getDense(JSContext context, PropertyKey key, long index) {
        JSObject current = this;
        while (current != null) {
            if (current.hasOwnProperty(key)) {
                if (current instanceof JSArray currentArray && currentArray.hasElement(index)) {
                    return currentArray.get(index);
                }
                return current.getWithReceiver(key, context, this);
            }
            current = current.getPrototype();
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Get the array length.
     */
    public long getLength() {
        return length;
    }

    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        PropertyDescriptor descriptor = super.getOwnPropertyDescriptor(key);
        if (descriptor != null) {
            return descriptor;
        }

        long index = getArrayIndex(key);
        if (index < 0) {
            return null;
        }

        if (index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            if (intIndex < denseArray.length && denseArray[intIndex] != null) {
                return PropertyDescriptor.dataDescriptor(denseArray[intIndex], true, true, true);
            }
            if (sparseProperties != null) {
                JSValue sparseValue = sparseProperties.get(intIndex);
                if (sparseValue != null) {
                    return PropertyDescriptor.dataDescriptor(sparseValue, true, true, true);
                }
            }
        } else {
            PropertyDescriptor largeIndexDescriptor = super.getOwnPropertyDescriptor(PropertyKey.fromString(Long.toString(index)));
            return largeIndexDescriptor;
        }

        return null;
    }

    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        return getOwnPropertyKeysInternal(false);
    }

    private List<PropertyKey> getOwnPropertyKeysInternal(boolean enumerableOnly) {
        Map<Long, JSValue> indexedValues = new HashMap<>();
        long denseLimit = Math.min(length, denseArray.length);
        for (int i = 0; i < denseLimit; i++) {
            JSValue value = denseArray[i];
            if (value != null) {
                indexedValues.put((long) i, value);
            }
        }
        if (sparseProperties != null) {
            for (Map.Entry<Integer, JSValue> entry : sparseProperties.entrySet()) {
                long index = entry.getKey();
                if (index >= 0 && index < length) {
                    indexedValues.put(index, entry.getValue());
                }
            }
        }

        List<PropertyKey> keys = new ArrayList<>(indexedValues.size() + shape.getPropertyCount());
        indexedValues.keySet().stream()
                .sorted()
                .forEach(index -> keys.add(PropertyKey.fromString(Long.toString(index))));

        for (PropertyKey key : shape.getPropertyKeys()) {
            long index = getArrayIndex(key);
            if (index >= 0) {
                if (!indexedValues.containsKey(index)) {
                    PropertyDescriptor descriptor = super.getOwnPropertyDescriptor(key);
                    if (descriptor != null && (!enumerableOnly || descriptor.isEnumerable())) {
                        keys.add(PropertyKey.fromString(Long.toString(index)));
                    }
                }
                continue;
            }
            if (!enumerableOnly) {
                keys.add(key);
                continue;
            }
            PropertyDescriptor descriptor = super.getOwnPropertyDescriptor(key);
            if (descriptor != null && descriptor.isEnumerable()) {
                keys.add(key);
            }
        }

        return keys;
    }

    /**
     * Check whether an index has an own element (distinguishes holes from undefined values).
     */
    public boolean hasElement(long index) {
        if (index < 0 || index >= length) {
            return false;
        }

        if (index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            if (intIndex < denseArray.length && denseArray[intIndex] != null) {
                return true;
            }
            return sparseProperties != null && sparseProperties.containsKey(intIndex);
        }
        return super.hasOwnProperty(PropertyKey.fromString(Long.toString(index)));
    }

    @Override
    public boolean hasOwnProperty(PropertyKey key) {
        if (super.hasOwnProperty(key)) {
            return true;
        }

        long index = getArrayIndex(key);
        if (index < 0) {
            return false;
        }

        if (index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            return intIndex < denseArray.length && denseArray[intIndex] != null;
        }
        return super.hasOwnProperty(PropertyKey.fromString(Long.toString(index)));
    }

    /**
     * Initialize the "length" property as a special data property.
     */
    private void initializeLengthProperty() {
        // The length property is special - it's writable but not enumerable or configurable
        PropertyDescriptor lengthDesc = PropertyDescriptor.dataDescriptor(
                JSNumber.of(length),
                true,  // writable
                false, // not enumerable
                false  // not configurable
        );
        super.defineProperty(PropertyKey.LENGTH, lengthDesc);
    }

    /**
     * Check if array is dense (no holes).
     */
    public boolean isDense() {
        if (sparseProperties != null && !sparseProperties.isEmpty()) {
            return false;
        }

        for (int i = 0; i < Math.min(length, denseArray.length); i++) {
            if (denseArray[i] == null) {
                return false;
            }
        }

        return true;
    }

    private boolean isLengthWritable() {
        PropertyDescriptor descriptor = shape.getDescriptor(PropertyKey.LENGTH);
        return descriptor == null || descriptor.isWritable();
    }

    @Override
    public PropertyKey[] ownPropertyKeys() {
        return getOwnPropertyKeys().toArray(new PropertyKey[0]);
    }

    /**
     * Remove and return the last element.
     */
    public JSValue pop() {
        if (length == 0) {
            return JSUndefined.INSTANCE;
        }

        long lastIndex = length - 1;
        JSValue value = get(lastIndex);

        // Remove the element
        if (lastIndex < denseArray.length) {
            denseArray[(int) lastIndex] = null;
        } else if (lastIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            sparseProperties.remove((int) lastIndex);
        } else {
            super.delete(PropertyKey.fromString(Long.toString(lastIndex)), null);
        }

        setLength(lastIndex);
        return value;
    }

    /**
     * Add element to the end of the array.
     */
    public void push(JSValue value) {
        push(value, null);
    }

    /**
     * Add element to the end of the array with context for strict mode checking.
     */
    public void push(JSValue value, JSContext context) {
        long previousLength = length;
        set(length, value, context);

        // Array.prototype.push must throw when append fails because the array is not extensible
        // or length is read-only.
        if (context != null && !context.hasPendingException() && length == previousLength) {
            if (!extensible) {
                context.throwTypeError("Cannot add property " + previousLength + ", object is not extensible");
            } else if (!isLengthWritable()) {
                context.throwTypeError(
                        "Cannot assign to read only property 'length' of object '[object Array]'");
            }
        }
    }

    /**
     * Set element at index.
     */
    public void set(long index, JSValue value) {
        set(index, value, null);
    }

    /**
     * Set element at index with context for strict mode checking.
     */
    public void set(long index, JSValue value, JSContext context) {
        if (index < 0 || index > MAX_ARRAY_INDEX) {
            // Negative indices are treated as string properties
            super.set(PropertyKey.fromString(Long.toString(index)), value, context);
            return;
        }

        // Check if we're adding a new element beyond current length
        boolean isAddingNewElement = index >= length;

        // Check if array is not extensible/frozen before adding new elements
        if (isAddingNewElement && !extensible) {
            // In strict mode, throw TypeError when trying to add to non-extensible array
            if (context != null && context.isStrictMode()) {
                context.throwTypeError(
                        "Cannot add property " + index + ", object is not extensible");
            }
            return;
        }

        // Growing the array requires writable length.
        if (isAddingNewElement && !isLengthWritable()) {
            if (context != null && context.isStrictMode()) {
                context.throwTypeError(
                        "Cannot assign to read only property 'length' of object '[object Array]'");
            }
            return;
        }

        // Check if array is frozen when modifying existing elements
        if (!isAddingNewElement && frozen) {
            // In strict mode, throw TypeError when trying to modify frozen array
            if (context != null && context.isStrictMode()) {
                context.throwTypeError(
                        "Cannot assign to read only property '" + index + "' of object '[object Array]'");
            }
            return;
        }

        // If this index has a shape property (e.g., from Object.defineProperty),
        // delegate to JSObject.set which handles descriptor constraints (writable, accessor, etc.).
        // Following QuickJS which checks find_own_property before using the fast array path.
        if (!isAddingNewElement && index <= Integer.MAX_VALUE) {
            PropertyKey key = PropertyKey.fromString(Long.toString(index));
            if (super.hasOwnShapeProperty(key)) {
                super.set(key, value, context);
                return;
            }
        }

        // Extend length if necessary
        if (index >= length) {
            setLength(index + 1);
        }

        // Use dense array if index is within reasonable range
        if (index < MAX_DENSE_SIZE) {
            ensureDenseCapacity((int) index + 1);
            denseArray[(int) index] = value;
        } else if (index <= Integer.MAX_VALUE) {
            // Use sparse storage for large indices
            if (sparseProperties == null) {
                sparseProperties = new HashMap<>();
            }
            sparseProperties.put((int) index, value);
        } else {
            // Preserve semantics for very large array indices without integer overflow.
            super.set(PropertyKey.fromString(Long.toString(index)), value, context);
        }
    }

    @Override
    public void set(int index, JSValue value) {
        set((long) index, value);
    }

    /**
     * Override set by PropertyKey to handle array indices.
     */
    @Override
    public void set(PropertyKey key, JSValue value) {
        set(key, value, null);
    }

    /**
     * Override set by PropertyKey with context to handle array indices.
     */
    @Override
    public void set(PropertyKey key, JSValue value, JSContext context) {
        long index = getArrayIndex(key);
        if (index >= 0) {
            set(index, value, context);
        } else if (key.isString() && "length".equals(key.asString())) {
            // Per ES spec ArraySetLength / QuickJS set_array_length:
            // Coerce value BEFORE the read-only test (coercion can change writable flag)
            Long newLength = toArrayLengthForLengthProperty(context, value);
            if (newLength == null) {
                return; // coercion error (pending exception)
            }
            if (!isLengthWritable()) {
                // Delegate to JSObject.set which will handle the non-writable error
                super.set(key, JSNumber.of(newLength), context);
                return;
            }
            setLength(newLength);
        } else {
            // Otherwise, use the shape-based storage from JSObject
            super.set(key, value, context);
        }
    }

    /**
     * Set the array length.
     * When length is reduced, elements beyond the new length are deleted.
     */
    public void setLength(long newLength) {
        if (newLength < 0 || newLength > MAX_ARRAY_LENGTH) {
            throw new IllegalArgumentException("Invalid array length: " + newLength);
        }

        if (newLength < length) {
            // Truncate array - delete elements beyond new length
            int denseLimit = (int) Math.min(newLength, denseArray.length);
            for (int i = denseLimit; i < Math.min(length, denseArray.length); i++) {
                denseArray[i] = null;
            }

            // Remove sparse elements beyond new length
            if (sparseProperties != null) {
                sparseProperties.entrySet().removeIf(entry -> entry.getKey() >= newLength);
            }

            // Remove indexed string properties outside the new length range.
            for (PropertyKey key : shape.getPropertyKeys()) {
                long index = getArrayIndex(key);
                if (index >= newLength && index >= 0) {
                    super.delete(key, null);
                }
            }
        }

        this.length = newLength;
        updateLengthProperty();
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSContext context) {
        return setWithResult(key, value, context, this);
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSContext context, JSObject receiver) {
        long index = getArrayIndex(key);
        if (index >= 0 || !(key.isString() && "length".equals(key.asString()))) {
            // Non-length keys: delegate to JSObject
            return super.setWithResult(key, value, context, receiver);
        }
        // Per ES spec ArraySetLength / QuickJS set_array_length:
        // Coerce value BEFORE the read-only test
        Long newLength = toArrayLengthForLengthProperty(context, value);
        if (newLength == null) {
            return false; // coercion error (pending exception)
        }
        if (!isLengthWritable()) {
            return false;
        }
        setLength(newLength);
        return true;
    }

    /**
     * Remove and return the first element.
     */
    public JSValue shift() {
        if (length == 0) {
            return JSUndefined.INSTANCE;
        }

        JSValue first = get(0);

        // Shift all elements down
        shiftElementsLeft(0, 1);

        setLength(length - 1);
        return first;
    }

    /**
     * Shift elements left (for shift/splice operations).
     */
    private void shiftElementsLeft(int start, int count) {
        if (count <= 0) return;

        // Shift dense elements
        int denseEnd = (int) Math.min(length, denseArray.length);
        for (int i = start + count; i < denseEnd; i++) {
            if (i - count < denseArray.length) {
                denseArray[i - count] = denseArray[i];
            }
        }

        // Clear the tail
        for (int i = Math.max(0, denseEnd - count); i < denseEnd; i++) {
            if (i < denseArray.length) {
                denseArray[i] = null;
            }
        }

        // Handle sparse properties
        if (sparseProperties != null) {
            Map<Integer, JSValue> newSparse = new HashMap<>();
            for (Map.Entry<Integer, JSValue> entry : sparseProperties.entrySet()) {
                int index = entry.getKey();
                if (index >= start + count) {
                    newSparse.put(index - count, entry.getValue());
                } else if (index < start) {
                    newSparse.put(index, entry.getValue());
                }
            }
            sparseProperties = newSparse.isEmpty() ? null : newSparse;
        }
    }

    /**
     * Shift elements right (for unshift operation).
     */
    private void shiftElementsRight(int start, int count) {
        ensureDenseCapacity((int) Math.min(length + count, MAX_DENSE_SIZE));

        Map<Integer, JSValue> newSparse = null;
        if (sparseProperties != null) {
            newSparse = new HashMap<>();
            for (Map.Entry<Integer, JSValue> entry : sparseProperties.entrySet()) {
                int index = entry.getKey();
                if (index >= start) {
                    newSparse.put(index + count, entry.getValue());
                } else {
                    newSparse.put(index, entry.getValue());
                }
            }
        }

        // Shift dense elements
        int denseEnd = (int) Math.min(length, denseArray.length);
        for (int i = denseEnd - 1; i >= start; i--) {
            if (i + count < denseArray.length) {
                denseArray[i + count] = denseArray[i];
            } else if (denseArray[i] != null) {
                // Move to sparse storage
                if (newSparse == null) {
                    newSparse = new HashMap<>();
                }
                newSparse.put(i + count, denseArray[i]);
            }
        }

        // Clear the gap
        for (int i = start; i < start + count && i < denseArray.length; i++) {
            denseArray[i] = null;
        }

        sparseProperties = newSparse == null || newSparse.isEmpty() ? null : newSparse;
        setLength(length + count);
    }

    /**
     * Convert array to a Java array.
     */
    public JSValue[] toArray() {
        JSValue[] result = new JSValue[(int) length];
        for (int i = 0; i < length; i++) {
            result[i] = get(i);
        }
        return result;
    }

    private Long toArrayLengthForLengthProperty(JSContext context, JSValue value) {
        if (value.isSymbol() || value.isSymbolObject()) {
            context.throwTypeError("cannot convert symbol to number");
            return null;
        }
        if (value.isBigInt() || value.isBigIntObject()) {
            context.throwTypeError("cannot convert bigint to number");
            return null;
        }

        JSNumber uint32Source = JSTypeConversions.toNumber(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        long uint32Length = toUint32(uint32Source.value());

        JSNumber numericValue = JSTypeConversions.toNumber(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        Long exactLength = toArrayLengthFromNumber(numericValue.value());
        if (exactLength == null || exactLength != uint32Length) {
            context.throwRangeError("Invalid array length");
            return null;
        }
        return exactLength;
    }

    /**
     * Align array element conversion with Javet's object conversion:
     * integral finite numbers are boxed as Integer/Long instead of Double.
     */
    private Object toJavaArrayElement(JSValue value) {
        if (value instanceof JSNumber number) {
            double numberValue = number.value();
            if (Double.isFinite(numberValue)
                    && numberValue == Math.rint(numberValue)
                    && Double.doubleToRawLongBits(numberValue) != Double.doubleToRawLongBits(-0.0d)) {
                if (numberValue >= Integer.MIN_VALUE && numberValue <= Integer.MAX_VALUE) {
                    return (int) numberValue;
                }
                if (numberValue >= Long.MIN_VALUE && numberValue <= Long.MAX_VALUE) {
                    return (long) numberValue;
                }
            }
        }
        return value.toJavaObject();
    }

    @Override
    public Object toJavaObject() {
        List<Object> values = new ArrayList<>((int) Math.min(length, Integer.MAX_VALUE));
        for (long i = 0; i < length; i++) {
            values.add(toJavaArrayElement(get(i)));
        }
        return values;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        long maxDisplay = Math.min(length, 100);
        for (long i = 0; i < maxDisplay; i++) {
            if (i > 0) sb.append(", ");
            JSValue val = get(i);
            if (val instanceof JSString s) {
                sb.append('"').append(s.value()).append('"');
            } else if (val instanceof JSUndefined) {
                sb.append("undefined");
            } else {
                sb.append(val);
            }
        }
        if (length > maxDisplay) {
            sb.append(", ... (").append(length - maxDisplay).append(" more)");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Add element to the beginning of the array.
     */
    public void unshift(JSValue value) {
        // Shift all elements up
        shiftElementsRight(0, 1);

        // Insert at position 0
        set(0, value);
    }

    /**
     * Update the length property value.
     */
    private void updateLengthProperty() {
        int offset = shape.getPropertyOffset(PropertyKey.LENGTH);
        if (offset >= 0) {
            propertyValues[offset] = JSNumber.of(length);
        }
    }
}
