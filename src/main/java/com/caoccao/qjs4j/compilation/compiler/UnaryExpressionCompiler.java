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

import java.util.List;

/**
 * Handles compilation of unary expressions including delete, typeof, increment/decrement, and standard unary operators.
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
                        compilerContext.emitter.emitPushConstant(new JSString(propId.getName()));
                    }

                    compilerContext.emitter.emitOpcode(Opcode.DELETE);
                }
            } else if (operand instanceof Identifier id) {
                // Match QuickJS scope_delete_var lowering:
                // - local/arg/closure/implicit arguments bindings => false
                // - unresolved/global binding => DELETE_VAR runtime check
                String identifierName = id.getName();
                boolean fallsBackToNonDeletableBinding = compilerContext.scopeManager
                        .findLocalInScopes(identifierName) != null
                        || compilerContext.captureResolver.resolveCapturedBindingIndex(identifierName) != null
                        || (JSArguments.NAME.equals(identifierName) && !compilerContext.inGlobalScope)
                        || compilerContext.nonDeletableGlobalBindings.contains(identifierName);
                List<Integer> activeWithObjectLocals = compilerContext.withObjectManager.getActiveLocals();
                if (!activeWithObjectLocals.isEmpty()) {
                    compilerContext.identifierCompiler.emitWithAwareDeleteIdentifier(identifierName,
                            activeWithObjectLocals, 0, fallsBackToNonDeletableBinding);
                } else {
                    List<String> inheritedWithBindingNames = compilerContext.withObjectManager
                            .getInheritedBindingNames();
                    if (!inheritedWithBindingNames.isEmpty()) {
                        compilerContext.identifierCompiler.emitInheritedWithAwareDeleteIdentifier(identifierName,
                                inheritedWithBindingNames, 0, fallsBackToNonDeletableBinding);
                    } else if (fallsBackToNonDeletableBinding) {
                        compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
                    } else {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, identifierName);
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
        if (unaryExpr.getOperator() == UnaryExpression.UnaryOperator.INC
                || unaryExpr.getOperator() == UnaryExpression.UnaryOperator.DEC) {
            Expression operand = unaryExpr.getOperand();
            boolean isInc = unaryExpr.getOperator() == UnaryExpression.UnaryOperator.INC;
            boolean isPrefix = unaryExpr.isPrefix();

            if (operand instanceof Identifier id) {
                Opcode update = isPrefix
                        ? (isInc ? Opcode.INC : Opcode.DEC)
                        : (isInc ? Opcode.POST_INC : Opcode.POST_DEC);
                updateIdentifier(id, update, isPrefix);
            } else if (operand instanceof MemberExpression memberExpr) {
                if (memberExpr.getObject().isSuperIdentifier()) {
                    // Super property update follows super-reference semantics:
                    // [this, superObj, key] + GET_SUPER_VALUE -> old value -> update -> PUT_SUPER_VALUE
                    compilerContext.emitHelpers.emitSuperPropertyReference(memberExpr);
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
                        compilerContext.emitter.emitOpcode(isInc ? Opcode.POST_INC : Opcode.POST_DEC); // obj prop
                                                                                                       // old_val
                                                                                                       // new_val
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
                            compilerContext.emitter.emitOpcode(isInc ? Opcode.POST_INC : Opcode.POST_DEC); // obj
                                                                                                           // old_val
                                                                                                           // new_val
                            // Stack: [obj, old_val, new_val] - need [old_val, new_val, obj] for PUT_FIELD
                            // ROT3L: [old_val, new_val, obj]
                            compilerContext.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            // PUT_FIELD pops obj, peeks new_val, leaves [old_val, new_val]
                            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName()); // old_val
                                                                                                        // new_val
                            compilerContext.emitter.emitOpcode(Opcode.DROP); // old_val
                        }
                    } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
                        // Private field: obj.#field
                        String fieldName = privateId.getName();
                        JSSymbol symbol = compilerContext.privateSymbols.get(fieldName);
                        if (symbol == null) {
                            throw new JSCompilerException("Private field not found: #" + fieldName, privateId);
                        }

                        compilerContext.expressionCompiler.compile(memberExpr.getObject());

                        if (isPrefix) {
                            // Prefix: ++obj.#field - returns new value
                            compilerContext.emitter.emitOpcode(Opcode.DUP); // obj obj
                            compilerContext.emitter.emitPushConstant(symbol);
                            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            compilerContext.emitter.emitOpcode(isInc ? Opcode.INC : Opcode.DEC); // obj new_val
                            compilerContext.emitter.emitPushConstant(symbol); // obj new_val symbol
                            compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // new_val
                        } else {
                            // Postfix: obj.#field++ - returns old value
                            compilerContext.emitter.emitOpcode(Opcode.DUP); // obj obj
                            compilerContext.emitter.emitPushConstant(symbol);
                            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            compilerContext.emitter.emitOpcode(isInc ? Opcode.POST_INC : Opcode.POST_DEC); // obj
                                                                                                           // old_val
                                                                                                           // new_val
                            compilerContext.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            compilerContext.emitter.emitPushConstant(symbol); // old_val new_val
                                                                              // obj symbol
                            compilerContext.emitter.emitOpcode(Opcode.ROT3L); // old_val obj symbol new_val
                            compilerContext.emitter.emitOpcode(Opcode.SWAP); // old_val obj new_val symbol
                            compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // old_val
                        }
                    } else {
                        throw new JSCompilerException("Invalid member expression property for increment/decrement",
                                memberExpr);
                    }
                }
            } else if (operand instanceof CallExpression) {
                // Annex B: CallExpression as increment/decrement target throws ReferenceError at runtime.
                compilerContext.expressionCompiler.compile(operand);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid increment/decrement operand");
                compilerContext.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE
            } else {
                throw new JSCompilerException("Invalid operand for increment/decrement operator", operand);
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
            } else if (JSArguments.NAME.equals(name) && compilerContext.hasEnclosingArgumentsBinding
                    && compilerContext.scopeManager.findLocalInScopes(name) == null) {
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(0);
            } else {
                Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
                if (localIndex != null) {
                    compilerContext.identifierCompiler.emitLocalRead(name, localIndex);
                } else {
                    compilerContext.identifierCompiler.emitCapturedOrGlobalIdentifierLookup(name, Opcode.GET_VAR_UNDEF);
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
            default -> throw new JSCompilerException("Unknown unary operator: " + unaryExpr.getOperator(), unaryExpr);
        };

        compilerContext.emitter.emitOpcode(op);
    }

    private void compileDeleteOptionalMemberExpression(MemberExpression memberExpression) {
        // delete a?.b: a short-circuited chain deletes nothing and evaluates to true (QuickJS: drop, push_true).
        compilerContext.emitHelpers.compileOptionalChain(() -> {
            compilerContext.emitHelpers.compileOptionalChainLinkTarget(memberExpression, memberExpression.getObject());
            if (memberExpression.isOptional()) {
                compilerContext.emitHelpers.emitOptionalChainTest(1);
            }
            if (memberExpression.isComputed()) {
                compilerContext.expressionCompiler.compile(memberExpression.getProperty());
            } else {
                // The parser rejects `delete a?.#x`, so a non-computed property is an identifier.
                compilerContext.emitter
                        .emitPushConstant(new JSString(((Identifier) memberExpression.getProperty()).getName()));
            }
            compilerContext.emitter.emitOpcode(Opcode.DELETE);
        }, () -> {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.PUSH_TRUE);
        });
    }

    private void updateIdentifier(Identifier id, Opcode update, boolean isPrefix) {
        if (!compilerContext.withObjectManager.hasActiveWithObject()
                && compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            Integer localIndex = compilerContext.scopeManager.findLocalInScopes(id.getName());
            if (localIndex != null) {
                // Local binding.
                compilerContext.expressionCompiler.compile(id);
                compilerContext.emitter.emitOpcode(update);
                if (compilerContext.scopeManager.isLocalBindingConst(id.getName())) {
                    compilerContext.emitHelpers.emitConstAssignmentErrorForLocal(id.getName(), localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_LOC : Opcode.PUT_LOC, localIndex);
                }
                return;
            }

            Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(id.getName());
            if (capturedIndex != null) {
                // Captured binding.
                compilerContext.expressionCompiler.compile(id);
                compilerContext.emitter.emitOpcode(update);
                if (compilerContext.captureResolver.isCapturedBindingImmutable(id.getName())) {
                    compilerContext.emitHelpers.emitConstAssignmentErrorForCaptured(id.getName(), capturedIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_VAR_REF : Opcode.PUT_VAR_REF,
                            capturedIndex);
                }
                return;
            }
        }
        // The reference determines the receiver before either reading or updating its value.
        compilerContext.assignmentExpressionCompiler.emitIdentifierReference(id.getName());
        compilerContext.emitter.emitOpcode(Opcode.GET_REF_VALUE);
        compilerContext.emitter.emitOpcode(update);
        compilerContext.emitter.emitOpcode(isPrefix ? Opcode.INSERT3 : Opcode.PERM4);
        compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
    }
}
