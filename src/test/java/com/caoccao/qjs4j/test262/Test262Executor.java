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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes test262 test cases with proper flag handling.
 */
public class Test262Executor {
    private final long asyncTimeoutMs;
    private final HarnessLoader harnessLoader;

    public Test262Executor(HarnessLoader harnessLoader) {
        this(harnessLoader, 5000);
    }

    public Test262Executor(HarnessLoader harnessLoader, long asyncTimeoutMs) {
        this.harnessLoader = harnessLoader;
        this.asyncTimeoutMs = asyncTimeoutMs;
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
        List<JSRuntime> realmRuntimes = new ArrayList<>();
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            install262Object(context, realmRuntimes);

            // Load harness files unless 'raw' flag is present
            if (!test.hasFlag("raw")) {
                Set<String> includes = new HashSet<>(HarnessLoader.getDefaultIncludes());
                includes.addAll(test.getIncludes());
                harnessLoader.loadIntoContext(context, includes);
            } else if (!test.getIncludes().isEmpty()) {
                // Load only explicitly included files for raw tests
                harnessLoader.loadIntoContext(context, test.getIncludes());
            }

            // Prepare code with strict mode if needed
            String code = prepareCode(test);

            // Execute based on flags
            if (test.hasFlag("async")) {
                return executeAsync(context, runtime, code, test);
            } else if (test.hasFlag("module")) {
                return executeModule(context, runtime, code, test);
            } else {
                return executeScript(context, runtime, code, test);
            }

        } catch (Exception e) {
            return handleException(e, test);
        } finally {
            for (JSRuntime realmRuntime : realmRuntimes) {
                realmRuntime.close();
            }
        }
    }

    private TestResult executeAsync(JSContext context, JSRuntime runtime,
                                    String code, Test262TestCase test) {
        // Set up $DONE callback
        AtomicReference<Object> doneResult = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);

        try {
            JSObject globalObject = context.getGlobalObject();

            JSNativeFunction doneFunction = new JSNativeFunction("$DONE", 1,
                    (ctx, thisArg, args) -> {
                        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
                            // Error passed to $DONE
                            doneResult.set(args[0].toString());
                        } else {
                            // Success - no error
                            doneResult.set(null);
                        }
                        doneLatch.countDown();
                        return JSUndefined.INSTANCE;
                    }
            );

            globalObject.set("$DONE", doneFunction);

            // Execute test code
            context.eval(code, test.getPath().toString(), false);

            // Process microtasks (promises)
            runtime.runJobs();

            // Wait for $DONE with timeout
            boolean completed = doneLatch.await(asyncTimeoutMs, TimeUnit.MILLISECONDS);

            if (!completed) {
                return TestResult.timeout(test);
            }

            Object result = doneResult.get();
            if (result != null) {
                // $DONE was called with an error
                if (test.getNegative() != null) {
                    return checkNegativeResult(result.toString(), test);
                } else {
                    return TestResult.fail(test, "Async error: " + result);
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TestResult.timeout(test);
        } catch (Exception e) {
            return handleException(e, test);
        }
    }

    private TestResult executeModule(JSContext context, JSRuntime runtime,
                                     String code, Test262TestCase test) {
        try {
            context.eval(code, test.getPath().toString(), true);
            runtime.runJobs();

            if (test.getNegative() != null) {
                return TestResult.fail(test, "Expected error " + test.getNegative().getType() + " was not thrown");
            }
            return TestResult.pass(test);

        } catch (JSException e) {
            return handleException(e, test);
        } catch (Exception e) {
            return handleException(e, test);
        }
    }

    private TestResult executeScript(JSContext context, JSRuntime runtime,
                                     String code, Test262TestCase test) {
        try {
            context.eval(code, test.getPath().toString(), false);
            runtime.runJobs();

            if (test.getNegative() != null) {
                return TestResult.fail(test, "Expected error " + test.getNegative().getType() + " was not thrown");
            }
            return TestResult.pass(test);

        } catch (JSException e) {
            return handleException(e, test);
        } catch (Exception e) {
            return handleException(e, test);
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
                } catch (Exception ignored) {
                    // Fall through to message parsing
                }
            }

            // Try to extract error type from message
            String message = jsException.getMessage();
            if (message != null) {
                if (message.contains("SyntaxError")) return "SyntaxError";
                if (message.contains("ReferenceError")) return "ReferenceError";
                if (message.contains("TypeError")) return "TypeError";
                if (message.contains("RangeError")) return "RangeError";
                if (message.contains("EvalError")) return "EvalError";
                if (message.contains("URIError")) return "URIError";
            }
        }

        // Fallback to generic Error
        return "Error";
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
    private void install262Object(JSContext context, List<JSRuntime> realmRuntimes) {
        JSObject global = context.getGlobalObject();
        JSObject host262 = context.createJSObject();

        host262.set("global", global);
        host262.set("evalScript", new JSNativeFunction("evalScript", 1,
                (ctx, thisArg, args) -> {
                    String script = args.length > 0 ? JSTypeConversions.toString(ctx, args[0]).value() : "";
                    return context.eval(script, "<test262-evalScript>", false);
                }));

        host262.set("createRealm", new JSNativeFunction("createRealm", 0,
                (ctx, thisArg, args) -> {
                    JSRuntime realmRuntime = new JSRuntime();
                    realmRuntimes.add(realmRuntime);
                    JSContext realmContext = realmRuntime.createContext();
                    install262Object(realmContext, realmRuntimes);

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
                                realmRuntime.runJobs();
                                return result;
                            }));
                    return realm;
                }));

        JSNativeFunction isHTMLDDA = new JSNativeFunction("IsHTMLDDA", 0,
                (ctx, thisArg, args) -> JSNull.INSTANCE);
        isHTMLDDA.setHTMLDDA(true);
        host262.set("IsHTMLDDA", isHTMLDDA);

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
