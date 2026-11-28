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

import java.util.Optional;

/**
 * Represents a JavaScript property descriptor. Based on ECMAScript specification and QuickJS implementation.
 * <p>
 * A property can be either: - Data descriptor: has value and writable - Accessor descriptor: has getter and/or setter
 * <p>
 * Both types can have enumerable and configurable attributes.
 * <p>
 * Optional fields use empty vs present to distinguish "not specified" from "specified". Getter/setter use JSUndefined
 * sentinel for "not specified", null for "explicitly undefined", and a JSFunction instance for a real accessor. All
 * fields are initialized and never null.
 */
public final class PropertyDescriptor {
    private Optional<Boolean> configurable;
    private Optional<Boolean> enumerable;
    private JSValue getter;
    private JSValue setter;
    private Optional<JSValue> value;
    private Optional<Boolean> writable;

    public PropertyDescriptor() {
        this.configurable = Optional.empty();
        this.enumerable = Optional.empty();
        this.getter = JSUndefined.INSTANCE;
        this.setter = JSUndefined.INSTANCE;
        this.value = Optional.empty();
        this.writable = Optional.empty();
    }

    /** Whether a partial descriptor can describe a data property with these fixed attributes. */
    public boolean acceptsDataAttributes(DataState state) {
        return !isAccessorDescriptor() && (!hasConfigurable() || isConfigurable() == state.isConfigurable())
                && (!hasEnumerable() || isEnumerable() == state.isEnumerable())
                && (!hasWritable() || isWritable() == state.isWritable());
    }

    /**
     * Complete this descriptor with default values for accessor.
     */
    public void completeAsAccessor() {
        if (getter instanceof JSUndefined) {
            getter = null;
        }
        if (setter instanceof JSUndefined) {
            setter = null;
        }
        completeAttributes();
    }

    /**
     * Complete this descriptor with default values. Used when defining a new property.
     */
    public void completeAsData() {
        if (value.isEmpty()) {
            setValue(JSUndefined.INSTANCE);
        }
        if (writable.isEmpty()) {
            setWritable(false);
        }
        completeAttributes();
    }

    private void completeAttributes() {
        if (enumerable.isEmpty()) {
            setEnumerable(false);
        }
        if (configurable.isEmpty()) {
            setConfigurable(false);
        }
    }

    /**
     * Create a shallow copy from another descriptor, preserving only explicitly specified attributes.
     */
    public PropertyDescriptor copyFrom(PropertyDescriptor source) {
        if (source.hasValue()) {
            setValue(source.getValue());
        }
        if (source.hasWritable()) {
            setWritable(source.isWritable());
        }
        if (source.hasEnumerable()) {
            setEnumerable(source.isEnumerable());
        }
        if (source.hasConfigurable()) {
            setConfigurable(source.isConfigurable());
        }
        if (source.hasGetter()) {
            setGetter(source.getGetter());
        }
        if (source.hasSetter()) {
            setSetter(source.getSetter());
        }
        return this;
    }

    public JSFunction getGetter() {
        return getter instanceof JSFunction f ? f : null;
    }

    public JSFunction getSetter() {
        return setter instanceof JSFunction f ? f : null;
    }

    // Getters

    public JSValue getValue() {
        return value.orElse(null);
    }

    public boolean hasConfigurable() {
        return configurable.isPresent();
    }

    public boolean hasEnumerable() {
        return enumerable.isPresent();
    }

    public boolean hasGetter() {
        return !(getter instanceof JSUndefined);
    }

    public boolean hasSetter() {
        return !(setter instanceof JSUndefined);
    }

    public boolean hasValue() {
        return value.isPresent();
    }

    public boolean hasWritable() {
        return writable.isPresent();
    }

    // "Has" checks for partial descriptors

    /**
     * Check if this is an accessor descriptor. An accessor descriptor has getter or setter attributes.
     */
    public boolean isAccessorDescriptor() {
        return !(getter instanceof JSUndefined) || !(setter instanceof JSUndefined);
    }

    public boolean isConfigurable() {
        return configurable.orElse(false);
    }

    /**
     * Check if this is a data descriptor. A data descriptor has value or writable attributes.
     */
    public boolean isDataDescriptor() {
        return value.isPresent() || writable.isPresent();
    }

    /**
     * Check if descriptor is empty (no attributes set).
     */
    public boolean isEmpty() {
        return value.isEmpty() && writable.isEmpty() && enumerable.isEmpty() && configurable.isEmpty()
                && getter instanceof JSUndefined && setter instanceof JSUndefined;
    }

    public boolean isEnumerable() {
        return enumerable.orElse(false);
    }

    /**
     * Check if this is a generic descriptor. A generic descriptor has neither data nor accessor attributes.
     */
    public boolean isGenericDescriptor() {
        return !isDataDescriptor() && !isAccessorDescriptor();
    }

    // Setters

    public boolean isWritable() {
        return writable.orElse(false);
    }

    /**
     * Merge attributes from another descriptor into this one. Only attributes explicitly specified in the other
     * descriptor are updated. Unspecified attributes in the other descriptor are left unchanged. Handles data↔accessor
     * descriptor type conversion by clearing the old type-specific attributes when switching types.
     */
    public void mergeFrom(PropertyDescriptor other) {
        // Handle data → accessor conversion: clear data attributes
        if ((other.hasGetter() || other.hasSetter()) && isDataDescriptor()) {
            value = Optional.empty();
            writable = Optional.empty();
        }
        // Handle accessor → data conversion: clear accessor attributes
        if ((other.hasValue() || other.hasWritable()) && isAccessorDescriptor()) {
            getter = JSUndefined.INSTANCE;
            setter = JSUndefined.INSTANCE;
        }
        if (other.hasValue()) {
            setValue(other.getValue());
        }
        if (other.hasWritable()) {
            setWritable(other.isWritable());
        }
        if (other.hasEnumerable()) {
            setEnumerable(other.isEnumerable());
        }
        if (other.hasConfigurable()) {
            setConfigurable(other.isConfigurable());
        }
        if (other.hasGetter()) {
            setGetter(other.getGetter());
        }
        if (other.hasSetter()) {
            setSetter(other.getSetter());
        }
    }

    public void setConfigurable(boolean configurable) {
        this.configurable = Optional.of(configurable);
    }

    public void setEnumerable(boolean enumerable) {
        this.enumerable = Optional.of(enumerable);
    }

    public void setGetter(JSFunction getter) {
        this.getter = getter;
    }

    public void setSetter(JSFunction setter) {
        this.setter = setter;
    }

    public void setValue(JSValue value) {
        this.value = Optional.ofNullable(value);
    }

    // Type checks

    public void setWritable(boolean writable) {
        this.writable = Optional.of(writable);
    }

    public JSObject toDescriptorObject(JSContext context) {
        JSObject descriptorObject = context.createJSObject();

        if (isDataDescriptor()) {
            descriptorObject.set(PropertyKey.VALUE, getValue() != null ? getValue() : JSUndefined.INSTANCE);
            descriptorObject.set(PropertyKey.WRITABLE, JSBoolean.valueOf(isWritable()));
        } else if (isAccessorDescriptor()) {
            descriptorObject.set(PropertyKey.GET, getGetter() != null ? getGetter() : JSUndefined.INSTANCE);
            descriptorObject.set(PropertyKey.SET, getSetter() != null ? getSetter() : JSUndefined.INSTANCE);
        }

        writeAttributes(descriptorObject, false);
        return descriptorObject;
    }

    /** FromPropertyDescriptor keeps absent fields absent in the descriptor passed to a proxy trap. */
    JSObject toPartialDescriptorObject(JSContext context) {
        JSObject result = context.createJSObject();
        if (hasValue()) {
            result.set(PropertyKey.VALUE, getValue());
        }
        if (hasWritable()) {
            result.set(PropertyKey.WRITABLE, JSBoolean.valueOf(isWritable()));
        }
        if (hasGetter()) {
            result.set(PropertyKey.GET, getGetter());
        }
        if (hasSetter()) {
            result.set(PropertyKey.SET, getSetter());
        }
        writeAttributes(result, true);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PropertyDescriptor{");
        if (value.isPresent()) {
            sb.append("value=").append(getValue()).append(", ");
        }
        if (writable.isPresent()) {
            sb.append("writable=").append(isWritable()).append(", ");
        }
        if (enumerable.isPresent()) {
            sb.append("enumerable=").append(isEnumerable()).append(", ");
        }
        if (configurable.isPresent()) {
            sb.append("configurable=").append(isConfigurable()).append(", ");
        }
        if (!(getter instanceof JSUndefined)) {
            sb.append("get=").append(getter instanceof JSFunction ? "function" : "undefined").append(", ");
        }
        if (!(setter instanceof JSUndefined)) {
            sb.append("set=").append(setter instanceof JSFunction ? "function" : "undefined").append(", ");
        }
        if (sb.length() > 19) {
            sb.setLength(sb.length() - 2);
        } // Remove trailing ", "
        sb.append("}");
        return sb.toString();
    }

    private void writeAttributes(JSObject object, boolean partial) {
        if (!partial || hasEnumerable()) {
            object.set(PropertyKey.ENUMERABLE, JSBoolean.valueOf(isEnumerable()));
        }
        if (!partial || hasConfigurable()) {
            object.set(PropertyKey.CONFIGURABLE, JSBoolean.valueOf(isConfigurable()));
        }
    }

    /**
     * Create an accessor descriptor.
     */
    public static PropertyDescriptor accessorDescriptor(JSFunction getter, JSFunction setter, AccessorState state) {
        PropertyDescriptor desc = new PropertyDescriptor();
        if (getter != null) {
            desc.setGetter(getter);
        }
        if (setter != null) {
            desc.setSetter(setter);
        }
        desc.setEnumerable(state.isEnumerable());
        desc.setConfigurable(state.isConfigurable());
        return desc;
    }

    /**
     * Create a data descriptor.
     */
    public static PropertyDescriptor dataDescriptor(JSValue value, DataState state) {
        PropertyDescriptor desc = new PropertyDescriptor();
        desc.setValue(value);
        desc.setWritable(state.isWritable());
        desc.setEnumerable(state.isEnumerable());
        desc.setConfigurable(state.isConfigurable());
        return desc;
    }

    /**
     * Create a default data descriptor (writable, enumerable, configurable).
     */
    public static PropertyDescriptor defaultData(JSValue value) {
        return dataDescriptor(value, DataState.All);
    }

    public static PropertyDescriptor fromDescriptorObject(JSContext context, JSObject descObj,
            boolean describeInvalidAccessor) {
        PropertyDescriptor desc = new PropertyDescriptor();
        boolean hasAccessor = false;
        boolean hasData = false;

        // Step 2: If HasProperty(Obj, "enumerable"), set [[Enumerable]] to ToBoolean(Get(Obj, "enumerable"))
        if (descObj.has(PropertyKey.ENUMERABLE)) {
            JSValue enumerable = descObj.get(PropertyKey.ENUMERABLE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setEnumerable(JSTypeChecking.isTruthy(enumerable));
        }

        // Step 3: If HasProperty(Obj, "configurable"), set [[Configurable]] to ToBoolean(Get(Obj, "configurable"))
        if (descObj.has(PropertyKey.CONFIGURABLE)) {
            JSValue configurable = descObj.get(PropertyKey.CONFIGURABLE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setConfigurable(JSTypeChecking.isTruthy(configurable));
        }

        // Step 4: If HasProperty(Obj, "value"), set [[Value]] to Get(Obj, "value")
        if (descObj.has(PropertyKey.VALUE)) {
            JSValue value = descObj.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setValue(value);
            hasData = true;
        }

        // Step 5: If HasProperty(Obj, "writable"), set [[Writable]] to ToBoolean(Get(Obj, "writable"))
        if (descObj.has(PropertyKey.WRITABLE)) {
            JSValue writable = descObj.get(PropertyKey.WRITABLE);
            if (context.hasPendingException()) {
                return null;
            }
            desc.setWritable(JSTypeChecking.isTruthy(writable));
            hasData = true;
        }

        if (descObj.has(PropertyKey.GET)) {
            if (!readAccessor(context, descObj, desc, true, describeInvalidAccessor)) {
                return null;
            }
            hasAccessor = true;
        }
        if (descObj.has(PropertyKey.SET)) {
            if (!readAccessor(context, descObj, desc, false, describeInvalidAccessor)) {
                return null;
            }
            hasAccessor = true;
        }

        // Step 8: If accessor and data properties are both present, throw TypeError
        if (hasAccessor && hasData) {
            return context.rejectTypeError(
                    "Invalid property descriptor. Cannot both specify accessors and a value or writable attribute, #<Object>");
        }

        return desc;
    }

    private static boolean readAccessor(JSContext context, JSObject source, PropertyDescriptor descriptor,
            boolean getter, boolean describeInvalidAccessor) {
        JSValue value = source.get(getter ? PropertyKey.GET : PropertyKey.SET);
        if (context.hasPendingException()) {
            return false;
        }
        if (!(value instanceof JSUndefined) && !JSTypeChecking.isCallable(value)) {
            String message = getter ? "Getter must be a function" : "Setter must be a function";
            if (describeInvalidAccessor) {
                message += ": " + JSTypeConversions.toJavaString(context, value);
            }
            context.throwTypeError(message);
            return false;
        }
        JSFunction function = value instanceof JSFunction fn ? fn : null;
        if (getter) {
            descriptor.setGetter(function);
        } else {
            descriptor.setSetter(function);
        }
        return true;
    }

    public static PropertyDescriptor readDefinition(JSContext context, JSObject descriptorObject, boolean reflect) {
        PropertyDescriptor descriptor = new PropertyDescriptor();

        // Following QuickJS js_obj_to_desc: use HasProperty to check field existence.
        // {value: undefined} is a valid descriptor with an explicit value field.

        if (descriptorObject.has(PropertyKey.ENUMERABLE)) {
            JSValue enumerable = descriptorObject.get(PropertyKey.ENUMERABLE);
            descriptor.setEnumerable(JSTypeConversions.toBoolean(enumerable) == JSBoolean.TRUE);
        }

        if (descriptorObject.has(PropertyKey.CONFIGURABLE)) {
            JSValue configurable = descriptorObject.get(PropertyKey.CONFIGURABLE);
            descriptor.setConfigurable(JSTypeConversions.toBoolean(configurable) == JSBoolean.TRUE);
        }

        if (descriptorObject.has(PropertyKey.VALUE)) {
            JSValue value = descriptorObject.get(PropertyKey.VALUE);
            descriptor.setValue(value);
        }

        if (descriptorObject.has(PropertyKey.WRITABLE)) {
            JSValue writable = descriptorObject.get(PropertyKey.WRITABLE);
            descriptor.setWritable(JSTypeConversions.toBoolean(writable) == JSBoolean.TRUE);
        }

        if (descriptorObject.has(PropertyKey.GET)) {
            JSValue get = descriptorObject.get(PropertyKey.GET);
            if (!(get instanceof JSUndefined) && !(get instanceof JSFunction)) {
                return context.rejectTypeError(reflect ? "invalid getter" : "Getter must be a function");
            }
            descriptor.setGetter(get instanceof JSFunction getter ? getter : null);
        }

        if (descriptorObject.has(PropertyKey.SET)) {
            JSValue set = descriptorObject.get(PropertyKey.SET);
            if (!(set instanceof JSUndefined) && !(set instanceof JSFunction)) {
                return context.rejectTypeError(reflect ? "invalid setter" : "Setter must be a function");
            }
            descriptor.setSetter(set instanceof JSFunction setter ? setter : null);
        }

        if (descriptor.isAccessorDescriptor() && descriptor.isDataDescriptor()) {
            String message = "Invalid property descriptor. Cannot both specify accessors and a value or writable attribute";
            return context.rejectTypeError(reflect ? message : message + ", #<Object>");
        }

        return descriptor;
    }

    public enum AccessorState {
        All(true, true),
        Configurable(false, true),
        Enumerable(true, false),
        None(false, false);

        private final boolean configurable;
        private final boolean enumerable;

        AccessorState(boolean enumerable, boolean configurable) {
            this.enumerable = enumerable;
            this.configurable = configurable;
        }

        public boolean isConfigurable() {
            return configurable;
        }

        public boolean isEnumerable() {
            return enumerable;
        }
    }

    public enum DataState {
        All(true, true, true),
        Configurable(false, false, true),
        ConfigurableWritable(true, false, true),
        Enumerable(false, true, false),
        EnumerableConfigurable(false, true, true),
        EnumerableWritable(true, true, false),
        None(false, false, false),
        Writable(true, false, false);

        private final boolean configurable;
        private final boolean enumerable;
        private final boolean writable;

        DataState(boolean writable, boolean enumerable, boolean configurable) {
            this.writable = writable;
            this.enumerable = enumerable;
            this.configurable = configurable;
        }

        public boolean isConfigurable() {
            return configurable;
        }

        public boolean isEnumerable() {
            return enumerable;
        }

        public boolean isWritable() {
            return writable;
        }

    }
}
