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

/**
 * Shared utilities for Temporal types.
 */
public final class TemporalUtils {
    private TemporalUtils() {
    }

    /**
     * Generic receiver type check for Temporal prototype methods.
     * Returns the cast value, or null after throwing TypeError.
     */
    @SuppressWarnings("unchecked")
    public static <T> T checkReceiver(JSContext context, JSValue thisArg, Class<T> expectedType, String typeName, String methodName) {
        if (!expectedType.isInstance(thisArg)) {
            context.throwTypeError("Method " + typeName + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return (T) thisArg;
    }

    public static String firstCalendarAnnotation(String text) {
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
        if (TemporalDisplayCalendar.fromString(calendarNameOption) == null) {
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
        if (TemporalOverflow.fromString(overflow) == null) {
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

    public static int islamicDaysBeforeYear(int islamicYear) {
        return (int) (354L * (islamicYear - 1L) + Math.floorDiv(11L * islamicYear + 3L, 30L));
    }

    /**
     * Returns true if firstDate surpasses secondDate in the direction indicated by sign.
     * Positive sign means firstDate is later; negative sign means firstDate is earlier.
     */
    public static boolean isoDateSurpasses(int sign, IsoDate firstDate, IsoDate secondDate) {
        return sign * firstDate.compareTo(secondDate) > 0;
    }

    /**
     * Appends calendar annotation to string if needed.
     */
    public static String maybeAppendCalendar(String dateTimeString, TemporalCalendarId calendarId, String calendarNameOption) {
        switch (calendarNameOption) {
            case "never":
                return dateTimeString;
            case "always":
                return dateTimeString + "[u-ca=" + calendarId.identifier() + "]";
            case "critical":
                return dateTimeString + "[!u-ca=" + calendarId.identifier() + "]";
            case "auto":
            default:
                if (calendarId == TemporalCalendarId.ISO8601) {
                    return dateTimeString;
                }
                return dateTimeString + "[u-ca=" + calendarId.identifier() + "]";
        }
    }

    public static int toArithmeticPersianYear(int persianYear) {
        if (persianYear <= 0) {
            return persianYear - 1;
        }
        return persianYear;
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

}
