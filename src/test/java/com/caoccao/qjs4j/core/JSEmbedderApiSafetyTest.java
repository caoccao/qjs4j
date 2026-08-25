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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Embedder-facing APIs must not corrupt engine invariants or raise raw {@code java.lang}
 * exceptions.
 * <p>
 * {@code JSObject.set(int, JSValue)} used to write indices {@code >= 100} straight into sparse
 * backing storage, checking nothing, so a frozen object silently stopped being frozen.
 * {@code createJSArray(long)}, {@code JSArray.toArray()} and {@code JSArray.setLength(long)} all
 * threw {@code NegativeArraySizeException} or {@code IllegalArgumentException} for ordinary uint32
 * array lengths.
 */
public class JSEmbedderApiSafetyTest extends BaseTest {

    @Test
    public void testCreateArrayAtTheLengthBoundaryIsStillAccepted() {
        assertThat(context.createJSArray(0).getLength()).isZero();
        assertThat(context.createJSArray(4294967295L).getLength()).isEqualTo(4294967295L);
    }

    @Test
    public void testCreateArrayWithCapacityHintHonoursTheHint() {
        // The hint used to be clamped down to INITIAL_CAPACITY by an inverted Math.min, making
        // every capacity hint in the engine a no-op.
        JSArray array = context.createJSArray(0, 4096);
        for (int index = 0; index < 4096; index++) {
            array.set(index, JSNumber.of(index));
        }
        assertThat(array.getLength()).isEqualTo(4096);
        assertThat(array.get(4095)).isEqualTo(JSNumber.of(4095));
    }

    @Test
    public void testCreateArrayWithInvalidLengthAndCapacityRaisesRangeError() {
        assertThatThrownBy(() -> context.createJSArray(-1, 16))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
        assertThatThrownBy(() -> new JSArray(context, 4294967296L, 16))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
    }

    @Test
    public void testCreateArrayWithInvalidLengthRaisesRangeError() {
        // Clamping only the capacity hint stopped the NegativeArraySizeException but left the
        // invalid length in place: createJSArray(-1) produced an array reporting length -1 and
        // createJSArray(2^32) one reporting 4294967296, neither of which is an array length.
        assertThatThrownBy(() -> context.createJSArray(-1))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
        assertThatThrownBy(() -> context.createJSArray(4294967296L))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
        assertThatThrownBy(() -> context.createJSArray(Long.MAX_VALUE))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
        assertThatThrownBy(() -> context.createJSArray(Long.MIN_VALUE))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
    }

    @Test
    public void testCreateArrayWithMaximumUint32Length() {
        JSArray array = context.createJSArray(4294967295L);
        assertThat(array.getLength()).isEqualTo(4294967295L);
    }

    @Test
    public void testCreateArrayWithNegativeCapacityHintIsClamped() {
        JSArray array = context.createJSArray(0, -1);
        array.set(0, JSNumber.of(7));
        assertThat(array.getLength()).isEqualTo(1);
        assertThat(array.get(0)).isEqualTo(JSNumber.of(7));
    }

    @Test
    public void testFromAsyncOnHugeArrayLikeDoesNotCrash() {
        // ArrayConstructor.fromAsync passes a toLength() result straight to createJSArray(long),
        // which is the JavaScript-reachable path to the negative-capacity crash.
        JSValue result = context.eval("Array.fromAsync({ length: 4294967295 })");
        context.processMicrotasks();
        assertThat(result).isInstanceOf(JSPromise.class);
    }

    @Test
    public void testSetIndexOnFrozenObjectIsRejected() {
        JSObject object = context.createJSObject();
        object.freeze();
        object.set(500, new JSString("leaked"));
        assertThat(object.get(PropertyKey.fromIndex(500))).isEqualTo(JSUndefined.INSTANCE);

        context.getGlobalObject().set("probe", object);
        assertThat(context.eval("JSON.stringify(Object.keys(probe))").toString()).isEqualTo("[]");
        assertThat(context.eval("Object.isFrozen(probe)")).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testSetIndexOnNonExtensibleObjectIsRejected() {
        JSObject object = context.createJSObject();
        object.preventExtensions();
        object.set(500, new JSString("leaked"));

        context.getGlobalObject().set("probe", object);
        assertThat(context.eval("JSON.stringify(Object.keys(probe))").toString()).isEqualTo("[]");
        assertThat(context.eval("Object.isExtensible(probe)")).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testSetIndexOnOrdinaryObjectStillStoresTheValue() {
        JSObject object = context.createJSObject();
        object.set(5, new JSString("small"));
        object.set(500, new JSString("large"));

        context.getGlobalObject().set("probe", object);
        assertThat(context.eval("probe[5]").toString()).isEqualTo("small");
        assertThat(context.eval("probe[500]").toString()).isEqualTo("large");
        assertThat(context.eval("JSON.stringify(Object.keys(probe))").toString()).isEqualTo("[\"5\",\"500\"]");
    }

    @Test
    public void testSetIndexOnSealedObjectUpdatesExistingButAddsNothing() {
        JSObject object = context.createJSObject();
        object.set(500, new JSString("original"));
        object.seal();
        object.set(500, new JSString("updated"));
        object.set(501, new JSString("added"));
        assertThat(object.get(PropertyKey.fromIndex(500)).toString()).isEqualTo("updated");
        assertThat(object.get(PropertyKey.fromIndex(501))).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testSetIndexRespectsAnExistingNonWritableProperty() {
        JSObject object = context.createJSObject();
        object.defineProperty(
                PropertyKey.fromIndex(500),
                new JSString("original"),
                PropertyDescriptor.DataState.Enumerable);
        object.set(500, new JSString("overwritten"));
        assertThat(object.get(PropertyKey.fromIndex(500)).toString()).isEqualTo("original");
    }

    @Test
    public void testSetLengthOutOfRangeRaisesRangeError() {
        JSArray array = context.createJSArray();
        assertThatThrownBy(() -> array.setLength(-1))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
        assertThatThrownBy(() -> array.setLength(4294967296L))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Invalid array length");
    }

    @Test
    public void testSetLengthWithinRangeIsAccepted() {
        JSArray array = context.createJSArray();
        array.setLength(4294967295L);
        assertThat(array.getLength()).isEqualTo(4294967295L);
        array.setLength(0);
        assertThat(array.getLength()).isZero();
    }

    @Test
    public void testToArrayAboveJavaArrayLimitRaisesRangeError() {
        JSArray array = context.createJSArray();
        array.setLength(3000000000L);
        assertThatThrownBy(array::toArray)
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("exceeds the maximum Java array size");
    }

    @Test
    public void testToArrayWithinJavaArrayLimitStillWorks() {
        JSArray array = context.createJSArray();
        array.set(0, JSNumber.of(1));
        array.set(1, JSNumber.of(2));
        assertThat(array.toArray()).containsExactly(JSNumber.of(1), JSNumber.of(2));
    }
}
