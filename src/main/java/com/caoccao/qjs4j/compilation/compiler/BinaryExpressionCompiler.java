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

final class BinaryExpressionCompiler {
    private final CompilerContext compilerContext;

    BinaryExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(BinaryExpression binExpr) {
        if (binExpr.getOperator() == BinaryOperator.IN &&
                binExpr.getLeft() instanceof PrivateIdentifier privateIdentifier) {
            compilePrivateInExpression(privateIdentifier, binExpr.getRight());
            return;
        }

        // Short-circuit operators: must NOT evaluate right operand eagerly
        switch (binExpr.getOperator()) {
            case LOGICAL_AND -> {
                // left && right: if left is falsy, return left; otherwise evaluate and return right
                compilerContext.pushState();
                compilerContext.emitTailCalls = false;
                compilerContext.expressionCompiler.compileExpression(binExpr.getLeft());
                compilerContext.popState();
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.expressionCompiler.compileExpression(binExpr.getRight());
                compilerContext.emitter.patchJump(jumpEnd, compilerContext.emitter.currentOffset());
                return;
            }
            case LOGICAL_OR -> {
                // left || right: if left is truthy, return left; otherwise evaluate and return right
                compilerContext.pushState();
                compilerContext.emitTailCalls = false;
                compilerContext.expressionCompiler.compileExpression(binExpr.getLeft());
                compilerContext.popState();
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.expressionCompiler.compileExpression(binExpr.getRight());
                compilerContext.emitter.patchJump(jumpEnd, compilerContext.emitter.currentOffset());
                return;
            }
            case NULLISH_COALESCING -> {
                // left ?? right: if left is not null/undefined, return left; otherwise evaluate and return right
                compilerContext.pushState();
                compilerContext.emitTailCalls = false;
                compilerContext.expressionCompiler.compileExpression(binExpr.getLeft());
                compilerContext.popState();
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
                int jumpEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.expressionCompiler.compileExpression(binExpr.getRight());
                compilerContext.emitter.patchJump(jumpEnd, compilerContext.emitter.currentOffset());
                return;
            }
            default -> {
                // Fall through to compile operands for other operators
            }
        }

        // Compile operands
        compilerContext.expressionCompiler.compileExpression(binExpr.getLeft());
        compilerContext.expressionCompiler.compileExpression(binExpr.getRight());

        // Emit operation
        Opcode op = switch (binExpr.getOperator()) {
            case ADD -> Opcode.ADD;
            case BIT_AND -> Opcode.AND;
            case BIT_OR -> Opcode.OR;
            case BIT_XOR -> Opcode.XOR;
            case DIV -> Opcode.DIV;
            case EQ -> Opcode.EQ;
            case EXP -> Opcode.POW;
            case GE -> Opcode.GTE;
            case GT -> Opcode.GT;
            case IN -> Opcode.IN;
            case INSTANCEOF -> Opcode.INSTANCEOF;
            case LE -> Opcode.LTE;
            case LSHIFT -> Opcode.SHL;
            case LT -> Opcode.LT;
            case MOD -> Opcode.MOD;
            case MUL -> Opcode.MUL;
            case NE -> Opcode.NEQ;
            case RSHIFT -> Opcode.SAR;
            case STRICT_EQ -> Opcode.STRICT_EQ;
            case STRICT_NE -> Opcode.STRICT_NEQ;
            case SUB -> Opcode.SUB;
            case URSHIFT -> Opcode.SHR;
            // LOGICAL_AND, LOGICAL_OR, NULLISH_COALESCING handled above with short-circuit evaluation
            default -> throw new JSCompilerException("Unknown binary operator: " + binExpr.getOperator());
        };

        compilerContext.emitter.emitOpcode(op);
    }

    private void compilePrivateInExpression(PrivateIdentifier privateIdentifier, Expression right) {
        compilerContext.expressionCompiler.compileExpression(right);

        JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(privateIdentifier.getName()) : null;
        if (symbol == null) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'");
        }

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
        compilerContext.emitter.emitOpcode(Opcode.PRIVATE_IN);
    }
}
