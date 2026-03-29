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
import com.caoccao.qjs4j.core.temporal.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.TemporalDurationRecord;
import com.caoccao.qjs4j.core.temporal.TemporalTimeZone;

import java.math.BigInteger;

/**
 * Implementation of Temporal.Instant prototype methods.
 */
public final class TemporalInstantPrototype {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final long MAX_ROUNDING_INCREMENT = 1_000_000_000L;
    private static final BigInteger NS_PER_HOUR = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger NS_PER_MINUTE = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger NS_PER_MS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger NS_PER_SECOND = BILLION;
    private static final BigInteger NS_PER_US = BigInteger.valueOf(1_000L);
    private static final long SOLAR_DAY_HOURS = 24L;
    private static final long SOLAR_DAY_MICROSECONDS = 86_400_000_000L;
    private static final long SOLAR_DAY_MILLISECONDS = 86_400_000L;
    private static final long SOLAR_DAY_MINUTES = 1_440L;
    private static final long SOLAR_DAY_NANOSECONDS = 86_400_000_000_000L;
    private static final long SOLAR_DAY_SECONDS = 86_400L;
    private static final String TYPE_NAME = "Temporal.Instant";

    private TemporalInstantPrototype() {
    }

    // ========== Getters ==========

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "add");
        if (instant == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, instant, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalInstant instant, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        TemporalDurationRecord durationRecord = temporalDuration.getRecord();
        if (durationRecord.years() != 0
                || durationRecord.months() != 0
                || durationRecord.weeks() != 0
                || durationRecord.days() != 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        BigInteger totalNs = BigInteger.valueOf(durationRecord.hours()).multiply(NS_PER_HOUR)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(NS_PER_MINUTE))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(NS_PER_SECOND))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(NS_PER_MS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(NS_PER_US))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
        if (sign < 0) {
            totalNs = totalNs.negate();
        }

        BigInteger result = instant.getEpochNanoseconds().add(totalNs);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(result)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalInstantConstructor.createInstant(context, result);
    }

    // ========== Methods ==========

    private static JSTemporalInstant checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalInstant instant)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver.");
            return null;
        }
        return instant;
    }

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

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "equals");
        if (instant == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        return instant.getEpochNanoseconds().equals(other.getEpochNanoseconds()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static BigInteger floorDiv(BigInteger a, BigInteger b) {
        BigInteger[] divRem = a.divideAndRemainder(b);
        if (divRem[1].signum() < 0 && b.signum() > 0 || divRem[1].signum() > 0 && b.signum() < 0) {
            return divRem[0].subtract(BigInteger.ONE);
        }
        return divRem[0];
    }

    private static BigInteger[] floorDivideAndRemainder(BigInteger value, BigInteger divisor) {
        BigInteger[] quotientAndRemainder = value.divideAndRemainder(divisor);
        BigInteger quotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];
        if (remainder.signum() < 0) {
            quotient = quotient.subtract(BigInteger.ONE);
            remainder = remainder.add(divisor);
        }
        return new BigInteger[]{quotient, remainder};
    }

    private static String formatInstantUtc(BigInteger epochNs) {
        IsoDateTime dt = TemporalTimeZone.epochNsToUtcDateTime(epochNs);
        return dt + "Z";
    }

    private static long getMaximumRoundingIncrement(String unit) {
        return switch (unit) {
            case "hour" -> SOLAR_DAY_HOURS;
            case "minute" -> SOLAR_DAY_MINUTES;
            case "second" -> SOLAR_DAY_SECONDS;
            case "millisecond" -> SOLAR_DAY_MILLISECONDS;
            case "microsecond" -> SOLAR_DAY_MICROSECONDS;
            case "nanosecond" -> SOLAR_DAY_NANOSECONDS;
            default -> -1L;
        };
    }

    private static BigInteger getUnitNs(String unit) {
        return switch (unit) {
            case "hour" -> NS_PER_HOUR;
            case "minute" -> NS_PER_MINUTE;
            case "second" -> NS_PER_SECOND;
            case "millisecond" -> NS_PER_MS;
            case "microsecond" -> NS_PER_US;
            case "nanosecond" -> BigInteger.ONE;
            default -> null;
        };
    }

    private static String normalizeSmallestUnit(String unit) {
        return switch (unit) {
            case "hour", "hours" -> "hour";
            case "minute", "minutes" -> "minute";
            case "second", "seconds" -> "second";
            case "millisecond", "milliseconds" -> "millisecond";
            case "microsecond", "microseconds" -> "microsecond";
            case "nanosecond", "nanoseconds" -> "nanosecond";
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

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "round");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }
        String smallestUnitText;
        long roundingIncrement = 1L;
        String roundingMode = "halfExpand";
        if (args[0] instanceof JSString unitStr) {
            smallestUnitText = unitStr.value();
        } else if (args[0] instanceof JSObject optionsObj) {
            JSValue roundingIncrementValue = optionsObj.get(PropertyKey.fromString("roundingIncrement"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(roundingIncrementValue instanceof JSUndefined) && roundingIncrementValue != null) {
                double roundingIncrementAsDouble = JSTypeConversions.toNumber(context, roundingIncrementValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!Double.isFinite(roundingIncrementAsDouble) || Double.isNaN(roundingIncrementAsDouble)) {
                    context.throwRangeError("Temporal error: Invalid rounding increment.");
                    return JSUndefined.INSTANCE;
                }
                roundingIncrement = (long) roundingIncrementAsDouble;
            }

            JSValue roundingModeValue = optionsObj.get(PropertyKey.fromString("roundingMode"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(roundingModeValue instanceof JSUndefined) && roundingModeValue != null) {
                roundingMode = JSTypeConversions.toString(context, roundingModeValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            JSValue smallestUnitValue = optionsObj.get(PropertyKey.fromString("smallestUnit"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(smallestUnitValue instanceof JSUndefined) && smallestUnitValue != null) {
                smallestUnitText = JSTypeConversions.toString(context, smallestUnitValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            } else {
                smallestUnitText = null;
            }
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return JSUndefined.INSTANCE;
        }

        if (smallestUnitText == null) {
            context.throwRangeError("Temporal error: smallestUnit is required.");
            return JSUndefined.INSTANCE;
        }

        String smallestUnit = normalizeSmallestUnit(smallestUnitText);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid unit for Instant.round: " + smallestUnitText);
            return JSUndefined.INSTANCE;
        }

        boolean validRoundingMode =
                "ceil".equals(roundingMode)
                        || "floor".equals(roundingMode)
                        || "trunc".equals(roundingMode)
                        || "expand".equals(roundingMode)
                        || "halfExpand".equals(roundingMode)
                        || "halfTrunc".equals(roundingMode)
                        || "halfEven".equals(roundingMode)
                        || "halfCeil".equals(roundingMode)
                        || "halfFloor".equals(roundingMode);
        if (!validRoundingMode) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return JSUndefined.INSTANCE;
        }

        if (roundingIncrement < 1 || roundingIncrement > MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return JSUndefined.INSTANCE;
        }

        long maximumRoundingIncrement = getMaximumRoundingIncrement(smallestUnit);
        if (maximumRoundingIncrement <= 0
                || roundingIncrement > maximumRoundingIncrement
                || maximumRoundingIncrement % roundingIncrement != 0) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return JSUndefined.INSTANCE;
        }

        BigInteger unitNs = getUnitNs(smallestUnit);
        if (unitNs == null) {
            context.throwRangeError("Temporal error: Invalid unit for Instant.round: " + smallestUnitText);
            return JSUndefined.INSTANCE;
        }
        BigInteger incrementNs = unitNs.multiply(BigInteger.valueOf(roundingIncrement));

        BigInteger epochNs = instant.getEpochNanoseconds();
        BigInteger rounded = roundBigIntegerToIncrement(epochNs, incrementNs, roundingMode);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(rounded)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalInstantConstructor.createInstant(context, rounded);
    }

    private static BigInteger roundBigIntegerToIncrement(BigInteger value, BigInteger increment, String roundingMode) {
        if (increment.signum() == 0) {
            return value;
        }

        BigInteger[] floorQuotientAndRemainder = floorDivideAndRemainder(value, increment);
        BigInteger floorQuotient = floorQuotientAndRemainder[0];
        BigInteger remainder = floorQuotientAndRemainder[1];
        BigInteger floorValue = floorQuotient.multiply(increment);
        if (remainder.signum() == 0) {
            return floorValue;
        }
        BigInteger ceilValue = floorValue.add(increment);

        switch (roundingMode) {
            case "floor":
                return floorValue;
            case "ceil":
                return ceilValue;
            case "trunc":
                return floorValue;
            case "expand":
                return ceilValue;
            case "halfExpand":
            case "halfTrunc":
            case "halfEven":
            case "halfCeil":
            case "halfFloor":
                BigInteger twoRemainder = remainder.shiftLeft(1);
                int halfComparison = twoRemainder.compareTo(increment);
                if (halfComparison < 0) {
                    return floorValue;
                }
                if (halfComparison > 0) {
                    return ceilValue;
                }
                return switch (roundingMode) {
                    case "halfExpand" -> {
                        yield ceilValue;
                    }
                    case "halfTrunc" -> {
                        yield floorValue;
                    }
                    case "halfEven" -> {
                        if (floorQuotient.testBit(0)) {
                            yield ceilValue;
                        } else {
                            yield floorValue;
                        }
                    }
                    case "halfCeil" -> ceilValue;
                    case "halfFloor" -> floorValue;
                    default -> ceilValue;
                };
            default:
                return ceilValue;
        }
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

    // ========== Internal helpers ==========

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
        if (instant == null) {
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
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{instant});
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
            return new JSString(dt + offset);
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
}
