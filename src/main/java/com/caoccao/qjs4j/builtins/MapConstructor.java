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
     * Map.groupBy(items, callbackFn)
     * ES2024 24.1.2.2
     * Groups array elements by a key returned from the callback function,
     * returning a Map where keys are callback results and values are arrays.
     */
    public static JSValue groupBy(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("Map.groupBy requires 2 arguments");
        }

        JSValue items = args[0];
        if (!(items instanceof JSArray arr)) {
            return context.throwTypeError("First argument must be an array");
        }

        if (!(args[1] instanceof JSFunction callback)) {
            return context.throwTypeError("Second argument must be a function");
        }

        JSMap result = new JSMap();

        long length = arr.getLength();
        for (long i = 0; i < length; i++) {
            JSValue element = arr.get(i);
            JSValue[] callbackArgs = {element, new JSNumber(i)};
            JSValue keyValue = callback.call(context, JSUndefined.INSTANCE, callbackArgs);

            // Get or create array for this key
            JSValue existingGroup = result.mapGet(keyValue);
            JSArray group;
            if (existingGroup instanceof JSArray) {
                group = (JSArray) existingGroup;
            } else {
                group = context.createJSArray();
                result.mapSet(keyValue, group);
            }

            // Add element to group
            group.push(element);
        }

        return result;
    }
}
