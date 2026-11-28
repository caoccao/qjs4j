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

package com.caoccao.qjs4j.core.temporal;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.PropertyKey;
import com.caoccao.qjs4j.core.temporal.duration.TemporalRelativeToOption;

import java.util.List;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Temporal units with explicit ranks from largest (YEAR) to smallest (NANOSECOND).
 */
public enum TemporalUnit {
    DAY("day", 3, TemporalConstants.BI_DAY_NANOSECONDS, -1),
    HOUR("hour", 4, TemporalConstants.BI_HOUR_NANOSECONDS, 24),
    MICROSECOND("microsecond", 8, TemporalConstants.BI_MICROSECOND_NANOSECONDS, 1000),
    MILLISECOND("millisecond", 7, TemporalConstants.BI_MILLISECOND_NANOSECONDS, 1000),
    MINUTE("minute", 5, TemporalConstants.BI_MINUTE_NANOSECONDS, 60),
    MONTH("month", 1, BigInteger.ZERO, -1),
    NANOSECOND("nanosecond", 9, BigInteger.ONE, 1000),
    SECOND("second", 6, TemporalConstants.BI_SECOND_NANOSECONDS, 60),
    WEEK("week", 2, TemporalConstants.BI_WEEK_NANOSECONDS, -1),
    YEAR("year", 0, BigInteger.ZERO, -1);

    /** Property access order for a Temporal duration-like object. */
    public static final List<TemporalUnit> DURATION_PROPERTY_ORDER = List.of(DAY, HOUR, MICROSECOND, MILLISECOND,
            MINUTE, MONTH, NANOSECOND, SECOND, WEEK, YEAR);
    private final PropertyKey durationProperty;
    private final BigInteger fixedNanoseconds;
    private final String jsName;
    private final int maximumSubDayIncrement;
    private final long nanosecondFactor;
    private final int rank;
    private final long solarDayDivisor;

    TemporalUnit(String jsName, int rank, BigInteger fixedNanoseconds, int maximumSubDayIncrement) {
        this.fixedNanoseconds = fixedNanoseconds;
        this.maximumSubDayIncrement = maximumSubDayIncrement;
        nanosecondFactor = fixedNanoseconds.signum() == 0
                || fixedNanoseconds.compareTo(TemporalConstants.BI_DAY_NANOSECONDS) > 0
                        ? 0
                        : fixedNanoseconds.longValueExact();
        solarDayDivisor = nanosecondFactor == 0 ? -1 : TemporalConstants.DAY_NANOSECONDS / nanosecondFactor;
        this.jsName = jsName;
        this.durationProperty = PropertyKey.fromString(jsName + "s");
        this.rank = rank;
    }

    public LocalDateTime addCalendarUnits(LocalDateTime startDateTime, long amount) {
        if (this == YEAR) {
            return startDateTime.plusYears(amount);
        } else if (this == MONTH) {
            return startDateTime.plusMonths(amount);
        } else if (this == WEEK) {
            return startDateTime.plusWeeks(amount);
        } else {
            return startDateTime.plusDays(amount);
        }
    }

    public LocalDateTime addFixedUnits(LocalDateTime startDateTime, long amount) {
        if (this == WEEK) {
            return startDateTime.plusWeeks(amount);
        } else {
            return startDateTime.plusDays(amount);
        }
    }

    /**
     * Returns the coarser (larger) unit between this unit and another parsed unit.
     */
    public TemporalUnit coarserDurationUnit(TemporalUnit otherUnit) {
        if (rank <= otherUnit.rank) {
            return this;
        } else {
            return otherUnit;
        }
    }

    public PropertyKey durationProperty() {
        return durationProperty;
    }

    public long estimateCalendarUnitCount(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        long dayDifference = endDateTime.toLocalDate().toEpochDay() - startDateTime.toLocalDate().toEpochDay();
        if (this == DAY) {
            return dayDifference;
        } else if (this == WEEK) {
            return dayDifference / 7L;
        }

        long yearDifference = (long) endDateTime.getYear() - startDateTime.getYear();
        if (this == YEAR) {
            return yearDifference;
        }

        long monthDifference = (long) endDateTime.getMonthValue() - startDateTime.getMonthValue();
        return yearDifference * 12L + monthDifference;
    }

    /** Length of a fixed unit, including a seven-day week. Calendar months and years have no fixed length. */
    public BigInteger fixedNanoseconds() {
        return fixedNanoseconds;
    }

    /**
     * Returns the maximum valid rounding increment for sub-day difference rounding. hour -> 24, minute/second -> 60,
     * millisecond/microsecond/nanosecond -> 1000. Returns -1 for non-sub-day units.
     */
    public long getMaximumSubDayIncrement() {
        return maximumSubDayIncrement;
    }

    /**
     * Returns the nanosecond factor as a long for time units (HOUR through NANOSECOND) and DAY. Returns 0 for YEAR,
     * MONTH, WEEK.
     */
    public long getNanosecondFactor() {
        return nanosecondFactor;
    }

    /**
     * Returns the number of this unit per solar day (for Instant rounding). Only valid for DAY through NANOSECOND.
     * Returns -1 for YEAR, MONTH, WEEK.
     */
    public long getSolarDayDivisor() {
        return solarDayDivisor;
    }

    /**
     * Returns fractional second digits implied by this smallestUnit in Temporal toString. second -> 0, millisecond ->
     * 3, microsecond -> 6, nanosecond -> 9, others -> 0.
     */
    public int getStringFractionalSecondDigits() {
        return switch (this) {
            case SECOND -> 0;
            case MILLISECOND -> 3;
            case MICROSECOND -> 6;
            case NANOSECOND -> 9;
            default -> 0;
        };
    }

    /**
     * Returns nanoseconds for one increment of this smallestUnit in Temporal toString. minute -> 60e9, second -> 1e9,
     * millisecond -> 1e6, microsecond -> 1e3, nanosecond -> 1. Returns 1 for other units.
     */
    public long getStringRoundingIncrementNanoseconds() {
        return rank >= MINUTE.rank ? nanosecondFactor : 1;
    }

    /**
     * Returns true for YEAR, MONTH, WEEK, DAY.
     */
    public boolean isDateUnit() {
        return rank <= DAY.rank;
    }

    /**
     * Returns true if this unit is strictly larger than the other unit.
     */
    public boolean isLargerThan(TemporalUnit other) {
        return rank < other.rank;
    }

    /**
     * Returns true if this unit is smaller than (or equal to) the other unit.
     */
    public boolean isSmallerOrEqual(TemporalUnit other) {
        return rank >= other.rank;
    }

    /**
     * Returns true for HOUR through NANOSECOND.
     */
    public boolean isTimeUnit() {
        return rank >= HOUR.rank;
    }

    /**
     * Validates rounding increment constraints for a unit-specific Temporal rounding operation. Non-time units do not
     * impose an increment bound here.
     */
    public boolean isValidIncrement(long roundingIncrement) {
        if (!isTimeUnit()) {
            return true;
        }
        long maximumIncrement = getMaximumSubDayIncrement();
        if (maximumIncrement <= 0L) {
            return true;
        } else {
            return roundingIncrement < maximumIncrement && maximumIncrement % roundingIncrement == 0L;
        }
    }

    public long moveByWholeCalendarUnits(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        int direction = endDateTime.isBefore(startDateTime) ? -1 : 1;
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime);
        while (Integer.signum(addCalendarUnits(startDateTime, unitCount).compareTo(endDateTime)) == direction) {
            unitCount -= direction;
        }
        while (Integer
                .signum(addCalendarUnits(startDateTime, unitCount + direction).compareTo(endDateTime)) != direction) {
            unitCount += direction;
        }
        return unitCount;
    }

    public long moveByWholeCalendarUnitsWithRelativeTo(JSContext context, LocalDateTime startDateTime,
            LocalDateTime endDateTime, TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return moveByWholeCalendarUnits(startDateTime, endDateTime);
        }

        BigInteger startToEndNanoseconds = nanosecondsBetween(context, startDateTime, endDateTime, relativeToOption);
        if (context.hasPendingException()) {
            return 0L;
        }

        int direction = startToEndNanoseconds.signum() < 0 ? -1 : 1;
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime);
        while (true) {
            LocalDateTime boundary = addCalendarUnits(startDateTime, unitCount);
            BigInteger remaining = nanosecondsBetween(context, boundary, endDateTime, relativeToOption);
            if (context.hasPendingException()) {
                return unitCount;
            }
            if (isWithinCalendarBoundary(direction, remaining, boundary, endDateTime)) {
                break;
            }
            unitCount -= direction;
        }
        while (true) {
            long next = unitCount + direction;
            LocalDateTime boundary = addCalendarUnits(startDateTime, next);
            BigInteger remaining = nanosecondsBetween(context, boundary, endDateTime, relativeToOption);
            if (context.hasPendingException()
                    || !isWithinCalendarBoundary(direction, remaining, boundary, endDateTime)) {
                return unitCount;
            }
            unitCount = next;
        }
    }

    public long moveByWholeFixedUnits(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigInteger unitNanoseconds;
        if (this == WEEK) {
            unitNanoseconds = TemporalConstants.BI_WEEK_NANOSECONDS;
        } else if (this == DAY) {
            unitNanoseconds = TemporalConstants.BI_DAY_NANOSECONDS;
        } else {
            return 0L;
        }

        BigInteger deltaNanoseconds = TemporalUtils.nanosecondsBetween(startDateTime, endDateTime);
        return deltaNanoseconds.divide(unitNanoseconds).longValue();
    }

    public int rank() {
        return rank;
    }

    /**
     * Returns true for YEAR, MONTH, WEEK (units that require a relativeTo argument).
     */
    public boolean requiresRelativeTo() {
        return rank <= WEEK.rank;
    }

    /**
     * Parses a JS unit string (singular or plural) to a TemporalUnit. Returns {@code null} if the string is not a
     * recognized unit.
     */
    public static Optional<TemporalUnit> fromString(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (text) {
            case "year", "years" -> YEAR;
            case "month", "months" -> MONTH;
            case "week", "weeks" -> WEEK;
            case "day", "days" -> DAY;
            case "hour", "hours" -> HOUR;
            case "minute", "minutes" -> MINUTE;
            case "second", "seconds" -> SECOND;
            case "millisecond", "milliseconds" -> MILLISECOND;
            case "microsecond", "microseconds" -> MICROSECOND;
            case "nanosecond", "nanoseconds" -> NANOSECOND;
            default -> null;
        });
    }

    private static boolean isWithinCalendarBoundary(int direction, BigInteger remaining, LocalDateTime boundary,
            LocalDateTime end) {
        if (remaining.signum() != 0) {
            return remaining.signum() == direction;
        }
        // Across a skipped hour, distinct wall times can resolve to the same instant.
        // When moving backwards the boundary must also have reached the local end time.
        return direction > 0 || boundary.equals(end);
    }

    private static BigInteger nanosecondsBetween(JSContext context, LocalDateTime startDateTime,
            LocalDateTime endDateTime, TemporalRelativeToOption relativeToOption) {
        return TemporalUtils.nanosecondsBetween(context, startDateTime, null, endDateTime, null, relativeToOption);
    }
}
