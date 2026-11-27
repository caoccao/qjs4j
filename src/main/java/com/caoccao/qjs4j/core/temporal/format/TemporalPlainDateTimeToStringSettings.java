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

public record TemporalPlainDateTimeToStringSettings(String calendarNameOption, TemporalUnit smallestUnit,
        TemporalRoundingMode roundingMode, boolean autoFractionalSecondDigits, int fractionalSecondDigits,
        long roundingIncrementNanoseconds) implements TemporalTimePrecision {
    public static final TemporalPlainDateTimeToStringSettings DEFAULT = new TemporalPlainDateTimeToStringSettings(
            "auto", null, TemporalRoundingMode.TRUNC, true, -1, 1L);

    public static TemporalPlainDateTimeToStringSettings read(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalUtils.toOptionalOptionsObject(context, optionsValue,
                "Temporal error: Option must be object: options.");
        if (context.hasPendingException()) {
            return null;
        }
        if (optionsObject == null) {
            return TemporalPlainDateTimeToStringSettings.DEFAULT;
        }

        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, optionsObject);
        if (context.hasPendingException() || calendarNameOption == null) {
            return null;
        }

        TemporalPlainTimeToStringSettings time = TemporalPlainTimeToStringSettings.read(context, optionsObject);
        if (time == null) {
            return null;
        }
        return new TemporalPlainDateTimeToStringSettings(calendarNameOption, time.smallestUnit(), time.roundingMode(),
                time.autoFractionalSecondDigits(), time.fractionalSecondDigits(), time.roundingIncrementNanoseconds());
    }
}
