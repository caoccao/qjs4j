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

package com.caoccao.qjs4j.core.temporal;

import com.caoccao.qjs4j.core.JSContext;
import java.math.BigInteger;

/** Resolved precision for the ISO time portion of a Temporal string. */
interface TemporalTimePrecision {
    boolean autoFractionalSecondDigits();
    default String format(IsoDateTime dateTime) {
        return dateTime.date() + "T" + format(dateTime.time());
    }
    default String format(IsoTime time) {
        return time.formatTimeString(smallestUnit(), autoFractionalSecondDigits(), fractionalSecondDigits());
    }
    int fractionalSecondDigits();
    default BigInteger roundedEpoch(JSContext context, BigInteger nanos) {
        long increment = roundingIncrementNanoseconds();
        BigInteger rounded = increment == 1L
                ? nanos
                : roundingMode().roundBigIntegerToIncrementAsIfPositive(nanos, BigInteger.valueOf(increment));
        if (!TemporalUtils.isValidEpochNanoseconds(rounded)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }
        return rounded;
    }

    default long roundedTime(IsoTime time) {
        long nanos = time.totalNanoseconds();
        long increment = roundingIncrementNanoseconds();
        return increment == 1L ? nanos : roundingMode().roundLongToIncrementAsIfPositive(nanos, increment);
    }

    long roundingIncrementNanoseconds();

    TemporalRoundingMode roundingMode();

    TemporalUnit smallestUnit();
}
