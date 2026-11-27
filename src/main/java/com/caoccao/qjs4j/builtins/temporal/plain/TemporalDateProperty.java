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
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalEraYear;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;

/** Calendar-derived properties shared by Temporal date types. */
public enum TemporalDateProperty {
    DAY("day"),
    DAY_OF_WEEK("dayOfWeek"),
    DAY_OF_YEAR("dayOfYear"),
    DAYS_IN_MONTH("daysInMonth"),
    DAYS_IN_YEAR("daysInYear"),
    ERA("era"),
    ERA_YEAR("eraYear"),
    IN_LEAP_YEAR("inLeapYear"),
    MONTH("month"),
    MONTH_CODE("monthCode"),
    MONTHS_IN_YEAR("monthsInYear"),
    WEEK_OF_YEAR("weekOfYear"),
    YEAR("year"),
    YEAR_OF_WEEK("yearOfWeek");
    final String key;
    TemporalDateProperty(String key) {
        this.key = key;
    }

    public String propertyName() {
        return key;
    }

    private JSValue read(IsoDate date, TemporalCalendarId calendar) {
        return switch (this) {
            case DAY -> JSNumber.of(calendarDate(date, calendar).day());
            case MONTH -> JSNumber.of(calendarDate(date, calendar).month());
            case YEAR -> JSNumber.of(calendarDate(date, calendar).year());
            case DAY_OF_WEEK -> JSNumber.of(date.dayOfWeek());
            case DAY_OF_YEAR -> JSNumber.of(date.dayOfYear(calendar));
            case DAYS_IN_MONTH -> JSNumber.of(date.daysInMonth(calendar));
            case DAYS_IN_YEAR -> JSNumber.of(date.daysInYear(calendar));
            case IN_LEAP_YEAR -> JSBoolean.valueOf(calendar.isCalendarLeapYear(calendarDate(date, calendar).year()));
            case MONTHS_IN_YEAR -> JSNumber.of(TemporalUtils.monthsInYear(date, calendar));
            case MONTH_CODE -> new JSString(date.toIsoCalendarDate(calendar).monthCode());
            case WEEK_OF_YEAR -> JSNumber.of(date.weekOfYear());
            case YEAR_OF_WEEK -> JSNumber.of(date.yearOfWeek());
            case ERA -> era(date.toTemporalEraYear(calendar));
            case ERA_YEAR -> eraYear(date.toTemporalEraYear(calendar));
        };
    }

    public <T extends JSObject & JSTemporalDateValue> JSValue read(JSContext context, JSValue receiver,
            TemporalReceiver<T> type) {
        T value = type.require(context, receiver, key);
        if (value == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendar = value.getCalendarId();
        if ((this == WEEK_OF_YEAR || this == YEAR_OF_WEEK) && calendar != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        return read(value.getIsoDate(), calendar);
    }

    private static IsoDate calendarDate(IsoDate date, TemporalCalendarId calendar) {
        return calendar == TemporalCalendarId.ISO8601 ? date : date.toIsoCalendarDate(calendar).toIsoDate();
    }

    /** Calendar identity and the constant week length do not require conversion to a local ISO date. */
    public static <T extends JSObject & JSTemporalDateValue> JSValue calendarId(JSContext context, JSValue thisArg,
            TemporalReceiver<T> receiver) {
        return receiver.read(context, thisArg, "calendarId", value -> new JSString(value.getCalendarId().identifier()));
    }

    public static <T extends JSObject & JSTemporalDateValue> JSValue daysInWeek(JSContext context, JSValue thisArg,
            TemporalReceiver<T> receiver) {
        return receiver.read(context, thisArg, "daysInWeek", value -> JSNumber.of(7));
    }

    private static JSValue era(TemporalEraYear fields) {
        return fields == null ? JSUndefined.INSTANCE : new JSString(fields.era().identifier());
    }

    private static JSValue eraYear(TemporalEraYear fields) {
        return fields == null || fields.eraYear() == null ? JSUndefined.INSTANCE : JSNumber.of(fields.eraYear());
    }
}
