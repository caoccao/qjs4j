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

import java.util.Arrays;

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
    private static final int INITIAL_CAPACITY = 8;
    private static final int MAX_DENSE_SIZE = 10000;

    private JSValue[] denseArray;
    private long length;

    /**
     * Create an empty array.
     */
    public JSArray() {
        super();
        this.denseArray = new JSValue[INITIAL_CAPACITY];
        this.length = 0;
        initializeLengthProperty();
    }

    /**
     * Create an array with a specific initial length.
     */
    public JSArray(long length) {
        super();
        this.length = length;
        int capacity = (int) Math.min(length, INITIAL_CAPACITY);
        this.denseArray = new JSValue[capacity];
        initializeLengthProperty();
    }

    /**
     * Create an array from values.
     */
    public JSArray(JSValue... values) {
        super();
        this.length = values.length;
        this.denseArray = Arrays.copyOf(values, Math.max(values.length, INITIAL_CAPACITY));
        initializeLengthProperty();
    }

    /**
     * Initialize the "length" property as a special data property.
     */
    private void initializeLengthProperty() {
        // The length property is special - it's writable but not enumerable or configurable
        PropertyDescriptor lengthDesc = PropertyDescriptor.dataDescriptor(
                new JSNumber(length),
                true,  // writable
                false, // not enumerable
                false  // not configurable
        );
        super.defineProperty(PropertyKey.fromString("length"), lengthDesc);
    }

    /**
     * Get the array length.
     */
    public long getLength() {
        return length;
    }

    /**
     * Set the array length.
     * When length is reduced, elements beyond the new length are deleted.
     */
    public void setLength(long newLength) {
        if (newLength < 0 || newLength > 0xFFFFFFFFL) {
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
        }

        this.length = newLength;
        updateLengthProperty();
    }

    /**
     * Update the length property value.
     */
    private void updateLengthProperty() {
        int offset = shape.getPropertyOffset(PropertyKey.fromString("length"));
        if (offset >= 0) {
            propertyValues[offset] = new JSNumber(length);
        }
    }

    /**
     * Get element at index.
     */
    public JSValue get(long index) {
        if (index < 0 || index >= length) {
            return JSUndefined.INSTANCE;
        }

        // Try dense array first
        if (index < denseArray.length && denseArray[(int) index] != null) {
            return denseArray[(int) index];
        }

        // Check sparse storage
        if (sparseProperties != null && index <= Integer.MAX_VALUE) {
            JSValue value = sparseProperties.get((int) index);
            if (value != null) {
                return value;
            }
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Set element at index.
     */
    public void set(long index, JSValue value) {
        if (index < 0) {
            // Negative indices are treated as string properties
            super.set(Long.toString(index), value);
            return;
        }

        // Extend length if necessary
        if (index >= length) {
            setLength(index + 1);
        }

        // Use dense array if index is within reasonable range
        if (index < MAX_DENSE_SIZE) {
            ensureDenseCapacity((int) index + 1);
            denseArray[(int) index] = value;
        } else {
            // Use sparse storage for large indices
            if (sparseProperties == null) {
                sparseProperties = new java.util.HashMap<>();
            }
            sparseProperties.put((int) index, value);
        }
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

    /**
     * Add element to the end of the array.
     */
    public void push(JSValue value) {
        set(length, value);
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
        } else if (sparseProperties != null) {
            sparseProperties.remove((int) lastIndex);
        }

        setLength(lastIndex);
        return value;
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
     * Add element to the beginning of the array.
     */
    public void unshift(JSValue value) {
        // Shift all elements up
        shiftElementsRight(0, 1);

        // Insert at position 0
        set(0, value);
    }

    /**
     * Shift elements right (for unshift operation).
     */
    private void shiftElementsRight(int start, int count) {
        ensureDenseCapacity((int) Math.min(length + count, MAX_DENSE_SIZE));

        // Shift dense elements
        int denseEnd = (int) Math.min(length, denseArray.length);
        for (int i = denseEnd - 1; i >= start; i--) {
            if (i + count < denseArray.length) {
                denseArray[i + count] = denseArray[i];
            } else if (denseArray[i] != null) {
                // Move to sparse storage
                if (sparseProperties == null) {
                    sparseProperties = new java.util.HashMap<>();
                }
                sparseProperties.put(i + count, denseArray[i]);
            }
        }

        // Clear the gap
        for (int i = start; i < start + count && i < denseArray.length; i++) {
            denseArray[i] = null;
        }

        setLength(length + count);
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
            java.util.Map<Integer, JSValue> newSparse = new java.util.HashMap<>();
            for (java.util.Map.Entry<Integer, JSValue> entry : sparseProperties.entrySet()) {
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
     * Convert array to a Java array.
     */
    public JSValue[] toArray() {
        JSValue[] result = new JSValue[(int) length];
        for (int i = 0; i < length; i++) {
            result[i] = get(i);
        }
        return result;
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

    @Override
    public JSValue get(int index) {
        return get((long) index);
    }

    @Override
    public void set(int index, JSValue value) {
        set((long) index, value);
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
}
