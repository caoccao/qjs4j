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

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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
    /**
     * Terminal: once set, the runtime refuses every operation that would create or accept new work.
     */
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<JSContext> contexts;
    private final Map<String, JSSymbol> globalSymbolRegistry;
    private final Map<JSSymbol, String> globalSymbolReverseRegistry;
    private final Queue<Job> jobQueue;
    /**
     * Serialises admission against shutdown.
     * <p>
     * Setting {@link #closed} and clearing what the runtime owns were two independent steps, so a
     * producer that had already passed the closed check could be suspended, let {@code close()}
     * clear the queue, and then deposit its job into a runtime that had finished letting go of it.
     * The registry had the same window, because it tested {@code closed} outside its own monitor.
     * Every accepted mutation now happens under this lock, and {@code close()} clears under it —
     * after sealing intake, so a producer either gets in before the clear or is refused.
     */
    private final Object lifecycleLock = new Object();
    private final JSMemoryAccounting memoryAccounting;
    private final JSRuntimeOptions options;
    /**
     * Whether {@link #close()} is responsible for closing the runtime's {@link AtomicsObject}.
     */
    private final boolean ownsAtomicsObject;
    /**
     * The weak collections this runtime's keys have outlived.
     * <p>
     * A {@code WeakMap}/{@code WeakSet} entry lives on its key and holds its value strongly, so a
     * collection that dies while its keys live has to be noticed somewhere or the value is retained
     * for as long as the key is. Each entry is a weak reference registered here, and the weak
     * collection operations — and {@link #gc()} — drain the queue on the calling thread. Owning the
     * queue rather than sharing a process-wide {@code Cleaner} keeps reclamation on the thread that
     * is using the engine, which is the only thread allowed to touch anything reachable from a
     * {@link JSContext}.
     */
    private final ReferenceQueue<Object> weakCollectionOwners = new ReferenceQueue<>();
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
        this.memoryAccounting = new JSMemoryAccounting(options.getMaxMemoryUsage());
        // An AtomicsObject the options created belongs to this runtime alone, and its waitAsync
        // executor is a resource nothing else will release; one the embedder injected is an agent
        // cluster's shared object, and shutting it down here would break the other members.
        this.ownsAtomicsObject = options.claimAtomicsObjectOwnership();
    }

    /**
     * Clear a pending interrupt request so evaluation can resume.
     */
    public void clearInterrupt() {
        this.interruptRequested = false;
    }

    /**
     * Close the runtime. Terminal and idempotent.
     * <p>
     * Closing used to be advisory: there was no closed state, so after {@code close()} an embedder
     * could still create a context and evaluate in it, still enqueue jobs and still drain them, and
     * the global symbol registries kept whatever they held. Shutdown is now an ownership boundary —
     * {@link #createContext()}, {@link #enqueueJob(Job)} and
     * {@link #getOrCreateGlobalSymbol(String)} refuse afterwards, and everything the runtime owns
     * is released.
     * <p>
     * Asynchronous producers are stopped before the queues are cleared, so nothing can be deposited
     * into a runtime that has already let go of it: {@code Atomics.waitAsync} operations this
     * runtime started are cancelled, which also releases the promises and contexts an unbounded
     * wait would otherwise have pinned for the life of the process. Waits belonging to other
     * runtimes in the same agent cluster are untouched.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Intake is sealed by the line above; from here nothing new is admitted. Stop asynchronous
        // producers before clearing anything they could still write to.
        if (options != null && options.getAtomicsObject() != null) {
            if (ownsAtomicsObject) {
                // Nothing else shares this one, so its waitAsync executor goes with the runtime
                // rather than keeping a daemon thread alive for the cached pool's idle timeout.
                options.getAtomicsObject().close();
            } else {
                options.getAtomicsObject().cancelAsyncWaits(this);
            }
        }
        List<JSContext> contextSnapshot;
        synchronized (lifecycleLock) {
            // A producer that passed the closed check holds this lock, so its job lands before the
            // clear rather than after it. Only the fast clears happen here.
            jobQueue.clear();
            globalSymbolRegistry.clear();
            globalSymbolReverseRegistry.clear();
            contextSnapshot = getContextSnapshot();
            contexts.clear();
        }
        // Context teardown is slow and reaches back into the runtime, so it stays outside the lock.
        for (JSContext context : contextSnapshot) {
            if (context != null) {
                context.close();
            }
        }
        atoms.clear();
        currentExecutingContext = null;
        gc();
    }

    /**
     * Create a new execution context.
     *
     * @return the new context
     * @throws IllegalStateException when the runtime is closed
     */
    public JSContext createContext() {
        synchronized (lifecycleLock) {
            requireOpen();
            JSContext context = new JSContext(this);
            contexts.add(context);
            return context;
        }
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
     * @throws IllegalStateException when the runtime is closed
     */
    public void enqueueJob(Job job) {
        synchronized (lifecycleLock) {
            requireOpen();
            if (job != null) {
                jobQueue.offer(job);
            }
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
        releaseDeadWeakCollectionEntries();
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
        synchronized (lifecycleLock) {
            return globalSymbolReverseRegistry.get(symbol);
        }
    }

    /**
     * Accounting for the binary data blocks guest code sizes directly.
     * <p>
     * This is what makes {@link JSRuntimeOptions#setMaxMemoryUsage(long)} an enforced limit rather
     * than a stored number. See {@link JSMemoryAccounting} for exactly what it does and does not
     * bound.
     *
     * @return this runtime's accounting, never {@code null}
     */
    public JSMemoryAccounting getMemoryAccounting() {
        return memoryAccounting;
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
        synchronized (lifecycleLock) {
            requireOpen();
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
     * Whether {@link #close()} has run.
     *
     * @return true when the runtime is closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Clear the values of weak-collection entries whose collection has been collected.
     * <p>
     * Cheap when there is nothing to do: an empty queue poll and no allocation.
     */
    void releaseDeadWeakCollectionEntries() {
        JSWeakEntryTable.releaseDeadEntries(weakCollectionOwners);
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

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("JSRuntime is closed");
        }
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
        requireOpen();
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
     * The queue a new weak-collection entry registers itself with.
     *
     * @return this runtime's queue of collected weak collections
     */
    ReferenceQueue<Object> weakCollectionOwners() {
        return weakCollectionOwners;
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
