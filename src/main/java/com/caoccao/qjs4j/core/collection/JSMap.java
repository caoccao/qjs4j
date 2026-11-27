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
    private final OrderedHashTable<EntryRecord> data;

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
        for (EntryRecord entryRecord : data.values()) {
            entries.add(Map.entry(entryRecord.keyWrapper, entryRecord.value));
        }
        return entries;
    }

    /**
     * Get all keys as an iterable.
     */
    public Iterable<KeyWrapper> keys() {
        List<KeyWrapper> keys = new ArrayList<>(data.size());
        for (EntryRecord entryRecord : data.values()) {
            keys.add(entryRecord.keyWrapper);
        }
        return keys;
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
        KeyWrapper keyWrapper = new KeyWrapper(normalizeKey(key));
        EntryRecord removedRecord = data.remove(keyWrapper);
        if (removedRecord == null) {
            return false;
        }
        return true;
    }

    /**
     * Get a value from the Map by key.
     */
    public JSValue mapGet(JSValue key) {
        EntryRecord record = data.get(new KeyWrapper(normalizeKey(key)));
        return record != null ? record.value : JSUndefined.INSTANCE;
    }

    /**
     * Check if the Map has a key.
     */
    public boolean mapHas(JSValue key) {
        return data.containsKey(new KeyWrapper(normalizeKey(key)));
    }

    /**
     * Set a key-value pair in the Map.
     */
    public void mapSet(JSValue key, JSValue value) {
        KeyWrapper keyWrapper = new KeyWrapper(normalizeKey(key));
        EntryRecord existingRecord = data.get(keyWrapper);
        if (existingRecord != null) {
            existingRecord.value = value;
            return;
        }
        EntryRecord record = new EntryRecord(keyWrapper, value);
        data.put(keyWrapper, record);
    }

    public IterationEntry nextIterationEntry(IterationCursor cursor) {
        EntryRecord record = cursor.cursor.next();
        return record == null ? null : new IterationEntry(record.keyWrapper.value(), record.value);
    }

    private JSValue normalizeKey(JSValue key) {
        if (key instanceof JSNumber number && number.value() == 0.0) {
            return JSNumber.of(0.0);
        }
        return key;
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
        for (EntryRecord entryRecord : data.values()) {
            values.add(entryRecord.value);
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

    private static final class EntryRecord {
        private final KeyWrapper keyWrapper;
        private JSValue value;

        private EntryRecord(KeyWrapper keyWrapper, JSValue value) {
            this.keyWrapper = keyWrapper;
            this.value = value;
        }
    }

    public static final class IterationCursor {
        private final OrderedHashTable.Cursor<EntryRecord> cursor;

        private IterationCursor(OrderedHashTable.Cursor<EntryRecord> cursor) {
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
