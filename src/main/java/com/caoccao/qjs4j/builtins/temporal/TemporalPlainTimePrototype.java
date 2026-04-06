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
import com.caoccao.qjs4j.core.temporal.TemporalDuration;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.math.BigInteger;

/**
 * Implementation of Temporal.PlainTime prototype methods.
 */
public final class TemporalPlainTimePrototype {
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final long MAX_ROUNDING_INCREMENT = 1_000_000_000L;
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

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainTime plainTime, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException() || temporalDuration == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration durationRecord = temporalDuration.getDuration();
        BigInteger durationNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
        if (sign < 0) {
            durationNanoseconds = durationNanoseconds.negate();
        }

        BigInteger dayNanoseconds = BigInteger.valueOf(86_400_000_000_000L);
        BigInteger timeNanoseconds = BigInteger.valueOf(plainTime.getIsoTime().totalNanoseconds());
        BigInteger resultNanoseconds = timeNanoseconds.add(durationNanoseconds).remainder(dayNanoseconds);
        if (resultNanoseconds.signum() < 0) {
            resultNanoseconds = resultNanoseconds.add(dayNanoseconds);
        }

        return TemporalPlainTimeConstructor.createPlainTime(
                context,
                IsoTime.fromNanoseconds(resultNanoseconds.longValue()));
    }

    private static String canonicalizeDifferenceUnit(String unitText, boolean largestUnit) {
        if ("auto".equals(unitText) && largestUnit) {
            return "auto";
        }
        return switch (unitText) {
            case "hour", "hours" -> "hour";
            case "minute", "minutes" -> "minute";
            case "second", "seconds" -> "second";
            case "millisecond", "milliseconds" -> "millisecond";
            case "microsecond", "microseconds" -> "microsecond";
            case "nanosecond", "nanoseconds" -> "nanosecond";
            default -> null;
        };
    }

    private static String canonicalizeToStringSmallestUnit(String unitText) {
        return switch (unitText) {
            case "minute", "minutes" -> "minute";
            case "second", "seconds" -> "second";
            case "millisecond", "milliseconds" -> "millisecond";
            case "microsecond", "microseconds" -> "microsecond";
            case "nanosecond", "nanoseconds" -> "nanosecond";
            default -> null;
        };
    }

    private static JSTemporalPlainTime checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainTime plainTime)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainTime;
    }

    private static JSValue differencePlainTime(
            JSContext context,
            JSTemporalPlainTime plainTime,
            JSTemporalPlainTime other,
            JSValue optionsArg,
            boolean sinceOperation) {
        DifferenceSettings differenceSettings = getDifferenceSettings(context, optionsArg);
        if (context.hasPendingException() || differenceSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long leftNanoseconds = plainTime.getIsoTime().totalNanoseconds();
        long rightNanoseconds = other.getIsoTime().totalNanoseconds();
        BigInteger differenceNanoseconds;
        if (sinceOperation) {
            differenceNanoseconds = BigInteger.valueOf(leftNanoseconds - rightNanoseconds);
        } else {
            differenceNanoseconds = BigInteger.valueOf(rightNanoseconds - leftNanoseconds);
        }

        long smallestUnitNanoseconds = unitToNanoseconds(differenceSettings.smallestUnit());
        BigInteger incrementNanoseconds = BigInteger.valueOf(smallestUnitNanoseconds)
                .multiply(BigInteger.valueOf(differenceSettings.roundingIncrement()));
        BigInteger roundedNanoseconds = roundBigIntegerToIncrementSigned(
                differenceNanoseconds,
                incrementNanoseconds,
                differenceSettings.roundingMode());

        TemporalDuration balancedDuration = TemporalDurationPrototype.balanceTimeDuration(
                roundedNanoseconds,
                differenceSettings.largestUnit());
        TemporalDuration normalizedDuration =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(balancedDuration);
        if (!normalizedDuration.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(normalizedDuration)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, normalizedDuration);
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

    private static long getDifferenceRoundingIncrementMaximum(String smallestUnit) {
        return switch (smallestUnit) {
            case "hour" -> 24L;
            case "minute" -> 60L;
            case "second" -> 60L;
            case "millisecond" -> 1_000L;
            case "microsecond" -> 1_000L;
            case "nanosecond" -> 1_000L;
            default -> -1L;
        };
    }

    private static DifferenceSettings getDifferenceSettings(JSContext context, JSValue optionsArg) {
        JSObject optionsObject = null;
        if (!(optionsArg instanceof JSUndefined) && optionsArg != null) {
            if (optionsArg instanceof JSObject castedOptionsObject) {
                optionsObject = castedOptionsObject;
            } else {
                context.throwTypeError("Temporal error: Options must be an object.");
                return null;
            }
        }

        String largestUnitText = null;
        long roundingIncrement = 1L;
        String roundingMode = "trunc";
        String smallestUnitText = null;
        if (optionsObject != null) {
            largestUnitText = getStringOption(context, optionsObject, "largestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }

            roundingIncrement = getRoundingIncrementOption(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }

            roundingMode = getStringOption(context, optionsObject, "roundingMode", "trunc");
            if (context.hasPendingException()) {
                return null;
            }

            smallestUnitText = getStringOption(context, optionsObject, "smallestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
        }

        String largestUnit = largestUnitText == null
                ? "auto"
                : canonicalizeDifferenceUnit(largestUnitText, true);
        if (largestUnit == null) {
            context.throwRangeError("Temporal error: Invalid largest unit.");
            return null;
        }
        String smallestUnit = smallestUnitText == null
                ? "nanosecond"
                : canonicalizeDifferenceUnit(smallestUnitText, false);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid smallest unit.");
            return null;
        }

        if (!isValidRoundingMode(roundingMode)) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        if ("auto".equals(largestUnit)) {
            largestUnit = largerOfTwoTemporalUnits("hour", smallestUnit);
        }
        if (!largestUnit.equals(largerOfTwoTemporalUnits(largestUnit, smallestUnit))) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        if (!isValidDifferenceRoundingIncrement(smallestUnit, roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        return new DifferenceSettings(largestUnit, smallestUnit, roundingIncrement, roundingMode);
    }

    private static String getRequiredSmallestUnitOption(JSContext context, JSObject optionsObject) {
        JSValue smallestUnitValue = optionsObject.get(PropertyKey.fromString("smallestUnit"));
        if (context.hasPendingException()) {
            return null;
        }
        if (smallestUnitValue instanceof JSUndefined || smallestUnitValue == null) {
            context.throwRangeError("Temporal error: Must specify a roundTo parameter.");
            return null;
        }
        JSString smallestUnitText = JSTypeConversions.toString(context, smallestUnitValue);
        if (context.hasPendingException() || smallestUnitText == null) {
            return null;
        }
        return smallestUnitText.value();
    }

    private static RoundSettings getRoundSettings(JSContext context, JSValue roundTo) {
        long roundingIncrement = 1L;
        String roundingMode = "halfExpand";
        String smallestUnitText;

        if (roundTo instanceof JSString unitString) {
            smallestUnitText = unitString.value();
        } else if (roundTo instanceof JSObject optionsObject) {
            // Read and coerce all option properties before algorithmic validation.
            roundingIncrement = getRoundingIncrementOption(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }
            roundingMode = getStringOption(context, optionsObject, "roundingMode", "halfExpand");
            if (context.hasPendingException() || roundingMode == null) {
                return null;
            }
            smallestUnitText = getRequiredSmallestUnitOption(context, optionsObject);
            if (context.hasPendingException() || smallestUnitText == null) {
                return null;
            }
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return null;
        }

        if (!isValidRoundingMode(roundingMode)) {
            context.throwRangeError("Temporal error: Invalid roundingMode option: " + roundingMode);
            return null;
        }

        String smallestUnit = canonicalizeDifferenceUnit(smallestUnitText, false);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid unit for rounding.");
            return null;
        }

        if (!isValidDifferenceRoundingIncrement(smallestUnit, roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
            return null;
        }

        return new RoundSettings(smallestUnit, roundingIncrement, roundingMode);
    }

    private static long getRoundingIncrementOption(JSContext context, JSObject optionsObject) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString("roundingIncrement"));
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return 1L;
        }
        JSNumber numericValue = JSTypeConversions.toNumber(context, optionValue);
        if (context.hasPendingException() || numericValue == null) {
            return Long.MIN_VALUE;
        }
        double roundingIncrement = numericValue.value();
        if (!Double.isFinite(roundingIncrement) || Double.isNaN(roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
            return Long.MIN_VALUE;
        }
        long integerIncrement = (long) roundingIncrement;
        if (integerIncrement < 1L || integerIncrement > MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
            return Long.MIN_VALUE;
        }
        return integerIncrement;
    }

    private static String getStringOption(JSContext context, JSObject optionsObject, String optionName, String defaultValue) {
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

    private static String getToStringFractionalPart(IsoTime time, int digits) {
        if (digits <= 0) {
            return "";
        }
        String nineDigits = String.format("%03d%03d%03d",
                time.millisecond(),
                time.microsecond(),
                time.nanosecond());
        return nineDigits.substring(0, digits);
    }

    private static FractionalSecondDigitsOption getToStringFractionalSecondDigitsOption(JSContext context, JSValue value) {
        if (value instanceof JSUndefined) {
            return new FractionalSecondDigitsOption(true, -1);
        }
        if (value instanceof JSNumber numberValue) {
            double numericValue = numberValue.value();
            if (!Double.isFinite(numericValue) || Double.isNaN(numericValue)) {
                context.throwRangeError("Temporal error: Invalid fractionalSecondDigits.");
                return null;
            }
            int flooredValue = (int) Math.floor(numericValue);
            if (flooredValue < 0 || flooredValue > 9) {
                context.throwRangeError("Temporal error: Invalid fractionalSecondDigits.");
                return null;
            }
            return new FractionalSecondDigitsOption(false, flooredValue);
        }

        JSString stringValue = JSTypeConversions.toString(context, value);
        if (context.hasPendingException() || stringValue == null) {
            return null;
        }
        if ("auto".equals(stringValue.value())) {
            return new FractionalSecondDigitsOption(true, -1);
        }
        context.throwRangeError("Temporal error: Invalid fractionalSecondDigits.");
        return null;
    }

    private static ToStringSettings getToStringSettings(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = null;
        if (!(optionsValue instanceof JSUndefined) && optionsValue != null) {
            if (optionsValue instanceof JSObject castedOptionsObject) {
                optionsObject = castedOptionsObject;
            } else {
                context.throwTypeError("Temporal error: Option must be object: options.");
                return null;
            }
        }

        FractionalSecondDigitsOption fractionalSecondDigitsOption = new FractionalSecondDigitsOption(true, -1);
        String roundingMode = "trunc";
        String smallestUnitText = null;
        if (optionsObject != null) {
            JSValue fractionalSecondDigitsValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
            if (context.hasPendingException()) {
                return null;
            }
            fractionalSecondDigitsOption = getToStringFractionalSecondDigitsOption(context, fractionalSecondDigitsValue);
            if (context.hasPendingException() || fractionalSecondDigitsOption == null) {
                return null;
            }

            roundingMode = getStringOption(context, optionsObject, "roundingMode", "trunc");
            if (context.hasPendingException() || roundingMode == null) {
                return null;
            }

            JSValue smallestUnitValue = optionsObject.get(PropertyKey.fromString("smallestUnit"));
            if (context.hasPendingException()) {
                return null;
            }
            if (!(smallestUnitValue instanceof JSUndefined) && smallestUnitValue != null) {
                JSString smallestUnitString = JSTypeConversions.toString(context, smallestUnitValue);
                if (context.hasPendingException() || smallestUnitString == null) {
                    return null;
                }
                smallestUnitText = smallestUnitString.value();
            }
        }

        if (!isValidRoundingMode(roundingMode)) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        String smallestUnit = null;
        if (smallestUnitText != null) {
            smallestUnit = canonicalizeToStringSmallestUnit(smallestUnitText);
            if (smallestUnit == null) {
                context.throwRangeError("Temporal error: Invalid smallestUnit option.");
                return null;
            }
        }

        boolean autoFractionalSecondDigits = smallestUnit == null && fractionalSecondDigitsOption.auto();
        int fractionalSecondDigits;
        long roundingIncrementNanoseconds;
        if (smallestUnit != null) {
            fractionalSecondDigits = switch (smallestUnit) {
                case "second" -> 0;
                case "millisecond" -> 3;
                case "microsecond" -> 6;
                case "nanosecond" -> 9;
                default -> 0;
            };
            roundingIncrementNanoseconds = switch (smallestUnit) {
                case "minute" -> 60_000_000_000L;
                case "second" -> 1_000_000_000L;
                case "millisecond" -> 1_000_000L;
                case "microsecond" -> 1_000L;
                case "nanosecond" -> 1L;
                default -> 1L;
            };
        } else if (autoFractionalSecondDigits) {
            fractionalSecondDigits = -1;
            roundingIncrementNanoseconds = 1L;
        } else {
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
            if (fractionalSecondDigits == 0) {
                roundingIncrementNanoseconds = 1_000_000_000L;
            } else {
                roundingIncrementNanoseconds = (long) Math.pow(10, 9 - fractionalSecondDigits);
            }
        }

        return new ToStringSettings(
                smallestUnit,
                roundingMode,
                autoFractionalSecondDigits,
                fractionalSecondDigits,
                roundingIncrementNanoseconds);
    }

    private static String getToStringTimeString(IsoTime time, ToStringSettings toStringSettings) {
        String hourMinute = String.format("%02d:%02d", time.hour(), time.minute());
        if ("minute".equals(toStringSettings.smallestUnit())) {
            return hourMinute;
        }

        String hourMinuteSecond = String.format("%s:%02d", hourMinute, time.second());
        if (toStringSettings.autoFractionalSecondDigits()) {
            String fullFraction = String.format("%03d%03d%03d",
                    time.millisecond(),
                    time.microsecond(),
                    time.nanosecond());
            int fractionEndIndex = fullFraction.length();
            while (fractionEndIndex > 0 && fullFraction.charAt(fractionEndIndex - 1) == '0') {
                fractionEndIndex--;
            }
            if (fractionEndIndex == 0) {
                return hourMinuteSecond;
            }
            return hourMinuteSecond + "." + fullFraction.substring(0, fractionEndIndex);
        }

        int fractionDigits = toStringSettings.fractionalSecondDigits();
        if (fractionDigits == 0) {
            return hourMinuteSecond;
        }
        return hourMinuteSecond + "." + getToStringFractionalPart(time, fractionDigits);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "hour");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().hour());
    }

    private static boolean isValidDifferenceRoundingIncrement(String smallestUnit, long roundingIncrement) {
        long maximumIncrement = getDifferenceRoundingIncrementMaximum(smallestUnit);
        if (maximumIncrement <= 0) {
            return false;
        }
        if (roundingIncrement < 1L || roundingIncrement >= maximumIncrement) {
            return false;
        }
        return maximumIncrement % roundingIncrement == 0L;
    }

    private static boolean isValidRoundingMode(String roundingMode) {
        return "ceil".equals(roundingMode)
                || "floor".equals(roundingMode)
                || "trunc".equals(roundingMode)
                || "expand".equals(roundingMode)
                || "halfExpand".equals(roundingMode)
                || "halfTrunc".equals(roundingMode)
                || "halfEven".equals(roundingMode)
                || "halfCeil".equals(roundingMode)
                || "halfFloor".equals(roundingMode);
    }

    private static String largerOfTwoTemporalUnits(String leftUnit, String rightUnit) {
        return temporalUnitRank(leftUnit) <= temporalUnitRank(rightUnit) ? leftUnit : rightUnit;
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "microsecond");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().microsecond());
    }

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

        RoundSettings roundSettings = getRoundSettings(context, args[0]);
        if (context.hasPendingException() || roundSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long totalNs = plainTime.getIsoTime().totalNanoseconds();
        long unitNs = unitToNanoseconds(roundSettings.smallestUnit());
        long incrementNs = unitNs * roundSettings.roundingIncrement();
        long roundedNs = roundToIncrementAsIfPositive(totalNs, incrementNs, roundSettings.roundingMode());
        if (roundedNs == DAY_NANOSECONDS.longValue()) {
            roundedNs = 0L;
        }

        return TemporalPlainTimeConstructor.createPlainTime(context, IsoTime.fromNanoseconds(roundedNs));
    }

    private static BigInteger roundBigIntegerToIncrementSigned(BigInteger value, BigInteger increment, String roundingMode) {
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
        int sign = value.signum();

        return switch (roundingMode) {
            case "floor" -> floorValue;
            case "ceil" -> ceilValue;
            case "trunc" -> sign < 0 ? ceilValue : floorValue;
            case "expand" -> sign < 0 ? floorValue : ceilValue;
            case "halfExpand", "halfTrunc", "halfEven", "halfCeil", "halfFloor" -> {
                BigInteger twoRemainder = remainder.shiftLeft(1);
                int halfComparison = twoRemainder.compareTo(increment);
                if (halfComparison < 0) {
                    yield floorValue;
                }
                if (halfComparison > 0) {
                    yield ceilValue;
                }
                yield switch (roundingMode) {
                    case "halfExpand" -> sign < 0 ? floorValue : ceilValue;
                    case "halfTrunc" -> sign < 0 ? ceilValue : floorValue;
                    case "halfEven" -> floorQuotient.testBit(0) ? ceilValue : floorValue;
                    case "halfCeil" -> ceilValue;
                    case "halfFloor" -> floorValue;
                    default -> ceilValue;
                };
            }
            default -> ceilValue;
        };
    }

    private static long roundToIncrementAsIfPositive(long quantity, long increment, String roundingMode) {
        long quotient = Math.floorDiv(quantity, increment);
        long lower = quotient * increment;
        long remainder = quantity - lower;
        if (remainder == 0L) {
            return quantity;
        }
        long upper = lower + increment;

        return switch (roundingMode) {
            case "ceil", "expand" -> upper;
            case "floor", "trunc" -> lower;
            case "halfExpand", "halfCeil" -> {
                if (remainder * 2L >= increment) {
                    yield upper;
                } else {
                    yield lower;
                }
            }
            case "halfTrunc", "halfFloor" -> {
                if (remainder * 2L > increment) {
                    yield upper;
                } else {
                    yield lower;
                }
            }
            case "halfEven" -> {
                long doubleRemainder = remainder * 2L;
                if (doubleRemainder < increment) {
                    yield lower;
                } else if (doubleRemainder > increment) {
                    yield upper;
                } else if (Math.floorMod(quotient, 2L) == 0L) {
                    yield lower;
                } else {
                    yield upper;
                }
            }
            default -> lower;
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
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return differencePlainTime(context, plainTime, other, optionsArg, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = checkReceiver(context, thisArg, "subtract");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainTime, args, -1);
    }

    private static int temporalUnitRank(String unit) {
        return switch (unit) {
            case "hour" -> 0;
            case "minute" -> 1;
            case "second" -> 2;
            case "millisecond" -> 3;
            case "microsecond" -> 4;
            case "nanosecond" -> 5;
            default -> 6;
        };
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
        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        ToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long roundedNanoseconds = plainTime.getIsoTime().totalNanoseconds();
        if (toStringSettings.roundingIncrementNanoseconds() > 1L) {
            roundedNanoseconds = roundToIncrementAsIfPositive(
                    roundedNanoseconds,
                    toStringSettings.roundingIncrementNanoseconds(),
                    toStringSettings.roundingMode());
        }
        if (roundedNanoseconds == DAY_NANOSECONDS.longValue()) {
            roundedNanoseconds = 0L;
        }
        IsoTime roundedTime = IsoTime.fromNanoseconds(roundedNanoseconds);
        return new JSString(getToStringTimeString(roundedTime, toStringSettings));
    }

    private static long unitToNanoseconds(String unit) {
        return switch (unit) {
            case "hour" -> 3_600_000_000_000L;
            case "minute" -> 60_000_000_000L;
            case "second" -> 1_000_000_000L;
            case "millisecond" -> 1_000_000L;
            case "microsecond" -> 1_000L;
            case "nanosecond" -> 1L;
            default -> 0L;
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
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return differencePlainTime(context, plainTime, other, optionsArg, false);
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

        if (fields instanceof JSTemporalPlainDate
                || fields instanceof JSTemporalPlainDateTime
                || fields instanceof JSTemporalPlainMonthDay
                || fields instanceof JSTemporalPlainTime
                || fields instanceof JSTemporalPlainYearMonth
                || fields instanceof JSTemporalZonedDateTime) {
            context.throwTypeError("Temporal error: Must specify at least one time field.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarLike = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(calendarLike instanceof JSUndefined) && calendarLike != null) {
            context.throwTypeError("Temporal error: Must specify at least one time field.");
            return JSUndefined.INSTANCE;
        }

        JSValue timeZoneLike = fields.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(timeZoneLike instanceof JSUndefined) && timeZoneLike != null) {
            context.throwTypeError("Temporal error: Must specify at least one time field.");
            return JSUndefined.INSTANCE;
        }

        IsoTime original = plainTime.getIsoTime();

        JSValue hourValue = fields.get(PropertyKey.fromString("hour"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasHour = !(hourValue instanceof JSUndefined) && hourValue != null;
        int hour = original.hour();
        if (hasHour) {
            hour = TemporalUtils.toIntegerThrowOnInfinity(context, hourValue);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue microsecondValue = fields.get(PropertyKey.fromString("microsecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMicrosecond = !(microsecondValue instanceof JSUndefined) && microsecondValue != null;
        int microsecond = original.microsecond();
        if (hasMicrosecond) {
            microsecond = TemporalUtils.toIntegerThrowOnInfinity(context, microsecondValue);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue millisecondValue = fields.get(PropertyKey.fromString("millisecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMillisecond = !(millisecondValue instanceof JSUndefined) && millisecondValue != null;
        int millisecond = original.millisecond();
        if (hasMillisecond) {
            millisecond = TemporalUtils.toIntegerThrowOnInfinity(context, millisecondValue);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue minuteValue = fields.get(PropertyKey.fromString("minute"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMinute = !(minuteValue instanceof JSUndefined) && minuteValue != null;
        int minute = original.minute();
        if (hasMinute) {
            minute = TemporalUtils.toIntegerThrowOnInfinity(context, minuteValue);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue nanosecondValue = fields.get(PropertyKey.fromString("nanosecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasNanosecond = !(nanosecondValue instanceof JSUndefined) && nanosecondValue != null;
        int nanosecond = original.nanosecond();
        if (hasNanosecond) {
            nanosecond = TemporalUtils.toIntegerThrowOnInfinity(context, nanosecondValue);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue secondValue = fields.get(PropertyKey.fromString("second"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasSecond = !(secondValue instanceof JSUndefined) && secondValue != null;
        int second = original.second();
        if (hasSecond) {
            second = TemporalUtils.toIntegerThrowOnInfinity(context, secondValue);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean hasAnyField = hasHour
                || hasMicrosecond
                || hasMillisecond
                || hasMinute
                || hasNanosecond
                || hasSecond;
        if (!hasAnyField) {
            context.throwTypeError("Temporal error: Must specify at least one time field.");
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
            IsoTime constrained = IsoTime.constrain(hour, minute, second, millisecond, microsecond, nanosecond);
            return TemporalPlainTimeConstructor.createPlainTime(context, constrained);
        }
    }

    private record DifferenceSettings(
            String largestUnit,
            String smallestUnit,
            long roundingIncrement,
            String roundingMode) {
    }

    private record FractionalSecondDigitsOption(boolean auto, int digits) {
    }

    private record RoundSettings(
            String smallestUnit,
            long roundingIncrement,
            String roundingMode) {
    }

    private record ToStringSettings(
            String smallestUnit,
            String roundingMode,
            boolean autoFractionalSecondDigits,
            int fractionalSecondDigits,
            long roundingIncrementNanoseconds) {
    }
}
