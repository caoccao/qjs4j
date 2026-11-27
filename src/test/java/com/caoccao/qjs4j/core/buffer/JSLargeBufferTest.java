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
        for (String type : new String[]{"Int8Array", "Uint8Array", "Int16Array", "Uint16Array", "Int32Array",
                "Uint32Array", "BigInt64Array", "BigUint64Array"}) {
            String value = type.startsWith("Big") ? "1n" : "1";
            for (String method : new String[]{"add", "and", "or", "xor", "sub", "exchange", "compareExchange", "store",
                    "load"}) {
                for (String effect : new String[]{"b.transfer()", "b.resize(0)", "b.resize(2**21)"}) {
                    assertStringWithJavet("""
                            (()=>{const b=new ArrayBuffer(16,{maxByteLength:2**21}),a=new %s(b),log=[];
                              const index={valueOf(){log.push('index');%s;return 0}},
                              value={valueOf(){log.push('value');return %s}};
                              try {log.push(String(Atomics.%s(a,index,value,value)))}catch(e){log.push(e.name)}
                              return JSON.stringify(log)})()
                            """.formatted(type, effect, value, method));
                    if (!method.equals("load")) {
                        assertStringWithJavet("""
                                (()=>{const b=new ArrayBuffer(16,{maxByteLength:2**21}),a=new %s(b),log=[];
                                  const value={valueOf(){log.push('value');%s;return %s}};
                                  try {log.push(String(Atomics.%s(a,0,value,value)))}catch(e){log.push(e.name)}
                                  return JSON.stringify(log)})()
                                """.formatted(type, effect, value, method));
                        assertStringWithJavet("""
                                (()=>{const a=new %s(4),log=[],v={valueOf(){log.push('value');throw 'stop'}};
                                  try {Atomics.%s(a,0,v,v)}catch(e){log.push(String(e))}
                                  return JSON.stringify([log,String(a[0])])})()
                                """.formatted(type, method));
                    }
                }
            }
        }
    }

    @Test
    void bufferLimitsMatchSandboxedV8() {
        for (String type : new String[]{"ArrayBuffer", "SharedArrayBuffer"}) {
            assertStringWithJavet("""
                    (()=>{const log=[];for (const n of [0,2**35-65536,2**35-65535,2**35-16384,2**35-4096,
                      %d,%d,2**35-1,2**35,Number.MAX_SAFE_INTEGER,Infinity,-1]) {
                      try {const b=new %s(0,{maxByteLength:n});log.push(b.maxByteLength)}catch(e){log.push(e.name)}
                    }return JSON.stringify(log)})()
                    """.formatted(JSArrayBuffer.MAX_RESIZABLE_DATA_BLOCK_BYTE_LENGTH,
                    JSArrayBuffer.MAX_RESIZABLE_DATA_BLOCK_BYTE_LENGTH + 1, type));
        }
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
        for (String type : new String[]{"Int8Array", "Uint8Array", "Int16Array", "Uint16Array", "Uint32Array",
                "BigInt64Array", "BigUint64Array"}) {
            assertStringWithJavet("""
                    (()=>{const b=new SharedArrayBuffer(2**31+64),a=new %s(b,2**31,4);
                    Atomics.store(a,1,%s);return [String(Atomics.add(a,1,%s)),String(Atomics.load(a,1))].join(',');})()
                    """.formatted(type, type.startsWith("Big") ? "7n" : "7", type.startsWith("Big") ? "2n" : "2"));
        }
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
        for (String type : new String[]{"Int32Array", "BigInt64Array"}) {
            assertStringWithJavet("""
                    (()=>{const target=new %s(4),log=[];
                    for(const offset of [4,5,2**31,2**32,Number.MAX_SAFE_INTEGER,1e100,Infinity,-Infinity]) {
                      for(const source of [[],new %s(0),new %s(2)]) {
                        try{target.set(source,offset);log.push('ok')}catch(e){log.push(e.name)}
                      }
                    }return JSON.stringify(log)})()
                    """.formatted(type, type, type));
        }
    }

    @Test
    void wideViewsAndBigIntsCrossPageAndIntegerBoundaries() {
        for (String type : new String[]{"Int8Array", "Uint8ClampedArray", "Int16Array", "Uint16Array", "Int32Array",
                "Uint32Array", "Float16Array", "Float32Array", "Float64Array", "BigInt64Array", "BigUint64Array"}) {
            assertStringWithJavet("""
                    (()=>{const b=new ArrayBuffer(2**31+64),a=new %s(b,2**31,4);
                    a[0]=%s;a[3]=%s;return JSON.stringify([a.byteOffset,a.byteLength,a.length,
                      String(a[0]),String(a.at(-1)),a.subarray(1).byteOffset]);})()
                    """.formatted(type, type.startsWith("Big") ? "123n" : "123", type.startsWith("Big") ? "7n" : "7"));
        }
        assertStringWithJavet("""
                (()=>{const b=new ArrayBuffer(2**31+32),d=new DataView(b);
                for(const p of [2**20-3,2**31-3])d.setFloat64(p,Math.PI,false);
                return JSON.stringify([d.getFloat64(2**20-3,false),d.getFloat64(2**31-3,false)]);})()
                """);
    }
}
