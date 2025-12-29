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
 * JSProxy proxy = new JSProxy(strObj, handler, ctx);
 * }</pre>
 *
 * @see <a href="https://tc39.es/ecma262/#sec-string-objects">ECMAScript String Objects</a>
 * @see JSProxy
 * @see JSString
 */
public final class JSStringObject extends JSObject {
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
        this.set("[[PrimitiveValue]]", value);
        // String objects have a length property
        this.set("length", new JSNumber(value.value().length()));
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

    /**
     * Get the JSString value wrapped by this String object.
     *
     * @return the JSString value
     */
    public JSString getValue() {
        return value;
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
