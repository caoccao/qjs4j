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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

final class MemberExpressionCompiler {
    private MemberExpressionCompiler() {
    }

    static void compile(CompilerContext compilerContext, MemberExpression memberExpr) {
        boolean joinsOptionalChain = OptionalChainCompiler.consumeOptionalChainLink(compilerContext, memberExpr);
        if (memberExpr.getObject().isSuperIdentifier()) {
            emitGetSuperValue(compilerContext, memberExpr, false);
            return;
        }
        if (!joinsOptionalChain && memberExpr.isPartOfOptionalChain()) {
            // The last link of an optional chain: every `?.` in it exits here.
            OptionalChainCompiler.compileOptionalChain(compilerContext, () -> compileLink(compilerContext, memberExpr),
                    null);
        } else {
            compileLink(compilerContext, memberExpr);
        }
    }

    private static void compileLink(CompilerContext compilerContext, MemberExpression memberExpr) {
        // Stack: -> value
        OptionalChainCompiler.compileOptionalChainLinkTarget(compilerContext, memberExpr, memberExpr.getObject());
        if (memberExpr.isOptional()) {
            OptionalChainCompiler.emitOptionalChainTest(compilerContext, 1);
        }
        emitPropertyAccess(compilerContext, memberExpr, false);
    }

    static void emitGetSuperValue(CompilerContext compilerContext, MemberExpression member,
            boolean keepReceiverForCall) {
        emitSuperPropertyReference(compilerContext, member, keepReceiverForCall);
        compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
    }

    /**
     * Record the position a failing access of this member reports in a stack trace, as V8 does: the property name of a
     * {@code .name} access, the {@code [} of a computed one.
     *
     * @param memberExpr
     *            the member expression
     */
    static void emitMemberSourcePosition(CompilerContext compilerContext, MemberExpression memberExpr) {
        compilerContext.emitter.emitSourcePosition(
                memberExpr.isComputed() ? memberExpr.getLocation() : memberExpr.getProperty().getLocation());
    }

    static void emitPrivateSymbol(CompilerContext compilerContext, PrivateIdentifier privateIdentifier) {
        JSSymbol symbol = compilerContext.privateSymbol(privateIdentifier);
        if (symbol == null) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'",
                    privateIdentifier);
        }

        compilerContext.emitter.emitPushConstant(symbol);
    }

    /** A method call keeps the receiver below its function on the operand stack. */
    static void emitPropertyAccess(CompilerContext compilerContext, MemberExpression memberExpr, boolean keepReceiver) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (keepReceiver) {
            emitMemberSourcePosition(compilerContext, memberExpr);
        }
        if (memberExpr.isComputed() || memberExpr.getProperty() instanceof PrivateIdentifier) {
            if (keepReceiver) {
                emitter.emitOpcode(Opcode.DUP);
            }
            Opcode read;
            if (memberExpr.isComputed()) {
                compilerContext.compileExpression(memberExpr.getProperty());
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
                emitMemberSourcePosition(compilerContext, memberExpr);
            }
            emitter.emitOpcode(read);
        } else if (memberExpr.getProperty() instanceof Identifier id) {
            if (!keepReceiver) {
                emitMemberSourcePosition(compilerContext, memberExpr);
            }
            emitter.emitOpcodeAtom(keepReceiver ? Opcode.GET_FIELD2 : Opcode.GET_FIELD, id.getName());
        }
    }

    static void emitSuperPropertyKey(CompilerContext compilerContext, MemberExpression memberExpr) {
        if (memberExpr.isComputed()) {
            compilerContext.compileExpression(memberExpr.getProperty());
        } else if (memberExpr.getProperty() instanceof Identifier propId) {
            compilerContext.emitter.emitPushConstant(new JSString(propId.getName()));
        } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
            throw new JSCompilerException("super private fields are not supported", privateIdentifier);
        } else {
            compilerContext.compileExpression(memberExpr.getProperty());
        }
    }

    /** Stack: ... -> ... this superBase propertyKey. Computed keys are evaluated before GetSuperBase. */
    static void emitSuperPropertyReference(CompilerContext compilerContext, MemberExpression member) {
        emitSuperPropertyReference(compilerContext, member, false);
    }

    private static void emitSuperPropertyReference(CompilerContext compilerContext, MemberExpression member,
            boolean keepReceiverForCall) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitOpcode(Opcode.PUSH_THIS);
        if (keepReceiverForCall) {
            emitter.emitOpcode(Opcode.DUP);
        }
        if (member.isComputed()) {
            emitSuperPropertyKey(compilerContext, member);
        }
        emitter.emitSuperBase();
        if (member.isComputed()) {
            emitter.emitOpcode(Opcode.SWAP);
        } else {
            emitSuperPropertyKey(compilerContext, member);
        }
    }
}
