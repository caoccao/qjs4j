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

package com.caoccao.qjs4j.test262;

import com.caoccao.qjs4j.builtins.AtomicsObject;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.*;
import com.caoccao.qjs4j.test262.harness.HarnessLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes test262 test cases with proper flag handling.
 * <p>
 * Negative tests are checked against <em>both</em> halves of their metadata, as
 * {@code INTERPRETING.md} requires: the {@code phase} the error must occur in, and the name of the
 * constructor it must be an instance of. A parse-phase test therefore never reaches evaluation —
 * it is compiled and nothing more, so a program that parses cleanly and throws later fails instead
 * of passing. The expected type is compared against the thrown value's actual constructor, never
 * against text in a formatted message, so {@code throw "SyntaxError"} no longer satisfies a test
 * that requires a {@code SyntaxError}.
 */
public class Test262Executor {
    /**
     * Test262's {@code negative.phase} values.
     */
    static final String PHASE_PARSE = "parse";
    static final String PHASE_RESOLUTION = "resolution";
    static final String PHASE_RUNTIME = "runtime";
    /**
     * The one non-native type a negative test may require.
     */
    private static final String TEST262_ERROR = "Test262Error";
    private final long asyncTimeoutMs;
    /**
     * The harness's {@code Test262Error.prototype} for the test this thread is executing.
     * <p>
     * Thread-confined because one executor is shared by every worker in a run, so a field would be
     * whichever test happened to write it last. See {@link #captureHarnessErrorPrototype(JSContext)}.
     */
    private final ThreadLocal<JSObject> harnessErrorPrototype = new ThreadLocal<>();
    private final HarnessLoader harnessLoader;
    private final long syncTimeoutMs;

    public Test262Executor(HarnessLoader harnessLoader) {
        this(harnessLoader, 5000);
    }

    public Test262Executor(HarnessLoader harnessLoader, long asyncTimeoutMs) {
        this(harnessLoader, asyncTimeoutMs, 60000);
    }

    public Test262Executor(HarnessLoader harnessLoader, long asyncTimeoutMs, long syncTimeoutMs) {
        this.harnessLoader = harnessLoader;
        this.asyncTimeoutMs = asyncTimeoutMs;
        this.syncTimeoutMs = syncTimeoutMs;
    }

    /**
     * Remember the harness's own {@code Test262Error.prototype}, before the test runs.
     * <p>
     * Captured by identity while the harness is the only thing that has executed, so a test that
     * later replaces {@code Test262Error} or its {@code prototype} cannot make an unrelated object
     * answer to the name.
     *
     * @param context the context whose harness has just been loaded
     */
    private void captureHarnessErrorPrototype(JSContext context) {
        harnessErrorPrototype.remove();
        if (context.getGlobalObject().get(PropertyKey.fromString(TEST262_ERROR))
                instanceof JSObject harnessConstructor
                && harnessConstructor.get(PropertyKey.fromString("prototype"))
                instanceof JSObject harnessPrototype) {
            harnessErrorPrototype.set(harnessPrototype);
        }
    }

    /**
     * Check the value an asynchronous test reported through {@code $DONE} against the expected
     * negative metadata.
     *
     * @param doneValue the value {@code $DONE} was called with
     * @param test      the test case
     * @return the result
     */
    private TestResult checkNegativeResult(JSValue doneValue, Test262TestCase test) {
        Test262TestCase.NegativeInfo negative = test.getNegative();
        if (!PHASE_RUNTIME.equals(negative.getPhase())) {
            return TestResult.fail(test, "Expected a " + negative.getPhase()
                    + "-phase " + negative.getType() + ", but the test evaluated and reported the "
                    + "failure asynchronously");
        }
        String actualType = errorConstructorName(doneValue);
        if (negative.getType().equals(actualType)) {
            return TestResult.pass(test);
        }
        return TestResult.fail(test, "Expected " + negative.getType() + " but got "
                + describeThrownValue(doneValue));
    }

    /**
     * Describe the value an asynchronous test reported through {@code $DONE}.
     *
     * @param doneValue the reported value
     * @return a short description for a failure message
     */
    private String describeAsyncFailure(JSValue doneValue) {
        if (doneValue instanceof JSObject errorObject) {
            JSValue message = errorObject.get(PropertyKey.MESSAGE);
            if (message != null && !(message instanceof JSUndefined)) {
                return message.toString();
            }
        }
        return String.valueOf(doneValue);
    }

    /**
     * Describe a thrown Java exception for a failure message.
     *
     * @param e the exception
     * @return a short description
     */
    private String describeThrown(Exception e) {
        String constructorName = thrownConstructorName(e);
        if (constructorName != null) {
            return constructorName + (e.getMessage() != null ? ": " + e.getMessage() : "");
        }
        if (e instanceof JSException jsException) {
            return describeThrownValue(jsException.getErrorValue());
        }
        return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
    }

    /**
     * Describe a thrown value for a failure message without claiming it is an error.
     *
     * @param thrown the thrown value
     * @return a short description
     */
    private String describeThrownValue(JSValue thrown) {
        String constructorName = errorConstructorName(thrown);
        if (constructorName != null) {
            return constructorName;
        }
        if (thrown == null) {
            return "no value";
        }
        return "a thrown " + JSTypeChecking.typeof(thrown) + " (" + thrown + ")";
    }

    /**
     * The type of a thrown JavaScript value, decided by what it <em>is</em>.
     * <p>
     * Test262 matches a negative test's {@code type} against the constructor of the thrown value.
     * Reading {@code thrown.constructor.name} to find that out asks the failing program to
     * describe its own failure: every part of that expression is guest-writable, so
     * {@code throw { constructor: { name: 'TypeError' } }} satisfied a test that requires a real
     * {@code TypeError}, and the reads themselves could run an accessor or a Proxy trap while the
     * runner was trying to classify a failure. It is the same class of false green as reading the
     * error message, moved to a different mutable property.
     * <p>
     * Native errors are sealed Java classes, so their identity is not forgeable from script.
     * {@code Test262Error} is defined by the harness, so it is identified by walking the internal
     * prototype chain to the prototype object this run captured before the test could touch
     * anything — again without consulting a property the test can write.
     *
     * @param thrown the thrown value
     * @return the type name, or {@code null} when the value is not one the suite can require
     */
    private String errorConstructorName(JSValue thrown) {
        // Order matters: the sealed subclasses before the base class they extend.
        if (thrown instanceof JSAggregateError) {
            return "AggregateError";
        }
        if (thrown instanceof JSEvalError) {
            return "EvalError";
        }
        if (thrown instanceof JSRangeError) {
            return "RangeError";
        }
        if (thrown instanceof JSReferenceError) {
            return "ReferenceError";
        }
        if (thrown instanceof JSSuppressedError) {
            return "SuppressedError";
        }
        if (thrown instanceof JSSyntaxError) {
            return "SyntaxError";
        }
        if (thrown instanceof JSTypeError) {
            return "TypeError";
        }
        if (thrown instanceof JSURIError) {
            return "URIError";
        }
        if (thrown instanceof JSError) {
            return "Error";
        }
        JSObject capturedHarnessErrorPrototype = harnessErrorPrototype.get();
        if (capturedHarnessErrorPrototype != null && thrown instanceof JSObject thrownObject) {
            for (JSObject prototype = thrownObject.getPrototype();
                 prototype != null;
                 prototype = prototype.getPrototype()) {
                if (prototype == capturedHarnessErrorPrototype) {
                    return TEST262_ERROR;
                }
            }
        }
        return null;
    }

    /**
     * Evaluate the prepared source in the interpretation the test case names.
     *
     * @param context the context
     * @param runtime the runtime
     * @param code    the prepared source
     * @param test    the test case
     * @return the result
     */
    private TestResult evaluate(JSContext context, JSRuntime runtime, String code, Test262TestCase test) {
        if (test.hasFlag("async")) {
            return executeAsync(context, runtime, code, test);
        }
        if (test.getVariant() == Test262TestCase.Variant.MODULE) {
            return executeModule(context, runtime, code, test);
        }
        return executeScript(context, runtime, code, test);
    }

    public TestResult execute(Test262TestCase test) {
        long startTime = System.currentTimeMillis();
        List<JSRuntime> realmRuntimes = new ArrayList<>();
        Test262AgentHost agentHost = new Test262AgentHost(this);
        TestResult result;
        // The agent cluster's Atomics object is created here and injected, so it is not owned by
        // any one of the runtimes that share it: the main runtime would otherwise close it —
        // cancelling every agent's wait — while the agents were still being shut down below.
        AtomicsObject clusterAtomics = new AtomicsObject();
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions()
                .setAtomicsObject(clusterAtomics)
                .setShadowRealmEnabled(true)
                .setTemporalEnabled(true))) {
            try (JSContext context = runtime.createContext()) {
                agentHost.setSharedAtomicsObject(clusterAtomics);
                context.setWaitable(!test.hasFlag("CanBlockIsFalse"));
                install262Object(context, realmRuntimes, agentHost, null);

                // Load harness files unless 'raw' flag is present.
                // Default includes (assert.js, sta.js) must load first since other
                // harness files (e.g., asyncHelpers.js) depend on them.
                if (!test.hasFlag("raw")) {
                    List<String> includes = new ArrayList<>(HarnessLoader.getDefaultIncludes());
                    for (String include : test.getIncludes()) {
                        if (!includes.contains(include)) {
                            includes.add(include);
                        }
                    }
                    harnessLoader.loadIntoContext(context, includes);
                    if (includes.contains("atomicsHelper.js")) {
                        agentHost.installAtomicsHelperOverrides(context);
                    }
                } else if (!test.getIncludes().isEmpty()) {
                    // Load only explicitly included files for raw tests
                    List<String> includes = new ArrayList<>(test.getIncludes());
                    harnessLoader.loadIntoContext(context, includes);
                    if (includes.contains("atomicsHelper.js")) {
                        agentHost.installAtomicsHelperOverrides(context);
                    }
                }

                // Taken while the harness is the only thing that has run, so a test cannot make
                // an object of its own answer to Test262Error by reassigning the constructor.
                captureHarnessErrorPrototype(context);

                // Prepare code for this interpretation (strict prologue, module, or raw)
                String code = prepareCode(test);
                boolean isModule = test.getVariant() == Test262TestCase.Variant.MODULE;
                Test262TestCase.NegativeInfo negative = test.getNegative();
                boolean[] bodyEvaluated = installDoNotEvaluateProbe(context);

                if (negative != null && PHASE_PARSE.equals(negative.getPhase())) {
                    // A parse-phase test must fail while the source is being compiled. Compiling
                    // and stopping is what makes that observable: evaluating as well would let a
                    // program that parses cleanly and throws at run time report a pass.
                    result = executeParsePhase(context, code, isModule, test);
                } else {
                    if (negative != null) {
                        // A resolution- or runtime-phase test must get past compilation first.
                        TestResult parseFailure = requireSourceCompiles(context, code, isModule, test);
                        result = parseFailure != null ? parseFailure : evaluate(context, runtime, code, test);
                        result = requireNegativePhase(result, test, context, bodyEvaluated[0]);
                    } else {
                        result = evaluate(context, runtime, code, test);
                    }
                }
            }
        } catch (Exception e) {
            result = handleException(e, test);
        } finally {
            // The captured prototype belongs to a context that is now closed, and this worker
            // thread is about to be handed another test.
            harnessErrorPrototype.remove();
            agentHost.close();
            for (JSRuntime realmRuntime : realmRuntimes) {
                realmRuntime.close();
            }
            // Last, once every runtime that shared it has gone: the cluster owner closes it.
            clusterAtomics.close();
        }
        test.setTimeElapsed(System.currentTimeMillis() - startTime);
        return result;
    }

    private TestResult executeAsync(
            JSContext context,
            JSRuntime runtime,
            String code,
            Test262TestCase test) {
        try {
            // Set execution deadline to prevent hangs in eval/runJobs
            if (syncTimeoutMs > 0) {
                context.getVirtualMachine().setExecutionDeadline(
                        System.currentTimeMillis() + syncTimeoutMs);
            }

            JSObject globalObject = context.getGlobalObject();

            // $DONE is called synchronously from within runJobs on the same thread,
            // so a simple array is sufficient to capture the result. The value itself is kept,
            // not a rendering of it: reducing an error to its message string is what let an
            // arbitrary message satisfy a typed negative test.
            JSValue[] doneResult = {null};
            boolean[] doneCalled = {false};

            JSNativeFunction doneFunction = new JSNativeFunction(context, "$DONE", 1,
                    (childContext, thisArg, args) -> {
                        doneCalled[0] = true;
                        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
                            doneResult[0] = args[0];
                        }
                        return JSUndefined.INSTANCE;
                    }
            );

            globalObject.set("$DONE", doneFunction);

            // Execute test code (module source is evaluated as a module for top-level await)
            context.eval(code, test.getPath().toString(),
                    test.getVariant() == Test262TestCase.Variant.MODULE);

            long deadline = System.currentTimeMillis() + Math.max(asyncTimeoutMs, 1);
            while (!doneCalled[0] && System.currentTimeMillis() <= deadline) {
                synchronized (runtime) {
                    runtime.runJobs();
                    context.processMicrotasks();
                }
                if (!doneCalled[0]) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (!doneCalled[0]) {
                return TestResult.timeout(test);
            }

            if (doneResult[0] != null) {
                // $DONE was called with an error
                if (test.getNegative() != null) {
                    return checkNegativeResult(doneResult[0], test);
                } else {
                    return TestResult.fail(test, "Async error: " + describeAsyncFailure(doneResult[0]));
                }
            } else {
                // $DONE was called without error
                if (test.getNegative() != null) {
                    return TestResult.fail(test, "Expected error " + test.getNegative().getType() + " was not thrown");
                } else {
                    return TestResult.pass(test);
                }
            }

        } catch (JSException e) {
            return handleException(e, test);
        } catch (JSTerminationException e) {
            // The runner is the host that set the deadline, so it is entitled to observe it. The
            // engine no longer lets a script or any internal catch block intercept termination,
            // which is why this needs its own clause: JSTerminationException is an Error.
            return TestResult.timeout(test);
        } catch (Exception e) {
            return handleException(e, test);
        } finally {
            context.getVirtualMachine().setExecutionDeadline(0);
        }
    }

    private TestResult executeModule(JSContext context, JSRuntime runtime,
                                     String code, Test262TestCase test) {
        try {
            // Set execution deadline to prevent hangs
            if (syncTimeoutMs > 0) {
                context.getVirtualMachine().setExecutionDeadline(
                        System.currentTimeMillis() + syncTimeoutMs);
            }
            context.eval(code, test.getPath().toString(), true);
            synchronized (runtime) {
                runtime.runJobs();
            }

            if (test.getNegative() != null) {
                return TestResult.fail(test, "Expected error " + test.getNegative().getType() + " was not thrown");
            }
            return TestResult.pass(test);

        } catch (JSException e) {
            return handleException(e, test);
        } catch (JSTerminationException e) {
            // The runner is the host that set the deadline, so it is entitled to observe it. The
            // engine no longer lets a script or any internal catch block intercept termination,
            // which is why this needs its own clause: JSTerminationException is an Error.
            return TestResult.timeout(test);
        } catch (Exception e) {
            return handleException(e, test);
        } finally {
            context.getVirtualMachine().setExecutionDeadline(0);
        }
    }

    /**
     * Compile the source and require that compilation is what fails.
     *
     * @param context  the context
     * @param code     the prepared source
     * @param isModule whether the source is module source
     * @param test     the test case
     * @return the result
     */
    private TestResult executeParsePhase(JSContext context, String code, boolean isModule, Test262TestCase test) {
        Test262TestCase.NegativeInfo negative = test.getNegative();
        try {
            new Compiler(code, test.getPath().toString()).setContext(context).compile(isModule);
        } catch (Exception e) {
            String actualType = thrownConstructorName(e);
            return negative.getType().equals(actualType)
                    ? TestResult.pass(test)
                    : TestResult.fail(test, "Expected a parse-phase " + negative.getType()
                                            + " but compilation failed with " + describeThrown(e));
        } finally {
            // A failed compilation can leave the error on the context; the next activation would
            // otherwise report this test's expected error as its own failure.
            context.clearAllPendingExceptions();
        }
        return TestResult.fail(test, "Expected a parse-phase " + negative.getType()
                + " but the source compiled successfully");
    }

    private TestResult executeScript(JSContext context, JSRuntime runtime,
                                     String code, Test262TestCase test) {
        try {
            // Set execution deadline for sync tests to prevent hangs
            if (syncTimeoutMs > 0) {
                context.getVirtualMachine().setExecutionDeadline(
                        System.currentTimeMillis() + syncTimeoutMs);
            }
            context.eval(code, test.getPath().toString(), false);
            synchronized (runtime) {
                runtime.runJobs();
            }

            if (test.getNegative() != null) {
                return TestResult.fail(test, "Expected error " + test.getNegative().getType() + " was not thrown");
            }
            return TestResult.pass(test);

        } catch (JSException e) {
            return handleException(e, test);
        } catch (JSTerminationException e) {
            // The runner is the host that set the deadline, so it is entitled to observe it. The
            // engine no longer lets a script or any internal catch block intercept termination,
            // which is why this needs its own clause: JSTerminationException is an Error.
            return TestResult.timeout(test);
        } catch (Exception e) {
            return handleException(e, test);
        } finally {
            // Clear the deadline after execution
            context.getVirtualMachine().setExecutionDeadline(0);
        }
    }

    public long getAsyncTimeoutMs() {
        return asyncTimeoutMs;
    }

    private TestResult handleException(Exception e, Test262TestCase test) {
        Test262TestCase.NegativeInfo negative = test.getNegative();
        if (negative == null) {
            // Unexpected error
            String message = e.getMessage();
            if (message == null) {
                message = e.getClass().getSimpleName();
            }
            return TestResult.fail(test, "Unexpected error: " + message);
        }

        if (PHASE_PARSE.equals(negative.getPhase())) {
            // Reached only when the compile-only stage passed and evaluation then threw, which is
            // exactly the phase mismatch a parse-phase test exists to catch.
            return TestResult.fail(test, "Expected a parse-phase " + negative.getType()
                    + " but the source compiled and failed later with " + describeThrown(e));
        }

        String actualType = thrownConstructorName(e);
        if (negative.getType().equals(actualType)) {
            return TestResult.pass(test);
        }
        return TestResult.fail(test,
                "Expected " + negative.getType() + " but got " + describeThrown(e));
    }

    /**
     * Install a minimal Test262 host object ($262) with createRealm()/evalScript().
     * This is enough for cross-realm tests used by annexB RegExp compile checks.
     */
    public void install262Object(
            JSContext context,
            List<JSRuntime> realmRuntimes,
            Test262AgentHost agentHost,
            Test262Agent agent) {
        JSObject global = context.getGlobalObject();
        JSObject host262 = context.createJSObject();

        host262.set("global", global);
        host262.set("evalScript", new JSNativeFunction(context, "evalScript", 1,
                (childContext, thisArg, args) -> {
                    String script = args.length > 0 ? JSTypeConversions.toString(childContext, args[0]).value() : "";
                    return context.eval(script, "<test262-evalScript>", false);
                }));

        host262.set("detachArrayBuffer", new JSNativeFunction(context, "detachArrayBuffer", 1,
                (childContext, thisArg, args) -> {
                    if (args.length > 0 && args[0] instanceof JSArrayBuffer jsArrayBuffer) {
                        jsArrayBuffer.detach();
                    }
                    return JSUndefined.INSTANCE;
                }));

        host262.set("gc", new JSNativeFunction(context, "gc", 0,
                (childContext, thisArg, args) -> {
                    childContext.getRuntime().gc();
                    return JSUndefined.INSTANCE;
                }));

        host262.set("createRealm", new JSNativeFunction(context, "createRealm", 0,
                (childContext, thisArg, args) -> {
                    JSRuntime realmRuntime = context.getRuntime();
                    JSContext realmContext = realmRuntime.createContext();
                    install262Object(realmContext, realmRuntimes, agentHost, null);

                    JSObject realm = childContext.createJSObject();
                    JSObject realmGlobal = realmContext.getGlobalObject();
                    realm.set("global", realmGlobal);
                    realm.set("globalThis", realmGlobal);
                    realm.set("evalScript", new JSNativeFunction(context, "evalScript", 1,
                            (innerCtx, innerThisArg, innerArgs) -> {
                                String script = innerArgs.length > 0
                                        ? JSTypeConversions.toString(innerCtx, innerArgs[0]).value()
                                        : "";
                                JSValue result = realmContext.eval(script, "<test262-realm-evalScript>", false);
                                synchronized (realmRuntime) {
                                    realmRuntime.runJobs();
                                }
                                return result;
                            }));
                    return realm;
                }));

        JSNativeFunction isHTMLDDA = new JSNativeFunction(context, "IsHTMLDDA", 0,
                (childContext, thisArg, args) -> JSNull.INSTANCE);
        isHTMLDDA.setHTMLDDA(true);
        host262.set("IsHTMLDDA", isHTMLDDA);
        host262.set("agent", agentHost.createAgentObject(context, realmRuntimes, agent));
        if (global.get("setTimeout") instanceof JSUndefined) {
            global.set("setTimeout", agentHost.createSetTimeoutFunction(context));
        }
        if (global.get("print") instanceof JSUndefined) {
            global.set("print", new JSNativeFunction(context, "print", 1,
                    (childContext, thisArg, args) -> JSUndefined.INSTANCE));
        }

        global.set("$262", host262);
    }

    /**
     * Wrap the harness's {@code $DONOTEVALUATE} so the executor can see whether the test body ran.
     * <p>
     * {@code INTERPRETING.md} splits negative tests by <em>phase</em>, and the engine has no
     * separate link step: loading, linking and evaluating a module all happen inside one
     * {@code eval}. Comparing the thrown constructor alone therefore cannot tell a resolution
     * failure from a body that ran and threw the same constructor later. Test262 marks the boundary
     * itself — every parse- and resolution-phase test opens its body with {@code $DONOTEVALUATE()},
     * whose whole purpose is to be unreachable — so observing that call is a direct, source-driven
     * answer to "did the body execute?".
     * <p>
     * The original function is still called, so the value the test throws is unchanged.
     *
     * @param context the context whose harness has already been loaded
     * @return a one-element array set to true once the test body starts executing
     */
    private boolean[] installDoNotEvaluateProbe(JSContext context) {
        boolean[] bodyEvaluated = {false};
        JSObject globalObject = context.getGlobalObject();
        JSValue existing = globalObject.get("$DONOTEVALUATE");
        if (!(existing instanceof JSFunction originalFunction)) {
            // A raw test has no harness. Nothing to observe, and no resolution-phase test is raw.
            return bodyEvaluated;
        }
        globalObject.set("$DONOTEVALUATE", new JSNativeFunction(context, "$DONOTEVALUATE", 0,
                (childContext, thisArg, args) -> {
                    bodyEvaluated[0] = true;
                    return originalFunction.call(childContext, thisArg, args);
                }));
        return bodyEvaluated;
    }

    /**
     * Whether anything in the module graph was actually evaluated.
     * <p>
     * Asked by the runtime-phase branch, which needs the opposite assurance from the
     * resolution-phase one: something must have run. The engine settles it two ways — the module
     * the runner itself handed to {@code eval} began running (the total body count exceeds the
     * count of bodies pulled in by an import), or some module body failed rather than finishing.
     * Either is evaluation, and a runtime-phase failure raised by a dependency that threw shows up
     * only as the second.
     *
     * @param context the context the test ran in
     * @return true when the graph got past linking
     */
    private boolean moduleGraphWasEvaluated(JSContext context) {
        return context.getModuleBodyEvaluationCount() > context.getImportedModuleBodyEvaluationCount()
                || context.hasModuleBodyEvaluationFailed();
    }

    /**
     * Produce the source for this interpretation.
     * <p>
     * The strict variant gets a {@code "use strict";} prologue as {@code INTERPRETING.md}
     * prescribes: inserted as the initial character sequence, before any other modification.
     * Prepending it unconditionally is correct even when the file already begins with its own
     * directive — a duplicated directive prologue entry is not an error — but the check below
     * keeps the reported source close to the file's.
     *
     * @param test the test case
     * @return the source to compile and evaluate
     */
    private String prepareCode(Test262TestCase test) {
        String code = test.getCode();

        if (test.getVariant() == Test262TestCase.Variant.STRICT
                && !code.stripLeading().startsWith("\"use strict\"")
                && !code.stripLeading().startsWith("'use strict'")) {
            code = "\"use strict\";\n" + code;
        }

        return code;
    }

    /**
     * Prewarm runtime/context class loading before parallel test execution.
     * This reduces startup class-loader contention when many workers create contexts simultaneously.
     *
     * @return elapsed prewarm time in milliseconds
     */
    public long prewarm() {
        long startTime = System.currentTimeMillis();
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions()
                .setShadowRealmEnabled(true)
                .setTemporalEnabled(true));
             JSContext context = runtime.createContext()) {
            context.clearAllPendingExceptions();
        } catch (Exception ignored) {
            // Best-effort optimization only.
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Hold a negative test to the phase its metadata declares.
     * <p>
     * {@code INTERPRETING.md} splits negative tests by the stage they fail in, and both directions
     * have to be enforced or neither claim means anything. A resolution-phase failure happens while
     * the graph is linked, so nothing may have been evaluated; a runtime-phase failure happens
     * while it is evaluated, so something must have been. Checking only the first let a genuine
     * link failure pass under {@code runtime}, and checking only the root test body let a
     * dependency that threw pass under {@code resolution}.
     * <p>
     * {@code $DONOTEVALUATE()} stays as a second, source-level witness: it is what the suite itself
     * writes to mark the boundary, and it names the offending module directly.
     *
     * @param result        the result the phase-agnostic comparison produced
     * @param test          the test case
     * @param context       the context the test ran in
     * @param bodyEvaluated whether the root test body started executing
     * @return the result, downgraded to a failure on a phase mismatch
     */
    private TestResult requireNegativePhase(
            TestResult result, Test262TestCase test, JSContext context, boolean bodyEvaluated) {
        Test262TestCase.NegativeInfo negative = test.getNegative();
        if (!result.isPassed() || negative == null) {
            return result;
        }
        if (PHASE_RUNTIME.equals(negative.getPhase())) {
            if (test.getVariant() != Test262TestCase.Variant.MODULE || moduleGraphWasEvaluated(context)) {
                return result;
            }
            return TestResult.fail(test, "Expected a runtime-phase " + negative.getType()
                    + " but no module body was evaluated, so the error was raised while the module "
                    + "graph was still being linked");
        }
        if (bodyEvaluated) {
            return TestResult.fail(test, "Expected a " + negative.getPhase() + "-phase "
                    + negative.getType() + " but the test body was evaluated, so the error was raised "
                    + "after the module graph had been linked");
        }
        // Not one module body, anywhere in the graph. This used to ask a weaker question — whether
        // the module the runner asked for had begun evaluating — because the engine linked named,
        // default and indirect exports before evaluating but decided ambiguity and resolution
        // across a cycle only by running the graph, and demanding zero would have failed the
        // suite's own tests for those. The link pass decides them now, so the real requirement can
        // be asked: a resolution failure means the graph never started.
        if (context.getModuleBodyEvaluationCount() > 0) {
            return TestResult.fail(test, "Expected a " + negative.getPhase() + "-phase "
                    + negative.getType() + " but a module body was evaluated, so the error was raised "
                    + "after the module graph had been linked");
        }
        return result;
    }

    /**
     * Compile the source and report a failure when compilation is <em>not</em> supposed to fail.
     *
     * @param context  the context
     * @param code     the prepared source
     * @param isModule whether the source is module source
     * @param test     the test case
     * @return a failing result when the source did not compile, or {@code null} when it did
     */
    private TestResult requireSourceCompiles(JSContext context, String code, boolean isModule, Test262TestCase test) {
        Test262TestCase.NegativeInfo negative = test.getNegative();
        try {
            new Compiler(code, test.getPath().toString()).setContext(context).compile(isModule);
            return null;
        } catch (Exception e) {
            return TestResult.fail(test, "Expected a " + negative.getPhase() + "-phase "
                    + negative.getType() + " but the source failed to compile with " + describeThrown(e));
        } finally {
            context.clearAllPendingExceptions();
        }
    }

    /**
     * The name of the constructor of the JavaScript value an exception carries.
     * <p>
     * The engine reports some errors as typed Java exceptions that never became JavaScript
     * objects. For those the Java class <em>is</em> the error's identity, so the mapping below is
     * exact rather than a guess at the text of a message. Everything else must produce a real
     * thrown value whose own constructor is read; no name is inferred from message text, which is
     * what let {@code throw "SyntaxError"} satisfy a typed negative test.
     *
     * @param e the exception
     * @return the constructor name, or {@code null} when the exception carries no error identity
     */
    private String thrownConstructorName(Exception e) {
        if (e instanceof JSException jsException) {
            return errorConstructorName(jsException.getErrorValue());
        }
        if (e instanceof JSSyntaxErrorException) {
            return "SyntaxError";
        }
        if (e instanceof JSCompilerException) {
            // Same mapping the engine itself applies: JSContext.eval turns any JSCompilerException
            // into a SyntaxError, so this is what a host would observe from the same source.
            return "SyntaxError";
        }
        if (e instanceof JSTypeErrorException) {
            return "TypeError";
        }
        if (e instanceof JSRangeErrorException) {
            return "RangeError";
        }
        return null;
    }

}
