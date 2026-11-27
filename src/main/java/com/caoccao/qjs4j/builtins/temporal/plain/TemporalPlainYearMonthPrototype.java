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

import com.caoccao.qjs4j.builtins.temporal.TemporalLocaleFormat;
import com.caoccao.qjs4j.builtins.temporal.TemporalPropertyBag;
import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalCalendarDifference;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDateAddition;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.calendar.IsoMonth;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;

/**
 * Implementation of Temporal.PlainYearMonth prototype methods.
 */
public final class TemporalPlainYearMonthPrototype {
    private static final TemporalReceiver<JSTemporalPlainYearMonth> RECEIVER = TemporalReceiver.PLAIN_YEAR_MONTH;

    private TemporalPlainYearMonthPrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSValue thisArg, JSValue[] args, int sign) {
        return TemporalDateAddition.apply(context, thisArg, args, sign, RECEIVER, TemporalDateAddition::apply);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "calendarId",
                plainYearMonth -> new JSString(plainYearMonth.getCalendarId().identifier()));
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_MONTH.read(context, thisArg, RECEIVER);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_YEAR.read(context, thisArg, RECEIVER);
    }

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        return RECEIVER.difference(context, thisArg, args, since,
                (ctx, left, right, options, sinceOperation) -> TemporalCalendarDifference.between(ctx, left, right,
                        options, sinceOperation, TemporalUnit.MONTH));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.equalTo(context, thisArg, args, JSTemporalCalendarDate::sameDate);
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA.read(context, thisArg, RECEIVER);
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.IN_LEAP_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTH.read(context, thisArg, RECEIVER);
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTH_CODE.read(context, thisArg, RECEIVER);
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTHS_IN_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue referenceISODay(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "referenceISODay",
                plainYearMonth -> JSNumber.of(plainYearMonth.getIsoDate().day()));
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "toJSON");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainYearMonth.getIsoDate().toYearMonthString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalLocaleFormat.formatDate(context, thisArg, args, RECEIVER);
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "toPlainDate");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            return TemporalError.INVALID_YEAR_ARGUMENT.undefined(context);
        }
        int dayOfMonth = TemporalUtils.getIntegerField(context, fields, "day", Integer.MIN_VALUE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dayOfMonth == Integer.MIN_VALUE) {
            return TemporalError.INVALID_YEAR_ARGUMENT.undefined(context);
        }
        IsoCalendarDate calendarDateFields = plainYearMonth.getIsoDate()
                .toIsoCalendarDate(plainYearMonth.getCalendarId());
        JSObject mergedFields = context.createJSObject();
        mergedFields.set(PropertyKey.fromString("calendar"), new JSString(plainYearMonth.getCalendarId().identifier()));
        mergedFields.set(PropertyKey.fromString("year"), JSNumber.of(calendarDateFields.year()));
        mergedFields.set(PropertyKey.fromString("monthCode"), new JSString(calendarDateFields.monthCode()));
        mergedFields.set(PropertyKey.fromString("day"), JSNumber.of(dayOfMonth));
        return TemporalCalendarFields.dateFromFields(context, mergedFields, JSUndefined.INSTANCE);
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateFormat.formatDate(context, thisArg, args, RECEIVER);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "with");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fields = TemporalUtils.requirePartialTemporalObject(context, args,
                TemporalError.MISSING_PARTIAL_FIELDS.message());
        if (fields == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        Integer month = bag.integer("month");
        String monthCode = bag.string("monthCode");
        Integer year = bag.integer("year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = plainYearMonth.getCalendarId();
        boolean calendarSupportsEras = calendarId.hasEra();
        boolean hasEra = false;
        boolean hasEraYear = false;
        String era = null;
        Integer eraYear = null;
        if (calendarSupportsEras) {
            era = TemporalUtils.getStringOption(context, fields, "era", null);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEra = era != null;

            eraYear = TemporalUtils.getOptionalIntegerField(context, fields, "eraYear");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYear = eraYear != null;
        } else if (calendarId != TemporalCalendarId.ISO8601) {
            JSValue eraValue = bag.value("era");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            JSValue eraYearValue = bag.value("eraYear");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
        }

        if (month == null && monthCode == null && year == null && !hasEra && !hasEraYear) {
            return TemporalError.MISSING_PARTIAL_FIELDS.undefined(context);
        }
        if (!calendarSupportsEras && (hasEra || hasEraYear)) {
            return TemporalError.INVALID_YEAR_ARGUMENT.undefined(context);
        }
        if (calendarSupportsEras && hasEra != hasEraYear) {
            return TemporalError.INVALID_YEAR_ARGUMENT.undefined(context);
        }
        JSValue optionsValue = JSValue.argumentOrUndefined(args, 1);
        String overflow = null;
        boolean shouldReadOptionsFirst = optionsValue instanceof JSObject;
        if (shouldReadOptionsFirst) {
            overflow = TemporalUtils.getOverflowOption(context, optionsValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (month != null && month < 1) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }
        IsoMonth monthCodeInfo = null;
        if (monthCode != null) {
            monthCodeInfo = IsoMonth.parseByMonthCode(context, monthCode,
                    TemporalError.MONTH_CODE_OUT_OF_RANGE.message());
            if (context.hasPendingException() || monthCodeInfo == null) {
                return JSUndefined.INSTANCE;
            }
            if (calendarId == TemporalCalendarId.ISO8601 && monthCodeInfo.leapMonth()) {
                return TemporalError.INVALID_ISO_DATE.undefined(context);
            }
            if (month != null && month != monthCodeInfo.month()) {
                return TemporalError.INVALID_ISO_DATE.undefined(context);
            }
        }
        if (!shouldReadOptionsFirst) {
            overflow = TemporalUtils.getOverflowOption(context, optionsValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalPartialDate partialDate = new TemporalPartialDate(null, month,
                monthCodeInfo == null ? null : monthCodeInfo.monthCode(), year, era, eraYear);
        return partialDate.mergeYearMonth(context, plainYearMonth, overflow);
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR.read(context, thisArg, RECEIVER);
    }
}
