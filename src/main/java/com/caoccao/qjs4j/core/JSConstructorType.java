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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.exceptions.JSException;

/**
 * Enum representing internal constructor type markers.
 * These correspond to [[Constructor]] internal slots in the ECMAScript specification.
 */
public enum JSConstructorType {
    AGGREGATE_ERROR(JSAggregateError::create),
    ARRAY(null),
    ARRAY_BUFFER(null),
    BIG_INT_OBJECT(JSBigIntObject::create),
    BOOLEAN_OBJECT(JSBooleanObject::create),
    DATA_VIEW(null),
    DATE(JSDate::create),
    ERROR(JSError::create),
    EVAL_ERROR(JSEvalError::create),
    FINALIZATION_REGISTRY(JSFinalizationRegistry::create),
    MAP(JSMap::create),
    NUMBER_OBJECT(JSNumberObject::create),
    PROMISE(JSPromise::create),
    PROXY(JSProxy::create),
    RANGE_ERROR(JSRangeError::create),
    REFERENCE_ERROR(JSReferenceError::create),
    REGEXP(JSRegExp::create),
    SET(JSSet::create),
    SHARED_ARRAY_BUFFER(JSSharedArrayBuffer::create),
    STRING_OBJECT(JSStringObject::create),
    SUPPRESSED_ERROR(JSSuppressedError::create),
    SYMBOL_OBJECT(JSSymbolObject::create),
    SYNTAX_ERROR(JSSyntaxError::create),
    TYPED_ARRAY_BIGINT64(JSBigInt64Array::create),
    TYPED_ARRAY_BIGUINT64(JSBigUint64Array::create),
    TYPED_ARRAY_FLOAT16(JSFloat16Array::create),
    TYPED_ARRAY_FLOAT32(JSFloat32Array::create),
    TYPED_ARRAY_FLOAT64(JSFloat64Array::create),
    TYPED_ARRAY_INT16(JSInt16Array::create),
    TYPED_ARRAY_INT32(JSInt32Array::create),
    TYPED_ARRAY_INT8(JSInt8Array::create),
    TYPED_ARRAY_UINT16(JSUint16Array::create),
    TYPED_ARRAY_UINT32(JSUint32Array::create),
    TYPED_ARRAY_UINT8(JSUint8Array::create),
    TYPED_ARRAY_UINT8_CLAMPED(JSUint8ClampedArray::create),
    TYPE_ERROR(JSTypeError::create),
    URI_ERROR(JSURIError::create),
    WEAK_MAP(JSWeakMap::create),
    WEAK_REF(JSWeakRef::create),
    WEAK_SET(JSWeakSet::create),
    ;

    private final JSConstructor constructor;

    JSConstructorType(JSConstructor constructor) {
        this.constructor = constructor;
    }

    public JSObject create(JSContext context, JSValue... args) {
        if (constructor == null) {
            throw new JSException(context.throwTypeError("Constructor for " + this.name() + " is not implemented"));
        }
        return constructor.construct(context, args);
    }
}
