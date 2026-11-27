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
        JSValue result = SharedArrayBufferPrototype.getByteLength(context, sab, JSValue.NO_ARGS);
        assertNumberValue(result, 64.0);

        // Normal case: empty buffer
        JSSharedArrayBuffer emptySab = new JSSharedArrayBuffer(context, 0);
        result = SharedArrayBufferPrototype.getByteLength(context, emptySab, JSValue.NO_ARGS);
        assertNumberValue(result, 0.0);

        // Edge case: called on non-SharedArrayBuffer
        assertPendingTypeError(context,
                SharedArrayBufferPrototype.getByteLength(context, new JSString("not sab"), JSValue.NO_ARGS));
    }

    @Test
    public void testGetGrowable() {
        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(context, 32);
        JSValue result = SharedArrayBufferPrototype.getGrowable(context, fixed, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(context, 16, 64);
        result = SharedArrayBufferPrototype.getGrowable(context, growable, JSValue.NO_ARGS);
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        assertPendingTypeError(context,
                SharedArrayBufferPrototype.getGrowable(context, new JSString("not sab"), JSValue.NO_ARGS));
    }

    @Test
    public void testGetMaxByteLength() {
        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(context, 32);
        JSValue result = SharedArrayBufferPrototype.getMaxByteLength(context, fixed, JSValue.NO_ARGS);
        assertNumberValue(result, 32.0);

        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(context, 16, 64);
        result = SharedArrayBufferPrototype.getMaxByteLength(context, growable, JSValue.NO_ARGS);
        assertNumberValue(result, 64.0);

        assertPendingTypeError(context,
                SharedArrayBufferPrototype.getMaxByteLength(context, new JSString("not sab"), JSValue.NO_ARGS));
    }

    @Test
    public void testGrow() {
        JSSharedArrayBuffer growable = new JSSharedArrayBuffer(context, 8, 32);

        var grow = builtin(SharedArrayBufferPrototype::grow, growable);
        JSValue result = grow.call(numberArgs(16));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(growable.getByteLength()).isEqualTo(16);

        result = grow.call(numberArgs(16));
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);
        assertThat(growable.getByteLength()).isEqualTo(16);

        result = grow.call(numberArgs(33));
        assertPendingRangeError(context, result);

        result = grow.call(numberArgs(15));
        assertPendingRangeError(context, result);

        JSSharedArrayBuffer fixed = new JSSharedArrayBuffer(context, 8);
        result = SharedArrayBufferPrototype.grow(context, fixed, numberArgs(12));
        assertPendingTypeError(context, result);

        result = SharedArrayBufferPrototype.grow(context, new JSString("not sab"), numberArgs(8));
        assertPendingTypeError(context, result);
    }

    @Test
    public void testSlice() {
        JSSharedArrayBuffer sab = new JSSharedArrayBuffer(context, 16);

        // Normal case: slice entire buffer
        var sliceMethod = builtin(SharedArrayBufferPrototype::slice, sab);
        JSValue result = sliceMethod.call();
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(16));

        // Normal case: slice with start only
        result = sliceMethod.call(numberArgs(4));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(12)); // 16 - 4

        // Normal case: slice with start and end
        result = sliceMethod.call(numberArgs(4, 12));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 12 - 4

        // Normal case: negative start (from end)
        result = sliceMethod.call(numberArgs(-8));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 16 - 8

        // Normal case: negative end (from end)
        result = sliceMethod.call(numberArgs(4, -4));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(8)); // 12 - 4

        // Edge case: start >= end (empty slice)
        result = sliceMethod.call(numberArgs(8, 4));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class,
                jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(0));

        // Edge case: called on non-SharedArrayBuffer
        assertPendingTypeError(context,
                SharedArrayBufferPrototype.slice(context, new JSString("not sab"), JSValue.NO_ARGS));
    }

    @Test
    public void testTypeofAndDescriptors() {
        assertBooleanWithJavet(getterDescriptor("SharedArrayBuffer.prototype", "byteLength"),
                getterDescriptor("SharedArrayBuffer.prototype", "maxByteLength"),
                getterDescriptor("SharedArrayBuffer.prototype", "growable"),
                methodDescriptor("SharedArrayBuffer.prototype", "grow"),
                toStringTagDescriptor("SharedArrayBuffer.prototype", "SharedArrayBuffer"), inArrowFunction("""
                        const sab = new SharedArrayBuffer(8, { maxByteLength: 32 });
                        sab.grow(16);
                        return sab.byteLength === 16 && sab.maxByteLength === 32 && sab.growable === true;
                        """), inArrowFunction("""
                        const sab = new SharedArrayBuffer(8);
                        try {
                          sab.grow(16);
                          return false;
                        } catch (e) {
                          return e instanceof TypeError;
                        }
                        """));
    }
}
