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
 * can enqueue microtasks from helper threads.
 */
public final class JSMicrotaskQueue {
    private final JSContext context;
    private final Object queueLock;
    private final ArrayDeque<Microtask> queue;
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
     * Microtasks can enqueue more microtasks, so this runs until the queue is empty.
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
            while (true) {
                synchronized (queueLock) {
                    microtask = queue.poll();
                    if (microtask == null) {
                        break;
                    }
                }
                try {
                    microtask.execute();
                } catch (Exception e) {
                    // Trigger unhandled rejection handler if set
                    IJSPromiseRejectCallback callback = context.getPromiseRejectCallback();
                    if (callback != null && e instanceof JSException jsException) {
                        JSValue reason = jsException.getErrorValue();
                        callback.callback(PromiseRejectEvent.PromiseRejectWithNoHandler, null, reason);
                    }
                }
            }
        } finally {
            synchronized (queueLock) {
                executing = false;
            }
        }
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
