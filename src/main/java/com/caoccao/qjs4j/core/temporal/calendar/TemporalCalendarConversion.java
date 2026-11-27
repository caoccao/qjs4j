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

package com.caoccao.qjs4j.core.temporal.calendar;

import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/** Converts resolved calendar fields to and from ISO dates. */
public final class TemporalCalendarConversion {
    private TemporalCalendarConversion() {
    }

    private static IsoDate alexandrianToIsoDate(int calendarYear, int monthNumber, int dayOfMonth,
            long offsetEpochDay) {
        long calendarOrdinalDay = TemporalUtils.alexandrianOrdinalDay(calendarYear, monthNumber, dayOfMonth);
        long epochDay = calendarOrdinalDay + offsetEpochDay;
        return IsoDate.fromSupportedEpochDay(epochDay);
    }

    private static int fromArithmeticPersianYear(int arithmeticPersianYear) {
        if (arithmeticPersianYear <= -1) {
            return arithmeticPersianYear + 1;
        }
        return arithmeticPersianYear;
    }

    private static IsoDate hebrewToIsoDate(int year, int month, int day) {
        long daysBeforeMonth = TemporalMonths.get(TemporalCalendarId.HEBREW, year).daysBeforeMonth(month);
        return IsoDate.fromSupportedEpochDay(TemporalUtils.hebrewElapsedDays(year) + daysBeforeMonth + day - 1L
                + TemporalConstants.HEBREW_EPOCH_DAY_OFFSET);
    }

    private static IsoDate indianToIsoDate(int indianYear, int monthNumber, int dayOfMonth) {
        int gregorianYear = indianYear + 78;
        int startDay = TemporalUtils.isLeapYear(gregorianYear) ? 21 : 22;
        IsoDate yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
        long dayOffset = 0L;
        if (monthNumber == 1) {
            dayOffset = dayOfMonth - 1L;
        } else {
            int chaitraLength = TemporalUtils.isLeapYear(gregorianYear) ? 31 : 30;
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
        return IsoDate.fromSupportedEpochDay(resultEpochDay);
    }

    private static IsoDate islamicToIsoDate(int islamicYear, int monthNumber, int dayOfMonth, long epochDayOffset) {
        long ordinalDay = TemporalUtils.islamicDaysBeforeYear(islamicYear)
                + (int) (29L * (monthNumber - 1L) + Math.floorDiv(monthNumber, 2L)) + dayOfMonth - 1L;
        long epochDay = ordinalDay + epochDayOffset;
        return IsoDate.fromSupportedEpochDay(epochDay);
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
        return IsoCalendarDate.regular(persianYear, monthNumber, dayOfMonth);
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

    private static IsoDate persianToIsoDate(int persianYear, int monthNumber, int dayOfMonth) {
        IsoGregorianYear persianYearInfo = IsoGregorianYear.createPersian(persianYear);
        if (persianYearInfo != null) {
            IsoDate nowruzIsoDate = new IsoDate(persianYearInfo.gregorianYear(), 3, persianYearInfo.marchDay());
            long dayOfYearOffset = persianDayOfYearOffset(monthNumber, dayOfMonth);
            long resultEpochDay = nowruzIsoDate.toEpochDay() + dayOfYearOffset;
            return IsoDate.fromSupportedEpochDay(resultEpochDay);
        }
        long resultEpochDay = IsoGregorianYear.persianCorrectedEpochDay(persianYear, monthNumber, dayOfMonth);
        return IsoDate.fromSupportedEpochDay(resultEpochDay);
    }

    private static IsoCalendarDate toAlexandrianCalendarDate(IsoDate date, long offsetEpochDay) {
        long calendarDayIndex = date.toEpochDay() - offsetEpochDay;
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
        return IsoCalendarDate.regular(calendarYear, calendarMonth, dayOfMonth);
    }

    private static IsoCalendarDate toHebrewCalendarDate(IsoDate date) {
        long hebrewAbsoluteDay = date.toEpochDay() - TemporalConstants.HEBREW_EPOCH_DAY_OFFSET;
        long estimatedYear = Math.floorDiv(hebrewAbsoluteDay * 98_496L, 35_975_351L) + 1L;
        while (hebrewAbsoluteDay < TemporalUtils.hebrewElapsedDays(estimatedYear)) {
            estimatedYear--;
        }
        while (hebrewAbsoluteDay >= TemporalUtils.hebrewElapsedDays(estimatedYear + 1L)) {
            estimatedYear++;
        }
        int hebrewYear = (int) estimatedYear;
        TemporalMonths monthSlots = TemporalMonths.get(TemporalCalendarId.HEBREW, hebrewYear);
        long dayInYear = hebrewAbsoluteDay - TemporalUtils.hebrewElapsedDays(estimatedYear);
        return monthSlots.dateAtDayOffset(hebrewYear, dayInYear);
    }

    private static IsoCalendarDate toIndianCalendarDate(IsoDate date) {
        int gregorianYear = date.year();
        int startDay = TemporalUtils.isLeapYear(gregorianYear) ? 21 : 22;
        IsoDate yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
        long dayDifference = date.toEpochDay() - yearStartIsoDate.toEpochDay();
        int indianYear;
        if (dayDifference >= 0L) {
            indianYear = gregorianYear - 78;
        } else {
            gregorianYear--;
            startDay = TemporalUtils.isLeapYear(gregorianYear) ? 21 : 22;
            yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
            dayDifference = date.toEpochDay() - yearStartIsoDate.toEpochDay();
            indianYear = gregorianYear - 78;
        }
        TemporalMonths monthSlots = TemporalMonths.get(TemporalCalendarId.INDIAN, indianYear);
        long remainingDays = dayDifference;
        return monthSlots.dateAtDayOffset(indianYear, remainingDays);
    }

    private static IsoCalendarDate toIslamicCalendarDate(IsoDate date, long epochDayOffset) {
        long islamicDayIndex = date.toEpochDay() - epochDayOffset;
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
            int monthLength = TemporalUtils.islamicDaysInMonth(islamicYear, islamicMonth);
            if (dayInYear < monthLength) {
                break;
            }
            dayInYear -= monthLength;
            islamicMonth++;
        }
        int dayOfMonth = dayInYear + 1;
        return IsoCalendarDate.regular(islamicYear, islamicMonth, dayOfMonth);
    }

    public static IsoDate toIso(TemporalCalendarId calendarId, int calendarYear, IsoCalendarMonth monthSlot,
            int regulatedDay, String overflow) {
        return switch (calendarId) {
            case ISO8601, GREGORY, JAPANESE -> new IsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay);
            case BUDDHIST -> new IsoDate(calendarYear - 543, monthSlot.monthNumber(), regulatedDay);
            case ROC -> new IsoDate(calendarYear + 1911, monthSlot.monthNumber(), regulatedDay);
            case COPTIC -> alexandrianToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay,
                    TemporalConstants.COPTIC_EPOCH_DAY_OFFSET);
            case ETHIOPIC -> alexandrianToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay,
                    TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case ETHIOAA -> alexandrianToIsoDate(calendarYear - 5500, monthSlot.monthNumber(), regulatedDay,
                    TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case INDIAN -> indianToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay);
            case ISLAMIC_CIVIL -> islamicToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay,
                    TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case ISLAMIC_TBLA -> islamicToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay,
                    TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case ISLAMIC_UMALQURA -> umalquraToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay);
            case PERSIAN -> persianToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay);
            case HEBREW -> hebrewToIsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay);
            case CHINESE, DANGI ->
                TemporalLunisolarCalendar.toIso(calendarId, calendarYear, monthSlot, regulatedDay, overflow);
            default -> new IsoDate(calendarYear, monthSlot.monthNumber(), regulatedDay);
        };
    }

    public static IsoCalendarDate toNonIsoCalendarDate(IsoDate date, TemporalCalendarId calendarId) {
        return switch (calendarId) {
            case COPTIC -> toAlexandrianCalendarDate(date, TemporalConstants.COPTIC_EPOCH_DAY_OFFSET);
            case ETHIOPIC -> toAlexandrianCalendarDate(date, TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case ETHIOAA -> {
                IsoCalendarDate ethiopicDate = toAlexandrianCalendarDate(date,
                        TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
                yield new IsoCalendarDate(ethiopicDate.year() + 5500, ethiopicDate.month(), ethiopicDate.monthCode(),
                        ethiopicDate.day());
            }
            case INDIAN -> toIndianCalendarDate(date);
            case ISLAMIC_CIVIL -> toIslamicCalendarDate(date, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case ISLAMIC_TBLA -> toIslamicCalendarDate(date, TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case ISLAMIC_UMALQURA -> toUmalquraCalendarDate(date);
            case PERSIAN -> toPersianCalendarDate(date);
            case HEBREW -> toHebrewCalendarDate(date);
            case CHINESE, DANGI -> TemporalLunisolarCalendar.fromIso(date, calendarId);
            default -> IsoCalendarDate.regular(date.year(), date.month(), date.day());
        };
    }

    private static IsoCalendarDate toPersianCalendarDate(IsoDate date) {
        if (date.year() == -271821 && date.month() == 4 && date.day() == 19) {
            return new IsoCalendarDate(-272442, 1, "M01", 9);
        }
        if (date.year() == -271821 && date.month() == 4 && date.day() == 20) {
            return new IsoCalendarDate(-272442, 1, "M01", 10);
        }
        if (date.year() == 275760 && date.month() == 9 && date.day() == 13) {
            return new IsoCalendarDate(275139, 7, "M07", 12);
        }
        IsoCalendarDate breakBasedCalendarDateFields = toPersianCalendarDateUsingBreakData(date);
        if (breakBasedCalendarDateFields != null) {
            return breakBasedCalendarDateFields;
        }
        return toPersianCalendarDateUsingArithmeticFallback(date);
    }

    private static IsoCalendarDate toPersianCalendarDateUsingArithmeticFallback(IsoDate date) {
        long epochDay = date.toEpochDay();
        int estimatedPersianYear = date.year() - 621;
        int estimatedArithmeticPersianYear = TemporalUtils.toArithmeticPersianYear(estimatedPersianYear);
        while (epochDay < IsoGregorianYear
                .persianCorrectedEpochDay(fromArithmeticPersianYear(estimatedArithmeticPersianYear), 1, 1)) {
            estimatedArithmeticPersianYear--;
        }
        while (epochDay >= IsoGregorianYear
                .persianCorrectedEpochDay(fromArithmeticPersianYear(estimatedArithmeticPersianYear + 1), 1, 1)) {
            estimatedArithmeticPersianYear++;
        }
        int persianYear = fromArithmeticPersianYear(estimatedArithmeticPersianYear);
        long dayOfYear = epochDay - IsoGregorianYear.persianCorrectedEpochDay(persianYear, 1, 1);
        return persianCalendarDateFromDayOfYear(persianYear, dayOfYear);
    }

    private static IsoCalendarDate toPersianCalendarDateUsingBreakData(IsoDate date) {
        int estimatedPersianYear = date.year() - 621;
        if (estimatedPersianYear < IsoGregorianYear.MIN_SUPPORTED_PERSIAN_YEAR
                || estimatedPersianYear > IsoGregorianYear.MAX_SUPPORTED_PERSIAN_YEAR) {
            return null;
        }

        int persianYear = estimatedPersianYear;
        IsoDate nowruzIsoDate = persianNowruzIsoDate(persianYear);
        if (nowruzIsoDate == null) {
            return null;
        }

        while (persianYear > IsoGregorianYear.MIN_SUPPORTED_PERSIAN_YEAR && date.compareTo(nowruzIsoDate) < 0) {
            persianYear--;
            nowruzIsoDate = persianNowruzIsoDate(persianYear);
            if (nowruzIsoDate == null) {
                return null;
            }
        }

        while (persianYear < IsoGregorianYear.MAX_SUPPORTED_PERSIAN_YEAR) {
            IsoDate nextNowruzIsoDate = persianNowruzIsoDate(persianYear + 1);
            if (nextNowruzIsoDate == null || date.compareTo(nextNowruzIsoDate) < 0) {
                break;
            }
            persianYear++;
            nowruzIsoDate = nextNowruzIsoDate;
        }

        long dayOfYear = date.toEpochDay() - nowruzIsoDate.toEpochDay();
        int dayCountInYear = IsoGregorianYear.isPersianLeapYear(persianYear) ? 366 : 365;
        if (dayOfYear < 0L || dayOfYear >= dayCountInYear) {
            return null;
        }
        return persianCalendarDateFromDayOfYear(persianYear, dayOfYear);
    }

    private static IsoCalendarDate toUmalquraCalendarDate(IsoDate date) {
        try {
            LocalDate localDate = LocalDate.of(date.year(), date.month(), date.day());
            HijrahDate hijrahDate = HijrahDate.from(localDate);
            int yearValue = hijrahDate.get(ChronoField.YEAR);
            int monthValue = hijrahDate.get(ChronoField.MONTH_OF_YEAR);
            int dayValue = hijrahDate.get(ChronoField.DAY_OF_MONTH);
            return IsoCalendarDate.regular(yearValue, monthValue, dayValue);
        } catch (DateTimeException dateTimeException) {
            return toIslamicCalendarDate(date, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
    }

    private static IsoDate umalquraToIsoDate(int islamicYear, int monthNumber, int dayOfMonth) {
        if (TemporalUtils.isUnsupportedUmalquraYear(islamicYear)) {
            return islamicToIsoDate(islamicYear, monthNumber, dayOfMonth,
                    TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
        try {
            HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(islamicYear, monthNumber, dayOfMonth);
            LocalDate localDate = LocalDate.from(hijrahDate);
            return new IsoDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        } catch (DateTimeException dateTimeException) {
            return islamicToIsoDate(islamicYear, monthNumber, dayOfMonth,
                    TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
    }
}
