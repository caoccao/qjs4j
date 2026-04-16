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
import com.caoccao.qjs4j.core.JSTemporalZonedDateTime;
import com.caoccao.qjs4j.core.temporal.*;

import java.math.BigInteger;
import java.time.DateTimeException;

final class TemporalZonedDateTimeArithmeticKernel {
    private static final BigInteger NS_PER_HOUR = TemporalConstants.BI_HOUR_NANOSECONDS;
    private static final BigInteger NS_PER_MINUTE = TemporalConstants.BI_MINUTE_NANOSECONDS;
    private static final BigInteger NS_PER_MS = TemporalConstants.BI_MILLISECOND_NANOSECONDS;
    private static final BigInteger NS_PER_SECOND = TemporalConstants.BI_SECOND_NANOSECONDS;

    private TemporalZonedDateTimeArithmeticKernel() {
    }

    static BigInteger addDurationToZonedDateTime(
            JSContext context,
            JSTemporalZonedDateTime zonedDateTime,
            TemporalDuration durationRecord,
            String overflow) {
        BigInteger intermediateEpochNanoseconds = zonedDateTime.getEpochNanoseconds();
        if (durationRecord.years() != 0
                || durationRecord.months() != 0
                || durationRecord.weeks() != 0
                || durationRecord.days() != 0) {
            IsoDateTime localDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            String calendarId = zonedDateTime.getCalendarId();
            IsoDate addedDate;
            if ("iso8601".equals(calendarId)) {
                addedDate = addIsoDateWithOverflow(
                        context,
                        localDateTime.date(),
                        durationRecord.years(),
                        durationRecord.months(),
                        durationRecord.weeks(),
                        durationRecord.days(),
                        overflow);
            } else {
                addedDate = TemporalCalendarMath.addCalendarDate(
                        context,
                        localDateTime.date(),
                        calendarId,
                        durationRecord.years(),
                        durationRecord.months(),
                        durationRecord.weeks(),
                        durationRecord.days(),
                        overflow);
            }
            if (context.hasPendingException() || addedDate == null) {
                return null;
            }

            IsoDateTime addedDateTime = new IsoDateTime(addedDate, localDateTime.time());
            try {
                intermediateEpochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                        addedDateTime,
                        zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        BigInteger timeNanoseconds = durationTimeToNanoseconds(durationRecord);
        BigInteger resultEpochNanoseconds = intermediateEpochNanoseconds.add(timeNanoseconds);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(resultEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }
        return resultEpochNanoseconds;
    }

    private static IsoDate addIsoDateWithOverflow(
            JSContext context,
            IsoDate date,
            long years,
            long months,
            long weeks,
            long days,
            String overflow) {
        long monthIndex;
        long balancedYear;
        try {
            monthIndex = Math.addExact(date.month() - 1L, months);
            long balancedYearDelta = Math.floorDiv(monthIndex, 12L);
            balancedYear = Math.addExact(date.year(), years);
            balancedYear = Math.addExact(balancedYear, balancedYearDelta);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        int balancedYearInt = (int) balancedYear;
        int maximumDay = IsoDate.daysInMonth(balancedYearInt, balancedMonth);
        int balancedDay = date.day();
        if ("reject".equals(overflow)) {
            if (balancedDay > maximumDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            balancedDay = Math.min(balancedDay, maximumDay);
        }

        IsoDate intermediateDate = new IsoDate(balancedYearInt, balancedMonth, balancedDay);
        long resultEpochDay;
        try {
            long daysFromWeeks = Math.multiplyExact(weeks, 7L);
            long totalDays = Math.addExact(days, daysFromWeeks);
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        try {
            return IsoDate.createFromEpochDay(resultEpochDay);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
    }

    static TemporalDuration differenceEpochNanoseconds(
            BigInteger startEpochNanoseconds,
            BigInteger endEpochNanoseconds,
            String largestUnit,
            long smallestUnitNanoseconds,
            long roundingIncrement,
            String roundingMode) {
        BigInteger differenceNanoseconds = endEpochNanoseconds.subtract(startEpochNanoseconds);
        BigInteger incrementNanoseconds = BigInteger.valueOf(smallestUnitNanoseconds)
                .multiply(BigInteger.valueOf(roundingIncrement));
        BigInteger roundedNanoseconds = TemporalMathKernel.roundBigIntegerToIncrementSigned(
                differenceNanoseconds,
                incrementNanoseconds,
                roundingMode);
        return TemporalDurationPrototype.balanceTimeDuration(roundedNanoseconds, largestUnit);
    }

    private static BigInteger durationTimeToNanoseconds(TemporalDuration durationRecord) {
        return BigInteger.valueOf(durationRecord.hours()).multiply(NS_PER_HOUR)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(NS_PER_MINUTE))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(NS_PER_SECOND))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(NS_PER_MS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(BigInteger.valueOf(1_000L)))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
    }
}
