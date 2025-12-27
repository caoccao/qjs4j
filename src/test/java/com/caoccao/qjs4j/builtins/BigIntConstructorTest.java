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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for BigInt constructor and static methods.
 */
public class BigIntConstructorTest extends BaseTest {

    @Test
    public void testAsIntN() {
        // Normal case: positive value within range
        JSValue result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(127))
        });
        JSBigInt bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(127), bigInt.value());

        // Normal case: negative value
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-1))
        });
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(-1), bigInt.value());

        // Normal case: wrap around positive
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(128))
        });
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(-128), bigInt.value());

        // Normal case: wrap around negative
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-129))
        });
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(127), bigInt.value());

        // Edge case: insufficient arguments
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(8)});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: first argument not a number
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("8"), new JSBigInt(BigInteger.ONE)
        });
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: second argument not a BigInt
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSNumber(123)
        });
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: negative bits
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-1), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(ctx);

        // Edge case: bits too large
        result = BigIntConstructor.asIntN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(2147483648L), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testAsUintN() {
        // Normal case: positive value within range
        JSValue result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(255))
        });
        JSBigInt bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(255), bigInt.value());

        // Normal case: wrap around
        result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(256))
        });
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.ZERO, bigInt.value());

        // Normal case: negative value becomes positive
        result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-1))
        });
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(255), bigInt.value());

        // Edge case: insufficient arguments
        result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(8)});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: first argument not a number
        result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("8"), new JSBigInt(BigInteger.ONE)
        });
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: second argument not a BigInt
        result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSNumber(123)
        });
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: negative bits
        result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-1), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(ctx);

        // Edge case: bits too large
        result = BigIntConstructor.asUintN(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(NumberPrototype.MAX_SAFE_INTEGER + 1), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testCall() {
        // Normal case: from number
        JSValue result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        JSBigInt bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(123), bigInt.value());

        // Normal case: from BigInt
        bigInt = new JSBigInt(BigInteger.valueOf(456));
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{bigInt});
        assertEquals(bigInt, result);

        // Normal case: from string (decimal)
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("789")});
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(789), bigInt.value());

        // Normal case: from string (hex)
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("0xFF")});
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(255), bigInt.value());

        // Normal case: from string (octal)
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("0o77")});
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(63), bigInt.value());

        // Normal case: from string (binary)
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("0b101")});
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.valueOf(5), bigInt.value());

        // Normal case: from boolean true
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.TRUE});
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.ONE, bigInt.value());

        // Normal case: from boolean false
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.FALSE});
        bigInt = result.asBigInt().orElse(null);
        assertNotNull(bigInt);
        assertEquals(BigInteger.ZERO, bigInt.value());

        // Edge case: no arguments
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: non-integer number
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1.5)});
        assertRangeError(result);
        assertPendingException(ctx);

        // Edge case: Infinity
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertRangeError(result);
        assertPendingException(ctx);

        // Edge case: NaN
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertRangeError(result);
        assertPendingException(ctx);

        // Edge case: invalid string
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not a number")});
        assertSyntaxError(result);
        assertPendingException(ctx);

        // Edge case: unsupported type
        result = BigIntConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSObject()});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}