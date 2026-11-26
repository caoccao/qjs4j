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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an arrow function expression.
 */
public final class ArrowFunctionExpression extends Expression implements FunctionNode {
    private final ASTNode body;
    private final FunctionParams functionParams;
    private final boolean isAsync;

    public ArrowFunctionExpression(FunctionParams functionParams, ASTNode body, boolean isAsync,
            SourceLocation location) {
        super(location);
        this.functionParams = functionParams;
        this.body = body;
        this.isAsync = isAsync;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public FunctionParams getFunctionParams() {
        return functionParams;
    }

    @Override
    public boolean isAnonymousFunction() {
        return true;
    }

    public boolean isAsync() {
        return isAsync;
    }
}
