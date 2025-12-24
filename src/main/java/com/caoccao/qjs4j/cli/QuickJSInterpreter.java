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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line JavaScript interpreter.
 */
public final class QuickJSInterpreter {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runREPL();
        } else {
            runScript(args[0]);
        }
    }

    private static void runScript(String filename) throws Exception {
        JSRuntime runtime = new JSRuntime();
        JSContext ctx = runtime.createContext();

        String code = Files.readString(Path.of(filename));
        JSValue result = ctx.eval(code);

        runtime.runJobs();
    }

    private static void runREPL() {
        REPL repl = new REPL();
        repl.run();
    }
}
