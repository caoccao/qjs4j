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

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;

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
        if (!TemporalUtils.requireNewTarget(context, "PlainYearMonth")) {
            return JSUndefined.INSTANCE;
        }

        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalUtils.calendarArgument(context, args, 2);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int referenceYear = TemporalUtils.optionalIntegerArgument(context, args, 3, DEFAULT_REFERENCE_ISO_YEAR);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = new IsoDate(referenceYear, isoMonth, isoDay);
        if (!isoDate.isValid()) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "PlainMonthDay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainMonthDay.create(context, isoDate, calendarId, resolvedPrototype);
    }

    /**
     * Temporal.PlainMonthDay.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        return toTemporalMonthDay(context, item, options);
    }

    static JSValue monthDayFromString(JSContext context, String input) {
        TemporalCalendarId calendar = TemporalCalendarId.fromDateAnnotation(context, input);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate parsedDate = IsoDate.parseDateString(context, input);
        if (parsedDate != null && !context.hasPendingException()) {
            if (calendar == TemporalCalendarId.ISO8601) {
                return JSTemporalPlainMonthDay.create(context,
                        new IsoDate(DEFAULT_REFERENCE_ISO_YEAR, parsedDate.month(), parsedDate.day()), calendar);
            }
            IsoCalendarDate calendarDateFields = parsedDate.toIsoCalendarDate(calendar);
            JSTemporalPlainMonthDay plainMonthDay = TemporalCalendarFields.createPlainMonthDayFromCalendarMonthDay(
                    context, calendar, calendarDateFields.monthCode(), calendarDateFields.day(), "constrain");
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
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        return JSTemporalPlainMonthDay.create(context, parsedMonthDay, calendar);
    }

    public static JSValue toTemporalMonthDay(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainMonthDay plainMonthDay) {
            if (!TemporalUtils.validateOverflow(context, options)) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainMonthDay.create(context, plainMonthDay.getIsoDate(), plainMonthDay.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            JSObject plainMonthDayPrototype = TemporalUtils.getTemporalPrototype(context, "PlainMonthDay");
            if (itemObj == plainMonthDayPrototype) {
                return TemporalError.INVALID_MONTH_DAY_ARGUMENT.undefined(context);
            }
            return TemporalCalendarFields.monthDayFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            return TemporalUtils.finishStringConversion(context, monthDayFromString(context, itemStr.value()), options);
        }
        return TemporalError.INVALID_MONTH_DAY_ARGUMENT.undefined(context);
    }

    public static JSTemporalPlainMonthDay toTemporalMonthDayObject(JSContext context, JSValue item) {
        return TemporalReceiver.PLAIN_MONTH_DAY.convert(context, item);
    }

}
