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

import com.caoccao.qjs4j.compilation.ast.WithStatement;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles with-statement AST nodes into bytecode.
 */
final class WithStatementCompiler {
    private final CompilerContext compilerContext;

    WithStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileWithStatement(WithStatement withStmt) {
        if (compilerContext.strictMode) {
            throw new JSSyntaxErrorException("Strict mode code may not include a with statement");
        }

        if (compilerContext.evalReturnLocalIndex >= 0) {
            // Default with completion value is undefined.
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }

        compilerContext.scopeManager.enterScope();
        int withObjectLocalIndex = compilerContext.scopeManager.currentScope().declareLocal("$withObject" + compilerContext.scopeManager.getScopeDepth());
        compilerContext.expressionCompiler.compileExpression(withStmt.getObject());
        compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, withObjectLocalIndex);

        compilerContext.withObjectManager.pushLocal(withObjectLocalIndex);
        try {
            if (withStmt.getBody() != null) {
                compilerContext.statementCompiler.compileStatement(withStmt.getBody());
            }
        } finally {
            // Do NOT clear the with-object local to undefined here.
            // Closures defined inside the with block capture a VarRef to this local
            // and need the with-object to remain accessible after the block exits.
            compilerContext.withObjectManager.popLocal();
        }

        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }
}
