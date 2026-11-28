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

final class TryStatementCompiler {
    private TryStatementCompiler() {
    }

    static void compile(CompilerContext compilerContext, TryStatement tryStmt) {
        if (tryStmt.getFinalizer() == null) {
            compileTryCatchWithoutFinally(compilerContext, tryStmt);
        } else {
            compileTryStatementWithFinally(compilerContext, tryStmt);
        }
        compileFinalizeTryStatementCompletion(compilerContext);
    }

    private static void compileFinalizeTryStatementCompletion(CompilerContext compilerContext) {
        BytecodeEmitter emitter = compilerContext.emitter;
        // Try statements produce a value on the stack (the try/catch result).
        // When eval_ret_idx is active, store the completion value first.
        if (compilerContext.evalReturnLocalIndex >= 0) {
            emitter.emitOpcode(Opcode.DUP);
            emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
            emitter.emitOpcode(Opcode.DROP);
        } else if (!compilerContext.isLastInProgram) {
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Compile the body of a finally block as statements with net-zero stack effect. Used for the GOSUB/RET path where
     * the return address must remain on top of the stack.
     */
    private static void compileFinallyBlockBody(CompilerContext compilerContext, BlockStatement block) {
        BytecodeEmitter emitter = compilerContext.emitter;
        boolean savedIsLastInProgram = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        compilerContext.scopeManager.enterScope();
        compilerContext.finallySubroutineDepth++;
        int savedEvalReturnLocalIndex = -1;
        if (compilerContext.evalReturnLocalIndex >= 0) {
            savedEvalReturnLocalIndex = compilerContext.declareTemporary("#finally_eval_ret_");
            emitter.emitGetLocal(compilerContext.evalReturnLocalIndex);
            emitter.emitPutLocal(savedEvalReturnLocalIndex);
            emitter.emitUndefinedLocal(compilerContext.evalReturnLocalIndex);
        }
        try {
            for (Statement stmt : block.getBody()) {
                compilerContext.compileStatement(stmt);
            }
        } finally {
            compilerContext.finallySubroutineDepth--;
        }
        if (savedEvalReturnLocalIndex >= 0) {
            emitter.emitGetLocal(savedEvalReturnLocalIndex);
            emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
        }
        EmitHelpers.emitCurrentScopeUsingDisposal(compilerContext);
        compilerContext.scopeManager.exitScope();
        compilerContext.isLastInProgram = savedIsLastInProgram;
    }

    /**
     * Compile a try block. A strict-mode {@code return f()} inside it used to be compiled as a tail call, which
     * discarded the frame and its catch handler: the exception escaped {@code try { return f() } catch (e) {}}.
     */
    private static void compileTryBlock(CompilerContext compilerContext, BlockStatement block) {
        compilerContext.tryBlockDepth++;
        try {
            compileTryFinallyBlock(compilerContext, block);
        } finally {
            compilerContext.tryBlockDepth--;
        }
    }

    private static void compileTryCatchWithoutFinally(CompilerContext compilerContext, TryStatement tryStmt) {
        BytecodeEmitter emitter = compilerContext.emitter;
        int catchJump = -1;
        if (tryStmt.getHandler() != null) {
            catchJump = emitter.emitJump(Opcode.CATCH);
        }

        compileTryBlock(compilerContext, tryStmt.getBlock());

        if (tryStmt.getHandler() != null) {
            emitter.emitOpcode(Opcode.NIP_CATCH);
        }

        int jumpOverCatch = emitter.emitJump(Opcode.GOTO);

        if (tryStmt.getHandler() != null) {
            emitter.patchJumpToHere(catchJump);
            TryStatement.CatchClause handler = tryStmt.getHandler();

            if (handler.getParam() != null) {
                enterCatchScope(compilerContext, handler.getParam());
            }
            compileTryFinallyBlock(compilerContext, handler.getBody());
            if (handler.getParam() != null) {
                exitCatchScope(compilerContext);
            }
        }

        emitter.patchJumpToHere(jumpOverCatch);
    }

    private static void compileTryFinallyBlock(CompilerContext compilerContext, BlockStatement block) {
        BytecodeEmitter emitter = compilerContext.emitter;
        boolean savedIsLastInProgram = compilerContext.isLastInProgram;
        compilerContext.isLastInProgram = false;
        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        List<Statement> body = block.getBody();

        // Phase 1: pre-declare lexical bindings for TDZ.
        StatementCompiler.predeclareLexicalBindings(compilerContext, body, false);

        // Phase 2: hoist function declarations.
        for (Statement statement : body) {
            if (statement instanceof FunctionDeclaration functionDeclaration) {
                FunctionDeclarationCompiler.compile(compilerContext, functionDeclaration);
            }
        }

        // Set up CATCH for exception-safe using disposal
        boolean blockHasUsing = EmitHelpers.hasUsingDeclarations(body);
        int usingCatchJump = EmitHelpers.beginUsingScope(compilerContext, blockHasUsing);

        if (compilerContext.evalReturnLocalIndex >= 0) {
            // The shared completion local handles every statement kind with net-zero stack effect.
            // Leave exactly one result above the catch marker, including when the last statement
            // is another try. A missing result here would make NIP_CATCH corrupt the handler stack.
            emitter.emitUndefinedLocal(compilerContext.evalReturnLocalIndex);
            for (Statement statement : body) {
                if (!(statement instanceof FunctionDeclaration)) {
                    compilerContext.compileStatement(statement);
                }
            }
            emitter.emitGetLocal(compilerContext.evalReturnLocalIndex);
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
                    emitter.emitSourcePosition(statement.getLocation());
                    compilerContext.compileExpression(expressionStatement.getExpression());
                    if (compilerContext.evalReturnLocalIndex >= 0) {
                        emitter.emitOpcode(Opcode.DUP);
                        emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
                    }
                    if (!isLast) {
                        emitter.emitOpcode(Opcode.DROP);
                    }
                } else if (statement instanceof TryStatement tryStatement) {
                    boolean saved = compilerContext.isLastInProgram;
                    compilerContext.isLastInProgram = isLast;
                    emitter.emitSourcePosition(statement.getLocation());
                    compile(compilerContext, tryStatement);
                    compilerContext.isLastInProgram = saved;
                } else {
                    compilerContext.compileStatement(statement);
                    if (isLast) {
                        emitUndefinedCompletion(compilerContext);
                    }
                }
            }

            if (effectiveLastIndex < 0) {
                emitUndefinedCompletion(compilerContext);
            }
        }

        EmitHelpers.finishUsingScope(compilerContext, usingCatchJump, true);
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
        compilerContext.isLastInProgram = savedIsLastInProgram;
    }

    private static void compileTryStatementWithFinally(CompilerContext compilerContext, TryStatement tryStmt) {
        BytecodeEmitter emitter = compilerContext.emitter;
        List<Integer> gosubPatches = new ArrayList<>();
        compilerContext.activeFinallyGosubPatches.push(gosubPatches);
        int finallyNipCatchCount = tryStmt.getHandler() != null ? 2 : 1;
        compilerContext.activeFinallyNipCatchCounts.push(finallyNipCatchCount);

        int finallyCatchJump = -1;
        int catchFromTryJump;
        if (tryStmt.getHandler() != null) {
            finallyCatchJump = emitter.emitJump(Opcode.CATCH);
            catchFromTryJump = emitter.emitJump(Opcode.CATCH);
        } else {
            catchFromTryJump = emitter.emitJump(Opcode.CATCH);
        }

        compileTryBlock(compilerContext, tryStmt.getBlock());
        emitter.emitOpcode(Opcode.NIP_CATCH);
        if (tryStmt.getHandler() != null) {
            emitter.emitOpcode(Opcode.NIP_CATCH);
        }
        int tryNormalGosub = emitter.emitJump(Opcode.GOSUB);
        List<Integer> jumpsToEnd = new ArrayList<>();
        jumpsToEnd.add(emitter.emitJump(Opcode.GOTO));

        int catchNormalGosub = -1;
        int catchExceptionGosub = -1;
        int finallyExceptionGosub = -1;

        if (tryStmt.getHandler() != null) {
            emitter.patchJumpToHere(catchFromTryJump);

            TryStatement.CatchClause handler = tryStmt.getHandler();
            if (handler.getParam() != null) {
                enterCatchScope(compilerContext, handler.getParam());
            } else {
                emitter.emitOpcode(Opcode.DROP);
            }
            int catchBodyCatchJump = emitter.emitJump(Opcode.CATCH);
            compileTryFinallyBlock(compilerContext, handler.getBody());
            emitter.emitOpcode(Opcode.NIP_CATCH);
            emitter.emitOpcode(Opcode.NIP_CATCH);
            catchNormalGosub = emitter.emitJump(Opcode.GOSUB);
            jumpsToEnd.add(emitter.emitJump(Opcode.GOTO));
            emitter.patchJumpToHere(catchBodyCatchJump);
            emitter.emitOpcode(Opcode.NIP_CATCH);
            catchExceptionGosub = emitter.emitJump(Opcode.GOSUB);
            emitter.emitOpcode(Opcode.THROW);
            if (handler.getParam() != null) {
                exitCatchScope(compilerContext);
            }
            emitter.patchJumpToHere(finallyCatchJump);
            emitter.markCatchAsFinally(finallyCatchJump);
            finallyExceptionGosub = emitter.emitJump(Opcode.GOSUB);
            emitter.emitOpcode(Opcode.THROW);
        } else {
            emitter.patchJumpToHere(catchFromTryJump);
            emitter.markCatchAsFinally(catchFromTryJump);
            finallyExceptionGosub = emitter.emitJump(Opcode.GOSUB);
            emitter.emitOpcode(Opcode.THROW);
        }

        compilerContext.activeFinallyGosubPatches.pop();
        compilerContext.activeFinallyNipCatchCounts.pop();

        int finallyOffset = emitter.currentOffset();
        compileFinallyBlockBody(compilerContext, tryStmt.getFinalizer());
        emitter.emitOpcode(Opcode.RET);

        emitter.patchJump(tryNormalGosub, finallyOffset);
        if (catchNormalGosub >= 0) {
            emitter.patchJump(catchNormalGosub, finallyOffset);
        }
        if (catchExceptionGosub >= 0) {
            emitter.patchJump(catchExceptionGosub, finallyOffset);
        }
        if (finallyExceptionGosub >= 0) {
            emitter.patchJump(finallyExceptionGosub, finallyOffset);
        }
        emitter.patchJumps(gosubPatches, finallyOffset);

        int endOffset = emitter.currentOffset();
        for (int jumpToEnd : jumpsToEnd) {
            emitter.patchJump(jumpToEnd, endOffset);
        }
    }

    /**
     * Start fresh bindings for a destructuring catch parameter: new on every entry, and in their temporal dead zone
     * until the pattern initializes them.
     */
    private static void emitFreshCatchBindings(CompilerContext compilerContext, Pattern catchParam) {
        Set<String> names = new LinkedHashSet<>();
        CompilerAnalysis.collectPatternBindingNames(catchParam, names);
        // declarePatternVariables has just declared every one of these names in the catch scope.
        for (String name : names) {
            int localIndex = compilerContext.scopeManager.currentScope().getLocal(name);
            compilerContext.emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, localIndex);
            compilerContext.tdzLocals.add(name);
        }
    }

    private static void emitUndefinedCompletion(CompilerContext compilerContext) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitOpcode(Opcode.UNDEFINED);
        if (compilerContext.evalReturnLocalIndex >= 0) {
            emitter.emitOpcode(Opcode.DUP);
            emitter.emitPutLocal(compilerContext.evalReturnLocalIndex);
        }
    }

    private static void enterCatchScope(CompilerContext compilerContext, Pattern catchParam) {
        BytecodeEmitter emitter = compilerContext.emitter;
        compilerContext.pushState();
        compilerContext.scopeManager.enterScope();
        compilerContext.inGlobalScope = false;

        if (catchParam instanceof Identifier id) {
            int localIndex = compilerContext.scopeManager.currentScope().declareLocal(id.getName());
            compilerContext.scopeManager.currentScope().markSimpleCatchParam(id.getName());
            // A new binding on every entry, so a closure from an earlier pass through this catch, in a loop,
            // keeps its own exception.
            emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, localIndex);
            emitter.emitPutLocal(localIndex);
        } else {
            PatternCompiler.declarePatternVariables(compilerContext, catchParam);
            emitFreshCatchBindings(compilerContext, catchParam);
            compilerContext.compilePattern(catchParam);
        }
    }

    private static void exitCatchScope(CompilerContext compilerContext) {
        EmitHelpers.emitCurrentScopeUsingDisposal(compilerContext);
        compilerContext.scopeManager.exitScope();
        compilerContext.popState();
    }
}
