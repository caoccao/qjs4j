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
 * Unit tests for Array constructor static methods.
 */
public class ArrayConstructorTest extends BaseJavetTest {

    @Test
    public void testFrom() {
        // Normal case: from array
        JSArray sourceArr = new JSArray();
        sourceArr.push(new JSNumber(1));
        sourceArr.push(new JSNumber(2));
        sourceArr.push(new JSNumber(3));

        JSValue result = ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{sourceArr});
        JSArray arr = result.asArray().orElseThrow();
        assertThat(arr).isNotNull();
        assertThat(arr.getLength()).isEqualTo(3);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Normal case: from string
        result = ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(3);
        assertThat(arr.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("a");
        assertThat(arr.get(1).asString().map(JSString::value).orElseThrow()).isEqualTo("b");
        assertThat(arr.get(2).asString().map(JSString::value).orElseThrow()).isEqualTo("c");

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

        result = ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{sourceArr2, mapFn});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(2);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Normal case: from string with mapping function
        JSFunction upperFn = new JSNativeFunction("upper", 1, (context, thisArg, args) -> {
            if (args.length > 0 && args[0] instanceof JSString str) {
                return new JSString(str.value().toUpperCase());
            }
            return args[0];
        });

        result = ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello"), upperFn});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(5);
        assertThat(arr.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("H");
        assertThat(arr.get(1).asString().map(JSString::value).orElseThrow()).isEqualTo("E");

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{emptyArr});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(0);

        // Edge case: empty string
        result = ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(0);

        // Edge case: no arguments
        assertTypeError(ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-iterable number → empty array (per spec, ToObject(123) has no length)
        result = ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(0);

        // Edge case: invalid mapFn
        assertTypeError(ArrayConstructor.from(context, JSUndefined.INSTANCE, new JSValue[]{sourceArr, new JSString("not a function")}));
        assertPendingException(context);
    }

    @Test
    public void testFromAsync() {
        // Normal case: from async iterator (array)
        JSArray sourceArr = new JSArray();
        sourceArr.push(new JSNumber(1));
        sourceArr.push(new JSNumber(2));
        sourceArr.push(new JSNumber(3));

        JSAsyncIterator asyncIterator = JSAsyncIterator.fromArray(sourceArr, context);
        JSValue result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{asyncIterator});
        JSPromise promise = result.asPromise().orElseThrow();
        awaitPromise(promise);

        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        JSArray arr = promise.getResult().asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(3);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Normal case: from regular array (sync fallback)
        JSArray sourceArr2 = new JSArray();
        sourceArr2.push(new JSNumber(10));
        sourceArr2.push(new JSNumber(20));

        result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{sourceArr2});
        promise = result.asPromise().orElseThrow();
        awaitPromise(promise);

        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        arr = promise.getResult().asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(2);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(20.0);

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

        result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{sourceArr3, mapFn});
        promise = result.asPromise().orElseThrow();
        awaitPromise(promise);

        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        arr = promise.getResult().asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(2);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Normal case: from string
        result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("abc")});
        promise = result.asPromise().orElseThrow();
        awaitPromise(promise);

        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        arr = promise.getResult().asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(3);
        assertThat(arr.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("a");
        assertThat(arr.get(1).asString().map(JSString::value).orElseThrow()).isEqualTo("b");
        assertThat(arr.get(2).asString().map(JSString::value).orElseThrow()).isEqualTo("c");

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{emptyArr});
        promise = result.asPromise().orElseThrow();
        awaitPromise(promise);

        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        arr = promise.getResult().asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(0);

        // Edge case: no arguments
        result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{});
        promise = result.asPromise().orElseThrow();
        awaitPromise(promise);
        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.REJECTED);

        // Edge case: number input treated as array-like with length 0 (per ES2024 spec)
        result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        promise = result.asPromise().orElseThrow();
        awaitPromise(promise);
        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);

        // Edge case: invalid mapFn
        result = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, new JSValue[]{sourceArr, new JSString("not a function")});
        promise = result.asPromise().orElseThrow();
        awaitPromise(promise);
        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.REJECTED);
    }

    @Test
    public void testFromAsyncWithAsyncGenerator() {
        assertStringWithJavet("""
                async function test() {
                  async function* gen() { yield* [0, 1, 2]; }
                  var result = await Array.fromAsync(gen());
                  return JSON.stringify(result);
                }
                test()""");
    }

    @Test
    public void testFromAsyncWithMapping() {
        assertStringWithJavet("""
                async function test() {
                  async function* gen() { yield* [1, 2, 3]; }
                  var result = await Array.fromAsync(gen(), function(v) { return v * 2; });
                  return JSON.stringify(result);
                }
                test()""");
    }

    @Test
    public void testFromAsyncWithNativeIterables() {
        assertStringWithJavet("""
                async function test() {
                  return JSON.stringify(await Array.fromAsync(new Set([1, 2, 3])));
                }
                test()""");
    }

    @Test
    public void testFromWithNativeIterables() {
        assertStringWithJavet("JSON.stringify(Array.from(new Set([1, 2, 2, 3])))");
        assertStringWithJavet("JSON.stringify(Array.from(new Map([['a', 1], ['b', 2]])))");
    }

    @Test
    public void testGetSpecies() {
        // Normal case: returns thisArg
        JSObject arrayConstructor = new JSObject();
        JSValue result = ArrayConstructor.getSpecies(context, arrayConstructor, new JSValue[]{});
        assertThat(result).isEqualTo(arrayConstructor);

        // Normal case: with different thisArg
        JSObject customConstructor = new JSObject();
        result = ArrayConstructor.getSpecies(context, customConstructor, new JSValue[]{});
        assertThat(result).isEqualTo(customConstructor);

        // Edge case: undefined thisArg
        result = ArrayConstructor.getSpecies(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    public void testGetSpeciesAccessorDescriptor() {
        assertBooleanWithJavet("""
                (() => {
                  const thisVal = {};
                  const desc = Object.getOwnPropertyDescriptor(Array, Symbol.species);
                  return typeof desc.get === 'function'
                    && desc.set === undefined
                    && desc.enumerable === false
                    && desc.configurable === true
                    && desc.get.name === 'get [Symbol.species]'
                    && typeof desc.get.call === 'function'
                    && desc.get.call(thisVal) === thisVal;
                })()
                """);
    }

    @Test
    public void testIsArray() {
        // Normal case: array
        JSArray arr = new JSArray();
        JSValue result = ArrayConstructor.isArray(context, JSUndefined.INSTANCE, new JSValue[]{arr});
        assertThat(result.isBooleanTrue()).isTrue();

        // Normal case: not array
        result = ArrayConstructor.isArray(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not array")});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayConstructor.isArray(context, JSUndefined.INSTANCE, new JSValue[]{new JSObject()});
        assertThat(result.isBooleanFalse()).isTrue();

        result = ArrayConstructor.isArray(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertThat(result.isBooleanFalse()).isTrue();

        // Edge case: no arguments
        result = ArrayConstructor.isArray(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.isBooleanFalse()).isTrue();
    }

    @Test
    public void testOf() {
        // Normal case: create array with elements
        JSValue result = ArrayConstructor.of(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1),
                new JSNumber(2),
                new JSNumber(3)
        });
        JSArray arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(3);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: no arguments
        result = ArrayConstructor.of(context, JSUndefined.INSTANCE, new JSValue[]{});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(0);

        // Edge case: single element
        result = ArrayConstructor.of(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello")});
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(1);
        assertThat(arr.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("hello");

        // Edge case: mixed types
        result = ArrayConstructor.of(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(1),
                new JSString("two"),
                JSBoolean.TRUE
        });
        arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(3);

        assertStringWithJavet(
                "JSON.stringify(Array.of(...[1,2,3]))");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                "typeof Array;");
        assertIntegerWithJavet(
                "Array.length;");
    }
}
