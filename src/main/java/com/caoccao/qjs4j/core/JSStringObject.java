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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a JavaScript String object (wrapper) as opposed to a string primitive.
 * <p>
 * In JavaScript, there's a distinction between:
 * - String primitives: {@code "hello"}, {@code 'world'}, {@code `template`}
 * - String objects: {@code new String("hello")}, {@code new String('world')}
 * <p>
 * This class represents the object form, which is necessary for use cases like {@link JSProxy Proxy},
 * since primitive string values cannot be used as Proxy targets. A primitive string value
 * is immutable and cannot have properties, so it cannot be wrapped by a Proxy. JSStringObject
 * provides an object wrapper that can be used with Proxy while maintaining the string value.
 * <p>
 * The wrapped string value is stored in the {@code [[PrimitiveValue]]} internal slot,
 * following the ECMAScript specification pattern for String wrapper objects.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a string object for use with Proxy
 * JSStringObject strObj = new JSStringObject("hello");
 * JSProxy proxy = new JSProxy(strObj, handler, context);
 * }</pre>
 *
 * @see <a href="https://tc39.es/ecma262/#sec-string-objects">ECMAScript String Objects</a>
 * @see JSProxy
 * @see JSString
 */
public final class JSStringObject extends JSObject {
    public static final String NAME = "String";
    private final JSString value;

    /**
     * Create a String object wrapping the given string value.
     *
     * @param value the primitive string value to wrap
     */
    public JSStringObject(String value) {
        this(new JSString(value));
    }

    /**
     * Create a String object wrapping the given JSString value.
     *
     * @param value the JSString value to wrap
     */
    public JSStringObject(JSString value) {
        super();
        this.value = value;
        this.setPrimitiveValue(value);
        // String objects have a non-writable, non-enumerable, non-configurable length property
        defineProperty(PropertyKey.fromString("length"),
                PropertyDescriptor.dataDescriptor(JSNumber.of(value.value().length()), PropertyDescriptor.DataState.None));
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSString strValue;
        if (args.length == 0) {
            strValue = new JSString("");
        } else {
            strValue = JSTypeConversions.toString(context, args[0]);
        }
        JSObject jsObject = new JSStringObject(strValue);
        context.transferPrototype(jsObject, NAME);
        return jsObject;
    }

    @Override
    public boolean delete(JSContext context, PropertyKey key) {
        boolean isCharacterIndex = false;
        if (key.isIndex()) {
            int index = key.asIndex();
            isCharacterIndex = index >= 0 && index < value.value().length();
        } else if (key.isString()) {
            try {
                int index = Integer.parseInt(key.asString());
                isCharacterIndex = index >= 0 && index < value.value().length();
            } catch (NumberFormatException e) {
                isCharacterIndex = false;
            }
        }
        if (isCharacterIndex) {
            if (context != null && context.isStrictMode()) {
                context.throwTypeError(
                        "Cannot delete property '" + key.toPropertyString() + "' of " + getObjectDescriptionForDelete());
            }
            return false;
        }
        return super.delete(context, key);
    }

    /**
     * Override enumerableKeys to include character index properties for for-in enumeration.
     * String exotic objects have enumerable index properties for each character.
     */
    @Override
    public PropertyKey[] enumerableKeys() {
        int len = value.value().length();
        PropertyKey[] superKeys = super.enumerableKeys();
        List<PropertyKey> keys = new ArrayList<>(len + superKeys.length);
        for (int i = 0; i < len; i++) {
            keys.add(PropertyKey.fromString(String.valueOf(i)));
        }
        Collections.addAll(keys, superKeys);
        return keys.toArray(new PropertyKey[0]);
    }

    /**
     * Override get to support indexed character access.
     * String objects are array-like and support accessing characters by index.
     *
     * @param propertyName the property name (can be a numeric string)
     * @return the character at the index (as JSString) or the property value
     */
    @Override
    public JSValue get(String propertyName) {
        // Try to parse as numeric index
        try {
            int index = Integer.parseInt(propertyName);
            if (index >= 0 && index < value.value().length()) {
                return new JSString(String.valueOf(value.value().charAt(index)));
            }
        } catch (NumberFormatException e) {
            // Not a numeric index, fall through to normal property access
        }
        return super.get(propertyName);
    }

    /**
     * Override get to support indexed character access.
     * String objects are array-like and support accessing characters by index.
     *
     * @param index the numeric index
     * @return the character at the index (as JSString) or undefined
     */
    @Override
    public JSValue get(int index) {
        if (index >= 0 && index < value.value().length()) {
            return new JSString(String.valueOf(value.value().charAt(index)));
        }
        return super.get(index);
    }

    private String getObjectDescriptionForDelete() {
        return "[object String]";
    }

    /**
     * Override getOwnPropertyDescriptor to support special String object semantics.
     * Per ES spec, String exotic objects have indexed properties for each character
     * with {writable: false, enumerable: true, configurable: false}.
     *
     * @param key the property key
     * @return the property descriptor
     */
    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        // Check if this is an indexed property within the string bounds
        if (key.isIndex()) {
            int index = (int) key.getValue();
            if (index >= 0 && index < value.value().length()) {
                // Return descriptor for character at index
                JSValue charValue = new JSString(String.valueOf(value.value().charAt(index)));
                return PropertyDescriptor.dataDescriptor(
                        charValue,
                        PropertyDescriptor.DataState.Enumerable
                );
            }
        } else if (key.isString()) {
            // Check if string key is a valid numeric index
            try {
                int index = Integer.parseInt(key.asString());
                if (index >= 0 && index < value.value().length()) {
                    // Return descriptor for character at index
                    JSValue charValue = new JSString(String.valueOf(value.value().charAt(index)));
                    return PropertyDescriptor.dataDescriptor(
                            charValue,
                            PropertyDescriptor.DataState.Enumerable
                    );
                }
            } catch (NumberFormatException e) {
                // Not a numeric index, fall through
            }
        }

        // For non-indexed properties, use default behavior
        return super.getOwnPropertyDescriptor(key);
    }

    /**
     * Override getOwnPropertyKeys for String exotic [[OwnPropertyKeys]] (ES2024 10.4.3.2).
     * Character indices 0..length-1 are listed first in ascending order,
     * followed by any other own property keys from the parent.
     */
    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        int len = value.value().length();
        List<PropertyKey> superKeys = super.getOwnPropertyKeys();
        List<PropertyKey> keys = new ArrayList<>(len + superKeys.size());
        for (int i = 0; i < len; i++) {
            keys.add(PropertyKey.fromString(String.valueOf(i)));
        }
        keys.addAll(superKeys);
        return keys;
    }

    /**
     * Get the JSString value wrapped by this String object.
     *
     * @return the JSString value
     */
    public JSString getValue() {
        return value;
    }

    /**
     * Override hasOwnProperty for String exotic [[HasProperty]] semantics.
     * Character indices within the string bounds are own properties.
     */
    @Override
    public boolean hasOwnProperty(PropertyKey key) {
        if (key.isIndex()) {
            int index = key.asIndex();
            if (index >= 0 && index < value.value().length()) {
                return true;
            }
        } else if (key.isString()) {
            try {
                int index = Integer.parseInt(key.asString());
                if (index >= 0 && index < value.value().length()) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // Not a numeric index, fall through
            }
        }
        return super.hasOwnProperty(key);
    }

    /**
     * Override set to reject writes to character index properties.
     * Per ES spec 10.4.3 / OrdinarySetWithOwnDescriptor, String exotic objects have
     * non-writable character index own properties, so Set must fail for those keys.
     */
    @Override
    public void set(JSContext context, PropertyKey key, JSValue val) {
        int charIndex = -1;
        if (key.isIndex()) {
            charIndex = key.asIndex();
        } else if (key.isString()) {
            try {
                charIndex = Integer.parseInt(key.asString());
            } catch (NumberFormatException e) {
                // Not a numeric index
            }
        }
        if (charIndex >= 0 && charIndex < value.value().length()) {
            // Character indices are non-writable, non-configurable own properties
            if (context != null) {
                context.throwTypeError("Cannot assign to read only property '" + key.toPropertyString()
                        + "' of object '[object String]'");
            }
            return;
        }
        super.set(context, key, val);
    }

    /**
     * Override setWithResult to reject writes to character index properties.
     * Per ES spec 10.4.3, String exotic objects have non-writable character index
     * own properties, so [[Set]] must return false for those keys.
     */
    @Override
    public boolean setWithResult(JSContext context, PropertyKey key, JSValue value) {
        int charIndex = -1;
        if (key.isIndex()) {
            charIndex = key.asIndex();
        } else if (key.isString()) {
            try {
                charIndex = Integer.parseInt(key.asString());
            } catch (NumberFormatException e) {
                // Not a numeric index
            }
        }
        if (charIndex >= 0 && charIndex < this.value.value().length()) {
            return false;
        }
        return super.setWithResult(context, key, value);
    }

    @Override
    public Object toJavaObject() {
        return value.value();
    }

    @Override
    public String toString() {
        return value.value();
    }
}
