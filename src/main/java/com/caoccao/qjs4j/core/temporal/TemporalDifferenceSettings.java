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

import java.util.Optional;

/**
 * Parsed and validated settings for Temporal since()/until() operations.
 * <p>
 * The {@link #parse} factory method consolidates the duplicated option-parsing
 * logic that was previously reimplemented in each Temporal prototype.
 */
public record TemporalDifferenceSettings(
        String largestUnit,
        String smallestUnit,
        long roundingIncrement,
        TemporalRoundingMode roundingMode) {

    private static long getMaximumSubDayIncrement(TemporalUnit unit) {
        return switch (unit) {
            case HOUR -> 24L;
            case MINUTE, SECOND -> 60L;
            case MILLISECOND, MICROSECOND, NANOSECOND -> 1_000L;
            default -> -1L;
        };
    }

    private static long getRoundingIncrementOption(JSContext context, JSObject optionsObject) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString("roundingIncrement"));
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return 1L;
        }
        JSNumber numericValue = JSTypeConversions.toNumber(context, optionValue);
        if (context.hasPendingException() || numericValue == null) {
            return Long.MIN_VALUE;
        }
        double roundingIncrement = numericValue.value();
        if (!Double.isFinite(roundingIncrement) || Double.isNaN(roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        long integerIncrement = (long) roundingIncrement;
        if (integerIncrement < 1L || integerIncrement > TemporalConstants.MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        return integerIncrement;
    }

    private static String getStringOption(JSContext context, JSObject optionsObject, String optionName, String defaultValue) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(optionName));
        if (context.hasPendingException()) {
            return null;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return defaultValue;
        }
        JSString optionText = JSTypeConversions.toString(context, optionValue);
        if (context.hasPendingException() || optionText == null) {
            return null;
        }
        return optionText.value();
    }

    private static String largerOfTwoUnits(String leftUnit, String rightUnit) {
        return TemporalUnit.fromString(leftUnit)
                .flatMap(left -> TemporalUnit.fromString(rightUnit)
                        .map(right -> left.ordinal() <= right.ordinal() ? left.jsName() : right.jsName()))
                .orElse(leftUnit);
    }

    /**
     * Parses and validates difference options from a JS options argument.
     * This replaces the per-prototype getDifferenceSettings / parseDifferenceOptions methods.
     *
     * @param context                 the JS context
     * @param sinceOperation          true for .since(), false for .until()
     * @param optionsArg              the raw JS options argument (may be undefined/null)
     * @param allowedMin              the largest allowed unit (e.g. YEAR for PlainDate, HOUR for PlainTime)
     * @param allowedMax              the smallest allowed unit (e.g. DAY for PlainDate, NANOSECOND for PlainTime)
     * @param defaultSmallestUnit     the default smallestUnit when not specified (e.g. DAY for PlainDate, NANOSECOND for PlainTime)
     * @param autoLargestUnit         what "auto" resolves to before being compared with smallestUnit
     * @param negateModeForSince      whether to negate roundingMode for since operations
     * @param validateSubDayIncrement whether to validate increment against sub-day maximums (hour:24, minute:60, etc.)
     * @return parsed settings, or null if a JS error was thrown
     */
    public static TemporalDifferenceSettings parse(
            JSContext context,
            boolean sinceOperation,
            JSValue optionsArg,
            TemporalUnit allowedMin,
            TemporalUnit allowedMax,
            TemporalUnit defaultSmallestUnit,
            TemporalUnit autoLargestUnit,
            boolean negateModeForSince,
            boolean validateSubDayIncrement) {

        // 1. Convert options to object
        JSObject optionsObject = null;
        if (!(optionsArg instanceof JSUndefined) && optionsArg != null) {
            if (optionsArg instanceof JSObject castedOptionsObject) {
                optionsObject = castedOptionsObject;
            } else {
                context.throwTypeError("Temporal error: Options must be an object.");
                return null;
            }
        }

        // 2. Extract raw option values
        String largestUnitText = null;
        long roundingIncrement = 1L;
        String roundingModeText = "trunc";
        String smallestUnitText = null;
        if (optionsObject != null) {
            largestUnitText = getStringOption(context, optionsObject, "largestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }

            roundingIncrement = getRoundingIncrementOption(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }

            roundingModeText = getStringOption(context, optionsObject, "roundingMode", "trunc");
            if (context.hasPendingException()) {
                return null;
            }

            smallestUnitText = getStringOption(context, optionsObject, "smallestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
        }

        // 3. Canonicalize and validate largestUnit
        String largestUnit;
        if (largestUnitText == null) {
            largestUnit = "auto";
        } else if ("auto".equals(largestUnitText)) {
            largestUnit = "auto";
        } else {
            Optional<TemporalUnit> parsed = TemporalUnit.fromString(largestUnitText)
                    .filter(u -> u.ordinal() >= allowedMin.ordinal() && u.ordinal() <= allowedMax.ordinal());
            if (parsed.isEmpty()) {
                context.throwRangeError("Temporal error: Invalid largest unit.");
                return null;
            }
            largestUnit = parsed.get().jsName();
        }

        // 4. Canonicalize and validate smallestUnit
        String smallestUnit;
        if (smallestUnitText == null) {
            smallestUnit = defaultSmallestUnit.jsName();
        } else {
            Optional<TemporalUnit> parsed = TemporalUnit.fromString(smallestUnitText)
                    .filter(u -> u.ordinal() >= allowedMin.ordinal() && u.ordinal() <= allowedMax.ordinal());
            if (parsed.isEmpty()) {
                context.throwRangeError("Temporal error: Invalid smallest unit.");
                return null;
            }
            smallestUnit = parsed.get().jsName();
        }

        // 5. Validate rounding mode
        TemporalRoundingMode roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        // 6. Negate rounding mode for since operations
        if (sinceOperation && negateModeForSince) {
            roundingMode = roundingMode.negate();
        }

        // 7. Resolve "auto" for largestUnit
        if ("auto".equals(largestUnit)) {
            largestUnit = largerOfTwoUnits(autoLargestUnit.jsName(), smallestUnit);
        }

        // 8. Validate largestUnit >= smallestUnit
        if (!largestUnit.equals(largerOfTwoUnits(largestUnit, smallestUnit))) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        // 9. Validate rounding increment
        if (validateSubDayIncrement) {
            Optional<TemporalUnit> smallestParsed = TemporalUnit.fromString(smallestUnit)
                    .filter(TemporalUnit::isTimeUnit);
            if (smallestParsed.isPresent()) {
                long maximumIncrement = getMaximumSubDayIncrement(smallestParsed.get());
                if (maximumIncrement > 0 && (roundingIncrement >= maximumIncrement || maximumIncrement % roundingIncrement != 0)) {
                    context.throwRangeError("Temporal error: Invalid rounding increment.");
                    return null;
                }
            }
        }

        return new TemporalDifferenceSettings(largestUnit, smallestUnit, roundingIncrement, roundingMode);
    }
}
