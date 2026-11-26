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
        JSValue oneArg = JSValue.argumentOrUndefined(args, 0);
        JSValue twoArg = JSValue.argumentOrUndefined(args, 1);

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

        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 1));
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

        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "PlainYearMonth");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainYearMonth.create(context, new IsoDate(isoYear, isoMonth, referenceDay), calendarId,
                resolvedPrototype);
    }

    /**
     * Temporal.PlainYearMonth.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        if (item instanceof JSTemporalPlainYearMonth plainYearMonth) {
            String overflow = TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException() || overflow == null) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainYearMonth.create(context, plainYearMonth.getIsoDate(),
                    plainYearMonth.getCalendarId());
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

    private static boolean isOutsideSupportedYearMonth(TemporalSupportedYearMonthBoundary supportedYearMonthBoundary,
            int year, int month) {
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

    public static JSValue toTemporalYearMonth(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainYearMonth plainYearMonth) {
            return JSTemporalPlainYearMonth.create(context, plainYearMonth.getIsoDate(),
                    plainYearMonth.getCalendarId());
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
        JSObject plainYearMonthPrototype = TemporalUtils.getTemporalPrototype(context, "PlainYearMonth");
        if (fields == plainYearMonthPrototype) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        Integer monthFromProperty = bag.integer("month");
        IsoMonth parsedMonthCode = bag.monthCode();
        String monthCodeFromProperty = parsedMonthCode == null ? null : parsedMonthCode.monthCode();
        Integer year = bag.integer("year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        year = TemporalUtils.resolveEraYear(context, fields, calendarId, year,
                "Temporal error: year argument must be an object.");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (year == null || (monthFromProperty == null && parsedMonthCode == null)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (calendarId == TemporalCalendarId.ISO8601) {
            Integer resolvedIsoMonth = monthFromProperty;
            if (parsedMonthCode != null) {
                int parsedMonthCodeMonth = parsedMonthCode.month();
                if (parsedMonthCode.leapMonth() || parsedMonthCodeMonth < 1 || parsedMonthCodeMonth > 12) {
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
            if (!"reject".equals(overflow) && resolvedIsoMonth > 12) {
                resolvedIsoMonth = 12;
            }
            if (!isValidIsoYearMonth(year, resolvedIsoMonth)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainYearMonth.create(context, new IsoDate(year, resolvedIsoMonth, 1), calendarId);
        }

        int requestedMonthNumber = monthFromProperty != null ? monthFromProperty : parsedMonthCode.month();
        TemporalSupportedYearMonthBoundary supportedYearMonthBoundary = getSupportedYearMonthBoundary(calendarId);
        if (supportedYearMonthBoundary != null && requestedMonthNumber != Integer.MIN_VALUE
                && isOutsideSupportedYearMonth(supportedYearMonthBoundary, year, requestedMonthNumber)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
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
                        && isValidIsoYearMonth(year, constrainedIsoMonth)) {
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

    static JSValue yearMonthFromString(JSContext context, String input) {
        IsoDate parsedYearMonthDate = IsoDate.parseYearMonthString(context, input);
        if (parsedYearMonthDate == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendar = TemporalCalendarId.fromDateAnnotation(context, input);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int referenceDay = 1;
        if (calendar != TemporalCalendarId.ISO8601) {
            IsoDate parsedDate = IsoDate.parseDateString(context, input);
            if (parsedDate == null) {
                return JSUndefined.INSTANCE;
            }
            referenceDay = parsedDate.day();
        }

        return JSTemporalPlainYearMonth.create(context,
                new IsoDate(parsedYearMonthDate.year(), parsedYearMonthDate.month(), referenceDay), calendar);
    }

}
