package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for global object functions.
 */
public class GlobalObjectTest extends BaseJavetTest {

    @Test
    public void testEscape() {
        // Test basic ASCII characters that should not be escaped
        assertStringWithJavet(
                "escape('abc')",
                "escape('ABC')",
                "escape('123')",
                "escape('@*_+-./')"
        );

        // Test characters that should be escaped with %XX format
        assertStringWithJavet(
                "escape('Hello World')",
                "escape('a b c')",
                "escape('!#$%&()=')",
                "escape('a=b&c=d')",
                "escape('test:value')",
                "escape('<script>')"
        );

        // Test Unicode characters (should use %uXXXX format)
        assertStringWithJavet(
                "escape('你好')",
                "escape('世界')",
                "escape('Hello 世界')",
                "escape('abc中def')"
        );

        // Test empty string
        assertStringWithJavet("escape('')");

        // Test special cases
        assertStringWithJavet(
                "escape('100% correct')",
                "escape('email@domain.com')",
                "escape('path/to/file.txt')"
        );
    }

    @Test
    public void testEscapeEdgeCases() {
        // Test with numbers and other types (should convert to string first)
        assertStringWithJavet(
                "escape('42')",
                "escape('3.14')"
        );

        // Test consecutive spaces and special characters
        assertStringWithJavet(
                "escape('  ')",
                "escape('!!!')",
                "escape('###')"
        );
    }

    @Test
    public void testEscapeUnescapeRoundTrip() {
        // Test that escape and unescape are inverses
        assertStringWithJavet(
                "unescape(escape('Hello World'))",
                "unescape(escape('test@example.com'))",
                "unescape(escape('a=b&c=d'))",
                "unescape(escape('你好世界'))",
                "unescape(escape('Hello 世界!'))",
                "unescape(escape('!@#$%^&*()'))",
                "unescape(escape(''))"
        );
    }

    @Test
    public void testUnescape() {
        // Test basic %XX sequences
        assertStringWithJavet(
                "unescape('%20')",
                "unescape('%21')",
                "unescape('%40')"
        );

        // Test %uXXXX sequences for Unicode
        assertStringWithJavet(
                "unescape('%u4F60')",
                "unescape('%u597D')",
                "unescape('%u4F60%u597D')"
        );

        // Test mixed sequences
        assertStringWithJavet(
                "unescape('Hello%20World')",
                "unescape('%21%23%24')",
                "unescape('test%3Avalue')"
        );

        // Test strings without escape sequences
        assertStringWithJavet(
                "unescape('abc')",
                "unescape('123')",
                "unescape('')"
        );

        // Test invalid sequences (should be treated as literals)
        assertStringWithJavet(
                "unescape('%')",
                "unescape('%Z')",
                "unescape('%uGGGG')"
        );
    }
}
