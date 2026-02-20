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
import com.caoccao.qjs4j.vm.Opcode;

final class ExpressionCallMemberCompiler {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;
    private final ExpressionCompiler owner;

    ExpressionCallMemberCompiler(ExpressionCompiler owner, CompilerContext ctx, CompilerDelegates delegates) {
        this.owner = owner;
        this.ctx = ctx;
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
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(3);
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(2);
            ctx.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            ctx.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            ctx.emitter.emitOpcode(Opcode.INIT_CTOR);
            return;
        }
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            if (ctx.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                ctx.emitter.emitOpcode(Opcode.SWAP);
                for (Expression arg : callExpr.arguments()) {
                    owner.compileExpression(arg);
                }
                ctx.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
                return;
            }

            owner.compileExpression(memberExpr.object());
            ctx.emitter.emitOpcode(Opcode.DUP);

            if (memberExpr.computed()) {
                owner.compileExpression(memberExpr.property());
                ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                String fieldName = privateId.name();
                JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                }
            } else if (memberExpr.property() instanceof Identifier propId) {
                ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            ctx.emitter.emitOpcode(Opcode.SWAP);

            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }

            ctx.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        } else {
            owner.compileExpression(callExpr.callee());
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }
            ctx.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        }
    }

    void compileCallExpressionWithSpread(CallExpression callExpr) {
        if (callExpr.callee() instanceof Identifier calleeId && "super".equals(calleeId.name())) {
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(3);
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(2);
            ctx.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            ctx.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            ctx.emitter.emitOpcode(Opcode.INIT_CTOR);
            return;
        }
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            if (ctx.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
                ctx.emitter.emitOpcodeU16(Opcode.APPLY, 0);
                return;
            }

            owner.compileExpression(memberExpr.object());
            ctx.emitter.emitOpcode(Opcode.DUP);

            if (memberExpr.computed()) {
                owner.compileExpression(memberExpr.property());
                ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                String fieldName = privateId.name();
                JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                }
            }
        } else {
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
            owner.compileExpression(callExpr.callee());
        }

        delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
        ctx.emitter.emitOpcodeU16(Opcode.APPLY, 0);
    }

    void compileMemberExpression(MemberExpression memberExpr) {
        if (ctx.isSuperMemberExpression(memberExpr)) {
            delegates.emitHelpers.emitGetSuperValue(memberExpr, false);
            return;
        }

        owner.compileExpression(memberExpr.object());

        if (memberExpr.computed()) {
            owner.compileExpression(memberExpr.property());
            ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
            String fieldName = privateId.name();
            JSSymbol symbol = ctx.privateSymbols.get(fieldName);
            if (symbol != null) {
                ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            } else {
                ctx.emitter.emitOpcode(Opcode.DROP);
                ctx.emitter.emitOpcode(Opcode.UNDEFINED);
            }
        } else if (memberExpr.property() instanceof Identifier propId) {
            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
        }
    }

    void compileNewExpression(NewExpression newExpr) {
        boolean hasSpread = newExpr.arguments().stream().anyMatch(arg -> arg instanceof SpreadElement);

        owner.compileExpression(newExpr.callee());

        if (hasSpread) {
            ctx.emitter.emitOpcode(Opcode.DUP);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(newExpr.arguments());
            ctx.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            return;
        }

        for (Expression arg : newExpr.arguments()) {
            owner.compileExpression(arg);
        }
        ctx.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.arguments().size());
    }
}
