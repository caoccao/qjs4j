package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

class ClassEvaluationTest extends BaseJavetTest {
    @Test
    void classExpressionsWithComputedFieldsLeaveOnlyTheClassOnTheStack() {
        assertStringWithJavet(
                """
                        (()=>{const a=[];a.push(class{['k']=1;static ['s']=2;get ['g'](){return 3}});const C=a[0];
                        return JSON.stringify([a.length,Object.keys(new C()),Object.keys(C),new C().g,[1,class{['k']=1},2].length,
                        (class{static ['s']=1}).s+1,String(class{['k']=1}).length>0])})()
                        """,
                """
                        (()=>{const keyed=[];for(let k=0;k<2;k++)keyed.push(class{[`k${k}`]=k;static [`s${k}`]=k});
                        return JSON.stringify(keyed.map(C=>[Object.keys(new C()),Object.keys(C)]))})()
                        """);
    }

    @Test
    void reevaluatedClassesKeepTheirOwnPrivateMethods() {
        assertStringWithJavet("""
                (()=>{const classes=[];for(let i=0;i<3;i++){class C {#x=i;#m(){return this.#x}
                get #g(){return this.#m()+10} set #s(v){this.#x=v} read(){this.#s=this.#g;return this.#m()}
                method(){return this.#m}};classes.push(C)}
                const out=classes.map(C=>new C().read());
                out.push(new classes[0]().method()===new classes[1]().method());
                return JSON.stringify(out)})()
                """, """
                (()=>{const classes=[];for(let i=0;i<3;i++){const C=class {#x=i;#m(){return this.#x}
                get #g(){return this.#m()+10} set #s(v){this.#x=v} read(){this.#s=this.#g;return this.#m()}
                method(){return this.#m}};classes.push(C)}
                const out=classes.map(C=>new C().read());
                out.push(new classes[0]().method()===new classes[1]().method());
                return JSON.stringify(out)})()
                """, """
                (()=>{const classes=[];for(let i=0;i<3;i++){const C=class C {#x=i;#m(){return this.#x}
                get #g(){return this.#m()+10} set #s(v){this.#x=v} read(){this.#s=this.#g;return this.#m()}
                method(){return this.#m}};classes.push(C)}
                const out=classes.map(C=>new C().read());
                out.push(new classes[0]().method()===new classes[1]().method());
                return JSON.stringify(out)})()
                """,

                """
                        (()=>{const subs=[];for(let k=0;k<2;k++){class B{#b=k;#bm(){return this.#b}readBase(){return this.#bm()}}
                        class S extends B{#s=k+100;#sm(){return this.#s}readSub(){return this.#sm()+this.readBase()}}subs.push(S)}
                        return JSON.stringify(subs.map(S=>new S().readSub()))})()
                        """,
                """
                        (()=>{const classes=[];let i=0;while(i++<2)classes.push(class{#x=i;#m(){return this.#x}
                        static make(){return new this()}read(){return this.#m()}});
                        return JSON.stringify([classes[0].make().read(),classes[1].make().read()])})()
                        """,
                """
                        (()=>{const out=[];for(const k of [1,2]){const C=class{#m(){return k}static{out.push(new this().read())}read(){return this.#m()}};out.push(new C().read())}
                        return JSON.stringify(out)})()
                        """,
                """
                        (()=>{const classes=[];let i=0;while(i++<2){try{(class{#x=i;#m(){return this.#x}read(){return this.#m()}
                        static{classes.push(this);throw 0}})}catch{}}
                        return JSON.stringify(classes.map(C=>new C().read()))})()
                        """);
    }
}
