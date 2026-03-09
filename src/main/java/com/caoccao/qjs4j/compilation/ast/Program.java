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
 * Represents a complete program (script or module).
 */
public final class Program extends ASTNode {
    private final List<Statement> body;
    private final boolean isModule;
    private final boolean strict;

    public Program(List<Statement> body, boolean isModule, boolean strict, SourceLocation location) {
        super(location);
        this.body = body;
        this.isModule = isModule;
        this.strict = strict;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (body != null) {
                for (Statement item : body) {
                    if (item != null && item.containsAwait()) {
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
            if (body != null) {
                for (Statement item : body) {
                    if (item != null && item.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
        }
        return yieldInside;
    }

    public List<Statement> getBody() {
        return body;
    }

    public boolean isModule() {
        return isModule;
    }

    public boolean isStrict() {
        return strict;
    }

}
