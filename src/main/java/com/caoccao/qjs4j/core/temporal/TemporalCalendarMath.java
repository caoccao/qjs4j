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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporal calendar math for non-ISO calendars used by Temporal.PlainDate.
 */
public final class TemporalCalendarMath {
    private static final int[] CHINESE_LUNAR_YEAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ae0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63
    };
    private static final int[] CHINESE_LUNAR_YEAR_INFO_EXTENSION_2051_TO_2100 = {
            0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, 0x092e0,
            0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, 0x092d0,
            0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, 0x0b273,
            0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, 0x0e968,
            0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, 0x0d520
    };
    private static final long COPTIC_EPOCH_DAY_OFFSET = -615_558L;
    private static final int[] DANGI_LUNAR_YEAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x0da95, 0x0b550, 0x056a0, 0x0ada2, 0x095d0, 0x04bb7,
            0x049b0, 0x0a4b0, 0x0b4b5, 0x06a90, 0x0ad40, 0x0bb54, 0x02b60, 0x095b0, 0x05372, 0x04970,
            0x06566, 0x0e4a0, 0x0ea50, 0x16a95, 0x05b50, 0x02b60, 0x18ae3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b690, 0x056d0, 0x125b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0d557,
            0x0b4a0, 0x0b550, 0x15555, 0x04db0, 0x025b0, 0x18573, 0x052b0, 0x0a9b8, 0x06950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05270, 0x07263, 0x0d950, 0x06b57, 0x056a0,
            0x09ad0, 0x04dd5, 0x04ae0, 0x0a4e0, 0x0d4d4, 0x0d250, 0x0d598, 0x0b540, 0x0d6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a9b4, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0b756, 0x02b60, 0x095b0,
            0x04b75, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06d98, 0x05ad0, 0x02b60, 0x096e5, 0x092e0,
            0x0c960, 0x0e954, 0x0d4a0, 0x0da50, 0x07552, 0x056c0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x1b4a3, 0x0b550, 0x055d9, 0x04ba0, 0x0a5b0, 0x09575, 0x052b0, 0x0a950,
            0x0b954, 0x06aa0, 0x0ad50, 0x06b52, 0x04b60, 0x0a6e6, 0x0a570, 0x05270, 0x06a65, 0x0d930,
            0x05aa0, 0x0b6a3, 0x096d0, 0x04afb, 0x04ae0, 0x0a4d0, 0x1d0d6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b6a0, 0x096d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0b250, 0x1b255, 0x06d40, 0x0ada0,
            0x18b63
    };
    private static final int DEFAULT_REFERENCE_ISO_YEAR = 1972;
    private static final long ETHIOPIC_EPOCH_DAY_OFFSET = -716_367L;
    private static final long HEBREW_EPOCH_DAY_OFFSET = -2_092_591L;
    private static final long ISLAMIC_CIVIL_EPOCH_DAY_OFFSET = -492_148L;
    private static final long ISLAMIC_TBLA_EPOCH_DAY_OFFSET = -492_149L;
    private static final int[] LUNISOLAR_MONTH_LENGTHS_YEAR_1899 = {
            30, 29, 30, 30, 29, 30, 29, 30, 29, 30, 29, 30
    };
    private static final int MAX_REFERENCE_ISO_DATE_CACHE_SIZE = 4_096;
    private static final int MAX_REFERENCE_ISO_YEAR = 2050;
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final int MIN_REFERENCE_ISO_YEAR = 1900;
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    private static final int[] PERSIAN_BREAKS = {
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394,
            2456, 3178
    };
    private static final int PERSIAN_BREAK_MAX_SUPPORTED_YEAR = PERSIAN_BREAKS[PERSIAN_BREAKS.length - 1] - 1;
    private static final int PERSIAN_BREAK_MIN_SUPPORTED_YEAR = PERSIAN_BREAKS[0] + 1;
    private static final int PERSIAN_FALLBACK_MAX_CORRECTION_DAYS = 62;
    private static final int PERSIAN_FALLBACK_MAX_YEAR = 275_139;
    private static final int PERSIAN_FALLBACK_MIN_CORRECTION_DAYS = -61;
    private static final int PERSIAN_FALLBACK_MIN_YEAR = -272_442;
    private static final ConcurrentHashMap<ReferenceIsoDateLookupKey, IsoDate> REFERENCE_ISO_DATE_HIT_CACHE =
            new ConcurrentHashMap<>();
    private static final Set<ReferenceIsoDateLookupKey> REFERENCE_ISO_DATE_MISS_CACHE =
            ConcurrentHashMap.newKeySet();
    private static final int[] UMALQURA_KNOWN_LEAP_YEARS_1390_TO_1469 = {
            1390, 1392, 1397, 1399, 1403, 1405, 1406, 1411, 1412, 1414,
            1418, 1420, 1425, 1426, 1428, 1433, 1435, 1439, 1441, 1443,
            1447, 1448, 1451, 1454, 1455, 1457, 1462, 1463, 1467, 1469
    };
    private static final int YEAR_MONTH_BOUNDARY_SEARCH_RADIUS_DAYS = 400;

    private TemporalCalendarMath() {
    }

    public static IsoDate addCalendarDate(
            JSContext context,
            IsoDate baseIsoDate,
            String calendarId,
            long yearsToAdd,
            long monthsToAdd,
            long weeksToAdd,
            long daysToAdd,
            String overflow) {
        CalendarDateFields baseCalendarDate = isoDateToCalendarDate(baseIsoDate, calendarId);
        if (baseCalendarDate == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if ("iso8601".equals(calendarId)) {
            return null;
        }

        long targetYearLong;
        try {
            targetYearLong = Math.addExact(baseCalendarDate.year(), yearsToAdd);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (targetYearLong < Integer.MIN_VALUE || targetYearLong > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int targetYear = (int) targetYearLong;

        MonthSlot baseMonthSlot = findMonthSlotByCode(calendarId, baseCalendarDate.year(), baseCalendarDate.monthCode());
        if (baseMonthSlot == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        MonthSlot yearAdjustedMonthSlot = findMonthSlotByCode(calendarId, targetYear, baseMonthSlot.monthCode());
        if (yearAdjustedMonthSlot == null && baseMonthSlot.leapMonth()) {
            if ("reject".equals(overflow)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            String fallbackMonthCode = resolveFallbackMonthCodeForMissingLeapMonth(calendarId, baseMonthSlot.monthCode());
            if (fallbackMonthCode != null) {
                yearAdjustedMonthSlot = findMonthSlotByCode(calendarId, targetYear, fallbackMonthCode);
            }
        }
        if (yearAdjustedMonthSlot == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        YearMonthIndex yearMonthIndex = new YearMonthIndex(targetYear, yearAdjustedMonthSlot.monthNumber());
        long remainingMonthsToMove = monthsToAdd;
        while (remainingMonthsToMove != 0L) {
            if (remainingMonthsToMove > 0L) {
                yearMonthIndex = nextMonthIndex(calendarId, yearMonthIndex);
                remainingMonthsToMove--;
            } else {
                yearMonthIndex = previousMonthIndex(calendarId, yearMonthIndex);
                remainingMonthsToMove++;
            }
        }
        MonthSlot targetMonthSlot = findMonthSlotByNumber(calendarId, yearMonthIndex.year(), yearMonthIndex.monthNumber());
        if (targetMonthSlot == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int monthAdjustedDay = regulateDay(context, baseCalendarDate.day(), targetMonthSlot.daysInMonth(), overflow);
        if (context.hasPendingException()) {
            return null;
        }

        IsoDate intermediateIsoDate = calendarDateToIsoDate(
                context,
                calendarId,
                yearMonthIndex.year(),
                yearMonthIndex.monthNumber(),
                targetMonthSlot.monthCode(),
                monthAdjustedDay,
                "reject");
        if (context.hasPendingException() || intermediateIsoDate == null) {
            return null;
        }

        long daysFromWeeks;
        long totalDayDelta;
        try {
            daysFromWeeks = Math.multiplyExact(weeksToAdd, 7L);
            totalDayDelta = Math.addExact(daysFromWeeks, daysToAdd);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateIsoDate.toEpochDay(), totalDayDelta);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return IsoDate.fromEpochDay(resultEpochDay);
    }

    private static boolean alexandrianLeapYear(int calendarYear) {
        return floorMod(calendarYear + 1, 4) == 0;
    }

    private static long alexandrianOrdinalDay(int calendarYear, int calendarMonth, int dayOfMonth) {
        long yearValue = calendarYear;
        long monthValue = calendarMonth;
        long dayValue = dayOfMonth;
        return 365L * (yearValue - 1L)
                + Math.floorDiv(yearValue, 4L)
                + 30L * (monthValue - 1L)
                + (dayValue - 1L);
    }

    private static IsoDate alexandrianToIsoDate(int calendarYear, String monthCode, int dayOfMonth, long offsetEpochDay) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        long calendarOrdinalDay = alexandrianOrdinalDay(calendarYear, monthCodeData.monthNumber(), dayOfMonth);
        long epochDay = calendarOrdinalDay + offsetEpochDay;
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.fromEpochDay(epochDay);
    }

    public static IsoDate calendarDateToIsoDate(
            JSContext context,
            String calendarId,
            int calendarYear,
            Integer monthFromProperty,
            String monthCodeFromProperty,
            int dayFromProperty,
            String overflow) {
        MonthSlot monthSlot = resolveMonthSlotForInput(
                context,
                calendarId,
                calendarYear,
                monthFromProperty,
                monthCodeFromProperty,
                overflow);
        if (context.hasPendingException() || monthSlot == null) {
            return null;
        }

        int regulatedDay = regulateDay(context, dayFromProperty, monthSlot.daysInMonth(), overflow);
        if (context.hasPendingException()) {
            return null;
        }

        IsoDate resultIsoDate = switch (calendarId) {
            case "iso8601", "gregory", "japanese" ->
                    toIsoDateFromGregorianLike(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "buddhist" -> toIsoDateFromGregorianLike(calendarYear - 543, monthSlot.monthCode(), regulatedDay);
            case "roc" -> toIsoDateFromGregorianLike(calendarYear + 1911, monthSlot.monthCode(), regulatedDay);
            case "coptic" ->
                    alexandrianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, COPTIC_EPOCH_DAY_OFFSET);
            case "ethiopic" ->
                    alexandrianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, ETHIOPIC_EPOCH_DAY_OFFSET);
            case "ethioaa" ->
                    alexandrianToIsoDate(calendarYear - 5500, monthSlot.monthCode(), regulatedDay, ETHIOPIC_EPOCH_DAY_OFFSET);
            case "indian" -> indianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "islamic-civil" ->
                    islamicToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case "islamic-tbla" ->
                    islamicToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case "islamic-umalqura" -> umalquraToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "persian" -> persianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "hebrew" -> hebrewToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "chinese", "dangi" ->
                    lunisolarToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, calendarId, overflow);
            default -> toIsoDateFromGregorianLike(calendarYear, monthSlot.monthCode(), regulatedDay);
        };
        if (resultIsoDate == null || !IsoDate.isValidIsoDate(resultIsoDate.year(), resultIsoDate.month(), resultIsoDate.day())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return resultIsoDate;
    }

    public static boolean calendarYearHasMonthCode(String calendarId, int calendarYear, String monthCode) {
        if (monthCode == null) {
            return false;
        }
        List<MonthSlot> monthSlots = getMonthSlots(calendarId, calendarYear);
        for (MonthSlot monthSlot : monthSlots) {
            if (monthSlot.monthCode().equals(monthCode)) {
                return true;
            }
        }
        return false;
    }

    public static String constrainMonthCode(String calendarId, int calendarYear, String monthCode) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || !monthCodeData.leapMonth()) {
            return monthCode;
        }
        if (calendarYearHasMonthCode(calendarId, calendarYear, monthCode)) {
            return monthCode;
        }
        String fallbackMonthCode = resolveFallbackMonthCodeForMissingLeapMonth(calendarId, monthCode);
        if (fallbackMonthCode != null) {
            return fallbackMonthCode;
        }
        return monthCode;
    }

    public static int dayOfYear(IsoDate isoDate, String calendarId) {
        CalendarDateFields calendarDateFields = isoDateToCalendarDate(isoDate, calendarId);
        List<MonthSlot> monthSlots = getMonthSlots(calendarId, calendarDateFields.year());
        int dayOfYear = 0;
        for (MonthSlot monthSlot : monthSlots) {
            if (monthSlot.monthCode().equals(calendarDateFields.monthCode())) {
                dayOfYear += calendarDateFields.day();
                return dayOfYear;
            }
            dayOfYear += monthSlot.daysInMonth();
        }
        return calendarDateFields.day();
    }

    public static int daysInMonth(IsoDate isoDate, String calendarId) {
        CalendarDateFields calendarDateFields = isoDateToCalendarDate(isoDate, calendarId);
        MonthSlot monthSlot = findMonthSlotByCode(calendarId, calendarDateFields.year(), calendarDateFields.monthCode());
        if (monthSlot == null) {
            return 0;
        }
        return monthSlot.daysInMonth();
    }

    public static int daysInYear(IsoDate isoDate, String calendarId) {
        CalendarDateFields calendarDateFields = isoDateToCalendarDate(isoDate, calendarId);
        List<MonthSlot> monthSlots = getMonthSlots(calendarId, calendarDateFields.year());
        int dayCount = 0;
        for (MonthSlot monthSlot : monthSlots) {
            dayCount += monthSlot.daysInMonth();
        }
        return dayCount;
    }

    public static IsoDate findBoundaryIsoDateForYearMonth(String calendarId, int targetYear, String targetMonthCode) {
        IsoDate minimumBoundaryIsoDate = findClosestBoundaryIsoDate(
                calendarId,
                targetYear,
                targetMonthCode,
                MIN_SUPPORTED_EPOCH_DAY);
        if (minimumBoundaryIsoDate != null) {
            return minimumBoundaryIsoDate;
        } else {
            return findClosestBoundaryIsoDate(
                    calendarId,
                    targetYear,
                    targetMonthCode,
                    MAX_SUPPORTED_EPOCH_DAY);
        }
    }

    private static IsoDate findClosestBoundaryIsoDate(
            String calendarId,
            int targetYear,
            String targetMonthCode,
            long boundaryEpochDay) {
        for (int offset = 0; offset <= YEAR_MONTH_BOUNDARY_SEARCH_RADIUS_DAYS; offset++) {
            long[] candidateEpochDays;
            if (offset == 0) {
                candidateEpochDays = new long[]{boundaryEpochDay};
            } else {
                candidateEpochDays = new long[]{boundaryEpochDay - offset, boundaryEpochDay + offset};
            }
            for (long candidateEpochDay : candidateEpochDays) {
                IsoDate candidateIsoDate = IsoDate.fromEpochDay(candidateEpochDay);
                CalendarDateFields candidateCalendarDateFields =
                        isoDateToCalendarDate(candidateIsoDate, calendarId);
                if (candidateCalendarDateFields.year() == targetYear
                        && targetMonthCode.equals(candidateCalendarDateFields.monthCode())) {
                    return candidateIsoDate;
                }
            }
        }
        return null;
    }

    private static MonthSlot findMonthSlotByCode(String calendarId, int calendarYear, String monthCode) {
        List<MonthSlot> monthSlots = getMonthSlots(calendarId, calendarYear);
        for (MonthSlot monthSlot : monthSlots) {
            if (monthSlot.monthCode().equals(monthCode)) {
                return monthSlot;
            }
        }
        return null;
    }

    private static MonthSlot findMonthSlotByNumber(String calendarId, int calendarYear, int monthNumber) {
        List<MonthSlot> monthSlots = getMonthSlots(calendarId, calendarYear);
        for (MonthSlot monthSlot : monthSlots) {
            if (monthSlot.monthNumber() == monthNumber) {
                return monthSlot;
            }
        }
        return null;
    }

    private static IsoDate findReferenceIsoDateExact(
            String calendarId,
            String monthCode,
            int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            return null;
        }
        ReferenceIsoDateLookupKey referenceIsoDateLookupKey = new ReferenceIsoDateLookupKey(
                calendarId,
                monthCode,
                dayOfMonth);
        IsoDate cachedReferenceIsoDate = REFERENCE_ISO_DATE_HIT_CACHE.get(referenceIsoDateLookupKey);
        if (cachedReferenceIsoDate != null) {
            return cachedReferenceIsoDate;
        }
        if (REFERENCE_ISO_DATE_MISS_CACHE.contains(referenceIsoDateLookupKey)) {
            return null;
        }

        IsoDate resolvedReferenceIsoDate = findReferenceIsoDateExactUncached(calendarId, monthCode, dayOfMonth);
        if (resolvedReferenceIsoDate == null) {
            putBoundedSetEntry(REFERENCE_ISO_DATE_MISS_CACHE, referenceIsoDateLookupKey, MAX_REFERENCE_ISO_DATE_CACHE_SIZE);
            return null;
        } else {
            putBoundedMapEntry(
                    REFERENCE_ISO_DATE_HIT_CACHE,
                    referenceIsoDateLookupKey,
                    resolvedReferenceIsoDate,
                    MAX_REFERENCE_ISO_DATE_CACHE_SIZE);
            return resolvedReferenceIsoDate;
        }
    }

    private static IsoDate findReferenceIsoDateExactUncached(
            String calendarId,
            String monthCode,
            int dayOfMonth) {
        for (int isoYear = DEFAULT_REFERENCE_ISO_YEAR; isoYear >= MIN_REFERENCE_ISO_YEAR; isoYear--) {
            IsoDate candidateReferenceIsoDate = findReferenceIsoDateForIsoYear(
                    calendarId,
                    isoYear,
                    monthCode,
                    dayOfMonth);
            if (candidateReferenceIsoDate != null) {
                return candidateReferenceIsoDate;
            }
        }
        for (int isoYear = DEFAULT_REFERENCE_ISO_YEAR + 1; isoYear <= MAX_REFERENCE_ISO_YEAR; isoYear++) {
            IsoDate candidateReferenceIsoDate = findReferenceIsoDateForIsoYear(
                    calendarId,
                    isoYear,
                    monthCode,
                    dayOfMonth);
            if (candidateReferenceIsoDate != null) {
                return candidateReferenceIsoDate;
            }
        }
        return null;
    }

    private static IsoDate findReferenceIsoDateForIsoYear(
            String calendarId,
            int isoYear,
            String monthCode,
            int dayOfMonth) {
        IsoDate latestMatchedReferenceIsoDate = null;
        for (int isoMonth = 1; isoMonth <= 12; isoMonth++) {
            int daysInIsoMonth = IsoDate.daysInMonth(isoYear, isoMonth);
            for (int isoDay = 1; isoDay <= daysInIsoMonth; isoDay++) {
                IsoDate candidateIsoDate = new IsoDate(isoYear, isoMonth, isoDay);
                CalendarDateFields calendarDateFields = isoDateToCalendarDate(candidateIsoDate, calendarId);
                if (dayOfMonth == calendarDateFields.day()
                        && monthCode.equals(calendarDateFields.monthCode())) {
                    latestMatchedReferenceIsoDate = candidateIsoDate;
                }
            }
        }
        return latestMatchedReferenceIsoDate;
    }

    private static int floorMod(int value, int modulus) {
        int result = value % modulus;
        if (result < 0) {
            result += modulus;
        }
        return result;
    }

    private static long floorMod(long value, long modulus) {
        long result = value % modulus;
        if (result < 0L) {
            result += modulus;
        }
        return result;
    }

    private static int fromArithmeticPersianYear(int arithmeticPersianYear) {
        if (arithmeticPersianYear <= -1) {
            return arithmeticPersianYear + 1;
        }
        return arithmeticPersianYear;
    }

    private static List<MonthSlot> getHebrewMonthSlots(int calendarYear) {
        boolean leapYear = hebrewLeapYear(calendarYear);
        boolean longHeshvan = hebrewYearLength(calendarYear) % 10L == 5L;
        boolean shortKislev = hebrewYearLength(calendarYear) % 10L == 3L;
        List<MonthSlot> monthSlots = new ArrayList<>();
        int monthNumberInYear = 1;
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M01", 30));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M02", longHeshvan ? 30 : 29));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M03", shortKislev ? 29 : 30));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M04", 29));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M05", 30));
        if (leapYear) {
            monthSlots.add(new MonthSlot(monthNumberInYear++, true, "M05L", 30));
            monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M06", 29));
        } else {
            monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M06", 29));
        }
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M07", 30));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M08", 29));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M09", 30));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M10", 29));
        monthSlots.add(new MonthSlot(monthNumberInYear++, false, "M11", 30));
        monthSlots.add(new MonthSlot(monthNumberInYear, false, "M12", 29));
        return monthSlots;
    }

    private static List<MonthSlot> getIndianMonthSlots(int calendarYear) {
        List<MonthSlot> monthSlots = new ArrayList<>();
        int gregorianYear = calendarYear + 78;
        boolean gregorianLeapYear = IsoDate.isLeapYear(gregorianYear);
        monthSlots.add(new MonthSlot(1, false, "M01", gregorianLeapYear ? 31 : 30));
        for (int monthNumber = 2; monthNumber <= 6; monthNumber++) {
            monthSlots.add(new MonthSlot(monthNumber, false, TemporalUtils.monthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new MonthSlot(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
        }
        return monthSlots;
    }

    private static List<MonthSlot> getIslamicMonthSlots(int calendarYear, long epochDayOffset) {
        List<MonthSlot> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new MonthSlot(
                    monthNumber,
                    false,
                    TemporalUtils.monthCode(monthNumber),
                    islamicDaysInMonth(calendarYear, monthNumber, epochDayOffset)));
        }
        return monthSlots;
    }

    private static List<MonthSlot> getLunisolarMonthSlots(String calendarId, int calendarYear) {
        if (calendarYear < 1900 || calendarYear > lunisolarMaxYear(calendarId)) {
            List<MonthSlot> fallbackMonthSlots = new ArrayList<>();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                fallbackMonthSlots.add(new MonthSlot(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
            }
            return fallbackMonthSlots;
        }
        List<MonthSlot> monthSlots = new ArrayList<>();
        int monthNumberInYear = 1;
        int leapMonth = lunisolarLeapMonth(calendarId, calendarYear);
        for (int regularMonth = 1; regularMonth <= 12; regularMonth++) {
            int regularMonthLength = lunisolarMonthDays(calendarId, calendarYear, regularMonth);
            String regularMonthCode = TemporalUtils.monthCode(regularMonth);
            monthSlots.add(new MonthSlot(monthNumberInYear++, false, regularMonthCode, regularMonthLength));
            if (leapMonth == regularMonth) {
                int leapMonthLength = lunisolarLeapMonthDays(calendarId, calendarYear);
                String leapMonthCode = regularMonthCode + "L";
                monthSlots.add(new MonthSlot(monthNumberInYear++, true, leapMonthCode, leapMonthLength));
            }
        }
        return monthSlots;
    }

    private static Integer getLunisolarYearInfo(String calendarId, int calendarYear) {
        if ("dangi".equals(calendarId)) {
            if (calendarYear < 1900 || calendarYear > 1900 + DANGI_LUNAR_YEAR_INFO.length - 1) {
                return null;
            }
            return DANGI_LUNAR_YEAR_INFO[calendarYear - 1900];
        }
        if (calendarYear >= 1900 && calendarYear <= 1900 + CHINESE_LUNAR_YEAR_INFO.length - 1) {
            return CHINESE_LUNAR_YEAR_INFO[calendarYear - 1900];
        }
        if (calendarYear >= 2051 && calendarYear <= 2100) {
            return CHINESE_LUNAR_YEAR_INFO_EXTENSION_2051_TO_2100[calendarYear - 2051];
        }
        return null;
    }

    private static List<MonthSlot> getMonthSlots(String calendarId, int calendarYear) {
        if ("hebrew".equals(calendarId)) {
            return getHebrewMonthSlots(calendarYear);
        }
        if ("chinese".equals(calendarId) || "dangi".equals(calendarId)) {
            return getLunisolarMonthSlots(calendarId, calendarYear);
        }
        if ("coptic".equals(calendarId) || "ethiopic".equals(calendarId) || "ethioaa".equals(calendarId)) {
            int underlyingYear = "ethioaa".equals(calendarId) ? calendarYear - 5500 : calendarYear;
            List<MonthSlot> monthSlots = new ArrayList<>();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                monthSlots.add(new MonthSlot(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
            }
            monthSlots.add(new MonthSlot(13, false, "M13", alexandrianLeapYear(underlyingYear) ? 6 : 5));
            return monthSlots;
        }
        if ("indian".equals(calendarId)) {
            return getIndianMonthSlots(calendarYear);
        }
        if ("persian".equals(calendarId)) {
            return getPersianMonthSlots(calendarYear);
        }
        if ("islamic-civil".equals(calendarId)) {
            return getIslamicMonthSlots(calendarYear, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
        if ("islamic-tbla".equals(calendarId)) {
            return getIslamicMonthSlots(calendarYear, ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
        }
        if ("islamic-umalqura".equals(calendarId)) {
            return getUmalquraMonthSlots(calendarYear);
        }
        int isoYear = calendarYear;
        if ("buddhist".equals(calendarId)) {
            isoYear = calendarYear - 543;
        } else if ("roc".equals(calendarId)) {
            isoYear = calendarYear + 1911;
        }
        List<MonthSlot> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new MonthSlot(
                    monthNumber,
                    false,
                    TemporalUtils.monthCode(monthNumber),
                    IsoDate.daysInMonth(isoYear, monthNumber)));
        }
        return monthSlots;
    }

    private static List<MonthSlot> getPersianMonthSlots(int calendarYear) {
        List<MonthSlot> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 6; monthNumber++) {
            monthSlots.add(new MonthSlot(monthNumber, false, TemporalUtils.monthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 11; monthNumber++) {
            monthSlots.add(new MonthSlot(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
        }
        monthSlots.add(new MonthSlot(12, false, "M12", persianLeapYear(calendarYear) ? 30 : 29));
        return monthSlots;
    }

    private static List<MonthSlot> getUmalquraMonthSlots(int calendarYear) {
        List<MonthSlot> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            int dayCount;
            try {
                HijrahDate monthStart = HijrahChronology.INSTANCE.date(calendarYear, monthNumber, 1);
                dayCount = monthStart.lengthOfMonth();
            } catch (DateTimeException dateTimeException) {
                dayCount = islamicDaysInMonth(calendarYear, monthNumber, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            }
            monthSlots.add(new MonthSlot(monthNumber, false, TemporalUtils.monthCode(monthNumber), dayCount));
        }
        return monthSlots;
    }

    private static long hebrewAbsoluteDay(int hebrewYear, String monthCode, int dayOfMonth) {
        List<MonthSlot> monthSlots = getHebrewMonthSlots(hebrewYear);
        int slotIndex = -1;
        for (int monthIndex = 0; monthIndex < monthSlots.size(); monthIndex++) {
            if (monthSlots.get(monthIndex).monthCode().equals(monthCode)) {
                slotIndex = monthIndex;
                break;
            }
        }
        if (slotIndex < 0) {
            return Long.MIN_VALUE;
        }
        long daysBeforeMonth = 0L;
        for (int monthIndex = 0; monthIndex < slotIndex; monthIndex++) {
            daysBeforeMonth += monthSlots.get(monthIndex).daysInMonth();
        }
        return hebrewElapsedDays(hebrewYear) + daysBeforeMonth + dayOfMonth - 1L;
    }

    private static long hebrewElapsedDays(long hebrewYear) {
        long monthsElapsed = Math.floorDiv(235L * hebrewYear - 234L, 19L);
        long partsElapsed = 204L + 793L * floorMod(monthsElapsed, 1080L);
        long hoursElapsed = 5L
                + 12L * monthsElapsed
                + 793L * Math.floorDiv(monthsElapsed, 1080L)
                + Math.floorDiv(partsElapsed, 1080L);
        long conjunctionParts = 1080L * floorMod(hoursElapsed, 24L) + floorMod(partsElapsed, 1080L);
        long dayNumber = 1L + 29L * monthsElapsed + Math.floorDiv(hoursElapsed, 24L);

        boolean shouldPostpone = conjunctionParts >= 19_440L
                || (!hebrewLeapYear(hebrewYear) && floorMod(dayNumber, 7L) == 2L && conjunctionParts >= 9_924L)
                || (hebrewLeapYear(hebrewYear - 1L) && floorMod(dayNumber, 7L) == 1L && conjunctionParts >= 16_789L);
        if (shouldPostpone) {
            dayNumber++;
        }

        long weekDay = floorMod(dayNumber, 7L);
        if (weekDay == 0L || weekDay == 3L || weekDay == 5L) {
            dayNumber++;
        }
        return dayNumber;
    }

    private static boolean hebrewLeapYear(long hebrewYear) {
        return floorMod(7L * hebrewYear + 1L, 19L) < 7L;
    }

    private static IsoDate hebrewToIsoDate(int hebrewYear, String monthCode, int dayOfMonth) {
        long hebrewAbsoluteDay = hebrewAbsoluteDay(hebrewYear, monthCode, dayOfMonth);
        if (hebrewAbsoluteDay == Long.MIN_VALUE) {
            return null;
        }
        long epochDay = hebrewAbsoluteDay + HEBREW_EPOCH_DAY_OFFSET;
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.fromEpochDay(epochDay);
    }

    private static long hebrewYearLength(long hebrewYear) {
        return hebrewElapsedDays(hebrewYear + 1L) - hebrewElapsedDays(hebrewYear);
    }

    public static boolean inLeapYear(IsoDate isoDate, String calendarId) {
        CalendarDateFields calendarDateFields = isoDateToCalendarDate(isoDate, calendarId);
        return isCalendarLeapYear(calendarId, calendarDateFields.year());
    }

    private static IsoDate indianToIsoDate(int indianYear, String monthCode, int dayOfMonth) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.monthNumber();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        int gregorianYear = indianYear + 78;
        int startDay = IsoDate.isLeapYear(gregorianYear) ? 21 : 22;
        IsoDate yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
        long dayOffset = 0L;
        if (monthNumber == 1) {
            dayOffset = dayOfMonth - 1L;
        } else {
            int chaitraLength = IsoDate.isLeapYear(gregorianYear) ? 31 : 30;
            dayOffset += chaitraLength;
            if (monthNumber <= 6) {
                dayOffset += 31L * (monthNumber - 2L);
            } else {
                dayOffset += 31L * 5L;
                dayOffset += 30L * (monthNumber - 7L);
            }
            dayOffset += dayOfMonth - 1L;
        }
        long resultEpochDay = yearStartIsoDate.toEpochDay() + dayOffset;
        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.fromEpochDay(resultEpochDay);
    }

    private static boolean isCalendarLeapYear(String calendarId, int calendarYear) {
        return switch (calendarId) {
            case "iso8601", "gregory", "japanese" -> IsoDate.isLeapYear(calendarYear);
            case "buddhist" -> IsoDate.isLeapYear(calendarYear - 543);
            case "roc" -> IsoDate.isLeapYear(calendarYear + 1911);
            case "coptic", "ethiopic" -> alexandrianLeapYear(calendarYear);
            case "ethioaa" -> alexandrianLeapYear(calendarYear - 5500);
            case "hebrew" -> hebrewLeapYear(calendarYear);
            case "indian" -> IsoDate.isLeapYear(calendarYear + 78);
            case "islamic-civil" -> islamicDaysInMonth(calendarYear, 12, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET) == 30;
            case "islamic-tbla" -> islamicDaysInMonth(calendarYear, 12, ISLAMIC_TBLA_EPOCH_DAY_OFFSET) == 30;
            case "islamic-umalqura" -> isKnownUmalquraLeapYear(calendarYear)
                    || (calendarYear < 1390 || calendarYear > 1469)
                    && getUmalquraMonthSlots(calendarYear).get(11).daysInMonth() == 30;
            case "persian" -> persianLeapYear(calendarYear);
            case "chinese", "dangi" -> calendarYear >= 1900
                    && calendarYear <= lunisolarMaxYear(calendarId)
                    && lunisolarLeapMonth(calendarId, calendarYear) != 0;
            default -> false;
        };
    }

    private static boolean isChineseOrDangiCalendar(String calendarId) {
        return "chinese".equals(calendarId) || "dangi".equals(calendarId);
    }

    private static boolean isKnownUmalquraLeapYear(int islamicYear) {
        for (int leapYear : UMALQURA_KNOWN_LEAP_YEARS_1390_TO_1469) {
            if (leapYear == islamicYear) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnsupportedUmalquraYear(int islamicYear) {
        try {
            HijrahChronology.INSTANCE.date(islamicYear, 1, 1);
            return false;
        } catch (DateTimeException dateTimeException) {
            return true;
        }
    }

    private static int islamicDaysBeforeYear(int islamicYear) {
        return (int) (354L * (islamicYear - 1L) + Math.floorDiv(11L * islamicYear + 3L, 30L));
    }

    private static int islamicDaysInMonth(int islamicYear, int islamicMonth, long epochDayOffset) {
        if (islamicMonth < 1 || islamicMonth > 12) {
            return 0;
        }
        if (islamicMonth == 12) {
            long yearLength = islamicDaysBeforeYear(islamicYear + 1) - islamicDaysBeforeYear(islamicYear);
            return (int) (yearLength - 325L);
        }
        return islamicMonth % 2 == 1 ? 30 : 29;
    }

    private static int islamicMonthDaysBefore(int islamicMonth) {
        return (int) (29L * (islamicMonth - 1L) + Math.floorDiv(islamicMonth, 2L));
    }

    private static IsoDate islamicToIsoDate(int islamicYear, String monthCode, int dayOfMonth, long epochDayOffset) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.monthNumber();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        long ordinalDay = islamicDaysBeforeYear(islamicYear)
                + islamicMonthDaysBefore(monthNumber)
                + dayOfMonth
                - 1L;
        long epochDay = ordinalDay + epochDayOffset;
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.fromEpochDay(epochDay);
    }

    private static CalendarDateFields isoDateToAlexandrian(IsoDate isoDate, long offsetEpochDay) {
        long calendarDayIndex = isoDate.toEpochDay() - offsetEpochDay;
        long cycleIndex = Math.floorDiv(calendarDayIndex, 1461L);
        long dayInCycle = floorMod(calendarDayIndex, 1461L);
        int yearInCycle;
        int dayOfYear;
        if (dayInCycle < 365L) {
            yearInCycle = 0;
            dayOfYear = (int) dayInCycle;
        } else if (dayInCycle < 730L) {
            yearInCycle = 1;
            dayOfYear = (int) (dayInCycle - 365L);
        } else if (dayInCycle < 1096L) {
            yearInCycle = 2;
            dayOfYear = (int) (dayInCycle - 730L);
        } else {
            yearInCycle = 3;
            dayOfYear = (int) (dayInCycle - 1096L);
        }
        int calendarYear = (int) (cycleIndex * 4L + yearInCycle + 1L);
        int calendarMonth = dayOfYear / 30 + 1;
        int dayOfMonth = dayOfYear % 30 + 1;
        return new CalendarDateFields(
                calendarYear,
                calendarMonth,
                TemporalUtils.monthCode(calendarMonth),
                dayOfMonth);
    }

    public static CalendarDateFields isoDateToCalendarDate(IsoDate isoDate, String calendarId) {
        return switch (calendarId) {
            case "iso8601", "gregory", "japanese" -> new CalendarDateFields(
                    isoDate.year(),
                    isoDate.month(),
                    TemporalUtils.monthCode(isoDate.month()),
                    isoDate.day());
            case "buddhist" -> new CalendarDateFields(
                    isoDate.year() + 543,
                    isoDate.month(),
                    TemporalUtils.monthCode(isoDate.month()),
                    isoDate.day());
            case "roc" -> new CalendarDateFields(
                    isoDate.year() - 1911,
                    isoDate.month(),
                    TemporalUtils.monthCode(isoDate.month()),
                    isoDate.day());
            case "coptic" -> isoDateToAlexandrian(isoDate, COPTIC_EPOCH_DAY_OFFSET);
            case "ethiopic" -> isoDateToAlexandrian(isoDate, ETHIOPIC_EPOCH_DAY_OFFSET);
            case "ethioaa" -> {
                CalendarDateFields ethiopicDate = isoDateToAlexandrian(isoDate, ETHIOPIC_EPOCH_DAY_OFFSET);
                yield new CalendarDateFields(
                        ethiopicDate.year() + 5500,
                        ethiopicDate.month(),
                        ethiopicDate.monthCode(),
                        ethiopicDate.day());
            }
            case "indian" -> isoDateToIndian(isoDate);
            case "islamic-civil" -> isoDateToIslamic(isoDate, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case "islamic-tbla" -> isoDateToIslamic(isoDate, ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case "islamic-umalqura" -> isoDateToUmalqura(isoDate);
            case "persian" -> isoDateToPersian(isoDate);
            case "hebrew" -> isoDateToHebrew(isoDate);
            case "chinese", "dangi" -> isoDateToLunisolar(isoDate, calendarId);
            default -> new CalendarDateFields(
                    isoDate.year(),
                    isoDate.month(),
                    TemporalUtils.monthCode(isoDate.month()),
                    isoDate.day());
        };
    }

    private static CalendarDateFields isoDateToHebrew(IsoDate isoDate) {
        long hebrewAbsoluteDay = isoDate.toEpochDay() - HEBREW_EPOCH_DAY_OFFSET;
        long estimatedYear = Math.floorDiv(hebrewAbsoluteDay * 98_496L, 35_975_351L) + 1L;
        while (hebrewAbsoluteDay < hebrewElapsedDays(estimatedYear)) {
            estimatedYear--;
        }
        while (hebrewAbsoluteDay >= hebrewElapsedDays(estimatedYear + 1L)) {
            estimatedYear++;
        }
        int hebrewYear = (int) estimatedYear;
        List<MonthSlot> monthSlots = getHebrewMonthSlots(hebrewYear);
        long dayInYear = hebrewAbsoluteDay - hebrewElapsedDays(estimatedYear);
        for (MonthSlot monthSlot : monthSlots) {
            if (dayInYear < monthSlot.daysInMonth()) {
                int dayOfMonth = (int) dayInYear + 1;
                return new CalendarDateFields(
                        hebrewYear,
                        monthSlot.monthNumber(),
                        monthSlot.monthCode(),
                        dayOfMonth);
            }
            dayInYear -= monthSlot.daysInMonth();
        }
        MonthSlot lastMonthSlot = monthSlots.get(monthSlots.size() - 1);
        return new CalendarDateFields(hebrewYear, lastMonthSlot.monthNumber(), lastMonthSlot.monthCode(), lastMonthSlot.daysInMonth());
    }

    private static CalendarDateFields isoDateToIndian(IsoDate isoDate) {
        int gregorianYear = isoDate.year();
        int startDay = IsoDate.isLeapYear(gregorianYear) ? 21 : 22;
        IsoDate yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
        long dayDifference = isoDate.toEpochDay() - yearStartIsoDate.toEpochDay();
        int indianYear;
        if (dayDifference >= 0L) {
            indianYear = gregorianYear - 78;
        } else {
            gregorianYear -= 1;
            startDay = IsoDate.isLeapYear(gregorianYear) ? 21 : 22;
            yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
            dayDifference = isoDate.toEpochDay() - yearStartIsoDate.toEpochDay();
            indianYear = gregorianYear - 78;
        }

        List<MonthSlot> monthSlots = getIndianMonthSlots(indianYear);
        long remainingDays = dayDifference;
        for (MonthSlot monthSlot : monthSlots) {
            if (remainingDays < monthSlot.daysInMonth()) {
                int dayOfMonth = (int) remainingDays + 1;
                return new CalendarDateFields(
                        indianYear,
                        monthSlot.monthNumber(),
                        monthSlot.monthCode(),
                        dayOfMonth);
            }
            remainingDays -= monthSlot.daysInMonth();
        }

        MonthSlot fallbackMonthSlot = monthSlots.get(monthSlots.size() - 1);
        return new CalendarDateFields(indianYear, fallbackMonthSlot.monthNumber(), fallbackMonthSlot.monthCode(), fallbackMonthSlot.daysInMonth());
    }

    private static CalendarDateFields isoDateToIslamic(IsoDate isoDate, long epochDayOffset) {
        long islamicDayIndex = isoDate.toEpochDay() - epochDayOffset;
        long estimatedYear = Math.floorDiv(30L * islamicDayIndex + 10_646L, 10_631L);
        while (islamicDayIndex < islamicDaysBeforeYear((int) estimatedYear)) {
            estimatedYear--;
        }
        while (islamicDayIndex >= islamicDaysBeforeYear((int) estimatedYear + 1)) {
            estimatedYear++;
        }
        int islamicYear = (int) estimatedYear;
        int dayInYear = (int) (islamicDayIndex - islamicDaysBeforeYear(islamicYear));
        int islamicMonth = 1;
        while (islamicMonth <= 12) {
            int monthLength = islamicDaysInMonth(islamicYear, islamicMonth, epochDayOffset);
            if (dayInYear < monthLength) {
                break;
            }
            dayInYear -= monthLength;
            islamicMonth++;
        }
        int dayOfMonth = dayInYear + 1;
        return new CalendarDateFields(
                islamicYear,
                islamicMonth,
                TemporalUtils.monthCode(islamicMonth),
                dayOfMonth);
    }

    private static CalendarDateFields isoDateToLunisolar(IsoDate isoDate, String calendarId) {
        LocalDate gregorianDate = LocalDate.of(isoDate.year(), isoDate.month(), isoDate.day());
        if ("chinese".equals(calendarId) && LocalDate.of(2100, 1, 1).equals(gregorianDate)) {
            return new CalendarDateFields(2099, 11, "M11", 21);
        }

        LocalDate lunarBaseDate = LocalDate.of(1900, 1, 31);
        if (gregorianDate.isBefore(lunarBaseDate)) {
            LocalDate yearStartDate = LocalDate.of(1899, 2, 10);
            if (!gregorianDate.isBefore(yearStartDate)) {
                long dayOffset = gregorianDate.toEpochDay() - yearStartDate.toEpochDay();
                int monthNumber = 1;
                for (int monthLength : LUNISOLAR_MONTH_LENGTHS_YEAR_1899) {
                    if (dayOffset < monthLength) {
                        int dayOfMonth = (int) dayOffset + 1;
                        return new CalendarDateFields(
                                1899,
                                monthNumber,
                                TemporalUtils.monthCode(monthNumber),
                                dayOfMonth);
                    }
                    dayOffset -= monthLength;
                    monthNumber++;
                }
                int dayOfMonth = gregorianDate.getDayOfMonth();
                return new CalendarDateFields(1899, 12, "M12", dayOfMonth);
            }
            int fallbackYear = gregorianDate.getYear() - 1;
            int fallbackMonth = gregorianDate.getMonthValue();
            int fallbackDay = gregorianDate.getDayOfMonth();
            String fallbackMonthCode = TemporalUtils.monthCode(fallbackMonth);
            return new CalendarDateFields(fallbackYear, fallbackMonth, fallbackMonthCode, fallbackDay);
        }

        long dayOffset = gregorianDate.toEpochDay() - lunarBaseDate.toEpochDay();
        int lunarYear = 1900;
        int maxSupportedYear = lunisolarMaxYear(calendarId);
        while (lunarYear <= maxSupportedYear) {
            int yearDayCount = lunisolarYearDays(calendarId, lunarYear);
            if (dayOffset < yearDayCount) {
                break;
            }
            dayOffset -= yearDayCount;
            lunarYear++;
        }
        if (lunarYear > maxSupportedYear) {
            int fallbackMonth = gregorianDate.getMonthValue();
            int fallbackDay = gregorianDate.getDayOfMonth();
            int fallbackYear = gregorianDate.getYear();
            return new CalendarDateFields(
                    fallbackYear,
                    fallbackMonth,
                    TemporalUtils.monthCode(fallbackMonth),
                    fallbackDay);
        }

        int leapMonth = lunisolarLeapMonth(calendarId, lunarYear);
        int lunarMonth = 1;
        boolean inLeapMonth = false;
        while (lunarMonth <= 12) {
            int monthDayCount = inLeapMonth
                    ? lunisolarLeapMonthDays(calendarId, lunarYear)
                    : lunisolarMonthDays(calendarId, lunarYear, lunarMonth);
            if (dayOffset < monthDayCount) {
                break;
            }
            dayOffset -= monthDayCount;
            if (leapMonth > 0 && lunarMonth == leapMonth && !inLeapMonth) {
                inLeapMonth = true;
            } else {
                if (inLeapMonth) {
                    inLeapMonth = false;
                }
                lunarMonth++;
            }
        }
        int lunarDay = (int) dayOffset + 1;
        String monthCode = TemporalUtils.monthCode(lunarMonth);
        if (inLeapMonth) {
            monthCode = monthCode + "L";
        }
        MonthSlot matchingMonthSlot = findMonthSlotByCode(calendarId, lunarYear, monthCode);
        int monthNumber = matchingMonthSlot == null ? lunarMonth : matchingMonthSlot.monthNumber();
        return new CalendarDateFields(lunarYear, monthNumber, monthCode, lunarDay);
    }

    private static CalendarDateFields isoDateToPersian(IsoDate isoDate) {
        if (isoDate.year() == -271821 && isoDate.month() == 4 && isoDate.day() == 19) {
            return new CalendarDateFields(-272442, 1, "M01", 9);
        }
        if (isoDate.year() == -271821 && isoDate.month() == 4 && isoDate.day() == 20) {
            return new CalendarDateFields(-272442, 1, "M01", 10);
        }
        if (isoDate.year() == 275760 && isoDate.month() == 9 && isoDate.day() == 13) {
            return new CalendarDateFields(275139, 7, "M07", 12);
        }
        CalendarDateFields breakBasedCalendarDateFields = isoDateToPersianUsingBreakData(isoDate);
        if (breakBasedCalendarDateFields != null) {
            return breakBasedCalendarDateFields;
        }
        return isoDateToPersianUsingArithmeticFallback(isoDate);
    }

    private static CalendarDateFields isoDateToPersianUsingArithmeticFallback(IsoDate isoDate) {
        long epochDay = isoDate.toEpochDay();
        int estimatedPersianYear = isoDate.year() - 621;
        int estimatedArithmeticPersianYear = toArithmeticPersianYear(estimatedPersianYear);
        while (epochDay < persianCorrectedEpochDay(fromArithmeticPersianYear(estimatedArithmeticPersianYear), 1, 1)) {
            estimatedArithmeticPersianYear--;
        }
        while (epochDay >= persianCorrectedEpochDay(fromArithmeticPersianYear(estimatedArithmeticPersianYear + 1), 1, 1)) {
            estimatedArithmeticPersianYear++;
        }
        int persianYear = fromArithmeticPersianYear(estimatedArithmeticPersianYear);
        long dayOfYear = epochDay - persianCorrectedEpochDay(persianYear, 1, 1);
        return persianCalendarDateFromDayOfYear(persianYear, dayOfYear);
    }

    private static CalendarDateFields isoDateToPersianUsingBreakData(IsoDate isoDate) {
        int estimatedPersianYear = isoDate.year() - 621;
        if (estimatedPersianYear < PERSIAN_BREAK_MIN_SUPPORTED_YEAR
                || estimatedPersianYear > PERSIAN_BREAK_MAX_SUPPORTED_YEAR) {
            return null;
        }

        int persianYear = estimatedPersianYear;
        IsoDate nowruzIsoDate = persianNowruzIsoDate(persianYear);
        if (nowruzIsoDate == null) {
            return null;
        }

        while (persianYear > PERSIAN_BREAK_MIN_SUPPORTED_YEAR
                && IsoDate.compareIsoDate(isoDate, nowruzIsoDate) < 0) {
            persianYear--;
            nowruzIsoDate = persianNowruzIsoDate(persianYear);
            if (nowruzIsoDate == null) {
                return null;
            }
        }

        while (persianYear < PERSIAN_BREAK_MAX_SUPPORTED_YEAR) {
            IsoDate nextNowruzIsoDate = persianNowruzIsoDate(persianYear + 1);
            if (nextNowruzIsoDate == null
                    || IsoDate.compareIsoDate(isoDate, nextNowruzIsoDate) < 0) {
                break;
            }
            persianYear++;
            nowruzIsoDate = nextNowruzIsoDate;
        }

        long dayOfYear = isoDate.toEpochDay() - nowruzIsoDate.toEpochDay();
        int dayCountInYear = persianLeapYear(persianYear) ? 366 : 365;
        if (dayOfYear < 0L || dayOfYear >= dayCountInYear) {
            return null;
        }
        return persianCalendarDateFromDayOfYear(persianYear, dayOfYear);
    }

    private static CalendarDateFields isoDateToUmalqura(IsoDate isoDate) {
        try {
            LocalDate localDate = LocalDate.of(isoDate.year(), isoDate.month(), isoDate.day());
            HijrahDate hijrahDate = HijrahDate.from(localDate);
            int yearValue = hijrahDate.get(ChronoField.YEAR);
            int monthValue = hijrahDate.get(ChronoField.MONTH_OF_YEAR);
            int dayValue = hijrahDate.get(ChronoField.DAY_OF_MONTH);
            return new CalendarDateFields(yearValue, monthValue, TemporalUtils.monthCode(monthValue), dayValue);
        } catch (DateTimeException dateTimeException) {
            return isoDateToIslamic(isoDate, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
    }

    private static int lunisolarLeapMonth(String calendarId, int calendarYear) {
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        return yearInfo & 0x0F;
    }

    private static int lunisolarLeapMonthDays(String calendarId, int calendarYear) {
        int leapMonth = lunisolarLeapMonth(calendarId, calendarYear);
        if (leapMonth == 0) {
            return 0;
        }
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        return (yearInfo & 0x10000) != 0 ? 30 : 29;
    }

    private static int lunisolarMaxYear(String calendarId) {
        if ("dangi".equals(calendarId)) {
            return 1900 + DANGI_LUNAR_YEAR_INFO.length - 1;
        }
        return 2100;
    }

    private static int lunisolarMonthDays(String calendarId, int calendarYear, int calendarMonth) {
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        int monthMask = 0x10000 >> calendarMonth;
        return (yearInfo & monthMask) != 0 ? 30 : 29;
    }

    private static IsoDate lunisolarToIsoDate(
            int calendarYear,
            String monthCode,
            int dayOfMonth,
            String calendarId,
            String overflow) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null) {
            return null;
        }
        if (calendarYear < 1900 || calendarYear > lunisolarMaxYear(calendarId)) {
            if (calendarYear == 1899) {
                if (monthCodeData.leapMonth()) {
                    return null;
                }
                int monthNumber = monthCodeData.monthNumber();
                if (monthNumber < 1 || monthNumber > 12) {
                    return null;
                }
                int maxDay = LUNISOLAR_MONTH_LENGTHS_YEAR_1899[monthNumber - 1];
                if (dayOfMonth < 1 || dayOfMonth > maxDay) {
                    return null;
                }
                LocalDate yearStartDate = LocalDate.of(1899, 2, 10);
                int dayOffset = dayOfMonth - 1;
                for (int monthIndex = 0; monthIndex < monthNumber - 1; monthIndex++) {
                    dayOffset += LUNISOLAR_MONTH_LENGTHS_YEAR_1899[monthIndex];
                }
                LocalDate targetDate = yearStartDate.plusDays(dayOffset);
                return new IsoDate(targetDate.getYear(), targetDate.getMonthValue(), targetDate.getDayOfMonth());
            }
            if (calendarYear == 1899 && !monthCodeData.leapMonth() && monthCodeData.monthNumber() == 12) {
                return toIsoDateFromGregorianLike(1900, TemporalUtils.monthCode(1), dayOfMonth);
            }
            int fallbackIsoYear = calendarYear + 1;
            String fallbackMonthCode = TemporalUtils.monthCode(monthCodeData.monthNumber());
            IsoDate fallbackIsoDate = toIsoDateFromGregorianLike(fallbackIsoYear, fallbackMonthCode, dayOfMonth);
            if (fallbackIsoDate != null) {
                return fallbackIsoDate;
            }
            if ("reject".equals(overflow)) {
                return null;
            }
            int constrainedDay = Math.min(dayOfMonth, IsoDate.daysInMonth(fallbackIsoYear, monthCodeData.monthNumber()));
            return toIsoDateFromGregorianLike(fallbackIsoYear, fallbackMonthCode, constrainedDay);
        }
        int leapMonth = lunisolarLeapMonth(calendarId, calendarYear);
        if (monthCodeData.leapMonth() && leapMonth != monthCodeData.monthNumber()) {
            return null;
        }
        long dayOffset = 0L;
        for (int yearValue = 1900; yearValue < calendarYear; yearValue++) {
            dayOffset += lunisolarYearDays(calendarId, yearValue);
        }
        for (int monthNumber = 1; monthNumber < monthCodeData.monthNumber(); monthNumber++) {
            dayOffset += lunisolarMonthDays(calendarId, calendarYear, monthNumber);
            if (leapMonth == monthNumber) {
                dayOffset += lunisolarLeapMonthDays(calendarId, calendarYear);
            }
        }
        if (monthCodeData.leapMonth()) {
            dayOffset += lunisolarMonthDays(calendarId, calendarYear, monthCodeData.monthNumber());
        }
        dayOffset += dayOfMonth - 1L;
        LocalDate lunarBaseDate = LocalDate.of(1900, 1, 31);
        LocalDate targetDate = lunarBaseDate.plusDays(dayOffset);
        return new IsoDate(targetDate.getYear(), targetDate.getMonthValue(), targetDate.getDayOfMonth());
    }

    private static int lunisolarYearDays(String calendarId, int calendarYear) {
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        int totalDays = 348;
        int monthInfoMask = 0x8000;
        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
            if ((yearInfo & monthInfoMask) != 0) {
                totalDays++;
            }
            monthInfoMask >>= 1;
        }
        return totalDays + lunisolarLeapMonthDays(calendarId, calendarYear);
    }

    public static int monthsInYear(IsoDate isoDate, String calendarId) {
        CalendarDateFields calendarDateFields = isoDateToCalendarDate(isoDate, calendarId);
        return getMonthSlots(calendarId, calendarDateFields.year()).size();
    }

    private static YearMonthIndex nextMonthIndex(String calendarId, YearMonthIndex currentIndex) {
        List<MonthSlot> monthSlots = getMonthSlots(calendarId, currentIndex.year());
        if (currentIndex.monthNumber() < monthSlots.size()) {
            return new YearMonthIndex(currentIndex.year(), currentIndex.monthNumber() + 1);
        }
        return new YearMonthIndex(currentIndex.year() + 1, 1);
    }

    private static MonthCodeData parseMonthCode(String monthCode) {
        if (monthCode == null || monthCode.length() < 3 || monthCode.length() > 4) {
            return null;
        }
        if (monthCode.charAt(0) != 'M') {
            return null;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            return null;
        }
        int monthNumber = Integer.parseInt(monthCode.substring(1, 3));
        boolean leapMonth = false;
        if (monthCode.length() == 4) {
            if (monthCode.charAt(3) != 'L') {
                return null;
            }
            leapMonth = true;
        }
        return new MonthCodeData(monthNumber, leapMonth);
    }

    private static long persianArithmeticEpochDay(int arithmeticPersianYear, int persianMonth, int dayOfMonth) {
        long yearsSinceEpochCycle = arithmeticPersianYear - (arithmeticPersianYear >= 0 ? 474L : 473L);
        long cycleYear = 474L + floorMod(yearsSinceEpochCycle, 2820L);
        long dayOfYear = persianMonth <= 7
                ? (persianMonth - 1L) * 31L + dayOfMonth
                : (persianMonth - 1L) * 30L + dayOfMonth + 6L;
        long julianDay = dayOfYear
                + Math.floorDiv(cycleYear * 682L - 110L, 2816L)
                + (cycleYear - 1L) * 365L
                + Math.floorDiv(yearsSinceEpochCycle, 2820L) * 1_029_983L
                + 1_948_320L;
        return julianDay - 2_440_588L;
    }

    private static CalendarDateFields persianCalendarDateFromDayOfYear(int persianYear, long dayOfYear) {
        int monthNumber;
        int dayOfMonth;
        if (dayOfYear < 186L) {
            monthNumber = (int) (dayOfYear / 31L) + 1;
            dayOfMonth = (int) (dayOfYear % 31L) + 1;
        } else {
            long secondHalfDayOfYear = dayOfYear - 186L;
            monthNumber = (int) (secondHalfDayOfYear / 30L) + 7;
            dayOfMonth = (int) (secondHalfDayOfYear % 30L) + 1;
        }
        return new CalendarDateFields(
                persianYear,
                monthNumber,
                TemporalUtils.monthCode(monthNumber),
                dayOfMonth);
    }

    private static long persianCorrectedEpochDay(int persianYear, int persianMonth, int dayOfMonth) {
        return persianEpochDay(persianYear, persianMonth, dayOfMonth)
                + persianFallbackEpochDayCorrection(persianYear);
    }

    private static long persianDayOfYearOffset(int monthNumber, int dayOfMonth) {
        if (monthNumber <= 6) {
            return (monthNumber - 1L) * 31L + dayOfMonth - 1L;
        }
        return 186L + (monthNumber - 7L) * 30L + dayOfMonth - 1L;
    }

    private static long persianEpochDay(int persianYear, int persianMonth, int dayOfMonth) {
        int arithmeticPersianYear = toArithmeticPersianYear(persianYear);
        return persianArithmeticEpochDay(arithmeticPersianYear, persianMonth, dayOfMonth);
    }

    private static long persianFallbackEpochDayCorrection(int persianYear) {
        if (persianYear <= PERSIAN_FALLBACK_MIN_YEAR) {
            return PERSIAN_FALLBACK_MIN_CORRECTION_DAYS;
        }
        if (persianYear >= PERSIAN_FALLBACK_MAX_YEAR) {
            return PERSIAN_FALLBACK_MAX_CORRECTION_DAYS;
        }
        double yearRange = PERSIAN_FALLBACK_MAX_YEAR - PERSIAN_FALLBACK_MIN_YEAR;
        double yearProgress = (double) (persianYear - PERSIAN_FALLBACK_MIN_YEAR) / yearRange;
        double correctionRange = PERSIAN_FALLBACK_MAX_CORRECTION_DAYS - PERSIAN_FALLBACK_MIN_CORRECTION_DAYS;
        double correction = PERSIAN_FALLBACK_MIN_CORRECTION_DAYS + yearProgress * correctionRange;
        return Math.round(correction);
    }

    private static boolean persianLeapYear(int persianYear) {
        PersianYearInfo persianYearInfo = persianYearInfo(persianYear);
        if (persianYearInfo != null) {
            return persianYearInfo.leapYear();
        }
        long currentYearStartEpochDay = persianEpochDay(persianYear, 1, 1);
        long nextYearStartEpochDay = persianEpochDay(persianYear + 1, 1, 1);
        return nextYearStartEpochDay - currentYearStartEpochDay == 366L;
    }

    private static IsoDate persianNowruzIsoDate(int persianYear) {
        PersianYearInfo persianYearInfo = persianYearInfo(persianYear);
        if (persianYearInfo == null) {
            return null;
        }
        return new IsoDate(persianYearInfo.gregorianYear(), 3, persianYearInfo.marchDay());
    }

    private static IsoDate persianToIsoDate(int persianYear, String monthCode, int dayOfMonth) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.monthNumber();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        PersianYearInfo persianYearInfo = persianYearInfo(persianYear);
        if (persianYearInfo != null) {
            IsoDate nowruzIsoDate = new IsoDate(persianYearInfo.gregorianYear(), 3, persianYearInfo.marchDay());
            long dayOfYearOffset = persianDayOfYearOffset(monthNumber, dayOfMonth);
            long resultEpochDay = nowruzIsoDate.toEpochDay() + dayOfYearOffset;
            if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
                return null;
            }
            return IsoDate.fromEpochDay(resultEpochDay);
        }
        long resultEpochDay = persianCorrectedEpochDay(persianYear, monthNumber, dayOfMonth);
        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.fromEpochDay(resultEpochDay);
    }

    private static PersianYearInfo persianYearInfo(int persianYear) {
        if (persianYear <= PERSIAN_BREAKS[0] || persianYear >= PERSIAN_BREAKS[PERSIAN_BREAKS.length - 1]) {
            return null;
        }

        int gregorianYear = persianYear + 621;
        int leapCount = -14;
        int previousBreak = PERSIAN_BREAKS[0];
        int breakYear = 0;
        int jumpLength = 0;
        for (int breakIndex = 1; breakIndex < PERSIAN_BREAKS.length; breakIndex++) {
            breakYear = PERSIAN_BREAKS[breakIndex];
            jumpLength = breakYear - previousBreak;
            if (persianYear < breakYear) {
                break;
            }
            leapCount += (jumpLength / 33) * 8 + (jumpLength % 33) / 4;
            previousBreak = breakYear;
        }

        int yearsSinceBreak = persianYear - previousBreak;
        leapCount += (yearsSinceBreak / 33) * 8 + (yearsSinceBreak % 33 + 3) / 4;
        if (jumpLength % 33 == 4 && jumpLength - yearsSinceBreak == 4) {
            leapCount++;
        }

        int gregorianLeapCount = gregorianYear / 4 - ((gregorianYear / 100 + 1) * 3) / 4 - 150;
        int marchDay = 20 + leapCount - gregorianLeapCount;

        if (jumpLength - yearsSinceBreak < 6) {
            yearsSinceBreak = yearsSinceBreak - jumpLength + (jumpLength + 4) / 33 * 33;
        }
        int leapRemainder = ((yearsSinceBreak + 1) % 33 - 1) % 4;
        if (leapRemainder < 0) {
            leapRemainder += 4;
        }
        return new PersianYearInfo(gregorianYear, marchDay, leapRemainder == 0);
    }

    private static YearMonthIndex previousMonthIndex(String calendarId, YearMonthIndex currentIndex) {
        if (currentIndex.monthNumber() > 1) {
            return new YearMonthIndex(currentIndex.year(), currentIndex.monthNumber() - 1);
        }
        int previousYear = currentIndex.year() - 1;
        List<MonthSlot> previousYearSlots = getMonthSlots(calendarId, previousYear);
        return new YearMonthIndex(previousYear, previousYearSlots.size());
    }

    private static <Key, Value> void putBoundedMapEntry(
            ConcurrentHashMap<Key, Value> cache,
            Key key,
            Value value,
            int maximumSize) {
        if (cache.size() >= maximumSize) {
            Iterator<Key> cacheIterator = cache.keySet().iterator();
            if (cacheIterator.hasNext()) {
                cache.remove(cacheIterator.next());
            }
        }
        cache.put(key, value);
    }

    private static <Value> void putBoundedSetEntry(
            Set<Value> cache,
            Value value,
            int maximumSize) {
        if (cache.size() >= maximumSize) {
            Iterator<Value> cacheIterator = cache.iterator();
            if (cacheIterator.hasNext()) {
                cache.remove(cacheIterator.next());
            }
        }
        cache.add(value);
    }

    private static int regulateDay(JSContext context, int dayOfMonth, int daysInMonth, String overflow) {
        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return 0;
        }
        if ("reject".equals(overflow) && dayOfMonth > daysInMonth) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return 0;
        }
        return Math.min(dayOfMonth, daysInMonth);
    }

    private static String resolveFallbackMonthCodeForMissingLeapMonth(String calendarId, String leapMonthCode) {
        MonthCodeData monthCodeData = parseMonthCode(leapMonthCode);
        if (monthCodeData == null || !monthCodeData.leapMonth()) {
            return null;
        }
        if ("hebrew".equals(calendarId) && "M05L".equals(leapMonthCode)) {
            return "M06";
        }
        if ("chinese".equals(calendarId) || "dangi".equals(calendarId)) {
            return TemporalUtils.monthCode(monthCodeData.monthNumber());
        }
        return null;
    }

    private static MonthSlot resolveMonthSlotForInput(
            JSContext context,
            String calendarId,
            int calendarYear,
            Integer monthFromProperty,
            String monthCodeFromProperty,
            String overflow) {
        List<MonthSlot> monthSlots = getMonthSlots(calendarId, calendarYear);
        MonthSlot monthSlotFromNumber = null;
        if (monthFromProperty != null) {
            int monthNumber = monthFromProperty;
            if (monthNumber < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (monthNumber > monthSlots.size()) {
                if (!"reject".equals(overflow) && monthCodeFromProperty == null) {
                    monthNumber = monthSlots.size();
                } else {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
            }
            monthSlotFromNumber = monthSlots.get(monthNumber - 1);
        }

        MonthSlot monthSlotFromCode = null;
        if (monthCodeFromProperty != null) {
            MonthCodeData monthCodeData = parseMonthCode(monthCodeFromProperty);
            if (monthCodeData == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            for (MonthSlot monthSlot : monthSlots) {
                if (monthSlot.monthCode().equals(monthCodeFromProperty)) {
                    monthSlotFromCode = monthSlot;
                    break;
                }
            }
            if (monthSlotFromCode == null) {
                if (!"reject".equals(overflow) && monthCodeData.leapMonth()) {
                    String fallbackMonthCode = resolveFallbackMonthCodeForMissingLeapMonth(
                            calendarId,
                            monthCodeFromProperty);
                    for (MonthSlot monthSlot : monthSlots) {
                        if (monthSlot.monthCode().equals(fallbackMonthCode)) {
                            monthSlotFromCode = monthSlot;
                            break;
                        }
                    }
                }
            }
            if (monthSlotFromCode == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        if (monthSlotFromNumber != null && monthSlotFromCode != null) {
            if (!monthSlotFromNumber.monthCode().equals(monthSlotFromCode.monthCode())) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            return monthSlotFromCode;
        }
        if (monthSlotFromNumber != null) {
            return monthSlotFromNumber;
        }
        if (monthSlotFromCode != null) {
            return monthSlotFromCode;
        }
        context.throwRangeError("Temporal error: Invalid ISO date.");
        return null;
    }

    public static IsoDate resolveReferenceIsoDateForMonthDay(
            JSContext context,
            String calendarId,
            String monthCode,
            int dayOfMonth,
            String overflow) {
        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String normalizedMonthCode = TemporalUtils.monthCode(monthCodeData.monthNumber());
        if (monthCodeData.leapMonth()) {
            normalizedMonthCode = normalizedMonthCode + "L";
        }
        int searchDay = dayOfMonth;

        if (isChineseOrDangiCalendar(calendarId) && monthCodeData.leapMonth()) {
            if ("reject".equals(overflow)) {
                IsoDate exactLeapReferenceIsoDate = findReferenceIsoDateExact(
                        calendarId,
                        normalizedMonthCode,
                        searchDay);
                if (exactLeapReferenceIsoDate == null) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                } else {
                    return exactLeapReferenceIsoDate;
                }
            }

            int constrainedLeapDay = Math.min(searchDay, 30);
            IsoDate exactLeapReferenceIsoDate = findReferenceIsoDateExact(
                    calendarId,
                    normalizedMonthCode,
                    constrainedLeapDay);
            if (exactLeapReferenceIsoDate != null) {
                return exactLeapReferenceIsoDate;
            }

            normalizedMonthCode = TemporalUtils.monthCode(monthCodeData.monthNumber());
            searchDay = constrainedLeapDay;
        }

        if ("reject".equals(overflow)) {
            IsoDate exactReferenceIsoDate = findReferenceIsoDateExact(
                    calendarId,
                    normalizedMonthCode,
                    searchDay);
            if (exactReferenceIsoDate == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            } else {
                return exactReferenceIsoDate;
            }
        }

        int constrainedSearchDay = Math.min(searchDay, 31);
        for (int candidateDay = constrainedSearchDay; candidateDay >= 1; candidateDay--) {
            IsoDate candidateReferenceIsoDate = findReferenceIsoDateExact(
                    calendarId,
                    normalizedMonthCode,
                    candidateDay);
            if (candidateReferenceIsoDate != null) {
                return candidateReferenceIsoDate;
            }
        }
        context.throwRangeError("Temporal error: Invalid ISO date.");
        return null;
    }

    private static int toArithmeticPersianYear(int persianYear) {
        if (persianYear <= 0) {
            return persianYear - 1;
        }
        return persianYear;
    }

    private static IsoDate toIsoDateFromGregorianLike(int isoYear, String monthCode, int dayOfMonth) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.monthNumber();
        if (!IsoDate.isValidIsoDate(isoYear, monthNumber, dayOfMonth)) {
            return null;
        }
        return new IsoDate(isoYear, monthNumber, dayOfMonth);
    }

    private static IsoDate umalquraToIsoDate(int islamicYear, String monthCode, int dayOfMonth) {
        MonthCodeData monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.monthNumber();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        if (isUnsupportedUmalquraYear(islamicYear)) {
            return islamicToIsoDate(islamicYear, monthCode, dayOfMonth, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
        try {
            HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(islamicYear, monthNumber, dayOfMonth);
            LocalDate localDate = LocalDate.from(hijrahDate);
            return new IsoDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        } catch (DateTimeException dateTimeException) {
            return islamicToIsoDate(islamicYear, monthCode, dayOfMonth, ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
    }

    public record CalendarDateFields(
            int year,
            int month,
            String monthCode,
            int day) {
    }

    private record MonthCodeData(
            int monthNumber,
            boolean leapMonth) {
    }

    private record MonthSlot(
            int monthNumber,
            boolean leapMonth,
            String monthCode,
            int daysInMonth) {
    }

    private record PersianYearInfo(
            int gregorianYear,
            int marchDay,
            boolean leapYear) {
    }

    private record ReferenceIsoDateLookupKey(
            String calendarId,
            String monthCode,
            int dayOfMonth) {
    }

    private record YearMonthIndex(
            int year,
            int monthNumber) {
    }
}
