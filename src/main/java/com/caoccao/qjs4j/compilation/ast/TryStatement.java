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
 * Represents a try statement.
 */
public final class TryStatement extends Statement {
    private final BlockStatement block;
    private final BlockStatement finalizer;
    private final CatchClause handler;

    public TryStatement(BlockStatement block, CatchClause handler, BlockStatement finalizer, SourceLocation location) {
        super(location);
        this.block = block;
        this.handler = handler;
        this.finalizer = finalizer;
    }

    public BlockStatement block() {
        return block;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (block != null && block.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && handler != null && handler.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && finalizer != null && finalizer.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (block != null && block.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && handler != null && handler.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && finalizer != null && finalizer.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public BlockStatement finalizer() {
        return finalizer;
    }

    public CatchClause handler() {
        return handler;
    }

    public static final class CatchClause extends ASTNode {
        private final BlockStatement body;
        private final Pattern param;

        public CatchClause(Pattern param, BlockStatement body) {
            super(param != null ? param.location() : (body != null ? body.location() : new SourceLocation(0, 0, 0, 0)));
            this.param = param;
            this.body = body;
        }

        public BlockStatement body() {
            return body;
        }

        @Override
        public boolean containsAwait() {
            if (awaitInside == null) {
                awaitInside = false;
                if (param != null && param.containsAwait()) {
                    awaitInside = true;
                }
                if (!awaitInside && body != null && body.containsAwait()) {
                    awaitInside = true;
                }
            }
            return awaitInside;
        }

        @Override
        public boolean containsYield() {
            if (yieldInside == null) {
                yieldInside = false;
                if (param != null && param.containsYield()) {
                    yieldInside = true;
                }
                if (!yieldInside && body != null && body.containsYield()) {
                    yieldInside = true;
                }
            }
            return yieldInside;
        }

        public Pattern param() {
            return param;
        }
    }

}
