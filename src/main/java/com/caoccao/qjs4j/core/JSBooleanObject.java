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
 * Represents a JavaScript Boolean object (wrapper) as opposed to a boolean primitive.
 * <p>
 * In JavaScript, there's a distinction between:
 * - Boolean primitives: {@code true}, {@code false}
 * - Boolean objects: {@code new Boolean(true)}, {@code new Boolean(false)}
 * <p>
 * This class represents the object form, which is necessary for use cases like {@link JSProxy Proxy},
 * since primitive boolean values cannot be used as Proxy targets. A primitive boolean value
 * is immutable and cannot have properties, so it cannot be wrapped by a Proxy. JSBooleanObject
 * provides an object wrapper that can be used with Proxy while maintaining the boolean value.
 * <p>
 * The wrapped boolean value is stored in the {@code [[PrimitiveValue]]} internal slot,
 * following the ECMAScript specification pattern for Boolean wrapper objects.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a boolean object for use with Proxy
 * JSBooleanObject boolObj = new JSBooleanObject(true);
 * JSProxy proxy = new JSProxy(boolObj, handler, context);
 * }</pre>
 *
 * @see <a href="https://tc39.es/ecma262/#sec-boolean-objects">ECMAScript Boolean Objects</a>
 * @see JSProxy
 * @see JSBoolean
 */
public final class JSBooleanObject extends JSObject {
    public static final String NAME = "Boolean";
    private final JSBoolean value;

    /**
     * Create a Boolean object wrapping the given boolean value.
     *
     * @param value the primitive boolean value to wrap
     */
    public JSBooleanObject(boolean value) {
        this(JSBoolean.valueOf(value));
    }

    /**
     * Create a Boolean object wrapping the given JSBoolean value.
     *
     * @param value the JSBoolean value to wrap
     */
    public JSBooleanObject(JSBoolean value) {
        super();
        this.value = value;
        this.setPrimitiveValue(value);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSBoolean boolValue = JSTypeConversions.toBoolean(value);
        JSObject jsObject = new JSBooleanObject(boolValue);
        context.getGlobalObject().get(NAME).asObject().ifPresent(jsObject::transferPrototypeFrom);
        return jsObject;
    }

    /**
     * Get the JSBoolean value wrapped by this Boolean object.
     *
     * @return the JSBoolean value
     */
    public JSBoolean getValue() {
        return value;
    }

    @Override
    public Object toJavaObject() {
        return value.value();
    }

    @Override
    public String toString() {
        return Boolean.toString(value.value());
    }
}
