package com.caoccao.qjs4j.core.buffer;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSRuntimeOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class JSLargeBufferTest extends BaseJavetTest {
    @Test
    void arrayBufferAndViewsUseLongOffsets() {
        assertStringWithJavet("""
                (()=>{const n=2**31,b=new ArrayBuffer(n+64),a=new Uint8Array(b),d=new DataView(b);
                a[n-1]=7;a[n]=8;a[n+1]=9;d.setUint32(n+4,0x12345678,true);
                const view=new DataView(b,n+4,4),tail=a.subarray(n-1,n+9);
                a.copyWithin(n+16,n-1,n+2);a.set([21,22],n+20);
                return JSON.stringify([b.byteLength,a.length,a.byteLength,a.at(n),a.at(-64),
                  a.indexOf(22,n+20),a.includes(22,n+20),a.lastIndexOf(8,n),
                  d.getUint32(n+4,true),view.byteOffset,view.getUint32(0,true),[...tail],
                  [...a.slice(n+16,n+22)],[...new Uint8Array(b.slice(n-1,n+2))],a[n+64]]);})()
                """);
    }

    @Test
    void atomicsRevalidateStorageAfterCoercion() {
        assertStringWithJavet("""
                (() => {
                  const results = [];
                  const types = [Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array,
                    BigInt64Array, BigUint64Array];
                  const methods = ['add', 'and', 'or', 'xor', 'sub', 'exchange', 'compareExchange', 'store', 'load'];
                  const effects = [b => b.transfer(), b => b.resize(0), b => b.resize(2 ** 21)];
                  for (const Type of types) {
                    const one = Type === BigInt64Array || Type === BigUint64Array ? 1n : 1;
                    for (const method of methods) {
                      for (const effect of effects) {
                        const buffer = new ArrayBuffer(16, { maxByteLength: 2 ** 21 });
                        const array = new Type(buffer);
                        const log = [];
                        const index = { valueOf() { log.push('index'); effect(buffer); return 0; } };
                        const value = { valueOf() { log.push('value'); return one; } };
                        try { log.push(String(Atomics[method](array, index, value, value))); }
                        catch (e) { log.push(e.name); }
                        results.push(log);
                        if (method === 'load') continue;

                        const secondBuffer = new ArrayBuffer(16, { maxByteLength: 2 ** 21 });
                        const secondArray = new Type(secondBuffer);
                        const valueLog = [];
                        const mutating = { valueOf() { valueLog.push('value'); effect(secondBuffer); return one; } };
                        try { valueLog.push(String(Atomics[method](secondArray, 0, mutating, mutating))); }
                        catch (e) { valueLog.push(e.name); }
                        results.push(valueLog);

                        const target = new Type(4);
                        const throwLog = [];
                        const throwing = { valueOf() { throwLog.push('value'); throw 'stop'; } };
                        try { Atomics[method](target, 0, throwing, throwing); }
                        catch (e) { throwLog.push(String(e)); }
                        results.push([throwLog, String(target[0])]);
                      }
                    }
                  }
                  return JSON.stringify(results);
                })()
                """);
    }

    @Test
    void bufferLimitsMatchSandboxedV8() {
        assertStringWithJavet("""
                (() => {
                  const ceiling = 2 ** 35;
                  const lengths = [0, ceiling - 65536, ceiling - 65535, ceiling - 16384, ceiling - 16383,
                    ceiling - 4096, ceiling - 4095, ceiling - 1, ceiling, Number.MAX_SAFE_INTEGER, Infinity, -1];
                  return JSON.stringify([ArrayBuffer, SharedArrayBuffer].map(Type => lengths.map(length => {
                    try { return new Type(0, { maxByteLength: length }).maxByteLength; }
                    catch (e) { return e.name; }
                  })));
                })()
                """);
    }

    @Override
    protected JSRuntime createRuntime() {
        return new JSRuntime(new JSRuntimeOptions().setMaxDataBlockBytes(0));
    }

    @Test
    void largeResizableBuffersZeroShrunkBytesAndTransfer() {
        assertStringWithJavet(
                """
                        (()=>{const n=2**31,b=new ArrayBuffer(8,{maxByteLength:n+32}),a=new Uint8Array(b);
                        a[0]=3;b.resize(n+16);a[n]=7;const fixed=new Uint8Array(b,n,8);
                        b.resize(n);const before=[fixed.length,a.length];b.resize(n+32);
                        const after=[a[n],fixed[0],a[0],a.length];const c=b.transfer(32);
                        return JSON.stringify([before,after,b.byteLength,c.byteLength,c.maxByteLength,new Uint8Array(c)[0]]);})()
                        """);
    }

    @Test
    void largeSharedBuffersAndAtomicsUseStablePages() {
        assertStringWithJavet("""
                (()=>{const n=2**31,b=new SharedArrayBuffer(n+16,{maxByteLength:n+64}),a=new Int32Array(b);
                const i=n/4;Atomics.store(a,i,7);const log=[Atomics.add(a,i,3),Atomics.compareExchange(a,i,10,20),
                 Atomics.exchange(a,i,30),Atomics.and(a,i,15),Atomics.or(a,i,16),Atomics.xor(a,i,1),Atomics.sub(a,i,2),
                 Atomics.load(a,i),Atomics.wait(a,i,999,0),Atomics.notify(a,i)];
                b.grow(n+64);log.push(a.length,a[i],new Uint8Array(b.slice(n,n+4)).join(','));
                return JSON.stringify(log);})()
                """);
        assertStringWithJavet("""
                (()=>{const b=new SharedArrayBuffer(2**31+64),a=new Int8Array(b,2**31,4);
                Atomics.store(a,1,7);return [String(Atomics.add(a,1,2)),String(Atomics.load(a,1))].join(',');})()
                """, """
                (()=>{const b=new SharedArrayBuffer(2**31+64),a=new Uint8Array(b,2**31,4);
                Atomics.store(a,1,7);return [String(Atomics.add(a,1,2)),String(Atomics.load(a,1))].join(',');})()
                """, """
                (()=>{const b=new SharedArrayBuffer(2**31+64),a=new Int16Array(b,2**31,4);
                Atomics.store(a,1,7);return [String(Atomics.add(a,1,2)),String(Atomics.load(a,1))].join(',');})()
                """, """
                (()=>{const b=new SharedArrayBuffer(2**31+64),a=new Uint16Array(b,2**31,4);
                Atomics.store(a,1,7);return [String(Atomics.add(a,1,2)),String(Atomics.load(a,1))].join(',');})()
                """, """
                (()=>{const b=new SharedArrayBuffer(2**31+64),a=new Uint32Array(b,2**31,4);
                Atomics.store(a,1,7);return [String(Atomics.add(a,1,2)),String(Atomics.load(a,1))].join(',');})()
                """, """
                (()=>{const b=new SharedArrayBuffer(2**31+64),a=new BigInt64Array(b,2**31,4);
                Atomics.store(a,1,7n);return [String(Atomics.add(a,1,2n)),String(Atomics.load(a,1))].join(',');})()
                """, """
                (()=>{const b=new SharedArrayBuffer(2**31+64),a=new BigUint64Array(b,2**31,4);
                Atomics.store(a,1,7n);return [String(Atomics.add(a,1,2n)),String(Atomics.load(a,1))].join(',');})()
                """);
    }

    @Test
    void sparseCopiesAndAccountingDoNotRequireContiguousHeap() {
        long length = (1L << 31) + 64;
        JSArrayBuffer buffer = context.createJSArrayBuffer(length, length + 64);
        buffer.getBuffer().put(0, (byte) 3).put(length - 1, (byte) 7);
        assertThat(context.getRuntime().getMemoryAccounting().getReservedBytes()).isEqualTo(length);
        JSArrayBuffer copy = buffer.slice(context, 0, length);
        assertThat(copy.getBuffer().get(length - 1)).isEqualTo((byte) 7);
        assertThat(copy.getBuffer().get(1L << 30)).isZero();
        buffer.resize(length + 64);
        assertThat(buffer.getBuffer().get(length - 1)).isEqualTo((byte) 7);
        buffer.resize(8);
        buffer.resize(length);
        assertThat(buffer.getBuffer().get(length - 1)).isZero();
        JSArrayBuffer transferred = copy.transfer(context, -1);
        assertThat(copy.isDetached()).isTrue();
        assertThat(transferred.getBuffer().get(length - 1)).isEqualTo((byte) 7);
        transferred.detach();
        buffer.detach();
        assertThat(context.getRuntime().getMemoryAccounting().getReservedBytes()).isZero();
    }

    @Test
    void typedArraySetRejectsOffsetsWithoutLongAdditionOverflow() {
        assertStringWithJavet("""
                (()=>{const target=new Int32Array(4),log=[];
                for(const offset of [4,5,2**31,2**32,Number.MAX_SAFE_INTEGER,1e100,Infinity,-Infinity]) {
                  for(const source of [[],new Int32Array(0),new Int32Array(2)]) {
                    try{target.set(source,offset);log.push('ok')}catch(e){log.push(e.name)}
                  }
                }return JSON.stringify(log)})()
                """, """
                (()=>{const target=new BigInt64Array(4),log=[];
                for(const offset of [4,5,2**31,2**32,Number.MAX_SAFE_INTEGER,1e100,Infinity,-Infinity]) {
                  for(const source of [[],new BigInt64Array(0),new BigInt64Array(2)]) {
                    try{target.set(source,offset);log.push('ok')}catch(e){log.push(e.name)}
                  }
                }return JSON.stringify(log)})()
                """);
    }

    @Test
    void wideViewsAndBigIntsCrossPageAndIntegerBoundaries() {
        assertStringWithJavet("""
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Int8Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Uint8ClampedArray(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Int16Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Uint16Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Int32Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Uint32Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Float16Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Float32Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new Float64Array(b,2**31,4);
                a[0]=123;a[3]=7;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new BigInt64Array(b,2**31,4);
                a[0]=123n;a[3]=7n;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """, """
                (()=>{const b=new ArrayBuffer(2**31+64),a=new BigUint64Array(b,2**31,4);
                a[0]=123n;a[3]=7n;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                  String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                """);
        assertStringWithJavet("""
                (()=>{const b=new ArrayBuffer(2**31+32),d=new DataView(b);
                for(const p of [2**20-3,2**31-3])d.setFloat64(p,Math.PI,false);
                return JSON.stringify([d.getFloat64(2**20-3,false),d.getFloat64(2**31-3,false)]);})()
                """);
    }
}
