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

package com.caoccao.qjs4j.builtins.temporal.duration;

import com.caoccao.qjs4j.builtins.temporal.TemporalPropertyBag;
import com.caoccao.qjs4j.builtins.temporal.zoned.TemporalOffsetInterpretation;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;
import com.caoccao.qjs4j.core.temporal.duration.TemporalRelativeToOption;
import com.caoccao.qjs4j.core.temporal.iso.*;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;

import java.math.BigInteger;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** The plain or zoned starting point for duration comparison, rounding, and totals. */
record TemporalRelativeTo(IsoDate relativeDate, IsoTime relativeTime, BigInteger epochNanoseconds, String timeZoneId,
        Integer offsetSeconds) {

    private static String constrainLeapSecond(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
        if (timeSeparatorIndex < 0) {
            return text;
        }
        int firstColonIndex = text.indexOf(':', timeSeparatorIndex + 1);
        if (firstColonIndex < 0) {
            return text;
        }
        int secondColonIndex = text.indexOf(':', firstColonIndex + 1);
        if (secondColonIndex < 0 || secondColonIndex + 2 >= text.length()) {
            return text;
        }
        if (text.charAt(secondColonIndex + 1) == '6' && text.charAt(secondColonIndex + 2) == '0') {
            return text.substring(0, secondColonIndex + 1) + "59" + text.substring(secondColonIndex + 3);
        }
        return text;
    }

    private static boolean hasOwnAccessor(JSObject object, String name) {
        PropertyKey key = PropertyKey.fromString(name);
        if (!object.hasOwnProperty(key)) {
            return false;
        }
        PropertyDescriptor desc = object.getOwnPropertyDescriptor(key);
        return desc != null && desc.isAccessorDescriptor();
    }

    private static boolean hasZuluDesignator(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
        return TemporalTimeZone.hasZuluDesignator(text, timeSeparatorIndex);
    }

    static TemporalRelativeTo parseRelativeToOption(JSContext context, JSValue optionsValue) {
        if (JSTypeChecking.isUndefinedOrMissing(optionsValue)) {
            return null;
        }
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return TemporalError.INVALID_OPTIONS.reject(context);
        }
        JSValue relativeToValue = optionsObject.get(PropertyKey.fromString("relativeTo"));
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeChecking.isUndefinedOrMissing(relativeToValue)) {
            return null;
        }
        return parseRelativeToValue(context, relativeToValue);
    }

    private static TemporalRelativeTo parseRelativeToPropertyBag(JSContext context, JSObject relativeToObject) {
        JSValue calendarValue = relativeToObject.get(PropertyKey.CALENDAR);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            TemporalCalendarId.createFromCalendarValue(context, calendarValue);
            if (context.hasPendingException()) {
                return null;
            }
        }

        if (!(relativeToObject instanceof JSProxy)) {
            boolean hasOwnDateField = relativeToObject.hasOwnProperty(PropertyKey.YEAR)
                    || relativeToObject.hasOwnProperty(PropertyKey.MONTH)
                    || relativeToObject.hasOwnProperty(PropertyKey.MONTH_CODE)
                    || relativeToObject.hasOwnProperty(PropertyKey.DAY);
            if (!hasOwnDateField) {
                return TemporalError.INVALID_RELATIVE_TO.reject(context);
            }
            if (hasOwnAccessor(relativeToObject, "day") || hasOwnAccessor(relativeToObject, "year")) {
                return TemporalError.INVALID_RELATIVE_TO.reject(context);
            }
        }

        TemporalPropertyBag bag = new TemporalPropertyBag(context, relativeToObject);
        Long dayOfMonth = toRequiredIntegralLong(context, bag.valueByKey(PropertyKey.DAY));
        Long hour = toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.HOUR), 0L);
        Long microsecond = toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.MICROSECOND), 0L);
        Long millisecond = toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.MILLISECOND), 0L);
        Long minute = toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.MINUTE), 0L);
        Long month = toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.MONTH), null);
        String monthCode = bag.stringByKey(PropertyKey.MONTH_CODE);
        Long nanosecond = toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.NANOSECOND), 0L);
        String offsetText = bag.offset();
        if (context.hasPendingException()) {
            return null;
        }
        if (offsetText != null && !TemporalTimeZone.isValidOffsetString(offsetText)) {
            return TemporalError.INVALID_OFFSET_STRING.reject(context);
        }
        Long second = toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.SECOND), 0L);
        JSValue timeZoneValue = bag.valueByKey(PropertyKey.TIME_ZONE);
        JSValue yearValue = bag.valueByKey(PropertyKey.YEAR);
        if (JSTypeChecking.isUndefinedOrMissing(yearValue)) {
            toOptionalIntegralLong(context, bag.valueByKey(PropertyKey.ERA_YEAR), null);
        }
        Long year = toRequiredIntegralLong(context, yearValue);
        if (context.hasPendingException()) {
            return null;
        }

        int monthNumber;
        if (month != null) {
            monthNumber = month.intValue();
        } else if (monthCode != null) {
            if (monthCode.length() != 3 || monthCode.charAt(0) != 'M') {
                return TemporalError.MONTH_CODE_OUT_OF_RANGE.reject(context);
            }
            if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
                return TemporalError.MONTH_CODE_OUT_OF_RANGE.reject(context);
            }
            monthNumber = Integer.parseInt(monthCode.substring(1));
            if (monthNumber < 1 || monthNumber > 12) {
                return TemporalError.MONTH_CODE_OUT_OF_RANGE.reject(context);
            }
        } else {
            return TemporalError.INVALID_RELATIVE_TO.reject(context);
        }

        IsoDate isoDate = new IsoDate(year.intValue(), monthNumber, dayOfMonth.intValue());
        if (!isoDate.isValid()) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }

        int hourInt = hour.intValue();
        int minuteInt = minute.intValue();
        int secondInt = second.intValue();
        if (secondInt == 60) {
            secondInt = 59;
        }
        int millisecondInt = millisecond.intValue();
        int microsecondInt = microsecond.intValue();
        int nanosecondInt = nanosecond.intValue();
        IsoTime relativeTime = new IsoTime(hourInt, minuteInt, secondInt, millisecondInt, microsecondInt,
                nanosecondInt);
        if (!relativeTime.isValid()) {
            return TemporalError.INVALID_TIME.reject(context);
        }
        IsoDateTime relativeDateTime = isoDate.atTime(relativeTime);
        if (JSTypeChecking.isUndefinedOrMissing(timeZoneValue)) {
            return new TemporalRelativeTo(isoDate, relativeTime, null, null, null);
        }
        if (!(timeZoneValue instanceof JSString timeZoneString)) {
            return context.rejectTypeError("Temporal error: Time zone must be string.");
        }
        String timeZoneText = timeZoneString.value();
        String normalizedTimeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneText);
        if (context.hasPendingException()) {
            return null;
        }
        boolean offsetTimeZoneIdentifier = TemporalTimeZone.isValidTimeZoneOffsetWithoutSeconds(normalizedTimeZoneId);
        if (!offsetTimeZoneIdentifier) {
            try {
                TemporalTimeZone.resolveTimeZone(normalizedTimeZoneId);
            } catch (DateTimeException invalidTimeZoneException) {
                return context.rejectRangeError("Temporal error: Invalid time zone: " + normalizedTimeZoneId);
            }
        }

        BigInteger epochNanoseconds;
        Integer referenceOffsetSeconds;
        if (offsetText != null) {
            int offsetSeconds = TemporalTimeZone.parseOffsetSeconds(offsetText);
            int zoneOffsetSeconds;
            if (offsetTimeZoneIdentifier) {
                zoneOffsetSeconds = TemporalTimeZone.parseOffsetSeconds(normalizedTimeZoneId);
            } else {
                try {
                    BigInteger guessedEpochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, relativeTime,
                            offsetSeconds);
                    zoneOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(guessedEpochNanoseconds,
                            normalizedTimeZoneId);
                } catch (DateTimeException invalidTimeZoneException) {
                    return context.rejectRangeError("Temporal error: Invalid time zone: " + normalizedTimeZoneId);
                }
            }
            if (zoneOffsetSeconds != offsetSeconds) {
                return TemporalError.INVALID_OFFSET.reject(context);
            }
            epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, relativeTime, offsetSeconds);
            referenceOffsetSeconds = offsetSeconds;
        } else {
            if (offsetTimeZoneIdentifier) {
                int offsetSeconds = TemporalTimeZone.parseOffsetSeconds(normalizedTimeZoneId);
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, relativeTime, offsetSeconds);
                referenceOffsetSeconds = offsetSeconds;
            } else {
                epochNanoseconds = relativeDateTime.toEpochNs(normalizedTimeZoneId);
                referenceOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, normalizedTimeZoneId);
            }
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.reject(context);
        }
        return new TemporalRelativeTo(isoDate, relativeTime, epochNanoseconds, normalizedTimeZoneId,
                referenceOffsetSeconds);
    }

    private static TemporalRelativeTo parseRelativeToString(JSContext context, String relativeToText) {
        if (relativeToText.startsWith("-000000")) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        String constrainedRelativeToText = constrainLeapSecond(relativeToText);
        boolean hasTimeZoneAnnotation = relativeToText.contains("[");
        boolean hasTimeComponent = relativeToText.contains("T") || relativeToText.contains("t");
        boolean hasOffsetDesignator = TemporalTimeZone.hasOffsetDesignator(relativeToText);

        if (hasTimeZoneAnnotation || (hasTimeComponent && hasOffsetDesignator)) {
            if (!hasTimeZoneAnnotation && hasZuluDesignator(relativeToText)) {
                return TemporalError.INVALID_RELATIVE_TO_STRING.reject(context);
            }
            String offsetText = TemporalTimeZone.extractOffsetText(relativeToText);
            if (offsetText != null && !TemporalTimeZone.isValidOffsetString(offsetText)) {
                return TemporalError.INVALID_OFFSET_STRING.reject(context);
            }
            if (!hasTimeZoneAnnotation) {
                return parsePlainDateTime(context, constrainedRelativeToText);
            }
            IsoZonedDateTimeOffset parsedZonedDateTime = IsoZonedDateTimeOffset.parseZonedDateTimeString(context,
                    constrainedRelativeToText);
            if (parsedZonedDateTime == null || context.hasPendingException()) {
                return null;
            }
            if (!parsedZonedDateTime.date().isWithinInstantDateTimeRange(parsedZonedDateTime.time())) {
                return TemporalError.DURATION_FIELD_OUT_OF_RANGE.reject(context);
            }
            String timeZoneId = parsedZonedDateTime.timeZoneId();
            boolean offsetTimeZoneIdentifier = TemporalTimeZone.isValidTimeZoneOffsetWithoutSeconds(timeZoneId);
            if (!offsetTimeZoneIdentifier) {
                try {
                    TemporalTimeZone.resolveTimeZone(timeZoneId);
                } catch (DateTimeException invalidTimeZoneException) {
                    return context.rejectRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                }
            }
            boolean hasSecondOrFractionOffset = offsetText != null
                    && TemporalTimeZone.offsetTextIncludesSecondsOrFraction(offsetText);
            IsoDateTime local = parsedZonedDateTime.date().atTime(parsedZonedDateTime.time());
            BigInteger epochNanoseconds;
            int zoneOffsetSeconds;
            try {
                if (offsetText == null && !offsetTimeZoneIdentifier) {
                    epochNanoseconds = local.toEpochNs(timeZoneId, "compatible");
                } else if (offsetText != null && !hasSecondOrFractionOffset && !offsetTimeZoneIdentifier) {
                    ZoneOffset matched = TemporalOffsetInterpretation.matchingOffset(local.toLocalDateTime(),
                            timeZoneId, parsedZonedDateTime.offsetSeconds(), true);
                    epochNanoseconds = matched == null
                            ? local.toEpochNs(timeZoneId, "compatible")
                            : TemporalTimeZone.utcDateTimeToEpochNs(local.date(), local.time(),
                                    matched.getTotalSeconds());
                } else {
                    epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(local.date(), local.time(),
                            parsedZonedDateTime.offsetSeconds());
                }
                zoneOffsetSeconds = offsetTimeZoneIdentifier
                        ? TemporalTimeZone.parseOffsetSeconds(timeZoneId)
                        : TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, timeZoneId);
            } catch (DateTimeException exception) {
                return context.rejectRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            }
            if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
                return TemporalError.DURATION_FIELD_OUT_OF_RANGE.reject(context);
            }
            if (offsetText != null && !TemporalOffsetInterpretation.matches(zoneOffsetSeconds,
                    parsedZonedDateTime.offsetSeconds(), !hasSecondOrFractionOffset)) {
                return TemporalError.INVALID_OFFSET.reject(context);
            }
            int referenceOffsetSeconds = hasSecondOrFractionOffset
                    ? parsedZonedDateTime.offsetSeconds()
                    : zoneOffsetSeconds;
            return new TemporalRelativeTo(parsedZonedDateTime.date(), parsedZonedDateTime.time(), epochNanoseconds,
                    timeZoneId, referenceOffsetSeconds);
        }

        if (hasOffsetDesignator) {
            return TemporalError.INVALID_RELATIVE_TO_STRING.reject(context);
        }

        return parsePlainDateTime(context, constrainedRelativeToText);
    }

    private static TemporalRelativeTo parsePlainDateTime(JSContext context, String constrainedRelativeToText) {
        IsoCalendarDateTime parsedDateTime = IsoCalendarDateTime.parseDateTimeString(context,
                constrainedRelativeToText);
        if (parsedDateTime == null || context.hasPendingException()) {
            return null;
        }
        IsoDate isoDate = parsedDateTime.date();
        if (!isoDate.isWithinSupportedRange()) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.reject(context);
        }
        return new TemporalRelativeTo(isoDate, parsedDateTime.time(), null, null, null);
    }

    static TemporalRelativeTo parseRelativeToValue(JSContext context, JSValue relativeToValue) {
        if (relativeToValue instanceof JSTemporalPlainDate plainDate) {
            return new TemporalRelativeTo(plainDate.getIsoDate(), IsoTime.MIDNIGHT, null, null, null);
        }
        if (relativeToValue instanceof JSTemporalPlainDateTime plainDateTime) {
            return new TemporalRelativeTo(plainDateTime.getIsoDateTime().date(), plainDateTime.getIsoDateTime().time(),
                    null, null, null);
        }
        if (relativeToValue instanceof JSTemporalZonedDateTime zonedDateTime) {
            IsoDateTime relativeDateTime = IsoDateTime
                    .createFromEpochNsAndTimeZoneId(zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId());
            int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return new TemporalRelativeTo(relativeDateTime.date(), relativeDateTime.time(),
                    zonedDateTime.getEpochNanoseconds(), zonedDateTime.getTimeZoneId(), offsetSeconds);
        }

        if (relativeToValue instanceof JSString relativeToString) {
            return parseRelativeToString(context, relativeToString.value());
        }

        if (relativeToValue instanceof JSObject relativeToObject) {
            return parseRelativeToPropertyBag(context, relativeToObject);
        }
        return TemporalError.INVALID_RELATIVE_TO.reject(context);
    }

    private static Long toOptionalIntegralLong(JSContext context, JSValue value, Long defaultValue) {
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeChecking.isUndefinedOrMissing(value)) {
            return defaultValue;
        }
        if (value instanceof JSFunction) {
            return TemporalError.INVALID_RELATIVE_TO.reject(context);
        }
        long numericValue = TemporalUtils.toLongIfIntegral(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return numericValue;
    }

    private static Long toRequiredIntegralLong(JSContext context, JSValue value) {
        if (context.hasPendingException()) {
            return null;
        }
        if (JSTypeChecking.isUndefinedOrMissing(value)) {
            return TemporalError.INVALID_RELATIVE_TO.reject(context);
        }
        return toOptionalIntegralLong(context, value, null);
    }

    static BigInteger totalDurationNanoseconds(JSContext context, TemporalDuration durationRecord,
            TemporalRelativeTo relativeToReference) {
        if (relativeToReference == null) {
            return durationRecord.dayTimeNanoseconds();
        }
        IsoDate date = relativeToReference.relativeDate();
        boolean zoned = relativeToReference.epochNanoseconds() != null && relativeToReference.timeZoneId() != null;
        BigInteger calendarNanoseconds;
        try {
            LocalDate start = LocalDate.of(date.year(), date.month(), date.day());
            LocalDate end = start.plusYears(durationRecord.years()).plusMonths(durationRecord.months())
                    .plusWeeks(durationRecord.weeks()).plusDays(durationRecord.days());
            if (zoned) {
                IsoDate endDate = new IsoDate(end.getYear(), end.getMonthValue(), end.getDayOfMonth());
                BigInteger endEpoch = endDate.atTime(relativeToReference.relativeTime())
                        .toEpochNs(relativeToReference.timeZoneId(), "compatible", relativeToReference.offsetSeconds());
                calendarNanoseconds = endEpoch.subtract(relativeToReference.epochNanoseconds());
            } else {
                calendarNanoseconds = BigInteger.valueOf(ChronoUnit.DAYS.between(start, end))
                        .multiply(TemporalConstants.BI_DAY_NANOSECONDS);
            }
        } catch (ArithmeticException | DateTimeException e) {
            TemporalError.INVALID_DURATION.raise(context);
            return BigInteger.ZERO;
        }
        BigInteger totalNanoseconds = calendarNanoseconds.add(durationRecord.timeNanoseconds());
        if (totalNanoseconds.abs().compareTo(TemporalConstants.MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            TemporalError.DURATION_FIELD_OUT_OF_RANGE.raise(context);
            return BigInteger.ZERO;
        }
        if (zoned && durationRecord.hasAnyDateUnits()) {
            BigInteger targetEpochNanoseconds = relativeToReference.epochNanoseconds().add(totalNanoseconds);
            if (!TemporalUtils.isValidEpochNanoseconds(targetEpochNanoseconds)) {
                TemporalError.DURATION_FIELD_OUT_OF_RANGE.raise(context);
                return BigInteger.ZERO;
            }
        }
        return totalNanoseconds;
    }

    static TemporalRelativeToOption parseRoundRelativeToOption(JSContext context, JSValue relativeToValue) {
        if (JSTypeChecking.isUndefinedOrMissing(relativeToValue)) {
            return null;
        }
        TemporalRelativeTo relativeToReference = parseRelativeToValue(context, relativeToValue);
        if (context.hasPendingException() || relativeToReference == null) {
            return null;
        }
        if (relativeToReference.epochNanoseconds() == null || relativeToReference.timeZoneId() == null) {
            LocalDateTime startDateTime = relativeToReference.relativeDate().atTime(relativeToReference.relativeTime())
                    .toLocalDateTime();
            return new TemporalRelativeToOption(startDateTime, false, null, null, null);
        }
        IsoDateTime localIsoDateTime;
        if (TemporalUtils.isOffsetTimeZoneIdentifier(relativeToReference.timeZoneId())) {
            int offsetSeconds = TemporalUtils.parseOffsetSecondsFromTimeZoneId(relativeToReference.timeZoneId());
            IsoDateTime utcDateTime = IsoDateTime.createByEpochNs(relativeToReference.epochNanoseconds());
            LocalDateTime utcLocalDateTime = utcDateTime.toLocalDateTime();
            LocalDateTime offsetLocalDateTime = utcLocalDateTime.plusSeconds(offsetSeconds);
            localIsoDateTime = IsoDateTime.createFromLocalDateTime(offsetLocalDateTime);
        } else {
            localIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(relativeToReference.epochNanoseconds(),
                    relativeToReference.timeZoneId());
        }
        LocalDateTime startDateTime = localIsoDateTime.toLocalDateTime();
        return new TemporalRelativeToOption(startDateTime, true, relativeToReference.epochNanoseconds(),
                relativeToReference.timeZoneId(), relativeToReference.offsetSeconds());
    }
}
