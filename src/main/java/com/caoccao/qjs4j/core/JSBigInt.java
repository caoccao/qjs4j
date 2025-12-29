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
 * Represents a JavaScript BigInt value.
 * Uses Java's BigInteger for implementation.
 */
public record JSBigInt(BigInteger value) implements JSValue {

    public JSBigInt(String value) {
        this(new BigInteger(value));
    }

    public JSBigInt(long value) {
        this(BigInteger.valueOf(value));
    }

    // Arithmetic operations
    public JSBigInt add(JSBigInt other) {
        return new JSBigInt(value.add(other.value));
    }

    // Bitwise operations
    public JSBigInt and(JSBigInt other) {
        return new JSBigInt(value.and(other.value));
    }

    public JSBigInt divide(JSBigInt other) {
        return new JSBigInt(value.divide(other.value));
    }

    public JSBigInt multiply(JSBigInt other) {
        return new JSBigInt(value.multiply(other.value));
    }

    public JSBigInt not() {
        return new JSBigInt(value.not());
    }

    public JSBigInt or(JSBigInt other) {
        return new JSBigInt(value.or(other.value));
    }

    public JSBigInt power(JSBigInt exponent) {
        return new JSBigInt(value.pow(exponent.value.intValue()));
    }

    public JSBigInt remainder(JSBigInt other) {
        return new JSBigInt(value.remainder(other.value));
    }

    public JSBigInt shiftLeft(long bits) {
        return new JSBigInt(value.shiftLeft((int) bits));
    }

    public JSBigInt shiftRight(long bits) {
        return new JSBigInt(value.shiftRight((int) bits));
    }

    public JSBigInt subtract(JSBigInt other) {
        return new JSBigInt(value.subtract(other.value));
    }

    @Override
    public Object toJavaObject() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public JSValueType type() {
        return JSValueType.BIGINT;
    }

    public JSBigInt xor(JSBigInt other) {
        return new JSBigInt(value.xor(other.value));
    }
}
