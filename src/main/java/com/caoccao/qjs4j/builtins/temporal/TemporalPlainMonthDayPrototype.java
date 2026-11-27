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
 * Implementation of Temporal.PlainMonthDay prototype methods.
 */
public final class TemporalPlainMonthDayPrototype {
    private static final String[] MONTH_DAY_FIELDS = {"day", "monthCode", "year", "era", "eraYear"};

    private static final TemporalReceiver<JSTemporalPlainMonthDay> RECEIVER = TemporalReceiver.PLAIN_MONTH_DAY;

    private TemporalPlainMonthDayPrototype() {
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "calendarId",
                plainMonthDay -> new JSString(plainMonthDay.getCalendarId().identifier()));
    }

    private static void copyFieldIfPresent(JSContext context, JSObject sourceObject, JSObject targetObject,
            String fieldName) {
        JSValue fieldValue = sourceObject.get(PropertyKey.fromString(fieldName));
        if (context.hasPendingException()) {
            return;
        }
        if (!(fieldValue instanceof JSUndefined) && fieldValue != null) {
            targetObject.set(PropertyKey.fromString(fieldName), fieldValue);
        }
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAY.read(context, thisArg, RECEIVER);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.equalTo(context, thisArg, args, JSTemporalCalendarDate::sameDate);
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTH_CODE.read(context, thisArg, RECEIVER);
    }

    private static JSObject monthDayFields(JSContext context, JSTemporalPlainMonthDay plainMonthDay) {
        IsoCalendarDate calendarDateFields = plainMonthDay.getIsoDate()
                .toIsoCalendarDate(plainMonthDay.getCalendarId());
        JSObject mergedFields = context.createJSObject();
        mergedFields.set(PropertyKey.fromString("calendar"), new JSString(plainMonthDay.getCalendarId().identifier()));
        mergedFields.set(PropertyKey.fromString("monthCode"), new JSString(calendarDateFields.monthCode()));
        mergedFields.set(PropertyKey.fromString("day"), JSNumber.of(calendarDateFields.day()));
        return mergedFields;
    }

    public static JSValue referenceISOYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "referenceISOYear",
                plainMonthDay -> JSNumber.of(plainMonthDay.getIsoDate().year()));
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "toJSON");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalDateFormat.format(context, plainMonthDay, JSUndefined.INSTANCE);
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalLocaleFormat.formatDate(context, thisArg, args, RECEIVER);
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "toPlainDate");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        JSObject mergedFields = monthDayFields(context, plainMonthDay);
        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(yearValue instanceof JSUndefined) && yearValue != null) {
            mergedFields.set(PropertyKey.fromString("year"), yearValue);
        } else {
            copyFieldIfPresent(context, fields, mergedFields, "era");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            copyFieldIfPresent(context, fields, mergedFields, "eraYear");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        return TemporalCalendarFields.dateFromFields(context, mergedFields, JSUndefined.INSTANCE);
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateFormat.formatDate(context, thisArg, args, RECEIVER);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "with");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fields = TemporalUtils.requirePartialTemporalObject(context, args,
                "Temporal error: Argument to with() must contain some date/time fields.");
        if (fields == null) {
            return JSUndefined.INSTANCE;
        }

        if (plainMonthDay.getCalendarId() != TemporalCalendarId.ISO8601) {
            return withNonIsoCalendar(context, plainMonthDay, fields, JSValue.argumentOrUndefined(args, 1));
        }

        IsoDate originalDate = plainMonthDay.getIsoDate();

        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        Integer dayField = bag.integer("day");
        Integer monthField = bag.integer("month");
        String monthCode = bag.string("monthCode");
        Integer yearField = bag.integer("year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dayField == null && monthField == null && monthCode == null && yearField == null) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        int year = yearField == null ? originalDate.year() : yearField;
        int dayOfMonth = dayField == null ? originalDate.day() : dayField;
        int month = monthField == null ? originalDate.month() : monthField;
        if (dayOfMonth < 1 || month < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoMonth parsedMonthCode = null;
        if (monthCode != null) {
            parsedMonthCode = IsoMonth.parseByMonthCode(context, monthCode, "Temporal error: Invalid ISO date.");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String overflow = TemporalUtils.getOverflowOption(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        Integer requestedMonth = monthField == null && parsedMonthCode != null ? null : Integer.valueOf(month);
        month = TemporalCalendarFields.resolveIsoMonth(context, requestedMonth, parsedMonthCode, overflow);
        if (month == 0) {
            return JSUndefined.INSTANCE;
        }

        if ("reject".equals(overflow)) {
            if (!new IsoDate(year, month, dayOfMonth).isValid()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainMonthDay.create(context, new IsoDate(1972, month, dayOfMonth),
                    plainMonthDay.getCalendarId());
        }

        int maximumDay = IsoDate.daysInMonth(year, month);
        int constrainedDay = Math.max(1, Math.min(maximumDay, dayOfMonth));
        return JSTemporalPlainMonthDay.create(context, new IsoDate(1972, month, constrainedDay),
                plainMonthDay.getCalendarId());
    }

    private static JSValue withNonIsoCalendar(JSContext context, JSTemporalPlainMonthDay plainMonthDay, JSObject fields,
            JSValue options) {
        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        boolean hasDay = bag.hasDefined("day");
        boolean hasMonthCode = bag.hasDefined("monthCode");
        boolean hasMonth = bag.hasDefined("month");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (hasMonth) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = bag.hasDefined("year");
        boolean hasEra = bag.hasDefined("era");
        boolean hasEraYear = bag.hasDefined("eraYear");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!hasDay && !hasMonthCode && !hasYear && !hasEra && !hasEraYear) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSObject mergedFields = monthDayFields(context, plainMonthDay);

        for (String name : MONTH_DAY_FIELDS) {
            copyFieldIfPresent(context, fields, mergedFields, name);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        return TemporalCalendarFields.monthDayFromFields(context, mergedFields, options);
    }
}
