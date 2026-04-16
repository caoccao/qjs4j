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
    public static TemporalUnit fromString(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
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
        };
    }

    /**
     * Returns the ordinal rank for a unit string, or {@code values().length} if unrecognized.
     * Suitable for comparison: smaller ordinal = larger unit.
     */
    public static int rank(String unitText) {
        TemporalUnit temporalUnit = fromString(unitText);
        return temporalUnit != null ? temporalUnit.ordinal() : values().length;
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
}
