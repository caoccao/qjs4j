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
import java.time.ZoneOffset;

/**
 * Implementation of Temporal.ZonedDateTime prototype methods.
 */
public final class TemporalZonedDateTimePrototype {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final long DAY_NANOSECONDS = 86_400_000_000_000L;
    private static final long MAX_ROUNDING_INCREMENT = 1_000_000_000L;
    private static final BigInteger NS_PER_HOUR = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger NS_PER_MINUTE = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger NS_PER_MS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger NS_PER_SECOND = BILLION;
    private static final BigInteger NS_PER_US = BigInteger.valueOf(1_000L);
    private static final String TYPE_NAME = "Temporal.ZonedDateTime";

    private TemporalZonedDateTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "add");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, zonedDateTime, args, 1);
    }

    private static BigInteger addDurationToZonedDateTime(
            JSContext context,
            JSTemporalZonedDateTime zonedDateTime,
            TemporalDuration durationRecord,
            String overflow) {
        BigInteger intermediateEpochNanoseconds = zonedDateTime.getEpochNanoseconds();
        if (durationRecord.years() != 0
                || durationRecord.months() != 0
                || durationRecord.weeks() != 0
                || durationRecord.days() != 0) {
            IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
            IsoDate addedDate = addIsoDateWithOverflow(
                    context,
                    localDateTime.date(),
                    durationRecord.years(),
                    durationRecord.months(),
                    durationRecord.weeks(),
                    durationRecord.days(),
                    overflow);
            if (context.hasPendingException() || addedDate == null) {
                return null;
            }

            IsoDateTime addedDateTime = new IsoDateTime(addedDate, localDateTime.time());
            try {
                intermediateEpochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                        addedDateTime,
                        zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        BigInteger timeNanoseconds = durationTimeToNanoseconds(durationRecord);
        BigInteger resultEpochNanoseconds = intermediateEpochNanoseconds.add(timeNanoseconds);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(resultEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }
        return resultEpochNanoseconds;
    }

    private static IsoDate addIsoDateWithOverflow(
            JSContext context,
            IsoDate date,
            long years,
            long months,
            long weeks,
            long days,
            String overflow) {
        long monthIndex;
        long balancedYear;
        try {
            monthIndex = Math.addExact(date.month() - 1L, months);
            long balancedYearDelta = Math.floorDiv(monthIndex, 12L);
            balancedYear = Math.addExact(date.year(), years);
            balancedYear = Math.addExact(balancedYear, balancedYearDelta);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if (balancedYear < Integer.MIN_VALUE || balancedYear > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int balancedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        int balancedYearInt = (int) balancedYear;
        int maximumDay = IsoDate.daysInMonth(balancedYearInt, balancedMonth);
        int balancedDay = date.day();
        if ("reject".equals(overflow)) {
            if (balancedDay > maximumDay) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        } else {
            balancedDay = Math.min(balancedDay, maximumDay);
        }

        IsoDate intermediateDate = new IsoDate(balancedYearInt, balancedMonth, balancedDay);
        long resultEpochDay;
        try {
            long daysFromWeeks = Math.multiplyExact(weeks, 7L);
            long totalDays = Math.addExact(days, daysFromWeeks);
            resultEpochDay = Math.addExact(intermediateDate.toEpochDay(), totalDays);
        } catch (ArithmeticException arithmeticException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        try {
            return IsoDate.fromEpochDay(resultEpochDay);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
    }

    private static JSValue addOrSubtract(JSContext context, JSTemporalZonedDateTime zonedDateTime, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException() || temporalDuration == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = temporalDuration.getDuration();
        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        BigInteger epochNanoseconds = addDurationToZonedDateTime(context, zonedDateTime, durationRecord, overflow);
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return TemporalZonedDateTimeConstructor.createZonedDateTime(
                context,
                epochNanoseconds,
                zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    public static JSValue calendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "calendar");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(zonedDateTime.getCalendarId());
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "calendarId");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return new JSString(zonedDateTime.getCalendarId());
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

    private static String canonicalizeRoundSmallestUnit(String unitText) {
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

    private static String canonicalizeTimeZoneIdentifierForEquals(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return timeZoneId;
        }
        String normalizedTimeZoneId = timeZoneId.replace('\u2212', '-');
        if ("Z".equals(normalizedTimeZoneId)) {
            return "+00:00";
        }
        try {
            ZoneOffset zoneOffset = ZoneOffset.of(normalizedTimeZoneId);
            return TemporalTimeZone.formatOffset(zoneOffset.getTotalSeconds());
        } catch (DateTimeException ignored) {
            return normalizedTimeZoneId;
        }
    }

    private static String canonicalizeToStringSmallestUnit(String unitText) {
        return switch (unitText) {
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

    private static JSTemporalZonedDateTime checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalZonedDateTime zonedDateTime)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return zonedDateTime;
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "day");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.date().day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "dayOfWeek");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.date().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "dayOfYear");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.date().dayOfYear());
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "daysInMonth");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(IsoDate.daysInMonth(localDateTime.date().year(), localDateTime.date().month()));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "daysInWeek");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "daysInYear");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(IsoDate.daysInYear(localDateTime.date().year()));
    }

    private static BigInteger durationTimeToNanoseconds(TemporalDuration durationRecord) {
        return BigInteger.valueOf(durationRecord.hours()).multiply(NS_PER_HOUR)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(NS_PER_MINUTE))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(NS_PER_SECOND))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(NS_PER_MS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(BigInteger.valueOf(1_000L)))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
    }

    public static JSValue epochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "epochMilliseconds");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        BigInteger epochMilliseconds = floorDiv(zonedDateTime.getEpochNanoseconds(), NS_PER_MS);
        return JSNumber.of(epochMilliseconds.longValue());
    }

    public static JSValue epochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "epochNanoseconds");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return new JSBigInt(zonedDateTime.getEpochNanoseconds());
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "equals");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other;
        if (otherArg instanceof JSTemporalZonedDateTime otherZdt) {
            other = otherZdt;
        } else {
            other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        String receiverTimeZoneId = canonicalizeTimeZoneIdentifierForEquals(zonedDateTime.getTimeZoneId());
        String argumentTimeZoneId = canonicalizeTimeZoneIdentifierForEquals(other.getTimeZoneId());
        boolean equal = zonedDateTime.getEpochNanoseconds().equals(other.getEpochNanoseconds())
                && receiverTimeZoneId.equals(argumentTimeZoneId)
                && zonedDateTime.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "era");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "eraYear");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
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

    private static String formatZonedDateTime(JSTemporalZonedDateTime zonedDateTime) {
        String base = formatZonedDateTimeBase(zonedDateTime);
        if (!"iso8601".equals(zonedDateTime.getCalendarId())) {
            base += "[u-ca=" + zonedDateTime.getCalendarId() + "]";
        }
        return base;
    }

    private static String formatZonedDateTimeBase(JSTemporalZonedDateTime zonedDateTime) {
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        String offset = TemporalTimeZone.formatOffset(offsetSeconds);
        return localDateTime + offset + "[" + zonedDateTime.getTimeZoneId() + "]";
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
        if ("day".equals(smallestUnit) && Math.abs(roundingIncrement) > 100_000_000L) {
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

    private static String getDirectionOption(JSContext context, JSValue directionParam) {
        if (directionParam instanceof JSString directionString) {
            String direction = directionString.value();
            if ("next".equals(direction) || "previous".equals(direction)) {
                return direction;
            }
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return null;
        }

        if (directionParam instanceof JSObject directionObject) {
            JSValue directionValue = directionObject.get(PropertyKey.fromString("direction"));
            if (context.hasPendingException()) {
                return null;
            }
            if (directionValue instanceof JSUndefined || directionValue == null) {
                context.throwRangeError("Temporal error: Invalid direction: undefined");
                return null;
            }
            JSString directionString = JSTypeConversions.toString(context, directionValue);
            if (context.hasPendingException() || directionString == null) {
                return null;
            }
            String direction = directionString.value();
            if ("next".equals(direction) || "previous".equals(direction)) {
                return direction;
            }
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return null;
        }

        if (directionParam instanceof JSUndefined || directionParam == null) {
            context.throwTypeError("Temporal error: direction is required.");
            return null;
        }

        context.throwTypeError("Temporal error: direction must be a string or an object.");
        return null;
    }

    private static IsoDateTime getLocalDateTime(JSTemporalZonedDateTime zonedDateTime) {
        return TemporalTimeZone.epochNsToDateTimeInZone(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
    }

    private static Integer getOptionalIntegerWithField(JSContext context, JSObject fieldsObject, String fieldName) {
        JSValue fieldValue = fieldsObject.get(PropertyKey.fromString(fieldName));
        if (context.hasPendingException()) {
            return null;
        }
        if (fieldValue instanceof JSUndefined || fieldValue == null) {
            return null;
        }
        double numericValue = JSTypeConversions.toNumber(context, fieldValue).value();
        if (context.hasPendingException()) {
            return null;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return null;
        }
        return (int) numericValue;
    }

    private static String getOptionalOffsetStringWithField(JSContext context, JSObject fieldsObject) {
        JSValue offsetValue = fieldsObject.get(PropertyKey.fromString("offset"));
        if (context.hasPendingException()) {
            return null;
        }
        if (offsetValue instanceof JSUndefined || offsetValue == null) {
            return null;
        }
        if (offsetValue instanceof JSString offsetString) {
            return offsetString.value();
        }
        if (offsetValue instanceof JSObject) {
            return JSTypeConversions.toString(context, offsetValue).value();
        }
        context.throwTypeError("Temporal error: Offset must be string.");
        return null;
    }

    private static String getOptionalStringWithField(JSContext context, JSObject fieldsObject, String fieldName) {
        JSValue fieldValue = fieldsObject.get(PropertyKey.fromString(fieldName));
        if (context.hasPendingException()) {
            return null;
        }
        if (fieldValue instanceof JSUndefined || fieldValue == null) {
            return null;
        }
        return JSTypeConversions.toString(context, fieldValue).value();
    }

    private static String getRequiredSmallestUnitOption(JSContext context, JSObject optionsObject) {
        JSValue smallestUnitValue = optionsObject.get(PropertyKey.fromString("smallestUnit"));
        if (context.hasPendingException()) {
            return null;
        }
        if (smallestUnitValue instanceof JSUndefined || smallestUnitValue == null) {
            context.throwRangeError("Temporal error: smallestUnit is required.");
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
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        String smallestUnit = canonicalizeRoundSmallestUnit(smallestUnitText);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid unit: " + smallestUnitText);
            return null;
        }

        if (!isValidRoundingIncrementForSmallestUnit(smallestUnit, roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
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
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        long integerRoundingIncrement = (long) numericRoundingIncrement;
        if (integerRoundingIncrement < 1L || integerRoundingIncrement > MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
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
        JSString roundingModeText = JSTypeConversions.toString(context, roundingModeValue);
        if (context.hasPendingException() || roundingModeText == null) {
            return null;
        }
        return roundingModeText.value();
    }

    public static JSValue getTimeZoneTransition(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "getTimeZoneTransition");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue directionArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String direction = getDirectionOption(context, directionArg);
        if (context.hasPendingException() || direction == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger transitionEpochNs;
        if ("next".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getNextTransition(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        } else if ("previous".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getPreviousTransition(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        } else {
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return JSUndefined.INSTANCE;
        }

        if (transitionEpochNs == null) {
            return JSNull.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                transitionEpochNs, zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
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
        String nineDigitFraction = String.format("%03d%03d%03d",
                time.millisecond(),
                time.microsecond(),
                time.nanosecond());
        return nineDigitFraction.substring(0, digits);
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

    private static String getToStringOffsetOption(JSContext context, JSObject optionsObject) {
        String offsetOption = getDifferenceStringOption(context, optionsObject, "offset", "auto");
        if (context.hasPendingException() || offsetOption == null) {
            return null;
        }
        if (!"auto".equals(offsetOption) && !"never".equals(offsetOption)) {
            context.throwRangeError("Temporal error: Invalid offset option.");
            return null;
        }
        return offsetOption;
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
        String offsetOption = "auto";
        String roundingMode = "trunc";
        String smallestUnitText = null;
        String timeZoneNameOption = "auto";
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

            offsetOption = getToStringOffsetOption(context, optionsObject);
            if (context.hasPendingException() || offsetOption == null) {
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

            timeZoneNameOption = getToStringTimeZoneNameOption(context, optionsObject);
            if (context.hasPendingException() || timeZoneNameOption == null) {
                return null;
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

        if (smallestUnit != null
                && !"minute".equals(smallestUnit)
                && !"second".equals(smallestUnit)
                && !"millisecond".equals(smallestUnit)
                && !"microsecond".equals(smallestUnit)
                && !"nanosecond".equals(smallestUnit)) {
            context.throwRangeError("Temporal error: Invalid smallestUnit option.");
            return null;
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
                case "minute" -> NS_PER_MINUTE.longValue();
                case "second" -> NS_PER_SECOND.longValue();
                case "millisecond" -> NS_PER_MS.longValue();
                case "microsecond" -> NS_PER_US.longValue();
                case "nanosecond" -> 1L;
                default -> 1L;
            };
        } else if (autoFractionalSecondDigits) {
            fractionalSecondDigits = -1;
            roundingIncrementNanoseconds = 1L;
        } else {
            fractionalSecondDigits = fractionalSecondDigitsOption.digits();
            if (fractionalSecondDigits == 0) {
                roundingIncrementNanoseconds = NS_PER_SECOND.longValue();
            } else {
                roundingIncrementNanoseconds = (long) Math.pow(10, 9 - fractionalSecondDigits);
            }
        }

        return new ToStringSettings(
                calendarNameOption,
                offsetOption,
                timeZoneNameOption,
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

        int fractionalSecondDigits = toStringSettings.fractionalSecondDigits();
        if (fractionalSecondDigits == 0) {
            return hourMinuteSecond;
        }
        return hourMinuteSecond + "." + getToStringFractionalPart(time, fractionalSecondDigits);
    }

    private static String getToStringTimeZoneNameOption(JSContext context, JSObject optionsObject) {
        String timeZoneNameOption = getDifferenceStringOption(context, optionsObject, "timeZoneName", "auto");
        if (context.hasPendingException() || timeZoneNameOption == null) {
            return null;
        }
        if (!"auto".equals(timeZoneNameOption)
                && !"never".equals(timeZoneNameOption)
                && !"critical".equals(timeZoneNameOption)) {
            context.throwRangeError("Temporal error: Invalid timeZoneName option.");
            return null;
        }
        return timeZoneNameOption;
    }

    private static WithOptions getWithOptions(JSContext context, JSValue optionsValue) {
        if (optionsValue instanceof JSUndefined || optionsValue == null) {
            return new WithOptions("compatible", "prefer", "constrain");
        }
        if (!(optionsValue instanceof JSObject optionsObject)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }

        String disambiguation = getDifferenceStringOption(context, optionsObject, "disambiguation", "compatible");
        if (context.hasPendingException() || disambiguation == null) {
            return null;
        }

        String offsetOption = getDifferenceStringOption(context, optionsObject, "offset", "prefer");
        if (context.hasPendingException() || offsetOption == null) {
            return null;
        }

        String overflow = getDifferenceStringOption(context, optionsObject, "overflow", "constrain");
        if (context.hasPendingException() || overflow == null) {
            return null;
        }

        if (!"compatible".equals(disambiguation)
                && !"earlier".equals(disambiguation)
                && !"later".equals(disambiguation)
                && !"reject".equals(disambiguation)) {
            context.throwRangeError("Temporal error: Invalid disambiguation option.");
            return null;
        }
        if (!"prefer".equals(offsetOption)
                && !"use".equals(offsetOption)
                && !"ignore".equals(offsetOption)
                && !"reject".equals(offsetOption)) {
            context.throwRangeError("Temporal error: Invalid offset option.");
            return null;
        }
        if (!"constrain".equals(overflow) && !"reject".equals(overflow)) {
            context.throwRangeError("Temporal error: Invalid overflow option.");
            return null;
        }
        return new WithOptions(disambiguation, offsetOption, overflow);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "hour");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.time().hour());
    }

    public static JSValue hoursInDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "hoursInDay");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        IsoDate todayDate = localDateTime.date();
        IsoDate tomorrowDate;
        try {
            tomorrowDate = todayDate.addDays(1);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        BigInteger todayStartEpochNanoseconds;
        BigInteger tomorrowStartEpochNanoseconds;
        try {
            todayStartEpochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    new IsoDateTime(todayDate, IsoTime.MIDNIGHT),
                    zonedDateTime.getTimeZoneId());
            tomorrowStartEpochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    new IsoDateTime(tomorrowDate, IsoTime.MIDNIGHT),
                    zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (!TemporalInstantConstructor.isValidEpochNanoseconds(todayStartEpochNanoseconds)
                || !TemporalInstantConstructor.isValidEpochNanoseconds(tomorrowStartEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }

        BigInteger dayNanoseconds = tomorrowStartEpochNanoseconds.subtract(todayStartEpochNanoseconds);
        BigInteger hoursInDay = dayNanoseconds.divide(NS_PER_HOUR);
        return JSNumber.of(hoursInDay.longValue());
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "inLeapYear");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return IsoDate.isLeapYear(localDateTime.date().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
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
                maximumIncrement = 1_000L;
                break;
            default:
                return true;
        }
        if (roundingIncrement >= maximumIncrement) {
            return false;
        }
        return maximumIncrement % roundingIncrement == 0L;
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

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "microsecond");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.time().microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "millisecond");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.time().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "minute");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.time().minute());
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "month");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.date().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "monthCode");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return new JSString(TemporalUtils.monthCode(localDateTime.date().month()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "monthsInYear");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return JSNumber.of(12);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "nanosecond");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.time().nanosecond());
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

    public static JSValue offset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "offset");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return new JSString(TemporalTimeZone.formatOffset(offsetSeconds));
    }

    public static JSValue offsetNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "offsetNanoseconds");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
        return JSNumber.of((long) offsetSeconds * 1_000_000_000L);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "round");
        if (zonedDateTime == null) {
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

        BigInteger roundedEpochNanoseconds;
        if ("day".equals(roundSettings.smallestUnit())) {
            roundedEpochNanoseconds = roundZonedDateTimeToDay(context, zonedDateTime, roundSettings.roundingMode());
            if (context.hasPendingException() || roundedEpochNanoseconds == null) {
                return JSUndefined.INSTANCE;
            }
        } else {
            IsoDateTime localDateTime;
            try {
                localDateTime = getLocalDateTime(zonedDateTime);
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            long totalNanoseconds = localDateTime.time().totalNanoseconds();
            long unitNanoseconds = unitToNanoseconds(roundSettings.smallestUnit());
            long incrementNanoseconds = unitNanoseconds * roundSettings.roundingIncrement();
            long roundedNanoseconds = roundToIncrementAsIfPositive(
                    totalNanoseconds,
                    incrementNanoseconds,
                    roundSettings.roundingMode());

            int dayAdjust = 0;
            if (roundedNanoseconds == DAY_NANOSECONDS) {
                dayAdjust = 1;
                roundedNanoseconds = 0L;
            }

            IsoDate roundedDate = localDateTime.date();
            if (dayAdjust != 0) {
                try {
                    roundedDate = roundedDate.addDays(dayAdjust);
                } catch (DateTimeException dateTimeException) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
            }
            IsoTime roundedTime = IsoTime.fromNanoseconds(roundedNanoseconds);
            IsoDateTime roundedLocalDateTime = new IsoDateTime(roundedDate, roundedTime);
            try {
                roundedEpochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                        roundedLocalDateTime,
                        zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        if (!TemporalInstantConstructor.isValidEpochNanoseconds(roundedEpochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }

        return TemporalZonedDateTimeConstructor.createZonedDateTime(
                context,
                roundedEpochNanoseconds,
                zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    private static BigInteger roundBigIntegerToIncrementAsIfPositive(BigInteger quantity, BigInteger increment, String roundingMode) {
        if (increment.signum() == 0) {
            return quantity;
        }
        BigInteger[] floorQuotientAndRemainder = floorDivideAndRemainder(quantity, increment);
        BigInteger floorQuotient = floorQuotientAndRemainder[0];
        BigInteger lower = floorQuotient.multiply(increment);
        BigInteger remainder = floorQuotientAndRemainder[1];
        if (remainder.signum() == 0) {
            return lower;
        }
        BigInteger upper = lower.add(increment);

        return switch (roundingMode) {
            case "ceil", "expand" -> upper;
            case "floor", "trunc" -> lower;
            case "halfExpand", "halfCeil" -> remainder.shiftLeft(1).compareTo(increment) >= 0 ? upper : lower;
            case "halfTrunc", "halfFloor" -> remainder.shiftLeft(1).compareTo(increment) > 0 ? upper : lower;
            case "halfEven" -> {
                BigInteger doubledRemainder = remainder.shiftLeft(1);
                int halfComparison = doubledRemainder.compareTo(increment);
                if (halfComparison < 0) {
                    yield lower;
                } else if (halfComparison > 0) {
                    yield upper;
                } else if (floorQuotient.testBit(0)) {
                    yield upper;
                } else {
                    yield lower;
                }
            }
            default -> lower;
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

    private static BigInteger roundZonedDateTimeToDay(
            JSContext context,
            JSTemporalZonedDateTime zonedDateTime,
            String roundingMode) {
        IsoDateTime localDateTime;
        try {
            localDateTime = getLocalDateTime(zonedDateTime);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate dateStart = localDateTime.date();
        IsoDate dateEnd;
        try {
            dateEnd = dateStart.addDays(1);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        BigInteger startNanoseconds;
        BigInteger endNanoseconds;
        try {
            startNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    new IsoDateTime(dateStart, IsoTime.MIDNIGHT),
                    zonedDateTime.getTimeZoneId());
            endNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    new IsoDateTime(dateEnd, IsoTime.MIDNIGHT),
                    zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        if (!TemporalInstantConstructor.isValidEpochNanoseconds(startNanoseconds)
                || !TemporalInstantConstructor.isValidEpochNanoseconds(endNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }

        BigInteger dayLengthNanoseconds = endNanoseconds.subtract(startNanoseconds);
        BigInteger elapsedNanoseconds = zonedDateTime.getEpochNanoseconds().subtract(startNanoseconds);
        BigInteger roundedElapsedNanoseconds = roundBigIntegerToIncrementAsIfPositive(
                elapsedNanoseconds,
                dayLengthNanoseconds,
                roundingMode);
        return startNanoseconds.add(roundedElapsedNanoseconds);
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "second");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.time().second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "since");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!zonedDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        DifferenceSettings settings = getDifferenceSettings(context, true, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        String receiverTimeZoneId = zonedDateTime.getTimeZoneId();
        IsoDateTime thisLocalDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                zonedDateTime.getEpochNanoseconds(),
                receiverTimeZoneId);
        IsoDateTime otherLocalDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                other.getEpochNanoseconds(),
                receiverTimeZoneId);

        TemporalDuration durationRecord = TemporalDurationPrototype.differencePlainDateTime(
                context,
                thisLocalDateTime,
                otherLocalDateTime,
                settings.largestUnit(),
                settings.smallestUnit(),
                settings.roundingIncrement(),
                settings.roundingMode());
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

    public static JSValue startOfDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "startOfDay");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime;
        try {
            localDateTime = getLocalDateTime(zonedDateTime);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        IsoDateTime startOfDay = new IsoDateTime(localDateTime.date(), IsoTime.MIDNIGHT);
        BigInteger epochNs;
        try {
            epochNs = TemporalTimeZone.localDateTimeToEpochNs(startOfDay, zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
                zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "subtract");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, zonedDateTime, args, -1);
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

    public static JSValue timeZoneId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "timeZoneId");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return new JSString(zonedDateTime.getTimeZoneId());
    }

    public static JSValue toInstant(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "toInstant");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return TemporalInstantConstructor.createInstant(context, zonedDateTime.getEpochNanoseconds());
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "toJSON");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return new JSString(formatZonedDateTime(zonedDateTime));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "toLocaleString");
        if (zonedDateTime == null) {
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
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{zonedDateTime});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "toPlainDate");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return TemporalPlainDateConstructor.createPlainDate(context, localDateTime.date(), zonedDateTime.getCalendarId());
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "toPlainDateTime");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context, localDateTime, zonedDateTime.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "toPlainTime");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return TemporalPlainTimeConstructor.createPlainTime(context, localDateTime.time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "toString");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        ToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger roundedEpochNanoseconds = zonedDateTime.getEpochNanoseconds();
        if (toStringSettings.roundingIncrementNanoseconds() > 1L) {
            roundedEpochNanoseconds = roundBigIntegerToIncrementAsIfPositive(
                    roundedEpochNanoseconds,
                    BigInteger.valueOf(toStringSettings.roundingIncrementNanoseconds()),
                    toStringSettings.roundingMode());
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(roundedEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
        }

        IsoDateTime localDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                roundedEpochNanoseconds,
                zonedDateTime.getTimeZoneId());
        String dateString = localDateTime.date().toString();
        String timeString = getToStringTimeString(localDateTime.time(), toStringSettings);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(dateString).append('T').append(timeString);
        if ("auto".equals(toStringSettings.offsetOption())) {
            int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(
                    roundedEpochNanoseconds,
                    zonedDateTime.getTimeZoneId());
            stringBuilder.append(TemporalTimeZone.formatOffset(offsetSeconds));
        }
        if ("auto".equals(toStringSettings.timeZoneNameOption())) {
            stringBuilder.append('[').append(zonedDateTime.getTimeZoneId()).append(']');
        } else if ("critical".equals(toStringSettings.timeZoneNameOption())) {
            stringBuilder.append("[!").append(zonedDateTime.getTimeZoneId()).append(']');
        }

        String result = TemporalUtils.maybeAppendCalendar(
                stringBuilder.toString(),
                zonedDateTime.getCalendarId(),
                toStringSettings.calendarNameOption());
        return new JSString(result);
    }

    private static long unitToNanoseconds(String unit) {
        return switch (unit) {
            case "day" -> DAY_NANOSECONDS;
            case "hour" -> NS_PER_HOUR.longValue();
            case "minute" -> NS_PER_MINUTE.longValue();
            case "second" -> NS_PER_SECOND.longValue();
            case "millisecond" -> NS_PER_MS.longValue();
            case "microsecond" -> NS_PER_US.longValue();
            case "nanosecond" -> 1L;
            default -> 0L;
        };
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "until");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!zonedDateTime.getCalendarId().equals(other.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        DifferenceSettings settings = getDifferenceSettings(context, false, optionsArg);
        if (context.hasPendingException() || settings == null) {
            return JSUndefined.INSTANCE;
        }

        String receiverTimeZoneId = zonedDateTime.getTimeZoneId();
        IsoDateTime thisLocalDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                zonedDateTime.getEpochNanoseconds(),
                receiverTimeZoneId);
        IsoDateTime otherLocalDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                other.getEpochNanoseconds(),
                receiverTimeZoneId);

        TemporalDuration durationRecord = TemporalDurationPrototype.differencePlainDateTime(
                context,
                thisLocalDateTime,
                otherLocalDateTime,
                settings.largestUnit(),
                settings.smallestUnit(),
                settings.roundingIncrement(),
                settings.roundingMode());
        if (context.hasPendingException() || durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration resultRecord = TemporalDurationConstructor.normalizeFloat64RepresentableFields(durationRecord);
        if (!resultRecord.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(resultRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, resultRecord);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.ZonedDateTime.prototype.valueOf; use Temporal.ZonedDateTime.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "weekOfYear");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.date().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "with");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fieldsObject)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }
        if (fieldsObject instanceof JSTemporalPlainDate
                || fieldsObject instanceof JSTemporalPlainDateTime
                || fieldsObject instanceof JSTemporalPlainMonthDay
                || fieldsObject instanceof JSTemporalPlainYearMonth
                || fieldsObject instanceof JSTemporalPlainTime
                || fieldsObject instanceof JSTemporalZonedDateTime
                || fieldsObject instanceof JSTemporalInstant
                || fieldsObject instanceof JSTemporalDuration) {
            context.throwTypeError("Temporal error: Invalid ZonedDateTime-like object.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarValue = fieldsObject.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            context.throwTypeError("Temporal error: Invalid ZonedDateTime-like object.");
            return JSUndefined.INSTANCE;
        }

        JSValue timeZoneValue = fieldsObject.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(timeZoneValue instanceof JSUndefined) && timeZoneValue != null) {
            context.throwTypeError("Temporal error: Invalid ZonedDateTime-like object.");
            return JSUndefined.INSTANCE;
        }

        Integer dayField = getOptionalIntegerWithField(context, fieldsObject, "day");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer hourField = getOptionalIntegerWithField(context, fieldsObject, "hour");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer microsecondField = getOptionalIntegerWithField(context, fieldsObject, "microsecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer millisecondField = getOptionalIntegerWithField(context, fieldsObject, "millisecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer minuteField = getOptionalIntegerWithField(context, fieldsObject, "minute");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer monthField = getOptionalIntegerWithField(context, fieldsObject, "month");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String monthCodeField = getOptionalStringWithField(context, fieldsObject, "monthCode");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer nanosecondField = getOptionalIntegerWithField(context, fieldsObject, "nanosecond");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String offsetField = getOptionalOffsetStringWithField(context, fieldsObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer secondField = getOptionalIntegerWithField(context, fieldsObject, "second");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Integer yearField = getOptionalIntegerWithField(context, fieldsObject, "year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean hasAnyWithField = dayField != null
                || hourField != null
                || microsecondField != null
                || millisecondField != null
                || minuteField != null
                || monthField != null
                || monthCodeField != null
                || nanosecondField != null
                || offsetField != null
                || secondField != null
                || yearField != null;
        if (!hasAnyWithField) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        if ((dayField != null && dayField < 1) || (monthField != null && monthField < 1)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        WithOptions withOptions = getWithOptions(context, optionsValue);
        if (context.hasPendingException() || withOptions == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        int mergedYear = yearField != null ? yearField : localDateTime.date().year();
        int mergedDay = dayField != null ? dayField : localDateTime.date().day();
        int mergedHour = hourField != null ? hourField : localDateTime.time().hour();
        int mergedMinute = minuteField != null ? minuteField : localDateTime.time().minute();
        int mergedSecond = secondField != null ? secondField : localDateTime.time().second();
        int mergedMillisecond = millisecondField != null ? millisecondField : localDateTime.time().millisecond();
        int mergedMicrosecond = microsecondField != null ? microsecondField : localDateTime.time().microsecond();
        int mergedNanosecond = nanosecondField != null ? nanosecondField : localDateTime.time().nanosecond();

        JSObject mergedFieldsObject = new JSObject(context);
        mergedFieldsObject.set(PropertyKey.fromString("year"), JSNumber.of(mergedYear));
        if (monthField != null || monthCodeField == null) {
            int mergedMonth = monthField != null ? monthField : localDateTime.date().month();
            mergedFieldsObject.set(PropertyKey.fromString("month"), JSNumber.of(mergedMonth));
        }
        if (monthCodeField != null) {
            mergedFieldsObject.set(PropertyKey.fromString("monthCode"), new JSString(monthCodeField));
        }
        mergedFieldsObject.set(PropertyKey.fromString("day"), JSNumber.of(mergedDay));
        mergedFieldsObject.set(PropertyKey.fromString("hour"), JSNumber.of(mergedHour));
        mergedFieldsObject.set(PropertyKey.fromString("minute"), JSNumber.of(mergedMinute));
        mergedFieldsObject.set(PropertyKey.fromString("second"), JSNumber.of(mergedSecond));
        mergedFieldsObject.set(PropertyKey.fromString("millisecond"), JSNumber.of(mergedMillisecond));
        mergedFieldsObject.set(PropertyKey.fromString("microsecond"), JSNumber.of(mergedMicrosecond));
        mergedFieldsObject.set(PropertyKey.fromString("nanosecond"), JSNumber.of(mergedNanosecond));
        mergedFieldsObject.set(PropertyKey.fromString("timeZone"), new JSString(zonedDateTime.getTimeZoneId()));
        mergedFieldsObject.set(PropertyKey.fromString("calendar"), new JSString(zonedDateTime.getCalendarId()));
        if (offsetField != null) {
            mergedFieldsObject.set(PropertyKey.fromString("offset"), new JSString(offsetField));
        }

        JSObject normalizedOptionsObject = new JSObject(context);
        normalizedOptionsObject.set(PropertyKey.fromString("disambiguation"), new JSString(withOptions.disambiguation()));
        normalizedOptionsObject.set(PropertyKey.fromString("offset"), new JSString(withOptions.offset()));
        normalizedOptionsObject.set(PropertyKey.fromString("overflow"), new JSString(withOptions.overflow()));

        return TemporalZonedDateTimeConstructor.from(
                context,
                JSUndefined.INSTANCE,
                new JSValue[]{mergedFieldsObject, normalizedOptionsObject});
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "withCalendar");
        if (zonedDateTime == null) {
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
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "withPlainTime");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        BigInteger epochNanoseconds;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(
                    context,
                    args[0],
                    JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime resultLocalDateTime = new IsoDateTime(localDateTime.date(), plainTime.getIsoTime());
            epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    resultLocalDateTime,
                    zonedDateTime.getTimeZoneId());
        } else {
            IsoDateTime startOfDayDateTime = new IsoDateTime(localDateTime.date(), IsoTime.MIDNIGHT);
            epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    startOfDayDateTime,
                    zonedDateTime.getTimeZoneId());
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNanoseconds,
                zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
    }

    public static JSValue withTimeZone(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "withTimeZone");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue tzArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(tzArg instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = TemporalZonedDateTimeConstructor.normalizeTimeZoneIdentifier(context, timeZoneString.value());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        try {
            TemporalTimeZone.resolveTimeZone(timeZoneId);
        } catch (DateTimeException invalidTimeZoneException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                zonedDateTime.getEpochNanoseconds(), timeZoneId, zonedDateTime.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "year");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.date().year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "yearOfWeek");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.date().yearOfWeek());
    }

    private record DifferenceSettings(
            String largestUnit,
            String smallestUnit,
            long roundingIncrement,
            String roundingMode) {
    }

    private record FractionalSecondDigitsOption(
            boolean auto,
            int digits) {
    }

    private record RoundSettings(
            String smallestUnit,
            long roundingIncrement,
            String roundingMode) {
    }

    private record ToStringSettings(
            String calendarNameOption,
            String offsetOption,
            String timeZoneNameOption,
            String smallestUnit,
            String roundingMode,
            boolean autoFractionalSecondDigits,
            int fractionalSecondDigits,
            long roundingIncrementNanoseconds) {
    }

    private record WithOptions(
            String disambiguation,
            String offset,
            String overflow) {
    }
}
