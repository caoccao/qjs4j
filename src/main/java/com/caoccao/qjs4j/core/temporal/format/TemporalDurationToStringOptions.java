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

package com.caoccao.qjs4j.core.temporal.format;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundingMode;

public record TemporalDurationToStringOptions(TemporalUnit smallestUnit, TemporalRoundingMode roundingMode,
        long roundingIncrementNanoseconds, boolean precisionAuto, int fractionalSecondDigits) {
    public static final TemporalDurationToStringOptions DEFAULT = new TemporalDurationToStringOptions(
            TemporalUnit.NANOSECOND, TemporalRoundingMode.TRUNC, 1L, true, -1);

    public static TemporalDurationToStringOptions read(JSContext context, JSValue optionsValue) {
        return TemporalTimeStringOptions.readOptions(context, optionsValue,
                "Temporal error: Options must be an object.", DEFAULT, TemporalDurationToStringOptions::readFields);
    }

    private static TemporalDurationToStringOptions readFields(JSContext context, JSObject optionsObject) {
        TemporalPlainTimeToStringSettings time = TemporalPlainTimeToStringSettings.read(context, optionsObject,
                TemporalUnit.SECOND, "Temporal error: Invalid smallestUnit.");
        if (time == null) {
            return null;
        }
        TemporalUnit unit = time.smallestUnit();
        if (unit == null) {
            unit = time.autoFractionalSecondDigits()
                    ? TemporalUnit.NANOSECOND
                    : smallestUnitFromFractionalSecondDigits(time.fractionalSecondDigits());
        }
        return new TemporalDurationToStringOptions(unit, time.roundingMode(), time.roundingIncrementNanoseconds(),
                time.autoFractionalSecondDigits(), time.fractionalSecondDigits());
    }

    private static TemporalUnit smallestUnitFromFractionalSecondDigits(int fractionalSecondDigits) {
        if (fractionalSecondDigits == 0) {
            return TemporalUnit.SECOND;
        } else if (fractionalSecondDigits <= 3) {
            return TemporalUnit.MILLISECOND;
        } else if (fractionalSecondDigits <= 6) {
            return TemporalUnit.MICROSECOND;
        } else {
            return TemporalUnit.NANOSECOND;
        }
    }
}
