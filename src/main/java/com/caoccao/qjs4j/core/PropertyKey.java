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

import java.util.Objects;

/**
 * Represents a property key (can be string, symbol, or integer index).
 * Based on ECMAScript property keys and QuickJS atom system.
 * <p>
 * In JavaScript, property keys can be:
 * - Strings (most common)
 * - Symbols (for unique properties)
 * - Integer indices (for array-like objects)
 */
public final class PropertyKey {
    public static final PropertyKey ARRAY = fromString("Array");
    public static final PropertyKey CALLEE = fromString("callee");
    public static final PropertyKey CAUSE = fromString("cause");
    public static final PropertyKey CONFIGURABLE = fromString("configurable");
    public static final PropertyKey CONSTRUCTOR = fromString("constructor");
    public static final PropertyKey DONE = fromString("done");
    public static final PropertyKey ENUMERABLE = fromString("enumerable");
    public static final PropertyKey ERROR = fromString("error");
    public static final PropertyKey GET = fromString("get");
    public static final PropertyKey HAS = fromString("has");
    public static final PropertyKey ITERATOR = fromString("iterator");
    public static final PropertyKey ITERATOR_CAP = fromString("Iterator");
    public static final PropertyKey KEYS = fromString("keys");
    public static final PropertyKey LAST_INDEX = fromString("lastIndex");
    public static final PropertyKey LENGTH = fromString("length");
    public static final PropertyKey MESSAGE = fromString("message");
    public static final PropertyKey NAME = fromString("name");
    public static final PropertyKey NEXT = fromString("next");
    public static final PropertyKey ONE = fromString("1");
    public static final PropertyKey PROTO = fromString("__proto__");
    public static final PropertyKey PROTOTYPE = fromString("prototype");
    public static final PropertyKey RETURN = fromString("return");
    public static final PropertyKey SET = fromString("set");
    public static final PropertyKey SIZE = fromString("size");
    public static final PropertyKey SUPPRESSED = fromString("suppressed");
    public static final PropertyKey SYMBOL = fromString("Symbol");
    public static final PropertyKey SYMBOL_ASYNC_DISPOSE = fromSymbol(JSSymbol.ASYNC_DISPOSE);
    public static final PropertyKey SYMBOL_ASYNC_ITERATOR = fromSymbol(JSSymbol.ASYNC_ITERATOR);
    public static final PropertyKey SYMBOL_DISPOSE = fromSymbol(JSSymbol.DISPOSE);
    public static final PropertyKey SYMBOL_HAS_INSTANCE = fromSymbol(JSSymbol.HAS_INSTANCE);
    public static final PropertyKey SYMBOL_IS_CONCAT_SPREADABLE = fromSymbol(JSSymbol.IS_CONCAT_SPREADABLE);
    public static final PropertyKey SYMBOL_ITERATOR = fromSymbol(JSSymbol.ITERATOR);
    public static final PropertyKey SYMBOL_MATCH = fromSymbol(JSSymbol.MATCH);
    public static final PropertyKey SYMBOL_MATCH_ALL = fromSymbol(JSSymbol.MATCH_ALL);
    public static final PropertyKey SYMBOL_REPLACE = fromSymbol(JSSymbol.REPLACE);
    public static final PropertyKey SYMBOL_SEARCH = fromSymbol(JSSymbol.SEARCH);
    public static final PropertyKey SYMBOL_SPECIES = fromSymbol(JSSymbol.SPECIES);
    public static final PropertyKey SYMBOL_SPLIT = fromSymbol(JSSymbol.SPLIT);
    public static final PropertyKey SYMBOL_TO_PRIMITIVE = fromSymbol(JSSymbol.TO_PRIMITIVE);
    public static final PropertyKey SYMBOL_TO_STRING_TAG = fromSymbol(JSSymbol.TO_STRING_TAG);
    public static final PropertyKey SYMBOL_UNSCOPABLES = fromSymbol(JSSymbol.UNSCOPABLES);
    public static final PropertyKey THROW = fromString("throw");
    public static final PropertyKey TO_ISO_STRING = fromString("toISOString");
    public static final PropertyKey TO_STRING = fromString("toString");
    public static final PropertyKey VALUE = fromString("value");
    public static final PropertyKey WRITABLE = fromString("writable");
    public static final PropertyKey ZERO = fromString("0");
    private final int atomIndex; // -1 if not interned
    private final Object value; // String, Integer, or JSSymbol

    private PropertyKey(Object value, int atomIndex) {
        this.value = value;
        this.atomIndex = atomIndex;
    }

    /**
     * Create a property key from an interned string (atom).
     */
    public static PropertyKey fromAtom(String str, int atomIndex) {
        return new PropertyKey(str, atomIndex);
    }

    /**
     * Create a property key from an integer index.
     */
    public static PropertyKey fromIndex(int index) {
        return new PropertyKey(index, -1);
    }

    /**
     * Create a property key from a string.
     */
    public static PropertyKey fromString(String str) {
        return new PropertyKey(str, -1);
    }

    /**
     * Create a property key from a symbol.
     */
    public static PropertyKey fromSymbol(JSSymbol symbol) {
        return new PropertyKey(symbol, -1);
    }

    /**
     * Create a property key from a JSValue.
     * Converts the value to an appropriate key.
     */
    public static PropertyKey fromValue(JSContext context, JSValue value) {
        if (value instanceof JSString s) {
            return fromString(s.value());
        }
        if (value instanceof JSSymbol s) {
            return fromSymbol(s);
        }
        if (value instanceof JSNumber n) {
            // Check if it's an array index
            double d = n.value();
            if (d >= 0 && d < 0xFFFFFFFFL && d == Math.floor(d)) {
                return fromIndex((int) d);
            }
        }
        // Convert to string for other types
        JSString str = JSTypeConversions.toString(context, value);
        return fromString(str.value());
    }

    /**
     * Get the integer value (if this is an index key).
     */
    public int asIndex() {
        return value instanceof Integer i ? i : -1;
    }

    /**
     * Get the string value (if this is a string key).
     */
    public String asString() {
        return value instanceof String s ? s : null;
    }

    /**
     * Get the symbol value (if this is a symbol key).
     */
    public JSSymbol asSymbol() {
        return value instanceof JSSymbol s ? s : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PropertyKey other)) return false;

        // Fast path: if both have atom indices, compare them
        if (atomIndex >= 0 && other.atomIndex >= 0) {
            return atomIndex == other.atomIndex;
        }

        // Compare values
        return Objects.equals(value, other.value);
    }

    public int getAtomIndex() {
        return atomIndex;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        // Use atom index for faster hashing if available
        if (atomIndex >= 0) {
            return atomIndex;
        }
        return Objects.hashCode(value);
    }

    /**
     * Check if this key is an integer index.
     */
    public boolean isIndex() {
        return value instanceof Integer;
    }

    /**
     * Check if this key is interned.
     */
    public boolean isInterned() {
        return atomIndex >= 0;
    }

    /**
     * Check if this key is a string.
     */
    public boolean isString() {
        return value instanceof String;
    }

    /**
     * Check if this key is a symbol.
     */
    public boolean isSymbol() {
        return value instanceof JSSymbol;
    }

    /**
     * Convert to a string representation.
     */
    public String toPropertyString() {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Integer i) {
            return i.toString();
        }
        if (value instanceof JSSymbol s) {
            return s.toJavaObject();
        }
        return value.toString();
    }

    @Override
    public String toString() {
        return "PropertyKey{" + toPropertyString() +
                (atomIndex >= 0 ? ", atom=" + atomIndex : "") +
                "}";
    }
}
