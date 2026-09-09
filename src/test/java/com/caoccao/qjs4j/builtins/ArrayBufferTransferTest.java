package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSArrayBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBufferTransferTest extends BaseJavetTest {
    @ParameterizedTest
    @ValueSource(strings = {"transfer", "transferToFixedLength"})
    public void testContentAndResizability(String method) {
        assertStringWithJavet(
                """
                        (() => [false, true].flatMap(resizable => [undefined, 0, 2, 4, 6].map(length => {
                          const source = resizable ? new ArrayBuffer(4, { maxByteLength: 8 }) : new ArrayBuffer(4);
                          const view = new Uint8Array(source);
                          view.set([1, 2, 3, 4]);
                          Object.defineProperty(source, 'constructor', { get() { throw 'species must not be read'; } });
                          const result = source['%s'](length);
                          return [source.detached, view.length, result.byteLength, result.maxByteLength, result.resizable,
                            Object.getPrototypeOf(result) === ArrayBuffer.prototype, Array.from(new Uint8Array(result)).join(',')]
                            .join('|');
                        })).join(';'))()
                        """
                        .formatted(method));
    }

    @Test
    public void testImmutableTransferCopiesBytesAndDetachesSource() {
        JSArrayBuffer source = context.createJSArrayBuffer(4, 8);
        source.getBuffer().put(new byte[]{1, 2, 3, 4}).position(2);
        JSArrayBuffer result = source.transferToImmutable(context);
        assertThat(source.isDetached()).isTrue();
        assertThat(result.isImmutable()).isTrue();
        assertThat(result.isResizable()).isFalse();
        assertThat(result.getByteLength()).isEqualTo(4);
        assertThat(result.getMaxByteLength()).isEqualTo(4);
        byte[] bytes = new byte[4];
        result.getBuffer().get(bytes);
        assertThat(bytes).containsExactly(1, 2, 3, 4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"transfer", "transferToFixedLength"})
    public void testLengthConversionAndErrorOrder(String method) {
        assertStringWithJavet("""
                (() => ['receiver', 'alreadyDetached', 'negative', 'throw', 'detach', 'shrink', 'grow'].map(mode => {
                  const source = new ArrayBuffer(4, { maxByteLength: 8 });
                  new Uint8Array(source).set([1, 2, 3, 4]);
                  const log = [];
                  const marker = {};
                  const length = { valueOf() {
                    log.push('length');
                    if (mode === 'throw') throw marker;
                    if (mode === 'detach') source.transfer();
                    if (mode === 'shrink') source.resize(2);
                    if (mode === 'grow') source.resize(6);
                    return mode === 'negative' ? -1 : 4;
                  } };
                  if (mode === 'alreadyDetached') source.transfer();
                  let outcome;
                  try {
                    const result = ArrayBuffer.prototype['%s'].call(mode === 'receiver' ? {} : source, length);
                    outcome = Array.from(new Uint8Array(result)).join(',');
                  } catch (e) { outcome = e === marker ? 'marker' : e.name; }
                  return [mode, outcome, log.join(','), source.detached, source.byteLength].join('|');
                }).join(';'))()
                """.formatted(method));
    }
}
