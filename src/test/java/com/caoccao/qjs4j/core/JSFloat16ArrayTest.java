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
import com.caoccao.qjs4j.utils.Float16;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Unit tests for JSFloat16Array.
 */
public class JSFloat16ArrayTest extends BaseTest {

    @Test
    public void testBasicCreationAndAccess() {
        JSFloat16Array array = new JSFloat16Array(5);

        assertThat(array.getLength()).isEqualTo(5);
        assertThat(array.getByteLength()).isEqualTo(10); // 5 * 2 bytes
        assertThat(array.getBytesPerElement()).isEqualTo(2);
        assertThat(array.getByteOffset()).isEqualTo(0);

        // Test setting and getting values
        array.setElement(0, 1.5);
        array.setElement(1, -2.75);
        array.setElement(2, 0.0);
        array.setElement(3, 100.5);
        array.setElement(4, -50.25);

        assertThat(array.getElement(0)).isCloseTo(1.5, offset(0.01));
        assertThat(array.getElement(1)).isCloseTo(-2.75, offset(0.01));
        assertThat(array.getElement(2)).isEqualTo(0.0);
        assertThat(array.getElement(3)).isCloseTo(100.5, offset(0.1));
        assertThat(array.getElement(4)).isCloseTo(-50.25, offset(0.1));
    }

    @Test
    public void testBufferView() {
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSFloat16Array array = new JSFloat16Array(buffer, 4, 4);

        assertThat(array.getLength()).isEqualTo(4);
        assertThat(array.getByteLength()).isEqualTo(8); // 4 * 2 bytes
        assertThat(array.getByteOffset()).isEqualTo(4);
        assertThat(array.getBuffer()).isSameAs(buffer);

        // Set values
        array.setElement(0, 10.0);
        array.setElement(1, 20.0);
        array.setElement(2, 30.0);
        array.setElement(3, 40.0);

        assertThat(array.getElement(0)).isCloseTo(10.0, offset(0.01));
        assertThat(array.getElement(1)).isCloseTo(20.0, offset(0.01));
        assertThat(array.getElement(2)).isCloseTo(30.0, offset(0.01));
        assertThat(array.getElement(3)).isCloseTo(40.0, offset(0.01));
    }

    @Test
    public void testBytesPerElement() {
        assertThat(JSFloat16Array.BYTES_PER_ELEMENT).isEqualTo(2);
    }

    @Test
    public void testFloat16Conversion() {
        // Test Float16 utility conversions
        float original = 3.14159f;
        short half = Float16.toHalf(original);
        float converted = Float16.toFloat(half);

        // Float16 has limited precision
        assertThat(converted).isCloseTo(original, offset(0.001f));
    }

    @Test
    public void testFloat16Precision() {
        JSFloat16Array array = new JSFloat16Array(7);

        // Test various float16 values
        float[] testValues = {0f, 1f, -1f, 12.345f, -12.345f, 65504f, -65504f};

        for (int i = 0; i < testValues.length; i++) {
            array.setElement(i, testValues[i]);
        }

        // Verify conversions (with appropriate precision tolerance for float16)
        assertThat(array.getElement(0)).isEqualTo(0.0);
        assertThat(array.getElement(1)).isCloseTo(1.0, offset(0.001));
        assertThat(array.getElement(2)).isCloseTo(-1.0, offset(0.001));
        assertThat(array.getElement(3)).isCloseTo(12.345, offset(0.01));
        assertThat(array.getElement(4)).isCloseTo(-12.345, offset(0.01));
        assertThat(array.getElement(5)).isCloseTo(65504.0, offset(1.0));
        assertThat(array.getElement(6)).isCloseTo(-65504.0, offset(1.0));
    }

    @Test
    public void testGlobalConstructor() {
        // Verify Float16Array is registered in global object
        JSObject global = context.getGlobalObject();
        JSValue float16ArrayCtor = global.get("Float16Array");

        assertThat(float16ArrayCtor).isNotNull();
        assertThat(float16ArrayCtor.isObject()).isTrue();

        // Verify BYTES_PER_ELEMENT is set
        JSValue bytesPerElement = float16ArrayCtor.asObject().orElseThrow().get("BYTES_PER_ELEMENT");
        assertThat(bytesPerElement.isNumber()).isTrue();
        assertThat(bytesPerElement.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(2);

        // Verify prototype
        JSValue prototype = float16ArrayCtor.asObject().orElseThrow().get("prototype");
        assertThat(prototype.isObject()).isTrue();

        // Verify prototype.BYTES_PER_ELEMENT
        JSValue protoBytesPerElement = prototype.asObject().orElseThrow().get("BYTES_PER_ELEMENT");
        assertThat(protoBytesPerElement.isNumber()).isTrue();
        assertThat(protoBytesPerElement.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(2);
    }

    @Test
    public void testJSValueIntegration() {
        JSFloat16Array array = new JSFloat16Array(5);

        // Test isFloat16Array
        assertThat(array.isFloat16Array()).isTrue();
        assertThat(array.isFloat32Array()).isFalse();
        assertThat(array.isTypedArray()).isTrue();

        // Test asFloat16Array
        assertThat(array.asFloat16Array()).isPresent();
        assertThat(array.asFloat16Array().get()).isSameAs(array);
        assertThat(array.asFloat32Array()).isEmpty();
    }

    @Test
    public void testSpecialValues() {
        JSFloat16Array array = new JSFloat16Array(6);

        // Test special float values
        array.setElement(0, Float.POSITIVE_INFINITY);
        array.setElement(1, Float.NEGATIVE_INFINITY);
        array.setElement(2, Float.NaN);
        array.setElement(3, 0.0);
        array.setElement(4, -0.0);
        array.setElement(5, Float.MIN_VALUE);

        assertThat(array.getElement(0)).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(array.getElement(1)).isEqualTo(Float.NEGATIVE_INFINITY);
        assertThat(array.getElement(2)).isNaN();
        assertThat(array.getElement(3)).isEqualTo(0.0);
        assertThat(array.getElement(4)).isEqualTo(-0.0);
        // MIN_VALUE might underflow to 0 in float16
        assertThat(array.getElement(5)).isCloseTo(0.0, offset(0.0001));
    }

    @Test
    public void testSubarray() {
        JSFloat16Array original = new JSFloat16Array(10);

        // Fill with test data
        for (int i = 0; i < 10; i++) {
            original.setElement(i, i * 1.5);
        }

        // Create subarray
        JSTypedArray sub = original.subarray(2, 7);

        assertThat(sub).isInstanceOf(JSFloat16Array.class);
        assertThat(sub.getLength()).isEqualTo(5);
        assertThat(sub.getByteLength()).isEqualTo(10); // 5 * 2 bytes
        assertThat(sub.getByteOffset()).isEqualTo(4); // 2 * 2 bytes

        // Verify subarray shares same buffer
        assertThat(sub.getBuffer()).isSameAs(original.getBuffer());

        // Verify values
        for (int i = 0; i < 5; i++) {
            assertThat(sub.getElement(i)).isCloseTo((i + 2) * 1.5, offset(0.01));
        }

        // Modify subarray should affect original
        sub.setElement(0, 999.0);
        assertThat(original.getElement(2)).isCloseTo(999.0, offset(1.0));
    }

    @Test
    public void testSubarrayNegativeIndices() {
        JSFloat16Array array = new JSFloat16Array(10);

        // subarray with negative begin
        JSTypedArray sub1 = array.subarray(-5, 10);
        assertThat(sub1.getLength()).isEqualTo(5);

        // subarray with negative end
        JSTypedArray sub2 = array.subarray(0, -2);
        assertThat(sub2.getLength()).isEqualTo(8);

        // subarray with both negative
        JSTypedArray sub3 = array.subarray(-7, -2);
        assertThat(sub3.getLength()).isEqualTo(5);
    }
}
