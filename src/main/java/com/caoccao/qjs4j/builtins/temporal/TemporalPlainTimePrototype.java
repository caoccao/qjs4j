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
 * Implementation of Temporal.PlainTime prototype methods.
 */
public final class TemporalPlainTimePrototype {
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;
    private static final String TYPE_NAME = "Temporal.PlainTime";

    private TemporalPlainTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "add");
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
        BigInteger durationNanoseconds = durationRecord.dayTimeNanoseconds();
        if (sign < 0) {
            durationNanoseconds = durationNanoseconds.negate();
        }

        BigInteger dayNanoseconds = DAY_NANOSECONDS;
        BigInteger timeNanoseconds = BigInteger.valueOf(plainTime.getIsoTime().totalNanoseconds());
        BigInteger resultNanoseconds = timeNanoseconds.add(durationNanoseconds).remainder(dayNanoseconds);
        if (resultNanoseconds.signum() < 0) {
            resultNanoseconds = resultNanoseconds.add(dayNanoseconds);
        }

        return TemporalPlainTimeConstructor.createPlainTime(
                context,
                IsoTime.createFromNanoseconds(resultNanoseconds.longValue()));
    }

    private static TemporalUnit canonicalizeToStringSmallestUnit(String unitText) {
        return TemporalUnit.fromString(unitText)
                .filter(u -> u.isSmallerOrEqual(TemporalUnit.MINUTE))
                .orElse(null);
    }

    private static JSValue differencePlainTime(
            JSContext context,
            JSTemporalPlainTime plainTime,
            JSTemporalPlainTime other,
            JSValue optionsArg,
            boolean sinceOperation) {
        TemporalDifferenceSettings differenceSettings = TemporalDifferenceSettings.parse(
                context, false, optionsArg,
                TemporalUnit.HOUR, TemporalUnit.NANOSECOND,
                TemporalUnit.NANOSECOND, TemporalUnit.HOUR,
                false, true);
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

        long smallestUnitNanoseconds = differenceSettings.smallestUnit().getNanosecondFactor();
        BigInteger incrementNanoseconds = BigInteger.valueOf(smallestUnitNanoseconds)
                .multiply(BigInteger.valueOf(differenceSettings.roundingIncrement()));
        BigInteger roundedNanoseconds = differenceSettings.roundingMode().roundBigIntegerToIncrementSigned(
                differenceNanoseconds,
                incrementNanoseconds);

        TemporalDuration balancedDuration = TemporalDuration.createBalance(
                roundedNanoseconds,
                differenceSettings.largestUnit());
        TemporalDuration normalizedDuration = balancedDuration.normalizeFloat64RepresentableFields();
        if (!normalizedDuration.isValid() || !TemporalDuration.isDurationRecordTimeRangeValid(normalizedDuration)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, normalizedDuration);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "equals");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainTime other = TemporalPlainTimeConstructor.toTemporalTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = plainTime.getIsoTime().compareTo(other.getIsoTime()) == 0;
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static TemporalPlainTimeToStringSettings getToStringSettings(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalOptionResolver.toOptionalOptionsObject(
                context,
                optionsValue,
                "Temporal error: Option must be object: options.");
        if (context.hasPendingException()) {
            return null;
        }
        if (optionsObject == null) {
            return TemporalPlainTimeToStringSettings.DEFAULT;
        }

        TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption = TemporalFractionalSecondDigitsOption.autoOption();
        TemporalRoundingMode roundingMode = TemporalRoundingMode.TRUNC;
        String smallestUnitText = null;
        JSValue fractionalSecondDigitsValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
        if (context.hasPendingException()) {
            return null;
        }
        TemporalFractionalSecondDigitsOption resolvedFractionalSecondDigitsOption =
                TemporalFractionalSecondDigitsOption.parse(
                        context,
                        fractionalSecondDigitsValue,
                        "Temporal error: Invalid fractionalSecondDigits.");
        if (context.hasPendingException() || resolvedFractionalSecondDigitsOption == null) {
            return null;
        }
        fractionalSecondDigitsOption = resolvedFractionalSecondDigitsOption;

        String roundingModeText = TemporalUtils.getStringOption(context, optionsObject, "roundingMode", "trunc");
        if (context.hasPendingException() || roundingModeText == null) {
            return null;
        }

        smallestUnitText = TemporalUtils.getStringOption(context, optionsObject, "smallestUnit", null);
        if (context.hasPendingException()) {
            return null;
        }

        roundingMode = TemporalRoundingMode.fromString(roundingModeText);
        if (roundingMode == null) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        TemporalUnit smallestUnit = null;
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
            fractionalSecondDigits = smallestUnit.getStringFractionalSecondDigits();
            roundingIncrementNanoseconds = smallestUnit.getStringRoundingIncrementNanoseconds();
        } else if (autoFractionalSecondDigits) {
            fractionalSecondDigits = -1;
            roundingIncrementNanoseconds = 1L;
        } else {
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
            roundingIncrementNanoseconds = fractionalSecondDigitsOption.roundingIncrementNanoseconds();
        }

        return new TemporalPlainTimeToStringSettings(
                smallestUnit,
                roundingMode,
                autoFractionalSecondDigits,
                fractionalSecondDigits,
                roundingIncrementNanoseconds);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "hour");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().hour());
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "microsecond");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "millisecond");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "minute");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().minute());
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "nanosecond");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().nanosecond());
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "round");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        TemporalRoundSettings roundSettings =
                TemporalRoundSettings.parse(context, args[0], TemporalUnit.HOUR, TemporalUnit.NANOSECOND);
        if (context.hasPendingException() || roundSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long totalNs = plainTime.getIsoTime().totalNanoseconds();
        long unitNs = roundSettings.smallestUnit().getNanosecondFactor();
        long incrementNs = unitNs * roundSettings.roundingIncrement();
        long roundedNs = roundSettings.roundingMode().roundLongToIncrementAsIfPositive(
                totalNs,
                incrementNs);
        if (roundedNs == DAY_NANOSECONDS.longValue()) {
            roundedNs = 0L;
        }

        return TemporalPlainTimeConstructor.createPlainTime(context, IsoTime.createFromNanoseconds(roundedNs));
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "second");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainTime.getIsoTime().second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "since");
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
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "subtract");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainTime, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "toJSON");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainTime.getIsoTime().toString());
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "toLocaleString");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (options instanceof JSObject optionsObject) {
            JSValue dateStyleValue = optionsObject.get(PropertyKey.fromString("dateStyle"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(dateStyleValue instanceof JSUndefined) && dateStyleValue != null) {
                context.throwTypeError("Invalid date/time formatting options");
                return JSUndefined.INSTANCE;
            }
        }
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
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "toString");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        TemporalPlainTimeToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long roundedNanoseconds = plainTime.getIsoTime().totalNanoseconds();
        if (toStringSettings.roundingIncrementNanoseconds() > 1L) {
            roundedNanoseconds = toStringSettings.roundingMode().roundLongToIncrementAsIfPositive(
                    roundedNanoseconds,
                    toStringSettings.roundingIncrementNanoseconds());
        }
        if (roundedNanoseconds == DAY_NANOSECONDS.longValue()) {
            roundedNanoseconds = 0L;
        }
        IsoTime roundedTime = IsoTime.createFromNanoseconds(roundedNanoseconds);
        return new JSString(roundedTime.formatTimeString(
                toStringSettings.smallestUnit(),
                toStringSettings.autoFractionalSecondDigits(),
                toStringSettings.fractionalSecondDigits()));
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "until");
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
        JSTemporalPlainTime plainTime = TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainTime.class, TYPE_NAME, "with");
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
            IsoTime isoTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            if (!isoTime.isValid()) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            return TemporalPlainTimeConstructor.createPlainTime(context, isoTime);
        } else {
            IsoTime constrained = IsoTime.createNormalized(hour, minute, second, millisecond, microsecond, nanosecond);
            return TemporalPlainTimeConstructor.createPlainTime(context, constrained);
        }
    }
}
