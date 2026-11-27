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

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;

import java.math.BigInteger;
import java.time.*;

/**
 * Implementation of Temporal.Duration constructor and static methods.
 */
public final class TemporalDurationConstructor {
    private static final long CALENDAR_UNIT_MAX = TemporalConstants.CALENDAR_UNIT_MAX;
    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS = TemporalConstants.MAX_ABSOLUTE_TIME_NANOSECONDS;

    private TemporalDurationConstructor() {
    }

    /**
     * Temporal.Duration.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = JSValue.argumentOrUndefined(args, 0);
        JSValue twoArg = JSValue.argumentOrUndefined(args, 1);
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 2);

        TemporalDuration firstRecord = toDurationRecord(context, oneArg);
        if (firstRecord == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration secondRecord = toDurationRecord(context, twoArg);
        if (secondRecord == null) {
            return JSUndefined.INSTANCE;
        }
        if (!isDurationRecordInRange(context, firstRecord) || !isDurationRecordInRange(context, secondRecord)) {
            return JSUndefined.INSTANCE;
        }

        TemporalRelativeTo relativeToReference = TemporalRelativeTo.parseRelativeToOption(context, optionsArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        boolean firstHasCalendarUnits = firstRecord.hasCalendarUnits();
        boolean secondHasCalendarUnits = secondRecord.hasCalendarUnits();
        if ((firstHasCalendarUnits || secondHasCalendarUnits) && relativeToReference == null) {
            if (firstRecord.equals(secondRecord)) {
                return JSNumber.of(0);
            } else {
                context.throwRangeError(
                        "Temporal error: A starting point is required for years, months, or weeks arithmetic.");
                return JSUndefined.INSTANCE;
            }
        }

        BigInteger firstTotalNanoseconds = TemporalRelativeTo.totalDurationNanoseconds(context, firstRecord,
                relativeToReference);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        BigInteger secondTotalNanoseconds = TemporalRelativeTo.totalDurationNanoseconds(context, secondRecord,
                relativeToReference);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(Integer.signum(firstTotalNanoseconds.compareTo(secondTotalNanoseconds)));
    }

    /**
     * Temporal.Duration(years?, months?, weeks?, days?, hours?, minutes?, seconds?, milliseconds?, microseconds?,
     * nanoseconds?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!TemporalUtils.requireNewTarget(context, "Duration")) {
            return JSUndefined.INSTANCE;
        }

        long[] components = new long[TemporalUnit.DURATION_PROPERTY_ORDER.size()];
        for (int i = 0; i < Math.min(args.length, components.length); i++) {
            if (!(args[i] instanceof JSUndefined)) {
                components[i] = parseDurationConstructorArgument(context, args[i]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        }
        TemporalDuration record = TemporalDuration.fromComponents(components);
        if (!validateRecord(context, record)) {
            return JSUndefined.INSTANCE;
        }

        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "Duration");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, record, resolvedPrototype);
    }

    private static TemporalDuration durationFromFields(JSContext context, JSObject fields) {
        BigInteger[] values = new BigInteger[TemporalUnit.DURATION_PROPERTY_ORDER.size()];
        boolean any = false;
        for (TemporalUnit unit : TemporalUnit.DURATION_PROPERTY_ORDER) {
            BigInteger value = getDurationLikeField(context, fields, unit.durationProperty());
            if (context.hasPendingException()) {
                return null;
            }
            any |= value != null;
            values[unit.rank()] = value == null ? BigInteger.ZERO : value;
        }
        if (!any) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return null;
        }
        normalizeSubseconds(values);
        long[] components = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            components[i] = toLongDurationField(context, values[i]);
            if (context.hasPendingException()) {
                return null;
            }
        }
        TemporalDuration record = TemporalDuration.fromComponents(components);
        if (!validateRecord(context, record)) {
            return null;
        }

        return record;
    }

    private static TemporalDuration durationFromString(JSContext context, String input) {
        TemporalDuration record = TemporalDuration.parseDurationString(context, input);
        if (record == null) {
            return null;
        }

        if (!validateRecord(context, record)) {
            return null;
        }

        return record;
    }

    /**
     * Temporal.Duration.from(item)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        return toTemporalDuration(context, item);
    }

    private static BigInteger getDurationLikeField(JSContext context, JSObject durationLikeObject,
            PropertyKey fieldKey) {
        JSValue value = durationLikeObject.get(fieldKey);
        if (value instanceof JSUndefined || value == null) {
            return null;
        }
        double numericValueAsDouble = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        if (!Double.isFinite(numericValueAsDouble)) {
            return TemporalError.EXPECTED_FINITE_INTEGER.reject(context);
        }
        if (numericValueAsDouble != Math.floor(numericValueAsDouble)) {
            return TemporalError.EXPECTED_FINITE_INTEGER.reject(context);
        }
        BigInteger numericValue = TemporalUtils.toBigIntegerFromIntegralDouble(numericValueAsDouble);
        return numericValue;
    }

    private static boolean isDurationRecordInRange(JSContext context, TemporalDuration durationRecord) {
        if (durationRecord.years() > CALENDAR_UNIT_MAX || durationRecord.years() < -CALENDAR_UNIT_MAX
                || durationRecord.months() > CALENDAR_UNIT_MAX || durationRecord.months() < -CALENDAR_UNIT_MAX
                || durationRecord.weeks() > CALENDAR_UNIT_MAX || durationRecord.weeks() < -CALENDAR_UNIT_MAX) {
            TemporalError.DURATION_FIELD_OUT_OF_RANGE.raise(context);
            return false;
        }
        BigInteger dayTimeNanoseconds = durationRecord.dayTimeNanoseconds();
        if (dayTimeNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            TemporalError.DURATION_FIELD_OUT_OF_RANGE.raise(context);
            return false;
        }
        return true;
    }

    private static void normalizeSubseconds(BigInteger[] values) {
        int nanos = TemporalUnit.NANOSECOND.rank();
        int micros = TemporalUnit.MICROSECOND.rank();
        int millis = TemporalUnit.MILLISECOND.rank();
        if (values[nanos].abs().bitLength() <= 63 && values[micros].abs().bitLength() <= 63
                && values[millis].abs().bitLength() <= 63) {
            return;
        }
        BigInteger thousand = BigInteger.valueOf(1_000);
        for (int i = nanos; i >= millis; i--) {
            if (i == nanos || values[i].abs().bitLength() > 63) {
                BigInteger[] parts = values[i].divideAndRemainder(thousand);
                values[i] = parts[1];
                values[i - 1] = values[i - 1].add(parts[0]);
            }
        }
    }

    private static long parseDurationConstructorArgument(JSContext context, JSValue value) {
        double numericValueAsDouble = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(numericValueAsDouble)) {
            TemporalError.EXPECTED_FINITE_INTEGER.raise(context);
            return Long.MIN_VALUE;
        }
        if (numericValueAsDouble != Math.floor(numericValueAsDouble)) {
            TemporalError.EXPECTED_FINITE_INTEGER.raise(context);
            return Long.MIN_VALUE;
        }
        BigInteger numericValue = TemporalUtils.toBigIntegerFromIntegralDouble(numericValueAsDouble);
        return toLongDurationField(context, numericValue);
    }

    public static TemporalDuration toDurationRecord(JSContext context, JSValue item) {
        if (item instanceof JSTemporalDuration duration) {
            return duration.getDuration();
        }
        if (item instanceof JSString string) {
            return durationFromString(context, string.value());
        }
        if (item instanceof JSObject fields) {
            return durationFromFields(context, fields);
        }
        context.throwTypeError("Temporal error: Must provide a duration.");
        return null;
    }

    private static long toLongDurationField(JSContext context, BigInteger value) {
        if (value.bitLength() > 63) {
            TemporalError.DURATION_FIELD_OUT_OF_RANGE.raise(context);
            return Long.MIN_VALUE;
        }
        long longValue = value.longValue();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        return longValue;
    }

    /**
     * ToTemporalDuration abstract operation.
     */
    public static JSValue toTemporalDuration(JSContext context, JSValue item) {
        TemporalDuration record = toDurationRecord(context, item);
        return record == null ? JSUndefined.INSTANCE : JSTemporalDuration.create(context, record);
    }

    public static JSTemporalDuration toTemporalDurationObject(JSContext context, JSValue item) {
        return TemporalReceiver.DURATION.convert(context, item);
    }

    private static boolean validateRecord(JSContext context, TemporalDuration record) {
        if (!record.isValid()) {
            TemporalError.INVALID_DURATION.raise(context);
            return false;
        }
        return isDurationRecordInRange(context, record);
    }

}
