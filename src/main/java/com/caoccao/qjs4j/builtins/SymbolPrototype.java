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

import java.util.Optional;

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
    public static JSValue getDescription(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asSymbolWithDownCast()
                .map(JSSymbol::getDescription)
                .map(description -> (JSValue) new JSString(description))
                .orElse(JSUndefined.INSTANCE);
    }

    /**
     * Symbol.prototype[@@toPrimitive](hint)
     * ES2020 19.4.3.4
     * Returns the primitive value.
     */
    public static JSValue toPrimitive(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asSymbolWithDownCast()
                .map(jsSymbol -> (JSValue) jsSymbol)
                .orElseGet(() -> context.throwTypeError("Symbol.prototype [ @@toPrimitive ] requires that 'this' be a Symbol"));
    }

    /**
     * Symbol.prototype.toString()
     * ES2020 19.4.3.2
     * Returns a string representation of the Symbol.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asSymbolWithDownCast()
                .map(jsSymbol -> Optional.ofNullable(jsSymbol.getDescription()))
                .map(optionalDescription -> (JSValue) new JSString(optionalDescription
                        .map(description -> "Symbol(" + description + ")")
                        .orElse("Symbol()")))
                .orElseGet(() -> context.throwTypeError("Symbol.prototype.toString requires that 'this' be a Symbol"));
    }

    /**
     * Symbol.prototype[@@toStringTag]
     * ES2020 19.4.3.5
     * Returns "Symbol".
     */
    public static JSValue toStringTag(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSString(JSSymbol.NAME);
    }

    /**
     * Symbol.prototype.valueOf()
     * ES2020 19.4.3.3
     * Returns the primitive value of the Symbol.
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asSymbolWithDownCast()
                .map(jsSymbol -> (JSValue) jsSymbol)
                .orElseGet(() -> context.throwTypeError("Symbol.prototype.valueOf requires that 'this' be a Symbol"));
    }
}
