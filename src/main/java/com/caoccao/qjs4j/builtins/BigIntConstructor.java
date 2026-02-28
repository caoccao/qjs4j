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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSErrorException;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.math.BigInteger;

/**
 * Implementation of BigInt constructor and static methods.
 * Based on ES2020 BigInt specification.
 */
public final class BigIntConstructor {
    /**
     * BigInt.asIntN(bits, bigint)
     * ES2020 20.2.2.1
     * Wraps a BigInt value to a signed integer of the given bit width.
     */
    public static JSValue asIntN(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue bitsArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue bigIntArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        try {
            long bits = JSTypeConversions.toIndex(context, bitsArg);
            JSBigInt bigInt = JSTypeConversions.toBigInt(context, bigIntArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return wrapBigIntSigned(bits, bigInt);
        } catch (JSErrorException e) {
            return rethrowError(context, e);
        }
    }

    /**
     * BigInt.asUintN(bits, bigint)
     * ES2020 20.2.2.2
     * Wraps a BigInt value to an unsigned integer of the given bit width.
     */
    public static JSValue asUintN(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue bitsArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue bigIntArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        try {
            long bits = JSTypeConversions.toIndex(context, bitsArg);
            JSBigInt bigInt = JSTypeConversions.toBigInt(context, bigIntArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return wrapBigIntUnsigned(bits, bigInt);
        } catch (JSErrorException e) {
            return rethrowError(context, e);
        }
    }

    /**
     * BigInt(value)
     * ES2020 20.2.1
     * Creates a new BigInt value from a number or string.
     * Note: BigInt cannot be called with new operator in ES2020.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("BigInt requires an argument");
        }

        JSValue arg = args[0];

        // Step 2: Let prim be ? ToPrimitive(value, number).
        if (!JSTypeConversions.isPrimitive(arg)) {
            arg = JSTypeConversions.toPrimitive(context, arg, JSTypeConversions.PreferredType.NUMBER);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
        }

        // Step 3: Return the value from ToBigInt(prim) conversion table.
        if (arg instanceof JSBigInt bigInt) {
            return bigInt;
        } else if (arg instanceof JSNumber num) {
            double value = num.value();
            // Check if value is an integer
            if (value != Math.floor(value) || Double.isInfinite(value) || Double.isNaN(value)) {
                return context.throwRangeError("The number " + num +
                        " cannot be converted to a BigInt because it is not an integer");
            }
            // Use BigDecimal for exact conversion to handle values outside long range
            BigInteger bigIntValue = new BigDecimal(value).toBigInteger();
            return new JSBigInt(bigIntValue);
        } else if (arg instanceof JSString str) {
            String strValue = str.value().trim();
            try {
                return JSTypeConversions.stringToBigInt(strValue);
            } catch (NumberFormatException e) {
                return context.throwSyntaxError("Cannot convert string to BigInt: " + strValue);
            }
        } else if (arg instanceof JSBoolean bool) {
            return new JSBigInt(bool.value() ? 1L : 0L);
        } else if (arg instanceof JSSymbol symbol) {
            return context.throwTypeError("Cannot convert " + symbol.toJavaObject() + " to a BigInt");
        } else if (arg instanceof JSUndefined) {
            return context.throwTypeError("Cannot convert undefined to a BigInt");
        } else if (arg instanceof JSNull) {
            return context.throwTypeError("Cannot convert null to a BigInt");
        } else {
            return context.throwTypeError("Cannot convert value to BigInt");
        }
    }

    private static JSValue rethrowError(JSContext context, JSErrorException errorException) {
        return switch (errorException.getErrorType()) {
            case RangeError -> context.throwRangeError(errorException.getMessage());
            case SyntaxError -> context.throwSyntaxError(errorException.getMessage());
            case TypeError -> context.throwTypeError(errorException.getMessage());
            default -> context.throwError(errorException.getMessage());
        };
    }

    private static JSBigInt wrapBigIntSigned(long bitsLong, JSBigInt bigInt) {
        if (bitsLong == 0) {
            return new JSBigInt(BigInteger.ZERO);
        }
        if (bitsLong > Integer.MAX_VALUE) {
            BigInteger value = bigInt.value();
            if (value.signum() >= 0) {
                if ((long) value.bitLength() < bitsLong) {
                    return bigInt;
                }
            } else {
                BigInteger minAbs = value.negate().subtract(BigInteger.ONE);
                if ((long) minAbs.bitLength() < bitsLong) {
                    return bigInt;
                }
            }
        }
        int bits = (int) bitsLong;
        BigInteger modulus = BigInteger.ONE.shiftLeft(bits);
        BigInteger result = bigInt.value().mod(modulus);
        BigInteger halfModulus = BigInteger.ONE.shiftLeft(bits - 1);
        if (result.compareTo(halfModulus) >= 0) {
            result = result.subtract(modulus);
        }
        return new JSBigInt(result);
    }

    private static JSBigInt wrapBigIntUnsigned(long bitsLong, JSBigInt bigInt) {
        if (bitsLong == 0) {
            return new JSBigInt(BigInteger.ZERO);
        }
        if (bitsLong > Integer.MAX_VALUE) {
            BigInteger value = bigInt.value();
            if (value.signum() >= 0 && (long) value.bitLength() <= bitsLong) {
                return bigInt;
            }
        }
        int bits = (int) bitsLong;
        BigInteger modulus = BigInteger.ONE.shiftLeft(bits);
        BigInteger result = bigInt.value().mod(modulus);
        if (result.signum() < 0) {
            result = result.add(modulus);
        }
        return new JSBigInt(result);
    }
}
