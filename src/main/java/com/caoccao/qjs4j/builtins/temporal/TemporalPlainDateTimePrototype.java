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
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final long MAX_ROUNDING_INCREMENT = 1_000_000_000L;
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final String TYPE_NAME = "Temporal.PlainDateTime";

    private TemporalPlainDateTimePrototype() {
    }

    // ========== Getters ==========

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "add");
        if (pdt == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, pdt, args, 1);
    }

    private static IsoDate addDurationToDate(
            JSContext context,
            IsoDate date,
            long years,
            long months,
            long weeks,
            long days,
            String overflow) {
        long totalDays;
        try {
            totalDays = Math.addExact(days, Math.multiplyExact(weeks, 7L));
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }

        long monthIndex = Math.addExact(date.month() - 1L, months);
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long balancedYear = Math.addExact(date.year(), years);
        balancedYear = Math.addExact(balancedYear, yearDelta);
        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int balancedYearInt = (int) balancedYear;
        int maxDay = IsoDate.daysInMonth(balancedYearInt, balancedMonth);
        int regulatedDay = date.day();
        if ("reject".equals(overflow)) {
            if (regulatedDay > maxDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            regulatedDay = Math.min(regulatedDay, maxDay);
        }

        IsoDate intermediateDate = new IsoDate(balancedYearInt, balancedMonth, regulatedDay);
        long resultEpochDay;
        try {
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (resultEpochDay < MIN_SUPPORTED_EPOCH_DAY || resultEpochDay > MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate resultDate = IsoDate.fromEpochDay(resultEpochDay);
        if (!IsoDate.isValidIsoDate(resultDate.year(), resultDate.month(), resultDate.day())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return resultDate;
    }

    private static TimeAddResult addDurationToTime(
            JSContext context,
            IsoTime time,
            TemporalDurationRecord durationRecord) {
        BigInteger durationTimeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));

        BigInteger totalNanoseconds = BigInteger.valueOf(time.totalNanoseconds()).add(durationTimeNanoseconds);
        BigInteger[] dayAndRemainder = totalNanoseconds.divideAndRemainder(DAY_NANOSECONDS);
        BigInteger dayCarryBigInteger = dayAndRemainder[0];
        BigInteger remainder = dayAndRemainder[1];
        if (remainder.signum() < 0) {
            remainder = remainder.add(DAY_NANOSECONDS);
            dayCarryBigInteger = dayCarryBigInteger.subtract(BigInteger.ONE);
        }

        long dayCarry;
        try {
            dayCarry = dayCarryBigInteger.longValueExact();
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        long normalizedTimeNanoseconds = remainder.longValue();
        IsoTime normalizedTime = IsoTime.fromNanoseconds(normalizedTimeNanoseconds);
        return new TimeAddResult(normalizedTime, dayCarry);
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainDateTime pdt, JSValue[] args, int sign) {
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

        TemporalDurationRecord durationRecord = temporalDuration.getRecord();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        TimeAddResult timeResult = addDurationToTime(context, pdt.getIsoDateTime().time(), durationRecord);
        if (context.hasPendingException() || timeResult == null) {
            return JSUndefined.INSTANCE;
        }

        long adjustedDays;
        try {
            adjustedDays = Math.addExact(durationRecord.days(), timeResult.dayCarry());
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        IsoDate newDate = addDurationToDate(
                context,
                pdt.getIsoDateTime().date(),
                durationRecord.years(),
                durationRecord.months(),
                durationRecord.weeks(),
                adjustedDays,
                overflow);
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
                pdt.getCalendarId());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "calendarId");
        if (pdt == null) return JSUndefined.INSTANCE;
        return new JSString(pdt.getCalendarId());
    }

    private static String canonicalizeDifferenceUnit(String unitText, boolean allowAuto) {
        return switch (unitText) {
            case "auto" -> allowAuto ? "auto" : null;
            case "year", "years" -> "year";
            case "month", "months" -> "month";
            case "week", "weeks" -> "week";
            case "day", "days" -> "day";
            case "hour", "hours" -> "hour";
            case "minute", "minutes" -> "minute";
            case "second", "seconds" -> "second";
            case "millisecond", "milliseconds" -> "millisecond";
            case "microsecond", "microseconds" -> "microsecond";
            case "nanosecond", "nanoseconds" -> "nanosecond";
            default -> null;
        };
    }

    private static String canonicalizeSmallestUnit(String unitText) {
        return switch (unitText) {
            case "day", "days" -> "day";
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

    private static JSTemporalPlainDateTime checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainDateTime pdt)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return pdt;
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "day");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "dayOfWeek");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "dayOfYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().dayOfYear());
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "daysInMonth");
        if (pdt == null) return JSUndefined.INSTANCE;
        IsoDate d = pdt.getIsoDateTime().date();
        return JSNumber.of(IsoDate.daysInMonth(d.year(), d.month()));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "daysInWeek");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "daysInYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(IsoDate.daysInYear(pdt.getIsoDateTime().date().year()));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "equals");
        if (pdt == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        boolean equal = IsoDateTime.compareIsoDateTime(pdt.getIsoDateTime(), other.getIsoDateTime()) == 0
                && pdt.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "era");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "eraYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    private static long getDifferenceRoundingIncrementOption(JSContext context, JSObject optionsObject) {
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
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        long integerIncrement = (long) roundingIncrement;
        if (integerIncrement < 1L || integerIncrement > MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        return integerIncrement;
    }

    private static DifferenceSettings getDifferenceSettings(
            JSContext context,
            boolean sinceOperation,
            JSValue optionsArg) {
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
            largestUnitText = getDifferenceStringOption(context, optionsObject, "largestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }

            roundingIncrement = getDifferenceRoundingIncrementOption(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }

            roundingMode = getDifferenceStringOption(context, optionsObject, "roundingMode", "trunc");
            if (context.hasPendingException()) {
                return null;
            }

            smallestUnitText = getDifferenceStringOption(context, optionsObject, "smallestUnit", null);
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
            largestUnit = largerOfTwoTemporalUnits("day", smallestUnit);
        }
        if (!largestUnit.equals(largerOfTwoTemporalUnits(largestUnit, smallestUnit))) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        if (!isValidDifferenceRoundingIncrement(smallestUnit, roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        if (sinceOperation) {
            roundingMode = negateRoundingMode(roundingMode);
        }
        return new DifferenceSettings(largestUnit, smallestUnit, roundingIncrement, roundingMode);
    }

    private static String getDifferenceStringOption(JSContext context, JSObject optionsObject, String optionName, String defaultValue) {
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

        return new RoundSettings(smallestUnit, roundingIncrement, roundingMode);
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
        String disambiguation = getDifferenceStringOption(context, optionsObject, "disambiguation", "compatible");
        if (context.hasPendingException() || disambiguation == null) {
            return null;
        }
        if (!"compatible".equals(disambiguation)
                && !"earlier".equals(disambiguation)
                && !"later".equals(disambiguation)
                && !"reject".equals(disambiguation)) {
            context.throwRangeError("Temporal error: Invalid disambiguation option.");
            return null;
        }
        return disambiguation;
    }

    private static String getToStringCalendarNameOption(JSContext context, JSObject optionsObject) {
        String calendarNameOption = getDifferenceStringOption(context, optionsObject, "calendarName", "auto");
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

        String stringValue = JSTypeConversions.toString(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        if ("auto".equals(stringValue)) {
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

        String calendarNameOption = "auto";
        FractionalSecondDigitsOption fractionalSecondDigitsOption = new FractionalSecondDigitsOption(true, -1);
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

            roundingMode = getDifferenceStringOption(context, optionsObject, "roundingMode", "trunc");
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

        return new ToStringSettings(
                calendarNameOption,
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
            int end = fullFraction.length();
            while (end > 0 && fullFraction.charAt(end - 1) == '0') {
                end--;
            }
            if (end == 0) {
                return hourMinuteSecond;
            }
            return hourMinuteSecond + "." + fullFraction.substring(0, end);
        }

        int fractionDigits = toStringSettings.fractionalSecondDigits();
        if (fractionDigits == 0) {
            return hourMinuteSecond;
        }
        return hourMinuteSecond + "." + getToStringFractionalPart(time, fractionDigits);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "hour");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().hour());
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "inLeapYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return IsoDate.isLeapYear(pdt.getIsoDateTime().date().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    private static boolean isValidDifferenceRoundingIncrement(String smallestUnit, long roundingIncrement) {
        long maximumIncrement;
        switch (smallestUnit) {
            case "hour":
                maximumIncrement = 24L;
                break;
            case "minute":
            case "second":
                maximumIncrement = 60L;
                break;
            case "millisecond":
            case "microsecond":
            case "nanosecond":
                maximumIncrement = 1000L;
                break;
            default:
                return true;
        }
        if (roundingIncrement >= maximumIncrement) {
            return false;
        }
        return maximumIncrement % roundingIncrement == 0L;
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
        int leftRank = temporalUnitRank(leftUnit);
        int rightRank = temporalUnitRank(rightUnit);
        if (leftRank > rightRank) {
            return rightUnit;
        }
        return leftUnit;
    }

    // ========== Methods ==========

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "microsecond");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "millisecond");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "minute");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().minute());
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "month");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "monthCode");
        if (pdt == null) return JSUndefined.INSTANCE;
        return new JSString(TemporalUtils.monthCode(pdt.getIsoDateTime().date().month()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "monthsInYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(12);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "nanosecond");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().nanosecond());
    }

    private static String negateRoundingMode(String roundingMode) {
        return switch (roundingMode) {
            case "ceil" -> "floor";
            case "floor" -> "ceil";
            case "halfCeil" -> "halfFloor";
            case "halfFloor" -> "halfCeil";
            default -> roundingMode;
        };
    }

    private static MonthCodeInfo parseMonthCodeForPlainDateTimeWith(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() < 3 || monthCode.length() > 4) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (monthCode.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int month = Integer.parseInt(monthCode.substring(1, 3));
        boolean leapMonth = false;
        if (monthCode.length() == 4) {
            if (monthCode.charAt(3) != 'L') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            leapMonth = true;
        }
        if (month < 1 || month > 12) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new MonthCodeInfo(month, leapMonth);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "round");
        if (pdt == null) {
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

        long totalNanoseconds = pdt.getIsoDateTime().time().totalNanoseconds();
        long unitNanoseconds = unitToNanoseconds(roundSettings.smallestUnit());
        long incrementNanoseconds = unitNanoseconds * roundSettings.roundingIncrement();
        long roundedNanoseconds = roundToIncrementAsIfPositive(
                totalNanoseconds,
                incrementNanoseconds,
                roundSettings.roundingMode());

        int dayAdjust = 0;
        if (roundedNanoseconds == DAY_NANOSECONDS.longValue()) {
            dayAdjust = 1;
            roundedNanoseconds = 0L;
        }

        IsoDate adjustedDate = pdt.getIsoDateTime().date();
        if (dayAdjust != 0) {
            adjustedDate = adjustedDate.addDays(dayAdjust);
        }
        IsoTime adjustedTime = IsoTime.fromNanoseconds(roundedNanoseconds);
        if (!isValidPlainDateTimeRange(adjustedDate, adjustedTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(
                context,
                new IsoDateTime(adjustedDate, adjustedTime),
                pdt.getCalendarId());
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
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "second");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().time().second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "since");
        if (pdt == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!pdt.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        DifferenceSettings settings = getDifferenceSettings(context, true, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord durationRecord = TemporalDurationPrototype.differencePlainDateTime(
                context,
                pdt.getIsoDateTime(),
                other.getIsoDateTime(),
                settings.largestUnit(),
                settings.smallestUnit(),
                settings.roundingIncrement(),
                settings.roundingMode());
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord resultRecord =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(durationRecord.negated());
        if (!resultRecord.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, resultRecord);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "subtract");
        if (pdt == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, pdt, args, -1);
    }

    private static int temporalUnitRank(String unit) {
        return switch (unit) {
            case "year" -> 0;
            case "month" -> 1;
            case "week" -> 2;
            case "day" -> 3;
            case "hour" -> 4;
            case "minute" -> 5;
            case "second" -> 6;
            case "millisecond" -> 7;
            case "microsecond" -> 8;
            case "nanosecond" -> 9;
            default -> 10;
        };
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toJSON");
        if (pdt == null) {
            return JSUndefined.INSTANCE;
        }
        ToStringSettings toStringSettings = new ToStringSettings("auto", null, "trunc", true, -1, 1L);
        String formattedString = toTemporalPlainDateTimeString(context, pdt, toStringSettings);
        if (context.hasPendingException() || formattedString == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(formattedString);
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toLocaleString");
        if (pdt == null) {
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
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{pdt});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toPlainDate");
        if (pdt == null) return JSUndefined.INSTANCE;
        return TemporalPlainDateConstructor.createPlainDate(context, pdt.getIsoDateTime().date(), pdt.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toPlainTime");
        if (pdt == null) return JSUndefined.INSTANCE;
        return TemporalPlainTimeConstructor.createPlainTime(context, pdt.getIsoDateTime().time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "toString");
        if (pdt == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        ToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        String formattedString = toTemporalPlainDateTimeString(context, pdt, toStringSettings);
        if (context.hasPendingException() || formattedString == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(formattedString);
    }

    private static String toTemporalPlainDateTimeString(
            JSContext context,
            JSTemporalPlainDateTime plainDateTime,
            ToStringSettings toStringSettings) {
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
        IsoTime roundedTime = IsoTime.fromNanoseconds(roundedNanoseconds);
        if (!isValidPlainDateTimeRange(roundedDate, roundedTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String dateString = TemporalUtils.formatIsoDate(roundedDate.year(), roundedDate.month(), roundedDate.day());
        String timeString = getToStringTimeString(roundedTime, toStringSettings);
        String dateTimeString = dateString + "T" + timeString;
        return TemporalUtils.maybeAppendCalendar(dateTimeString, plainDateTime.getCalendarId(), toStringSettings.calendarNameOption());
    }

    private static String toTemporalTimeZoneIdentifier(JSContext context, JSValue timeZoneLike) {
        if (!(timeZoneLike instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
        }
        return TemporalDurationConstructor.parseTimeZoneIdentifierString(context, timeZoneString.value());
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
        } catch (DateTimeException e) {
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
        return switch (unit) {
            case "day" -> 86_400_000_000_000L;
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
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "until");
        if (pdt == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainDateTime other = TemporalPlainDateTimeConstructor.toTemporalDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!pdt.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        DifferenceSettings settings = getDifferenceSettings(context, false, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord durationRecord = TemporalDurationPrototype.differencePlainDateTime(
                context,
                pdt.getIsoDateTime(),
                other.getIsoDateTime(),
                settings.largestUnit(),
                settings.smallestUnit(),
                settings.roundingIncrement(),
                settings.roundingMode());
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord resultRecord =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(durationRecord);
        if (!resultRecord.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, resultRecord);
    }

    // ========== Internal helpers ==========

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainDateTime.prototype.valueOf; use Temporal.PlainDateTime.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "weekOfYear");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "with");
        if (pdt == null) {
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

        IsoDate origDate = pdt.getIsoDateTime().date();
        IsoTime origTime = pdt.getIsoDateTime().time();

        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;
        int day = origDate.day();
        if (hasDay) {
            day = TemporalUtils.toIntegerThrowOnInfinity(context, dayValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue hourValue = fields.get(PropertyKey.fromString("hour"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasHour = !(hourValue instanceof JSUndefined) && hourValue != null;
        int hour = origTime.hour();
        if (hasHour) {
            hour = TemporalUtils.toIntegerThrowOnInfinity(context, hourValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue microsecondValue = fields.get(PropertyKey.fromString("microsecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMicrosecond = !(microsecondValue instanceof JSUndefined) && microsecondValue != null;
        int microsecond = origTime.microsecond();
        if (hasMicrosecond) {
            microsecond = TemporalUtils.toIntegerThrowOnInfinity(context, microsecondValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue millisecondValue = fields.get(PropertyKey.fromString("millisecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMillisecond = !(millisecondValue instanceof JSUndefined) && millisecondValue != null;
        int millisecond = origTime.millisecond();
        if (hasMillisecond) {
            millisecond = TemporalUtils.toIntegerThrowOnInfinity(context, millisecondValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue minuteValue = fields.get(PropertyKey.fromString("minute"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMinute = !(minuteValue instanceof JSUndefined) && minuteValue != null;
        int minute = origTime.minute();
        if (hasMinute) {
            minute = TemporalUtils.toIntegerThrowOnInfinity(context, minuteValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        int month = origDate.month();
        if (hasMonth) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = !(monthCodeValue instanceof JSUndefined) && monthCodeValue != null;
        String monthCode = null;
        if (hasMonthCode) {
            monthCode = JSTypeConversions.toString(context, monthCodeValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue nanosecondValue = fields.get(PropertyKey.fromString("nanosecond"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasNanosecond = !(nanosecondValue instanceof JSUndefined) && nanosecondValue != null;
        int nanosecond = origTime.nanosecond();
        if (hasNanosecond) {
            nanosecond = TemporalUtils.toIntegerThrowOnInfinity(context, nanosecondValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue secondValue = fields.get(PropertyKey.fromString("second"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasSecond = !(secondValue instanceof JSUndefined) && secondValue != null;
        int second = origTime.second();
        if (hasSecond) {
            second = TemporalUtils.toIntegerThrowOnInfinity(context, secondValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = origDate.year();
        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        boolean hasAnyField = hasDay
                || hasHour
                || hasMicrosecond
                || hasMillisecond
                || hasMinute
                || hasMonth
                || hasMonthCode
                || hasNanosecond
                || hasSecond
                || hasYear;
        if (!hasAnyField) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        if (day < 1 || month < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        MonthCodeInfo parsedMonthCode = null;
        if (hasMonthCode) {
            parsedMonthCode = parseMonthCodeForPlainDateTimeWith(context, monthCode);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (parsedMonthCode != null) {
            if (parsedMonthCode.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != parsedMonthCode.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            month = parsedMonthCode.month();
        }

        IsoDate resultDate;
        IsoTime resultTime;
        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            resultDate = new IsoDate(year, month, day);
            resultTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        } else {
            resultDate = TemporalUtils.constrainIsoDate(year, month, day);
            resultTime = TemporalUtils.constrainIsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        }

        if (!isValidPlainDateTimeRange(resultDate, resultTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainDateTimeConstructor.createPlainDateTime(
                context,
                new IsoDateTime(resultDate, resultTime),
                pdt.getCalendarId());
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "withCalendar");
        if (pdt == null) {
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
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context, pdt.getIsoDateTime(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "withPlainTime");
        if (pdt == null) {
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
        if (!isValidPlainDateTimeRange(pdt.getIsoDateTime().date(), time)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context,
                new IsoDateTime(pdt.getIsoDateTime().date(), time), pdt.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "year");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainDateTime pdt = checkReceiver(context, thisArg, "yearOfWeek");
        if (pdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(pdt.getIsoDateTime().date().yearOfWeek());
    }

    private record DifferenceSettings(String largestUnit, String smallestUnit, long roundingIncrement,
                                      String roundingMode) {
    }

    private record FractionalSecondDigitsOption(boolean auto, int digits) {
    }

    private record MonthCodeInfo(int month, boolean leapMonth) {
    }

    private record RoundSettings(String smallestUnit, long roundingIncrement, String roundingMode) {
    }

    private record TimeAddResult(IsoTime time, long dayCarry) {
    }

    private record ToStringSettings(
            String calendarNameOption,
            String smallestUnit,
            String roundingMode,
            boolean autoFractionalSecondDigits,
            int fractionalSecondDigits,
            long roundingIncrementNanoseconds) {
    }
}
