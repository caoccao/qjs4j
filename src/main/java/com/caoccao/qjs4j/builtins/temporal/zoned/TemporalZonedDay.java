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

package com.caoccao.qjs4j.builtins.temporal.zoned;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundingMode;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;

import java.math.BigInteger;
import java.time.DateTimeException;

/** The two instants bounding a local calendar day in a time zone. */
record TemporalZonedDay(BigInteger start, BigInteger end) {

    static TemporalZonedDay containing(JSContext context, JSTemporalZonedDateTime dateTime) {
        try {
            IsoDate date = dateTime.toLocalDateTime().date();
            String zone = dateTime.getTimeZoneId();
            BigInteger start = TemporalTimeZone.startOfDayToEpochNs(date, zone);
            BigInteger end = TemporalTimeZone.startOfDayToEpochNs(date.addDays(1), zone);
            if (TemporalUtils.isValidEpochNanoseconds(start) && TemporalUtils.isValidEpochNanoseconds(end)) {
                return new TemporalZonedDay(start, end);
            }
            TemporalError.NANOSECONDS_OUT_OF_RANGE.raise(context);
        } catch (DateTimeException e) {
            TemporalError.INVALID_ISO_DATE.raise(context);
        }
        return null;
    }

    BigInteger length() {
        return end.subtract(start);
    }

    BigInteger round(BigInteger epochNanoseconds, TemporalRoundingMode roundingMode) {
        BigInteger elapsed = epochNanoseconds.subtract(start);
        return start.add(roundingMode.roundBigIntegerToIncrementAsIfPositive(elapsed, length()));
    }
}
