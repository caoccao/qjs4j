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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AsyncGeneratorPrototype methods.
 */
public class AsyncGeneratorPrototypeTest extends BaseTest {

    @Test
    public void testCreateDelayedGenerator() {
        // Test creating a delayed generator with some values
        JSValue[] values = {new JSNumber(1), new JSNumber(2), new JSNumber(3)};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createDelayedGenerator(context, values);

        assertThat(generator).isNotNull();
        assertThat(generator.toString()).isEqualTo("[object AsyncGenerator]");
        assertThat(generator.getState()).isEqualTo(JSAsyncGenerator.AsyncGeneratorState.SUSPENDED_START);
    }

    @Test
    public void testCreateFromPromises() {
        // Create some promises
        JSPromise promise1 = context.createJSPromise();
        promise1.fulfill(new JSNumber(10));

        JSPromise promise2 = context.createJSPromise();
        promise2.fulfill(new JSNumber(20));

        JSPromise[] promises = {promise1, promise2};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createFromPromises(context, promises);

        assertThat(generator).isNotNull();
        assertThat(generator.toString()).isEqualTo("[object AsyncGenerator]");
        assertThat(generator.getState()).isEqualTo(JSAsyncGenerator.AsyncGeneratorState.SUSPENDED_START);
    }

    @Test
    public void testCreateFromValues() {
        // Test creating a generator from values
        JSValue[] values = {new JSString("hello"), new JSString("world")};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createFromValues(context, values);

        assertThat(generator).isNotNull();
        assertThat(generator.toString()).isEqualTo("[object AsyncGenerator]");
        assertThat(generator.getState()).isEqualTo(JSAsyncGenerator.AsyncGeneratorState.SUSPENDED_START);
    }

    @Test
    public void testGeneratorCompletion() {
        // Create a generator with one value
        JSValue[] values = {new JSNumber(42)};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createFromValues(context, values);

        // Get first value
        JSValue result1 = AsyncGeneratorPrototype.next(context, generator, new JSValue[0]);
        JSPromise promise1 = (JSPromise) result1;
        assertThat(awaitPromise(promise1)).isTrue();

        JSObject resultObj1 = (JSObject) promise1.getResult();
        assertThat(resultObj1.get("value")).isEqualTo(new JSNumber(42));
        assertThat(resultObj1.get("done")).isEqualTo(JSBoolean.FALSE);

        // Get second value (should be done)
        JSValue result2 = AsyncGeneratorPrototype.next(context, generator, new JSValue[0]);
        JSPromise promise2 = (JSPromise) result2;
        assertThat(awaitPromise(promise2)).isTrue();

        JSObject resultObj2 = (JSObject) promise2.getResult();
        assertThat(resultObj2.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(resultObj2.get("done")).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testNext() {
        // Create a simple generator
        JSValue[] values = {new JSNumber(42)};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createFromValues(context, values);

        // Test next() method
        JSValue result = AsyncGeneratorPrototype.next(context, generator, new JSValue[0]);
        JSPromise promise = result.asPromise().orElseThrow();
        assertThat(awaitPromise(promise)).isTrue();

        // Check the result
        JSValue promiseResult = promise.getResult();
        JSObject resultObj = promiseResult.asObject().orElseThrow();

        assertThat(resultObj.get("value")).isEqualTo(new JSNumber(42));
        assertThat(resultObj.get("done")).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testNextOnNonAsyncGenerator() {
        // Test next() called on non-async generator
        JSValue result = AsyncGeneratorPrototype.next(context, new JSString("not a generator"), new JSValue[0]);
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testNextWithValue() {
        // Create a generator and test next with a value
        JSValue[] values = {new JSNumber(100)};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createFromValues(context, values);

        // Test next() with a value parameter
        JSValue result = AsyncGeneratorPrototype.next(context, generator, new JSValue[]{new JSString("ignored")});

        JSPromise promise = result.asPromise().orElseThrow();
        assertThat(awaitPromise(promise)).isTrue();

        // Check the result
        JSValue promiseResult = promise.getResult();
        JSObject resultObj = promiseResult.asObject().orElseThrow();

        assertThat(resultObj.get("value")).isEqualTo(new JSNumber(100));
        assertThat(resultObj.get("done")).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testReturn() {
        // Create a generator
        JSValue[] values = {new JSNumber(1), new JSNumber(2)};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createFromValues(context, values);

        // Test return() method
        JSValue returnValue = new JSString("early return");
        JSValue result = AsyncGeneratorPrototype.return_(context, generator, new JSValue[]{returnValue});

        JSPromise promise = result.asPromise().orElseThrow();
        assertThat(awaitPromise(promise)).isTrue();

        // Check the result
        JSValue promiseResult = promise.getResult();
        JSObject resultObj = promiseResult.asObject().orElseThrow();

        assertThat(resultObj.get("value")).isEqualTo(returnValue);
        assertThat(resultObj.get("done")).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testReturnOnNonAsyncGenerator() {
        // Test return() called on non-async generator
        JSValue result = AsyncGeneratorPrototype.return_(context, new JSNumber(123), new JSValue[]{new JSString("value")});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testThrow() {
        // Create a generator
        JSValue[] values = {new JSNumber(1)};
        JSAsyncGenerator generator = AsyncGeneratorPrototype.createFromValues(context, values);

        // Test throw() method
        JSValue exception = new JSString("test exception");
        JSValue result = AsyncGeneratorPrototype.throw_(context, generator, new JSValue[]{exception});

        JSPromise promise = result.asPromise().orElseThrow();
        assertThat(awaitPromise(promise)).isTrue();

        // The promise should be rejected with the exception
        assertThat(promise.getState()).isEqualTo(JSPromise.PromiseState.REJECTED);
        assertThat(promise.getResult()).isEqualTo(exception);
    }

    @Test
    public void testThrowOnNonAsyncGenerator() {
        // Test throw() called on non-async generator
        JSValue result = AsyncGeneratorPrototype.throw_(context, new JSObject(), new JSValue[]{new JSString("error")});
        assertTypeError(result);
        assertPendingException(context);
    }
}