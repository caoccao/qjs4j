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

package com.caoccao.qjs4j.builtins.object;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FunctionPrototype methods.
 */
public class FunctionPrototypeTest extends BaseJavetTest {

    private JSFunction sumFunction() {
        return new JSNativeFunction(context, "sum", 2, (childContext, thisArg, args) -> {
            double sum = 0;
            for (JSValue arg : args) {
                if (arg instanceof JSNumber num) {
                    sum += num.value();
                }
            }
            return new JSNumber(sum);
        });
    }

    @Test
    public void testApply() {
        JSFunction testFunc = sumFunction();

        // Normal case: apply with array of arguments
        JSArray argsArray = nativeArray(1, 2, 3);

        BuiltinAssertions apply = builtin(FunctionPrototype::apply, testFunc);
        apply.expectNumber(6.0, JSUndefined.INSTANCE, // thisArg
                argsArray);

        // Normal case: apply with custom thisArg
        JSObject customThis = new JSObject(context);
        apply.expectNumber(6.0, customThis, argsArray);

        // Normal case: apply with no arguments array
        apply.expectNumber(0.0, JSUndefined.INSTANCE);

        // Normal case: apply with null/undefined arguments array
        apply.expectNumber(0.0, JSUndefined.INSTANCE, JSNull.INSTANCE);

        // Normal case: apply with array-like object
        JSObject arrayLikeObject = new JSObject(context);
        arrayLikeObject.set("0", new JSNumber(4));
        arrayLikeObject.set("1", new JSNumber(5));
        arrayLikeObject.set("2", new JSNumber(6));
        arrayLikeObject.set("length", new JSNumber(3));
        apply.expectNumber(15.0, JSUndefined.INSTANCE, arrayLikeObject);

        // Edge case: apply with non-array arguments
        JSValue result = callBuiltin(FunctionPrototype::apply, testFunc, JSUndefined.INSTANCE, "not an array");
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();

        // Edge case: called on non-function
        result = callBuiltin(FunctionPrototype::apply, new JSString("not a function"));
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testBind() {
        // Create a test function that returns this.value + sum of arguments
        JSFunction testFunc = new JSNativeFunction(context, "addToThis", 2, (childContext, thisArg, args) -> {
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
        JSObject boundThis = new JSObject(context);
        boundThis.set("value", new JSNumber(10));

        var bindMethod = builtin(FunctionPrototype::bind, testFunc);
        JSValue result = bindMethod.call(boundThis, 1, 2);
        JSBoundFunction boundFunc = result.asBoundFunction().orElseThrow();

        // Call the bound function
        JSValue callResult = boundFunc.call(context, JSUndefined.INSTANCE, nativeArguments(3));
        assertNumberValue(callResult, 16.0); // 10
                                             // +
                                             // 1
                                             // +
                                             // 2
                                             // +
                                             // 3

        // Normal case: bind with no pre-bound arguments
        result = bindMethod.call(boundThis);
        boundFunc = result.asBoundFunction().orElseThrow();

        callResult = boundFunc.call(context, JSUndefined.INSTANCE, nativeArguments(5));
        assertNumberValue(callResult, 15.0); // 10
                                             // +
                                             // 5

        // Edge case: called on non-function
        result = callBuiltin(FunctionPrototype::bind, new JSString("not a function"));
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testCall() {
        JSFunction testFunc = sumFunction();

        // Normal case: call with arguments
        BuiltinAssertions call = builtin(FunctionPrototype::call, testFunc);
        call.expectNumber(6.0, JSUndefined.INSTANCE, // thisArg
                1, 2, 3);

        // Normal case: call with custom thisArg
        JSObject customThis = new JSObject(context);
        call.expectNumber(5.0, customThis, 5);

        // Normal case: call with no arguments
        call.expectNumber(0.0, JSUndefined.INSTANCE);

        // Normal case: call with no thisArg and no arguments
        call.expectNumber(0.0);

        // Edge case: called on non-function
        assertTypeError(callBuiltin(FunctionPrototype::call, new JSString("not a function")));
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testCallerAndArgumentsAccessOnNonStrictFunction() {
        // Non-strict function accessing .caller and .arguments should not throw (returns undefined per QuickJS)
        assertBooleanWithJavet("(function f() { f.caller; return true; })()",
                "(function f() { f.arguments; return true; })()");

        // Strict function .caller and .arguments access should throw TypeError
        assertErrorWithJavet("(() => {}).caller", "(() => {}).arguments", "(function() { 'use strict'; }).caller",
                "(function() { 'use strict'; }).arguments");
    }

    @Test
    public void testFunctionCall() {
        assertStringWithJavet("""
                const a = {};
                a.x = function f(a, b) {
                  if (a === b) {
                    // Handle +/-0 vs. -/+0
                    return a !== 0 || 1 / a === 1 / b;
                  }
                  // Handle NaN vs. NaN
                  return a !== a && b !== b;
                };
                a.f = a.x;
                JSON.stringify(a.f(2,3), a.f('a','b'), a.f(null, undefined));""");
        assertIntegerWithJavet("""
                function f1(a, b) {
                    return a + b;
                }
                f1(2, 3);""", """
                function f2(a, b) {
                    return a - b;
                }
                f2(5, 7);""");
    }

    @Test
    public void testFunctionConstructor() {
        // Normal case: function with parameters and body
        var functionConstructor = builtin(FunctionConstructor::call, JSUndefined.INSTANCE);
        JSValue result = functionConstructor.call("a", "b", "return a + b;");
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, nativeArguments(2, 3));
            assertNumberValue(callResult, 5.0);
        });

        // Normal case: function with only body (no parameters)
        result = functionConstructor.call("return 42;");
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
            assertNumberValue(callResult, 42.0);
        });

        // Normal case: function with empty body
        result = functionConstructor.call();
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
            assertThat(callResult).isEqualTo(JSUndefined.INSTANCE);
        });

        // Normal case: function with multiple parameters
        result = functionConstructor.call("x", "y", "z", "return x * y + z;");
        assertThat(result).isInstanceOfSatisfying(JSFunction.class, func -> {
            JSValue callResult = func.call(context, JSUndefined.INSTANCE, nativeArguments(3, 4, 5));
            assertNumberValue(callResult, 17.0);
        });

        // Edge case: syntax error in function body
        result = functionConstructor.call("a", "return a +"); // Incomplete expression
        assertPendingSyntaxError(context, result);
    }

    @Test
    public void testFunctionConstructorWithJavet() {
        assertIntegerWithJavet(
                // Test basic function creation with parameters and body
                "new Function('a', 'b', 'return a + b;')(2, 3)", "new Function('x', 'y', 'return x * y;')(4, 5)",
                // Test function with single parameter
                "new Function('n', 'return n * 2;')(21)",
                // Test function with multiple statements in body
                "new Function('x', 'var y = x * 2; return y + 1;')(5)",
                // Test function that uses this context
                "var obj = {value: 10}; new Function('return this.value;').call(obj)",
                // Test function with closure
                "(function() { var x = 5; var f = new Function('y', 'return y * 2;'); return f(x); })()",
                // Test function length property (number of parameters)
                "new Function('a', 'b', 'c', 'return a + b + c;').length", "new Function('return 1;').length",
                "new Function('x', 'return x;').length",
                // Test function with only body (no parameters)
                "new Function('return 42;')()");
        assertDoubleWithJavet("new Function('return Math.PI * 2;')()");

        assertObjectWithJavet(
                // Test function with empty body
                "new Function()()");

        assertStringWithJavet("new Function('name', 'return \"Hello, \" + name;')('World')",
                "new Function('a', 'b', 'return a + b;')('foo', 'bar')",
                // Test function name property
                "new Function('x', 'return x;').name");

        assertBooleanWithJavet(
                // Test boolean return
                "new Function('x', 'return x > 5;')(10)", "new Function('a', 'b', 'return a === b;')(5, 5)");
    }

    @Test
    public void testFunctionEdgeCasesWithJavet() {
        assertIntegerWithJavet("""
                (() => {
                  function sum(a, b, c) {
                    return a + b + c;
                  }
                  return sum.apply(null, { 0: 1, 1: 2, 2: 3, length: 3 });
                })()""", """
                (() => {
                  function sum(a, b, c) {
                    return a + b + c;
                  }
                  const args = (function() { return arguments; })(1, 2, 3);
                  return sum.apply(null, args);
                })()""");

        assertBooleanWithJavet("""
                (() => {
                  function C(a, b) {
                    this.sum = a + b;
                  }
                  const Bound = C.bind({ sum: -1 }, 2);
                  const instance = new Bound(5);
                  return instance.sum === 7 && instance instanceof C && instance instanceof Bound;
                })()""", """
                (() => {
                  const methods = ['call', 'apply', 'bind', 'toString'];
                  const methodsOk = methods.every((name) => {
                    const d = Object.getOwnPropertyDescriptor(Function.prototype, name);
                    return d.writable === true && d.enumerable === false && d.configurable === true;
                  });
                  const constructorDesc = Object.getOwnPropertyDescriptor(Function, 'prototype');
                  return methodsOk
                    && constructorDesc.writable === false
                    && constructorDesc.enumerable === false
                    && constructorDesc.configurable === false;
                })()""");

        assertErrorWithJavet("new Function(Symbol('x'))");

        JSValue nonConstructorBoundFunction = context.eval("(() => 1).bind(null)");
        assertThat(JSTypeChecking.isConstructor(nonConstructorBoundFunction)).isFalse();
    }

    @Test
    public void testGetLength() {
        // Normal case: function with length
        JSFunction testFunc = new JSNativeFunction(context, "test", 3,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = callBuiltin(FunctionPrototype::getLength, testFunc);
        assertNumberValue(result, 3.0);

        // Normal case: function with zero length
        JSFunction zeroFunc = new JSNativeFunction(context, "zero", 0,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        result = callBuiltin(FunctionPrototype::getLength, zeroFunc);
        assertNumberValue(result, 0.0);

        // Edge case: called on non-function
        result = callBuiltin(FunctionPrototype::getLength, new JSString("not a function"));
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testGetName() {
        // Normal case: function with name
        JSFunction testFunc = new JSNativeFunction(context, "myFunction", 1,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = callBuiltin(FunctionPrototype::getName, testFunc);
        assertStringValue(result, "myFunction");

        // Normal case: function without name
        JSFunction anonFunc = new JSNativeFunction(context, "", 1,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        result = callBuiltin(FunctionPrototype::getName, anonFunc);
        assertStringValue(result, "");

        // Edge case: called on non-function
        result = callBuiltin(FunctionPrototype::getName, new JSString("not a function"));
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testPrototype() {
        assertObjectWithJavet("Object.getOwnPropertyNames(Function.prototype).sort()");
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
        assertStringWithJavet("try { Function.prototype.caller; 'no error' } catch (e) { `${e.name}: ${e.message}` }",
                // Test that setting 'caller' throws TypeError
                "try { Function.prototype.caller = 'test'; 'no error' } catch (e) { `${e.name}: ${e.message}` }");

        // Test property descriptor
        assertObjectWithJavet("Object.getOwnPropertyDescriptor(Function.prototype, 'caller')");
    }

    @Test
    public void testPrototypeLength() {
        // Test that 'length' property value is 0
        assertIntegerWithJavet("Function.prototype.length");

        // Test property descriptor
        assertStringWithJavet("JSON.stringify(Object.getOwnPropertyDescriptor(Function.prototype, 'length'))");

        assertBooleanWithJavet(
                // Test that length is not enumerable
                "Object.keys(Function.prototype).includes('length')",
                // Test that length is configurable
                "Object.getOwnPropertyDescriptor(Function.prototype, 'length').configurable");

        assertIntegerWithJavet("Function.prototype.length = 5; Function.prototype.length");

        // Test that length is not writable
        assertBooleanWithJavet("Object.getOwnPropertyDescriptor(Function.prototype, 'length').writable");
    }

    @Test
    public void testPrototypeName() {
        assertStringWithJavet(
                // Test that 'name' property value is empty string
                "Function.prototype.name", "Function.prototype.name = 'test';");

        // Test property descriptor
        assertObjectWithJavet("Object.getOwnPropertyDescriptor(Function.prototype, 'name')");

        assertBooleanWithJavet(
                // Test that name is not enumerable
                "Object.keys(Function.prototype).includes('name')",
                // Test that name is configurable
                "Object.getOwnPropertyDescriptor(Function.prototype, 'name').configurable");
    }

    @Test
    public void testToString() {
        // Normal case: function with name (QuickJS format)
        JSFunction testFunc = new JSNativeFunction(context, "testFunction", 1,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        JSValue result = callBuiltin(FunctionPrototype::toString_, testFunc);
        assertStringValue(result, "function testFunction() { [native code] }");

        // Normal case: function without name
        JSFunction anonFunc = new JSNativeFunction(context, "", 1,
                (childContext, thisArg, args) -> JSUndefined.INSTANCE);
        result = callBuiltin(FunctionPrototype::toString_, anonFunc);
        assertStringValue(result, "function () { [native code] }");

        // Edge case: called on non-function
        result = callBuiltin(FunctionPrototype::toString_, new JSString("not a function"));
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();
        context.clearPendingException(); // Clear the exception before next test

        assertStringWithJavet("""
                function f1() {
                    return 1; // Test
                }
                String(f1);""", """
                function f2() {
                    return 1; // Test
                }
                f2.toString();""");
    }
}
