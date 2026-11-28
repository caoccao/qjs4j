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
import com.caoccao.qjs4j.core.disposable.JSAsyncDisposableStack;
import com.caoccao.qjs4j.core.disposable.JSDisposableStack;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

/** Emits resource registration and disposal on normal and abrupt scope exits. */
final class UsingScopeCompiler {
    private UsingScopeCompiler() {
    }

    static int beginUsingScope(CompilerContext compilerContext, boolean hasUsing) {
        if (!hasUsing) {
            return -1;
        }
        int handler = compilerContext.emitter.emitJump(Opcode.CATCH);
        compilerContext.scopeManager.currentScope().setUsingCatchJumpPosition(handler);
        return handler;
    }

    static void emitCreateStack(BytecodeEmitter emitter, int local, boolean async) {
        emitter.emitOpcodeAtom(Opcode.GET_VAR, async ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME);
        emitter.emitIntOperand(Opcode.CALL_CONSTRUCTOR, 0);
        emitter.emitPutLocal(local);
    }

    static void emitCurrentScopeUsingDisposal(CompilerContext compilerContext) {
        emitScopeUsingDisposal(compilerContext, compilerContext.scopeManager.currentScope());
    }

    private static void emitDisposeCall(BytecodeEmitter emitter, CompilerScope scope, int stackLocal, int errorLocal) {
        boolean async = scope.isUsingStackAsync();
        emitter.emitLocalMethodCall(stackLocal, async ? "disposeAsync" : "dispose", errorLocal);
        if (async) {
            emitter.emitOpcode(Opcode.AWAIT);
        }
        emitter.emitOpcode(Opcode.DROP);
    }

    static void emitRegisterResource(CompilerContext context, int stackLocal) {
        int argument = context.storeTemporary("#using_arg_");
        context.emitter.emitLocalMethodCall(stackLocal, "use", argument);
    }

    static void emitScopeUsingDisposal(CompilerContext compilerContext, CompilerScope scope) {
        BytecodeEmitter emitter = compilerContext.emitter;
        Integer usingStackLocalIndex = scope.getUsingStackLocalIndex();
        if (usingStackLocalIndex == null) {
            return;
        }

        emitDisposeCall(emitter, scope, usingStackLocalIndex, -1);
    }

    /**
     * Emit disposal with a caught exception passed as an argument. Stack on entry: [caught_exception] The caught
     * exception is passed to dispose/disposeAsync so it can compose SuppressedErrors.
     */
    static void emitScopeUsingDisposalWithException(CompilerContext compilerContext, CompilerScope scope) {
        BytecodeEmitter emitter = compilerContext.emitter;
        Integer usingStackLocalIndex = scope.getUsingStackLocalIndex();
        if (usingStackLocalIndex == null) {
            // No using stack - just re-throw the caught exception
            emitter.emitOpcode(Opcode.THROW);
            return;
        }

        // Stack: [caught_exception]
        // Save exception to a temp local so we can pass it as an argument
        int exceptionLocalIndex = compilerContext.storeTemporary("#using_exception_");

        // Check if the using stack was actually initialized at runtime.
        // If the exception occurred before the `using` declaration executed,
        // the stack local is still uninitialized - just re-throw.
        emitter.emitGetLocal(usingStackLocalIndex);
        emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int skipDisposeJump = emitter.emitJump(Opcode.IF_TRUE);

        emitDisposeCall(emitter, scope, usingStackLocalIndex, exceptionLocalIndex);
        // After dispose/disposeAsync, the pending exception is set (either original or composed SuppressedError)
        // The VM will detect it and unwind automatically
        int skipRethrowJump = emitter.emitJump(Opcode.GOTO);

        // Stack local was uninitialized - just re-throw the original exception
        emitter.patchJumpToHere(skipDisposeJump);
        emitter.emitGetLocal(exceptionLocalIndex);
        emitter.emitOpcode(Opcode.THROW);

        emitter.patchJumpToHere(skipRethrowJump);
    }

    static void emitUsingDisposalsForScopeDepthGreaterThan(CompilerContext compilerContext, int targetScopeDepth) {
        BytecodeEmitter emitter = compilerContext.emitter;
        for (CompilerScope scope : compilerContext.scopeManager) {
            if (scope.getScopeDepth() > targetScopeDepth) {
                // If scope has a CATCH handler on the stack (from using declaration),
                // remove it before calling dispose (we're leaving via control flow)
                if (scope.getUsingCatchJumpPosition() >= 0 && scope.getUsingStackLocalIndex() != null) {
                    emitter.emitOpcode(Opcode.UNDEFINED);
                    emitter.emitOpcode(Opcode.NIP_CATCH);
                    emitter.emitOpcode(Opcode.DROP);
                }
                emitScopeUsingDisposal(compilerContext, scope);
            }
        }
    }

    static int ensureUsingStackLocal(CompilerContext compilerContext, boolean asyncUsingDeclaration, ASTNode ast) {
        BytecodeEmitter emitter = compilerContext.emitter;
        CompilerScope scope = compilerContext.scopeManager.currentScope();
        Integer existingLocalIndex = scope.getUsingStackLocalIndex();
        if (existingLocalIndex != null) {
            if (asyncUsingDeclaration && !scope.isUsingStackAsync()) {
                throw new JSCompilerException("Cannot mix await using with sync using stack in the same scope", ast);
            }
            return existingLocalIndex;
        }

        boolean useAsyncStack = asyncUsingDeclaration || compilerContext.isInAsyncFunction;
        int stackLocalIndex = scope
                .declareLocal("#using_stack_" + scope.getScopeDepth() + "_" + emitter.currentOffset());

        emitCreateStack(emitter, stackLocalIndex, useAsyncStack);

        scope.setUsingStackLocal(stackLocalIndex, useAsyncStack);
        return stackLocalIndex;
    }

    /** Dispose on both normal and abrupt completion, preserving a block result when present. */
    static void finishUsingScope(CompilerContext compilerContext, int handler, boolean hasCompletionValue) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (handler < 0) {
            emitCurrentScopeUsingDisposal(compilerContext);
            return;
        }
        if (!hasCompletionValue) {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        emitter.emitOpcode(Opcode.NIP_CATCH);
        if (!hasCompletionValue) {
            emitter.emitOpcode(Opcode.DROP);
        }
        emitCurrentScopeUsingDisposal(compilerContext);
        int end = emitter.emitJump(Opcode.GOTO);
        emitter.patchJumpToHere(handler);
        emitScopeUsingDisposalWithException(compilerContext, compilerContext.scopeManager.currentScope());
        emitter.patchJumpToHere(end);
    }

    static boolean hasUsingDeclarations(java.util.List<Statement> statements) {
        for (Statement stmt : statements) {
            if (stmt instanceof VariableDeclaration vd && vd.getKind().isUsing()) {
                return true;
            }
        }
        return false;
    }
}
