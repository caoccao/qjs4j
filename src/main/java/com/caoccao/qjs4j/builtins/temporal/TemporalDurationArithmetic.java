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
import java.time.LocalDateTime;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.time.DateTimeException;

/** Calendar and elapsed-time arithmetic used by Temporal differences, rounding, and totals. */
final class TemporalDurationArithmetic {
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;

    private static final BigInteger MAX_FLOAT64_MICROSECONDS_COMPONENT = new BigInteger("9007199254740991475711");
    private static final BigInteger MAX_FLOAT64_MILLISECONDS_COMPONENT = new BigInteger("9007199254740991487");
    private static final BigInteger MAX_FLOAT64_NANOSECONDS_COMPONENT = new BigInteger("9007199254740991463129087");
    private static final BigInteger MICROSECOND_NANOSECONDS = TemporalConstants.BI_MICROSECOND_NANOSECONDS;
    private static final BigInteger MILLISECOND_NANOSECONDS = TemporalConstants.BI_MILLISECOND_NANOSECONDS;
    private static final BigInteger WEEK_NANOSECONDS = TemporalConstants.BI_WEEK_NANOSECONDS;
    private TemporalDurationArithmetic() {
    }
    private static TemporalDuration balanceZonedDurationWithoutRounding(JSContext context,
            TemporalDuration durationRecord, TemporalRelativeToOption relativeToOption) {
        LocalDateTime dayBalanceAnchorDateTime = relativeToOption.startDateTime().plusYears(durationRecord.years())
                .plusMonths(durationRecord.months()).plusWeeks(durationRecord.weeks()).plusDays(durationRecord.days());
        BigInteger remainingTimeNanoseconds = durationRecord.timeNanoseconds();
        long additionalDayCount = 0L;
        while (remainingTimeNanoseconds.signum() != 0) {
            int direction = remainingTimeNanoseconds.signum() < 0 ? -1 : 1;
            LocalDateTime adjacentDayDateTime = dayBalanceAnchorDateTime.plusDays(direction);
            BigInteger oneDayNanoseconds = TemporalUtils.nanosecondsBetween(context, dayBalanceAnchorDateTime, null,
                    adjacentDayDateTime, null, relativeToOption).abs();
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
        TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(remainingTimeNanoseconds,
                TemporalUnit.HOUR);
        long balancedDayCount;
        try {
            balancedDayCount = Math.addExact(durationRecord.days(), additionalDayCount);
            balancedDayCount = Math.addExact(balancedDayCount, balancedTimeDuration.days());
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new TemporalDuration(durationRecord.years(), durationRecord.months(), durationRecord.weeks(),
                balancedDayCount, balancedTimeDuration.hours(), balancedTimeDuration.minutes(),
                balancedTimeDuration.seconds(), balancedTimeDuration.milliseconds(),
                balancedTimeDuration.microseconds(), balancedTimeDuration.nanoseconds());
    }

    private static TemporalDuration buildBalancedDurationFromDateTimes(JSContext context, LocalDateTime startDateTime,
            LocalDateTime endDateTime, TemporalUnit largestUnit, TemporalUnit smallestUnit,
            TemporalRelativeToOption relativeToOption) {
        if (largestUnit.isTimeUnit()) {
            BigInteger totalNanoseconds = TemporalUtils.nanosecondsBetween(context, startDateTime, null, endDateTime,
                    null, relativeToOption);
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
            long yearStepCount = TemporalUnit.YEAR.moveByWholeCalendarUnits(cursorDateTime, endDateTime);
            years = yearStepCount;
            cursorDateTime = TemporalUnit.YEAR.addCalendarUnits(cursorDateTime, yearStepCount);
            if (smallestUnit == TemporalUnit.YEAR) {
                return new TemporalDuration(years, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if (largestUnit == TemporalUnit.YEAR || largestUnit == TemporalUnit.MONTH) {
            long monthStepCount = TemporalUnit.MONTH.moveByWholeCalendarUnits(cursorDateTime, endDateTime);
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
                weekStepCount = TemporalUnit.WEEK.moveByWholeCalendarUnitsWithRelativeTo(context, cursorDateTime,
                        endDateTime, relativeToOption);
                if (context.hasPendingException()) {
                    return null;
                }
                cursorDateTime = TemporalUnit.WEEK.addCalendarUnits(cursorDateTime, weekStepCount);
            } else {
                weekStepCount = TemporalUnit.WEEK.moveByWholeFixedUnits(cursorDateTime, endDateTime);
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
                dayStepCount = TemporalUnit.DAY.moveByWholeCalendarUnitsWithRelativeTo(context, cursorDateTime,
                        endDateTime, relativeToOption);
                if (context.hasPendingException()) {
                    return null;
                }
                cursorDateTime = TemporalUnit.DAY.addCalendarUnits(cursorDateTime, dayStepCount);
            } else {
                dayStepCount = TemporalUnit.DAY.moveByWholeFixedUnits(cursorDateTime, endDateTime);
                cursorDateTime = TemporalUnit.DAY.addFixedUnits(cursorDateTime, dayStepCount);
            }
            days = dayStepCount;
            if (smallestUnit == TemporalUnit.DAY) {
                return new TemporalDuration(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
            }
        }

        BigInteger remainingNanoseconds = TemporalUtils.nanosecondsBetween(context, cursorDateTime, null, endDateTime,
                null, relativeToOption);
        if (context.hasPendingException()) {
            return null;
        }
        TemporalDuration timeDuration = TemporalDuration.createBalance(remainingNanoseconds, TemporalUnit.HOUR);
        TemporalDuration durationRecord = new TemporalDuration(years, months, weeks, days, timeDuration.hours(),
                timeDuration.minutes(), timeDuration.seconds(), timeDuration.milliseconds(),
                timeDuration.microseconds(), timeDuration.nanoseconds());
        if (!durationRecord.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return null;
        }
        return durationRecord;
    }

    private static TemporalDuration difference(JSContext context, LocalDateTime startDateTime,
            LocalDateTime endDateTime, BigInteger endEpochNanoseconds, TemporalUnit largestUnit,
            TemporalUnit smallestUnit, long roundingIncrement, TemporalRoundingMode roundingMode,
            TemporalRelativeToOption relativeToOption) {
        TemporalDuration unroundedDuration = buildBalancedDurationFromDateTimes(context, startDateTime, endDateTime,
                largestUnit, TemporalUnit.NANOSECOND, relativeToOption);
        if (context.hasPendingException() || unroundedDuration == null) {
            return null;
        }

        boolean requiresRounding = roundingIncrement != 1L || smallestUnit != TemporalUnit.NANOSECOND;
        if (!requiresRounding) {
            return unroundedDuration;
        }

        TemporalRelativeToOption anchor = relativeToOption != null
                ? relativeToOption
                : new TemporalRelativeToOption(startDateTime, false, null, null, null);
        TemporalRoundOptions roundOptions = new TemporalRoundOptions(smallestUnit, largestUnit, roundingIncrement,
                roundingMode, anchor);
        LocalDateTime roundedEndDateTime = roundDateTimeDifference(context, startDateTime, endDateTime,
                endEpochNanoseconds, roundOptions, anchor, unroundedDuration);
        if (context.hasPendingException() || roundedEndDateTime == null) {
            return null;
        }

        return buildBalancedDurationFromDateTimes(context, startDateTime, roundedEndDateTime, largestUnit, smallestUnit,
                relativeToOption);
    }

    static TemporalDuration differencePlainDateTime(JSContext context, IsoDateTime startIsoDateTime,
            IsoDateTime endIsoDateTime, TemporalUnit largestUnit, TemporalUnit smallestUnit, long roundingIncrement,
            TemporalRoundingMode roundingMode) {
        LocalDateTime startDateTime = startIsoDateTime.toLocalDateTime();
        LocalDateTime endDateTime = endIsoDateTime.toLocalDateTime();

        return difference(context, startDateTime, endDateTime, null, largestUnit, smallestUnit, roundingIncrement,
                roundingMode, null);
    }

    static TemporalDuration differenceZonedDateTime(JSContext context, BigInteger startEpochNanoseconds,
            BigInteger endEpochNanoseconds, String timeZoneId, TemporalUnit largestUnit, TemporalUnit smallestUnit,
            long roundingIncrement, TemporalRoundingMode roundingMode) {
        IsoDateTime startIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(startEpochNanoseconds, timeZoneId);
        IsoDateTime endIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(endEpochNanoseconds, timeZoneId);
        LocalDateTime startDateTime = startIsoDateTime.toLocalDateTime();
        LocalDateTime endDateTime = endIsoDateTime.toLocalDateTime();
        TemporalRelativeToOption relativeToOption = new TemporalRelativeToOption(startDateTime, true,
                startEpochNanoseconds, timeZoneId, null);

        return difference(context, startDateTime, endDateTime, endEpochNanoseconds, largestUnit, smallestUnit,
                roundingIncrement, roundingMode, relativeToOption);
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
        BigDecimal quotient = new BigDecimal(normalizedNumerator).divide(new BigDecimal(normalizedDivisor),
                java.math.MathContext.DECIMAL128);
        return quotient.doubleValue();
    }

    private static DurationEndpoint endOfDuration(JSContext context, TemporalDuration durationRecord, TemporalUnit unit,
            TemporalRelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        if (!relativeToOption.zoned() && !durationRecord.isBlank()
                && (unit.isTimeUnit() || durationRecord.hasTimeUnits())
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
            if (!TemporalUtils.isValidEpochNanoseconds(endEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        LocalDateTime unroundedEndDateTime;
        BigInteger unroundedEndEpochNanoseconds = null;
        if (relativeToOption.zoned()) {
            TemporalZonedDateTimeComputation zonedDateTimeComputation = durationRecord
                    .addDurationToZonedDateTime(context, relativeToOption);
            if (context.hasPendingException() || zonedDateTimeComputation == null) {
                return null;
            }
            unroundedEndDateTime = zonedDateTimeComputation.localDateTime();
            unroundedEndEpochNanoseconds = zonedDateTimeComputation.epochNanoseconds();
        } else {
            unroundedEndDateTime = IsoDateTime.createFromLocalDateTime(startDateTime)
                    .addDurationToLocalDateTime(context, durationRecord);
        }
        if (context.hasPendingException()) {
            return null;
        }
        if (!withinRelativeRange(unroundedEndDateTime, relativeToOption)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new DurationEndpoint(unroundedEndDateTime, unroundedEndEpochNanoseconds);
    }

    private static LocalDateTime roundDateTimeDifference(JSContext context, LocalDateTime startDateTime,
            LocalDateTime unroundedEndDateTime, BigInteger unroundedEndEpochNanoseconds,
            TemporalRoundOptions roundOptions, TemporalRelativeToOption relativeToOption,
            TemporalDuration durationRecord) {
        TemporalUnit smallestUnit = roundOptions.smallestUnit();
        TemporalUnit largestUnit = roundOptions.largestUnit();
        long roundingIncrement = roundOptions.roundingIncrement();
        TemporalRoundingMode roundingMode = roundOptions.roundingMode();

        if (smallestUnit == TemporalUnit.YEAR || smallestUnit == TemporalUnit.MONTH) {
            return roundDateTimeToCalendarUnit(context, startDateTime, unroundedEndDateTime,
                    unroundedEndEpochNanoseconds, smallestUnit, roundingIncrement, roundingMode, relativeToOption);
        }

        if (smallestUnit == TemporalUnit.WEEK && (largestUnit == TemporalUnit.YEAR || largestUnit == TemporalUnit.MONTH
                || largestUnit == TemporalUnit.WEEK)) {
            LocalDateTime weekAnchorDateTime = startDateTime;
            if (largestUnit == TemporalUnit.YEAR) {
                long yearStepCount = TemporalUnit.YEAR.moveByWholeCalendarUnits(weekAnchorDateTime,
                        unroundedEndDateTime);
                weekAnchorDateTime = TemporalUnit.YEAR.addCalendarUnits(weekAnchorDateTime, yearStepCount);
                long monthStepCount = TemporalUnit.MONTH.moveByWholeCalendarUnits(weekAnchorDateTime,
                        unroundedEndDateTime);
                weekAnchorDateTime = TemporalUnit.MONTH.addCalendarUnits(weekAnchorDateTime, monthStepCount);
            } else if (largestUnit == TemporalUnit.MONTH) {
                long monthStepCount = TemporalUnit.MONTH.moveByWholeCalendarUnits(weekAnchorDateTime,
                        unroundedEndDateTime);
                weekAnchorDateTime = TemporalUnit.MONTH.addCalendarUnits(weekAnchorDateTime, monthStepCount);
            }
            BigInteger weekRemainderNanoseconds = TemporalUtils.nanosecondsBetween(context, weekAnchorDateTime, null,
                    unroundedEndDateTime, unroundedEndEpochNanoseconds, relativeToOption);
            BigInteger weekIncrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
            BigInteger roundedWeekRemainderNanoseconds = roundingMode
                    .roundBigIntegerToIncrementSigned(weekRemainderNanoseconds, weekIncrementNanoseconds);
            return IsoDateTime.createFromLocalDateTime(weekAnchorDateTime).addNanosecondsToDateTime(context,
                    roundedWeekRemainderNanoseconds, relativeToOption);
        }

        if (relativeToOption.zoned() && largestUnit == TemporalUnit.DAY && smallestUnit.isTimeUnit()) {
            BigInteger nextDayEpochNanoseconds = relativeToOption.epochNanoseconds().add(DAY_NANOSECONDS);
            if (!TemporalUtils.isValidEpochNanoseconds(nextDayEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        if (relativeToOption.zoned() && smallestUnit.isTimeUnit() && !durationRecord.hasAnyDateUnits()
                && !largestUnit.isTimeUnit()) {
            TemporalDuration balancedDurationWithoutRounding = balanceZonedDurationWithoutRounding(context,
                    durationRecord, relativeToOption);
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
            BigInteger daySpanNanoseconds = TemporalUtils.nanosecondsBetween(context, startAnchorDateTime, null,
                    endAnchorDateTime, null, relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
            BigInteger roundedTimeNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(
                    balancedDurationWithoutRounding.timeNanoseconds(),
                    smallestUnit.fixedNanoseconds().multiply(BigInteger.valueOf(roundingIncrement)));
            BigInteger beyondDaySpanNanoseconds = roundedTimeNanoseconds.subtract(daySpanNanoseconds);
            boolean roundedBeyondOneDay = beyondDaySpanNanoseconds.signum() != -durationSign;
            if (roundedBeyondOneDay) {
                roundedTimeNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(beyondDaySpanNanoseconds,
                        smallestUnit.fixedNanoseconds().multiply(BigInteger.valueOf(roundingIncrement)));
                return IsoDateTime.createFromLocalDateTime(endAnchorDateTime).addNanosecondsToDateTime(context,
                        roundedTimeNanoseconds, relativeToOption);
            }
            return IsoDateTime.createFromLocalDateTime(startAnchorDateTime).addNanosecondsToDateTime(context,
                    roundedTimeNanoseconds, relativeToOption);
        }

        if (relativeToOption.zoned() && smallestUnit.isTimeUnit() && durationRecord.hasAnyDateUnits()
                && !largestUnit.isTimeUnit()) {
            LocalDateTime dayAdjustedDateTime;
            try {
                dayAdjustedDateTime = startDateTime.plusYears(durationRecord.years())
                        .plusMonths(durationRecord.months()).plusWeeks(durationRecord.weeks())
                        .plusDays(durationRecord.days());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger timeRemainderNanoseconds = TemporalUtils.nanosecondsBetween(context, dayAdjustedDateTime, null,
                    unroundedEndDateTime, unroundedEndEpochNanoseconds, relativeToOption);
            BigInteger incrementNanoseconds = smallestUnit.fixedNanoseconds()
                    .multiply(BigInteger.valueOf(roundingIncrement));
            BigInteger roundedTimeRemainderNanoseconds = roundingMode
                    .roundBigIntegerToIncrementSigned(timeRemainderNanoseconds, incrementNanoseconds);
            return IsoDateTime.createFromLocalDateTime(dayAdjustedDateTime).addNanosecondsToDateTime(context,
                    roundedTimeRemainderNanoseconds, relativeToOption);
        }

        BigInteger totalNanoseconds = TemporalUtils.nanosecondsBetween(context, startDateTime, null,
                unroundedEndDateTime, unroundedEndEpochNanoseconds, relativeToOption);
        BigInteger incrementNanoseconds;
        if (relativeToOption.zoned() && smallestUnit == TemporalUnit.DAY) {
            return roundDateTimeToCalendarUnit(context, startDateTime, unroundedEndDateTime,
                    unroundedEndEpochNanoseconds, TemporalUnit.DAY, roundingIncrement, roundingMode, relativeToOption);
        } else if (smallestUnit == TemporalUnit.WEEK) {
            incrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
        } else {
            incrementNanoseconds = smallestUnit.fixedNanoseconds().multiply(BigInteger.valueOf(roundingIncrement));
        }
        BigInteger roundedNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(totalNanoseconds,
                incrementNanoseconds);
        return IsoDateTime.createFromLocalDateTime(startDateTime).addNanosecondsToDateTime(context, roundedNanoseconds,
                relativeToOption);
    }

    private static LocalDateTime roundDateTimeToCalendarUnit(JSContext context, LocalDateTime startDateTime,
            LocalDateTime endDateTime, BigInteger knownEndEpochNanoseconds, TemporalUnit calendarUnit,
            long roundingIncrement, TemporalRoundingMode roundingMode, TemporalRelativeToOption relativeToOption) {
        long truncatedCount = relativeToOption != null && relativeToOption.zoned()
                ? calendarUnit.moveByWholeCalendarUnitsWithRelativeTo(context, startDateTime, endDateTime,
                        relativeToOption)
                : calendarUnit.moveByWholeCalendarUnits(startDateTime, endDateTime);
        if (context.hasPendingException()) {
            return null;
        }
        LocalDateTime truncatedBoundaryDateTime = calendarUnit.addCalendarUnits(startDateTime, truncatedCount);

        int boundaryToEndDirection;
        if (relativeToOption != null && relativeToOption.zoned()) {
            BigInteger boundaryToEndNanoseconds = TemporalUtils.nanosecondsBetween(context, truncatedBoundaryDateTime,
                    null, endDateTime, knownEndEpochNanoseconds, relativeToOption);
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

        long lowerUnitCount = boundaryToEndDirection < 0 ? truncatedCount - 1 : truncatedCount;
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
            BigInteger startToEndNanoseconds = TemporalUtils.nanosecondsBetween(context, startDateTime, null,
                    endDateTime, knownEndEpochNanoseconds, relativeToOption);
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
        return switch (roundingMode) {
            case CEIL -> upperBoundaryDateTime;
            case FLOOR -> lowerBoundaryDateTime;
            case TRUNC -> direction < 0 ? upperBoundaryDateTime : lowerBoundaryDateTime;
            case EXPAND -> direction < 0 ? lowerBoundaryDateTime : upperBoundaryDateTime;
            default -> {
                BigInteger lowerDistance = TemporalUtils
                        .nanosecondsBetween(context, lowerBoundaryDateTime, null, endDateTime, null, relativeToOption)
                        .abs();
                BigInteger upperDistance = TemporalUtils
                        .nanosecondsBetween(context, endDateTime, null, upperBoundaryDateTime, null, relativeToOption)
                        .abs();
                int comparison = lowerDistance.compareTo(upperDistance);
                if (comparison != 0) {
                    yield comparison < 0 ? lowerBoundaryDateTime : upperBoundaryDateTime;
                }
                boolean chooseLower = switch (roundingMode) {
                    case HALF_EVEN -> (lowerMultipleCount / roundingIncrement) % 2 == 0;
                    case HALF_EXPAND -> direction < 0;
                    case HALF_TRUNC -> direction >= 0;
                    case HALF_FLOOR -> true;
                    case HALF_CEIL -> false;
                    default -> throw new IllegalStateException("Unexpected half rounding mode " + roundingMode);
                };
                yield chooseLower ? lowerBoundaryDateTime : upperBoundaryDateTime;
            }
        };
    }

    static TemporalRoundComputationResult roundWithoutRelativeTo(JSContext context, TemporalDuration durationRecord,
            TemporalRoundOptions roundOptions) {
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger roundingUnitNanoseconds = roundOptions.smallestUnit().fixedNanoseconds();
        BigInteger incrementNanoseconds = roundingUnitNanoseconds
                .multiply(BigInteger.valueOf(roundOptions.roundingIncrement()));
        BigInteger roundedNanoseconds = roundOptions.roundingMode().roundBigIntegerToIncrementSigned(totalNanoseconds,
                incrementNanoseconds);
        TemporalDurationDouble durationFieldOverrides = null;
        TemporalUnit balancingLargestUnit = roundOptions.largestUnit();
        if (roundOptions.largestUnit() == TemporalUnit.MILLISECOND) {
            BigInteger millisecondValue = roundedNanoseconds.divide(MILLISECOND_NANOSECONDS);
            if (millisecondValue.abs().compareTo(MAX_FLOAT64_MILLISECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger microsecondRemainder = roundedNanoseconds.remainder(MILLISECOND_NANOSECONDS)
                    .divide(MICROSECOND_NANOSECONDS);
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new TemporalDurationDouble(0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    millisecondValue.doubleValue(), microsecondRemainder.doubleValue(),
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
            durationFieldOverrides = new TemporalDurationDouble(0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    microsecondValue.doubleValue(), nanosecondRemainder.doubleValue());
            if (microsecondValue.abs().bitLength() > 63) {
                balancingLargestUnit = TemporalUnit.SECOND;
            }
        } else if (roundOptions.largestUnit() == TemporalUnit.NANOSECOND) {
            if (roundedNanoseconds.abs().compareTo(MAX_FLOAT64_NANOSECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            durationFieldOverrides = new TemporalDurationDouble(0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    roundedNanoseconds.doubleValue());
            if (roundedNanoseconds.abs().bitLength() > 63) {
                balancingLargestUnit = TemporalUnit.SECOND;
            }
        }

        TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(roundedNanoseconds,
                balancingLargestUnit);
        return new TemporalRoundComputationResult(balancedTimeDuration, durationFieldOverrides);
    }

    static TemporalDuration roundWithRelativeTo(JSContext context, TemporalDuration durationRecord,
            TemporalRoundOptions roundOptions, TemporalRelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        DurationEndpoint end = endOfDuration(context, durationRecord, roundOptions.smallestUnit(), relativeToOption);
        if (end == null) {
            return null;
        }
        boolean noRoundingRequested = roundOptions.roundingIncrement() == 1L
                && roundOptions.smallestUnit() == TemporalUnit.NANOSECOND;
        if (relativeToOption.zoned() && noRoundingRequested && durationRecord.months() == 0L
                && durationRecord.weeks() == 0L && durationRecord.days() == 0L
                && !roundOptions.largestUnit().isTimeUnit()) {
            return balanceZonedDurationWithoutRounding(context, durationRecord, relativeToOption);
        }

        LocalDateTime roundedEndDateTime = roundDateTimeDifference(context, startDateTime, end.localDateTime(),
                end.epochNanoseconds(), roundOptions, relativeToOption, durationRecord);
        if (context.hasPendingException()) {
            return null;
        }

        if (relativeToOption.zoned() && roundOptions.largestUnit().isTimeUnit()) {
            BigInteger roundedNanoseconds = TemporalUtils.nanosecondsBetween(context, startDateTime, null,
                    roundedEndDateTime, null, relativeToOption);
            if (context.hasPendingException()) {
                return null;
            }
            TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(roundedNanoseconds,
                    roundOptions.largestUnit());
            return balancedTimeDuration;
        }

        return buildBalancedDurationFromDateTimes(context, startDateTime, roundedEndDateTime,
                roundOptions.largestUnit(), roundOptions.smallestUnit(), relativeToOption);
    }

    private static double totalCalendarUnitBetween(JSContext context, LocalDateTime startDateTime,
            LocalDateTime endDateTime, TemporalUnit calendarUnit, TemporalRelativeToOption relativeToOption,
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
            BigInteger deltaNanoseconds = TemporalUtils.nanosecondsBetween(context, startDateTime,
                    relativeToOption.epochNanoseconds(), endDateTime, endEpochNanoseconds, relativeToOption);
            if (context.hasPendingException()) {
                return Double.NaN;
            }
            direction = deltaNanoseconds.signum() < 0 ? -1L : 1L;
        } else {
            direction = endDateTime.isBefore(startDateTime) ? -1L : 1L;
        }
        LocalDateTime nextBoundaryDateTime = calendarUnit.addCalendarUnits(startDateTime, wholeUnits + direction);
        if (!withinRelativeRange(nextBoundaryDateTime, relativeToOption)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return Double.NaN;
        }

        BigInteger remainderNanoseconds = TemporalUtils.nanosecondsBetween(context, boundaryDateTime,
                boundaryDateTime.equals(startDateTime) && zoned ? relativeToOption.epochNanoseconds() : null,
                endDateTime,
                endDateTime.equals(startDateTime) && zoned ? relativeToOption.epochNanoseconds() : endEpochNanoseconds,
                relativeToOption).abs();
        BigInteger intervalNanoseconds = TemporalUtils.nanosecondsBetween(context, boundaryDateTime,
                boundaryDateTime.equals(startDateTime) && zoned ? relativeToOption.epochNanoseconds() : null,
                nextBoundaryDateTime, null, relativeToOption).abs();
        BigDecimal fraction = new BigDecimal(remainderNanoseconds).divide(new BigDecimal(intervalNanoseconds),
                java.math.MathContext.DECIMAL128);
        BigDecimal total = BigDecimal.valueOf(wholeUnits);
        if (direction < 0) {
            total = total.subtract(fraction);
        } else {
            total = total.add(fraction);
        }
        return total.doubleValue();
    }

    static double totalWithoutRelativeTo(TemporalDuration durationRecord, TemporalUnit unit) {
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger divisorNanoseconds = unit.fixedNanoseconds();
        return divideBigIntegersToDouble(totalNanoseconds, divisorNanoseconds);
    }

    static double totalWithRelativeTo(JSContext context, TemporalDuration durationRecord, TemporalUnit unit,
            TemporalRelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        DurationEndpoint end = endOfDuration(context, durationRecord, unit, relativeToOption);
        if (end == null) {
            return Double.NaN;
        }
        LocalDateTime endDateTime = end.localDateTime();
        BigInteger endEpochNanoseconds = end.epochNanoseconds();

        if (relativeToOption.zoned() && unit == TemporalUnit.DAY) {
            BigInteger nextDayEpochNanoseconds = relativeToOption.epochNanoseconds().add(DAY_NANOSECONDS);
            if (!TemporalUtils.isValidEpochNanoseconds(nextDayEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return Double.NaN;
            }
        }

        if (unit.isDateUnit()) {
            return totalCalendarUnitBetween(context, startDateTime, endDateTime, unit, relativeToOption,
                    endEpochNanoseconds);
        }
        BigInteger totalNanoseconds;
        if (relativeToOption.zoned()) {
            totalNanoseconds = endEpochNanoseconds.subtract(relativeToOption.epochNanoseconds());
        } else {
            totalNanoseconds = TemporalUtils.nanosecondsBetween(startDateTime, endDateTime);
        }
        BigInteger divisorNanoseconds = unit.fixedNanoseconds();
        return divideBigIntegersToDouble(totalNanoseconds, divisorNanoseconds);
    }

    private static boolean withinRelativeRange(LocalDateTime end, TemporalRelativeToOption relativeToOption) {
        return relativeToOption != null && relativeToOption.zoned()
                ? TemporalUtils.isDateTimeWithinTemporalRange(end)
                : TemporalUtils.isDateWithinTemporalRange(end.toLocalDate());
    }

    private record DurationEndpoint(LocalDateTime localDateTime, BigInteger epochNanoseconds) {
    }
}
