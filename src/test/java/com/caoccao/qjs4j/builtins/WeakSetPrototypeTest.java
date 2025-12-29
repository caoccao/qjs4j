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
import com.caoccao.qjs4j.core.JSWeakSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WeakSet.prototype methods.
 */
public class WeakSetPrototypeTest extends BaseTest {

    @Test
    public void testAdd() {
        JSWeakSet weakSet = new JSWeakSet();
        JSObject value1 = new JSObject();
        JSObject value2 = new JSObject();

        // Normal case: add new value
        JSValue result = WeakSetPrototype.add(context, weakSet, new JSValue[]{value1});
        assertSame(weakSet, result);
        assertTrue(weakSet.weakSetHas(value1));

        // Normal case: add duplicate value (should not change)
        result = WeakSetPrototype.add(context, weakSet, new JSValue[]{value1});
        assertSame(weakSet, result);
        assertTrue(weakSet.weakSetHas(value1));

        // Normal case: add different value
        result = WeakSetPrototype.add(context, weakSet, new JSValue[]{value2});
        assertSame(weakSet, result);
        assertTrue(weakSet.weakSetHas(value2));

        // Edge case: no arguments
        assertTypeError(WeakSetPrototype.add(context, weakSet, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-object value
        assertTypeError(WeakSetPrototype.add(context, weakSet, new JSValue[]{new JSString("string")}));
        assertPendingException(context);

        // Edge case: called on non-WeakSet
        assertTypeError(WeakSetPrototype.add(context, new JSString("not weakset"), new JSValue[]{value1}));
        assertPendingException(context);
    }

    @Test
    public void testDelete() {
        JSWeakSet weakSet = new JSWeakSet();
        JSObject value1 = new JSObject();
        JSObject value2 = new JSObject();
        weakSet.weakSetAdd(value1);
        weakSet.weakSetAdd(value2);

        // Normal case: delete existing value
        JSValue result = WeakSetPrototype.delete(context, weakSet, new JSValue[]{value1});
        assertTrue(result.isBooleanTrue());
        assertFalse(weakSet.weakSetHas(value1));

        // Normal case: delete non-existing value
        result = WeakSetPrototype.delete(context, weakSet, new JSValue[]{new JSObject()});
        assertTrue(result.isBooleanFalse());

        // Normal case: no arguments
        result = WeakSetPrototype.delete(context, weakSet, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: non-object value
        result = WeakSetPrototype.delete(context, weakSet, new JSValue[]{new JSString("string")});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-WeakSet
        assertTypeError(WeakSetPrototype.delete(context, new JSString("not weakset"), new JSValue[]{value1}));
        assertPendingException(context);
    }

    @Test
    public void testHas() {
        JSWeakSet weakSet = new JSWeakSet();
        JSObject value1 = new JSObject();
        JSObject value2 = new JSObject();
        weakSet.weakSetAdd(value1);
        weakSet.weakSetAdd(value2);

        // Normal case: has existing value
        JSValue result = WeakSetPrototype.has(context, weakSet, new JSValue[]{value1});
        assertTrue(result.isBooleanTrue());

        // Normal case: has non-existing value
        result = WeakSetPrototype.has(context, weakSet, new JSValue[]{new JSObject()});
        assertTrue(result.isBooleanFalse());

        // Normal case: no arguments
        result = WeakSetPrototype.has(context, weakSet, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: non-object value
        result = WeakSetPrototype.has(context, weakSet, new JSValue[]{new JSString("string")});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-WeakSet
        assertTypeError(WeakSetPrototype.has(context, new JSString("not weakset"), new JSValue[]{value1}));
        assertPendingException(context);
    }
}
