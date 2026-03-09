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
 * Represents a while statement.
 */
public final class WhileStatement extends Statement {
    private final Statement body;
    private final Expression test;

    public WhileStatement(Expression test, Statement body, SourceLocation location) {
        super(location);
        this.test = test;
        this.body = body;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = test != null && test.containsAwait();
            if (!awaitInside && body != null && body.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = test != null && test.containsYield();
            if (!yieldInside && body != null && body.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Statement getBody() {
        return body;
    }

    public Expression getTest() {
        return test;
    }

}
