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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundingMode;

/** Read both properties before validating them. Zoned and Instant formatting read a zone option in between. */
record TemporalTimeStringOptions(String roundingMode, String smallestUnit) {
    static TemporalTimeStringOptions read(JSContext context, JSObject options) {
        String mode = TemporalUtils.getStringOption(context, options, "roundingMode", "trunc");
        if (context.hasPendingException()) {
            return null;
        }
        String unit = TemporalUtils.getStringOption(context, options, "smallestUnit", null);
        return context.hasPendingException() ? null : new TemporalTimeStringOptions(mode, unit);
    }

    TemporalPlainTimeToStringSettings resolve(JSContext context, TemporalFractionalSecondDigitsOption digits,
            TemporalUnit largest, String invalidUnit) {
        TemporalRoundingMode mode = TemporalRoundingMode.fromString(roundingMode);
        if (mode == null) {
            return TemporalError.INVALID_ROUNDING_MODE.reject(context);
        }
        TemporalUnit unit = TemporalUnit.fromString(smallestUnit).orElse(null);
        if (smallestUnit != null && (unit == null || !unit.isSmallerOrEqual(largest))) {
            return context.rejectRangeError(invalidUnit);
        }
        if (unit != null) {
            return new TemporalPlainTimeToStringSettings(unit, mode, false, unit.getStringFractionalSecondDigits(),
                    unit.getStringRoundingIncrementNanoseconds());
        }
        return new TemporalPlainTimeToStringSettings(null, mode, digits.auto(), digits.auto() ? -1 : digits.digits(),
                digits.roundingIncrementNanoseconds());
    }
}
