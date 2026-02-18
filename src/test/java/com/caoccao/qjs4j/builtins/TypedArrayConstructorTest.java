package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class TypedArrayConstructorTest extends BaseJavetTest {

    @Test
    public void testBigIntObjectArgStringToBigInt() {
        assertStringWithJavet(
                "new BigInt64Array(['', '1'])[0].toString()",
                "new BigInt64Array(['', '1'])[1].toString()",
                "new BigUint64Array(['', '1'])[0].toString()",
                "new BigUint64Array(['', '1'])[1].toString()");
        assertErrorWithJavet(
                "new BigInt64Array(['1n'])",
                "new BigInt64Array(['Infinity'])",
                "new BigInt64Array(['1.1'])",
                "new BigInt64Array(['1e7'])");
    }

    @Test
    public void testConstructor() {
        assertErrorWithJavet(
                "BigInt64Array(1)",
                "BigUint64Array(1)",
                "Float16Array(1)",
                "Float32Array(1)",
                "Float64Array(1)",
                "Int16Array(1)",
                "Int32Array(1)",
                "Int8Array(1)",
                "Uint16Array(1)",
                "Uint32Array(1)",
                "Uint8Array(1)",
                "Uint8ClampedArray(1)");
        assertStringWithJavet(
                "var aUint8Array = new Uint8Array(); aUint8Array.toString()",
                "var aUint8Array = new Uint8Array(3); aUint8Array.toString()",
                "var aUint8Array = new Uint8Array({0:1,1:2,2:3,length:3}); aUint8Array.toString()",
                "var aUint8Array = new Uint8Array([1,2,3]); aUint8Array.toString()");
    }

    @Test
    public void testDescriptorsAndAliases() {
        assertStringWithJavet(
                "(() => {" +
                        "const c = Object.getOwnPropertyDescriptor(Uint8Array, 'BYTES_PER_ELEMENT');" +
                        "const p = Object.getOwnPropertyDescriptor(Uint8Array.prototype, 'BYTES_PER_ELEMENT');" +
                        "return [c.value, c.writable, c.enumerable, c.configurable, p.value, p.writable, p.enumerable, p.configurable].join(',');" +
                        "})()",
                "(() => Uint8Array.prototype.toString === Array.prototype.toString)().toString()",
                "(() => Uint8Array.prototype[Symbol.iterator] === Uint8Array.prototype.values)().toString()");
    }

    @Test
    public void testFromOfAndSpecies() {
        assertStringWithJavet(
                "Uint8Array.of(1, 2, 3).toString()",
                "Uint8Array.from([1, 2, 3]).toString()",
                "Uint8Array.from([1, 2, 3], x => x + 1).toString()",
                "Uint8Array.from({0: 7, 1: 8, length: 2}).toString()");
        assertBooleanWithJavet(
                "(() => { class X extends Uint8Array {}; return X[Symbol.species] === X; })()",
                "(() => { try { Uint8Array.from.call({}, [1]); return false; } catch (e) { return e instanceof TypeError; } })()");
    }

    @Test
    public void testPrototypeMethodsAndEdgeCases() {
        assertStringWithJavet(
                "(() => { const b = new ArrayBuffer(4); const a = new Uint8Array(b); a.set([1, 2, 3, 4]); return new Uint8Array(b, 1, 2).toString(); })()",
                "(() => { const a = new Uint8Array([1, 2, 3, 4]); a.set([9, 8], 1); return a.toString(); })()",
                "(() => { const a = new Uint8Array([1, 2, 3, 4]); return a.subarray(1, -1).toString(); })()",
                "(() => { const b = new ArrayBuffer(8); const a = new Uint16Array(b, 2, 3); return [a.length, a.byteLength, a.byteOffset, a.buffer === b].join(','); })()");
        assertBooleanWithJavet(
                "(() => { const a = new Uint8Array(2); try { a.set([1], 3); return false; } catch (e) { return e instanceof RangeError; } })()",
                "(() => { const a = new Uint8Array(2); try { a.set([1, 2, 3], 0); return false; } catch (e) { return e instanceof RangeError; } })()");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                "typeof BigInt64Array",
                "typeof BigUint64Array",
                "typeof Float16Array",
                "typeof Float32Array",
                "typeof Float64Array",
                "typeof Int16Array",
                "typeof Int32Array",
                "typeof Int8Array",
                "typeof TypedArray",
                "typeof Uint16Array",
                "typeof Uint32Array",
                "typeof Uint8Array",
                "typeof Uint8ClampedArray");
    }
}
