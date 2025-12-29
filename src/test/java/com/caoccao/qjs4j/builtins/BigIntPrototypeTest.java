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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BigIntPrototype methods.
 */
public class BigIntPrototypeTest extends BaseTest {

    @Test
    public void testEquals() {
        // Verify that loose equality passes between primitive and primitive
        assertTrue(ctx.eval("123n == 123n").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123n == 321n").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(ctx.eval("123n == BigInt(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123n == BigInt(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality passes between primitive and primitive
        assertTrue(ctx.eval("123n === 123n").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123n === 321n").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(ctx.eval("123n === BigInt(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123n === BigInt(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and primitive
        assertTrue(ctx.eval("BigInt(123) == BigInt(123)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("BigInt(123) == BigInt(321)").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(ctx.eval("BigInt(123) == 123n").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("BigInt(123) == 321n").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality passes between primitive and object
        assertTrue(ctx.eval("123n == Object(BigInt(123))").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("123n == Object(BigInt(321))").asBoolean().map(JSBoolean::value).orElseThrow());
        assertTrue(ctx.eval("BigInt(123) == Object(BigInt(123))").asBoolean().map(JSBoolean::value).orElseThrow());
        assertFalse(ctx.eval("BigInt(123) == Object(BigInt(321))").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that loose equality fails between object and object
        assertFalse(ctx.eval("Object(BigInt(123)) == Object(BigInt(123))").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between primitive and object
        assertFalse(ctx.eval("123n === Object(BigInt(123))").asBoolean().map(JSBoolean::value).orElseThrow());
        // Verify that strict equality fails between object and object
        assertFalse(ctx.eval("Object(BigInt(123)) === Object(BigInt(123))").asBoolean().map(JSBoolean::value).orElseThrow());
    }

    @Test
    public void testLiterals() {
        // Test typeof for BigInt literal
        JSValue typeResult = ctx.eval("typeof 123n");

        // Test basic decimal BigInt literal
        JSValue result = ctx.eval("123n");

        // Just check if it works without assertion for now
        if (!result.isBigInt()) {
            fail("Expected BigInt but got: " + result.getClass().getName() + " with value: " + result);
        }

        JSBigInt bigInt = (JSBigInt) result;
        assertEquals(BigInteger.valueOf(123), bigInt.value());

        // Test hex BigInt literal
        result = ctx.eval("0xFFn");
        assertTrue(result.isBigInt());
        assertEquals(BigInteger.valueOf(255), result.asBigInt().map(JSBigInt::value).orElseThrow());

        // Test binary BigInt literal
        result = ctx.eval("0b1111n");
        assertTrue(result.isBigInt());
        assertEquals(BigInteger.valueOf(15), result.asBigInt().map(JSBigInt::value).orElseThrow());

        // Test octal BigInt literal
        result = ctx.eval("0o77n");
        assertTrue(result.isBigInt());
        assertEquals(BigInteger.valueOf(63), result.asBigInt().map(JSBigInt::value).orElseThrow());

        // Test large BigInt literal
        result = ctx.eval("9007199254740991n");
        assertTrue(result.isBigInt());
        assertEquals(new BigInteger("9007199254740991"), result.asBigInt().map(JSBigInt::value).orElseThrow());
    }

    @Test
    public void testToLocaleString() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: default
        JSValue result = BigIntPrototype.toLocaleString(ctx, bigInt, new JSValue[]{});
        JSString str = result.asString().orElseThrow();
        assertEquals("12345", str.value());

        // Normal case: with arguments (ignored in simplified implementation)
        result = BigIntPrototype.toLocaleString(ctx, bigInt, new JSValue[]{new JSString("en-US")});
        str = result.asString().orElseThrow();
        assertEquals("12345", str.value());

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
        JSString str = result.asString().orElseThrow();
        assertEquals("12345", str.value());

        // Normal case: explicit radix 10
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(10)});
        str = result.asString().orElseThrow();
        assertEquals("12345", str.value());

        // Normal case: radix 16
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(16)});
        str = result.asString().orElseThrow();
        assertEquals("3039", str.value());

        // Normal case: radix 2
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(2)});
        str = result.asString().orElseThrow();
        assertEquals("11000000111001", str.value());

        // Normal case: negative BigInt
        JSBigInt negativeBigInt = new JSBigInt(BigInteger.valueOf(-12345));
        result = BigIntPrototype.toString(ctx, negativeBigInt, new JSValue[]{});
        str = result.asString().orElseThrow();
        assertEquals("-12345", str.value());

        // Edge case: radix 2 (minimum)
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(2)});
        str = result.asString().orElseThrow();
        assertEquals("11000000111001", str.value());

        // Edge case: radix 36 (maximum)
        result = BigIntPrototype.toString(ctx, bigInt, new JSValue[]{new JSNumber(36)});
        str = result.asString().orElseThrow();
        assertEquals("9ix", str.value());

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
