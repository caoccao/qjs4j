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

import com.caoccao.qjs4j.exceptions.JSException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a JavaScript Symbol value.
 * Includes well-known symbols.
 */
public final class JSSymbol implements JSValue {
    private static final int WELL_KNOWN_ID_START = 1000;
    // Well-known symbols (ES2015+)
    public static final JSSymbol ITERATOR = new JSSymbol("Symbol.iterator", WELL_KNOWN_ID_START);
    public static final JSSymbol ASYNC_ITERATOR = new JSSymbol("Symbol.asyncIterator", WELL_KNOWN_ID_START + 1);
    public static final JSSymbol TO_STRING_TAG = new JSSymbol("Symbol.toStringTag", WELL_KNOWN_ID_START + 2);
    public static final JSSymbol HAS_INSTANCE = new JSSymbol("Symbol.hasInstance", WELL_KNOWN_ID_START + 3);
    public static final JSSymbol IS_CONCAT_SPREADABLE = new JSSymbol("Symbol.isConcatSpreadable", WELL_KNOWN_ID_START + 4);
    public static final JSSymbol TO_PRIMITIVE = new JSSymbol("Symbol.toPrimitive", WELL_KNOWN_ID_START + 5);
    public static final JSSymbol MATCH = new JSSymbol("Symbol.match", WELL_KNOWN_ID_START + 6);
    public static final JSSymbol MATCH_ALL = new JSSymbol("Symbol.matchAll", WELL_KNOWN_ID_START + 7);
    public static final JSSymbol REPLACE = new JSSymbol("Symbol.replace", WELL_KNOWN_ID_START + 8);
    public static final JSSymbol SEARCH = new JSSymbol("Symbol.search", WELL_KNOWN_ID_START + 9);
    public static final JSSymbol SPLIT = new JSSymbol("Symbol.split", WELL_KNOWN_ID_START + 10);
    public static final JSSymbol SPECIES = new JSSymbol("Symbol.species", WELL_KNOWN_ID_START + 11);
    public static final JSSymbol UNSCOPABLES = new JSSymbol("Symbol.unscopables", WELL_KNOWN_ID_START + 12);
    private static final AtomicInteger nextId = new AtomicInteger(0);
    private final String description;
    private final int id;

    public JSSymbol(String description) {
        this.id = nextId.getAndIncrement();
        this.description = description;
    }

    private JSSymbol(String description, int id) {
        this.id = id;
        this.description = description;
    }

    /**
     * Get a well-known symbol by name.
     *
     * @param name The symbol name (without "Symbol." prefix)
     * @return The well-known symbol, or null if not found
     */
    public static JSSymbol getWellKnownSymbol(String name) {
        return switch (name) {
            case "iterator" -> ITERATOR;
            case "asyncIterator" -> ASYNC_ITERATOR;
            case "toStringTag" -> TO_STRING_TAG;
            case "hasInstance" -> HAS_INSTANCE;
            case "isConcatSpreadable" -> IS_CONCAT_SPREADABLE;
            case "toPrimitive" -> TO_PRIMITIVE;
            case "match" -> MATCH;
            case "matchAll" -> MATCH_ALL;
            case "replace" -> REPLACE;
            case "search" -> SEARCH;
            case "split" -> SPLIT;
            case "species" -> SPECIES;
            case "unscopables" -> UNSCOPABLES;
            default -> null;
        };
    }

    public String getDescription() {
        return description;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toJavaObject() {
        if (description == null) {
            return "Symbol()";
        }
        return "Symbol(" + description + ")";
    }

    public String toString(JSContext context) {
        throw new JSException(context.throwTypeError("Cannot convert a Symbol value to a string"));
    }

    @Override
    public JSValueType type() {
        return JSValueType.SYMBOL;
    }
}
