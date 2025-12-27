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
    public void testFrom() {
        // Normal case: from array
        JSArray sourceArr = new JSArray();
        sourceArr.push(new JSNumber(1));
        sourceArr.push(new JSNumber(2));
        sourceArr.push(new JSNumber(3));

        JSValue result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{sourceArr});
        JSArray arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(3, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Normal case: from string
        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(3, arr.getLength());
        assertEquals("a", arr.get(0).asString().map(JSString::value).orElse(""));
        assertEquals("b", arr.get(1).asString().map(JSString::value).orElse(""));
        assertEquals("c", arr.get(2).asString().map(JSString::value).orElse(""));

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
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(2, arr.getLength());
        assertEquals(2.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));

        // Normal case: from string with mapping function
        JSFunction upperFn = new JSNativeFunction("upper", 1, (context, thisArg, args) -> {
            if (args.length > 0 && args[0] instanceof JSString str) {
                return new JSString(str.value().toUpperCase());
            }
            return args[0];
        });

        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello"), upperFn});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(5, arr.getLength());
        assertEquals("H", arr.get(0).asString().map(JSString::value).orElse(""));
        assertEquals("E", arr.get(1).asString().map(JSString::value).orElse(""));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyArr});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(0, arr.getLength());

        // Edge case: empty string
        result = ArrayConstructor.from(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
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

    // Note: Tests for JavaScript native iterables (Set, Map, custom iterables) are not included
    // because the current JSIteratorHelper implementation doesn't properly bridge with
    // JavaScript native objects. The iterable support works with Java-created iterables
    // (JSIterator, JSGenerator) but not with JavaScript's built-in Set/Map or custom iterables.

    @Test
    public void testFromAsync() {
        // Normal case: from async iterator (array)
        JSArray sourceArr = new JSArray();
        sourceArr.push(new JSNumber(1));
        sourceArr.push(new JSNumber(2));
        sourceArr.push(new JSNumber(3));

        JSAsyncIterator asyncIterator = JSAsyncIterator.fromArray(sourceArr, ctx);
        JSValue result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{asyncIterator});
        JSPromise promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);

        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
        JSArray arr = promise.getResult().asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(3, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Normal case: from regular array (sync fallback)
        JSArray sourceArr2 = new JSArray();
        sourceArr2.push(new JSNumber(10));
        sourceArr2.push(new JSNumber(20));

        result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{sourceArr2});
        promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);

        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
        arr = promise.getResult().asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(2, arr.getLength());
        assertEquals(10.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(20.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));

        // Normal case: with mapping function
        JSFunction mapFn = new JSNativeFunction("double", 1, (context, thisArg, args) -> {
            if (args.length > 0 && args[0] instanceof JSNumber num) {
                return new JSNumber(num.value() * 2);
            }
            return args[0];
        });

        JSArray sourceArr3 = new JSArray();
        sourceArr3.push(new JSNumber(1));
        sourceArr3.push(new JSNumber(2));

        result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{sourceArr3, mapFn});
        promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);

        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
        arr = promise.getResult().asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(2, arr.getLength());
        assertEquals(2.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));

        // Normal case: from string
        result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);

        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
        arr = promise.getResult().asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(3, arr.getLength());
        assertEquals("a", arr.get(0).asString().map(JSString::value).orElse(""));
        assertEquals("b", arr.get(1).asString().map(JSString::value).orElse(""));
        assertEquals("c", arr.get(2).asString().map(JSString::value).orElse(""));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{emptyArr});
        promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);

        assertEquals(JSPromise.PromiseState.FULFILLED, promise.getState());
        arr = promise.getResult().asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(0, arr.getLength());

        // Edge case: no arguments
        result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);
        assertEquals(JSPromise.PromiseState.REJECTED, promise.getState());

        // Edge case: non-iterable
        result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);
        assertEquals(JSPromise.PromiseState.REJECTED, promise.getState());

        // Edge case: invalid mapFn
        result = ArrayConstructor.fromAsync(ctx, JSUndefined.INSTANCE, new JSValue[]{sourceArr, new JSString("not a function")});
        promise = result.asPromise().orElse(null);
        assertNotNull(promise);
        awaitPromise(promise);
        assertEquals(JSPromise.PromiseState.REJECTED, promise.getState());
    }

    @Test
    public void testGetSpecies() {
        // Normal case: returns thisArg
        JSObject arrayConstructor = new JSObject();
        JSValue result = ArrayConstructor.getSpecies(ctx, arrayConstructor, new JSValue[]{});
        assertEquals(arrayConstructor, result);

        // Normal case: with different thisArg
        JSObject customConstructor = new JSObject();
        result = ArrayConstructor.getSpecies(ctx, customConstructor, new JSValue[]{});
        assertEquals(customConstructor, result);

        // Edge case: undefined thisArg
        result = ArrayConstructor.getSpecies(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testIsArray() {
        // Normal case: array
        JSArray arr = new JSArray();
        JSValue result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{arr});
        assertTrue(result.isBooleanTrue());

        // Normal case: not array
        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not array")});
        assertTrue(result.isBooleanFalse());

        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSObject()});
        assertTrue(result.isBooleanFalse());

        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTrue(result.isBooleanFalse());

        // Edge case: no arguments
        result = ArrayConstructor.isArray(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());
    }

    @Test
    public void testOf() {
        // Normal case: create array with elements
        JSValue result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1),
                new JSNumber(2),
                new JSNumber(3)
        });
        JSArray arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(3, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: no arguments
        result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(0, arr.getLength());

        // Edge case: single element
        result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello")});
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(1, arr.getLength());
        assertEquals("hello", arr.get(0).asString().map(JSString::value).orElse(""));

        // Edge case: mixed types
        result = ArrayConstructor.of(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1),
                new JSString("two"),
                JSBoolean.TRUE
        });
        arr = result.asArray().orElse(null);
        assertNotNull(arr);
        assertEquals(3, arr.getLength());
    }
}
