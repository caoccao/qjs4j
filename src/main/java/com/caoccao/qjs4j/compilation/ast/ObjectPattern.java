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
 * Represents an object destructuring pattern.
 * Example: { proxy, revoke } or { x: a, y: b }.
 */
public final class ObjectPattern extends Pattern {
    private final List<ObjectPatternProperty> properties;
    private final RestElement restElement;

    public ObjectPattern(List<ObjectPatternProperty> properties, RestElement restElement, SourceLocation location) {
        super(location);
        this.properties = properties;
        this.restElement = restElement;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (properties != null) {
                for (ObjectPatternProperty property : properties) {
                    if (property != null && property.containsAwait()) {
                        awaitInside = true;
                        break;
                    }
                }
            }
            if (!awaitInside && restElement != null && restElement.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (properties != null) {
                for (ObjectPatternProperty property : properties) {
                    if (property != null && property.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
            if (!yieldInside && restElement != null && restElement.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public List<ObjectPatternProperty> properties() {
        return properties;
    }

    public RestElement restElement() {
        return restElement;
    }

}
