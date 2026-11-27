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
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDateArithmetic;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;

import java.math.BigInteger;
import java.time.DateTimeException;

/**
 * Implementation of Temporal.PlainDate prototype methods.
 */
public final class TemporalPlainDatePrototype {
    private static final TemporalDateArithmetic<JSTemporalPlainDate> ARITHMETIC = new TemporalDateArithmetic<>(
            TemporalReceiver.PLAIN_DATE, TemporalDateAddition::apply,
            (ctx, left, right, options, sinceOperation) -> TemporalCalendarDifference.between(ctx, left, right, options,
                    sinceOperation, TemporalUnit.DAY));
    private static final TemporalReceiver<JSTemporalPlainDate> RECEIVER = TemporalReceiver.PLAIN_DATE;

    private TemporalPlainDatePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.shift(context, thisArg, args, 1);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "calendarId",
                plainDate -> new JSString(plainDate.getCalendarId().identifier()));
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAY.read(context, thisArg, RECEIVER);
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAY_OF_WEEK.read(context, thisArg, RECEIVER);
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAY_OF_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_MONTH.read(context, thisArg, RECEIVER);
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInWeek", plainDate -> JSNumber.of(7));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_YEAR.read(context, thisArg, RECEIVER);
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

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.between(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.shift(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "toJSON", plainDate -> new JSString(plainDate.getIsoDate().toString()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalLocaleFormat.formatDate(context, thisArg, args, RECEIVER);
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toPlainDateTime");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoTime time = TemporalPlainTimeConstructor.optionalTime(context, args);
        if (time == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = plainDate.getIsoDate();
        if (!isoDate.isWithinPlainDateTimeRange(time)) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        return JSTemporalPlainDateTime.create(context, isoDate.atTime(time), plainDate.getCalendarId());
    }

    public static JSValue toPlainMonthDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toPlainMonthDay");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields = plainDate.getIsoDate().toIsoCalendarDate(plainDate.getCalendarId());
        JSTemporalPlainMonthDay plainMonthDay = TemporalCalendarFields.createPlainMonthDayFromCalendarMonthDay(context,
                plainDate.getCalendarId(), calendarDateFields.monthCode(), calendarDateFields.day(), "constrain");
        if (context.hasPendingException() || plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        return plainMonthDay;
    }

    public static JSValue toPlainYearMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toPlainYearMonth");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainDate.getIsoDate();
        IsoDate plainYearMonthIsoDate;
        if (plainDate.getCalendarId() == TemporalCalendarId.ISO8601) {
            plainYearMonthIsoDate = new IsoDate(isoDate.year(), isoDate.month(), 1);
        } else {
            plainYearMonthIsoDate = isoDate;
        }
        return JSTemporalPlainYearMonth.create(context, plainYearMonthIsoDate, plainDate.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateFormat.formatDate(context, thisArg, args, RECEIVER);
    }

    public static JSValue toZonedDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toZonedDateTime");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue timeZoneLike = item;
        JSValue plainTimeLike = JSUndefined.INSTANCE;
        if (item instanceof JSObject itemObject) {
            JSValue maybeTimeZone = itemObject.get(PropertyKey.fromString("timeZone"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(maybeTimeZone instanceof JSUndefined) && maybeTimeZone != null) {
                timeZoneLike = maybeTimeZone;
                plainTimeLike = itemObject.get(PropertyKey.fromString("plainTime"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }

        String timeZoneId = TemporalTimeZone.requireTimeZoneIdentifier(context, timeZoneLike);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = plainDate.getIsoDate();
        boolean hasPlainTimeArgument = !(plainTimeLike instanceof JSUndefined) && plainTimeLike != null;
        IsoTime isoTime = IsoTime.MIDNIGHT;
        if (hasPlainTimeArgument) {
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(context, plainTimeLike,
                    JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            isoTime = plainTime.getIsoTime();
            if (!isoDate.isWithinPlainDateTimeRange(isoTime)) {
                return TemporalError.INVALID_ISO_DATE.undefined(context);
            }
        }

        BigInteger epochNanoseconds;
        try {
            if (hasPlainTimeArgument) {
                epochNanoseconds = isoDate.atTime(isoTime).toEpochNs(timeZoneId);
            } else {
                epochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(isoDate, timeZoneId);
            }
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }
        return JSTemporalZonedDateTime.create(context, epochNanoseconds, timeZoneId, plainDate.getCalendarId());
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.between(context, thisArg, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.WEEK_OF_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "with");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fields = TemporalUtils.requirePartialTemporalObject(context, args,
                "Temporal error: Must specify at least one calendar field.");
        if (fields == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = plainDate.getCalendarId();
        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        Integer day = bag.positiveDateField("day");
        Integer month = bag.positiveDateField("month");
        String monthCode = bag.string("monthCode");
        TemporalPartialDate dateFields = bag.readYearAndEra(calendarId, day, month, monthCode);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dateFields.isEmpty()) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }
        return dateFields.merge(context, plainDate, JSValue.argumentOrUndefined(args, 1));
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.withCalendar(context, thisArg, args,
                (ctx, plainDate, calendarId) -> JSTemporalPlainDate.create(ctx, plainDate.getIsoDate(), calendarId));
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR_OF_WEEK.read(context, thisArg, RECEIVER);
    }
}
