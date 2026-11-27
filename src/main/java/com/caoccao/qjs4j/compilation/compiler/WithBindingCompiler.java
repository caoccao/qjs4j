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

import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.vm.Opcode;

/** Emits the HasBinding checks for a with environment, including Symbol.unscopables. */
final class WithBindingCompiler {
    private WithBindingCompiler() {
    }

    /** On success, the with-object remains on the stack. Both failure paths enter the same fallback. */
    static Fallback emitHasBinding(CompilerContext compilerContext, String name) {
        BytecodeEmitter emitter = compilerContext.emitter;
        int jumpToFallback = emitHasPropertyTest(emitter, name);
        emitter.emitOpcode(Opcode.DUP);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps(compilerContext);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = emitter.emitJump(Opcode.IF_TRUE);

        int jumpToGetBindingValue = emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        emitter.emitOpcode(Opcode.DROP);

        emitter.patchJumpToHere(jumpToGetBindingValue);
        return new Fallback(jumpToFallback, jumpToFallbackWhenBlocked);
    }

    static int emitHasPropertyTest(BytecodeEmitter emitter, String name) {
        emitter.emitOpcode(Opcode.DUP);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        emitter.emitOpcode(Opcode.ROT3L);
        emitter.emitOpcode(Opcode.IN);
        return emitter.emitJump(Opcode.IF_FALSE);
    }

    private static int[] emitWithUnscopablesSkipJumps(CompilerContext compilerContext) {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToResolveWithoutUnscopablesOnNullish = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("object"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToCheckBlockedWhenObject = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("function"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToResolveWithoutUnscopablesOnPrimitive = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.patchJumpToHere(jumpToCheckBlockedWhenObject);
        return new int[]{jumpToResolveWithoutUnscopablesOnNullish, jumpToResolveWithoutUnscopablesOnPrimitive};
    }

    record Fallback(int missingProperty, int blockedByUnscopables) {
        void begin(BytecodeEmitter emitter) {
            emitter.patchJumpToHere(missingProperty);
            emitter.patchJumpToHere(blockedByUnscopables);
            emitter.emitOpcode(Opcode.DROP);
        }
    }
}
