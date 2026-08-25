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
    public static final long DEFAULT_MAX_MEMORY_USAGE = 64 * 1024 * 1024; // 64 MB default
    public static final long DEFAULT_MAX_STACK_SIZE = 256 * 1024; // 256 KB default
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
    protected AtomicsObject atomicsObject;
    protected long maxMemoryUsage;
    protected long maxStackSize;
    protected long regExpBacktrackLimit;
    protected boolean shadowRealmEnabled;
    protected boolean temporalEnabled;

    public JSRuntimeOptions() {
        atomicsObject = new AtomicsObject();
        maxMemoryUsage = DEFAULT_MAX_MEMORY_USAGE;
        maxStackSize = DEFAULT_MAX_STACK_SIZE;
        regExpBacktrackLimit = DEFAULT_REGEXP_BACKTRACK_LIMIT;
        shadowRealmEnabled = false;
        temporalEnabled = false;
    }

    public AtomicsObject getAtomicsObject() {
        return atomicsObject;
    }

    public long getMaxMemoryUsage() {
        return maxMemoryUsage;
    }

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

    public boolean isShadowRealmEnabled() {
        return shadowRealmEnabled;
    }

    public boolean isTemporalEnabled() {
        return temporalEnabled;
    }

    public JSRuntimeOptions setAtomicsObject(AtomicsObject atomicsObject) {
        this.atomicsObject = Objects.requireNonNull(atomicsObject);
        return this;
    }

    public JSRuntimeOptions setMaxMemoryUsage(long maxMemoryUsage) {
        this.maxMemoryUsage = maxMemoryUsage;
        return this;
    }

    public JSRuntimeOptions setMaxStackSize(long maxStackSize) {
        this.maxStackSize = maxStackSize;
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
