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

package com.caoccao.qjs4j.core.temporal.rounding;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;

/**
 * Parsed and validated settings for Temporal round() operations.
 * <p>
 * The {@link #parse} factory method consolidates the duplicated round-option parsing logic from PlainTime,
 * PlainDateTime, and ZonedDateTime prototypes.
 */
public record TemporalRoundSettings(TemporalUnit smallestUnit, long roundingIncrement,
        TemporalRoundingMode roundingMode) {

    private static String getRequiredStringOption(JSContext context, JSObject optionsObject, String optionName,
            String missingMessage) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(optionName));
        if (context.hasPendingException()) {
            return null;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            context.throwRangeError(missingMessage);
            return null;
        }
        JSString optionText = JSTypeConversions.toString(context, optionValue);
        if (context.hasPendingException() || optionText == null) {
            return null;
        }
        return optionText.value();
    }

    /**
     * Parses round options from a JS value (either a unit string or an options object).
     *
     * @param context
     *            the JS context
     * @param roundTo
     *            the raw JS argument (string or options object)
     * @param allowedMin
     *            the largest allowed unit (e.g. HOUR for PlainTime, DAY for others)
     * @param allowedMax
     *            the smallest allowed unit (typically NANOSECOND)
     * @return parsed settings, or null if a JS error was thrown
     */
    public static TemporalRoundSettings parse(JSContext context, JSValue roundTo, TemporalUnit allowedMin,
            TemporalUnit allowedMax) {
        long roundingIncrement = 1L;
        TemporalRoundingMode roundingMode = TemporalRoundingMode.HALF_EXPAND;
        String smallestUnitText;

        if (roundTo instanceof JSString unitString) {
            smallestUnitText = unitString.value();
        } else if (roundTo instanceof JSObject optionsObject) {
            roundingIncrement = TemporalUtils.getRoundingIncrementOption(context, optionsObject,
                    "Temporal error: Invalid roundingIncrement option.");
            if (context.hasPendingException()) {
                return null;
            }
            roundingMode = TemporalRoundingMode.readRoundOption(context, optionsObject, true);
            if (roundingMode == null) {
                return null;
            }
            smallestUnitText = getRequiredStringOption(context, optionsObject, "smallestUnit",
                    "Temporal error: smallestUnit is required.");
            if (context.hasPendingException() || smallestUnitText == null) {
                return null;
            }
        } else {
            return TemporalError.INVALID_ROUND_TO.reject(context);
        }

        TemporalUnit smallestUnit = TemporalUnit.fromString(smallestUnitText).orElse(null);
        if (smallestUnit == null || smallestUnit.rank() < allowedMin.rank()
                || smallestUnit.rank() > allowedMax.rank()) {
            context.throwRangeError("Temporal error: Invalid unit for rounding: " + smallestUnitText);
            return null;
        }

        // Validate rounding increment: DAY requires increment=1, time units have per-unit maximum
        if (smallestUnit == TemporalUnit.DAY) {
            if (roundingIncrement != 1L) {
                context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
                return null;
            }
        } else if (!smallestUnit.isValidIncrement(roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
            return null;
        }

        return new TemporalRoundSettings(smallestUnit, roundingIncrement, roundingMode);
    }

    public long roundedNanoseconds(IsoTime time) {
        return roundingMode.roundLongToIncrementAsIfPositive(time.totalNanoseconds(),
                smallestUnit.getNanosecondFactor() * roundingIncrement);
    }

    public IsoDateTime roundDateTime(IsoDateTime value) {
        long nanos = roundedNanoseconds(value.time());
        IsoDate date = nanos == TemporalConstants.DAY_NANOSECONDS ? value.date().addDays(1) : value.date();
        return date.atTime(IsoTime.createFromNanoseconds(nanos));
    }

    public static TemporalRoundSettings parseRoundTo(JSContext context, JSValue[] args, TemporalUnit largestUnit) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            return TemporalError.MISSING_ROUND_TO.reject(context);
        }
        return parse(context, args[0], largestUnit, TemporalUnit.NANOSECOND);
    }
}
