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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSRuntimeOptions;
import com.caoccao.qjs4j.core.JSValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class Test262Agent implements AutoCloseable {
    private final BlockingQueue<JSValue> broadcasts;
    private final Test262AgentHost host;
    private final String script;
    private final Test262Executor test262Executor;
    private final Thread thread;
    private volatile boolean closed;
    private volatile JSRuntime runtime;

    Test262Agent(Test262Executor test262Executor, String script, List<JSRuntime> realmRuntimes, Test262AgentHost host) {
        this.test262Executor = test262Executor;
        this.script = script;
        this.host = host;
        broadcasts = new LinkedBlockingQueue<>();
        closed = false;
        runtime = null;
        thread = new Thread(this::run, "qjs4j-test262-agent");
        thread.setDaemon(true);
    }

    public JSValue awaitBroadcast() {
        while (!closed) {
            try {
                JSValue value = broadcasts.poll(100, TimeUnit.MILLISECONDS);
                if (value != null) {
                    return value;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public void awaitClosed() {
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
    }

    public JSRuntime getRuntime() {
        return runtime;
    }

    public void markLeaving() {
        closed = true;
    }

    private void run() {
        List<JSRuntime> agentRealmRuntimes = new ArrayList<>();
        try (JSRuntime agentRuntime = new JSRuntime(new JSRuntimeOptions()
                .setAtomicsObject(host.getSharedAtomicsObject())
                .setTemporalEnabled(true));
             JSContext agentContext = agentRuntime.createContext()) {
            runtime = agentRuntime;
            test262Executor.install262Object(agentContext, agentRealmRuntimes, host, this);
            agentContext.eval(script, "<test262-agent>", false);
            long deadline = System.currentTimeMillis() + Math.max(test262Executor.getAsyncTimeoutMs(), 1000);
            while (!closed && System.currentTimeMillis() <= deadline) {
                synchronized (agentRuntime) {
                    agentRuntime.runJobs();
                    agentContext.processMicrotasks();
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            synchronized (agentRuntime) {
                agentRuntime.runJobs();
                agentContext.processMicrotasks();
            }
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isEmpty()) {
                message = e.getClass().getSimpleName();
            }
            host.getReports().offer("Agent error: " + message);
        } finally {
            runtime = null;
            for (JSRuntime realmRuntime : agentRealmRuntimes) {
                realmRuntime.close();
            }
        }
    }

    public void sendBroadcast(JSValue sharedValue) {
        broadcasts.offer(sharedValue);
    }

    public void start() {
        thread.start();
    }
}
