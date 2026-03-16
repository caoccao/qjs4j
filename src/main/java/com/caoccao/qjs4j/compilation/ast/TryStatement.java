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

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = block != null && block.containsAwait();
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
            yieldInside = block != null && block.containsYield();
            if (!yieldInside && handler != null && handler.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && finalizer != null && finalizer.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public BlockStatement getBlock() {
        return block;
    }

    public BlockStatement getFinalizer() {
        return finalizer;
    }

    public CatchClause getHandler() {
        return handler;
    }

    @Override
    public List<VariableDeclarator> getVarDeclarators() {
        if (varDeclarators == null) {
            List<VariableDeclarator> collectedVarDeclarators = new ArrayList<>();
            if (block != null) {
                collectedVarDeclarators.addAll(block.getVarDeclarators());
            }
            if (handler != null && handler.getBody() != null) {
                collectedVarDeclarators.addAll(handler.getBody().getVarDeclarators());
            }
            if (finalizer != null) {
                collectedVarDeclarators.addAll(finalizer.getVarDeclarators());
            }
            varDeclarators = collectedVarDeclarators;
        }
        return varDeclarators;
    }

    public static final class CatchClause extends ASTNode {
        private final BlockStatement body;
        private final Pattern param;

        public CatchClause(Pattern param, BlockStatement body) {
            super(param != null ? param.getLocation() : (body != null ? body.getLocation() : new SourceLocation(0, 0, 0, 0)));
            this.param = param;
            this.body = body;
        }

        @Override
        public boolean containsAwait() {
            if (awaitInside == null) {
                awaitInside = param != null && param.containsAwait();
                if (!awaitInside && body != null && body.containsAwait()) {
                    awaitInside = true;
                }
            }
            return awaitInside;
        }

        @Override
        public boolean containsYield() {
            if (yieldInside == null) {
                yieldInside = param != null && param.containsYield();
                if (!yieldInside && body != null && body.containsYield()) {
                    yieldInside = true;
                }
            }
            return yieldInside;
        }

        public BlockStatement getBody() {
            return body;
        }

        public Pattern getParam() {
            return param;
        }
    }

}
