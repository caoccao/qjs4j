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
        if (args.length < 2) {
            return context.throwTypeError("BigInt.asIntN requires 2 arguments");
        }

        JSValue bitsArg = args[0];
        JSValue bigIntArg = args[1];

        if (!(bitsArg instanceof JSNumber bitsNum)) {
            return context.throwTypeError("First argument must be a number");
        }

        if (!(bigIntArg instanceof JSBigInt bigInt)) {
            return context.throwTypeError("Second argument must be a BigInt");
        }

        double bitsDouble = bitsNum.value();
        if (bitsDouble < 0 || bitsDouble > Integer.MAX_VALUE || bitsDouble != Math.floor(bitsDouble)) {
            return context.throwRangeError("Invalid bit width");
        }
        int bits = (int) bitsDouble;

        // Simplified implementation
        BigInteger value = bigInt.value();
        BigInteger modulus = BigInteger.TWO.pow(bits);
        BigInteger result = value.mod(modulus);

        // Adjust for signed representation
        BigInteger halfModulus = BigInteger.TWO.pow(bits - 1);
        if (result.compareTo(halfModulus) >= 0) {
            result = result.subtract(modulus);
        }

        return new JSBigInt(result);
    }

    /**
     * BigInt.asUintN(bits, bigint)
     * ES2020 20.2.2.2
     * Wraps a BigInt value to an unsigned integer of the given bit width.
     */
    public static JSValue asUintN(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("BigInt.asUintN requires 2 arguments");
        }

        JSValue bitsArg = args[0];
        JSValue bigIntArg = args[1];

        if (!(bitsArg instanceof JSNumber bitsNum)) {
            return context.throwTypeError("First argument must be a number");
        }

        if (!(bigIntArg instanceof JSBigInt bigInt)) {
            return context.throwTypeError("Second argument must be a BigInt");
        }

        double bitsDouble = bitsNum.value();
        if (bitsDouble < 0 || bitsDouble > Integer.MAX_VALUE || bitsDouble != Math.floor(bitsDouble)) {
            return context.throwRangeError("Invalid bit width");
        }
        int bits = (int) bitsDouble;

        // Simplified implementation
        BigInteger value = bigInt.value();
        BigInteger modulus = BigInteger.TWO.pow(bits);
        BigInteger result = value.mod(modulus);

        if (result.signum() < 0) {
            result = result.add(modulus);
        }

        return new JSBigInt(result);
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

        // Convert argument to BigInt
        if (arg instanceof JSBigInt bigInt) {
            return bigInt;
        } else if (arg instanceof JSNumber num) {
            double value = num.value();
            // Check if value is an integer
            if (value != Math.floor(value) || Double.isInfinite(value) || Double.isNaN(value)) {
                return context.throwRangeError("Cannot convert non-integer number to BigInt");
            }
            return new JSBigInt((long) value);
        } else if (arg instanceof JSString str) {
            String strValue = str.value().trim();
            try {
                // Handle different radix
                if (strValue.startsWith("0x") || strValue.startsWith("0X")) {
                    return new JSBigInt(new BigInteger(strValue.substring(2), 16));
                } else if (strValue.startsWith("0o") || strValue.startsWith("0O")) {
                    return new JSBigInt(new BigInteger(strValue.substring(2), 8));
                } else if (strValue.startsWith("0b") || strValue.startsWith("0B")) {
                    return new JSBigInt(new BigInteger(strValue.substring(2), 2));
                } else {
                    return new JSBigInt(new BigInteger(strValue, 10));
                }
            } catch (NumberFormatException e) {
                return context.throwSyntaxError("Cannot convert string to BigInt: " + strValue);
            }
        } else if (arg instanceof JSBoolean bool) {
            return new JSBigInt(bool.value() ? 1L : 0L);
        } else {
            return context.throwTypeError("Cannot convert value to BigInt");
        }
    }
}
