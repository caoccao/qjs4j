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
    private final CompilerContext conpilerConext;
    private final CompilerDelegates delegates;

    ExpressionCompiler(CompilerContext conpilerConext, CompilerDelegates delegates) {
        this.conpilerConext = conpilerConext;
        this.delegates = delegates;
        assignmentCompiler = new ExpressionAssignmentCompiler(this, conpilerConext, delegates);
        callMemberCompiler = new ExpressionCallMemberCompiler(this, conpilerConext, delegates);
    }

    void compileArrayExpression(ArrayExpression arrayExpr) {
        conpilerConext.emitter.emitOpcode(Opcode.ARRAY_NEW);

        // Check if we have any spread elements or holes
        boolean hasSpread = arrayExpr.elements().stream()
                .anyMatch(e -> e instanceof SpreadElement);
        boolean hasHoles = arrayExpr.elements().stream()
                .anyMatch(e -> e == null);

        if (!hasSpread && !hasHoles) {
            // Simple case: no spread elements, no holes - use PUSH_ARRAY
            for (Expression element : arrayExpr.elements()) {
                compileExpression(element);
                conpilerConext.emitter.emitOpcode(Opcode.PUSH_ARRAY);
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
                        conpilerConext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                    }
                    // Compile the iterable expression
                    compileExpression(spreadElement.argument());
                    // Emit APPEND to spread elements into the array
                    // Stack: array pos iterable -> array pos
                    conpilerConext.emitter.emitOpcode(Opcode.APPEND);
                    // After APPEND, index is updated on stack
                    needsLength = false;
                } else if (element != null) {
                    if (needsIndex) {
                        // We have index on stack, use DEFINE_ARRAY_EL
                        compileExpression(element);
                        conpilerConext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        conpilerConext.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    } else {
                        // No index on stack yet
                        // Start using index-based assignment since we have holes or spread
                        conpilerConext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                        compileExpression(element);
                        conpilerConext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        conpilerConext.emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    }
                } else {
                    // Hole in array
                    if (needsIndex) {
                        // We have position on stack, just increment it
                        conpilerConext.emitter.emitOpcode(Opcode.INC);
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
                    conpilerConext.emitter.emitOpcode(Opcode.DUP1);  // array array idx
                    conpilerConext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx (PUT_FIELD leaves value)
                    conpilerConext.emitter.emitOpcode(Opcode.DROP);  // array
                } else {
                    // Stack: array (idx is compile-time constant)
                    // QuickJS pattern: dup, push idx, swap, put_field "length", drop
                    conpilerConext.emitter.emitOpcode(Opcode.DUP);  // array array
                    conpilerConext.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);  // array array idx
                    conpilerConext.emitter.emitOpcode(Opcode.SWAP);  // array idx array
                    conpilerConext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx
                    conpilerConext.emitter.emitOpcode(Opcode.DROP);  // array
                }
            } else if (needsIndex) {
                // No trailing hole, just drop the index
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
            }
        }
    }

    void compileAssignmentExpression(AssignmentExpression assignExpr) {
        assignmentCompiler.compileAssignmentExpression(assignExpr);
    }

    void compileAwaitExpression(AwaitExpression awaitExpr) {
        // Compile the argument expression
        compileExpression(awaitExpr.argument());

        // Emit the AWAIT opcode
        // This will convert the value to a promise (if it isn't already)
        // and pause execution until the promise resolves
        conpilerConext.emitter.emitOpcode(Opcode.AWAIT);
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
                conpilerConext.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = conpilerConext.emitter.emitJump(Opcode.IF_FALSE);
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.right());
                conpilerConext.emitter.patchJump(jumpEnd, conpilerConext.emitter.currentOffset());
                return;
            }
            case LOGICAL_OR -> {
                // left || right: if left is truthy, return left; otherwise evaluate and return right
                compileExpression(binExpr.left());
                conpilerConext.emitter.emitOpcode(Opcode.DUP);
                int jumpEnd = conpilerConext.emitter.emitJump(Opcode.IF_TRUE);
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.right());
                conpilerConext.emitter.patchJump(jumpEnd, conpilerConext.emitter.currentOffset());
                return;
            }
            case NULLISH_COALESCING -> {
                // left ?? right: if left is not null/undefined, return left; otherwise evaluate and return right
                compileExpression(binExpr.left());
                conpilerConext.emitter.emitOpcode(Opcode.DUP);
                conpilerConext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
                int jumpEnd = conpilerConext.emitter.emitJump(Opcode.IF_FALSE);
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
                compileExpression(binExpr.right());
                conpilerConext.emitter.patchJump(jumpEnd, conpilerConext.emitter.currentOffset());
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

        conpilerConext.emitter.emitOpcode(op);
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
        // Compile test
        compileExpression(condExpr.test());

        // Jump to alternate if false
        int jumpToAlternate = conpilerConext.emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent
        compileExpression(condExpr.consequent());

        // Jump over alternate
        int jumpToEnd = conpilerConext.emitter.emitJump(Opcode.GOTO);

        // Patch jump to alternate
        conpilerConext.emitter.patchJump(jumpToAlternate, conpilerConext.emitter.currentOffset());

        // Compile alternate
        compileExpression(condExpr.alternate());

        // Patch jump to end
        conpilerConext.emitter.patchJump(jumpToEnd, conpilerConext.emitter.currentOffset());
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
        }
    }

    void compileIdentifier(Identifier id) {
        String name = id.name();

        // Handle 'this' keyword
        if ("this".equals(name)) {
            conpilerConext.emitter.emitOpcode(Opcode.PUSH_THIS);
            return;
        }

        // Handle 'new.target' meta-property
        if ("new.target".equals(name)) {
            conpilerConext.emitter.emitOpcodeU8(Opcode.SPECIAL_OBJECT, 3);
            return;
        }

        if (conpilerConext.hasActiveWithObject()) {
            emitWithAwareIdentifierLookup(name);
            return;
        }

        emitIdentifierLookupWithoutWith(name);
    }

    void compileLiteral(Literal literal) {
        Object value = literal.value();

        if (value == null) {
            conpilerConext.emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            conpilerConext.emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
        } else if (value instanceof BigInteger bigInt) {
            // Check BigInteger before Number since BigInteger extends Number.
            // Match QuickJS: emit PUSH_BIGINT_I32 when the literal fits in signed i32.
            if (bigInt.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bigInt.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                conpilerConext.emitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
                conpilerConext.emitter.emitI32(bigInt.intValue());
            } else {
                conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            }
        } else if (value instanceof Number num) {
            // Try to emit as i32 if it's an integer in range
            if (num instanceof Integer || num instanceof Long) {
                long longValue = num.longValue();
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    conpilerConext.emitter.emitOpcode(Opcode.PUSH_I32);
                    conpilerConext.emitter.emitI32((int) longValue);
                    return;
                }
            }
            // Otherwise emit as constant
            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(num.doubleValue()));
        } else if (value instanceof RegExpLiteralValue regExpLiteralValue) {
            String source = regExpLiteralValue.source();
            int lastSlash = source.lastIndexOf('/');
            if (lastSlash > 0) {
                String pattern = source.substring(1, lastSlash);
                String flags = lastSlash < source.length() - 1 ? source.substring(lastSlash + 1) : "";
                try {
                    JSRegExp regexp = new JSRegExp(pattern, flags);
                    conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, regexp);
                    return;
                } catch (Exception e) {
                    throw new JSSyntaxErrorException("Invalid regular expression literal: " + source);
                }
            }
            throw new JSSyntaxErrorException("Invalid regular expression literal: " + source);
        } else if (value instanceof String str) {
            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
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
        conpilerConext.emitter.emitOpcode(Opcode.OBJECT_NEW);

        for (ObjectExpression.Property prop : objExpr.properties()) {
            String kind = prop.kind();

            if ("spread".equals(kind)) {
                // Object spread: {...expr}
                // Stack: obj -> obj expr null -> obj (via COPY_DATA_PROPERTIES)
                compileExpression(prop.value());
                conpilerConext.emitter.emitOpcode(Opcode.NULL);
                // mask=6: target@sp[-3](offset 2), source@sp[-2](offset 1), exclude@sp[-1](offset 0)
                conpilerConext.emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 6);
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
                continue;
            }

            if ("get".equals(kind) || "set".equals(kind)) {
                // Getter/setter property: use DEFINE_METHOD_COMPUTED
                // Stack: obj -> obj key method -> obj
                // Push key
                if (prop.computed()) {
                    compileExpression(prop.key());
                } else if (prop.key() instanceof Identifier id) {
                    conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
                } else {
                    compileExpression(prop.key());
                }

                // Compile the getter/setter function (not constructable per ES spec)
                delegates.functions.compileFunctionExpression((FunctionExpression) prop.value(), true);

                // DEFINE_METHOD_COMPUTED with flags: kind (1=get, 2=set) | enumerable (4)
                int methodKind = "get".equals(kind) ? 1 : 2;
                int flags = methodKind | 4; // enumerable = true for object literal properties
                conpilerConext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, flags);
            } else {
                // Regular property: key: value
                // ES2015 B.3.1: __proto__ in object literal sets prototype
                if (!prop.computed() && !prop.shorthand()
                        && prop.key() instanceof Identifier id
                        && "__proto__".equals(id.name())) {
                    // Stack: obj -> obj proto -> obj
                    compileExpression(prop.value());
                    conpilerConext.emitter.emitOpcode(Opcode.SET_PROTO);
                } else {
                    // Push key
                    if (prop.key() instanceof Identifier id && !prop.computed()) {
                        conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
                    } else {
                        compileExpression(prop.key());
                    }

                    // Push value
                    if (prop.method() && prop.value() instanceof FunctionExpression methodFunc) {
                        // Concise methods are not constructors per ES spec
                        delegates.functions.compileFunctionExpression(methodFunc, true);
                        // Object literal methods are enumerable.
                        conpilerConext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
                    } else {
                        compileExpression(prop.value());
                        // Define data property.
                        conpilerConext.emitter.emitOpcode(Opcode.DEFINE_PROP);
                    }
                }
            }
        }
    }

    void compilePrivateInExpression(PrivateIdentifier privateIdentifier, Expression right) {
        compileExpression(right);

        JSSymbol symbol = conpilerConext.privateSymbols != null ? conpilerConext.privateSymbols.get(privateIdentifier.name()) : null;
        if (symbol == null) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.name() + "'");
        }

        conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
        conpilerConext.emitter.emitOpcode(Opcode.PRIVATE_IN);
    }

    void compileSequenceExpression(SequenceExpression seqExpr) {
        // Following QuickJS: evaluate each expression in order,
        // dropping all but the last one's value
        List<Expression> expressions = seqExpr.expressions();

        for (int i = 0; i < expressions.size(); i++) {
            compileExpression(expressions.get(i));

            // Drop the value of all expressions except the last one
            if (i < expressions.size() - 1) {
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
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
            conpilerConext.emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                // obj[expr]
                compileExpression(memberExpr.property());
                conpilerConext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                // obj.prop
                conpilerConext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            // Now stack is: receiver, method
            // Swap so method is on top: method, receiver
            conpilerConext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            // Regular function call: func`template`
            // Compile the tag function first (will be the callee)
            compileExpression(taggedTemplate.tag());

            // Add undefined as receiver/thisArg
            conpilerConext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // Stack is now: function, receiver

        // QuickJS behavior: each call site uses a stable, frozen template object.
        // Build it once in the constant pool and pass it as the first argument.
        conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, delegates.functions.createTaggedTemplateObject(template));
        // Stack: function, receiver, template_object

        // Add substitution expressions as additional arguments
        for (Expression expr : expressions) {
            compileExpression(expr);
        }

        // Call the tag function
        // argCount = 1 (template array) + number of expressions
        int argCount = 1 + expressions.size();
        conpilerConext.emitter.emitOpcode(Opcode.CALL);
        conpilerConext.emitter.emitU16(argCount);
    }

    void compileTemplateLiteral(TemplateLiteral templateLiteral) {
        // For untagged template literals, concatenate strings and expressions
        // Example: `Hello ${name}!` becomes "Hello " + name + "!"

        List<String> quasis = templateLiteral.quasis();
        List<Expression> expressions = templateLiteral.expressions();

        if (quasis.isEmpty()) {
            // Empty template literal
            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(""));
            return;
        }

        // Start with the first quasi
        String firstQuasi = quasis.get(0);
        if (firstQuasi == null) {
            throw new JSCompilerException("Invalid escape sequence in untagged template literal");
        }
        conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(firstQuasi));

        // Add each expression and subsequent quasi using string concatenation (ADD)
        for (int i = 0; i < expressions.size(); i++) {
            // Compile the expression
            compileExpression(expressions.get(i));

            // Template substitutions use ToString coercion (not + operator default hint).
            conpilerConext.emitter.emitOpcode(Opcode.TO_STRING);

            // Concatenate using ADD after explicit ToString on the substitution.
            conpilerConext.emitter.emitOpcode(Opcode.ADD);

            // Add the next quasi if it exists
            if (i + 1 < quasis.size()) {
                String quasi = quasis.get(i + 1);
                if (quasi == null) {
                    throw new JSCompilerException("Invalid escape sequence in untagged template literal");
                }
                if (!quasi.isEmpty()) {
                    conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
                    conpilerConext.emitter.emitOpcode(Opcode.ADD);
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
                    conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.name()));
                }

                conpilerConext.emitter.emitOpcode(Opcode.DELETE);
            } else if (operand instanceof Identifier id) {
                // Match QuickJS scope_delete_var lowering:
                // - local/arg/closure/implicit arguments bindings => false
                // - unresolved/global binding => DELETE_VAR runtime check
                boolean isLocalBinding = conpilerConext.findLocalInScopes(id.name()) != null
                        || conpilerConext.resolveCapturedBindingIndex(id.name()) != null
                        || (JSArguments.NAME.equals(id.name()) && !conpilerConext.inGlobalScope)
                        || conpilerConext.nonDeletableGlobalBindings.contains(id.name());
                if (isLocalBinding) {
                    conpilerConext.emitter.emitOpcode(Opcode.PUSH_FALSE);
                } else {
                    conpilerConext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, id.name());
                }
            } else {
                // delete literal / non-reference expression => true
                conpilerConext.emitter.emitOpcode(Opcode.PUSH_TRUE);
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
                conpilerConext.emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                        : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                Integer localIndex = conpilerConext.findLocalInScopes(id.name());
                if (localIndex != null) {
                    conpilerConext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_LOCAL : Opcode.PUT_LOCAL, localIndex);
                } else {
                    Integer capturedIndex = conpilerConext.resolveCapturedBindingIndex(id.name());
                    if (capturedIndex != null) {
                        conpilerConext.emitter.emitOpcodeU16(isPrefix ? Opcode.SET_VAR_REF : Opcode.PUT_VAR_REF, capturedIndex);
                    } else {
                        conpilerConext.emitter.emitOpcodeAtom(isPrefix ? Opcode.SET_VAR : Opcode.PUT_VAR, id.name());
                    }
                }
            } else if (operand instanceof MemberExpression memberExpr) {
                if (memberExpr.computed()) {
                    // Array element: obj[prop]
                    compileExpression(memberExpr.object());
                    compileExpression(memberExpr.property());

                    if (isPrefix) {
                        // Prefix: ++arr[i] - returns new value
                        conpilerConext.emitter.emitOpcode(Opcode.DUP2);
                        conpilerConext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                        conpilerConext.emitter.emitOpcode(Opcode.PLUS); // ToNumber conversion
                        conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                        conpilerConext.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                        // Stack: [obj, prop, new_val] — already in QuickJS order
                        conpilerConext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else {
                        // Postfix: arr[i]++ - returns old value (must be ToNumber'd per ES spec)
                        conpilerConext.emitter.emitOpcode(Opcode.DUP2); // obj prop obj prop
                        conpilerConext.emitter.emitOpcode(Opcode.GET_ARRAY_EL); // obj prop old_val
                        conpilerConext.emitter.emitOpcode(Opcode.PLUS); // obj prop old_numeric (ToNumber conversion)
                        conpilerConext.emitter.emitOpcode(Opcode.DUP); // obj prop old_numeric old_numeric
                        conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                        conpilerConext.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj prop old_val new_val
                        // PERM4 to rearrange: [obj, prop, old_val, new_val] -> [old_val, obj, prop, new_val]
                        conpilerConext.emitter.emitOpcode(Opcode.PERM4); // old_val obj prop new_val
                        conpilerConext.emitter.emitOpcode(Opcode.PUT_ARRAY_EL); // old_val new_val
                        conpilerConext.emitter.emitOpcode(Opcode.DROP); // old_val
                    }
                } else {
                    // Object property: obj.prop or obj.#field
                    if (memberExpr.property() instanceof Identifier propId) {
                        compileExpression(memberExpr.object());

                        if (isPrefix) {
                            // Prefix: ++obj.prop - returns new value
                            conpilerConext.emitter.emitOpcode(Opcode.DUP);
                            conpilerConext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                            conpilerConext.emitter.emitOpcode(Opcode.PLUS); // ToNumber conversion
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            conpilerConext.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                            // Stack: [obj, new_val] -> need [new_val, obj] for PUT_FIELD
                            conpilerConext.emitter.emitOpcode(Opcode.SWAP);
                            // PUT_FIELD pops obj, peeks new_val, leaves [new_val]
                            conpilerConext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                        } else {
                            // Postfix: obj.prop++ - returns old value (must be ToNumber'd per ES spec)
                            conpilerConext.emitter.emitOpcode(Opcode.DUP); // obj obj
                            conpilerConext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name()); // obj old_val
                            conpilerConext.emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            conpilerConext.emitter.emitOpcode(Opcode.DUP); // obj old_numeric old_numeric
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            conpilerConext.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj old_val new_val
                            // Stack: [obj, old_val, new_val] - need [old_val, new_val, obj] for PUT_FIELD
                            // ROT3L: [old_val, new_val, obj]
                            conpilerConext.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            // PUT_FIELD pops obj, peeks new_val, leaves [old_val, new_val]
                            conpilerConext.emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name()); // old_val new_val
                            conpilerConext.emitter.emitOpcode(Opcode.DROP); // old_val
                        }
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        // Private field: obj.#field
                        String fieldName = privateId.name();
                        JSSymbol symbol = conpilerConext.privateSymbols.get(fieldName);
                        if (symbol == null) {
                            throw new JSCompilerException("Private field not found: #" + fieldName);
                        }

                        compileExpression(memberExpr.object());

                        if (isPrefix) {
                            // Prefix: ++obj.#field - returns new value
                            conpilerConext.emitter.emitOpcode(Opcode.DUP); // obj obj
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            conpilerConext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            conpilerConext.emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            conpilerConext.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj new_val
                            conpilerConext.emitter.emitOpcode(Opcode.DUP); // obj new_val new_val
                            conpilerConext.emitter.emitOpcode(Opcode.ROT3R); // new_val obj new_val
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // new_val obj new_val symbol
                            conpilerConext.emitter.emitOpcode(Opcode.SWAP); // new_val obj symbol new_val
                            conpilerConext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // new_val
                        } else {
                            // Postfix: obj.#field++ - returns old value (must be ToNumber'd per ES spec)
                            conpilerConext.emitter.emitOpcode(Opcode.DUP); // obj obj
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            conpilerConext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            conpilerConext.emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            conpilerConext.emitter.emitOpcode(Opcode.DUP); // obj old_numeric old_numeric
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(1));
                            conpilerConext.emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj old_val new_val
                            conpilerConext.emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // old_val new_val obj symbol
                            // Need: obj symbol new_val for PUT_PRIVATE_FIELD
                            // Have: old_val new_val obj symbol
                            // SWAP to get: old_val new_val symbol obj
                            conpilerConext.emitter.emitOpcode(Opcode.SWAP); // old_val new_val symbol obj
                            // ROT3L to get: old_val obj symbol new_val
                            conpilerConext.emitter.emitOpcode(Opcode.ROT3L); // old_val obj symbol new_val
                            conpilerConext.emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // old_val
                        }
                    } else {
                        throw new JSCompilerException("Invalid member expression property for increment/decrement");
                    }
                }
            } else if (operand instanceof CallExpression) {
                // Annex B: CallExpression as increment/decrement target throws ReferenceError at runtime.
                compileExpression(operand);
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
                conpilerConext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, "invalid increment/decrement operand");
                conpilerConext.emitter.emitU8(5); // JS_THROW_ERROR_INVALID_LVALUE
            } else {
                throw new JSCompilerException("Invalid operand for increment/decrement operator");
            }
            return;
        }

        if (unaryExpr.operator() == UnaryExpression.UnaryOperator.TYPEOF
                && unaryExpr.operand() instanceof Identifier id) {
            String name = id.name();
            if ("this".equals(name)) {
                conpilerConext.emitter.emitOpcode(Opcode.PUSH_THIS);
            } else if (JSArguments.NAME.equals(name) && !conpilerConext.inGlobalScope
                    && (!conpilerConext.isInArrowFunction || conpilerConext.hasEnclosingArgumentsBinding)
                    && conpilerConext.findLocalInScopes(name) == null) {
                conpilerConext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                conpilerConext.emitter.emitU8(0);
            } else {
                Integer localIndex = conpilerConext.findLocalInScopes(name);
                if (localIndex != null) {
                    // Use GET_LOC_CHECK for TDZ locals - typeof of an uninitialized
                    // lexical binding throws ReferenceError per ES spec
                    if (conpilerConext.tdzLocals.contains(name)) {
                        conpilerConext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
                    } else {
                        conpilerConext.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
                    }
                } else {
                    Integer capturedIndex = conpilerConext.resolveCapturedBindingIndex(name);
                    if (capturedIndex != null) {
                        conpilerConext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                    } else {
                        conpilerConext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                        conpilerConext.emitter.emitU8(5);
                        conpilerConext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
                    }
                }
            }
            conpilerConext.emitter.emitOpcode(Opcode.TYPEOF);
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
                conpilerConext.emitter.emitOpcode(Opcode.DROP);
                yield Opcode.UNDEFINED;
            }
            default -> throw new JSCompilerException("Unknown unary operator: " + unaryExpr.operator());
        };

        conpilerConext.emitter.emitOpcode(op);
    }

    void compileYieldExpression(YieldExpression yieldExpr) {
        // Compile the argument expression (if present)
        if (yieldExpr.argument() != null) {
            compileExpression(yieldExpr.argument());
        } else {
            // No argument means yield undefined
            conpilerConext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // Emit the appropriate yield opcode
        if (yieldExpr.delegate()) {
            // yield* delegates to another generator/iterable
            conpilerConext.emitter.emitOpcode(conpilerConext.isInAsyncFunction ? Opcode.ASYNC_YIELD_STAR : Opcode.YIELD_STAR);
        } else {
            // Regular yield
            conpilerConext.emitter.emitOpcode(Opcode.YIELD);
        }
    }

    private void emitIdentifierLookupWithoutWith(String name) {

        // Always check local scopes first, even in global scope (for nested blocks/loops)
        // This must happen BEFORE the 'arguments' special handling so that
        // explicit `var arguments` or `let arguments` declarations take precedence.
        // Following QuickJS: arguments is resolved through normal variable lookup first.
        Integer localIndex = conpilerConext.findLocalInScopes(name);

        if (localIndex != null) {
            // Use GET_LOC_CHECK for TDZ locals (let/const/class in program scope)
            // to throw ReferenceError if accessed before initialization
            if (conpilerConext.tdzLocals.contains(name)) {
                conpilerConext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
            } else {
                conpilerConext.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
            }
            return;
        }

        // Handle 'arguments' keyword in function scope (only if not found as a local)
        // For regular functions: SPECIAL_OBJECT creates the arguments object
        // For arrow functions with enclosing regular function: SPECIAL_OBJECT walks up call stack
        // For arrow functions without enclosing regular function: resolve as normal variable
        // Following QuickJS: arrow functions inherit arguments from enclosing scope,
        // but only if there is an enclosing scope with arguments binding
        if (JSArguments.NAME.equals(name) && !conpilerConext.inGlobalScope
                && (!conpilerConext.isInArrowFunction || conpilerConext.hasEnclosingArgumentsBinding)) {
            // Emit SPECIAL_OBJECT opcode with type 0 (SPECIAL_OBJECT_ARGUMENTS)
            // The VM will handle differently for arrow vs regular functions
            conpilerConext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            conpilerConext.emitter.emitU8(0);  // Type 0 = arguments object
            return;
        }

        Integer capturedIndex = conpilerConext.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            conpilerConext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
        } else {
            // Not found in local scopes, use global variable
            conpilerConext.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
        }
    }

    private void emitWithAwareIdentifierLookup(String name) {
        List<Integer> withObjectLocals = conpilerConext.getActiveWithObjectLocals();
        emitWithAwareIdentifierLookup(name, withObjectLocals, 0);
    }

    private void emitWithAwareIdentifierLookup(String name, List<Integer> withObjectLocals, int withDepth) {
        if (withDepth >= withObjectLocals.size()) {
            emitIdentifierLookupWithoutWith(name);
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        conpilerConext.emitter.emitOpcodeU16(Opcode.GET_LOCAL, withObjectLocalIndex);
        conpilerConext.emitter.emitOpcode(Opcode.DUP);
        conpilerConext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        conpilerConext.emitter.emitOpcode(Opcode.ROT3L);
        conpilerConext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = conpilerConext.emitter.emitJump(Opcode.IF_FALSE);
        conpilerConext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEnd = conpilerConext.emitter.emitJump(Opcode.GOTO);

        conpilerConext.emitter.patchJump(jumpToFallback, conpilerConext.emitter.currentOffset());
        conpilerConext.emitter.emitOpcode(Opcode.DROP);
        emitWithAwareIdentifierLookup(name, withObjectLocals, withDepth + 1);
        conpilerConext.emitter.patchJump(jumpToEnd, conpilerConext.emitter.currentOffset());
    }
}
