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
import com.caoccao.qjs4j.core.temporal.TemporalDurationRecord;
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * Implementation of Temporal.Duration constructor and static methods.
 */
public final class TemporalDurationConstructor {

    private TemporalDurationConstructor() {
    }

    /**
     * Temporal.Duration.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

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

        // For durations with calendar units (years/months/weeks), require relativeTo
        if (r1.years() != 0 || r1.months() != 0 || r1.weeks() != 0 ||
                r2.years() != 0 || r2.months() != 0 || r2.weeks() != 0) {
            // Without relativeTo, compare by total nanoseconds including days
            // This is a simplification — full spec requires relativeTo for calendar units
        }

        long ns1 = r1.totalNanoseconds();
        long ns2 = r2.totalNanoseconds();
        return JSNumber.of(Long.compare(ns1, ns2));
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

    static JSValue durationFromFields(JSContext context, JSObject fields) {
        long years = getLongField(context, fields, "years", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long months = getLongField(context, fields, "months", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long weeks = getLongField(context, fields, "weeks", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long days = getLongField(context, fields, "days", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long hours = getLongField(context, fields, "hours", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long minutes = getLongField(context, fields, "minutes", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long seconds = getLongField(context, fields, "seconds", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long milliseconds = getLongField(context, fields, "milliseconds", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long microseconds = getLongField(context, fields, "microseconds", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        long nanoseconds = getLongField(context, fields, "nanoseconds", 0);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        TemporalDurationRecord record = new TemporalDurationRecord(years, months, weeks, days,
                hours, minutes, seconds, milliseconds, microseconds, nanoseconds);

        if (!record.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
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

        return createDuration(context, record);
    }

    /**
     * Temporal.Duration.from(item)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return toTemporalDuration(context, item);
    }

    private static long getLongField(JSContext context, JSObject obj, String key, long defaultValue) {
        JSValue value = obj.get(PropertyKey.fromString(key));
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        double num = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(num)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        return (long) num;
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
}
