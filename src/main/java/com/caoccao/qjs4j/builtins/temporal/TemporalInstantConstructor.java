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
import com.caoccao.qjs4j.core.temporal.TemporalParser;
import com.caoccao.qjs4j.core.temporal.TemporalTimeZone;

import java.math.BigInteger;

/**
 * Implementation of Temporal.Instant constructor and static methods.
 */
public final class TemporalInstantConstructor {

    static final BigInteger NS_MAX_INSTANT = new BigInteger("8640000000000000000000");
    static final BigInteger NS_MIN_INSTANT = new BigInteger("-8640000000000000000000");
    private static final BigInteger NS_PER_MS = BigInteger.valueOf(1_000_000L);

    private TemporalInstantConstructor() {
    }

    /**
     * Temporal.Instant.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalInstant one = toTemporalInstantObject(context, oneArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        JSTemporalInstant two = toTemporalInstantObject(context, twoArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        return JSNumber.of(one.getEpochNanoseconds().compareTo(two.getEpochNanoseconds()));
    }

    /**
     * Temporal.Instant(epochNanoseconds)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.Instant.");
            return JSUndefined.INSTANCE;
        }
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(arg instanceof JSBigInt bigInt)) {
            context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, arg).value() + " to a BigInt");
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = bigInt.value();
        if (!isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "Instant");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createInstant(context, epochNs, resolvedPrototype);
    }

    public static JSTemporalInstant createInstant(JSContext context, BigInteger epochNs) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "Instant");
        return createInstant(context, epochNs, prototype);
    }

    static JSTemporalInstant createInstant(JSContext context, BigInteger epochNs, JSObject prototype) {
        JSTemporalInstant instant = new JSTemporalInstant(context, epochNs);
        if (prototype != null) {
            instant.setPrototype(prototype);
        }
        return instant;
    }

    /**
     * Temporal.Instant.from(item)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return toTemporalInstant(context, item);
    }

    /**
     * Temporal.Instant.fromEpochMilliseconds(epochMilliseconds)
     */
    public static JSValue fromEpochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double ms = JSTypeConversions.toNumber(context, arg).value();
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        if (!Double.isFinite(ms) || ms != Math.floor(ms)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = BigInteger.valueOf((long) ms).multiply(NS_PER_MS);
        if (!isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return createInstant(context, epochNs);
    }

    /**
     * Temporal.Instant.fromEpochNanoseconds(epochNanoseconds)
     */
    public static JSValue fromEpochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(arg instanceof JSBigInt bigInt)) {
            context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, arg).value() + " to a BigInt");
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = bigInt.value();
        if (!isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return createInstant(context, epochNs);
    }

    static boolean isValidEpochNanoseconds(BigInteger epochNs) {
        return epochNs.compareTo(NS_MIN_INSTANT) >= 0 && epochNs.compareTo(NS_MAX_INSTANT) <= 0;
    }

    public static JSValue toTemporalInstant(JSContext context, JSValue item) {
        if (item instanceof JSTemporalInstant instant) {
            return createInstant(context, instant.getEpochNanoseconds());
        }
        if (item instanceof JSString str) {
            TemporalParser.ParsedInstant parsed = TemporalParser.parseInstantString(context, str.value());
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
            BigInteger epochNs = TemporalTimeZone.utcDateTimeToEpochNs(parsed.date(), parsed.time(), parsed.offsetSeconds());
            if (!isValidEpochNanoseconds(epochNs)) {
                context.throwRangeError("Temporal error: Nanoseconds out of range.");
                return JSUndefined.INSTANCE;
            }
            return createInstant(context, epochNs);
        }
        context.throwTypeError("Temporal error: Instant argument must be Instant or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalInstant toTemporalInstantObject(JSContext context, JSValue item) {
        JSValue result = toTemporalInstant(context, item);
        if (context.hasPendingException()) return null;
        return (JSTemporalInstant) result;
    }
}
