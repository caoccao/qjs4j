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
    private AssignmentExpressionCompiler() {
    }

    static void compile(CompilerContext compilerContext, AssignmentExpression assignExpr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        Expression left = assignExpr.getLeft();
        AssignmentOperator operator = assignExpr.getOperator();

        if (operator == AssignmentOperator.LOGICAL_AND_ASSIGN || operator == AssignmentOperator.LOGICAL_OR_ASSIGN
                || operator == AssignmentOperator.NULLISH_ASSIGN) {
            compileLogicalAssignment(compilerContext, assignExpr);
            return;
        }

        if (left instanceof CallExpression) {
            compilerContext.compileExpression(left);
            emitter.emitOpcode(Opcode.DROP);
            emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            emitter.emitU8(5);
            return;
        }

        if (left instanceof Identifier identifier) {
            compileIdentifierAssignmentExpression(compilerContext, assignExpr, identifier);
            return;
        }

        if (left instanceof MemberExpression member) {
            compileMemberAssignment(compilerContext, member, operator, assignExpr.getRight());
            return;
        }
        compilerContext.compileExpression(assignExpr.getRight());
        if (operator != AssignmentOperator.ASSIGN) {
            emitCompoundOperator(compilerContext, operator);
        }
        if (left instanceof ArrayExpression arrayExpr) {
            emitter.emitOpcode(Opcode.DUP);
            ArrayExpressionDestructuringAssignmentCompiler.compile(compilerContext, arrayExpr);
        } else if (left instanceof ObjectExpression objExpr) {
            emitter.emitOpcode(Opcode.DUP);
            ObjectExpressionDestructuringAssignmentCompiler.compile(compilerContext, objExpr);
        }
    }

    private static void compileAssignmentValue(CompilerContext compilerContext, AssignmentExpression assignExpr,
            String name) {
        AssignmentOperator operator = assignExpr.getOperator();
        if (operator == AssignmentOperator.ASSIGN && assignExpr.isLhsIdentifierRef()
                && assignExpr.getRight() instanceof ClassExpression classExpr && classExpr.getId() == null) {
            compilerContext.inferredClassName = name;
        }
        compilerContext.compileExpression(assignExpr.getRight());
        compilerContext.inferredClassName = null;
        finishAssignmentValue(compilerContext, assignExpr, name);
    }

    /**
     * Assign to a local or captured binding directly, as QuickJS's {@code optimize_scope_make_ref} rewrites
     * {@code scope_make_ref ... put_ref_value} into {@code get_loc ... set_loc}.
     * <p>
     * Every assignment to a local used to build a reference object — a {@code JSObject} with an accessor property
     * backed by two new native functions — and go through {@code GET_REF_VALUE}/{@code PUT_REF_VALUE}. Without a direct
     * {@code eval} or a {@code with} in scope, a binding the compiler resolved to a slot cannot be redirected while the
     * right-hand side runs, so the slot is read and written in place; the {@code _CHECK} forms keep the temporal dead
     * zone: a compound assignment fails reading, a plain one fails writing, after the right-hand side, as the
     * specification orders it. (A sloppy direct eval in the right-hand side can declare a nearer binding, and the
     * assignment must still go to the one resolved first, so code that may contain one keeps references.)
     * <p>
     * Stack: value
     */
    private static void compileDirectBindingAssignment(CompilerContext compilerContext, AssignmentExpression assignExpr,
            String name, Integer localIndex, Integer capturedIndex) {
        BytecodeEmitter emitter = compilerContext.emitter;
        AssignmentOperator operator = assignExpr.getOperator();
        if (operator != AssignmentOperator.ASSIGN) {
            if (localIndex != null) {
                emitter.emitIntOperand(Opcode.GET_LOC_CHECK, localIndex);
            } else {
                emitter.emitIntOperand(Opcode.GET_VAR_REF_CHECK, capturedIndex);
            }
        }
        compileAssignmentValue(compilerContext, assignExpr, name);
        if (localIndex != null) {
            emitter.emitIntOperand(Opcode.SET_LOC_CHECK, localIndex);
        } else {
            emitter.emitOpcode(Opcode.DUP);
            emitter.emitIntOperand(Opcode.PUT_VAR_REF_CHECK, capturedIndex);
        }
    }

    private static void compileIdentifierAssignmentExpression(CompilerContext compilerContext,
            AssignmentExpression assignExpr, Identifier identifier) {
        BytecodeEmitter emitter = compilerContext.emitter;
        String name = identifier.getName();
        AssignmentOperator operator = assignExpr.getOperator();
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
        Integer capturedIndex = localIndex == null
                ? compilerContext.captureResolver.resolveCapturedBindingIndex(name)
                : null;
        boolean isConstLocalBinding = localIndex != null && compilerContext.scopeManager.isLocalBindingConst(name);
        boolean isConstCapturedBinding = capturedIndex != null
                && compilerContext.captureResolver.isCapturedBindingImmutable(name);

        boolean isFunctionNameLocal = localIndex != null
                && compilerContext.scopeManager.isLocalBindingFunctionName(name);
        boolean isFunctionNameCaptured = capturedIndex != null
                && compilerContext.captureResolver.isCapturedBindingFunctionName(name);
        if (isFunctionNameLocal || isFunctionNameCaptured) {
            if (operator != AssignmentOperator.ASSIGN) {
                if (isFunctionNameLocal) {
                    emitter.emitGetLocal(localIndex);
                } else {
                    emitter.emitIntOperand(Opcode.GET_VAR_REF, capturedIndex);
                }
            }
            compilerContext.compileExpression(assignExpr.getRight());
            finishAssignmentValue(compilerContext, assignExpr, name);
            return;
        }

        if (isConstLocalBinding || isConstCapturedBinding) {
            if (operator != AssignmentOperator.ASSIGN) {
                if (localIndex != null) {
                    emitter.emitIntOperand(Opcode.GET_LOC_CHECK, localIndex);
                } else {
                    emitter.emitIntOperand(Opcode.GET_VAR_REF_CHECK, capturedIndex);
                }
            }

            compilerContext.compileExpression(assignExpr.getRight());

            if (operator != AssignmentOperator.ASSIGN) {
                emitCompoundOperator(compilerContext, operator);
            }

            if (localIndex != null) {
                EmitHelpers.emitConstAssignmentErrorForLocal(compilerContext, name, localIndex);
            } else {
                EmitHelpers.emitConstAssignmentErrorForCaptured(compilerContext, name, capturedIndex);
            }
            return;
        }

        if ((localIndex != null || capturedIndex != null) && !compilerContext.mayContainDirectEval
                && !compilerContext.withObjectManager.hasActiveWithObject()
                && compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            compileDirectBindingAssignment(compilerContext, assignExpr, name, localIndex, capturedIndex);
            return;
        }

        emitIdentifierReference(compilerContext, name);
        if (operator != AssignmentOperator.ASSIGN) {
            emitter.emitOpcode(Opcode.GET_REF_VALUE);
        }

        compileAssignmentValue(compilerContext, assignExpr, name);
        emitter.emitOpcode(Opcode.INSERT3);
        emitter.emitOpcode(Opcode.PUT_REF_VALUE);
    }

    private static void compileLogicalAssignment(CompilerContext compilerContext, AssignmentExpression assignExpr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        Expression left = assignExpr.getLeft();
        AssignmentOperator operator = assignExpr.getOperator();
        boolean privateMemberAssignment = false;

        int depthLvalue;
        if (left instanceof Identifier id) {
            depthLvalue = 0;
            String name = id.getName();
            Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
            if (localIndex != null) {
                if (compilerContext.tdzLocals.contains(name)) {
                    emitter.emitIntOperand(Opcode.GET_LOC_CHECK, localIndex);
                } else {
                    emitter.emitGetLocal(localIndex);
                }
            } else {
                Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    emitter.emitIntOperand(Opcode.GET_VAR_REF_CHECK, capturedIndex);
                } else {
                    emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression member) {
            depthLvalue = emitMemberReadForAssignment(compilerContext, member, true);
            privateMemberAssignment = !member.getObject().isSuperIdentifier() && !member.isComputed()
                    && member.getProperty() instanceof PrivateIdentifier;
        } else {
            throw new JSCompilerException("Invalid left-hand side in logical assignment", assignExpr);
        }

        emitter.emitOpcode(Opcode.DUP);

        int jumpToCleanup;
        if (operator == AssignmentOperator.NULLISH_ASSIGN) {
            emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToCleanup = emitter.emitJump(Opcode.IF_FALSE);
        } else if (operator == AssignmentOperator.LOGICAL_OR_ASSIGN) {
            jumpToCleanup = emitter.emitJump(Opcode.IF_TRUE);
        } else {
            jumpToCleanup = emitter.emitJump(Opcode.IF_FALSE);
        }

        emitter.emitOpcode(Opcode.DROP);
        compilerContext.compileExpression(assignExpr.getRight());
        if (left instanceof Identifier identifier && assignExpr.isLhsIdentifierRef()
                && assignExpr.getRight().isAnonymousFunction()) {
            emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.getName());
        }

        if (depthLvalue == 1) {
            emitter.emitOpcode(Opcode.SWAP);
        }
        if (privateMemberAssignment) {
            emitter.emitOpcode(Opcode.SWAP);
        }

        if (left instanceof Identifier id) {
            String name = id.getName();
            Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
            if (localIndex != null) {
                if (compilerContext.scopeManager.isLocalBindingFunctionName(name)) {
                    if (compilerContext.strictMode) {
                        EmitHelpers.emitConstAssignmentErrorForLocal(compilerContext, name, localIndex);
                    }
                } else if (compilerContext.scopeManager.isLocalBindingConst(name)) {
                    EmitHelpers.emitConstAssignmentErrorForLocal(compilerContext, name, localIndex);
                } else if (compilerContext.tdzLocals.contains(name)) {
                    emitter.emitIntOperand(Opcode.SET_LOC_CHECK, localIndex);
                } else {
                    emitter.emitIntOperand(Opcode.SET_LOC, localIndex);
                }
            } else {
                Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    if (compilerContext.captureResolver.isCapturedBindingFunctionName(name)) {
                        if (compilerContext.strictMode) {
                            EmitHelpers.emitConstAssignmentErrorForCaptured(compilerContext, name, capturedIndex);
                        }
                    } else if (compilerContext.captureResolver.isCapturedBindingImmutable(name)) {
                        EmitHelpers.emitConstAssignmentErrorForCaptured(compilerContext, name, capturedIndex);
                    } else {
                        emitter.emitOpcode(Opcode.DUP);
                        emitter.emitIntOperand(Opcode.PUT_VAR_REF_CHECK, capturedIndex);
                    }
                } else {
                    emitter.emitOpcode(Opcode.DUP);
                    emitter.emitOpcodeAtom(Opcode.PUT_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
            } else if (memberExpr.isComputed()) {
                emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
            } else if (privateMemberAssignment) {
                emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
            }
        }

        int jumpToEnd = emitter.emitJump(Opcode.GOTO);
        emitter.patchJumpToHere(jumpToCleanup);

        for (int i = 0; i < depthLvalue; i++) {
            emitter.emitOpcode(Opcode.NIP);
        }

        emitter.patchJumpToHere(jumpToEnd);
    }

    private static void compileMemberAssignment(CompilerContext compilerContext, MemberExpression member,
            AssignmentOperator operator, Expression right) {
        BytecodeEmitter emitter = compilerContext.emitter;
        boolean compound = operator != AssignmentOperator.ASSIGN;
        boolean superProperty = member.getObject().isSuperIdentifier();
        if (compound) {
            emitMemberReadForAssignment(compilerContext, member, false);
        } else if (superProperty) {
            EmitHelpers.emitSuperPropertyReference(compilerContext, member);
        } else if (member.isComputed()) {
            compilerContext.compileExpression(member.getObject());
            compilerContext.compileExpression(member.getProperty());
        }
        compilerContext.compileExpression(right);
        if (compound) {
            emitCompoundOperator(compilerContext, operator);
        } else if (!superProperty && !member.isComputed()) {
            compilerContext.compileExpression(member.getObject());
        }
        if (superProperty) {
            emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
        } else if (member.isComputed()) {
            emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
        } else if (member.getProperty() instanceof PrivateIdentifier id) {
            JSSymbol symbol = compilerContext.privateSymbol(id);
            if (symbol == null) {
                emitter.emitOpcode(Opcode.DROP);
            } else {
                if (!compound) {
                    emitter.emitOpcode(Opcode.SWAP);
                }
                emitter.emitPushConstant(symbol);
                emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
            }
        } else if (member.getProperty() instanceof Identifier id) {
            if (compound) {
                emitter.emitOpcode(Opcode.SWAP);
            }
            emitter.emitOpcodeAtom(Opcode.PUT_FIELD, id.getName());
        }
    }

    /**
     * Apply a compound operator to the old value and the right operand. Logical assignments never get here: compile()
     * hands them to compileLogicalAssignment first.
     */
    private static void emitCompoundOperator(CompilerContext compilerContext, AssignmentOperator operator) {
        compilerContext.emitter.emitOpcode(operator.getOpcode());
    }

    private static void emitDirectIdentifierReference(CompilerContext compilerContext, String name) {
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
        if (localIndex != null) {
            emitScopedReference(compilerContext, Opcode.MAKE_LOC_REF, name, localIndex);
            return;
        }

        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            emitScopedReference(compilerContext, Opcode.MAKE_VAR_REF_REF, name, capturedIndex);
            return;
        }

        compilerContext.emitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF, name);
    }

    static void emitIdentifierReference(CompilerContext compilerContext, String name) {
        if (compilerContext.withObjectManager.hasActiveWithObject()
                || !compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            emitWithAwareIdentifierReference(compilerContext, name);
        } else {
            emitDirectIdentifierReference(compilerContext, name);
        }
    }

    /** Leave the reference below its value and return the number of saved stack entries. */
    private static int emitMemberReadForAssignment(CompilerContext compilerContext, MemberExpression member,
            boolean logical) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (member.getObject().isSuperIdentifier()) {
            EmitHelpers.emitSuperPropertyReference(compilerContext, member);
            emitter.emitOpcode(Opcode.TO_PROPKEY);
            emitter.emitOpcode(Opcode.DUP3);
            emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
            return 3;
        }
        compilerContext.compileExpression(member.getObject());
        if (member.isComputed()) {
            compilerContext.compileExpression(member.getProperty());
            if (logical) {
                emitter.emitOpcode(Opcode.TO_PROPKEY);
                emitter.emitOpcode(Opcode.DUP2);
                emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else {
                emitter.emitOpcode(Opcode.GET_ARRAY_EL3);
            }
            return 2;
        }
        if (member.getProperty() instanceof PrivateIdentifier id) {
            JSSymbol symbol = compilerContext.privateSymbol(id);
            if (logical) {
                if (symbol == null) {
                    throw new JSCompilerException("undefined private field '#" + id.getName() + "'", id);
                }
                emitter.emitPushConstant(symbol);
                emitter.emitOpcode(Opcode.DUP2);
            } else if (symbol != null) {
                emitter.emitOpcode(Opcode.DUP);
                emitter.emitPushConstant(symbol);
            } else {
                emitter.emitOpcode(Opcode.UNDEFINED);
                return 1;
            }
            emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            return logical ? 2 : 1;
        }
        if (member.getProperty() instanceof Identifier id) {
            emitter.emitOpcodeAtom(Opcode.GET_FIELD2, id.getName());
        }
        return 1;
    }

    private static void emitScopedReference(CompilerContext compilerContext, Opcode makeReferenceOpcode, String name,
            int referenceIndex) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitOpcode(makeReferenceOpcode);
        emitter.emitAtom(name);
        emitter.emitU16(referenceIndex);
    }

    private static void emitWithAwareIdentifierReference(CompilerContext compilerContext, String name) {
        BytecodeEmitter emitter = compilerContext.emitter;
        List<Integer> jumpToResolvedOffsets = new ArrayList<>();

        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        for (int withObjectLocalIndex : withObjectLocals) {
            emitter.emitGetLocal(withObjectLocalIndex);
            emitWithCandidateReference(compilerContext, name, jumpToResolvedOffsets);
        }

        for (String withBindingName : compilerContext.withObjectManager.getInheritedBindingNames()) {
            if (IdentifierCompiler.emitInheritedWithObject(compilerContext, withBindingName)) {
                emitWithCandidateReference(compilerContext, name, jumpToResolvedOffsets);
            }
        }

        emitDirectIdentifierReference(compilerContext, name);
        int resolveEndOffset = emitter.currentOffset();
        emitter.patchJumps(jumpToResolvedOffsets, resolveEndOffset);
    }

    private static void emitWithCandidateReference(CompilerContext compilerContext, String name,
            List<Integer> resolved) {
        BytecodeEmitter emitter = compilerContext.emitter;
        var fallback = WithBindingCompiler.emitHasBinding(compilerContext, name);
        emitter.emitPushConstant(new JSString(name));
        resolved.add(emitter.emitJump(Opcode.GOTO));
        fallback.begin(emitter);
    }

    private static void finishAssignmentValue(CompilerContext compilerContext, AssignmentExpression assignExpr,
            String name) {
        AssignmentOperator operator = assignExpr.getOperator();
        if (operator == AssignmentOperator.ASSIGN && assignExpr.isLhsIdentifierRef()
                && assignExpr.getRight().isAnonymousFunction()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, name);
        }
        if (operator != AssignmentOperator.ASSIGN) {
            emitCompoundOperator(compilerContext, operator);
        }
    }

}
