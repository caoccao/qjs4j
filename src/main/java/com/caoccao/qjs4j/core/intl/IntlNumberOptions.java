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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.core.*;
import java.util.Set;

/** Digit ranges and rounding options shared by Intl.NumberFormat and Intl.PluralRules. */
final class IntlNumberOptions {
    private static final String[] NOTATION_VALUES = {"standard", "scientific", "engineering", "compact"};

    private static final String[] ROUNDING_MODE_VALUES = {"ceil", "floor", "expand", "trunc", "halfCeil", "halfFloor",
            "halfExpand", "halfTrunc", "halfEven"};

    private static final String[] ROUNDING_PRIORITY_VALUES = {"auto", "morePrecision", "lessPrecision"};

    private static final String[] TRAILING_ZERO_DISPLAY_VALUES = {"auto", "stripIfInteger"};

    private static final Set<Integer> VALID_ROUNDING_INCREMENTS = Set.of(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500,
            1000, 2000, 2500, 5000);

    private IntlNumberOptions() {
    }

    static void defineDigitRange(JSObject options, boolean significant, int minimum, int maximum) {
        IntlBuiltins.createDataProperty(options, significant ? "minimumSignificantDigits" : "minimumFractionDigits",
                JSNumber.of(minimum));
        IntlBuiltins.createDataProperty(options, significant ? "maximumSignificantDigits" : "maximumFractionDigits",
                JSNumber.of(maximum));
    }

    private static int readMinimumIntegerDigits(JSContext context, JSObject options) {
        Integer digits = IntlFormatOptions.getOptionIntegerOrUndefined(context, options, "minimumIntegerDigits", 1, 21);
        return digits == null ? 1 : digits;
    }

    static RoundingOptions readRoundingOptions(JSContext context, JSObject optionsObject, boolean validateIncrement) {
        Integer increment = IntlFormatOptions.getOptionIntegerOrUndefined(context, optionsObject, "roundingIncrement",
                1, 5000);
        if (context.hasPendingException()) {
            return null;
        }
        int roundingIncrement = increment == null ? 1 : increment;
        if (validateIncrement && !VALID_ROUNDING_INCREMENTS.contains(roundingIncrement)) {
            context.throwRangeError("Invalid roundingIncrement value: " + roundingIncrement);
            return null;
        }
        String mode = IntlFormatOptions.normalizedStringOption(context, optionsObject, "roundingMode", "halfExpand",
                ROUNDING_MODE_VALUES);
        String priority = IntlFormatOptions.normalizedStringOption(context, optionsObject, "roundingPriority", "auto",
                ROUNDING_PRIORITY_VALUES);
        String trailingZero = IntlFormatOptions.normalizedStringOption(context, optionsObject, "trailingZeroDisplay",
                "auto", TRAILING_ZERO_DISPLAY_VALUES);
        return context.hasPendingException()
                ? null
                : new RoundingOptions(roundingIncrement, mode, priority, trailingZero);
    }

    record DigitOptions(String notation, int minimumIntegerDigits, DigitRange fractionDigits) {
        static DigitOptions read(JSContext context, JSObject optionsObject) {
            String notation = IntlFormatOptions.normalizedStringOption(context, optionsObject, "notation", "standard",
                    NOTATION_VALUES);
            if (context.hasPendingException()) {
                return null;
            }
            int minimum = readMinimumIntegerDigits(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }
            DigitRange fraction = DigitRange.readFraction(context, optionsObject);
            return fraction == null ? null : new DigitOptions(notation, minimum, fraction);
        }
    }

    record DigitRange(Integer minimum, Integer maximum) {
        static DigitRange readFraction(JSContext context, JSObject options) {
            return read(context, options, "minimumFractionDigits", "maximumFractionDigits", 0, 100);
        }

        static DigitRange readSignificant(JSContext context, JSObject options) {
            return read(context, options, "minimumSignificantDigits", "maximumSignificantDigits", 1, 21);
        }

        static DigitRange read(JSContext context, JSObject options, String minKey, String maxKey, int low, int high) {
            Integer min = IntlFormatOptions.getOptionIntegerOrUndefined(context, options, minKey, low, high);
            if (context.hasPendingException()) {
                return null;
            }
            Integer max = IntlFormatOptions.getOptionIntegerOrUndefined(context, options, maxKey, low, high);
            return context.hasPendingException() ? null : new DigitRange(min, max);
        }

        boolean specified() {
            return minimum != null || maximum != null;
        }

        DigitRange resolveFraction(int defaultMin, int defaultMax) {
            int min = minimum != null ? minimum : maximum == null ? defaultMin : Math.min(defaultMin, maximum);
            int max = maximum != null ? maximum : Math.max(min, defaultMax);
            if (min > max) {
                throw new IllegalArgumentException("minimumFractionDigits value is out of range");
            }
            return new DigitRange(min, max);
        }

        DigitRange resolveSignificant() {
            int min = minimum == null ? 1 : minimum;
            int max = maximum == null ? 21 : maximum;
            if (min > max) {
                throw new IllegalArgumentException("minimumSignificantDigits value is out of range");
            }
            return new DigitRange(min, max);
        }
    }

    record RoundingOptions(int roundingIncrement, String roundingMode, String roundingPriority,
            String trailingZeroDisplay) {
    }
}
