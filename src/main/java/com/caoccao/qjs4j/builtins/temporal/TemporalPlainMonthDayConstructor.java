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

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * Implementation of Temporal.PlainMonthDay constructor and static methods.
 */
public final class TemporalPlainMonthDayConstructor {

    private TemporalPlainMonthDayConstructor() {
    }

    /**
     * Temporal.PlainMonthDay(isoMonth, isoDay, calendar?, referenceISOYear?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainYearMonth.");
            return JSUndefined.INSTANCE;
        }

        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        String calendarId = "iso8601";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[2]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        int referenceYear = 1972;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            referenceYear = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        if (!IsoDate.isValidIsoDate(referenceYear, isoMonth, isoDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainMonthDay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainMonthDay(context, new IsoDate(referenceYear, isoMonth, isoDay), calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainMonthDay createPlainMonthDay(JSContext context, IsoDate isoDate, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainMonthDay");
        return createPlainMonthDay(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainMonthDay createPlainMonthDay(JSContext context, IsoDate isoDate, String calendarId, JSObject prototype) {
        JSTemporalPlainMonthDay md = new JSTemporalPlainMonthDay(context, isoDate, calendarId);
        if (prototype != null) {
            md.setPrototype(prototype);
        }
        return md;
    }

    /**
     * Temporal.PlainMonthDay.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalMonthDay(context, item, options);
    }

    static JSValue monthDayFromFields(JSContext context, JSObject fields, JSValue options) {
        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int month;
        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (monthValue instanceof JSUndefined || monthValue == null) {
            if (monthCodeValue instanceof JSString monthCodeStr) {
                month = TemporalPlainDateConstructor.parseMonthCode(context, monthCodeStr.value());
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
            } else {
                context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
        } else {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        int day = TemporalUtils.getIntegerField(context, fields, "day", Integer.MIN_VALUE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        if (day == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        String calendarId = "iso8601";
        if (calendarValue instanceof JSString calStr) {
            calendarId = calStr.value().toLowerCase(java.util.Locale.ROOT);
        }

        int referenceYear = 1972;
        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(referenceYear, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainMonthDay(context, new IsoDate(referenceYear, month, day), calendarId);
        } else {
            month = Math.max(1, Math.min(12, month));
            day = Math.max(1, Math.min(IsoDate.daysInMonth(referenceYear, month), day));
            return createPlainMonthDay(context, new IsoDate(referenceYear, month, day), calendarId);
        }
    }

    static JSValue monthDayFromString(JSContext context, String input) {
        IsoDate date = TemporalParser.parseMonthDayString(context, input);
        if (date == null) return JSUndefined.INSTANCE;
        String calendar = "iso8601";
        int calIdx = input.indexOf("[u-ca=");
        if (calIdx >= 0) {
            int endIdx = input.indexOf(']', calIdx);
            if (endIdx > calIdx) {
                calendar = input.substring(calIdx + 6, endIdx).toLowerCase(java.util.Locale.ROOT);
            }
        }
        return createPlainMonthDay(context, new IsoDate(date.year(), date.month(), date.day()), calendar);
    }

    public static JSValue toTemporalMonthDay(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainMonthDay md) {
            return createPlainMonthDay(context, md.getIsoDate(), md.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return monthDayFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            return monthDayFromString(context, itemStr.value());
        }
        context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainMonthDay toTemporalMonthDayObject(JSContext context, JSValue item) {
        JSValue result = toTemporalMonthDay(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) return null;
        return (JSTemporalPlainMonthDay) result;
    }
}
