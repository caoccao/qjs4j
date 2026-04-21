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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.*;

final class TemporalOptionResolver {
    private TemporalOptionResolver() {
    }

    static long getRoundingIncrementOption(
            JSContext context,
            JSObject optionsObject,
            String optionName,
            long defaultValue,
            long minimumValue,
            long maximumValue,
            String invalidOptionMessage) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(optionName));
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return defaultValue;
        }

        JSNumber optionNumber = JSTypeConversions.toNumber(context, optionValue);
        if (context.hasPendingException() || optionNumber == null) {
            return Long.MIN_VALUE;
        }

        double numericOptionValue = optionNumber.value();
        if (!Double.isFinite(numericOptionValue) || Double.isNaN(numericOptionValue)) {
            context.throwRangeError(invalidOptionMessage);
            return Long.MIN_VALUE;
        }

        long integerOptionValue = (long) numericOptionValue;
        if (integerOptionValue < minimumValue || integerOptionValue > maximumValue) {
            context.throwRangeError(invalidOptionMessage);
            return Long.MIN_VALUE;
        }
        return integerOptionValue;
    }

    static String getStringOption(JSContext context, JSObject optionsObject, String optionName, String defaultValue) {
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

    static JSObject toOptionalOptionsObject(JSContext context, JSValue optionsValue, String invalidTypeMessage) {
        if (optionsValue instanceof JSUndefined || optionsValue == null) {
            return null;
        }
        if (optionsValue instanceof JSObject optionsObject) {
            return optionsObject;
        }
        context.throwTypeError(invalidTypeMessage);
        return null;
    }

}
