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
 * Represents an array literal expression.
 */
public final class ArrayExpression extends Expression {
    private final List<Expression> elements;

    public ArrayExpression(List<Expression> elements, SourceLocation location) {
        super(location);
        this.elements = elements;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (elements != null) {
                for (Expression element : elements) {
                    if (element != null && element.containsAwait()) {
                        awaitInside = true;
                        break;
                    }
                }
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (elements != null) {
                for (Expression element : elements) {
                    if (element != null && element.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    public List<Expression> elements() {
        return elements;
    }
}
