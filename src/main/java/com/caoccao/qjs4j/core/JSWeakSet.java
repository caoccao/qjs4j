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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Represents a JavaScript WeakSet object.
 * Values must be objects and are weakly referenced.
 * WeakSets are not enumerable.
 */
public final class JSWeakSet extends JSObject {
    public static final String NAME = "WeakSet";
    // Use WeakHashMap with dummy values to implement WeakSet
    // Keys are compared by identity (reference equality)
    private final Set<JSObject> data;

    /**
     * Create an empty WeakSet.
     */
    public JSWeakSet() {
        super();
        // WeakHashMap's keySet backed by weak references
        this.data = Collections.newSetFromMap(new WeakHashMap<>());
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // Create WeakSet object
        JSWeakSet weakSetObj = new JSWeakSet();
        // If an iterable is provided, populate the weakset
        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];
            // Handle array directly for efficiency
            if (iterableArg instanceof JSArray arr) {
                for (long i = 0; i < arr.getLength(); i++) {
                    JSValue value = arr.get((int) i);
                    // WeakSet requires object values
                    if (!(value instanceof JSObject)) {
                        return context.throwTypeError("WeakSet value must be an object");
                    }
                    weakSetObj.weakSetAdd((JSObject) value);
                }
            } else if (iterableArg instanceof JSObject) {
                // Try to get iterator
                JSValue iterator = JSIteratorHelper.getIterator(iterableArg, context);
                if (iterator instanceof JSIterator iter) {
                    // Iterate and populate
                    while (true) {
                        JSObject nextResult = iter.next();
                        if (nextResult == null) {
                            break;
                        }
                        JSValue done = nextResult.get("done");
                        if (done == JSBoolean.TRUE) {
                            break;
                        }
                        JSValue value = nextResult.get("value");
                        // WeakSet requires object values
                        if (!(value instanceof JSObject)) {
                            return context.throwTypeError("WeakSet value must be an object");
                        }
                        weakSetObj.weakSetAdd((JSObject) value);
                    }
                }
            }
        }
        context.getGlobalObject().get(NAME).asObject().ifPresent(weakSetObj::transferPrototypeFrom);
        return weakSetObj;
    }

    @Override
    public String toString() {
        return "[object WeakSet]";
    }

    /**
     * Add a value to the WeakSet.
     * Value must be an object.
     */
    public void weakSetAdd(JSObject value) {
        data.add(value);
    }

    /**
     * Delete a value from the WeakSet.
     */
    public boolean weakSetDelete(JSObject value) {
        return data.remove(value);
    }

    /**
     * Check if the WeakSet has a value.
     */
    public boolean weakSetHas(JSObject value) {
        return data.contains(value);
    }
}
