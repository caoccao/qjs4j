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

public final class VariableDeclarator extends ASTNode {
    private final Pattern id;
    private final Expression init;

    public VariableDeclarator(Pattern id, Expression init) {
        super(SourceLocation.firstOf(id, init));
        this.id = id;
        this.init = init;
    }

    @Override
    boolean containsSuspension(SyntaxQuery kind) {
        return kind.in(id) || kind.in(init);
    }

    public Pattern getId() {
        return id;
    }

    public Expression getInit() {
        return init;
    }
}
