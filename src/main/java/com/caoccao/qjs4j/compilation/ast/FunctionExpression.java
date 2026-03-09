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
 * Represents a function expression.
 */
public final class FunctionExpression extends Expression {
    private final BlockStatement body;
    private final List<Expression> defaults;
    private final Identifier id;
    private final boolean isAsync;
    private final boolean isGenerator;
    private final List<Pattern> params;
    private final RestParameter restParameter;

    public FunctionExpression(
            Identifier id,
            List<Pattern> params,
            List<Expression> defaults,
            RestParameter restParameter,
            BlockStatement body,
            boolean isAsync,
            boolean isGenerator,
            SourceLocation location) {
        super(location);
        this.id = id;
        this.params = params;
        this.defaults = defaults;
        this.restParameter = restParameter;
        this.body = body;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;
    }

    public BlockStatement body() {
        return body;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside != null) {
            return awaitInside;
        }
        awaitInside = false;
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside != null) {
            return yieldInside;
        }
        yieldInside = false;
        return yieldInside;
    }

    public List<Expression> defaults() {
        return defaults;
    }

    public Identifier id() {
        return id;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public boolean isGenerator() {
        return isGenerator;
    }

    public List<Pattern> params() {
        return params;
    }

    public RestParameter restParameter() {
        return restParameter;
    }
}
