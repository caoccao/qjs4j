package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class JSTypedArrayTest extends BaseJavetTest {
    @ParameterizedTest
    @MethodSource("typedArrayNames")
    public void testConstructorBufferConversionOrder(String name) {
        assertStringWithJavet("""
                (() => {
                  const C = %s;
                  const buffer = new ArrayBuffer(4 * C.BYTES_PER_ELEMENT);
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return C.BYTES_PER_ELEMENT; } };
                  const length = { valueOf() { log.push('length'); return 2; } };
                  const view = new C(buffer, offset, length);
                  let error;
                  try {
                    new C(buffer, { valueOf() { log.push('invalid'); return -1; } }, length);
                  } catch (e) { error = e.name; }
                  return [log.join(','), error, view.length, view.byteOffset,
                    view.buffer === buffer, Object.getPrototypeOf(view) === C.prototype].join('|');
                })()
                """.formatted(name));
    }

    @ParameterizedTest
    @MethodSource("typedArrayNames")
    public void testConstructorSources(String name) {
        assertStringWithJavet("""
                (() => {
                  const C = %s;
                  const values = C.name.startsWith('Big') ? [1n, 2n, 3n] : [1, 2, 3];
                  const original = new C(values);
                  const sources = [values, original, { 0: values[0], 1: values[1], length: 2 },
                    new Set(values), values.values()];
                  const results = sources.map(source => {
                    const array = new C(source);
                    return [array.constructor === C, Object.getPrototypeOf(array) === C.prototype,
                      array.buffer !== original.buffer, Array.from(array, String).join(',')].join(':');
                  });
                  results.push(new C().length, new C(2).length, new C('2').length);
                  return results.join('|');
                })()
                """.formatted(name));
    }

    @ParameterizedTest
    @MethodSource("typedArrayNames")
    public void testLengthTrackingAndFixedBufferViews(String name) {
        assertStringWithJavet("""
                (() => {
                  const C = %s;
                  const bytes = C.BYTES_PER_ELEMENT;
                  return [false, true].map(shared => {
                    const buffer = shared
                      ? new SharedArrayBuffer(4 * bytes, { maxByteLength: 8 * bytes })
                      : new ArrayBuffer(4 * bytes, { maxByteLength: 8 * bytes });
                    const tracking = new C(buffer, bytes, undefined);
                    const fixed = new C(buffer, bytes, 2);
                    if (shared) buffer.grow(6 * bytes); else buffer.resize(6 * bytes);
                    return [tracking.length, fixed.length, tracking.buffer === buffer,
                      tracking.byteOffset, fixed.byteOffset].join(':');
                  }).join('|');
                })()
                """.formatted(name));
    }

    @ParameterizedTest
    @MethodSource("typedArrayNames")
    public void testSubarrayPreservesTypeAndSharesBuffer(String name) {
        JSTypedArray source = (JSTypedArray) context.eval("""
                (() => {
                  const C = %s;
                  return new C(new ArrayBuffer(6 * C.BYTES_PER_ELEMENT), C.BYTES_PER_ELEMENT, 4);
                })()
                """.formatted(name));
        // begin, end, expected relative offset, expected length
        int[][] ranges = {{1, 3, 1, 2}, {-3, -1, 1, 2}, {-99, 99, 0, 4}, {3, 1, 3, 0}, {99, 100, 4, 0},
                {Integer.MIN_VALUE, 0, 0, 0}};
        for (int[] range : ranges) {
            JSTypedArray view = source.subarray(range[0], range[1]);
            assertThat(view).isExactlyInstanceOf(source.getClass());
            assertThat(view.getBuffer()).isSameAs(source.getBuffer());
            assertThat(view.getByteOffset()).isEqualTo((1 + range[2]) * source.getBytesPerElement());
            assertThat(view.getLength()).isEqualTo(range[3]);
        }
        JSTypedArray view = source.subarray(1, 3);
        view.setElement(0, 7);
        assertThat(source.getElement(1)).isEqualTo(7);
    }

    private static Stream<String> typedArrayNames() {
        return Stream.of("Int8Array", "Int16Array", "Int32Array", "Uint8Array", "Uint8ClampedArray", "Uint16Array",
                "Uint32Array", "Float16Array", "Float32Array", "Float64Array", "BigInt64Array", "BigUint64Array");
    }
}
