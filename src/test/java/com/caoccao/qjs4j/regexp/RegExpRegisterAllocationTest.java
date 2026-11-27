package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegExpRegisterAllocationTest extends BaseJavetTest {
    @Test
    void reusedRegistersSurviveBacktracking() {
        assertStringWithJavet("JSON.stringify([1,2,3,4,5,6,7].map(n=>/^a{1,3}a{1,3}$/.test('a'.repeat(n))))",
                "JSON.stringify(/^(a{1,2})(a{1,2})(a{1,2})$/.exec('aaaa'))",
                "JSON.stringify(/^(?:a{1,2}b{1,2}){1,2}c$/.exec('aabbabc'))",
                "JSON.stringify(/(?:a?)*(?:a?)*b/.exec('aaab'))",
                "JSON.stringify([4,6,8,9].map(n=>/^(?:(?:a{1,2}){1,2}){1,2}$/.test('a'.repeat(n))))",
                "JSON.stringify(['ab','aab','aabb','abab','aabab','aababab'].map(s=>/^(?:a{1,2}b?){1,2}$/.test(s)))",
                "JSON.stringify(/(a{1,2})b{1,2}(a{1,2})/.exec('aaabbaaa'))", "JSON.stringify('aaaaa'.match(/a{1,2}/g))",
                "JSON.stringify(/^(?:a{1,2}){1,2}?a{1,2}$/.exec('aaaaa'))",
                "JSON.stringify(/^(?:a{2,3}|a{1,2}b)(?:a{1,2}|b{1,2})$/.exec('aaab'))",
                "JSON.stringify(/^(?=a{1,2}b)(?:a{1,2}|b{1,2}){2}$/.exec('aab'))",
                "JSON.stringify(/(?<=a{1,2})b{1,2}(?:c?)*$/.exec('aabbcc'))");
    }

    @Test
    void siblingQuantifiersShareRegisters() {
        RegExpCompiler compiler = new RegExpCompiler(new UnicodePropertyResolver());
        assertThat(compiler.compile("a{1,2}".repeat(300), "").registerCount()).isEqualTo(1);
        assertThat(compiler.compile("(?:a?)*".repeat(300) + "a{5000,6000}".repeat(300), "").registerCount())
                .isEqualTo(1);
        assertThat(compiler.compile("(?:(?:a{1,2}){1,2}){1,2}", "").registerCount()).isEqualTo(3);
        // An alternation that may match nothing needs an advance check beside its counter.
        assertThat(compiler.compile("(?:a{1,2}b{1,2}|(?:c?)*){1,2}", "").registerCount()).isEqualTo(3);
        assertThat(compiler.compile("(?:a{2}){1,2}(?:ab){1,2}(?:a|b){1,2}", "").registerCount()).isEqualTo(2);
        assertStringWithJavet("""
                JSON.stringify([
                  'a{1,2}'.repeat(257), '(?:a?)*'.repeat(300), 'a{5000,6000}'.repeat(300),
                  '(?:' + 'a{1,2}'.repeat(150) + '|' + 'b{1,2}'.repeat(150) + ')'
                ].map(pattern => eval(`[/${pattern}/.test('a'.repeat(600)), /${pattern}/.test('b'.repeat(600))]`)))
                """);
    }
}
