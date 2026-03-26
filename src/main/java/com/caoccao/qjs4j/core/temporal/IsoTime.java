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
 * Represents an ISO 8601 time with hour, minute, second, millisecond, microsecond, and nanosecond components.
 */
public record IsoTime(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {

    public static final IsoTime MIDNIGHT = new IsoTime(0, 0, 0, 0, 0, 0);

    public static int compareIsoTime(IsoTime one, IsoTime two) {
        if (one.hour != two.hour) {
            return Integer.compare(one.hour, two.hour);
        }
        if (one.minute != two.minute) {
            return Integer.compare(one.minute, two.minute);
        }
        if (one.second != two.second) {
            return Integer.compare(one.second, two.second);
        }
        if (one.millisecond != two.millisecond) {
            return Integer.compare(one.millisecond, two.millisecond);
        }
        if (one.microsecond != two.microsecond) {
            return Integer.compare(one.microsecond, two.microsecond);
        }
        return Integer.compare(one.nanosecond, two.nanosecond);
    }

    /**
     * Creates an IsoTime from total nanoseconds (mod 24 hours).
     */
    public static IsoTime fromNanoseconds(long totalNs) {
        long nsPerDay = 86_400_000_000_000L;
        totalNs = Math.floorMod(totalNs, nsPerDay);
        int h = (int) (totalNs / 3_600_000_000_000L);
        totalNs %= 3_600_000_000_000L;
        int min = (int) (totalNs / 60_000_000_000L);
        totalNs %= 60_000_000_000L;
        int s = (int) (totalNs / 1_000_000_000L);
        totalNs %= 1_000_000_000L;
        int ms = (int) (totalNs / 1_000_000L);
        totalNs %= 1_000_000L;
        int us = (int) (totalNs / 1_000L);
        int ns = (int) (totalNs % 1_000L);
        return new IsoTime(h, min, s, ms, us, ns);
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
    public AddResult addNanoseconds(long addNs) {
        long totalNs = totalNanoseconds() + addNs;
        long nsPerDay = 86_400_000_000_000L;
        int days = (int) Math.floorDiv(totalNs, nsPerDay);
        long remainder = Math.floorMod(totalNs, nsPerDay);
        return new AddResult(fromNanoseconds(remainder), days);
    }

    @Override
    public String toString() {
        return TemporalUtils.formatIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
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
