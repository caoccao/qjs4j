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
 * {@code getOwnPropertyDescriptor} must not hand out the object's live descriptor.
 * <p>
 * {@link PropertyDescriptor} is mutable, so returning the internal instance let any caller silently
 * rewrite a property's attributes. It also made the result aliased for shape-backed properties but
 * fresh for dense array elements, so a caller could not tell which it had.
 */
public class JSPropertyDescriptorAliasingTest extends BaseJavetTest {

    @Test
    public void testAccessorDescriptorIsCopiedWithItsFunctions() {
        context.eval(
                """
                        globalThis.probe = {};
                        Object.defineProperty(probe, 'x', { get() { return 7 }, configurable: true });""");
        JSObject probe = (JSObject) context.getGlobalObject().get(PropertyKey.fromString("probe"));
        PropertyDescriptor descriptor = probe.getOwnPropertyDescriptor(PropertyKey.fromString("x"));

        assertThat(descriptor.isAccessorDescriptor()).isTrue();
        assertThat(descriptor.getGetter()).isNotNull();
        descriptor.setGetter(null);
        assertThat(context.eval("probe.x")).as("clearing the copy's getter must not affect the object")
                .isEqualTo(JSNumber.of(7));
    }

    @Test
    public void testArgumentsDescriptorViewDoesNotCorruptTheStoredDescriptor() {
        // JSArguments adjusts the descriptor it returns to reflect the mapped argument. That
        // adjustment must apply to the returned view, not to the object's stored descriptor.
        assertThat(context.eval(
                """
                        function f(a) {
                            const first = Object.getOwnPropertyDescriptor(arguments, '0');
                            a = 99;
                            const second = Object.getOwnPropertyDescriptor(arguments, '0');
                            return first.value + ',' + second.value;
                        }
                        f(1)""").toString())
                .isEqualTo("1,99");
    }

    @Test
    public void testArrayIndexAboveIntegerRangeIsDescribable() {
        // JSArray's descriptor override delegates to super for indices above Integer.MAX_VALUE.
        // Splitting getOwnPropertyDescriptor into a public copying method and an overridable raw
        // one made `super.getOwnPropertyDescriptor(...)` dispatch straight back into the override,
        // so any such index recursed until the stack was exhausted.
        assertThat(context.eval(
                        """
                                const a = [];
                                a[4294967294] = 'far';
                                a.length + ',' + a[4294967294]
                                        + ',' + Object.getOwnPropertyDescriptor(a, '4294967294').value""")
                .toString())
                .isEqualTo("4294967295,far,far");
    }

    @Test
    public void testArrayIndexAtTheIntegerBoundaryIsDescribable() {
        assertThat(context.eval(
                """
                        const a = [];
                        a[2147483647] = 'low';
                        a[2147483648] = 'high';
                        a.length + ',' + a[2147483647] + ',' + a[2147483648]""").toString())
                .isEqualTo("2147483649,low,high");
    }

    @Test
    public void testDescriptorStillReflectsTheCurrentValue() {
        JSObject object = context.createJSObject();
        object.set(PropertyKey.fromString("a"), JSNumber.of(1));
        assertThat(object.getOwnPropertyDescriptor(PropertyKey.fromString("a")).getValue())
                .isEqualTo(JSNumber.of(1));
        object.set(PropertyKey.fromString("a"), JSNumber.of(2));
        assertThat(object.getOwnPropertyDescriptor(PropertyKey.fromString("a")).getValue())
                .as("the copy must be taken after the value is synced, not before")
                .isEqualTo(JSNumber.of(2));
    }

    @Test
    public void testMissingPropertyStillReturnsNull() {
        JSObject object = context.createJSObject();
        assertThat(object.getOwnPropertyDescriptor(PropertyKey.fromString("absent"))).isNull();
    }

    @Test
    public void testMutatingADenseArrayElementDescriptorDoesNotAffectTheArray() {
        JSArray array = context.createJSArray();
        array.set(0, JSNumber.of(1));
        PropertyDescriptor descriptor = array.getOwnPropertyDescriptor(PropertyKey.fromIndex(0));
        descriptor.setValue(new JSString("tampered"));
        descriptor.setWritable(false);
        descriptor.setEnumerable(false);

        assertThat(array.get(0)).isEqualTo(JSNumber.of(1));
        PropertyDescriptor fresh = array.getOwnPropertyDescriptor(PropertyKey.fromIndex(0));
        assertThat(fresh.getValue()).isEqualTo(JSNumber.of(1));
        assertThat(fresh.isWritable()).isTrue();
        assertThat(fresh.isEnumerable()).isTrue();
    }

    @Test
    public void testMutatingAReturnedDescriptorDoesNotChangeTheObject() {
        JSObject object = context.createJSObject();
        object.defineProperty(
                PropertyKey.fromString("locked"),
                new JSString("original"),
                PropertyDescriptor.DataState.EnumerableConfigurable);

        PropertyDescriptor descriptor = object.getOwnPropertyDescriptor(PropertyKey.fromString("locked"));
        assertThat(descriptor.isWritable()).isFalse();
        descriptor.setWritable(true);
        descriptor.setValue(new JSString("tampered"));

        PropertyDescriptor fresh = object.getOwnPropertyDescriptor(PropertyKey.fromString("locked"));
        assertThat(fresh.isWritable()).as("attributes must not be rewritable through the copy").isFalse();
        assertThat(fresh.getValue().toString()).isEqualTo("original");
        assertThat(object.get(PropertyKey.fromString("locked")).toString()).isEqualTo("original");
    }

    @Test
    public void testNonConfigurableLargeIndexBlocksLengthTruncation() {
        // Truncating length scans shape keys through the same descriptor path.
        //
        // The assignment is caught rather than left to propagate: the Javet harness runs every
        // source twice, once with 'use strict' prepended, and in strict mode both engines throw a
        // TypeError here with different wording — V8 blames the delete it could not perform,
        // qjs4j the length assignment. That message gap is real but is not what this test is
        // about, so it is reported separately rather than asserted away here.
        assertStringWithJavet(
                """
                        (function () {
                          const a = [];
                          Object.defineProperty(a, '2147483648', { value: 1, configurable: false });
                          try { a.length = 0 } catch (e) { return e.name + ',' + String(a.length) }
                          return String(a.length);
                        })()""");
    }

    @Test
    public void testObjectGetOwnPropertyDescriptorStillReportsTrueAttributes() {
        assertStringWithJavet(
                """
                        (function () {
                          const o = {};
                          Object.defineProperty(
                              o, 'a', { value: 1, writable: false, enumerable: true, configurable: false });
                          return JSON.stringify(Object.getOwnPropertyDescriptor(o, 'a'));
                        })()""");
    }

    @Test
    public void testTwoCallsReturnIndependentDescriptors() {
        JSObject object = context.createJSObject();
        object.set(PropertyKey.fromString("a"), JSNumber.of(1));
        PropertyDescriptor first = object.getOwnPropertyDescriptor(PropertyKey.fromString("a"));
        PropertyDescriptor second = object.getOwnPropertyDescriptor(PropertyKey.fromString("a"));
        assertThat(first).isNotSameAs(second);
        first.setValue(new JSString("tampered"));
        assertThat(second.getValue()).isEqualTo(JSNumber.of(1));
    }
}
