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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporalMathKernelTest extends BaseTest {
    @Test
    public void testIsValidSubDayRoundingIncrement() {
        assertThat(TemporalMathKernel.isValidSubDayRoundingIncrement("hour", 1L)).isTrue();
        assertThat(TemporalMathKernel.isValidSubDayRoundingIncrement("hour", 3L)).isTrue();
        assertThat(TemporalMathKernel.isValidSubDayRoundingIncrement("hour", 5L)).isFalse();
        assertThat(TemporalMathKernel.isValidSubDayRoundingIncrement("minute", 30L)).isTrue();
        assertThat(TemporalMathKernel.isValidSubDayRoundingIncrement("minute", 61L)).isFalse();
        assertThat(TemporalMathKernel.isValidSubDayRoundingIncrement("day", 1L)).isFalse();
    }

    @Test
    public void testRoundBigIntegerToIncrementAsIfPositive() {
        BigInteger value = BigInteger.valueOf(15L);
        BigInteger increment = BigInteger.TEN;
        assertThat(TemporalMathKernel.roundBigIntegerToIncrementAsIfPositive(
                value,
                increment,
                "halfExpand")).isEqualTo(BigInteger.valueOf(20L));
        assertThat(TemporalMathKernel.roundBigIntegerToIncrementAsIfPositive(
                value,
                increment,
                "halfTrunc")).isEqualTo(BigInteger.valueOf(10L));
    }

    @Test
    public void testRoundBigIntegerToIncrementSigned() {
        BigInteger value = BigInteger.valueOf(-15L);
        BigInteger increment = BigInteger.TEN;
        assertThat(TemporalMathKernel.roundBigIntegerToIncrementSigned(
                value,
                increment,
                "trunc")).isEqualTo(BigInteger.valueOf(-10L));
        assertThat(TemporalMathKernel.roundBigIntegerToIncrementSigned(
                value,
                increment,
                "floor")).isEqualTo(BigInteger.valueOf(-20L));
    }

    @Test
    public void testRoundLongToIncrementAsIfPositive() {
        assertThat(TemporalMathKernel.roundLongToIncrementAsIfPositive(
                155L,
                10L,
                "halfFloor")).isEqualTo(150L);
        assertThat(TemporalMathKernel.roundLongToIncrementAsIfPositive(
                155L,
                10L,
                "halfExpand")).isEqualTo(160L);
    }
}
