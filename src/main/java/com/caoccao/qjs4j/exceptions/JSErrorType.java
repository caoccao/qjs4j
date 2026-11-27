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

package com.caoccao.qjs4j.exceptions;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.error.*;

public enum JSErrorType implements JSNativeCallback {
    AggregateError(JSConstructorType.AGGREGATE_ERROR, 2),
    Error(JSConstructorType.ERROR, 1),
    EvalError(JSConstructorType.EVAL_ERROR, 1),
    RangeError(JSConstructorType.RANGE_ERROR, 1),
    ReferenceError(JSConstructorType.REFERENCE_ERROR, 1),
    SuppressedError(JSConstructorType.SUPPRESSED_ERROR, 3),
    SyntaxError(JSConstructorType.SYNTAX_ERROR, 1),
    TypeError(JSConstructorType.TYPE_ERROR, 1),
    URIError(JSConstructorType.URI_ERROR, 1);

    private final JSConstructorType constructorType;
    private final int length;

    JSErrorType(JSConstructorType constructorType, int length) {
        this.constructorType = constructorType;
        this.length = length;
    }

    @Override
    public JSValue call(JSContext context, JSValue receiver, JSValue[] args) {
        return constructorType.create(context, args);
    }

    public JSValue create(JSContext context, JSValue... args) {
        JSObject constructor = createPrototype(context);
        if (constructor instanceof JSNativeFunction nativeFunction) {
            nativeFunction.setConstructorType(constructorType);
        }
        return constructor;
    }

    public JSObject createPrototype(JSContext context) {
        if (this == Error) {
            return JSError.createPrototype(context);
        }
        JSObject constructor = JSError.createNativeErrorPrototype(context, name(), length, this);
        if (this == AggregateError && context.getGlobalObject().get(JSError.NAME) instanceof JSObject parent) {
            constructor.setPrototype(parent);
        }
        return constructor;
    }
}
