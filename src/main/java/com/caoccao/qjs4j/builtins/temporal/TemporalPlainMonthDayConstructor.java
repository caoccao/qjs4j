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
import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * Implementation of Temporal.PlainMonthDay constructor and static methods.
 */
public final class TemporalPlainMonthDayConstructor {

    private TemporalPlainMonthDayConstructor() {
    }

    /**
     * Temporal.PlainMonthDay(isoMonth, isoDay, calendar?, referenceISOYear?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainYearMonth.");
            return JSUndefined.INSTANCE;
        }

        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        String calendarId = "iso8601";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[2]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        int referenceYear = 1972;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            referenceYear = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        if (!IsoDate.isValidIsoDate(referenceYear, isoMonth, isoDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainMonthDay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainMonthDay(context, new IsoDate(referenceYear, isoMonth, isoDay), calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainMonthDay createPlainMonthDay(JSContext context, IsoDate isoDate, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainMonthDay");
        return createPlainMonthDay(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainMonthDay createPlainMonthDay(JSContext context, IsoDate isoDate, String calendarId, JSObject prototype) {
        JSTemporalPlainMonthDay plainMonthDay = new JSTemporalPlainMonthDay(context, isoDate, calendarId);
        if (prototype != null) {
            plainMonthDay.setPrototype(prototype);
        }
        return plainMonthDay;
    }

    private static String firstCalendarAnnotation(String text) {
        int annotationStart = text.indexOf('[');
        while (annotationStart >= 0) {
            int annotationEnd = text.indexOf(']', annotationStart);
            if (annotationEnd <= annotationStart) {
                return null;
            }
            String annotationContent = text.substring(annotationStart + 1, annotationEnd);
            if (!annotationContent.isEmpty() && annotationContent.charAt(0) == '!') {
                annotationContent = annotationContent.substring(1);
            }
            int equalSignIndex = annotationContent.indexOf('=');
            if (equalSignIndex > 0) {
                String annotationKey = annotationContent.substring(0, equalSignIndex);
                if ("u-ca".equals(annotationKey)) {
                    return annotationContent.substring(equalSignIndex + 1);
                }
            }
            annotationStart = text.indexOf('[', annotationEnd + 1);
        }
        return null;
    }

    /**
     * Temporal.PlainMonthDay.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalMonthDay(context, item, options);
    }

    static JSValue monthDayFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String calendarId = "iso8601";
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, calendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
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
        String monthCode = null;
        if (hasMonthCode) {
            monthCode = JSTypeConversions.toString(context, monthCodeValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = 1972;

        ParsedMonthCode parsedMonthCode = null;
        if (hasMonthCode) {
            parsedMonthCode = parseMonthCodeSyntaxForMonthDayFrom(context, monthCode);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (!hasDay || (!hasMonth && !hasMonthCode)) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        int resolvedMonth = hasMonth ? month : parsedMonthCode.month();
        if (resolvedMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (parsedMonthCode != null) {
            if (parsedMonthCode.month() < 1 || parsedMonthCode.month() > 12) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (parsedMonthCode.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != parsedMonthCode.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, resolvedMonth, dayOfMonth)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainMonthDay(context, new IsoDate(1972, resolvedMonth, dayOfMonth), calendarId);
        }

        IsoDate constrainedDate = IsoDate.constrain(year, resolvedMonth, dayOfMonth);
        if (resolvedMonth > 12) {
            constrainedDate = IsoDate.constrain(year, 12, dayOfMonth);
        }
        return createPlainMonthDay(
                context,
                new IsoDate(1972, constrainedDate.month(), constrainedDate.day()),
                calendarId);
    }

    static JSValue monthDayFromString(JSContext context, String input) {
        IsoDate date = TemporalParser.parseMonthDayString(context, input);
        if (date == null) {
            return JSUndefined.INSTANCE;
        }
        String calendar = "iso8601";
        String calendarAnnotation = firstCalendarAnnotation(input);
        if (calendarAnnotation != null) {
            calendar = TemporalUtils.validateCalendar(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        return createPlainMonthDay(context, new IsoDate(date.year(), date.month(), date.day()), calendar);
    }

    private static ParsedMonthCode parseMonthCodeSyntaxForMonthDayFrom(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() < 3 || monthCode.length() > 4) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (monthCode.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int month = Integer.parseInt(monthCode.substring(1, 3));
        boolean leapMonth = false;
        if (monthCode.length() == 4) {
            if (monthCode.charAt(3) != 'L') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            leapMonth = true;
        }
        return new ParsedMonthCode(month, leapMonth);
    }

    public static JSValue toTemporalMonthDay(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainMonthDay plainMonthDay) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainMonthDay(context, plainMonthDay.getIsoDate(), plainMonthDay.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            JSObject plainMonthDayPrototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainMonthDay");
            if (itemObj == plainMonthDayPrototype) {
                context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            return monthDayFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue result = monthDayFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return result;
        }
        context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainMonthDay toTemporalMonthDayObject(JSContext context, JSValue item) {
        JSValue result = toTemporalMonthDay(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) return null;
        return (JSTemporalPlainMonthDay) result;
    }

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }
}
