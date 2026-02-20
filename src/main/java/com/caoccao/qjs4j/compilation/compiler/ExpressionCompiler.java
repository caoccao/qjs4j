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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.regexp.RegExpLiteralValue;
import com.caoccao.qjs4j.vm.Opcode;

import java.math.BigInteger;
import java.util.List;

/**
 * Compiles expression AST nodes into bytecode.
 * Handles all expression types including binary, unary, assignment,
 * call, member access, template literals, and more.
 */
final class ExpressionCompiler {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;

    ExpressionCompiler(CompilerContext ctx, CompilerDelegates delegates) {
        this.ctx = ctx;
        this.delegates = delegates;
    }

    void compileArrayExpression(ArrayExpression arrayExpr) {
        ctx.emitter.emitOpcode(Opcode.ARRAY_NEW);

        // Check if we have any spread elements or holes
        boolean hasSpread = arrayExpr.elements().stream()
                .anyMatch(e -> e instanceof SpreadElement);
        boolean hasHoles = arrayExpr.elements().stream()
                .anyMatch(e -> e == null);

        if (!hasSpread && !hasHoles) {
            // Simple case: no spread elements, no holes - use PUSH_ARRAY
            for (Expression element : arrayExpr.elements()) {
                compileExpression(element);
                ctx.emitter.emitOpcode(Opcode.PUSH_ARRAY);
            }
        } else {
            // Complex case: has spread elements or holes
            // Following QuickJS: emit position tracking
            // Stack starts with: array
            int idx = 0;
            boolean needsIndex = false;
            boolean needsLength = false;

            for (Expression element : arrayExpr.elements()) {
                if (element instanceof SpreadElement spreadElement) {
                    // Emit index if not already on stack
                    if (!needsIndex) {
                        ctx.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                    }
                    // Compile the iterable expression
                    compileExpression(spreadElement.argument());
                    // Emit APPEND to spread elements into the array
                    // Stack: array pos iterable -> array pos
                    ctx.emitter.emitOpcode(Opcode.APPEND);
                    // After APPEND, index is updated on stack
                    needsLength = false;
                } else if (element != null) {
                    if (needsIndex) {
                        // We have index on stack, use DEFINE_ARRAY_EL
                        compileExpression(element);
                        ctx.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        ctx.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    } else {
                        // No index on stack yet
                        // Start using index-based assignment since we have holes or spread
                        ctx.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                        compileExpression(element);
                        ctx.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        ctx.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    }
                } else {
                    // Hole in array
                    if (needsIndex) {
                        // We have position on stack, just increment it
                        ctx.emitter.emitOpcode(Opcode.INC);
                    } else {
                        idx++;
                    }
                    needsLength = true;
                }
            }

            // If we have a trailing hole, set the array length explicitly
            // This handles cases like [1, 2, ,] where we need length=3 but only 2 elements
            if (needsLength) {
                if (needsIndex) {
                    // Stack: array idx
                    // QuickJS pattern: dup1 (duplicate array), put_field "length"
                    // dup1: array idx -> array array idx
                    ctx.emitter.emitOpcode(Opcode.DUP1);  // array array idx
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx (PUT_FIELD leaves value)
                    ctx.emitter.emitOpcode(Opcode.DROP);  // array
                } else {
                    // Stack: array (idx is compile-time constant)
                    // QuickJS pattern: dup, push idx, swap, put_field "length", drop
                    ctx.emitter.emitOpcode(Opcode.DUP);  // array array
                    ctx.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);  // array array idx
                    ctx.emitter.emitOpcode(Opcode.SWAP);  // array idx array
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx
                    ctx.emitter.emitOpcode(Opcode.DROP);  // array
                }
            } else if (needsIndex) {
                // No trailing hole, just drop the index
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
        }
    }

    void compileAssignmentExpression(AssignmentExpression assignExpr) {
        Expression left = assignExpr.left();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();

        // Handle logical assignment operators (&&=, ||=, ??=) with short-circuit evaluation
        if (operator == AssignmentExpression.AssignmentOperator.LOGICAL_AND_ASSIGN ||
                operator == AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN ||
                operator == AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN) {
            compileLogicalAssignment(assignExpr);
            return;
        }

        // Annex B: CallExpression as assignment target throws ReferenceError at runtime.
        // Evaluate the call expression, then throw. Don't evaluate the RHS.
        if (left instanceof CallExpression) {
            compileExpression(left);
            ctx.emitter.emitOpcode(Opcode.DROP);
            ctx.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid assignment left-hand side");
            ctx.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE
            return;
        }

        // For compound assignments (+=, -=, etc.), we need to load the current value first
        if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
            // Load current value of left side
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
                // For obj.prop += value, we need DUP2 pattern or similar
                if (ctx.isSuperMemberExpression(memberExpr)) {
                    ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
                    ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    ctx.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                    ctx.emitter.emitOpcode(Opcode.GET_SUPER);
                    delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                    ctx.emitter.emitOpcode(Opcode.DUP3); // Keep receiver/super/property for put phase
                    ctx.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
                } else {
                    compileExpression(memberExpr.object());
                    if (memberExpr.computed()) {
                        compileExpression(memberExpr.property());
                        ctx.emitter.emitOpcode(Opcode.DUP2);  // Duplicate obj and prop
                        ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        // obj.#field += value
                        String fieldName = privateId.name();
                        JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                        if (symbol != null) {
                            ctx.emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                        } else {
                            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                        }
                    } else {
                        if (memberExpr.property() instanceof Identifier propId) {
                            ctx.emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                        }
                    }
                }
            }

            // Compile right side
            compileExpression(assignExpr.right());

            // Perform the compound operation
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
            // Simple assignment
            // Compile right side
            compileExpression(assignExpr.right());
        }

        // Store the result to left side
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
            // obj[prop] = value or obj.prop = value or obj.#field = value
            if (operator == AssignmentExpression.AssignmentOperator.ASSIGN) {
                if (ctx.isSuperMemberExpression(memberExpr)) {
                    // Stack starts with [value]
                    ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
                    ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    ctx.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                    ctx.emitter.emitOpcode(Opcode.GET_SUPER);
                    delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                    ctx.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                } else {
                    // For simple assignment, compile object and property now
                    compileExpression(memberExpr.object());
                    if (memberExpr.computed()) {
                        compileExpression(memberExpr.property());
                        ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        // obj.#field = value
                        // Stack: value obj
                        // Need: obj value symbol (for PUT_PRIVATE_FIELD)
                        String fieldName = privateId.name();
                        JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                        if (symbol != null) {
                            ctx.emitter.emitOpcode(Opcode.SWAP);  // Stack: obj value
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            // Stack: obj value symbol
                            ctx.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                        } else {
                            // Error: private field not found - clean up stack and leave value
                            ctx.emitter.emitOpcode(Opcode.DROP);  // Drop obj, leaving value
                        }
                    } else if (memberExpr.property() instanceof Identifier propId) {
                        ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                    }
                }
            } else {
                // For compound assignment, object and property are already on stack from DUP
                if (ctx.isSuperMemberExpression(memberExpr)) {
                    // [receiver, super, key, newValue] -> [newValue, receiver, super, key]
                    ctx.emitter.emitOpcode(Opcode.INSERT4);
                    ctx.emitter.emitOpcode(Opcode.DROP);
                    ctx.emitter.emitOpcode(Opcode.PUT_SUPER_VALUE);
                } else if (memberExpr.computed()) {
                    ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                    // obj.#field += value
                    // Stack: obj value (from compound operation)
                    String fieldName = privateId.name();
                    JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        // Stack: obj value symbol
                        ctx.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    } else {
                        // Error: private field not found - clean up stack and leave value
                        ctx.emitter.emitOpcode(Opcode.DROP);  // Drop obj, leaving value
                    }
                } else if (memberExpr.property() instanceof Identifier propId) {
                    ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                }
            }
        } else if (left instanceof ArrayExpression arrayExpr) {
            // Destructuring array assignment: [x, y] = rhs
            // Stack: [rightValue]
            ctx.emitter.emitOpcode(Opcode.DUP);
            // Stack: [rightValue, rightValue]
            delegates.patterns.compileArrayDestructuringAssignment(arrayExpr);
            // Stack: [rightValue]
        } else if (left instanceof ObjectExpression objExpr) {
            // Destructuring object assignment: {x, y} = rhs
            // Stack: [rightValue]
            ctx.emitter.emitOpcode(Opcode.DUP);
            // Stack: [rightValue, rightValue]
            delegates.patterns.compileObjectDestructuringAssignment(objExpr);
            // Stack: [rightValue]
        }
    }

    void compileAwaitExpression(AwaitExpression awaitExpr) {
        // Compile the argument expression
        compileExpression(awaitExpr.argument());

        // Emit the AWAIT opcode
        // This will convert the value to a promise (if it isn't already)
        // and pause execution until the promise resolves
        ctx.emitter.emitOpcode(Opcode.AWAIT);
    }

    void compileBinaryExpression(BinaryExpression binExpr) {
        if (binExpr.operator() == BinaryExpression.BinaryOperator.IN &&
                binExpr.left() instanceof PrivateIdentifier privateIdentifier) {
            compilePrivateInExpression(privateIdentifier, binExpr.right());
            return;
        }

        // Short-circuit operators: must NOT evaluate right operand eagerly
        switch (binExpr.operator()) {
            case LOGICAL_AND -> {
                // left && right: if left is falsy, return left; otherwise evaluate and return right
                compileExpression(binExpr.left());
                ctx.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = ctx.emitter.emitJump(Opcode.IF_FALSE);
                ctx.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.right());
                ctx.emitter.patchJump(jumpEnd, ctx.emitter.currentOffset());
                return;
            }
            case LOGICAL_OR -> {
                // left || right: if left is truthy, return left; otherwise evaluate and return right
                compileExpression(binExpr.left());
                ctx.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = ctx.emitter.emitJump(Opcode.IF_TRUE);
                ctx.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.right());
                ctx.emitter.patchJump(jumpEnd, ctx.emitter.currentOffset());
                return;
            }
            case NULLISH_COALESCING -> {
                // left ?? right: if left is not null/undefined, return left; otherwise evaluate and return right
                compileExpression(binExpr.left());
                ctx.emitter.emitOpcode(Opcode.DUP);
                ctx.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
                int jumpEnd = ctx.emitter.emitJump(Opcode.IF_FALSE);
                ctx.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.right());
                ctx.emitter.patchJump(jumpEnd, ctx.emitter.currentOffset());
                return;
            }
            default -> {
                // Fall through to compile operands for other operators
            }
        }

        // Compile operands
        compileExpression(binExpr.left());
        compileExpression(binExpr.right());

        // Emit operation
        Opcode op = switch (binExpr.operator()) {
            case ADD -> Opcode.ADD;
            case BIT_AND -> Opcode.AND;
            case BIT_OR -> Opcode.OR;
            case BIT_XOR -> Opcode.XOR;
            case DIV -> Opcode.DIV;
            case EQ -> Opcode.EQ;
            case EXP -> Opcode.EXP;
            case GE -> Opcode.GTE;
            case GT -> Opcode.GT;
            case IN -> Opcode.IN;
            case INSTANCEOF -> Opcode.INSTANCEOF;
            case LE -> Opcode.LTE;
            case LSHIFT -> Opcode.SHL;
            case LT -> Opcode.LT;
            case MOD -> Opcode.MOD;
            case MUL -> Opcode.MUL;
            case NE -> Opcode.NEQ;
            case RSHIFT -> Opcode.SAR;
            case STRICT_EQ -> Opcode.STRICT_EQ;
            case STRICT_NE -> Opcode.STRICT_NEQ;
            case SUB -> Opcode.SUB;
            case URSHIFT -> Opcode.SHR;
            // LOGICAL_AND, LOGICAL_OR, NULLISH_COALESCING handled above with short-circuit evaluation
            default -> throw new JSCompilerException("Unknown binary operator: " + binExpr.operator());
        };

        ctx.emitter.emitOpcode(op);
    }

    void compileCallExpression(CallExpression callExpr) {
        // Check if any arguments contain spread
        boolean hasSpread = callExpr.arguments().stream()
                .anyMatch(arg -> arg instanceof SpreadElement);

        if (hasSpread) {
            // Use APPLY for calls with spread arguments
            compileCallExpressionWithSpread(callExpr);
        } else {
            // Use regular CALL for calls without spread
            compileCallExpressionRegular(callExpr);
        }
    }

    void compileCallExpressionRegular(CallExpression callExpr) {
        // Check for super() call in derived constructor
        if (callExpr.callee() instanceof Identifier calleeId && "super".equals(calleeId.name())) {
            // super(args) — constructor call with propagated new.target.
            // Stack for APPLY constructor mode: newTarget, superConstructor, argsArray
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(3); // SPECIAL_OBJECT_NEW_TARGET
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(2); // SPECIAL_OBJECT_THIS_FUNC
            ctx.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            ctx.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            ctx.emitter.emitOpcode(Opcode.INIT_CTOR);
            return;
        }
        // Check if this is a method call (callee is a member expression)
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            if (ctx.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                // Stack: receiver method -> method receiver
                ctx.emitter.emitOpcode(Opcode.SWAP);
                for (Expression arg : callExpr.arguments()) {
                    compileExpression(arg);
                }
                ctx.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
                return;
            }
            // For method calls: obj.method()
            // We need to preserve obj as the 'this' value

            // Push object (receiver)
            compileExpression(memberExpr.object());

            // Duplicate it (one copy for 'this', one for property access)
            ctx.emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                // obj[expr]
                compileExpression(memberExpr.property());
                ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                // obj.#privateField
                // Stack: obj obj (obj is already duplicated)
                // Need to get the private symbol and call GET_PRIVATE_FIELD
                String fieldName = privateId.name();
                JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    // Stack: obj obj symbol
                    ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                    // Stack: obj method (GET_PRIVATE_FIELD pops symbol and one obj, pushes method)
                } else {
                    // Error: private field not found
                    // Stack: obj obj -> need to drop one obj and push undefined
                    ctx.emitter.emitOpcode(Opcode.DROP);  // Drop the duplicated obj
                    ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                    // Stack: obj undefined
                }
            } else if (memberExpr.property() instanceof Identifier propId) {
                // obj.prop
                ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            // Now stack is: receiver, method
            // Swap so method is on top: method, receiver
            ctx.emitter.emitOpcode(Opcode.SWAP);

            // Push arguments
            for (Expression arg : callExpr.arguments()) {
                compileExpression(arg);
            }

            // Call with argument count (will use receiver as thisArg)
            ctx.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        } else {
            // Regular function call: func()
            // Push callee
            compileExpression(callExpr.callee());

            // Push undefined as receiver (thisArg for regular calls)
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);

            // Push arguments
            for (Expression arg : callExpr.arguments()) {
                compileExpression(arg);
            }

            // Call with argument count
            ctx.emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        }
    }

    void compileCallExpressionWithSpread(CallExpression callExpr) {
        // For calls with spread: func(...args) or obj.method(...args)
        // Strategy: Build an arguments array and use APPLY

        // Determine thisArg and function
        if (callExpr.callee() instanceof Identifier calleeId && "super".equals(calleeId.name())) {
            // super(...args) — constructor call with propagated new.target.
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(3); // SPECIAL_OBJECT_NEW_TARGET
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(2); // SPECIAL_OBJECT_THIS_FUNC
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
                // Stack: thisArg function argsArray
                ctx.emitter.emitOpcodeU16(Opcode.APPLY, 0);
                return;
            }
            // Method call: obj.method(...args)
            // Stack should be: thisArg function argsArray

            // Push object (will be thisArg)
            compileExpression(memberExpr.object());

            // Duplicate it for getting the method
            ctx.emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                compileExpression(memberExpr.property());
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

            // Stack: thisArg function
        } else {
            // Regular function call: func(...args)
            // Push undefined as thisArg
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);

            // Push function
            compileExpression(callExpr.callee());

            // Stack: thisArg function
        }

        delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());

        // Stack: thisArg function argsArray
        // Use APPLY to call with the array
        // Parameter: isConstructorCall (0 for regular call, 1 for new)
        ctx.emitter.emitOpcodeU16(Opcode.APPLY, 0);
    }

    void compileConditionalExpression(ConditionalExpression condExpr) {
        // Compile test
        compileExpression(condExpr.test());

        // Jump to alternate if false
        int jumpToAlternate = ctx.emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent
        compileExpression(condExpr.consequent());

        // Jump over alternate
        int jumpToEnd = ctx.emitter.emitJump(Opcode.GOTO);

        // Patch jump to alternate
        ctx.emitter.patchJump(jumpToAlternate, ctx.emitter.currentOffset());

        // Compile alternate
        compileExpression(condExpr.alternate());

        // Patch jump to end
        ctx.emitter.patchJump(jumpToEnd, ctx.emitter.currentOffset());
    }

    void compileExpression(Expression expr) {
        if (expr instanceof Literal literal) {
            compileLiteral(literal);
        } else if (expr instanceof Identifier id) {
            compileIdentifier(id);
        } else if (expr instanceof PrivateIdentifier privateIdentifier) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.name() + "'");
        } else if (expr instanceof BinaryExpression binExpr) {
            compileBinaryExpression(binExpr);
        } else if (expr instanceof UnaryExpression unaryExpr) {
            compileUnaryExpression(unaryExpr);
        } else if (expr instanceof AssignmentExpression assignExpr) {
            compileAssignmentExpression(assignExpr);
        } else if (expr instanceof ConditionalExpression condExpr) {
            compileConditionalExpression(condExpr);
        } else if (expr instanceof CallExpression callExpr) {
            compileCallExpression(callExpr);
        } else if (expr instanceof MemberExpression memberExpr) {
            compileMemberExpression(memberExpr);
        } else if (expr instanceof NewExpression newExpr) {
            compileNewExpression(newExpr);
        } else if (expr instanceof FunctionExpression funcExpr) {
            delegates.functions.compileFunctionExpression(funcExpr);
        } else if (expr instanceof ArrowFunctionExpression arrowExpr) {
            delegates.functions.compileArrowFunctionExpression(arrowExpr);
        } else if (expr instanceof AwaitExpression awaitExpr) {
            compileAwaitExpression(awaitExpr);
        } else if (expr instanceof YieldExpression yieldExpr) {
            compileYieldExpression(yieldExpr);
        } else if (expr instanceof ArrayExpression arrayExpr) {
            compileArrayExpression(arrayExpr);
        } else if (expr instanceof ObjectExpression objExpr) {
            compileObjectExpression(objExpr);
        } else if (expr instanceof TemplateLiteral templateLiteral) {
            compileTemplateLiteral(templateLiteral);
        } else if (expr instanceof TaggedTemplateExpression taggedTemplate) {
            compileTaggedTemplateExpression(taggedTemplate);
        } else if (expr instanceof ClassExpression classExpr) {
            delegates.functions.compileClassExpression(classExpr);
        } else if (expr instanceof SequenceExpression seqExpr) {
            compileSequenceExpression(seqExpr);
        }
    }

    void compileIdentifier(Identifier id) {
        String name = id.name();

        // Handle 'this' keyword
        if ("this".equals(name)) {
            ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
            return;
        }

        // Always check local scopes first, even in global scope (for nested blocks/loops)
        // This must happen BEFORE the 'arguments' special handling so that
        // explicit `var arguments` or `let arguments` declarations take precedence.
        // Following QuickJS: arguments is resolved through normal variable lookup first.
        Integer localIndex = ctx.findLocalInScopes(name);

        if (localIndex != null) {
            // Use GET_LOC_CHECK for TDZ locals (let/const/class in program scope)
            // to throw ReferenceError if accessed before initialization
            if (ctx.tdzLocals.contains(name)) {
                ctx.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
            } else {
                ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
            }
            return;
        }

        // Handle 'arguments' keyword in function scope (only if not found as a local)
        // For regular functions: SPECIAL_OBJECT creates the arguments object
        // For arrow functions with enclosing regular function: SPECIAL_OBJECT walks up call stack
        // For arrow functions without enclosing regular function: resolve as normal variable
        // Following QuickJS: arrow functions inherit arguments from enclosing scope,
        // but only if there is an enclosing scope with arguments binding
        if (JSArguments.NAME.equals(name) && !ctx.inGlobalScope
                && (!ctx.isInArrowFunction || ctx.hasEnclosingArgumentsBinding)) {
            // Emit SPECIAL_OBJECT opcode with type 0 (SPECIAL_OBJECT_ARGUMENTS)
            // The VM will handle differently for arrow vs regular functions
            ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            ctx.emitter.emitU8(0);  // Type 0 = arguments object
            return;
        }

        Integer capturedIndex = ctx.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            ctx.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
        } else {
            // Not found in local scopes, use global variable
            ctx.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
        }
    }

    void compileLiteral(Literal literal) {
        Object value = literal.value();

        if (value == null) {
            ctx.emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            ctx.emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
        } else if (value instanceof BigInteger bigInt) {
            // Check BigInteger before Number since BigInteger extends Number.
            // Match QuickJS: emit PUSH_BIGINT_I32 when the literal fits in signed i32.
            if (bigInt.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bigInt.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                ctx.emitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
                ctx.emitter.emitI32(bigInt.intValue());
            } else {
                ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            }
        } else if (value instanceof Number num) {
            // Try to emit as i32 if it's an integer in range
            if (num instanceof Integer || num instanceof Long) {
                long longValue = num.longValue();
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    ctx.emitter.emitOpcode(Opcode.PUSH_I32);
                    ctx.emitter.emitI32((int) longValue);
                    return;
                }
            }
            // Otherwise emit as constant
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(num.doubleValue()));
        } else if (value instanceof RegExpLiteralValue regExpLiteralValue) {
            String source = regExpLiteralValue.source();
            int lastSlash = source.lastIndexOf('/');
            if (lastSlash > 0) {
                String pattern = source.substring(1, lastSlash);
                String flags = lastSlash < source.length() - 1 ? source.substring(lastSlash + 1) : "";
                try {
                    JSRegExp regexp = new JSRegExp(pattern, flags);
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, regexp);
                    return;
                } catch (Exception e) {
                    throw new JSCompilerException("Invalid regular expression literal: " + source);
                }
            }
            throw new JSCompilerException("Invalid regular expression literal: " + source);
        } else if (value instanceof String str) {
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
        } else {
            // Other types as constants
            throw new JSCompilerException("Unsupported literal type: " + value.getClass());
        }
    }

    /**
     * Compile logical assignment operators (&&=, ||=, ??=) with short-circuit evaluation.
     * Based on QuickJS implementation in quickjs.c (lines 27635-27690).
     * <p>
     * For a ??= b:
     * 1. Load current value of a (with DUP for member expressions)
     * 2. Duplicate it
     * 3. Check if it's null or undefined
     * 4. If not null/undefined, jump to end (keep current value, cleanup lvalue stack)
     * 5. If null/undefined, drop duplicate, evaluate b, insert below lvalue, assign, jump to end2
     * 6. At end: cleanup lvalue stack with NIP operations
     * 7. At end2: continue
     * <p>
     * For a &&= b:
     * Similar but check for falsy
     * <p>
     * For a ||= b:
     * Similar but check for truthy
     */
    void compileLogicalAssignment(AssignmentExpression assignExpr) {
        Expression left = assignExpr.left();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();

        // Determine the depth of lvalue for proper stack manipulation
        // depth 0 = identifier, depth 1 = obj.prop, depth 2 = obj[prop]
        int depthLvalue;
        if (left instanceof Identifier) {
            depthLvalue = 0;
        } else if (left instanceof MemberExpression memberExpr) {
            depthLvalue = ctx.isSuperMemberExpression(memberExpr) ? 3 : (memberExpr.computed() ? 2 : 1);
        } else {
            throw new JSCompilerException("Invalid left-hand side in logical assignment");
        }

        // Load the current value
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
                ctx.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                ctx.emitter.emitOpcode(Opcode.GET_SUPER);
                delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
                ctx.emitter.emitOpcode(Opcode.DUP3);
                ctx.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
            } else {
                compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    compileExpression(memberExpr.property());
                    ctx.emitter.emitOpcode(Opcode.DUP2);  // Duplicate obj and prop
                    ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else {
                    if (memberExpr.property() instanceof Identifier propId) {
                        ctx.emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                        ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                    }
                }
            }
        }

        // Duplicate the current value for the test
        ctx.emitter.emitOpcode(Opcode.DUP);

        // Emit the test based on operator type
        int jumpToCleanup;
        if (operator == AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN) {
            // For ??=, check if null or undefined
            ctx.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            // Jump to cleanup if NOT null/undefined (value is on stack)
            jumpToCleanup = ctx.emitter.emitJump(Opcode.IF_FALSE);
        } else if (operator == AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN) {
            // For ||=, jump to cleanup if truthy
            jumpToCleanup = ctx.emitter.emitJump(Opcode.IF_TRUE);
        } else { // LOGICAL_AND_ASSIGN
            // For &&=, jump to cleanup if falsy
            jumpToCleanup = ctx.emitter.emitJump(Opcode.IF_FALSE);
        }

        // The current value didn't meet the condition, so we assign the new value
        // The boolean was already popped by IF_FALSE
        // Drop the old value
        ctx.emitter.emitOpcode(Opcode.DROP);

        // Compile the right-hand side expression
        compileExpression(assignExpr.right());

        // Insert the new value below the lvalue on stack for proper assignment
        // This matches QuickJS's OP_insert2, OP_insert3, OP_insert4 pattern
        // INSERT2: [a, b] -> [b, a, b]
        // INSERT3: [a, b, c] -> [c, a, b, c]
        // INSERT4: [a, b, c, d] -> [d, a, b, c, d]
        switch (depthLvalue) {
            case 0 -> {
                // For identifier: SET_VAR/SET_LOCAL/SET_VAR_REF all use peek(0),
                // so the value is already kept on the stack. No DUP needed.
            }
            case 1 -> {
                // For obj.prop: stack is [obj, newValue]
                // We need: [newValue, obj] for PUT_FIELD
                // PUT_FIELD pops obj, peeks newValue, leaves newValue on stack
                ctx.emitter.emitOpcode(Opcode.SWAP);
            }
            case 2 -> {
                // For obj[prop]: stack is [obj, prop, newValue]
                // We need: [newValue, obj, prop] for PUT_ARRAY_EL
                // ROT3R rotates right: [a, b, c] -> [c, a, b]
                // So [obj, prop, newValue] -> [newValue, obj, prop]
                ctx.emitter.emitOpcode(Opcode.ROT3R);
            }
            case 3 -> {
                // For super[prop]: stack is [receiver, superObj, key, newValue]
                // We need: [newValue, receiver, superObj, key] for PUT_SUPER_VALUE
                ctx.emitter.emitOpcode(Opcode.INSERT4);
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
            default -> throw new JSCompilerException("Invalid depth for logical assignment");
        }

        // Store the result to left side
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

        // Jump over the cleanup code
        int jumpToEnd = ctx.emitter.emitJump(Opcode.GOTO);

        // Patch the jump to cleanup - if we took this branch, we need to cleanup lvalue stack
        ctx.emitter.patchJump(jumpToCleanup, ctx.emitter.currentOffset());

        // Remove the lvalue stack entries using NIP
        // NIP removes the value below the top, keeping the top value
        // For depth 0 (identifier): no cleanup needed
        // For depth 1 (obj.prop): NIP removes obj, keeps the value
        // For depth 2 (obj[prop]): NIP twice removes obj and prop, keeps the value
        for (int i = 0; i < depthLvalue; i++) {
            ctx.emitter.emitOpcode(Opcode.NIP);
        }

        // Patch the jump to end - both paths converge here
        ctx.emitter.patchJump(jumpToEnd, ctx.emitter.currentOffset());
    }

    void compileMemberExpression(MemberExpression memberExpr) {
        if (ctx.isSuperMemberExpression(memberExpr)) {
            delegates.emitHelpers.emitGetSuperValue(memberExpr, false);
            return;
        }

        compileExpression(memberExpr.object());

        if (memberExpr.computed()) {
            // obj[expr]
            compileExpression(memberExpr.property());
            ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
            // obj.#privateField
            // Stack: obj
            // Need: obj symbol
            String fieldName = privateId.name();
            JSSymbol symbol = ctx.privateSymbols.get(fieldName);
            if (symbol != null) {
                ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                // Stack: obj symbol
                ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                // Stack: value
            } else {
                // Error: private field not found
                // For now, just emit undefined
                ctx.emitter.emitOpcode(Opcode.DROP);  // Drop obj
                ctx.emitter.emitOpcode(Opcode.UNDEFINED);
            }
        } else if (memberExpr.property() instanceof Identifier propId) {
            // obj.prop
            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
        }
    }

    void compileNewExpression(NewExpression newExpr) {
        boolean hasSpread = newExpr.arguments().stream()
                .anyMatch(arg -> arg instanceof SpreadElement);

        // Push constructor
        compileExpression(newExpr.callee());

        if (hasSpread) {
            // QuickJS `OP_apply` constructor path: thisArg/newTarget, function, argsArray.
            ctx.emitter.emitOpcode(Opcode.DUP);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(newExpr.arguments());
            ctx.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            return;
        }

        for (Expression arg : newExpr.arguments()) {
            compileExpression(arg);
        }
        ctx.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.arguments().size());
    }

    void compileObjectExpression(ObjectExpression objExpr) {
        ctx.emitter.emitOpcode(Opcode.OBJECT_NEW);

        for (ObjectExpression.Property prop : objExpr.properties()) {
            String kind = prop.kind();

            if ("get".equals(kind) || "set".equals(kind)) {
                // Getter/setter property: use DEFINE_METHOD_COMPUTED
                // Stack: obj -> obj key method -> obj
                // Push key
                if (prop.computed()) {
                    compileExpression(prop.key());
                } else if (prop.key() instanceof Identifier id) {
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
                } else {
                    compileExpression(prop.key());
                }

                // Compile the getter/setter function
                delegates.functions.compileFunctionExpression((FunctionExpression) prop.value());

                // DEFINE_METHOD_COMPUTED with flags: kind (1=get, 2=set) | enumerable (4)
                int methodKind = "get".equals(kind) ? 1 : 2;
                int flags = methodKind | 4; // enumerable = true for object literal properties
                ctx.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, flags);
            } else {
                // Regular property: key: value
                // Push key
                if (prop.key() instanceof Identifier id && !prop.computed()) {
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
                } else {
                    compileExpression(prop.key());
                }

                // Push value
                compileExpression(prop.value());

                // Define property
                ctx.emitter.emitOpcode(Opcode.DEFINE_PROP);
            }
        }
    }

    void compilePrivateInExpression(PrivateIdentifier privateIdentifier, Expression right) {
        compileExpression(right);

        JSSymbol symbol = ctx.privateSymbols != null ? ctx.privateSymbols.get(privateIdentifier.name()) : null;
        if (symbol == null) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.name() + "'");
        }

        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
        ctx.emitter.emitOpcode(Opcode.PRIVATE_IN);
    }

    void compileSequenceExpression(SequenceExpression seqExpr) {
        // Following QuickJS: evaluate each expression in order,
        // dropping all but the last one's value
        List<Expression> expressions = seqExpr.expressions();

        for (int i = 0; i < expressions.size(); i++) {
            compileExpression(expressions.get(i));

            // Drop the value of all expressions except the last one
            if (i < expressions.size() - 1) {
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
        }
        // The last expression's value remains on the stack
    }

    void compileTaggedTemplateExpression(TaggedTemplateExpression taggedTemplate) {
        // Tagged template: tag`template`
        // The tag function receives:
        // 1. A template object (array-like) with cooked strings and a 'raw' property
        // 2. The values of the substitutions as additional arguments

        TemplateLiteral template = taggedTemplate.quasi();
        List<Expression> expressions = template.expressions();

        // Check if this is a method call (tag is a member expression)
        if (taggedTemplate.tag() instanceof MemberExpression memberExpr) {
            // For method calls: obj.method`template`
            // We need to preserve obj as the 'this' value

            // Push object (receiver)
            compileExpression(memberExpr.object());

            // Duplicate it (one copy for 'this', one for property access)
            ctx.emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                // obj[expr]
                compileExpression(memberExpr.property());
                ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                // obj.prop
                ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            // Now stack is: receiver, method
            // Swap so method is on top: method, receiver
            ctx.emitter.emitOpcode(Opcode.SWAP);
        } else {
            // Regular function call: func`template`
            // Compile the tag function first (will be the callee)
            compileExpression(taggedTemplate.tag());

            // Add undefined as receiver/thisArg
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // Stack is now: function, receiver

        // QuickJS behavior: each call site uses a stable, frozen template object.
        // Build it once in the constant pool and pass it as the first argument.
        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, delegates.functions.createTaggedTemplateObject(template));
        // Stack: function, receiver, template_object

        // Add substitution expressions as additional arguments
        for (Expression expr : expressions) {
            compileExpression(expr);
        }

        // Call the tag function
        // argCount = 1 (template array) + number of expressions
        int argCount = 1 + expressions.size();
        ctx.emitter.emitOpcode(Opcode.CALL);
        ctx.emitter.emitU16(argCount);
    }

    void compileTemplateLiteral(TemplateLiteral templateLiteral) {
        // For untagged template literals, concatenate strings and expressions
        // Example: `Hello ${name}!` becomes "Hello " + name + "!"

        List<String> quasis = templateLiteral.quasis();
        List<Expression> expressions = templateLiteral.expressions();

        if (quasis.isEmpty()) {
            // Empty template literal
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(""));
            return;
        }

        // Start with the first quasi
        String firstQuasi = quasis.get(0);
        if (firstQuasi == null) {
            throw new JSCompilerException("Invalid escape sequence in untagged template literal");
        }
        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(firstQuasi));

        // Add each expression and subsequent quasi using string concatenation (ADD)
        for (int i = 0; i < expressions.size(); i++) {
            // Compile the expression
            compileExpression(expressions.get(i));

            // Concatenate using ADD opcode (JavaScript + operator)
            ctx.emitter.emitOpcode(Opcode.ADD);

            // Add the next quasi if it exists
            if (i + 1 < quasis.size()) {
                String quasi = quasis.get(i + 1);
                if (quasi == null) {
                    throw new JSCompilerException("Invalid escape sequence in untagged template literal");
                }
                if (!quasi.isEmpty()) {
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
                    ctx.emitter.emitOpcode(Opcode.ADD);
                }
            }
        }
    }

    void compileUnaryExpression(UnaryExpression unaryExpr) {
        // DELETE operator needs special handling - it doesn't evaluate the operand,
        // but instead emits object and property separately
        if (unaryExpr.operator() == UnaryExpression.UnaryOperator.DELETE) {
            Expression operand = unaryExpr.operand();

            if (operand instanceof MemberExpression memberExpr) {
                // delete obj.prop or delete obj[expr]
                compileExpression(memberExpr.object());

                if (memberExpr.computed()) {
                    // obj[expr]
                    compileExpression(memberExpr.property());
                } else if (memberExpr.property() instanceof Identifier propId) {
                    // obj.prop
                    ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.name()));
                }

                ctx.emitter.emitOpcode(Opcode.DELETE);
            } else if (operand instanceof Identifier id) {
                // Match QuickJS scope_delete_var lowering:
                // - local/arg/closure/implicit arguments bindings => false
                // - unresolved/global binding => DELETE_VAR runtime check
                boolean isLocalBinding = ctx.findLocalInScopes(id.name()) != null
                        || ctx.resolveCapturedBindingIndex(id.name()) != null
                        || (JSArguments.NAME.equals(id.name()) && !ctx.inGlobalScope)
                        || ctx.nonDeletableGlobalBindings.contains(id.name());
                if (isLocalBinding) {
                    ctx.emitter.emitOpcode(Opcode.PUSH_FALSE);
                } else {
                    ctx.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, id.name());
                }
            } else {
                // delete literal / non-reference expression => true
                ctx.emitter.emitOpcode(Opcode.PUSH_TRUE);
            }
            return;
        }

        // INC and DEC operators - following QuickJS pattern:
        // 1. Compile get_lvalue (loads current value)
        // 2. Apply INC/DEC (prefix) or POST_INC/POST_DEC (postfix)
        // 3. Apply put_lvalue (stores with appropriate stack manipulation)
        if (unaryExpr.operator() == UnaryExpression.UnaryOperator.INC ||
                unaryExpr.operator() == UnaryExpression.UnaryOperator.DEC) {
            Expression operand = unaryExpr.operand();
            boolean isInc = unaryExpr.operator() == UnaryExpression.UnaryOperator.INC;
            boolean isPrefix = unaryExpr.prefix();

            if (operand instanceof Identifier id) {
                // Simple variable: get, inc/dec, set/put
                compileExpression(operand);
                ctx.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                        : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                Integer localIndex = ctx.findLocalInScopes(id.name());
                if (localIndex != null) {
                    ctx.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_LOCAL : Opcode.PUT_LOCAL, localIndex);
                } else {
                    Integer capturedIndex = ctx.resolveCapturedBindingIndex(id.name());
                    if (capturedIndex != null) {
                        ctx.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_VAR_REF : Opcode.PUT_VAR_REF, capturedIndex);
                    } else {
                        ctx.emitter.emitOpcodeAtom(isPrefix ? Opcode.SET_VAR : Opcode.PUT_VAR, id.name());
                    }
                }
            } else if (operand instanceof MemberExpression memberExpr) {
                if (memberExpr.computed()) {
                    // Array element: obj[prop]
                    compileExpression(memberExpr.object());
                    compileExpression(memberExpr.property());

                    if (isPrefix) {
                        // Prefix: ++arr[i] - returns new value
                        ctx.emitter.emitOpcode(Opcode.DUP2);
                        ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                        ctx.emitter.emitOpcode(Opcode.PLUS); // ToNumber conversion
                        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                        ctx.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                        // Stack: [obj, prop, new_val] -> need [new_val, obj, prop]
                        ctx.emitter.emitOpcode(Opcode.ROT3R);
                        ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else {
                        // Postfix: arr[i]++ - returns old value (must be ToNumber'd per ES spec)
                        ctx.emitter.emitOpcode(Opcode.DUP2); // obj prop obj prop
                        ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL); // obj prop old_val
                        ctx.emitter.emitOpcode(Opcode.PLUS); // obj prop old_numeric (ToNumber conversion)
                        ctx.emitter.emitOpcode(Opcode.DUP); // obj prop old_numeric old_numeric
                        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                        ctx.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj prop old_val new_val
                        // SWAP2 to rearrange: [obj, prop, old_val, new_val] -> [old_val, new_val, obj, prop]
                        ctx.emitter.emitOpcode(Opcode.SWAP2); // old_val new_val obj prop
                        ctx.emitter.emitOpcode(Opcode.PUT_ARRAY_EL); // old_val new_val
                        ctx.emitter.emitOpcode(Opcode.DROP); // old_val
                    }
                } else {
                    // Object property: obj.prop or obj.#field
                    if (memberExpr.property() instanceof Identifier propId) {
                        compileExpression(memberExpr.object());

                        if (isPrefix) {
                            // Prefix: ++obj.prop - returns new value
                            ctx.emitter.emitOpcode(Opcode.DUP);
                            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                            ctx.emitter.emitOpcode(Opcode.PLUS); // ToNumber conversion
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            ctx.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                            // Stack: [obj, new_val] -> need [new_val, obj] for PUT_FIELD
                            ctx.emitter.emitOpcode(Opcode.SWAP);
                            // PUT_FIELD pops obj, peeks new_val, leaves [new_val]
                            ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                        } else {
                            // Postfix: obj.prop++ - returns old value (must be ToNumber'd per ES spec)
                            ctx.emitter.emitOpcode(Opcode.DUP); // obj obj
                            ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name()); // obj old_val
                            ctx.emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            ctx.emitter.emitOpcode(Opcode.DUP); // obj old_numeric old_numeric
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            ctx.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj old_val new_val
                            // Stack: [obj, old_val, new_val] - need [old_val, new_val, obj] for PUT_FIELD
                            // ROT3L: [old_val, new_val, obj]
                            ctx.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            // PUT_FIELD pops obj, peeks new_val, leaves [old_val, new_val]
                            ctx.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name()); // old_val new_val
                            ctx.emitter.emitOpcode(Opcode.DROP); // old_val
                        }
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        // Private field: obj.#field
                        String fieldName = privateId.name();
                        JSSymbol symbol = ctx.privateSymbols.get(fieldName);
                        if (symbol == null) {
                            throw new JSCompilerException("Private field not found: #" + fieldName);
                        }

                        compileExpression(memberExpr.object());

                        if (isPrefix) {
                            // Prefix: ++obj.#field - returns new value
                            ctx.emitter.emitOpcode(Opcode.DUP); // obj obj
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            ctx.emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            ctx.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj new_val
                            ctx.emitter.emitOpcode(Opcode.DUP); // obj new_val new_val
                            ctx.emitter.emitOpcode(Opcode.ROT3R); // new_val obj new_val
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // new_val obj new_val symbol
                            ctx.emitter.emitOpcode(Opcode.SWAP); // new_val obj symbol new_val
                            ctx.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // new_val
                        } else {
                            // Postfix: obj.#field++ - returns old value (must be ToNumber'd per ES spec)
                            ctx.emitter.emitOpcode(Opcode.DUP); // obj obj
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            ctx.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            ctx.emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            ctx.emitter.emitOpcode(Opcode.DUP); // obj old_numeric old_numeric
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            ctx.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj old_val new_val
                            ctx.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // old_val new_val obj symbol
                            // Need: obj symbol new_val for PUT_PRIVATE_FIELD
                            // Have: old_val new_val obj symbol
                            // SWAP to get: old_val new_val symbol obj
                            ctx.emitter.emitOpcode(Opcode.SWAP); // old_val new_val symbol obj
                            // ROT3L to get: old_val obj symbol new_val
                            ctx.emitter.emitOpcode(Opcode.ROT3L); // old_val obj symbol new_val
                            ctx.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // old_val
                        }
                    } else {
                        throw new JSCompilerException("Invalid member expression property for increment/decrement");
                    }
                }
            } else if (operand instanceof CallExpression) {
                // Annex B: CallExpression as increment/decrement target throws ReferenceError at runtime.
                compileExpression(operand);
                ctx.emitter.emitOpcode(Opcode.DROP);
                ctx.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid increment/decrement operand");
                ctx.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE
            } else {
                throw new JSCompilerException("Invalid operand for increment/decrement operator");
            }
            return;
        }

        if (unaryExpr.operator() == UnaryExpression.UnaryOperator.TYPEOF
                && unaryExpr.operand() instanceof Identifier id) {
            String name = id.name();
            if ("this".equals(name)) {
                ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
            } else if (JSArguments.NAME.equals(name) && !ctx.inGlobalScope
                    && (!ctx.isInArrowFunction || ctx.hasEnclosingArgumentsBinding)
                    && ctx.findLocalInScopes(name) == null) {
                ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                ctx.emitter.emitU8(0);
            } else {
                Integer localIndex = ctx.findLocalInScopes(name);
                if (localIndex != null) {
                    // Use GET_LOC_CHECK for TDZ locals - typeof of an uninitialized
                    // lexical binding throws ReferenceError per ES spec
                    if (ctx.tdzLocals.contains(name)) {
                        ctx.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
                    } else {
                        ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
                    }
                } else {
                    Integer capturedIndex = ctx.resolveCapturedBindingIndex(name);
                    if (capturedIndex != null) {
                        ctx.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                    } else {
                        ctx.emitter.emitOpcodeAtom(Opcode.GET_VAR, "globalThis");
                        ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
                    }
                }
            }
            ctx.emitter.emitOpcode(Opcode.TYPEOF);
            return;
        }

        compileExpression(unaryExpr.operand());

        Opcode op = switch (unaryExpr.operator()) {
            case BIT_NOT -> Opcode.NOT;
            case MINUS -> Opcode.NEG;
            case NOT -> Opcode.LOGICAL_NOT;
            case PLUS -> Opcode.PLUS;
            case TYPEOF -> Opcode.TYPEOF;
            case VOID -> {
                ctx.emitter.emitOpcode(Opcode.DROP);
                yield Opcode.UNDEFINED;
            }
            default -> throw new JSCompilerException("Unknown unary operator: " + unaryExpr.operator());
        };

        ctx.emitter.emitOpcode(op);
    }

    void compileYieldExpression(YieldExpression yieldExpr) {
        // Compile the argument expression (if present)
        if (yieldExpr.argument() != null) {
            compileExpression(yieldExpr.argument());
        } else {
            // No argument means yield undefined
            ctx.emitter.emitConstant(null);
        }

        // Emit the appropriate yield opcode
        if (yieldExpr.delegate()) {
            // yield* delegates to another generator/iterable
            ctx.emitter.emitOpcode(ctx.isInAsyncFunction ? Opcode.ASYNC_YIELD_STAR : Opcode.YIELD_STAR);
        } else {
            // Regular yield
            ctx.emitter.emitOpcode(Opcode.YIELD);
        }
    }
}
