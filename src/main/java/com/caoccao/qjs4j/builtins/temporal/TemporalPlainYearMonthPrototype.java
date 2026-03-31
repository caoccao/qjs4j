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
import com.caoccao.qjs4j.core.temporal.TemporalDurationRecord;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.util.Locale;

/**
 * Implementation of Temporal.PlainYearMonth prototype methods.
 */
public final class TemporalPlainYearMonthPrototype {
    private static final String TYPE_NAME = "Temporal.PlainYearMonth";

    private TemporalPlainYearMonthPrototype() {
    }

    // ========== Getters ==========

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "add");
        if (ym == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, ym, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainYearMonth ym, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        long years = 0, months = 0;
        JSValue durationArg = args[0];
        if (durationArg instanceof JSTemporalDuration dur) {
            years = dur.getRecord().years();
            months = dur.getRecord().months();
        } else if (durationArg instanceof JSString durationStr) {
            com.caoccao.qjs4j.core.temporal.TemporalParser.DurationFields df =
                    com.caoccao.qjs4j.core.temporal.TemporalParser.parseDurationString(context, durationStr.value());
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            years = df.years();
            months = df.months();
        } else if (durationArg instanceof JSObject durationObj) {
            years = TemporalUtils.getIntegerField(context, durationObj, "years", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            months = TemporalUtils.getIntegerField(context, durationObj, "months", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        } else {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        years *= sign;
        months *= sign;

        IsoDate d = ym.getIsoDate();
        int newYear = d.year() + (int) years;
        int newMonth = d.month() + (int) months;
        // Normalize month
        if (newMonth > 12) {
            newYear += (newMonth - 1) / 12;
            newMonth = ((newMonth - 1) % 12) + 1;
        } else if (newMonth < 1) {
            newYear += (newMonth - 12) / 12;
            newMonth = 12 + (newMonth % 12);
            if (newMonth == 12 && months * sign < 0) {
                // already normalized
            }
        }

        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context,
                new IsoDate(newYear, newMonth, 1), ym.getCalendarId());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "calendarId");
        if (ym == null) return JSUndefined.INSTANCE;
        return new JSString(ym.getCalendarId());
    }

    private static JSTemporalPlainYearMonth checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainYearMonth ym)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver " + JSTypeConversions.toString(context, thisArg).value());
            return null;
        }
        return ym;
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "daysInMonth");
        if (ym == null) return JSUndefined.INSTANCE;
        IsoDate d = ym.getIsoDate();
        return JSNumber.of(IsoDate.daysInMonth(d.year(), d.month()));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "daysInYear");
        if (ym == null) return JSUndefined.INSTANCE;
        return JSNumber.of(IsoDate.daysInYear(ym.getIsoDate().year()));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "equals");
        if (ym == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth other = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        boolean equal = ym.getIsoDate().year() == other.getIsoDate().year()
                && ym.getIsoDate().month() == other.getIsoDate().month()
                && ym.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "era");
        if (ym == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "eraYear");
        if (ym == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "inLeapYear");
        if (ym == null) return JSUndefined.INSTANCE;
        return IsoDate.isLeapYear(ym.getIsoDate().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    // ========== Methods ==========

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "month");
        if (ym == null) return JSUndefined.INSTANCE;
        return JSNumber.of(ym.getIsoDate().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "monthCode");
        if (ym == null) return JSUndefined.INSTANCE;
        return new JSString(TemporalUtils.monthCode(ym.getIsoDate().month()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "monthsInYear");
        if (ym == null) return JSUndefined.INSTANCE;
        return JSNumber.of(12);
    }

    public static JSValue referenceISODay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "referenceISODay");
        if (ym == null) return JSUndefined.INSTANCE;
        return JSNumber.of(ym.getIsoDate().day());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "since");
        if (ym == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth other = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int monthsDiff = (ym.getIsoDate().year() - other.getIsoDate().year()) * 12
                + (ym.getIsoDate().month() - other.getIsoDate().month());
        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, monthsDiff, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "subtract");
        if (ym == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, ym, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "toJSON");
        if (ym == null) return JSUndefined.INSTANCE;
        IsoDate d = ym.getIsoDate();
        return new JSString(String.format(Locale.ROOT, "%04d-%02d", d.year(), d.month()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "toLocaleString");
        if (plainYearMonth == null) {
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
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainYearMonth});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "toPlainDate");
        if (ym == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        int day = TemporalUtils.getIntegerField(context, fields, "day", Integer.MIN_VALUE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        if (day == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        IsoDate d = ym.getIsoDate();
        if (!IsoDate.isValidIsoDate(d.year(), d.month(), day)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateConstructor.createPlainDate(context,
                new IsoDate(d.year(), d.month(), day), ym.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "toString");
        if (ym == null) return JSUndefined.INSTANCE;
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate d = ym.getIsoDate();
        boolean includeReferenceDay = !"never".equals(calendarNameOption)
                && (!"auto".equals(calendarNameOption) || !"iso8601".equals(ym.getCalendarId()));
        String result = TemporalUtils.formatIsoDate(d.year(), d.month(), includeReferenceDay ? d.day() : 1);
        if (!includeReferenceDay) {
            result = result.substring(0, result.lastIndexOf('-'));
        }
        result = TemporalUtils.maybeAppendCalendar(result, ym.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "until");
        if (ym == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth other = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int monthsDiff = (other.getIsoDate().year() - ym.getIsoDate().year()) * 12
                + (other.getIsoDate().month() - ym.getIsoDate().month());
        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, monthsDiff, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainYearMonth.prototype.valueOf; use Temporal.PlainYearMonth.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    // ========== Internal helpers ==========

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "with");
        if (ym == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoDate original = ym.getIsoDate();
        int year = TemporalUtils.getIntegerField(context, fields, "year", original.year());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int month = TemporalUtils.getIntegerField(context, fields, "month", original.month());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        month = Math.max(1, Math.min(12, month));
        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context,
                new IsoDate(year, month, 1), ym.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth ym = checkReceiver(context, thisArg, "year");
        if (ym == null) return JSUndefined.INSTANCE;
        return JSNumber.of(ym.getIsoDate().year());
    }
}
