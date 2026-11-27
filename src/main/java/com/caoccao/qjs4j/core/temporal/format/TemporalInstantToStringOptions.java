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
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;

public record TemporalInstantToStringOptions(TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption,
        TemporalRoundingMode roundingMode, TemporalUnit smallestUnit,
        String timeZoneId) implements TemporalTimePrecision {
    public static final TemporalInstantToStringOptions DEFAULT = new TemporalInstantToStringOptions(
            TemporalFractionalSecondDigitsOption.autoOption(), TemporalRoundingMode.TRUNC, null, null);

    public static TemporalInstantToStringOptions read(JSContext context, JSValue optionsArg) {
        JSObject optionsObject = TemporalUtils.toOptionalOptionsObject(context, optionsArg,
                "Temporal error: Options must be an object.");
        if (context.hasPendingException() || optionsObject == null) {
            return context.hasPendingException() ? null : DEFAULT;
        }

        TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption = TemporalFractionalSecondDigitsOption
                .read(context, optionsObject);
        if (fractionalSecondDigitsOption == null) {
            return null;
        }
        TemporalTimeStringOptions options = TemporalTimeStringOptions.read(context, optionsObject);
        if (options == null) {
            return null;
        }
        JSValue timeZoneValue = optionsObject.get(PropertyKey.TIME_ZONE);
        if (context.hasPendingException()) {
            return null;
        }

        String timeZoneId = null;
        if (!(timeZoneValue instanceof JSUndefined) && timeZoneValue != null) {
            if (!(timeZoneValue instanceof JSString timeZoneString)) {
                return context.rejectTypeError("Temporal error: Time zone must be string");
            }
            timeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneString.value());
            if (context.hasPendingException()) {
                return null;
            }
        }

        TemporalPlainTimeToStringSettings time = options.resolve(context, fractionalSecondDigitsOption,
                TemporalUnit.MINUTE, "Temporal error: Invalid smallest unit.");
        if (time == null) {
            return null;
        }
        if (timeZoneId != null) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (Exception invalidTimeZoneException) {
                return context.rejectRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            }
        }

        return new TemporalInstantToStringOptions(fractionalSecondDigitsOption, time.roundingMode(),
                time.smallestUnit(), timeZoneId);
    }

    @Override
    public boolean autoFractionalSecondDigits() {
        return smallestUnit == null && fractionalSecondDigitsOption.auto();
    }

    @Override
    public int fractionalSecondDigits() {
        return smallestUnit == null
                ? fractionalSecondDigitsOption.digits()
                : smallestUnit.getStringFractionalSecondDigits();
    }

    @Override
    public long roundingIncrementNanoseconds() {
        return smallestUnit == null
                ? fractionalSecondDigitsOption.roundingIncrementNanoseconds()
                : smallestUnit.getStringRoundingIncrementNanoseconds();
    }
}
