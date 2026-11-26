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
    boolean containsSuspension(Suspension kind) {
        return kind.in(block) || kind.in(handler) || kind.in(finalizer);
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
            super(param != null
                    ? param.getLocation()
                    : (body != null ? body.getLocation() : new SourceLocation(0, 0, 0, 0)));
            this.param = param;
            this.body = body;
        }

        @Override
        boolean containsSuspension(Suspension kind) {
            return kind.in(param) || kind.in(body);
        }

        public BlockStatement getBody() {
            return body;
        }

        public Pattern getParam() {
            return param;
        }
    }

}
