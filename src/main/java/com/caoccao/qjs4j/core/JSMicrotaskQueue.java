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

import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.ArrayDeque;

/**
 * Manages the microtask queue for promise resolution and async operations.
 * Based on ES2020 Jobs and Job Queues specification.
 * <p>
 * Microtasks are executed after the current script completes and before
 * returning to the event loop. This ensures promise handlers run at the
 * right time.
 * <p>
 * The queue is synchronized because some host integrations (for example, Atomics.waitAsync)
 * can enqueue microtasks from helper threads. <strong>Enqueueing is the only cross-thread
 * operation that is safe</strong>: {@link #processMicrotasks()} runs the microtasks, which touch
 * unsynchronised engine state, so it must run on the context's own thread. See {@link JSRuntime}
 * for the full threading contract.
 */
public final class JSMicrotaskQueue {
    /**
     * How many microtasks to run between interrupt checks.
     */
    private static final int INTERRUPT_CHECK_INTERVAL = 1024;
    private final JSContext context;
    private final ArrayDeque<Microtask> queue;
    private final Object queueLock;
    private boolean executing;

    /**
     * Create a new microtask queue.
     *
     * @param context The JavaScript context
     */
    public JSMicrotaskQueue(JSContext context) {
        this.context = context;
        this.queueLock = new Object();
        this.queue = new ArrayDeque<>();
        this.executing = false;
    }

    /**
     * Clear all pending microtasks.
     * This is used for cleanup or testing.
     */
    public void clear() {
        synchronized (queueLock) {
            queue.clear();
        }
    }

    /**
     * Enqueue a microtask to be executed.
     *
     * @param microtask The microtask to enqueue
     */
    public void enqueue(Microtask microtask) {
        synchronized (queueLock) {
            queue.offer(microtask);
        }
    }

    /**
     * Check if there are pending microtasks.
     *
     * @return true if the queue is not empty
     */
    public boolean hasPendingMicrotasks() {
        synchronized (queueLock) {
            return !queue.isEmpty();
        }
    }

    /**
     * Process all pending microtasks.
     * This should be called at the end of each task in the event loop.
     * <p>
     * Microtasks can enqueue more microtasks, so this runs until the queue is empty. A microtask
     * that keeps re-enqueueing itself would otherwise loop forever, so the host interrupt and the
     * execution deadline are polled every {@link #INTERRUPT_CHECK_INTERVAL} microtasks; both raise
     * a {@link com.caoccao.qjs4j.exceptions.JSTerminationException} that ends the drain.
     * <p>
     * A nested call returns immediately and drains nothing. That is deliberate rather than a
     * partial drain: the outer loop still owns the queue and continues past whatever the nested
     * call would have run, so the queue is fully drained by the time the outer call returns. Use
     * {@link #isProcessing()} to tell the two situations apart.
     */
    public void processMicrotasks() {
        synchronized (queueLock) {
            if (executing) {
                return;
            }
            executing = true;
        }
        try {
            Microtask microtask;
            int processedCount = 0;
            while (true) {
                synchronized (queueLock) {
                    microtask = queue.poll();
                    if (microtask == null) {
                        break;
                    }
                }
                if (++processedCount % INTERRUPT_CHECK_INTERVAL == 0) {
                    context.getVirtualMachine().checkExecutionInterrupt();
                }
                try {
                    microtask.execute();
                } catch (RuntimeException | StackOverflowError e) {
                    handleMicrotaskFailure(e);
                }
            }
        } finally {
            synchronized (queueLock) {
                executing = false;
            }
        }
    }

    /**
     * Whether a drain is already in progress on this queue.
     *
     * @return true when {@link #processMicrotasks()} is running
     */
    public boolean isProcessing() {
        synchronized (queueLock) {
            return executing;
        }
    }

    /**
     * Report a failure that escaped a microtask.
     * <p>
     * A rejected promise with no handler goes to the promise reject callback as before. Everything
     * is additionally recorded on the context: previously, with no callback installed, every
     * exception from a microtask disappeared without trace — a throwing {@code .then()} handler, a
     * {@code JSVirtualMachineException}, or a {@link NullPointerException} from an engine defect.
     * <p>
     * A {@link com.caoccao.qjs4j.exceptions.JSTerminationException} never reaches here: it is an
     * {@link Error}, and the drain catches only {@link RuntimeException} and
     * {@link StackOverflowError}, so termination ends the drain and propagates to the embedder.
     *
     * @param failure the exception that escaped the microtask
     */
    private void handleMicrotaskFailure(Throwable failure) {
        IJSPromiseRejectCallback callback = context.getPromiseRejectCallback();
        if (callback != null && failure instanceof JSException jsException) {
            JSValue reason = jsException.getErrorValue();
            callback.callback(PromiseRejectEvent.PromiseRejectWithNoHandler, null, reason);
        }
        context.recordMicrotaskFailure(failure);
    }

    /**
     * Get the number of pending microtasks.
     *
     * @return The queue size
     */
    public int size() {
        synchronized (queueLock) {
            return queue.size();
        }
    }

    /**
     * Functional interface for microtask callbacks.
     */
    @FunctionalInterface
    public interface Microtask {
        /**
         * Execute the microtask.
         */
        void execute();
    }
}
