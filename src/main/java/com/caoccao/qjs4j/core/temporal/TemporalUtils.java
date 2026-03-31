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

import java.math.BigInteger;
import java.util.Locale;

/**
 * Shared utilities for Temporal types.
 */
public final class TemporalUtils {

    private TemporalUtils() {
    }

    private static boolean canParseCalendarLikeString(JSContext context, String calendarLikeText) {
        JSValue originalPendingException = context.getPendingException();
        String calendarLikeBaseText = calendarLikeText;
        int annotationStart = calendarLikeBaseText.indexOf('[');
        if (annotationStart >= 0) {
            calendarLikeBaseText = calendarLikeBaseText.substring(0, annotationStart);
        }

        if (TemporalParser.parseDateString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (TemporalParser.parseYearMonthString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (TemporalParser.parseMonthDayString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (calendarLikeBaseText.length() == 5
                && Character.isDigit(calendarLikeBaseText.charAt(0))
                && Character.isDigit(calendarLikeBaseText.charAt(1))
                && calendarLikeBaseText.charAt(2) == '-'
                && Character.isDigit(calendarLikeBaseText.charAt(3))
                && Character.isDigit(calendarLikeBaseText.charAt(4))
                && TemporalParser.parseMonthDayString(context, "--" + calendarLikeBaseText) != null
                && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (originalPendingException != null) {
            context.setPendingException(originalPendingException);
        }
        return false;
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

    private static String firstCalendarAnnotation(String text) {
        int annotationStart = text.indexOf('[');
        while (annotationStart >= 0) {
            int annotationEnd = text.indexOf(']', annotationStart);
            if (annotationEnd <= annotationStart) {
                return null;
            }
            String annotationContent = text.substring(annotationStart + 1, annotationEnd);
            if (!annotationContent.isEmpty() && annotationContent.charAt(0) == '!') {
                annotationContent = annotationContent.substring(1);
            }
            int equalSignIndex = annotationContent.indexOf('=');
            if (equalSignIndex > 0) {
                String annotationKey = annotationContent.substring(0, equalSignIndex);
                if ("u-ca".equals(annotationKey)) {
                    return annotationContent.substring(equalSignIndex + 1);
                }
            }
            annotationStart = text.indexOf('[', annotationEnd + 1);
        }
        return null;
    }

    /**
     * Format a Duration string from components.
     */
    public static String formatDurationString(long years, long months, long weeks, long days,
                                              long hours, long minutes, long seconds,
                                              long milliseconds, long microseconds, long nanoseconds) {
        boolean negative = years < 0 || months < 0 || weeks < 0 || days < 0
                || hours < 0 || minutes < 0 || seconds < 0
                || milliseconds < 0 || microseconds < 0 || nanoseconds < 0;
        BigInteger yearsValue = BigInteger.valueOf(years);
        BigInteger monthsValue = BigInteger.valueOf(months);
        BigInteger weeksValue = BigInteger.valueOf(weeks);
        BigInteger daysValue = BigInteger.valueOf(days);
        BigInteger hoursValue = BigInteger.valueOf(hours);
        BigInteger minutesValue = BigInteger.valueOf(minutes);
        BigInteger secondsValue = BigInteger.valueOf(seconds);
        BigInteger millisecondsValue = BigInteger.valueOf(milliseconds);
        BigInteger microsecondsValue = BigInteger.valueOf(microseconds);
        BigInteger nanosecondsValue = BigInteger.valueOf(nanoseconds);
        if (negative) {
            yearsValue = yearsValue.abs();
            monthsValue = monthsValue.abs();
            weeksValue = weeksValue.abs();
            daysValue = daysValue.abs();
            hoursValue = hoursValue.abs();
            minutesValue = minutesValue.abs();
            secondsValue = secondsValue.abs();
            millisecondsValue = millisecondsValue.abs();
            microsecondsValue = microsecondsValue.abs();
            nanosecondsValue = nanosecondsValue.abs();
        }

        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }
        sb.append('P');

        if (yearsValue.signum() != 0) {
            sb.append(yearsValue).append('Y');
        }
        if (monthsValue.signum() != 0) {
            sb.append(monthsValue).append('M');
        }
        if (weeksValue.signum() != 0) {
            sb.append(weeksValue).append('W');
        }
        if (daysValue.signum() != 0) {
            sb.append(daysValue).append('D');
        }

        BigInteger totalSubsecondNanoseconds = millisecondsValue.multiply(BigInteger.valueOf(1_000_000L))
                .add(microsecondsValue.multiply(BigInteger.valueOf(1_000L)))
                .add(nanosecondsValue);
        BigInteger[] secondCarryAndSubsecondNanoseconds = totalSubsecondNanoseconds.divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
        BigInteger secondsWithCarry = secondsValue.add(secondCarryAndSubsecondNanoseconds[0]);
        BigInteger subsecondNanosecondsRemainder = secondCarryAndSubsecondNanoseconds[1];

        boolean hasTimePart = hoursValue.signum() != 0 || minutesValue.signum() != 0 || secondsWithCarry.signum() != 0
                || subsecondNanosecondsRemainder.signum() != 0;
        if (hasTimePart) {
            sb.append('T');
            if (hoursValue.signum() != 0) {
                sb.append(hoursValue).append('H');
            }
            if (minutesValue.signum() != 0) {
                sb.append(minutesValue).append('M');
            }
            if (secondsWithCarry.signum() != 0 || subsecondNanosecondsRemainder.signum() != 0) {
                sb.append(secondsWithCarry);
                if (subsecondNanosecondsRemainder.signum() != 0) {
                    String fractional = String.format(Locale.ROOT, "%09d", subsecondNanosecondsRemainder.intValue());
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
        if (options instanceof JSUndefined || options == null) {
            return "auto";
        }
        if (!(options instanceof JSObject optionsObj)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }
        String calendarNameOption = getStringOption(context, optionsObj, "calendarName", "auto");
        if (context.hasPendingException()) {
            return null;
        }
        if (!"auto".equals(calendarNameOption)
                && !"always".equals(calendarNameOption)
                && !"never".equals(calendarNameOption)
                && !"critical".equals(calendarNameOption)) {
            context.throwRangeError("Temporal error: Invalid calendarName option: " + calendarNameOption);
            return null;
        }
        return calendarNameOption;
    }

    /**
     * Gets an integer field from a JSObject, returning the default if undefined.
     */
    public static int getIntegerField(JSContext context, JSObject obj, String key, int defaultValue) {
        JSValue value = obj.get(PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
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
        if (options instanceof JSUndefined || options == null) {
            return "constrain";
        }
        if (!(options instanceof JSObject optionsObj)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }
        String overflow = getStringOption(context, optionsObj, "overflow", "constrain");
        if (context.hasPendingException()) {
            return null;
        }
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
        if (context.hasPendingException()) {
            return null;
        }
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        JSString stringValue = JSTypeConversions.toString(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return stringValue.value();
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

    public static String toTemporalCalendarWithISODefault(JSContext context, JSValue calendarValue) {
        if (calendarValue instanceof JSUndefined || calendarValue == null) {
            return "iso8601";
        }
        if (calendarValue instanceof JSTemporalPlainDate temporalPlainDate) {
            return temporalPlainDate.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainDateTime temporalPlainDateTime) {
            return temporalPlainDateTime.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainMonthDay temporalPlainMonthDay) {
            return temporalPlainMonthDay.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainYearMonth temporalPlainYearMonth) {
            return temporalPlainYearMonth.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalZonedDateTime temporalZonedDateTime) {
            return temporalZonedDateTime.getCalendarId();
        }
        if (!(calendarValue instanceof JSString calendarString)) {
            context.throwTypeError("Temporal error: Calendar must be string.");
            return null;
        }

        String normalizedCalendarId = calendarString.value().toLowerCase(Locale.ROOT);
        if ("iso8601".equals(normalizedCalendarId)) {
            return normalizedCalendarId;
        }

        String calendarStringValue = calendarString.value();
        if (!canParseCalendarLikeString(context, calendarStringValue)) {
            context.throwRangeError("Temporal error: Invalid calendar.");
            return null;
        }

        String firstCalendarAnnotation = firstCalendarAnnotation(calendarStringValue);
        if (firstCalendarAnnotation != null) {
            String validatedCalendar = validateCalendar(context, new JSString(firstCalendarAnnotation));
            if (context.hasPendingException()) {
                return null;
            }
            return validatedCalendar;
        }
        return "iso8601";
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
        String normalizedCalendarId = calendarString.value().toLowerCase(Locale.ROOT);
        if (!"iso8601".equals(normalizedCalendarId)) {
            context.throwRangeError("Temporal error: Invalid calendar.");
            return null;
        }
        return normalizedCalendarId;
    }
}
