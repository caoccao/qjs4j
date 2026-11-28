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

package com.caoccao.qjs4j.core.collection;

import com.caoccao.qjs4j.core.*;

import java.util.*;

/**
 * Represents a JavaScript Map object. Maps maintain insertion order and use SameValueZero equality for keys.
 */
public final class JSMap extends JSObject {
    public static final String NAME = "Map";
    private final OrderedHashTable<JSValue> data;

    /**
     * Create an empty Map.
     */
    public JSMap(JSContext context) {
        super(context);
        this.data = new OrderedHashTable<>();
    }

    public IterationCursor createIterationCursor() {
        return new IterationCursor(data.cursor());
    }

    /**
     * Get all entries as an iterable.
     */
    public Iterable<Map.Entry<KeyWrapper, JSValue>> entries() {
        List<Map.Entry<KeyWrapper, JSValue>> entries = new ArrayList<>(data.size());
        var cursor = data.cursor();
        for (var node = cursor.nextEntry(); node != null; node = cursor.nextEntry()) {
            entries.add(Map.entry(node.key(), node.value()));
        }
        return entries;
    }

    /**
     * Get all keys as an iterable.
     */
    public Iterable<KeyWrapper> keys() {
        return data.keys();
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
        return data.remove(key);
    }

    /**
     * Get a value from the Map by key.
     */
    public JSValue mapGet(JSValue key) {
        return data.getOrDefault(KeyWrapper.forCollection(key), JSUndefined.INSTANCE);
    }

    /**
     * Check if the Map has a key.
     */
    public boolean mapHas(JSValue key) {
        return data.containsKey(key);
    }

    /**
     * Set a key-value pair in the Map.
     */
    public void mapSet(JSValue key, JSValue value) {
        data.put(KeyWrapper.forCollection(key), value);
    }

    public IterationEntry nextIterationEntry(IterationCursor cursor) {
        var node = cursor.cursor.nextEntry();
        return node == null ? null : new IterationEntry(node.key().value(), node.value());
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
        List<JSValue> values = new ArrayList<>(data.size());
        for (JSValue value : data.values()) {
            values.add(value);
        }
        return values;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSMap mapObj = context.createJSMap();
        CollectionInitializer.initializePrototypeFromNewTarget(context, mapObj, NAME);
        if (context.hasPendingException()) {
            return mapObj;
        }
        return CollectionInitializer.initializeFromIterable(context, mapObj, args, true);
    }

    public static final class IterationCursor {
        private final OrderedHashTable.Cursor<JSValue> cursor;

        private IterationCursor(OrderedHashTable.Cursor<JSValue> cursor) {
            this.cursor = cursor;
        }
    }

    public record IterationEntry(JSValue key, JSValue value) {
    }

    /**
     * Wrapper class for Map keys to handle JSValue equality using SameValueZero. SameValueZero is like === except NaN
     * equals NaN.
     */
    public record KeyWrapper(JSValue value) {
        /** Canonicalize negative zero when storing or looking up a keyed collection entry. */
        static KeyWrapper forCollection(JSValue value) {
            return new KeyWrapper(value instanceof JSNumber number && number.value() == 0.0 ? JSNumber.of(0.0) : value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof KeyWrapper other)) {
                return false;
            }

            // SameValueZero algorithm
            return JSTypeConversions.sameValueZero(this.value, other.value);
        }

        @Override
        public int hashCode() {
            // Must ensure that values that are equal according to sameValueZero
            // have the same hash code
            if (value instanceof JSNumber num) {
                double doubleValue = num.value();
                // Normalize NaN to a canonical value
                if (Double.isNaN(doubleValue)) {
                    return Double.hashCode(Double.NaN);
                }
                // Normalize -0.0 to +0.0 for hash code consistency
                // (SameValueZero treats +0 and -0 as equal)
                if (doubleValue == 0.0) {
                    return Double.hashCode(0.0);
                }
                return Double.hashCode(doubleValue);
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

    }
}
