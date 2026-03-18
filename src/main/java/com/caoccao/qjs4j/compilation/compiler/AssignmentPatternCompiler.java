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

import com.caoccao.qjs4j.compilation.ast.AssignmentPattern;
import com.caoccao.qjs4j.compilation.ast.Identifier;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles AssignmentPattern (destructuring with default value) into bytecode.
 * Handles patterns like [x = defaultVal] or { y = defaultVal }.
 */
final class AssignmentPatternCompiler extends AstNodeCompiler<AssignmentPattern> {

    AssignmentPatternCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(AssignmentPattern assignPattern) {
        // Stack: [value]
        // If value is undefined, use the default value instead
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
        int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        // Value is undefined: drop it and use default
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.expressionCompiler.compile(assignPattern.getRight());
        if (assignPattern.getLeft() instanceof Identifier identifier
                && assignPattern.getRight().isAnonymousFunction()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.getName());
        }
        // Patch jump target
        compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
        // Now the stack has the resolved value; assign to the inner pattern
        compilerContext.patternCompiler.compile(assignPattern.getLeft());
    }
}
