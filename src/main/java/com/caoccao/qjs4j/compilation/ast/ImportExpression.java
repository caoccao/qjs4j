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
 * Represents a dynamic import() expression.
 * <p>
 * import(source) or import(source, options)
 */
public final class ImportExpression extends Expression {
    private final boolean defer;
    private final Expression options;
    private final Expression source;

    public ImportExpression(
            Expression source,
            Expression options,
            boolean defer,
            SourceLocation location) {
        super(location);
        this.source = source;
        this.options = options;
        this.defer = defer;
    }

    public ImportExpression(
            Expression source,
            Expression options,
            SourceLocation location) {
        this(source, options, false, location);
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside != null) {
            return awaitInside;
        }
        boolean sourceContainsAwait = source != null && source.containsAwait();
        boolean optionsContainsAwait = options != null && options.containsAwait();
        awaitInside = sourceContainsAwait || optionsContainsAwait;
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside != null) {
            return yieldInside;
        }
        boolean sourceContainsYield = source != null && source.containsYield();
        boolean optionsContainsYield = options != null && options.containsYield();
        yieldInside = sourceContainsYield || optionsContainsYield;
        return yieldInside;
    }

    public boolean defer() {
        return defer;
    }

    public Expression options() {
        return options;
    }

    public Expression source() {
        return source;
    }
}
