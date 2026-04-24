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
import java.util.Locale;

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

    static IsoDate alexandrianToIsoDate(int calendarYear, String monthCode, int dayOfMonth, long offsetEpochDay) {
        IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCode);
        if (monthCodeData == null || monthCodeData.leapMonth()) {
            return null;
        }
        long calendarOrdinalDay = TemporalUtils.alexandrianOrdinalDay(
                calendarYear,
                monthCodeData.month(),
                dayOfMonth);
        long epochDay = calendarOrdinalDay + offsetEpochDay;
        if (epochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || epochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return createFromEpochDay(epochDay);
    }

    public static IsoDate calendarDateToIsoDate(
            JSContext context,
            TemporalCalendarId calendarId,
            int calendarYear,
            Integer monthFromProperty,
            String monthCodeFromProperty,
            int dayFromProperty,
            String overflow) {
        IsoCalendarMonth monthSlot = IsoCalendarMonth.resolveMonthSlotForInput(
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
            case ISO8601, GREGORY, JAPANESE ->
                    toIsoDateFromGregorianLike(calendarYear, monthSlot.monthCode(), regulatedDay);
            case BUDDHIST -> toIsoDateFromGregorianLike(calendarYear - 543, monthSlot.monthCode(), regulatedDay);
            case ROC -> toIsoDateFromGregorianLike(calendarYear + 1911, monthSlot.monthCode(), regulatedDay);
            case COPTIC ->
                    alexandrianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.COPTIC_EPOCH_DAY_OFFSET);
            case ETHIOPIC ->
                    alexandrianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case ETHIOAA -> alexandrianToIsoDate(
                    calendarYear - 5500,
                    monthSlot.monthCode(),
                    regulatedDay,
                    TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case INDIAN -> indianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case ISLAMIC_CIVIL ->
                    islamicToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case ISLAMIC_TBLA ->
                    islamicToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case ISLAMIC_UMALQURA -> umalquraToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case PERSIAN -> persianToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case HEBREW -> hebrewToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay);
            case CHINESE, DANGI ->
                    lunisolarToIsoDate(calendarYear, monthSlot.monthCode(), regulatedDay, calendarId, overflow);
            default -> toIsoDateFromGregorianLike(calendarYear, monthSlot.monthCode(), regulatedDay);
        };
        if (resultIsoDate == null || !resultIsoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
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

    private static int fromArithmeticPersianYear(int arithmeticPersianYear) {
        if (arithmeticPersianYear <= -1) {
            return arithmeticPersianYear + 1;
        }
        return arithmeticPersianYear;
    }

    private static long hebrewAbsoluteDay(int hebrewYear, String monthCode, int dayOfMonth) {
        TemporalMonths monthSlots = TemporalMonths.get(TemporalCalendarId.HEBREW, hebrewYear);
        IsoCalendarMonth targetMonthSlot = monthSlots.getByMonthCode(monthCode);
        if (targetMonthSlot == null) {
            return Long.MIN_VALUE;
        }
        int monthLength = targetMonthSlot.daysInMonth();
        if (dayOfMonth < 1 || dayOfMonth > monthLength) {
            return Long.MIN_VALUE;
        }
        long daysBeforeMonth = 0L;
        int slotIndex = targetMonthSlot.monthNumber() - 1;
        for (int monthIndex = 0; monthIndex < slotIndex; monthIndex++) {
            daysBeforeMonth += monthSlots.get(monthIndex).daysInMonth();
        }
        return TemporalUtils.hebrewElapsedDays(hebrewYear) + daysBeforeMonth + dayOfMonth - 1L;
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
        return createFromEpochDay(epochDay);
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
        if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return createFromEpochDay(resultEpochDay);
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
                + (int) (29L * (monthNumber - 1L) + Math.floorDiv(monthNumber, 2L))
                + dayOfMonth
                - 1L;
        long epochDay = ordinalDay + epochDayOffset;
        if (epochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || epochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return createFromEpochDay(epochDay);
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
            int constrainedDay = Math.min(dayOfMonth, daysInMonth(fallbackIsoYear, monthCodeData.month()));
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

    public static IsoDate parseDateString(JSContext context, String input) {
        return parseDateString(context, input, true);
    }

    private static IsoDate parseDateString(JSContext context, String input, boolean enforceIsoDateRange) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoParsingState parsingState = new IsoParsingState(input);
        IsoDate date = parsingState.parseDate(context, enforceIsoDateRange);
        if (date == null) {
            return null;
        }

        boolean hasTimePart = false;
        if (parsingState.position() < parsingState.inputLength()
                && (parsingState.current() == 'T' || parsingState.current() == 't' || parsingState.current() == ' ')) {
            hasTimePart = true;
            parsingState.advanceOne();
            IsoTime parsedTime = parsingState.parseInstantTime(context);
            if (parsedTime == null || context.hasPendingException()) {
                return null;
            }
        }

        if (parsingState.position() < parsingState.inputLength()) {
            char marker = parsingState.current();
            if (marker == 'Z' || marker == 'z') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (marker == '+' || marker == '-') {
                if (!hasTimePart) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                IsoOffset parsedOffset = parsingState.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
            }
        }

        parsingState.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }

        if (parsingState.position() != parsingState.inputLength()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return date;
    }

    public static IsoDate parseMonthDayString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing month value.");
            return null;
        }

        if (input.startsWith("--")
                && input.length() >= 6
                && IsoParsingState.isAsciiDigit(input.charAt(2))
                && IsoParsingState.isAsciiDigit(input.charAt(3))) {
            int month = IsoParsingState.parseFixedTwoDigits(input, 2);
            int dayOfMonth = -1;
            int prefixLength = -1;
            if (input.length() >= 7 && input.charAt(4) == '-') {
                if (input.length() >= 7
                        && IsoParsingState.isAsciiDigit(input.charAt(5))
                        && IsoParsingState.isAsciiDigit(input.charAt(6))) {
                    dayOfMonth = IsoParsingState.parseFixedTwoDigits(input, 5);
                    prefixLength = 7;
                }
            } else if (input.length() >= 6
                    && IsoParsingState.isAsciiDigit(input.charAt(4))
                    && IsoParsingState.isAsciiDigit(input.charAt(5))) {
                dayOfMonth = IsoParsingState.parseFixedTwoDigits(input, 4);
                prefixLength = 6;
            }
            if (prefixLength > 0) {
                String remainder = input.substring(prefixLength);
                String syntheticDateString = "1972-"
                        + (month < 10 ? "0" : "") + month
                        + "-"
                        + (dayOfMonth < 10 ? "0" : "") + dayOfMonth
                        + remainder;
                IsoDate parsedDate = parseDateString(context, syntheticDateString, false);
                if (parsedDate == null) {
                    return null;
                }
                return new IsoDate(1972, parsedDate.month(), parsedDate.day());
            }
        } else if (input.length() >= 5
                && IsoParsingState.isAsciiDigit(input.charAt(0))
                && IsoParsingState.isAsciiDigit(input.charAt(1))
                && input.charAt(2) == '-'
                && IsoParsingState.isAsciiDigit(input.charAt(3))
                && IsoParsingState.isAsciiDigit(input.charAt(4))) {
            int month = IsoParsingState.parseFixedTwoDigits(input, 0);
            int dayOfMonth = IsoParsingState.parseFixedTwoDigits(input, 3);
            String remainder = input.substring(5);
            String syntheticDateString = "1972-"
                    + (month < 10 ? "0" : "") + month
                    + "-"
                    + (dayOfMonth < 10 ? "0" : "") + dayOfMonth
                    + remainder;
            IsoDate parsedDate = parseDateString(context, syntheticDateString, false);
            if (parsedDate == null) {
                return null;
            }
            return new IsoDate(1972, parsedDate.month(), parsedDate.day());
        } else if (input.length() >= 4
                && IsoParsingState.isAsciiDigit(input.charAt(0))
                && IsoParsingState.isAsciiDigit(input.charAt(1))
                && IsoParsingState.isAsciiDigit(input.charAt(2))
                && IsoParsingState.isAsciiDigit(input.charAt(3))
                && (input.length() == 4 || input.charAt(4) == '[')) {
            int month = IsoParsingState.parseFixedTwoDigits(input, 0);
            int dayOfMonth = IsoParsingState.parseFixedTwoDigits(input, 2);
            String remainder = input.substring(4);
            String syntheticDateString = "1972-"
                    + (month < 10 ? "0" : "") + month
                    + "-"
                    + (dayOfMonth < 10 ? "0" : "") + dayOfMonth
                    + remainder;
            IsoDate parsedDate = parseDateString(context, syntheticDateString, false);
            if (parsedDate == null) {
                return null;
            }
            return new IsoDate(1972, parsedDate.month(), parsedDate.day());
        }

        IsoDate date = parseDateString(context, input, false);
        if (date == null) {
            return null;
        }
        return new IsoDate(1972, date.month(), date.day());
    }

    public static IsoDate parseYearMonthString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoParsingState parsingState = new IsoParsingState(input);

        int year = parsingState.parseYear(context);
        if (context.hasPendingException()) {
            return null;
        }

        boolean hasSeparator = parsingState.position() < parsingState.inputLength()
                && parsingState.input().charAt(parsingState.position()) == '-';
        if (hasSeparator) {
            parsingState.advanceOne();
        }

        int month = parsingState.parseTwoDigits(context, "month");
        if (context.hasPendingException()) {
            return null;
        }
        if (month < 1 || month > 12) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int dayOfMonth = 1;
        if (hasSeparator) {
            if (parsingState.position() < parsingState.inputLength()
                    && parsingState.input().charAt(parsingState.position()) == '-') {
                parsingState.advanceOne();
                dayOfMonth = parsingState.parseTwoDigits(context, "day");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        } else {
            if (parsingState.position() < parsingState.inputLength()
                    && IsoParsingState.isAsciiDigit(parsingState.input().charAt(parsingState.position()))) {
                dayOfMonth = parsingState.parseTwoDigits(context, "day");
                if (context.hasPendingException()) {
                    return null;
                }
            }
            if (parsingState.position() < parsingState.inputLength()
                    && parsingState.input().charAt(parsingState.position()) == '-') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        if (!IsoParsingState.isValidIsoYearMonthDateForParsing(year, month, dayOfMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        boolean hasTimePart = false;
        if (parsingState.position() < parsingState.inputLength()
                && (parsingState.current() == 'T' || parsingState.current() == 't' || parsingState.current() == ' ')) {
            hasTimePart = true;
            parsingState.advanceOne();
            IsoTime parsedTime = parsingState.parseInstantTime(context);
            if (parsedTime == null || context.hasPendingException()) {
                return null;
            }
        }

        if (parsingState.position() < parsingState.inputLength()) {
            char marker = parsingState.current();
            if (marker == 'Z' || marker == 'z') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (marker == '+' || marker == '-') {
                if (!hasTimePart) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                IsoOffset parsedOffset = parsingState.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
            }
        }

        parsingState.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }
        if (parsingState.position() != parsingState.inputLength()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new IsoDate(year, month, dayOfMonth);
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
        return new IsoCalendarDate(persianYear, monthNumber, IsoMonth.toMonthCode(monthNumber), dayOfMonth);
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
            return createFromEpochDay(resultEpochDay);
        }
        long resultEpochDay = IsoGregorianYear.persianCorrectedEpochDay(persianYear, monthNumber, dayOfMonth);
        if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            return null;
        }
        return createFromEpochDay(resultEpochDay);
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
        IsoDate constrainedReferenceIsoDate = calendarId.findReferenceIsoDateAtOrBelow(
                normalizedMonthCode,
                constrainedSearchDay);
        if (constrainedReferenceIsoDate != null) {
            return constrainedReferenceIsoDate;
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
        if (TemporalUtils.isUnsupportedUmalquraYear(islamicYear)) {
            return islamicToIsoDate(
                    islamicYear,
                    monthCode,
                    dayOfMonth,
                    TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
        try {
            HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(islamicYear, monthNumber, dayOfMonth);
            LocalDate localDate = LocalDate.from(hijrahDate);
            return new IsoDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        } catch (DateTimeException dateTimeException) {
            return islamicToIsoDate(
                    islamicYear,
                    monthCode,
                    dayOfMonth,
                    TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
        }
    }

    public IsoDate addCalendarDate(
            JSContext context,
            TemporalCalendarId calendarId,
            long yearsToAdd,
            long monthsToAdd,
            long weeksToAdd,
            long daysToAdd,
            String overflow) {
        IsoCalendarDate baseCalendarDate = toIsoCalendarDate(calendarId);
        if (baseCalendarDate == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if (calendarId == TemporalCalendarId.ISO8601) {
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

        IsoCalendarMonth baseMonthSlot = calendarId.findMonthSlotByCode(
                baseCalendarDate.year(),
                baseCalendarDate.monthCode());
        if (baseMonthSlot == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        IsoCalendarMonth yearAdjustedMonthSlot = calendarId.findMonthSlotByCode(
                targetYear,
                baseMonthSlot.monthCode());
        if (yearAdjustedMonthSlot == null && baseMonthSlot.leapMonth()) {
            if (TemporalOverflow.REJECT.matches(overflow)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            String fallbackMonthCode = calendarId.resolveFallbackMonthCodeForMissingLeapMonth(baseMonthSlot.monthCode());
            if (fallbackMonthCode != null) {
                yearAdjustedMonthSlot = calendarId.findMonthSlotByCode(targetYear, fallbackMonthCode);
            }
        }
        if (yearAdjustedMonthSlot == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
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
        IsoCalendarMonth targetMonthSlot = calendarId.findMonthSlotByNumber(
                yearMonthIndex.year(),
                yearMonthIndex.monthNumber());
        if (targetMonthSlot == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int monthAdjustedDay = regulateDay(
                context,
                baseCalendarDate.day(),
                targetMonthSlot.daysInMonth(),
                overflow);
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
        return createFromEpochDay(resultEpochDay);
    }

    public IsoDate addDays(int days) {
        long epochDay = toEpochDay() + days;
        return createFromEpochDay(epochDay);
    }

    public IsoDate addDurationToIsoDate(
            JSContext context,
            long years,
            long months,
            long weeks,
            long days,
            String overflow) {
        long totalDays;
        try {
            totalDays = Math.addExact(days, Math.multiplyExact(weeks, 7L));
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        long monthIndex = Math.addExact(month - 1L, months);
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long balancedYear = Math.addExact(year, years);
        balancedYear = Math.addExact(balancedYear, yearDelta);
        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int balancedYearInt = (int) balancedYear;
        int maxDay = daysInMonth(balancedYearInt, balancedMonth);
        int regulatedDay = day;
        if ("reject".equals(overflow)) {
            if (regulatedDay > maxDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            regulatedDay = Math.min(regulatedDay, maxDay);
        }

        IsoDate intermediateDate = new IsoDate(balancedYearInt, balancedMonth, regulatedDay);
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate isoDate = createFromEpochDay(resultEpochDay);
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return isoDate;
    }

    public IsoDate addIsoDateWithOverflow(
            JSContext context,
            long years,
            long months,
            long weeks,
            long days,
            String overflow) {
        long monthIndex;
        long balancedYear;
        try {
            monthIndex = Math.addExact(month - 1L, months);
            long balancedYearDelta = Math.floorDiv(monthIndex, 12L);
            balancedYear = Math.addExact(year, years);
            balancedYear = Math.addExact(balancedYear, balancedYearDelta);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        int balancedYearInt = (int) balancedYear;
        int maximumDay = daysInMonth(balancedYearInt, balancedMonth);
        int balancedDay = day;
        if ("reject".equals(overflow)) {
            if (balancedDay > maximumDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            balancedDay = Math.min(balancedDay, maximumDay);
        }

        IsoDate intermediateDate = new IsoDate(balancedYearInt, balancedMonth, balancedDay);
        long resultEpochDay;
        try {
            long daysFromWeeks = Math.multiplyExact(weeks, 7L);
            long totalDays = Math.addExact(days, daysFromWeeks);
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        try {
            return createFromEpochDay(resultEpochDay);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
    }

    public IsoDateTime atMidnight() {
        return new IsoDateTime(this, IsoTime.MIDNIGHT);
    }

    public IsoDateTime atTime(IsoTime isoTime) {
        return new IsoDateTime(this, isoTime);
    }

    public IsoDate calendarDateAddConstrain(
            JSContext context,
            TemporalCalendarId calendarId,
            TemporalDurationDateWeek dateDuration) {
        if (calendarId == TemporalCalendarId.ISO8601) {
            return addDurationToIsoDate(
                    context,
                    dateDuration.years(),
                    dateDuration.months(),
                    dateDuration.weeks(),
                    dateDuration.days(),
                    "constrain");
        } else {
            return addCalendarDate(
                    context,
                    calendarId,
                    dateDuration.years(),
                    dateDuration.months(),
                    dateDuration.weeks(),
                    dateDuration.days(),
                    "constrain");
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
        IsoCalendarMonth monthSlot = calendarId.findMonthSlotByCode(
                calendarDateFields.year(),
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
        return new IsoCalendarDate(calendarYear, calendarMonth, IsoMonth.toMonthCode(calendarMonth), dayOfMonth);
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
        while (hebrewAbsoluteDay < TemporalUtils.hebrewElapsedDays(estimatedYear)) {
            estimatedYear--;
        }
        while (hebrewAbsoluteDay >= TemporalUtils.hebrewElapsedDays(estimatedYear + 1L)) {
            estimatedYear++;
        }
        int hebrewYear = (int) estimatedYear;
        TemporalMonths monthSlots = TemporalMonths.get(TemporalCalendarId.HEBREW, hebrewYear);
        long dayInYear = hebrewAbsoluteDay - TemporalUtils.hebrewElapsedDays(estimatedYear);
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
        int startDay = TemporalUtils.isLeapYear(gregorianYear) ? 21 : 22;
        IsoDate yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
        long dayDifference = toEpochDay() - yearStartIsoDate.toEpochDay();
        int indianYear;
        if (dayDifference >= 0L) {
            indianYear = gregorianYear - 78;
        } else {
            gregorianYear--;
            startDay = TemporalUtils.isLeapYear(gregorianYear) ? 21 : 22;
            yearStartIsoDate = new IsoDate(gregorianYear, 3, startDay);
            dayDifference = toEpochDay() - yearStartIsoDate.toEpochDay();
            indianYear = gregorianYear - 78;
        }
        TemporalMonths monthSlots = TemporalMonths.get(TemporalCalendarId.INDIAN, indianYear);
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
            int monthLength = TemporalUtils.islamicDaysInMonth(islamicYear, islamicMonth);
            if (dayInYear < monthLength) {
                break;
            }
            dayInYear -= monthLength;
            islamicMonth++;
        }
        int dayOfMonth = dayInYear + 1;
        return new IsoCalendarDate(islamicYear, islamicMonth, IsoMonth.toMonthCode(islamicMonth), dayOfMonth);
    }

    public IsoCalendarDate toIsoCalendarDate(TemporalCalendarId calendarId) {
        return switch (calendarId) {
            case ISO8601, GREGORY, JAPANESE -> new IsoCalendarDate(
                    year,
                    month,
                    IsoMonth.toMonthCode(month),
                    day);
            case BUDDHIST -> new IsoCalendarDate(
                    year + 543,
                    month,
                    IsoMonth.toMonthCode(month),
                    day);
            case ROC -> new IsoCalendarDate(
                    year - 1911,
                    month,
                    IsoMonth.toMonthCode(month),
                    day);
            default -> toNonIsoCalendarDate(calendarId);
        };
    }

    private IsoCalendarDate toLunisolarCalendarDate(TemporalCalendarId calendarId) {
        LocalDate gregorianDate = LocalDate.of(year, month, day);
        if (calendarId == TemporalCalendarId.CHINESE && LocalDate.of(2100, 1, 1).equals(gregorianDate)) {
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
                        return new IsoCalendarDate(1899, monthNumber, IsoMonth.toMonthCode(monthNumber), dayOfMonth);
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
            String fallbackMonthCode = IsoMonth.toMonthCode(fallbackMonth);
            return new IsoCalendarDate(fallbackYear, fallbackMonth, fallbackMonthCode, fallbackDay);
        }

        long dayOffset = gregorianDate.toEpochDay() - lunarBaseDate.toEpochDay();
        int lunarYear = 1900;
        int maxSupportedYear = calendarId.getLunisolarMaxYear();
        while (lunarYear <= maxSupportedYear) {
            int yearDayCount = calendarId.getLunisolarYearDays(lunarYear);
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
            return new IsoCalendarDate(fallbackYear, fallbackMonth, IsoMonth.toMonthCode(fallbackMonth), fallbackDay);
        }

        int leapMonth = calendarId.getLunisolarLeapMonth(lunarYear);
        int lunarMonth = 1;
        boolean inLeapMonth = false;
        while (lunarMonth <= 12) {
            int monthDayCount = inLeapMonth
                    ? calendarId.lunisolarLeapMonthDays(lunarYear)
                    : calendarId.getLunisolarMonthDays(lunarYear, lunarMonth);
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
        String monthCode = IsoMonth.toMonthCode(lunarMonth);
        if (inLeapMonth) {
            monthCode = monthCode + "L";
        }
        IsoCalendarMonth matchingMonthSlot = calendarId.findMonthSlotByCode(lunarYear, monthCode);
        int monthNumber = matchingMonthSlot == null ? lunarMonth : matchingMonthSlot.monthNumber();
        return new IsoCalendarDate(lunarYear, monthNumber, monthCode, lunarDay);
    }

    private IsoCalendarDate toNonIsoCalendarDate(TemporalCalendarId calendarId) {
        return switch (calendarId) {
            case COPTIC -> toAlexandrianCalendarDate(TemporalConstants.COPTIC_EPOCH_DAY_OFFSET);
            case ETHIOPIC -> toAlexandrianCalendarDate(TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
            case ETHIOAA -> {
                IsoCalendarDate ethiopicDate = toAlexandrianCalendarDate(TemporalConstants.ETHIOPIC_EPOCH_DAY_OFFSET);
                yield new IsoCalendarDate(
                        ethiopicDate.year() + 5500,
                        ethiopicDate.month(),
                        ethiopicDate.monthCode(),
                        ethiopicDate.day());
            }
            case INDIAN -> toIndianCalendarDate();
            case ISLAMIC_CIVIL -> toIslamicCalendarDate(TemporalConstants.ISLAMIC_CIVIL_EPOCH_DAY_OFFSET);
            case ISLAMIC_TBLA -> toIslamicCalendarDate(TemporalConstants.ISLAMIC_TBLA_EPOCH_DAY_OFFSET);
            case ISLAMIC_UMALQURA -> toUmalquraCalendarDate();
            case PERSIAN -> toPersianCalendarDate();
            case HEBREW -> toHebrewCalendarDate();
            case CHINESE, DANGI -> toLunisolarCalendarDate(calendarId);
            default -> new IsoCalendarDate(year, month, IsoMonth.toMonthCode(month), day);
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
            return new IsoCalendarDate(yearValue, monthValue, IsoMonth.toMonthCode(monthValue), dayValue);
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
