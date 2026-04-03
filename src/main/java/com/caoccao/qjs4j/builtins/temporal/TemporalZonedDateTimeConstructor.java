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
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of Temporal.ZonedDateTime constructor and static methods.
 */
public final class TemporalZonedDateTimeConstructor {
    private static final Pattern OFFSET_BASIC_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2})(\\d{2})(?:(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final Pattern OFFSET_EXTENDED_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?$");

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

    private static int computeZoneOffsetSeconds(
            JSContext context,
            IsoDate isoDate,
            IsoTime isoTime,
            String timeZoneId,
            int explicitOffsetSeconds,
            boolean offsetTimeZoneIdentifier) {
        if (offsetTimeZoneIdentifier) {
            return parseOffsetSeconds(timeZoneId);
        }

        try {
            BigInteger guessedEpochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, explicitOffsetSeconds);
            return TemporalTimeZone.getOffsetSecondsFor(guessedEpochNanoseconds, timeZoneId);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return Integer.MIN_VALUE;
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

        String calendarId = "iso8601";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[2]);
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
                                                              String timeZoneId, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "ZonedDateTime");
        return createZonedDateTime(context, epochNs, timeZoneId, calendarId, prototype);
    }

    static JSTemporalZonedDateTime createZonedDateTime(JSContext context, BigInteger epochNs,
                                                       String timeZoneId, String calendarId, JSObject prototype) {
        JSTemporalZonedDateTime zonedDateTime = new JSTemporalZonedDateTime(context, epochNs, timeZoneId, calendarId);
        if (prototype != null) {
            zonedDateTime.setPrototype(prototype);
        }
        return zonedDateTime;
    }

    private static JSValue createZonedDateTimeFromPropertyBag(
            JSContext context,
            ZonedDateTimePropertyBagData propertyBagData,
            ZonedDateTimeOptions options) {
        Integer monthNumber = propertyBagData.month();
        ParsedMonthCode parsedMonthCode = propertyBagData.parsedMonthCode();
        if (parsedMonthCode != null) {
            if (parsedMonthCode.month() < 1 || parsedMonthCode.month() > 12 || parsedMonthCode.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (monthNumber != null && monthNumber != parsedMonthCode.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (monthNumber == null) {
                monthNumber = parsedMonthCode.month();
            }
        }

        if (monthNumber == null) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        int year = propertyBagData.year();
        int dayOfMonth = propertyBagData.day();
        int month = monthNumber;
        int hour = propertyBagData.hour();
        int minute = propertyBagData.minute();
        int second = propertyBagData.second();
        if (second == 60) {
            second = 59;
        }
        int millisecond = propertyBagData.millisecond();
        int microsecond = propertyBagData.microsecond();
        int nanosecond = propertyBagData.nanosecond();

        IsoDate isoDate;
        IsoTime isoTime;
        if ("reject".equals(options.overflow())) {
            if (!IsoDate.isValidIsoDate(year, month, dayOfMonth)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            isoDate = new IsoDate(year, month, dayOfMonth);
            isoTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        } else {
            if (month < 1 || dayOfMonth < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            isoDate = TemporalUtils.constrainIsoDate(year, month, dayOfMonth);
            isoTime = TemporalUtils.constrainIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
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
                options);
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return createZonedDateTime(context, epochNanoseconds, timeZoneId, propertyBagData.calendarId());
    }

    private static JSValue createZonedDateTimeFromString(
            JSContext context,
            String input,
            TemporalParser.ParsedZonedDateTime parsed,
            ZonedDateTimeOptions options) {
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

        BigInteger epochNanoseconds = interpretOffset(
                context,
                parsed.date(),
                parsed.time(),
                timeZoneId,
                parsed.offsetSeconds(),
                hasExplicitOffset,
                hasZuluOffset,
                options);
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return createZonedDateTime(context, epochNanoseconds, timeZoneId, parsed.calendarId());
    }

    /**
     * Temporal.ZonedDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            ZonedDateTimeOptions options = parseFromOptions(context, optionsArg);
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
            ZonedDateTimePropertyBagData propertyBagData = parseZonedDateTimePropertyBag(context, itemObject);
            if (context.hasPendingException() || propertyBagData == null) {
                return JSUndefined.INSTANCE;
            }

            ZonedDateTimeOptions options = parseFromOptions(context, optionsArg);
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }

            return createZonedDateTimeFromPropertyBag(context, propertyBagData, options);
        }

        if (item instanceof JSString zonedDateTimeString) {
            TemporalParser.ParsedZonedDateTime parsed =
                    TemporalParser.parseZonedDateTimeString(context, zonedDateTimeString.value());
            if (context.hasPendingException() || parsed == null) {
                return JSUndefined.INSTANCE;
            }

            ZonedDateTimeOptions options = parseFromOptions(context, optionsArg);
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }

            return createZonedDateTimeFromString(context, zonedDateTimeString.value(), parsed, options);
        }

        context.throwTypeError("Temporal error: DateTime argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    private static String getStringOption(
            JSContext context,
            JSObject optionsObject,
            String propertyName,
            String defaultValue,
            String[] validValues,
            String rangeErrorMessage) {
        String optionValue = TemporalUtils.getStringOption(context, optionsObject, propertyName, defaultValue);
        if (context.hasPendingException() || optionValue == null) {
            return null;
        }
        for (String validValue : validValues) {
            if (validValue.equals(optionValue)) {
                return optionValue;
            }
        }

        context.throwRangeError(rangeErrorMessage);
        return null;
    }

    private static boolean hasOffsetDesignator(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
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
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
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
            ZonedDateTimeOptions options) {
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
            int offsetSeconds = explicitOffsetSeconds != null ? explicitOffsetSeconds : 0;
            String offsetOption = options.offset();
            if ("ignore".equals(offsetOption)) {
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
            } else if ("use".equals(offsetOption)) {
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, offsetSeconds);
            } else if ("prefer".equals(offsetOption)) {
                if (!isZonedDateTimeWithinRange(isoDate, isoTime)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, offsetSeconds);
            } else {
                if (!isZonedDateTimeWithinRange(isoDate, isoTime)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                int zoneOffsetSeconds = computeZoneOffsetSeconds(
                        context,
                        isoDate,
                        isoTime,
                        timeZoneId,
                        offsetSeconds,
                        offsetTimeZoneIdentifier);
                if (context.hasPendingException()) {
                    return null;
                }
                if (zoneOffsetSeconds != offsetSeconds) {
                    context.throwRangeError("Temporal error: Invalid offset.");
                    return null;
                }
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, offsetSeconds);
            }
        }

        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }

        return epochNanoseconds;
    }

    private static boolean isMinutePrecisionOffsetIdentifier(String text) {
        OffsetParts offsetParts = parseOffsetParts(text);
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
        OffsetParts offsetParts = parseOffsetParts(offsetText);
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
        String normalizedTimeZoneId = TemporalDurationConstructor.parseTimeZoneIdentifierString(context, timeZoneText);
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

    private static ZonedDateTimeOptions parseFromOptions(JSContext context, JSValue optionsValue) {
        if (optionsValue instanceof JSUndefined || optionsValue == null) {
            return new ZonedDateTimeOptions("compatible", "reject", "constrain");
        }

        if (!(optionsValue instanceof JSObject optionsObject)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }

        String disambiguation = getStringOption(
                context,
                optionsObject,
                "disambiguation",
                "compatible",
                new String[]{"compatible", "earlier", "later", "reject"},
                "Temporal error: Invalid disambiguation option.");
        if (context.hasPendingException() || disambiguation == null) {
            return null;
        }

        String offset = getStringOption(
                context,
                optionsObject,
                "offset",
                "reject",
                new String[]{"prefer", "use", "ignore", "reject"},
                "Temporal error: Invalid offset option.");
        if (context.hasPendingException() || offset == null) {
            return null;
        }

        String overflow = getStringOption(
                context,
                optionsObject,
                "overflow",
                "constrain",
                new String[]{"constrain", "reject"},
                "Temporal error: Invalid overflow option.");
        if (context.hasPendingException() || overflow == null) {
            return null;
        }

        return new ZonedDateTimeOptions(disambiguation, offset, overflow);
    }

    private static ParsedMonthCode parseMonthCodeSyntax(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() < 3 || monthCode.length() > 4) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }
        if (monthCode.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }

        boolean leapMonth = false;
        if (monthCode.length() == 4) {
            if (monthCode.charAt(3) != 'L') {
                context.throwRangeError("Temporal error: Month code out of range.");
                return null;
            }
            leapMonth = true;
        }

        int month = Integer.parseInt(monthCode.substring(1, 3));
        return new ParsedMonthCode(month, leapMonth);
    }

    private static OffsetParts parseOffsetParts(String offsetText) {
        Matcher extendedMatcher = OFFSET_EXTENDED_PATTERN.matcher(offsetText);
        if (extendedMatcher.matches()) {
            return new OffsetParts(
                    extendedMatcher.group(1),
                    Integer.parseInt(extendedMatcher.group(2)),
                    Integer.parseInt(extendedMatcher.group(3)),
                    extendedMatcher.group(4),
                    extendedMatcher.group(5));
        }

        Matcher basicMatcher = OFFSET_BASIC_PATTERN.matcher(offsetText);
        if (basicMatcher.matches()) {
            return new OffsetParts(
                    basicMatcher.group(1),
                    Integer.parseInt(basicMatcher.group(2)),
                    Integer.parseInt(basicMatcher.group(3)),
                    basicMatcher.group(4),
                    basicMatcher.group(5));
        }

        return null;
    }

    private static int parseOffsetSeconds(String offsetText) {
        OffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return 0;
        }

        String signText = offsetParts.signText();
        int sign = ("-".equals(signText) || "\u2212".equals(signText)) ? -1 : 1;
        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        return sign * (hours * 3600 + minutes * 60);
    }

    private static ZonedDateTimePropertyBagData parseZonedDateTimePropertyBag(JSContext context, JSObject itemObject) {
        String calendarId = "iso8601";
        JSValue calendarValue = itemObject.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return null;
        }
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, calendarValue);
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
        ParsedMonthCode parsedMonthCode = null;
        if (!(monthCodeValue instanceof JSUndefined) && monthCodeValue != null) {
            String monthCodeText = convertMonthCodeToString(context, monthCodeValue);
            if (context.hasPendingException() || monthCodeText == null) {
                return null;
            }
            parsedMonthCode = parseMonthCodeSyntax(context, monthCodeText);
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
        Long year = toRequiredIntegralLong(context, yearValue);
        if (context.hasPendingException() || year == null) {
            return null;
        }

        if (month == null && parsedMonthCode == null) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return null;
        }

        return new ZonedDateTimePropertyBagData(
                calendarId,
                year.intValue(),
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
        ZonedDateTimeOptions defaultOptions = new ZonedDateTimeOptions("compatible", "reject", "constrain");

        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            return createZonedDateTime(
                    context,
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId(),
                    zonedDateTime.getCalendarId());
        }

        if (item instanceof JSObject itemObject) {
            ZonedDateTimePropertyBagData propertyBagData = parseZonedDateTimePropertyBag(context, itemObject);
            if (context.hasPendingException() || propertyBagData == null) {
                return JSUndefined.INSTANCE;
            }
            return createZonedDateTimeFromPropertyBag(context, propertyBagData, defaultOptions);
        }

        if (item instanceof JSString zonedDateTimeString) {
            TemporalParser.ParsedZonedDateTime parsed =
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

    private record OffsetParts(String signText, int hours, int minutes, String secondsText, String fractionText) {
    }

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }

    private record ZonedDateTimeOptions(String disambiguation, String offset, String overflow) {
    }

    private record ZonedDateTimePropertyBagData(
            String calendarId,
            int year,
            Integer month,
            ParsedMonthCode parsedMonthCode,
            int day,
            int hour,
            int minute,
            int second,
            int millisecond,
            int microsecond,
            int nanosecond,
            String timeZoneId,
            Integer offsetSeconds) {
    }
}
