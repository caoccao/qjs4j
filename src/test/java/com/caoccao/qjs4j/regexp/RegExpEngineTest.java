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

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RegExpEngineTest extends BaseTest {
    @Test
    public void testExecSimpleMatch() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("xxabcxx", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("abc", result.getMatch());
        assertEquals(2, result.startIndex());
        assertEquals(5, result.endIndex());
    }

    @Test
    public void testExecNoMatch() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("defgh", 0);
        assertNull(result);
    }

    @Test
    public void testExecWithGroups() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a(bc)d", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("xabcd", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("abcd", result.getMatch());
        assertEquals("bc", result.getCapture(1));
    }

    @Test
    public void testEdgeCases() {
        RegExpCompiler compiler = new RegExpCompiler();
        // Empty pattern matches at every position
        RegExpBytecode bytecode = compiler.compile("", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("", result.getMatch());

        // Start index out of bounds
        result = engine.exec("abc", -1);
        assertNull(result);
        result = engine.exec("abc", 4);
        assertNull(result);
    }

    @Test
    public void testCaseInsensitiveMatching() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("abc", "i");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("ABC", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("ABC", result.getMatch());

        result = engine.exec("aBc", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("aBc", result.getMatch());
    }

    @Test
    public void testMultilineMode() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("^abc$", "m");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("abc\ndef", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("abc", result.getMatch());

        result = engine.exec("abc\ndef", 4);
        assertNull(result); // Should not match "def" with "^abc$"

        // Test ^ matching at line start
        bytecode = compiler.compile("^def", "m");
        engine = new RegExpEngine(bytecode);
        result = engine.exec("abc\ndef", 4);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("def", result.getMatch());
    }

    @Test
    public void testDotAllMode() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a.b", "s");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("a\nb", result.getMatch());
    }

    @Test
    public void testDotWithoutDotAll() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a.b", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertNull(result); // Should not match because . doesn't match \n
    }

    @Test
    public void testLineAnchors() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("^abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("abc", 0);
        assertNotNull(result);
        assertTrue(result.matched());

        result = engine.exec("xabc", 0);
        assertNull(result); // Should not match at position 0

        bytecode = compiler.compile("abc$", "");
        engine = new RegExpEngine(bytecode);
        result = engine.exec("abc", 0);
        assertNotNull(result);
        assertTrue(result.matched());

        result = engine.exec("abcx", 0);
        assertNull(result); // Should not match at end
    }

    @Test
    public void testMultipleCaptureGroups() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("(a)(b)(c)", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("abc", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("abc", result.getMatch());
        assertEquals("a", result.getCapture(1));
        assertEquals("b", result.getCapture(2));
        assertEquals("c", result.getCapture(3));
    }

    @Test
    public void testEscapedCharacters() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a\\nb", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("a\nb", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("a\nb", result.getMatch());

        bytecode = compiler.compile("a\\tb", "");
        engine = new RegExpEngine(bytecode);
        result = engine.exec("a\tb", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("a\tb", result.getMatch());
    }

    @Test
    public void testUnicodeCharacters() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("😀🌟🚀", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("😀🌟🚀", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("😀🌟🚀", result.getMatch());
        assertTrue(engine.test("😀🌟🚀"));
    }

    @Test
    public void testFlagsCombination() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("a.b", "is");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("A\nB", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("A\nB", result.getMatch());
    }

    @Test
    public void testStartIndexInMiddle() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("abc", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("xxxabc", 3);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("abc", result.getMatch());
        assertEquals(3, result.startIndex());
        assertEquals(6, result.endIndex());
    }

    @Test
    public void testNestedCaptureGroups() {
        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode bytecode = compiler.compile("((a)b)", "");
        RegExpEngine engine = new RegExpEngine(bytecode);
        RegExpEngine.MatchResult result = engine.exec("ab", 0);
        assertNotNull(result);
        assertTrue(result.matched());
        assertEquals("ab", result.getMatch());
        assertEquals("ab", result.getCapture(1));
        assertEquals("a", result.getCapture(2));
    }
}
