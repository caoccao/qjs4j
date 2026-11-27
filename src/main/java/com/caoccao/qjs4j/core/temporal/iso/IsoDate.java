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

package com.caoccao.qjs4j.core.temporal.iso;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalOverflow;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.*;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDurationDateWeek;

import java.time.DateTimeException;

/**
 * Represents an ISO 8601 date with year, month, and day components.
 */
public record IsoDate(int year, int month, int day) implements Comparable<IsoDate> {

    public static final IsoDate MAX_SUPPORTED = new IsoDate(275760, 9, 13);
    public static final IsoDate MIN_SUPPORTED = new IsoDate(-271821, 4, 19);
    private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    private static final long MIN_SUPPORTED_INSTANT_EPOCH_DAY = new IsoDate(-271821, 4, 20).toEpochDay();

    public static IsoDate calendarDateToIsoDate(JSContext context, TemporalCalendarId calendarId, int calendarYear,
            Integer monthFromProperty, String monthCodeFromProperty, int dayFromProperty, String overflow) {
        IsoCalendarMonth monthSlot = IsoCalendarMonth.resolveMonthSlotForInput(context, calendarId, calendarYear,
                monthFromProperty, monthCodeFromProperty, overflow);
        if (context.hasPendingException() || monthSlot == null) {
            return null;
        }

        int regulatedDay = regulateDay(context, dayFromProperty, monthSlot.daysInMonth(), overflow);
        if (context.hasPendingException()) {
            return null;
        }

        IsoDate resultIsoDate = TemporalCalendarConversion.toIso(calendarId, calendarYear, monthSlot, regulatedDay,
                overflow);
        if (resultIsoDate == null || !resultIsoDate.isValid()) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        return resultIsoDate;
    }

    public static IsoDate createConstrained(long year, int month, int dayOfMonth) {
        int constrainedYear = (int) year;
        int constrainedDay = Math.min(dayOfMonth, daysInMonth(constrainedYear, month));
        return new IsoDate(constrainedYear, month, constrainedDay);
    }

    public static IsoDate createFromEpochDay(long epochDay) {
        // Algorithm from https://howardhinnant.github.io/date_algorithms.html
        long shiftedEpochDay = epochDay + 719468;
        long eraIndex = (shiftedEpochDay >= 0 ? shiftedEpochDay : shiftedEpochDay - 146096) / 146097;
        long dayOfEra = shiftedEpochDay - eraIndex * 146097;
        long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
        long computedYear = yearOfEra + eraIndex * 400;
        long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        long monthPrime = (5 * dayOfYear + 2) / 153;
        long dayOfMonth = dayOfYear - (153 * monthPrime + 2) / 5 + 1;
        long computedMonth = monthPrime + (monthPrime < 10 ? 3 : -9);
        if (computedMonth <= 2) {
            computedYear++;
        }
        return new IsoDate((int) computedYear, (int) computedMonth, (int) dayOfMonth);
    }

    public static int daysInMonth(int year, int month) {
        if (month == 2 && TemporalUtils.isLeapYear(year)) {
            return 29;
        }
        return DAYS_IN_MONTH[month];
    }

    public static int daysInYear(int year) {
        return TemporalUtils.isLeapYear(year) ? 366 : 365;
    }

    public static IsoDate parseDateString(JSContext context, String input) {
        return IsoDateTimeParser.parseDateString(context, input);
    }

    public static IsoDate parseMonthDayString(JSContext context, String input) {
        return IsoDateTimeParser.parseMonthDayString(context, input);
    }

    public static IsoDate parseYearMonthString(JSContext context, String input) {
        return IsoDateTimeParser.parseYearMonthString(context, input);
    }

    public static int regulateDay(JSContext context, int dayOfMonth, int daysInMonth, String overflow) {
        if (dayOfMonth < 1) {
            TemporalError.INVALID_ISO_DATE.raise(context);
            return 0;
        }
        if (TemporalOverflow.REJECT.matches(overflow) && dayOfMonth > daysInMonth) {
            TemporalError.INVALID_ISO_DATE.raise(context);
            return 0;
        }
        return Math.min(dayOfMonth, daysInMonth);
    }

    public static IsoDate resolveReferenceIsoDateForMonthDay(JSContext context, TemporalCalendarId calendarId,
            String monthCode, int dayOfMonth, String overflow) {
        if (dayOfMonth < 1) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
        if (monthCodeData == null) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        String normalizedMonthCode = IsoMonth.toMonthCode(monthCodeData.month());
        if (monthCodeData.leapMonth()) {
            normalizedMonthCode = normalizedMonthCode + "L";
        }
        int searchDay = dayOfMonth;

        if (calendarId.isChineseOrDangiCalendar() && monthCodeData.leapMonth()) {
            if (TemporalOverflow.REJECT.matches(overflow)) {
                IsoDate exactLeapReferenceIsoDate = calendarId.findReferenceIsoDateExact(normalizedMonthCode,
                        searchDay);
                if (exactLeapReferenceIsoDate == null) {
                    return TemporalError.INVALID_ISO_DATE.reject(context);
                } else {
                    return exactLeapReferenceIsoDate;
                }
            }

            int constrainedLeapDay = Math.min(searchDay, 30);
            IsoDate exactLeapReferenceIsoDate = calendarId.findReferenceIsoDateExact(normalizedMonthCode,
                    constrainedLeapDay);
            if (exactLeapReferenceIsoDate != null) {
                return exactLeapReferenceIsoDate;
            }

            normalizedMonthCode = IsoMonth.toMonthCode(monthCodeData.month());
            searchDay = constrainedLeapDay;
        }

        if (TemporalOverflow.REJECT.matches(overflow)) {
            IsoDate exactReferenceIsoDate = calendarId.findReferenceIsoDateExact(normalizedMonthCode, searchDay);
            if (exactReferenceIsoDate == null) {
                return TemporalError.INVALID_ISO_DATE.reject(context);
            } else {
                return exactReferenceIsoDate;
            }
        }

        int constrainedSearchDay = Math.min(searchDay, 31);
        IsoDate constrainedReferenceIsoDate = calendarId.findReferenceIsoDateAtOrBelow(normalizedMonthCode,
                constrainedSearchDay);
        if (constrainedReferenceIsoDate != null) {
            return constrainedReferenceIsoDate;
        }
        return TemporalError.INVALID_ISO_DATE.reject(context);
    }

    public IsoDate addCalendarDate(JSContext context, TemporalCalendarId calendarId, long yearsToAdd, long monthsToAdd,
            long weeksToAdd, long daysToAdd, String overflow) {
        IsoCalendarDate baseCalendarDate = toIsoCalendarDate(calendarId);
        if (baseCalendarDate == null) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        if (calendarId == TemporalCalendarId.ISO8601) {
            return null;
        }

        long targetYearLong;
        try {
            targetYearLong = Math.addExact(baseCalendarDate.year(), yearsToAdd);
        } catch (ArithmeticException arithmeticException) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        if (targetYearLong < Integer.MIN_VALUE || targetYearLong > Integer.MAX_VALUE) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        int targetYear = (int) targetYearLong;

        IsoCalendarMonth baseMonthSlot = calendarId.findMonthSlotByCode(baseCalendarDate.year(),
                baseCalendarDate.monthCode());
        if (baseMonthSlot == null) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        IsoCalendarMonth yearAdjustedMonthSlot = calendarId.findMonthSlotByCode(targetYear, baseMonthSlot.monthCode());
        if (yearAdjustedMonthSlot == null && baseMonthSlot.leapMonth()) {
            if (TemporalOverflow.REJECT.matches(overflow)) {
                return TemporalError.INVALID_ISO_DATE.reject(context);
            }
            String fallbackMonthCode = calendarId
                    .resolveFallbackMonthCodeForMissingLeapMonth(baseMonthSlot.monthCode());
            if (fallbackMonthCode != null) {
                yearAdjustedMonthSlot = calendarId.findMonthSlotByCode(targetYear, fallbackMonthCode);
            }
        }
        if (yearAdjustedMonthSlot == null) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        IsoYearMonth yearMonthIndex = new IsoYearMonth(targetYear, yearAdjustedMonthSlot.monthNumber());
        long remainingMonthsToMove = monthsToAdd;
        while (remainingMonthsToMove != 0L) {
            if (remainingMonthsToMove > 0L) {
                yearMonthIndex = IsoYearMonth.getNextMonth(calendarId, yearMonthIndex);
                remainingMonthsToMove--;
            } else {
                yearMonthIndex = IsoYearMonth.getPreviousMonth(calendarId, yearMonthIndex);
                remainingMonthsToMove++;
            }
        }
        IsoCalendarMonth targetMonthSlot = calendarId.findMonthSlotByNumber(yearMonthIndex.year(),
                yearMonthIndex.monthNumber());
        if (targetMonthSlot == null) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        int monthAdjustedDay = regulateDay(context, baseCalendarDate.day(), targetMonthSlot.daysInMonth(), overflow);
        if (context.hasPendingException()) {
            return null;
        }

        IsoDate intermediateIsoDate = calendarDateToIsoDate(context, calendarId, yearMonthIndex.year(),
                yearMonthIndex.monthNumber(), targetMonthSlot.monthCode(), monthAdjustedDay, "reject");
        if (context.hasPendingException() || intermediateIsoDate == null) {
            return null;
        }

        long daysFromWeeks;
        long totalDayDelta;
        try {
            daysFromWeeks = Math.multiplyExact(weeksToAdd, 7L);
            totalDayDelta = Math.addExact(daysFromWeeks, daysToAdd);
        } catch (ArithmeticException arithmeticException) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateIsoDate.toEpochDay(), totalDayDelta);
        } catch (ArithmeticException arithmeticException) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY
                || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        return createFromEpochDay(resultEpochDay);
    }

    public IsoDate addDays(int days) {
        long epochDay = toEpochDay() + days;
        return createFromEpochDay(epochDay);
    }

    public IsoDate addDurationToIsoDate(JSContext context, long years, long months, long weeks, long days,
            String overflow) {
        long totalDays;
        try {
            totalDays = Math.addExact(days, Math.multiplyExact(weeks, 7L));
        } catch (ArithmeticException arithmeticException) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.reject(context);
        }

        IsoDate intermediateDate = addYearsAndMonths(context, years, months, overflow);
        if (intermediateDate == null) {
            return null;
        }
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException arithmeticException) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        IsoDate isoDate = createFromEpochDay(resultEpochDay);
        if (!isoDate.isValid()) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        return isoDate;
    }

    public IsoDate addIsoDateWithOverflow(JSContext context, long years, long months, long weeks, long days,
            String overflow) {
        IsoDate intermediateDate;
        try {
            intermediateDate = addYearsAndMonths(context, years, months, overflow);
        } catch (ArithmeticException e) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        if (intermediateDate == null) {
            return null;
        }
        long resultEpochDay;
        try {
            long daysFromWeeks = Math.multiplyExact(weeks, 7L);
            long totalDays = Math.addExact(days, daysFromWeeks);
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException arithmeticException) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        try {
            return createFromEpochDay(resultEpochDay);
        } catch (DateTimeException dateTimeException) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
    }

    public IsoDateTime atMidnight() {
        return new IsoDateTime(this, IsoTime.MIDNIGHT);
    }

    public IsoDateTime atTime(IsoTime isoTime) {
        return new IsoDateTime(this, isoTime);
    }

    public IsoDate calendarDateAddConstrain(JSContext context, TemporalCalendarId calendarId,
            TemporalDurationDateWeek dateDuration) {
        if (calendarId == TemporalCalendarId.ISO8601) {
            return addDurationToIsoDate(context, dateDuration.years(), dateDuration.months(), dateDuration.weeks(),
                    dateDuration.days(), "constrain");
        } else {
            return addCalendarDate(context, calendarId, dateDuration.years(), dateDuration.months(),
                    dateDuration.weeks(), dateDuration.days(), "constrain");
        }
    }

    @Override
    public int compareTo(IsoDate otherIsoDate) {
        if (year != otherIsoDate.year) {
            return Integer.compare(year, otherIsoDate.year);
        }
        if (month != otherIsoDate.month) {
            return Integer.compare(month, otherIsoDate.month);
        }
        return Integer.compare(day, otherIsoDate.day);
    }

    public int dayOfWeek() {
        long epochDay = toEpochDay();
        // 1970-01-01 is Thursday (4), ISO weekday: 1=Monday, 7=Sunday
        int dayOfWeek = Math.floorMod(epochDay + 3, 7) + 1;
        return dayOfWeek;
    }

    public int dayOfYear() {
        int result = day;
        for (int monthIndex = 1; monthIndex < month; monthIndex++) {
            result += daysInMonth(year, monthIndex);
        }
        return result;
    }

    public int dayOfYear(TemporalCalendarId calendarId) {
        IsoCalendarDate calendarDateFields = toIsoCalendarDate(calendarId);
        int dayOfYear = 0;
        for (IsoCalendarMonth monthSlot : TemporalMonths.get(calendarId, calendarDateFields.year())) {
            if (monthSlot.monthCode().equals(calendarDateFields.monthCode())) {
                dayOfYear += calendarDateFields.day();
                return dayOfYear;
            }
            dayOfYear += monthSlot.daysInMonth();
        }
        return calendarDateFields.day();
    }

    public int daysInMonth() {
        return daysInMonth(year, month);
    }

    public int daysInMonth(TemporalCalendarId calendarId) {
        IsoCalendarDate calendarDateFields = toIsoCalendarDate(calendarId);
        IsoCalendarMonth monthSlot = calendarId.findMonthSlotByCode(calendarDateFields.year(),
                calendarDateFields.monthCode());
        if (monthSlot == null) {
            return 0;
        }
        return monthSlot.daysInMonth();
    }

    public int daysInYear() {
        return daysInYear(year);
    }

    public int daysInYear(TemporalCalendarId calendarId) {
        IsoCalendarDate calendarDateFields = toIsoCalendarDate(calendarId);
        int dayCount = 0;
        for (IsoCalendarMonth monthSlot : TemporalMonths.get(calendarId, calendarDateFields.year())) {
            dayCount += monthSlot.daysInMonth();
        }
        return dayCount;
    }

    public boolean isSurpassedBy(int sign, long otherYear, long otherMonth, long otherDayOfMonth) {
        if (otherYear != year) {
            return sign * (otherYear - year) > 0;
        }
        if (otherMonth != month) {
            return sign * (otherMonth - month) > 0;
        }
        if (otherDayOfMonth != day) {
            return sign * (otherDayOfMonth - day) > 0;
        }
        return false;
    }

    public boolean isValid() {
        return month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth(year, month) && isWithinSupportedRange();
    }

    public boolean isWithinInstantDateTimeRange(IsoTime isoTime) {
        long epochDay = toEpochDay();
        if (epochDay < MIN_SUPPORTED_INSTANT_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return false;
        }
        IsoTime clampedIsoTime = isoTime.clampSecondToValidRange();
        return clampedIsoTime.isValid();
    }

    public boolean isWithinPlainDateTimeRange(IsoTime isoTime) {
        long epochDay = toEpochDay();
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return false;
        }
        if (!isoTime.isValid()) {
            return false;
        }
        return epochDay != MIN_SUPPORTED_EPOCH_DAY || isoTime.totalNanoseconds() != 0L;
    }

    public boolean isWithinSupportedRange() {
        return compareTo(MIN_SUPPORTED) >= 0 && compareTo(MAX_SUPPORTED) <= 0;
    }

    public long toEpochDay() {
        long yearValue = year;
        long monthValue = month;
        long dayOfMonth = day;
        // Algorithm from https://howardhinnant.github.io/date_algorithms.html
        if (monthValue <= 2) {
            yearValue--;
        }
        long eraIndex = (yearValue >= 0 ? yearValue : yearValue - 399) / 400;
        long yearOfEra = yearValue - eraIndex * 400;
        long dayOfYear = (153 * (monthValue + (monthValue > 2 ? -3 : 9)) + 2) / 5 + dayOfMonth - 1;
        long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return eraIndex * 146097 + dayOfEra - 719468;
    }

    public IsoCalendarDate toIsoCalendarDate(TemporalCalendarId calendarId) {
        return switch (calendarId) {
            case ISO8601, GREGORY, JAPANESE -> new IsoCalendarDate(year, month, IsoMonth.toMonthCode(month), day);
            case BUDDHIST -> new IsoCalendarDate(year + 543, month, IsoMonth.toMonthCode(month), day);
            case ROC -> new IsoCalendarDate(year - 1911, month, IsoMonth.toMonthCode(month), day);
            default -> TemporalCalendarConversion.toNonIsoCalendarDate(this, calendarId);
        };
    }

    @Override
    public String toString() {
        return IsoDateTimeFormat.date(this, true, true);
    }

    public String toYearMonthString() {
        return IsoDateTimeFormat.date(this, true, false);
    }

    public String toMonthDayString() {
        return IsoDateTimeFormat.date(this, false, true);
    }

    public int weekOfYear() {
        long epochDay = toEpochDay();
        long start = firstIsoWeekStart(year);
        if (epochDay < start) {
            start = firstIsoWeekStart(year - 1);
        } else if (month == 12 && epochDay >= firstIsoWeekStart(year + 1)) {
            return 1;
        }
        return (int) ((epochDay - start) / 7) + 1;
    }

    public int yearOfWeek() {
        if (month == 1 && toEpochDay() < firstIsoWeekStart(year)) {
            return year - 1;
        }
        if (month == 12 && toEpochDay() >= firstIsoWeekStart(year + 1)) {
            return year + 1;
        }
        return year;
    }

    public TemporalEraYear toTemporalEraYear(TemporalCalendarId calendarId) {
        return calendarId.hasEra()
                ? TemporalEraYear.fromDate(this, calendarId, toIsoCalendarDate(calendarId).year())
                : null;
    }

    public static IsoDate fromSupportedEpochDay(long epochDay) {
        return epochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY
                || epochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY ? null : createFromEpochDay(epochDay);
    }

    private static long firstIsoWeekStart(int year) {
        IsoDate januaryFourth = new IsoDate(year, 1, 4);
        return januaryFourth.toEpochDay() - (januaryFourth.dayOfWeek() - 1);
    }

    private IsoDate addYearsAndMonths(JSContext context, long years, long months, String overflow) {
        long monthIndex = Math.addExact(month - 1L, months);
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long balancedYear = Math.addExact(year, years);
        balancedYear = Math.addExact(balancedYear, yearDelta);
        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        int balancedYearInt = (int) balancedYear;
        int maxDay = daysInMonth(balancedYearInt, balancedMonth);
        int regulatedDay = day;
        if ("reject".equals(overflow)) {
            if (regulatedDay > maxDay) {
                return TemporalError.INVALID_ISO_DATE.reject(context);
            }
        } else {
            regulatedDay = Math.min(regulatedDay, maxDay);
        }

        return new IsoDate(balancedYearInt, balancedMonth, regulatedDay);
    }

    public static boolean isValidYearMonth(int year, int month) {
        if (month < 1 || month > 12) {
            return false;
        }
        if (year < -271821 || year > 275760) {
            return false;
        }
        if (year == -271821 && month < 4) {
            return false;
        }
        return year != 275760 || month <= 9;
    }
}
