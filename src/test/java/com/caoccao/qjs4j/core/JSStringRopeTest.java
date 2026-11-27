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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A long {@code +} or {@code +=} result is a rope, flattened the first time its characters are needed.
 * <p>
 * Every concatenation used to copy both operands, so building a string by appending in a loop was quadratic. The script
 * cases compare every observable use of a rope — reading characters, comparing, hashing it as a key, serializing,
 * appending in both directions — against V8; the unit cases pin the representation itself.
 */
public class JSStringRopeTest extends BaseJavetTest {

    @Test
    public void testAppendingAShortStringRebuildsTheLastLeaf() {
        // 100,000 one-character appends: a node per append would make the rope as deep as it is long.
        JSString rope = new JSString("a".repeat(JSString.ROPE_MIN_LENGTH));
        JSString x = new JSString("x");
        for (int i = 0; i < 100_000; i++) {
            rope = JSString.concat(rope, x);
        }
        assertThat(rope.isRope()).isTrue();
        assertThat(rope.length()).isEqualTo(JSString.ROPE_MIN_LENGTH + 100_000);
        assertThat(rope.value()).startsWith("aaa").endsWith("xxx").hasSize(JSString.ROPE_MIN_LENGTH + 100_000);
    }

    @Test
    public void testAppendingInALoop() {
        assertStringWithJavet("var s = ''; for (var i = 0; i < 20000; i++) { s += 'x' } s.length + ':' + s.slice(-3)",
                "var s = ''; for (var i = 0; i < 20000; i++) { s = s + i } s.length + ':' + s.slice(-10)",
                "var s = ''; for (var i = 0; i < 2000; i++) { s = i + s } s.length + ':' + s.slice(0, 10)",
                "var s = ''; for (var i = 0; i < 20000; i++) { s += 'x'; if (s.length !== i + 1) throw 0 } s.length + ''",
                "var s = ''; var t = ''; for (var i = 0; i < 2000; i++) { s += 'ab'; t += s.charAt(i) } t.slice(-8)",
                "var s = 'a'; for (var i = 0; i < 12; i++) { s = s + s } s.length + ':' + s.indexOf('b')",
                "var s = ''; for (var i = 0; i < 1000; i++) { s += `${i},` } s.split(',').length + ''",
                "var s = '0123456789'.repeat(30); s += 'tail'; s.length + ':' + s.at(-1) + ':' + s[0]");
    }

    @Test
    public void testAShortConcatenationIsFlat() {
        JSString hello = JSString.concat(new JSString("hello, "), new JSString("world"));
        assertThat(hello.isRope()).isFalse();
        assertThat(hello.value()).isEqualTo("hello, world");

        JSString text = new JSString("x".repeat(JSString.ROPE_MIN_LENGTH));
        JSString empty = new JSString("");
        assertThat(JSString.concat(empty, text)).isSameAs(text);
        assertThat(JSString.concat(text, empty)).isSameAs(text);
    }

    @Test
    public void testDeepRopeFlattensWithoutRecursion() {
        // Long leaves defeat leaf merging, so this rope really is 100,000 nodes deep.
        JSString leaf = new JSString("y".repeat(JSString.ROPE_MIN_LENGTH));
        JSString rope = leaf;
        for (int i = 0; i < 100_000; i++) {
            rope = JSString.concat(rope, leaf);
        }
        assertThat(rope.isRope()).isTrue();
        assertThat(rope.toString()).hasSize(JSString.ROPE_MIN_LENGTH * 100_001);
        assertThat(rope.isRope()).isFalse();
    }

    @Test
    public void testEqualityAndHashingFollowTheCharacters() {
        JSString half = new JSString("z".repeat(JSString.ROPE_MIN_LENGTH));
        JSString rope = JSString.concat(half, half);
        JSString flat = new JSString("z".repeat(JSString.ROPE_MIN_LENGTH * 2));
        assertThat(rope.isRope()).isTrue();
        assertThat(rope).isEqualTo(flat).hasSameHashCodeAs(flat);
        assertThat(rope.toJavaObject()).isEqualTo(flat.value());
        assertThat(rope.type()).isEqualTo(JSValueType.STRING);
        assertThat(rope).isNotEqualTo(half).isNotEqualTo(flat.value()).isEqualTo(rope);
        assertThat(new JSString("a", 1)).isNotEqualTo(new JSString("a", 2));
        assertThat(new JSString("a", 3).atomIndex()).isEqualTo(3);
    }

    @Test
    public void testIndexedCharactersPreserveUtf16CodeUnits() {
        for (char unit : new char[]{0, 127, 128, 'é', Character.MIN_HIGH_SURROGATE, Character.MAX_LOW_SURROGATE}) {
            JSString character = JSString.of(unit);
            assertThat(character.value()).isEqualTo(String.valueOf(unit));
            assertThat(character.atomIndex()).isEqualTo(-1);
        }
        assertThat(JSString.of('a')).isSameAs(JSString.of('a'));
    }

    @Test
    public void testOnlyAShortLastLeafIsRebuilt() {
        String big = "b".repeat(JSString.ROPE_MIN_LENGTH);
        JSString x = new JSString("x");
        // The last leaf is long: a node is added instead.
        JSString longLeaf = JSString.concat(JSString.concat(new JSString(big), new JSString(big)), x);
        // The last child is itself a rope.
        JSString ropeChild = JSString
                .concat(JSString.concat(new JSString(big), JSString.concat(new JSString(big), new JSString(big))), x);
        // The appended string is long.
        JSString longRight = JSString.concat(JSString.concat(new JSString(big), x), new JSString(big));
        for (JSString rope : new JSString[]{longLeaf, ropeChild, longRight}) {
            assertThat(rope.isRope()).isTrue();
        }
        assertThat(longLeaf.value()).isEqualTo(big + big + "x");
        assertThat(ropeChild.value()).isEqualTo(big + big + big + "x");
        assertThat(longRight.value()).isEqualTo(big + "x" + big);
    }

    @Test
    public void testReadingTheLengthDoesNotFlatten() {
        assertThat(context.eval("var s = ''; for (var i = 0; i < 1000; i++) { s += 'x'; s.length } s"))
                .isInstanceOfSatisfying(JSString.class, string -> {
                    assertThat(string.isRope()).isTrue();
                    assertThat(string.length()).isEqualTo(1000);
                });
        assertIntegerWithJavet("'abc'.length", "var s = 'abc'; s.length",
                "var s = '0123456789'.repeat(30) + 'x'; s.length", "var s = ''; s.length", "String.prototype.length");
        assertErrorWithJavet("var s = 'abc'; s.length()");
        assertStringWithJavet("var s = 'abc'; typeof s.length",
                "'use strict'; var s = 'abc'; try { s.length = 1; 'no' } catch (e) { e.constructor.name }");
    }

    @Test
    public void testRopesAreUsableEverywhereAStringIs() {
        assertBooleanWithJavet(
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); r === '0123456789'.repeat(60)",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); r == '0123456789'.repeat(60)",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); r < r + 'a'",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); Object.is(r, '0123456789'.repeat(60))",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); new Set([r]).has('0123456789'.repeat(60))",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); var o = {}; o[r] = 1; o['0123456789'.repeat(60)] === 1",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); new Map([[r, 1]]).get('0123456789'.repeat(60)) === 1",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); /9012$/.test(r)",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); r.startsWith('0123') && r.endsWith('6789')");
        assertStringWithJavet(
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); JSON.stringify(r).length + ''",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); r.toUpperCase().slice(0, 3)",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); typeof r",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); [...r].length + ''",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); r.replace('0', '_').slice(0, 2)",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); new String(r).length + ''",
                "var r = '0123456789'.repeat(30) + '0123456789'.repeat(30); String(r.length)");
    }
}
