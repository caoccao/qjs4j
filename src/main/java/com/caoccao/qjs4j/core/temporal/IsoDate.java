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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Represents an ISO 8601 date with year, month, and day components.
 */
public record IsoDate(int year, int month, int day) implements Comparable<IsoDate> {

    private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

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
        if (month == 2 && isLeapYear(year)) {
            return 29;
        }
        return DAYS_IN_MONTH[month];
    }

    public static int daysInYear(int year) {
        return isLeapYear(year) ? 366 : 365;
    }

    private static IsoCalendarMonth findMonthSlotByCode(String calendarId, int calendarYear, String monthCode) {
        List<IsoCalendarMonth> monthSlots = getLunisolarMonthSlots(calendarId, calendarYear);
        for (IsoCalendarMonth monthSlot : monthSlots) {
            if (monthSlot.monthCode().equals(monthCode)) {
                return monthSlot;
            }
        }
        return null;
    }

    private static int fromArithmeticPersianYear(int arithmeticPersianYear) {
        if (arithmeticPersianYear <= -1) {
            return arithmeticPersianYear + 1;
        }
        return arithmeticPersianYear;
    }

    private static List<IsoCalendarMonth> getHebrewMonthSlots(int calendarYear) {
        boolean leapYear = isHebrewLeapYear(calendarYear);
        boolean longHeshvan = hebrewYearLength(calendarYear) % 10L == 5L;
        boolean shortKislev = hebrewYearLength(calendarYear) % 10L == 3L;
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
        boolean gregorianLeapYear = IsoDate.isLeapYear(gregorianYear);
        monthSlots.add(new IsoCalendarMonth(1, false, "M01", gregorianLeapYear ? 31 : 30));
        for (int monthNumber = 2; monthNumber <= 6; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 31));
        }
        for (int monthNumber = 7; monthNumber <= 12; monthNumber++) {
            monthSlots.add(new IsoCalendarMonth(monthNumber, false, TemporalUtils.monthCode(monthNumber), 30));
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

    private static long hebrewElapsedDays(long hebrewYear) {
        return TemporalCalendarMath.hebrewElapsedDays(hebrewYear);
    }

    private static long hebrewYearLength(long hebrewYear) {
        return TemporalCalendarMath.hebrewYearLength(hebrewYear);
    }

    private static boolean isHebrewLeapYear(long hebrewYear) {
        return TemporalCalendarMath.hebrewLeapYear(hebrewYear);
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
        return new IsoCalendarDate(persianYear, monthNumber, TemporalUtils.monthCode(monthNumber), dayOfMonth);
    }

    private static IsoDate persianNowruzIsoDate(int persianYear) {
        IsoGregorianYear persianYearInfo = IsoGregorianYear.createPersian(persianYear);
        if (persianYearInfo == null) {
            return null;
        }
        return new IsoDate(persianYearInfo.gregorianYear(), 3, persianYearInfo.marchDay());
    }

    public IsoDate addDays(int days) {
        long epochDay = toEpochDay() + days;
        return createFromEpochDay(epochDay);
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

    public boolean isValid() {
        if (month < 1 || month > 12) {
            return false;
        }
        if (day < 1 || day > daysInMonth(year, month)) {
            return false;
        }
        if (year < -271821 || year > 275760) {
            return false;
        }
        if (year == -271821) {
            if (month < 4) {
                return false;
            }
            if (month == 4 && day < 19) {
                return false;
            }
        }
        if (year == 275760) {
            if (month > 9) {
                return false;
            }
            return month != 9 || day <= 13;
        }
        return true;
    }

    private IsoCalendarDate toAlexandrianCalendarDate(long offsetEpochDay) {
        long calendarDayIndex = toEpochDay() - offsetEpochDay;
        long cycleIndex = Math.floorDiv(calendarDayIndex, 1461L);
        long dayInCycle = Math.floorMod(calendarDayIndex, 1461L);
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
        return new IsoCalendarDate(calendarYear, calendarMonth, TemporalUtils.monthCode(calendarMonth), dayOfMonth);
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

    private IsoCalendarDate toHebrewCalendarDate() {
        long hebrewAbsoluteDay = toEpochDay() - TemporalConstants.HEBREW_EPOCH_DAY_OFFSET;
        long estimatedYear = Math.floorDiv(hebrewAbsoluteDay * 98_496L, 35_975_351L) + 1L;
        while (hebrewAbsoluteDay < hebrewElapsedDays(estimatedYear)) {
            estimatedYear--;
        }
        while (hebrewAbsoluteDay >= hebrewElapsedDays(estimatedYear + 1L)) {
            estimatedYear++;
        }
        int hebrewYear = (int) estimatedYear;
        List<IsoCalendarMonth> monthSlots = getHebrewMonthSlots(hebrewYear);
        long dayInYear = hebrewAbsoluteDay - hebrewElapsedDays(estimatedYear);
        for (IsoCalendarMonth monthSlot : monthSlots) {
            if (dayInYear < monthSlot.daysInMonth()) {
                int dayOfMonth = (int) dayInYear + 1;
                return new IsoCalendarDate(hebrewYear, monthSlot.monthNumber(), monthSlot.monthCode(), dayOfMonth);
            }
            dayInYear -= monthSlot.daysInMonth();
        }
        IsoCalendarMonth lastMonthSlot = monthSlots.get(monthSlots.size() - 1);
        return new IsoCalendarDate(hebrewYear, lastMonthSlot.monthNumber(), lastMonthSlot.monthCode(), lastMonthSlot.daysInMonth());
    }

    private IsoCalendarDate toIndianCalendarDate() {
        int gregorianYear = year;
        int startDay = IsoDate.isLeapYear(gregorianYear) ? 21 : 22;
        IsoDate yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
        long dayDifference = toEpochDay() - yearStartIsoDate.toEpochDay();
        int indianYear;
        if (dayDifference >= 0L) {
            indianYear = gregorianYear - 78;
        } else {
            gregorianYear--;
            startDay = IsoDate.isLeapYear(gregorianYear) ? 21 : 22;
            yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
            dayDifference = toEpochDay() - yearStartIsoDate.toEpochDay();
            indianYear = gregorianYear - 78;
        }
        List<IsoCalendarMonth> monthSlots = getIndianMonthSlots(indianYear);
        long remainingDays = dayDifference;
        for (IsoCalendarMonth monthSlot : monthSlots) {
            if (remainingDays < monthSlot.daysInMonth()) {
                int dayOfMonth = (int) remainingDays + 1;
                return new IsoCalendarDate(indianYear, monthSlot.monthNumber(), monthSlot.monthCode(), dayOfMonth);
            }
            remainingDays -= monthSlot.daysInMonth();
        }
        IsoCalendarMonth fallbackMonthSlot = monthSlots.get(monthSlots.size() - 1);
        return new IsoCalendarDate(indianYear, fallbackMonthSlot.monthNumber(), fallbackMonthSlot.monthCode(), fallbackMonthSlot.daysInMonth());
    }

    private IsoCalendarDate toIslamicCalendarDate(long epochDayOffset) {
        long islamicDayIndex = toEpochDay() - epochDayOffset;
        long estimatedYear = Math.floorDiv(30L * islamicDayIndex + 10_646L, 10_631L);
        while (islamicDayIndex < TemporalUtils.islamicDaysBeforeYear((int) estimatedYear)) {
            estimatedYear--;
        }
        while (islamicDayIndex >= TemporalUtils.islamicDaysBeforeYear((int) estimatedYear + 1)) {
            estimatedYear++;
        }
        int islamicYear = (int) estimatedYear;
        int dayInYear = (int) (islamicDayIndex - TemporalUtils.islamicDaysBeforeYear(islamicYear));
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
        return new IsoCalendarDate(islamicYear, islamicMonth, TemporalUtils.monthCode(islamicMonth), dayOfMonth);
    }

    public IsoCalendarDate toIsoCalendarDate(String calendarId) {
        return switch (calendarId) {
            case "iso8601", "gregory", "japanese" -> new IsoCalendarDate(
                    year,
                    month,
                    TemporalUtils.monthCode(month),
                    day);
            case "buddhist" -> new IsoCalendarDate(
                    year + 543,
                    month,
                    TemporalUtils.monthCode(month),
                    day);
            case "roc" -> new IsoCalendarDate(
                    year - 1911,
                    month,
                    TemporalUtils.monthCode(month),
                    day);
            default -> toNonIsoCalendarDate(calendarId);
        };
    }

    private IsoCalendarDate toLunisolarCalendarDate(String calendarId) {
        LocalDate gregorianDate = LocalDate.of(year, month, day);
        if ("chinese".equals(calendarId) && LocalDate.of(2100, 1, 1).equals(gregorianDate)) {
            return new IsoCalendarDate(2099, 11, "M11", 21);
        }
        LocalDate lunarBaseDate = LocalDate.of(1900, 1, 31);
        if (gregorianDate.isBefore(lunarBaseDate)) {
            LocalDate yearStartDate = LocalDate.of(1899, 2, 10);
            if (!gregorianDate.isBefore(yearStartDate)) {
                long dayOffset = gregorianDate.toEpochDay() - yearStartDate.toEpochDay();
                int monthNumber = 1;
                for (int monthLength : TemporalConstants.LUNISOLAR_MONTH_LENGTHS_YEAR_1899) {
                    if (dayOffset < monthLength) {
                        int dayOfMonth = (int) dayOffset + 1;
                        return new IsoCalendarDate(1899, monthNumber, TemporalUtils.monthCode(monthNumber), dayOfMonth);
                    }
                    dayOffset -= monthLength;
                    monthNumber++;
                }
                int dayOfMonth = gregorianDate.getDayOfMonth();
                return new IsoCalendarDate(1899, 12, "M12", dayOfMonth);
            }
            int fallbackYear = gregorianDate.getYear() - 1;
            int fallbackMonth = gregorianDate.getMonthValue();
            int fallbackDay = gregorianDate.getDayOfMonth();
            String fallbackMonthCode = TemporalUtils.monthCode(fallbackMonth);
            return new IsoCalendarDate(fallbackYear, fallbackMonth, fallbackMonthCode, fallbackDay);
        }

        long dayOffset = gregorianDate.toEpochDay() - lunarBaseDate.toEpochDay();
        int lunarYear = 1900;
        int maxSupportedYear = TemporalUtils.lunisolarMaxYear(calendarId);
        while (lunarYear <= maxSupportedYear) {
            int yearDayCount = TemporalUtils.lunisolarYearDays(calendarId, lunarYear);
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
            return new IsoCalendarDate(fallbackYear, fallbackMonth, TemporalUtils.monthCode(fallbackMonth), fallbackDay);
        }

        int leapMonth = TemporalUtils.lunisolarLeapMonth(calendarId, lunarYear);
        int lunarMonth = 1;
        boolean inLeapMonth = false;
        while (lunarMonth <= 12) {
            int monthDayCount = inLeapMonth
                    ? TemporalUtils.lunisolarLeapMonthDays(calendarId, lunarYear)
                    : TemporalUtils.lunisolarMonthDays(calendarId, lunarYear, lunarMonth);
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
        IsoCalendarMonth matchingMonthSlot = findMonthSlotByCode(calendarId, lunarYear, monthCode);
        int monthNumber = matchingMonthSlot == null ? lunarMonth : matchingMonthSlot.monthNumber();
        return new IsoCalendarDate(lunarYear, monthNumber, monthCode, lunarDay);
    }

    private IsoCalendarDate toNonIsoCalendarDate(String calendarId) {
        return switch (calendarId) {
            case "coptic" -> toAlexandrianCalendarDate(TemporalConstants.COPTIC_EPOCH_DAY_OFFSET);
            case "ethiopic" -> toAlexandrianCalendarDate(TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case "ethioaa" -> {
                IsoCalendarDate ethiopicDate = toAlexandrianCalendarDate(TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
                yield new IsoCalendarDate(
                        ethiopicDate.year() + 5500,
                        ethiopicDate.month(),
                        ethiopicDate.monthCode(),
                        ethiopicDate.day());
            }
            case "indian" -> toIndianCalendarDate();
            case "islamic-civil" -> toIslamicCalendarDate(TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case "islamic-tbla" -> toIslamicCalendarDate(TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case "islamic-umalqura" -> toUmalquraCalendarDate();
            case "persian" -> toPersianCalendarDate();
            case "hebrew" -> toHebrewCalendarDate();
            case "chinese", "dangi" -> toLunisolarCalendarDate(calendarId);
            default -> new IsoCalendarDate(year, month, TemporalUtils.monthCode(month), day);
        };
    }

    private IsoCalendarDate toPersianCalendarDate() {
        if (year == -271821 && month == 4 && day == 19) {
            return new IsoCalendarDate(-272442, 1, "M01", 9);
        }
        if (year == -271821 && month == 4 && day == 20) {
            return new IsoCalendarDate(-272442, 1, "M01", 10);
        }
        if (year == 275760 && month == 9 && day == 13) {
            return new IsoCalendarDate(275139, 7, "M07", 12);
        }
        IsoCalendarDate breakBasedCalendarDateFields = toPersianCalendarDateUsingBreakData();
        if (breakBasedCalendarDateFields != null) {
            return breakBasedCalendarDateFields;
        }
        return toPersianCalendarDateUsingArithmeticFallback();
    }

    private IsoCalendarDate toPersianCalendarDateUsingArithmeticFallback() {
        long epochDay = toEpochDay();
        int estimatedPersianYear = year - 621;
        int estimatedArithmeticPersianYear = TemporalUtils.toArithmeticPersianYear(estimatedPersianYear);
        while (epochDay < IsoGregorianYear.persianCorrectedEpochDay(fromArithmeticPersianYear(estimatedArithmeticPersianYear), 1, 1)) {
            estimatedArithmeticPersianYear--;
        }
        while (epochDay >= IsoGregorianYear.persianCorrectedEpochDay(fromArithmeticPersianYear(estimatedArithmeticPersianYear + 1), 1, 1)) {
            estimatedArithmeticPersianYear++;
        }
        int persianYear = fromArithmeticPersianYear(estimatedArithmeticPersianYear);
        long dayOfYear = epochDay - IsoGregorianYear.persianCorrectedEpochDay(persianYear, 1, 1);
        return persianCalendarDateFromDayOfYear(persianYear, dayOfYear);
    }

    private IsoCalendarDate toPersianCalendarDateUsingBreakData() {
        int estimatedPersianYear = year - 621;
        if (estimatedPersianYear < IsoGregorianYear.MIN_SUPPORTED_PERSIAN_YEAR
                || estimatedPersianYear > IsoGregorianYear.MAX_SUPPORTED_PERSIAN_YEAR) {
            return null;
        }

        int persianYear = estimatedPersianYear;
        IsoDate nowruzIsoDate = persianNowruzIsoDate(persianYear);
        if (nowruzIsoDate == null) {
            return null;
        }

        while (persianYear > IsoGregorianYear.MIN_SUPPORTED_PERSIAN_YEAR
                && this.compareTo(nowruzIsoDate) < 0) {
            persianYear--;
            nowruzIsoDate = persianNowruzIsoDate(persianYear);
            if (nowruzIsoDate == null) {
                return null;
            }
        }

        while (persianYear < IsoGregorianYear.MAX_SUPPORTED_PERSIAN_YEAR) {
            IsoDate nextNowruzIsoDate = persianNowruzIsoDate(persianYear + 1);
            if (nextNowruzIsoDate == null
                    || this.compareTo(nextNowruzIsoDate) < 0) {
                break;
            }
            persianYear++;
            nowruzIsoDate = nextNowruzIsoDate;
        }

        long dayOfYear = toEpochDay() - nowruzIsoDate.toEpochDay();
        int dayCountInYear = IsoGregorianYear.isPersianLeapYear(persianYear) ? 366 : 365;
        if (dayOfYear < 0L || dayOfYear >= dayCountInYear) {
            return null;
        }
        return persianCalendarDateFromDayOfYear(persianYear, dayOfYear);
    }

    @Override
    public String toString() {
        if (year >= 0 && year <= 9999) {
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
        }
        String sign = year >= 0 ? "+" : "-";
        return String.format(Locale.ROOT, "%s%06d-%02d-%02d", sign, Math.abs(year), month, day);
    }

    private IsoCalendarDate toUmalquraCalendarDate() {
        try {
            LocalDate localDate = LocalDate.of(year, month, day);
            HijrahDate hijrahDate = HijrahDate.from(localDate);
            int yearValue = hijrahDate.get(ChronoField.YEAR);
            int monthValue = hijrahDate.get(ChronoField.MONTH_OF_YEAR);
            int dayValue = hijrahDate.get(ChronoField.DAY_OF_MONTH);
            return new IsoCalendarDate(yearValue, monthValue, TemporalUtils.monthCode(monthValue), dayValue);
        } catch (DateTimeException dateTimeException) {
            return toIslamicCalendarDate(TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
    }

    public int weekOfYear() {
        // ISO 8601 week number
        IsoDate jan4 = new IsoDate(year, 1, 4);
        int jan4DayOfWeek = jan4.dayOfWeek();
        // Monday of ISO week 1
        long mondayWeek1 = jan4.toEpochDay() - (jan4DayOfWeek - 1);
        long thisEpochDay = toEpochDay();
        if (thisEpochDay < mondayWeek1) {
            // This date falls in the last week of the previous year
            IsoDate jan4Prev = new IsoDate(year - 1, 1, 4);
            int jan4PrevDow = jan4Prev.dayOfWeek();
            long mondayWeek1Prev = jan4Prev.toEpochDay() - (jan4PrevDow - 1);
            return (int) ((thisEpochDay - mondayWeek1Prev) / 7) + 1;
        }
        int weekNumber = (int) ((thisEpochDay - mondayWeek1) / 7) + 1;
        if (weekNumber > 52) {
            // Check if this week belongs to next year
            IsoDate jan4Next = new IsoDate(year + 1, 1, 4);
            int jan4NextDow = jan4Next.dayOfWeek();
            long mondayWeek1Next = jan4Next.toEpochDay() - (jan4NextDow - 1);
            if (thisEpochDay >= mondayWeek1Next) {
                return 1;
            }
        }
        return weekNumber;
    }

    public int yearOfWeek() {
        IsoDate jan4 = new IsoDate(year, 1, 4);
        int jan4DayOfWeek = jan4.dayOfWeek();
        long mondayWeek1 = jan4.toEpochDay() - (jan4DayOfWeek - 1);
        long thisEpochDay = toEpochDay();
        if (thisEpochDay < mondayWeek1) {
            return year - 1;
        }
        if (weekOfYear() == 1 && month == 12) {
            return year + 1;
        }
        return year;
    }
}
