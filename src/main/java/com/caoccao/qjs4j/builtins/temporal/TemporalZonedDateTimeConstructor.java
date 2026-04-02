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
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        JSTemporalZonedDateTime secondZonedDateTime = toTemporalZonedDateTimeObject(context, twoArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        return JSNumber.of(firstZonedDateTime.getEpochNanoseconds().compareTo(secondZonedDateTime.getEpochNanoseconds()));
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
        if (!(args[1] instanceof JSString tzStr)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = TemporalDurationConstructor.parseTimeZoneIdentifierString(context, tzStr.value());
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
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "ZonedDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createZonedDateTime(context, epochNs, timeZoneId, calendarId, resolvedPrototype);
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

    /**
     * Temporal.ZonedDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return toTemporalZonedDateTime(context, item);
    }

    private static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
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

    public static JSValue toTemporalZonedDateTime(JSContext context, JSValue item) {
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            return createZonedDateTime(context, zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
        }
        if (item instanceof JSObject itemObject) {
            return toTemporalZonedDateTimeFromPropertyBag(context, itemObject);
        }
        if (item instanceof JSString zonedDateTimeString) {
            TemporalParser.ParsedZonedDateTime parsed = TemporalParser.parseZonedDateTimeString(context, zonedDateTimeString.value());
            if (context.hasPendingException() || parsed == null) {
                return JSUndefined.INSTANCE;
            }
            if (!isZonedDateTimeWithinRange(parsed.date(), parsed.time())) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }

            String timeZoneId = TemporalDurationConstructor.parseTimeZoneIdentifierString(context, parsed.timeZoneId());
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
            BigInteger epochNs = TemporalTimeZone.utcDateTimeToEpochNs(parsed.date(), parsed.time(), parsed.offsetSeconds());
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNs)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
            return createZonedDateTime(context, epochNs, timeZoneId, parsed.calendarId());
        }
        context.throwTypeError("Temporal error: DateTime argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    private static JSValue toTemporalZonedDateTimeFromPropertyBag(JSContext context, JSObject itemObject) {
        String calendarId = "iso8601";
        JSValue calendarValue = itemObject.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, calendarValue);
            if (context.hasPendingException() || calendarId == null) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalDurationConstructor.RelativeToReference relativeToReference =
                TemporalDurationConstructor.parseRelativeToValue(context, itemObject);
        if (context.hasPendingException() || relativeToReference == null) {
            return JSUndefined.INSTANCE;
        }
        if (relativeToReference.epochNanoseconds() == null || relativeToReference.timeZoneId() == null) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(relativeToReference.epochNanoseconds())) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }

        return createZonedDateTime(
                context,
                relativeToReference.epochNanoseconds(),
                relativeToReference.timeZoneId(),
                calendarId);
    }

    public static JSTemporalZonedDateTime toTemporalZonedDateTimeObject(JSContext context, JSValue item) {
        JSValue result = toTemporalZonedDateTime(context, item);
        if (context.hasPendingException()) return null;
        return (JSTemporalZonedDateTime) result;
    }
}
