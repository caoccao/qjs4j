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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code [[Delete]]} and the {@code delete} operator are not the same thing. Only the operator
 * raises a {@code TypeError} when the property survives, and only when the <em>calling script</em>
 * is strict. Specification algorithms that call {@code [[Delete]]} internally must not inherit
 * that: {@code InternalizeJSONProperty} discards the result, so a strict caller made
 * {@code JSON.parse} throw where the specification says nothing happens.
 */
public class JSInternalDeleteTest extends BaseJavetTest {
    @Test
    void testDataViewAndTypedArrayAreNamedInDiagnostics() {
        // The failed-delete message names the object, and a typed array's Symbol.toStringTag is an
        // accessor on %TypedArray%.prototype — invisible to the physical-storage read the
        // diagnostic path uses — so it used to read "[object Object]".
        assertErrorWithJavet("""
                'use strict';
                var a = new Uint16Array(2);
                delete a[1];""");
    }

    @Test
    void testDeleteOperatorStillThrowsInStrictModeOnArray() {
        assertErrorWithJavet("""
                'use strict';
                var a = [1, 2];
                Object.defineProperty(a, '1', { configurable: false });
                delete a[1];""");
    }

    @Test
    void testDeleteOperatorStillThrowsInStrictModeOnObject() {
        assertErrorWithJavet("""
                'use strict';
                var o = { a: 1 };
                Object.defineProperty(o, 'a', { configurable: false });
                delete o.a;""");
    }

    @Test
    void testDeleteWithoutThrowLeavesNonConfigurablePropertyInPlace() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            JSObject object = context.createJSObject();
            object.defineProperty(
                    PropertyKey.fromString("a"),
                    PropertyDescriptor.dataDescriptor(JSNumber.of(1), PropertyDescriptor.DataState.None));
            assertThat(object.delete(PropertyKey.fromString("a"), false)).isFalse();
            assertThat(context.hasPendingException()).isFalse();
            assertThat(object.has(PropertyKey.fromString("a"))).isTrue();
        }
    }

    @Test
    void testJsonParseReviverDeleteOnArrayDoesNotThrowInStrictMode() {
        assertStringWithJavet("""
                'use strict';
                JSON.stringify(JSON.parse('[1, 2]', function (key, value) {
                  if (key === '0') {
                    Object.defineProperty(this, '1', { configurable: false });
                  }
                  return key === '1' ? undefined : value;
                }));""");
    }

    @Test
    void testJsonParseReviverDeleteOnObjectDoesNotThrowInStrictMode() {
        assertStringWithJavet("""
                'use strict';
                JSON.stringify(JSON.parse('{"a":1,"b":2}', function (key, value) {
                  if (key === 'a') {
                    Object.defineProperty(this, 'b', { configurable: false });
                  }
                  return key === 'b' ? undefined : value;
                }));""");
    }

    @Test
    void testTypedArrayElementDeleteStillThrowsInStrictMode() {
        assertErrorWithJavet("""
                'use strict';
                var a = new Int8Array(2);
                delete a[0];""");
    }
}
