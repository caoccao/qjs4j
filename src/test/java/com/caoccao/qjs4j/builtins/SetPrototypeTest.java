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
 * Unit tests for Set.prototype methods.
 */
public class SetPrototypeTest extends BaseJavetTest {

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
}