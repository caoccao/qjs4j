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

import com.caoccao.qjs4j.exceptions.JSException;

import java.util.WeakHashMap;

/**
 * Represents a JavaScript WeakMap object.
 * Keys must be objects and are weakly referenced.
 * WeakMaps are not enumerable.
 */
public final class JSWeakMap extends JSObject {
    public static final String NAME = "WeakMap";
    // Use WeakHashMap for automatic garbage collection of keys
    // Keys are compared by identity (reference equality)
    private final WeakHashMap<JSObject, JSValue> data;

    /**
     * Create an empty WeakMap.
     */
    public JSWeakMap() {
        super();
        this.data = new WeakHashMap<>();
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // Create WeakMap object
        JSWeakMap weakMapObj = new JSWeakMap();
        // If an iterable is provided, populate the weakmap
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
                    // WeakMap requires object keys
                    if (!(key instanceof JSObject)) {
                        return context.throwTypeError("WeakMap key must be an object");
                    }
                    weakMapObj.weakMapSet((JSObject) key, value);
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
                        JSValue entry = nextResult.get("value");
                        if (!(entry instanceof JSObject entryObj)) {
                            return context.throwTypeError("Iterator value must be an object");
                        }
                        // Get key and value from entry [key, value]
                        JSValue key = entryObj.get(0);
                        JSValue value = entryObj.get(1);
                        // WeakMap requires object keys
                        if (!(key instanceof JSObject)) {
                            return context.throwTypeError("WeakMap key must be an object");
                        }
                        weakMapObj.weakMapSet((JSObject) key, value);
                    }
                }
            }
        }
        context.getGlobalObject().get(NAME).asObject().ifPresent(weakMapObj::transferPrototypeFrom);
        return weakMapObj;
    }

    @Override
    public String toString() {
        return "[object WeakMap]";
    }

    /**
     * Delete a key from the WeakMap.
     */
    public boolean weakMapDelete(JSObject key) {
        return data.remove(key) != null;
    }

    /**
     * Get a value from the WeakMap by key.
     */
    public JSValue weakMapGet(JSObject key) {
        JSValue value = data.get(key);
        return value != null ? value : JSUndefined.INSTANCE;
    }

    /**
     * Check if the WeakMap has a key.
     */
    public boolean weakMapHas(JSObject key) {
        return data.containsKey(key);
    }

    /**
     * Set a key-value pair in the WeakMap.
     * Key must be an object.
     */
    public void weakMapSet(JSObject key, JSValue value) {
        data.put(key, value);
    }
}
