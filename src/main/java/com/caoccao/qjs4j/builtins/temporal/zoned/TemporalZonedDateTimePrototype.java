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

package com.caoccao.qjs4j.builtins.temporal.zoned;

import com.caoccao.qjs4j.builtins.temporal.TemporalLocaleFormat;
import com.caoccao.qjs4j.builtins.temporal.TemporalTimeFields;
import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalCalendarDifference;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDateAddition;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDateArithmetic;
import com.caoccao.qjs4j.builtins.temporal.plain.TemporalDateProperty;
import com.caoccao.qjs4j.builtins.temporal.plain.TemporalPartialDate;
import com.caoccao.qjs4j.builtins.temporal.plain.TemporalPlainTimeConstructor;
import com.caoccao.qjs4j.builtins.temporal.plain.TemporalTimeProperty;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.format.TemporalZonedDateTimeToStringSettings;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundSettings;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalZonedDateTimeOptions;

import java.math.BigInteger;
import java.time.DateTimeException;

/**
 * Implementation of Temporal.ZonedDateTime prototype methods.
 */
public final class TemporalZonedDateTimePrototype {
    private static final TemporalDateArithmetic<JSTemporalZonedDateTime> ARITHMETIC = new TemporalDateArithmetic<>(
            TemporalReceiver.ZONED_DATE_TIME, TemporalDateAddition::apply, TemporalCalendarDifference::between);
    private static final BigInteger NS_PER_HOUR = TemporalConstants.BI_HOUR_NANOSECONDS;
    private static final TemporalReceiver<JSTemporalZonedDateTime> RECEIVER = TemporalReceiver.ZONED_DATE_TIME;

    private TemporalZonedDateTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.shift(context, thisArg, args, 1);
    }

    public static JSValue calendar(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "calendar",
                zonedDateTime -> new JSString(zonedDateTime.getCalendarId().identifier()));
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "calendarId",
                zonedDateTime -> new JSString(zonedDateTime.getCalendarId().identifier()));
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAY.read(context, thisArg, RECEIVER);
    }

    public static JSValue dayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "dayOfWeek",
                zonedDateTime -> JSNumber.of(zonedDateTime.toLocalDateTime().date().dayOfWeek()));
    }

    public static JSValue dayOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAY_OF_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_MONTH.read(context, thisArg, RECEIVER);
    }

    public static JSValue daysInWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInWeek", zonedDateTime -> JSNumber.of(7));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue epochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "epochMilliseconds");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalUtils.epochMilliseconds(zonedDateTime.getEpochNanoseconds()));
    }

    public static JSValue epochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "epochNanoseconds",
                zonedDateTime -> new JSBigInt(zonedDateTime.getEpochNanoseconds()));
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "equals");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalZonedDateTime other;
        if (otherArg instanceof JSTemporalZonedDateTime otherZdt) {
            other = otherZdt;
        } else {
            other = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, otherArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        String receiverTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(context,
                zonedDateTime.getTimeZoneId());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String argumentTimeZoneId = TemporalTimeZone.canonicalizeTimeZoneIdentifierForEquals(context,
                other.getTimeZoneId());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = zonedDateTime.getEpochNanoseconds().equals(other.getEpochNanoseconds())
                && receiverTimeZoneId.equals(argumentTimeZoneId)
                && zonedDateTime.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA.read(context, thisArg, RECEIVER);
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA_YEAR.read(context, thisArg, RECEIVER);
    }

    private static String formatZonedDateTime(JSTemporalZonedDateTime zonedDateTime) {
        String base = formatZonedDateTimeBase(zonedDateTime);
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601) {
            base += "[u-ca=" + zonedDateTime.getCalendarId() + "]";
        }
        return base;
    }

    private static String formatZonedDateTimeBase(JSTemporalZonedDateTime zonedDateTime) {
        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(),
                zonedDateTime.getTimeZoneId());
        String offset = TemporalTimeZone.formatOffsetRoundedToMinute(offsetSeconds);
        return localDateTime + offset + "[" + zonedDateTime.getTimeZoneId() + "]";
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

    public static JSValue getTimeZoneTransition(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "getTimeZoneTransition");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue directionArg = JSValue.argumentOrUndefined(args, 0);
        String direction = getDirectionOption(context, directionArg);
        if (context.hasPendingException() || direction == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger transitionEpochNs;
        if ("next".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getNextTransition(zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
        } else if ("previous".equals(direction)) {
            transitionEpochNs = TemporalTimeZone.getPreviousTransition(zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
        } else {
            context.throwRangeError("Temporal error: Invalid direction: " + direction);
            return JSUndefined.INSTANCE;
        }

        if (transitionEpochNs == null) {
            return JSNull.INSTANCE;
        }
        if ("next".equals(direction)) {
            if (transitionEpochNs.compareTo(zonedDateTime.getEpochNanoseconds()) <= 0) {
                return JSNull.INSTANCE;
            }
        } else if ("previous".equals(direction)) {
            if (transitionEpochNs.compareTo(zonedDateTime.getEpochNanoseconds()) >= 0) {
                return JSNull.INSTANCE;
            }
        }
        if (!TemporalUtils.isValidEpochNanoseconds(transitionEpochNs)) {
            return JSNull.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context, transitionEpochNs, zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.HOUR.read(context, thisArg, RECEIVER);
    }

    public static JSValue hoursInDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "hoursInDay");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalZonedDay day = TemporalZonedDay.containing(context, zonedDateTime);
        return day == null ? JSUndefined.INSTANCE : JSNumber.of(day.length().doubleValue() / NS_PER_HOUR.doubleValue());
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.IN_LEAP_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MICROSECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MILLISECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MINUTE.read(context, thisArg, RECEIVER);
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTH.read(context, thisArg, RECEIVER);
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTH_CODE.read(context, thisArg, RECEIVER);
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTHS_IN_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.NANOSECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue offset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "offset");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(),
                zonedDateTime.getTimeZoneId());
        return new JSString(TemporalTimeZone.formatOffset(offsetSeconds));
    }

    public static JSValue offsetNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "offsetNanoseconds");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(),
                zonedDateTime.getTimeZoneId());
        return JSNumber.of((long) offsetSeconds * 1_000_000_000L);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "round");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalRoundSettings roundSettings = TemporalRoundSettings.parseRoundTo(context, args, TemporalUnit.DAY);
        if (context.hasPendingException() || roundSettings == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger roundedEpochNanoseconds;
        if (roundSettings.smallestUnit() == TemporalUnit.DAY) {
            TemporalZonedDay day = TemporalZonedDay.containing(context, zonedDateTime);
            if (day == null) {
                return JSUndefined.INSTANCE;
            }
            roundedEpochNanoseconds = day.round(zonedDateTime.getEpochNanoseconds(), roundSettings.roundingMode());
        } else {
            try {
                IsoDateTime rounded = roundSettings.roundDateTime(zonedDateTime.toLocalDateTime());
                roundedEpochNanoseconds = rounded.toEpochNs(zonedDateTime.getTimeZoneId());
            } catch (DateTimeException dateTimeException) {
                return TemporalError.INVALID_ISO_DATE.undefined(context);
            }
        }

        if (!TemporalUtils.isValidEpochNanoseconds(roundedEpochNanoseconds)) {
            return TemporalError.NANOSECONDS_OUT_OF_RANGE.undefined(context);
        }

        return JSTemporalZonedDateTime.create(context, roundedEpochNanoseconds, zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.SECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.between(context, thisArg, args, true);
    }

    public static JSValue startOfDay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "startOfDay");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime;
        try {
            localDateTime = zonedDateTime.toLocalDateTime();
        } catch (DateTimeException dateTimeException) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }
        BigInteger epochNs;
        try {
            epochNs = TemporalTimeZone.startOfDayToEpochNs(localDateTime.date(), zonedDateTime.getTimeZoneId());
        } catch (DateTimeException dateTimeException) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            return TemporalError.NANOSECONDS_OUT_OF_RANGE.undefined(context);
        }
        return JSTemporalZonedDateTime.create(context, epochNs, zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.shift(context, thisArg, args, -1);
    }

    public static JSValue timeZoneId(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "timeZoneId",
                zonedDateTime -> new JSString(zonedDateTime.getTimeZoneId()));
    }

    public static JSValue toInstant(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "toInstant");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, zonedDateTime.getEpochNanoseconds());
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "toJSON",
                zonedDateTime -> new JSString(formatZonedDateTime(zonedDateTime)));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "toLocaleString");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalLocaleFormat.format(context, zonedDateTime, args);
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "toPlainDate");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        return JSTemporalPlainDate.create(context, localDateTime.date(), zonedDateTime.getCalendarId());
    }

    public static JSValue toPlainDateTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "toPlainDateTime");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        return JSTemporalPlainDateTime.create(context, localDateTime, zonedDateTime.getCalendarId());
    }

    public static JSValue toPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "toPlainTime");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        return JSTemporalPlainTime.create(context, localDateTime.time());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "toString");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = JSValue.argumentOrUndefined(args, 0);
        TemporalZonedDateTimeToStringSettings toStringSettings = TemporalZonedDateTimeToStringSettings.read(context,
                optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        BigInteger roundedEpochNanoseconds = toStringSettings.roundedEpoch(context,
                zonedDateTime.getEpochNanoseconds());
        if (roundedEpochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(roundedEpochNanoseconds,
                zonedDateTime.getTimeZoneId());
        IsoDateTime localDateTime = IsoDateTime.createFromEpochNsAndOffset(roundedEpochNanoseconds, offsetSeconds);
        StringBuilder stringBuilder = new StringBuilder(toStringSettings.format(localDateTime));
        if ("auto".equals(toStringSettings.offsetOption())) {
            stringBuilder.append(TemporalTimeZone.formatOffsetRoundedToMinute(offsetSeconds));
        }
        if ("auto".equals(toStringSettings.timeZoneNameOption())) {
            stringBuilder.append('[').append(zonedDateTime.getTimeZoneId()).append(']');
        } else if ("critical".equals(toStringSettings.timeZoneNameOption())) {
            stringBuilder.append("[!").append(zonedDateTime.getTimeZoneId()).append(']');
        }

        String result = TemporalUtils.maybeAppendCalendar(stringBuilder.toString(), zonedDateTime.getCalendarId(),
                toStringSettings.calendarNameOption());
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return ARITHMETIC.between(context, thisArg, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue weekOfYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "weekOfYear");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        return JSNumber.of(localDateTime.date().weekOfYear());
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "with");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fieldsObject = TemporalUtils.requirePartialZonedDateTime(context, args);
        if (fieldsObject == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = zonedDateTime.getCalendarId();
        TemporalTimeFields bag = new TemporalTimeFields(context, fieldsObject);
        Integer dayField = bag.integer("day");
        bag.readThroughMinute();
        Integer monthField = bag.integer("month");
        String monthCodeField = bag.string("monthCode");
        bag.readNanosecond();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String offsetField = bag.offset();
        bag.readSecond();
        TemporalPartialDate dateFields = bag.readYearAndEra(calendarId, dayField, monthField, monthCodeField);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dateFields.isEmpty() && !bag.hasTimeField() && offsetField == null) {
            return TemporalError.MISSING_PARTIAL_FIELDS.undefined(context);
        }

        if ((dayField != null && dayField < 1) || (monthField != null && monthField < 1)) {
            return TemporalError.INVALID_ISO_DATE.undefined(context);
        }

        JSValue optionsValue = JSValue.argumentOrUndefined(args, 1);
        TemporalZonedDateTimeOptions withOptions = TemporalZonedDateTimeOptions.parse(context, optionsValue, "prefer");
        if (context.hasPendingException() || withOptions == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        IsoDate mergedDate = dateFields.mergeDate(context, localDateTime.date(), calendarId, withOptions.overflow());
        if (mergedDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime mergedIsoTime = bag.regulate(localDateTime.time(), withOptions.overflow(),
                TemporalError.INVALID_ISO_DATE.message());
        if (mergedIsoTime == null) {
            return JSUndefined.INSTANCE;
        }

        String zone = zonedDateTime.getTimeZoneId();
        Integer offsetSeconds;
        if (offsetField == null) {
            offsetSeconds = TemporalUtils.roundOffsetSecondsToMinute(
                    TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(), zone));
        } else {
            offsetSeconds = TemporalZonedDateTimeConstructor.parsePropertyOffset(context, offsetField);
            if (offsetSeconds == null) {
                return JSUndefined.INSTANCE;
            }
        }
        return TemporalZonedDateTimeConstructor.fromLocalDateTime(context, mergedDate, mergedIsoTime, zone, calendarId,
                offsetSeconds, withOptions);
    }

    public static JSValue withCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.withCalendar(context, thisArg, args, (ctx, zonedDateTime, calendarId) -> JSTemporalZonedDateTime
                .create(ctx, zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId(), calendarId));
    }

    public static JSValue withPlainTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "withPlainTime");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        BigInteger epochNanoseconds;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            JSValue temporalTime = TemporalPlainTimeConstructor.toTemporalTime(context, args[0], JSUndefined.INSTANCE);
            if (context.hasPendingException() || !(temporalTime instanceof JSTemporalPlainTime plainTime)) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime resultLocalDateTime = localDateTime.withTime(plainTime.getIsoTime());
            epochNanoseconds = resultLocalDateTime.toEpochNs(zonedDateTime.getTimeZoneId());
        } else {
            epochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(localDateTime.date(),
                    zonedDateTime.getTimeZoneId());
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            return TemporalError.NANOSECONDS_OUT_OF_RANGE.undefined(context);
        }
        return JSTemporalZonedDateTime.create(context, epochNanoseconds, zonedDateTime.getTimeZoneId(),
                zonedDateTime.getCalendarId());
    }

    public static JSValue withTimeZone(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "withTimeZone");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue tzArg = JSValue.argumentOrUndefined(args, 0);
        if (!(tzArg instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = TemporalOffsetInterpretation.normalizeTimeZoneIdentifier(context, timeZoneString.value());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        try {
            TemporalTimeZone.resolveTimeZone(timeZoneId);
        } catch (DateTimeException invalidTimeZoneException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return JSUndefined.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context, zonedDateTime.getEpochNanoseconds(), timeZoneId,
                zonedDateTime.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue yearOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalZonedDateTime zonedDateTime = RECEIVER.require(context, thisArg, "yearOfWeek");
        if (zonedDateTime == null) {
            return JSUndefined.INSTANCE;
        }
        if (zonedDateTime.getCalendarId() != TemporalCalendarId.ISO8601) {
            return JSUndefined.INSTANCE;
        }
        IsoDateTime localDateTime = zonedDateTime.toLocalDateTime();
        return JSNumber.of(localDateTime.date().yearOfWeek());
    }
}
