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

public record TemporalDurationDateWeek(long years, long months, long weeks, long days) {
    public static final TemporalDurationDateWeek ZERO = new TemporalDurationDateWeek(0, 0, 0, 0);

    public static TemporalDurationDateWeek calendarDateUntil(
            JSContext context,
            IsoDate firstDate,
            IsoDate secondDate,
            TemporalCalendarId calendarId,
            TemporalUnit largestUnit) {
        if (calendarId == TemporalCalendarId.ISO8601) {
            return calendarDateUntilIso(firstDate, secondDate, largestUnit);
        }
        int sign = -Integer.signum(firstDate.compareTo(secondDate));
        if (sign == 0) {
            return ZERO;
        }

        long years = 0;
        long months = 0;
        if (largestUnit == TemporalUnit.YEAR || largestUnit == TemporalUnit.MONTH) {
            IsoCalendarDate firstCalendarDateFields = firstDate.toIsoCalendarDate(calendarId);
            IsoCalendarDate secondCalendarDateFields = secondDate.toIsoCalendarDate(calendarId);
            long candidateYears = (long) secondCalendarDateFields.year() - firstCalendarDateFields.year();
            if (candidateYears != 0L) {
                candidateYears -= sign;
            }
            while (true) {
                TemporalDurationDateWeek candidateYearDuration = new TemporalDurationDateWeek(candidateYears, 0, 0, 0);
                IsoDate candidateYearDate = firstDate.calendarDateAddConstrain(context, calendarId, candidateYearDuration);
                if (context.hasPendingException() || candidateYearDate == null) {
                    return null;
                }
                if (TemporalUtils.isoDateSurpasses(sign, candidateYearDate, secondDate)) {
                    break;
                }
                if (doesConceptualYearDateSurpassSecondDate(
                        sign,
                        firstCalendarDateFields,
                        candidateYears,
                        secondCalendarDateFields)) {
                    break;
                }
                if (doesConstrainedCalendarDaySurpassSecondDate(
                        sign,
                        firstCalendarDateFields,
                        candidateYearDate,
                        secondCalendarDateFields,
                        calendarId)) {
                    break;
                }
                years = candidateYears;
                candidateYears += sign;
            }

            long candidateMonths = sign;
            while (true) {
                TemporalDurationDateWeek candidateMonthDuration = new TemporalDurationDateWeek(years, candidateMonths, 0, 0);
                IsoDate candidateMonthDate = firstDate.calendarDateAddConstrain(context, calendarId, candidateMonthDuration);
                if (context.hasPendingException() || candidateMonthDate == null) {
                    return null;
                }
                if (TemporalUtils.isoDateSurpasses(sign, candidateMonthDate, secondDate)) {
                    break;
                }
                if (doesConstrainedCalendarDaySurpassSecondDate(
                        sign,
                        firstCalendarDateFields,
                        candidateMonthDate,
                        secondCalendarDateFields,
                        calendarId)) {
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
                    TemporalDurationDateWeek totalMonthDuration = new TemporalDurationDateWeek(0, totalMonths, 0, 0);
                    IsoDate totalMonthDate = firstDate.calendarDateAddConstrain(context, calendarId, totalMonthDuration);
                    if (context.hasPendingException() || totalMonthDate == null) {
                        return null;
                    }
                    if (TemporalUtils.isoDateSurpasses(sign, totalMonthDate, secondDate)) {
                        totalMonths -= sign;
                        continue;
                    }
                    if (doesConstrainedCalendarDaySurpassSecondDate(
                            sign,
                            firstCalendarDateFields,
                            totalMonthDate,
                            secondCalendarDateFields,
                            calendarId)) {
                        totalMonths -= sign;
                        continue;
                    }

                    long nextTotalMonths;
                    try {
                        nextTotalMonths = Math.addExact(totalMonths, sign);
                    } catch (ArithmeticException arithmeticException) {
                        break;
                    }
                    TemporalDurationDateWeek nextTotalMonthDuration = new TemporalDurationDateWeek(0, nextTotalMonths, 0, 0);
                    IsoDate nextTotalMonthDate = firstDate.calendarDateAddConstrain(context, calendarId, nextTotalMonthDuration);
                    if (context.hasPendingException() || nextTotalMonthDate == null) {
                        return null;
                    }
                    if (TemporalUtils.isoDateSurpasses(sign, nextTotalMonthDate, secondDate)) {
                        break;
                    }
                    if (doesConstrainedCalendarDaySurpassSecondDate(
                            sign,
                            firstCalendarDateFields,
                            nextTotalMonthDate,
                            secondCalendarDateFields,
                            calendarId)) {
                        break;
                    }
                    totalMonths = nextTotalMonths;
                }
                months = totalMonths;
                years = 0;
            }
        }

        TemporalDurationDateWeek intermediateDuration = new TemporalDurationDateWeek(years, months, 0, 0);
        IsoDate constrainedDate = firstDate.calendarDateAddConstrain(context, calendarId, intermediateDuration);
        if (context.hasPendingException() || constrainedDate == null) {
            return null;
        }
        long dayDifference = secondDate.toEpochDay() - constrainedDate.toEpochDay();
        long weeks = 0;
        if (largestUnit == TemporalUnit.WEEK) {
            weeks = dayDifference / 7;
            dayDifference = dayDifference % 7;
        }
        return new TemporalDurationDateWeek(years, months, weeks, dayDifference);
    }

    private static TemporalDurationDateWeek calendarDateUntilIso(
            IsoDate firstDate,
            IsoDate secondDate,
            TemporalUnit largestUnit) {
        int sign = -Integer.signum(firstDate.compareTo(secondDate));
        if (sign == 0) {
            return ZERO;
        }

        long years = 0;
        long months = 0;
        if (largestUnit == TemporalUnit.YEAR || largestUnit == TemporalUnit.MONTH) {
            long candidateYears = (long) secondDate.year() - firstDate.year();
            if (candidateYears != 0) {
                candidateYears -= sign;
            }
            while (!secondDate.isSurpassedBy(sign, firstDate.year() + candidateYears, firstDate.month(), firstDate.day())) {
                years = candidateYears;
                candidateYears += sign;
            }

            long candidateMonths = sign;
            TemporalYearMonth intermediateYearMonth =
                    TemporalYearMonth.createBalanced(firstDate.year() + years, firstDate.month() + candidateMonths);
            while (!secondDate.isSurpassedBy(sign, intermediateYearMonth.year(), intermediateYearMonth.month(), firstDate.day())) {
                months = candidateMonths;
                candidateMonths += sign;
                intermediateYearMonth = TemporalYearMonth.createBalanced(
                        intermediateYearMonth.year(),
                        intermediateYearMonth.month() + sign);
            }

            if (largestUnit == TemporalUnit.MONTH) {
                months += years * 12;
                years = 0;
            }
        }

        TemporalYearMonth intermediateYearMonth =
                TemporalYearMonth.createBalanced(firstDate.year() + years, firstDate.month() + months);
        IsoDate constrainedDate = IsoDate.createConstrained(
                intermediateYearMonth.year(),
                intermediateYearMonth.month(),
                firstDate.day());
        long dayDifference = secondDate.toEpochDay() - constrainedDate.toEpochDay();
        long weeks = 0;
        if (largestUnit == TemporalUnit.WEEK) {
            weeks = dayDifference / 7;
            dayDifference = dayDifference % 7;
        }
        return new TemporalDurationDateWeek(years, months, weeks, dayDifference);
    }

    private static int compareCalendarDateFields(
            long firstYear,
            String firstMonthCode,
            int firstDay,
            int secondYear,
            String secondMonthCode,
            int secondDay) {
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
        int monthNumberComparison = Integer.compare(
                firstMonthCodeParts.month(),
                secondMonthCodeParts.month());
        if (monthNumberComparison != 0) {
            return monthNumberComparison;
        }
        if (firstMonthCodeParts.leapMonth() == secondMonthCodeParts.leapMonth()) {
            return 0;
        } else {
            return firstMonthCodeParts.leapMonth() ? 1 : -1;
        }
    }

    private static boolean doesConceptualYearDateSurpassSecondDate(
            int sign,
            IsoCalendarDate firstCalendarDateFields,
            long candidateYears,
            IsoCalendarDate secondCalendarDateFields) {
        long candidateYear = firstCalendarDateFields.year() + candidateYears;
        int comparison = compareCalendarDateFields(
                candidateYear,
                firstCalendarDateFields.monthCode(),
                firstCalendarDateFields.day(),
                secondCalendarDateFields.year(),
                secondCalendarDateFields.monthCode(),
                secondCalendarDateFields.day());
        return sign * comparison > 0;
    }

    private static boolean doesConstrainedCalendarDaySurpassSecondDate(
            int sign,
            IsoCalendarDate firstCalendarDateFields,
            IsoDate constrainedCandidateDate,
            IsoCalendarDate secondCalendarDateFields,
            TemporalCalendarId calendarId) {
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

    private static long monthsForYearDelta(
            JSContext context,
            IsoDate firstDate,
            TemporalCalendarId calendarId,
            long yearDelta) {
        if (yearDelta == 0L) {
            return 0L;
        }
        if (calendarId == TemporalCalendarId.COPTIC
                || calendarId == TemporalCalendarId.ETHIOPIC
                || calendarId == TemporalCalendarId.ETHIOAA) {
            try {
                return Math.multiplyExact(yearDelta, 13L);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
        }
        if (calendarId == TemporalCalendarId.ISO8601
                || calendarId == TemporalCalendarId.GREGORY
                || calendarId == TemporalCalendarId.JAPANESE
                || calendarId == TemporalCalendarId.BUDDHIST
                || calendarId == TemporalCalendarId.ROC
                || calendarId == TemporalCalendarId.INDIAN
                || calendarId == TemporalCalendarId.PERSIAN
                || calendarId == TemporalCalendarId.ISLAMIC_CIVIL
                || calendarId == TemporalCalendarId.ISLAMIC_TBLA
                || calendarId == TemporalCalendarId.ISLAMIC_UMALQURA) {
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

    public TemporalDurationDateWeek adjust(long newDays, Long newWeeks, Long newMonths) {
        long adjustedMonths = newMonths == null ? months : newMonths;
        long adjustedWeeks = newWeeks == null ? weeks : newWeeks;
        return new TemporalDurationDateWeek(years, adjustedMonths, adjustedWeeks, newDays);
    }

    public TemporalDurationDateWeek bubbleRelativeDuration(
            JSContext context,
            int sign,
            long nudgedEpochDay,
            IsoDate originDate,
            TemporalCalendarId calendarId,
            TemporalUnit largestUnit,
            TemporalUnit smallestUnit) {
        TemporalDurationDateWeek duration = this;
        if (smallestUnit == largestUnit) {
            return duration;
        }

        TemporalUnit[] units = TemporalUnit.values();
        int largestUnitIndex = largestUnit.rank();
        int smallestUnitIndex = smallestUnit.rank();
        for (int unitIndex = smallestUnitIndex - 1; unitIndex >= largestUnitIndex; unitIndex--) {
            TemporalUnit unit = units[unitIndex];
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
