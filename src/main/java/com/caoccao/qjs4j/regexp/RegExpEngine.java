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

package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.core.JSRuntimeOptions;

/**
 * Regular expression bytecode executor. Implements ES2020 regex semantics using a stack-based interpreter. Based on
 * QuickJS libregexp.c execution engine.
 */
public final class RegExpEngine {
    /**
     * Backtracking steps and start positions between two interrupt polls.
     */
    static final int INTERRUPT_POLL_INTERVAL = 4096;
    private final long backtrackLimit;
    private final RegExpBytecode bytecode;

    public RegExpEngine(RegExpBytecode bytecode) {
        this(bytecode, JSRuntimeOptions.DEFAULT_REGEXP_BACKTRACK_LIMIT);
    }

    /**
     * Create an engine with an explicit backtracking budget.
     *
     * @param bytecode
     *            the compiled pattern
     * @param backtrackLimit
     *            the maximum number of backtracking steps for one match attempt; 0 or negative means unbounded
     */
    public RegExpEngine(RegExpBytecode bytecode, long backtrackLimit) {
        this.bytecode = bytecode;
        this.backtrackLimit = Math.max(0L, backtrackLimit);
    }

    /**
     * Execute the regex against the input string starting at the given index.
     *
     * @param input
     *            The string to match against
     * @param startIndex
     *            The index to start matching from
     * @return The match result, or null if no match
     */
    public MatchResult exec(String input, int startIndex) {
        return exec(input, startIndex, null);
    }

    /**
     * Execute the regex, polling the host for an interrupt or a deadline as the match runs.
     * <p>
     * The matcher never polled, so an embedder that raised or removed the backtracking limit could not stop a match:
     * {@code JSRuntime.requestInterrupt()} and the execution deadline were ignored until it returned.
     *
     * @param input
     *            The string to match against
     * @param startIndex
     *            The index to start matching from
     * @param interruptProbe
     *            called every {@value #INTERRUPT_POLL_INTERVAL} backtracking steps and start positions; it throws to
     *            stop the match, or null not to poll
     * @return The match result, or null if no match
     */
    public MatchResult exec(String input, int startIndex, Runnable interruptProbe) {
        if (input == null || startIndex < 0 || startIndex > input.length()) {
            return null;
        }

        return RegExpMatcher.find(bytecode, input, startIndex, backtrackLimit, interruptProbe);
    }

    /**
     * Test if the regex matches the input.
     */
    public boolean test(String input) {
        return exec(input, 0) != null;
    }

    /**
     * Result of a regex match operation.
     *
     * @param matched
     *            Whether the pattern matched
     * @param startIndex
     *            Starting position of the match (in code points)
     * @param endIndex
     *            Ending position of the match (in code points)
     * @param captures
     *            Array of captured groups (including group 0 - the full match)
     * @param indices
     *            Array of [start, end] indices for each capture group
     */
    public record MatchResult(boolean matched, int startIndex, int endIndex, String[] captures, int[][] indices) {
        /**
         * Get a specific capture group.
         */
        public String getCapture(int index) {
            return matched && captures != null && index >= 0 && index < captures.length ? captures[index] : null;
        }

        /**
         * Get the full matched string.
         */
        public String getMatch() {
            return matched && captures != null && captures.length > 0 ? captures[0] : null;
        }
    }
}
