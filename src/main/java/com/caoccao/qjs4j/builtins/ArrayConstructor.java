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
 * Implementation of Array constructor static methods. Based on ES2020 Array specification.
 */
public final class ArrayConstructor {

    /**
     * Array constructor call/new. Delegates to JSArray.create().
     * <p>
     * Based on ES2020 22.1.1.1
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSArray.create(context, args);
    }

    /**
     * Array.from(items, mapFn, thisArg) ES2024 23.1.2.1 Creates a new Array instance from an array-like or iterable
     * object.
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayFrom.from(context, thisArg, args);
    }

    /**
     * Array.fromAsync(asyncIterable, mapFn, thisArg) ES2022 23.1.2.2 Creates a new Array instance from an async
     * iterable, iterable, or array-like object. Returns a Promise that resolves to the created array.
     */
    public static JSValue fromAsync(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayFromAsync.from(context, thisArg, args);
    }

    /**
     * get Array[@@species] ES2015 22.1.2.4 Returns the Array constructor.
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    /**
     * Array.isArray(arg) ES2020 22.1.2.2 Determines whether the passed value is an Array.
     */
    public static JSValue isArray(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        int res = JSTypeChecking.isArray(context, args[0]);
        if (res < 0) {
            return context.getPendingException();
        }
        return JSBoolean.valueOf(res != 0);
    }

    /**
     * Array.of(...items) ES2020 22.1.2.3 Creates a new Array instance with a variable number of arguments.
     */
    public static JSValue of(JSContext context, JSValue thisArg, JSValue[] args) {
        return ArrayFrom.of(context, thisArg, args);
    }

}
