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

/**
 * Implementation of Temporal.PlainDateTime constructor and static methods.
 */
public final class TemporalPlainDateTimeConstructor {
    private TemporalPlainDateTimeConstructor() {
    }

    /**
     * Temporal.PlainDateTime.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = JSValue.argumentOrUndefined(args, 0);
        JSValue twoArg = JSValue.argumentOrUndefined(args, 1);

        JSTemporalPlainDateTime firstDateTime = toTemporalDateTimeObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDateTime secondDateTime = toTemporalDateTimeObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(firstDateTime.getIsoDateTime().compareTo(secondDateTime.getIsoDateTime()));
    }

    /**
     * Temporal.PlainDateTime(isoYear, isoMonth, isoDay, hour?, minute?, second?, millisecond?, microsecond?,
     * nanosecond?, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDateTime.");
            return JSUndefined.INSTANCE;
        }

        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 2));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int hour = 0, minute = 0, second = 0, millisecond = 0, microsecond = 0, nanosecond = 0;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            hour = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 4 && !(args[4] instanceof JSUndefined)) {
            minute = TemporalUtils.toIntegerThrowOnInfinity(context, args[4]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 5 && !(args[5] instanceof JSUndefined)) {
            second = TemporalUtils.toIntegerThrowOnInfinity(context, args[5]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 6 && !(args[6] instanceof JSUndefined)) {
            millisecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[6]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 7 && !(args[7] instanceof JSUndefined)) {
            microsecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[7]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 8 && !(args[8] instanceof JSUndefined)) {
            nanosecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[8]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (args.length > 9 && !(args[9] instanceof JSUndefined)) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, args[9]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate isoDate = new IsoDate(isoYear, isoMonth, isoDay);
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoTime isoTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        if (!isoTime.isValid()) {
            context.throwRangeError("Temporal error: Invalid time");
            return JSUndefined.INSTANCE;
        }

        if (!isoDate.isWithinPlainDateTimeRange(isoTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoDateTime isoDateTime = isoDate.atTime(isoTime);
        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "PlainDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDateTime.create(context, isoDateTime, calendarId, resolvedPrototype);
    }

    static JSValue dateTimeFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        Integer dayOfMonth = TemporalUtils.getOptionalIntegerField(context, fields, "day");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int hour = TemporalUtils.getIntegerField(context, fields, "hour", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int microsecond = TemporalUtils.getIntegerField(context, fields, "microsecond", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int millisecond = TemporalUtils.getIntegerField(context, fields, "millisecond", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int minute = TemporalUtils.getIntegerField(context, fields, "minute", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer month = TemporalUtils.getOptionalIntegerField(context, fields, "month");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoMonth parsedMonthCode = IsoMonth.fromField(context, monthCodeValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int second = TemporalUtils.getIntegerField(context, fields, "second", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer year = TemporalUtils.getOptionalIntegerField(context, fields, "year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        year = TemporalUtils.resolveEraYear(context, fields, calendarId, year,
                "Temporal error: DateTime argument must be object or string.");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (year == null || dayOfMonth == null || (month == null && parsedMonthCode == null)) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String monthCodeText = parsedMonthCode == null ? null : parsedMonthCode.monthCode();
        IsoDate resultDate = IsoDate.calendarDateToIsoDate(context, calendarId, year, month, monthCodeText, dayOfMonth,
                overflow);
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime resultTime;
        if ("reject".equals(overflow)) {
            resultTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            if (!resultTime.isValid()) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
        } else {
            resultTime = IsoTime.createNormalized(hour, minute, second, millisecond, microsecond, nanosecond);
        }

        if (!resultDate.isWithinPlainDateTimeRange(resultTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDateTime.create(context, resultDate.atTime(resultTime), calendarId);
    }

    static JSValue dateTimeFromString(JSContext context, String input) {
        IsoCalendarDateTime parsed = IsoCalendarDateTime.parseDateTimeString(context, input);
        if (parsed == null) {
            return JSUndefined.INSTANCE;
        }
        if (!parsed.date().isWithinPlainDateTimeRange(parsed.time())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDateTime.create(context, parsed.date().atTime(parsed.time()), parsed.calendar());
    }

    /**
     * Temporal.PlainDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        return toTemporalDateTime(context, item, options);
    }

    private static boolean isTemporalPlainDateTimePrototype(JSContext context, JSObject candidate) {
        JSValue temporal = context.getGlobalObject().get(PropertyKey.fromString("Temporal"));
        if (!(temporal instanceof JSObject temporalObject)) {
            return false;
        }
        JSValue constructor = temporalObject.get(PropertyKey.fromString("PlainDateTime"));
        if (!(constructor instanceof JSObject constructorObject)) {
            return false;
        }
        JSValue prototype = constructorObject.get(PropertyKey.PROTOTYPE);
        return prototype == candidate;
    }

    /**
     * ToTemporalDateTime abstract operation.
     */
    public static JSValue toTemporalDateTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainDateTime.create(context, plainDateTime.getIsoDateTime(),
                    plainDateTime.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDate plainDate) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainDateTime.create(context, plainDate.getIsoDate().atMidnight(),
                    plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return JSTemporalPlainDateTime.create(context, localDateTime, zonedDateTime.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            if (isTemporalPlainDateTimePrototype(context, itemObj)) {
                context.throwTypeError("Temporal error: DateTime argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            return dateTimeFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue parsed = dateTimeFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return parsed;
        }
        context.throwTypeError("Temporal error: DateTime argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainDateTime toTemporalDateTimeObject(JSContext context, JSValue item) {
        JSValue result = toTemporalDateTime(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainDateTime) result;
    }

}
