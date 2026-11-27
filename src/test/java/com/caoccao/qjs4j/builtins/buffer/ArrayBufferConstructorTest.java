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

package com.caoccao.qjs4j.builtins.buffer;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ArrayBuffer constructor static methods.
 */
public class ArrayBufferConstructorTest extends BaseJavetTest {

    @Test
    public void testGetSpecies() {
        // Get ArrayBuffer constructor from global
        JSObject arrayBufferConstructor = context.getGlobalObject().get("ArrayBuffer").asObject().orElseThrow();

        // Call the getter with the constructor as thisArg
        JSValue result = ArrayBufferConstructor.getSpecies(context, arrayBufferConstructor, JSValue.NO_ARGS);

        // Should return the same ArrayBuffer constructor
        assertThat(result).isSameAs(arrayBufferConstructor);

        // Verify it also works via Symbol.species property
        PropertyKey speciesKey = PropertyKey.SYMBOL_SPECIES;
        JSValue speciesGetter = arrayBufferConstructor.get(speciesKey);
        assertThat(speciesGetter).isNotNull();
        assertThat(speciesGetter.isFunction()).isTrue();
    }

    @Test
    public void testIsView() {
        // Normal case: TypedArray instances should return true
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSUint8Array uint8Array = new JSUint8Array(context, buffer, 0, 8);
        BuiltinAssertions isView = builtin(ArrayBufferConstructor::isView, JSUndefined.INSTANCE);
        isView.expectValue(JSBoolean.TRUE, uint8Array);

        JSInt32Array int32Array = new JSInt32Array(context, buffer, 0, 4);
        isView.expectValue(JSBoolean.TRUE, int32Array);

        JSFloat16Array float16Array = new JSFloat16Array(context, buffer, 0, 8);
        isView.expectValue(JSBoolean.TRUE, float16Array);

        JSFloat32Array float32Array = new JSFloat32Array(context, buffer, 0, 4);
        isView.expectValue(JSBoolean.TRUE, float32Array);

        // Normal case: DataView should return true
        JSDataView dataView = new JSDataView(context, buffer);
        isView.expectValue(JSBoolean.TRUE, dataView);

        // Normal case: non-view objects should return false
        isView.expectValue(JSBoolean.FALSE, new JSArray(context));

        isView.expectValue(JSBoolean.FALSE, new JSObject(context));

        isView.expectValue(JSBoolean.FALSE, stringArgs("test"));

        isView.expectValue(JSBoolean.FALSE, numberArgs(123));

        isView.expectValue(JSBoolean.FALSE, JSBoolean.TRUE);

        isView.expectValue(JSBoolean.FALSE, JSNull.INSTANCE);

        isView.expectValue(JSBoolean.FALSE, JSUndefined.INSTANCE);

        // Edge case: no arguments
        isView.expectValue(JSBoolean.FALSE);
    }

    @Test
    public void testResize() {
        // Normal case: resize to larger
        assertIntegerWithJavet("var buf = new ArrayBuffer(16, {maxByteLength: 64}); buf.resize(32); buf.byteLength;");
        // Normal case: resize to smaller
        assertIntegerWithJavet("var buf = new ArrayBuffer(16, {maxByteLength: 64}); buf.resize(8); buf.byteLength;");
        // No arguments: ToIndex(undefined) = 0, resizes to 0
        assertIntegerWithJavet("var buf = new ArrayBuffer(16, {maxByteLength: 64}); buf.resize(); buf.byteLength;");
        // Resize non-resizable buffer: TypeError
        assertErrorWithJavet("new ArrayBuffer(16).resize(32);");
        // Resize beyond maxByteLength: RangeError
        assertErrorWithJavet("new ArrayBuffer(16, {maxByteLength: 64}).resize(128);");
        // Resize detached buffer: TypeError
        assertErrorWithJavet("var buf = new ArrayBuffer(16, {maxByteLength: 64}); buf.transfer(); buf.resize(8);");
        // Coercion order: valueOf detaches buffer, then detach check triggers TypeError
        assertBooleanWithJavet(
                "var buf = new ArrayBuffer(16, {maxByteLength: 64});var evil = { valueOf() { buf.transfer(); return 8; } };try { buf.resize(evil); false; } catch(e) { e instanceof TypeError; }");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet("typeof ArrayBuffer;");
        assertIntegerWithJavet("ArrayBuffer.length;");
    }
}
