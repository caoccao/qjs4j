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
import com.caoccao.qjs4j.core.disposable.JSDisposableStack;

/**
 * Implementation of DisposableStack.prototype methods.
 */
public final class DisposableStackPrototype {
    private DisposableStackPrototype() {
    }

    public static JSValue adopt(JSContext context, JSValue thisArg, JSValue[] args) {
        return DisposableStackMethods.SYNC.adopt(context, thisArg, args);
    }

    public static JSValue defer(JSContext context, JSValue thisArg, JSValue[] args) {
        return DisposableStackMethods.SYNC.defer(context, thisArg, args);
    }

    public static JSValue dispose(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDisposableStack stack)) {
            return context.throwTypeError("DisposableStack.prototype.dispose called on non-DisposableStack");
        }
        JSValue completionError = args.length > 0 && !args[0].isUndefined() ? args[0] : null;
        return stack.dispose(context, completionError);
    }

    public static JSValue getDisposed(JSContext context, JSValue thisArg, JSValue[] args) {
        return DisposableStackMethods.SYNC.getDisposed(context, thisArg);
    }

    public static JSValue move(JSContext context, JSValue thisArg, JSValue[] args) {
        return DisposableStackMethods.SYNC.move(context, thisArg);
    }

    public static JSValue use(JSContext context, JSValue thisArg, JSValue[] args) {
        return DisposableStackMethods.SYNC.use(context, thisArg, args);
    }
}
