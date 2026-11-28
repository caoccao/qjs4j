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

package com.caoccao.qjs4j.builtins.temporal.instant;

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTimeOffset;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;
import com.caoccao.qjs4j.exceptions.JSErrorException;

import java.math.BigInteger;

/**
 * Implementation of Temporal.Instant constructor and static methods.
 */
public final class TemporalInstantConstructor {
    private static final BigInteger NS_PER_MS = TemporalConstants.BI_MILLISECOND_NANOSECONDS;

    private TemporalInstantConstructor() {
    }

    /**
     * Temporal.Instant.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalReceiver.INSTANT.compare(context, args,
                (left, right) -> left.getEpochNanoseconds().compareTo(right.getEpochNanoseconds()));
    }

    /**
     * Temporal.Instant(epochNanoseconds)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!TemporalUtils.requireNewTarget(context, "Instant")) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = readEpochNanoseconds(context, JSValue.argumentOrUndefined(args, 0));
        if (epochNs == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject resolvedPrototype = TemporalUtils.resolveTemporalPrototype(context, "Instant");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, epochNs, resolvedPrototype);
    }

    /**
     * Temporal.Instant.from(item)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        return toTemporalInstant(context, item);
    }

    /**
     * Temporal.Instant.fromEpochMilliseconds(epochMilliseconds)
     */
    public static JSValue fromEpochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue epochMillisecondsArgument = JSValue.argumentOrUndefined(args, 0);
        double epochMilliseconds = JSTypeConversions.toDouble(context, epochMillisecondsArgument);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!Double.isFinite(epochMilliseconds) || epochMilliseconds != Math.floor(epochMilliseconds)) {
            return TemporalError.EXPECTED_FINITE_INTEGER.undefined(context);
        }
        BigInteger epochNs = BigInteger.valueOf((long) epochMilliseconds).multiply(NS_PER_MS);
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            return TemporalError.NANOSECONDS_OUT_OF_RANGE.undefined(context);
        }
        return JSTemporalInstant.create(context, epochNs);
    }

    /**
     * Temporal.Instant.fromEpochNanoseconds(epochNanoseconds)
     */
    public static JSValue fromEpochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        BigInteger epochNs = readEpochNanoseconds(context, JSValue.argumentOrUndefined(args, 0));
        if (epochNs == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, epochNs);
    }

    public static BigInteger readEpochNanoseconds(JSContext context, JSValue epochNanosecondsArgument) {
        final JSBigInt bigInt;
        try {
            bigInt = JSTypeConversions.toBigInt(context, epochNanosecondsArgument);
        } catch (JSErrorException conversionException) {
            context.throwError(conversionException);
            return null;
        }
        if (context.hasPendingException()) {
            return null;
        }
        BigInteger epochNs = bigInt.value();
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            return TemporalError.NANOSECONDS_OUT_OF_RANGE.reject(context);
        }
        return epochNs;
    }

    public static JSValue toTemporalInstant(JSContext context, JSValue item) {
        if (item instanceof JSTemporalInstant instant) {
            return JSTemporalInstant.create(context, instant.getEpochNanoseconds());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            return JSTemporalInstant.create(context, zonedDateTime.getEpochNanoseconds());
        }

        JSValue primitiveItem = item;
        if (item instanceof JSObject objectItem) {
            JSObject instantPrototype = TemporalUtils.getTemporalPrototype(context, "Instant");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (objectItem == instantPrototype) {
                context.throwTypeError("Method invoked on an incompatible receiver.");
                return JSUndefined.INSTANCE;
            }
            primitiveItem = JSTypeConversions.toPrimitive(context, objectItem, JSTypeConversions.PreferredType.STRING);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (!(primitiveItem instanceof JSString instantString)) {
            context.throwTypeError("Temporal error: Instant argument must be Instant or string.");
            return JSUndefined.INSTANCE;
        }

        IsoDateTimeOffset parsed = IsoDateTimeOffset.parseInstantString(context, instantString.value());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = TemporalTimeZone.utcDateTimeToEpochNs(parsed.date(), parsed.time(),
                parsed.offset().totalNanoseconds());
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            return TemporalError.NANOSECONDS_OUT_OF_RANGE.undefined(context);
        }
        return JSTemporalInstant.create(context, epochNs);
    }

    public static JSTemporalInstant toTemporalInstantObject(JSContext context, JSValue item) {
        return TemporalReceiver.INSTANT.convert(context, item);
    }
}
