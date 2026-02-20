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

/**
 * Implementation of Map constructor static methods.
 * Based on ES2024 Map specification.
 */
public final class MapConstructor {

    /**
     * Map constructor call handler.
     * Creates a new Map object.
     *
     * @param context The execution context
     * @param thisArg The this value (unused for constructor)
     * @param args    The arguments array (optional iterable)
     * @return New Map object
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSMap.create(context, args);
    }

    private static void closeIterator(JSContext context, JSValue iterator) {
        if (!(iterator instanceof JSObject iteratorObject)) {
            return;
        }
        JSValue returnMethod = iteratorObject.get(PropertyKey.RETURN);
        if (returnMethod instanceof JSFunction returnFunction) {
            returnFunction.call(context, iterator, new JSValue[0]);
        }
    }

    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    /**
     * Map.groupBy(items, callbackFn)
     * ES2024 24.1.2.2
     * Groups array elements by a key returned from the callback function,
     * returning a Map where keys are callback results and values are arrays.
     */
    public static JSValue groupBy(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2 || !(args[1] instanceof JSFunction callback)) {
            return context.throwTypeError("not a function");
        }

        JSValue items = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue iterator = JSIteratorHelper.getIterator(context, items);
        if (!(iterator instanceof JSObject)) {
            return context.throwTypeError("Object is not iterable");
        }

        JSMap groups = context.createJSMap();
        long index = 0;
        while (true) {
            if (index >= NumberPrototype.MAX_SAFE_INTEGER) {
                closeIterator(context, iterator);
                return context.throwTypeError("too many elements");
            }

            JSObject nextResult = JSIteratorHelper.iteratorNext(iterator, context);
            if (context.hasPendingException()) {
                closeIterator(context, iterator);
                return JSUndefined.INSTANCE;
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
            JSValue key = callback.call(context, JSUndefined.INSTANCE, new JSValue[]{value, JSNumber.of(index)});
            if (context.hasPendingException()) {
                closeIterator(context, iterator);
                return JSUndefined.INSTANCE;
            }

            JSValue existingGroup = groups.mapGet(key);
            JSArray group = existingGroup instanceof JSArray existingArray
                    ? existingArray
                    : context.createJSArray();
            if (!(existingGroup instanceof JSArray)) {
                groups.mapSet(key, group);
            }
            group.push(value);
            index++;
        }

        return groups;
    }
}
