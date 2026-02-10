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
 * Implementation of DisposableStack.prototype methods.
 */
public final class DisposableStackPrototype {
    private DisposableStackPrototype() {
    }

    public static JSValue adopt(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDisposableStack stack)) {
            return context.throwTypeError("DisposableStack.prototype.adopt called on non-DisposableStack");
        }
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue onDispose = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return stack.adopt(context, value, onDispose);
    }

    public static JSValue defer(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDisposableStack stack)) {
            return context.throwTypeError("DisposableStack.prototype.defer called on non-DisposableStack");
        }
        JSValue onDispose = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return stack.defer(context, onDispose);
    }

    public static JSValue dispose(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDisposableStack stack)) {
            return context.throwTypeError("DisposableStack.prototype.dispose called on non-DisposableStack");
        }
        return stack.dispose(context);
    }

    public static JSValue getDisposed(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDisposableStack stack)) {
            return context.throwTypeError("get DisposableStack.prototype.disposed called on non-DisposableStack");
        }
        return JSBoolean.valueOf(stack.isDisposed());
    }

    public static JSValue move(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDisposableStack stack)) {
            return context.throwTypeError("DisposableStack.prototype.move called on non-DisposableStack");
        }
        return stack.move(context);
    }

    public static JSValue use(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDisposableStack stack)) {
            return context.throwTypeError("DisposableStack.prototype.use called on non-DisposableStack");
        }
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return stack.use(context, value);
    }
}
