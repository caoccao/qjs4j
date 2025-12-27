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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArrayBuffer constructor static methods.
 */
public class ArrayBufferConstructorTest extends BaseTest {

    @Test
    public void testGetSpecies() {
        // Get ArrayBuffer constructor from global
        JSObject arrayBufferConstructor = ctx.getGlobalObject().get("ArrayBuffer").asObject().orElse(null);
        assertNotNull(arrayBufferConstructor);

        // Call the getter with the constructor as thisArg
        JSValue result = ArrayBufferConstructor.getSpecies(ctx, arrayBufferConstructor, new JSValue[]{});

        // Should return the same ArrayBuffer constructor
        assertSame(arrayBufferConstructor, result);

        // Verify it also works via Symbol.species property
        PropertyKey speciesKey = PropertyKey.fromSymbol(JSSymbol.SPECIES);
        JSValue speciesGetter = arrayBufferConstructor.get(speciesKey);
        assertNotNull(speciesGetter);
        assertTrue(speciesGetter.isFunction());
    }

    @Test
    public void testIsView() {
        // Normal case: TypedArray instances should return true
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSUint8Array uint8Array = new JSUint8Array(buffer, 0, 8);
        JSValue result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{uint8Array});
        assertTrue(result.isBooleanTrue());

        JSInt32Array int32Array = new JSInt32Array(buffer, 0, 4);
        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{int32Array});
        assertTrue(result.isBooleanTrue());

        JSFloat32Array float32Array = new JSFloat32Array(buffer, 0, 4);
        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{float32Array});
        assertTrue(result.isBooleanTrue());

        // Normal case: DataView should return true
        JSDataView dataView = new JSDataView(buffer);
        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{dataView});
        assertTrue(result.isBooleanTrue());

        // Normal case: non-view objects should return false
        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSArray()});
        assertTrue(result.isBooleanFalse());

        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSObject()});
        assertTrue(result.isBooleanFalse());

        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        assertTrue(result.isBooleanFalse());

        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        assertTrue(result.isBooleanFalse());

        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.TRUE});
        assertTrue(result.isBooleanFalse());

        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTrue(result.isBooleanFalse());

        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = ArrayBufferConstructor.isView(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());
    }
}