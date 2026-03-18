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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TryStatementCompiler {
    private final CompilerContext compilerContext;

    TryStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(TryStatement tryStmt) {
        if (tryStmt.getFinalizer() == null) {
            compileTryCatchWithoutFinally(tryStmt);
        } else {
            compileTryStatementWithFinally(tryStmt);
        }
        compileFinalizeTryStatementCompletion();
    }

    private void compileFinalizeTryStatementCompletion() {
        // Try statements produce a value on the stack (the try/catch result).
        // When eval_ret_idx is active, store the completion value first.
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else if (!compilerContext.isLastInProgram) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Compile the body of a finally block as statements with net-zero stack effect.
     * Used for the GOSUB/RET path where the return address must remain on top of the stack.
     */
    private void compileFinallyBlockBody(BlockStatement block) {
        compilerContext.scopeManager.enterScope();
        compilerContext.finallySubroutineDepth++;
        int savedEvalReturnLocalIndex = -1;
        if (compilerContext.evalReturnLocalIndex >= 0) {
            savedEvalReturnLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                    "$finally_eval_ret_" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, compilerContext.evalReturnLocalIndex);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, savedEvalReturnLocalIndex);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }
        try {
            for (Statement stmt : block.getBody()) {
                compilerContext.statementCompiler.compile(stmt);
            }
        } finally {
            compilerContext.finallySubroutineDepth--;
        }
        if (savedEvalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, savedEvalReturnLocalIndex);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
        }
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
    }

    private void compileTryCatchWithoutFinally(TryStatement tryStmt) {
        int catchJump = -1;
        if (tryStmt.getHandler() != null) {
            catchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
        }

        compileTryFinallyBlock(tryStmt.getBlock());

        if (tryStmt.getHandler() != null) {
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
        }

        int jumpOverCatch = compilerContext.emitter.emitJump(Opcode.GOTO);

        if (tryStmt.getHandler() != null) {
            compilerContext.emitter.patchJump(catchJump, compilerContext.emitter.currentOffset());
            TryStatement.CatchClause handler = tryStmt.getHandler();

            if (handler.getParam() != null) {
                compilerContext.pushState();
                compilerContext.scopeManager.enterScope();
                compilerContext.inGlobalScope = false;

                Pattern catchParam = handler.getParam();
                if (catchParam instanceof Identifier id) {
                    int localIndex = compilerContext.scopeManager.currentScope().declareLocal(id.getName());
                    compilerContext.scopeManager.currentScope().markSimpleCatchParam(id.getName());
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                } else {
                    compilerContext.patternCompiler.declarePatternVariables(catchParam);
                    compilerContext.patternCompiler.compilePatternAssignment(catchParam);
                }

                compileTryFinallyBlock(handler.getBody());

                compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
                compilerContext.scopeManager.exitScope();
                compilerContext.popState();
            } else {
                compileTryFinallyBlock(handler.getBody());
            }
        }

        compilerContext.emitter.patchJump(jumpOverCatch, compilerContext.emitter.currentOffset());
    }

    private void compileTryFinallyBlock(BlockStatement block) {
        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        List<Statement> body = block.getBody();

        // Phase 1: pre-declare lexical bindings for TDZ.
        for (Statement statement : body) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    Set<String> declarationNames = new HashSet<>();
                    compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), declarationNames);
                    for (String declarationName : declarationNames) {
                        Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(declarationName);
                        if (localIndex == null) {
                            localIndex = compilerContext.scopeManager.currentScope().declareLocal(declarationName);
                        }
                        if (variableDeclaration.getKind() == VariableKind.CONST
                                || variableDeclaration.getKind() == VariableKind.USING
                                || variableDeclaration.getKind() == VariableKind.AWAIT_USING) {
                            compilerContext.scopeManager.currentScope().markConstLocal(declarationName);
                        }
                        compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                        compilerContext.tdzLocals.add(declarationName);
                    }
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String className = classDeclaration.getId().getName();
                Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(className);
                if (localIndex == null) {
                    localIndex = compilerContext.scopeManager.currentScope().declareLocal(className);
                }
                compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                compilerContext.tdzLocals.add(className);
            }
        }

        // Phase 2: hoist function declarations.
        for (Statement statement : body) {
            if (statement instanceof FunctionDeclaration functionDeclaration) {
                compilerContext.functionDeclarationCompiler.compile(functionDeclaration);
            }
        }

        // Phase 3: compile non-function statements and preserve block completion value.
        int effectiveLastIndex = -1;
        for (int index = body.size() - 1; index >= 0; index--) {
            if (!(body.get(index) instanceof FunctionDeclaration)) {
                effectiveLastIndex = index;
                break;
            }
        }

        for (int index = 0; index < body.size(); index++) {
            Statement statement = body.get(index);
            if (statement instanceof FunctionDeclaration) {
                continue;
            }
            boolean isLast = index == effectiveLastIndex;

            if (statement instanceof ExpressionStatement expressionStatement) {
                compilerContext.expressionCompiler.compile(expressionStatement.getExpression());
                if (compilerContext.evalReturnLocalIndex >= 0) {
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
                }
                if (!isLast) {
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                }
            } else if (statement instanceof TryStatement tryStatement) {
                boolean saved = compilerContext.isLastInProgram;
                compilerContext.isLastInProgram = isLast;
                compile(tryStatement);
                compilerContext.isLastInProgram = saved;
            } else {
                compilerContext.statementCompiler.compile(statement);
                if (isLast) {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                    if (compilerContext.evalReturnLocalIndex >= 0) {
                        compilerContext.emitter.emitOpcode(Opcode.DUP);
                        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
                    }
                }
            }
        }

        if (effectiveLastIndex < 0) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            if (compilerContext.evalReturnLocalIndex >= 0) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, compilerContext.evalReturnLocalIndex);
            }
        }

        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
    }

    private void compileTryStatementWithFinally(TryStatement tryStmt) {
        List<Integer> gosubPatches = new ArrayList<>();
        compilerContext.activeFinallyGosubPatches.push(gosubPatches);
        int finallyNipCatchCount = tryStmt.getHandler() != null ? 2 : 1;
        compilerContext.activeFinallyNipCatchCounts.push(finallyNipCatchCount);

        int finallyCatchJump = -1;
        int catchFromTryJump;
        if (tryStmt.getHandler() != null) {
            finallyCatchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
            catchFromTryJump = compilerContext.emitter.emitJump(Opcode.CATCH);
        } else {
            catchFromTryJump = compilerContext.emitter.emitJump(Opcode.CATCH);
        }

        compileTryFinallyBlock(tryStmt.getBlock());
        compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
        if (tryStmt.getHandler() != null) {
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
        }
        int tryNormalGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
        List<Integer> jumpsToEnd = new ArrayList<>();
        jumpsToEnd.add(compilerContext.emitter.emitJump(Opcode.GOTO));

        int catchNormalGosub = -1;
        int catchExceptionGosub = -1;
        int finallyExceptionGosub = -1;

        if (tryStmt.getHandler() != null) {
            compilerContext.emitter.patchJump(catchFromTryJump, compilerContext.emitter.currentOffset());

            TryStatement.CatchClause handler = tryStmt.getHandler();
            if (handler.getParam() != null) {
                compilerContext.pushState();
                compilerContext.scopeManager.enterScope();
                compilerContext.inGlobalScope = false;
                Pattern catchParam = handler.getParam();
                if (catchParam instanceof Identifier id) {
                    int localIndex = compilerContext.scopeManager.currentScope().declareLocal(id.getName());
                    compilerContext.scopeManager.currentScope().markSimpleCatchParam(id.getName());
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                } else {
                    compilerContext.patternCompiler.declarePatternVariables(catchParam);
                    compilerContext.patternCompiler.compilePatternAssignment(catchParam);
                }

                int catchBodyCatchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
                compileTryFinallyBlock(handler.getBody());
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
                catchNormalGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
                jumpsToEnd.add(compilerContext.emitter.emitJump(Opcode.GOTO));
                compilerContext.emitter.patchJump(catchBodyCatchJump, compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
                catchExceptionGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
                compilerContext.emitter.emitOpcode(Opcode.THROW);

                compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
                compilerContext.scopeManager.exitScope();
                compilerContext.popState();
            } else {
                compilerContext.emitter.emitOpcode(Opcode.DROP);

                int catchBodyCatchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
                compileTryFinallyBlock(handler.getBody());
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
                catchNormalGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
                jumpsToEnd.add(compilerContext.emitter.emitJump(Opcode.GOTO));
                compilerContext.emitter.patchJump(catchBodyCatchJump, compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
                catchExceptionGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
                compilerContext.emitter.emitOpcode(Opcode.THROW);
            }
            compilerContext.emitter.patchJump(finallyCatchJump, compilerContext.emitter.currentOffset());
            compilerContext.emitter.markCatchAsFinally(finallyCatchJump);
            finallyExceptionGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
            compilerContext.emitter.emitOpcode(Opcode.THROW);
        } else {
            compilerContext.emitter.patchJump(catchFromTryJump, compilerContext.emitter.currentOffset());
            compilerContext.emitter.markCatchAsFinally(catchFromTryJump);
            finallyExceptionGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
            compilerContext.emitter.emitOpcode(Opcode.THROW);
        }

        compilerContext.activeFinallyGosubPatches.pop();
        compilerContext.activeFinallyNipCatchCounts.pop();

        int finallyOffset = compilerContext.emitter.currentOffset();
        compileFinallyBlockBody(tryStmt.getFinalizer());
        compilerContext.emitter.emitOpcode(Opcode.RET);

        compilerContext.emitter.patchJump(tryNormalGosub, finallyOffset);
        if (catchNormalGosub >= 0) {
            compilerContext.emitter.patchJump(catchNormalGosub, finallyOffset);
        }
        if (catchExceptionGosub >= 0) {
            compilerContext.emitter.patchJump(catchExceptionGosub, finallyOffset);
        }
        if (finallyExceptionGosub >= 0) {
            compilerContext.emitter.patchJump(finallyExceptionGosub, finallyOffset);
        }
        for (int gosubPatch : gosubPatches) {
            compilerContext.emitter.patchJump(gosubPatch, finallyOffset);
        }

        int endOffset = compilerContext.emitter.currentOffset();
        for (int jumpToEnd : jumpsToEnd) {
            compilerContext.emitter.patchJump(jumpToEnd, endOffset);
        }
    }
}
