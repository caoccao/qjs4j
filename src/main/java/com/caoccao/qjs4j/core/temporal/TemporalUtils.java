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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Shared utilities for Temporal types.
 */
public final class TemporalUtils {
    private static final LocalDate MAXIMUM_TEMPORAL_DATE = LocalDate.of(275760, 9, 13);
    private static final LocalDateTime MAXIMUM_TEMPORAL_DATE_TIME = LocalDateTime.of(
            275760, 9, 13, 23, 59, 59, 999_999_999);
    private static final LocalDate MINIMUM_TEMPORAL_DATE = LocalDate.of(-271821, 4, 19);
    private static final LocalDateTime MINIMUM_TEMPORAL_DATE_TIME = LocalDateTime.of(
            -271821, 4, 20, 0, 0, 0, 0);
    private static final BigInteger NS_MAX_INSTANT = new BigInteger("8640000000000000000000");
    private static final BigInteger NS_MIN_INSTANT = new BigInteger("-8640000000000000000000");

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

    public static JSObject getTemporalPrototype(JSContext context, String typeName) {
        JSValue temporal = context.getGlobalObject().get(PropertyKey.fromString("Temporal"));
        if (temporal instanceof JSObject temporalObject) {
            JSValue constructor = temporalObject.get(PropertyKey.fromString(typeName));
            if (constructor instanceof JSObject constructorObject) {
                JSValue prototype = constructorObject.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject prototypeObject) {
                    return prototypeObject;
                }
            }
        }
        return null;
    }

    public static boolean isDateTimeWithinTemporalRange(LocalDateTime dateTime) {
        return !dateTime.isBefore(MINIMUM_TEMPORAL_DATE_TIME) && !dateTime.isAfter(MAXIMUM_TEMPORAL_DATE_TIME);
    }

    public static boolean isDateWithinTemporalRange(LocalDate date) {
        return !date.isBefore(MINIMUM_TEMPORAL_DATE) && !date.isAfter(MAXIMUM_TEMPORAL_DATE);
    }

    public static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return false;
        }
        char signCharacter = timeZoneId.charAt(0);
        return signCharacter == '+' || signCharacter == '-' || signCharacter == '\u2212';
    }

    public static boolean isValidEpochNanoseconds(BigInteger epochNanoseconds) {
        return epochNanoseconds.compareTo(NS_MIN_INSTANT) >= 0
                && epochNanoseconds.compareTo(NS_MAX_INSTANT) <= 0;
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

    public static BigInteger nanosecondsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigInteger startSeconds = BigInteger.valueOf(startDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger endSeconds = BigInteger.valueOf(endDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger secondDifference = endSeconds.subtract(startSeconds).multiply(TemporalConstants.BI_SECOND_NANOSECONDS);
        long nanosecondDifference = endDateTime.getNano() - startDateTime.getNano();
        return secondDifference.add(BigInteger.valueOf(nanosecondDifference));
    }

    public static int parseOffsetSecondsFromTimeZoneId(String timeZoneId) {
        String normalizedTimeZoneId = timeZoneId.replace('\u2212', '-');
        int sign = normalizedTimeZoneId.charAt(0) == '-' ? -1 : 1;
        int hours;
        int minutes;
        if (normalizedTimeZoneId.length() == 6 && normalizedTimeZoneId.charAt(3) == ':') {
            hours = Integer.parseInt(normalizedTimeZoneId.substring(1, 3));
            minutes = Integer.parseInt(normalizedTimeZoneId.substring(4, 6));
        } else if (normalizedTimeZoneId.length() == 5) {
            hours = Integer.parseInt(normalizedTimeZoneId.substring(1, 3));
            minutes = Integer.parseInt(normalizedTimeZoneId.substring(3, 5));
        } else {
            throw new IllegalArgumentException("Invalid offset time zone identifier: " + timeZoneId);
        }
        return sign * (hours * 3600 + minutes * 60);
    }

    public static JSObject resolveTemporalPrototype(JSContext context, String typeName) {
        JSValue constructorNewTarget = context.getConstructorNewTarget();
        if (constructorNewTarget instanceof JSObject constructorObject) {
            JSValue constructorPrototype = constructorObject.get(PropertyKey.PROTOTYPE);
            if (context.hasPendingException()) {
                return null;
            }
            if (constructorPrototype instanceof JSObject) {
                JSObject resolvedPrototype = context.getPrototypeFromConstructor(constructorObject, JSObject.NAME);
                if (context.hasPendingException()) {
                    return null;
                }
                return resolvedPrototype;
            }
        }
        return getTemporalPrototype(context, typeName);
    }

    public static int roundOffsetSecondsToMinute(int offsetSeconds) {
        int sign = offsetSeconds < 0 ? -1 : 1;
        int absoluteOffsetSeconds = Math.abs(offsetSeconds);
        int absoluteOffsetMinutes = absoluteOffsetSeconds / 60;
        int remainingSeconds = absoluteOffsetSeconds % 60;
        if (remainingSeconds >= 30) {
            absoluteOffsetMinutes++;
        }
        return sign * absoluteOffsetMinutes * 60;
    }

    public static int toArithmeticPersianYear(int persianYear) {
        if (persianYear <= 0) {
            return persianYear - 1;
        }
        return persianYear;
    }

    public static BigInteger toBigIntegerFromIntegralDouble(double value) {
        long bits = Double.doubleToRawLongBits(value);
        boolean negative = (bits & (1L << 63)) != 0;
        int exponentBits = (int) ((bits >>> 52) & 0x7FFL);
        long mantissaBits = bits & ((1L << 52) - 1);

        long significand;
        int shift;
        if (exponentBits == 0) {
            significand = mantissaBits;
            shift = -1074;
        } else {
            significand = (1L << 52) | mantissaBits;
            shift = exponentBits - 1075;
        }

        BigInteger integerValue = BigInteger.valueOf(significand);
        if (shift > 0) {
            integerValue = integerValue.shiftLeft(shift);
        } else if (shift < 0) {
            integerValue = integerValue.shiftRight(-shift);
        }

        if (negative) {
            return integerValue.negate();
        }
        return integerValue;
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

    public static JSObject toOptionalOptionsObject(
            JSContext context,
            JSValue optionsValue,
            String invalidTypeMessage) {
        if (optionsValue instanceof JSUndefined || optionsValue == null) {
            return null;
        }
        if (optionsValue instanceof JSObject optionsObject) {
            return optionsObject;
        } else {
            context.throwTypeError(invalidTypeMessage);
            return null;
        }
    }

}
