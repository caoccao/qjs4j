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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
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
        JSValue result = ArrayBufferConstructor.getSpecies(context, arrayBufferConstructor, new JSValue[]{});

        // Should return the same ArrayBuffer constructor
        assertThat(result).isSameAs(arrayBufferConstructor);

        // Verify it also works via Symbol.species property
        PropertyKey speciesKey = PropertyKey.SYMBOL_SPECIES;
        JSValue speciesGetter = arrayBufferConstructor.get(speciesKey);
        assertThat(speciesGetter).isNotNull();
        assertThat(speciesGetter.isFunction()).isFalse();
    }

    @Test
    public void testIsView() {
        // Normal case: TypedArray instances should return true
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSUint8Array uint8Array = new JSUint8Array(buffer, 0, 8);
        JSValue result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{uint8Array});
        assertThat(result.isBooleanTrue()).isTrue();

        JSInt32Array int32Array = new JSInt32Array(buffer, 0, 4);
        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{int32Array});
        assertThat(result.isBooleanTrue()).isTrue();

        JSFloat16Array float16Array = new JSFloat16Array(buffer, 0, 8);
        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{float16Array});
        assertThat(result.isBooleanTrue()).isTrue();

        JSFloat32Array float32Array = new JSFloat32Array(buffer, 0, 4);
        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{float32Array});
        assertThat(result.isBooleanTrue()).isTrue();

        // Normal case: DataView should return true
        JSDataView dataView = new JSDataView(buffer);
        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{dataView});
        assertThat(result.isBooleanTrue()).isTrue();

        // Normal case: non-view objects should return false
        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{new JSArray()});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{new JSObject()});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.TRUE});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: no arguments
        result = ArrayBufferConstructor.isView(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                "typeof ArrayBuffer;");
        assertIntegerWithJavet(
                "ArrayBuffer.length;");
    }
}