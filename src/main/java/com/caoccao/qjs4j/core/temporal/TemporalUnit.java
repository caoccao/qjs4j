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

import java.util.Optional;

/**
 * Temporal unit enum, ordered from largest (YEAR) to smallest (NANOSECOND).
 */
public enum TemporalUnit {
    YEAR("year", 0),
    MONTH("month", 1),
    WEEK("week", 2),
    DAY("day", 3),
    HOUR("hour", 4),
    MINUTE("minute", 5),
    SECOND("second", 6),
    MILLISECOND("millisecond", 7),
    MICROSECOND("microsecond", 8),
    NANOSECOND("nanosecond", 9);

    private static final int UNKNOWN_RANK = 10;
    private final String jsName;
    private final int rank;

    TemporalUnit(String jsName, int rank) {
        this.jsName = jsName;
        this.rank = rank;
    }

    /**
     * Parses a JS unit string (singular or plural) to a TemporalUnit.
     * Returns {@code null} if the string is not a recognized unit.
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

    /**
     * Returns the maximum valid rounding increment for sub-day difference rounding.
     * hour -> 24, minute/second -> 60, millisecond/microsecond/nanosecond -> 1000.
     * Returns -1 for non-sub-day units.
     */
    public long getMaximumSubDayIncrement() {
        return switch (this) {
            case HOUR -> 24L;
            case MINUTE, SECOND -> 60L;
            case MILLISECOND, MICROSECOND, NANOSECOND -> 1_000L;
            default -> -1L;
        };
    }

    /**
     * Returns the nanosecond factor as a long for time units (HOUR through NANOSECOND) and DAY.
     * Returns 0 for YEAR, MONTH, WEEK.
     */
    public long getNanosecondFactor() {
        return switch (this) {
            case DAY -> TemporalConstants.DAY_NANOSECONDS;
            case HOUR -> TemporalConstants.HOUR_NANOSECONDS;
            case MINUTE -> TemporalConstants.MINUTE_NANOSECONDS;
            case SECOND -> TemporalConstants.SECOND_NANOSECONDS;
            case MILLISECOND -> TemporalConstants.MILLISECOND_NANOSECONDS;
            case MICROSECOND -> TemporalConstants.MICROSECOND_NANOSECONDS;
            case NANOSECOND -> 1L;
            default -> 0L;
        };
    }

    /**
     * Returns the number of this unit per solar day (for Instant rounding).
     * Only valid for DAY through NANOSECOND. Returns -1 for YEAR, MONTH, WEEK.
     */
    public long getSolarDayDivisor() {
        return switch (this) {
            case DAY -> 1L;
            case HOUR -> TemporalConstants.SOLAR_DAY_HOURS;
            case MINUTE -> TemporalConstants.SOLAR_DAY_MINUTES;
            case SECOND -> TemporalConstants.SOLAR_DAY_SECONDS;
            case MILLISECOND -> TemporalConstants.SOLAR_DAY_MILLISECONDS;
            case MICROSECOND -> TemporalConstants.SOLAR_DAY_MICROSECONDS;
            case NANOSECOND -> TemporalConstants.SOLAR_DAY_NANOSECONDS;
            default -> -1L;
        };
    }

    /**
     * Returns fractional second digits implied by this smallestUnit in Temporal toString.
     * second -> 0, millisecond -> 3, microsecond -> 6, nanosecond -> 9, others -> 0.
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
     * Returns nanoseconds for one increment of this smallestUnit in Temporal toString.
     * minute -> 60e9, second -> 1e9, millisecond -> 1e6, microsecond -> 1e3, nanosecond -> 1.
     * Returns 1 for other units.
     */
    public long getStringRoundingIncrementNanoseconds() {
        return switch (this) {
            case MINUTE -> TemporalConstants.MINUTE_NANOSECONDS;
            case SECOND -> TemporalConstants.SECOND_NANOSECONDS;
            case MILLISECOND -> TemporalConstants.MILLISECOND_NANOSECONDS;
            case MICROSECOND -> TemporalConstants.MICROSECOND_NANOSECONDS;
            case NANOSECOND -> 1L;
            default -> 1L;
        };
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
     * Validates rounding increment constraints for a unit-specific Temporal rounding operation.
     * Non-time units do not impose an increment bound here.
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

    public int rank() {
        return rank;
    }

    /**
     * Returns true for YEAR, MONTH, WEEK (units that require a relativeTo argument).
     */
    public boolean requiresRelativeTo() {
        return rank <= WEEK.rank;
    }
}
