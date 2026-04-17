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
import java.time.DateTimeException;

/**
 * Implementation of Temporal.PlainDateTime prototype methods.
 */
public final class TemporalPlainDateTimePrototype {
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;
    private static final long MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final long MAX_SUPPORTED_EPOCH_DAY = TemporalConstants.MAX_SUPPORTED_EPOCH_DAY;
    private static final BigInteger MICROSECOND_NANOSECONDS = TemporalConstants.BI_MICROSECOND_NANOSECONDS;
    private static final BigInteger MILLISECOND_NANOSECONDS = TemporalConstants.BI_MILLISECOND_NANOSECONDS;
    private static final BigInteger MINUTE_NANOSECONDS = TemporalConstants.BI_MINUTE_NANOSECONDS;
    private static final long MIN_SUPPORTED_EPOCH_DAY = TemporalConstants.MIN_SUPPORTED_EPOCH_DAY;
    private static final BigInteger SECOND_NANOSECONDS = TemporalConstants.BI_SECOND_NANOSECONDS;
    private static final String TYPE_NAME = "Temporal.PlainDateTime";

    private TemporalPlainDateTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "add");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDateTime, args, 1);
    }

    private static TemporalTimeAddResult addDurationToTime(
            JSContext context,
            IsoTime time,
            TemporalDuration durationRecord) {
        TemporalTimeAddResult result = durationRecord.addToTime(time);
        if (result == null) {
            context.throwRangeError("Temporal error: Duration field out of range.");
        }
        return result;
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainDateTime plainDateTime, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = temporalDuration.getDuration();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        TemporalTimeAddResult timeResult = addDurationToTime(context, plainDateTime.getIsoDateTime().time(), durationRecord);
        if (context.hasPendingException() || timeResult == null) {
            return JSUndefined.INSTANCE;
        }

        long adjustedDays;
        try {
            adjustedDays = Math.addExact(durationRecord.days(), timeResult.dayCarry());
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        String calendarId = plainDateTime.getCalendarId();
        IsoDate newDate;
        if ("iso8601".equals(calendarId)) {
            newDate = TemporalDurationArithmeticKernel.addDurationToIsoDate(
                    context,
                    plainDateTime.getIsoDateTime().date(),
                    durationRecord.years(),
                    durationRecord.months(),
                    durationRecord.weeks(),
                    adjustedDays,
                    overflow);
        } else {
            newDate = TemporalCalendarMath.addCalendarDate(
                    context,
                    plainDateTime.getIsoDateTime().date(),
                    calendarId,
                    durationRecord.years(),
                    durationRecord.months(),
                    durationRecord.weeks(),
                    adjustedDays,
                    overflow);
        }
        if (context.hasPendingException() || newDate == null) {
            return JSUndefined.INSTANCE;
        }

        if (!isValidPlainDateTimeRange(newDate, timeResult.time())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(
                context,
                new IsoDateTime(newDate, timeResult.time()),
                plainDateTime.getCalendarId());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "calendarId");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainDateTime.getCalendarId());
    }

    private static String canonicalizeSmallestUnit(String unitText) {
        return TemporalUnit.fromString(unitText)
                .filter(u -> u.isSmallerOrEqual(TemporalUnit.DAY))
                .map(TemporalUnit::jsName)
                .orElse(null);
    }

    private static String canonicalizeToStringSmallestUnit(String unitText) {
        return TemporalUnit.fromString(unitText)
                .filter(u -> u.isSmallerOrEqual(TemporalUnit.MINUTE))
                .map(TemporalUnit::jsName)
                .orElse(null);
    }

    private static JSTemporalPlainDateTime checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        return TemporalUtils.checkReceiver(context, thisArg, JSTemporalPlainDateTime.class, TYPE_NAME, methodName);
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "day");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.day(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "dayOfWeek");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.dayOfWeek(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "dayOfYear");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.dayOfYear(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "daysInMonth");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.daysInMonth(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "daysInWeek");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "daysInYear");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.daysInYear(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    private static TemporalDuration differenceTemporalPlainDateTime(
            JSContext context,
            JSTemporalPlainDateTime firstDateTime,
            JSTemporalPlainDateTime secondDateTime,
            TemporalDifferenceSettings settings) {
        String calendarId = firstDateTime.getCalendarId();
        boolean noRounding = settings.roundingIncrement() == 1L
                && "nanosecond".equals(settings.smallestUnit());
        boolean sameTime = firstDateTime.getIsoDateTime().time().compareTo(
                secondDateTime.getIsoDateTime().time()) == 0;
        boolean dateLargestUnit = "year".equals(settings.largestUnit())
                || "month".equals(settings.largestUnit())
                || "week".equals(settings.largestUnit())
                || "day".equals(settings.largestUnit());
        if (!"iso8601".equals(calendarId) && noRounding && sameTime && dateLargestUnit) {
            return TemporalPlainDatePrototype.differenceCalendarDates(
                    context,
                    firstDateTime.getIsoDateTime().date(),
                    secondDateTime.getIsoDateTime().date(),
                    calendarId,
                    settings.largestUnit());
        } else {
            return TemporalDurationPrototype.differencePlainDateTime(
                    context,
                    firstDateTime.getIsoDateTime(),
                    secondDateTime.getIsoDateTime(),
                    settings.largestUnit(),
                    settings.smallestUnit(),
                    settings.roundingIncrement(),
                    settings.roundingMode().jsName());
        }
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "equals");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = plainDateTime.getIsoDateTime().compareTo(other.getIsoDateTime()) == 0
                && plainDateTime.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "era");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.era(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "eraYear");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.eraYear(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    private static TemporalDifferenceSettings getDifferenceSettings(
            JSContext context,
            boolean sinceOperation,
            JSValue optionsArg) {
        return TemporalDifferenceSettings.parse(
                context, sinceOperation, optionsArg,
                TemporalUnit.YEAR, TemporalUnit.NANOSECOND,
                TemporalUnit.NANOSECOND, TemporalUnit.DAY,
                true, true);
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
        return JSTypeConversions.toString(context, smallestUnitValue).value();
    }

    private static TemporalRoundSettings getRoundSettings(JSContext context, JSValue roundTo) {
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
            roundingMode = getRoundingModeOption(context, optionsObject);
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

        String smallestUnit = canonicalizeSmallestUnit(smallestUnitText);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid unit for rounding.");
            return null;
        }

        if (!isValidRoundingIncrementForSmallestUnit(smallestUnit, roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
            return null;
        }

        return new TemporalRoundSettings(smallestUnit, roundingIncrement, TemporalRoundingMode.fromString(roundingMode));
    }

    private static long getRoundingIncrementOption(JSContext context, JSObject optionsObject) {
        JSValue roundingIncrementValue = optionsObject.get(PropertyKey.fromString("roundingIncrement"));
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (roundingIncrementValue instanceof JSUndefined || roundingIncrementValue == null) {
            return 1L;
        }
        double numericRoundingIncrement = JSTypeConversions.toNumber(context, roundingIncrementValue).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(numericRoundingIncrement) || Double.isNaN(numericRoundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
            return Long.MIN_VALUE;
        }
        long integerRoundingIncrement = (long) numericRoundingIncrement;
        if (integerRoundingIncrement < 1L || integerRoundingIncrement > MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid roundingIncrement option.");
            return Long.MIN_VALUE;
        }
        return integerRoundingIncrement;
    }

    private static String getRoundingModeOption(JSContext context, JSObject optionsObject) {
        JSValue roundingModeValue = optionsObject.get(PropertyKey.fromString("roundingMode"));
        if (context.hasPendingException()) {
            return null;
        }
        if (roundingModeValue instanceof JSUndefined || roundingModeValue == null) {
            return "halfExpand";
        }
        return JSTypeConversions.toString(context, roundingModeValue).value();
    }

    private static String getTemporalDisambiguation(JSContext context, JSValue optionsArg) {
        if (optionsArg instanceof JSUndefined || optionsArg == null) {
            return "compatible";
        }
        if (!(optionsArg instanceof JSObject optionsObject)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }
        String disambiguation = TemporalOptionResolver.getStringOption(context, optionsObject, "disambiguation", "compatible");
        if (context.hasPendingException() || disambiguation == null) {
            return null;
        }
        if (!TemporalDisambiguation.isValid(disambiguation)) {
            context.throwRangeError("Temporal error: Invalid disambiguation option.");
            return null;
        }
        return disambiguation;
    }

    private static String getToStringCalendarNameOption(JSContext context, JSObject optionsObject) {
        String calendarNameOption = TemporalOptionResolver.getStringOption(context, optionsObject, "calendarName", "auto");
        if (context.hasPendingException() || calendarNameOption == null) {
            return null;
        }
        if (!"auto".equals(calendarNameOption)
                && !"always".equals(calendarNameOption)
                && !"never".equals(calendarNameOption)
                && !"critical".equals(calendarNameOption)) {
            context.throwRangeError("Temporal error: Invalid calendarName option: " + calendarNameOption);
            return null;
        }
        return calendarNameOption;
    }

    private static TemporalFractionalSecondDigitsOption getToStringFractionalSecondDigitsOption(JSContext context, JSValue value) {
        if (value instanceof JSUndefined) {
            return new TemporalFractionalSecondDigitsOption(true, -1);
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
            return new TemporalFractionalSecondDigitsOption(false, flooredValue);
        }

        String stringValue = JSTypeConversions.toString(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        if ("auto".equals(stringValue)) {
            return new TemporalFractionalSecondDigitsOption(true, -1);
        }
        context.throwRangeError("Temporal error: Invalid fractionalSecondDigits.");
        return null;
    }

    private static TemporalPlainDateTimeToStringSettings getToStringSettings(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = null;
        if (!(optionsValue instanceof JSUndefined) && optionsValue != null) {
            if (optionsValue instanceof JSObject castedOptionsObject) {
                optionsObject = castedOptionsObject;
            } else {
                context.throwTypeError("Temporal error: Option must be object: options.");
                return null;
            }
        }

        String calendarNameOption = "auto";
        TemporalFractionalSecondDigitsOption fractionalSecondDigitsOption = new TemporalFractionalSecondDigitsOption(true, -1);
        String roundingMode = "trunc";
        String smallestUnitText = null;
        if (optionsObject != null) {
            calendarNameOption = getToStringCalendarNameOption(context, optionsObject);
            if (context.hasPendingException() || calendarNameOption == null) {
                return null;
            }

            JSValue fractionalSecondDigitsValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
            if (context.hasPendingException()) {
                return null;
            }
            fractionalSecondDigitsOption = getToStringFractionalSecondDigitsOption(context, fractionalSecondDigitsValue);
            if (context.hasPendingException() || fractionalSecondDigitsOption == null) {
                return null;
            }

            roundingMode = TemporalOptionResolver.getStringOption(context, optionsObject, "roundingMode", "trunc");
            if (context.hasPendingException() || roundingMode == null) {
                return null;
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
                case "minute" -> MINUTE_NANOSECONDS.longValue();
                case "second" -> SECOND_NANOSECONDS.longValue();
                case "millisecond" -> MILLISECOND_NANOSECONDS.longValue();
                case "microsecond" -> MICROSECOND_NANOSECONDS.longValue();
                case "nanosecond" -> 1L;
                default -> 1L;
            };
        } else if (autoFractionalSecondDigits) {
            fractionalSecondDigits = -1;
            roundingIncrementNanoseconds = 1L;
        } else {
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
            if (fractionalSecondDigits == 0) {
                roundingIncrementNanoseconds = SECOND_NANOSECONDS.longValue();
            } else {
                roundingIncrementNanoseconds = (long) Math.pow(10, 9 - fractionalSecondDigits);
            }
        }

        return new TemporalPlainDateTimeToStringSettings(
                calendarNameOption,
                smallestUnit,
                roundingMode,
                autoFractionalSecondDigits,
                fractionalSecondDigits,
                roundingIncrementNanoseconds);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "hour");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDateTime.getIsoDateTime().time().hour());
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "inLeapYear");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.inLeapYear(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    private static boolean isValidPlainDateTimeRange(IsoDate date, IsoTime time) {
        long epochDay = date.toEpochDay();
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return false;
        }
        return epochDay != MIN_SUPPORTED_EPOCH_DAY || time.totalNanoseconds() != 0L;
    }

    private static boolean isValidRoundingIncrementForSmallestUnit(String smallestUnit, long roundingIncrement) {
        if ("day".equals(smallestUnit)) {
            return roundingIncrement == 1L;
        }
        long dividend = switch (smallestUnit) {
            case "hour" -> 24L;
            case "minute", "second" -> 60L;
            case "millisecond", "microsecond", "nanosecond" -> 1_000L;
            default -> 0L;
        };
        return roundingIncrement >= 1L
                && roundingIncrement < dividend
                && dividend % roundingIncrement == 0L;
    }

    private static boolean isValidRoundingMode(String roundingMode) {
        return TemporalRoundingMode.isValid(roundingMode);
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "microsecond");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDateTime.getIsoDateTime().time().microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "millisecond");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDateTime.getIsoDateTime().time().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "minute");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDateTime.getIsoDateTime().time().minute());
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "month");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.month(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "monthCode");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.monthCode(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "monthsInYear");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.monthsInYear(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "nanosecond");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDateTime.getIsoDateTime().time().nanosecond());
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "round");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        TemporalRoundSettings roundSettings = getRoundSettings(context, args[0]);
        if (context.hasPendingException() || roundSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long totalNanoseconds = plainDateTime.getIsoDateTime().time().totalNanoseconds();
        long unitNanoseconds = unitToNanoseconds(roundSettings.smallestUnit());
        long incrementNanoseconds = unitNanoseconds * roundSettings.roundingIncrement();
        long roundedNanoseconds = roundToIncrementAsIfPositive(
                totalNanoseconds,
                incrementNanoseconds,
                roundSettings.roundingMode().jsName());

        int dayAdjust = 0;
        if (roundedNanoseconds == DAY_NANOSECONDS.longValue()) {
            dayAdjust = 1;
            roundedNanoseconds = 0L;
        }

        IsoDate adjustedDate = plainDateTime.getIsoDateTime().date();
        if (dayAdjust != 0) {
            adjustedDate = adjustedDate.addDays(dayAdjust);
        }
        IsoTime adjustedTime = IsoTime.createFromNanoseconds(roundedNanoseconds);
        if (!isValidPlainDateTimeRange(adjustedDate, adjustedTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(
                context,
                new IsoDateTime(adjustedDate, adjustedTime),
                plainDateTime.getCalendarId());
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
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "second");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainDateTime.getIsoDateTime().time().second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "since");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!plainDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        TemporalDifferenceSettings settings = getDifferenceSettings(context, true, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = differenceTemporalPlainDateTime(
                context,
                plainDateTime,
                other,
                settings);
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration resultRecord =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(durationRecord.negated());
        if (!resultRecord.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, resultRecord);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "subtract");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainDateTime, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "toJSON");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalPlainDateTimeToStringSettings toStringSettings = new TemporalPlainDateTimeToStringSettings("auto", null, "trunc", true, -1, 1L);
        String formattedString = toTemporalPlainDateTimeString(context, plainDateTime, toStringSettings);
        if (context.hasPendingException() || formattedString == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(formattedString);
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "toLocaleString");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, options},
                "any",
                "all");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainDateTime});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "toPlainDate");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "toPlainTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainTimeConstructor.createPlainTime(context, plainDateTime.getIsoDateTime().time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "toString");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        TemporalPlainDateTimeToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        String formattedString = toTemporalPlainDateTimeString(context, plainDateTime, toStringSettings);
        if (context.hasPendingException() || formattedString == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(formattedString);
    }

    private static String toTemporalPlainDateTimeString(
            JSContext context,
            JSTemporalPlainDateTime plainDateTime,
            TemporalPlainDateTimeToStringSettings toStringSettings) {
        IsoDate isoDate = plainDateTime.getIsoDateTime().date();
        IsoTime isoTime = plainDateTime.getIsoDateTime().time();

        long roundedNanoseconds = isoTime.totalNanoseconds();
        if (toStringSettings.roundingIncrementNanoseconds() > 1L) {
            roundedNanoseconds = roundToIncrementAsIfPositive(
                    roundedNanoseconds,
                    toStringSettings.roundingIncrementNanoseconds(),
                    toStringSettings.roundingMode());
        }

        int dayAdjust = 0;
        if (roundedNanoseconds == DAY_NANOSECONDS.longValue()) {
            dayAdjust = 1;
            roundedNanoseconds = 0L;
        }

        IsoDate roundedDate = isoDate;
        if (dayAdjust != 0) {
            roundedDate = roundedDate.addDays(dayAdjust);
        }
        IsoTime roundedTime = IsoTime.createFromNanoseconds(roundedNanoseconds);
        if (!isValidPlainDateTimeRange(roundedDate, roundedTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String dateString = roundedDate.toString();
        String timeString = roundedTime.formatTimeString(
                toStringSettings.smallestUnit(),
                toStringSettings.autoFractionalSecondDigits(),
                toStringSettings.fractionalSecondDigits());
        String dateTimeString = dateString + "T" + timeString;
        return TemporalUtils.maybeAppendCalendar(dateTimeString, plainDateTime.getCalendarId(), toStringSettings.calendarNameOption());
    }

    private static String toTemporalTimeZoneIdentifier(JSContext context, JSValue timeZoneLike) {
        if (!(timeZoneLike instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
        }
        return TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneString.value());
    }

    public static JSValue toZonedDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "toZonedDateTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue temporalTimeZoneLike = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String timeZoneId = toTemporalTimeZoneIdentifier(context, temporalTimeZoneLike);
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        String disambiguation = getTemporalDisambiguation(context, optionsArg);
        if (context.hasPendingException() || disambiguation == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger epochNanoseconds;
        try {
            epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    plainDateTime.getIsoDateTime(),
                    timeZoneId,
                    disambiguation);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        return TemporalZonedDateTimeConstructor.createZonedDateTime(
                context,
                epochNanoseconds,
                timeZoneId,
                plainDateTime.getCalendarId());
    }

    private static long unitToNanoseconds(String unit) {
        return TemporalUnit.fromString(unit)
                .map(TemporalUnit::nanosecondFactorLong)
                .orElse(0L);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "until");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!plainDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        TemporalDifferenceSettings settings = getDifferenceSettings(context, false, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = differenceTemporalPlainDateTime(
                context,
                plainDateTime,
                other,
                settings);
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration resultRecord =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(durationRecord);
        if (!resultRecord.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, resultRecord);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainDateTime.prototype.valueOf; use Temporal.PlainDateTime.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "weekOfYear");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.weekOfYear(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "with");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        if (fields instanceof JSTemporalPlainDate
                || fields instanceof JSTemporalPlainDateTime
                || fields instanceof JSTemporalPlainMonthDay
                || fields instanceof JSTemporalPlainTime
                || fields instanceof JSTemporalPlainYearMonth
                || fields instanceof JSTemporalZonedDateTime) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarLike = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(calendarLike instanceof JSUndefined) && calendarLike != null) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSValue timeZoneLike = fields.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(timeZoneLike instanceof JSUndefined) && timeZoneLike != null) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoTime originalTime = plainDateTime.getIsoDateTime().time();
        String calendarId = plainDateTime.getCalendarId();
        boolean calendarSupportsEraFields = !"iso8601".equals(calendarId)
                && !"chinese".equals(calendarId)
                && !"dangi".equals(calendarId);

        JSValue dayFieldValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDayField = !(dayFieldValue instanceof JSUndefined) && dayFieldValue != null;
        Integer day = null;
        if (hasDayField) {
            day = TemporalUtils.toIntegerThrowOnInfinity(context, dayFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (day < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        JSValue hourFieldValue = fields.get(PropertyKey.fromString("hour"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasHourField = !(hourFieldValue instanceof JSUndefined) && hourFieldValue != null;
        int hour = originalTime.hour();
        if (hasHourField) {
            hour = TemporalUtils.toIntegerThrowOnInfinity(context, hourFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue microsecondFieldValue = fields.get(PropertyKey.fromString("microsecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMicrosecondField = !(microsecondFieldValue instanceof JSUndefined) && microsecondFieldValue != null;
        int microsecond = originalTime.microsecond();
        if (hasMicrosecondField) {
            microsecond = TemporalUtils.toIntegerThrowOnInfinity(context, microsecondFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue millisecondFieldValue = fields.get(PropertyKey.fromString("millisecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMillisecondField = !(millisecondFieldValue instanceof JSUndefined) && millisecondFieldValue != null;
        int millisecond = originalTime.millisecond();
        if (hasMillisecondField) {
            millisecond = TemporalUtils.toIntegerThrowOnInfinity(context, millisecondFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue minuteFieldValue = fields.get(PropertyKey.fromString("minute"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMinuteField = !(minuteFieldValue instanceof JSUndefined) && minuteFieldValue != null;
        int minute = originalTime.minute();
        if (hasMinuteField) {
            minute = TemporalUtils.toIntegerThrowOnInfinity(context, minuteFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthFieldValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthField = !(monthFieldValue instanceof JSUndefined) && monthFieldValue != null;
        Integer month = null;
        if (hasMonthField) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (month < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeFieldValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCodeField = !(monthCodeFieldValue instanceof JSUndefined) && monthCodeFieldValue != null;
        String monthCode = null;
        if (hasMonthCodeField) {
            monthCode = JSTypeConversions.toString(context, monthCodeFieldValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue nanosecondFieldValue = fields.get(PropertyKey.fromString("nanosecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasNanosecondField = !(nanosecondFieldValue instanceof JSUndefined) && nanosecondFieldValue != null;
        int nanosecond = originalTime.nanosecond();
        if (hasNanosecondField) {
            nanosecond = TemporalUtils.toIntegerThrowOnInfinity(context, nanosecondFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue secondFieldValue = fields.get(PropertyKey.fromString("second"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasSecondField = !(secondFieldValue instanceof JSUndefined) && secondFieldValue != null;
        int second = originalTime.second();
        if (hasSecondField) {
            second = TemporalUtils.toIntegerThrowOnInfinity(context, secondFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearFieldValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYearField = !(yearFieldValue instanceof JSUndefined) && yearFieldValue != null;
        Integer year = null;
        if (hasYearField) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearFieldValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        boolean hasEraField = false;
        boolean hasEraYearField = false;
        String era = null;
        Integer eraYear = null;
        if (calendarSupportsEraFields) {
            JSValue eraFieldValue = fields.get(PropertyKey.fromString("era"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraField = !(eraFieldValue instanceof JSUndefined) && eraFieldValue != null;
            if (hasEraField) {
                era = JSTypeConversions.toString(context, eraFieldValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            JSValue eraYearFieldValue = fields.get(PropertyKey.fromString("eraYear"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYearField = !(eraYearFieldValue instanceof JSUndefined) && eraYearFieldValue != null;
            if (hasEraYearField) {
                eraYear = TemporalUtils.toIntegerThrowOnInfinity(context, eraYearFieldValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }

        boolean hasDateField = hasDayField
                || hasMonthField
                || hasMonthCodeField
                || hasYearField
                || hasEraField
                || hasEraYearField;
        boolean hasTimeField = hasHourField
                || hasMinuteField
                || hasSecondField
                || hasMillisecondField
                || hasMicrosecondField
                || hasNanosecondField;
        if (!hasDateField && !hasTimeField) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        String overflow = TemporalUtils.getOverflowOption(context, optionsValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        IsoDate resultDate = plainDateTime.getIsoDateTime().date();
        if (hasDateField) {
            JSObject dateFieldsObject = new JSObject(context);
            if (hasDayField) {
                dateFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(day));
            }
            if (hasMonthField) {
                dateFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(month));
            }
            if (hasMonthCodeField) {
                dateFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(monthCode));
            }
            if (hasYearField) {
                dateFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(year));
            }
            if (hasEraField) {
                dateFieldsObject.set(PropertyKey.fromString("era"), new JSString(era));
            }
            if (hasEraYearField) {
                dateFieldsObject.set(PropertyKey.fromString("eraYear"), JSNumber.of(eraYear));
            }
            JSObject normalizedOptionsObject = new JSObject(context);
            normalizedOptionsObject.set(PropertyKey.fromString("overflow"), new JSString(overflow));
            JSValue mergedDateValue = TemporalPlainDatePrototype.with(
                    context,
                    TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()),
                    new JSValue[]{dateFieldsObject, normalizedOptionsObject});
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (mergedDateValue instanceof JSTemporalPlainDate mergedDate) {
                resultDate = mergedDate.getIsoDate();
            } else {
                context.throwTypeError("Temporal error: Date argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
        }

        IsoTime resultTime;
        if ("reject".equals(overflow)) {
            resultTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
            if (!resultTime.isValid()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        } else {
            resultTime = IsoTime.createNormalized(hour, minute, second, millisecond, microsecond, nanosecond);
        }

        if (!isValidPlainDateTimeRange(resultDate, resultTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(
                context,
                new IsoDateTime(resultDate, resultTime),
                plainDateTime.getCalendarId());
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "withCalendar");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] == null) {
            context.throwTypeError("Temporal error: Calendar is required.");
            return JSUndefined.INSTANCE;
        }
        String calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, args[0]);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context, plainDateTime.getIsoDateTime(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "withPlainTime");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime time = IsoTime.MIDNIGHT;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue timeArg = args[0];
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(
                    context,
                    timeArg,
                    JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            time = plainTime.getIsoTime();
        }
        if (!isValidPlainDateTimeRange(plainDateTime.getIsoDateTime().date(), time)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new IsoDateTime(plainDateTime.getIsoDateTime().date(), time), plainDateTime.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "year");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.year(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime plainDateTime = checkReceiver(context, thisArg, "yearOfWeek");
        if (plainDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDatePrototype.yearOfWeek(context, TemporalPlainDateConstructor.createPlainDate(context, plainDateTime.getIsoDateTime().date(), plainDateTime.getCalendarId()), args);
    }
}
