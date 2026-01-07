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
}