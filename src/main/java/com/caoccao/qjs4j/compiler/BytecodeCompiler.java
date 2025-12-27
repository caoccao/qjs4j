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
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.*;

/**
 * Compiles AST into bytecode.
 * Implements visitor pattern for traversing AST nodes and emitting appropriate bytecode.
 */
public final class BytecodeCompiler {
    private final BytecodeEmitter emitter;
    private final Deque<Scope> scopes;
    private final Deque<LoopContext> loopStack;
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
        return emitter.build();
    }

    // ==================== Program Compilation ====================

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

    // ==================== Statement Compilation ====================

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

    private void compileBlockStatement(BlockStatement block) {
        enterScope();
        for (Statement stmt : block.body()) {
            compileStatement(stmt);
        }
        exitScope();
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

    private void compileReturnStatement(ReturnStatement retStmt) {
        if (retStmt.argument() != null) {
            compileExpression(retStmt.argument());
        } else {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        emitter.emitOpcode(Opcode.RETURN);
    }

    private void compileBreakStatement(BreakStatement breakStmt) {
        if (loopStack.isEmpty()) {
            throw new CompilerException("Break statement outside of loop");
        }
        int jumpPos = emitter.emitJump(Opcode.GOTO);
        loopStack.peek().breakPositions.add(jumpPos);
    }

    private void compileContinueStatement(ContinueStatement contStmt) {
        if (loopStack.isEmpty()) {
            throw new CompilerException("Continue statement outside of loop");
        }
        int jumpPos = emitter.emitJump(Opcode.GOTO);
        loopStack.peek().continuePositions.add(jumpPos);
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

    private void compileVariableDeclaration(VariableDeclaration varDecl) {
        for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
            String varName = declarator.id().name();

            // Compile initializer or push undefined
            if (declarator.init() != null) {
                compileExpression(declarator.init());
            } else {
                emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Store to variable
            if (inGlobalScope) {
                // Global variables go to the global object
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            } else {
                // Local variables use local slots
                int localIndex = currentScope().declareLocal(varName);
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        }
    }

    // ==================== Expression Compilation ====================

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

    private void compileLiteral(Literal literal) {
        Object value = literal.value();

        if (value == null) {
            emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
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

    private void compileIdentifier(Identifier id) {
        String name = id.name();

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

    private void compileBinaryExpression(BinaryExpression binExpr) {
        // Compile operands
        compileExpression(binExpr.left());
        compileExpression(binExpr.right());

        // Emit operation
        Opcode op;
        switch (binExpr.operator()) {
            case ADD:
                op = Opcode.ADD;
                break;
            case SUB:
                op = Opcode.SUB;
                break;
            case MUL:
                op = Opcode.MUL;
                break;
            case DIV:
                op = Opcode.DIV;
                break;
            case MOD:
                op = Opcode.MOD;
                break;
            case EXP:
                op = Opcode.EXP;
                break;
            case EQ:
                op = Opcode.EQ;
                break;
            case NE:
                op = Opcode.NEQ;
                break;
            case STRICT_EQ:
                op = Opcode.STRICT_EQ;
                break;
            case STRICT_NE:
                op = Opcode.STRICT_NEQ;
                break;
            case LT:
                op = Opcode.LT;
                break;
            case LE:
                op = Opcode.LTE;
                break;
            case GT:
                op = Opcode.GT;
                break;
            case GE:
                op = Opcode.GTE;
                break;
            case BIT_AND:
                op = Opcode.AND;
                break;
            case BIT_OR:
                op = Opcode.OR;
                break;
            case BIT_XOR:
                op = Opcode.XOR;
                break;
            case LSHIFT:
                op = Opcode.SHL;
                break;
            case RSHIFT:
                op = Opcode.SAR;
                break;
            case URSHIFT:
                op = Opcode.SHR;
                break;
            case LOGICAL_AND:
                op = Opcode.LOGICAL_AND;
                break;
            case LOGICAL_OR:
                op = Opcode.LOGICAL_OR;
                break;
            case NULLISH_COALESCING:
                op = Opcode.NULLISH_COALESCE;
                break;
            case IN:
                op = Opcode.IN;
                break;
            case INSTANCEOF:
                op = Opcode.INSTANCEOF;
                break;
            default:
                throw new CompilerException("Unknown binary operator: " + binExpr.operator());
        }

        emitter.emitOpcode(op);
    }

    private void compileUnaryExpression(UnaryExpression unaryExpr) {
        compileExpression(unaryExpr.operand());

        Opcode op;
        switch (unaryExpr.operator()) {
            case PLUS:
                op = Opcode.PLUS;
                break;
            case MINUS:
                op = Opcode.NEG;
                break;
            case NOT:
                op = Opcode.LOGICAL_NOT;
                break;
            case BIT_NOT:
                op = Opcode.NOT;
                break;
            case TYPEOF:
                op = Opcode.TYPEOF;
                break;
            case VOID:
                emitter.emitOpcode(Opcode.DROP);
                op = Opcode.UNDEFINED;
                break;
            case DELETE:
                op = Opcode.DELETE;
                break;
            case INC:
                op = Opcode.INC;
                break;
            case DEC:
                op = Opcode.DEC;
                break;
            default:
                throw new CompilerException("Unknown unary operator: " + unaryExpr.operator());
        }

        emitter.emitOpcode(op);
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

    private void compileFunctionExpression(FunctionExpression funcExpr) {
        // For now, emit a placeholder
        // Full implementation would compile function body into separate bytecode
        emitter.emitOpcode(Opcode.UNDEFINED);
    }

    private void compileArrowFunctionExpression(ArrowFunctionExpression arrowExpr) {
        // For now, emit a placeholder
        // Full implementation would compile function body into separate bytecode
        emitter.emitOpcode(Opcode.UNDEFINED);
    }

    private void compileArrayExpression(ArrayExpression arrayExpr) {
        emitter.emitOpcode(Opcode.ARRAY_NEW);

        for (Expression element : arrayExpr.elements()) {
            if (element != null) {
                compileExpression(element);
                emitter.emitOpcode(Opcode.PUSH_ARRAY);
            }
        }
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

    // ==================== Scope Management ====================

    private void enterScope() {
        scopes.push(new Scope());
    }

    private void exitScope() {
        scopes.pop();
    }

    private Scope currentScope() {
        if (scopes.isEmpty()) {
            throw new CompilerException("No scope available");
        }
        return scopes.peek();
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
    }

    /**
     * Tracks loop context for break/continue statements.
     */
    private static class LoopContext {
        final int startOffset;
        final List<Integer> breakPositions = new ArrayList<>();
        final List<Integer> continuePositions = new ArrayList<>();

        LoopContext(int startOffset) {
            this.startOffset = startOffset;
        }
    }

    /**
     * Compiler exception for compilation errors.
     */
    public static class CompilerException extends RuntimeException {
        public CompilerException(String message) {
            super(message);
        }
    }
}
