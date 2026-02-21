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

public enum JSErrorType {
    Error(JSError::createPrototype),
    AggregateError(JSAggregateError::createPrototype),
    EvalError(JSEvalError::createPrototype),
    RangeError(JSRangeError::createPrototype),
    ReferenceError(JSReferenceError::createPrototype),
    SuppressedError(JSSuppressedError::createPrototype),
    SyntaxError(JSSyntaxError::createPrototype),
    TypeError(JSTypeError::createPrototype),
    URIError(JSURIError::createPrototype),
    ;

    private final IJSConstructor prototypeConstructor;

    JSErrorType(IJSConstructor prototypeConstructor) {
        this.prototypeConstructor = prototypeConstructor;
    }

    public JSObject create(JSContext context, JSValue... args) {
        if (prototypeConstructor == null) {
            throw new JSException(context.throwTypeError("Constructor for " + this.name() + " is not implemented"));
        }
        return prototypeConstructor.construct(context, args);
    }
}
