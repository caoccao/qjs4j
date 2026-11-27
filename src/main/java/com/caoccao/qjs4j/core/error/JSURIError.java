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

package com.caoccao.qjs4j.core.error;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.exceptions.JSErrorType;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Represents a JavaScript URIError object.
 */
public final class JSURIError extends JSError {

    public static final String NAME = "URIError";

    /**
     * Create a URIError with a message.
     */
    public JSURIError(JSContext context, String message) {
        this(context, message, null);
    }

    public JSURIError(JSContext context, String message, SourceLocation sourceLocation) {
        super(context, NAME, message, sourceLocation);
    }

    public static JSValue create(JSContext context, JSValue... args) {
        return createNativeError(context, args, JSContext::createJSURIError);
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        return JSErrorType.URIError.createPrototype(context);
    }
}
