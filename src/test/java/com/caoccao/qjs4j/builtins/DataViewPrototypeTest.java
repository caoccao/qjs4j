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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for DataView prototype methods.
 */
public class DataViewPrototypeTest extends BaseTest {

    @Test
    public void testGetBuffer() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.getBuffer(ctx, dataView, new JSValue[]{});
        assertEquals(buffer, result);

        // Edge case: called on non-DataView
        result = DataViewPrototype.getBuffer(ctx, new JSString("not a dataview"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetByteLength() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.getByteLength(ctx, dataView, new JSValue[]{});
        assertEquals(16.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // With offset and length
        JSDataView dataView2 = new JSDataView(buffer, 4, 8);
        result = DataViewPrototype.getByteLength(ctx, dataView2, new JSValue[]{});
        assertEquals(8.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.getByteLength(ctx, new JSNumber(123), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetByteOffset() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.getByteOffset(ctx, dataView, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // With offset
        JSDataView dataView2 = new JSDataView(buffer, 4, 8);
        result = DataViewPrototype.getByteOffset(ctx, dataView2, new JSValue[]{});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.getByteOffset(ctx, new JSObject(), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetFloat32() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        // Set some test data using DataView
        DataViewPrototype.setFloat32(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(3.14159f)});

        JSValue result = DataViewPrototype.getFloat32(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(3.14159f, result.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // With little-endian flag
        DataViewPrototype.setFloat32(ctx, dataView, new JSValue[]{new JSNumber(4), new JSNumber(2.718f), JSBoolean.TRUE});
        result = DataViewPrototype.getFloat32(ctx, dataView, new JSValue[]{new JSNumber(4), JSBoolean.TRUE});
        assertEquals(2.718f, result.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // Edge case: called on non-DataView
        result = DataViewPrototype.getFloat32(ctx, new JSString("not a dataview"), new JSValue[]{new JSNumber(0)});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: out of bounds
        result = DataViewPrototype.getFloat32(ctx, dataView, new JSValue[]{new JSNumber(20)});
        assertRangeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetFloat64() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        // Set some test data using DataView
        DataViewPrototype.setFloat64(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(2.71828)});

        JSValue result = DataViewPrototype.getFloat64(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(2.71828, result.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // With little-endian flag
        DataViewPrototype.setFloat64(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(3.14159), JSBoolean.TRUE});
        result = DataViewPrototype.getFloat64(ctx, dataView, new JSValue[]{new JSNumber(0), JSBoolean.TRUE});
        assertEquals(3.14159, result.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // Edge case: called on non-DataView
        result = DataViewPrototype.getFloat64(ctx, new JSNumber(123), new JSValue[]{new JSNumber(0)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetInt16() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        // Set some test data using DataView
        DataViewPrototype.setInt16(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(12345)});

        JSValue result = DataViewPrototype.getInt16(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(12345.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // With little-endian flag
        DataViewPrototype.setInt16(ctx, dataView, new JSValue[]{new JSNumber(2), new JSNumber(-12345), JSBoolean.TRUE});
        result = DataViewPrototype.getInt16(ctx, dataView, new JSValue[]{new JSNumber(2), JSBoolean.TRUE});
        assertEquals(-12345.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.getInt16(ctx, new JSObject(), new JSValue[]{new JSNumber(0)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetInt32() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        // Set some test data using DataView
        DataViewPrototype.setInt32(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(123456789)});

        JSValue result = DataViewPrototype.getInt32(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(123456789.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // With little-endian flag
        DataViewPrototype.setInt32(ctx, dataView, new JSValue[]{new JSNumber(4), new JSNumber(-987654321), JSBoolean.TRUE});
        result = DataViewPrototype.getInt32(ctx, dataView, new JSValue[]{new JSNumber(4), JSBoolean.TRUE});
        assertEquals(-987654321.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.getInt32(ctx, new JSString("not a dataview"), new JSValue[]{new JSNumber(0)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetInt8() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        // Set some test data
        buffer.getBuffer().put(0, (byte) -123);

        JSValue result = DataViewPrototype.getInt8(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(-123.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.getInt8(ctx, new JSNumber(456), new JSValue[]{new JSNumber(0)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetUint8() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        // Set some test data
        buffer.getBuffer().put(0, (byte) 200);

        JSValue result = DataViewPrototype.getUint8(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(200.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.getUint8(ctx, new JSObject(), new JSValue[]{new JSNumber(0)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetFloat32() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.setFloat32(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(3.14159f)});
        assertTrue(result.isUndefined());

        // Check the value was set by reading it back
        JSValue readResult = DataViewPrototype.getFloat32(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(3.14159f, readResult.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // With little-endian flag
        result = DataViewPrototype.setFloat32(ctx, dataView, new JSValue[]{new JSNumber(4), new JSNumber(2.718f), JSBoolean.TRUE});
        assertTrue(result.isUndefined());
        readResult = DataViewPrototype.getFloat32(ctx, dataView, new JSValue[]{new JSNumber(4), JSBoolean.TRUE});
        assertEquals(2.718f, readResult.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // Edge case: called on non-DataView
        result = DataViewPrototype.setFloat32(ctx, new JSString("not a dataview"), new JSValue[]{new JSNumber(0), new JSNumber(1.0f)});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: out of bounds
        result = DataViewPrototype.setFloat32(ctx, dataView, new JSValue[]{new JSNumber(20), new JSNumber(1.0f)});
        assertRangeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetFloat64() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.setFloat64(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(2.71828)});
        assertTrue(result.isUndefined());

        // Check the value was set by reading it back
        JSValue readResult = DataViewPrototype.getFloat64(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(2.71828, readResult.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // With little-endian flag
        result = DataViewPrototype.setFloat64(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(3.14159), JSBoolean.TRUE});
        assertTrue(result.isUndefined());
        readResult = DataViewPrototype.getFloat64(ctx, dataView, new JSValue[]{new JSNumber(0), JSBoolean.TRUE});
        assertEquals(3.14159, readResult.asNumber().map(JSNumber::value).orElse(0D), 0.00001);

        // Edge case: called on non-DataView
        result = DataViewPrototype.setFloat64(ctx, new JSNumber(123), new JSValue[]{new JSNumber(0), new JSNumber(1.0)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetInt16() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.setInt16(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(12345)});
        assertTrue(result.isUndefined());

        // Check the value was set by reading it back
        JSValue readResult = DataViewPrototype.getInt16(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(12345.0, readResult.asNumber().map(JSNumber::value).orElse(0D));

        // With little-endian flag
        result = DataViewPrototype.setInt16(ctx, dataView, new JSValue[]{new JSNumber(2), new JSNumber(-12345), JSBoolean.TRUE});
        assertTrue(result.isUndefined());
        readResult = DataViewPrototype.getInt16(ctx, dataView, new JSValue[]{new JSNumber(2), JSBoolean.TRUE});
        assertEquals(-12345.0, readResult.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.setInt16(ctx, new JSObject(), new JSValue[]{new JSNumber(0), new JSNumber(123)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetInt32() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.setInt32(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(123456789)});
        assertTrue(result.isUndefined());

        // Check the value was set by reading it back
        JSValue readResult = DataViewPrototype.getInt32(ctx, dataView, new JSValue[]{new JSNumber(0)});
        assertEquals(123456789.0, readResult.asNumber().map(JSNumber::value).orElse(0D));

        // With little-endian flag
        result = DataViewPrototype.setInt32(ctx, dataView, new JSValue[]{new JSNumber(4), new JSNumber(-987654321), JSBoolean.TRUE});
        assertTrue(result.isUndefined());
        readResult = DataViewPrototype.getInt32(ctx, dataView, new JSValue[]{new JSNumber(4), JSBoolean.TRUE});
        assertEquals(-987654321.0, readResult.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: called on non-DataView
        result = DataViewPrototype.setInt32(ctx, new JSString("not a dataview"), new JSValue[]{new JSNumber(0), new JSNumber(123)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetInt8() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.setInt8(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(-123)});
        assertTrue(result.isUndefined());

        // Check the value was set
        byte value = buffer.getBuffer().get(0);
        assertEquals((byte) -123, value);

        // Edge case: called on non-DataView
        result = DataViewPrototype.setInt8(ctx, new JSNumber(456), new JSValue[]{new JSNumber(0), new JSNumber(123)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetUint8() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = new JSDataView(buffer);

        JSValue result = DataViewPrototype.setUint8(ctx, dataView, new JSValue[]{new JSNumber(0), new JSNumber(200)});
        assertTrue(result.isUndefined());

        // Check the value was set
        byte value = buffer.getBuffer().get(0);
        assertEquals((byte) 200, value);

        // Edge case: called on non-DataView
        result = DataViewPrototype.setUint8(ctx, new JSObject(), new JSValue[]{new JSNumber(0), new JSNumber(123)});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}