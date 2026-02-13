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
 * Implementation of WeakSet.prototype methods.
 * Based on ES2020 WeakSet specification.
 */
public final class WeakSetPrototype {

    /**
     * WeakSet.prototype.add(value)
     * ES2020 23.4.3.1
     * Adds the value to the WeakSet object. Returns the WeakSet object.
     * Value must be an object.
     */
    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakSet jsWeakSet)) {
            return context.throwTypeError("Method WeakSet.prototype.add called on incompatible receiver not weakset");
        }
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakSet.isWeakSetValue(value)) {
            return context.throwTypeError("Invalid value used in weak set");
        }
        jsWeakSet.weakSetAdd(value);
        return jsWeakSet;
    }

    /**
     * WeakSet.prototype.delete(value)
     * ES2020 23.4.3.2
     * Removes the value from the WeakSet. Returns true if the value existed and was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakSet jsWeakSet)) {
            return context.throwTypeError("Method WeakSet.prototype.delete called on incompatible receiver not weakset");
        }
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakSet.isWeakSetValue(value)) {
            return JSBoolean.FALSE;
        }
        return JSBoolean.valueOf(jsWeakSet.weakSetDelete(value));
    }

    /**
     * WeakSet.prototype.has(value)
     * ES2020 23.4.3.3
     * Returns a boolean indicating whether a value exists in the WeakSet.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakSet jsWeakSet)) {
            return context.throwTypeError("Method WeakSet.prototype.has called on incompatible receiver not weakset");
        }
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!JSWeakSet.isWeakSetValue(value)) {
            return JSBoolean.FALSE;
        }
        return JSBoolean.valueOf(jsWeakSet.weakSetHas(value));
    }
}
