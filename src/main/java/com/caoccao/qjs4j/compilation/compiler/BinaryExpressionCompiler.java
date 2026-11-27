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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.compilation.ast.BinaryExpression;
import com.caoccao.qjs4j.compilation.ast.BinaryOperator;
import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.PrivateIdentifier;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;

final class BinaryExpressionCompiler extends AstNodeCompiler<BinaryExpression> {
    BinaryExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    /**
     * Compiles a binary expression and every binary expression on its left spine with a loop.
     * <p>
     * Left-associative chains ({@code a + b + c + ...}, {@code a || b || c ...}) nest on the left, so compiling the
     * left operand recursively costs one Java frame per operand and generated code with a few thousand operands
     * overflowed the stack. The loop below performs exactly the steps the recursion did, in the same order: descending
     * the spine, a short-circuit node saves the compiler state and disables tail calls for its left operand; ascending
     * it, each node restores that state, then compiles its right operand and emits its operator.
     */
    @Override
    void compile(BinaryExpression binExpr) {
        ArrayList<BinaryExpression> spine = new ArrayList<>();
        Expression leftmost = binExpr;
        while (leftmost instanceof BinaryExpression binaryExpression && !isPrivateIn(binaryExpression)) {
            spine.add(binaryExpression);
            if (!binaryExpression.getOperator().isEagerOperator()) {
                compilerContext.pushState();
                compilerContext.emitTailCalls = false;
            }
            leftmost = binaryExpression.getLeft();
        }
        if (leftmost instanceof BinaryExpression privateInExpression) {
            compilePrivateInExpression((PrivateIdentifier) privateInExpression.getLeft(),
                    privateInExpression.getRight());
        } else {
            compilerContext.expressionCompiler.compile(leftmost);
        }
        for (int i = spine.size() - 1; i >= 0; i--) {
            BinaryExpression binaryExpression = spine.get(i);
            if (binaryExpression.getOperator().isEagerOperator()) {
                compilerContext.expressionCompiler.compile(binaryExpression.getRight());
                // An operator can call valueOf, toString or Symbol.hasInstance; a frame stopped there reports
                // the operator's position, as V8 does.
                compilerContext.emitter.emitSourcePosition(binaryExpression.getLocation());
                compilerContext.emitter.emitOpcode(binaryExpression.getOperator().getOpcode());
            } else {
                compilerContext.popState();
                compileShortCircuitTail(binaryExpression);
            }
        }
    }

    private void compilePrivateInExpression(PrivateIdentifier privateIdentifier, Expression right) {
        compilerContext.expressionCompiler.compile(right);

        compilerContext.emitHelpers.emitPrivateSymbol(privateIdentifier);
        compilerContext.emitter.emitOpcode(Opcode.PRIVATE_IN);
    }

    private void compileShortCircuitTail(BinaryExpression binExpr) {
        // Stack: left
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        int jumpEnd = switch (binExpr.getOperator()) {
            // left && right: if left is falsy, return left; otherwise evaluate and return right
            case LOGICAL_AND -> compilerContext.emitter.emitJump(Opcode.IF_FALSE);
            // left || right: if left is truthy, return left; otherwise evaluate and return right
            case LOGICAL_OR -> compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            // left ?? right: if left is not null/undefined, return left; otherwise evaluate and return right
            default -> {
                compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
                yield compilerContext.emitter.emitJump(Opcode.IF_FALSE);
            }
        };
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.expressionCompiler.compile(binExpr.getRight());
        compilerContext.emitter.patchJumpToHere(jumpEnd);
    }

    private static boolean isPrivateIn(BinaryExpression binExpr) {
        return binExpr.getOperator() == BinaryOperator.IN && binExpr.getLeft() instanceof PrivateIdentifier;
    }
}
