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
    public void testConcat() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));

        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        // Normal case: concat arrays
        JSValue result = ArrayPrototype.concat(ctx, arr, new JSValue[]{arr2});
        JSArray concatenated = result.asArray().orElse(null);
        assertNotNull(concatenated);
        assertEquals(4, concatenated.getLength());
        assertEquals(1.0, concatenated.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, concatenated.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, concatenated.get(2).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, concatenated.get(3).asNumber().map(JSNumber::value).orElse(0D));

        // Concat with values
        result = ArrayPrototype.concat(ctx, arr, new JSValue[]{new JSNumber(5), arr2});
        concatenated = result.asArray().orElse(null);
        assertNotNull(concatenated);
        assertEquals(5, concatenated.getLength());
        assertEquals(5.0, concatenated.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: concat with empty arrays
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.concat(ctx, emptyArr, new JSValue[]{arr});
        concatenated = result.asArray().orElse(null);
        assertNotNull(concatenated);
        assertEquals(2, concatenated.getLength());

        // Edge case: concat on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.concat(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
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

        JSValue result = ArrayPrototype.copyWithin(ctx, arr, new JSValue[]{new JSNumber(0), new JSNumber(3), new JSNumber(5)});
        assertSame(arr, result);
        assertEquals(5, arr.getLength());
        assertEquals(4.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(5.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, arr.get(3).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(5.0, arr.get(4).asNumber().map(JSNumber::value).orElse(0D));

        // With negative indices
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));
        arr2.push(new JSNumber(5));

        result = ArrayPrototype.copyWithin(ctx, arr2, new JSValue[]{new JSNumber(-2), new JSNumber(0), new JSNumber(2)});
        assertSame(arr2, result);
        assertEquals(5, arr2.getLength());
        assertEquals(1.0, arr2.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr2.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr2.get(2).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(1.0, arr2.get(3).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr2.get(4).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.copyWithin(ctx, emptyArr, new JSValue[]{new JSNumber(0), new JSNumber(1)});
        assertSame(emptyArr, result);

        // Edge case: no arguments
        JSArray arr3 = new JSArray();
        arr3.push(new JSNumber(1));
        result = ArrayPrototype.copyWithin(ctx, arr3, new JSValue[]{});
        assertSame(arr3, result);

        // Edge case: copyWithin on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.copyWithin(ctx, nonArray, new JSValue[]{new JSNumber(0), new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testEvery() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(4));
        arr.push(new JSNumber(6));

        // Normal case: all even
        JSFunction isEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.every(ctx, arr, new JSValue[]{isEvenFn});
        assertTrue(result.isBooleanTrue());

        // Not all pass
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        result = ArrayPrototype.every(ctx, arr2, new JSValue[]{isEvenFn});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: empty array (vacuously true)
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.every(ctx, emptyArr, new JSValue[]{isEvenFn});
        assertEquals(JSBoolean.TRUE, result);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.every(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: every on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.every(ctx, nonArray, new JSValue[]{isEvenFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testFill() {
        // Normal case: fill entire array
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        JSValue result = ArrayPrototype.fill(ctx, arr, new JSValue[]{new JSNumber(0)});
        assertSame(arr, result);
        assertEquals(4, arr.getLength());
        assertEquals(0.0, arr.get(0).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr.get(1).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr.get(2).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr.get(3).asNumber().map(JSNumber::value).orElse(-1D));

        // Fill with start index
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        result = ArrayPrototype.fill(ctx, arr2, new JSValue[]{new JSNumber(0), new JSNumber(2)});
        assertSame(arr2, result);
        assertEquals(4, arr2.getLength());
        assertEquals(1.0, arr2.get(0).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(2.0, arr2.get(1).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr2.get(2).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr2.get(3).asNumber().map(JSNumber::value).orElse(-1D));

        // Fill with start and end
        JSArray arr3 = new JSArray();
        arr3.push(new JSNumber(1));
        arr3.push(new JSNumber(2));
        arr3.push(new JSNumber(3));
        arr3.push(new JSNumber(4));

        result = ArrayPrototype.fill(ctx, arr3, new JSValue[]{new JSNumber(0), new JSNumber(1), new JSNumber(3)});
        assertSame(arr3, result);
        assertEquals(4, arr3.getLength());
        assertEquals(1.0, arr3.get(0).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr3.get(1).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr3.get(2).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(4.0, arr3.get(3).asNumber().map(JSNumber::value).orElse(-1D));

        // With negative indices
        JSArray arr4 = new JSArray();
        arr4.push(new JSNumber(1));
        arr4.push(new JSNumber(2));
        arr4.push(new JSNumber(3));
        arr4.push(new JSNumber(4));

        result = ArrayPrototype.fill(ctx, arr4, new JSValue[]{new JSNumber(0), new JSNumber(-3), new JSNumber(-1)});
        assertSame(arr4, result);
        assertEquals(4, arr4.getLength());
        assertEquals(1.0, arr4.get(0).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr4.get(1).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(0.0, arr4.get(2).asNumber().map(JSNumber::value).orElse(-1D));
        assertEquals(4.0, arr4.get(3).asNumber().map(JSNumber::value).orElse(-1D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.fill(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertSame(emptyArr, result);

        // Edge case: fill on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.fill(ctx, nonArray, new JSValue[]{new JSNumber(0)}));
        assertPendingException(ctx);
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
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.filter(ctx, arr, new JSValue[]{evenFn});
        JSArray filtered = result.asArray().orElse(null);
        assertNotNull(filtered);
        assertEquals(2, filtered.getLength());
        assertEquals(2.0, filtered.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, filtered.get(1).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: no elements pass filter
        JSFunction noneFn = createTestFunction(args -> JSBoolean.FALSE);
        result = ArrayPrototype.filter(ctx, arr, new JSValue[]{noneFn});
        JSArray arr1 = result.asArray().orElse(null);
        assertNotNull(arr1);
        assertEquals(0, arr1.getLength());

        // Edge case: all elements pass filter
        JSFunction allFn = createTestFunction(args -> JSBoolean.TRUE);
        result = ArrayPrototype.filter(ctx, arr, new JSValue[]{allFn});
        arr1 = result.asArray().orElse(null);
        assertNotNull(arr1);
        assertEquals(4, arr1.getLength());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.filter(ctx, emptyArr, new JSValue[]{evenFn});
        arr1 = result.asArray().orElse(null);
        assertNotNull(arr1);
        assertEquals(0, arr1.getLength());

        // Edge case: no callback
        assertTypeError(ArrayPrototype.filter(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: filter on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.filter(ctx, nonArray, new JSValue[]{evenFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testFind() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: find even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.find(ctx, arr, new JSValue[]{findEvenFn});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.find(ctx, arr, new JSValue[]{findNegativeFn});
        assertTrue(result.isUndefined());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.find(ctx, emptyArr, new JSValue[]{findEvenFn});
        assertTrue(result.isUndefined());

        // Edge case: no callback
        assertTypeError(ArrayPrototype.find(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: find on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.find(ctx, nonArray, new JSValue[]{findEvenFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testFindIndex() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: find index of even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.findIndex(ctx, arr, new JSValue[]{findEvenFn});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findIndex(ctx, arr, new JSValue[]{findNegativeFn});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findIndex(ctx, emptyArr, new JSValue[]{findEvenFn});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: no callback
        assertTypeError(ArrayPrototype.findIndex(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: findIndex on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.findIndex(ctx, nonArray, new JSValue[]{findEvenFn}));
        assertPendingException(ctx);
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
        JSValue result = ArrayPrototype.flat(ctx, arr, new JSValue[]{});
        JSArray flattened = result.asArray().orElse(null);
        assertNotNull(flattened);
        assertEquals(5, flattened.getLength());
        assertEquals(0.0, flattened.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, flattened.get(1).asNumber().map(JSNumber::value).orElse(0D));
        JSArray childArray = flattened.get(2).asArray().orElse(null);
        assertNotNull(childArray);
        assertEquals(1.0, childArray.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, childArray.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, flattened.get(3).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(5.0, flattened.get(4).asNumber().map(JSNumber::value).orElse(0D));

        // With depth
        result = ArrayPrototype.flat(ctx, arr, new JSValue[]{new JSNumber(0)});
        flattened = result.asArray().orElse(null);
        assertNotNull(flattened);
        assertEquals(3, flattened.getLength()); // No flattening

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.flat(ctx, emptyArr, new JSValue[]{});
        JSArray arr1 = result.asArray().orElse(null);
        assertNotNull(arr1);
        assertEquals(0, arr1.getLength());

        // Edge case: flat on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.flat(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testFlatMap() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: flatMap with function that returns arrays
        JSFunction doubleAndWrapFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            JSArray result = new JSArray();
            result.push(new JSNumber(val));
            result.push(new JSNumber(val * 2));
            return result;
        });

        JSValue result = ArrayPrototype.flatMap(ctx, arr, new JSValue[]{doubleAndWrapFn});
        JSArray flattened = result.asArray().orElse(null);
        assertNotNull(flattened);
        assertEquals(6, flattened.getLength());
        assertEquals(1.0, flattened.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, flattened.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, flattened.get(2).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, flattened.get(3).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, flattened.get(4).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(6.0, flattened.get(5).asNumber().map(JSNumber::value).orElse(0D));

        // Case where callback returns non-array
        JSFunction identityFn = createTestFunction(args -> args[0]);
        result = ArrayPrototype.flatMap(ctx, arr, new JSValue[]{identityFn});
        flattened = result.asArray().orElse(null);
        assertNotNull(flattened);
        assertEquals(3, flattened.getLength());
        assertEquals(1.0, flattened.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, flattened.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, flattened.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.flatMap(ctx, emptyArr, new JSValue[]{doubleAndWrapFn});
        JSArray arr1 = result.asArray().orElse(null);
        assertNotNull(arr1);
        assertEquals(0, arr1.getLength());

        // Edge case: flatMap on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.flatMap(ctx, nonArray, new JSValue[]{doubleAndWrapFn}));
        assertPendingException(ctx);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.flatMap(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);
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
            sum[0] += args[0].asNumber().map(JSNumber::value).orElse(0D);
            return JSUndefined.INSTANCE;
        });

        JSValue result = ArrayPrototype.forEach(ctx, arr, new JSValue[]{collectFn});
        assertTrue(result.isUndefined());
        assertEquals(3, count[0]);
        assertEquals(6.0, sum[0]);

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.forEach(ctx, arr, new JSValue[]{collectFn, thisArg});
        assertTrue(result.isUndefined());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.forEach(ctx, emptyArr, new JSValue[]{collectFn});
        assertTrue(result.isUndefined());

        // Edge case: no callback
        assertTypeError(ArrayPrototype.forEach(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: forEach on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.forEach(ctx, nonArray, new JSValue[]{collectFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetLength() {
        // Normal case: non-empty array
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        JSValue result = ArrayPrototype.getLength(ctx, arr, new JSValue[]{});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.getLength(ctx, emptyArr, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(-1.0));

        // Array with specific length
        JSArray arrWithLength = new JSArray(10);
        result = ArrayPrototype.getLength(ctx, arrWithLength, new JSValue[]{});
        assertEquals(10.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // After modifying array
        arr.push(new JSNumber(4));
        result = ArrayPrototype.getLength(ctx, arr, new JSValue[]{});
        assertEquals(4.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        arr.pop();
        result = ArrayPrototype.getLength(ctx, arr, new JSValue[]{});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: getLength on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.getLength(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetSymbolUnscopables() {
        // Normal case: get unscopables
        JSArray arr = new JSArray();
        JSValue result = ArrayPrototype.getSymbolUnscopables(ctx, arr, new JSValue[]{});

        assertTrue(result.isObject());
        JSObject unscopables = result.asObject().orElse(null);
        assertNotNull(unscopables);

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
        JSValue result = ArrayPrototype.includes(ctx, arr, new JSValue[]{new JSNumber(2)});
        assertEquals(JSBoolean.TRUE, result);

        // Not found
        result = ArrayPrototype.includes(ctx, arr, new JSValue[]{new JSNumber(3)});
        assertEquals(JSBoolean.FALSE, result);

        // With fromIndex
        result = ArrayPrototype.includes(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(JSBoolean.FALSE, result);

        // Negative fromIndex
        result = ArrayPrototype.includes(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(-3)});
        assertEquals(JSBoolean.TRUE, result);

        // Edge case: includes undefined
        result = ArrayPrototype.includes(ctx, arr, new JSValue[]{JSUndefined.INSTANCE});
        assertEquals(JSBoolean.TRUE, result);

        // Edge case: includes NaN (special case)
        JSArray nanArr = new JSArray();
        nanArr.push(new JSNumber(Double.NaN));
        result = ArrayPrototype.includes(ctx, nanArr, new JSValue[]{new JSNumber(Double.NaN)});
        assertEquals(JSBoolean.TRUE, result);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.includes(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: no search element
        result = ArrayPrototype.includes(ctx, arr, new JSValue[]{});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: includes on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.includes(ctx, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testIndexOf() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case
        JSValue result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(2)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // With fromIndex
        result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Not found
        result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(4)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Negative fromIndex
        result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(-2)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.indexOf(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: no search element
        result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{});
        assertEquals(-1.0, ((JSNumber) result).value());

        // Edge case: indexOf on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.indexOf(ctx, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testJoin() {
        JSArray arr = new JSArray();
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        // Normal case
        JSValue result = ArrayPrototype.join(ctx, arr, new JSValue[]{new JSString("-")});
        assertEquals("a-b-c", result.asString().map(JSString::value).orElse(""));

        // Default separator
        result = ArrayPrototype.join(ctx, arr, new JSValue[]{});
        assertEquals("a,b,c", result.asString().map(JSString::value).orElse(""));

        // Empty separator
        result = ArrayPrototype.join(ctx, arr, new JSValue[]{new JSString("")});
        assertEquals("abc", result.asString().map(JSString::value).orElse(""));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.join(ctx, emptyArr, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse(""));

        // Edge case: array with null/undefined
        JSArray mixedArr = new JSArray();
        mixedArr.push(new JSString("a"));
        mixedArr.push(JSNull.INSTANCE);
        mixedArr.push(JSUndefined.INSTANCE);
        mixedArr.push(new JSString("b"));
        result = ArrayPrototype.join(ctx, mixedArr, new JSValue[]{});
        assertEquals("a,,,b", result.asString().map(JSString::value).orElse(""));

        // Edge case: join on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.join(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testLastIndexOf() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case
        JSValue result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(2)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // With fromIndex
        result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Not found
        result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(4)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Negative fromIndex
        result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(-1)});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.lastIndexOf(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(-1.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: no search element
        result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{});
        assertEquals(-1.0, ((JSNumber) result).value());

        // Edge case: lastIndexOf on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.lastIndexOf(ctx, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testMap() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: double each element
        JSFunction doubleFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return new JSNumber(val * 2);
        });

        JSValue result = ArrayPrototype.map(ctx, arr, new JSValue[]{doubleFn});
        JSArray mapped = result.asArray().orElse(null);
        assertNotNull(mapped);
        assertEquals(3, mapped.getLength());
        assertEquals(2.0, mapped.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, mapped.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(6.0, mapped.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.map(ctx, arr, new JSValue[]{doubleFn, thisArg});
        assertTrue(result.isArray());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.map(ctx, emptyArr, new JSValue[]{doubleFn});
        JSArray mappedEmpty = result.asArray().orElse(null);
        assertNotNull(mappedEmpty);
        assertEquals(0, mappedEmpty.getLength());

        // Edge case: no callback
        assertTypeError(ArrayPrototype.map(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: non-function callback
        assertTypeError(ArrayPrototype.map(ctx, arr, new JSValue[]{new JSString("not a function")}));
        assertPendingException(ctx);

        // Edge case: map on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.map(ctx, nonArray, new JSValue[]{doubleFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testPop() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertEquals(3.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2, arr.getLength());

        result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertEquals(2.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(1, arr.getLength());

        // Pop from empty array
        result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(0, arr.getLength());

        result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertTrue(result.isUndefined());

        // Edge case: pop from non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.pop(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testPush() {
        // Normal case
        JSArray arr = new JSArray();
        JSValue result = ArrayPrototype.push(ctx, arr, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        assertEquals(2, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));

        // Push more
        result = ArrayPrototype.push(ctx, arr, new JSValue[]{new JSNumber(3)});
        assertEquals(3, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3, arr.getLength());

        // Edge case: push to non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.push(ctx, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testReduce() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: sum
        JSFunction sumFn = createTestFunction(args -> {
            double acc = args[0].asNumber().map(JSNumber::value).orElse(0D);
            double curr = args[1].asNumber().map(JSNumber::value).orElse(0D);
            return new JSNumber(acc + curr);
        });

        JSValue result = ArrayPrototype.reduce(ctx, arr, new JSValue[]{sumFn});
        assertEquals(6.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // With initial value
        result = ArrayPrototype.reduce(ctx, arr, new JSValue[]{sumFn, new JSNumber(10)});
        assertEquals(16.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduce(ctx, emptyArr, new JSValue[]{sumFn}));
        assertPendingException(ctx);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduce(ctx, emptyArr, new JSValue[]{sumFn, new JSNumber(42)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(5));
        result = ArrayPrototype.reduce(ctx, singleArr, new JSValue[]{sumFn});
        assertEquals(5.0, result.asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: no callback
        assertTypeError(ArrayPrototype.reduce(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: reduce on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.reduce(ctx, nonArray, new JSValue[]{sumFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testReduceRight() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: concatenate strings in reverse
        JSFunction concatFn = createTestFunction(args -> {
            String acc = args[0].asString().map(JSString::value).orElse("");
            String curr = args[1].asString().map(JSString::value).orElse("");
            return new JSString(acc + curr);
        });

        JSArray strArr = new JSArray();
        strArr.push(new JSString("a"));
        strArr.push(new JSString("b"));
        strArr.push(new JSString("c"));

        JSValue result = ArrayPrototype.reduceRight(ctx, strArr, new JSValue[]{concatFn});
        assertEquals("cba", result.asString().map(JSString::value).orElse(""));

        // With initial value
        result = ArrayPrototype.reduceRight(ctx, strArr, new JSValue[]{concatFn, new JSString("d")});
        assertEquals("dcba", result.asString().map(JSString::value).orElse(""));

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduceRight(ctx, emptyArr, new JSValue[]{concatFn}));
        assertPendingException(ctx);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduceRight(ctx, emptyArr, new JSValue[]{concatFn, new JSString("x")});
        assertEquals("x", result.asString().map(JSString::value).orElse(""));

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSString("z"));
        result = ArrayPrototype.reduceRight(ctx, singleArr, new JSValue[]{concatFn});
        assertEquals("z", result.asString().map(JSString::value).orElse(""));

        // Edge case: no callback
        assertTypeError(ArrayPrototype.reduceRight(ctx, strArr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: reduceRight on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.reduceRight(ctx, nonArray, new JSValue[]{concatFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testReverse() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case
        JSValue result = ArrayPrototype.reverse(ctx, arr, new JSValue[]{});
        assertSame(arr, result);
        assertEquals(4, arr.getLength());
        assertEquals(4.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(1.0, arr.get(3).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.reverse(ctx, emptyArr, new JSValue[]{});
        assertSame(emptyArr, result);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.reverse(ctx, singleArr, new JSValue[]{});
        assertSame(singleArr, result);
        assertEquals(42.0, singleArr.get(0).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: reverse on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.reverse(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testShift() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.shift(ctx, arr, new JSValue[]{});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2, arr.getLength());
        assertEquals(2.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: shift from empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.shift(ctx, emptyArr, new JSValue[]{});
        assertTrue(result.isUndefined());

        // Edge case: shift from non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.shift(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
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
        JSValue result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(1), new JSNumber(4)});
        JSArray sliced = result.asArray().orElse(null);
        assertNotNull(sliced);
        assertEquals(3, sliced.getLength());
        assertEquals(1.0, sliced.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, sliced.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, sliced.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Slice from start
        result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(2)});
        sliced = result.asArray().orElse(null);
        assertNotNull(sliced);
        assertEquals(3, sliced.getLength());
        assertEquals(2.0, sliced.get(0).asNumber().map(JSNumber::value).orElse(0D));

        // Slice with negative indices
        result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(-2)});
        sliced = result.asArray().orElse(null);
        assertNotNull(sliced);
        assertEquals(2, sliced.getLength());
        assertEquals(3.0, sliced.get(0).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty slice
        result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        sliced = result.asArray().orElse(null);
        assertNotNull(sliced);
        assertEquals(0, sliced.getLength());

        // Edge case: slice on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.slice(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testSome() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(5));

        // Normal case: some odd
        JSFunction isOddFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElse(0D);
            return (val % 2 != 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.some(ctx, arr, new JSValue[]{isOddFn});
        assertEquals(JSBoolean.TRUE, result);

        // None pass
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(4));
        arr2.push(new JSNumber(6));

        result = ArrayPrototype.some(ctx, arr2, new JSValue[]{isOddFn});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: empty array (vacuously false)
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.some(ctx, emptyArr, new JSValue[]{isOddFn});
        assertEquals(JSBoolean.FALSE, result);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.some(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: some on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.some(ctx, nonArray, new JSValue[]{isOddFn}));
        assertPendingException(ctx);
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
        JSValue result = ArrayPrototype.sort(ctx, arr, new JSValue[]{});
        assertSame(arr, result);
        assertEquals(5, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(1.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, arr.get(3).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(5.0, arr.get(4).asNumber().map(JSNumber::value).orElse(0D));

        // With compare function
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(4));

        JSFunction descCompare = createTestFunction(args -> {
            double a = args[0].asNumber().map(JSNumber::value).orElse(0D);
            double b = args[1].asNumber().map(JSNumber::value).orElse(0D);
            return new JSNumber(b - a);
        });

        result = ArrayPrototype.sort(ctx, arr2, new JSValue[]{descCompare});
        assertSame(arr2, result);
        assertEquals(3, arr2.getLength());
        assertEquals(4.0, arr2.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr2.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(1.0, arr2.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.sort(ctx, emptyArr, new JSValue[]{});
        assertSame(emptyArr, result);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.sort(ctx, singleArr, new JSValue[]{});
        assertSame(singleArr, result);
        assertEquals(42.0, singleArr.get(0).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: sort on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.sort(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
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
        JSValue result = ArrayPrototype.splice(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2), new JSNumber(10), new JSNumber(11)});
        JSArray deleted = result.asArray().orElse(null);
        assertNotNull(deleted);
        assertEquals(2, deleted.getLength());
        assertEquals(2.0, deleted.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, deleted.get(1).asNumber().map(JSNumber::value).orElse(0D));

        // Check modified array
        assertEquals(5, arr.getLength());
        assertEquals(0.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(1.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(10.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(11.0, arr.get(3).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(4.0, arr.get(4).asNumber().map(JSNumber::value).orElse(0D));

        // Edge case: splice with no deletions
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        result = ArrayPrototype.splice(ctx, arr2, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(1.5)});
        deleted = result.asArray().orElse(null);
        assertNotNull(deleted);
        assertEquals(0, deleted.getLength());
        assertEquals(3, arr2.getLength());

        // Edge case: splice on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.splice(ctx, nonArray, new JSValue[]{new JSNumber(0), new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testSymbolIterator() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Set prototype to Array.prototype
        JSObject arrayProto = ctx.getGlobalObject().get("Array").asObject().orElse(null).get("prototype").asObject().orElse(null);
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
        JSValue iteratorResult = ((JSFunction) iteratorFn).call(ctx, arr, new JSValue[0]);
        assertTrue(iteratorResult.isIterator());

        // Test iteration
        JSIterator iterator = (JSIterator) iteratorResult;
        JSObject result1 = iterator.next();
        assertTrue(result1.get("done").isBooleanFalse());
        assertEquals(1.0, result1.get("value").asNumber().map(JSNumber::value).orElse(0.0));

        JSObject result2 = iterator.next();
        assertTrue(result2.get("done").isBooleanFalse());
        assertEquals(2.0, result2.get("value").asNumber().map(JSNumber::value).orElse(0.0));

        JSObject result3 = iterator.next();
        assertTrue(result3.get("done").isBooleanFalse());
        assertEquals(3.0, result3.get("value").asNumber().map(JSNumber::value).orElse(0.0));

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

        JSValue result = ArrayPrototype.toLocaleString(ctx, arr, new JSValue[]{});
        assertEquals("a,b,c", result.asString().map(JSString::value).orElse(""));

        // With numbers
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        arr2.push(new JSNumber(3));

        result = ArrayPrototype.toLocaleString(ctx, arr2, new JSValue[]{});
        assertEquals("1,2,3", result.asString().map(JSString::value).orElse(""));

        // With null and undefined
        JSArray arr3 = new JSArray();
        arr3.push(new JSString("a"));
        arr3.push(JSNull.INSTANCE);
        arr3.push(JSUndefined.INSTANCE);
        arr3.push(new JSString("b"));

        result = ArrayPrototype.toLocaleString(ctx, arr3, new JSValue[]{});
        assertEquals("a,,,b", result.asString().map(JSString::value).orElse(""));

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.toLocaleString(ctx, emptyArr, new JSValue[]{});
        assertEquals("", result.asString().map(JSString::value).orElse("FAIL"));

        // Edge case: toLocaleString on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.toLocaleString(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testToString() {
        JSArray arr = new JSArray();
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        // Normal case
        JSValue result = ArrayPrototype.toString(ctx, arr, new JSValue[]{});
        assertEquals("a,b,c", result.asString().map(JSString::value).orElse(""));

        // Edge case: toString on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.toString(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testUnshift() {
        JSArray arr = new JSArray();
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.unshift(ctx, arr, new JSValue[]{new JSNumber(1)});
        assertEquals(3, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3, arr.getLength());
        assertEquals(1.0, arr.get(0).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(2.0, arr.get(1).asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(3.0, arr.get(2).asNumber().map(JSNumber::value).orElse(0D));

        // Unshift multiple
        result = ArrayPrototype.unshift(ctx, arr, new JSValue[]{new JSNumber(-1), new JSNumber(0)});
        assertEquals(5, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(5, arr.getLength());

        // Edge case: unshift to empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.unshift(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(1, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(1, emptyArr.getLength());

        // Edge case: unshift to non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.unshift(ctx, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);
    }
}
