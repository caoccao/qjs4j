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

import java.util.Optional;

/**
 * Base sealed interface for all JavaScript values.
 * Implements the value representation using sealed interfaces for type safety.
 */
public sealed interface JSValue permits
        JSUndefined, JSNull, JSBoolean, JSNumber, JSString,
        JSObject, JSSymbol, JSBigInt, JSFunction {

    /**
     * Attempt to cast this value to JSBigInt.
     * @return Optional containing the JSBigInt if this value is a BigInt, empty otherwise
     */
    default Optional<JSBigInt> asBigInt() {
        return this instanceof JSBigInt v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSBoolean.
     * @return Optional containing the JSBoolean if this value is a boolean, empty otherwise
     */
    default Optional<JSBoolean> asBoolean() {
        return this instanceof JSBoolean v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSFunction.
     * @return Optional containing the JSFunction if this value is a function, empty otherwise
     */
    default Optional<JSFunction> asFunction() {
        return this instanceof JSFunction v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSNull.
     * @return Optional containing the JSNull if this value is null, empty otherwise
     */
    default Optional<JSNull> asNull() {
        return this instanceof JSNull v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSNumber.
     * @return Optional containing the JSNumber if this value is a number, empty otherwise
     */
    default Optional<JSNumber> asNumber() {
        return this instanceof JSNumber v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSObject.
     * @return Optional containing the JSObject if this value is an object, empty otherwise
     */
    default Optional<JSObject> asObject() {
        return this instanceof JSObject v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSString.
     * @return Optional containing the JSString if this value is a string, empty otherwise
     */
    default Optional<JSString> asString() {
        return this instanceof JSString v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSSymbol.
     * @return Optional containing the JSSymbol if this value is a symbol, empty otherwise
     */
    default Optional<JSSymbol> asSymbol() {
        return this instanceof JSSymbol v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Attempt to cast this value to JSUndefined.
     * @return Optional containing the JSUndefined if this value is undefined, empty otherwise
     */
    default Optional<JSUndefined> asUndefined() {
        return this instanceof JSUndefined v ? Optional.of(v) : Optional.empty();
    }

    /**
     * Convert to Java object representation.
     */
    Object toJavaObject();

    /**
     * Get the type of this value.
     */
    JSValueType type();
}
