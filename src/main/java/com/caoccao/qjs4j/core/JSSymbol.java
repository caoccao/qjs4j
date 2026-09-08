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
 * Represents a JavaScript Symbol value. Includes well-known symbols.
 */
public final class JSSymbol implements JSValue {
    public static final JSSymbol ASYNC_DISPOSE = new JSSymbol("Symbol.asyncDispose", JSSymbol.WELL_KNOWN_ID_START + 14);
    public static final JSSymbol ASYNC_ITERATOR = new JSSymbol("Symbol.asyncIterator",
            JSSymbol.WELL_KNOWN_ID_START + 1);
    public static final JSSymbol DISPOSE = new JSSymbol("Symbol.dispose", JSSymbol.WELL_KNOWN_ID_START + 13);
    public static final JSSymbol HAS_INSTANCE = new JSSymbol("Symbol.hasInstance", JSSymbol.WELL_KNOWN_ID_START + 3);
    public static final JSSymbol IS_CONCAT_SPREADABLE = new JSSymbol("Symbol.isConcatSpreadable",
            JSSymbol.WELL_KNOWN_ID_START + 4);
    // Well-known symbols (ES2015+)
    public static final JSSymbol ITERATOR = new JSSymbol("Symbol.iterator", JSSymbol.WELL_KNOWN_ID_START);
    public static final JSSymbol MATCH = new JSSymbol("Symbol.match", JSSymbol.WELL_KNOWN_ID_START + 6);
    public static final JSSymbol MATCH_ALL = new JSSymbol("Symbol.matchAll", JSSymbol.WELL_KNOWN_ID_START + 7);
    public static final String NAME = "Symbol";
    private static final AtomicInteger nextId = new AtomicInteger(0);
    public static final JSSymbol REPLACE = new JSSymbol("Symbol.replace", JSSymbol.WELL_KNOWN_ID_START + 8);
    public static final JSSymbol SEARCH = new JSSymbol("Symbol.search", JSSymbol.WELL_KNOWN_ID_START + 9);
    public static final JSSymbol SPECIES = new JSSymbol("Symbol.species", JSSymbol.WELL_KNOWN_ID_START + 11);
    public static final JSSymbol SPLIT = new JSSymbol("Symbol.split", JSSymbol.WELL_KNOWN_ID_START + 10);
    public static final JSSymbol TO_PRIMITIVE = new JSSymbol("Symbol.toPrimitive", JSSymbol.WELL_KNOWN_ID_START + 5);
    public static final JSSymbol TO_STRING_TAG = new JSSymbol("Symbol.toStringTag", JSSymbol.WELL_KNOWN_ID_START + 2);
    public static final JSSymbol UNSCOPABLES = new JSSymbol("Symbol.unscopables", JSSymbol.WELL_KNOWN_ID_START + 12);
    private static final int WELL_KNOWN_ID_START = 1000;
    private final String description;
    private final int id;
    private final boolean registered;
    /**
     * {@code WeakMap}/{@code WeakSet} entries naming this symbol as their key. Unregistered symbols became legal
     * weak-collection keys in ES2023.
     *
     * @see JSWeakEntryTable
     */
    private JSWeakEntryTable weakEntryTable;

    public JSSymbol(String description) {
        this.id = nextId.getAndIncrement();
        this.description = description;
        this.registered = false;
    }

    public JSSymbol(String description, boolean registered) {
        this.id = nextId.getAndIncrement();
        this.description = description;
        this.registered = registered;
    }

    private JSSymbol(String description, int id) {
        this.id = id;
        this.description = description;
        this.registered = false;
    }

    public String getDescription() {
        return description;
    }

    public int getId() {
        return id;
    }

    public boolean isRegistered() {
        return registered;
    }

    @Override
    public String toJavaObject() {
        if (description == null) {
            return "Symbol()";
        }
        return "Symbol(" + description + ")";
    }

    @Override
    public String toString() {
        return toJavaObject();
    }

    public String toString(JSContext context) {
        throw new JSException(context.throwTypeError("Cannot convert a Symbol value to a string"));
    }

    @Override
    public JSValueType type() {
        return JSValueType.SYMBOL;
    }

    /**
     * The weak-collection entries naming this symbol as their key.
     *
     * @param create
     *            true to create the table when absent
     * @return the table, or {@code null} when absent and {@code create} is false
     * @see JSWeakEntryTable
     */
    JSWeakEntryTable weakEntries(boolean create) {
        if (weakEntryTable == null && create) {
            weakEntryTable = new JSWeakEntryTable();
        }
        return weakEntryTable;
    }

    /**
     * Get a well-known symbol by name.
     *
     * @param name
     *            The symbol name (without "Symbol." prefix)
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
            case "dispose" -> DISPOSE;
            case "asyncDispose" -> ASYNC_DISPOSE;
            default -> null;
        };
    }
}
