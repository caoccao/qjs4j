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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.*;
import com.caoccao.qjs4j.exceptions.JSErrorException;

import java.math.BigInteger;
import java.time.*;
import java.util.List;

/**
 * Implementation of Temporal.ZonedDateTime constructor and static methods.
 */
public final class TemporalZonedDateTimeConstructor {

    private TemporalZonedDateTimeConstructor() {
    }

    /**
     * Temporal.ZonedDateTime.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalZonedDateTime firstZonedDateTime = toTemporalZonedDateTimeObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalZonedDateTime secondZonedDateTime = toTemporalZonedDateTimeObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(firstZonedDateTime.getEpochNanoseconds().compareTo(secondZonedDateTime.getEpochNanoseconds()));
    }

    private static BigInteger computeWallEpochNanoseconds(
            JSContext context,
            IsoDate isoDate,
            IsoTime isoTime,
            String timeZoneId,
            String disambiguation) {
        if (!isZonedDateTimeWithinRange(isoDate, isoTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        try {
            return TemporalTimeZone.localDateTimeToEpochNs(new IsoDateTime(isoDate, isoTime), timeZoneId, disambiguation);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
    }

    /**
     * Temporal.ZonedDateTime(epochNanoseconds, timeZone, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.ZonedDateTime.");
            return JSUndefined.INSTANCE;
        }

        JSValue epochNsArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSBigInt epochNanosecondsBigInt;
        try {
            epochNanosecondsBigInt = JSTypeConversions.toBigInt(context, epochNsArg);
        } catch (JSErrorException conversionException) {
            return context.throwError(conversionException);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = epochNanosecondsBigInt.value();
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }

        if (args.length < 2 || args[1] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        if (!(args[1] instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        if (timeZoneString.value().indexOf('[') >= 0) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneString.value());
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = normalizeTimeZoneIdentifier(context, timeZoneString.value());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        if (!isOffsetTimeZoneIdentifier(timeZoneId)) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (DateTimeException invalidTimeZoneException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return JSUndefined.INSTANCE;
            }
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "ZonedDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createZonedDateTime(context, epochNs, timeZoneId, calendarId, resolvedPrototype);
    }

    private static String convertMonthCodeToString(JSContext context, JSValue monthCodeValue) {
        if (monthCodeValue instanceof JSString monthCodeString) {
            return monthCodeString.value();
        }
        if (monthCodeValue instanceof JSObject) {
            JSValue primitiveMonthCode =
                    JSTypeConversions.toPrimitive(context, monthCodeValue, JSTypeConversions.PreferredType.STRING);
            if (context.hasPendingException()) {
                return null;
            }
            if (!(primitiveMonthCode instanceof JSString primitiveMonthCodeString)) {
                context.throwTypeError("Temporal error: Month code must be string.");
                return null;
            }
            return primitiveMonthCodeString.value();
        }

        context.throwTypeError("Temporal error: Month code must be string.");
        return null;
    }

    public static JSTemporalZonedDateTime createZonedDateTime(JSContext context, BigInteger epochNs,
                                                              String timeZoneId, TemporalCalendarId calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "ZonedDateTime");
        return createZonedDateTime(context, epochNs, timeZoneId, calendarId, prototype);
    }

    static JSTemporalZonedDateTime createZonedDateTime(JSContext context, BigInteger epochNs,
                                                       String timeZoneId, TemporalCalendarId calendarId, JSObject prototype) {
        JSTemporalZonedDateTime zonedDateTime = new JSTemporalZonedDateTime(context, epochNs, timeZoneId, calendarId);
        if (prototype != null) {
            zonedDateTime.setPrototype(prototype);
        }
        return zonedDateTime;
    }

    private static JSValue createZonedDateTimeFromPropertyBag(
            JSContext context,
            TemporalZonedDateTimePropertyBagData propertyBagData,
            TemporalZonedDateTimeOptions options) {
        Integer monthFromProperty = propertyBagData.month();
        IsoMonth parsedMonthCode = propertyBagData.parsedMonthCode();
        if (monthFromProperty == null && parsedMonthCode == null) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String monthCodeFromProperty = null;
        if (parsedMonthCode != null) {
            monthCodeFromProperty = IsoMonth.toMonthCode(parsedMonthCode.month());
            if (parsedMonthCode.leapMonth()) {
                monthCodeFromProperty += "L";
            }
        }

        int calendarYear = propertyBagData.year();
        int dayOfMonth = propertyBagData.day();
        int hour = propertyBagData.hour();
        int minute = propertyBagData.minute();
        int second = propertyBagData.second();
        if (second == 60) {
            second = 59;
        }
        int millisecond = propertyBagData.millisecond();
        int microsecond = propertyBagData.microsecond();
        int nanosecond = propertyBagData.nanosecond();

        IsoDate isoDate = TemporalCalendarMath.calendarDateToIsoDate(
                context,
                propertyBagData.calendarId(),
                calendarYear,
                monthFromProperty,
                monthCodeFromProperty,
                dayOfMonth,
                options.overflow());
        if (context.hasPendingException() || isoDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime isoTime;
        if ("reject".equals(options.overflow())) {
            isoTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            if (!isoTime.isValid()) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
        } else {
            isoTime = IsoTime.createNormalized(hour, minute, second, millisecond, microsecond, nanosecond);
        }

        String timeZoneId = propertyBagData.timeZoneId();
        BigInteger epochNanoseconds = interpretOffset(
                context,
                isoDate,
                isoTime,
                timeZoneId,
                propertyBagData.offsetSeconds(),
                propertyBagData.offsetSeconds() != null,
                false,
                false,
                false,
                options);
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return createZonedDateTime(context, epochNanoseconds, timeZoneId, propertyBagData.calendarId());
    }

    private static JSValue createZonedDateTimeFromString(
            JSContext context,
            String input,
            IsoZonedDateTimeOffset parsed,
            TemporalZonedDateTimeOptions options) {
        String timeZoneId = normalizeTimeZoneIdentifier(context, parsed.timeZoneId());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }

        if (!isOffsetTimeZoneIdentifier(timeZoneId)) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (DateTimeException invalidTimeZoneException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return JSUndefined.INSTANCE;
            }
        }

        boolean hasExplicitOffset = hasOffsetDesignator(input);
        boolean hasZuluOffset = hasZuluDesignator(input);
        boolean hasTimeComponent = findDateTimeSeparatorIndex(input) >= 0;
        if (!hasTimeComponent && !hasExplicitOffset) {
            BigInteger epochNanoseconds;
            try {
                epochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(parsed.date(), timeZoneId);
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return JSUndefined.INSTANCE;
            }
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
            return createZonedDateTime(context, epochNanoseconds, timeZoneId, parsed.calendarId());
        }
        boolean offsetIncludesSecondsOrFraction = false;
        if (hasExplicitOffset && !hasZuluOffset) {
            String offsetText = extractOffsetText(input);
            if (offsetText != null) {
                offsetIncludesSecondsOrFraction = offsetTextIncludesSecondsOrFraction(offsetText);
            }
        }

        BigInteger epochNanoseconds = interpretOffset(
                context,
                parsed.date(),
                parsed.time(),
                timeZoneId,
                parsed.offsetSeconds(),
                hasExplicitOffset,
                hasZuluOffset,
                true,
                offsetIncludesSecondsOrFraction,
                options);
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return createZonedDateTime(context, epochNanoseconds, timeZoneId, parsed.calendarId());
    }

    private static String extractOffsetText(String text) {
        int timeSeparatorIndex = findDateTimeSeparatorIndex(text);
        if (timeSeparatorIndex < 0) {
            return null;
        }
        int offsetStart = -1;
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[' || character == 'Z' || character == 'z') {
                break;
            }
            if (character == '+' || character == '-' || character == '\u2212') {
                offsetStart = index;
                break;
            }
        }
        if (offsetStart < 0) {
            return null;
        }
        int offsetEnd = text.indexOf('[', offsetStart);
        if (offsetEnd < 0) {
            offsetEnd = text.length();
        }
        return text.substring(offsetStart, offsetEnd);
    }

    private static int findDateTimeSeparatorIndex(String text) {
        int annotationStart = text.indexOf('[');
        int searchEnd = annotationStart >= 0 ? annotationStart : text.length();
        for (int index = 0; index < searchEnd; index++) {
            char character = text.charAt(index);
            if (character == 'T' || character == 't') {
                return index;
            }
        }
        return -1;
    }

    private static BigInteger findMatchingEpochNanosecondsForExplicitOffset(
            JSContext context,
            IsoDate isoDate,
            IsoTime isoTime,
            String timeZoneId,
            int explicitOffsetSeconds,
            boolean offsetTimeZoneIdentifier,
            boolean allowMinuteRounding) {
        if (offsetTimeZoneIdentifier) {
            int timeZoneOffsetSeconds = parseOffsetSeconds(timeZoneId);
            boolean offsetMatches = allowMinuteRounding
                    ? roundOffsetSecondsToMinute(timeZoneOffsetSeconds) == explicitOffsetSeconds
                    : timeZoneOffsetSeconds == explicitOffsetSeconds;
            if (offsetMatches) {
                return TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, timeZoneOffsetSeconds);
            } else {
                return null;
            }
        }

        try {
            return selectMatchingEpochNanosecondsForExplicitOffset(
                    isoDate,
                    isoTime,
                    timeZoneId,
                    explicitOffsetSeconds,
                    allowMinuteRounding);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return null;
        }
    }

    /**
     * Temporal.ZonedDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalZonedDateTimeOptions options = parseFromOptions(context, optionsArg);
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }
            return createZonedDateTime(
                    context,
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId(),
                    zonedDateTime.getCalendarId());
        }

        if (item instanceof JSObject itemObject) {
            TemporalZonedDateTimePropertyBagData propertyBagData = parseZonedDateTimePropertyBag(context, itemObject);
            if (context.hasPendingException() || propertyBagData == null) {
                return JSUndefined.INSTANCE;
            }

            TemporalZonedDateTimeOptions options = parseFromOptions(context, optionsArg);
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }

            return createZonedDateTimeFromPropertyBag(context, propertyBagData, options);
        }

        if (item instanceof JSString zonedDateTimeString) {
            IsoZonedDateTimeOffset parsed =
                    TemporalParser.parseZonedDateTimeString(context, zonedDateTimeString.value());
            if (context.hasPendingException() || parsed == null) {
                return JSUndefined.INSTANCE;
            }

            TemporalZonedDateTimeOptions options = parseFromOptions(context, optionsArg);
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }

            return createZonedDateTimeFromString(context, zonedDateTimeString.value(), parsed, options);
        }

        context.throwTypeError("Temporal error: DateTime argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    private static boolean hasOffsetDesignator(String text) {
        int timeSeparatorIndex = findDateTimeSeparatorIndex(text);
        if (timeSeparatorIndex < 0) {
            return false;
        }
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[') {
                break;
            }
            if (character == 'Z' || character == 'z' || character == '+' || character == '-' || character == '\u2212') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasZuluDesignator(String text) {
        int timeSeparatorIndex = findDateTimeSeparatorIndex(text);
        if (timeSeparatorIndex < 0) {
            return false;
        }
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[') {
                break;
            }
            if (character == 'Z' || character == 'z') {
                return true;
            }
        }
        return false;
    }

    private static BigInteger interpretOffset(
            JSContext context,
            IsoDate isoDate,
            IsoTime isoTime,
            String timeZoneId,
            Integer explicitOffsetSeconds,
            boolean hasExplicitOffset,
            boolean hasZuluOffset,
            boolean stringInput,
            boolean stringOffsetIncludesSecondsOrFraction,
            TemporalZonedDateTimeOptions options) {
        boolean offsetTimeZoneIdentifier = isOffsetTimeZoneIdentifier(timeZoneId);

        BigInteger epochNanoseconds;
        if (!hasExplicitOffset) {
            if (offsetTimeZoneIdentifier) {
                int timeZoneOffsetSeconds = parseOffsetSeconds(timeZoneId);
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, timeZoneOffsetSeconds);
            } else {
                epochNanoseconds = computeWallEpochNanoseconds(
                        context,
                        isoDate,
                        isoTime,
                        timeZoneId,
                        options.disambiguation());
                if (context.hasPendingException() || epochNanoseconds == null) {
                    return null;
                }
            }
        } else if (hasZuluOffset) {
            epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, 0);
        } else {
            int explicitOffsetSecondsValue = explicitOffsetSeconds != null ? explicitOffsetSeconds : 0;
            String offsetOption = options.offset();
            if ("use".equals(offsetOption)) {
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, explicitOffsetSecondsValue);
            } else {
                if ("ignore".equals(offsetOption)) {
                    epochNanoseconds = resolveEpochNanosecondsIgnoringOffset(
                            context,
                            isoDate,
                            isoTime,
                            timeZoneId,
                            offsetTimeZoneIdentifier,
                            options.disambiguation());
                    if (context.hasPendingException() || epochNanoseconds == null) {
                        return null;
                    }
                } else {
                    if (!isZonedDateTimeWithinRange(isoDate, isoTime)) {
                        context.throwRangeError("Temporal error: Invalid ISO date.");
                        return null;
                    }
                    boolean allowMinuteRounding = stringInput && !stringOffsetIncludesSecondsOrFraction;
                    BigInteger matchingEpochNanoseconds = findMatchingEpochNanosecondsForExplicitOffset(
                            context,
                            isoDate,
                            isoTime,
                            timeZoneId,
                            explicitOffsetSecondsValue,
                            offsetTimeZoneIdentifier,
                            allowMinuteRounding);
                    if (context.hasPendingException()) {
                        return null;
                    }
                    if (matchingEpochNanoseconds != null) {
                        epochNanoseconds = matchingEpochNanoseconds;
                    } else if ("reject".equals(offsetOption)) {
                        context.throwRangeError("Temporal error: Invalid offset.");
                        return null;
                    } else {
                        epochNanoseconds = resolveEpochNanosecondsIgnoringOffset(
                                context,
                                isoDate,
                                isoTime,
                                timeZoneId,
                                offsetTimeZoneIdentifier,
                                options.disambiguation());
                        if (context.hasPendingException() || epochNanoseconds == null) {
                            return null;
                        }
                    }
                }
            }
        }

        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }

        return epochNanoseconds;
    }

    private static boolean isMinutePrecisionOffsetIdentifier(String text) {
        TemporalOffsetParts offsetParts = TemporalTimeZone.parseOffsetParts(text);
        if (offsetParts == null) {
            return false;
        }
        if (offsetParts.secondsText() != null || offsetParts.fractionText() != null) {
            return false;
        }
        return offsetParts.hours() <= 23 && offsetParts.minutes() <= 59;
    }

    static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.length() < 3) {
            return false;
        }
        char signCharacter = timeZoneId.charAt(0);
        if (signCharacter != '+' && signCharacter != '-' && signCharacter != '\u2212') {
            return false;
        }

        int hours;
        int minutes;
        if (timeZoneId.length() == 6 && timeZoneId.charAt(3) == ':') {
            if (!Character.isDigit(timeZoneId.charAt(1))
                    || !Character.isDigit(timeZoneId.charAt(2))
                    || !Character.isDigit(timeZoneId.charAt(4))
                    || !Character.isDigit(timeZoneId.charAt(5))) {
                return false;
            }
            hours = Integer.parseInt(timeZoneId.substring(1, 3));
            minutes = Integer.parseInt(timeZoneId.substring(4, 6));
        } else if (timeZoneId.length() == 5) {
            if (!Character.isDigit(timeZoneId.charAt(1))
                    || !Character.isDigit(timeZoneId.charAt(2))
                    || !Character.isDigit(timeZoneId.charAt(3))
                    || !Character.isDigit(timeZoneId.charAt(4))) {
                return false;
            }
            hours = Integer.parseInt(timeZoneId.substring(1, 3));
            minutes = Integer.parseInt(timeZoneId.substring(3, 5));
        } else {
            return false;
        }

        return hours <= 23 && minutes <= 59;
    }

    private static boolean isValidOffsetString(String offsetText) {
        TemporalOffsetParts offsetParts = TemporalTimeZone.parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }

        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        String secondsText = offsetParts.secondsText();
        String fractionText = offsetParts.fractionText();
        if (hours > 23 || minutes > 59) {
            return false;
        }

        if (secondsText != null) {
            int seconds = Integer.parseInt(secondsText);
            if (seconds != 0) {
                return false;
            }
        }

        if (fractionText != null) {
            for (int index = 0; index < fractionText.length(); index++) {
                if (fractionText.charAt(index) != '0') {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isZonedDateTimeWithinRange(IsoDate isoDate, IsoTime isoTime) {
        int second = isoTime.second();
        if (second == 60) {
            second = 59;
        }
        LocalDateTime localDateTime = LocalDateTime.of(
                isoDate.year(),
                isoDate.month(),
                isoDate.day(),
                isoTime.hour(),
                isoTime.minute(),
                second,
                isoTime.millisecond() * 1_000_000
                        + isoTime.microsecond() * 1_000
                        + isoTime.nanosecond());
        LocalDateTime minimumDateTime = LocalDateTime.of(-271821, 4, 20, 0, 0, 0, 0);
        LocalDateTime maximumDateTime = LocalDateTime.of(275760, 9, 13, 23, 59, 59, 999_999_999);
        return !localDateTime.isBefore(minimumDateTime) && !localDateTime.isAfter(maximumDateTime);
    }

    static String normalizeTimeZoneIdentifier(JSContext context, String timeZoneText) {
        String normalizedTimeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneText);
        if (context.hasPendingException() || normalizedTimeZoneId == null) {
            return null;
        }

        if (isMinutePrecisionOffsetIdentifier(timeZoneText)) {
            return TemporalTimeZone.formatOffset(parseOffsetSeconds(timeZoneText));
        }
        if (isMinutePrecisionOffsetIdentifier(normalizedTimeZoneId)) {
            return TemporalTimeZone.formatOffset(parseOffsetSeconds(normalizedTimeZoneId));
        }
        return normalizedTimeZoneId;
    }

    private static boolean offsetTextIncludesSecondsOrFraction(String offsetText) {
        TemporalOffsetParts offsetParts = TemporalTimeZone.parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }
        return offsetParts.secondsText() != null || offsetParts.fractionText() != null;
    }

    private static TemporalZonedDateTimeOptions parseFromOptions(JSContext context, JSValue optionsValue) {
        if (optionsValue instanceof JSUndefined || optionsValue == null) {
            return new TemporalZonedDateTimeOptions("compatible", "reject", "constrain");
        }

        if (!(optionsValue instanceof JSObject optionsObject)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }

        String disambiguation = TemporalUtils.getStringOption(context, optionsObject, "disambiguation", "compatible");
        if (context.hasPendingException() || disambiguation == null) {
            return null;
        }
        if (!TemporalDisambiguation.isValid(disambiguation)) {
            context.throwRangeError("Temporal error: Invalid disambiguation option.");
            return null;
        }

        String offset = TemporalUtils.getStringOption(context, optionsObject, "offset", "reject");
        if (context.hasPendingException() || offset == null) {
            return null;
        }
        if (!TemporalOffsetOption.isValid(offset)) {
            context.throwRangeError("Temporal error: Invalid offset option.");
            return null;
        }

        String overflow = TemporalUtils.getStringOption(context, optionsObject, "overflow", "constrain");
        if (context.hasPendingException() || overflow == null) {
            return null;
        }
        if (!TemporalOverflow.isValid(overflow)) {
            context.throwRangeError("Temporal error: Invalid overflow option.");
            return null;
        }

        return new TemporalZonedDateTimeOptions(disambiguation, offset, overflow);
    }

    private static int parseOffsetSeconds(String offsetText) {
        TemporalOffsetParts offsetParts = TemporalTimeZone.parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return 0;
        }
        String signText = offsetParts.signText();
        int sign = ("-".equals(signText) || "\u2212".equals(signText)) ? -1 : 1;
        return sign * (offsetParts.hours() * 3600 + offsetParts.minutes() * 60);
    }

    private static TemporalZonedDateTimePropertyBagData parseZonedDateTimePropertyBag(JSContext context, JSObject itemObject) {
        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        JSValue calendarValue = itemObject.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return null;
        }
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
            if (context.hasPendingException() || calendarId == null) {
                return null;
            }
        }

        JSValue dayValue = itemObject.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return null;
        }
        Long dayOfMonth = toRequiredIntegralLong(context, dayValue);
        if (context.hasPendingException() || dayOfMonth == null) {
            return null;
        }

        int hour = toOptionalIntegralInt(context, itemObject, "hour", 0);
        if (context.hasPendingException()) {
            return null;
        }
        int microsecond = toOptionalIntegralInt(context, itemObject, "microsecond", 0);
        if (context.hasPendingException()) {
            return null;
        }
        int millisecond = toOptionalIntegralInt(context, itemObject, "millisecond", 0);
        if (context.hasPendingException()) {
            return null;
        }
        int minute = toOptionalIntegralInt(context, itemObject, "minute", 0);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue monthValue = itemObject.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return null;
        }
        Integer month = null;
        if (!(monthValue instanceof JSUndefined) && monthValue != null) {
            Long monthLong = toRequiredIntegralLong(context, monthValue);
            if (context.hasPendingException() || monthLong == null) {
                return null;
            }
            month = monthLong.intValue();
        }

        JSValue monthCodeValue = itemObject.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return null;
        }
        IsoMonth parsedMonthCode = null;
        if (!(monthCodeValue instanceof JSUndefined) && monthCodeValue != null) {
            String monthCodeText = convertMonthCodeToString(context, monthCodeValue);
            if (context.hasPendingException() || monthCodeText == null) {
                return null;
            }
            parsedMonthCode = IsoMonth.parseByMonthCode(
                    context,
                    monthCodeText,
                    "Temporal error: Month code out of range.");
            if (context.hasPendingException() || parsedMonthCode == null) {
                return null;
            }
        }

        int nanosecond = toOptionalIntegralInt(context, itemObject, "nanosecond", 0);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue offsetValue = itemObject.get(PropertyKey.fromString("offset"));
        if (context.hasPendingException()) {
            return null;
        }
        Integer offsetSeconds = null;
        if (!(offsetValue instanceof JSUndefined) && offsetValue != null) {
            String offsetText;
            if (offsetValue instanceof JSString offsetString) {
                offsetText = offsetString.value();
            } else if (offsetValue instanceof JSObject) {
                offsetText = JSTypeConversions.toString(context, offsetValue).value();
                if (context.hasPendingException()) {
                    return null;
                }
            } else {
                context.throwTypeError("Temporal error: Offset must be string.");
                return null;
            }
            if (!isValidOffsetString(offsetText)) {
                context.throwRangeError("Temporal error: Invalid offset string.");
                return null;
            }
            offsetSeconds = parseOffsetSeconds(offsetText);
        }

        int second = toOptionalIntegralInt(context, itemObject, "second", 0);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue timeZoneValue = itemObject.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return null;
        }
        String timeZoneId = toTemporalTimeZoneId(context, timeZoneValue);
        if (context.hasPendingException() || timeZoneId == null) {
            return null;
        }

        JSValue yearValue = itemObject.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return null;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        Integer year = null;
        if (hasYear) {
            long yearLong = TemporalUtils.toLongIfIntegral(context, yearValue);
            if (context.hasPendingException()) {
                return null;
            }
            year = (int) yearLong;
        }

        if (calendarId.hasEra()) {
            JSValue eraValue = itemObject.get(PropertyKey.fromString("era"));
            if (context.hasPendingException()) {
                return null;
            }
            boolean hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            String era = null;
            if (hasEra) {
                era = JSTypeConversions.toString(context, eraValue).value();
                if (context.hasPendingException()) {
                    return null;
                }
            }

            JSValue eraYearValue = itemObject.get(PropertyKey.fromString("eraYear"));
            if (context.hasPendingException()) {
                return null;
            }
            boolean hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
            Integer eraYear = null;
            if (hasEraYear) {
                long eraYearLong = TemporalUtils.toLongIfIntegral(context, eraYearValue);
                if (context.hasPendingException()) {
                    return null;
                }
                eraYear = (int) eraYearLong;
            }

            if (hasEra != hasEraYear) {
                context.throwTypeError("Temporal error: DateTime argument must be object or string.");
                return null;
            }

            if (!hasYear && hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return null;
                }
                year = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                hasYear = true;
            } else if (hasYear && hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return null;
                }
                int expectedYear = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (year != expectedYear) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
            }
        }

        if (!hasYear || (month == null && parsedMonthCode == null)) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return null;
        }

        return new TemporalZonedDateTimePropertyBagData(
                calendarId,
                year,
                month,
                parsedMonthCode,
                dayOfMonth.intValue(),
                hour,
                minute,
                second,
                millisecond,
                microsecond,
                nanosecond,
                timeZoneId,
                offsetSeconds);
    }

    private static BigInteger resolveEpochNanosecondsIgnoringOffset(
            JSContext context,
            IsoDate isoDate,
            IsoTime isoTime,
            String timeZoneId,
            boolean offsetTimeZoneIdentifier,
            String disambiguation) {
        if (offsetTimeZoneIdentifier) {
            int timeZoneOffsetSeconds = parseOffsetSeconds(timeZoneId);
            return TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, timeZoneOffsetSeconds);
        } else {
            return computeWallEpochNanoseconds(context, isoDate, isoTime, timeZoneId, disambiguation);
        }
    }

    private static int roundOffsetSecondsToMinute(int offsetSeconds) {
        int sign = offsetSeconds < 0 ? -1 : 1;
        int absoluteOffsetSeconds = Math.abs(offsetSeconds);
        int absoluteOffsetMinutes = absoluteOffsetSeconds / 60;
        int remainingSeconds = absoluteOffsetSeconds % 60;
        if (remainingSeconds >= 30) {
            absoluteOffsetMinutes++;
        }
        return sign * absoluteOffsetMinutes * 60;
    }

    private static BigInteger selectMatchingEpochNanosecondsForExplicitOffset(
            IsoDate isoDate,
            IsoTime isoTime,
            String timeZoneId,
            int parsedOffsetSeconds,
            boolean allowMinuteRounding) {
        int second = isoTime.second();
        BigInteger leapSecondNanoseconds = BigInteger.ZERO;
        if (second == 60) {
            second = 59;
            leapSecondNanoseconds = BigInteger.valueOf(1_000_000_000L);
        }

        LocalDateTime localDateTime = LocalDateTime.of(
                isoDate.year(),
                isoDate.month(),
                isoDate.day(),
                isoTime.hour(),
                isoTime.minute(),
                second,
                isoTime.millisecond() * 1_000_000
                        + isoTime.microsecond() * 1_000
                        + isoTime.nanosecond());
        ZoneId zoneId = TemporalTimeZone.resolveTimeZone(timeZoneId);
        List<ZoneOffset> validOffsets = zoneId.getRules().getValidOffsets(localDateTime);
        BigInteger selectedEpochNanoseconds = null;
        for (ZoneOffset validOffset : validOffsets) {
            int candidateOffsetSeconds = validOffset.getTotalSeconds();
            boolean offsetMatches;
            if (allowMinuteRounding) {
                int roundedCandidateOffsetSeconds = roundOffsetSecondsToMinute(candidateOffsetSeconds);
                offsetMatches = roundedCandidateOffsetSeconds == parsedOffsetSeconds;
            } else {
                offsetMatches = candidateOffsetSeconds == parsedOffsetSeconds;
            }
            if (!offsetMatches) {
                continue;
            }
            Instant candidateInstant = localDateTime.atOffset(validOffset).toInstant();
            BigInteger candidateEpochNanoseconds = BigInteger.valueOf(candidateInstant.getEpochSecond())
                    .multiply(BigInteger.valueOf(1_000_000_000L))
                    .add(BigInteger.valueOf(candidateInstant.getNano()))
                    .add(leapSecondNanoseconds);
            if (selectedEpochNanoseconds == null
                    || candidateEpochNanoseconds.compareTo(selectedEpochNanoseconds) < 0) {
                selectedEpochNanoseconds = candidateEpochNanoseconds;
            }
        }
        return selectedEpochNanoseconds;
    }

    private static int toOptionalIntegralInt(JSContext context, JSObject sourceObject, String key, int defaultValue) {
        JSValue fieldValue = sourceObject.get(PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (fieldValue instanceof JSUndefined || fieldValue == null) {
            return defaultValue;
        }
        long longValue = TemporalUtils.toLongIfIntegral(context, fieldValue);
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        return (int) longValue;
    }

    private static Long toRequiredIntegralLong(JSContext context, JSValue value) {
        if (value instanceof JSUndefined || value == null) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return null;
        }

        long longValue = TemporalUtils.toLongIfIntegral(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return longValue;
    }

    private static String toTemporalTimeZoneId(JSContext context, JSValue timeZoneValue) {
        if (timeZoneValue instanceof JSUndefined || timeZoneValue == null) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return null;
        }

        String timeZoneId;
        if (timeZoneValue instanceof JSTemporalZonedDateTime zonedDateTime) {
            timeZoneId = zonedDateTime.getTimeZoneId();
        } else if (timeZoneValue instanceof JSString timeZoneString) {
            timeZoneId = normalizeTimeZoneIdentifier(context, timeZoneString.value());
            if (context.hasPendingException() || timeZoneId == null) {
                return null;
            }
        } else {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
        }

        if (!isOffsetTimeZoneIdentifier(timeZoneId)) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (DateTimeException invalidTimeZoneException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return null;
            }
        }

        return timeZoneId;
    }

    public static JSValue toTemporalZonedDateTime(JSContext context, JSValue item) {
        TemporalZonedDateTimeOptions defaultOptions = new TemporalZonedDateTimeOptions("compatible", "reject", "constrain");

        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            return createZonedDateTime(
                    context,
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId(),
                    zonedDateTime.getCalendarId());
        }

        if (item instanceof JSObject itemObject) {
            TemporalZonedDateTimePropertyBagData propertyBagData = parseZonedDateTimePropertyBag(context, itemObject);
            if (context.hasPendingException() || propertyBagData == null) {
                return JSUndefined.INSTANCE;
            }
            return createZonedDateTimeFromPropertyBag(context, propertyBagData, defaultOptions);
        }

        if (item instanceof JSString zonedDateTimeString) {
            IsoZonedDateTimeOffset parsed =
                    TemporalParser.parseZonedDateTimeString(context, zonedDateTimeString.value());
            if (context.hasPendingException() || parsed == null) {
                return JSUndefined.INSTANCE;
            }
            return createZonedDateTimeFromString(context, zonedDateTimeString.value(), parsed, defaultOptions);
        }

        context.throwTypeError("Temporal error: DateTime argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalZonedDateTime toTemporalZonedDateTimeObject(JSContext context, JSValue item) {
        JSValue result = toTemporalZonedDateTime(context, item);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalZonedDateTime) result;
    }

}
