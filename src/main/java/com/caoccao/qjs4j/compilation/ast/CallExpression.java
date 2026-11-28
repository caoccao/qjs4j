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
public final class CallExpression extends InvocationExpression {
    private final boolean optional;
    private final boolean optionalChainInside;
    private final boolean partOfOptionalChain;

    public CallExpression(Expression callee, List<Expression> arguments, boolean optional, boolean partOfOptionalChain,
            SourceLocation location) {
        super(callee, arguments, location);
        this.optional = optional;
        this.partOfOptionalChain = partOfOptionalChain;
        // See MemberExpression: cached so the parser's per-link question costs O(1).
        this.optionalChainInside = optional || callee.containsOptionalChain();
    }

    public CallExpression(Expression callee, List<Expression> arguments, boolean optional, SourceLocation location) {
        this(callee, arguments, optional, optional || (callee != null && callee.isPartOfOptionalChain()), location);
    }

    @Override
    boolean computeDirectEvalVarArguments() {
        List<Expression> arguments = getArguments();
        return getCallee() instanceof Identifier calleeIdentifier && JSKeyword.EVAL.equals(calleeIdentifier.getName())
                && arguments != null && !arguments.isEmpty() && arguments.get(0) instanceof Literal firstArgumentLiteral
                && firstArgumentLiteral.getValue() instanceof String evalSourceString
                && evalSourceString.contains("var arguments");
    }

    @Override
    public boolean containsOptionalChain() {
        return optionalChainInside;
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
