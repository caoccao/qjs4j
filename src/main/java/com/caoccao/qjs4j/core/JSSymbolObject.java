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
 * Represents a JavaScript Symbol object (wrapper) as opposed to a symbol primitive.
 * <p>
 * In JavaScript, there's a distinction between:
 * - Symbol primitives: {@code Symbol('foo')}, {@code Symbol.iterator}
 * - Symbol objects: {@code Object(Symbol('foo'))}, {@code Object(Symbol.iterator)}
 * <p>
 * This class represents the object form, which is necessary for use cases like {@link JSProxy Proxy},
 * since primitive symbol values cannot be used as Proxy targets. A primitive symbol value
 * is immutable and cannot have properties, so it cannot be wrapped by a Proxy. JSSymbolObject
 * provides an object wrapper that can be used with Proxy while maintaining the symbol value.
 * <p>
 * Note: In JavaScript, Symbol cannot be called with the new operator (it throws TypeError).
 * Symbol objects are created using {@code Object(symbolValue)}.
 * <p>
 * The wrapped symbol value is stored in the {@code [[PrimitiveValue]]} internal slot,
 * following the ECMAScript specification pattern for Symbol wrapper objects.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a symbol object for use with Proxy
 * JSSymbol sym = new JSSymbol("foo");
 * JSSymbolObject symObj = new JSSymbolObject(sym);
 * JSProxy proxy = new JSProxy(symObj, handler, context);
 *
 * // In JavaScript code:
 * // var symObj = Object(Symbol('foo'));
 * // var proxy = new Proxy(symObj, handler);
 * }</pre>
 *
 * @see <a href="https://tc39.es/ecma262/#sec-symbol-objects">ECMAScript Symbol Objects</a>
 * @see JSProxy
 * @see JSSymbol
 */
public final class JSSymbolObject extends JSObject {
    private final JSSymbol value;

    /**
     * Create a Symbol object wrapping the given string description.
     *
     * @param description the symbol description to wrap
     */
    public JSSymbolObject(String description) {
        this(new JSSymbol(description));
    }

    /**
     * Create a Symbol object wrapping the given JSSymbol value.
     *
     * @param value the JSSymbol value to wrap
     */
    public JSSymbolObject(JSSymbol value) {
        super();
        this.value = value;
        this.setPrimitiveValue(value);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return context.throwTypeError("Symbol is not a constructor");
    }

    /**
     * Get the JSSymbol value wrapped by this Symbol object.
     *
     * @return the JSSymbol value
     */
    public JSSymbol getValue() {
        return value;
    }

    @Override
    public Object toJavaObject() {
        return value.toJavaObject();
    }

    @Override
    public String toString() {
        // Symbol objects toString() returns "Symbol(description)"
        String desc = value.getDescription();
        if (desc == null) {
            return "Symbol()";
        }
        return "Symbol(" + desc + ")";
    }
}
