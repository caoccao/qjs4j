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
import com.caoccao.qjs4j.core.temporal.IsoDateTimeOffset;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalTimeZone;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
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
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalInstant firstInstant = toTemporalInstantObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalInstant secondInstant = toTemporalInstantObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(firstInstant.getEpochNanoseconds().compareTo(secondInstant.getEpochNanoseconds()));
    }

    /**
     * Temporal.Instant(epochNanoseconds)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.Instant.");
            return JSUndefined.INSTANCE;
        }
        JSValue epochNanosecondsArgument = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        final JSBigInt bigInt;
        try {
            bigInt = JSTypeConversions.toBigInt(context, epochNanosecondsArgument);
        } catch (JSErrorException conversionException) {
            return context.throwError(conversionException);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = bigInt.value();
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
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
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return toTemporalInstant(context, item);
    }

    /**
     * Temporal.Instant.fromEpochMilliseconds(epochMilliseconds)
     */
    public static JSValue fromEpochMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue epochMillisecondsArgument = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double epochMilliseconds = JSTypeConversions.toNumber(context, epochMillisecondsArgument).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!Double.isFinite(epochMilliseconds) || epochMilliseconds != Math.floor(epochMilliseconds)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = BigInteger.valueOf((long) epochMilliseconds).multiply(NS_PER_MS);
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, epochNs);
    }

    /**
     * Temporal.Instant.fromEpochNanoseconds(epochNanoseconds)
     */
    public static JSValue fromEpochNanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue epochNanosecondsArgument = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        final JSBigInt bigInt;
        try {
            bigInt = JSTypeConversions.toBigInt(context, epochNanosecondsArgument);
        } catch (JSErrorException conversionException) {
            return context.throwError(conversionException);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        BigInteger epochNs = bigInt.value();
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, epochNs);
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
        BigInteger epochNs = TemporalTimeZone.utcDateTimeToEpochNs(
                parsed.date(),
                parsed.time(),
                parsed.offset().totalNanoseconds());
        if (!TemporalUtils.isValidEpochNanoseconds(epochNs)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalInstant.create(context, epochNs);
    }

    public static JSTemporalInstant toTemporalInstantObject(JSContext context, JSValue item) {
        JSValue result = toTemporalInstant(context, item);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalInstant) result;
    }
}
