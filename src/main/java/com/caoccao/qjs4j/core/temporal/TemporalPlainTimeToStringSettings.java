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
import com.caoccao.qjs4j.core.JSObject;

public record TemporalPlainTimeToStringSettings(TemporalUnit smallestUnit, TemporalRoundingMode roundingMode,
        boolean autoFractionalSecondDigits, int fractionalSecondDigits,
        long roundingIncrementNanoseconds) implements TemporalTimePrecision {
    public static final TemporalPlainTimeToStringSettings DEFAULT = new TemporalPlainTimeToStringSettings(null,
            TemporalRoundingMode.TRUNC, true, -1, 1L);

    public static TemporalPlainTimeToStringSettings resolve(JSContext context, String roundingModeText,
            String smallestUnitText, TemporalFractionalSecondDigitsOption digits) {
        return new TemporalTimeStringOptions(roundingModeText, smallestUnitText).resolve(context, digits,
                TemporalUnit.MINUTE, "Temporal error: Invalid smallestUnit option.");
    }

    public static TemporalPlainTimeToStringSettings read(JSContext context, JSObject optionsObject) {
        return read(context, optionsObject, TemporalUnit.MINUTE, "Temporal error: Invalid smallestUnit option.");
    }

    static TemporalPlainTimeToStringSettings read(JSContext context, JSObject optionsObject, TemporalUnit largest,
            String invalidUnit) {
        TemporalFractionalSecondDigitsOption digits = TemporalFractionalSecondDigitsOption.read(context, optionsObject);
        if (digits == null) {
            return null;
        }

        TemporalTimeStringOptions options = TemporalTimeStringOptions.read(context, optionsObject);
        return options == null ? null : options.resolve(context, digits, largest, invalidUnit);
    }
}
