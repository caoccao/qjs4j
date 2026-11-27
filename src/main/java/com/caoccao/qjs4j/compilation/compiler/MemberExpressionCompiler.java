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
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

final class MemberExpressionCompiler extends AstNodeCompiler<MemberExpression> {
    MemberExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(MemberExpression memberExpr) {
        boolean joinsOptionalChain = compilerContext.emitHelpers.consumeOptionalChainLink(memberExpr);
        if (memberExpr.getObject().isSuperIdentifier()) {
            compilerContext.emitHelpers.emitGetSuperValue(memberExpr, false);
            return;
        }
        if (!joinsOptionalChain && memberExpr.isPartOfOptionalChain()) {
            // The last link of an optional chain: every `?.` in it exits here.
            compilerContext.emitHelpers.compileOptionalChain(() -> compileLink(memberExpr), null);
        } else {
            compileLink(memberExpr);
        }
    }

    private void compileLink(MemberExpression memberExpr) {
        // Stack: -> value
        compilerContext.emitHelpers.compileOptionalChainLinkTarget(memberExpr, memberExpr.getObject());
        if (memberExpr.isOptional()) {
            compilerContext.emitHelpers.emitOptionalChainTest(1);
        }
        emitPropertyAccess(memberExpr, false);
    }

    /** A method call keeps the receiver below its function on the operand stack. */
    void emitPropertyAccess(MemberExpression memberExpr, boolean keepReceiver) {
        if (keepReceiver) {
            compilerContext.emitHelpers.emitMemberSourcePosition(memberExpr);
        }
        if (memberExpr.isComputed() || memberExpr.getProperty() instanceof PrivateIdentifier) {
            if (keepReceiver) {
                emitter.emitOpcode(Opcode.DUP);
            }
            Opcode read;
            if (memberExpr.isComputed()) {
                compilerContext.expressionCompiler.compile(memberExpr.getProperty());
                read = Opcode.GET_ARRAY_EL;
            } else {
                PrivateIdentifier id = (PrivateIdentifier) memberExpr.getProperty();
                JSSymbol symbol = compilerContext.privateSymbol(id);
                if (symbol == null) {
                    throw new JSSyntaxErrorException("Unexpected private field", id.getLocation());
                }
                emitter.emitPushConstant(symbol);
                read = Opcode.GET_PRIVATE_FIELD;
            }
            if (!keepReceiver) {
                compilerContext.emitHelpers.emitMemberSourcePosition(memberExpr);
            }
            emitter.emitOpcode(read);
        } else if (memberExpr.getProperty() instanceof Identifier id) {
            if (!keepReceiver) {
                compilerContext.emitHelpers.emitMemberSourcePosition(memberExpr);
            }
            emitter.emitOpcodeAtom(keepReceiver ? Opcode.GET_FIELD2 : Opcode.GET_FIELD, id.getName());
        }
    }
}
