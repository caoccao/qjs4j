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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StatementContainsAwaitYieldTest {
    private static final SourceLocation LOCATION = new SourceLocation(1, 1, 0);

    private static BlockStatement emptyBlock() {
        return new BlockStatement(List.of(), LOCATION);
    }

    @Test
    public void testClassDeclarationContainsAwaitFromSuperClass() {
        ClassDeclaration classDeclaration = new ClassDeclaration(
                new Identifier("C", LOCATION),
                new AwaitExpression(new Identifier("Base", LOCATION), LOCATION),
                List.of(),
                LOCATION);

        assertThat(classDeclaration.containsAwait()).isTrue();
        assertThat(classDeclaration.containsYield()).isFalse();
    }

    @Test
    public void testClassDeclarationContainsYieldFromStaticBlock() {
        ExpressionStatement yieldStatement = new ExpressionStatement(
                new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                LOCATION);
        ClassDeclaration classDeclaration = new ClassDeclaration(
                new Identifier("C", LOCATION),
                null,
                List.of(new StaticBlock(List.of(yieldStatement))),
                LOCATION);

        assertThat(classDeclaration.containsYield()).isTrue();
        assertThat(classDeclaration.containsAwait()).isFalse();
    }

    @Test
    public void testFunctionDeclarationContainsInnerAwaitAndYield() {
        ExpressionStatement awaitStatement = new ExpressionStatement(
                new AwaitExpression(new Literal(1, LOCATION), LOCATION),
                LOCATION);
        ExpressionStatement yieldStatement = new ExpressionStatement(
                new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                LOCATION);
        FunctionDeclaration functionDeclaration = new FunctionDeclaration(
                new Identifier("f", LOCATION),
                List.of(),
                List.of(),
                null,
                new BlockStatement(List.of(awaitStatement, yieldStatement), LOCATION),
                false,
                false,
                false,
                LOCATION);

        assertThat(functionDeclaration.containsAwait()).isTrue();
        assertThat(functionDeclaration.containsYield()).isTrue();
    }

    @Test
    public void testTryStatementContainsAwaitInCatchPatternDefault() {
        TryStatement.CatchClause catchClause = new TryStatement.CatchClause(
                new AssignmentPattern(
                        new Identifier("error", LOCATION),
                        new AwaitExpression(new Literal(1, LOCATION), LOCATION),
                        LOCATION),
                emptyBlock());
        TryStatement tryStatement = new TryStatement(emptyBlock(), catchClause, null, LOCATION);

        assertThat(tryStatement.containsAwait()).isTrue();
    }

    @Test
    public void testTryStatementContainsYieldInCatchPatternDefault() {
        TryStatement.CatchClause catchClause = new TryStatement.CatchClause(
                new AssignmentPattern(
                        new Identifier("error", LOCATION),
                        new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                        LOCATION),
                emptyBlock());
        TryStatement tryStatement = new TryStatement(emptyBlock(), catchClause, null, LOCATION);

        assertThat(tryStatement.containsYield()).isTrue();
    }

    @Test
    public void testVariableDeclarationContainsAwaitAndYieldFromPatternDefaults() {
        VariableDeclaration awaitDeclaration = new VariableDeclaration(
                List.of(new VariableDeclaration.VariableDeclarator(
                        new AssignmentPattern(
                                new Identifier("value", LOCATION),
                                new AwaitExpression(new Literal(1, LOCATION), LOCATION),
                                LOCATION),
                        null)),
                VariableKind.CONST,
                LOCATION);
        VariableDeclaration yieldDeclaration = new VariableDeclaration(
                List.of(new VariableDeclaration.VariableDeclarator(
                        new AssignmentPattern(
                                new Identifier("value", LOCATION),
                                new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                                LOCATION),
                        null)),
                VariableKind.CONST,
                LOCATION);

        assertThat(awaitDeclaration.containsAwait()).isTrue();
        assertThat(yieldDeclaration.containsYield()).isTrue();
    }
}
