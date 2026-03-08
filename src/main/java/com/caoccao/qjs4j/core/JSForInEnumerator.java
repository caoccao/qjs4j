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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enumerator for for-in loops.
 * Iterates over enumerable properties of an object.
 */
public class JSForInEnumerator {
    private final List<String> keys;
    private int index;

    public JSForInEnumerator(JSValue obj) {
        this.keys = new ArrayList<>();
        this.index = 0;

        // Collect all enumerable property keys
        if (obj instanceof JSObject jsObj) {
            collectKeys(jsObj, new HashSet<>(), new HashSet<>());
        }
    }

    private void collectKeys(JSObject obj, Set<JSObject> visitedObjects, Set<String> seenPropertyNames) {
        if (!visitedObjects.add(obj)) {
            return;
        }

        // EnumerateObjectProperties uses [[OwnPropertyKeys]] and then [[GetOwnProperty]].
        // getOwnPropertyKeys() is the object-internal implementation point for exotics.
        List<PropertyKey> ownKeys = obj.getOwnPropertyKeys();
        for (PropertyKey key : ownKeys) {
            // For-in includes string keys (array index keys are emitted as strings), but not symbols.
            if (key.isSymbol()) {
                continue;
            }
            PropertyDescriptor descriptor = obj.getOwnPropertyDescriptor(key);
            if (descriptor == null || !descriptor.isEnumerable()) {
                continue;
            }
            String propertyName = key.toPropertyString();
            if (seenPropertyNames.add(propertyName)) {
                keys.add(propertyName);
            }
        }

        // Walk up the prototype chain
        JSObject prototype = obj.getPrototype();
        if (prototype != null) {
            collectKeys(prototype, visitedObjects, seenPropertyNames);
        }
    }

    /**
     * Get the next property key, or null if iteration is complete.
     */
    public JSValue next() {
        if (index >= keys.size()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(keys.get(index++));
    }
}
