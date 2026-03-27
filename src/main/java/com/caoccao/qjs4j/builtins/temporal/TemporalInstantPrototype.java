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
import com.caoccao.qjs4j.core.temporal.*;

import java.math.BigInteger;

/**
 * Implementation of Temporal.Instant prototype methods.
 */
public final class TemporalInstantPrototype {
    private static final String TYPE_NAME = "Temporal.Instant";
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger NS_PER_MS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger NS_PER_HOUR = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger NS_PER_MINUTE = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger NS_PER_SECOND = BILLION;

    private TemporalInstantPrototype() {
    }

    // ========== Getters ==========

    public static JSValue epochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "epochMilliseconds");
        if (instant == null) return JSUndefined.INSTANCE;
        BigInteger ms = floorDiv(instant.getEpochNanoseconds(), NS_PER_MS);
        return JSNumber.of(ms.longValue());
    }

    public static JSValue epochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "epochNanoseconds");
        if (instant == null) return JSUndefined.INSTANCE;
        return new JSBigInt(instant.getEpochNanoseconds());
    }

    // ========== Methods ==========

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "add");
        if (instant == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, instant, args, 1);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "equals");
        if (instant == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        return instant.getEpochNanoseconds().equals(other.getEpochNanoseconds()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "round");
        if (instant == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }
        String smallestUnit;
        if (args[0] instanceof JSString unitStr) {
            smallestUnit = unitStr.value();
        } else if (args[0] instanceof JSObject optionsObj) {
            smallestUnit = TemporalUtils.getStringOption(context, optionsObj, "smallestUnit", null);
            if (smallestUnit == null) {
                context.throwRangeError("Temporal error: smallestUnit is required.");
                return JSUndefined.INSTANCE;
            }
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return JSUndefined.INSTANCE;
        }

        BigInteger unitNs = getUnitNs(smallestUnit);
        if (unitNs == null) {
            context.throwRangeError("Temporal error: Invalid unit for Instant.round: " + smallestUnit);
            return JSUndefined.INSTANCE;
        }

        BigInteger epochNs = instant.getEpochNanoseconds();
        BigInteger rounded = roundToIncrement(epochNs, unitNs);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(rounded)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalInstantConstructor.createInstant(context, rounded);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "since");
        if (instant == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        BigInteger diffNs = instant.getEpochNanoseconds().subtract(other.getEpochNanoseconds());
        return nsToDuration(context, diffNs);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "subtract");
        if (instant == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, instant, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "toJSON");
        if (instant == null) return JSUndefined.INSTANCE;
        return new JSString(formatInstantUtc(instant.getEpochNanoseconds()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "toLocaleString");
        if (instant == null) return JSUndefined.INSTANCE;
        return new JSString(formatInstantUtc(instant.getEpochNanoseconds()));
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "toString");
        if (instant == null) return JSUndefined.INSTANCE;

        String timeZoneId = null;
        if (args.length > 0 && args[0] instanceof JSObject optionsObj) {
            JSValue tzVal = optionsObj.get(PropertyKey.fromString("timeZone"));
            if (tzVal instanceof JSString tzStr) {
                timeZoneId = tzStr.value();
            }
        }

        if (timeZoneId != null) {
            try {
                java.time.ZoneId.of(timeZoneId);
            } catch (Exception e) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return JSUndefined.INSTANCE;
            }
            IsoDateTime dt = TemporalTimeZone.epochNsToDateTimeInZone(instant.getEpochNanoseconds(), timeZoneId);
            int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(instant.getEpochNanoseconds(), timeZoneId);
            String offset = TemporalTimeZone.formatOffset(offsetSeconds);
            return new JSString(dt.toString() + offset);
        }
        return new JSString(formatInstantUtc(instant.getEpochNanoseconds()));
    }

    public static JSValue toZonedDateTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "toZonedDateTimeISO");
        if (instant == null) return JSUndefined.INSTANCE;
        JSValue tzArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(tzArg instanceof JSString tzStr)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = tzStr.value();
        try {
            java.time.ZoneId.of(timeZoneId);
        } catch (Exception e) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                instant.getEpochNanoseconds(), timeZoneId, "iso8601");
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "until");
        if (instant == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        BigInteger diffNs = other.getEpochNanoseconds().subtract(instant.getEpochNanoseconds());
        return nsToDuration(context, diffNs);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.Instant.prototype.valueOf; use Temporal.Instant.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    // ========== Internal helpers ==========

    private static JSValue addOrSubtract(JSContext context, JSTemporalInstant instant, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        long hours = 0, minutes = 0, seconds = 0, ms = 0, us = 0, ns = 0;
        JSValue durationArg = args[0];
        if (durationArg instanceof JSTemporalDuration dur) {
            TemporalDurationRecord rec = dur.getRecord();
            hours = rec.hours();
            minutes = rec.minutes();
            seconds = rec.seconds();
            ms = rec.milliseconds();
            us = rec.microseconds();
            ns = rec.nanoseconds();
        } else if (durationArg instanceof JSString durStr) {
            TemporalParser.DurationFields df = TemporalParser.parseDurationString(context, durStr.value());
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            hours = df.hours();
            minutes = df.minutes();
            seconds = df.seconds();
            ms = df.milliseconds();
            us = df.microseconds();
            ns = df.nanoseconds();
        } else if (durationArg instanceof JSObject durObj) {
            hours = TemporalUtils.getIntegerField(context, durObj, "hours", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            minutes = TemporalUtils.getIntegerField(context, durObj, "minutes", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            seconds = TemporalUtils.getIntegerField(context, durObj, "seconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            ms = TemporalUtils.getIntegerField(context, durObj, "milliseconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            us = TemporalUtils.getIntegerField(context, durObj, "microseconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            ns = TemporalUtils.getIntegerField(context, durObj, "nanoseconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        } else {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        BigInteger totalNs = BigInteger.valueOf(hours * 3_600_000_000_000L
                + minutes * 60_000_000_000L + seconds * 1_000_000_000L
                + ms * 1_000_000L + us * 1_000L + ns);
        if (sign < 0) totalNs = totalNs.negate();

        BigInteger result = instant.getEpochNanoseconds().add(totalNs);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(result)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalInstantConstructor.createInstant(context, result);
    }

    private static JSTemporalInstant checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalInstant instant)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver " + JSTypeConversions.toString(context, thisArg).value());
            return null;
        }
        return instant;
    }

    private static BigInteger floorDiv(BigInteger a, BigInteger b) {
        BigInteger[] divRem = a.divideAndRemainder(b);
        if (divRem[1].signum() < 0 && b.signum() > 0 || divRem[1].signum() > 0 && b.signum() < 0) {
            return divRem[0].subtract(BigInteger.ONE);
        }
        return divRem[0];
    }

    private static String formatInstantUtc(BigInteger epochNs) {
        IsoDateTime dt = TemporalTimeZone.epochNsToUtcDateTime(epochNs);
        return dt.toString() + "Z";
    }

    private static BigInteger getUnitNs(String unit) {
        return switch (unit) {
            case "hour" -> NS_PER_HOUR;
            case "minute" -> NS_PER_MINUTE;
            case "second" -> NS_PER_SECOND;
            case "millisecond" -> NS_PER_MS;
            case "microsecond" -> BigInteger.valueOf(1_000L);
            case "nanosecond" -> BigInteger.ONE;
            default -> null;
        };
    }

    private static JSValue nsToDuration(JSContext context, BigInteger diffNs) {
        int signum = diffNs.signum();
        BigInteger abs = diffNs.abs();
        // For Instant, the default largestUnit is "second" per spec
        long totalSeconds = abs.divide(NS_PER_SECOND).longValue();
        abs = abs.remainder(NS_PER_SECOND);
        long totalMs = abs.divide(NS_PER_MS).longValue();
        abs = abs.remainder(NS_PER_MS);
        long totalUs = abs.divide(BigInteger.valueOf(1_000)).longValue();
        long totalNs = abs.remainder(BigInteger.valueOf(1_000)).longValue();

        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, 0, 0, 0,
                        0, 0, totalSeconds * signum,
                        totalMs * signum, totalUs * signum, totalNs * signum));
    }

    private static BigInteger roundToIncrement(BigInteger value, BigInteger increment) {
        BigInteger[] divRem = value.divideAndRemainder(increment);
        BigInteger remainder = divRem[1];
        if (remainder.signum() < 0) {
            remainder = remainder.add(increment);
        }
        BigInteger half = increment.divide(BigInteger.TWO);
        if (remainder.compareTo(half) > 0 || (remainder.equals(half) && divRem[0].testBit(0))) {
            return value.subtract(remainder).add(increment);
        }
        return value.subtract(remainder);
    }
}
