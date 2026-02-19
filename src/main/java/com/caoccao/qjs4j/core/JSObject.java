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
    private static final long MAX_ARRAY_INDEX = 0xFFFF_FFFEL; // 2^32 - 2
    // ThreadLocal to track visited objects during prototype chain traversal
    private static final ThreadLocal<Set<JSObject>> visitedObjects = ThreadLocal.withInitial(HashSet::new);
    protected JSConstructorType constructorType; // Internal slot for [[Constructor]] type (not accessible from JS)
    protected boolean extensible = true;
    protected boolean frozen = false;
    protected boolean htmlDDA; // Internal slot for [IsHTMLDDA] (Annex B test262 host object)
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
     * CreateDataProperty (ES2024 7.3.6).
     * Defines a data property with {writable: true, enumerable: true, configurable: true}.
     * Returns false if the property cannot be defined (e.g., existing non-configurable property
     * with incompatible attributes, or non-extensible object for new properties).
     */
    public boolean createDataProperty(PropertyKey key, JSValue value) {
        PropertyDescriptor current = getOwnPropertyDescriptor(key);
        if (current != null) {
            if (!current.isConfigurable()) {
                // Non-configurable property exists — can't redefine as configurable
                return false;
            }
        } else if (!isExtensible()) {
            // Property doesn't exist and object is not extensible
            return false;
        }
        defineProperty(key, PropertyDescriptor.dataDescriptor(value, true, true, true));
        return true;
    }

    /**
     * Define a non-enumerable, configurable getter accessor property.
     */
    public void defineGetterConfigurable(String name, JSNativeFunction.NativeCallback callback) {
        defineProperty(PropertyKey.fromString(name),
                PropertyDescriptor.accessorDescriptor(new JSNativeFunction("get " + name, 0, callback), null, false, true));
    }

    /**
     * Define a non-enumerable, configurable getter accessor property with a symbol key.
     */
    public void defineGetterConfigurable(JSSymbol symbol, JSNativeFunction.NativeCallback callback) {
        defineProperty(PropertyKey.fromSymbol(symbol),
                PropertyDescriptor.accessorDescriptor(new JSNativeFunction("get [" + symbol + "]", 0, callback), null, false, true));
    }

    public void defineGetterConfigurable(String name) {
        JSObject thisObject = this;
        JSNativeFunction getter = new JSNativeFunction("get " + name, 0, (ctx, thisArg, args) -> {
            if (thisArg != thisObject) {
                return ctx.throwTypeError("Generic static accessor property access is not supported");
            }
            return new JSString("");
        }, false);
        defineProperty(
                PropertyKey.fromString(name),
                PropertyDescriptor.accessorDescriptor(getter, null, false, true));
    }

    public void defineGetterSetterConfigurable(String name) {
        JSObject thisObject = this;
        JSNativeFunction getter = new JSNativeFunction("get " + name, 0, (ctx, thisArg, args) -> {
            if (thisArg != thisObject) {
                return ctx.throwTypeError("Generic static accessor property access is not supported");
            }
            return new JSString("");
        }, false);
        JSNativeFunction setter = new JSNativeFunction("set " + name, 1, (ctx, thisArg, args) -> {
            if (thisArg != thisObject) {
                return ctx.throwTypeError("Generic static accessor property access is not supported");
            }
            return JSUndefined.INSTANCE;
        }, false);
        defineProperty(
                PropertyKey.fromString(name),
                PropertyDescriptor.accessorDescriptor(getter, setter, false, true));
    }

    // Property operations

    public void defineGetterSetterConfigurable(String name, JSNativeFunction getter, JSNativeFunction setter) {
        defineProperty(
                PropertyKey.fromString(name),
                PropertyDescriptor.accessorDescriptor(getter, setter, false, true));
    }

    /**
     * [[DefineOwnProperty]] per ES spec.
     * Returns true if the property was successfully defined, false if the object
     * is not extensible and the property does not already exist.
     */
    public boolean defineOwnProperty(PropertyKey key, PropertyDescriptor descriptor, JSContext context) {
        if (!extensible && !hasOwnProperty(key)) {
            return false;
        }
        defineProperty(key, descriptor);
        return true;
    }

    /**
     * Define a new property with a descriptor.
     * This is the internal method that always succeeds (used by freeze, seal, etc.).
     */
    public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        // When defining a property (especially accessor), remove any sparse entry
        // so the shape-based property takes precedence in get().
        if (sparseProperties != null) {
            long arrayIndex = getCanonicalArrayIndex(key);
            if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE) {
                sparseProperties.remove((int) arrayIndex);
            }
        }

        // Use getOwnShapeKey to handle integer/string key equivalence (e.g., 0 vs "0")
        PropertyKey shapeKey = getOwnShapeKey(key);
        if (shapeKey != null) {
            int offset = shape.getPropertyOffset(shapeKey);
            // Property exists, update descriptor and value
            shape.addProperty(shapeKey, descriptor);
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

    public void definePropertyConfigurable(JSSymbol jsSymbol, JSValue value) {
        defineProperty(PropertyKey.fromSymbol(jsSymbol),
                PropertyDescriptor.dataDescriptor(value, false, false, true));
    }

    public void definePropertyConfigurable(String name, JSValue value) {
        defineProperty(PropertyKey.fromString(name),
                PropertyDescriptor.dataDescriptor(value, false, false, true));
    }

    public void definePropertyReadonlyNonConfigurable(JSSymbol jsSymbol, JSValue value) {
        defineProperty(PropertyKey.fromSymbol(jsSymbol),
                PropertyDescriptor.dataDescriptor(value, false, false, false));
    }

    public void definePropertyReadonlyNonConfigurable(String name, JSValue value) {
        defineProperty(PropertyKey.fromString(name),
                PropertyDescriptor.dataDescriptor(value, false, false, false));
    }

    /**
     * Define a writable, non-enumerable, configurable data property.
     */
    public void definePropertyWritableConfigurable(JSSymbol jsSymbol, JSValue value) {
        defineProperty(PropertyKey.fromSymbol(jsSymbol),
                PropertyDescriptor.dataDescriptor(value, true, false, true));
    }

    /**
     * Define a writable, non-enumerable, configurable data property.
     */
    public void definePropertyWritableConfigurable(String name, JSValue value) {
        defineProperty(PropertyKey.fromString(name),
                PropertyDescriptor.dataDescriptor(value, true, false, true));
    }

    /**
     * Delete a property.
     * Returns true if deletion was successful.
     * Following QuickJS delete_property() logic.
     */
    public boolean delete(String propertyName) {
        return delete(PropertyKey.fromString(propertyName), null);
    }

    /**
     * Delete a property by key.
     * Following QuickJS delete_property() implementation.
     */
    public boolean delete(PropertyKey key) {
        return delete(key, null);
    }

    /**
     * Delete a property by key with context for strict mode checking.
     * Following QuickJS delete_property() implementation.
     */
    public boolean delete(PropertyKey key, JSContext context) {
        // Check sparse properties first.
        long arrayIndex = getCanonicalArrayIndex(key);
        if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            int sparseIndex = (int) arrayIndex;
            if (!sparseProperties.containsKey(sparseIndex)) {
                // Property doesn't exist, deletion successful.
                // Even non-extensible/sealed/frozen objects return true for absent properties.
                return true;
            }
            if (sealed || frozen) {
                if (context != null && context.isStrictMode()) {
                    context.throwTypeError(
                            "Cannot delete property '" + key.toPropertyString() + "' of " + getObjectDescriptionForError(true));
                }
                return false;
            }
            sparseProperties.remove(sparseIndex);
            return true;
        }

        // Find property in shape and check if it exists
        PropertyKey shapeKey = getOwnShapeKey(key);
        if (shapeKey == null) {
            return true; // Property doesn't exist, deletion successful
        }

        // Cannot delete an existing own property from sealed or frozen objects.
        if (sealed || frozen) {
            if (context != null && context.isStrictMode()) {
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
            if (context != null && context.isStrictMode()) {
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

    private boolean failSet(PropertyKey key, JSContext context, boolean throwOnFailure) {
        if (throwOnFailure && context != null && context.isStrictMode()) {
            context.throwTypeError(
                    "Cannot assign to read only property '" + key.toPropertyString() + "' of " + getObjectDescriptionForError(false));
        }
        return false;
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
        long arrayIndex = getCanonicalArrayIndex(key);
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
        PropertyKey shapeKey = getOwnShapeKey(key);
        int offset = shapeKey != null ? shape.getPropertyOffset(shapeKey) : -1;
        if (offset >= 0) {
            // Check if property has a getter
            PropertyDescriptor desc = shape.getDescriptor(shapeKey);
            if (desc != null && desc.hasGetter()) {
                JSFunction getter = desc.getGetter();
                if (getter != null && context != null) {
                    try {
                        // Call the getter with the ORIGINAL receiver as 'this', not the prototype
                        JSValue result = getter.call(context, receiver, new JSValue[0]);
                        // Check if getter threw an exception - return the error value or undefined
                        if (context.hasPendingException()) {
                            return result != null ? result : context.getPendingException();
                        }
                        return result;
                    } catch (JSVirtualMachineException e) {
                        // Getter threw - convert to pending exception so callers can handle it
                        JSValue exception = e.getJsError() != null ? e.getJsError()
                                : e.getJsValue() != null ? e.getJsValue()
                                : context.throwError("Error", e.getMessage());
                        context.setPendingException(exception);
                        return JSUndefined.INSTANCE;
                    }
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

    protected long getCanonicalArrayIndex(PropertyKey key) {
        if (key.isIndex()) {
            return Integer.toUnsignedLong(key.asIndex());
        }
        if (!key.isString()) {
            return -1;
        }
        return getCanonicalArrayIndex(key.asString());
    }

    private long getCanonicalArrayIndex(String key) {
        if (key.isEmpty()) {
            return -1;
        }
        if ("0".equals(key)) {
            return 0;
        }
        if (key.charAt(0) == '0') {
            return -1;
        }
        long value = 0;
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (ch < '0' || ch > '9') {
                return -1;
            }
            value = value * 10 + (ch - '0');
            if (value > MAX_ARRAY_INDEX) {
                return -1;
            }
        }
        return value;
    }

    /**
     * Get the constructor type internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public JSConstructorType getConstructorType() {
        return constructorType;
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
        List<PropertyKey> stringKeys = new ArrayList<>();
        List<PropertyKey> symbolKeys = new ArrayList<>();
        List<Map.Entry<Long, PropertyKey>> numericKeys = new ArrayList<>();
        Set<Long> seenNumericIndices = new HashSet<>();
        Set<PropertyKey> seenPropertyKeys = new HashSet<>();

        PropertyKey[] shapeKeys = shape.getPropertyKeys();
        for (PropertyKey shapeKey : shapeKeys) {
            PropertyDescriptor descriptor = shape.getDescriptor(shapeKey);
            if (descriptor == null) {
                continue;
            }
            if (enumerableOnly && !descriptor.isEnumerable()) {
                continue;
            }
            long index = getCanonicalArrayIndex(shapeKey);
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
        PropertyKey shapeKey = getOwnShapeKey(key);
        if (shapeKey != null) {
            return shape.getDescriptor(shapeKey);
        }

        long arrayIndex = getCanonicalArrayIndex(key);
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

    private PropertyKey getOwnShapeKey(PropertyKey key) {
        if (shape.hasProperty(key)) {
            return key;
        }
        long index = getCanonicalArrayIndex(key);
        if (index < 0 || index > Integer.MAX_VALUE) {
            return null;
        }
        PropertyKey alternateKey = key.isIndex()
                ? PropertyKey.fromString(Long.toString(index))
                : PropertyKey.fromIndex((int) index);
        return shape.hasProperty(alternateKey) ? alternateKey : null;
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
     * Get a property value with an explicit receiver for getter invocation.
     * Used by Reflect.get to pass a different receiver than the target.
     */
    public JSValue getWithReceiver(PropertyKey key, JSContext context, JSObject receiver) {
        return get(key, context, receiver);
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
        if (getOwnShapeKey(key) != null) {
            return true;
        }
        long arrayIndex = getCanonicalArrayIndex(key);
        if (arrayIndex >= 0 && arrayIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            return sparseProperties.containsKey((int) arrayIndex);
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
     * Check if this object has the [IsHTMLDDA] internal slot set.
     */
    public boolean isHTMLDDA() {
        return htmlDDA;
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
        set(key, value, context, this);
    }

    /**
     * Set a property value with an explicit receiver for setter invocation.
     * Used by Reflect.set to pass a different receiver than the target.
     */
    public void set(PropertyKey key, JSValue value, JSContext context, JSObject receiver) {
        setInternal(key, value, context, receiver, true);
    }

    /**
     * Set the constructor type internal slot.
     * This is for internal use only - not accessible from JavaScript.
     */
    public void setConstructorType(JSConstructorType type) {
        this.constructorType = type;
    }

    /**
     * Set the [IsHTMLDDA] internal slot.
     */
    public void setHTMLDDA(boolean htmlDDA) {
        this.htmlDDA = htmlDDA;
    }

    private boolean setInternal(PropertyKey key, JSValue value, JSContext context, JSObject receiver, boolean throwOnFailure) {
        // Check if property already exists
        PropertyKey ownShapeKey = getOwnShapeKey(key);
        if (ownShapeKey != null) {
            int offset = shape.getPropertyOffset(ownShapeKey);
            PropertyDescriptor descriptor = shape.getDescriptorAt(offset);

            if (descriptor != null && descriptor.isAccessorDescriptor()) {
                JSFunction setter = descriptor.getSetter();
                if (setter != null && context != null) {
                    setter.call(context, receiver, new JSValue[]{value});
                    return !context.hasPendingException();
                }
                return failSet(key, context, throwOnFailure);
            }

            if (descriptor == null || !descriptor.isWritable() || frozen) {
                return failSet(key, context, throwOnFailure);
            }

            if (receiver != this) {
                return setOnReceiver(key, value, context, receiver, throwOnFailure);
            }

            propertyValues[offset] = value;
            return true;
        }

        // Property doesn't exist on own object - walk prototype chain for setters/writability checks.
        // Per ES spec 9.1.9.1 OrdinarySet, if a prototype is a Proxy, delegate to its [[Set]].
        Set<JSObject> visited = new HashSet<>();
        visited.add(this);
        JSObject proto = prototype;
        while (proto != null && !visited.contains(proto)) {
            if (proto instanceof JSProxy proxy) {
                return proxy.setWithResult(key, value, context, receiver);
            }
            visited.add(proto);
            PropertyKey protoShapeKey = proto.getOwnShapeKey(key);
            if (protoShapeKey != null) {
                int protoOffset = proto.shape.getPropertyOffset(protoShapeKey);
                PropertyDescriptor protoDescriptor = proto.shape.getDescriptorAt(protoOffset);
                if (protoDescriptor != null && protoDescriptor.isAccessorDescriptor()) {
                    JSFunction setter = protoDescriptor.getSetter();
                    if (setter != null && context != null) {
                        setter.call(context, receiver, new JSValue[]{value});
                        return !context.hasPendingException();
                    }
                    return failSet(key, context, throwOnFailure);
                }
                if (protoDescriptor != null && !protoDescriptor.isWritable()) {
                    return failSet(key, context, throwOnFailure);
                }
                break;
            }
            proto = proto.prototype;
        }

        return setOnReceiver(key, value, context, receiver, throwOnFailure);
    }

    private boolean setOnReceiver(PropertyKey key, JSValue value, JSContext context, JSObject receiver, boolean throwOnFailure) {
        PropertyKey receiverShapeKey = receiver.getOwnShapeKey(key);
        if (receiverShapeKey != null) {
            int receiverOffset = receiver.shape.getPropertyOffset(receiverShapeKey);
            PropertyDescriptor receiverDescriptor = receiver.shape.getDescriptorAt(receiverOffset);
            if (receiverDescriptor != null && receiverDescriptor.isAccessorDescriptor()) {
                JSFunction receiverSetter = receiverDescriptor.getSetter();
                if (receiverSetter != null && context != null) {
                    receiverSetter.call(context, receiver, new JSValue[]{value});
                    return !context.hasPendingException();
                }
                return failSet(key, context, throwOnFailure);
            }
            if (receiverDescriptor == null || !receiverDescriptor.isWritable() || receiver.frozen) {
                return failSet(key, context, throwOnFailure);
            }
            receiver.propertyValues[receiverOffset] = value;
            return true;
        }

        if (!receiver.extensible) {
            if (throwOnFailure && context != null && context.isStrictMode()) {
                context.throwTypeError("Cannot add property " + key.toPropertyString() + ", object is not extensible");
            }
            return false;
        }

        receiver.defineProperty(key, PropertyDescriptor.defaultData(value));
        return true;
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

        // Non-extensible objects cannot have their prototype changed
        if (!this.extensible) {
            return SetPrototypeResult.NOT_EXTENSIBLE;
        }

        // Check for circular prototype chain
        if (proto != null) {
            JSObject p = proto;
            while (p != null) {
                if (p == this) {
                    return SetPrototypeResult.CIRCULAR;
                }
                p = p.getPrototype();
            }
        }

        this.prototype = proto;
        return SetPrototypeResult.SUCCESS;
    }

    public boolean setWithResult(PropertyKey key, JSValue value, JSContext context) {
        return setWithResult(key, value, context, this);
    }

    public boolean setWithResult(PropertyKey key, JSValue value, JSContext context, JSObject receiver) {
        return setInternal(key, value, context, receiver, false);
    }

    /**
     * Set with result, accepting any JSValue as receiver.
     * Per ES spec, [[Set]](P, V, Receiver) accepts any ECMAScript language value as Receiver.
     * When receiver is not an object, OrdinarySet returns false (cannot create properties on non-objects).
     * Subclasses (e.g. TypedArray) may override for spec-specific behavior.
     */
    public boolean setWithResult(PropertyKey key, JSValue value, JSContext context, JSValue receiver) {
        if (receiver instanceof JSObject objReceiver) {
            return setWithResult(key, value, context, objReceiver);
        }
        // Per QuickJS JS_SetPropertyInternal: when receiver (this_obj) is not an object (p == NULL),
        // after prototype lookup completes without finding a setter, returns error/false.
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
