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

/**
 * Implementation of DataView.prototype methods.
 * Based on ES2020 DataView specification.
 */
public final class DataViewPrototype {

    /**
     * get DataView.prototype.buffer
     */
    public static JSValue getBuffer(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("get DataView.prototype.buffer called on non-DataView");
        }
        return dataView.getBuffer();
    }

    /**
     * get DataView.prototype.byteLength
     */
    public static JSValue getByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("get DataView.prototype.byteLength called on non-DataView");
        }
        return new JSNumber(dataView.getByteLength());
    }

    /**
     * get DataView.prototype.byteOffset
     */
    public static JSValue getByteOffset(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("get DataView.prototype.byteOffset called on non-DataView");
        }
        return new JSNumber(dataView.getByteOffset());
    }

    // Float32 methods
    public static JSValue getFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.getFloat32 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return new JSNumber(dataView.getFloat32(byteOffset, littleEndian));
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    // Float64 methods
    public static JSValue getFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.getFloat64 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return new JSNumber(dataView.getFloat64(byteOffset, littleEndian));
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    // Int16 methods
    public static JSValue getInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.getInt16 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return new JSNumber(dataView.getInt16(byteOffset, littleEndian));
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    // Int32 methods
    public static JSValue getInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.getInt32 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        boolean littleEndian = args.length > 1 && JSTypeConversions.toBoolean(args[1]) == JSBoolean.TRUE;
        try {
            return new JSNumber(dataView.getInt32(byteOffset, littleEndian));
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    // Int8 methods
    public static JSValue getInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.getInt8 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        try {
            return new JSNumber(dataView.getInt8(byteOffset));
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    // Uint8 methods
    public static JSValue getUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.getUint8 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        try {
            return new JSNumber(dataView.getUint8(byteOffset));
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue setFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.setFloat32 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        float value = args.length > 1 ? (float) JSTypeConversions.toNumber(context, args[1]).value() : 0;
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setFloat32(byteOffset, value, littleEndian);
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue setFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.setFloat64 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        double value = args.length > 1 ? JSTypeConversions.toNumber(context, args[1]).value() : 0;
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setFloat64(byteOffset, value, littleEndian);
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue setInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.setInt16 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        short value = args.length > 1 ? (short) JSTypeConversions.toInt32(context, args[1]) : 0;
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setInt16(byteOffset, value, littleEndian);
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue setInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.setInt32 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        int value = args.length > 1 ? JSTypeConversions.toInt32(context, args[1]) : 0;
        boolean littleEndian = args.length > 2 && JSTypeConversions.toBoolean(args[2]) == JSBoolean.TRUE;
        try {
            dataView.setInt32(byteOffset, value, littleEndian);
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue setInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.setInt8 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        byte value = args.length > 1 ? (byte) JSTypeConversions.toInt32(context, args[1]) : 0;
        try {
            dataView.setInt8(byteOffset, value);
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue setUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDataView dataView)) {
            return context.throwTypeError("DataView.prototype.setUint8 called on non-DataView");
        }
        int byteOffset = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        int value = args.length > 1 ? JSTypeConversions.toInt32(context, args[1]) : 0;
        try {
            dataView.setUint8(byteOffset, value);
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            return context.throwRangeError(e.getMessage());
        }
    }
}
