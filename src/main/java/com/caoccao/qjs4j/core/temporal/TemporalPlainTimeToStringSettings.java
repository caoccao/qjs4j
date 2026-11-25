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

public record TemporalPlainTimeToStringSettings(TemporalUnit smallestUnit, TemporalRoundingMode roundingMode,
        boolean autoFractionalSecondDigits, int fractionalSecondDigits, long roundingIncrementNanoseconds) {
    public static final TemporalPlainTimeToStringSettings DEFAULT = new TemporalPlainTimeToStringSettings(null,
            TemporalRoundingMode.TRUNC, true, -1, 1L);

    public static TemporalPlainTimeToStringSettings resolve(JSContext context, String roundingModeText,
            String smallestUnitText, TemporalFractionalSecondDigitsOption digits) {
        TemporalRoundingMode roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }
        TemporalUnit unit = TemporalUnit.fromString(smallestUnitText).orElse(null);
        if (smallestUnitText != null && (unit == null || !unit.isSmallerOrEqual(TemporalUnit.MINUTE))) {
            context.throwRangeError("Temporal error: Invalid smallestUnit option.");
            return null;
        }
        if (unit != null) {
            return new TemporalPlainTimeToStringSettings(unit, roundingMode, false,
                    unit.getStringFractionalSecondDigits(), unit.getStringRoundingIncrementNanoseconds());
        }
        return new TemporalPlainTimeToStringSettings(null, roundingMode, digits.auto(),
                digits.auto() ? -1 : digits.digits(), digits.roundingIncrementNanoseconds());
    }
}
