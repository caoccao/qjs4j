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

import com.caoccao.qjs4j.memory.GarbageCollector;
import com.caoccao.qjs4j.util.AtomTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Represents a JavaScript runtime environment.
 * Based on QuickJS JSRuntime structure.
 *
 * The runtime is the top-level container that manages:
 * - Multiple execution contexts (JSContext)
 * - Shared atom table for string interning
 * - Garbage collector
 * - Job queue for promises and microtasks
 * - Runtime-wide limits and configuration
 *
 * A single runtime can have multiple contexts that share:
 * - Atom table (interned strings)
 * - Garbage collector
 * - Job queue
 *
 * But contexts have separate:
 * - Global objects
 * - Module caches
 * - Stack traces
 */
public final class JSRuntime {
    private final List<JSContext> contexts;
    private final GarbageCollector gc;
    private final AtomTable atoms;
    private final Queue<Job> jobQueue;
    private final RuntimeOptions options;

    // Runtime limits
    private long maxStackSize;
    private long maxMemoryUsage;
    private int interruptCheckCounter;

    /**
     * Create a new runtime with default options.
     */
    public JSRuntime() {
        this(new RuntimeOptions());
    }

    /**
     * Create a new runtime with custom options.
     */
    public JSRuntime(RuntimeOptions options) {
        this.contexts = new ArrayList<>();
        this.gc = new GarbageCollector();
        this.atoms = new AtomTable();
        this.jobQueue = new ConcurrentLinkedQueue<>();
        this.options = options;
        this.maxStackSize = options.maxStackSize;
        this.maxMemoryUsage = options.maxMemoryUsage;
        this.interruptCheckCounter = 0;
    }

    /**
     * Create a new execution context.
     */
    public JSContext createContext() {
        JSContext context = new JSContext(this);
        contexts.add(context);
        return context;
    }

    /**
     * Remove a context from this runtime.
     */
    public void destroyContext(JSContext context) {
        contexts.remove(context);
    }

    /**
     * Get all contexts in this runtime.
     */
    public List<JSContext> getContexts() {
        return new ArrayList<>(contexts);
    }

    /**
     * Run all pending jobs (microtasks).
     * This processes promise reactions and other microtasks.
     *
     * @return Number of jobs executed
     */
    public int runJobs() {
        int count = 0;
        while (!jobQueue.isEmpty()) {
            Job job = jobQueue.poll();
            if (job != null) {
                try {
                    job.run();
                    count++;
                } catch (Exception e) {
                    // In full implementation, this would be handled properly
                    // For now, just continue with next job
                }
            }
        }
        return count;
    }

    /**
     * Enqueue a job to be executed later.
     * Used for promise reactions and queueMicrotask().
     */
    public void enqueueJob(Job job) {
        if (job != null) {
            jobQueue.offer(job);
        }
    }

    /**
     * Check if there are pending jobs.
     */
    public boolean hasPendingJobs() {
        return !jobQueue.isEmpty();
    }

    /**
     * Get the atom table for this runtime.
     */
    public AtomTable getAtoms() {
        return atoms;
    }

    /**
     * Get the garbage collector.
     */
    public GarbageCollector getGarbageCollector() {
        return gc;
    }

    /**
     * Get runtime options.
     */
    public RuntimeOptions getOptions() {
        return options;
    }

    /**
     * Set maximum stack size in bytes.
     */
    public void setMaxStackSize(long bytes) {
        this.maxStackSize = bytes;
    }

    /**
     * Get maximum stack size.
     */
    public long getMaxStackSize() {
        return maxStackSize;
    }

    /**
     * Set maximum memory usage in bytes.
     */
    public void setMaxMemoryUsage(long bytes) {
        this.maxMemoryUsage = bytes;
    }

    /**
     * Get maximum memory usage.
     */
    public long getMaxMemoryUsage() {
        return maxMemoryUsage;
    }

    /**
     * Check if execution should be interrupted.
     * Called periodically during bytecode execution.
     */
    public boolean shouldInterrupt() {
        // In full implementation, this would check:
        // - Timeout limits
        // - Memory limits
        // - User-requested interrupts
        return false;
    }

    /**
     * Perform garbage collection.
     */
    public void gc() {
        gc.collectGarbage();
    }

    /**
     * Runtime configuration options.
     */
    public static class RuntimeOptions {
        public long maxStackSize = 256 * 1024; // 256 KB default
        public long maxMemoryUsage = 64 * 1024 * 1024; // 64 MB default
        public boolean enableBigInt = true;
        public boolean enableOperatorOverloading = false;
        public boolean enableDateExtensions = false;

        public RuntimeOptions() {
        }

        public RuntimeOptions maxStackSize(long bytes) {
            this.maxStackSize = bytes;
            return this;
        }

        public RuntimeOptions maxMemoryUsage(long bytes) {
            this.maxMemoryUsage = bytes;
            return this;
        }

        public RuntimeOptions enableBigInt(boolean enable) {
            this.enableBigInt = enable;
            return this;
        }
    }

    /**
     * A job to be executed in the job queue.
     * Used for promises, queueMicrotask, and other async operations.
     */
    @FunctionalInterface
    public interface Job {
        void run();
    }
}
