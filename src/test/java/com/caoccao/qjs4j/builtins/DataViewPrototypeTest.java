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
        assertNumberValue(get.call(context, view, numberArgs(0)), value);
        assertThat(set.call(context, view, numberArgs(1, value))).isEqualTo(JSUndefined.INSTANCE);
        assertThat(buffer.getBuffer().get(1)).isEqualTo((byte) value);
        assertInvalidAccess(get, set, view, invalidReceiver);
    }

    private void assertInvalidAccess(JSNativeCallback get, JSNativeCallback set, JSDataView view, JSValue receiver) {
        assertPendingTypeError(context, get.call(context, receiver, numberArgs(0)));
        assertPendingTypeError(context, set.call(context, receiver, numberArgs(0, 1)));
        assertPendingRangeError(context, get.call(context, view, numberArgs(20)));
        assertPendingRangeError(context, set.call(context, view, numberArgs(20, 1)));
    }

    private void assertMultibyteAccess(JSNativeCallback get, JSNativeCallback set, double bigEndian,
            double littleEndian, int secondOffset, JSValue invalidReceiver) {
        JSDataView view = new JSDataView(context, new JSArrayBuffer(context, 16));
        assertThat(set.call(context, view, numberArgs(0, bigEndian))).isEqualTo(JSUndefined.INSTANCE);
        assertNumberValue(get.call(context, view, numberArgs(0)), bigEndian);
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

        JSValue result = DataViewPrototype.getBuffer(context, dataView, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(buffer);

        // Edge case: called on non-DataView
        result = DataViewPrototype.getBuffer(context, new JSString("not a dataview"), JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetByteLength() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = DataViewPrototype.getByteLength(context, dataView, JSValue.NO_ARGS);
        assertNumberValue(result, 16.0);

        // With offset and length
        JSDataView dataView2 = new JSDataView(context, buffer, 4, 8);
        result = DataViewPrototype.getByteLength(context, dataView2, JSValue.NO_ARGS);
        assertNumberValue(result, 8.0);

        // Edge case: called on non-DataView
        result = DataViewPrototype.getByteLength(context, new JSNumber(123), JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }

    @Test
    public void testGetByteOffset() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = DataViewPrototype.getByteOffset(context, dataView, JSValue.NO_ARGS);
        assertNumberValue(result, 0.0);

        // With offset
        JSDataView dataView2 = new JSDataView(context, buffer, 4, 8);
        result = DataViewPrototype.getByteOffset(context, dataView2, JSValue.NO_ARGS);
        assertNumberValue(result, 4.0);

        // Edge case: called on non-DataView
        result = DataViewPrototype.getByteOffset(context, new JSObject(context), JSValue.NO_ARGS);
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

        JSValue result = DataViewPrototype.setBigInt64(context, dataView,
                new JSValue[]{new JSNumber(0), new JSString("1")});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = DataViewPrototype.getBigInt64(context, dataView, numberArgs(0));
        assertThat(result).isEqualTo(new JSBigInt(1));

        result = DataViewPrototype.setBigInt64(context, dataView, new JSValue[]{new JSNumber(0), JSBoolean.TRUE});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = DataViewPrototype.getBigInt64(context, dataView, numberArgs(0));
        assertThat(result).isEqualTo(new JSBigInt(1));

        result = DataViewPrototype.setBigUint64(context, dataView, new JSValue[]{new JSNumber(8), new JSBigInt(-1)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = DataViewPrototype.getBigUint64(context, dataView, numberArgs(8));
        assertThat(result).isEqualTo(new JSBigInt("18446744073709551615"));

        result = DataViewPrototype.setBigInt64(context, dataView, numberArgs(0, 1));
        assertPendingTypeError(context, result);

        result = DataViewPrototype.setBigInt64(context, dataView, new JSValue[]{new JSNumber(0), JSUndefined.INSTANCE});
        assertPendingTypeError(context, result);
    }

    @Test
    public void testSetGetFloat16Uint16Uint32() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = DataViewPrototype.setFloat16(context, dataView,
                new JSValue[]{new JSNumber(0), new JSNumber(1.5), JSBoolean.TRUE});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = DataViewPrototype.getFloat16(context, dataView, new JSValue[]{new JSNumber(0), JSBoolean.TRUE});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class,
                jsNum -> assertThat(jsNum.value()).isCloseTo(1.5, Offset.offset(0.001)));

        result = DataViewPrototype.setUint16(context, dataView,
                new JSValue[]{new JSNumber(2), new JSNumber(65535), JSBoolean.TRUE});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = DataViewPrototype.getUint16(context, dataView, new JSValue[]{new JSNumber(2), JSBoolean.TRUE});
        assertNumberValue(result, 65535.0);

        result = DataViewPrototype.setUint32(context, dataView,
                new JSValue[]{new JSNumber(4), new JSNumber(4294967295L), JSBoolean.TRUE});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = DataViewPrototype.getUint32(context, dataView, new JSValue[]{new JSNumber(4), JSBoolean.TRUE});
        assertNumberValue(result, 4294967295.0);
    }

    @Test
    public void testToIndexAndOutOfBoundsSemantics() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        JSValue result = DataViewPrototype.setInt8(context, dataView, numberArgs(1.9, 7));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        result = DataViewPrototype.getInt8(context, dataView, numberArgs(1));
        assertNumberValue(result, 7.0);

        result = DataViewPrototype.getInt8(context, dataView, numberArgs(-1));
        assertPendingRangeError(context, result);

        result = DataViewPrototype.getInt8(context, dataView, new JSValue[]{new JSSymbol("offset")});
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

        JSValue result = DataViewPrototype.getBuffer(context, detachedView, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(detachedBuffer);

        result = DataViewPrototype.getByteLength(context, detachedView, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);

        result = DataViewPrototype.getByteOffset(context, detachedView, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);

        JSArrayBuffer resizableBuffer = new JSArrayBuffer(context, 16, 16);
        JSDataView outOfBoundsView = new JSDataView(context, resizableBuffer, 8, 8);
        resizableBuffer.resize(4);

        result = DataViewPrototype.getBuffer(context, outOfBoundsView, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(resizableBuffer);

        result = DataViewPrototype.getByteLength(context, outOfBoundsView, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);

        result = DataViewPrototype.getByteOffset(context, outOfBoundsView, JSValue.NO_ARGS);
        assertPendingTypeError(context, result);
    }
}
