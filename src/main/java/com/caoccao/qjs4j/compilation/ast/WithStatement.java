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

/**
 * Represents a with statement.
 */
public final class WithStatement extends Statement {
    private final Statement body;
    private final Expression object;

    public WithStatement(Expression object, Statement body, SourceLocation location) {
        super(location);
        this.object = object;
        this.body = body;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = object != null && object.containsAwait();
            if (!awaitInside && body != null && body.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = object != null && object.containsYield();
            if (!yieldInside && body != null && body.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Statement getBody() {
        return body;
    }

    public Expression getObject() {
        return object;
    }

}
