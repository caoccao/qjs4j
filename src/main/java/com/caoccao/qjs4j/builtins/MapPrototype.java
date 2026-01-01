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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;

import java.util.Map;

/**
 * Implementation of Map.prototype methods.
 * Based on ES2020 Map specification.
 */
public final class MapPrototype {

    /**
     * Map.prototype.clear()
     * ES2020 23.1.3.1
     * Removes all elements from the Map.
     */
    public static JSValue clear(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.clear called on non-Map");
        }

        map.mapClear();
        return JSUndefined.INSTANCE;
    }

    /**
     * Map.prototype.delete(key)
     * ES2020 23.1.3.3
     * Removes the element with the specified key. Returns true if an element existed and was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.delete called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(map.mapDelete(key));
    }

    /**
     * Map.prototype.entries()
     * ES2020 23.1.3.4
     * Returns an iterator over [key, value] pairs.
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.entries called on non-Map");
        }

        return JSIterator.mapEntriesIterator(context, map);
    }

    /**
     * Map.prototype.forEach(callbackFn, thisArg)
     * ES2020 23.1.3.4
     * Executes a provided function once per each key/value pair in the Map, in insertion order.
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.forEach called on non-Map");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Map.prototype.forEach requires a function");
        }

        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // Iterate over entries in insertion order
        for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : map.entries()) {
            JSValue key = entry.getKey().value();
            JSValue value = entry.getValue();

            // Call callback with (value, key, map)
            JSValue[] callbackArgs = new JSValue[]{value, key, map};
            callback.call(context, callbackThisArg, callbackArgs);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Map.prototype.get(key)
     * ES2020 23.1.3.5
     * Returns the value associated with the key, or undefined if none exists.
     */
    public static JSValue get(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.get called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return map.mapGet(key);
    }

    /**
     * get Map.prototype.size
     * ES2020 23.1.3.10
     * Returns the number of entries in the Map.
     */
    public static JSValue getSize(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("get Map.prototype.size called on non-Map");
        }

        return new JSNumber(map.size());
    }

    /**
     * Map.prototype.has(key)
     * ES2020 23.1.3.6
     * Returns a boolean indicating whether an element with the specified key exists.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.has called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(map.mapHas(key));
    }

    /**
     * Map.prototype.keys()
     * ES2020 23.1.3.7
     * Returns an iterator over keys.
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.keys called on non-Map");
        }

        return JSIterator.mapKeysIterator(map);
    }

    /**
     * Map.prototype.set(key, value)
     * ES2020 23.1.3.9
     * Sets the value for the key in the Map object. Returns the Map object.
     */
    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.set called on non-Map");
        }

        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue value = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        map.mapSet(key, value);
        return map; // Return the Map object for chaining
    }

    /**
     * Map.prototype.values()
     * ES2020 23.1.3.11
     * Returns an iterator over values.
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.values called on non-Map");
        }

        return JSIterator.mapValuesIterator(map);
    }
}
