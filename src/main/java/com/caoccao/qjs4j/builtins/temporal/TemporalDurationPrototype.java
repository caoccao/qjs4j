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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Implementation of Temporal.Duration prototype methods.
 */
public final class TemporalDurationPrototype {
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;
    private static final BigInteger HOUR_NANOSECONDS = TemporalConstants.BI_HOUR_NANOSECONDS;
    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS = TemporalConstants.MAX_ABSOLUTE_TIME_NANOSECONDS;
    private static final BigInteger MAX_FLOAT64_MICROSECONDS_COMPONENT =
            new BigInteger("9007199254740991475711");
    private static final BigInteger MAX_FLOAT64_MILLISECONDS_COMPONENT =
            new BigInteger("9007199254740991487");
    private static final BigInteger MAX_FLOAT64_NANOSECONDS_COMPONENT =
            new BigInteger("9007199254740991463129087");
    private static final BigInteger MICROSECOND_NANOSECONDS = TemporalConstants.BI_MICROSECOND_NANOSECONDS;
    private static final BigInteger MILLISECOND_NANOSECONDS = TemporalConstants.BI_MILLISECOND_NANOSECONDS;
    private static final BigInteger MINUTE_NANOSECONDS = TemporalConstants.BI_MINUTE_NANOSECONDS;
    private static final BigInteger SECOND_NANOSECONDS = TemporalConstants.BI_SECOND_NANOSECONDS;
    private static final String TYPE_NAME = "Temporal.Duration";
    private static final BigInteger WEEK_NANOSECONDS = TemporalConstants.BI_WEEK_NANOSECONDS;

    private TemporalDurationPrototype() {
    }

    public static JSValue abs(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "abs");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, duration.getDuration().abs());
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "add");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, duration, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalDuration duration, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration other = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration leftRecord = duration.getDuration();
        TemporalDuration rightRecord = other.getDuration();

        if (hasCalendarUnits(leftRecord) || hasCalendarUnits(rightRecord)) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        BigInteger leftNanoseconds = leftRecord.dayTimeNanoseconds();
        BigInteger rightNanoseconds = rightRecord.dayTimeNanoseconds();
        BigInteger signedRightNanoseconds;
        if (sign < 0) {
            signedRightNanoseconds = rightNanoseconds.negate();
        } else {
            signedRightNanoseconds = rightNanoseconds;
        }
        BigInteger totalNanoseconds = leftNanoseconds.add(signedRightNanoseconds);
        if (totalNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        String receiverLargestUnit = largestUnitOfDuration(leftRecord);
        String otherLargestUnit = largestUnitOfDuration(rightRecord);
        String largestUnit = TemporalDurationConstructor.largerTemporalUnit(receiverLargestUnit, otherLargestUnit);
        TemporalDuration balanced = balanceTimeDuration(totalNanoseconds, largestUnit);
        TemporalDuration normalized = TemporalDurationConstructor.normalizeFloat64RepresentableFields(balanced);
        if (!TemporalDurationConstructor.isDurationRecordTimeRangeValid(normalized)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, normalized);
    }

    private static void applyDurationFieldOverrides(
            JSTemporalDuration duration,
            TemporalDurationFieldOverrides durationFieldOverrides) {
        duration.defineProperty(
                PropertyKey.fromString("years"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.years())));
        duration.defineProperty(
                PropertyKey.fromString("months"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.months())));
        duration.defineProperty(
                PropertyKey.fromString("weeks"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.weeks())));
        duration.defineProperty(
                PropertyKey.fromString("days"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.days())));
        duration.defineProperty(
                PropertyKey.fromString("hours"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.hours())));
        duration.defineProperty(
                PropertyKey.fromString("minutes"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.minutes())));
        duration.defineProperty(
                PropertyKey.fromString("seconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.seconds())));
        duration.defineProperty(
                PropertyKey.fromString("milliseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.milliseconds())));
        duration.defineProperty(
                PropertyKey.fromString("microseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.microseconds())));
        duration.defineProperty(
                PropertyKey.fromString("nanoseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.nanoseconds())));
    }

    static TemporalDuration balanceTimeDuration(BigInteger totalNs, String largestUnit) {
        boolean negative = totalNs.signum() < 0;
        if (negative) {
            totalNs = totalNs.negate();
        }

        long days = 0, hours = 0, minutes = 0, seconds = 0;
        long milliseconds = 0, microseconds = 0, nanoseconds = 0;

        switch (largestUnit) {
            case "day" -> {
                BigInteger[] dayDivision = totalNs.divideAndRemainder(DAY_NANOSECONDS);
                days = dayDivision[0].longValue();
                BigInteger remainingNanoseconds = dayDivision[1];
                BigInteger[] hourDivision = remainingNanoseconds.divideAndRemainder(HOUR_NANOSECONDS);
                hours = hourDivision[0].longValue();
                remainingNanoseconds = hourDivision[1];
                BigInteger[] minuteDivision = remainingNanoseconds.divideAndRemainder(MINUTE_NANOSECONDS);
                minutes = minuteDivision[0].longValue();
                remainingNanoseconds = minuteDivision[1];
                BigInteger[] secondDivision = remainingNanoseconds.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "hour" -> {
                BigInteger[] hourDivision = totalNs.divideAndRemainder(HOUR_NANOSECONDS);
                hours = hourDivision[0].longValue();
                BigInteger remainingNanoseconds = hourDivision[1];
                BigInteger[] minuteDivision = remainingNanoseconds.divideAndRemainder(MINUTE_NANOSECONDS);
                minutes = minuteDivision[0].longValue();
                remainingNanoseconds = minuteDivision[1];
                BigInteger[] secondDivision = remainingNanoseconds.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "minute" -> {
                BigInteger[] minuteDivision = totalNs.divideAndRemainder(MINUTE_NANOSECONDS);
                minutes = minuteDivision[0].longValue();
                BigInteger remainingNanoseconds = minuteDivision[1];
                BigInteger[] secondDivision = remainingNanoseconds.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "second" -> {
                BigInteger[] secondDivision = totalNs.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                BigInteger remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "millisecond" -> {
                BigInteger[] millisecondDivision = totalNs.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                BigInteger remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "microsecond" -> {
                BigInteger[] microsecondDivision = totalNs.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            default -> {
                nanoseconds = totalNs.longValue();
            }
        }

        if (negative) {
            days = -days;
            hours = -hours;
            minutes = -minutes;
            seconds = -seconds;
            milliseconds = -milliseconds;
            microseconds = -microseconds;
            nanoseconds = -nanoseconds;
        }

        return new TemporalDuration(0, 0, 0, days, hours, minutes, seconds,
                milliseconds, microseconds, nanoseconds);
    }

    private static TemporalDuration balanceZonedDurationWithoutRounding(
            JSContext context,
            TemporalDuration durationRecord,
            TemporalRelativeToOption relativeToOption) {
        LocalDateTime dayBalanceAnchorDateTime = relativeToOption.startDateTime()
                .plusYears(durationRecord.years())
                .plusMonths(durationRecord.months())
                .plusWeeks(durationRecord.weeks())
                .plusDays(durationRecord.days());
        BigInteger remainingTimeNanoseconds = durationRecord.timeNanoseconds();
        long additionalDayCount = 0L;
        while (remainingTimeNanoseconds.signum() != 0) {
            int direction = remainingTimeNanoseconds.signum() < 0 ? -1 : 1;
            LocalDateTime adjacentDayDateTime = dayBalanceAnchorDateTime.plusDays(direction);
            BigInteger oneDayNanoseconds = nanosecondsBetween(
                    context,
                    dayBalanceAnchorDateTime,
                    null,
                    adjacentDayDateTime,
                    null,
                    relativeToOption).abs();
            if (context.hasPendingException()) {
                return null;
            }
            if (oneDayNanoseconds.signum() == 0) {
                if (direction < 0) {
                    additionalDayCount--;
                } else {
                    additionalDayCount++;
                }
                dayBalanceAnchorDateTime = adjacentDayDateTime;
                continue;
            }
            if (remainingTimeNanoseconds.abs().compareTo(oneDayNanoseconds) < 0) {
                break;
            }
            if (direction < 0) {
                remainingTimeNanoseconds = remainingTimeNanoseconds.add(oneDayNanoseconds);
                additionalDayCount--;
            } else {
                remainingTimeNanoseconds = remainingTimeNanoseconds.subtract(oneDayNanoseconds);
                additionalDayCount++;
            }
            dayBalanceAnchorDateTime = adjacentDayDateTime;
        }
        TemporalDuration balancedTimeDuration = balanceTimeDuration(remainingTimeNanoseconds, "hour");
        long balancedDayCount;
        try {
            balancedDayCount = Math.addExact(durationRecord.days(), additionalDayCount);
            balancedDayCount = Math.addExact(balancedDayCount, balancedTimeDuration.days());
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new TemporalDuration(
                durationRecord.years(),
                durationRecord.months(),
                durationRecord.weeks(),
                balancedDayCount,
                balancedTimeDuration.hours(),
                balancedTimeDuration.minutes(),
                balancedTimeDuration.seconds(),
                balancedTimeDuration.milliseconds(),
                balancedTimeDuration.microseconds(),
                balancedTimeDuration.nanoseconds());
    }

    public static JSValue blank(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "blank");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return duration.getDuration().isBlank() ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static TemporalDuration buildBalancedDurationFromDateTimes(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String largestUnit,
            String smallestUnit,
            TemporalRelativeToOption relativeToOption) {
        if ("hour".equals(largestUnit)
                || "minute".equals(largestUnit)
                || "second".equals(largestUnit)
                || "millisecond".equals(largestUnit)
                || "microsecond".equals(largestUnit)
                || "nanosecond".equals(largestUnit)) {
            BigInteger totalNanoseconds = nanosecondsBetween(
                    context,
                    startDateTime,
                    null,
                    endDateTime,
                    null,
                    relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
            TemporalDuration timeDuration = balanceTimeDuration(totalNanoseconds, largestUnit);
            return new TemporalDuration(
                    0,
                    0,
                    0,
                    timeDuration.days(),
                    timeDuration.hours(),
                    timeDuration.minutes(),
                    timeDuration.seconds(),
                    timeDuration.milliseconds(),
                    timeDuration.microseconds(),
                    timeDuration.nanoseconds());
        }

        long years = 0;
        long months = 0;
        long weeks = 0;
        long days = 0;
        LocalDateTime cursorDateTime = startDateTime;
        int smallestUnitRank = temporalDurationUnitRank(smallestUnit);

        if ("year".equals(largestUnit)) {
            TemporalUnitStepResult yearStepResult = moveByWholeCalendarUnits(cursorDateTime, endDateTime, "year");
            years = yearStepResult.count();
            cursorDateTime = yearStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("year")) {
                return new TemporalDuration(years, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if ("year".equals(largestUnit) || "month".equals(largestUnit)) {
            TemporalUnitStepResult monthStepResult = moveByWholeCalendarUnits(cursorDateTime, endDateTime, "month");
            months = monthStepResult.count();
            cursorDateTime = monthStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("month")) {
                return new TemporalDuration(years, months, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        boolean shouldBalanceWeeks = "week".equals(largestUnit) || "week".equals(smallestUnit);
        if (shouldBalanceWeeks) {
            TemporalUnitStepResult weekStepResult;
            if (relativeToOption != null && relativeToOption.zoned()) {
                weekStepResult = moveByWholeCalendarUnits(
                        context,
                        cursorDateTime,
                        endDateTime,
                        "week",
                        relativeToOption);
                if (context.hasPendingException()) {
                    return null;
                }
            } else {
                weekStepResult = moveByWholeFixedUnits(cursorDateTime, endDateTime, "week");
            }
            weeks = weekStepResult.count();
            cursorDateTime = weekStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("week")) {
                return new TemporalDuration(years, months, weeks, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if (smallestUnitRank >= temporalDurationUnitRank("day")) {
            TemporalUnitStepResult dayStepResult;
            if (relativeToOption != null && relativeToOption.zoned()) {
                dayStepResult = moveByWholeCalendarUnits(
                        context,
                        cursorDateTime,
                        endDateTime,
                        "day",
                        relativeToOption);
                if (context.hasPendingException()) {
                    return null;
                }
            } else {
                dayStepResult = moveByWholeFixedUnits(cursorDateTime, endDateTime, "day");
            }
            days = dayStepResult.count();
            cursorDateTime = dayStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("day")) {
                return new TemporalDuration(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
            }
        }

        BigInteger remainingNanoseconds = nanosecondsBetween(
                context,
                cursorDateTime,
                null,
                endDateTime,
                null,
                relativeToOption);
        if (context.hasPendingException()) {
            return null;
        }
        TemporalDuration timeDuration = balanceTimeDuration(remainingNanoseconds, "hour");
        TemporalDuration durationRecord = new TemporalDuration(
                years,
                months,
                weeks,
                days,
                timeDuration.hours(),
                timeDuration.minutes(),
                timeDuration.seconds(),
                timeDuration.milliseconds(),
                timeDuration.microseconds(),
                timeDuration.nanoseconds());
        if (!durationRecord.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return null;
        }
        return durationRecord;
    }

    private static String canonicalizeDurationToStringSmallestUnit(JSContext context, String unitText) {
        String result = TemporalUnit.fromString(unitText)
                .filter(u -> !u.isLargerThan(TemporalUnit.SECOND))
                .map(TemporalUnit::jsName)
                .orElse(null);
        if (result == null) {
            context.throwRangeError("Temporal error: Invalid smallestUnit.");
        }
        return result;
    }

    private static String canonicalizeTemporalDurationUnit(JSContext context, String unitText, String optionName) {
        String result = TemporalUnit.fromString(unitText)
                .map(TemporalUnit::jsName)
                .orElse(null);
        if (result == null) {
            context.throwRangeError("Temporal error: Invalid " + optionName + ".");
        }
        return result;
    }

    private static JSTemporalDuration checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        return TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, methodName);
    }

    private static String coarserDurationUnit(String leftUnit, String rightUnit) {
        if (temporalDurationUnitRank(leftUnit) <= temporalDurationUnitRank(rightUnit)) {
            return leftUnit;
        } else {
            return rightUnit;
        }
    }

    public static JSValue days(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "days");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().days());
    }

    static TemporalDuration differencePlainDateTime(
            JSContext context,
            IsoDateTime startIsoDateTime,
            IsoDateTime endIsoDateTime,
            String largestUnit,
            String smallestUnit,
            long roundingIncrement,
            String roundingMode) {
        LocalDateTime startDateTime = startIsoDateTime.toLocalDateTime();
        LocalDateTime endDateTime = endIsoDateTime.toLocalDateTime();

        TemporalDuration unroundedDuration = buildBalancedDurationFromDateTimes(
                context,
                startDateTime,
                endDateTime,
                largestUnit,
                "nanosecond",
                null);
        if (context.hasPendingException() || unroundedDuration == null) {
            return null;
        }

        boolean requiresRounding = roundingIncrement != 1L || !"nanosecond".equals(smallestUnit);
        if (!requiresRounding) {
            return unroundedDuration;
        }

        TemporalRelativeToOption relativeToOption = new TemporalRelativeToOption(startDateTime, false, null, null, null);
        TemporalRoundOptions roundOptions = new TemporalRoundOptions(
                smallestUnit,
                largestUnit,
                roundingIncrement,
                TemporalRoundingMode.fromString(roundingMode),
                relativeToOption);
        LocalDateTime roundedEndDateTime = roundDateTimeDifference(
                context,
                startDateTime,
                endDateTime,
                null,
                roundOptions,
                relativeToOption,
                unroundedDuration);
        if (context.hasPendingException() || roundedEndDateTime == null) {
            return null;
        }

        return buildBalancedDurationFromDateTimes(
                context,
                startDateTime,
                roundedEndDateTime,
                largestUnit,
                smallestUnit,
                null);
    }

    static TemporalDuration differenceZonedDateTime(
            JSContext context,
            BigInteger startEpochNanoseconds,
            BigInteger endEpochNanoseconds,
            String timeZoneId,
            String largestUnit,
            String smallestUnit,
            long roundingIncrement,
            String roundingMode) {
        IsoDateTime startIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                startEpochNanoseconds,
                timeZoneId);
        IsoDateTime endIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                endEpochNanoseconds,
                timeZoneId);
        LocalDateTime startDateTime = startIsoDateTime.toLocalDateTime();
        LocalDateTime endDateTime = endIsoDateTime.toLocalDateTime();
        TemporalRelativeToOption relativeToOption = new TemporalRelativeToOption(
                startDateTime,
                true,
                startEpochNanoseconds,
                timeZoneId,
                null);

        TemporalDuration unroundedDuration = buildBalancedDurationFromDateTimes(
                context,
                startDateTime,
                endDateTime,
                largestUnit,
                "nanosecond",
                relativeToOption);
        if (context.hasPendingException() || unroundedDuration == null) {
            return null;
        }

        boolean requiresRounding = roundingIncrement != 1L || !"nanosecond".equals(smallestUnit);
        if (!requiresRounding) {
            return unroundedDuration;
        }

        TemporalRoundOptions roundOptions = new TemporalRoundOptions(
                smallestUnit,
                largestUnit,
                roundingIncrement,
                TemporalRoundingMode.fromString(roundingMode),
                relativeToOption);
        LocalDateTime roundedEndDateTime = roundDateTimeDifference(
                context,
                startDateTime,
                endDateTime,
                endEpochNanoseconds,
                roundOptions,
                relativeToOption,
                unroundedDuration);
        if (context.hasPendingException() || roundedEndDateTime == null) {
            return null;
        }

        return buildBalancedDurationFromDateTimes(
                context,
                startDateTime,
                roundedEndDateTime,
                largestUnit,
                smallestUnit,
                relativeToOption);
    }

    private static double divideBigIntegersToDouble(BigInteger numerator, BigInteger divisor) {
        if (numerator.signum() == 0) {
            return 0D;
        }
        BigInteger normalizedNumerator = numerator;
        BigInteger normalizedDivisor = divisor;
        if (normalizedDivisor.signum() < 0) {
            normalizedNumerator = normalizedNumerator.negate();
            normalizedDivisor = normalizedDivisor.negate();
        }
        if (normalizedDivisor.equals(BigInteger.ONE)) {
            return normalizedNumerator.doubleValue();
        }
        BigDecimal quotient = new BigDecimal(normalizedNumerator)
                .divide(new BigDecimal(normalizedDivisor), java.math.MathContext.DECIMAL128);
        return quotient.doubleValue();
    }

    private static long estimateCalendarUnitCount(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit) {
        long dayDifference = endDateTime.toLocalDate().toEpochDay() - startDateTime.toLocalDate().toEpochDay();
        if ("day".equals(calendarUnit)) {
            return dayDifference;
        }
        if ("week".equals(calendarUnit)) {
            return dayDifference / 7L;
        }
        long yearDifference = (long) endDateTime.getYear() - startDateTime.getYear();
        if ("year".equals(calendarUnit)) {
            return yearDifference;
        }
        long monthDifference = (long) endDateTime.getMonthValue() - startDateTime.getMonthValue();
        return yearDifference * 12L + monthDifference;
    }

    private static String formatDurationStringWithPrecision(
            TemporalDuration durationRecord,
            TemporalDurationToStringOptions options) {
        if (options.precisionAuto()) {
            return durationRecord.toString();
        }

        boolean negative = durationRecord.sign() < 0;
        BigInteger years = BigInteger.valueOf(durationRecord.years()).abs();
        BigInteger months = BigInteger.valueOf(durationRecord.months()).abs();
        BigInteger weeks = BigInteger.valueOf(durationRecord.weeks()).abs();
        BigInteger days = BigInteger.valueOf(durationRecord.days()).abs();
        BigInteger hours = BigInteger.valueOf(durationRecord.hours()).abs();
        BigInteger minutes = BigInteger.valueOf(durationRecord.minutes()).abs();
        BigInteger seconds = BigInteger.valueOf(durationRecord.seconds()).abs();
        BigInteger milliseconds = BigInteger.valueOf(durationRecord.milliseconds()).abs();
        BigInteger microseconds = BigInteger.valueOf(durationRecord.microseconds()).abs();
        BigInteger nanoseconds = BigInteger.valueOf(durationRecord.nanoseconds()).abs();

        BigInteger totalSubsecondNanoseconds = milliseconds.multiply(MILLISECOND_NANOSECONDS)
                .add(microseconds.multiply(MICROSECOND_NANOSECONDS))
                .add(nanoseconds);
        BigInteger[] secondCarryAndRemainder = totalSubsecondNanoseconds.divideAndRemainder(SECOND_NANOSECONDS);
        BigInteger secondsWithCarry = seconds.add(secondCarryAndRemainder[0]);
        BigInteger subsecondNanosecondsRemainder = secondCarryAndRemainder[1];

        StringBuilder stringBuilder = new StringBuilder();
        if (negative) {
            stringBuilder.append('-');
        }
        stringBuilder.append('P');
        if (years.signum() != 0) {
            stringBuilder.append(years).append('Y');
        }
        if (months.signum() != 0) {
            stringBuilder.append(months).append('M');
        }
        if (weeks.signum() != 0) {
            stringBuilder.append(weeks).append('W');
        }
        if (days.signum() != 0) {
            stringBuilder.append(days).append('D');
        }

        stringBuilder.append('T');
        if (hours.signum() != 0) {
            stringBuilder.append(hours).append('H');
        }
        if (minutes.signum() != 0) {
            stringBuilder.append(minutes).append('M');
        }
        stringBuilder.append(secondsWithCarry);
        if (options.fractionalSecondDigits() > 0) {
            String fractionalPart = String.format(
                    Locale.ROOT,
                    "%09d",
                    subsecondNanosecondsRemainder.intValue());
            stringBuilder.append('.').append(fractionalPart, 0, options.fractionalSecondDigits());
        }
        stringBuilder.append('S');
        return stringBuilder.toString();
    }

    private static int fractionalSecondDigitsFromSmallestUnit(String smallestUnit) {
        return switch (smallestUnit) {
            case "second" -> 0;
            case "millisecond" -> 3;
            case "microsecond" -> 6;
            default -> 9;
        };
    }

    private static boolean hasAnyDateUnits(TemporalDuration durationRecord) {
        return durationRecord.years() != 0
                || durationRecord.months() != 0
                || durationRecord.weeks() != 0
                || durationRecord.days() != 0;
    }

    private static boolean hasCalendarUnits(TemporalDuration durationRecord) {
        return durationRecord.years() != 0 || durationRecord.months() != 0 || durationRecord.weeks() != 0;
    }

    private static boolean hasTimeUnits(TemporalDuration durationRecord) {
        return durationRecord.hours() != 0
                || durationRecord.minutes() != 0
                || durationRecord.seconds() != 0
                || durationRecord.milliseconds() != 0
                || durationRecord.microseconds() != 0
                || durationRecord.nanoseconds() != 0;
    }

    public static JSValue hours(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "hours");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().hours());
    }

    private static boolean isCalendarUnit(String unit) {
        return TemporalUnit.fromString(unit)
                .map(TemporalUnit::isDateUnit)
                .orElse(false);
    }

    private static boolean isDateTimeWithinTemporalRange(LocalDateTime dateTime) {
        LocalDateTime minimumDateTime = LocalDateTime.of(-271821, 4, 20, 0, 0, 0, 0);
        LocalDateTime maximumDateTime = LocalDateTime.of(275760, 9, 13, 23, 59, 59, 999_999_999);
        return !dateTime.isBefore(minimumDateTime) && !dateTime.isAfter(maximumDateTime);
    }

    private static boolean isDateWithinTemporalRange(LocalDate date) {
        LocalDate minimumDate = LocalDate.of(-271821, 4, 19);
        LocalDate maximumDate = LocalDate.of(275760, 9, 13);
        return !date.isBefore(minimumDate) && !date.isAfter(maximumDate);
    }

    private static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return false;
        }
        char signCharacter = timeZoneId.charAt(0);
        return signCharacter == '+' || signCharacter == '-' || signCharacter == '\u2212';
    }

    private static boolean isRelativeToRequiredUnit(String unit) {
        return TemporalUnit.fromString(unit)
                .map(TemporalUnit::requiresRelativeTo)
                .orElse(false);
    }

    private static boolean isTimeUnit(String unit) {
        return TemporalUnit.fromString(unit)
                .map(TemporalUnit::isTimeUnit)
                .orElse(false);
    }

    private static boolean isValidIncrementForUnit(String smallestUnit, long roundingIncrement) {
        return TemporalUnit.fromString(smallestUnit)
                .filter(TemporalUnit::isTimeUnit)
                .map(parsedUnit -> {
                    long maximumIncrement = switch (parsedUnit) {
                        case HOUR -> 24L;
                        case MINUTE, SECOND -> 60L;
                        case MILLISECOND, MICROSECOND, NANOSECOND -> 1_000L;
                        default -> -1L;
                    };
                    return maximumIncrement <= 0 || (roundingIncrement < maximumIncrement && maximumIncrement % roundingIncrement == 0);
                })
                .orElse(true);
    }

    private static String largestDayTimeUnit(TemporalDuration durationRecord) {
        if (durationRecord.days() != 0) {
            return "day";
        } else if (durationRecord.hours() != 0) {
            return "hour";
        } else if (durationRecord.minutes() != 0) {
            return "minute";
        } else if (durationRecord.seconds() != 0) {
            return "second";
        } else if (durationRecord.milliseconds() != 0) {
            return "millisecond";
        } else if (durationRecord.microseconds() != 0) {
            return "microsecond";
        } else if (durationRecord.nanoseconds() != 0) {
            return "nanosecond";
        } else {
            return "second";
        }
    }

    private static String largestUnitOfDuration(TemporalDuration durationRecord) {
        if (durationRecord.years() != 0) {
            return "year";
        }
        if (durationRecord.months() != 0) {
            return "month";
        }
        if (durationRecord.weeks() != 0) {
            return "week";
        }
        if (durationRecord.days() != 0) {
            return TemporalDurationConstructor.UNIT_DAY;
        }
        if (durationRecord.hours() != 0) {
            return TemporalDurationConstructor.UNIT_HOUR;
        }
        if (durationRecord.minutes() != 0) {
            return TemporalDurationConstructor.UNIT_MINUTE;
        }
        if (durationRecord.seconds() != 0) {
            return TemporalDurationConstructor.UNIT_SECOND;
        }
        if (durationRecord.milliseconds() != 0) {
            return TemporalDurationConstructor.UNIT_MILLISECOND;
        }
        if (durationRecord.microseconds() != 0) {
            return TemporalDurationConstructor.UNIT_MICROSECOND;
        }
        return TemporalDurationConstructor.UNIT_NANOSECOND;
    }

    public static JSValue microseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "microseconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().microseconds());
    }

    public static JSValue milliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "milliseconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().milliseconds());
    }

    public static JSValue minutes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "minutes");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().minutes());
    }

    public static JSValue months(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "months");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().months());
    }

    private static TemporalUnitStepResult moveByWholeCalendarUnits(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit) {
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime, calendarUnit);
        LocalDateTime boundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, unitCount);
        if (!endDateTime.isBefore(startDateTime)) {
            while (boundaryDateTime.isAfter(endDateTime)) {
                unitCount--;
                boundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount + 1L;
                LocalDateTime nextDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, nextUnitCount);
                if (nextDateTime.isAfter(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        } else {
            while (boundaryDateTime.isBefore(endDateTime)) {
                unitCount++;
                boundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount - 1L;
                LocalDateTime nextDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, nextUnitCount);
                if (nextDateTime.isBefore(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        }
        return new TemporalUnitStepResult(unitCount, boundaryDateTime);
    }

    private static TemporalUnitStepResult moveByWholeCalendarUnits(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit,
            TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return moveByWholeCalendarUnits(startDateTime, endDateTime, calendarUnit);
        }
        BigInteger startToEndNanoseconds = nanosecondsBetween(
                context,
                startDateTime,
                null,
                endDateTime,
                null,
                relativeToOption);
        if (context.hasPendingException()) {
            return new TemporalUnitStepResult(0L, startDateTime);
        }

        int direction = startToEndNanoseconds.signum();
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime, calendarUnit);
        LocalDateTime boundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, unitCount);
        if (direction >= 0) {
            while (true) {
                BigInteger boundaryToEndNanoseconds = nanosecondsBetween(
                        context,
                        boundaryDateTime,
                        null,
                        endDateTime,
                        null,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new TemporalUnitStepResult(unitCount, boundaryDateTime);
                }
                if (boundaryToEndNanoseconds.signum() >= 0) {
                    break;
                }
                unitCount--;
                boundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount + 1L;
                LocalDateTime nextDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, nextUnitCount);
                BigInteger nextToEndNanoseconds = nanosecondsBetween(
                        context,
                        nextDateTime,
                        null,
                        endDateTime,
                        null,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new TemporalUnitStepResult(unitCount, boundaryDateTime);
                }
                if (nextToEndNanoseconds.signum() < 0) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        } else {
            while (true) {
                BigInteger boundaryToEndNanoseconds = nanosecondsBetween(
                        context,
                        boundaryDateTime,
                        null,
                        endDateTime,
                        null,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new TemporalUnitStepResult(unitCount, boundaryDateTime);
                }
                if (boundaryToEndNanoseconds.signum() < 0
                        || (boundaryToEndNanoseconds.signum() == 0 && boundaryDateTime.equals(endDateTime))) {
                    break;
                }
                unitCount++;
                boundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount - 1L;
                LocalDateTime nextDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, nextUnitCount);
                BigInteger nextToEndNanoseconds = nanosecondsBetween(
                        context,
                        nextDateTime,
                        null,
                        endDateTime,
                        null,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new TemporalUnitStepResult(unitCount, boundaryDateTime);
                }
                if (nextToEndNanoseconds.signum() > 0
                        || (nextToEndNanoseconds.signum() == 0 && !nextDateTime.equals(endDateTime))) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        }
        return new TemporalUnitStepResult(unitCount, boundaryDateTime);
    }

    private static TemporalUnitStepResult moveByWholeFixedUnits(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String unit) {
        BigInteger unitNanoseconds = unitToNanosecondsBigInteger(unit);
        if (unitNanoseconds.signum() == 0) {
            return new TemporalUnitStepResult(0, startDateTime);
        }
        BigInteger deltaNanoseconds = nanosecondsBetween(startDateTime, endDateTime);
        long unitCount = deltaNanoseconds.divide(unitNanoseconds).longValue();
        LocalDateTime boundaryDateTime =
                IsoDateTime.createFromLocalDateTime(startDateTime).addFixedUnitsToLocalDateTime(unit, unitCount);
        return new TemporalUnitStepResult(unitCount, boundaryDateTime);
    }

    public static JSValue nanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "nanoseconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().nanoseconds());
    }

    private static BigInteger nanosecondsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigInteger startSeconds = BigInteger.valueOf(startDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger endSeconds = BigInteger.valueOf(endDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger secondDifference = endSeconds.subtract(startSeconds).multiply(SECOND_NANOSECONDS);
        long nanosecondDifference = endDateTime.getNano() - startDateTime.getNano();
        return secondDifference.add(BigInteger.valueOf(nanosecondDifference));
    }

    private static BigInteger nanosecondsBetween(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger knownStartEpochNanoseconds,
            LocalDateTime endDateTime,
            BigInteger knownEndEpochNanoseconds,
            TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return nanosecondsBetween(startDateTime, endDateTime);
        }
        BigInteger startEpochNanoseconds;
        if (knownStartEpochNanoseconds != null) {
            startEpochNanoseconds = knownStartEpochNanoseconds;
        } else if (startDateTime.equals(relativeToOption.startDateTime())) {
            startEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            startEpochNanoseconds =
                    IsoDateTime.zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, startDateTime);
        }
        if (context.hasPendingException() || startEpochNanoseconds == null) {
            return BigInteger.ZERO;
        }
        BigInteger endEpochNanoseconds;
        if (knownEndEpochNanoseconds != null) {
            endEpochNanoseconds = knownEndEpochNanoseconds;
        } else if (endDateTime.equals(relativeToOption.startDateTime())) {
            endEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            endEpochNanoseconds =
                    IsoDateTime.zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, endDateTime);
        }
        if (context.hasPendingException() || endEpochNanoseconds == null) {
            return BigInteger.ZERO;
        }
        return endEpochNanoseconds.subtract(startEpochNanoseconds);
    }

    public static JSValue negated(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "negated");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, duration.getDuration().negated());
    }

    private static TemporalDurationToStringOptions parseDurationToStringOptions(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalOptionResolver.toOptionalOptionsObject(
                context,
                optionsValue,
                "Temporal error: Options must be an object.");
        if (context.hasPendingException()) {
            return null;
        }
        if (optionsObject == null) {
            return TemporalDurationToStringOptions.DEFAULT;
        }

        JSValue fractionalSecondDigitsValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
        if (context.hasPendingException()) {
            return null;
        }
        TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption =
                TemporalFractionalSecondDigitsOption.parse(
                        context,
                        fractionalSecondDigitsValue,
                        "Temporal error: Invalid fractionalSecondDigits.");
        if (context.hasPendingException() || fractionalSecondDigitsOption == null) {
            return null;
        }

        String roundingModeText = TemporalOptionResolver.getStringOption(context, optionsObject, "roundingMode", "trunc");
        if (context.hasPendingException() || roundingModeText == null) {
            return null;
        }

        String smallestUnitText = TemporalOptionResolver.getStringOption(context, optionsObject, "smallestUnit", null);
        if (context.hasPendingException()) {
            return null;
        }

        TemporalRoundingMode roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        String smallestUnit = null;
        if (smallestUnitText != null) {
            smallestUnit = canonicalizeDurationToStringSmallestUnit(context, smallestUnitText);
            if (context.hasPendingException()) {
                return null;
            }
        }

        boolean precisionAuto;
        int fractionalSecondDigits;
        long roundingIncrementNanoseconds;
        if (smallestUnit != null) {
            precisionAuto = false;
            fractionalSecondDigits = fractionalSecondDigitsFromSmallestUnit(smallestUnit);
            roundingIncrementNanoseconds = roundingIncrementNanosecondsForSmallestUnit(smallestUnit);
        } else if (fractionalSecondDigitsOption.auto()) {
            precisionAuto = true;
            fractionalSecondDigits = -1;
            smallestUnit = "nanosecond";
            roundingIncrementNanoseconds = 1L;
        } else {
            precisionAuto = false;
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
            smallestUnit = smallestUnitFromFractionalSecondDigits(fractionalSecondDigits);
            roundingIncrementNanoseconds = fractionalSecondDigitsOption.roundingIncrementNanoseconds();
        }

        return new TemporalDurationToStringOptions(
                smallestUnit,
                roundingMode,
                roundingIncrementNanoseconds,
                precisionAuto,
                fractionalSecondDigits);
    }

    private static int parseOffsetSecondsFromTimeZoneId(String timeZoneId) {
        String normalizedTimeZoneId = timeZoneId.replace('\u2212', '-');
        int sign = normalizedTimeZoneId.charAt(0) == '-' ? -1 : 1;
        int hours;
        int minutes;
        if (normalizedTimeZoneId.length() == 6 && normalizedTimeZoneId.charAt(3) == ':') {
            hours = Integer.parseInt(normalizedTimeZoneId.substring(1, 3));
            minutes = Integer.parseInt(normalizedTimeZoneId.substring(4, 6));
        } else if (normalizedTimeZoneId.length() == 5) {
            hours = Integer.parseInt(normalizedTimeZoneId.substring(1, 3));
            minutes = Integer.parseInt(normalizedTimeZoneId.substring(3, 5));
        } else {
            throw new IllegalArgumentException("Invalid offset time zone identifier: " + timeZoneId);
        }
        return sign * (hours * 3600 + minutes * 60);
    }

    private static TemporalRoundOptions parseRoundOptions(JSContext context, JSValue roundToArg, TemporalDuration durationRecord) {
        String largestUnitText = null;
        String smallestUnitText = null;
        TemporalRelativeToOption relativeToOption = null;
        boolean largestUnitProvided = false;
        long roundingIncrement = 1L;
        String roundingMode = "halfExpand";

        if (roundToArg instanceof JSString smallestUnitStringValue) {
            smallestUnitText = smallestUnitStringValue.value();
        } else if (roundToArg instanceof JSObject optionsObject) {
            largestUnitText = TemporalOptionResolver.getStringOption(context, optionsObject, "largestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
            if (largestUnitText != null) {
                largestUnitProvided = true;
            }

            JSValue relativeToValue = optionsObject.get(PropertyKey.fromString("relativeTo"));
            if (context.hasPendingException()) {
                return null;
            }
            relativeToOption = parseRoundRelativeToOption(context, relativeToValue);
            if (context.hasPendingException()) {
                return null;
            }

            roundingIncrement = TemporalOptionResolver.getRoundingIncrementOption(
                    context,
                    optionsObject,
                    "roundingIncrement",
                    1L,
                    1L,
                    1_000_000_000L,
                    "Temporal error: Invalid rounding increment.");
            if (context.hasPendingException()) {
                return null;
            }

            roundingMode = TemporalOptionResolver.getStringOption(context, optionsObject, "roundingMode", "halfExpand");
            if (context.hasPendingException() || roundingMode == null) {
                return null;
            }

            smallestUnitText = TemporalOptionResolver.getStringOption(context, optionsObject, "smallestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return null;
        }

        String canonicalLargestUnit = null;
        if (largestUnitText != null) {
            if (!"auto".equals(largestUnitText)) {
                canonicalLargestUnit = canonicalizeTemporalDurationUnit(context, largestUnitText, "largestUnit");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }

        String canonicalSmallestUnit = null;
        if (smallestUnitText != null) {
            canonicalSmallestUnit = canonicalizeTemporalDurationUnit(context, smallestUnitText, "smallestUnit");
            if (context.hasPendingException()) {
                return null;
            }
        }

        if (canonicalLargestUnit == null && canonicalSmallestUnit == null && !largestUnitProvided) {
            context.throwRangeError("Temporal error: Must specify either smallestUnit or largestUnit.");
            return null;
        }

        if (canonicalSmallestUnit == null) {
            canonicalSmallestUnit = "nanosecond";
        }
        if (canonicalLargestUnit == null) {
            String durationLargestUnit = largestUnitOfDuration(durationRecord);
            canonicalLargestUnit = coarserDurationUnit(canonicalSmallestUnit, durationLargestUnit);
        }

        if (temporalDurationUnitRank(canonicalSmallestUnit) < temporalDurationUnitRank(canonicalLargestUnit)) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        if (!TemporalRoundingMode.isValid(roundingMode)) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        if (!isValidIncrementForUnit(canonicalSmallestUnit, roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        if (roundingIncrement != 1
                && isCalendarUnit(canonicalSmallestUnit)
                && temporalDurationUnitRank(canonicalLargestUnit) < temporalDurationUnitRank(canonicalSmallestUnit)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        return new TemporalRoundOptions(
                canonicalSmallestUnit,
                canonicalLargestUnit,
                roundingIncrement,
                TemporalRoundingMode.fromString(roundingMode),
                relativeToOption);
    }

    private static TemporalRelativeToOption parseRoundRelativeToOption(JSContext context, JSValue relativeToValue) {
        if (relativeToValue instanceof JSUndefined || relativeToValue == null) {
            return null;
        }
        TemporalDurationConstructor.RelativeToReference relativeToReference =
                TemporalDurationConstructor.parseRelativeToValue(context, relativeToValue);
        if (context.hasPendingException() || relativeToReference == null) {
            return null;
        }
        if (relativeToReference.epochNanoseconds() == null || relativeToReference.timeZoneId() == null) {
            LocalDateTime startDateTime = relativeToReference.relativeDate()
                    .atTime(relativeToReference.relativeTime())
                    .toLocalDateTime();
            return new TemporalRelativeToOption(startDateTime, false, null, null, null);
        }
        IsoDateTime localIsoDateTime;
        if (isOffsetTimeZoneIdentifier(relativeToReference.timeZoneId())) {
            int offsetSeconds = parseOffsetSecondsFromTimeZoneId(relativeToReference.timeZoneId());
            IsoDateTime utcDateTime = IsoDateTime.createByEpochNs(relativeToReference.epochNanoseconds());
            LocalDateTime utcLocalDateTime = utcDateTime.toLocalDateTime();
            LocalDateTime offsetLocalDateTime = utcLocalDateTime.plusSeconds(offsetSeconds);
            localIsoDateTime = IsoDateTime.createFromLocalDateTime(offsetLocalDateTime);
        } else {
            localIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                    relativeToReference.epochNanoseconds(),
                    relativeToReference.timeZoneId());
        }
        LocalDateTime startDateTime = localIsoDateTime.toLocalDateTime();
        return new TemporalRelativeToOption(
                startDateTime,
                true,
                relativeToReference.epochNanoseconds(),
                relativeToReference.timeZoneId(),
                relativeToReference.offsetSeconds());
    }

    private static TemporalPartialDurationFieldValue readPartialDurationField(JSContext context, JSObject fields, String fieldKey) {
        JSValue value = fields.get(PropertyKey.fromString(fieldKey));
        if (context.hasPendingException()) {
            return null;
        }
        if (value instanceof JSUndefined || value == null) {
            return new TemporalPartialDurationFieldValue(false, 0L);
        }
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        if (!Double.isFinite(numericValue) || numericValue != Math.floor(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return null;
        }
        if (numericValue < Long.MIN_VALUE || numericValue > Long.MAX_VALUE) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new TemporalPartialDurationFieldValue(true, (long) numericValue);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "round");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        TemporalRoundOptions roundOptions = parseRoundOptions(context, args[0], duration.getDuration());
        if (context.hasPendingException() || roundOptions == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalRelativeToOption relativeToOption = roundOptions.relativeToOption();

        TemporalDuration durationRecord = duration.getDuration();
        boolean requiresRelativeTo =
                hasCalendarUnits(durationRecord)
                        || isRelativeToRequiredUnit(roundOptions.smallestUnit())
                        || isRelativeToRequiredUnit(roundOptions.largestUnit());
        if (requiresRelativeTo && relativeToOption == null) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        TemporalRoundComputationResult roundComputationResult;
        if (relativeToOption == null) {
            roundComputationResult = roundWithoutRelativeTo(context, durationRecord, roundOptions);
        } else {
            TemporalDuration roundedDurationRecord =
                    roundWithRelativeTo(context, durationRecord, roundOptions, relativeToOption);
            roundComputationResult = new TemporalRoundComputationResult(roundedDurationRecord, null);
        }
        if (context.hasPendingException() || roundComputationResult == null || roundComputationResult.durationRecord() == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration normalizedDurationRecord =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(roundComputationResult.durationRecord());
        if (!normalizedDurationRecord.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(normalizedDurationRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        JSTemporalDuration roundedDuration = TemporalDurationConstructor.createDuration(context, normalizedDurationRecord);
        TemporalDurationFieldOverrides durationFieldOverrides = roundComputationResult.durationFieldOverrides();
        if (durationFieldOverrides != null) {
            applyDurationFieldOverrides(roundedDuration, durationFieldOverrides);
        }
        return roundedDuration;
    }

    private static LocalDateTime roundDateTimeDifference(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime unroundedEndDateTime,
            BigInteger unroundedEndEpochNanoseconds,
            TemporalRoundOptions roundOptions,
            TemporalRelativeToOption relativeToOption,
            TemporalDuration durationRecord) {
        String smallestUnit = roundOptions.smallestUnit();
        long roundingIncrement = roundOptions.roundingIncrement();
        TemporalRoundingMode roundingMode = roundOptions.roundingMode();

        if ("year".equals(smallestUnit) || "month".equals(smallestUnit)) {
            return roundDateTimeToCalendarUnit(
                    context,
                    startDateTime,
                    unroundedEndDateTime,
                    unroundedEndEpochNanoseconds,
                    smallestUnit,
                    roundingIncrement,
                    roundingMode.jsName(),
                    relativeToOption);
        }

        if ("week".equals(smallestUnit)
                && ("year".equals(roundOptions.largestUnit())
                || "month".equals(roundOptions.largestUnit())
                || "week".equals(roundOptions.largestUnit()))) {
            LocalDateTime weekAnchorDateTime = startDateTime;
            if ("year".equals(roundOptions.largestUnit())) {
                TemporalUnitStepResult yearStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "year");
                weekAnchorDateTime = yearStepResult.boundaryDateTime();
                TemporalUnitStepResult monthStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "month");
                weekAnchorDateTime = monthStepResult.boundaryDateTime();
            } else if ("month".equals(roundOptions.largestUnit())) {
                TemporalUnitStepResult monthStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "month");
                weekAnchorDateTime = monthStepResult.boundaryDateTime();
            }
            BigInteger weekRemainderNanoseconds = nanosecondsBetween(
                    context,
                    weekAnchorDateTime,
                    null,
                    unroundedEndDateTime,
                    unroundedEndEpochNanoseconds,
                    relativeToOption);
            BigInteger weekIncrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
            BigInteger roundedWeekRemainderNanoseconds =
                    TemporalMathKernel.roundBigIntegerToIncrementSigned(weekRemainderNanoseconds, weekIncrementNanoseconds, roundingMode);
            return IsoDateTime.createFromLocalDateTime(weekAnchorDateTime).addNanosecondsToDateTime(
                    context,
                    roundedWeekRemainderNanoseconds,
                    relativeToOption);
        }

        if (relativeToOption.zoned() && "day".equals(roundOptions.largestUnit()) && isTimeUnit(smallestUnit)) {
            BigInteger nextDayEpochNanoseconds = relativeToOption.epochNanoseconds().add(DAY_NANOSECONDS);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(nextDayEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        if (relativeToOption.zoned()
                && isTimeUnit(smallestUnit)
                && !hasAnyDateUnits(durationRecord)
                && !isTimeUnit(roundOptions.largestUnit())) {
            TemporalDuration balancedDurationWithoutRounding =
                    balanceZonedDurationWithoutRounding(context, durationRecord, relativeToOption);
            if (context.hasPendingException() || balancedDurationWithoutRounding == null) {
                return null;
            }
            int durationSign = balancedDurationWithoutRounding.sign();
            if (durationSign == 0) {
                durationSign = 1;
            }
            LocalDateTime startAnchorDateTime;
            try {
                startAnchorDateTime = startDateTime.plusDays(balancedDurationWithoutRounding.days());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            LocalDateTime endAnchorDateTime = startAnchorDateTime.plusDays(durationSign);
            BigInteger daySpanNanoseconds = nanosecondsBetween(
                    context,
                    startAnchorDateTime,
                    null,
                    endAnchorDateTime,
                    null,
                    relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
            BigInteger roundedTimeNanoseconds = TemporalMathKernel.roundBigIntegerToIncrementSigned(
                    balancedDurationWithoutRounding.timeNanoseconds(),
                    unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement)),
                    roundingMode);
            BigInteger beyondDaySpanNanoseconds = roundedTimeNanoseconds.subtract(daySpanNanoseconds);
            boolean roundedBeyondOneDay = beyondDaySpanNanoseconds.signum() != -durationSign;
            if (roundedBeyondOneDay) {
                roundedTimeNanoseconds = TemporalMathKernel.roundBigIntegerToIncrementSigned(
                        beyondDaySpanNanoseconds,
                        unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement)),
                        roundingMode);
                return IsoDateTime.createFromLocalDateTime(endAnchorDateTime).addNanosecondsToDateTime(
                        context,
                        roundedTimeNanoseconds,
                        relativeToOption);
            }
            return IsoDateTime.createFromLocalDateTime(startAnchorDateTime).addNanosecondsToDateTime(
                    context,
                    roundedTimeNanoseconds,
                    relativeToOption);
        }

        if (relativeToOption.zoned()
                && isTimeUnit(smallestUnit)
                && hasAnyDateUnits(durationRecord)
                && !isTimeUnit(roundOptions.largestUnit())) {
            LocalDateTime dayAdjustedDateTime;
            try {
                dayAdjustedDateTime = startDateTime
                        .plusYears(durationRecord.years())
                        .plusMonths(durationRecord.months())
                        .plusWeeks(durationRecord.weeks())
                        .plusDays(durationRecord.days());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger timeRemainderNanoseconds = nanosecondsBetween(
                    context,
                    dayAdjustedDateTime,
                    null,
                    unroundedEndDateTime,
                    unroundedEndEpochNanoseconds,
                    relativeToOption);
            BigInteger incrementNanoseconds = unitToNanosecondsBigInteger(smallestUnit)
                    .multiply(BigInteger.valueOf(roundingIncrement));
            BigInteger roundedTimeRemainderNanoseconds =
                    TemporalMathKernel.roundBigIntegerToIncrementSigned(timeRemainderNanoseconds, incrementNanoseconds, roundingMode);
            return IsoDateTime.createFromLocalDateTime(dayAdjustedDateTime).addNanosecondsToDateTime(
                    context,
                    roundedTimeRemainderNanoseconds,
                    relativeToOption);
        }

        BigInteger totalNanoseconds = nanosecondsBetween(
                context,
                startDateTime,
                null,
                unroundedEndDateTime,
                unroundedEndEpochNanoseconds,
                relativeToOption);
        BigInteger incrementNanoseconds;
        if (relativeToOption.zoned() && "day".equals(smallestUnit)) {
            return roundDateTimeToCalendarUnit(
                    context,
                    startDateTime,
                    unroundedEndDateTime,
                    unroundedEndEpochNanoseconds,
                    "day",
                    roundingIncrement,
                    roundingMode.jsName(),
                    relativeToOption);
        } else if ("week".equals(smallestUnit)) {
            incrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
        } else {
            incrementNanoseconds = unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement));
        }
        BigInteger roundedNanoseconds = TemporalMathKernel.roundBigIntegerToIncrementSigned(totalNanoseconds, incrementNanoseconds, roundingMode);
        return IsoDateTime.createFromLocalDateTime(startDateTime).addNanosecondsToDateTime(
                context,
                roundedNanoseconds,
                relativeToOption);
    }

    private static LocalDateTime roundDateTimeToCalendarUnit(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            BigInteger knownEndEpochNanoseconds,
            String calendarUnit,
            long roundingIncrement,
            String roundingMode,
            TemporalRelativeToOption relativeToOption) {
        TemporalUnitStepResult truncatedStepResult = moveByWholeCalendarUnits(startDateTime, endDateTime, calendarUnit);
        if (relativeToOption != null && relativeToOption.zoned()) {
            truncatedStepResult = moveByWholeCalendarUnits(
                    context,
                    startDateTime,
                    endDateTime,
                    calendarUnit,
                    relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
        }
        long truncatedCount = truncatedStepResult.count();
        LocalDateTime truncatedBoundaryDateTime = truncatedStepResult.boundaryDateTime();

        int boundaryToEndDirection;
        if (relativeToOption != null && relativeToOption.zoned()) {
            BigInteger boundaryToEndNanoseconds = nanosecondsBetween(
                    context,
                    truncatedBoundaryDateTime,
                    null,
                    endDateTime,
                    knownEndEpochNanoseconds,
                    relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
            boundaryToEndDirection = boundaryToEndNanoseconds.signum();
        } else if (truncatedBoundaryDateTime.isBefore(endDateTime)) {
            boundaryToEndDirection = 1;
        } else if (truncatedBoundaryDateTime.isAfter(endDateTime)) {
            boundaryToEndDirection = -1;
        } else {
            boundaryToEndDirection = 0;
        }

        long lowerUnitCount;
        long upperUnitCount;
        if (boundaryToEndDirection < 0) {
            lowerUnitCount = truncatedCount - 1;
            upperUnitCount = truncatedCount;
        } else if (boundaryToEndDirection > 0) {
            lowerUnitCount = truncatedCount;
            upperUnitCount = truncatedCount + 1;
        } else {
            lowerUnitCount = truncatedCount;
            upperUnitCount = truncatedCount;
        }

        long lowerMultipleCount = Math.floorDiv(lowerUnitCount, roundingIncrement) * roundingIncrement;
        long upperMultipleCount = lowerMultipleCount + roundingIncrement;

        LocalDateTime lowerBoundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, lowerMultipleCount);
        LocalDateTime upperBoundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, upperMultipleCount);
        if (!isDateWithinTemporalRange(lowerBoundaryDateTime.toLocalDate())
                || !isDateWithinTemporalRange(upperBoundaryDateTime.toLocalDate())) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        if (endDateTime.equals(lowerBoundaryDateTime)) {
            return lowerBoundaryDateTime;
        }
        if (endDateTime.equals(upperBoundaryDateTime)) {
            return upperBoundaryDateTime;
        }

        int direction;
        if (relativeToOption != null && relativeToOption.zoned()) {
            BigInteger startToEndNanoseconds = nanosecondsBetween(
                    context,
                    startDateTime,
                    null,
                    endDateTime,
                    knownEndEpochNanoseconds,
                    relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
            direction = startToEndNanoseconds.signum();
            if (direction == 0) {
                direction = 1;
            }
        } else if (endDateTime.isBefore(startDateTime)) {
            direction = -1;
        } else {
            direction = 1;
        }
        switch (roundingMode) {
            case "ceil":
                return upperBoundaryDateTime;
            case "floor":
                return lowerBoundaryDateTime;
            case "trunc":
                if (direction < 0) {
                    return upperBoundaryDateTime;
                } else {
                    return lowerBoundaryDateTime;
                }
            case "expand":
                if (direction < 0) {
                    return lowerBoundaryDateTime;
                } else {
                    return upperBoundaryDateTime;
                }
            case "halfEven":
            case "halfExpand":
            case "halfTrunc":
            case "halfCeil":
            case "halfFloor":
                BigInteger lowerDistanceNanoseconds = nanosecondsBetween(
                        context,
                        lowerBoundaryDateTime,
                        null,
                        endDateTime,
                        null,
                        relativeToOption).abs();
                BigInteger upperDistanceNanoseconds = nanosecondsBetween(
                        context,
                        endDateTime,
                        null,
                        upperBoundaryDateTime,
                        null,
                        relativeToOption).abs();
                int distanceComparison = lowerDistanceNanoseconds.compareTo(upperDistanceNanoseconds);
                if (distanceComparison < 0) {
                    return lowerBoundaryDateTime;
                }
                if (distanceComparison > 0) {
                    return upperBoundaryDateTime;
                }
                return switch (roundingMode) {
                    case "halfEven" -> {
                        if (Math.floorMod(lowerMultipleCount / roundingIncrement, 2L) == 0L) {
                            yield lowerBoundaryDateTime;
                        } else {
                            yield upperBoundaryDateTime;
                        }
                    }
                    case "halfExpand" -> {
                        if (direction < 0) {
                            yield lowerBoundaryDateTime;
                        } else {
                            yield upperBoundaryDateTime;
                        }
                    }
                    case "halfTrunc" -> {
                        if (direction < 0) {
                            yield upperBoundaryDateTime;
                        } else {
                            yield lowerBoundaryDateTime;
                        }
                    }
                    case "halfCeil" -> upperBoundaryDateTime;
                    case "halfFloor" -> lowerBoundaryDateTime;
                    default -> lowerBoundaryDateTime;
                };
            default:
                return upperBoundaryDateTime;
        }
    }

    private static TemporalDuration roundWithRelativeTo(
            JSContext context,
            TemporalDuration durationRecord,
            TemporalRoundOptions roundOptions,
            TemporalRelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        if (!relativeToOption.zoned()
                && !durationRecord.isBlank()
                && (isTimeUnit(roundOptions.smallestUnit()) || hasTimeUnits(durationRecord))
                && !isDateTimeWithinTemporalRange(startDateTime)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        if (relativeToOption.zoned() && !hasCalendarUnits(durationRecord)) {
            BigInteger dayTimeNanoseconds = durationRecord.dayTimeNanoseconds();
            if (dayTimeNanoseconds.abs().compareTo(MAX_FLOAT64_NANOSECONDS_COMPONENT) >= 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger endEpochNanoseconds = relativeToOption.epochNanoseconds().add(dayTimeNanoseconds);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(endEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        LocalDateTime unroundedEndDateTime;
        BigInteger unroundedEndEpochNanoseconds = null;
        if (relativeToOption.zoned()) {
            TemporalZonedDateTimeComputation zonedDateTimeComputation =
                    durationRecord.addDurationToZonedDateTime(context, relativeToOption);
            if (context.hasPendingException() || zonedDateTimeComputation == null) {
                return null;
            }
            unroundedEndDateTime = zonedDateTimeComputation.localDateTime();
            unroundedEndEpochNanoseconds = zonedDateTimeComputation.epochNanoseconds();
        } else {
            unroundedEndDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addDurationToLocalDateTime(context, durationRecord);
        }
        if (context.hasPendingException()) {
            return null;
        }
        if (relativeToOption.zoned()) {
            if (!isDateTimeWithinTemporalRange(unroundedEndDateTime)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        } else {
            if (!isDateWithinTemporalRange(unroundedEndDateTime.toLocalDate())) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        boolean noRoundingRequested = roundOptions.roundingIncrement() == 1L
                && "nanosecond".equals(roundOptions.smallestUnit());
        if (relativeToOption.zoned()
                && noRoundingRequested
                && durationRecord.months() == 0L
                && durationRecord.weeks() == 0L
                && durationRecord.days() == 0L
                && !isTimeUnit(roundOptions.largestUnit())) {
            return balanceZonedDurationWithoutRounding(context, durationRecord, relativeToOption);
        }

        LocalDateTime roundedEndDateTime = roundDateTimeDifference(
                context,
                startDateTime,
                unroundedEndDateTime,
                unroundedEndEpochNanoseconds,
                roundOptions,
                relativeToOption,
                durationRecord);
        if (context.hasPendingException()) {
            return null;
        }

        if (relativeToOption.zoned() && isTimeUnit(roundOptions.largestUnit())) {
            BigInteger roundedNanoseconds = nanosecondsBetween(
                    context,
                    startDateTime,
                    null,
                    roundedEndDateTime,
                    null,
                    relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
            TemporalDuration balancedTimeDuration = balanceTimeDuration(roundedNanoseconds, roundOptions.largestUnit());
            return new TemporalDuration(
                    0,
                    0,
                    0,
                    balancedTimeDuration.days(),
                    balancedTimeDuration.hours(),
                    balancedTimeDuration.minutes(),
                    balancedTimeDuration.seconds(),
                    balancedTimeDuration.milliseconds(),
                    balancedTimeDuration.microseconds(),
                    balancedTimeDuration.nanoseconds());
        }

        return buildBalancedDurationFromDateTimes(
                context,
                startDateTime,
                roundedEndDateTime,
                roundOptions.largestUnit(),
                roundOptions.smallestUnit(),
                relativeToOption);
    }

    private static TemporalRoundComputationResult roundWithoutRelativeTo(
            JSContext context,
            TemporalDuration durationRecord,
            TemporalRoundOptions roundOptions) {
        if (isRelativeToRequiredUnit(roundOptions.smallestUnit()) || isRelativeToRequiredUnit(roundOptions.largestUnit())) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        if (hasCalendarUnits(durationRecord)) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger roundingUnitNanoseconds = unitToNanosecondsBigInteger(roundOptions.smallestUnit());
        BigInteger incrementNanoseconds = roundingUnitNanoseconds.multiply(BigInteger.valueOf(roundOptions.roundingIncrement()));
        BigInteger roundedNanoseconds = TemporalMathKernel.roundBigIntegerToIncrementSigned(totalNanoseconds, incrementNanoseconds, roundOptions.roundingMode());
        TemporalDurationFieldOverrides durationFieldOverrides = null;
        String balancingLargestUnit = roundOptions.largestUnit();
        if ("millisecond".equals(roundOptions.largestUnit())) {
            BigInteger millisecondValue = roundedNanoseconds.divide(MILLISECOND_NANOSECONDS);
            if (millisecondValue.abs().compareTo(MAX_FLOAT64_MILLISECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger microsecondRemainder = roundedNanoseconds.remainder(MILLISECOND_NANOSECONDS).divide(MICROSECOND_NANOSECONDS);
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new TemporalDurationFieldOverrides(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    millisecondValue.doubleValue(),
                    microsecondRemainder.doubleValue(),
                    nanosecondRemainder.doubleValue());
            if (millisecondValue.abs().bitLength() > 63) {
                balancingLargestUnit = "second";
            }
        } else if ("microsecond".equals(roundOptions.largestUnit())) {
            BigInteger microsecondValue = roundedNanoseconds.divide(MICROSECOND_NANOSECONDS);
            if (microsecondValue.abs().compareTo(MAX_FLOAT64_MICROSECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new TemporalDurationFieldOverrides(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    0D,
                    microsecondValue.doubleValue(),
                    nanosecondRemainder.doubleValue());
            if (microsecondValue.abs().bitLength() > 63) {
                balancingLargestUnit = "second";
            }
        } else if ("nanosecond".equals(roundOptions.largestUnit())) {
            if (roundedNanoseconds.abs().compareTo(MAX_FLOAT64_NANOSECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            durationFieldOverrides = new TemporalDurationFieldOverrides(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    0D, 0D,
                    roundedNanoseconds.doubleValue());
            if (roundedNanoseconds.abs().bitLength() > 63) {
                balancingLargestUnit = "second";
            }
        }

        TemporalDuration balancedTimeDuration = balanceTimeDuration(roundedNanoseconds, balancingLargestUnit);
        TemporalDuration durationResult = new TemporalDuration(
                0,
                0,
                0,
                balancedTimeDuration.days(),
                balancedTimeDuration.hours(),
                balancedTimeDuration.minutes(),
                balancedTimeDuration.seconds(),
                balancedTimeDuration.milliseconds(),
                balancedTimeDuration.microseconds(),
                balancedTimeDuration.nanoseconds());
        return new TemporalRoundComputationResult(durationResult, durationFieldOverrides);
    }

    private static long roundingIncrementNanosecondsForSmallestUnit(String smallestUnit) {
        return switch (smallestUnit) {
            case "second" -> 1_000_000_000L;
            case "millisecond" -> 1_000_000L;
            case "microsecond" -> 1_000L;
            default -> 1L;
        };
    }

    public static JSValue seconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "seconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().seconds());
    }

    public static JSValue sign(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "sign");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().sign());
    }

    private static String smallestUnitFromFractionalSecondDigits(int fractionalSecondDigits) {
        if (fractionalSecondDigits == 0) {
            return "second";
        } else if (fractionalSecondDigits <= 3) {
            return "millisecond";
        } else if (fractionalSecondDigits <= 6) {
            return "microsecond";
        } else {
            return "nanosecond";
        }
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "subtract");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, duration, args, -1);
    }

    private static int temporalDurationUnitRank(String unit) {
        return switch (unit) {
            case "year" -> 0;
            case "month" -> 1;
            case "week" -> 2;
            case "day" -> 3;
            case "hour" -> 4;
            case "minute" -> 5;
            case "second" -> 6;
            case "millisecond" -> 7;
            case "microsecond" -> 8;
            case "nanosecond" -> 9;
            default -> 10;
        };
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "toJSON");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(duration.getDuration().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "toLocaleString");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue durationFormat = JSIntlObject.createDurationFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.durationFormatFormat(context, durationFormat, new JSValue[]{duration});
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "toString");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        TemporalDurationToStringOptions options = parseDurationToStringOptions(context, optionsValue);
        if (context.hasPendingException() || options == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = duration.getDuration();
        BigInteger dayTimeNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger incrementNanoseconds = BigInteger.valueOf(options.roundingIncrementNanoseconds());
        BigInteger roundedDayTimeNanoseconds =
                TemporalMathKernel.roundBigIntegerToIncrementSigned(dayTimeNanoseconds, incrementNanoseconds, options.roundingMode());
        if (roundedDayTimeNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        String largestExistingDayTimeUnit = largestDayTimeUnit(durationRecord);
        String largestBalancingUnit = coarserDurationUnit(largestExistingDayTimeUnit, options.smallestUnit());
        TemporalDuration balancedDayTime = balanceTimeDuration(roundedDayTimeNanoseconds, largestBalancingUnit);
        TemporalDuration roundedDurationRecord = new TemporalDuration(
                durationRecord.years(),
                durationRecord.months(),
                durationRecord.weeks(),
                balancedDayTime.days(),
                balancedDayTime.hours(),
                balancedDayTime.minutes(),
                balancedDayTime.seconds(),
                balancedDayTime.milliseconds(),
                balancedDayTime.microseconds(),
                balancedDayTime.nanoseconds());
        if (!roundedDurationRecord.isValid()
                || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(roundedDurationRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        String formattedDuration = formatDurationStringWithPrecision(roundedDurationRecord, options);
        return new JSString(formattedDuration);
    }

    public static JSValue total(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "total");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] == null) {
            context.throwTypeError("Temporal error: Must specify a totalOf parameter");
            return JSUndefined.INSTANCE;
        }

        JSValue totalOfValue = args[0];
        TemporalRelativeToOption relativeToOption = null;
        String unitText;

        if (totalOfValue instanceof JSString unitStringValue) {
            unitText = unitStringValue.value();
        } else {
            if (!(totalOfValue instanceof JSObject optionsObject)) {
                context.throwTypeError("Temporal error: totalOf must be an object.");
                return JSUndefined.INSTANCE;
            }
            JSValue relativeToValue = optionsObject.get(PropertyKey.fromString("relativeTo"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            relativeToOption = parseRoundRelativeToOption(context, relativeToValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue unitValue = optionsObject.get(PropertyKey.fromString("unit"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (unitValue instanceof JSUndefined || unitValue == null) {
                context.throwRangeError("Temporal error: Must specify a totalOf parameter");
                return JSUndefined.INSTANCE;
            }
            unitText = JSTypeConversions.toString(context, unitValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String unit = canonicalizeTemporalDurationUnit(context, unitText, "unit");
        if (context.hasPendingException() || unit == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = duration.getDuration();
        boolean requiresRelativeTo = hasCalendarUnits(durationRecord) || isRelativeToRequiredUnit(unit);
        if (requiresRelativeTo && relativeToOption == null) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        double totalValue;
        if (relativeToOption == null) {
            totalValue = totalWithoutRelativeTo(context, durationRecord, unit);
        } else {
            totalValue = totalWithRelativeTo(context, durationRecord, unit, relativeToOption);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(totalValue);
    }

    private static double totalCalendarUnitBetween(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit,
            TemporalRelativeToOption relativeToOption,
            BigInteger endEpochNanoseconds) {
        boolean zoned = relativeToOption != null && relativeToOption.zoned();
        if (startDateTime.equals(endDateTime)) {
            return 0D;
        }
        TemporalUnitStepResult stepResult = moveByWholeCalendarUnits(startDateTime, endDateTime, calendarUnit);
        long wholeUnits = stepResult.count();
        LocalDateTime boundaryDateTime = stepResult.boundaryDateTime();
        if (boundaryDateTime.equals(endDateTime)) {
            return (double) wholeUnits;
        }
        long direction;
        if (zoned) {
            BigInteger deltaNanoseconds = nanosecondsBetween(
                    context,
                    startDateTime,
                    relativeToOption.epochNanoseconds(),
                    endDateTime,
                    endEpochNanoseconds,
                    relativeToOption);
            if (context.hasPendingException()) {
                return Double.NaN;
            }
            direction = deltaNanoseconds.signum() < 0 ? -1L : 1L;
        } else {
            direction = endDateTime.isBefore(startDateTime) ? -1L : 1L;
        }
        LocalDateTime nextBoundaryDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addCalendarUnitsToLocalDateTime(calendarUnit, wholeUnits + direction);
        if (zoned) {
            if (!isDateTimeWithinTemporalRange(nextBoundaryDateTime)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        } else {
            if (!isDateWithinTemporalRange(nextBoundaryDateTime.toLocalDate())) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        }

        BigInteger remainderNanoseconds = nanosecondsBetween(
                context,
                boundaryDateTime,
                boundaryDateTime.equals(startDateTime) && zoned
                        ? relativeToOption.epochNanoseconds()
                        : null,
                endDateTime,
                endDateTime.equals(startDateTime) && zoned
                        ? relativeToOption.epochNanoseconds()
                        : endEpochNanoseconds,
                relativeToOption).abs();
        BigInteger intervalNanoseconds = nanosecondsBetween(
                context,
                boundaryDateTime,
                boundaryDateTime.equals(startDateTime) && zoned
                        ? relativeToOption.epochNanoseconds()
                        : null,
                nextBoundaryDateTime,
                null,
                relativeToOption).abs();
        BigDecimal fraction = new BigDecimal(remainderNanoseconds)
                .divide(new BigDecimal(intervalNanoseconds), java.math.MathContext.DECIMAL128);
        BigDecimal total = BigDecimal.valueOf(wholeUnits);
        if (direction < 0) {
            total = total.subtract(fraction);
        } else {
            total = total.add(fraction);
        }
        return total.doubleValue();
    }

    private static double totalWithRelativeTo(
            JSContext context,
            TemporalDuration durationRecord,
            String unit,
            TemporalRelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        if (!relativeToOption.zoned()
                && !durationRecord.isBlank()
                && (isTimeUnit(unit) || hasTimeUnits(durationRecord))
                && !isDateTimeWithinTemporalRange(startDateTime)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return Double.NaN;
        }
        if (relativeToOption.zoned() && !hasCalendarUnits(durationRecord)) {
            BigInteger dayTimeNanoseconds = durationRecord.dayTimeNanoseconds();
            if (dayTimeNanoseconds.abs().compareTo(MAX_FLOAT64_NANOSECONDS_COMPONENT) >= 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
            BigInteger endEpochNanoseconds = relativeToOption.epochNanoseconds().add(dayTimeNanoseconds);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(endEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        }

        LocalDateTime endDateTime;
        BigInteger endEpochNanoseconds = null;
        if (relativeToOption.zoned()) {
            TemporalZonedDateTimeComputation zonedDateTimeComputation =
                    durationRecord.addDurationToZonedDateTime(context, relativeToOption);
            if (context.hasPendingException() || zonedDateTimeComputation == null) {
                return Double.NaN;
            }
            endDateTime = zonedDateTimeComputation.localDateTime();
            endEpochNanoseconds = zonedDateTimeComputation.epochNanoseconds();
        } else {
            endDateTime = IsoDateTime.createFromLocalDateTime(startDateTime).addDurationToLocalDateTime(context, durationRecord);
        }
        if (context.hasPendingException()) {
            return Double.NaN;
        }
        if (relativeToOption.zoned()) {
            if (!isDateTimeWithinTemporalRange(endDateTime)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        } else {
            if (!isDateWithinTemporalRange(endDateTime.toLocalDate())) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        }

        if (relativeToOption.zoned() && "day".equals(unit)) {
            BigInteger nextDayEpochNanoseconds = relativeToOption.epochNanoseconds().add(DAY_NANOSECONDS);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(nextDayEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        }

        if ("year".equals(unit) || "month".equals(unit) || "week".equals(unit) || "day".equals(unit)) {
            return totalCalendarUnitBetween(
                    context,
                    startDateTime,
                    endDateTime,
                    unit,
                    relativeToOption,
                    endEpochNanoseconds);
        }
        BigInteger totalNanoseconds;
        if (relativeToOption.zoned()) {
            totalNanoseconds = endEpochNanoseconds.subtract(relativeToOption.epochNanoseconds());
        } else {
            totalNanoseconds = nanosecondsBetween(startDateTime, endDateTime);
        }
        BigInteger divisorNanoseconds = unitDivisorNanosecondsForTotal(unit);
        return divideBigIntegersToDouble(totalNanoseconds, divisorNanoseconds);
    }

    private static double totalWithoutRelativeTo(
            JSContext context,
            TemporalDuration durationRecord,
            String unit) {
        if (isRelativeToRequiredUnit(unit)) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return Double.NaN;
        }
        if (hasCalendarUnits(durationRecord)) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return Double.NaN;
        }
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger divisorNanoseconds = unitDivisorNanosecondsForTotal(unit);
        return divideBigIntegersToDouble(totalNanoseconds, divisorNanoseconds);
    }

    private static BigInteger unitDivisorNanosecondsForTotal(String unit) {
        return switch (unit) {
            case "week" -> WEEK_NANOSECONDS;
            case "day" -> DAY_NANOSECONDS;
            case "hour" -> HOUR_NANOSECONDS;
            case "minute" -> MINUTE_NANOSECONDS;
            case "second" -> SECOND_NANOSECONDS;
            case "millisecond" -> MILLISECOND_NANOSECONDS;
            case "microsecond" -> MICROSECOND_NANOSECONDS;
            case "nanosecond" -> BigInteger.ONE;
            default -> BigInteger.ONE;
        };
    }

    private static BigInteger unitToNanosecondsBigInteger(String unit) {
        return TemporalUnit.fromString(unit)
                .map(temporalUnit -> switch (temporalUnit) {
                    case WEEK -> WEEK_NANOSECONDS;
                    case DAY -> DAY_NANOSECONDS;
                    case HOUR -> HOUR_NANOSECONDS;
                    case MINUTE -> MINUTE_NANOSECONDS;
                    case SECOND -> SECOND_NANOSECONDS;
                    case MILLISECOND -> MILLISECOND_NANOSECONDS;
                    case MICROSECOND -> MICROSECOND_NANOSECONDS;
                    case NANOSECOND -> BigInteger.ONE;
                    default -> BigInteger.ZERO;
                })
                .orElse(BigInteger.ZERO);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.Duration.prototype.valueOf; use Temporal.Duration.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weeks(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "weeks");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().weeks());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "with");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        TemporalPartialDurationFieldValue daysFieldValue = readPartialDurationField(context, fields, "days");
        if (context.hasPendingException() || daysFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue hoursFieldValue = readPartialDurationField(context, fields, "hours");
        if (context.hasPendingException() || hoursFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue microsecondsFieldValue = readPartialDurationField(context, fields, "microseconds");
        if (context.hasPendingException() || microsecondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue millisecondsFieldValue = readPartialDurationField(context, fields, "milliseconds");
        if (context.hasPendingException() || millisecondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue minutesFieldValue = readPartialDurationField(context, fields, "minutes");
        if (context.hasPendingException() || minutesFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue monthsFieldValue = readPartialDurationField(context, fields, "months");
        if (context.hasPendingException() || monthsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue nanosecondsFieldValue = readPartialDurationField(context, fields, "nanoseconds");
        if (context.hasPendingException() || nanosecondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue secondsFieldValue = readPartialDurationField(context, fields, "seconds");
        if (context.hasPendingException() || secondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue weeksFieldValue = readPartialDurationField(context, fields, "weeks");
        if (context.hasPendingException() || weeksFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPartialDurationFieldValue yearsFieldValue = readPartialDurationField(context, fields, "years");
        if (context.hasPendingException() || yearsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }

        boolean hasAnyDefinedFields =
                daysFieldValue.present()
                        || hoursFieldValue.present()
                        || microsecondsFieldValue.present()
                        || millisecondsFieldValue.present()
                        || minutesFieldValue.present()
                        || monthsFieldValue.present()
                        || nanosecondsFieldValue.present()
                        || secondsFieldValue.present()
                        || weeksFieldValue.present()
                        || yearsFieldValue.present();
        if (!hasAnyDefinedFields) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = duration.getDuration();
        long years = yearsFieldValue.present() ? yearsFieldValue.value() : durationRecord.years();
        long months = monthsFieldValue.present() ? monthsFieldValue.value() : durationRecord.months();
        long weeks = weeksFieldValue.present() ? weeksFieldValue.value() : durationRecord.weeks();
        long days = daysFieldValue.present() ? daysFieldValue.value() : durationRecord.days();
        long hours = hoursFieldValue.present() ? hoursFieldValue.value() : durationRecord.hours();
        long minutes = minutesFieldValue.present() ? minutesFieldValue.value() : durationRecord.minutes();
        long seconds = secondsFieldValue.present() ? secondsFieldValue.value() : durationRecord.seconds();
        long milliseconds = millisecondsFieldValue.present() ? millisecondsFieldValue.value() : durationRecord.milliseconds();
        long microseconds = microsecondsFieldValue.present() ? microsecondsFieldValue.value() : durationRecord.microseconds();
        long nanoseconds = nanosecondsFieldValue.present() ? nanosecondsFieldValue.value() : durationRecord.nanoseconds();
        TemporalDuration newRecord = new TemporalDuration(years, months, weeks, days,
                hours, minutes, seconds, milliseconds, microseconds, nanoseconds);

        if (!newRecord.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return JSUndefined.INSTANCE;
        }

        return TemporalDurationConstructor.createDuration(context, newRecord);
    }

    public static JSValue years(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "years");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().years());
    }

}
