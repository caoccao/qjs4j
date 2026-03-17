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

package com.caoccao.qjs4j.compilation.compiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Manages loop contexts for break/continue compilation.
 * Tracks the loop stack and pending loop labels.
 */
final class LoopManager implements Iterable<LoopContext> {
    private final Deque<LoopContext> loopStack;
    private String pendingLabel;

    LoopManager() {
        this.loopStack = new ArrayDeque<>();
    }

    void clearPendingLabel() {
        this.pendingLabel = null;
    }

    LoopContext createLoopContext(int startOffset, int breakScopeDepth, int continueScopeDepth) {
        String label = pendingLabel;
        pendingLabel = null;
        return new LoopContext(startOffset, breakScopeDepth, continueScopeDepth, label);
    }

    boolean hasActiveIteratorLoops() {
        for (LoopContext loopContext : loopStack) {
            if (loopContext.hasIterator) {
                return true;
            }
        }
        return false;
    }

    boolean isEmpty() {
        return loopStack.isEmpty();
    }

    @Override
    public Iterator<LoopContext> iterator() {
        return loopStack.iterator();
    }

    LoopContext popLoop() {
        return loopStack.pop();
    }

    void pushLoop(LoopContext loopContext) {
        loopStack.push(loopContext);
    }

    void setPendingLabel(String label) {
        this.pendingLabel = label;
    }
}
