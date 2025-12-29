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
 * Implementation of Symbol.prototype methods.
 * Based on ES2020 Symbol specification.
 */
public final class SymbolPrototype {

    /**
     * Symbol.prototype.description
     * ES2020 19.4.3.1
     * Getter for the Symbol's description.
     */
    public static JSValue getDescription(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSSymbol symbol;

        if (thisArg instanceof JSSymbol sym) {
            symbol = sym;
        } else if (thisArg instanceof JSObject obj) {
            JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
            if (primitiveValue instanceof JSSymbol sym) {
                symbol = sym;
            } else {
                return JSUndefined.INSTANCE;
            }
        } else {
            return JSUndefined.INSTANCE;
        }

        String description = symbol.getDescription();
        return description != null ? new JSString(description) : JSUndefined.INSTANCE;
    }

    /**
     * Symbol.prototype[@@toPrimitive](hint)
     * ES2020 19.4.3.4
     * Returns the primitive value.
     */
    public static JSValue toPrimitive(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return valueOf(ctx, thisArg, args);
    }

    /**
     * Symbol.prototype.toString()
     * ES2020 19.4.3.2
     * Returns a string representation of the Symbol.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSSymbol symbol;

        if (thisArg instanceof JSSymbol sym) {
            symbol = sym;
        } else if (thisArg instanceof JSObject obj) {
            JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
            if (primitiveValue instanceof JSSymbol sym) {
                symbol = sym;
            } else {
                return context.throwTypeError("Symbol.prototype.toString requires that 'this' be a Symbol");
            }
        } else {
            return context.throwTypeError("Symbol.prototype.toString requires that 'this' be a Symbol");
        }

        String description = symbol.getDescription();
        if (description != null) {
            return new JSString("Symbol(" + description + ")");
        } else {
            return new JSString("Symbol()");
        }
    }

    /**
     * Symbol.prototype[@@toStringTag]
     * ES2020 19.4.3.5
     * Returns "Symbol".
     */
    public static JSValue toStringTag(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return new JSString("Symbol");
    }

    /**
     * Symbol.prototype.valueOf()
     * ES2020 19.4.3.3
     * Returns the primitive value of the Symbol.
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSSymbol symbol) {
            return symbol;
        } else if (thisArg instanceof JSObject obj) {
            JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
            if (primitiveValue instanceof JSSymbol symbol) {
                return symbol;
            }
        }

        return context.throwTypeError("Symbol.prototype.valueOf called on non-Symbol");
    }
}
