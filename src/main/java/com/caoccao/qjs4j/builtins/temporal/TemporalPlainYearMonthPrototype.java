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

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "add");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, plainYearMonth, args, 1);
    }

    private static IsoDate addDateDurationToPlainYearMonth(
            JSContext context,
            IsoDate baseDate,
            long yearsToAdd,
            long monthsToAdd,
            String overflow) {
        long monthIndex = (long) baseDate.month() - 1L + monthsToAdd;
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int normalizedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long normalizedYearAsLong = (long) baseDate.year() + yearsToAdd + yearDelta;
        if (normalizedYearAsLong < Integer.MIN_VALUE || normalizedYearAsLong > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int normalizedYear = (int) normalizedYearAsLong;

        if (!TemporalPlainYearMonthConstructor.isValidIsoYearMonth(normalizedYear, normalizedMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int normalizedDay = baseDate.day();
        int daysInNormalizedMonth = IsoDate.daysInMonth(normalizedYear, normalizedMonth);
        if (normalizedDay > daysInNormalizedMonth) {
            if ("reject".equals(overflow)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            normalizedDay = daysInNormalizedMonth;
        }

        if (!IsoDate.isValidIsoDate(normalizedYear, normalizedMonth, normalizedDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        return new IsoDate(normalizedYear, normalizedMonth, normalizedDay);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainYearMonth plainYearMonth, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException() || temporalDuration == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord durationRecord = temporalDuration.getRecord();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException() || overflow == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate originalDate = plainYearMonth.getIsoDate();
        if (!IsoDate.isValidIsoDate(originalDate.year(), originalDate.month(), originalDate.day())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (durationRecord.weeks() != 0
                || durationRecord.days() != 0
                || durationRecord.hours() != 0
                || durationRecord.minutes() != 0
                || durationRecord.seconds() != 0
                || durationRecord.milliseconds() != 0
                || durationRecord.microseconds() != 0
                || durationRecord.nanoseconds() != 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoDate resultDate = addDateDurationToPlainYearMonth(
                context,
                originalDate,
                durationRecord.years(),
                durationRecord.months(),
                overflow);
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context, resultDate, plainYearMonth.getCalendarId());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "calendarId");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return new JSString(plainYearMonth.getCalendarId());
    }

    private static JSTemporalPlainYearMonth checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainYearMonth plainYearMonth)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainYearMonth;
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "daysInMonth");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        IsoDate isoDate = plainYearMonth.getIsoDate();
        return JSNumber.of(IsoDate.daysInMonth(isoDate.year(), isoDate.month()));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "daysInYear");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return JSNumber.of(IsoDate.daysInYear(plainYearMonth.getIsoDate().year()));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "equals");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth other = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        boolean equal = plainYearMonth.getIsoDate().year() == other.getIsoDate().year()
                && plainYearMonth.getIsoDate().month() == other.getIsoDate().month()
                && plainYearMonth.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "era");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "eraYear");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "inLeapYear");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return IsoDate.isLeapYear(plainYearMonth.getIsoDate().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "month");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return JSNumber.of(plainYearMonth.getIsoDate().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "monthCode");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return new JSString(TemporalUtils.monthCode(plainYearMonth.getIsoDate().month()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "monthsInYear");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return JSNumber.of(12);
    }

    public static JSValue referenceISODay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "referenceISODay");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return JSNumber.of(plainYearMonth.getIsoDate().day());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "since");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth other = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int monthsDiff = (plainYearMonth.getIsoDate().year() - other.getIsoDate().year()) * 12
                + (plainYearMonth.getIsoDate().month() - other.getIsoDate().month());
        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, monthsDiff, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "subtract");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, plainYearMonth, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "toJSON");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        IsoDate isoDate = plainYearMonth.getIsoDate();
        return new JSString(String.format(Locale.ROOT, "%04d-%02d", isoDate.year(), isoDate.month()));
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
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "toPlainDate");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        int dayOfMonth = TemporalUtils.getIntegerField(context, fields, "day", Integer.MIN_VALUE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        if (dayOfMonth == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainYearMonth.getIsoDate();
        if (!IsoDate.isValidIsoDate(isoDate.year(), isoDate.month(), dayOfMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateConstructor.createPlainDate(context,
                new IsoDate(isoDate.year(), isoDate.month(), dayOfMonth), plainYearMonth.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth jsTemporalPlainYearMonth = checkReceiver(context, thisArg, "toString");
        if (jsTemporalPlainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = jsTemporalPlainYearMonth.getIsoDate();
        boolean includeReferenceDay = !"never".equals(calendarNameOption)
                && (!"auto".equals(calendarNameOption) || !"iso8601".equals(jsTemporalPlainYearMonth.getCalendarId()));
        String result = TemporalUtils.formatIsoDate(isoDate.year(), isoDate.month(), includeReferenceDay ? isoDate.day() : 1);
        if (!includeReferenceDay) {
            result = result.substring(0, result.lastIndexOf('-'));
        }
        result = TemporalUtils.maybeAppendCalendar(result, jsTemporalPlainYearMonth.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "until");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth other = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int monthsDiff = (other.getIsoDate().year() - plainYearMonth.getIsoDate().year()) * 12
                + (other.getIsoDate().month() - plainYearMonth.getIsoDate().month());
        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, monthsDiff, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainYearMonth.prototype.valueOf; use Temporal.PlainYearMonth.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "with");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoDate original = plainYearMonth.getIsoDate();
        int year = TemporalUtils.getIntegerField(context, fields, "year", original.year());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int month = TemporalUtils.getIntegerField(context, fields, "month", original.month());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        month = Math.max(1, Math.min(12, month));
        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context,
                new IsoDate(year, month, 1), plainYearMonth.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "year");
        if (plainYearMonth == null) return JSUndefined.INSTANCE;
        return JSNumber.of(plainYearMonth.getIsoDate().year());
    }
}
