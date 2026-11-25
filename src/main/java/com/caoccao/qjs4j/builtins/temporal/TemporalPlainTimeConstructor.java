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
import com.caoccao.qjs4j.core.temporal.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.IsoTime;
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
        JSValue oneArg = JSValue.argumentOrUndefined(args, 0);
        JSValue twoArg = JSValue.argumentOrUndefined(args, 1);

        JSTemporalPlainTime firstTime = toTemporalTimeObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainTime secondTime = toTemporalTimeObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(firstTime.getIsoTime().compareTo(secondTime.getIsoTime()));
    }

    /**
     * Temporal.PlainTime(hour?, minute?, second?, millisecond?, microsecond?, nanosecond?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainTime.");
            return JSUndefined.INSTANCE;
        }
        IsoTime isoTime = readTimeArguments(context, args, 0);
        if (isoTime == null) {
            return JSUndefined.INSTANCE;
        }

        if (!isoTime.isValid()) {
            context.throwRangeError("Temporal error: Invalid time");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "PlainTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainTime.create(context, isoTime, resolvedPrototype);
    }

    /**
     * Temporal.PlainTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        return toTemporalTime(context, item, options);
    }

    static IsoTime readTimeArguments(JSContext context, JSValue[] args, int start) {
        if (args.length <= start) {
            return IsoTime.MIDNIGHT;
        }
        int[] fields = new int[6];
        int count = Math.min(fields.length, args.length - start);
        for (int i = 0; i < count; i++) {
            JSValue argument = args[start + i];
            if (!(argument instanceof JSUndefined)) {
                fields[i] = TemporalUtils.toIntegerThrowOnInfinity(context, argument);
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }
        return new IsoTime(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
    }

    static JSValue timeFromFields(JSContext context, JSObject fields, JSValue options) {
        return timeFromFields(context, fields, options, IsoTime.MIDNIGHT,
                "Temporal error: Time-like argument must be object or string");
    }

    static JSValue timeFromFields(JSContext context, JSObject fields, JSValue options, IsoTime original,
            String missingFieldsError) {
        Integer hourField = TemporalUtils.getOptionalIntegerField(context, fields, "hour");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int hour = hourField != null ? hourField : original.hour();

        Integer microsecondField = TemporalUtils.getOptionalIntegerField(context, fields, "microsecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int microsecond = microsecondField != null ? microsecondField : original.microsecond();

        Integer millisecondField = TemporalUtils.getOptionalIntegerField(context, fields, "millisecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int millisecond = millisecondField != null ? millisecondField : original.millisecond();

        Integer minuteField = TemporalUtils.getOptionalIntegerField(context, fields, "minute");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int minute = minuteField != null ? minuteField : original.minute();

        Integer nanosecondField = TemporalUtils.getOptionalIntegerField(context, fields, "nanosecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int nanosecond = nanosecondField != null ? nanosecondField : original.nanosecond();

        Integer secondField = TemporalUtils.getOptionalIntegerField(context, fields, "second");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int second = secondField != null ? secondField : original.second();

        boolean hasAnyField = hourField != null || microsecondField != null || millisecondField != null
                || minuteField != null || nanosecondField != null || secondField != null;
        if (!hasAnyField) {
            context.throwTypeError(missingFieldsError);
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if ("reject".equals(overflow)) {
            IsoTime isoTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            if (!isoTime.isValid()) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainTime.create(context, isoTime);
        } else {
            IsoTime constrained = IsoTime.createNormalized(hour, minute, second, millisecond, microsecond, nanosecond);
            return JSTemporalPlainTime.create(context, constrained);
        }
    }

    static JSValue timeFromString(JSContext context, String input) {
        IsoTime time = IsoTime.parseTimeString(context, input);
        if (time == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainTime.create(context, time);
    }

    /**
     * ToTemporalTime abstract operation.
     */
    public static JSValue toTemporalTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainTime plainTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainTime.create(context, plainTime.getIsoTime());
        }
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainTime.create(context, plainDateTime.getIsoDateTime().time());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainTime.create(context, IsoDateTime
                    .createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId())
                    .time());
        }
        if (item instanceof JSObject itemObj) {
            return timeFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue result = timeFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return result;
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
