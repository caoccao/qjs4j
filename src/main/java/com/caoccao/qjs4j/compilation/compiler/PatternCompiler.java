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
import com.caoccao.qjs4j.vm.Opcode;

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
        // Stack: [array]
        int index = 0;
        for (Expression element : arrayExpr.elements()) {
            if (element != null) {
                // Duplicate array for property access
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                compilerContext.emitter.emitI32(index);
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                // Stack: [array, value]

                if (element instanceof AssignmentExpression assignExpr
                        && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
                    // Default value: check if value is undefined
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
                    int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    delegates.expressions.compileExpression(assignExpr.right());
                    compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
                    // Assign to target
                    compileAssignmentTarget(assignExpr.left());
                } else if (element instanceof SpreadElement spreadElem) {
                    // Rest element in assignment: [...rest] = value
                    // Drop the single-element value we just got
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    // Re-get remaining elements as array using Array.from + slice
                    compileAssignmentTarget(spreadElem.argument());
                } else {
                    compileAssignmentTarget(element);
                }
            }
            index++;
        }
        // Drop the array
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }

    void compileAssignmentTarget(Expression target) {
        // Stack: [value]
        // Assign value to target and pop value from stack
        if (target instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = compilerContext.findLocalInScopes(name);
            if (localIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            } else {
                Integer capturedIndex = compilerContext.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_VAR_REF, capturedIndex);
                } else {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, name);
                }
            }
        } else if (target instanceof MemberExpression memberExpr) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                // Stack starts with [value]
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                compilerContext.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
            } else {
                // Stack: [value]
                delegates.expressions.compileExpression(memberExpr.object());
                // Stack: [value, obj]
                if (memberExpr.computed()) {
                    delegates.expressions.compileExpression(memberExpr.property());
                    // Stack: [value, obj, prop] → ROT3L → [obj, prop, value]
                    compilerContext.emitter.emitOpcode(Opcode.ROT3L);
                    compilerContext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.property() instanceof Identifier propId) {
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                }
            }
            // PUT_* leaves value on stack; drop it
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else if (target instanceof ArrayExpression nestedArray) {
            compileArrayDestructuringAssignment(nestedArray);
        } else if (target instanceof ObjectExpression nestedObj) {
            compileObjectDestructuringAssignment(nestedObj);
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
            Integer localIndex = compilerContext.findLocalInScopes(id.name());
            if (localIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            } else if (compilerContext.inGlobalScope) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.name());
            } else {
                int idx = compilerContext.currentScope().declareLocal(id.name());
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, idx);
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
        delegates.expressions.compileExpression(forOfStmt.right());

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
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "done");
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
        // Stack: [object]
        for (ObjectExpression.Property prop : objExpr.properties()) {
            String propName;
            if (prop.key() instanceof Identifier keyId) {
                propName = keyId.name();
            } else if (prop.key() instanceof Literal lit && lit.value() instanceof String s) {
                propName = s;
            } else {
                continue;
            }

            // Duplicate object for property access
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propName);
            // Stack: [object, value]

            Expression value = prop.value();
            if (value instanceof AssignmentExpression assignExpr
                    && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
                // Default value
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
                int jumpNotUndefined = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                delegates.expressions.compileExpression(assignExpr.right());
                compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
                compileAssignmentTarget(assignExpr.left());
            } else {
                compileAssignmentTarget(value);
            }
        }
        // Drop the object
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }

    void compilePatternAssignment(Pattern pattern) {
        compilePatternAssignment(pattern, false);
    }

    private void compilePatternAssignment(Pattern pattern, boolean useExistingBindingInParentScopes) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: value is on stack, just assign it
            String varName = id.name();
            if (compilerContext.inGlobalScope && compilerContext.tdzLocals.contains(varName)) {
                // TDZ local: let/const was pre-declared as a local for TDZ enforcement
                Integer tdzLocal = compilerContext.findLocalInScopes(varName);
                if (tdzLocal != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, tdzLocal);
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
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, existingLocal);
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
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: { proxy, revoke } = value
            // Stack: [object]
            // RequireObjectCoercible(value) even for empty patterns.
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.TO_OBJECT);
            compilerContext.emitter.emitOpcode(Opcode.DROP);

            for (ObjectPattern.Property prop : objPattern.properties()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                Expression propertyKey = prop.key();
                if (!prop.computed() && propertyKey instanceof Identifier identifier) {
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.value());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, identifier.name());
                } else if (!prop.computed() && propertyKey instanceof Literal literal && literal.value() instanceof String propertyName) {
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.value());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propertyName);
                } else if (!prop.computed() && propertyKey instanceof Literal literal
                        && (literal.value() instanceof Integer || literal.value() instanceof Long)) {
                    long propertyIndex = ((Number) literal.value()).longValue();
                    if (propertyIndex >= Integer.MIN_VALUE && propertyIndex <= Integer.MAX_VALUE) {
                        compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                        compilerContext.emitter.emitI32((int) propertyIndex);
                    } else {
                        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(propertyIndex));
                    }
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.value());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else {
                    delegates.expressions.compileExpression(propertyKey);
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                    Identifier bindingIdentifier = getBindingIdentifierForPreResolve(prop.value());
                    if (useExistingBindingInParentScopes && bindingIdentifier != null) {
                        delegates.expressions.compileIdentifier(bindingIdentifier);
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                }
                // Assign to the pattern (could be nested)
                compilePatternAssignment(prop.value(), useExistingBindingInParentScopes);
            }
            // Drop the original object
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: [a, b] = value
            // Stack: [array]

            // Check if there's a rest element
            boolean hasRest = false;
            int restIndex = -1;
            for (int i = 0; i < arrPattern.elements().size(); i++) {
                if (arrPattern.elements().get(i) instanceof RestElement) {
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
                    Pattern element = arrPattern.elements().get(i);
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
                RestElement restElement = (RestElement) arrPattern.elements().get(restIndex);
                compilePatternAssignment(restElement.argument(), useExistingBindingInParentScopes);

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
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, iteratorDoneLocalIndex);
                for (Pattern element : arrPattern.elements()) {
                    compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, iteratorDoneLocalIndex);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    if (element != null) {
                        compilePatternAssignment(element, useExistingBindingInParentScopes);
                    } else {
                        compilerContext.emitter.emitOpcode(Opcode.DROP);
                    }
                }
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOCAL, iteratorDoneLocalIndex);
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
            delegates.expressions.compileExpression(assignPattern.right());
            if (assignPattern.left() instanceof Identifier identifier
                    && isAnonymousFunctionDefinition(assignPattern.right())) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, identifier.name());
            }
            // Patch jump target
            compilerContext.emitter.patchJump(jumpNotUndefined, compilerContext.emitter.currentOffset());
            // Now the stack has the resolved value; assign to the inner pattern
            compilePatternAssignment(assignPattern.left(), useExistingBindingInParentScopes);
        } else if (pattern instanceof RestElement) {
            // RestElement should only appear inside ArrayPattern, shouldn't reach here
            throw new RuntimeException("RestElement can only appear inside ArrayPattern");
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
            compilerContext.currentScope().declareLocal(id.name());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: declare all element variables
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    if (element instanceof RestElement restElement) {
                        // Rest element: declare the argument pattern
                        declarePatternVariables(restElement.argument());
                    } else {
                        // Regular element: recursively declare
                        declarePatternVariables(element);
                    }
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: declare all property variables
            for (ObjectPattern.Property prop : objPattern.properties()) {
                declarePatternVariables(prop.value());
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            // Default value pattern: declare the left-hand side
            declarePatternVariables(assignPattern.left());
        } else if (pattern instanceof RestElement restElement) {
            // Rest element at top level (shouldn't normally happen, but handle it)
            declarePatternVariables(restElement.argument());
        }
    }

    private Identifier getBindingIdentifierForPreResolve(Pattern pattern) {
        if (pattern instanceof Identifier identifier) {
            return identifier;
        }
        if (pattern instanceof AssignmentPattern assignmentPattern
                && assignmentPattern.left() instanceof Identifier identifier) {
            return identifier;
        }
        return null;
    }

    private boolean isAnonymousFunctionDefinition(Expression expression) {
        if (expression instanceof ArrowFunctionExpression) {
            return true;
        }
        if (expression instanceof FunctionExpression functionExpression) {
            return functionExpression.id() == null;
        }
        if (expression instanceof ClassExpression classExpression) {
            return classExpression.id() == null;
        }
        return false;
    }
}
