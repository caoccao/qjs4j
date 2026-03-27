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
 * Implementation of Temporal.PlainYearMonth constructor and static methods.
 */
public final class TemporalPlainYearMonthConstructor {

    private TemporalPlainYearMonthConstructor() {
    }

    /**
     * Temporal.PlainYearMonth.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainYearMonth one = toTemporalYearMonthObject(context, oneArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth two = toTemporalYearMonthObject(context, twoArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int cmp = Integer.compare(one.getIsoDate().year(), two.getIsoDate().year());
        if (cmp != 0) return JSNumber.of(cmp);
        return JSNumber.of(Integer.compare(one.getIsoDate().month(), two.getIsoDate().month()));
    }

    /**
     * Temporal.PlainYearMonth(isoYear, isoMonth, calendar?, referenceISODay?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainYearMonth.");
            return JSUndefined.INSTANCE;
        }

        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        String calendarId = "iso8601";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[2]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        int referenceDay = 1;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            referenceDay = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        if (!IsoDate.isValidIsoDate(isoYear, isoMonth, referenceDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainYearMonth");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainYearMonth(context, new IsoDate(isoYear, isoMonth, referenceDay), calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainYearMonth createPlainYearMonth(JSContext context, IsoDate isoDate, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainYearMonth");
        return createPlainYearMonth(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainYearMonth createPlainYearMonth(JSContext context, IsoDate isoDate, String calendarId, JSObject prototype) {
        JSTemporalPlainYearMonth ym = new JSTemporalPlainYearMonth(context, isoDate, calendarId);
        if (prototype != null) {
            ym.setPrototype(prototype);
        }
        return ym;
    }

    /**
     * Temporal.PlainYearMonth.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalYearMonth(context, item, options);
    }

    public static JSValue toTemporalYearMonth(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainYearMonth ym) {
            return createPlainYearMonth(context, ym.getIsoDate(), ym.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return yearMonthFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            return yearMonthFromString(context, itemStr.value());
        }
        context.throwTypeError("Temporal error: year argument must be an object.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainYearMonth toTemporalYearMonthObject(JSContext context, JSValue item) {
        JSValue result = toTemporalYearMonth(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) return null;
        return (JSTemporalPlainYearMonth) result;
    }

    static JSValue yearMonthFromFields(JSContext context, JSObject fields, JSValue options) {
        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int year = TemporalUtils.getIntegerField(context, fields, "year", Integer.MIN_VALUE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        if (year == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }

        int month;
        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (monthValue instanceof JSUndefined || monthValue == null) {
            if (monthCodeValue instanceof JSString monthCodeStr) {
                month = TemporalPlainDateConstructor.parseMonthCode(context, monthCodeStr.value());
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
            } else {
                context.throwTypeError("Temporal error: year argument must be an object.");
                return JSUndefined.INSTANCE;
            }
        } else {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        String calendarId = "iso8601";
        if (calendarValue instanceof JSString calStr) {
            calendarId = calStr.value().toLowerCase(java.util.Locale.ROOT);
        }

        if ("reject".equals(overflow)) {
            if (month < 1 || month > 12) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainYearMonth(context, new IsoDate(year, month, 1), calendarId);
        } else {
            month = Math.max(1, Math.min(12, month));
            return createPlainYearMonth(context, new IsoDate(year, month, 1), calendarId);
        }
    }

    static JSValue yearMonthFromString(JSContext context, String input) {
        IsoDate date = TemporalParser.parseYearMonthString(context, input);
        if (date == null) return JSUndefined.INSTANCE;
        String calendar = "iso8601";
        int calIdx = input.indexOf("[u-ca=");
        if (calIdx >= 0) {
            int endIdx = input.indexOf(']', calIdx);
            if (endIdx > calIdx) {
                calendar = input.substring(calIdx + 6, endIdx).toLowerCase(java.util.Locale.ROOT);
            }
        }
        return createPlainYearMonth(context, new IsoDate(date.year(), date.month(), 1), calendar);
    }
}
