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
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

/**
 * Implementation of DataView.prototype methods.
 * Based on QuickJS DataView semantics.
 */
public final class DataViewPrototype {
    private DataViewPrototype() {
    }

    private static JSValue convertDataViewException(JSContext context, RuntimeException e) {
        if (e instanceof JSRangeErrorException) {
            return context.throwRangeError(e.getMessage());
        }
        return context.throwTypeError(e.getMessage());
    }

    public static JSValue getBigInt64(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getBigInt64 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return dataView.getBigInt64(byteOffset, littleEndian);
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue getBigUint64(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getBigUint64 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return dataView.getBigUint64(byteOffset, littleEndian);
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
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
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getFloat16 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return JSNumber.of(dataView.getFloat16(byteOffset, littleEndian));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    // Float32 methods
    public static JSValue getFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getFloat32 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return JSNumber.of(dataView.getFloat32(byteOffset, littleEndian));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    // Float64 methods
    public static JSValue getFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getFloat64 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return JSNumber.of(dataView.getFloat64(byteOffset, littleEndian));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    // Int16 methods
    public static JSValue getInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getInt16 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return JSNumber.of(dataView.getInt16(byteOffset, littleEndian));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    // Int32 methods
    public static JSValue getInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getInt32 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return JSNumber.of(dataView.getInt32(byteOffset, littleEndian));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    // Int8 methods
    public static JSValue getInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getInt8 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        try {
            return JSNumber.of(dataView.getInt8(byteOffset));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue getUint16(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getUint16 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return JSNumber.of(dataView.getUint16(byteOffset, littleEndian));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue getUint32(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getUint32 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return JSNumber.of(dataView.getUint32(byteOffset, littleEndian));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    // Uint8 methods
    public static JSValue getUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.getUint8 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        try {
            return JSNumber.of(dataView.getUint8(byteOffset));
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    private static JSBigInt parseBigInt(JSContext context, String value) {
        try {
            return JSTypeConversions.stringToBigInt(value);
        } catch (NumberFormatException e) {
            context.throwSyntaxError("invalid bigint literal");
            return null;
        }
    }

    private static JSDataView requireDataView(JSContext context, JSValue thisArg, String errorMessage) {
        if (!(thisArg instanceof JSDataView dataView)) {
            context.throwTypeError(errorMessage);
            return null;
        }
        return dataView;
    }

    public static JSValue setBigInt64(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setBigInt64 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        JSBigInt value = toBigInt(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (value == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setBigInt64(byteOffset, value, littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setBigUint64(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setBigUint64 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        JSBigInt value = toBigInt(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (value == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setBigUint64(byteOffset, value, littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setFloat16(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setFloat16 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        JSNumber numberValue = JSTypeConversions.toNumber(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setFloat16(byteOffset, (float) numberValue.value(), littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setFloat32 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        JSNumber numberValue = JSTypeConversions.toNumber(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setFloat32(byteOffset, (float) numberValue.value(), littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setFloat64 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        JSNumber numberValue = JSTypeConversions.toNumber(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setFloat64(byteOffset, numberValue.value(), littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setInt16 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        Long uint32Value = toUint32(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (uint32Value == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setInt16(byteOffset, (short) (uint32Value & 0xFFFF), littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setInt32 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        Long uint32Value = toUint32(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (uint32Value == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setInt32(byteOffset, (int) (uint32Value & 0xFFFFFFFFL), littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setInt8 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        Long uint32Value = toUint32(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (uint32Value == null) {
            return context.getPendingException();
        }
        try {
            dataView.setInt8(byteOffset, (byte) (uint32Value & 0xFF));
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setUint16(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setUint16 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        Long uint32Value = toUint32(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (uint32Value == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setUint16(byteOffset, (int) (uint32Value & 0xFFFF), littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setUint32(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setUint32 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        Long uint32Value = toUint32(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (uint32Value == null) {
            return context.getPendingException();
        }
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setUint32(byteOffset, uint32Value, littleEndian);
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
    }

    public static JSValue setUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = requireDataView(context, thisArg, "DataView.prototype.setUint8 called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        Integer byteOffset = toDataViewIndex(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (byteOffset == null) {
            return context.getPendingException();
        }
        Long uint32Value = toUint32(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (uint32Value == null) {
            return context.getPendingException();
        }
        try {
            dataView.setUint8(byteOffset, (int) (uint32Value & 0xFF));
            return JSUndefined.INSTANCE;
        } catch (RuntimeException e) {
            return convertDataViewException(context, e);
        }
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
        if (index > Integer.MAX_VALUE) {
            context.throwRangeError("byteOffset out of range");
            return null;
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
}
