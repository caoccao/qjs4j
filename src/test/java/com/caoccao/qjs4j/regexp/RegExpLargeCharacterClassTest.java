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

package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.utils.DynamicBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A {@code RANGE} instruction used to declare its payload size and its range count in unsigned
 * 16-bit fields, and {@link DynamicBuffer#appendU16(int)} wrote the low two bytes of whatever it
 * was handed. At 8,192 disjoint ranges the payload is 65,538 bytes, which was encoded as 2: the
 * matcher resumed decoding opcodes in the middle of the range data, so a valid pattern compiled
 * without complaint and then matched the wrong thing.
 * <p>
 * 8,191 ranges is the last size that fit and 8,192 the first that did not, so both are pinned,
 * along with a size well past the old ceiling.
 */
public class RegExpLargeCharacterClassTest extends BaseJavetTest {
    private static final String BUILD_PATTERN = """
            function classOf(count, negated) {
              var p = negated ? '[^' : '[';
              for (var i = 0; i < count; i++) { p += '\\\\u{' + (i * 2).toString(16) + '}'; }
              return p + ']';
            }
            """;

    @Test
    public void testAppendU16RejectsValuesThatDoNotFit() {
        DynamicBuffer buffer = new DynamicBuffer();
        assertThatThrownBy(() -> buffer.appendU16(0x10000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> buffer.appendU16(-1)).isInstanceOf(IllegalArgumentException.class);
        buffer.appendU16(0xFFFF);
        buffer.appendU16(0);
    }

    @Test
    public void testLargeClassMatchesAndRejectsCorrectly() {
        for (int count : new int[]{8191, 8192, 20000}) {
            assertBooleanWithJavet(BUILD_PATTERN
                    + "new RegExp(classOf(" + count + ", false), 'u').test(String.fromCodePoint(0));");
            assertBooleanWithJavet(BUILD_PATTERN
                    + "new RegExp(classOf(" + count + ", false), 'u').test(String.fromCodePoint(1));");
            assertBooleanWithJavet(BUILD_PATTERN
                    + "new RegExp(classOf(" + count + ", false), 'u').test(String.fromCodePoint((" + count + " - 1) * 2));");
        }
    }

    @Test
    public void testLargeClassWithIgnoreCase() {
        assertBooleanWithJavet(BUILD_PATTERN
                + "new RegExp(classOf(8192, false), 'ui').test(String.fromCodePoint(0));");
        assertBooleanWithJavet(BUILD_PATTERN
                + "new RegExp(classOf(8192, false), 'ui').test(String.fromCodePoint(1));");
    }

    @Test
    public void testLargeInvertedClass() {
        for (int count : new int[]{8191, 8192}) {
            assertBooleanWithJavet(BUILD_PATTERN
                    + "new RegExp(classOf(" + count + ", true), 'u').test(String.fromCodePoint(0));");
            assertBooleanWithJavet(BUILD_PATTERN
                    + "new RegExp(classOf(" + count + ", true), 'u').test(String.fromCodePoint(1));");
        }
    }

    @Test
    public void testOrdinaryClassesAndShorthandsAreUnaffected() {
        assertStringWithJavet("/[a-c]+/.exec('xxabcyy')[0];");
        assertStringWithJavet("/[^a-c]+/.exec('abxyzc')[0];");
        assertStringWithJavet("/\\w+/.exec('ab_9!')[0];");
        assertStringWithJavet("/\\W+/.exec('ab_9!!')[0];");
        assertStringWithJavet("/\\d+/.exec('a123b')[0];");
        assertStringWithJavet("/\\D+/.exec('12ab34')[0];");
        assertStringWithJavet("/\\w+/iu.exec('ab_9!')[0];");
        assertStringWithJavet("/\\W+/iu.exec('ab_9!!')[0];");
    }

    @Test
    public void testUnicodeSetsClassWithManyRanges() {
        assertBooleanWithJavet(BUILD_PATTERN
                + "new RegExp(classOf(9000, false), 'v').test(String.fromCodePoint(0));");
        assertBooleanWithJavet(BUILD_PATTERN
                + "new RegExp(classOf(9000, false), 'v').test(String.fromCodePoint(1));");
    }
}
