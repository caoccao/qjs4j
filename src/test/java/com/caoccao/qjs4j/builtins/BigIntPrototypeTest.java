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
import com.caoccao.qjs4j.core.JSBigInt;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unit tests for BigIntPrototype methods.
 */
public class BigIntPrototypeTest extends BaseTest {

    @Test
    public void testToLocaleString() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: default
        JSValue result = BigIntPrototype.toLocaleString(ctx, bigInt, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("12345", ((JSString) result).getValue());

        // Normal case: with arguments (ignored in simplified implementation)
        result = BigIntPrototype.toLocaleString(ctx, bigInt, new JSValue[]{new JSString("en-US")});
        assertInstanceOf(JSString.class, result);
        assertEquals("12345", ((JSString) result).getValue());

        // Edge case: called on non-BigInt
        result = BigIntPrototype.toLocaleString(ctx, new JSString("not a bigint"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testToString() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: default radix (10)
        JSValue result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("12345", ((JSString) result).getValue());

        // Normal case: explicit radix 10
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(10)});
        assertInstanceOf(JSString.class, result);
        assertEquals("12345", ((JSString) result).getValue());

        // Normal case: radix 16
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(16)});
        assertInstanceOf(JSString.class, result);
        assertEquals("3039", ((JSString) result).getValue());

        // Normal case: radix 2
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(2)});
        assertInstanceOf(JSString.class, result);
        assertEquals("11000000111001", ((JSString) result).getValue());

        // Normal case: negative BigInt
        JSBigInt negativeBigInt = new JSBigInt(BigInteger.valueOf(-12345));
        result = BigIntPrototype.toString(ctx, negativeBigInt, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("-12345", ((JSString) result).getValue());

        // Edge case: radix 2 (minimum)
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(2)});
        assertInstanceOf(JSString.class, result);
        assertEquals("11000000111001", ((JSString) result).getValue());

        // Edge case: radix 36 (maximum)
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(36)});
        assertInstanceOf(JSString.class, result);
        assertEquals("9ix", ((JSString) result).getValue());

        // Edge case: radix too small
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(1)});
        assertRangeError(result);
        assertPendingException(ctx);

        // Edge case: radix too large
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(37)});
        assertRangeError(result);
        assertPendingException(ctx);

        // Edge case: called on non-BigInt
        result = BigIntPrototype.toString(ctx, new JSString("not a bigint"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testValueOf() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: BigInt
        JSValue result = BigIntPrototype.valueOf(ctx, bigInt, new JSValue[]{});
        assertEquals(bigInt, result);

        // Edge case: called on non-BigInt
        result = BigIntPrototype.valueOf(ctx, new JSString("not a bigint"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}