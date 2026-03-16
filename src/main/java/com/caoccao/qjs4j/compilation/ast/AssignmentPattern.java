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

package com.caoccao.qjs4j.compilation.ast;

import java.util.List;

/**
 * Represents a destructuring pattern with a default value.
 * Example: [x = defaultVal] or { y = defaultVal }.
 */
public final class AssignmentPattern extends Pattern {
    private final Pattern left;
    private final Expression right;

    public AssignmentPattern(Pattern left, Expression right, SourceLocation location) {
        super(location);
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = left != null && left.containsAwait();
            if (!awaitInside && right != null && right.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = left != null && left.containsYield();
            if (!yieldInside && right != null && right.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    @Override
    public List<String> getBoundNames() {
        if (boundNames == null) {
            boundNames = left == null ? List.of() : left.getBoundNames();
        }
        return boundNames;
    }

    public Pattern getLeft() {
        return left;
    }

    public Expression getRight() {
        return right;
    }

    @Override
    public String toPatternString() {
        return left.toPatternString() + " = ...";
    }
}
