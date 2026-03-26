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
import com.caoccao.qjs4j.core.temporal.TemporalDurationRecord;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * Implementation of Temporal.Duration prototype methods.
 */
public final class TemporalDurationPrototype {
    private static final String TYPE_NAME = "Temporal.Duration";

    private TemporalDurationPrototype() {
    }

    // ========== Getters ==========

    public static JSValue abs(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "abs");
        if (d == null) return JSUndefined.INSTANCE;
        return TemporalDurationConstructor.createDuration(context, d.getRecord().abs());
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "add");
        if (d == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, d, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalDuration d, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration other = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord r1 = d.getRecord();
        TemporalDurationRecord r2 = other.getRecord();

        long totalNs = r1.totalNanoseconds() + sign * r2.totalNanoseconds();
        TemporalDurationRecord balanced = balanceTimeDuration(totalNs, "hour");

        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(
                        r1.years() + sign * r2.years(),
                        r1.months() + sign * r2.months(),
                        r1.weeks() + sign * r2.weeks(),
                        balanced.days(), balanced.hours(), balanced.minutes(), balanced.seconds(),
                        balanced.milliseconds(), balanced.microseconds(), balanced.nanoseconds()));
    }

    static TemporalDurationRecord balanceTimeDuration(long totalNs, String largestUnit) {
        boolean negative = totalNs < 0;
        if (negative) {
            totalNs = -totalNs;
        }

        long days = 0, hours = 0, minutes = 0, seconds = 0;
        long milliseconds = 0, microseconds = 0, nanoseconds;

        switch (largestUnit) {
            case "day" -> {
                days = totalNs / 86_400_000_000_000L;
                totalNs %= 86_400_000_000_000L;
                hours = totalNs / 3_600_000_000_000L;
                totalNs %= 3_600_000_000_000L;
                minutes = totalNs / 60_000_000_000L;
                totalNs %= 60_000_000_000L;
                seconds = totalNs / 1_000_000_000L;
                totalNs %= 1_000_000_000L;
                milliseconds = totalNs / 1_000_000L;
                totalNs %= 1_000_000L;
                microseconds = totalNs / 1_000L;
                nanoseconds = totalNs % 1_000L;
            }
            case "hour" -> {
                hours = totalNs / 3_600_000_000_000L;
                totalNs %= 3_600_000_000_000L;
                minutes = totalNs / 60_000_000_000L;
                totalNs %= 60_000_000_000L;
                seconds = totalNs / 1_000_000_000L;
                totalNs %= 1_000_000_000L;
                milliseconds = totalNs / 1_000_000L;
                totalNs %= 1_000_000L;
                microseconds = totalNs / 1_000L;
                nanoseconds = totalNs % 1_000L;
            }
            case "minute" -> {
                minutes = totalNs / 60_000_000_000L;
                totalNs %= 60_000_000_000L;
                seconds = totalNs / 1_000_000_000L;
                totalNs %= 1_000_000_000L;
                milliseconds = totalNs / 1_000_000L;
                totalNs %= 1_000_000L;
                microseconds = totalNs / 1_000L;
                nanoseconds = totalNs % 1_000L;
            }
            case "second" -> {
                seconds = totalNs / 1_000_000_000L;
                totalNs %= 1_000_000_000L;
                milliseconds = totalNs / 1_000_000L;
                totalNs %= 1_000_000L;
                microseconds = totalNs / 1_000L;
                nanoseconds = totalNs % 1_000L;
            }
            case "millisecond" -> {
                milliseconds = totalNs / 1_000_000L;
                totalNs %= 1_000_000L;
                microseconds = totalNs / 1_000L;
                nanoseconds = totalNs % 1_000L;
            }
            case "microsecond" -> {
                microseconds = totalNs / 1_000L;
                nanoseconds = totalNs % 1_000L;
            }
            default -> {
                nanoseconds = totalNs;
            }
        }

        if (negative) {
            days = -days;
            hours = -hours;
            minutes = -minutes;
            seconds = -seconds;
            milliseconds = -milliseconds;
            microseconds = -microseconds;
            nanoseconds = -nanoseconds;
        }

        return new TemporalDurationRecord(0, 0, 0, days, hours, minutes, seconds,
                milliseconds, microseconds, nanoseconds);
    }

    public static JSValue blank(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "blank");
        if (d == null) return JSUndefined.INSTANCE;
        return d.getRecord().isBlank() ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static JSTemporalDuration checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalDuration duration)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver " + JSTypeConversions.toString(context, thisArg).value());
            return null;
        }
        return duration;
    }

    public static JSValue days(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "days");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().days());
    }

    private static long getLongFieldOr(JSContext context, JSObject obj, String key, long defaultValue) {
        JSValue value = obj.get(PropertyKey.fromString(key));
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        double num = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(num)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        return (long) num;
    }

    public static JSValue hours(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "hours");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().hours());
    }

    public static JSValue microseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "microseconds");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().microseconds());
    }

    public static JSValue milliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "milliseconds");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().milliseconds());
    }

    public static JSValue minutes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "minutes");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().minutes());
    }

    // ========== Methods ==========

    public static JSValue months(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "months");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().months());
    }

    public static JSValue nanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "nanoseconds");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().nanoseconds());
    }

    public static JSValue negated(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "negated");
        if (d == null) return JSUndefined.INSTANCE;
        return TemporalDurationConstructor.createDuration(context, d.getRecord().negated());
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "round");
        if (d == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        String smallestUnit = "nanosecond";
        String largestUnit = null;

        if (args[0] instanceof JSString unitStr) {
            smallestUnit = unitStr.value();
        } else if (args[0] instanceof JSObject options) {
            smallestUnit = TemporalUtils.getStringOption(context, options, "smallestUnit", "nanosecond");
            largestUnit = TemporalUtils.getStringOption(context, options, "largestUnit", null);
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return JSUndefined.INSTANCE;
        }

        if (largestUnit == null) {
            largestUnit = smallestUnit;
        }

        TemporalDurationRecord r = d.getRecord();
        long totalNs = r.totalNanoseconds();

        // Round to smallest unit
        long unitNs = unitToNanoseconds(smallestUnit);
        if (unitNs > 0 && unitNs > 1) {
            long remainder = Math.floorMod(totalNs, unitNs);
            if (remainder * 2 >= unitNs) {
                totalNs = Math.floorDiv(totalNs, unitNs) * unitNs + unitNs;
            } else {
                totalNs = Math.floorDiv(totalNs, unitNs) * unitNs;
            }
        }

        // Re-balance to largest unit
        TemporalDurationRecord balanced = balanceTimeDuration(totalNs, largestUnit);
        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(r.years(), r.months(), r.weeks(),
                        balanced.days(), balanced.hours(), balanced.minutes(), balanced.seconds(),
                        balanced.milliseconds(), balanced.microseconds(), balanced.nanoseconds()));
    }

    public static JSValue seconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "seconds");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().seconds());
    }

    public static JSValue sign(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "sign");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().sign());
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "subtract");
        if (d == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, d, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "toJSON");
        if (d == null) return JSUndefined.INSTANCE;
        return new JSString(d.getRecord().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "toLocaleString");
        if (d == null) return JSUndefined.INSTANCE;
        return new JSString(d.getRecord().toString());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "toString");
        if (d == null) return JSUndefined.INSTANCE;
        return new JSString(d.getRecord().toString());
    }

    public static JSValue total(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "total");
        if (d == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a totalOf parameter");
            return JSUndefined.INSTANCE;
        }

        String unit;
        if (args[0] instanceof JSString unitStr) {
            unit = unitStr.value();
        } else if (args[0] instanceof JSObject options) {
            unit = TemporalUtils.getStringOption(context, options, "unit", null);
            if (unit == null) {
                context.throwRangeError("Temporal error: Must specify a totalOf parameter");
                return JSUndefined.INSTANCE;
            }
        } else {
            context.throwTypeError("Temporal error: totalOf must be an object.");
            return JSUndefined.INSTANCE;
        }

        long totalNs = d.getRecord().totalNanoseconds();
        long unitNs = unitToNanoseconds(unit);
        if (unitNs == 0) {
            context.throwRangeError("Temporal error: Invalid unit for total.");
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of((double) totalNs / unitNs);
    }

    // ========== Internal helpers ==========

    private static long unitToNanoseconds(String unit) {
        return switch (unit) {
            case "day", "days" -> 86_400_000_000_000L;
            case "hour", "hours" -> 3_600_000_000_000L;
            case "minute", "minutes" -> 60_000_000_000L;
            case "second", "seconds" -> 1_000_000_000L;
            case "millisecond", "milliseconds" -> 1_000_000L;
            case "microsecond", "microseconds" -> 1_000L;
            case "nanosecond", "nanoseconds" -> 1L;
            default -> 0;
        };
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.Duration.prototype.valueOf; use Temporal.Duration.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weeks(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "weeks");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().weeks());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "with");
        if (d == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord r = d.getRecord();
        long years = getLongFieldOr(context, fields, "years", r.years());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long months = getLongFieldOr(context, fields, "months", r.months());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long weeks = getLongFieldOr(context, fields, "weeks", r.weeks());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long days = getLongFieldOr(context, fields, "days", r.days());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long hours = getLongFieldOr(context, fields, "hours", r.hours());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long minutes = getLongFieldOr(context, fields, "minutes", r.minutes());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long seconds = getLongFieldOr(context, fields, "seconds", r.seconds());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long milliseconds = getLongFieldOr(context, fields, "milliseconds", r.milliseconds());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long microseconds = getLongFieldOr(context, fields, "microseconds", r.microseconds());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long nanoseconds = getLongFieldOr(context, fields, "nanoseconds", r.nanoseconds());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        TemporalDurationRecord newRecord = new TemporalDurationRecord(years, months, weeks, days,
                hours, minutes, seconds, milliseconds, microseconds, nanoseconds);

        if (!newRecord.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return JSUndefined.INSTANCE;
        }

        return TemporalDurationConstructor.createDuration(context, newRecord);
    }

    public static JSValue years(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "years");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().years());
    }
}
