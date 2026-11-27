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

import com.caoccao.qjs4j.builtins.buffer.AtomicsObject;
import com.caoccao.qjs4j.core.module.IJSModuleLoader;
import com.caoccao.qjs4j.core.module.JSFileSystemModuleLoader;

import java.util.Objects;

/**
 * Runtime configuration options.
 */
public class JSRuntimeOptions {
    /**
     * The stack budget a single activation is charged by the deprecated {@link #setMaxStackSize(long)}.
     *
     * @deprecated the limit is a call depth; use {@link #setMaxCallDepth(int)}
     */
    @Deprecated
    public static final long BYTES_PER_STACK_FRAME = 256;
    /**
     * Default maximum call depth: 10,000 nested activations, about what V8 reaches for plain recursion.
     * <p>
     * Each activation also occupies Java stack — about 1.4 KiB for a plain call — so reaching the limit needs a thread
     * of about 16 MiB. On a smaller stack the JVM runs out first, and that too is reported to the script as a catchable
     * {@code RangeError: Maximum call stack size exceeded}; run evaluations on a thread with a larger stack, or raise
     * {@code -Xss}, to get the full depth.
     */
    public static final int DEFAULT_MAX_CALL_DEPTH = 10_000;
    /**
     * Default ceiling on {@code ArrayBuffer}/{@code SharedArrayBuffer} data blocks: 64 MiB.
     */
    public static final long DEFAULT_MAX_DATA_BLOCK_BYTES = 64 * 1024 * 1024;
    /**
     * Default ceiling on data blocks, under the name that read as a heap limit.
     *
     * @deprecated use {@link #DEFAULT_MAX_DATA_BLOCK_BYTES}
     */
    @Deprecated
    public static final long DEFAULT_MAX_MEMORY_USAGE = DEFAULT_MAX_DATA_BLOCK_BYTES;
    /**
     * The default call depth expressed as a deprecated stack budget.
     *
     * @deprecated use {@link #DEFAULT_MAX_CALL_DEPTH}
     */
    @Deprecated
    public static final long DEFAULT_MAX_STACK_SIZE = DEFAULT_MAX_CALL_DEPTH * BYTES_PER_STACK_FRAME;
    /**
     * Default budget of regular expression backtracking steps for a single match attempt.
     * <p>
     * The matcher is a backtracking engine, so a pattern such as {@code /(a+)+$/} takes time exponential in the input
     * length. Without a budget a 40-character input hangs the calling thread indefinitely, which is a single-request
     * denial of service for any embedder that lets guest scripts compile regular expressions. The budget is generous
     * enough that realistic patterns never reach it, and is raised or disabled with
     * {@link #setRegExpBacktrackLimit(long)}.
     */
    public static final long DEFAULT_REGEXP_BACKTRACK_LIMIT = 10_000_000L;
    /**
     * The {@link AtomicsObject} the embedder injected, or null.
     * <p>
     * Null is the ordinary case and means "one per runtime": every {@link JSRuntime} built from these options
     * constructs its own and closes it on shutdown. A non-null value is an agent cluster's shared object, which belongs
     * to whoever injected it and is never closed by a member runtime.
     * <p>
     * These options used to construct one eagerly and hand it to whichever runtime claimed it first. That made the
     * default case indistinguishable from injection: every runtime built from one options object shared a single
     * instance, so closing the first one took {@code Atomics.waitAsync} away from the others while they were still
     * running.
     */
    protected AtomicsObject atomicsObject;
    protected int maxCallDepth;
    protected long maxDataBlockBytes;
    protected IJSModuleLoader moduleLoader;
    protected long regExpBacktrackLimit;
    protected boolean shadowRealmEnabled;
    protected boolean temporalEnabled;

    public JSRuntimeOptions() {
        maxCallDepth = DEFAULT_MAX_CALL_DEPTH;
        maxDataBlockBytes = DEFAULT_MAX_DATA_BLOCK_BYTES;
        moduleLoader = null;
        regExpBacktrackLimit = DEFAULT_REGEXP_BACKTRACK_LIMIT;
        shadowRealmEnabled = false;
        temporalEnabled = false;
    }

    /**
     * The shared {@link AtomicsObject} an agent cluster coordinates through, if one was injected.
     * <p>
     * A runtime does not read this after construction — it snapshots what it will use — so changing it later cannot
     * alter a runtime that already exists. Read a live runtime's instance from {@link JSRuntime#getAtomicsObject()}.
     *
     * @return the injected instance, or null when each runtime makes its own
     */
    public AtomicsObject getAtomicsObject() {
        return atomicsObject;
    }

    /**
     * The maximum number of nested activations.
     *
     * @return the maximum call depth
     * @see #setMaxCallDepth(int)
     */
    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    /**
     * The ceiling on {@code ArrayBuffer} and {@code SharedArrayBuffer} data blocks.
     *
     * @return the limit in bytes, or 0 for no limit
     * @see #setMaxDataBlockBytes(long)
     */
    public long getMaxDataBlockBytes() {
        return maxDataBlockBytes;
    }

    /**
     * The ceiling on {@code ArrayBuffer} and {@code SharedArrayBuffer} data blocks.
     *
     * @return the limit in bytes, or 0 for no limit
     * @deprecated the limit bounds data blocks, not memory; use {@link #getMaxDataBlockBytes()}
     */
    @Deprecated
    public long getMaxMemoryUsage() {
        return maxDataBlockBytes;
    }

    /**
     * The maximum call depth.
     *
     * @return the maximum number of nested activations
     * @deprecated use {@link #getMaxCallDepth()}
     */
    @Deprecated
    public int getMaxStackDepth() {
        return maxCallDepth;
    }

    /**
     * The maximum call depth expressed as a stack budget of {@link #BYTES_PER_STACK_FRAME} per activation.
     *
     * @return the budget in bytes
     * @deprecated use {@link #getMaxCallDepth()}
     */
    @Deprecated
    public long getMaxStackSize() {
        return maxCallDepth * BYTES_PER_STACK_FRAME;
    }

    /**
     * The host hook every module is located and read through.
     *
     * @return the loader, or null when no module can be loaded
     * @see #setModuleLoader(IJSModuleLoader)
     */
    public IJSModuleLoader getModuleLoader() {
        return moduleLoader;
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
     * Whether an {@link AtomicsObject} was supplied by the embedder rather than left to each runtime.
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
     * The instance stays the caller's: no runtime built from these options will close it, and each of them only cancels
     * the waits it started itself. Without this, every runtime makes and owns its own, and
     * {@code Atomics.wait}/{@code notify} coordinate only within that runtime.
     * <p>
     * A runtime snapshots the instance when it is constructed, so setting this afterwards affects only runtimes created
     * later.
     *
     * @param atomicsObject
     *            the shared instance
     * @return this
     */
    public JSRuntimeOptions setAtomicsObject(AtomicsObject atomicsObject) {
        this.atomicsObject = Objects.requireNonNull(atomicsObject);
        return this;
    }

    /**
     * Set the maximum call depth.
     * <p>
     * A call nested deeper raises {@code RangeError: Maximum call stack size exceeded}, which guest code can catch. The
     * limit used to be a byte budget, {@link #setMaxStackSize(long)}, charged 256 nominal bytes per activation: 1,024
     * frames by default, a tenth of V8's. See {@link #DEFAULT_MAX_CALL_DEPTH} for the Java stack a given depth needs.
     *
     * @param maxCallDepth
     *            the maximum number of nested activations, at least 1
     * @return this
     * @throws IllegalArgumentException
     *             when the depth is below 1, which would refuse every call, the top-level code included
     */
    public JSRuntimeOptions setMaxCallDepth(int maxCallDepth) {
        if (maxCallDepth < 1) {
            throw new IllegalArgumentException("maxCallDepth must be at least 1: " + maxCallDepth);
        }
        this.maxCallDepth = maxCallDepth;
        return this;
    }

    /**
     * Set the ceiling on {@code ArrayBuffer} and {@code SharedArrayBuffer} data blocks.
     * <p>
     * <strong>This bounds data blocks, not the heap.</strong> Every byte allocated for an {@code ArrayBuffer} or
     * {@code SharedArrayBuffer} — and so for every typed array and {@code DataView} over one — is counted; exceeding
     * the ceiling raises a catchable {@code RangeError} in guest code. Objects, arrays, strings and bytecode are
     * ordinary Java allocations bounded by {@code -Xmx}. See {@link JSMemoryAccounting} for the full contract, and read
     * the ceiling back through {@link JSRuntime#getMemoryAccounting()}.
     * <p>
     * The default ceiling is 64 MiB. Raise it or pass zero to use larger buffers; segmented storage supports the 64-bit
     * sandboxed V8 length limit (32 GiB - 1) and commits pages on first write. Accounting charges logical capacity,
     * including unmaterialized pages, so sparse allocation does not bypass the host policy.
     * <p>
     * The limit is fixed when a {@link JSRuntime} is constructed; changing it on an options object afterwards has no
     * effect on runtimes already created from it.
     *
     * @param maxDataBlockBytes
     *            the limit in bytes; 0 or negative for no limit
     * @return this
     */
    public JSRuntimeOptions setMaxDataBlockBytes(long maxDataBlockBytes) {
        this.maxDataBlockBytes = Math.max(0L, maxDataBlockBytes);
        return this;
    }

    /**
     * Set the ceiling on {@code ArrayBuffer} and {@code SharedArrayBuffer} data blocks.
     *
     * @param maxMemoryUsage
     *            the limit in bytes; 0 or negative for no limit
     * @return this
     * @deprecated the name read as a heap limit, which this is not; use {@link #setMaxDataBlockBytes(long)}
     */
    @Deprecated
    public JSRuntimeOptions setMaxMemoryUsage(long maxMemoryUsage) {
        return setMaxDataBlockBytes(maxMemoryUsage);
    }

    /**
     * Set the maximum call depth as a stack budget of {@link #BYTES_PER_STACK_FRAME} per activation.
     *
     * @param maxStackSize
     *            the budget in bytes; values below one frame are treated as one frame
     * @return this
     * @deprecated use {@link #setMaxCallDepth(int)}
     */
    @Deprecated
    public JSRuntimeOptions setMaxStackSize(long maxStackSize) {
        long depth = maxStackSize / BYTES_PER_STACK_FRAME;
        this.maxCallDepth = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, depth));
        return this;
    }

    /**
     * Install the host hook every module is located and read through.
     * <p>
     * <strong>Without one, no module can be loaded.</strong> Every static {@code import}, dynamic {@code import()} and
     * {@code ShadowRealm.prototype.importValue} then fails with {@code TypeError: Cannot find module}, so a default
     * runtime gives scripts no access to the file system. Use {@link JSFileSystemModuleLoader} to load modules from
     * disk — confined to a root directory when the scripts are not trusted — or implement {@link IJSModuleLoader} to
     * serve them from anywhere else.
     * <p>
     * Runtimes read the loader when they load a module, so changing it affects every runtime built from these options.
     *
     * @param moduleLoader
     *            the loader, or null to load no modules
     * @return this
     */
    public JSRuntimeOptions setModuleLoader(IJSModuleLoader moduleLoader) {
        this.moduleLoader = moduleLoader;
        return this;
    }

    /**
     * Set the regular expression backtracking budget for a single match attempt.
     * <p>
     * Exceeding the budget raises {@code RangeError: regular expression execution exceeded the
     * backtracking limit}, which JavaScript can catch.
     *
     * @param regExpBacktrackLimit
     *            the maximum number of backtracking steps; 0 or negative disables the limit and restores unbounded
     *            backtracking
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
