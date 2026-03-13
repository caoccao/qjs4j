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
 * Represents a function declaration.
 */
public final class FunctionDeclaration extends Declaration {
    private final BlockStatement body;
    private final List<Expression> defaults;
    private final Identifier id;
    private final boolean isAsync;
    private final boolean isGenerator;
    private final boolean needsArguments;
    private final List<Pattern> params;
    private final RestParameter restParameter;

    public FunctionDeclaration(Identifier id, List<Pattern> params, List<Expression> defaults, RestParameter restParameter, BlockStatement body, boolean isAsync, boolean isGenerator, boolean needsArguments, SourceLocation location) {
        super(location);
        this.id = id;
        this.params = params;
        this.defaults = defaults;
        this.restParameter = restParameter;
        this.body = body;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;
        this.needsArguments = needsArguments;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (params != null) {
                for (Pattern pattern : params) {
                    if (pattern != null && pattern.containsAwait()) {
                        awaitInside = true;
                        break;
                    }
                }
            }
            if (!awaitInside && defaults != null) {
                for (Expression defaultValue : defaults) {
                    if (defaultValue != null && defaultValue.containsAwait()) {
                        awaitInside = true;
                        break;
                    }
                }
            }
            if (!awaitInside && restParameter != null && restParameter.containsAwait()) {
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
            if (params != null) {
                for (Pattern pattern : params) {
                    if (pattern != null && pattern.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
            if (!yieldInside && defaults != null) {
                for (Expression defaultValue : defaults) {
                    if (defaultValue != null && defaultValue.containsYield()) {
                        yieldInside = true;
                        break;
                    }
                }
            }
            if (!yieldInside && restParameter != null && restParameter.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && body != null && body.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public BlockStatement getBody() {
        return body;
    }

    public List<Expression> getDefaults() {
        return defaults;
    }

    public Identifier getId() {
        return id;
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

    public boolean isGenerator() {
        return isGenerator;
    }

    public boolean needsArguments() {
        return needsArguments;
    }
}
