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

import com.caoccao.qjs4j.core.temporal.IsoDate;

/**
 * JSObject subclass representing a Temporal.PlainDate value.
 * Internal slots: [[ISODate]], [[Calendar]]
 */
public final class JSTemporalPlainDate extends JSObject {
    private final String calendarId;
    private final IsoDate isoDate;

    public JSTemporalPlainDate(JSContext context, IsoDate isoDate, String calendarId) {
        super(context);
        this.isoDate = isoDate;
        this.calendarId = calendarId;
    }

    public String getCalendarId() {
        return calendarId;
    }

    public IsoDate getIsoDate() {
        return isoDate;
    }
}
