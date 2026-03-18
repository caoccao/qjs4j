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
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Compiles MemberExpression destructuring assignment targets into bytecode.
 */
final class MemberExpressionDestructuringAssignmentCompiler extends AstNodeCompiler<MemberExpression> {

    MemberExpressionDestructuringAssignmentCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(MemberExpression memberExpr) {
        // Stack: [value]
        if (memberExpr.isOptional()) {
            throw new JSSyntaxErrorException("Invalid destructuring assignment target");
        }
        if (memberExpr.getObject().isSuperIdentifier()) {
            // Stack starts with [value]
            compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
            // Stack: [value, this, superObj, key] → ROT4L → [this, superObj, key, value]
            compilerContext.emitter.emitOpcode(Opcode.ROT4L);
            compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
        } else {
            // Stack: [value]
            compilerContext.expressionCompiler.compile(memberExpr.getObject());
            // Stack: [value, obj]
            if (memberExpr.isComputed()) {
                compilerContext.expressionCompiler.compile(memberExpr.getProperty());
                // Stack: [value, obj, prop] → ROT3L → [obj, prop, value]
                compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
            } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
                String fieldName = privateIdentifier.getName();
                JSSymbol privateSymbol = compilerContext.privateSymbols != null
                        ? compilerContext.privateSymbols.get(fieldName)
                        : null;
                if (privateSymbol == null) {
                    throw new JSCompilerException("undefined private field '#" + fieldName + "'");
                }
                // Stack: [value, obj] -> [obj, value] -> [obj, value, privateSymbol]
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, privateSymbol);
                compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
            }
        }
        // PUT_* leaves value on stack; drop it
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }
}
