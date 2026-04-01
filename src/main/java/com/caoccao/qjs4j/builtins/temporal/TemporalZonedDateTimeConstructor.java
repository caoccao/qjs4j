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
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalTimeZone;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.math.BigInteger;

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
        if (!(epochNsArg instanceof JSBigInt bigInt)) {
            context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, epochNsArg).value() + " to a BigInt");
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = bigInt.value();
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
        String timeZoneId = tzStr.value();
        try {
            java.time.ZoneId.of(timeZoneId);
        } catch (Exception invalidTimeZoneException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
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

    public static JSValue toTemporalZonedDateTime(JSContext context, JSValue item) {
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            return createZonedDateTime(context, zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
        }
        if (item instanceof JSString zonedDateTimeString) {
            TemporalParser.ParsedZonedDateTime parsed = TemporalParser.parseZonedDateTimeString(context, zonedDateTimeString.value());
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            try {
                java.time.ZoneId.of(parsed.timeZoneId());
            } catch (Exception invalidTimeZoneException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + parsed.timeZoneId());
                return JSUndefined.INSTANCE;
            }
            BigInteger epochNs = TemporalTimeZone.utcDateTimeToEpochNs(parsed.date(), parsed.time(), parsed.offsetSeconds());
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNs)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
            return createZonedDateTime(context, epochNs, parsed.timeZoneId(), parsed.calendarId());
        }
        context.throwTypeError("Temporal error: DateTime argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalZonedDateTime toTemporalZonedDateTimeObject(JSContext context, JSValue item) {
        JSValue result = toTemporalZonedDateTime(context, item);
        if (context.hasPendingException()) return null;
        return (JSTemporalZonedDateTime) result;
    }
}
