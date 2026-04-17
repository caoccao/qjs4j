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
import java.time.LocalDate;

/**
 * Implementation of Temporal.PlainDate prototype methods.
 */
public final class TemporalPlainDatePrototype {
    private static final long MIN_SUPPORTED_EPOCH_DAY = TemporalConstants.MIN_SUPPORTED_EPOCH_DAY;
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
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "add");
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
                TemporalDurationPrototype.balanceTimeDuration(totalTimeNanoseconds, "day");

        long totalDays;
        try {
            long weeksInDays = Math.multiplyExact(durationRecord.weeks(), 7L);
            totalDays = Math.addExact(durationRecord.days(), weeksInDays);
            totalDays = Math.addExact(totalDays, balancedTimeDuration.days());
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        return TemporalDurationArithmeticKernel.addDurationToIsoDate(
                context, date, durationRecord.years(), durationRecord.months(),
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

        String calendarId = plainDate.getCalendarId();
        IsoDate resultIsoDate;
        if ("iso8601".equals(calendarId)) {
            resultIsoDate = addDurationToDate(context, plainDate.getIsoDate(), durationRecord, overflow);
            if (context.hasPendingException() || resultIsoDate == null) {
                return JSUndefined.INSTANCE;
            }
        } else {
            BigInteger totalTimeNanoseconds = durationRecord.timeNanoseconds();
            TemporalDuration balancedTimeDuration =
                    TemporalDurationPrototype.balanceTimeDuration(totalTimeNanoseconds, "day");
            long dayDelta;
            try {
                dayDelta = Math.addExact(durationRecord.days(), balancedTimeDuration.days());
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            resultIsoDate = TemporalCalendarMath.addCalendarDate(
                    context,
                    plainDate.getIsoDate(),
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
        return TemporalPlainDateConstructor.createPlainDate(context, resultIsoDate, calendarId);
    }

    static IsoDate addToIsoDate(IsoDate date, int years, int months, int weeks, int days) {
        long monthIndex = (long) date.month() - 1L + months;
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int normalizedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        int normalizedYear = (int) (date.year() + years + yearDelta);
        int maxDay = IsoDate.daysInMonth(normalizedYear, normalizedMonth);
        int normalizedDay = Math.min(date.day(), maxDay);
        IsoDate intermediate = new IsoDate(normalizedYear, normalizedMonth, normalizedDay);
        long totalDays = (long) weeks * 7L + days;
        return intermediate.addDays((int) totalDays);
    }

    private static TemporalDateDurationFields adjustDateDurationRecord(
            TemporalDateDurationFields dateDuration,
            long newDays,
            Long newWeeks,
            Long newMonths) {
        long adjustedMonths = newMonths == null ? dateDuration.months() : newMonths;
        long adjustedWeeks = newWeeks == null ? dateDuration.weeks() : newWeeks;
        return new TemporalDateDurationFields(
                dateDuration.years(),
                adjustedMonths,
                adjustedWeeks,
                newDays);
    }


    private static TemporalYearMonthBalance balanceIsoYearMonth(long year, long month) {
        long monthIndex = month - 1;
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int normalizedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long normalizedYear = year + yearDelta;
        return new TemporalYearMonthBalance(normalizedYear, normalizedMonth);
    }

    private static TemporalDateDurationFields bubbleRelativeDuration(
            JSContext context,
            int sign,
            TemporalDateDurationFields duration,
            long nudgedEpochDay,
            IsoDate originDate,
            String calendarId,
            String largestUnit,
            String smallestUnit) {
        if (smallestUnit.equals(largestUnit)) {
            return duration;
        }
        int largestUnitIndex = TemporalUnit.rank(largestUnit);
        int smallestUnitIndex = TemporalUnit.rank(smallestUnit);
        for (int unitIndex = smallestUnitIndex - 1; unitIndex >= largestUnitIndex; unitIndex--) {
            String unit = temporalUnitByRank(unitIndex);
            if (UNIT_WEEK.equals(unit) && !UNIT_WEEK.equals(largestUnit)) {
                continue;
            }

            TemporalDateDurationFields endDuration;
            if (UNIT_YEAR.equals(unit)) {
                endDuration = new TemporalDateDurationFields(duration.years() + sign, 0, 0, 0);
            } else if (UNIT_MONTH.equals(unit)) {
                endDuration = adjustDateDurationRecord(duration, 0, 0L, duration.months() + sign);
            } else {
                endDuration = adjustDateDurationRecord(duration, 0, duration.weeks() + sign, null);
            }

            IsoDate endDate = calendarDateAddConstrain(context, originDate, calendarId, endDuration);
            if (context.hasPendingException() || endDate == null) {
                return null;
            }
            boolean didExpandToEnd = Long.compare(nudgedEpochDay, endDate.toEpochDay()) != -sign;
            if (didExpandToEnd) {
                duration = endDuration;
            } else {
                break;
            }
        }
        return duration;
    }

    private static IsoDate calendarDateAddConstrain(
            JSContext context,
            IsoDate baseDate,
            String calendarId,
            TemporalDateDurationFields dateDuration) {
        if ("iso8601".equals(calendarId)) {
            TemporalDuration durationRecord = new TemporalDuration(
                    dateDuration.years(),
                    dateDuration.months(),
                    dateDuration.weeks(),
                    dateDuration.days(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0);
            return addDurationToDate(context, baseDate, durationRecord, "constrain");
        }
        return TemporalCalendarMath.addCalendarDate(
                context,
                baseDate,
                calendarId,
                dateDuration.years(),
                dateDuration.months(),
                dateDuration.weeks(),
                dateDuration.days(),
                "constrain");
    }

    private static TemporalDateDurationFields calendarDateUntil(
            JSContext context,
            IsoDate firstDate,
            IsoDate secondDate,
            String calendarId,
            String largestUnit) {
        if ("iso8601".equals(calendarId)) {
            return calendarDateUntilIso(firstDate, secondDate, largestUnit);
        }
        int sign = -Integer.signum(firstDate.compareTo(secondDate));
        if (sign == 0) {
            return TemporalDateDurationFields.ZERO;
        }

        long years = 0;
        long months = 0;
        if (UNIT_YEAR.equals(largestUnit) || UNIT_MONTH.equals(largestUnit)) {
            IsoCalendarDate firstCalendarDateFields = firstDate.toIsoCalendarDate(calendarId);
            IsoCalendarDate secondCalendarDateFields = secondDate.toIsoCalendarDate(calendarId);
            long candidateYears = (long) secondCalendarDateFields.year() - firstCalendarDateFields.year();
            if (candidateYears != 0L) {
                candidateYears -= sign;
            }
            while (true) {
                TemporalDateDurationFields candidateYearDuration = new TemporalDateDurationFields(candidateYears, 0, 0, 0);
                IsoDate candidateYearDate = calendarDateAddConstrain(context, firstDate, calendarId, candidateYearDuration);
                if (context.hasPendingException() || candidateYearDate == null) {
                    return null;
                }
                if (TemporalUtils.isoDateSurpasses(sign, candidateYearDate, secondDate)) {
                    break;
                }
                if (doesConceptualYearDateSurpassSecondDate(
                        sign,
                        firstCalendarDateFields,
                        candidateYears,
                        secondCalendarDateFields)) {
                    break;
                }
                if (doesConstrainedCalendarDaySurpassSecondDate(
                        sign,
                        firstCalendarDateFields,
                        candidateYearDate,
                        secondCalendarDateFields,
                        calendarId)) {
                    break;
                }
                years = candidateYears;
                candidateYears += sign;
            }

            long candidateMonths = sign;
            while (true) {
                TemporalDateDurationFields candidateMonthDuration = new TemporalDateDurationFields(years, candidateMonths, 0, 0);
                IsoDate candidateMonthDate = calendarDateAddConstrain(context, firstDate, calendarId, candidateMonthDuration);
                if (context.hasPendingException() || candidateMonthDate == null) {
                    return null;
                }
                if (TemporalUtils.isoDateSurpasses(sign, candidateMonthDate, secondDate)) {
                    break;
                }
                if (doesConstrainedCalendarDaySurpassSecondDate(
                        sign,
                        firstCalendarDateFields,
                        candidateMonthDate,
                        secondCalendarDateFields,
                        calendarId)) {
                    break;
                }
                months = candidateMonths;
                candidateMonths += sign;
            }

            if (UNIT_MONTH.equals(largestUnit)) {
                long monthsFromYears = monthsForYearDelta(context, firstDate, calendarId, years);
                if (context.hasPendingException()) {
                    return null;
                }
                long totalMonths;
                try {
                    totalMonths = Math.addExact(months, monthsFromYears);
                } catch (ArithmeticException arithmeticException) {
                    context.throwRangeError("Temporal error: Duration field out of range.");
                    return null;
                }
                while (true) {
                    TemporalDateDurationFields totalMonthDuration = new TemporalDateDurationFields(0, totalMonths, 0, 0);
                    IsoDate totalMonthDate = calendarDateAddConstrain(context, firstDate, calendarId, totalMonthDuration);
                    if (context.hasPendingException() || totalMonthDate == null) {
                        return null;
                    }
                    if (TemporalUtils.isoDateSurpasses(sign, totalMonthDate, secondDate)) {
                        totalMonths -= sign;
                        continue;
                    }
                    if (doesConstrainedCalendarDaySurpassSecondDate(
                            sign,
                            firstCalendarDateFields,
                            totalMonthDate,
                            secondCalendarDateFields,
                            calendarId)) {
                        totalMonths -= sign;
                        continue;
                    }

                    long nextTotalMonths;
                    try {
                        nextTotalMonths = Math.addExact(totalMonths, sign);
                    } catch (ArithmeticException arithmeticException) {
                        break;
                    }
                    TemporalDateDurationFields nextTotalMonthDuration = new TemporalDateDurationFields(0, nextTotalMonths, 0, 0);
                    IsoDate nextTotalMonthDate = calendarDateAddConstrain(context, firstDate, calendarId, nextTotalMonthDuration);
                    if (context.hasPendingException() || nextTotalMonthDate == null) {
                        return null;
                    }
                    if (TemporalUtils.isoDateSurpasses(sign, nextTotalMonthDate, secondDate)) {
                        break;
                    }
                    if (doesConstrainedCalendarDaySurpassSecondDate(
                            sign,
                            firstCalendarDateFields,
                            nextTotalMonthDate,
                            secondCalendarDateFields,
                            calendarId)) {
                        break;
                    }
                    totalMonths = nextTotalMonths;
                }
                months = totalMonths;
                years = 0;
            }
        }

        TemporalDateDurationFields intermediateDuration = new TemporalDateDurationFields(years, months, 0, 0);
        IsoDate constrainedDate = calendarDateAddConstrain(context, firstDate, calendarId, intermediateDuration);
        if (context.hasPendingException() || constrainedDate == null) {
            return null;
        }
        long dayDifference = secondDate.toEpochDay() - constrainedDate.toEpochDay();
        long weeks = 0;
        if (UNIT_WEEK.equals(largestUnit)) {
            weeks = dayDifference / 7;
            dayDifference = dayDifference % 7;
        }
        return new TemporalDateDurationFields(years, months, weeks, dayDifference);
    }

    private static TemporalDateDurationFields calendarDateUntilIso(IsoDate firstDate, IsoDate secondDate, String largestUnit) {
        int sign = -Integer.signum(firstDate.compareTo(secondDate));
        if (sign == 0) {
            return TemporalDateDurationFields.ZERO;
        }

        long years = 0;
        long months = 0;
        if (UNIT_YEAR.equals(largestUnit) || UNIT_MONTH.equals(largestUnit)) {
            long candidateYears = (long) secondDate.year() - firstDate.year();
            if (candidateYears != 0) {
                candidateYears -= sign;
            }
            while (!isoDateSurpasses(sign, firstDate.year() + candidateYears, firstDate.month(), firstDate.day(), secondDate)) {
                years = candidateYears;
                candidateYears += sign;
            }

            long candidateMonths = sign;
            TemporalYearMonthBalance intermediateYearMonth = balanceIsoYearMonth(firstDate.year() + years, firstDate.month() + candidateMonths);
            while (!isoDateSurpasses(sign, intermediateYearMonth.year(), intermediateYearMonth.month(), firstDate.day(), secondDate)) {
                months = candidateMonths;
                candidateMonths += sign;
                intermediateYearMonth = balanceIsoYearMonth(intermediateYearMonth.year(), intermediateYearMonth.month() + sign);
            }

            if (UNIT_MONTH.equals(largestUnit)) {
                months += years * 12;
                years = 0;
            }
        }

        TemporalYearMonthBalance intermediateYearMonth = balanceIsoYearMonth(firstDate.year() + years, firstDate.month() + months);
        IsoDate constrainedDate = constrainIsoDate(intermediateYearMonth.year(), intermediateYearMonth.month(), firstDate.day());
        long dayDifference = secondDate.toEpochDay() - constrainedDate.toEpochDay();
        long weeks = 0;
        if (UNIT_WEEK.equals(largestUnit)) {
            weeks = dayDifference / 7;
            dayDifference = dayDifference % 7;
        }
        return new TemporalDateDurationFields(years, months, weeks, dayDifference);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "calendarId");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.getCalendarId());
    }

    private static boolean calendarSupportsEraFields(String calendarId) {
        return !"iso8601".equals(calendarId)
                && !"chinese".equals(calendarId)
                && !"dangi".equals(calendarId);
    }

    private static JSTemporalPlainDate checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        return TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDate.class, TYPE_NAME, methodName);
    }

    private static int compareCalendarDateFields(
            long firstYear,
            String firstMonthCode,
            int firstDay,
            int secondYear,
            String secondMonthCode,
            int secondDay) {
        int yearComparison = Long.compare(firstYear, secondYear);
        if (yearComparison != 0) {
            return yearComparison;
        }
        int monthComparison = compareMonthCodes(firstMonthCode, secondMonthCode);
        if (monthComparison != 0) {
            return monthComparison;
        }
        return Integer.compare(firstDay, secondDay);
    }

    private static int compareMonthCodes(String firstMonthCode, String secondMonthCode) {
        TemporalParsedMonthCode firstMonthCodeParts = parseMonthCodeParts(firstMonthCode);
        TemporalParsedMonthCode secondMonthCodeParts = parseMonthCodeParts(secondMonthCode);
        if (firstMonthCodeParts == null || secondMonthCodeParts == null) {
            return firstMonthCode.compareTo(secondMonthCode);
        }
        int monthNumberComparison = Integer.compare(
                firstMonthCodeParts.month(),
                secondMonthCodeParts.month());
        if (monthNumberComparison != 0) {
            return monthNumberComparison;
        }
        if (firstMonthCodeParts.leapMonth() == secondMonthCodeParts.leapMonth()) {
            return 0;
        }
        return firstMonthCodeParts.leapMonth() ? 1 : -1;
    }

    private static IsoDate constrainIsoDate(long year, int month, int dayOfMonth) {
        int constrainedYear = (int) year;
        int constrainedDay = Math.min(dayOfMonth, IsoDate.daysInMonth(constrainedYear, month));
        return new IsoDate(constrainedYear, month, constrainedDay);
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "day");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = resolveCalendarDate(context, plainDate);
        return JSNumber.of(calendarDate.day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "dayOfWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "dayOfYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalCalendarMath.dayOfYear(plainDate.getIsoDate(), plainDate.getCalendarId()));
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "daysInMonth");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalCalendarMath.daysInMonth(plainDate.getIsoDate(), plainDate.getCalendarId()));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "daysInWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "daysInYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalCalendarMath.daysInYear(plainDate.getIsoDate(), plainDate.getCalendarId()));
    }

    static TemporalDuration differenceCalendarDates(
            JSContext context,
            IsoDate firstDate,
            IsoDate secondDate,
            String calendarId,
            String largestUnit) {
        TemporalDateDurationFields dateDifference = calendarDateUntil(
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
        TemporalDifferenceSettings settings = getDifferenceSettings(context, sinceOperation, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate thisDate = plainDate.getIsoDate();
        IsoDate otherDate = other.getIsoDate();
        if (thisDate.compareTo(otherDate) == 0) {
            return TemporalDurationConstructor.createDuration(context, TemporalDuration.ZERO);
        }

        TemporalDateDurationFields dateDifference = calendarDateUntil(
                context,
                thisDate,
                otherDate,
                plainDate.getCalendarId(),
                settings.largestUnit());
        boolean roundingNoOp = UNIT_DAY.equals(settings.smallestUnit()) && settings.roundingIncrement() == 1L;
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
        return TemporalDurationConstructor.createDuration(context, resultDuration);
    }

    private static boolean doesConceptualYearDateSurpassSecondDate(
            int sign,
            IsoCalendarDate firstCalendarDateFields,
            long candidateYears,
            IsoCalendarDate secondCalendarDateFields) {
        long candidateYear = firstCalendarDateFields.year() + candidateYears;
        int comparison = compareCalendarDateFields(
                candidateYear,
                firstCalendarDateFields.monthCode(),
                firstCalendarDateFields.day(),
                secondCalendarDateFields.year(),
                secondCalendarDateFields.monthCode(),
                secondCalendarDateFields.day());
        return sign * comparison > 0;
    }

    private static boolean doesConstrainedCalendarDaySurpassSecondDate(
            int sign,
            IsoCalendarDate firstCalendarDateFields,
            IsoDate constrainedCandidateDate,
            IsoCalendarDate secondCalendarDateFields,
            String calendarId) {
        IsoCalendarDate candidateCalendarDateFields = constrainedCandidateDate.toIsoCalendarDate(calendarId);
        if (candidateCalendarDateFields.day() == firstCalendarDateFields.day()) {
            return false;
        }
        if (candidateCalendarDateFields.year() != secondCalendarDateFields.year()) {
            return false;
        }
        if (!candidateCalendarDateFields.monthCode().equals(secondCalendarDateFields.monthCode())) {
            return false;
        }
        return sign * Integer.compare(firstCalendarDateFields.day(), secondCalendarDateFields.day()) > 0;
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "equals");
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
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "era");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraFields eraFields = resolveEraFields(plainDate);
        if (eraFields == null || eraFields.era() == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(eraFields.era());
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "eraYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraFields eraFields = resolveEraFields(plainDate);
        if (eraFields == null || eraFields.eraYear() == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(eraFields.eraYear());
    }

    private static TemporalDifferenceSettings getDifferenceSettings(
            JSContext context,
            boolean sinceOperation,
            JSValue optionsArg) {
        return TemporalDifferenceSettings.parse(
                context, sinceOperation, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.DAY,
                TemporalUnit.DAY, TemporalUnit.DAY,
                true, false);
    }


    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "inLeapYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalCalendarMath.inLeapYear(plainDate.getIsoDate(), plainDate.getCalendarId())
                ? JSBoolean.TRUE
                : JSBoolean.FALSE;
    }

    private static boolean isCalendarUnit(String unit) {
        return UNIT_YEAR.equals(unit) || UNIT_MONTH.equals(unit) || UNIT_WEEK.equals(unit);
    }

    private static boolean isoDateSurpasses(int sign, long year, long month, long dayOfMonth, IsoDate isoDate) {
        if (year != isoDate.year()) {
            return sign * (year - isoDate.year()) > 0;
        }
        if (month != isoDate.month()) {
            return sign * (month - isoDate.month()) > 0;
        }
        if (dayOfMonth != isoDate.day()) {
            return sign * (dayOfMonth - isoDate.day()) > 0;
        }
        return false;
    }


    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "month");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = resolveCalendarDate(context, plainDate);
        return JSNumber.of(calendarDate.month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "monthCode");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(resolveCalendarMonthCode(context, plainDate));
    }

    private static long monthsForYearDelta(
            JSContext context,
            IsoDate firstDate,
            String calendarId,
            long yearDelta) {
        if (yearDelta == 0L) {
            return 0L;
        }
        if ("coptic".equals(calendarId) || "ethiopic".equals(calendarId) || "ethioaa".equals(calendarId)) {
            try {
                return Math.multiplyExact(yearDelta, 13L);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
        }
        if ("iso8601".equals(calendarId)
                || "gregory".equals(calendarId)
                || "japanese".equals(calendarId)
                || "buddhist".equals(calendarId)
                || "roc".equals(calendarId)
                || "indian".equals(calendarId)
                || "persian".equals(calendarId)
                || "islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)) {
            try {
                return Math.multiplyExact(yearDelta, 12L);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
        }

        int yearSign = yearDelta > 0L ? 1 : -1;
        long absoluteYearDelta = Math.abs(yearDelta);
        long totalMonths = 0L;
        IsoDate cursorDate = firstDate;
        for (long yearIndex = 0L; yearIndex < absoluteYearDelta; yearIndex++) {
            int monthsInYear = TemporalCalendarMath.monthsInYear(cursorDate, calendarId);
            try {
                totalMonths = Math.addExact(totalMonths, (long) yearSign * monthsInYear);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
            TemporalDateDurationFields oneYearDuration = new TemporalDateDurationFields(yearSign, 0, 0, 0);
            cursorDate = calendarDateAddConstrain(context, cursorDate, calendarId, oneYearDuration);
            if (context.hasPendingException() || cursorDate == null) {
                return 0L;
            }
        }
        return totalMonths;
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "monthsInYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalCalendarMath.monthsInYear(plainDate.getIsoDate(), plainDate.getCalendarId()));
    }

    private static TemporalNudgeResult nudgeToCalendarUnit(
            JSContext context,
            int sign,
            TemporalDateDurationFields duration,
            long destinationEpochDay,
            IsoDate originDate,
            String calendarId,
            TemporalDifferenceSettings settings) {
        String smallestUnit = settings.smallestUnit();
        long increment = settings.roundingIncrement();
        long roundingStartValue;
        long roundingEndValue;
        TemporalDateDurationFields startDuration;
        TemporalDateDurationFields endDuration;
        if (UNIT_YEAR.equals(smallestUnit)) {
            long roundedYears = TemporalMathKernel.roundNumberToIncrement(duration.years(), increment, "trunc");
            roundingStartValue = roundedYears;
            roundingEndValue = roundedYears + increment * sign;
            startDuration = new TemporalDateDurationFields(roundedYears, 0, 0, 0);
            endDuration = new TemporalDateDurationFields(roundingEndValue, 0, 0, 0);
        } else if (UNIT_MONTH.equals(smallestUnit)) {
            long roundedMonths = TemporalMathKernel.roundNumberToIncrement(duration.months(), increment, "trunc");
            roundingStartValue = roundedMonths;
            roundingEndValue = roundedMonths + increment * sign;
            startDuration = adjustDateDurationRecord(duration, 0, 0L, roundedMonths);
            endDuration = adjustDateDurationRecord(duration, 0, 0L, roundingEndValue);
        } else if (UNIT_WEEK.equals(smallestUnit)) {
            TemporalDateDurationFields yearsAndMonthsDuration = adjustDateDurationRecord(duration, 0, 0L, null);
            IsoDate weeksStart = calendarDateAddConstrain(context, originDate, calendarId, yearsAndMonthsDuration);
            if (context.hasPendingException() || weeksStart == null) {
                return null;
            }
            long weeksEndEpochDay = weeksStart.toEpochDay() + duration.days();
            IsoDate weeksEnd = IsoDate.createFromEpochDay(weeksEndEpochDay);
            TemporalDateDurationFields weekDifference = calendarDateUntil(
                    context,
                    weeksStart,
                    weeksEnd,
                    calendarId,
                    UNIT_WEEK);
            if (context.hasPendingException() || weekDifference == null) {
                return null;
            }
            long roundedWeeks = TemporalMathKernel.roundNumberToIncrement(duration.weeks() + weekDifference.weeks(), increment, "trunc");
            roundingStartValue = roundedWeeks;
            roundingEndValue = roundedWeeks + increment * sign;
            startDuration = adjustDateDurationRecord(duration, 0, roundedWeeks, null);
            endDuration = adjustDateDurationRecord(duration, 0, roundingEndValue, null);
        } else {
            long roundedDays = TemporalMathKernel.roundNumberToIncrement(duration.days(), increment, "trunc");
            roundingStartValue = roundedDays;
            roundingEndValue = roundedDays + increment * sign;
            startDuration = adjustDateDurationRecord(duration, roundedDays, null, null);
            endDuration = adjustDateDurationRecord(duration, roundingEndValue, null, null);
        }

        IsoDate startDate = calendarDateAddConstrain(context, originDate, calendarId, startDuration);
        if (context.hasPendingException() || startDate == null) {
            return null;
        }
        IsoDate endDate = calendarDateAddConstrain(context, originDate, calendarId, endDuration);
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

        String unsignedRoundingMode = TemporalMathKernel.getUnsignedRoundingMode(
                settings.roundingMode().jsName(),
                sign < 0 ? "negative" : "positive");
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
            roundedUnit = TemporalMathKernel.applyUnsignedRoundingMode(
                    Math.abs(roundingStartValue),
                    Math.abs(roundingEndValue),
                    roundingComparison,
                    isEvenCardinality,
                    unsignedRoundingMode);
        }

        boolean didExpandCalendarUnit = roundedUnit == Math.abs(roundingEndValue);
        TemporalDateDurationFields roundedDuration = didExpandCalendarUnit ? endDuration : startDuration;
        long nudgedEpochDay = didExpandCalendarUnit ? endEpochDay : startEpochDay;
        return new TemporalNudgeResult(roundedDuration, nudgedEpochDay, didExpandCalendarUnit);
    }

    private static TemporalNudgeResult nudgeToDayUnit(
            TemporalDateDurationFields duration,
            long destinationEpochDay,
            TemporalDifferenceSettings settings) {
        long originalDays = duration.days();
        long roundedDays = TemporalMathKernel.roundNumberToIncrement(originalDays, settings.roundingIncrement(), settings.roundingMode().jsName());
        long dayDelta = roundedDays - originalDays;
        int durationSign = Long.compare(originalDays, 0);
        int deltaSign = Long.compare(dayDelta, 0);
        boolean didExpandCalendarUnit = deltaSign == durationSign;
        TemporalDateDurationFields roundedDuration = adjustDateDurationRecord(duration, roundedDays, null, null);
        long nudgedEpochDay = destinationEpochDay + dayDelta;
        return new TemporalNudgeResult(roundedDuration, nudgedEpochDay, didExpandCalendarUnit);
    }

    private static TemporalParsedMonthCode parseMonthCodeParts(String monthCodeText) {
        return TemporalCalendarMath.parseMonthCode(monthCodeText);
    }

    private static IsoDate resolveCalendarDate(JSContext context, JSTemporalPlainDate plainDate) {
        IsoCalendarDate calendarDateFields =
                plainDate.getIsoDate().toIsoCalendarDate(plainDate.getCalendarId());
        return new IsoDate(
                calendarDateFields.year(),
                calendarDateFields.month(),
                calendarDateFields.day());
    }

    private static String resolveCalendarMonthCode(JSContext context, JSTemporalPlainDate plainDate) {
        IsoCalendarDate calendarDateFields =
                plainDate.getIsoDate().toIsoCalendarDate(plainDate.getCalendarId());
        return calendarDateFields.monthCode();
    }

    private static TemporalEraFields resolveEraFields(JSTemporalPlainDate plainDate) {
        String calendarId = plainDate.getCalendarId();
        if ("iso8601".equals(calendarId) || "chinese".equals(calendarId) || "dangi".equals(calendarId)) {
            return null;
        }

        IsoDate isoDate = plainDate.getIsoDate();
        IsoDate calendarDate = resolveCalendarDate(plainDate.getContext(), plainDate);
        int calendarYear = calendarDate.year();

        if ("gregory".equals(calendarId)) {
            if (calendarYear <= 0) {
                return new TemporalEraFields("bce", 1 - calendarYear);
            }
            return new TemporalEraFields("ce", calendarYear);
        }

        if ("japanese".equals(calendarId)) {
            LocalDate date = LocalDate.of(isoDate.year(), isoDate.month(), isoDate.day());
            LocalDate reiwaStart = LocalDate.of(2019, 5, 1);
            LocalDate heiseiStart = LocalDate.of(1989, 1, 8);
            LocalDate showaStart = LocalDate.of(1926, 12, 25);
            LocalDate taishoStart = LocalDate.of(1912, 7, 30);
            LocalDate meijiStart = LocalDate.of(1873, 1, 1);

            if (!date.isBefore(reiwaStart)) {
                return new TemporalEraFields("reiwa", isoDate.year() - 2018);
            }
            if (!date.isBefore(heiseiStart)) {
                return new TemporalEraFields("heisei", isoDate.year() - 1988);
            }
            if (!date.isBefore(showaStart)) {
                return new TemporalEraFields("showa", isoDate.year() - 1925);
            }
            if (!date.isBefore(taishoStart)) {
                return new TemporalEraFields("taisho", isoDate.year() - 1911);
            }
            if (!date.isBefore(meijiStart)) {
                return new TemporalEraFields("meiji", isoDate.year() - 1867);
            }
            if (isoDate.year() <= 0) {
                return new TemporalEraFields("bce", 1 - isoDate.year());
            }
            return new TemporalEraFields("ce", isoDate.year());
        }

        if ("roc".equals(calendarId)) {
            if (calendarYear >= 1) {
                return new TemporalEraFields("roc", calendarYear);
            }
            return new TemporalEraFields("broc", 1 - calendarYear);
        }

        if ("buddhist".equals(calendarId)) {
            return new TemporalEraFields("be", calendarYear);
        }
        if ("coptic".equals(calendarId)) {
            return new TemporalEraFields("am", calendarYear);
        }
        if ("ethiopic".equals(calendarId)) {
            if (calendarYear <= 0) {
                return new TemporalEraFields("aa", 5500 + calendarYear);
            }
            return new TemporalEraFields("am", calendarYear);
        }
        if ("ethioaa".equals(calendarId)) {
            return new TemporalEraFields("aa", calendarYear);
        }
        if ("hebrew".equals(calendarId)) {
            return new TemporalEraFields("am", calendarYear);
        }
        if ("indian".equals(calendarId)) {
            return new TemporalEraFields("shaka", calendarYear);
        }
        if ("persian".equals(calendarId)) {
            return new TemporalEraFields("ap", calendarYear);
        }
        if ("islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)) {
            if (calendarYear > 0) {
                return new TemporalEraFields("ah", calendarYear);
            }
            return new TemporalEraFields("bh", 1 - calendarYear);
        }

        return null;
    }


    private static TemporalDateDurationFields roundRelativeDurationDate(
            JSContext context,
            TemporalDateDurationFields duration,
            long destinationEpochDay,
            IsoDate originDate,
            String calendarId,
            TemporalDifferenceSettings settings) {
        int sign = duration.sign() < 0 ? -1 : 1;
        TemporalNudgeResult nudgeResult;
        if (isCalendarUnit(settings.smallestUnit())) {
            nudgeResult = nudgeToCalendarUnit(
                    context,
                    sign,
                    duration,
                    destinationEpochDay,
                    originDate,
                    calendarId,
                    settings);
        } else {
            nudgeResult = nudgeToDayUnit(duration, destinationEpochDay, settings);
        }
        if (context.hasPendingException() || nudgeResult == null) {
            return null;
        }
        TemporalDateDurationFields roundedDuration = nudgeResult.duration();
        if (nudgeResult.didExpandCalendarUnit() && !UNIT_WEEK.equals(settings.smallestUnit())) {
            String bubbleSmallestUnit = TemporalUnit.larger(settings.smallestUnit(), UNIT_DAY);
            roundedDuration = bubbleRelativeDuration(
                    context,
                    sign,
                    roundedDuration,
                    nudgeResult.nudgedEpochDay(),
                    originDate,
                    calendarId,
                    settings.largestUnit(),
                    bubbleSmallestUnit);
            if (context.hasPendingException() || roundedDuration == null) {
                return null;
            }
        }
        return roundedDuration;
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "since");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainDate(context, plainDate, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "subtract");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDate, args, -1);
    }

    private static String temporalUnitByRank(int unitRank) {
        TemporalUnit[] units = TemporalUnit.values();
        if (unitRank >= 0 && unitRank < units.length) {
            return units[unitRank].jsName();
        }
        return TemporalUnit.NANOSECOND.jsName();
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toJSON");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDate.getIsoDate().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toLocaleString");
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
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toPlainDateTime");
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
        if (isoDate.toEpochDay() == MIN_SUPPORTED_EPOCH_DAY && time.compareTo(IsoTime.MIDNIGHT) == 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new IsoDateTime(isoDate, time), plainDate.getCalendarId());
    }

    public static JSValue toPlainMonthDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toPlainMonthDay");
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
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toPlainYearMonth");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainDate.getIsoDate();
        IsoDate plainYearMonthIsoDate;
        if ("iso8601".equals(plainDate.getCalendarId())) {
            plainYearMonthIsoDate = new IsoDate(isoDate.year(), isoDate.month(), 1);
        } else {
            plainYearMonthIsoDate = isoDate;
        }
        return TemporalPlainYearMonthConstructor.createPlainYearMonth(
                context,
                plainYearMonthIsoDate,
                plainDate.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toString");
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
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "toZonedDateTime");
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
            if (isoDate.toEpochDay() == MIN_SUPPORTED_EPOCH_DAY
                    && isoTime.compareTo(IsoTime.MIDNIGHT) == 0) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        BigInteger epochNanoseconds;
        try {
            if (hasPlainTimeArgument) {
                epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                        new IsoDateTime(isoDate, isoTime),
                        timeZoneId);
            } else {
                epochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(isoDate, timeZoneId);
            }
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(
                context,
                epochNanoseconds,
                timeZoneId,
                plainDate.getCalendarId());
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "until");
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
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "weekOfYear");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (!"iso8601".equals(plainDate.getCalendarId())) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "with");
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

        String calendarId = plainDate.getCalendarId();
        boolean calendarSupportsEraFields = calendarSupportsEraFields(calendarId);

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

        IsoDate calendarDate = resolveCalendarDate(context, plainDate);
        if (context.hasPendingException() || calendarDate == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarMonthCode = resolveCalendarMonthCode(context, plainDate);
        if (context.hasPendingException() || calendarMonthCode == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraFields eraFields = resolveEraFields(plainDate);

        JSObject mergedFieldsObject = new JSObject(context);
        mergedFieldsObject.set(PropertyKey.fromString("calendar"), new JSString(calendarId));
        mergedFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(calendarDate.day()));
        if ("iso8601".equals(calendarId)) {
            mergedFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(calendarDate.month()));
        }
        mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(calendarMonthCode));
        mergedFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(calendarDate.year()));
        if (eraFields != null) {
            if (eraFields.era() != null) {
                mergedFieldsObject.set(PropertyKey.fromString("era"), new JSString(eraFields.era()));
            }
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
                String constrainedMonthCode = TemporalCalendarMath.constrainMonthCode(
                        calendarId,
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
        return TemporalPlainDateConstructor.createPlainDate(
                context,
                mergedDate.getIsoDate(),
                calendarId);
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "withCalendar");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] == null) {
            context.throwTypeError("Temporal error: Calendar is required.");
            return JSUndefined.INSTANCE;
        }
        String calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateConstructor.createPlainDate(context, plainDate.getIsoDate(), calendarId);
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "year");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = resolveCalendarDate(context, plainDate);
        return JSNumber.of(calendarDate.year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDate plainDate = checkReceiver(context, thisArg, "yearOfWeek");
        if (plainDate == null) {
            return JSUndefined.INSTANCE;
        }
        if (!"iso8601".equals(plainDate.getCalendarId())) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDate.getIsoDate().yearOfWeek());
    }
}
