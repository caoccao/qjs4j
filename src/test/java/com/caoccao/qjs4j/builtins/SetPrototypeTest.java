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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Set.prototype methods.
 */
public class SetPrototypeTest extends BaseJavetTest {

    @Test
    void demonstrateBugFix() {
        // This is the exact test case from SetPrototypeTest.testForEachEdgeCases()
        // that was failing before the fix

        // Before fix: returned 1 (only visited the original element)
        // After fix: returns 6 (visited original + 5 newly added elements)
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var s = new Set([1]);
                        s.forEach(function(v) {
                          if(count < 5) s.add(count + 10);
                          count++;
                        });
                        count""");

        // The test expects 6 because:
        // 1. First iteration: visits 1, adds 10, count becomes 1
        // 2. Second iteration: visits 10, adds 11, count becomes 2
        // 3. Third iteration: visits 11, adds 12, count becomes 3
        // 4. Fourth iteration: visits 12, adds 13, count becomes 4
        // 5. Fifth iteration: visits 13, adds 14, count becomes 5
        // 6. Sixth iteration: visits 14, count becomes 6 (condition fails, no add)
    }

    @Test
    void demonstrateDuplicatesNotRevisited() {
        // Adding a duplicate element should not cause it to be visited again
        // Always add 1 (which is already in the set)
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var s = new Set([1, 2, 3]);
                        s.forEach(function(v) {
                          s.add(1);
                          count++;
                        });
                        count""");
        // Result should be 3 (not more), showing duplicates don't add visits
    }

    @Test
    void demonstrateInsertionOrderPreserved() {
        // Verify that newly added elements are visited in insertion order
        assertStringWithJavet(
                """
                        var result = '';
                        var s = new Set(['a']);
                        s.forEach(function(v) {
                          result += v;
                          if(v === 'a') { s.add('b'); s.add('c'); }
                          if(v === 'b') { s.add('d'); }
                        });
                        result""");
        // Result should be 'abcd' showing all elements were visited in order
    }

    @Test
    public void testAdd() {
        JSSet set = new JSSet();

        // Normal case: add new value
        JSValue result = SetPrototype.add(context, set, new JSValue[]{new JSString("value1")});
        assertThat(result).isSameAs(set);
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.setHas(new JSString("value1"))).isTrue();

        // Normal case: add duplicate value (should not increase size)
        result = SetPrototype.add(context, set, new JSValue[]{new JSString("value1")});
        assertThat(result).isSameAs(set);
        assertThat(set.size()).isEqualTo(1);

        // Normal case: add different value
        result = SetPrototype.add(context, set, new JSValue[]{new JSNumber(42)});
        assertThat(result).isSameAs(set);
        assertThat(set.size()).isEqualTo(2);
        assertThat(set.setHas(new JSNumber(42))).isTrue();

        // Edge case: no arguments (adds undefined)
        result = SetPrototype.add(context, set, new JSValue[]{});
        assertThat(result).isSameAs(set);
        assertThat(set.size()).isEqualTo(3); // Should add undefined, increasing size

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.add(context, new JSString("not set"), new JSValue[]{new JSString("value")}));
        assertPendingException(context);
    }

    @Test
    void testAddDuringIteration() {
        // Add entries during iteration - they should be visited
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  if(v === 2) s.add(4);
                  count++;
                });
                count""");
    }

    @Test
    void testAddEdgeCases() {
        // Add primitive values
        assertIntegerWithJavet("var s = new Set(); s.add(1); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add('str'); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add(true); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add(false); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add(null); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add(undefined); s.size");

        // Add returns the Set itself (for chaining)
        assertBooleanWithJavet("var s = new Set(); s.add(1) === s");
        assertIntegerWithJavet("var s = new Set(); s.add(1).add(2).add(3).size");

        // Adding duplicate values
        assertIntegerWithJavet("var s = new Set(); s.add(1); s.add(1); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add('a'); s.add('a'); s.add('a'); s.size");

        // Zero values: +0 and -0 are treated as the same
        assertIntegerWithJavet("var s = new Set(); s.add(0); s.add(-0); s.size");
        assertBooleanWithJavet("var s = new Set(); s.add(0); s.has(-0)");
        assertBooleanWithJavet("var s = new Set(); s.add(-0); s.has(0)");

        // NaN handling: all NaN values are treated as the same
        assertIntegerWithJavet("var s = new Set(); s.add(NaN); s.add(NaN); s.size");
        assertBooleanWithJavet("var s = new Set(); s.add(NaN); s.has(NaN)");
        assertIntegerWithJavet("var s = new Set(); s.add(0/0); s.add(NaN); s.size");

        // Add objects (objects are compared by reference)
        assertIntegerWithJavet("var s = new Set(); s.add({}); s.add({}); s.size");
        assertIntegerWithJavet("var s = new Set(); var obj = {}; s.add(obj); s.add(obj); s.size");
        assertBooleanWithJavet("var s = new Set(); var obj = {}; s.add(obj); s.has(obj)");

        // Add arrays
        assertIntegerWithJavet("var s = new Set(); s.add([1,2]); s.add([1,2]); s.size");
        assertIntegerWithJavet("var s = new Set(); var arr = [1,2]; s.add(arr); s.add(arr); s.size");

        // Add symbols
        assertIntegerWithJavet("var s = new Set(); var sym = Symbol('test'); s.add(sym); s.add(sym); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add(Symbol('a')); s.add(Symbol('a')); s.size");
    }

    @Test
    void testAdvancedSetMethods() {
        assertThat(context.eval("""
                (() => {
                  const a = new Set([1, 2, 3]);
                  const b = new Set([3, 4]);
                  const union = [...a.union(b)].join(',');
                  const intersection = [...a.intersection(b)].join(',');
                  const difference = [...a.difference(b)].join(',');
                  const symmetricDifference = [...a.symmetricDifference(b)].join(',');
                  const d1 = Object.getOwnPropertyDescriptor(Set.prototype, 'union');
                  const d2 = Object.getOwnPropertyDescriptor(Set.prototype, 'isSubsetOf');
                  return union === '1,2,3,4'
                    && intersection === '3'
                    && difference === '1,2'
                    && symmetricDifference === '1,2,4'
                    && a.isSubsetOf(new Set([1, 2, 3, 4]))
                    && !a.isSubsetOf(new Set([1, 2]))
                    && a.isSupersetOf(new Set([1, 2]))
                    && !a.isSupersetOf(new Set([1, 2, 5]))
                    && a.isDisjointFrom(new Set([4, 5]))
                    && !a.isDisjointFrom(new Set([2, 5]))
                    && a.size === 3
                    && typeof d1.value === 'function'
                    && d1.value.length === 1
                    && d1.writable === true
                    && d1.enumerable === false
                    && d1.configurable === true
                    && typeof d2.value === 'function'
                    && d2.value.length === 1;
                })()
                """).toJavaObject()).isEqualTo(true);
    }

    @Test
    void testAdvancedSetMethodsWithSetLikeObject() {
        assertThat(context.eval("""
                (() => {
                  const a = new Set([1, 2, 3]);
                  const setLike = {
                    size: 2,
                    has(v) { return v === 2 || v === 5; },
                    keys() { return [2, 5][Symbol.iterator](); }
                  };
                  return [...a.union(setLike)].join(',') === '1,2,3,5'
                    && [...a.intersection(setLike)].join(',') === '2'
                    && [...a.difference(setLike)].join(',') === '1,3'
                    && [...a.symmetricDifference(setLike)].join(',') === '1,3,5'
                    && a.isSupersetOf(setLike) === false
                    && a.isSubsetOf(setLike) === false
                    && a.isDisjointFrom(setLike) === false;
                })()
                """).toJavaObject()).isEqualTo(true);
    }

    @Test
    void testBothWorkCorrectly() {
        // Despite being the same object, both keys() and values() work
        assertStringWithJavet(
                """
                        var s = new Set([1, 2, 3]);
                        var keysStr = '';
                        for (var k of s.keys()) keysStr += k;
                        var valuesStr = '';
                        for (var v of s.values()) valuesStr += v;
                        keysStr === valuesStr ? 'same' : 'different'""");
    }

    @Test
    public void testClear() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));

        // Normal case: clear set
        JSValue result = SetPrototype.clear(context, set, new JSValue[]{});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(set.size()).isEqualTo(0);

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.clear(context, new JSString("not set"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    void testClearEdgeCases() {
        // Clear empty set
        assertIntegerWithJavet("var s = new Set(); s.clear(); s.size");

        // Clear set with values
        assertIntegerWithJavet("var s = new Set([1,2,3]); s.clear(); s.size");

        // Clear returns undefined
        assertUndefinedWithJavet("var s = new Set([1,2,3]); s.clear()");

        // Clear multiple times
        assertIntegerWithJavet("var s = new Set([1,2,3]); s.clear(); s.clear(); s.size");

        // Add after clear
        assertIntegerWithJavet("var s = new Set([1,2,3]); s.clear(); s.add(4); s.size");
        assertBooleanWithJavet("var s = new Set([1,2,3]); s.clear(); s.add(4); s.has(4)");
        assertBooleanWithJavet("var s = new Set([1,2,3]); s.clear(); s.add(4); s.has(1)");
    }

    @Test
    void testComplexScenarios() {
        // Multiple operations with +0 and -0
        assertIntegerWithJavet(
                """
                        var s = new Set();
                        s.add(0);
                        s.add(-0);
                        s.add(1);
                        s.add(-1);
                        s.size""");

        // Verify all values are present
        assertBooleanWithJavet(
                """
                        var s = new Set([0, 1, -1]);
                        s.has(0) && s.has(-0) && s.has(1) && s.has(-1)""");

        // Clear and re-add with different zero
        assertIntegerWithJavet(
                """
                        var s = new Set();
                        s.add(0);
                        s.clear();
                        s.add(-0);
                        s.size""");
    }

    @Test
    public void testDelete() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));

        // Normal case: delete existing value
        JSValue result = SetPrototype.delete(context, set, new JSValue[]{new JSString("value1")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.setHas(new JSString("value1"))).isFalse();

        // Normal case: delete non-existing value
        result = SetPrototype.delete(context, set, new JSValue[]{new JSString("nonexistent")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);
        assertThat(set.size()).isEqualTo(1);

        // Normal case: no arguments
        result = SetPrototype.delete(context, set, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.delete(context, new JSString("not set"), new JSValue[]{new JSString("value")}));
        assertPendingException(context);
    }

    @Test
    void testDeleteAll() {
        // Delete all entries during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var visited = [];
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  visited.push(v);
                  s.delete(1);
                  s.delete(2);
                  s.delete(3);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteAndCheckSetSize() {
        // Check set size after deletion during forEach
        assertIntegerWithJavet("""
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  if(v === 2) s.delete(3);
                });
                s.size""");
    }

    @Test
    void testDeleteAndReAdd() {
        // Delete and re-add an entry
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  if(v === 1) {
                    s.delete(3);
                    s.add(3);
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
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  s.delete(v);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteDuringIteration() {
        // Delete an entry that hasn't been visited yet
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  if(v === 2) s.delete(3);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteEdgeCases() {
        // Delete from empty set
        assertBooleanWithJavet("var s = new Set(); s.delete(1)");

        // Delete existing value
        assertBooleanWithJavet("var s = new Set([1,2,3]); s.delete(2)");
        assertIntegerWithJavet("var s = new Set([1,2,3]); s.delete(2); s.size");

        // Delete non-existing value
        assertBooleanWithJavet("var s = new Set([1,2,3]); s.delete(5)");

        // Delete same value twice
        assertBooleanWithJavet("var s = new Set([1,2,3]); s.delete(2); s.delete(2)");

        // Delete with different types
        assertBooleanWithJavet("var s = new Set([1,'1',true]); s.delete(1)");
        assertBooleanWithJavet("var s = new Set([1,'1',true]); s.delete('1')");
        assertBooleanWithJavet("var s = new Set([1,'1',true]); s.delete(true)");

        // Delete NaN
        assertBooleanWithJavet("var s = new Set(); s.add(NaN); s.delete(NaN)");
        assertIntegerWithJavet("var s = new Set(); s.add(NaN); s.delete(NaN); s.size");

        // Delete undefined and null
        assertBooleanWithJavet("var s = new Set(); s.add(undefined); s.delete(undefined)");
        assertBooleanWithJavet("var s = new Set(); s.add(null); s.delete(null)");

        // Delete object by reference
        assertBooleanWithJavet("var s = new Set(); var obj = {}; s.add(obj); s.delete(obj)");
        assertBooleanWithJavet("var s = new Set(); s.add({}); s.delete({})");

        // Zero values
        assertBooleanWithJavet("var s = new Set(); s.add(0); s.has(-0)");
        assertBooleanWithJavet("var s = new Set(); s.add(-0); s.has(0)");
    }

    @Test
    void testDeleteFromEmptySet() {
        // Delete from empty set (no-op)
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set();
                s.forEach(function(v) {
                  s.delete(1);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteMultipleDuringIteration() {
        // Delete multiple entries during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([1, 2, 3, 4, 5]);
                s.forEach(function(v) {
                  if(v === 1) {
                    s.delete(3);
                    s.delete(4);
                  }
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteNonExistent() {
        // Delete non-existent value during iteration
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([1, 2]);
                s.forEach(function(v) {
                  s.delete(999);
                  count++;
                });
                count""");
    }

    @Test
    void testDeletePreviousEntry() {
        // Delete an entry that was already visited
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  if(v === 3) s.delete(1);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteWithComplexValues() {
        // Delete with object values
        assertIntegerWithJavet("""
                var count = 0;
                var obj1 = {id: 1};
                var obj2 = {id: 2};
                var obj3 = {id: 3};
                var s = new Set([obj1, obj2, obj3]);
                s.forEach(function(v) {
                  if(v === obj2) s.delete(obj3);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteWithNullValue() {
        // Test with a set that has null as a value
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([null, 2, 3]);
                s.forEach(function(v) {
                  if(v === null) s.delete(2);
                  count++;
                });
                count""");
    }

    @Test
    void testDeleteWithUndefinedValue() {
        // Test with a set that has undefined as a value
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([undefined, 2, 3]);
                s.forEach(function(v) {
                  if(v === undefined) s.delete(2);
                  count++;
                });
                count""");
    }

    @Test
    void testEntriesIsDifferent() {
        // entries() should be a DIFFERENT function from keys/values
        assertBooleanWithJavet("Set.prototype.entries !== Set.prototype.values");
        assertBooleanWithJavet("Set.prototype.entries !== Set.prototype.keys");
    }

    @Test
    public void testForEach() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));

        // Normal case: forEach with callback
        final StringBuilder result = new StringBuilder();
        JSFunction callback = new JSNativeFunction("testCallback", 3, (childContext, thisArg, args) -> {
            String value = args[0].asString().map(JSString::value).orElseThrow();
            result.append(value).append(",");
            return JSUndefined.INSTANCE;
        });

        JSValue forEachResult = SetPrototype.forEach(context, set, new JSValue[]{callback});
        assertThat(forEachResult).isEqualTo(JSUndefined.INSTANCE);
        // Note: Order might vary, but both values should be present
        String resultStr = result.toString();
        assertThat(resultStr).contains("value1");
        assertThat(resultStr).contains("value2");

        // Edge case: no callback function
        assertTypeError(SetPrototype.forEach(context, set, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-function callback
        assertTypeError(SetPrototype.forEach(context, set, new JSValue[]{new JSString("not function")}));
        assertPendingException(context);

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.forEach(context, new JSString("not set"), new JSValue[]{callback}));
        assertPendingException(context);
    }

    @Test
    void testForEachAddAtEnd() {
        // Test adding elements at the end during iteration
        assertStringWithJavet("var result = ''; var s = new Set([1,2]); s.forEach(function(v) { result += v; if(v < 3) s.add(v + 2); }); result");
    }

    @Test
    void testForEachAddDuringFirstIteration() {
        // Test that element added in first iteration is visited
        assertStringWithJavet("var result = ''; var s = new Set(['a']); s.forEach(function(v) { if(v === 'a') s.add('b'); result += v; }); result");
    }

    @Test
    void testForEachAddsDifferentTypes() {
        // Test adding different types during iteration
        assertIntegerWithJavet("var count = 0; var s = new Set([1]); s.forEach(function(v) { if(count === 0) { s.add('string'); s.add(true); } count++; }); count");
    }

    @Test
    void testForEachBasic() {
        // Basic forEach test
        assertIntegerWithJavet("var count = 0; new Set([1,2,3]).forEach(function(v, k, s) { count++; }); count");
    }

    @Test
    void testForEachCanModifySetDuringIteration() {
        // Test that we can both add and the set reflects changes
        assertIntegerWithJavet(
                """
                        var s = new Set([1, 2]);
                        s.forEach(function(v) {
                          if(v === 1) s.add(3);
                        });
                        s.size""");
    }

    @Test
    void testForEachChainedAdds() {
        // Test where each iteration adds the next element
        assertIntegerWithJavet("var s = new Set([1]); var count = 0; s.forEach(function(v) { if(v < 5) s.add(v + 1); count++; }); count");
    }

    @Test
    void testForEachComplexDynamicGrowth() {
        // Complex test: each iteration adds multiple elements if under limit
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var s = new Set([0]);
                        s.forEach(function(v) {
                          if(count < 3) {
                            s.add(count * 10 + 1);
                            s.add(count * 10 + 2);
                          }
                          count++;
                        });
                        count""");
    }

    @Test
    void testForEachDuplicateAddDoesNotIncreaseVisits() {
        // Adding a duplicate should not cause additional visits
        // Always add 1 (duplicate)
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var s = new Set([1, 2]);
                        s.forEach(function(v) {
                          s.add(1);
                          count++;
                        });
                        count""");
    }

    @Test
    void testForEachDynamicAddition() {
        // Basic test from the original failing case
        assertIntegerWithJavet("var count = 0; var s = new Set([1]); s.forEach(function(v) { if(count < 5) s.add(count + 10); count++; }); count");
    }

    @Test
    void testForEachEdgeCases() {
        // forEach with empty set - returns undefined
        assertUndefinedWithJavet("new Set().forEach(function() {})");

        // forEach callback receives value, key (same as value), and set
        assertIntegerWithJavet("var count = 0; new Set([1,2,3]).forEach(function(v, k, s) { count++; }); count");
        assertBooleanWithJavet("var result = true; new Set([1,2,3]).forEach(function(v, k) { result = result && (v === k); }); result");

        // forEach with thisArg
        assertIntegerWithJavet("var obj = {count: 0}; new Set([1,2,3]).forEach(function() { this.count++; }, obj); obj.count");

        // forEach returns undefined
        assertUndefinedWithJavet("new Set([1,2,3]).forEach(function() {})");

        // forEach doesn't visit values added during iteration (implementation dependent)
        // Note: behavior may vary by implementation
        assertIntegerWithJavet("var count = 0; var s = new Set([1]); s.forEach(function(v) { if(count < 5) s.add(count + 10); count++; }); count");

        // forEach visits values in insertion order
        assertStringWithJavet("var result = ''; new Set([3,1,2]).forEach(function(v) { result += v; }); result");
    }

    @Test
    void testForEachEmptySet() {
        // Test with empty set
        assertIntegerWithJavet("var count = 0; var s = new Set(); s.forEach(function() { count++; }); count");
    }

    @Test
    void testForEachEmptySetDoesNotInvokeCallback() {
        // Empty set should not invoke the callback at all
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var s = new Set();
                        s.forEach(function() { count++; });
                        count""");
    }

    @Test
    void testForEachMultipleAdds() {
        // Test multiple adds in single iteration
        assertIntegerWithJavet("var count = 0; var s = new Set([1]); s.forEach(function(v) { if(count === 0) { s.add(2); s.add(3); } count++; }); count");
    }

    @Test
    void testForEachNewElementsVisitedInOrder() {
        // Newly added elements should also be visited in insertion order
        assertStringWithJavet(
                """
                        var result = '';
                        var s = new Set(['a']);
                        s.forEach(function(v) {
                          result += v;
                          if(v === 'a') { s.add('b'); s.add('c'); }
                          if(v === 'b') { s.add('d'); }
                        });
                        result""");
    }

    @Test
    void testForEachNoAddition() {
        // Test that normal forEach still works
        assertIntegerWithJavet("var count = 0; new Set([1,2,3]).forEach(function() { count++; }); count");
    }

    @Test
    void testForEachReceivesSetAsThirdArg() {
        // The third argument should be the Set itself
        assertBooleanWithJavet(
                """
                        var isSet = false;
                        var s = new Set([1]);
                        s.forEach(function(v, k, set) {
                          isSet = (set === s);
                        });
                        isSet""");
    }

    @Test
    void testForEachReturnsUndefined() {
        // forEach should return undefined
        assertUndefinedWithJavet("new Set([1,2,3]).forEach(function() {})");
    }

    @Test
    void testForEachThisArg() {
        // Test that thisArg is properly passed - use increment which works
        assertIntegerWithJavet("var obj = {count: 0}; var s = new Set([1,2,3]); s.forEach(function(v) { this.count++; }, obj); obj.count");
    }

    @Test
    void testForEachValueAndKeyAreSame() {
        // In Set.forEach, the first and second arguments are both the value
        assertBooleanWithJavet(
                """
                        var same = true;
                        var s = new Set([1, 2, 3]);
                        s.forEach(function(value, key) {
                          if(value !== key) same = false;
                        });
                        same""");
    }

    @Test
    void testForEachValueEquality() {
        // Verify value and key are the same
        assertBooleanWithJavet("var same = true; var s = new Set([1,2,3]); s.forEach(function(v, k) { if(v !== k) same = false; }); same");
    }

    @Test
    void testForEachVisitsElementsInInsertionOrder() {
        // Elements should be visited in the order they were inserted
        assertStringWithJavet(
                """
                        var result = '';
                        var s = new Set([3, 1, 2]);
                        s.forEach(function(v) { result += v; });
                        result""");
    }

    @Test
    void testForEachVisitsNewlyAddedElements() {
        // QuickJS behavior: forEach continues to visit elements added during iteration
        // This is the core bug that was fixed
        assertIntegerWithJavet(
                """
                        var count = 0; var s = new Set([1]);
                        s.forEach(function(v) {
                          if(count < 5) s.add(count + 10);
                          count++;
                        });
                        count""");
    }

    @Test
    void testForEachWithDuplicateAdd() {
        // Test adding duplicate values (should not increase count)
        assertIntegerWithJavet("var count = 0; var s = new Set([1,2]); s.forEach(function(v) { s.add(1); count++; }); count");
    }

    @Test
    void testForEachWithDynamicAddition() {
        // This test verifies that forEach continues to visit elements added during iteration
        // Expected: count = 6 (visits original element + 5 newly added elements)
        // The callback adds elements when count < 5:
        // - iteration 0: count=0, visits value=1, adds 10, count becomes 1
        // - iteration 1: count=1, visits value=10, adds 11, count becomes 2
        // - iteration 2: count=2, visits value=11, adds 12, count becomes 3
        // - iteration 3: count=3, visits value=12, adds 13, count becomes 4
        // - iteration 4: count=4, visits value=13, adds 14, count becomes 5
        // - iteration 5: count=5, visits value=14, doesn't add, count becomes 6
        assertIntegerWithJavet("var count = 0; var s = new Set([1]); s.forEach(function(v) { if(count < 5) s.add(count + 10); count++; }); count");
    }

    @Test
    void testForEachWithValueKeyCheck() {
        // Verify that value and key are the same in Set.forEach
        assertBooleanWithJavet("var result = true; new Set([1,2,3]).forEach(function(v, k) { result = result && (v === k); }); result");
    }

    @Test
    void testForOfUsesSymbolIterator() {
        // for-of loops use Symbol.iterator, which is the same as values()
        assertStringWithJavet(
                """
                        var s = new Set(['a', 'b', 'c']);
                        var result = '';
                        for (var item of s) result += item;
                        result""");
    }

    @Test
    void testFunctionLength() {
        // Both should have length 0
        assertIntegerWithJavet("Set.prototype.values.length");
        assertIntegerWithJavet("Set.prototype.keys.length");
    }

    @Test
    void testFunctionName() {
        // The function should be named "values" (not "keys")
        assertStringWithJavet("Set.prototype.values.name");

        // Since keys is the same object, it should also have name "values"
        assertStringWithJavet("Set.prototype.keys.name");
    }

    @Test
    void testFunctionNameIsValues() {
        // Even though the property is called "keys", the function name is "values"
        assertStringWithJavet("Set.prototype.keys.name");
        assertStringWithJavet("Set.prototype.values.name");
    }

    @Test
    public void testGetSize() {
        JSSet set = new JSSet();

        // Normal case: empty set
        JSValue result = SetPrototype.getSize(context, set, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(0.0));

        // Normal case: set with values
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));
        result = SetPrototype.getSize(context, set, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(2.0));

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.getSize(context, new JSString("not set"), new JSValue[]{}));
        assertPendingException(context);

        assertIntegerWithJavet(
                "new Set().size",
                "var a = new Set(); a.add('a'); a.size");
    }

    @Test
    public void testHas() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));

        // Normal case: existing value
        JSValue result = SetPrototype.has(context, set, new JSValue[]{new JSString("value1")});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: non-existing value
        result = SetPrototype.has(context, set, new JSValue[]{new JSString("value2")});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Normal case: no arguments
        result = SetPrototype.has(context, set, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.has(context, new JSString("not set"), new JSValue[]{new JSString("value")}));
        assertPendingException(context);
    }

    @Test
    void testHasEdgeCases() {
        // Has in empty set
        assertBooleanWithJavet("var s = new Set(); s.has(1)");

        // Has with various types
        assertBooleanWithJavet("var s = new Set([1,2,3]); s.has(2)");
        assertBooleanWithJavet("var s = new Set([1,2,3]); s.has(5)");
        assertBooleanWithJavet("var s = new Set(['a','b']); s.has('a')");
        assertBooleanWithJavet("var s = new Set(['a','b']); s.has('c')");

        // Type matters
        assertBooleanWithJavet("var s = new Set([1]); s.has('1')");
        assertBooleanWithJavet("var s = new Set(['1']); s.has(1)");

        // NaN
        assertBooleanWithJavet("var s = new Set(); s.add(NaN); s.has(NaN)");
        assertBooleanWithJavet("var s = new Set(); s.add(0/0); s.has(NaN)");

        // Undefined and null
        assertBooleanWithJavet("var s = new Set(); s.add(undefined); s.has(undefined)");
        assertBooleanWithJavet("var s = new Set(); s.add(null); s.has(null)");
        assertBooleanWithJavet("var s = new Set(); s.add(null); s.has(undefined)");

        // Objects by reference
        assertBooleanWithJavet("var s = new Set(); var obj = {}; s.add(obj); s.has(obj)");
        assertBooleanWithJavet("var s = new Set(); s.add({}); s.has({})");

        // Zero values
        assertBooleanWithJavet("var s = new Set(); s.add(0); s.has(-0)");
        assertBooleanWithJavet("var s = new Set(); s.add(-0); s.has(0)");
    }

    @Test
    void testIterationBehavior() {
        // Verify that keys() produces the same iteration as values()
        assertStringWithJavet(
                """
                        var s = new Set([1, 2, 3]);
                        var keysResult = '';
                        var valuesResult = '';
                        for (var k of s.keys()) keysResult += k;
                        for (var v of s.values()) valuesResult += v;
                        keysResult === valuesResult ? 'same' : 'different'""");
    }

    @Test
    void testIteratorEquality() {
        // All three should be the exact same function
        assertBooleanWithJavet(
                "Set.prototype.keys === Set.prototype.values && " +
                        "Set.prototype.values === Set.prototype[Symbol.iterator]"
        );
    }

    @Test
    void testIteratorMethods() {
        // values()
        assertBooleanWithJavet("typeof new Set().values() === 'object'");
        assertBooleanWithJavet("typeof new Set([1,2,3]).values().next === 'function'");

        // keys() is same as values() for Set
        assertBooleanWithJavet("Set.prototype.keys === Set.prototype.values");

        // entries()
        assertBooleanWithJavet("typeof new Set().entries() === 'object'");
        assertBooleanWithJavet("typeof new Set([1,2,3]).entries().next === 'function'");

        // @@iterator is same as values
        assertBooleanWithJavet("Set.prototype[Symbol.iterator] === Set.prototype.values");
    }

    @Test
    void testKeysBehavesLikeValues() {
        // Even though they're the same object, verify they work correctly
        assertBooleanWithJavet("typeof new Set().keys() === 'object'");
        assertBooleanWithJavet("typeof new Set([1,2,3]).keys().next === 'function'");

        // Calling keys() should produce an iterator
        assertBooleanWithJavet("var it = new Set([1,2,3]).keys(); it.next().value === 1");
    }

    @Test
    void testKeysIsValues() {
        // Set.prototype.keys should be the exact same function object as values
        assertBooleanWithJavet("Set.prototype.keys === Set.prototype.values");
    }

    @Test
    void testLargeSet() {
        // Add many values
        assertIntegerWithJavet("var s = new Set(); for(var i = 0; i < 1000; i++) s.add(i); s.size");
        assertBooleanWithJavet("var s = new Set(); for(var i = 0; i < 1000; i++) s.add(i); s.has(500)");
        assertBooleanWithJavet("var s = new Set(); for(var i = 0; i < 1000; i++) s.add(i); s.has(1000)");

        // Delete from large set
        assertIntegerWithJavet("var s = new Set(); for(var i = 0; i < 1000; i++) s.add(i); s.delete(500); s.size");
        assertBooleanWithJavet("var s = new Set(); for(var i = 0; i < 1000; i++) s.add(i); s.delete(500); s.has(500)");
    }

    @Test
    void testMapComparison() {
        // For comparison: Map.prototype.keys and Map.prototype.values should be DIFFERENT
        // (This is different from Set where keys === values)
        assertBooleanWithJavet("Map.prototype.keys !== Map.prototype.values");
    }

    @Test
    void testMapDeleteZeroKeys() {
        // Deleting -0 from Map with +0 should succeed
        assertBooleanWithJavet("var m = new Map(); m.set(0, 'a'); m.delete(-0)");
        assertIntegerWithJavet("var m = new Map(); m.set(0, 'a'); m.delete(-0); m.size");

        // Deleting +0 from Map with -0 should succeed
        assertBooleanWithJavet("var m = new Map(); m.set(-0, 'a'); m.delete(0)");
        assertIntegerWithJavet("var m = new Map(); m.set(-0, 'a'); m.delete(0); m.size");
    }

    @Test
    void testMapHasZeroKeys() {
        // Map with +0 should have -0
        assertBooleanWithJavet("var m = new Map(); m.set(0, 'a'); m.has(-0)");

        // Map with -0 should have +0
        assertBooleanWithJavet("var m = new Map(); m.set(-0, 'a'); m.has(0)");
    }

    @Test
    void testMapKeysIsNotValues() {
        // For comparison: Map is different (keys !== values)
        assertBooleanWithJavet("Map.prototype.keys !== Map.prototype.values");
    }

    @Test
    void testMapSetZeroKeys() {
        // Map should treat +0 and -0 as the same key
        assertIntegerWithJavet("var m = new Map(); m.set(0, 'a'); m.set(-0, 'b'); m.size");
        assertStringWithJavet("var m = new Map(); m.set(0, 'a'); m.set(-0, 'b'); m.get(0)");
        assertStringWithJavet("var m = new Map(); m.set(0, 'a'); m.set(-0, 'b'); m.get(-0)");
    }

    @Test
    void testMixedTypes() {
        // Set can contain mixed types
        assertIntegerWithJavet("new Set([1, 'str', true, null, undefined, {}]).size");
        assertBooleanWithJavet("var s = new Set([1, 'str', true, null, undefined]); s.has(1)");
        assertBooleanWithJavet("var s = new Set([1, 'str', true, null, undefined]); s.has('str')");
        assertBooleanWithJavet("var s = new Set([1, 'str', true, null, undefined]); s.has(true)");
        assertBooleanWithJavet("var s = new Set([1, 'str', true, null, undefined]); s.has(null)");
        assertBooleanWithJavet("var s = new Set([1, 'str', true, null, undefined]); s.has(undefined)");
    }

    @Test
    void testModifyingOneAffectsBoth() {
        // Since they're the same object, modifying one affects both
        // Restore
        assertBooleanWithJavet(
                """
                        var original = Set.prototype.keys;
                        Set.prototype.keys = function() { return 'modified'; };
                        var result = Set.prototype.keys === Set.prototype.values;
                        Set.prototype.keys = original;
                        result""");
    }

    @Test
    void testNaNHandling() {
        // NaN values should also be treated as equal (SameValueZero)
        assertIntegerWithJavet("var s = new Set(); s.add(NaN); s.add(NaN); s.size");
        assertBooleanWithJavet("var s = new Set(); s.add(NaN); s.has(NaN)");
        assertIntegerWithJavet("var s = new Set(); s.add(0/0); s.add(NaN); s.size");

        // Same for Map
        assertIntegerWithJavet("var m = new Map(); m.set(NaN, 'a'); m.set(NaN, 'b'); m.size");
        assertStringWithJavet("var m = new Map(); m.set(NaN, 'a'); m.set(NaN, 'b'); m.get(NaN)");
    }

    @Test
    void testNoDelete() {
        // Control test: no deletion
        assertIntegerWithJavet("""
                var count = 0;
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  count++;
                });
                count""");
    }

    @Test
    void testSetAddZeroValues() {
        // Adding +0 and then -0 should result in size 1
        assertIntegerWithJavet("var s = new Set(); s.add(0); s.add(-0); s.size");

        // Adding -0 and then +0 should result in size 1
        assertIntegerWithJavet("var s = new Set(); s.add(-0); s.add(0); s.size");

        // Set with +0 should have -0
        assertBooleanWithJavet("var s = new Set(); s.add(0); s.has(-0)");

        // Set with -0 should have +0
        assertBooleanWithJavet("var s = new Set(); s.add(-0); s.has(0)");
    }

    @Test
    void testSetChaining() {
        // add returns the set for chaining
        assertIntegerWithJavet("new Set().add(1).add(2).add(3).size");
        assertBooleanWithJavet("var s = new Set(); s.add(1).add(2).add(3).has(2)");

        // delete doesn't chain (returns boolean)
        assertBooleanWithJavet("new Set([1,2,3]).delete(2)");

        // clear doesn't chain (returns undefined)
        assertUndefinedWithJavet("new Set([1,2,3]).clear()");
    }

    @Test
    void testSetConstructorWithIterable() {
        // Construct from array
        assertIntegerWithJavet("new Set([1,2,3]).size");
        assertIntegerWithJavet("new Set([1,2,2,3,3,3]).size");
        assertBooleanWithJavet("new Set([1,2,3]).has(2)");

        // Empty iterable
        assertIntegerWithJavet("new Set([]).size");

        // No argument
        assertIntegerWithJavet("new Set().size");
    }

    @Test
    void testSetDeleteZeroValues() {
        // Deleting -0 from Set with +0 should succeed
        assertBooleanWithJavet("var s = new Set(); s.add(0); s.delete(-0)");
        assertIntegerWithJavet("var s = new Set(); s.add(0); s.delete(-0); s.size");

        // Deleting +0 from Set with -0 should succeed
        assertBooleanWithJavet("var s = new Set(); s.add(-0); s.delete(0)");
        assertIntegerWithJavet("var s = new Set(); s.add(-0); s.delete(0); s.size");
    }

    @Test
    void testSetEquality() {
        // Two sets with same values are not equal
        assertBooleanWithJavet("new Set([1,2,3]) === new Set([1,2,3])");

        // Same set instance is equal to itself
        assertBooleanWithJavet("var s = new Set([1,2,3]); s === s");
    }

    @Test
    void testSetIteratorMutationSemantics() {
        assertStringWithJavet("""
                const s = new Set([1, 2, 3]);
                const it = s.values();
                const out = [];
                out.push(it.next().value);
                s.delete(2);
                s.add(2);
                out.push(it.next().value);
                out.push(it.next().value);
                out.join(',');
                """);
    }

    @Test
    void testSetKeysIsValues() {
        // The core requirement: keys and values must be the same object
        assertBooleanWithJavet("Set.prototype.keys === Set.prototype.values");
    }

    @Test
    void testSetKeysSymbolIteratorIdentity() {
        // Transitively, keys and Symbol.iterator must be the same
        assertBooleanWithJavet("Set.prototype.keys === Set.prototype[Symbol.iterator]");
    }

    @Test
    void testSetMethodInputValidation() {
        assertThatThrownBy(() -> context.eval("new Set([1]).union({ size: 1, keys() { return [1][Symbol.iterator](); } })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Set([1]).intersection({ size: 1, has: 1, keys() { return [1][Symbol.iterator](); } })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Set([1]).isSubsetOf({ size: -1, has() { return true; }, keys() { return [][Symbol.iterator](); } })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
    }

    @Test
    void testSetMethodsDoNotUseOverriddenAdd() {
        assertThat(context.eval("""
                (() => {
                  const a = new Set([1]);
                  const b = new Set([2]);
                  const originalAdd = Set.prototype.add;
                  Set.prototype.add = function() { throw new Error('boom'); };
                  try {
                    const union = a.union(b);
                    const intersection = a.intersection(b);
                    return union instanceof Set
                      && union.has(1)
                      && union.has(2)
                      && intersection instanceof Set
                      && intersection.size === 0;
                  } finally {
                    Set.prototype.add = originalAdd;
                  }
                })()
                """).toJavaObject()).isEqualTo(true);
    }

    @Test
    void testSetSymbolIteratorIsValues() {
        // Symbol.iterator must also be the same object
        assertBooleanWithJavet("Set.prototype[Symbol.iterator] === Set.prototype.values");
    }

    @Test
    void testSetToString() {
        // toString returns [object Set]
        assertStringWithJavet("new Set().toString()");
        assertStringWithJavet("new Set([1,2,3]).toString()");
        assertStringWithJavet("Object.prototype.toString.call(new Set())");
    }

    @Test
    void testSetToStringTagDescriptor() {
        assertBooleanWithJavet("""
                (() => {
                  const d = Object.getOwnPropertyDescriptor(Set.prototype, Symbol.toStringTag);
                  return d.value === 'Set'
                    && d.writable === false
                    && d.enumerable === false
                    && d.configurable === true
                    && d.get === undefined;
                })()
                """);
    }

    @Test
    void testSizeEdgeCases() {
        // Empty set
        assertIntegerWithJavet("new Set().size");

        // Set with values
        assertIntegerWithJavet("new Set([1,2,3]).size");
        assertIntegerWithJavet("new Set([1,2,3,2,1]).size"); // duplicates

        // Size after operations
        assertIntegerWithJavet("var s = new Set(); s.add(1); s.add(2); s.size");
        assertIntegerWithJavet("var s = new Set([1,2,3]); s.delete(2); s.size");
        assertIntegerWithJavet("var s = new Set([1,2,3]); s.clear(); s.size");

        // Size is a getter, not a method
        assertBooleanWithJavet("typeof Object.getOwnPropertyDescriptor(Set.prototype, 'size').get === 'function'");
        assertUndefinedWithJavet("Object.getOwnPropertyDescriptor(Set.prototype, 'size').set");
    }

    @Test
    void testSymbolIteratorIsValues() {
        // Set.prototype[Symbol.iterator] should be the same as values
        assertBooleanWithJavet("Set.prototype[Symbol.iterator] === Set.prototype.values");
    }

    @Test
    void testValuesWorks() {
        // Verify values() still works correctly
        assertBooleanWithJavet("typeof new Set().values() === 'object'");
        assertBooleanWithJavet("typeof new Set([1,2,3]).values().next === 'function'");

        // Calling values() should produce an iterator
        assertBooleanWithJavet("var it = new Set([1,2,3]).values(); it.next().value === 1");
    }

    @Test
    void testVerifyCorrectEntriesVisited() {
        // Verify which entries are actually visited
        assertStringWithJavet("""
                var result = '';
                var s = new Set([1, 2, 3]);
                s.forEach(function(v) {
                  result += v;
                  if(v === 2) s.delete(3);
                });
                result""");
    }

    @Test
    void testZeroVsNonZero() {
        // +0 and -0 should not equal other numbers
        assertIntegerWithJavet("var s = new Set(); s.add(0); s.add(1); s.size");
        assertIntegerWithJavet("var s = new Set(); s.add(-0); s.add(1); s.size");
        assertBooleanWithJavet("var s = new Set([0, 1]); s.has(1)");
        assertBooleanWithJavet("var s = new Set([-0, 1]); s.has(1)");
    }
}
