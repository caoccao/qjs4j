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

import java.util.IdentityHashMap;

/** Storage shared by WeakMap and WeakSet. Each collection owns its entries for immortal well-known symbols. */
public abstract class JSWeakCollection extends JSObject {
    /** The value held for a WeakSet membership. */
    protected static final JSValue PRESENT = JSBoolean.TRUE;
    private IdentityHashMap<JSSymbol, JSValue> wellKnownValues;

    protected JSWeakCollection(JSContext context) {
        super(context);
    }

    protected final boolean deleteEntry(JSValue key) {
        context.getRuntime().releaseDeadWeakCollectionEntries();
        if (key instanceof JSSymbol symbol && symbol.isWellKnown()) {
            return wellKnownValues != null && wellKnownValues.remove(symbol) != null;
        }
        JSWeakEntryTable entries = entriesOf(key, false);
        return entries != null && entries.remove(this);
    }

    protected final JSValue getEntry(JSValue key) {
        context.getRuntime().releaseDeadWeakCollectionEntries();
        if (key instanceof JSSymbol symbol && symbol.isWellKnown()) {
            return wellKnownValues == null ? null : wellKnownValues.get(symbol);
        }
        JSWeakEntryTable entries = entriesOf(key, false);
        return entries == null ? null : entries.get(this);
    }

    protected final boolean putEntry(JSValue key, JSValue value) {
        JSRuntime runtime = context.getRuntime();
        runtime.releaseDeadWeakCollectionEntries();
        if (key instanceof JSSymbol symbol && symbol.isWellKnown()) {
            if (wellKnownValues == null) {
                wellKnownValues = new IdentityHashMap<>(2);
            }
            wellKnownValues.put(symbol, value);
            return true;
        }
        JSWeakEntryTable entries = entriesOf(key, true);
        if (entries == null) {
            return false;
        }
        entries.put(this, value, runtime.weakCollectionOwners());
        return true;
    }

    private static JSWeakEntryTable entriesOf(JSValue key, boolean create) {
        if (key instanceof JSObject object) {
            return object.weakEntries(create);
        }
        return key instanceof JSSymbol symbol && !symbol.isRegistered() ? symbol.weakEntries(create) : null;
    }
}
