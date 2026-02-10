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
    public static final String NAME = "Map";
    // Use LinkedHashMap to maintain insertion order
    // Use a wrapper class for keys to handle JSValue equality properly
    private final Map<KeyWrapper, JSValue> data;

    /**
     * Create an empty Map.
     */
    public JSMap() {
        super();
        this.data = new LinkedHashMap<>();
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // Create Map object
        JSMap mapObj = new JSMap();
        // If an iterable is provided, populate the map
        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];
            // Handle array directly for efficiency
            if (iterableArg instanceof JSArray arr) {
                for (long i = 0; i < arr.getLength(); i++) {
                    JSValue entry = arr.get((int) i);
                    if (!(entry instanceof JSObject entryObj)) {
                        return context.throwTypeError("Iterator value must be an object");
                    }
                    // Get key and value from entry [key, value]
                    JSValue key = entryObj.get(0);
                    JSValue value = entryObj.get(1);
                    mapObj.mapSet(key, value);
                }
            } else {
                // Follow the generic iterator protocol for all iterable inputs.
                JSValue iterator = JSIteratorHelper.getIterator(context, iterableArg);
                if (iterator == null) {
                    return context.throwTypeError("Object is not iterable");
                }
                while (true) {
                    JSObject nextResult = JSIteratorHelper.iteratorNext(iterator, context);
                    if (nextResult == null) {
                        return context.throwTypeError("Iterator result must be an object");
                    }
                    JSValue done = nextResult.get("done");
                    if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                        break;
                    }
                    JSValue entry = nextResult.get("value");
                    if (!(entry instanceof JSObject entryObj)) {
                        return context.throwTypeError("Iterator value must be an object");
                    }
                    // Get key and value from entry [key, value]
                    JSValue key = entryObj.get(0);
                    JSValue value = entryObj.get(1);
                    mapObj.mapSet(key, value);
                }
            }
        }
        context.transferPrototype(mapObj, NAME);
        return mapObj;
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
     * Clear all entries from the Map.
     */
    public void mapClear() {
        data.clear();
    }

    /**
     * Delete a key from the Map.
     */
    public boolean mapDelete(JSValue key) {
        return data.remove(new KeyWrapper(key)) != null;
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
     * Set a key-value pair in the Map.
     */
    public void mapSet(JSValue key, JSValue value) {
        data.put(new KeyWrapper(key), value);
    }

    /**
     * Get the number of entries in the Map.
     */
    public int size() {
        return data.size();
    }

    @Override
    public String toString() {
        return "[object Map]";
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
    public record KeyWrapper(JSValue value) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof KeyWrapper other)) return false;

            // SameValueZero algorithm
            return sameValueZero(this.value, other.value);
        }

        @Override
        public int hashCode() {
            // Must ensure that values that are equal according to sameValueZero
            // have the same hash code
            if (value instanceof JSNumber num) {
                double d = num.value();
                // Normalize NaN to a canonical value
                if (Double.isNaN(d)) {
                    return Double.hashCode(Double.NaN);
                }
                // Normalize -0.0 to +0.0 for hash code consistency
                // (SameValueZero treats +0 and -0 as equal)
                if (d == 0.0) {
                    return Double.hashCode(0.0);
                }
                return Double.hashCode(d);
            }
            if (value instanceof JSString str) {
                return str.value().hashCode();
            }
            if (value instanceof JSBoolean bool) {
                return Boolean.hashCode(bool.value());
            }
            if (value instanceof JSBigInt bigInt) {
                return bigInt.value().hashCode();
            }
            if (value instanceof JSNull) {
                return 0; // All null values are the same
            }
            if (value instanceof JSUndefined) {
                return 1; // All undefined values are the same
            }
            // For objects and symbols, use identity hash code
            return System.identityHashCode(value);
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
                return xStr.value().equals(yStr.value());
            }

            // Booleans
            if (x instanceof JSBoolean xBool && y instanceof JSBoolean yBool) {
                return xBool.value() == yBool.value();
            }

            // BigInt
            if (x instanceof JSBigInt xBig && y instanceof JSBigInt yBig) {
                return xBig.value().equals(yBig.value());
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
}
