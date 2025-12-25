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
 * Unit tests for Array constructor static methods.
 */
public class ArrayConstructorTest extends BaseTest {

    @Test
    public void testIsArray() {
        // Normal case: array
        JSArray arr = new JSArray();
        JSValue result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{arr});
        assertEquals(JSBoolean.TRUE, result);

        // Normal case: not array
        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not array")});
        assertEquals(JSBoolean.FALSE, result);

        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSObject()});
        assertEquals(JSBoolean.FALSE, result);

        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: no arguments
        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);
    }

    @Test
    public void testOf() {
        // Normal case: create array with elements
        JSValue result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{
            new JSNumber(1),
            new JSNumber(2),
            new JSNumber(3)
        });
        assertInstanceOf(JSArray.class, result);
        JSArray arr = (JSArray) result;
        assertEquals(3, arr.getLength());
        assertEquals(1.0, ((JSNumber) arr.get(0)).value());
        assertEquals(2.0, ((JSNumber) arr.get(1)).value());
        assertEquals(3.0, ((JSNumber) arr.get(2)).value());

        // Edge case: no arguments
        result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        arr = (JSArray) result;
        assertEquals(0, arr.getLength());

        // Edge case: single element
        result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello")});
        arr = (JSArray) result;
        assertEquals(1, arr.getLength());
        assertEquals("hello", ((JSString) arr.get(0)).getValue());

        // Edge case: mixed types
        result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{
            new JSNumber(1),
            new JSString("two"),
            JSBoolean.TRUE
        });
        arr = (JSArray) result;
        assertEquals(3, arr.getLength());
    }

    @Test
    public void testFrom() {
        // Normal case: from array
        JSArray sourceArr = new JSArray();
        sourceArr.push(new JSNumber(1));
        sourceArr.push(new JSNumber(2));
        sourceArr.push(new JSNumber(3));

        JSValue result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{sourceArr});
        assertInstanceOf(JSArray.class, result);
        JSArray arr = (JSArray) result;
        assertEquals(3, arr.getLength());
        assertEquals(1.0, ((JSNumber) arr.get(0)).value());
        assertEquals(2.0, ((JSNumber) arr.get(1)).value());
        assertEquals(3.0, ((JSNumber) arr.get(2)).value());

        // Normal case: from string
        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        arr = (JSArray) result;
        assertEquals(3, arr.getLength());
        assertEquals("a", ((JSString) arr.get(0)).getValue());
        assertEquals("b", ((JSString) arr.get(1)).getValue());
        assertEquals("c", ((JSString) arr.get(2)).getValue());

        // Normal case: with mapping function
        JSFunction mapFn = new JSNativeFunction("double", 1, (context, thisArg, args) -> {
            if (args.length > 0 && args[0] instanceof JSNumber num) {
                return new JSNumber(num.value() * 2);
            }
            return args[0];
        });

        JSArray sourceArr2 = new JSArray();
        sourceArr2.push(new JSNumber(1));
        sourceArr2.push(new JSNumber(2));

        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{sourceArr2, mapFn});
        arr = (JSArray) result;
        assertEquals(2, arr.getLength());
        assertEquals(2.0, ((JSNumber) arr.get(0)).value());
        assertEquals(4.0, ((JSNumber) arr.get(1)).value());

        // Normal case: from string with mapping function
        JSFunction upperFn = new JSNativeFunction("upper", 1, (context, thisArg, args) -> {
            if (args.length > 0 && args[0] instanceof JSString str) {
                return new JSString(str.getValue().toUpperCase());
            }
            return args[0];
        });

        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello"), upperFn});
        arr = (JSArray) result;
        assertEquals(5, arr.getLength());
        assertEquals("H", ((JSString) arr.get(0)).getValue());
        assertEquals("E", ((JSString) arr.get(1)).getValue());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyArr});
        arr = (JSArray) result;
        assertEquals(0, arr.getLength());

        // Edge case: empty string
        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        arr = (JSArray) result;
        assertEquals(0, arr.getLength());

        // Edge case: no arguments
        assertTypeError(ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: non-iterable
        assertTypeError(ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)}));
        assertPendingException(ctx);

        // Edge case: invalid mapFn
        assertTypeError(ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{sourceArr, new JSString("not a function")}));
        assertPendingException(ctx);
    }
}
