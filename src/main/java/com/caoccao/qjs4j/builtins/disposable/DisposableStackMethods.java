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

package com.caoccao.qjs4j.builtins.disposable;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.disposable.*;

/** Native methods shared by both stack prototypes, with distinct receiver brands. */
enum DisposableStackMethods {
    ASYNC(JSAsyncDisposableStack.class, JSAsyncDisposableStack.NAME),
    SYNC(JSDisposableStack.class, JSDisposableStack.NAME);

    private final String name;
    private final Class<? extends JSDisposableStackBase> type;

    DisposableStackMethods(Class<? extends JSDisposableStackBase> type, String name) {
        this.type = type;
        this.name = name;
    }

    JSValue adopt(JSContext context, JSValue receiver, JSValue[] args) {
        JSDisposableStackBase stack = require(context, receiver, "adopt");
        return stack == null
                ? context.getPendingException()
                : stack.adopt(context, JSValue.argumentOrUndefined(args, 0), JSValue.argumentOrUndefined(args, 1));
    }

    JSValue defer(JSContext context, JSValue receiver, JSValue[] args) {
        JSDisposableStackBase stack = require(context, receiver, "defer");
        return stack == null
                ? context.getPendingException()
                : stack.defer(context, JSValue.argumentOrUndefined(args, 0));
    }

    JSValue getDisposed(JSContext context, JSValue receiver) {
        JSDisposableStackBase stack = require(context, receiver, "disposed");
        return stack == null ? context.getPendingException() : JSBoolean.valueOf(stack.isDisposed());
    }

    JSValue move(JSContext context, JSValue receiver) {
        JSDisposableStackBase stack = require(context, receiver, "move");
        return stack == null ? context.getPendingException() : stack.move(context);
    }

    private JSDisposableStackBase require(JSContext context, JSValue value, String method) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        String prefix = "disposed".equals(method) ? "get " : "";
        context.throwTypeError(prefix + name + ".prototype." + method + " called on non-" + name);
        return null;
    }

    JSValue use(JSContext context, JSValue receiver, JSValue[] args) {
        JSDisposableStackBase stack = require(context, receiver, "use");
        return stack == null ? context.getPendingException() : stack.use(context, JSValue.argumentOrUndefined(args, 0));
    }
}
