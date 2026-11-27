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
    private static final long MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final TemporalReceiver<JSTemporalInstant> RECEIVER = TemporalReceiver.INSTANT;

    private TemporalInstantPrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSValue thisArg, JSValue[] args, int sign) {
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, sign > 0 ? "add" : "subtract");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration durationRecord = TemporalDurationConstructor.toDurationRecord(context,
                JSValue.argumentOrUndefined(args, 0));
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }
        if (durationRecord.hasAnyDateUnits()) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        BigInteger totalNs = durationRecord.timeNanoseconds();
        if (sign < 0) {
            totalNs = totalNs.negate();
        }

        BigInteger result = instant.getEpochNanoseconds().add(totalNs);
        if (!TemporalUtils.isValidEpochNanoseconds(result)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, result);
    }

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, since ? "since" : "until");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalInstant other = TemporalInstantConstructor.toTemporalInstantObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        return since
                ? instantDifference(context, instant, other, optionsArg)
                : instantDifference(context, other, instant, optionsArg);
    }

    public static JSValue epochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, "epochMilliseconds");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalUtils.epochMilliseconds(instant.getEpochNanoseconds()));
    }

    public static JSValue epochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "epochNanoseconds",
                instant -> new JSBigInt(instant.getEpochNanoseconds()));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.equalTo(context, thisArg, args,
                (left, right) -> left.getEpochNanoseconds().equals(right.getEpochNanoseconds()));
    }

    private static JSString formatInstantWithOptions(JSContext context, JSTemporalInstant instant, JSValue optionsArg) {
        TemporalInstantToStringOptions instantToStringOptions = TemporalInstantToStringOptions.read(context,
                optionsArg);
        if (context.hasPendingException() || instantToStringOptions == null) {
            return null;
        }

        BigInteger roundedEpochNanoseconds = instantToStringOptions.roundedEpoch(context,
                instant.getEpochNanoseconds());
        if (roundedEpochNanoseconds == null) {
            return null;
        }

        String timeZoneId = instantToStringOptions.timeZoneId();
        IsoDateTime isoDateTime;
        String zoneSuffix;
        if (timeZoneId == null) {
            isoDateTime = IsoDateTime.createByEpochNs(roundedEpochNanoseconds);
            zoneSuffix = "Z";
        } else {
            int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(roundedEpochNanoseconds, timeZoneId);
            isoDateTime = IsoDateTime.createFromEpochNsAndOffset(roundedEpochNanoseconds, offsetSeconds);
            zoneSuffix = TemporalTimeZone.formatOffsetRoundedToMinute(offsetSeconds);
        }

        String dateTimeText = instantToStringOptions.format(isoDateTime);
        return new JSString(dateTimeText + zoneSuffix);
    }

    private static BigInteger getUnitNs(TemporalUnit unit) {
        long nanosecondFactor = unit.getNanosecondFactor();
        if (nanosecondFactor <= 0L) {
            return null;
        }
        return BigInteger.valueOf(nanosecondFactor);
    }

    private static JSValue instantDifference(JSContext context, JSTemporalInstant leftInstant,
            JSTemporalInstant rightInstant, JSValue optionsArg) {
        TemporalDifferenceSettings differenceOptions = TemporalDifferenceSettings.parse(context, false, optionsArg,
                TemporalUnit.HOUR, TemporalUnit.NANOSECOND, TemporalUnit.NANOSECOND, TemporalUnit.SECOND, false, true);
        if (context.hasPendingException() || differenceOptions == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger differenceNanoseconds = leftInstant.getEpochNanoseconds()
                .subtract(rightInstant.getEpochNanoseconds());
        TemporalDuration normalizedDuration = differenceOptions.roundTimeDifference(differenceNanoseconds);
        if (!TemporalDuration.isDurationRecordTimeRangeValid(normalizedDuration)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, normalizedDuration);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, "round");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }
        String smallestUnitText;
        long roundingIncrement = 1L;
        TemporalRoundingMode roundingMode = TemporalRoundingMode.HALF_EXPAND;
        if (args[0] instanceof JSString unitStr) {
            smallestUnitText = unitStr.value();
        } else if (args[0] instanceof JSObject optionsObj) {
            roundingIncrement = TemporalUtils.readRoundingIncrement(context, optionsObj,
                    "Temporal error: Invalid rounding increment.");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String mode = TemporalUtils.getStringOption(context, optionsObj, "roundingMode", "halfExpand");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            roundingMode = TemporalRoundingMode.fromString(mode);
            if (roundingMode == null) {
                context.throwRangeError("Temporal error: Invalid rounding mode.");
                return JSUndefined.INSTANCE;
            }
            smallestUnitText = TemporalUtils.getStringOption(context, optionsObj, "smallestUnit", null);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return JSUndefined.INSTANCE;
        }

        if (smallestUnitText == null) {
            context.throwRangeError("Temporal error: smallestUnit is required.");
            return JSUndefined.INSTANCE;
        }

        TemporalUnit smallestUnit = TemporalUnit.fromString(smallestUnitText).filter(TemporalUnit::isTimeUnit)
                .orElse(null);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid unit for Instant.round: " + smallestUnitText);
            return JSUndefined.INSTANCE;
        }

        if (roundingIncrement < 1 || roundingIncrement > MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return JSUndefined.INSTANCE;
        }

        long maximumRoundingIncrement = smallestUnit.getSolarDayDivisor();
        if (maximumRoundingIncrement <= 0 || roundingIncrement > maximumRoundingIncrement
                || maximumRoundingIncrement % roundingIncrement != 0) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return JSUndefined.INSTANCE;
        }

        BigInteger incrementNs = smallestUnit.fixedNanoseconds().multiply(BigInteger.valueOf(roundingIncrement));
        BigInteger epochNs = instant.getEpochNanoseconds();
        BigInteger rounded = roundingMode.roundBigIntegerToIncrementAsIfPositive(epochNs, incrementNs);
        if (!TemporalUtils.isValidEpochNanoseconds(rounded)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, rounded);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, "toJSON");
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
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, "toLocaleString");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalLocaleFormat.formatDateTime(context, instant, args, "date");
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, "toString");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 0);
        JSString formatted = formatInstantWithOptions(context, instant, optionsArg);
        if (context.hasPendingException() || formatted == null) {
            return JSUndefined.INSTANCE;
        }
        return formatted;
    }

    public static JSValue toZonedDateTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalInstant instant = RECEIVER.require(context, thisArg, "toZonedDateTimeISO");
        if (instant == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue tzArg = JSValue.argumentOrUndefined(args, 0);
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
        return JSTemporalZonedDateTime.create(context, instant.getEpochNanoseconds(), timeZoneId,
                TemporalCalendarId.ISO8601);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }
}
