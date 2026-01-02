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

import java.math.BigInteger;

/**
 * Represents a JavaScript BigInt object (wrapper) as opposed to a BigInt primitive.
 * <p>
 * In JavaScript, there's a distinction between:
 * - BigInt primitives: {@code 42n}, {@code BigInt(123)}, {@code 9007199254740991n}
 * - BigInt objects: {@code Object(42n)}, {@code Object(BigInt(123))}
 * <p>
 * Note: BigInt cannot be called with {@code new} operator - attempting {@code new BigInt(42)}
 * will throw a TypeError: "BigInt is not a constructor". Use {@code Object(BigInt(42))} instead.
 * <p>
 * This class represents the object form, which is necessary for use cases like {@link JSProxy Proxy},
 * since primitive BigInt values cannot be used as Proxy targets. A primitive BigInt value
 * is immutable and cannot have properties, so it cannot be wrapped by a Proxy. JSBigIntObject
 * provides an object wrapper that can be used with Proxy while maintaining the BigInt value.
 * <p>
 * The wrapped BigInt value is stored in the {@code [[PrimitiveValue]]} internal slot,
 * following the ECMAScript specification pattern for BigInt wrapper objects.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a BigInt object for use with Proxy
 * JSBigIntObject bigIntObj = new JSBigIntObject(BigInteger.valueOf(42));
 * JSProxy proxy = new JSProxy(bigIntObj, handler, context);
 * }</pre>
 *
 * @see <a href="https://tc39.es/ecma262/#sec-bigint-objects">ECMAScript BigInt Objects</a>
 * @see JSProxy
 * @see JSBigInt
 */
public final class JSBigIntObject extends JSObject {
    private final JSBigInt value;

    /**
     * Create a BigInt object wrapping the given BigInteger value.
     *
     * @param value the primitive BigInteger value to wrap
     */
    public JSBigIntObject(BigInteger value) {
        this(new JSBigInt(value));
    }

    /**
     * Create a BigInt object wrapping the given JSBigInt value.
     *
     * @param value the JSBigInt value to wrap
     */
    public JSBigIntObject(JSBigInt value) {
        super();
        this.value = value;
        this.setPrimitiveValue(value);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return context.throwTypeError("BigInt is not a constructor");
    }

    /**
     * Get the JSBigInt value wrapped by this BigInt object.
     *
     * @return the JSBigInt value
     */
    public JSBigInt getValue() {
        return value;
    }

    @Override
    public Object toJavaObject() {
        return value.value();
    }

    @Override
    public String toString() {
        return value.value().toString() + "n";
    }
}
