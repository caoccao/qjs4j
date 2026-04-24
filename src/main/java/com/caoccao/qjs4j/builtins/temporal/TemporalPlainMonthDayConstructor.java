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
 * Implementation of Temporal.PlainMonthDay constructor and static methods.
 */
public final class TemporalPlainMonthDayConstructor {
    private static final int DEFAULT_REFERENCE_ISO_YEAR = 1972;

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
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        int referenceYear = DEFAULT_REFERENCE_ISO_YEAR;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            referenceYear = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate isoDate = new IsoDate(referenceYear, isoMonth, isoDay);
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "PlainMonthDay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainMonthDay.create(context, isoDate, calendarId, resolvedPrototype);
    }

    static JSTemporalPlainMonthDay createPlainMonthDayFromCalendarMonthDay(
            JSContext context,
            TemporalCalendarId calendarId,
            String monthCode,
            int dayOfMonth,
            String overflow) {
        IsoDate referenceIsoDate = IsoDate.resolveReferenceIsoDateForMonthDay(
                context,
                calendarId,
                monthCode,
                dayOfMonth,
                overflow);
        if (context.hasPendingException() || referenceIsoDate == null) {
            return null;
        }
        return JSTemporalPlainMonthDay.create(context, referenceIsoDate, calendarId);
    }

    private static JSTemporalPlainMonthDay createResolvedMonthDay(
            JSContext context,
            TemporalCalendarId calendarId,
            String monthCode,
            int dayOfMonth,
            String overflow) {
        JSTemporalPlainMonthDay resolvedMonthDay = createPlainMonthDayFromCalendarMonthDay(
                context,
                calendarId,
                monthCode,
                dayOfMonth,
                overflow);
        if (context.hasPendingException() || resolvedMonthDay == null) {
            return null;
        }
        return resolvedMonthDay;
    }

    private static String formatMonthCode(IsoMonth parsedMonthCode) {
        String formattedMonthCode = IsoMonth.toMonthCode(parsedMonthCode.month());
        if (parsedMonthCode.leapMonth()) {
            formattedMonthCode = formattedMonthCode + "L";
        }
        return formattedMonthCode;
    }

    /**
     * Temporal.PlainMonthDay.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalMonthDay(context, item, options);
    }

    private static boolean isIsoLeapYearWithoutRangeCheck(int year) {
        if (Math.floorMod(year, 4) != 0) {
            return false;
        }
        if (Math.floorMod(year, 100) != 0) {
            return true;
        }
        return Math.floorMod(year, 400) == 0;
    }

    private static int isoDaysInMonthWithoutRangeCheck(int year, int month) {
        if (month == 2) {
            if (isIsoLeapYearWithoutRangeCheck(year)) {
                return 29;
            }
            return 28;
        }
        if (month == 4 || month == 6 || month == 9 || month == 11) {
            return 30;
        }
        return 31;
    }

    static JSValue monthDayFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
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
        IsoMonth parsedMonthCode = null;
        if (hasMonthCode) {
            monthCode = JSTypeConversions.toString(context, monthCodeValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            parsedMonthCode = IsoMonth.parseByMonthCode(
                    context,
                    monthCode,
                    "Temporal error: Invalid ISO date.");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYearFromProperty = !(yearValue instanceof JSUndefined) && yearValue != null;
        Integer resolvedYear = null;
        if (hasYearFromProperty) {
            resolvedYear = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        boolean calendarSupportsEras = calendarId.hasEra();
        if (calendarSupportsEras) {
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
                context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            if (resolvedYear == null && hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                resolvedYear = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            } else if (hasEra && hasEraYear) {
                TemporalEra canonicalEra = TemporalEra.createByCalendarId(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int expectedYear = calendarId.getEraYearFromEra(canonicalEra, eraYear);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!resolvedYear.equals(expectedYear)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
            }
        }

        if (!hasDay) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (!hasMonth && !hasMonthCode) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        if (resolvedYear == null && hasMonth && calendarId != TemporalCalendarId.ISO8601) {
            context.throwTypeError("Temporal error: MonthDay argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String resolvedMonthCode;
        int resolvedDay;
        if (resolvedYear != null || hasMonth) {
            if (calendarId == TemporalCalendarId.ISO8601) {
                TemporalResolvedMonthDay resolvedIsoMonthDay = resolveIsoMonthDay(
                        context,
                        resolvedYear != null ? resolvedYear : DEFAULT_REFERENCE_ISO_YEAR,
                        hasMonth ? month : null,
                        parsedMonthCode,
                        dayOfMonth,
                        overflow);
                if (context.hasPendingException() || resolvedIsoMonthDay == null) {
                    return JSUndefined.INSTANCE;
                }
                resolvedMonthCode = resolvedIsoMonthDay.monthCode();
                resolvedDay = resolvedIsoMonthDay.dayOfMonth();
                JSTemporalPlainMonthDay resolvedMonthDay = createResolvedMonthDay(
                        context,
                        calendarId,
                        resolvedMonthCode,
                        resolvedDay,
                        overflow);
                if (resolvedMonthDay == null) {
                    return JSUndefined.INSTANCE;
                }
                return resolvedMonthDay;
            }

            int resolutionYear = resolvedYear != null ? resolvedYear : DEFAULT_REFERENCE_ISO_YEAR;
            Integer monthFromProperty = null;
            if (hasMonth) {
                monthFromProperty = month;
            }
            String monthCodeFromProperty = null;
            if (parsedMonthCode != null) {
                monthCodeFromProperty = formatMonthCode(parsedMonthCode);
            }
            IsoDate resolvedIsoDate = IsoDate.calendarDateToIsoDate(
                    context,
                    calendarId,
                    resolutionYear,
                    monthFromProperty,
                    monthCodeFromProperty,
                    dayOfMonth,
                    overflow);
            if (context.hasPendingException() || resolvedIsoDate == null) {
                return JSUndefined.INSTANCE;
            }
            IsoCalendarDate resolvedCalendarDateFields = resolvedIsoDate.toIsoCalendarDate(calendarId);
            resolvedMonthCode = resolvedCalendarDateFields.monthCode();
            resolvedDay = resolvedCalendarDateFields.day();
        } else {
            resolvedMonthCode = formatMonthCode(parsedMonthCode);
            resolvedDay = dayOfMonth;
        }

        JSTemporalPlainMonthDay resolvedMonthDay = createResolvedMonthDay(
                context,
                calendarId,
                resolvedMonthCode,
                resolvedDay,
                overflow);
        if (resolvedMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        return resolvedMonthDay;
    }

    static JSValue monthDayFromString(JSContext context, String input) {
        TemporalCalendarId calendar = TemporalCalendarId.ISO8601;
        String calendarAnnotation = TemporalUtils.firstCalendarAnnotation(input);
        if (calendarAnnotation != null) {
            calendar = TemporalCalendarId.createFromCalendarString(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate parsedDate = IsoDate.parseDateString(context, input);
        if (parsedDate != null && !context.hasPendingException()) {
            if (calendar == TemporalCalendarId.ISO8601) {
                return JSTemporalPlainMonthDay.create(
                        context,
                        new IsoDate(DEFAULT_REFERENCE_ISO_YEAR, parsedDate.month(), parsedDate.day()),
                        calendar);
            }
            IsoCalendarDate calendarDateFields = parsedDate.toIsoCalendarDate(calendar);
            JSTemporalPlainMonthDay plainMonthDay = createPlainMonthDayFromCalendarMonthDay(
                    context,
                    calendar,
                    calendarDateFields.monthCode(),
                    calendarDateFields.day(),
                    "constrain");
            if (context.hasPendingException() || plainMonthDay == null) {
                return JSUndefined.INSTANCE;
            }
            return plainMonthDay;
        }
        context.clearPendingException();

        IsoDate parsedMonthDay = IsoDate.parseMonthDayString(context, input);
        if (parsedMonthDay == null) {
            return JSUndefined.INSTANCE;
        }

        if (calendar != TemporalCalendarId.ISO8601) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalPlainMonthDay.create(context, parsedMonthDay, calendar);
    }

    private static TemporalResolvedMonthDay resolveIsoMonthDay(
            JSContext context,
            int year,
            Integer monthFromProperty,
            IsoMonth monthCodeFromProperty,
            int dayOfMonth,
            String overflow) {
        if (dayOfMonth < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        Integer resolvedMonth = monthFromProperty;
        if (monthCodeFromProperty != null) {
            if (monthCodeFromProperty.month() < 1 || monthCodeFromProperty.month() > 12 || monthCodeFromProperty.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (resolvedMonth != null && resolvedMonth != monthCodeFromProperty.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (resolvedMonth == null) {
                resolvedMonth = monthCodeFromProperty.month();
            }
        }
        if (resolvedMonth == null) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int constrainedMonth = resolvedMonth;
        if ("reject".equals(overflow)) {
            if (constrainedMonth < 1 || constrainedMonth > 12) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            if (constrainedMonth < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (constrainedMonth > 12) {
                constrainedMonth = 12;
            }
        }

        int maximumDay = isoDaysInMonthWithoutRangeCheck(year, constrainedMonth);
        int constrainedDay = dayOfMonth;
        if ("reject".equals(overflow)) {
            if (constrainedDay > maximumDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else if (constrainedDay > maximumDay) {
            constrainedDay = maximumDay;
        }
        return new TemporalResolvedMonthDay(IsoMonth.toMonthCode(constrainedMonth), constrainedDay);
    }

    public static JSValue toTemporalMonthDay(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainMonthDay plainMonthDay) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainMonthDay.create(context, plainMonthDay.getIsoDate(), plainMonthDay.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            JSObject plainMonthDayPrototype = TemporalUtils.getTemporalPrototype(context, "PlainMonthDay");
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
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainMonthDay) result;
    }

}
