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

package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.iterator.JSIterator;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ArrayPrototype methods.
 */
public class ArrayPrototypeTest extends BaseJavetTest {
    private void assertFind(JSNativeCallback method, int expected, JSValue missing) {
        JSArray array = nativeArray(1, 2, 3, 4);
        JSValue[] even = {evenPredicate()};
        JSFunction negative = createTestFunction(
                args -> JSBoolean.valueOf(args[0].asNumber().orElseThrow().value() < 0));
        assertNumberValue(callBuiltin(method, array, even), expected);
        assertThat(callBuiltin(method, array, new JSValue[]{negative})).isEqualTo(missing);
        assertThat(callBuiltin(method, new JSArray(context), even)).isEqualTo(missing);
        assertPendingTypeError(callBuiltin(method, array));
        assertPendingTypeError(callBuiltin(method, JSNull.INSTANCE, even));
    }

    // Helper method to create a simple test function
    private JSFunction createTestFunction(Function<JSValue[], JSValue> impl) {
        return new JSNativeFunction(context, "test", 1, (context, thisArg, args) -> impl.apply(args));
    }

    private JSFunction evenPredicate() {
        return createTestFunction(args -> JSBoolean.valueOf(args[0].asNumber().orElseThrow().value() % 2 == 0));
    }

    @Test
    public void testAt() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case: positive index
        BuiltinAssertions at = builtin(ArrayPrototype::at, arr);
        at.expectNumber(1.0, 0);

        at.expectNumber(3.0, 2);

        // Negative index
        at.expectNumber(3.0, -1);

        at.expectNumber(2.0, -2);

        // Out of bounds
        at.expectValue(JSUndefined.INSTANCE, 3);

        at.expectValue(JSUndefined.INSTANCE, -4);

        // No arguments: ToIntegerOrInfinity(undefined) = 0, so returns arr[0]
        at.expectNumber(1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        var atMethod = nativeMethod(ArrayPrototype::at);
        assertThat(atMethod.call(emptyArr, 0)).isEqualTo(JSUndefined.INSTANCE);

        // Generic: at works on non-array objects via ToObject + LengthOfArrayLike
        JSValue nonArray = new JSString("abc");
        assertStringValue(atMethod.call(nonArray, 0), "a");
    }

    @Test
    public void testBorrowedTypedArrayTraversalReadsLengthAsAProperty() {
        assertStringWithJavet("""
                (() => {
                    const view = new Uint8Array([2, 4, 8]);
                    Object.defineProperty(view, 'length', { get() { return 1; } });
                    const sum = (a, b) => a + b;
                    return [view.reduce(sum), Array.prototype.reduce.call(view, sum),
                            view.includes(8), Array.prototype.includes.call(view, 8)].join(',');
                })()""");
    }

    @Test
    public void testConcat() {
        JSArray arr = nativeArray(1, 2);

        JSArray arr2 = nativeArray(3, 4);

        // Normal case: concat arrays
        var concatMethod = builtin(ArrayPrototype::concat, arr);
        JSValue result = concatMethod.call(arr2);
        JSArray concatenated = result.asArray().orElseThrow();
        assertThat(concatenated.getLength()).isEqualTo(4);
        assertNumberElements(concatenated, 0, 1.0, 2.0, 3.0, 4.0);

        // Concat with values
        result = concatMethod.call(5, arr2);
        concatenated = result.asArray().orElseThrow();
        assertThat(concatenated.getLength()).isEqualTo(5);
        assertNumberValue(concatenated.get(2), 5.0);

        // Edge case: concat with empty arrays
        JSArray emptyArr = new JSArray(context);
        result = callBuiltin(ArrayPrototype::concat, emptyArr, arr);
        concatenated = result.asArray().orElseThrow();
        assertThat(concatenated.getLength()).isEqualTo(2);

        // Edge case: concat on non-array (per ES spec, ToObject wraps it; the wrapper is non-spreadable)
        JSValue nonArray = new JSString("not an array");
        concatenated = callBuiltin(ArrayPrototype::concat, nonArray).asArray().orElseThrow();
        assertThat(concatenated.getLength()).isEqualTo(1);
    }

    @Test
    public void testCopyWithin() {
        // Normal case: copyWithin(0, 3, 5)
        JSArray arr = nativeArray(1, 2, 3, 4, 5);

        var copyWithinMethod = nativeMethod(ArrayPrototype::copyWithin);
        assertThat(copyWithinMethod.call(arr, 0, 3, 5)).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(5);
        assertNumberElements(arr, 0, 4.0, 5.0, 3.0, 4.0, 5.0);

        // With negative indices
        JSArray arr2 = nativeArray(1, 2, 3, 4, 5);

        assertThat(copyWithinMethod.call(arr2, -2, 0, 2)).isSameAs(arr2);
        assertThat(arr2.getLength()).isEqualTo(5);
        assertNumberElements(arr2, 0, 1.0, 2.0, 3.0, 1.0, 2.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(copyWithinMethod.call(emptyArr, 0, 1)).isSameAs(emptyArr);

        // Edge case: no arguments
        JSArray arr3 = nativeArray(1);
        assertThat(copyWithinMethod.call(arr3)).isSameAs(arr3);

        // Edge case: copyWithin on non-array
        JSValue nonArray = new JSString("not an array");
        assertPendingTypeError(copyWithinMethod.call(nonArray, 0, 1));

        assertObjectWithJavet("[1,2,3,4,5].copyWithin(0,3,5)", "[1,2,3,4,5].copyWithin(-2,0,2)",
                "[1,2,3,4,5].copyWithin(2)");
        assertStringWithJavet("(() => { const a = [1,2,3,4,5]; a.copyWithin(0,3,5); return a.join(','); })()",
                "(() => { const a = [1,2,3,4,5]; a.copyWithin(-2,0,2); return a.join(','); })()",
                "(() => { const a = [1,2,3,4,5]; a.copyWithin(2); return a.join(','); })()");
        assertBooleanWithJavet("(() => { const a = [1,2,3]; return a.copyWithin(1,2) === a; })()");
        assertIntegerWithJavet("(() => { const a = [1,2,3,4,5]; a.copyWithin(1,3,5); return a.length; })()");
    }

    @Test
    public void testEvery() {
        JSArray arr = nativeArray(2, 4, 6);

        // Normal case: all even
        JSFunction isEvenFn = evenPredicate();

        var everyMethod = builtin(ArrayPrototype::every, arr);
        assertThat(everyMethod.call(isEvenFn)).isSameAs(JSBoolean.TRUE);

        // Not all pass
        JSArray arr2 = nativeArray(2, 3, 4);

        assertThat(callBuiltin(ArrayPrototype::every, arr2, isEvenFn)).isEqualTo(JSBoolean.FALSE);

        // Edge case: empty array (vacuously true)
        JSArray emptyArr = new JSArray(context);
        assertThat(callBuiltin(ArrayPrototype::every, emptyArr, isEvenFn)).isEqualTo(JSBoolean.TRUE);

        // Edge case: no callback
        assertPendingTypeError(everyMethod.call());

        // Edge case: every on non-array (generic, works on any object via ToObject)
        JSValue nonArray = new JSString("ab");
        JSFunction isStringFn = createTestFunction(a -> a[0] instanceof JSString ? JSBoolean.TRUE : JSBoolean.FALSE);
        assertThat(callBuiltin(ArrayPrototype::every, nonArray, isStringFn)).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testEveryGeneric() {
        // Generic: called on plain object with length
        assertBooleanWithJavet("Array.prototype.every.call({0: 2, 1: 4, length: 2}, v => v % 2 === 0)",
                "Array.prototype.every.call({0: 1, 1: 2, length: 2}, v => v > 0)",
                // Generic: not all pass on plain object
                "Array.prototype.every.call({0: 2, 1: 3, length: 2}, v => v % 2 === 0)",
                // Generic: called on string primitive (ToObject wraps to String object)
                "Array.prototype.every.call('abc', v => typeof v === 'string')",
                "Array.prototype.every.call('', v => false)",
                // Generic: called on boolean primitive
                "Array.prototype.every.call(true, v => false)",
                // Generic: called on number primitive
                "Array.prototype.every.call(42, v => false)",
                // Generic: callback receives correct arguments (element, index, object)
                "Array.prototype.every.call({0: 'a', length: 1}, function(v, i, o) { return typeof o === 'object' && o.length === 1; })",
                // Generic: callback thisArg is respected
                "Array.prototype.every.call({0: 1, length: 1}, function(v) { return this.x === 10; }, {x: 10})",
                // Generic: empty object (length 0) is vacuously true
                "Array.prototype.every.call({length: 0}, v => false)",
                // Generic: object with no length property (length coerces to 0)
                "Array.prototype.every.call({}, v => false)");
        // Generic: Math object
        assertStringWithJavet(
                "(() => { Math.length = 1; Math[0] = 1; var r = Array.prototype.every.call(Math, function(v, i, o) { return '[object Math]' !== Object.prototype.toString.call(o); }); delete Math[0]; delete Math.length; return String(r); })()");
        // Error: null/undefined throws TypeError
        assertErrorWithJavet("Array.prototype.every.call(null, v => true)",
                "Array.prototype.every.call(undefined, v => true)");
    }

    @Test
    public void testFill() {
        // Normal case: fill entire array
        JSArray arr = nativeArray(1, 2, 3, 4);

        var fillMethod = nativeMethod(ArrayPrototype::fill);
        assertThat(fillMethod.call(arr, 0)).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(4);
        assertNumberElements(arr, 0, 0.0, 0.0, 0.0, 0.0);

        // Fill with start index
        JSArray arr2 = nativeArray(1, 2, 3, 4);

        assertThat(fillMethod.call(arr2, 0, 2)).isSameAs(arr2);
        assertThat(arr2.getLength()).isEqualTo(4);
        assertNumberElements(arr2, 0, 1.0, 2.0, 0.0, 0.0);

        // Fill with start and end
        JSArray arr3 = nativeArray(1, 2, 3, 4);

        assertThat(fillMethod.call(arr3, 0, 1, 3)).isSameAs(arr3);
        assertThat(arr3.getLength()).isEqualTo(4);
        assertNumberElements(arr3, 0, 1.0, 0.0, 0.0, 4.0);

        // With negative indices
        JSArray arr4 = nativeArray(1, 2, 3, 4);

        assertThat(fillMethod.call(arr4, 0, -3, -1)).isSameAs(arr4);
        assertThat(arr4.getLength()).isEqualTo(4);
        assertNumberElements(arr4, 0, 1.0, 0.0, 0.0, 4.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(fillMethod.call(emptyArr, 1)).isSameAs(emptyArr);

        // Edge case: fill on null/undefined should throw TypeError
        assertPendingTypeError(fillMethod.call(JSNull.INSTANCE, 0));
    }

    @Test
    public void testFillGeneric() {
        // fill is generic — works on non-array objects and primitives via ToObject
        assertStringWithJavet(
                // fill on boolean primitive returns Boolean wrapper object
                "(() => (Array.prototype.fill.call(true) instanceof Boolean).toString())()",
                // fill on an array-like object
                "(() => { var obj = {0: 'a', 1: 'b', 2: 'c', length: 3}; Array.prototype.fill.call(obj, 'x'); return obj[0] + obj[1] + obj[2]; })()",
                // fill with start/end on array-like
                "(() => { var obj = {0: 'a', 1: 'b', 2: 'c', length: 3}; Array.prototype.fill.call(obj, 'x', 1, 2); return obj[0] + obj[1] + obj[2]; })()");
        // fill on null/undefined should throw TypeError with matching error message
        assertErrorWithJavet("Array.prototype.fill.call(null, 0)", "Array.prototype.fill.call(undefined, 0)");
    }

    @Test
    public void testFilter() {
        JSArray arr = nativeArray(1, 2, 3, 4);

        // Normal case: filter even numbers
        JSFunction evenFn = evenPredicate();

        var filterMethod = builtin(ArrayPrototype::filter, arr);
        JSValue result = filterMethod.call(evenFn);
        JSArray filtered = result.asArray().orElseThrow();
        assertThat(filtered.getLength()).isEqualTo(2);
        assertNumberElements(filtered, 0, 2.0, 4.0);

        // Edge case: no elements pass filter
        JSFunction noneFn = createTestFunction(args -> JSBoolean.FALSE);
        result = filterMethod.call(noneFn);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(0);

        // Edge case: all elements pass filter
        JSFunction allFn = createTestFunction(args -> JSBoolean.TRUE);
        result = filterMethod.call(allFn);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(4);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        result = callBuiltin(ArrayPrototype::filter, emptyArr, evenFn);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(0);

        // Edge case: no callback
        assertPendingTypeError(filterMethod.call());

        // Edge case: filter on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::filter, JSNull.INSTANCE, evenFn));
        assertPendingTypeError(callFunction(ArrayPrototype::filter, evenFn));

        assertStringWithJavet(
                "(() => { var obj = {0: 1, 1: 2, 2: 3, length: 3}; return Array.prototype.filter.call(obj, x => x > 1).join(','); })()",
                "(() => { var obj = {0: 'a', 1: 'b', 2: 'c', length: 3}; return Array.prototype.filter.call(obj, (v, i) => i > 0).join(','); })()",
                "(() => { return Array.prototype.filter.call({length: 0}, x => true).length.toString(); })()");
        assertErrorWithJavet("Array.prototype.filter.call(null, x => x)",
                "Array.prototype.filter.call(undefined, x => x)");
    }

    @Test
    public void testFind() {
        assertFind(ArrayPrototype::find, 2, JSUndefined.INSTANCE);
    }

    @Test
    public void testFindIndex() {
        assertFind(ArrayPrototype::findIndex, 1, JSNumber.of(-1));
    }

    @Test
    public void testFindLast() {
        assertFind(ArrayPrototype::findLast, 4, JSUndefined.INSTANCE);
    }

    @Test
    public void testFindLastIndex() {
        assertFind(ArrayPrototype::findLastIndex, 3, JSNumber.of(-1));
    }

    @Test
    public void testFlat() {
        // Create nested arrays
        JSArray nested = nativeArray(1, 2);

        JSArray arr2 = nativeArray(3);
        arr2.push(nested);
        arr2.push(new JSNumber(4));

        JSArray arr = nativeArray(0);
        arr.push(arr2);
        arr.push(new JSNumber(5));

        // Normal case: flat()
        var flatMethod = builtin(ArrayPrototype::flat, arr);
        JSValue result = flatMethod.call();
        JSArray flattened = result.asArray().orElseThrow();
        assertThat(flattened.getLength()).isEqualTo(5);
        assertNumberElements(flattened, 0, 0.0, 3.0);
        assertNumberElements(flattened.get(2).asArray().orElseThrow(), 0, 1.0, 2.0);
        assertNumberElements(flattened, 3, 4.0, 5.0);

        // With depth
        result = flatMethod.call(0);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(3); // No flattening

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(callBuiltin(ArrayPrototype::flat, emptyArr).asArray().orElseThrow().getLength()).isEqualTo(0);

        // Edge case: flat on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::flat, JSNull.INSTANCE));
    }

    @Test
    public void testFlatMap() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case: flatMap with function that returns arrays
        JSFunction doubleAndWrapFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            JSArray result = nativeArray(val);
            result.push(new JSNumber(val * 2));
            return result;
        });

        var flatMapMethod = builtin(ArrayPrototype::flatMap, arr);
        JSValue result = flatMapMethod.call(doubleAndWrapFn);
        JSArray flattened = result.asArray().orElseThrow();
        assertThat(flattened.getLength()).isEqualTo(6);
        assertNumberElements(flattened, 0, 1.0, 2.0, 2.0, 4.0, 3.0, 6.0);

        // Case where callback returns non-array
        JSFunction identityFn = createTestFunction(args -> args[0]);
        result = flatMapMethod.call(identityFn);
        flattened = result.asArray().orElseThrow();
        assertThat(flattened.getLength()).isEqualTo(3);
        assertNumberElements(flattened, 0, 1.0, 2.0, 3.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        result = callBuiltin(ArrayPrototype::flatMap, emptyArr, doubleAndWrapFn);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(0);

        // Edge case: flatMap on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::flatMap, JSNull.INSTANCE, doubleAndWrapFn));

        // Edge case: no callback
        assertPendingTypeError(flatMapMethod.call());
    }

    @Test
    public void testForEach() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case
        final int[] count = {0};
        final double[] sum = {0};
        JSFunction collectFn = createTestFunction(args -> {
            count[0]++;
            sum[0] += args[0].asNumber().map(JSNumber::value).orElseThrow();
            return JSUndefined.INSTANCE;
        });

        var forEachMethod = builtin(ArrayPrototype::forEach, arr);
        assertThat(forEachMethod.call(collectFn).isUndefined()).isTrue();
        assertThat(count[0]).isEqualTo(3);
        assertThat(sum[0]).isEqualTo(6.0);

        // With thisArg
        JSValue thisArg = new JSObject(context);
        assertThat(forEachMethod.call(collectFn, thisArg).isUndefined()).isTrue();

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(callBuiltin(ArrayPrototype::forEach, emptyArr, collectFn).isUndefined()).isTrue();

        // Edge case: no callback
        assertPendingTypeError(forEachMethod.call());

        // Edge case: forEach on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::forEach, JSNull.INSTANCE, collectFn));
    }

    @Test
    public void testGetLength() {
        // Normal case: non-empty array
        JSArray arr = nativeArray(1, 2, 3);

        BuiltinAssertions getLength = builtin(ArrayPrototype::getLength, arr);
        getLength.expectNumber(3.0);

        // Empty array
        JSArray emptyArr = new JSArray(context);
        var getLengthMethod = nativeMethod(ArrayPrototype::getLength);
        assertNumberValue(getLengthMethod.call(emptyArr), 0.0);

        // Array with specific length
        JSArray arrWithLength = new JSArray(context, 10);
        assertNumberValue(getLengthMethod.call(arrWithLength), 10.0);

        // After modifying array
        arr.push(new JSNumber(4));
        getLength.expectNumber(4.0);

        arr.pop();
        getLength.expectNumber(3.0);

        // Edge case: getLength on non-array
        JSValue nonArray = new JSString("not an array");
        assertThat(getLengthMethod.call(nonArray).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        assertIntegerWithJavet("[].length", "[1,2].length", "['a','b'].length", "[[],].length", "[[1],[2]].length");

        assertErrorWithJavet("Array.prototype.length.call({})", "Array.prototype.length.call(123)",
                "Array.prototype.length.call(true)", "Array['prototype'].length.call(true)",
                "Array.prototype.length.call(null)", "Array.prototype.length.call(undefined)");
    }

    @Test
    public void testGetSymbolUnscopables() {
        // Normal case: get unscopables
        JSArray arr = new JSArray(context);
        JSValue result = ArrayPrototype.createUnscopablesObject(context);

        assertThat(result.isObject()).isTrue();
        JSObject unscopables = result.asObject().orElseThrow();

        // Verify ES2015 methods are marked as unscopable
        for (String name : new String[]{"copyWithin", "entries", "fill", "find", "findIndex", "flat", "flatMap",
                "includes", "keys", "values"}) {
            assertThat(unscopables.get(name)).as(name).isEqualTo(JSBoolean.TRUE);
        }

        // Verify ES2022+ methods are marked as unscopable
        for (String name : new String[]{"at", "findLast", "findLastIndex", "toReversed", "toSorted", "toSpliced"}) {
            assertThat(unscopables.get(name)).as(name).isEqualTo(JSBoolean.TRUE);
        }

        // Verify old methods are NOT in unscopables (they should return undefined)
        for (String name : new String[]{"push", "pop", "shift", "unshift", "slice", "splice", "concat", "join",
                "reverse", "sort", "indexOf", "lastIndexOf"}) {
            assertThat(unscopables.get(name).isUndefined()).as(name).isTrue();
        }
    }

    @Test
    public void testIncludes() {
        JSArray arr = new JSArray(context, new JSNumber(1), new JSNumber(2), JSUndefined.INSTANCE, new JSNumber(4));

        // Normal case
        BuiltinAssertions includes = builtin(ArrayPrototype::includes, arr);
        includes.expectValue(JSBoolean.TRUE, 2);

        // Not found
        includes.expectValue(JSBoolean.FALSE, 3);

        // With fromIndex
        includes.expectValue(JSBoolean.FALSE, 2, 2);

        // Negative fromIndex
        includes.expectValue(JSBoolean.TRUE, 2, -3);

        // Edge case: includes undefined
        includes.expectValue(JSBoolean.TRUE, JSUndefined.INSTANCE);

        // Edge case: includes NaN (special case)
        JSArray nanArr = nativeArray(Double.NaN);
        var includesMethod = nativeMethod(ArrayPrototype::includes);
        assertThat(includesMethod.call(nanArr, Double.NaN)).isEqualTo(JSBoolean.TRUE);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(includesMethod.call(emptyArr, 1)).isEqualTo(JSBoolean.FALSE);

        // Edge case: no search element (searches for undefined, which is in arr)
        includes.expectValue(JSBoolean.TRUE);

        // Edge case: includes on null/undefined
        assertPendingTypeError(includesMethod.call(JSNull.INSTANCE, 1));
    }

    @Test
    public void testIndexOf() {
        JSArray arr = nativeArray(1, 2, 3, 2);

        // Normal case
        BuiltinAssertions indexOf = builtin(ArrayPrototype::indexOf, arr);
        indexOf.expectNumber(1.0, 2);

        // With fromIndex
        indexOf.expectNumber(3.0, 2, 2);

        // Not found
        indexOf.expectNumber(-1.0, 4);

        // Negative fromIndex
        indexOf.expectNumber(3.0, 2, -2);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        var indexOfMethod = nativeMethod(ArrayPrototype::indexOf);
        JSValue result = indexOfMethod.call(emptyArr, 1);
        assertNumberValue(result, -1.0);

        // Edge case: no search element
        result = indexOfMethod.call(arr);
        assertThat(((JSNumber) result).value()).isEqualTo(-1.0);

        // Edge case: indexOf on null/undefined
        assertPendingTypeError(indexOfMethod.call(JSNull.INSTANCE, 1));
    }

    @Test
    public void testJoin() {
        JSArray arr = new JSArray(context, new JSString("a"), new JSString("b"), new JSString("c"));

        // Normal case
        BuiltinAssertions join = builtin(ArrayPrototype::join, arr);
        join.expectString("a-b-c", "-");

        // Default separator
        join.expectString("a,b,c");

        // Empty separator
        join.expectString("abc", "");

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        var joinMethod = nativeMethod(ArrayPrototype::join);
        assertStringValue(joinMethod.call(emptyArr), "");

        // Edge case: array with null/undefined
        JSArray mixedArr = new JSArray(context, new JSString("a"), JSNull.INSTANCE, JSUndefined.INSTANCE,
                new JSString("b"));
        assertStringValue(joinMethod.call(mixedArr), "a,,,b");

        // Edge case: join on a primitive, which ToObject makes an array-like of its characters (as in V8)
        JSValue nonArray = new JSString("not an array");
        assertStringValue(joinMethod.call(nonArray), "n,o,t, ,a,n, ,a,r,r,a,y");

        // Edge case: join called on array-like object
        assertStringValue(
                context.eval(
                        "Array.prototype.join.call({ 0: 'a', 1: null, 2: undefined, 3: 'd', 4: 'e', length: 5 }, ',')"),
                "a,,,d,e");
        assertStringValue(
                context.eval("Array.prototype.join.call({ 0: 'a', 1: null, 2: undefined, 3: 'd', 4: 'e', length: 5 })"),
                "a,,,d,e");
        assertThat(context.eval("Array.prototype.join.call({ 0: 'a', 0.1: 'b', length: 5 }, '-')").asString()
                .map(JSString::value).orElseThrow()).isEqualTo("a----");
    }

    @Test
    public void testLastIndexOf() {
        JSArray arr = nativeArray(1, 2, 3, 2);

        // Normal case
        BuiltinAssertions lastIndexOf = builtin(ArrayPrototype::lastIndexOf, arr);
        lastIndexOf.expectNumber(3.0, 2);

        // With fromIndex
        lastIndexOf.expectNumber(1.0, 2, 2);

        // Not found
        lastIndexOf.expectNumber(-1.0, 4);

        // Negative fromIndex
        lastIndexOf.expectNumber(3.0, 2, -1);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        var lastIndexOfMethod = nativeMethod(ArrayPrototype::lastIndexOf);
        JSValue result = lastIndexOfMethod.call(emptyArr, 1);
        assertNumberValue(result, -1.0);

        // Edge case: no search element
        result = lastIndexOfMethod.call(arr);
        assertThat(((JSNumber) result).value()).isEqualTo(-1.0);

        // Edge case: lastIndexOf on null/undefined
        assertPendingTypeError(lastIndexOfMethod.call(JSNull.INSTANCE, 1));
    }

    @Test
    public void testMap() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case: double each element
        JSFunction doubleFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(val * 2);
        });

        var mapMethod = builtin(ArrayPrototype::map, arr);
        JSValue result = mapMethod.call(doubleFn);
        JSArray mapped = result.asArray().orElseThrow();
        assertThat(mapped.getLength()).isEqualTo(3);
        assertNumberElements(mapped, 0, 2.0, 4.0, 6.0);

        // With thisArg
        JSValue thisArg = new JSObject(context);
        result = mapMethod.call(doubleFn, thisArg);
        assertThat(result.isArray()).isTrue();

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        result = callBuiltin(ArrayPrototype::map, emptyArr, doubleFn);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(0);

        // Edge case: no callback
        assertPendingTypeError(mapMethod.call());

        // Edge case: non-function callback
        assertPendingTypeError(mapMethod.call("not a function"));

        // Edge case: map on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::map, JSNull.INSTANCE, doubleFn));
    }

    @Test
    public void testPop() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case
        BuiltinAssertions pop = builtin(ArrayPrototype::pop, arr);
        pop.expectNumber(3.0);
        assertThat(arr.getLength()).isEqualTo(2);

        pop.expectNumber(2.0);
        assertThat(arr.getLength()).isEqualTo(1);

        // Pop from empty array
        pop.expectNumber(1.0);
        assertThat(arr.getLength()).isEqualTo(0);

        var popMethod = nativeMethod(ArrayPrototype::pop);
        assertThat(popMethod.call(arr).isUndefined()).isTrue();

        // Edge case: pop from null/undefined
        assertPendingTypeError(popMethod.call(JSNull.INSTANCE));
    }

    @Test
    public void testPrototype() {
        assertObjectWithJavet("Object.getOwnPropertyNames(Array.prototype).sort()");
    }

    @Test
    public void testPush() {
        // Normal case
        JSArray arr = new JSArray(context);
        var pushMethod = builtin(ArrayPrototype::push, arr);
        assertNumberValue(pushMethod.call(1, 2), 2);
        assertThat(arr.getLength()).isEqualTo(2);
        assertNumberElements(arr, 0, 1.0, 2.0);

        // Push more
        assertNumberValue(pushMethod.call(3), 3);
        assertThat(arr.getLength()).isEqualTo(3);

        // Edge case: push to null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::push, JSNull.INSTANCE, 1));
    }

    @Test
    public void testReduce() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case: sum
        JSFunction sumFn = createTestFunction(args -> {
            double acc = args[0].asNumber().map(JSNumber::value).orElseThrow();
            double curr = args[1].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(acc + curr);
        });

        var reduceMethod = builtin(ArrayPrototype::reduce, arr);
        assertNumberValue(reduceMethod.call(sumFn), 6.0);

        // With initial value
        assertNumberValue(reduceMethod.call(sumFn, 10), 16.0);

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray(context);
        assertPendingTypeError(callBuiltin(ArrayPrototype::reduce, emptyArr, sumFn));

        // Edge case: empty array with initial value
        assertNumberValue(callBuiltin(ArrayPrototype::reduce, emptyArr, sumFn, 42), 42.0);

        // Edge case: single element without initial value
        JSArray singleArr = nativeArray(5);
        assertNumberValue(callBuiltin(ArrayPrototype::reduce, singleArr, sumFn), 5.0);

        // Edge case: no callback
        assertPendingTypeError(reduceMethod.call());

        // Edge case: reduce on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::reduce, JSNull.INSTANCE, sumFn));
    }

    @Test
    public void testReduceRight() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case: concatenate strings in reverse
        JSFunction concatFn = createTestFunction(args -> {
            String acc = args[0].asString().map(JSString::value).orElseThrow();
            String curr = args[1].asString().map(JSString::value).orElseThrow();
            return new JSString(acc + curr);
        });

        JSArray strArr = new JSArray(context, new JSString("a"), new JSString("b"), new JSString("c"));

        var reduceRightMethod = builtin(ArrayPrototype::reduceRight, strArr);
        assertStringValue(reduceRightMethod.call(concatFn), "cba");

        // With initial value
        assertStringValue(reduceRightMethod.call(concatFn, "d"), "dcba");

        // Edge case: empty array without initial value
        JSArray emptyArr = new JSArray(context);
        assertPendingTypeError(callBuiltin(ArrayPrototype::reduceRight, emptyArr, concatFn));

        // Edge case: empty array with initial value
        assertStringValue(callBuiltin(ArrayPrototype::reduceRight, emptyArr, concatFn, "x"), "x");

        // Edge case: single element without initial value
        JSArray singleArr = new JSArray(context, new JSString("z"));
        assertStringValue(callBuiltin(ArrayPrototype::reduceRight, singleArr, concatFn), "z");

        // Edge case: no callback
        assertPendingTypeError(reduceRightMethod.call());

        // Edge case: reduceRight on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::reduceRight, JSNull.INSTANCE, concatFn));
    }

    @Test
    public void testReverse() {
        JSArray arr = nativeArray(1, 2, 3, 4);

        // Normal case
        var reverseMethod = nativeMethod(ArrayPrototype::reverse);
        assertThat(reverseMethod.call(arr)).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(4);
        assertNumberElements(arr, 0, 4.0, 3.0, 2.0, 1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(reverseMethod.call(emptyArr)).isSameAs(emptyArr);

        // Edge case: single element
        JSArray singleArr = nativeArray(42);
        assertThat(reverseMethod.call(singleArr)).isSameAs(singleArr);
        assertNumberValue(singleArr.get(0), 42.0);

        // Edge case: reverse on null/undefined
        assertPendingTypeError(reverseMethod.call(JSNull.INSTANCE));
    }

    @Test
    public void testShift() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case
        var shiftMethod = nativeMethod(ArrayPrototype::shift);
        assertNumberValue(shiftMethod.call(arr), 1.0);
        assertThat(arr.getLength()).isEqualTo(2);
        assertNumberElements(arr, 0, 2.0, 3.0);

        // Edge case: shift from empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(shiftMethod.call(emptyArr).isUndefined()).isTrue();

        // Edge case: shift from null/undefined
        assertPendingTypeError(shiftMethod.call(JSNull.INSTANCE));
    }

    @Test
    public void testSlice() {
        JSArray arr = nativeArray(0, 1, 2, 3, 4);

        // Normal case: slice(1, 4)
        var sliceMethod = builtin(ArrayPrototype::slice, arr);
        JSValue result = sliceMethod.call(1, 4);
        JSArray sliced = result.asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(3);
        assertNumberElements(sliced, 0, 1.0, 2.0, 3.0);

        // Slice from start
        sliced = sliceMethod.call(2).asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(3);
        assertNumberValue(sliced.get(0), 2.0);

        // Slice with negative indices
        sliced = sliceMethod.call(-2).asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(2);
        assertNumberValue(sliced.get(0), 3.0);

        // Edge case: empty slice
        sliced = sliceMethod.call(2, 2).asArray().orElseThrow();
        assertThat(sliced.getLength()).isEqualTo(0);

        // Edge case: slice on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::slice, JSNull.INSTANCE));
    }

    @Test
    public void testSome() {
        JSArray arr = nativeArray(1, 3, 5);

        // Normal case: some odd
        JSFunction isOddFn = createTestFunction(args -> {
            double val = args[0].asNumber().map(JSNumber::value).orElseThrow();
            return (val % 2 != 0) ? JSBoolean.TRUE : JSBoolean.FALSE;
        });

        var someMethod = builtin(ArrayPrototype::some, arr);
        assertThat(someMethod.call(isOddFn)).isEqualTo(JSBoolean.TRUE);

        // None pass
        JSArray arr2 = nativeArray(2, 4, 6);

        assertThat(callBuiltin(ArrayPrototype::some, arr2, isOddFn)).isEqualTo(JSBoolean.FALSE);

        // Edge case: empty array (vacuously false)
        JSArray emptyArr = new JSArray(context);
        assertThat(callBuiltin(ArrayPrototype::some, emptyArr, isOddFn)).isEqualTo(JSBoolean.FALSE);

        // Edge case: no callback
        assertPendingTypeError(someMethod.call());

        // Edge case: some on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::some, JSNull.INSTANCE, isOddFn));
    }

    @Test
    public void testSort() {
        JSArray arr = nativeArray(3, 1, 4, 1, 5);

        // Normal case: default sort (string comparison)
        var sortMethod = nativeMethod(ArrayPrototype::sort);
        assertThat(sortMethod.call(arr)).isSameAs(arr);
        assertThat(arr.getLength()).isEqualTo(5);
        assertNumberElements(arr, 0, 1.0, 1.0, 3.0, 4.0, 5.0);

        // With compare function
        JSArray arr2 = nativeArray(3, 1, 4);

        JSFunction descCompare = createTestFunction(args -> {
            double a = args[0].asNumber().map(JSNumber::value).orElseThrow();
            double b = args[1].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(b - a);
        });

        assertThat(sortMethod.call(arr2, descCompare)).isSameAs(arr2);
        assertThat(arr2.getLength()).isEqualTo(3);
        assertNumberElements(arr2, 0, 4.0, 3.0, 1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(sortMethod.call(emptyArr)).isSameAs(emptyArr);

        // Edge case: single element
        JSArray singleArr = nativeArray(42);
        assertThat(sortMethod.call(singleArr)).isSameAs(singleArr);
        assertNumberValue(singleArr.get(0), 42.0);

        // Edge case: sort on null/undefined
        assertPendingTypeError(sortMethod.call(JSNull.INSTANCE));

        assertObjectWithJavet("[].sort()", "[1.1,2.1].sort()", "['b','a'].sort()", "['b','a'].sort(undefined)",
                "['a',null,undefined,[],'b'].sort()");
        assertErrorWithJavet("[1].sort(null)", "[1].sort(true)", "[1].sort(1)", "[1].sort('abc')");
    }

    @Test
    public void testSpeciesCopyObservesHasBeforeGet() {
        assertStringWithJavet("""
                (() => {
                    const log = [];
                    const source = new Proxy({length: 3, 0: undefined, 2: 4}, {
                        has(target, key) { log.push('has ' + key); return Reflect.has(target, key); },
                        get(target, key) { log.push('get ' + key); return Reflect.get(target, key); }
                    });
                    const copy = Array.prototype.slice.call(source);
                    return JSON.stringify([log, Object.keys(copy), copy]);
                })()""");
    }

    @Test
    public void testSplice() {
        JSArray arr = nativeArray(0, 1, 2, 3, 4);

        // Normal case: splice(2, 2, 10, 11)
        var spliceMethod = nativeMethod(ArrayPrototype::splice);
        JSValue result = spliceMethod.call(arr, 2, 2, 10, 11);
        JSArray deleted = result.asArray().orElseThrow();
        assertThat(deleted.getLength()).isEqualTo(2);
        assertNumberElements(deleted, 0, 2.0, 3.0);

        // Check modified array
        assertThat(arr.getLength()).isEqualTo(5);
        assertNumberElements(arr, 0, 0.0, 1.0, 10.0, 11.0, 4.0);

        // Edge case: splice with no deletions
        JSArray arr2 = nativeArray(1, 2);
        deleted = spliceMethod.call(arr2, 1, 0, 1.5).asArray().orElseThrow();
        assertThat(deleted.getLength()).isEqualTo(0);
        assertThat(arr2.getLength()).isEqualTo(3);

        // Edge case: splice on null/undefined
        assertPendingTypeError(spliceMethod.call(JSNull.INSTANCE, 0, 1));
    }

    @Test
    public void testSymbolIterator() {
        JSArray arr = nativeArray(1, 2, 3);

        context.transferPrototype(arr, JSArray.NAME);

        // Get Symbol.iterator
        PropertyKey iteratorKey = PropertyKey.SYMBOL_ITERATOR;
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
        JSValue iteratorResult = ((JSFunction) iteratorFn).call(context, arr, JSValue.NO_ARGS);
        assertThat(iteratorResult.isIterator()).isTrue();

        // Test iteration
        JSIterator iterator = (JSIterator) iteratorResult;
        JSObject result1 = iterator.next();
        assertThat(result1.get("done")).isSameAs(JSBoolean.FALSE);
        assertNumberValue(result1.get("value"), 1.0);

        JSObject result2 = iterator.next();
        assertThat(result2.get("done")).isSameAs(JSBoolean.FALSE);
        assertNumberValue(result2.get("value"), 2.0);

        JSObject result3 = iterator.next();
        assertThat(result3.get("done")).isSameAs(JSBoolean.FALSE);
        assertNumberValue(result3.get("value"), 3.0);

        JSObject result4 = iterator.next();
        assertThat(result4.get("done")).isSameAs(JSBoolean.TRUE);
        assertThat(result4.get("value").isUndefined()).isTrue();
    }

    @Test
    public void testToLocaleString() {
        // Normal case
        JSArray arr = new JSArray(context, new JSString("a"), new JSString("b"), new JSString("c"));

        var toLocaleStringMethod = nativeMethod(ArrayPrototype::toLocaleString);
        assertStringValue(toLocaleStringMethod.call(arr), "a,b,c");

        // With numbers
        JSArray arr2 = nativeArray(1, 2, 3);

        assertStringValue(toLocaleStringMethod.call(arr2), "1,2,3");

        // With null and undefined
        JSArray arr3 = new JSArray(context, new JSString("a"), JSNull.INSTANCE, JSUndefined.INSTANCE,
                new JSString("b"));

        assertStringValue(toLocaleStringMethod.call(arr3), "a,,,b");

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        assertThat(toLocaleStringMethod.call(emptyArr).asString().map(JSString::value).orElse("FAIL")).isEqualTo("");

        // Edge case: toLocaleString on null/undefined
        assertPendingTypeError(toLocaleStringMethod.call(JSNull.INSTANCE));
    }

    @Test
    public void testToReversed() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case
        var toReversedMethod = nativeMethod(ArrayPrototype::toReversed);
        JSValue result = toReversedMethod.call(arr);
        JSArray reversed = result.asArray().orElseThrow();
        assertThat(reversed.getLength()).isEqualTo(3);
        assertNumberElements(reversed, 0, 3.0, 2.0, 1.0);

        // Original array should be unchanged
        assertNumberElements(arr, 0, 1.0, 2.0, 3.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        result = toReversedMethod.call(emptyArr);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(0);

        // Edge case: single element
        JSArray singleArr = nativeArray(42);
        JSArray singleReversed = toReversedMethod.call(singleArr).asArray().orElseThrow();
        assertThat(singleReversed.getLength()).isEqualTo(1);
        assertNumberValue(singleReversed.get(0), 42.0);

        // Edge case: toReversed on null/undefined
        assertPendingTypeError(toReversedMethod.call(JSNull.INSTANCE));
    }

    @Test
    public void testToSorted() {
        JSArray arr = nativeArray(3, 1, 2);

        // Normal case: default sort (string comparison)
        var toSortedMethod = builtin(ArrayPrototype::toSorted, arr);
        JSValue result = toSortedMethod.call();
        JSArray sorted = result.asArray().orElseThrow();
        assertThat(sorted.getLength()).isEqualTo(3);
        assertNumberElements(sorted, 0, 1.0, 2.0, 3.0);

        // Original array should be unchanged
        assertNumberElements(arr, 0, 3.0, 1.0, 2.0);

        // With custom compare function
        JSFunction compareFn = createTestFunction(args -> {
            double a = args[0].asNumber().map(JSNumber::value).orElseThrow();
            double b = args[1].asNumber().map(JSNumber::value).orElseThrow();
            return new JSNumber(b - a); // descending order
        });

        result = toSortedMethod.call(compareFn);
        JSArray sortedDesc = result.asArray().orElseThrow();
        assertThat(sortedDesc.getLength()).isEqualTo(3);
        assertNumberElements(sortedDesc, 0, 3.0, 2.0, 1.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        result = callBuiltin(ArrayPrototype::toSorted, emptyArr);
        assertThat(result.asArray().orElseThrow().getLength()).isEqualTo(0);

        // Edge case: single element
        JSArray singleArr = nativeArray(42);
        result = callBuiltin(ArrayPrototype::toSorted, singleArr);
        JSArray singleSorted = result.asArray().orElseThrow();
        assertThat(singleSorted.getLength()).isEqualTo(1);
        assertNumberValue(singleSorted.get(0), 42.0);

        // Edge case: toSorted on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::toSorted, JSNull.INSTANCE));
        assertErrorWithJavet("[1].toSorted(null)", "[1].toSorted(true)", "[1].toSorted(1)", "[1].toSorted('abc')");
    }

    @Test
    public void testToSpliced() {
        JSArray arr = nativeArray(1, 2, 3, 4);

        // Normal case: remove 2 elements starting at index 1
        var toSplicedMethod = builtin(ArrayPrototype::toSpliced, arr);
        JSValue result = toSplicedMethod.call(1, 2);
        JSArray spliced = result.asArray().orElseThrow();
        assertThat(spliced.getLength()).isEqualTo(2);
        assertNumberElements(spliced, 0, 1.0, 4.0);

        // Original array should be unchanged
        assertThat(arr.getLength()).isEqualTo(4);
        assertNumberElements(arr, 0, 1.0, 2.0, 3.0, 4.0);

        // Insert elements
        JSArray inserted = toSplicedMethod.call(1, 0, 5, 6).asArray().orElseThrow();
        assertThat(inserted.getLength()).isEqualTo(6);
        assertNumberElements(inserted, 0, 1.0, 5.0, 6.0, 2.0, 3.0, 4.0);

        // Replace elements
        JSArray replaced = toSplicedMethod.call(1, 2, 7, 8).asArray().orElseThrow();
        assertThat(replaced.getLength()).isEqualTo(4);
        assertNumberElements(replaced, 0, 1.0, 7.0, 8.0, 4.0);

        // Negative start index
        result = toSplicedMethod.call(-2, 1);
        JSArray negStart = result.asArray().orElseThrow();
        assertThat(negStart.getLength()).isEqualTo(3);
        assertNumberElements(negStart, 0, 1.0, 2.0, 4.0);

        // Edge case: empty array
        JSArray emptyArr = new JSArray(context);
        JSArray emptySpliced = callBuiltin(ArrayPrototype::toSpliced, emptyArr, 0, 0, 1).asArray().orElseThrow();
        assertThat(emptySpliced.getLength()).isEqualTo(1);
        assertNumberValue(emptySpliced.get(0), 1.0);

        // Edge case: toSpliced on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::toSpliced, JSNull.INSTANCE, 0, 1));
    }

    @Test
    public void testToSplicedCreatesDenseOwnPropertiesInGetOrder() {
        assertBooleanWithJavet("""
                (() => {
                  const reads = [];
                  const source = {
                    length: 4,
                    get 0() { reads.push(0); return 'a'; },
                    get 1() { reads.push(1); return undefined; },
                    get 3() { reads.push(3); return 'd'; }
                  };
                  const result = Array.prototype.toSpliced.call(source, 2, 1, 'x');
                  return reads.join(',') === '0,1,3'
                    && result.length === 4
                    && result[0] === 'a'
                    && Object.hasOwn(result, 1)
                    && result[1] === undefined
                    && result[2] === 'x'
                    && result[3] === 'd';
                })()""");
    }

    @Test
    public void testToString() {
        JSArray jsArray = context.createJSArray();
        jsArray.push(new JSString("a"));
        jsArray.push(new JSNumber(1));
        jsArray.push(new JSString("c"));

        // Normal case
        var toStringMethod = nativeMethod(ArrayPrototype::toString);
        assertStringValue(toStringMethod.call(jsArray), "a,1,c");

        // An array with no join method — here one built without Array.prototype — is not joined
        JSArray withoutPrototype = new JSArray(context, new JSString("a"));
        assertThat(toStringMethod.call(withoutPrototype).asString().map(JSString::value).orElseThrow())
                .isEqualTo("[object Array]");

        // Edge case: toString on string
        assertThat(toStringMethod.call("a").asString().map(JSString::value).orElseThrow()).isEqualTo("[object String]");

        // Edge case: toString on object
        assertThat(toStringMethod.call(new JSObject(context)).asString().map(JSString::value).orElseThrow())
                .isEqualTo("[object Object]");

        // Edge case: toString on null
        assertPendingTypeError(toStringMethod.call(new JSNull()), "Cannot convert undefined or null to object");

        // Edge case: toString on undefined
        assertPendingTypeError(toStringMethod.call(new JSUndefined()), "Cannot convert undefined or null to object");
    }

    @Test
    public void testUnshift() {
        JSArray arr = nativeArray(2, 3);

        // Normal case
        var unshiftMethod = builtin(ArrayPrototype::unshift, arr);
        assertNumberValue(unshiftMethod.call(1), 3.0);
        assertThat(arr.getLength()).isEqualTo(3);
        assertNumberElements(arr, 0, 1.0, 2.0, 3.0);

        // Unshift multiple
        assertNumberValue(unshiftMethod.call(-1, 0), 5.0);
        assertThat(arr.getLength()).isEqualTo(5);

        // Edge case: unshift to empty array
        JSArray emptyArr = new JSArray(context);
        assertNumberValue(callBuiltin(ArrayPrototype::unshift, emptyArr, 1), 1.0);
        assertThat(emptyArr.getLength()).isEqualTo(1);

        // Edge case: unshift to null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::unshift, JSNull.INSTANCE, 1));
    }

    @Test
    public void testWith() {
        JSArray arr = nativeArray(1, 2, 3);

        // Normal case: replace element at index 1
        var withMethod = builtin(ArrayPrototype::with, arr);
        JSValue result = withMethod.call(1, 42);
        JSArray withResult = result.asArray().orElseThrow();
        assertThat(withResult.getLength()).isEqualTo(3);
        assertNumberElements(withResult, 0, 1.0, 42.0, 3.0);

        // Original array should be unchanged
        assertNumberElements(arr, 0, 1.0, 2.0, 3.0);

        // Negative index
        JSArray negIndex = withMethod.call(-1, 99).asArray().orElseThrow();
        assertThat(negIndex.getLength()).isEqualTo(3);
        assertNumberElements(negIndex, 0, 1.0, 2.0, 99.0);

        // Edge case: index 0
        JSArray zeroIndex = withMethod.call(0, 100).asArray().orElseThrow();
        assertThat(zeroIndex.getLength()).isEqualTo(3);
        assertNumberElements(zeroIndex, 0, 100.0, 2.0, 3.0);

        // Edge case: out of bounds positive index
        assertPendingRangeError(withMethod.call(3, 1));

        // Edge case: out of bounds negative index
        assertPendingRangeError(withMethod.call(-4, 1));

        // Edge case: insufficient arguments
        assertPendingTypeError(withMethod.call(1));

        // Edge case: with on null/undefined
        assertPendingTypeError(callBuiltin(ArrayPrototype::with, JSNull.INSTANCE, 0, 1));
    }
}
