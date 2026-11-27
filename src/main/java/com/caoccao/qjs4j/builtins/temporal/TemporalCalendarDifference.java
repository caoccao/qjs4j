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

/** Calendar differences and rounding boundaries for dates and year-months. */
final class TemporalCalendarDifference {
    private TemporalCalendarDifference() {
    }

    static JSValue between(JSContext context, JSTemporalCalendarDate first, JSTemporalCalendarDate second,
            JSValue options, boolean since, TemporalUnit smallest) {
        TemporalCalendarId calendar = first.getCalendarId();
        if (calendar != second.getCalendarId()) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }
        boolean monthDifference = smallest == TemporalUnit.MONTH;
        TemporalDifferenceSettings settings = TemporalDifferenceSettings.parse(context, since, options,
                TemporalUnit.YEAR, smallest, smallest, monthDifference ? TemporalUnit.YEAR : TemporalUnit.DAY, true,
                false);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration result = monthDifference
                ? yearMonthUntil(context, first.getIsoDate(), second.getIsoDate(), calendar, settings)
                : dateUntil(context, first.getIsoDate(), second.getIsoDate(), calendar, settings);
        if (context.hasPendingException() || result == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, since ? result.negated() : result);
    }

    static JSValue between(JSContext context, JSTemporalPlainDateTime plainDateTime, JSTemporalPlainDateTime other,
            JSValue optionsArg, boolean since) {
        if (!plainDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        TemporalDifferenceSettings settings = TemporalDifferenceSettings.parse(context, since, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.NANOSECOND, TemporalUnit.NANOSECOND, TemporalUnit.DAY, true, true);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = differenceDateTimes(context, plainDateTime.getIsoDateTime(),
                other.getIsoDateTime(), plainDateTime.getCalendarId(), settings);
        return finishDifference(context, durationRecord, since);
    }

    static JSValue between(JSContext context, JSTemporalZonedDateTime zonedDateTime, JSTemporalZonedDateTime other,
            JSValue optionsArg, boolean since) {
        if (!zonedDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        TemporalDifferenceSettings settings = zonedSettings(context, since, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = differenceTemporalZonedDateTime(context, zonedDateTime, other, settings);
        return finishDifference(context, durationRecord, since);
    }

    private static TemporalDuration dateUntil(JSContext context, IsoDate thisDate, IsoDate otherDate,
            TemporalCalendarId calendarId, TemporalDifferenceSettings settings) {
        if (thisDate.compareTo(otherDate) == 0) {
            return TemporalDuration.ZERO;
        }

        TemporalDurationDateWeek dateDifference = TemporalDurationDateWeek.calendarDateUntil(context, thisDate,
                otherDate, calendarId, settings.largestUnit());
        if (dateDifference == null || context.hasPendingException()) {
            return null;
        }
        boolean roundingNoOp = settings.smallestUnit() == TemporalUnit.DAY && settings.roundingIncrement() == 1L;
        if (!roundingNoOp) {
            dateDifference = roundRelativeDurationDate(context, dateDifference, otherDate.toEpochDay(), thisDate,
                    calendarId, settings);
            if (context.hasPendingException() || dateDifference == null) {
                return null;
            }
        }

        return new TemporalDuration(dateDifference.years(), dateDifference.months(), dateDifference.weeks(),
                dateDifference.days(), 0, 0, 0, 0, 0, 0);
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

    private static TemporalDuration differenceDateTimes(JSContext context, IsoDateTime start, IsoDateTime end,
            TemporalCalendarId calendarId, TemporalDifferenceSettings settings) {
        boolean noRounding = settings.roundingIncrement() == 1L && settings.smallestUnit() == TemporalUnit.NANOSECOND;
        boolean sameTime = start.time().compareTo(end.time()) == 0;
        boolean dateLargestUnit = settings.largestUnit().isDateUnit();
        if (calendarId != TemporalCalendarId.ISO8601 && noRounding && sameTime && dateLargestUnit) {
            return TemporalCalendarDifference.differenceCalendarDates(context, start.date(), end.date(), calendarId,
                    settings.largestUnit());
        } else {
            return TemporalDurationArithmetic.differencePlainDateTime(context, start, end, settings.largestUnit(),
                    settings.smallestUnit(), settings.roundingIncrement(), settings.roundingMode());
        }
    }

    private static TemporalDuration differenceTemporalZonedDateTime(JSContext context,
            JSTemporalZonedDateTime startZonedDateTime, JSTemporalZonedDateTime endZonedDateTime,
            TemporalDifferenceSettings settings) {
        boolean requiresDateUnits = settings.largestUnit().isDateUnit() || settings.smallestUnit().isDateUnit();
        if (!requiresDateUnits) {
            return TemporalDuration.differenceEpochNanoseconds(startZonedDateTime.getEpochNanoseconds(),
                    endZonedDateTime.getEpochNanoseconds(), settings.largestUnit(),
                    settings.smallestUnit().getNanosecondFactor(), settings.roundingIncrement(),
                    settings.roundingMode());
        }

        String startCanonicalTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(context,
                startZonedDateTime.getTimeZoneId());
        if (context.hasPendingException()) {
            return null;
        }
        String endCanonicalTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(context,
                endZonedDateTime.getTimeZoneId());
        if (context.hasPendingException()) {
            return null;
        }
        if (!startCanonicalTimeZoneId.equals(endCanonicalTimeZoneId)) {
            context.throwRangeError("Temporal error: Mismatched time zones.");
            return null;
        }

        IsoDateTime startLocalDateTime = startZonedDateTime.toLocalDateTime();
        IsoDateTime endLocalDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                endZonedDateTime.getEpochNanoseconds(), startZonedDateTime.getTimeZoneId());

        if (settings.largestUnit().isDateUnit() && !settings.smallestUnit().isDateUnit()) {
            boolean sameDate = startLocalDateTime.date().equals(endLocalDateTime.date());
            int wallClockSign = Integer.signum(endLocalDateTime.compareTo(startLocalDateTime));
            int epochSign = Integer
                    .signum(endZonedDateTime.getEpochNanoseconds().compareTo(startZonedDateTime.getEpochNanoseconds()));
            if (sameDate && wallClockSign != 0 && epochSign != 0 && wallClockSign != epochSign) {
                TemporalUnit timeLargestUnit;
                if (TemporalUnit.HOUR.isLargerThan(settings.smallestUnit())) {
                    timeLargestUnit = TemporalUnit.HOUR;
                } else {
                    timeLargestUnit = settings.smallestUnit();
                }
                return TemporalDuration.differenceEpochNanoseconds(startZonedDateTime.getEpochNanoseconds(),
                        endZonedDateTime.getEpochNanoseconds(), timeLargestUnit,
                        settings.smallestUnit().getNanosecondFactor(), settings.roundingIncrement(),
                        settings.roundingMode());
            }
        }

        TemporalCalendarId calendarId = startZonedDateTime.getCalendarId();
        if (calendarId == TemporalCalendarId.ISO8601) {
            return TemporalDurationArithmetic.differenceZonedDateTime(context, startZonedDateTime.getEpochNanoseconds(),
                    endZonedDateTime.getEpochNanoseconds(), startZonedDateTime.getTimeZoneId(), settings.largestUnit(),
                    settings.smallestUnit(), settings.roundingIncrement(), settings.roundingMode());
        }

        return differenceDateTimes(context, startLocalDateTime, endLocalDateTime, calendarId, settings);
    }

    private static JSValue finishDifference(JSContext context, TemporalDuration durationRecord, boolean since) {
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

    private static boolean isRoundedYearMonthInRange(IsoDate origin, long years, long months) {
        long monthIndex = origin.month() - 1L + months;
        long year = origin.year() + years + Math.floorDiv(monthIndex, 12L);
        int month = (int) Math.floorMod(monthIndex, 12L) + 1;
        return year >= Integer.MIN_VALUE && year <= Integer.MAX_VALUE
                && new IsoDate((int) year, month, 1).isWithinSupportedRange();
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

        boolean didExpandCalendarUnit = roundsToEnd(settings, sign, roundingStartValue, roundingEndValue, numerator,
                denominator);
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

    private static TemporalDurationYearMonth roundRelativeYearMonthDuration(JSContext context,
            TemporalDurationYearMonth durationFields, long destinationMonthsDifference, IsoDate originDifferenceDate,
            TemporalDifferenceSettings differenceSettings) {
        long years = durationFields.years();
        long months = durationFields.months();
        long increment = differenceSettings.roundingIncrement();
        long sign = Long.signum(destinationMonthsDifference);
        if (sign == 0L) {
            return TemporalDurationYearMonth.ZERO;
        }

        long roundingStartValue;
        long roundingEndValue;
        long startYears;
        long startMonths;
        long endYears;
        long endMonths;
        if (differenceSettings.smallestUnit() == TemporalUnit.YEAR) {
            roundingStartValue = TemporalRoundingMode.TRUNC.roundNumberToIncrement(years, increment);
            roundingEndValue = roundingStartValue + increment * sign;
            startYears = roundingStartValue;
            startMonths = 0L;
            endYears = roundingEndValue;
            endMonths = 0L;
        } else {
            roundingStartValue = TemporalRoundingMode.TRUNC.roundNumberToIncrement(months, increment);
            roundingEndValue = roundingStartValue + increment * sign;
            startYears = years;
            startMonths = roundingStartValue;
            endYears = years;
            endMonths = roundingEndValue;
        }

        if (!isRoundedYearMonthInRange(originDifferenceDate, startYears, startMonths)
                || !isRoundedYearMonthInRange(originDifferenceDate, endYears, endMonths)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        long startTotalMonths = startYears * 12L + startMonths;
        long endTotalMonths = endYears * 12L + endMonths;
        long numerator = destinationMonthsDifference - startTotalMonths;
        long denominator = endTotalMonths - startTotalMonths;
        if (denominator == 0L) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        boolean didExpandCalendarUnit = roundsToEnd(differenceSettings, sign, roundingStartValue, roundingEndValue,
                numerator, denominator);

        long roundedYears = didExpandCalendarUnit ? endYears : startYears;
        long roundedMonths = didExpandCalendarUnit ? endMonths : startMonths;
        if (didExpandCalendarUnit && differenceSettings.largestUnit() == TemporalUnit.YEAR
                && differenceSettings.smallestUnit() == TemporalUnit.MONTH) {
            long balancedTotalMonths = roundedYears * 12L + roundedMonths;
            roundedYears = balancedTotalMonths / 12L;
            roundedMonths = balancedTotalMonths % 12L;
        }
        return new TemporalDurationYearMonth(roundedYears, roundedMonths);
    }

    private static boolean roundsToEnd(TemporalDifferenceSettings settings, long sign, long start, long end,
            long numerator, long denominator) {
        if (numerator == 0) {
            return false;
        }
        if (numerator == denominator) {
            return true;
        }
        // Distances are measured in supported ISO days or months and fit in a long even when doubled.
        int comparison = Long.compare(Math.abs(numerator) * 2L, Math.abs(denominator));
        long magnitude = Math.abs(start);
        boolean even = (magnitude / settings.roundingIncrement()) % 2 == 0;
        TemporalUnsignedRoundingMode mode = settings.roundingMode().toUnsigned(TemporalSign.fromSignum(sign));
        return mode.getRoundedUnit(magnitude, Math.abs(end), comparison, even) == Math.abs(end);
    }

    private static TemporalDuration yearMonthUntil(JSContext context, IsoDate thisIsoDate, IsoDate otherIsoDate,
            TemporalCalendarId calendarId, TemporalDifferenceSettings differenceSettings) {
        if (thisIsoDate.equals(otherIsoDate)) {
            return TemporalDuration.ZERO;
        }

        if (!thisIsoDate.isWithinSupportedRange() || !otherIsoDate.isWithinSupportedRange()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        TemporalDurationYearMonth durationFields;
        long totalMonthsDifference;
        if (calendarId == TemporalCalendarId.ISO8601) {
            totalMonthsDifference = (long) (otherIsoDate.year() - thisIsoDate.year()) * 12L
                    + (otherIsoDate.month() - thisIsoDate.month());
            durationFields = TemporalDurationYearMonth.fromTotalMonths(totalMonthsDifference,
                    differenceSettings.largestUnit());
        } else {
            durationFields = TemporalDurationYearMonth.betweenCalendarDates(context, thisIsoDate, otherIsoDate,
                    calendarId, differenceSettings.largestUnit());
            if (context.hasPendingException() || durationFields == null) {
                return null;
            }
            TemporalDurationYearMonth monthDifferenceFields = TemporalDurationYearMonth.betweenCalendarDates(context,
                    thisIsoDate, otherIsoDate, calendarId, TemporalUnit.MONTH);
            if (context.hasPendingException() || monthDifferenceFields == null) {
                return null;
            }
            totalMonthsDifference = monthDifferenceFields.months();
        }
        boolean roundingNoOp = differenceSettings.smallestUnit() == TemporalUnit.MONTH
                && differenceSettings.roundingIncrement() == 1L;
        if (!roundingNoOp) {
            durationFields = roundRelativeYearMonthDuration(context, durationFields, totalMonthsDifference, thisIsoDate,
                    differenceSettings);
            if (context.hasPendingException() || durationFields == null) {
                return null;
            }
        }

        return new TemporalDuration(durationFields.years(), durationFields.months(), 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static TemporalDifferenceSettings zonedSettings(JSContext context, boolean sinceOperation,
            JSValue optionsArg) {
        TemporalDifferenceSettings settings = TemporalDifferenceSettings.parse(context, sinceOperation, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.NANOSECOND, TemporalUnit.NANOSECOND, TemporalUnit.HOUR, true, true);
        if (settings == null) {
            return null;
        }
        if (settings.smallestUnit() == TemporalUnit.DAY && Math.abs(settings.roundingIncrement()) > 100_000_000L) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }
        return settings;
    }
}
