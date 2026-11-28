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

import com.caoccao.qjs4j.vm.Opcode;
import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.exceptions.JSCompilerException;

/**
 * Compiles expression AST nodes into bytecode. Dispatches to specialized expression compilers.
 */
final class ExpressionCompiler {
    private ExpressionCompiler() {
    }

    static void compile(CompilerContext compilerContext, Expression expr) {
        if (expr instanceof Literal literal) {
            LiteralCompiler.compile(compilerContext, literal);
        } else if (expr instanceof Identifier identifier) {
            IdentifierCompiler.compile(compilerContext, identifier);
        } else if (expr instanceof PrivateIdentifier privateIdentifier) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'",
                    privateIdentifier);
        } else if (expr instanceof BinaryExpression binExpr) {
            BinaryExpressionCompiler.compile(compilerContext, binExpr);
        } else if (expr instanceof UnaryExpression unaryExpr) {
            UnaryExpressionCompiler.compile(compilerContext, unaryExpr);
        } else if (expr instanceof AssignmentExpression assignExpr) {
            AssignmentExpressionCompiler.compile(compilerContext, assignExpr);
        } else if (expr instanceof ConditionalExpression condExpr) {
            ConditionalExpressionCompiler.compile(compilerContext, condExpr);
        } else if (expr instanceof CallExpression callExpr) {
            CallExpressionCompiler.compile(compilerContext, callExpr);
        } else if (expr instanceof MemberExpression memberExpr) {
            MemberExpressionCompiler.compile(compilerContext, memberExpr);
        } else if (expr instanceof NewExpression newExpr) {
            NewExpressionCompiler.compile(compilerContext, newExpr);
        } else if (expr instanceof FunctionExpression functionExpression) {
            FunctionExpressionCompiler.compile(compilerContext, functionExpression);
        } else if (expr instanceof ArrowFunctionExpression arrowExpr) {
            ArrowFunctionExpressionCompiler.compile(compilerContext, arrowExpr);
        } else if (expr instanceof AwaitExpression awaitExpr) {
            AwaitExpressionCompiler.compile(compilerContext, awaitExpr);
        } else if (expr instanceof YieldExpression yieldExpr) {
            YieldExpressionCompiler.compile(compilerContext, yieldExpr);
        } else if (expr instanceof ArrayExpression arrayExpr) {
            ArrayExpressionCompiler.compile(compilerContext, arrayExpr);
        } else if (expr instanceof ObjectExpression objExpr) {
            ObjectExpressionCompiler.compile(compilerContext, objExpr);
        } else if (expr instanceof TemplateLiteral templateLiteral) {
            TemplateLiteralCompiler.compile(compilerContext, templateLiteral);
        } else if (expr instanceof TaggedTemplateExpression taggedTemplate) {
            TaggedTemplateExpressionCompiler.compile(compilerContext, taggedTemplate);
        } else if (expr instanceof ClassExpression classExpr) {
            ClassExpressionCompiler.compile(compilerContext, classExpr);
        } else if (expr instanceof SequenceExpression seqExpr) {
            SequenceExpressionCompiler.compile(compilerContext, seqExpr);
        } else if (expr instanceof ImportExpression importExpr) {
            ImportExpressionCompiler.compile(compilerContext, importExpr);
        }
    }

    static void compileNamed(CompilerContext compilerContext, Expression value, String name) {
        compile(compilerContext, value);
        if (value.isAnonymousFunction()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, name);
        }
    }

    /** Evaluate a computed property name and perform ToPropertyKey before evaluating its value. */
    static void compilePropertyKey(CompilerContext compilerContext, Expression key) {
        compile(compilerContext, key);
        compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
    }

    /**
     * Extract the actual assignment target from an element, unwrapping default values.
     */
    private static Expression getAssignmentTarget(Expression element) {
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
     * Pre-evaluate the LHS of a destructuring assignment element before calling FOR_OF_NEXT. Per spec
     * (IteratorDestructuringAssignmentEvaluation step 1a): if the target is not a pattern, evaluate it first to get the
     * reference. Returns the number of values pushed on the stack (the depth for FOR_OF_NEXT).
     */
    static int preEvaluateAssignmentTarget(CompilerContext compilerContext, Expression element) {
        Expression target = getAssignmentTarget(element);
        if (target == null) {
            return 0; // hole
        }
        if (target instanceof MemberExpression memberExpr && !memberExpr.isOptional()) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                // Pre-evaluate super reference: this, superObj, key
                MemberExpressionCompiler.emitSuperPropertyReference(compilerContext, memberExpr);
                return 3; // this, superObj, key on stack
            }
            // Pre-evaluate the object
            compile(compilerContext, memberExpr.getObject());
            if (memberExpr.isComputed()) {
                // Pre-evaluate the computed key
                compile(compilerContext, memberExpr.getProperty());
                return 2; // obj + key on stack
            }
            return 1; // obj on stack
        }
        // Identifiers and nested patterns: no pre-evaluation needed
        return 0;
    }

}
