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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.temporal.*;

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDateTime;

final class TemporalDurationArithmeticKernel {
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;

    private TemporalDurationArithmeticKernel() {
    }

    static LocalDateTime addCalendarUnits(LocalDateTime dateTime, String calendarUnit, long amount) {
        if ("year".equals(calendarUnit)) {
            return dateTime.plusYears(amount);
        } else if ("month".equals(calendarUnit)) {
            return dateTime.plusMonths(amount);
        } else if ("week".equals(calendarUnit)) {
            return dateTime.plusWeeks(amount);
        } else {
            return dateTime.plusDays(amount);
        }
    }

    static LocalDateTime addDurationToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            TemporalDuration durationRecord) {
        LocalDateTime dateBalancedDateTime = startDateTime
                .plusYears(durationRecord.years())
                .plusMonths(durationRecord.months())
                .plusWeeks(durationRecord.weeks())
                .plusDays(durationRecord.days());
        BigInteger timeNanoseconds = durationRecord.timeNanoseconds();
        return addNanosecondsToDateTime(context, dateBalancedDateTime, timeNanoseconds);
    }

    /**
     * Adds date-component duration (years, months, weeks, days) to an ISO date.
     * Shared by PlainDate and PlainDateTime add/subtract operations.
     */
    static IsoDate addDurationToIsoDate(
            JSContext context,
            IsoDate date,
            long years,
            long months,
            long weeks,
            long days,
            String overflow) {
        long totalDays;
        try {
            totalDays = Math.addExact(days, Math.multiplyExact(weeks, 7L));
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        long monthIndex = Math.addExact(date.month() - 1L, months);
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long balancedYear = Math.addExact(date.year(), years);
        balancedYear = Math.addExact(balancedYear, yearDelta);
        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int balancedYearInt = (int) balancedYear;
        int maxDay = IsoDate.daysInMonth(balancedYearInt, balancedMonth);
        int regulatedDay = date.day();
        if ("reject".equals(overflow)) {
            if (regulatedDay > maxDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            regulatedDay = Math.min(regulatedDay, maxDay);
        }

        IsoDate intermediateDate = new IsoDate(balancedYearInt, balancedMonth, regulatedDay);
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (resultEpochDay < TemporalConstants.MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > TemporalConstants.MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate isoDate = IsoDate.createFromEpochDay(resultEpochDay);
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return isoDate;
    }

    static TemporalZonedDateTimeComputation addDurationToZonedDateTime(
            JSContext context,
            TemporalRelativeToOption relativeToOption,
            TemporalDuration durationRecord) {
        LocalDateTime dateBalancedDateTime = relativeToOption.startDateTime()
                .plusYears(durationRecord.years())
                .plusMonths(durationRecord.months())
                .plusWeeks(durationRecord.weeks())
                .plusDays(durationRecord.days());
        BigInteger intermediateEpochNanoseconds;
        if (dateBalancedDateTime.equals(relativeToOption.startDateTime())) {
            intermediateEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            intermediateEpochNanoseconds =
                    zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, dateBalancedDateTime);
        }
        if (context.hasPendingException() || intermediateEpochNanoseconds == null) {
            return null;
        }

        BigInteger timeNanoseconds = durationRecord.timeNanoseconds();
        BigInteger endEpochNanoseconds = intermediateEpochNanoseconds.add(timeNanoseconds);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(endEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        IsoDateTime endIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                endEpochNanoseconds,
                relativeToOption.timeZoneId());
        LocalDateTime endDateTime = endIsoDateTime.toLocalDateTime();
        int endOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(
                endEpochNanoseconds,
                relativeToOption.timeZoneId());
        return new TemporalZonedDateTimeComputation(endDateTime, endEpochNanoseconds, endOffsetSeconds);
    }

    static LocalDateTime addFixedUnits(LocalDateTime dateTime, String unit, long amount) {
        if ("week".equals(unit)) {
            return dateTime.plusWeeks(amount);
        } else {
            return dateTime.plusDays(amount);
        }
    }

    static LocalDateTime addNanosecondsToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger nanoseconds) {
        BigInteger[] dayQuotientAndRemainder = nanoseconds.divideAndRemainder(DAY_NANOSECONDS);
        long dayAdjustment;
        try {
            dayAdjustment = dayQuotientAndRemainder[0].longValueExact();
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        BigInteger nanosecondRemainder = dayQuotientAndRemainder[1];
        if (nanosecondRemainder.signum() < 0) {
            dayAdjustment--;
            nanosecondRemainder = nanosecondRemainder.add(DAY_NANOSECONDS);
        }
        long nanosecondAdjustment = nanosecondRemainder.longValueExact();
        try {
            return startDateTime.plusDays(dayAdjustment).plusNanos(nanosecondAdjustment);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
    }

    static LocalDateTime addNanosecondsToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger nanoseconds,
            TemporalRelativeToOption relativeToOption) {
        if (relativeToOption == null || !relativeToOption.zoned()) {
            return addNanosecondsToDateTime(context, startDateTime, nanoseconds);
        }
        BigInteger startEpochNanoseconds;
        if (startDateTime.equals(relativeToOption.startDateTime())) {
            startEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            startEpochNanoseconds =
                    zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption, startDateTime);
        }
        if (context.hasPendingException() || startEpochNanoseconds == null) {
            return null;
        }
        BigInteger resultEpochNanoseconds = startEpochNanoseconds.add(nanoseconds);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(resultEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        IsoDateTime isoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                resultEpochNanoseconds,
                relativeToOption.timeZoneId());
        return isoDateTime.toLocalDateTime();
    }

    private static BigInteger zonedLocalDateTimeToEpochNanoseconds(
            JSContext context,
            TemporalRelativeToOption relativeToOption,
            LocalDateTime localDateTime) {
        IsoDateTime isoDateTime = IsoDateTime.createFromLocalDateTime(localDateTime);
        try {
            return isoDateTime.toEpochNs(
                    relativeToOption.timeZoneId(),
                    "compatible");
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
    }
}
