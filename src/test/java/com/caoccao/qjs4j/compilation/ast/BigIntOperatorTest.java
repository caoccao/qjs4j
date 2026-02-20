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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class BigIntOperatorTest extends BaseJavetTest {
    @Test
    public void testBigIntArithmeticAndBitwiseOperators() {
        assertBooleanWithJavet(
                """
                        (() => {
                        const a = 5n;
                        const b = 2n;
                        return a + b === 7n
                          && a - b === 3n
                          && a * b === 10n
                          && a / b === 2n
                          && a % b === 1n
                          && a ** b === 25n
                          && ~a === -6n
                          && (a & b) === 0n
                          && (a | b) === 7n
                          && (a ^ b) === 7n
                          && (a << 1n) === 10n
                          && (a >> 1n) === 2n;
                        })()""");
    }

    @Test
    public void testBigIntComparisonAndEqualityOperators() {
        assertBooleanWithJavet(
                """
                        (() => {
                        return (1n < 2)
                          && (2n > 1)
                          && (1n <= 1)
                          && (1n >= 1)
                          && (1n == 1)
                          && !(1n == 1.5)
                          && (1n < '2')
                          && !('x' < 1n)
                          && !(1n < NaN);
                        })()""");
    }

    @Test
    public void testBigIntIncDecAndCompoundAdd() {
        assertBooleanWithJavet(
                """
                        (() => {
                        let x = 1n;
                        const y = x++;
                        const z = ++x;
                        const a = x--;
                        const b = --x;
                        let v = 1n;
                        v += 2n;
                        ++v;
                        --v;
                        return y === 1n
                          && z === 3n
                          && a === 3n
                          && b === 1n
                          && x === 1n
                          && v === 3n;
                        })()""");
    }

    @Test
    public void testBigIntOperatorErrors() {
        assertBooleanWithJavet(
                """
                        (() => {
                        try { 1n + 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n - 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n * 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n / 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n % 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n ** 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n & 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n | 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n ^ 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n << 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n >> 1; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { 1n >>> 1n; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        try { +1n; return false; } catch (e) { if (!(e instanceof TypeError)) return false; }
                        return true;
                        })()""");
    }

    @Test
    public void testBigIntRangeErrors() {
        assertBooleanWithJavet(
                """
                        (() => {
                        try { 1n / 0n; return false; } catch (e) { if (!(e instanceof RangeError)) return false; }
                        try { 1n % 0n; return false; } catch (e) { if (!(e instanceof RangeError)) return false; }
                        try { 2n ** (-1n); return false; } catch (e) { if (!(e instanceof RangeError)) return false; }
                        return true;
                        })()""");
    }
}
