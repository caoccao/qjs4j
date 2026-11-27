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

    private String log(String code) {
        outStream.reset();
        errStream.reset();
        context.eval(code);
        return out() + err();
    }

    @Test
    public void testFormatSpecifiers() {
        assertThat(log("console.log('%s=%d', 'a', 1)")).isEqualTo("a=1\n");
        assertThat(log("console.log('%i|%f|%j|%o|%O|%%|%c', 3.7, 2.5, {x: 1}, [1], {y: 2}, 'color: red')"))
                .isEqualTo("3|2.5|{\"x\":1}|[ 1, [length]: 1 ]|{ y: 2 }|%|\n");
        assertThat(log("console.log('%s', -0, 'extra', 5)")).isEqualTo("-0 extra 5\n");
        assertThat(log("console.log('%d', '42abc', '%s')")).isEqualTo("NaN %s\n");
        assertThat(log("console.log('100%', 'done')")).isEqualTo("100% done\n");
        assertThat(log("console.log('%s %s', 'only')")).isEqualTo("only %s\n");
        assertThat(log("console.log('%x', 1)")).isEqualTo("%x 1\n");
        assertThat(log("console.log('%s', { toString() { return 'T' } })")).isEqualTo("T\n");
        assertThat(log("console.log('%s', [1, 2], Symbol('q'))")).isEqualTo("[ 1, 2 ] Symbol(q)\n");
        assertThat(log("console.log('%d', 12n, '%i', 1.9)")).isEqualTo("12n %i 1.9\n");
        assertThat(log("console.log(5, '%s', 'x')")).isEqualTo("5 %s x\n");
        assertThat(log("console.log('%s', null, undefined, NaN, -Infinity)"))
                .isEqualTo("null undefined NaN -Infinity\n");
        assertThat(log("console.log('%d %i %f', -0, -0, -0)")).isEqualTo("-0 0 0\n");
        assertThat(log("var c = {}; c.self = c; console.log('%j', c)")).isEqualTo("[Circular]\n");
        assertThat(log("console.log('end%')")).isEqualTo("end%\n");
        assertThat(log("console.log('%%s', 1)")).isEqualTo("%s 1\n");
        assertThat(log("console.log('%s')")).isEqualTo("%s\n");
        assertThat(log("console.log('%d', { valueOf() { return 7 } })")).isEqualTo("7\n");
        assertThat(log("console.log('%i', '0x1F')")).isEqualTo("31\n");
        assertThat(log("console.log('%f', '1.5e3x')")).isEqualTo("1500\n");
        assertThat(log("class K { toString() { return 'K!' } } console.log('%s', new K())")).isEqualTo("K!\n");
        assertThat(log("console.log('%s', function f() {})")).isEqualTo("function f() {}\n");
        assertThat(log("console.log('%c styled %s', 'color: red', 'x')")).isEqualTo(" styled x\n");
        assertThat(log("console.log('%j', undefined)")).isEqualTo("undefined\n");
        assertThat(log("console.log('%d %i %f', Symbol(), Symbol(), Symbol())")).isEqualTo("NaN NaN NaN\n");
        assertThat(log("console.log('%i', 7n)")).isEqualTo("7n\n");
    }

    @Test
    public void testMoreSpecifiersAsNodePrintsThem() {
        assertThat(log("console.log('', 1)")).isEqualTo(" 1\n");
        assertThat(log("console.log('%s %s', 10n, Symbol('s'))")).isEqualTo("10n Symbol(s)\n");
        assertThat(log("console.log('%i %f %i', 10n, 10n, '42.9px')")).isEqualTo("10n 10 42\n");
        assertThat(log("console.log(new Float64Array([1.5, -0, NaN, 2]))"))
                .isEqualTo("Float64Array(4) [ 1.5, -0, NaN, 2 ]\n");
        assertThat(log("console.log('%o', [1, 2])")).isEqualTo("[ 1, 2, [length]: 2 ]\n");
        assertThat(log("console.log('%o', 'str')")).isEqualTo("\"str\"\n");
        // A toString inherited from a plain prototype object is the object's own conversion.
        assertThat(log("console.log('%s', Object.create({ toString() { return 'P'; } }))")).isEqualTo("P\n");
        // Node prints "[Object: null prototype] {}" and "Plain {}"; the layout of objects is not reproduced.
        assertThat(log("console.log('%s', Object.create(null))")).isEqualTo("{  }\n");
        assertThat(log("console.log('%s', new (class Plain {})())")).isEqualTo("{  }\n");
        // A prototype chain with no own toString, reachable only through a proxy (Node throws a TypeError here).
        assertThat(log("""
                const proxied = new Proxy(Object.create(null), { get: () => function () { return 'p'; } });
                console.log('%s', Object.create(proxied))""")).isEqualTo("{  }\n");
    }

    @Test
    public void testNegativeZeroAndOtherStreams() {
        assertThat(log("console.log(-0)")).isEqualTo("-0\n");
        assertThat(log("console.log([-0, 0, 1])")).isEqualTo("[ -0, 0, 1 ]\n");
        assertThat(log("console.log({ a: -0 })")).isEqualTo("{ a: -0 }\n");
        assertThat(log("console.log(1, 'a', [-0], new Float64Array([-0, 1.5]))"))
                .isEqualTo("1 a [ -0 ] Float64Array(2) [ -0, 1.5 ]\n");
        assertThat(log("console.info('info %s', 'i')")).isEqualTo("info i\n");
        assertThat(log("console.warn('warn %d', 7)")).isEqualTo("warn 7\n");
        assertThat(log("console.error('err %s', 'e')")).isEqualTo("err e\n");
        assertThat(log("console.debug('dbg %O', [2])")).isEqualTo("dbg [ 2 ]\n");
        assertThat(log("console.log()")).isEqualTo("\n");
        assertThat(log("console.log('%o', [])")).isEqualTo("[ [length]: 0 ]\n");
        assertThat(log("console.log('%s', { toString: 1 })")).isEqualTo("{ toString: 1 }\n");
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
