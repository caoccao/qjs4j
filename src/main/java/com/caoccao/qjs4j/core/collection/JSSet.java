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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;

import java.util.*;

/**
 * Represents a JavaScript Set object. Sets maintain insertion order and use SameValueZero equality for values.
 */
public final class JSSet extends JSObject {
    public static final String NAME = "Set";
    private final OrderedHashTable<JSMap.KeyWrapper> data;

    /**
     * Create an empty Set.
     */
    public JSSet(JSContext context) {
        super(context);
        this.data = new OrderedHashTable<>();
    }

    public IterationCursor createIterationCursor() {
        return new IterationCursor(data.cursor());
    }

    public JSValue nextIterationValue(IterationCursor cursor) {
        JSMap.KeyWrapper key = cursor.cursor.next();
        return key == null ? null : key.value();
    }

    /**
     * Add a value to the Set.
     */
    public void setAdd(JSValue value) {
        JSMap.KeyWrapper key = JSMap.KeyWrapper.forCollection(value);
        data.putIfAbsent(key, key);
    }

    /**
     * Clear all values from the Set.
     */
    public void setClear() {
        data.clear();
    }

    /**
     * Delete a value from the Set.
     */
    public boolean setDelete(JSValue value) {
        return data.remove(value);
    }

    /**
     * Check if the Set has a value.
     */
    public boolean setHas(JSValue value) {
        return data.containsKey(value);
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
        return data.keys();
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSSet setObj = context.createJSSet();
        CollectionInitializer.initializePrototypeFromNewTarget(context, setObj, NAME);
        if (context.hasPendingException()) {
            return CollectionInitializer.returnAbruptResult(context, setObj);
        }
        return CollectionInitializer.initializeFromIterable(context, setObj, args, false);
    }

    public static final class IterationCursor {
        private final OrderedHashTable.Cursor<JSMap.KeyWrapper> cursor;

        private IterationCursor(OrderedHashTable.Cursor<JSMap.KeyWrapper> cursor) {
            this.cursor = cursor;
        }
    }
}
