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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Read-Eval-Print Loop for interactive JavaScript execution.
 * <p>
 * The loop owns its runtime and its context and is {@link AutoCloseable}, so the interactive mode
 * releases what it created. It used to keep only the context — the {@code JSRuntime} was a local
 * that went out of scope at the end of the constructor — and nothing ever closed either.
 * <p>
 * Results are rendered by the same formatter {@code console.log} uses, which reads objects through
 * physical storage rather than through {@code Get}, so printing a prompt result cannot run a guest
 * accessor. The renderer used to be {@code return null}, so every evaluation printed {@code null}.
 */
public final class REPL implements AutoCloseable {
    private final JSContext context;
    private final PrintStream errorStream;
    private final PrintStream outputStream;
    private final BufferedReader reader;
    private final JSRuntime runtime;

    public REPL() {
        this(System.in, System.out, System.err);
    }

    /**
     * Create a REPL over explicit streams, which is what makes the loop testable.
     *
     * @param input       the source of lines
     * @param output      where results are printed
     * @param errorOutput where errors are printed
     */
    public REPL(InputStream input, PrintStream output, PrintStream errorOutput) {
        this.runtime = new JSRuntime();
        this.context = runtime.createContext();
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.outputStream = output;
        this.errorStream = errorOutput;
    }

    @Override
    public void close() {
        runtime.close();
    }

    public void run() {
        outputStream.println("QuickJS REPL - Type JavaScript code");
        while (true) {
            try {
                outputStream.print("qjs> ");
                outputStream.flush();
                String line = reader.readLine();
                if (line == null || line.equals("exit") || line.equals("quit")) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }

                JSValue result = context.eval(line);
                // An expression that evaluates to undefined prints nothing, as in every other
                // JavaScript REPL; a value that happens to be `undefined` is not interesting and
                // printing it for every statement is noise.
                if (result != null && !(result instanceof JSUndefined)) {
                    outputStream.println(stringify(result));
                }
            } catch (IOException e) {
                errorStream.println("Error: " + e.getMessage());
                break;
            } catch (RuntimeException e) {
                errorStream.println("Error: " + e.getMessage());
            }
        }
    }

    /**
     * Render an evaluation result for the prompt.
     *
     * @param value the result
     * @return the text to print
     */
    private String stringify(JSValue value) {
        return context.getJSGlobalObject().getConsole().formatValue(context, value);
    }
}
