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
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.assertj.core.api.Assertions.assertThat;

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
