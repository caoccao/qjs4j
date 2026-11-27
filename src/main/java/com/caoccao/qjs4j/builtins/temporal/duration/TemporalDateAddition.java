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

package com.caoccao.qjs4j.builtins.temporal.duration;

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;
import com.caoccao.qjs4j.core.temporal.duration.TemporalTimeAddResult;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;

import java.math.BigInteger;
import java.time.DateTimeException;

/** A signed duration and overflow policy for calendar date arithmetic. */
public record TemporalDateAddition(TemporalDuration duration, String overflow) {

    static TemporalDateAddition read(JSContext context, JSValue[] args, int sign) {
        TemporalDuration duration = TemporalDurationConstructor.toDurationRecord(context,
                JSValue.argumentOrUndefined(args, 0));
        if (duration == null) {
            return null;
        }
        String overflow = TemporalUtils.getOverflowOption(context, JSValue.argumentOrUndefined(args, 1));
        return context.hasPendingException()
                ? null
                : new TemporalDateAddition(sign < 0 ? duration.negated() : duration, overflow);
    }

    private IsoDate addDateParts(JSContext context, IsoDate date, TemporalCalendarId calendar, long days) {
        if (calendar == TemporalCalendarId.ISO8601) {
            return date.addDurationToIsoDate(context, duration.years(), duration.months(), duration.weeks(), days,
                    overflow);
        }
        return date.addCalendarDate(context, calendar, duration.years(), duration.months(), duration.weeks(), days,
                overflow);
    }

    public JSValue apply(JSContext context, JSTemporalPlainDate plainDate) {
        long days;
        try {
            days = Math.addExact(duration.days(), duration.wholeTimeDays());
        } catch (ArithmeticException e) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }
        TemporalCalendarId calendar = plainDate.getCalendarId();
        IsoDate result = addDateParts(context, plainDate.getIsoDate(), calendar, days);
        return result == null ? JSUndefined.INSTANCE : JSTemporalPlainDate.create(context, result, calendar);
    }

    public JSValue apply(JSContext context, JSTemporalPlainYearMonth plainYearMonth) {
        IsoDate isoDate = plainYearMonth.getIsoDate();
        if (!isoDate.isValid()) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        if (duration.weeks() != 0 || duration.days() != 0 || duration.hasTimeUnits()) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        TemporalCalendarId calendarId = plainYearMonth.getCalendarId();
        IsoDate resultDate = addDateParts(context, isoDate, calendarId, 0L);
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        return JSTemporalPlainYearMonth.create(context, resultDate, calendarId);
    }

    public JSValue apply(JSContext context, JSTemporalPlainDateTime plainDateTime) {
        TemporalTimeAddResult timeResult = duration.addToTime(plainDateTime.getIsoDateTime().time());
        if (timeResult == null) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }

        long adjustedDays;
        try {
            adjustedDays = Math.addExact(duration.days(), timeResult.dayCarry());
        } catch (ArithmeticException arithmeticException) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }

        TemporalCalendarId calendarId = plainDateTime.getCalendarId();
        IsoDate newDate = addDateParts(context, plainDateTime.getIsoDateTime().date(), calendarId, adjustedDays);
        if (context.hasPendingException() || newDate == null) {
            return JSUndefined.INSTANCE;
        }

        if (!newDate.isWithinPlainDateTimeRange(timeResult.time())) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        return JSTemporalPlainDateTime.create(context, newDate.atTime(timeResult.time()),
                plainDateTime.getCalendarId());
    }

    public JSValue apply(JSContext context, JSTemporalZonedDateTime zonedDateTime) {
        BigInteger intermediateEpochNanoseconds = zonedDateTime.getEpochNanoseconds();
        if (duration.hasAnyDateUnits()) {
            IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
            TemporalCalendarId calendarId = zonedDateTime.getCalendarId();
            IsoDate addedDate;
            if (calendarId == TemporalCalendarId.ISO8601) {
                addedDate = localDateTime.date().addIsoDateWithOverflow(context, duration.years(), duration.months(),
                        duration.weeks(), duration.days(), overflow);
            } else {
                addedDate = localDateTime.date().addCalendarDate(context, calendarId, duration.years(),
                        duration.months(), duration.weeks(), duration.days(), overflow);
            }
            if (context.hasPendingException() || addedDate == null) {
                return JSUndefined.INSTANCE;
            }

            IsoDateTime addedDateTime = addedDate.atTime(localDateTime.time());
            try {
                intermediateEpochNanoseconds = addedDateTime.toEpochNs(zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                return TemporalError.INVALID_ISO_DATE.undefined(context);
            }
        }

        BigInteger epochNanoseconds = intermediateEpochNanoseconds.add(duration.timeNanoseconds());
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            return TemporalError.NANOSECONDS_OUT_OF_RANGE.undefined(context);
        }

        return JSTemporalZonedDateTime.create(context, epochNanoseconds, zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    public static <T extends JSObject> JSValue apply(JSContext context, JSValue receiver, JSValue[] args, int sign,
            TemporalReceiver<T> type, Apply<T> operation) {
        T date = type.require(context, receiver, sign > 0 ? "add" : "subtract");
        if (date == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDateAddition addition = read(context, args, sign);
        return addition == null ? JSUndefined.INSTANCE : operation.apply(addition, context, date);
    }

    @FunctionalInterface
    public interface Apply<T> {
        JSValue apply(TemporalDateAddition addition, JSContext context, T date);
    }
}
