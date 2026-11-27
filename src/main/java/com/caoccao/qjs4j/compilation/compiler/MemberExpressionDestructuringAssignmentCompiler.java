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

import com.caoccao.qjs4j.compilation.ast.Identifier;
import com.caoccao.qjs4j.compilation.ast.MemberExpression;
import com.caoccao.qjs4j.compilation.ast.PrivateIdentifier;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles MemberExpression destructuring assignment targets into bytecode.
 */
final class MemberExpressionDestructuringAssignmentCompiler {

    private MemberExpressionDestructuringAssignmentCompiler() {
    }

    static void compile(CompilerContext compilerContext, MemberExpression memberExpr) {
        if (memberExpr.isOptional()) {
            throw new JSSyntaxErrorException("Invalid destructuring assignment target", memberExpr.getLocation());
        }
        if (memberExpr.getObject().isSuperIdentifier()) {
            compilerContext.emitHelpers.emitSuperPropertyReference(memberExpr);
        } else {
            compilerContext.expressionCompiler.compile(memberExpr.getObject());
            if (memberExpr.isComputed()) {
                compilerContext.expressionCompiler.compile(memberExpr.getProperty());
            }
        }
        store(compilerContext, memberExpr, false);
    }

    /** A pre-evaluated reference is below the value. An ordinary destructuring target is evaluated above it. */
    static void store(CompilerContext compilerContext, MemberExpression member, boolean valueOnTop) {
        if (member.getObject().isSuperIdentifier()) {
            if (!valueOnTop) {
                compilerContext.emitter.emitOpcode(Opcode.ROT4L);
            }
            compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
        } else if (member.isComputed()) {
            if (!valueOnTop) {
                compilerContext.emitter.emitOpcode(Opcode.ROT3L);
            }
            compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
        } else if (member.getProperty() instanceof PrivateIdentifier id) {
            if (!valueOnTop) {
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            }
            compilerContext.emitHelpers.emitPrivateSymbol(id);
            compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
        } else if (member.getProperty() instanceof Identifier id) {
            if (valueOnTop) {
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            }
            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, id.getName());
        } else if (valueOnTop) {
            return;
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }
}
