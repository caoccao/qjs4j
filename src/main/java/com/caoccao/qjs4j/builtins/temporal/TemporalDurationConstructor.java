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
import java.util.List;
import java.util.Optional;

/**
 * Implementation of Temporal.Duration constructor and static methods.
 */
public final class TemporalDurationConstructor {
    static final String UNIT_DAY = "day";
    static final String UNIT_HOUR = "hour";
    static final String UNIT_MICROSECOND = "microsecond";
    static final String UNIT_MILLISECOND = "millisecond";
    static final String UNIT_MINUTE = "minute";
    static final String UNIT_NANOSECOND = "nanosecond";
    static final String UNIT_SECOND = "second";
    private static final long CALENDAR_UNIT_MAX = TemporalConstants.CALENDAR_UNIT_MAX;
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;
    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS = TemporalConstants.MAX_ABSOLUTE_TIME_NANOSECONDS;
    private static final BigInteger SECOND_NANOSECONDS = TemporalConstants.BI_SECOND_NANOSECONDS;

    private TemporalDurationConstructor() {
    }

    private static long calendarDaysFromRelativeTo(
            JSContext context,
            TemporalDuration durationRecord,
            RelativeToReference relativeToReference) {
        IsoDate relativeDate = relativeToReference.relativeDate();
        try {
            LocalDate startDate = LocalDate.of(relativeDate.year(), relativeDate.month(), relativeDate.day());
            LocalDate endDate = startDate
                    .plusYears(durationRecord.years())
                    .plusMonths(durationRecord.months())
                    .plusWeeks(durationRecord.weeks())
                    .plusDays(durationRecord.days());
            return ChronoUnit.DAYS.between(startDate, endDate);
        } catch (ArithmeticException | DateTimeException rangeException) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return Long.MIN_VALUE;
        }
    }

    private static BigInteger calendarNanosecondsFromRelativeToZoned(
            JSContext context,
            TemporalDuration durationRecord,
            RelativeToReference relativeToReference) {
        BigInteger startEpochNanoseconds = relativeToReference.epochNanoseconds();
        Integer preferredOffsetSeconds = relativeToReference.offsetSeconds();
        String timeZoneId = relativeToReference.timeZoneId();
        if (startEpochNanoseconds == null || timeZoneId == null) {
            return BigInteger.ZERO;
        }
        try {
            IsoDate startDate = relativeToReference.relativeDate();
            IsoTime startTime = relativeToReference.relativeTime();
            LocalDate endDate = LocalDate.of(
                            startDate.year(),
                            startDate.month(),
                            startDate.day())
                    .plusYears(durationRecord.years())
                    .plusMonths(durationRecord.months())
                    .plusWeeks(durationRecord.weeks())
                    .plusDays(durationRecord.days());
            IsoDate endIsoDate = new IsoDate(endDate.getYear(), endDate.getMonthValue(), endDate.getDayOfMonth());
            IsoDateTime endDateTime = new IsoDateTime(endIsoDate, startTime);
            BigInteger endEpochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(
                    endDateTime,
                    timeZoneId,
                    "compatible",
                    preferredOffsetSeconds);
            return endEpochNanoseconds.subtract(startEpochNanoseconds);
        } catch (ArithmeticException | DateTimeException rangeException) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return BigInteger.ZERO;
        }
    }

    /**
     * Temporal.Duration.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue optionsArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        JSTemporalDuration firstDuration = toTemporalDurationObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalDuration secondDuration = toTemporalDurationObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration firstRecord = firstDuration.getDuration();
        TemporalDuration secondRecord = secondDuration.getDuration();

        if (!isDurationRecordInRange(context, firstRecord) || !isDurationRecordInRange(context, secondRecord)) {
            return JSUndefined.INSTANCE;
        }

        RelativeToReference relativeToReference = parseRelativeToOption(context, optionsArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean firstHasCalendarUnits = hasCalendarUnits(firstRecord);
        boolean secondHasCalendarUnits = hasCalendarUnits(secondRecord);
        if ((firstHasCalendarUnits || secondHasCalendarUnits) && relativeToReference == null) {
            if (firstRecord.equals(secondRecord)) {
                return JSNumber.of(0);
            } else {
                context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
                return JSUndefined.INSTANCE;
            }
        }

        BigInteger firstTotalNanoseconds = totalDurationNanoseconds(context, firstRecord, relativeToReference);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        BigInteger secondTotalNanoseconds = totalDurationNanoseconds(context, secondRecord, relativeToReference);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(Integer.signum(firstTotalNanoseconds.compareTo(secondTotalNanoseconds)));
    }

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

    /**
     * Temporal.Duration(years?, months?, weeks?, days?, hours?, minutes?, seconds?, milliseconds?, microseconds?, nanoseconds?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.Duration.");
            return JSUndefined.INSTANCE;
        }

        long years = 0, months = 0, weeks = 0, days = 0;
        long hours = 0, minutes = 0, seconds = 0;
        long milliseconds = 0, microseconds = 0, nanoseconds = 0;

        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            years = parseDurationConstructorArgument(context, args[0]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            months = parseDurationConstructorArgument(context, args[1]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            weeks = parseDurationConstructorArgument(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            days = parseDurationConstructorArgument(context, args[3]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 4 && !(args[4] instanceof JSUndefined)) {
            hours = parseDurationConstructorArgument(context, args[4]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 5 && !(args[5] instanceof JSUndefined)) {
            minutes = parseDurationConstructorArgument(context, args[5]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 6 && !(args[6] instanceof JSUndefined)) {
            seconds = parseDurationConstructorArgument(context, args[6]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 7 && !(args[7] instanceof JSUndefined)) {
            milliseconds = parseDurationConstructorArgument(context, args[7]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 8 && !(args[8] instanceof JSUndefined)) {
            microseconds = parseDurationConstructorArgument(context, args[8]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (args.length > 9 && !(args[9] instanceof JSUndefined)) {
            nanoseconds = parseDurationConstructorArgument(context, args[9]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalDuration record = new TemporalDuration(years, months, weeks, days,
                hours, minutes, seconds, milliseconds, microseconds, nanoseconds);

        if (!record.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return JSUndefined.INSTANCE;
        }
        if (!isDurationRecordInRange(context, record)) {
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "Duration");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createDuration(context, record, resolvedPrototype);
    }

    public static JSTemporalDuration createDuration(JSContext context, TemporalDuration record) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "Duration");
        return createDuration(context, record, prototype);
    }

    static JSTemporalDuration createDuration(JSContext context, TemporalDuration record, JSObject prototype) {
        JSTemporalDuration duration = new JSTemporalDuration(context, record);
        if (prototype != null) {
            duration.setPrototype(prototype);
        }
        return duration;
    }

    static JSValue durationFromFields(JSContext context, JSObject fields) {
        Optional<BigInteger> daysFieldValue = getDurationLikeField(context, fields, "days");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> hoursFieldValue = getDurationLikeField(context, fields, "hours");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> microsecondsFieldValue = getDurationLikeField(context, fields, "microseconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> millisecondsFieldValue = getDurationLikeField(context, fields, "milliseconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> minutesFieldValue = getDurationLikeField(context, fields, "minutes");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> monthsFieldValue = getDurationLikeField(context, fields, "months");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> nanosecondsFieldValue = getDurationLikeField(context, fields, "nanoseconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> secondsFieldValue = getDurationLikeField(context, fields, "seconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> weeksFieldValue = getDurationLikeField(context, fields, "weeks");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        Optional<BigInteger> yearsFieldValue = getDurationLikeField(context, fields, "years");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean hasRecognizedField =
                daysFieldValue.isPresent()
                        || hoursFieldValue.isPresent()
                        || microsecondsFieldValue.isPresent()
                        || millisecondsFieldValue.isPresent()
                        || minutesFieldValue.isPresent()
                        || monthsFieldValue.isPresent()
                        || nanosecondsFieldValue.isPresent()
                        || secondsFieldValue.isPresent()
                        || weeksFieldValue.isPresent()
                        || yearsFieldValue.isPresent();
        if (!hasRecognizedField) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        BigInteger yearsBigInteger = yearsFieldValue.orElse(BigInteger.ZERO);
        BigInteger monthsBigInteger = monthsFieldValue.orElse(BigInteger.ZERO);
        BigInteger weeksBigInteger = weeksFieldValue.orElse(BigInteger.ZERO);
        BigInteger daysBigInteger = daysFieldValue.orElse(BigInteger.ZERO);
        BigInteger hoursBigInteger = hoursFieldValue.orElse(BigInteger.ZERO);
        BigInteger minutesBigInteger = minutesFieldValue.orElse(BigInteger.ZERO);
        BigInteger secondsBigInteger = secondsFieldValue.orElse(BigInteger.ZERO);
        BigInteger millisecondsBigInteger = millisecondsFieldValue.orElse(BigInteger.ZERO);
        BigInteger microsecondsBigInteger = microsecondsFieldValue.orElse(BigInteger.ZERO);
        BigInteger nanosecondsBigInteger = nanosecondsFieldValue.orElse(BigInteger.ZERO);

        BigInteger normalizedMillisecondsBigInteger = millisecondsBigInteger;
        BigInteger normalizedMicrosecondsBigInteger = microsecondsBigInteger;
        BigInteger normalizedNanosecondsBigInteger = nanosecondsBigInteger;
        boolean needsSubsecondNormalization =
                millisecondsBigInteger.abs().bitLength() > 63
                        || microsecondsBigInteger.abs().bitLength() > 63
                        || nanosecondsBigInteger.abs().bitLength() > 63;
        if (needsSubsecondNormalization) {
            BigInteger thousand = BigInteger.valueOf(1_000L);

            BigInteger nanosecondCarryToMicroseconds = normalizedNanosecondsBigInteger.divide(thousand);
            normalizedNanosecondsBigInteger = normalizedNanosecondsBigInteger.remainder(thousand);
            normalizedMicrosecondsBigInteger = normalizedMicrosecondsBigInteger.add(nanosecondCarryToMicroseconds);

            if (normalizedMicrosecondsBigInteger.abs().bitLength() > 63) {
                BigInteger microsecondCarryToMilliseconds = normalizedMicrosecondsBigInteger.divide(thousand);
                normalizedMicrosecondsBigInteger = normalizedMicrosecondsBigInteger.remainder(thousand);
                normalizedMillisecondsBigInteger = normalizedMillisecondsBigInteger.add(microsecondCarryToMilliseconds);
            }

            if (normalizedMillisecondsBigInteger.abs().bitLength() > 63) {
                BigInteger millisecondCarryToSeconds = normalizedMillisecondsBigInteger.divide(thousand);
                normalizedMillisecondsBigInteger = normalizedMillisecondsBigInteger.remainder(thousand);
                secondsBigInteger = secondsBigInteger.add(millisecondCarryToSeconds);
            }
        }

        long years = toLongDurationField(context, yearsBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long months = toLongDurationField(context, monthsBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long weeks = toLongDurationField(context, weeksBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long days = toLongDurationField(context, daysBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long hours = toLongDurationField(context, hoursBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long minutes = toLongDurationField(context, minutesBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long seconds = toLongDurationField(context, secondsBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long milliseconds = toLongDurationField(context, normalizedMillisecondsBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long microseconds = toLongDurationField(context, normalizedMicrosecondsBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long nanoseconds = toLongDurationField(context, normalizedNanosecondsBigInteger);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration record = new TemporalDuration(years, months, weeks, days,
                hours, minutes, seconds, milliseconds, microseconds, nanoseconds);

        if (!record.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return JSUndefined.INSTANCE;
        }
        if (!isDurationRecordInRange(context, record)) {
            return JSUndefined.INSTANCE;
        }

        return createDuration(context, record);
    }

    static JSValue durationFromString(JSContext context, String input) {
        TemporalDuration record = TemporalParser.parseDurationString(context, input);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        if (!record.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return JSUndefined.INSTANCE;
        }
        if (!isDurationRecordInRange(context, record)) {
            return JSUndefined.INSTANCE;
        }

        return createDuration(context, record);
    }

    private static String extractOffsetText(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
        if (timeSeparatorIndex < 0) {
            return null;
        }
        int offsetStart = -1;
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[' || character == 'Z' || character == 'z') {
                break;
            }
            if (character == '+' || character == '-' || character == '\u2212') {
                offsetStart = index;
                break;
            }
        }
        if (offsetStart < 0) {
            return null;
        }
        int offsetEnd = text.indexOf('[', offsetStart);
        if (offsetEnd < 0) {
            offsetEnd = text.length();
        }
        return text.substring(offsetStart, offsetEnd);
    }

    /**
     * Temporal.Duration.from(item)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return toTemporalDuration(context, item);
    }

    private static Optional<BigInteger> getDurationLikeField(JSContext context, JSObject durationLikeObject, String fieldKey) {
        JSValue value = durationLikeObject.get(PropertyKey.fromString(fieldKey));
        if (value instanceof JSUndefined || value == null) {
            return Optional.empty();
        }
        double numericValueAsDouble = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Optional.of(BigInteger.ZERO);
        }
        if (!Double.isFinite(numericValueAsDouble)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Optional.of(BigInteger.ZERO);
        }
        if (numericValueAsDouble != Math.floor(numericValueAsDouble)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Optional.of(BigInteger.ZERO);
        }
        BigInteger numericValue = toBigIntegerFromIntegralDouble(numericValueAsDouble);
        return Optional.of(numericValue);
    }

    private static boolean hasCalendarUnits(TemporalDuration durationRecord) {
        return durationRecord.years() != 0 || durationRecord.months() != 0 || durationRecord.weeks() != 0;
    }

    private static boolean hasOffsetDesignator(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
        if (timeSeparatorIndex < 0) {
            return false;
        }
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[') {
                break;
            }
            if (character == 'Z' || character == 'z' || character == '+' || character == '-' || character == '\u2212') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasZuluDesignator(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
        if (timeSeparatorIndex < 0) {
            return false;
        }
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[') {
                break;
            }
            if (character == 'Z' || character == 'z') {
                return true;
            }
        }
        return false;
    }

    private static boolean isDateTimeWithinRelativeToRange(IsoDate isoDate, IsoTime isoTime) {
        int constrainedSecond = isoTime.second();
        if (constrainedSecond == 60) {
            constrainedSecond = 59;
        }
        LocalDateTime dateTime = LocalDateTime.of(
                isoDate.year(),
                isoDate.month(),
                isoDate.day(),
                isoTime.hour(),
                isoTime.minute(),
                constrainedSecond,
                isoTime.millisecond() * 1_000_000
                        + isoTime.microsecond() * 1_000
                        + isoTime.nanosecond());
        LocalDateTime minDateTime = LocalDateTime.of(-271821, 4, 20, 0, 0, 0, 0);
        LocalDateTime maxDateTime = LocalDateTime.of(275760, 9, 13, 23, 59, 59, 999_999_999);
        return !dateTime.isBefore(minDateTime) && !dateTime.isAfter(maxDateTime);
    }

    private static boolean isDateWithinRelativeToRange(IsoDate isoDate) {
        LocalDate date = LocalDate.of(isoDate.year(), isoDate.month(), isoDate.day());
        LocalDate minDate = LocalDate.of(-271821, 4, 19);
        LocalDate maxDate = LocalDate.of(275760, 9, 13);
        return !date.isBefore(minDate) && !date.isAfter(maxDate);
    }

    private static boolean isDurationRecordInRange(JSContext context, TemporalDuration durationRecord) {
        if (durationRecord.years() > CALENDAR_UNIT_MAX
                || durationRecord.years() < -CALENDAR_UNIT_MAX
                || durationRecord.months() > CALENDAR_UNIT_MAX
                || durationRecord.months() < -CALENDAR_UNIT_MAX
                || durationRecord.weeks() > CALENDAR_UNIT_MAX
                || durationRecord.weeks() < -CALENDAR_UNIT_MAX) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return false;
        }
        BigInteger dayTimeNanoseconds = durationRecord.dayTimeNanoseconds();
        if (dayTimeNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return false;
        }
        return true;
    }

    static boolean isDurationRecordTimeRangeValid(TemporalDuration durationRecord) {
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        return totalNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) <= 0;
    }

    static String largerTemporalUnit(String leftUnit, String rightUnit) {
        if (TemporalUnit.rank(leftUnit) <= TemporalUnit.rank(rightUnit)) {
            return leftUnit;
        } else {
            return rightUnit;
        }
    }

    static TemporalDuration normalizeFloat64RepresentableFields(TemporalDuration durationRecord) {
        return new TemporalDuration(
                toFloat64RepresentableLong(durationRecord.years()),
                toFloat64RepresentableLong(durationRecord.months()),
                toFloat64RepresentableLong(durationRecord.weeks()),
                toFloat64RepresentableLong(durationRecord.days()),
                toFloat64RepresentableLong(durationRecord.hours()),
                toFloat64RepresentableLong(durationRecord.minutes()),
                toFloat64RepresentableLong(durationRecord.seconds()),
                toFloat64RepresentableLong(durationRecord.milliseconds()),
                toFloat64RepresentableLong(durationRecord.microseconds()),
                toFloat64RepresentableLong(durationRecord.nanoseconds()));
    }

    private static boolean offsetMatchesTimeZoneOffsetForString(
            String offsetText,
            int parsedOffsetSeconds,
            int zoneOffsetSeconds) {
        if (TemporalTimeZone.offsetTextIncludesSecondsOrFraction(offsetText)) {
            return parsedOffsetSeconds == zoneOffsetSeconds;
        }
        int roundedZoneOffsetSeconds = roundOffsetSecondsToMinute(zoneOffsetSeconds);
        return parsedOffsetSeconds == roundedZoneOffsetSeconds;
    }

    private static long parseDurationConstructorArgument(JSContext context, JSValue value) {
        double numericValueAsDouble = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(numericValueAsDouble)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        if (numericValueAsDouble != Math.floor(numericValueAsDouble)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        BigInteger numericValue = toBigIntegerFromIntegralDouble(numericValueAsDouble);
        return toLongDurationField(context, numericValue);
    }

    private static RelativeToReference parseRelativeToOption(JSContext context, JSValue optionsValue) {
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

    private static RelativeToReference parseRelativeToPropertyBag(JSContext context, JSObject relativeToObject) {
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
            boolean hasOwnDateField =
                    relativeToObject.hasOwnProperty(PropertyKey.fromString("year"))
                            || relativeToObject.hasOwnProperty(PropertyKey.fromString("month"))
                            || relativeToObject.hasOwnProperty(PropertyKey.fromString("monthCode"))
                            || relativeToObject.hasOwnProperty(PropertyKey.fromString("day"));
            if (!hasOwnDateField) {
                context.throwTypeError("Temporal error: Invalid relativeTo option.");
                return null;
            }
            PropertyKey dayKey = PropertyKey.fromString("day");
            if (relativeToObject.hasOwnProperty(dayKey)) {
                PropertyDescriptor dayPropertyDescriptor = relativeToObject.getOwnPropertyDescriptor(dayKey);
                if (dayPropertyDescriptor != null && dayPropertyDescriptor.isAccessorDescriptor()) {
                    context.throwTypeError("Temporal error: Invalid relativeTo option.");
                    return null;
                }
            }
            PropertyKey yearKey = PropertyKey.fromString("year");
            if (relativeToObject.hasOwnProperty(yearKey)) {
                PropertyDescriptor yearPropertyDescriptor = relativeToObject.getOwnPropertyDescriptor(yearKey);
                if (yearPropertyDescriptor != null && yearPropertyDescriptor.isAccessorDescriptor()) {
                    context.throwTypeError("Temporal error: Invalid relativeTo option.");
                    return null;
                }
            }
        }

        JSValue dayValue = relativeToObject.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return null;
        }
        Long dayOfMonth = toRequiredIntegralLong(context, dayValue);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue hourValue = relativeToObject.get(PropertyKey.fromString("hour"));
        if (context.hasPendingException()) {
            return null;
        }
        Long hour = toOptionalIntegralLong(context, hourValue, 0L);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue microsecondValue = relativeToObject.get(PropertyKey.fromString("microsecond"));
        if (context.hasPendingException()) {
            return null;
        }
        Long microsecond = toOptionalIntegralLong(context, microsecondValue, 0L);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue millisecondValue = relativeToObject.get(PropertyKey.fromString("millisecond"));
        if (context.hasPendingException()) {
            return null;
        }
        Long millisecond = toOptionalIntegralLong(context, millisecondValue, 0L);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue minuteValue = relativeToObject.get(PropertyKey.fromString("minute"));
        if (context.hasPendingException()) {
            return null;
        }
        Long minute = toOptionalIntegralLong(context, minuteValue, 0L);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue monthValue = relativeToObject.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return null;
        }

        Long month = null;
        if (!(monthValue instanceof JSUndefined) && monthValue != null) {
            month = toRequiredIntegralLong(context, monthValue);
            if (context.hasPendingException()) {
                return null;
            }
        }

        JSValue monthCodeValue = relativeToObject.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return null;
        }
        String monthCode = null;
        if (!(monthCodeValue instanceof JSUndefined) && monthCodeValue != null) {
            monthCode = JSTypeConversions.toString(context, monthCodeValue).value();
            if (context.hasPendingException()) {
                return null;
            }
        }

        JSValue nanosecondValue = relativeToObject.get(PropertyKey.fromString("nanosecond"));
        if (context.hasPendingException()) {
            return null;
        }
        Long nanosecond = toOptionalIntegralLong(context, nanosecondValue, 0L);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue offsetValue = relativeToObject.get(PropertyKey.fromString("offset"));
        if (context.hasPendingException()) {
            return null;
        }
        String offsetText = null;
        if (!(offsetValue instanceof JSUndefined) && offsetValue != null) {
            if (offsetValue instanceof JSString offsetString) {
                offsetText = offsetString.value();
            } else if (offsetValue instanceof JSObject) {
                offsetText = JSTypeConversions.toString(context, offsetValue).value();
                if (context.hasPendingException()) {
                    return null;
                }
            } else {
                context.throwTypeError("Temporal error: Offset must be string.");
                return null;
            }
            if (!TemporalTimeZone.isValidOffsetString(offsetText)) {
                context.throwRangeError("Temporal error: Invalid offset string.");
                return null;
            }
        }

        JSValue secondValue = relativeToObject.get(PropertyKey.fromString("second"));
        if (context.hasPendingException()) {
            return null;
        }
        Long second = toOptionalIntegralLong(context, secondValue, 0L);
        if (context.hasPendingException()) {
            return null;
        }

        JSValue timeZoneValue = relativeToObject.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return null;
        }

        JSValue yearValue = relativeToObject.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return null;
        }
        if (yearValue instanceof JSUndefined || yearValue == null) {
            JSValue eraYearValue = relativeToObject.get(PropertyKey.fromString("eraYear"));
            if (context.hasPendingException()) {
                return null;
            }
            if (!(eraYearValue instanceof JSUndefined) && eraYearValue != null) {
                toOptionalIntegralLong(context, eraYearValue, null);
                if (context.hasPendingException()) {
                    return null;
                }
            }
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
        IsoTime relativeTime = new IsoTime(
                hourInt,
                minuteInt,
                secondInt,
                millisecondInt,
                microsecondInt,
                nanosecondInt);
        if (!relativeTime.isValid()) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        IsoDateTime relativeDateTime = new IsoDateTime(isoDate, relativeTime);
        if (timeZoneValue instanceof JSUndefined || timeZoneValue == null) {
            return new RelativeToReference(isoDate, relativeTime, null, null, null);
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
                    BigInteger guessedEpochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, relativeTime, offsetSeconds);
                    zoneOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(guessedEpochNanoseconds, normalizedTimeZoneId);
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
                epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(relativeDateTime, normalizedTimeZoneId);
                referenceOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, normalizedTimeZoneId);
            }
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new RelativeToReference(
                isoDate,
                relativeTime,
                epochNanoseconds,
                normalizedTimeZoneId,
                referenceOffsetSeconds);
    }

    private static RelativeToReference parseRelativeToString(JSContext context, String relativeToText) {
        if (relativeToText.startsWith("-000000")) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        String constrainedRelativeToText = constrainLeapSecond(relativeToText);
        boolean hasTimeZoneAnnotation = relativeToText.contains("[");
        boolean hasTimeComponent = relativeToText.contains("T") || relativeToText.contains("t");
        boolean hasOffsetDesignator = hasOffsetDesignator(relativeToText);

        if (hasTimeZoneAnnotation || (hasTimeComponent && hasOffsetDesignator)) {
            if (!hasTimeZoneAnnotation && hasZuluDesignator(relativeToText)) {
                context.throwRangeError("Temporal error: Invalid relativeTo string.");
                return null;
            }
            if (!hasTimeZoneAnnotation) {
                String offsetText = extractOffsetText(relativeToText);
                if (offsetText != null && !TemporalTimeZone.isValidOffsetString(offsetText)) {
                    context.throwRangeError("Temporal error: Invalid offset string.");
                    return null;
                }
                IsoCalendarDateTime parsedDateTime = TemporalParser.parseDateTimeString(context, constrainedRelativeToText);
                if (parsedDateTime == null || context.hasPendingException()) {
                    return null;
                }
                if (!isDateWithinRelativeToRange(parsedDateTime.date())) {
                    context.throwRangeError("Temporal error: Duration field out of range.");
                    return null;
                }
                return new RelativeToReference(parsedDateTime.date(), parsedDateTime.time(), null, null, null);
            }

            String offsetText = extractOffsetText(relativeToText);
            if (offsetText != null && !TemporalTimeZone.isValidOffsetString(offsetText)) {
                context.throwRangeError("Temporal error: Invalid offset string.");
                return null;
            }
            IsoZonedDateTimeOffset parsedZonedDateTime =
                    TemporalParser.parseZonedDateTimeString(context, constrainedRelativeToText);
            if (parsedZonedDateTime == null || context.hasPendingException()) {
                return null;
            }
            if (!isDateTimeWithinRelativeToRange(parsedZonedDateTime.date(), parsedZonedDateTime.time())) {
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
            boolean hasSecondOrFractionOffset =
                    offsetText != null && TemporalTimeZone.offsetTextIncludesSecondsOrFraction(offsetText);
            BigInteger epochNanoseconds;
            int zoneOffsetSeconds;
            if (offsetText == null && !offsetTimeZoneIdentifier) {
                IsoDateTime parsedIsoDateTime = new IsoDateTime(parsedZonedDateTime.date(), parsedZonedDateTime.time());
                epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(parsedIsoDateTime, timeZoneId, "compatible");
                try {
                    zoneOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, timeZoneId);
                } catch (DateTimeException invalidTimeZoneException) {
                    context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                    return null;
                }
            } else if (offsetText != null && !hasSecondOrFractionOffset && !offsetTimeZoneIdentifier) {
                IsoDateTime parsedIsoDateTime = new IsoDateTime(parsedZonedDateTime.date(), parsedZonedDateTime.time());
                int parsedOffsetSeconds = parsedZonedDateTime.offsetSeconds();
                LocalDateTime parsedLocalDateTime = LocalDateTime.of(
                        parsedIsoDateTime.date().year(),
                        parsedIsoDateTime.date().month(),
                        parsedIsoDateTime.date().day(),
                        parsedIsoDateTime.time().hour(),
                        parsedIsoDateTime.time().minute(),
                        parsedIsoDateTime.time().second(),
                        parsedIsoDateTime.time().millisecond() * 1_000_000
                                + parsedIsoDateTime.time().microsecond() * 1_000
                                + parsedIsoDateTime.time().nanosecond());
                ZoneId zoneId = TemporalTimeZone.resolveTimeZone(timeZoneId);
                List<ZoneOffset> validOffsets = zoneId.getRules().getValidOffsets(parsedLocalDateTime);
                BigInteger selectedEpochNanoseconds = null;
                Integer selectedOffsetSeconds = null;
                for (ZoneOffset validOffset : validOffsets) {
                    int candidateOffsetSeconds = validOffset.getTotalSeconds();
                    if (!offsetMatchesTimeZoneOffsetForString(
                            offsetText,
                            parsedOffsetSeconds,
                            candidateOffsetSeconds)) {
                        continue;
                    }
                    Instant candidateInstant = parsedLocalDateTime.atOffset(validOffset).toInstant();
                    BigInteger candidateEpochNanoseconds = BigInteger.valueOf(candidateInstant.getEpochSecond())
                            .multiply(SECOND_NANOSECONDS)
                            .add(BigInteger.valueOf(candidateInstant.getNano()));
                    if (selectedEpochNanoseconds == null
                            || candidateEpochNanoseconds.compareTo(selectedEpochNanoseconds) < 0) {
                        selectedEpochNanoseconds = candidateEpochNanoseconds;
                        selectedOffsetSeconds = candidateOffsetSeconds;
                    }
                }
                if (selectedEpochNanoseconds != null && selectedOffsetSeconds != null) {
                    epochNanoseconds = selectedEpochNanoseconds;
                    zoneOffsetSeconds = selectedOffsetSeconds;
                } else {
                    epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(parsedIsoDateTime, timeZoneId, "compatible");
                    try {
                        zoneOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, timeZoneId);
                    } catch (DateTimeException invalidTimeZoneException) {
                        context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                        return null;
                    }
                }
            } else {
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(
                        parsedZonedDateTime.date(),
                        parsedZonedDateTime.time(),
                        parsedZonedDateTime.offsetSeconds());
                if (offsetTimeZoneIdentifier) {
                    zoneOffsetSeconds = TemporalTimeZone.parseOffsetSeconds(timeZoneId);
                } else {
                    try {
                        zoneOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, timeZoneId);
                    } catch (DateTimeException invalidTimeZoneException) {
                        context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                        return null;
                    }
                }
            }
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            if (offsetText != null
                    && !offsetMatchesTimeZoneOffsetForString(
                    offsetText,
                    parsedZonedDateTime.offsetSeconds(),
                    zoneOffsetSeconds)) {
                context.throwRangeError("Temporal error: Invalid offset.");
                return null;
            }
            int referenceOffsetSeconds;
            if (offsetText == null) {
                referenceOffsetSeconds = zoneOffsetSeconds;
            } else if (hasSecondOrFractionOffset) {
                referenceOffsetSeconds = parsedZonedDateTime.offsetSeconds();
            } else {
                referenceOffsetSeconds = zoneOffsetSeconds;
            }
            return new RelativeToReference(
                    parsedZonedDateTime.date(),
                    parsedZonedDateTime.time(),
                    epochNanoseconds,
                    timeZoneId,
                    referenceOffsetSeconds);
        }

        if (hasOffsetDesignator) {
            context.throwRangeError("Temporal error: Invalid relativeTo string.");
            return null;
        }

        IsoCalendarDateTime parsedDateTime = TemporalParser.parseDateTimeString(context, constrainedRelativeToText);
        if (parsedDateTime == null || context.hasPendingException()) {
            return null;
        }
        IsoDate isoDate = parsedDateTime.date();
        if (!isDateWithinRelativeToRange(isoDate)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new RelativeToReference(isoDate, parsedDateTime.time(), null, null, null);
    }

    static RelativeToReference parseRelativeToValue(JSContext context, JSValue relativeToValue) {
        if (relativeToValue instanceof JSTemporalPlainDate plainDate) {
            return new RelativeToReference(plainDate.getIsoDate(), IsoTime.MIDNIGHT, null, null, null);
        }
        if (relativeToValue instanceof JSTemporalPlainDateTime plainDateTime) {
            return new RelativeToReference(
                    plainDateTime.getIsoDateTime().date(),
                    plainDateTime.getIsoDateTime().time(),
                    null,
                    null,
                    null);
        }
        if (relativeToValue instanceof JSTemporalZonedDateTime zonedDateTime) {
            IsoDateTime relativeDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return new RelativeToReference(
                    relativeDateTime.date(),
                    relativeDateTime.time(),
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId(),
                    offsetSeconds);
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

    private static int roundOffsetSecondsToMinute(int offsetSeconds) {
        int sign = offsetSeconds < 0 ? -1 : 1;
        int absoluteOffsetSeconds = Math.abs(offsetSeconds);
        int absoluteOffsetMinutes = absoluteOffsetSeconds / 60;
        int remainingSeconds = absoluteOffsetSeconds % 60;
        if (remainingSeconds >= 30) {
            absoluteOffsetMinutes++;
        }
        return sign * absoluteOffsetMinutes * 60;
    }

    private static BigInteger toBigIntegerFromIntegralDouble(double value) {
        long bits = Double.doubleToRawLongBits(value);
        boolean negative = (bits & (1L << 63)) != 0;
        int exponentBits = (int) ((bits >>> 52) & 0x7FFL);
        long mantissaBits = bits & ((1L << 52) - 1);

        long significand;
        int shift;
        if (exponentBits == 0) {
            significand = mantissaBits;
            shift = -1074;
        } else {
            significand = (1L << 52) | mantissaBits;
            shift = exponentBits - 1075;
        }

        BigInteger integerValue = BigInteger.valueOf(significand);
        if (shift > 0) {
            integerValue = integerValue.shiftLeft(shift);
        } else if (shift < 0) {
            integerValue = integerValue.shiftRight(-shift);
        }

        if (negative) {
            return integerValue.negate();
        }
        return integerValue;
    }

    private static long toFloat64RepresentableLong(long value) {
        return (long) ((double) value);
    }

    private static long toLongDurationField(JSContext context, BigInteger value) {
        if (value.bitLength() > 63) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return Long.MIN_VALUE;
        }
        long longValue = value.longValue();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        return longValue;
    }

    private static Long toOptionalIntegralLong(JSContext context, JSValue value, Long defaultValue) {
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
        if (value instanceof JSUndefined || value == null) {
            context.throwTypeError("Temporal error: Invalid relativeTo option.");
            return null;
        }
        return toOptionalIntegralLong(context, value, null);
    }

    /**
     * ToTemporalDuration abstract operation.
     */
    public static JSValue toTemporalDuration(JSContext context, JSValue item) {
        if (item instanceof JSTemporalDuration duration) {
            return createDuration(context, duration.getDuration());
        }
        if (item instanceof JSString itemStr) {
            return durationFromString(context, itemStr.value());
        }
        if (item instanceof JSObject itemObj) {
            return durationFromFields(context, itemObj);
        }
        context.throwTypeError("Temporal error: Must provide a duration.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalDuration toTemporalDurationObject(JSContext context, JSValue item) {
        JSValue result = toTemporalDuration(context, item);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalDuration) result;
    }

    private static BigInteger totalDurationNanoseconds(
            JSContext context,
            TemporalDuration durationRecord,
            RelativeToReference relativeToReference) {
        if (relativeToReference == null) {
            return durationRecord.dayTimeNanoseconds();
        }
        BigInteger calendarNanoseconds;
        if (relativeToReference.epochNanoseconds() != null && relativeToReference.timeZoneId() != null) {
            calendarNanoseconds = calendarNanosecondsFromRelativeToZoned(context, durationRecord, relativeToReference);
        } else {
            long calendarDays = calendarDaysFromRelativeTo(context, durationRecord, relativeToReference);
            if (context.hasPendingException()) {
                return BigInteger.ZERO;
            }
            calendarNanoseconds = BigInteger.valueOf(calendarDays).multiply(DAY_NANOSECONDS);
        }
        if (context.hasPendingException()) {
            return BigInteger.ZERO;
        }
        BigInteger totalNanoseconds = calendarNanoseconds.add(durationRecord.timeNanoseconds());
        if (totalNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return BigInteger.ZERO;
        }
        boolean hasDateUnits =
                durationRecord.years() != 0
                        || durationRecord.months() != 0
                        || durationRecord.weeks() != 0
                        || durationRecord.days() != 0;
        if (hasDateUnits && relativeToReference.epochNanoseconds() != null && relativeToReference.timeZoneId() != null) {
            BigInteger targetEpochNanoseconds = relativeToReference.epochNanoseconds().add(totalNanoseconds);
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(targetEpochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return BigInteger.ZERO;
            }
        }
        return totalNanoseconds;
    }

    record RelativeToReference(
            IsoDate relativeDate,
            IsoTime relativeTime,
            BigInteger epochNanoseconds,
            String timeZoneId,
            Integer offsetSeconds) {
    }
}
