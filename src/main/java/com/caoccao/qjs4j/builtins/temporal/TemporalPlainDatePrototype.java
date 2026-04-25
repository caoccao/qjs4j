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
    private static final BigInteger SECOND_NANOSECONDS = TemporalConstants.BI_SECOND_NANOSECONDS;
    private static final long TEMPORAL_MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final String TYPE_NAME = "Temporal.PlainDate";
    private static final String UNIT_AUTO = "auto";
    private static final String UNIT_DAY = "day";
    private static final String UNIT_MONTH = "month";
    private static final String UNIT_WEEK = "week";
    private static final String UNIT_YEAR = "year";

    private TemporalPlainDatePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "add");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDate, args, 1);
    }

    private static IsoDate addDurationToDate(
            JSContext context,
            IsoDate date,
            TemporalDuration durationRecord,
            String overflow) {
        BigInteger totalTimeNanoseconds = durationRecord.timeNanoseconds();
        TemporalDuration balancedTimeDuration =
                TemporalDuration.createBalance(totalTimeNanoseconds, TemporalUnit.DAY);

        long totalDays;
        try {
            long weeksInDays = Math.multiplyExact(durationRecord.weeks(), 7L);
            totalDays = Math.addExact(durationRecord.days(), weeksInDays);
            totalDays = Math.addExact(totalDays, balancedTimeDuration.days());
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        return date.addDurationToIsoDate(
                context, durationRecord.years(), durationRecord.months(),
                0L, totalDays, overflow);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainDate plainDate, JSValue[] args, int sign) {
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

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
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
            TemporalDuration balancedTimeDuration =
                    TemporalDuration.createBalance(totalTimeNanoseconds, TemporalUnit.DAY);
            long dayDelta;
            try {
                dayDelta = Math.addExact(durationRecord.days(), balancedTimeDuration.days());
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            resultIsoDate = plainDate.getIsoDate().addCalendarDate(
                    context,
                    calendarId,
                    durationRecord.years(),
                    durationRecord.months(),
                    durationRecord.weeks(),
                    dayDelta,
                    overflow);
            if (context.hasPendingException() || resultIsoDate == null) {
                return JSUndefined.INSTANCE;
            }
        }
        return JSTemporalPlainDate.create(context, resultIsoDate, calendarId);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "calendarId");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.getCalendarId().identifier());
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "day");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = plainDate.toIsoCalendarDate().toIsoDate();
        return JSNumber.of(calendarDate.day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "dayOfWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "dayOfYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().dayOfYear(plainDate.getCalendarId()));
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "daysInMonth");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().daysInMonth(plainDate.getCalendarId()));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "daysInWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "daysInYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().daysInYear(plainDate.getCalendarId()));
    }

    static TemporalDuration differenceCalendarDates(
            JSContext context,
            IsoDate firstDate,
            IsoDate secondDate,
            TemporalCalendarId calendarId,
            TemporalUnit largestUnit) {
        TemporalDurationDateWeek dateDifference = TemporalDurationDateWeek.calendarDateUntil(
                context,
                firstDate,
                secondDate,
                calendarId,
                largestUnit);
        if (context.hasPendingException() || dateDifference == null) {
            return null;
        }
        return new TemporalDuration(
                dateDifference.years(),
                dateDifference.months(),
                dateDifference.weeks(),
                dateDifference.days(),
                0,
                0,
                0,
                0,
                0,
                0);
    }

    private static JSValue differenceTemporalPlainDate(
            JSContext context,
            JSTemporalPlainDate plainDate,
            JSValue[] args,
            boolean sinceOperation) {
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException() || other == null) {
            return JSUndefined.INSTANCE;
        }
        if (!plainDate.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        TemporalDifferenceSettings settings = TemporalDifferenceSettings.parse(
                context, sinceOperation, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.DAY,
                TemporalUnit.DAY, TemporalUnit.DAY,
                true, false);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate thisDate = plainDate.getIsoDate();
        IsoDate otherDate = other.getIsoDate();
        if (thisDate.compareTo(otherDate) == 0) {
            return JSTemporalDuration.create(context, TemporalDuration.ZERO);
        }

        TemporalDurationDateWeek dateDifference = TemporalDurationDateWeek.calendarDateUntil(
                context,
                thisDate,
                otherDate,
                plainDate.getCalendarId(),
                settings.largestUnit());
        boolean roundingNoOp = settings.smallestUnit() == TemporalUnit.DAY && settings.roundingIncrement() == 1L;
        if (!roundingNoOp) {
            dateDifference = roundRelativeDurationDate(
                    context,
                    dateDifference,
                    otherDate.toEpochDay(),
                    thisDate,
                    plainDate.getCalendarId(),
                    settings);
            if (context.hasPendingException() || dateDifference == null) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalDuration resultDuration = new TemporalDuration(
                dateDifference.years(),
                dateDifference.months(),
                dateDifference.weeks(),
                dateDifference.days(),
                0,
                0,
                0,
                0,
                0,
                0);
        if (sinceOperation) {
            resultDuration = resultDuration.negated();
        }
        return JSTemporalDuration.create(context, resultDuration);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "equals");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDate other = TemporalPlainDateConstructor.toTemporalDateObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = plainDate.getIsoDate().compareTo(other.getIsoDate()) == 0
                && plainDate.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "era");
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
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "eraYear");
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
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "inLeapYear");
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
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "month");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = plainDate.toIsoCalendarDate().toIsoDate();
        return JSNumber.of(calendarDate.month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "monthCode");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.toIsoCalendarDate().monthCode());
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "monthsInYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalUtils.monthsInYear(plainDate.getIsoDate(), plainDate.getCalendarId()));
    }

    private static TemporalDurationDateWeek nudgeToCalendarUnit(
            JSContext context,
            int sign,
            TemporalDurationDateWeek duration,
            long destinationEpochDay,
            IsoDate originDate,
            TemporalCalendarId calendarId,
            TemporalDifferenceSettings settings) {
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
            TemporalDurationDateWeek weekDifference = TemporalDurationDateWeek.calendarDateUntil(
                    context,
                    weeksStart,
                    weeksEnd,
                    calendarId,
                    TemporalUnit.WEEK);
            if (context.hasPendingException() || weekDifference == null) {
                return null;
            }
            long roundedWeeks = TemporalRoundingMode.TRUNC.roundNumberToIncrement(
                    duration.weeks() + weekDifference.weeks(),
                    increment);
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

        TemporalUnsignedRoundingMode unsignedRoundingMode = settings.roundingMode().toUnsigned(
                TemporalSign.fromSignum(sign));
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
            roundedUnit = unsignedRoundingMode.getRoundedUnit(
                    Math.abs(roundingStartValue),
                    Math.abs(roundingEndValue),
                    roundingComparison,
                    isEvenCardinality);
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
            return roundedDuration.bubbleRelativeDuration(
                    context,
                    sign,
                    nudgedEpochDay,
                    originDate,
                    calendarId,
                    settings.largestUnit(),
                    bubbleSmallestUnit);
        }
        return roundedDuration;
    }

    private static TemporalDurationDateWeek nudgeToDayUnit(
            JSContext context,
            int sign,
            TemporalDurationDateWeek duration,
            long destinationEpochDay,
            IsoDate originDate,
            TemporalCalendarId calendarId,
            TemporalDifferenceSettings settings) {
        long originalDays = duration.days();
        long roundedDays = settings.roundingMode().roundNumberToIncrement(
                originalDays,
                settings.roundingIncrement());
        long dayDelta = roundedDays - originalDays;
        int durationSign = Long.compare(originalDays, 0);
        int deltaSign = Long.compare(dayDelta, 0);
        boolean didExpandCalendarUnit = deltaSign == durationSign;
        TemporalDurationDateWeek roundedDuration = duration.adjust(roundedDays, null, null);
        if (didExpandCalendarUnit) {
            long nudgedEpochDay = destinationEpochDay + dayDelta;
            return roundedDuration.bubbleRelativeDuration(
                    context,
                    sign,
                    nudgedEpochDay,
                    originDate,
                    calendarId,
                    settings.largestUnit(),
                    TemporalUnit.DAY);
        }
        return roundedDuration;
    }

    private static TemporalDurationDateWeek roundRelativeDurationDate(
            JSContext context,
            TemporalDurationDateWeek duration,
            long destinationEpochDay,
            IsoDate originDate,
            TemporalCalendarId calendarId,
            TemporalDifferenceSettings settings) {
        int sign = duration.sign() < 0 ? -1 : 1;
        if (settings.smallestUnit() == TemporalUnit.YEAR
                || settings.smallestUnit() == TemporalUnit.MONTH
                || settings.smallestUnit() == TemporalUnit.WEEK) {
            return nudgeToCalendarUnit(
                    context,
                    sign,
                    duration,
                    destinationEpochDay,
                    originDate,
                    calendarId,
                    settings);
        } else {
            return nudgeToDayUnit(
                    context,
                    sign,
                    duration,
                    destinationEpochDay,
                    originDate,
                    calendarId,
                    settings);
        }
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "since");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainDate(context, plainDate, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "subtract");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDate, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "toJSON");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.getIsoDate().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "toLocaleString");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (options instanceof JSObject optionsObject) {
            JSValue timeStyleValue = optionsObject.get(PropertyKey.fromString("timeStyle"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(timeStyleValue instanceof JSUndefined) && timeStyleValue != null) {
                context.throwTypeError("Invalid date/time formatting options");
                return JSUndefined.INSTANCE;
            }
        }
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainDate});
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "toPlainDateTime");
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

        return JSTemporalPlainDateTime.create(context,
                isoDate.atTime(time), plainDate.getCalendarId());
    }

    public static JSValue toPlainMonthDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "toPlainMonthDay");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields =
                plainDate.getIsoDate().toIsoCalendarDate(plainDate.getCalendarId());
        JSTemporalPlainMonthDay plainMonthDay = TemporalPlainMonthDayConstructor.createPlainMonthDayFromCalendarMonthDay(
                context,
                plainDate.getCalendarId(),
                calendarDateFields.monthCode(),
                calendarDateFields.day(),
                "constrain");
        if (context.hasPendingException() || plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        return plainMonthDay;
    }

    public static JSValue toPlainYearMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "toPlainYearMonth");
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
        return JSTemporalPlainYearMonth.create(
                context,
                plainYearMonthIsoDate,
                plainDate.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "toString");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
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
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "toZonedDateTime");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(context, plainTimeLike, JSUndefined.INSTANCE);
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
        return JSTemporalZonedDateTime.create(
                context,
                epochNanoseconds,
                timeZoneId,
                plainDate.getCalendarId());
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "until");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainDate(context, plainDate, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainDate.prototype.valueOf; use Temporal.PlainDate.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "weekOfYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (plainDate.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "with");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }
        if (fields instanceof JSTemporalPlainDate
                || fields instanceof JSTemporalPlainDateTime
                || fields instanceof JSTemporalPlainMonthDay
                || fields instanceof JSTemporalPlainTime
                || fields instanceof JSTemporalPlainYearMonth
                || fields instanceof JSTemporalZonedDateTime) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarLike = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(calendarLike instanceof JSUndefined) && calendarLike != null) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }
        JSValue timeZoneLike = fields.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(timeZoneLike instanceof JSUndefined) && timeZoneLike != null) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = plainDate.getCalendarId();
        boolean calendarSupportsEraFields = calendarId != TemporalCalendarId.ISO8601
                && calendarId != TemporalCalendarId.CHINESE
                && calendarId != TemporalCalendarId.DANGI;

        JSValue dayFieldValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDayField = !(dayFieldValue instanceof JSUndefined) && dayFieldValue != null;
        Integer dayOfMonth = null;
        if (hasDayField) {
            dayOfMonth = TemporalUtils.toIntegerThrowOnInfinity(context, dayFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (dayOfMonth < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthFieldValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthField = !(monthFieldValue instanceof JSUndefined) && monthFieldValue != null;
        Integer month = null;
        if (hasMonthField) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (month < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeFieldValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCodeField = !(monthCodeFieldValue instanceof JSUndefined) && monthCodeFieldValue != null;
        String monthCode = null;
        if (hasMonthCodeField) {
            monthCode = JSTypeConversions.toString(context, monthCodeFieldValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearFieldValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYearField = !(yearFieldValue instanceof JSUndefined) && yearFieldValue != null;
        Integer year = null;
        if (hasYearField) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        boolean hasEraField = false;
        boolean hasEraYearField = false;
        String era = null;
        Integer eraYear = null;
        if (calendarSupportsEraFields) {
            JSValue eraFieldValue = fields.get(PropertyKey.fromString("era"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraField = !(eraFieldValue instanceof JSUndefined) && eraFieldValue != null;
            if (hasEraField) {
                era = JSTypeConversions.toString(context, eraFieldValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            JSValue eraYearFieldValue = fields.get(PropertyKey.fromString("eraYear"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYearField = !(eraYearFieldValue instanceof JSUndefined) && eraYearFieldValue != null;
            if (hasEraYearField) {
                eraYear = TemporalUtils.toIntegerThrowOnInfinity(context, eraYearFieldValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }

        boolean hasAnyField = hasDayField
                || hasMonthField
                || hasMonthCodeField
                || hasYearField
                || hasEraField
                || hasEraYearField;
        if (!hasAnyField) {
            context.throwTypeError("Temporal error: Must specify at least one calendar field.");
            return JSUndefined.INSTANCE;
        }

        IsoDate calendarDate = plainDate.toIsoCalendarDate().toIsoDate();
        if (context.hasPendingException() || calendarDate == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarMonthCode = plainDate.toIsoCalendarDate().monthCode();
        if (context.hasPendingException() || calendarMonthCode == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraYear eraFields = plainDate.toTemporalEraYear();

        JSObject mergedFieldsObject = new JSObject(context);
        mergedFieldsObject.set(PropertyKey.fromString("calendar"), new JSString(calendarId.identifier()));
        mergedFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(calendarDate.day()));
        if (calendarId == TemporalCalendarId.ISO8601) {
            mergedFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(calendarDate.month()));
        }
        mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(calendarMonthCode));
        mergedFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(calendarDate.year()));
        if (eraFields != null) {
            mergedFieldsObject.set(PropertyKey.fromString("era"), new JSString(eraFields.era().identifier()));
            if (eraFields.eraYear() != null) {
                mergedFieldsObject.set(PropertyKey.fromString("eraYear"), JSNumber.of(eraFields.eraYear()));
            }
        }

        if (hasYearField) {
            mergedFieldsObject.delete(PropertyKey.fromString("era"));
            mergedFieldsObject.delete(PropertyKey.fromString("eraYear"));
        }
        if (hasEraField || hasEraYearField) {
            mergedFieldsObject.delete(PropertyKey.fromString("year"));
            mergedFieldsObject.delete(PropertyKey.fromString("era"));
            mergedFieldsObject.delete(PropertyKey.fromString("eraYear"));
        }
        if (hasMonthField) {
            mergedFieldsObject.delete(PropertyKey.fromString("monthCode"));
        }
        if (hasMonthCodeField) {
            mergedFieldsObject.delete(PropertyKey.fromString("month"));
        }

        if (hasDayField) {
            mergedFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(dayOfMonth));
        }
        if (hasMonthField) {
            mergedFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(month));
        }
        if (hasMonthCodeField) {
            mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(monthCode));
        }
        if (hasYearField) {
            mergedFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(year));
        }
        if (hasEraField) {
            mergedFieldsObject.set(PropertyKey.fromString("era"), new JSString(era));
        }
        if (hasEraYearField) {
            mergedFieldsObject.set(PropertyKey.fromString("eraYear"), JSNumber.of(eraYear));
        }

        JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        String overflow = TemporalUtils.getOverflowOption(context, optionsValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!"reject".equals(overflow)
                && hasYearField
                && !hasMonthField
                && !hasMonthCodeField) {
            JSValue mergedMonthCodeValue = mergedFieldsObject.get(PropertyKey.fromString("monthCode"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (mergedMonthCodeValue instanceof JSString mergedMonthCodeString) {
                String constrainedMonthCode = calendarId.constrainMonthCode(
                        year,
                        mergedMonthCodeString.value());
                if (constrainedMonthCode != null
                        && !constrainedMonthCode.equals(mergedMonthCodeString.value())) {
                    mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(constrainedMonthCode));
                }
            }
        }
        JSObject normalizedOptionsObject = new JSObject(context);
        normalizedOptionsObject.set(PropertyKey.fromString("overflow"), new JSString(overflow));
        JSValue mergedDateValue = TemporalPlainDateConstructor.dateFromFields(
                context,
                mergedFieldsObject,
                normalizedOptionsObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(mergedDateValue instanceof JSTemporalPlainDate mergedDate)) {
            context.throwTypeError("Temporal error: Date argument must be object or string.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(
                context,
                mergedDate.getIsoDate(),
                calendarId);
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "withCalendar");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] == null) {
            context.throwTypeError("Temporal error: Calendar is required.");
            return JSUndefined.INSTANCE;
        }
        TemporalCalendarId calendarId = TemporalCalendarId.createFromCalendarValue(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainDate.create(context, plainDate.getIsoDate(), calendarId);
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "year");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = plainDate.toIsoCalendarDate().toIsoDate();
        return JSNumber.of(calendarDate.year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, "yearOfWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (plainDate.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().yearOfWeek());
    }
}
