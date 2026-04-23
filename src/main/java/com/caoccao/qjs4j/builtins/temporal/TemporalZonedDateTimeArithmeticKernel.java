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
            IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            TemporalCalendarId calendarId = zonedDateTime.getCalendarId();
            IsoDate addedDate;
            if (calendarId == TemporalCalendarId.ISO8601) {
                addedDate = localDateTime.date().addIsoDateWithOverflow(
                        context,
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

            IsoDateTime addedDateTime = addedDate.atTime(localDateTime.time());
            try {
                intermediateEpochNanoseconds = addedDateTime.toEpochNs(zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        BigInteger timeNanoseconds = durationRecord.timeNanoseconds();
        BigInteger resultEpochNanoseconds = intermediateEpochNanoseconds.add(timeNanoseconds);
        if (!TemporalUtils.isValidEpochNanoseconds(resultEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }
        return resultEpochNanoseconds;
    }

    static TemporalDuration differenceEpochNanoseconds(
            BigInteger startEpochNanoseconds,
            BigInteger endEpochNanoseconds,
            TemporalUnit largestUnit,
            long smallestUnitNanoseconds,
            long roundingIncrement,
            TemporalRoundingMode roundingMode) {
        BigInteger differenceNanoseconds = endEpochNanoseconds.subtract(startEpochNanoseconds);
        BigInteger incrementNanoseconds = BigInteger.valueOf(smallestUnitNanoseconds)
                .multiply(BigInteger.valueOf(roundingIncrement));
        BigInteger roundedNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(
                differenceNanoseconds,
                incrementNanoseconds);
        return TemporalDuration.createBalance(roundedNanoseconds, largestUnit);
    }
}
