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

import java.math.BigInteger;
import java.time.LocalDateTime;
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

    private static BigInteger nanosecondsBetween(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return TemporalUtils.nanosecondsBetween(startDateTime, endDateTime);
        }

        BigInteger startEpochNanoseconds;
        if (startDateTime.equals(relativeToOption.startDateTime())) {
            startEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            startEpochNanoseconds =
                    IsoDateTime.zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, startDateTime);
        }
        if (context.hasPendingException() || startEpochNanoseconds == null) {
            return BigInteger.ZERO;
        }

        BigInteger endEpochNanoseconds;
        if (endDateTime.equals(relativeToOption.startDateTime())) {
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

    public long moveByWholeCalendarUnits(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime);
        LocalDateTime boundaryDateTime = addCalendarUnits(startDateTime, unitCount);
        if (!endDateTime.isBefore(startDateTime)) {
            while (boundaryDateTime.isAfter(endDateTime)) {
                unitCount--;
                boundaryDateTime = addCalendarUnits(startDateTime, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount + 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, nextUnitCount);
                if (nextDateTime.isAfter(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        } else {
            while (boundaryDateTime.isBefore(endDateTime)) {
                unitCount++;
                boundaryDateTime = addCalendarUnits(startDateTime, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount - 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, nextUnitCount);
                if (nextDateTime.isBefore(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        }
        return unitCount;
    }

    public long moveByWholeCalendarUnitsWithRelativeTo(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return moveByWholeCalendarUnits(startDateTime, endDateTime);
        }

        BigInteger startToEndNanoseconds = nanosecondsBetween(context, startDateTime, endDateTime, relativeToOption);
        if (context.hasPendingException()) {
            return 0L;
        }

        int direction = startToEndNanoseconds.signum();
        long unitCount = estimateCalendarUnitCount(startDateTime, endDateTime);
        LocalDateTime boundaryDateTime = addCalendarUnits(startDateTime, unitCount);
        if (direction >= 0) {
            while (true) {
                BigInteger boundaryToEndNanoseconds =
                        nanosecondsBetween(context, boundaryDateTime, endDateTime, relativeToOption);
                if (context.hasPendingException()) {
                    return unitCount;
                }
                if (boundaryToEndNanoseconds.signum() >= 0) {
                    break;
                }
                unitCount--;
                boundaryDateTime = addCalendarUnits(startDateTime, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount + 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, nextUnitCount);
                BigInteger nextToEndNanoseconds = nanosecondsBetween(context, nextDateTime, endDateTime, relativeToOption);
                if (context.hasPendingException()) {
                    return unitCount;
                }
                if (nextToEndNanoseconds.signum() < 0) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        } else {
            while (true) {
                BigInteger boundaryToEndNanoseconds =
                        nanosecondsBetween(context, boundaryDateTime, endDateTime, relativeToOption);
                if (context.hasPendingException()) {
                    return unitCount;
                }
                if (boundaryToEndNanoseconds.signum() < 0
                        || (boundaryToEndNanoseconds.signum() == 0 && boundaryDateTime.equals(endDateTime))) {
                    break;
                }
                unitCount++;
                boundaryDateTime = addCalendarUnits(startDateTime, unitCount);
            }
            while (true) {
                long nextUnitCount = unitCount - 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, nextUnitCount);
                BigInteger nextToEndNanoseconds = nanosecondsBetween(context, nextDateTime, endDateTime, relativeToOption);
                if (context.hasPendingException()) {
                    return unitCount;
                }
                if (nextToEndNanoseconds.signum() > 0
                        || (nextToEndNanoseconds.signum() == 0 && !nextDateTime.equals(endDateTime))) {
                    break;
                }
                unitCount = nextUnitCount;
                boundaryDateTime = nextDateTime;
            }
        }
        return unitCount;
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
}
