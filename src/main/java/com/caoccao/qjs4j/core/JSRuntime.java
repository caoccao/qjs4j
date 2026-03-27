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

import com.caoccao.qjs4j.utils.AtomTable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Represents a JavaScript runtime environment.
 * Based on QuickJS JSRuntime structure.
 * <p>
 * The runtime is the top-level container that manages:
 * - Multiple execution contexts (JSContext)
 * - Shared atom table for string interning
 * - JVM garbage collection hints
 * - Job queue for promises and microtasks
 * - Runtime-wide limits and configuration
 * <p>
 * A single runtime can have multiple contexts that share:
 * - Atom table (interned strings)
 * - JVM garbage collection hints
 * - Job queue
 * <p>
 * But contexts have separate:
 * - Global objects
 * - Module caches
 * - Stack traces
 */
public final class JSRuntime implements AutoCloseable {
    private final AtomTable atoms;
    private final List<JSContext> contexts;
    private final Map<String, JSSymbol> globalSymbolRegistry;
    private final Map<JSSymbol, String> globalSymbolReverseRegistry;
    private final Queue<Job> jobQueue;
    private final JSRuntimeOptions options;
    private JSContext currentExecutingContext;

    /**
     * Create a new runtime with default options.
     */
    public JSRuntime() {
        this(new JSRuntimeOptions());
    }

    /**
     * Create a new runtime with custom options.
     * If {@link JSRuntimeOptions#atomicsObject} is set, that shared instance is used
     * so multiple runtimes in the same agent cluster can coordinate via Atomics.wait/notify.
     * Otherwise a new AtomicsObject is created for this runtime.
     */
    public JSRuntime(JSRuntimeOptions options) {
        this.contexts = new ArrayList<>();
        this.atoms = new AtomTable();
        this.jobQueue = new ConcurrentLinkedQueue<>();
        this.globalSymbolRegistry = new HashMap<>();
        this.globalSymbolReverseRegistry = new HashMap<>();
        this.options = options;
    }

    @Override
    public void close() {
        jobQueue.clear();
        for (JSContext context : new ArrayList<>(contexts)) {
            context.close();
        }
        atoms.clear();
        gc();
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
     * Enqueue a job to be executed later.
     * Used for promise reactions and queueMicrotask().
     */
    public void enqueueJob(Job job) {
        if (job != null) {
            jobQueue.offer(job);
        }
    }

    /**
     * Trigger JVM garbage collection and then poll finalization registries.
     */
    public void gc() {
        List<JSContext> contextSnapshot = new ArrayList<>(contexts);
        for (JSContext context : contextSnapshot) {
            context.pollFinalizationRegistries();
        }
    }

    /**
     * Get the atom table for this runtime.
     */
    public AtomTable getAtoms() {
        return atoms;
    }

    /**
     * Get all contexts in this runtime.
     */
    public List<JSContext> getContexts() {
        return new ArrayList<>(contexts);
    }

    public JSContext getCurrentExecutingContext() {
        return currentExecutingContext;
    }

    /**
     * Get the key for a runtime-global symbol, or null if the symbol is not in the runtime registry.
     */
    public String getGlobalSymbolKey(JSSymbol symbol) {
        synchronized (globalSymbolRegistry) {
            return globalSymbolReverseRegistry.get(symbol);
        }
    }

    /**
     * Get runtime options.
     */
    public JSRuntimeOptions getOptions() {
        return options;
    }

    /**
     * Get or create a runtime-global symbol by key.
     */
    public JSSymbol getOrCreateGlobalSymbol(String key) {
        synchronized (globalSymbolRegistry) {
            JSSymbol existing = globalSymbolRegistry.get(key);
            if (existing != null) {
                return existing;
            }
            JSSymbol symbol = new JSSymbol(key, true);
            globalSymbolRegistry.put(key, symbol);
            globalSymbolReverseRegistry.put(symbol, key);
            return symbol;
        }
    }

    /**
     * Check if there are pending jobs.
     */
    public boolean hasPendingJobs() {
        return !jobQueue.isEmpty();
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
                job.run();
                count++;
            }
        }
        return count;
    }

    /**
     * Check if execution should be interrupted.
     * Called periodically during bytecode execution.
     */
    public void setCurrentExecutingContext(JSContext context) {
        this.currentExecutingContext = context;
    }

    public boolean shouldInterrupt() {
        // In full implementation, this would check:
        // - Timeout limits
        // - Memory limits
        // - User-requested interrupts
        return false;
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
