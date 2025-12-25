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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArrayPrototype methods.
 */
public class ArrayPrototypeTest extends BaseTest {
    private JSArray arr;

    // Helper method to create a simple test function
    private JSFunction createTestFunction(java.util.function.Function<JSValue[], JSValue> impl) {
        return new JSNativeFunction("test", 1, (ctx, thisArg, args) -> impl.apply(args));
    }

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        arr = new JSArray();
    }

    @Test
    public void testConcat() {
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));

        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(4));

        // Normal case: concat arrays
        JSValue result = ArrayPrototype.concat(ctx, arr, new JSValue[]{arr2});
        assertInstanceOf(JSArray.class, result);
        JSArray concatenated = (JSArray) result;
        assertEquals(4, concatenated.getLength());
        assertEquals(1.0, ((JSNumber) concatenated.get(0)).value());
        assertEquals(2.0, ((JSNumber) concatenated.get(1)).value());
        assertEquals(3.0, ((JSNumber) concatenated.get(2)).value());
        assertEquals(4.0, ((JSNumber) concatenated.get(3)).value());

        // Concat with values
        result = ArrayPrototype.concat(ctx, arr, new JSValue[]{new JSNumber(5), arr2});
        concatenated = (JSArray) result;
        assertEquals(5, concatenated.getLength());
        assertEquals(5.0, ((JSNumber) concatenated.get(2)).value());

        // Edge case: concat with empty arrays
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.concat(ctx, emptyArr, new JSValue[]{arr});
        concatenated = (JSArray) result;
        assertEquals(2, concatenated.getLength());

        // Edge case: concat on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.concat(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testEvery() {
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(4));
        arr.push(new JSNumber(6));

        // Normal case: all even
        JSFunction isEvenFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.every(ctx, arr, new JSValue[]{isEvenFn});
        assertEquals(JSBoolean.TRUE, result);

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
    public void testFilter() {
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case: filter even numbers
        JSFunction evenFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.filter(ctx, arr, new JSValue[]{evenFn});
        assertInstanceOf(JSArray.class, result);
        JSArray filtered = (JSArray) result;
        assertEquals(2, filtered.getLength());
        assertEquals(2.0, ((JSNumber) filtered.get(0)).value());
        assertEquals(4.0, ((JSNumber) filtered.get(1)).value());

        // Edge case: no elements pass filter
        JSFunction noneFn = createTestFunction(args -> JSBoolean.FALSE);
        result = ArrayPrototype.filter(ctx, arr, new JSValue[]{noneFn});
        assertEquals(0, ((JSArray) result).getLength());

        // Edge case: all elements pass filter
        JSFunction allFn = createTestFunction(args -> JSBoolean.TRUE);
        result = ArrayPrototype.filter(ctx, arr, new JSValue[]{allFn});
        assertEquals(4, ((JSArray) result).getLength());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.filter(ctx, emptyArr, new JSValue[]{evenFn});
        assertEquals(0, ((JSArray) result).getLength());

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
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: find even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.find(ctx, arr, new JSValue[]{findEvenFn});
        assertEquals(2.0, ((JSNumber) result).value());

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.find(ctx, arr, new JSValue[]{findNegativeFn});
        assertInstanceOf(JSUndefined.class, result);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.find(ctx, emptyArr, new JSValue[]{findEvenFn});
        assertInstanceOf(JSUndefined.class, result);

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
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: find index of even number
        JSFunction findEvenFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
            return (val % 2 == 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        JSValue result = ArrayPrototype.findIndex(ctx, arr, new JSValue[]{findEvenFn});
        assertEquals(1.0, ((JSNumber) result).value());

        // No match
        JSFunction findNegativeFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
            return (val < 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        result = ArrayPrototype.findIndex(ctx, arr, new JSValue[]{findNegativeFn});
        assertEquals(-1.0, ((JSNumber) result).value());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.findIndex(ctx, emptyArr, new JSValue[]{findEvenFn});
        assertEquals(-1.0, ((JSNumber) result).value());

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

        arr.push(new JSNumber(0));
        arr.push(arr2);
        arr.push(new JSNumber(5));

        // Normal case: flat()
        JSValue result = ArrayPrototype.flat(ctx, arr, new JSValue[]{});
        assertInstanceOf(JSArray.class, result);
        JSArray flattened = (JSArray) result;
        assertEquals(5, flattened.getLength());
        assertEquals(0.0, ((JSNumber) flattened.get(0)).value());
        assertEquals(3.0, ((JSNumber) flattened.get(1)).value());
        assertInstanceOf(JSArray.class, flattened.get(2)); // nested array
        assertEquals(4.0, ((JSNumber) flattened.get(3)).value());
        assertEquals(5.0, ((JSNumber) flattened.get(4)).value());

        // With depth
        result = ArrayPrototype.flat(ctx, arr, new JSValue[]{new JSNumber(0)});
        flattened = (JSArray) result;
        assertEquals(3, flattened.getLength()); // No flattening

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.flat(ctx, emptyArr, new JSValue[]{});
        assertEquals(0, ((JSArray) result).getLength());

        // Edge case: flat on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.flat(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testForEach() {
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        final int[] count = {0};
        final double[] sum = {0};
        JSFunction collectFn = createTestFunction(args -> {
            count[0]++;
            sum[0] += ((JSNumber) args[0]).value();
            return JSUndefined.INSTANCE;
        });

        JSValue result = ArrayPrototype.forEach(ctx, arr, new JSValue[]{collectFn});
        assertInstanceOf(JSUndefined.class, result);
        assertEquals(3, count[0]);
        assertEquals(6.0, sum[0]);

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.forEach(ctx, arr, new JSValue[]{collectFn, thisArg});
        assertInstanceOf(JSUndefined.class, result);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.forEach(ctx, emptyArr, new JSValue[]{collectFn});
        assertInstanceOf(JSUndefined.class, result);

        // Edge case: no callback
        assertTypeError(ArrayPrototype.forEach(ctx, arr, new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: forEach on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.forEach(ctx, nonArray, new JSValue[]{collectFn}));
        assertPendingException(ctx);
    }

    @Test
    public void testIncludes() {
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
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case
        JSValue result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(2)});
        assertEquals(1.0, ((JSNumber) result).value());

        // With fromIndex
        result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(3.0, ((JSNumber) result).value());

        // Not found
        result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(4)});
        assertEquals(-1.0, ((JSNumber) result).value());

        // Negative fromIndex
        result = ArrayPrototype.indexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(-2)});
        assertEquals(3.0, ((JSNumber) result).value());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.indexOf(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(-1.0, ((JSNumber) result).value());

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
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        // Normal case
        JSValue result = ArrayPrototype.join(ctx, arr, new JSValue[]{new JSString("-")});
        assertEquals("a-b-c", ((JSString) result).getValue());

        // Default separator
        result = ArrayPrototype.join(ctx, arr, new JSValue[]{});
        assertEquals("a,b,c", ((JSString) result).getValue());

        // Empty separator
        result = ArrayPrototype.join(ctx, arr, new JSValue[]{new JSString("")});
        assertEquals("abc", ((JSString) result).getValue());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.join(ctx, emptyArr, new JSValue[]{});
        assertEquals("", ((JSString) result).getValue());

        // Edge case: array with null/undefined
        JSArray mixedArr = new JSArray();
        mixedArr.push(new JSString("a"));
        mixedArr.push(JSNull.INSTANCE);
        mixedArr.push(JSUndefined.INSTANCE);
        mixedArr.push(new JSString("b"));
        result = ArrayPrototype.join(ctx, mixedArr, new JSValue[]{});
        assertEquals("a,,,b", ((JSString) result).getValue());

        // Edge case: join on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.join(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testLastIndexOf() {
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(2));

        // Normal case
        JSValue result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(2)});
        assertEquals(3.0, ((JSNumber) result).value());

        // With fromIndex
        result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        assertEquals(1.0, ((JSNumber) result).value());

        // Not found
        result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(4)});
        assertEquals(-1.0, ((JSNumber) result).value());

        // Negative fromIndex
        result = ArrayPrototype.lastIndexOf(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(-1)});
        assertEquals(3.0, ((JSNumber) result).value());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.lastIndexOf(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(-1.0, ((JSNumber) result).value());

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
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: double each element
        JSFunction doubleFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
            return new JSNumber(val * 2);
        });

        JSValue result = ArrayPrototype.map(ctx, arr, new JSValue[]{doubleFn});
        assertInstanceOf(JSArray.class, result);
        JSArray mapped = (JSArray) result;
        assertEquals(3, mapped.getLength());
        assertEquals(2.0, ((JSNumber) mapped.get(0)).value());
        assertEquals(4.0, ((JSNumber) mapped.get(1)).value());
        assertEquals(6.0, ((JSNumber) mapped.get(2)).value());

        // With thisArg
        JSValue thisArg = new JSObject();
        result = ArrayPrototype.map(ctx, arr, new JSValue[]{doubleFn, thisArg});
        assertInstanceOf(JSArray.class, result);

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.map(ctx, emptyArr, new JSValue[]{doubleFn});
        assertInstanceOf(JSArray.class, result);
        assertEquals(0, ((JSArray) result).getLength());

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
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertEquals(3.0, ((JSNumber) result).value());
        assertEquals(2, arr.getLength());

        result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertEquals(2.0, ((JSNumber) result).value());
        assertEquals(1, arr.getLength());

        // Pop from empty array
        result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertEquals(1.0, ((JSNumber) result).value());
        assertEquals(0, arr.getLength());

        result = ArrayPrototype.pop(ctx, arr, new JSValue[]{});
        assertInstanceOf(JSUndefined.class, result);

        // Edge case: pop from non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.pop(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testPush() {
        // Normal case
        JSValue result = ArrayPrototype.push(ctx, arr, new JSValue[]{new JSNumber(1), new JSNumber(2)});
        assertEquals(2, ((JSNumber) result).value());
        assertEquals(2, arr.getLength());
        assertEquals(1.0, ((JSNumber) arr.get(0)).value());
        assertEquals(2.0, ((JSNumber) arr.get(1)).value());

        // Push more
        result = ArrayPrototype.push(ctx, arr, new JSValue[]{new JSNumber(3)});
        assertEquals(3, ((JSNumber) result).value());
        assertEquals(3, arr.getLength());

        // Edge case: push to non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.push(ctx, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testReduce() {
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: sum
        JSFunction sumFn = createTestFunction(args -> {
            double acc = ((JSNumber) args[0]).value();
            double curr = ((JSNumber) args[1]).value();
            return new JSNumber(acc + curr);
        });

        JSValue result = ArrayPrototype.reduce(ctx, arr, new JSValue[]{sumFn});
        assertEquals(6.0, ((JSNumber) result).value());

        // With initial value
        result = ArrayPrototype.reduce(ctx, arr, new JSValue[]{sumFn, new JSNumber(10)});
        assertEquals(16.0, ((JSNumber) result).value());

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduce(ctx, emptyArr, new JSValue[]{sumFn}));
        assertPendingException(ctx);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduce(ctx, emptyArr, new JSValue[]{sumFn, new JSNumber(42)});
        assertEquals(42.0, ((JSNumber) result).value());

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(5));
        result = ArrayPrototype.reduce(ctx, singleArr, new JSValue[]{sumFn});
        assertEquals(5.0, ((JSNumber) result).value());

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
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case: concatenate strings in reverse
        JSFunction concatFn = createTestFunction(args -> {
            String acc = ((JSString) args[0]).getValue();
            String curr = ((JSString) args[1]).getValue();
            return new JSString(acc + curr);
        });

        JSArray strArr = new JSArray();
        strArr.push(new JSString("a"));
        strArr.push(new JSString("b"));
        strArr.push(new JSString("c"));

        JSValue result = ArrayPrototype.reduceRight(ctx, strArr, new JSValue[]{concatFn});
        assertEquals("cba", ((JSString) result).getValue());

        // With initial value
        result = ArrayPrototype.reduceRight(ctx, strArr, new JSValue[]{concatFn, new JSString("d")});
        assertEquals("dcba", ((JSString) result).getValue());

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray();
        assertTypeError(ArrayPrototype.reduceRight(ctx, emptyArr, new JSValue[]{concatFn}));
        assertPendingException(ctx);

        // Edge case: empty array with initial value
        result = ArrayPrototype.reduceRight(ctx, emptyArr, new JSValue[]{concatFn, new JSString("x")});
        assertEquals("x", ((JSString) result).getValue());

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray();
        singleArr.push(new JSString("z"));
        result = ArrayPrototype.reduceRight(ctx, singleArr, new JSValue[]{concatFn});
        assertEquals("z", ((JSString) result).getValue());

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
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case
        JSValue result = ArrayPrototype.reverse(ctx, arr, new JSValue[]{});
        assertSame(arr, result);
        assertEquals(4, arr.getLength());
        assertEquals(4.0, ((JSNumber) arr.get(0)).value());
        assertEquals(3.0, ((JSNumber) arr.get(1)).value());
        assertEquals(2.0, ((JSNumber) arr.get(2)).value());
        assertEquals(1.0, ((JSNumber) arr.get(3)).value());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.reverse(ctx, emptyArr, new JSValue[]{});
        assertSame(emptyArr, result);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.reverse(ctx, singleArr, new JSValue[]{});
        assertSame(singleArr, result);
        assertEquals(42.0, ((JSNumber) singleArr.get(0)).value());

        // Edge case: reverse on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.reverse(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testShift() {
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.shift(ctx, arr, new JSValue[]{});
        assertEquals(1.0, ((JSNumber) result).value());
        assertEquals(2, arr.getLength());
        assertEquals(2.0, ((JSNumber) arr.get(0)).value());
        assertEquals(3.0, ((JSNumber) arr.get(1)).value());

        // Edge case: shift from empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.shift(ctx, emptyArr, new JSValue[]{});
        assertInstanceOf(JSUndefined.class, result);

        // Edge case: shift from non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.shift(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testSlice() {
        arr.push(new JSNumber(0));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case: slice(1, 4)
        JSValue result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(1), new JSNumber(4)});
        assertInstanceOf(JSArray.class, result);
        JSArray sliced = (JSArray) result;
        assertEquals(3, sliced.getLength());
        assertEquals(1.0, ((JSNumber) sliced.get(0)).value());
        assertEquals(2.0, ((JSNumber) sliced.get(1)).value());
        assertEquals(3.0, ((JSNumber) sliced.get(2)).value());

        // Slice from start
        result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(2)});
        sliced = (JSArray) result;
        assertEquals(3, sliced.getLength());
        assertEquals(2.0, ((JSNumber) sliced.get(0)).value());

        // Slice with negative indices
        result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(-2)});
        sliced = (JSArray) result;
        assertEquals(2, sliced.getLength());
        assertEquals(3.0, ((JSNumber) sliced.get(0)).value());

        // Edge case: empty slice
        result = ArrayPrototype.slice(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2)});
        sliced = (JSArray) result;
        assertEquals(0, sliced.getLength());

        // Edge case: slice on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.slice(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testSome() {
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(5));

        // Normal case: some odd
        JSFunction isOddFn = createTestFunction(args -> {
            double val = ((JSNumber) args[0]).value();
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
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(4));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(5));

        // Normal case: default sort (string comparison)
        JSValue result = ArrayPrototype.sort(ctx, arr, new JSValue[]{});
        assertSame(arr, result);
        assertEquals(5, arr.getLength());
        assertEquals(1.0, ((JSNumber) arr.get(0)).value());
        assertEquals(1.0, ((JSNumber) arr.get(1)).value());
        assertEquals(3.0, ((JSNumber) arr.get(2)).value());
        assertEquals(4.0, ((JSNumber) arr.get(3)).value());
        assertEquals(5.0, ((JSNumber) arr.get(4)).value());

        // With compare function
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(3));
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(4));

        JSFunction descCompare = createTestFunction(args -> {
            double a = ((JSNumber) args[0]).value();
            double b = ((JSNumber) args[1]).value();
            return new JSNumber(b - a);
        });

        result = ArrayPrototype.sort(ctx, arr2, new JSValue[]{descCompare});
        assertSame(arr2, result);
        assertEquals(3, arr2.getLength());
        assertEquals(4.0, ((JSNumber) arr2.get(0)).value());
        assertEquals(3.0, ((JSNumber) arr2.get(1)).value());
        assertEquals(1.0, ((JSNumber) arr2.get(2)).value());

        // Edge case: empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.sort(ctx, emptyArr, new JSValue[]{});
        assertSame(emptyArr, result);

        // Edge case: single element
        JSArray singleArr = new JSArray();
        singleArr.push(new JSNumber(42));
        result = ArrayPrototype.sort(ctx, singleArr, new JSValue[]{});
        assertSame(singleArr, result);
        assertEquals(42.0, ((JSNumber) singleArr.get(0)).value());

        // Edge case: sort on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.sort(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testSplice() {
        arr.push(new JSNumber(0));
        arr.push(new JSNumber(1));
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));
        arr.push(new JSNumber(4));

        // Normal case: splice(2, 2, 10, 11)
        JSValue result = ArrayPrototype.splice(ctx, arr, new JSValue[]{new JSNumber(2), new JSNumber(2), new JSNumber(10), new JSNumber(11)});
        assertInstanceOf(JSArray.class, result);
        JSArray deleted = (JSArray) result;
        assertEquals(2, deleted.getLength());
        assertEquals(2.0, ((JSNumber) deleted.get(0)).value());
        assertEquals(3.0, ((JSNumber) deleted.get(1)).value());

        // Check modified array
        assertEquals(5, arr.getLength());
        assertEquals(0.0, ((JSNumber) arr.get(0)).value());
        assertEquals(1.0, ((JSNumber) arr.get(1)).value());
        assertEquals(10.0, ((JSNumber) arr.get(2)).value());
        assertEquals(11.0, ((JSNumber) arr.get(3)).value());
        assertEquals(4.0, ((JSNumber) arr.get(4)).value());

        // Edge case: splice with no deletions
        JSArray arr2 = new JSArray();
        arr2.push(new JSNumber(1));
        arr2.push(new JSNumber(2));
        result = ArrayPrototype.splice(ctx, arr2, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(1.5)});
        deleted = (JSArray) result;
        assertEquals(0, deleted.getLength());
        assertEquals(3, arr2.getLength());

        // Edge case: splice on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.splice(ctx, nonArray, new JSValue[]{new JSNumber(0), new JSNumber(1)}));
        assertPendingException(ctx);
    }

    @Test
    public void testToString() {
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        // Normal case
        JSValue result = ArrayPrototype.toString(ctx, arr, new JSValue[]{});
        assertEquals("a,b,c", ((JSString) result).getValue());

        // Edge case: toString on non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.toString(ctx, nonArray, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testUnshift() {
        arr.push(new JSNumber(2));
        arr.push(new JSNumber(3));

        // Normal case
        JSValue result = ArrayPrototype.unshift(ctx, arr, new JSValue[]{new JSNumber(1)});
        assertEquals(3, ((JSNumber) result).value());
        assertEquals(3, arr.getLength());
        assertEquals(1.0, ((JSNumber) arr.get(0)).value());
        assertEquals(2.0, ((JSNumber) arr.get(1)).value());
        assertEquals(3.0, ((JSNumber) arr.get(2)).value());

        // Unshift multiple
        result = ArrayPrototype.unshift(ctx, arr, new JSValue[]{new JSNumber(-1), new JSNumber(0)});
        assertEquals(5, ((JSNumber) result).value());
        assertEquals(5, arr.getLength());

        // Edge case: unshift to empty array
        JSArray emptyArr = new JSArray();
        result = ArrayPrototype.unshift(ctx, emptyArr, new JSValue[]{new JSNumber(1)});
        assertEquals(1, ((JSNumber) result).value());
        assertEquals(1, emptyArr.getLength());

        // Edge case: unshift to non-array
        JSValue nonArray = new JSString("not an array");
        assertTypeError(ArrayPrototype.unshift(ctx, nonArray, new JSValue[]{new JSNumber(1)}));
        assertPendingException(ctx);
    }
}
