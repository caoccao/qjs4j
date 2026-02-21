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
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Delegate compiler for pattern matching and destructuring assignment compilation.
 * Handles assignment targets, pattern assignments, and destructuring for arrays and objects.
 */
final class PatternCompiler {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;

    PatternCompiler(CompilerContext ctx, CompilerDelegates delegates) {
        this.ctx = ctx;
        this.delegates = delegates;
    }

    void compileArrayDestructuringAssignment(ArrayExpression arrayExpr) {
        // Stack: [array]
        int index = 0;
        for (Expression element : arrayExpr.elements()) {
            if (element != null) {
                // Duplicate array for property access
                ctx.emitter.emitOpcode(Opcode.DUP);
                ctx.emitter.emitOpcode(Opcode.PUSH_I32);
                ctx.emitter.emitI32(index);
                ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                // Stack: [array, value]

                if (element instanceof AssignmentExpression assignExpr
                        && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
                    // Default value: check if value is undefined
                    ctx.emitter.emitOpcode(Opcode.DUP);
                    ctx.emitter.emitOpcode(Opcode.IS_UNDEFINED);
                    int jumpNotUndefined = ctx.emitter.emitJump(Opcode.IF_FALSE);
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    delegates.expressions.compileExpression(assignExpr.right());
                    ctx.emitter.patchJump(jumpNotUndefined, ctx.emitter.currentOffset());
                    // Assign to target
                    compileAssignmentTarget(assignExpr.left());
                } else if (element instanceof SpreadElement spreadElem) {
                    // Rest element in assignment: [...rest] = value
                    // Drop the single-element value we just got
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    // Re-get remaining elements as array using Array.from + slice
                    compileAssignmentTarget(spreadElem.argument());
                } else {
                    compileAssignmentTarget(element);
                }
            }
            index++;
        }
        // Drop the array
        ctx.emitter.emitOpcode(Opcode.DROP);
    }

    void compileAssignmentTarget(Expression target) {
        // Stack: [value]
        // Assign value to target and pop value from stack
        if (target instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = ctx.findLocalInScopes(name);
            if (localIndex != null) {
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            } else {
                Integer capturedIndex = ctx.resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_VAR_REF, capturedIndex);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, name);
                }
            }
        } else if (target instanceof MemberExpression memberExpr) {
            if (ctx.isSuperMemberExpression(memberExpr)) {
                // Stack starts with [value]
                ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
                ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                ctx.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                ctx.emitter.emitOpcode(Opcode.GET_SUPER);
                delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                ctx.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
            } else {
                // Stack: [value]
                delegates.expressions.compileExpression(memberExpr.object());
                // Stack: [value, obj]
                if (memberExpr.computed()) {
                    delegates.expressions.compileExpression(memberExpr.property());
                    // Stack: [value, obj, prop] → ROT3L → [obj, prop, value]
                    ctx.emitter.emitOpcode(Opcode.ROT3L);
                    ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.property() instanceof Identifier propId) {
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                }
            }
            // PUT_* leaves value on stack; drop it
            ctx.emitter.emitOpcode(Opcode.DROP);
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
            Integer localIndex = ctx.findLocalInScopes(id.name());
            if (localIndex != null) {
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            } else if (ctx.inGlobalScope) {
                ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.name());
            } else {
                int idx = ctx.currentScope().declareLocal(id.name());
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, idx);
            }
        } else {
            compilePatternAssignment(pattern);
        }
    }

    /**
     * Compile a for-of statement where the LHS is a CallExpression (Annex B).
     * The call is evaluated each iteration, then a ReferenceError is thrown.
     */
    void compileForOfWithCallExpressionTarget(ForOfStatement forOfStmt, CallExpression callExpr) {
        ctx.enterScope();

        // Compile the iterable expression
        delegates.expressions.compileExpression(forOfStmt.right());

        // FOR_OF_START to get iterator
        if (forOfStmt.isAsync()) {
            ctx.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            ctx.emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        // Stack: iter, next, catch_offset
        int loopStart = ctx.emitter.currentOffset();

        // FOR_OF_NEXT to get next value
        if (forOfStmt.isAsync()) {
            ctx.emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);
            ctx.emitter.emitOpcode(Opcode.AWAIT);
            ctx.emitter.emitOpcode(Opcode.DUP);
            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "done");
        } else {
            ctx.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
        }

        // Stack: iter, next, catch_offset, value, done
        int jumpToEnd = ctx.emitter.emitJump(Opcode.IF_TRUE);
        // Stack: iter, next, catch_offset, value (or result for async)

        if (forOfStmt.isAsync()) {
            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
        }

        // Drop the value - we can't assign it
        ctx.emitter.emitOpcode(Opcode.DROP);
        // Evaluate the call expression (f() is called at runtime)
        delegates.expressions.compileExpression(callExpr);
        ctx.emitter.emitOpcode(Opcode.DROP);
        // Throw ReferenceError
        ctx.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
        ctx.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE

        // End label (when done=true)
        ctx.emitter.patchJump(jumpToEnd, ctx.emitter.currentOffset());
        // Drop remaining value on stack
        ctx.emitter.emitOpcode(Opcode.DROP);

        // Clean up iterator: drop catch_offset, next, iter
        ctx.emitter.emitOpcode(Opcode.DROP);
        ctx.emitter.emitOpcode(Opcode.DROP);
        ctx.emitter.emitOpcode(Opcode.DROP);

        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.exitScope();
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
            ctx.emitter.emitOpcode(Opcode.DUP);
            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propName);
            // Stack: [object, value]

            Expression value = prop.value();
            if (value instanceof AssignmentExpression assignExpr
                    && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
                // Default value
                ctx.emitter.emitOpcode(Opcode.DUP);
                ctx.emitter.emitOpcode(Opcode.IS_UNDEFINED);
                int jumpNotUndefined = ctx.emitter.emitJump(Opcode.IF_FALSE);
                ctx.emitter.emitOpcode(Opcode.DROP);
                delegates.expressions.compileExpression(assignExpr.right());
                ctx.emitter.patchJump(jumpNotUndefined, ctx.emitter.currentOffset());
                compileAssignmentTarget(assignExpr.left());
            } else {
                compileAssignmentTarget(value);
            }
        }
        // Drop the object
        ctx.emitter.emitOpcode(Opcode.DROP);
    }

    void compilePatternAssignment(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: value is on stack, just assign it
            String varName = id.name();
            if (ctx.inGlobalScope && ctx.tdzLocals.contains(varName)) {
                // TDZ local: let/const was pre-declared as a local for TDZ enforcement
                Integer tdzLocal = ctx.findLocalInScopes(varName);
                if (tdzLocal != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, tdzLocal);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
                }
            } else if (ctx.inGlobalScope) {
                ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            } else if (ctx.varInGlobalProgram) {
                // var declaration in global program inside a block (for, try, if, etc.).
                // var is global-scoped, so use PUT_VAR — UNLESS the name is already
                // a local (e.g., catch parameter per ES B.3.5), in which case use PUT_LOCAL.
                Integer existingLocal = ctx.findLocalInScopes(varName);
                if (existingLocal != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, existingLocal);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
                }
            } else {
                int localIndex = ctx.currentScope().declareLocal(varName);
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: { proxy, revoke } = value
            // Stack: [object]
            for (ObjectPattern.Property prop : objPattern.properties()) {
                // Get the property name
                String propName = ((Identifier) prop.key()).name();

                // Duplicate object for each property access
                ctx.emitter.emitOpcode(Opcode.DUP);
                // Get the property value
                ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propName);
                // Assign to the pattern (could be nested)
                compilePatternAssignment(prop.value());
            }
            // Drop the original object
            ctx.emitter.emitOpcode(Opcode.DROP);
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
                ctx.emitter.emitOpcode(Opcode.FOR_OF_START);

                // Process elements before rest
                for (int i = 0; i < restIndex; i++) {
                    Pattern element = arrPattern.elements().get(i);
                    if (element != null) {
                        // Get next value: iter next -> iter next catch_offset value done
                        ctx.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                        // Stack: iter next catch_offset value done
                        // Drop done flag
                        ctx.emitter.emitOpcode(Opcode.DROP);
                        // Stack: iter next catch_offset value
                        // Assign value to pattern
                        compilePatternAssignment(element);
                        // Stack: iter next catch_offset (after assignment drops the value)
                    } else {
                        // Skip element
                        ctx.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                        // Stack: iter next catch_offset value done
                        ctx.emitter.emitOpcode(Opcode.DROP);  // Drop done
                        ctx.emitter.emitOpcode(Opcode.DROP);  // Drop value
                        // Stack: iter next catch_offset
                    }
                }

                // Now handle the rest element
                // Following QuickJS js_emit_spread_code at line 25663
                // Stack: iter next catch_offset -> iter next catch_offset array

                // Create empty array with 0 elements
                ctx.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);
                // Push initial index 0
                ctx.emitter.emitOpcode(Opcode.PUSH_I32);
                ctx.emitter.emitI32(0);

                // Loop to collect remaining elements
                int labelRestNext = ctx.emitter.currentOffset();

                // Get next value: iter next catch_offset array idx -> iter next catch_offset array idx value done
                ctx.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 2);  // depth = 2 (array and idx)

                // Check if done
                int jumpRestDone = ctx.emitter.emitJump(Opcode.IF_TRUE);

                // Not done: array idx value -> array idx
                ctx.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                // Increment index
                ctx.emitter.emitOpcode(Opcode.INC);
                // Continue loop - jump back to labelRestNext
                ctx.emitter.emitOpcode(Opcode.GOTO);
                int backJumpPos = ctx.emitter.currentOffset();
                ctx.emitter.emitU32(labelRestNext - (backJumpPos + 4));

                // Done collecting - patch the IF_TRUE jump
                ctx.emitter.patchJump(jumpRestDone, ctx.emitter.currentOffset());
                // Stack: iter next catch_offset array idx undef
                // Drop undef and idx
                ctx.emitter.emitOpcode(Opcode.DROP);
                ctx.emitter.emitOpcode(Opcode.DROP);
                // Stack: iter next catch_offset array

                // Assign array to rest pattern
                RestElement restElement = (RestElement) arrPattern.elements().get(restIndex);
                compilePatternAssignment(restElement.argument());

                // Clean up iterator state: drop catch_offset, next, iter
                ctx.emitter.emitOpcode(Opcode.DROP);
                ctx.emitter.emitOpcode(Opcode.DROP);
                ctx.emitter.emitOpcode(Opcode.DROP);
            } else {
                // Simple indexed access (no rest element)
                int index = 0;
                for (Pattern element : arrPattern.elements()) {
                    if (element != null) {
                        // Duplicate array
                        ctx.emitter.emitOpcode(Opcode.DUP);
                        // Push index
                        ctx.emitter.emitOpcode(Opcode.PUSH_I32);
                        ctx.emitter.emitI32(index);
                        // Get array element
                        ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                        // Assign to the pattern
                        compilePatternAssignment(element);
                    }
                    index++;
                }
                // Drop the original array
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            // Destructuring with default value: [x = defaultVal] or { y = defaultVal }
            // Stack: [value]
            // If value is undefined, use the default value instead
            ctx.emitter.emitOpcode(Opcode.DUP);
            ctx.emitter.emitOpcode(Opcode.IS_UNDEFINED);
            int jumpNotUndefined = ctx.emitter.emitJump(Opcode.IF_FALSE);
            // Value is undefined: drop it and use default
            ctx.emitter.emitOpcode(Opcode.DROP);
            delegates.expressions.compileExpression(assignPattern.right());
            // Patch jump target
            ctx.emitter.patchJump(jumpNotUndefined, ctx.emitter.currentOffset());
            // Now the stack has the resolved value; assign to the inner pattern
            compilePatternAssignment(assignPattern.left());
        } else if (pattern instanceof RestElement) {
            // RestElement should only appear inside ArrayPattern, shouldn't reach here
            throw new RuntimeException("RestElement can only appear inside ArrayPattern");
        }
    }

    /**
     * Declare all variables in a pattern (used for for-of loops with destructuring).
     * This recursively declares variables for Identifier, ArrayPattern, and ObjectPattern.
     */
    void declarePatternVariables(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: declare it as a local variable
            ctx.currentScope().declareLocal(id.name());
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
}
