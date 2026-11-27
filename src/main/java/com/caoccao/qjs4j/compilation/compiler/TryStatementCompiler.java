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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TryStatementCompiler extends AstNodeCompiler<TryStatement> {
    TryStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
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
            compilerContext.emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else if (!compilerContext.isLastInProgram) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Compile the body of a finally block as statements with net-zero stack effect. Used for the GOSUB/RET path where
     * the return address must remain on top of the stack.
     */
    private void compileFinallyBlockBody(BlockStatement block) {
        boolean savedIsLastInProgram = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        compilerContext.scopeManager.enterScope();
        compilerContext.finallySubroutineDepth++;
        int savedEvalReturnLocalIndex = -1;
        if (compilerContext.evalReturnLocalIndex >= 0) {
            savedEvalReturnLocalIndex = compilerContext.declareTemporary("#finally_eval_ret_");
            compilerContext.emitter.emitGetLocal(compilerContext.evalReturnLocalIndex);
            compilerContext.emitter.emitPutLocal(savedEvalReturnLocalIndex);
            compilerContext.emitter.emitUndefinedLocal(compilerContext.evalReturnLocalIndex);
        }
        try {
            for (Statement stmt : block.getBody()) {
                compilerContext.statementCompiler.compile(stmt);
            }
        } finally {
            compilerContext.finallySubroutineDepth--;
        }
        if (savedEvalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitGetLocal(savedEvalReturnLocalIndex);
            compilerContext.emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
        }
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
        compilerContext.isLastInProgram = savedIsLastInProgram;
    }

    /**
     * Compile a try block. A strict-mode {@code return f()} inside it used to be compiled as a tail call, which
     * discarded the frame and its catch handler: the exception escaped {@code try { return f() } catch (e) {}}.
     */
    private void compileTryBlock(BlockStatement block) {
        compilerContext.tryBlockDepth++;
        try {
            compileTryFinallyBlock(block);
        } finally {
            compilerContext.tryBlockDepth--;
        }
    }

    private void compileTryCatchWithoutFinally(TryStatement tryStmt) {
        int catchJump = -1;
        if (tryStmt.getHandler() != null) {
            catchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
        }

        compileTryBlock(tryStmt.getBlock());

        if (tryStmt.getHandler() != null) {
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
        }

        int jumpOverCatch = compilerContext.emitter.emitJump(Opcode.GOTO);

        if (tryStmt.getHandler() != null) {
            compilerContext.emitter.patchJumpToHere(catchJump);
            TryStatement.CatchClause handler = tryStmt.getHandler();

            if (handler.getParam() != null) {
                enterCatchScope(handler.getParam());
            }
            compileTryFinallyBlock(handler.getBody());
            if (handler.getParam() != null) {
                exitCatchScope();
            }
        }

        compilerContext.emitter.patchJumpToHere(jumpOverCatch);
    }

    private void compileTryFinallyBlock(BlockStatement block) {
        boolean savedIsLastInProgram = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        List<Statement> body = block.getBody();

        // Phase 1: pre-declare lexical bindings for TDZ.
        compilerContext.statementCompiler.predeclareLexicalBindings(body, false);

        // Phase 2: hoist function declarations.
        for (Statement statement : body) {
            if (statement instanceof FunctionDeclaration functionDeclaration) {
                FunctionDeclarationCompiler.compile(compilerContext, functionDeclaration);
            }
        }

        // Set up CATCH for exception-safe using disposal
        boolean blockHasUsing = EmitHelpers.hasUsingDeclarations(body);
        int usingCatchJump = compilerContext.emitHelpers.beginUsingScope(blockHasUsing);

        if (compilerContext.evalReturnLocalIndex >= 0) {
            // The shared completion local handles every statement kind with net-zero stack effect.
            // Leave exactly one result above the catch marker, including when the last statement
            // is another try. A missing result here would make NIP_CATCH corrupt the handler stack.
            compilerContext.emitter.emitUndefinedLocal(compilerContext.evalReturnLocalIndex);
            for (Statement statement : body) {
                if (!(statement instanceof FunctionDeclaration)) {
                    compilerContext.statementCompiler.compile(statement);
                }
            }
            compilerContext.emitter.emitGetLocal(compilerContext.evalReturnLocalIndex);
        } else {
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
                    compilerContext.emitter.emitSourcePosition(statement.getLocation());
                    compilerContext.expressionCompiler.compile(expressionStatement.getExpression());
                    if (compilerContext.evalReturnLocalIndex >= 0) {
                        compilerContext.emitter.emitOpcode(Opcode.DUP);
                        compilerContext.emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
                    }
                    if (!isLast) {
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                } else if (statement instanceof TryStatement tryStatement) {
                    boolean saved = compilerContext.isLastInProgram;
                    compilerContext.isLastInProgram = isLast;
                    compilerContext.emitter.emitSourcePosition(statement.getLocation());
                    compile(tryStatement);
                    compilerContext.isLastInProgram = saved;
                } else {
                    compilerContext.statementCompiler.compile(statement);
                    if (isLast) {
                        emitUndefinedCompletion();
                    }
                }
            }

            if (effectiveLastIndex < 0) {
                emitUndefinedCompletion();
            }
        }

        compilerContext.emitHelpers.finishUsingScope(usingCatchJump, true);
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
        compilerContext.isLastInProgram = savedIsLastInProgram;
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

        compileTryBlock(tryStmt.getBlock());
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
            compilerContext.emitter.patchJumpToHere(catchFromTryJump);

            TryStatement.CatchClause handler = tryStmt.getHandler();
            if (handler.getParam() != null) {
                enterCatchScope(handler.getParam());
            } else {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
            int catchBodyCatchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
            compileTryFinallyBlock(handler.getBody());
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            catchNormalGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
            jumpsToEnd.add(compilerContext.emitter.emitJump(Opcode.GOTO));
            compilerContext.emitter.patchJumpToHere(catchBodyCatchJump);
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            catchExceptionGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
            compilerContext.emitter.emitOpcode(Opcode.THROW);
            if (handler.getParam() != null) {
                exitCatchScope();
            }
            compilerContext.emitter.patchJumpToHere(finallyCatchJump);
            compilerContext.emitter.markCatchAsFinally(finallyCatchJump);
            finallyExceptionGosub = compilerContext.emitter.emitJump(Opcode.GOSUB);
            compilerContext.emitter.emitOpcode(Opcode.THROW);
        } else {
            compilerContext.emitter.patchJumpToHere(catchFromTryJump);
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
        compilerContext.emitter.patchJumps(gosubPatches, finallyOffset);

        int endOffset = compilerContext.emitter.currentOffset();
        for (int jumpToEnd : jumpsToEnd) {
            compilerContext.emitter.patchJump(jumpToEnd, endOffset);
        }
    }

    /**
     * Start fresh bindings for a destructuring catch parameter: new on every entry, and in their temporal dead zone
     * until the pattern initializes them.
     */
    private void emitFreshCatchBindings(Pattern catchParam) {
        Set<String> names = new LinkedHashSet<>();
        compilerContext.compilerAnalysis.collectPatternBindingNames(catchParam, names);
        // declarePatternVariables has just declared every one of these names in the catch scope.
        for (String name : names) {
            int localIndex = compilerContext.scopeManager.currentScope().getLocal(name);
            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
            compilerContext.tdzLocals.add(name);
        }
    }

    private void emitUndefinedCompletion() {
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        if (compilerContext.evalReturnLocalIndex >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
        }
    }

    private void enterCatchScope(Pattern catchParam) {
        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        if (catchParam instanceof Identifier id) {
            int localIndex = compilerContext.scopeManager.currentScope().declareLocal(id.getName());
            compilerContext.scopeManager.currentScope().markSimpleCatchParam(id.getName());
            // A new binding on every entry, so a closure from an earlier pass through this catch, in a loop,
            // keeps its own exception.
            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
            compilerContext.emitter.emitPutLocal(localIndex);
        } else {
            compilerContext.patternCompiler.declarePatternVariables(catchParam);
            emitFreshCatchBindings(catchParam);
            compilerContext.patternCompiler.compile(catchParam);
        }
    }

    private void exitCatchScope() {
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
    }
}
