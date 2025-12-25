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

        JSValue result = FunctionPrototype.apply(ctx, testFunc, new JSValue[]{
                JSUndefined.INSTANCE, // thisArg
                argsArray
        });
        assertInstanceOf(JSNumber.class, result);
        assertEquals(6.0, ((JSNumber) result).value());

        // Normal case: apply with custom thisArg
        JSObject customThis = new JSObject();
        result = FunctionPrototype.apply(ctx, testFunc, new JSValue[]{
                customThis,
                argsArray
        });
        assertInstanceOf(JSNumber.class, result);
        assertEquals(6.0, ((JSNumber) result).value());

        // Normal case: apply with no arguments array
        result = FunctionPrototype.apply(ctx, testFunc, new JSValue[]{JSUndefined.INSTANCE});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Normal case: apply with null/undefined arguments array
        result = FunctionPrototype.apply(ctx, testFunc, new JSValue[]{JSUndefined.INSTANCE, JSNull.INSTANCE});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Edge case: apply with non-array arguments
        result = FunctionPrototype.apply(ctx, testFunc, new JSValue[]{
                JSUndefined.INSTANCE,
                new JSString("not an array")
        });
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: called on non-function
        result = FunctionPrototype.apply(ctx, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
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

        JSValue result = FunctionPrototype.bind(ctx, testFunc, new JSValue[]{
                boundThis,
                new JSNumber(1),
                new JSNumber(2)
        });
        assertInstanceOf(JSBoundFunction.class, result);
        JSBoundFunction boundFunc = (JSBoundFunction) result;

        // Call the bound function
        JSValue callResult = boundFunc.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(3)});
        assertInstanceOf(JSNumber.class, callResult);
        assertEquals(16.0, ((JSNumber) callResult).value()); // 10 + 1 + 2 + 3

        // Normal case: bind with no pre-bound arguments
        result = FunctionPrototype.bind(ctx, testFunc, new JSValue[]{boundThis});
        assertInstanceOf(JSBoundFunction.class, result);
        boundFunc = (JSBoundFunction) result;

        callResult = boundFunc.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5)});
        assertInstanceOf(JSNumber.class, callResult);
        assertEquals(15.0, ((JSNumber) callResult).value()); // 10 + 5

        // Edge case: called on non-function
        result = FunctionPrototype.bind(ctx, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
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
        JSValue result = FunctionPrototype.call(ctx, testFunc, new JSValue[]{
                JSUndefined.INSTANCE, // thisArg
                new JSNumber(1),
                new JSNumber(2),
                new JSNumber(3)
        });
        assertInstanceOf(JSNumber.class, result);
        assertEquals(6.0, ((JSNumber) result).value());

        // Normal case: call with custom thisArg
        JSObject customThis = new JSObject();
        result = FunctionPrototype.call(ctx, testFunc, new JSValue[]{
                customThis,
                new JSNumber(5)
        });
        assertInstanceOf(JSNumber.class, result);
        assertEquals(5.0, ((JSNumber) result).value());

        // Normal case: call with no arguments
        result = FunctionPrototype.call(ctx, testFunc, new JSValue[]{JSUndefined.INSTANCE});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Edge case: called on non-function
        result = FunctionPrototype.call(ctx, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetLength() {
        // Normal case: function with length
        JSFunction testFunc = new JSNativeFunction("test", 3, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.getLength(ctx, testFunc, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(3.0, ((JSNumber) result).value());

        // Normal case: function with zero length
        JSFunction zeroFunc = new JSNativeFunction("zero", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.getLength(ctx, zeroFunc, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Edge case: called on non-function
        result = FunctionPrototype.getLength(ctx, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetName() {
        // Normal case: function with name
        JSFunction testFunc = new JSNativeFunction("myFunction", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.getName(ctx, testFunc, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("myFunction", ((JSString) result).getValue());

        // Normal case: function without name
        JSFunction anonFunc = new JSNativeFunction("", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.getName(ctx, anonFunc, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        assertEquals("", ((JSString) result).getValue());

        // Edge case: called on non-function
        result = FunctionPrototype.getName(ctx, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testToStringMethod() {
        // Normal case: function with name
        JSFunction testFunc = new JSNativeFunction("testFunction", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.toStringMethod(ctx, testFunc, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        String str = ((JSString) result).getValue();
        assertTrue(str.contains("function testFunction"));
        assertTrue(str.contains("[native code]"));

        // Normal case: function without name (anonymous)
        JSFunction anonFunc = new JSNativeFunction("", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.toStringMethod(ctx, anonFunc, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        str = ((JSString) result).getValue();
        assertTrue(str.contains("function anonymous"));
        assertTrue(str.contains("[native code]"));

        // Edge case: called on non-function
        result = FunctionPrototype.toStringMethod(ctx, new JSString("not a function"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}