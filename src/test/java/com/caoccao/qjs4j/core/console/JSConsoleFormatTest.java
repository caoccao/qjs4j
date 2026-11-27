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

package com.caoccao.qjs4j.core.console;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code console.log} substitutes format specifiers and prints {@code -0} as Node does.
 * <p>
 * The format string used to be printed verbatim with the arguments after it, and {@code -0} printed as {@code 0}. The
 * expected lines are Node v24's output for the same calls; Node's string quoting and multi-line layout are not
 * reproduced, so the cases avoid quoted strings inside inspected values.
 */
public class JSConsoleFormatTest extends ConsoleOutputTestBase {

    private void assertOutput(String code, String expected) {
        assertThat(log(code)).as(code).isEqualTo(expected);
    }

    private String log(String code) {
        outStream.reset();
        errStream.reset();
        context.eval(code);
        return out() + err();
    }

    @Test
    public void testFormatSpecifiers() {
        assertOutput("console.log('%s=%d', 'a', 1)", "a=1\n");
        assertOutput("console.log('%i|%f|%j|%o|%O|%%|%c', 3.7, 2.5, {x: 1}, [1], {y: 2}, 'color: red')",
                "3|2.5|{\"x\":1}|[ 1, [length]: 1 ]|{ y: 2 }|%|\n");
        assertOutput("console.log('%s', -0, 'extra', 5)", "-0 extra 5\n");
        assertOutput("console.log('%d', '42abc', '%s')", "NaN %s\n");
        assertOutput("console.log('100%', 'done')", "100% done\n");
        assertOutput("console.log('%s %s', 'only')", "only %s\n");
        assertOutput("console.log('%x', 1)", "%x 1\n");
        assertOutput("console.log('%s', { toString() { return 'T' } })", "T\n");
        assertOutput("console.log('%s', [1, 2], Symbol('q'))", "[ 1, 2 ] Symbol(q)\n");
        assertOutput("console.log('%d', 12n, '%i', 1.9)", "12n %i 1.9\n");
        assertOutput("console.log(5, '%s', 'x')", "5 %s x\n");
        assertOutput("console.log('%s', null, undefined, NaN, -Infinity)", "null undefined NaN -Infinity\n");
        assertOutput("console.log('%d %i %f', -0, -0, -0)", "-0 0 0\n");
        assertOutput("var c = {}; c.self = c; console.log('%j', c)", "[Circular]\n");
        assertOutput("console.log('end%')", "end%\n");
        assertOutput("console.log('%%s', 1)", "%s 1\n");
        assertOutput("console.log('%s')", "%s\n");
        assertOutput("console.log('%d', { valueOf() { return 7 } })", "7\n");
        assertOutput("console.log('%i', '0x1F')", "31\n");
        assertOutput("console.log('%f', '1.5e3x')", "1500\n");
        assertOutput("class K { toString() { return 'K!' } } console.log('%s', new K())", "K!\n");
        assertOutput("console.log('%s', function f() {})", "function f() {}\n");
        assertOutput("console.log('%c styled %s', 'color: red', 'x')", " styled x\n");
        assertOutput("console.log('%j', undefined)", "undefined\n");
        assertOutput("console.log('%d %i %f', Symbol(), Symbol(), Symbol())", "NaN NaN NaN\n");
        assertOutput("console.log('%i', 7n)", "7n\n");
    }

    @Test
    public void testMoreSpecifiersAsNodePrintsThem() {
        assertOutput("console.log('', 1)", " 1\n");
        assertOutput("console.log('%s %s', 10n, Symbol('s'))", "10n Symbol(s)\n");
        assertOutput("console.log('%i %f %i', 10n, 10n, '42.9px')", "10n 10 42\n");
        assertOutput("console.log(new Float64Array([1.5, -0, NaN, 2]))", "Float64Array(4) [ 1.5, -0, NaN, 2 ]\n");
        assertOutput("console.log('%o', [1, 2])", "[ 1, 2, [length]: 2 ]\n");
        assertOutput("console.log('%o', 'str')", "\"str\"\n");
        // A toString inherited from a plain prototype object is the object's own conversion.
        assertOutput("console.log('%s', Object.create({ toString() { return 'P'; } }))", "P\n");
        // Node prints "[Object: null prototype] {}" and "Plain {}"; the layout of objects is not reproduced.
        assertOutput("console.log('%s', Object.create(null))", "{  }\n");
        assertOutput("console.log('%s', new (class Plain {})())", "{  }\n");
        // A prototype chain with no own toString, reachable only through a proxy (Node throws a TypeError here).
        assertThat(log("""
                const proxied = new Proxy(Object.create(null), { get: () => function () { return 'p'; } });
                console.log('%s', Object.create(proxied))""")).isEqualTo("{  }\n");
    }

    @Test
    public void testNegativeZeroAndOtherStreams() {
        assertOutput("console.log(-0)", "-0\n");
        assertOutput("console.log([-0, 0, 1])", "[ -0, 0, 1 ]\n");
        assertOutput("console.log({ a: -0 })", "{ a: -0 }\n");
        assertOutput("console.log(1, 'a', [-0], new Float64Array([-0, 1.5]))",
                "1 a [ -0 ] Float64Array(2) [ -0, 1.5 ]\n");
        assertOutput("console.info('info %s', 'i')", "info i\n");
        assertOutput("console.warn('warn %d', 7)", "warn 7\n");
        assertOutput("console.error('err %s', 'e')", "err e\n");
        assertOutput("console.debug('dbg %O', [2])", "dbg [ 2 ]\n");
        assertOutput("console.log()", "\n");
        assertOutput("console.log('%o', [])", "[ [length]: 0 ]\n");
        assertOutput("console.log('%s', { toString: 1 })", "{ toString: 1 }\n");
        // A throwing toString propagates out of console.log, as in Node.
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> log("console.log('%s', { toString() { throw new Error('ts') } })"))
                .hasMessageContaining("ts");
    }

    @Test
    public void testNothingIsPrintedWhenFormattingThrows() {
        // The exception propagates and the line is not printed, as in Node; an empty line used to be printed first.
        for (String code : new String[]{"console.log('%d', { valueOf() { throw new Error('d') } })",
                "console.warn('%i', { toString() { throw new Error('i') } })",
                "console.error('%s', { toString() { throw new Error('s') } })"}) {
            outStream.reset();
            errStream.reset();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> context.eval(code)).hasMessageContaining("Error");
            assertThat(outStream.size() + errStream.size()).as(code).isZero();
        }
    }
}
