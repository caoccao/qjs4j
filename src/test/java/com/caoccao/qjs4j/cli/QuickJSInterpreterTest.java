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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CLI is the project's {@code mainClass}, so its behaviour is part of the shipped surface.
 * <p>
 * It used to close neither the runtime nor the context despite both being {@code AutoCloseable},
 * drop the filename so stack traces had no source name and module detection never triggered, offer
 * no {@code --module}, {@code --eval} or {@code scriptArgs}, and exit 0 after printing a Java stack
 * trace for an uncaught error.
 */
public class QuickJSInterpreterTest {
    @TempDir
    Path workingDirectory;

    private ByteArrayOutputStream capturedErr;
    private ByteArrayOutputStream capturedOut;
    private PrintStream originalErr;
    private PrintStream originalOut;

    @BeforeEach
    public void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String err() {
        return capturedErr.toString(StandardCharsets.UTF_8);
    }

    private String out() {
        return capturedOut.toString(StandardCharsets.UTF_8);
    }

    private Path writeFile(String name, String content) throws IOException {
        Path path = workingDirectory.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    public void testEvalOptionEvaluatesInlineSource() {
        assertThat(QuickJSInterpreter.run(new String[]{"-e", "console.log(1 + 1)"})).isZero();
        assertThat(out()).contains("2");
    }

    @Test
    public void testMissingEvalArgumentIsAUsageError() {
        assertThat(QuickJSInterpreter.run(new String[]{"-e"})).isEqualTo(2);
        assertThat(err()).contains("requires an argument");
    }

    @Test
    public void testUnknownOptionIsAUsageError() {
        assertThat(QuickJSInterpreter.run(new String[]{"--bogus"})).isEqualTo(2);
        assertThat(err()).contains("unknown option --bogus");
    }

    @Test
    public void testHelpExitsSuccessfully() {
        assertThat(QuickJSInterpreter.run(new String[]{"--help"})).isZero();
        assertThat(err()).contains("Usage: qjs4j");
    }

    @Test
    public void testMicrotasksAreDrainedBeforeExit() throws IOException {
        Path script = writeFile("micro.js",
                "Promise.resolve('settled').then(v => console.log('promise', v));\nconsole.log('sync');\n");
        assertThat(QuickJSInterpreter.run(new String[]{script.toString()})).isZero();
        assertThat(out()).contains("sync").contains("promise settled");
    }

    @Test
    public void testModuleModeResolvesImports() throws IOException {
        writeFile("dep.mjs", "export const value = 5;\n");
        Path main = writeFile("main.mjs",
                "import { value } from './dep.mjs';\nconsole.log('value', value);\n");
        assertThat(QuickJSInterpreter.run(new String[]{"-m", main.toString()})).isZero();
        assertThat(out()).contains("value 5");
    }

    @Test
    public void testScriptArgsAreExposed() throws IOException {
        Path script = writeFile("args.js", "console.log(JSON.stringify(scriptArgs.slice(1)));\n");
        assertThat(QuickJSInterpreter.run(new String[]{script.toString(), "alpha", "beta"})).isZero();
        assertThat(out()).contains("[\"alpha\",\"beta\"]");
    }

    @Test
    public void testUncaughtErrorExitsNonZeroWithADiagnosticMessage() {
        assertThat(QuickJSInterpreter.run(new String[]{"-e", "throw new TypeError('boom')"}))
                .isEqualTo(1);
        assertThat(err()).contains("TypeError: boom");
    }

    @Test
    public void testUnreadableFileExitsNonZero() {
        assertThat(QuickJSInterpreter.run(new String[]{
                workingDirectory.resolve("does-not-exist.js").toString()})).isEqualTo(1);
        assertThat(err()).contains("cannot read");
    }

    @Test
    public void testStackTraceCarriesTheSourceName() throws IOException {
        Path script = writeFile("named.js", "function boom() { throw new Error('inside') }\nboom();\n");
        assertThat(QuickJSInterpreter.run(new String[]{script.toString()})).isEqualTo(1);
        assertThat(err())
                .as("the filename must reach eval so stack traces can name the source")
                .contains("named.js");
    }
}
