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

package com.caoccao.qjs4j.builtins.number;

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
        var asIntN = builtin(BigIntConstructor::asIntN);
        JSValue result = asIntN.call(8, new JSBigInt(BigInteger.valueOf(127)));
        assertThat(result.asBigInt().orElseThrow().value()).isEqualTo(BigInteger.valueOf(127));

        // Normal case: negative value
        assertThat(asIntN.call(8, new JSBigInt(BigInteger.valueOf(-1))).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.valueOf(-1));

        // Normal case: wrap around positive
        assertThat(asIntN.call(8, new JSBigInt(BigInteger.valueOf(128))).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.valueOf(-128));

        // Normal case: wrap around negative
        assertThat(asIntN.call(8, new JSBigInt(BigInteger.valueOf(-129))).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.valueOf(127));

        // Edge case: insufficient arguments
        assertPendingTypeError(asIntN.call(8));

        // Edge case: first argument is coerced via ToIndex
        assertThat(asIntN.call("8", new JSBigInt(BigInteger.ONE)).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.ONE);

        // Edge case: second argument not a BigInt
        assertPendingTypeError(asIntN.call(8, 123));

        // Edge case: negative bits
        assertPendingRangeError(asIntN.call(-1, new JSBigInt(BigInteger.ONE)));

        // Edge case: bits larger than Integer.MAX_VALUE are still valid (ToIndex)
        assertThat(asIntN.call(2147483648L, new JSBigInt(BigInteger.ONE)).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.ONE);
    }

    @Test
    public void testAsUintN() {
        // Normal case: positive value within range
        var asUintN = builtin(BigIntConstructor::asUintN);
        JSValue result = asUintN.call(8, new JSBigInt(BigInteger.valueOf(255)));
        assertThat(result.asBigInt().orElseThrow().value()).isEqualTo(BigInteger.valueOf(255));

        // Normal case: wrap around
        assertThat(asUintN.call(8, new JSBigInt(BigInteger.valueOf(256))).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.ZERO);

        // Normal case: negative value becomes positive
        assertThat(asUintN.call(8, new JSBigInt(BigInteger.valueOf(-1))).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.valueOf(255));

        // Edge case: insufficient arguments
        assertPendingTypeError(asUintN.call(8));

        // Edge case: first argument is coerced via ToIndex
        assertThat(asUintN.call("8", new JSBigInt(BigInteger.ONE)).asBigInt().orElseThrow().value())
                .isEqualTo(BigInteger.ONE);

        // Edge case: second argument not a BigInt
        assertPendingTypeError(asUintN.call(8, 123));

        // Edge case: negative bits
        assertPendingRangeError(asUintN.call(-1, new JSBigInt(BigInteger.ONE)));

        // Edge case: bits too large
        assertPendingRangeError(asUintN.call(NumberPrototype.MAX_SAFE_INTEGER + 1, new JSBigInt(BigInteger.ONE)));
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
        assertStringWithJavet("typeof Object(BigInt(42))", "typeof BigInt(42)");
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
        var bigIntConstructor = builtin(BigIntConstructor::call);
        JSValue result = bigIntConstructor.call(123);
        JSBigInt bigInt = result.asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(123));

        // Normal case: from BigInt
        bigInt = new JSBigInt(BigInteger.valueOf(456));
        assertThat(bigIntConstructor.call(bigInt)).isEqualTo(bigInt);

        // Normal case: from string (decimal)
        bigInt = bigIntConstructor.call("789").asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(789));

        // Normal case: from string (hex)
        bigInt = bigIntConstructor.call("0xFF").asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(255));

        // Normal case: from string (octal)
        bigInt = bigIntConstructor.call("0o77").asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(63));

        // Normal case: from string (binary)
        bigInt = bigIntConstructor.call("0b101").asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.valueOf(5));

        // Normal case: from boolean true
        bigInt = bigIntConstructor.call(JSBoolean.TRUE).asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.ONE);

        // Normal case: from boolean false
        bigInt = bigIntConstructor.call(JSBoolean.FALSE).asBigInt().orElseThrow();
        assertThat(bigInt.value()).isEqualTo(BigInteger.ZERO);

        // Edge case: no arguments
        assertPendingTypeError(bigIntConstructor.call());

        // Edge case: non-integer number
        assertPendingRangeError(bigIntConstructor.call(1.5));

        // Edge case: Infinity
        assertPendingRangeError(bigIntConstructor.call(Double.POSITIVE_INFINITY));

        // Edge case: NaN
        assertPendingRangeError(bigIntConstructor.call(Double.NaN));

        // Edge case: invalid string
        assertPendingSyntaxError(bigIntConstructor.call("not a number"));

        // Edge case: unsupported type
        assertPendingTypeError(bigIntConstructor.call(new JSObject(context)));
    }

    @Test
    public void testCallWithToPrimitive() {
        // BigInt() should call ToPrimitive(value, number) on objects before conversion
        assertLongWithJavet("BigInt({valueOf: () => 3})", "BigInt({valueOf: () => true})",
                "BigInt({toString: () => '42'})");
        assertErrorWithJavet("BigInt({valueOf: () => 1.5})", "BigInt(Symbol('foo'))");
    }

    @Test
    public void testNewBigIntThrowsTypeError() {
        // BigInt cannot be called with 'new' operator per ES2020 spec
        assertErrorWithJavet("new BigInt(123)");
        // The correct way to create a BigInt object is Object(BigInt())
        assertThat(context.eval("Object(BigInt(123))")).isInstanceOfSatisfying(JSBigIntObject.class,
                bigIntObj -> assertThat(bigIntObj.getValue().value()).isEqualTo(BigInteger.valueOf(123)));
    }
}
