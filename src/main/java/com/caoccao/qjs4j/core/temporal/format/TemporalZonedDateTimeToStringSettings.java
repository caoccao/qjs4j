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
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundingMode;

public record TemporalZonedDateTimeToStringSettings(String calendarNameOption, String offsetOption,
        String timeZoneNameOption, TemporalUnit smallestUnit, TemporalRoundingMode roundingMode,
        boolean autoFractionalSecondDigits, int fractionalSecondDigits,
        long roundingIncrementNanoseconds) implements TemporalTimePrecision {
    public static final TemporalZonedDateTimeToStringSettings DEFAULT = new TemporalZonedDateTimeToStringSettings(
            "auto", "auto", "auto", null, TemporalRoundingMode.TRUNC, true, -1, 1L);

    private static String getToStringOffsetOption(JSContext context, JSObject optionsObject) {
        String offsetOption = TemporalUtils.getStringOption(context, optionsObject, "offset", "auto");
        if (context.hasPendingException() || offsetOption == null) {
            return null;
        }
        if (TemporalDisplayOffset.fromString(offsetOption) == null) {
            context.throwRangeError("Temporal error: Invalid offset option.");
            return null;
        }
        return offsetOption;
    }

    public static TemporalZonedDateTimeToStringSettings read(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalUtils.toOptionalOptionsObject(context, optionsValue,
                TemporalError.INVALID_OPTIONS.message());
        if (context.hasPendingException()) {
            return null;
        }
        if (optionsObject == null) {
            return TemporalZonedDateTimeToStringSettings.DEFAULT;
        }

        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, optionsObject);
        if (context.hasPendingException() || calendarNameOption == null) {
            return null;
        }

        TemporalFractionalSecondDigitsOption digits = TemporalFractionalSecondDigitsOption.read(context, optionsObject);
        if (digits == null) {
            return null;
        }

        String offsetOption = getToStringOffsetOption(context, optionsObject);
        if (context.hasPendingException() || offsetOption == null) {
            return null;
        }

        TemporalTimeStringOptions options = TemporalTimeStringOptions.read(context, optionsObject);
        if (options == null) {
            return null;
        }

        String timeZoneNameOption = getToStringTimeZoneNameOption(context, optionsObject);
        if (context.hasPendingException() || timeZoneNameOption == null) {
            return null;
        }

        TemporalPlainTimeToStringSettings time = options.resolve(context, digits, TemporalUnit.MINUTE,
                "Temporal error: Invalid smallestUnit option.");
        if (time == null) {
            return null;
        }
        return new TemporalZonedDateTimeToStringSettings(calendarNameOption, offsetOption, timeZoneNameOption,
                time.smallestUnit(), time.roundingMode(), time.autoFractionalSecondDigits(),
                time.fractionalSecondDigits(), time.roundingIncrementNanoseconds());
    }

    private static String getToStringTimeZoneNameOption(JSContext context, JSObject optionsObject) {
        String timeZoneNameOption = TemporalUtils.getStringOption(context, optionsObject, "timeZoneName", "auto");
        if (context.hasPendingException() || timeZoneNameOption == null) {
            return null;
        }
        if (TemporalDisplayTimeZone.fromString(timeZoneNameOption) == null) {
            context.throwRangeError("Temporal error: Invalid timeZoneName option.");
            return null;
        }
        return timeZoneNameOption;
    }
}
