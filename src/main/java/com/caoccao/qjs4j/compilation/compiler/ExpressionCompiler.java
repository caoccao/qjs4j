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
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
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
    private final ExpressionAssignmentCompiler assignmentCompiler;
    private final ExpressionCallMemberCompiler callMemberCompiler;
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;

    ExpressionCompiler(CompilerContext compilerContext, CompilerDelegates delegates) {
        this.compilerContext = compilerContext;
        this.delegates = delegates;
        assignmentCompiler = new ExpressionAssignmentCompiler(this, compilerContext, delegates);
        callMemberCompiler = new ExpressionCallMemberCompiler(this, compilerContext, delegates);
    }

    void compileArrayExpression(ArrayExpression arrayExpr) {
        compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);

        // Check if we have any spread elements or holes
        boolean hasSpread = arrayExpr.getElements().stream()
                .anyMatch(e -> e instanceof SpreadElement);
        boolean hasHoles = arrayExpr.getElements().stream()
                .anyMatch(e -> e == null);

        if (!hasSpread && !hasHoles) {
            // Simple case: no spread elements, no holes
            compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, 0);
            for (Expression element : arrayExpr.getElements()) {
                compileExpression(element);
                compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                compilerContext.emitter.emitOpcode(Opcode.INC);
            }
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else {
            // Complex case: has spread elements or holes
            // Following QuickJS: emit position tracking
            // Stack starts with: array
            int idx = 0;
            boolean needsIndex = false;
            boolean needsLength = false;

            for (Expression element : arrayExpr.getElements()) {
                if (element instanceof SpreadElement spreadElement) {
                    // Emit index if not already on stack
                    if (!needsIndex) {
                        compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                    }
                    // Compile the iterable expression
                    compileExpression(spreadElement.getArgument());
                    // Emit APPEND to spread elements into the array
                    // Stack: array pos iterable -> array pos
                    compilerContext.emitter.emitOpcode(Opcode.APPEND);
                    // After APPEND, index is updated on stack
                    needsLength = false;
                } else if (element != null) {
                    if (needsIndex) {
                        // We have index on stack, use DEFINE_ARRAY_EL
                        compileExpression(element);
                        compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        compilerContext.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    } else {
                        // No index on stack yet
                        // Start using index-based assignment since we have holes or spread
                        compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                        compileExpression(element);
                        compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        compilerContext.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    }
                } else {
                    // Hole in array
                    if (needsIndex) {
                        // We have position on stack, just increment it
                        compilerContext.emitter.emitOpcode(Opcode.INC);
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
                    compilerContext.emitter.emitOpcode(Opcode.DUP1);  // array array idx
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx (PUT_FIELD leaves value)
                    compilerContext.emitter.emitOpcode(Opcode.DROP);  // array
                } else {
                    // Stack: array (idx is compile-time constant)
                    // QuickJS pattern: dup, push idx, swap, put_field "length", drop
                    compilerContext.emitter.emitOpcode(Opcode.DUP);  // array array
                    compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);  // array array idx
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);  // array idx array
                    compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx
                    compilerContext.emitter.emitOpcode(Opcode.DROP);  // array
                }
            } else if (needsIndex) {
                // No trailing hole, just drop the index
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }
    }

    void compileAssignmentExpression(AssignmentExpression assignExpr) {
        assignmentCompiler.compileAssignmentExpression(assignExpr);
    }

    void compileAwaitExpression(AwaitExpression awaitExpr) {
        // Compile the argument expression
        compileExpression(awaitExpr.getArgument());

        // Emit the AWAIT opcode
        // This will convert the value to a promise (if it isn't already)
        // and pause execution until the promise resolves
        compilerContext.emitter.emitOpcode(Opcode.AWAIT);
    }

    void compileBinaryExpression(BinaryExpression binExpr) {
        if (binExpr.getOperator() == BinaryOperator.IN &&
                binExpr.getLeft() instanceof PrivateIdentifier privateIdentifier) {
            compilePrivateInExpression(privateIdentifier, binExpr.getRight());
            return;
        }

        // Short-circuit operators: must NOT evaluate right operand eagerly
        switch (binExpr.getOperator()) {
            case LOGICAL_AND -> {
                // left && right: if left is falsy, return left; otherwise evaluate and return right
                boolean savedTailCalls = compilerContext.emitTailCalls;
                compilerContext.emitTailCalls = false;
                compileExpression(binExpr.getLeft());
                compilerContext.emitTailCalls = savedTailCalls;
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.getRight());
                compilerContext.emitter.patchJump(jumpEnd, compilerContext.emitter.currentOffset());
                return;
            }
            case LOGICAL_OR -> {
                // left || right: if left is truthy, return left; otherwise evaluate and return right
                boolean savedTailCalls = compilerContext.emitTailCalls;
                compilerContext.emitTailCalls = false;
                compileExpression(binExpr.getLeft());
                compilerContext.emitTailCalls = savedTailCalls;
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.getRight());
                compilerContext.emitter.patchJump(jumpEnd, compilerContext.emitter.currentOffset());
                return;
            }
            case NULLISH_COALESCING -> {
                // left ?? right: if left is not null/undefined, return left; otherwise evaluate and return right
                boolean savedTailCalls = compilerContext.emitTailCalls;
                compilerContext.emitTailCalls = false;
                compileExpression(binExpr.getLeft());
                compilerContext.emitTailCalls = savedTailCalls;
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
                int jumpEnd = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.getRight());
                compilerContext.emitter.patchJump(jumpEnd, compilerContext.emitter.currentOffset());
                return;
            }
            default -> {
                // Fall through to compile operands for other operators
            }
        }

        // Compile operands
        compileExpression(binExpr.getLeft());
        compileExpression(binExpr.getRight());

        // Emit operation
        Opcode op = switch (binExpr.getOperator()) {
            case ADD -> Opcode.ADD;
            case BIT_AND -> Opcode.AND;
            case BIT_OR -> Opcode.OR;
            case BIT_XOR -> Opcode.XOR;
            case DIV -> Opcode.DIV;
            case EQ -> Opcode.EQ;
            case EXP -> Opcode.POW;
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
            default -> throw new JSCompilerException("Unknown binary operator: " + binExpr.getOperator());
        };

        compilerContext.emitter.emitOpcode(op);
    }

    void compileCallExpression(CallExpression callExpr) {
        callMemberCompiler.compileCallExpression(callExpr);
    }

    void compileCallExpressionRegular(CallExpression callExpr) {
        callMemberCompiler.compileCallExpressionRegular(callExpr);
    }

    void compileCallExpressionWithSpread(CallExpression callExpr) {
        callMemberCompiler.compileCallExpressionWithSpread(callExpr);
    }

    void compileConditionalExpression(ConditionalExpression condExpr) {
        // Compile test (not in tail position)
        boolean savedTailCalls = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;
        compileExpression(condExpr.getTest());
        compilerContext.emitTailCalls = savedTailCalls;

        // Jump to alternate if false
        int jumpToAlternate = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent (in tail position)
        compileExpression(condExpr.getConsequent());

        // Jump over alternate
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Patch jump to alternate
        compilerContext.emitter.patchJump(jumpToAlternate, compilerContext.emitter.currentOffset());

        // Compile alternate (in tail position)
        compilerContext.emitTailCalls = savedTailCalls;
        compileExpression(condExpr.getAlternate());

        // Patch jump to end
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
    }

    void compileExpression(Expression expr) {
        if (expr instanceof Literal literal) {
            compileLiteral(literal);
        } else if (expr instanceof Identifier id) {
            compileIdentifier(id);
        } else if (expr instanceof PrivateIdentifier privateIdentifier) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'");
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
        } else if (expr instanceof FunctionExpression functionExpression) {
            delegates.functions.compileFunctionExpression(functionExpression);
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
        } else if (expr instanceof ImportExpression importExpr) {
            compileImportExpression(importExpr);
        }
    }

    void compileIdentifier(Identifier id) {
        String name = id.getName();

        // Handle 'this' keyword
        if (JSKeyword.THIS.equals(name)) {
            compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
            return;
        }

        // Handle 'new.target' meta-property
        if ("new.target".equals(name)) {
            if (compilerContext.classFieldEvalContext || compilerContext.inClassFieldInitializer) {
                // ES2024: eval in class field initializer treats new.target as undefined
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            } else {
                compilerContext.emitter.emitOpcodeU8(Opcode.SPECIAL_OBJECT, 3);
            }
            return;
        }

        // Handle 'import.meta' meta-property
        if ("import.meta".equals(name)) {
            compilerContext.emitter.emitOpcodeU8(Opcode.SPECIAL_OBJECT, 6);
            return;
        }

        if (compilerContext.hasActiveWithObject()) {
            emitWithAwareIdentifierLookup(name);
            return;
        }

        emitIdentifierLookupWithoutWith(name);
    }

    void compileImportExpression(ImportExpression importExpr) {
        // Stack: -> specifier options
        compileExpression(importExpr.getSource());
        if (importExpr.getOptions() != null) {
            compileExpression(importExpr.getOptions());
        } else if (importExpr.isDefer()) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSImportDeferMarker.VALUE);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        // Stack: specifier options -> promise
        compilerContext.emitter.emitOpcode(Opcode.IMPORT);
    }

    void compileLiteral(Literal literal) {
        Object value = literal.getValue();

        if (value == null) {
            compilerContext.emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            compilerContext.emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
        } else if (value instanceof BigInteger bigInt) {
            // Check BigInteger before Number since BigInteger extends Number.
            // Match QuickJS: emit PUSH_BIGINT_I32 when the literal fits in signed i32.
            if (bigInt.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bigInt.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
                compilerContext.emitter.emitI32(bigInt.intValue());
            } else {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            }
        } else if (value instanceof Number num) {
            // Try to emit as i32 if it's an integer in range
            if (num instanceof Integer || num instanceof Long) {
                long longValue = num.longValue();
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                    compilerContext.emitter.emitI32((int) longValue);
                    return;
                }
            }
            // Otherwise emit as constant
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(num.doubleValue()));
        } else if (value instanceof RegExpLiteralValue regExpLiteralValue) {
            String source = regExpLiteralValue.source();
            int lastSlash = source.lastIndexOf('/');
            if (lastSlash > 0) {
                String pattern = source.substring(1, lastSlash);
                String flags = lastSlash < source.length() - 1 ? source.substring(lastSlash + 1) : "";
                try {
                    JSRegExp regexp = new JSRegExp(compilerContext.context, pattern, flags);
                    compilerContext.emitter.emitOpcodeConstant(Opcode.REGEXP, regexp);
                    return;
                } catch (Exception e) {
                    throw new JSSyntaxErrorException("Invalid regular expression literal: " + source);
                }
            }
            throw new JSSyntaxErrorException("Invalid regular expression literal: " + source);
        } else if (value instanceof String str) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
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
        assignmentCompiler.compileLogicalAssignment(assignExpr);
    }

    void compileMemberExpression(MemberExpression memberExpr) {
        callMemberCompiler.compileMemberExpression(memberExpr);
    }

    void compileNewExpression(NewExpression newExpr) {
        callMemberCompiler.compileNewExpression(newExpr);
    }

    void compileObjectExpression(ObjectExpression objExpr) {
        int protoDataPropertyCount = 0;
        for (ObjectExpressionProperty property : objExpr.getProperties()) {
            if (isProtoDataProperty(property)) {
                protoDataPropertyCount++;
                if (protoDataPropertyCount > 1) {
                    throw new JSCompilerException("Duplicate __proto__ fields are not allowed in object literals");
                }
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.OBJECT);

        for (ObjectExpressionProperty prop : objExpr.getProperties()) {
            String kind = prop.getKind();

            if ("spread".equals(kind)) {
                // Object spread: {...expr}
                // Stack: obj -> obj expr null -> obj (via COPY_DATA_PROPERTIES)
                compileExpression(prop.getValue());
                compilerContext.emitter.emitOpcode(Opcode.NULL);
                // mask=6: target@sp[-3](offset 2), source@sp[-2](offset 1), exclude@sp[-1](offset 0)
                compilerContext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 6);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                continue;
            }

            if (JSKeyword.GET.equals(kind) || JSKeyword.SET.equals(kind)) {
                // Getter/setter property: use DEFINE_METHOD_COMPUTED
                // Stack: obj -> obj key method -> obj
                // Push key
                if (prop.isComputed()) {
                    compileExpression(prop.getKey());
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                } else if (prop.getKey() instanceof Identifier id) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.getName()));
                } else {
                    compileExpression(prop.getKey());
                    compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                }

                // Compile the getter/setter function (not constructable per ES spec)
                delegates.functions.compileFunctionExpression((FunctionExpression) prop.getValue(), true);

                // DEFINE_METHOD_COMPUTED with flags: kind (1=get, 2=set) | enumerable (4)
                int methodKind = JSKeyword.GET.equals(kind) ? 1 : 2;
                int flags = methodKind | 4; // enumerable = true for object literal properties
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, flags);
            } else {
                // Regular property: key: value
                // ES2015 B.3.1: __proto__ in object literal sets prototype
                if (!prop.isComputed() && !prop.isShorthand()
                        && prop.getKey() instanceof Identifier id
                        && "__proto__".equals(id.getName())) {
                    // Stack: obj -> obj proto -> obj
                    compileExpression(prop.getValue());
                    compilerContext.emitter.emitOpcode(Opcode.SET_PROTO);
                } else {
                    // Push value
                    if (prop.isMethod() && prop.getValue() instanceof FunctionExpression methodFunc) {
                        // Push key for DEFINE_METHOD_COMPUTED
                        if (prop.getKey() instanceof Identifier id && !prop.isComputed()) {
                            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.getName()));
                        } else {
                            compileExpression(prop.getKey());
                            compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                        }
                        // Concise methods are not constructors per ES spec
                        delegates.functions.compileFunctionExpression(methodFunc, true);
                        // Object literal methods are enumerable.
                        compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
                    } else if (prop.getKey() instanceof Identifier id && !prop.isComputed()) {
                        // Non-computed identifier key: use DEFINE_FIELD with atom
                        compileExpression(prop.getValue());
                        if (prop.getValue().isAnonymousFunction()) {
                            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, id.getName());
                        }
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, id.getName());
                    } else {
                        // Computed or non-identifier key: define own data property directly.
                        // Stack: [obj] -> [obj, key, value] -> [obj]
                        compileExpression(prop.getKey());
                        compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
                        compileExpression(prop.getValue());
                        if (prop.getValue().isAnonymousFunction()) {
                            compilerContext.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
                        }
                        compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
                    }
                }
            }
        }
    }

    void compilePrivateInExpression(PrivateIdentifier privateIdentifier, Expression right) {
        compileExpression(right);

        JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(privateIdentifier.getName()) : null;
        if (symbol == null) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'");
        }

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
        compilerContext.emitter.emitOpcode(Opcode.PRIVATE_IN);
    }

    void compileSequenceExpression(SequenceExpression seqExpr) {
        // Following QuickJS: evaluate each expression in order,
        // dropping all but the last one's value
        List<Expression> expressions = seqExpr.getExpressions();

        for (int i = 0; i < expressions.size(); i++) {
            if (i < expressions.size() - 1) {
                // Non-last expressions are not in tail position
                boolean savedTailCalls = compilerContext.emitTailCalls;
                compilerContext.emitTailCalls = false;
                compileExpression(expressions.get(i));
                compilerContext.emitTailCalls = savedTailCalls;
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                // Last expression inherits tail call context
                compileExpression(expressions.get(i));
            }
        }
        // The last expression's value remains on the stack
    }

    void compileTaggedTemplateExpression(TaggedTemplateExpression taggedTemplate) {
        boolean isTailCall = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;

        // Tagged template: tag`template`
        // The tag function receives:
        // 1. A template object (array-like) with cooked strings and a 'raw' property
        // 2. The values of the substitutions as additional arguments

        TemplateLiteral template = taggedTemplate.getQuasi();
        List<Expression> expressions = template.getExpressions();

        // Check if this is a method call (tag is a member expression)
        if (taggedTemplate.getTag() instanceof MemberExpression memberExpr) {
            // For method calls: obj.method`template`
            // We need to preserve obj as the 'this' value

            // Push object (receiver)
            compileExpression(memberExpr.getObject());

            // Get the method while keeping the object on the stack
            if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compileExpression(memberExpr.getProperty());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
            }

            // SWAP converts obj/func to func/obj for internalHandleCall and locks property chain
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            // Regular function call: func`template`
            // Compile the tag function first (will be the callee)
            compileExpression(taggedTemplate.getTag());

            // Add undefined as receiver/thisArg
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // QuickJS behavior: each call site uses a stable, frozen template object.
        // Build it once in the constant pool and pass it as the first argument.
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, delegates.functions.createTaggedTemplateObject(template));

        // Add substitution expressions as additional arguments
        for (Expression expr : expressions) {
            compileExpression(expr);
        }

        // Call the tag function
        // argCount = 1 (template array) + number of expressions
        int argCount = 1 + expressions.size();
        boolean isMethodCall = taggedTemplate.getTag() instanceof MemberExpression;
        if (isMethodCall) {
            compilerContext.emitter.emitOpcodeU16(
                    isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD,
                    argCount);
        } else {
            compilerContext.emitter.emitOpcodeU16(
                    isTailCall ? Opcode.TAIL_CALL : Opcode.CALL,
                    argCount);
        }
    }

    void compileTemplateLiteral(TemplateLiteral templateLiteral) {
        // For untagged template literals, concatenate strings and expressions
        // Example: `Hello ${name}!` becomes "Hello " + name + "!"

        List<String> quasis = templateLiteral.getQuasis();
        List<Expression> expressions = templateLiteral.getExpressions();

        if (quasis.isEmpty()) {
            // Empty template literal
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(""));
            return;
        }

        // Start with the first quasi
        String firstQuasi = quasis.get(0);
        if (firstQuasi == null) {
            throw new JSCompilerException("Invalid escape sequence in untagged template literal");
        }
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(firstQuasi));

        // Add each expression and subsequent quasi using string concatenation (ADD)
        for (int i = 0; i < expressions.size(); i++) {
            // Template substitutions use ToString coercion (not + operator default hint).
            // Call String(expr) to ensure ToString semantics.
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, "String");
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compileExpression(expressions.get(i));
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);

            // Concatenate using ADD after explicit ToString on the substitution.
            compilerContext.emitter.emitOpcode(Opcode.ADD);

            // Add the next quasi if it exists
            if (i + 1 < quasis.size()) {
                String quasi = quasis.get(i + 1);
                if (quasi == null) {
                    throw new JSCompilerException("Invalid escape sequence in untagged template literal");
                }
                if (!quasi.isEmpty()) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
                    compilerContext.emitter.emitOpcode(Opcode.ADD);
                }
            }
        }
    }

    void compileUnaryExpression(UnaryExpression unaryExpr) {
        // DELETE operator needs special handling - it doesn't evaluate the operand,
        // but instead emits object and property separately
        if (unaryExpr.getOperator() == UnaryExpression.UnaryOperator.DELETE) {
            Expression operand = unaryExpr.getOperand();

            if (operand instanceof MemberExpression memberExpr) {
                // delete obj.prop or delete obj[expr]
                compileExpression(memberExpr.getObject());

                if (memberExpr.isComputed()) {
                    // obj[expr]
                    compileExpression(memberExpr.getProperty());
                } else if (memberExpr.getProperty() instanceof Identifier propId) {
                    // obj.prop
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.getName()));
                }

                compilerContext.emitter.emitOpcode(Opcode.DELETE);
            } else if (operand instanceof Identifier id) {
                // Match QuickJS scope_delete_var lowering:
                // - local/arg/closure/implicit arguments bindings => false
                // - unresolved/global binding => DELETE_VAR runtime check
                boolean isLocalBinding = compilerContext.findLocalInScopes(id.getName()) != null
                        || compilerContext.captureResolver.resolveCapturedBindingIndex(id.getName()) != null
                        || (JSArguments.NAME.equals(id.getName()) && !compilerContext.inGlobalScope)
                        || compilerContext.nonDeletableGlobalBindings.contains(id.getName());
                if (isLocalBinding) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
                } else {
                    List<Integer> withObjectLocals = compilerContext.getActiveWithObjectLocals();
                    if (!withObjectLocals.isEmpty()) {
                        emitWithAwareDeleteIdentifier(id.getName(), withObjectLocals, 0);
                    } else if (!compilerContext.inheritedWithObjectBindingNames.isEmpty()) {
                        emitInheritedWithAwareDeleteIdentifier(id.getName(), compilerContext.inheritedWithObjectBindingNames, 0);
                    } else {
                        compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, id.getName());
                    }
                }
            } else {
                // delete non-reference expression => evaluate for side effects, then true
                compileExpression(operand);
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
                if (compilerContext.hasActiveWithObject() || !compilerContext.inheritedWithObjectBindingNames.isEmpty()) {
                    // Use reference semantics so with-scope resolution happens before local/captured fallback.
                    assignmentCompiler.emitIdentifierReference(id.getName());
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

                Integer localIndex = compilerContext.findLocalInScopes(id.getName());
                if (localIndex != null) {
                    // Local binding.
                    compileExpression(operand);
                    compilerContext.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                            : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                    if (compilerContext.isLocalBindingConst(id.getName())) {
                        emitConstAssignmentErrorForLocal(id.getName(), localIndex);
                    } else {
                        compilerContext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_LOC : Opcode.PUT_LOC, localIndex);
                    }
                    return;
                }

                Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(id.getName());
                if (capturedIndex != null) {
                    // Captured binding.
                    compileExpression(operand);
                    compilerContext.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                            : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                    if (compilerContext.isCapturedBindingConst(id.getName())) {
                        emitConstAssignmentErrorForCaptured(id.getName(), capturedIndex);
                    } else {
                        compilerContext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_VAR_REF : Opcode.PUT_VAR_REF, capturedIndex);
                    }
                    return;
                }

                // Unresolved identifier: use reference semantics so strict errors and with-scopes are handled correctly.
                assignmentCompiler.emitIdentifierReference(id.getName());
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
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
                    compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
                    delegates.emitHelpers.emitSuperPropertyKey(memberExpr);
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
                    compileExpression(memberExpr.getObject());
                    compileExpression(memberExpr.getProperty());
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
                        compileExpression(memberExpr.getObject());

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

                        compileExpression(memberExpr.getObject());

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
                compileExpression(operand);
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
                compileExpression(unaryExpr.getOperand());
                compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
                return;
            }
            if (JSKeyword.THIS.equals(name)) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
            } else if (JSArguments.NAME.equals(name)
                    && compilerContext.hasEnclosingArgumentsBinding
                    && compilerContext.findLocalInScopes(name) == null) {
                compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                compilerContext.emitter.emitU8(0);
            } else {
                Integer localIndex = compilerContext.findLocalInScopes(name);
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

        compileExpression(unaryExpr.getOperand());

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

    void compileYieldExpression(YieldExpression yieldExpr) {
        // Compile the argument expression (if present)
        if (yieldExpr.getArgument() != null) {
            compileExpression(yieldExpr.getArgument());
        } else {
            // No argument means yield undefined
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // Emit the appropriate yield opcode
        if (yieldExpr.isDelegate()) {
            // yield* delegates to another generator/iterable
            compilerContext.emitter.emitOpcode(compilerContext.isInAsyncFunction ? Opcode.ASYNC_YIELD_STAR : Opcode.YIELD_STAR);
        } else {
            // Regular yield
            compilerContext.emitter.emitOpcode(Opcode.YIELD);
        }
    }

    private void emitCapturedOrGlobalIdentifierLookup(String name) {
        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
        } else {
            // Not found in local scopes, use global variable
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
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

    private void emitIdentifierLookupWithoutWith(String name) {

        // Always check local scopes first, even in global scope (for nested blocks/loops)
        // This must happen BEFORE the 'arguments' special handling so that
        // explicit `var arguments` or `let arguments` declarations take precedence.
        // Following QuickJS: arguments is resolved through normal variable lookup first.
        Integer localIndex = compilerContext.findLocalInScopes(name);

        if (localIndex != null) {
            // Use GET_LOC_CHECK for TDZ locals (let/const/class in program scope)
            // to throw ReferenceError if accessed before initialization
            if (compilerContext.tdzLocals.contains(name)) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
            } else {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
            }
            return;
        }

        // Handle 'arguments' keyword in function scope (only if not found as a local)
        // For regular functions: SPECIAL_OBJECT creates the arguments object
        // For arrow functions with enclosing regular function: SPECIAL_OBJECT walks up call stack
        // For arrow functions without enclosing regular function: resolve as normal variable
        // Following QuickJS: arrow functions inherit arguments from enclosing scope,
        // but only if there is an enclosing scope with arguments binding
        if (JSArguments.NAME.equals(name) && compilerContext.hasEnclosingArgumentsBinding) {
            // Emit SPECIAL_OBJECT opcode with type 0 (SPECIAL_OBJECT_ARGUMENTS)
            // The VM will handle differently for arrow vs regular functions
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(0);  // Type 0 = arguments object
            return;
        }

        if (emitInheritedWithAwareIdentifierLookup(name)) {
            return;
        }

        emitCapturedOrGlobalIdentifierLookup(name);
    }

    void emitIdentifierReference(String name) {
        assignmentCompiler.emitIdentifierReference(name);
    }

    private void emitInheritedWithAwareDeleteIdentifier(String name, List<String> withBindingNames, int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            return;
        }

        String withBindingName = withBindingNames.get(withDepth);
        Integer withLocalIndex = compilerContext.findLocalInScopes(withBindingName);
        if (withLocalIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withLocalIndex);
        } else {
            Integer withCapturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(withBindingName);
            if (withCapturedIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, withCapturedIndex);
            } else {
                emitInheritedWithAwareDeleteIdentifier(name, withBindingNames, withDepth + 1);
                return;
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);
        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToDeleteWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int deleteWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToDeleteWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, deleteWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitInheritedWithAwareDeleteIdentifier(name, withBindingNames, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
    }

    private boolean emitInheritedWithAwareIdentifierLookup(String name) {
        if (compilerContext.inheritedWithObjectBindingNames.isEmpty()) {
            return false;
        }
        emitInheritedWithAwareIdentifierLookup(name, compilerContext.inheritedWithObjectBindingNames, 0);
        return true;
    }

    private void emitInheritedWithAwareIdentifierLookup(String name, List<String> withBindingNames, int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(name);
            return;
        }

        String withBindingName = withBindingNames.get(withDepth);
        Integer withLocalIndex = compilerContext.findLocalInScopes(withBindingName);
        if (withLocalIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withLocalIndex);
        } else {
            Integer withCapturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(withBindingName);
            if (withCapturedIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, withCapturedIndex);
            } else {
                emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1);
                return;
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    private void emitInheritedWithAwareIdentifierLookupForCall(String name, List<String> withBindingNames, int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(name);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            return;
        }

        String withBindingName = withBindingNames.get(withDepth);
        Integer withLocalIndex = compilerContext.findLocalInScopes(withBindingName);
        if (withLocalIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withLocalIndex);
        } else {
            Integer withCapturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(withBindingName);
            if (withCapturedIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, withCapturedIndex);
            } else {
                emitInheritedWithAwareIdentifierLookupForCall(name, withBindingNames, withDepth + 1);
                return;
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitInheritedWithAwareIdentifierLookupForCall(name, withBindingNames, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    /**
     * Emit with-aware delete for identifiers inside with-scope.
     * Checks each with-object for the property (respecting @@unscopables),
     * and if found, deletes it from the with-object. Otherwise falls back to DELETE_VAR.
     * Stack: ... -> ... result (boolean)
     */
    private void emitWithAwareDeleteIdentifier(String name, List<Integer> withObjectLocals, int withDepth) {
        if (withDepth >= withObjectLocals.size()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        // Load with-object and check if it has the property
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        // Check @@unscopables
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToDeleteWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        // Property found and not blocked by unscopables: delete from with-object
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        // No unscopables object: delete from with-object
        int deleteWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToDeleteWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, deleteWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop undefined unscopables result
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Property not in with-object or blocked by unscopables: fall through
        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop with-object
        emitWithAwareDeleteIdentifier(name, withObjectLocals, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
    }

    private void emitWithAwareIdentifierLookup(String name) {
        List<Integer> withObjectLocals = compilerContext.getActiveWithObjectLocals();
        emitWithAwareIdentifierLookup(name, withObjectLocals, 0);
    }

    private void emitWithAwareIdentifierLookup(String name, List<Integer> withObjectLocals, int withDepth) {
        if (withDepth >= withObjectLocals.size()) {
            emitIdentifierLookupWithoutWith(name);
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitWithAwareIdentifierLookup(name, withObjectLocals, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    /**
     * Emit with-aware identifier lookup for call expressions.
     * Pushes two values: [value, receiver] where receiver is the with object
     * if found, or undefined if not found in any with scope.
     * Stack: ... -> ... value receiver
     */
    void emitWithAwareIdentifierLookupForCall(String name) {
        List<Integer> withObjectLocals = compilerContext.getActiveWithObjectLocals();
        if (!withObjectLocals.isEmpty()) {
            emitWithAwareIdentifierLookupForCall(name, withObjectLocals, 0);
            return;
        }
        if (!compilerContext.inheritedWithObjectBindingNames.isEmpty()) {
            emitInheritedWithAwareIdentifierLookupForCall(name, compilerContext.inheritedWithObjectBindingNames, 0);
            return;
        }
        emitIdentifierLookupWithoutWith(name);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
    }

    private void emitWithAwareIdentifierLookupForCall(String name, List<Integer> withObjectLocals, int withDepth) {
        if (withDepth >= withObjectLocals.size()) {
            emitIdentifierLookupWithoutWith(name);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        // Check @@unscopables
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        // Found and not blocked: GET_FIELD2 keeps withObj, SWAP → [value, withObj]
        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Found without unscopables check:
        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Not found / blocked: fallback to next with scope or global
        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitWithAwareIdentifierLookupForCall(name, withObjectLocals, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    private int emitWithHasPropertyAndJumpIfMissing(String name) {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);
        return compilerContext.emitter.emitJump(Opcode.IF_FALSE);
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

    private boolean isProtoDataProperty(ObjectExpressionProperty property) {
        if (property.isComputed() || property.isShorthand() || property.isMethod() || !"init".equals(property.getKind())) {
            return false;
        }
        if (property.getKey() instanceof Identifier identifier) {
            return "__proto__".equals(identifier.getName());
        }
        if (property.getKey() instanceof Literal literal && literal.getValue() instanceof String stringValue) {
            return "__proto__".equals(stringValue);
        }
        return false;
    }
}
