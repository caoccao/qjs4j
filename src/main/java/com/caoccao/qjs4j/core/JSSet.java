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

import java.util.LinkedHashSet;

/**
 * Represents a JavaScript Set object.
 * Sets maintain insertion order and use SameValueZero equality for values.
 */
public final class JSSet extends JSObject {
    public static final String NAME = "Set";
    // Use LinkedHashSet to maintain insertion order
    // Use KeyWrapper from JSMap for consistent SameValueZero equality
    private final LinkedHashSet<JSMap.KeyWrapper> data;

    /**
     * Create an empty Set.
     */
    public JSSet() {
        super();
        this.data = new LinkedHashSet<>();
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // Create Set object
        JSSet setObj = new JSSet();
        // If an iterable is provided, populate the set
        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];
            // Handle array directly for efficiency
            if (iterableArg instanceof JSArray arr) {
                for (long i = 0; i < arr.getLength(); i++) {
                    JSValue value = arr.get((int) i);
                    setObj.setAdd(value);
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
                    JSValue value = nextResult.get("value");
                    setObj.setAdd(value);
                }
            }
        }
        context.transferPrototype(setObj, NAME);
        return setObj;
    }

    /**
     * Add a value to the Set.
     */
    public void setAdd(JSValue value) {
        data.add(new JSMap.KeyWrapper(value));
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
        return data.remove(new JSMap.KeyWrapper(value));
    }

    /**
     * Check if the Set has a value.
     */
    public boolean setHas(JSValue value) {
        return data.contains(new JSMap.KeyWrapper(value));
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
        return data;
    }
}
