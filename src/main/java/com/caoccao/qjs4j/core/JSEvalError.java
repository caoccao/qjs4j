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

/**
 * Represents a JavaScript EvalError object.
 */
public final class JSEvalError extends JSError {

    public static final String NAME = "EvalError";

    /**
     * Create an EvalError with an empty message.
     */
    public JSEvalError(JSContext context) {
        this(context, "");
    }

    /**
     * Create an EvalError with a message.
     */
    public JSEvalError(JSContext context, String message) {
        super(context, NAME, message);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        String message = "";
        if (args.length > 0 && !args[0].isUndefined()) {
            message = JSTypeConversions.toString(context, args[0]).value();
        }
        JSObject jsObject = new JSEvalError(context, message);
        context.getGlobalObject().get(NAME).asObject().ifPresent(jsObject::transferPrototypeFrom);
        return jsObject;
    }
}
