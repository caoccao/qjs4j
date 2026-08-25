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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A failure escaping a microtask must never disappear silently.
 * <p>
 * The drain used to {@code catch (Exception e)} and, with no promise reject callback installed —
 * the default — discard it entirely: a throwing {@code .then()} handler, a
 * {@code JSVirtualMachineException}, an engine {@link NullPointerException}. The drain was also
 * unbounded, so a microtask that re-enqueued itself looped forever with no interrupt check.
 */
public class JSMicrotaskQueueFailureTest extends BaseTest {

    @Test
    public void testEngineFailureInAMicrotaskIsRecorded() {
        List<Throwable> observed = new ArrayList<>();
        context.setMicrotaskFailureCallback(observed::add);
        context.enqueueMicrotask(() -> {
            throw new IllegalStateException("engine defect");
        });
        context.processMicrotasks();

        assertThat(observed).hasSize(1);
        assertThat(observed.get(0)).isInstanceOf(IllegalStateException.class).hasMessage("engine defect");
        assertThat(context.getMicrotaskFailures()).hasSize(1);
    }

    @Test
    public void testFailureIsRecordedWithoutAnyCallbackInstalled() {
        context.setMicrotaskFailureCallback(null);
        context.setPromiseRejectCallback(null);
        context.clearMicrotaskFailures();
        context.enqueueMicrotask(() -> {
            throw new JSVirtualMachineException("vm failure with no callback");
        });
        context.processMicrotasks();

        assertThat(context.getMicrotaskFailures())
                .as("a failure must be observable even with no callback installed")
                .hasSize(1);
        assertThat(context.getMicrotaskFailures().get(0))
                .hasMessage("vm failure with no callback");
    }

    @Test
    public void testFailuresAreCappedSoARepeatedlyFailingMicrotaskCannotLeak() {
        context.setMicrotaskFailureCallback(failure -> {
        });
        context.clearMicrotaskFailures();
        int failureCount = 1000;
        for (int index = 0; index < failureCount; index++) {
            int current = index;
            context.enqueueMicrotask(() -> {
                throw new IllegalStateException("failure " + current);
            });
        }
        context.processMicrotasks();

        List<Throwable> failures = context.getMicrotaskFailures();
        assertThat(failures)
                .as("the record must be bounded, not grow with the number of failures")
                .hasSizeLessThan(failureCount);
        // The oldest entries are dropped, so the most recent failure is always retained.
        assertThat(failures.get(failures.size() - 1)).hasMessage("failure " + (failureCount - 1));
    }

    @Test
    public void testClearMicrotaskFailuresDiscardsTheRecord() {
        context.setMicrotaskFailureCallback(failure -> {
        });
        context.enqueueMicrotask(() -> {
            throw new IllegalStateException("discarded");
        });
        context.processMicrotasks();
        assertThat(context.getMicrotaskFailures()).isNotEmpty();
        context.clearMicrotaskFailures();
        assertThat(context.getMicrotaskFailures()).isEmpty();
    }

    @Test
    public void testNestedDrainReportsThatOneIsAlreadyRunning() {
        List<Boolean> nestedProcessingFlags = new ArrayList<>();
        context.enqueueMicrotask(() -> nestedProcessingFlags.add(context.getMicrotaskQueue().isProcessing()));
        assertThat(context.getMicrotaskQueue().isProcessing()).isFalse();
        context.processMicrotasks();
        assertThat(nestedProcessingFlags).containsExactly(true);
        assertThat(context.getMicrotaskQueue().isProcessing()).isFalse();
    }

    @Test
    public void testNestedDrainStillLeavesTheQueueEmpty() {
        List<String> log = new ArrayList<>();
        context.enqueueMicrotask(() -> {
            log.add("outer");
            context.enqueueMicrotask(() -> log.add("inner"));
            // A nested drain must not run the inner task; the outer loop picks it up.
            context.processMicrotasks();
            log.add("after-nested");
        });
        context.processMicrotasks();

        assertThat(log).containsExactly("outer", "after-nested", "inner");
        assertThat(context.getMicrotaskQueue().hasPendingMicrotasks()).isFalse();
    }

    @Test
    @Timeout(60)
    public void testSelfReEnqueueingMicrotaskIsInterruptible() throws InterruptedException {
        try (JSRuntime runtime = new JSRuntime(); JSContext loopingContext = runtime.createContext()) {
            Thread interrupter = new Thread(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                runtime.requestInterrupt();
            });
            interrupter.start();
            try {
                // Without an interrupt check in the drain loop this never returns.
                assertThatThrownBy(() -> {
                    enqueueForever(loopingContext);
                    loopingContext.processMicrotasks();
                })
                        .isInstanceOf(JSVirtualMachineException.class)
                        .hasMessage("execution interrupted");
            } finally {
                interrupter.join();
            }
        }
    }

    private static void enqueueForever(JSContext loopingContext) {
        loopingContext.enqueueMicrotask(() -> enqueueForever(loopingContext));
    }

    @Test
    public void testThrowingPromiseHandlerIsAPromiseRejectionNotAMicrotaskFailure() {
        // A throwing .then() handler rejects the derived promise, which is the specified
        // behaviour — it must not also be reported as a microtask failure.
        List<Throwable> observed = new ArrayList<>();
        context.setMicrotaskFailureCallback(observed::add);
        context.clearMicrotaskFailures();
        JSValue derived = context.eval(
                "Promise.resolve(1).then(() => { throw new TypeError('from then') })");
        assertThat(derived).isInstanceOf(JSPromise.class);
        assertThat(awaitPromise((JSPromise) derived)).isTrue();

        assertThat(((JSPromise) derived).getState()).isEqualTo(JSPromise.PromiseState.REJECTED);
        assertThat(observed).isEmpty();
        assertThat(context.getMicrotaskFailures()).isEmpty();
    }
}
