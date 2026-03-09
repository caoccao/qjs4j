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
 * Represents a do-while statement.
 */
public final class DoWhileStatement extends Statement {
    private final Statement body;
    private final Expression test;

    public DoWhileStatement(Statement body, Expression test, SourceLocation location) {
        super(location);
        this.body = body;
        this.test = test;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = body != null && body.containsAwait();
            if (!awaitInside && test != null && test.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = body != null && body.containsYield();
            if (!yieldInside && test != null && test.containsYield()) {
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
