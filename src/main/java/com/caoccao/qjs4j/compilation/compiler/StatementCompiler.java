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
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiles statement AST nodes into bytecode.
 * Extracted from BytecodeCompiler to separate statement compilation concerns.
 */
final class StatementCompiler {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;
    private final StatementLoopCompiler loopCompiler;

    StatementCompiler(CompilerContext ctx, CompilerDelegates delegates) {
        this.ctx = ctx;
        this.delegates = delegates;
        loopCompiler = new StatementLoopCompiler(this, ctx, delegates);
    }

    void compileBlockStatement(BlockStatement block) {
        boolean savedGlobalScope = ctx.inGlobalScope;
        ctx.enterScope();
        ctx.inGlobalScope = false;
        // Hoist function declarations to top of block (ES2015+ block-scoped functions
        // are initialized before any statements in the block execute).
        for (Statement stmt : block.body()) {
            if (stmt instanceof FunctionDeclaration funcDecl) {
                delegates.functions.compileFunctionDeclaration(funcDecl);
            }
        }
        for (Statement stmt : block.body()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted
            }
            compileStatement(stmt);
        }
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.inGlobalScope = savedGlobalScope;
        ctx.exitScope();
    }

    void compileBreakStatement(BreakStatement breakStmt) {
        loopCompiler.compileBreakStatement(breakStmt);
    }

    void compileContinueStatement(ContinueStatement contStmt) {
        loopCompiler.compileContinueStatement(contStmt);
    }

    void compileForInStatement(ForInStatement forInStmt) {
        loopCompiler.compileForInStatement(forInStmt);
    }

    void compileForOfStatement(ForOfStatement forOfStmt) {
        loopCompiler.compileForOfStatement(forOfStmt);
    }

    void compileForStatement(ForStatement forStmt) {
        loopCompiler.compileForStatement(forStmt);
    }

    void compileIfStatement(IfStatement ifStmt) {
        // In strict mode, function declarations are not allowed as direct body of if/else
        // (they must be at top level or inside a block). Per ES2024 B.3.3 Note.
        if (ctx.strictMode) {
            if (ifStmt.consequent() instanceof FunctionDeclaration
                    || ifStmt.alternate() instanceof FunctionDeclaration) {
                throw new JSSyntaxErrorException(
                        "In strict mode code, functions can only be declared at top level or inside a block.");
            }
        }

        // Compile condition
        delegates.expressions.compileExpression(ifStmt.test());

        // Jump to else/end if condition is false
        int jumpToElse = ctx.emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent — wrap bare function declarations in implicit block scope
        compileImplicitBlockStatement(ifStmt.consequent());

        if (ifStmt.alternate() != null) {
            // Jump over else block after consequent
            int jumpToEnd = ctx.emitter.emitJump(Opcode.GOTO);

            // Patch jump to else
            ctx.emitter.patchJump(jumpToElse, ctx.emitter.currentOffset());

            // Compile alternate — wrap bare function declarations in implicit block scope
            compileImplicitBlockStatement(ifStmt.alternate());

            // Patch jump to end
            ctx.emitter.patchJump(jumpToEnd, ctx.emitter.currentOffset());
        } else {
            // Patch jump to end
            ctx.emitter.patchJump(jumpToElse, ctx.emitter.currentOffset());
        }
    }

    /**
     * Compile a statement that may be a bare function declaration in sloppy mode.
     * Per ES2024 B.3.3, function declarations in if-statement positions are treated
     * as if wrapped in a block. This ensures the function binding is block-scoped
     * and does not overwrite outer let/const bindings when Annex B is skipped.
     */
    void compileImplicitBlockStatement(Statement stmt) {
        if (stmt instanceof FunctionDeclaration funcDecl && funcDecl.id() != null) {
            // Per ES2024 B.3.3, function declarations in if-statement positions
            // are treated as if wrapped in a block scope.
            ctx.enterScope();
            ctx.currentScope().declareLocal(funcDecl.id().name());
            delegates.functions.compileFunctionDeclaration(funcDecl);
            delegates.emitHelpers.emitCurrentScopeUsingDisposal();
            ctx.exitScope();
        } else {
            compileStatement(stmt);
        }
    }

    /**
     * Compile a labeled loop: the label is attached to the loop's LoopContext.
     * This is needed so that 'break label;' and 'continue label;' work on the loop.
     */
    void compileLabeledLoop(String labelName, Statement loopStmt) {
        // We temporarily store the label name so the loop compilation methods can pick it up
        ctx.pendingLoopLabel = labelName;
        compileStatement(loopStmt);
        ctx.pendingLoopLabel = null;
    }

    /**
     * Compile a labeled statement following QuickJS js_parse_statement_or_decl.
     * Creates a break entry so that 'break label;' jumps past the labeled body.
     * For labeled loops (while/for/for-in/for-of), the label is attached to the
     * loop's LoopContext so labeled break/continue work on the loop.
     */
    void compileLabeledStatement(LabeledStatement labeledStmt) {
        String labelName = labeledStmt.label().name();
        Statement body = labeledStmt.body();

        // In strict mode, function declarations are not allowed as labeled statement body
        // (they must be at top level or inside a block). Per ES2024 B.3.3 Note.
        if (ctx.strictMode && body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "In strict mode code, functions can only be declared at top level or inside a block.");
        }

        // Check if the body is a loop statement — if so, the label applies to the loop
        if (body instanceof WhileStatement || body instanceof ForStatement
                || body instanceof ForInStatement || body instanceof ForOfStatement) {
            // Push a labeled loop context; the loop compilation will use loopStack.peek()
            // We need to wrap the loop compilation to attach the label
            compileLabeledLoop(labelName, body);
        } else {
            // Regular labeled statement: only 'break label;' is valid (not continue)
            LoopContext labelContext = new LoopContext(ctx.emitter.currentOffset(), ctx.scopeDepth, ctx.scopeDepth, labelName);
            labelContext.isRegularStmt = true;
            ctx.loopStack.push(labelContext);

            // Body can be null for empty statements (label: ;)
            if (body != null) {
                compileStatement(body);
            }

            // Patch all break positions to jump here
            int breakTarget = ctx.emitter.currentOffset();
            for (int pos : labelContext.breakPositions) {
                ctx.emitter.patchJump(pos, breakTarget);
            }
            ctx.loopStack.pop();
        }
    }

    void compileProgram(Program program) {
        ctx.inGlobalScope = true;
        ctx.isGlobalProgram = true;
        ctx.strictMode = program.strict();  // Set strict mode from program directive
        ctx.enterScope();

        // Pre-declare class declarations as locals with TDZ.
        // Per ES spec EvalDeclarationInstantiation step 14: lexically declared names
        // are instantiated here but not initialized. Accessing them before their
        // declaration throws ReferenceError (temporal dead zone).
        // Following QuickJS js_closure_define_global_var pattern for eval lexical bindings.
        for (Statement stmt : program.body()) {
            if (stmt instanceof ClassDeclaration classDecl && classDecl.id() != null) {
                String name = classDecl.id().name();
                int localIndex = ctx.currentScope().declareLocal(name);
                ctx.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                ctx.tdzLocals.add(name);
            }
        }

        delegates.analysis.registerGlobalProgramBindings(program.body());

        List<Statement> body = program.body();

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        Set<String> hoistedFunctionNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.id() != null) {
                    hoistedFunctionNames.add(funcDecl.id().name());
                }
                delegates.functions.compileFunctionDeclaration(funcDecl);
            } else {
                // Collect var names from all statements, including nested ones.
                // var declarations are function/global-scoped and must be hoisted
                // regardless of block nesting (for, try, if, etc.).
                delegates.analysis.collectVarNamesFromStatement(stmt, varNames);
            }
        }

        // Phase 1.25: Var hoisting — create undefined bindings for var names not
        // already covered by hoisted function declarations.
        // Per ES2024 CreateGlobalVarBinding, only create binding if it doesn't already exist.
        for (String varName : varNames) {
            if (!hoistedFunctionNames.contains(varName)) {
                delegates.emitHelpers.emitConditionalVarInit(varName);
            }
        }

        Set<String> declaredFuncVarNames = new HashSet<>();
        declaredFuncVarNames.addAll(hoistedFunctionNames);
        declaredFuncVarNames.addAll(varNames);

        // Phase 1.5: Annex B.3.3.3 - create var bindings for function declarations
        // nested inside blocks, if-statements, catch clauses, etc.
        delegates.analysis.scanAnnexBFunctions(body, declaredFuncVarNames);

        // Find the effective last statement index (last non-FunctionDeclaration),
        // since function declarations don't contribute a completion value.
        int effectiveLastIndex = -1;
        for (int i = body.size() - 1; i >= 0; i--) {
            if (!(body.get(i) instanceof FunctionDeclaration)) {
                effectiveLastIndex = i;
                break;
            }
        }

        // Phase 2: Compile all non-FunctionDeclaration statements in source order.
        boolean lastProducesValue = false;

        for (int i = 0; i < body.size(); i++) {
            Statement stmt = body.get(i);
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted in Phase 1
            }

            boolean isLast = (i == effectiveLastIndex);

            if (isLast && stmt instanceof ExpressionStatement) {
                lastProducesValue = true;
            } else if (isLast && stmt instanceof TryStatement) {
                // Try statements can produce values
                lastProducesValue = true;
            }

            compileStatement(stmt, isLast);
        }

        // If last statement didn't produce a value, push undefined
        if (!lastProducesValue) {
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        int programResultLocalIndex = ctx.currentScope().declareLocal("$program_result_" + ctx.emitter.currentOffset());
        ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, programResultLocalIndex);
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, programResultLocalIndex);

        // Return the value on top of stack
        ctx.emitter.emitOpcode(Opcode.RETURN);

        ctx.exitScope();
        ctx.inGlobalScope = false;
    }

    void compileReturnStatement(ReturnStatement retStmt) {
        if (retStmt.argument() != null) {
            delegates.expressions.compileExpression(retStmt.argument());
        } else {
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        int returnValueIndex = ctx.currentScope().declareLocal("$return_value_" + ctx.emitter.currentOffset());
        ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);

        delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(0);

        if (ctx.hasActiveIteratorLoops()) {
            delegates.emitHelpers.emitAbruptCompletionIteratorClose();
        }

        ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
        // Emit RETURN_ASYNC for async functions, RETURN for sync functions
        ctx.emitter.emitOpcode(ctx.isInAsyncFunction ? Opcode.RETURN_ASYNC : Opcode.RETURN);
    }

    void compileStatement(Statement stmt) {
        compileStatement(stmt, false);
    }

    void compileStatement(Statement stmt, boolean isLastInProgram) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            delegates.expressions.compileExpression(exprStmt.expression());
            // Only drop the result if this is not the last statement in the program
            if (!isLastInProgram) {
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (stmt instanceof BlockStatement block) {
            compileBlockStatement(block);
        } else if (stmt instanceof IfStatement ifStmt) {
            compileIfStatement(ifStmt);
        } else if (stmt instanceof WhileStatement whileStmt) {
            compileWhileStatement(whileStmt);
        } else if (stmt instanceof ForStatement forStmt) {
            compileForStatement(forStmt);
        } else if (stmt instanceof ForInStatement forInStmt) {
            compileForInStatement(forInStmt);
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            compileForOfStatement(forOfStmt);
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
            // Try statements produce a value on the stack (the try/catch result).
            // Drop it when not the last statement in a program.
            if (!isLastInProgram) {
                ctx.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            compileSwitchStatement(switchStmt);
        } else if (stmt instanceof VariableDeclaration varDecl) {
            compileVariableDeclaration(varDecl);
        } else if (stmt instanceof FunctionDeclaration funcDecl) {
            delegates.functions.compileFunctionDeclaration(funcDecl);
        } else if (stmt instanceof ClassDeclaration classDecl) {
            delegates.functions.compileClassDeclaration(classDecl);
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            compileLabeledStatement(labeledStmt);
        }
    }

    void compileSwitchStatement(SwitchStatement switchStmt) {
        // Compile discriminant
        delegates.expressions.compileExpression(switchStmt.discriminant());

        List<Integer> caseJumps = new ArrayList<>();
        List<Integer> caseBodyStarts = new ArrayList<>();

        // Emit comparisons for each case.
        // Following QuickJS pattern: when a case matches, drop the discriminant
        // before jumping to the case body. This ensures the stack depth is
        // consistent regardless of which case (or no case) matches.
        for (SwitchStatement.SwitchCase switchCase : switchStmt.cases()) {
            if (switchCase.test() != null) {
                // Duplicate discriminant for comparison
                ctx.emitter.emitOpcode(Opcode.DUP);
                delegates.expressions.compileExpression(switchCase.test());
                ctx.emitter.emitOpcode(Opcode.STRICT_EQ);

                // If no match, skip to next test
                int jumpToNextTest = ctx.emitter.emitJump(Opcode.IF_FALSE);
                // Match: drop discriminant and jump to case body
                ctx.emitter.emitOpcode(Opcode.DROP);
                int jumpToBody = ctx.emitter.emitJump(Opcode.GOTO);
                caseJumps.add(jumpToBody);
                // Patch IF_FALSE to continue with next test
                ctx.emitter.patchJump(jumpToNextTest, ctx.emitter.currentOffset());
            }
        }

        // No case matched: drop discriminant
        ctx.emitter.emitOpcode(Opcode.DROP);

        // Jump to default or end
        int jumpToDefault = ctx.emitter.emitJump(Opcode.GOTO);

        // Compile case bodies
        // The switch body always creates a block scope for lexical declarations (let/const).
        // Per QuickJS: push_scope is unconditional for switch statements.
        boolean savedGlobalScope = ctx.inGlobalScope;
        ctx.enterScope();
        ctx.inGlobalScope = false;

        LoopContext loop = ctx.createLoopContext(ctx.emitter.currentOffset(), ctx.scopeDepth, ctx.scopeDepth);
        ctx.loopStack.push(loop);

        int defaultBodyStart = -1;
        for (int i = 0; i < switchStmt.cases().size(); i++) {
            SwitchStatement.SwitchCase switchCase = switchStmt.cases().get(i);

            if (switchCase.test() != null) {
                int bodyStart = ctx.emitter.currentOffset();
                caseBodyStarts.add(bodyStart);
            } else {
                defaultBodyStart = ctx.emitter.currentOffset();
            }

            for (Statement stmt : switchCase.consequent()) {
                compileStatement(stmt);
            }
        }

        int switchEnd = ctx.emitter.currentOffset();

        // Patch case jumps
        for (int i = 0; i < caseJumps.size(); i++) {
            ctx.emitter.patchJump(caseJumps.get(i), caseBodyStarts.get(i));
        }

        // Patch default jump
        if (defaultBodyStart >= 0) {
            ctx.emitter.patchJump(jumpToDefault, defaultBodyStart);
        } else {
            ctx.emitter.patchJump(jumpToDefault, switchEnd);
        }

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            ctx.emitter.patchJump(breakPos, switchEnd);
        }

        ctx.loopStack.pop();

        ctx.inGlobalScope = savedGlobalScope;
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.exitScope();
    }

    void compileThrowStatement(ThrowStatement throwStmt) {
        delegates.expressions.compileExpression(throwStmt.argument());
        int throwValueIndex = ctx.currentScope().declareLocal("$throw_value_" + ctx.emitter.currentOffset());
        ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, throwValueIndex);

        delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(0);

        if (ctx.hasActiveIteratorLoops()) {
            delegates.emitHelpers.emitAbruptCompletionIteratorClose();
        }
        ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, throwValueIndex);
        ctx.emitter.emitOpcode(Opcode.THROW);
    }

    /**
     * Compile a block for try/catch/finally, preserving the value of the last expression.
     */
    void compileTryFinallyBlock(BlockStatement block) {
        ctx.enterScope();
        List<Statement> body = block.body();
        for (int i = 0; i < body.size(); i++) {
            boolean isLast = (i == body.size() - 1);
            Statement stmt = body.get(i);

            if (stmt instanceof ExpressionStatement exprStmt) {
                delegates.expressions.compileExpression(exprStmt.expression());
                // Keep the value on stack for the last expression, drop otherwise
                if (!isLast) {
                    ctx.emitter.emitOpcode(Opcode.DROP);
                }
            } else {
                compileStatement(stmt, false);
                // If last statement is not an expression, push undefined
                if (isLast) {
                    ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                }
            }
        }
        // If block is empty, push undefined
        if (body.isEmpty()) {
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        ctx.exitScope();
    }

    void compileTryStatement(TryStatement tryStmt) {
        // Mark catch handler location
        int catchJump = -1;
        if (tryStmt.handler() != null) {
            catchJump = ctx.emitter.emitJump(Opcode.CATCH);
        }

        // Compile try block - preserve value of last expression
        compileTryFinallyBlock(tryStmt.block());

        // Remove the CatchOffset marker from the stack (normal path, no exception).
        // NIP_CATCH pops everything down to and including the CatchOffset marker,
        // then re-pushes the try result value.
        if (tryStmt.handler() != null) {
            ctx.emitter.emitOpcode(Opcode.NIP_CATCH);
        }

        // Jump over catch block
        int jumpOverCatch = ctx.emitter.emitJump(Opcode.GOTO);

        if (tryStmt.handler() != null) {
            // Patch catch jump
            ctx.emitter.patchJump(catchJump, ctx.emitter.currentOffset());

            // Catch handler puts exception on stack
            TryStatement.CatchClause handler = tryStmt.handler();

            // Bind exception to parameter if present
            if (handler.param() != null) {
                ctx.enterScope();
                // Catch block creates a local scope - variables should use GET_LOCAL
                boolean savedGlobalScope = ctx.inGlobalScope;
                ctx.inGlobalScope = false;

                // Declare all pattern variables and assign the exception value
                Pattern catchParam = handler.param();
                if (catchParam instanceof Identifier id) {
                    // Simple catch parameter: catch (e)
                    // Per B.3.5, simple catch parameters do not block Annex B var hoisting
                    int localIndex = ctx.currentScope().declareLocal(id.name());
                    ctx.currentScope().markSimpleCatchParam(id.name());
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                } else {
                    // Destructuring catch parameter: catch ({ f }) or catch ([a, b])
                    delegates.patterns.declarePatternVariables(catchParam);
                    delegates.patterns.compilePatternAssignment(catchParam);
                }

                // Compile catch body in the SAME scope as the parameter
                List<Statement> body = handler.body().body();
                for (int i = 0; i < body.size(); i++) {
                    boolean isLast = (i == body.size() - 1);
                    Statement stmt = body.get(i);

                    if (stmt instanceof ExpressionStatement exprStmt) {
                        delegates.expressions.compileExpression(exprStmt.expression());
                        // Keep the value on stack for the last expression, drop otherwise
                        if (!isLast) {
                            ctx.emitter.emitOpcode(Opcode.DROP);
                        }
                    } else {
                        compileStatement(stmt, false);
                        // If last statement is not an expression, push undefined
                        if (isLast) {
                            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                        }
                    }
                }
                // If block is empty, push undefined
                if (body.isEmpty()) {
                    ctx.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                ctx.inGlobalScope = savedGlobalScope;
                delegates.emitHelpers.emitCurrentScopeUsingDisposal();
                ctx.exitScope();
            } else {
                // No parameter, compile catch body without binding
                compileTryFinallyBlock(handler.body());
            }
        }

        // Patch jump over catch
        ctx.emitter.patchJump(jumpOverCatch, ctx.emitter.currentOffset());

        // Compile finally block
        if (tryStmt.finalizer() != null) {
            compileTryFinallyBlock(tryStmt.finalizer());
        }
    }

    void compileVariableDeclaration(VariableDeclaration varDecl) {
        boolean isUsingDeclaration = varDecl.kind() == VariableKind.USING || varDecl.kind() == VariableKind.AWAIT_USING;
        boolean isAwaitUsingDeclaration = varDecl.kind() == VariableKind.AWAIT_USING;
        // Track whether this is a var declaration in global program scope
        // so compilePatternAssignment can use PUT_VAR for global-scoped vars.
        boolean savedVarInGlobalProgram = ctx.varInGlobalProgram;
        if (ctx.isGlobalProgram && varDecl.kind() == VariableKind.VAR) {
            ctx.varInGlobalProgram = true;
        }
        for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
            if (ctx.inGlobalScope || ctx.varInGlobalProgram) {
                delegates.analysis.collectPatternBindingNames(declarator.id(), ctx.nonDeletableGlobalBindings);
            }
            if (isUsingDeclaration) {
                if (declarator.init() == null) {
                    throw new JSCompilerException(varDecl.kind() + " declaration requires an initializer");
                }

                delegates.expressions.compileExpression(declarator.init());
                int usingStackLocalIndex = delegates.emitHelpers.ensureUsingStackLocal(isAwaitUsingDeclaration);
                delegates.emitHelpers.emitMethodCallWithSingleArgOnLocalObject(usingStackLocalIndex, "use");
                delegates.patterns.compilePatternAssignment(declarator.id());
                continue;
            }

            // Compile initializer or push undefined
            if (declarator.init() != null) {
                delegates.expressions.compileExpression(declarator.init());
            } else {
                ctx.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            // Assign to pattern (handles Identifier, ObjectPattern, ArrayPattern)
            delegates.patterns.compilePatternAssignment(declarator.id());
        }
        ctx.varInGlobalProgram = savedVarInGlobalProgram;
    }

    void compileWhileStatement(WhileStatement whileStmt) {
        loopCompiler.compileWhileStatement(whileStmt);
    }
}
