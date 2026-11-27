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

/** Convert an ownKeys trap result in order, then verify it against the target's own properties. */
final class ProxyOwnKeys {
    private ProxyOwnKeys() {
    }

    static List<PropertyKey> read(JSContext context, JSObject target, JSValue result) {
        if (!(result instanceof JSObject object)) {
            return reject(context, "ownKeys trap result must be an object");
        }
        long length = JSTypeConversions.toLength(context, object.get(PropertyKey.LENGTH));
        if (context.hasPendingException()) {
            return new ArrayList<>();
        }
        if (length > Integer.MAX_VALUE) {
            return reject(context, "ownKeys trap result is too large");
        }
        List<PropertyKey> keys = new ArrayList<>((int) length);
        for (int i = 0; i < length; i++) {
            JSValue value = object.get(PropertyKey.fromIndex(i));
            if (value instanceof JSString string) {
                keys.add(PropertyKey.fromString(string.value()));
            } else if (value instanceof JSSymbol symbol) {
                keys.add(PropertyKey.fromSymbol(symbol));
            } else {
                return reject(context, "ownKeys trap result must contain only strings or symbols");
            }
        }

        // Duplicate detection follows all indexed Gets, including any getters after a repeated key.
        Set<PropertyKey> remaining = new HashSet<>(keys);
        if (remaining.size() != keys.size()) {
            return reject(context, "'ownKeys' on proxy: trap returned duplicate entries");
        }

        List<PropertyKey> targetKeys = target.getOwnPropertyKeys();
        boolean extensible = target.isExtensible();
        PropertyKey missingRequired = null;
        PropertyKey missingConfigurable = null;
        for (PropertyKey key : targetKeys) {
            PropertyDescriptor descriptor = target.getOwnPropertyDescriptor(key);
            if (remaining.remove(key)) {
                continue;
            }
            if (descriptor != null && !descriptor.isConfigurable()) {
                if (missingRequired == null) {
                    missingRequired = key;
                }
            } else if (!extensible && missingConfigurable == null) {
                missingConfigurable = key;
            }
        }
        PropertyKey missing = missingRequired == null ? missingConfigurable : missingRequired;
        if (missing != null) {
            return reject(context,
                    "'ownKeys' on proxy: trap result did not include '" + missing.toPropertyString() + "'");
        }
        if (!extensible && !remaining.isEmpty()) {
            return reject(context, "'ownKeys' on proxy: trap returned extra keys but proxy target is non-extensible");
        }
        return keys;
    }

    private static List<PropertyKey> reject(JSContext context, String message) {
        context.throwTypeError(message);
        return new ArrayList<>();
    }
}
