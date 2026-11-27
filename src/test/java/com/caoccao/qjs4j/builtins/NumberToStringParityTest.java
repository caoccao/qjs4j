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
import com.caoccao.qjs4j.utils.DtoaConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Number-to-string conversions print what V8 prints.
 * <p>
 * The shortest-digits search tested only the nearest decimal at each length, so {@code String(2 ** -24)} had 17 digits
 * where 16 round-trip; {@code toString(radix)} used QuickJS's algorithm, which prints more digits than the input's
 * precision supports; and {@code toLocaleString()} without arguments skipped {@code Intl.NumberFormat}.
 */
public class NumberToStringParityTest extends BaseJavetTest {
    private static final String RANDOM_VALUES = """
            var seed = 12345;
            function rnd() { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; }
            var view = new DataView(new ArrayBuffer(8));
            var values = [];
            for (var i = 0; i < 500; i++) {
              view.setUint32(0, Math.floor(rnd() * 4294967296));
              view.setUint32(4, Math.floor(rnd() * 4294967296));
              var bits = view.getFloat64(0);
              if (Number.isFinite(bits)) values.push(bits);
              values.push(rnd() * 1000, Math.floor(rnd() * 1e6) / 1000, rnd() * 1e-10, rnd() * 1e25);
            }
            """;

    @Test
    public void testAutomaticExponentialUsesShortestDigitsAtPowersOfTwo() {
        assertStringWithJavet("(2 ** -24).toExponential()", "(-(2 ** -24)).toExponential()");
    }

    @Test
    public void testPowersOfTwoUseTheShortestDigits() {
        assertStringWithJavet("String(2 ** -24)", "String(new Float16Array([2 ** -24])[0])", """
                var out = [];
                for (var e = -1074; e <= 1023; e++) {
                  out.push(String(2 ** e), String(-(2 ** e)), String(2 ** e * (1 + 2 ** -52)));
                }
                out.join()""");
        assertThat(DtoaConverter.convert(Math.pow(2, -24))).isEqualTo("5.960464477539063e-8");
    }

    @Test
    public void testRadix() {
        assertStringWithJavet("(1e21).toString(36)", "(1/3).toString(36)", "(0.1).toString(36)", "(0.5).toString(2)",
                "(-255.5).toString(16)", "(2 ** 53 + 2).toString(7)", "(1.7976931348623157e308).toString(36)",
                "(5e-324).toString(2).length + ''", "(-0).toString(2)", "NaN.toString(16)", "(-Infinity).toString(3)",
                "(0.9999999999999999).toString(3)", "(35.99999999999999).toString(36)", RANDOM_VALUES + """
                        var out = [];
                        for (var v of values) {
                          for (var r of [2, 3, 7, 8, 16, 32, 36]) out.push(v.toString(r));
                        }
                        out.join()""");
    }

    @Test
    public void testRadixOfNonFiniteValues() {
        assertStringWithJavet("[NaN.toString(2), Infinity.toString(16), (-Infinity).toString(36)].join()");
        // Number.prototype.toString answers these itself; the converter answers them for a Java caller.
        assertThat(DtoaConverter.convertToRadix(Double.NaN, 2)).isEqualTo("NaN");
        assertThat(DtoaConverter.convertToRadix(Double.POSITIVE_INFINITY, 16)).isEqualTo("Infinity");
        assertThat(DtoaConverter.convertToRadix(Double.NEGATIVE_INFINITY, 36)).isEqualTo("-Infinity");
    }

    @Test
    public void testShortestDecimal() {
        assertStringWithJavet(RANDOM_VALUES + "values.map(String).join()",
                "[0.1, 0.2 + 0.1, 1e21, 1e-7, 123e-20, 5e-324, 1.7976931348623157e308, 2 ** 53 + 2].map(String).join()");
    }

    @Test
    public void testToLocaleStringUsesIntl() {
        assertStringWithJavet("(1234567.891).toLocaleString()", "(0).toLocaleString()", "(-1e21).toLocaleString()",
                "NaN.toLocaleString()", "(1.5).toLocaleString()", "(1234.5).toLocaleString('de-DE')",
                "(0.000001234).toLocaleString()", "Infinity.toLocaleString()");
    }
}
