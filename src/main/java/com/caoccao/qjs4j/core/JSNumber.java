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

import com.caoccao.qjs4j.utils.DtoaConverter;

/**
 * Represents a JavaScript number value (IEEE 754 double-precision).
 */
public record JSNumber(double value) implements JSValue {
    public static final String NAME = "number";

    // Cache for small integers [-1, 256] to reduce allocation in hot loops
    private static final int CACHE_LOW = -1;
    private static final int CACHE_HIGH = 256;
    private static final JSNumber[] CACHE = new JSNumber[CACHE_HIGH - CACHE_LOW + 1];

    static {
        for (int i = CACHE_LOW; i <= CACHE_HIGH; i++) {
            CACHE[i - CACHE_LOW] = new JSNumber(i);
        }
    }

    /**
     * Returns a JSNumber for the given double value.
     * Uses a cache for small integer values to reduce allocation.
     */
    public static JSNumber of(double value) {
        int intVal = (int) value;
        if (intVal == value && intVal >= CACHE_LOW && intVal <= CACHE_HIGH) {
            // Exclude -0.0 which must remain distinct from +0.0
            if (intVal == 0 && Double.doubleToRawLongBits(value) != 0L) {
                return new JSNumber(value);
            }
            return CACHE[intVal - CACHE_LOW];
        }
        return new JSNumber(value);
    }

    /**
     * Returns a JSNumber for the given int value.
     * Uses a cache for small integer values to reduce allocation.
     */
    public static JSNumber of(int value) {
        if (value >= CACHE_LOW && value <= CACHE_HIGH) {
            return CACHE[value - CACHE_LOW];
        }
        return new JSNumber(value);
    }

    /**
     * Returns a JSNumber for the given long value.
     * Uses a cache for small integer values to reduce allocation.
     */
    public static JSNumber of(long value) {
        if (value >= CACHE_LOW && value <= CACHE_HIGH) {
            return CACHE[(int) value - CACHE_LOW];
        }
        return new JSNumber(value);
    }

    @Override
    public Object toJavaObject() {
        return value;
    }

    @Override
    public String toString() {
        return DtoaConverter.convert(value);
    }

    @Override
    public JSValueType type() {
        return JSValueType.NUMBER;
    }
}
