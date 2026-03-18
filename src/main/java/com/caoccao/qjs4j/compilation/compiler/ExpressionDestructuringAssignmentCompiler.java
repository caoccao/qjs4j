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

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles assignment targets (destructuring assignment LHS) into bytecode.
 * Dispatches to dedicated compilers for Identifier, MemberExpression, ArrayExpression,
 * and ObjectExpression targets.
 */
final class ExpressionDestructuringAssignmentCompiler extends AstNodeCompiler<Expression> {

    ExpressionDestructuringAssignmentCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Expression target) {
        // Stack: [value]
        // Assign value to target and pop value from stack
        if (target instanceof Identifier id) {
            compilerContext.identifierDestructuringAssignmentCompiler.compile(id);
        } else if (target instanceof MemberExpression memberExpr) {
            compilerContext.memberExpressionDestructuringAssignmentCompiler.compile(memberExpr);
        } else if (target instanceof ArrayExpression nestedArray) {
            compilerContext.arrayExpressionDestructuringAssignmentCompiler.compile(nestedArray);
        } else if (target instanceof ObjectExpression nestedObj) {
            compilerContext.objectExpressionDestructuringAssignmentCompiler.compile(nestedObj);
        } else {
            throw new JSSyntaxErrorException("Invalid destructuring assignment target");
        }
    }

    void compileFromPreEvaluated(Expression element, int depth) {
        if (depth == 0) {
            compileWithDefaultValue(element);
            return;
        }
        // Handle default values first
        Expression target;
        if (element instanceof AssignmentExpression assignExpr
                && assignExpr.getOperator() == AssignmentOperator.ASSIGN) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
            int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.expressionCompiler.compile(assignExpr.getRight());
            if (assignExpr.getLeft() instanceof Identifier targetId
                    && assignExpr.getRight().isAnonymousFunction()) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, targetId.getName());
            }
            compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
            target = assignExpr.getLeft();
        } else {
            target = element;
        }
        // Now assign using the pre-evaluated references
        // Stack: [pre-eval-values...] value
        if (target instanceof MemberExpression memberExpr) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
                String fieldName = privateIdentifier.getName();
                JSSymbol privateSymbol = compilerContext.privateSymbols != null
                        ? compilerContext.privateSymbols.get(fieldName)
                        : null;
                if (privateSymbol == null) {
                    throw new JSCompilerException("undefined private field '#" + fieldName + "'");
                }
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, privateSymbol);
                compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }
    }

    private void compileWithDefaultValue(Expression element) {
        // Stack: [value]
        if (element instanceof AssignmentExpression assignExpr
                && assignExpr.getOperator() == AssignmentOperator.ASSIGN) {
            // Default value: check if value is undefined
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
            int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.expressionCompiler.compile(assignExpr.getRight());
            // Set function name for anonymous function definitions
            if (assignExpr.getLeft() instanceof Identifier targetId
                    && assignExpr.getRight().isAnonymousFunction()) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, targetId.getName());
            }
            compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
            compile(assignExpr.getLeft());
        } else {
            compile(element);
        }
    }
}
