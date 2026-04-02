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

import java.util.Locale;

/**
 * Implementation of Temporal.PlainYearMonth constructor and static methods.
 */
public final class TemporalPlainYearMonthConstructor {

    private TemporalPlainYearMonthConstructor() {
    }

    /**
     * Temporal.PlainYearMonth.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainYearMonth firstYearMonth = toTemporalYearMonthObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainYearMonth secondYearMonth = toTemporalYearMonthObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int comparisonResult = Integer.compare(firstYearMonth.getIsoDate().year(), secondYearMonth.getIsoDate().year());
        if (comparisonResult != 0) {
            return JSNumber.of(comparisonResult);
        }

        comparisonResult = Integer.compare(firstYearMonth.getIsoDate().month(), secondYearMonth.getIsoDate().month());
        if (comparisonResult != 0) {
            return JSNumber.of(comparisonResult);
        }

        return JSNumber.of(Integer.compare(firstYearMonth.getIsoDate().day(), secondYearMonth.getIsoDate().day()));
    }

    /**
     * Temporal.PlainYearMonth(isoYear, isoMonth, calendar?, referenceISODay?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainYearMonth.");
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

        String calendarId = "iso8601";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        int referenceDay = 1;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            referenceDay = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!isValidIsoYearMonth(isoYear, isoMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        if (referenceDay < 1 || referenceDay > IsoDate.daysInMonth(isoYear, isoMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainYearMonth");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainYearMonth(context, new IsoDate(isoYear, isoMonth, referenceDay), calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainYearMonth createPlainYearMonth(JSContext context, IsoDate isoDate, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainYearMonth");
        return createPlainYearMonth(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainYearMonth createPlainYearMonth(JSContext context, IsoDate isoDate, String calendarId, JSObject prototype) {
        JSTemporalPlainYearMonth plainYearMonth = new JSTemporalPlainYearMonth(context, isoDate, calendarId);
        if (prototype != null) {
            plainYearMonth.setPrototype(prototype);
        }
        return plainYearMonth;
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
     * Temporal.PlainYearMonth.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (item instanceof JSTemporalPlainYearMonth plainYearMonth) {
            String overflow = TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException() || overflow == null) {
                return JSUndefined.INSTANCE;
            }
            return createPlainYearMonth(context, plainYearMonth.getIsoDate(), plainYearMonth.getCalendarId());
        }
        return toTemporalYearMonth(context, item, options);
    }

    static boolean isValidIsoYearMonth(int year, int month) {
        if (month < 1 || month > 12) {
            return false;
        }
        if (year < -271821 || year > 275760) {
            return false;
        }
        if (year == -271821 && month < 4) {
            return false;
        }
        return year != 275760 || month <= 9;
    }

    private static ParsedMonthCode parseMonthCodeForYearMonthFrom(JSContext context, JSValue monthCodeValue) {
        String monthCodeText;
        if (monthCodeValue instanceof JSString monthCodeString) {
            monthCodeText = monthCodeString.value();
        } else if (monthCodeValue instanceof JSObject) {
            JSValue primitiveMonthCode =
                    JSTypeConversions.toPrimitive(context, monthCodeValue, JSTypeConversions.PreferredType.STRING);
            if (context.hasPendingException()) {
                return null;
            }
            if (!(primitiveMonthCode instanceof JSString primitiveMonthCodeString)) {
                context.throwTypeError("Temporal error: Month code must be string.");
                return null;
            }
            monthCodeText = primitiveMonthCodeString.value();
        } else {
            context.throwTypeError("Temporal error: Month code must be string.");
            return null;
        }

        if (monthCodeText.length() < 3 || monthCodeText.length() > 4) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }
        if (monthCodeText.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }
        if (!Character.isDigit(monthCodeText.charAt(1)) || !Character.isDigit(monthCodeText.charAt(2))) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }

        boolean leapMonth = false;
        if (monthCodeText.length() == 4) {
            if (monthCodeText.charAt(3) != 'L') {
                context.throwRangeError("Temporal error: Month code out of range.");
                return null;
            }
            leapMonth = true;
        }

        int month = Integer.parseInt(monthCodeText.substring(1, 3));
        return new ParsedMonthCode(month, leapMonth);
    }

    public static JSValue toTemporalYearMonth(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainYearMonth plainYearMonth) {
            return createPlainYearMonth(context, plainYearMonth.getIsoDate(), plainYearMonth.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return yearMonthFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue result = yearMonthFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String overflow = TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException() || overflow == null) {
                return JSUndefined.INSTANCE;
            }
            return result;
        }
        context.throwTypeError("Temporal error: year argument must be an object.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainYearMonth toTemporalYearMonthObject(JSContext context, JSValue item) {
        JSValue result = toTemporalYearMonth(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainYearMonth) result;
    }

    static JSValue yearMonthFromFields(JSContext context, JSObject fields, JSValue options) {
        JSObject plainYearMonthPrototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainYearMonth");
        if (fields == plainYearMonthPrototype) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }

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
            parsedMonthCode = parseMonthCodeForYearMonthFrom(context, monthCodeValue);
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

        if (!hasYear || (!hasMonth && !hasMonthCode)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (hasMonthCode) {
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

        if (month < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if ("reject".equals(overflow)) {
            if (month > 12 || !isValidIsoYearMonth(year, month)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainYearMonth(context, new IsoDate(year, month, 1), calendarId);
        }

        if (month > 12) {
            month = 12;
        }
        if (!isValidIsoYearMonth(year, month)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return createPlainYearMonth(context, new IsoDate(year, month, 1), calendarId);
    }

    static JSValue yearMonthFromString(JSContext context, String input) {
        IsoDate date = TemporalParser.parseYearMonthString(context, input);
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

        return createPlainYearMonth(
                context,
                new IsoDate(date.year(), date.month(), 1),
                calendar.toLowerCase(Locale.ROOT));
    }

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }
}
