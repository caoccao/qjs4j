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
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.temporal.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.TemporalTimeZone;

import java.math.BigInteger;

/**
 * Implementation of Temporal.Now namespace methods.
 */
public final class TemporalNow {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);

    private TemporalNow() {
    }

    private static String getTimeZone(JSValue[] args) {
        if (args.length > 0 && args[0] instanceof JSString tzStr) {
            return tzStr.value();
        }
        return java.time.ZoneId.systemDefault().getId();
    }

    public static JSValue instant(JSContext context, JSValue thisArg, JSValue[] args) {
        BigInteger epochNs = systemEpochNs();
        return TemporalInstantConstructor.createInstant(context, epochNs);
    }

    public static JSValue plainDateISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(args);
        BigInteger epochNs = systemEpochNs();
        IsoDateTime dt = TemporalTimeZone.epochNsToDateTimeInZone(epochNs, timeZoneId);
        return TemporalPlainDateConstructor.createPlainDate(context, dt.date(), "iso8601");
    }

    public static JSValue plainDateTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(args);
        BigInteger epochNs = systemEpochNs();
        IsoDateTime dt = TemporalTimeZone.epochNsToDateTimeInZone(epochNs, timeZoneId);
        return TemporalPlainDateTimeConstructor.createPlainDateTime(context, dt, "iso8601");
    }

    public static JSValue plainTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(args);
        BigInteger epochNs = systemEpochNs();
        IsoDateTime dt = TemporalTimeZone.epochNsToDateTimeInZone(epochNs, timeZoneId);
        return TemporalPlainTimeConstructor.createPlainTime(context, dt.time());
    }

    private static BigInteger systemEpochNs() {
        java.time.Instant now = java.time.Instant.now();
        return BigInteger.valueOf(now.getEpochSecond()).multiply(BILLION)
                .add(BigInteger.valueOf(now.getNano()));
    }

    public static JSValue timeZoneId(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSString(java.time.ZoneId.systemDefault().getId());
    }

    public static JSValue zonedDateTimeISO(JSContext context, JSValue thisArg, JSValue[] args) {
        String timeZoneId = getTimeZone(args);
        BigInteger epochNs = systemEpochNs();
        return TemporalZonedDateTimeConstructor.createZonedDateTime(context, epochNs, timeZoneId, "iso8601");
    }
}
