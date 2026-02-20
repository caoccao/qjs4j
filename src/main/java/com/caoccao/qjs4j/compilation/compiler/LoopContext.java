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

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks loop context for break/continue statements.
 * Also used for labeled statements (where isRegularStmt is true).
 */
final class LoopContext {
    final List<Integer> breakPositions = new ArrayList<>();
    final int breakTargetScopeDepth;
    final List<Integer> continuePositions = new ArrayList<>();
    final int continueTargetScopeDepth;
    final String label;
    final int startOffset;
    boolean hasIterator;
    boolean isRegularStmt; // true for labeled non-loop statements (break allowed, continue not)

    LoopContext(int startOffset, int breakTargetScopeDepth, int continueTargetScopeDepth) {
        this(startOffset, breakTargetScopeDepth, continueTargetScopeDepth, null);
    }

    LoopContext(int startOffset, int breakTargetScopeDepth, int continueTargetScopeDepth, String label) {
        this.startOffset = startOffset;
        this.breakTargetScopeDepth = breakTargetScopeDepth;
        this.continueTargetScopeDepth = continueTargetScopeDepth;
        this.label = label;
    }
}
