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
 * Implementation of Temporal.PlainDate constructor and static methods.
 */
public final class TemporalPlainDateConstructor {

    private TemporalPlainDateConstructor() {
    }

    /**
     * Temporal.PlainDate.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainDate one = toTemporalDateObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate two = toTemporalDateObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(IsoDate.compareIsoDate(one.getIsoDate(), two.getIsoDate()));
    }

    /**
     * Temporal.PlainDate(isoYear, isoMonth, isoDay, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDate.");
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
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 2 ? args[2] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String calendarId = "iso8601";
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!IsoDate.isValidIsoDate(isoYear, isoMonth, isoDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = resolveTemporalPrototype(context, "PlainDate");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDate(context, new IsoDate(isoYear, isoMonth, isoDay), calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainDate createPlainDate(JSContext context, IsoDate isoDate, String calendarId) {
        JSObject prototype = getTemporalPrototype(context, "PlainDate");
        return createPlainDate(context, isoDate, calendarId, prototype);
    }

    static JSTemporalPlainDate createPlainDate(JSContext context, IsoDate isoDate, String calendarId, JSObject prototype) {
        JSTemporalPlainDate plainDate = new JSTemporalPlainDate(context, isoDate, calendarId);
        if (prototype != null) {
            plainDate.setPrototype(prototype);
        }
        return plainDate;
    }

    static JSValue dateFromFields(JSContext context, JSObject fields, JSValue options) {
        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        int year = TemporalUtils.getIntegerField(context, fields, "year", Integer.MIN_VALUE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (year == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        // Try month or monthCode
        int month;
        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (monthValue instanceof JSUndefined || monthValue == null) {
            if (monthCodeValue instanceof JSString monthCodeStr) {
                month = parseMonthCode(context, monthCodeStr.value());
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            } else {
                context.throwTypeError("Temporal error: Date argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
        } else {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        int day = TemporalUtils.getIntegerField(context, fields, "day", Integer.MIN_VALUE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (day == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        String calendarId = "iso8601";
        if (calendarValue instanceof JSString calStr) {
            calendarId = calStr.value().toLowerCase(java.util.Locale.ROOT);
        }

        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return createPlainDate(context, new IsoDate(year, month, day), calendarId);
        } else {
            IsoDate constrained = TemporalUtils.constrainIsoDate(year, month, day);
            return createPlainDate(context, constrained, calendarId);
        }
    }

    static JSValue dateFromString(JSContext context, String input) {
        IsoDate date = TemporalParser.parseDateString(context, input);
        if (date == null) {
            return JSUndefined.INSTANCE;
        }
        // Check for calendar annotation
        String calendar = "iso8601";
        int calIdx = input.indexOf("[u-ca=");
        if (calIdx >= 0) {
            int endIdx = input.indexOf(']', calIdx);
            if (endIdx > calIdx) {
                calendar = input.substring(calIdx + 6, endIdx).toLowerCase(java.util.Locale.ROOT);
            }
        }
        return createPlainDate(context, date, calendar);
    }

    /**
     * Temporal.PlainDate.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalDate(context, item, options);
    }

    static JSObject getTemporalPrototype(JSContext context, String typeName) {
        JSValue temporal = context.getGlobalObject().get(PropertyKey.fromString("Temporal"));
        if (temporal instanceof JSObject temporalObj) {
            JSValue constructor = temporalObj.get(PropertyKey.fromString(typeName));
            if (constructor instanceof JSObject constructorObj) {
                JSValue prototype = constructorObj.get(PropertyKey.PROTOTYPE);
                if (prototype instanceof JSObject prototypeObj) {
                    return prototypeObj;
                }
            }
        }
        return null;
    }

    static int parseMonthCode(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() != 3 || monthCode.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Month code out of range.");
            return 0;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return 0;
        }
        int month = Integer.parseInt(monthCode.substring(1));
        if (month < 1 || month > 12) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return 0;
        }
        return month;
    }

    static JSObject resolveTemporalPrototype(JSContext context, String typeName) {
        JSValue constructorNewTarget = context.getConstructorNewTarget();
        if (constructorNewTarget instanceof JSObject constructorObject) {
            JSValue constructorPrototype = constructorObject.get(PropertyKey.PROTOTYPE);
            if (context.hasPendingException()) {
                return null;
            }
            if (constructorPrototype instanceof JSObject) {
                JSObject resolvedPrototype = context.getPrototypeFromConstructor(constructorObject, JSObject.NAME);
                if (context.hasPendingException()) {
                    return null;
                }
                return resolvedPrototype;
            }
        }
        return getTemporalPrototype(context, typeName);
    }

    /**
     * ToTemporalDate abstract operation — converts item to JSTemporalPlainDate.
     */
    public static JSValue toTemporalDate(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDate plainDate) {
            return createPlainDate(context, plainDate.getIsoDate(), plainDate.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            return dateFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            return dateFromString(context, itemStr.value());
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
