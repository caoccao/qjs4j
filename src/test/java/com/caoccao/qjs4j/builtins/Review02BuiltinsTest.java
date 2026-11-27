package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

class Review02BuiltinsTest extends BaseJavetTest {
    @Test
    void callableProxyCallbacks() {
        assertStringWithJavet(
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].map(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].filter(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].find(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].findIndex(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].findLast(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].findLastIndex(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].every(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].some(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].flatMap(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].forEach(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].reduce(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """,
                """
                        (() => {
                          const log=[];
                          const callback=new Proxy(function(value, index){log.push([value,index,this && this.tag]); return value;}, {});
                          const result=[1,2].reduceRight(callback,{tag:'this'});
                          return JSON.stringify([result,log]);
                        })()
                        """);
        assertStringWithJavet(
                """
                        (() => {
                          const log=[];
                          for(const collection of [new Map([['x',2]]),new Set([2])]) {
                            collection.forEach(new Proxy(function(v,k){log.push([v,k,this.tag]);},{}),{tag:3});
                          }
                          const callback=new Proxy(x=>x%2,{});
                          return JSON.stringify([log,Array.from(Map.groupBy([1,2,3],callback)),Object.groupBy([1,2,3],callback)]);
                        })()
                        """,
                "new Promise(new Proxy(resolve=>resolve('ok'),{}))",
                "JSON.stringify(JSON.parse('{\"x\":1}',new Proxy((k,v)=>k==='x'?2:v,{})))",
                "JSON.stringify({x:1},new Proxy((k,v)=>k==='x'?2:v,{}))",
                "JSON.stringify({toJSON:new Proxy(()=>({x:2}),{})})", "JSON.stringify([new Proxy(()=>0,{})])");
        assertStringWithJavet("""
                (()=>{const r=Proxy.revocable(x=>x,{});r.revoke();try{[1].map(r.proxy)}catch(e){return e.name}})()
                """);
    }

    @Test
    void callableProxyReplacementAndSetProtocols() {
        assertStringWithJavet("'aba'.replaceAll('a',new Proxy((x,i)=>String(i),{}))",
                "'aba'.replace(/a/g,new Proxy((x,i)=>String(i),{}))",
                "'aba'.replace('a',new Proxy((x,i)=>String(i),{}))",
                "'a'.replace({[Symbol.replace]:new Proxy(()=> 'custom',{})},'b')");
        assertStringWithJavet("""
                (()=>{const values=new Set([2,3]);const other={size:2,
                has:new Proxy(k=>values.has(k),{}), keys:new Proxy(()=>{
                  const i=values.keys();return {next:new Proxy(()=>i.next(),{}),return:new Proxy(()=>({done:true}),{})};
                },{})};const r=new Set([1,2]).union(other);return JSON.stringify(r instanceof Set?[...r]:r)})()
                """, """
                (()=>{const values=new Set([2,3]);const other={size:2,
                has:new Proxy(k=>values.has(k),{}), keys:new Proxy(()=>{
                  const i=values.keys();return {next:new Proxy(()=>i.next(),{}),return:new Proxy(()=>({done:true}),{})};
                },{})};const r=new Set([1,2]).intersection(other);return JSON.stringify(r instanceof Set?[...r]:r)})()
                """, """
                (()=>{const values=new Set([2,3]);const other={size:2,
                has:new Proxy(k=>values.has(k),{}), keys:new Proxy(()=>{
                  const i=values.keys();return {next:new Proxy(()=>i.next(),{}),return:new Proxy(()=>({done:true}),{})};
                },{})};const r=new Set([1,2]).difference(other);return JSON.stringify(r instanceof Set?[...r]:r)})()
                """,
                """
                        (()=>{const values=new Set([2,3]);const other={size:2,
                        has:new Proxy(k=>values.has(k),{}), keys:new Proxy(()=>{
                          const i=values.keys();return {next:new Proxy(()=>i.next(),{}),return:new Proxy(()=>({done:true}),{})};
                        },{})};const r=new Set([1,2]).symmetricDifference(other);return JSON.stringify(r instanceof Set?[...r]:r)})()
                        """,
                """
                        (()=>{const values=new Set([2,3]);const other={size:2,
                        has:new Proxy(k=>values.has(k),{}), keys:new Proxy(()=>{
                          const i=values.keys();return {next:new Proxy(()=>i.next(),{}),return:new Proxy(()=>({done:true}),{})};
                        },{})};const r=new Set([1,2]).isSubsetOf(other);return JSON.stringify(r instanceof Set?[...r]:r)})()
                        """,
                """
                        (()=>{const values=new Set([2,3]);const other={size:2,
                        has:new Proxy(k=>values.has(k),{}), keys:new Proxy(()=>{
                          const i=values.keys();return {next:new Proxy(()=>i.next(),{}),return:new Proxy(()=>({done:true}),{})};
                        },{})};const r=new Set([1,2]).isSupersetOf(other);return JSON.stringify(r instanceof Set?[...r]:r)})()
                        """,
                """
                        (()=>{const values=new Set([2,3]);const other={size:2,
                        has:new Proxy(k=>values.has(k),{}), keys:new Proxy(()=>{
                          const i=values.keys();return {next:new Proxy(()=>i.next(),{}),return:new Proxy(()=>({done:true}),{})};
                        },{})};const r=new Set([1,2]).isDisjointFrom(other);return JSON.stringify(r instanceof Set?[...r]:r)})()
                        """);
    }

    @Test
    void computedMapInsertionPreservesOrder() {
        assertStringWithJavet("""
                (()=>{const m=new Map(); const v=m.getOrInsertComputed('a',new Proxy(k=>{
                  m.set(k,1);m.set('b',2);return 3;
                },{})); return JSON.stringify([v,Array.from(m)]);})()
                """);
    }

    @Test
    void jsonReplacerDeduplicationAndSource() {
        assertStringWithJavet("""
                (()=>{const log=[];const k=new String('b');k.toString=()=>{log.push('b');return 'b'};
                return JSON.stringify([JSON.stringify({a:1,b:2},['b',k,'a','b',1,'1']),log]);})()
                """, "JSON.stringify(JSON.parse('{\"a\":[1,2],\"b\":true}'))",
                "JSON.stringify(JSON.parse('{\"a\":[1,2],\"b\":true}',null))", """
                        (()=>{const sources=[];JSON.parse('{"a":[1e2,2],"b":true}',new Proxy((k,v,c)=>{
                        sources.push([k,c.source]);return v;},{}));return JSON.stringify(sources)})()
                        """);
    }

    @Test
    void largeSparseArrayAt() {
        assertStringWithJavet("""
                (()=>{const a=[];a[2147483648]=42;a[4294967294]=43;
                return JSON.stringify([a.at(-1),a.at(2147483648),a.at(4294967294),a.at(Infinity),a.at(-Infinity)]);})()
                """);
    }

    @Test
    void sortAbruptCompletionDoesNotContinueOrWriteBack() {
        assertStringWithJavet("""
                (()=>{const a=[3,1,2];let calls=0;try{a.sort(()=>{calls++;throw 'stop'})}
                catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                """, """
                (()=>{const a=[3,1,2];let calls=0;try{a.sort(()=>{calls++;return {valueOf(){a[0]=99;throw 'stop'}}})}
                catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                """, """
                (()=>{const a=[3,1,2];let calls=0;try{a.toSorted(()=>{calls++;throw 'stop'})}
                catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                """,
                """
                        (()=>{const a=[3,1,2];let calls=0;try{a.toSorted(()=>{calls++;return {valueOf(){a[0]=99;throw 'stop'}}})}
                        catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                        """,
                """
                        (()=>{const a=new Int32Array([3,1,2]);let calls=0;try{a.sort(()=>{calls++;throw 'stop'})}
                        catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                        """,
                """
                        (()=>{const a=new Int32Array([3,1,2]);let calls=0;try{a.sort(()=>{calls++;return {valueOf(){a[0]=99;throw 'stop'}}})}
                        catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                        """,
                """
                        (()=>{const a=new Int32Array([3,1,2]);let calls=0;try{a.toSorted(()=>{calls++;throw 'stop'})}
                        catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                        """,
                """
                        (()=>{const a=new Int32Array([3,1,2]);let calls=0;try{a.toSorted(()=>{calls++;return {valueOf(){a[0]=99;throw 'stop'}}})}
                        catch(e){return JSON.stringify([e,calls,Array.from(a)])}})()
                        """);
        assertStringWithJavet(
                """
                        (()=>{const log=[];const a=[{toString(){log.push('first');throw 'stop'}},{toString(){log.push('second');throw 'stop'}}];
                        try{a.sort()}catch(e){return JSON.stringify([e,log.length])}})()
                        """);
    }

    @Test
    void sortComparatorSignAndUndefined() {
        assertStringWithJavet("JSON.stringify([3,1,2,1].sort((a,b)=>(a-b)/10))",
                "JSON.stringify([3,1,2,1].sort((a,b)=>(a-b)*4294967296))",
                "JSON.stringify([3,1,2,1].sort((a,b)=>a===b?0:a<b?-Infinity:Infinity))",
                "JSON.stringify([3,1,2,1].sort((a,b)=>NaN))", "JSON.stringify([undefined,'z',,'a',undefined].sort())",
                """
                        (()=>{const log=[];const a=[undefined,2,1,undefined];
                        const b=a.sort((x,y)=>{log.push([typeof x,typeof y]);return x-y});
                        return JSON.stringify([b,log]);})()
                        """, """
                        (()=>{let calls=0;const comparator={toString(){calls++;throw 'unexpected'}};
                        try{[1,2].sort(comparator)}catch(e){return JSON.stringify([e.name,calls])}})()
                        """, "JSON.stringify([3,1,2,1].toSorted((a,b)=>(a-b)/10))",
                "JSON.stringify([3,1,2,1].toSorted((a,b)=>(a-b)*4294967296))",
                "JSON.stringify([3,1,2,1].toSorted((a,b)=>a===b?0:a<b?-Infinity:Infinity))",
                "JSON.stringify([3,1,2,1].toSorted((a,b)=>NaN))",
                "JSON.stringify([undefined,'z',,'a',undefined].toSorted())", """
                        (()=>{const log=[];const a=[undefined,2,1,undefined];
                        const b=a.toSorted((x,y)=>{log.push([typeof x,typeof y]);return x-y});
                        return JSON.stringify([b,log]);})()
                        """, """
                        (()=>{let calls=0;const comparator={toString(){calls++;throw 'unexpected'}};
                        try{[1,2].toSorted(comparator)}catch(e){return JSON.stringify([e.name,calls])}})()
                        """);
        assertStringWithJavet("""
                (()=>{let seed=1;const random=()=>seed=(Math.imul(seed,1664525)+1013904223)>>>0;
                for(let i=0;i<100;i++)Array.from({length:100},(_,k)=>k).sort(()=>random()%3-1);
                return 'completed'})()
                """);
    }

    @Test
    void typedArrayCallableProxyCallbacks() {
        assertStringWithJavet("""
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).every(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).some(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).filter(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).find(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).findIndex(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).findLast(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).findLastIndex(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).forEach(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).map(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).reduce(f);
                  return JSON.stringify([String(result),log]);})()
                """, """
                (()=>{const log=[], f=new Proxy((x,i)=>{log.push([x,i]);return x>1},{});
                  const result=new Uint8Array([1,2,3]).reduceRight(f);
                  return JSON.stringify([String(result),log]);})()
                """);
    }
}
