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

import com.caoccao.qjs4j.compilation.ast.FunctionDeclaration;
import com.caoccao.qjs4j.compilation.ast.IfStatement;
import com.caoccao.qjs4j.compilation.ast.Statement;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles if-statement AST nodes into bytecode.
 */
final class IfStatementCompiler {
    private final CompilerContext compilerContext;

    IfStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileIfStatement(IfStatement ifStmt) {
        // In strict mode, function declarations are not allowed as direct body of if/else
        // (they must be at top level or inside a block). Per ES2024 B.3.3 Note.
        if (compilerContext.strictMode) {
            if (ifStmt.getConsequent() instanceof FunctionDeclaration
                    || ifStmt.getAlternate() instanceof FunctionDeclaration) {
                throw new JSSyntaxErrorException(
                        "In strict mode code, functions can only be declared at top level or inside a block.");
            }
        }

        if (compilerContext.evalReturnLocalIndex >= 0) {
            // IfStatement completion defaults to undefined before branch evaluation.
            // Branch statements that produce a completion value overwrite this slot.
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        // Compile condition
        compilerContext.expressionCompiler.compileExpression(ifStmt.getTest());

        // Jump to else/end if condition is false
        int jumpToElse = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent — wrap bare function declarations in implicit block scope
        compileImplicitBlockStatement(ifStmt.getConsequent());

        if (ifStmt.getAlternate() != null) {
            // Jump over else block after consequent
            int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

            // Patch jump to else
            compilerContext.emitter.patchJump(jumpToElse, compilerContext.emitter.currentOffset());

            // Compile alternate — wrap bare function declarations in implicit block scope
            compileImplicitBlockStatement(ifStmt.getAlternate());

            // Patch jump to end
            compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        } else {
            // Patch jump to end
            compilerContext.emitter.patchJump(jumpToElse, compilerContext.emitter.currentOffset());
        }
    }

    /**
     * Compile a statement that may be a bare function declaration in sloppy mode.
     * Per ES2024 B.3.3, function declarations in if-statement positions are treated
     * as if wrapped in a block. This ensures the function binding is block-scoped
     * and does not overwrite outer let/const bindings when Annex B is skipped.
     */
    private void compileImplicitBlockStatement(Statement stmt) {
        if (stmt instanceof FunctionDeclaration funcDecl && funcDecl.getId() != null) {
            // Per ES2024 B.3.3, function declarations in if-statement positions
            // are treated as if wrapped in a block scope.
            compilerContext.scopeManager.enterScope();
            compilerContext.scopeManager.currentScope().declareLocal(funcDecl.getId().getName());
            compilerContext.functionDeclarationCompiler.compileFunctionDeclaration(funcDecl);
            compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
            compilerContext.scopeManager.exitScope();
        } else {
            compilerContext.statementCompiler.compileStatement(stmt);
        }
    }
}
