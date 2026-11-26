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

import java.math.BigInteger;
import java.time.DateTimeException;

/**
 * Implementation of Temporal.PlainDate prototype methods.
 */
public final class TemporalPlainDatePrototype {
    private static final TemporalReceiver<JSTemporalPlainDate> RECEIVER = new TemporalReceiver<>(
            JSTemporalPlainDate.class, "Temporal.PlainDate");

    private TemporalPlainDatePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, 1);
    }

    private static IsoDate addDurationToDate(JSContext context, IsoDate date, TemporalDuration durationRecord,
            String overflow) {
        BigInteger totalTimeNanoseconds = durationRecord.timeNanoseconds();
        TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(totalTimeNanoseconds, TemporalUnit.DAY);

        long totalDays;
        try {
            long weeksInDays = Math.multiplyExact(durationRecord.weeks(), 7L);
            totalDays = Math.addExact(durationRecord.days(), weeksInDays);
            totalDays = Math.addExact(totalDays, balancedTimeDuration.days());
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        return date.addDurationToIsoDate(context, durationRecord.years(), durationRecord.months(), 0L, totalDays,
                overflow);
    }

    private static JSValue addOrSubtract(JSContext context, JSValue thisArg, JSValue[] args, int sign) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, sign > 0 ? "add" : "subtract");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration durationRecord = TemporalDurationConstructor.toDurationRecord(context,
                JSValue.argumentOrUndefined(args, 0));
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        String overflow = TemporalUtils.getOverflowOption(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = plainDate.getCalendarId();
        IsoDate resultIsoDate;
        if (calendarId == TemporalCalendarId.ISO8601) {
            resultIsoDate = addDurationToDate(context, plainDate.getIsoDate(), durationRecord, overflow);
            if (context.hasPendingException() || resultIsoDate == null) {
                return JSUndefined.INSTANCE;
            }
        } else {
            BigInteger totalTimeNanoseconds = durationRecord.timeNanoseconds();
            TemporalDuration balancedTimeDuration = TemporalDuration.createBalance(totalTimeNanoseconds,
                    TemporalUnit.DAY);
            long dayDelta;
            try {
                dayDelta = Math.addExact(durationRecord.days(), balancedTimeDuration.days());
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            resultIsoDate = plainDate.getIsoDate().addCalendarDate(context, calendarId, durationRecord.years(),
                    durationRecord.months(), durationRecord.weeks(), dayDelta, overflow);
            if (context.hasPendingException() || resultIsoDate == null) {
                return JSUndefined.INSTANCE;
            }
        }
        return JSTemporalPlainDate.create(context, resultIsoDate, calendarId);
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

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, since ? "since" : "until");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainDate other = otherArg instanceof JSTemporalPlainDate date
                ? date
                : TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException() || other == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalCalendarDifference.between(context, plainDate, other, JSValue.argumentOrUndefined(args, 1),
                since, TemporalUnit.DAY);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "equals");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = plainDate.getIsoDate().compareTo(other.getIsoDate()) == 0
                && plainDate.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
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
        return difference(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "toJSON", plainDate -> new JSString(plainDate.getIsoDate().toString()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toLocaleString");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue dateTimeFormat = TemporalLocaleFormat.create(context, args, "timeStyle");
        if (dateTimeFormat == null) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainDate});
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toPlainDateTime");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoTime time = IsoTime.MIDNIGHT;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(context, args[0], JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            time = plainTime.getIsoTime();
        }

        IsoDate isoDate = plainDate.getIsoDate();
        if (!isoDate.isWithinPlainDateTimeRange(time)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalPlainDateTime.create(context, isoDate.atTime(time), plainDate.getCalendarId());
    }

    public static JSValue toPlainMonthDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toPlainMonthDay");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields = plainDate.getIsoDate().toIsoCalendarDate(plainDate.getCalendarId());
        JSTemporalPlainMonthDay plainMonthDay = TemporalPlainMonthDayConstructor
                .createPlainMonthDayFromCalendarMonthDay(context, plainDate.getCalendarId(),
                        calendarDateFields.monthCode(), calendarDateFields.day(), "constrain");
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
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "toString");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String result = plainDate.getIsoDate().toString();
        result = TemporalUtils.maybeAppendCalendar(result, plainDate.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    private static String toTemporalTimeZoneIdentifier(JSContext context, JSValue timeZoneLike) {
        if (!(timeZoneLike instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
        }
        return TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneString.value());
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

        String timeZoneId = toTemporalTimeZoneIdentifier(context, timeZoneLike);
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
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
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
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context, epochNanoseconds, timeZoneId, plainDate.getCalendarId());
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, false);
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
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "withCalendar");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalUtils.requireCalendarArgument(context, args);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, plainDate.getIsoDate(), calendarId);
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR_OF_WEEK.read(context, thisArg, RECEIVER);
    }
}
