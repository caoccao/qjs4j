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
import java.util.Locale;

/**
 * Implementation of Temporal.Instant prototype methods.
 */
public final class TemporalInstantPrototype {
    private static final long MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final BigInteger NS_PER_MINUTE = TemporalConstants.BI_MINUTE_NANOSECONDS;
    private static final BigInteger NS_PER_MS = TemporalConstants.BI_MILLISECOND_NANOSECONDS;
    private static final BigInteger NS_PER_SECOND = TemporalConstants.BI_SECOND_NANOSECONDS;
    private static final BigInteger NS_PER_US = TemporalConstants.BI_MICROSECOND_NANOSECONDS;
    private static final String TYPE_NAME = "Temporal.Instant";

    private TemporalInstantPrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "add");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
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
        TemporalDuration durationRecord = temporalDuration.getDuration();
        if (durationRecord.years() != 0
                || durationRecord.months() != 0
                || durationRecord.weeks() != 0
                || durationRecord.days() != 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        BigInteger totalNs = durationRecord.timeNanoseconds();
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

    private static JSTemporalInstant checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        return TemporalUtils.checkReceiver(context, thisArg, JSTemporalInstant.class, TYPE_NAME, methodName);
    }

    public static JSValue epochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "epochMilliseconds");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochMilliseconds = floorDiv(instant.getEpochNanoseconds(), NS_PER_MS);
        return JSNumber.of(epochMilliseconds.longValue());
    }

    public static JSValue epochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "epochNanoseconds");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSBigInt(instant.getEpochNanoseconds());
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "equals");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return instant.getEpochNanoseconds().equals(other.getEpochNanoseconds()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static BigInteger floorDiv(BigInteger dividend, BigInteger divisor) {
        BigInteger[] quotientAndRemainder = dividend.divideAndRemainder(divisor);
        if (quotientAndRemainder[1].signum() < 0 && divisor.signum() > 0
                || quotientAndRemainder[1].signum() > 0 && divisor.signum() < 0) {
            return quotientAndRemainder[0].subtract(BigInteger.ONE);
        }
        return quotientAndRemainder[0];
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

    private static JSString formatInstantWithOptions(JSContext context, JSTemporalInstant instant, JSValue optionsArg) {
        TemporalInstantToStringOptions instantToStringOptions = parseInstantToStringOptions(context, optionsArg);
        if (context.hasPendingException() || instantToStringOptions == null) {
            return null;
        }

        String smallestUnit = instantToStringOptions.smallestUnit();
        TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption =
                instantToStringOptions.fractionalSecondDigitsOption();
        Integer fractionalSecondDigits;
        if (fractionalSecondDigitsOption.auto()) {
            fractionalSecondDigits = null;
        } else {
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
        }
        BigInteger incrementNanoseconds;
        if ("minute".equals(smallestUnit)) {
            incrementNanoseconds = NS_PER_MINUTE;
        } else if ("second".equals(smallestUnit)) {
            incrementNanoseconds = NS_PER_SECOND;
        } else if ("millisecond".equals(smallestUnit)) {
            incrementNanoseconds = NS_PER_MS;
        } else if ("microsecond".equals(smallestUnit)) {
            incrementNanoseconds = NS_PER_US;
        } else if ("nanosecond".equals(smallestUnit)) {
            incrementNanoseconds = BigInteger.ONE;
        } else if (fractionalSecondDigits == null) {
            incrementNanoseconds = BigInteger.ONE;
        } else {
            incrementNanoseconds = BigInteger.valueOf(fractionalSecondDigitsOption.roundingIncrementNanoseconds());
        }

        BigInteger roundedEpochNanoseconds = roundBigIntegerToIncrement(
                instant.getEpochNanoseconds(),
                incrementNanoseconds,
                instantToStringOptions.roundingMode());
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(roundedEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }

        String timeZoneId = instantToStringOptions.timeZoneId();
        IsoDateTime isoDateTime;
        String zoneSuffix;
        if (timeZoneId == null) {
            isoDateTime = IsoDateTime.createByEpochNs(roundedEpochNanoseconds);
            zoneSuffix = "Z";
        } else {
            isoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(roundedEpochNanoseconds, timeZoneId);
            int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(roundedEpochNanoseconds, timeZoneId);
            zoneSuffix = TemporalTimeZone.formatOffsetRoundedToMinute(offsetSeconds);
        }

        String dateTimeText = formatWithPrecision(isoDateTime, smallestUnit, fractionalSecondDigits);
        return new JSString(dateTimeText + zoneSuffix);
    }

    private static String formatWithPrecision(IsoDateTime isoDateTime, String smallestUnit, Integer fractionalSecondDigits) {
        String datePart = isoDateTime.date().toString();
        String timePart;
        if ("minute".equals(smallestUnit)) {
            timePart = String.format(Locale.ROOT, "%02d:%02d", isoDateTime.time().hour(), isoDateTime.time().minute());
        } else if ("second".equals(smallestUnit)) {
            timePart = isoDateTime.time().toString(0);
        } else if ("millisecond".equals(smallestUnit)) {
            timePart = isoDateTime.time().toString(3);
        } else if ("microsecond".equals(smallestUnit)) {
            timePart = isoDateTime.time().toString(6);
        } else if ("nanosecond".equals(smallestUnit)) {
            timePart = isoDateTime.time().toString(9);
        } else {
            timePart = isoDateTime.time().toString(fractionalSecondDigits);
        }
        return datePart + "T" + timePart;
    }

    private static long getMaximumRoundingIncrement(String unit) {
        return TemporalUnit.fromString(unit)
                .map(TemporalUnit::solarDayDivisor)
                .orElse(-1L);
    }

    private static BigInteger getUnitNs(String unit) {
        return TemporalUnit.fromString(unit)
                .map(TemporalUnit::nanosecondFactorLong)
                .filter(factor -> factor > 0)
                .map(BigInteger::valueOf)
                .orElse(null);
    }

    private static JSValue instantDifference(
            JSContext context,
            JSTemporalInstant leftInstant,
            JSTemporalInstant rightInstant,
            JSValue optionsArg) {
        TemporalDifferenceSettings differenceOptions = TemporalDifferenceSettings.parse(
                context, false, optionsArg,
                TemporalUnit.HOUR, TemporalUnit.NANOSECOND,
                TemporalUnit.NANOSECOND, TemporalUnit.SECOND,
                false, true);
        if (context.hasPendingException() || differenceOptions == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger differenceNanoseconds = leftInstant.getEpochNanoseconds().subtract(rightInstant.getEpochNanoseconds());
        BigInteger smallestUnitNanoseconds = getUnitNs(differenceOptions.smallestUnit());
        BigInteger incrementNanoseconds = smallestUnitNanoseconds.multiply(BigInteger.valueOf(differenceOptions.roundingIncrement()));
        BigInteger roundedNanoseconds = TemporalMathKernel.roundBigIntegerToIncrementSigned(
                differenceNanoseconds,
                incrementNanoseconds,
                differenceOptions.roundingMode());

        TemporalDuration balancedDuration = TemporalDurationPrototype.balanceTimeDuration(
                roundedNanoseconds,
                differenceOptions.largestUnit());
        TemporalDuration normalizedDuration =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(balancedDuration);
        if (!TemporalDurationConstructor.isDurationRecordTimeRangeValid(normalizedDuration)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, normalizedDuration);
    }

    private static TemporalUnit normalizeToStringSmallestUnit(String unit) {
        return TemporalUnit.fromString(unit)
                .filter(u -> u.isTimeUnit() && u != TemporalUnit.HOUR)
                .orElse(null);
    }

    private static TemporalInstantToStringOptions parseInstantToStringOptions(JSContext context, JSValue optionsArg) {
        JSObject optionsObject;
        if (optionsArg instanceof JSUndefined || optionsArg == null) {
            return TemporalInstantToStringOptions.DEFAULT;
        } else if (optionsArg instanceof JSObject typedOptionsObject) {
            optionsObject = typedOptionsObject;
        } else {
            context.throwTypeError("Temporal error: Options must be an object.");
            return null;
        }

        TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption = TemporalFractionalSecondDigitsOption.autoOption();
        String roundingModeText = "trunc";
        String smallestUnitText = null;
        JSValue timeZoneValue = JSUndefined.INSTANCE;
        JSValue fractionalSecondDigitsValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
        if (context.hasPendingException()) {
            return null;
        }
        TemporalFractionalSecondDigitsOption resolvedFractionalSecondDigitsOption =
                TemporalOptionResolver.parseFractionalSecondDigitsOption(
                        context,
                        fractionalSecondDigitsValue,
                        "Temporal error: Invalid fractionalSecondDigits.");
        if (context.hasPendingException() || resolvedFractionalSecondDigitsOption == null) {
            return null;
        }
        fractionalSecondDigitsOption = resolvedFractionalSecondDigitsOption;

        JSValue roundingModeValue = optionsObject.get(PropertyKey.fromString("roundingMode"));
        if (context.hasPendingException()) {
            return null;
        }
        if (!(roundingModeValue instanceof JSUndefined) && roundingModeValue != null) {
            roundingModeText = JSTypeConversions.toString(context, roundingModeValue).value();
            if (context.hasPendingException()) {
                return null;
            }
        }

        JSValue smallestUnitValue = optionsObject.get(PropertyKey.fromString("smallestUnit"));
        if (context.hasPendingException()) {
            return null;
        }
        if (!(smallestUnitValue instanceof JSUndefined) && smallestUnitValue != null) {
            smallestUnitText = JSTypeConversions.toString(context, smallestUnitValue).value();
            if (context.hasPendingException()) {
                return null;
            }
        }

        timeZoneValue = optionsObject.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return null;
        }

        String timeZoneId = null;
        if (!(timeZoneValue instanceof JSUndefined) && timeZoneValue != null) {
            if (!(timeZoneValue instanceof JSString timeZoneString)) {
                context.throwTypeError("Temporal error: Time zone must be string");
                return null;
            }
            timeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneString.value());
            if (context.hasPendingException()) {
                return null;
            }
        }

        TemporalRoundingMode roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        TemporalUnit smallestUnit = null;
        if (smallestUnitText != null) {
            smallestUnit = normalizeToStringSmallestUnit(smallestUnitText);
            if (smallestUnit == null) {
                context.throwRangeError("Temporal error: Invalid smallest unit.");
                return null;
            }
        }

        if (timeZoneId != null) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (Exception invalidTimeZoneException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return null;
            }
        }

        return new TemporalInstantToStringOptions(
                fractionalSecondDigitsOption,
                roundingMode,
                smallestUnit == null ? null : smallestUnit.jsName(),
                timeZoneId);
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

        String smallestUnit = TemporalUnit.fromString(smallestUnitText)
                .filter(TemporalUnit::isTimeUnit)
                .map(TemporalUnit::jsName)
                .orElse(null);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid unit for Instant.round: " + smallestUnitText);
            return JSUndefined.INSTANCE;
        }

        TemporalRoundingMode parsedRoundingMode = TemporalRoundingMode.fromString(roundingMode);
        if (parsedRoundingMode == null) {
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
        BigInteger rounded = roundBigIntegerToIncrement(epochNs, incrementNs, parsedRoundingMode);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(rounded)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalInstantConstructor.createInstant(context, rounded);
    }

    private static BigInteger roundBigIntegerToIncrement(
            BigInteger value,
            BigInteger increment,
            TemporalRoundingMode roundingMode) {
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
            case FLOOR:
                return floorValue;
            case CEIL:
                return ceilValue;
            case TRUNC:
                return floorValue;
            case EXPAND:
                return ceilValue;
            case HALF_EXPAND:
            case HALF_TRUNC:
            case HALF_EVEN:
            case HALF_CEIL:
            case HALF_FLOOR:
                BigInteger twoRemainder = remainder.shiftLeft(1);
                int halfComparison = twoRemainder.compareTo(increment);
                if (halfComparison < 0) {
                    return floorValue;
                }
                if (halfComparison > 0) {
                    return ceilValue;
                }
                return switch (roundingMode) {
                    case HALF_EXPAND -> {
                        yield ceilValue;
                    }
                    case HALF_TRUNC -> {
                        yield floorValue;
                    }
                    case HALF_EVEN -> {
                        if (floorQuotient.testBit(0)) {
                            yield ceilValue;
                        } else {
                            yield floorValue;
                        }
                    }
                    case HALF_CEIL -> ceilValue;
                    case HALF_FLOOR -> floorValue;
                    default -> ceilValue;
                };
            default:
                return ceilValue;
        }
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "since");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return instantDifference(context, instant, other, optionsArg);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "subtract");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, instant, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "toJSON");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSString formatted = formatInstantWithOptions(context, instant, JSUndefined.INSTANCE);
        if (context.hasPendingException() || formatted == null) {
            return JSUndefined.INSTANCE;
        }
        return formatted;
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
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSString formatted = formatInstantWithOptions(context, instant, optionsArg);
        if (context.hasPendingException() || formatted == null) {
            return JSUndefined.INSTANCE;
        }
        return formatted;
    }

    public static JSValue toZonedDateTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "toZonedDateTimeISO");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue tzArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(tzArg instanceof JSString tzStr)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(context, tzStr.value());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        try {
            TemporalTimeZone.resolveTimeZone(timeZoneId);
        } catch (Exception invalidTimeZoneException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                instant.getEpochNanoseconds(), timeZoneId, TemporalCalendarId.ISO8601);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = checkReceiver(context, thisArg, "until");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return instantDifference(context, other, instant, optionsArg);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.Instant.prototype.valueOf; use Temporal.Instant.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }
}
