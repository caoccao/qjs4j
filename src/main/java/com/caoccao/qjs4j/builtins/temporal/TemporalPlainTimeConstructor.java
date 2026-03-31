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
import com.caoccao.qjs4j.core.temporal.IsoTime;
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalTimeZone;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * Implementation of Temporal.PlainTime constructor and static methods.
 */
public final class TemporalPlainTimeConstructor {

    private TemporalPlainTimeConstructor() {
    }

    /**
     * Temporal.PlainTime.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainTime one = toTemporalTimeObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainTime two = toTemporalTimeObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(IsoTime.compareIsoTime(one.getIsoTime(), two.getIsoTime()));
    }

    /**
     * Temporal.PlainTime(hour?, minute?, second?, millisecond?, microsecond?, nanosecond?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainTime.");
            return JSUndefined.INSTANCE;
        }
        int hour = 0, minute = 0, second = 0, millisecond = 0, microsecond = 0, nanosecond = 0;

        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            hour = TemporalUtils.toIntegerThrowOnInfinity(context, args[0]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            minute = TemporalUtils.toIntegerThrowOnInfinity(context, args[1]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            second = TemporalUtils.toIntegerThrowOnInfinity(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            millisecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 4 && !(args[4] instanceof JSUndefined)) {
            microsecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[4]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 5 && !(args[5] instanceof JSUndefined)) {
            nanosecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[5]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
            context.throwRangeError("Temporal error: Invalid time");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainTime(context, new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond), resolvedPrototype);
    }

    public static JSTemporalPlainTime createPlainTime(JSContext context, IsoTime isoTime) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainTime");
        return createPlainTime(context, isoTime, prototype);
    }

    static JSTemporalPlainTime createPlainTime(JSContext context, IsoTime isoTime, JSObject prototype) {
        JSTemporalPlainTime plainTime = new JSTemporalPlainTime(context, isoTime);
        if (prototype != null) {
            plainTime.setPrototype(prototype);
        }
        return plainTime;
    }

    /**
     * Temporal.PlainTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalTime(context, item, options);
    }

    private static boolean isUndefinedOrNull(JSValue value) {
        return value instanceof JSUndefined || value == null;
    }

    static JSValue timeFromFields(JSContext context, JSObject fields, JSValue options) {
        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean hasTimeField = false;

        JSValue hourValue = fields.get(PropertyKey.fromString("hour"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        hasTimeField |= !isUndefinedOrNull(hourValue);
        int hour = toIntegerFieldOrDefault(context, hourValue, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue microsecondValue = fields.get(PropertyKey.fromString("microsecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        hasTimeField |= !isUndefinedOrNull(microsecondValue);
        int microsecond = toIntegerFieldOrDefault(context, microsecondValue, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue millisecondValue = fields.get(PropertyKey.fromString("millisecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        hasTimeField |= !isUndefinedOrNull(millisecondValue);
        int millisecond = toIntegerFieldOrDefault(context, millisecondValue, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue minuteValue = fields.get(PropertyKey.fromString("minute"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        hasTimeField |= !isUndefinedOrNull(minuteValue);
        int minute = toIntegerFieldOrDefault(context, minuteValue, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue nanosecondValue = fields.get(PropertyKey.fromString("nanosecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        hasTimeField |= !isUndefinedOrNull(nanosecondValue);
        int nanosecond = toIntegerFieldOrDefault(context, nanosecondValue, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue secondValue = fields.get(PropertyKey.fromString("second"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        hasTimeField |= !isUndefinedOrNull(secondValue);
        int second = toIntegerFieldOrDefault(context, secondValue, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!hasTimeField) {
            context.throwTypeError("Temporal error: Time-like argument must be object or string");
            return JSUndefined.INSTANCE;
        }

        if ("reject".equals(overflow)) {
            if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            return createPlainTime(context, new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond));
        } else {
            IsoTime constrained = TemporalUtils.constrainIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            return createPlainTime(context, constrained);
        }
    }

    static JSValue timeFromString(JSContext context, String input) {
        IsoTime time = TemporalParser.parseTimeString(context, input);
        if (time == null) {
            return JSUndefined.INSTANCE;
        }
        return createPlainTime(context, time);
    }

    private static int toIntegerFieldOrDefault(JSContext context, JSValue value, int defaultValue) {
        if (isUndefinedOrNull(value)) {
            return defaultValue;
        }
        return TemporalUtils.toIntegerThrowOnInfinity(context, value);
    }

    /**
     * ToTemporalTime abstract operation.
     */
    public static JSValue toTemporalTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainTime plainTime) {
            return createPlainTime(context, plainTime.getIsoTime());
        }
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            return createPlainTime(context, plainDateTime.getIsoDateTime().time());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            return createPlainTime(
                    context,
                    TemporalTimeZone.epochNsToDateTimeInZone(
                            zonedDateTime.getEpochNanoseconds(),
                            zonedDateTime.getTimeZoneId()).time());
        }
        if (item instanceof JSObject itemObj) {
            return timeFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            return timeFromString(context, itemStr.value());
        }
        context.throwTypeError("Temporal error: Time-like argument must be object or string");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainTime toTemporalTimeObject(JSContext context, JSValue item) {
        JSValue result = toTemporalTime(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainTime) result;
    }
}
