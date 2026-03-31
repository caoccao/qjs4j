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
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.util.Locale;

/**
 * Implementation of Temporal.PlainMonthDay prototype methods.
 */
public final class TemporalPlainMonthDayPrototype {
    private static final String TYPE_NAME = "Temporal.PlainMonthDay";

    private TemporalPlainMonthDayPrototype() {
    }

    // ========== Getters ==========

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "calendarId");
        if (md == null) return JSUndefined.INSTANCE;
        return new JSString(md.getCalendarId());
    }

    private static JSTemporalPlainMonthDay checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainMonthDay md)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return md;
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "day");
        if (md == null) return JSUndefined.INSTANCE;
        return JSNumber.of(md.getIsoDate().day());
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "equals");
        if (md == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainMonthDay other = TemporalPlainMonthDayConstructor.toTemporalMonthDayObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        boolean equal = md.getIsoDate().month() == other.getIsoDate().month()
                && md.getIsoDate().day() == other.getIsoDate().day()
                && md.getIsoDate().year() == other.getIsoDate().year()
                && md.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    // ========== Methods ==========

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "monthCode");
        if (md == null) return JSUndefined.INSTANCE;
        return new JSString(TemporalUtils.monthCode(md.getIsoDate().month()));
    }

    public static JSValue referenceISOYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "referenceISOYear");
        if (md == null) return JSUndefined.INSTANCE;
        return JSNumber.of(md.getIsoDate().year());
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "toJSON");
        if (md == null) return JSUndefined.INSTANCE;
        IsoDate d = md.getIsoDate();
        return new JSString(String.format(Locale.ROOT, "%02d-%02d", d.month(), d.day()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "toLocaleString");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainMonthDay});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "toPlainDate");
        if (md == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        int year = TemporalUtils.getIntegerField(context, fields, "year", Integer.MIN_VALUE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (year == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        IsoDate d = md.getIsoDate();
        int month = d.month();
        int day = d.day();
        if (!IsoDate.isValidIsoDate(year, month, day)) {
            day = Math.min(day, IsoDate.daysInMonth(year, month));
            if (!IsoDate.isValidIsoDate(year, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }
        return TemporalPlainDateConstructor.createPlainDate(context,
                new IsoDate(year, month, day), md.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "toString");
        if (md == null) return JSUndefined.INSTANCE;
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate d = md.getIsoDate();
        boolean includeReferenceYear = !"never".equals(calendarNameOption)
                && (!"auto".equals(calendarNameOption) || !"iso8601".equals(md.getCalendarId()));
        String result;
        if (includeReferenceYear) {
            result = TemporalUtils.formatIsoDate(d.year(), d.month(), d.day());
        } else {
            result = String.format(Locale.ROOT, "%02d-%02d", d.month(), d.day());
        }
        result = TemporalUtils.maybeAppendCalendar(result, md.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainMonthDay.prototype.valueOf; use Temporal.PlainMonthDay.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    // ========== Internal helpers ==========

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay md = checkReceiver(context, thisArg, "with");
        if (md == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoDate original = md.getIsoDate();
        int month = original.month();
        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (!(monthValue instanceof JSUndefined || monthValue == null)) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        } else if (monthCodeValue instanceof JSString monthCodeStr) {
            month = TemporalPlainDateConstructor.parseMonthCode(context, monthCodeStr.value());
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        int day = TemporalUtils.getIntegerField(context, fields, "day", original.day());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        month = Math.max(1, Math.min(12, month));
        day = Math.max(1, Math.min(IsoDate.daysInMonth(1972, month), day));
        return TemporalPlainMonthDayConstructor.createPlainMonthDay(context,
                new IsoDate(1972, month, day), md.getCalendarId());
    }
}
