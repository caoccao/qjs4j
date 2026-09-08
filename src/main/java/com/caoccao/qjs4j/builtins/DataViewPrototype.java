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

import com.caoccao.qjs4j.core.*;

import java.util.function.BiFunction;

/**
 * Implementation of DataView.prototype methods.
 * Based on QuickJS DataView semantics.
 */
public final class DataViewPrototype {
    private DataViewPrototype() {
    }

    /**
     * Pre-validate a DataView access. Returns null on success, or a JS error value on failure.
     */
    private static JSValue checkAccess(JSContext context, JSDataView dataView, int byteOffset, int size) {
        if (((IJSArrayBuffer) dataView.getBuffer()).isDetached()) {
            return context.throwTypeError("ArrayBuffer is detached");
        }
        if (dataView.isOutOfBounds()) {
            return context.throwTypeError("DataView is out of bounds");
        }
        int effectiveByteLength = dataView.getByteLength();
        if (byteOffset < 0 || (long) byteOffset + size > effectiveByteLength) {
            return context.throwRangeError("DataView offset out of range");
        }
        return null;
    }

    public static JSValue getBigInt64(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getBigInt64 called on non-DataView", 8,
                JSDataView::getBigInt64);
    }

    public static JSValue getBigUint64(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getBigUint64 called on non-DataView", 8,
                JSDataView::getBigUint64);
    }

    /**
     * get DataView.prototype.buffer
     */
    public static JSValue getBuffer(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "get DataView.prototype.buffer called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        return dataView.getBuffer();
    }

    /**
     * get DataView.prototype.byteLength
     */
    public static JSValue getByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "get DataView.prototype.byteLength called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        if (dataView.isOutOfBounds()) {
            return context.throwTypeError("DataView is out of bounds");
        }
        return JSNumber.of(dataView.getByteLength());
    }

    /**
     * get DataView.prototype.byteOffset
     */
    public static JSValue getByteOffset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "get DataView.prototype.byteOffset called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        if (dataView.isOutOfBounds()) {
            return context.throwTypeError("DataView is out of bounds");
        }
        return JSNumber.of(dataView.getByteOffset());
    }

    public static JSValue getFloat16(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getFloat16 called on non-DataView", 2,
                (view, offset, littleEndian) -> JSNumber.of(view.getFloat16(offset, littleEndian)));
    }

    // Float32 methods
    public static JSValue getFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getFloat32 called on non-DataView", 4,
                (view, offset, littleEndian) -> JSNumber.of(view.getFloat32(offset, littleEndian)));
    }

    // Float64 methods
    public static JSValue getFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getFloat64 called on non-DataView", 8,
                (view, offset, littleEndian) -> JSNumber.of(view.getFloat64(offset, littleEndian)));
    }

    // Int16 methods
    public static JSValue getInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getInt16 called on non-DataView", 2,
                (view, offset, littleEndian) -> JSNumber.of(view.getInt16(offset, littleEndian)));
    }

    // Int32 methods
    public static JSValue getInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getInt32 called on non-DataView", 4,
                (view, offset, littleEndian) -> JSNumber.of(view.getInt32(offset, littleEndian)));
    }

    // Int8 methods
    public static JSValue getInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getInt8 called on non-DataView", 1,
                (view, offset, littleEndian) -> JSNumber.of(view.getInt8(offset)));
    }

    public static JSValue getUint16(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getUint16 called on non-DataView", 2,
                (view, offset, littleEndian) -> JSNumber.of(view.getUint16(offset, littleEndian)));
    }

    public static JSValue getUint32(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getUint32 called on non-DataView", 4,
                (view, offset, littleEndian) -> JSNumber.of(view.getUint32(offset, littleEndian)));
    }

    // Uint8 methods
    public static JSValue getUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        return getValue(context, thisArg, args, "DataView.prototype.getUint8 called on non-DataView", 1,
                (view, offset, littleEndian) -> JSNumber.of(view.getUint8(offset)));
    }

    /**
     * GetViewValue: validate the receiver, convert the offset, then check the current buffer state.
     */
    private static JSValue getValue(
            JSContext context, JSValue thisArg, JSValue[] args, String receiverError,
            int size, DataViewReader reader) {
        JSDataView dataView = requireDataView(context, thisArg, receiverError);
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = size > 1 && args.length > 1
                && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        JSValue error = checkAccess(context, dataView, byteOffset, size);
        if (error != null) {
            return error;
        }
        return reader.read(dataView, byteOffset, littleEndian);
    }

    private static JSBigInt parseBigInt(JSContext context, String value) {
        JSBigInt parsed = JSTypeConversions.stringToBigInt(value);
        if (parsed == null) {
            context.throwSyntaxError("invalid bigint literal");
            return null;
        }
        return parsed;
    }

    private static JSDataView requireDataView(JSContext context, JSValue thisArg, String errorMessage) {
        if (!(thisArg instanceof JSDataView dataView)) {
            context.throwTypeError(errorMessage);
            return null;
        }
        return dataView;
    }

    public static JSValue setBigInt64(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setBigInt64 called on non-DataView", 8,
                DataViewPrototype::toBigInt,
                (view, offset, value, littleEndian) -> view.setBigInt64(offset, value, littleEndian));
    }

    public static JSValue setBigUint64(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setBigUint64 called on non-DataView", 8,
                DataViewPrototype::toBigInt,
                (view, offset, value, littleEndian) -> view.setBigUint64(offset, value, littleEndian));
    }

    public static JSValue setFloat16(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setFloat16 called on non-DataView", 2,
                JSTypeConversions::toNumber,
                (view, offset, value, littleEndian) -> view.setFloat16(offset, value.value(), littleEndian));
    }

    public static JSValue setFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setFloat32 called on non-DataView", 4,
                JSTypeConversions::toNumber,
                (view, offset, value, littleEndian) -> view.setFloat32(offset, (float) value.value(), littleEndian));
    }

    public static JSValue setFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setFloat64 called on non-DataView", 8,
                JSTypeConversions::toNumber,
                (view, offset, value, littleEndian) -> view.setFloat64(offset, value.value(), littleEndian));
    }

    public static JSValue setInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setInt16 called on non-DataView", 2,
                DataViewPrototype::toUint32,
                (view, offset, value, littleEndian) -> view.setInt16(offset, (short) (value & 0xFFFF), littleEndian));
    }

    public static JSValue setInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setInt32 called on non-DataView", 4,
                DataViewPrototype::toUint32,
                (view, offset, value, littleEndian) -> view.setInt32(offset, (int) (value & 0xFFFFFFFFL), littleEndian));
    }

    public static JSValue setInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setInt8 called on non-DataView", 1,
                DataViewPrototype::toUint32,
                (view, offset, value, littleEndian) -> view.setInt8(offset, (byte) (value & 0xFF)));
    }

    public static JSValue setUint16(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setUint16 called on non-DataView", 2,
                DataViewPrototype::toUint32,
                (view, offset, value, littleEndian) -> view.setUint16(offset, (int) (value & 0xFFFF), littleEndian));
    }

    public static JSValue setUint32(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setUint32 called on non-DataView", 4,
                DataViewPrototype::toUint32,
                (view, offset, value, littleEndian) -> view.setUint32(offset, value, littleEndian));
    }

    public static JSValue setUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValue(context, thisArg, args, "DataView.prototype.setUint8 called on non-DataView", 1,
                DataViewPrototype::toUint32,
                (view, offset, value, littleEndian) -> view.setUint8(offset, (int) (value & 0xFF)));
    }

    /**
     * SetViewValue: offset and value conversion precede buffer bounds checks, since either
     * conversion can resize or detach the buffer. The writer receives the converted value.
     */
    private static <T> JSValue setValue(
            JSContext context, JSValue thisArg, JSValue[] args, String receiverError, int size,
            BiFunction<JSContext, JSValue, T> converter, DataViewWriter<T> writer) {
        JSDataView dataView = requireDataView(context, thisArg, receiverError);
        if (dataView == null) {
            return context.getPendingException();
        }
        if (dataView.isImmutable()) {
            return context.throwTypeError("cannot write to an immutable ArrayBuffer");
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        T value = converter.apply(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (value == null || context.hasPendingException()) {
            return context.getPendingException();
        }
        boolean littleEndian = size > 1 && args.length > 2
                && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        JSValue error = checkAccess(context, dataView, byteOffset, size);
        if (error != null) {
            return error;
        }
        writer.write(dataView, byteOffset, value, littleEndian);
        return JSUndefined.INSTANCE;
    }

    private static JSBigInt toBigInt(JSContext context, JSValue value) {
        if (value instanceof JSBigInt bigInt) {
            return bigInt;
        }
        if (value instanceof JSBigIntObject bigIntObject) {
            return bigIntObject.getValue();
        }
        if (value instanceof JSBoolean booleanValue) {
            return new JSBigInt(booleanValue.value() ? 1L : 0L);
        }
        if (value instanceof JSString stringValue) {
            return parseBigInt(context, stringValue.value());
        }
        if (value instanceof JSObject objectValue) {
            JSValue primitive = JSTypeConversions.toPrimitive(context, objectValue, JSTypeConversions.PreferredType.NUMBER);
            if (context.hasPendingException()) {
                return null;
            }
            return toBigInt(context, primitive);
        }
        context.throwTypeError("Cannot convert value to BigInt");
        return null;
    }

    private static Integer toDataViewIndex(JSContext context, JSValue value) {
        double index = JSTypeConversions.toInteger(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        if (index < 0 || Double.isInfinite(index)) {
            context.throwRangeError("Invalid byteOffset");
            return null;
        }
        if (index > NumberPrototype.MAX_SAFE_INTEGER) {
            context.throwRangeError("byteOffset out of range");
            return null;
        }
        if (index > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) index;
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
        JSValue read(JSDataView view, int byteOffset, boolean littleEndian);
    }

    @FunctionalInterface
    private interface DataViewWriter<T> {
        void write(JSDataView view, int byteOffset, T value, boolean littleEndian);
    }
}
