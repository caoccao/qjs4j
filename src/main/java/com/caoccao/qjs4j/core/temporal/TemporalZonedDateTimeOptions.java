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
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;

public record TemporalZonedDateTimeOptions(String disambiguation, String offset, String overflow) {
    public static final TemporalZonedDateTimeOptions DEFAULT_FROM = new TemporalZonedDateTimeOptions("compatible", "reject", "constrain");
    public static final TemporalZonedDateTimeOptions DEFAULT_WITH = new TemporalZonedDateTimeOptions("compatible", "prefer", "constrain");

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
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }

        String disambiguation = TemporalUtils.getStringOption(context, optionsObject, "disambiguation", "compatible");
        if (context.hasPendingException() || disambiguation == null) {
            return null;
        }
        if (TemporalDisambiguation.fromString(disambiguation) == null) {
            context.throwRangeError("Temporal error: Invalid disambiguation option.");
            return null;
        }

        String offset = TemporalUtils.getStringOption(context, optionsObject, "offset", defaultOffset);
        if (context.hasPendingException() || offset == null) {
            return null;
        }
        if (TemporalOffsetOption.fromString(offset) == null) {
            context.throwRangeError("Temporal error: Invalid offset option.");
            return null;
        }

        String overflow = TemporalUtils.getStringOption(context, optionsObject, "overflow", "constrain");
        if (context.hasPendingException() || overflow == null) {
            return null;
        }
        if (TemporalOverflow.fromString(overflow) == null) {
            context.throwRangeError("Temporal error: Invalid overflow option.");
            return null;
        }

        return createCanonical(disambiguation, offset, overflow);
    }
}
