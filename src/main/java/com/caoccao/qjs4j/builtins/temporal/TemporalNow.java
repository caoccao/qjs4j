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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.temporal.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalTimeZone;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Implementation of Temporal.Now namespace methods.
 */
public final class TemporalNow {
    private static final BigInteger BILLION = TemporalConstants.BI_BILLION;

    private TemporalNow() {
    }

    private static String getTimeZone(JSContext context, JSValue[] args) {
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] == null) {
            return ZoneId.systemDefault().getId();
        }
        JSValue timeZoneValue = args[0];
        if (!(timeZoneValue instanceof JSString timeZoneString)) {
            context.throwTypeError("Temporal error: Time zone must be string");
            return null;
        }

        String normalizedTimeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(
                context,
                timeZoneString.value());
        if (context.hasPendingException() || normalizedTimeZoneId == null) {
            return null;
        }
        try {
            TemporalTimeZone.resolveTimeZone(normalizedTimeZoneId);
        } catch (Exception ignored) {
            context.throwRangeError("Temporal error: Invalid time zone: " + normalizedTimeZoneId);
            return null;
        }
        return normalizedTimeZoneId;
    }

    public static JSValue instant(JSContext context, JSValue thisArg, JSValue[] args) {
        BigInteger epochNs = systemEpochNs();
        return TemporalInstantConstructor.createInstant(context, epochNs);
    }

    public static JSValue plainDateISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(context, args);
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = systemEpochNs();
        IsoDateTime isoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(epochNs, timeZoneId);
        return TemporalPlainDateConstructor.createPlainDate(context, isoDateTime.date(), "iso8601");
    }

    public static JSValue plainDateTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(context, args);
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = systemEpochNs();
        IsoDateTime isoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(epochNs, timeZoneId);
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context, isoDateTime, "iso8601");
    }

    public static JSValue plainTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(context, args);
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = systemEpochNs();
        IsoDateTime isoDateTime = TemporalTimeZone.epochNsToDateTimeInZone(epochNs, timeZoneId);
        return TemporalPlainTimeConstructor.createPlainTime(context, isoDateTime.time());
    }

    private static BigInteger systemEpochNs() {
        Instant currentInstant = Instant.now();
        return BigInteger.valueOf(currentInstant.getEpochSecond()).multiply(BILLION)
                .add(BigInteger.valueOf(currentInstant.getNano()));
    }

    public static JSValue timeZoneId(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSString(java.time.ZoneId.systemDefault().getId());
    }

    public static JSValue zonedDateTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(context, args);
        if (context.hasPendingException() || timeZoneId == null) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = systemEpochNs();
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs, timeZoneId, "iso8601");
    }
}
