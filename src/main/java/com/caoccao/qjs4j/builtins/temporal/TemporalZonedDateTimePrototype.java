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
 * Implementation of Temporal.ZonedDateTime prototype methods.
 */
public final class TemporalZonedDateTimePrototype {
    private static final String TYPE_NAME = "Temporal.ZonedDateTime";
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger NS_PER_MS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger NS_PER_HOUR = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger NS_PER_MINUTE = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger NS_PER_SECOND = BILLION;

    private TemporalZonedDateTimePrototype() {
    }

    // ========== Getters ==========

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "calendarId");
        if (zdt == null) return JSUndefined.INSTANCE;
        return new JSString(zdt.getCalendarId());
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "day");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.date().day());
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "dayOfWeek");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.date().dayOfWeek());
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "dayOfYear");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.date().dayOfYear());
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "daysInMonth");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(IsoDate.daysInMonth(dt.date().year(), dt.date().month()));
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "daysInWeek");
        if (zdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(7);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "daysInYear");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(IsoDate.daysInYear(dt.date().year()));
    }

    public static JSValue epochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "epochMilliseconds");
        if (zdt == null) return JSUndefined.INSTANCE;
        BigInteger ms = floorDiv(zdt.getEpochNanoseconds(), NS_PER_MS);
        return JSNumber.of(ms.longValue());
    }

    public static JSValue epochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "epochNanoseconds");
        if (zdt == null) return JSUndefined.INSTANCE;
        return new JSBigInt(zdt.getEpochNanoseconds());
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "era");
        if (zdt == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "eraYear");
        if (zdt == null) return JSUndefined.INSTANCE;
        return JSUndefined.INSTANCE;
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "hour");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.time().hour());
    }

    public static JSValue hoursInDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "hoursInDay");
        if (zdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(TemporalTimeZone.getHoursInDay(zdt.getEpochNanoseconds(), zdt.getTimeZoneId()));
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "inLeapYear");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return IsoDate.isLeapYear(dt.date().year()) ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "microsecond");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.time().microsecond());
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "millisecond");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.time().millisecond());
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "minute");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.time().minute());
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "month");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.date().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "monthCode");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return new JSString(TemporalUtils.monthCode(dt.date().month()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "monthsInYear");
        if (zdt == null) return JSUndefined.INSTANCE;
        return JSNumber.of(12);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "nanosecond");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.time().nanosecond());
    }

    public static JSValue offset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "offset");
        if (zdt == null) return JSUndefined.INSTANCE;
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zdt.getEpochNanoseconds(), zdt.getTimeZoneId());
        return new JSString(TemporalTimeZone.formatOffset(offsetSeconds));
    }

    public static JSValue offsetNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "offsetNanoseconds");
        if (zdt == null) return JSUndefined.INSTANCE;
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zdt.getEpochNanoseconds(), zdt.getTimeZoneId());
        return JSNumber.of((long) offsetSeconds * 1_000_000_000L);
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "second");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.time().second());
    }

    public static JSValue timeZoneId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "timeZoneId");
        if (zdt == null) return JSUndefined.INSTANCE;
        return new JSString(zdt.getTimeZoneId());
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "weekOfYear");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.date().weekOfYear());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "year");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.date().year());
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "yearOfWeek");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return JSNumber.of(dt.date().yearOfWeek());
    }

    // ========== Methods ==========

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "add");
        if (zdt == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, zdt, args, 1);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "equals");
        if (zdt == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other;
        if (otherArg instanceof JSTemporalZonedDateTime otherZdt) {
            other = otherZdt;
        } else {
            other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        boolean equal = zdt.getEpochNanoseconds().equals(other.getEpochNanoseconds())
                && zdt.getTimeZoneId().equals(other.getTimeZoneId())
                && zdt.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue getTimeZoneTransition(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "getTimeZoneTransition");
        if (zdt == null) return JSUndefined.INSTANCE;
        JSValue directionArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String direction;
        if (directionArg instanceof JSString dirStr) {
            direction = dirStr.value();
        } else {
            context.throwTypeError("Temporal error: Expected string for direction.");
            return JSUndefined.INSTANCE;
        }

        BigInteger transitionEpochNs;
        if ("next".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getNextTransition(zdt.getEpochNanoseconds(), zdt.getTimeZoneId());
        } else if ("previous".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getPreviousTransition(zdt.getEpochNanoseconds(), zdt.getTimeZoneId());
        } else {
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return JSUndefined.INSTANCE;
        }

        if (transitionEpochNs == null) {
            return JSNull.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                transitionEpochNs, zdt.getTimeZoneId(), zdt.getCalendarId());
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "round");
        if (zdt == null) return JSUndefined.INSTANCE;
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

        BigInteger epochNs = zdt.getEpochNanoseconds();
        BigInteger rounded = roundToIncrement(epochNs, unitNs);
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(rounded)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, rounded,
                zdt.getTimeZoneId(), zdt.getCalendarId());
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "since");
        if (zdt == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        BigInteger diffNs = zdt.getEpochNanoseconds().subtract(other.getEpochNanoseconds());
        return nsToDuration(context, diffNs);
    }

    public static JSValue startOfDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "startOfDay");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        IsoDateTime startOfDay = new IsoDateTime(dt.date(), IsoTime.MIDNIGHT);
        BigInteger epochNs = TemporalTimeZone.localDateTimeToEpochNs(startOfDay, zdt.getTimeZoneId());
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
                zdt.getTimeZoneId(), zdt.getCalendarId());
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "subtract");
        if (zdt == null) return JSUndefined.INSTANCE;
        return addOrSubtract(context, zdt, args, -1);
    }

    public static JSValue toInstant(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "toInstant");
        if (zdt == null) return JSUndefined.INSTANCE;
        return TemporalInstantConstructor.createInstant(context, zdt.getEpochNanoseconds());
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "toJSON");
        if (zdt == null) return JSUndefined.INSTANCE;
        return new JSString(formatZonedDateTime(zdt));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "toLocaleString");
        if (zdt == null) return JSUndefined.INSTANCE;
        return new JSString(formatZonedDateTime(zdt));
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "toPlainDate");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return TemporalPlainDateConstructor.createPlainDate(context, dt.date(), zdt.getCalendarId());
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "toPlainDateTime");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context, dt, zdt.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "toPlainTime");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        return TemporalPlainTimeConstructor.createPlainTime(context, dt.time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "toString");
        if (zdt == null) return JSUndefined.INSTANCE;
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        String result = formatZonedDateTimeBase(zdt);
        result = TemporalUtils.maybeAppendCalendar(result, zdt.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "until");
        if (zdt == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalZonedDateTime other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        BigInteger diffNs = other.getEpochNanoseconds().subtract(zdt.getEpochNanoseconds());
        return nsToDuration(context, diffNs);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.ZonedDateTime.prototype.valueOf; use Temporal.ZonedDateTime.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "with");
        if (zdt == null) return JSUndefined.INSTANCE;
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoDateTime dt = getLocalDateTime(zdt);
        int year = TemporalUtils.getIntegerField(context, fields, "year", dt.date().year());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int month = TemporalUtils.getIntegerField(context, fields, "month", dt.date().month());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int day = TemporalUtils.getIntegerField(context, fields, "day", dt.date().day());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int hour = TemporalUtils.getIntegerField(context, fields, "hour", dt.time().hour());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int minute = TemporalUtils.getIntegerField(context, fields, "minute", dt.time().minute());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int second = TemporalUtils.getIntegerField(context, fields, "second", dt.time().second());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int millisecond = TemporalUtils.getIntegerField(context, fields, "millisecond", dt.time().millisecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int microsecond = TemporalUtils.getIntegerField(context, fields, "microsecond", dt.time().microsecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", dt.time().nanosecond());
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        IsoDateTime newDt = new IsoDateTime(new IsoDate(year, month, day),
                new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond));
        BigInteger epochNs = TemporalTimeZone.localDateTimeToEpochNs(newDt, zdt.getTimeZoneId());
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
                zdt.getTimeZoneId(), zdt.getCalendarId());
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "withCalendar");
        if (zdt == null) return JSUndefined.INSTANCE;
        String calendarId = TemporalUtils.validateCalendar(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context,
                zdt.getEpochNanoseconds(), zdt.getTimeZoneId(), calendarId);
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "withPlainTime");
        if (zdt == null) return JSUndefined.INSTANCE;
        IsoDateTime dt = getLocalDateTime(zdt);
        IsoTime newTime = IsoTime.MIDNIGHT;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue timeArg = args[0];
            if (timeArg instanceof JSTemporalPlainTime pt) {
                newTime = pt.getIsoTime();
            } else if (timeArg instanceof JSString timeStr) {
                newTime = TemporalParser.parseTimeString(context, timeStr.value());
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
            } else if (timeArg instanceof JSObject timeObj) {
                int h = TemporalUtils.getIntegerField(context, timeObj, "hour", 0);
                int min = TemporalUtils.getIntegerField(context, timeObj, "minute", 0);
                int sec = TemporalUtils.getIntegerField(context, timeObj, "second", 0);
                int ms = TemporalUtils.getIntegerField(context, timeObj, "millisecond", 0);
                int us = TemporalUtils.getIntegerField(context, timeObj, "microsecond", 0);
                int ns = TemporalUtils.getIntegerField(context, timeObj, "nanosecond", 0);
                if (context.hasPendingException()) return JSUndefined.INSTANCE;
                newTime = new IsoTime(h, min, sec, ms, us, ns);
            }
        }
        IsoDateTime newDt = new IsoDateTime(dt.date(), newTime);
        BigInteger epochNs = TemporalTimeZone.localDateTimeToEpochNs(newDt, zdt.getTimeZoneId());
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
                zdt.getTimeZoneId(), zdt.getCalendarId());
    }

    public static JSValue withTimeZone(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zdt = checkReceiver(context, thisArg, "withTimeZone");
        if (zdt == null) return JSUndefined.INSTANCE;
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
                zdt.getEpochNanoseconds(), timeZoneId, zdt.getCalendarId());
    }

    // ========== Internal helpers ==========

    private static JSValue addOrSubtract(JSContext context, JSTemporalZonedDateTime zdt, JSValue[] args, int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        long years = 0, months = 0, weeks = 0, days = 0;
        long hours = 0, minutes = 0, seconds = 0, ms = 0, us = 0, ns = 0;
        JSValue durationArg = args[0];
        if (durationArg instanceof JSTemporalDuration dur) {
            TemporalDurationRecord rec = dur.getRecord();
            years = rec.years(); months = rec.months(); weeks = rec.weeks(); days = rec.days();
            hours = rec.hours(); minutes = rec.minutes(); seconds = rec.seconds();
            ms = rec.milliseconds(); us = rec.microseconds(); ns = rec.nanoseconds();
        } else if (durationArg instanceof JSString durStr) {
            TemporalParser.DurationFields df = TemporalParser.parseDurationString(context, durStr.value());
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            years = df.years(); months = df.months(); weeks = df.weeks(); days = df.days();
            hours = df.hours(); minutes = df.minutes(); seconds = df.seconds();
            ms = df.milliseconds(); us = df.microseconds(); ns = df.nanoseconds();
        } else if (durationArg instanceof JSObject durObj) {
            years = TemporalUtils.getIntegerField(context, durObj, "years", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            months = TemporalUtils.getIntegerField(context, durObj, "months", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            weeks = TemporalUtils.getIntegerField(context, durObj, "weeks", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            days = TemporalUtils.getIntegerField(context, durObj, "days", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            hours = TemporalUtils.getIntegerField(context, durObj, "hours", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            minutes = TemporalUtils.getIntegerField(context, durObj, "minutes", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            seconds = TemporalUtils.getIntegerField(context, durObj, "seconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            ms = TemporalUtils.getIntegerField(context, durObj, "milliseconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            us = TemporalUtils.getIntegerField(context, durObj, "microseconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            ns = TemporalUtils.getIntegerField(context, durObj, "nanoseconds", 0);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        } else {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        years *= sign; months *= sign; weeks *= sign; days *= sign;
        hours *= sign; minutes *= sign; seconds *= sign;
        ms *= sign; us *= sign; ns *= sign;

        // For date components, add to local date then resolve
        if (years != 0 || months != 0 || weeks != 0 || days != 0) {
            IsoDateTime dt = getLocalDateTime(zdt);
            IsoDate newDate = TemporalPlainDatePrototype.addToIsoDate(dt.date(),
                    (int) years, (int) months, (int) weeks, (int) days);
            IsoDateTime newDt = new IsoDateTime(newDate, dt.time());
            BigInteger epochNs = TemporalTimeZone.localDateTimeToEpochNs(newDt, zdt.getTimeZoneId());
            // Add time components
            BigInteger timeNs = BigInteger.valueOf(hours * 3_600_000_000_000L
                    + minutes * 60_000_000_000L + seconds * 1_000_000_000L
                    + ms * 1_000_000L + us * 1_000L + ns);
            epochNs = epochNs.add(timeNs);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNs)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
            return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs,
                    zdt.getTimeZoneId(), zdt.getCalendarId());
        } else {
            // Time-only: add directly to epochNs
            BigInteger timeNs = BigInteger.valueOf(hours * 3_600_000_000_000L
                    + minutes * 60_000_000_000L + seconds * 1_000_000_000L
                    + ms * 1_000_000L + us * 1_000L + ns);
            BigInteger result = zdt.getEpochNanoseconds().add(timeNs);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(result)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
            return TemporalZonedDateTimeConstructor.createZonedDateTime(context, result,
                    zdt.getTimeZoneId(), zdt.getCalendarId());
        }
    }

    private static JSTemporalZonedDateTime checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalZonedDateTime zdt)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver " + JSTypeConversions.toString(context, thisArg).value());
            return null;
        }
        return zdt;
    }

    private static BigInteger floorDiv(BigInteger a, BigInteger b) {
        BigInteger[] divRem = a.divideAndRemainder(b);
        if (divRem[1].signum() < 0 && b.signum() > 0 || divRem[1].signum() > 0 && b.signum() < 0) {
            return divRem[0].subtract(BigInteger.ONE);
        }
        return divRem[0];
    }

    private static String formatZonedDateTime(JSTemporalZonedDateTime zdt) {
        String base = formatZonedDateTimeBase(zdt);
        if (!"iso8601".equals(zdt.getCalendarId())) {
            base += "[u-ca=" + zdt.getCalendarId() + "]";
        }
        return base;
    }

    private static String formatZonedDateTimeBase(JSTemporalZonedDateTime zdt) {
        IsoDateTime dt = getLocalDateTime(zdt);
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zdt.getEpochNanoseconds(), zdt.getTimeZoneId());
        String offset = TemporalTimeZone.formatOffset(offsetSeconds);
        return dt.toString() + offset + "[" + zdt.getTimeZoneId() + "]";
    }

    private static IsoDateTime getLocalDateTime(JSTemporalZonedDateTime zdt) {
        return TemporalTimeZone.epochNsToDateTimeInZone(zdt.getEpochNanoseconds(), zdt.getTimeZoneId());
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

    private static JSValue nsToDuration(JSContext context, BigInteger diffNs) {
        int signum = diffNs.signum();
        BigInteger abs = diffNs.abs();
        long totalHours = abs.divide(NS_PER_HOUR).longValue();
        abs = abs.remainder(NS_PER_HOUR);
        long totalMinutes = abs.divide(NS_PER_MINUTE).longValue();
        abs = abs.remainder(NS_PER_MINUTE);
        long totalSeconds = abs.divide(NS_PER_SECOND).longValue();
        abs = abs.remainder(NS_PER_SECOND);
        long totalMs = abs.divide(NS_PER_MS).longValue();
        abs = abs.remainder(NS_PER_MS);
        long totalUs = abs.divide(BigInteger.valueOf(1_000)).longValue();
        long totalNs = abs.remainder(BigInteger.valueOf(1_000)).longValue();

        return TemporalDurationConstructor.createDuration(context,
                new TemporalDurationRecord(0, 0, 0, 0,
                        totalHours * signum, totalMinutes * signum, totalSeconds * signum,
                        totalMs * signum, totalUs * signum, totalNs * signum));
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
}
