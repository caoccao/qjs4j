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
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles expression AST nodes into bytecode.
 * Dispatches to specialized expression compilers.
 */
final class ExpressionCompiler extends AstNodeCompiler<Expression> {
    ExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Expression expr) {
        if (expr instanceof Literal literal) {
            compilerContext.literalCompiler.compile(literal);
        } else if (expr instanceof Identifier identifier) {
            compilerContext.identifierCompiler.compile(identifier);
        } else if (expr instanceof PrivateIdentifier privateIdentifier) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'");
        } else if (expr instanceof BinaryExpression binExpr) {
            compilerContext.binaryExpressionCompiler.compile(binExpr);
        } else if (expr instanceof UnaryExpression unaryExpr) {
            compilerContext.unaryExpressionCompiler.compile(unaryExpr);
        } else if (expr instanceof AssignmentExpression assignExpr) {
            compilerContext.assignmentExpressionCompiler.compile(assignExpr);
        } else if (expr instanceof ConditionalExpression condExpr) {
            compilerContext.conditionalExpressionCompiler.compile(condExpr);
        } else if (expr instanceof CallExpression callExpr) {
            compilerContext.callExpressionCompiler.compile(callExpr);
        } else if (expr instanceof MemberExpression memberExpr) {
            compilerContext.memberExpressionCompiler.compile(memberExpr);
        } else if (expr instanceof NewExpression newExpr) {
            compilerContext.newExpressionCompiler.compile(newExpr);
        } else if (expr instanceof FunctionExpression functionExpression) {
            compilerContext.functionExpressionCompiler.compile(functionExpression);
        } else if (expr instanceof ArrowFunctionExpression arrowExpr) {
            compilerContext.arrowFunctionExpressionCompiler.compile(arrowExpr);
        } else if (expr instanceof AwaitExpression awaitExpr) {
            compilerContext.awaitExpressionCompiler.compile(awaitExpr);
        } else if (expr instanceof YieldExpression yieldExpr) {
            compilerContext.yieldExpressionCompiler.compile(yieldExpr);
        } else if (expr instanceof ArrayExpression arrayExpr) {
            compilerContext.arrayExpressionCompiler.compile(arrayExpr);
        } else if (expr instanceof ObjectExpression objExpr) {
            compilerContext.objectExpressionCompiler.compile(objExpr);
        } else if (expr instanceof TemplateLiteral templateLiteral) {
            compilerContext.templateLiteralCompiler.compile(templateLiteral);
        } else if (expr instanceof TaggedTemplateExpression taggedTemplate) {
            compilerContext.taggedTemplateExpressionCompiler.compile(taggedTemplate);
        } else if (expr instanceof ClassExpression classExpr) {
            compilerContext.classExpressionCompiler.compile(classExpr);
        } else if (expr instanceof SequenceExpression seqExpr) {
            compilerContext.sequenceExpressionCompiler.compile(seqExpr);
        } else if (expr instanceof ImportExpression importExpr) {
            compilerContext.importExpressionCompiler.compile(importExpr);
        }
    }

    /**
     * Extract the actual assignment target from an element, unwrapping default values.
     */
    private Expression getAssignmentTarget(Expression element) {
        if (element == null) {
            return null;
        }
        if (element instanceof AssignmentExpression assignExpr
                && assignExpr.getOperator() == AssignmentOperator.ASSIGN) {
            return assignExpr.getLeft();
        }
        return element;
    }

    /**
     * Pre-evaluate the LHS of a destructuring assignment element before calling FOR_OF_NEXT.
     * Per spec (IteratorDestructuringAssignmentEvaluation step 1a): if the target is not
     * a pattern, evaluate it first to get the reference.
     * Returns the number of values pushed on the stack (the depth for FOR_OF_NEXT).
     */
    int preEvaluateAssignmentTarget(Expression element) {
        Expression target = getAssignmentTarget(element);
        if (target == null) {
            return 0; // hole
        }
        if (target instanceof MemberExpression memberExpr && !memberExpr.isOptional()) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                // Pre-evaluate super reference: this, superObj, key
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                if (memberExpr.isComputed()) {
                    // Per ES spec: computed key evaluated before GetSuperBase()
                    compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                }
                return 3; // this, superObj, key on stack
            }
            // Pre-evaluate the object
            compile(memberExpr.getObject());
            if (memberExpr.isComputed()) {
                // Pre-evaluate the computed key
                compile(memberExpr.getProperty());
                return 2; // obj + key on stack
            }
            return 1; // obj on stack
        }
        // Identifiers and nested patterns: no pre-evaluation needed
        return 0;
    }
}
