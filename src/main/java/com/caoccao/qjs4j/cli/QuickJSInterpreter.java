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

import com.caoccao.qjs4j.core.JSArray;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.PropertyKey;
import com.caoccao.qjs4j.exceptions.JSException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line JavaScript interpreter.
 * <p>
 * <pre>
 * qjs4j                        start the REPL
 * qjs4j script.js [args...]    run a script
 * qjs4j -m module.mjs          run as an ES module
 * qjs4j -e 'code'              evaluate the given source
 * </pre>
 * <p>
 * The script's own name and any trailing arguments are exposed to the script as
 * {@code globalThis.scriptArgs}. An uncaught error is reported on standard error and exits with
 * status 1.
 */
public final class QuickJSInterpreter {
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Run the interpreter.
     *
     * @param args command-line arguments
     * @return the process exit status
     */
    static int run(String[] args) {
        if (args.length == 0) {
            new REPL().run();
            return 0;
        }

        boolean moduleMode = false;
        String inlineSource = null;
        String filename = null;
        List<String> scriptArgs = new ArrayList<>();

        int index = 0;
        while (index < args.length) {
            String arg = args[index];
            if (filename != null || inlineSource != null) {
                scriptArgs.add(arg);
                index++;
            } else if ("-m".equals(arg) || "--module".equals(arg)) {
                moduleMode = true;
                index++;
            } else if ("-e".equals(arg) || "--eval".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("qjs4j: " + arg + " requires an argument");
                    printUsage();
                    return EXIT_USAGE;
                }
                inlineSource = args[index + 1];
                index += 2;
            } else if ("-h".equals(arg) || "--help".equals(arg)) {
                printUsage();
                return 0;
            } else if (arg.startsWith("-") && arg.length() > 1) {
                System.err.println("qjs4j: unknown option " + arg);
                printUsage();
                return EXIT_USAGE;
            } else {
                filename = arg;
                index++;
            }
        }

        String source;
        String sourceName;
        if (inlineSource != null) {
            source = inlineSource;
            sourceName = "<eval>";
        } else {
            sourceName = filename;
            try {
                source = Files.readString(Path.of(filename));
            } catch (IOException | RuntimeException e) {
                System.err.println("qjs4j: cannot read " + filename
                        + ": " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
                return EXIT_FAILURE;
            }
        }

        // try-with-resources: neither the runtime nor the context used to be closed, despite both
        // being AutoCloseable.
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            setScriptArgs(context, sourceName, scriptArgs);
            // The filename is passed through: without it, stack traces have no source name and
            // module detection never triggers.
            context.eval(source, sourceName, moduleMode);
            // Both, and in this order: runJobs() drains host jobs, processMicrotasks() settles
            // promise reactions. The original code called only runJobs(), which never settled a
            // promise — it happened to work because eval() drains microtasks internally.
            runtime.runJobs();
            context.processMicrotasks();
            return 0;
        } catch (JSException e) {
            System.err.println("qjs4j: " + describe(e));
            return EXIT_FAILURE;
        } catch (RuntimeException e) {
            System.err.println("qjs4j: " + e);
            return EXIT_FAILURE;
        }
    }

    /**
     * Render a JavaScript error for the terminal, including its stack when it has one.
     *
     * @param exception the uncaught exception
     * @return the text to print
     */
    private static String describe(JSException exception) {
        String headline = exception.getMessage();
        JSValue errorValue = exception.getErrorValue();
        if (errorValue instanceof com.caoccao.qjs4j.core.JSObject errorObject) {
            JSValue stack = errorObject.get(PropertyKey.STACK);
            if (stack instanceof JSString stackString && !stackString.value().isBlank()) {
                return headline + System.lineSeparator() + stackString.value().stripTrailing();
            }
        }
        return headline;
    }

    /**
     * Expose the script name and its trailing arguments as {@code globalThis.scriptArgs}.
     *
     * @param context    the context to populate
     * @param sourceName the script name, used as {@code scriptArgs[0]}
     * @param scriptArgs the trailing arguments
     */
    private static void setScriptArgs(JSContext context, String sourceName, List<String> scriptArgs) {
        JSArray argv = context.createJSArray();
        argv.push(new JSString(sourceName));
        for (String scriptArg : scriptArgs) {
            argv.push(new JSString(scriptArg));
        }
        context.getGlobalObject().set(PropertyKey.fromString("scriptArgs"), argv);
    }

    private static void printUsage() {
        System.err.println("""
                Usage: qjs4j [options] [script.js] [args...]
                       qjs4j -e <code>
                       qjs4j                       start the REPL

                Options:
                  -m, --module    evaluate the script as an ES module
                  -e, --eval      evaluate the given source instead of a file
                  -h, --help      show this message""");
    }
}
