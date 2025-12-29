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
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.JSWeakMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WeakMap.prototype methods.
 */
public class WeakMapPrototypeTest extends BaseTest {

    @Test
    public void testDelete() {
        JSWeakMap weakMap = new JSWeakMap();
        JSObject key1 = new JSObject();
        JSObject key2 = new JSObject();
        weakMap.weakMapSet(key1, new JSString("value1"));
        weakMap.weakMapSet(key2, new JSString("value2"));

        // Normal case: delete existing key
        JSValue result = WeakMapPrototype.delete(context, weakMap, new JSValue[]{key1});
        assertTrue(result.isBooleanTrue());
        assertFalse(weakMap.weakMapHas(key1));

        // Normal case: delete non-existing key
        result = WeakMapPrototype.delete(context, weakMap, new JSValue[]{new JSObject()});
        assertTrue(result.isBooleanFalse());

        // Normal case: no arguments
        result = WeakMapPrototype.delete(context, weakMap, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: non-object key
        result = WeakMapPrototype.delete(context, weakMap, new JSValue[]{new JSString("string")});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-WeakMap
        assertTypeError(WeakMapPrototype.delete(context, new JSString("not weakmap"), new JSValue[]{key1}));
        assertPendingException(context);
    }

    @Test
    public void testGet() {
        JSWeakMap weakMap = new JSWeakMap();
        JSObject key1 = new JSObject();
        JSObject key2 = new JSObject();
        JSString value1 = new JSString("value1");
        JSString value2 = new JSString("value2");
        weakMap.weakMapSet(key1, value1);
        weakMap.weakMapSet(key2, value2);

        // Normal case: get existing key
        JSValue result = WeakMapPrototype.get(context, weakMap, new JSValue[]{key1});
        assertSame(value1, result);

        // Normal case: get non-existing key
        result = WeakMapPrototype.get(context, weakMap, new JSValue[]{new JSObject()});
        assertTrue(result.isUndefined());

        // Normal case: no arguments
        result = WeakMapPrototype.get(context, weakMap, new JSValue[]{});
        assertTrue(result.isUndefined());

        // Edge case: non-object key
        result = WeakMapPrototype.get(context, weakMap, new JSValue[]{new JSString("string")});
        assertTrue(result.isUndefined());

        // Edge case: called on non-WeakMap
        assertTypeError(WeakMapPrototype.get(context, new JSString("not weakmap"), new JSValue[]{key1}));
        assertPendingException(context);
    }

    @Test
    public void testHas() {
        JSWeakMap weakMap = new JSWeakMap();
        JSObject key1 = new JSObject();
        JSObject key2 = new JSObject();
        weakMap.weakMapSet(key1, new JSString("value1"));
        weakMap.weakMapSet(key2, new JSString("value2"));

        // Normal case: has existing key
        JSValue result = WeakMapPrototype.has(context, weakMap, new JSValue[]{key1});
        assertTrue(result.isBooleanTrue());

        // Normal case: has non-existing key
        result = WeakMapPrototype.has(context, weakMap, new JSValue[]{new JSObject()});
        assertTrue(result.isBooleanFalse());

        // Normal case: no arguments
        result = WeakMapPrototype.has(context, weakMap, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: non-object key
        result = WeakMapPrototype.has(context, weakMap, new JSValue[]{new JSString("string")});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-WeakMap
        assertTypeError(WeakMapPrototype.has(context, new JSString("not weakmap"), new JSValue[]{key1}));
        assertPendingException(context);
    }

    @Test
    public void testSet() {
        JSWeakMap weakMap = new JSWeakMap();
        JSObject key1 = new JSObject();
        JSObject key2 = new JSObject();
        JSString value1 = new JSString("value1");
        JSString value2 = new JSString("value2");

        // Normal case: set new key-value
        JSValue result = WeakMapPrototype.set(context, weakMap, new JSValue[]{key1, value1});
        assertSame(weakMap, result);
        assertTrue(weakMap.weakMapHas(key1));
        assertSame(value1, weakMap.weakMapGet(key1));

        // Normal case: set existing key with new value
        result = WeakMapPrototype.set(context, weakMap, new JSValue[]{key1, value2});
        assertSame(weakMap, result);
        assertSame(value2, weakMap.weakMapGet(key1));

        // Normal case: set with undefined value
        result = WeakMapPrototype.set(context, weakMap, new JSValue[]{key2});
        assertSame(weakMap, result);
        assertTrue(weakMap.weakMapHas(key2));
        assertTrue(weakMap.weakMapGet(key2).isUndefined());

        // Edge case: no arguments
        assertTypeError(WeakMapPrototype.set(context, weakMap, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object key
        assertTypeError(WeakMapPrototype.set(context, weakMap, new JSValue[]{new JSString("string"), value1}));
        assertPendingException(context);

        // Edge case: called on non-WeakMap
        assertTypeError(WeakMapPrototype.set(context, new JSString("not weakmap"), new JSValue[]{key1, value1}));
        assertPendingException(context);
    }
}
