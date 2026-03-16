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

import com.caoccao.qjs4j.core.JSKeyword;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a function expression.
 */
public final class FunctionExpression extends Expression {
    private final BlockStatement body;
    private final FunctionParams functionParams;
    private final Identifier id;
    private final boolean isAsync;
    private final boolean isGenerator;
    private final boolean needsArguments;

    public FunctionExpression(
            Identifier id,
            FunctionParams functionParams,
            BlockStatement body,
            boolean isAsync,
            boolean isGenerator,
            boolean needsArguments,
            SourceLocation location) {
        super(location);
        this.id = id;
        this.functionParams = functionParams;
        this.body = body;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;
        this.needsArguments = needsArguments;
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

    public BlockStatement getBody() {
        return body;
    }

    public List<Expression> getDefaults() {
        return functionParams.defaults();
    }

    public FunctionParams getFunctionParams() {
        return functionParams;
    }

    public Identifier getId() {
        return id;
    }

    public Set<String> getParameterNames() {
        Set<String> paramNames = new HashSet<>();
        for (Pattern param : functionParams.params()) {
            paramNames.addAll(param.getBoundNames());
        }
        if (!paramNames.contains(JSKeyword.ARGUMENTS)) {
            boolean hasVarArguments = body.getBody().stream().anyMatch(Statement::containsVarArguments);
            if (!hasVarArguments) {
                paramNames.add(JSKeyword.ARGUMENTS);
            }
        }
        return paramNames;
    }

    public List<Pattern> getParams() {
        return functionParams.params();
    }

    public RestParameter getRestParameter() {
        return functionParams.restParameter();
    }

    @Override
    public boolean isAnonymousFunction() {
        return id == null;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public boolean isGenerator() {
        return isGenerator;
    }

    public boolean needsArguments() {
        return needsArguments;
    }
}
