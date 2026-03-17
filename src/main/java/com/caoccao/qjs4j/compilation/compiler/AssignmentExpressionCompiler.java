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

/**
 * Handles compilation of assignment expressions.
 */
final class AssignmentExpressionCompiler {
    private final CompilerContext compilerContext;

    AssignmentExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(AssignmentExpression assignExpr) {
        Expression left = assignExpr.getLeft();
        AssignmentOperator operator = assignExpr.getOperator();

        if (operator == AssignmentOperator.LOGICAL_AND_ASSIGN
                || operator == AssignmentOperator.LOGICAL_OR_ASSIGN
                || operator == AssignmentOperator.NULLISH_ASSIGN) {
            compileLogicalAssignment(assignExpr);
            return;
        }

        if (left instanceof CallExpression) {
            compilerContext.expressionCompiler.compileExpression(left);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            compilerContext.emitter.emitU8(5);
            return;
        }

        if (left instanceof Identifier identifier) {
            compileIdentifierAssignmentExpression(assignExpr, identifier);
            return;
        }

        if (operator != AssignmentOperator.ASSIGN) {
            if (left instanceof MemberExpression memberExpr) {
                if (memberExpr.getObject().isSuperIdentifier()) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4);
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                    compilerContext.emitter.emitOpcode(Opcode.DUP3);
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
                } else {
                    compilerContext.expressionCompiler.compileExpression(memberExpr.getObject());
                    if (memberExpr.isComputed()) {
                        compilerContext.expressionCompiler.compileExpression(memberExpr.getProperty());
                        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL3);
                    } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
                        String fieldName = privateId.getName();
                        JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                        if (symbol != null) {
                            compilerContext.emitter.emitOpcode(Opcode.DUP);
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                        } else {
                            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                        }
                    } else if (memberExpr.getProperty() instanceof Identifier propId) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
                    }
                }
            }

            compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());

            switch (operator) {
                case PLUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.ADD);
                case MINUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SUB);
                case MUL_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MUL);
                case DIV_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.DIV);
                case MOD_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MOD);
                case EXP_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.POW);
                case LSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHL);
                case RSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SAR);
                case URSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHR);
                case AND_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.AND);
                case OR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.OR);
                case XOR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.XOR);
                default -> throw new JSCompilerException("Unknown assignment operator: " + operator);
            }
        } else {
            if (left instanceof MemberExpression memberExpr) {
                if (memberExpr.getObject().isSuperIdentifier()) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4);
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                    compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());
                    compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                    return;
                }
                if (memberExpr.isComputed()) {
                    compilerContext.expressionCompiler.compileExpression(memberExpr.getObject());
                    compilerContext.expressionCompiler.compileExpression(memberExpr.getProperty());
                    compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    return;
                }
            }
            compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());
        }

        if (left instanceof MemberExpression memberExpr) {
            if (operator == AssignmentOperator.ASSIGN) {
                compilerContext.expressionCompiler.compileExpression(memberExpr.getObject());
                if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
                    String fieldName = privateId.getName();
                    JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        compilerContext.emitter.emitOpcode(Opcode.SWAP);
                        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    } else {
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                } else if (memberExpr.getProperty() instanceof Identifier propId) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
                }
            } else {
                if (memberExpr.getObject().isSuperIdentifier()) {
                    compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                } else if (memberExpr.isComputed()) {
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
                    String fieldName = privateId.getName();
                    JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    } else {
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                } else if (memberExpr.getProperty() instanceof Identifier propId) {
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
                }
            }
        } else if (left instanceof ArrayExpression arrayExpr) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.arrayExpressionDestructuringAssignmentCompiler.compile(arrayExpr);
        } else if (left instanceof ObjectExpression objExpr) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.patternCompiler.compileObjectDestructuringAssignment(objExpr);
        }
    }

    private void compileIdentifierAssignmentExpression(AssignmentExpression assignExpr, Identifier identifier) {
        String name = identifier.getName();
        AssignmentOperator operator = assignExpr.getOperator();
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
        Integer capturedIndex = localIndex == null ? compilerContext.captureResolver.resolveCapturedBindingIndex(name) : null;
        boolean isConstLocalBinding = localIndex != null && compilerContext.scopeManager.isLocalBindingConst(name);
        boolean isConstCapturedBinding = capturedIndex != null && compilerContext.captureResolver.isCapturedBindingImmutable(name);

        boolean isFunctionNameLocal = localIndex != null && compilerContext.scopeManager.isLocalBindingFunctionName(name);
        boolean isFunctionNameCaptured = capturedIndex != null && compilerContext.captureResolver.isCapturedBindingFunctionName(name);
        if (isFunctionNameLocal || isFunctionNameCaptured) {
            if (operator != AssignmentOperator.ASSIGN) {
                if (isFunctionNameLocal) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                }
            }
            compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());
            if (operator == AssignmentOperator.ASSIGN
                    && assignExpr.isLhsIdentifierRef()
                    && assignExpr.getRight().isAnonymousFunction()) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, name);
            }
            if (operator != AssignmentOperator.ASSIGN) {
                switch (operator) {
                    case PLUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.ADD);
                    case MINUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SUB);
                    case MUL_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MUL);
                    case DIV_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.DIV);
                    case MOD_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MOD);
                    case EXP_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.POW);
                    case LSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHL);
                    case RSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SAR);
                    case URSHIFT_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SHR);
                    case AND_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.AND);
                    case OR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.OR);
                    case XOR_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.XOR);
                    default -> throw new JSCompilerException("Unknown assignment operator: " + operator);
                }
            }
            return;
        }

        if (isConstLocalBinding || isConstCapturedBinding) {
            if (operator != AssignmentOperator.ASSIGN) {
                if (localIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
                }
            }

            compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());

            if (operator != AssignmentOperator.ASSIGN) {
                switch (operator) {
                    case PLUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.ADD);
                    case MINUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SUB);
                    case MUL_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MUL);
                    case DIV_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.DIV);
                    case MOD_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MOD);
                    case EXP_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.POW);
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
        if (operator != AssignmentOperator.ASSIGN) {
            compilerContext.emitter.emitOpcode(Opcode.GET_REF_VALUE);
        }

        if (operator == AssignmentOperator.ASSIGN
                && assignExpr.isLhsIdentifierRef()
                && assignExpr.getRight() instanceof ClassExpression classExpr
                && classExpr.getId() == null) {
            compilerContext.inferredClassName = name;
        }
        compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());
        compilerContext.inferredClassName = null;

        if (operator == AssignmentOperator.ASSIGN
                && assignExpr.isLhsIdentifierRef()
                && assignExpr.getRight().isAnonymousFunction()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, name);
        }

        if (operator != AssignmentOperator.ASSIGN) {
            switch (operator) {
                case PLUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.ADD);
                case MINUS_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.SUB);
                case MUL_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MUL);
                case DIV_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.DIV);
                case MOD_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.MOD);
                case EXP_ASSIGN -> compilerContext.emitter.emitOpcode(Opcode.POW);
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

    private void compileLogicalAssignment(AssignmentExpression assignExpr) {
        Expression left = assignExpr.getLeft();
        AssignmentOperator operator = assignExpr.getOperator();
        boolean privateMemberAssignment = false;

        int depthLvalue;
        if (left instanceof Identifier) {
            depthLvalue = 0;
        } else if (left instanceof MemberExpression memberExpr) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                depthLvalue = 3;
            } else if (memberExpr.isComputed()) {
                depthLvalue = 2;
            } else if (memberExpr.getProperty() instanceof PrivateIdentifier) {
                depthLvalue = 2;
                privateMemberAssignment = true;
            } else {
                depthLvalue = 1;
            }
        } else {
            throw new JSCompilerException("Invalid left-hand side in logical assignment");
        }

        if (left instanceof Identifier id) {
            String name = id.getName();
            Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
            if (localIndex != null) {
                if (compilerContext.tdzLocals.contains(name)) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
                }
            } else {
                Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(4);
                compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                compilerContext.emitter.emitOpcode(Opcode.DUP3);
                compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
            } else {
                compilerContext.expressionCompiler.compileExpression(memberExpr.getObject());
                if (memberExpr.isComputed()) {
                    compilerContext.expressionCompiler.compileExpression(memberExpr.getProperty());
                    compilerContext.emitter.emitOpcode(Opcode.DUP2);
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
                    String fieldName = privateIdentifier.getName();
                    JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                    if (symbol == null) {
                        throw new JSCompilerException("undefined private field '#" + fieldName + "'");
                    }
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.DUP2);
                    compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else if (memberExpr.getProperty() instanceof Identifier propId) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
                }
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);

        int jumpToCleanup;
        if (operator == AssignmentOperator.NULLISH_ASSIGN) {
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToCleanup = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        } else if (operator == AssignmentOperator.LOGICAL_OR_ASSIGN) {
            jumpToCleanup = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        } else {
            jumpToCleanup = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        }

        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.expressionCompiler.compileExpression(assignExpr.getRight());
        if (left instanceof Identifier identifier
                && assignExpr.getRight().isAnonymousFunction()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.getName());
        }

        switch (depthLvalue) {
            case 0 -> {
            }
            case 1 -> compilerContext.emitter.emitOpcode(Opcode.SWAP);
            case 2 -> {
            }
            case 3 -> {
            }
            default -> throw new JSCompilerException("Invalid depth for logical assignment");
        }
        if (privateMemberAssignment) {
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }

        if (left instanceof Identifier id) {
            String name = id.getName();
            Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
            if (localIndex != null) {
                if (compilerContext.scopeManager.isLocalBindingConst(name)) {
                    emitConstAssignmentErrorForLocal(name, localIndex);
                } else if (compilerContext.tdzLocals.contains(name)) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_CHECK, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC, localIndex);
                }
            } else {
                Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    if (compilerContext.captureResolver.isCapturedBindingImmutable(name)) {
                        emitConstAssignmentErrorForCaptured(name, capturedIndex);
                    } else {
                        compilerContext.emitter.emitOpcode(Opcode.DUP);
                        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_VAR_REF_CHECK, capturedIndex);
                    }
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
            } else if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
            } else if (privateMemberAssignment) {
                compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
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
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
        if (localIndex != null) {
            emitScopedReference(Opcode.MAKE_LOC_REF, name, localIndex);
            return;
        }

        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            emitScopedReference(Opcode.MAKE_VAR_REF_REF, name, capturedIndex);
            return;
        }

        compilerContext.emitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF, name);
    }

    void emitIdentifierReference(String name) {
        if (compilerContext.withObjectManager.hasActiveWithObject()
                || !compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
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

        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        for (int withObjectLocalIndex : withObjectLocals) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
            emitWithCandidateReference(name, jumpToResolvedOffsets);
        }

        for (String withBindingName : compilerContext.withObjectManager.getInheritedBindingNames()) {
            Integer withLocalIndex = compilerContext.scopeManager.findLocalInScopes(withBindingName);
            if (withLocalIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withLocalIndex);
                emitWithCandidateReference(name, jumpToResolvedOffsets);
                continue;
            }
            Integer withCapturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(withBindingName);
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
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToCandidateFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        jumpToResolvedOffsets.add(compilerContext.emitter.emitJump(Opcode.GOTO));

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        jumpToResolvedOffsets.add(compilerContext.emitter.emitJump(Opcode.GOTO));

        int candidateFallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToCandidateFallback, candidateFallbackOffset);
        compilerContext.emitter.patchJump(jumpToCandidateFallbackWhenBlocked, candidateFallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }

    private int[] emitWithUnscopablesSkipJumps() {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToResolveWithoutUnscopablesOnNullish = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("object"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToCheckBlockedWhenObject = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("function"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToResolveWithoutUnscopablesOnPrimitive = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.patchJump(jumpToCheckBlockedWhenObject, compilerContext.emitter.currentOffset());
        return new int[]{jumpToResolveWithoutUnscopablesOnNullish, jumpToResolveWithoutUnscopablesOnPrimitive};
    }
}
