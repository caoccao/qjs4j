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

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ArrayPrototype methods.
 */
public class ArrayPrototypeTest extends BaseJavetTest {
    // Helper method to create a simple test function
    private JSFunction createTestFunction(Function<JSValue[], JSValue> impl) {
        return new JSNativeFunction(
                "test",
                1,
                (context, thisArg, args) -> impl.apply(args));
    }

    @Test
    public void testAt() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: positive index
        JSValue result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Negative index
        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(-1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(-2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Out of bounds
        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(3)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(-4)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // No arguments
        result = ArrayPrototype.at(context, arr, new JSValue[]{});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.at(context, emptyArr, new JSValue[]{new JSNumber(0)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: at on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.at(context, nonArray, new JSValue[]{new JSNumber(0)}));
        assertPendingException(context);
    }

    @Test
    public void testConcat() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));

        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        // Normal case: concat arrays
        JSValue result = ArrayPrototype.concat(context, arr, new JSValue[]{arr2});
        JSArray concatenated = result.asArray().orElseThrow();
        assertThat(concatenated.getLength()).isEqualTo(4);
        assertThat(concatenated.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(concatenated.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(concatenated.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(concatenated.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Concat with values
        result = ArrayPrototype.concat(context, arr, new JSValue[]{new JSNumber(5), arr2});
        concatenated = result.asArray().orElseThrow();
        assertThat(concatenated.getLength()).isEqualTo(5);
        assertThat(concatenated.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // Edge case: concat with empty arrays
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.concat(context, emptyArr, new JSValue[]{arr});
        concatenated = result.asArray().orElseThrow();
        assertThat(concatenated.getLength()).isEqualTo(2);

        // Edge case: concat on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.concat(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testCopyWithin() {
        // Normal case: copyWithin(0, 3, 5)
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));
        arr.push(new JSNumber(5));

        JSValue result = ArrayPrototype.copyWithin(context, arr, new JSValue[]{new JSNumber(0), new JSNumber(3), new JSNumber(5)});
        assertThat(result).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(5);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(arr.get(4).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // With negative indices
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));
        arr2.push(new JSNumber(5));

        result = ArrayPrototype.copyWithin(context, arr2, new JSValue[]{new JSNumber(-2), new JSNumber(0), new JSNumber(2)});
        assertThat(result).isSameAs(arr2);
        assertThat(arr2.getLength()).isEqualTo(5);
        assertThat(arr2.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr2.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr2.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr2.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr2.get(4).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.copyWithin(context, emptyArr, new JSValue[]{new JSNumber(0), new JSNumber(1)});
        assertThat(result).isSameAs(emptyArr);

        // Edge case: no arguments
        JSArray arr3 = new JSArray();
        arr3.push(new JSNumber(1));
        result = ArrayPrototype.copyWithin(context, arr3, new JSValue[]{});
        assertThat(result).isSameAs(arr3);

        // Edge case: copyWithin on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.copyWithin(context, nonArray, new JSValue[]{new JSNumber(0), new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testEvery() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(4));
        arr.push(new JSNumber(6));

        // Normal case: all even
        JSFunction isEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.every(context, arr, new JSValue[]{isEvenFn});
        assertThat(result.isBooleanTrue()).isTrue();

        // Not all pass
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        result = ArrayPrototype.every(context, arr2, new JSValue[]{isEvenFn});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: empty array (vacuously true)
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.every(context, emptyArr, new JSValue[]{isEvenFn});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.every(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: every on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.every(context, nonArray, new JSValue[]{isEvenFn}));
        assertPendingException(context);
    }

    @Test
    public void testFill() {
        // Normal case: fill entire array
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        JSValue result = ArrayPrototype.fill(context, arr, new JSValue[]{new JSNumber(0)});
        assertThat(result).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(4);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Fill with start index
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        result = ArrayPrototype.fill(context, arr2, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertThat(result).isSameAs(arr2);
        assertThat(arr2.getLength()).isEqualTo(4);
        assertThat(arr2.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr2.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr2.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr2.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Fill with start and end
        JSArray arr3 = new JSArray();
        arr3.push(new JSNumber(1));
        arr3.push(new JSNumber(2));
        arr3.push(new JSNumber(3));
        arr3.push(new JSNumber(4));

        result = ArrayPrototype.fill(context, arr3, new JSValue[]{new JSNumber(0), new JSNumber(1), new JSNumber(3)});
        assertThat(result).isSameAs(arr3);
        assertThat(arr3.getLength()).isEqualTo(4);
        assertThat(arr3.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr3.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr3.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr3.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // With negative indices
        JSArray arr4 = new JSArray();
        arr4.push(new JSNumber(1));
        arr4.push(new JSNumber(2));
        arr4.push(new JSNumber(3));
        arr4.push(new JSNumber(4));

        result = ArrayPrototype.fill(context, arr4, new JSValue[]{new JSNumber(0), new JSNumber(-3), new JSNumber(-1)});
        assertThat(result).isSameAs(arr4);
        assertThat(arr4.getLength()).isEqualTo(4);
        assertThat(arr4.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr4.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr4.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr4.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.fill(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertThat(result).isSameAs(emptyArr);

        // Edge case: fill on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.fill(context, nonArray, new JSValue[]{new JSNumber(0)}));
        assertPendingException(context);
    }

    @Test
    public void testFilter() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case: filter even numbers
        JSFunction evenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.filter(context, arr, new JSValue[]{evenFn});
        JSArray filtered = result.asArray().orElseThrow();
        assertThat(filtered.getLength()).isEqualTo(2);
        assertThat(filtered.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(filtered.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Edge case: no elements pass filter
        JSFunction noneFn = createTestFunction(args -> JSBoolean.FALSE);
        result = ArrayPrototype.filter(context, arr, new JSValue[]{noneFn});
        JSArray arr1 = result.asArray().orElseThrow();
        assertThat(arr1.getLength()).isEqualTo(0);

        // Edge case: all elements pass filter
        JSFunction allFn = createTestFunction(args -> JSBoolean.TRUE);
        result = ArrayPrototype.filter(context, arr, new JSValue[]{allFn});
        arr1 = result.asArray().orElseThrow();
        assertThat(arr1.getLength()).isEqualTo(4);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.filter(context, emptyArr, new JSValue[]{evenFn});
        arr1 = result.asArray().orElseThrow();
        assertThat(arr1.getLength()).isEqualTo(0);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.filter(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: filter on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.filter(context, nonArray, new JSValue[]{evenFn}));
        assertPendingException(context);
    }

    @Test
    public void testFind() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: find even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.find(context, arr, new JSValue[]{findEvenFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.find(context, arr, new JSValue[]{findNegativeFn});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.find(context, emptyArr, new JSValue[]{findEvenFn});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: no callback
        assertTypeError(ArrayPrototype.find(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: find on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.find(context, nonArray, new JSValue[]{findEvenFn}));
        assertPendingException(context);
    }

    @Test
    public void testFindIndex() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: find index of even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.findIndex(context, arr, new JSValue[]{findEvenFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findIndex(context, arr, new JSValue[]{findNegativeFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findIndex(context, emptyArr, new JSValue[]{findEvenFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.findIndex(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: findIndex on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.findIndex(context, nonArray, new JSValue[]{findEvenFn}));
        assertPendingException(context);
    }

    @Test
    public void testFindLast() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case: find last even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.findLast(context, arr, new JSValue[]{findEvenFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findLast(context, arr, new JSValue[]{findNegativeFn});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findLast(context, emptyArr, new JSValue[]{findEvenFn});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.findLast(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: findLast on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.findLast(context, nonArray, new JSValue[]{findEvenFn}));
        assertPendingException(context);
    }

    @Test
    public void testFindLastIndex() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case: find last index of even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.findLastIndex(context, arr, new JSValue[]{findEvenFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findLastIndex(context, arr, new JSValue[]{findNegativeFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findLastIndex(context, emptyArr, new JSValue[]{findEvenFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.findLastIndex(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: findLastIndex on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.findLastIndex(context, nonArray, new JSValue[]{findEvenFn}));
        assertPendingException(context);
    }

    @Test
    public void testFlat() {
        // Create nested arrays
        JSArray nested = new JSArray();
        nested.push(new JSNumber(1));
        nested.push(new JSNumber(2));

        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(3));
        arr2.push(nested);
        arr2.push(new JSNumber(4));

        JSArray arr = new JSArray();
        arr.push(new JSNumber(0));
        arr.push(arr2);
        arr.push(new JSNumber(5));

        // Normal case: flat()
        JSValue result = ArrayPrototype.flat(context, arr, new JSValue[]{});
        JSArray flattened = result.asArray().orElseThrow();
        assertThat(flattened.getLength()).isEqualTo(5);
        assertThat(flattened.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(flattened.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        JSArray childArray = flattened.get(2).asArray().orElseThrow();
        assertThat(childArray.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(childArray.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(flattened.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(flattened.get(4).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // With depth
        result = ArrayPrototype.flat(context, arr, new JSValue[]{new JSNumber(0)});
        flattened = result.asArray().orElseThrow();
        assertThat(flattened.getLength()).isEqualTo(3); // No flattening

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.flat(context, emptyArr, new JSValue[]{});
        JSArray arr1 = result.asArray().orElseThrow();
        assertThat(arr1.getLength()).isEqualTo(0);

        // Edge case: flat on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.flat(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testFlatMap() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: flatMap with function that returns arrays
        JSFunction doubleAndWrapFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            JSArray result = new JSArray();
            result.push(new JSNumber(val));
            result.push(new JSNumber(val * 2));
            return result;
        });

        JSValue result = ArrayPrototype.flatMap(context, arr, new JSValue[]{doubleAndWrapFn});
        JSArray flattened = result.asArray().orElseThrow();
        assertThat(flattened.getLength()).isEqualTo(6);
        assertThat(flattened.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(flattened.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(flattened.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(flattened.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(flattened.get(4).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(flattened.get(5).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(6.0);

        // Case where callback returns non-array
        JSFunction identityFn = createTestFunction(args -> args[0]);
        result = ArrayPrototype.flatMap(context, arr, new JSValue[]{identityFn});
        flattened = result.asArray().orElseThrow();
        assertThat(flattened.getLength()).isEqualTo(3);
        assertThat(flattened.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(flattened.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(flattened.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.flatMap(context, emptyArr, new JSValue[]{doubleAndWrapFn});
        JSArray arr1 = result.asArray().orElseThrow();
        assertThat(arr1.getLength()).isEqualTo(0);

        // Edge case: flatMap on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.flatMap(context, nonArray, new JSValue[]{doubleAndWrapFn}));
        assertPendingException(context);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.flatMap(context, arr, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testForEach() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        final int[] count = {0};
        final double[] sum = {0};
        JSFunction collectFn = createTestFunction(args -> {
            count[0]++;
            sum[0] += args[0].asNumber().map(JSNumber::value).orElseThrow();
            return JSUndefined.INSTANCE;
        });

        JSValue result = ArrayPrototype.forEach(context, arr, new JSValue[]{collectFn});
        assertThat(result.isUndefined()).isTrue();
        assertThat(count[0]).isEqualTo(3);
        assertThat(sum[0]).isEqualTo(6.0);

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.forEach(context, arr, new JSValue[]{collectFn, thisArg});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.forEach(context, emptyArr, new JSValue[]{collectFn});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: no callback
        assertTypeError(ArrayPrototype.forEach(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: forEach on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.forEach(context, nonArray, new JSValue[]{collectFn}));
        assertPendingException(context);
    }

    @Test
    public void testGetLength() {
        // Normal case: non-empty array
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        JSValue result = ArrayPrototype.getLength(context, arr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.getLength(context, emptyArr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Array with specific length
        JSArray arrWithLength = new JSArray(10);
        result = ArrayPrototype.getLength(context, arrWithLength, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0);

        // After modifying array
        arr.push(new JSNumber(4));
        result = ArrayPrototype.getLength(context, arr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        arr.pop();
        result = ArrayPrototype.getLength(context, arr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: getLength on non-array
        JSValue nonArray = new JSString("not an array");
        assertThat(ArrayPrototype.getLength(context, nonArray, new JSValue[]{}).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        assertIntegerWithJavet("[].length",
                "[1,2].length",
                "['a','b'].length",
                "[[],].length",
                "[[1],[2]].length");

        assertErrorWithJavet("Array.prototype.length.call({})",
                "Array.prototype.length.call(123)",
                "Array.prototype.length.call(true)",
                "Array['prototype'].length.call(true)",
                "Array.prototype.length.call(null)",
                "Array.prototype.length.call(undefined)");
    }

    @Test
    public void testGetSymbolUnscopables() {
        // Normal case: get unscopables
        JSArray arr = new JSArray();
        JSValue result = ArrayPrototype.getSymbolUnscopables(context, arr, new JSValue[]{});

        assertThat(result.isObject()).isTrue();
        JSObject unscopables = result.asObject().orElseThrow();

        // Verify ES2015 methods are marked as unscopable
        assertThat(unscopables.get("copyWithin")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("entries")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("fill")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("find")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("findIndex")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("flat")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("flatMap")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("includes")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("keys")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("values")).isEqualTo(JSBoolean.TRUE);

        // Verify ES2022+ methods are marked as unscopable
        assertThat(unscopables.get("at")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("findLast")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("findLastIndex")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("toReversed")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("toSorted")).isEqualTo(JSBoolean.TRUE);
        assertThat(unscopables.get("toSpliced")).isEqualTo(JSBoolean.TRUE);

        // Verify old methods are NOT in unscopables (they should return undefined)
        assertThat(unscopables.get("push").isUndefined()).isTrue();
        assertThat(unscopables.get("pop").isUndefined()).isTrue();
        assertThat(unscopables.get("shift").isUndefined()).isTrue();
        assertThat(unscopables.get("unshift").isUndefined()).isTrue();
        assertThat(unscopables.get("slice").isUndefined()).isTrue();
        assertThat(unscopables.get("splice").isUndefined()).isTrue();
        assertThat(unscopables.get("concat").isUndefined()).isTrue();
        assertThat(unscopables.get("join").isUndefined()).isTrue();
        assertThat(unscopables.get("reverse").isUndefined()).isTrue();
        assertThat(unscopables.get("sort").isUndefined()).isTrue();
        assertThat(unscopables.get("indexOf").isUndefined()).isTrue();
        assertThat(unscopables.get("lastIndexOf").isUndefined()).isTrue();
    }

    @Test
    public void testIncludes() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(JSUndefined.INSTANCE);
        arr.push(new JSNumber(4));

        // Normal case
        JSValue result = ArrayPrototype.includes(context, arr, new JSValue[]{new JSNumber(2)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Not found
        result = ArrayPrototype.includes(context, arr, new JSValue[]{new JSNumber(3)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // With fromIndex
        result = ArrayPrototype.includes(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Negative fromIndex
        result = ArrayPrototype.includes(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(-3)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Edge case: includes undefined
        result = ArrayPrototype.includes(context, arr, new JSValue[]{JSUndefined.INSTANCE});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Edge case: includes NaN (special case)
        JSArray nanArr = new JSArray();
        nanArr.push(new JSNumber(Double.NaN));
        result = ArrayPrototype.includes(context, nanArr, new JSValue[]{new JSNumber(Double.NaN)});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.includes(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: no search element
        result = ArrayPrototype.includes(context, arr, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: includes on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.includes(context, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testIndexOf() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case
        JSValue result = ArrayPrototype.indexOf(context, arr, new JSValue[]{new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // With fromIndex
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Not found
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{new JSNumber(4)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Negative fromIndex
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(-2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.indexOf(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Edge case: no search element
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{});
        assertThat(((JSNumber) result).value()).isEqualTo(-1.0);

        // Edge case: indexOf on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.indexOf(context, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testJoin() {
        JSArray arr = new JSArray();
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        // Normal case
        JSValue result = ArrayPrototype.join(context, arr, new JSValue[]{new JSString("-")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("a-b-c");

        // Default separator
        result = ArrayPrototype.join(context, arr, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("a,b,c");

        // Empty separator
        result = ArrayPrototype.join(context, arr, new JSValue[]{new JSString("")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("abc");

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.join(context, emptyArr, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("");

        // Edge case: array with null/undefined
        JSArray mixedArr = new JSArray();
        mixedArr.push(new JSString("a"));
        mixedArr.push(JSNull.INSTANCE);
        mixedArr.push(JSUndefined.INSTANCE);
        mixedArr.push(new JSString("b"));
        result = ArrayPrototype.join(context, mixedArr, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("a,,,b");

        // Edge case: join on non-array
        JSValue nonArray = new JSString("not an array");
        assertThat(ArrayPrototype.join(context, nonArray, new JSValue[]{}).asString().map(JSString::value).orElseThrow()).isEqualTo("");

        // Edge case: join called on array-like object
        assertThat(
                context.eval("Array.prototype.join.call({ 0: 'a', 1: null, 2: undefined, 3: 'd', 4: 'e', length: 5 }, ',')").asString().map(JSString::value).orElseThrow()).isEqualTo("a,,,d,e");
        assertThat(
                context.eval("Array.prototype.join.call({ 0: 'a', 1: null, 2: undefined, 3: 'd', 4: 'e', length: 5 })").asString().map(JSString::value).orElseThrow()).isEqualTo("a,,,d,e");
        assertThat(
                context.eval("Array.prototype.join.call({ 0: 'a', 0.1: 'b', length: 5 }, '-')").asString().map(JSString::value).orElseThrow()).isEqualTo("a----");
    }

    @Test
    public void testLastIndexOf() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case
        JSValue result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // With fromIndex
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Not found
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{new JSNumber(4)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Negative fromIndex
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(-1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.lastIndexOf(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-1.0);

        // Edge case: no search element
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{});
        assertThat(((JSNumber) result).value()).isEqualTo(-1.0);

        // Edge case: lastIndexOf on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.lastIndexOf(context, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testMap() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: double each element
        JSFunction doubleFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(val * 2);
        });

        JSValue result = ArrayPrototype.map(context, arr, new JSValue[]{doubleFn});
        JSArray mapped = result.asArray().orElseThrow();
        assertThat(mapped.getLength()).isEqualTo(3);
        assertThat(mapped.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(mapped.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(mapped.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(6.0);

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.map(context, arr, new JSValue[]{doubleFn, thisArg});
        assertThat(result.isArray()).isTrue();

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.map(context, emptyArr, new JSValue[]{doubleFn});
        JSArray mappedEmpty = result.asArray().orElseThrow();
        assertThat(mappedEmpty.getLength()).isEqualTo(0);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.map(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: non-function callback
        assertTypeError(ArrayPrototype.map(context, arr, new JSValue[]{new JSString("not a function")}));
        assertPendingException(context);

        // Edge case: map on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.map(context, nonArray, new JSValue[]{doubleFn}));
        assertPendingException(context);
    }

    @Test
    public void testPop() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.pop(context, arr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr.getLength()).isEqualTo(2);

        result = ArrayPrototype.pop(context, arr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.getLength()).isEqualTo(1);

        // Pop from empty array
        result = ArrayPrototype.pop(context, arr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.getLength()).isEqualTo(0);

        result = ArrayPrototype.pop(context, arr, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: pop from non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.pop(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testPrototype() {
        assertObjectWithJavet(
                "Object.getOwnPropertyNames(Array.prototype).sort()");
    }

    @Test
    public void testPush() {
        // Normal case
        JSArray arr = new JSArray();
        JSValue result = ArrayPrototype.push(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2);
        assertThat(arr.getLength()).isEqualTo(2);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Push more
        result = ArrayPrototype.push(context, arr, new JSValue[]{new JSNumber(3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3);
        assertThat(arr.getLength()).isEqualTo(3);

        // Edge case: push to non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.push(context, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testReduce() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: sum
        JSFunction sumFn = createTestFunction(args -> {
            double acc = args[0].asNumber().map(JSNumber::value).orElseThrow();
            double curr = args[1].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(acc + curr);
        });

        JSValue result = ArrayPrototype.reduce(context, arr, new JSValue[]{sumFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(6.0);

        // With initial value
        result = ArrayPrototype.reduce(context, arr, new JSValue[]{sumFn, new JSNumber(10)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(16.0);

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduce(context, emptyArr, new JSValue[]{sumFn}));
        assertPendingException(context);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduce(context, emptyArr, new JSValue[]{sumFn, new JSNumber(42)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(5));
        result = ArrayPrototype.reduce(context, singleArr, new JSValue[]{sumFn});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.reduce(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: reduce on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.reduce(context, nonArray, new JSValue[]{sumFn}));
        assertPendingException(context);
    }

    @Test
    public void testReduceRight() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: concatenate strings in reverse
        JSFunction concatFn = createTestFunction(args -> {
            String acc = args[0].asString().map(JSString::value).orElseThrow();
            String curr = args[1].asString().map(JSString::value).orElseThrow();
            return new JSString(acc + curr);
        });

        JSArray strArr = new JSArray();
        strArr.push(new JSString("a"));
        strArr.push(new JSString("b"));
        strArr.push(new JSString("c"));

        JSValue result = ArrayPrototype.reduceRight(context, strArr, new JSValue[]{concatFn});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("cba");

        // With initial value
        result = ArrayPrototype.reduceRight(context, strArr, new JSValue[]{concatFn, new JSString("d")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("dcba");

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduceRight(context, emptyArr, new JSValue[]{concatFn}));
        assertPendingException(context);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduceRight(context, emptyArr, new JSValue[]{concatFn, new JSString("x")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("x");

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSString("z"));
        result = ArrayPrototype.reduceRight(context, singleArr, new JSValue[]{concatFn});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("z");

        // Edge case: no callback
        assertTypeError(ArrayPrototype.reduceRight(context, strArr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: reduceRight on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.reduceRight(context, nonArray, new JSValue[]{concatFn}));
        assertPendingException(context);
    }

    @Test
    public void testReverse() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case
        JSValue result = ArrayPrototype.reverse(context, arr, new JSValue[]{});
        assertThat(result).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(4);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.reverse(context, emptyArr, new JSValue[]{});
        assertThat(result).isSameAs(emptyArr);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.reverse(context, singleArr, new JSValue[]{});
        assertThat(result).isSameAs(singleArr);
        assertThat(singleArr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Edge case: reverse on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.reverse(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testShift() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.shift(context, arr, new JSValue[]{});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.getLength()).isEqualTo(2);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: shift from empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.shift(context, emptyArr, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: shift from non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.shift(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testSlice() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(0));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case: slice(1, 4)
        JSValue result = ArrayPrototype.slice(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(4)});
        JSArray sliced = result.asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(3);
        assertThat(sliced.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(sliced.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(sliced.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Slice from start
        result = ArrayPrototype.slice(context, arr, new JSValue[]{new JSNumber(2)});
        sliced = result.asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(3);
        assertThat(sliced.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Slice with negative indices
        result = ArrayPrototype.slice(context, arr, new JSValue[]{new JSNumber(-2)});
        sliced = result.asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(2);
        assertThat(sliced.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: empty slice
        result = ArrayPrototype.slice(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        sliced = result.asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(0);

        // Edge case: slice on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.slice(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testSome() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(5));

        // Normal case: some odd
        JSFunction isOddFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 != 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.some(context, arr, new JSValue[]{isOddFn});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // None pass
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(4));
        arr2.push(new JSNumber(6));

        result = ArrayPrototype.some(context, arr2, new JSValue[]{isOddFn});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: empty array (vacuously false)
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.some(context, emptyArr, new JSValue[]{isOddFn});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.some(context, arr, new JSValue[]{}));
        assertPendingException(context);

        // Edge case: some on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.some(context, nonArray, new JSValue[]{isOddFn}));
        assertPendingException(context);
    }

    @Test
    public void testSort() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(4));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(5));

        // Normal case: default sort (string comparison)
        JSValue result = ArrayPrototype.sort(context, arr, new JSValue[]{});
        assertThat(result).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(5);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(arr.get(4).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);

        // With compare function
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(4));

        JSFunction descCompare = createTestFunction(args -> {
            double a = args[0].asNumber().map(JSNumber::value).orElseThrow();
            double b = args[1].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(b - a);
        });

        result = ArrayPrototype.sort(context, arr2, new JSValue[]{descCompare});
        assertThat(result).isSameAs(arr2);
        assertThat(arr2.getLength()).isEqualTo(3);
        assertThat(arr2.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(arr2.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr2.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.sort(context, emptyArr, new JSValue[]{});
        assertThat(result).isSameAs(emptyArr);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.sort(context, singleArr, new JSValue[]{});
        assertThat(result).isSameAs(singleArr);
        assertThat(singleArr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Edge case: sort on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.sort(context, nonArray, new JSValue[]{}));
        assertPendingException(context);

        assertObjectWithJavet(
                "[].sort()",
                "[1.1,2.1]",
                "['b','a']",
                "['a',null,undefined,[],'b']");
    }

    @Test
    public void testSplice() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(0));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case: splice(2, 2, 10, 11)
        JSValue result = ArrayPrototype.splice(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2), new JSNumber(10), new JSNumber(11)});
        JSArray deleted = result.asArray().orElseThrow();
        assertThat(deleted.getLength()).isEqualTo(2);
        assertThat(deleted.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(deleted.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Check modified array
        assertThat(arr.getLength()).isEqualTo(5);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0);
        assertThat(arr.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(11.0);
        assertThat(arr.get(4).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Edge case: splice with no deletions
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        result = ArrayPrototype.splice(context, arr2, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(1.5)});
        deleted = result.asArray().orElseThrow();
        assertThat(deleted.getLength()).isEqualTo(0);
        assertThat(arr2.getLength()).isEqualTo(3);

        // Edge case: splice on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.splice(context, nonArray, new JSValue[]{new JSNumber(0), new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testSymbolIterator() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Set prototype to Array.prototype
        JSObject arrayObj = context.getGlobalObject().get("Array").asObject().orElseThrow();
        JSObject arrayProto = arrayObj.get("prototype").asObject().orElseThrow();
        arr.setPrototype(arrayProto);

        // Get Symbol.iterator
        PropertyKey iteratorKey = PropertyKey.fromSymbol(JSSymbol.ITERATOR);
        JSValue iteratorFn = arr.get(iteratorKey);
        assertThat(iteratorFn).isNotNull();
        assertThat(iteratorFn.isFunction()).isTrue();

        // Get values function
        JSValue valuesFn = arr.get("values");
        assertThat(valuesFn).isNotNull();
        assertThat(valuesFn.isFunction()).isTrue();

        // Symbol.iterator should be the same as values
        assertThat(valuesFn).isSameAs(iteratorFn);

        // Test that calling Symbol.iterator works
        JSValue iteratorResult = ((JSFunction) iteratorFn).call(context, arr, new JSValue[0]);
        assertThat(iteratorResult.isIterator()).isTrue();

        // Test iteration
        JSIterator iterator = (JSIterator) iteratorResult;
        JSObject result1 = iterator.next();
        assertThat(result1.get("done").isBooleanFalse()).isTrue();
        assertThat(result1.get("value").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        JSObject result2 = iterator.next();
        assertThat(result2.get("done").isBooleanFalse()).isTrue();
        assertThat(result2.get("value").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        JSObject result3 = iterator.next();
        assertThat(result3.get("done").isBooleanFalse()).isTrue();
        assertThat(result3.get("value").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        JSObject result4 = iterator.next();
        assertThat(result4.get("done").isBooleanTrue()).isTrue();
        assertThat(result4.get("value").isUndefined()).isTrue();
    }

    @Test
    public void testToLocaleString() {
        // Normal case
        JSArray arr = new JSArray();
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        JSValue result = ArrayPrototype.toLocaleString(context, arr, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("a,b,c");

        // With numbers
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));

        result = ArrayPrototype.toLocaleString(context, arr2, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("1,2,3");

        // With null and undefined
        JSArray arr3 = new JSArray();
        arr3.push(new JSString("a"));
        arr3.push(JSNull.INSTANCE);
        arr3.push(JSUndefined.INSTANCE);
        arr3.push(new JSString("b"));

        result = ArrayPrototype.toLocaleString(context, arr3, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("a,,,b");

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toLocaleString(context, emptyArr, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElse("FAIL")).isEqualTo("");

        // Edge case: toLocaleString on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.toLocaleString(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testToReversed() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.toReversed(context, arr, new JSValue[]{});
        JSArray reversed = result.asArray().orElseThrow();
        assertThat(reversed.getLength()).isEqualTo(3);
        assertThat(reversed.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(reversed.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(reversed.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Original array should be unchanged
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toReversed(context, emptyArr, new JSValue[]{});
        JSArray emptyReversed = result.asArray().orElseThrow();
        assertThat(emptyReversed.getLength()).isEqualTo(0);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.toReversed(context, singleArr, new JSValue[]{});
        JSArray singleReversed = result.asArray().orElseThrow();
        assertThat(singleReversed.getLength()).isEqualTo(1);
        assertThat(singleReversed.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Edge case: toReversed on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.toReversed(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testToSorted() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));

        // Normal case: default sort (string comparison)
        JSValue result = ArrayPrototype.toSorted(context, arr, new JSValue[]{});
        JSArray sorted = result.asArray().orElseThrow();
        assertThat(sorted.getLength()).isEqualTo(3);
        assertThat(sorted.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(sorted.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(sorted.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Original array should be unchanged
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // With custom compare function
        JSFunction compareFn = createTestFunction(args -> {
            double a = args[0].asNumber().map(JSNumber::value).orElseThrow();
            double b = args[1].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(b - a); // descending order
        });

        result = ArrayPrototype.toSorted(context, arr, new JSValue[]{compareFn});
        JSArray sortedDesc = result.asArray().orElseThrow();
        assertThat(sortedDesc.getLength()).isEqualTo(3);
        assertThat(sortedDesc.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(sortedDesc.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(sortedDesc.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toSorted(context, emptyArr, new JSValue[]{});
        JSArray emptySorted = result.asArray().orElseThrow();
        assertThat(emptySorted.getLength()).isEqualTo(0);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.toSorted(context, singleArr, new JSValue[]{});
        JSArray singleSorted = result.asArray().orElseThrow();
        assertThat(singleSorted.getLength()).isEqualTo(1);
        assertThat(singleSorted.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Edge case: toSorted on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.toSorted(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testToSpliced() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case: remove 2 elements starting at index 1
        JSValue result = ArrayPrototype.toSpliced(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        JSArray spliced = result.asArray().orElseThrow();
        assertThat(spliced.getLength()).isEqualTo(2);
        assertThat(spliced.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(spliced.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Original array should be unchanged
        assertThat(arr.getLength()).isEqualTo(4);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Insert elements
        result = ArrayPrototype.toSpliced(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(5), new JSNumber(6)});
        JSArray inserted = result.asArray().orElseThrow();
        assertThat(inserted.getLength()).isEqualTo(6);
        assertThat(inserted.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(inserted.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);
        assertThat(inserted.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(6.0);
        assertThat(inserted.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(inserted.get(4).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(inserted.get(5).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Replace elements
        result = ArrayPrototype.toSpliced(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(2), new JSNumber(7), new JSNumber(8)});
        JSArray replaced = result.asArray().orElseThrow();
        assertThat(replaced.getLength()).isEqualTo(4);
        assertThat(replaced.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(replaced.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(7.0);
        assertThat(replaced.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(8.0);
        assertThat(replaced.get(3).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Negative start index
        result = ArrayPrototype.toSpliced(context, arr, new JSValue[]{new JSNumber(-2), new JSNumber(1)});
        JSArray negStart = result.asArray().orElseThrow();
        assertThat(negStart.getLength()).isEqualTo(3);
        assertThat(negStart.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(negStart.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(negStart.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toSpliced(context, emptyArr, new JSValue[]{new JSNumber(0), new JSNumber(0), new JSNumber(1)});
        JSArray emptySpliced = result.asArray().orElseThrow();
        assertThat(emptySpliced.getLength()).isEqualTo(1);
        assertThat(emptySpliced.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);

        // Edge case: toSpliced on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.toSpliced(context, nonArray, new JSValue[]{new JSNumber(0), new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testToString() {
        JSArray jsArray = new JSArray();
        jsArray.push(new JSString("a"));
        jsArray.push(new JSNumber(1));
        jsArray.push(new JSString("c"));

        // Normal case
        JSValue result = ArrayPrototype.toString(context, jsArray, new JSValue[]{});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("a,1,c");

        // Edge case: toString on string
        assertThat(
                ArrayPrototype.toString(context, new JSString("a"), new JSValue[]{}).asString().map(JSString::value).orElseThrow()).isEqualTo("[object String]");

        // Edge case: toString on object
        assertThat(
                ArrayPrototype.toString(context, new JSObject(), new JSValue[]{}).asString().map(JSString::value).orElseThrow()).isEqualTo("[object Object]");

        // Edge case: toString on null
        assertTypeError(ArrayPrototype.toString(context, new JSNull(), new JSValue[]{}), "Cannot convert undefined or null to object");
        assertPendingException(context);

        // Edge case: toString on undefined
        assertTypeError(ArrayPrototype.toString(context, new JSUndefined(), new JSValue[]{}), "Cannot convert undefined or null to object");
        assertPendingException(context);
    }

    @Test
    public void testUnshift() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.unshift(context, arr, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);
        assertThat(arr.getLength()).isEqualTo(3);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Unshift multiple
        result = ArrayPrototype.unshift(context, arr, new JSValue[]{new JSNumber(-1), new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(5.0);
        assertThat(arr.getLength()).isEqualTo(5);

        // Edge case: unshift to empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.unshift(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(emptyArr.getLength()).isEqualTo(1);

        // Edge case: unshift to non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.unshift(context, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(context);
    }

    @Test
    public void testWith() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: replace element at index 1
        JSValue result = ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(42)});
        JSArray withResult = result.asArray().orElseThrow();
        assertThat(withResult.getLength()).isEqualTo(3);
        assertThat(withResult.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(withResult.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);
        assertThat(withResult.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Original array should be unchanged
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Negative index
        result = ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(-1), new JSNumber(99)});
        JSArray negIndex = result.asArray().orElseThrow();
        assertThat(negIndex.getLength()).isEqualTo(3);
        assertThat(negIndex.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(negIndex.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(negIndex.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(99.0);

        // Edge case: index 0
        result = ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(0), new JSNumber(100)});
        JSArray zeroIndex = result.asArray().orElseThrow();
        assertThat(zeroIndex.getLength()).isEqualTo(3);
        assertThat(zeroIndex.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(100.0);
        assertThat(zeroIndex.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(zeroIndex.get(2).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(3.0);

        // Edge case: out of bounds positive index
        JSValue outOfBoundsResult = ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(3), new JSNumber(1)});
        assertRangeError(outOfBoundsResult);
        assertPendingException(context);

        // Edge case: out of bounds negative index
        outOfBoundsResult = ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(-4), new JSNumber(1)});
        assertRangeError(outOfBoundsResult);
        assertPendingException(context);

        // Edge case: insufficient arguments
        assertTypeError(ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(1)}));
        assertPendingException(context);

        // Edge case: with on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.with(context, nonArray, new JSValue[]{new JSNumber(0), new JSNumber(1)}));
        assertPendingException(context);
    }
}
