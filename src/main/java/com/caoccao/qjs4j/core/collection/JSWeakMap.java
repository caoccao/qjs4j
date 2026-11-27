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

package com.caoccao.qjs4j.core.collection;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

/**
 * A JavaScript WeakMap. Collectible keys hold their entries in a weak entry table. Entries for well-known symbols live
 * on the collection so independent runtimes can use the same symbol concurrently.
 */
public final class JSWeakMap extends JSWeakCollection {
    public static final String NAME = "WeakMap";

    /**
     * Create an empty WeakMap.
     */
    public JSWeakMap(JSContext context) {
        super(context);
    }

    @Override
    public String toString() {
        return "[object WeakMap]";
    }

    /**
     * Delete a key from the WeakMap.
     */
    public boolean weakMapDelete(JSValue key) {
        return deleteEntry(key);
    }

    /**
     * Get a value from the WeakMap by key.
     */
    public JSValue weakMapGet(JSValue key) {
        JSValue value = getEntry(key);
        return value == null ? JSUndefined.INSTANCE : value;
    }

    /**
     * Check if the WeakMap has a key.
     */
    public boolean weakMapHas(JSValue key) {
        return getEntry(key) != null;
    }

    /**
     * Set a key-value pair in the WeakMap.
     * <p>
     * The key must be an object or an <em>unregistered</em> symbol, exactly as {@code WeakMap.prototype.set} requires —
     * see {@link #isWeakMapKey(JSValue)}. The value may be any value, including {@code undefined}.
     *
     * @param key
     *            the key
     * @param value
     *            the value
     * @throws JSTypeErrorException
     *             when the key cannot be held weakly
     */
    public void weakMapSet(JSValue key, JSValue value) {
        if (!putEntry(key, value == null ? JSUndefined.INSTANCE : value)) {
            throw new JSTypeErrorException("Invalid WeakMap key type");
        }
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSWeakMap weakMapObj = context.createJSWeakMap();
        CollectionInitializer.initializePrototypeFromNewTarget(context, weakMapObj, NAME);
        if (context.hasPendingException()) {
            return CollectionInitializer.returnAbruptResult(context, weakMapObj);
        }
        return CollectionInitializer.initializeFromIterable(context, weakMapObj, args, true);
    }

    /**
     * Whether a value may be held weakly as a key.
     * <p>
     * Objects and unregistered symbols can, per ES2024 CanBeHeldWeakly. A symbol from {@code Symbol.for} cannot: the
     * global registry keeps it alive for the realm's lifetime, so an entry keyed on one could never be collected.
     *
     * @param key
     *            the candidate key
     * @return true when the key can be held weakly
     */
    public static boolean isWeakMapKey(JSValue key) {
        return JSTypeChecking.canBeHeldWeakly(key);
    }
}
