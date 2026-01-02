package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class TypedArrayPrototypeTest extends BaseJavetTest {

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
                "typeof Uint16Array",
                "typeof Uint32Array",
                "typeof Uint8Array",
                "typeof Uint8ClampedArray");
    }
}
