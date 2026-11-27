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

import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

/**
 * A JavaScript WeakSet. A collectible member holds its membership in a {@link JSWeakEntryTable}. The set holds its own
 * memberships for well-known symbols, as in {@link JSWeakMap}.
 */
public final class JSWeakSet extends JSWeakCollection {
    public static final String NAME = "WeakSet";

    /**
     * Create an empty WeakSet.
     */
    public JSWeakSet(JSContext context) {
        super(context);
    }

    @Override
    public String toString() {
        return "[object WeakSet]";
    }

    /**
     * Add a value to the WeakSet.
     * <p>
     * The value must be an object or an <em>unregistered</em> symbol, exactly as {@code WeakSet.prototype.add} requires
     * — see {@link #isWeakSetValue(JSValue)}.
     *
     * @param value
     *            the member to add
     * @throws JSTypeErrorException
     *             when the value cannot be held weakly
     */
    public void weakSetAdd(JSValue value) {
        if (!putEntry(value, JSWeakEntryTable.PRESENT)) {
            throw new JSTypeErrorException("Invalid WeakSet value type");
        }
    }

    /**
     * Delete a value from the WeakSet.
     */
    public boolean weakSetDelete(JSValue value) {
        return deleteEntry(value);
    }

    /**
     * Check if the WeakSet has a value.
     */
    public boolean weakSetHas(JSValue value) {
        return getEntry(value) != null;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSWeakSet weakSetObj = context.createJSWeakSet();
        CollectionInitializer.initializePrototypeFromNewTarget(context, weakSetObj, NAME);
        if (context.hasPendingException()) {
            return CollectionInitializer.returnAbruptResult(context, weakSetObj);
        }
        return CollectionInitializer.initializeFromIterable(context, weakSetObj, args, false);
    }

    /**
     * Whether a value may be held weakly as a member.
     * <p>
     * Objects and unregistered symbols can, per ES2024 CanBeHeldWeakly. A symbol from {@code Symbol.for} cannot: the
     * global registry keeps it alive for the realm's lifetime, so a membership keyed on one could never be collected.
     *
     * @param value
     *            the candidate member
     * @return true when the value can be held weakly
     */
    public static boolean isWeakSetValue(JSValue value) {
        return JSTypeChecking.canBeHeldWeakly(value);
    }
}
