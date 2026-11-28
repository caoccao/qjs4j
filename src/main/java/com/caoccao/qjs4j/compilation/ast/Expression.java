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
 * Base sealed class for all expression nodes.
 */
public abstract sealed class Expression extends Pattern
        permits Literal, Identifier, PrivateIdentifier, BinaryExpression, UnaryExpression, AssignmentExpression,
        ConditionalExpression, InvocationExpression, MemberExpression, FunctionExpression, ArrowFunctionExpression,
        ArrayExpression, ObjectExpression, AwaitExpression, YieldExpression, TemplateLiteral, TaggedTemplateExpression,
        ClassExpression, SpreadElement, SequenceExpression, ImportExpression {
    protected Expression(SourceLocation location) {
        super(location);
    }

    boolean computeDirectEvalVarArguments() {
        return false;
    }

    public final boolean containsDirectEvalVarArguments() {
        return SyntaxQuery.DIRECT_EVAL_VAR_ARGUMENTS.in(this);
    }

    /**
     * Whether this expression is a member access or call whose chain of objects and callees contains an optional
     * {@code ?.} link, parenthesised or not.
     *
     * @return true when the chain contains an optional link
     */
    public boolean containsOptionalChain() {
        return false;
    }

    public boolean hasTailCallInTailPosition() {
        return false;
    }

    public boolean isAnonymousFunction() {
        return false;
    }

    public boolean isPartOfOptionalChain() {
        return false;
    }

    public boolean isSuperIdentifier() {
        return this instanceof Identifier identifier && JSKeyword.SUPER.equals(identifier.getName());
    }

    @Override
    public String toPatternString() {
        return "?";
    }

}
