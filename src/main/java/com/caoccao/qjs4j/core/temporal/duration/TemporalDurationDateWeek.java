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

package com.caoccao.qjs4j.core.temporal.duration;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalYearMonth;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;

public record TemporalDurationDateWeek(long years, long months, long weeks, long days) {
    public static final TemporalDurationDateWeek ZERO = new TemporalDurationDateWeek(0, 0, 0, 0);

    public static TemporalDurationDateWeek calendarDateUntil(JSContext context, IsoDate firstDate, IsoDate secondDate,
            TemporalCalendarId calendarId, TemporalUnit largestUnit) {
        int sign = -Integer.signum(firstDate.compareTo(secondDate));
        if (sign == 0) {
            return ZERO;
        }
        if (calendarId == TemporalCalendarId.ISO8601) {
            return calendarDateUntilIso(firstDate, secondDate, largestUnit, sign);
        }

        long years = 0;
        long months = 0;
        if (largestUnit == TemporalUnit.YEAR || largestUnit == TemporalUnit.MONTH) {
            TemporalDurationYearMonth difference = TemporalDurationYearMonth.betweenCalendarDates(context, firstDate,
                    secondDate, calendarId, largestUnit);
            if (difference == null) {
                return null;
            }
            years = difference.years();
            months = difference.months();
        }

        TemporalDurationDateWeek intermediateDuration = new TemporalDurationDateWeek(years, months, 0, 0);
        IsoDate constrainedDate = firstDate.calendarDateAddConstrain(context, calendarId, intermediateDuration);
        if (context.hasPendingException() || constrainedDate == null) {
            return null;
        }
        return withRemainingDays(years, months, secondDate.toEpochDay() - constrainedDate.toEpochDay(), largestUnit);
    }

    private static TemporalDurationDateWeek calendarDateUntilIso(IsoDate firstDate, IsoDate secondDate,
            TemporalUnit largestUnit, int sign) {
        long years = 0;
        long months = 0;
        if (largestUnit == TemporalUnit.YEAR || largestUnit == TemporalUnit.MONTH) {
            long candidateYears = (long) secondDate.year() - firstDate.year();
            if (candidateYears != 0) {
                candidateYears -= sign;
            }
            while (!secondDate.isSurpassedBy(sign, firstDate.year() + candidateYears, firstDate.month(),
                    firstDate.day())) {
                years = candidateYears;
                candidateYears += sign;
            }

            long candidateMonths = sign;
            TemporalYearMonth intermediateYearMonth = TemporalYearMonth.createBalanced(firstDate.year() + years,
                    firstDate.month() + candidateMonths);
            while (!secondDate.isSurpassedBy(sign, intermediateYearMonth.year(), intermediateYearMonth.month(),
                    firstDate.day())) {
                months = candidateMonths;
                candidateMonths += sign;
                intermediateYearMonth = TemporalYearMonth.createBalanced(intermediateYearMonth.year(),
                        intermediateYearMonth.month() + sign);
            }

            if (largestUnit == TemporalUnit.MONTH) {
                months += years * 12;
                years = 0;
            }
        }

        TemporalYearMonth intermediateYearMonth = TemporalYearMonth.createBalanced(firstDate.year() + years,
                firstDate.month() + months);
        IsoDate constrainedDate = IsoDate.createConstrained(intermediateYearMonth.year(), intermediateYearMonth.month(),
                firstDate.day());
        return withRemainingDays(years, months, secondDate.toEpochDay() - constrainedDate.toEpochDay(), largestUnit);
    }

    private static TemporalDurationDateWeek withRemainingDays(long years, long months, long dayDifference,
            TemporalUnit largestUnit) {
        long weeks = 0;
        if (largestUnit == TemporalUnit.WEEK) {
            weeks = dayDifference / 7;
            dayDifference = dayDifference % 7;
        }
        return new TemporalDurationDateWeek(years, months, weeks, dayDifference);
    }

    public TemporalDurationDateWeek adjust(long newDays, Long newWeeks, Long newMonths) {
        long adjustedMonths = newMonths == null ? months : newMonths;
        long adjustedWeeks = newWeeks == null ? weeks : newWeeks;
        return new TemporalDurationDateWeek(years, adjustedMonths, adjustedWeeks, newDays);
    }

    public TemporalDurationDateWeek bubbleRelativeDuration(JSContext context, int sign, long nudgedEpochDay,
            IsoDate originDate, TemporalCalendarId calendarId, TemporalUnit largestUnit, TemporalUnit smallestUnit) {
        TemporalDurationDateWeek duration = this;
        if (smallestUnit == largestUnit) {
            return duration;
        }

        // Bubble through progressively larger calendar units, independently of enum positions.
        for (TemporalUnit unit : new TemporalUnit[]{TemporalUnit.WEEK, TemporalUnit.MONTH, TemporalUnit.YEAR}) {
            if (!unit.isLargerThan(smallestUnit) || unit.isLargerThan(largestUnit)) {
                continue;
            }
            if (unit == TemporalUnit.WEEK && largestUnit != TemporalUnit.WEEK) {
                continue;
            }

            TemporalDurationDateWeek endDuration;
            if (unit == TemporalUnit.YEAR) {
                endDuration = new TemporalDurationDateWeek(duration.years() + sign, 0, 0, 0);
            } else if (unit == TemporalUnit.MONTH) {
                endDuration = duration.adjust(0, 0L, duration.months() + sign);
            } else {
                endDuration = duration.adjust(0, duration.weeks() + sign, null);
            }

            IsoDate endDate = originDate.calendarDateAddConstrain(context, calendarId, endDuration);
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

    public int sign() {
        if (years > 0 || months > 0 || weeks > 0 || days > 0) {
            return 1;
        } else if (years < 0 || months < 0 || weeks < 0 || days < 0) {
            return -1;
        } else {
            return 0;
        }
    }
}
