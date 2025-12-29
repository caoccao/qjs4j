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

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArrayPrototype methods.
 */
public class ArrayPrototypeTest extends BaseTest {
    // Helper method to create a simple test function
    private JSFunction createTestFunction(Function<JSValue[], JSValue> impl) {
        return new JSNativeFunction("test", 1, (ctx, thisArg, args) -> impl.apply(args));
    }

    @Test
    public void testAt() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: positive index
        JSValue result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(0)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(2)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Negative index
        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(-1)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(-2)});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Out of bounds
        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(3)});
        assertEquals(JSUndefined.INSTANCE, result);

        result = ArrayPrototype.at(context, arr, new JSValue[]{new JSNumber(-4)});
        assertEquals(JSUndefined.INSTANCE, result);

        // No arguments
        result = ArrayPrototype.at(context, arr, new JSValue[]{});
        assertEquals(JSUndefined.INSTANCE, result);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.at(context, emptyArr, new JSValue[]{new JSNumber(0)});
        assertEquals(JSUndefined.INSTANCE, result);

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
        assertEquals(4, concatenated.getLength());
        assertEquals(1.0, concatenated.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, concatenated.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, concatenated.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, concatenated.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // Concat with values
        result = ArrayPrototype.concat(context, arr, new JSValue[]{new JSNumber(5), arr2});
        concatenated = result.asArray().orElseThrow();
        assertEquals(5, concatenated.getLength());
        assertEquals(5.0, concatenated.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: concat with empty arrays
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.concat(context, emptyArr, new JSValue[]{arr});
        concatenated = result.asArray().orElseThrow();
        assertEquals(2, concatenated.getLength());

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
        assertSame(arr, result);
        assertEquals(5, arr.getLength());
        assertEquals(4.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(5.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, arr.get(3).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(5.0, arr.get(4).asNumber().map(JSNumber::value).orElseThrow());

        // With negative indices
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));
        arr2.push(new JSNumber(5));

        result = ArrayPrototype.copyWithin(context, arr2, new JSValue[]{new JSNumber(-2), new JSNumber(0), new JSNumber(2)});
        assertSame(arr2, result);
        assertEquals(5, arr2.getLength());
        assertEquals(1.0, arr2.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr2.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr2.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, arr2.get(3).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr2.get(4).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.copyWithin(context, emptyArr, new JSValue[]{new JSNumber(0), new JSNumber(1)});
        assertSame(emptyArr, result);

        // Edge case: no arguments
        JSArray arr3 = new JSArray();
        arr3.push(new JSNumber(1));
        result = ArrayPrototype.copyWithin(context, arr3, new JSValue[]{});
        assertSame(arr3, result);

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
        assertTrue(result.isBooleanTrue());

        // Not all pass
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        result = ArrayPrototype.every(context, arr2, new JSValue[]{isEvenFn});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: empty array (vacuously true)
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.every(context, emptyArr, new JSValue[]{isEvenFn});
        assertEquals(JSBoolean.TRUE, result);

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
        assertSame(arr, result);
        assertEquals(4, arr.getLength());
        assertEquals(0.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // Fill with start index
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        result = ArrayPrototype.fill(context, arr2, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertSame(arr2, result);
        assertEquals(4, arr2.getLength());
        assertEquals(1.0, arr2.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr2.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr2.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr2.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // Fill with start and end
        JSArray arr3 = new JSArray();
        arr3.push(new JSNumber(1));
        arr3.push(new JSNumber(2));
        arr3.push(new JSNumber(3));
        arr3.push(new JSNumber(4));

        result = ArrayPrototype.fill(context, arr3, new JSValue[]{new JSNumber(0), new JSNumber(1), new JSNumber(3)});
        assertSame(arr3, result);
        assertEquals(4, arr3.getLength());
        assertEquals(1.0, arr3.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr3.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr3.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, arr3.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // With negative indices
        JSArray arr4 = new JSArray();
        arr4.push(new JSNumber(1));
        arr4.push(new JSNumber(2));
        arr4.push(new JSNumber(3));
        arr4.push(new JSNumber(4));

        result = ArrayPrototype.fill(context, arr4, new JSValue[]{new JSNumber(0), new JSNumber(-3), new JSNumber(-1)});
        assertSame(arr4, result);
        assertEquals(4, arr4.getLength());
        assertEquals(1.0, arr4.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr4.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0.0, arr4.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, arr4.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.fill(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertSame(emptyArr, result);

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
        assertEquals(2, filtered.getLength());
        assertEquals(2.0, filtered.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, filtered.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no elements pass filter
        JSFunction noneFn = createTestFunction(args -> JSBoolean.FALSE);
        result = ArrayPrototype.filter(context, arr, new JSValue[]{noneFn});
        JSArray arr1 = result.asArray().orElseThrow();
        assertEquals(0, arr1.getLength());

        // Edge case: all elements pass filter
        JSFunction allFn = createTestFunction(args -> JSBoolean.TRUE);
        result = ArrayPrototype.filter(context, arr, new JSValue[]{allFn});
        arr1 = result.asArray().orElseThrow();
        assertEquals(4, arr1.getLength());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.filter(context, emptyArr, new JSValue[]{evenFn});
        arr1 = result.asArray().orElseThrow();
        assertEquals(0, arr1.getLength());

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
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.find(context, arr, new JSValue[]{findNegativeFn});
        assertTrue(result.isUndefined());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.find(context, emptyArr, new JSValue[]{findEvenFn});
        assertTrue(result.isUndefined());

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
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findIndex(context, arr, new JSValue[]{findNegativeFn});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findIndex(context, emptyArr, new JSValue[]{findEvenFn});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

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
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findLast(context, arr, new JSValue[]{findNegativeFn});
        assertEquals(JSUndefined.INSTANCE, result);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findLast(context, emptyArr, new JSValue[]{findEvenFn});
        assertEquals(JSUndefined.INSTANCE, result);

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
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findLastIndex(context, arr, new JSValue[]{findNegativeFn});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findLastIndex(context, emptyArr, new JSValue[]{findEvenFn});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

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
        assertEquals(5, flattened.getLength());
        assertEquals(0.0, flattened.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, flattened.get(1).asNumber().map(JSNumber::value).orElseThrow());
        JSArray childArray = flattened.get(2).asArray().orElseThrow();
        assertEquals(1.0, childArray.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, childArray.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, flattened.get(3).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(5.0, flattened.get(4).asNumber().map(JSNumber::value).orElseThrow());

        // With depth
        result = ArrayPrototype.flat(context, arr, new JSValue[]{new JSNumber(0)});
        flattened = result.asArray().orElseThrow();
        assertEquals(3, flattened.getLength()); // No flattening

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.flat(context, emptyArr, new JSValue[]{});
        JSArray arr1 = result.asArray().orElseThrow();
        assertEquals(0, arr1.getLength());

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
        assertEquals(6, flattened.getLength());
        assertEquals(1.0, flattened.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, flattened.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, flattened.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, flattened.get(3).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, flattened.get(4).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(6.0, flattened.get(5).asNumber().map(JSNumber::value).orElseThrow());

        // Case where callback returns non-array
        JSFunction identityFn = createTestFunction(args -> args[0]);
        result = ArrayPrototype.flatMap(context, arr, new JSValue[]{identityFn});
        flattened = result.asArray().orElseThrow();
        assertEquals(3, flattened.getLength());
        assertEquals(1.0, flattened.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, flattened.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, flattened.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.flatMap(context, emptyArr, new JSValue[]{doubleAndWrapFn});
        JSArray arr1 = result.asArray().orElseThrow();
        assertEquals(0, arr1.getLength());

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
        assertTrue(result.isUndefined());
        assertEquals(3, count[0]);
        assertEquals(6.0, sum[0]);

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.forEach(context, arr, new JSValue[]{collectFn, thisArg});
        assertTrue(result.isUndefined());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.forEach(context, emptyArr, new JSValue[]{collectFn});
        assertTrue(result.isUndefined());

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
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.getLength(context, emptyArr, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Array with specific length
        JSArray arrWithLength = new JSArray(10);
        result = ArrayPrototype.getLength(context, arrWithLength, new JSValue[]{});
        assertEquals(10.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // After modifying array
        arr.push(new JSNumber(4));
        result = ArrayPrototype.getLength(context, arr, new JSValue[]{});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElseThrow());

        arr.pop();
        result = ArrayPrototype.getLength(context, arr, new JSValue[]{});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: getLength on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.getLength(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testGetSymbolUnscopables() {
        // Normal case: get unscopables
        JSArray arr = new JSArray();
        JSValue result = ArrayPrototype.getSymbolUnscopables(context, arr, new JSValue[]{});

        assertTrue(result.isObject());
        JSObject unscopables = result.asObject().orElseThrow();

        // Verify ES2015 methods are marked as unscopable
        assertEquals(JSBoolean.TRUE, unscopables.get("copyWithin"));
        assertEquals(JSBoolean.TRUE, unscopables.get("entries"));
        assertEquals(JSBoolean.TRUE, unscopables.get("fill"));
        assertEquals(JSBoolean.TRUE, unscopables.get("find"));
        assertEquals(JSBoolean.TRUE, unscopables.get("findIndex"));
        assertEquals(JSBoolean.TRUE, unscopables.get("flat"));
        assertEquals(JSBoolean.TRUE, unscopables.get("flatMap"));
        assertEquals(JSBoolean.TRUE, unscopables.get("includes"));
        assertEquals(JSBoolean.TRUE, unscopables.get("keys"));
        assertEquals(JSBoolean.TRUE, unscopables.get("values"));

        // Verify ES2022+ methods are marked as unscopable
        assertEquals(JSBoolean.TRUE, unscopables.get("at"));
        assertEquals(JSBoolean.TRUE, unscopables.get("findLast"));
        assertEquals(JSBoolean.TRUE, unscopables.get("findLastIndex"));
        assertEquals(JSBoolean.TRUE, unscopables.get("toReversed"));
        assertEquals(JSBoolean.TRUE, unscopables.get("toSorted"));
        assertEquals(JSBoolean.TRUE, unscopables.get("toSpliced"));

        // Verify old methods are NOT in unscopables (they should return undefined)
        assertTrue(unscopables.get("push").isUndefined());
        assertTrue(unscopables.get("pop").isUndefined());
        assertTrue(unscopables.get("shift").isUndefined());
        assertTrue(unscopables.get("unshift").isUndefined());
        assertTrue(unscopables.get("slice").isUndefined());
        assertTrue(unscopables.get("splice").isUndefined());
        assertTrue(unscopables.get("concat").isUndefined());
        assertTrue(unscopables.get("join").isUndefined());
        assertTrue(unscopables.get("reverse").isUndefined());
        assertTrue(unscopables.get("sort").isUndefined());
        assertTrue(unscopables.get("indexOf").isUndefined());
        assertTrue(unscopables.get("lastIndexOf").isUndefined());
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
        assertEquals(JSBoolean.TRUE, result);

        // Not found
        result = ArrayPrototype.includes(context, arr, new JSValue[]{new JSNumber(3)});
        assertEquals(JSBoolean.FALSE, result);

        // With fromIndex
        result = ArrayPrototype.includes(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(JSBoolean.FALSE, result);

        // Negative fromIndex
        result = ArrayPrototype.includes(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(-3)});
        assertEquals(JSBoolean.TRUE, result);

        // Edge case: includes undefined
        result = ArrayPrototype.includes(context, arr, new JSValue[]{JSUndefined.INSTANCE});
        assertEquals(JSBoolean.TRUE, result);

        // Edge case: includes NaN (special case)
        JSArray nanArr = new JSArray();
        nanArr.push(new JSNumber(Double.NaN));
        result = ArrayPrototype.includes(context, nanArr, new JSValue[]{new JSNumber(Double.NaN)});
        assertEquals(JSBoolean.TRUE, result);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.includes(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: no search element
        result = ArrayPrototype.includes(context, arr, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

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
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // With fromIndex
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Not found
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{new JSNumber(4)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Negative fromIndex
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(-2)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.indexOf(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no search element
        result = ArrayPrototype.indexOf(context, arr, new JSValue[]{});
        assertEquals(-1.0, ((JSNumber) result).value());

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
        assertEquals("a-b-c", result.asString().map(JSString::value).orElseThrow());

        // Default separator
        result = ArrayPrototype.join(context, arr, new JSValue[]{});
        assertEquals("a,b,c", result.asString().map(JSString::value).orElseThrow());

        // Empty separator
        result = ArrayPrototype.join(context, arr, new JSValue[]{new JSString("")});
        assertEquals("abc", result.asString().map(JSString::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.join(context, emptyArr, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElseThrow());

        // Edge case: array with null/undefined
        JSArray mixedArr = new JSArray();
        mixedArr.push(new JSString("a"));
        mixedArr.push(JSNull.INSTANCE);
        mixedArr.push(JSUndefined.INSTANCE);
        mixedArr.push(new JSString("b"));
        result = ArrayPrototype.join(context, mixedArr, new JSValue[]{});
        assertEquals("a,,,b", result.asString().map(JSString::value).orElseThrow());

        // Edge case: join on non-array
        JSValue nonArray = new JSString("not an array");
        assertEquals("", ArrayPrototype.join(context, nonArray, new JSValue[]{}).asString().map(JSString::value).orElseThrow());

        // Edge case: join called on array-like object
        assertEquals(
                "a,,,d,e",
                context.eval("Array.prototype.join.call({ 0: 'a', 1: null, 2: undefined, 3: 'd', 4: 'e', length: 5 }, ',')").asString().map(JSString::value).orElseThrow());
        assertEquals(
                "a,,,d,e",
                context.eval("Array.prototype.join.call({ 0: 'a', 1: null, 2: undefined, 3: 'd', 4: 'e', length: 5 })").asString().map(JSString::value).orElseThrow());
        assertEquals(
                "a----",
                context.eval("Array.prototype.join.call({ 0: 'a', 0.1: 'b', length: 5 }, '-')").asString().map(JSString::value).orElseThrow());
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
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // With fromIndex
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Not found
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{new JSNumber(4)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Negative fromIndex
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(-1)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.lastIndexOf(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: no search element
        result = ArrayPrototype.lastIndexOf(context, arr, new JSValue[]{});
        assertEquals(-1.0, ((JSNumber) result).value());

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
        assertEquals(3, mapped.getLength());
        assertEquals(2.0, mapped.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, mapped.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(6.0, mapped.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.map(context, arr, new JSValue[]{doubleFn, thisArg});
        assertTrue(result.isArray());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.map(context, emptyArr, new JSValue[]{doubleFn});
        JSArray mappedEmpty = result.asArray().orElseThrow();
        assertEquals(0, mappedEmpty.getLength());

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
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2, arr.getLength());

        result = ArrayPrototype.pop(context, arr, new JSValue[]{});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1, arr.getLength());

        // Pop from empty array
        result = ArrayPrototype.pop(context, arr, new JSValue[]{});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(0, arr.getLength());

        result = ArrayPrototype.pop(context, arr, new JSValue[]{});
        assertTrue(result.isUndefined());

        // Edge case: pop from non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.pop(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testPush() {
        // Normal case
        JSArray arr = new JSArray();
        JSValue result = ArrayPrototype.push(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        assertEquals(2, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Push more
        result = ArrayPrototype.push(context, arr, new JSValue[]{new JSNumber(3)});
        assertEquals(3, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3, arr.getLength());

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
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // With initial value
        result = ArrayPrototype.reduce(context, arr, new JSValue[]{sumFn, new JSNumber(10)});
        assertEquals(16.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduce(context, emptyArr, new JSValue[]{sumFn}));
        assertPendingException(context);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduce(context, emptyArr, new JSValue[]{sumFn, new JSNumber(42)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(5));
        result = ArrayPrototype.reduce(context, singleArr, new JSValue[]{sumFn});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElseThrow());

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
        assertEquals("cba", result.asString().map(JSString::value).orElseThrow());

        // With initial value
        result = ArrayPrototype.reduceRight(context, strArr, new JSValue[]{concatFn, new JSString("d")});
        assertEquals("dcba", result.asString().map(JSString::value).orElseThrow());

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduceRight(context, emptyArr, new JSValue[]{concatFn}));
        assertPendingException(context);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduceRight(context, emptyArr, new JSValue[]{concatFn, new JSString("x")});
        assertEquals("x", result.asString().map(JSString::value).orElseThrow());

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSString("z"));
        result = ArrayPrototype.reduceRight(context, singleArr, new JSValue[]{concatFn});
        assertEquals("z", result.asString().map(JSString::value).orElseThrow());

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
        assertSame(arr, result);
        assertEquals(4, arr.getLength());
        assertEquals(4.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, arr.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.reverse(context, emptyArr, new JSValue[]{});
        assertSame(emptyArr, result);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.reverse(context, singleArr, new JSValue[]{});
        assertSame(singleArr, result);
        assertEquals(42.0, singleArr.get(0).asNumber().map(JSNumber::value).orElseThrow());

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
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2, arr.getLength());
        assertEquals(2.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: shift from empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.shift(context, emptyArr, new JSValue[]{});
        assertTrue(result.isUndefined());

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
        assertEquals(3, sliced.getLength());
        assertEquals(1.0, sliced.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, sliced.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, sliced.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Slice from start
        result = ArrayPrototype.slice(context, arr, new JSValue[]{new JSNumber(2)});
        sliced = result.asArray().orElseThrow();
        assertEquals(3, sliced.getLength());
        assertEquals(2.0, sliced.get(0).asNumber().map(JSNumber::value).orElseThrow());

        // Slice with negative indices
        result = ArrayPrototype.slice(context, arr, new JSValue[]{new JSNumber(-2)});
        sliced = result.asArray().orElseThrow();
        assertEquals(2, sliced.getLength());
        assertEquals(3.0, sliced.get(0).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty slice
        result = ArrayPrototype.slice(context, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        sliced = result.asArray().orElseThrow();
        assertEquals(0, sliced.getLength());

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
        assertEquals(JSBoolean.TRUE, result);

        // None pass
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(4));
        arr2.push(new JSNumber(6));

        result = ArrayPrototype.some(context, arr2, new JSValue[]{isOddFn});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: empty array (vacuously false)
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.some(context, emptyArr, new JSValue[]{isOddFn});
        assertEquals(JSBoolean.FALSE, result);

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
        assertSame(arr, result);
        assertEquals(5, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, arr.get(3).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(5.0, arr.get(4).asNumber().map(JSNumber::value).orElseThrow());

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
        assertSame(arr2, result);
        assertEquals(3, arr2.getLength());
        assertEquals(4.0, arr2.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr2.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, arr2.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.sort(context, emptyArr, new JSValue[]{});
        assertSame(emptyArr, result);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.sort(context, singleArr, new JSValue[]{});
        assertSame(singleArr, result);
        assertEquals(42.0, singleArr.get(0).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: sort on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.sort(context, nonArray, new JSValue[]{}));
        assertPendingException(context);
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
        assertEquals(2, deleted.getLength());
        assertEquals(2.0, deleted.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, deleted.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Check modified array
        assertEquals(5, arr.getLength());
        assertEquals(0.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(10.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(11.0, arr.get(3).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, arr.get(4).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: splice with no deletions
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        result = ArrayPrototype.splice(context, arr2, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(1.5)});
        deleted = result.asArray().orElseThrow();
        assertEquals(0, deleted.getLength());
        assertEquals(3, arr2.getLength());

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
        assertNotNull(iteratorFn);
        assertTrue(iteratorFn.isFunction());

        // Get values function
        JSValue valuesFn = arr.get("values");
        assertNotNull(valuesFn);
        assertTrue(valuesFn.isFunction());

        // Symbol.iterator should be the same as values
        assertSame(valuesFn, iteratorFn);

        // Test that calling Symbol.iterator works
        JSValue iteratorResult = ((JSFunction) iteratorFn).call(context, arr, new JSValue[0]);
        assertTrue(iteratorResult.isIterator());

        // Test iteration
        JSIterator iterator = (JSIterator) iteratorResult;
        JSObject result1 = iterator.next();
        assertTrue(result1.get("done").isBooleanFalse());
        assertEquals(1.0, result1.get("value").asNumber().map(JSNumber::value).orElseThrow());

        JSObject result2 = iterator.next();
        assertTrue(result2.get("done").isBooleanFalse());
        assertEquals(2.0, result2.get("value").asNumber().map(JSNumber::value).orElseThrow());

        JSObject result3 = iterator.next();
        assertTrue(result3.get("done").isBooleanFalse());
        assertEquals(3.0, result3.get("value").asNumber().map(JSNumber::value).orElseThrow());

        JSObject result4 = iterator.next();
        assertTrue(result4.get("done").isBooleanTrue());
        assertTrue(result4.get("value").isUndefined());
    }

    @Test
    public void testToLocaleString() {
        // Normal case
        JSArray arr = new JSArray();
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        JSValue result = ArrayPrototype.toLocaleString(context, arr, new JSValue[]{});
        assertEquals("a,b,c", result.asString().map(JSString::value).orElseThrow());

        // With numbers
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));

        result = ArrayPrototype.toLocaleString(context, arr2, new JSValue[]{});
        assertEquals("1,2,3", result.asString().map(JSString::value).orElseThrow());

        // With null and undefined
        JSArray arr3 = new JSArray();
        arr3.push(new JSString("a"));
        arr3.push(JSNull.INSTANCE);
        arr3.push(JSUndefined.INSTANCE);
        arr3.push(new JSString("b"));

        result = ArrayPrototype.toLocaleString(context, arr3, new JSValue[]{});
        assertEquals("a,,,b", result.asString().map(JSString::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toLocaleString(context, emptyArr, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse("FAIL"));

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
        assertEquals(3, reversed.getLength());
        assertEquals(3.0, reversed.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, reversed.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, reversed.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Original array should be unchanged
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toReversed(context, emptyArr, new JSValue[]{});
        JSArray emptyReversed = result.asArray().orElseThrow();
        assertEquals(0, emptyReversed.getLength());

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.toReversed(context, singleArr, new JSValue[]{});
        JSArray singleReversed = result.asArray().orElseThrow();
        assertEquals(1, singleReversed.getLength());
        assertEquals(42.0, singleReversed.get(0).asNumber().map(JSNumber::value).orElseThrow());

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
        assertEquals(3, sorted.getLength());
        assertEquals(1.0, sorted.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, sorted.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, sorted.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Original array should be unchanged
        assertEquals(3.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // With custom compare function
        JSFunction compareFn = createTestFunction(args -> {
            double a = args[0].asNumber().map(JSNumber::value).orElseThrow();
            double b = args[1].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(b - a); // descending order
        });

        result = ArrayPrototype.toSorted(context, arr, new JSValue[]{compareFn});
        JSArray sortedDesc = result.asArray().orElseThrow();
        assertEquals(3, sortedDesc.getLength());
        assertEquals(3.0, sortedDesc.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, sortedDesc.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1.0, sortedDesc.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toSorted(context, emptyArr, new JSValue[]{});
        JSArray emptySorted = result.asArray().orElseThrow();
        assertEquals(0, emptySorted.getLength());

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.toSorted(context, singleArr, new JSValue[]{});
        JSArray singleSorted = result.asArray().orElseThrow();
        assertEquals(1, singleSorted.getLength());
        assertEquals(42.0, singleSorted.get(0).asNumber().map(JSNumber::value).orElseThrow());

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
        assertEquals(2, spliced.getLength());
        assertEquals(1.0, spliced.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, spliced.get(1).asNumber().map(JSNumber::value).orElseThrow());

        // Original array should be unchanged
        assertEquals(4, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, arr.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // Insert elements
        result = ArrayPrototype.toSpliced(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(5), new JSNumber(6)});
        JSArray inserted = result.asArray().orElseThrow();
        assertEquals(6, inserted.getLength());
        assertEquals(1.0, inserted.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(5.0, inserted.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(6.0, inserted.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, inserted.get(3).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, inserted.get(4).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, inserted.get(5).asNumber().map(JSNumber::value).orElseThrow());

        // Replace elements
        result = ArrayPrototype.toSpliced(context, arr, new JSValue[]{new JSNumber(1), new JSNumber(2), new JSNumber(7), new JSNumber(8)});
        JSArray replaced = result.asArray().orElseThrow();
        assertEquals(4, replaced.getLength());
        assertEquals(1.0, replaced.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(7.0, replaced.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(8.0, replaced.get(2).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, replaced.get(3).asNumber().map(JSNumber::value).orElseThrow());

        // Negative start index
        result = ArrayPrototype.toSpliced(context, arr, new JSValue[]{new JSNumber(-2), new JSNumber(1)});
        JSArray negStart = result.asArray().orElseThrow();
        assertEquals(3, negStart.getLength());
        assertEquals(1.0, negStart.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, negStart.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(4.0, negStart.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toSpliced(context, emptyArr, new JSValue[]{new JSNumber(0), new JSNumber(0), new JSNumber(1)});
        JSArray emptySpliced = result.asArray().orElseThrow();
        assertEquals(1, emptySpliced.getLength());
        assertEquals(1.0, emptySpliced.get(0).asNumber().map(JSNumber::value).orElseThrow());

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
        assertEquals("a,1,c", result.asString().map(JSString::value).orElseThrow());

        // Edge case: toString on string
        assertEquals(
                "[object String]",
                ArrayPrototype.toString(context, new JSString("a"), new JSValue[]{}).asString().map(JSString::value).orElseThrow());

        // Edge case: toString on object
        assertEquals(
                "[object Object]",
                ArrayPrototype.toString(context, new JSObject(), new JSValue[]{}).asString().map(JSString::value).orElseThrow());

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
        assertEquals(3, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Unshift multiple
        result = ArrayPrototype.unshift(context, arr, new JSValue[]{new JSNumber(-1), new JSNumber(0)});
        assertEquals(5, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(5, arr.getLength());

        // Edge case: unshift to empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.unshift(context, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(1, result.asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(1, emptyArr.getLength());

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
        assertEquals(3, withResult.getLength());
        assertEquals(1.0, withResult.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(42.0, withResult.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, withResult.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Original array should be unchanged
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Negative index
        result = ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(-1), new JSNumber(99)});
        JSArray negIndex = result.asArray().orElseThrow();
        assertEquals(3, negIndex.getLength());
        assertEquals(1.0, negIndex.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, negIndex.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(99.0, negIndex.get(2).asNumber().map(JSNumber::value).orElseThrow());

        // Edge case: index 0
        result = ArrayPrototype.with(context, arr, new JSValue[]{new JSNumber(0), new JSNumber(100)});
        JSArray zeroIndex = result.asArray().orElseThrow();
        assertEquals(3, zeroIndex.getLength());
        assertEquals(100.0, zeroIndex.get(0).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(2.0, zeroIndex.get(1).asNumber().map(JSNumber::value).orElseThrow());
        assertEquals(3.0, zeroIndex.get(2).asNumber().map(JSNumber::value).orElseThrow());

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
