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

import java.util.List;

/**
 * Represents a function call expression.
 */
public final class CallExpression extends Expression {
    private final List<Expression> arguments;
    private final Expression callee;
    private final boolean optional;
    private final boolean partOfOptionalChain;
    private Boolean directEvalVarArgumentsInside;

    public CallExpression(
            Expression callee,
            List<Expression> arguments,
            boolean optional,
            SourceLocation location) {
        this(
                callee,
                arguments,
                optional,
                optional || (callee != null && callee.isPartOfOptionalChain()),
                location);
    }

    public CallExpression(
            Expression callee,
            List<Expression> arguments,
            boolean optional,
            boolean partOfOptionalChain,
            SourceLocation location) {
        super(location);
        this.callee = callee;
        this.arguments = arguments;
        this.optional = optional;
        this.partOfOptionalChain = partOfOptionalChain;
        directEvalVarArgumentsInside = null;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            boolean hasAwait = callee != null && callee.containsAwait();
            if (!hasAwait && arguments != null) {
                for (Expression argument : arguments) {
                    if (argument != null && argument.containsAwait()) {
                        hasAwait = true;
                        break;
                    }
                }
            }
            awaitInside = hasAwait;
        }
        return awaitInside;
    }

    @Override
    public boolean containsDirectEvalVarArguments() {
        if (directEvalVarArgumentsInside == null) {
            directEvalVarArgumentsInside = callee instanceof Identifier calleeIdentifier
                    && JSKeyword.EVAL.equals(calleeIdentifier.getName())
                    && arguments != null
                    && !arguments.isEmpty()
                    && arguments.get(0) instanceof Literal firstArgumentLiteral
                    && firstArgumentLiteral.getValue() instanceof String evalSourceString
                    && evalSourceString.contains("var arguments");
        }
        return directEvalVarArgumentsInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            boolean hasYield = callee != null && callee.containsYield();
            if (!hasYield && arguments != null) {
                for (Expression argument : arguments) {
                    if (argument != null && argument.containsYield()) {
                        hasYield = true;
                        break;
                    }
                }
            }
            yieldInside = hasYield;
        }
        return yieldInside;
    }

    public List<Expression> getArguments() {
        return arguments;
    }

    public Expression getCallee() {
        return callee;
    }

    @Override
    public boolean hasTailCallInTailPosition() {
        return getArguments().stream().noneMatch(arg -> arg instanceof SpreadElement)
                && !(getCallee() instanceof Identifier id && JSKeyword.SUPER.equals(id.getName()));
    }

    public boolean isDirectEvalCall() {
        if (isOptional()) {
            return false;
        }
        if (!(getCallee() instanceof Identifier calleeIdentifier)) {
            return false;
        }
        return JSKeyword.EVAL.equals(calleeIdentifier.getName());
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public boolean isPartOfOptionalChain() {
        return partOfOptionalChain;
    }
}
