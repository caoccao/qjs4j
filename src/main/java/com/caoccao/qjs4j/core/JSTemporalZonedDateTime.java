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

import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;

import java.math.BigInteger;

public final class JSTemporalZonedDateTime extends JSObject implements JSTemporalDateValue, JSTemporalTimeValue {
    private final TemporalCalendarId calendarId;
    private final BigInteger epochNanoseconds;
    private final String timeZoneId;

    public JSTemporalZonedDateTime(JSContext context, BigInteger epochNanoseconds, String timeZoneId,
            TemporalCalendarId calendarId) {
        super(context);
        this.epochNanoseconds = epochNanoseconds;
        this.timeZoneId = timeZoneId;
        this.calendarId = calendarId;
    }

    @Override
    public TemporalCalendarId getCalendarId() {
        return calendarId;
    }

    public BigInteger getEpochNanoseconds() {
        return epochNanoseconds;
    }

    @Override
    public IsoDate getIsoDate() {
        return toLocalDateTime().date();
    }

    @Override
    public IsoTime getIsoTime() {
        return toLocalDateTime().time();
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public IsoDateTime toLocalDateTime() {
        return IsoDateTime.createFromEpochNsAndTimeZoneId(epochNanoseconds, timeZoneId);
    }

    public static JSTemporalZonedDateTime create(JSContext context, BigInteger epochNanoseconds, String timeZoneId,
            TemporalCalendarId calendarId) {
        JSObject prototype = TemporalUtils.getTemporalPrototype(context, "ZonedDateTime");
        return create(context, epochNanoseconds, timeZoneId, calendarId, prototype);
    }

    public static JSTemporalZonedDateTime create(JSContext context, BigInteger epochNanoseconds, String timeZoneId,
            TemporalCalendarId calendarId, JSObject prototype) {
        JSTemporalZonedDateTime zonedDateTime = new JSTemporalZonedDateTime(context, epochNanoseconds, timeZoneId,
                calendarId);
        if (prototype != null) {
            zonedDateTime.setPrototype(prototype);
        }
        return zonedDateTime;
    }
}
