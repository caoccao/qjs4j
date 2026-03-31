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
import com.caoccao.qjs4j.core.temporal.IsoTime;
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * Implementation of Temporal.PlainTime prototype methods.
 */
public final class TemporalPlainTimePrototype {
    private static final String TYPE_NAME = "Temporal.PlainTime";

    private TemporalPlainTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "add");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainTime, args, 1);
    }

    // ========== Getters ==========

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainTime plainTime, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }
        long hours = 0, minutes = 0, seconds = 0, milliseconds = 0, microseconds = 0, nanoseconds = 0;

        JSValue durationArg = args[0];
        if (durationArg instanceof JSString durationStr) {
            TemporalParser.DurationFields df = TemporalParser.parseDurationString(context, durationStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hours = df.hours();
            minutes = df.minutes();
            seconds = df.seconds();
            milliseconds = df.milliseconds();
            microseconds = df.microseconds();
            nanoseconds = df.nanoseconds();
        } else if (durationArg instanceof JSObject durationObj) {
            hours = TemporalUtils.getIntegerField(context, durationObj, "hours", 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            minutes = TemporalUtils.getIntegerField(context, durationObj, "minutes", 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            seconds = TemporalUtils.getIntegerField(context, durationObj, "seconds", 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            milliseconds = TemporalUtils.getIntegerField(context, durationObj, "milliseconds", 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            microseconds = TemporalUtils.getIntegerField(context, durationObj, "microseconds", 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            nanoseconds = TemporalUtils.getIntegerField(context, durationObj, "nanoseconds", 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        } else {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        long totalAddNs = sign * (hours * 3_600_000_000_000L
                + minutes * 60_000_000_000L
                + seconds * 1_000_000_000L
                + milliseconds * 1_000_000L
                + microseconds * 1_000L
                + nanoseconds);

        IsoTime.AddResult result = plainTime.getIsoTime().addNanoseconds(totalAddNs);
        return TemporalPlainTimeConstructor.createPlainTime(context, result.time());
    }

    private static JSTemporalPlainTime checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainTime plainTime)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainTime;
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "equals");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainTime other = TemporalPlainTimeConstructor.toTemporalTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = IsoTime.compareIsoTime(plainTime.getIsoTime(), other.getIsoTime()) == 0;
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static String formatTimeDuration(long totalNs) {
        boolean negative = totalNs < 0;
        if (negative) {
            totalNs = -totalNs;
        }
        long hours = totalNs / 3_600_000_000_000L;
        totalNs %= 3_600_000_000_000L;
        long minutes = totalNs / 60_000_000_000L;
        totalNs %= 60_000_000_000L;
        long seconds = totalNs / 1_000_000_000L;
        totalNs %= 1_000_000_000L;
        long milliseconds = totalNs / 1_000_000L;
        totalNs %= 1_000_000L;
        long microseconds = totalNs / 1_000L;
        long nanoseconds = totalNs % 1_000L;

        return TemporalUtils.formatDurationString(0, 0, 0, 0,
                negative ? -hours : hours,
                negative ? -minutes : minutes,
                negative ? -seconds : seconds,
                negative ? -milliseconds : milliseconds,
                negative ? -microseconds : microseconds,
                negative ? -nanoseconds : nanoseconds);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "hour");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().hour());
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "microsecond");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().microsecond());
    }

    // ========== Methods ==========

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "millisecond");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "minute");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().minute());
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "nanosecond");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().nanosecond());
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "round");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        String smallestUnit;
        int increment = 1;
        String roundingMode = "halfExpand";

        if (args[0] instanceof JSString unitStr) {
            smallestUnit = unitStr.value();
        } else if (args[0] instanceof JSObject options) {
            smallestUnit = TemporalUtils.getStringOption(context, options, "smallestUnit", null);
            if (smallestUnit == null) {
                context.throwRangeError("Temporal error: Must specify a roundTo parameter.");
                return JSUndefined.INSTANCE;
            }
            String incStr = TemporalUtils.getStringOption(context, options, "roundingIncrement", "1");
            try {
                increment = Integer.parseInt(incStr);
            } catch (NumberFormatException e) {
                // Keep default
            }
            roundingMode = TemporalUtils.getStringOption(context, options, "roundingMode", "halfExpand");
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return JSUndefined.INSTANCE;
        }

        long totalNs = plainTime.getIsoTime().totalNanoseconds();
        long unitNs = unitToNanoseconds(smallestUnit);
        if (unitNs == 0) {
            context.throwRangeError("Temporal error: Invalid unit for rounding.");
            return JSUndefined.INSTANCE;
        }

        long incrementNs = unitNs * increment;
        long roundedNs = roundToIncrement(totalNs, incrementNs, roundingMode);
        // Wrap to 24 hours
        roundedNs = Math.floorMod(roundedNs, 86_400_000_000_000L);

        return TemporalPlainTimeConstructor.createPlainTime(context, IsoTime.fromNanoseconds(roundedNs));
    }

    private static long roundToIncrement(long value, long increment, String mode) {
        if (increment == 0) {
            return value;
        }
        return switch (mode) {
            case "ceil" -> {
                long div = Math.floorDiv(value, increment);
                yield (value % increment == 0) ? value : (div + 1) * increment;
            }
            case "floor", "trunc" -> Math.floorDiv(value, increment) * increment;
            case "halfExpand" -> {
                long remainder = Math.floorMod(value, increment);
                if (remainder * 2 >= increment) {
                    yield Math.floorDiv(value, increment) * increment + increment;
                }
                yield Math.floorDiv(value, increment) * increment;
            }
            default -> Math.floorDiv(value, increment) * increment;
        };
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "second");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "since");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainTime other = TemporalPlainTimeConstructor.toTemporalTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long diffNs = plainTime.getIsoTime().totalNanoseconds() - other.getIsoTime().totalNanoseconds();
        return new JSString(formatTimeDuration(diffNs));
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "subtract");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainTime, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "toJSON");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainTime.getIsoTime().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "toLocaleString");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainTime});
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "toString");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainTime.getIsoTime().toString());
    }

    private static long unitToNanoseconds(String unit) {
        return switch (unit) {
            case "hour" -> 3_600_000_000_000L;
            case "minute" -> 60_000_000_000L;
            case "second" -> 1_000_000_000L;
            case "millisecond" -> 1_000_000L;
            case "microsecond" -> 1_000L;
            case "nanosecond" -> 1L;
            default -> 0;
        };
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "until");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainTime other = TemporalPlainTimeConstructor.toTemporalTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long diffNs = other.getIsoTime().totalNanoseconds() - plainTime.getIsoTime().totalNanoseconds();
        return new JSString(formatTimeDuration(diffNs));
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainTime.prototype.valueOf; use Temporal.PlainTime.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "with");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Must specify at least one time field.");
            return JSUndefined.INSTANCE;
        }

        IsoTime original = plainTime.getIsoTime();
        int hour = TemporalUtils.getIntegerField(context, fields, "hour", original.hour());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int minute = TemporalUtils.getIntegerField(context, fields, "minute", original.minute());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int second = TemporalUtils.getIntegerField(context, fields, "second", original.second());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int millisecond = TemporalUtils.getIntegerField(context, fields, "millisecond", original.millisecond());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int microsecond = TemporalUtils.getIntegerField(context, fields, "microsecond", original.microsecond());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", original.nanosecond());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if ("reject".equals(overflow)) {
            if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            return TemporalPlainTimeConstructor.createPlainTime(context, new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond));
        } else {
            IsoTime constrained = TemporalUtils.constrainIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            return TemporalPlainTimeConstructor.createPlainTime(context, constrained);
        }
    }
}
