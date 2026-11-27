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

import com.caoccao.qjs4j.builtins.number.NumberPrototype;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.IJSArrayBuffer;
import com.caoccao.qjs4j.core.buffer.JSDataView;

import java.util.function.BiFunction;

/** Describes both directions of access for a DataView element type. */
final class DataViewElement<T> {
    static final DataViewElement<JSBigInt> BIGINT64 = new DataViewElement<>("BigInt64", 8, JSDataView::getBigInt64,
            DataViewElement::toBigInt,
            (view, offset, value, littleEndian) -> view.setBigInt64(offset, value, littleEndian));
    static final DataViewElement<JSBigInt> BIGUINT64 = new DataViewElement<>("BigUint64", 8, JSDataView::getBigUint64,
            DataViewElement::toBigInt,
            (view, offset, value, littleEndian) -> view.setBigUint64(offset, value, littleEndian));
    static final DataViewElement<JSNumber> FLOAT16 = new DataViewElement<>("Float16", 2,
            (view, offset, littleEndian) -> JSNumber.of(view.getFloat16(offset, littleEndian)),
            JSTypeConversions::toNumber,
            (view, offset, value, littleEndian) -> view.setFloat16(offset, value.value(), littleEndian));
    static final DataViewElement<JSNumber> FLOAT32 = new DataViewElement<>("Float32", 4,
            (view, offset, littleEndian) -> JSNumber.of(view.getFloat32(offset, littleEndian)),
            JSTypeConversions::toNumber,
            (view, offset, value, littleEndian) -> view.setFloat32(offset, (float) value.value(), littleEndian));
    static final DataViewElement<JSNumber> FLOAT64 = new DataViewElement<>("Float64", 8,
            (view, offset, littleEndian) -> JSNumber.of(view.getFloat64(offset, littleEndian)),
            JSTypeConversions::toNumber,
            (view, offset, value, littleEndian) -> view.setFloat64(offset, value.value(), littleEndian));
    static final DataViewElement<Long> INT16 = new DataViewElement<>("Int16", 2,
            (view, offset, littleEndian) -> JSNumber.of(view.getInt16(offset, littleEndian)), DataViewElement::toUint32,
            (view, offset, value, littleEndian) -> view.setInt16(offset, (short) (value & 0xFFFF), littleEndian));
    static final DataViewElement<Long> INT32 = new DataViewElement<>("Int32", 4,
            (view, offset, littleEndian) -> JSNumber.of(view.getInt32(offset, littleEndian)), DataViewElement::toUint32,
            (view, offset, value, littleEndian) -> view.setInt32(offset, (int) (value & 0xFFFFFFFFL), littleEndian));
    static final DataViewElement<Long> INT8 = new DataViewElement<>("Int8", 1,
            (view, offset, littleEndian) -> JSNumber.of(view.getInt8(offset)), DataViewElement::toUint32,
            (view, offset, value, littleEndian) -> view.setInt8(offset, (byte) (value & 0xFF)));
    static final DataViewElement<Long> UINT16 = new DataViewElement<>("Uint16", 2,
            (view, offset, littleEndian) -> JSNumber.of(view.getUint16(offset, littleEndian)),
            DataViewElement::toUint32,
            (view, offset, value, littleEndian) -> view.setUint16(offset, (int) (value & 0xFFFF), littleEndian));
    static final DataViewElement<Long> UINT32 = new DataViewElement<>("Uint32", 4,
            (view, offset, littleEndian) -> JSNumber.of(view.getUint32(offset, littleEndian)),
            DataViewElement::toUint32,
            (view, offset, value, littleEndian) -> view.setUint32(offset, value, littleEndian));
    static final DataViewElement<Long> UINT8 = new DataViewElement<>("Uint8", 1,
            (view, offset, littleEndian) -> JSNumber.of(view.getUint8(offset)), DataViewElement::toUint32,
            (view, offset, value, littleEndian) -> view.setUint8(offset, (int) (value & 0xFF)));

    private final BiFunction<JSContext, JSValue, T> converter;
    private final String getError;
    private final DataViewReader reader;
    private final String setError;
    private final int size;
    private final DataViewWriter<T> writer;

    private DataViewElement(String name, int size, DataViewReader reader, BiFunction<JSContext, JSValue, T> converter,
            DataViewWriter<T> writer) {
        this.size = size;
        this.getError = "DataView.prototype.get" + name + " called on non-DataView";
        this.setError = "DataView.prototype.set" + name + " called on non-DataView";
        this.reader = reader;
        this.converter = converter;
        this.writer = writer;
    }

    /**
     * GetViewValue: validate the receiver, convert the offset, then check the current buffer state.
     */
    JSValue get(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, getError);
        if (dataView == null) {
            return context.getPendingException();
        }
        long byteOffset = toDataViewIndex(context, JSValue.argumentOrUndefined(args, 0));
        if (byteOffset < 0) {
            return context.getPendingException();
        }
        boolean littleEndian = size > 1 && args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        JSValue error = checkAccess(context, dataView, byteOffset, size);
        if (error != null) {
            return error;
        }
        return reader.read(dataView, byteOffset, littleEndian);
    }

    /**
     * SetViewValue: offset and value conversion precede buffer bounds checks, since either conversion can resize or
     * detach the buffer. The writer receives the converted value.
     */
    JSValue set(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, setError);
        if (dataView == null) {
            return context.getPendingException();
        }
        if (dataView.isImmutable()) {
            return context.throwTypeError("cannot write to an immutable ArrayBuffer");
        }
        long byteOffset = toDataViewIndex(context, JSValue.argumentOrUndefined(args, 0));
        if (byteOffset < 0) {
            return context.getPendingException();
        }
        T value = converter.apply(context, JSValue.argumentOrUndefined(args, 1));
        if (value == null || context.hasPendingException()) {
            return context.getPendingException();
        }
        boolean littleEndian = size > 1 && args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        JSValue error = checkAccess(context, dataView, byteOffset, size);
        if (error != null) {
            return error;
        }
        writer.write(dataView, byteOffset, value, littleEndian);
        return JSUndefined.INSTANCE;
    }

    /**
     * Pre-validate a DataView access. Returns null on success, or a JS error value on failure.
     */
    private static JSValue checkAccess(JSContext context, JSDataView dataView, long byteOffset, int size) {
        if (((IJSArrayBuffer) dataView.getBuffer()).isDetached()) {
            return context.throwTypeError("ArrayBuffer is detached");
        }
        if (dataView.isOutOfBounds()) {
            return context.throwTypeError("DataView is out of bounds");
        }
        long effectiveByteLength = dataView.getByteLength();
        if (byteOffset < 0 || byteOffset + size > effectiveByteLength) {
            return context.throwRangeError("DataView offset out of range");
        }
        return null;
    }

    private static JSBigInt parseBigInt(JSContext context, String value) {
        JSBigInt parsed = JSTypeConversions.stringToBigInt(value);
        if (parsed == null) {
            context.throwSyntaxError("invalid bigint literal");
            return null;
        }
        return parsed;
    }

    static JSDataView requireDataView(JSContext context, JSValue thisArg, String errorMessage) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.rejectTypeError(errorMessage);
        }
        return dataView;
    }

    private static JSBigInt toBigInt(JSContext context, JSValue value) {
        JSValue primitive = JSTypeConversions.toBigIntPrimitive(context, value);
        if (primitive instanceof JSBigInt bigInt) {
            return bigInt;
        }
        if (primitive instanceof JSString string) {
            return parseBigInt(context, string.value());
        }
        if (!context.hasPendingException()) {
            context.throwTypeError("Cannot convert value to BigInt");
        }
        return null;
    }

    private static long toDataViewIndex(JSContext context, JSValue value) {
        double index = JSTypeConversions.toInteger(context, value);
        if (context.hasPendingException()) {
            return -1;
        }
        if (index < 0 || Double.isInfinite(index)) {
            context.throwRangeError("Invalid byteOffset");
            return -1;
        }
        if (index > NumberPrototype.MAX_SAFE_INTEGER) {
            context.throwRangeError("byteOffset out of range");
            return -1;
        }
        return (long) index;
    }

    private static Long toUint32(JSContext context, JSValue value) {
        long uint32Value = JSTypeConversions.toUint32(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return uint32Value;
    }

    @FunctionalInterface
    private interface DataViewReader {
        JSValue read(JSDataView view, long byteOffset, boolean littleEndian);
    }

    @FunctionalInterface
    private interface DataViewWriter<T> {
        void write(JSDataView view, long byteOffset, T value, boolean littleEndian);
    }
}
