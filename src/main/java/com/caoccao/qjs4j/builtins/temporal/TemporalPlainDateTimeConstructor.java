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
        if (!TemporalUtils.requireNewTarget(context, "PlainDateTime")) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = TemporalPlainDateConstructor.readDateArguments(context, args);
        if (isoDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime isoTime = TemporalPlainTimeConstructor.readTimeArguments(context, args, 3);
        if (isoTime == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalUtils.calendarArgument(context, args, 9);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

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

        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        Integer dayOfMonth = bag.integer("day");
        int hour = bag.time("hour", 0);
        int microsecond = bag.time("microsecond", 0);
        int millisecond = bag.time("millisecond", 0);
        int minute = bag.time("minute", 0);
        Integer month = bag.integer("month");
        IsoMonth parsedMonthCode = bag.monthCode();
        int nanosecond = bag.time("nanosecond", 0);
        int second = bag.time("second", 0);
        Integer year = bag.integer("year");
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

        IsoTime resultTime = IsoTime.regulate(context, overflow, "Temporal error: Invalid time", hour, minute, second,
                millisecond, microsecond, nanosecond);
        if (resultTime == null) {
            return JSUndefined.INSTANCE;
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
            IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
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
