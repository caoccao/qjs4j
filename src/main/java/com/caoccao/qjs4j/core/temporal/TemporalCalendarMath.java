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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporal calendar math for non-ISO calendars used by Temporal.PlainDate.
 */
public final class TemporalCalendarMath {
    private static final int DEFAULT_REFERENCE_ISO_YEAR = 1972;
    private static final int MAX_REFERENCE_ISO_DATE_CACHE_SIZE = 4_096;
    private static final int MAX_REFERENCE_ISO_YEAR = 2050;
    private static final int MIN_REFERENCE_ISO_YEAR = 1900;
    private static final ConcurrentHashMap<TemporalReferenceIsoDateLookupKey, IsoDate> REFERENCE_ISO_DATE_HIT_CACHE =
            new ConcurrentHashMap<>();
    private static final Set<TemporalReferenceIsoDateLookupKey> REFERENCE_ISO_DATE_MISS_CACHE =
            ConcurrentHashMap.newKeySet();
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
        IsoCalendarDate baseCalendarDate = baseIsoDate.toIsoCalendarDate(calendarId);
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

        IsoCalendarMonth baseMonthSlot = findMonthSlotByCode(calendarId, baseCalendarDate.year(), baseCalendarDate.monthCode());
        if (baseMonthSlot == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        IsoCalendarMonth yearAdjustedMonthSlot = findMonthSlotByCode(calendarId, targetYear, baseMonthSlot.monthCode());
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

        TemporalYearMonthIndex yearMonthIndex = new TemporalYearMonthIndex(targetYear, yearAdjustedMonthSlot.monthNumber());
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
        IsoCalendarMonth targetMonthSlot = findMonthSlotByNumber(calendarId, yearMonthIndex.year(), yearMonthIndex.monthNumber());
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
        if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return IsoDate.createFromEpochDay(resultEpochDay);
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
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        long calendarOrdinalDay = alexandrianOrdinalDay(calendarYear, monthCodeData.month(), dayOfMonth);
        long epochDay = calendarOrdinalDay + offsetEpochDay;
        if (epochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || epochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.createFromEpochDay(epochDay);
    }

    public static IsoDate calendarDateToIsoDate(
            JSContext context,
            String calendarId,
            int calendarYear,
            Integer monthFromProperty,
            String monthCodeFromProperty,
            int dayFromProperty,
            String overflow) {
        IsoCalendarMonth monthSlot = resolveMonthSlotForInput(
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
                    alexandrianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.COPTIC_EPOCH_DAY_OFFSET);
            case "ethiopic" ->
                    alexandrianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case "ethioaa" ->
                    alexandrianToIsoDate(calendarYear - 5500, monthSlot.monthCode(), regulatedDay, TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case "indian" -> indianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "islamic-civil" ->
                    islamicToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case "islamic-tbla" ->
                    islamicToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case "islamic-umalqura" -> umalquraToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "persian" -> persianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "hebrew" -> hebrewToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case "chinese", "dangi" ->
                    lunisolarToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, calendarId, overflow);
            default -> toIsoDateFromGregorianLike(calendarYear, monthSlot.monthCode(), regulatedDay);
        };
        if (resultIsoDate == null || !resultIsoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return resultIsoDate;
    }

    public static boolean calendarYearHasMonthCode(String calendarId, int calendarYear, String monthCode) {
        if (monthCode == null) {
            return false;
        }
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, calendarYear);
        for (IsoCalendarMonth monthSlot : monthSlots) {
            if (monthSlot.monthCode().equals(monthCode)) {
                return true;
            }
        }
        return false;
    }

    public static String constrainMonthCode(String calendarId, int calendarYear, String monthCode) {
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
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
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, calendarDateFields.year());
        int dayOfYear = 0;
        for (IsoCalendarMonth monthSlot : monthSlots) {
            if (monthSlot.monthCode().equals(calendarDateFields.monthCode())) {
                dayOfYear += calendarDateFields.day();
                return dayOfYear;
            }
            dayOfYear += monthSlot.daysInMonth();
        }
        return calendarDateFields.day();
    }

    public static int daysInMonth(IsoDate isoDate, String calendarId) {
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        IsoCalendarMonth monthSlot = findMonthSlotByCode(calendarId, calendarDateFields.year(), calendarDateFields.monthCode());
        if (monthSlot == null) {
            return 0;
        }
        return monthSlot.daysInMonth();
    }

    public static int daysInYear(IsoDate isoDate, String calendarId) {
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, calendarDateFields.year());
        int dayCount = 0;
        for (IsoCalendarMonth monthSlot : monthSlots) {
            dayCount += monthSlot.daysInMonth();
        }
        return dayCount;
    }

    public static IsoDate findBoundaryIsoDateForYearMonth(String calendarId, int targetYear, String targetMonthCode) {
        IsoDate minimumBoundaryIsoDate = findClosestBoundaryIsoDate(
                calendarId,
                targetYear,
                targetMonthCode,
                TemporalConstants.MIN_SUPPORTED_EPOCH_DAY);
        if (minimumBoundaryIsoDate != null) {
            return minimumBoundaryIsoDate;
        } else {
            return findClosestBoundaryIsoDate(
                    calendarId,
                    targetYear,
                    targetMonthCode,
                    TemporalConstants.MAX_SUPPORTED_EPOCH_DAY);
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
                IsoDate candidateIsoDate = IsoDate.createFromEpochDay(candidateEpochDay);
                IsoCalendarDate candidateCalendarDateFields =
                        candidateIsoDate.toIsoCalendarDate(calendarId);
                if (candidateCalendarDateFields.year() == targetYear
                        && targetMonthCode.equals(candidateCalendarDateFields.monthCode())) {
                    return candidateIsoDate;
                }
            }
        }
        return null;
    }

    private static IsoCalendarMonth findMonthSlotByCode(String calendarId, int calendarYear, String monthCode) {
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, calendarYear);
        for (IsoCalendarMonth monthSlot : monthSlots) {
            if (monthSlot.monthCode().equals(monthCode)) {
                return monthSlot;
            }
        }
        return null;
    }

    private static IsoCalendarMonth findMonthSlotByNumber(String calendarId, int calendarYear, int monthNumber) {
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, calendarYear);
        for (IsoCalendarMonth monthSlot : monthSlots) {
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
        TemporalReferenceIsoDateLookupKey referenceIsoDateLookupKey = new TemporalReferenceIsoDateLookupKey(
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
                IsoCalendarDate calendarDateFields = candidateIsoDate.toIsoCalendarDate(calendarId);
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

    private static List<IsoCalendarMonth> getHebrewMonthSlots(int calendarYear) {
        boolean leapYear = isHebrewLeapYear(calendarYear);
        boolean longHeshvan = isHebrewYearLength(calendarYear) % 10L == 5L;
        boolean shortKislev = isHebrewYearLength(calendarYear) % 10L == 3L;
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        int monthNumberInYear = 1;
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M01", 30));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M02", longHeshvan ? 30 : 29));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M03", shortKislev ? 29 : 30));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M04", 29));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M05", 30));
        if (leapYear) {
            monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, true, "M05L", 30));
            monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M06", 29));
        } else {
            monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M06", 29));
        }
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M07", 30));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M08", 29));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M09", 30));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M10", 29));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, "M11", 30));
        monthSlots.add(new IsoCalendarMonth(monthNumberInYear, false, "M12", 29));
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getIndianMonthSlots(int calendarYear) {
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        int gregorianYear = calendarYear + 78;
        boolean gregorianLeapYear = isLeapYear(gregorianYear);
        monthSlots.add(new IsoCalendarMonth(1, false, "M01", gregorianLeapYear ? 31 : 30));
        for (int monthNumber = 2; monthNumber <= 6; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
        }
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getIslamicMonthSlots(int calendarYear, long epochDayOffset) {
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(
                    monthNumber,
                    false,
                    TemporalUtils.monthCode(monthNumber),
                    islamicDaysInMonth(calendarYear, monthNumber, epochDayOffset)));
        }
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getLunisolarMonthSlots(String calendarId, int calendarYear) {
        if (calendarYear < 1900 || calendarYear > TemporalUtils.lunisolarMaxYear(calendarId)) {
            List<IsoCalendarMonth> fallbackMonthSlots = new ArrayList<>();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                fallbackMonthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
            }
            return fallbackMonthSlots;
        }
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        int monthNumberInYear = 1;
        int leapMonth = TemporalUtils.lunisolarLeapMonth(calendarId, calendarYear);
        for (int regularMonth = 1; regularMonth <= 12; regularMonth++) {
            int regularMonthLength = TemporalUtils.lunisolarMonthDays(calendarId, calendarYear, regularMonth);
            String regularMonthCode = TemporalUtils.monthCode(regularMonth);
            monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, regularMonthCode, regularMonthLength));
            if (leapMonth == regularMonth) {
                int leapMonthLength = TemporalUtils.lunisolarLeapMonthDays(calendarId, calendarYear);
                String leapMonthCode = regularMonthCode + "L";
                monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, true, leapMonthCode, leapMonthLength));
            }
        }
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getMonthSlots(String calendarId, int calendarYear) {
        if ("hebrew".equals(calendarId)) {
            return getHebrewMonthSlots(calendarYear);
        }
        if ("chinese".equals(calendarId) || "dangi".equals(calendarId)) {
            return getLunisolarMonthSlots(calendarId, calendarYear);
        }
        if ("coptic".equals(calendarId) || "ethiopic".equals(calendarId) || "ethioaa".equals(calendarId)) {
            int underlyingYear = "ethioaa".equals(calendarId) ? calendarYear - 5500 : calendarYear;
            List<IsoCalendarMonth> monthSlots = new ArrayList<>();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
            }
            monthSlots.add(new IsoCalendarMonth(13, false, "M13", alexandrianLeapYear(underlyingYear) ? 6 : 5));
            return monthSlots;
        }
        if ("indian".equals(calendarId)) {
            return getIndianMonthSlots(calendarYear);
        }
        if ("persian".equals(calendarId)) {
            return getPersianMonthSlots(calendarYear);
        }
        if ("islamic-civil".equals(calendarId)) {
            return getIslamicMonthSlots(calendarYear, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
        if ("islamic-tbla".equals(calendarId)) {
            return getIslamicMonthSlots(calendarYear, TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
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
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(
                    monthNumber,
                    false,
                    TemporalUtils.monthCode(monthNumber),
                    IsoDate.daysInMonth(isoYear, monthNumber)));
        }
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getPersianMonthSlots(int calendarYear) {
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 6; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 11; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
        }
        monthSlots.add(new IsoCalendarMonth(12, false, "M12", IsoGregorianYear.isPersianLeapYear(calendarYear) ? 30 : 29));
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getUmalquraMonthSlots(int calendarYear) {
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            int dayCount;
            try {
                HijrahDate monthStart = HijrahChronology.INSTANCE.date(calendarYear, monthNumber, 1);
                dayCount = monthStart.lengthOfMonth();
            } catch (DateTimeException dateTimeException) {
                dayCount = islamicDaysInMonth(calendarYear, monthNumber, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            }
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), dayCount));
        }
        return monthSlots;
    }

    private static long hebrewAbsoluteDay(int hebrewYear, String monthCode, int dayOfMonth) {
        List<IsoCalendarMonth> monthSlots = getHebrewMonthSlots(hebrewYear);
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

    public static long hebrewElapsedDays(long hebrewYear) {
        long monthsElapsed = Math.floorDiv(235L * hebrewYear - 234L, 19L);
        long partsElapsed = 204L + 793L * floorMod(monthsElapsed, 1080L);
        long hoursElapsed = 5L
                + 12L * monthsElapsed
                + 793L * Math.floorDiv(monthsElapsed, 1080L)
                + Math.floorDiv(partsElapsed, 1080L);
        long conjunctionParts = 1080L * floorMod(hoursElapsed, 24L) + floorMod(partsElapsed, 1080L);
        long dayNumber = 1L + 29L * monthsElapsed + Math.floorDiv(hoursElapsed, 24L);

        boolean shouldPostpone = conjunctionParts >= 19_440L
                || (!isHebrewLeapYear(hebrewYear) && floorMod(dayNumber, 7L) == 2L && conjunctionParts >= 9_924L)
                || (isHebrewLeapYear(hebrewYear - 1L) && floorMod(dayNumber, 7L) == 1L && conjunctionParts >= 16_789L);
        if (shouldPostpone) {
            dayNumber++;
        }

        long weekDay = floorMod(dayNumber, 7L);
        if (weekDay == 0L || weekDay == 3L || weekDay == 5L) {
            dayNumber++;
        }
        return dayNumber;
    }

    private static IsoDate hebrewToIsoDate(int hebrewYear, String monthCode, int dayOfMonth) {
        long hebrewAbsoluteDay = hebrewAbsoluteDay(hebrewYear, monthCode, dayOfMonth);
        if (hebrewAbsoluteDay == Long.MIN_VALUE) {
            return null;
        }
        long epochDay = hebrewAbsoluteDay + TemporalConstants.HEBREW_EPOCH_DAY_OFFSET;
        if (epochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || epochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.createFromEpochDay(epochDay);
    }

    public static boolean inLeapYear(IsoDate isoDate, String calendarId) {
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        return isCalendarLeapYear(calendarId, calendarDateFields.year());
    }

    private static IsoDate indianToIsoDate(int indianYear, String monthCode, int dayOfMonth) {
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.month();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        int gregorianYear = indianYear + 78;
        int startDay = isLeapYear(gregorianYear) ? 21 : 22;
        IsoDate yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
        long dayOffset = 0L;
        if (monthNumber == 1) {
            dayOffset = dayOfMonth - 1L;
        } else {
            int chaitraLength = isLeapYear(gregorianYear) ? 31 : 30;
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
        if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.createFromEpochDay(resultEpochDay);
    }

    private static boolean isCalendarLeapYear(String calendarId, int calendarYear) {
        return switch (calendarId) {
            case "iso8601", "gregory", "japanese" -> isLeapYear(calendarYear);
            case "buddhist" -> isLeapYear(calendarYear - 543);
            case "roc" -> isLeapYear(calendarYear + 1911);
            case "coptic", "ethiopic" -> alexandrianLeapYear(calendarYear);
            case "ethioaa" -> alexandrianLeapYear(calendarYear - 5500);
            case "hebrew" -> isHebrewLeapYear(calendarYear);
            case "indian" -> isLeapYear(calendarYear + 78);
            case "islamic-civil" ->
                    islamicDaysInMonth(calendarYear, 12, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET) == 30;
            case "islamic-tbla" ->
                    islamicDaysInMonth(calendarYear, 12, TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET) == 30;
            case "islamic-umalqura" -> isKnownUmalquraLeapYear(calendarYear)
                    || (calendarYear < 1390 || calendarYear > 1469)
                    && getUmalquraMonthSlots(calendarYear).get(11).daysInMonth() == 30;
            case "persian" -> IsoGregorianYear.isPersianLeapYear(calendarYear);
            case "chinese", "dangi" -> calendarYear >= 1900
                    && calendarYear <= TemporalUtils.lunisolarMaxYear(calendarId)
                    && TemporalUtils.lunisolarLeapMonth(calendarId, calendarYear) != 0;
            default -> false;
        };
    }

    private static boolean isChineseOrDangiCalendar(String calendarId) {
        return "chinese".equals(calendarId) || "dangi".equals(calendarId);
    }

    public static boolean isHebrewLeapYear(long hebrewYear) {
        return floorMod(7L * hebrewYear + 1L, 19L) < 7L;
    }

    public static long isHebrewYearLength(long hebrewYear) {
        return hebrewElapsedDays(hebrewYear + 1L) - hebrewElapsedDays(hebrewYear);
    }

    private static boolean isKnownUmalquraLeapYear(int islamicYear) {
        for (int leapYear : TemporalConstants.UMALQURA_KNOWN_LEAP_YEARS_1390_TO_1469) {
            if (leapYear == islamicYear) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLeapYear(int year) {
        if (year % 4 != 0) {
            return false;
        }
        if (year % 100 != 0) {
            return true;
        }
        return year % 400 == 0;
    }

    private static boolean isUnsupportedUmalquraYear(int islamicYear) {
        try {
            HijrahChronology.INSTANCE.date(islamicYear, 1, 1);
            return false;
        } catch (DateTimeException dateTimeException) {
            return true;
        }
    }

    private static int islamicDaysInMonth(int islamicYear, int islamicMonth, long epochDayOffset) {
        if (islamicMonth < 1 || islamicMonth > 12) {
            return 0;
        }
        if (islamicMonth == 12) {
            long yearLength = TemporalUtils.islamicDaysBeforeYear(islamicYear + 1)
                    - TemporalUtils.islamicDaysBeforeYear(islamicYear);
            return (int) (yearLength - 325L);
        }
        return islamicMonth % 2 == 1 ? 30 : 29;
    }

    private static int islamicMonthDaysBefore(int islamicMonth) {
        return (int) (29L * (islamicMonth - 1L) + Math.floorDiv(islamicMonth, 2L));
    }

    private static IsoDate islamicToIsoDate(int islamicYear, String monthCode, int dayOfMonth, long epochDayOffset) {
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.month();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        long ordinalDay = TemporalUtils.islamicDaysBeforeYear(islamicYear)
                + islamicMonthDaysBefore(monthNumber)
                + dayOfMonth
                - 1L;
        long epochDay = ordinalDay + epochDayOffset;
        if (epochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || epochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.createFromEpochDay(epochDay);
    }

    private static IsoDate lunisolarToIsoDate(
            int calendarYear,
            String monthCode,
            int dayOfMonth,
            String calendarId,
            String overflow) {
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null) {
            return null;
        }
        if (calendarYear < 1900 || calendarYear > TemporalUtils.lunisolarMaxYear(calendarId)) {
            if (calendarYear == 1899) {
                if (monthCodeData.leapMonth()) {
                    return null;
                }
                int monthNumber = monthCodeData.month();
                if (monthNumber < 1 || monthNumber > 12) {
                    return null;
                }
                int maxDay = TemporalConstants.LUNISOLAR_MONTH_LENGTHS_YEAR_1899[monthNumber - 1];
                if (dayOfMonth < 1 || dayOfMonth > maxDay) {
                    return null;
                }
                LocalDate yearStartDate = LocalDate.of(1899, 2, 10);
                int dayOffset = dayOfMonth - 1;
                for (int monthIndex = 0; monthIndex < monthNumber - 1; monthIndex++) {
                    dayOffset += TemporalConstants.LUNISOLAR_MONTH_LENGTHS_YEAR_1899[monthIndex];
                }
                LocalDate targetDate = yearStartDate.plusDays(dayOffset);
                return new IsoDate(targetDate.getYear(), targetDate.getMonthValue(), targetDate.getDayOfMonth());
            }
            if (calendarYear == 1899 && !monthCodeData.leapMonth() && monthCodeData.month() == 12) {
                return toIsoDateFromGregorianLike(1900, TemporalUtils.monthCode(1), dayOfMonth);
            }
            int fallbackIsoYear = calendarYear + 1;
            String fallbackMonthCode = TemporalUtils.monthCode(monthCodeData.month());
            IsoDate fallbackIsoDate = toIsoDateFromGregorianLike(fallbackIsoYear, fallbackMonthCode, dayOfMonth);
            if (fallbackIsoDate != null) {
                return fallbackIsoDate;
            }
            if ("reject".equals(overflow)) {
                return null;
            }
            int constrainedDay = Math.min(dayOfMonth, IsoDate.daysInMonth(fallbackIsoYear, monthCodeData.month()));
            return toIsoDateFromGregorianLike(fallbackIsoYear, fallbackMonthCode, constrainedDay);
        }
        int leapMonth = TemporalUtils.lunisolarLeapMonth(calendarId, calendarYear);
        if (monthCodeData.leapMonth() && leapMonth != monthCodeData.month()) {
            return null;
        }
        long dayOffset = 0L;
        for (int yearValue = 1900; yearValue < calendarYear; yearValue++) {
            dayOffset += TemporalUtils.lunisolarYearDays(calendarId, yearValue);
        }
        for (int monthNumber = 1; monthNumber < monthCodeData.month(); monthNumber++) {
            dayOffset += TemporalUtils.lunisolarMonthDays(calendarId, calendarYear, monthNumber);
            if (leapMonth == monthNumber) {
                dayOffset += TemporalUtils.lunisolarLeapMonthDays(calendarId, calendarYear);
            }
        }
        if (monthCodeData.leapMonth()) {
            dayOffset += TemporalUtils.lunisolarMonthDays(calendarId, calendarYear, monthCodeData.month());
        }
        dayOffset += dayOfMonth - 1L;
        LocalDate lunarBaseDate = LocalDate.of(1900, 1, 31);
        LocalDate targetDate = lunarBaseDate.plusDays(dayOffset);
        return new IsoDate(targetDate.getYear(), targetDate.getMonthValue(), targetDate.getDayOfMonth());
    }

    public static int monthsInYear(IsoDate isoDate, String calendarId) {
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        return getMonthSlots(calendarId, calendarDateFields.year()).size();
    }

    private static TemporalYearMonthIndex nextMonthIndex(String calendarId, TemporalYearMonthIndex currentIndex) {
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, currentIndex.year());
        if (currentIndex.monthNumber() < monthSlots.size()) {
            return new TemporalYearMonthIndex(currentIndex.year(), currentIndex.monthNumber() + 1);
        }
        return new TemporalYearMonthIndex(currentIndex.year() + 1, 1);
    }

    public static TemporalParsedMonthCode parseMonthCode(String monthCode) {
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
        return new TemporalParsedMonthCode(monthNumber, leapMonth);
    }

    private static IsoCalendarDate persianCalendarDateFromDayOfYear(int persianYear, long dayOfYear) {
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
        return new IsoCalendarDate(
                persianYear,
                monthNumber,
                TemporalUtils.monthCode(monthNumber),
                dayOfMonth);
    }

    private static long persianDayOfYearOffset(int monthNumber, int dayOfMonth) {
        if (monthNumber <= 6) {
            return (monthNumber - 1L) * 31L + dayOfMonth - 1L;
        }
        return 186L + (monthNumber - 7L) * 30L + dayOfMonth - 1L;
    }

    private static IsoDate persianNowruzIsoDate(int persianYear) {
        IsoGregorianYear persianYearInfo = IsoGregorianYear.createPersian(persianYear);
        if (persianYearInfo == null) {
            return null;
        }
        return new IsoDate(persianYearInfo.gregorianYear(), 3, persianYearInfo.marchDay());
    }

    private static IsoDate persianToIsoDate(int persianYear, String monthCode, int dayOfMonth) {
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.month();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        IsoGregorianYear persianYearInfo = IsoGregorianYear.createPersian(persianYear);
        if (persianYearInfo != null) {
            IsoDate nowruzIsoDate = new IsoDate(persianYearInfo.gregorianYear(), 3, persianYearInfo.marchDay());
            long dayOfYearOffset = persianDayOfYearOffset(monthNumber, dayOfMonth);
            long resultEpochDay = nowruzIsoDate.toEpochDay() + dayOfYearOffset;
            if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
                return null;
            }
            return IsoDate.createFromEpochDay(resultEpochDay);
        }
        long resultEpochDay = IsoGregorianYear.persianCorrectedEpochDay(persianYear, monthNumber, dayOfMonth);
        if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return IsoDate.createFromEpochDay(resultEpochDay);
    }

    private static TemporalYearMonthIndex previousMonthIndex(String calendarId, TemporalYearMonthIndex currentIndex) {
        if (currentIndex.monthNumber() > 1) {
            return new TemporalYearMonthIndex(currentIndex.year(), currentIndex.monthNumber() - 1);
        }
        int previousYear = currentIndex.year() - 1;
        List<IsoCalendarMonth> previousYearSlots = getMonthSlots(calendarId, previousYear);
        return new TemporalYearMonthIndex(previousYear, previousYearSlots.size());
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
        TemporalParsedMonthCode monthCodeData = parseMonthCode(leapMonthCode);
        if (monthCodeData == null || !monthCodeData.leapMonth()) {
            return null;
        }
        if ("hebrew".equals(calendarId) && "M05L".equals(leapMonthCode)) {
            return "M06";
        }
        if ("chinese".equals(calendarId) || "dangi".equals(calendarId)) {
            return TemporalUtils.monthCode(monthCodeData.month());
        }
        return null;
    }

    private static IsoCalendarMonth resolveMonthSlotForInput(
            JSContext context,
            String calendarId,
            int calendarYear,
            Integer monthFromProperty,
            String monthCodeFromProperty,
            String overflow) {
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, calendarYear);
        IsoCalendarMonth monthSlotFromNumber = null;
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

        IsoCalendarMonth monthSlotFromCode = null;
        if (monthCodeFromProperty != null) {
            TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCodeFromProperty);
            if (monthCodeData == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            for (IsoCalendarMonth monthSlot : monthSlots) {
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
                    for (IsoCalendarMonth monthSlot : monthSlots) {
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
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String normalizedMonthCode = TemporalUtils.monthCode(monthCodeData.month());
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

            normalizedMonthCode = TemporalUtils.monthCode(monthCodeData.month());
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

    private static IsoDate toIsoDateFromGregorianLike(int isoYear, String monthCode, int dayOfMonth) {
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.month();
        IsoDate isoDate = new IsoDate(isoYear, monthNumber, dayOfMonth);
        if (!isoDate.isValid()) {
            return null;
        }
        return new IsoDate(isoYear, monthNumber, dayOfMonth);
    }

    private static IsoDate umalquraToIsoDate(int islamicYear, String monthCode, int dayOfMonth) {
        TemporalParsedMonthCode monthCodeData = parseMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        int monthNumber = monthCodeData.month();
        if (monthNumber < 1 || monthNumber > 12) {
            return null;
        }
        if (isUnsupportedUmalquraYear(islamicYear)) {
            return islamicToIsoDate(islamicYear, monthCode, dayOfMonth, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
        try {
            HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(islamicYear, monthNumber, dayOfMonth);
            LocalDate localDate = LocalDate.from(hijrahDate);
            return new IsoDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        } catch (DateTimeException dateTimeException) {
            return islamicToIsoDate(islamicYear, monthCode, dayOfMonth, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
    }
}
