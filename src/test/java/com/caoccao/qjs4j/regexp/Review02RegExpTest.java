package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Review02RegExpTest extends BaseJavetTest {
    @Test
    void largeBoundsUseCompactBytecode() {
        RegExpBytecode bytecode = new RegExpCompiler(new com.caoccao.qjs4j.unicode.UnicodePropertyResolver())
                .compile("a{1000000}", "");
        assertThat(bytecode.instructions().length).isLessThan(100);
    }

    @Test
    void largeExactAndBoundedQuantifiersPreserveBounds() {
        assertStringWithJavet(
                "JSON.stringify([0,4095,4096,4097,4098,4099,8194].map(n=>/^a{4097}$/.test('a'.repeat(n))))",
                "JSON.stringify([0,4095,4096,4097,4098,4099,8194].map(n=>/^a{4096,4098}$/.test('a'.repeat(n))))",
                "JSON.stringify([0,4095,4096,4097,4098,4099,8194].map(n=>/^a{4096,4098}?$/.test('a'.repeat(n))))",
                "JSON.stringify([0,4095,4096,4097,4098,4099,8194].map(n=>/^a{4097,}$/.test('a'.repeat(n))))",
                "JSON.stringify([0,4095,4096,4097,4098,4099,8194].map(n=>/^(?:aa){4097}$/.test('a'.repeat(n))))");
        assertStringWithJavet("JSON.stringify(/^(a|b){4097}(a{1,2})$/.exec('a'.repeat(4099)).slice(1))",
                "JSON.stringify(/^(?:a{2}){4097}?$/.test('a'.repeat(8194)))", "JSON.stringify(/^(?:){4097}$/.test(''))",
                "JSON.stringify(/^(?=a{4097})a{4097}$/.test('a'.repeat(4097)))");
    }
}
