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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for GeneratorPrototype methods.
 */
public class GeneratorPrototypeTest extends BaseTest {

    @Test
    public void testCustomGenerator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create a custom generator that yields specific values
        final int[] counter = {0};
        JSGenerator generator = JSGenerator.fromIteratorFunction(() -> {
            counter[0]++;
            if (counter[0] == 1) {
                return JSIterator.IteratorResult.of(new JSString("first"));
            } else if (counter[0] == 2) {
                return JSIterator.IteratorResult.of(new JSString("second"));
            } else {
                return JSIterator.IteratorResult.done();
            }
        });

        // Test the custom generator
        JSValue result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals("first", iteratorResult.get("value").asString().map(JSString::value).orElse(""));
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals("second", iteratorResult.get("value").asString().map(JSString::value).orElse(""));
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));
    }

    @Test
    public void testEmptyGenerator() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create an empty generator
        JSArray emptyArray = new JSArray();
        JSGenerator generator = JSGenerator.fromArray(emptyArray);

        // Normal case: next() on empty generator
        JSValue result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Normal case: return on empty generator
        JSGenerator generator2 = JSGenerator.fromArray(emptyArray);
        result = GeneratorPrototype.returnMethod(ctx, generator2, new JSValue[]{new JSString("done")});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals("done", iteratorResult.get("value").asString().map(JSString::value).orElse(""));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));
    }

    @Test
    public void testNext() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create a simple generator from array
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        array.push(new JSNumber(3));
        JSGenerator generator = JSGenerator.fromArray(array);

        // Normal case: first next() call
        JSValue result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(1.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        // Normal case: second next() call
        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(2.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        // Normal case: third next() call
        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(3.0, iteratorResult.get("value").asNumber().map(JSNumber::value).orElse(0.0));
        assertEquals(JSBoolean.FALSE, iteratorResult.get("done"));

        // Normal case: fourth next() call (done)
        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Normal case: subsequent calls after done
        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Normal case: next() with value argument (ignored in this simple implementation)
        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{new JSString("ignored")});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-generator
        result = GeneratorPrototype.next(ctx, new JSString("not a generator"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: called on null
        result = GeneratorPrototype.next(ctx, JSNull.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testReturn() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create a generator
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        array.push(new JSNumber(3));
        JSGenerator generator = JSGenerator.fromArray(array);

        // Normal case: return with value
        JSValue result = GeneratorPrototype.returnMethod(ctx, generator, new JSValue[]{new JSString("returned")});
        JSObject iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals("returned", iteratorResult.get("value").asString().map(JSString::value).orElse(""));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Normal case: subsequent next() calls after return
        result = GeneratorPrototype.next(ctx, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals("returned", iteratorResult.get("value").asString().map(JSString::value).orElse(""));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Normal case: return without value (undefined)
        JSGenerator generator2 = JSGenerator.fromArray(array);
        result = GeneratorPrototype.returnMethod(ctx, generator2, new JSValue[]{});
        iteratorResult = result.asObject().orElse(null);
        assertNotNull(iteratorResult);
        assertEquals(JSUndefined.INSTANCE, iteratorResult.get("value"));
        assertEquals(JSBoolean.TRUE, iteratorResult.get("done"));

        // Edge case: called on non-generator
        result = GeneratorPrototype.returnMethod(ctx, new JSObject(), new JSValue[]{new JSNumber(42)});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: called on undefined
        result = GeneratorPrototype.returnMethod(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testThrow() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Create a generator
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        JSGenerator generator = JSGenerator.fromArray(array);

        // Normal case: throw with exception
        JSValue result = GeneratorPrototype.throwMethod(ctx, generator, new JSValue[]{new JSString("test exception")});
        // In this simplified implementation, throw completes the generator and returns an error
        assertError(result);
        assertPendingException(ctx);

        // Normal case: throw without exception (undefined)
        JSGenerator generator2 = JSGenerator.fromArray(array);
        result = GeneratorPrototype.throwMethod(ctx, generator2, new JSValue[]{});
        assertError(result);
        assertPendingException(ctx);

        // Edge case: called on non-generator
        result = GeneratorPrototype.throwMethod(ctx, new JSNumber(123), new JSValue[]{new JSString("error")});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: called on null
        result = GeneratorPrototype.throwMethod(ctx, JSNull.INSTANCE, new JSValue[]{new JSString("error")});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}