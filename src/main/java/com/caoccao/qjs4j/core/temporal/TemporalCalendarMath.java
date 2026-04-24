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
import java.util.List;

/**
 * Temporal calendar math for non-ISO calendars used by Temporal.PlainDate.
 */
public final class TemporalCalendarMath {
    private TemporalCalendarMath() {
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
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
        }
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getIslamicMonthSlots(int calendarYear, long epochDayOffset) {
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(
                    monthNumber,
                    false,
                    IsoMonth.toMonthCode(monthNumber),
                    islamicDaysInMonth(calendarYear, monthNumber, epochDayOffset)));
        }
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getLunisolarMonthSlots(TemporalCalendarId calendarId, int calendarYear) {
        if (calendarYear < 1900 || calendarYear > calendarId.getLunisolarMaxYear()) {
            List<IsoCalendarMonth> fallbackMonthSlots = new ArrayList<>();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                fallbackMonthSlots.add(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
            }
            return fallbackMonthSlots;
        }
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        int monthNumberInYear = 1;
        int leapMonth = calendarId.getLunisolarLeapMonth(calendarYear);
        for (int regularMonth = 1; regularMonth <= 12; regularMonth++) {
            int regularMonthLength = calendarId.getLunisolarMonthDays(calendarYear, regularMonth);
            String regularMonthCode = IsoMonth.toMonthCode(regularMonth);
            monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, false, regularMonthCode, regularMonthLength));
            if (leapMonth == regularMonth) {
                int leapMonthLength = calendarId.lunisolarLeapMonthDays(calendarYear);
                String leapMonthCode = regularMonthCode + "L";
                monthSlots.add(new IsoCalendarMonth(monthNumberInYear++, true, leapMonthCode, leapMonthLength));
            }
        }
        return monthSlots;
    }

    static List<IsoCalendarMonth> getMonthSlots(TemporalCalendarId calendarId, int calendarYear) {
        if (calendarId == TemporalCalendarId.HEBREW) {
            return getHebrewMonthSlots(calendarYear);
        }
        if (calendarId == TemporalCalendarId.CHINESE || calendarId == TemporalCalendarId.DANGI) {
            return getLunisolarMonthSlots(calendarId, calendarYear);
        }
        if (calendarId == TemporalCalendarId.COPTIC
                || calendarId == TemporalCalendarId.ETHIOPIC
                || calendarId == TemporalCalendarId.ETHIOAA) {
            int underlyingYear = calendarId == TemporalCalendarId.ETHIOAA ? calendarYear - 5500 : calendarYear;
            List<IsoCalendarMonth> monthSlots = new ArrayList<>();
            for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
                monthSlots.add(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
            }
            monthSlots.add(new IsoCalendarMonth(
                    13,
                    false,
                    "M13",
                    TemporalUtils.alexandrianLeapYear(underlyingYear) ? 6 : 5));
            return monthSlots;
        }
        if (calendarId == TemporalCalendarId.INDIAN) {
            return getIndianMonthSlots(calendarYear);
        }
        if (calendarId == TemporalCalendarId.PERSIAN) {
            return getPersianMonthSlots(calendarYear);
        }
        if (calendarId == TemporalCalendarId.ISLAMIC_CIVIL) {
            return getIslamicMonthSlots(calendarYear, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
        if (calendarId == TemporalCalendarId.ISLAMIC_TBLA) {
            return getIslamicMonthSlots(calendarYear, TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
        }
        if (calendarId == TemporalCalendarId.ISLAMIC_UMALQURA) {
            return getUmalquraMonthSlots(calendarYear);
        }
        int isoYear = calendarYear;
        if (calendarId == TemporalCalendarId.BUDDHIST) {
            isoYear = calendarYear - 543;
        } else if (calendarId == TemporalCalendarId.ROC) {
            isoYear = calendarYear + 1911;
        }
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(
                    monthNumber,
                    false,
                    IsoMonth.toMonthCode(monthNumber),
                    IsoDate.daysInMonth(isoYear, monthNumber)));
        }
        return monthSlots;
    }

    private static List<IsoCalendarMonth> getPersianMonthSlots(int calendarYear) {
        List<IsoCalendarMonth> monthSlots = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 6; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 11; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), 30));
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
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, IsoMonth.toMonthCode(monthNumber), dayCount));
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
        long partsElapsed = 204L + 793L * TemporalUtils.floorMod(monthsElapsed, 1080L);
        long hoursElapsed = 5L
                + 12L * monthsElapsed
                + 793L * Math.floorDiv(monthsElapsed, 1080L)
                + Math.floorDiv(partsElapsed, 1080L);
        long conjunctionParts = 1080L * TemporalUtils.floorMod(hoursElapsed, 24L) + TemporalUtils.floorMod(partsElapsed, 1080L);
        long dayNumber = 1L + 29L * monthsElapsed + Math.floorDiv(hoursElapsed, 24L);

        boolean shouldPostpone = conjunctionParts >= 19_440L
                || (!isHebrewLeapYear(hebrewYear) && TemporalUtils.floorMod(dayNumber, 7L) == 2L && conjunctionParts >= 9_924L)
                || (isHebrewLeapYear(hebrewYear - 1L) && TemporalUtils.floorMod(dayNumber, 7L) == 1L && conjunctionParts >= 16_789L);
        if (shouldPostpone) {
            dayNumber++;
        }

        long weekDay = TemporalUtils.floorMod(dayNumber, 7L);
        if (weekDay == 0L || weekDay == 3L || weekDay == 5L) {
            dayNumber++;
        }
        return dayNumber;
    }

    static IsoDate hebrewToIsoDate(int hebrewYear, String monthCode, int dayOfMonth) {
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

    public static boolean inLeapYear(IsoDate isoDate, TemporalCalendarId calendarId) {
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        return calendarId.isCalendarLeapYear(calendarDateFields.year());
    }

    static IsoDate indianToIsoDate(int indianYear, String monthCode, int dayOfMonth) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
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

    public static boolean isHebrewLeapYear(long hebrewYear) {
        return TemporalUtils.floorMod(7L * hebrewYear + 1L, 19L) < 7L;
    }

    public static long isHebrewYearLength(long hebrewYear) {
        return hebrewElapsedDays(hebrewYear + 1L) - hebrewElapsedDays(hebrewYear);
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

    static IsoDate islamicToIsoDate(int islamicYear, String monthCode, int dayOfMonth, long epochDayOffset) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
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

    static IsoDate lunisolarToIsoDate(
            int calendarYear,
            String monthCode,
            int dayOfMonth,
            TemporalCalendarId calendarId,
            String overflow) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
        if (monthCodeData == null) {
            return null;
        }
        if (calendarYear < 1900 || calendarYear > calendarId.getLunisolarMaxYear()) {
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
                return toIsoDateFromGregorianLike(1900, IsoMonth.toMonthCode(1), dayOfMonth);
            }
            int fallbackIsoYear = calendarYear + 1;
            String fallbackMonthCode = IsoMonth.toMonthCode(monthCodeData.month());
            IsoDate fallbackIsoDate = toIsoDateFromGregorianLike(fallbackIsoYear, fallbackMonthCode, dayOfMonth);
            if (fallbackIsoDate != null) {
                return fallbackIsoDate;
            }
            if (TemporalOverflow.REJECT.matches(overflow)) {
                return null;
            }
            int constrainedDay = Math.min(dayOfMonth, IsoDate.daysInMonth(fallbackIsoYear, monthCodeData.month()));
            return toIsoDateFromGregorianLike(fallbackIsoYear, fallbackMonthCode, constrainedDay);
        }
        int leapMonth = calendarId.getLunisolarLeapMonth(calendarYear);
        if (monthCodeData.leapMonth() && leapMonth != monthCodeData.month()) {
            return null;
        }
        long dayOffset = 0L;
        for (int yearValue = 1900; yearValue < calendarYear; yearValue++) {
            dayOffset += calendarId.getLunisolarYearDays(yearValue);
        }
        for (int monthNumber = 1; monthNumber < monthCodeData.month(); monthNumber++) {
            dayOffset += calendarId.getLunisolarMonthDays(calendarYear, monthNumber);
            if (leapMonth == monthNumber) {
                dayOffset += calendarId.lunisolarLeapMonthDays(calendarYear);
            }
        }
        if (monthCodeData.leapMonth()) {
            dayOffset += calendarId.getLunisolarMonthDays(calendarYear, monthCodeData.month());
        }
        dayOffset += dayOfMonth - 1L;
        LocalDate lunarBaseDate = LocalDate.of(1900, 1, 31);
        LocalDate targetDate = lunarBaseDate.plusDays(dayOffset);
        return new IsoDate(targetDate.getYear(), targetDate.getMonthValue(), targetDate.getDayOfMonth());
    }

    public static int monthsInYear(IsoDate isoDate, TemporalCalendarId calendarId) {
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        return getMonthSlots(calendarId, calendarDateFields.year()).size();
    }

    static TemporalYearMonthIndex nextMonthIndex(TemporalCalendarId calendarId, TemporalYearMonthIndex currentIndex) {
        List<IsoCalendarMonth> monthSlots = getMonthSlots(calendarId, currentIndex.year());
        if (currentIndex.monthNumber() < monthSlots.size()) {
            return new TemporalYearMonthIndex(currentIndex.year(), currentIndex.monthNumber() + 1);
        }
        return new TemporalYearMonthIndex(currentIndex.year() + 1, 1);
    }

    private static long persianDayOfYearOffset(int monthNumber, int dayOfMonth) {
        if (monthNumber <= 6) {
            return (monthNumber - 1L) * 31L + dayOfMonth - 1L;
        }
        return 186L + (monthNumber - 7L) * 30L + dayOfMonth - 1L;
    }

    static IsoDate persianToIsoDate(int persianYear, String monthCode, int dayOfMonth) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
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

    static TemporalYearMonthIndex previousMonthIndex(TemporalCalendarId calendarId, TemporalYearMonthIndex currentIndex) {
        if (currentIndex.monthNumber() > 1) {
            return new TemporalYearMonthIndex(currentIndex.year(), currentIndex.monthNumber() - 1);
        }
        int previousYear = currentIndex.year() - 1;
        List<IsoCalendarMonth> previousYearSlots = getMonthSlots(calendarId, previousYear);
        return new TemporalYearMonthIndex(previousYear, previousYearSlots.size());
    }

    static int regulateDay(JSContext context, int dayOfMonth, int daysInMonth, String overflow) {
        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return 0;
        }
        if (TemporalOverflow.REJECT.matches(overflow) && dayOfMonth > daysInMonth) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return 0;
        }
        return Math.min(dayOfMonth, daysInMonth);
    }

    static String resolveFallbackMonthCodeForMissingLeapMonth(TemporalCalendarId calendarId, String leapMonthCode) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(leapMonthCode);
        if (monthCodeData == null || !monthCodeData.leapMonth()) {
            return null;
        }
        if (calendarId == TemporalCalendarId.HEBREW && "M05L".equals(leapMonthCode)) {
            return "M06";
        }
        if (calendarId == TemporalCalendarId.CHINESE || calendarId == TemporalCalendarId.DANGI) {
            return IsoMonth.toMonthCode(monthCodeData.month());
        }
        return null;
    }

    static IsoCalendarMonth resolveMonthSlotForInput(
            JSContext context,
            TemporalCalendarId calendarId,
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
                if (!TemporalOverflow.REJECT.matches(overflow) && monthCodeFromProperty == null) {
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
            IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCodeFromProperty);
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
                if (!TemporalOverflow.REJECT.matches(overflow) && monthCodeData.leapMonth()) {
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
            TemporalCalendarId calendarId,
            String monthCode,
            int dayOfMonth,
            String overflow) {
        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
        if (monthCodeData == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String normalizedMonthCode = IsoMonth.toMonthCode(monthCodeData.month());
        if (monthCodeData.leapMonth()) {
            normalizedMonthCode = normalizedMonthCode + "L";
        }
        int searchDay = dayOfMonth;

        if (calendarId.isChineseOrDangiCalendar() && monthCodeData.leapMonth()) {
            if (TemporalOverflow.REJECT.matches(overflow)) {
                IsoDate exactLeapReferenceIsoDate = calendarId.findReferenceIsoDateExact(
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
            IsoDate exactLeapReferenceIsoDate = calendarId.findReferenceIsoDateExact(
                    normalizedMonthCode,
                    constrainedLeapDay);
            if (exactLeapReferenceIsoDate != null) {
                return exactLeapReferenceIsoDate;
            }

            normalizedMonthCode = IsoMonth.toMonthCode(monthCodeData.month());
            searchDay = constrainedLeapDay;
        }

        if (TemporalOverflow.REJECT.matches(overflow)) {
            IsoDate exactReferenceIsoDate = calendarId.findReferenceIsoDateExact(
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
            IsoDate candidateReferenceIsoDate = calendarId.findReferenceIsoDateExact(
                    normalizedMonthCode,
                    candidateDay);
            if (candidateReferenceIsoDate != null) {
                return candidateReferenceIsoDate;
            }
        }
        context.throwRangeError("Temporal error: Invalid ISO date.");
        return null;
    }

    static IsoDate toIsoDateFromGregorianLike(int isoYear, String monthCode, int dayOfMonth) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
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

    static IsoDate umalquraToIsoDate(int islamicYear, String monthCode, int dayOfMonth) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
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
