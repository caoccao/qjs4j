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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An assignment to a local or captured variable reads and writes the variable's slot.
 * <p>
 * Every such assignment built a reference object — a {@code JSObject} with an accessor backed by two new native
 * functions — so {@code s += i} in a loop allocated three objects an iteration. The direct form must keep every
 * observable rule of the reference form: evaluation order, the temporal dead zone, function names, and the binding a
 * sloppy direct {@code eval} on the right-hand side must not redirect.
 */
public class DirectBindingAssignmentTest extends BaseJavetTest {
    @Test
    public void testDirectEvalKeepsReferenceSemantics() {
        // test262 S11.13.1_A6_T1 and S11.13.2_A6.*: the assignment goes to the binding resolved before the
        // right-hand side declared a nearer one. V8 assigns to the nearer one, against the specification, so these
        // are asserted directly rather than against V8.
        assertEvalText("""
                function testAssignment() {
                  var x = 0;
                  var innerX = (function () {
                    x = (eval('var x;'), 1);
                    return x;
                  })();
                  return innerX + ':' + x;
                }
                testAssignment()""", "undefined:1");
        assertEvalText("""
                function testCompound() {
                  var x = 1;
                  var innerX = (function () {
                    x += (eval('var x = 2;'), 3);
                    return x;
                  })();
                  return innerX + ':' + x;
                }
                testCompound()""", "2:4");
        assertThat(usesReferences("function f() { var x = 0; (function () { x = (eval('var x;'), 1) })() }")).isTrue();
        assertThat(usesReferences("function f() { let s = 0; for (let i = 0; i < 3; i++) { s += i } return s }"))
                .isFalse();
        assertThat(usesReferences("function f() { let s = 0; return () => { s = s * 2 + 1; return s } }")).isFalse();
    }

    @Test
    public void testNamingWithAndEscapedEval() {
        assertStringWithJavet("""
                (function () {
                  let a, b, c, d, e = 1, f = 2;
                  a = class {};
                  b = class Named {};
                  (c) = class {};
                  d = function () {};
                  e += 2;
                  f **= 3;
                  (d) = function () {};
                  return [a.name, b.name, c.name, d.name, e, f].join();
                })()""", """
                new Function('let x = 1; const o = { x: 5 }; with (o) { x = 2; } return [x, o.x].join();')()""",
                """
                        new Function('let x = 1; const o = {}; with (o) { (function () { x = 3; })(); } return String(x);')()""",
                """
                        (function () { var x = 1; \\u0065val('x = 5'); return String(x); })()""");
        // A function expression's own name is immutable: a compound assignment to it is ignored, or throws in strict
        // code.
        assertStringWithJavet("""
                (function g() { try { g += 1; } catch (e) { return e.name; } return typeof g; })()""");
        // Without the source, a function may contain a direct eval as far as the compiler can tell.
        assertThat(CompilerContext.sourceMayContainDirectEval(null)).isTrue();
        assertThat(CompilerContext.sourceMayContainDirectEval("x = 1")).isFalse();
        assertThat(CompilerContext.sourceMayContainDirectEval("\\u0065val(s)")).isTrue();
    }

    @Test
    public void testSemanticsMatchV8() {
        assertStringWithJavet("""
                function f() { let s = 0; for (let i = 0; i < 10; i++) { s += i; s -= 1; s *= 2; s /= 2 } return s }
                f() + ''""", """
                function f() { var log = []; var x = 1; x = (log.push(x), x + 1); log.push(x); return log.join() }
                f()""", """
                function f() { let a, b, c; a = b = c = 5; return [a, b, c].join() }
                f()""",
                """
                        function f() { var g = function () {}; var h; h = function () {}; let k; k = class {}; return [g.name, h.name, k.name].join() }
                        f()""",
                """
                        function counter() { let n = 0; return () => { n += 1; n = n * 1; return n } }
                        var c = counter(); c(); c(); c() + ''""",
                """
                        function f() { try { x = 1; let x; } catch (e) { return e.constructor.name + ': ' + e.message } }
                        f()""",
                """
                        function f() { try { x += 1; let x = 0; } catch (e) { return e.constructor.name + ': ' + e.message } }
                        f()""",
                """
                        function f() { var order = []; try { x = (order.push('rhs'), 1); let x; } catch (e) { order.push(e.name) } return order.join() }
                        f()""",
                """
                        function f() {
                          let n = 0;
                          const inc = () => { try { m = n; } catch (e) { return e.name } let m; };
                          return inc();
                        }
                        f()""", """
                        function f() { let s = 'a'; s += 'b'; s **= 1; return typeof s + s }
                        f()""",
                """
                        function f() { let x = 1; x <<= 3; x >>= 1; x >>>= 1; x &= 7; x |= 8; x ^= 3; x %= 5; return x + '' }
                        f()""");
        assertErrorWithJavet("function f() { const c = 1; c = 2 } f()", "function f() { const c = 1; c += 2 } f()");
    }

    private boolean usesReferences(String source) {
        Bytecode bytecode = new Compiler(source, "test.js").setContext(context).compile(false).function().getBytecode();
        return containsOpcode(bytecode, Opcode.MAKE_LOC_REF) || containsOpcode(bytecode, Opcode.MAKE_VAR_REF_REF);
    }

    private static boolean containsOpcode(Bytecode bytecode, Opcode opcode) {
        for (Object constant : bytecode.getConstants()) {
            if (constant instanceof com.caoccao.qjs4j.core.JSBytecodeFunction function
                    && containsOpcode(function.getBytecode(), opcode)) {
                return true;
            }
        }
        for (Opcode decoded : bytecode.getDecodedOpcodes()) {
            if (decoded == opcode) {
                return true;
            }
        }
        return false;
    }
}
