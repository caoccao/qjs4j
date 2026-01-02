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
import com.caoccao.qjs4j.exceptions.JSException;

public final class TypedArrayPrototype {
    public static JSValue createBigInt64ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor BigInt64Array requires 'new'"));
    }

    public static JSValue createBigUint64ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor BigUint64Array requires 'new'"));
    }

    public static JSValue createFloat16ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Float16Array requires 'new'"));
    }

    public static JSValue createFloat32ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Float32Array requires 'new'"));
    }

    public static JSValue createFloat64ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Float64Array requires 'new'"));
    }

    public static JSValue createInt16ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Int16Array requires 'new'"));
    }

    public static JSValue createInt32ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Int32Array requires 'new'"));
    }

    public static JSValue createInt8ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Int8Array requires 'new'"));
    }

    public static JSValue createUint16ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Uint16Array requires 'new'"));
    }

    public static JSValue createUint32ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Uint32Array requires 'new'"));
    }

    public static JSValue createUint8ArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Uint8Array requires 'new'"));
    }

    public static JSValue createUint8ClampedArrayWithoutNew(JSContext context, JSValue thisArg, JSValue[] args) {
        throw new JSException(context.throwTypeError("Constructor Uint8ClampedArray requires 'new'"));
    }

    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg.isNullOrUndefined()) {
            throw new JSException(context.throwTypeError("Cannot convert undefined or null to object"));
        } else if (thisArg instanceof JSTypedArray jsTypedArray) {
            return new JSString(jsTypedArray.toString());
        }
        return JSTypeConversions.toString(context, thisArg);
    }
}
