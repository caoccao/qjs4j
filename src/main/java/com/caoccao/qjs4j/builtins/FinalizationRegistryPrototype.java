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
 * FinalizationRegistry.prototype methods.
 * Based on QuickJS js_finrec_register and js_finrec_unregister.
 */
public final class FinalizationRegistryPrototype {
    private FinalizationRegistryPrototype() {
    }

    /**
     * FinalizationRegistry.prototype.register(target, heldValue [, unregisterToken])
     */
    public static JSValue register(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSFinalizationRegistry registry)) {
            return context.throwTypeError("FinalizationRegistry value expected");
        }
        JSValue target = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue heldValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue token = args.length > 2 ? args[2] : JSUndefined.INSTANCE;

        if (!(target instanceof JSObject targetObj)) {
            return context.throwTypeError("invalid target");
        }
        if (target == heldValue) {
            return context.throwTypeError("held value cannot be the target");
        }
        if (!(token instanceof JSUndefined) && !(token instanceof JSObject) && !(token instanceof JSSymbol)) {
            return context.throwTypeError("invalid unregister token");
        }

        JSValue tokenToStore = token instanceof JSUndefined ? null : token;
        registry.register(targetObj, heldValue, tokenToStore);
        return JSUndefined.INSTANCE;
    }

    /**
     * FinalizationRegistry.prototype.unregister(unregisterToken)
     */
    public static JSValue unregister(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSFinalizationRegistry registry)) {
            return context.throwTypeError("FinalizationRegistry value expected");
        }
        JSValue token = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(token instanceof JSObject) && !(token instanceof JSSymbol)) {
            return context.throwTypeError("invalid unregister token");
        }
        return JSBoolean.valueOf(registry.unregister(token));
    }
}
