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
 * Represents a JavaScript string value.
 * Supports atom indexing for interned strings.
 */
public record JSString(String value, int atomIndex) implements JSValue {
    /**
     * Maximum length of a JavaScript string, in UTF-16 code units.
     * <p>
     * Operations that would produce a longer string raise {@code RangeError: Invalid string length}
     * rather than exhausting the heap with an {@link OutOfMemoryError}.
     * <p>
     * The value is smaller than V8's {@code 2**29 - 24}. A Java {@code String} of that length is
     * ~1.1 GB of {@code char} data, and building it incrementally needs roughly twice that while
     * the backing array grows — so on any ordinary heap the JVM runs out of memory long before the
     * limit is reached, which is exactly the failure the limit exists to prevent. At
     * {@code 2**27 - 1} the limit is reachable on a 2 GB heap while staying far above any length a
     * realistic program produces.
     */
    public static final int MAX_LENGTH = (1 << 27) - 1;
    public static final String NAME = "String";

    public JSString(String value) {
        this(value, -1);
    }

    @Override
    public Object toJavaObject() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public JSValueType type() {
        return JSValueType.STRING;
    }
}
