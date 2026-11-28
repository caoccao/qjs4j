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
        assertNumberValue(callBuiltin(get, view, 0), value);
        assertThat(callBuiltin(set, view, 1, value)).isEqualTo(JSUndefined.INSTANCE);
        assertThat(buffer.getBuffer().get(1)).isEqualTo((byte) value);
        assertInvalidAccess(get, set, view, invalidReceiver);
    }

    private void assertInvalidAccess(JSNativeCallback get, JSNativeCallback set, JSDataView view, JSValue receiver) {
        assertPendingTypeError(callBuiltin(get, receiver, 0));
        assertPendingTypeError(callBuiltin(set, receiver, 0, 1));
        assertPendingRangeError(callBuiltin(get, view, 20));
        assertPendingRangeError(callBuiltin(set, view, 20, 1));
    }

    private void assertMultibyteAccess(JSNativeCallback get, JSNativeCallback set, double bigEndian,
            double littleEndian, int secondOffset, JSValue invalidReceiver) {
        JSDataView view = new JSDataView(context, new JSArrayBuffer(context, 16));
        assertThat(callBuiltin(set, view, 0, bigEndian)).isEqualTo(JSUndefined.INSTANCE);
        assertNumberValue(callBuiltin(get, view, 0), bigEndian);
        JSNumber offset = JSNumber.of(secondOffset);
        assertThat(callBuiltin(set, view, new JSValue[]{offset, JSNumber.of(littleEndian), JSBoolean.TRUE}))
                .isEqualTo(JSUndefined.INSTANCE);
        assertNumberValue(callBuiltin(get, view, new JSValue[]{offset, JSBoolean.TRUE}), littleEndian);
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

        var getBuffer = nativeMethod(DataViewPrototype::getBuffer);
        assertThat(getBuffer.call(dataView)).isEqualTo(buffer);

        // Edge case: called on non-DataView
        assertPendingTypeError(getBuffer.call("not a dataview"));
    }

    @Test
    public void testGetByteLength() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        var getByteLength = nativeMethod(DataViewPrototype::getByteLength);
        assertNumberValue(getByteLength.call(dataView), 16.0);

        // With offset and length
        JSDataView dataView2 = new JSDataView(context, buffer, 4, 8);
        assertNumberValue(getByteLength.call(dataView2), 8.0);

        // Edge case: called on non-DataView
        assertPendingTypeError(getByteLength.call(123));
    }

    @Test
    public void testGetByteOffset() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        var getByteOffset = nativeMethod(DataViewPrototype::getByteOffset);
        assertNumberValue(getByteOffset.call(dataView), 0.0);

        // With offset
        JSDataView dataView2 = new JSDataView(context, buffer, 4, 8);
        assertNumberValue(getByteOffset.call(dataView2), 4.0);

        // Edge case: called on non-DataView
        assertPendingTypeError(getByteOffset.call(new JSObject(context)));
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
        assertThat(setBigInt64.call(0, "1")).isEqualTo(JSUndefined.INSTANCE);
        var getBigInt64 = builtin(DataViewPrototype::getBigInt64, dataView);
        assertThat(getBigInt64.call(0)).isEqualTo(new JSBigInt(1));

        assertThat(setBigInt64.call(0, JSBoolean.TRUE)).isEqualTo(JSUndefined.INSTANCE);
        assertThat(getBigInt64.call(0)).isEqualTo(new JSBigInt(1));

        assertThat(callBuiltin(DataViewPrototype::setBigUint64, dataView, 8, new JSBigInt(-1)))
                .isEqualTo(JSUndefined.INSTANCE);
        assertThat(callBuiltin(DataViewPrototype::getBigUint64, dataView, 8))
                .isEqualTo(new JSBigInt("18446744073709551615"));

        assertPendingTypeError(setBigInt64.call(0, 1));

        assertPendingTypeError(setBigInt64.call(0, JSUndefined.INSTANCE));
    }

    @Test
    public void testSetGetFloat16Uint16Uint32() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        assertThat(callBuiltin(DataViewPrototype::setFloat16, dataView, 0, 1.5, JSBoolean.TRUE))
                .isEqualTo(JSUndefined.INSTANCE);
        assertThat(callBuiltin(DataViewPrototype::getFloat16, dataView, 0, JSBoolean.TRUE)).isInstanceOfSatisfying(
                JSNumber.class, jsNum -> assertThat(jsNum.value()).isCloseTo(1.5, Offset.offset(0.001)));

        assertThat(callBuiltin(DataViewPrototype::setUint16, dataView, 2, 65535, JSBoolean.TRUE))
                .isEqualTo(JSUndefined.INSTANCE);
        assertNumberValue(callBuiltin(DataViewPrototype::getUint16, dataView, 2, JSBoolean.TRUE), 65535.0);

        assertThat(callBuiltin(DataViewPrototype::setUint32, dataView, 4, 4294967295L, JSBoolean.TRUE))
                .isEqualTo(JSUndefined.INSTANCE);
        assertNumberValue(callBuiltin(DataViewPrototype::getUint32, dataView, 4, JSBoolean.TRUE), 4294967295.0);
    }

    @Test
    public void testToIndexAndOutOfBoundsSemantics() {
        JSArrayBuffer buffer = new JSArrayBuffer(context, 16);
        JSDataView dataView = new JSDataView(context, buffer);

        assertThat(callBuiltin(DataViewPrototype::setInt8, dataView, 1.9, 7)).isEqualTo(JSUndefined.INSTANCE);
        var getInt8 = builtin(DataViewPrototype::getInt8, dataView);
        assertNumberValue(getInt8.call(1), 7.0);

        assertPendingRangeError(getInt8.call(-1));

        assertPendingTypeError(getInt8.call(new JSSymbol("offset")));
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

        var getBuffer = nativeMethod(DataViewPrototype::getBuffer);
        assertThat(getBuffer.call(detachedView)).isEqualTo(detachedBuffer);

        var getByteLength = nativeMethod(DataViewPrototype::getByteLength);
        assertPendingTypeError(getByteLength.call(detachedView));

        var getByteOffset = nativeMethod(DataViewPrototype::getByteOffset);
        assertPendingTypeError(getByteOffset.call(detachedView));

        JSArrayBuffer resizableBuffer = new JSArrayBuffer(context, 16, 16);
        JSDataView outOfBoundsView = new JSDataView(context, resizableBuffer, 8, 8);
        resizableBuffer.resize(4);

        assertThat(getBuffer.call(outOfBoundsView)).isEqualTo(resizableBuffer);

        assertPendingTypeError(getByteLength.call(outOfBoundsView));

        assertPendingTypeError(getByteOffset.call(outOfBoundsView));
    }
}
