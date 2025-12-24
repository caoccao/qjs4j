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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a JavaScript Symbol value.
 * Includes well-known symbols.
 */
public final class JSSymbol implements JSValue {
    private static final AtomicInteger nextId = new AtomicInteger(0);
    private static final int WELL_KNOWN_ID_START = 1000;

    // Well-known symbols
    public static final JSSymbol ITERATOR = new JSSymbol("Symbol.iterator", WELL_KNOWN_ID_START);
    public static final JSSymbol TO_STRING_TAG = new JSSymbol("Symbol.toStringTag", WELL_KNOWN_ID_START + 1);
    public static final JSSymbol HAS_INSTANCE = new JSSymbol("Symbol.hasInstance", WELL_KNOWN_ID_START + 2);
    public static final JSSymbol IS_CONCAT_SPREADABLE = new JSSymbol("Symbol.isConcatSpreadable", WELL_KNOWN_ID_START + 3);
    public static final JSSymbol TO_PRIMITIVE = new JSSymbol("Symbol.toPrimitive", WELL_KNOWN_ID_START + 4);

    private final int id;
    private final String description;

    public JSSymbol(String description) {
        this.id = nextId.getAndIncrement();
        this.description = description;
    }

    private JSSymbol(String description, int id) {
        this.id = id;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public JSValueType type() {
        return JSValueType.SYMBOL;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }
}
