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

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Weak collections used a {@code WeakHashMap}, which gets both halves of the contract wrong.
 * <p>
 * It compares keys with {@code equals}, so {@link JSError}'s value equality — two errors with the
 * same {@code name} and {@code message} — made distinct objects collide, and computing the hash ran
 * a guest {@code message} accessor. And it holds values strongly, so a value that refers back to
 * its own key kept the key reachable through the map, which is the opposite of what a
 * specification-conforming {@code WeakMap} does.
 * <p>
 * Entries now live on the key and name their collection by identity, so both properties follow.
 * <p>
 * Moving the entries onto the key introduced the mirror-image retention: an entry holds its value
 * strongly, and a dead collection's entry was only dropped when something else happened to touch
 * that key's table. A short-lived {@code WeakMap} could therefore leave a large value graph attached
 * to a long-lived key indefinitely. An entry is now itself the weak reference to its collection,
 * registered with a queue the runtime owns, so any weak-collection operation — or
 * {@code JSRuntime.gc()} — releases the values of every collection that has died, whichever keys
 * they were attached to.
 */
public class JSWeakCollectionTest extends BaseJavetTest {
    /**
     * Ask the collector for a while and report whether the reference cleared.
     *
     * @param reference the reference to watch
     * @return true when it cleared
     */
    private static boolean awaitCleared(WeakReference<?> reference) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (reference.get() == null) {
                return true;
            }
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return reference.get() == null;
            }
        }
        return reference.get() == null;
    }

    /**
     * Ask the collector for a while, running the engine-side drain between attempts.
     *
     * @param reference the reference to watch
     * @param drain     the engine operation that reclaims dead entries
     * @return true when it cleared
     */
    private static boolean awaitClearedWhile(WeakReference<?> reference, Runnable drain) {
        for (int attempt = 0; attempt < 50 && reference.get() != null; attempt++) {
            drain.run();
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return reference.get() == null;
    }

    @Test
    public void testDeadCollectionDoesNotPinItsEntriesOnALiveKey() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSObject key = context.createJSObject();
            JSWeakMap weakMap = context.createJSWeakMap();
            weakMap.weakMapSet(key, context.createJSObject());

            WeakReference<JSWeakMap> mapReference = new WeakReference<>(weakMap);
            weakMap = null;
            assertThat(awaitCleared(mapReference))
                    .as("a key must hold its collection weakly")
                    .isTrue();
            // Touching the table prunes the dead entry rather than leaving it forever.
            assertThat(new JSWeakMap(context).weakMapHas(key)).isFalse();
        }
    }

    @Test
    public void testDeadCollectionReleasesItsValueOnAnUnrelatedWeakOperation() {
        // The queue belongs to the runtime, so one dead collection's values are released by work on
        // a completely different collection and a completely different key.
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSObject key = context.createJSObject();
            JSWeakMap weakMap = context.createJSWeakMap();
            JSObject value = context.createJSObject();
            weakMap.weakMapSet(key, value);

            JSWeakSet unrelatedSet = context.createJSWeakSet();
            JSObject unrelatedKey = context.createJSObject();

            WeakReference<JSWeakMap> mapReference = new WeakReference<>(weakMap);
            WeakReference<JSObject> valueReference = new WeakReference<>(value);
            weakMap = null;
            value = null;

            assertThat(awaitCleared(mapReference)).isTrue();
            assertThat(awaitClearedWhile(valueReference, () -> unrelatedSet.weakSetHas(unrelatedKey)))
                    .as("an operation on any weak collection releases every dead collection's values")
                    .isTrue();
            assertThat(key).isNotNull();
        }
    }

    @Test
    public void testDeadCollectionReleasesItsValueWhenTheRuntimeIsPolled() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            // The key deliberately stays live for the whole test and its own table is never touched
            // again, which is the case pruning on access could not reach.
            JSObject key = context.createJSObject();
            JSWeakMap weakMap = context.createJSWeakMap();
            JSObject value = context.createJSObject();
            weakMap.weakMapSet(key, value);
            assertThat(weakMap.weakMapGet(key)).isSameAs(value);

            WeakReference<JSWeakMap> mapReference = new WeakReference<>(weakMap);
            WeakReference<JSObject> valueReference = new WeakReference<>(value);
            weakMap = null;
            value = null;

            assertThat(awaitCleared(mapReference))
                    .as("a key must hold its collection weakly")
                    .isTrue();
            // gc() is the engine's documented "drain what the collector has already taken" poll,
            // and it reclaims on the calling thread rather than on a thread of its own.
            assertThat(awaitClearedWhile(valueReference, runtime::gc))
                    .as("a value must not outlive the collection that held it")
                    .isTrue();
            assertThat(key).isNotNull();
        }
    }

    @Test
    public void testDistinctErrorsWithTheSameMessageAreDistinctKeys() {
        assertStringWithJavet("""
                const a = new Error('x');
                const b = new Error('x');
                const wm = new WeakMap([[a, 1], [b, 2]]);
                [wm.get(a), wm.get(b), new WeakSet([a]).has(b)].join(',');""");
    }

    @Test
    public void testKeyMayBelongToSeveralCollections() {
        assertStringWithJavet("""
                const key = {};
                const first = new WeakMap();
                const second = new WeakMap();
                const set = new WeakSet();
                first.set(key, 1);
                second.set(key, 2);
                set.add(key);
                first.delete(key);
                [first.has(key), second.get(key), set.has(key)].join(',');""");
    }

    @Test
    public void testMutatingAKeyDoesNotMoveIt() {
        // A WeakHashMap bucket is chosen from hashCode at insertion; JSError hashed over its
        // mutable message, so assigning to it lost the entry.
        assertStringWithJavet("""
                const key = new Error('before');
                const wm = new WeakMap();
                wm.set(key, 'value');
                key.message = 'after';
                [wm.has(key), String(wm.get(key))].join(',');""");
    }

    @Test
    public void testRegisteredSymbolsAreRejectedByBothBoundaries() {
        // Symbol.for keeps its symbol reachable for the realm's lifetime, so it can never be
        // collected and is not a legal weak key. The guest-facing methods enforced that; the direct
        // Java API did not, so an embedder could create state JavaScript itself cannot.
        assertStringWithJavet("""
                const registered = Symbol.for('registered');
                const results = [];
                for (const attempt of [
                  () => new WeakMap().set(registered, 1),
                  () => new WeakSet().add(registered),
                ]) {
                  try { attempt(); results.push('accepted'); }
                  catch (e) { results.push(e.constructor.name); }
                }
                results.join(',');""");

        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSSymbol registered = runtime.getOrCreateGlobalSymbol("registered");
            JSWeakMap weakMap = context.createJSWeakMap();
            JSWeakSet weakSet = context.createJSWeakSet();

            assertThatThrownBy(() -> weakMap.weakMapSet(registered, JSNumber.of(1)))
                    .isInstanceOf(JSTypeErrorException.class);
            assertThatThrownBy(() -> weakSet.weakSetAdd(registered))
                    .isInstanceOf(JSTypeErrorException.class);
            assertThat(weakMap.weakMapHas(registered)).isFalse();
            assertThat(weakMap.weakMapDelete(registered)).isFalse();
            assertThat(weakSet.weakSetHas(registered)).isFalse();
            assertThat(weakSet.weakSetDelete(registered)).isFalse();

            // An unregistered symbol is still a legal key through the same API.
            JSSymbol unregistered = new JSSymbol("unregistered");
            weakMap.weakMapSet(unregistered, JSNumber.of(7));
            weakSet.weakSetAdd(unregistered);
            assertThat(weakMap.weakMapGet(unregistered)).isEqualTo(JSNumber.of(7));
            assertThat(weakSet.weakSetHas(unregistered)).isTrue();
        }
    }

    @Test
    public void testSettingAKeyDoesNotRunItsAccessors() {
        assertStringWithJavet("""
                let calls = 0;
                const error = new Error();
                Object.defineProperty(error, 'message', { get() { calls++; return 'x' } });
                Object.defineProperty(error, 'name', { get() { calls++; return 'y' } });
                const wm = new WeakMap();
                wm.set(error, 1);
                wm.get(error);
                wm.has(error);
                new WeakSet().add(error);
                String(calls);""");
    }

    @Test
    public void testSymbolKeysBehaveTheSameWay() {
        assertStringWithJavet("""
                const first = Symbol('same');
                const second = Symbol('same');
                const wm = new WeakMap();
                wm.set(first, 1);
                [wm.has(first), wm.has(second), wm.get(first)].join(',');""");
    }

    @Test
    public void testUndefinedValueStillRegistersTheKey() {
        assertStringWithJavet("""
                const key = {};
                const wm = new WeakMap();
                wm.set(key, undefined);
                [wm.has(key), String(wm.get(key)), wm.delete(key), wm.has(key)].join(',');""");
    }

    @Test
    public void testValueReferringToItsKeyDoesNotKeepTheKeyAlive() {
        // The ephemeron case. Built through the Java API so nothing else in the realm holds the
        // key: with a WeakHashMap the path map -> value -> key kept it reachable forever.
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSWeakMap weakMap = context.createJSWeakMap();
            JSObject key = context.createJSObject();
            JSObject value = context.createJSObject();
            value.set(PropertyKey.fromString("backReference"), key);
            weakMap.weakMapSet(key, value);
            assertThat(weakMap.weakMapHas(key)).isTrue();

            WeakReference<JSObject> keyReference = new WeakReference<>(key);
            WeakReference<JSObject> valueReference = new WeakReference<>(value);
            key = null;
            value = null;
            assertThat(awaitCleared(keyReference))
                    .as("a value's back-reference to its own key must not keep the key alive")
                    .isTrue();
            assertThat(valueReference.get())
                    .as("the value dies with its key")
                    .isNull();
            assertThat(weakMap).isNotNull();
        }
    }

    @Test
    public void testWeakCollectionDoesNotKeepAnUnrelatedKeyAlive() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSWeakSet weakSet = context.createJSWeakSet();
            JSObject member = context.createJSObject();
            weakSet.weakSetAdd(member);
            assertThat(weakSet.weakSetHas(member)).isTrue();

            WeakReference<JSObject> memberReference = new WeakReference<>(member);
            member = null;
            assertThat(awaitCleared(memberReference)).isTrue();
            assertThat(weakSet).isNotNull();
        }
    }
}
