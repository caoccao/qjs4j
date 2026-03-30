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
import com.caoccao.qjs4j.core.temporal.*;

import java.math.BigInteger;

/**
 * Implementation of Temporal.PlainDate prototype methods.
 */
public final class TemporalPlainDatePrototype {
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final String TYPE_NAME = "Temporal.PlainDate";

    private TemporalPlainDatePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "add");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDate, args, 1);
    }

    // ========== Getters ==========

    private static IsoDate addDurationToDate(
            JSContext context,
            IsoDate date,
            TemporalDurationRecord durationRecord,
            String overflow) {
        BigInteger totalTimeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
        TemporalDurationRecord balancedTimeDuration =
                TemporalDurationPrototype.balanceTimeDuration(totalTimeNanoseconds, "day");

        long totalDays;
        try {
            long weeksInDays = Math.multiplyExact(durationRecord.weeks(), 7L);
            totalDays = Math.addExact(durationRecord.days(), weeksInDays);
            totalDays = Math.addExact(totalDays, balancedTimeDuration.days());
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        long monthIndex = Math.addExact(date.month() - 1L, durationRecord.months());
        long balancedYearDelta = Math.floorDiv(monthIndex, 12L);
        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long balancedYear = Math.addExact(date.year(), durationRecord.years());
        balancedYear = Math.addExact(balancedYear, balancedYearDelta);

        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int balancedYearInt = (int) balancedYear;
        int maxDay = IsoDate.daysInMonth(balancedYearInt, balancedMonth);
        int regulatedDay = date.day();
        if ("reject".equals(overflow)) {
            if (regulatedDay > maxDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            regulatedDay = Math.min(regulatedDay, maxDay);
        }

        IsoDate intermediateDate = new IsoDate(balancedYearInt, balancedMonth, regulatedDay);
        long intermediateEpochDay = intermediateDate.toEpochDay();
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateEpochDay, totalDays);
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        IsoDate result = IsoDate.fromEpochDay(resultEpochDay);
        if (!IsoDate.isValidIsoDate(result.year(), result.month(), result.day())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return result;
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainDate plainDate, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord durationRecord = temporalDuration.getRecord();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate result = addDurationToDate(context, plainDate.getIsoDate(), durationRecord, overflow);
        if (context.hasPendingException() || result == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateConstructor.createPlainDate(context, result, plainDate.getCalendarId());
    }

    static IsoDate addToIsoDate(IsoDate date, int years, int months, int weeks, int days) {
        long monthIndex = (long) date.month() - 1L + months;
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int normalizedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        int normalizedYear = (int) (date.year() + years + yearDelta);
        int maxDay = IsoDate.daysInMonth(normalizedYear, normalizedMonth);
        int normalizedDay = Math.min(date.day(), maxDay);
        IsoDate intermediate = new IsoDate(normalizedYear, normalizedMonth, normalizedDay);
        long totalDays = (long) weeks * 7L + days;
        return intermediate.addDays((int) totalDays);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "calendarId");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.getCalendarId());
    }

    private static JSTemporalPlainDate checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainDate plainDate)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainDate;
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "day");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "dayOfWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "dayOfYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().dayOfYear());
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "daysInMonth");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate d = plainDate.getIsoDate();
        return JSNumber.of(IsoDate.daysInMonth(d.year(), d.month()));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "daysInWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "daysInYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(IsoDate.daysInYear(plainDate.getIsoDate().year()));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "equals");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = IsoDate.compareIsoDate(plainDate.getIsoDate(), other.getIsoDate()) == 0
                && plainDate.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "era");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        // ISO calendar has no era
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "eraYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        // ISO calendar has no eraYear
        return JSUndefined.INSTANCE;
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "inLeapYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return IsoDate.isLeapYear(plainDate.getIsoDate().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "month");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "monthCode");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(TemporalUtils.monthCode(plainDate.getIsoDate().month()));
    }

    // ========== Methods ==========

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "monthsInYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(12);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "since");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long daysDiff = plainDate.getIsoDate().toEpochDay() - other.getIsoDate().toEpochDay();
        return TemporalDurationConstructor.createDuration(
                context,
                new TemporalDurationRecord(0, 0, 0, daysDiff, 0, 0, 0, 0, 0, 0));
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "subtract");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDate, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toJSON");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.getIsoDate().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toLocaleString");
        if (plainDate == null) {
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
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainDate});
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toPlainDateTime");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoTime time = IsoTime.MIDNIGHT;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue timeArg = args[0];
            if (timeArg instanceof JSTemporalPlainTime pt) {
                time = pt.getIsoTime();
            } else if (timeArg instanceof JSString timeStr) {
                time = TemporalParser.parseTimeString(context, timeStr.value());
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            } else if (timeArg instanceof JSObject timeObj) {
                int hour = TemporalUtils.getIntegerField(context, timeObj, "hour", 0);
                int minute = TemporalUtils.getIntegerField(context, timeObj, "minute", 0);
                int second = TemporalUtils.getIntegerField(context, timeObj, "second", 0);
                int millisecond = TemporalUtils.getIntegerField(context, timeObj, "millisecond", 0);
                int microsecond = TemporalUtils.getIntegerField(context, timeObj, "microsecond", 0);
                int nanosecond = TemporalUtils.getIntegerField(context, timeObj, "nanosecond", 0);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                time = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            }
        }
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new com.caoccao.qjs4j.core.temporal.IsoDateTime(plainDate.getIsoDate(), time), plainDate.getCalendarId());
    }

    public static JSValue toPlainMonthDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toPlainMonthDay");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate d = plainDate.getIsoDate();
        return TemporalPlainMonthDayConstructor.createPlainMonthDay(context,
                new IsoDate(1972, d.month(), d.day()), plainDate.getCalendarId());
    }

    public static JSValue toPlainYearMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toPlainYearMonth");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate d = plainDate.getIsoDate();
        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context,
                new IsoDate(d.year(), d.month(), 1), plainDate.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toString");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        String result = plainDate.getIsoDate().toString();
        result = TemporalUtils.maybeAppendCalendar(result, plainDate.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "until");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long daysDiff = other.getIsoDate().toEpochDay() - plainDate.getIsoDate().toEpochDay();
        return TemporalDurationConstructor.createDuration(
                context,
                new TemporalDurationRecord(0, 0, 0, daysDiff, 0, 0, 0, 0, 0, 0));
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainDate.prototype.valueOf; use Temporal.PlainDate.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "weekOfYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "with");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }

        IsoDate original = plainDate.getIsoDate();
        JSValue yearVal = fields.get(PropertyKey.fromString("year"));
        JSValue monthVal = fields.get(PropertyKey.fromString("month"));
        JSValue monthCodeVal = fields.get(PropertyKey.fromString("monthCode"));
        JSValue dayVal = fields.get(PropertyKey.fromString("day"));

        boolean hasAnyField = !(yearVal instanceof JSUndefined || yearVal == null)
                || !(monthVal instanceof JSUndefined || monthVal == null)
                || !(monthCodeVal instanceof JSUndefined || monthCodeVal == null)
                || !(dayVal instanceof JSUndefined || dayVal == null);
        if (!hasAnyField) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }

        int year = (yearVal instanceof JSUndefined || yearVal == null) ? original.year()
                : TemporalUtils.toIntegerThrowOnInfinity(context, yearVal);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int month;
        if (monthVal instanceof JSUndefined || monthVal == null) {
            if (monthCodeVal instanceof JSString monthCodeStr) {
                month = TemporalPlainDateConstructor.parseMonthCode(context, monthCodeStr.value());
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            } else {
                month = original.month();
            }
        } else {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthVal);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        int day = (dayVal instanceof JSUndefined || dayVal == null) ? original.day()
                : TemporalUtils.toIntegerThrowOnInfinity(context, dayVal);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return TemporalPlainDateConstructor.createPlainDate(context, new IsoDate(year, month, day), plainDate.getCalendarId());
        } else {
            IsoDate constrained = TemporalUtils.constrainIsoDate(year, month, day);
            return TemporalPlainDateConstructor.createPlainDate(context, constrained, plainDate.getCalendarId());
        }
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "withCalendar");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarId = TemporalUtils.validateCalendar(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateConstructor.createPlainDate(context, plainDate.getIsoDate(), calendarId);
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "year");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "yearOfWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().yearOfWeek());
    }
}
