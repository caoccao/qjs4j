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
 * Unit tests for FunctionPrototype methods.
 */
public class FunctionPrototypeTest extends BaseJavetTest {

    @Test
    public void testApply() {
        // Create a test function that returns the sum of its arguments
        JSFunction testFunc = new JSNativeFunction("sum", 2, (childContext, thisArg, args) -> {
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
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(6.0));

        // Normal case: apply with custom thisArg
        JSObject customThis = new JSObject();
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{
                customThis,
                argsArray
        });
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(6.0));

        // Normal case: apply with no arguments array
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{JSUndefined.INSTANCE});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(0.0));

        // Normal case: apply with null/undefined arguments array
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{JSUndefined.INSTANCE, JSNull.INSTANCE});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(0.0));

        // Edge case: apply with non-array arguments
        result = FunctionPrototype.apply(context, testFunc, new JSValue[]{
                JSUndefined.INSTANCE,
                new JSString("not an array")
        });
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();

        // Edge case: called on non-function
        result = FunctionPrototype.apply(context, new JSString("not a function"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testBind() {
        // Create a test function that returns this.value + sum of arguments
        JSFunction testFunc = new JSNativeFunction("addToThis", 2, (childContext, thisArg, args) -> {
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
        assertThat(callResult).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(16.0)); // 10 + 1 + 2 + 3

        // Normal case: bind with no pre-bound arguments
        result = FunctionPrototype.bind(context, testFunc, new JSValue[]{boundThis});
        boundFunc = result.asBoundFunction().orElseThrow();

        callResult = boundFunc.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(5)});
        assertThat(callResult).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(15.0)); // 10 + 5

        // Edge case: called on non-function
        result = FunctionPrototype.bind(context, new JSString("not a function"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testCall() {
        // Create a test function that returns the sum of its arguments
        JSFunction testFunc = new JSNativeFunction("sum", 2, (childContext, thisArg, args) -> {
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
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(6.0));

        // Normal case: call with custom thisArg
        JSObject customThis = new JSObject();
        result = FunctionPrototype.call(context, testFunc, new JSValue[]{
                customThis,
                new JSNumber(5)
        });
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(5.0));

        // Normal case: call with no arguments
        result = FunctionPrototype.call(context, testFunc, new JSValue[]{JSUndefined.INSTANCE});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(0.0));

        // Edge case: called on non-function
        result = FunctionPrototype.call(context, new JSString("not a function"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testFunctionConstructor() {
        // Normal case: function with parameters and body
        JSValue result = FunctionConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("a"),
                new JSString("b"),
                new JSString("return a + b;")
        });
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, new JSValue[]{
                    new JSNumber(2),
                    new JSNumber(3)
            });
            assertThat(callResult).isInstanceOfSatisfying(JSNumber.class, num ->
                    assertThat(num.value()).isEqualTo(5.0));
        });

        // Normal case: function with only body (no parameters)
        result = FunctionConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("return 42;")
        });
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, new JSValue[]{});
            assertThat(callResult).isInstanceOfSatisfying(JSNumber.class, num ->
                    assertThat(num.value()).isEqualTo(42.0));
        });

        // Normal case: function with empty body
        result = FunctionConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, new JSValue[]{});
            assertThat(callResult).isEqualTo(JSUndefined.INSTANCE);
        });

        // Normal case: function with multiple parameters
        result = FunctionConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("x"),
                new JSString("y"),
                new JSString("z"),
                new JSString("return x * y + z;")
        });
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, new JSValue[]{
                    new JSNumber(3),
                    new JSNumber(4),
                    new JSNumber(5)
            });
            assertThat(callResult).isInstanceOfSatisfying(JSNumber.class, num ->
                    assertThat(num.value()).isEqualTo(17.0));
        });

        // Edge case: syntax error in function body
        result = FunctionConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("a"),
                new JSString("return a +")  // Incomplete expression
        });
        assertSyntaxError(result);
        assertPendingException(context);
    }

    @Test
    public void testFunctionConstructorWithJavet() {
        assertIntegerWithJavet(
                // Test basic function creation with parameters and body
                "new Function('a', 'b', 'return a + b;')(2, 3)",
                "new Function('x', 'y', 'return x * y;')(4, 5)",
                // Test function with single parameter
                "new Function('n', 'return n * 2;')(21)",
                // Test function with multiple statements in body
                "new Function('x', 'var y = x * 2; return y + 1;')(5)",
                // Test function that uses this context
                "var obj = {value: 10}; new Function('return this.value;').call(obj)",
                // Test function with closure
                "(function() { var x = 5; var f = new Function('y', 'return y * 2;'); return f(x); })()",
                // Test function length property (number of parameters)
                "new Function('a', 'b', 'c', 'return a + b + c;').length",
                "new Function('return 1;').length",
                "new Function('x', 'return x;').length",
                // Test function with only body (no parameters)
                "new Function('return 42;')()");
        assertDoubleWithJavet(
                "new Function('return Math.PI * 2;')()");

        assertObjectWithJavet(
                // Test function with empty body
                "new Function()()");

        assertStringWithJavet(
                "new Function('name', 'return \"Hello, \" + name;')('World')",
                "new Function('a', 'b', 'return a + b;')('foo', 'bar')",
                // Test function name property
                "new Function('x', 'return x;').name");

        assertBooleanWithJavet(
                // Test boolean return
                "new Function('x', 'return x > 5;')(10)",
                "new Function('a', 'b', 'return a === b;')(5, 5)");
    }

    @Test
    public void testGetLength() {
        // Normal case: function with length
        JSFunction testFunc = new JSNativeFunction("test", 3, (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.getLength(context, testFunc, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(3.0));

        // Normal case: function with zero length
        JSFunction zeroFunc = new JSNativeFunction("zero", 0, (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.getLength(context, zeroFunc, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(0.0));

        // Edge case: called on non-function
        result = FunctionPrototype.getLength(context, new JSString("not a function"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testGetName() {
        // Normal case: function with name
        JSFunction testFunc = new JSNativeFunction("myFunction", 1, (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.getName(context, testFunc, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("myFunction"));

        // Normal case: function without name
        JSFunction anonFunc = new JSNativeFunction("", 1, (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.getName(context, anonFunc, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo(""));

        // Edge case: called on non-function
        result = FunctionPrototype.getName(context, new JSString("not a function"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testPrototype() {
        assertObjectWithJavet(
                "Object.getOwnPropertyNames(Function.prototype).sort()");
    }

    @Test
    public void testPrototypeArguments() {
        // Test that 'arguments' property exists
        assertBooleanWithJavet("'arguments' in Function.prototype");

        assertStringWithJavet(
                // Test that accessing 'arguments' throws TypeError
                "try { Function.prototype.arguments; 'no error' } catch (e) { `${e.name}: ${e.message}` }",
                // Test that setting 'arguments' throws TypeError
                "try { Function.prototype.arguments = 'test'; 'no error' } catch (e) { `${e.name}: ${e.message}` }",
                // Test property descriptor
                "JSON.stringify(Object.getOwnPropertyDescriptor(Function.prototype, 'arguments'))");
    }

    @Test
    public void testPrototypeCaller() {
        // Test that 'caller' property exists
        assertBooleanWithJavet("'caller' in Function.prototype");

        // Test that accessing 'caller' throws TypeError
        assertStringWithJavet(
                "try { Function.prototype.caller; 'no error' } catch (e) { `${e.name}: ${e.message}` }");

        // Test that setting 'caller' throws TypeError
        assertStringWithJavet(
                "try { Function.prototype.caller = 'test'; 'no error' } catch (e) { `${e.name}: ${e.message}` }");

        // Test property descriptor
        assertObjectWithJavet(
                "Object.getOwnPropertyDescriptor(Function.prototype, 'caller')");
    }

    @Test
    public void testPrototypeLength() {
        // Test that 'length' property value is 0
        assertIntegerWithJavet("Function.prototype.length");

        // Test property descriptor
        assertStringWithJavet(
                "JSON.stringify(Object.getOwnPropertyDescriptor(Function.prototype, 'length'))");

        assertBooleanWithJavet(
                // Test that length is not enumerable
                "Object.keys(Function.prototype).includes('length')",
                // Test that length is configurable
                "Object.getOwnPropertyDescriptor(Function.prototype, 'length').configurable");

        // Test that length is not writable
        assertErrorWithJavet(
                "'use strict';\nFunction.prototype.length = 5; Function.prototype.length");
    }

    @Test
    public void testPrototypeName() {
        // Test that 'name' property value is empty string
        assertStringWithJavet("Function.prototype.name");

        // Test property descriptor
        assertObjectWithJavet(
                "Object.getOwnPropertyDescriptor(Function.prototype, 'name')");

        // Test that name is not writable
        assertErrorWithJavet(
                "'use strict';\nFunction.prototype.name = 'test';");

        assertBooleanWithJavet(
                // Test that name is not enumerable
                "Object.keys(Function.prototype).includes('name')",
                // Test that name is configurable
                "Object.getOwnPropertyDescriptor(Function.prototype, 'name').configurable");
    }

    @Test
    public void testToString() {
        // Normal case: function with name
        JSFunction testFunc = new JSNativeFunction("testFunction", 1, (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = FunctionPrototype.toString_(context, testFunc, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("function testFunction() { [native code] }"));

        // Normal case: function without name (anonymous)
        JSFunction anonFunc = new JSNativeFunction("", 1, (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        result = FunctionPrototype.toString_(context, anonFunc, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("function anonymous() { [native code] }"));

        // Edge case: called on non-function
        result = FunctionPrototype.toString_(context, new JSString("not a function"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }
}
