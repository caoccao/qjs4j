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

/**
 * Unit tests for Map constructor static methods.
 */
public class MapConstructorTest extends BaseTest {

    @Test
    public void testGroupBy() {
        JSArray items = new JSArray();
        items.push(new JSNumber(1));
        items.push(new JSNumber(2));
        items.push(new JSNumber(3));
        items.push(new JSNumber(4));

        // Callback function: group by even/odd
        JSFunction callback = new JSNativeFunction("testCallback", 3, (ctx, thisArg, args) -> {
            double num = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (num % 2 == 0) ? new JSString("even") : new JSString("odd");
        });

        // Normal case: group by even/odd
        JSValue result = MapConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{items, callback});
        JSMap map = result.asMap().orElseThrow();

        // Check even group
        JSValue evenGroup = map.mapGet(new JSString("even"));
        JSArray evenArray = evenGroup.asArray().orElseThrow();
        assertEquals(2, evenArray.getLength());
        assertEquals(2.0, evenArray.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, evenArray.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Check odd group
        JSValue oddGroup = map.mapGet(new JSString("odd"));
        JSArray oddArray = oddGroup.asArray().orElseThrow();
        assertEquals(2, oddArray.getLength());
        assertEquals(1.0, oddArray.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, oddArray.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyItems = new JSArray();
        result = MapConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{emptyItems, callback});
        map = result.asMap().orElseThrow();
        assertEquals(0, map.size());

        // Edge case: insufficient arguments
        assertTypeError(MapConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{items}));
        assertPendingException(context);

        // Edge case: non-array items
        assertTypeError(MapConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not array"), callback}));
        assertPendingException(context);

        // Edge case: non-function callback
        assertTypeError(MapConstructor.groupBy(context, JSUndefined.INSTANCE, new JSValue[]{items, new JSString("not function")}));
        assertPendingException(context);
    }
}