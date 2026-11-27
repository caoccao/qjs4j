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

        void method(String name, int length, JSNativeCallback callback) {
            entries.add(new Entry(PropertyKey.fromString(name), name, length, callback, false));
        }
    }

    private record Entry(PropertyKey key, String name, int length, JSNativeCallback callback, boolean getter) {
    }
}
