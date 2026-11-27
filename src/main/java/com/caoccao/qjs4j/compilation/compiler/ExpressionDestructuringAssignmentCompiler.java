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
 * Compiles assignment targets (destructuring assignment LHS) into bytecode. Dispatches to dedicated compilers for
 * Identifier, MemberExpression, ArrayExpression, and ObjectExpression targets.
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
            MemberExpressionDestructuringAssignmentCompiler.compile(compilerContext, memberExpr);
        } else if (target instanceof ArrayExpression nestedArray) {
            compilerContext.arrayExpressionDestructuringAssignmentCompiler.compile(nestedArray);
        } else if (target instanceof ObjectExpression nestedObj) {
            ObjectExpressionDestructuringAssignmentCompiler.compile(compilerContext, nestedObj);
        } else {
            throw new JSSyntaxErrorException("Invalid destructuring assignment target", target.getLocation());
        }
    }

    void compileFromPreEvaluated(Expression element, int depth) {
        Expression target = element;
        if (element instanceof AssignmentExpression assignment
                && assignment.getOperator() == AssignmentOperator.ASSIGN) {
            target = assignment.getLeft();
            compilerContext.emitHelpers.emitDefaultValue(target instanceof Identifier id ? id.getName() : null,
                    assignment.getRight());
        }
        if (depth == 0) {
            compile(target);
        } else if (target instanceof MemberExpression member) {
            MemberExpressionDestructuringAssignmentCompiler.store(compilerContext, member, true);
        }
    }

    void compileIterationTarget(Expression leftExpression) {
        if (leftExpression instanceof CallExpression) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.expressionCompiler.compile(leftExpression);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }
        compile(leftExpression);
    }

}
