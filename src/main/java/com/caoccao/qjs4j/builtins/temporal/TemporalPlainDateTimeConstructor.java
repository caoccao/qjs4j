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
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();

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

        IsoDate date = new IsoDate(isoYear, isoMonth, isoDay);
        IsoTime time = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        if (!isValidPlainDateTimeRange(date, time)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoDateTime dt = new IsoDateTime(date, time);
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
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;
        int day = Integer.MIN_VALUE;
        if (hasDay) {
            day = TemporalUtils.toIntegerThrowOnInfinity(context, dayValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
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

        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        int month = Integer.MIN_VALUE;
        if (hasMonth) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = !(monthCodeValue instanceof JSUndefined) && monthCodeValue != null;
        ParsedMonthCode parsedMonthCode = null;
        if (hasMonthCode) {
            String monthCodeText;
            if (monthCodeValue instanceof JSString monthCodeString) {
                monthCodeText = monthCodeString.value();
            } else if (monthCodeValue instanceof JSObject) {
                JSValue primitiveMonthCode =
                        JSTypeConversions.toPrimitive(context, monthCodeValue, JSTypeConversions.PreferredType.STRING);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!(primitiveMonthCode instanceof JSString primitiveMonthCodeString)) {
                    context.throwTypeError("Temporal error: Month code must be string.");
                    return JSUndefined.INSTANCE;
                }
                monthCodeText = primitiveMonthCodeString.value();
            } else {
                context.throwTypeError("Temporal error: Month code must be string.");
                return JSUndefined.INSTANCE;
            }
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            parsedMonthCode = parseMonthCodeSyntax(context, monthCodeText);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int second = TemporalUtils.getIntegerField(context, fields, "second", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = Integer.MIN_VALUE;
        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!hasYear || !hasDay || (!hasMonth && !hasMonthCode)) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (hasMonthCode && parsedMonthCode != null) {
            if (parsedMonthCode.month() < 1 || parsedMonthCode.month() > 12 || parsedMonthCode.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != parsedMonthCode.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (!hasMonth) {
                month = parsedMonthCode.month();
            }
        }

        String calendarId = "iso8601";
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, calendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate resultDate;
        IsoTime resultTime;
        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            resultDate = new IsoDate(year, month, day);
            resultTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        } else {
            if (month < 1 || day < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            resultDate = TemporalUtils.constrainIsoDate(year, month, day);
            resultTime = TemporalUtils.constrainIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        }

        if (!isValidPlainDateTimeRange(resultDate, resultTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, new IsoDateTime(resultDate, resultTime), calendarId);
    }

    static JSValue dateTimeFromString(JSContext context, String input) {
        TemporalParser.ParsedDateTime parsed = TemporalParser.parseDateTimeString(context, input);
        if (parsed == null) {
            return JSUndefined.INSTANCE;
        }
        if (!isValidPlainDateTimeRange(parsed.date(), parsed.time())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
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

    private static boolean isValidPlainDateTimeRange(IsoDate date, IsoTime time) {
        long epochDay = date.toEpochDay();
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return false;
        }
        if (epochDay == MIN_SUPPORTED_EPOCH_DAY && time.totalNanoseconds() == 0L) {
            return false;
        }
        return true;
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

    /**
     * ToTemporalDateTime abstract operation.
     */
    public static JSValue toTemporalDateTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDateTime pdt) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDateTime(context, pdt.getIsoDateTime(), pdt.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDate plainDate) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDateTime(
                    context,
                    new IsoDateTime(plainDate.getIsoDate(), IsoTime.MIDNIGHT),
                    plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return createPlainDateTime(context, localDateTime, zonedDateTime.getCalendarId());
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

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }
}
