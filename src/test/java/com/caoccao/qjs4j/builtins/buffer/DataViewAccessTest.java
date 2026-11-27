package com.caoccao.qjs4j.builtins.buffer;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.buffer.JSArrayBuffer;
import com.caoccao.qjs4j.core.buffer.JSDataView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

public class DataViewAccessTest extends BaseJavetTest {
    private void assertImmutableBufferRejectsWrite(String code) {
        JSDataView view = (JSDataView) resetContext().eval("var view = new DataView(new ArrayBuffer(16)); view");
        ((JSArrayBuffer) view.getBuffer()).setImmutable(true);
        JSString result = (JSString) context.eval(code);
        assertThat(result.value()).isEqualTo("TypeError:");
    }

    @Test
    public void testConversionsStopOnErrors() {
        assertStringWithJavet("""
                (() => {
                  const type = 'Int8';
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
                })()""", """
                (() => {
                  const type = 'Uint8';
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
                })()""", """
                (() => {
                  const type = 'Int16';
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
                })()""", """
                (() => {
                  const type = 'Uint16';
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
                })()""", """
                (() => {
                  const type = 'Int32';
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
                })()""", """
                (() => {
                  const type = 'Uint32';
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
                })()""", """
                (() => {
                  const type = 'Float16';
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
                })()""", """
                (() => {
                  const type = 'Float32';
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
                })()""", """
                (() => {
                  const type = 'Float64';
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
                })()""", """
                (() => {
                  const type = 'BigInt64';
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
                })()""", """
                (() => {
                  const type = 'BigUint64';
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
                })()""");
    }

    @Test
    public void testImmutableBufferRejectsWritesBeforeConversion() {
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setInt8(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setUint8(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setInt16(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setUint16(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setInt32(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setUint32(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setFloat16(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setFloat32(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setFloat64(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setBigInt64(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
        assertImmutableBufferRejectsWrite("""
                (() => {
                  const log = [];
                  const offset = { valueOf() { log.push('offset'); return 0; } };
                  const value = { valueOf() { log.push('value'); return 1; } };
                  try { view.setBigUint64(offset, value); }
                  catch (e) { return e.name + ':' + log.join(','); }
                  return 'missing error';
                })()
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testRawNaNPayloadsAndBackingByteOrder(boolean littleEndian) {
        JSDataView view = (JSDataView) context.eval("new DataView(new ArrayBuffer(16), 1)");
        var bytes = ((JSArrayBuffer) view.getBuffer()).getBuffer();
        int floatBits = 0x7fc12345;
        long doubleBits = 0xfff8123456789abcL;
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            bytes.order(order);
            view.setFloat32(0, Float.intBitsToFloat(floatBits), littleEndian);
            view.setFloat64(4, Double.longBitsToDouble(doubleBits), littleEndian);
            assertThat(bytes.order()).isEqualTo(order);
            boolean sameOrder = littleEndian == (order == ByteOrder.LITTLE_ENDIAN);
            assertThat(bytes.getInt(1)).isEqualTo(sameOrder ? floatBits : Integer.reverseBytes(floatBits));
            assertThat(bytes.getLong(5)).isEqualTo(sameOrder ? doubleBits : Long.reverseBytes(doubleBits));
            assertThat(Float.floatToRawIntBits(view.getFloat32(0, littleEndian))).isEqualTo(floatBits);
            assertThat(Double.doubleToRawLongBits(view.getFloat64(4, littleEndian))).isEqualTo(doubleBits);
            assertThat(bytes.order()).isEqualTo(order);
        }
    }

    @Test
    public void testReadsAndWritesUseCurrentBufferState() {
        assertStringWithJavet("""
                (() => {
                  const type = 'Int8';
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
                })()""", """
                (() => {
                  const type = 'Uint8';
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
                })()""", """
                (() => {
                  const type = 'Int16';
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
                })()""", """
                (() => {
                  const type = 'Uint16';
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
                })()""", """
                (() => {
                  const type = 'Int32';
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
                })()""", """
                (() => {
                  const type = 'Uint32';
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
                })()""", """
                (() => {
                  const type = 'Float16';
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
                })()""", """
                (() => {
                  const type = 'Float32';
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
                })()""", """
                (() => {
                  const type = 'Float64';
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
                })()""", """
                (() => {
                  const type = 'BigInt64';
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
                })()""", """
                (() => {
                  const type = 'BigUint64';
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
                })()""");
    }

    @Test
    public void testRoundTripAndByteOrder() {
        assertStringWithJavet("""
                (() => {
                  const type = 'Int8';
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
                })()""", """
                (() => {
                  const type = 'Uint8';
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
                })()""", """
                (() => {
                  const type = 'Int16';
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
                })()""", """
                (() => {
                  const type = 'Uint16';
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
                })()""", """
                (() => {
                  const type = 'Int32';
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
                })()""", """
                (() => {
                  const type = 'Uint32';
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
                })()""", """
                (() => {
                  const type = 'Float16';
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
                })()""", """
                (() => {
                  const type = 'Float32';
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
                })()""", """
                (() => {
                  const type = 'Float64';
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
                })()""", """
                (() => {
                  const type = 'BigInt64';
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
                })()""", """
                (() => {
                  const type = 'BigUint64';
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
                })()""");
    }

}
