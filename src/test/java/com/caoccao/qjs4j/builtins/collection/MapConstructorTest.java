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

package com.caoccao.qjs4j.builtins.collection;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.collection.JSMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Map constructor static methods.
 */
public class MapConstructorTest extends BaseJavetTest {

    @Test
    void testMapConstructorBehavior() {
        // Map with iterable
        String code = """
                const map = new Map([['a', 1], ['b', 2]]);
                map.size === 2 && map.get('a') === 1 && map.get('b') === 2""";
        assertBooleanWithJavet(code);

        // Empty Map
        assertIntegerWithJavet("new Map().size");
    }

    @Test
    void testMapConstructorIterableEdgeCases() {
        assertBooleanWithJavet("""
                var map = new Map(new Map([['a', 1], ['b', 2]]));
                map.size === 2 && map.get('a') === 1 && map.get('b') === 2""");

        assertEvalThrows("new Map({})", "TypeError");
        assertEvalThrows("new Map(1)", "TypeError");
        assertEvalThrows("new Map('ab')", "TypeError");

    }

    @Test
    void testMapSpeciesDescriptor() {
        assertBooleanWithJavet("""
                const desc = Object.getOwnPropertyDescriptor(Map, Symbol.species);
                typeof desc.get === 'function'
                && desc.enumerable === false
                && desc.configurable === true
                && desc.set === undefined
                && Map[Symbol.species] === Map""");
    }

    @Test
    void testMapStaticMethods() {
        // Map.groupBy should still work
        assertStringWithJavet("typeof Map.groupBy");
        assertIntegerWithJavet("Map.groupBy.length");

        // Test Map.groupBy functionality
        assertBooleanWithJavet("""
                const arr = [1, 2, 3, 4, 5];
                const grouped = Map.groupBy(arr, (x) => x % 2 === 0 ? 'even' : 'odd');
                grouped.get('even').length === 2 && grouped.get('odd').length === 3""");

        assertBooleanWithJavet("""
                const iterable = {
                  [Symbol.iterator]() {
                    let i = 0;
                    return {
                      next() {
                        return i < 4 ? { value: i++, done: false } : { done: true };
                      }
                    };
                  }
                };
                const grouped = Map.groupBy(iterable, (x) => x % 2);
                grouped.get(0).length === 2 && grouped.get(1).length === 2""");
    }

    @Test
    void testMapTypeof() {
        // Map should be a function
        assertStringWithJavet("typeof Map");

        // Map.length should be 0
        assertIntegerWithJavet("Map.length");

        // Map.name should be "Map"
        assertStringWithJavet("Map.name");

        // new Map() should work
        assertBooleanWithJavet("new Map() instanceof Map");

        // Map() without new should throw TypeError (requires new)
        assertErrorWithJavet("Map()");
    }

    @Test
    void testMapUsesOverriddenSetDuringConstruction() {
        JSArray entry1 = context.createJSArray();
        entry1.push(new JSNumber(1));
        entry1.push(new JSString("a"));
        JSArray entry2 = context.createJSArray();
        entry2.push(new JSNumber(2));
        entry2.push(new JSString("b"));
        JSArray entries = context.createJSArray();
        entries.push(entry1);
        entries.push(entry2);

        JSObject mapConstructor = context.getGlobalObject().get("Map").asObject().orElseThrow();
        JSObject mapPrototype = mapConstructor.get("prototype").asObject().orElseThrow();
        JSValue originalSet = mapPrototype.get("set");
        int[] called = {0};
        mapPrototype.set("set", new JSNativeFunction(context, "set", 2, (childContext, thisArg, args) -> {
            called[0]++;
            return thisArg;
        }));
        try {
            JSMap map = JSMap.create(context, entries).asMap().orElseThrow();
            assertThat(called[0]).isEqualTo(2);
            assertThat(map.size()).isEqualTo(0);
        } finally {
            mapPrototype.set("set", originalSet);
        }
    }
}
