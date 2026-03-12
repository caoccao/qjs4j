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

    public JSStringObject(JSContext context) {
        this(context, new JSString(""));
    }

    /**
     * Create a String object wrapping the given string value.
     *
     * @param value the primitive string value to wrap
     */
    public JSStringObject(JSContext context, String value) {
        this(context, new JSString(value));
    }

    /**
     * Create a String object wrapping the given JSString value.
     *
     * @param value the JSString value to wrap
     */
    public JSStringObject(JSContext context, JSString value) {
        super(context);
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
        JSObject jsObject = new JSStringObject(context, strValue);
        context.transferPrototype(jsObject, NAME);
        return jsObject;
    }

    /**
     * Parse a string as a valid non-negative integer index, ensuring the string round-trips.
     * This rejects strings like "-0", "+1", "01" which Integer.parseInt would accept
     * but are not canonical numeric index strings per ES spec.
     */
    private static int parseValidIndex(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        try {
            int index = Integer.parseInt(str);
            if (index >= 0 && String.valueOf(index).equals(str)) {
                return index;
            }
        } catch (NumberFormatException e) {
            // Not a numeric string
        }
        return -1;
    }

    @Override
    public boolean delete(PropertyKey key) {
        JSContext context = this.context;
        boolean isCharacterIndex = false;
        if (key.isIndex()) {
            int index = key.asIndex();
            isCharacterIndex = index >= 0 && index < value.value().length();
        } else if (key.isString()) {
            int index = parseValidIndex(key.asString());
            if (index >= 0 && index < value.value().length()) {
                isCharacterIndex = true;
            }
        }
        if (isCharacterIndex) {
            if (context.isStrictMode()) {
                context.throwTypeError(
                        "Cannot delete property '" + key.toPropertyString() + "' of " + getObjectDescriptionForDelete());
            }
            return false;
        }
        return super.delete(key);
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
        int index = parseValidIndex(propertyName);
        if (index >= 0 && index < value.value().length()) {
            return new JSString(String.valueOf(value.value().charAt(index)));
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
        int charIndex = resolveCharacterIndex(key);
        if (charIndex >= 0 && charIndex < value.value().length()) {
            JSValue charValue = new JSString(String.valueOf(value.value().charAt(charIndex)));
            return PropertyDescriptor.dataDescriptor(
                    charValue,
                    PropertyDescriptor.DataState.Enumerable
            );
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
        int charIndex = resolveCharacterIndex(key);
        if (charIndex >= 0 && charIndex < value.value().length()) {
            return true;
        }
        return super.hasOwnProperty(key);
    }

    /**
     * Resolve a PropertyKey to a character index for String exotic object semantics.
     * Returns -1 if the key is not a valid character index.
     */
    private int resolveCharacterIndex(PropertyKey key) {
        if (key.isIndex()) {
            return key.asIndex();
        } else if (key.isString()) {
            return parseValidIndex(key.asString());
        }
        return -1;
    }

    /**
     * Override set to reject writes to character index properties.
     * Per ES spec 10.4.3 / OrdinarySetWithOwnDescriptor, String exotic objects have
     * non-writable character index own properties, so Set must fail for those keys.
     */
    @Override
    public void set(PropertyKey key, JSValue val) {
        int charIndex = resolveCharacterIndex(key);
        if (charIndex >= 0 && charIndex < value.value().length()) {
            // Character indices are non-writable, non-configurable own properties
            this.context.throwTypeError("Cannot assign to read only property '" + key.toPropertyString()
                    + "' of object '[object String]'");
            return;
        }
        super.set(key, val);
    }

    /**
     * Override setWithResult to reject writes to character index properties.
     * Per ES spec 10.4.3, String exotic objects have non-writable character index
     * own properties, so [[Set]] must return false for those keys.
     */
    @Override
    public boolean setWithResult(PropertyKey key, JSValue value) {
        int charIndex = resolveCharacterIndex(key);
        if (charIndex >= 0 && charIndex < this.value.value().length()) {
            return false;
        }
        return super.setWithResult(key, value);
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
