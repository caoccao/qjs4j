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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bytecode for block-bodied functions, including hoisting and completion. */
enum FunctionBodyCompiler {
    ARROW("#arrow_return_"),
    METHOD("#method_return_"),
    ORDINARY("#function_return_");

    private final String returnLocalPrefix;

    FunctionBodyCompiler(String returnLocalPrefix) {
        this.returnLocalPrefix = returnLocalPrefix;
    }

    void compile(CompilerContext context, FunctionNode function, BlockStatement block, boolean async) {
        List<Statement> statements = block.getBody();
        // Declare all var slots before compiling the functions that may capture them.
        CompilerAnalysis.hoistAllDeclarationsAsLocals(context, statements);
        for (Statement statement : statements) {
            if (statement instanceof FunctionDeclaration declaration) {
                FunctionDeclarationCompiler.compile(context, declaration);
            }
        }
        if (this != METHOD) {
            CompilerAnalysis.hoistFunctionBodyAnnexBDeclarations(context, statements, function.getParameterNames());
        }

        int usingCatch = -1;
        if (this == ORDINARY && UsingScopeCompiler.hasUsingDeclarations(statements)) {
            usingCatch = context.emitter.emitJump(Opcode.CATCH);
            context.scopeManager.currentScope().setUsingCatchJumpPosition(usingCatch);
        }
        for (Statement statement : statements) {
            if (!(statement instanceof FunctionDeclaration)) {
                context.compileStatement(statement);
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
            context.emitter.patchJumpToHere(usingCatch);
            UsingScopeCompiler.emitScopeUsingDisposalWithException(context, context.scopeManager.currentScope());
            context.emitter.patchJumpToHere(end);
        }
    }

    void emitReturn(CompilerContext context, boolean async) {
        int result = context.scopeManager.currentScope()
                .declareLocal(returnLocalPrefix + context.emitter.currentOffset());
        context.emitter.emitPutLocal(result);
        UsingScopeCompiler.emitCurrentScopeUsingDisposal(context);
        context.emitter.emitGetLocal(result);
        context.emitter.emitOpcode(async ? Opcode.RETURN_ASYNC : Opcode.RETURN);
    }

    static void emitDefaultParameterInit(BytecodeCompiler functionCompiler, FunctionParams functionParams,
            List<Integer> parameterSlotIndexes, ASTNode ast) {
        List<Pattern> params = functionParams.params();
        List<Expression> defaults = functionParams.defaults();
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        if (parameterSlotIndexes == null || parameterSlotIndexes.size() < defaults.size()) {
            throw new JSCompilerException("Parameter slot indexes are not aligned with default parameters", ast);
        }

        if (!functionParams.hasNonSimpleParameters()) {
            return;
        }
        CompilerContext functionContext = functionCompiler.context();
        Set<String> originalTdzLocals = new HashSet<>(functionContext.tdzLocals);
        List<Set<String>> parameterBoundNames = new ArrayList<>(params.size());
        Set<String> pendingParameterTdzNames = new HashSet<>();
        for (Pattern pattern : params) {
            Set<String> parameterNames = new HashSet<>(pattern.getBoundNames());
            parameterBoundNames.add(parameterNames);
            pendingParameterTdzNames.addAll(parameterNames);
        }

        for (int i = 0; i < defaults.size(); i++) {
            int parameterSlotIndex = parameterSlotIndexes.get(i);
            functionContext.emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, parameterSlotIndex);
        }

        for (int i = 0; i < defaults.size(); i++) {
            Expression defaultExpr = defaults.get(i);
            functionContext.tdzLocals.clear();
            functionContext.tdzLocals.addAll(originalTdzLocals);
            functionContext.tdzLocals.addAll(pendingParameterTdzNames);
            // GET_ARG idx - push the argument value onto the stack
            functionContext.emitter.emitIntOperand(Opcode.GET_ARG, i);
            if (defaultExpr != null) {
                // DUP - duplicate for the comparison
                functionContext.emitter.emitOpcode(Opcode.DUP);
                // UNDEFINED - push undefined for comparison
                functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
                // STRICT_EQ - check if arg === undefined
                functionContext.emitter.emitOpcode(Opcode.STRICT_EQ);
                // IF_FALSE label - if arg !== undefined, skip default
                int skipLabel = functionContext.emitter.emitJump(Opcode.IF_FALSE);
                // DROP - drop the duplicated arg value (it was undefined)
                functionContext.emitter.emitOpcode(Opcode.DROP);
                // Compile the default expression
                functionContext.compileExpression(defaultExpr);
                if (params.get(i) instanceof Identifier parameterIdentifier && defaultExpr.isAnonymousFunction()) {
                    functionContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, parameterIdentifier.getName());
                }
                // DUP - duplicate for PUT_ARG
                functionContext.emitter.emitOpcode(Opcode.DUP);
                // PUT_ARG idx - store back into the argument slot
                functionContext.emitter.emitIntOperand(Opcode.PUT_ARG, i);
                // label: - skip target (value is on stack, either original arg or default)
                functionContext.emitter.patchJumpToHere(skipLabel);
            }
            // PUT_LOCAL idx - store into the local variable slot
            int parameterSlotIndex = parameterSlotIndexes.get(i);
            functionContext.emitter.emitPutLocal(parameterSlotIndex);
            pendingParameterTdzNames.removeAll(parameterBoundNames.get(i));
        }
        functionContext.tdzLocals.clear();
        functionContext.tdzLocals.addAll(originalTdzLocals);
    }
}
