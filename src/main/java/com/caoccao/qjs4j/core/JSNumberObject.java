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
 * Represents a JavaScript Number object (wrapper) as opposed to a number primitive.
 * <p>
 * In JavaScript, there's a distinction between:
 * - Number primitives: {@code 42}, {@code 3.14}, {@code NaN}, {@code Infinity}
 * - Number objects: {@code new Number(42)}, {@code new Number(3.14)}
 * <p>
 * This class represents the object form, which is necessary for use cases like {@link JSProxy Proxy},
 * since primitive number values cannot be used as Proxy targets. A primitive number value
 * is immutable and cannot have properties, so it cannot be wrapped by a Proxy. JSNumberObject
 * provides an object wrapper that can be used with Proxy while maintaining the number value.
 * <p>
 * The wrapped number value is stored in the {@code [[PrimitiveValue]]} internal slot,
 * following the ECMAScript specification pattern for Number wrapper objects.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a number object for use with Proxy
 * JSNumberObject numObj = new JSNumberObject(42);
 * JSProxy proxy = new JSProxy(numObj, handler, context);
 * }</pre>
 *
 * @see <a href="https://tc39.es/ecma262/#sec-number-objects">ECMAScript Number Objects</a>
 * @see JSProxy
 * @see JSNumber
 */
public final class JSNumberObject extends JSObject {
    public static final String NAME = "Number";
    private final JSNumber value;

    /**
     * Create a Number object wrapping the given number value.
     *
     * @param value the primitive number value to wrap
     */
    public JSNumberObject(double value) {
        this(new JSNumber(value));
    }

    /**
     * Create a Number object wrapping the given JSNumber value.
     *
     * @param value the JSNumber value to wrap
     */
    public JSNumberObject(JSNumber value) {
        super();
        this.value = value;
        this.setPrimitiveValue(value);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSNumber numValue;
        if (args.length == 0) {
            numValue = new JSNumber(0.0);
        } else {
            numValue = JSTypeConversions.toNumber(context, args[0]);
        }
        JSObject jsObject = new JSNumberObject(numValue);
        context.getGlobalObject().get(NAME).asObject().ifPresent(jsObject::transferPrototypeFrom);
        return jsObject;
    }

    /**
     * Get the JSNumber value wrapped by this Number object.
     *
     * @return the JSNumber value
     */
    public JSNumber getValue() {
        return value;
    }

    @Override
    public Object toJavaObject() {
        return value.value();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
