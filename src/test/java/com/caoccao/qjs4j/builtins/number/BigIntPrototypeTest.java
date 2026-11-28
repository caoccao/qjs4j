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
import com.caoccao.qjs4j.core.JSBigInt;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BigIntPrototype methods.
 */
public class BigIntPrototypeTest extends BaseJavetTest {

    @Test
    public void testEquals() {
        assertBooleanWithJavet(
                // Verify that loose equality passes between primitive and primitive
                "123n == 123n", "123n == 321n", "123n == BigInt(123)", "123n == BigInt(321)",
                // Verify that strict equality passes between primitive and primitive
                "123n === 123n", "123n === 321n", "123n === BigInt(123)", "123n === BigInt(321)",
                // Verify that loose equality passes between primitive and primitive
                "BigInt(123) == BigInt(123)", "BigInt(123) == BigInt(321)", "BigInt(123) == 123n",
                "BigInt(123) == 321n",
                // Verify that loose equality passes between primitive and object
                "123n == Object(BigInt(123))", "123n == Object(BigInt(321))", "BigInt(123) == Object(BigInt(123))",
                "BigInt(123) == Object(BigInt(321))",
                // Verify that loose equality fails between object and object
                "Object(BigInt(123)) == Object(BigInt(123))",
                // Verify that strict equality fails between primitive and object
                "123n === Object(BigInt(123))",
                // Verify that strict equality fails between object and object
                "Object(BigInt(123)) === Object(BigInt(123))");
    }

    @Test
    public void testLiterals() {
        // Test typeof for BigInt literal
        assertStringWithJavet("typeof 123n");
        // Test basic decimal BigInt literal
        assertLongWithJavet("123n", "0xFFn", "0b1111n", "0o77n", "9007199254740991n");
    }

    @Test
    public void testToLocaleString() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: with locale — grouping is enabled by default per Intl.NumberFormat spec
        var toLocaleString = nativeMethod(BigIntPrototype::toLocaleString);
        JSValue result = toLocaleString.call(bigInt, "en-US");
        assertThat(result.asString().orElseThrow().value()).isEqualTo("12,345");

        // Normal case: small number (no grouping needed)
        JSBigInt smallBigInt = new JSBigInt(BigInteger.valueOf(123));
        assertThat(toLocaleString.call(smallBigInt, "en-US").asString().orElseThrow().value()).isEqualTo("123");

        // Edge case: called on non-BigInt
        assertPendingTypeError(toLocaleString.call("not a bigint"));
    }

    @Test
    public void testToString() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: default radix (10)
        var toString = builtin(BigIntPrototype::toString, bigInt);
        JSValue result = toString.call();
        assertThat(result.asString().orElseThrow().value()).isEqualTo("12345");

        // Normal case: explicit radix 10
        assertThat(toString.call(10).asString().orElseThrow().value()).isEqualTo("12345");

        // Normal case: radix 16
        assertThat(toString.call(16).asString().orElseThrow().value()).isEqualTo("3039");

        // Normal case: radix 2
        result = toString.call(2);
        assertThat(result.asString().orElseThrow().value()).isEqualTo("11000000111001");

        // Normal case: negative BigInt
        JSBigInt negativeBigInt = new JSBigInt(BigInteger.valueOf(-12345));
        var toStringMethod = nativeMethod(BigIntPrototype::toString);
        assertThat(toStringMethod.call(negativeBigInt).asString().orElseThrow().value()).isEqualTo("-12345");

        // Edge case: radix 2 (minimum)
        assertThat(toString.call(2).asString().orElseThrow().value()).isEqualTo("11000000111001");

        // Edge case: radix 36 (maximum)
        assertThat(toString.call(36).asString().orElseThrow().value()).isEqualTo("9ix");

        // Edge case: radix too small
        assertPendingRangeError(toString.call(1));

        // Edge case: radix too large
        assertPendingRangeError(toString.call(37));

        // Edge case: called on non-BigInt
        assertPendingTypeError(toStringMethod.call("not a bigint"));

        assertStringWithJavet("Object(BigInt(123n)).toString()");
    }

    @Test
    public void testValueOf() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: BigInt
        var valueOf = nativeMethod(BigIntPrototype::valueOf);
        assertThat(valueOf.call(bigInt)).isEqualTo(bigInt);

        // Edge case: called on non-BigInt
        assertPendingTypeError(valueOf.call("not a bigint"));

        assertLongWithJavet("Object(BigInt(123n)).valueOf()");
    }
}
