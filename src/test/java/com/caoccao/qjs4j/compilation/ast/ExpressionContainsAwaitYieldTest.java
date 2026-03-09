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

public class ExpressionContainsAwaitYieldTest {
    private static final SourceLocation LOCATION = new SourceLocation(1, 1, 0);

    @Test
    public void testArrayExpressionShortCircuitsWhenContainmentResolved() {
        AwaitExpression awaitExpression = new AwaitExpression(new Literal(1, LOCATION), LOCATION);
        Identifier identifierForAwait = new Identifier("x", LOCATION);
        ArrayExpression awaitArrayExpression = new ArrayExpression(
                List.of(awaitExpression, identifierForAwait),
                LOCATION);

        assertThat(awaitArrayExpression.containsAwait()).isTrue();
        assertThat(identifierForAwait.awaitInside).isNull();

        YieldExpression yieldExpression = new YieldExpression(new Literal(1, LOCATION), false, LOCATION);
        Identifier identifierForYield = new Identifier("x", LOCATION);
        ArrayExpression yieldArrayExpression = new ArrayExpression(
                List.of(yieldExpression, identifierForYield),
                LOCATION);

        assertThat(yieldArrayExpression.containsYield()).isTrue();
        assertThat(identifierForYield.yieldInside).isNull();
    }

    @Test
    public void testClassExpressionContainsAwaitAndYield() {
        ClassExpression awaitClassExpression = new ClassExpression(
                null,
                new AwaitExpression(new Identifier("Base", LOCATION), LOCATION),
                List.of(),
                LOCATION);
        ClassExpression yieldClassExpression = new ClassExpression(
                null,
                null,
                List.of(new StaticBlock(List.of(
                        new ExpressionStatement(
                                new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                                LOCATION)))),
                LOCATION);

        assertThat(awaitClassExpression.containsAwait()).isTrue();
        assertThat(yieldClassExpression.containsYield()).isTrue();
    }

    @Test
    public void testFunctionAndArrowExpressionContainmentFromDefaultsAndRest() {
        FunctionExpression functionExpression = new FunctionExpression(
                null,
                List.of(),
                List.of(new AwaitExpression(new Literal(1, LOCATION), LOCATION)),
                new RestParameter(
                        new AssignmentPattern(
                                new Identifier("a", LOCATION),
                                new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                                LOCATION),
                        LOCATION),
                new BlockStatement(List.of(), LOCATION),
                false,
                false,
                LOCATION);
        ArrowFunctionExpression arrowFunctionExpression = new ArrowFunctionExpression(
                List.of(),
                List.of(new YieldExpression(new Literal(1, LOCATION), false, LOCATION)),
                new RestParameter(
                        new AssignmentPattern(
                                new Identifier("a", LOCATION),
                                new AwaitExpression(new Literal(1, LOCATION), LOCATION),
                                LOCATION),
                        LOCATION),
                new Identifier("body", LOCATION),
                false,
                LOCATION);

        assertThat(functionExpression.containsAwait()).isTrue();
        assertThat(functionExpression.containsYield()).isTrue();
        assertThat(arrowFunctionExpression.containsAwait()).isTrue();
        assertThat(arrowFunctionExpression.containsYield()).isTrue();
    }

    @Test
    public void testFunctionAndArrowExpressionContainmentFromBody() {
        FunctionExpression functionExpression = new FunctionExpression(
                null,
                List.of(),
                List.of(),
                null,
                new BlockStatement(List.of(
                        new ExpressionStatement(
                                new AwaitExpression(new Literal(1, LOCATION), LOCATION),
                                LOCATION),
                        new ExpressionStatement(
                                new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                                LOCATION)),
                        LOCATION),
                false,
                false,
                LOCATION);
        ArrowFunctionExpression arrowFunctionExpression = new ArrowFunctionExpression(
                List.of(),
                List.of(),
                null,
                new SequenceExpression(List.of(
                        new AwaitExpression(new Literal(1, LOCATION), LOCATION),
                        new YieldExpression(new Literal(1, LOCATION), false, LOCATION)),
                        LOCATION),
                false,
                LOCATION);

        assertThat(functionExpression.containsAwait()).isTrue();
        assertThat(functionExpression.containsYield()).isTrue();
        assertThat(arrowFunctionExpression.containsAwait()).isTrue();
        assertThat(arrowFunctionExpression.containsYield()).isTrue();
    }
}
