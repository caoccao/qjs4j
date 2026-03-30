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

/**
 * Represents an ISO 8601 date with year, month, and day components.
 */
public record IsoDate(int year, int month, int day) {

    private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    public static int compareIsoDate(IsoDate one, IsoDate two) {
        if (one.year != two.year) {
            return Integer.compare(one.year, two.year);
        }
        if (one.month != two.month) {
            return Integer.compare(one.month, two.month);
        }
        return Integer.compare(one.day, two.day);
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
        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        if (m <= 2) {
            y++;
        }
        return new IsoDate((int) y, (int) m, (int) d);
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

    public static boolean isValidIsoDate(int year, int month, int day) {
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

    public IsoDate addDays(int days) {
        long epochDay = toEpochDay() + days;
        return fromEpochDay(epochDay);
    }

    public int dayOfWeek() {
        long epochDay = toEpochDay();
        // 1970-01-01 is Thursday (4), ISO weekday: 1=Monday, 7=Sunday
        int dow = Math.floorMod(epochDay + 3, 7) + 1;
        return dow;
    }

    public int dayOfYear() {
        int result = day;
        for (int m = 1; m < month; m++) {
            result += daysInMonth(year, m);
        }
        return result;
    }

    public long toEpochDay() {
        long y = year;
        long m = month;
        long d = day;
        // Algorithm from https://howardhinnant.github.io/date_algorithms.html
        if (m <= 2) {
            y--;
        }
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    @Override
    public String toString() {
        return TemporalUtils.formatIsoDate(year, month, day);
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
