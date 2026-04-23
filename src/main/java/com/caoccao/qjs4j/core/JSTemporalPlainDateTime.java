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

import com.caoccao.qjs4j.core.temporal.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

public final class JSTemporalPlainDateTime extends JSObject {
    private final TemporalCalendarId calendarId;
    private final IsoDateTime isoDateTime;

    public JSTemporalPlainDateTime(JSContext context, IsoDateTime isoDateTime, TemporalCalendarId calendarId) {
        super(context);
        this.isoDateTime = isoDateTime;
        this.calendarId = calendarId;
    }

    public static JSTemporalPlainDateTime create(
            JSContext context,
            IsoDateTime isoDateTime,
            TemporalCalendarId calendarId) {
        JSObject prototype = TemporalUtils.getTemporalPrototype(context, "PlainDateTime");
        return create(context, isoDateTime, calendarId, prototype);
    }

    public static JSTemporalPlainDateTime create(
            JSContext context,
            IsoDateTime isoDateTime,
            TemporalCalendarId calendarId,
            JSObject prototype) {
        JSTemporalPlainDateTime plainDateTime = new JSTemporalPlainDateTime(context, isoDateTime, calendarId);
        if (prototype != null) {
            plainDateTime.setPrototype(prototype);
        }
        return plainDateTime;
    }

    public TemporalCalendarId getCalendarId() {
        return calendarId;
    }

    public IsoDateTime getIsoDateTime() {
        return isoDateTime;
    }
}
