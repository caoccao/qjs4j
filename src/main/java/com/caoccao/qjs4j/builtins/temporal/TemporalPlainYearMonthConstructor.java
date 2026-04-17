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

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
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

    public static JSTemporalPlainYearMonth createPlainYearMonth(JSContext context, IsoDate isoDate, TemporalCalendarId calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainYearMonth");
        return createPlainYearMonth(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainYearMonth createPlainYearMonth(JSContext context, IsoDate isoDate, TemporalCalendarId calendarId, JSObject prototype) {
        JSTemporalPlainYearMonth plainYearMonth = new JSTemporalPlainYearMonth(context, isoDate, calendarId);
        if (prototype != null) {
            plainYearMonth.setPrototype(prototype);
        }
        return plainYearMonth;
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

    private static TemporalSupportedYearMonthBoundary getSupportedYearMonthBoundary(TemporalCalendarId calendarId) {
        return switch (calendarId) {
            case BUDDHIST -> new TemporalSupportedYearMonthBoundary(-271278, 5, 276303, 9);
            case COPTIC -> new TemporalSupportedYearMonthBoundary(-272099, 4, 275471, 6);
            case ETHIOAA -> new TemporalSupportedYearMonthBoundary(-266323, 4, 281247, 6);
            case ETHIOPIC -> new TemporalSupportedYearMonthBoundary(-271823, 4, 275747, 6);
            case GREGORY -> new TemporalSupportedYearMonthBoundary(-271821, 4, 275760, 9);
            case HEBREW -> new TemporalSupportedYearMonthBoundary(-268058, 12, 279517, 10);
            case INDIAN -> new TemporalSupportedYearMonthBoundary(-271899, 2, 275682, 7);
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA ->
                    new TemporalSupportedYearMonthBoundary(-280804, 4, 283583, 6);
            case JAPANESE -> new TemporalSupportedYearMonthBoundary(-271821, 4, 275760, 9);
            case PERSIAN -> new TemporalSupportedYearMonthBoundary(-272442, 2, 275139, 7);
            case ROC -> new TemporalSupportedYearMonthBoundary(-273732, 5, 273849, 9);
            default -> null;
        };
    }

    private static boolean isOutsideSupportedYearMonth(
            TemporalSupportedYearMonthBoundary supportedYearMonthBoundary,
            int year,
            int month) {
        if (year < supportedYearMonthBoundary.minimumYear()) {
            return true;
        }
        if (year == supportedYearMonthBoundary.minimumYear() && month < supportedYearMonthBoundary.minimumMonth()) {
            return true;
        }
        if (year > supportedYearMonthBoundary.maximumYear()) {
            return true;
        }
        return year == supportedYearMonthBoundary.maximumYear() && month > supportedYearMonthBoundary.maximumMonth();
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

    private static int monthNumberFromMonthCode(String monthCode) {
        if (monthCode == null || monthCode.length() < 3) {
            return Integer.MIN_VALUE;
        }
        return Integer.parseInt(monthCode.substring(1, 3));
    }

    private static IsoMonth parseMonthCodeForYearMonthFrom(JSContext context, JSValue monthCodeValue) {
        IsoMonth parsedMonthCode = TemporalFieldResolver.parseMonthCodeValue(
                context,
                monthCodeValue,
                "Temporal error: Month code must be string.",
                "Temporal error: Month code out of range.");
        if (parsedMonthCode == null) {
            return null;
        }
        return new IsoMonth(parsedMonthCode.month(), parsedMonthCode.leapMonth());
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
        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
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
        Integer monthFromProperty = null;
        if (hasMonth) {
            int month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            monthFromProperty = month;
        }

        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = !(monthCodeValue instanceof JSUndefined) && monthCodeValue != null;
        String monthCodeFromProperty = null;
        if (hasMonthCode) {
            IsoMonth parsedMonthCode = parseMonthCodeForYearMonthFrom(context, monthCodeValue);
            if (context.hasPendingException() || parsedMonthCode == null) {
                return JSUndefined.INSTANCE;
            }
            monthCodeFromProperty = TemporalUtils.monthCode(parsedMonthCode.month());
            if (parsedMonthCode.leapMonth()) {
                monthCodeFromProperty = monthCodeFromProperty + "L";
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

        boolean calendarSupportsEras = calendarId.hasEra();
        if (!calendarSupportsEras) {
            if (!hasYear) {
                context.throwTypeError("Temporal error: year argument must be an object.");
                return JSUndefined.INSTANCE;
            }
        } else {
            JSValue eraValue = fields.get(PropertyKey.fromString("era"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            String era = null;
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
            boolean hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
            Integer eraYear = null;
            if (hasEraYear) {
                eraYear = TemporalUtils.toIntegerThrowOnInfinity(context, eraYearValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            if (hasEra != hasEraYear) {
                context.throwTypeError("Temporal error: year argument must be an object.");
                return JSUndefined.INSTANCE;
            }
            if (!hasYear && hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalFieldResolver.getEraByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                year = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                hasYear = true;
            } else if (hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalFieldResolver.getEraByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int expectedYear = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (year != expectedYear) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
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

        if (calendarId == TemporalCalendarId.ISO8601) {
            Integer resolvedIsoMonth = monthFromProperty;
            if (monthCodeFromProperty != null) {
                int parsedMonthCodeMonth = monthNumberFromMonthCode(monthCodeFromProperty);
                boolean parsedMonthCodeLeapMonth = monthCodeFromProperty.length() == 4;
                if (parsedMonthCodeLeapMonth || parsedMonthCodeMonth < 1 || parsedMonthCodeMonth > 12) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
                if (resolvedIsoMonth != null && resolvedIsoMonth != parsedMonthCodeMonth) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
                if (resolvedIsoMonth == null) {
                    resolvedIsoMonth = parsedMonthCodeMonth;
                }
            }
            if (resolvedIsoMonth == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if ("reject".equals(overflow)) {
                if (!isValidIsoYearMonth(year, resolvedIsoMonth)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
                return createPlainYearMonth(context, new IsoDate(year, resolvedIsoMonth, 1), calendarId);
            }
            if (resolvedIsoMonth < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (resolvedIsoMonth > 12) {
                resolvedIsoMonth = 12;
            }
            if (!isValidIsoYearMonth(year, resolvedIsoMonth)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainYearMonth(context, new IsoDate(year, resolvedIsoMonth, 1), calendarId);
        }

        int requestedMonthNumber = monthFromProperty != null
                ? monthFromProperty
                : monthNumberFromMonthCode(monthCodeFromProperty);
        TemporalSupportedYearMonthBoundary supportedYearMonthBoundary = getSupportedYearMonthBoundary(calendarId);
        if (supportedYearMonthBoundary != null
                && requestedMonthNumber != Integer.MIN_VALUE
                && isOutsideSupportedYearMonth(supportedYearMonthBoundary, year, requestedMonthNumber)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoDate convertedIsoDate = TemporalCalendarMath.calendarDateToIsoDate(
                context,
                calendarId,
                year,
                monthFromProperty,
                monthCodeFromProperty,
                1,
                overflow);
        if (!context.hasPendingException() && convertedIsoDate != null) {
            return createPlainYearMonth(context, convertedIsoDate, calendarId);
        }

        JSValue pendingException = context.getPendingException();
        if ("constrain".equals(overflow) && monthCodeFromProperty != null) {
            if ((calendarId == TemporalCalendarId.GREGORY || calendarId == TemporalCalendarId.JAPANESE)
                    && monthFromProperty == null
                    && monthCodeFromProperty.length() == 3) {
                int constrainedIsoMonth = monthNumberFromMonthCode(monthCodeFromProperty);
                if (constrainedIsoMonth >= 1
                        && constrainedIsoMonth <= 12
                        && isValidIsoYearMonth(year, constrainedIsoMonth)) {
                    context.clearPendingException();
                    return createPlainYearMonth(context, new IsoDate(year, constrainedIsoMonth, 1), calendarId);
                }
            }
            context.clearPendingException();
            IsoDate boundaryIsoDate = TemporalCalendarMath.findBoundaryIsoDateForYearMonth(
                    calendarId,
                    year,
                    monthCodeFromProperty);
            if (boundaryIsoDate != null) {
                return createPlainYearMonth(context, boundaryIsoDate, calendarId);
            }
        }
        if (pendingException != null) {
            context.setPendingException(pendingException);
        }
        if (convertedIsoDate == null) {
            return JSUndefined.INSTANCE;
        }
        return createPlainYearMonth(context, convertedIsoDate, calendarId);
    }

    static JSValue yearMonthFromString(JSContext context, String input) {
        IsoDate parsedYearMonthDate = TemporalParser.parseYearMonthString(context, input);
        if (parsedYearMonthDate == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendar = TemporalCalendarId.ISO8601;
        String calendarAnnotation = TemporalUtils.firstCalendarAnnotation(input);
        if (calendarAnnotation != null) {
            calendar = TemporalUtils.validateCalendar(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        int referenceDay = 1;
        if (calendar != TemporalCalendarId.ISO8601) {
            IsoDate parsedDate = TemporalParser.parseDateString(context, input);
            if (parsedDate == null) {
                return JSUndefined.INSTANCE;
            }
            referenceDay = parsedDate.day();
        }

        return createPlainYearMonth(
                context,
                new IsoDate(parsedYearMonthDate.year(), parsedYearMonthDate.month(), referenceDay),
                calendar);
    }

}
