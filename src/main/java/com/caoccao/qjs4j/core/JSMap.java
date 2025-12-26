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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a JavaScript Map object.
 * Maps maintain insertion order and use SameValueZero equality for keys.
 */
public final class JSMap extends JSObject {
    // Use LinkedHashMap to maintain insertion order
    // Use a wrapper class for keys to handle JSValue equality properly
    private final LinkedHashMap<KeyWrapper, JSValue> data;

    /**
     * Create an empty Map.
     */
    public JSMap() {
        super();
        this.data = new LinkedHashMap<>();
    }

    /**
     * Get the number of entries in the Map.
     */
    public int size() {
        return data.size();
    }

    /**
     * Set a key-value pair in the Map.
     */
    public void mapSet(JSValue key, JSValue value) {
        data.put(new KeyWrapper(key), value);
    }

    /**
     * Get a value from the Map by key.
     */
    public JSValue mapGet(JSValue key) {
        JSValue value = data.get(new KeyWrapper(key));
        return value != null ? value : JSUndefined.INSTANCE;
    }

    /**
     * Check if the Map has a key.
     */
    public boolean mapHas(JSValue key) {
        return data.containsKey(new KeyWrapper(key));
    }

    /**
     * Delete a key from the Map.
     */
    public boolean mapDelete(JSValue key) {
        return data.remove(new KeyWrapper(key)) != null;
    }

    /**
     * Clear all entries from the Map.
     */
    public void mapClear() {
        data.clear();
    }

    /**
     * Get all entries as an iterable.
     */
    public Iterable<Map.Entry<KeyWrapper, JSValue>> entries() {
        return data.entrySet();
    }

    /**
     * Get all keys as an iterable.
     */
    public Iterable<KeyWrapper> keys() {
        return data.keySet();
    }

    /**
     * Get all values as an iterable.
     */
    public Iterable<JSValue> values() {
        return data.values();
    }

    /**
     * Wrapper class for Map keys to handle JSValue equality using SameValueZero.
     * SameValueZero is like === except NaN equals NaN.
     */
    public static class KeyWrapper {
        private final JSValue value;

        public KeyWrapper(JSValue value) {
            this.value = value;
        }

        public JSValue getValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof KeyWrapper other)) return false;

            // SameValueZero algorithm
            return sameValueZero(this.value, other.value);
        }

        @Override
        public int hashCode() {
            // For numbers, use the numeric value for hash
            if (value instanceof JSNumber num) {
                double d = num.value();
                // NaN values should hash the same
                if (Double.isNaN(d)) {
                    return Double.hashCode(Double.NaN);
                }
                // +0 and -0 should hash the same (SameValueZero)
                if (d == 0.0) {
                    return Double.hashCode(0.0);
                }
                return Double.hashCode(d);
            } else if (value instanceof JSString str) {
                return str.getValue().hashCode();
            } else if (value instanceof JSBoolean bool) {
                return Boolean.hashCode(bool.value());
            } else if (value instanceof JSNull) {
                return "null".hashCode();
            } else if (value instanceof JSUndefined) {
                return "undefined".hashCode();
            } else if (value instanceof JSSymbol sym) {
                return sym.getId();
            } else if (value instanceof JSBigInt bigInt) {
                return bigInt.getValue().hashCode();
            } else {
                // For objects, use identity hash
                return System.identityHashCode(value);
            }
        }

        /**
         * SameValueZero comparison.
         * Like === but NaN equals NaN, and +0 equals -0.
         */
        private boolean sameValueZero(JSValue x, JSValue y) {
            // Same reference
            if (x == y) return true;

            // Different types
            if (x.type() != y.type()) return false;

            // Numbers
            if (x instanceof JSNumber xNum && y instanceof JSNumber yNum) {
                double xVal = xNum.value();
                double yVal = yNum.value();

                // NaN == NaN in SameValueZero
                if (Double.isNaN(xVal) && Double.isNaN(yVal)) {
                    return true;
                }

                // +0 == -0 in SameValueZero
                return xVal == yVal;
            }

            // Strings
            if (x instanceof JSString xStr && y instanceof JSString yStr) {
                return xStr.getValue().equals(yStr.getValue());
            }

            // Booleans
            if (x instanceof JSBoolean xBool && y instanceof JSBoolean yBool) {
                return xBool.value() == yBool.value();
            }

            // BigInt
            if (x instanceof JSBigInt xBig && y instanceof JSBigInt yBig) {
                return xBig.getValue().equals(yBig.getValue());
            }

            // Symbols (compare by identity, each Symbol is unique except well-known ones)
            if (x instanceof JSSymbol && y instanceof JSSymbol) {
                return x == y;
            }

            // null and undefined
            if (x instanceof JSNull && y instanceof JSNull) return true;
            if (x instanceof JSUndefined && y instanceof JSUndefined) return true;

            // Objects (compare by identity)
            return x == y;
        }
    }

    @Override
    public String toString() {
        return "[object Map]";
    }
}
