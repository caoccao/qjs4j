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

import java.nio.IntBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Converted collection keys and their stack-safe Java equality and hash operations. */
final class JavaCollectionKeys {
    private JavaCollectionKeys() {
    }

    static boolean collectionEquals(CollectionKey collection, Object other, Class<?> kind) {
        return collection == other
                || !(collection instanceof IdentityKey) && kind.isInstance(other) && valueEquals(collection, other);
    }

    static int collectionHash(CollectionKey key, Integer knownHash) {
        if (key instanceof IdentityKey) {
            return System.identityHashCode(key);
        }
        return knownHash != null ? knownHash : valueHashCode(key, new IdentityHashMap<>());
    }

    static void forget(IdentityHashMap<Object, Integer> hashes) {
        for (Object value : hashes.keySet()) {
            if (value instanceof CollectionKey key) {
                key.cacheHash(null);
            }
        }
    }

    /**
     * Whether value equality looks inside an object: a list, a set or a map, unless it compares by identity.
     */
    private static boolean isValueCollection(Object value) {
        return (value instanceof List || value instanceof Set || value instanceof Map)
                && !(value instanceof IdentityKey);
    }

    /**
     * A collection's members: a list's or a set's elements, or a map's keys and values in turn.
     */
    private static List<Object> membersOf(Object collection) {
        if (collection instanceof Map<?, ?> map) {
            List<Object> members = new ArrayList<>(map.size() * 2);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                members.add(entry.getKey());
                members.add(entry.getValue());
            }
            return members;
        }
        return new ArrayList<>((Collection<?>) collection);
    }

    static void remember(Object value, IdentityHashMap<Object, Integer> hashes) {
        if (value instanceof CollectionKey key && !(key instanceof IdentityKey)) {
            key.cacheHash(valueHashCode(value, hashes));
        }
    }

    /**
     * Whether two collections are equal as {@link List#equals}, {@link Set#equals} and {@link Map#equals} define it,
     * decided without recursion.
     * <p>
     * Every collection reachable from either is given a canonical id, members first: equal leaves share an id, and
     * collections of the same kind whose members have the same ids — in order for a list, in any order for a set or a
     * map — share one. The two are equal when their ids are. Comparing member by member instead would recurse through
     * {@code contains} for every set or map key nested inside a set or map key.
     */
    static boolean valueEquals(Object left, Object right) {
        int[] nextId = {0};
        Map<Object, Integer> leafIds = new HashMap<>();
        // A signature is a collection's kind followed by its members' ids; an IntBuffer compares and hashes by content.
        Map<IntBuffer, Integer> signatureIds = new HashMap<>();
        IdentityHashMap<Object, Integer> collectionIds = new IdentityHashMap<>();
        IdentityHashMap<Object, Integer> backReferenceIds = new IdentityHashMap<>();
        ToIntFunction<Object> idOf = member -> {
            if (!isValueCollection(member)) {
                return leafIds.computeIfAbsent(member, key -> nextId[0]++);
            }
            Integer id = collectionIds.get(member);
            return id != null ? id : backReferenceIds.computeIfAbsent(member, key -> nextId[0]++);
        };
        Consumer<Object> assignId = collection -> {
            List<Object> members = membersOf(collection);
            int[] signature = new int[members.size() + 1];
            if (collection instanceof Map) {
                // Entries in any order: sorted by key id, then value id.
                long[] entries = new long[members.size() / 2];
                for (int i = 0; i < entries.length; i++) {
                    entries[i] = (long) idOf.applyAsInt(members.get(2 * i)) << 32
                            | idOf.applyAsInt(members.get(2 * i + 1));
                }
                Arrays.sort(entries);
                signature[0] = 2;
                for (int i = 0; i < entries.length; i++) {
                    signature[2 * i + 1] = (int) (entries[i] >>> 32);
                    signature[2 * i + 2] = (int) entries[i];
                }
            } else {
                for (int i = 0; i < members.size(); i++) {
                    signature[i + 1] = idOf.applyAsInt(members.get(i));
                }
                if (collection instanceof Set) {
                    // Elements in any order.
                    signature[0] = 1;
                    Arrays.sort(signature, 1, signature.length);
                }
            }
            collectionIds.put(collection, signatureIds.computeIfAbsent(IntBuffer.wrap(signature), key -> nextId[0]++));
        };
        for (Object root : new Object[]{left, right}) {
            if (isValueCollection(root)) {
                visitMembersFirst(root, collectionIds::containsKey, assignId);
            }
        }
        return idOf.applyAsInt(left) == idOf.applyAsInt(right);
    }

    /**
     * The hash code {@link List#hashCode}, {@link Set#hashCode} or {@link Map#hashCode} defines for a collection,
     * computed without recursion.
     *
     * @param root
     *            a list, set or map
     * @param hashes
     *            the hash codes of collections known not to have changed since they were computed, which this adds to
     */
    private static int valueHashCode(Object root, IdentityHashMap<Object, Integer> hashes) {
        ToIntFunction<Object> hashOf = member -> {
            if (!isValueCollection(member)) {
                return Objects.hashCode(member);
            }
            Integer hash = hashes.get(member);
            return hash != null ? hash : System.identityHashCode(member);
        };
        visitMembersFirst(root, hashes::containsKey, collection -> {
            List<Object> members = membersOf(collection);
            int hash;
            if (collection instanceof List) {
                hash = 1;
                for (Object element : members) {
                    hash = 31 * hash + hashOf.applyAsInt(element);
                }
            } else if (collection instanceof Set) {
                hash = 0;
                for (Object element : members) {
                    hash += hashOf.applyAsInt(element);
                }
            } else {
                hash = 0;
                for (int i = 0; i < members.size(); i += 2) {
                    hash += hashOf.applyAsInt(members.get(i)) ^ hashOf.applyAsInt(members.get(i + 1));
                }
            }
            hashes.put(collection, hash);
        });
        return hashes.get(root);
    }

    /**
     * Visit the collections reachable from a root, each once and after every collection among its members, with a work
     * list rather than recursion.
     * <p>
     * A collection the converter returned can be made cyclic afterwards. A member that leads back to a collection still
     * being visited is not waited for, so a cycle ends the walk instead of looping; the visitor finds that member not
     * yet visited and goes by its identity.
     *
     * @param root
     *            a list, set or map
     * @param visited
     *            whether a collection has been visited already, in this walk or an earlier one sharing its results
     * @param visitor
     *            called on each collection, which it must record as visited
     */
    private static void visitMembersFirst(Object root, Predicate<Object> visited, Consumer<Object> visitor) {
        Set<Object> started = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Object> work = new ArrayDeque<>();
        work.push(root);
        while (!work.isEmpty()) {
            Object collection = work.peek();
            if (visited.test(collection)) {
                work.pop();
            } else if (started.add(collection)) {
                for (Object member : membersOf(collection)) {
                    if (isValueCollection(member) && !started.contains(member) && !visited.test(member)) {
                        work.push(member);
                    }
                }
            } else {
                work.pop();
                visitor.accept(collection);
            }
        }
    }

    interface CollectionKey {
        void cacheHash(Integer hash);
    }

    interface IdentityKey {
    }
}
