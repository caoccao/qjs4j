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
 * Implementation of Symbol constructor and static methods.
 * Based on ES2020 Symbol specification.
 */
public final class SymbolConstructor {
    /**
     * Symbol(description)
     * ES2020 19.4.1
     * Creates a new unique Symbol value.
     * Note: Symbol cannot be called with new operator.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        String description = null;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            description = JSTypeConversions.toString(context, args[0]).value();
        }
        return new JSSymbol(description);
    }

    /**
     * Symbol.asyncDispose
     */
    public static JSValue getAsyncDispose(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSymbol.ASYNC_DISPOSE;
    }

    /**
     * Symbol.dispose
     */
    public static JSValue getDispose(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSymbol.DISPOSE;
    }

    /**
     * Symbol.hasInstance
     * ES2020 19.4.2.3
     */
    public static JSValue getHasInstance(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSymbol.HAS_INSTANCE;
    }

    /**
     * Symbol.isConcatSpreadable
     * ES2020 19.4.2.5
     */
    public static JSValue getIsConcatSpreadable(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSymbol.IS_CONCAT_SPREADABLE;
    }

    /**
     * Symbol.iterator
     * ES2020 19.4.2.4
     */
    public static JSValue getIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSymbol.ITERATOR;
    }

    /**
     * Symbol.toPrimitive
     * ES2020 19.4.2.13
     */
    public static JSValue getToPrimitive(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSymbol.TO_PRIMITIVE;
    }

    /**
     * Symbol.toStringTag
     * ES2020 19.4.2.14
     */
    public static JSValue getToStringTag(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSymbol.TO_STRING_TAG;
    }

    /**
     * Symbol.keyFor(sym)
     * ES2020 19.4.2.6
     * Returns the key for a Symbol from the global symbol registry.
     */
    public static JSValue keyFor(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(arg instanceof JSSymbol symbol)) {
            return context.throwTypeError("Symbol.keyFor requires a Symbol");
        }

        String key = context.getRuntime().getGlobalSymbolKey(symbol);
        if (key != null) {
            return new JSString(key);
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * Symbol.for(key)
     * ES2020 19.4.2.1
     * Returns a Symbol from the global symbol registry.
     */
    public static JSValue symbolFor(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue keyValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String key = JSTypeConversions.toString(context, keyValue).value();
        return context.getRuntime().getOrCreateGlobalSymbol(key);
    }
}
