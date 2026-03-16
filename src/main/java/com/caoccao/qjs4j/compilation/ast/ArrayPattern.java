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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an array destructuring pattern.
 * Example: [a, b] or [x, , z]
 */
public final class ArrayPattern extends Pattern {
    private final List<Pattern> elements;

    public ArrayPattern(List<Pattern> elements, SourceLocation location) {
        super(location);
        this.elements = elements;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (elements != null) {
                for (Pattern element : elements) {
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
                for (Pattern element : elements) {
                    if (element != null && element.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    @Override
    public List<String> getBoundNames() {
        if (boundNames == null) {
            List<String> collectedBoundNames = new ArrayList<>();
            if (elements != null) {
                for (Pattern element : elements) {
                    if (element != null) {
                        collectedBoundNames.addAll(element.getBoundNames());
                    }
                }
            }
            boundNames = List.copyOf(collectedBoundNames);
        }
        return boundNames;
    }

    public List<Pattern> getElements() {
        return elements;
    }

    @Override
    public String toPatternString() {
        StringBuilder stringBuilder = new StringBuilder("[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                stringBuilder.append(", ");
            }
            Pattern element = elements.get(i);
            if (element != null) {
                stringBuilder.append(element.toPatternString());
            }
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
