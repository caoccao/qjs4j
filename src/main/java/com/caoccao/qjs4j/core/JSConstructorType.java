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

import com.caoccao.qjs4j.core.buffer.*;
import com.caoccao.qjs4j.core.collection.*;
import com.caoccao.qjs4j.core.date.JSDate;
import com.caoccao.qjs4j.core.disposable.JSAsyncDisposableStack;
import com.caoccao.qjs4j.core.disposable.JSDisposableStack;
import com.caoccao.qjs4j.core.error.*;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import com.caoccao.qjs4j.exceptions.JSException;

/**
 * Enum representing internal constructor type markers. These correspond to [[Constructor]] internal slots in the
 * ECMAScript specification.
 */
public enum JSConstructorType {
    AGGREGATE_ERROR(JSAggregateError.NAME, JSAggregateError::create),
    ARRAY(JSArray.NAME, JSArray::create),
    ARRAY_BUFFER(JSArrayBuffer.NAME, JSArrayBuffer::create),
    ASYNC_DISPOSABLE_STACK(JSAsyncDisposableStack.NAME, JSAsyncDisposableStack::create),
    BIG_INT_OBJECT(JSBigIntObject.NAME, JSBigIntObject::create),
    BOOLEAN_OBJECT(JSBooleanObject.NAME, JSBooleanObject::create),
    DATA_VIEW(JSDataView.NAME, JSDataView::create),
    DATE(JSDate.NAME, JSDate::create),
    DISPOSABLE_STACK(JSDisposableStack.NAME, JSDisposableStack::create),
    ERROR(JSError.NAME, JSError::create),
    EVAL_ERROR(JSEvalError.NAME, JSEvalError::create),
    FINALIZATION_REGISTRY(JSFinalizationRegistry.NAME, JSFinalizationRegistry::create),
    MAP(JSMap.NAME, JSMap::create),
    NUMBER_OBJECT(JSNumberObject.NAME, JSNumberObject::create),
    PROMISE(JSPromise.NAME, JSPromise::create),
    PROXY(JSObject.NAME, JSProxy::create),
    RANGE_ERROR(JSRangeError.NAME, JSRangeError::create),
    REFERENCE_ERROR(JSReferenceError.NAME, JSReferenceError::create),
    REGEXP(JSRegExp.NAME, JSRegExp::create),
    SET(JSSet.NAME, JSSet::create),
    SHARED_ARRAY_BUFFER(JSSharedArrayBuffer.NAME, JSSharedArrayBuffer::create),
    STRING_OBJECT(JSStringObject.NAME, JSStringObject::create),
    SUPPRESSED_ERROR(JSSuppressedError.NAME, JSSuppressedError::create),
    SYMBOL_OBJECT(JSSymbolObject.NAME, JSSymbolObject::create),
    SYNTAX_ERROR(JSSyntaxError.NAME, JSSyntaxError::create),
    TYPE_ERROR(JSTypeError.NAME, JSTypeError::create),
    TYPED_ARRAY_BIGINT64(JSBigInt64Array.NAME, JSBigInt64Array::create),
    TYPED_ARRAY_BIGUINT64(JSBigUint64Array.NAME, JSBigUint64Array::create),
    TYPED_ARRAY_FLOAT16(JSFloat16Array.NAME, JSFloat16Array::create),
    TYPED_ARRAY_FLOAT32(JSFloat32Array.NAME, JSFloat32Array::create),
    TYPED_ARRAY_FLOAT64(JSFloat64Array.NAME, JSFloat64Array::create),
    TYPED_ARRAY_INT16(JSInt16Array.NAME, JSInt16Array::create),
    TYPED_ARRAY_INT32(JSInt32Array.NAME, JSInt32Array::create),
    TYPED_ARRAY_INT8(JSInt8Array.NAME, JSInt8Array::create),
    TYPED_ARRAY_UINT16(JSUint16Array.NAME, JSUint16Array::create),
    TYPED_ARRAY_UINT32(JSUint32Array.NAME, JSUint32Array::create),
    TYPED_ARRAY_UINT8(JSUint8Array.NAME, JSUint8Array::create),
    TYPED_ARRAY_UINT8_CLAMPED(JSUint8ClampedArray.NAME, JSUint8ClampedArray::create),
    URI_ERROR(JSURIError.NAME, JSURIError::create),
    WEAK_MAP(JSWeakMap.NAME, JSWeakMap::create),
    WEAK_REF(JSWeakRef.NAME, JSWeakRef::create),
    WEAK_SET(JSWeakSet.NAME, JSWeakSet::create);

    private final IJSConstructor constructor;

    private final String defaultPrototypeName;

    JSConstructorType(String defaultPrototypeName, IJSConstructor constructor) {
        this.defaultPrototypeName = defaultPrototypeName;
        this.constructor = constructor;
    }

    public JSValue create(JSContext context, JSValue... args) {
        if (constructor == null) {
            throw new JSException(context.throwTypeError("Constructor for " + this.name() + " is not implemented"));
        }
        return constructor.construct(context, args);
    }

    String defaultPrototypeName() {
        return defaultPrototypeName;
    }
}
