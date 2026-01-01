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
    // ThreadLocal to track visited objects during prototype chain traversal
    private static final ThreadLocal<Set<JSObject>> visitedObjects = ThreadLocal.withInitial(HashSet::new);
    protected ConstructorType constructorType; // Internal slot for [[Constructor]] type (not accessible from JS)
    protected boolean extensible = true;
    protected boolean frozen = false;
    protected JSValue primitiveValue; // Internal slot for [[PrimitiveValue]] (not accessible from JS)
    protected JSValue[] propertyValues;
    protected JSObject prototype;
    protected boolean sealed = false;
    protected JSShape shape;
    protected Map<Integer, JSValue> sparseProperties; // For array indices

    /**
     * Create an empty object with no prototype.
     * Each object gets its own shape copy (not shared).
     */
    public JSObject() {
        this.shape = new JSShape();  // Each object gets its own shape
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
     * Compact properties by removing deleted properties.
     * Following QuickJS compact_properties() logic.
     */
    private void compactProperties() {
        if (shape.getDeletedPropCount() == 0) {
            return; // Nothing to compact
        }

        // Compact the shape
        shape.compact();

        // Rebuild property values array without deleted entries
        PropertyKey[] keys = shape.getPropertyKeys();
        JSValue[] newValues = new JSValue[keys.length];

        int newIndex = 0;
        for (int i = 0; i < propertyValues.length; i++) {
            // Check if this slot is still valid after compaction
            if (newIndex < keys.length) {
                newValues[newIndex] = propertyValues[i];
                newIndex++;
            }
        }

        this.propertyValues = newValues;
    }

    /**
     * Define a new property with a descriptor.
     */
    public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        // Check if property already exists
        int offset = shape.getPropertyOffset(key);
        if (offset >= 0) {
            // Property exists, update descriptor and value
            shape.addProperty(key, descriptor);
            if (descriptor.hasValue()) {
                propertyValues[offset] = descriptor.getValue();
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
        // Cannot delete from sealed or frozen objects
        if (sealed || frozen) {
            return false;
        }

        // Check sparse properties first
        if (key.isIndex() && sparseProperties != null) {
            sparseProperties.remove(key.asIndex());
            return true;
        }

        // Find property in shape and check if it exists
        int offset = shape.getPropertyOffset(key);
        if (offset < 0) {
            return true; // Property doesn't exist, deletion successful
        }

        // Remove from shape (checks configurability internally)
        boolean removed = shape.removeProperty(key);
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
        // Check sparse properties first
        if (sparseProperties != null) {
            JSValue value = sparseProperties.get(index);
            if (value != null) {
                return value;
            }
        }

        // Check string property
        JSValue stringValue = get(String.valueOf(index));
        if (!(stringValue instanceof JSUndefined)) {
            return stringValue;
        }

        // Fall back to index property
        return get(PropertyKey.fromIndex(index));
    }

    /**
     * Get a property value by property key.
     */
    public JSValue get(PropertyKey key) {
        return get(key, null);
    }

    /**
     * Get a property value by property key with context for getter functions.
     */
    public JSValue get(PropertyKey key, JSContext context) {
        return get(key, context, this);  // Pass original receiver
    }

    /**
     * Internal get method with receiver tracking for prototype chain getter invocation.
     * Protected to allow JSProxy to override with proper trap handling.
     */
    protected JSValue get(PropertyKey key, JSContext context, JSObject receiver) {
        // Look in own properties
        int offset = shape.getPropertyOffset(key);
        if (offset >= 0) {
            // Check if property has a getter
            PropertyDescriptor desc = shape.getDescriptor(key);
            if (desc != null && desc.hasGetter()) {
                JSFunction getter = desc.getGetter();
                if (getter != null && context != null) {
                    // Call the getter with the ORIGINAL receiver as 'this', not the prototype
                    JSValue result = getter.call(context, receiver, new JSValue[0]);
                    // Check if getter threw an exception - return the error value or undefined
                    if (context.hasPendingException()) {
                        return result != null ? result : context.getPendingException();
                    }
                    return result;
                }
                // Getter is explicitly undefined or no context available
                return JSUndefined.INSTANCE;
            }
            // Regular property with value
            return propertyValues[offset];
        }

        // Look in prototype chain with cycle detection
        if (prototype != null) {
            Set<JSObject> visited = visitedObjects.get();
            boolean isTopLevel = visited.isEmpty();

            try {
                // Check for circular reference
                if (visited.contains(prototype)) {
                    return JSUndefined.INSTANCE;
                }

                // Add current prototype to visited set
                visited.add(prototype);

                // Recurse into prototype chain, passing along the original receiver
                return prototype.get(key, context, receiver);
            } finally {
                // Clean up: remove from visited set
                visited.remove(prototype);

                // If this was the top-level call, clear the ThreadLocal
                if (isTopLevel) {
                    visited.clear();
                }
            }
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Get the constructor type internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public ConstructorType getConstructorType() {
        return constructorType;
    }

    /**
     * Get the property descriptor for a property.
     */
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        return shape.getDescriptor(key);
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
        if (shape.hasProperty(key)) {
            return true;
        }
        if (key.isIndex() && sparseProperties != null) {
            return sparseProperties.containsKey(key.asIndex());
        }
        return false;
    }

    /**
     * Check if this object is extensible.
     * ES5.1 15.2.3.13
     */
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
     * Check if this object is sealed.
     */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * Get all own property keys.
     */
    public PropertyKey[] ownPropertyKeys() {
        List<PropertyKey> keys = new ArrayList<>();

        // Add shape properties
        PropertyKey[] shapeKeys = shape.getPropertyKeys();
        keys.addAll(Arrays.asList(shapeKeys));

        // Add sparse properties
        if (sparseProperties != null) {
            for (Integer index : sparseProperties.keySet()) {
                keys.add(PropertyKey.fromIndex(index));
            }
        }

        return keys.toArray(new PropertyKey[0]);
    }

    /**
     * Prevent new properties from being added to this object.
     * ES5.1 15.2.3.10
     */
    public void preventExtensions() {
        this.extensible = false;
    }

    /**
     * Seal this object.
     * Prevents adding new properties and deleting existing properties.
     * Existing properties can still be modified.
     */
    public void seal() {
        this.sealed = true;
        this.extensible = false; // Sealed objects are not extensible
    }

    // Prototype chain

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

    // Object integrity levels (ES5)

    /**
     * Set a property value by property key.
     */
    public void set(PropertyKey key, JSValue value) {
        set(key, value, null);
    }

    /**
     * Set a property value by property key with context for setter functions.
     */
    public void set(PropertyKey key, JSValue value, JSContext context) {
        // Check if property already exists
        int offset = shape.getPropertyOffset(key);
        if (offset >= 0) {
            // Property exists, check if it has a setter
            PropertyDescriptor desc = shape.getDescriptorAt(offset);

            // If property has a setter, call it
            if (desc.hasSetter()) {
                JSFunction setter = desc.getSetter();
                if (setter != null && context != null) {
                    // Call the setter with 'this' as the object and value as argument
                    setter.call(context, this, new JSValue[]{value});
                    // If setter threw an exception, it remains pending in context
                    // The VM will check for it after property access
                }
                // If setter is null/undefined or no context, the assignment does nothing (silently fails)
                return;
            }

            // Regular property - check if writable
            if (!desc.isWritable() || frozen) {
                // In strict mode, throw TypeError
                if (context != null && context.isStrictMode()) {
                    // Get better description of object for error message
                    String objectDesc = this instanceof JSFunction func ?
                            "function '" + func + "'" : this.toString();
                    context.throwTypeError(
                            "Cannot assign to read only property '" + key.asString() + "' of " + objectDesc);
                }
                // In non-strict mode, silently fail
                return;
            }
            propertyValues[offset] = value;
            return;
        }

        // Property doesn't exist, add it (only if extensible)
        if (extensible) {
            defineProperty(key, PropertyDescriptor.defaultData(value));
        }
    }

    /**
     * Set the constructor type internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public void setConstructorType(ConstructorType type) {
        this.constructorType = type;
    }

    /**
     * Set the [[PrimitiveValue]] internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public void setPrimitiveValue(JSValue value) {
        this.primitiveValue = value;
    }

    public void setPrototype(JSObject prototype) {
        this.prototype = prototype;
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
}
