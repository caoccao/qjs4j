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
 * Implementation of WeakMap.prototype methods.
 * Based on ES2020 WeakMap specification.
 */
public final class WeakMapPrototype {

    /**
     * WeakMap.prototype.delete(key)
     * ES2020 23.3.3.2
     * Removes the element with the specified key. Returns true if an element existed and was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap jsWeakMap)) {
            return context.throwTypeError("Method WeakMap.prototype.delete called on incompatible receiver not weakmap");
        }
        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakMap.isWeakMapKey(key)) {
            return JSBoolean.FALSE;
        }
        return JSBoolean.valueOf(jsWeakMap.weakMapDelete(key));
    }

    /**
     * WeakMap.prototype.get(key)
     * ES2020 23.3.3.3
     * Returns the value associated with the key, or undefined if none exists.
     */
    public static JSValue get(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap jsWeakMap)) {
            return context.throwTypeError("Method WeakMap.prototype.get called on incompatible receiver not weakmap");
        }
        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakMap.isWeakMapKey(key)) {
            return JSUndefined.INSTANCE;
        }
        return jsWeakMap.weakMapGet(key);
    }

    /**
     * WeakMap.prototype.getOrInsert(key, defaultValue)
     * QuickJS extension.
     */
    public static JSValue getOrInsert(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap jsWeakMap)) {
            return context.throwTypeError("WeakMap.prototype.getOrInsert called on non-WeakMap");
        }
        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakMap.isWeakMapKey(key)) {
            return context.throwTypeError("Invalid value used as weak map key");
        }
        if (jsWeakMap.weakMapHas(key)) {
            return jsWeakMap.weakMapGet(key);
        }
        JSValue value = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        jsWeakMap.weakMapSet(key, value);
        return value;
    }

    /**
     * WeakMap.prototype.getOrInsertComputed(key, callback)
     * QuickJS extension.
     */
    public static JSValue getOrInsertComputed(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap jsWeakMap)) {
            return context.throwTypeError("WeakMap.prototype.getOrInsertComputed called on non-WeakMap");
        }
        if (args.length < 2 || !(args[1] instanceof JSFunction callback)) {
            return context.throwTypeError("not a function");
        }
        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakMap.isWeakMapKey(key)) {
            return context.throwTypeError("Invalid value used as weak map key");
        }
        if (jsWeakMap.weakMapHas(key)) {
            return jsWeakMap.weakMapGet(key);
        }

        JSValue value = callback.call(context, JSUndefined.INSTANCE, new JSValue[]{key});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        jsWeakMap.weakMapDelete(key);
        jsWeakMap.weakMapSet(key, value);
        return value;
    }

    /**
     * WeakMap.prototype.has(key)
     * ES2020 23.3.3.4
     * Returns a boolean indicating whether an element with the specified key exists.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap jsWeakMap)) {
            return context.throwTypeError("Method WeakMap.prototype.has called on incompatible receiver not weakmap");
        }
        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakMap.isWeakMapKey(key)) {
            return JSBoolean.FALSE;
        }
        return JSBoolean.valueOf(jsWeakMap.weakMapHas(key));
    }

    /**
     * WeakMap.prototype.set(key, value)
     * ES2020 23.3.3.5
     * Sets the value for the key in the WeakMap object. Returns the WeakMap object.
     * Key must be an object.
     */
    public static JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakMap jsWeakMap)) {
            return context.throwTypeError("Method WeakMap.prototype.set called on incompatible receiver not weakmap");
        }
        JSValue key = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakMap.isWeakMapKey(key)) {
            return context.throwTypeError("Invalid value used as weak map key");
        }
        JSValue value = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        jsWeakMap.weakMapSet(key, value);
        return jsWeakMap;
    }
}
