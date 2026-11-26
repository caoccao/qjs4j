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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.util.function.Function;

/** Receiver validation shared by the methods and getters of one Temporal prototype. */
record TemporalReceiver<T extends JSObject>(Class<T> type, String typeName) {
    T require(JSContext context, JSValue receiver, String method) {
        return TemporalUtils.checkReceiver(context, receiver, type, typeName, method);
    }

    JSValue read(JSContext context, JSValue receiver, String property, Function<T, JSValue> getter) {
        T value = require(context, receiver, property);
        return value == null ? JSUndefined.INSTANCE : getter.apply(value);
    }

    JSValue rejectValueOf(JSContext context) {
        context.throwTypeError("Do not use " + typeName + ".prototype.valueOf; use " + typeName
                + ".prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }
}
