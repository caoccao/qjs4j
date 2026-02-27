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
import com.caoccao.qjs4j.core.*;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

final class Test262AgentHost implements AutoCloseable {
    private final CopyOnWriteArrayList<Test262Agent> agents;
    private final BlockingQueue<String> reports;
    private final Test262Executor test262Executor;
    private final AtomicLong timerIds;
    private AtomicsObject sharedAtomicsObject;

    Test262AgentHost(Test262Executor test262Executor) {
        this.test262Executor = test262Executor;
        agents = new CopyOnWriteArrayList<>();
        reports = new LinkedBlockingQueue<>();
        timerIds = new AtomicLong(1);
    }

    @Override
    public void close() {
        for (Test262Agent agent : agents) {
            agent.close();
        }
        for (Test262Agent agent : agents) {
            agent.awaitClosed();
        }
        agents.clear();
        reports.clear();
    }

    private JSNativeFunction createAgentFunction(
            JSContext context,
            String name,
            int length,
            JSNativeFunction.NativeCallback callback
    ) {
        JSNativeFunction function = new JSNativeFunction(name, length, callback);
        context.transferPrototype(function, JSFunction.NAME);
        return function;
    }

    public JSObject createAgentObject(JSContext context, List<JSRuntime> realmRuntimes, Test262Agent agent) {
        JSObject agentObject = context.createJSObject();
        agentObject.set("sleep", createAgentFunction(context, "sleep", 1,
                (ctx, thisArg, args) -> {
                    long milliseconds = 0;
                    if (args.length > 0) {
                        milliseconds = (long) JSTypeConversions.toInteger(ctx, args[0]);
                    }
                    if (milliseconds > 0) {
                        try {
                            Thread.sleep(milliseconds);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return JSUndefined.INSTANCE;
                }));
        agentObject.set("monotonicNow", createAgentFunction(context, "monotonicNow", 0,
                (ctx, thisArg, args) -> JSNumber.of(System.nanoTime() / 1_000_000.0)));

        if (agent == null) {
            agentObject.set("start", createAgentFunction(context, "start", 1,
                    (ctx, thisArg, args) -> {
                        String script = args.length > 0
                                ? JSTypeConversions.toString(ctx, args[0]).value()
                                : "";
                        Test262Agent newAgent = new Test262Agent(test262Executor, script, realmRuntimes, this);
                        agents.add(newAgent);
                        newAgent.start();
                        return JSUndefined.INSTANCE;
                    }));
            agentObject.set("broadcast", createAgentFunction(context, "broadcast", 1,
                    (ctx, thisArg, args) -> {
                        JSValue sharedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                        for (Test262Agent item : agents) {
                            item.sendBroadcast(sharedValue);
                        }
                        return JSUndefined.INSTANCE;
                    }));
            agentObject.set("getReport", createAgentFunction(context, "getReport", 0,
                    (ctx, thisArg, args) -> {
                        String report = reports.poll();
                        return report == null ? JSNull.INSTANCE : new JSString(report);
                    }));
        } else {
            agentObject.set("receiveBroadcast", createAgentFunction(context, "receiveBroadcast", 1,
                    (ctx, thisArg, args) -> {
                        if (args.length < 1 || !(args[0] instanceof JSFunction callback)) {
                            return ctx.throwTypeError("$262.agent.receiveBroadcast callback must be a function");
                        }
                        JSValue broadcastValue = agent.awaitBroadcast();
                        if (broadcastValue == null) {
                            return JSUndefined.INSTANCE;
                        }
                        JSValue result = callback.call(ctx, JSUndefined.INSTANCE, new JSValue[]{broadcastValue});
                        if (agent.getRuntime() != null) {
                            synchronized (agent.getRuntime()) {
                                agent.getRuntime().runJobs();
                            }
                        }
                        return result;
                    }));
            agentObject.set("report", createAgentFunction(context, "report", 1,
                    (ctx, thisArg, args) -> {
                        String report = args.length > 0
                                ? JSTypeConversions.toString(ctx, args[0]).value()
                                : "undefined";
                        reports.offer(report);
                        return JSUndefined.INSTANCE;
                    }));
            agentObject.set("leaving", createAgentFunction(context, "leaving", 0,
                    (ctx, thisArg, args) -> {
                        agent.markLeaving();
                        return JSUndefined.INSTANCE;
                    }));
            agentObject.set("getReport", createAgentFunction(context, "getReport", 0,
                    (ctx, thisArg, args) -> JSNull.INSTANCE));
            agentObject.set("start", createAgentFunction(context, "start", 1,
                    (ctx, thisArg, args) -> ctx.throwTypeError("$262.agent.start is only available on the main agent")));
            agentObject.set("broadcast", createAgentFunction(context, "broadcast", 1,
                    (ctx, thisArg, args) -> ctx.throwTypeError("$262.agent.broadcast is only available on the main agent")));
        }
        return agentObject;
    }

    public JSNativeFunction createSetTimeoutFunction(JSContext context) {
        return createAgentFunction(context, "setTimeout", 2,
                (ctx, thisArg, args) -> {
                    if (args.length < 1 || !(args[0] instanceof JSFunction callback)) {
                        return ctx.throwTypeError("setTimeout callback must be a function");
                    }
                    long delayMillis = 0;
                    if (args.length > 1) {
                        delayMillis = Math.max(0L, (long) JSTypeConversions.toInteger(ctx, args[1]));
                        if (ctx.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                    }
                    final long scheduledDelayMillis = delayMillis;
                    JSRuntime runtime = ctx.getRuntime();
                    long timerId = timerIds.getAndIncrement();
                    Thread timerThread = new Thread(() -> {
                        try {
                            if (scheduledDelayMillis > 0) {
                                Thread.sleep(scheduledDelayMillis);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        // Only enqueue the callback as a microtask.
                        // Do NOT call runJobs()/processMicrotasks() here
                        // because eval() may still be running on the main thread.
                        // The main event loop will process the microtask.
                        ctx.enqueueMicrotask(() -> {
                            callback.call(ctx, JSUndefined.INSTANCE, JSValue.NO_ARGS);
                            if (ctx.hasPendingException()) {
                                ctx.clearAllPendingExceptions();
                            }
                        });
                    }, "qjs4j-test262-setTimeout");
                    timerThread.setDaemon(true);
                    timerThread.start();
                    return JSNumber.of(timerId);
                });
    }

    public BlockingQueue<String> getReports() {
        return reports;
    }

    public AtomicsObject getSharedAtomicsObject() {
        return sharedAtomicsObject;
    }

    public void installAtomicsHelperOverrides(JSContext context) {
        // Keep helper-provided getReportAsync()/tryYield()/trySleep(). The runtime now supports
        // named function expressions used in atomicsHelper.js, so no post-load override is needed.
    }

    public void setSharedAtomicsObject(AtomicsObject sharedAtomicsObject) {
        this.sharedAtomicsObject = sharedAtomicsObject;
    }
}
