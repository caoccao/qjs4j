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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Implementation of JavaScript Boolean.prototype methods. Based on ES2020 Boolean specification.
 */
public final class BooleanPrototype {
    /**
     * Boolean.prototype.toString() ES2020 19.3.3.2
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asBooleanWithDownCast().map(jsBoolean -> (JSValue) new JSString(jsBoolean.toString())).orElseGet(
                () -> context.throwTypeError("Boolean.prototype.toString requires that 'this' be a Boolean"));
    }

    /**
     * Boolean.prototype.valueOf() ES2020 19.3.3.3
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asBooleanWithDownCast().map(jsBoolean -> (JSValue) jsBoolean)
                .orElseGet(() -> context.throwTypeError("Boolean.prototype.valueOf requires that 'this' be a Boolean"));
    }
}
