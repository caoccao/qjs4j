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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ordered native property definitions. Metadata is shared, while functions and descriptors belong to the target realm.
 */
final class BuiltinFunctionList {
    private final Entry[] entries;

    BuiltinFunctionList(Consumer<Builder> define) {
        Builder builder = new Builder();
        define.accept(builder);
        entries = builder.entries.toArray(Entry[]::new);
    }

    void install(JSObject target) {
        JSContext context = target.getContext();
        for (Entry entry : entries) {
            JSNativeFunction function = new JSNativeFunction(context, entry.name, entry.length, entry.callback);
            if (entry.getter) {
                target.defineProperty(entry.key, function, PropertyDescriptor.AccessorState.Configurable);
            } else {
                target.defineProperty(entry.key, function, PropertyDescriptor.DataState.ConfigurableWritable);
            }
        }
    }

    static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        void getter(String name, JSNativeCallback callback) {
            entries.add(new Entry(PropertyKey.fromString(name), "get " + name, 0, callback, true));
        }

        void method(Method method, JSNativeCallback callback) {
            entries.add(new Entry(method.key, method.key.asString(), method.length, callback, false));
        }

        void method(String name, int length, JSNativeCallback callback) {
            entries.add(new Entry(PropertyKey.fromString(name), name, length, callback, false));
        }

        /** Merge getter groups into the lexicographic order specified for Temporal prototypes. */
        void sortedGetters(Consumer<Builder> define) {
            int first = entries.size();
            define.accept(this);
            entries.subList(first, entries.size()).sort(java.util.Comparator.comparing(entry -> entry.key.asString()));
        }
    }

    private record Entry(PropertyKey key, String name, int length, JSNativeCallback callback, boolean getter) {
    }

    /** Names and lengths shared by multiple intrinsic function lists. */
    enum Method {
        ADD("add", 1),
        AT("at", 1),
        CLEAR("clear", 0),
        COMPARE("compare", 2),
        COPY_WITHIN("copyWithin", 2),
        DEFINE_PROPERTY("defineProperty", 3),
        DELETE("delete", 1),
        EQUALS("equals", 1),
        EVERY("every", 1),
        FILL("fill", 1),
        FILTER("filter", 1),
        FIND("find", 1),
        FIND_INDEX("findIndex", 1),
        FIND_LAST("findLast", 1),
        FIND_LAST_INDEX("findLastIndex", 1),
        FLAT_MAP("flatMap", 1),
        FOR_EACH("forEach", 1),
        FORMAT_RANGE("formatRange", 2),
        FORMAT_RANGE_TO_PARTS("formatRangeToParts", 2),
        FROM("from", 1),
        GET_OR_INSERT("getOrInsert", 2),
        GET_OR_INSERT_COMPUTED("getOrInsertComputed", 2),
        GET_OWN_PROPERTY_DESCRIPTOR("getOwnPropertyDescriptor", 2),
        GET_PROTOTYPE_OF("getPrototypeOf", 1),
        INCLUDES("includes", 1),
        INDEX_OF("indexOf", 1),
        IS_EXTENSIBLE("isExtensible", 1),
        IS_FINITE("isFinite", 1),
        IS_NA_N("isNaN", 1),
        JOIN("join", 1),
        LAST_INDEX_OF("lastIndexOf", 1),
        MAP("map", 1),
        PREVENT_EXTENSIONS("preventExtensions", 1),
        REDUCE("reduce", 1),
        REDUCE_RIGHT("reduceRight", 1),
        RESOLVED_OPTIONS("resolvedOptions", 0),
        REVERSE("reverse", 0),
        ROUND("round", 1),
        SET_PROTOTYPE_OF("setPrototypeOf", 2),
        SINCE("since", 1),
        SLICE("slice", 2),
        SOME("some", 1),
        SORT("sort", 1),
        SUBTRACT("subtract", 1),
        TO_LOCALE_STRING("toLocaleString", 0),
        TO_PLAIN_DATE_TIME("toPlainDateTime", 0),
        TO_PLAIN_TIME("toPlainTime", 0),
        TO_REVERSED("toReversed", 0),
        TO_SORTED("toSorted", 1),
        TO_ZONED_DATE_TIME("toZonedDateTime", 1),
        UNTIL("until", 1),
        VALUE_OF("valueOf", 0),
        WITH("with", 1),
        WITH_CALENDAR("withCalendar", 1),
        WITH_PLAIN_TIME("withPlainTime", 0);

        private final PropertyKey key;
        private final int length;

        Method(String name, int length) {
            this.key = PropertyKey.fromString(name);
            this.length = length;
        }
    }
}
