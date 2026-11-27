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

package com.caoccao.qjs4j.core.module;

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleSourceTextTest extends BaseTest {
    @Test
    public void testDecodeIdentifierEscapesAppliesUnicodeEscapes() {
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u0078")).isEqualTo("x");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("caf\\u00e9")).isEqualTo("café");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{1D4AA}")).isEqualTo("\uD835\uDCAA");
        // Nothing to decode: the input is handed back as it stands.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("plain$_0")).isEqualTo("plain$_0");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("café")).isEqualTo("café");
    }

    @Test
    public void testDecodeIdentifierEscapesLeavesAnUnterminatedBracedEscapeAlone() {
        // An unterminated braced escape is malformed, so it gets the same answer as every other
        // malformed escape: its text stands. The branch used to append the escape and then rewind
        // the cursor to the backslash, so the loop walked the same characters twice and a
        // three-character unterminated escape decoded to five.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{")).isEqualTo("\\u{");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("a\\u{")).isEqualTo("a\\u{");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{12")).isEqualTo("\\u{12");
        // Only the malformed escape is left alone. Whatever follows it is still decoded, exactly as
        // it is after an escape with non-hexadecimal digits, so one bad escape does not silently
        // stop the scan.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{ \\u0041")).isEqualTo("\\u{ A");
    }

    @Test
    public void testDecodeIdentifierEscapesLeavesMalformedEscapesAlone() {
        // Not a decodable escape, so the text stands: the compiler rejects it with a real message.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\uZZZZ")).isEqualTo("\\uZZZZ");
        assertThat(ModuleSourceText.decodeIdentifierEscapes("a\\u12")).isEqualTo("a\\u12");
    }

    @Test
    public void testDecodeIdentifierEscapesReadsABracedEscapeThatIsTerminated() {
        // The sibling of the case above: the brace does close, so the escape decodes. Both branches
        // read the same opening prefix, and only the closing brace tells them apart.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{41}")).isEqualTo("A");
        // A braced escape whose body is not hexadecimal is malformed too, and stands as written.
        assertThat(ModuleSourceText.decodeIdentifierEscapes("\\u{ZZ}b")).isEqualTo("\\u{ZZ}b");
    }

    @Test
    public void testDecodeModuleStringLiteralValueAppliesEscapes() {
        // A specifier's meaning is its StringValue, not its spelling: './dep.mjs' either way.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./d\\u0065p.mjs")).isEqualTo("./dep.mjs");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\u002db")).isEqualTo("a-b");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("\\x41")).isEqualTo("A");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\nb")).isEqualTo("a\nb");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\tb")).isEqualTo("a\tb");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./a\\'b.mjs")).isEqualTo("./a'b.mjs");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("\\u{1F600}")).isEqualTo("\uD83D\uDE00");
    }

    @Test
    public void testDecodeModuleStringLiteralValueDropsLineContinuations() {
        // A LineContinuation contributes nothing to the value, and CRLF is one line terminator.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./de\\\np.mjs")).isEqualTo("./dep.mjs");
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("./de\\\r\np.mjs")).isEqualTo("./dep.mjs");
    }

    @Test
    public void testDecodeModuleStringLiteralValueLeavesLegacyOctalAlone() {
        // A legacy octal escape is a SyntaxError in a module, and the source has already been
        // compiled once by the time this runs. Inventing a value for it would be worse.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("\\01")).isEqualTo("\\01");
        // \0 not followed by a digit is NUL, which is a legal escape.
        assertThat(ModuleSourceText.decodeModuleStringLiteralValue("a\\0b")).isEqualTo("a\0b");
    }

    @Test
    public void testEscapingAJavaScriptStringCoversEveryLineTerminator() {
        // A line terminator inside an interpolated value would end the generated literal and turn
        // the rest of the value into source text. U+2028 and U+2029 are line terminators too.
        assertThat(ModuleSourceText.escapeJavaScriptString("a\"b")).isEqualTo("a\\\"b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\\b")).isEqualTo("a\\\\b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\nb")).isEqualTo("a\\nb");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\u2028b")).isEqualTo("a\\u2028b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\u2029b")).isEqualTo("a\\u2029b");
        assertThat(ModuleSourceText.escapeJavaScriptString("a\u0001b")).isEqualTo("a\\u0001b");
    }

    @Test
    public void testIdentifierNameValidationFollowsUnicodeNotAscii() {
        // `café` and `módulo` are ordinary identifiers; the transformer used to extract binding
        // names with [A-Za-z_$][A-Za-z0-9_$]* and lose them.
        assertThat(ModuleSourceText.isValidIdentifierName("café")).isTrue();
        assertThat(ModuleSourceText.isValidIdentifierName("módulo")).isTrue();
        assertThat(ModuleSourceText.isValidIdentifierName("$_0")).isTrue();
        assertThat(ModuleSourceText.isValidIdentifierName("0abc")).isFalse();
        assertThat(ModuleSourceText.isValidIdentifierName("a-b")).isFalse();
        assertThat(ModuleSourceText.isValidIdentifierName("")).isFalse();
        assertThat(ModuleSourceText.isValidIdentifierName(null)).isFalse();
    }

    @Test
    public void testQuotedClosingBraceInModuleBody() {
        String body = "{ return 'https://example.org/}'; }";
        assertThat(ModuleSourceText.findMatchingClosingBrace(body, 0)).isEqualTo(body.length() - 1);
    }

    @Test
    public void testSplitOnTopLevelCommasIgnoresCommasInsideQuotes() {
        assertThat(ModuleSourceText.splitOnTopLevelCommas("a, b as c")).containsExactly("a", " b as c");
        assertThat(ModuleSourceText.splitOnTopLevelCommas("\"a,b\" as c, d")).containsExactly("\"a,b\" as c", " d");
    }
}
