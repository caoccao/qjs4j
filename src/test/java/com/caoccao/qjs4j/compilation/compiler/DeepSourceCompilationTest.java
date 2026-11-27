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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Source that is long or deeply nested must never end an evaluation with a raw {@link StackOverflowError}.
 * <p>
 * The parser and the bytecode compiler are recursive descent, and nothing outside the interpreter loop converted a Java
 * stack overflow into a JavaScript error. Two different shapes of input reached that gap:
 * <ul>
 * <li>Long left-associative chains — {@code 1 + 1 + ...}, {@code a || b || ...} — which the parser builds with a loop
 * but which the compiler walked recursively, one Java frame per operand. V8 evaluates them at any length, so they now
 * compile with a loop too.</li>
 * <li>Genuinely nested source — parentheses, ternaries, member chains — which V8 also refuses, with
 * {@code RangeError: Maximum call stack size exceeded}. That is now what qjs4j reports, catchably, instead of a
 * {@code java.lang.StackOverflowError} escaping {@code JSContext.eval()}.</li>
 * </ul>
 */
public class DeepSourceCompilationTest extends BaseJavetTest {
    private static final int CHAIN_LENGTH = 100_000;
    private static final int NESTING_DEPTH = 100_000;
    @TempDir
    Path moduleDirectory;

    @Test
    public void testDeeplyNestedSourceIsACatchableRangeError() {
        assertErrorWithJavet("(".repeat(NESTING_DEPTH) + "1" + ")".repeat(NESTING_DEPTH),
                "true ? ".repeat(NESTING_DEPTH) + "1" + " : 0".repeat(NESTING_DEPTH),
                "[".repeat(NESTING_DEPTH) + "1" + "]".repeat(NESTING_DEPTH));
        assertThatThrownBy(() -> new Compiler("(".repeat(NESTING_DEPTH) + "1" + ")".repeat(NESTING_DEPTH), "parens.js")
                .parse(false)).isInstanceOf(JSRangeErrorException.class).hasMessage("Maximum call stack size exceeded");
    }

    @Test
    public void testDeeplyNestedSourceThroughEvalAndFunctionIsCaughtByTheScript() {
        // The script's own catch sees the RangeError: the overflow happens inside a native call made
        // by an opcode handler, so it must surface as an ordinary engine error in the same frame.
        assertStringWithJavet("""
                const deep = '('.repeat(100_000) + '1' + ')'.repeat(100_000);
                try { eval(deep); 'no error' } catch (e) { e.name + ': ' + e.message }
                """, """
                const deep = '('.repeat(100_000) + '1' + ')'.repeat(100_000);
                try { new Function('return ' + deep); 'no error' } catch (e) { e.name + ': ' + e.message }
                """, """
                const deep = '('.repeat(100_000) + '1' + ')'.repeat(100_000);
                try { (0, eval)(deep); 'no error' } catch (e) { e.name + ': ' + e.message }
                """);
    }

    @Test
    public void testDeepMemberChainIsARangeErrorRaisedByTheCompiler() {
        // The parser builds a member chain with a loop; the compiler recursion is what overflows.
        String source = "var x = {}; x.a = x; x" + ".a".repeat(4 * NESTING_DEPTH);
        assertErrorWithJavet(source);
        assertThatThrownBy(() -> new Compiler(source, "member.js").compile(false))
                .isInstanceOf(JSRangeErrorException.class).hasMessage("Maximum call stack size exceeded");
    }

    @Test
    public void testDynamicImportOfADeeplyNestedModuleRejectsWithARangeError() throws IOException {
        Files.writeString(moduleDirectory.resolve("deep.mjs"),
                "export default " + "(".repeat(NESTING_DEPTH) + "1" + ")".repeat(NESTING_DEPTH) + ";\n");
        Path entry = moduleDirectory.resolve("entry.mjs");
        String entrySource = """
                globalThis.result = 'pending';
                import('./deep.mjs').then(
                  () => { globalThis.result = 'resolved'; },
                  e => { globalThis.result = e.name + ': ' + e.message; });
                """;
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = createRuntime(); JSContext moduleContext = runtime.createContext()) {
            moduleContext.eval(entrySource, entry.toString(), true);
            moduleContext.processMicrotasks();
            assertThat(moduleContext.eval("globalThis.result").toString())
                    .isEqualTo("RangeError: Maximum call stack size exceeded");
        }
    }

    @Test
    public void testLongChainsInDefaultParametersAreAnalysedWithoutRecursion() {
        // validateArrowParameters asks every default whether it contains yield or await; the
        // answer used to be computed by recursing down the left spine of the chain.
        String sum = "1 + ".repeat(CHAIN_LENGTH - 1) + "1";
        assertIntegerWithJavet("((a = " + sum + ") => a)()", "(async (a = " + sum + ") => a)()");
        assertBooleanWithJavet("""
                const sum = '1 + '.repeat(99_999) + '1';
                try { eval(`async function f() { (a = ${sum} + await 1) => a }`); false }
                catch (e) { e instanceof SyntaxError }""", """
                const sum = '1 + '.repeat(99_999) + '1';
                try { eval(`async function f() { (a = (await 1) + ${sum}) => a }`); false }
                catch (e) { e instanceof SyntaxError }""", """
                const sum = '1 + '.repeat(99_999) + '1';
                try { eval(`function* g() { (a = ${sum} + (yield)) => a }`); false }
                catch (e) { e instanceof SyntaxError }""");
        // Direct eval of `var arguments` in a parameter default is detected through the same walk.
        assertStringWithJavet("""
                try { ((a = %s + eval('var arguments = 1; 0'), b = typeof arguments) => b)() }
                catch (e) { e.name }""".formatted(sum));
    }

    @Test
    public void testLongLeftAssociativeChainsEvaluate() {
        assertIntegerWithJavet("1 + ".repeat(CHAIN_LENGTH - 1) + "1",
                "1 - ".repeat(CHAIN_LENGTH - 1) + "1" + " + " + (CHAIN_LENGTH - 1) * 2,
                "1 * ".repeat(CHAIN_LENGTH - 1) + "1", "0 || ".repeat(CHAIN_LENGTH - 1) + "0" + " || 7",
                "1 && ".repeat(CHAIN_LENGTH - 1) + "1", "null ?? ".repeat(CHAIN_LENGTH - 1) + "null" + " ?? 3",
                "(" + "'a' + ".repeat(CHAIN_LENGTH - 1) + "'a'" + ").length",
                "var x = 2; " + "x | ".repeat(CHAIN_LENGTH - 1) + "x",
                "1" + " + 2 * 3 - 4 / 5 % 6 ** 1 | 7 & 8 ^ 9 << 1 >> 1 >>> 1".repeat(CHAIN_LENGTH / 10));
        assertBooleanWithJavet("var o = {a: 1}; " + "'a' in o && ".repeat(CHAIN_LENGTH - 1) + "'a' in o",
                "var o = {}; " + "o instanceof Object && ".repeat(CHAIN_LENGTH - 1) + "o instanceof Object");
        // V8 compiles comparison chains recursively and gives up on them with a RangeError at about
        // ten thousand operands on Linux and macOS, and already at five thousand on Windows; that is
        // a V8 limit, not semantics. Compare at a length every platform's V8 accepts, then check
        // that qjs4j has no such limit.
        String shortEquality = String.join(" == ", Collections.nCopies(1_000, "1")) + " == true";
        String shortStrictEquality = String.join(" === ", Collections.nCopies(1_000, "1"));
        String shortLessThan = String.join(" < ", Collections.nCopies(1_000, "1")) + " < 2";
        assertBooleanWithJavet(shortEquality, shortStrictEquality, shortLessThan);
        assertEvalValue("1 == ".repeat(CHAIN_LENGTH - 1) + "1" + " == true", JSBoolean.TRUE);
        assertEvalValue("1 === ".repeat(CHAIN_LENGTH - 1) + "1", JSBoolean.FALSE);
        assertEvalValue("1 < ".repeat(CHAIN_LENGTH - 1) + "1" + " < 2", JSBoolean.TRUE);
    }

    @Test
    public void testLongModuleSpecifiersAndIdentifiersDoNotOverflowTheModuleScanner() {
        // The module rewriter's regular expressions repeated an alternation greedily, which Java
        // matches by recursion: one frame per character of a specifier or binding name.
        String longName = "a".repeat(200_000);
        assertThatThrownBy(() -> context.eval("import x from './" + longName + "';", "long.mjs", true))
                .isInstanceOfSatisfying(JSException.class, e -> assertThat(e.getMessage())
                        .startsWith("TypeError: Cannot find module './" + longName.substring(0, 10)));
        assertThatThrownBy(() -> resetContext().eval("import '" + "\\\\a".repeat(100_000) + "';", "long.mjs", true))
                .isInstanceOfSatisfying(JSException.class,
                        e -> assertThat(e.getMessage()).startsWith("TypeError: Cannot find module '\\a\\a"));
        assertThatThrownBy(
                () -> resetContext().eval("import * as " + longName + " from './missing.mjs';", "long.mjs", true))
                .isInstanceOfSatisfying(JSException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("TypeError: Cannot find module './missing.mjs'"));
        moduleMode = true;
        assertStringWithJavet("export class " + "A".repeat(200_000) + " {}\nString('ok');",
                "export function " + "f".repeat(200_000) + "() {}\nString('ok');");
    }

    @Test
    public void testLongShortCircuitChainsKeepTailCallsAndShortCircuiting() {
        // Every operand but the last is a left operand of a short-circuit node, so none of them is in
        // tail position; the last one is, in strict mode. The counter proves the right operands that
        // must not run do not.
        assertIntegerWithJavet(
                "var n = 0; function hit() { n++; return 0; } " + "hit() || ".repeat(CHAIN_LENGTH - 1) + "hit()"
                        + "; n",
                "var n = 0; function hit() { n++; return 0; } " + "hit() && ".repeat(CHAIN_LENGTH - 1) + "hit()"
                        + "; n",
                "var n = 0; function hit() { n++; return null; } " + "hit() ?? ".repeat(CHAIN_LENGTH - 1) + "hit()"
                        + "; n",
                "function f(k) { return k === 0 ? 5 : " + "0 || ".repeat(CHAIN_LENGTH - 1) + "0"
                        + " || f(k - 1); } f(3)");
    }

    @Test
    public void testPrivateInAtTheBottomOfAChain() {
        assertBooleanWithJavet("class C { #x; static m(o) { return #x in o === true && #x in o; } } C.m(new C())",
                "class C { #x; static m(o) { return #x in o || #x in o; } } C.m({})");
    }

}
