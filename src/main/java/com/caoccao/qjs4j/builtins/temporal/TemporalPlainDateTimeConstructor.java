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
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainDateTime one = toTemporalDateTimeObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDateTime two = toTemporalDateTimeObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(IsoDateTime.compareIsoDateTime(one.getIsoDateTime(), two.getIsoDateTime()));
    }

    /**
     * Temporal.PlainDateTime(isoYear, isoMonth, isoDay, hour?, minute?, second?, millisecond?, microsecond?, nanosecond?, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDateTime.");
            return JSUndefined.INSTANCE;
        }

        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 2 ? args[2] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int hour = 0, minute = 0, second = 0, millisecond = 0, microsecond = 0, nanosecond = 0;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            hour = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 4 && !(args[4] instanceof JSUndefined)) {
            minute = TemporalUtils.toIntegerThrowOnInfinity(context, args[4]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 5 && !(args[5] instanceof JSUndefined)) {
            second = TemporalUtils.toIntegerThrowOnInfinity(context, args[5]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 6 && !(args[6] instanceof JSUndefined)) {
            millisecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[6]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 7 && !(args[7] instanceof JSUndefined)) {
            microsecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[7]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 8 && !(args[8] instanceof JSUndefined)) {
            nanosecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[8]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        String calendarId = "iso8601";
        if (args.length > 9 && !(args[9] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[9]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        if (!IsoDate.isValidIsoDate(isoYear, isoMonth, isoDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
            context.throwRangeError("Temporal error: Invalid time");
            return JSUndefined.INSTANCE;
        }

        IsoDateTime dt = new IsoDateTime(new IsoDate(isoYear, isoMonth, isoDay),
                new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond));
        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, dt, calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainDateTime createPlainDateTime(JSContext context, IsoDateTime isoDateTime, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainDateTime");
        return createPlainDateTime(context, isoDateTime, calendarId, prototype);
    }

    static JSTemporalPlainDateTime createPlainDateTime(JSContext context, IsoDateTime isoDateTime, String calendarId, JSObject prototype) {
        JSTemporalPlainDateTime plainDateTime = new JSTemporalPlainDateTime(context, isoDateTime, calendarId);
        if (prototype != null) {
            plainDateTime.setPrototype(prototype);
        }
        return plainDateTime;
    }

    static JSValue dateTimeFromFields(JSContext context, JSObject fields, JSValue options) {
        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int year = TemporalUtils.getIntegerField(context, fields, "year", Integer.MIN_VALUE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        if (year == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        // Try month or monthCode
        int month;
        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (monthValue instanceof JSUndefined || monthValue == null) {
            if (monthCodeValue instanceof JSString monthCodeStr) {
                month = TemporalPlainDateConstructor.parseMonthCode(context, monthCodeStr.value());
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
            } else {
                context.throwTypeError("Temporal error: DateTime argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
        } else {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        int day = TemporalUtils.getIntegerField(context, fields, "day", Integer.MIN_VALUE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        if (day == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        int hour = TemporalUtils.getIntegerField(context, fields, "hour", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int minute = TemporalUtils.getIntegerField(context, fields, "minute", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int second = TemporalUtils.getIntegerField(context, fields, "second", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int millisecond = TemporalUtils.getIntegerField(context, fields, "millisecond", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int microsecond = TemporalUtils.getIntegerField(context, fields, "microsecond", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        String calendarId = "iso8601";
        if (calendarValue instanceof JSString calStr) {
            calendarId = calStr.value().toLowerCase(java.util.Locale.ROOT);
        }

        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            return createPlainDateTime(context,
                    new IsoDateTime(new IsoDate(year, month, day),
                            new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond)),
                    calendarId);
        } else {
            IsoDate constrained = TemporalUtils.constrainIsoDate(year, month, day);
            IsoTime constrainedTime = TemporalUtils.constrainIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            return createPlainDateTime(context, new IsoDateTime(constrained, constrainedTime), calendarId);
        }
    }

    static JSValue dateTimeFromString(JSContext context, String input) {
        TemporalParser.ParsedDateTime parsed = TemporalParser.parseDateTimeString(context, input);
        if (parsed == null) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, new IsoDateTime(parsed.date(), parsed.time()), parsed.calendar());
    }

    /**
     * Temporal.PlainDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalDateTime(context, item, options);
    }

    /**
     * ToTemporalDateTime abstract operation.
     */
    public static JSValue toTemporalDateTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDateTime pdt) {
            return createPlainDateTime(context, pdt.getIsoDateTime(), pdt.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return dateTimeFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            return dateTimeFromString(context, itemStr.value());
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
