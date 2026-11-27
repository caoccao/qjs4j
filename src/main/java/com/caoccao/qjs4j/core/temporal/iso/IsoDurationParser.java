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

package com.caoccao.qjs4j.core.temporal.iso;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;

import static com.caoccao.qjs4j.core.temporal.TemporalConstants.*;
import static com.caoccao.qjs4j.core.temporal.iso.IsoParsingState.*;

/** Reads the ordered date and time components of an ISO duration. */
public final class IsoDurationParser {
    private static final String INVALID = "Temporal error: Invalid duration string.";

    private IsoDurationParser() {
    }

    private static TemporalDuration invalid(JSContext context) {
        return context.rejectRangeError(INVALID);
    }

    public static TemporalDuration parse(JSContext context, String input) {
        int position = 0;
        char first = input.charAt(position++);
        boolean negative = first == '-' || first == '\u2212';
        if (negative || first == '+') {
            if (position == input.length()) {
                return invalid(context);
            }
            first = input.charAt(position++);
        }
        if (first != 'P' && first != 'p') {
            return invalid(context);
        }
        long years = 0, months = 0, weeks = 0, days = 0, hours = 0, minutes = 0, seconds = 0;
        int fraction = 0;
        int last = -1;
        boolean time = false;
        boolean fractionalUnit = false;
        while (position < input.length()) {
            char next = input.charAt(position++);
            if (next == 'T' || next == 't') {
                if (time) {
                    return invalid(context);
                }
                time = true;
                continue;
            }
            if (!isAsciiDigit(next)) {
                return invalid(context);
            }
            long number = next - '0';
            while (position < input.length() && isAsciiDigit(next = input.charAt(position))) {
                int digit = next - '0';
                if (number >= Long.MAX_VALUE / 10 && (number > Long.MAX_VALUE / 10 || digit > Long.MAX_VALUE % 10)) {
                    return TemporalError.INVALID_DURATION.reject(context);
                }
                number = number * 10 + digit;
                position++;
            }
            boolean hasFraction = next == '.' || next == ',';

            if (hasFraction) {
                int start = ++position;
                position = fractionEnd(input, start);
                fraction = fractionNanoseconds(context, input, start, position, INVALID, INVALID);
                if (fraction < 0) {
                    return null;
                }
            }
            if (position == input.length()) {
                return invalid(context);
            }
            char unit = input.charAt(position++);
            int order;
            switch (unit) {
                case 'Y', 'y' -> {
                    order = 0;
                    years = number;
                }
                case 'M', 'm' -> {
                    if (time) {
                        order = 5;
                        minutes = number;
                    } else {
                        order = 1;
                        months = number;
                    }
                }
                case 'W', 'w' -> {
                    order = 2;
                    weeks = number;
                }
                case 'D', 'd' -> {
                    order = 3;
                    days = number;
                }
                case 'H', 'h' -> {
                    order = 4;
                    hours = number;
                }
                case 'S', 's' -> {
                    order = 6;
                    seconds = number;
                }
                default -> {
                    return invalid(context);
                }
            }
            if (order <= last || (order >= 4) != time || fractionalUnit || hasFraction && !time) {
                return invalid(context);
            }
            last = order;
            fractionalUnit = hasFraction;
        }
        if (last < 0 || time && last < 4) {
            return invalid(context);
        }
        long subsecond = fraction;
        if (fraction != 0 && last != 6) {
            // A decimal is allowed only on the final time unit.
            long nanos = fraction * (last == 4 ? 3600L : 60L);
            if (last == 4) {
                minutes = nanos / MINUTE_NANOSECONDS;
            }
            seconds = nanos / SECOND_NANOSECONDS % 60;
            subsecond = nanos % SECOND_NANOSECONDS;
        }
        int sign = negative ? -1 : 1;
        return new TemporalDuration(sign * years, sign * months, sign * weeks, sign * days, sign * hours,
                sign * minutes, sign * seconds, sign * (subsecond / MILLISECOND_NANOSECONDS),
                sign * (subsecond / MICROSECOND_NANOSECONDS % 1000), sign * (subsecond % 1000));
    }
}
