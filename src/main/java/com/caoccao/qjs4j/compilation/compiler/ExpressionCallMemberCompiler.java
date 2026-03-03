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
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

final class ExpressionCallMemberCompiler {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;
    private final ExpressionCompiler owner;

    ExpressionCallMemberCompiler(ExpressionCompiler owner, CompilerContext compilerContext, CompilerDelegates delegates) {
        this.owner = owner;
        this.compilerContext = compilerContext;
        this.delegates = delegates;
    }

    void compileCallExpression(CallExpression callExpr) {
        boolean hasSpread = callExpr.arguments().stream().anyMatch(arg -> arg instanceof SpreadElement);
        if (hasSpread) {
            compileCallExpressionWithSpread(callExpr);
        } else {
            compileCallExpressionRegular(callExpr);
        }
    }

    void compileCallExpressionRegular(CallExpression callExpr) {
        if (callExpr.callee() instanceof Identifier calleeId && "super".equals(calleeId.name())) {
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(3);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(2);
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            compilerContext.emitter.emitOpcode(Opcode.INIT_CTOR);
            return;
        }
        if (callExpr.callee() instanceof Identifier calleeId && "eval".equals(calleeId.name())) {
            owner.compileExpression(callExpr.callee());
            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }
            compilerContext.emitter.emitOpcode(Opcode.EVAL);
            compilerContext.emitter.emitU16(callExpr.arguments().size());
            compilerContext.emitter.emitU16(0);
            return;
        }
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                for (Expression arg : callExpr.arguments()) {
                    owner.compileExpression(arg);
                }
                compilerContext.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
                return;
            }

            owner.compileExpression(memberExpr.object());
            compilerContext.emitter.emitOpcode(Opcode.DUP);

            if (memberExpr.computed()) {
                owner.compileExpression(memberExpr.property());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                String fieldName = privateId.name();
                JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    throw new JSSyntaxErrorException("Unexpected private field");
                }
            } else if (memberExpr.property() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            compilerContext.emitter.emitOpcode(Opcode.SWAP);

            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }

            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        } else {
            owner.compileExpression(callExpr.callee());
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        }
    }

    void compileCallExpressionWithSpread(CallExpression callExpr) {
        if (callExpr.callee() instanceof Identifier calleeId && "super".equals(calleeId.name())) {
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(3);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(2);
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            compilerContext.emitter.emitOpcode(Opcode.INIT_CTOR);
            return;
        }
        if (callExpr.callee() instanceof Identifier calleeId && "eval".equals(calleeId.name())) {
            owner.compileExpression(callExpr.callee());
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY_EVAL, 0);
            return;
        }
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
                compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
                return;
            }

            owner.compileExpression(memberExpr.object());
            compilerContext.emitter.emitOpcode(Opcode.DUP);

            if (memberExpr.computed()) {
                owner.compileExpression(memberExpr.property());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                String fieldName = privateId.name();
                JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    throw new JSSyntaxErrorException("Unexpected private field");
                }
            }
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            owner.compileExpression(callExpr.callee());
        }

        delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
        compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
    }

    void compileMemberExpression(MemberExpression memberExpr) {
        if (compilerContext.isSuperMemberExpression(memberExpr)) {
            delegates.emitHelpers.emitGetSuperValue(memberExpr, false);
            return;
        }

        owner.compileExpression(memberExpr.object());

        if (memberExpr.optional()) {
            // Optional chaining: obj?.prop
            // Stack: obj -> DUP -> IS_UNDEFINED_OR_NULL -> IF_TRUE(undef) -> access -> GOTO(end) -> undef: DROP UNDEFINED -> end:
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            int jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

            // Normal property access
            emitPropertyAccess(memberExpr);

            int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

            // Undefined path
            compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);

            // End
            compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
            return;
        }

        emitPropertyAccess(memberExpr);
    }

    void compileNewExpression(NewExpression newExpr) {
        boolean hasSpread = newExpr.arguments().stream().anyMatch(arg -> arg instanceof SpreadElement);

        owner.compileExpression(newExpr.callee());

        if (hasSpread) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(newExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            return;
        }

        for (Expression arg : newExpr.arguments()) {
            owner.compileExpression(arg);
        }
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.arguments().size());
    }

    private void emitPropertyAccess(MemberExpression memberExpr) {
        if (memberExpr.computed()) {
            owner.compileExpression(memberExpr.property());
            compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
            String fieldName = privateId.name();
            JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
            if (symbol != null) {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            } else {
                throw new JSSyntaxErrorException("Unexpected private field");
            }
        } else if (memberExpr.property() instanceof Identifier propId) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
        }
    }
}
