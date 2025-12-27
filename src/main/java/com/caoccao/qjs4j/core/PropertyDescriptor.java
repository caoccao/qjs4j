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
 * Represents a JavaScript property descriptor.
 * Based on ECMAScript specification and QuickJS implementation.
 * <p>
 * A property can be either:
 * - Data descriptor: has value and writable
 * - Accessor descriptor: has getter and/or setter
 * <p>
 * Both types can have enumerable and configurable attributes.
 */
public final class PropertyDescriptor {
    // Attribute flags
    private static final int FLAG_WRITABLE = 1 << 0;
    private static final int FLAG_ENUMERABLE = 1 << 1;
    private static final int FLAG_CONFIGURABLE = 1 << 2;
    private static final int FLAG_HAS_VALUE = 1 << 3;
    private static final int FLAG_HAS_WRITABLE = 1 << 4;
    private static final int FLAG_HAS_ENUMERABLE = 1 << 5;
    private static final int FLAG_HAS_CONFIGURABLE = 1 << 6;
    private static final int FLAG_HAS_GET = 1 << 7;
    private static final int FLAG_HAS_SET = 1 << 8;

    private int flags;
    private JSValue value;
    private JSFunction getter;
    private JSFunction setter;

    public PropertyDescriptor() {
        this.flags = 0;
    }

    /**
     * Create a data descriptor.
     */
    public static PropertyDescriptor dataDescriptor(JSValue value, boolean writable,
                                                    boolean enumerable, boolean configurable) {
        PropertyDescriptor desc = new PropertyDescriptor();
        desc.setValue(value);
        desc.setWritable(writable);
        desc.setEnumerable(enumerable);
        desc.setConfigurable(configurable);
        return desc;
    }

    /**
     * Create an accessor descriptor.
     */
    public static PropertyDescriptor accessorDescriptor(JSFunction getter, JSFunction setter,
                                                        boolean enumerable, boolean configurable) {
        PropertyDescriptor desc = new PropertyDescriptor();
        if (getter != null) desc.setGetter(getter);
        if (setter != null) desc.setSetter(setter);
        desc.setEnumerable(enumerable);
        desc.setConfigurable(configurable);
        return desc;
    }

    /**
     * Create a default data descriptor (writable, enumerable, configurable).
     */
    public static PropertyDescriptor defaultData(JSValue value) {
        return dataDescriptor(value, true, true, true);
    }

    // Getters

    public boolean isWritable() {
        return (flags & FLAG_WRITABLE) != 0;
    }

    public boolean isEnumerable() {
        return (flags & FLAG_ENUMERABLE) != 0;
    }

    public boolean isConfigurable() {
        return (flags & FLAG_CONFIGURABLE) != 0;
    }

    public JSValue getValue() {
        return value;
    }

    public JSFunction getGetter() {
        return getter;
    }

    public JSFunction getSetter() {
        return setter;
    }

    // "Has" checks for partial descriptors

    public boolean hasValue() {
        return (flags & FLAG_HAS_VALUE) != 0;
    }

    public boolean hasWritable() {
        return (flags & FLAG_HAS_WRITABLE) != 0;
    }

    public boolean hasEnumerable() {
        return (flags & FLAG_HAS_ENUMERABLE) != 0;
    }

    public boolean hasConfigurable() {
        return (flags & FLAG_HAS_CONFIGURABLE) != 0;
    }

    public boolean hasGetter() {
        return (flags & FLAG_HAS_GET) != 0;
    }

    public boolean hasSetter() {
        return (flags & FLAG_HAS_SET) != 0;
    }

    // Setters

    public void setWritable(boolean writable) {
        flags |= FLAG_HAS_WRITABLE;
        if (writable) {
            flags |= FLAG_WRITABLE;
        } else {
            flags &= ~FLAG_WRITABLE;
        }
    }

    public void setEnumerable(boolean enumerable) {
        flags |= FLAG_HAS_ENUMERABLE;
        if (enumerable) {
            flags |= FLAG_ENUMERABLE;
        } else {
            flags &= ~FLAG_ENUMERABLE;
        }
    }

    public void setConfigurable(boolean configurable) {
        flags |= FLAG_HAS_CONFIGURABLE;
        if (configurable) {
            flags |= FLAG_CONFIGURABLE;
        } else {
            flags &= ~FLAG_CONFIGURABLE;
        }
    }

    public void setValue(JSValue value) {
        this.value = value;
        flags |= FLAG_HAS_VALUE;
    }

    public void setGetter(JSFunction getter) {
        this.getter = getter;
        flags |= FLAG_HAS_GET;
    }

    public void setSetter(JSFunction setter) {
        this.setter = setter;
        flags |= FLAG_HAS_SET;
    }

    // Type checks

    /**
     * Check if this is a data descriptor.
     * A data descriptor has value or writable attributes.
     */
    public boolean isDataDescriptor() {
        return hasValue() || hasWritable();
    }

    /**
     * Check if this is an accessor descriptor.
     * An accessor descriptor has getter or setter attributes.
     */
    public boolean isAccessorDescriptor() {
        return hasGetter() || hasSetter();
    }

    /**
     * Check if this is a generic descriptor.
     * A generic descriptor has neither data nor accessor attributes.
     */
    public boolean isGenericDescriptor() {
        return !isDataDescriptor() && !isAccessorDescriptor();
    }

    /**
     * Check if descriptor is empty (no attributes set).
     */
    public boolean isEmpty() {
        return flags == 0;
    }

    /**
     * Complete this descriptor with default values.
     * Used when defining a new property.
     */
    public void completeAsData() {
        if (!hasValue()) setValue(JSUndefined.INSTANCE);
        if (!hasWritable()) setWritable(false);
        if (!hasEnumerable()) setEnumerable(false);
        if (!hasConfigurable()) setConfigurable(false);
    }

    /**
     * Complete this descriptor with default values for accessor.
     */
    public void completeAsAccessor() {
        if (!hasGetter()) setGetter(null);
        if (!hasSetter()) setSetter(null);
        if (!hasEnumerable()) setEnumerable(false);
        if (!hasConfigurable()) setConfigurable(false);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PropertyDescriptor{");
        if (hasValue()) sb.append("value=").append(value).append(", ");
        if (hasWritable()) sb.append("writable=").append(isWritable()).append(", ");
        if (hasEnumerable()) sb.append("enumerable=").append(isEnumerable()).append(", ");
        if (hasConfigurable()) sb.append("configurable=").append(isConfigurable()).append(", ");
        if (hasGetter()) sb.append("get=").append(getter != null ? "function" : "undefined").append(", ");
        if (hasSetter()) sb.append("set=").append(setter != null ? "function" : "undefined").append(", ");
        if (sb.length() > 19) sb.setLength(sb.length() - 2); // Remove trailing ", "
        sb.append("}");
        return sb.toString();
    }
}
