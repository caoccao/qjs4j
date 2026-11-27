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
import com.caoccao.qjs4j.core.buffer.JSArrayBuffer;
import com.caoccao.qjs4j.core.buffer.JSDataView;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DataView prototype methods.
 */
public class DataViewPrototypeTest extends BaseJavetTest {

    private void assertByteAccess(JSNativeCallback get, JSNativeCallback set, int value, JSValue invalidReceiver) {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView view = new JSDataView(context, buffer);
        buffer.getBuffer().put(0, (byte) value);
        assertNumberValue(get.call(context, view, nativeArguments(0)), value);
        assertThat(set.call(context, view, numberArgs(1, value))).isEqualTo(JSUndefined.INSTANCE);
        assertThat(buffer.getBuffer().get(1)).isEqualTo((byte) value);
        assertInvalidAccess(get, set, view, invalidReceiver);
    }

    private void assertInvalidAccess(JSNativeCallback get, JSNativeCallback set, JSDataView view, JSValue receiver) {
        assertPendingTypeError(context, get.call(context, receiver, nativeArguments(0)));
        assertPendingTypeError(context, set.call(context, receiver, nativeArguments(0, 1)));
        assertPendingRangeError(context, get.call(context, view, nativeArguments(20)));
        assertPendingRangeError(context, set.call(context, view, nativeArguments(20, 1)));
    }

    private void assertMultibyteAccess(JSNativeCallback get, JSNativeCallback set, double bigEndian,
            double littleEndian, int secondOffset, JSValue invalidReceiver) {
        JSDataView view = new JSDataView(context, new JSArrayBuffer(context, 16));
        assertThat(set.call(context, view, numberArgs(0, bigEndian))).isEqualTo(JSUndefined.INSTANCE);
        assertNumberValue(get.call(context, view, nativeArguments(0)), bigEndian);
        JSNumber offset = JSNumber.of(secondOffset);
        assertThat(set.call(context, view, new JSValue[]{offset, JSNumber.of(littleEndian), JSBoolean.TRUE}))
                .isEqualTo(JSUndefined.INSTANCE);
        assertNumberValue(get.call(context, view, new JSValue[]{offset, JSBoolean.TRUE}), littleEndian);
        assertInvalidAccess(get, set, view, invalidReceiver);
    }

    @Test
    public void testFloat32Access() {
        assertMultibyteAccess(DataViewPrototype::getFloat32, DataViewPrototype::setFloat32, 3.14159f, 2.718f, 4,
                new JSString("not a dataview"));
    }

    @Test
    public void testFloat64Access() {
        assertMultibyteAccess(DataViewPrototype::getFloat64, DataViewPrototype::setFloat64, 2.71828, 3.14159, 0,
                JSNumber.of(123));
    }

    @Test
    public void testGetBuffer() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = callBuiltin(DataViewPrototype::getBuffer, dataView);
        assertThat(result).isEqualTo(buffer);

        // Edge case: called on non-DataView
        result = callBuiltin(DataViewPrototype::getBuffer, new JSString("not a dataview"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetByteLength() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = callBuiltin(DataViewPrototype::getByteLength, dataView);
        assertNumberValue(result, 16.0);

        // With offset and length
        JSDataView dataView2 = new JSDataView(context, buffer, 4, 8);
        result = callBuiltin(DataViewPrototype::getByteLength, dataView2);
        assertNumberValue(result, 8.0);

        // Edge case: called on non-DataView
        result = callBuiltin(DataViewPrototype::getByteLength, new JSNumber(123));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetByteOffset() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = callBuiltin(DataViewPrototype::getByteOffset, dataView);
        assertNumberValue(result, 0.0);

        // With offset
        JSDataView dataView2 = new JSDataView(context, buffer, 4, 8);
        result = callBuiltin(DataViewPrototype::getByteOffset, dataView2);
        assertNumberValue(result, 4.0);

        // Edge case: called on non-DataView
        result = callBuiltin(DataViewPrototype::getByteOffset, new JSObject(context));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testInt16Access() {
        assertMultibyteAccess(DataViewPrototype::getInt16, DataViewPrototype::setInt16, 12345, -12345, 2,
                new JSObject(context));
    }

    @Test
    public void testInt32Access() {
        assertMultibyteAccess(DataViewPrototype::getInt32, DataViewPrototype::setInt32, 123456789, -987654321, 4,
                new JSString("not a dataview"));
    }

    @Test
    public void testInt8Access() {
        assertByteAccess(DataViewPrototype::getInt8, DataViewPrototype::setInt8, -123, JSNumber.of(456));
    }

    @Test
    public void testSetGetBigInt64AndBigUint64() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        var setBigInt64 = builtin(DataViewPrototype::setBigInt64, dataView);
        JSValue result = setBigInt64.call(0, "1");
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        var getBigInt64 = builtin(DataViewPrototype::getBigInt64, dataView);
        result = getBigInt64.call(0);
        assertThat(result).isEqualTo(new JSBigInt(1));

        result = setBigInt64.call(0, JSBoolean.TRUE);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = getBigInt64.call(0);
        assertThat(result).isEqualTo(new JSBigInt(1));

        result = callBuiltin(DataViewPrototype::setBigUint64, dataView, 8, new JSBigInt(-1));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = callBuiltin(DataViewPrototype::getBigUint64, dataView, 8);
        assertThat(result).isEqualTo(new JSBigInt("18446744073709551615"));

        result = setBigInt64.call(0, 1);
        assertPendingTypeError(context, result);

        result = setBigInt64.call(0, JSUndefined.INSTANCE);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testSetGetFloat16Uint16Uint32() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = callBuiltin(DataViewPrototype::setFloat16, dataView, 0, 1.5, JSBoolean.TRUE);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = callBuiltin(DataViewPrototype::getFloat16, dataView, 0, JSBoolean.TRUE);
        assertThat(result).isInstanceOfSatisfying(JSNumber.class,
                jsNum -> assertThat(jsNum.value()).isCloseTo(1.5, Offset.offset(0.001)));

        result = callBuiltin(DataViewPrototype::setUint16, dataView, 2, 65535, JSBoolean.TRUE);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = callBuiltin(DataViewPrototype::getUint16, dataView, 2, JSBoolean.TRUE);
        assertNumberValue(result, 65535.0);

        result = callBuiltin(DataViewPrototype::setUint32, dataView, 4, 4294967295L, JSBoolean.TRUE);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = callBuiltin(DataViewPrototype::getUint32, dataView, 4, JSBoolean.TRUE);
        assertNumberValue(result, 4294967295.0);
    }

    @Test
    public void testToIndexAndOutOfBoundsSemantics() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = callBuiltin(DataViewPrototype::setInt8, dataView, 1.9, 7);
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        var getInt8 = builtin(DataViewPrototype::getInt8, dataView);
        result = getInt8.call(1);
        assertNumberValue(result, 7.0);

        result = getInt8.call(-1);
        assertPendingRangeError(context, result);

        result = getInt8.call(new JSSymbol("offset"));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testUint8Access() {
        assertByteAccess(DataViewPrototype::getUint8, DataViewPrototype::setUint8, 200, new JSObject(context));
    }

    @Test
    public void testViewStateWhenDetachedOrOutOfBounds() {
        JSArrayBuffer detachedBuffer = new JSArrayBuffer(context, 8);
        JSDataView detachedView = new JSDataView(context, detachedBuffer);
        detachedBuffer.detach();

        JSValue result = callBuiltin(DataViewPrototype::getBuffer, detachedView);
        assertThat(result).isEqualTo(detachedBuffer);

        result = callBuiltin(DataViewPrototype::getByteLength, detachedView);
        assertPendingTypeError(context, result);

        result = callBuiltin(DataViewPrototype::getByteOffset, detachedView);
        assertPendingTypeError(context, result);

        JSArrayBuffer resizableBuffer = new JSArrayBuffer(context, 16, 16);
        JSDataView outOfBoundsView = new JSDataView(context, resizableBuffer, 8, 8);
        resizableBuffer.resize(4);

        result = callBuiltin(DataViewPrototype::getBuffer, outOfBoundsView);
        assertThat(result).isEqualTo(resizableBuffer);

        result = callBuiltin(DataViewPrototype::getByteLength, outOfBoundsView);
        assertPendingTypeError(context, result);

        result = callBuiltin(DataViewPrototype::getByteOffset, outOfBoundsView);
        assertPendingTypeError(context, result);
    }
}
