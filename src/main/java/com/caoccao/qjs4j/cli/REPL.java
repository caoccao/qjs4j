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
import com.caoccao.qjs4j.core.JSValue;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Read-Eval-Print Loop for interactive JavaScript execution.
 */
public final class REPL {
    private final JSContext ctx;
    private final BufferedReader reader;

    public REPL() {
        JSRuntime runtime = new JSRuntime();
        this.ctx = runtime.createContext();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run() {
        System.out.println("QuickJS REPL - Type JavaScript code");
        while (true) {
            try {
                System.out.print("qjs> ");
                String line = reader.readLine();
                if (line == null || line.equals("exit") || line.equals("quit")) {
                    break;
                }

                JSValue result = ctx.eval(line);
                if (result != null) {
                    System.out.println(stringify(result));
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private String stringify(JSValue value) {
        return null;
    }
}
