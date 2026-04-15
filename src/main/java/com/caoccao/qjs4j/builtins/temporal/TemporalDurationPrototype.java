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
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger MAX_FLOAT64_MICROSECONDS_COMPONENT =
            new BigInteger("9007199254740991475711");
    private static final BigInteger MAX_FLOAT64_MILLISECONDS_COMPONENT =
            new BigInteger("9007199254740991487");
    private static final BigInteger MAX_FLOAT64_NANOSECONDS_COMPONENT =
            new BigInteger("9007199254740991463129087");
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS =
            BigInteger.valueOf(9_007_199_254_740_992L).multiply(SECOND_NANOSECONDS).subtract(BigInteger.ONE);
    private static final String TYPE_NAME = "Temporal.Duration";
    private static final BigInteger WEEK_NANOSECONDS = DAY_NANOSECONDS.multiply(BigInteger.valueOf(7L));

    private TemporalDurationPrototype() {
    }

    public static JSValue abs(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "abs");
        if (duration == null) return JSUndefined.INSTANCE;
        return TemporalDurationConstructor.createDuration(context, duration.getDuration().abs());
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "add");
        if (duration == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, duration, args, 1);
    }

    private static LocalDateTime addCalendarUnits(LocalDateTime dateTime, String calendarUnit, long amount) {
        if ("year".equals(calendarUnit)) {
            return dateTime.plusYears(amount);
        } else if ("month".equals(calendarUnit)) {
            return dateTime.plusMonths(amount);
        } else if ("week".equals(calendarUnit)) {
            return dateTime.plusWeeks(amount);
        } else {
            return dateTime.plusDays(amount);
        }
    }

    private static LocalDateTime addDurationToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            TemporalDuration durationRecord) {
        LocalDateTime dateBalancedDateTime = startDateTime
                .plusYears(durationRecord.years())
                .plusMonths(durationRecord.months())
                .plusWeeks(durationRecord.weeks())
                .plusDays(durationRecord.days());
        BigInteger timeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
        return addNanosecondsToDateTime(context, dateBalancedDateTime, timeNanoseconds);
    }

    private static ZonedDateTimeComputation addDurationToZonedDateTime(
            JSContext context,
            RelativeToOption relativeToOption,
            TemporalDuration durationRecord) {
        LocalDateTime dateBalancedDateTime = relativeToOption.startDateTime()
                .plusYears(durationRecord.years())
                .plusMonths(durationRecord.months())
                .plusWeeks(durationRecord.weeks())
                .plusDays(durationRecord.days());
        BigInteger intermediateEpochNanoseconds;
        if (dateBalancedDateTime.equals(relativeToOption.startDateTime())) {
            intermediateEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            intermediateEpochNanoseconds =
                    zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, dateBalancedDateTime);
        }
        if (context.hasPendingException() || intermediateEpochNanoseconds == null) {
            return null;
        }

        BigInteger timeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
        BigInteger endEpochNanoseconds = intermediateEpochNanoseconds.add(timeNanoseconds);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(endEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        IsoDateTime endIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                endEpochNanoseconds,
                relativeToOption.timeZoneId());
        LocalDateTime endDateTime = toLocalDateTime(endIsoDateTime.date(), endIsoDateTime.time());
        int endOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(
                endEpochNanoseconds,
                relativeToOption.timeZoneId());
        return new ZonedDateTimeComputation(endDateTime, endEpochNanoseconds, endOffsetSeconds);
    }

    private static LocalDateTime addFixedUnits(LocalDateTime dateTime, String unit, long amount) {
        if ("week".equals(unit)) {
            return dateTime.plusWeeks(amount);
        } else {
            return dateTime.plusDays(amount);
        }
    }

    private static LocalDateTime addNanosecondsToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger nanoseconds) {
        BigInteger[] dayQuotientAndRemainder = nanoseconds.divideAndRemainder(DAY_NANOSECONDS);
        long dayAdjustment;
        try {
            dayAdjustment = dayQuotientAndRemainder[0].longValueExact();
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        BigInteger nanosecondRemainder = dayQuotientAndRemainder[1];
        if (nanosecondRemainder.signum() < 0) {
            dayAdjustment--;
            nanosecondRemainder = nanosecondRemainder.add(DAY_NANOSECONDS);
        }
        long nanosecondAdjustment = nanosecondRemainder.longValueExact();
        try {
            return startDateTime.plusDays(dayAdjustment).plusNanos(nanosecondAdjustment);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
    }

    private static LocalDateTime addNanosecondsToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger nanoseconds,
            RelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return addNanosecondsToDateTime(context, startDateTime, nanoseconds);
        }
        BigInteger startEpochNanoseconds;
        if (startDateTime.equals(relativeToOption.startDateTime())) {
            startEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            startEpochNanoseconds =
                    zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, startDateTime);
        }
        if (context.hasPendingException() || startEpochNanoseconds == null) {
            return null;
        }
        BigInteger resultEpochNanoseconds = startEpochNanoseconds.add(nanoseconds);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(resultEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        IsoDateTime resultIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                resultEpochNanoseconds,
                relativeToOption.timeZoneId());
        return toLocalDateTime(resultIsoDateTime.date(), resultIsoDateTime.time());
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

        BigInteger leftNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(leftRecord);
        BigInteger rightNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(rightRecord);
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
            DurationFieldOverrides durationFieldOverrides) {
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

    static TemporalDuration balanceTimeDuration(long totalNs, String largestUnit) {
        return balanceTimeDuration(BigInteger.valueOf(totalNs), largestUnit);
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
            RelativeToOption relativeToOption) {
        LocalDateTime dayBalanceAnchorDateTime = relativeToOption.startDateTime()
                .plusYears(durationRecord.years())
                .plusMonths(durationRecord.months())
                .plusWeeks(durationRecord.weeks())
                .plusDays(durationRecord.days());
        BigInteger remainingTimeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
        long additionalDayCount = 0L;
        while (remainingTimeNanoseconds.signum() != 0) {
            int direction = remainingTimeNanoseconds.signum() < 0 ? -1 : 1;
            LocalDateTime adjacentDayDateTime = dayBalanceAnchorDateTime.plusDays(direction);
            BigInteger oneDayNanoseconds = nanosecondsBetween(
                    context,
                    dayBalanceAnchorDateTime,
                    adjacentDayDateTime,
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
        if (duration == null) return JSUndefined.INSTANCE;
        return duration.getDuration().isBlank() ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static TemporalDuration buildBalancedDurationFromDateTimes(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String largestUnit,
            String smallestUnit,
            RelativeToOption relativeToOption) {
        if ("hour".equals(largestUnit)
                || "minute".equals(largestUnit)
                || "second".equals(largestUnit)
                || "millisecond".equals(largestUnit)
                || "microsecond".equals(largestUnit)
                || "nanosecond".equals(largestUnit)) {
            BigInteger totalNanoseconds = nanosecondsBetween(
                    context,
                    startDateTime,
                    endDateTime,
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
            UnitStepResult yearStepResult = moveByWholeCalendarUnits(cursorDateTime, endDateTime, "year");
            years = yearStepResult.count();
            cursorDateTime = yearStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("year")) {
                return new TemporalDuration(years, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if ("year".equals(largestUnit) || "month".equals(largestUnit)) {
            UnitStepResult monthStepResult = moveByWholeCalendarUnits(cursorDateTime, endDateTime, "month");
            months = monthStepResult.count();
            cursorDateTime = monthStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("month")) {
                return new TemporalDuration(years, months, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        boolean shouldBalanceWeeks = "week".equals(largestUnit) || "week".equals(smallestUnit);
        if (shouldBalanceWeeks) {
            UnitStepResult weekStepResult;
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
            UnitStepResult dayStepResult;
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
                endDateTime,
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
        String canonicalizedUnit = switch (unitText) {
            case "second", "seconds" -> "second";
            case "millisecond", "milliseconds" -> "millisecond";
            case "microsecond", "microseconds" -> "microsecond";
            case "nanosecond", "nanoseconds" -> "nanosecond";
            default -> null;
        };
        if (canonicalizedUnit == null) {
            context.throwRangeError("Temporal error: Invalid smallestUnit.");
            return null;
        }
        return canonicalizedUnit;
    }

    private static String canonicalizeTemporalDurationUnit(JSContext context, String unitText, String optionName) {
        String normalizedUnitText;
        switch (unitText) {
            case "year":
            case "years":
                normalizedUnitText = "year";
                break;
            case "month":
            case "months":
                normalizedUnitText = "month";
                break;
            case "week":
            case "weeks":
                normalizedUnitText = "week";
                break;
            case "day":
            case "days":
                normalizedUnitText = "day";
                break;
            case "hour":
            case "hours":
                normalizedUnitText = "hour";
                break;
            case "minute":
            case "minutes":
                normalizedUnitText = "minute";
                break;
            case "second":
            case "seconds":
                normalizedUnitText = "second";
                break;
            case "millisecond":
            case "milliseconds":
                normalizedUnitText = "millisecond";
                break;
            case "microsecond":
            case "microseconds":
                normalizedUnitText = "microsecond";
                break;
            case "nanosecond":
            case "nanoseconds":
                normalizedUnitText = "nanosecond";
                break;
            default:
                context.throwRangeError("Temporal error: Invalid " + optionName + ".");
                return null;
        }
        return normalizedUnitText;
    }

    private static JSTemporalDuration checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalDuration duration)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return duration;
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
        if (duration == null) return JSUndefined.INSTANCE;
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
        LocalDateTime startDateTime = toLocalDateTime(startIsoDateTime.date(), startIsoDateTime.time());
        LocalDateTime endDateTime = toLocalDateTime(endIsoDateTime.date(), endIsoDateTime.time());

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

        RelativeToOption relativeToOption = new RelativeToOption(startDateTime, false, null, null, null);
        RoundOptions roundOptions = new RoundOptions(
                smallestUnit,
                largestUnit,
                roundingIncrement,
                roundingMode,
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
        IsoDateTime startIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                startEpochNanoseconds,
                timeZoneId);
        IsoDateTime endIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                endEpochNanoseconds,
                timeZoneId);
        LocalDateTime startDateTime = toLocalDateTime(
                startIsoDateTime.date(),
                startIsoDateTime.time());
        LocalDateTime endDateTime = toLocalDateTime(
                endIsoDateTime.date(),
                endIsoDateTime.time());
        RelativeToOption relativeToOption = new RelativeToOption(
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

        RoundOptions roundOptions = new RoundOptions(
                smallestUnit,
                largestUnit,
                roundingIncrement,
                roundingMode,
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

    private static BigInteger[] floorDivideAndRemainder(BigInteger value, BigInteger divisor) {
        BigInteger[] quotientAndRemainder = value.divideAndRemainder(divisor);
        BigInteger quotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];
        if (remainder.signum() < 0) {
            quotient = quotient.subtract(BigInteger.ONE);
            remainder = remainder.add(divisor);
        }
        return new BigInteger[]{quotient, remainder};
    }

    private static String formatDurationStringWithPrecision(
            TemporalDuration durationRecord,
            DurationToStringOptions options) {
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
        if (duration == null) return JSUndefined.INSTANCE;
        return JSNumber.of(duration.getDuration().hours());
    }

    private static boolean isCalendarUnit(String unit) {
        return "year".equals(unit) || "month".equals(unit) || "week".equals(unit) || "day".equals(unit);
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
        return "year".equals(unit) || "month".equals(unit) || "week".equals(unit);
    }

    private static boolean isTimeUnit(String unit) {
        return "hour".equals(unit)
                || "minute".equals(unit)
                || "second".equals(unit)
                || "millisecond".equals(unit)
                || "microsecond".equals(unit)
                || "nanosecond".equals(unit);
    }

    private static boolean isValidIncrementForUnit(String smallestUnit, long roundingIncrement) {
        long maximumIncrement;
        switch (smallestUnit) {
            case "hour":
                maximumIncrement = 24L;
                break;
            case "minute":
            case "second":
                maximumIncrement = 60L;
                break;
            case "millisecond":
            case "microsecond":
            case "nanosecond":
                maximumIncrement = 1000L;
                break;
            default:
                return true;
        }
        if (roundingIncrement >= maximumIncrement) {
            return false;
        }
        return maximumIncrement % roundingIncrement == 0L;
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
        if (duration == null) return JSUndefined.INSTANCE;
        return JSNumber.of(duration.getDuration().microseconds());
    }

    public static JSValue milliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "milliseconds");
        if (duration == null) return JSUndefined.INSTANCE;
        return JSNumber.of(duration.getDuration().milliseconds());
    }

    public static JSValue minutes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "minutes");
        if (duration == null) return JSUndefined.INSTANCE;
        return JSNumber.of(duration.getDuration().minutes());
    }

    public static JSValue months(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "months");
        if (duration == null) return JSUndefined.INSTANCE;
        return JSNumber.of(duration.getDuration().months());
    }

    private static UnitStepResult moveByWholeCalendarUnits(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit) {
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime, calendarUnit);
        LocalDateTime boundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, unitCount);
        if (!endDateTime.isBefore(startDateTime)) {
            while (boundaryDateTime.isAfter(endDateTime)) {
                unitCount--;
                boundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount + 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, calendarUnit, nextUnitCount);
                if (nextDateTime.isAfter(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        } else {
            while (boundaryDateTime.isBefore(endDateTime)) {
                unitCount++;
                boundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount - 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, calendarUnit, nextUnitCount);
                if (nextDateTime.isBefore(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        }
        return new UnitStepResult(unitCount, boundaryDateTime);
    }

    private static UnitStepResult moveByWholeCalendarUnits(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit,
            RelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return moveByWholeCalendarUnits(startDateTime, endDateTime, calendarUnit);
        }
        BigInteger startToEndNanoseconds = nanosecondsBetween(
                context,
                startDateTime,
                endDateTime,
                relativeToOption);
        if (context.hasPendingException()) {
            return new UnitStepResult(0L, startDateTime);
        }

        int direction = startToEndNanoseconds.signum();
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime, calendarUnit);
        LocalDateTime boundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, unitCount);
        if (direction >= 0) {
            while (true) {
                BigInteger boundaryToEndNanoseconds = nanosecondsBetween(
                        context,
                        boundaryDateTime,
                        endDateTime,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new UnitStepResult(unitCount, boundaryDateTime);
                }
                if (boundaryToEndNanoseconds.signum() >= 0) {
                    break;
                }
                unitCount--;
                boundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount + 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, calendarUnit, nextUnitCount);
                BigInteger nextToEndNanoseconds = nanosecondsBetween(
                        context,
                        nextDateTime,
                        endDateTime,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new UnitStepResult(unitCount, boundaryDateTime);
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
                        endDateTime,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new UnitStepResult(unitCount, boundaryDateTime);
                }
                if (boundaryToEndNanoseconds.signum() < 0
                        || (boundaryToEndNanoseconds.signum() == 0 && boundaryDateTime.equals(endDateTime))) {
                    break;
                }
                unitCount++;
                boundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount - 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, calendarUnit, nextUnitCount);
                BigInteger nextToEndNanoseconds = nanosecondsBetween(
                        context,
                        nextDateTime,
                        endDateTime,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return new UnitStepResult(unitCount, boundaryDateTime);
                }
                if (nextToEndNanoseconds.signum() > 0
                        || (nextToEndNanoseconds.signum() == 0 && !nextDateTime.equals(endDateTime))) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        }
        return new UnitStepResult(unitCount, boundaryDateTime);
    }

    private static UnitStepResult moveByWholeFixedUnits(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String unit) {
        BigInteger unitNanoseconds = unitToNanosecondsBigInteger(unit);
        if (unitNanoseconds.signum() == 0) {
            return new UnitStepResult(0, startDateTime);
        }
        BigInteger deltaNanoseconds = nanosecondsBetween(startDateTime, endDateTime);
        long unitCount = deltaNanoseconds.divide(unitNanoseconds).longValue();
        LocalDateTime boundaryDateTime = addFixedUnits(startDateTime, unit, unitCount);
        return new UnitStepResult(unitCount, boundaryDateTime);
    }

    public static JSValue nanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "nanoseconds");
        if (duration == null) return JSUndefined.INSTANCE;
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
            LocalDateTime endDateTime,
            RelativeToOption relativeToOption) {
        return nanosecondsBetween(
                context,
                startDateTime,
                null,
                endDateTime,
                null,
                relativeToOption);
    }

    private static BigInteger nanosecondsBetween(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger knownStartEpochNanoseconds,
            LocalDateTime endDateTime,
            BigInteger knownEndEpochNanoseconds,
            RelativeToOption relativeToOption) {
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
                    zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, startDateTime);
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
                    zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, endDateTime);
        }
        if (context.hasPendingException() || endEpochNanoseconds == null) {
            return BigInteger.ZERO;
        }
        return endEpochNanoseconds.subtract(startEpochNanoseconds);
    }

    public static JSValue negated(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "negated");
        if (duration == null) return JSUndefined.INSTANCE;
        return TemporalDurationConstructor.createDuration(context, duration.getDuration().negated());
    }

    private static DurationToStringOptions parseDurationToStringOptions(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalOptionResolver.toOptionalOptionsObject(
                context,
                optionsValue,
                "Temporal error: Options must be an object.");
        if (context.hasPendingException()) {
            return null;
        }

        JSValue fractionalSecondDigitsValue = JSUndefined.INSTANCE;
        if (optionsObject != null) {
            fractionalSecondDigitsValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
            if (context.hasPendingException()) {
                return null;
            }
        }
        TemporalOptionResolver.FractionalSecondDigitsOption resolvedFractionalSecondDigitsOption =
                TemporalOptionResolver.parseFractionalSecondDigitsOption(
                        context,
                        fractionalSecondDigitsValue,
                        "Temporal error: Invalid fractionalSecondDigits.");
        if (context.hasPendingException() || resolvedFractionalSecondDigitsOption == null) {
            return null;
        }
        FractionalSecondDigitsOption fractionalSecondDigitsOption = new FractionalSecondDigitsOption(
                resolvedFractionalSecondDigitsOption.auto(),
                resolvedFractionalSecondDigitsOption.digits());

        String roundingMode = "trunc";
        if (optionsObject != null) {
            roundingMode = TemporalOptionResolver.getStringOption(context, optionsObject, "roundingMode", "trunc");
            if (context.hasPendingException() || roundingMode == null) {
                return null;
            }
        }

        String smallestUnitText = null;
        if (optionsObject != null) {
            smallestUnitText = TemporalOptionResolver.getStringOption(context, optionsObject, "smallestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
        }

        if (!TemporalOptionResolver.isValidRoundingMode(roundingMode)) {
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
            roundingIncrementNanoseconds = Math.round(Math.pow(10, 9 - fractionalSecondDigits));
        }

        return new DurationToStringOptions(
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

    private static RoundOptions parseRoundOptions(JSContext context, JSValue roundToArg, TemporalDuration durationRecord) {
        String largestUnitText = null;
        String smallestUnitText = null;
        RelativeToOption relativeToOption = null;
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

        if (!TemporalOptionResolver.isValidRoundingMode(roundingMode)) {
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

        return new RoundOptions(
                canonicalSmallestUnit,
                canonicalLargestUnit,
                roundingIncrement,
                roundingMode,
                relativeToOption);
    }

    private static RelativeToOption parseRoundRelativeToOption(JSContext context, JSValue relativeToValue) {
        if (relativeToValue instanceof JSUndefined || relativeToValue == null) {
            return null;
        }
        TemporalDurationConstructor.RelativeToReference relativeToReference =
                TemporalDurationConstructor.parseRelativeToValue(context, relativeToValue);
        if (context.hasPendingException() || relativeToReference == null) {
            return null;
        }
        if (relativeToReference.epochNanoseconds() == null || relativeToReference.timeZoneId() == null) {
            LocalDateTime startDateTime = toLocalDateTime(
                    relativeToReference.relativeDate(),
                    relativeToReference.relativeTime());
            return new RelativeToOption(startDateTime, false, null, null, null);
        }
        IsoDateTime localIsoDateTime;
        if (isOffsetTimeZoneIdentifier(relativeToReference.timeZoneId())) {
            int offsetSeconds = parseOffsetSecondsFromTimeZoneId(relativeToReference.timeZoneId());
            IsoDateTime utcDateTime = TemporalTimeZone.epochNsToUtcDateTime(relativeToReference.epochNanoseconds());
            LocalDateTime utcLocalDateTime = toLocalDateTime(utcDateTime.date(), utcDateTime.time());
            LocalDateTime offsetLocalDateTime = utcLocalDateTime.plusSeconds(offsetSeconds);
            localIsoDateTime = new IsoDateTime(
                    new IsoDate(
                            offsetLocalDateTime.getYear(),
                            offsetLocalDateTime.getMonthValue(),
                            offsetLocalDateTime.getDayOfMonth()),
                    new IsoTime(
                            offsetLocalDateTime.getHour(),
                            offsetLocalDateTime.getMinute(),
                            offsetLocalDateTime.getSecond(),
                            offsetLocalDateTime.getNano() / 1_000_000,
                            (offsetLocalDateTime.getNano() / 1_000) % 1_000,
                            offsetLocalDateTime.getNano() % 1_000));
        } else {
            localIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    relativeToReference.epochNanoseconds(),
                    relativeToReference.timeZoneId());
        }
        LocalDateTime startDateTime = toLocalDateTime(localIsoDateTime.date(), localIsoDateTime.time());
        return new RelativeToOption(
                startDateTime,
                true,
                relativeToReference.epochNanoseconds(),
                relativeToReference.timeZoneId(),
                relativeToReference.offsetSeconds());
    }

    private static PartialDurationFieldValue readPartialDurationField(JSContext context, JSObject fields, String fieldKey) {
        JSValue value = fields.get(PropertyKey.fromString(fieldKey));
        if (context.hasPendingException()) {
            return null;
        }
        if (value instanceof JSUndefined || value == null) {
            return new PartialDurationFieldValue(false, 0L);
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
        return new PartialDurationFieldValue(true, (long) numericValue);
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

        RoundOptions roundOptions = parseRoundOptions(context, args[0], duration.getDuration());
        if (context.hasPendingException() || roundOptions == null) {
            return JSUndefined.INSTANCE;
        }

        RelativeToOption relativeToOption = roundOptions.relativeToOption();

        TemporalDuration durationRecord = duration.getDuration();
        boolean requiresRelativeTo =
                hasCalendarUnits(durationRecord)
                        || isRelativeToRequiredUnit(roundOptions.smallestUnit())
                        || isRelativeToRequiredUnit(roundOptions.largestUnit());
        if (requiresRelativeTo && relativeToOption == null) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        RoundComputationResult roundComputationResult;
        if (relativeToOption == null) {
            roundComputationResult = roundWithoutRelativeTo(context, durationRecord, roundOptions);
        } else {
            TemporalDuration roundedDurationRecord =
                    roundWithRelativeTo(context, durationRecord, roundOptions, relativeToOption);
            roundComputationResult = new RoundComputationResult(roundedDurationRecord, null);
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
        DurationFieldOverrides durationFieldOverrides = roundComputationResult.durationFieldOverrides();
        if (durationFieldOverrides != null) {
            applyDurationFieldOverrides(roundedDuration, durationFieldOverrides);
        }
        return roundedDuration;
    }

    private static BigInteger roundBigIntegerToIncrement(BigInteger value, BigInteger increment, String roundingMode) {
        if (increment.signum() == 0) {
            return value;
        }

        BigInteger[] floorQuotientAndRemainder = floorDivideAndRemainder(value, increment);
        BigInteger floorQuotient = floorQuotientAndRemainder[0];
        BigInteger remainder = floorQuotientAndRemainder[1];
        BigInteger floorValue = floorQuotient.multiply(increment);
        if (remainder.signum() == 0) {
            return floorValue;
        }
        BigInteger ceilValue = floorValue.add(increment);
        int sign = value.signum();

        switch (roundingMode) {
            case "floor":
                return floorValue;
            case "ceil":
                return ceilValue;
            case "trunc":
                if (sign < 0) {
                    return ceilValue;
                } else {
                    return floorValue;
                }
            case "expand":
                if (sign < 0) {
                    return floorValue;
                } else {
                    return ceilValue;
                }
            case "halfExpand":
            case "halfTrunc":
            case "halfEven":
            case "halfCeil":
            case "halfFloor":
                BigInteger twoRemainder = remainder.shiftLeft(1);
                int halfComparison = twoRemainder.compareTo(increment);
                if (halfComparison < 0) {
                    return floorValue;
                }
                if (halfComparison > 0) {
                    return ceilValue;
                }
                return switch (roundingMode) {
                    case "halfExpand" -> {
                        if (sign < 0) {
                            yield floorValue;
                        } else {
                            yield ceilValue;
                        }
                    }
                    case "halfTrunc" -> {
                        if (sign < 0) {
                            yield ceilValue;
                        } else {
                            yield floorValue;
                        }
                    }
                    case "halfEven" -> {
                        if (floorQuotient.testBit(0)) {
                            yield ceilValue;
                        } else {
                            yield floorValue;
                        }
                    }
                    case "halfCeil" -> ceilValue;
                    case "halfFloor" -> floorValue;
                    default -> ceilValue;
                };
            default:
                return ceilValue;
        }
    }

    private static LocalDateTime roundDateTimeDifference(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime unroundedEndDateTime,
            BigInteger unroundedEndEpochNanoseconds,
            RoundOptions roundOptions,
            RelativeToOption relativeToOption,
            TemporalDuration durationRecord) {
        String smallestUnit = roundOptions.smallestUnit();
        long roundingIncrement = roundOptions.roundingIncrement();
        String roundingMode = roundOptions.roundingMode();

        if ("year".equals(smallestUnit) || "month".equals(smallestUnit)) {
            return roundDateTimeToCalendarUnit(
                    context,
                    startDateTime,
                    unroundedEndDateTime,
                    unroundedEndEpochNanoseconds,
                    smallestUnit,
                    roundingIncrement,
                    roundingMode,
                    relativeToOption);
        }

        if ("week".equals(smallestUnit)
                && ("year".equals(roundOptions.largestUnit())
                || "month".equals(roundOptions.largestUnit())
                || "week".equals(roundOptions.largestUnit()))) {
            LocalDateTime weekAnchorDateTime = startDateTime;
            if ("year".equals(roundOptions.largestUnit())) {
                UnitStepResult yearStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "year");
                weekAnchorDateTime = yearStepResult.boundaryDateTime();
                UnitStepResult monthStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "month");
                weekAnchorDateTime = monthStepResult.boundaryDateTime();
            } else if ("month".equals(roundOptions.largestUnit())) {
                UnitStepResult monthStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "month");
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
                    roundBigIntegerToIncrement(weekRemainderNanoseconds, weekIncrementNanoseconds, roundingMode);
            return addNanosecondsToDateTime(
                    context,
                    weekAnchorDateTime,
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
            BigInteger roundedTimeNanoseconds = roundBigIntegerToIncrement(
                    BigInteger.valueOf(balancedDurationWithoutRounding.hours()).multiply(HOUR_NANOSECONDS)
                            .add(BigInteger.valueOf(balancedDurationWithoutRounding.minutes()).multiply(MINUTE_NANOSECONDS))
                            .add(BigInteger.valueOf(balancedDurationWithoutRounding.seconds()).multiply(SECOND_NANOSECONDS))
                            .add(BigInteger.valueOf(balancedDurationWithoutRounding.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                            .add(BigInteger.valueOf(balancedDurationWithoutRounding.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                            .add(BigInteger.valueOf(balancedDurationWithoutRounding.nanoseconds())),
                    unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement)),
                    roundingMode);
            BigInteger beyondDaySpanNanoseconds = roundedTimeNanoseconds.subtract(daySpanNanoseconds);
            boolean roundedBeyondOneDay = beyondDaySpanNanoseconds.signum() != -durationSign;
            if (roundedBeyondOneDay) {
                roundedTimeNanoseconds = roundBigIntegerToIncrement(
                        beyondDaySpanNanoseconds,
                        unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement)),
                        roundingMode);
                return addNanosecondsToDateTime(context, endAnchorDateTime, roundedTimeNanoseconds, relativeToOption);
            }
            return addNanosecondsToDateTime(context, startAnchorDateTime, roundedTimeNanoseconds, relativeToOption);
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
                    roundBigIntegerToIncrement(timeRemainderNanoseconds, incrementNanoseconds, roundingMode);
            return addNanosecondsToDateTime(
                    context,
                    dayAdjustedDateTime,
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
                    roundingMode,
                    relativeToOption);
        } else if ("week".equals(smallestUnit)) {
            incrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
        } else {
            incrementNanoseconds = unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement));
        }
        BigInteger roundedNanoseconds = roundBigIntegerToIncrement(totalNanoseconds, incrementNanoseconds, roundingMode);
        return addNanosecondsToDateTime(context, startDateTime, roundedNanoseconds, relativeToOption);
    }

    private static LocalDateTime roundDateTimeToCalendarUnit(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            BigInteger knownEndEpochNanoseconds,
            String calendarUnit,
            long roundingIncrement,
            String roundingMode,
            RelativeToOption relativeToOption) {
        UnitStepResult truncatedStepResult = moveByWholeCalendarUnits(startDateTime, endDateTime, calendarUnit);
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

        LocalDateTime lowerBoundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, lowerMultipleCount);
        LocalDateTime upperBoundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, upperMultipleCount);
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
                        endDateTime,
                        relativeToOption).abs();
                BigInteger upperDistanceNanoseconds = nanosecondsBetween(
                        context,
                        endDateTime,
                        upperBoundaryDateTime,
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
            RoundOptions roundOptions,
            RelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        if (!relativeToOption.zoned()
                && !durationRecord.isBlank()
                && (isTimeUnit(roundOptions.smallestUnit()) || hasTimeUnits(durationRecord))
                && !isDateTimeWithinTemporalRange(startDateTime)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        if (relativeToOption.zoned() && !hasCalendarUnits(durationRecord)) {
            BigInteger dayTimeNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
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
            ZonedDateTimeComputation zonedDateTimeComputation =
                    addDurationToZonedDateTime(context, relativeToOption, durationRecord);
            if (context.hasPendingException() || zonedDateTimeComputation == null) {
                return null;
            }
            unroundedEndDateTime = zonedDateTimeComputation.localDateTime();
            unroundedEndEpochNanoseconds = zonedDateTimeComputation.epochNanoseconds();
        } else {
            unroundedEndDateTime = addDurationToDateTime(context, startDateTime, durationRecord);
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

    private static RoundComputationResult roundWithoutRelativeTo(
            JSContext context,
            TemporalDuration durationRecord,
            RoundOptions roundOptions) {
        if (isRelativeToRequiredUnit(roundOptions.smallestUnit()) || isRelativeToRequiredUnit(roundOptions.largestUnit())) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        if (hasCalendarUnits(durationRecord)) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        BigInteger totalNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
        BigInteger roundingUnitNanoseconds = unitToNanosecondsBigInteger(roundOptions.smallestUnit());
        BigInteger incrementNanoseconds = roundingUnitNanoseconds.multiply(BigInteger.valueOf(roundOptions.roundingIncrement()));
        BigInteger roundedNanoseconds = roundBigIntegerToIncrement(totalNanoseconds, incrementNanoseconds, roundOptions.roundingMode());
        DurationFieldOverrides durationFieldOverrides = null;
        String balancingLargestUnit = roundOptions.largestUnit();
        if ("millisecond".equals(roundOptions.largestUnit())) {
            BigInteger millisecondValue = roundedNanoseconds.divide(MILLISECOND_NANOSECONDS);
            if (millisecondValue.abs().compareTo(MAX_FLOAT64_MILLISECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger microsecondRemainder = roundedNanoseconds.remainder(MILLISECOND_NANOSECONDS).divide(MICROSECOND_NANOSECONDS);
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new DurationFieldOverrides(
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
            durationFieldOverrides = new DurationFieldOverrides(
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
            durationFieldOverrides = new DurationFieldOverrides(
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
        return new RoundComputationResult(durationResult, durationFieldOverrides);
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
        if (duration == null) return JSUndefined.INSTANCE;
        return JSNumber.of(duration.getDuration().seconds());
    }

    public static JSValue sign(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "sign");
        if (duration == null) return JSUndefined.INSTANCE;
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
        if (duration == null) return JSUndefined.INSTANCE;
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

    private static IsoDateTime toIsoDateTime(LocalDateTime localDateTime) {
        int nanosecondOfSecond = localDateTime.getNano();
        int millisecond = nanosecondOfSecond / 1_000_000;
        int microsecond = (nanosecondOfSecond / 1_000) % 1_000;
        int nanosecond = nanosecondOfSecond % 1_000;
        IsoDate isoDate = new IsoDate(
                localDateTime.getYear(),
                localDateTime.getMonthValue(),
                localDateTime.getDayOfMonth());
        IsoTime isoTime = new IsoTime(
                localDateTime.getHour(),
                localDateTime.getMinute(),
                localDateTime.getSecond(),
                millisecond,
                microsecond,
                nanosecond);
        return new IsoDateTime(isoDate, isoTime);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "toJSON");
        if (duration == null) return JSUndefined.INSTANCE;
        return new JSString(duration.getDuration().toString());
    }

    private static LocalDateTime toLocalDateTime(IsoDate isoDate, IsoTime isoTime) {
        return LocalDateTime.of(
                isoDate.year(),
                isoDate.month(),
                isoDate.day(),
                isoTime.hour(),
                isoTime.minute(),
                isoTime.second(),
                isoTime.millisecond() * 1_000_000
                        + isoTime.microsecond() * 1_000
                        + isoTime.nanosecond());
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
        DurationToStringOptions options = parseDurationToStringOptions(context, optionsValue);
        if (context.hasPendingException() || options == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = duration.getDuration();
        BigInteger dayTimeNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
        BigInteger incrementNanoseconds = BigInteger.valueOf(options.roundingIncrementNanoseconds());
        BigInteger roundedDayTimeNanoseconds =
                roundBigIntegerToIncrement(dayTimeNanoseconds, incrementNanoseconds, options.roundingMode());
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
        RelativeToOption relativeToOption = null;
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
            RelativeToOption relativeToOption,
            BigInteger endEpochNanoseconds) {
        boolean zoned = relativeToOption != null && relativeToOption.zoned();
        if (startDateTime.equals(endDateTime)) {
            return 0D;
        }
        UnitStepResult stepResult = moveByWholeCalendarUnits(startDateTime, endDateTime, calendarUnit);
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
        LocalDateTime nextBoundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, wholeUnits + direction);
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
            RelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        if (!relativeToOption.zoned()
                && !durationRecord.isBlank()
                && (isTimeUnit(unit) || hasTimeUnits(durationRecord))
                && !isDateTimeWithinTemporalRange(startDateTime)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return Double.NaN;
        }
        if (relativeToOption.zoned() && !hasCalendarUnits(durationRecord)) {
            BigInteger dayTimeNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
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
            ZonedDateTimeComputation zonedDateTimeComputation =
                    addDurationToZonedDateTime(context, relativeToOption, durationRecord);
            if (context.hasPendingException() || zonedDateTimeComputation == null) {
                return Double.NaN;
            }
            endDateTime = zonedDateTimeComputation.localDateTime();
            endEpochNanoseconds = zonedDateTimeComputation.epochNanoseconds();
        } else {
            endDateTime = addDurationToDateTime(context, startDateTime, durationRecord);
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
        BigInteger totalNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
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

    private static long unitToNanoseconds(String unit) {
        return switch (unit) {
            case "day", "days" -> 86_400_000_000_000L;
            case "hour", "hours" -> 3_600_000_000_000L;
            case "minute", "minutes" -> 60_000_000_000L;
            case "second", "seconds" -> 1_000_000_000L;
            case "millisecond", "milliseconds" -> 1_000_000L;
            case "microsecond", "microseconds" -> 1_000L;
            case "nanosecond", "nanoseconds" -> 1L;
            default -> 0;
        };
    }

    private static BigInteger unitToNanosecondsBigInteger(String unit) {
        return switch (unit) {
            case "day" -> DAY_NANOSECONDS;
            case "hour" -> HOUR_NANOSECONDS;
            case "minute" -> MINUTE_NANOSECONDS;
            case "second" -> SECOND_NANOSECONDS;
            case "millisecond" -> MILLISECOND_NANOSECONDS;
            case "microsecond" -> MICROSECOND_NANOSECONDS;
            case "nanosecond" -> BigInteger.ONE;
            case "week" -> WEEK_NANOSECONDS;
            default -> BigInteger.ZERO;
        };
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.Duration.prototype.valueOf; use Temporal.Duration.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weeks(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "weeks");
        if (duration == null) return JSUndefined.INSTANCE;
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

        PartialDurationFieldValue daysFieldValue = readPartialDurationField(context, fields, "days");
        if (context.hasPendingException() || daysFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue hoursFieldValue = readPartialDurationField(context, fields, "hours");
        if (context.hasPendingException() || hoursFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue microsecondsFieldValue = readPartialDurationField(context, fields, "microseconds");
        if (context.hasPendingException() || microsecondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue millisecondsFieldValue = readPartialDurationField(context, fields, "milliseconds");
        if (context.hasPendingException() || millisecondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue minutesFieldValue = readPartialDurationField(context, fields, "minutes");
        if (context.hasPendingException() || minutesFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue monthsFieldValue = readPartialDurationField(context, fields, "months");
        if (context.hasPendingException() || monthsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue nanosecondsFieldValue = readPartialDurationField(context, fields, "nanoseconds");
        if (context.hasPendingException() || nanosecondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue secondsFieldValue = readPartialDurationField(context, fields, "seconds");
        if (context.hasPendingException() || secondsFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue weeksFieldValue = readPartialDurationField(context, fields, "weeks");
        if (context.hasPendingException() || weeksFieldValue == null) {
            return JSUndefined.INSTANCE;
        }
        PartialDurationFieldValue yearsFieldValue = readPartialDurationField(context, fields, "years");
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
        if (duration == null) return JSUndefined.INSTANCE;
        return JSNumber.of(duration.getDuration().years());
    }

    private static BigInteger zonedLocalDateTimeToEpochNanoseconds(
            JSContext context,
            RelativeToOption relativeToOption,
            LocalDateTime localDateTime) {
        IsoDateTime isoDateTime = toIsoDateTime(localDateTime);
        try {
            return TemporalTimeZone.localDateTimeToEpochNs(
                    isoDateTime,
                    relativeToOption.timeZoneId(),
                    "compatible");
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
    }

    private record DurationFieldOverrides(
            double years,
            double months,
            double weeks,
            double days,
            double hours,
            double minutes,
            double seconds,
            double milliseconds,
            double microseconds,
            double nanoseconds) {
    }

    private record DurationToStringOptions(
            String smallestUnit,
            String roundingMode,
            long roundingIncrementNanoseconds,
            boolean precisionAuto,
            int fractionalSecondDigits) {
    }

    private record FractionalSecondDigitsOption(boolean auto, int digits) {
    }

    private record PartialDurationFieldValue(boolean present, long value) {
    }

    private record RelativeToOption(
            LocalDateTime startDateTime,
            boolean zoned,
            BigInteger epochNanoseconds,
            String timeZoneId,
            Integer offsetSeconds) {
    }

    private record RoundComputationResult(
            TemporalDuration durationRecord,
            DurationFieldOverrides durationFieldOverrides) {
    }

    private record RoundOptions(
            String smallestUnit,
            String largestUnit,
            long roundingIncrement,
            String roundingMode,
            RelativeToOption relativeToOption) {
    }

    private record UnitStepResult(long count, LocalDateTime boundaryDateTime) {
    }

    private record ZonedDateTimeComputation(
            LocalDateTime localDateTime,
            BigInteger epochNanoseconds,
            int offsetSeconds) {
    }
}
