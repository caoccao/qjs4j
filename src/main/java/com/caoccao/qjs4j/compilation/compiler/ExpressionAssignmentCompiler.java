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
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;
import java.util.List;

final class ExpressionAssignmentCompiler {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;
    private final ExpressionCompiler owner;

    ExpressionAssignmentCompiler(ExpressionCompiler owner, CompilerContext compilerContext, CompilerDelegates delegates) {
        this.owner = owner;
        this.compilerContext = compilerContext;
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
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }

        if (left instanceof Identifier identifier) {
            compileIdentifierAssignmentExpression(assignExpr, identifier);
            return;
        }

        if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
            if (left instanceof MemberExpression memberExpr) {
                if (compilerContext.isSuperMemberExpression(memberExpr)) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4);
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                    compilerContext.emitter.emitOpcode(Opcode.DUP3);
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
                } else {
                    owner.compileExpression(memberExpr.object());
                    if (memberExpr.computed()) {
                        owner.compileExpression(memberExpr.property());
                        compilerContext.emitter.emitOpcode(Opcode.DUP2);
                        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        String fieldName = privateId.name();
                        JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                        if (symbol != null) {
                            compilerContext.emitter.emitOpcode(Opcode.DUP);
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                        } else {
                            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                        }
                    } else if (memberExpr.property() instanceof Identifier propId) {
                        compilerContext.emitter.emitOpcode(Opcode.DUP);
                        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                    }
                }
            }

            owner.compileExpression(assignExpr.right());

            switch (operator) {
                case PLUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.ADD);
                case MINUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SUB);
                case MUL_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MUL);
                case DIV_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.DIV);
                case MOD_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MOD);
                case EXP_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.EXP);
                case LSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHL);
                case RSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SAR);
                case URSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHR);
                case AND_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.AND);
                case OR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.OR);
                case XOR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.XOR);
                default -> throw new JSCompilerException("Unknown assignment operator: " + operator);
            }
        } else {
            owner.compileExpression(assignExpr.right());
        }

        if (left instanceof MemberExpression memberExpr) {
            if (operator == AssignmentExpression.AssignmentOperator.ASSIGN) {
                if (compilerContext.isSuperMemberExpression(memberExpr)) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4);
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                } else {
                    owner.compileExpression(memberExpr.object());
                    if (memberExpr.computed()) {
                        owner.compileExpression(memberExpr.property());
                        // Stack: [value, obj, prop] → ROT3L → [obj, prop, value]
                        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        String fieldName = privateId.name();
                        JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                        if (symbol != null) {
                            compilerContext.emitter.emitOpcode(Opcode.SWAP);
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                        } else {
                            compilerContext.emitter.emitOpcode(Opcode.DROP);
                        }
                    } else if (memberExpr.property() instanceof Identifier propId) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                    }
                }
            } else {
                if (compilerContext.isSuperMemberExpression(memberExpr)) {
                    compilerContext.emitter.emitOpcode(Opcode.INSERT4);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                } else if (memberExpr.computed()) {
                    // Stack: [obj, prop, newValue] — already in QuickJS order
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                    String fieldName = privateId.name();
                    JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    } else {
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                } else if (memberExpr.property() instanceof Identifier propId) {
                    // Stack: [obj, newValue] — need [newValue, obj] for PUT_FIELD
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                }
            }
        } else if (left instanceof ArrayExpression arrayExpr) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            delegates.patterns.compileArrayDestructuringAssignment(arrayExpr);
        } else if (left instanceof ObjectExpression objExpr) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            delegates.patterns.compileObjectDestructuringAssignment(objExpr);
        }
    }

    private void compileIdentifierAssignmentExpression(AssignmentExpression assignExpr, Identifier identifier) {
        String name = identifier.name();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();
        Integer localIndex = compilerContext.findLocalInScopes(name);
        Integer capturedIndex = localIndex == null ? compilerContext.resolveCapturedBindingIndex(name) : null;
        boolean isConstLocalBinding = localIndex != null && compilerContext.isLocalBindingConst(name);
        boolean isConstCapturedBinding = capturedIndex != null && compilerContext.isCapturedBindingConst(name);

        if (isConstLocalBinding || isConstCapturedBinding) {
            if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
                if (localIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
                }
            }

            owner.compileExpression(assignExpr.right());

            if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
                switch (operator) {
                    case PLUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.ADD);
                    case MINUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SUB);
                    case MUL_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MUL);
                    case DIV_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.DIV);
                    case MOD_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MOD);
                    case EXP_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.EXP);
                    case LSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHL);
                    case RSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SAR);
                    case URSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHR);
                    case AND_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.AND);
                    case OR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.OR);
                    case XOR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.XOR);
                    default -> throw new JSCompilerException("Unknown assignment operator: " + operator);
                }
            }

            if (localIndex != null) {
                emitConstAssignmentErrorForLocal(name, localIndex);
            } else {
                emitConstAssignmentErrorForCaptured(name, capturedIndex);
            }
            return;
        }

        emitIdentifierReference(name);
        if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
            compilerContext.emitter.emitOpcode(Opcode.GET_REF_VALUE);
        }

        owner.compileExpression(assignExpr.right());

        if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
            switch (operator) {
                case PLUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.ADD);
                case MINUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SUB);
                case MUL_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MUL);
                case DIV_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.DIV);
                case MOD_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MOD);
                case EXP_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.EXP);
                case LSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHL);
                case RSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SAR);
                case URSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHR);
                case AND_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.AND);
                case OR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.OR);
                case XOR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.XOR);
                default -> throw new JSCompilerException("Unknown assignment operator: " + operator);
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.INSERT3);
        compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
    }

    void compileLogicalAssignment(AssignmentExpression assignExpr) {
        Expression left = assignExpr.left();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();

        int depthLvalue;
        if (left instanceof Identifier) {
            depthLvalue = 0;
        } else if (left instanceof MemberExpression memberExpr) {
            depthLvalue = compilerContext.isSuperMemberExpression(memberExpr) ? 3 : (memberExpr.computed() ? 2 : 1);
        } else {
            throw new JSCompilerException("Invalid left-hand side in logical assignment");
        }

        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = compilerContext.findLocalInScopes(name);
            if (localIndex != null) {
                if (compilerContext.tdzLocals.contains(name)) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
                }
            } else {
                Integer capturedIndex = compilerContext.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(4);
                compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                compilerContext.emitter.emitOpcode(Opcode.DUP3);
                compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
            } else {
                owner.compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    owner.compileExpression(memberExpr.property());
                    compilerContext.emitter.emitOpcode(Opcode.DUP2);
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else if (memberExpr.property() instanceof Identifier propId) {
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                }
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);

        int jumpToCleanup;
        if (operator == AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN) {
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToCleanup = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        } else if (operator == AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN) {
            jumpToCleanup = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        } else {
            jumpToCleanup = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        }

        compilerContext.emitter.emitOpcode(Opcode.DROP);
        owner.compileExpression(assignExpr.right());

        switch (depthLvalue) {
            case 0 -> {
            }
            case 1 -> compilerContext.emitter.emitOpcode(Opcode.SWAP);
            case 2 -> {
                // Stack: [obj, prop, value] — already in QuickJS order for PUT_ARRAY_EL
            }
            case 3 -> {
                compilerContext.emitter.emitOpcode(Opcode.INSERT4);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
            default -> throw new JSCompilerException("Invalid depth for logical assignment");
        }

        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = compilerContext.findLocalInScopes(name);
            if (localIndex != null) {
                if (compilerContext.isLocalBindingConst(name)) {
                    emitConstAssignmentErrorForLocal(name, localIndex);
                } else if (compilerContext.tdzLocals.contains(name)) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_CHECK, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOCAL, localIndex);
                }
            } else {
                Integer capturedIndex = compilerContext.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    if (compilerContext.isCapturedBindingConst(name)) {
                        emitConstAssignmentErrorForCaptured(name, capturedIndex);
                    } else {
                        compilerContext.emitter.emitOpcodeU16(Opcode.SET_VAR_REF, capturedIndex);
                    }
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
            } else if (memberExpr.computed()) {
                compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
            }
        }

        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);
        compilerContext.emitter.patchJump(jumpToCleanup, compilerContext.emitter.currentOffset());

        for (int i = 0; i < depthLvalue; i++) {
            compilerContext.emitter.emitOpcode(Opcode.NIP);
        }

        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
    }

    private void emitConstAssignmentError(String name) {
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name);
        compilerContext.emitter.emitU8(0);
    }

    private void emitConstAssignmentErrorForCaptured(String name, int capturedIndex) {
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitConstAssignmentError(name);
    }

    private void emitConstAssignmentErrorForLocal(String name, int localIndex) {
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitConstAssignmentError(name);
    }

    private void emitDirectIdentifierReference(String name) {
        Integer localIndex = compilerContext.findLocalInScopes(name);
        if (localIndex != null) {
            emitScopedReference(Opcode.MAKE_LOC_REF, name, localIndex);
            return;
        }

        Integer capturedIndex = compilerContext.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            emitScopedReference(Opcode.MAKE_VAR_REF_REF, name, capturedIndex);
            return;
        }

        compilerContext.emitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF, name);
    }

    private void emitIdentifierReference(String name) {
        if (compilerContext.hasActiveWithObject() || !compilerContext.inheritedWithObjectBindingNames.isEmpty()) {
            emitWithAwareIdentifierReference(name);
        } else {
            emitDirectIdentifierReference(name);
        }
    }

    private void emitScopedReference(Opcode makeReferenceOpcode, String name, int referenceIndex) {
        compilerContext.emitter.emitOpcode(makeReferenceOpcode);
        compilerContext.emitter.emitAtom(name);
        compilerContext.emitter.emitU16(referenceIndex);
    }

    private void emitWithAwareIdentifierReference(String name) {
        List<Integer> jumpToResolvedOffsets = new ArrayList<>();

        List<Integer> withObjectLocals = compilerContext.getActiveWithObjectLocals();
        for (int withObjectLocalIndex : withObjectLocals) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOCAL, withObjectLocalIndex);
            emitWithCandidateReference(name, jumpToResolvedOffsets);
        }

        for (String withBindingName : compilerContext.inheritedWithObjectBindingNames) {
            Integer withLocalIndex = compilerContext.findLocalInScopes(withBindingName);
            if (withLocalIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOCAL, withLocalIndex);
                emitWithCandidateReference(name, jumpToResolvedOffsets);
                continue;
            }
            Integer withCapturedIndex = compilerContext.resolveCapturedBindingIndex(withBindingName);
            if (withCapturedIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, withCapturedIndex);
                emitWithCandidateReference(name, jumpToResolvedOffsets);
            }
        }

        emitDirectIdentifierReference(name);
        int resolveEndOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolvedOffsets) {
            compilerContext.emitter.patchJump(jumpOffset, resolveEndOffset);
        }
    }

    private void emitWithCandidateReference(String name, List<Integer> jumpToResolvedOffsets) {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);
        int jumpToCandidateFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToResolveWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToCandidateFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        jumpToResolvedOffsets.add(compilerContext.emitter.emitJump(Opcode.GOTO));

        compilerContext.emitter.patchJump(jumpToResolveWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        jumpToResolvedOffsets.add(compilerContext.emitter.emitJump(Opcode.GOTO));

        int candidateFallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToCandidateFallback, candidateFallbackOffset);
        compilerContext.emitter.patchJump(jumpToCandidateFallbackWhenBlocked, candidateFallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }
}
