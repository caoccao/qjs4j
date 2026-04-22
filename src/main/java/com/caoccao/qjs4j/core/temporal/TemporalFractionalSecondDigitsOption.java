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

public record TemporalFractionalSecondDigitsOption(boolean auto, int digits) {
    public static final TemporalFractionalSecondDigitsOption AUTO = new TemporalFractionalSecondDigitsOption(true, -1);
    private static final TemporalFractionalSecondDigitsOption[] DIGIT_OPTIONS;
    private static final long[] DIGIT_ROUNDING_INCREMENT_NANOSECONDS = {
            TemporalConstants.SECOND_NANOSECONDS,
            100_000_000L,
            10_000_000L,
            TemporalConstants.MILLISECOND_NANOSECONDS,
            100_000L,
            10_000L,
            TemporalConstants.MICROSECOND_NANOSECONDS,
            100L,
            10L,
            1L,
    };

    static {
        DIGIT_OPTIONS = new TemporalFractionalSecondDigitsOption[10];
        for (int index = 0; index < DIGIT_OPTIONS.length; index++) {
            DIGIT_OPTIONS[index] = new TemporalFractionalSecondDigitsOption(false, index);
        }
    }

    public static TemporalFractionalSecondDigitsOption autoOption() {
        return AUTO;
    }

    public static TemporalFractionalSecondDigitsOption ofDigits(int digits) {
        if (digits < 0 || digits >= DIGIT_OPTIONS.length) {
            throw new IllegalArgumentException("Fractional second digits must be in range [0, 9].");
        }
        return DIGIT_OPTIONS[digits];
    }

    public static TemporalFractionalSecondDigitsOption parse(
            JSContext context,
            JSValue fractionalSecondDigitsValue,
            String invalidOptionMessage) {
        if (fractionalSecondDigitsValue instanceof JSUndefined || fractionalSecondDigitsValue == null) {
            return autoOption();
        }
        if (fractionalSecondDigitsValue instanceof JSNumber fractionalSecondDigitsNumberValue) {
            double numericValue = fractionalSecondDigitsNumberValue.value();
            if (!Double.isFinite(numericValue) || Double.isNaN(numericValue)) {
                context.throwRangeError(invalidOptionMessage);
                return null;
            }
            int flooredValue = (int) Math.floor(numericValue);
            if (flooredValue < 0 || flooredValue > 9) {
                context.throwRangeError(invalidOptionMessage);
                return null;
            }
            return ofDigits(flooredValue);
        }

        JSString stringValue = JSTypeConversions.toString(context, fractionalSecondDigitsValue);
        if (context.hasPendingException() || stringValue == null) {
            return null;
        }
        if ("auto".equals(stringValue.value())) {
            return autoOption();
        }

        context.throwRangeError(invalidOptionMessage);
        return null;
    }

    public long roundingIncrementNanoseconds() {
        if (auto) {
            return 1L;
        }
        return DIGIT_ROUNDING_INCREMENT_NANOSECONDS[digits];
    }
}
