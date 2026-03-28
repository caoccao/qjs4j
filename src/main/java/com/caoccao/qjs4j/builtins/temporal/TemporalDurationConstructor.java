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
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final long CALENDAR_UNIT_MAX = 4_294_967_295L;
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final Pattern OFFSET_BASIC_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2})(\\d{2})(?:(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final Pattern OFFSET_EXTENDED_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS =
            BigInteger.valueOf(9_007_199_254_740_992L).multiply(SECOND_NANOSECONDS).subtract(BigInteger.ONE);
    private static final String UNIT_MONTH = "month";
    private static final String UNIT_WEEK = "week";
    private static final String UNIT_YEAR = "year";

    private TemporalDurationConstructor() {
    }

    private static boolean areDurationRecordsEqual(TemporalDurationRecord one, TemporalDurationRecord two) {
        return one.years() == two.years()
                && one.months() == two.months()
                && one.weeks() == two.weeks()
                && one.days() == two.days()
                && one.hours() == two.hours()
                && one.minutes() == two.minutes()
                && one.seconds() == two.seconds()
                && one.milliseconds() == two.milliseconds()
                && one.microseconds() == two.microseconds()
                && one.nanoseconds() == two.nanoseconds();
    }

    private static long calendarDaysFromRelativeTo(
            JSContext context,
            TemporalDurationRecord durationRecord,
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
        } catch (ArithmeticException | DateTimeException e) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return Long.MIN_VALUE;
        }
    }

    /**
     * Temporal.Duration.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue optionsArg = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        JSTemporalDuration one = toTemporalDurationObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalDuration two = toTemporalDurationObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        TemporalDurationRecord r1 = one.getRecord();
        TemporalDurationRecord r2 = two.getRecord();

        if (!isDurationRecordInRange(context, r1) || !isDurationRecordInRange(context, r2)) {
            return JSUndefined.INSTANCE;
        }

        RelativeToReference relativeToReference = parseRelativeToOption(context, optionsArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean oneHasCalendarUnits = hasCalendarUnits(r1);
        boolean twoHasCalendarUnits = hasCalendarUnits(r2);
        if ((oneHasCalendarUnits || twoHasCalendarUnits) && relativeToReference == null) {
            if (areDurationRecordsEqual(r1, r2)) {
                return JSNumber.of(0);
            } else {
                context.throwRangeError("Temporal error: A starting point is required for years, months, or weeks arithmetic.");
                return JSUndefined.INSTANCE;
            }
        }

        BigInteger oneTotalNanoseconds = totalDurationNanoseconds(context, r1, relativeToReference);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        BigInteger twoTotalNanoseconds = totalDurationNanoseconds(context, r2, relativeToReference);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(Integer.signum(oneTotalNanoseconds.compareTo(twoTotalNanoseconds)));
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

        TemporalDurationRecord record = new TemporalDurationRecord(years, months, weeks, days,
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

    public static JSTemporalDuration createDuration(JSContext context, TemporalDurationRecord record) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "Duration");
        return createDuration(context, record, prototype);
    }

    static JSTemporalDuration createDuration(JSContext context, TemporalDurationRecord record, JSObject prototype) {
        JSTemporalDuration duration = new JSTemporalDuration(context, record);
        if (prototype != null) {
            duration.setPrototype(prototype);
        }
        return duration;
    }

    static BigInteger dayTimeNanoseconds(TemporalDurationRecord durationRecord) {
        return BigInteger.valueOf(durationRecord.days()).multiply(DAY_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
    }

    static JSValue durationFromFields(JSContext context, JSObject fields) {
        DurationLikeFieldValue daysFieldValue = getDurationLikeField(context, fields, "days");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue hoursFieldValue = getDurationLikeField(context, fields, "hours");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue microsecondsFieldValue = getDurationLikeField(context, fields, "microseconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue millisecondsFieldValue = getDurationLikeField(context, fields, "milliseconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue minutesFieldValue = getDurationLikeField(context, fields, "minutes");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue monthsFieldValue = getDurationLikeField(context, fields, "months");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue nanosecondsFieldValue = getDurationLikeField(context, fields, "nanoseconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue secondsFieldValue = getDurationLikeField(context, fields, "seconds");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue weeksFieldValue = getDurationLikeField(context, fields, "weeks");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DurationLikeFieldValue yearsFieldValue = getDurationLikeField(context, fields, "years");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean hasRecognizedField =
                daysFieldValue.present()
                        || hoursFieldValue.present()
                        || microsecondsFieldValue.present()
                        || millisecondsFieldValue.present()
                        || minutesFieldValue.present()
                        || monthsFieldValue.present()
                        || nanosecondsFieldValue.present()
                        || secondsFieldValue.present()
                        || weeksFieldValue.present()
                        || yearsFieldValue.present();
        if (!hasRecognizedField) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        BigInteger yearsBigInteger = yearsFieldValue.value();
        BigInteger monthsBigInteger = monthsFieldValue.value();
        BigInteger weeksBigInteger = weeksFieldValue.value();
        BigInteger daysBigInteger = daysFieldValue.value();
        BigInteger hoursBigInteger = hoursFieldValue.value();
        BigInteger minutesBigInteger = minutesFieldValue.value();
        BigInteger secondsBigInteger = secondsFieldValue.value();
        BigInteger millisecondsBigInteger = millisecondsFieldValue.value();
        BigInteger microsecondsBigInteger = microsecondsFieldValue.value();
        BigInteger nanosecondsBigInteger = nanosecondsFieldValue.value();

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

        TemporalDurationRecord record = new TemporalDurationRecord(years, months, weeks, days,
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
        TemporalParser.DurationFields df = TemporalParser.parseDurationString(context, input);
        if (df == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDurationRecord record = new TemporalDurationRecord(
                df.years(), df.months(), df.weeks(), df.days(),
                df.hours(), df.minutes(), df.seconds(),
                df.milliseconds(), df.microseconds(), df.nanoseconds());

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

    private static DurationLikeFieldValue getDurationLikeField(JSContext context, JSObject obj, String key) {
        JSValue value = obj.get(PropertyKey.fromString(key));
        if (value instanceof JSUndefined || value == null) {
            return new DurationLikeFieldValue(false, BigInteger.ZERO);
        }
        double numericValueAsDouble = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return new DurationLikeFieldValue(true, BigInteger.ZERO);
        }
        if (!Double.isFinite(numericValueAsDouble)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return new DurationLikeFieldValue(true, BigInteger.ZERO);
        }
        if (numericValueAsDouble != Math.floor(numericValueAsDouble)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return new DurationLikeFieldValue(true, BigInteger.ZERO);
        }
        BigInteger numericValue = toBigIntegerFromIntegralDouble(numericValueAsDouble);
        return new DurationLikeFieldValue(true, numericValue);
    }

    private static boolean hasCalendarUnits(TemporalDurationRecord durationRecord) {
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

    private static boolean isCalendarTemporalObject(JSValue value) {
        return value instanceof JSTemporalPlainDate
                || value instanceof JSTemporalPlainDateTime
                || value instanceof JSTemporalPlainMonthDay
                || value instanceof JSTemporalPlainYearMonth
                || value instanceof JSTemporalZonedDateTime;
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

    private static boolean isDurationRecordInRange(JSContext context, TemporalDurationRecord durationRecord) {
        if (durationRecord.years() > CALENDAR_UNIT_MAX
                || durationRecord.years() < -CALENDAR_UNIT_MAX
                || durationRecord.months() > CALENDAR_UNIT_MAX
                || durationRecord.months() < -CALENDAR_UNIT_MAX
                || durationRecord.weeks() > CALENDAR_UNIT_MAX
                || durationRecord.weeks() < -CALENDAR_UNIT_MAX) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return false;
        }
        BigInteger dayTimeNanoseconds = dayTimeNanoseconds(durationRecord);
        if (dayTimeNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return false;
        }
        return true;
    }

    static boolean isDurationRecordTimeRangeValid(TemporalDurationRecord durationRecord) {
        BigInteger totalNanoseconds = dayTimeNanoseconds(durationRecord);
        return totalNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) <= 0;
    }

    private static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        return isValidTimeZoneOffsetWithoutSeconds(timeZoneId);
    }

    private static boolean isValidOffsetString(String offsetText) {
        OffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }
        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        String secondsGroup = offsetParts.secondsText();
        String fractionGroup = offsetParts.fractionText();
        if (hours > 23 || minutes > 59) {
            return false;
        }
        if (secondsGroup != null) {
            int seconds = Integer.parseInt(secondsGroup);
            if (seconds != 0) {
                return false;
            }
        }
        if (fractionGroup != null) {
            for (int index = 0; index < fractionGroup.length(); index++) {
                if (fractionGroup.charAt(index) != '0') {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isValidTimeZoneOffsetWithoutSeconds(String offsetText) {
        OffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }
        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        String secondsGroup = offsetParts.secondsText();
        String fractionGroup = offsetParts.fractionText();
        if (hours > 23 || minutes > 59) {
            return false;
        }
        return secondsGroup == null && fractionGroup == null;
    }

    static String largerTemporalUnit(String leftUnit, String rightUnit) {
        if (temporalUnitRank(leftUnit) <= temporalUnitRank(rightUnit)) {
            return leftUnit;
        } else {
            return rightUnit;
        }
    }

    static TemporalDurationRecord normalizeFloat64RepresentableFields(TemporalDurationRecord durationRecord) {
        return new TemporalDurationRecord(
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

    private static OffsetParts parseOffsetParts(String offsetText) {
        Matcher extendedMatcher = OFFSET_EXTENDED_PATTERN.matcher(offsetText);
        if (extendedMatcher.matches()) {
            return new OffsetParts(
                    extendedMatcher.group(1),
                    Integer.parseInt(extendedMatcher.group(2)),
                    Integer.parseInt(extendedMatcher.group(3)),
                    extendedMatcher.group(4),
                    extendedMatcher.group(5));
        }
        Matcher basicMatcher = OFFSET_BASIC_PATTERN.matcher(offsetText);
        if (basicMatcher.matches()) {
            return new OffsetParts(
                    basicMatcher.group(1),
                    Integer.parseInt(basicMatcher.group(2)),
                    Integer.parseInt(basicMatcher.group(3)),
                    basicMatcher.group(4),
                    basicMatcher.group(5));
        }
        return null;
    }

    private static int parseOffsetSeconds(String offsetText) {
        OffsetParts offsetParts = parseOffsetParts(offsetText);
        String signGroup = offsetParts.signText();
        int sign = ("-".equals(signGroup) || "\u2212".equals(signGroup)) ? -1 : 1;
        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        return sign * (hours * 3600 + minutes * 60);
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
            if (calendarValue instanceof JSString calendarString) {
                String calendarId = calendarString.value().toLowerCase(java.util.Locale.ROOT);
                if (!"iso8601".equals(calendarId)) {
                    context.throwRangeError("Temporal error: Invalid calendar.");
                    return null;
                }
            } else if (isCalendarTemporalObject(calendarValue)) {
                // Fast path: calendar-carrying Temporal objects provide their internal calendar.
            } else {
                context.throwTypeError("Temporal error: Calendar must be string.");
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
        Long day = toRequiredIntegralLong(context, dayValue);
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
            if (!isValidOffsetString(offsetText)) {
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
        Long year = toRequiredIntegralLong(context, yearValue);
        if (context.hasPendingException()) {
            return null;
        }

        int monthNumber;
        if (month != null) {
            monthNumber = month.intValue();
        } else if (monthCode != null) {
            monthNumber = TemporalPlainDateConstructor.parseMonthCode(context, monthCode);
            if (context.hasPendingException()) {
                return null;
            }
        } else {
            context.throwTypeError("Temporal error: Invalid relativeTo option.");
            return null;
        }

        if (!IsoDate.isValidIsoDate(year.intValue(), monthNumber, day.intValue())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate relativeDate = new IsoDate(year.intValue(), monthNumber, day.intValue());

        if (timeZoneValue instanceof JSUndefined || timeZoneValue == null) {
            return new RelativeToReference(relativeDate, null, null);
        }
        if (!(timeZoneValue instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string.");
            return null;
        }
        String timeZoneText = timeZoneString.value();
        String normalizedTimeZoneId = parseTimeZoneIdentifierString(context, timeZoneText);
        if (context.hasPendingException()) {
            return null;
        }
        boolean offsetTimeZoneIdentifier = isOffsetTimeZoneIdentifier(normalizedTimeZoneId);
        if (!offsetTimeZoneIdentifier) {
            try {
                TemporalTimeZone.resolveTimeZone(normalizedTimeZoneId);
            } catch (DateTimeException e) {
                context.throwRangeError("Temporal error: Invalid time zone: " + normalizedTimeZoneId);
                return null;
            }
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
        if (!IsoTime.isValidTime(hourInt, minuteInt, secondInt, millisecondInt, microsecondInt, nanosecondInt)) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        IsoTime relativeTime = new IsoTime(hourInt, minuteInt, secondInt, millisecondInt, microsecondInt, nanosecondInt);
        IsoDateTime relativeDateTime = new IsoDateTime(relativeDate, relativeTime);

        BigInteger epochNanoseconds;
        if (offsetText != null) {
            int offsetSeconds = parseOffsetSeconds(offsetText);
            int zoneOffsetSeconds;
            if (offsetTimeZoneIdentifier) {
                zoneOffsetSeconds = parseOffsetSeconds(normalizedTimeZoneId);
            } else {
                try {
                    BigInteger guessedEpochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(relativeDate, relativeTime, offsetSeconds);
                    zoneOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(guessedEpochNanoseconds, normalizedTimeZoneId);
                } catch (DateTimeException e) {
                    context.throwRangeError("Temporal error: Invalid time zone: " + normalizedTimeZoneId);
                    return null;
                }
            }
            if (zoneOffsetSeconds != offsetSeconds) {
                context.throwRangeError("Temporal error: Invalid offset.");
                return null;
            }
            epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(relativeDate, relativeTime, offsetSeconds);
        } else {
            if (offsetTimeZoneIdentifier) {
                int offsetSeconds = parseOffsetSeconds(normalizedTimeZoneId);
                epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(relativeDate, relativeTime, offsetSeconds);
            } else {
                epochNanoseconds = TemporalTimeZone.localDateTimeToEpochNs(relativeDateTime, normalizedTimeZoneId);
            }
        }
        if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new RelativeToReference(relativeDate, epochNanoseconds, normalizedTimeZoneId);
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
                if (offsetText != null && !isValidOffsetString(offsetText)) {
                    context.throwRangeError("Temporal error: Invalid offset string.");
                    return null;
                }
                TemporalParser.ParsedDateTime parsedDateTime = TemporalParser.parseDateTimeString(context, constrainedRelativeToText);
                if (parsedDateTime == null || context.hasPendingException()) {
                    return null;
                }
                if (!isDateWithinRelativeToRange(parsedDateTime.date())) {
                    context.throwRangeError("Temporal error: Duration field out of range.");
                    return null;
                }
                return new RelativeToReference(parsedDateTime.date(), null, null);
            }

            String offsetText = extractOffsetText(relativeToText);
            if (offsetText != null && !isValidOffsetString(offsetText)) {
                context.throwRangeError("Temporal error: Invalid offset string.");
                return null;
            }
            TemporalParser.ParsedZonedDateTime parsedZonedDateTime =
                    TemporalParser.parseZonedDateTimeString(context, constrainedRelativeToText);
            if (parsedZonedDateTime == null || context.hasPendingException()) {
                return null;
            }
            if (!isDateTimeWithinRelativeToRange(parsedZonedDateTime.date(), parsedZonedDateTime.time())) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            String timeZoneId = parsedZonedDateTime.timeZoneId();
            boolean offsetTimeZoneIdentifier = isOffsetTimeZoneIdentifier(timeZoneId);
            if (!offsetTimeZoneIdentifier) {
                try {
                    TemporalTimeZone.resolveTimeZone(timeZoneId);
                } catch (DateTimeException e) {
                    context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                    return null;
                }
            }
            BigInteger epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(
                    parsedZonedDateTime.date(),
                    parsedZonedDateTime.time(),
                    parsedZonedDateTime.offsetSeconds());
            if (!TemporalInstantConstructor.isValidEpochNanoseconds(epochNanoseconds)) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }
            int zoneOffsetSeconds;
            if (offsetTimeZoneIdentifier) {
                zoneOffsetSeconds = parseOffsetSeconds(timeZoneId);
            } else {
                try {
                    zoneOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, timeZoneId);
                } catch (DateTimeException e) {
                    context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                    return null;
                }
            }
            if (offsetText != null && zoneOffsetSeconds != parsedZonedDateTime.offsetSeconds()) {
                context.throwRangeError("Temporal error: Invalid offset.");
                return null;
            }
            return new RelativeToReference(parsedZonedDateTime.date(), epochNanoseconds, timeZoneId);
        }

        if (hasOffsetDesignator) {
            context.throwRangeError("Temporal error: Invalid relativeTo string.");
            return null;
        }

        TemporalParser.ParsedDateTime parsedDateTime = TemporalParser.parseDateTimeString(context, constrainedRelativeToText);
        if (parsedDateTime == null || context.hasPendingException()) {
            return null;
        }
        IsoDate isoDate = parsedDateTime.date();
        if (!isDateWithinRelativeToRange(isoDate)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new RelativeToReference(isoDate, null, null);
    }

    static RelativeToReference parseRelativeToValue(JSContext context, JSValue relativeToValue) {
        if (relativeToValue instanceof JSTemporalPlainDate plainDate) {
            return new RelativeToReference(plainDate.getIsoDate(), null, null);
        }
        if (relativeToValue instanceof JSTemporalPlainDateTime plainDateTime) {
            return new RelativeToReference(plainDateTime.getIsoDateTime().date(), null, null);
        }
        if (relativeToValue instanceof JSTemporalZonedDateTime zonedDateTime) {
            IsoDateTime relativeDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return new RelativeToReference(
                    relativeDateTime.date(),
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
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

    private static String parseTimeZoneIdentifierString(JSContext context, String timeZoneText) {
        if (timeZoneText.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid time zone.");
            return null;
        }
        boolean looksLikeIsoDateTime =
                (timeZoneText.contains("T") || timeZoneText.contains("t")) && timeZoneText.contains("-");
        if (!looksLikeIsoDateTime) {
            return timeZoneText;
        }

        if (timeZoneText.contains("[")) {
            String adjustedTimeZoneText = timeZoneText;
            if (adjustedTimeZoneText.contains(":60")) {
                adjustedTimeZoneText = adjustedTimeZoneText.replace(":60", ":59");
            }
            String offsetText = extractOffsetText(adjustedTimeZoneText);
            if (offsetText != null && !isValidOffsetString(offsetText)) {
                context.throwRangeError("Temporal error: Invalid offset string.");
                return null;
            }
            TemporalParser.ParsedZonedDateTime parsedZonedDateTime =
                    TemporalParser.parseZonedDateTimeString(context, adjustedTimeZoneText);
            if (parsedZonedDateTime == null || context.hasPendingException()) {
                return null;
            }
            return parsedZonedDateTime.timeZoneId();
        }

        if (!hasOffsetDesignator(timeZoneText)) {
            context.throwRangeError("Temporal error: Invalid time zone.");
            return null;
        }
        String offsetText = extractOffsetText(timeZoneText);
        if (offsetText != null && !isValidTimeZoneOffsetWithoutSeconds(offsetText)) {
            context.throwRangeError("Temporal error: Invalid offset string.");
            return null;
        }
        TemporalParser.ParsedInstant parsedInstant = TemporalParser.parseInstantString(context, timeZoneText);
        if (parsedInstant == null || context.hasPendingException()) {
            return null;
        }
        if (parsedInstant.offsetSeconds() == 0) {
            return "UTC";
        }
        return TemporalTimeZone.formatOffset(parsedInstant.offsetSeconds());
    }

    private static int temporalUnitRank(String temporalUnit) {
        return switch (temporalUnit) {
            case UNIT_DAY -> 0;
            case UNIT_HOUR -> 1;
            case UNIT_MINUTE -> 2;
            case UNIT_SECOND -> 3;
            case UNIT_MILLISECOND -> 4;
            case UNIT_MICROSECOND -> 5;
            case UNIT_NANOSECOND -> 6;
            default -> 7;
        };
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
            return createDuration(context, duration.getRecord());
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
            TemporalDurationRecord durationRecord,
            RelativeToReference relativeToReference) {
        if (relativeToReference == null) {
            return dayTimeNanoseconds(durationRecord);
        }
        long calendarDays = calendarDaysFromRelativeTo(context, durationRecord, relativeToReference);
        if (context.hasPendingException()) {
            return BigInteger.ZERO;
        }
        BigInteger calendarNanoseconds = BigInteger.valueOf(calendarDays).multiply(DAY_NANOSECONDS);
        BigInteger timeNanoseconds = BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
        BigInteger totalNanoseconds = calendarNanoseconds.add(timeNanoseconds);
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

    private record DurationLikeFieldValue(boolean present, BigInteger value) {
    }

    private record OffsetParts(String signText, int hours, int minutes, String secondsText, String fractionText) {
    }

    record RelativeToReference(IsoDate relativeDate, BigInteger epochNanoseconds, String timeZoneId) {
    }
}
