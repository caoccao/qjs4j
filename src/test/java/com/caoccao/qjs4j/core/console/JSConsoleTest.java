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

import com.caoccao.qjs4j.core.JSGlobalObject;
import com.caoccao.qjs4j.core.JSUndefined;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JSConsole. Uses custom PrintStreams to capture and verify output.
 */
public class JSConsoleTest extends ConsoleOutputTestBase {
    private void assertStderr(String code, String expected) {
        context.eval(code);
        assertThat(err()).as(code).isEqualTo(expected);
    }

    private void assertStdout(String code, String expected) {
        context.eval(code);
        assertThat(out()).as(code).isEqualTo(expected);
    }

    @Test
    public void testAllMethodsReturnUndefined() {
        assertThat(context.eval("console.log('x')")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.info('x')")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.debug('x')")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.warn('x')")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.error('x')")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.assert(true)")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.trace()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.dir('x')")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.dirxml('x')")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.clear()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.count()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.countReset()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.time()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.timeEnd()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.table([])")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.group()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.groupCollapsed()")).isInstanceOf(JSUndefined.class);
        assertThat(context.eval("console.groupEnd()")).isInstanceOf(JSUndefined.class);
    }

    @Test
    public void testAssertFalseCondition() {
        assertStderr("console.assert(false, 'oops')", "Assertion failed: oops\n");
    }

    @Test
    public void testAssertFalseMultipleArgs() {
        assertStderr("console.assert(false, 'a', 'b', 42)", "Assertion failed: a b 42\n");
    }

    @Test
    public void testAssertFalseNoMessage() {
        assertStderr("console.assert(false)", "Assertion failed:\n");
    }

    @Test
    public void testAssertFalsyValues() {
        assertStderr("console.assert(0, 'zero')", "Assertion failed: zero\n");
        errStream.reset();
        assertStderr("console.assert('', 'empty')", "Assertion failed: empty\n");
        errStream.reset();
        assertStderr("console.assert(null, 'null')", "Assertion failed: null\n");
        errStream.reset();
        assertStderr("console.assert(undefined, 'undef')", "Assertion failed: undef\n");
    }

    @Test
    public void testAssertNoArgs() {
        // No args means condition is undefined (falsy)
        assertStderr("console.assert()", "Assertion failed:\n");
    }

    @Test
    public void testAssertTrueCondition() {
        context.eval("console.assert(true, 'should not print')");
        assertThat(err()).isEmpty();
    }

    @Test
    public void testAssertTruthyValues() {
        context.eval("console.assert(1, 'fail')");
        assertThat(err()).isEmpty();
        context.eval("console.assert('non-empty', 'fail')");
        assertThat(err()).isEmpty();
    }

    @Test
    public void testClear() {
        context.eval("console.clear()");
        assertThat(out()).isEmpty();
        assertThat(err()).isEmpty();
    }

    @Test
    public void testConsoleAccessFromJava() {
        JSGlobalObject jsGlobalObject = context.getJSGlobalObject();
        assertThat(jsGlobalObject.getConsole()).isSameAs(jsConsole);
        assertThat(jsGlobalObject.getConsole().getOut()).isSameAs(jsConsole.getOut());
        assertThat(jsGlobalObject.getConsole().getErr()).isSameAs(jsConsole.getErr());
    }

    @Test
    public void testConsoleHasAllMethods() {
        assertThat(context.eval("typeof console.log").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("console.log.length").toJavaObject()).isEqualTo(1.0);
        assertThat(context.eval("typeof console.info").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.debug").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.warn").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.error").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.assert").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.trace").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.dir").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.dirxml").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.clear").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.count").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.countReset").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.time").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.timeLog").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.timeEnd").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.table").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.group").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.groupCollapsed").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("typeof console.groupEnd").toJavaObject()).isEqualTo("function");
    }

    @Test
    public void testConsoleIsObject() {
        assertThat(context.eval("typeof console").toJavaObject()).isEqualTo("object");
    }

    @Test
    public void testCountDefault() {
        assertStdout("console.count()", "default: 1\n");
        outStream.reset();
        assertStdout("console.count()", "default: 2\n");
        outStream.reset();
        assertStdout("console.count()", "default: 3\n");
    }

    @Test
    public void testCountMultipleLabels() {
        assertStdout("console.count('a')", "a: 1\n");
        outStream.reset();
        assertStdout("console.count('b')", "b: 1\n");
        outStream.reset();
        assertStdout("console.count('a')", "a: 2\n");
    }

    @Test
    public void testCountResetDefault() {
        context.eval("console.count()");
        context.eval("console.count()");
        outStream.reset();
        context.eval("console.countReset()");
        assertStdout("console.count()", "default: 1\n");
    }

    @Test
    public void testCountResetDoesNotAffectOtherLabels() {
        context.eval("console.count('a')");
        context.eval("console.count('b')");
        outStream.reset();
        context.eval("console.countReset('a')");
        assertStdout("console.count('b')", "b: 2\n");
    }

    @Test
    public void testCountResetWithLabel() {
        context.eval("console.count('x')");
        context.eval("console.count('x')");
        outStream.reset();
        context.eval("console.countReset('x')");
        assertStdout("console.count('x')", "x: 1\n");
    }

    @Test
    public void testCountUndefinedLabel() {
        // Explicit undefined should use "default"
        assertStdout("console.count(undefined)", "default: 1\n");
    }

    @Test
    public void testCountWithLabel() {
        assertStdout("console.count('myLabel')", "myLabel: 1\n");
        outStream.reset();
        assertStdout("console.count('myLabel')", "myLabel: 2\n");
    }

    @Test
    public void testCustomStreams() {
        assertThat(jsConsole.getOut()).isNotSameAs(System.out);
        assertThat(jsConsole.getErr()).isNotSameAs(System.err);
    }

    @Test
    public void testDebug() {
        assertStdout("console.debug('debug message')", "debug message\n");
    }

    @Test
    public void testDefaultConstructor() {
        JSConsole defaultConsole = new JSConsole();
        assertThat(defaultConsole.getOut()).isSameAs(System.out);
        assertThat(defaultConsole.getErr()).isSameAs(System.err);
    }

    @Test
    public void testDir() {
        assertStdout("console.dir('hello')", "hello\n");
    }

    @Test
    public void testDirxml() {
        assertStdout("console.dirxml('hello')", "hello\n");
    }

    @Test
    public void testError() {
        assertStderr("console.error('error')", "error\n");
        assertThat(out()).isEmpty();
    }

    @Test
    public void testErrorMultipleArgs() {
        assertStderr("console.error('err', 42)", "err 42\n");
    }

    @Test
    public void testErrorNoArgs() {
        assertStderr("console.error()", "\n");
    }

    @Test
    public void testFormatValueBigInt() {
        assertStdout("console.log(123n)", "123n\n");
    }

    @Test
    public void testFormatValueEmptyArray() {
        assertStdout("console.log([])", "[ ]\n");
    }

    @Test
    public void testFormatValueEmptyString() {
        assertStdout("console.log('')", "\n");
    }

    @Test
    public void testFormatValueInfinity() {
        assertStdout("console.log(Infinity, -Infinity)", "Infinity -Infinity\n");
    }

    @Test
    public void testFormatValueInvalidDateFallsBackToDateObject() {
        assertStdout("console.log(new Date(NaN))", "Date {  }\n");
    }

    @Test
    public void testFormatValueNaN() {
        assertStdout("console.log(NaN)", "NaN\n");
    }

    @Test
    public void testFormatValueNestedArray() {
        assertStdout("console.log([1, [2, 3], 4])", "[ 1, [ 2, 3 ], 4 ]\n");
    }

    @Test
    public void testFormatValueNestedStringQuoted() {
        assertStdout("console.log(['x'])", "[ \"x\" ]\n");
    }

    @Test
    public void testFormatValueRegExp() {
        assertStdout("console.log(/a/)", "/a/\n");
    }

    @Test
    public void testFormatValueRegExpEscapesSlash() {
        assertStdout("console.log(/a\\/b/)", "/a\\/b/\n");
    }

    @Test
    public void testFormatValueSparseArrayHoles() {
        assertStdout("console.log([,])", "[ <1 empty item> ]\n");
    }

    @Test
    public void testFormatValueSymbol() {
        assertStdout("console.log(Symbol('test'))", "Symbol(test)\n");
    }

    @Test
    public void testFormatValueTypedArray() {
        assertStdout("console.log(new Uint8Array([1, 2, 3]))", "Uint8Array(3) [ 1, 2, 3 ]\n");
    }

    @Test
    public void testFormatValueValidDateAsIsoString() {
        assertStdout("console.log(new Date('2020-01-01T00:00:00.000Z'))", "2020-01-01T00:00:00.000Z\n");
    }

    @Test
    public void testGroupAffectsAssert() {
        context.eval("console.group()");
        assertStderr("console.assert(false, 'msg')", "  Assertion failed: msg\n");
    }

    @Test
    public void testGroupAffectsCount() {
        context.eval("console.group()");
        context.eval("console.count('x')");
        assertThat(out()).contains("  x: 1\n");
    }

    @Test
    public void testGroupAffectsTime() {
        context.eval("console.group()");
        context.eval("console.time('t')");
        context.eval("console.timeEnd('t')");
        assertThat(out()).matches("(?s).*  t: \\d+ms\n");
    }

    @Test
    public void testGroupAffectsTrace() {
        context.eval("console.group()");
        assertStderr("console.trace('t')", "  Trace: t\n");
    }

    @Test
    public void testGroupAffectsWarnAndError() {
        context.eval("console.group()");
        assertStderr("console.warn('w')", "  w\n");
        assertStderr("console.error('e')", "  w\n  e\n");
    }

    @Test
    public void testGroupCollapsed() {
        assertStdout("console.groupCollapsed('label')", "label\n");
        outStream.reset();
        assertStdout("console.log('indented')", "  indented\n");
    }

    @Test
    public void testGroupEnd() {
        context.eval("console.group()");
        context.eval("console.log('indented')");
        outStream.reset();
        context.eval("console.groupEnd()");
        assertStdout("console.log('normal')", "normal\n");
    }

    @Test
    public void testGroupEndAtZero() {
        // groupEnd when not in a group should be a no-op
        context.eval("console.groupEnd()");
        assertStdout("console.log('ok')", "ok\n");
    }

    @Test
    public void testGroupIndent() {
        context.eval("console.group()");
        assertStdout("console.log('indented')", "  indented\n");
    }

    @Test
    public void testGroupWithLabel() {
        context.eval("console.group('label')");
        String output = out();
        assertThat(output).isEqualTo("label\n");
        outStream.reset();
        assertStdout("console.log('indented')", "  indented\n");
    }

    @Test
    public void testInfo() {
        assertStdout("console.info('info message')", "info message\n");
    }

    @Test
    public void testLog() {
        assertStdout("console.log('hello')", "hello\n");
    }

    @Test
    public void testLogAnonymousFunction() {
        assertStdout("console.log(function () {})", "[Function (anonymous)]\n");
    }

    @Test
    public void testLogArray() {
        assertStdout("console.log([1, 2, 3])", "[ 1, 2, 3 ]\n");
    }

    @Test
    public void testLogBoolean() {
        assertStdout("console.log(true, false)", "true false\n");
    }

    @Test
    public void testLogMixedTypes() {
        assertStdout("console.log('count:', 42, true, null, undefined)", "count: 42 true null undefined\n");
    }

    @Test
    public void testLogMultipleArgs() {
        assertStdout("console.log('a', 'b', 'c')", "a b c\n");
    }

    @Test
    public void testLogNamedFunction() {
        assertStdout("console.log(function f() {})", "[Function f]\n");
    }

    @Test
    public void testLogNoArgs() {
        assertStdout("console.log()", "\n");
    }

    @Test
    public void testLogNull() {
        assertStdout("console.log(null)", "null\n");
    }

    @Test
    public void testLogNumber() {
        assertStdout("console.log(42)", "42\n");
    }

    @Test
    public void testLogObject() {
        assertStdout("console.log({})", "{  }\n");
    }

    @Test
    public void testLogObjectWithGetter() {
        context.eval(
                "const o = {}; Object.defineProperty(o, 'x', { get() { return 1; }, enumerable: true }); console.log(o);");
        assertThat(out()).isEqualTo("{ x: [Getter] }\n");
    }

    @Test
    public void testLogObjectWithNonAsciiPropertyKey() {
        assertStdout("console.log({ 'é': 1 })", "{ \"é\": 1 }\n");
    }

    @Test
    public void testLogObjectWithProperties() {
        assertStdout("console.log({ a: 1, b: 'x' })", "{ a: 1, b: \"x\" }\n");
    }

    @Test
    public void testLogObjectWithQuotedPropertyKey() {
        assertStdout("console.log({ 'a b': 1 })", "{ \"a b\": 1 }\n");
    }

    @Test
    public void testLogUndefined() {
        assertStdout("console.log(undefined)", "undefined\n");
    }

    @Test
    public void testMultipleTimers() {
        context.eval("console.time('a')");
        context.eval("console.time('b')");
        context.eval("console.timeEnd('a')");
        assertThat(out()).matches("a: \\d+ms\n");
        outStream.reset();
        context.eval("console.timeEnd('b')");
        assertThat(out()).matches("b: \\d+ms\n");
    }

    @Test
    public void testNestedGroups() {
        context.eval("console.group()");
        context.eval("console.group()");
        assertStdout("console.log('deep')", "    deep\n");
    }

    @Test
    public void testTable() {
        assertStdout("console.table([1, 2])", "[ 1, 2 ]\n");
    }

    @Test
    public void testTimeAndTimeEnd() {
        context.eval("console.time('t')");
        context.eval("console.timeEnd('t')");
        assertThat(out()).matches("t: \\d+ms\n");
    }

    @Test
    public void testTimeDefault() {
        context.eval("console.time()");
        context.eval("console.timeEnd()");
        assertThat(out()).matches("default: \\d+ms\n");
    }

    @Test
    public void testTimeEndNonexistentTimer() {
        // Should produce no output for unknown timer
        context.eval("console.timeEnd('nonexistent')");
        assertThat(out()).isEmpty();
    }

    @Test
    public void testTimeEndRemovesTimer() {
        context.eval("console.time('t')");
        context.eval("console.timeEnd('t')");
        outStream.reset();
        // Second timeEnd should produce no output (timer removed)
        context.eval("console.timeEnd('t')");
        assertThat(out()).isEmpty();
    }

    @Test
    public void testTimeLog() {
        context.eval("console.time('t')");
        context.eval("console.timeLog('t')");
        assertThat(out()).matches("t: \\d+ms\n");
        outStream.reset();
        // Timer should still be running after timeLog
        context.eval("console.timeEnd('t')");
        assertThat(out()).matches("t: \\d+ms\n");
    }

    @Test
    public void testTimeLogNonexistentTimer() {
        context.eval("console.timeLog('nonexistent')");
        assertThat(out()).isEmpty();
    }

    @Test
    public void testTimeLogWithExtraArgs() {
        context.eval("console.time('t')");
        context.eval("console.timeLog('t', 'extra', 42)");
        assertThat(out()).matches("t: \\d+ms extra 42\n");
    }

    @Test
    public void testTimeUndefinedLabel() {
        context.eval("console.time(undefined)");
        context.eval("console.timeEnd(undefined)");
        assertThat(out()).matches("default: \\d+ms\n");
    }

    @Test
    public void testTrace() {
        assertStderr("console.trace('msg')", "Trace: msg\n");
    }

    @Test
    public void testTraceMultipleArgs() {
        assertStderr("console.trace('a', 'b')", "Trace: a b\n");
    }

    @Test
    public void testTraceNoArgs() {
        assertStderr("console.trace()", "Trace:\n");
    }

    @Test
    public void testWarn() {
        assertStderr("console.warn('warning')", "warning\n");
        assertThat(out()).isEmpty();
    }

    @Test
    public void testWarnMultipleArgs() {
        assertStderr("console.warn('a', 'b')", "a b\n");
    }

    @Test
    public void testWarnNoArgs() {
        assertStderr("console.warn()", "\n");
    }
}
