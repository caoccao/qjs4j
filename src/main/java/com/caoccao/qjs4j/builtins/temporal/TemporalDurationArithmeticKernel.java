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
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);

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
        BigInteger timeNanoseconds = durationTimeToNanoseconds(durationRecord);
        return addNanosecondsToDateTime(context, dateBalancedDateTime, timeNanoseconds);
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

        BigInteger timeNanoseconds = durationTimeToNanoseconds(durationRecord);
        BigInteger endEpochNanoseconds = intermediateEpochNanoseconds.add(timeNanoseconds);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(endEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        IsoDateTime endIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                endEpochNanoseconds,
                relativeToOption.timeZoneId());
        LocalDateTime endDateTime = toLocalDateTime(endIsoDateTime.date(), endIsoDateTime.time());
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
        IsoDateTime resultIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                resultEpochNanoseconds,
                relativeToOption.timeZoneId());
        return toLocalDateTime(resultIsoDateTime.date(), resultIsoDateTime.time());
    }

    private static BigInteger durationTimeToNanoseconds(TemporalDuration durationRecord) {
        return BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
    }

    private static IsoDateTime toIsoDateTime(LocalDateTime localDateTime) {
        int nanosecondOfSecond = localDateTime.getNano();
        int millisecond = nanosecondOfSecond / 1_000_000;
        int microsecond = (nanosecondOfSecond / 1_000) % 1_000;
        int nanosecond = nanosecondOfSecond % 1_000;
        IsoDate isoDate = new IsoDate(
                localDateTime.getYear(),
                localDateTime.getMonthValue(),
                localDateTime.getDayOfMonth());
        IsoTime isoTime = new IsoTime(
                localDateTime.getHour(),
                localDateTime.getMinute(),
                localDateTime.getSecond(),
                millisecond,
                microsecond,
                nanosecond);
        return new IsoDateTime(isoDate, isoTime);
    }

    private static LocalDateTime toLocalDateTime(IsoDate isoDate, IsoTime isoTime) {
        return LocalDateTime.of(
                isoDate.year(),
                isoDate.month(),
                isoDate.day(),
                isoTime.hour(),
                isoTime.minute(),
                isoTime.second(),
                isoTime.millisecond() * 1_000_000
                        + isoTime.microsecond() * 1_000
                        + isoTime.nanosecond());
    }

    private static BigInteger zonedLocalDateTimeToEpochNanoseconds(
            JSContext context,
            TemporalRelativeToOption relativeToOption,
            LocalDateTime localDateTime) {
        IsoDateTime isoDateTime = toIsoDateTime(localDateTime);
        try {
            return TemporalTimeZone.localDateTimeToEpochNs(
                    isoDateTime,
                    relativeToOption.timeZoneId(),
                    "compatible");
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
    }
}
