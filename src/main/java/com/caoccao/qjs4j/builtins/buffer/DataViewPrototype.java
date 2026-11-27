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

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.JSDataView;

/**
 * Implementation of DataView.prototype methods. Based on QuickJS DataView semantics.
 */
public final class DataViewPrototype {
    private DataViewPrototype() {
    }

    public static JSValue getBigInt64(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.BIGINT64.get(context, thisArg, args);
    }

    public static JSValue getBigUint64(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.BIGUINT64.get(context, thisArg, args);
    }

    /**
     * get DataView.prototype.buffer
     */
    public static JSValue getBuffer(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = DataViewElement.requireDataView(context, thisArg,
                "get DataView.prototype.buffer called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        return dataView.getBuffer();
    }

    /**
     * get DataView.prototype.byteLength
     */
    public static JSValue getByteLength(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDataView dataView = DataViewElement.requireDataView(context, thisArg,
                "get DataView.prototype.byteLength called on non-DataView");
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
        JSDataView dataView = DataViewElement.requireDataView(context, thisArg,
                "get DataView.prototype.byteOffset called on non-DataView");
        if (dataView == null) {
            return context.getPendingException();
        }
        if (dataView.isOutOfBounds()) {
            return context.throwTypeError("DataView is out of bounds");
        }
        return JSNumber.of(dataView.getByteOffset());
    }

    public static JSValue getFloat16(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.FLOAT16.get(context, thisArg, args);
    }

    public static JSValue getFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.FLOAT32.get(context, thisArg, args);
    }

    public static JSValue getFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.FLOAT64.get(context, thisArg, args);
    }

    public static JSValue getInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.INT16.get(context, thisArg, args);
    }

    public static JSValue getInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.INT32.get(context, thisArg, args);
    }

    public static JSValue getInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.INT8.get(context, thisArg, args);
    }

    public static JSValue getUint16(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.UINT16.get(context, thisArg, args);
    }

    public static JSValue getUint32(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.UINT32.get(context, thisArg, args);
    }

    public static JSValue getUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.UINT8.get(context, thisArg, args);
    }

    public static JSValue setBigInt64(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.BIGINT64.set(context, thisArg, args);
    }

    public static JSValue setBigUint64(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.BIGUINT64.set(context, thisArg, args);
    }

    public static JSValue setFloat16(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.FLOAT16.set(context, thisArg, args);
    }

    public static JSValue setFloat32(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.FLOAT32.set(context, thisArg, args);
    }

    public static JSValue setFloat64(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.FLOAT64.set(context, thisArg, args);
    }

    public static JSValue setInt16(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.INT16.set(context, thisArg, args);
    }

    public static JSValue setInt32(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.INT32.set(context, thisArg, args);
    }

    public static JSValue setInt8(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.INT8.set(context, thisArg, args);
    }

    public static JSValue setUint16(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.UINT16.set(context, thisArg, args);
    }

    public static JSValue setUint32(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.UINT32.set(context, thisArg, args);
    }

    public static JSValue setUint8(JSContext context, JSValue thisArg, JSValue[] args) {
        return DataViewElement.UINT8.set(context, thisArg, args);
    }

}
