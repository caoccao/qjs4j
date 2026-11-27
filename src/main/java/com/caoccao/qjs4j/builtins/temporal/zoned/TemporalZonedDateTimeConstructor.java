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

import com.caoccao.qjs4j.builtins.temporal.TemporalPropertyBag;
import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.builtins.temporal.instant.TemporalInstantConstructor;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoMonth;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.iso.IsoDate;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoZonedDateTimeOffset;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalOffsetParts;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalZonedDateTimeOptions;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalZonedDateTimePropertyBagData;

import java.math.BigInteger;
import java.time.*;

/**
 * Implementation of Temporal.ZonedDateTime constructor and static methods.
 */
public final class TemporalZonedDateTimeConstructor {

    private TemporalZonedDateTimeConstructor() {
    }

    /**
     * Temporal.ZonedDateTime.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalReceiver.ZONED_DATE_TIME.compare(context, args,
                (left, right) -> left.getEpochNanoseconds().compareTo(right.getEpochNanoseconds()));
    }

    /**
     * Temporal.ZonedDateTime(epochNanoseconds, timeZone, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!TemporalUtils.requireNewTarget(context, "ZonedDateTime")) {
            return JSUndefined.INSTANCE;
        }

        BigInteger epochNs = TemporalInstantConstructor.readEpochNanoseconds(context,
                JSValue.argumentOrUndefined(args, 0));
        if (epochNs == null) {
            return JSUndefined.INSTANCE;
        }

        if (!(JSValue.argumentOrUndefined(args, 1) instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return JSUndefined.INSTANCE;
        }
        if (timeZoneString.value().indexOf('[') >= 0) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneString.value());
            return JSUndefined.INSTANCE;
        }
        String timeZoneId = TemporalOffsetInterpretation.resolveTimeZoneIdentifier(context, timeZoneString.value());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = TemporalUtils.calendarArgument(context, args, 2);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "ZonedDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalZonedDateTime.create(context, epochNs, timeZoneId, calendarId, resolvedPrototype);
    }

    private static JSValue createZonedDateTimeFromPropertyBag(JSContext context,
            TemporalZonedDateTimePropertyBagData propertyBagData, TemporalZonedDateTimeOptions options) {
        Integer monthFromProperty = propertyBagData.month();
        IsoMonth parsedMonthCode = propertyBagData.parsedMonthCode();
        if (monthFromProperty == null && parsedMonthCode == null) {
            return TemporalError.INVALID_DATE_TIME_ARGUMENT.undefined(context);
        }

        String monthCodeFromProperty = parsedMonthCode == null ? null : parsedMonthCode.monthCode();

        int calendarYear = propertyBagData.year();
        int dayOfMonth = propertyBagData.day();
        int hour = propertyBagData.hour();
        int minute = propertyBagData.minute();
        int second = propertyBagData.second();
        if (second == 60) {
            second = 59;
        }
        int millisecond = propertyBagData.millisecond();
        int microsecond = propertyBagData.microsecond();
        int nanosecond = propertyBagData.nanosecond();

        IsoDate isoDate = IsoDate.calendarDateToIsoDate(context, propertyBagData.calendarId(), calendarYear,
                monthFromProperty, monthCodeFromProperty, dayOfMonth, options.overflow());
        if (context.hasPendingException() || isoDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime isoTime = IsoTime.regulate(context, options.overflow(), TemporalError.INVALID_TIME.message(), hour,
                minute, second, millisecond, microsecond, nanosecond);
        if (isoTime == null) {
            return JSUndefined.INSTANCE;
        }

        return fromLocalDateTime(context, isoDate, isoTime, propertyBagData.timeZoneId(), propertyBagData.calendarId(),
                propertyBagData.offsetSeconds(), options);
    }

    private static JSValue createZonedDateTimeFromString(JSContext context, String input, IsoZonedDateTimeOffset parsed,
            TemporalZonedDateTimeOptions options) {
        String timeZoneId = TemporalOffsetInterpretation.resolveTimeZoneIdentifier(context, parsed.timeZoneId());
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }

        int timeSeparatorIndex = findDateTimeSeparatorIndex(input);
        boolean hasExplicitOffset = TemporalTimeZone.hasOffsetDesignator(input, timeSeparatorIndex);
        boolean hasZuluOffset = TemporalTimeZone.hasZuluDesignator(input, timeSeparatorIndex);
        boolean hasTimeComponent = timeSeparatorIndex >= 0;
        if (!hasTimeComponent && !hasExplicitOffset) {
            BigInteger epochNanoseconds;
            try {
                epochNanoseconds = TemporalTimeZone.startOfDayToEpochNs(parsed.date(), timeZoneId);
            } catch (DateTimeException dateTimeException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return JSUndefined.INSTANCE;
            }
            if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
                return TemporalError.NANOSECONDS_OUT_OF_RANGE.undefined(context);
            }
            return JSTemporalZonedDateTime.create(context, epochNanoseconds, timeZoneId, parsed.calendarId());
        }
        boolean offsetIncludesSecondsOrFraction = false;
        if (hasExplicitOffset && !hasZuluOffset) {
            String offsetText = TemporalTimeZone.extractOffsetText(input, timeSeparatorIndex);
            if (offsetText != null) {
                offsetIncludesSecondsOrFraction = TemporalTimeZone.offsetTextIncludesSecondsOrFraction(offsetText);
            }
        }

        BigInteger epochNanoseconds = TemporalOffsetInterpretation.interpretOffset(context, parsed.date(),
                parsed.time(), timeZoneId, parsed.offsetSeconds(), hasExplicitOffset, hasZuluOffset, true,
                offsetIncludesSecondsOrFraction, options);
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return JSTemporalZonedDateTime.create(context, epochNanoseconds, timeZoneId, parsed.calendarId());
    }

    private static int findDateTimeSeparatorIndex(String text) {
        int annotationStart = text.indexOf('[');
        int searchEnd = annotationStart >= 0 ? annotationStart : text.length();
        for (int index = 0; index < searchEnd; index++) {
            char character = text.charAt(index);
            if (character == 'T' || character == 't') {
                return index;
            }
        }
        return -1;
    }

    /**
     * Temporal.ZonedDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);

        return toTemporalZonedDateTime(context, item, optionsArg);
    }

    static JSValue fromLocalDateTime(JSContext context, IsoDate isoDate, IsoTime isoTime, String timeZoneId,
            TemporalCalendarId calendar, Integer offsetSeconds, TemporalZonedDateTimeOptions options) {
        BigInteger epochNanoseconds = TemporalOffsetInterpretation.interpretOffset(context, isoDate, isoTime,
                timeZoneId, offsetSeconds, offsetSeconds != null, false, false, false, options);
        if (context.hasPendingException() || epochNanoseconds == null) {
            return JSUndefined.INSTANCE;
        }

        return JSTemporalZonedDateTime.create(context, epochNanoseconds, timeZoneId, calendar);
    }

    static Integer parsePropertyOffset(JSContext context, String text) {
        TemporalOffsetParts parts = TemporalTimeZone.parseOffsetParts(text);
        if (parts == null || !parts.hasValidHoursAndMinutes() || !parts.hasZeroSubMinutePart()) {
            return TemporalError.INVALID_OFFSET_STRING.reject(context);
        }
        return parts.totalSeconds();
    }

    private static TemporalZonedDateTimePropertyBagData parseZonedDateTimePropertyBag(JSContext context,
            JSObject itemObject) {
        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        JSValue calendarValue = itemObject.get(PropertyKey.CALENDAR);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalCalendarId.createFromCalendarValue(context, calendarValue);
            if (context.hasPendingException() || calendarId == null) {
                return null;
            }
        }

        JSValue dayValue = itemObject.get(PropertyKey.DAY);
        if (context.hasPendingException()) {
            return null;
        }
        Long dayOfMonth = toRequiredIntegralLong(context, dayValue);
        if (context.hasPendingException() || dayOfMonth == null) {
            return null;
        }

        TemporalPropertyBag bag = new TemporalPropertyBag(context, itemObject);
        int hour = bag.integralOrZeroByKey(PropertyKey.HOUR);
        int microsecond = bag.integralOrZeroByKey(PropertyKey.MICROSECOND);
        int millisecond = bag.integralOrZeroByKey(PropertyKey.MILLISECOND);
        int minute = bag.integralOrZeroByKey(PropertyKey.MINUTE);
        Integer month = bag.integralByKey(PropertyKey.MONTH);
        IsoMonth parsedMonthCode = bag.monthCode();
        int nanosecond = bag.integralOrZeroByKey(PropertyKey.NANOSECOND);
        if (context.hasPendingException()) {
            return null;
        }

        String offsetText = bag.offset();
        if (context.hasPendingException()) {
            return null;
        }
        Integer offsetSeconds = null;
        if (offsetText != null) {
            offsetSeconds = parsePropertyOffset(context, offsetText);
            if (offsetSeconds == null) {
                return null;
            }
        }

        int second = bag.integralOrZeroByKey(PropertyKey.SECOND);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue timeZoneValue = itemObject.get(PropertyKey.TIME_ZONE);
        if (context.hasPendingException()) {
            return null;
        }
        String timeZoneId = toTemporalTimeZoneId(context, timeZoneValue);
        if (context.hasPendingException() || timeZoneId == null) {
            return null;
        }

        Integer year = bag.integralByKey(PropertyKey.YEAR);
        if (calendarId.hasEra()) {
            String era = bag.stringByKey(PropertyKey.ERA);
            Integer eraYear = bag.integralByKey(PropertyKey.ERA_YEAR);
            if (!context.hasPendingException()) {
                year = TemporalUtils.resolveEraYear(context, calendarId, year, era, eraYear,
                        TemporalError.INVALID_DATE_TIME_ARGUMENT.message());
            }
        }
        if (context.hasPendingException()) {
            return null;
        }
        if (year == null || (month == null && parsedMonthCode == null)) {
            return TemporalError.INVALID_DATE_TIME_ARGUMENT.reject(context);
        }

        return new TemporalZonedDateTimePropertyBagData(calendarId, year, month, parsedMonthCode, dayOfMonth.intValue(),
                hour, minute, second, millisecond, microsecond, nanosecond, timeZoneId, offsetSeconds);
    }

    private static Long toRequiredIntegralLong(JSContext context, JSValue value) {
        if (JSTypeChecking.isUndefinedOrMissing(value)) {
            return TemporalError.INVALID_DATE_TIME_ARGUMENT.reject(context);
        }

        long longValue = TemporalUtils.toLongIfIntegral(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return longValue;
    }

    private static String toTemporalTimeZoneId(JSContext context, JSValue timeZoneValue) {
        if (JSTypeChecking.isUndefinedOrMissing(timeZoneValue)) {
            return TemporalError.INVALID_DATE_TIME_ARGUMENT.reject(context);
        }

        String timeZoneId;
        if (timeZoneValue instanceof JSTemporalZonedDateTime zonedDateTime) {
            timeZoneId = zonedDateTime.getTimeZoneId();
        } else if (timeZoneValue instanceof JSString timeZoneString) {
            timeZoneId = TemporalOffsetInterpretation.normalizeTimeZoneIdentifier(context, timeZoneString.value());
            if (context.hasPendingException() || timeZoneId == null) {
                return null;
            }
        } else {
            return context.rejectTypeError("Temporal error: Time zone must be string.");
        }

        if (!TemporalOffsetInterpretation.isOffsetTimeZoneIdentifier(timeZoneId)) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (DateTimeException invalidTimeZoneException) {
                return context.rejectRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            }
        }

        return timeZoneId;
    }

    public static JSValue toTemporalZonedDateTime(JSContext context, JSValue item) {
        return toTemporalZonedDateTime(context, item, JSUndefined.INSTANCE);
    }

    private static JSValue toTemporalZonedDateTime(JSContext context, JSValue item, JSValue optionsArg) {
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalZonedDateTimeOptions options = TemporalZonedDateTimeOptions.parse(context, optionsArg, "reject");
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }
            return JSTemporalZonedDateTime.create(context, zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId(), zonedDateTime.getCalendarId());
        }

        if (item instanceof JSObject itemObject) {
            TemporalZonedDateTimePropertyBagData propertyBagData = parseZonedDateTimePropertyBag(context, itemObject);
            if (context.hasPendingException() || propertyBagData == null) {
                return JSUndefined.INSTANCE;
            }

            TemporalZonedDateTimeOptions options = TemporalZonedDateTimeOptions.parse(context, optionsArg, "reject");
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }

            return createZonedDateTimeFromPropertyBag(context, propertyBagData, options);
        }

        if (item instanceof JSString zonedDateTimeString) {
            IsoZonedDateTimeOffset parsed = IsoZonedDateTimeOffset.parseZonedDateTimeString(context,
                    zonedDateTimeString.value());
            if (context.hasPendingException() || parsed == null) {
                return JSUndefined.INSTANCE;
            }

            TemporalZonedDateTimeOptions options = TemporalZonedDateTimeOptions.parse(context, optionsArg, "reject");
            if (context.hasPendingException() || options == null) {
                return JSUndefined.INSTANCE;
            }

            return createZonedDateTimeFromString(context, zonedDateTimeString.value(), parsed, options);
        }

        return TemporalError.INVALID_DATE_TIME_ARGUMENT.undefined(context);
    }

    public static JSTemporalZonedDateTime toTemporalZonedDateTimeObject(JSContext context, JSValue item) {
        return TemporalReceiver.ZONED_DATE_TIME.convert(context, item);
    }

}
