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
 * Represents a JavaScript Set object.
 * Sets maintain insertion order and use SameValueZero equality for values.
 */
public final class JSSet extends JSObject {
    public static final String NAME = "Set";
    private final Map<JSMap.KeyWrapper, EntryRecord> data;
    private final Map<Long, EntryRecord> entriesById;
    private long nextEntryId;

    /**
     * Create an empty Set.
     */
    public JSSet() {
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
        JSSet setObj = context.createJSSet();

        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];

            JSValue adder = setObj.get("add");
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
                    return context.throwTypeError("Set constructor failed");
                }
                if (nextResult == null) {
                    closeIterator(context, iterator);
                    return context.throwTypeError("Iterator result must be an object");
                }
                JSValue done = nextResult.get("done");
                if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                    break;
                }

                JSValue value = nextResult.get(PropertyKey.VALUE);
                JSValue adderResult;
                try {
                    adderResult = adderFunction.call(context, setObj, new JSValue[]{value});
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
                    return context.throwTypeError("Set constructor failed");
                }
            }
        }
        return setObj;
    }

    public IterationCursor createIterationCursor() {
        IterationCursor cursor = new IterationCursor();
        refreshIterationCursor(cursor);
        return cursor;
    }

    public JSValue nextIterationValue(IterationCursor cursor) {
        while (true) {
            while (cursor.index < cursor.orderedIds.size()) {
                long entryId = cursor.orderedIds.get(cursor.index++);
                EntryRecord record = entriesById.get(entryId);
                if (record != null) {
                    return record.keyWrapper.value();
                }
            }
            if (!refreshIterationCursor(cursor)) {
                return null;
            }
        }
    }

    private JSValue normalizeValue(JSValue value) {
        if (value instanceof JSNumber number && number.value() == 0.0) {
            return JSNumber.of(0.0);
        }
        return value;
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
     * Add a value to the Set.
     */
    public void setAdd(JSValue value) {
        JSMap.KeyWrapper keyWrapper = new JSMap.KeyWrapper(normalizeValue(value));
        if (data.containsKey(keyWrapper)) {
            return;
        }
        EntryRecord record = new EntryRecord(nextEntryId++, keyWrapper);
        data.put(keyWrapper, record);
        entriesById.put(record.id, record);
    }

    /**
     * Clear all values from the Set.
     */
    public void setClear() {
        data.clear();
        entriesById.clear();
    }

    /**
     * Delete a value from the Set.
     */
    public boolean setDelete(JSValue value) {
        EntryRecord removedRecord = data.remove(new JSMap.KeyWrapper(normalizeValue(value)));
        if (removedRecord == null) {
            return false;
        }
        entriesById.remove(removedRecord.id);
        return true;
    }

    /**
     * Check if the Set has a value.
     */
    public boolean setHas(JSValue value) {
        return data.containsKey(new JSMap.KeyWrapper(normalizeValue(value)));
    }

    /**
     * Get the number of values in the Set.
     */
    public int size() {
        return data.size();
    }

    @Override
    public String toString() {
        return "[object Set]";
    }

    /**
     * Get all values as an iterable.
     */
    public Iterable<JSMap.KeyWrapper> values() {
        List<JSMap.KeyWrapper> values = new ArrayList<>(data.size());
        for (EntryRecord entryRecord : data.values()) {
            values.add(entryRecord.keyWrapper);
        }
        return values;
    }

    private record EntryRecord(long id, JSMap.KeyWrapper keyWrapper) {
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
}
