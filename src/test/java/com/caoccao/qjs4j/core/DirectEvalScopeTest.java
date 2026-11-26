package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DirectEvalScopeTest extends BaseJavetTest {
    @Test
    void displacedBindingsKeepTheirWriteRestrictions() {
        for (String assignment : new String[]{"x = 7", "x += 7", "x++", "[x] = [7]", "({x} = {x: 7})"}) {
            assertStringWithJavet("""
                    (() => {
                        function outer() {
                            const x = 1;
                            return eval(`
                                const write = () => {
                                    try { %s; return 'accepted'; }
                                    catch (error) { return error.name; }
                                };
                                const during = (function () {
                                    let x = 2;
                                    return eval('JSON.stringify([write(), x])');
                                })();
                                JSON.stringify([during, write(), x]);
                            `);
                        }
                        return outer();
                    })()
                    """.formatted(assignment));
        }
        assertStringWithJavet("""
                (() => {
                    function outer() {
                        let x = 1;
                        const during = eval(`
                            const write = () => { x = 8; return x; };
                            (function () {
                                const x = 2;
                                return eval('JSON.stringify([write(), x])');
                            })();
                        `);
                        return JSON.stringify([during, x]);
                    }
                    return outer();
                })()
                """, """
                (function named() {
                    return eval(`
                        const write = () => {
                            try { named = 7; return typeof named; }
                            catch (error) { return error.name; }
                        };
                        const during = (function () {
                            let named = 2;
                            return eval('JSON.stringify([write(), named])');
                        })();
                        JSON.stringify([during, write(), typeof named]);
                    `);
                })()
                """);
    }

    @Test
    void evalResolvesRemappedAndTemplatePrivateSymbols() {
        JSBytecodeFunction caller = (JSBytecodeFunction) context.eval("(function caller() {})");
        EvalRunner runner = new EvalRunner(context, new ModuleSourceTransformer(context));
        JSSymbol template = new JSSymbol("#field");
        JSSymbol active = new JSSymbol("#field");
        JSSymbol inherited = new JSSymbol("#outer");
        caller.setClassPrivateSymbols(List.of(template, inherited, new JSSymbol("ordinary"), new JSSymbol("#")));
        assertThat(runner.collectEvalPrivateSymbols(caller)).isEqualTo(Map.of("field", template, "outer", inherited));

        IdentityHashMap<JSSymbol, JSSymbol> remap = new IdentityHashMap<>();
        remap.put(template, active);
        remap.put(inherited, null);
        caller.setClassPrivateSymbolRemap(remap);
        assertThat(runner.collectEvalPrivateSymbols(caller)).isEqualTo(Map.of("field", active, "outer", inherited));

        remap.clear();
        remap.put(new JSSymbol("ordinary"), active);
        assertThat(runner.collectEvalPrivateSymbols(caller)).isEqualTo(Map.of("field", template, "outer", inherited));
    }

    @Test
    void evalUsesThePrivateNamesOfItsClassEvaluation() {
        assertStringWithJavet("""
                (() => {
                  function make(value) {
                    return new class {
                      #value = value;
                      read(other) { return eval('other.#value'); }
                    };
                  }
                  const first = make(1), second = make(2);
                  try { first.read(second); }
                  catch (error) { return [first.read(first), second.read(second), error.name].join('|'); }
                  return 'accepted a different class';
                })()
                """);
    }

    @Test
    void nestedEvalOverlaysKeepEachEnvironmentsOwnBinding() {
        assertStringWithJavet(
                """
                        (()=>{function f(){let x=1;return eval('globalThis.read=()=>x;(function(){let x=2;return eval("read()")})()')}
                        return String(f())})()
                        """,
                """
                        (()=>{function f(){let x=1;const inner=eval('globalThis.write=v=>{x=v};(function(){let x=2;eval("write(7)");return x})()');
                        return [inner,x]}return JSON.stringify(f())})()
                        """,
                """
                        (()=>{function f(){let x=1;const inner=eval('globalThis.assign=()=>{[x]=[8]};(function(){let x=2;eval("assign()");return x})()');
                        return [inner,x]}return JSON.stringify(f())})()
                        """,
                """
                        (()=>{function f(){let x=1;let y=10;return eval('globalThis.read2=()=>[x,y];(function(){let x=2;return JSON.stringify(eval("read2()"))})()')}
                        return f()})()
                        """,
                """
                        (()=>{function f(){let x=1;const out=eval('globalThis.bump=()=>{x+=10;return x};(function(){let x=2;const a=eval("bump()");x+=100;return [a,x,eval("bump()")]})()');
                        return JSON.stringify([out,x])}return f()})()
                        """,
                """
                        (()=>{function f(){let x=1;return eval('const read=()=>x;(function(r){let x=2;return eval("r()")})(read)')}
                        return String(f())})()
                        """,
                """
                        (()=>{function f(){let x=1;return eval('(function(){let x=2;const readInner=()=>x;return String(eval("readInner()"))+x})()+x')}
                        return f()})()
                        """);
    }

    @Test
    void nestedEvalResolvesTheNearestDisplacedValue() {
        assertStringWithJavet("""
                (() => {
                    function outer() {
                        let x = 1;
                        const values = eval(`
                            const readOuter = () => x;
                            const writeOuter = () => ++x;
                            (function () {
                                let x = 20;
                                return eval(\"const readMiddle = () => x;\"
                                    + \"(function () { let x = 300;\"
                                    + \"return eval('JSON.stringify([readOuter(), readMiddle(), writeOuter(), x])');\"
                                    + \"})()\");
                            })();
                        `);
                        return JSON.stringify([values, x]);
                    }
                    return outer();
                })()
                """);
    }

    @Test
    void nestedEvalRestoresShadowedGlobalVar() {
        assertStringWithJavet("""
                var shadowed = 'global';
                function readNestedScope() {
                    'use strict';
                    var shadowed = 'function';
                    const inner = eval("var shadowed = 'eval'; eval('shadowed')");
                    return [inner, shadowed];
                }
                const readings = readNestedScope();
                const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'shadowed');
                JSON.stringify([readings, descriptor.value, descriptor.configurable, descriptor.writable]);
                """);
    }
}
