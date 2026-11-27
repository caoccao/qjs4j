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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Array elements are stored densely at any size, and unboxed while they are all numbers.
 * <p>
 * Dense storage stopped at 10,000 elements for every array, so every later element went into a
 * {@code HashMap<Integer, JSValue>}, and every element was a boxed {@link JSNumber}: an array of 3e7 numbers needed
 * more than 1 GB. The script cases exercise each storage transition against V8; the unit cases pin the storage the
 * engine chose.
 */
public class JSArrayStorageTest extends BaseJavetTest {
    @Test
    public void testElementsBeyondTenThousandStayDense() throws ReflectiveOperationException {
        JSValue pushed = context.eval("var a = []; for (var i = 0; i < 50000; i++) a.push(i * 0.5); a");
        assertThat(storageOf(pushed, "doubleArray")).isInstanceOfSatisfying(double[].class,
                doubles -> assertThat(doubles.length).isGreaterThanOrEqualTo(50000));
        assertThat(sparseOf(pushed)).isNull();
        JSValue filled = context.eval("var b = []; for (var i = 0; i < 30000; i++) b[i] = 'v' + i; b");
        assertThat(storageOf(filled, "denseArray")).isInstanceOf(JSValue[].class);
        assertThat(storageOf(filled, "doubleArray")).isNull();
        assertThat(sparseOf(filled)).isNull();
        // An isolated write far past the end is still sparse, and moves into dense storage once it is reached.
        JSValue far = context.eval("var c = [1, 2]; c[1000000] = 3; c");
        assertThat(sparseOf(far)).isNotNull();
        JSValue reached = context
                .eval("var d = []; d[20000] = 'far'; for (var i = 0; i < 20001; i++) d[i] = d[i] || i; d");
        assertThat(sparseOf(reached)).isNull();
        assertEvalText("d[20000] + ':' + d.length + ':' + d[19999]", "far:20001:19999");
    }

    @Test
    public void testShiftAndPopFromJava() {
        JSArray doubles = (JSArray) context.eval("[1.5, 2.5, 3.5]");
        assertThat(doubles.shift().toJavaObject()).isEqualTo(1.5);
        assertThat(doubles.pop().toJavaObject()).isEqualTo(3.5);
        assertThat(doubles.getLength()).isEqualTo(1);
        JSArray boxed = (JSArray) context.eval("['a', 'b', 'c']");
        assertThat(boxed.shift().toJavaObject()).isEqualTo("a");
        assertThat(boxed.hasElement(1)).isTrue();
        assertThat(boxed.hasElement(5)).isFalse();
        // A sparse element is popped from sparse storage, and shifted down with its index.
        JSArray popped = (JSArray) context.eval("var s = [1, 2]; s[1000000] = 'far'; s");
        assertThat(popped.pop().toJavaObject()).isEqualTo("far");
        context.eval("var t = [1, 2]; t[1000000] = 'far'; var u = []; u[1000000] = 'only'");
        ((JSArray) context.getGlobalObject().get(PropertyKey.fromString("t"))).shift();
        ((JSArray) context.getGlobalObject().get(PropertyKey.fromString("u"))).shift();
        assertEvalText("[t[999999], t.length, u[999999], u.length].join()", "far,1000000,only,1000000");
        JSArray single = (JSArray) context.eval("['only']");
        assertThat(single.shift().toJavaObject()).isEqualTo("only");
        assertThat(single.getLength()).isZero();
        JSArray empty = context.createJSArray();
        assertThat(empty.shift().isUndefined()).isTrue();
        assertThat(empty.pop().isUndefined()).isTrue();
    }

    @Test
    public void testStorageTransitionsMatchV8() {
        assertStringWithJavet("""
                var a = [1, 2.5, -0, NaN, Infinity];
                a.push('s');
                a.push({ k: 1 });
                JSON.stringify([a.length, Object.is(a[2], -0), Number.isNaN(a[3]), a[4], a[5], a[6]])""", """
                var a = [];
                for (var i = 0; i < 20000; i++) a.push(i);
                a[15000] = 'mixed';
                [a.length, a[14999], a[15000], a[19999], a.indexOf('mixed'), a.includes(19999)].join()""", """
                var a = new Array(25000);
                a[0] = 1; a[24999] = 2;
                [a.length, 1 in a, 5 in a, 24999 in a, Object.keys(a).join('|'), a.filter(() => true).length].join()""",
                """
                        var a = [];
                        for (var i = 0; i < 12000; i++) a.push(i);
                        delete a[11000];
                        a.length = 11500;
                        a.shift();
                        a.unshift(-1, -2);
                        [a.length, a[0], a[1], a[2], 10999 in a, a[10999], a[11000], a[a.length - 1]].join()""", """
                        var a = [];
                        for (var i = 0; i < 15000; i++) a.push(15000 - i);
                        a.sort((x, y) => x - y);
                        a.reverse();
                        a.splice(100, 5, 'x', 'y');
                        [a.length, a[0], a[99], a[100], a[101], a[102], a.at(-1)].join()""", """
                        var a = [0.1, 0.2];
                        a[5] = 0.3;
                        var holes = [];
                        for (var i = 0; i < a.length; i++) holes.push(i in a);
                        a.fill(7, 2);
                        JSON.stringify([holes, a, Array.from({ length: 3 }, (_, i) => i * 1.5)])""", """
                        var far = [];
                        Object.defineProperty(far, 1e6, { value: 1, writable: false });
                        var near = [1, 2];
                        Object.defineProperty(near, 0, { value: 9, writable: false });
                        [far.length, far[1e6], Object.getOwnPropertyDescriptor(far, 1e6).writable, near[0],
                          Object.getOwnPropertyDescriptor(near, 0).writable].join()""", """
                        var a = [NaN, 0 / 0, -(0 / 0)];
                        JSON.stringify([a.map(Number.isNaN), a.indexOf(NaN), a.includes(NaN), a.length])""");
    }

    private static Object sparseOf(JSValue array) throws ReflectiveOperationException {
        Field field = JSObject.class.getDeclaredField("sparseProperties");
        field.setAccessible(true);
        return field.get(array);
    }

    private static Object storageOf(JSValue array, String fieldName) throws ReflectiveOperationException {
        Field field = JSArray.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(array);
    }
}
