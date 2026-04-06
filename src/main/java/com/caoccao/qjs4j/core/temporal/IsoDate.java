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

import java.util.Locale;

/**
 * Represents an ISO 8601 date with year, month, and day components.
 */
public record IsoDate(int year, int month, int day) {

    private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    public static int compareIsoDate(IsoDate firstDate, IsoDate secondDate) {
        if (firstDate.year != secondDate.year) {
            return Integer.compare(firstDate.year, secondDate.year);
        }
        if (firstDate.month != secondDate.month) {
            return Integer.compare(firstDate.month, secondDate.month);
        }
        return Integer.compare(firstDate.day, secondDate.day);
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

    public static IsoDate fromEpochDay(long epochDay) {
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

    public static boolean isLeapYear(int year) {
        if (year % 4 != 0) {
            return false;
        }
        if (year % 100 != 0) {
            return true;
        }
        return year % 400 == 0;
    }

    public static IsoDate constrain(int year, int month, int dayOfMonth) {
        month = Math.max(1, Math.min(12, month));
        dayOfMonth = Math.max(1, Math.min(daysInMonth(year, month), dayOfMonth));
        return new IsoDate(year, month, dayOfMonth);
    }

    public static boolean isValidIsoDate(int year, int month, int dayOfMonth) {
        if (month < 1 || month > 12) {
            return false;
        }
        if (dayOfMonth < 1 || dayOfMonth > daysInMonth(year, month)) {
            return false;
        }
        if (year < -271821 || year > 275760) {
            return false;
        }
        if (year == -271821) {
            if (month < 4) {
                return false;
            }
            if (month == 4 && dayOfMonth < 19) {
                return false;
            }
        }
        if (year == 275760) {
            if (month > 9) {
                return false;
            }
            return month != 9 || dayOfMonth <= 13;
        }
        return true;
    }

    public IsoDate addDays(int days) {
        long epochDay = toEpochDay() + days;
        return fromEpochDay(epochDay);
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

    @Override
    public String toString() {
        if (year >= 0 && year <= 9999) {
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
        }
        String sign = year >= 0 ? "+" : "-";
        return String.format(Locale.ROOT, "%s%06d-%02d-%02d", sign, Math.abs(year), month, day);
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
