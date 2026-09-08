package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSArrayBuffer;
import com.caoccao.qjs4j.core.JSDataView;
import com.caoccao.qjs4j.core.JSString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DataViewAccessTest extends BaseJavetTest {
    @ParameterizedTest
    @MethodSource("elementTypes")
    public void testConversionsStopOnErrors(String type) {
        assertStringWithJavet("""
                (() => {
                  const type = '%s';
                  return ['receiver', 'negative', 'offsetThrow', 'valueThrow', 'bounds'].map(mode => {
                    const view = new DataView(new ArrayBuffer(16));
                    const log = [];
                    const marker = {};
                    const offset = { valueOf() {
                      log.push('offset');
                      if (mode === 'offsetThrow') throw marker;
                      return mode === 'negative' ? -1 : mode === 'bounds' ? 16 : 0;
                    } };
                    const value = { valueOf() {
                      log.push('value');
                      if (mode === 'valueThrow') throw marker;
                      return type.startsWith('Big') ? 7n : 7;
                    } };
                    let result;
                    try {
                      view['set' + type].call(mode === 'receiver' ? {} : view, offset, value);
                      result = 'success';
                    } catch (e) { result = e === marker ? 'marker' : e.name; }
                    return mode + ':' + result + ':' + log.join(',');
                  }).join('|');
                })()
                """.formatted(type));
    }

    @ParameterizedTest
    @MethodSource("elementTypes")
    public void testImmutableBufferRejectsWritesBeforeConversion(String type) {
        JSDataView view = (JSDataView) context.eval("var view = new DataView(new ArrayBuffer(16)); view");
        ((JSArrayBuffer) view.getBuffer()).setImmutable(true);
        JSString result = (JSString) context.eval("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.set%s(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """.formatted(type));
        assertThat(result.value()).isEqualTo("TypeError:");
    }

    @ParameterizedTest
    @MethodSource("elementTypes")
    public void testReadsAndWritesUseCurrentBufferState(String type) {
        assertStringWithJavet("""
                (() => {
                  const type = '%s';
                  return ['getDetach', 'getShrink', 'offsetDetach', 'valueDetach', 'valueShrink', 'valueGrow']
                    .map(mode => {
                      const buffer = new ArrayBuffer(16, { maxByteLength: 32 });
                      const view = new DataView(buffer, 4, 8);
                      const log = [];
                      const offset = { valueOf() {
                        log.push('offset');
                        if (mode === 'getDetach' || mode === 'offsetDetach') buffer.transfer();
                        if (mode === 'getShrink') buffer.resize(0);
                        return 0;
                      } };
                      const value = { valueOf() {
                        log.push('value');
                        if (mode === 'valueDetach') buffer.transfer();
                        if (mode === 'valueShrink') buffer.resize(0);
                        if (mode === 'valueGrow') buffer.resize(32);
                        return type.startsWith('Big') ? 9n : 9;
                      } };
                      let result;
                      try {
                        if (mode.startsWith('get')) result = view['get' + type](offset, true);
                        else {
                          view['set' + type](offset, value, true);
                          result = view['get' + type](0, true);
                        }
                      } catch (e) { result = e.name; }
                      return mode + ':' + result + ':' + log.join(',');
                    }).join('|');
                })()
                """.formatted(type));
    }

    @ParameterizedTest
    @MethodSource("elementTypes")
    public void testRoundTripAndByteOrder(String type) {
        assertStringWithJavet("""
                (() => {
                  const type = '%s';
                  const values = type.startsWith('Big') ? [-(2n ** 63n) - 1n, 2n ** 64n + 1n]
                    : type.startsWith('Float') ? [-0, 1.5, Infinity, NaN] : [-1, 258, 4294967297, NaN];
                  return [false, true].flatMap(littleEndian => values.map(value => {
                    const buffer = new ArrayBuffer(16);
                    const view = new DataView(buffer);
                    view['set' + type](1, value, littleEndian);
                    const result = view['get' + type](1, littleEndian);
                    return [Object.is(result, -0) ? '-0' : String(result),
                      Array.from(new Uint8Array(buffer)).join(',')].join(':');
                  })).join('|');
                })()
                """.formatted(type));
    }

    private static Stream<String> elementTypes() {
        return Stream.of("Int8", "Uint8", "Int16", "Uint16", "Int32", "Uint32", "Float16", "Float32", "Float64",
                "BigInt64", "BigUint64");
    }
}
