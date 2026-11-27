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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;

import java.util.Locale;

/**
 * Represents an ISO 8601 time with hour, minute, second, millisecond, microsecond, and nanosecond components.
 */
public record IsoTime(int hour, int minute, int second, int millisecond, int microsecond,
        int nanosecond) implements Comparable<IsoTime> {

    public static final IsoTime MIDNIGHT = new IsoTime(0, 0, 0, 0, 0, 0);

    /**
     * Creates an IsoTime from total nanoseconds (mod 24 hours).
     */
    public static IsoTime createFromNanoseconds(long totalNanoseconds) {
        totalNanoseconds = Math.floorMod(totalNanoseconds, TemporalConstants.DAY_NANOSECONDS);
        int hourValue = (int) (totalNanoseconds / TemporalConstants.HOUR_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.HOUR_NANOSECONDS;
        int minuteValue = (int) (totalNanoseconds / TemporalConstants.MINUTE_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.MINUTE_NANOSECONDS;
        int secondValue = (int) (totalNanoseconds / TemporalConstants.SECOND_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.SECOND_NANOSECONDS;
        int millisecondValue = (int) (totalNanoseconds / TemporalConstants.MILLISECOND_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.MILLISECOND_NANOSECONDS;
        int microsecondValue = (int) (totalNanoseconds / TemporalConstants.MICROSECOND_NANOSECONDS);
        int nanosecondValue = (int) (totalNanoseconds % TemporalConstants.MICROSECOND_NANOSECONDS);
        return new IsoTime(hourValue, minuteValue, secondValue, millisecondValue, microsecondValue, nanosecondValue);
    }

    public static IsoTime createNormalized(int hour, int minute, int second, int millisecond, int microsecond,
            int nanosecond) {
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        second = Math.max(0, Math.min(59, second));
        millisecond = Math.max(0, Math.min(999, millisecond));
        microsecond = Math.max(0, Math.min(999, microsecond));
        nanosecond = Math.max(0, Math.min(999, nanosecond));
        return new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
    }

    public static IsoTime parseTimeString(JSContext context, String input) {
        return IsoDateTimeParser.parseTimeString(context, input);
    }

    public IsoTime clampSecondToValidRange() {
        if (second == 60) {
            return new IsoTime(hour, minute, 59, millisecond, microsecond, nanosecond);
        } else {
            return this;
        }
    }

    @Override
    public int compareTo(IsoTime otherIsoTime) {
        if (hour != otherIsoTime.hour) {
            return Integer.compare(hour, otherIsoTime.hour);
        }
        if (minute != otherIsoTime.minute) {
            return Integer.compare(minute, otherIsoTime.minute);
        }
        if (second != otherIsoTime.second) {
            return Integer.compare(second, otherIsoTime.second);
        }
        if (millisecond != otherIsoTime.millisecond) {
            return Integer.compare(millisecond, otherIsoTime.millisecond);
        }
        if (microsecond != otherIsoTime.microsecond) {
            return Integer.compare(microsecond, otherIsoTime.microsecond);
        }
        return Integer.compare(nanosecond, otherIsoTime.nanosecond);
    }

    /**
     * Formats the fractional seconds part (ms, us, ns) to the given number of digits. Returns empty string if
     * {@code digits <= 0}.
     */
    public String formatFractionalPart(int digits) {
        if (digits <= 0) {
            return "";
        }
        String nineDigits = String.format(Locale.ROOT, "%03d%03d%03d", millisecond, microsecond, nanosecond);
        return nineDigits.substring(0, digits);
    }

    /**
     * Formats this time as a string with configurable precision. Shared by PlainTime, PlainDateTime, and ZonedDateTime
     * toString operations.
     *
     * @param smallestUnit
     *            the smallest unit to include (e.g. minute truncates seconds)
     * @param autoFractionalSecondDigits
     *            if true, auto-trim trailing zeros from fractional part
     * @param fractionalSecondDigits
     *            number of fractional digits (0-9), ignored if auto
     */
    public String formatTimeString(TemporalUnit smallestUnit, boolean autoFractionalSecondDigits,
            int fractionalSecondDigits) {
        return IsoDateTimeFormat.time(this, smallestUnit, autoFractionalSecondDigits, fractionalSecondDigits);
    }

    public boolean isValid() {
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

    public String toString(Integer fractionalSecondDigits) {
        return formatTimeString(TemporalUnit.SECOND, fractionalSecondDigits == null,
                fractionalSecondDigits == null ? 0 : Math.max(0, fractionalSecondDigits));
    }

    @Override
    public String toString() {
        return toString(null);
    }

    /**
     * Returns total nanoseconds from midnight.
     */
    public long totalNanoseconds() {
        return ((long) hour * TemporalConstants.HOUR_NANOSECONDS)
                + ((long) minute * TemporalConstants.MINUTE_NANOSECONDS)
                + ((long) second * TemporalConstants.SECOND_NANOSECONDS)
                + ((long) millisecond * TemporalConstants.MILLISECOND_NANOSECONDS)
                + ((long) microsecond * TemporalConstants.MICROSECOND_NANOSECONDS) + nanosecond;
    }

    public int totalNanosecondsWithinSecond() {
        return millisecond * 1_000_000 + microsecond * 1_000 + nanosecond;
    }

    /** Append ISO fractional seconds at fixed precision, or trim trailing zeros for automatic precision. */

    public static IsoTime regulate(JSContext context, String overflow, String invalidTimeMessage, int hour, int minute,
            int second, int millisecond, int microsecond, int nanosecond) {
        if (!"reject".equals(overflow)) {
            return createNormalized(hour, minute, second, millisecond, microsecond, nanosecond);
        }
        IsoTime time = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        if (!time.isValid()) {
            return context.rejectRangeError(invalidTimeMessage);
        }
        return time;
    }
}
