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
 * Represents a JavaScript property descriptor.
 * Based on ECMAScript specification and QuickJS implementation.
 * <p>
 * A property can be either:
 * - Data descriptor: has value and writable
 * - Accessor descriptor: has getter and/or setter
 * <p>
 * Both types can have enumerable and configurable attributes.
 * <p>
 * Optional fields use empty vs present to distinguish "not specified" from "specified".
 * Getter/setter use JSUndefined sentinel for "not specified", null for "explicitly undefined",
 * and a JSFunction instance for a real accessor.
 * All fields are initialized and never null.
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

    /**
     * Create an accessor descriptor.
     */
    public static PropertyDescriptor accessorDescriptor(
            JSFunction getter,
            JSFunction setter,
            AccessorState state) {
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
        if (enumerable.isEmpty()) {
            setEnumerable(false);
        }
        if (configurable.isEmpty()) {
            setConfigurable(false);
        }
    }

    /**
     * Complete this descriptor with default values.
     * Used when defining a new property.
     */
    public void completeAsData() {
        if (value.isEmpty()) {
            setValue(JSUndefined.INSTANCE);
        }
        if (writable.isEmpty()) {
            setWritable(false);
        }
        if (enumerable.isEmpty()) {
            setEnumerable(false);
        }
        if (configurable.isEmpty()) {
            setConfigurable(false);
        }
    }

    // Getters

    public JSFunction getGetter() {
        return getter instanceof JSFunction f ? f : null;
    }

    public JSFunction getSetter() {
        return setter instanceof JSFunction f ? f : null;
    }

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

    // "Has" checks for partial descriptors

    public boolean hasSetter() {
        return !(setter instanceof JSUndefined);
    }

    public boolean hasValue() {
        return value.isPresent();
    }

    public boolean hasWritable() {
        return writable.isPresent();
    }

    /**
     * Check if this is an accessor descriptor.
     * An accessor descriptor has getter or setter attributes.
     */
    public boolean isAccessorDescriptor() {
        return !(getter instanceof JSUndefined) || !(setter instanceof JSUndefined);
    }

    public boolean isConfigurable() {
        return configurable.orElse(false);
    }

    /**
     * Check if this is a data descriptor.
     * A data descriptor has value or writable attributes.
     */
    public boolean isDataDescriptor() {
        return value.isPresent() || writable.isPresent();
    }

    // Setters

    /**
     * Check if descriptor is empty (no attributes set).
     */
    public boolean isEmpty() {
        return value.isEmpty() && writable.isEmpty() && enumerable.isEmpty()
                && configurable.isEmpty() && getter instanceof JSUndefined && setter instanceof JSUndefined;
    }

    public boolean isEnumerable() {
        return enumerable.orElse(false);
    }

    /**
     * Check if this is a generic descriptor.
     * A generic descriptor has neither data nor accessor attributes.
     */
    public boolean isGenericDescriptor() {
        return !isDataDescriptor() && !isAccessorDescriptor();
    }

    public boolean isWritable() {
        return writable.orElse(false);
    }

    /**
     * Merge attributes from another descriptor into this one.
     * Only attributes explicitly specified in the other descriptor are updated.
     * Unspecified attributes in the other descriptor are left unchanged.
     * Handles data↔accessor descriptor type conversion by clearing
     * the old type-specific attributes when switching types.
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

    // Type checks

    public void setGetter(JSFunction getter) {
        this.getter = getter;
    }

    public void setSetter(JSFunction setter) {
        this.setter = setter;
    }

    public void setValue(JSValue value) {
        this.value = Optional.ofNullable(value);
    }

    public void setWritable(boolean writable) {
        this.writable = Optional.of(writable);
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

    public enum AccessorState {
        None(false, false),
        Enumerable(true, false),
        Configurable(false, true),
        All(true, true);

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
        None(false, false, false),
        Enumerable(false, true, false),
        EnumerableConfigurable(false, true, true),
        Configurable(false, false, true),
        ConfigurableWritable(true, false, true),
        Writable(true, false, false),
        EnumerableWritable(true, true, false),
        All(true, true, true);

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
