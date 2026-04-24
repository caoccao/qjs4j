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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.core.temporal.IsoTime;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * JSObject subclass representing a Temporal.PlainTime value.
 * Internal slot: [[ISOTime]]
 */
public final class JSTemporalPlainTime extends JSObject {
    private final IsoTime isoTime;

    public JSTemporalPlainTime(JSContext context, IsoTime isoTime) {
        super(context);
        this.isoTime = isoTime;
    }

    public static JSTemporalPlainTime create(JSContext context, IsoTime isoTime) {
        JSObject prototype = TemporalUtils.getTemporalPrototype(context, "PlainTime");
        return create(context, isoTime, prototype);
    }

    public static JSTemporalPlainTime create(JSContext context, IsoTime isoTime, JSObject prototype) {
        JSTemporalPlainTime plainTime = new JSTemporalPlainTime(context, isoTime);
        if (prototype != null) {
            plainTime.setPrototype(prototype);
        }
        return plainTime;
    }

    public IsoTime getIsoTime() {
        return isoTime;
    }
}
