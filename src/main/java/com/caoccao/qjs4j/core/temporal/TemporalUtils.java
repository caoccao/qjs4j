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
    private static final String[] SUPPORTED_CALENDAR_IDENTIFIERS = {
            "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
            "gregory", "hebrew", "indian", "islamic-civil", "islamic-tbla",
            "islamic-umalqura", "iso8601", "japanese", "persian", "roc"
    };

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

        if (TemporalParser.parseDateTimeString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
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

        if (TemporalParser.parseTimeString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
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

    private static String canonicalizeCalendarIdentifier(String calendarIdentifier) {
        if (calendarIdentifier == null) {
            return null;
        }
        String normalizedCalendarIdentifier = calendarIdentifier.toLowerCase(Locale.ROOT);
        if ("islamicc".equals(normalizedCalendarIdentifier)) {
            return "islamic-civil";
        }
        if ("ethiopic-amete-alem".equals(normalizedCalendarIdentifier)) {
            return "ethioaa";
        }
        if ("gregorian".equals(normalizedCalendarIdentifier)) {
            return "gregory";
        }
        return normalizedCalendarIdentifier;
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
    public static int getIntegerField(JSContext context, JSObject sourceObject, String optionKey, int defaultValue) {
        JSValue value = sourceObject.get(PropertyKey.fromString(optionKey));
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Integer.MIN_VALUE;
        }
        return (int) numericValue;
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
    public static String getStringOption(JSContext context, JSObject options, String optionKey, String defaultValue) {
        JSValue value = options.get(PropertyKey.fromString(optionKey));
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

    private static boolean isSupportedCalendarIdentifier(String calendarIdentifier) {
        if (calendarIdentifier == null) {
            return false;
        }
        String canonicalCalendarIdentifier = canonicalizeCalendarIdentifier(calendarIdentifier);
        for (String supportedCalendarIdentifier : SUPPORTED_CALENDAR_IDENTIFIERS) {
            if (supportedCalendarIdentifier.equals(canonicalCalendarIdentifier)) {
                return true;
            }
        }
        return false;
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
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Integer.MIN_VALUE;
        }
        return (int) numericValue;
    }

    /**
     * Converts a JSValue to a finite integral long or throws a RangeError.
     * Unlike toIntegerThrowOnInfinity, this rejects fractional values.
     * Returns Long.MIN_VALUE if a pending exception was set.
     */
    public static long toLongIfIntegral(JSContext context, JSValue value) {
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        if (numericValue != Math.floor(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        return (long) numericValue;
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

        String canonicalCalendarId = canonicalizeCalendarIdentifier(calendarString.value());
        if (isSupportedCalendarIdentifier(canonicalCalendarId)) {
            return canonicalCalendarId;
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
        String canonicalCalendarId = canonicalizeCalendarIdentifier(calendarString.value());
        if (!isSupportedCalendarIdentifier(canonicalCalendarId)) {
            context.throwRangeError("Temporal error: Invalid calendar.");
            return null;
        }
        return canonicalCalendarId;
    }
}
