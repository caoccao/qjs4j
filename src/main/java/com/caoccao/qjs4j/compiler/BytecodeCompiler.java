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

package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.compiler.ast.*;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.math.BigInteger;
import java.util.*;

/**
 * Compiles AST into bytecode.
 * Implements visitor pattern for traversing AST nodes and emitting appropriate bytecode.
 */
public final class BytecodeCompiler {
    private final BytecodeEmitter emitter;
    private final Deque<LoopContext> loopStack;
    private final Deque<Scope> scopes;
    private boolean inGlobalScope;

    public BytecodeCompiler() {
        this.emitter = new BytecodeEmitter();
        this.scopes = new ArrayDeque<>();
        this.loopStack = new ArrayDeque<>();
        this.inGlobalScope = false;
    }

    /**
     * Compile an AST node into bytecode.
     */
    public Bytecode compile(ASTNode ast) {
        if (ast instanceof Program program) {
            compileProgram(program);
        } else {
            throw new IllegalArgumentException("Expected Program node, got: " + ast.getClass().getName());
        }
        int localCount = scopes.isEmpty() ? 0 : currentScope().getLocalCount();
        return emitter.build(localCount);
    }

    // ==================== Program Compilation ====================

    private void compileArrayExpression(ArrayExpression arrayExpr) {
        emitter.emitOpcode(Opcode.ARRAY_NEW);

        for (Expression element : arrayExpr.elements()) {
            if (element != null) {
                compileExpression(element);
                emitter.emitOpcode(Opcode.PUSH_ARRAY);
            }
        }
    }

    // ==================== Statement Compilation ====================

    private void compileArrowFunctionExpression(ArrowFunctionExpression arrowExpr) {
        // Create a new compiler for the function body
        BytecodeCompiler functionCompiler = new BytecodeCompiler();

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;

        for (Identifier param : arrowExpr.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // Compile function body
        // Arrow functions can have expression body or block statement body
        if (arrowExpr.body() instanceof BlockStatement block) {
            // Compile block body statements (don't call compileBlockStatement as it would create a new scope)
            for (Statement stmt : block.body()) {
                functionCompiler.compileStatement(stmt);
            }

            // If body doesn't end with return, add implicit return undefined
            List<Statement> bodyStatements = block.body();
            if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
                functionCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
                functionCompiler.emitter.emitOpcode(Opcode.RETURN);
            }
        } else if (arrowExpr.body() instanceof Expression expr) {
            // Expression body - implicitly returns the expression value
            functionCompiler.compileExpression(expr);
            functionCompiler.emitter.emitOpcode(Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount);

        // Arrow functions are always anonymous
        String functionName = "";

        // Create JSBytecodeFunction
        // Arrow functions cannot be constructors
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                arrowExpr.params().size(),
                new JSValue[0],  // closure vars - for now empty
                null,            // prototype - arrow functions don't have prototype
                false,           // isConstructor - arrow functions cannot be constructors
                arrowExpr.isAsync(),
                false            // Arrow functions cannot be generators
        );

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        // Emit FCLOSURE opcode with function in constant pool
        emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    private void compileAssignmentExpression(AssignmentExpression assignExpr) {
        // Compile right side
        compileExpression(assignExpr.right());

        // Handle assignment to different patterns
        Expression left = assignExpr.left();

        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = currentScope().getLocal(name);

            if (localIndex != null) {
                emitter.emitOpcodeU16(Opcode.SET_LOCAL, localIndex);
            } else {
                emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
            }
        } else if (left instanceof MemberExpression memberExpr) {
            // obj[prop] = value or obj.prop = value
            compileExpression(memberExpr.object());

            if (memberExpr.computed()) {
                compileExpression(memberExpr.property());
                emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
            }
        }
    }

    private void compileBinaryExpression(BinaryExpression binExpr) {
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
            case LOGICAL_AND -> Opcode.LOGICAL_AND;
            case LOGICAL_OR -> Opcode.LOGICAL_OR;
            case LSHIFT -> Opcode.SHL;
            case LT -> Opcode.LT;
            case MOD -> Opcode.MOD;
            case MUL -> Opcode.MUL;
            case NE -> Opcode.NEQ;
            case NULLISH_COALESCING -> Opcode.NULLISH_COALESCE;
            case RSHIFT -> Opcode.SAR;
            case STRICT_EQ -> Opcode.STRICT_EQ;
            case STRICT_NE -> Opcode.STRICT_NEQ;
            case SUB -> Opcode.SUB;
            case URSHIFT -> Opcode.SHR;
            default -> throw new CompilerException("Unknown binary operator: " + binExpr.operator());
        };

        emitter.emitOpcode(op);
    }

    private void compileBlockStatement(BlockStatement block) {
        enterScope();
        for (Statement stmt : block.body()) {
            compileStatement(stmt);
        }
        exitScope();
    }

    private void compileBreakStatement(BreakStatement breakStmt) {
        if (loopStack.isEmpty()) {
            throw new CompilerException("Break statement outside of loop");
        }
        int jumpPos = emitter.emitJump(Opcode.GOTO);
        loopStack.peek().breakPositions.add(jumpPos);
    }

    private void compileCallExpression(CallExpression callExpr) {
        // Check if this is a method call (callee is a member expression)
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            // For method calls: obj.method()
            // We need to preserve obj as the 'this' value

            // Push object (receiver)
            compileExpression(memberExpr.object());

            // Duplicate it (one copy for 'this', one for property access)
            emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                // obj[expr]
                compileExpression(memberExpr.property());
                emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                // obj.prop
                emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            // Now stack is: receiver, method
            // Swap so method is on top: method, receiver
            emitter.emitOpcode(Opcode.SWAP);

            // Push arguments
            for (Expression arg : callExpr.arguments()) {
                compileExpression(arg);
            }

            // Call with argument count (will use receiver as thisArg)
            emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        } else {
            // Regular function call: func()
            // Push callee
            compileExpression(callExpr.callee());

            // Push undefined as receiver (thisArg for regular calls)
            emitter.emitOpcode(Opcode.UNDEFINED);

            // Push arguments
            for (Expression arg : callExpr.arguments()) {
                compileExpression(arg);
            }

            // Call with argument count
            emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        }
    }

    private void compileConditionalExpression(ConditionalExpression condExpr) {
        // Compile test
        compileExpression(condExpr.test());

        // Jump to alternate if false
        int jumpToAlternate = emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent
        compileExpression(condExpr.consequent());

        // Jump over alternate
        int jumpToEnd = emitter.emitJump(Opcode.GOTO);

        // Patch jump to alternate
        emitter.patchJump(jumpToAlternate, emitter.currentOffset());

        // Compile alternate
        compileExpression(condExpr.alternate());

        // Patch jump to end
        emitter.patchJump(jumpToEnd, emitter.currentOffset());
    }

    private void compileContinueStatement(ContinueStatement contStmt) {
        if (loopStack.isEmpty()) {
            throw new CompilerException("Continue statement outside of loop");
        }
        int jumpPos = emitter.emitJump(Opcode.GOTO);
        loopStack.peek().continuePositions.add(jumpPos);
    }

    private void compileExpression(Expression expr) {
        if (expr instanceof Literal literal) {
            compileLiteral(literal);
        } else if (expr instanceof Identifier id) {
            compileIdentifier(id);
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
            compileFunctionExpression(funcExpr);
        } else if (expr instanceof ArrowFunctionExpression arrowExpr) {
            compileArrowFunctionExpression(arrowExpr);
        } else if (expr instanceof ArrayExpression arrayExpr) {
            compileArrayExpression(arrayExpr);
        } else if (expr instanceof ObjectExpression objExpr) {
            compileObjectExpression(objExpr);
        }
    }

    private void compileForStatement(ForStatement forStmt) {
        enterScope();

        // Compile init
        if (forStmt.init() != null) {
            if (forStmt.init() instanceof VariableDeclaration varDecl) {
                compileVariableDeclaration(varDecl);
            } else if (forStmt.init() instanceof Expression expr) {
                compileExpression(expr);
                emitter.emitOpcode(Opcode.DROP);
            }
        }

        int loopStart = emitter.currentOffset();
        LoopContext loop = new LoopContext(loopStart);
        loopStack.push(loop);

        int jumpToEnd = -1;
        // Compile test
        if (forStmt.test() != null) {
            compileExpression(forStmt.test());
            jumpToEnd = emitter.emitJump(Opcode.IF_FALSE);
        }

        // Compile body
        compileStatement(forStmt.body());

        // Update position for continue statements
        int updateStart = emitter.currentOffset();

        // Compile update
        if (forStmt.update() != null) {
            compileExpression(forStmt.update());
            emitter.emitOpcode(Opcode.DROP);
        }

        // Jump back to test
        emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = emitter.currentOffset();
        emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = emitter.currentOffset();

        // Patch test jump
        if (jumpToEnd != -1) {
            emitter.patchJump(jumpToEnd, loopEnd);
        }

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, loopEnd);
        }

        // Patch continue statements
        for (int continuePos : loop.continuePositions) {
            emitter.patchJump(continuePos, updateStart);
        }

        loopStack.pop();
        exitScope();
    }

    private void compileFunctionExpression(FunctionExpression funcExpr) {
        // Create a new compiler for the function body
        BytecodeCompiler functionCompiler = new BytecodeCompiler();

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;

        for (Identifier param : funcExpr.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // Compile function body statements (don't call compileBlockStatement as it would create a new scope)
        for (Statement stmt : funcExpr.body().body()) {
            functionCompiler.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        // Check if last statement is a return statement
        List<Statement> bodyStatements = funcExpr.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
            functionCompiler.emitter.emitOpcode(Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount);

        // Get function name (empty string for anonymous)
        String functionName = funcExpr.id() != null ? funcExpr.id().name() : "";

        // Create JSBytecodeFunction
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                funcExpr.params().size(),
                new JSValue[0],  // closure vars - for now empty
                null,            // prototype - will be set by VM
                true,            // isConstructor - regular functions can be constructors
                funcExpr.isAsync(),
                funcExpr.isGenerator()
        );

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        // Emit FCLOSURE opcode with function in constant pool
        emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    private void compileIdentifier(Identifier id) {
        String name = id.name();

        // Handle 'this' keyword
        if ("this".equals(name)) {
            emitter.emitOpcode(Opcode.PUSH_THIS);
            return;
        }

        if (inGlobalScope) {
            // In global scope, always use GET_VAR
            emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
        } else {
            // In function scope, check locals first
            Integer localIndex = currentScope().getLocal(name);

            if (localIndex != null) {
                emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
            } else {
                // Try outer scopes or global
                emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
            }
        }
    }

    private void compileIfStatement(IfStatement ifStmt) {
        // Compile condition
        compileExpression(ifStmt.test());

        // Jump to else/end if condition is false
        int jumpToElse = emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent
        compileStatement(ifStmt.consequent());

        if (ifStmt.alternate() != null) {
            // Jump over else block after consequent
            int jumpToEnd = emitter.emitJump(Opcode.GOTO);

            // Patch jump to else
            emitter.patchJump(jumpToElse, emitter.currentOffset());

            // Compile alternate
            compileStatement(ifStmt.alternate());

            // Patch jump to end
            emitter.patchJump(jumpToEnd, emitter.currentOffset());
        } else {
            // Patch jump to end
            emitter.patchJump(jumpToElse, emitter.currentOffset());
        }
    }

    // ==================== Expression Compilation ====================

    private void compileLiteral(Literal literal) {
        Object value = literal.value();

        if (value == null) {
            emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
        } else if (value instanceof BigInteger bigInt) {
            // Check BigInteger before Number since BigInteger extends Number
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
        } else if (value instanceof Number num) {
            // Try to emit as i32 if it's an integer in range
            if (num instanceof Integer || num instanceof Long) {
                long longValue = num.longValue();
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    emitter.emitOpcode(Opcode.PUSH_I32);
                    emitter.emitI32((int) longValue);
                    return;
                }
            }
            // Otherwise emit as constant
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(num.doubleValue()));
        } else if (value instanceof String str) {
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
        } else {
            // Other types as constants
            throw new CompilerException("Unsupported literal type: " + value.getClass());
        }
    }

    private void compileMemberExpression(MemberExpression memberExpr) {
        compileExpression(memberExpr.object());

        if (memberExpr.computed()) {
            // obj[expr]
            compileExpression(memberExpr.property());
            emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.property() instanceof Identifier propId) {
            // obj.prop
            emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
        }
    }

    private void compileNewExpression(NewExpression newExpr) {
        // Push constructor
        compileExpression(newExpr.callee());

        // Push arguments
        for (Expression arg : newExpr.arguments()) {
            compileExpression(arg);
        }

        // Call constructor
        emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.arguments().size());
    }

    private void compileObjectExpression(ObjectExpression objExpr) {
        emitter.emitOpcode(Opcode.OBJECT_NEW);

        for (ObjectExpression.Property prop : objExpr.properties()) {
            // Push key
            if (prop.key() instanceof Identifier id) {
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
            } else {
                compileExpression(prop.key());
            }

            // Push value
            compileExpression(prop.value());

            // Define property
            emitter.emitOpcode(Opcode.DEFINE_PROP);
        }
    }

    private void compilePatternAssignment(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: value is on stack, just assign it
            String varName = id.name();
            if (inGlobalScope) {
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            } else {
                int localIndex = currentScope().declareLocal(varName);
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: { proxy, revoke } = value
            // Stack: [object]
            for (ObjectPattern.Property prop : objPattern.properties()) {
                // Get the property name
                String propName = ((Identifier) prop.key()).name();

                // Duplicate object for each property access
                emitter.emitOpcode(Opcode.DUP);
                // Get the property value
                emitter.emitOpcodeAtom(Opcode.GET_FIELD, propName);
                // Assign to the pattern (could be nested)
                compilePatternAssignment(prop.value());
            }
            // Drop the original object
            emitter.emitOpcode(Opcode.DROP);
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: [a, b] = value
            // Stack: [array]
            int index = 0;
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    // Duplicate array
                    emitter.emitOpcode(Opcode.DUP);
                    // Push index
                    emitter.emitOpcode(Opcode.PUSH_I32);
                    emitter.emitI32(index);
                    // Get array element
                    emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                    // Assign to the pattern
                    compilePatternAssignment(element);
                }
                index++;
            }
            // Drop the original array
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    private void compileProgram(Program program) {
        inGlobalScope = true;
        enterScope();

        List<Statement> body = program.body();
        int lastIndex = body.size() - 1;
        boolean lastIsExpression = false;

        for (int i = 0; i < body.size(); i++) {
            boolean isLast = (i == lastIndex);
            Statement stmt = body.get(i);

            if (isLast && stmt instanceof ExpressionStatement) {
                lastIsExpression = true;
            }

            compileStatement(stmt, isLast);
        }

        // If last statement wasn't an expression, push undefined
        if (!lastIsExpression) {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // Return the value on top of stack
        emitter.emitOpcode(Opcode.RETURN);

        exitScope();
        inGlobalScope = false;
    }

    private void compileReturnStatement(ReturnStatement retStmt) {
        if (retStmt.argument() != null) {
            compileExpression(retStmt.argument());
        } else {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        emitter.emitOpcode(Opcode.RETURN);
    }

    private void compileStatement(Statement stmt) {
        compileStatement(stmt, false);
    }

    private void compileStatement(Statement stmt, boolean isLastInProgram) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            compileExpression(exprStmt.expression());
            // Only drop the result if this is not the last statement in the program
            if (!isLastInProgram) {
                emitter.emitOpcode(Opcode.DROP);
            }
        } else if (stmt instanceof BlockStatement block) {
            compileBlockStatement(block);
        } else if (stmt instanceof IfStatement ifStmt) {
            compileIfStatement(ifStmt);
        } else if (stmt instanceof WhileStatement whileStmt) {
            compileWhileStatement(whileStmt);
        } else if (stmt instanceof ForStatement forStmt) {
            compileForStatement(forStmt);
        } else if (stmt instanceof ReturnStatement retStmt) {
            compileReturnStatement(retStmt);
        } else if (stmt instanceof BreakStatement breakStmt) {
            compileBreakStatement(breakStmt);
        } else if (stmt instanceof ContinueStatement contStmt) {
            compileContinueStatement(contStmt);
        } else if (stmt instanceof ThrowStatement throwStmt) {
            compileThrowStatement(throwStmt);
        } else if (stmt instanceof TryStatement tryStmt) {
            compileTryStatement(tryStmt);
        } else if (stmt instanceof SwitchStatement switchStmt) {
            compileSwitchStatement(switchStmt);
        } else if (stmt instanceof VariableDeclaration varDecl) {
            compileVariableDeclaration(varDecl);
        }
    }

    private void compileSwitchStatement(SwitchStatement switchStmt) {
        // Compile discriminant
        compileExpression(switchStmt.discriminant());

        List<Integer> caseJumps = new ArrayList<>();
        List<Integer> caseBodyStarts = new ArrayList<>();

        // Emit comparisons for each case
        for (SwitchStatement.SwitchCase switchCase : switchStmt.cases()) {
            if (switchCase.test() != null) {
                // Duplicate discriminant for comparison
                emitter.emitOpcode(Opcode.DUP);
                compileExpression(switchCase.test());
                emitter.emitOpcode(Opcode.STRICT_EQ);

                int jumpToCase = emitter.emitJump(Opcode.IF_TRUE);
                caseJumps.add(jumpToCase);
            }
        }

        // Drop discriminant
        emitter.emitOpcode(Opcode.DROP);

        // Jump to default or end
        int jumpToDefault = emitter.emitJump(Opcode.GOTO);

        // Compile case bodies
        LoopContext loop = new LoopContext(emitter.currentOffset());
        loopStack.push(loop);

        for (int i = 0; i < switchStmt.cases().size(); i++) {
            SwitchStatement.SwitchCase switchCase = switchStmt.cases().get(i);

            if (switchCase.test() != null) {
                int bodyStart = emitter.currentOffset();
                caseBodyStarts.add(bodyStart);
            }

            for (Statement stmt : switchCase.consequent()) {
                compileStatement(stmt);
            }
        }

        int switchEnd = emitter.currentOffset();

        // Patch case jumps
        for (int i = 0; i < caseJumps.size(); i++) {
            emitter.patchJump(caseJumps.get(i), caseBodyStarts.get(i));
        }

        // Patch default jump
        boolean hasDefault = switchStmt.cases().stream().anyMatch(c -> c.test() == null);
        if (hasDefault) {
            // Find default case body start
            int defaultIndex = 0;
            for (int i = 0; i < switchStmt.cases().size(); i++) {
                if (switchStmt.cases().get(i).test() == null) {
                    defaultIndex = i;
                    break;
                }
            }
            int defaultStart = caseBodyStarts.isEmpty() ? switchEnd :
                    (defaultIndex < caseBodyStarts.size() ? caseBodyStarts.get(defaultIndex) : switchEnd);
            emitter.patchJump(jumpToDefault, defaultStart);
        } else {
            emitter.patchJump(jumpToDefault, switchEnd);
        }

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, switchEnd);
        }

        loopStack.pop();
    }

    private void compileThrowStatement(ThrowStatement throwStmt) {
        compileExpression(throwStmt.argument());
        emitter.emitOpcode(Opcode.THROW);
    }

    private void compileTryStatement(TryStatement tryStmt) {
        // Mark catch handler location
        int catchJump = -1;
        if (tryStmt.handler() != null) {
            catchJump = emitter.emitJump(Opcode.CATCH);
        }

        // Compile try block
        compileBlockStatement(tryStmt.block());

        // Jump over catch block
        int jumpOverCatch = emitter.emitJump(Opcode.GOTO);

        if (tryStmt.handler() != null) {
            // Patch catch jump
            emitter.patchJump(catchJump, emitter.currentOffset());

            // Catch handler puts exception on stack
            TryStatement.CatchClause handler = tryStmt.handler();

            // Bind exception to parameter if present
            if (handler.param() != null) {
                enterScope();
                String paramName = handler.param().name();
                int localIndex = currentScope().declareLocal(paramName);
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }

            // Compile catch body
            compileBlockStatement(handler.body());

            if (handler.param() != null) {
                exitScope();
            }
        }

        // Patch jump over catch
        emitter.patchJump(jumpOverCatch, emitter.currentOffset());

        // Compile finally block
        if (tryStmt.finalizer() != null) {
            compileBlockStatement(tryStmt.finalizer());
        }
    }

    private void compileUnaryExpression(UnaryExpression unaryExpr) {
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
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.name()));
                }

                emitter.emitOpcode(Opcode.DELETE);
            } else {
                // delete identifier or delete literal - always returns true
                emitter.emitOpcode(Opcode.PUSH_TRUE);
            }
            return;
        }

        compileExpression(unaryExpr.operand());

        Opcode op = switch (unaryExpr.operator()) {
            case BIT_NOT -> Opcode.NOT;
            case DEC -> Opcode.DEC;
            case INC -> Opcode.INC;
            case MINUS -> Opcode.NEG;
            case NOT -> Opcode.LOGICAL_NOT;
            case PLUS -> Opcode.PLUS;
            case TYPEOF -> Opcode.TYPEOF;
            case VOID -> {
                emitter.emitOpcode(Opcode.DROP);
                yield Opcode.UNDEFINED;
            }
            default -> throw new CompilerException("Unknown unary operator: " + unaryExpr.operator());
        };

        emitter.emitOpcode(op);
    }

    private void compileVariableDeclaration(VariableDeclaration varDecl) {
        for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
            // Compile initializer or push undefined
            if (declarator.init() != null) {
                compileExpression(declarator.init());
            } else {
                emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Assign to pattern (handles Identifier, ObjectPattern, ArrayPattern)
            compilePatternAssignment(declarator.id());
        }
    }

    private void compileWhileStatement(WhileStatement whileStmt) {
        int loopStart = emitter.currentOffset();
        LoopContext loop = new LoopContext(loopStart);
        loopStack.push(loop);

        // Compile test condition
        compileExpression(whileStmt.test());

        // Jump to end if false
        int jumpToEnd = emitter.emitJump(Opcode.IF_FALSE);

        // Compile body
        compileStatement(whileStmt.body());

        // Jump back to start
        emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = emitter.currentOffset();
        emitter.emitU32(loopStart - (backJumpPos + 4));

        // Patch end jump
        int loopEnd = emitter.currentOffset();
        emitter.patchJump(jumpToEnd, loopEnd);

        // Patch all break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, loopEnd);
        }

        // Patch all continue statements
        for (int continuePos : loop.continuePositions) {
            emitter.patchJump(continuePos, loopStart);
        }

        loopStack.pop();
    }

    // ==================== Scope Management ====================

    private Scope currentScope() {
        if (scopes.isEmpty()) {
            throw new CompilerException("No scope available");
        }
        return scopes.peek();
    }

    private void enterScope() {
        scopes.push(new Scope());
    }

    private void exitScope() {
        scopes.pop();
    }

    /**
     * Compiler exception for compilation errors.
     */
    public static class CompilerException extends RuntimeException {
        public CompilerException(String message) {
            super(message);
        }
    }

    /**
     * Tracks loop context for break/continue statements.
     */
    private static class LoopContext {
        final List<Integer> breakPositions = new ArrayList<>();
        final List<Integer> continuePositions = new ArrayList<>();
        final int startOffset;

        LoopContext(int startOffset) {
            this.startOffset = startOffset;
        }
    }

    /**
     * Represents a lexical scope for tracking local variables.
     */
    private static class Scope {
        private final Map<String, Integer> locals = new HashMap<>();
        private int nextLocalIndex = 0;

        int declareLocal(String name) {
            if (locals.containsKey(name)) {
                return locals.get(name);
            }
            int index = nextLocalIndex++;
            locals.put(name, index);
            return index;
        }

        Integer getLocal(String name) {
            return locals.get(name);
        }

        int getLocalCount() {
            return nextLocalIndex;
        }
    }
}
