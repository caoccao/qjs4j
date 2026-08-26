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

import com.caoccao.qjs4j.builtins.AtomicsObject;

import java.util.Objects;

/**
 * Runtime configuration options.
 */
public class JSRuntimeOptions {
    /**
     * The stack budget a single activation is charged.
     * <p>
     * A Java interpreter cannot measure its own stack the way QuickJS measures the C stack, so the
     * budget is spent in units of one nominal frame. The constant is what turns
     * {@link #setMaxStackSize(long)} into a call-depth limit; it is not a measurement of the JVM
     * frames an activation actually occupies.
     */
    public static final long BYTES_PER_STACK_FRAME = 256;
    /**
     * Default ceiling on {@code ArrayBuffer}/{@code SharedArrayBuffer} data blocks: 64 MiB.
     */
    public static final long DEFAULT_MAX_MEMORY_USAGE = 64 * 1024 * 1024;
    /**
     * Default interpreter stack budget: 256 KiB, which at
     * {@link #BYTES_PER_STACK_FRAME} is 1,024 nested activations.
     */
    public static final long DEFAULT_MAX_STACK_SIZE = 256 * 1024;
    /**
     * Default budget of regular expression backtracking steps for a single match attempt.
     * <p>
     * The matcher is a backtracking engine, so a pattern such as {@code /(a+)+$/} takes time
     * exponential in the input length. Without a budget a 40-character input hangs the calling
     * thread indefinitely, which is a single-request denial of service for any embedder that lets
     * guest scripts compile regular expressions. The budget is generous enough that realistic
     * patterns never reach it, and is raised or disabled with
     * {@link #setRegExpBacktrackLimit(long)}.
     */
    public static final long DEFAULT_REGEXP_BACKTRACK_LIMIT = 10_000_000L;
    /**
     * The {@link AtomicsObject} the embedder injected, or null.
     * <p>
     * Null is the ordinary case and means "one per runtime": every {@link JSRuntime} built from
     * these options constructs its own and closes it on shutdown. A non-null value is an agent
     * cluster's shared object, which belongs to whoever injected it and is never closed by a member
     * runtime.
     * <p>
     * These options used to construct one eagerly and hand it to whichever runtime claimed it
     * first. That made the default case indistinguishable from injection: every runtime built from
     * one options object shared a single instance, so closing the first one took
     * {@code Atomics.waitAsync} away from the others while they were still running.
     */
    protected AtomicsObject atomicsObject;
    protected long maxMemoryUsage;
    protected long maxStackSize;
    protected long regExpBacktrackLimit;
    protected boolean shadowRealmEnabled;
    protected boolean temporalEnabled;

    public JSRuntimeOptions() {
        maxMemoryUsage = DEFAULT_MAX_MEMORY_USAGE;
        maxStackSize = DEFAULT_MAX_STACK_SIZE;
        regExpBacktrackLimit = DEFAULT_REGEXP_BACKTRACK_LIMIT;
        shadowRealmEnabled = false;
        temporalEnabled = false;
    }

    /**
     * The shared {@link AtomicsObject} an agent cluster coordinates through, if one was injected.
     * <p>
     * A runtime does not read this after construction — it snapshots what it will use — so changing
     * it later cannot alter a runtime that already exists. Read a live runtime's instance from
     * {@link JSRuntime#getAtomicsObject()}.
     *
     * @return the injected instance, or null when each runtime makes its own
     */
    public AtomicsObject getAtomicsObject() {
        return atomicsObject;
    }

    /**
     * The ceiling on {@code ArrayBuffer} and {@code SharedArrayBuffer} data blocks.
     *
     * @return the limit in bytes, or 0 for no limit
     * @see #setMaxMemoryUsage(long)
     */
    public long getMaxMemoryUsage() {
        return maxMemoryUsage;
    }

    /**
     * The call depth {@link #getMaxStackSize()} buys, as
     * {@code maxStackSize / }{@link #BYTES_PER_STACK_FRAME}, clamped to at least one activation.
     *
     * @return the maximum number of nested activations
     */
    public int getMaxStackDepth() {
        long depth = maxStackSize / BYTES_PER_STACK_FRAME;
        if (depth < 1) {
            return 1;
        }
        return depth > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) depth;
    }

    /**
     * The interpreter stack budget.
     *
     * @return the budget in bytes
     * @see #setMaxStackSize(long)
     */
    public long getMaxStackSize() {
        return maxStackSize;
    }

    /**
     * Get the regular expression backtracking budget for a single match attempt.
     *
     * @return the maximum number of backtracking steps, or 0 for no limit
     */
    public long getRegExpBacktrackLimit() {
        return regExpBacktrackLimit;
    }

    /**
     * Whether an {@link AtomicsObject} was supplied by the embedder rather than left to each
     * runtime.
     *
     * @return true when an instance was injected
     */
    public boolean isAtomicsObjectInjected() {
        return atomicsObject != null;
    }

    public boolean isShadowRealmEnabled() {
        return shadowRealmEnabled;
    }

    public boolean isTemporalEnabled() {
        return temporalEnabled;
    }

    /**
     * Share one {@link AtomicsObject} between the runtimes of an agent cluster.
     * <p>
     * The instance stays the caller's: no runtime built from these options will close it, and each
     * of them only cancels the waits it started itself. Without this, every runtime makes and owns
     * its own, and {@code Atomics.wait}/{@code notify} coordinate only within that runtime.
     * <p>
     * A runtime snapshots the instance when it is constructed, so setting this afterwards affects
     * only runtimes created later.
     *
     * @param atomicsObject the shared instance
     * @return this
     */
    public JSRuntimeOptions setAtomicsObject(AtomicsObject atomicsObject) {
        this.atomicsObject = Objects.requireNonNull(atomicsObject);
        return this;
    }

    /**
     * Set the ceiling on {@code ArrayBuffer} and {@code SharedArrayBuffer} data blocks.
     * <p>
     * <strong>This bounds data blocks, not the heap.</strong> Every byte allocated for an
     * {@code ArrayBuffer} or {@code SharedArrayBuffer} — and so for every typed array and
     * {@code DataView} over one — is counted; exceeding the ceiling raises a catchable
     * {@code RangeError} in guest code. Objects, arrays, strings and bytecode are ordinary Java
     * allocations bounded by {@code -Xmx}. See {@link JSMemoryAccounting} for the full contract,
     * and read the ceiling back through {@link JSRuntime#getMemoryAccounting()}.
     * <p>
     * The limit is fixed when a {@link JSRuntime} is constructed; changing it on an options object
     * afterwards has no effect on runtimes already created from it.
     *
     * @param maxMemoryUsage the limit in bytes; 0 or negative for no limit
     * @return this
     */
    public JSRuntimeOptions setMaxMemoryUsage(long maxMemoryUsage) {
        this.maxMemoryUsage = Math.max(0L, maxMemoryUsage);
        return this;
    }

    /**
     * Set the interpreter stack budget.
     * <p>
     * The budget is spent in units of {@link #BYTES_PER_STACK_FRAME} per activation, so it fixes
     * the maximum call depth: exceeding it raises {@code RangeError: Maximum call stack size
     * exceeded}, which guest code can catch. It does not bound the JVM's own stack — deeply
     * recursive engine-internal work is bounded by {@code -Xss}.
     *
     * @param maxStackSize the budget in bytes; values below one frame are treated as one frame
     * @return this
     */
    public JSRuntimeOptions setMaxStackSize(long maxStackSize) {
        this.maxStackSize = Math.max(0L, maxStackSize);
        return this;
    }

    /**
     * Set the regular expression backtracking budget for a single match attempt.
     * <p>
     * Exceeding the budget raises {@code RangeError: regular expression execution exceeded the
     * backtracking limit}, which JavaScript can catch.
     *
     * @param regExpBacktrackLimit the maximum number of backtracking steps; 0 or negative disables
     *                             the limit and restores unbounded backtracking
     * @return this
     */
    public JSRuntimeOptions setRegExpBacktrackLimit(long regExpBacktrackLimit) {
        this.regExpBacktrackLimit = Math.max(0L, regExpBacktrackLimit);
        return this;
    }

    public JSRuntimeOptions setShadowRealmEnabled(boolean shadowRealmEnabled) {
        this.shadowRealmEnabled = shadowRealmEnabled;
        return this;
    }

    public JSRuntimeOptions setTemporalEnabled(boolean temporalEnabled) {
        this.temporalEnabled = temporalEnabled;
        return this;
    }
}
