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
import com.caoccao.qjs4j.vm.Opcode;
import java.util.List;

/** Bytecode for block-bodied functions, including hoisting and completion. */
enum FunctionBodyCompiler {
    ARROW("#arrow_return_"), METHOD("#method_return_"), ORDINARY("#function_return_");

    private final String returnLocalPrefix;

    FunctionBodyCompiler(String returnLocalPrefix) {
        this.returnLocalPrefix = returnLocalPrefix;
    }

    void compile(CompilerContext context, FunctionNode function, BlockStatement block, boolean async) {
        List<Statement> statements = block.getBody();
        // Declare all var slots before compiling the functions that may capture them.
        context.compilerAnalysis.hoistAllDeclarationsAsLocals(statements);
        for (Statement statement : statements) {
            if (statement instanceof FunctionDeclaration declaration) {
                FunctionDeclarationCompiler.compile(context, declaration);
            }
        }
        if (this != METHOD) {
            context.compilerAnalysis.hoistFunctionBodyAnnexBDeclarations(statements, function.getParameterNames());
        }

        int usingCatch = -1;
        if (this == ORDINARY && EmitHelpers.hasUsingDeclarations(statements)) {
            usingCatch = context.emitter.emitJump(Opcode.CATCH);
            context.scopeManager.currentScope().setUsingCatchJumpPosition(usingCatch);
        }
        for (Statement statement : statements) {
            if (!(statement instanceof FunctionDeclaration)) {
                context.statementCompiler.compile(statement);
            }
        }
        if (statements.isEmpty() || !(statements.get(statements.size() - 1) instanceof ReturnStatement)) {
            if (usingCatch >= 0) {
                context.emitter.emitOpcode(Opcode.UNDEFINED);
                context.emitter.emitOpcode(Opcode.NIP_CATCH);
                context.emitter.emitOpcode(Opcode.DROP);
            }
            context.emitter.emitOpcode(Opcode.UNDEFINED);
            emitReturn(context, async);
        }
        if (usingCatch >= 0) {
            int end = context.emitter.emitJump(Opcode.GOTO);
            context.emitter.patchJump(usingCatch, context.emitter.currentOffset());
            context.emitHelpers.emitScopeUsingDisposalWithException(context.scopeManager.currentScope());
            context.emitter.patchJump(end, context.emitter.currentOffset());
        }
    }

    void emitReturn(CompilerContext context, boolean async) {
        int result = context.scopeManager.currentScope()
                .declareLocal(returnLocalPrefix + context.emitter.currentOffset());
        context.emitter.emitOpcodeU16(Opcode.PUT_LOC, result);
        context.emitHelpers.emitCurrentScopeUsingDisposal();
        context.emitter.emitOpcodeU16(Opcode.GET_LOC, result);
        context.emitter.emitOpcode(async ? Opcode.RETURN_ASYNC : Opcode.RETURN);
    }
}
