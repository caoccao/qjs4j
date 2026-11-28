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
import com.caoccao.qjs4j.core.temporal.calendar.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalEra;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalMonths;
import com.caoccao.qjs4j.core.temporal.duration.TemporalRelativeToOption;
import com.caoccao.qjs4j.core.temporal.format.TemporalDisplayCalendar;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.chrono.HijrahChronology;
import java.time.temporal.ChronoField;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Shared utilities for Temporal types.
 */
public final class TemporalUtils {
    private static final LocalDate MAXIMUM_TEMPORAL_DATE = LocalDate.of(275760, 9, 13);
    private static final LocalDateTime MAXIMUM_TEMPORAL_DATE_TIME = LocalDateTime.of(275760, 9, 13, 23, 59, 59,
            999_999_999);
    private static final LocalDate MINIMUM_TEMPORAL_DATE = LocalDate.of(-271821, 4, 19);
    private static final LocalDateTime MINIMUM_TEMPORAL_DATE_TIME = LocalDateTime.of(-271821, 4, 20, 0, 0, 0, 0);
    private static final BigInteger NS_MAX_INSTANT = new BigInteger("8640000000000000000000");
    private static final BigInteger NS_MIN_INSTANT = new BigInteger("-8640000000000000000000");

    private static final PropertyKey TEMPORAL = PropertyKey.fromString("Temporal");

    private TemporalUtils() {
    }

    public static boolean alexandrianLeapYear(int calendarYear) {
        return Math.floorMod(calendarYear + 1, 4) == 0;
    }

    public static long alexandrianOrdinalDay(int calendarYear, int calendarMonth, int dayOfMonth) {
        long yearValue = calendarYear;
        long monthValue = calendarMonth;
        long dayValue = dayOfMonth;
        return 365L * (yearValue - 1L) + Math.floorDiv(yearValue, 4L) + 30L * (monthValue - 1L) + (dayValue - 1L);
    }

    public static TemporalCalendarId calendarArgument(JSContext context, JSValue[] args, int index) {
        JSValue value = JSValue.argumentOrUndefined(args, index);
        return value instanceof JSUndefined
                ? TemporalCalendarId.ISO8601
                : TemporalCalendarId.createFromCalendarString(context, value);
    }

    /**
     * Generic receiver type check for Temporal prototype methods. Returns the cast value, or null after throwing
     * TypeError.
     */
    @SuppressWarnings("unchecked")
    public static <T> T checkReceiver(JSContext context, JSValue thisArg, Class<T> expectedType, String typeName,
            String methodName) {
        if (!expectedType.isInstance(thisArg)) {
            return context.rejectTypeError(
                    "Method " + typeName + ".prototype." + methodName + " called on incompatible receiver");
        }
        return (T) thisArg;
    }

    public static long epochMilliseconds(BigInteger epochNanoseconds) {
        BigInteger[] parts = epochNanoseconds.divideAndRemainder(TemporalConstants.BI_MILLISECOND_NANOSECONDS);
        long milliseconds = parts[0].longValue();
        return parts[1].signum() < 0 ? milliseconds - 1 : milliseconds;
    }

    /** String conversions parse the input before consulting the overflow option. */
    public static JSValue finishStringConversion(JSContext context, JSValue parsed, JSValue options) {
        return !context.hasPendingException() && validateOverflow(context, options) ? parsed : JSUndefined.INSTANCE;
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

    public static long floorMod(long value, long modulus) {
        long result = value % modulus;
        if (result < 0L) {
            result += modulus;
        }
        return result;
    }

    /**
     * Gets a calendar name display option from options. Returns "auto", "always", "never", or "critical".
     */
    public static String getCalendarNameOption(JSContext context, JSValue options) {
        return getEnumOption(context, options, "calendarName", "auto", TemporalDisplayCalendar::fromString, true);
    }

    public static long getCalendarYearCacheKey(TemporalCalendarId calendarId, int calendarYear) {
        // Explicit identities keep the packed cache key independent of enum declaration order.
        int calendarKey = switch (calendarId) {
            case ISO8601 -> 0;
            case BUDDHIST -> 1;
            case CHINESE -> 2;
            case COPTIC -> 3;
            case DANGI -> 4;
            case ETHIOAA -> 5;
            case ETHIOPIC -> 6;
            case GREGORY -> 7;
            case HEBREW -> 8;
            case INDIAN -> 9;
            case ISLAMIC_CIVIL -> 10;
            case ISLAMIC_TBLA -> 11;
            case ISLAMIC_UMALQURA -> 12;
            case JAPANESE -> 13;
            case PERSIAN -> 14;
            case ROC -> 15;
        };
        long normalizedYear = (long) calendarYear - Integer.MIN_VALUE;
        return ((long) calendarKey << 32) | (normalizedYear & 0xFFFF_FFFFL);
    }

    /** Read an optional string-valued Temporal option and validate its canonical spelling. */
    public static String getEnumOption(JSContext context, JSValue options, String name, String fallback,
            Function<String, ?> parse) {
        return getEnumOption(context, options, name, fallback, parse, false);
    }

    private static String getEnumOption(JSContext context, JSValue options, String name, String fallback,
            Function<String, ?> parse, boolean includeInvalidValue) {
        if (JSTypeChecking.isUndefinedOrMissing(options)) {
            return fallback;
        }
        if (!(options instanceof JSObject object)) {
            return TemporalError.INVALID_OPTIONS.reject(context);
        }
        String value = getStringOption(context, object, name, fallback);
        if (context.hasPendingException() || (value == null && !includeInvalidValue)) {
            return null;
        }
        if (parse.apply(value) == null) {
            return context.rejectRangeError(
                    "Temporal error: Invalid " + name + " option" + (includeInvalidValue ? ": " + value : "."));
        }
        return value;
    }

    /**
     * Gets an integer field from a JSObject, returning the default if undefined.
     */
    public static int getIntegerField(JSContext context, JSObject sourceObject, String optionKey, int defaultValue) {
        JSValue value = sourceObject.get(PropertyKey.fromString(optionKey));
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (JSTypeChecking.isUndefinedOrMissing(value)) {
            return defaultValue;
        }
        return toIntegerThrowOnInfinity(context, value);
    }

    public static Integer getOptionalIntegerField(JSContext context, JSObject fields, String name) {
        return getOptionalIntegerFieldByKey(context, fields, PropertyKey.fromString(name));
    }

    public static Integer getOptionalIntegerFieldByKey(JSContext context, JSObject fields, PropertyKey name) {
        JSValue value = fields.get(name);
        if (context.hasPendingException() || JSTypeChecking.isUndefinedOrMissing(value)) {
            return null;
        }
        int integer = toIntegerThrowOnInfinity(context, value);
        return context.hasPendingException() ? null : integer;
    }

    /**
     * Gets the overflow option from an options object. Returns "constrain" or "reject".
     */
    public static String getOverflowOption(JSContext context, JSValue options) {
        return getEnumOption(context, options, "overflow", "constrain", TemporalOverflow::fromString, true);
    }

    public static long getRoundingIncrementOption(JSContext context, JSObject optionsObject,
            String invalidOptionMessage) {
        long integerOptionValue = readRoundingIncrement(context, optionsObject, invalidOptionMessage);
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (integerOptionValue < 1L || integerOptionValue > TemporalConstants.MAX_ROUNDING_INCREMENT) {
            context.throwRangeError(invalidOptionMessage);
            return Long.MIN_VALUE;
        }
        return integerOptionValue;
    }

    /**
     * Gets a string option from an options object.
     */
    public static String getStringOption(JSContext context, JSObject options, String optionKey, String defaultValue) {
        return getStringOptionByKey(context, options, PropertyKey.fromString(optionKey), defaultValue);
    }

    public static String getStringOptionByKey(JSContext context, JSObject options, PropertyKey optionKey,
            String defaultValue) {
        JSValue value = options.get(optionKey);
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeChecking.isUndefinedOrMissing(value)) {
            return defaultValue;
        }
        JSString stringValue = JSTypeConversions.toString(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return stringValue.value();
    }

    public static JSObject getTemporalPrototype(JSContext context, String typeName) {
        return getTemporalPrototypeByKey(context, PropertyKey.fromString(typeName));
    }

    /** Look up the constructor and prototype in the active realm, including any accessor calls. */
    public static JSObject getTemporalPrototypeByKey(JSContext context, PropertyKey type) {
        JSValue temporal = context.getGlobalObject().get(TEMPORAL);
        if (temporal instanceof JSObject temporalObject) {
            JSValue constructor = temporalObject.get(type);
            if (constructor instanceof JSObject constructorObject) {
                JSValue prototype = constructorObject.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject prototypeObject) {
                    return prototypeObject;
                }
            }
        }
        return null;
    }

    public static long hebrewElapsedDays(long hebrewYear) {
        long monthsElapsed = Math.floorDiv(235L * hebrewYear - 234L, 19L);
        long partsElapsed = 204L + 793L * floorMod(monthsElapsed, 1080L);
        long hoursElapsed = 5L + 12L * monthsElapsed + 793L * Math.floorDiv(monthsElapsed, 1080L)
                + Math.floorDiv(partsElapsed, 1080L);
        long conjunctionParts = 1080L * floorMod(hoursElapsed, 24L) + floorMod(partsElapsed, 1080L);
        long dayNumber = 1L + 29L * monthsElapsed + Math.floorDiv(hoursElapsed, 24L);

        boolean shouldPostpone = conjunctionParts >= 19_440L
                || (!isHebrewLeapYear(hebrewYear) && floorMod(dayNumber, 7L) == 2L && conjunctionParts >= 9_924L)
                || (isHebrewLeapYear(hebrewYear - 1L) && floorMod(dayNumber, 7L) == 1L && conjunctionParts >= 16_789L);
        if (shouldPostpone) {
            dayNumber++;
        }

        long weekDay = floorMod(dayNumber, 7L);
        if (weekDay == 0L || weekDay == 3L || weekDay == 5L) {
            dayNumber++;
        }
        return dayNumber;
    }

    public static boolean isDateTimeWithinTemporalRange(LocalDateTime dateTime) {
        return !dateTime.isBefore(MINIMUM_TEMPORAL_DATE_TIME) && !dateTime.isAfter(MAXIMUM_TEMPORAL_DATE_TIME);
    }

    public static boolean isDateWithinTemporalRange(LocalDate date) {
        return !date.isBefore(MINIMUM_TEMPORAL_DATE) && !date.isAfter(MAXIMUM_TEMPORAL_DATE);
    }

    public static boolean isHebrewLeapYear(long hebrewYear) {
        return floorMod(7L * hebrewYear + 1L, 19L) < 7L;
    }

    public static long isHebrewYearLength(long hebrewYear) {
        return hebrewElapsedDays(hebrewYear + 1L) - hebrewElapsedDays(hebrewYear);
    }

    public static int islamicDaysBeforeYear(int islamicYear) {
        return (int) (354L * (islamicYear - 1L) + Math.floorDiv(11L * islamicYear + 3L, 30L));
    }

    public static int islamicDaysInMonth(int islamicYear, int islamicMonth) {
        if (islamicMonth < 1 || islamicMonth > 12) {
            return 0;
        }
        if (islamicMonth == 12) {
            long yearLength = islamicDaysBeforeYear(islamicYear + 1) - islamicDaysBeforeYear(islamicYear);
            return (int) (yearLength - 325L);
        }
        return islamicMonth % 2 == 1 ? 30 : 29;
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

    /**
     * Returns true if firstDate surpasses secondDate in the direction indicated by sign. Positive sign means firstDate
     * is later; negative sign means firstDate is earlier.
     */
    public static boolean isoDateSurpasses(int sign, IsoDate firstDate, IsoDate secondDate) {
        return sign * firstDate.compareTo(secondDate) > 0;
    }

    public static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return false;
        }
        char signCharacter = timeZoneId.charAt(0);
        return signCharacter == '+' || signCharacter == '-' || signCharacter == '\u2212';
    }

    public static boolean isUnsupportedUmalquraYear(int islamicYear) {
        return !HijrahChronology.INSTANCE.range(ChronoField.YEAR).isValidValue(islamicYear);
    }

    public static boolean isValidEpochNanoseconds(BigInteger epochNanoseconds) {
        return epochNanoseconds.compareTo(NS_MIN_INSTANT) >= 0 && epochNanoseconds.compareTo(NS_MAX_INSTANT) <= 0;
    }

    /**
     * Appends calendar annotation to string if needed.
     */
    public static String maybeAppendCalendar(String dateTimeString, TemporalCalendarId calendarId,
            String calendarNameOption) {
        switch (calendarNameOption) {
            case "never" :
                return dateTimeString;
            case "always" :
                return dateTimeString + "[u-ca=" + calendarId.identifier() + "]";
            case "critical" :
                return dateTimeString + "[!u-ca=" + calendarId.identifier() + "]";
            case "auto" :
            default :
                if (calendarId == TemporalCalendarId.ISO8601) {
                    return dateTimeString;
                }
                return dateTimeString + "[u-ca=" + calendarId.identifier() + "]";
        }
    }

    public static int monthsInYear(IsoDate isoDate, TemporalCalendarId calendarId) {
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        return TemporalMonths.get(calendarId, calendarDateFields.year()).size();
    }

    public static BigInteger nanosecondsBetween(JSContext context, LocalDateTime startDateTime,
            BigInteger knownStartEpochNanoseconds, LocalDateTime endDateTime, BigInteger knownEndEpochNanoseconds,
            TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return nanosecondsBetween(startDateTime, endDateTime);
        }
        BigInteger start = relativeToOption.epochNanosecondsAt(context, startDateTime, knownStartEpochNanoseconds);
        if (context.hasPendingException() || start == null) {
            return BigInteger.ZERO;
        }
        BigInteger end = relativeToOption.epochNanosecondsAt(context, endDateTime, knownEndEpochNanoseconds);
        return context.hasPendingException() || end == null ? BigInteger.ZERO : end.subtract(start);
    }

    public static BigInteger nanosecondsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigInteger startSeconds = BigInteger.valueOf(startDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger endSeconds = BigInteger.valueOf(endDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger secondDifference = endSeconds.subtract(startSeconds)
                .multiply(TemporalConstants.BI_SECOND_NANOSECONDS);
        long nanosecondDifference = endDateTime.getNano() - startDateTime.getNano();
        return secondDifference.add(BigInteger.valueOf(nanosecondDifference));
    }

    /** Read an optional integer component of an ISO reference date. */
    public static int optionalIntegerArgument(JSContext context, JSValue[] args, int index, int defaultValue) {
        return index >= args.length || args[index] instanceof JSUndefined
                ? defaultValue
                : toIntegerThrowOnInfinity(context, args[index]);
    }

    /** An options bag whose overflow value has already been validated. */
    public static JSObject overflowOptions(JSContext context, String overflow) {
        JSObject options = new JSObject(context);
        options.set(PropertyKey.fromString("overflow"), new JSString(overflow));
        return options;
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
            throw new JSRangeErrorException("Invalid offset time zone identifier: " + timeZoneId);
        }
        return sign * (hours * 3600 + minutes * 60);
    }

    public static <Key, Value> void putBoundedMapEntry(ConcurrentHashMap<Key, Value> cache, Queue<Key> evictionQueue,
            Key key, Value value, int maximumSize) {
        Value previousValue = cache.put(key, value);
        if (previousValue == null) {
            evictionQueue.offer(key);
        }
        while (cache.size() > maximumSize) {
            Key oldestKey = evictionQueue.poll();
            if (oldestKey == null) {
                break;
            }
            cache.remove(oldestKey);
        }
    }

    public static <Value> void putBoundedSetEntry(Set<Value> cache, Queue<Value> evictionQueue, Value value,
            int maximumSize) {
        boolean inserted = cache.add(value);
        if (inserted) {
            evictionQueue.offer(value);
        }
        while (cache.size() > maximumSize) {
            Value oldestValue = evictionQueue.poll();
            if (oldestValue == null) {
                break;
            }
            cache.remove(oldestValue);
        }
    }

    /** Coerce roundingIncrement, leaving the unit-specific bounds check to the caller. */
    public static long readRoundingIncrement(JSContext context, JSObject optionsObject, String invalidOptionMessage) {
        JSValue optionValue = optionsObject.get(PropertyKey.ROUNDING_INCREMENT);
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (JSTypeChecking.isUndefinedOrMissing(optionValue)) {
            return 1L;
        }

        JSNumber optionNumber = JSTypeConversions.toNumber(context, optionValue);
        if (context.hasPendingException() || optionNumber == null) {
            return Long.MIN_VALUE;
        }

        double numericOptionValue = optionNumber.value();
        if (!Double.isFinite(numericOptionValue)) {
            context.throwRangeError(invalidOptionMessage);
            return Long.MIN_VALUE;
        }

        return (long) numericOptionValue;
    }

    public static TemporalCalendarId requireCalendarArgument(JSContext context, JSValue[] args) {
        JSValue calendar = JSValue.argumentOrUndefined(args, 0);
        if (JSTypeChecking.isUndefinedOrMissing(calendar)) {
            return context.rejectTypeError("Temporal error: Calendar is required.");
        }
        return TemporalCalendarId.createFromCalendarValue(context, calendar);
    }

    public static boolean requireNewTarget(JSContext context, String type) {
        if (context.getConstructorNewTarget() != null) {
            return true;
        }
        context.throwTypeError("Method invoked on an object that is not Temporal." + type + ".");
        return false;
    }

    private static JSObject requirePartialTemporalObject(JSContext context, JSObject fields, String errorMessage) {
        if (fields instanceof JSTemporalPlainDate || fields instanceof JSTemporalPlainDateTime
                || fields instanceof JSTemporalPlainMonthDay || fields instanceof JSTemporalPlainTime
                || fields instanceof JSTemporalPlainYearMonth || fields instanceof JSTemporalZonedDateTime) {
            return context.rejectTypeError(errorMessage);
        }
        JSValue calendar = fields.get(PropertyKey.CALENDAR);
        if (context.hasPendingException()) {
            return null;
        }
        if (calendar != null && !(calendar instanceof JSUndefined)) {
            return context.rejectTypeError(errorMessage);
        }
        JSValue timeZone = fields.get(PropertyKey.TIME_ZONE);
        if (context.hasPendingException()) {
            return null;
        }
        if (timeZone != null && !(timeZone instanceof JSUndefined)) {
            return context.rejectTypeError(errorMessage);
        }
        return fields;
    }

    /** Validate the partial object for a plain date or time's with() method, in observable property-read order. */
    public static JSObject requirePartialTemporalObject(JSContext context, JSValue[] args, String errorMessage) {
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            return context.rejectTypeError(errorMessage);
        }
        return requirePartialTemporalObject(context, fields, errorMessage);
    }

    public static JSObject requirePartialZonedDateTime(JSContext context, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            return TemporalError.MISSING_PARTIAL_FIELDS.reject(context);
        }
        String invalid = "Temporal error: Invalid ZonedDateTime-like object.";
        if (fields instanceof JSTemporalInstant || fields instanceof JSTemporalDuration) {
            return context.rejectTypeError(invalid);
        }
        return requirePartialTemporalObject(context, fields, invalid);
    }

    /** Resolve the optional year and era fields after reading the calendar's other date fields. */
    public static Integer resolveEraYear(JSContext context, JSObject fields, TemporalCalendarId calendar, Integer year,
            String missingEraMessage) {
        if (!calendar.hasEra()) {
            return year;
        }
        String era = getStringOption(context, fields, "era", null);
        if (context.hasPendingException()) {
            return null;
        }
        Integer eraYear = getOptionalIntegerField(context, fields, "eraYear");
        if (context.hasPendingException()) {
            return null;
        }
        return resolveEraYear(context, calendar, year, era, eraYear, missingEraMessage);
    }

    public static Integer resolveEraYear(JSContext context, TemporalCalendarId calendar, Integer year, String era,
            Integer eraYear, String missingEraMessage) {
        if ((era == null) != (eraYear == null)) {
            return context.rejectTypeError(missingEraMessage);
        }
        if (era == null) {
            return year;
        }
        TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendar, era);
        if (context.hasPendingException()) {
            return null;
        }
        int expectedYear = calendar.getEraYearFromEra(canonicalEra, eraYear);
        if (year != null && year != expectedYear) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        return expectedYear;
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
     * Converts a JSValue to a finite integer or throws a RangeError. Returns Integer.MIN_VALUE if a pending exception
     * was set.
     */
    public static int toIntegerThrowOnInfinity(JSContext context, JSValue value) {
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            TemporalError.EXPECTED_FINITE_INTEGER.raise(context);
            return Integer.MIN_VALUE;
        }
        return (int) numericValue;
    }

    /**
     * Converts a JSValue to a finite integral long or throws a RangeError. Unlike toIntegerThrowOnInfinity, this
     * rejects fractional values. Returns Long.MIN_VALUE if a pending exception was set.
     */
    public static long toLongIfIntegral(JSContext context, JSValue value) {
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            TemporalError.EXPECTED_FINITE_INTEGER.raise(context);
            return Long.MIN_VALUE;
        }
        if (numericValue != Math.floor(numericValue)) {
            TemporalError.EXPECTED_FINITE_INTEGER.raise(context);
            return Long.MIN_VALUE;
        }
        return (long) numericValue;
    }

    public static JSObject toOptionalOptionsObject(JSContext context, JSValue optionsValue, String invalidTypeMessage) {
        if (JSTypeChecking.isUndefinedOrMissing(optionsValue)) {
            return null;
        }
        if (optionsValue instanceof JSObject optionsObject) {
            return optionsObject;
        } else {
            return context.rejectTypeError(invalidTypeMessage);
        }
    }

    /** Reading overflow is observable even when there is nothing to constrain. */
    public static boolean validateOverflow(JSContext context, JSValue options) {
        getOverflowOption(context, options);
        return !context.hasPendingException();
    }

    /** Finish creating an intrinsic with a prototype supplied by its constructor or conversion. */
    public static <T extends JSObject> T withPrototype(T object, JSObject prototype) {
        if (prototype != null) {
            object.setPrototype(prototype);
        }
        return object;
    }

}
