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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JSConsole.
 * Uses custom PrintStreams to capture and verify output.
 */
public class JSConsoleTest extends BaseJavetTest {
    private ByteArrayOutputStream errStream;
    private JSConsole jsConsole;
    private ByteArrayOutputStream outStream;

    private String err() {
        return errStream.toString();
    }

    private String out() {
        return outStream.toString();
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        errStream = new ByteArrayOutputStream();
        outStream = new ByteArrayOutputStream();
        jsConsole = context.getJSGlobalObject().getConsole();
        jsConsole.setErr(new PrintStream(errStream));
        jsConsole.setOut(new PrintStream(outStream));
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
        context.eval("console.assert(false, 'oops')");
        assertThat(err()).isEqualTo("Assertion failed: oops\n");
    }

    @Test
    public void testAssertFalseMultipleArgs() {
        context.eval("console.assert(false, 'a', 'b', 42)");
        assertThat(err()).isEqualTo("Assertion failed: a b 42\n");
    }

    @Test
    public void testAssertFalseNoMessage() {
        context.eval("console.assert(false)");
        assertThat(err()).isEqualTo("Assertion failed:\n");
    }

    @Test
    public void testAssertFalsyValues() {
        context.eval("console.assert(0, 'zero')");
        assertThat(err()).isEqualTo("Assertion failed: zero\n");
        errStream.reset();
        context.eval("console.assert('', 'empty')");
        assertThat(err()).isEqualTo("Assertion failed: empty\n");
        errStream.reset();
        context.eval("console.assert(null, 'null')");
        assertThat(err()).isEqualTo("Assertion failed: null\n");
        errStream.reset();
        context.eval("console.assert(undefined, 'undef')");
        assertThat(err()).isEqualTo("Assertion failed: undef\n");
    }

    @Test
    public void testAssertNoArgs() {
        // No args means condition is undefined (falsy)
        context.eval("console.assert()");
        assertThat(err()).isEqualTo("Assertion failed:\n");
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
        context.eval("console.count()");
        assertThat(out()).isEqualTo("default: 1\n");
        outStream.reset();
        context.eval("console.count()");
        assertThat(out()).isEqualTo("default: 2\n");
        outStream.reset();
        context.eval("console.count()");
        assertThat(out()).isEqualTo("default: 3\n");
    }

    @Test
    public void testCountMultipleLabels() {
        context.eval("console.count('a')");
        assertThat(out()).isEqualTo("a: 1\n");
        outStream.reset();
        context.eval("console.count('b')");
        assertThat(out()).isEqualTo("b: 1\n");
        outStream.reset();
        context.eval("console.count('a')");
        assertThat(out()).isEqualTo("a: 2\n");
    }

    @Test
    public void testCountResetDefault() {
        context.eval("console.count()");
        context.eval("console.count()");
        outStream.reset();
        context.eval("console.countReset()");
        context.eval("console.count()");
        assertThat(out()).isEqualTo("default: 1\n");
    }

    @Test
    public void testCountResetDoesNotAffectOtherLabels() {
        context.eval("console.count('a')");
        context.eval("console.count('b')");
        outStream.reset();
        context.eval("console.countReset('a')");
        context.eval("console.count('b')");
        assertThat(out()).isEqualTo("b: 2\n");
    }

    @Test
    public void testCountResetWithLabel() {
        context.eval("console.count('x')");
        context.eval("console.count('x')");
        outStream.reset();
        context.eval("console.countReset('x')");
        context.eval("console.count('x')");
        assertThat(out()).isEqualTo("x: 1\n");
    }

    @Test
    public void testCountUndefinedLabel() {
        // Explicit undefined should use "default"
        context.eval("console.count(undefined)");
        assertThat(out()).isEqualTo("default: 1\n");
    }

    @Test
    public void testCountWithLabel() {
        context.eval("console.count('myLabel')");
        assertThat(out()).isEqualTo("myLabel: 1\n");
        outStream.reset();
        context.eval("console.count('myLabel')");
        assertThat(out()).isEqualTo("myLabel: 2\n");
    }

    @Test
    public void testCustomStreams() {
        assertThat(jsConsole.getOut()).isNotSameAs(System.out);
        assertThat(jsConsole.getErr()).isNotSameAs(System.err);
    }

    @Test
    public void testDebug() {
        context.eval("console.debug('debug message')");
        assertThat(out()).isEqualTo("debug message\n");
    }

    @Test
    public void testDefaultConstructor() {
        JSConsole defaultConsole = new JSConsole();
        assertThat(defaultConsole.getOut()).isSameAs(System.out);
        assertThat(defaultConsole.getErr()).isSameAs(System.err);
    }

    @Test
    public void testDir() {
        context.eval("console.dir('hello')");
        assertThat(out()).isEqualTo("hello\n");
    }

    @Test
    public void testDirxml() {
        context.eval("console.dirxml('hello')");
        assertThat(out()).isEqualTo("hello\n");
    }

    @Test
    public void testError() {
        context.eval("console.error('error')");
        assertThat(err()).isEqualTo("error\n");
        assertThat(out()).isEmpty();
    }

    @Test
    public void testErrorMultipleArgs() {
        context.eval("console.error('err', 42)");
        assertThat(err()).isEqualTo("err 42\n");
    }

    @Test
    public void testErrorNoArgs() {
        context.eval("console.error()");
        assertThat(err()).isEqualTo("\n");
    }

    @Test
    public void testFormatValueBigInt() {
        context.eval("console.log(123n)");
        assertThat(out()).isEqualTo("123n\n");
    }

    @Test
    public void testFormatValueEmptyArray() {
        context.eval("console.log([])");
        assertThat(out()).isEqualTo("[ ]\n");
    }

    @Test
    public void testFormatValueEmptyString() {
        context.eval("console.log('')");
        assertThat(out()).isEqualTo("\n");
    }

    @Test
    public void testFormatValueInfinity() {
        context.eval("console.log(Infinity, -Infinity)");
        assertThat(out()).isEqualTo("Infinity -Infinity\n");
    }

    @Test
    public void testFormatValueInvalidDateFallsBackToDateObject() {
        context.eval("console.log(new Date(NaN))");
        assertThat(out()).isEqualTo("Date {  }\n");
    }

    @Test
    public void testFormatValueNaN() {
        context.eval("console.log(NaN)");
        assertThat(out()).isEqualTo("NaN\n");
    }

    @Test
    public void testFormatValueNestedArray() {
        context.eval("console.log([1, [2, 3], 4])");
        assertThat(out()).isEqualTo("[ 1, [ 2, 3 ], 4 ]\n");
    }

    @Test
    public void testFormatValueNestedStringQuoted() {
        context.eval("console.log(['x'])");
        assertThat(out()).isEqualTo("[ \"x\" ]\n");
    }

    @Test
    public void testFormatValueRegExp() {
        context.eval("console.log(/a/)");
        assertThat(out()).isEqualTo("/a/\n");
    }

    @Test
    public void testFormatValueRegExpEscapesSlash() {
        context.eval("console.log(/a\\/b/)");
        assertThat(out()).isEqualTo("/a\\/b/\n");
    }

    @Test
    public void testFormatValueSparseArrayHoles() {
        context.eval("console.log([,])");
        assertThat(out()).isEqualTo("[ <1 empty item> ]\n");
    }

    @Test
    public void testFormatValueSymbol() {
        context.eval("console.log(Symbol('test'))");
        assertThat(out()).isEqualTo("Symbol(test)\n");
    }

    @Test
    public void testFormatValueTypedArray() {
        context.eval("console.log(new Uint8Array([1, 2, 3]))");
        assertThat(out()).isEqualTo("Uint8Array(3) [ 1, 2, 3 ]\n");
    }

    @Test
    public void testFormatValueValidDateAsIsoString() {
        context.eval("console.log(new Date('2020-01-01T00:00:00.000Z'))");
        assertThat(out()).isEqualTo("2020-01-01T00:00:00.000Z\n");
    }

    @Test
    public void testGroupAffectsAssert() {
        context.eval("console.group()");
        context.eval("console.assert(false, 'msg')");
        assertThat(err()).isEqualTo("  Assertion failed: msg\n");
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
        context.eval("console.trace('t')");
        assertThat(err()).isEqualTo("  Trace: t\n");
    }

    @Test
    public void testGroupAffectsWarnAndError() {
        context.eval("console.group()");
        context.eval("console.warn('w')");
        assertThat(err()).isEqualTo("  w\n");
        context.eval("console.error('e')");
        assertThat(err()).isEqualTo("  w\n  e\n");
    }

    @Test
    public void testGroupCollapsed() {
        context.eval("console.groupCollapsed('label')");
        assertThat(out()).isEqualTo("label\n");
        outStream.reset();
        context.eval("console.log('indented')");
        assertThat(out()).isEqualTo("  indented\n");
    }

    @Test
    public void testGroupEnd() {
        context.eval("console.group()");
        context.eval("console.log('indented')");
        outStream.reset();
        context.eval("console.groupEnd()");
        context.eval("console.log('normal')");
        assertThat(out()).isEqualTo("normal\n");
    }

    @Test
    public void testGroupEndAtZero() {
        // groupEnd when not in a group should be a no-op
        context.eval("console.groupEnd()");
        context.eval("console.log('ok')");
        assertThat(out()).isEqualTo("ok\n");
    }

    @Test
    public void testGroupIndent() {
        context.eval("console.group()");
        context.eval("console.log('indented')");
        assertThat(out()).isEqualTo("  indented\n");
    }

    @Test
    public void testGroupWithLabel() {
        context.eval("console.group('label')");
        String output = out();
        assertThat(output).isEqualTo("label\n");
        outStream.reset();
        context.eval("console.log('indented')");
        assertThat(out()).isEqualTo("  indented\n");
    }

    @Test
    public void testInfo() {
        context.eval("console.info('info message')");
        assertThat(out()).isEqualTo("info message\n");
    }

    @Test
    public void testLog() {
        context.eval("console.log('hello')");
        assertThat(out()).isEqualTo("hello\n");
    }

    @Test
    public void testLogAnonymousFunction() {
        context.eval("console.log(function () {})");
        assertThat(out()).isEqualTo("[Function (anonymous)]\n");
    }

    @Test
    public void testLogArray() {
        context.eval("console.log([1, 2, 3])");
        assertThat(out()).isEqualTo("[ 1, 2, 3 ]\n");
    }

    @Test
    public void testLogBoolean() {
        context.eval("console.log(true, false)");
        assertThat(out()).isEqualTo("true false\n");
    }

    @Test
    public void testLogMixedTypes() {
        context.eval("console.log('count:', 42, true, null, undefined)");
        assertThat(out()).isEqualTo("count: 42 true null undefined\n");
    }

    @Test
    public void testLogMultipleArgs() {
        context.eval("console.log('a', 'b', 'c')");
        assertThat(out()).isEqualTo("a b c\n");
    }

    @Test
    public void testLogNamedFunction() {
        context.eval("console.log(function f() {})");
        assertThat(out()).isEqualTo("[Function f]\n");
    }

    @Test
    public void testLogNoArgs() {
        context.eval("console.log()");
        assertThat(out()).isEqualTo("\n");
    }

    @Test
    public void testLogNull() {
        context.eval("console.log(null)");
        assertThat(out()).isEqualTo("null\n");
    }

    @Test
    public void testLogNumber() {
        context.eval("console.log(42)");
        assertThat(out()).isEqualTo("42\n");
    }

    @Test
    public void testLogObject() {
        context.eval("console.log({})");
        assertThat(out()).isEqualTo("{  }\n");
    }

    @Test
    public void testLogObjectWithGetter() {
        context.eval("const o = {}; Object.defineProperty(o, 'x', { get() { return 1; }, enumerable: true }); console.log(o);");
        assertThat(out()).isEqualTo("{ x: [Getter] }\n");
    }

    @Test
    public void testLogObjectWithNonAsciiPropertyKey() {
        context.eval("console.log({ 'é': 1 })");
        assertThat(out()).isEqualTo("{ \"é\": 1 }\n");
    }

    @Test
    public void testLogObjectWithProperties() {
        context.eval("console.log({ a: 1, b: 'x' })");
        assertThat(out()).isEqualTo("{ a: 1, b: \"x\" }\n");
    }

    @Test
    public void testLogObjectWithQuotedPropertyKey() {
        context.eval("console.log({ 'a b': 1 })");
        assertThat(out()).isEqualTo("{ \"a b\": 1 }\n");
    }

    @Test
    public void testLogUndefined() {
        context.eval("console.log(undefined)");
        assertThat(out()).isEqualTo("undefined\n");
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
        context.eval("console.log('deep')");
        assertThat(out()).isEqualTo("    deep\n");
    }

    @Test
    public void testTable() {
        context.eval("console.table([1, 2])");
        assertThat(out()).isEqualTo("[ 1, 2 ]\n");
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
        context.eval("console.trace('msg')");
        assertThat(err()).isEqualTo("Trace: msg\n");
    }

    @Test
    public void testTraceMultipleArgs() {
        context.eval("console.trace('a', 'b')");
        assertThat(err()).isEqualTo("Trace: a b\n");
    }

    @Test
    public void testTraceNoArgs() {
        context.eval("console.trace()");
        assertThat(err()).isEqualTo("Trace:\n");
    }

    @Test
    public void testWarn() {
        context.eval("console.warn('warning')");
        assertThat(err()).isEqualTo("warning\n");
        assertThat(out()).isEmpty();
    }

    @Test
    public void testWarnMultipleArgs() {
        context.eval("console.warn('a', 'b')");
        assertThat(err()).isEqualTo("a b\n");
    }

    @Test
    public void testWarnNoArgs() {
        context.eval("console.warn()");
        assertThat(err()).isEqualTo("\n");
    }
}
