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
 * Unit tests for FunctionPrototype methods.
 */
public class FunctionPrototypeTest extends BaseTest {

    @Test
    public void testApply() {
        // Create a test function that returns the sum of its arguments
        JSFunction testFunc = new JSNativeFunction("sum", 2, (ctx, thisArg, args) -> {
            double sum = 0;
            for (JSValue arg : args) {
                if (arg instanceof JSNumber num) {
                    sum += num.value();
                }
            }
            return new JSNumber(sum);
        });

        // Normal case: apply with array of arguments
        JSArray argsArray = new JSArray();
        argsArray.push(new JSNumber(1));
        argsArray.push(new JSNumber(2));
        argsArray.push(new JSNumber(3));

        JSValue result = FunctionPrototype.apply(context, testFunc, new JSValue[]{
                JSUndefined.INSTANCE, // thisArg
                argsArray
        });
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: apply with custom thisArg
        JSObject customThis = new JSObject();
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{
                customThis,
                argsArray
        });
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: apply with no arguments array
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{JSUndefined.INSTANCE});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: apply with null/undefined arguments array
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{JSUndefined.INSTANCE, JSNull.INSTANCE});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: apply with non-array arguments
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{
                JSUndefined.INSTANCE,
                new JSString("not an array")
        });
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: called on non-function
        result = FunctionPrototype.apply(context, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testBind() {
        // Create a test function that returns this.value + sum of arguments
        JSFunction testFunc = new JSNativeFunction("addToThis", 2, (ctx, thisArg, args) -> {
            double sum = 0;
            if (thisArg instanceof JSObject obj && obj.get("value") instanceof JSNumber num) {
                sum += num.value();
            }
            for (JSValue arg : args) {
                if (arg instanceof JSNumber num) {
                    sum += num.value();
                }
            }
            return new JSNumber(sum);
        });

        // Normal case: bind with thisArg
        JSObject boundThis = new JSObject();
        boundThis.set("value", new JSNumber(10));

        JSValue result = FunctionPrototype.bind(context, testFunc, new JSValue[]{
                boundThis,
                new JSNumber(1),
                new JSNumber(2)
        });
        JSBoundFunction boundFunc = result.asBoundFunction().orElseThrow();

        // Call the bound function
        JSValue callResult = boundFunc.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3)});
        assertEquals(16.0, callResult.asNumber().map(JSNumber::value).orElseThrow()); // 10 + 1 + 2 + 3

        // Normal case: bind with no pre-bound arguments
        result = FunctionPrototype.bind(context, testFunc, new JSValue[]{boundThis});
        boundFunc = result.asBoundFunction().orElseThrow();

        callResult = boundFunc.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5)});
        assertEquals(15.0, callResult.asNumber().map(JSNumber::value).orElseThrow()); // 10 + 5

        // Edge case: called on non-function
        result = FunctionPrototype.bind(context, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testCall() {
        // Create a test function that returns the sum of its arguments
        JSFunction testFunc = new JSNativeFunction("sum", 2, (ctx, thisArg, args) -> {
            double sum = 0;
            for (JSValue arg : args) {
                if (arg instanceof JSNumber num) {
                    sum += num.value();
                }
            }
            return new JSNumber(sum);
        });

        // Normal case: call with arguments
        JSValue result = FunctionPrototype.call(context, testFunc, new JSValue[]{
                JSUndefined.INSTANCE, // thisArg
                new JSNumber(1),
                new JSNumber(2),
                new JSNumber(3)
        });
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: call with custom thisArg
        JSObject customThis = new JSObject();
        result = FunctionPrototype.call(context, testFunc, new JSValue[]{
                customThis,
                new JSNumber(5)
        });
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: call with no arguments
        result = FunctionPrototype.call(context, testFunc, new JSValue[]{JSUndefined.INSTANCE});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: called on non-function
        result = FunctionPrototype.call(context, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetLength() {
        // Normal case: function with length
        JSFunction testFunc = new JSNativeFunction("test", 3, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.getLength(context, testFunc, new JSValue[]{});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Normal case: function with zero length
        JSFunction zeroFunc = new JSNativeFunction("zero", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.getLength(context, zeroFunc, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: called on non-function
        result = FunctionPrototype.getLength(context, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testGetName() {
        // Normal case: function with name
        JSFunction testFunc = new JSNativeFunction("myFunction", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.getName(context, testFunc, new JSValue[]{});
        assertEquals("myFunction", result.asString().map(JSString::value).orElseThrow());

        // Normal case: function without name
        JSFunction anonFunc = new JSNativeFunction("", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.getName(context, anonFunc, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Edge case: called on non-function
        result = FunctionPrototype.getName(context, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testToString() {
        // Normal case: function with name
        JSFunction testFunc = new JSNativeFunction("testFunction", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.toString_(context, testFunc, new JSValue[]{});
        assertEquals("function testFunction() { [native code] }", result.asString().map(JSString::value).orElseThrow());

        // Normal case: function without name (anonymous)
        JSFunction anonFunc = new JSNativeFunction("", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.toString_(context, anonFunc, new JSValue[]{});
        assertEquals("function anonymous() { [native code] }", result.asString().map(JSString::value).orElseThrow());

        // Edge case: called on non-function
        result = FunctionPrototype.toString_(context, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }
}