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

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BigInt constructor and static methods.
 */
public class BigIntConstructorTest extends BaseJavetTest {

    @Test
    public void testAsIntN() {
        // Normal case: positive value within range
        JSValue result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(127))
        });
        JSBigInt bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(127));

        // Normal case: negative value
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-1))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(-1));

        // Normal case: wrap around positive
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(128))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(-128));

        // Normal case: wrap around negative
        result = BigIntConstructor.asIntN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-129))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(127));

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
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(255));

        // Normal case: wrap around
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(256))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.ZERO);

        // Normal case: negative value becomes positive
        result = BigIntConstructor.asUintN(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(8), new JSBigInt(BigInteger.valueOf(-1))
        });
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(255));

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
        assertLongWithJavet("""
                var obj = Object(BigInt(10));
                obj.valueOf()""");
    }

    @Test
    public void testBigIntObjectComparison() {
        // Test using valueOf for comparison
        assertLongWithJavet("""
                var obj = Object(BigInt(42));
                var val = obj.valueOf();
                val""");
    }

    @Test
    public void testBigIntObjectCreation() {
        // Test that 'Object(BigInt(42))' creates a JSBigIntObject
        assertBigIntegerObjectWithJavet("Object(BigInt(42))");
    }

    @Test
    public void testBigIntObjectEquality() {
        // BigInt object is not the same as primitive when checking with typeof
        assertStringWithJavet(
                "typeof Object(BigInt(42))",
                "typeof BigInt(42)");
    }

    @Test
    public void testBigIntObjectLargeValue() {
        assertLongWithJavet("Object(BigInt('9007199254740991')).valueOf()");
    }

    @Test
    public void testBigIntObjectNegative() {
        assertLongWithJavet("Object(BigInt(-999)).valueOf()");
    }

    @Test
    public void testBigIntObjectToString() {
        assertStringWithJavet("Object(BigInt(123)).toString()");
    }

    @Test
    public void testBigIntObjectToStringWithRadix() {
        assertStringWithJavet("Object(BigInt(255)).toString(16)");
    }

    @Test
    public void testBigIntObjectTypeof() {
        assertStringWithJavet("typeof Object(BigInt(42))");
    }

    @Test
    public void testBigIntObjectValueOf() {
        assertLongWithJavet("Object(BigInt(42)).valueOf()");
    }

    @Test
    public void testCall() {
        // Normal case: from number
        JSValue result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        JSBigInt bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(123));

        // Normal case: from BigInt
        bigInt = new JSBigInt(BigInteger.valueOf(456));
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{bigInt});
        assertThat(result).isEqualTo(bigInt);

        // Normal case: from string (decimal)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("789")});
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(789));

        // Normal case: from string (hex)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0xFF")});
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(255));

        // Normal case: from string (octal)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0o77")});
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(63));

        // Normal case: from string (binary)
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("0b101")});
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(5));

        // Normal case: from boolean true
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.TRUE});
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.ONE);

        // Normal case: from boolean false
        result = BigIntConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.FALSE});
        bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.ZERO);

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
    public void testCallWithToPrimitive() {
        // BigInt() should call ToPrimitive(value, number) on objects before conversion
        assertLongWithJavet(
                "BigInt({valueOf: () => 3})",
                "BigInt({valueOf: () => true})",
                "BigInt({toString: () => '42'})"
        );
        assertErrorWithJavet(
                "BigInt({valueOf: () => 1.5})",
                "BigInt(Symbol('foo'))"
        );
    }

    @Test
    public void testNewBigIntThrowsTypeError() {
        // BigInt cannot be called with 'new' operator per ES2020 spec
        assertErrorWithJavet("new BigInt(123)");
        // The correct way to create a BigInt object is Object(BigInt())
        assertThat(context.eval("Object(BigInt(123))"))
                .isInstanceOfSatisfying(JSBigIntObject.class, bigIntObj ->
                        assertThat(bigIntObj.getValue().value()).isEqualTo(BigInteger.valueOf(123)));
    }
}
