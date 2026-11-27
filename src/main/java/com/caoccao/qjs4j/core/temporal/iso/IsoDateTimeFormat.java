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

package com.caoccao.qjs4j.core.temporal.iso;

import com.caoccao.qjs4j.core.temporal.TemporalUnit;

/** Formats ISO date, time and offset components for Temporal strings. */
public final class IsoDateTimeFormat {
    private IsoDateTimeFormat() {
    }

    public static void appendFraction(StringBuilder result, int nanos, int digits) {
        if (digits == 0 || (digits < 0 && nanos == 0)) {
            return;
        }
        // A leading 1 preserves all nine fractional positions, including leading zeros.
        String padded = Integer.toString(1_000_000_000 + nanos);
        int end = digits < 0 ? padded.length() : 1 + digits;
        if (digits < 0) {
            while (padded.charAt(end - 1) == '0') {
                end--;
            }
        }
        result.append('.').append(padded, 1, end);
    }

    private static void appendTwoDigits(StringBuilder result, int value) {
        if (value >= 0 && value < 10) {
            result.append('0');
        }
        result.append(value);
    }

    static String date(IsoDate date, boolean includeYear, boolean includeDay) {
        StringBuilder text = new StringBuilder(14);
        if (includeYear) {
            int year = date.year();
            boolean extended = year < 0 || year > 9999;
            if (extended) {
                text.append(year < 0 ? '-' : '+');
            }
            long magnitude = Math.abs((long) year);
            for (int place = extended ? 100_000 : 1000; place > 1 && magnitude < place; place /= 10) {
                text.append('0');
            }
            text.append(magnitude).append('-');
        }
        appendTwoDigits(text, date.month());
        if (includeDay) {
            text.append('-');
            appendTwoDigits(text, date.day());
        }
        return text.toString();
    }

    public static String offset(int totalSeconds) {
        StringBuilder text = new StringBuilder(9);
        text.append(totalSeconds < 0 ? '-' : '+');
        long magnitude = Math.abs((long) totalSeconds);
        appendTwoDigits(text, (int) (magnitude / 3600));
        text.append(':');
        appendTwoDigits(text, (int) (magnitude / 60 % 60));
        int seconds = (int) (magnitude % 60);
        if (seconds != 0) {
            text.append(':');
            appendTwoDigits(text, seconds);
        }
        return text.toString();
    }

    static String time(IsoTime time, TemporalUnit smallestUnit, boolean autoFractionalSecondDigits,
            int fractionalSecondDigits) {
        StringBuilder result = new StringBuilder(18);
        appendTwoDigits(result, time.hour());
        result.append(':');
        appendTwoDigits(result, time.minute());
        if (smallestUnit != TemporalUnit.MINUTE) {
            result.append(':');
            appendTwoDigits(result, time.second());
            appendFraction(result, time.millisecond() * 1_000_000 + time.microsecond() * 1_000 + time.nanosecond(),
                    autoFractionalSecondDigits ? -1 : fractionalSecondDigits);
        }
        return result.toString();
    }
}
