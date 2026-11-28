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

package com.caoccao.qjs4j.core.json;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code JSON.parse} and {@link JSValue#toJavaObject()} cost heap, not Java stack, per nesting level.
 * <p>
 * Both recursed once per level: {@code JSON.parse} of 100,000 nested arrays, which V8 parses, overflowed, and
 * {@code toJavaObject()} — a public API with no VM frame around it — threw a raw {@link StackOverflowError} for a deep
 * nest and for any cycle. {@code toJavaObject()} also turned dates, maps, sets and regular expressions into empty maps.
 */
public class JSONDeepNestingAndJavaObjectTest extends BaseJavetTest {
    @Test
    public void testDeeplyNestedJsonParses() throws Exception {
        assertIntegerWithJavet("JSON.parse('['.repeat(100000) + ']'.repeat(100000)).length", """
                var value = JSON.parse('{"a":'.repeat(50000) + '1' + '}'.repeat(50000));
                var depth = 0;
                while (typeof value === 'object') { value = value.a; depth++; }
                depth + value""");
        // On a thread whose stack holds only a few hundred recursive calls.
        Object length = runOnSmallStack(
                () -> context.eval("JSON.parse('['.repeat(200000) + ']'.repeat(200000)).length").toJavaObject());
        assertThat(length).isEqualTo(1.0);
    }

    @Test
    public void testParseResultsAndErrors() {
        assertStringWithJavet(
                """
                        JSON.stringify([
                          '[]', '{}', '[1,2,[3,{"a":[4]}]]', '{"a":1,"b":{"c":[true,false,null]},"__proto__":5}', ' [ 1 , 2 ] ',
                          '"str"', '-0', '1e400', '{"a":1,"a":2}', '\\t\\n[\\r1\\n]\\t', '{"":{"":{"":0}}}', '{"\\\\u0041":"\\\\n"}',
                        ].map(s => [JSON.parse(s), Object.keys(JSON.parse(s) ?? {})]))""",
                """
                        var log = [];
                        JSON.parse('{"a":[1, 2.50, {"b": "x"}], "c": -0}', function (k, v, ctx) {
                          log.push(k + '=' + JSON.stringify(ctx));
                          return v;
                        });
                        log.join(';')""", """
                        var parsed = JSON.parse('{"__proto__": [1], "x": {"__proto__": null}}');
                        [Object.getPrototypeOf(parsed) === Object.prototype, Array.isArray(parsed.__proto__),
                          Object.hasOwn(parsed.x, '__proto__')].join()""",
                // V8's own wording for these is different; what matters here is that every malformed input is a
                // SyntaxError at the same place.
                """
                        ['[', '[1', '[1,', '[1,]', '[1 2]', '{', '{"a"', '{"a":', '{"a":1', '{"a":1,', '{"a":1,}', '{"a" 1}',
                          '{a:1}', '{"a":1 "b":2}', '[[[]]]x', '', ' ', '[}', '{]', '{"a":[}', '[1]]', '{"a":1}}', 'nul', '[tru]']
                          .map(s => { try { JSON.parse(s); return 'parsed' } catch (e) { return e.name } }).join()""");
    }

    @Test
    public void testToJavaObjectBoxesArrayNumbersByRange() {
        // Integer when it fits, Long beyond int, Double beyond long or when not integral, -0 included.
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) context.eval("[1, 2 ** 40, -(2 ** 40), 1e20, -1e20, -0, 1.5, Infinity, NaN]")
                .toJavaObject();
        assertThat(list).containsExactly(1, 1099511627776L, -1099511627776L, 1e20, -1e20, -0.0, 1.5,
                Double.POSITIVE_INFINITY, Double.NaN);
    }

    @Test
    public void testToJavaObjectCompletesKeysBeforeHashingThem() {
        // A key is hashed by value when it is inserted, so it must be complete by then, whichever path reached it
        // first. Populating maps in reverse discovery order inserted `k` into `m` while `k` was still empty, so `m`
        // could not find it afterwards.
        for (String code : List.of("""
                const k = new Map([['x', 1]]);
                ({ k, m: new Map([[k, 2]]) })""", """
                const k = new Map([['x', 1]]);
                ({ m: new Map([[k, 2]]), k })""", """
                const k = [new Map([['x', 1]]), new Set([new Map([['y', 2]])])];
                ({ m: new Map([[k, 2]]), k })""")) {
            Map<?, ?> result = (Map<?, ?>) resetContext().eval(code).toJavaObject();
            Map<?, ?> map = (Map<?, ?>) result.get("m");
            assertThat(map).hasSize(1);
            assertThat(map.containsKey(result.get("k"))).as(code).isTrue();
            assertThat(map.get(result.get("k"))).as(code).isEqualTo(2);
        }
        // A key that can be hashed by value is still found by value.
        Set<?> set = (Set<?>) context.eval("new Set([[1, 2], new Set([3]), new Map([['a', [4]]])])").toJavaObject();
        assertThat(set.contains(List.of(1, 2))).isTrue();
        assertThat(set.contains(Set.of(3))).isTrue();
        assertThat(set.contains(Map.of("a", List.of(4)))).isTrue();
    }

    @Test
    public void testToJavaObjectConvertsBuiltInTypes() {
        // Integral numbers in arrays, maps and sets come out as Integer, as Javet converts them; plain object
        // properties keep their long-standing Double.
        assertObjectWithJavet("({ a: 1.5, b: 'x', c: [1, 2.5, 'y', null], d: { e: true } })",
                "new Map([['k', 1], ['n', { deep: [1] }]])", "new Set([1, 'two', 3])",
                "[new Map([['a', 2]]), new Set()]", "new Date(0)", "[new Date(1700000000000)]");
        JSValue values = context.eval("""
                [new Date(0), /x\\/y/gi, new Map([[{ k: 1 }, new Set([[1]])]]), 1n, new Date(NaN),
                  function f() {}, Symbol('s'), new Number(5), new String('s')]""");
        List<Object> list = new java.util.ArrayList<>((List<?>) values.toJavaObject());
        assertThat(list.get(0)).isInstanceOfSatisfying(ZonedDateTime.class,
                dateTime -> assertThat(dateTime.toInstant().toEpochMilli()).isZero());
        assertThat(list.get(1)).isEqualTo("/x\\/y/gi");
        assertThat(list.get(2)).isInstanceOfSatisfying(Map.class, map -> {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) map.entrySet().iterator().next();
            assertThat(entry.getKey()).isEqualTo(Map.of("k", 1.0));
            assertThat(map.size()).isEqualTo(1);
            assertThat(entry.getValue()).isEqualTo(Set.of(List.of(1)));
        });
        assertThat(list.get(3)).isEqualTo(BigInteger.ONE);
        assertThat(list.get(4)).isNull();
        // Objects that convert themselves keep doing so.
        assertThat(list.subList(5, 9)).containsExactly("function f() {}", "Symbol(s)", 5.0, "s");
    }

    @Test
    public void testToJavaObjectConvertsEachObjectOnce() {
        JSValue cyclic = context.eval("var c = { name: 'c', list: [] }; c.self = c; c.list.push(c, c.list); c");
        Object converted = cyclic.toJavaObject();
        assertThat(converted).isInstanceOfSatisfying(Map.class, map -> {
            assertThat(map.get("name")).isEqualTo("c");
            assertThat(map.get("self")).isSameAs(map);
            List<?> list = (List<?>) map.get("list");
            assertThat(list.get(0)).isSameAs(map);
            assertThat(list.get(1)).isSameAs(list);
        });
        JSValue shared = context.eval("var leaf = { v: 1 }; [leaf, { inner: leaf }]");
        List<?> sharedList = (List<?>) shared.toJavaObject();
        assertThat(((Map<?, ?>) sharedList.get(1)).get("inner")).isSameAs(sharedList.get(0));
        // A leaf met twice converts once; a property that converts to null is left out.
        List<?> leaves = (List<?>) context.eval("var day = new Date(0); [day, { day, gone: null, missing: undefined }]")
                .toJavaObject();
        assertThat(((Map<?, ?>) leaves.get(1)).get("day")).isSameAs(leaves.get(0));
        assertThat(((Map<?, ?>) leaves.get(1)).keySet()).isEqualTo(Set.of("day"));

        JSValue deep = context.eval("var d = []; for (var i = 0; i < 200000; i++) d = [d]; d");
        Object deepList = deep.toJavaObject();
        int depth = 0;
        while (deepList instanceof List<?> list && !list.isEmpty()) {
            deepList = list.get(0);
            depth++;
        }
        assertThat(depth).isEqualTo(200000);
        assertThat(context.eval("var o = {}; for (var i = 0; i < 200000; i++) o = { o }; o").toJavaObject())
                .isInstanceOf(Map.class);
        assertThat(context.eval("var m = new Map(); for (var i = 0; i < 200000; i++) m = new Map([[i, m]]); m")
                .toJavaObject()).isInstanceOf(Map.class);
    }

    @Test
    public void testToJavaObjectHashesKeysOfAnyDepthOnASmallStack() throws Exception {
        // Java's collections hash and compare recursively, a few frames per level, so a key nested 900 levels deep
        // overflowed a 256 KB stack; only keys deeper than 1,000 levels were hashed by identity. Keys of any depth now
        // hash and compare by value with a work list, and a nest of keys in keys hashes in linear time.
        JSValue keys = context
                .eval("""
                        const nest = (depth, wrap) => {
                          let value = 'leaf';
                          for (let i = 0; i < depth; i++) value = wrap(value);
                          return value;
                        };
                        [
                          new Map([[nest(900, k => ({ k })), 1]]),
                          new Set([nest(900, k => ({ k }))]),
                          new Set([nest(200000, k => [k]), nest(200000, k => [k])]),
                          new Set([nest(900, k => new Set([k])), nest(900, k => new Set([k]))]),
                          new Map([[nest(900, k => new Map([[k, k]])), 'first'], [nest(900, k => new Map([[k, k]])), 'second']]),
                          new Set([nest(200000, k => new Set([k]))]),
                        ]""");
        int leafHash = "leaf".hashCode();
        // Sizes and hash codes only: printing a deep collection would overflow the stack itself.
        runOnSmallStack(() -> {
            List<?> results = (List<?>) keys.toJavaObject();
            Map<?, ?> objectKeyMap = (Map<?, ?>) results.get(0);
            Object equalKey = ((Set<?>) results.get(1)).iterator().next();
            // An even number of { k } levels, each XORing in the hash of "k".
            assertThat(equalKey.hashCode()).isEqualTo(leafHash);
            assertThat(objectKeyMap.get(equalKey)).isEqualTo(1);
            // Two keys equal by value are one key, as Java's sets compare them.
            Set<?> arrays = (Set<?>) results.get(2);
            assertThat(arrays.size()).isEqualTo(1);
            assertThat(arrays.iterator().next().hashCode()).isEqualTo(leafHash + 31 * 200000);
            Set<?> sets = (Set<?>) results.get(3);
            assertThat(sets.size()).isEqualTo(1);
            assertThat(sets.iterator().next().hashCode()).isEqualTo(leafHash);
            // Each level is its own key and value, so its hash code is zero.
            Map<?, ?> maps = (Map<?, ?>) results.get(4);
            assertThat(maps.size()).isEqualTo(1);
            assertThat(maps.keySet().iterator().next().hashCode()).isZero();
            assertThat(maps.values().iterator().next()).isEqualTo("second");
            Set<?> deepSets = (Set<?>) results.get(5);
            assertThat(deepSets.iterator().next().hashCode()).isEqualTo(leafHash);
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToJavaObjectKeysHashAndCompareLikeJavaCollections() {
        // A key hashed by value keeps the List, Set and Map contracts: equal to every collection of its kind with equal
        // contents, in any order for a set or a map, and with that collection's hash code.
        Set<?> set = (Set<?>) context.eval("""
                const shared = [1];
                const cyclic = [];
                cyclic.push(cyclic);
                new Set([[shared, shared, [shared], null, 'a'], new Set([2, 'b', null]),
                  new Map([[3, 'c'], ['d', [4]], [null, null]]), shared, cyclic])""").toJavaObject();
        List<?> keys = new ArrayList<>(set);
        Map<Object, Object> expectedMap = new HashMap<>();
        expectedMap.put(null, null);
        expectedMap.put("d", List.of(4));
        expectedMap.put(3, "c");
        List<Object> expected = List.of(Arrays.asList(List.of(1), List.of(1), List.of(List.of(1)), null, "a"),
                new HashSet<>(Arrays.asList(null, "b", 2)), expectedMap, List.of(1));
        List<Object> differentContents = List.of(Arrays.asList(List.of(1), List.of(1), List.of(List.of(2)), null, "a"),
                Set.of(2, "b"), Map.of(3, "c"), List.of(2));
        List<Object> differentKinds = List.of(Set.of(), List.of(), List.of(), Set.of(1));
        for (int i = 0; i < expected.size(); i++) {
            Object key = keys.get(i);
            assertThat(key.equals(key)).isTrue();
            assertThat(key.equals(expected.get(i))).as("key %d", i).isTrue();
            assertThat(expected.get(i).equals(key)).as("key %d", i).isTrue();
            assertThat(key.hashCode()).as("key %d", i).isEqualTo(expected.get(i).hashCode());
            assertThat(set.contains(expected.get(i))).as("key %d", i).isTrue();
            assertThat(key.equals(new ArrayList<>(List.of(key)).get(0))).isTrue();
            assertThat(key.equals(differentContents.get(i))).as("key %d", i).isFalse();
            assertThat(key.equals(differentKinds.get(i))).as("key %d", i).isFalse();
        }
        // A key that hashes by identity equals nothing else.
        Object cyclicKey = keys.get(4);
        assertThat(keys.get(3).equals(cyclicKey)).isFalse();
        // A key made cyclic after conversion still hashes and compares, going by identity where it leads back.
        List<Object> mutated = (List<Object>) keys.get(3);
        mutated.add(mutated);
        assertThat(mutated.hashCode()).isEqualTo(31 * (31 + 1) + System.identityHashCode(mutated));
        assertThat(mutated.equals(mutated)).isTrue();
        assertThat(mutated.equals(new ArrayList<>(mutated))).isFalse();
    }

    @Test
    public void testToJavaObjectKeysThatCannotBeHashedByValueUseIdentity() {
        // A key whose contents lead into a cycle has no finite hash code by value, so Java's hashed collections
        // overflowed the stack inserting it. Such a key hashes and compares by identity, as JavaScript compares keys.
        Map<?, ?> cyclicKeyMap = (Map<?, ?>) context.eval("""
                const o = {};
                o.self = o;
                new Map([[o, 1]])""").toJavaObject();
        Map<?, ?> cyclicKey = (Map<?, ?>) cyclicKeyMap.keySet().iterator().next();
        assertThat(cyclicKey.get("self")).isSameAs(cyclicKey);
        assertThat(cyclicKeyMap.get(cyclicKey)).isEqualTo(1);
        assertThat(cyclicKey.equals(cyclicKey)).isTrue();
        assertThat(cyclicKey).isNotEqualTo(new java.util.LinkedHashMap<>(cyclicKey));

        List<?> results = (List<?>) context.eval("""
                const array = [];
                array.push(array);
                const set = new Set();
                set.add(set);
                const map = new Map();
                map.set(map, 'itself');
                const x = {};
                x.self = x;
                [new Set([array]), new Map([[set, 'set']]), map, new Map([[{ x }, 'reaches']])]""").toJavaObject();
        Set<?> arraySet = (Set<?>) results.get(0);
        List<?> cyclicArray = (List<?>) arraySet.iterator().next();
        assertThat(cyclicArray.get(0)).isSameAs(cyclicArray);
        assertThat(arraySet.contains(cyclicArray)).isTrue();
        assertThat(cyclicArray.equals(cyclicArray)).isTrue();
        assertThat(cyclicArray).isNotEqualTo(new java.util.ArrayList<>(cyclicArray));
        Map<?, ?> setMap = (Map<?, ?>) results.get(1);
        Set<?> cyclicSet = (Set<?>) setMap.keySet().iterator().next();
        assertThat(cyclicSet.contains(cyclicSet)).isTrue();
        assertThat(setMap.get(cyclicSet)).isEqualTo("set");
        assertThat(cyclicSet.equals(cyclicSet)).isTrue();
        assertThat(cyclicSet).isNotEqualTo(new java.util.LinkedHashSet<>(cyclicSet));
        Map<?, ?> selfKeyed = (Map<?, ?>) results.get(2);
        assertThat(selfKeyed.get(selfKeyed)).isEqualTo("itself");
        Map<?, ?> reachingMap = (Map<?, ?>) results.get(3);
        assertThat(reachingMap.get(reachingMap.keySet().iterator().next())).isEqualTo("reaches");
    }

    private static Object runOnSmallStack(java.util.concurrent.Callable<Object> callable) throws Exception {
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(null, () -> {
            try {
                result[0] = callable.call();
            } catch (Throwable e) {
                failure[0] = e;
            }
        }, "small-stack", 256 * 1024);
        thread.start();
        thread.join();
        if (failure[0] instanceof Error error) {
            // A StackOverflowError, or a failed assertion.
            throw error;
        }
        if (failure[0] != null) {
            throw (Exception) failure[0];
        }
        return result[0];
    }
}
