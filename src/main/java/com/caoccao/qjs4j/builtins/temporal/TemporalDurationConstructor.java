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
import java.time.temporal.ChronoUnit;

/**
 * Implementation of Temporal.Duration constructor and static methods.
 */
public final class TemporalDurationConstructor {
    private static final long CALENDAR_UNIT_MAX = 4_294_967_295L;
    private static final BigInteger DAY_NANOSECONDS = BigInteger.valueOf(86_400_000_000_000L);
    private static final BigInteger HOUR_NANOSECONDS = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger MICROSECOND_NANOSECONDS = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLISECOND_NANOSECONDS = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MINUTE_NANOSECONDS = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger SECOND_NANOSECONDS = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS =
            BigInteger.valueOf(9_007_199_254_740_992L).multiply(SECOND_NANOSECONDS).subtract(BigInteger.ONE);

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
            years = TemporalUtils.toLongIfIntegral(context, args[0]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            months = TemporalUtils.toLongIfIntegral(context, args[1]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            weeks = TemporalUtils.toLongIfIntegral(context, args[2]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            days = TemporalUtils.toLongIfIntegral(context, args[3]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 4 && !(args[4] instanceof JSUndefined)) {
            hours = TemporalUtils.toLongIfIntegral(context, args[4]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 5 && !(args[5] instanceof JSUndefined)) {
            minutes = TemporalUtils.toLongIfIntegral(context, args[5]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 6 && !(args[6] instanceof JSUndefined)) {
            seconds = TemporalUtils.toLongIfIntegral(context, args[6]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 7 && !(args[7] instanceof JSUndefined)) {
            milliseconds = TemporalUtils.toLongIfIntegral(context, args[7]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 8 && !(args[8] instanceof JSUndefined)) {
            microseconds = TemporalUtils.toLongIfIntegral(context, args[8]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 9 && !(args[9] instanceof JSUndefined)) {
            nanoseconds = TemporalUtils.toLongIfIntegral(context, args[9]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
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

    private static BigInteger dayTimeNanoseconds(TemporalDurationRecord durationRecord) {
        return BigInteger.valueOf(durationRecord.days()).multiply(DAY_NANOSECONDS)
                .add(BigInteger.valueOf(durationRecord.hours()).multiply(HOUR_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.minutes()).multiply(MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.seconds()).multiply(SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.milliseconds()).multiply(MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.microseconds()).multiply(MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(durationRecord.nanoseconds()));
    }

    static JSValue durationFromFields(JSContext context, JSObject fields) {
        long years = getDurationLikeField(context, fields, "years", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long months = getDurationLikeField(context, fields, "months", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long weeks = getDurationLikeField(context, fields, "weeks", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long days = getDurationLikeField(context, fields, "days", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long hours = getDurationLikeField(context, fields, "hours", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long minutes = getDurationLikeField(context, fields, "minutes", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long seconds = getDurationLikeField(context, fields, "seconds", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long milliseconds = getDurationLikeField(context, fields, "milliseconds", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long microseconds = getDurationLikeField(context, fields, "microseconds", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long nanoseconds = getDurationLikeField(context, fields, "nanoseconds", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean hasRecognizedField =
                hasDurationLikeField(fields, "years")
                        || hasDurationLikeField(fields, "months")
                        || hasDurationLikeField(fields, "weeks")
                        || hasDurationLikeField(fields, "days")
                        || hasDurationLikeField(fields, "hours")
                        || hasDurationLikeField(fields, "minutes")
                        || hasDurationLikeField(fields, "seconds")
                        || hasDurationLikeField(fields, "milliseconds")
                        || hasDurationLikeField(fields, "microseconds")
                        || hasDurationLikeField(fields, "nanoseconds");
        if (!hasRecognizedField) {
            context.throwTypeError("Temporal error: Must provide a duration.");
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

    /**
     * Temporal.Duration.from(item)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return toTemporalDuration(context, item);
    }

    private static long getDurationLikeField(JSContext context, JSObject obj, String key, long defaultValue) {
        JSValue value = obj.get(PropertyKey.fromString(key));
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        return TemporalUtils.toLongIfIntegral(context, value);
    }

    private static boolean hasCalendarUnits(TemporalDurationRecord durationRecord) {
        return durationRecord.years() != 0 || durationRecord.months() != 0 || durationRecord.weeks() != 0;
    }

    private static boolean hasDurationLikeField(JSObject fields, String key) {
        JSValue value = fields.get(PropertyKey.fromString(key));
        return !(value instanceof JSUndefined || value == null);
    }

    private static boolean isDurationRecordInRange(JSContext context, TemporalDurationRecord durationRecord) {
        if (Math.abs(durationRecord.years()) > CALENDAR_UNIT_MAX
                || Math.abs(durationRecord.months()) > CALENDAR_UNIT_MAX
                || Math.abs(durationRecord.weeks()) > CALENDAR_UNIT_MAX) {
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

        if (relativeToValue instanceof JSTemporalPlainDate plainDate) {
            return new RelativeToReference(plainDate.getIsoDate());
        }
        if (relativeToValue instanceof JSTemporalPlainDateTime plainDateTime) {
            return new RelativeToReference(plainDateTime.getIsoDateTime().date());
        }
        if (relativeToValue instanceof JSTemporalZonedDateTime zonedDateTime) {
            IsoDateTime relativeDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return new RelativeToReference(relativeDateTime.date());
        }

        if (relativeToValue instanceof JSString relativeToString) {
            String relativeToText = relativeToString.value();
            if (relativeToText.contains("[")) {
                JSTemporalZonedDateTime zonedDateTime = TemporalZonedDateTimeConstructor.toTemporalZonedDateTimeObject(context, relativeToValue);
                if (context.hasPendingException()) {
                    return null;
                }
                IsoDateTime relativeDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                        zonedDateTime.getEpochNanoseconds(),
                        zonedDateTime.getTimeZoneId());
                return new RelativeToReference(relativeDateTime.date());
            } else {
                JSTemporalPlainDate plainDate = TemporalPlainDateConstructor.toTemporalDateObject(context, relativeToValue);
                if (context.hasPendingException()) {
                    return null;
                }
                return new RelativeToReference(plainDate.getIsoDate());
            }
        }

        JSTemporalPlainDate plainDate = TemporalPlainDateConstructor.toTemporalDateObject(context, relativeToValue);
        if (context.hasPendingException()) {
            return null;
        }
        return new RelativeToReference(plainDate.getIsoDate());
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
        return totalNanoseconds;
    }

    private record RelativeToReference(IsoDate relativeDate) {
    }
}
