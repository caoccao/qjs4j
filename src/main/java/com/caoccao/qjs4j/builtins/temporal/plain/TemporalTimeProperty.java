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

package com.caoccao.qjs4j.builtins.temporal.plain;

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;

import java.util.function.ToIntFunction;

/** Numeric time getters shared by the Temporal prototypes with a local ISO time. */
public enum TemporalTimeProperty {
    HOUR("hour", IsoTime::hour),
    MICROSECOND("microsecond", IsoTime::microsecond),
    MILLISECOND("millisecond", IsoTime::millisecond),
    MINUTE("minute", IsoTime::minute),
    NANOSECOND("nanosecond", IsoTime::nanosecond),
    SECOND("second", IsoTime::second);

    private final ToIntFunction<IsoTime> field;
    private final String key;

    TemporalTimeProperty(String key, ToIntFunction<IsoTime> field) {
        this.key = key;
        this.field = field;
    }

    public String propertyName() {
        return key;
    }

    public <T extends JSObject & JSTemporalTimeValue> JSValue read(JSContext context, JSValue thisArg,
            TemporalReceiver<T> receiver) {
        T value = receiver.require(context, thisArg, key);
        return value == null ? JSUndefined.INSTANCE : JSNumber.of(field.applyAsInt(value.getIsoTime()));
    }
}
