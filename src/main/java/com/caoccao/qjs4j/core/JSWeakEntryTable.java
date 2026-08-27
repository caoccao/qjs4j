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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
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
 * <p>
 * <strong>How a dead collection lets go of its values.</strong> An entry is itself the weak
 * reference to its collection, registered with the owning {@link JSRuntime}'s queue. When the
 * collection is collected the entry is enqueued, and the next weak-collection operation on that
 * runtime — or {@link JSRuntime#gc()} — clears its value. That covers the case pruning on access
 * could not: entries used to be dropped only when something touched the <em>same</em> key's table,
 * so a short-lived {@code WeakMap} could leave a large value graph attached to a long-lived key
 * with nothing that would ever collect it. Draining a queue the runtime owns reaches every dead
 * entry in the runtime, whichever key it sits on.
 * <p>
 * Draining happens on the thread that is using the engine, which is what keeps this class — like
 * everything else reachable from a {@link JSContext} — free of any synchronisation and free of a
 * background thread reaching into it.
 */
final class JSWeakEntryTable {
    /**
     * The value stored for a {@code WeakSet} membership, which has no value of its own.
     */
    static final JSValue PRESENT = JSBoolean.TRUE;

    private final List<Entry> entries = new ArrayList<>(2);

    /**
     * Clear the values of every entry whose collection has been collected.
     * <p>
     * The queue belongs to a {@link JSRuntime}, so one call reaches every dead entry in that
     * runtime rather than only the entries of whichever key is being touched.
     *
     * @param deadOwners the runtime's queue of collected weak collections
     */
    static void releaseDeadEntries(ReferenceQueue<Object> deadOwners) {
        Reference<?> dead;
        while ((dead = deadOwners.poll()) != null) {
            // The entry stays on its key's list until that list is next scanned; by then it holds
            // nothing, and the scan drops it.
            ((Entry) dead).value = null;
        }
    }

    /**
     * The value this key holds for a collection.
     *
     * @param owner the owning {@code WeakMap} or {@code WeakSet}
     * @return the value, or {@code null} when this key is not in that collection
     */
    JSValue get(Object owner) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            Object entryOwner = entry.get();
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
     * @param owner      the owning {@code WeakMap} or {@code WeakSet}
     * @param value      the value to store; {@link #PRESENT} for a set
     * @param deadOwners the runtime's queue, which the new entry registers itself with
     */
    void put(Object owner, JSValue value, ReferenceQueue<Object> deadOwners) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            Object entryOwner = entry.get();
            if (entryOwner == null) {
                entries.remove(index);
            } else if (entryOwner == owner) {
                entry.value = value;
                return;
            }
        }
        entries.add(new Entry(owner, value, deadOwners));
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
            Object entryOwner = entry.get();
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
        entries.removeIf(entry -> entry.get() == null);
        return entries.size();
    }

    /**
     * One key's membership of one collection, and the weak reference to that collection.
     * <p>
     * Being the reference is what makes reclamation work without a second object to keep alive: the
     * key's list holds the entry, so the entry is still reachable when its collection dies and can
     * therefore be enqueued — while a key that dies first takes the entry with it, and nothing is
     * enqueued at all.
     */
    private static final class Entry extends WeakReference<Object> {
        private JSValue value;

        private Entry(Object owner, JSValue value, ReferenceQueue<Object> deadOwners) {
            super(owner, deadOwners);
            this.value = value;
        }
    }
}
