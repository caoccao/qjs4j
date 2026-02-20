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
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

final class ExpressionAssignmentCompiler {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;
    private final ExpressionCompiler owner;

    ExpressionAssignmentCompiler(ExpressionCompiler owner, CompilerContext ctx, CompilerDelegates delegates) {
        this.owner = owner;
        this.ctx = ctx;
        this.delegates = delegates;
    }

    void compileAssignmentExpression(AssignmentExpression assignExpr) {
        Expression left = assignExpr.left();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();

        if (operator == AssignmentExpression.AssignmentOperator.LOGICAL_AND_ASSIGN ||
                operator == AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN ||
                operator == AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN) {
            compileLogicalAssignment(assignExpr);
            return;
        }

        if (left instanceof CallExpression) {
            owner.compileExpression(left);
            ctx.emitter.emitOpcode(Opcode.DROP);
            ctx.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            ctx.emitter.emitU8(5);
            return;
        }

        if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
            if (left instanceof Identifier id) {
                String name = id.name();
                Integer localIndex = ctx.findLocalInScopes(name);
                if (localIndex != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
                } else {
                    Integer capturedIndex = ctx.resolveCapturedBindingIndex(name);
                    if (capturedIndex != null) {
                        ctx.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                    } else {
                        ctx.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                    }
                }
            } else if (left instanceof MemberExpression memberExpr) {
                if (ctx.isSuperMemberExpression(memberExpr)) {
                    ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
                    ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    ctx.emitter.emitU8(4);
                    ctx.emitter.emitOpcode(Opcode.GET_SUPER);
                    delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                    ctx.emitter.emitOpcode(Opcode.DUP3);
                    ctx.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
                } else {
                    owner.compileExpression(memberExpr.object());
                    if (memberExpr.computed()) {
                        owner.compileExpression(memberExpr.property());
                        ctx.emitter.emitOpcode(Opcode.DUP2);
                        ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        String fieldName = privateId.name();
                        JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                        if (symbol != null) {
                            ctx.emitter.emitOpcode(Opcode.DUP);
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                        } else {
                            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                        }
                    } else if (memberExpr.property() instanceof Identifier propId) {
                        ctx.emitter.emitOpcode(Opcode.DUP);
                        ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                    }
                }
            }

            owner.compileExpression(assignExpr.right());

            switch (operator) {
                case PLUS_ASSIGN -> ctx.emitter.emitOpcode(Opcode.ADD);
                case MINUS_ASSIGN -> ctx.emitter.emitOpcode(Opcode.SUB);
                case MUL_ASSIGN -> ctx.emitter.emitOpcode(Opcode.MUL);
                case DIV_ASSIGN -> ctx.emitter.emitOpcode(Opcode.DIV);
                case MOD_ASSIGN -> ctx.emitter.emitOpcode(Opcode.MOD);
                case EXP_ASSIGN -> ctx.emitter.emitOpcode(Opcode.EXP);
                case LSHIFT_ASSIGN -> ctx.emitter.emitOpcode(Opcode.SHL);
                case RSHIFT_ASSIGN -> ctx.emitter.emitOpcode(Opcode.SAR);
                case URSHIFT_ASSIGN -> ctx.emitter.emitOpcode(Opcode.SHR);
                case AND_ASSIGN -> ctx.emitter.emitOpcode(Opcode.AND);
                case OR_ASSIGN -> ctx.emitter.emitOpcode(Opcode.OR);
                case XOR_ASSIGN -> ctx.emitter.emitOpcode(Opcode.XOR);
                default -> throw new JSCompilerException("Unknown assignment operator: " + operator);
            }
        } else {
            owner.compileExpression(assignExpr.right());
        }

        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = ctx.findLocalInScopes(name);

            if (localIndex != null) {
                ctx.emitter.emitOpcodeU16(Opcode.SET_LOCAL, localIndex);
            } else {
                Integer capturedIndex = ctx.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.SET_VAR_REF, capturedIndex);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (operator == AssignmentExpression.AssignmentOperator.ASSIGN) {
                if (ctx.isSuperMemberExpression(memberExpr)) {
                    ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
                    ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    ctx.emitter.emitU8(4);
                    ctx.emitter.emitOpcode(Opcode.GET_SUPER);
                    delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                    ctx.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                } else {
                    owner.compileExpression(memberExpr.object());
                    if (memberExpr.computed()) {
                        owner.compileExpression(memberExpr.property());
                        ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        String fieldName = privateId.name();
                        JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                        if (symbol != null) {
                            ctx.emitter.emitOpcode(Opcode.SWAP);
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            ctx.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                        } else {
                            ctx.emitter.emitOpcode(Opcode.DROP);
                        }
                    } else if (memberExpr.property() instanceof Identifier propId) {
                        ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                    }
                }
            } else {
                if (ctx.isSuperMemberExpression(memberExpr)) {
                    ctx.emitter.emitOpcode(Opcode.INSERT4);
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    ctx.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                } else if (memberExpr.computed()) {
                    ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                    String fieldName = privateId.name();
                    JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        ctx.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    } else {
                        ctx.emitter.emitOpcode(Opcode.DROP);
                    }
                } else if (memberExpr.property() instanceof Identifier propId) {
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                }
            }
        } else if (left instanceof ArrayExpression arrayExpr) {
            ctx.emitter.emitOpcode(Opcode.DUP);
            delegates.patterns.compileArrayDestructuringAssignment(arrayExpr);
        } else if (left instanceof ObjectExpression objExpr) {
            ctx.emitter.emitOpcode(Opcode.DUP);
            delegates.patterns.compileObjectDestructuringAssignment(objExpr);
        }
    }

    void compileLogicalAssignment(AssignmentExpression assignExpr) {
        Expression left = assignExpr.left();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();

        int depthLvalue;
        if (left instanceof Identifier) {
            depthLvalue = 0;
        } else if (left instanceof MemberExpression memberExpr) {
            depthLvalue = ctx.isSuperMemberExpression(memberExpr) ? 3 : (memberExpr.computed() ? 2 : 1);
        } else {
            throw new JSCompilerException("Invalid left-hand side in logical assignment");
        }

        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = ctx.findLocalInScopes(name);
            if (localIndex != null) {
                ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
            } else {
                Integer capturedIndex = ctx.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (ctx.isSuperMemberExpression(memberExpr)) {
                ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
                ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                ctx.emitter.emitU8(4);
                ctx.emitter.emitOpcode(Opcode.GET_SUPER);
                delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                ctx.emitter.emitOpcode(Opcode.DUP3);
                ctx.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
            } else {
                owner.compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    owner.compileExpression(memberExpr.property());
                    ctx.emitter.emitOpcode(Opcode.DUP2);
                    ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else if (memberExpr.property() instanceof Identifier propId) {
                    ctx.emitter.emitOpcode(Opcode.DUP);
                    ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                }
            }
        }

        ctx.emitter.emitOpcode(Opcode.DUP);

        int jumpToCleanup;
        if (operator == AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN) {
            ctx.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToCleanup = ctx.emitter.emitJump(Opcode.IF_FALSE);
        } else if (operator == AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN) {
            jumpToCleanup = ctx.emitter.emitJump(Opcode.IF_TRUE);
        } else {
            jumpToCleanup = ctx.emitter.emitJump(Opcode.IF_FALSE);
        }

        ctx.emitter.emitOpcode(Opcode.DROP);
        owner.compileExpression(assignExpr.right());

        switch (depthLvalue) {
            case 0 -> {
            }
            case 1 -> ctx.emitter.emitOpcode(Opcode.SWAP);
            case 2 -> ctx.emitter.emitOpcode(Opcode.ROT3R);
            case 3 -> {
                ctx.emitter.emitOpcode(Opcode.INSERT4);
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
            default -> throw new JSCompilerException("Invalid depth for logical assignment");
        }

        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = ctx.findLocalInScopes(name);
            if (localIndex != null) {
                ctx.emitter.emitOpcodeU16(Opcode.SET_LOCAL, localIndex);
            } else {
                Integer capturedIndex = ctx.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.SET_VAR_REF, capturedIndex);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (ctx.isSuperMemberExpression(memberExpr)) {
                ctx.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
            } else if (memberExpr.computed()) {
                ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
            }
        }

        int jumpToEnd = ctx.emitter.emitJump(Opcode.GOTO);
        ctx.emitter.patchJump(jumpToCleanup, ctx.emitter.currentOffset());

        for (int i = 0; i < depthLvalue; i++) {
            ctx.emitter.emitOpcode(Opcode.NIP);
        }

        ctx.emitter.patchJump(jumpToEnd, ctx.emitter.currentOffset());
    }
}
