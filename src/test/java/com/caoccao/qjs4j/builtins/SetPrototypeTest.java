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
 * Unit tests for Set.prototype methods.
 */
public class SetPrototypeTest extends BaseTest {

    @Test
    public void testAdd() {
        JSSet set = new JSSet();

        // Normal case: add new value
        JSValue result = SetPrototype.add(context, set, new JSValue[]{new JSString("value1")});
        assertSame(set, result);
        assertEquals(1, set.size());
        assertTrue(set.setHas(new JSString("value1")));

        // Normal case: add duplicate value (should not increase size)
        result = SetPrototype.add(context, set, new JSValue[]{new JSString("value1")});
        assertSame(set, result);
        assertEquals(1, set.size());

        // Normal case: add different value
        result = SetPrototype.add(context, set, new JSValue[]{new JSNumber(42)});
        assertSame(set, result);
        assertEquals(2, set.size());
        assertTrue(set.setHas(new JSNumber(42)));

        // Edge case: no arguments (adds undefined)
        result = SetPrototype.add(context, set, new JSValue[]{});
        assertSame(set, result);
        assertEquals(3, set.size()); // Should add undefined, increasing size

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.add(context, new JSString("not set"), new JSValue[]{new JSString("value")}));
        assertPendingException(context);
    }

    @Test
    public void testClear() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));

        // Normal case: clear set
        JSValue result = SetPrototype.clear(context, set, new JSValue[]{});
        assertTrue(result.isUndefined());
        assertEquals(0, set.size());

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.clear(context, new JSString("not set"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testDelete() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));

        // Normal case: delete existing value
        JSValue result = SetPrototype.delete(context, set, new JSValue[]{new JSString("value1")});
        assertTrue(result.isBooleanTrue());
        assertEquals(1, set.size());
        assertFalse(set.setHas(new JSString("value1")));

        // Normal case: delete non-existing value
        result = SetPrototype.delete(context, set, new JSValue[]{new JSString("nonexistent")});
        assertTrue(result.isBooleanFalse());
        assertEquals(1, set.size());

        // Normal case: no arguments
        result = SetPrototype.delete(context, set, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.delete(context, new JSString("not set"), new JSValue[]{new JSString("value")}));
        assertPendingException(context);
    }

    @Test
    public void testForEach() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));

        // Normal case: forEach with callback
        final StringBuilder result = new StringBuilder();
        JSFunction callback = new JSNativeFunction("testCallback", 3, (ctx, thisArg, args) -> {
            String value = args[0].asString().map(JSString::value).orElseThrow();
            result.append(value).append(",");
            return JSUndefined.INSTANCE;
        });

        JSValue forEachResult = SetPrototype.forEach(context, set, new JSValue[]{callback});
        assertTrue(forEachResult.isUndefined());
        // Note: Order might vary, but both values should be present
        String resultStr = result.toString();
        assertTrue(resultStr.contains("value1"));
        assertTrue(resultStr.contains("value2"));

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
    public void testGetSize() {
        JSSet set = new JSSet();

        // Normal case: empty set
        JSValue result = SetPrototype.getSize(context, set, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: set with values
        set.setAdd(new JSString("value1"));
        set.setAdd(new JSString("value2"));
        result = SetPrototype.getSize(context, set, new JSValue[]{});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.getSize(context, new JSString("not set"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testHas() {
        JSSet set = new JSSet();
        set.setAdd(new JSString("value1"));

        // Normal case: existing value
        JSValue result = SetPrototype.has(context, set, new JSValue[]{new JSString("value1")});
        assertTrue(result.isBooleanTrue());

        // Normal case: non-existing value
        result = SetPrototype.has(context, set, new JSValue[]{new JSString("value2")});
        assertTrue(result.isBooleanFalse());

        // Normal case: no arguments
        result = SetPrototype.has(context, set, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-Set
        assertTypeError(SetPrototype.has(context, new JSString("not set"), new JSValue[]{new JSString("value")}));
        assertPendingException(context);
    }
}