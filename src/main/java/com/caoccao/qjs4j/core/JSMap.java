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

import java.util.*;

/**
 * Represents a JavaScript Map object.
 * Maps maintain insertion order and use SameValueZero equality for keys.
 */
public final class JSMap extends JSObject {
    public static final String NAME = "Map";
    private final Map<KeyWrapper, EntryRecord> data;
    private final Map<Long, EntryRecord> entriesById;
    private long nextEntryId;

    /**
     * Create an empty Map.
     */
    public JSMap() {
        super();
        this.data = new LinkedHashMap<>();
        this.entriesById = new HashMap<>();
        this.nextEntryId = 1;
    }

    private static void closeIterator(JSContext context, JSValue iterator) {
        if (!(iterator instanceof JSObject iteratorObject)) {
            return;
        }
        JSValue pendingException = context.getPendingException();
        if (pendingException != null) {
            context.clearPendingException();
        }
        JSValue returnMethod = iteratorObject.get(PropertyKey.RETURN);
        if (returnMethod instanceof JSFunction returnFunction) {
            returnFunction.call(context, iterator, new JSValue[0]);
        }
        if (pendingException != null) {
            context.setPendingException(pendingException);
        }
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSMap mapObj = context.createJSMap();

        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];

            JSValue adder = mapObj.get(PropertyKey.SET);
            if (!(adder instanceof JSFunction adderFunction)) {
                return context.throwTypeError("set/add is not a function");
            }

            JSValue iterator = JSIteratorHelper.getIterator(context, iterableArg);
            if (!(iterator instanceof JSObject)) {
                return context.throwTypeError("Object is not iterable");
            }

            while (true) {
                JSObject nextResult;
                try {
                    nextResult = JSIteratorHelper.iteratorNext(iterator, context);
                } catch (RuntimeException e) {
                    closeIterator(context, iterator);
                    throw e;
                }
                if (nextResult instanceof JSError) {
                    closeIterator(context, iterator);
                    return nextResult;
                }
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    JSValue pendingException = context.getPendingException();
                    if (pendingException instanceof JSObject pendingObject) {
                        return pendingObject;
                    }
                    return context.throwTypeError("Map constructor failed");
                }
                if (nextResult == null) {
                    closeIterator(context, iterator);
                    return context.throwTypeError("Iterator result must be an object");
                }
                JSValue done = nextResult.get("done");
                if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                    break;
                }

                JSValue entry = nextResult.get(PropertyKey.VALUE);
                if (!(entry instanceof JSObject entryObj)) {
                    closeIterator(context, iterator);
                    return context.throwTypeError("Iterator value must be an object");
                }

                JSValue key = entryObj.get(0);
                JSValue value = entryObj.get(1);
                JSValue adderResult;
                try {
                    adderResult = adderFunction.call(context, mapObj, new JSValue[]{key, value});
                } catch (RuntimeException e) {
                    closeIterator(context, iterator);
                    throw e;
                }
                if (adderResult instanceof JSError || context.hasPendingException()) {
                    closeIterator(context, iterator);
                    if (adderResult instanceof JSObject adderResultObject) {
                        return adderResultObject;
                    }
                    JSValue pendingException = context.getPendingException();
                    if (pendingException instanceof JSObject pendingObject) {
                        return pendingObject;
                    }
                    return context.throwTypeError("Map constructor failed");
                }
            }
        }
        return mapObj;
    }

    public IterationCursor createIterationCursor() {
        IterationCursor cursor = new IterationCursor();
        refreshIterationCursor(cursor);
        return cursor;
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
        entriesById.clear();
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
        entriesById.remove(removedRecord.id);
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
        EntryRecord record = new EntryRecord(nextEntryId++, keyWrapper, value);
        data.put(keyWrapper, record);
        entriesById.put(record.id, record);
    }

    public IterationEntry nextIterationEntry(IterationCursor cursor) {
        while (true) {
            while (cursor.index < cursor.orderedIds.size()) {
                long entryId = cursor.orderedIds.get(cursor.index++);
                EntryRecord record = entriesById.get(entryId);
                if (record != null) {
                    return new IterationEntry(record.keyWrapper.value(), record.value);
                }
            }
            if (!refreshIterationCursor(cursor)) {
                return null;
            }
        }
    }

    private JSValue normalizeKey(JSValue key) {
        if (key instanceof JSNumber number && number.value() == 0.0) {
            return JSNumber.of(0.0);
        }
        return key;
    }

    private boolean refreshIterationCursor(IterationCursor cursor) {
        boolean appended = false;
        for (EntryRecord record : data.values()) {
            if (cursor.seenIds.add(record.id)) {
                cursor.orderedIds.add(record.id);
                appended = true;
            }
        }
        return appended;
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

    private static final class EntryRecord {
        private final long id;
        private final KeyWrapper keyWrapper;
        private JSValue value;

        private EntryRecord(long id, KeyWrapper keyWrapper, JSValue value) {
            this.id = id;
            this.keyWrapper = keyWrapper;
            this.value = value;
        }
    }

    public static final class IterationCursor {
        private final List<Long> orderedIds;
        private final Set<Long> seenIds;
        private int index;

        private IterationCursor() {
            this.orderedIds = new ArrayList<>();
            this.seenIds = new HashSet<>();
            this.index = 0;
        }
    }

    public record IterationEntry(JSValue key, JSValue value) {
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
