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

import com.caoccao.qjs4j.core.*;

import java.util.Locale;

/**
 * Shared utilities for Temporal types.
 */
public final class TemporalUtils {

    private TemporalUtils() {
    }

    /**
     * Constrains a date to valid ISO values.
     */
    public static IsoDate constrainIsoDate(int year, int month, int day) {
        month = Math.max(1, Math.min(12, month));
        day = Math.max(1, Math.min(IsoDate.daysInMonth(year, month), day));
        return new IsoDate(year, month, day);
    }

    /**
     * Constrains a time to valid ISO values.
     */
    public static IsoTime constrainIsoTime(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        second = Math.max(0, Math.min(59, second));
        millisecond = Math.max(0, Math.min(999, millisecond));
        microsecond = Math.max(0, Math.min(999, microsecond));
        nanosecond = Math.max(0, Math.min(999, nanosecond));
        return new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
    }

    /**
     * Format a Duration string from components.
     */
    public static String formatDurationString(long years, long months, long weeks, long days,
                                              long hours, long minutes, long seconds,
                                              long milliseconds, long microseconds, long nanoseconds) {
        boolean negative = false;
        if (years < 0 || months < 0 || weeks < 0 || days < 0 ||
                hours < 0 || minutes < 0 || seconds < 0 ||
                milliseconds < 0 || microseconds < 0 || nanoseconds < 0) {
            negative = true;
            years = Math.abs(years);
            months = Math.abs(months);
            weeks = Math.abs(weeks);
            days = Math.abs(days);
            hours = Math.abs(hours);
            minutes = Math.abs(minutes);
            seconds = Math.abs(seconds);
            milliseconds = Math.abs(milliseconds);
            microseconds = Math.abs(microseconds);
            nanoseconds = Math.abs(nanoseconds);
        }

        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }
        sb.append('P');

        if (years != 0) {
            sb.append(years).append('Y');
        }
        if (months != 0) {
            sb.append(months).append('M');
        }
        if (weeks != 0) {
            sb.append(weeks).append('W');
        }
        if (days != 0) {
            sb.append(days).append('D');
        }

        boolean hasTimePart = hours != 0 || minutes != 0 || seconds != 0 ||
                milliseconds != 0 || microseconds != 0 || nanoseconds != 0;
        if (hasTimePart) {
            sb.append('T');
            if (hours != 0) {
                sb.append(hours).append('H');
            }
            if (minutes != 0) {
                sb.append(minutes).append('M');
            }
            long totalSubSecondNs = milliseconds * 1_000_000 + microseconds * 1_000 + nanoseconds;
            if (seconds != 0 || totalSubSecondNs != 0) {
                sb.append(seconds);
                if (totalSubSecondNs != 0) {
                    String fractional = String.format(Locale.ROOT, "%09d", totalSubSecondNs);
                    // Trim trailing zeros
                    int end = fractional.length();
                    while (end > 0 && fractional.charAt(end - 1) == '0') {
                        end--;
                    }
                    sb.append('.').append(fractional, 0, end);
                }
                sb.append('S');
            }
        }

        // Empty duration = PT0S
        if (sb.length() == 1 || (sb.length() == 2 && negative)) {
            sb.append("T0S");
        }

        return sb.toString();
    }

    public static String formatIsoDate(int year, int month, int day) {
        if (year >= 0 && year <= 9999) {
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
        } else {
            String sign = year >= 0 ? "+" : "-";
            return String.format(Locale.ROOT, "%s%06d-%02d-%02d", sign, Math.abs(year), month, day);
        }
    }

    public static String formatIsoTime(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%02d:%02d:%02d", hour, minute, second));
        if (nanosecond != 0) {
            sb.append(String.format(Locale.ROOT, ".%03d%03d%03d", millisecond, microsecond, nanosecond));
        } else if (microsecond != 0) {
            sb.append(String.format(Locale.ROOT, ".%03d%03d", millisecond, microsecond));
        } else if (millisecond != 0) {
            sb.append(String.format(Locale.ROOT, ".%03d", millisecond));
        }
        return sb.toString();
    }

    public static String formatIsoTimeWithPrecision(int hour, int minute, int second,
                                                    int millisecond, int microsecond, int nanosecond,
                                                    Object fractionalSecondDigits) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%02d:%02d:%02d", hour, minute, second));
        if (fractionalSecondDigits instanceof Integer digits) {
            if (digits == 0) {
                return sb.toString();
            }
            int totalFractionalNs = millisecond * 1_000_000 + microsecond * 1_000 + nanosecond;
            String fractional = String.format(Locale.ROOT, "%09d", totalFractionalNs);
            sb.append('.').append(fractional, 0, digits);
        } else {
            // "auto" mode — same as default
            if (nanosecond != 0) {
                sb.append(String.format(Locale.ROOT, ".%03d%03d%03d", millisecond, microsecond, nanosecond));
            } else if (microsecond != 0) {
                sb.append(String.format(Locale.ROOT, ".%03d%03d", millisecond, microsecond));
            } else if (millisecond != 0) {
                sb.append(String.format(Locale.ROOT, ".%03d", millisecond));
            }
        }
        return sb.toString();
    }

    /**
     * Gets a calendar name display option from options.
     * Returns "auto", "always", "never", or "critical".
     */
    public static String getCalendarNameOption(JSContext context, JSValue options) {
        if (!(options instanceof JSObject optionsObj)) {
            return "auto";
        }
        return getStringOption(context, optionsObj, "calendarName", "auto");
    }

    /**
     * Gets an integer field from a JSObject, returning the default if undefined.
     */
    public static int getIntegerField(JSContext context, JSObject obj, String key, int defaultValue) {
        JSValue value = obj.get(PropertyKey.fromString(key));
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        double num = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (!Double.isFinite(num)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Integer.MIN_VALUE;
        }
        return (int) num;
    }

    /**
     * Gets the overflow option from an options object.
     * Returns "constrain" or "reject".
     */
    public static String getOverflowOption(JSContext context, JSValue options) {
        if (!(options instanceof JSObject optionsObj)) {
            return "constrain";
        }
        String overflow = getStringOption(context, optionsObj, "overflow", "constrain");
        if (!"constrain".equals(overflow) && !"reject".equals(overflow)) {
            context.throwRangeError("Temporal error: Invalid overflow option: " + overflow);
            return null;
        }
        return overflow;
    }

    /**
     * Gets a string option from an options object.
     */
    public static String getStringOption(JSContext context, JSObject options, String key, String defaultValue) {
        JSValue value = options.get(PropertyKey.fromString(key));
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        return JSTypeConversions.toString(context, value).value();
    }

    /**
     * Appends calendar annotation to string if needed.
     */
    public static String maybeAppendCalendar(String dateTimeString, String calendarId, String calendarNameOption) {
        switch (calendarNameOption) {
            case "never":
                return dateTimeString;
            case "always":
                return dateTimeString + "[u-ca=" + calendarId + "]";
            case "critical":
                return dateTimeString + "[!u-ca=" + calendarId + "]";
            case "auto":
            default:
                if ("iso8601".equals(calendarId)) {
                    return dateTimeString;
                }
                return dateTimeString + "[u-ca=" + calendarId + "]";
        }
    }

    /**
     * Formats a month code in "M01"..."M12" format.
     */
    public static String monthCode(int month) {
        return String.format(Locale.ROOT, "M%02d", month);
    }

    /**
     * Converts a JSValue to a finite integer or throws a RangeError.
     * Returns Integer.MIN_VALUE if a pending exception was set.
     */
    public static int toIntegerThrowOnInfinity(JSContext context, JSValue value) {
        double num = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (!Double.isFinite(num)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Integer.MIN_VALUE;
        }
        return (int) num;
    }

    /**
     * Converts a JSValue to a finite integral long or throws a RangeError.
     * Unlike toIntegerThrowOnInfinity, this rejects fractional values.
     * Returns Long.MIN_VALUE if a pending exception was set.
     */
    public static long toLongIfIntegral(JSContext context, JSValue value) {
        double num = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(num)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        if (num != Math.floor(num)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        return (long) num;
    }

    /**
     * Validates that a calendar value is a string, matching V8's error message.
     */
    public static String validateCalendar(JSContext context, JSValue calendarValue) {
        if (calendarValue instanceof JSUndefined || calendarValue == null) {
            return "iso8601";
        }
        if (!(calendarValue instanceof JSString calendarString)) {
            context.throwTypeError("Temporal error: Calendar must be string.");
            return null;
        }
        String calendarId = calendarString.value();
        // Validate calendar identifier (case-insensitive)
        String normalized = calendarId.toLowerCase(Locale.ROOT);
        // For now, accept "iso8601" and other known calendar names
        return normalized;
    }
}
