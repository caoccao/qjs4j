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
import java.time.DateTimeException;

/**
 * Implementation of Temporal.PlainDate prototype methods.
 */
public final class TemporalPlainDatePrototype {
    private static final String DIFFERENCE_LARGEST_UNIT_OPTION = "largestUnit";
    private static final String DIFFERENCE_ROUNDING_INCREMENT_OPTION = "roundingIncrement";
    private static final String DIFFERENCE_ROUNDING_MODE_OPTION = "roundingMode";
    private static final String DIFFERENCE_SMALLEST_UNIT_OPTION = "smallestUnit";
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final long TEMPORAL_MAX_ROUNDING_INCREMENT = 1_000_000_000L;
    private static final String TYPE_NAME = "Temporal.PlainDate";
    private static final String UNIT_AUTO = "auto";
    private static final String UNIT_DAY = "day";
    private static final String UNIT_HOUR = "hour";
    private static final String UNIT_MICROSECOND = "microsecond";
    private static final String UNIT_MILLISECOND = "millisecond";
    private static final String UNIT_MINUTE = "minute";
    private static final String UNIT_MONTH = "month";
    private static final String UNIT_NANOSECOND = "nanosecond";
    private static final String UNIT_SECOND = "second";
    private static final String UNIT_WEEK = "week";
    private static final String UNIT_YEAR = "year";

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

    private static DateDurationFields adjustDateDurationRecord(
            DateDurationFields dateDuration,
            long newDays,
            Long newWeeks,
            Long newMonths) {
        long adjustedMonths = newMonths == null ? dateDuration.months() : newMonths;
        long adjustedWeeks = newWeeks == null ? dateDuration.weeks() : newWeeks;
        return new DateDurationFields(
                dateDuration.years(),
                adjustedMonths,
                adjustedWeeks,
                newDays);
    }

    private static long applyUnsignedRoundingMode(
            long roundingFloor,
            long roundingCeiling,
            int comparison,
            boolean evenCardinality,
            String unsignedRoundingMode) {
        if ("zero".equals(unsignedRoundingMode)) {
            return roundingFloor;
        }
        if ("infinity".equals(unsignedRoundingMode)) {
            return roundingCeiling;
        }
        if (comparison < 0) {
            return roundingFloor;
        }
        if (comparison > 0) {
            return roundingCeiling;
        }
        if ("half-zero".equals(unsignedRoundingMode)) {
            return roundingFloor;
        }
        if ("half-infinity".equals(unsignedRoundingMode)) {
            return roundingCeiling;
        }
        return evenCardinality ? roundingFloor : roundingCeiling;
    }

    private static YearMonthBalance balanceIsoYearMonth(long year, long month) {
        long monthIndex = month - 1;
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int normalizedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long normalizedYear = year + yearDelta;
        return new YearMonthBalance(normalizedYear, normalizedMonth);
    }

    private static DateDurationFields bubbleRelativeDuration(
            JSContext context,
            int sign,
            DateDurationFields duration,
            long nudgedEpochDay,
            IsoDate originDate,
            String largestUnit,
            String smallestUnit) {
        if (smallestUnit.equals(largestUnit)) {
            return duration;
        }
        int largestUnitIndex = temporalUnitRank(largestUnit);
        int smallestUnitIndex = temporalUnitRank(smallestUnit);
        for (int unitIndex = smallestUnitIndex - 1; unitIndex >= largestUnitIndex; unitIndex--) {
            String unit = temporalUnitByRank(unitIndex);
            if (UNIT_WEEK.equals(unit) && !UNIT_WEEK.equals(largestUnit)) {
                continue;
            }

            DateDurationFields endDuration;
            if (UNIT_YEAR.equals(unit)) {
                endDuration = new DateDurationFields(duration.years() + sign, 0, 0, 0);
            } else if (UNIT_MONTH.equals(unit)) {
                endDuration = adjustDateDurationRecord(duration, 0, 0L, duration.months() + sign);
            } else {
                endDuration = adjustDateDurationRecord(duration, 0, duration.weeks() + sign, null);
            }

            IsoDate endDate = calendarDateAddConstrain(context, originDate, endDuration);
            if (context.hasPendingException() || endDate == null) {
                return null;
            }
            boolean didExpandToEnd = Long.compare(nudgedEpochDay, endDate.toEpochDay()) != -sign;
            if (didExpandToEnd) {
                duration = endDuration;
            } else {
                break;
            }
        }
        return duration;
    }

    private static IsoDate calendarDateAddConstrain(JSContext context, IsoDate baseDate, DateDurationFields dateDuration) {
        TemporalDurationRecord durationRecord = new TemporalDurationRecord(
                dateDuration.years(),
                dateDuration.months(),
                dateDuration.weeks(),
                dateDuration.days(),
                0,
                0,
                0,
                0,
                0,
                0);
        return addDurationToDate(context, baseDate, durationRecord, "constrain");
    }

    private static DateDurationFields calendarDateUntil(IsoDate one, IsoDate two, String largestUnit) {
        int sign = -Integer.signum(IsoDate.compareIsoDate(one, two));
        if (sign == 0) {
            return DateDurationFields.ZERO;
        }

        long years = 0;
        long months = 0;
        if (UNIT_YEAR.equals(largestUnit) || UNIT_MONTH.equals(largestUnit)) {
            long candidateYears = (long) two.year() - one.year();
            if (candidateYears != 0) {
                candidateYears -= sign;
            }
            while (!isoDateSurpasses(sign, one.year() + candidateYears, one.month(), one.day(), two)) {
                years = candidateYears;
                candidateYears += sign;
            }

            long candidateMonths = sign;
            YearMonthBalance intermediateYearMonth = balanceIsoYearMonth(one.year() + years, one.month() + candidateMonths);
            while (!isoDateSurpasses(sign, intermediateYearMonth.year(), intermediateYearMonth.month(), one.day(), two)) {
                months = candidateMonths;
                candidateMonths += sign;
                intermediateYearMonth = balanceIsoYearMonth(intermediateYearMonth.year(), intermediateYearMonth.month() + sign);
            }

            if (UNIT_MONTH.equals(largestUnit)) {
                months += years * 12;
                years = 0;
            }
        }

        YearMonthBalance intermediateYearMonth = balanceIsoYearMonth(one.year() + years, one.month() + months);
        IsoDate constrainedDate = constrainIsoDate(intermediateYearMonth.year(), intermediateYearMonth.month(), one.day());
        long dayDifference = two.toEpochDay() - constrainedDate.toEpochDay();
        long weeks = 0;
        if (UNIT_WEEK.equals(largestUnit)) {
            weeks = dayDifference / 7;
            dayDifference = dayDifference % 7;
        }
        return new DateDurationFields(years, months, weeks, dayDifference);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "calendarId");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.getCalendarId());
    }

    private static String canonicalizeTemporalUnit(String unitText, boolean allowAuto) {
        if (unitText == null) {
            return null;
        }
        if (allowAuto && UNIT_AUTO.equals(unitText)) {
            return UNIT_AUTO;
        }
        return switch (unitText) {
            case UNIT_YEAR, "years" -> UNIT_YEAR;
            case UNIT_MONTH, "months" -> UNIT_MONTH;
            case UNIT_WEEK, "weeks" -> UNIT_WEEK;
            case UNIT_DAY, "days" -> UNIT_DAY;
            case UNIT_HOUR, "hours" -> UNIT_HOUR;
            case UNIT_MINUTE, "minutes" -> UNIT_MINUTE;
            case UNIT_SECOND, "seconds" -> UNIT_SECOND;
            case UNIT_MILLISECOND, "milliseconds" -> UNIT_MILLISECOND;
            case UNIT_MICROSECOND, "microseconds" -> UNIT_MICROSECOND;
            case UNIT_NANOSECOND, "nanoseconds" -> UNIT_NANOSECOND;
            default -> null;
        };
    }

    private static JSTemporalPlainDate checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainDate plainDate)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainDate;
    }

    private static IsoDate constrainIsoDate(long year, int month, int day) {
        int constrainedYear = (int) year;
        int constrainedDay = Math.min(day, IsoDate.daysInMonth(constrainedYear, month));
        return new IsoDate(constrainedYear, month, constrainedDay);
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

    // ========== Methods ==========

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

    private static JSValue differenceTemporalPlainDate(
            JSContext context,
            JSTemporalPlainDate plainDate,
            JSValue[] args,
            boolean sinceOperation) {
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException() || other == null) {
            return JSUndefined.INSTANCE;
        }
        if (!plainDate.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        DifferenceSettings settings = getDifferenceSettings(context, sinceOperation, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate thisDate = plainDate.getIsoDate();
        IsoDate otherDate = other.getIsoDate();
        if (IsoDate.compareIsoDate(thisDate, otherDate) == 0) {
            return TemporalDurationConstructor.createDuration(context, TemporalDurationRecord.ZERO);
        }

        DateDurationFields dateDifference = calendarDateUntil(thisDate, otherDate, settings.largestUnit());
        boolean roundingNoOp = UNIT_DAY.equals(settings.smallestUnit()) && settings.roundingIncrement() == 1L;
        if (!roundingNoOp) {
            dateDifference = roundRelativeDurationDate(
                    context,
                    dateDifference,
                    otherDate.toEpochDay(),
                    thisDate,
                    settings);
            if (context.hasPendingException() || dateDifference == null) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalDurationRecord resultDuration = new TemporalDurationRecord(
                dateDifference.years(),
                dateDifference.months(),
                dateDifference.weeks(),
                dateDifference.days(),
                0,
                0,
                0,
                0,
                0,
                0);
        if (sinceOperation) {
            resultDuration = resultDuration.negated();
        }
        return TemporalDurationConstructor.createDuration(context, resultDuration);
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

    private static long getDifferenceRoundingIncrementOption(JSContext context, JSObject optionsObject) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(DIFFERENCE_ROUNDING_INCREMENT_OPTION));
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return 1L;
        }
        JSNumber numericValue = JSTypeConversions.toNumber(context, optionValue);
        if (context.hasPendingException() || numericValue == null) {
            return Long.MIN_VALUE;
        }
        double roundingIncrement = numericValue.value();
        if (!Double.isFinite(roundingIncrement) || Double.isNaN(roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        long integerIncrement = (long) roundingIncrement;
        if (integerIncrement < 1L || integerIncrement > TEMPORAL_MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        return integerIncrement;
    }

    private static DifferenceSettings getDifferenceSettings(
            JSContext context,
            boolean sinceOperation,
            JSValue optionsArg) {
        JSObject optionsObject = null;
        if (!(optionsArg instanceof JSUndefined) && optionsArg != null) {
            if (optionsArg instanceof JSObject castedOptionsObject) {
                optionsObject = castedOptionsObject;
            } else {
                context.throwTypeError("Temporal error: Options must be an object.");
                return null;
            }
        }

        String largestUnitText = null;
        long roundingIncrement = 1L;
        String roundingMode = "trunc";
        String smallestUnitText = null;
        if (optionsObject != null) {
            largestUnitText = getDifferenceStringOption(context, optionsObject, DIFFERENCE_LARGEST_UNIT_OPTION, null);
            if (context.hasPendingException()) {
                return null;
            }

            roundingIncrement = getDifferenceRoundingIncrementOption(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }

            roundingMode = getDifferenceStringOption(context, optionsObject, DIFFERENCE_ROUNDING_MODE_OPTION, "trunc");
            if (context.hasPendingException()) {
                return null;
            }

            smallestUnitText = getDifferenceStringOption(context, optionsObject, DIFFERENCE_SMALLEST_UNIT_OPTION, null);
            if (context.hasPendingException()) {
                return null;
            }
        }

        String largestUnit = largestUnitText == null
                ? UNIT_AUTO
                : canonicalizeTemporalUnit(largestUnitText, true);
        if (largestUnit == null) {
            context.throwRangeError("Temporal error: Invalid largest unit.");
            return null;
        }
        String smallestUnit = smallestUnitText == null
                ? UNIT_DAY
                : canonicalizeTemporalUnit(smallestUnitText, false);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid smallest unit.");
            return null;
        }
        if (!isValidDifferenceRoundingMode(roundingMode)) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }
        if (sinceOperation) {
            roundingMode = negateRoundingMode(roundingMode);
        }
        if (!UNIT_AUTO.equals(largestUnit) && !isDateUnit(largestUnit)) {
            context.throwRangeError("Temporal error: Invalid largest unit.");
            return null;
        }
        if (!isDateUnit(smallestUnit)) {
            context.throwRangeError("Temporal error: Invalid smallest unit.");
            return null;
        }

        if (UNIT_AUTO.equals(largestUnit)) {
            largestUnit = largerOfTwoTemporalUnits(UNIT_DAY, smallestUnit);
        }
        if (!largestUnit.equals(largerOfTwoTemporalUnits(largestUnit, smallestUnit))) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        return new DifferenceSettings(largestUnit, smallestUnit, roundingIncrement, roundingMode);
    }

    private static String getDifferenceStringOption(JSContext context, JSObject optionsObject, String optionName, String defaultValue) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(optionName));
        if (context.hasPendingException()) {
            return null;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return defaultValue;
        }
        JSString optionText = JSTypeConversions.toString(context, optionValue);
        if (context.hasPendingException() || optionText == null) {
            return null;
        }
        return optionText.value();
    }

    private static String getUnsignedRoundingMode(String roundingMode, String sign) {
        boolean negativeSign = "negative".equals(sign);
        return switch (roundingMode) {
            case "ceil" -> negativeSign ? "zero" : "infinity";
            case "floor" -> negativeSign ? "infinity" : "zero";
            case "expand" -> "infinity";
            case "trunc" -> "zero";
            case "halfCeil" -> negativeSign ? "half-zero" : "half-infinity";
            case "halfFloor" -> negativeSign ? "half-infinity" : "half-zero";
            case "halfExpand" -> "half-infinity";
            case "halfTrunc" -> "half-zero";
            case "halfEven" -> "half-even";
            default -> "half-infinity";
        };
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "inLeapYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return IsoDate.isLeapYear(plainDate.getIsoDate().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static boolean isCalendarUnit(String unit) {
        return UNIT_YEAR.equals(unit) || UNIT_MONTH.equals(unit) || UNIT_WEEK.equals(unit);
    }

    private static boolean isDateUnit(String unit) {
        return UNIT_YEAR.equals(unit)
                || UNIT_MONTH.equals(unit)
                || UNIT_WEEK.equals(unit)
                || UNIT_DAY.equals(unit);
    }

    private static boolean isValidDifferenceRoundingMode(String roundingMode) {
        return "ceil".equals(roundingMode)
                || "floor".equals(roundingMode)
                || "trunc".equals(roundingMode)
                || "expand".equals(roundingMode)
                || "halfExpand".equals(roundingMode)
                || "halfTrunc".equals(roundingMode)
                || "halfEven".equals(roundingMode)
                || "halfCeil".equals(roundingMode)
                || "halfFloor".equals(roundingMode);
    }

    private static boolean isoDateSurpasses(int sign, long year, long month, long day, IsoDate isoDate) {
        if (year != isoDate.year()) {
            return sign * (year - isoDate.year()) > 0;
        }
        if (month != isoDate.month()) {
            return sign * (month - isoDate.month()) > 0;
        }
        if (day != isoDate.day()) {
            return sign * (day - isoDate.day()) > 0;
        }
        return false;
    }

    private static String largerOfTwoTemporalUnits(String leftUnit, String rightUnit) {
        int leftRank = temporalUnitRank(leftUnit);
        int rightRank = temporalUnitRank(rightUnit);
        if (leftRank > rightRank) {
            return rightUnit;
        }
        return leftUnit;
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

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "monthsInYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(12);
    }

    private static String negateRoundingMode(String roundingMode) {
        return switch (roundingMode) {
            case "ceil" -> "floor";
            case "floor" -> "ceil";
            case "halfCeil" -> "halfFloor";
            case "halfFloor" -> "halfCeil";
            default -> roundingMode;
        };
    }

    private static NudgeResult nudgeToCalendarUnit(
            JSContext context,
            int sign,
            DateDurationFields duration,
            long destinationEpochDay,
            IsoDate originDate,
            DifferenceSettings settings) {
        String smallestUnit = settings.smallestUnit();
        long increment = settings.roundingIncrement();
        long roundingStartValue;
        long roundingEndValue;
        DateDurationFields startDuration;
        DateDurationFields endDuration;
        if (UNIT_YEAR.equals(smallestUnit)) {
            long roundedYears = roundNumberToIncrement(duration.years(), increment, "trunc");
            roundingStartValue = roundedYears;
            roundingEndValue = roundedYears + increment * sign;
            startDuration = new DateDurationFields(roundedYears, 0, 0, 0);
            endDuration = new DateDurationFields(roundingEndValue, 0, 0, 0);
        } else if (UNIT_MONTH.equals(smallestUnit)) {
            long roundedMonths = roundNumberToIncrement(duration.months(), increment, "trunc");
            roundingStartValue = roundedMonths;
            roundingEndValue = roundedMonths + increment * sign;
            startDuration = adjustDateDurationRecord(duration, 0, 0L, roundedMonths);
            endDuration = adjustDateDurationRecord(duration, 0, 0L, roundingEndValue);
        } else if (UNIT_WEEK.equals(smallestUnit)) {
            DateDurationFields yearsAndMonthsDuration = adjustDateDurationRecord(duration, 0, 0L, null);
            IsoDate weeksStart = calendarDateAddConstrain(context, originDate, yearsAndMonthsDuration);
            if (context.hasPendingException() || weeksStart == null) {
                return null;
            }
            long weeksEndEpochDay = weeksStart.toEpochDay() + duration.days();
            IsoDate weeksEnd = IsoDate.fromEpochDay(weeksEndEpochDay);
            DateDurationFields weekDifference = calendarDateUntil(weeksStart, weeksEnd, UNIT_WEEK);
            long roundedWeeks = roundNumberToIncrement(duration.weeks() + weekDifference.weeks(), increment, "trunc");
            roundingStartValue = roundedWeeks;
            roundingEndValue = roundedWeeks + increment * sign;
            startDuration = adjustDateDurationRecord(duration, 0, roundedWeeks, null);
            endDuration = adjustDateDurationRecord(duration, 0, roundingEndValue, null);
        } else {
            long roundedDays = roundNumberToIncrement(duration.days(), increment, "trunc");
            roundingStartValue = roundedDays;
            roundingEndValue = roundedDays + increment * sign;
            startDuration = adjustDateDurationRecord(duration, roundedDays, null, null);
            endDuration = adjustDateDurationRecord(duration, roundingEndValue, null, null);
        }

        IsoDate startDate = calendarDateAddConstrain(context, originDate, startDuration);
        if (context.hasPendingException() || startDate == null) {
            return null;
        }
        IsoDate endDate = calendarDateAddConstrain(context, originDate, endDuration);
        if (context.hasPendingException() || endDate == null) {
            return null;
        }
        long startEpochDay = startDate.toEpochDay();
        long endEpochDay = endDate.toEpochDay();
        long numerator = destinationEpochDay - startEpochDay;
        long denominator = endEpochDay - startEpochDay;
        if (denominator == 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String unsignedRoundingMode = getUnsignedRoundingMode(
                settings.roundingMode(),
                sign < 0 ? "negative" : "positive");
        int comparison = BigInteger.valueOf(Math.abs(numerator)).shiftLeft(1)
                .compareTo(BigInteger.valueOf(Math.abs(denominator)));
        int roundingComparison = Integer.compare(comparison, 0);
        boolean isEvenCardinality = Math.floorMod(Math.abs(roundingStartValue) / increment, 2L) == 0L;
        long roundedUnit;
        if (numerator == 0) {
            roundedUnit = Math.abs(roundingStartValue);
        } else if (numerator == denominator) {
            roundedUnit = Math.abs(roundingEndValue);
        } else {
            roundedUnit = applyUnsignedRoundingMode(
                    Math.abs(roundingStartValue),
                    Math.abs(roundingEndValue),
                    roundingComparison,
                    isEvenCardinality,
                    unsignedRoundingMode);
        }

        boolean didExpandCalendarUnit = roundedUnit == Math.abs(roundingEndValue);
        DateDurationFields roundedDuration = didExpandCalendarUnit ? endDuration : startDuration;
        long nudgedEpochDay = didExpandCalendarUnit ? endEpochDay : startEpochDay;
        return new NudgeResult(roundedDuration, nudgedEpochDay, didExpandCalendarUnit);
    }

    private static NudgeResult nudgeToDayUnit(
            DateDurationFields duration,
            long destinationEpochDay,
            DifferenceSettings settings) {
        long originalDays = duration.days();
        long roundedDays = roundNumberToIncrement(originalDays, settings.roundingIncrement(), settings.roundingMode());
        long dayDelta = roundedDays - originalDays;
        int durationSign = Long.compare(originalDays, 0);
        int deltaSign = Long.compare(dayDelta, 0);
        boolean didExpandCalendarUnit = deltaSign == durationSign;
        DateDurationFields roundedDuration = adjustDateDurationRecord(duration, roundedDays, null, null);
        long nudgedEpochDay = destinationEpochDay + dayDelta;
        return new NudgeResult(roundedDuration, nudgedEpochDay, didExpandCalendarUnit);
    }

    private static long roundNumberToIncrement(long quantity, long increment, String roundingMode) {
        long quotient = quantity / increment;
        long remainder = quantity % increment;
        String sign = quantity < 0 ? "negative" : "positive";
        long roundingFloor = Math.abs(quotient);
        long roundingCeiling = roundingFloor + 1L;
        int comparison = Integer.compare(Long.compare(Math.abs(remainder * 2L), increment), 0);
        boolean evenCardinality = Math.floorMod(roundingFloor, 2L) == 0L;
        String unsignedRoundingMode = getUnsignedRoundingMode(roundingMode, sign);
        long rounded;
        if (remainder == 0L) {
            rounded = roundingFloor;
        } else {
            rounded = applyUnsignedRoundingMode(
                    roundingFloor,
                    roundingCeiling,
                    comparison,
                    evenCardinality,
                    unsignedRoundingMode);
        }
        return "positive".equals(sign) ? increment * rounded : -increment * rounded;
    }

    private static DateDurationFields roundRelativeDurationDate(
            JSContext context,
            DateDurationFields duration,
            long destinationEpochDay,
            IsoDate originDate,
            DifferenceSettings settings) {
        int sign = duration.sign() < 0 ? -1 : 1;
        NudgeResult nudgeResult;
        if (isCalendarUnit(settings.smallestUnit())) {
            nudgeResult = nudgeToCalendarUnit(context, sign, duration, destinationEpochDay, originDate, settings);
        } else {
            nudgeResult = nudgeToDayUnit(duration, destinationEpochDay, settings);
        }
        if (context.hasPendingException() || nudgeResult == null) {
            return null;
        }
        DateDurationFields roundedDuration = nudgeResult.duration();
        if (nudgeResult.didExpandCalendarUnit() && !UNIT_WEEK.equals(settings.smallestUnit())) {
            String bubbleSmallestUnit = largerOfTwoTemporalUnits(settings.smallestUnit(), UNIT_DAY);
            roundedDuration = bubbleRelativeDuration(
                    context,
                    sign,
                    roundedDuration,
                    nudgeResult.nudgedEpochDay(),
                    originDate,
                    settings.largestUnit(),
                    bubbleSmallestUnit);
            if (context.hasPendingException() || roundedDuration == null) {
                return null;
            }
        }
        return roundedDuration;
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "since");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainDate(context, plainDate, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "subtract");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDate, args, -1);
    }

    private static String temporalUnitByRank(int unitRank) {
        return switch (unitRank) {
            case 0 -> UNIT_YEAR;
            case 1 -> UNIT_MONTH;
            case 2 -> UNIT_WEEK;
            case 3 -> UNIT_DAY;
            case 4 -> UNIT_HOUR;
            case 5 -> UNIT_MINUTE;
            case 6 -> UNIT_SECOND;
            case 7 -> UNIT_MILLISECOND;
            case 8 -> UNIT_MICROSECOND;
            case 9 -> UNIT_NANOSECOND;
            default -> UNIT_NANOSECOND;
        };
    }

    private static int temporalUnitRank(String unit) {
        return switch (unit) {
            case UNIT_YEAR -> 0;
            case UNIT_MONTH -> 1;
            case UNIT_WEEK -> 2;
            case UNIT_DAY -> 3;
            case UNIT_HOUR -> 4;
            case UNIT_MINUTE -> 5;
            case UNIT_SECOND -> 6;
            case UNIT_MILLISECOND -> 7;
            case UNIT_MICROSECOND -> 8;
            case UNIT_NANOSECOND -> 9;
            default -> 10;
        };
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
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(context, args[0], JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            time = plainTime.getIsoTime();
        }

        IsoDate isoDate = plainDate.getIsoDate();
        if (isoDate.toEpochDay() == MIN_SUPPORTED_EPOCH_DAY && IsoTime.compareIsoTime(time, IsoTime.MIDNIGHT) == 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new com.caoccao.qjs4j.core.temporal.IsoDateTime(isoDate, time), plainDate.getCalendarId());
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
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String result = plainDate.getIsoDate().toString();
        result = TemporalUtils.maybeAppendCalendar(result, plainDate.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    private static String toTemporalTimeZoneIdentifier(JSContext context, JSValue timeZoneLike) {
        if (!(timeZoneLike instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
        }
        return TemporalDurationConstructor.parseTimeZoneIdentifierString(context, timeZoneString.value());
    }

    public static JSValue toZonedDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toZonedDateTime");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue timeZoneLike = item;
        JSValue plainTimeLike = JSUndefined.INSTANCE;
        if (item instanceof JSObject itemObject) {
            JSValue maybeTimeZone = itemObject.get(PropertyKey.fromString("timeZone"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(maybeTimeZone instanceof JSUndefined) && maybeTimeZone != null) {
                timeZoneLike = maybeTimeZone;
                plainTimeLike = itemObject.get(PropertyKey.fromString("plainTime"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }

        String timeZoneId = toTemporalTimeZoneIdentifier(context, timeZoneLike);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = plainDate.getIsoDate();
        IsoTime isoTime = IsoTime.MIDNIGHT;
        if (!(plainTimeLike instanceof JSUndefined) && plainTimeLike != null) {
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(context, plainTimeLike, JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            isoTime = plainTime.getIsoTime();
            if (isoDate.toEpochDay() == MIN_SUPPORTED_EPOCH_DAY
                    && IsoTime.compareIsoTime(isoTime, IsoTime.MIDNIGHT) == 0) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        BigInteger epochNanoseconds;
        try {
            epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    new IsoDateTime(isoDate, isoTime),
                    timeZoneId);
        } catch (DateTimeException e) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(
                context,
                epochNanoseconds,
                timeZoneId,
                plainDate.getCalendarId());
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "until");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainDate(context, plainDate, args, false);
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

    private record DateDurationFields(
            long years,
            long months,
            long weeks,
            long days) {
        private static final DateDurationFields ZERO = new DateDurationFields(0, 0, 0, 0);

        private int sign() {
            if (years > 0 || months > 0 || weeks > 0 || days > 0) {
                return 1;
            }
            if (years < 0 || months < 0 || weeks < 0 || days < 0) {
                return -1;
            }
            return 0;
        }
    }

    private record DifferenceSettings(
            String largestUnit,
            String smallestUnit,
            long roundingIncrement,
            String roundingMode) {
    }

    private record NudgeResult(
            DateDurationFields duration,
            long nudgedEpochDay,
            boolean didExpandCalendarUnit) {
    }

    private record YearMonthBalance(
            long year,
            int month) {
    }
}
