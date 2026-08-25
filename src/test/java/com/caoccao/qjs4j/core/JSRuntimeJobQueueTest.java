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

package com.caoccao.qjs4j.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The host job queue and a context's microtask queue are separate, and {@code runJobs()} drains only
 * the first.
 * <p>
 * The two Javadocs said opposite things: {@code enqueueJob()} claimed promise reactions go to the
 * context's microtask queue "which runJobs() also drains", while {@code runJobs()} said it
 * deliberately does not touch any context's microtask queue — which is what the code does. An
 * embedder following the first would omit {@link JSContext#processMicrotasks()} and leave promises
 * unsettled. These tests pin the real contract so the documentation cannot drift from it silently
 * again.
 */
public class JSRuntimeJobQueueTest {

    @Test
    public void testProcessMicrotasksSettlesWhatRunJobsLeaves() {
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            context.enqueueMicrotask(() -> context.getGlobalObject()
                    .set(PropertyKey.fromString("microtaskRan"), JSBoolean.TRUE));
            runtime.runJobs();
            context.processMicrotasks();

            assertThat(context.getMicrotaskQueue().hasPendingMicrotasks()).isFalse();
            assertThat(context.getGlobalObject().get(PropertyKey.fromString("microtaskRan")))
                    .isEqualTo(JSBoolean.TRUE);
        }
    }

    @Test
    public void testPromiseReactionsDoNotReachTheHostJobQueue() {
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            context.eval("globalThis.settled = 'no'; Promise.resolve(1).then(() => { globalThis.settled = 'yes' })");
            assertThat(runtime.hasPendingJobs())
                    .as("a promise reaction is a microtask, not a host job")
                    .isFalse();
            // eval() drains microtasks before returning, so the reaction has already run.
            assertThat(context.eval("settled").toString()).isEqualTo("yes");
        }
    }

    @Test
    public void testRunJobsDrainsHostJobsOnly() {
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            boolean[] hostJobRan = {false};
            runtime.enqueueJob(() -> hostJobRan[0] = true);
            // enqueueMicrotask directly: eval() drains the queue itself before returning.
            context.enqueueMicrotask(() -> context.getGlobalObject()
                    .set(PropertyKey.fromString("microtaskRan"), JSBoolean.TRUE));

            assertThat(runtime.hasPendingJobs()).isTrue();
            assertThat(runtime.runJobs()).isEqualTo(1);

            assertThat(hostJobRan[0]).isTrue();
            assertThat(runtime.hasPendingJobs()).isFalse();
            assertThat(context.getMicrotaskQueue().hasPendingMicrotasks())
                    .as("runJobs() must not drain a context's microtask queue")
                    .isTrue();
            assertThat(context.getGlobalObject().get(PropertyKey.fromString("microtaskRan")))
                    .isEqualTo(JSUndefined.INSTANCE);
        }
    }

    @Test
    public void testRunJobsIgnoresNullJobs() {
        try (JSRuntime runtime = new JSRuntime()) {
            runtime.enqueueJob(null);
            assertThat(runtime.hasPendingJobs()).isFalse();
            assertThat(runtime.runJobs()).isZero();
        }
    }
}
