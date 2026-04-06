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
 * Represents an ISO 8601 time with hour, minute, second, millisecond, microsecond, and nanosecond components.
 */
public record IsoTime(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {

    public static final IsoTime MIDNIGHT = new IsoTime(0, 0, 0, 0, 0, 0);

    public static int compareIsoTime(IsoTime firstTime, IsoTime secondTime) {
        if (firstTime.hour != secondTime.hour) {
            return Integer.compare(firstTime.hour, secondTime.hour);
        }
        if (firstTime.minute != secondTime.minute) {
            return Integer.compare(firstTime.minute, secondTime.minute);
        }
        if (firstTime.second != secondTime.second) {
            return Integer.compare(firstTime.second, secondTime.second);
        }
        if (firstTime.millisecond != secondTime.millisecond) {
            return Integer.compare(firstTime.millisecond, secondTime.millisecond);
        }
        if (firstTime.microsecond != secondTime.microsecond) {
            return Integer.compare(firstTime.microsecond, secondTime.microsecond);
        }
        return Integer.compare(firstTime.nanosecond, secondTime.nanosecond);
    }

    /**
     * Creates an IsoTime from total nanoseconds (mod 24 hours).
     */
    public static IsoTime fromNanoseconds(long totalNanoseconds) {
        long nanosecondsPerDay = 86_400_000_000_000L;
        totalNanoseconds = Math.floorMod(totalNanoseconds, nanosecondsPerDay);
        int hourValue = (int) (totalNanoseconds / 3_600_000_000_000L);
        totalNanoseconds %= 3_600_000_000_000L;
        int minuteValue = (int) (totalNanoseconds / 60_000_000_000L);
        totalNanoseconds %= 60_000_000_000L;
        int secondValue = (int) (totalNanoseconds / 1_000_000_000L);
        totalNanoseconds %= 1_000_000_000L;
        int millisecondValue = (int) (totalNanoseconds / 1_000_000L);
        totalNanoseconds %= 1_000_000L;
        int microsecondValue = (int) (totalNanoseconds / 1_000L);
        int nanosecondValue = (int) (totalNanoseconds % 1_000L);
        return new IsoTime(hourValue, minuteValue, secondValue, millisecondValue, microsecondValue, nanosecondValue);
    }

    public static IsoTime constrain(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        second = Math.max(0, Math.min(59, second));
        millisecond = Math.max(0, Math.min(999, millisecond));
        microsecond = Math.max(0, Math.min(999, microsecond));
        nanosecond = Math.max(0, Math.min(999, nanosecond));
        return new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
    }

    public static boolean isValidTime(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {
        if (hour < 0 || hour > 23) {
            return false;
        }
        if (minute < 0 || minute > 59) {
            return false;
        }
        if (second < 0 || second > 59) {
            return false;
        }
        if (millisecond < 0 || millisecond > 999) {
            return false;
        }
        if (microsecond < 0 || microsecond > 999) {
            return false;
        }
        return nanosecond >= 0 && nanosecond <= 999;
    }

    /**
     * Add nanoseconds to this time, returning a record with the new time and day overflow.
     */
    public AddResult addNanoseconds(long nanosecondsToAdd) {
        long totalNanoseconds = totalNanoseconds() + nanosecondsToAdd;
        long nanosecondsPerDay = 86_400_000_000_000L;
        int days = (int) Math.floorDiv(totalNanoseconds, nanosecondsPerDay);
        long remainder = Math.floorMod(totalNanoseconds, nanosecondsPerDay);
        return new AddResult(fromNanoseconds(remainder), days);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format(Locale.ROOT, "%02d:%02d:%02d", hour, minute, second));
        int totalFractionalNanoseconds = millisecond * 1_000_000 + microsecond * 1_000 + nanosecond;
        if (totalFractionalNanoseconds != 0) {
            String fractional = String.format(Locale.ROOT, "%09d", totalFractionalNanoseconds);
            int fractionalEndIndex = fractional.length();
            while (fractionalEndIndex > 0 && fractional.charAt(fractionalEndIndex - 1) == '0') {
                fractionalEndIndex--;
            }
            stringBuilder.append('.').append(fractional, 0, fractionalEndIndex);
        }
        return stringBuilder.toString();
    }

    /**
     * Returns total nanoseconds from midnight.
     */
    public long totalNanoseconds() {
        return ((long) hour * 3_600_000_000_000L)
                + ((long) minute * 60_000_000_000L)
                + ((long) second * 1_000_000_000L)
                + ((long) millisecond * 1_000_000L)
                + ((long) microsecond * 1_000L)
                + nanosecond;
    }

    public record AddResult(IsoTime time, int days) {
    }
}
