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
        if (optionsValue instanceof JSUndefined || optionsValue == null) {
            return null;
        }
        if (!(optionsValue instanceof JSObject optionsObject)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }
        JSValue relativeToValue = optionsObject.get(PropertyKey.fromString("relativeTo"));
        if (context.hasPendingException()) {
            return null;
        }
        if (relativeToValue instanceof JSUndefined || relativeToValue == null) {
            return null;
        }
        return parseRelativeToValue(context, relativeToValue);
    }

    private static TemporalRelativeTo parseRelativeToPropertyBag(JSContext context, JSObject relativeToObject) {
        JSValue calendarValue = relativeToObject.get(PropertyKey.fromString("calendar"));
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
            boolean hasOwnDateField = relativeToObject.hasOwnProperty(PropertyKey.fromString("year"))
                    || relativeToObject.hasOwnProperty(PropertyKey.fromString("month"))
                    || relativeToObject.hasOwnProperty(PropertyKey.fromString("monthCode"))
                    || relativeToObject.hasOwnProperty(PropertyKey.fromString("day"));
            if (!hasOwnDateField) {
                context.throwTypeError("Temporal error: Invalid relativeTo option.");
                return null;
            }
            if (hasOwnAccessor(relativeToObject, "day") || hasOwnAccessor(relativeToObject, "year")) {
                context.throwTypeError("Temporal error: Invalid relativeTo option.");
                return null;
            }
        }

        TemporalPropertyBag bag = new TemporalPropertyBag(context, relativeToObject);
        Long dayOfMonth = toRequiredIntegralLong(context, bag.value("day"));
        Long hour = toOptionalIntegralLong(context, bag.value("hour"), 0L);
        Long microsecond = toOptionalIntegralLong(context, bag.value("microsecond"), 0L);
        Long millisecond = toOptionalIntegralLong(context, bag.value("millisecond"), 0L);
        Long minute = toOptionalIntegralLong(context, bag.value("minute"), 0L);
        Long month = toOptionalIntegralLong(context, bag.value("month"), null);
        String monthCode = bag.string("monthCode");
        Long nanosecond = toOptionalIntegralLong(context, bag.value("nanosecond"), 0L);
        String offsetText = bag.offset();
        if (context.hasPendingException()) {
            return null;
        }
        if (offsetText != null && !TemporalTimeZone.isValidOffsetString(offsetText)) {
            context.throwRangeError("Temporal error: Invalid offset string.");
            return null;
        }
        Long second = toOptionalIntegralLong(context, bag.value("second"), 0L);
        JSValue timeZoneValue = bag.value("timeZone");
        JSValue yearValue = bag.value("year");
        if (yearValue instanceof JSUndefined || yearValue == null) {
            toOptionalIntegralLong(context, bag.value("eraYear"), null);
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
                context.throwRangeError("Temporal error: Month code out of range.");
                return null;
            }
            if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
                context.throwRangeError("Temporal error: Month code out of range.");
                return null;
            }
            monthNumber = Integer.parseInt(monthCode.substring(1));
            if (monthNumber < 1 || monthNumber > 12) {
                context.throwRangeError("Temporal error: Month code out of range.");
                return null;
            }
        } else {
            context.throwTypeError("Temporal error: Invalid relativeTo option.");
            return null;
        }

        IsoDate isoDate = new IsoDate(year.intValue(), monthNumber, dayOfMonth.intValue());
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
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
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        IsoDateTime relativeDateTime = isoDate.atTime(relativeTime);
        if (timeZoneValue instanceof JSUndefined || timeZoneValue == null) {
            return new TemporalRelativeTo(isoDate, relativeTime, null, null, null);
        }
        if (!(timeZoneValue instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
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
                context.throwRangeError("Temporal error: Invalid time zone: " + normalizedTimeZoneId);
                return null;
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
                    context.throwRangeError("Temporal error: Invalid time zone: " + normalizedTimeZoneId);
                    return null;
                }
            }
            if (zoneOffsetSeconds != offsetSeconds) {
                context.throwRangeError("Temporal error: Invalid offset.");
                return null;
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
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new TemporalRelativeTo(isoDate, relativeTime, epochNanoseconds, normalizedTimeZoneId,
                referenceOffsetSeconds);
    }

    private static TemporalRelativeTo parseRelativeToString(JSContext context, String relativeToText) {
        if (relativeToText.startsWith("-000000")) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        String constrainedRelativeToText = constrainLeapSecond(relativeToText);
        boolean hasTimeZoneAnnotation = relativeToText.contains("[");
        boolean hasTimeComponent = relativeToText.contains("T") || relativeToText.contains("t");
        boolean hasOffsetDesignator = TemporalTimeZone.hasOffsetDesignator(relativeToText);

        if (hasTimeZoneAnnotation || (hasTimeComponent && hasOffsetDesignator)) {
            if (!hasTimeZoneAnnotation && hasZuluDesignator(relativeToText)) {
                context.throwRangeError("Temporal error: Invalid relativeTo string.");
                return null;
            }
            String offsetText = TemporalTimeZone.extractOffsetText(relativeToText);
            if (offsetText != null && !TemporalTimeZone.isValidOffsetString(offsetText)) {
                context.throwRangeError("Temporal error: Invalid offset string.");
                return null;
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
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            String timeZoneId = parsedZonedDateTime.timeZoneId();
            boolean offsetTimeZoneIdentifier = TemporalTimeZone.isValidTimeZoneOffsetWithoutSeconds(timeZoneId);
            if (!offsetTimeZoneIdentifier) {
                try {
                    TemporalTimeZone.resolveTimeZone(timeZoneId);
                } catch (DateTimeException invalidTimeZoneException) {
                    context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                    return null;
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
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return null;
            }
            if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            if (offsetText != null && !TemporalOffsetInterpretation.matches(zoneOffsetSeconds,
                    parsedZonedDateTime.offsetSeconds(), !hasSecondOrFractionOffset)) {
                context.throwRangeError("Temporal error: Invalid offset.");
                return null;
            }
            int referenceOffsetSeconds = hasSecondOrFractionOffset
                    ? parsedZonedDateTime.offsetSeconds()
                    : zoneOffsetSeconds;
            return new TemporalRelativeTo(parsedZonedDateTime.date(), parsedZonedDateTime.time(), epochNanoseconds,
                    timeZoneId, referenceOffsetSeconds);
        }

        if (hasOffsetDesignator) {
            context.throwRangeError("Temporal error: Invalid relativeTo string.");
            return null;
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
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
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
        context.throwTypeError("Temporal error: Invalid relativeTo option.");
        return null;
    }

    private static Long toOptionalIntegralLong(JSContext context, JSValue value, Long defaultValue) {
        if (context.hasPendingException()) {
            return null;
        }
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        if (value instanceof JSFunction) {
            context.throwTypeError("Temporal error: Invalid relativeTo option.");
            return null;
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
        if (value instanceof JSUndefined || value == null) {
            context.throwTypeError("Temporal error: Invalid relativeTo option.");
            return null;
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
            context.throwRangeError("Temporal error: Duration was not valid.");
            return BigInteger.ZERO;
        }
        BigInteger totalNanoseconds = calendarNanoseconds.add(durationRecord.timeNanoseconds());
        if (totalNanoseconds.abs().compareTo(TemporalConstants.MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return BigInteger.ZERO;
        }
        if (zoned && durationRecord.hasAnyDateUnits()) {
            BigInteger targetEpochNanoseconds = relativeToReference.epochNanoseconds().add(totalNanoseconds);
            if (!TemporalUtils.isValidEpochNanoseconds(targetEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return BigInteger.ZERO;
            }
        }
        return totalNanoseconds;
    }

    static TemporalRelativeToOption parseRoundRelativeToOption(JSContext context, JSValue relativeToValue) {
        if (relativeToValue instanceof JSUndefined || relativeToValue == null) {
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
