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
 * Represents an arrow function expression.
 */
public final class ArrowFunctionExpression extends Expression {
    private final ASTNode body;
    private final List<Expression> defaults;
    private final boolean isAsync;
    private final List<Pattern> params;
    private final RestParameter restParameter;

    public ArrowFunctionExpression(
            List<Pattern> params,
            List<Expression> defaults,
            RestParameter restParameter,
            ASTNode body,
            boolean isAsync,
            SourceLocation location) {
        super(location);
        this.params = params;
        this.defaults = defaults;
        this.restParameter = restParameter;
        this.body = body;
        this.isAsync = isAsync;
    }

    @Override
    public boolean containsAwait() {
        // ES2024 8.1.4: Contains always returns false for function boundaries.
        return false;
    }

    @Override
    public boolean containsYield() {
        // ES2024 8.1.4: Contains always returns false for function boundaries.
        return false;
    }

    public ASTNode getBody() {
        return body;
    }

    public List<Expression> getDefaults() {
        return defaults;
    }

    public List<Pattern> getParams() {
        return params;
    }

    public RestParameter getRestParameter() {
        return restParameter;
    }

    public boolean isAsync() {
        return isAsync;
    }
}
