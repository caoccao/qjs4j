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

import com.caoccao.qjs4j.compilation.ast.ArrayPattern;
import com.caoccao.qjs4j.compilation.ast.Pattern;
import com.caoccao.qjs4j.compilation.ast.RestElement;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles ArrayPattern destructuring into bytecode.
 */
final class ArrayPatternCompiler extends AstNodeCompiler<ArrayPattern> {

    ArrayPatternCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ArrayPattern arrPattern) {
        // Array destructuring: [a, b] = value
        // Stack: [array]

        // Check if there's a rest element
        boolean hasRest = false;
        int restIndex = -1;
        for (int i = 0; i < arrPattern.getElements().size(); i++) {
            if (arrPattern.getElements().get(i) instanceof RestElement) {
                hasRest = true;
                restIndex = i;
                break;
            }
        }

        if (hasRest) {
            // Use iterator-based approach for rest elements (following QuickJS js_emit_spread_code)
            // Stack: [iterable]

            // Start iteration: iterable -> iter next catch_offset
            compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);

            // Process elements before rest
            for (int i = 0; i < restIndex; i++) {
                Pattern element = arrPattern.getElements().get(i);
                if (element != null) {
                    // Get next value: iter next -> iter next catch_offset value done
                    compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                    // Stack: iter next catch_offset value done
                    // Drop done flag
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    // Stack: iter next catch_offset value
                    // Assign value to pattern
                    compilerContext.patternCompiler.compile(element);
                    // Stack: iter next catch_offset (after assignment drops the value)
                } else {
                    // Skip element
                    compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                    // Stack: iter next catch_offset value done
                    compilerContext.emitter.emitOpcode(Opcode.DROP);  // Drop done
                    compilerContext.emitter.emitOpcode(Opcode.DROP);  // Drop value
                    // Stack: iter next catch_offset
                }
            }

            // Now handle the rest element
            // Following QuickJS js_emit_spread_code at line 25663
            // Stack: iter next catch_offset -> iter next catch_offset array

            // Create empty array with 0 elements
            compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);
            // Push initial index 0
            compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
            compilerContext.emitter.emitI32(0);

            // Loop to collect remaining elements
            int labelRestNext = compilerContext.emitter.currentOffset();

            // Get next value: iter next catch_offset array idx -> iter next catch_offset array idx value done
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 2);  // depth = 2 (array and idx)

            // Check if done
            int jumpRestDone = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

            // Not done: array idx value -> array idx
            compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
            // Increment index
            compilerContext.emitter.emitOpcode(Opcode.INC);
            // Continue loop - jump back to labelRestNext
            compilerContext.emitter.emitOpcode(Opcode.GOTO);
            int backJumpPos = compilerContext.emitter.currentOffset();
            compilerContext.emitter.emitU32(labelRestNext - (backJumpPos + 4));

            // Done collecting - patch the IF_TRUE jump
            compilerContext.emitter.patchJump(jumpRestDone, compilerContext.emitter.currentOffset());
            // Stack: iter next catch_offset array idx undef
            // Drop undef and idx
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            // Stack: iter next catch_offset array

            // Assign array to rest pattern
            RestElement restElement = (RestElement) arrPattern.getElements().get(restIndex);
            compilerContext.patternCompiler.compile(restElement.getArgument());

            // Clean up iterator state: drop catch_offset, next, iter
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else {
            // Iterator-based array binding semantics.
            compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
            int iteratorDoneLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                    "$arrayPatternIteratorDone" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);
            for (Pattern element : arrPattern.getElements()) {
                compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                if (element != null) {
                    compilerContext.patternCompiler.compile(element);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                }
            }
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, iteratorDoneLocalIndex);
            int skipIteratorCloseJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            // Iterator not exhausted by this pattern; call return() for IteratorClose.
            compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            int iteratorCloseDoneJump = compilerContext.emitter.emitJump(Opcode.GOTO);
            compilerContext.emitter.patchJump(skipIteratorCloseJump, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.patchJump(iteratorCloseDoneJump, compilerContext.emitter.currentOffset());
        }
    }
}
