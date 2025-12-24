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

package com.caoccao.qjs4j.compiler.ast;

/**
 * Represents an assignment expression.
 */
public record AssignmentExpression(
        Expression left,
        AssignmentOperator operator,
        Expression right,
        SourceLocation location
) implements Expression {
    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public enum AssignmentOperator {
        ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, MUL_ASSIGN, DIV_ASSIGN,
        MOD_ASSIGN, EXP_ASSIGN, LSHIFT_ASSIGN, RSHIFT_ASSIGN,
        URSHIFT_ASSIGN, AND_ASSIGN, OR_ASSIGN, XOR_ASSIGN,
        LOGICAL_AND_ASSIGN, LOGICAL_OR_ASSIGN, NULLISH_ASSIGN
    }
}
