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
        JSValue oneArg = JSValue.argumentOrUndefined(args, 0);
        JSValue twoArg = JSValue.argumentOrUndefined(args, 1);

        JSTemporalPlainDate firstDate = toTemporalDateObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate secondDate = toTemporalDateObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(firstDate.getIsoDate().compareTo(secondDate.getIsoDate()));
    }

    /**
     * Temporal.PlainDate(isoYear, isoMonth, isoDay, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDate.");
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
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 2));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate isoDate = new IsoDate(isoYear, isoMonth, isoDay);
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "PlainDate");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, isoDate, calendarId, resolvedPrototype);
    }

    static JSValue dateFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        Integer dayOfMonth = bag.integer("day");
        Integer month = bag.integer("month");
        IsoMonth parsedMonthCode = bag.monthCode();
        Integer year = bag.integer("year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        year = TemporalUtils.resolveEraYear(context, fields, calendarId, year,
                "Temporal error: Date argument must be object or string.");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (year == null || dayOfMonth == null || (month == null && parsedMonthCode == null)) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String monthCodeText = parsedMonthCode == null ? null : parsedMonthCode.monthCode();
        IsoDate convertedIsoDate = IsoDate.calendarDateToIsoDate(context, calendarId, year, month, monthCodeText,
                dayOfMonth, overflow);
        if (context.hasPendingException() || convertedIsoDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, convertedIsoDate, calendarId);
    }

    static JSValue dateFromString(JSContext context, String input) {
        IsoDate date = IsoDate.parseDateString(context, input);
        if (date == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.fromDateAnnotation(context, input);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, date, calendarId);
    }

    /**
     * Temporal.PlainDate.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        return toTemporalDate(context, item, options);
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
            return JSTemporalPlainDate.create(context, plainDate.getIsoDate(), plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainDate.create(context, plainDateTime.getIsoDateTime().date(),
                    plainDateTime.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
            return JSTemporalPlainDate.create(context, localDateTime.date(), zonedDateTime.getCalendarId());
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

}
