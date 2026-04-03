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
    private static final BigInteger NS_PER_HOUR = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger NS_PER_MINUTE = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger NS_PER_MS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger NS_PER_SECOND = BILLION;
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

    private static JSValue addOrSubtract(JSContext context, JSTemporalZonedDateTime zonedDateTime, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException() || temporalDuration == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord durationRecord = temporalDuration.getRecord();
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

    private static BigInteger addDurationToZonedDateTime(
            JSContext context,
            JSTemporalZonedDateTime zonedDateTime,
            TemporalDurationRecord durationRecord,
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

    private static BigInteger durationTimeToNanoseconds(TemporalDurationRecord durationRecord) {
        return BigInteger.valueOf(durationRecord.hours()).multiply(NS_PER_HOUR)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(NS_PER_MINUTE))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(NS_PER_SECOND))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(NS_PER_MS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(BigInteger.valueOf(1_000L)))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
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

    private static IsoDateTime getLocalDateTime(JSTemporalZonedDateTime zonedDateTime) {
        return TemporalTimeZone.epochNsToDateTimeInZone(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
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

    private static JSValue nsToDuration(JSContext context, BigInteger diffNs) {
        int signum = diffNs.signum();
        BigInteger absoluteDiffNanoseconds = diffNs.abs();
        long totalHours = absoluteDiffNanoseconds.divide(NS_PER_HOUR).longValue();
        absoluteDiffNanoseconds = absoluteDiffNanoseconds.remainder(NS_PER_HOUR);
        long totalMinutes = absoluteDiffNanoseconds.divide(NS_PER_MINUTE).longValue();
        absoluteDiffNanoseconds = absoluteDiffNanoseconds.remainder(NS_PER_MINUTE);
        long totalSeconds = absoluteDiffNanoseconds.divide(NS_PER_SECOND).longValue();
        absoluteDiffNanoseconds = absoluteDiffNanoseconds.remainder(NS_PER_SECOND);
        long totalMilliseconds = absoluteDiffNanoseconds.divide(NS_PER_MS).longValue();
        absoluteDiffNanoseconds = absoluteDiffNanoseconds.remainder(NS_PER_MS);
        long totalMicroseconds = absoluteDiffNanoseconds.divide(BigInteger.valueOf(1_000)).longValue();
        long totalNanoseconds = absoluteDiffNanoseconds.remainder(BigInteger.valueOf(1_000)).longValue();

        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, 0, 0, 0,
                        totalHours * signum, totalMinutes * signum, totalSeconds * signum,
                        totalMilliseconds * signum, totalMicroseconds * signum, totalNanoseconds * signum));
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
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
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
            context.throwRangeError("Temporal error: Invalid unit: " + smallestUnit);
            return JSUndefined.INSTANCE;
        }

        BigInteger epochNs = zonedDateTime.getEpochNanoseconds();
        BigInteger rounded = roundToIncrement(epochNs, unitNs);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(rounded)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, rounded,
                zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
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

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "second");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        return JSNumber.of(localDateTime.time().second());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "since");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        BigInteger diffNs = zonedDateTime.getEpochNanoseconds().subtract(other.getEpochNanoseconds());
        return nsToDuration(context, diffNs);
    }

    public static JSValue startOfDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "startOfDay");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        IsoDateTime startOfDay = new IsoDateTime(localDateTime.date(), IsoTime.MIDNIGHT);
        BigInteger epochNs = TemporalTimeZone.localDateTimeToEpochNs(startOfDay, zonedDateTime.getTimeZoneId());
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
                zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "subtract");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, zonedDateTime, args, -1);
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
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String result = formatZonedDateTimeBase(zonedDateTime);
        result = TemporalUtils.maybeAppendCalendar(result, zonedDateTime.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "until");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        BigInteger diffNs = other.getEpochNanoseconds().subtract(zonedDateTime.getEpochNanoseconds());
        return nsToDuration(context, diffNs);
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
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        int year = TemporalUtils.getIntegerField(context, fields, "year", localDateTime.date().year());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int month = TemporalUtils.getIntegerField(context, fields, "month", localDateTime.date().month());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int dayOfMonth = TemporalUtils.getIntegerField(context, fields, "day", localDateTime.date().day());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int hour = TemporalUtils.getIntegerField(context, fields, "hour", localDateTime.time().hour());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int minute = TemporalUtils.getIntegerField(context, fields, "minute", localDateTime.time().minute());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int second = TemporalUtils.getIntegerField(context, fields, "second", localDateTime.time().second());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int millisecond = TemporalUtils.getIntegerField(context, fields, "millisecond", localDateTime.time().millisecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int microsecond = TemporalUtils.getIntegerField(context, fields, "microsecond", localDateTime.time().microsecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", localDateTime.time().nanosecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        IsoDateTime newLocalDateTime = new IsoDateTime(new IsoDate(year, month, dayOfMonth),
                new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond));
        BigInteger epochNs = TemporalTimeZone.localDateTimeToEpochNs(newLocalDateTime, zonedDateTime.getTimeZoneId());
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
                zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "withCalendar");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        String calendarId = TemporalUtils.validateCalendar(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = checkReceiver(context, thisArg, "withPlainTime");
        if (zonedDateTime == null) return JSUndefined.INSTANCE;
        IsoDateTime localDateTime = getLocalDateTime(zonedDateTime);
        IsoTime newTime = IsoTime.MIDNIGHT;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue timeArg = args[0];
            if (timeArg instanceof JSTemporalPlainTime plainTime) {
                newTime = plainTime.getIsoTime();
            } else if (timeArg instanceof JSString timeStr) {
                newTime = TemporalParser.parseTimeString(context, timeStr.value());
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
            } else if (timeArg instanceof JSObject timeObj) {
                int hourValue = TemporalUtils.getIntegerField(context, timeObj, "hour", 0);
                int minuteValue = TemporalUtils.getIntegerField(context, timeObj, "minute", 0);
                int secondValue = TemporalUtils.getIntegerField(context, timeObj, "second", 0);
                int millisecondValue = TemporalUtils.getIntegerField(context, timeObj, "millisecond", 0);
                int microsecondValue = TemporalUtils.getIntegerField(context, timeObj, "microsecond", 0);
                int nanosecondValue = TemporalUtils.getIntegerField(context, timeObj, "nanosecond", 0);
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
                newTime = new IsoTime(hourValue, minuteValue, secondValue, millisecondValue, microsecondValue, nanosecondValue);
            }
        }
        IsoDateTime newLocalDateTime = new IsoDateTime(localDateTime.date(), newTime);
        BigInteger epochNs = TemporalTimeZone.localDateTimeToEpochNs(newLocalDateTime, zonedDateTime.getTimeZoneId());
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
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
}
