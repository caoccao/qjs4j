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
 * Implementation of JavaScript Boolean.prototype methods.
 * Based on ES2020 Boolean specification.
 */
public final class BooleanPrototype {

    /**
     * Boolean.prototype.toString()
     * ES2020 19.3.3.2
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        boolean value;

        if (thisArg instanceof JSBoolean bool) {
            value = bool.value();
        } else if (thisArg instanceof JSObject obj) {
            // Check for [[BooleanData]] internal slot
            JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
            if (primitiveValue instanceof JSBoolean bool) {
                value = bool.value();
            } else {
                return context.throwTypeError("Boolean.prototype.toString called on non-boolean");
            }
        } else {
            return context.throwTypeError("Boolean.prototype.toString called on non-boolean");
        }

        return new JSString(value ? "true" : "false");
    }

    /**
     * Boolean.prototype.valueOf()
     * ES2020 19.3.3.3
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSBoolean bool) {
            return bool;
        }

        if (thisArg instanceof JSObject obj) {
            // Check for [[BooleanData]] internal slot
            JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
            if (primitiveValue instanceof JSBoolean bool) {
                return bool;
            }
        }

        return context.throwTypeError("Boolean.prototype.valueOf called on non-boolean");
    }
}
