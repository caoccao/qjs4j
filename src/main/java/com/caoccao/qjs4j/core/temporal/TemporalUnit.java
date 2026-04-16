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
 * Ordinal values provide a natural ordering where smaller ordinal = larger unit.
 * <p>
 * Replaces duplicated {@code temporalUnitRank()}, {@code canonicalizeDifferenceUnit()},
 * {@code canonicalizeTemporalUnit()}, and {@code UNIT_*} string constants.
 */
public enum TemporalUnit {
    YEAR("year"),
    MONTH("month"),
    WEEK("week"),
    DAY("day"),
    HOUR("hour"),
    MINUTE("minute"),
    SECOND("second"),
    MILLISECOND("millisecond"),
    MICROSECOND("microsecond"),
    NANOSECOND("nanosecond");

    private final String jsName;

    TemporalUnit(String jsName) {
        this.jsName = jsName;
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
     * Returns the ordinal rank for a unit string, or {@code values().length} if unrecognized.
     * Suitable for comparison: smaller ordinal = larger unit.
     */
    public static int rank(String unitText) {
        return fromString(unitText)
                .map(Enum::ordinal)
                .orElse(values().length);
    }

    /**
     * Returns true for YEAR, MONTH, WEEK, DAY.
     */
    public boolean isDateUnit() {
        return ordinal() <= DAY.ordinal();
    }

    /**
     * Returns true if this unit is strictly larger than the other unit.
     */
    public boolean isLargerThan(TemporalUnit other) {
        return this.ordinal() < other.ordinal();
    }

    /**
     * Returns true if this unit is smaller than (or equal to) the other unit.
     */
    public boolean isSmallerOrEqual(TemporalUnit other) {
        return this.ordinal() >= other.ordinal();
    }

    /**
     * Returns true for HOUR through NANOSECOND.
     */
    public boolean isTimeUnit() {
        return ordinal() >= HOUR.ordinal();
    }

    /**
     * Returns the JS-canonical singular name (e.g. "year", "nanosecond").
     */
    public String jsName() {
        return jsName;
    }

    /**
     * Returns the nanosecond factor as a long for time units (HOUR through NANOSECOND) and DAY.
     * Returns 0 for YEAR, MONTH, WEEK.
     */
    public long nanosecondFactorLong() {
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
     * Returns true for YEAR, MONTH, WEEK (units that require a relativeTo argument).
     */
    public boolean requiresRelativeTo() {
        return ordinal() <= WEEK.ordinal();
    }

    /**
     * Returns the number of this unit per solar day (for Instant rounding).
     * Only valid for DAY through NANOSECOND. Returns -1 for YEAR, MONTH, WEEK.
     */
    public long solarDayDivisor() {
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
}
