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

import java.util.Locale;

/**
 * Implementation of Temporal.PlainMonthDay prototype methods.
 */
public final class TemporalPlainMonthDayPrototype {
    private static final TemporalReceiver<JSTemporalPlainMonthDay> RECEIVER = new TemporalReceiver<>(
            JSTemporalPlainMonthDay.class, "Temporal.PlainMonthDay");

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
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "day");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields = plainMonthDay.getIsoDate()
                .toIsoCalendarDate(plainMonthDay.getCalendarId());
        return JSNumber.of(calendarDateFields.day());
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "equals");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainMonthDay other = TemporalPlainMonthDayConstructor.toTemporalMonthDayObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = plainMonthDay.getIsoDate().month() == other.getIsoDate().month()
                && plainMonthDay.getIsoDate().day() == other.getIsoDate().day()
                && plainMonthDay.getIsoDate().year() == other.getIsoDate().year()
                && plainMonthDay.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "monthCode");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields = plainMonthDay.getIsoDate()
                .toIsoCalendarDate(plainMonthDay.getCalendarId());
        return new JSString(calendarDateFields.monthCode());
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
        return toStringMethod(context, plainMonthDay, new JSValue[]{JSUndefined.INSTANCE});
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "toLocaleString");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue dateTimeFormat = TemporalLocaleFormat.create(context, args, "timeStyle");
        if (dateTimeFormat == null) {
            return JSUndefined.INSTANCE;
        }
        if (!TemporalLocaleFormat.matchesCalendar(context, dateTimeFormat, plainMonthDay.getCalendarId())) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainMonthDay});
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
        IsoCalendarDate calendarDateFields = plainMonthDay.getIsoDate()
                .toIsoCalendarDate(plainMonthDay.getCalendarId());
        JSObject mergedFields = context.createJSObject();
        mergedFields.set(PropertyKey.fromString("calendar"), new JSString(plainMonthDay.getCalendarId().identifier()));
        mergedFields.set(PropertyKey.fromString("monthCode"), new JSString(calendarDateFields.monthCode()));
        mergedFields.set(PropertyKey.fromString("day"), JSNumber.of(calendarDateFields.day()));
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
        return TemporalPlainDateConstructor.dateFromFields(context, mergedFields, JSUndefined.INSTANCE);
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = RECEIVER.require(context, thisArg, "toString");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainMonthDay.getIsoDate();
        boolean includeReferenceYear;
        if (plainMonthDay.getCalendarId() == TemporalCalendarId.ISO8601) {
            TemporalDisplayCalendar displayCalendar = TemporalDisplayCalendar.fromString(calendarNameOption);
            includeReferenceYear = displayCalendar != null && displayCalendar.requiresAnnotation();
        } else {
            includeReferenceYear = true;
        }
        String result;
        if (includeReferenceYear) {
            result = isoDate.toString();
        } else {
            result = String.format(Locale.ROOT, "%02d-%02d", isoDate.month(), isoDate.day());
        }
        result = TemporalUtils.maybeAppendCalendar(result, plainMonthDay.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError(
                "Do not use Temporal.PlainMonthDay.prototype.valueOf; use Temporal.PlainMonthDay.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
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

        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;
        int dayOfMonth = originalDate.day();
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
        int month = originalDate.month();
        if (hasMonth) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String monthCode = TemporalUtils.getStringOption(context, fields, "monthCode", null);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = monthCode != null;

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = originalDate.year();
        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!hasDay && !hasMonth && !hasMonthCode && !hasYear) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        if (dayOfMonth < 1 || month < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoMonth parsedMonthCode = null;
        if (hasMonthCode) {
            parsedMonthCode = IsoMonth.parseByMonthCode(context, monthCode, "Temporal error: Invalid ISO date.");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String overflow = TemporalUtils.getOverflowOption(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (parsedMonthCode != null) {
            if (parsedMonthCode.month() < 1 || parsedMonthCode.month() > 12) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (parsedMonthCode.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != parsedMonthCode.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            month = parsedMonthCode.month();
        }

        if ("reject".equals(overflow)) {
            if (!new IsoDate(year, month, dayOfMonth).isValid()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return JSTemporalPlainMonthDay.create(context, new IsoDate(1972, month, dayOfMonth),
                    plainMonthDay.getCalendarId());
        }

        int constrainedMonth = Math.max(1, Math.min(12, month));
        int maximumDay = IsoDate.daysInMonth(year, constrainedMonth);
        int constrainedDay = Math.max(1, Math.min(maximumDay, dayOfMonth));
        return JSTemporalPlainMonthDay.create(context, new IsoDate(1972, constrainedMonth, constrainedDay),
                plainMonthDay.getCalendarId());
    }

    private static JSValue withNonIsoCalendar(JSContext context, JSTemporalPlainMonthDay plainMonthDay, JSObject fields,
            JSValue options) {
        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;

        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = !(monthCodeValue instanceof JSUndefined) && monthCodeValue != null;

        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        if (hasMonth) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;

        JSValue eraValue = fields.get(PropertyKey.fromString("era"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;

        JSValue eraYearValue = fields.get(PropertyKey.fromString("eraYear"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;

        if (!hasDay && !hasMonthCode && !hasYear && !hasEra && !hasEraYear) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoCalendarDate calendarDateFields = plainMonthDay.getIsoDate()
                .toIsoCalendarDate(plainMonthDay.getCalendarId());
        JSObject mergedFields = context.createJSObject();
        mergedFields.set(PropertyKey.fromString("calendar"), new JSString(plainMonthDay.getCalendarId().identifier()));
        mergedFields.set(PropertyKey.fromString("monthCode"), new JSString(calendarDateFields.monthCode()));
        mergedFields.set(PropertyKey.fromString("day"), JSNumber.of(calendarDateFields.day()));

        copyFieldIfPresent(context, fields, mergedFields, "day");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        copyFieldIfPresent(context, fields, mergedFields, "monthCode");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        copyFieldIfPresent(context, fields, mergedFields, "year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        copyFieldIfPresent(context, fields, mergedFields, "era");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        copyFieldIfPresent(context, fields, mergedFields, "eraYear");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainMonthDayConstructor.monthDayFromFields(context, mergedFields, options);
    }

}
