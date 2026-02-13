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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SharedArrayBuffer.prototype methods.
 */
public class SharedArrayBufferPrototypeTest extends BaseJavetTest {

    @Test
    public void testGetByteLength() {
        JSSharedArrayBuffer sab = new JSSharedArrayBuffer(64);

        // Normal case: get byte length
        JSValue result = SharedArrayBufferPrototype.getByteLength(context, sab, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(64.0));

        // Normal case: empty buffer
        JSSharedArrayBuffer emptySab = new JSSharedArrayBuffer(0);
        result = SharedArrayBufferPrototype.getByteLength(context, emptySab, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(0.0));

        // Edge case: called on non-SharedArrayBuffer
        assertTypeError(SharedArrayBufferPrototype.getByteLength(context, new JSString("not sab"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testGetGrowable() {
        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(32);
        JSValue result = SharedArrayBufferPrototype.getGrowable(context, fixed, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(16, 64);
        result = SharedArrayBufferPrototype.getGrowable(context, growable, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        assertTypeError(SharedArrayBufferPrototype.getGrowable(context, new JSString("not sab"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testGetMaxByteLength() {
        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(32);
        JSValue result = SharedArrayBufferPrototype.getMaxByteLength(context, fixed, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(32.0));

        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(16, 64);
        result = SharedArrayBufferPrototype.getMaxByteLength(context, growable, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSNumber.class, jsNum -> assertThat(jsNum.value()).isEqualTo(64.0));

        assertTypeError(SharedArrayBufferPrototype.getMaxByteLength(context, new JSString("not sab"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testGrow() {
        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(8, 32);

        JSValue result = SharedArrayBufferPrototype.grow(context, growable, new JSValue[]{new JSNumber(16)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(growable.getByteLength()).isEqualTo(16);

        result = SharedArrayBufferPrototype.grow(context, growable, new JSValue[]{new JSNumber(16)});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(growable.getByteLength()).isEqualTo(16);

        result = SharedArrayBufferPrototype.grow(context, growable, new JSValue[]{new JSNumber(33)});
        assertRangeError(result);
        assertPendingException(context);

        result = SharedArrayBufferPrototype.grow(context, growable, new JSValue[]{new JSNumber(15)});
        assertRangeError(result);
        assertPendingException(context);

        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(8);
        result = SharedArrayBufferPrototype.grow(context, fixed, new JSValue[]{new JSNumber(12)});
        assertTypeError(result);
        assertPendingException(context);

        result = SharedArrayBufferPrototype.grow(context, new JSString("not sab"), new JSValue[]{new JSNumber(8)});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testSlice() {
        JSSharedArrayBuffer sab = new JSSharedArrayBuffer(16);

        // Normal case: slice entire buffer
        JSValue result = SharedArrayBufferPrototype.slice(context, sab, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(16));

        // Normal case: slice with start only
        result = SharedArrayBufferPrototype.slice(context, sab, new JSValue[]{new JSNumber(4)});
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(12)); // 16 - 4

        // Normal case: slice with start and end
        result = SharedArrayBufferPrototype.slice(context, sab, new JSValue[]{new JSNumber(4), new JSNumber(12)});
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 12 - 4

        // Normal case: negative start (from end)
        result = SharedArrayBufferPrototype.slice(context, sab, new JSValue[]{new JSNumber(-8)});
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 16 - 8

        // Normal case: negative end (from end)
        result = SharedArrayBufferPrototype.slice(context, sab, new JSValue[]{new JSNumber(4), new JSNumber(-4)});
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 12 - 4

        // Edge case: start >= end (empty slice)
        result = SharedArrayBufferPrototype.slice(context, sab, new JSValue[]{new JSNumber(8), new JSNumber(4)});
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(0));

        // Edge case: called on non-SharedArrayBuffer
        assertTypeError(SharedArrayBufferPrototype.slice(context, new JSString("not sab"), new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testTypeofAndDescriptors() {
        assertBooleanWithJavet(
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "byteLength");
                          return typeof d.get === "function"
                            && d.set === undefined
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """,
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "maxByteLength");
                          return typeof d.get === "function"
                            && d.set === undefined
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """,
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "growable");
                          return typeof d.get === "function"
                            && d.set === undefined
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """,
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, "grow");
                          return typeof d.value === "function"
                            && d.writable === true
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """,
                """
                        (() => {
                          const d = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, Symbol.toStringTag);
                          return d.value === "SharedArrayBuffer"
                            && d.writable === false
                            && d.enumerable === false
                            && d.configurable === true;
                        })()
                        """,
                """
                        (() => {
                          const sab = new SharedArrayBuffer(8, { maxByteLength: 32 });
                          sab.grow(16);
                          return sab.byteLength === 16 && sab.maxByteLength === 32 && sab.growable === true;
                        })()
                        """,
                """
                        (() => {
                          const sab = new SharedArrayBuffer(8);
                          try {
                            sab.grow(16);
                            return false;
                          } catch (e) {
                            return e instanceof TypeError;
                          }
                        })()
                        """);
    }
}
