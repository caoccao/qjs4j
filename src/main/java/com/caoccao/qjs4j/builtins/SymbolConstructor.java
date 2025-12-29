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

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of Symbol constructor and static methods.
 * Based on ES2020 Symbol specification.
 */
public final class SymbolConstructor {
    private static final Map<String, JSSymbol> symbolRegistry = new HashMap<>();

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
     * Symbol.hasInstance
     * ES2020 19.4.2.3
     */
    public static JSValue getHasInstance(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return JSSymbol.HAS_INSTANCE;
    }

    /**
     * Symbol.isConcatSpreadable
     * ES2020 19.4.2.5
     */
    public static JSValue getIsConcatSpreadable(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return JSSymbol.IS_CONCAT_SPREADABLE;
    }

    /**
     * Symbol.iterator
     * ES2020 19.4.2.4
     */
    public static JSValue getIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return JSSymbol.ITERATOR;
    }

    /**
     * Symbol.toPrimitive
     * ES2020 19.4.2.13
     */
    public static JSValue getToPrimitive(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return JSSymbol.TO_PRIMITIVE;
    }

    /**
     * Symbol.toStringTag
     * ES2020 19.4.2.14
     */
    public static JSValue getToStringTag(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return JSSymbol.TO_STRING_TAG;
    }

    /**
     * Symbol.keyFor(sym)
     * ES2020 19.4.2.6
     * Returns the key for a Symbol from the global symbol registry.
     */
    public static JSValue keyFor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Symbol.keyFor requires a Symbol");
        }

        JSValue arg = args[0];
        if (!(arg instanceof JSSymbol symbol)) {
            return context.throwTypeError("Symbol.keyFor requires a Symbol");
        }

        // Search the registry for this symbol
        synchronized (symbolRegistry) {
            for (Map.Entry<String, JSSymbol> entry : symbolRegistry.entrySet()) {
                if (entry.getValue() == symbol) {
                    return new JSString(entry.getKey());
                }
            }
        }

        // Symbol not in registry
        return JSUndefined.INSTANCE;
    }

    /**
     * Symbol.for(key)
     * ES2020 19.4.2.1
     * Returns a Symbol from the global symbol registry.
     */
    public static JSValue symbolFor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Symbol.for requires a key");
        }

        String key = JSTypeConversions.toString(context, args[0]).value();

        synchronized (symbolRegistry) {
            return symbolRegistry.computeIfAbsent(key, k -> new JSSymbol(k));
        }
    }
}
