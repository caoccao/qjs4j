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

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.test262.harness.HarnessLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes test262 test cases with proper flag handling.
 */
public class Test262Executor {
    private final long asyncTimeoutMs;
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

    private TestResult checkNegativeResult(String errorMessage, Test262TestCase test) {
        Test262TestCase.NegativeInfo negative = test.getNegative();

        // Simple check: does the error message contain the expected error type?
        if (errorMessage.contains(negative.getType())) {
            return TestResult.pass(test);
        } else {
            return TestResult.fail(test,
                    "Expected " + negative.getType() + " but got: " + errorMessage);
        }
    }

    public TestResult execute(Test262TestCase test) {
        long startTime = System.currentTimeMillis();
        List<JSRuntime> realmRuntimes = new ArrayList<>();
        Test262AgentHost agentHost = new Test262AgentHost(this);
        TestResult result;
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setShadowRealmEnabled(true))) {
            try (JSContext context = runtime.createContext()) {
                agentHost.setSharedAtomicsObject(runtime.getOptions().getAtomicsObject());
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

                // Prepare code with strict mode if needed
                String code = prepareCode(test);

                // Execute based on flags
                if (test.hasFlag("async")) {
                    result = executeAsync(context, runtime, code, test);
                } else if (test.hasFlag("module")) {
                    result = executeModule(context, runtime, code, test);
                } else {
                    result = executeScript(context, runtime, code, test);
                }

            }
        } catch (Exception e) {
            result = handleException(e, test);
        } finally {
            agentHost.close();
            for (JSRuntime realmRuntime : realmRuntimes) {
                realmRuntime.close();
            }
        }
        test.setTimeElapsed(System.currentTimeMillis() - startTime);
        return result;
    }

    private TestResult executeAsync(JSContext context, JSRuntime runtime,
                                    String code, Test262TestCase test) {
        try {
            // Set execution deadline to prevent hangs in eval/runJobs
            if (syncTimeoutMs > 0) {
                context.getVirtualMachine().setExecutionDeadline(
                        System.currentTimeMillis() + syncTimeoutMs);
            }

            JSObject globalObject = context.getGlobalObject();

            // $DONE is called synchronously from within runJobs on the same thread,
            // so a simple array is sufficient to capture the result.
            Object[] doneResult = {null};
            boolean[] doneCalled = {false};

            JSNativeFunction doneFunction = new JSNativeFunction("$DONE", 1,
                    (ctx, thisArg, args) -> {
                        doneCalled[0] = true;
                        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
                            // Error passed to $DONE
                            JSValue doneArg = args[0];
                            if (doneArg instanceof JSObject errorObject) {
                                JSValue message = errorObject.get(ctx, PropertyKey.MESSAGE);
                                if (!ctx.hasPendingException() && message != null && !(message instanceof JSUndefined)) {
                                    doneResult[0] = message.toString();
                                } else {
                                    if (ctx.hasPendingException()) {
                                        ctx.clearPendingException();
                                    }
                                    doneResult[0] = doneArg.toString();
                                }
                            } else {
                                doneResult[0] = doneArg.toString();
                            }
                        }
                        return JSUndefined.INSTANCE;
                    }
            );

            globalObject.set("$DONE", doneFunction);

            // Execute test code
            context.eval(code, test.getPath().toString(), false);

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
                    return checkNegativeResult(doneResult[0].toString(), test);
                } else {
                    return TestResult.fail(test, "Async error: " + doneResult[0]);
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
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("execution timeout")) {
                return TestResult.timeout(test);
            }
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
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("execution timeout")) {
                return TestResult.timeout(test);
            }
            return handleException(e, test);
        } finally {
            context.getVirtualMachine().setExecutionDeadline(0);
        }
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
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("execution timeout")) {
                return TestResult.timeout(test);
            }
            return handleException(e, test);
        } finally {
            // Clear the deadline after execution
            context.getVirtualMachine().setExecutionDeadline(0);
        }
    }

    private String extractErrorType(Exception e) {
        if (e instanceof JSException jsException) {
            JSValue error = jsException.getErrorValue();

            if (error instanceof JSObject errorObj) {
                try {
                    JSValue nameValue = errorObj.get("name");
                    if (nameValue != null && !(nameValue instanceof JSUndefined)) {
                        return nameValue.toString();
                    }

                    // Try constructor.name for custom error types (e.g. Test262Error)
                    JSValue constructor = errorObj.get("constructor");
                    if (constructor instanceof JSObject constructorObj) {
                        JSValue constructorName = constructorObj.get("name");
                        if (constructorName instanceof JSString nameStr && !nameStr.value().isEmpty()) {
                            return nameStr.value();
                        }
                    }
                } catch (Exception ignored) {
                    // Fall through to message parsing
                }
            }

            // Try to extract error type from message
            String message = jsException.getMessage();
            if (message != null) {
                if (message.contains("SyntaxError")) {
                    return "SyntaxError";
                }
                if (message.contains("ReferenceError")) {
                    return "ReferenceError";
                }
                if (message.contains("TypeError")) {
                    return "TypeError";
                }
                if (message.contains("RangeError")) {
                    return "RangeError";
                }
                if (message.contains("EvalError")) {
                    return "EvalError";
                }
                if (message.contains("URIError")) {
                    return "URIError";
                }
            }
        }

        // Fallback to generic Error
        return "Error";
    }

    public long getAsyncTimeoutMs() {
        return asyncTimeoutMs;
    }

    private TestResult handleException(Exception e, Test262TestCase test) {
        if (test.getNegative() == null) {
            // Unexpected error
            String message = e.getMessage();
            if (message == null) {
                message = e.getClass().getSimpleName();
            }
            return TestResult.fail(test, "Unexpected error: " + message);
        }

        // Check if error type matches expected
        Test262TestCase.NegativeInfo negative = test.getNegative();
        String errorType = extractErrorType(e);

        if (errorType.equals(negative.getType())) {
            return TestResult.pass(test);
        } else {
            return TestResult.fail(test,
                    "Expected " + negative.getType() + " but got " + errorType);
        }
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
        host262.set("evalScript", new JSNativeFunction("evalScript", 1,
                (ctx, thisArg, args) -> {
                    String script = args.length > 0 ? JSTypeConversions.toString(ctx, args[0]).value() : "";
                    return context.eval(script, "<test262-evalScript>", false);
                }));

        host262.set("detachArrayBuffer", new JSNativeFunction("detachArrayBuffer", 1,
                (ctx, thisArg, args) -> {
                    if (args.length > 0 && args[0] instanceof JSArrayBuffer jsArrayBuffer) {
                        jsArrayBuffer.detach();
                    }
                    return JSUndefined.INSTANCE;
                }));

        host262.set("createRealm", new JSNativeFunction("createRealm", 0,
                (ctx, thisArg, args) -> {
                    JSRuntime realmRuntime = context.getRuntime();
                    JSContext realmContext = realmRuntime.createContext();
                    install262Object(realmContext, realmRuntimes, agentHost, null);

                    JSObject realm = ctx.createJSObject();
                    JSObject realmGlobal = realmContext.getGlobalObject();
                    realm.set("global", realmGlobal);
                    realm.set("globalThis", realmGlobal);
                    realm.set("evalScript", new JSNativeFunction("evalScript", 1,
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

        JSNativeFunction isHTMLDDA = new JSNativeFunction("IsHTMLDDA", 0,
                (ctx, thisArg, args) -> JSNull.INSTANCE);
        isHTMLDDA.setHTMLDDA(true);
        host262.set("IsHTMLDDA", isHTMLDDA);
        host262.set("agent", agentHost.createAgentObject(context, realmRuntimes, agent));
        if (global.get("setTimeout") instanceof JSUndefined) {
            global.set("setTimeout", agentHost.createSetTimeoutFunction(context));
        }
        if (global.get("print") instanceof JSUndefined) {
            global.set("print", new JSNativeFunction("print", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE));
        }

        global.set("$262", host262);
    }

    private String prepareCode(Test262TestCase test) {
        String code = test.getCode();

        // Handle strict mode flags
        if (test.hasFlag("onlyStrict") && !code.trim().startsWith("\"use strict\"")
                && !code.trim().startsWith("'use strict'")) {
            code = "\"use strict\";\n" + code;
        }

        return code;
    }

}
