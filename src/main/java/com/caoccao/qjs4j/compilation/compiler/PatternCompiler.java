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
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

/**
 * Delegate compiler for pattern matching and destructuring assignment compilation.
 * Handles assignment targets, pattern assignments, and destructuring for arrays and objects.
 */
final class PatternCompiler {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;

    PatternCompiler(CompilerContext compilerContext, CompilerDelegates delegates) {
        this.compilerContext = compilerContext;
        this.delegates = delegates;
    }

    void compileArrayDestructuringAssignment(ArrayExpression arrayExpr) {
        // Stack: [iterable]
        // Use iterator protocol (FOR_OF_START/FOR_OF_NEXT/ITERATOR_CLOSE) per ES spec.
        // Following QuickJS: pre-evaluate LHS references before calling next(),
        // and the VM auto-closes iterators on exception via the JSCatchOffset(0) marker.

        List<Expression> elements = arrayExpr.getElements();
        boolean hasRest = false;
        int restIndex = -1;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof SpreadElement) {
                hasRest = true;
                restIndex = i;
                break;
            }
        }

        // Start iteration: iterable -> iter next catch_offset
        compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);

        if (hasRest) {
            // Process elements before rest
            for (int i = 0; i < restIndex; i++) {
                Expression element = elements.get(i);
                // Pre-evaluate LHS, then call FOR_OF_NEXT with the appropriate depth
                int depth = preEvaluateAssignmentTarget(element);
                compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, depth);
                // Stack: iter next catch_offset [pre-eval...] value done
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop done
                // Stack: iter next catch_offset [pre-eval...] value
                if (element != null) {
                    emitAssignmentFromPreEvaluated(element, depth);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // skip hole
                }
            }

            SpreadElement spreadElem = (SpreadElement) elements.get(restIndex);
            Expression restTarget = spreadElem.getArgument();
            int restTargetDepth = preEvaluateAssignmentTarget(restTarget);

            // Collect remaining elements into array for rest
            compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);
            compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
            compilerContext.emitter.emitI32(0);

            int labelRestNext = compilerContext.emitter.currentOffset();
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 2 + restTargetDepth);
            int jumpRestDone = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
            compilerContext.emitter.emitOpcode(Opcode.INC);
            compilerContext.emitter.emitOpcode(Opcode.GOTO);
            int backJumpPos = compilerContext.emitter.currentOffset();
            compilerContext.emitter.emitU32(labelRestNext - (backJumpPos + 4));

            compilerContext.emitter.patchJump(jumpRestDone, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP); // drop undefined
            compilerContext.emitter.emitOpcode(Opcode.DROP); // drop index

            // Iterator is fully exhausted after rest collection. Remove iterator state
            // before assigning the rest target so abrupt completions in nested patterns
            // do not attempt an extra IteratorClose.
            emitDropIteratorStatePreservingTopValues(restTargetDepth + 1);

            // Assign collected array to rest target.
            emitAssignmentFromPreEvaluated(restTarget, restTargetDepth);
        } else {
            // No rest element - use iterator with done tracking and IteratorClose
            int iteratorDoneLocalIndex = compilerContext.currentScope().declareLocal(
                    "$arrayAssignIterDone" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);

            for (Expression element : elements) {
                // Pre-evaluate LHS, then call FOR_OF_NEXT with the appropriate depth
                int depth = preEvaluateAssignmentTarget(element);
                compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, depth);
                // Stack: iter next catch_offset [pre-eval...] value done
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop done
                // Stack: iter next catch_offset [pre-eval...] value
                if (element != null) {
                    emitAssignmentFromPreEvaluated(element, depth);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // skip hole
                }
            }

            // Check if iterator was exhausted
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, iteratorDoneLocalIndex);
            int skipIteratorCloseJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
            // Not exhausted - call IteratorClose
            compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            int iteratorCloseDoneJump = compilerContext.emitter.emitJump(Opcode.GOTO);
            // Exhausted - just drop iter state
            compilerContext.emitter.patchJump(skipIteratorCloseJump, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP); // catch_offset
            compilerContext.emitter.emitOpcode(Opcode.DROP); // next
            compilerContext.emitter.emitOpcode(Opcode.DROP); // iter
            compilerContext.emitter.patchJump(iteratorCloseDoneJump, compilerContext.emitter.currentOffset());
        }
    }

    void compileAssignmentTarget(Expression target) {
        // Stack: [value]
        // Assign value to target and pop value from stack
        if (target instanceof Identifier id) {
            String name = id.getName();
            Integer localIndex = compilerContext.findLocalInScopes(name);
            if (localIndex != null) {
                if (compilerContext.isLocalBindingConst(name)) {
                    emitConstAssignmentErrorForLocal(name, localIndex);
                    return;
                }
                if (compilerContext.tdzLocals.contains(name)) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC_CHECK, localIndex);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                }
            } else {
                Integer capturedIndex = compilerContext.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    if (compilerContext.isCapturedBindingConst(name)) {
                        emitConstAssignmentErrorForCaptured(name, capturedIndex);
                        return;
                    }
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_VAR_REF_CHECK, capturedIndex);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF, name);
                    compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_REF_VALUE);
                }
            }
        } else if (target instanceof MemberExpression memberExpr) {
            if (memberExpr.isOptional()) {
                throw new JSSyntaxErrorException("Invalid destructuring assignment target");
            }
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                // Stack starts with [value]
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                // Stack: [value, this, superObj, key] → ROT4L → [this, superObj, key, value]
                compilerContext.emitter.emitOpcode(Opcode.ROT4L);
                compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
            } else {
                // Stack: [value]
                delegates.expressions.compileExpression(memberExpr.getObject());
                // Stack: [value, obj]
                if (memberExpr.isComputed()) {
                    delegates.expressions.compileExpression(memberExpr.getProperty());
                    // Stack: [value, obj, prop] → ROT3L → [obj, prop, value]
                    compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
                    String fieldName = privateIdentifier.getName();
                    JSSymbol privateSymbol = compilerContext.privateSymbols != null
                            ? compilerContext.privateSymbols.get(fieldName)
                            : null;
                    if (privateSymbol == null) {
                        throw new JSCompilerException("undefined private field '#" + fieldName + "'");
                    }
                    // Stack: [value, obj] -> [obj, value] -> [obj, value, privateSymbol]
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, privateSymbol);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                } else if (memberExpr.getProperty() instanceof Identifier propId) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
                }
            }
            // PUT_* leaves value on stack; drop it
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else if (target instanceof ArrayExpression nestedArray) {
            compileArrayDestructuringAssignment(nestedArray);
        } else if (target instanceof ObjectExpression nestedObj) {
            compileObjectDestructuringAssignment(nestedObj);
        } else {
            throw new JSSyntaxErrorException("Invalid destructuring assignment target");
        }
    }

    private void compileDestructuringAssignmentElement(Expression element) {
        // Stack: [value]
        if (element instanceof AssignmentExpression assignExpr
                && assignExpr.getOperator() == AssignmentOperator.ASSIGN) {
            // Default value: check if value is undefined
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
            int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            delegates.expressions.compileExpression(assignExpr.getRight());
            // Set function name for anonymous function definitions
            if (assignExpr.getLeft() instanceof Identifier targetId
                    && isAnonymousFunctionDefinition(assignExpr.getRight())) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, targetId.getName());
            }
            compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
            compileAssignmentTarget(assignExpr.getLeft());
        } else {
            compileAssignmentTarget(element);
        }
    }

    /**
     * Assign the iteration value in a for-of loop.
     * For var declarations, use findLocalInScopes to store to the parent scope's local
     * (since var is function-scoped, not block-scoped). For let/const, use the normal
     * compilePatternAssignment which creates locals in the current (loop) scope.
     */
    void compileForOfValueAssignment(Pattern pattern, boolean isVar) {
        if (isVar && pattern instanceof Identifier id) {
            Integer localIndex = compilerContext.findLocalInScopes(id.getName());
            if (localIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            } else if (compilerContext.inGlobalScope) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.getName());
            } else {
                int idx = compilerContext.currentScope().declareLocal(id.getName());
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, idx);
            }
        } else {
            if (isVar) {
                compileVarPatternAssignment(pattern);
            } else {
                compilePatternAssignment(pattern);
            }
        }
    }

    /**
     * Compile a for-of statement where the LHS is a CallExpression (Annex B).
     * The call is evaluated each iteration, then a ReferenceError is thrown.
     */
    void compileForOfWithCallExpressionTarget(ForOfStatement forOfStmt, CallExpression callExpr) {
        compilerContext.enterScope();

        // Compile the iterable expression
        delegates.expressions.compileExpression(forOfStmt.getRight());

        // FOR_OF_START to get iterator
        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        // Stack: iter, next, catch_offset
        int loopStart = compilerContext.emitter.currentOffset();

        // FOR_OF_NEXT to get next value
        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "done");
        } else {
            compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
        }

        // Stack: iter, next, catch_offset, value, done
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        // Stack: iter, next, catch_offset, value (or result for async)

        if (forOfStmt.isAsync()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
        }

        // Drop the value - we can't assign it
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        // Evaluate the call expression (f() is called at runtime)
        delegates.expressions.compileExpression(callExpr);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        // Throw ReferenceError
        compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
        compilerContext.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE

        // End label (when done=true)
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        // Drop remaining value on stack
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        // Clean up iterator: drop catch_offset, next, iter
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
    }

    void compileObjectDestructuringAssignment(ObjectExpression objExpr) {
        // Stack: [source]
        // Separate regular properties from spread (rest) property.
        // In ObjectExpression, spread is represented as kind="spread".
        java.util.List<ObjectExpressionProperty> regularProperties = new java.util.ArrayList<>();
        Expression restTarget = null;
        for (ObjectExpressionProperty prop : objExpr.getProperties()) {
            if ("spread".equals(prop.getKind())) {
                restTarget = prop.getValue();
            } else {
                regularProperties.add(prop);
            }
        }

        // Per spec: RequireObjectCoercible(value) for all ObjectAssignmentPattern forms.
        // When there are regular properties, the first GET_FIELD throws for null/undefined.
        // For empty patterns or rest-only patterns, we need an explicit check.
        if (regularProperties.isEmpty()) {
            if (restTarget == null) {
                // {} = val → just check and return
                compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                return;
            }
            // {...rest} = val → explicit coercibility check
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        int sourceLocalIndex = compilerContext.currentScope().declareLocal(
                "$objectAssignSource" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, sourceLocalIndex);

        // If there's a rest element with regular properties, create an exclude list
        int excludeListLocalIndex = -1;
        if (restTarget != null && !regularProperties.isEmpty()) {
            compilerContext.emitter.emitOpcode(Opcode.OBJECT);
            excludeListLocalIndex = compilerContext.currentScope().declareLocal(
                    "$excludeList" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, excludeListLocalIndex);
        }

        for (ObjectExpressionProperty property : regularProperties) {
            int propertyKeyLocalIndex = -1;
            if (property.isComputed()) {
                delegates.expressions.compileExpression(property.getKey());
                compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                propertyKeyLocalIndex = compilerContext.currentScope().declareLocal(
                        "$objectAssignKey" + compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, propertyKeyLocalIndex);
            }

            int targetDepth = preEvaluateAssignmentTarget(property.getValue());

            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, sourceLocalIndex);
            if (property.isComputed()) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, propertyKeyLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (property.getKey() instanceof Identifier identifier) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, identifier.getName());
            } else if (property.getKey() instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propertyName);
            } else if (property.getKey() instanceof Literal literal
                    && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                long propertyIndex = ((Number) literal.getValue()).longValue();
                if (propertyIndex >= Integer.MIN_VALUE && propertyIndex <= Integer.MAX_VALUE) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                    compilerContext.emitter.emitI32((int) propertyIndex);
                } else {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(propertyIndex));
                }
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else {
                delegates.expressions.compileExpression(property.getKey());
                compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            }

            emitAssignmentFromPreEvaluated(property.getValue(), targetDepth);

            // If rest, add property key to exclude list
            if (restTarget != null) {
                if (property.isComputed()) {
                    // Computed property: use PUT_ARRAY_EL which works with objects.
                    // Stack: [] → [excludeList, key, null] → PUT_ARRAY_EL → [null] → DROP → []
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, excludeListLocalIndex);
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, propertyKeyLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null value
                } else {
                    // Non-computed: use DEFINE_FIELD with atom name.
                    // Stack: [] → [excludeList, null] → DEFINE_FIELD → [excludeList] → DROP → []
                    compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, excludeListLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    if (property.getKey() instanceof Identifier identifier) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, identifier.getName());
                    } else if (property.getKey() instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, propertyName);
                    } else if (property.getKey() instanceof Literal literal
                            && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD,
                                String.valueOf(((Number) literal.getValue()).longValue()));
                    }
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop excludeList
                }
            }
        }

        if (restTarget != null) {
            if (regularProperties.isEmpty()) {
                // No exclude list — copy all properties from source to a new object.
                // Stack: []
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, sourceLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                // Stack: [source, target]
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                // Stack: [source, target, target]
                compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                // Stack: [target, target, source]
                compilerContext.emitter.emitOpcode(Opcode.NULL);
                // Stack: [target, target, source, null]
                // COPY_DATA_PROPERTIES mask=7: target@sp-4, source@sp-2, exclude@sp-1
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 7);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // null
                compilerContext.emitter.emitOpcode(Opcode.DROP); // source
                // Stack: [target, target] — assign TOS (target) to rest, drop extra
                compileAssignmentTarget(restTarget);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // drop extra target
            } else {
                // Stack: []
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, excludeListLocalIndex);
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, sourceLocalIndex);
                compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                // Stack: [excludeList, source, target]
                // COPY_DATA_PROPERTIES mask=68: target@sp-1(0), source@sp-2(1), exclude@sp-3(2)
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 68);
                // Stack: [excludeList, source, target]
                compileAssignmentTarget(restTarget);
                // Stack: [excludeList, source]
                compilerContext.emitter.emitOpcode(Opcode.DROP); // source
                compilerContext.emitter.emitOpcode(Opcode.DROP); // excludeList
            }
        }
    }

    void compilePatternAssignment(Pattern pattern) {
        compilePatternAssignment(pattern, false);
    }

    private void compilePatternAssignment(Pattern pattern, boolean useExistingBindingInParentScopes) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: value is on stack, just assign it
            String varName = id.getName();
            if (compilerContext.inGlobalScope && compilerContext.tdzLocals.contains(varName)) {
                // TDZ local: let/const was pre-declared as a local for TDZ enforcement
                Integer tdzLocal = compilerContext.findLocalInScopes(varName);
                if (tdzLocal != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, tdzLocal);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
                }
            } else if (compilerContext.inGlobalScope) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            } else if (compilerContext.varInGlobalProgram) {
                // var declaration in global program inside a block (for, try, if, etc.).
                // var is global-scoped, so use PUT_VAR — UNLESS the name is already
                // a local (e.g., catch parameter per ES B.3.5), in which case use PUT_LOCAL.
                Integer existingLocal = compilerContext.findLocalInScopes(varName);
                if (existingLocal != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, existingLocal);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
                }
            } else {
                Integer localIndex;
                if (useExistingBindingInParentScopes && compilerContext.varDeclarationScopeOverride != null) {
                    CompilerScope varDeclarationScope = compilerContext.varDeclarationScopeOverride;
                    localIndex = varDeclarationScope.getLocal(varName);
                    if (localIndex == null) {
                        localIndex = varDeclarationScope.declareLocal(varName);
                    }
                } else if (useExistingBindingInParentScopes) {
                    localIndex = compilerContext.findLocalInScopes(varName);
                    if (localIndex == null) {
                        localIndex = compilerContext.currentScope().declareLocal(varName);
                    }
                } else {
                    // let/const declarations are lexical. They should resolve only against
                    // the current scope so block bindings shadow outer bindings.
                    localIndex = compilerContext.currentScope().getLocal(varName);
                    if (localIndex == null) {
                        localIndex = compilerContext.currentScope().declareLocal(varName);
                    }
                }
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: { proxy, revoke } = value
            // Stack: [object]
            boolean hasRest = objPattern.getRestElement() != null;

            // RequireObjectCoercible(value) even for empty patterns.
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
            compilerContext.emitter.emitOpcode(Opcode.DROP);

            if (hasRest && !objPattern.getProperties().isEmpty()) {
                // Create exclude list object and put it under source on stack
                // Stack: [source] -> [excludeList, source]
                compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            }

            for (ObjectPatternProperty prop : objPattern.getProperties()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                Expression propertyKey = prop.getKey();
                if (!prop.isComputed() && propertyKey instanceof Identifier identifier) {
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, identifier.getName());
                } else if (!prop.isComputed() && propertyKey instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propertyName);
                } else if (!prop.isComputed() && propertyKey instanceof Literal literal
                        && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                    long propertyIndex = ((Number) literal.getValue()).longValue();
                    if (propertyIndex >= Integer.MIN_VALUE && propertyIndex <= Integer.MAX_VALUE) {
                        compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                        compilerContext.emitter.emitI32((int) propertyIndex);
                    } else {
                        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(propertyIndex));
                    }
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else {
                    delegates.expressions.compileExpression(propertyKey);
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.getValue());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                }
                // Assign to the pattern (could be nested)
                compilePatternAssignment(prop.getValue(), useExistingBindingInParentScopes);

                if (hasRest) {
                    // Add the property key to the exclude list
                    // Stack: [excludeList, source]
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    // Stack: [source, excludeList]
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    // Stack: [source, excludeList, excludeList]
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    // Stack: [source, excludeList, excludeList, null]
                    if (!prop.isComputed() && propertyKey instanceof Identifier identifier) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, identifier.getName());
                        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop duplicate excludeList
                    } else if (!prop.isComputed() && propertyKey instanceof Literal literal && literal.getValue() instanceof String propertyName) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, propertyName);
                        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop duplicate excludeList
                    } else if (!prop.isComputed() && propertyKey instanceof Literal literal
                            && (literal.getValue() instanceof Integer || literal.getValue() instanceof Long)) {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, String.valueOf(((Number) literal.getValue()).longValue()));
                        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop duplicate excludeList
                    } else {
                        // Computed property key: re-evaluate expression to get the key name
                        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null
                        delegates.expressions.compileExpression(propertyKey);
                        compilerContext.emitter.emitOpcode(Opcode.NULL);
                        compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null value
                    }
                    // DEFINE_FIELD leaves excludeList on stack, so normalize both paths to:
                    // Stack: [source, excludeList]
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    // Stack: [excludeList, source]
                }
            }

            if (hasRest) {
                // Compile rest element: {...rest} = source
                if (objPattern.getProperties().isEmpty()) {
                    // No properties to exclude, just copy all
                    // Stack: [source]
                    compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                    // Stack: [source, target]
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    // Stack: [source, target, target]
                    compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                    // Stack: [target, target, source]
                    compilerContext.emitter.emitOpcode(Opcode.NULL);
                    // Stack: [target, target, source, null(excludeList)]
                    // COPY_DATA_PROPERTIES: target=sp[-1-(mask&3)], source=sp[-1-((mask>>2)&7)], exclude=sp[-1-((mask>>5)&7)]
                    // target at sp-4 (offset 3), source at sp-2 (offset 1), exclude at sp-1 (offset 0)
                    // mask = 3 | (1 << 2) | (0 << 5) = 3 + 4 + 0 = 7
                    compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 7);
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop null
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop source
                    // Stack: [target]
                } else {
                    // Stack: [excludeList, source]
                    compilerContext.emitter.emitOpcode(Opcode.OBJECT);
                    // Stack: [excludeList, source, target]
                    // COPY_DATA_PROPERTIES: target=sp-1(offset 0), source=sp-2(offset 1), exclude=sp-3(offset 2)
                    // mask = 0 | (1 << 2) | (2 << 5) = 0 + 4 + 64 = 68
                    compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 68);
                    // Stack: [excludeList, source, target]
                }
                // Assign target (TOS) to the rest pattern
                compilePatternAssignment(objPattern.getRestElement().getArgument(), useExistingBindingInParentScopes);
                if (!objPattern.getProperties().isEmpty()) {
                    // Stack: [excludeList, source]
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop source
                    compilerContext.emitter.emitOpcode(Opcode.DROP); // drop excludeList
                }
            } else {
                // Drop the original object
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: [a, b] = value
            // Stack: [array]

            // Check if there's a rest element
            boolean hasRest = false;
            int restIndex = -1;
            for (int i = 0; i < arrPattern.getElements().size(); i++) {
                if (arrPattern.getElements().get(i) instanceof RestElement) {
                    hasRest = true;
                    restIndex = i;
                    break;
                }
            }

            if (hasRest) {
                // Use iterator-based approach for rest elements (following QuickJS js_emit_spread_code)
                // Stack: [iterable]

                // Start iteration: iterable -> iter next catch_offset
                compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);

                // Process elements before rest
                for (int i = 0; i < restIndex; i++) {
                    Pattern element = arrPattern.getElements().get(i);
                    if (element != null) {
                        // Get next value: iter next -> iter next catch_offset value done
                        compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                        // Stack: iter next catch_offset value done
                        // Drop done flag
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                        // Stack: iter next catch_offset value
                        // Assign value to pattern
                        compilePatternAssignment(element, useExistingBindingInParentScopes);
                        // Stack: iter next catch_offset (after assignment drops the value)
                    } else {
                        // Skip element
                        compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                        // Stack: iter next catch_offset value done
                        compilerContext.emitter.emitOpcode(Opcode.DROP);  // Drop done
                        compilerContext.emitter.emitOpcode(Opcode.DROP);  // Drop value
                        // Stack: iter next catch_offset
                    }
                }

                // Now handle the rest element
                // Following QuickJS js_emit_spread_code at line 25663
                // Stack: iter next catch_offset -> iter next catch_offset array

                // Create empty array with 0 elements
                compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);
                // Push initial index 0
                compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                compilerContext.emitter.emitI32(0);

                // Loop to collect remaining elements
                int labelRestNext = compilerContext.emitter.currentOffset();

                // Get next value: iter next catch_offset array idx -> iter next catch_offset array idx value done
                compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 2);  // depth = 2 (array and idx)

                // Check if done
                int jumpRestDone = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

                // Not done: array idx value -> array idx
                compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                // Increment index
                compilerContext.emitter.emitOpcode(Opcode.INC);
                // Continue loop - jump back to labelRestNext
                compilerContext.emitter.emitOpcode(Opcode.GOTO);
                int backJumpPos = compilerContext.emitter.currentOffset();
                compilerContext.emitter.emitU32(labelRestNext - (backJumpPos + 4));

                // Done collecting - patch the IF_TRUE jump
                compilerContext.emitter.patchJump(jumpRestDone, compilerContext.emitter.currentOffset());
                // Stack: iter next catch_offset array idx undef
                // Drop undef and idx
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                // Stack: iter next catch_offset array

                // Assign array to rest pattern
                RestElement restElement = (RestElement) arrPattern.getElements().get(restIndex);
                compilePatternAssignment(restElement.getArgument(), useExistingBindingInParentScopes);

                // Clean up iterator state: drop catch_offset, next, iter
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                // Iterator-based array binding semantics.
                compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
                int iteratorDoneLocalIndex = compilerContext.currentScope().declareLocal(
                        "$arrayPatternIteratorDone" + compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);
                for (Pattern element : arrPattern.getElements()) {
                    compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, iteratorDoneLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    if (element != null) {
                        compilePatternAssignment(element, useExistingBindingInParentScopes);
                    } else {
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                }
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, iteratorDoneLocalIndex);
                int skipIteratorCloseJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
                // Iterator not exhausted by this pattern; call return() for IteratorClose.
                compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
                int iteratorCloseDoneJump = compilerContext.emitter.emitJump(Opcode.GOTO);
                compilerContext.emitter.patchJump(skipIteratorCloseJump, compilerContext.emitter.currentOffset());
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.patchJump(iteratorCloseDoneJump, compilerContext.emitter.currentOffset());
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            // Destructuring with default value: [x = defaultVal] or { y = defaultVal }
            // Stack: [value]
            // If value is undefined, use the default value instead
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
            int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
            // Value is undefined: drop it and use default
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            delegates.expressions.compileExpression(assignPattern.getRight());
            if (assignPattern.getLeft() instanceof Identifier identifier
                    && isAnonymousFunctionDefinition(assignPattern.getRight())) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.getName());
            }
            // Patch jump target
            compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
            // Now the stack has the resolved value; assign to the inner pattern
            compilePatternAssignment(assignPattern.getLeft(), useExistingBindingInParentScopes);
        } else if (pattern instanceof RestElement) {
            // RestElement should only appear inside ArrayPattern or ObjectPattern, shouldn't reach here
            throw new RuntimeException("RestElement can only appear inside ArrayPattern or ObjectPattern");
        }
    }

    void compileVarPatternAssignment(Pattern pattern) {
        compilePatternAssignment(pattern, true);
    }

    /**
     * Declare all variables in a pattern (used for for-of loops with destructuring).
     * This recursively declares variables for Identifier, ArrayPattern, and ObjectPattern.
     */
    void declarePatternVariables(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: declare it as a local variable
            compilerContext.currentScope().declareLocal(id.getName());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: declare all element variables
            for (Pattern element : arrPattern.getElements()) {
                if (element != null) {
                    if (element instanceof RestElement restElement) {
                        // Rest element: declare the argument pattern
                        declarePatternVariables(restElement.getArgument());
                    } else {
                        // Regular element: recursively declare
                        declarePatternVariables(element);
                    }
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: declare all property variables
            for (ObjectPatternProperty prop : objPattern.getProperties()) {
                declarePatternVariables(prop.getValue());
            }
            if (objPattern.getRestElement() != null) {
                declarePatternVariables(objPattern.getRestElement().getArgument());
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            // Default value pattern: declare the left-hand side
            declarePatternVariables(assignPattern.getLeft());
        } else if (pattern instanceof RestElement restElement) {
            // Rest element at top level (shouldn't normally happen, but handle it)
            declarePatternVariables(restElement.getArgument());
        }
    }

    /**
     * Emit assignment using pre-evaluated LHS references.
     * Stack: [pre-eval-values...] value
     * After: all consumed (value assigned, pre-eval values consumed)
     */
    private void emitAssignmentFromPreEvaluated(Expression element, int depth) {
        if (depth == 0) {
            // No pre-evaluation was done; use normal path
            compileDestructuringAssignmentElement(element);
            return;
        }
        // Handle default values first
        Expression target;
        if (element instanceof AssignmentExpression assignExpr
                && assignExpr.getOperator() == AssignmentOperator.ASSIGN) {
            // Default value: check if value is undefined
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
            int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            delegates.expressions.compileExpression(assignExpr.getRight());
            if (assignExpr.getLeft() instanceof Identifier targetId
                    && isAnonymousFunctionDefinition(assignExpr.getRight())) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, targetId.getName());
            }
            compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
            target = assignExpr.getLeft();
        } else {
            target = element;
        }
        // Now assign using the pre-evaluated references
        // Stack: [pre-eval-values...] value
        if (target instanceof MemberExpression memberExpr) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                // Stack: [this, superObj, key, value] → PUT_SUPER_VALUE pops value from top
                compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // PUT_SUPER_VALUE leaves value
            } else if (memberExpr.isComputed()) {
                // Stack: [obj, key, value] — already correct for PUT_ARRAY_EL
                compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // PUT_ARRAY_EL leaves value
            } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
                String fieldName = privateIdentifier.getName();
                JSSymbol privateSymbol = compilerContext.privateSymbols != null
                        ? compilerContext.privateSymbols.get(fieldName)
                        : null;
                if (privateSymbol == null) {
                    throw new JSCompilerException("undefined private field '#" + fieldName + "'");
                }
                // Stack: [obj, value] -> [obj, value, privateSymbol]
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, privateSymbol);
                compilerContext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                compilerContext.emitter.emitOpcode(Opcode.DROP); // PUT_PRIVATE_FIELD leaves value
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                // Stack: [obj, value] → PUT_FIELD expects [value, obj]
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.getName());
                compilerContext.emitter.emitOpcode(Opcode.DROP); // PUT_FIELD leaves value
            }
        }
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

    private void emitDropIteratorStatePreservingTopValues(int preservedValueCount) {
        if (preservedValueCount < 0) {
            throw new IllegalArgumentException("preservedValueCount must not be negative");
        }
        if (preservedValueCount == 0) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            return;
        }

        int[] preservedLocalIndexes = new int[preservedValueCount];
        for (int valueIndex = preservedValueCount - 1; valueIndex >= 0; valueIndex--) {
            int localIndex = compilerContext.currentScope().declareLocal(
                    "$iter_preserve_" + valueIndex + "_" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            preservedLocalIndexes[valueIndex] = localIndex;
        }

        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        for (int valueIndex = 0; valueIndex < preservedValueCount; valueIndex++) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, preservedLocalIndexes[valueIndex]);
        }
    }

    /**
     * Extract the actual assignment target from an element, unwrapping default values.
     */
    private Expression getAssignmentTarget(Expression element) {
        if (element == null) {
            return null;
        }
        if (element instanceof AssignmentExpression assignExpr
                && assignExpr.getOperator() == AssignmentOperator.ASSIGN) {
            return assignExpr.getLeft();
        }
        return element;
    }

    private Identifier getBindingIdentifierForPreResolve(Pattern pattern) {
        if (pattern instanceof Identifier identifier) {
            return identifier;
        }
        if (pattern instanceof AssignmentPattern assignmentPattern
                && assignmentPattern.getLeft() instanceof Identifier identifier) {
            return identifier;
        }
        return null;
    }

    private boolean isAnonymousFunctionDefinition(Expression expression) {
        if (expression instanceof ArrowFunctionExpression) {
            return true;
        }
        if (expression instanceof FunctionExpression functionExpression) {
            return functionExpression.getId() == null;
        }
        if (expression instanceof ClassExpression classExpression) {
            return classExpression.getId() == null;
        }
        return false;
    }

    /**
     * Pre-evaluate the LHS of a destructuring assignment element before calling FOR_OF_NEXT.
     * Per spec (IteratorDestructuringAssignmentEvaluation step 1a): if the target is not
     * a pattern, evaluate it first to get the reference.
     * Returns the number of values pushed on the stack (the depth for FOR_OF_NEXT).
     */
    private int preEvaluateAssignmentTarget(Expression element) {
        Expression target = getAssignmentTarget(element);
        if (target == null) {
            return 0; // hole
        }
        if (target instanceof MemberExpression memberExpr && !memberExpr.isOptional()) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                // Pre-evaluate super reference: this, superObj, key
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                return 3; // this, superObj, key on stack
            }
            // Pre-evaluate the object
            delegates.expressions.compileExpression(memberExpr.getObject());
            if (memberExpr.isComputed()) {
                // Pre-evaluate the computed key
                delegates.expressions.compileExpression(memberExpr.getProperty());
                return 2; // obj + key on stack
            }
            return 1; // obj on stack
        }
        // Identifiers and nested patterns: no pre-evaluation needed
        return 0;
    }
}
