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
        if (!TemporalUtils.requireNewTarget(context, "PlainYearMonth")) {
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

        TemporalCalendarId calendarId = TemporalUtils.calendarArgument(context, args, 2);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
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

    static boolean isValidIsoYearMonth(int year, int month) {
        return IsoDate.isValidYearMonth(year, month);
    }

    public static JSValue toTemporalYearMonth(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainYearMonth plainYearMonth) {
            return JSTemporalPlainYearMonth.create(context, plainYearMonth.getIsoDate(),
                    plainYearMonth.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return TemporalCalendarFields.yearMonthFromFields(context, itemObj, options);
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
