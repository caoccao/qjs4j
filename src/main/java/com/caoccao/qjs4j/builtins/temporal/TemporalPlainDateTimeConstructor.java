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
     * Temporal.PlainDateTime(isoYear, isoMonth, isoDay, hour?, minute?, second?, millisecond?, microsecond?, nanosecond?, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDateTime.");
            return JSUndefined.INSTANCE;
        }

        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 2 ? args[2] : JSUndefined.INSTANCE);
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
        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, isoDateTime, calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainDateTime createPlainDateTime(JSContext context, IsoDateTime isoDateTime, TemporalCalendarId calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainDateTime");
        return createPlainDateTime(context, isoDateTime, calendarId, prototype);
    }

    static JSTemporalPlainDateTime createPlainDateTime(JSContext context, IsoDateTime isoDateTime, TemporalCalendarId calendarId, JSObject prototype) {
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
        int dayOfMonth = Integer.MIN_VALUE;
        if (hasDay) {
            dayOfMonth = TemporalUtils.toIntegerThrowOnInfinity(context, dayValue);
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
        IsoMonth parsedMonthCode = null;
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
            parsedMonthCode = IsoMonth.parseByMonthCode(
                    context,
                    monthCodeText,
                    "Temporal error: Month code out of range.");
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

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        boolean hasEra = false;
        boolean hasEraYear = false;
        String era = null;
        Integer eraYear = null;
        if (calendarId.hasEra()) {
            JSValue eraValue = fields.get(PropertyKey.fromString("era"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            if (hasEra) {
                era = JSTypeConversions.toString(context, eraValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            JSValue eraYearValue = fields.get(PropertyKey.fromString("eraYear"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
            if (hasEraYear) {
                eraYear = TemporalUtils.toIntegerThrowOnInfinity(context, eraYearValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            if (hasEra != hasEraYear) {
                context.throwTypeError("Temporal error: DateTime argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            if (!hasYear && hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                year = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                hasYear = true;
            } else if (hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int expectedYear = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (year != expectedYear) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
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

        String monthCodeText = null;
        if (parsedMonthCode != null) {
            monthCodeText = IsoMonth.toMonthCode(parsedMonthCode.month());
            if (parsedMonthCode.leapMonth()) {
                monthCodeText += "L";
            }
        }
        Integer monthFromProperty = hasMonth ? month : null;
        IsoDate resultDate = TemporalCalendarMath.calendarDateToIsoDate(
                context,
                calendarId,
                year,
                monthFromProperty,
                monthCodeText,
                dayOfMonth,
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
        return createPlainDateTime(context, resultDate.atTime(resultTime), calendarId);
    }

    static JSValue dateTimeFromString(JSContext context, String input) {
        IsoCalendarDateTime parsed = TemporalParser.parseDateTimeString(context, input);
        if (parsed == null) {
            return JSUndefined.INSTANCE;
        }
        if (!parsed.date().isWithinPlainDateTimeRange(parsed.time())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, parsed.date().atTime(parsed.time()), parsed.calendar());
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

    /**
     * ToTemporalDateTime abstract operation.
     */
    public static JSValue toTemporalDateTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDateTime(context, plainDateTime.getIsoDateTime(), plainDateTime.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDate plainDate) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDateTime(
                    context,
                    plainDate.getIsoDate().atMidnight(),
                    plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
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

}
