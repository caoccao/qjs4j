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

import com.caoccao.qjs4j.builtins.temporal.TemporalTimeFields;
import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoMonth;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.iso.IsoCalendarDateTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;

/**
 * Implementation of Temporal.PlainDateTime constructor and static methods.
 */
public final class TemporalPlainDateTimeConstructor {
    private TemporalPlainDateTimeConstructor() {
    }

    /**
     * Temporal.PlainDateTime.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalReceiver.PLAIN_DATE_TIME.compare(context, args,
                (left, right) -> left.getIsoDateTime().compareTo(right.getIsoDateTime()));
    }

    /**
     * Temporal.PlainDateTime(isoYear, isoMonth, isoDay, hour?, minute?, second?, millisecond?, microsecond?,
     * nanosecond?, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!TemporalUtils.requireNewTarget(context, "PlainDateTime")) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = TemporalPlainDateConstructor.readDateArguments(context, args);
        if (isoDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime isoTime = TemporalPlainTimeConstructor.readTimeArguments(context, args, 3);
        if (isoTime == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalUtils.calendarArgument(context, args, 9);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (!isoDate.isValid()) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        if (!isoTime.isValid()) {
            context.throwRangeError("Temporal error: Invalid time");
            return JSUndefined.INSTANCE;
        }

        if (!isoDate.isWithinPlainDateTimeRange(isoTime)) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        IsoDateTime isoDateTime = isoDate.atTime(isoTime);
        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "PlainDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDateTime.create(context, isoDateTime, calendarId, resolvedPrototype);
    }

    static JSValue dateTimeFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalTimeFields bag = new TemporalTimeFields(context, fields);
        Integer dayOfMonth = bag.integer("day");
        bag.readThroughMinute();
        Integer month = bag.integer("month");
        IsoMonth parsedMonthCode = bag.monthCode();
        bag.readNanosecond();
        bag.readSecond();
        Integer year = bag.integer("year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        year = TemporalUtils.resolveEraYear(context, fields, calendarId, year,
                TemporalError.INVALID_DATE_TIME_ARGUMENT.message());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (year == null || dayOfMonth == null || (month == null && parsedMonthCode == null)) {
            return TemporalError.INVALID_DATE_TIME_ARGUMENT.undefined(context);
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String monthCodeText = parsedMonthCode == null ? null : parsedMonthCode.monthCode();
        IsoDate resultDate = IsoDate.calendarDateToIsoDate(context, calendarId, year, month, monthCodeText, dayOfMonth,
                overflow);
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime resultTime = bag.regulate(IsoTime.MIDNIGHT, overflow, "Temporal error: Invalid time");
        if (resultTime == null) {
            return JSUndefined.INSTANCE;
        }

        if (!resultDate.isWithinPlainDateTimeRange(resultTime)) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }
        return JSTemporalPlainDateTime.create(context, resultDate.atTime(resultTime), calendarId);
    }

    static JSValue dateTimeFromString(JSContext context, String input) {
        IsoCalendarDateTime parsed = IsoCalendarDateTime.parseDateTimeString(context, input);
        if (parsed == null) {
            return JSUndefined.INSTANCE;
        }
        if (!parsed.date().isWithinPlainDateTimeRange(parsed.time())) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }
        return JSTemporalPlainDateTime.create(context, parsed.date().atTime(parsed.time()), parsed.calendar());
    }

    /**
     * Temporal.PlainDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        return toTemporalDateTime(context, item, options);
    }

    private static boolean isTemporalPlainDateTimePrototype(JSContext context, JSObject candidate) {
        JSValue temporal = context.getGlobalObject().get(PropertyKey.fromString("Temporal"));
        if (!(temporal instanceof JSObject temporalObject)) {
            return false;
        }
        JSValue constructor = temporalObject.get(PropertyKey.fromString("PlainDateTime"));
        if (!(constructor instanceof JSObject constructorObject)) {
            return false;
        }
        JSValue prototype = constructorObject.get(PropertyKey.PROTOTYPE);
        return prototype == candidate;
    }

    /**
     * ToTemporalDateTime abstract operation.
     */
    public static JSValue toTemporalDateTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            if (!TemporalUtils.validateOverflow(context, options)) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainDateTime.create(context, plainDateTime.getIsoDateTime(),
                    plainDateTime.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDate plainDate) {
            if (!TemporalUtils.validateOverflow(context, options)) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainDateTime.create(context, plainDate.getIsoDate().atMidnight(),
                    plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            if (!TemporalUtils.validateOverflow(context, options)) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
            return JSTemporalPlainDateTime.create(context, localDateTime, zonedDateTime.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            if (isTemporalPlainDateTimePrototype(context, itemObj)) {
                return TemporalError.INVALID_DATE_TIME_ARGUMENT.undefined(context);
            }
            return dateTimeFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            return TemporalUtils.finishStringConversion(context, dateTimeFromString(context, itemStr.value()), options);
        }
        return TemporalError.INVALID_DATE_TIME_ARGUMENT.undefined(context);
    }

    public static JSTemporalPlainDateTime toTemporalDateTimeObject(JSContext context, JSValue item) {
        return TemporalReceiver.PLAIN_DATE_TIME.convert(context, item);
    }

}
