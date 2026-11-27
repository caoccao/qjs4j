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
import com.caoccao.qjs4j.builtins.temporal.TemporalTimeFields;
import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalCalendarDifference;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDateAddition;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDateArithmetic;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.format.TemporalPlainDateTimeToStringSettings;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundSettings;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalZonedDateTimeOptions;

import java.math.BigInteger;
import java.time.DateTimeException;

/**
 * Implementation of Temporal.PlainDateTime prototype methods.
 */
public final class TemporalPlainDateTimePrototype {
    private static final TemporalDateArithmetic<JSTemporalPlainDateTime> ARITHMETIC = new TemporalDateArithmetic<>(
            TemporalReceiver.PLAIN_DATE_TIME, TemporalDateAddition::apply, TemporalCalendarDifference::between);
    private static final TemporalReceiver<JSTemporalPlainDateTime> RECEIVER = TemporalReceiver.PLAIN_DATE_TIME;

    private TemporalPlainDateTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.shift(context, thisArg, args, 1);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.calendarId(context, thisArg, RECEIVER);
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
        return TemporalDateProperty.daysInWeek(context, thisArg, RECEIVER);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.equalTo(context, thisArg, args,
                (left, right) -> left.getIsoDateTime().compareTo(right.getIsoDateTime()) == 0
                        && left.getCalendarId() == right.getCalendarId());
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA.read(context, thisArg, RECEIVER);
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.HOUR.read(context, thisArg, RECEIVER);
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.IN_LEAP_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MICROSECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MILLISECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MINUTE.read(context, thisArg, RECEIVER);
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

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.NANOSECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "round");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalRoundSettings roundSettings = TemporalRoundSettings.parseRoundTo(context, args, TemporalUnit.DAY);
        if (context.hasPendingException() || roundSettings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime rounded = roundSettings.roundDateTime(plainDateTime.getIsoDateTime());
        if (!rounded.date().isWithinPlainDateTimeRange(rounded.time())) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        return JSTemporalPlainDateTime.create(context, rounded, plainDateTime.getCalendarId());
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.SECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.between(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.shift(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toJSON");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        String formattedString = toTemporalPlainDateTimeString(context, plainDateTime,
                TemporalPlainDateTimeToStringSettings.DEFAULT);
        if (context.hasPendingException() || formattedString == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(formattedString);
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toLocaleString");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalLocaleFormat.formatDateTime(context, plainDateTime, args, "all");
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toPlainDate");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, plainDateTime.getIsoDateTime().date(),
                plainDateTime.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toPlainTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainTime.create(context, plainDateTime.getIsoDateTime().time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toString");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = JSValue.argumentOrUndefined(args, 0);
        TemporalPlainDateTimeToStringSettings toStringSettings = TemporalPlainDateTimeToStringSettings.read(context,
                optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        String formattedString = toTemporalPlainDateTimeString(context, plainDateTime, toStringSettings);
        if (context.hasPendingException() || formattedString == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(formattedString);
    }

    private static String toTemporalPlainDateTimeString(JSContext context, JSTemporalPlainDateTime plainDateTime,
            TemporalPlainDateTimeToStringSettings toStringSettings) {
        IsoDate isoDate = plainDateTime.getIsoDateTime().date();
        IsoTime isoTime = plainDateTime.getIsoDateTime().time();

        long roundedNanoseconds = toStringSettings.roundedTime(isoTime);
        IsoDate roundedDate = roundedNanoseconds == TemporalConstants.DAY_NANOSECONDS ? isoDate.addDays(1) : isoDate;
        IsoTime roundedTime = IsoTime.createFromNanoseconds(roundedNanoseconds);
        if (!roundedDate.isWithinPlainDateTimeRange(roundedTime)) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        String dateTimeString = roundedDate + "T" + toStringSettings.format(roundedTime);
        return TemporalUtils.maybeAppendCalendar(dateTimeString, plainDateTime.getCalendarId(),
                toStringSettings.calendarNameOption());
    }

    public static JSValue toZonedDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toZonedDateTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue temporalTimeZoneLike = JSValue.argumentOrUndefined(args, 0);
        String timeZoneId = TemporalTimeZone.requireTimeZoneIdentifier(context, temporalTimeZoneLike);
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        String disambiguation = TemporalZonedDateTimeOptions.disambiguation(context, optionsArg);
        if (context.hasPendingException() || disambiguation == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger epochNanoseconds;
        try {
            epochNanoseconds = plainDateTime.getIsoDateTime().toEpochNs(timeZoneId, disambiguation);
        } catch (DateTimeException dateTimeException) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }

        return JSTemporalZonedDateTime.create(context, epochNanoseconds, timeZoneId, plainDateTime.getCalendarId());
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
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "with");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fields = TemporalUtils.requirePartialTemporalObject(context, args,
                TemporalError.MISSING_PARTIAL_FIELDS.message());
        if (fields == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime originalTime = plainDateTime.getIsoDateTime().time();
        TemporalCalendarId calendarId = plainDateTime.getCalendarId();
        TemporalTimeFields bag = new TemporalTimeFields(context, fields);
        Integer day = bag.positiveDateField("day");
        bag.readThroughMinute();
        Integer month = bag.positiveDateField("month");
        String monthCode = bag.string("monthCode");
        bag.readNanosecond();
        bag.readSecond();
        TemporalPartialDate dateFields = bag.readYearAndEra(calendarId, day, month, monthCode);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dateFields.isEmpty() && !bag.hasTimeField()) {
            return TemporalError.MISSING_PARTIAL_FIELDS.undefined(context);
        }

        JSValue optionsValue = JSValue.argumentOrUndefined(args, 1);
        String overflow = TemporalUtils.getOverflowOption(context, optionsValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate resultDate = dateFields.mergeDate(context, plainDateTime.getIsoDateTime().date(), calendarId, overflow);
        if (resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime resultTime = bag.regulate(originalTime, overflow, TemporalError.INVALID_ISO_DATE.message());
        if (resultTime == null) {
            return JSUndefined.INSTANCE;
        }

        if (!resultDate.isWithinPlainDateTimeRange(resultTime)) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        return JSTemporalPlainDateTime.create(context, resultDate.atTime(resultTime), plainDateTime.getCalendarId());
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.withCalendar(context, thisArg, args, (ctx, plainDateTime, calendarId) -> JSTemporalPlainDateTime
                .create(ctx, plainDateTime.getIsoDateTime(), calendarId));
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "withPlainTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime time = TemporalPlainTimeConstructor.optionalTime(context, args);
        if (time == null) {
            return JSUndefined.INSTANCE;
        }
        if (!plainDateTime.getIsoDateTime().date().isWithinPlainDateTimeRange(time)) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }
        return JSTemporalPlainDateTime.create(context, plainDateTime.getIsoDateTime().withTime(time),
                plainDateTime.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR_OF_WEEK.read(context, thisArg, RECEIVER);
    }
}
