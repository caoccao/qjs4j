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
import java.time.LocalDateTime;

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
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "abs");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, duration.getDuration().abs());
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "add");
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

        if (leftRecord.hasCalendarUnits() || rightRecord.hasCalendarUnits()) {
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

        TemporalUnit receiverLargestUnit = leftRecord.largestUnitOfDuration();
        TemporalUnit otherLargestUnit = rightRecord.largestUnitOfDuration();
        TemporalUnit largestUnit = receiverLargestUnit.coarserDurationUnit(otherLargestUnit);
        TemporalDuration balanced = TemporalDuration.createBalance(totalNanoseconds, largestUnit);
        TemporalDuration normalized = balanced.normalizeFloat64RepresentableFields();
        if (!TemporalDuration.isDurationRecordTimeRangeValid(normalized)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, normalized);
    }

    private static void applyDurationFieldOverrides(
            JSTemporalDuration duration,
            TemporalDurationDouble durationFieldOverrides) {
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
        TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(remainingTimeNanoseconds, TemporalUnit.HOUR);
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
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "blank");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return duration.getDuration().isBlank() ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static TemporalDuration buildBalancedDurationFromDateTimes(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            TemporalUnit largestUnit,
            TemporalUnit smallestUnit,
            TemporalRelativeToOption relativeToOption) {
        if (largestUnit.isTimeUnit()) {
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
            TemporalDuration timeDuration = TemporalDuration.createBalance(totalNanoseconds, largestUnit);
            return timeDuration;
        }

        long years = 0;
        long months = 0;
        long weeks = 0;
        long days = 0;
        LocalDateTime cursorDateTime = startDateTime;
        int smallestUnitRank = smallestUnit.rank();

        if (largestUnit == TemporalUnit.YEAR) {
            long yearStepCount = TemporalUnit.YEAR.moveByWholeCalendarUnits(
                    cursorDateTime,
                    endDateTime);
            years = yearStepCount;
            cursorDateTime = TemporalUnit.YEAR.addCalendarUnits(cursorDateTime, yearStepCount);
            if (smallestUnit == TemporalUnit.YEAR) {
                return new TemporalDuration(years, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if (largestUnit == TemporalUnit.YEAR || largestUnit == TemporalUnit.MONTH) {
            long monthStepCount = TemporalUnit.MONTH.moveByWholeCalendarUnits(
                    cursorDateTime,
                    endDateTime);
            months = monthStepCount;
            cursorDateTime = TemporalUnit.MONTH.addCalendarUnits(cursorDateTime, monthStepCount);
            if (smallestUnit == TemporalUnit.MONTH) {
                return new TemporalDuration(years, months, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        boolean shouldBalanceWeeks = largestUnit == TemporalUnit.WEEK || smallestUnit == TemporalUnit.WEEK;
        if (shouldBalanceWeeks) {
            long weekStepCount;
            if (relativeToOption != null && relativeToOption.zoned()) {
                weekStepCount = TemporalUnit.WEEK.moveByWholeCalendarUnitsWithRelativeTo(
                        context,
                        cursorDateTime,
                        endDateTime,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return null;
                }
                cursorDateTime = TemporalUnit.WEEK.addCalendarUnits(cursorDateTime, weekStepCount);
            } else {
                weekStepCount = TemporalUnit.WEEK.moveByWholeFixedUnits(
                        cursorDateTime,
                        endDateTime);
                cursorDateTime = TemporalUnit.WEEK.addFixedUnits(cursorDateTime, weekStepCount);
            }
            weeks = weekStepCount;
            if (smallestUnit == TemporalUnit.WEEK) {
                return new TemporalDuration(years, months, weeks, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if (smallestUnitRank >= TemporalUnit.DAY.rank()) {
            long dayStepCount;
            if (relativeToOption != null && relativeToOption.zoned()) {
                dayStepCount = TemporalUnit.DAY.moveByWholeCalendarUnitsWithRelativeTo(
                        context,
                        cursorDateTime,
                        endDateTime,
                        relativeToOption);
                if (context.hasPendingException()) {
                    return null;
                }
                cursorDateTime = TemporalUnit.DAY.addCalendarUnits(cursorDateTime, dayStepCount);
            } else {
                dayStepCount = TemporalUnit.DAY.moveByWholeFixedUnits(
                        cursorDateTime,
                        endDateTime);
                cursorDateTime = TemporalUnit.DAY.addFixedUnits(cursorDateTime, dayStepCount);
            }
            days = dayStepCount;
            if (smallestUnit == TemporalUnit.DAY) {
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
        TemporalDuration timeDuration = TemporalDuration.createBalance(remainingNanoseconds, TemporalUnit.HOUR);
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

    private static TemporalUnit canonicalizeDurationToStringSmallestUnit(JSContext context, String unitText) {
        TemporalUnit smallestUnit = TemporalUnit.fromString(unitText)
                .filter(unit -> !unit.isLargerThan(TemporalUnit.SECOND))
                .orElse(null);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid smallestUnit.");
        }
        return smallestUnit;
    }

    private static TemporalUnit canonicalizeTemporalDurationUnit(JSContext context, String unitText, String optionName) {
        TemporalUnit temporalUnit = TemporalUnit.fromString(unitText).orElse(null);
        if (temporalUnit == null) {
            context.throwRangeError("Temporal error: Invalid " + optionName + ".");
        }
        return temporalUnit;
    }

    public static JSValue days(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "days");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().days());
    }

    static TemporalDuration differencePlainDateTime(
            JSContext context,
            IsoDateTime startIsoDateTime,
            IsoDateTime endIsoDateTime,
            TemporalUnit largestUnit,
            TemporalUnit smallestUnit,
            long roundingIncrement,
            TemporalRoundingMode roundingMode) {
        LocalDateTime startDateTime = startIsoDateTime.toLocalDateTime();
        LocalDateTime endDateTime = endIsoDateTime.toLocalDateTime();

        TemporalDuration unroundedDuration = buildBalancedDurationFromDateTimes(
                context,
                startDateTime,
                endDateTime,
                largestUnit,
                TemporalUnit.NANOSECOND,
                null);
        if (context.hasPendingException() || unroundedDuration == null) {
            return null;
        }

        boolean requiresRounding = roundingIncrement != 1L || smallestUnit != TemporalUnit.NANOSECOND;
        if (!requiresRounding) {
            return unroundedDuration;
        }

        TemporalRelativeToOption relativeToOption = new TemporalRelativeToOption(startDateTime, false, null, null, null);
        TemporalRoundOptions roundOptions = new TemporalRoundOptions(
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
            TemporalUnit largestUnit,
            TemporalUnit smallestUnit,
            long roundingIncrement,
            TemporalRoundingMode roundingMode) {
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
                TemporalUnit.NANOSECOND,
                relativeToOption);
        if (context.hasPendingException() || unroundedDuration == null) {
            return null;
        }

        boolean requiresRounding = roundingIncrement != 1L || smallestUnit != TemporalUnit.NANOSECOND;
        if (!requiresRounding) {
            return unroundedDuration;
        }

        TemporalRoundOptions roundOptions = new TemporalRoundOptions(
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

    public static JSValue hours(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "hours");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().hours());
    }

    public static JSValue microseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "microseconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().microseconds());
    }

    public static JSValue milliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "milliseconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().milliseconds());
    }

    public static JSValue minutes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "minutes");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().minutes());
    }

    public static JSValue months(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "months");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().months());
    }

    public static JSValue nanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "nanoseconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().nanoseconds());
    }

    private static BigInteger nanosecondsBetween(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger knownStartEpochNanoseconds,
            LocalDateTime endDateTime,
            BigInteger knownEndEpochNanoseconds,
            TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return TemporalUtils.nanosecondsBetween(startDateTime, endDateTime);
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
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "negated");
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

        String roundingModeText = TemporalUtils.getStringOption(context, optionsObject, "roundingMode", "trunc");
        if (context.hasPendingException() || roundingModeText == null) {
            return null;
        }

        String smallestUnitText = TemporalUtils.getStringOption(context, optionsObject, "smallestUnit", null);
        if (context.hasPendingException()) {
            return null;
        }

        TemporalRoundingMode roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        TemporalUnit smallestUnit = null;
        if (smallestUnitText != null) {
            smallestUnit = canonicalizeDurationToStringSmallestUnit(context, smallestUnitText);
            if (context.hasPendingException()) {
                return null;
            }
        }

        boolean precisionAuto;
        int fractionalSecondDigits;
        long roundingIncrementNanoseconds;
        TemporalUnit smallestUnitForOptions;
        if (smallestUnit != null) {
            precisionAuto = false;
            fractionalSecondDigits = smallestUnit.getStringFractionalSecondDigits();
            roundingIncrementNanoseconds = roundingIncrementNanosecondsForSmallestUnit(smallestUnit);
            smallestUnitForOptions = smallestUnit;
        } else if (fractionalSecondDigitsOption.auto()) {
            precisionAuto = true;
            fractionalSecondDigits = -1;
            smallestUnitForOptions = TemporalUnit.NANOSECOND;
            roundingIncrementNanoseconds = 1L;
        } else {
            precisionAuto = false;
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
            smallestUnitForOptions = smallestUnitFromFractionalSecondDigits(fractionalSecondDigits);
            roundingIncrementNanoseconds = fractionalSecondDigitsOption.roundingIncrementNanoseconds();
        }

        return new TemporalDurationToStringOptions(
                smallestUnitForOptions,
                roundingMode,
                roundingIncrementNanoseconds,
                precisionAuto,
                fractionalSecondDigits);
    }

    private static TemporalRoundOptions parseRoundOptions(JSContext context, JSValue roundToArg, TemporalDuration durationRecord) {
        String largestUnitText = null;
        String smallestUnitText = null;
        TemporalRelativeToOption relativeToOption = null;
        boolean largestUnitProvided = false;
        long roundingIncrement = 1L;
        TemporalRoundingMode roundingMode = TemporalRoundingMode.HALF_EXPAND;

        if (roundToArg instanceof JSString smallestUnitStringValue) {
            smallestUnitText = smallestUnitStringValue.value();
        } else if (roundToArg instanceof JSObject optionsObject) {
            largestUnitText = TemporalUtils.getStringOption(context, optionsObject, "largestUnit", null);
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

            String roundingModeText = TemporalUtils.getStringOption(context, optionsObject, "roundingMode", "halfExpand");
            if (context.hasPendingException() || roundingModeText == null) {
                return null;
            }
            roundingMode = TemporalRoundingMode.fromString(roundingModeText);
            if (roundingMode == null) {
                context.throwRangeError("Temporal error: Invalid rounding mode.");
                return null;
            }

            smallestUnitText = TemporalUtils.getStringOption(context, optionsObject, "smallestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return null;
        }

        TemporalUnit canonicalLargestUnit = null;
        if (largestUnitText != null) {
            if (!"auto".equals(largestUnitText)) {
                canonicalLargestUnit = canonicalizeTemporalDurationUnit(context, largestUnitText, "largestUnit");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }

        TemporalUnit canonicalSmallestUnit = null;
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
            canonicalSmallestUnit = TemporalUnit.NANOSECOND;
        }
        TemporalUnit smallestRoundUnit = canonicalSmallestUnit;
        TemporalUnit largestRoundUnit;
        if (canonicalLargestUnit == null) {
            largestRoundUnit = smallestRoundUnit.coarserDurationUnit(durationRecord.largestUnitOfDuration());
        } else {
            largestRoundUnit = canonicalLargestUnit;
        }

        if (smallestRoundUnit.rank() < largestRoundUnit.rank()) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        if (!canonicalSmallestUnit.isValidIncrement(roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        if (roundingIncrement != 1
                && smallestRoundUnit.isDateUnit()
                && largestRoundUnit.rank() < smallestRoundUnit.rank()) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        return new TemporalRoundOptions(
                smallestRoundUnit,
                largestRoundUnit,
                roundingIncrement,
                roundingMode,
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
        if (TemporalUtils.isOffsetTimeZoneIdentifier(relativeToReference.timeZoneId())) {
            int offsetSeconds = TemporalUtils.parseOffsetSecondsFromTimeZoneId(relativeToReference.timeZoneId());
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
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "round");
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
                durationRecord.hasCalendarUnits()
                        || roundOptions.smallestUnit().requiresRelativeTo()
                        || roundOptions.largestUnit().requiresRelativeTo();
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
                roundComputationResult.durationRecord().normalizeFloat64RepresentableFields();
        if (!normalizedDurationRecord.isValid() || !TemporalDuration.isDurationRecordTimeRangeValid(normalizedDurationRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        JSTemporalDuration roundedDuration = TemporalDurationConstructor.createDuration(context, normalizedDurationRecord);
        TemporalDurationDouble durationFieldOverrides = roundComputationResult.durationFieldOverrides();
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
        TemporalUnit smallestUnit = roundOptions.smallestUnit();
        TemporalUnit largestUnit = roundOptions.largestUnit();
        long roundingIncrement = roundOptions.roundingIncrement();
        TemporalRoundingMode roundingMode = roundOptions.roundingMode();

        if (smallestUnit == TemporalUnit.YEAR || smallestUnit == TemporalUnit.MONTH) {
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

        if (smallestUnit == TemporalUnit.WEEK
                && (largestUnit == TemporalUnit.YEAR
                || largestUnit == TemporalUnit.MONTH
                || largestUnit == TemporalUnit.WEEK)) {
            LocalDateTime weekAnchorDateTime = startDateTime;
            if (largestUnit == TemporalUnit.YEAR) {
                long yearStepCount = TemporalUnit.YEAR.moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime);
                weekAnchorDateTime = TemporalUnit.YEAR.addCalendarUnits(weekAnchorDateTime, yearStepCount);
                long monthStepCount = TemporalUnit.MONTH.moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime);
                weekAnchorDateTime = TemporalUnit.MONTH.addCalendarUnits(weekAnchorDateTime, monthStepCount);
            } else if (largestUnit == TemporalUnit.MONTH) {
                long monthStepCount = TemporalUnit.MONTH.moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime);
                weekAnchorDateTime = TemporalUnit.MONTH.addCalendarUnits(weekAnchorDateTime, monthStepCount);
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
                    roundingMode.roundBigIntegerToIncrementSigned(weekRemainderNanoseconds, weekIncrementNanoseconds);
            return IsoDateTime.createFromLocalDateTime(weekAnchorDateTime).addNanosecondsToDateTime(
                    context,
                    roundedWeekRemainderNanoseconds,
                    relativeToOption);
        }

        if (relativeToOption.zoned() && largestUnit == TemporalUnit.DAY && smallestUnit.isTimeUnit()) {
            BigInteger nextDayEpochNanoseconds = relativeToOption.epochNanoseconds().add(DAY_NANOSECONDS);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(nextDayEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        if (relativeToOption.zoned()
                && smallestUnit.isTimeUnit()
                && !durationRecord.hasAnyDateUnits()
                && !largestUnit.isTimeUnit()) {
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
            BigInteger roundedTimeNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(
                    balancedDurationWithoutRounding.timeNanoseconds(),
                    unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement)));
            BigInteger beyondDaySpanNanoseconds = roundedTimeNanoseconds.subtract(daySpanNanoseconds);
            boolean roundedBeyondOneDay = beyondDaySpanNanoseconds.signum() != -durationSign;
            if (roundedBeyondOneDay) {
                roundedTimeNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(
                        beyondDaySpanNanoseconds,
                        unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement)));
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
                && smallestUnit.isTimeUnit()
                && durationRecord.hasAnyDateUnits()
                && !largestUnit.isTimeUnit()) {
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
                    roundingMode.roundBigIntegerToIncrementSigned(timeRemainderNanoseconds, incrementNanoseconds);
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
        if (relativeToOption.zoned() && smallestUnit == TemporalUnit.DAY) {
            return roundDateTimeToCalendarUnit(
                    context,
                    startDateTime,
                    unroundedEndDateTime,
                    unroundedEndEpochNanoseconds,
                    TemporalUnit.DAY,
                    roundingIncrement,
                    roundingMode,
                    relativeToOption);
        } else if (smallestUnit == TemporalUnit.WEEK) {
            incrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
        } else {
            incrementNanoseconds = unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement));
        }
        BigInteger roundedNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(totalNanoseconds, incrementNanoseconds);
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
            TemporalUnit calendarUnit,
            long roundingIncrement,
            TemporalRoundingMode roundingMode,
            TemporalRelativeToOption relativeToOption) {
        long truncatedCount = calendarUnit.moveByWholeCalendarUnits(startDateTime, endDateTime);
        if (relativeToOption != null && relativeToOption.zoned()) {
            truncatedCount = calendarUnit.moveByWholeCalendarUnitsWithRelativeTo(
                    context,
                    startDateTime,
                    endDateTime,
                    relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
        }
        LocalDateTime truncatedBoundaryDateTime = calendarUnit.addCalendarUnits(startDateTime, truncatedCount);

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

        LocalDateTime lowerBoundaryDateTime = calendarUnit.addCalendarUnits(startDateTime, lowerMultipleCount);
        LocalDateTime upperBoundaryDateTime = calendarUnit.addCalendarUnits(startDateTime, upperMultipleCount);
        if (!TemporalUtils.isDateWithinTemporalRange(lowerBoundaryDateTime.toLocalDate())
                || !TemporalUtils.isDateWithinTemporalRange(upperBoundaryDateTime.toLocalDate())) {
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
            case CEIL:
                return upperBoundaryDateTime;
            case FLOOR:
                return lowerBoundaryDateTime;
            case TRUNC:
                if (direction < 0) {
                    return upperBoundaryDateTime;
                } else {
                    return lowerBoundaryDateTime;
                }
            case EXPAND:
                if (direction < 0) {
                    return lowerBoundaryDateTime;
                } else {
                    return upperBoundaryDateTime;
                }
            case HALF_EVEN:
            case HALF_EXPAND:
            case HALF_TRUNC:
            case HALF_CEIL:
            case HALF_FLOOR:
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
                    case HALF_EVEN -> {
                        if (Math.floorMod(lowerMultipleCount / roundingIncrement, 2L) == 0L) {
                            yield lowerBoundaryDateTime;
                        } else {
                            yield upperBoundaryDateTime;
                        }
                    }
                    case HALF_EXPAND -> {
                        if (direction < 0) {
                            yield lowerBoundaryDateTime;
                        } else {
                            yield upperBoundaryDateTime;
                        }
                    }
                    case HALF_TRUNC -> {
                        if (direction < 0) {
                            yield upperBoundaryDateTime;
                        } else {
                            yield lowerBoundaryDateTime;
                        }
                    }
                    case HALF_CEIL -> upperBoundaryDateTime;
                    case HALF_FLOOR -> lowerBoundaryDateTime;
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
                && (roundOptions.smallestUnit().isTimeUnit() || durationRecord.hasTimeUnits())
                && !TemporalUtils.isDateTimeWithinTemporalRange(startDateTime)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        if (relativeToOption.zoned() && !durationRecord.hasCalendarUnits()) {
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
            if (!TemporalUtils.isDateTimeWithinTemporalRange(unroundedEndDateTime)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        } else {
            if (!TemporalUtils.isDateWithinTemporalRange(unroundedEndDateTime.toLocalDate())) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        boolean noRoundingRequested = roundOptions.roundingIncrement() == 1L
                && roundOptions.smallestUnit() == TemporalUnit.NANOSECOND;
        if (relativeToOption.zoned()
                && noRoundingRequested
                && durationRecord.months() == 0L
                && durationRecord.weeks() == 0L
                && durationRecord.days() == 0L
                && !roundOptions.largestUnit().isTimeUnit()) {
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

        if (relativeToOption.zoned() && roundOptions.largestUnit().isTimeUnit()) {
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
            TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(
                    roundedNanoseconds,
                    roundOptions.largestUnit());
            return balancedTimeDuration;
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
        if (roundOptions.smallestUnit().requiresRelativeTo() || roundOptions.largestUnit().requiresRelativeTo()) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        if (durationRecord.hasCalendarUnits()) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger roundingUnitNanoseconds = unitToNanosecondsBigInteger(roundOptions.smallestUnit());
        BigInteger incrementNanoseconds = roundingUnitNanoseconds.multiply(BigInteger.valueOf(roundOptions.roundingIncrement()));
        BigInteger roundedNanoseconds = roundOptions.roundingMode().roundBigIntegerToIncrementSigned(totalNanoseconds, incrementNanoseconds);
        TemporalDurationDouble durationFieldOverrides = null;
        TemporalUnit balancingLargestUnit = roundOptions.largestUnit();
        if (roundOptions.largestUnit() == TemporalUnit.MILLISECOND) {
            BigInteger millisecondValue = roundedNanoseconds.divide(MILLISECOND_NANOSECONDS);
            if (millisecondValue.abs().compareTo(MAX_FLOAT64_MILLISECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger microsecondRemainder = roundedNanoseconds.remainder(MILLISECOND_NANOSECONDS).divide(MICROSECOND_NANOSECONDS);
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new TemporalDurationDouble(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    millisecondValue.doubleValue(),
                    microsecondRemainder.doubleValue(),
                    nanosecondRemainder.doubleValue());
            if (millisecondValue.abs().bitLength() > 63) {
                balancingLargestUnit = TemporalUnit.SECOND;
            }
        } else if (roundOptions.largestUnit() == TemporalUnit.MICROSECOND) {
            BigInteger microsecondValue = roundedNanoseconds.divide(MICROSECOND_NANOSECONDS);
            if (microsecondValue.abs().compareTo(MAX_FLOAT64_MICROSECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new TemporalDurationDouble(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    0D,
                    microsecondValue.doubleValue(),
                    nanosecondRemainder.doubleValue());
            if (microsecondValue.abs().bitLength() > 63) {
                balancingLargestUnit = TemporalUnit.SECOND;
            }
        } else if (roundOptions.largestUnit() == TemporalUnit.NANOSECOND) {
            if (roundedNanoseconds.abs().compareTo(MAX_FLOAT64_NANOSECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            durationFieldOverrides = new TemporalDurationDouble(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    0D, 0D,
                    roundedNanoseconds.doubleValue());
            if (roundedNanoseconds.abs().bitLength() > 63) {
                balancingLargestUnit = TemporalUnit.SECOND;
            }
        }

        TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(roundedNanoseconds, balancingLargestUnit);
        return new TemporalRoundComputationResult(balancedTimeDuration, durationFieldOverrides);
    }

    private static long roundingIncrementNanosecondsForSmallestUnit(TemporalUnit smallestUnit) {
        return switch (smallestUnit) {
            case SECOND -> 1_000_000_000L;
            case MILLISECOND -> 1_000_000L;
            case MICROSECOND -> 1_000L;
            default -> 1L;
        };
    }

    public static JSValue seconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "seconds");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().seconds());
    }

    public static JSValue sign(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "sign");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().sign());
    }

    private static TemporalUnit smallestUnitFromFractionalSecondDigits(int fractionalSecondDigits) {
        if (fractionalSecondDigits == 0) {
            return TemporalUnit.SECOND;
        } else if (fractionalSecondDigits <= 3) {
            return TemporalUnit.MILLISECOND;
        } else if (fractionalSecondDigits <= 6) {
            return TemporalUnit.MICROSECOND;
        } else {
            return TemporalUnit.NANOSECOND;
        }
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "subtract");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, duration, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "toJSON");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(duration.getDuration().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "toLocaleString");
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
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "toString");
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
                options.roundingMode().roundBigIntegerToIncrementSigned(dayTimeNanoseconds, incrementNanoseconds);
        if (roundedDayTimeNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        TemporalUnit largestExistingDayTimeUnit = durationRecord.largestDayTimeUnit();
        TemporalUnit smallestBalancingUnit = options.smallestUnit();
        TemporalUnit largestBalancingUnit = largestExistingDayTimeUnit.coarserDurationUnit(smallestBalancingUnit);
        TemporalDuration balancedDayTime = TemporalDuration.createBalance(roundedDayTimeNanoseconds, largestBalancingUnit);
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
                || !TemporalDuration.isDurationRecordTimeRangeValid(roundedDurationRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        String formattedDuration = roundedDurationRecord.formatWithPrecision(options);
        return new JSString(formattedDuration);
    }

    public static JSValue total(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "total");
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

        TemporalUnit unit = canonicalizeTemporalDurationUnit(context, unitText, "unit");
        if (context.hasPendingException() || unit == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = duration.getDuration();
        boolean requiresRelativeTo = durationRecord.hasCalendarUnits() || unit.requiresRelativeTo();
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
            TemporalUnit calendarUnit,
            TemporalRelativeToOption relativeToOption,
            BigInteger endEpochNanoseconds) {
        boolean zoned = relativeToOption != null && relativeToOption.zoned();
        if (startDateTime.equals(endDateTime)) {
            return 0D;
        }
        long wholeUnits = calendarUnit.moveByWholeCalendarUnits(startDateTime, endDateTime);
        LocalDateTime boundaryDateTime = calendarUnit.addCalendarUnits(startDateTime, wholeUnits);
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
        LocalDateTime nextBoundaryDateTime = calendarUnit.addCalendarUnits(startDateTime, wholeUnits + direction);
        if (zoned) {
            if (!TemporalUtils.isDateTimeWithinTemporalRange(nextBoundaryDateTime)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        } else {
            if (!TemporalUtils.isDateWithinTemporalRange(nextBoundaryDateTime.toLocalDate())) {
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
            TemporalUnit unit,
            TemporalRelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        if (!relativeToOption.zoned()
                && !durationRecord.isBlank()
                && (unit.isTimeUnit() || durationRecord.hasTimeUnits())
                && !TemporalUtils.isDateTimeWithinTemporalRange(startDateTime)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return Double.NaN;
        }
        if (relativeToOption.zoned() && !durationRecord.hasCalendarUnits()) {
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
            if (!TemporalUtils.isDateTimeWithinTemporalRange(endDateTime)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        } else {
            if (!TemporalUtils.isDateWithinTemporalRange(endDateTime.toLocalDate())) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        }

        if (relativeToOption.zoned() && unit == TemporalUnit.DAY) {
            BigInteger nextDayEpochNanoseconds = relativeToOption.epochNanoseconds().add(DAY_NANOSECONDS);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(nextDayEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        }

        if (unit.isDateUnit()) {
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
            totalNanoseconds = TemporalUtils.nanosecondsBetween(startDateTime, endDateTime);
        }
        BigInteger divisorNanoseconds = unitDivisorNanosecondsForTotal(unit);
        return divideBigIntegersToDouble(totalNanoseconds, divisorNanoseconds);
    }

    private static double totalWithoutRelativeTo(
            JSContext context,
            TemporalDuration durationRecord,
            TemporalUnit unit) {
        if (unit.requiresRelativeTo()) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return Double.NaN;
        }
        if (durationRecord.hasCalendarUnits()) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return Double.NaN;
        }
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger divisorNanoseconds = unitDivisorNanosecondsForTotal(unit);
        return divideBigIntegersToDouble(totalNanoseconds, divisorNanoseconds);
    }

    private static BigInteger unitDivisorNanosecondsForTotal(TemporalUnit unit) {
        return switch (unit) {
            case WEEK -> WEEK_NANOSECONDS;
            case DAY -> DAY_NANOSECONDS;
            case HOUR -> HOUR_NANOSECONDS;
            case MINUTE -> MINUTE_NANOSECONDS;
            case SECOND -> SECOND_NANOSECONDS;
            case MILLISECOND -> MILLISECOND_NANOSECONDS;
            case MICROSECOND -> MICROSECOND_NANOSECONDS;
            case NANOSECOND -> BigInteger.ONE;
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

    private static BigInteger unitToNanosecondsBigInteger(TemporalUnit unit) {
        return switch (unit) {
            case WEEK -> WEEK_NANOSECONDS;
            case DAY -> DAY_NANOSECONDS;
            case HOUR -> HOUR_NANOSECONDS;
            case MINUTE -> MINUTE_NANOSECONDS;
            case SECOND -> SECOND_NANOSECONDS;
            case MILLISECOND -> MILLISECOND_NANOSECONDS;
            case MICROSECOND -> MICROSECOND_NANOSECONDS;
            case NANOSECOND -> BigInteger.ONE;
            default -> BigInteger.ZERO;
        };
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.Duration.prototype.valueOf; use Temporal.Duration.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weeks(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "weeks");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().weeks());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "with");
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
        JSTemporalDuration duration = TemporalUtils.checkReceiver(context, thisArg, JSTemporalDuration.class, TYPE_NAME, "years");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(duration.getDuration().years());
    }

}
