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
 * Represents a JavaScript object.
 * Based on QuickJS object implementation with shape-based optimization.
 * <p>
 * Uses a shape (hidden class) system for efficient property access:
 * - Shape tracks property names and their offsets
 * - Property values stored in parallel array indexed by offset
 * - Sparse properties (numeric indices) stored separately
 * <p>
 * This design enables:
 * - Fast property access (O(1) with inline caching)
 * - Memory efficiency (shapes shared across objects)
 * - Optimized for objects with similar structure
 */
public non-sealed class JSObject implements JSValue {
    protected JSShape shape;
    protected JSValue[] propertyValues;
    protected Map<Integer, JSValue> sparseProperties; // For array indices
    protected JSObject prototype;
    protected boolean frozen = false;
    protected boolean sealed = false;

    /**
     * Create an empty object with no prototype.
     */
    public JSObject() {
        this.shape = JSShape.getRoot();
        this.propertyValues = new JSValue[0];
        this.sparseProperties = null;
        this.prototype = null;
    }

    /**
     * Create an object with a specific prototype.
     */
    public JSObject(JSObject prototype) {
        this();
        this.prototype = prototype;
    }

    // Property operations

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
        // Check sparse properties first
        if (sparseProperties != null) {
            JSValue value = sparseProperties.get(index);
            if (value != null) {
                return value;
            }
        }

        // Fall back to string property
        return get(PropertyKey.fromIndex(index));
    }

    /**
     * Get a property value by property key.
     */
    public JSValue get(PropertyKey key) {
        // Look in own properties
        int offset = shape.getPropertyOffset(key);
        if (offset >= 0) {
            return propertyValues[offset];
        }

        // Look in prototype chain
        if (prototype != null) {
            return prototype.get(key);
        }

        return JSUndefined.INSTANCE;
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
            return;
        }

        set(PropertyKey.fromIndex(index), value);
    }

    /**
     * Set a property value by property key.
     */
    public void set(PropertyKey key, JSValue value) {
        // Check if property already exists
        int offset = shape.getPropertyOffset(key);
        if (offset >= 0) {
            // Property exists, just update the value
            PropertyDescriptor desc = shape.getDescriptorAt(offset);
            if (!desc.isWritable() || frozen) {
                // In strict mode, this would throw TypeError
                return;
            }
            propertyValues[offset] = value;
            return;
        }

        // Property doesn't exist, add it (only if not sealed/frozen)
        if (!sealed && !frozen) {
            defineProperty(key, PropertyDescriptor.defaultData(value));
        }
    }

    /**
     * Define a new property with a descriptor.
     */
    public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        // Transition to new shape
        JSShape newShape = shape.addProperty(key, descriptor);

        // Allocate new property array
        JSValue[] newValues = Arrays.copyOf(propertyValues, newShape.getPropertyCount());

        // Set the new property value
        if (descriptor.hasValue()) {
            newValues[newShape.getPropertyCount() - 1] = descriptor.getValue();
        } else {
            newValues[newShape.getPropertyCount() - 1] = JSUndefined.INSTANCE;
        }

        // Update shape and values
        this.shape = newShape;
        this.propertyValues = newValues;
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
        if (shape.hasProperty(key)) {
            return true;
        }
        if (key.isIndex() && sparseProperties != null) {
            return sparseProperties.containsKey(key.asIndex());
        }
        return false;
    }

    /**
     * Get all own property keys (not including prototype chain).
     */
    public List<PropertyKey> getOwnPropertyKeys() {
        List<PropertyKey> keys = new ArrayList<>();

        // Add shaped properties
        PropertyKey[] shapeKeys = shape.getPropertyKeys();
        keys.addAll(Arrays.asList(shapeKeys));

        // Add sparse properties (array indices)
        if (sparseProperties != null) {
            for (Integer index : sparseProperties.keySet()) {
                keys.add(PropertyKey.fromIndex(index));
            }
        }

        return keys;
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
     * Delete a property.
     * Returns true if deletion was successful.
     */
    public boolean delete(String propertyName) {
        return delete(PropertyKey.fromString(propertyName));
    }

    /**
     * Delete a property by key.
     */
    public boolean delete(PropertyKey key) {
        // Cannot delete from sealed or frozen objects
        if (sealed || frozen) {
            return false;
        }

        // Check if property exists
        int offset = shape.getPropertyOffset(key);
        if (offset < 0) {
            // Check sparse properties
            if (key.isIndex() && sparseProperties != null) {
                return sparseProperties.remove(key.asIndex()) != null;
            }
            return true; // Property doesn't exist, deletion successful
        }

        // Check if property is configurable
        PropertyDescriptor desc = shape.getDescriptorAt(offset);
        if (!desc.isConfigurable()) {
            return false; // Cannot delete non-configurable property
        }

        // For simplicity, we don't actually remove from shape
        // In a full implementation, we'd need to create a new shape without this property
        // For now, just set to undefined
        propertyValues[offset] = JSUndefined.INSTANCE;
        return true;
    }

    /**
     * Get all own property keys.
     */
    public PropertyKey[] ownPropertyKeys() {
        List<PropertyKey> keys = new ArrayList<>();

        // Add shape properties
        PropertyKey[] shapeKeys = shape.getPropertyKeys();
        Collections.addAll(keys, shapeKeys);

        // Add sparse properties
        if (sparseProperties != null) {
            for (Integer index : sparseProperties.keySet()) {
                keys.add(PropertyKey.fromIndex(index));
            }
        }

        return keys.toArray(new PropertyKey[0]);
    }

    /**
     * Get own enumerable property keys.
     */
    public PropertyKey[] enumerableKeys() {
        List<PropertyKey> keys = new ArrayList<>();

        PropertyKey[] allKeys = shape.getPropertyKeys();
        PropertyDescriptor[] descriptors = shape.getDescriptors();

        for (int i = 0; i < allKeys.length; i++) {
            if (descriptors[i].isEnumerable()) {
                keys.add(allKeys[i]);
            }
        }

        // Sparse properties are enumerable by default
        if (sparseProperties != null) {
            for (Integer index : sparseProperties.keySet()) {
                keys.add(PropertyKey.fromIndex(index));
            }
        }

        return keys.toArray(new PropertyKey[0]);
    }

    /**
     * Get the property descriptor for a property.
     */
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        return shape.getDescriptor(key);
    }

    // Prototype chain

    public JSObject getPrototype() {
        return prototype;
    }

    public void setPrototype(JSObject prototype) {
        this.prototype = prototype;
    }

    // Object integrity levels (ES5)

    /**
     * Freeze this object.
     * Prevents adding new properties, deleting existing properties, and modifying existing properties.
     */
    public void freeze() {
        this.frozen = true;
        this.sealed = true; // Frozen objects are also sealed
    }

    /**
     * Seal this object.
     * Prevents adding new properties and deleting existing properties.
     * Existing properties can still be modified.
     */
    public void seal() {
        this.sealed = true;
    }

    /**
     * Check if this object is frozen.
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Check if this object is sealed.
     */
    public boolean isSealed() {
        return sealed;
    }

    // JSValue implementation

    @Override
    public JSValueType type() {
        return JSValueType.OBJECT;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }

    @Override
    public String toString() {
        return "[object Object]";
    }
}
