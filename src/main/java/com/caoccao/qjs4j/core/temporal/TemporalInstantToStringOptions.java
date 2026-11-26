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

import com.caoccao.qjs4j.core.*;

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
        String roundingModeText = TemporalUtils.getStringOption(context, optionsObject, "roundingMode", "trunc");
        if (context.hasPendingException()) {
            return null;
        }
        String smallestUnitText = TemporalUtils.getStringOption(context, optionsObject, "smallestUnit", null);
        if (context.hasPendingException()) {
            return null;
        }
        JSValue timeZoneValue = optionsObject.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return null;
        }

        String timeZoneId = null;
        if (!(timeZoneValue instanceof JSUndefined) && timeZoneValue != null) {
            if (!(timeZoneValue instanceof JSString timeZoneString)) {
                context.throwTypeError("Temporal error: Time zone must be string");
                return null;
            }
            timeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneString.value());
            if (context.hasPendingException()) {
                return null;
            }
        }

        TemporalRoundingMode roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        TemporalUnit smallestUnit = null;
        if (smallestUnitText != null) {
            smallestUnit = TemporalUnit.fromString(smallestUnitText)
                    .filter(u -> u.isTimeUnit() && u != TemporalUnit.HOUR).orElse(null);
            if (smallestUnit == null) {
                context.throwRangeError("Temporal error: Invalid smallest unit.");
                return null;
            }
        }

        if (timeZoneId != null) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (Exception invalidTimeZoneException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return null;
            }
        }

        return new TemporalInstantToStringOptions(fractionalSecondDigitsOption, roundingMode, smallestUnit, timeZoneId);
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
