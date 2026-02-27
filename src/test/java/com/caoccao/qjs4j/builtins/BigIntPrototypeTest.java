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
import com.caoccao.qjs4j.core.JSBigInt;
import com.caoccao.qjs4j.core.JSNumber;
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
                "123n == 123n",
                "123n == 321n",
                "123n == BigInt(123)",
                "123n == BigInt(321)",
                // Verify that strict equality passes between primitive and primitive
                "123n === 123n",
                "123n === 321n",
                "123n === BigInt(123)",
                "123n === BigInt(321)",
                // Verify that loose equality passes between primitive and primitive
                "BigInt(123) == BigInt(123)",
                "BigInt(123) == BigInt(321)",
                "BigInt(123) == 123n",
                "BigInt(123) == 321n",
                // Verify that loose equality passes between primitive and object
                "123n == Object(BigInt(123))",
                "123n == Object(BigInt(321))",
                "BigInt(123) == Object(BigInt(123))",
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
        JSValue result = BigIntPrototype.toLocaleString(context, bigInt, new JSValue[]{new JSString("en-US")});
        JSString str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("12,345");

        // Normal case: small number (no grouping needed)
        JSBigInt smallBigInt = new JSBigInt(BigInteger.valueOf(123));
        result = BigIntPrototype.toLocaleString(context, smallBigInt, new JSValue[]{new JSString("en-US")});
        str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("123");

        // Edge case: called on non-BigInt
        result = BigIntPrototype.toLocaleString(context, new JSString("not a bigint"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testToString() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: default radix (10)
        JSValue result = BigIntPrototype.toString(context, bigInt, new JSValue[]{});
        JSString str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("12345");

        // Normal case: explicit radix 10
        result = BigIntPrototype.toString(context, bigInt, new JSValue[]{new JSNumber(10)});
        str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("12345");

        // Normal case: radix 16
        result = BigIntPrototype.toString(context, bigInt, new JSValue[]{new JSNumber(16)});
        str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("3039");

        // Normal case: radix 2
        result = BigIntPrototype.toString(context, bigInt, new JSValue[]{new JSNumber(2)});
        str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("11000000111001");

        // Normal case: negative BigInt
        JSBigInt negativeBigInt = new JSBigInt(BigInteger.valueOf(-12345));
        result = BigIntPrototype.toString(context, negativeBigInt, new JSValue[]{});
        str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("-12345");

        // Edge case: radix 2 (minimum)
        result = BigIntPrototype.toString(context, bigInt, new JSValue[]{new JSNumber(2)});
        str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("11000000111001");

        // Edge case: radix 36 (maximum)
        result = BigIntPrototype.toString(context, bigInt, new JSValue[]{new JSNumber(36)});
        str = result.asString().orElseThrow();
        assertThat(str.value()).isEqualTo("9ix");

        // Edge case: radix too small
        result = BigIntPrototype.toString(context, bigInt, new JSValue[]{new JSNumber(1)});
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: radix too large
        result = BigIntPrototype.toString(context, bigInt, new JSValue[]{new JSNumber(37)});
        assertRangeError(result);
        assertPendingException(context);

        // Edge case: called on non-BigInt
        result = BigIntPrototype.toString(context, new JSString("not a bigint"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        assertStringWithJavet("Object(BigInt(123n)).toString()");
    }

    @Test
    public void testValueOf() {
        JSBigInt bigInt = new JSBigInt(BigInteger.valueOf(12345));
        // Normal case: BigInt
        JSValue result = BigIntPrototype.valueOf(context, bigInt, new JSValue[]{});
        assertThat(result).isEqualTo(bigInt);

        // Edge case: called on non-BigInt
        result = BigIntPrototype.valueOf(context, new JSString("not a bigint"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        assertLongWithJavet("Object(BigInt(123n)).valueOf()");
    }
}
