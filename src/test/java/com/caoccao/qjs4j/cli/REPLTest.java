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

package com.caoccao.qjs4j.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The REPL's renderer was {@code private String stringify(JSValue value) { return null; }}, so the
 * primary interactive interface printed {@code null} for every evaluation. Its runtime was a local
 * variable in the constructor that nothing kept and nothing closed.
 */
public class REPLTest {
    private Output runRepl(String input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
             PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8);
             REPL repl = new REPL(
                     new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                     outStream,
                     errStream)) {
            repl.run();
        }
        return new Output(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testBlankLinesArePassedOver() {
        Output output = runRepl("\n\n   \n1 + 1\nexit\n");
        assertThat(output.out()).contains("2");
        assertThat(output.err()).isEmpty();
    }

    @Test
    public void testEndOfInputEndsTheLoop() {
        // No `exit`: the loop must end when readLine returns null rather than spinning.
        Output output = runRepl("6 * 7\n");
        assertThat(output.out()).contains("42");
    }

    @Test
    public void testErrorsGoToStandardErrorAndTheLoopContinues() {
        Output output = runRepl("noSuchThing()\n1 + 1\nexit\n");
        assertThat(output.err()).contains("ReferenceError").contains("noSuchThing");
        assertThat(output.out()).contains("2");
    }

    @Test
    public void testNumbersAndStringsAreRendered() {
        // The review's reproducer printed `null` here.
        Output output = runRepl("1 + 1\n'hello'.toUpperCase()\nexit\n");
        assertThat(output.out()).contains("2").contains("HELLO").doesNotContain("null");
    }

    @Test
    public void testObjectsAndArraysAreRendered() {
        Output output = runRepl("({ a: 1, b: [2, 3] })\nexit\n");
        assertThat(output.out()).contains("a: 1").contains("2").contains("3");
    }

    @Test
    public void testQuitEndsTheLoop() {
        Output output = runRepl("1 + 1\nquit\n2 + 2\n");
        assertThat(output.out()).contains("2").doesNotContain("4");
    }

    @Test
    public void testRenderingAResultDoesNotRunGuestAccessors() {
        Output output = runRepl("""
                globalThis.calls = 0;
                var probe = {}; Object.defineProperty(probe, 'trap', { get() { calls++; return 1 }, enumerable: true });
                probe
                'calls=' + calls
                exit
                """);
        // The accessor is described, not invoked, and the count proves it.
        assertThat(output.out()).contains("[Getter]").contains("calls=0");
    }

    @Test
    public void testStateIsRetainedBetweenLines() {
        Output output = runRepl("var x = 5\nx * 2\nexit\n");
        assertThat(output.out()).contains("10");
    }

    @Test
    public void testTheReplClosesItsRuntime() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        REPL repl = new REPL(
                new ByteArrayInputStream("exit\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        repl.run();
        repl.close();
        // Closing twice is the harmless case a try-with-resources plus an explicit close produces.
        repl.close();
    }

    @Test
    public void testUndefinedResultsPrintNothing() {
        Output output = runRepl("undefined\nvoid 0\nexit\n");
        assertThat(output.out()).doesNotContain("undefined");
    }

    private record Output(String out, String err) {
    }
}
