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
    private static final BigInteger SECOND_NANOSECONDS = TemporalConstants.BI_SECOND_NANOSECONDS;
    private static final long TEMPORAL_MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final String UNIT_AUTO = "auto";
    private static final String UNIT_DAY = "day";
    private static final String UNIT_MONTH = "month";
    private static final String UNIT_WEEK = "week";
    private static final String UNIT_YEAR = "year";

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
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = temporalDuration.getDuration();
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
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "day");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = plainDate.toIsoCalendarDate().toIsoDate();
        return JSNumber.of(calendarDate.day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "dayOfWeek",
                plainDate -> JSNumber.of(plainDate.getIsoDate().dayOfWeek()));
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "dayOfYear",
                plainDate -> JSNumber.of(plainDate.getIsoDate().dayOfYear(plainDate.getCalendarId())));
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInMonth",
                plainDate -> JSNumber.of(plainDate.getIsoDate().daysInMonth(plainDate.getCalendarId())));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInWeek", plainDate -> JSNumber.of(7));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInYear",
                plainDate -> JSNumber.of(plainDate.getIsoDate().daysInYear(plainDate.getCalendarId())));
    }

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, since ? "since" : "until");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainDate(context, plainDate, args, since);
    }

    static TemporalDuration differenceCalendarDates(JSContext context, IsoDate firstDate, IsoDate secondDate,
            TemporalCalendarId calendarId, TemporalUnit largestUnit) {
        TemporalDurationDateWeek dateDifference = TemporalDurationDateWeek.calendarDateUntil(context, firstDate,
                secondDate, calendarId, largestUnit);
        if (context.hasPendingException() || dateDifference == null) {
            return null;
        }
        return new TemporalDuration(dateDifference.years(), dateDifference.months(), dateDifference.weeks(),
                dateDifference.days(), 0, 0, 0, 0, 0, 0);
    }

    private static JSValue differenceTemporalPlainDate(JSContext context, JSTemporalPlainDate plainDate, JSValue[] args,
            boolean sinceOperation) {
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException() || other == null) {
            return JSUndefined.INSTANCE;
        }
        if (!plainDate.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        TemporalDifferenceSettings settings = TemporalDifferenceSettings.parse(context, sinceOperation, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.DAY, TemporalUnit.DAY, TemporalUnit.DAY, true, false);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate thisDate = plainDate.getIsoDate();
        IsoDate otherDate = other.getIsoDate();
        if (thisDate.compareTo(otherDate) == 0) {
            return JSTemporalDuration.create(context, TemporalDuration.ZERO);
        }

        TemporalDurationDateWeek dateDifference = TemporalDurationDateWeek.calendarDateUntil(context, thisDate,
                otherDate, plainDate.getCalendarId(), settings.largestUnit());
        boolean roundingNoOp = settings.smallestUnit() == TemporalUnit.DAY && settings.roundingIncrement() == 1L;
        if (!roundingNoOp) {
            dateDifference = roundRelativeDurationDate(context, dateDifference, otherDate.toEpochDay(), thisDate,
                    plainDate.getCalendarId(), settings);
            if (context.hasPendingException() || dateDifference == null) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalDuration resultDuration = new TemporalDuration(dateDifference.years(), dateDifference.months(),
                dateDifference.weeks(), dateDifference.days(), 0, 0, 0, 0, 0, 0);
        if (sinceOperation) {
            resultDuration = resultDuration.negated();
        }
        return JSTemporalDuration.create(context, resultDuration);
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
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "era");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraYear temporalEraYear = plainDate.toTemporalEraYear();
        if (temporalEraYear == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(temporalEraYear.era().identifier());
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "eraYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraYear temporalEraYear = plainDate.toTemporalEraYear();
        if (temporalEraYear == null || temporalEraYear.eraYear() == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(temporalEraYear.eraYear());
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "inLeapYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields = plainDate.getIsoDate().toIsoCalendarDate(plainDate.getCalendarId());
        boolean leapYear = plainDate.getCalendarId().isCalendarLeapYear(calendarDateFields.year());
        if (leapYear) {
            return JSBoolean.TRUE;
        } else {
            return JSBoolean.FALSE;
        }
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "month");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = plainDate.toIsoCalendarDate().toIsoDate();
        return JSNumber.of(calendarDate.month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "monthCode",
                plainDate -> new JSString(plainDate.toIsoCalendarDate().monthCode()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "monthsInYear", plainDate -> JSNumber
                .of(TemporalUtils.monthsInYear(plainDate.getIsoDate(), plainDate.getCalendarId())));
    }

    private static TemporalDurationDateWeek nudgeToCalendarUnit(JSContext context, int sign,
            TemporalDurationDateWeek duration, long destinationEpochDay, IsoDate originDate,
            TemporalCalendarId calendarId, TemporalDifferenceSettings settings) {
        TemporalUnit smallestUnit = settings.smallestUnit();
        long increment = settings.roundingIncrement();
        long roundingStartValue;
        long roundingEndValue;
        TemporalDurationDateWeek startDuration;
        TemporalDurationDateWeek endDuration;
        if (smallestUnit == TemporalUnit.YEAR) {
            long roundedYears = TemporalRoundingMode.TRUNC.roundNumberToIncrement(duration.years(), increment);
            roundingStartValue = roundedYears;
            roundingEndValue = roundedYears + increment * sign;
            startDuration = new TemporalDurationDateWeek(roundedYears, 0, 0, 0);
            endDuration = new TemporalDurationDateWeek(roundingEndValue, 0, 0, 0);
        } else if (smallestUnit == TemporalUnit.MONTH) {
            long roundedMonths = TemporalRoundingMode.TRUNC.roundNumberToIncrement(duration.months(), increment);
            roundingStartValue = roundedMonths;
            roundingEndValue = roundedMonths + increment * sign;
            startDuration = duration.adjust(0, 0L, roundedMonths);
            endDuration = duration.adjust(0, 0L, roundingEndValue);
        } else if (smallestUnit == TemporalUnit.WEEK) {
            TemporalDurationDateWeek yearsAndMonthsDuration = duration.adjust(0, 0L, null);
            IsoDate weeksStart = originDate.calendarDateAddConstrain(context, calendarId, yearsAndMonthsDuration);
            if (context.hasPendingException() || weeksStart == null) {
                return null;
            }
            long weeksEndEpochDay = weeksStart.toEpochDay() + duration.days();
            IsoDate weeksEnd = IsoDate.createFromEpochDay(weeksEndEpochDay);
            TemporalDurationDateWeek weekDifference = TemporalDurationDateWeek.calendarDateUntil(context, weeksStart,
                    weeksEnd, calendarId, TemporalUnit.WEEK);
            if (context.hasPendingException() || weekDifference == null) {
                return null;
            }
            long roundedWeeks = TemporalRoundingMode.TRUNC
                    .roundNumberToIncrement(duration.weeks() + weekDifference.weeks(), increment);
            roundingStartValue = roundedWeeks;
            roundingEndValue = roundedWeeks + increment * sign;
            startDuration = duration.adjust(0, roundedWeeks, null);
            endDuration = duration.adjust(0, roundingEndValue, null);
        } else {
            long roundedDays = TemporalRoundingMode.TRUNC.roundNumberToIncrement(duration.days(), increment);
            roundingStartValue = roundedDays;
            roundingEndValue = roundedDays + increment * sign;
            startDuration = duration.adjust(roundedDays, null, null);
            endDuration = duration.adjust(roundingEndValue, null, null);
        }

        IsoDate startDate = originDate.calendarDateAddConstrain(context, calendarId, startDuration);
        if (context.hasPendingException() || startDate == null) {
            return null;
        }
        IsoDate endDate = originDate.calendarDateAddConstrain(context, calendarId, endDuration);
        if (context.hasPendingException() || endDate == null) {
            return null;
        }
        long startEpochDay = startDate.toEpochDay();
        long endEpochDay = endDate.toEpochDay();
        long numerator = destinationEpochDay - startEpochDay;
        long denominator = endEpochDay - startEpochDay;
        if (denominator == 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        TemporalUnsignedRoundingMode unsignedRoundingMode = settings.roundingMode()
                .toUnsigned(TemporalSign.fromSignum(sign));
        int comparison = BigInteger.valueOf(Math.abs(numerator)).shiftLeft(1)
                .compareTo(BigInteger.valueOf(Math.abs(denominator)));
        int roundingComparison = Integer.compare(comparison, 0);
        boolean isEvenCardinality = Math.floorMod(Math.abs(roundingStartValue) / increment, 2L) == 0L;
        long roundedUnit;
        if (numerator == 0) {
            roundedUnit = Math.abs(roundingStartValue);
        } else if (numerator == denominator) {
            roundedUnit = Math.abs(roundingEndValue);
        } else {
            roundedUnit = unsignedRoundingMode.getRoundedUnit(Math.abs(roundingStartValue), Math.abs(roundingEndValue),
                    roundingComparison, isEvenCardinality);
        }

        boolean didExpandCalendarUnit = roundedUnit == Math.abs(roundingEndValue);
        TemporalDurationDateWeek roundedDuration = didExpandCalendarUnit ? endDuration : startDuration;
        if (didExpandCalendarUnit && smallestUnit != TemporalUnit.WEEK) {
            TemporalUnit bubbleSmallestUnit;
            if (smallestUnit.isLargerThan(TemporalUnit.DAY)) {
                bubbleSmallestUnit = smallestUnit;
            } else {
                bubbleSmallestUnit = TemporalUnit.DAY;
            }
            long nudgedEpochDay = endEpochDay;
            return roundedDuration.bubbleRelativeDuration(context, sign, nudgedEpochDay, originDate, calendarId,
                    settings.largestUnit(), bubbleSmallestUnit);
        }
        return roundedDuration;
    }

    private static TemporalDurationDateWeek nudgeToDayUnit(JSContext context, int sign,
            TemporalDurationDateWeek duration, long destinationEpochDay, IsoDate originDate,
            TemporalCalendarId calendarId, TemporalDifferenceSettings settings) {
        long originalDays = duration.days();
        long roundedDays = settings.roundingMode().roundNumberToIncrement(originalDays, settings.roundingIncrement());
        long dayDelta = roundedDays - originalDays;
        int durationSign = Long.compare(originalDays, 0);
        int deltaSign = Long.compare(dayDelta, 0);
        boolean didExpandCalendarUnit = deltaSign == durationSign;
        TemporalDurationDateWeek roundedDuration = duration.adjust(roundedDays, null, null);
        if (didExpandCalendarUnit) {
            long nudgedEpochDay = destinationEpochDay + dayDelta;
            return roundedDuration.bubbleRelativeDuration(context, sign, nudgedEpochDay, originDate, calendarId,
                    settings.largestUnit(), TemporalUnit.DAY);
        }
        return roundedDuration;
    }

    private static TemporalDurationDateWeek roundRelativeDurationDate(JSContext context,
            TemporalDurationDateWeek duration, long destinationEpochDay, IsoDate originDate,
            TemporalCalendarId calendarId, TemporalDifferenceSettings settings) {
        int sign = duration.sign() < 0 ? -1 : 1;
        if (settings.smallestUnit() == TemporalUnit.YEAR || settings.smallestUnit() == TemporalUnit.MONTH
                || settings.smallestUnit() == TemporalUnit.WEEK) {
            return nudgeToCalendarUnit(context, sign, duration, destinationEpochDay, originDate, calendarId, settings);
        } else {
            return nudgeToDayUnit(context, sign, duration, destinationEpochDay, originDate, calendarId, settings);
        }
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
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "weekOfYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (plainDate.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().weekOfYear());
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
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "year");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = plainDate.toIsoCalendarDate().toIsoDate();
        return JSNumber.of(calendarDate.year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = RECEIVER.require(context, thisArg, "yearOfWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (plainDate.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().yearOfWeek());
    }
}
