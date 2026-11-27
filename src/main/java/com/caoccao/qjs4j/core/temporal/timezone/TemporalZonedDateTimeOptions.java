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

package com.caoccao.qjs4j.core.temporal.timezone;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalOverflow;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

public record TemporalZonedDateTimeOptions(String disambiguation, String offset, String overflow) {
    public static final TemporalZonedDateTimeOptions DEFAULT_FROM = new TemporalZonedDateTimeOptions("compatible",
            "reject", "constrain");
    public static final TemporalZonedDateTimeOptions DEFAULT_WITH = new TemporalZonedDateTimeOptions("compatible",
            "prefer", "constrain");

    private static TemporalZonedDateTimeOptions createCanonical(String disambiguation, String offset, String overflow) {
        if ("compatible".equals(disambiguation) && "constrain".equals(overflow)) {
            if ("reject".equals(offset)) {
                return DEFAULT_FROM;
            } else if ("prefer".equals(offset)) {
                return DEFAULT_WITH;
            }
        }
        return new TemporalZonedDateTimeOptions(disambiguation, offset, overflow);
    }

    public static TemporalZonedDateTimeOptions parse(JSContext context, JSValue optionsValue, String defaultOffset) {
        if (optionsValue instanceof JSUndefined || optionsValue == null) {
            return createCanonical("compatible", defaultOffset, "constrain");
        }
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return TemporalError.INVALID_OPTIONS.reject(context);
        }

        String disambiguation = disambiguation(context, optionsObject);
        if (disambiguation == null) {
            return null;
        }
        String offset = TemporalUtils.getEnumOption(context, optionsObject, "offset", defaultOffset,
                TemporalOffsetOption::fromString);
        if (offset == null) {
            return null;
        }
        String overflow = TemporalUtils.getEnumOption(context, optionsObject, "overflow", "constrain",
                TemporalOverflow::fromString);
        if (overflow == null) {
            return null;
        }
        return createCanonical(disambiguation, offset, overflow);
    }

    public static String disambiguation(JSContext context, JSValue options) {
        return TemporalUtils.getEnumOption(context, options, "disambiguation", "compatible",
                TemporalDisambiguation::fromString);
    }
}
