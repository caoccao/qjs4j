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
import com.caoccao.qjs4j.core.JSArguments;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles compilation of unary expressions including delete, typeof,
 * increment/decrement, and standard unary operators.
 */
final class UnaryExpressionCompiler extends AstNodeCompiler<UnaryExpression> {
    UnaryExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(UnaryExpression unaryExpr) {
        // DELETE operator needs special handling - it doesn't evaluate the operand,
        // but instead emits object and property separately
        if (unaryExpr.getOperator() == UnaryExpression.UnaryOperator.DELETE) {
            Expression operand = unaryExpr.getOperand();

            if (operand instanceof MemberExpression memberExpr) {
                if (memberExpr.getObject().isSuperIdentifier()) {
                    // delete super.prop / delete super[expr]
                    // Per spec, the property expression must be evaluated for side effects,
                    // then a ReferenceError is thrown. Following QuickJS: emit super setup +
                    // property key evaluation, then THROW_ERROR instead of GET_SUPER_VALUE.
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                    compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "");
                    compilerContext.emitter.emitU8(3); // JS_THROW_ERROR_DELETE_SUPER
                } else {
                    if (memberExpr.isPartOfOptionalChain()) {
                        compileDeleteOptionalMemberExpression(memberExpr);
                        return;
                    }
                    // delete obj.prop or delete obj[expr]
                    compilerContext.expressionCompiler.compile(memberExpr.getObject());

                    if (memberExpr.isComputed()) {
                        // obj[expr]
                        compilerContext.expressionCompiler.compile(memberExpr.getProperty());
                    } else if (memberExpr.getProperty() instanceof Identifier propId) {
                        // obj.prop
                        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.getName()));
                    }

                    compilerContext.emitter.emitOpcode(Opcode.DELETE);
                }
            } else if (operand instanceof Identifier id) {
                // Match QuickJS scope_delete_var lowering:
                // - local/arg/closure/implicit arguments bindings => false
                // - unresolved/global binding => DELETE_VAR runtime check
                boolean isLocalBinding = compilerContext.scopeManager.findLocalInScopes(id.getName()) != null
                        || compilerContext.captureResolver.resolveCapturedBindingIndex(id.getName()) != null
                        || (JSArguments.NAME.equals(id.getName()) && !compilerContext.inGlobalScope)
                        || compilerContext.nonDeletableGlobalBindings.contains(id.getName());
                if (isLocalBinding) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
                } else {
                    List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
                    if (!withObjectLocals.isEmpty()) {
                        compilerContext.identifierCompiler.emitWithAwareDeleteIdentifier(id.getName(), withObjectLocals, 0);
                    } else if (!compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
                        compilerContext.identifierCompiler.emitInheritedWithAwareDeleteIdentifier(id.getName(), compilerContext.withObjectManager.getInheritedBindingNames(), 0);
                    } else {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, id.getName());
                    }
                }
            } else {
                // delete non-reference expression => evaluate for side effects, then true
                compilerContext.expressionCompiler.compile(operand);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.PUSH_TRUE);
            }
            return;
        }

        // INC and DEC operators - following QuickJS pattern:
        // 1. Compile get_lvalue (loads current value)
        // 2. Apply INC/DEC (prefix) or POST_INC/POST_DEC (postfix)
        // 3. Apply put_lvalue (stores with appropriate stack manipulation)
        if (unaryExpr.getOperator() == UnaryExpression.UnaryOperator.INC ||
                unaryExpr.getOperator() == UnaryExpression.UnaryOperator.DEC) {
            Expression operand = unaryExpr.getOperand();
            boolean isInc = unaryExpr.getOperator() == UnaryExpression.UnaryOperator.INC;
            boolean isPrefix = unaryExpr.isPrefix();

            if (operand instanceof Identifier id) {
                if (compilerContext.withObjectManager.hasActiveWithObject() || !compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
                    // Use reference semantics so with-scope resolution happens before local/captured fallback.
                    compilerContext.assignmentExpressionCompiler.emitIdentifierReference(id.getName());
                    compilerContext.emitter.emitOpcode(Opcode.GET_REF_VALUE);
                    compilerContext.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                            : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                    if (isPrefix) {
                        // obj prop new -> new obj prop new -> store -> new
                        compilerContext.emitter.emitOpcode(Opcode.INSERT3);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
                    } else {
                        // obj prop old new -> old obj prop new -> store -> old
                        compilerContext.emitter.emitOpcode(Opcode.PERM4);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
                    }
                    return;
                }

                Integer localIndex = compilerContext.scopeManager.findLocalInScopes(id.getName());
                if (localIndex != null) {
                    // Local binding.
                    compilerContext.expressionCompiler.compile(operand);
                    compilerContext.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                            : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                    if (compilerContext.scopeManager.isLocalBindingConst(id.getName())) {
                        emitConstAssignmentErrorForLocal(id.getName(), localIndex);
                    } else {
                        compilerContext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_LOC : Opcode.PUT_LOC, localIndex);
                    }
                    return;
                }

                Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(id.getName());
                if (capturedIndex != null) {
                    // Captured binding.
                    compilerContext.expressionCompiler.compile(operand);
                    compilerContext.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                            : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                    if (compilerContext.captureResolver.isCapturedBindingImmutable(id.getName())) {
                        emitConstAssignmentErrorForCaptured(id.getName(), capturedIndex);
                    } else {
                        compilerContext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_VAR_REF : Opcode.PUT_VAR_REF, capturedIndex);
                    }
                    return;
                }

                // Unresolved identifier: use reference semantics so strict errors and with-scopes are handled correctly.
                compilerContext.assignmentExpressionCompiler.emitIdentifierReference(id.getName());
                compilerContext.emitter.emitOpcode(Opcode.GET_REF_VALUE);
                compilerContext.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                        : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                if (isPrefix) {
                    // obj prop new -> new obj prop new -> store -> new
                    compilerContext.emitter.emitOpcode(Opcode.INSERT3);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
                } else {
                    // obj prop old new -> old obj prop new -> store -> old
                    compilerContext.emitter.emitOpcode(Opcode.PERM4);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
                }
            } else if (operand instanceof MemberExpression memberExpr) {
                if (memberExpr.getObject().isSuperIdentifier()) {
                    // Super property update follows super-reference semantics:
                    // [this, superObj, key] + GET_SUPER_VALUE -> old value -> update -> PUT_SUPER_VALUE
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                    if (memberExpr.isComputed()) {
                        // Per ES spec: computed key evaluated before GetSuperBase()
                        compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                        compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                        compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                        compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                        compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    } else {
                        compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                        compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                        compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                        compilerContext.emitHelpers.emitSuperPropertyKey(memberExpr);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                    compilerContext.emitter.emitOpcode(Opcode.DUP3);
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);

                    if (isPrefix) {
                        compilerContext.emitter.emitOpcode(isInc ? Opcode.INC : Opcode.DEC);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                    } else {
                        compilerContext.emitter.emitOpcode(isInc ? Opcode.POST_INC : Opcode.POST_DEC);
                        compilerContext.emitter.emitOpcode(Opcode.PERM5); // old this superObj key new
                        compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE); // old new
                        compilerContext.emitter.emitOpcode(Opcode.DROP); // old
                    }
                    return;
                }
                if (memberExpr.isComputed()) {
                    // Array element: obj[prop]
                    compilerContext.expressionCompiler.compile(memberExpr.getObject());
                    compilerContext.expressionCompiler.compile(memberExpr.getProperty());
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL3);

                    if (isPrefix) {
                        // Prefix: ++arr[i] - returns new value
                        compilerContext.emitter.emitOpcode(isInc ? Opcode.INC : Opcode.DEC);
                        // Stack: [obj, prop, new_val] — already in QuickJS order
                        compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else {
                        // Postfix: arr[i]++ - returns old value
                        compilerContext.emitter.emitOpcode(isInc ? Opcode.POST_INC : Opcode.POST_DEC); // obj prop old_val new_val
                        // PERM4 to rearrange: [obj, prop, old_val, new_val] -> [old_val, obj, prop, new_val]
                        compilerContext.emitter.emitOpcode(Opcode.PERM4); // old_val obj prop new_val
                        // PUT_ARRAY_EL leaves assigned value on stack, so drop it to preserve old value result.
                        compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL); // old_val new_val
                        compilerContext.emitter.emitOpcode(Opcode.DROP); // old_val
                    }
                } else {
                    // Object property: obj.prop or obj.#field
                    if (memberExpr.getProperty() instanceof Identifier propId) {
                        compilerContext.expressionCompiler.compile(memberExpr.getObject());

                        if (isPrefix) {
                            // Prefix: ++obj.prop - returns new value
                            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
                            compilerContext.emitter.emitOpcode(isInc ? Opcode.INC : Opcode.DEC);
                            // Stack: [obj, new_val] -> need [new_val, obj] for PUT_FIELD
                            compilerContext.emitter.emitOpcode(Opcode.SWAP);
                            // PUT_FIELD pops obj, peeks new_val, leaves [new_val]
                            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
                        } else {
                            // Postfix: obj.prop++ - returns old value
                            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName()); // obj old_val
                            compilerContext.emitter.emitOpcode(isInc ? Opcode.POST_INC : Opcode.POST_DEC); // obj old_val new_val
                            // Stack: [obj, old_val, new_val] - need [old_val, new_val, obj] for PUT_FIELD
                            // ROT3L: [old_val, new_val, obj]
                            compilerContext.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            // PUT_FIELD pops obj, peeks new_val, leaves [old_val, new_val]
                            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName()); // old_val new_val
                            compilerContext.emitter.emitOpcode(Opcode.DROP); // old_val
                        }
                    } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
                        // Private field: obj.#field
                        String fieldName = privateId.getName();
                        JSSymbol symbol = compilerContext.privateSymbols.get(fieldName);
                        if (symbol == null) {
                            throw new JSCompilerException("Private field not found: #" + fieldName);
                        }

                        compilerContext.expressionCompiler.compile(memberExpr.getObject());

                        if (isPrefix) {
                            // Prefix: ++obj.#field - returns new value
                            compilerContext.emitter.emitOpcode(Opcode.DUP); // obj obj
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            compilerContext.emitter.emitOpcode(isInc ? Opcode.INC : Opcode.DEC); // obj new_val
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // obj new_val symbol
                            compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // new_val
                        } else {
                            // Postfix: obj.#field++ - returns old value
                            compilerContext.emitter.emitOpcode(Opcode.DUP); // obj obj
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            compilerContext.emitter.emitOpcode(isInc ? Opcode.POST_INC : Opcode.POST_DEC); // obj old_val new_val
                            compilerContext.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // old_val new_val obj symbol
                            compilerContext.emitter.emitOpcode(Opcode.ROT3L); // old_val obj symbol new_val
                            compilerContext.emitter.emitOpcode(Opcode.SWAP); // old_val obj new_val symbol
                            compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // old_val
                        }
                    } else {
                        throw new JSCompilerException("Invalid member expression property for increment/decrement");
                    }
                }
            } else if (operand instanceof CallExpression) {
                // Annex B: CallExpression as increment/decrement target throws ReferenceError at runtime.
                compilerContext.expressionCompiler.compile(operand);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid increment/decrement operand");
                compilerContext.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE
            } else {
                throw new JSCompilerException("Invalid operand for increment/decrement operator");
            }
            return;
        }

        if (unaryExpr.getOperator() == UnaryExpression.UnaryOperator.TYPEOF
                && unaryExpr.getOperand() instanceof Identifier id) {
            String name = id.getName();
            if ("import.meta".equals(name) || "new.target".equals(name)) {
                compilerContext.expressionCompiler.compile(unaryExpr.getOperand());
                compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
                return;
            }
            if (JSKeyword.THIS.equals(name)) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
            } else if (JSArguments.NAME.equals(name)
                    && compilerContext.hasEnclosingArgumentsBinding
                    && compilerContext.scopeManager.findLocalInScopes(name) == null) {
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(0);
            } else {
                Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
                if (localIndex != null) {
                    // Use GET_LOC_CHECK for TDZ locals - typeof of an uninitialized
                    // lexical binding throws ReferenceError per ES spec
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
                        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR_UNDEF, name);
                    }
                }
            }
            compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
            return;
        }

        compilerContext.expressionCompiler.compile(unaryExpr.getOperand());

        Opcode op = switch (unaryExpr.getOperator()) {
            case BIT_NOT -> Opcode.NOT;
            case MINUS -> Opcode.NEG;
            case NOT -> Opcode.LNOT;
            case PLUS -> Opcode.PLUS;
            case TYPEOF -> Opcode.TYPEOF;
            case VOID -> {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                yield Opcode.UNDEFINED;
            }
            default -> throw new JSCompilerException("Unknown unary operator: " + unaryExpr.getOperator());
        };

        compilerContext.emitter.emitOpcode(op);
    }

    private void compileDeleteOptionalMemberExpression(MemberExpression memberExpression) {
        ArrayList<MemberExpression> memberChain = new ArrayList<>();
        Expression currentExpression = memberExpression;
        while (currentExpression instanceof MemberExpression currentMemberExpression) {
            memberChain.add(0, currentMemberExpression);
            if (currentMemberExpression.isOptional()) {
                break;
            }
            currentExpression = currentMemberExpression.getObject();
        }

        if (memberChain.isEmpty()) {
            compilerContext.expressionCompiler.compile(memberExpression);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.PUSH_TRUE);
            return;
        }

        MemberExpression optionalRootMemberExpression = memberChain.get(0);
        compilerContext.expressionCompiler.compile(optionalRootMemberExpression.getObject());
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToTrueResult = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        for (int chainIndex = 0; chainIndex < memberChain.size() - 1; chainIndex++) {
            emitMemberPropertyAccess(memberChain.get(chainIndex));
        }

        MemberExpression deleteTargetMemberExpression = memberChain.get(memberChain.size() - 1);
        if (deleteTargetMemberExpression.isComputed()) {
            compilerContext.expressionCompiler.compile(deleteTargetMemberExpression.getProperty());
        } else if (deleteTargetMemberExpression.getProperty() instanceof Identifier propertyIdentifier) {
            compilerContext.emitter.emitOpcodeConstant(
                    Opcode.PUSH_CONST,
                    new JSString(propertyIdentifier.getName()));
        } else if (deleteTargetMemberExpression.getProperty() instanceof PrivateIdentifier privateIdentifier) {
            throw new JSCompilerException("Unexpected private field '#" + privateIdentifier.getName() + "'");
        } else {
            throw new JSCompilerException("Invalid delete target");
        }
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        compilerContext.emitter.patchJump(jumpToTrueResult, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.PUSH_TRUE);

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

    private void emitMemberPropertyAccess(MemberExpression memberExpression) {
        if (memberExpression.isComputed()) {
            compilerContext.expressionCompiler.compile(memberExpression.getProperty());
            compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            return;
        }
        if (memberExpression.getProperty() instanceof Identifier propertyIdentifier) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propertyIdentifier.getName());
            return;
        }
        if (memberExpression.getProperty() instanceof PrivateIdentifier privateIdentifier) {
            String fieldName = privateIdentifier.getName();
            JSSymbol symbol = compilerContext.privateSymbols != null
                    ? compilerContext.privateSymbols.get(fieldName)
                    : null;
            if (symbol == null) {
                throw new JSCompilerException("Unexpected private field '#" + fieldName + "'");
            }
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            return;
        }
        throw new JSCompilerException("Invalid member property");
    }
}
