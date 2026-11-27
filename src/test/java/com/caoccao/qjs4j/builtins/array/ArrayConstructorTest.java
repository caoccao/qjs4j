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

package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.iterator.JSAsyncIterator;
import com.caoccao.qjs4j.core.promise.JSPromise;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Array constructor static methods.
 */
public class ArrayConstructorTest extends BaseJavetTest {

    private void assertElements(JSValue result, JSValue... expected) {
        JSArray array = result.asArray().orElseThrow();
        assertThat(array.getLength()).isEqualTo(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(array.get(i)).as("element %d", i).isEqualTo(expected[i]);
        }
    }

    private JSPromise awaitFromAsync(JSValue... args) {
        JSPromise promise = ArrayConstructor.fromAsync(context, JSUndefined.INSTANCE, args).asPromise().orElseThrow();
        awaitPromise(promise);
        return promise;
    }

    private JSFunction doubleNumbers() {
        return new JSNativeFunction(context, "double", 1,
                (ctx, receiver, args) -> args[0] instanceof JSNumber num ? JSNumber.of(num.value() * 2) : args[0]);
    }

    private JSValue from(JSValue... args) {
        return ArrayConstructor.from(context, JSUndefined.INSTANCE, args);
    }

    private JSValue fulfilledFromAsync(JSValue... args) {
        JSPromise promise = awaitFromAsync(args);
        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        return promise.getResult();
    }

    @Test
    public void testFrom() {
        JSArray source = new JSArray(context, numberArgs(1, 2, 3));
        assertElements(from(source), numberArgs(1, 2, 3));
        assertElements(from(new JSString("abc")), stringArgs("a", "b", "c"));
        assertElements(from(new JSArray(context, numberArgs(1, 2)), doubleNumbers()), numberArgs(2, 4));

        JSFunction upper = new JSNativeFunction(context, "upper", 1, (ctx, receiver,
                args) -> args[0] instanceof JSString str ? new JSString(str.value().toUpperCase()) : args[0]);
        assertElements(from(new JSString("hello"), upper), stringArgs("H", "E", "L", "L", "O"));
        assertElements(from(new JSArray(context)));
        assertElements(from(new JSString("")));
        assertPendingTypeError(context, from());
        assertElements(from(JSNumber.of(123)));
        assertPendingTypeError(context, from(source, new JSString("not a function")));
    }

    @Test
    public void testFromAsync() {
        JSArray source = new JSArray(context, numberArgs(1, 2, 3));
        assertElements(fulfilledFromAsync(JSAsyncIterator.fromArray(source, context)), numberArgs(1, 2, 3));
        assertElements(fulfilledFromAsync(new JSArray(context, numberArgs(10, 20))), numberArgs(10, 20));
        assertElements(fulfilledFromAsync(new JSArray(context, numberArgs(1, 2)), doubleNumbers()), numberArgs(2, 4));
        assertElements(fulfilledFromAsync(new JSString("abc")), stringArgs("a", "b", "c"));
        assertElements(fulfilledFromAsync(new JSArray(context)));
        assertThat(awaitFromAsync().getState()).isEqualTo(JSPromise.PromiseState.REJECTED);
        assertElements(fulfilledFromAsync(JSNumber.of(123)));
        assertThat(awaitFromAsync(source, new JSString("not a function")).getState())
                .isEqualTo(JSPromise.PromiseState.REJECTED);
    }

    @Test
    public void testFromAsyncRejectsFailedMappedPropertyDefinition() {
        JSPromise promise = context.eval("""
                Array.fromAsync.call(function () { return Object.freeze({}); },
                    {length: 1, 0: Promise.resolve(3)}, async value => value + 1)
                """).asPromise().orElseThrow();
        awaitPromise(promise);
        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.REJECTED);
        assertTypeError(promise.getResult());
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
        assertStringWithJavet("JSON.stringify(Array.from(new Set([1, 2, 2, 3])))",
                "JSON.stringify(Array.from(new Map([['a', 1], ['b', 2]])))");
    }

    @Test
    public void testGetSpecies() {
        // Normal case: returns thisArg
        JSObject arrayConstructor = new JSObject(context);
        JSValue result = ArrayConstructor.getSpecies(context, arrayConstructor, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(arrayConstructor);

        // Normal case: with different thisArg
        JSObject customConstructor = new JSObject(context);
        result = ArrayConstructor.getSpecies(context, customConstructor, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(customConstructor);

        // Edge case: undefined thisArg
        result = ArrayConstructor.getSpecies(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
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
        JSArray arr = new JSArray(context);
        BuiltinAssertions isArray = builtin(ArrayConstructor::isArray, JSUndefined.INSTANCE);
        isArray.expectValue(JSBoolean.TRUE, arr);

        // Normal case: not array
        isArray.expectValue(JSBoolean.FALSE, stringArgs("not array"));

        isArray.expectValue(JSBoolean.FALSE, new JSObject(context));

        isArray.expectValue(JSBoolean.FALSE, JSNull.INSTANCE);

        // Edge case: no arguments
        isArray.expectValue(JSBoolean.FALSE);
    }

    @Test
    public void testOf() {
        assertElements(ArrayConstructor.of(context, JSUndefined.INSTANCE, numberArgs(1, 2, 3)), numberArgs(1, 2, 3));
        assertElements(ArrayConstructor.of(context, JSUndefined.INSTANCE, JSValue.NO_ARGS));
        assertElements(ArrayConstructor.of(context, JSUndefined.INSTANCE, stringArgs("hello")), new JSString("hello"));

        JSValue[] mixed = {JSNumber.of(1), new JSString("two"), JSBoolean.TRUE};
        assertElements(ArrayConstructor.of(context, JSUndefined.INSTANCE, mixed), mixed);
        assertStringWithJavet("JSON.stringify(Array.of(...[1,2,3]))");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet("typeof Array;");
        assertIntegerWithJavet("Array.length;");
    }
}
