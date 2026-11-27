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
        return TemporalReceiver.PLAIN_DATE.compare(context, args,
                (left, right) -> left.getIsoDate().compareTo(right.getIsoDate()));
    }

    /**
     * Temporal.PlainDate(isoYear, isoMonth, isoDay, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!TemporalUtils.requireNewTarget(context, "PlainDate")) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = readDateArguments(context, args);
        if (isoDate == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalUtils.calendarArgument(context, args, 3);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

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

    static IsoDate readDateArguments(JSContext context, JSValue[] args) {
        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return null;
        }
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return null;
        }
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, JSValue.argumentOrUndefined(args, 2));
        if (context.hasPendingException()) {
            return null;
        }
        return new IsoDate(isoYear, isoMonth, isoDay);
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
            return TemporalCalendarFields.dateFromFields(context, itemObj, options);
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
        return TemporalReceiver.PLAIN_DATE.convert(context, item);
    }
}
