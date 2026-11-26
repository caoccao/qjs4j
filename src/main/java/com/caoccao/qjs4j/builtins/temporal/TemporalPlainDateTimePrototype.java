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
 * Implementation of Temporal.PlainDateTime prototype methods.
 */
public final class TemporalPlainDateTimePrototype {
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;
    private static final long MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final TemporalReceiver<JSTemporalPlainDateTime> RECEIVER = new TemporalReceiver<>(
            JSTemporalPlainDateTime.class, "Temporal.PlainDateTime");

    private TemporalPlainDateTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, 1);
    }

    private static TemporalTimeAddResult addDurationToTime(JSContext context, IsoTime time,
            TemporalDuration durationRecord) {
        TemporalTimeAddResult result = durationRecord.addToTime(time);
        if (result == null) {
            context.throwRangeError("Temporal error: Duration field out of range.");
        }
        return result;
    }

    private static JSValue addOrSubtract(JSContext context, JSValue thisArg, JSValue[] args, int sign) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, sign > 0 ? "add" : "subtract");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = temporalDuration.getDuration();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        TemporalTimeAddResult timeResult = addDurationToTime(context, plainDateTime.getIsoDateTime().time(),
                durationRecord);
        if (context.hasPendingException() || timeResult == null) {
            return JSUndefined.INSTANCE;
        }

        long adjustedDays;
        try {
            adjustedDays = Math.addExact(durationRecord.days(), timeResult.dayCarry());
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = plainDateTime.getCalendarId();
        IsoDate newDate;
        if (calendarId == TemporalCalendarId.ISO8601) {
            newDate = plainDateTime.getIsoDateTime().date().addDurationToIsoDate(context, durationRecord.years(),
                    durationRecord.months(), durationRecord.weeks(), adjustedDays, overflow);
        } else {
            newDate = plainDateTime.getIsoDateTime().date().addCalendarDate(context, calendarId, durationRecord.years(),
                    durationRecord.months(), durationRecord.weeks(), adjustedDays, overflow);
        }
        if (context.hasPendingException() || newDate == null) {
            return JSUndefined.INSTANCE;
        }

        if (!newDate.isWithinPlainDateTimeRange(timeResult.time())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalPlainDateTime.create(context, newDate.atTime(timeResult.time()),
                plainDateTime.getCalendarId());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "calendarId",
                plainDateTime -> new JSString(plainDateTime.getCalendarId().identifier()));
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.DAY);
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.DAY_OF_WEEK);
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.DAY_OF_YEAR);
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.DAYS_IN_MONTH);
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInWeek", plainDateTime -> JSNumber.of(7));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.DAYS_IN_YEAR);
    }

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, since ? "since" : "until");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!plainDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        TemporalDifferenceSettings settings = TemporalDifferenceSettings.parse(context, since, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.NANOSECOND, TemporalUnit.NANOSECOND, TemporalUnit.DAY, true, true);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = differenceTemporalPlainDateTime(context, plainDateTime, other, settings);
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration resultRecord = (since ? durationRecord.negated() : durationRecord)
                .normalizeFloat64RepresentableFields();
        if (!resultRecord.isValid() || !TemporalDuration.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, resultRecord);
    }

    private static TemporalDuration differenceTemporalPlainDateTime(JSContext context,
            JSTemporalPlainDateTime firstDateTime, JSTemporalPlainDateTime secondDateTime,
            TemporalDifferenceSettings settings) {
        TemporalCalendarId calendarId = firstDateTime.getCalendarId();
        boolean noRounding = settings.roundingIncrement() == 1L && settings.smallestUnit() == TemporalUnit.NANOSECOND;
        boolean sameTime = firstDateTime.getIsoDateTime().time().compareTo(secondDateTime.getIsoDateTime().time()) == 0;
        boolean dateLargestUnit = settings.largestUnit().isDateUnit();
        if (calendarId != TemporalCalendarId.ISO8601 && noRounding && sameTime && dateLargestUnit) {
            return TemporalPlainDatePrototype.differenceCalendarDates(context, firstDateTime.getIsoDateTime().date(),
                    secondDateTime.getIsoDateTime().date(), calendarId, settings.largestUnit());
        } else {
            return TemporalDurationPrototype.differencePlainDateTime(context, firstDateTime.getIsoDateTime(),
                    secondDateTime.getIsoDateTime(), settings.largestUnit(), settings.smallestUnit(),
                    settings.roundingIncrement(), settings.roundingMode());
        }
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "equals");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = plainDateTime.getIsoDateTime().compareTo(other.getIsoDateTime()) == 0
                && plainDateTime.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.ERA);
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.ERA_YEAR);
    }

    private static String getTemporalDisambiguation(JSContext context, JSValue optionsArg) {
        if (optionsArg instanceof JSUndefined || optionsArg == null) {
            return "compatible";
        }
        if (!(optionsArg instanceof JSObject optionsObject)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }
        String disambiguation = TemporalUtils.getStringOption(context, optionsObject, "disambiguation", "compatible");
        if (context.hasPendingException() || disambiguation == null) {
            return null;
        }
        if (TemporalDisambiguation.fromString(disambiguation) == null) {
            context.throwRangeError("Temporal error: Invalid disambiguation option.");
            return null;
        }
        return disambiguation;
    }

    private static IsoTime getTime(JSContext context, JSValue thisArg, String field) {
        JSTemporalPlainDateTime receiver = RECEIVER.require(context, thisArg, field);
        return receiver == null ? null : receiver.getIsoDateTime().time();
    }

    private static TemporalPlainDateTimeToStringSettings getToStringSettings(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalUtils.toOptionalOptionsObject(context, optionsValue,
                "Temporal error: Option must be object: options.");
        if (context.hasPendingException()) {
            return null;
        }
        if (optionsObject == null) {
            return TemporalPlainDateTimeToStringSettings.DEFAULT;
        }

        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, optionsObject);
        if (context.hasPendingException() || calendarNameOption == null) {
            return null;
        }

        TemporalPlainTimeToStringSettings time = TemporalPlainTimeToStringSettings.read(context, optionsObject);
        if (time == null) {
            return null;
        }
        return new TemporalPlainDateTimeToStringSettings(calendarNameOption, time.smallestUnit(), time.roundingMode(),
                time.autoFractionalSecondDigits(), time.fractionalSecondDigits(), time.roundingIncrementNanoseconds());
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        IsoTime time = getTime(context, thisArg, "hour");
        return time == null ? JSUndefined.INSTANCE : JSNumber.of(time.hour());
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.IN_LEAP_YEAR);
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        IsoTime time = getTime(context, thisArg, "microsecond");
        return time == null ? JSUndefined.INSTANCE : JSNumber.of(time.microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        IsoTime time = getTime(context, thisArg, "millisecond");
        return time == null ? JSUndefined.INSTANCE : JSNumber.of(time.millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        IsoTime time = getTime(context, thisArg, "minute");
        return time == null ? JSUndefined.INSTANCE : JSNumber.of(time.minute());
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.MONTH);
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.MONTH_CODE);
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.MONTHS_IN_YEAR);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        IsoTime time = getTime(context, thisArg, "nanosecond");
        return time == null ? JSUndefined.INSTANCE : JSNumber.of(time.nanosecond());
    }

    private static JSValue readDateField(JSContext context, JSValue thisArg, TemporalDateProperty property) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, property.key);
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDate date = JSTemporalPlainDate.create(context, plainDateTime.getIsoDateTime().date(),
                plainDateTime.getCalendarId());
        return property.read(date);
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
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalPlainDateTime.create(context, rounded, plainDateTime.getCalendarId());
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        IsoTime time = getTime(context, thisArg, "second");
        return time == null ? JSUndefined.INSTANCE : JSNumber.of(time.second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toJSON");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPlainDateTimeToStringSettings toStringSettings = new TemporalPlainDateTimeToStringSettings("auto", null,
                TemporalRoundingMode.TRUNC, true, -1, 1L);
        String formattedString = toTemporalPlainDateTimeString(context, plainDateTime, toStringSettings);
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
        JSValue locales = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(context, null, new JSValue[]{locales, options},
                "any", "all");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainDateTime});
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
        TemporalPlainDateTimeToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
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

        long roundedNanoseconds = isoTime.totalNanoseconds();
        if (toStringSettings.roundingIncrementNanoseconds() > 1L) {
            roundedNanoseconds = toStringSettings.roundingMode().roundLongToIncrementAsIfPositive(roundedNanoseconds,
                    toStringSettings.roundingIncrementNanoseconds());
        }

        int dayAdjust = 0;
        if (roundedNanoseconds == DAY_NANOSECONDS.longValue()) {
            dayAdjust = 1;
            roundedNanoseconds = 0L;
        }

        IsoDate roundedDate = isoDate;
        if (dayAdjust != 0) {
            roundedDate = roundedDate.addDays(dayAdjust);
        }
        IsoTime roundedTime = IsoTime.createFromNanoseconds(roundedNanoseconds);
        if (!roundedDate.isWithinPlainDateTimeRange(roundedTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String dateString = roundedDate.toString();
        String timeString = roundedTime.formatTimeString(toStringSettings.smallestUnit(),
                toStringSettings.autoFractionalSecondDigits(), toStringSettings.fractionalSecondDigits());
        String dateTimeString = dateString + "T" + timeString;
        return TemporalUtils.maybeAppendCalendar(dateTimeString, plainDateTime.getCalendarId(),
                toStringSettings.calendarNameOption());
    }

    private static String toTemporalTimeZoneIdentifier(JSContext context, JSValue timeZoneLike) {
        if (!(timeZoneLike instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
        }
        return TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneString.value());
    }

    public static JSValue toZonedDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "toZonedDateTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue temporalTimeZoneLike = JSValue.argumentOrUndefined(args, 0);
        String timeZoneId = toTemporalTimeZoneIdentifier(context, temporalTimeZoneLike);
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        String disambiguation = getTemporalDisambiguation(context, optionsArg);
        if (context.hasPendingException() || disambiguation == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger epochNanoseconds;
        try {
            epochNanoseconds = plainDateTime.getIsoDateTime().toEpochNs(timeZoneId, disambiguation);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalZonedDateTime.create(context, epochNanoseconds, timeZoneId, plainDateTime.getCalendarId());
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.WEEK_OF_YEAR);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "with");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fields = TemporalUtils.requirePartialTemporalObject(context, args,
                "Temporal error: Argument to with() must contain some date/time fields.");
        if (fields == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime originalTime = plainDateTime.getIsoDateTime().time();
        TemporalCalendarId calendarId = plainDateTime.getCalendarId();
        TemporalPropertyBag bag = new TemporalPropertyBag(context, fields);
        Integer day = bag.positiveDateField("day");
        int hour = bag.time("hour", originalTime.hour());
        int microsecond = bag.time("microsecond", originalTime.microsecond());
        int millisecond = bag.time("millisecond", originalTime.millisecond());
        int minute = bag.time("minute", originalTime.minute());
        Integer month = bag.positiveDateField("month");
        String monthCode = bag.string("monthCode");
        int nanosecond = bag.time("nanosecond", originalTime.nanosecond());
        int second = bag.time("second", originalTime.second());
        TemporalPartialDate dateFields = bag.readYearAndEra(calendarId, day, month, monthCode);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dateFields.isEmpty() && !bag.hasTimeField()) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsValue = JSValue.argumentOrUndefined(args, 1);
        String overflow = TemporalUtils.getOverflowOption(context, optionsValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate resultDate = plainDateTime.getIsoDateTime().date();
        if (!dateFields.isEmpty()) {
            JSTemporalPlainDate plainDate = JSTemporalPlainDate.create(context, resultDate, calendarId);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue mergedDateValue = dateFields.merge(context, plainDate,
                    TemporalUtils.overflowOptions(context, overflow));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (mergedDateValue instanceof JSTemporalPlainDate mergedDate) {
                resultDate = mergedDate.getIsoDate();
            } else {
                context.throwTypeError("Temporal error: Date argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
        }

        IsoTime resultTime;
        if ("reject".equals(overflow)) {
            resultTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            if (!resultTime.isValid()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        } else {
            resultTime = IsoTime.createNormalized(hour, minute, second, millisecond, microsecond, nanosecond);
        }

        if (!resultDate.isWithinPlainDateTimeRange(resultTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalPlainDateTime.create(context, resultDate.atTime(resultTime), plainDateTime.getCalendarId());
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "withCalendar");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalUtils.requireCalendarArgument(context, args);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDateTime.create(context, plainDateTime.getIsoDateTime(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = RECEIVER.require(context, thisArg, "withPlainTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime time = IsoTime.MIDNIGHT;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue timeArg = args[0];
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(context, timeArg, JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            time = plainTime.getIsoTime();
        }
        if (!plainDateTime.getIsoDateTime().date().isWithinPlainDateTimeRange(time)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDateTime.create(context, plainDateTime.getIsoDateTime().withTime(time),
                plainDateTime.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.YEAR);
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return readDateField(context, thisArg, TemporalDateProperty.YEAR_OF_WEEK);
    }
}
