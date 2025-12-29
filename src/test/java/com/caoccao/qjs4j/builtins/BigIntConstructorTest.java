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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BigInt constructor and static methods.
 */
public class BigIntConstructorTest extends BaseTest {

    @Test
    public void testAsIntN() {
        // Normal case: positive value within range
        JSValue result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(127))
        });
        JSBigInt bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(127), bigInt.value());

        // Normal case: negative value
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-1))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(-1), bigInt.value());

        // Normal case: wrap around positive
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(128))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(-128), bigInt.value());

        // Normal case: wrap around negative
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-129))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(127), bigInt.value());

        // Edge case: insufficient arguments
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(8)});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: first argument not a number
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("8"), new JSBigInt(BigInteger.ONE)
        });
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: second argument not a BigInt
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSNumber(123)
        });
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: negative bits
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-1), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: bits too large
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(2147483648L), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(context);
    }

    @Test
    public void testAsUintN() {
        // Normal case: positive value within range
        JSValue result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(255))
        });
        JSBigInt bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(255), bigInt.value());

        // Normal case: wrap around
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(256))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.ZERO, bigInt.value());

        // Normal case: negative value becomes positive
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-1))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(255), bigInt.value());

        // Edge case: insufficient arguments
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(8)});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: first argument not a number
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("8"), new JSBigInt(BigInteger.ONE)
        });
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: second argument not a BigInt
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSNumber(123)
        });
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: negative bits
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(-1), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: bits too large
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(NumberPrototype.MAX_SAFE_INTEGER + 1), new JSBigInt(BigInteger.ONE)
        });
        assertRangeError(result);
        assertPendingException(context);
    }

    @Test
    public void testBigIntObjectArithmetic() {
        // BigInt objects can be converted via valueOf
        JSValue result = context.eval("""
                var obj = Object(BigInt(10));
                obj.valueOf()""");
        assertTrue(result.isBigInt());
        assertEquals(BigInteger.valueOf(10), result.asBigInt().map(JSBigInt::value).orElseThrow());
    }

    @Test
    public void testBigIntObjectComparison() {
        // Test using valueOf for comparison
        JSValue result = context.eval("""
                var obj = Object(BigInt(42));
                var val = obj.valueOf();
                val""");
        assertTrue(result.isBigInt());
        assertEquals(BigInteger.valueOf(42), result.asBigInt().map(JSBigInt::value).orElseThrow());
    }

    @Test
    public void testBigIntObjectCreation() {
        // Test that 'Object(BigInt(42))' creates a JSBigIntObject
        JSValue result = context.eval("Object(BigInt(42))");
        assertTrue(result.isBigIntObject(), "Object(BigInt(42)) should create a JSBigIntObject");

        JSBigIntObject bigIntObj = (JSBigIntObject) result;
        assertEquals(BigInteger.valueOf(42), bigIntObj.getValue().value());
    }

    @Test
    public void testBigIntObjectEquality() {
        // BigInt object is not the same as primitive when checking with typeof
        JSValue result = context.eval("typeof Object(BigInt(42))");
        assertEquals("object", result.toJavaObject());

        JSValue result2 = context.eval("typeof BigInt(42)");
        assertEquals("bigint", result2.toJavaObject());
    }

    @Test
    public void testBigIntObjectLargeValue() {
        JSValue result = context.eval("Object(BigInt('9007199254740991')).valueOf()");
        assertTrue(result.isBigInt());
        assertEquals(new BigInteger("9007199254740991"), result.asBigInt().map(JSBigInt::value).orElseThrow());
    }

    @Test
    public void testBigIntObjectNegative() {
        JSValue result = context.eval("Object(BigInt(-999)).valueOf()");
        assertTrue(result.isBigInt());
        assertEquals(BigInteger.valueOf(-999), result.asBigInt().map(JSBigInt::value).orElseThrow());
    }

    @Test
    public void testBigIntObjectToString() {
        JSValue result = context.eval("Object(BigInt(123)).toString()");
        assertEquals("123", result.toJavaObject());
    }

    @Test
    public void testBigIntObjectToStringWithRadix() {
        JSValue result = context.eval("Object(BigInt(255)).toString(16)");
        assertEquals("ff", result.toJavaObject());
    }

    @Test
    public void testBigIntObjectTypeof() {
        JSValue result = context.eval("typeof Object(BigInt(42))");
        assertEquals("object", result.toJavaObject());
    }

    @Test
    public void testBigIntObjectValueOf() {
        JSValue result = context.eval("Object(BigInt(42)).valueOf()");
        assertTrue(result.isBigInt());
        assertEquals(BigInteger.valueOf(42), result.asBigInt().map(JSBigInt::value).orElseThrow());
    }

    @Test
    public void testCall() {
        // Normal case: from number
        JSValue result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        JSBigInt bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(123), bigInt.value());

        // Normal case: from BigInt
        bigInt = new JSBigInt(BigInteger.valueOf(456));
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{bigInt});
        assertEquals(bigInt, result);

        // Normal case: from string (decimal)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("789")});
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(789), bigInt.value());

        // Normal case: from string (hex)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0xFF")});
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(255), bigInt.value());

        // Normal case: from string (octal)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0o77")});
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(63), bigInt.value());

        // Normal case: from string (binary)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0b101")});
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.valueOf(5), bigInt.value());

        // Normal case: from boolean true
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.TRUE});
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.ONE, bigInt.value());

        // Normal case: from boolean false
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.FALSE});
        bigInt = result.asBigInt().orElseThrow();
        assertEquals(BigInteger.ZERO, bigInt.value());

        // Edge case: no arguments
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: non-integer number
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(1.5)});
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: Infinity
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.POSITIVE_INFINITY)});
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: NaN
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(Double.NaN)});
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: invalid string
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not a number")});
        assertSyntaxError(result);
        assertPendingException(context);

        // Edge case: unsupported type
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSObject()});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testNewBigIntThrowsTypeError() {
        // BigInt cannot be called with 'new' operator per ES2020 spec
        try {
            context.eval("new BigInt(123)");
            throw new AssertionError("Should throw TypeError when using new BigInt()");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("TypeError") || e.getMessage().contains("BigInt is not a constructor"),
                    "Expected TypeError about BigInt not being a constructor, got: " + e.getMessage());
        }

        // The correct way to create a BigInt object is Object(BigInt())
        JSValue result = context.eval("Object(BigInt(123))");
        assertTrue(result.isBigIntObject(), "Object(BigInt()) should create a BigInt object");
        JSBigIntObject bigIntObj = (JSBigIntObject) result;
        assertEquals(BigInteger.valueOf(123), bigIntObj.getValue().value());
    }
}
