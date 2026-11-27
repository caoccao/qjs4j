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

package com.caoccao.qjs4j.core.error;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.vm.Bytecode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code error.stack} matches V8's, character for character: a {@code Name: message} first line, then one
 * {@code at name (file:line:column)} line per frame.
 * <p>
 * Every frame used to report line 1, the string had no first line and no columns, the eval pipeline's bookkeeping
 * frames were appended as {@code at <eval>}, and {@code Error.captureStackTrace} and {@code Error.stackTraceLimit} did
 * not exist. Each script runs in V8 under the resource name {@code test.js} and in qjs4j under the same file name, and
 * the two strings are compared. Native frames such as V8's {@code at Array.map (<anonymous>)} are not reproduced, so
 * the cases avoid them.
 */
public class JSErrorStackTraceTest extends BaseJavetTest {
    private void assertStackWithJavet(String... codeArray) throws JavetException {
        for (String code : codeArray) {
            v8Runtime.resetContext();
            String expected = v8Runtime.getExecutor(code).setResourceName("test.js").executeString();
            assertThat(resetContext().eval(code, "test.js", false).toString()).as(code).isEqualTo(expected);
        }
    }

    @Test
    public void testAnnexBFunctionDeclarationsInIfStatements() throws JavetException {
        // An Annex B `if (...) function f() {} else function g() {}` hoists both declarations away,
        // leaving absent statements behind, which the per-statement source position must tolerate.
        assertStackWithJavet("""
                if (true) function annex() { return 1 } else function annex2() { return 2 }
                typeof annex""",
                "(function () { eval('if (false) function f() {} else function g() {}'); return typeof g })()",
                "eval('if (true) function f() {} else function g() {} typeof f')",
                "(function () { eval('if (false) ; else function f() { return 1 } f()'); return typeof f })()",
                "eval('if (false) ; else function h() {} typeof h')");
    }

    @Test
    public void testAScriptWithoutAFileName() throws JavetException {
        String code = "function f() { return new Error('x').stack } f()";
        v8Runtime.resetContext();
        String expected = v8Runtime.getExecutor(code).executeString();
        assertThat(resetContext().eval(code, "", false).toString()).isEqualTo(expected);
    }

    @Test
    public void testCallSitesReportTheCalleePosition() throws JavetException {
        assertStackWithJavet("""
                function inner() {
                  const e = new Error('x');
                  return e;
                }
                function outer() {
                  return inner();
                }
                outer().stack""", """
                const o = { m() { return new Error('x').stack } };
                o.m()""", "var o = {}; try { o['x']() } catch (e) { e.stack }",
                "var o = {}; try { o.x() } catch (e) { e.stack }", "var f; try { f() } catch (e) { e.stack }", """
                        var x = { toString() { return new Error('x').stack } };
                        ({ toString() { return new Error('ts').stack } }) + ''""",
                "function lvl(n) { return n ? lvl(n - 1) : new Error('deep').stack } lvl(12)",
                "function f(a = new Error('d').stack) { return a } f()",
                "for (let i = 0; i < 1; i++) { var s = new Error('for').stack } s", "[new Error('arr').stack][0]");
    }

    @Test
    public void testCaptureStackTrace() throws JavetException {
        assertStackWithJavet("const o = {}; Error.captureStackTrace(o); o.stack", """
                const o = { message: 'mm', name: 'Nm' };
                function f() { Error.captureStackTrace(o, f) }
                function g() { f() }
                g();
                o.stack""", "const o = {}; function absent() {} Error.captureStackTrace(o, absent); o.stack",
                "const o = {}; Error.captureStackTrace(o, 42); o.stack.split('\\n').length + ''",
                "try { Error.captureStackTrace(1) } catch (e) { e.message }",
                "try { Error.captureStackTrace(Object.preventExtensions({})) } catch (e) { e.message }",
                "const e = new Error('x'); function f() { Error.captureStackTrace(e) } f(); e.stack",
                "const o = {}; Error.captureStackTrace(o); o.stack = 'set'; o.stack", """
                        const d = Object.getOwnPropertyDescriptor(Error, 'captureStackTrace');
                        [typeof d.value, d.writable, d.enumerable, d.configurable, d.value.length].join()""",
                "JSON.stringify(Object.getOwnPropertyDescriptor(Error, 'stackTraceLimit'))");
    }

    @Test
    public void testConstructorsAndSubclasses() throws JavetException {
        assertStackWithJavet("function F() { this.s = new Error('x').stack } new F().s",
                "class B { constructor() { this.s = new Error('x').stack } } new B().s",
                "class B { constructor() { this.s = new Error('x').stack } } class D extends B {} new D().s",
                "new (class Q { constructor() { return { s: new Error('anonc').stack } } })().s",
                // An error subclass's own constructor frames are left out, as V8 does.
                """
                        class MyErr extends Error { constructor(m) { super(m); this.name = 'MyErr' } }
                        new MyErr('m').stack""",
                "class E2 extends TypeError {} function mk() { return new E2('t') } mk().stack",
                "function F() { return Reflect.construct(Error, ['y'], F).stack } F()",
                "Reflect.construct(Error, ['x'], function F() {}).stack");
    }

    @Test
    public void testConstructorWithoutAnyName() {
        // V8 names this constructor by inference from the source, `Object.create.constructor`; qjs4j does
        // not infer names, so it goes on up the prototype chain.
        assertThat(context.eval("""
                const o = Object.create({ constructor: (() => function () {})() });
                o.m = function m() { return new Error('x').stack };
                o.m()""", "test.js", false).toString()).isEqualTo("""
                Error: x
                    at Object.m (test.js:2:29)
                    at test.js:3:3""");
    }

    @Test
    public void testEvalFrames() throws JavetException {
        assertStackWithJavet("function outer() { return eval(\"new Error('x').stack\") } outer()",
                "function outer() { return eval(\"(function inner() { return new Error('y').stack })()\") } outer()",
                "function outer() { return eval(\"eval('new Error(1).stack')\") } outer()",
                "var o = { m() { return eval(\"new Error('m').stack\") } }; o.m()", "eval('new Error(\"top\").stack')");
    }

    @Test
    public void testFailuresWhileFormattingTheFirstLine() throws JavetException {
        assertStackWithJavet("""
                const e = new Error('x');
                Object.defineProperty(e, 'name', { get() { throw new Error('boom') } });
                try { e.stack } catch (x) { 'threw ' + x.message }""", """
                const e = new Error('x');
                e.name = { toString() { throw new Error('name') } };
                try { e.stack } catch (x) { 'threw ' + x.message }""", """
                const o = { message: { toString() { throw new Error('message') } } };
                Error.captureStackTrace(o);
                try { o.stack } catch (x) { 'threw ' + x.message }""",
                // Describing the exception fails as well.
                """
                        const e = new Error('x');
                        Object.defineProperty(e, 'name', { get() { throw Object.create(null) } });
                        try { e.stack } catch (x) { 'threw' }""", """
                        const e = new Error('x');
                        Object.defineProperty(e, 'name', { get() { throw 'primitive' } });
                        try { e.stack } catch (x) { 'threw' }""",
                "try { Error.captureStackTrace() } catch (e) { e.message }",
                "const e = new Error('x'); Object.getOwnPropertyDescriptor(e, 'stack').set.call(e); String(e.stack)");
    }

    @Test
    public void testFrameNames() throws JavetException {
        assertStackWithJavet("""
                function a() { return new Error('x').stack }
                a()""", """
                class A { static s() { return new Error('x').stack } get g() { return new Error('y').stack } }
                A.s() + '\\n' + new A().g""", "const f = function named() { return new Error('x').stack }; f()",
                "const obj = { f: function () { return new Error('x').stack } }; obj.f()",
                "var f = function () { return new Error('anon').stack }; f()",
                "function f() { return new Error('x').stack } f.call({})",
                "function f() { return new Error('x').stack } f.call(new Map())",
                "function f() { 'use strict'; return new Error('x').stack } f()", "(() => new Error('arrow').stack)()",
                "(function () { 'use strict'; return new Error('iife').stack })()",
                "const o = { get ['comp' + 'uted']() { return new Error('c').stack } }; o.computed",
                "class C { static field = new Error('sf').stack } C.field",
                "class C { static { globalThis.sb = new Error('sb').stack } } sb",
                "function* g() { yield new Error('g').stack } const it = g(); it.next().value.split('\\n')[1]",
                "function f() { return new Error('x').stack } delete f.name; f()",
                "function outer() { return eval(\"new Error('x').stack\") } delete outer.name; outer()",
                "(class { static s() { return new Error('x').stack } }).s()",
                "const o = Object.create(null); o.f = function f() { return new Error('x').stack }; o.f()", """
                        const o = Object.create({ constructor: function () {} });
                        o.m = function m() { return new Error('x').stack };
                        o.m()""", """
                        function C() {}
                        delete C.name;
                        const o = new C();
                        o.m = function m() { return new Error('x').stack };
                        o.m()""",
                "const K = (() => class {})(); K.s = function s() { return new Error('x').stack }; K.s()");
    }

    @Test
    public void testHeaderIsComputedOnFirstRead() throws JavetException {
        // V8 reads the name when stack is first read, but keeps the message the error was created with.
        assertStackWithJavet("const e = new Error('x'); e.message = 'y'; e.stack",
                "class MyErr extends Error { constructor(m) { super(m); this.message = 'changed' } } new MyErr('m').stack",
                "const e = new Error('x'); Object.defineProperty(e, 'message', { get() { return 'getter' } }); e.stack",
                "const o = { message: 'mm', name: 'Nm' }; Error.captureStackTrace(o); o.name = 'Later'; o.stack",
                "const o = Object.create({ message: 'inherited' }); Error.captureStackTrace(o); o.stack",
                "const o = { name: '' , message: 'only message' }; Error.captureStackTrace(o); o.stack",
                "const o = Object.create(new Proxy({}, {})); Error.captureStackTrace(o); o.stack",
                "const e = new Error('x'); e.name = 'Renamed'; e.stack",
                "const e = new Error('x'); void e.stack; e.name = 'Late'; e.stack",
                "const e = new Error('x'); e.stack = 'custom'; e.stack", "new TypeError('').stack",
                "new Error(undefined).stack", "String(Object.create(Error.prototype).stack)",
                "const e = new Error('m'); JSON.stringify([Object.keys(e), JSON.stringify(e)])", """
                        const d = Object.getOwnPropertyDescriptor(new Error('a'), 'stack');
                        [
                          typeof d.get, typeof d.set, d.enumerable, d.configurable, d.get.name, d.get.length,
                          d.set.length, d.get === Object.getOwnPropertyDescriptor(new Error('b'), 'stack').get,
                        ].join(',')""", """
                        const g = Object.getOwnPropertyDescriptor(new Error('a'), 'stack').get;
                        String(g.call({})) + ',' + String(g.call(1))""", """
                        const s = Object.getOwnPropertyDescriptor(new Error('a'), 'stack').set;
                        const o = {};
                        s.call(o, 'v');
                        s.call(1, 'v');
                        String(o.stack)""");
    }

    @Test
    public void testHostEvalAndClosedContexts() {
        // An eval the host runs, with no script frame calling it, is a script in its own right.
        assertThat(context.evalIndirect("new Error('host').stack", "host.js").toString())
                .isEqualTo("Error: host\n    at host.js:1:1");
        // A context that has been closed has released its intrinsics; an error it is still asked for gets
        // the default limit.
        JSContext closed = createRuntime().createContext();
        closed.close();
        JSError error = closed.throwError("after close");
        assertThat(closed.getStackFramesForDiagnostics(error)).isEmpty();
    }

    @Test
    public void testRuntimeErrorsReportTheFailingPosition() throws JavetException {
        assertStackWithJavet("try { undefined.foo } catch (e) { e.stack }", "try { null[0] } catch (e) { e.stack }",
                "try { null [ 0 ] } catch (e) { e.stack }", "var o = {}; try { o.a.b } catch (e) { e.stack }", """
                        function thrower() { throw new TypeError('bad') }
                        try { thrower() } catch (e) { e.stack }""", "try { x } catch (e) { e.stack }",
                "function f() { notDefined } try { f() } catch (e) { e.stack }",
                "function f(o) { return o.a.b } try { f({}) } catch (e) { e.stack }",
                "function f() { let x = x } try { f() } catch (e) { e.stack }",
                "function f(a = b) { } try { f() } catch (e) { e.stack }",
                "function f() { ({}).x.y } try { f() } catch (e) { e.stack }",
                "function f() { throw 1 } try { f() } catch (e) { String(e.stack) }", """
                          let s;
                          try {
                            (function deep() { throw new RangeError('r') })();
                          } catch (e) { s = e.stack }
                          s
                        """);
    }

    @Test
    public void testStackTraceLimit() throws JavetException {
        assertStackWithJavet(
                "Error.stackTraceLimit = 1; function a() { return b() } function b() { return new Error('lim').stack } a()",
                "Error.stackTraceLimit = 0; new Error('zero').stack",
                "Error.stackTraceLimit = -5; new Error('negative').stack",
                "Error.stackTraceLimit = NaN; new Error('nan').stack",
                "Error.stackTraceLimit = Infinity; function a() { return new Error('inf').stack } a()",
                "Error.stackTraceLimit = 2.9; function a() { return b() } function b() { return new Error('f').stack } a()",
                "Error.stackTraceLimit = 'x'; String(new Error('nan').stack) + ' ' + Object.hasOwn(new Error('n'), 'stack')",
                "delete Error.stackTraceLimit; String(new Error('deleted').stack)");
    }

    @Test
    public void testTheEngineReadsItsOwnStorage() {
        // The diagnostics path a host uses runs no script and is empty for an object with no trace.
        JSError error = (JSError) context.eval("new Error('diagnostic')", "test.js", false);
        assertThat(context.getStackFramesForDiagnostics(error)).isEqualTo("    at test.js:1:1");
        assertThat(context.getStackFramesForDiagnostics(context.createJSObject())).isNull();
        JSObject captured = (JSObject) context.eval("const o = {}; Error.captureStackTrace(o); o", "test.js", false);
        assertThat(context.getStackFramesForDiagnostics(captured)).isEqualTo("    at test.js:1:21");

        // A function that recorded no source position — bytecode built by hand — prints its file alone.
        Bytecode bytecode = new Bytecode(new byte[0], JSValue.NO_ARGS, new String[0], 0);
        assertThat(bytecode.getSourcePosition(0)).isNull();
        Bytecode compiled = new Compiler("""
                1;
                new Error('x');""", "test.js").setContext(context).compile(false).function().getBytecode();
        assertThat(compiled.getSourcePositions()).isNotEmpty();
        assertThat(compiled.getSourcePosition(0)).containsExactly(1, 1);
    }

    @Test
    public void testThrownErrorsPrintTheirStackInTheConsole() {
        JSValue error = context.eval("function boom() { return new Error('printed') } boom()", "test.js", false);
        assertThat(context.getJSGlobalObject().getConsole().formatValue(context, error)).isEqualTo("""
                Error: printed
                    at boom (test.js:1:26)
                    at test.js:1:49""");
        JSValue plain = context.eval("const e = new Error('no stack'); e.stack = ''; e", "test.js", false);
        assertThat(context.getJSGlobalObject().getConsole().formatValue(context, plain)).isEqualTo("Error: no stack");
        JSValue limited = context.eval("Error.stackTraceLimit = 'off'; new Error('undefined stack')", "test.js", false);
        assertThat(context.getJSGlobalObject().getConsole().formatValue(context, limited))
                .isEqualTo("Error: undefined stack");
    }
}
