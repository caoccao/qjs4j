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

import com.caoccao.qjs4j.compilation.ast.ThrowStatement;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.vm.Opcode;

final class ThrowStatementCompiler extends AstNodeCompiler<ThrowStatement> {
    ThrowStatementCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ThrowStatement throwStmt) {
        compilerContext.expressionCompiler.compile(throwStmt.getArgument());

        // Check if any scope in the disposal path has a CATCH handler for using declarations.
        // If so, just throw directly - the CATCH handler will handle disposal with the error
        // and compose SuppressedErrors per ES spec DisposeResources algorithm.
        boolean hasScopeUsingCatch = false;
        for (CompilerScope scope : compilerContext.scopeManager) {
            if (scope.getScopeDepth() > 0 && scope.getUsingStackLocalIndex() != null
                    && scope.getUsingCatchJumpPosition() >= 0) {
                hasScopeUsingCatch = true;
                break;
            }
        }

        if (hasScopeUsingCatch) {
            // Just throw - the block/function CATCH handler will dispose with the error
            compilerContext.emitter.emitOpcode(Opcode.THROW);
            return;
        }

        // Legacy path: when no block-level CATCH protects using declarations
        int throwValueIndex = compilerContext.scopeManager.currentScope().declareLocal("$throw_value_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, throwValueIndex);

        int disposalCatchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
        compilerContext.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(0);
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, throwValueIndex);
        compilerContext.emitter.emitOpcode(Opcode.THROW);

        compilerContext.emitter.patchJump(disposalCatchJump, compilerContext.emitter.currentOffset());

        int disposalErrorIndex = compilerContext.scopeManager.currentScope().declareLocal("$disposal_error_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, disposalErrorIndex);

        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, "SuppressedError");
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, disposalErrorIndex);
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, throwValueIndex);
        compilerContext.emitter.emitOpcodeConstant(
                Opcode.PUSH_CONST,
                new JSString("An error was suppressed during disposal"));
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, 3);
        compilerContext.emitter.emitOpcode(Opcode.THROW);
    }
}
