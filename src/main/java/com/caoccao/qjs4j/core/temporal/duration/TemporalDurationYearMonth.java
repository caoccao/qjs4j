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
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.calendar.IsoMonth;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;

public record TemporalDurationYearMonth(long years, long months) {
    public static final TemporalDurationYearMonth ZERO = new TemporalDurationYearMonth(0L, 0L);

    public static TemporalDurationYearMonth fromTotalMonths(long totalMonthsDifference, TemporalUnit largestUnit) {
        if (largestUnit == TemporalUnit.YEAR) {
            long years = totalMonthsDifference / 12L;
            long months = totalMonthsDifference % 12L;
            return new TemporalDurationYearMonth(years, months);
        }
        return new TemporalDurationYearMonth(0L, totalMonthsDifference);
    }

    private static int compareCalendarDateFields(long firstYear, String firstMonthCode, int firstDay, int secondYear,
            String secondMonthCode, int secondDay) {
        int yearComparison = Long.compare(firstYear, secondYear);
        if (yearComparison != 0) {
            return yearComparison;
        }
        int monthComparison = compareMonthCodes(firstMonthCode, secondMonthCode);
        if (monthComparison != 0) {
            return monthComparison;
        }
        return Integer.compare(firstDay, secondDay);
    }

    private static int compareMonthCodes(String firstMonthCode, String secondMonthCode) {
        IsoMonth firstMonthCodeParts = IsoMonth.parseByMonthCode(firstMonthCode);
        IsoMonth secondMonthCodeParts = IsoMonth.parseByMonthCode(secondMonthCode);
        if (firstMonthCodeParts == null || secondMonthCodeParts == null) {
            return firstMonthCode.compareTo(secondMonthCode);
        }
        int monthNumberComparison = Integer.compare(firstMonthCodeParts.month(), secondMonthCodeParts.month());
        if (monthNumberComparison != 0) {
            return monthNumberComparison;
        }
        if (firstMonthCodeParts.leapMonth() == secondMonthCodeParts.leapMonth()) {
            return 0;
        } else {
            return firstMonthCodeParts.leapMonth() ? 1 : -1;
        }
    }

    private static boolean doesConceptualYearDateSurpassSecondDate(int sign, IsoCalendarDate firstCalendarDateFields,
            long candidateYears, IsoCalendarDate secondCalendarDateFields) {
        long candidateYear = firstCalendarDateFields.year() + candidateYears;
        int comparison = compareCalendarDateFields(candidateYear, firstCalendarDateFields.monthCode(),
                firstCalendarDateFields.day(), secondCalendarDateFields.year(), secondCalendarDateFields.monthCode(),
                secondCalendarDateFields.day());
        return sign * comparison > 0;
    }

    private static boolean doesConstrainedCalendarDaySurpassSecondDate(int sign,
            IsoCalendarDate firstCalendarDateFields, IsoDate constrainedCandidateDate,
            IsoCalendarDate secondCalendarDateFields, TemporalCalendarId calendarId) {
        IsoCalendarDate candidateCalendarDateFields = constrainedCandidateDate.toIsoCalendarDate(calendarId);
        if (candidateCalendarDateFields.day() == firstCalendarDateFields.day()) {
            return false;
        }
        if (candidateCalendarDateFields.year() != secondCalendarDateFields.year()) {
            return false;
        }
        if (!candidateCalendarDateFields.monthCode().equals(secondCalendarDateFields.monthCode())) {
            return false;
        }
        return sign * Integer.compare(firstCalendarDateFields.day(), secondCalendarDateFields.day()) > 0;
    }

    private static long monthsForYearDelta(JSContext context, IsoDate firstDate, TemporalCalendarId calendarId,
            long yearDelta) {
        if (yearDelta == 0L) {
            return 0L;
        }
        if (calendarId == TemporalCalendarId.COPTIC || calendarId == TemporalCalendarId.ETHIOPIC
                || calendarId == TemporalCalendarId.ETHIOAA) {
            try {
                return Math.multiplyExact(yearDelta, 13L);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
        }
        if (calendarId == TemporalCalendarId.ISO8601 || calendarId == TemporalCalendarId.GREGORY
                || calendarId == TemporalCalendarId.JAPANESE || calendarId == TemporalCalendarId.BUDDHIST
                || calendarId == TemporalCalendarId.ROC || calendarId == TemporalCalendarId.INDIAN
                || calendarId == TemporalCalendarId.PERSIAN || calendarId == TemporalCalendarId.ISLAMIC_CIVIL
                || calendarId == TemporalCalendarId.ISLAMIC_TBLA || calendarId == TemporalCalendarId.ISLAMIC_UMALQURA) {
            try {
                return Math.multiplyExact(yearDelta, 12L);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
        }

        int yearSign = yearDelta > 0L ? 1 : -1;
        long absoluteYearDelta = Math.abs(yearDelta);
        long totalMonths = 0L;
        IsoDate cursorDate = firstDate;
        for (long yearIndex = 0L; yearIndex < absoluteYearDelta; yearIndex++) {
            int monthsInYear = TemporalUtils.monthsInYear(cursorDate, calendarId);
            try {
                totalMonths = Math.addExact(totalMonths, (long) yearSign * monthsInYear);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
            TemporalDurationDateWeek oneYearDuration = new TemporalDurationDateWeek(yearSign, 0, 0, 0);
            cursorDate = cursorDate.calendarDateAddConstrain(context, calendarId, oneYearDuration);
            if (context.hasPendingException() || cursorDate == null) {
                return 0L;
            }
        }
        return totalMonths;
    }

    /** Whole calendar years and months between dates, including the leap months of lunisolar calendars. */
    public static TemporalDurationYearMonth betweenCalendarDates(JSContext context, IsoDate firstDate,
            IsoDate secondDate, TemporalCalendarId calendarId, TemporalUnit largestUnit) {
        int sign = -Integer.signum(firstDate.compareTo(secondDate));
        if (sign == 0) {
            return TemporalDurationYearMonth.ZERO;
        }

        long years = 0L;
        long months = 0L;
        IsoCalendarDate firstCalendarDateFields = firstDate.toIsoCalendarDate(calendarId);
        IsoCalendarDate secondCalendarDateFields = secondDate.toIsoCalendarDate(calendarId);

        long candidateYears = (long) secondCalendarDateFields.year() - firstCalendarDateFields.year();
        if (candidateYears != 0L) {
            candidateYears -= sign;
        }
        while (true) {
            IsoDate candidateYearDate = addYearMonth(context, firstDate, calendarId, candidateYears, 0L);
            if (context.hasPendingException() || candidateYearDate == null) {
                return null;
            }
            if (TemporalUtils.isoDateSurpasses(sign, candidateYearDate, secondDate)) {
                break;
            }
            if (doesConceptualYearDateSurpassSecondDate(sign, firstCalendarDateFields, candidateYears,
                    secondCalendarDateFields)) {
                break;
            }
            if (doesConstrainedCalendarDaySurpassSecondDate(sign, firstCalendarDateFields, candidateYearDate,
                    secondCalendarDateFields, calendarId)) {
                break;
            }
            years = candidateYears;
            candidateYears += sign;
        }

        long candidateMonths = sign;
        while (true) {
            IsoDate candidateMonthDate = addYearMonth(context, firstDate, calendarId, years, candidateMonths);
            if (context.hasPendingException() || candidateMonthDate == null) {
                return null;
            }
            if (TemporalUtils.isoDateSurpasses(sign, candidateMonthDate, secondDate)) {
                break;
            }
            if (doesConstrainedCalendarDaySurpassSecondDate(sign, firstCalendarDateFields, candidateMonthDate,
                    secondCalendarDateFields, calendarId)) {
                break;
            }
            months = candidateMonths;
            candidateMonths += sign;
        }

        if (largestUnit == TemporalUnit.MONTH) {
            long monthsFromYears = monthsForYearDelta(context, firstDate, calendarId, years);
            if (context.hasPendingException()) {
                return null;
            }
            long totalMonths;
            try {
                totalMonths = Math.addExact(months, monthsFromYears);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }

            while (true) {
                IsoDate totalMonthDate = addYearMonth(context, firstDate, calendarId, 0L, totalMonths);
                if (context.hasPendingException() || totalMonthDate == null) {
                    return null;
                }
                if (TemporalUtils.isoDateSurpasses(sign, totalMonthDate, secondDate)) {
                    totalMonths -= sign;
                    continue;
                }
                if (doesConstrainedCalendarDaySurpassSecondDate(sign, firstCalendarDateFields, totalMonthDate,
                        secondCalendarDateFields, calendarId)) {
                    totalMonths -= sign;
                    continue;
                }

                long nextTotalMonths;
                try {
                    nextTotalMonths = Math.addExact(totalMonths, sign);
                } catch (ArithmeticException arithmeticException) {
                    break;
                }
                IsoDate nextTotalMonthDate = addYearMonth(context, firstDate, calendarId, 0L, nextTotalMonths);
                if (context.hasPendingException() || nextTotalMonthDate == null) {
                    return null;
                }
                if (TemporalUtils.isoDateSurpasses(sign, nextTotalMonthDate, secondDate)) {
                    break;
                }
                if (doesConstrainedCalendarDaySurpassSecondDate(sign, firstCalendarDateFields, nextTotalMonthDate,
                        secondCalendarDateFields, calendarId)) {
                    break;
                }
                totalMonths = nextTotalMonths;
            }
            return new TemporalDurationYearMonth(0L, totalMonths);
        }
        return new TemporalDurationYearMonth(years, months);
    }

    private static IsoDate addYearMonth(JSContext context, IsoDate date, TemporalCalendarId calendar, long years,
            long months) {
        if (calendar == TemporalCalendarId.ISO8601) {
            return date.addDurationToIsoDate(context, years, months, 0L, 0L, "constrain");
        }
        return date.addCalendarDate(context, calendar, years, months, 0L, 0L, "constrain");
    }
}
