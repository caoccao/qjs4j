package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class Uint8ArrayBase64HexTest extends BaseJavetTest {
    @Test
    public void testDescriptorsAndMethods() {
        assertBooleanWithJavet(
                "typeof Uint8Array.fromBase64 === 'function'",
                "typeof Uint8Array.fromHex === 'function'",
                "typeof Uint8Array.prototype.toBase64 === 'function'",
                "typeof Uint8Array.prototype.toHex === 'function'",
                "typeof Uint8Array.prototype.setFromBase64 === 'function'",
                "typeof Uint8Array.prototype.setFromHex === 'function'");
        assertStringWithJavet(
                "(() => { const d = Object.getOwnPropertyDescriptor(Uint8Array, 'fromBase64'); return [d.writable, d.enumerable, d.configurable].join(','); })()",
                "(() => { const d = Object.getOwnPropertyDescriptor(Uint8Array.prototype, 'toBase64'); return [d.writable, d.enumerable, d.configurable].join(','); })()");
    }

    @Test
    public void testErrors() {
        assertErrorWithJavet(
                "Uint8Array.fromBase64(1)",
                "Uint8Array.fromBase64('AQ==', { alphabet: 'invalid' })",
                "Uint8Array.fromBase64('A', { lastChunkHandling: 'strict' })",
                "Uint8Array.fromHex('0')",
                "Uint8Array.fromHex('zz')",
                "new Uint8Array([1]).toBase64({ alphabet: 'invalid' })",
                "new Uint8Array([1]).setFromBase64('A', { lastChunkHandling: 'strict' })",
                "new Uint8Array([1]).setFromHex('0')");
    }

    @Test
    public void testFromAndTo() {
        assertStringWithJavet(
                "Uint8Array.fromBase64('AQID').toString()",
                "Uint8Array.fromHex('000102ff').toString()",
                "new Uint8Array([1, 2, 3]).toBase64()",
                "new Uint8Array([1, 2]).toBase64({ omitPadding: true })",
                "new Uint8Array([251, 255]).toBase64({ alphabet: 'base64url', omitPadding: true })",
                "new Uint8Array([1, 2, 255]).toHex()");
    }

    @Test
    public void testSetFrom() {
        assertStringWithJavet(
                """
                        (() => {
                          const a = new Uint8Array(8);
                          const r = a.setFromBase64('AQIDBA==');
                          return [r.read, r.written, a[0], a[1], a[2], a[3]].join(',');
                        })()""",
                """
                        (() => {
                          const a = new Uint8Array(8);
                          const r = a.setFromHex('00112233');
                          return [r.read, r.written, a[0], a[1], a[2], a[3]].join(',');
                        })()""");
    }
}
