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

package com.caoccao.qjs4j.builtins.temporal.plain;

import com.caoccao.qjs4j.builtins.temporal.TemporalPropertyBag;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.calendar.IsoMonth;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalSupportedYearMonthBoundary;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;

/** Resolves calendar date, month-day and year-month fields supplied to Temporal constructors. */
final class TemporalCalendarFields {
    private TemporalCalendarFields() {
    }

    static JSValue constructPartialDate(JSContext context, JSValue[] args, int referenceDefault,
            PartialDateFactory factory) {
        if (!TemporalUtils.requireNewTarget(context, "PlainYearMonth")) {
            return JSUndefined.INSTANCE;
        }

        int first = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int second = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalUtils.calendarArgument(context, args, 2);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int reference = TemporalUtils.optionalIntegerArgument(context, args, 3, referenceDefault);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return factory.create(context, first, second, calendarId, reference);
    }

    static JSTemporalPlainMonthDay createPlainMonthDayFromCalendarMonthDay(JSContext context,
            TemporalCalendarId calendarId, String monthCode, int dayOfMonth, String overflow) {
        IsoDate referenceIsoDate = IsoDate.resolveReferenceIsoDateForMonthDay(context, calendarId, monthCode,
                dayOfMonth, overflow);
        if (context.hasPendingException() || referenceIsoDate == null) {
            return null;
        }
        return JSTemporalPlainMonthDay.create(context, referenceIsoDate, calendarId);
    }

    static JSValue dateFromFields(JSContext context, JSObject fields, JSValue options) {
        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        TemporalCalendarId calendarId = bag.calendar();
        if (calendarId == null) {
            return JSUndefined.INSTANCE;
        }

        Integer dayOfMonth = bag.integerByKey(PropertyKey.DAY);
        Integer month = bag.integerByKey(PropertyKey.MONTH);
        IsoMonth parsedMonthCode = bag.monthCode();
        Integer year = bag.resolvedYear(calendarId, TemporalError.INVALID_DATE_ARGUMENT.message());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String overflow = requiredDateOverflow(context, options, year, month, parsedMonthCode, dayOfMonth,
                TemporalError.INVALID_DATE_ARGUMENT);
        if (overflow == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate convertedIsoDate = toIsoDate(context, calendarId, year, month, parsedMonthCode, dayOfMonth, overflow);
        if (context.hasPendingException() || convertedIsoDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, convertedIsoDate, calendarId);
    }

    static JSValue monthDayFromFields(JSContext context, JSObject fields, JSValue options) {
        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        TemporalCalendarId calendarId = bag.calendar();
        if (calendarId == null) {
            return JSUndefined.INSTANCE;
        }

        Integer dayOfMonth = bag.integerByKey(PropertyKey.DAY);
        Integer month = bag.integerByKey(PropertyKey.MONTH);
        String monthCode = bag.stringByKey(PropertyKey.MONTH_CODE);
        IsoMonth parsedMonthCode = monthCode == null
                ? null
                : IsoMonth.parseByMonthCode(context, monthCode, TemporalError.INVALID_ISO_DATE.message());
        Integer resolvedYear = bag.resolvedYear(calendarId, TemporalError.INVALID_MONTH_DAY_ARGUMENT.message());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dayOfMonth == null || (month == null && parsedMonthCode == null)
                || (resolvedYear == null && month != null && calendarId != TemporalCalendarId.ISO8601)) {
            return TemporalError.INVALID_MONTH_DAY_ARGUMENT.undefined(context);
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String code = parsedMonthCode == null ? null : parsedMonthCode.monthCode();
        int day = dayOfMonth;
        if (resolvedYear != null || month != null) {
            int year = resolvedYear == null ? 1972 : resolvedYear;
            if (calendarId == TemporalCalendarId.ISO8601) {
                int isoMonth = resolveIsoMonth(context, month, parsedMonthCode, overflow);
                if (isoMonth == 0) {
                    return JSUndefined.INSTANCE;
                }
                day = IsoDate.regulateDay(context, day, IsoDate.daysInMonth(year, isoMonth), overflow);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                code = IsoMonth.toMonthCode(isoMonth);
            } else {
                IsoDate date = IsoDate.calendarDateToIsoDate(context, calendarId, year, month, code, day, overflow);
                if (date == null) {
                    return JSUndefined.INSTANCE;
                }
                IsoCalendarDate converted = date.toIsoCalendarDate(calendarId);
                code = converted.monthCode();
                day = converted.day();
            }
        }
        JSTemporalPlainMonthDay result = createPlainMonthDayFromCalendarMonthDay(context, calendarId, code, day,
                overflow);
        return result == null ? JSUndefined.INSTANCE : result;
    }

    static String requiredDateOverflow(JSContext context, JSValue options, Integer year, Integer month,
            IsoMonth monthCode, Integer day, TemporalError invalidDate) {
        if (year == null || day == null || (month == null && monthCode == null)) {
            return invalidDate.reject(context);
        }
        String overflow = TemporalUtils.getOverflowOption(context, options);
        return context.hasPendingException() ? null : overflow;
    }

    static int resolveIsoMonth(JSContext context, Integer month, IsoMonth code, String overflow) {
        if (code != null) {
            if (code.leapMonth() || code.month() < 1 || code.month() > 12 || (month != null && month != code.month())) {
                TemporalError.INVALID_ISO_DATE.raise(context);
                return 0;
            }
            return code.month();
        }
        if (month == null || month < 1 || (month > 12 && "reject".equals(overflow))) {
            TemporalError.INVALID_ISO_DATE.raise(context);
            return 0;
        }
        return Math.min(month, 12);
    }

    static IsoDate toIsoDate(JSContext context, TemporalCalendarId calendar, int year, Integer month,
            IsoMonth monthCode, int day, String overflow) {
        return IsoDate.calendarDateToIsoDate(context, calendar, year, month,
                monthCode == null ? null : monthCode.monthCode(), day, overflow);
    }

    static JSValue yearMonth(JSContext context, TemporalCalendarId calendarId, int year, Integer monthFromProperty,
            IsoMonth parsedMonthCode, String overflow) {
        String monthCodeFromProperty = parsedMonthCode == null ? null : parsedMonthCode.monthCode();
        if (calendarId == TemporalCalendarId.ISO8601) {
            int resolvedIsoMonth = resolveIsoMonth(context, monthFromProperty, parsedMonthCode, overflow);
            if (resolvedIsoMonth == 0) {
                return JSUndefined.INSTANCE;
            }
            if (!IsoDate.isValidYearMonth(year, resolvedIsoMonth)) {
                return TemporalError.INVALID_ISO_DATE.undefined(context);
            }
            return JSTemporalPlainYearMonth.create(context, new IsoDate(year, resolvedIsoMonth, 1), calendarId);
        }

        int requestedMonthNumber = monthFromProperty != null ? monthFromProperty : parsedMonthCode.month();
        TemporalSupportedYearMonthBoundary supportedYearMonthBoundary = TemporalSupportedYearMonthBoundary
                .forCalendar(calendarId);
        if (supportedYearMonthBoundary != null && requestedMonthNumber != Integer.MIN_VALUE
                && supportedYearMonthBoundary.excludes(year, requestedMonthNumber)) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        IsoDate convertedIsoDate = IsoDate.calendarDateToIsoDate(context, calendarId, year, monthFromProperty,
                monthCodeFromProperty, 1, overflow);
        if (!context.hasPendingException() && convertedIsoDate != null) {
            return JSTemporalPlainYearMonth.create(context, convertedIsoDate, calendarId);
        }

        JSValue pendingException = context.getPendingException();
        if ("constrain".equals(overflow) && monthCodeFromProperty != null) {
            if ((calendarId == TemporalCalendarId.GREGORY || calendarId == TemporalCalendarId.JAPANESE)
                    && monthFromProperty == null && monthCodeFromProperty.length() == 3) {
                int constrainedIsoMonth = parsedMonthCode.month();
                if (constrainedIsoMonth >= 1 && constrainedIsoMonth <= 12
                        && IsoDate.isValidYearMonth(year, constrainedIsoMonth)) {
                    context.clearPendingException();
                    return JSTemporalPlainYearMonth.create(context, new IsoDate(year, constrainedIsoMonth, 1),
                            calendarId);
                }
            }
            context.clearPendingException();
            IsoDate boundaryIsoDate = calendarId.findBoundaryIsoDateForYearMonth(year, monthCodeFromProperty);
            if (boundaryIsoDate != null) {
                return JSTemporalPlainYearMonth.create(context, boundaryIsoDate, calendarId);
            }
        }
        if (pendingException != null) {
            context.setPendingException(pendingException);
        }
        if (convertedIsoDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainYearMonth.create(context, convertedIsoDate, calendarId);
    }

    static JSValue yearMonthFromFields(JSContext context, JSObject fields, JSValue options) {
        JSObject plainYearMonthPrototype = TemporalUtils.getTemporalPrototype(context, "PlainYearMonth");
        if (fields == plainYearMonthPrototype) {
            return TemporalError.INVALID_YEAR_ARGUMENT.undefined(context);
        }

        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        TemporalCalendarId calendarId = bag.calendar();
        if (calendarId == null) {
            return JSUndefined.INSTANCE;
        }

        Integer monthFromProperty = bag.integerByKey(PropertyKey.MONTH);
        IsoMonth parsedMonthCode = bag.monthCode();
        Integer year = bag.resolvedYear(calendarId, TemporalError.INVALID_YEAR_ARGUMENT.message());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (year == null || (monthFromProperty == null && parsedMonthCode == null)) {
            return TemporalError.INVALID_YEAR_ARGUMENT.undefined(context);
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return yearMonth(context, calendarId, year, monthFromProperty, parsedMonthCode, overflow);
    }

    @FunctionalInterface
    interface PartialDateFactory {
        JSValue create(JSContext context, int first, int second, TemporalCalendarId calendar, int reference);
    }
}
