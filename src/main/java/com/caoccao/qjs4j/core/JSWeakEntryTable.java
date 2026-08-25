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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code WeakMap}/{@code WeakSet} entries that name one key, stored <em>on that key</em>.
 * <p>
 * <strong>Why the storage is inverted.</strong> A {@code WeakHashMap} holds its values strongly,
 * so {@code wm.set(key, {backReference: key})} makes the key reachable as map → value → key and it
 * is never cleared. ECMAScript requires the opposite: a value reachable only through its entry must
 * not keep that entry's key alive. Java has no ephemeron, but it does not need one if the entry
 * lives on the key. Then the reachability path is key → value, so:
 * <ul>
 * <li>a collection holds no reference to any key or value, and clears entirely when its keys go;</li>
 * <li>a value that refers back to its key forms a self-contained cycle, which the collector
 * reclaims as a unit;</li>
 * <li>lookup compares the owning collection by identity, because {@code ==} is the only comparison
 * ECMAScript object keys have — a {@code WeakHashMap} calls {@code equals}, which any subclass can
 * override, and did.</li>
 * </ul>
 * The owning collection is held weakly so that a key outliving a collection does not pin it; dead
 * entries are pruned whenever the table is touched. A key typically belongs to one or two weak
 * collections, so the linear scan is the right shape.
 * <p>
 * Not thread-safe, like everything else reachable from a {@link JSContext}.
 */
final class JSWeakEntryTable {
    /**
     * The value stored for a {@code WeakSet} membership, which has no value of its own.
     */
    static final JSValue PRESENT = JSBoolean.TRUE;

    private final List<Entry> entries = new ArrayList<>(2);

    /**
     * The value this key holds for a collection.
     *
     * @param owner the owning {@code WeakMap} or {@code WeakSet}
     * @return the value, or {@code null} when this key is not in that collection
     */
    JSValue get(Object owner) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            Object entryOwner = entry.owner.get();
            if (entryOwner == null) {
                entries.remove(index);
            } else if (entryOwner == owner) {
                return entry.value;
            }
        }
        return null;
    }

    /**
     * Whether this key is in a collection.
     *
     * @param owner the owning {@code WeakMap} or {@code WeakSet}
     * @return true when an entry exists
     */
    boolean has(Object owner) {
        return get(owner) != null;
    }

    /**
     * Record or replace this key's entry for a collection.
     *
     * @param owner the owning {@code WeakMap} or {@code WeakSet}
     * @param value the value to store; {@link #PRESENT} for a set
     */
    void put(Object owner, JSValue value) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            Object entryOwner = entry.owner.get();
            if (entryOwner == null) {
                entries.remove(index);
            } else if (entryOwner == owner) {
                entry.value = value;
                return;
            }
        }
        entries.add(new Entry(owner, value));
    }

    /**
     * Remove this key's entry for a collection.
     *
     * @param owner the owning {@code WeakMap} or {@code WeakSet}
     * @return true when an entry was removed
     */
    boolean remove(Object owner) {
        boolean removed = false;
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            Object entryOwner = entry.owner.get();
            if (entryOwner == null) {
                entries.remove(index);
            } else if (entryOwner == owner) {
                entries.remove(index);
                removed = true;
            }
        }
        return removed;
    }

    /**
     * The number of live entries, for tests.
     *
     * @return the entry count after pruning dead owners
     */
    int size() {
        entries.removeIf(entry -> entry.owner.get() == null);
        return entries.size();
    }

    private static final class Entry {
        private final WeakReference<Object> owner;
        private JSValue value;

        private Entry(Object owner, JSValue value) {
            this.owner = new WeakReference<>(owner);
            this.value = value;
        }
    }
}
