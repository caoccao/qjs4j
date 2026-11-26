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

import java.util.Arrays;

/** Conversions for the Chinese and Dangi calendars using their tabulated year and month lengths. */
final class TemporalLunisolarCalendar {
    private static final long[] CHINESE_YEARS;

    private static final long[] DANGI_YEARS;
    private static final long START_OF_1899 = new IsoDate(1899, 2, 10).toEpochDay();
    private static final long START_OF_1900 = new IsoDate(1900, 1, 31).toEpochDay();
    static {
        CHINESE_YEARS = yearStarts(TemporalCalendarId.CHINESE);
        DANGI_YEARS = yearStarts(TemporalCalendarId.DANGI);
    }

    private TemporalLunisolarCalendar() {
    }

    static IsoCalendarDate fromIso(IsoDate date, TemporalCalendarId calendar) {
        if (calendar == TemporalCalendarId.CHINESE && date.year() == 2100 && date.month() == 1 && date.day() == 1) {
            return IsoCalendarDate.regular(2099, 11, 21);
        }
        long epochDay = date.toEpochDay();
        if (epochDay < START_OF_1899) {
            return IsoCalendarDate.regular(date.year() - 1, date.month(), date.day());
        }
        if (epochDay < START_OF_1900) {
            long remaining = epochDay - START_OF_1899;
            int month = 1;
            for (int days : TemporalConstants.LUNISOLAR_MONTH_LENGTHS_YEAR_1899) {
                if (remaining < days) {
                    return IsoCalendarDate.regular(1899, month, (int) remaining + 1);
                }
                remaining -= days;
                month++;
            }
            return IsoCalendarDate.regular(1899, 12, date.day());
        }
        long[] starts = years(calendar);
        if (epochDay >= starts[starts.length - 1]) {
            return IsoCalendarDate.regular(date.year(), date.month(), date.day());
        }
        int index = Arrays.binarySearch(starts, epochDay);
        if (index < 0) {
            index = -index - 2;
        }
        int year = 1900 + index;
        return TemporalMonths.get(calendar, year).dateAtDayOffset(year, epochDay - starts[index]);
    }

    static IsoDate toIso(TemporalCalendarId calendar, int year, IsoCalendarMonth month, int day, String overflow) {
        long[] starts = years(calendar);
        int monthNumber = month.monthNumber();
        if (year == 1899) {
            int[] lengths = TemporalConstants.LUNISOLAR_MONTH_LENGTHS_YEAR_1899;
            if (month.leapMonth() || day > lengths[monthNumber - 1]) {
                return null;
            }
            long epochDay = START_OF_1899 + day - 1;
            for (int i = 0; i < monthNumber - 1; i++) {
                epochDay += lengths[i];
            }
            return IsoDate.createFromEpochDay(epochDay);
        }
        if (year < 1900 || year > calendar.getLunisolarMaxYear()) {
            int isoYear = year + 1;
            int isoDay = TemporalOverflow.REJECT.matches(overflow)
                    ? day
                    : Math.min(day, IsoDate.daysInMonth(isoYear, monthNumber));
            return new IsoDate(isoYear, monthNumber, isoDay);
        }
        long epochDay = starts[year - 1900] + TemporalMonths.get(calendar, year).daysBeforeMonth(monthNumber) + day - 1;
        return IsoDate.createFromEpochDay(epochDay);
    }

    private static long[] years(TemporalCalendarId calendar) {
        return calendar == TemporalCalendarId.DANGI ? DANGI_YEARS : CHINESE_YEARS;
    }

    private static long[] yearStarts(TemporalCalendarId calendar) {
        long[] starts = new long[calendar.getLunisolarMaxYear() - 1900 + 2];
        starts[0] = START_OF_1900;
        for (int i = 1; i < starts.length; i++) {
            starts[i] = starts[i - 1] + calendar.getLunisolarYearDays(1899 + i);
        }
        return starts;
    }
}
