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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for IteratorPrototype methods.
 */
public class IteratorPrototypeTest extends BaseTest {

    @Test
    public void testArrayEntries() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test array
        JSArray array = new JSArray();
        array.push(new JSString("a"));
        array.push(new JSString("b"));

        // Normal case: get entries iterator
        JSValue result = IteratorPrototype.arrayEntries(ctx, array, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration - first entry [0, "a"]
        JSObject iteratorResult = iterator.next();
        JSArray pair = iteratorResult.get("value").asArray().orElseThrow();
        assertEquals(0.0, pair.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals("a", pair.get(1).asString().map(JSString::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        // Second entry [1, "b"]
        iteratorResult = iterator.next();
        pair = iteratorResult.get("value").asArray().orElseThrow();
        assertEquals(1.0, pair.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals("b", pair.get(1).asString().map(JSString::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        // End
        iteratorResult = iterator.next();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-array
        result = IteratorPrototype.arrayEntries(ctx, JSNull.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testArrayKeys() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test array
        JSArray array = new JSArray();
        array.push(new JSNumber(10));
        array.push(new JSNumber(20));

        // Normal case: get keys iterator
        JSValue result = IteratorPrototype.arrayKeys(ctx, array, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        JSObject iteratorResult = iterator.next();
        assertEquals(0.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals(1.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-array
        result = IteratorPrototype.arrayKeys(ctx, new JSObject(), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testArrayValues() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test array
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSString("hello"));
        array.push(JSBoolean.TRUE);

        // Normal case: get values iterator
        JSValue result = IteratorPrototype.arrayValues(ctx, array, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        JSObject iteratorResult = iterator.next();
        assertEquals(1.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals("hello", iteratorResult.get("value").asString().map(JSString::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals(JSBoolean.TRUE, iteratorResult.get("value"));
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-array
        result = IteratorPrototype.arrayValues(ctx, new JSString("not an array"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testMapEntriesIterator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test map
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSNumber(42));

        // Normal case: get entries iterator
        JSValue result = IteratorPrototype.mapEntriesIterator(ctx, map, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration (order may vary)
        boolean foundKey1 = false, foundKey2 = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));
            JSArray pair = iteratorResult.get("value").asArray().orElseThrow();

            String key = pair.get(0).asString().map(JSString::value).orElseThrow();
            if ("key1".equals(key)) {
                assertEquals("value1", pair.get(1).asString().map(JSString::value).orElseThrow());
                foundKey1 = true;
            } else if ("key2".equals(key)) {
                assertEquals(42.0, pair.get(1).asNumber().map(JSNumber::value).orElseThrow());
                foundKey2 = true;
            }
        }
        assertTrue(foundKey1 && foundKey2);

        // End of iteration
        JSObject iteratorResult = iterator.next();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-map
        result = IteratorPrototype.mapEntriesIterator(ctx, new JSArray(), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testMapKeysIterator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test map
        JSMap map = new JSMap();
        map.mapSet(new JSString("a"), new JSNumber(1));
        map.mapSet(new JSString("b"), new JSNumber(2));

        // Normal case: get keys iterator
        JSValue result = IteratorPrototype.mapKeysIterator(ctx, map, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        boolean foundA = false, foundB = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));
            String key = iteratorResult.get("value").asString().map(JSString::value).orElseThrow();
            if ("a".equals(key)) foundA = true;
            else if ("b".equals(key)) foundB = true;
        }
        assertTrue(foundA && foundB);

        // Edge case: called on non-map
        result = IteratorPrototype.mapKeysIterator(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testMapValuesIterator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test map
        JSMap map = new JSMap();
        map.mapSet(new JSNumber(1), new JSString("one"));
        map.mapSet(new JSNumber(2), new JSString("two"));

        // Normal case: get values iterator
        JSValue result = IteratorPrototype.mapValuesIterator(ctx, map, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        boolean foundOne = false, foundTwo = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));
            String value = iteratorResult.get("value").asString().map(JSString::value).orElseThrow();
            if ("one".equals(value)) foundOne = true;
            else if ("two".equals(value)) foundTwo = true;
        }
        assertTrue(foundOne && foundTwo);

        // Edge case: called on non-map
        result = IteratorPrototype.mapValuesIterator(ctx, new JSString("not a map"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testNext() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create an array iterator
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        JSIterator iterator = JSIterator.arrayIterator(array);

        // Normal case: next() on iterator
        JSValue result = IteratorPrototype.next(ctx, iterator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElseThrow();
        assertEquals(1.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        // Continue iteration
        result = IteratorPrototype.next(ctx, iterator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertEquals(2.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        // End of iteration
        result = IteratorPrototype.next(ctx, iterator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-iterator
        result = IteratorPrototype.next(ctx, new JSString("not an iterator"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetEntriesIterator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test set
        JSSet set = new JSSet();
        set.setAdd(new JSString("hello"));
        set.setAdd(new JSNumber(42));

        // Normal case: get entries iterator
        JSValue result = IteratorPrototype.setEntriesIterator(ctx, set, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration - each entry should be [value, value]
        boolean foundHello = false, found42 = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));
            JSArray pair = iteratorResult.get("value").asArray().orElseThrow();

            // In Set entries, both elements should be the same
            assertEquals(pair.get(0), pair.get(1));

            JSValue value = pair.get(0);
            if (value instanceof JSString str && "hello".equals(str.value())) {
                foundHello = true;
            } else if (value instanceof JSNumber num && num.value() == 42.0) {
                found42 = true;
            }
        }
        assertTrue(foundHello && found42);

        // Edge case: called on non-set
        result = IteratorPrototype.setEntriesIterator(ctx, JSBoolean.FALSE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetKeysIterator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test set
        JSSet set = new JSSet();
        set.setAdd(new JSString("x"));
        set.setAdd(new JSNumber(5));

        // Normal case: keys() should be same as values() for Set
        JSValue result = IteratorPrototype.setKeysIterator(ctx, set, new JSValue[]{});
        result.asIterator().orElseThrow();

        // Should behave identically to values iterator
        JSValue valuesResult = IteratorPrototype.setValuesIterator(ctx, set, new JSValue[]{});
        // Note: In a full test, we'd verify they produce the same results

        // Edge case: called on non-set (should delegate to setValuesIterator)
        result = IteratorPrototype.setKeysIterator(ctx, new JSArray(), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testSetValuesIterator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create test set
        JSSet set = new JSSet();
        set.setAdd(new JSString("a"));
        set.setAdd(new JSString("b"));
        set.setAdd(new JSNumber(3));

        // Normal case: get values iterator
        JSValue result = IteratorPrototype.setValuesIterator(ctx, set, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration (order may vary, but all values should be present)
        boolean foundA = false, foundB = false, found3 = false;
        for (int i = 0; i < 3; i++) {
            JSObject iteratorResult = iterator.next();
            assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));
            JSValue value = iteratorResult.get("value");
            if (value instanceof JSString str) {
                if ("a".equals(str.value())) foundA = true;
                else if ("b".equals(str.value())) foundB = true;
            } else if (value instanceof JSNumber num && num.value() == 3.0) {
                found3 = true;
            }
        }
        assertTrue(foundA && foundB && found3);

        // End of iteration
        JSObject iteratorResult = iterator.next();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-set
        result = IteratorPrototype.setValuesIterator(ctx, new JSObject(), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testStringIterator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Normal case: string iteration
        JSString str = new JSString("abc");
        JSValue result = IteratorPrototype.stringIterator(ctx, str, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        JSObject iteratorResult = iterator.next();
        assertEquals("a", iteratorResult.get("value").asString().map(JSString::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals("b", iteratorResult.get("value").asString().map(JSString::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals("c", iteratorResult.get("value").asString().map(JSString::value).orElseThrow());
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        iteratorResult = iterator.next();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Normal case: empty string
        JSString emptyStr = new JSString("");
        result = IteratorPrototype.stringIterator(ctx, emptyStr, new JSValue[]{});
        iterator = result.asIterator().orElseThrow();
        iteratorResult = iterator.next();
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Normal case: boxed string
        JSObject boxedString = new JSObject();
        boxedString.set("[[PrimitiveValue]]", str);
        result = IteratorPrototype.stringIterator(ctx, boxedString, new JSValue[]{});
        result.asIterator().orElseThrow();

        // Edge case: called on non-string
        result = IteratorPrototype.stringIterator(ctx, new JSNumber(123), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: boxed non-string
        JSObject badBox = new JSObject();
        badBox.set("[[PrimitiveValue]]", new JSNumber(456));
        result = IteratorPrototype.stringIterator(ctx, badBox, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}