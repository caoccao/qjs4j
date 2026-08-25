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
 * <h2>Threading</h2>
 * <strong>A {@link JSContext} is confined to one thread.</strong> {@code JSContext},
 * {@code JSObject}, {@code JSArray}, {@code VirtualMachine}, {@code JSShape} and {@code AtomTable}
 * have no synchronisation at all, and {@code JSShape} in particular carries a mutable single-entry
 * lookup memo that concurrent readers would tear. Evaluating on two threads against one context —
 * or against two contexts that share values — is undefined behaviour.
 * <p>
 * Only three things on a {@code JSRuntime} are safe to touch from another thread:
 * <ul>
 * <li>{@link #requestInterrupt()} and {@link #clearInterrupt()}, which is the supported way to stop
 * a running evaluation;</li>
 * <li>the global symbol registry ({@link #getOrCreateGlobalSymbol(String)},
 * {@link #getGlobalSymbolKey(JSSymbol)}), which is synchronised;</li>
 * <li>enqueueing work — {@link #enqueueJob(Job)} and {@code JSContext.enqueueMicrotask}, which some
 * host integrations (for example {@code Atomics.waitAsync}) call from helper threads. Enqueueing is
 * safe; <em>draining</em> the queue must happen on the owning thread.</li>
 * </ul>
 * The collections here are concurrent so those three paths are sound. That is the whole extent of
 * the guarantee: it does not make the engine thread-safe.
 */
public final class JSRuntime implements AutoCloseable {
    private final AtomTable atoms;
    private final List<JSContext> contexts;
    private final Map<String, JSSymbol> globalSymbolRegistry;
    private final Map<JSSymbol, String> globalSymbolReverseRegistry;
    private final Queue<Job> jobQueue;
    private final JSRuntimeOptions options;
    /**
     * Written by {@code VirtualMachine.execute} on the evaluating thread and read from cross-realm
     * proxy paths. Declared volatile so a reader never observes a stale context.
     */
    private volatile JSContext currentExecutingContext;
    /**
     * Set from any thread by {@link #requestInterrupt()} and polled by the interpreter loop.
     */
    private volatile boolean interruptRequested;

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
        this.contexts = Collections.synchronizedList(new ArrayList<>());
        this.atoms = new AtomTable();
        this.jobQueue = new ConcurrentLinkedQueue<>();
        this.globalSymbolRegistry = new HashMap<>();
        this.globalSymbolReverseRegistry = new HashMap<>();
        this.options = options;
    }

    /**
     * Clear a pending interrupt request so evaluation can resume.
     */
    public void clearInterrupt() {
        this.interruptRequested = false;
    }

    @Override
    public void close() {
        jobQueue.clear();
        for (JSContext context : getContextSnapshot()) {
            if (context != null) {
                context.close();
            }
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
     * Enqueue a host job to run on the next {@link #runJobs()} drain.
     * <p>
     * This is the embedder's entry point for scheduling work alongside the engine's own promise
     * reactions. It is <em>not</em> where those reactions live: promise reactions and
     * {@code queueMicrotask} go to the owning context's microtask queue, which {@link #runJobs()}
     * deliberately does <strong>not</strong> drain. Settling promises requires
     * {@link JSContext#processMicrotasks()} on the context that owns them.
     *
     * @param job the job to enqueue; {@code null} is ignored
     */
    public void enqueueJob(Job job) {
        if (job != null) {
            jobQueue.offer(job);
        }
    }

    /**
     * Poll every context's finalization registries.
     * <p>
     * This does <strong>not</strong> ask the JVM to collect, despite the name — it drains
     * {@code FinalizationRegistry} callbacks for objects the JVM has <em>already</em> collected,
     * which is all an engine can usefully do. Deciding when to collect belongs to the embedder:
     * call {@link System#gc()} yourself if you want that, then call this to drain the callbacks it
     * makes eligible.
     * <p>
     * The Javadoc here used to say it triggered a collection, which it never did. Making it
     * actually call {@code System.gc()} is not the fix: {@link #close()} calls this method, so an
     * embedder that creates a runtime per unit of work would pay a full JVM collection every time.
     */
    public void gc() {
        List<JSContext> contextSnapshot = getContextSnapshot();
        for (JSContext context : contextSnapshot) {
            if (context != null) {
                context.pollFinalizationRegistries();
            }
        }
    }

    /**
     * Get the atom table for this runtime.
     */
    public AtomTable getAtoms() {
        return atoms;
    }

    private List<JSContext> getContextSnapshot() {
        synchronized (contexts) {
            return new ArrayList<>(contexts);
        }
    }

    /**
     * Get all contexts in this runtime.
     */
    public List<JSContext> getContexts() {
        return getContextSnapshot();
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
     * Check whether any host job is pending on this runtime.
     * <p>
     * This covers the host queue only. Promise reactions and {@code queueMicrotask} live on the
     * owning {@link JSContext}'s microtask queue — ask
     * {@code context.getMicrotaskQueue().hasPendingMicrotasks()} for those.
     *
     * @return true when a host job enqueued with {@link #enqueueJob(Job)} is waiting
     */
    public boolean hasPendingJobs() {
        return !jobQueue.isEmpty();
    }

    /**
     * Ask any evaluation running on this runtime to stop.
     * <p>
     * Safe to call from another thread. The interpreter polls this every
     * {@code INTERRUPT_CHECK_INTERVAL} opcodes and terminates with an exception that JavaScript
     * {@code try}/{@code catch} cannot intercept, so a script cannot keep itself alive by
     * swallowing the signal. The flag stays set until {@link #clearInterrupt()} is called, so a
     * later evaluation on this runtime would also stop immediately.
     */
    public void requestInterrupt() {
        this.interruptRequested = true;
    }

    /**
     * Run all pending host jobs on this runtime.
     * <p>
     * <strong>This drains the host queue only.</strong> It deliberately does <em>not</em> touch any
     * context's microtask queue, for two reasons: a microtask queue belongs to one
     * {@link JSContext} and must be drained on that context's own thread (see the threading
     * contract on this class), and draining it here would be unbounded — a microtask that
     * re-enqueues itself would spin forever with no deadline to stop it.
     * <p>
     * To settle promises, call {@link JSContext#processMicrotasks()} on the context that owns them.
     * {@link JSContext#eval} already does so before returning.
     *
     * @return the number of host jobs executed
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
     * Record the context whose bytecode is currently executing.
     * Used by cross-realm proxy paths to find the active realm.
     *
     * @param context the executing context, or {@code null} when execution has finished
     */
    public void setCurrentExecutingContext(JSContext context) {
        this.currentExecutingContext = context;
    }

    /**
     * Check if execution should be interrupted.
     * Called periodically during bytecode execution.
     *
     * @return true when a host thread has called {@link #requestInterrupt()}
     */
    public boolean shouldInterrupt() {
        return interruptRequested;
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
