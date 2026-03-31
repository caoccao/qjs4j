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
 * Implementation of Temporal.PlainDateTime prototype methods.
 */
public final class TemporalPlainDateTimePrototype {
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final String TYPE_NAME = "Temporal.PlainDateTime";

    private TemporalPlainDateTimePrototype() {
    }

    // ========== Getters ==========

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "add");
        if (pdt == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, pdt, args, 1);
    }

    private static IsoDate addDurationToDate(
            JSContext context,
            IsoDate date,
            long years,
            long months,
            long weeks,
            long days,
            String overflow) {
        long totalDays;
        try {
            totalDays = Math.addExact(days, Math.multiplyExact(weeks, 7L));
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        long monthIndex = Math.addExact(date.month() - 1L, months);
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long balancedYear = Math.addExact(date.year(), years);
        balancedYear = Math.addExact(balancedYear, yearDelta);
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
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate resultDate = IsoDate.fromEpochDay(resultEpochDay);
        if (!IsoDate.isValidIsoDate(resultDate.year(), resultDate.month(), resultDate.day())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return resultDate;
    }

    private static TimeAddResult addDurationToTime(
            JSContext context,
            IsoTime time,
            TemporalDurationRecord durationRecord) {
        BigInteger durationTimeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));

        BigInteger totalNanoseconds = BigInteger.valueOf(time.totalNanoseconds()).add(durationTimeNanoseconds);
        BigInteger[] dayAndRemainder = totalNanoseconds.divideAndRemainder(DAY_NANOSECONDS);
        BigInteger dayCarryBigInteger = dayAndRemainder[0];
        BigInteger remainder = dayAndRemainder[1];
        if (remainder.signum() < 0) {
            remainder = remainder.add(DAY_NANOSECONDS);
            dayCarryBigInteger = dayCarryBigInteger.subtract(BigInteger.ONE);
        }

        long dayCarry;
        try {
            dayCarry = dayCarryBigInteger.longValueExact();
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        long normalizedTimeNanoseconds = remainder.longValue();
        IsoTime normalizedTime = IsoTime.fromNanoseconds(normalizedTimeNanoseconds);
        return new TimeAddResult(normalizedTime, dayCarry);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainDateTime pdt, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord durationRecord = temporalDuration.getRecord();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        TimeAddResult timeResult = addDurationToTime(context, pdt.getIsoDateTime().time(), durationRecord);
        if (context.hasPendingException() || timeResult == null) {
            return JSUndefined.INSTANCE;
        }

        long adjustedDays;
        try {
            adjustedDays = Math.addExact(durationRecord.days(), timeResult.dayCarry());
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        IsoDate newDate = addDurationToDate(
                context,
                pdt.getIsoDateTime().date(),
                durationRecord.years(),
                durationRecord.months(),
                durationRecord.weeks(),
                adjustedDays,
                overflow);
        if (context.hasPendingException() || newDate == null) {
            return JSUndefined.INSTANCE;
        }

        if (!isValidPlainDateTimeRange(newDate, timeResult.time())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(
                context,
                new IsoDateTime(newDate, timeResult.time()),
                pdt.getCalendarId());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "calendarId");
        if (pdt == null) return JSUndefined.INSTANCE;
        return new JSString(pdt.getCalendarId());
    }

    private static JSTemporalPlainDateTime checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainDateTime pdt)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return pdt;
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "day");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "dayOfWeek");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "dayOfYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().dayOfYear());
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "daysInMonth");
        if (pdt == null) return JSUndefined.INSTANCE;
        IsoDate d = pdt.getIsoDateTime().date();
        return JSNumber.of(IsoDate.daysInMonth(d.year(), d.month()));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "daysInWeek");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "daysInYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(IsoDate.daysInYear(pdt.getIsoDateTime().date().year()));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "equals");
        if (pdt == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        boolean equal = IsoDateTime.compareIsoDateTime(pdt.getIsoDateTime(), other.getIsoDateTime()) == 0
                && pdt.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "era");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "eraYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "hour");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().hour());
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "inLeapYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return IsoDate.isLeapYear(pdt.getIsoDateTime().date().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static boolean isValidPlainDateTimeRange(IsoDate date, IsoTime time) {
        long epochDay = date.toEpochDay();
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return false;
        }
        return epochDay != MIN_SUPPORTED_EPOCH_DAY || time.totalNanoseconds() != 0L;
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "microsecond");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "millisecond");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "minute");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().minute());
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "month");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "monthCode");
        if (pdt == null) return JSUndefined.INSTANCE;
        return new JSString(TemporalUtils.monthCode(pdt.getIsoDateTime().date().month()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "monthsInYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(12);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "nanosecond");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().nanosecond());
    }

    // ========== Methods ==========

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "round");
        if (pdt == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        String smallestUnit;
        int increment = 1;
        String roundingMode = "halfExpand";

        if (args[0] instanceof JSString unitStr) {
            smallestUnit = unitStr.value();
        } else if (args[0] instanceof JSObject options) {
            smallestUnit = TemporalUtils.getStringOption(context, options, "smallestUnit", null);
            if (smallestUnit == null) {
                context.throwRangeError("Temporal error: Must specify a roundTo parameter.");
                return JSUndefined.INSTANCE;
            }
            String incStr = TemporalUtils.getStringOption(context, options, "roundingIncrement", "1");
            try {
                increment = Integer.parseInt(incStr);
            } catch (NumberFormatException e) {
                // Keep default
            }
            roundingMode = TemporalUtils.getStringOption(context, options, "roundingMode", "halfExpand");
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return JSUndefined.INSTANCE;
        }

        long totalNs = pdt.getIsoDateTime().time().totalNanoseconds();
        long unitNs = unitToNanoseconds(smallestUnit);
        if (unitNs == 0) {
            context.throwRangeError("Temporal error: Invalid unit for rounding.");
            return JSUndefined.INSTANCE;
        }

        long incrementNs = unitNs * increment;
        long roundedNs = roundToIncrement(totalNs, incrementNs, roundingMode);

        // Handle day overflow
        int dayAdjust = 0;
        if (roundedNs >= 86_400_000_000_000L) {
            dayAdjust = (int) (roundedNs / 86_400_000_000_000L);
            roundedNs = Math.floorMod(roundedNs, 86_400_000_000_000L);
        } else if (roundedNs < 0) {
            dayAdjust = (int) Math.floorDiv(roundedNs, 86_400_000_000_000L);
            roundedNs = Math.floorMod(roundedNs, 86_400_000_000_000L);
        }

        IsoDate adjustedDate = dayAdjust != 0 ? pdt.getIsoDateTime().date().addDays(dayAdjust) : pdt.getIsoDateTime().date();
        IsoTime adjustedTime = IsoTime.fromNanoseconds(roundedNs);

        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new IsoDateTime(adjustedDate, adjustedTime), pdt.getCalendarId());
    }

    private static long roundToIncrement(long value, long increment, String mode) {
        if (increment == 0) return value;
        return switch (mode) {
            case "ceil" -> {
                long div = Math.floorDiv(value, increment);
                yield (value % increment == 0) ? value : (div + 1) * increment;
            }
            case "floor", "trunc" -> Math.floorDiv(value, increment) * increment;
            case "halfExpand" -> {
                long remainder = Math.floorMod(value, increment);
                if (remainder * 2 >= increment) {
                    yield Math.floorDiv(value, increment) * increment + increment;
                }
                yield Math.floorDiv(value, increment) * increment;
            }
            default -> Math.floorDiv(value, increment) * increment;
        };
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "second");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "since");
        if (pdt == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        long daysDiff = pdt.getIsoDateTime().date().toEpochDay() - other.getIsoDateTime().date().toEpochDay();
        long timeDiffNs = pdt.getIsoDateTime().time().totalNanoseconds() - other.getIsoDateTime().time().totalNanoseconds();
        long totalNs = daysDiff * 86_400_000_000_000L + timeDiffNs;

        TemporalDurationRecord balanced = TemporalDurationPrototype.balanceTimeDuration(totalNs, "day");
        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, 0, 0, balanced.days(),
                        balanced.hours(), balanced.minutes(), balanced.seconds(),
                        balanced.milliseconds(), balanced.microseconds(), balanced.nanoseconds()));
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "subtract");
        if (pdt == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, pdt, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toJSON");
        if (pdt == null) return JSUndefined.INSTANCE;
        return new JSString(pdt.getIsoDateTime().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toLocaleString");
        if (pdt == null) {
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
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{pdt});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toPlainDate");
        if (pdt == null) return JSUndefined.INSTANCE;
        return TemporalPlainDateConstructor.createPlainDate(context, pdt.getIsoDateTime().date(), pdt.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toPlainTime");
        if (pdt == null) return JSUndefined.INSTANCE;
        return TemporalPlainTimeConstructor.createPlainTime(context, pdt.getIsoDateTime().time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toString");
        if (pdt == null) return JSUndefined.INSTANCE;

        Object fractionalSecondDigits = "auto";
        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, optionsValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (optionsValue instanceof JSObject options) {
            JSValue fsdValue = options.get(PropertyKey.fromString("fractionalSecondDigits"));
            if (fsdValue instanceof JSNumber fsdNum) {
                fractionalSecondDigits = (int) fsdNum.value();
            }
        }

        IsoDate d = pdt.getIsoDateTime().date();
        IsoTime t = pdt.getIsoDateTime().time();
        String dateStr = TemporalUtils.formatIsoDate(d.year(), d.month(), d.day());
        String timeStr = TemporalUtils.formatIsoTimeWithPrecision(t.hour(), t.minute(), t.second(),
                t.millisecond(), t.microsecond(), t.nanosecond(), fractionalSecondDigits);
        String result = dateStr + "T" + timeStr;
        result = TemporalUtils.maybeAppendCalendar(result, pdt.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    private static long unitToNanoseconds(String unit) {
        return switch (unit) {
            case "day" -> 86_400_000_000_000L;
            case "hour" -> 3_600_000_000_000L;
            case "minute" -> 60_000_000_000L;
            case "second" -> 1_000_000_000L;
            case "millisecond" -> 1_000_000L;
            case "microsecond" -> 1_000L;
            case "nanosecond" -> 1L;
            default -> 0;
        };
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "until");
        if (pdt == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        long daysDiff = other.getIsoDateTime().date().toEpochDay() - pdt.getIsoDateTime().date().toEpochDay();
        long timeDiffNs = other.getIsoDateTime().time().totalNanoseconds() - pdt.getIsoDateTime().time().totalNanoseconds();
        long totalNs = daysDiff * 86_400_000_000_000L + timeDiffNs;

        TemporalDurationRecord balanced = TemporalDurationPrototype.balanceTimeDuration(totalNs, "day");
        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, 0, 0, balanced.days(),
                        balanced.hours(), balanced.minutes(), balanced.seconds(),
                        balanced.milliseconds(), balanced.microseconds(), balanced.nanoseconds()));
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainDateTime.prototype.valueOf; use Temporal.PlainDateTime.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "weekOfYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "with");
        if (pdt == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoDate origDate = pdt.getIsoDateTime().date();
        IsoTime origTime = pdt.getIsoDateTime().time();

        int year = TemporalUtils.getIntegerField(context, fields, "year", origDate.year());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int month = TemporalUtils.getIntegerField(context, fields, "month", origDate.month());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int day = TemporalUtils.getIntegerField(context, fields, "day", origDate.day());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int hour = TemporalUtils.getIntegerField(context, fields, "hour", origTime.hour());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int minute = TemporalUtils.getIntegerField(context, fields, "minute", origTime.minute());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int second = TemporalUtils.getIntegerField(context, fields, "second", origTime.second());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int millisecond = TemporalUtils.getIntegerField(context, fields, "millisecond", origTime.millisecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int microsecond = TemporalUtils.getIntegerField(context, fields, "microsecond", origTime.microsecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", origTime.nanosecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        IsoDate constrained = TemporalUtils.constrainIsoDate(year, month, day);
        IsoTime constrainedTime = TemporalUtils.constrainIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new IsoDateTime(constrained, constrainedTime), pdt.getCalendarId());
    }

    // ========== Internal helpers ==========

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "withCalendar");
        if (pdt == null) return JSUndefined.INSTANCE;

        String calendarId = "iso8601";
        if (args.length > 0) {
            calendarId = TemporalUtils.validateCalendar(context, args[0]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context, pdt.getIsoDateTime(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "withPlainTime");
        if (pdt == null) return JSUndefined.INSTANCE;

        IsoTime time = IsoTime.MIDNIGHT;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue timeArg = args[0];
            if (timeArg instanceof JSTemporalPlainTime pt) {
                time = pt.getIsoTime();
            } else if (timeArg instanceof JSString timeStr) {
                time = TemporalParser.parseTimeString(context, timeStr.value());
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
            } else if (timeArg instanceof JSObject timeObj) {
                int h = TemporalUtils.getIntegerField(context, timeObj, "hour", 0);
                int m = TemporalUtils.getIntegerField(context, timeObj, "minute", 0);
                int s = TemporalUtils.getIntegerField(context, timeObj, "second", 0);
                int ms = TemporalUtils.getIntegerField(context, timeObj, "millisecond", 0);
                int us = TemporalUtils.getIntegerField(context, timeObj, "microsecond", 0);
                int ns = TemporalUtils.getIntegerField(context, timeObj, "nanosecond", 0);
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
                time = new IsoTime(h, m, s, ms, us, ns);
            }
        }
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new IsoDateTime(pdt.getIsoDateTime().date(), time), pdt.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "year");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "yearOfWeek");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().yearOfWeek());
    }

    private record TimeAddResult(IsoTime time, long dayCarry) {
    }
}
