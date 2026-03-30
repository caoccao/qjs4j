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
 * Implementation of Temporal.PlainDate constructor and static methods.
 */
public final class TemporalPlainDateConstructor {

    private TemporalPlainDateConstructor() {
    }

    /**
     * Temporal.PlainDate.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainDate one = toTemporalDateObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate two = toTemporalDateObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(IsoDate.compareIsoDate(one.getIsoDate(), two.getIsoDate()));
    }

    /**
     * Temporal.PlainDate(isoYear, isoMonth, isoDay, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDate.");
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

        String calendarId = "iso8601";
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!IsoDate.isValidIsoDate(isoYear, isoMonth, isoDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = resolveTemporalPrototype(context, "PlainDate");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDate(context, new IsoDate(isoYear, isoMonth, isoDay), calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainDate createPlainDate(JSContext context, IsoDate isoDate, String calendarId) {
        JSObject prototype = getTemporalPrototype(context, "PlainDate");
        return createPlainDate(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainDate createPlainDate(JSContext context, IsoDate isoDate, String calendarId, JSObject prototype) {
        JSTemporalPlainDate plainDate = new JSTemporalPlainDate(context, isoDate, calendarId);
        if (prototype != null) {
            plainDate.setPrototype(prototype);
        }
        return plainDate;
    }

    static JSValue dateFromFields(JSContext context, JSObject fields, JSValue options) {
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
            parsedMonthCode = parseMonthCodeSyntax(context, monthCodeText);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
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

        if (!hasYear) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!hasDay) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!hasMonth && !hasMonthCode) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int resolvedMonth;
        if (hasMonth) {
            resolvedMonth = month;
        } else {
            resolvedMonth = parsedMonthCode.month();
        }

        if (parsedMonthCode != null) {
            if (parsedMonthCode.month() < 1 || parsedMonthCode.month() > 12 || parsedMonthCode.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != parsedMonthCode.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        if (resolvedMonth < 1 || day < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        String calendarId = "iso8601";
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, calendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, resolvedMonth, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainDate(context, new IsoDate(year, resolvedMonth, day), calendarId);
        } else {
            IsoDate constrained = TemporalUtils.constrainIsoDate(year, resolvedMonth, day);
            if (!IsoDate.isValidIsoDate(constrained.year(), constrained.month(), constrained.day())) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainDate(context, constrained, calendarId);
        }
    }

    static JSValue dateFromString(JSContext context, String input) {
        IsoDate date = TemporalParser.parseDateString(context, input);
        if (date == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarId = "iso8601";
        int annotationStart = input.indexOf('[');
        while (annotationStart >= 0) {
            int annotationEnd = input.indexOf(']', annotationStart);
            if (annotationEnd <= annotationStart) {
                break;
            }
            String annotationContent = input.substring(annotationStart + 1, annotationEnd);
            if (!annotationContent.isEmpty() && annotationContent.charAt(0) == '!') {
                annotationContent = annotationContent.substring(1);
            }
            int equalSignIndex = annotationContent.indexOf('=');
            if (equalSignIndex > 0) {
                String annotationKey = annotationContent.substring(0, equalSignIndex);
                if ("u-ca".equals(annotationKey)) {
                    String annotationValue = annotationContent.substring(equalSignIndex + 1);
                    calendarId = TemporalUtils.validateCalendar(context, new JSString(annotationValue));
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    break;
                }
            }
            annotationStart = input.indexOf('[', annotationEnd + 1);
        }
        return createPlainDate(context, date, calendarId);
    }

    /**
     * Temporal.PlainDate.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalDate(context, item, options);
    }

    static JSObject getTemporalPrototype(JSContext context, String typeName) {
        JSValue temporal = context.getGlobalObject().get(PropertyKey.fromString("Temporal"));
        if (temporal instanceof JSObject temporalObj) {
            JSValue constructor = temporalObj.get(PropertyKey.fromString(typeName));
            if (constructor instanceof JSObject constructorObj) {
                JSValue prototype = constructorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject prototypeObj) {
                    return prototypeObj;
                }
            }
        }
        return null;
    }

    static int parseMonthCode(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() != 3 || monthCode.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Month code out of range.");
            return 0;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return 0;
        }
        int month = Integer.parseInt(monthCode.substring(1));
        if (month < 1 || month > 12) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return 0;
        }
        return month;
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

    static JSObject resolveTemporalPrototype(JSContext context, String typeName) {
        JSValue constructorNewTarget = context.getConstructorNewTarget();
        if (constructorNewTarget instanceof JSObject constructorObject) {
            JSValue constructorPrototype = constructorObject.get(PropertyKey.PROTOTYPE);
            if (context.hasPendingException()) {
                return null;
            }
            if (constructorPrototype instanceof JSObject) {
                JSObject resolvedPrototype = context.getPrototypeFromConstructor(constructorObject, JSObject.NAME);
                if (context.hasPendingException()) {
                    return null;
                }
                return resolvedPrototype;
            }
        }
        return getTemporalPrototype(context, typeName);
    }

    /**
     * ToTemporalDate abstract operation — converts item to JSTemporalPlainDate.
     */
    public static JSValue toTemporalDate(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDate plainDate) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDate(context, plainDate.getIsoDate(), plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return createPlainDate(context, localDateTime.date(), zonedDateTime.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return dateFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue parsedDate = dateFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return parsedDate;
        }
        context.throwTypeError("Temporal error: Date argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainDate toTemporalDateObject(JSContext context, JSValue item) {
        JSValue result = toTemporalDate(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainDate) result;
    }

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }
}
