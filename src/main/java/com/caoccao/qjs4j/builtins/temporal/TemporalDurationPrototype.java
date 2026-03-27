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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Implementation of Temporal.Duration prototype methods.
 */
public final class TemporalDurationPrototype {
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS =
            BigInteger.valueOf(9_007_199_254_740_992L).multiply(SECOND_NANOSECONDS).subtract(BigInteger.ONE);
    private static final BigInteger MAX_FLOAT64_MILLISECONDS_COMPONENT =
            new BigInteger("9007199254740991487");
    private static final BigInteger MAX_FLOAT64_MICROSECONDS_COMPONENT =
            new BigInteger("9007199254740991475711");
    private static final BigInteger MAX_FLOAT64_NANOSECONDS_COMPONENT =
            new BigInteger("9007199254740991463129087");
    private static final String TYPE_NAME = "Temporal.Duration";
    private static final BigInteger WEEK_NANOSECONDS = DAY_NANOSECONDS.multiply(BigInteger.valueOf(7L));

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

    private static LocalDateTime addCalendarUnits(LocalDateTime dateTime, String calendarUnit, long amount) {
        if ("year".equals(calendarUnit)) {
            return dateTime.plusYears(amount);
        } else {
            return dateTime.plusMonths(amount);
        }
    }

    private static LocalDateTime addDurationToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            TemporalDurationRecord durationRecord) {
        LocalDateTime dateBalancedDateTime = startDateTime
                .plusYears(durationRecord.years())
                .plusMonths(durationRecord.months())
                .plusWeeks(durationRecord.weeks())
                .plusDays(durationRecord.days());
        BigInteger timeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
        return addNanosecondsToDateTime(context, dateBalancedDateTime, timeNanoseconds);
    }

    private static LocalDateTime addFixedUnits(LocalDateTime dateTime, String unit, long amount) {
        if ("week".equals(unit)) {
            return dateTime.plusWeeks(amount);
        } else {
            return dateTime.plusDays(amount);
        }
    }

    private static LocalDateTime addNanosecondsToDateTime(
            JSContext context,
            LocalDateTime startDateTime,
            BigInteger nanoseconds) {
        BigInteger[] dayQuotientAndRemainder = nanoseconds.divideAndRemainder(DAY_NANOSECONDS);
        long dayAdjustment;
        try {
            dayAdjustment = dayQuotientAndRemainder[0].longValueExact();
        } catch (ArithmeticException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        BigInteger nanosecondRemainder = dayQuotientAndRemainder[1];
        if (nanosecondRemainder.signum() < 0) {
            dayAdjustment--;
            nanosecondRemainder = nanosecondRemainder.add(DAY_NANOSECONDS);
        }
        long nanosecondAdjustment = nanosecondRemainder.longValueExact();
        try {
            return startDateTime.plusDays(dayAdjustment).plusNanos(nanosecondAdjustment);
        } catch (DateTimeException e) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
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

        if (hasCalendarUnits(r1) || hasCalendarUnits(r2)) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        BigInteger leftNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(r1);
        BigInteger rightNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(r2);
        BigInteger signedRightNanoseconds;
        if (sign < 0) {
            signedRightNanoseconds = rightNanoseconds.negate();
        } else {
            signedRightNanoseconds = rightNanoseconds;
        }
        BigInteger totalNanoseconds = leftNanoseconds.add(signedRightNanoseconds);
        if (totalNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        String receiverLargestUnit = largestUnitOfDuration(r1);
        String otherLargestUnit = largestUnitOfDuration(r2);
        String largestUnit = TemporalDurationConstructor.largerTemporalUnit(receiverLargestUnit, otherLargestUnit);
        TemporalDurationRecord balanced = balanceTimeDuration(totalNanoseconds, largestUnit);
        TemporalDurationRecord normalized = TemporalDurationConstructor.normalizeFloat64RepresentableFields(balanced);
        if (!TemporalDurationConstructor.isDurationRecordTimeRangeValid(normalized)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalDurationConstructor.createDuration(context, normalized);
    }

    static TemporalDurationRecord balanceTimeDuration(long totalNs, String largestUnit) {
        return balanceTimeDuration(BigInteger.valueOf(totalNs), largestUnit);
    }

    static TemporalDurationRecord balanceTimeDuration(BigInteger totalNs, String largestUnit) {
        boolean negative = totalNs.signum() < 0;
        if (negative) {
            totalNs = totalNs.negate();
        }

        long days = 0, hours = 0, minutes = 0, seconds = 0;
        long milliseconds = 0, microseconds = 0, nanoseconds = 0;

        switch (largestUnit) {
            case "day" -> {
                BigInteger[] dayDivision = totalNs.divideAndRemainder(DAY_NANOSECONDS);
                days = dayDivision[0].longValue();
                BigInteger remainingNanoseconds = dayDivision[1];
                BigInteger[] hourDivision = remainingNanoseconds.divideAndRemainder(HOUR_NANOSECONDS);
                hours = hourDivision[0].longValue();
                remainingNanoseconds = hourDivision[1];
                BigInteger[] minuteDivision = remainingNanoseconds.divideAndRemainder(MINUTE_NANOSECONDS);
                minutes = minuteDivision[0].longValue();
                remainingNanoseconds = minuteDivision[1];
                BigInteger[] secondDivision = remainingNanoseconds.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "hour" -> {
                BigInteger[] hourDivision = totalNs.divideAndRemainder(HOUR_NANOSECONDS);
                hours = hourDivision[0].longValue();
                BigInteger remainingNanoseconds = hourDivision[1];
                BigInteger[] minuteDivision = remainingNanoseconds.divideAndRemainder(MINUTE_NANOSECONDS);
                minutes = minuteDivision[0].longValue();
                remainingNanoseconds = minuteDivision[1];
                BigInteger[] secondDivision = remainingNanoseconds.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "minute" -> {
                BigInteger[] minuteDivision = totalNs.divideAndRemainder(MINUTE_NANOSECONDS);
                minutes = minuteDivision[0].longValue();
                BigInteger remainingNanoseconds = minuteDivision[1];
                BigInteger[] secondDivision = remainingNanoseconds.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "second" -> {
                BigInteger[] secondDivision = totalNs.divideAndRemainder(SECOND_NANOSECONDS);
                seconds = secondDivision[0].longValue();
                BigInteger remainingNanoseconds = secondDivision[1];
                BigInteger[] millisecondDivision = remainingNanoseconds.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "millisecond" -> {
                BigInteger[] millisecondDivision = totalNs.divideAndRemainder(MILLISECOND_NANOSECONDS);
                milliseconds = millisecondDivision[0].longValue();
                BigInteger remainingNanoseconds = millisecondDivision[1];
                BigInteger[] microsecondDivision = remainingNanoseconds.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            case "microsecond" -> {
                BigInteger[] microsecondDivision = totalNs.divideAndRemainder(MICROSECOND_NANOSECONDS);
                microseconds = microsecondDivision[0].longValue();
                nanoseconds = microsecondDivision[1].longValue();
            }
            default -> {
                nanoseconds = totalNs.longValue();
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

    private static TemporalDurationRecord buildBalancedDurationFromDateTimes(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String largestUnit,
            String smallestUnit) {
        if ("hour".equals(largestUnit)
                || "minute".equals(largestUnit)
                || "second".equals(largestUnit)
                || "millisecond".equals(largestUnit)
                || "microsecond".equals(largestUnit)
                || "nanosecond".equals(largestUnit)) {
            BigInteger totalNanoseconds = nanosecondsBetween(startDateTime, endDateTime);
            TemporalDurationRecord timeDuration = balanceTimeDuration(totalNanoseconds, largestUnit);
            return new TemporalDurationRecord(
                    0,
                    0,
                    0,
                    timeDuration.days(),
                    timeDuration.hours(),
                    timeDuration.minutes(),
                    timeDuration.seconds(),
                    timeDuration.milliseconds(),
                    timeDuration.microseconds(),
                    timeDuration.nanoseconds());
        }

        long years = 0;
        long months = 0;
        long weeks = 0;
        long days = 0;
        LocalDateTime cursorDateTime = startDateTime;
        int smallestUnitRank = temporalDurationUnitRank(smallestUnit);

        if ("year".equals(largestUnit)) {
            UnitStepResult yearStepResult = moveByWholeCalendarUnits(cursorDateTime, endDateTime, "year");
            years = yearStepResult.count();
            cursorDateTime = yearStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("year")) {
                return new TemporalDurationRecord(years, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if ("year".equals(largestUnit) || "month".equals(largestUnit)) {
            UnitStepResult monthStepResult = moveByWholeCalendarUnits(cursorDateTime, endDateTime, "month");
            months = monthStepResult.count();
            cursorDateTime = monthStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("month")) {
                return new TemporalDurationRecord(years, months, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        boolean shouldBalanceWeeks = "week".equals(largestUnit) || "week".equals(smallestUnit);
        if (shouldBalanceWeeks) {
            UnitStepResult weekStepResult = moveByWholeFixedUnits(cursorDateTime, endDateTime, "week");
            weeks = weekStepResult.count();
            cursorDateTime = weekStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("week")) {
                return new TemporalDurationRecord(years, months, weeks, 0, 0, 0, 0, 0, 0, 0);
            }
        }

        if (smallestUnitRank >= temporalDurationUnitRank("day")) {
            UnitStepResult dayStepResult = moveByWholeFixedUnits(cursorDateTime, endDateTime, "day");
            days = dayStepResult.count();
            cursorDateTime = dayStepResult.boundaryDateTime();
            if (smallestUnitRank == temporalDurationUnitRank("day")) {
                return new TemporalDurationRecord(years, months, weeks, days, 0, 0, 0, 0, 0, 0);
            }
        }

        BigInteger remainingNanoseconds = nanosecondsBetween(cursorDateTime, endDateTime);
        TemporalDurationRecord timeDuration = balanceTimeDuration(remainingNanoseconds, "hour");
        TemporalDurationRecord durationRecord = new TemporalDurationRecord(
                years,
                months,
                weeks,
                days,
                timeDuration.hours(),
                timeDuration.minutes(),
                timeDuration.seconds(),
                timeDuration.milliseconds(),
                timeDuration.microseconds(),
                timeDuration.nanoseconds());
        if (!durationRecord.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return null;
        }
        return durationRecord;
    }

    private static String canonicalizeTemporalDurationUnit(JSContext context, String unitText, String optionName) {
        String normalizedUnitText;
        switch (unitText) {
            case "year":
            case "years":
                normalizedUnitText = "year";
                break;
            case "month":
            case "months":
                normalizedUnitText = "month";
                break;
            case "week":
            case "weeks":
                normalizedUnitText = "week";
                break;
            case "day":
            case "days":
                normalizedUnitText = "day";
                break;
            case "hour":
            case "hours":
                normalizedUnitText = "hour";
                break;
            case "minute":
            case "minutes":
                normalizedUnitText = "minute";
                break;
            case "second":
            case "seconds":
                normalizedUnitText = "second";
                break;
            case "millisecond":
            case "milliseconds":
                normalizedUnitText = "millisecond";
                break;
            case "microsecond":
            case "microseconds":
                normalizedUnitText = "microsecond";
                break;
            case "nanosecond":
            case "nanoseconds":
                normalizedUnitText = "nanosecond";
                break;
            default:
                context.throwRangeError("Temporal error: Invalid " + optionName + ".");
                return null;
        }
        return normalizedUnitText;
    }

    private static JSTemporalDuration checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalDuration duration)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return duration;
    }

    // ========== Methods ==========

    private static String coarserDurationUnit(String leftUnit, String rightUnit) {
        if (temporalDurationUnitRank(leftUnit) <= temporalDurationUnitRank(rightUnit)) {
            return leftUnit;
        } else {
            return rightUnit;
        }
    }

    public static JSValue days(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "days");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().days());
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

    private static boolean hasAnyDateUnits(TemporalDurationRecord durationRecord) {
        return durationRecord.years() != 0
                || durationRecord.months() != 0
                || durationRecord.weeks() != 0
                || durationRecord.days() != 0;
    }

    private static boolean hasTimeUnits(TemporalDurationRecord durationRecord) {
        return durationRecord.hours() != 0
                || durationRecord.minutes() != 0
                || durationRecord.seconds() != 0
                || durationRecord.milliseconds() != 0
                || durationRecord.microseconds() != 0
                || durationRecord.nanoseconds() != 0;
    }

    private static boolean hasCalendarUnits(TemporalDurationRecord durationRecord) {
        return durationRecord.years() != 0 || durationRecord.months() != 0 || durationRecord.weeks() != 0;
    }

    public static JSValue hours(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "hours");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().hours());
    }

    private static boolean isCalendarUnit(String unit) {
        return "year".equals(unit) || "month".equals(unit) || "week".equals(unit) || "day".equals(unit);
    }

    private static boolean isRelativeToRequiredUnit(String unit) {
        return "year".equals(unit) || "month".equals(unit) || "week".equals(unit);
    }

    private static boolean isTimeUnit(String unit) {
        return "hour".equals(unit)
                || "minute".equals(unit)
                || "second".equals(unit)
                || "millisecond".equals(unit)
                || "microsecond".equals(unit)
                || "nanosecond".equals(unit);
    }

    private static boolean isValidIncrementForUnit(String smallestUnit, long roundingIncrement) {
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

    private static String largestUnitOfDuration(TemporalDurationRecord durationRecord) {
        if (durationRecord.years() != 0) {
            return "year";
        }
        if (durationRecord.months() != 0) {
            return "month";
        }
        if (durationRecord.weeks() != 0) {
            return "week";
        }
        if (durationRecord.days() != 0) {
            return TemporalDurationConstructor.UNIT_DAY;
        }
        if (durationRecord.hours() != 0) {
            return TemporalDurationConstructor.UNIT_HOUR;
        }
        if (durationRecord.minutes() != 0) {
            return TemporalDurationConstructor.UNIT_MINUTE;
        }
        if (durationRecord.seconds() != 0) {
            return TemporalDurationConstructor.UNIT_SECOND;
        }
        if (durationRecord.milliseconds() != 0) {
            return TemporalDurationConstructor.UNIT_MILLISECOND;
        }
        if (durationRecord.microseconds() != 0) {
            return TemporalDurationConstructor.UNIT_MICROSECOND;
        }
        return TemporalDurationConstructor.UNIT_NANOSECOND;
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

    public static JSValue months(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "months");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().months());
    }

    private static UnitStepResult moveByWholeCalendarUnits(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit) {
        long unitCount = 0;
        if (!endDateTime.isBefore(startDateTime)) {
            while (true) {
                long nextUnitCount = unitCount + 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, calendarUnit, nextUnitCount);
                if (nextDateTime.isAfter(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
            }
        } else {
            while (true) {
                long nextUnitCount = unitCount - 1L;
                LocalDateTime nextDateTime = addCalendarUnits(startDateTime, calendarUnit, nextUnitCount);
                if (nextDateTime.isBefore(endDateTime)) {
                    break;
                }
                unitCount = nextUnitCount;
            }
        }
        LocalDateTime boundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, unitCount);
        return new UnitStepResult(unitCount, boundaryDateTime);
    }

    private static UnitStepResult moveByWholeFixedUnits(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String unit) {
        BigInteger unitNanoseconds = unitToNanosecondsBigInteger(unit);
        if (unitNanoseconds.signum() == 0) {
            return new UnitStepResult(0, startDateTime);
        }
        BigInteger deltaNanoseconds = nanosecondsBetween(startDateTime, endDateTime);
        long unitCount = deltaNanoseconds.divide(unitNanoseconds).longValue();
        LocalDateTime boundaryDateTime = addFixedUnits(startDateTime, unit, unitCount);
        return new UnitStepResult(unitCount, boundaryDateTime);
    }

    public static JSValue nanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "nanoseconds");
        if (d == null) return JSUndefined.INSTANCE;
        return JSNumber.of(d.getRecord().nanoseconds());
    }

    private static BigInteger nanosecondsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigInteger startSeconds = BigInteger.valueOf(startDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger endSeconds = BigInteger.valueOf(endDateTime.toEpochSecond(ZoneOffset.UTC));
        BigInteger secondDifference = endSeconds.subtract(startSeconds).multiply(SECOND_NANOSECONDS);
        long nanosecondDifference = endDateTime.getNano() - startDateTime.getNano();
        return secondDifference.add(BigInteger.valueOf(nanosecondDifference));
    }

    public static JSValue negated(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration d = checkReceiver(context, thisArg, "negated");
        if (d == null) return JSUndefined.INSTANCE;
        return TemporalDurationConstructor.createDuration(context, d.getRecord().negated());
    }

    private static RoundOptions parseRoundOptions(JSContext context, JSValue roundToArg, TemporalDurationRecord durationRecord) {
        String largestUnitText = null;
        String smallestUnitText = null;
        RelativeToOption relativeToOption = null;
        boolean largestUnitProvided = false;
        long roundingIncrement = 1L;
        String roundingMode = "halfExpand";

        if (roundToArg instanceof JSString smallestUnitStringValue) {
            smallestUnitText = smallestUnitStringValue.value();
        } else if (roundToArg instanceof JSObject optionsObject) {
            JSValue largestUnitValue = optionsObject.get(PropertyKey.fromString("largestUnit"));
            if (context.hasPendingException()) {
                return null;
            }
            if (!(largestUnitValue instanceof JSUndefined) && largestUnitValue != null) {
                largestUnitProvided = true;
                largestUnitText = JSTypeConversions.toString(context, largestUnitValue).value();
                if (context.hasPendingException()) {
                    return null;
                }
            }

            JSValue relativeToValue = optionsObject.get(PropertyKey.fromString("relativeTo"));
            if (context.hasPendingException()) {
                return null;
            }
            relativeToOption = parseRoundRelativeToOption(context, relativeToValue);
            if (context.hasPendingException()) {
                return null;
            }

            JSValue roundingIncrementValue = optionsObject.get(PropertyKey.fromString("roundingIncrement"));
            if (context.hasPendingException()) {
                return null;
            }
            roundingIncrement = parseRoundingIncrement(context, roundingIncrementValue);
            if (context.hasPendingException()) {
                return null;
            }

            JSValue roundingModeValue = optionsObject.get(PropertyKey.fromString("roundingMode"));
            if (context.hasPendingException()) {
                return null;
            }
            if (!(roundingModeValue instanceof JSUndefined) && roundingModeValue != null) {
                roundingMode = JSTypeConversions.toString(context, roundingModeValue).value();
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
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return null;
        }

        String canonicalLargestUnit = null;
        if (largestUnitText != null) {
            if (!"auto".equals(largestUnitText)) {
                canonicalLargestUnit = canonicalizeTemporalDurationUnit(context, largestUnitText, "largestUnit");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }

        String canonicalSmallestUnit = null;
        if (smallestUnitText != null) {
            canonicalSmallestUnit = canonicalizeTemporalDurationUnit(context, smallestUnitText, "smallestUnit");
            if (context.hasPendingException()) {
                return null;
            }
        }

        if (canonicalLargestUnit == null && canonicalSmallestUnit == null && !largestUnitProvided) {
            context.throwRangeError("Temporal error: Must specify either smallestUnit or largestUnit.");
            return null;
        }

        if (canonicalSmallestUnit == null) {
            canonicalSmallestUnit = "nanosecond";
        }
        if (canonicalLargestUnit == null) {
            String durationLargestUnit = largestUnitOfDuration(durationRecord);
            canonicalLargestUnit = coarserDurationUnit(canonicalSmallestUnit, durationLargestUnit);
        }

        if (temporalDurationUnitRank(canonicalSmallestUnit) < temporalDurationUnitRank(canonicalLargestUnit)) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        if (!isValidRoundingMode(roundingMode)) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }

        if (!isValidIncrementForUnit(canonicalSmallestUnit, roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        if (roundingIncrement != 1
                && isCalendarUnit(canonicalSmallestUnit)
                && temporalDurationUnitRank(canonicalLargestUnit) < temporalDurationUnitRank(canonicalSmallestUnit)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        return new RoundOptions(
                canonicalSmallestUnit,
                canonicalLargestUnit,
                roundingIncrement,
                roundingMode,
                relativeToOption);
    }

    private static RelativeToOption parseRoundRelativeToOption(JSContext context, JSValue relativeToValue) {
        if (relativeToValue instanceof JSUndefined || relativeToValue == null) {
            return null;
        }
        TemporalDurationConstructor.RelativeToReference relativeToReference =
                TemporalDurationConstructor.parseRelativeToValue(context, relativeToValue);
        if (context.hasPendingException() || relativeToReference == null) {
            return null;
        }
        if (relativeToReference.epochNanoseconds() == null || relativeToReference.timeZoneId() == null) {
            LocalDateTime startDateTime = toLocalDateTime(relativeToReference.relativeDate(), IsoTime.MIDNIGHT);
            return new RelativeToOption(startDateTime, false, null, null);
        }
        IsoDateTime localIsoDateTime;
        if (isOffsetTimeZoneIdentifier(relativeToReference.timeZoneId())) {
            int offsetSeconds = parseOffsetSecondsFromTimeZoneId(relativeToReference.timeZoneId());
            IsoDateTime utcDateTime = TemporalTimeZone.epochNsToUtcDateTime(relativeToReference.epochNanoseconds());
            LocalDateTime utcLocalDateTime = toLocalDateTime(utcDateTime.date(), utcDateTime.time());
            LocalDateTime offsetLocalDateTime = utcLocalDateTime.plusSeconds(offsetSeconds);
            localIsoDateTime = new IsoDateTime(
                    new IsoDate(
                            offsetLocalDateTime.getYear(),
                            offsetLocalDateTime.getMonthValue(),
                            offsetLocalDateTime.getDayOfMonth()),
                    new IsoTime(
                            offsetLocalDateTime.getHour(),
                            offsetLocalDateTime.getMinute(),
                            offsetLocalDateTime.getSecond(),
                            offsetLocalDateTime.getNano() / 1_000_000,
                            (offsetLocalDateTime.getNano() / 1_000) % 1_000,
                            offsetLocalDateTime.getNano() % 1_000));
        } else {
            localIsoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    relativeToReference.epochNanoseconds(),
                    relativeToReference.timeZoneId());
        }
        LocalDateTime startDateTime = toLocalDateTime(localIsoDateTime.date(), localIsoDateTime.time());
        return new RelativeToOption(
                startDateTime,
                true,
                relativeToReference.epochNanoseconds(),
                relativeToReference.timeZoneId());
    }

    private static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return false;
        }
        char signCharacter = timeZoneId.charAt(0);
        return signCharacter == '+' || signCharacter == '-' || signCharacter == '\u2212';
    }

    private static int parseOffsetSecondsFromTimeZoneId(String timeZoneId) {
        String normalizedTimeZoneId = timeZoneId.replace('\u2212', '-');
        int sign = normalizedTimeZoneId.charAt(0) == '-' ? -1 : 1;
        int hours;
        int minutes;
        if (normalizedTimeZoneId.length() == 6 && normalizedTimeZoneId.charAt(3) == ':') {
            hours = Integer.parseInt(normalizedTimeZoneId.substring(1, 3));
            minutes = Integer.parseInt(normalizedTimeZoneId.substring(4, 6));
        } else if (normalizedTimeZoneId.length() == 5) {
            hours = Integer.parseInt(normalizedTimeZoneId.substring(1, 3));
            minutes = Integer.parseInt(normalizedTimeZoneId.substring(3, 5));
        } else {
            throw new IllegalArgumentException("Invalid offset time zone identifier: " + timeZoneId);
        }
        return sign * (hours * 3600 + minutes * 60);
    }

    private static long parseRoundingIncrement(JSContext context, JSValue value) {
        if (value instanceof JSUndefined || value == null) {
            return 1L;
        }
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        long integerValue = (long) numericValue;
        if (integerValue < 1 || integerValue > 1_000_000_000L) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        return integerValue;
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "round");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        RoundOptions roundOptions = parseRoundOptions(context, args[0], duration.getRecord());
        if (context.hasPendingException() || roundOptions == null) {
            return JSUndefined.INSTANCE;
        }

        RelativeToOption relativeToOption = roundOptions.relativeToOption();

        TemporalDurationRecord durationRecord = duration.getRecord();
        boolean requiresRelativeTo =
                hasCalendarUnits(durationRecord)
                        || isRelativeToRequiredUnit(roundOptions.smallestUnit())
                        || isRelativeToRequiredUnit(roundOptions.largestUnit());
        if (requiresRelativeTo && relativeToOption == null) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        RoundComputationResult roundComputationResult;
        if (relativeToOption == null) {
            roundComputationResult = roundWithoutRelativeTo(context, durationRecord, roundOptions);
        } else {
            TemporalDurationRecord roundedDurationRecord =
                    roundWithRelativeTo(context, durationRecord, roundOptions, relativeToOption);
            roundComputationResult = new RoundComputationResult(roundedDurationRecord, null);
        }
        if (context.hasPendingException() || roundComputationResult == null || roundComputationResult.durationRecord() == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord normalizedDurationRecord =
                TemporalDurationConstructor.normalizeFloat64RepresentableFields(roundComputationResult.durationRecord());
        if (!normalizedDurationRecord.isValid() || !TemporalDurationConstructor.isDurationRecordTimeRangeValid(normalizedDurationRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        JSTemporalDuration roundedDuration = TemporalDurationConstructor.createDuration(context, normalizedDurationRecord);
        DurationFieldOverrides durationFieldOverrides = roundComputationResult.durationFieldOverrides();
        if (durationFieldOverrides != null) {
            applyDurationFieldOverrides(roundedDuration, durationFieldOverrides);
        }
        return roundedDuration;
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
        int sign = value.signum();

        switch (roundingMode) {
            case "floor":
                return floorValue;
            case "ceil":
                return ceilValue;
            case "trunc":
                if (sign < 0) {
                    return ceilValue;
                } else {
                    return floorValue;
                }
            case "expand":
                if (sign < 0) {
                    return floorValue;
                } else {
                    return ceilValue;
                }
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
                        if (sign < 0) {
                            yield floorValue;
                        } else {
                            yield ceilValue;
                        }
                    }
                    case "halfTrunc" -> {
                        if (sign < 0) {
                            yield ceilValue;
                        } else {
                            yield floorValue;
                        }
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

    private static LocalDateTime roundDateTimeDifference(
            JSContext context,
            LocalDateTime startDateTime,
            LocalDateTime unroundedEndDateTime,
            RoundOptions roundOptions,
            RelativeToOption relativeToOption,
            TemporalDurationRecord durationRecord) {
        String smallestUnit = roundOptions.smallestUnit();
        long roundingIncrement = roundOptions.roundingIncrement();
        String roundingMode = roundOptions.roundingMode();

        if ("year".equals(smallestUnit) || "month".equals(smallestUnit)) {
            return roundDateTimeToCalendarUnit(
                    startDateTime,
                    unroundedEndDateTime,
                    smallestUnit,
                    roundingIncrement,
                    roundingMode);
        }

        if ("week".equals(smallestUnit)
                && ("year".equals(roundOptions.largestUnit())
                || "month".equals(roundOptions.largestUnit())
                || "week".equals(roundOptions.largestUnit()))) {
            LocalDateTime weekAnchorDateTime = startDateTime;
            if ("year".equals(roundOptions.largestUnit())) {
                UnitStepResult yearStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "year");
                weekAnchorDateTime = yearStepResult.boundaryDateTime();
                UnitStepResult monthStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "month");
                weekAnchorDateTime = monthStepResult.boundaryDateTime();
            } else if ("month".equals(roundOptions.largestUnit())) {
                UnitStepResult monthStepResult = moveByWholeCalendarUnits(weekAnchorDateTime, unroundedEndDateTime, "month");
                weekAnchorDateTime = monthStepResult.boundaryDateTime();
            }
            BigInteger weekRemainderNanoseconds = nanosecondsBetween(weekAnchorDateTime, unroundedEndDateTime);
            BigInteger weekIncrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
            BigInteger roundedWeekRemainderNanoseconds =
                    roundBigIntegerToIncrement(weekRemainderNanoseconds, weekIncrementNanoseconds, roundingMode);
            return addNanosecondsToDateTime(context, weekAnchorDateTime, roundedWeekRemainderNanoseconds);
        }

        if (relativeToOption.zoned() && "day".equals(roundOptions.largestUnit()) && isTimeUnit(smallestUnit)) {
            BigInteger nextDayEpochNanoseconds = relativeToOption.epochNanoseconds().add(DAY_NANOSECONDS);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(nextDayEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        if (relativeToOption.zoned() && isTimeUnit(smallestUnit) && hasAnyDateUnits(durationRecord)) {
            long wholeDayCount = java.time.temporal.ChronoUnit.DAYS.between(
                    startDateTime.toLocalDate(),
                    unroundedEndDateTime.toLocalDate());
            LocalDateTime dayAdjustedDateTime = startDateTime.plusDays(wholeDayCount);
            BigInteger timeRemainderNanoseconds = nanosecondsBetween(dayAdjustedDateTime, unroundedEndDateTime);
            BigInteger incrementNanoseconds = unitToNanosecondsBigInteger(smallestUnit)
                    .multiply(BigInteger.valueOf(roundingIncrement));
            BigInteger roundedTimeRemainderNanoseconds =
                    roundBigIntegerToIncrement(timeRemainderNanoseconds, incrementNanoseconds, roundingMode);
            return addNanosecondsToDateTime(context, dayAdjustedDateTime, roundedTimeRemainderNanoseconds);
        }

        BigInteger totalNanoseconds = nanosecondsBetween(startDateTime, unroundedEndDateTime);
        BigInteger incrementNanoseconds;
        if ("week".equals(smallestUnit)) {
            incrementNanoseconds = WEEK_NANOSECONDS.multiply(BigInteger.valueOf(roundingIncrement));
        } else {
            incrementNanoseconds = unitToNanosecondsBigInteger(smallestUnit).multiply(BigInteger.valueOf(roundingIncrement));
        }
        BigInteger roundedNanoseconds = roundBigIntegerToIncrement(totalNanoseconds, incrementNanoseconds, roundingMode);
        return addNanosecondsToDateTime(context, startDateTime, roundedNanoseconds);
    }

    private static LocalDateTime roundDateTimeToCalendarUnit(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String calendarUnit,
            long roundingIncrement,
            String roundingMode) {
        UnitStepResult truncatedStepResult = moveByWholeCalendarUnits(startDateTime, endDateTime, calendarUnit);
        long truncatedCount = truncatedStepResult.count();
        LocalDateTime truncatedBoundaryDateTime = truncatedStepResult.boundaryDateTime();

        long lowerUnitCount;
        long upperUnitCount;
        if (truncatedBoundaryDateTime.isAfter(endDateTime)) {
            lowerUnitCount = truncatedCount - 1;
            upperUnitCount = truncatedCount;
        } else if (truncatedBoundaryDateTime.isBefore(endDateTime)) {
            lowerUnitCount = truncatedCount;
            upperUnitCount = truncatedCount + 1;
        } else {
            lowerUnitCount = truncatedCount;
            upperUnitCount = truncatedCount;
        }

        long lowerMultipleCount = Math.floorDiv(lowerUnitCount, roundingIncrement) * roundingIncrement;
        long upperMultipleCount = lowerMultipleCount + roundingIncrement;

        LocalDateTime lowerBoundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, lowerMultipleCount);
        LocalDateTime upperBoundaryDateTime = addCalendarUnits(startDateTime, calendarUnit, upperMultipleCount);

        if (endDateTime.equals(lowerBoundaryDateTime)) {
            return lowerBoundaryDateTime;
        }
        if (endDateTime.equals(upperBoundaryDateTime)) {
            return upperBoundaryDateTime;
        }

        int direction = endDateTime.isBefore(startDateTime) ? -1 : 1;
        switch (roundingMode) {
            case "ceil":
                return upperBoundaryDateTime;
            case "floor":
                return lowerBoundaryDateTime;
            case "trunc":
                if (direction < 0) {
                    return upperBoundaryDateTime;
                } else {
                    return lowerBoundaryDateTime;
                }
            case "expand":
                if (direction < 0) {
                    return lowerBoundaryDateTime;
                } else {
                    return upperBoundaryDateTime;
                }
            case "halfEven":
            case "halfExpand":
            case "halfTrunc":
            case "halfCeil":
            case "halfFloor":
                BigInteger lowerDistanceNanoseconds = nanosecondsBetween(lowerBoundaryDateTime, endDateTime).abs();
                BigInteger upperDistanceNanoseconds = nanosecondsBetween(endDateTime, upperBoundaryDateTime).abs();
                int distanceComparison = lowerDistanceNanoseconds.compareTo(upperDistanceNanoseconds);
                if (distanceComparison < 0) {
                    return lowerBoundaryDateTime;
                }
                if (distanceComparison > 0) {
                    return upperBoundaryDateTime;
                }
                return switch (roundingMode) {
                    case "halfEven" -> {
                        if (Math.floorMod(lowerMultipleCount / roundingIncrement, 2L) == 0L) {
                            yield lowerBoundaryDateTime;
                        } else {
                            yield upperBoundaryDateTime;
                        }
                    }
                    case "halfExpand" -> {
                        if (direction < 0) {
                            yield lowerBoundaryDateTime;
                        } else {
                            yield upperBoundaryDateTime;
                        }
                    }
                    case "halfTrunc" -> {
                        if (direction < 0) {
                            yield upperBoundaryDateTime;
                        } else {
                            yield lowerBoundaryDateTime;
                        }
                    }
                    case "halfCeil" -> upperBoundaryDateTime;
                    case "halfFloor" -> lowerBoundaryDateTime;
                    default -> lowerBoundaryDateTime;
                };
            default:
                return upperBoundaryDateTime;
        }
    }

    private static TemporalDurationRecord roundWithRelativeTo(
            JSContext context,
            TemporalDurationRecord durationRecord,
            RoundOptions roundOptions,
            RelativeToOption relativeToOption) {
        LocalDateTime startDateTime = relativeToOption.startDateTime();
        if (!relativeToOption.zoned()
                && !durationRecord.isBlank()
                && (isTimeUnit(roundOptions.smallestUnit()) || hasTimeUnits(durationRecord))
                && !isDateTimeWithinTemporalRange(startDateTime)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        if (relativeToOption.zoned() && !hasCalendarUnits(durationRecord)) {
            BigInteger dayTimeNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
            if (dayTimeNanoseconds.abs().compareTo(MAX_FLOAT64_NANOSECONDS_COMPONENT) >= 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger endEpochNanoseconds = relativeToOption.epochNanoseconds().add(dayTimeNanoseconds);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(endEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        LocalDateTime unroundedEndDateTime = addDurationToDateTime(context, startDateTime, durationRecord);
        if (context.hasPendingException()) {
            return null;
        }
        if (relativeToOption.zoned()) {
            if (!isDateTimeWithinTemporalRange(unroundedEndDateTime)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        } else {
            if (!isDateWithinTemporalRange(unroundedEndDateTime.toLocalDate())) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
        }

        LocalDateTime roundedEndDateTime = roundDateTimeDifference(
                context,
                startDateTime,
                unroundedEndDateTime,
                roundOptions,
                relativeToOption,
                durationRecord);
        if (context.hasPendingException()) {
            return null;
        }

        return buildBalancedDurationFromDateTimes(
                context,
                startDateTime,
                roundedEndDateTime,
                roundOptions.largestUnit(),
                roundOptions.smallestUnit());
    }

    private static RoundComputationResult roundWithoutRelativeTo(
            JSContext context,
            TemporalDurationRecord durationRecord,
            RoundOptions roundOptions) {
        if (isRelativeToRequiredUnit(roundOptions.smallestUnit()) || isRelativeToRequiredUnit(roundOptions.largestUnit())) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        if (hasCalendarUnits(durationRecord)) {
            context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return null;
        }
        BigInteger totalNanoseconds = TemporalDurationConstructor.dayTimeNanoseconds(durationRecord);
        BigInteger roundingUnitNanoseconds = unitToNanosecondsBigInteger(roundOptions.smallestUnit());
        BigInteger incrementNanoseconds = roundingUnitNanoseconds.multiply(BigInteger.valueOf(roundOptions.roundingIncrement()));
        BigInteger roundedNanoseconds = roundBigIntegerToIncrement(totalNanoseconds, incrementNanoseconds, roundOptions.roundingMode());
        DurationFieldOverrides durationFieldOverrides = null;
        String balancingLargestUnit = roundOptions.largestUnit();
        if ("millisecond".equals(roundOptions.largestUnit())) {
            BigInteger millisecondValue = roundedNanoseconds.divide(MILLISECOND_NANOSECONDS);
            if (millisecondValue.abs().compareTo(MAX_FLOAT64_MILLISECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger microsecondRemainder = roundedNanoseconds.remainder(MILLISECOND_NANOSECONDS).divide(MICROSECOND_NANOSECONDS);
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new DurationFieldOverrides(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    millisecondValue.doubleValue(),
                    microsecondRemainder.doubleValue(),
                    nanosecondRemainder.doubleValue());
            if (millisecondValue.abs().bitLength() > 63) {
                balancingLargestUnit = "second";
            }
        } else if ("microsecond".equals(roundOptions.largestUnit())) {
            BigInteger microsecondValue = roundedNanoseconds.divide(MICROSECOND_NANOSECONDS);
            if (microsecondValue.abs().compareTo(MAX_FLOAT64_MICROSECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            BigInteger nanosecondRemainder = roundedNanoseconds.remainder(MICROSECOND_NANOSECONDS);
            durationFieldOverrides = new DurationFieldOverrides(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    0D,
                    microsecondValue.doubleValue(),
                    nanosecondRemainder.doubleValue());
            if (microsecondValue.abs().bitLength() > 63) {
                balancingLargestUnit = "second";
            }
        } else if ("nanosecond".equals(roundOptions.largestUnit())) {
            if (roundedNanoseconds.abs().compareTo(MAX_FLOAT64_NANOSECONDS_COMPONENT) > 0) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            durationFieldOverrides = new DurationFieldOverrides(
                    0D, 0D, 0D, 0D, 0D, 0D, 0D,
                    0D, 0D,
                    roundedNanoseconds.doubleValue());
            if (roundedNanoseconds.abs().bitLength() > 63) {
                balancingLargestUnit = "second";
            }
        }

        TemporalDurationRecord balancedTimeDuration = balanceTimeDuration(roundedNanoseconds, balancingLargestUnit);
        TemporalDurationRecord durationResult = new TemporalDurationRecord(
                0,
                0,
                0,
                balancedTimeDuration.days(),
                balancedTimeDuration.hours(),
                balancedTimeDuration.minutes(),
                balancedTimeDuration.seconds(),
                balancedTimeDuration.milliseconds(),
                balancedTimeDuration.microseconds(),
                balancedTimeDuration.nanoseconds());
        return new RoundComputationResult(durationResult, durationFieldOverrides);
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

    private static int temporalDurationUnitRank(String unit) {
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
        JSTemporalDuration d = checkReceiver(context, thisArg, "toJSON");
        if (d == null) return JSUndefined.INSTANCE;
        return new JSString(d.getRecord().toString());
    }

    private static LocalDateTime toLocalDateTime(IsoDate isoDate, IsoTime isoTime) {
        return LocalDateTime.of(
                isoDate.year(),
                isoDate.month(),
                isoDate.day(),
                isoTime.hour(),
                isoTime.minute(),
                isoTime.second(),
                isoTime.millisecond() * 1_000_000
                        + isoTime.microsecond() * 1_000
                        + isoTime.nanosecond());
    }

    private static boolean isDateTimeWithinTemporalRange(LocalDateTime dateTime) {
        LocalDateTime minimumDateTime = LocalDateTime.of(-271821, 4, 20, 0, 0, 0, 0);
        LocalDateTime maximumDateTime = LocalDateTime.of(275760, 9, 13, 23, 59, 59, 999_999_999);
        return !dateTime.isBefore(minimumDateTime) && !dateTime.isAfter(maximumDateTime);
    }

    private static boolean isDateWithinTemporalRange(LocalDate date) {
        LocalDate minimumDate = LocalDate.of(-271821, 4, 19);
        LocalDate maximumDate = LocalDate.of(275760, 9, 13);
        return !date.isBefore(minimumDate) && !date.isAfter(maximumDate);
    }

    private static void applyDurationFieldOverrides(
            JSTemporalDuration duration,
            DurationFieldOverrides durationFieldOverrides) {
        duration.defineProperty(
                PropertyKey.fromString("years"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.years())));
        duration.defineProperty(
                PropertyKey.fromString("months"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.months())));
        duration.defineProperty(
                PropertyKey.fromString("weeks"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.weeks())));
        duration.defineProperty(
                PropertyKey.fromString("days"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.days())));
        duration.defineProperty(
                PropertyKey.fromString("hours"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.hours())));
        duration.defineProperty(
                PropertyKey.fromString("minutes"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.minutes())));
        duration.defineProperty(
                PropertyKey.fromString("seconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.seconds())));
        duration.defineProperty(
                PropertyKey.fromString("milliseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.milliseconds())));
        duration.defineProperty(
                PropertyKey.fromString("microseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.microseconds())));
        duration.defineProperty(
                PropertyKey.fromString("nanoseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.nanoseconds())));
    }

    // ========== Internal helpers ==========

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = checkReceiver(context, thisArg, "toLocaleString");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue durationFormat = JSIntlObject.createDurationFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.durationFormatFormat(context, durationFormat, new JSValue[]{duration});
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

        BigInteger totalNs = TemporalDurationConstructor.dayTimeNanoseconds(d.getRecord());
        long unitNs = unitToNanoseconds(unit);
        if (unitNs == 0) {
            context.throwRangeError("Temporal error: Invalid unit for total.");
            return JSUndefined.INSTANCE;
        }

        double totalValue = totalNs.doubleValue() / unitNs;
        return JSNumber.of(totalValue);
    }

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

    private static BigInteger unitToNanosecondsBigInteger(String unit) {
        return switch (unit) {
            case "day" -> DAY_NANOSECONDS;
            case "hour" -> HOUR_NANOSECONDS;
            case "minute" -> MINUTE_NANOSECONDS;
            case "second" -> SECOND_NANOSECONDS;
            case "millisecond" -> MILLISECOND_NANOSECONDS;
            case "microsecond" -> MICROSECOND_NANOSECONDS;
            case "nanosecond" -> BigInteger.ONE;
            case "week" -> WEEK_NANOSECONDS;
            default -> BigInteger.ZERO;
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

    private record RelativeToOption(
            LocalDateTime startDateTime,
            boolean zoned,
            BigInteger epochNanoseconds,
            String timeZoneId) {
    }

    private record RoundOptions(
            String smallestUnit,
            String largestUnit,
            long roundingIncrement,
            String roundingMode,
            RelativeToOption relativeToOption) {
    }

    private record DurationFieldOverrides(
            double years,
            double months,
            double weeks,
            double days,
            double hours,
            double minutes,
            double seconds,
            double milliseconds,
            double microseconds,
            double nanoseconds) {
    }

    private record RoundComputationResult(
            TemporalDurationRecord durationRecord,
            DurationFieldOverrides durationFieldOverrides) {
    }

    private record UnitStepResult(long count, LocalDateTime boundaryDateTime) {
    }
}
