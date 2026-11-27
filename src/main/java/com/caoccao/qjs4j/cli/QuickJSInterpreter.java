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

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.module.JSFileSystemModuleLoader;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.core.promise.PromiseRejectEvent;
import com.caoccao.qjs4j.exceptions.JSException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Command-line JavaScript interpreter.
 * <p>
 *
 * <pre>
 * qjs4j                        start the REPL
 * qjs4j script.js [args...]    run a script
 * qjs4j -m module.mjs          run as an ES module
 * qjs4j -e 'code'              evaluate the given source
 * </pre>
 * <p>
 * The script's own name and any trailing arguments are exposed to the script as {@code globalThis.scriptArgs}. An
 * uncaught error, or a promise rejection that nothing handled by the time every job has run, is reported on standard
 * error and exits with status 1.
 */
public final class QuickJSInterpreter {
    /**
     * The Java stack the interpreter thread gets: room for the default call depth even on paths that cost several
     * kilobytes of Java stack per activation, such as {@code eval}. It is address space reserved, not memory committed.
     */
    private static final long CLI_THREAD_STACK_SIZE = 256L * 1024 * 1024;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;

    /**
     * Render a JavaScript error for the terminal, including its stack when it has one.
     * <p>
     * The stack is read out of physical storage, never through {@code Get(error, "stack")}. An ordinary property read
     * runs a {@code stack} getter or a Proxy trap on the thrown value, which means the failing script chooses what the
     * interpreter prints while reporting its own crash: it can spoof the trace, do unbounded work, or throw a second
     * error out of the reporting path. A thrown Proxy and an accessor {@code stack} both fall back to the headline.
     *
     * @param exception
     *            the uncaught exception
     * @return the text to print
     */
    private static String describe(JSException exception) {
        String headline = exception.getMessage();
        JSValue errorValue = exception.getErrorValue();
        if (errorValue instanceof com.caoccao.qjs4j.core.JSObject errorObject) {
            // An error's own stack is an accessor whose first read can run script; its frames are not.
            String frames = errorObject.getContext().getStackFramesForDiagnostics(errorObject);
            if (frames != null) {
                return frames.isEmpty() ? headline : headline + System.lineSeparator() + frames;
            }
            JSValue stack = errorObject.getOwnDataPropertyForDiagnostics(PropertyKey.STACK);
            if (stack instanceof JSString stackString && !stackString.value().isBlank()) {
                return headline + System.lineSeparator() + stackString.value().stripTrailing();
            }
        }
        return headline;
    }

    public static void main(String[] args) throws InterruptedException {
        System.exit(runWithLargeStack(args));
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

    /**
     * Report the rejections nothing handled. A script whose promise rejected unhandled used to exit 0 and print
     * nothing.
     *
     * @param unhandledRejections
     *            the rejections still unhandled
     * @return true when there was one to report
     */
    private static boolean reportUnhandledRejections(Map<JSPromise, JSValue> unhandledRejections) {
        for (JSValue reason : unhandledRejections.values()) {
            System.err.println("qjs4j: Uncaught (in promise) " + describe(new JSException(reason)));
        }
        return !unhandledRejections.isEmpty();
    }

    /**
     * Run the interpreter.
     *
     * @param args
     *            command-line arguments
     * @return the process exit status
     */
    static int run(String[] args) {
        return run(args, runtime -> {
        });
    }

    /**
     * Run the interpreter, letting the caller adjust the runtime before any script runs.
     *
     * @param args
     *            command-line arguments
     * @param runtimeInitializer
     *            applied to the script runtime right after it is created
     * @return the process exit status
     */
    static int run(String[] args, Consumer<JSRuntime> runtimeInitializer) {
        if (args.length == 0) {
            // try-with-resources: the interactive mode used to leak its runtime, whose queues and
            // registries then stayed live until the process ended.
            try (REPL repl = new REPL()) {
                repl.run();
            }
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
                System.err.println("qjs4j: cannot read " + filename + ": " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
                return EXIT_FAILURE;
            }
        }

        // try-with-resources: neither the runtime nor the context used to be closed, despite both
        // being AutoCloseable.
        // The interpreter is a local tool running the user's own scripts, so it loads modules from
        // anywhere the user can read, as node does. A runtime has no module access unless given it.
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setModuleLoader(new JSFileSystemModuleLoader()));
                JSContext context = runtime.createContext()) {
            runtimeInitializer.accept(runtime);
            setScriptArgs(context, sourceName, scriptArgs);
            CommandLineHost host = new CommandLineHost(context);
            host.install();
            // Track rejections nothing handled, as node does: a rejection is unhandled until a
            // handler is attached, so the set is only read once everything has settled.
            Map<JSPromise, JSValue> unhandledRejections = new LinkedHashMap<>();
            context.setPromiseRejectCallback((event, promise, reason) -> {
                // The engine reports only these two events: a rejection with no handler, and a handler
                // attached to a promise reported earlier.
                if (event == PromiseRejectEvent.PromiseRejectWithNoHandler) {
                    unhandledRejections.put(promise, reason);
                } else {
                    unhandledRejections.remove(promise);
                }
            });
            // The filename is passed through: without it, stack traces have no source name and
            // module detection never triggers.
            host.eval(source, sourceName, moduleMode);
            // Host jobs, then promise reactions: the original code called only runtime.runJobs(),
            // which never settled a promise — it happened to work because eval() drains microtasks
            // internally. Then the timers, each turn followed by its jobs. A rejection still unhandled
            // when a turn ends is reported then, as node does, rather than after every timer has run.
            host.runJobs();
            if (reportUnhandledRejections(unhandledRejections)
                    || !host.runTimers(() -> !reportUnhandledRejections(unhandledRejections))) {
                return EXIT_FAILURE;
            }
            return 0;
        } catch (JSException e) {
            System.err.println("qjs4j: " + describe(e));
            return EXIT_FAILURE;
        } catch (RuntimeException | Error e) {
            // An Error (an OutOfMemoryError, or a StackOverflowError from code the engine does not
            // guard) used to escape as a JVM stack dump; report it like any other failure instead.
            System.err.println("qjs4j: " + e);
            return EXIT_FAILURE;
        }
    }

    /**
     * Run the interpreter on a thread with a Java stack large enough for the default call depth.
     * <p>
     * Each JavaScript activation also occupies Java stack, so on the JVM's default thread stack a script runs out of it
     * long before {@link JSRuntimeOptions#DEFAULT_MAX_CALL_DEPTH} — at a few hundred frames — and gets a
     * {@code RangeError} that V8 would not give it.
     *
     * @param args
     *            command-line arguments
     * @return the process exit status
     * @throws InterruptedException
     *             when the waiting thread is interrupted
     */
    static int runWithLargeStack(String[] args) throws InterruptedException {
        int[] status = new int[1];
        Thread thread = new Thread(null, () -> status[0] = run(args), "qjs4j", CLI_THREAD_STACK_SIZE);
        thread.start();
        thread.join();
        return status[0];
    }

    /**
     * Expose the script name and its trailing arguments as {@code globalThis.scriptArgs}.
     *
     * @param context
     *            the context to populate
     * @param sourceName
     *            the script name, used as {@code scriptArgs[0]}
     * @param scriptArgs
     *            the trailing arguments
     */
    private static void setScriptArgs(JSContext context, String sourceName, List<String> scriptArgs) {
        JSArray argv = context.createJSArray();
        argv.push(new JSString(sourceName));
        for (String scriptArg : scriptArgs) {
            argv.push(new JSString(scriptArg));
        }
        context.getGlobalObject().set(PropertyKey.fromString("scriptArgs"), argv);
    }
}
