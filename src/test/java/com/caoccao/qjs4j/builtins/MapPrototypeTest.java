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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Map.prototype methods.
 */
public class MapPrototypeTest extends BaseTest {

    @Test
    public void testClear() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: clear map
        JSValue result = MapPrototype.clear(context, map, new JSValue[]{});
        assertTrue(result.isUndefined());
        assertEquals(0, map.size());

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
        assertTrue(result.isBooleanTrue());
        assertEquals(1, map.size());
        assertTrue(map.mapGet(new JSString("key1")).isUndefined());

        // Normal case: delete non-existing key
        result = MapPrototype.delete(context, map, new JSValue[]{new JSString("key3")});
        assertTrue(result.isBooleanFalse());
        assertEquals(1, map.size());

        // Normal case: no arguments
        result = MapPrototype.delete(context, map, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.delete(context, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(context);
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
        assertFalse((Boolean) iterResult1.get("done").toJavaObject());
        JSArray entry1 = iterResult1.get("value").asArray().orElseThrow();
        assertEquals(2, entry1.getLength());
        assertEquals("key1", entry1.get(0).asString().map(JSString::value).orElseThrow());
        assertEquals("value1", entry1.get(1).asString().map(JSString::value).orElseThrow());

        // Check second entry
        JSObject iterResult2 = iterator.next();
        assertFalse((Boolean) iterResult2.get("done").toJavaObject());
        JSArray entry2 = iterResult2.get("value").asArray().orElseThrow();
        assertEquals(2, entry2.getLength());
        assertEquals("key2", entry2.get(0).asString().map(JSString::value).orElseThrow());
        assertEquals("value2", entry2.get(1).asString().map(JSString::value).orElseThrow());

        // Iterator should be exhausted
        JSObject iterResult3 = iterator.next();
        assertTrue((Boolean) iterResult3.get("done").toJavaObject());

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.entries(context, emptyMap, new JSValue[]{});
        iterator = result.asIterator().orElseThrow();
        JSObject emptyResult = iterator.next();
        assertTrue((Boolean) emptyResult.get("done").toJavaObject());

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
        JSFunction callback = new JSNativeFunction("testCallback", 3, (ctx, thisArg, args) -> {
            String value = args[0].asString().map(JSString::value).orElseThrow();
            String key = args[1].asString().map(JSString::value).orElseThrow();
            result.append(key).append(":").append(value).append(",");
            return JSUndefined.INSTANCE;
        });

        JSValue forEachResult = MapPrototype.forEach(context, map, new JSValue[]{callback});
        assertTrue(forEachResult.isUndefined());
        // Note: Order might vary, but both entries should be present
        String resultStr = result.toString();
        assertTrue(resultStr.contains("key1:value1"));
        assertTrue(resultStr.contains("key2:value2"));

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
        assertEquals("value1", result.asString().map(JSString::value).orElseThrow());

        // Normal case: non-existing key
        result = MapPrototype.get(context, map, new JSValue[]{new JSString("key2")});
        assertTrue(result.isUndefined());

        // Normal case: no arguments
        result = MapPrototype.get(context, map, new JSValue[]{});
        assertTrue(result.isUndefined());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.get(context, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(context);
    }

    @Test
    public void testGetSize() {
        JSMap map = new JSMap();

        // Normal case: empty map
        JSValue result = MapPrototype.getSize(context, map, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: map with entries
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));
        result = MapPrototype.getSize(context, map, new JSValue[]{});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.getSize(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testHas() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));

        // Normal case: existing key
        JSValue result = MapPrototype.has(context, map, new JSValue[]{new JSString("key1")});
        assertTrue(result.isBooleanTrue());

        // Normal case: non-existing key
        result = MapPrototype.has(context, map, new JSValue[]{new JSString("key2")});
        assertTrue(result.isBooleanFalse());

        // Normal case: no arguments
        result = MapPrototype.has(context, map, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.has(context, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(context);
    }

    @Test
    public void testKeys() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get keys
        JSValue result = MapPrototype.keys(context, map, new JSValue[]{});
        JSIterator keys = result.asIterator().orElseThrow();
        JSArray jsArray = JSIteratorHelper.toArray(keys);
        assertEquals(2, jsArray.getLength());
        assertEquals("key1", jsArray.get(0).asString().map(JSString::value).orElse(null));
        assertEquals("key2", jsArray.get(1).asString().map(JSString::value).orElse(null));

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.keys(context, emptyMap, new JSValue[]{});
        keys = result.asIterator().orElseThrow();
        jsArray = JSIteratorHelper.toArray(keys);
        assertEquals(0, jsArray.getLength());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.keys(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testSet() {
        JSMap map = new JSMap();

        // Normal case: set key-value pair
        JSValue result = MapPrototype.set(context, map, new JSValue[]{new JSString("key"), new JSString("value")});
        assertEquals(map, result); // Should return the map
        assertEquals(1, map.size());
        assertEquals("value", map.mapGet(new JSString("key")).asString().map(JSString::value).orElseThrow());

        // Normal case: update existing key
        result = MapPrototype.set(context, map, new JSValue[]{new JSString("key"), new JSString("newValue")});
        assertEquals(map, result);
        assertEquals(1, map.size());
        assertEquals("newValue", map.mapGet(new JSString("key")).asString().map(JSString::value).orElseThrow());

        // Normal case: set with undefined value
        result = MapPrototype.set(context, map, new JSValue[]{new JSString("key2")});
        assertEquals(map, result);
        assertEquals(2, map.size());
        assertTrue(map.mapGet(new JSString("key2")).isUndefined());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.set(context, new JSString("not map"), new JSValue[]{new JSString("key"), new JSString("value")}));
        assertPendingException(context);
    }

    @Test
    public void testValues() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get values
        JSValue result = MapPrototype.values(context, map, new JSValue[]{});
        JSIterator values = result.asIterator().orElseThrow();
        JSArray jsArray = JSIteratorHelper.toArray(values);
        assertEquals(2, jsArray.getLength());
        assertEquals("value1", jsArray.get(0).asString().map(JSString::value).orElse(null));
        assertEquals("value2", jsArray.get(1).asString().map(JSString::value).orElse(null));

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.values(context, emptyMap, new JSValue[]{});
        values = result.asIterator().orElseThrow();
        jsArray = JSIteratorHelper.toArray(values);
        assertEquals(0, jsArray.getLength());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.values(context, new JSString("not map"), new JSValue[]{}));
        assertPendingException(context);
    }
}