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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpressionContainsAwaitYieldTest {
    private static final SourceLocation LOCATION = new SourceLocation(1, 1, 0);

    @Test
    public void testArrayExpressionShortCircuitsWhenContainmentResolved() {
        AwaitExpression awaitExpression = new AwaitExpression(new Literal(1, LOCATION), LOCATION);
        Identifier identifierForAwait = new Identifier("x", LOCATION);
        ArrayExpression awaitArrayExpression = new ArrayExpression(List.of(awaitExpression, identifierForAwait),
                LOCATION);

        assertThat(awaitArrayExpression.containsAwait()).isTrue();
        assertThat(ASTNode.SyntaxQuery.AWAIT.cachedResult(identifierForAwait)).isNull();

        YieldExpression yieldExpression = new YieldExpression(new Literal(1, LOCATION), false, LOCATION);
        Identifier identifierForYield = new Identifier("x", LOCATION);
        ArrayExpression yieldArrayExpression = new ArrayExpression(List.of(yieldExpression, identifierForYield),
                LOCATION);

        assertThat(yieldArrayExpression.containsYield()).isTrue();
        assertThat(ASTNode.SyntaxQuery.YIELD.cachedResult(identifierForYield)).isNull();
    }

    @Test
    public void testAwaitAndYieldAreCachedIndependently() {
        List<Expression> elements = new ArrayList<>();
        ArrayExpression array = new ArrayExpression(elements, LOCATION);
        assertThat(array.containsAwait()).isFalse();

        elements.add(new AwaitExpression(new Literal(1, LOCATION), LOCATION));
        elements.add(new YieldExpression(new Literal(2, LOCATION), false, LOCATION));
        assertThat(array.containsYield()).isTrue();
        assertThat(array.containsAwait()).isFalse();
    }

    @Test
    public void testClassExpressionContainsAwaitAndYield() {
        ClassExpression awaitClassExpression = new ClassExpression(null,
                new AwaitExpression(new Identifier("Base", LOCATION), LOCATION), List.of(), LOCATION);
        ClassExpression yieldClassExpression = new ClassExpression(null, null, List.of(new StaticBlock(List.of(
                new ExpressionStatement(new YieldExpression(new Literal(1, LOCATION), false, LOCATION), LOCATION)))),
                LOCATION);

        assertThat(awaitClassExpression.containsAwait()).isTrue();
        assertThat(yieldClassExpression.containsYield()).isTrue();
    }

    @Test
    public void testFunctionAndArrowExpressionContainmentFromBody() {
        FunctionExpression functionExpression = new FunctionExpression(null,
                new FunctionParams(List.of(), List.of(), null),
                new BlockStatement(List.of(
                        new ExpressionStatement(new AwaitExpression(new Literal(1, LOCATION), LOCATION), LOCATION),
                        new ExpressionStatement(new YieldExpression(new Literal(1, LOCATION), false, LOCATION),
                                LOCATION)),
                        LOCATION),
                false, false, false, LOCATION);
        ArrowFunctionExpression arrowFunctionExpression = new ArrowFunctionExpression(
                new FunctionParams(List.of(), List.of(), null),
                new SequenceExpression(List.of(new AwaitExpression(new Literal(1, LOCATION), LOCATION),
                        new YieldExpression(new Literal(1, LOCATION), false, LOCATION)), LOCATION),
                false, LOCATION);

        assertFunctionBoundary(functionExpression);
        assertFunctionBoundary(arrowFunctionExpression);
    }

    @Test
    public void testFunctionAndArrowExpressionContainmentFromDefaultsAndRest() {
        FunctionExpression functionExpression = new FunctionExpression(null, new FunctionParams(List.of(),
                List.of(new AwaitExpression(new Literal(1, LOCATION), LOCATION)),
                new RestParameter(new AssignmentPattern(new Identifier("a", LOCATION),
                        new YieldExpression(new Literal(1, LOCATION), false, LOCATION), LOCATION), LOCATION)),
                new BlockStatement(List.of(), LOCATION), false, false, false, LOCATION);
        ArrowFunctionExpression arrowFunctionExpression = new ArrowFunctionExpression(
                new FunctionParams(List.of(), List.of(new YieldExpression(new Literal(1, LOCATION), false, LOCATION)),
                        new RestParameter(new AssignmentPattern(new Identifier("a", LOCATION),
                                new AwaitExpression(new Literal(1, LOCATION), LOCATION), LOCATION), LOCATION)),
                new Identifier("body", LOCATION), false, LOCATION);

        assertFunctionBoundary(functionExpression);
        assertFunctionBoundary(arrowFunctionExpression);
    }

    @Test
    public void testLongBinaryChainIsWalkedWithoutRecursion() {
        // A left-deep chain one million operands long: a recursive walk down the left spine would
        // need one Java frame per operand.
        Expression awaitChain = new AwaitExpression(new Literal(1, LOCATION), LOCATION);
        Expression yieldChain = new Identifier("x", LOCATION);
        Expression evalChain = new Identifier("x", LOCATION);
        Identifier lastRightOfAwaitChain = null;
        for (int i = 0; i < 1_000_000; i++) {
            lastRightOfAwaitChain = new Identifier("x", LOCATION);
            awaitChain = new BinaryExpression(BinaryOperator.ADD, awaitChain, lastRightOfAwaitChain, LOCATION);
            yieldChain = new BinaryExpression(BinaryOperator.LOGICAL_OR, yieldChain, new Identifier("x", LOCATION),
                    LOCATION);
            evalChain = new BinaryExpression(BinaryOperator.ADD, evalChain, new Identifier("x", LOCATION), LOCATION);
        }
        yieldChain = new BinaryExpression(BinaryOperator.ADD, yieldChain,
                new YieldExpression(new Literal(1, LOCATION), false, LOCATION), LOCATION);
        evalChain = new BinaryExpression(BinaryOperator.ADD, evalChain,
                new CallExpression(new Identifier("eval", LOCATION), List.of(new Literal("var arguments", LOCATION)),
                        false, LOCATION),
                LOCATION);

        assertThat(awaitChain.containsAwait()).isTrue();
        assertThat(awaitChain.containsYield()).isFalse();
        // The await is the leftmost operand, so no right operand needed to be asked.
        assertThat(ASTNode.SyntaxQuery.AWAIT.cachedResult(lastRightOfAwaitChain)).isNull();
        assertThat(yieldChain.containsYield()).isTrue();
        assertThat(yieldChain.containsAwait()).isFalse();
        assertThat(evalChain.containsDirectEvalVarArguments()).isTrue();
        assertThat(awaitChain.containsDirectEvalVarArguments()).isFalse();
        // Cached on every node of the spine.
        assertThat(((BinaryExpression) yieldChain).getLeft().containsYield()).isFalse();
    }

    /** ES2024 8.1.4 stops Contains at an ordinary or arrow function boundary. */
    private static void assertFunctionBoundary(Expression function) {
        assertThat(function.containsAwait()).isFalse();
        assertThat(function.containsYield()).isFalse();
    }
}
