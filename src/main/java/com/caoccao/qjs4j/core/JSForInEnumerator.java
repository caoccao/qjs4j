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
    private final JSObject rootObject;
    private int index;

    public JSForInEnumerator(JSValue obj) {
        this.keys = new ArrayList<>();
        this.index = 0;
        this.rootObject = obj instanceof JSObject jsObject ? jsObject : null;

        // Collect all enumerable property keys
        if (rootObject != null) {
            collectKeys(rootObject, new HashSet<>(), new HashSet<>());
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
            String propertyName = key.toPropertyString();
            if (!seenPropertyNames.add(propertyName)) {
                continue;
            }
            if (descriptor != null && descriptor.isEnumerable()) {
                keys.add(propertyName);
            }
        }

        // Walk up the prototype chain
        JSObject prototype = obj.getPrototype();
        if (prototype != null) {
            collectKeys(prototype, visitedObjects, seenPropertyNames);
        }
    }

    private boolean isCurrentlyEnumerable(JSObject obj, PropertyKey key) {
        JSObject currentObject = obj;
        while (currentObject != null) {
            PropertyDescriptor descriptor = currentObject.getOwnPropertyDescriptor(key);
            if (descriptor != null) {
                return descriptor.isEnumerable();
            }
            currentObject = currentObject.getPrototype();
        }
        return false;
    }

    /**
     * Get the next property key, or null if iteration is complete.
     */
    public JSValue next() {
        while (index < keys.size()) {
            String key = keys.get(index++);
            if (rootObject == null || isCurrentlyEnumerable(rootObject, PropertyKey.fromString(key))) {
                return new JSString(key);
            }
        }
        return JSUndefined.INSTANCE;
    }
}
