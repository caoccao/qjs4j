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

package com.caoccao.qjs4j.builtins.buffer;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.JSSharedArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SharedArrayBuffer.prototype methods.
 */
public class SharedArrayBufferPrototypeTest extends BaseJavetTest {

    @Test
    public void testGetByteLength() {
        JSSharedArrayBuffer sab = new JSSharedArrayBuffer(context, 64);

        // Normal case: get byte length
        var getByteLength = nativeMethod(SharedArrayBufferPrototype::getByteLength);
        assertNumberValue(getByteLength.call(sab), 64.0);

        // Normal case: empty buffer
        JSSharedArrayBuffer emptySab = new JSSharedArrayBuffer(context, 0);
        assertNumberValue(getByteLength.call(emptySab), 0.0);

        // Edge case: called on non-SharedArrayBuffer
        assertPendingTypeError(getByteLength.call("not sab"));
    }

    @Test
    public void testGetGrowable() {
        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(context, 32);
        var getGrowable = nativeMethod(SharedArrayBufferPrototype::getGrowable);
        assertThat(getGrowable.call(fixed)).isEqualTo(JSBoolean.FALSE);

        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(context, 16, 64);
        assertThat(getGrowable.call(growable)).isEqualTo(JSBoolean.TRUE);

        assertPendingTypeError(getGrowable.call("not sab"));
    }

    @Test
    public void testGetMaxByteLength() {
        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(context, 32);
        var getMaxByteLength = nativeMethod(SharedArrayBufferPrototype::getMaxByteLength);
        assertNumberValue(getMaxByteLength.call(fixed), 32.0);

        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(context, 16, 64);
        assertNumberValue(getMaxByteLength.call(growable), 64.0);

        assertPendingTypeError(getMaxByteLength.call("not sab"));
    }

    @Test
    public void testGrow() {
        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(context, 8, 32);

        var grow = builtin(SharedArrayBufferPrototype::grow, growable);
        assertThat(grow.call(16)).isEqualTo(JSUndefined.INSTANCE);
        assertThat(growable.getByteLength()).isEqualTo(16);

        assertThat(grow.call(16)).isEqualTo(JSUndefined.INSTANCE);
        assertThat(growable.getByteLength()).isEqualTo(16);

        assertPendingRangeError(grow.call(33));

        assertPendingRangeError(grow.call(15));

        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(context, 8);
        var growMethod = nativeMethod(SharedArrayBufferPrototype::grow);
        assertPendingTypeError(growMethod.call(fixed, 12));

        assertPendingTypeError(growMethod.call("not sab", 8));
    }

    @Test
    public void testSlice() {
        JSSharedArrayBuffer sab = new JSSharedArrayBuffer(context, 16);

        // Normal case: slice entire buffer
        var sliceMethod = builtin(SharedArrayBufferPrototype::slice, sab);
        assertThat(sliceMethod.call()).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(16));

        // Normal case: slice with start only
        assertThat(sliceMethod.call(4)).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(12)); // 16 - 4

        // Normal case: slice with start and end
        assertThat(sliceMethod.call(4, 12)).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 12 - 4

        // Normal case: negative start (from end)
        assertThat(sliceMethod.call(-8)).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 16 - 8

        // Normal case: negative end (from end)
        assertThat(sliceMethod.call(4, -4)).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 12 - 4

        // Edge case: start >= end (empty slice)
        assertThat(sliceMethod.call(8, 4)).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(0));

        // Edge case: called on non-SharedArrayBuffer
        assertPendingTypeError(callBuiltin(SharedArrayBufferPrototype::slice, "not sab"));
    }

    @Test
    public void testTypeofAndDescriptors() {
        assertBooleanWithJavet("""
                (() => {
                    const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "byteLength");
                    return typeof d.get === "function"
                        && d.set === undefined
                        && d.enumerable === false
                        && d.configurable === true;
                })()
                """, """
                (() => {
                    const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "maxByteLength");
                    return typeof d.get === "function"
                        && d.set === undefined
                        && d.enumerable === false
                        && d.configurable === true;
                })()
                """, """
                (() => {
                    const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "growable");
                    return typeof d.get === "function"
                        && d.set === undefined
                        && d.enumerable === false
                        && d.configurable === true;
                })()
                """, """
                (() => {
                    const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "grow");
                    return typeof d.value === "function"
                        && d.writable === true
                        && d.enumerable === false
                        && d.configurable === true;
                })()
                """, """
                (() => {
                    const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, Symbol.toStringTag);
                    return d.value === "SharedArrayBuffer"
                        && d.writable === false
                        && d.enumerable === false
                        && d.configurable === true;
                })()
                """, """
                (() => {
                  const sab = new SharedArrayBuffer(8, { maxByteLength: 32 });
                  sab.grow(16);
                  return sab.byteLength === 16 && sab.maxByteLength === 32 && sab.growable === true;
                })()""", """
                (() => {
                  const sab = new SharedArrayBuffer(8);
                  try {
                    sab.grow(16);
                    return false;
                  } catch (e) {
                    return e instanceof TypeError;
                  }
                })()""");
    }
}
