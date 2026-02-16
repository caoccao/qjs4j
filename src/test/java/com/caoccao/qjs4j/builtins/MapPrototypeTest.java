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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Map.prototype methods.
 */
public class MapPrototypeTest extends BaseJavetTest {

    @Test
    void compareWithSet() {
        // For reference: Set follows the same pattern
        // Set.prototype[Symbol.iterator] === Set.prototype.values
        assertBooleanWithJavet("Set.prototype[Symbol.iterator] === Set.prototype.values");

        // And for Map:
        // Map.prototype[Symbol.iterator] === Map.prototype.entries
        assertBooleanWithJavet("Map.prototype[Symbol.iterator] === Map.prototype.entries");
    }

    @Test
    void demonstrateQuickJSAlignment() {
        // QuickJS implementation (quickjs.c line 52182):
        // JS_ALIAS_DEF("[Symbol.iterator]", "entries")
        //
        // This means [Symbol.iterator] is an ALIAS to entries,
        // not a separate function with the same implementation.
        //
        // Our fix implements this by creating one JSNativeFunction
        // and assigning it to both properties.

        assertBooleanWithJavet("""
                // Verify the alias relationship
                var entriesFunc = Map.prototype.entries;
                var iteratorFunc = Map.prototype[Symbol.iterator];
                
                // They must be the exact same reference
                entriesFunc === iteratorFunc""");
    }

    @Test
    void showTheyWorkIdentically() {
        // Both produce the same iteration results
        assertBooleanWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                
                // Using entries()
                var result1 = '';
                var it1 = m.entries();
                var entry;
                while (!(entry = it1.next()).done) {
                  result1 += entry.value[0] + entry.value[1];
                }
                
                // Using Symbol.iterator
                var result2 = '';
                var it2 = m[Symbol.iterator]();
                while (!(entry = it2.next()).done) {
                  result2 += entry.value[0] + entry.value[1];
                }
                
                // Results are identical
                result1 === result2 && result1 === '1a2b3c'""");
    }

    @Test
    void testBooleanHashCodes() {
        JSMap.KeyWrapper trueVal1 = new JSMap.KeyWrapper(JSBoolean.TRUE);
        JSMap.KeyWrapper trueVal2 = new JSMap.KeyWrapper(JSBoolean.TRUE);
        JSMap.KeyWrapper falseVal = new JSMap.KeyWrapper(JSBoolean.FALSE);

        // Same booleans should have equal hash codes
        assertThat(trueVal1.equals(trueVal2)).isTrue();
        assertThat(trueVal1.hashCode()).isEqualTo(trueVal2.hashCode());

        // Different booleans
        assertThat(trueVal1.equals(falseVal)).isFalse();
        assertThat(trueVal1.hashCode()).isNotEqualTo(falseVal.hashCode());
    }

    @Test
    void testBothIteratorsGiveSameResult() {
        // Verify entries() and Symbol.iterator give identical results
        assertBooleanWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                var it1 = m.entries();
                var it2 = m[Symbol.iterator]();
                var result1 = '';
                var result2 = '';
                var entry;
                while (!(entry = it1.next()).done) {
                  result1 += entry.value[0] + entry.value[1];
                }
                while (!(entry = it2.next()).done) {
                  result2 += entry.value[0] + entry.value[1];
                }
                result1 === result2""");
    }

    @Test
    public void testClear() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: clear map
        JSValue result = MapPrototype.clear(context, map, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();
        assertThat(map.size()).isEqualTo(0);

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.clear(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    void testClearAndAdd() {
        // Clear then add
        assertIntegerWithJavet("var m = new Map([[1, 'a']]); m.clear(); m.set(2, 'b'); m.size");
        assertStringWithJavet("var m = new Map([[1, 'a']]); m.clear(); m.set(2, 'b'); m.get(2)");
        assertBooleanWithJavet("var m = new Map([[1, 'a']]); m.clear(); m.set(2, 'b'); m.has(1)");
    }

    @Test
    void testClearEmpty() {
        // Clear empty map
        assertUndefinedWithJavet("new Map().clear()");
        assertIntegerWithJavet("var m = new Map(); m.clear(); m.size");
    }

    @Test
    void testClearMultipleTimes() {
        // Clear multiple times
        assertIntegerWithJavet("var m = new Map([[1, 'a']]); m.clear(); m.clear(); m.size");
    }

    @Test
    void testClearNonEmpty() {
        // Clear non-empty map
        assertUndefinedWithJavet("new Map([[1, 'a'], [2, 'b']]).clear()");
        assertIntegerWithJavet("var m = new Map([[1, 'a'], [2, 'b']]); m.clear(); m.size");
        assertBooleanWithJavet("var m = new Map([[1, 'a']]); m.clear(); m.has(1)");
    }

    @Test
    void testCompareWithSetBehavior() {
        // Compare with Set: Set.prototype[Symbol.iterator] === Set.prototype.values
        assertBooleanWithJavet("Set.prototype[Symbol.iterator] === Set.prototype.values");
        // But for Map, it should be entries
        assertBooleanWithJavet("Map.prototype[Symbol.iterator] === Map.prototype.entries");
        // They should be different patterns
        assertBooleanWithJavet("Map.prototype[Symbol.iterator] !== Map.prototype.values");
    }

    @Test
    public void testDelete() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: delete existing key
        JSValue result = MapPrototype.delete(context, map, new JSValue[]{new JSString("key1")});
        assertThat(result.isBooleanTrue()).isTrue();
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.mapGet(new JSString("key1")).isUndefined()).isTrue();

        // Normal case: delete non-existing key
        result = MapPrototype.delete(context, map, new JSValue[]{new JSString("key3")});
        assertThat(result.isBooleanFalse()).isTrue();
        assertThat(map.size()).isEqualTo(1);

        // Normal case: no arguments
        result = MapPrototype.delete(context, map, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.delete(context, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(context);
    }

    @Test
    void testDeleteAll() {
        // Delete all entries during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var visited = [];
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  visited.push(k);
                  m.delete(1);
                  m.delete(2);
                  m.delete(3);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteAndCheckMapSize() {
        // Check map size after deletion during forEach
        assertIntegerWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === 2) m.delete(3);
                });
                m.size""");
    }

    @Test
    void testDeleteAndReAdd() {
        // Delete and re-add an entry
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === 1) {
                    m.delete(3);
                    m.set(3, 'new');
                  }
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteCurrentEntry() {
        // Delete the current entry during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  m.delete(k);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteDuringIteration() {
        // Delete an entry that hasn't been visited yet
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === 2) m.delete(3);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteEmpty() {
        // delete from empty map returns false
        assertBooleanWithJavet("new Map().delete('key')");
        assertBooleanWithJavet("new Map().delete(1)");
    }

    @Test
    void testDeleteExisting() {
        // delete existing returns true and removes entry
        assertBooleanWithJavet("var m = new Map([[1, 'a']]); m.delete(1)");
        assertIntegerWithJavet("var m = new Map([[1, 'a'], [2, 'b']]); m.delete(1); m.size");
        assertBooleanWithJavet("var m = new Map([[1, 'a']]); m.delete(1); m.has(1)");
    }

    @Test
    void testDeleteFromEmptyMap() {
        // Delete from empty map (no-op)
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map();
                m.forEach(function(v, k) {
                  m.delete(1);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteMultipleDuringIteration() {
        // Delete multiple entries during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c'], [4, 'd'], [5, 'e']]);
                m.forEach(function(v, k) {
                  if(k === 1) {
                    m.delete(3);
                    m.delete(4);
                  }
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteNaN() {
        // delete with NaN
        assertBooleanWithJavet("var m = new Map(); m.set(NaN, 'val'); m.delete(NaN)");
        assertIntegerWithJavet("var m = new Map(); m.set(NaN, 'val'); m.delete(NaN); m.size");
        assertBooleanWithJavet("var m = new Map(); m.set(0/0, 'val'); m.delete(NaN)");
    }

    @Test
    void testDeleteNonExistent() {
        // Delete non-existent key during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b']]);
                m.forEach(function(v, k) {
                  m.delete(999);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteNonExisting() {
        // delete non-existing returns false
        assertBooleanWithJavet("var m = new Map([[1, 'a']]); m.delete(2)");
        assertIntegerWithJavet("var m = new Map([[1, 'a']]); m.delete(2); m.size");
    }

    @Test
    void testDeleteObject() {
        // delete with object key (by reference)
        assertBooleanWithJavet("var m = new Map(); m.set({}, 'val'); m.delete({})");
        assertBooleanWithJavet("var m = new Map(); var obj = {}; m.set(obj, 'val'); m.delete(obj)");
    }

    @Test
    void testDeletePreviousEntry() {
        // Delete an entry that was already visited
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === 3) m.delete(1);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteTwice() {
        // Deleting twice
        assertBooleanWithJavet("var m = new Map([[1, 'a']]); m.delete(1); m.delete(1)");
    }

    @Test
    void testDeleteWithComplexKeys() {
        // Delete with object keys
        assertIntegerWithJavet("""
                var count = 0;
                var obj1 = {id: 1};
                var obj2 = {id: 2};
                var obj3 = {id: 3};
                var m = new Map([[obj1, 'a'], [obj2, 'b'], [obj3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === obj2) m.delete(obj3);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteWithNullValue() {
        // Test with a map that has null as a value
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, null], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === 1) m.delete(2);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteWithUndefinedValue() {
        // Test with a map that has undefined as a value
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, undefined], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === 1) m.delete(2);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteZero() {
        // delete with +0/-0
        assertBooleanWithJavet("var m = new Map(); m.set(0, 'val'); m.delete(-0)");
        assertIntegerWithJavet("var m = new Map(); m.set(0, 'val'); m.delete(-0); m.size");
        assertBooleanWithJavet("var m = new Map(); m.set(-0, 'val'); m.delete(0)");
    }

    @Test
    void testDifferentNumbersHaveDifferentHashes() {
        // Different numbers should (likely) have different hash codes
        JSMap.KeyWrapper one = new JSMap.KeyWrapper(new JSNumber(1.0));
        JSMap.KeyWrapper two = new JSMap.KeyWrapper(new JSNumber(2.0));

        assertThat(one.equals(two)).isFalse();
        // While not strictly required, good hash functions should produce different hashes
        assertThat(one.hashCode()).isNotEqualTo(two.hashCode());
    }

    @Test
    public void testEntries() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get entries (returns iterator)
        JSValue result = MapPrototype.entries(context, map, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Check first entry
        JSObject iterResult1 = iterator.next();
        assertThat((Boolean) iterResult1.get("done").toJavaObject()).isFalse();
        JSArray entry1 = iterResult1.get("value").asArray().orElseThrow();
        assertThat(entry1.getLength()).isEqualTo(2);
        assertThat(entry1.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("key1");
        assertThat(entry1.get(1).asString().map(JSString::value).orElseThrow()).isEqualTo("value1");

        // Check second entry
        JSObject iterResult2 = iterator.next();
        assertThat((Boolean) iterResult2.get("done").toJavaObject()).isFalse();
        JSArray entry2 = iterResult2.get("value").asArray().orElseThrow();
        assertThat(entry2.getLength()).isEqualTo(2);
        assertThat(entry2.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("key2");
        assertThat(entry2.get(1).asString().map(JSString::value).orElseThrow()).isEqualTo("value2");

        // Iterator should be exhausted
        JSObject iterResult3 = iterator.next();
        assertThat((Boolean) iterResult3.get("done").toJavaObject()).isTrue();

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.entries(context, emptyMap, new JSValue[]{});
        iterator = result.asIterator().orElseThrow();
        JSObject emptyResult = iterator.next();
        assertThat((Boolean) emptyResult.get("done").toJavaObject()).isTrue();

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.entries(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    void testEntriesIterationOrder() {
        // Iteration in insertion order using manual iteration
        assertStringWithJavet("""
                var result = '';
                var it = new Map([[3, 'c'], [1, 'a'], [2, 'b']]).entries();
                var entry;
                while (!(entry = it.next()).done) {
                  result += entry.value[0] + entry.value[1];
                }
                result""");
    }

    @Test
    void testEntriesIterator() {
        // entries() returns iterator
        assertBooleanWithJavet("typeof new Map().entries() === 'object'");
        assertBooleanWithJavet("typeof new Map([[1, 'a']]).entries().next === 'function'");
    }

    @Test
    void testEntriesPreservesInsertionOrder() {
        // entries() should preserve insertion order
        assertStringWithJavet("""
                var m = new Map();
                m.set(3, 'c');
                m.set(1, 'a');
                m.set(2, 'b');
                var it = m.entries();
                var result = '';
                var entry;
                while (!(entry = it.next()).done) {
                  result += entry.value[0];
                }
                result""");
    }

    @Test
    void testEntriesProducesKeyValuePairs() {
        // entries() should produce [key, value] pairs
        assertStringWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                var it = m.entries();
                var result = '';
                var entry;
                while (!(entry = it.next()).done) {
                  result += entry.value[0] + entry.value[1];
                }
                result""");
    }

    @Test
    void testEntriesReturnsIterator() {
        // entries() should return an iterator
        assertBooleanWithJavet("typeof new Map([[1, 'a']]).entries() === 'object'");
        assertBooleanWithJavet("typeof new Map([[1, 'a']]).entries().next === 'function'");
    }

    @Test
    void testEntriesWithEmptyMap() {
        // entries() should work with empty map
        assertBooleanWithJavet("""
                var m = new Map();
                var it = m.entries();
                var entry = it.next();
                entry.done""");
    }

    @Test
    void testEntriesWithMixedTypes() {
        // entries() should handle mixed key types
        assertIntegerWithJavet("""
                var m = new Map();
                m.set(1, 'num');
                m.set('str', 'string');
                m.set(true, 'bool');
                m.set(null, 'null');
                m.set(undefined, 'undef');
                var it = m.entries();
                var count = 0;
                while (!it.next().done) count++;
                count""");
    }

    @Test
    public void testForEach() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: forEach with callback
        final StringBuilder result = new StringBuilder();
        JSFunction callback = new JSNativeFunction("testCallback", 3, (childContext, thisArg, args) -> {
            String value = args[0].asString().map(JSString::value).orElseThrow();
            String key = args[1].asString().map(JSString::value).orElseThrow();
            result.append(key).append(":").append(value).append(",");
            return JSUndefined.INSTANCE;
        });

        JSValue forEachResult = MapPrototype.forEach(context, map, new JSValue[]{callback});
        assertThat(forEachResult.isUndefined()).isTrue();
        // Note: Order might vary, but both entries should be present
        String resultStr = result.toString();
        assertThat(resultStr.contains("key1:value1")).isTrue();
        assertThat(resultStr.contains("key2:value2")).isTrue();

        // Edge case: no callback function
        assertTypeError(MapPrototype.forEach(context, map, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-function callback
        assertTypeError(MapPrototype.forEach(context, map, new JSValue[]{new JSString("not function")}));
        assertPendingException(context);

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.forEach(context, new JSString("not map"), new JSValue[]{callback}));
        assertPendingException(context);
    }

    @Test
    void testForEachAddsDifferentTypes() {
        // Test adding different types of keys during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'num']]);
                m.forEach(function(v, k) {
                  if(count === 0) {
                    m.set('string', 's');
                    m.set(true, 't');
                  }
                  count++;
                });
                count""");
    }

    @Test
    void testForEachBasic() {
        // Basic forEach test
        assertIntegerWithJavet("""
                var count = 0;
                new Map([[1, 'a'], [2, 'b'], [3, 'c']]).forEach(function(v, k, m) { count++; });
                count""");
    }

    @Test
    void testForEachChainedAdds() {
        // Test where each iteration adds the next entry
        assertIntegerWithJavet("""
                var m = new Map([[1, 'a']]);
                var count = 0;
                m.forEach(function(v, k) {
                  if(k < 5) m.set(k + 1, 'x');
                  count++;
                });
                count""");
    }

    @Test
    void testForEachComplexDynamicGrowth() {
        // Complex test: each iteration adds multiple entries if under limit
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[0, 'start']]);
                m.forEach(function(v, k) {
                  if(count < 3) {
                    m.set(count * 10 + 1, 'a');
                    m.set(count * 10 + 2, 'b');
                  }
                  count++;
                });
                count""");
    }

    @Test
    void testForEachDeleteDuringIteration() {
        // Delete during iteration - implementation specific behavior
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  if(k === 2) m.delete(3);
                  count++;
                });
                count""");
    }

    @Test
    void testForEachDuplicateKeyDoesNotIncreaseVisits() {
        // Setting an existing key should not cause additional visits
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b']]);
                m.forEach(function(v, k) {
                  m.set(1, 'updated');
                  count++;
                });
                count""");
    }

    @Test
    void testForEachEmptyMap() {
        // Empty map should not invoke callback
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map();
                m.forEach(function() { count++; });
                count""");
    }

    @Test
    void testForEachEmptyMapReturnsUndefined() {
        // forEach on empty map returns undefined
        assertUndefinedWithJavet("new Map().forEach(function() {})");
    }

    @Test
    void testForEachInsertionOrder() {
        // Entries should be visited in insertion order
        assertStringWithJavet("""
                var result = '';
                var m = new Map([[3, 'c'], [1, 'a'], [2, 'b']]);
                m.forEach(function(v) { result += v; });
                result""");
    }

    @Test
    void testForEachNewEntriesInOrder() {
        // Newly added entries should be visited in insertion order
        assertStringWithJavet("""
                var result = '';
                var m = new Map([['a', 1]]);
                m.forEach(function(v, k) {
                  result += k;
                  if(k === 'a') { m.set('b', 2); m.set('c', 3); }
                  if(k === 'b') { m.set('d', 4); }
                });
                result""");
    }

    @Test
    void testForEachReceivesMapAsThirdArg() {
        // Third argument should be the Map itself
        assertBooleanWithJavet("""
                var isMap = false;
                var m = new Map([[1, 'a']]);
                m.forEach(function(v, k, map) {
                  isMap = (map === m);
                });
                isMap""");
    }

    @Test
    void testForEachReturnsUndefined() {
        // forEach should always return undefined
        assertUndefinedWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b']]);
                m.forEach(function() {})""");
    }

    @Test
    void testForEachThisArgBinding() {
        // thisArg properly bound
        assertIntegerWithJavet("""
                var obj = {count: 0};
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function() { this.count++; }, obj);
                obj.count""");
    }

    @Test
    void testForEachValueKeyOrder() {
        // Verify that callback receives (value, key, map) not (key, value, map)
        assertStringWithJavet("""
                var result = '';
                new Map([[1, 'a'], [2, 'b']]).forEach(function(v, k) { result += v + k; });
                result""");
    }

    @Test
    void testForEachVisitsNewlyAddedEntries() {
        // QuickJS behavior: forEach continues to visit entries added during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a']]);
                m.forEach(function(v, k) {
                  if(count < 5) m.set(count + 10, 'x');
                  count++;
                });
                count""");
    }

    @Test
    void testForEachWithThisArg() {
        // Test that thisArg is properly passed
        assertIntegerWithJavet("""
                var obj = {count: 0};
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function() { this.count++; }, obj);
                obj.count""");
    }

    @Test
    void testForOfMapCheckEntryType() {
        // Check what type each entry is
        assertBooleanWithJavet("""
                var allArrays = true;
                for (var entry of new Map([[1, 'a'], [2, 'b']])) {
                  if (!Array.isArray(entry)) allArrays = false;
                }
                allArrays""");
    }

    @Test
    void testForOfMapWithDestructuring() {
        // for-of over Map with destructuring (the target test)
        assertStringWithJavet("""
                var result = '';
                for (var [k, v] of new Map([[1, 'a'], [2, 'b']])) result += k + v;
                result""");
    }

    @Test
    void testForOfUsesSymbolIterator() {
        // for-of uses Symbol.iterator (which is entries)
        assertStringWithJavet("""
                var result = '';
                for (var [k, v] of new Map([[1, 'a'], [2, 'b']])) result += k + v;
                result""");
    }

    @Test
    void testFunctionLength() {
        // Both should have the same length (0 parameters)
        assertIntegerWithJavet("Map.prototype[Symbol.iterator].length");
        assertIntegerWithJavet("Map.prototype.entries.length");
        assertBooleanWithJavet("Map.prototype[Symbol.iterator].length === Map.prototype.entries.length");
    }

    @Test
    void testFunctionName() {
        // The function name should be "entries" (not "[Symbol.iterator]")
        assertStringWithJavet("Map.prototype[Symbol.iterator].name");
        assertStringWithJavet("Map.prototype.entries.name");
        // Since they're the same function, names should match
        assertBooleanWithJavet("Map.prototype[Symbol.iterator].name === Map.prototype.entries.name");
    }

    @Test
    public void testGet() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));

        // Normal case: existing key
        JSValue result = MapPrototype.get(context, map, new JSValue[]{new JSString("key1")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("value1");

        // Normal case: non-existing key
        result = MapPrototype.get(context, map, new JSValue[]{new JSString("key2")});
        assertThat(result.isUndefined()).isTrue();

        // Normal case: no arguments
        result = MapPrototype.get(context, map, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.get(context, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(context);
    }

    @Test
    void testGetNaN() {
        // Getting with NaN key
        assertStringWithJavet("var m = new Map(); m.set(NaN, 'value'); m.get(NaN)");
        assertStringWithJavet("var m = new Map(); m.set(0/0, 'value'); m.get(NaN)");
    }

    @Test
    void testGetNonExistent() {
        // Non-existent keys return undefined
        assertUndefinedWithJavet("new Map().get('missing')");
        assertUndefinedWithJavet("new Map([[1, 'a']]).get(2)");
        assertUndefinedWithJavet("new Map([[1, 'a']]).get('1')");
    }

    @Test
    void testGetObject() {
        // Getting with object keys (by reference)
        assertUndefinedWithJavet("var m = new Map(); m.set({}, 'value'); m.get({})");
        assertStringWithJavet("var m = new Map(); var obj = {}; m.set(obj, 'value'); m.get(obj)");
    }

    @Test
    void testGetOrInsertMethods() {
        assertThat(context.eval("""
                const m = new Map();
                const v1 = m.getOrInsert('x', 1);
                const v2 = m.getOrInsert('x', 2);
                let calls = 0;
                const v3 = m.getOrInsertComputed('x', () => { calls++; return 3; });
                const v4 = m.getOrInsertComputed('y', () => { calls++; m.set('y', 99); return 4; });
                v1 === 1 && v2 === 1 && v3 === 1 && v4 === 4 && m.get('y') === 4 && calls === 1""").toJavaObject())
                .isEqualTo(true);
        assertThat(context.eval("""
                (() => {
                  try {
                    new Map().getOrInsertComputed('x', 1);
                    return false;
                  } catch (e) {
                    return e instanceof TypeError;
                  }
                })()""").toJavaObject()).isEqualTo(true);
    }

    @Test
    public void testGetSize() {
        JSMap map = new JSMap();

        // Normal case: empty map
        JSValue result = MapPrototype.getSize(context, map, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Normal case: map with entries
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));
        result = MapPrototype.getSize(context, map, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.getSize(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);

        assertIntegerWithJavet(
                "new Map().size",
                "var a = new Map(); a.set('a', 1); a.size");
    }

    @Test
    void testGetVariousTypes() {
        // Get with various key types
        assertStringWithJavet("var m = new Map(); m.set(1, 'num'); m.get(1)");
        assertStringWithJavet("var m = new Map(); m.set('str', 'string'); m.get('str')");
        assertStringWithJavet("var m = new Map(); m.set(true, 'bool'); m.get(true)");
        assertStringWithJavet("var m = new Map(); m.set(null, 'null'); m.get(null)");
        assertStringWithJavet("var m = new Map(); m.set(undefined, 'undef'); m.get(undefined)");
    }

    @Test
    void testGetZero() {
        // Getting with +0/-0 keys
        assertStringWithJavet("var m = new Map(); m.set(0, 'zero'); m.get(-0)");
        assertStringWithJavet("var m = new Map(); m.set(-0, 'zero'); m.get(0)");
    }

    @Test
    public void testHas() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));

        // Normal case: existing key
        JSValue result = MapPrototype.has(context, map, new JSValue[]{new JSString("key1")});
        assertThat(result.isBooleanTrue()).isTrue();

        // Normal case: non-existing key
        result = MapPrototype.has(context, map, new JSValue[]{new JSString("key2")});
        assertThat(result.isBooleanFalse()).isTrue();

        // Normal case: no arguments
        result = MapPrototype.has(context, map, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.has(context, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(context);
    }

    @Test
    void testHasEmpty() {
        // has on empty map
        assertBooleanWithJavet("new Map().has(1)");
        assertBooleanWithJavet("new Map().has('key')");
        assertBooleanWithJavet("new Map().has(undefined)");
    }

    @Test
    void testHasNaN() {
        // has with NaN
        assertBooleanWithJavet("var m = new Map(); m.set(NaN, 'val'); m.has(NaN)");
        assertBooleanWithJavet("var m = new Map(); m.set(0/0, 'val'); m.has(NaN)");
        assertBooleanWithJavet("new Map().has(NaN)");
    }

    @Test
    void testHasTypeMismatch() {
        // Type must match exactly
        assertBooleanWithJavet("var m = new Map(); m.set(1, 'val'); m.has('1')");
        assertBooleanWithJavet("var m = new Map(); m.set('1', 'val'); m.has(1)");
        assertBooleanWithJavet("var m = new Map(); m.set(true, 'val'); m.has(1)");
    }

    @Test
    void testHasVariousTypes() {
        // has with various types
        assertBooleanWithJavet("var m = new Map([[1, 'a'], [2, 'b']]); m.has(1)");
        assertBooleanWithJavet("var m = new Map([[1, 'a'], [2, 'b']]); m.has(3)");
        assertBooleanWithJavet("var m = new Map([['key', 'val']]); m.has('key')");
        assertBooleanWithJavet("var m = new Map([[true, 'val']]); m.has(true)");
        assertBooleanWithJavet("var m = new Map([[null, 'val']]); m.has(null)");
        assertBooleanWithJavet("var m = new Map([[undefined, 'val']]); m.has(undefined)");
    }

    @Test
    void testHasZero() {
        // has with +0/-0
        assertBooleanWithJavet("var m = new Map(); m.set(0, 'val'); m.has(-0)");
        assertBooleanWithJavet("var m = new Map(); m.set(-0, 'val'); m.has(0)");
    }

    @Test
    void testHashCodeConsistency() {
        // hashCode() should return the same value when called multiple times
        JSMap.KeyWrapper key = new JSMap.KeyWrapper(new JSNumber(0.0));
        int hash1 = key.hashCode();
        int hash2 = key.hashCode();
        int hash3 = key.hashCode();

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash2).isEqualTo(hash3);
    }

    @Test
    void testInsertionOrderAfterDeleteAndReAdd() {
        // Deleting and re-adding moves to end
        assertStringWithJavet("""
                var result = '';
                var m = new Map();
                m.set(1, 'a');
                m.set(2, 'b');
                m.set(3, 'c');
                m.delete(2);
                m.set(2, 'b');
                for (var k of m.keys()) result += k;
                result""");
    }

    @Test
    void testInsertionOrderAfterUpdate() {
        // Updating value doesn't change order
        assertStringWithJavet("""
                var result = '';
                var m = new Map();
                m.set(1, 'a');
                m.set(2, 'b');
                m.set(3, 'c');
                m.set(2, 'updated');
                for (var k of m.keys()) result += k;
                result""");
    }

    @Test
    void testInsertionOrderPreserved() {
        // Map preserves insertion order
        assertStringWithJavet("""
                var result = '';
                var m = new Map();
                m.set(3, 'c');
                m.set(1, 'a');
                m.set(2, 'b');
                for (var k of m.keys()) result += k;
                result""");
    }

    @Test
    void testIteratorProtocol() {
        // Verify the iterator protocol is followed
        assertBooleanWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b']]);
                var it = m[Symbol.iterator]();
                var result1 = it.next();
                var result2 = it.next();
                var result3 = it.next();
                result1.done === false &&
                result2.done === false &&
                result3.done === true""");
    }

    @Test
    void testIteratorValueStructure() {
        // Verify the structure of iterator values [key, value]
        assertBooleanWithJavet("""
                var m = new Map([[1, 'a']]);
                var it = m[Symbol.iterator]();
                var result = it.next();
                Array.isArray(result.value) &&
                result.value.length === 2 &&
                result.value[0] === 1 &&
                result.value[1] === 'a'""");
    }

    @Test
    public void testKeys() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get keys
        JSValue result = MapPrototype.keys(context, map, new JSValue[]{});
        JSIterator keys = result.asIterator().orElseThrow();
        JSArray jsArray = JSIteratorHelper.toArray(context, keys);
        assertThat(jsArray.getLength()).isEqualTo(2);
        assertThat(jsArray.get(0).asString().map(JSString::value).orElse(null)).isEqualTo("key1");
        assertThat(jsArray.get(1).asString().map(JSString::value).orElse(null)).isEqualTo("key2");

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.keys(context, emptyMap, new JSValue[]{});
        keys = result.asIterator().orElseThrow();
        jsArray = JSIteratorHelper.toArray(context, keys);
        assertThat(jsArray.getLength()).isEqualTo(0);

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.keys(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    void testKeysIsNotValues() {
        // Map.prototype.keys and values are DIFFERENT (unlike Set)
        assertBooleanWithJavet("Map.prototype.keys !== Map.prototype.values");
    }

    @Test
    void testKeysIterationOrder() {
        // Iteration in insertion order
        assertStringWithJavet("""
                var result = '';
                for (var k of new Map([[3, 'c'], [1, 'a'], [2, 'b']]).keys()) result += k;
                result""");
    }

    @Test
    void testKeysIterator() {
        // keys() returns iterator
        assertBooleanWithJavet("typeof new Map().keys() === 'object'");
        assertBooleanWithJavet("typeof new Map([[1, 'a']]).keys().next === 'function'");
        assertIntegerWithJavet("var it = new Map([[1, 'a']]).keys(); it.next().value");
        assertBooleanWithJavet("var it = new Map([[1, 'a']]).keys(); it.next().done");
    }

    @Test
    void testLargeMap() {
        // Add many entries
        assertIntegerWithJavet("var m = new Map(); for(var i = 0; i < 1000; i++) m.set(i, 'val' + i); m.size");
        assertStringWithJavet("var m = new Map(); for(var i = 0; i < 1000; i++) m.set(i, 'val' + i); m.get(500)");
        assertBooleanWithJavet("var m = new Map(); for(var i = 0; i < 1000; i++) m.set(i, 'val' + i); m.has(999)");
        assertBooleanWithJavet("var m = new Map(); for(var i = 0; i < 1000; i++) m.set(i, 'val' + i); m.has(1000)");
    }

    @Test
    void testLargeMapDelete() {
        // Delete from large map
        assertIntegerWithJavet("var m = new Map(); for(var i = 0; i < 1000; i++) m.set(i, 'val'); m.delete(500); m.size");
        assertBooleanWithJavet("var m = new Map(); for(var i = 0; i < 1000; i++) m.set(i, 'val'); m.delete(500); m.has(500)");
    }

    @Test
    void testMapConstructorClosesIteratorOnError() {
        boolean[] closed = {false};
        boolean[] produced = {false};
        JSObject iterator = context.createJSObject();
        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, thisArg, args) -> {
            JSObject result = childContext.createJSObject();
            if (produced[0]) {
                result.set("done", JSBoolean.TRUE);
                result.set("value", JSUndefined.INSTANCE);
                return result;
            }
            produced[0] = true;
            JSArray entry = childContext.createJSArray();
            entry.push(new JSNumber(1));
            entry.push(new JSNumber(2));
            result.set("done", JSBoolean.FALSE);
            result.set("value", entry);
            return result;
        });
        JSNativeFunction returnFunction = new JSNativeFunction("return", 0, (childContext, thisArg, args) -> {
            JSObject result = childContext.createJSObject();
            result.set("done", JSBoolean.TRUE);
            result.set("value", JSUndefined.INSTANCE);
            closed[0] = true;
            return result;
        });
        iterator.set("next", nextFunction);
        iterator.set("return", returnFunction);

        JSObject iterable = context.createJSObject();
        iterable.set(PropertyKey.SYMBOL_ITERATOR,
                new JSNativeFunction("[Symbol.iterator]", 0, (childContext, thisArg, args) -> iterator));

        JSObject mapConstructor = context.getGlobalObject().get("Map").asObject().orElseThrow();
        JSObject mapPrototype = mapConstructor.get("prototype").asObject().orElseThrow();
        JSValue originalSet = mapPrototype.get("set");
        mapPrototype.set("set", new JSNativeFunction("set", 2, (childContext, thisArg, args) -> childContext.throwError("Error", "boom")));
        try {
            JSValue result = JSMap.create(context, iterable);
            assertThat(result.isError()).isTrue();
            assertThat(closed[0]).isTrue();
        } finally {
            mapPrototype.set("set", originalSet);
        }
    }

    @Test
    void testMapConstructorEmpty() {
        // Empty Map
        assertIntegerWithJavet("new Map().size");
    }

    @Test
    void testMapConstructorWithArray() {
        // Construct from array of entries
        assertIntegerWithJavet("new Map([[1, 'a'], [2, 'b'], [3, 'c']]).size");
        assertStringWithJavet("new Map([[1, 'a'], [2, 'b']]).get(1)");
        assertStringWithJavet("new Map([['key', 'value']]).get('key')");
    }

    @Test
    void testMapConstructorWithDuplicates() {
        // Later values should override earlier ones
        assertIntegerWithJavet("new Map([[1, 'a'], [1, 'b'], [1, 'c']]).size");
        assertStringWithJavet("new Map([[1, 'a'], [1, 'b'], [1, 'c']]).get(1)");
    }

    @Test
    void testMapEntriesManual() {
        // Manual iteration over Map.entries() to verify entries work
        assertStringWithJavet("""
                var result = '';
                var m = new Map([[1, 'a'], [2, 'b']]);
                var it = m.entries();
                var entry;
                while (!(entry = it.next()).done) {
                  var arr = entry.value;
                  result += arr[0] + arr[1];
                }
                result""");
    }

    @Test
    void testMapEquality() {
        // Two maps with same entries are not equal
        assertBooleanWithJavet("new Map([[1, 'a']]) === new Map([[1, 'a']])");
        // Same map instance is equal to itself
        assertBooleanWithJavet("var m = new Map(); m === m");
    }

    @Test
    void testMapIteratorMutationSemantics() {
        assertStringWithJavet("""
                const m = new Map([[1, 'a'], [2, 'b']]);
                const it = m.keys();
                const out = [];
                out.push(it.next().value);
                m.set(3, 'c');
                out.push(it.next().value);
                out.push(it.next().value);
                JSON.stringify(out)""");

        assertStringWithJavet("""
                const m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                const it = m.keys();
                const out = [];
                out.push(it.next().value);
                m.delete(2);
                m.set(2, 'bb');
                for (let n = it.next(); !n.done; n = it.next()) out.push(n.value);
                JSON.stringify(out)""");
    }

    @Test
    void testMapKeysWithForOf() {
        // for-of over Map.keys()
        assertStringWithJavet("""
                var result = '';
                for (var k of new Map([[1, 'a'], [2, 'b']]).keys()) result += k;
                result""");
    }

    @Test
    void testMapPrototypeDescriptors() {
        assertBooleanWithJavet("""
                (() => {
                  const setDesc = Object.getOwnPropertyDescriptor(Map.prototype, 'set');
                  const iteratorDesc = Object.getOwnPropertyDescriptor(Map.prototype, Symbol.iterator);
                  const tagDesc = Object.getOwnPropertyDescriptor(Map.prototype, Symbol.toStringTag);
                  return setDesc.enumerable === false
                    && setDesc.writable === true
                    && setDesc.configurable === true
                    && iteratorDesc.enumerable === false
                    && Map.prototype[Symbol.iterator] === Map.prototype.entries
                    && tagDesc.value === 'Map'
                    && tagDesc.enumerable === false
                    && tagDesc.writable === false
                    && tagDesc.configurable === true
                    && Object.prototype.toString.call(new Map()) === '[object Map]';
                })()""");

        assertThat(context.eval("""
                (() => {
                  const d1 = Object.getOwnPropertyDescriptor(Map.prototype, 'getOrInsert');
                  const d2 = Object.getOwnPropertyDescriptor(Map.prototype, 'getOrInsertComputed');
                  return typeof d1.value === 'function'
                    && d1.writable === true
                    && d1.enumerable === false
                    && d1.configurable === true
                    && d1.value.length === 2
                    && typeof d2.value === 'function'
                    && d2.writable === true
                    && d2.enumerable === false
                    && d2.configurable === true
                    && d2.value.length === 2;
                })()""").toJavaObject()).isEqualTo(true);
    }

    @Test
    void testMapSymbolIteratorManual() {
        // Manual iteration over Map[Symbol.iterator]() to verify it works
        assertStringWithJavet("""
                var result = '';
                var m = new Map([[1, 'a'], [2, 'b']]);
                var it = m[Symbol.iterator]();
                var entry;
                while (!(entry = it.next()).done) {
                  var arr = entry.value;
                  result += arr[0] + arr[1];
                }
                result""");
    }

    @Test
    void testMapToString() {
        // Map toString
        assertStringWithJavet("new Map().toString()");
        assertStringWithJavet("Object.prototype.toString.call(new Map())");
    }

    @Test
    void testMapValuesWithForOf() {
        // for-of over Map.values()
        assertStringWithJavet("""
                var result = '';
                for (var v of new Map([[1, 'a'], [2, 'b']]).values()) result += v;
                result""");
    }

    @Test
    void testMethodLengths() {
        // Method lengths (number of parameters)
        assertIntegerWithJavet("Map.prototype.set.length");
        assertIntegerWithJavet("Map.prototype.get.length");
        assertIntegerWithJavet("Map.prototype.has.length");
        assertIntegerWithJavet("Map.prototype.delete.length");
        assertIntegerWithJavet("Map.prototype.clear.length");
        assertIntegerWithJavet("Map.prototype.forEach.length");
    }

    @Test
    void testMethodNames() {
        // Method names
        assertStringWithJavet("Map.prototype.keys.name");
        assertStringWithJavet("Map.prototype.values.name");
        assertStringWithJavet("Map.prototype.entries.name");
    }

    @Test
    void testMixedTypeKeys() {
        // Map can have keys of different types
        assertIntegerWithJavet("new Map([[1, 'a'], ['str', 'b'], [true, 'c'], [null, 'd'], [undefined, 'e']]).size");
        assertStringWithJavet("var m = new Map([[1, 'a'], ['str', 'b']]); m.get(1)");
        assertStringWithJavet("var m = new Map([[1, 'a'], ['str', 'b']]); m.get('str')");
    }

    @Test
    void testModifyingOneAffectsBoth() {
        // Since they're the same object, modifying one affects the other
        assertBooleanWithJavet("""
                var original = Map.prototype[Symbol.iterator];
                var isSame = Map.prototype.entries === original;
                // Verify we can access both and they're the same
                isSame""");
    }

    @Test
    void testNaNHashCodeEquality() {
        // Create KeyWrappers for different NaN representations
        JSMap.KeyWrapper nan1 = new JSMap.KeyWrapper(new JSNumber(Double.NaN));
        JSMap.KeyWrapper nan2 = new JSMap.KeyWrapper(new JSNumber(0.0 / 0.0));
        JSMap.KeyWrapper nan3 = new JSMap.KeyWrapper(new JSNumber(Double.longBitsToDouble(0x7ff8000000000001L)));

        // All NaN values should be equal according to SameValueZero
        assertThat(nan1.equals(nan2)).isTrue();
        assertThat(nan2.equals(nan3)).isTrue();

        // They MUST have the same hashCode
        assertThat(nan1.hashCode()).isEqualTo(nan2.hashCode());
        assertThat(nan2.hashCode()).isEqualTo(nan3.hashCode());
    }

    @Test
    void testNaNInRealHashSet() {
        java.util.HashSet<JSMap.KeyWrapper> set = new java.util.HashSet<>();

        JSMap.KeyWrapper nan1 = new JSMap.KeyWrapper(new JSNumber(Double.NaN));
        JSMap.KeyWrapper nan2 = new JSMap.KeyWrapper(new JSNumber(0.0 / 0.0));

        assertThat(set.add(nan1)).isTrue();
        assertThat(set.add(nan2)).isFalse(); // Duplicate
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.contains(nan2)).isTrue();
    }

    @Test
    void testNoDelete() {
        // Control test: no deletion
        assertIntegerWithJavet("""
                var count = 0;
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  count++;
                });
                count""");
    }

    @Test
    void testNullAndUndefinedHashCodes() {
        JSMap.KeyWrapper null1 = new JSMap.KeyWrapper(JSNull.INSTANCE);
        JSMap.KeyWrapper null2 = new JSMap.KeyWrapper(JSNull.INSTANCE);
        JSMap.KeyWrapper undef1 = new JSMap.KeyWrapper(JSUndefined.INSTANCE);
        JSMap.KeyWrapper undef2 = new JSMap.KeyWrapper(JSUndefined.INSTANCE);

        // All nulls should have same hash code
        assertThat(null1.hashCode()).isEqualTo(null2.hashCode());

        // All undefineds should have same hash code
        assertThat(undef1.hashCode()).isEqualTo(undef2.hashCode());

        // Null and undefined should have different hash codes
        assertThat(null1.hashCode()).isNotEqualTo(undef1.hashCode());
    }

    @Test
    void testObjectHashCodesUseIdentity() {
        JSObject obj1 = new JSObject();
        JSObject obj2 = new JSObject();
        JSMap.KeyWrapper key1 = new JSMap.KeyWrapper(obj1);
        JSMap.KeyWrapper key2 = new JSMap.KeyWrapper(obj1); // Same object
        JSMap.KeyWrapper key3 = new JSMap.KeyWrapper(obj2); // Different object

        // Same object reference should have equal keys
        assertThat(key1.equals(key2)).isTrue();
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());

        // Different objects should not be equal (compared by identity)
        assertThat(key1.equals(key3)).isFalse();
    }

    @Test
    void testQuickJSCompatibility() {
        // This test verifies QuickJS behavior compatibility
        // In QuickJS: JS_ALIAS_DEF("[Symbol.iterator]", "entries")
        // This means Symbol.iterator is an alias, not a separate function
        assertBooleanWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b']]);
                // Both should work identically
                var entries = m.entries();
                var iterator = m[Symbol.iterator]();
                // And the methods should be the same object
                Map.prototype.entries === Map.prototype[Symbol.iterator]""");
    }

    @Test
    public void testSet() {
        JSMap map = new JSMap();

        // Normal case: set key-value pair
        JSValue result = MapPrototype.set(context, map, new JSValue[]{new JSString("key"), new JSString("value")});
        assertThat(result).isEqualTo(map); // Should return the map
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.mapGet(new JSString("key")).asString().map(JSString::value).orElseThrow()).isEqualTo("value");

        // Normal case: update existing key
        result = MapPrototype.set(context, map, new JSValue[]{new JSString("key"), new JSString("newValue")});
        assertThat(result).isEqualTo(map);
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.mapGet(new JSString("key")).asString().map(JSString::value).orElseThrow()).isEqualTo("newValue");

        // Normal case: set with undefined value
        result = MapPrototype.set(context, map, new JSValue[]{new JSString("key2")});
        assertThat(result).isEqualTo(map);
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.mapGet(new JSString("key2")).isUndefined()).isTrue();

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.set(context, new JSString("not map"), new JSValue[]{new JSString("key"), new JSString("value")}));
        assertPendingException(context);
    }

    @Test
    void testSetArrayKeys() {
        // Arrays are compared by reference
        assertIntegerWithJavet("var m = new Map(); m.set([1,2], 'a'); m.set([1,2], 'b'); m.size");
        assertIntegerWithJavet("var m = new Map(); var arr = [1,2]; m.set(arr, 'a'); m.set(arr, 'b'); m.size");
        assertStringWithJavet("var m = new Map(); var arr = [1,2]; m.set(arr, 'value'); m.get(arr)");
    }

    @Test
    void testSetChaining() {
        // set returns the Map for chaining
        assertBooleanWithJavet("var m = new Map(); m.set(1, 'a') === m");
        assertIntegerWithJavet("new Map().set(1, 'a').set(2, 'b').set(3, 'c').size");
        assertStringWithJavet("new Map().set(1, 'a').set(2, 'b').set(3, 'c').get(2)");
    }

    @Test
    void testSetNaNKeys() {
        // All NaN values treated as the same key
        assertIntegerWithJavet("var m = new Map(); m.set(NaN, 'a'); m.set(NaN, 'b'); m.size");
        assertStringWithJavet("var m = new Map(); m.set(NaN, 'a'); m.set(NaN, 'b'); m.get(NaN)");
        assertIntegerWithJavet("var m = new Map(); m.set(0/0, 'a'); m.set(NaN, 'b'); m.size");
        assertBooleanWithJavet("var m = new Map(); m.set(NaN, 'value'); m.has(NaN)");
    }

    @Test
    void testSetObjectKeys() {
        // Objects are compared by reference
        assertIntegerWithJavet("var m = new Map(); m.set({}, 'a'); m.set({}, 'b'); m.size");
        assertIntegerWithJavet("var m = new Map(); var obj = {}; m.set(obj, 'a'); m.set(obj, 'b'); m.size");
        assertStringWithJavet("var m = new Map(); var obj = {}; m.set(obj, 'a'); m.set(obj, 'b'); m.get(obj)");
        assertBooleanWithJavet("var m = new Map(); var obj = {}; m.set(obj, 'val'); m.has(obj)");
    }

    @Test
    void testSetPrimitiveKeys() {
        // Test setting various primitive types as keys
        assertIntegerWithJavet("var m = new Map(); m.set(1, 'num'); m.size");
        assertIntegerWithJavet("var m = new Map(); m.set('str', 'string'); m.size");
        assertIntegerWithJavet("var m = new Map(); m.set(true, 'bool'); m.size");
        assertIntegerWithJavet("var m = new Map(); m.set(false, 'bool'); m.size");
        assertIntegerWithJavet("var m = new Map(); m.set(null, 'null'); m.size");
        assertIntegerWithJavet("var m = new Map(); m.set(undefined, 'undef'); m.size");
    }

    @Test
    void testSetSymbolKeys() {
        // Symbols are unique
        assertIntegerWithJavet("var m = new Map(); var s = Symbol('test'); m.set(s, 'a'); m.set(s, 'b'); m.size");
        assertIntegerWithJavet("var m = new Map(); m.set(Symbol('a'), 1); m.set(Symbol('a'), 2); m.size");
        assertStringWithJavet("var m = new Map(); var s = Symbol('test'); m.set(s, 'value'); m.get(s)");
    }

    @Test
    void testSetUpdateValue() {
        // Updating existing key should replace value
        assertStringWithJavet("""
                var m = new Map();
                m.set('key', 'old');
                m.set('key', 'new');
                m.get('key')""");
        assertIntegerWithJavet("""
                var m = new Map();
                m.set(1, 'a');
                m.set(1, 'b');
                m.size""");
    }

    @Test
    void testSetZeroKeys() {
        // +0 and -0 are treated as the same key
        assertIntegerWithJavet("var m = new Map(); m.set(0, 'a'); m.set(-0, 'b'); m.size");
        assertStringWithJavet("var m = new Map(); m.set(0, 'a'); m.set(-0, 'b'); m.get(0)");
        assertStringWithJavet("var m = new Map(); m.set(0, 'a'); m.set(-0, 'b'); m.get(-0)");
        assertBooleanWithJavet("var m = new Map(); m.set(0, 'val'); m.has(-0)");
        assertBooleanWithJavet("var m = new Map(); m.set(-0, 'val'); m.has(0)");
    }

    @Test
    void testSimpleForOfMap() {
        // Simple for-of over Map without destructuring
        assertIntegerWithJavet("""
                var count = 0;
                for (var entry of new Map([[1, 'a'], [2, 'b']])) count++;
                count""");
    }

    @Test
    void testSizeAfterOperations() {
        // Size after various operations
        assertIntegerWithJavet("var m = new Map(); m.set(1, 'a'); m.size");
        assertIntegerWithJavet("var m = new Map(); m.set(1, 'a').set(2, 'b'); m.size");
        assertIntegerWithJavet("var m = new Map([[1, 'a'], [2, 'b']]); m.delete(1); m.size");
        assertIntegerWithJavet("var m = new Map([[1, 'a'], [2, 'b']]); m.clear(); m.size");
    }

    @Test
    void testSizeEmpty() {
        // Empty map size
        assertIntegerWithJavet("new Map().size");
    }

    @Test
    void testSizeIsGetter() {
        // size is a getter, not a method
        assertBooleanWithJavet("typeof Object.getOwnPropertyDescriptor(Map.prototype, 'size').get === 'function'");
        assertUndefinedWithJavet("Object.getOwnPropertyDescriptor(Map.prototype, 'size').set");
    }

    @Test
    void testStringHashCodes() {
        JSMap.KeyWrapper str1 = new JSMap.KeyWrapper(new JSString("test"));
        JSMap.KeyWrapper str2 = new JSMap.KeyWrapper(new JSString("test"));
        JSMap.KeyWrapper str3 = new JSMap.KeyWrapper(new JSString("other"));

        // Equal strings should have equal hash codes
        assertThat(str1.equals(str2)).isTrue();
        assertThat(str1.hashCode()).isEqualTo(str2.hashCode());

        // Different strings
        assertThat(str1.equals(str3)).isFalse();
    }

    @Test
    void testSymbolIteratorIsEntries() {
        // Map[Symbol.iterator] should be same as entries
        assertBooleanWithJavet("Map.prototype[Symbol.iterator] === Map.prototype.entries");
    }

    @Test
    void testSymbolIteratorIsNotKeys() {
        // Symbol.iterator should NOT be the same as keys
        assertBooleanWithJavet("Map.prototype[Symbol.iterator] !== Map.prototype.keys");
    }

    @Test
    void testSymbolIteratorIsNotValues() {
        // Symbol.iterator should NOT be the same as values (unlike Set)
        assertBooleanWithJavet("Map.prototype[Symbol.iterator] !== Map.prototype.values");
    }

    @Test
    void testSymbolIteratorPreservesInsertionOrder() {
        // Symbol.iterator should preserve insertion order (like entries)
        assertStringWithJavet("""
                var m = new Map();
                m.set(3, 'c');
                m.set(1, 'a');
                m.set(2, 'b');
                var it = m[Symbol.iterator]();
                var result = '';
                var entry;
                while (!(entry = it.next()).done) {
                  result += entry.value[0];
                }
                result""");
    }

    @Test
    void testSymbolIteratorProducesSameAsEntries() {
        // Symbol.iterator should produce the same results as entries
        assertStringWithJavet("""
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                var it = m[Symbol.iterator]();
                var result = '';
                var entry;
                while (!(entry = it.next()).done) {
                  result += entry.value[0] + entry.value[1];
                }
                result""");
    }

    @Test
    void testSymbolIteratorReturnsIterator() {
        // Symbol.iterator should return an iterator (because it's the same as entries)
        assertBooleanWithJavet("typeof new Map([[1, 'a']])[Symbol.iterator]() === 'object'");
        assertBooleanWithJavet("typeof new Map([[1, 'a']])[Symbol.iterator]().next === 'function'");
    }

    @Test
    void testSymbolIteratorWithEmptyMap() {
        // Symbol.iterator should work with empty map
        assertBooleanWithJavet("""
                var m = new Map();
                var it = m[Symbol.iterator]();
                var entry = it.next();
                entry.done""");
    }

    @Test
    void testSymbolIteratorWithMixedTypes() {
        // Symbol.iterator should handle mixed key types
        assertIntegerWithJavet("""
                var m = new Map();
                m.set(1, 'num');
                m.set('str', 'string');
                m.set(true, 'bool');
                m.set(null, 'null');
                m.set(undefined, 'undef');
                var it = m[Symbol.iterator]();
                var count = 0;
                while (!it.next().done) count++;
                count""");
    }

    @Test
    void testUndefinedVsNull() {
        // undefined and null are different keys
        assertIntegerWithJavet("var m = new Map(); m.set(null, 'a'); m.set(undefined, 'b'); m.size");
        assertStringWithJavet("var m = new Map(); m.set(null, 'a'); m.set(undefined, 'b'); m.get(null)");
        assertStringWithJavet("var m = new Map(); m.set(null, 'a'); m.set(undefined, 'b'); m.get(undefined)");
        assertBooleanWithJavet("var m = new Map(); m.set(null, 'a'); m.has(undefined)");
        assertBooleanWithJavet("var m = new Map(); m.set(undefined, 'a'); m.has(null)");
    }

    @Test
    public void testValues() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get values
        JSValue result = MapPrototype.values(context, map, new JSValue[]{});
        JSIterator values = result.asIterator().orElseThrow();
        JSArray jsArray = JSIteratorHelper.toArray(context, values);
        assertThat(jsArray.getLength()).isEqualTo(2);
        assertThat(jsArray.get(0).asString().map(JSString::value).orElse(null)).isEqualTo("value1");
        assertThat(jsArray.get(1).asString().map(JSString::value).orElse(null)).isEqualTo("value2");

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.values(context, emptyMap, new JSValue[]{});
        values = result.asIterator().orElseThrow();
        jsArray = JSIteratorHelper.toArray(context, values);
        assertThat(jsArray.getLength()).isEqualTo(0);

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.values(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    void testValuesIterationOrder() {
        // Iteration in insertion order
        assertStringWithJavet("""
                var result = '';
                for (var v of new Map([[3, 'c'], [1, 'a'], [2, 'b']]).values()) result += v;
                result""");
    }

    @Test
    void testValuesIterator() {
        // values() returns iterator
        assertBooleanWithJavet("typeof new Map().values() === 'object'");
        assertBooleanWithJavet("typeof new Map([[1, 'a']]).values().next === 'function'");
        assertStringWithJavet("var it = new Map([[1, 'a']]).values(); it.next().value");
    }

    @Test
    void testVerifyCorrectEntriesVisited() {
        // Verify which entries are actually visited
        assertStringWithJavet("""
                var result = '';
                var m = new Map([[1, 'a'], [2, 'b'], [3, 'c']]);
                m.forEach(function(v, k) {
                  result += k;
                  if(k === 2) m.delete(3);
                });
                result""");
    }

    @Test
    void testZeroHashCodeEquality() {
        // Create KeyWrappers for +0 and -0
        JSMap.KeyWrapper plusZero = new JSMap.KeyWrapper(new JSNumber(0.0));
        JSMap.KeyWrapper minusZero = new JSMap.KeyWrapper(new JSNumber(-0.0));

        // They should be equal according to SameValueZero
        assertThat(plusZero.equals(minusZero)).isTrue();
        assertThat(minusZero.equals(plusZero)).isTrue();

        // CRITICAL: They MUST have the same hashCode for HashMap/HashSet to work
        assertThat(plusZero.hashCode()).isEqualTo(minusZero.hashCode());
    }

    @Test
    void testZeroInRealHashSet() {
        // This is the real-world test: Does it work in an actual HashSet?
        java.util.HashSet<JSMap.KeyWrapper> set = new java.util.HashSet<>();

        JSMap.KeyWrapper plusZero = new JSMap.KeyWrapper(new JSNumber(0.0));
        JSMap.KeyWrapper minusZero = new JSMap.KeyWrapper(new JSNumber(-0.0));

        // Add +0
        assertThat(set.add(plusZero)).isTrue(); // First add succeeds

        // Try to add -0 - should fail because +0 is already present
        assertThat(set.add(minusZero)).isFalse(); // Should return false (duplicate)

        // Set should have size 1
        assertThat(set.size()).isEqualTo(1);

        // Set should contain both +0 and -0 (they're the same)
        assertThat(set.contains(plusZero)).isTrue();
        assertThat(set.contains(minusZero)).isTrue();
    }

    @Test
    void verifyFunctionProperties() {
        // Since they're the same object, they have the same properties
        assertBooleanWithJavet("""
                // Same name
                Map.prototype[Symbol.iterator].name === Map.prototype.entries.name &&
                // Same length
                Map.prototype[Symbol.iterator].length === Map.prototype.entries.length &&
                // Both are 'entries' with length 0
                Map.prototype.entries.name === 'entries' &&
                Map.prototype.entries.length === 0""");
    }
}
