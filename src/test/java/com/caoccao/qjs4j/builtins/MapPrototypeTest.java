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
    public void testGetSize() {
        JSMap map = new JSMap();

        // Normal case: empty map
        JSValue result = MapPrototype.getSize(ctx, map, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Normal case: map with entries
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));
        result = MapPrototype.getSize(ctx, map, new JSValue[]{});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.getSize(ctx, new JSString("not map"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testSet() {
        JSMap map = new JSMap();

        // Normal case: set key-value pair
        JSValue result = MapPrototype.set(ctx, map, new JSValue[]{new JSString("key"), new JSString("value")});
        assertEquals(map, result); // Should return the map
        assertEquals(1, map.size());
        assertEquals("value", map.mapGet(new JSString("key")).asString().map(JSString::value).orElse(""));

        // Normal case: update existing key
        result = MapPrototype.set(ctx, map, new JSValue[]{new JSString("key"), new JSString("newValue")});
        assertEquals(map, result);
        assertEquals(1, map.size());
        assertEquals("newValue", map.mapGet(new JSString("key")).asString().map(JSString::value).orElse(""));

        // Normal case: set with undefined value
        result = MapPrototype.set(ctx, map, new JSValue[]{new JSString("key2")});
        assertEquals(map, result);
        assertEquals(2, map.size());
        assertTrue(map.mapGet(new JSString("key2")).isUndefined());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.set(ctx, new JSString("not map"), new JSValue[]{new JSString("key"), new JSString("value")}));
        assertPendingException(ctx);
    }

    @Test
    public void testGet() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));

        // Normal case: existing key
        JSValue result = MapPrototype.get(ctx, map, new JSValue[]{new JSString("key1")});
        assertEquals("value1", result.asString().map(JSString::value).orElse(""));

        // Normal case: non-existing key
        result = MapPrototype.get(ctx, map, new JSValue[]{new JSString("key2")});
        assertTrue(result.isUndefined());

        // Normal case: no arguments
        result = MapPrototype.get(ctx, map, new JSValue[]{});
        assertTrue(result.isUndefined());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.get(ctx, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(ctx);
    }

    @Test
    public void testHas() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));

        // Normal case: existing key
        JSValue result = MapPrototype.has(ctx, map, new JSValue[]{new JSString("key1")});
        assertTrue(result.isBooleanTrue());

        // Normal case: non-existing key
        result = MapPrototype.has(ctx, map, new JSValue[]{new JSString("key2")});
        assertTrue(result.isBooleanFalse());

        // Normal case: no arguments
        result = MapPrototype.has(ctx, map, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.has(ctx, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(ctx);
    }

    @Test
    public void testDelete() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: delete existing key
        JSValue result = MapPrototype.delete(ctx, map, new JSValue[]{new JSString("key1")});
        assertTrue(result.isBooleanTrue());
        assertEquals(1, map.size());
        assertTrue(map.mapGet(new JSString("key1")).isUndefined());

        // Normal case: delete non-existing key
        result = MapPrototype.delete(ctx, map, new JSValue[]{new JSString("key3")});
        assertTrue(result.isBooleanFalse());
        assertEquals(1, map.size());

        // Normal case: no arguments
        result = MapPrototype.delete(ctx, map, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.delete(ctx, new JSString("not map"), new JSValue[]{new JSString("key")}));
        assertPendingException(ctx);
    }

    @Test
    public void testClear() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: clear map
        JSValue result = MapPrototype.clear(ctx, map, new JSValue[]{});
        assertTrue(result.isUndefined());
        assertEquals(0, map.size());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.clear(ctx, new JSString("not map"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testForEach() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: forEach with callback
        final StringBuilder result = new StringBuilder();
        JSFunction callback = new JSNativeFunction("testCallback", 3, (ctx, thisArg, args) -> {
            String value = args[0].asString().map(JSString::value).orElse("");
            String key = args[1].asString().map(JSString::value).orElse("");
            result.append(key).append(":").append(value).append(",");
            return JSUndefined.INSTANCE;
        });

        JSValue forEachResult = MapPrototype.forEach(ctx, map, new JSValue[]{callback});
        assertTrue(forEachResult.isUndefined());
        // Note: Order might vary, but both entries should be present
        String resultStr = result.toString();
        assertTrue(resultStr.contains("key1:value1"));
        assertTrue(resultStr.contains("key2:value2"));

        // Edge case: no callback function
        assertTypeError(MapPrototype.forEach(ctx, map, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: non-function callback
        assertTypeError(MapPrototype.forEach(ctx, map, new JSValue[]{new JSString("not function")}));
        assertPendingException(ctx);

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.forEach(ctx, new JSString("not map"), new JSValue[]{callback}));
        assertPendingException(ctx);
    }

    @Test
    public void testEntries() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get entries
        JSValue result = MapPrototype.entries(ctx, map, new JSValue[]{});
        JSArray entries = result.asArray().orElse(null);
        assertNotNull(entries);
        assertEquals(2, entries.getLength());

        // Check first entry
        JSArray entry1 = entries.get(0).asArray().orElse(null);
        assertNotNull(entry1);
        assertEquals(2, entry1.getLength());
        assertEquals("key1", entry1.get(0).asString().map(JSString::value).orElse(""));
        assertEquals("value1", entry1.get(1).asString().map(JSString::value).orElse(""));

        // Check second entry
        JSArray entry2 = entries.get(1).asArray().orElse(null);
        assertNotNull(entry2);
        assertEquals(2, entry2.getLength());
        assertEquals("key2", entry2.get(0).asString().map(JSString::value).orElse(""));
        assertEquals("value2", entry2.get(1).asString().map(JSString::value).orElse(""));

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.entries(ctx, emptyMap, new JSValue[]{});
        entries = result.asArray().orElse(null);
        assertNotNull(entries);
        assertEquals(0, entries.getLength());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.entries(ctx, new JSString("not map"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testKeys() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get keys
        JSValue result = MapPrototype.keys(ctx, map, new JSValue[]{});
        JSArray keys = result.asArray().orElse(null);
        assertNotNull(keys);
        assertEquals(2, keys.getLength());

        // Check that both keys are present (order may vary)
        String key1 = keys.get(0).asString().map(JSString::value).orElse("");
        String key2 = keys.get(1).asString().map(JSString::value).orElse("");
        assertTrue((key1.equals("key1") && key2.equals("key2")) || (key1.equals("key2") && key2.equals("key1")));

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.keys(ctx, emptyMap, new JSValue[]{});
        keys = result.asArray().orElse(null);
        assertNotNull(keys);
        assertEquals(0, keys.getLength());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.keys(ctx, new JSString("not map"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testValues() {
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSString("value2"));

        // Normal case: get values
        JSValue result = MapPrototype.values(ctx, map, new JSValue[]{});
        JSArray values = result.asArray().orElse(null);
        assertNotNull(values);
        assertEquals(2, values.getLength());

        // Check that both values are present (order may vary)
        String value1 = values.get(0).asString().map(JSString::value).orElse("");
        String value2 = values.get(1).asString().map(JSString::value).orElse("");
        assertTrue((value1.equals("value1") && value2.equals("value2")) || (value1.equals("value2") && value2.equals("value1")));

        // Normal case: empty map
        JSMap emptyMap = new JSMap();
        result = MapPrototype.values(ctx, emptyMap, new JSValue[]{});
        values = result.asArray().orElse(null);
        assertNotNull(values);
        assertEquals(0, values.getLength());

        // Edge case: called on non-Map
        assertTypeError(MapPrototype.values(ctx, new JSString("not map"), new JSValue[]{}));
        assertPendingException(ctx);
    }
}