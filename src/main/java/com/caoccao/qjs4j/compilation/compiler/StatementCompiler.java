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
import com.caoccao.qjs4j.core.JSKeyword;
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
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;
    private final StatementLoopCompiler loopCompiler;

    StatementCompiler(CompilerContext compilerContext, CompilerDelegates delegates) {
        this.compilerContext = compilerContext;
        this.delegates = delegates;
        loopCompiler = new StatementLoopCompiler(this, compilerContext, delegates);
    }

    private static boolean hasTailCallInTailPosition(Expression expr) {
        if (expr instanceof CallExpression callExpr) {
            return isTailCallableCallExpression(callExpr);
        }
        if (expr instanceof BinaryExpression binExpr
                && binExpr.operator() == BinaryExpression.BinaryOperator.NULLISH_COALESCING) {
            return hasTailCallInTailPosition(binExpr.right());
        }
        if (expr instanceof ConditionalExpression condExpr) {
            return hasTailCallInTailPosition(condExpr.consequent())
                    || hasTailCallInTailPosition(condExpr.alternate());
        }
        if (expr instanceof BinaryExpression binExpr
                && (binExpr.operator() == BinaryExpression.BinaryOperator.LOGICAL_AND
                || binExpr.operator() == BinaryExpression.BinaryOperator.LOGICAL_OR)) {
            return hasTailCallInTailPosition(binExpr.right());
        }
        if (expr instanceof SequenceExpression seqExpr && !seqExpr.expressions().isEmpty()) {
            return hasTailCallInTailPosition(seqExpr.expressions().get(seqExpr.expressions().size() - 1));
        }
        return false;
    }

    private static boolean isTailCallableCallExpression(CallExpression callExpr) {
        return callExpr.arguments().stream().noneMatch(arg -> arg instanceof SpreadElement)
                && !(callExpr.callee() instanceof Identifier id && JSKeyword.SUPER.equals(id.name()));
    }

    void compileBlockStatement(BlockStatement block) {
        boolean savedGlobalScope = compilerContext.inGlobalScope;
        compilerContext.enterScope();
        compilerContext.inGlobalScope = false;
        // Phase 0: Pre-declare block-scoped let/const variables so that function
        // declarations hoisted in Phase 1 can capture them via closure.
        preDeclareLexicalBindings(block.body());
        // Phase 1: Hoist function declarations to top of block (ES2015+ block-scoped
        // functions are initialized before any statements in the block execute).
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
        compilerContext.inGlobalScope = savedGlobalScope;
        compilerContext.exitScope();
    }

    void compileBreakStatement(BreakStatement breakStmt) {
        loopCompiler.compileBreakStatement(breakStmt);
    }

    void compileContinueStatement(ContinueStatement contStmt) {
        loopCompiler.compileContinueStatement(contStmt);
    }

    void compileDoWhileStatement(DoWhileStatement doWhileStmt) {
        loopCompiler.compileDoWhileStatement(doWhileStmt);
    }

    /**
     * Compile the body of a finally block as statements with net-zero stack effect.
     * Used for the GOSUB/RET path where the return address must remain on top of the stack.
     */
    private void compileFinallyBlockBody(BlockStatement block) {
        compilerContext.enterScope();
        for (Statement stmt : block.body()) {
            if (stmt instanceof ExpressionStatement exprStmt) {
                delegates.expressions.compileExpression(exprStmt.expression());
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                compileStatement(stmt, false);
            }
        }
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
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
        if (compilerContext.strictMode) {
            if (ifStmt.consequent() instanceof FunctionDeclaration
                    || ifStmt.alternate() instanceof FunctionDeclaration) {
                throw new JSSyntaxErrorException(
                        "In strict mode code, functions can only be declared at top level or inside a block.");
            }
        }

        // Compile condition
        delegates.expressions.compileExpression(ifStmt.test());

        // Jump to else/end if condition is false
        int jumpToElse = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent — wrap bare function declarations in implicit block scope
        compileImplicitBlockStatement(ifStmt.consequent());

        if (ifStmt.alternate() != null) {
            // Jump over else block after consequent
            int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

            // Patch jump to else
            compilerContext.emitter.patchJump(jumpToElse, compilerContext.emitter.currentOffset());

            // Compile alternate — wrap bare function declarations in implicit block scope
            compileImplicitBlockStatement(ifStmt.alternate());

            // Patch jump to end
            compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        } else {
            // Patch jump to end
            compilerContext.emitter.patchJump(jumpToElse, compilerContext.emitter.currentOffset());
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
            compilerContext.enterScope();
            compilerContext.currentScope().declareLocal(funcDecl.id().name());
            delegates.functions.compileFunctionDeclaration(funcDecl);
            delegates.emitHelpers.emitCurrentScopeUsingDisposal();
            compilerContext.exitScope();
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
        compilerContext.pendingLoopLabel = labelName;
        compileStatement(loopStmt);
        compilerContext.pendingLoopLabel = null;
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
        if (compilerContext.strictMode && body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "In strict mode code, functions can only be declared at top level or inside a block.");
        }

        // Check if the body is a loop statement — if so, the label applies to the loop
        if (body instanceof WhileStatement || body instanceof DoWhileStatement || body instanceof ForStatement
                || body instanceof ForInStatement || body instanceof ForOfStatement) {
            // Push a labeled loop context; the loop compilation will use loopStack.peek()
            // We need to wrap the loop compilation to attach the label
            compileLabeledLoop(labelName, body);
        } else {
            // Regular labeled statement: only 'break label;' is valid (not continue)
            LoopContext labelContext = new LoopContext(compilerContext.emitter.currentOffset(), compilerContext.scopeDepth, compilerContext.scopeDepth, labelName);
            labelContext.isRegularStmt = true;
            compilerContext.loopStack.push(labelContext);

            // Body can be null for empty statements (label: ;)
            if (body != null) {
                compileStatement(body);
            }

            // Patch all break positions to jump here
            int breakTarget = compilerContext.emitter.currentOffset();
            for (int pos : labelContext.breakPositions) {
                compilerContext.emitter.patchJump(pos, breakTarget);
            }
            compilerContext.loopStack.pop();
        }
    }

    void compileProgram(Program program) {
        compilerContext.inGlobalScope = true;
        compilerContext.isGlobalProgram = true;
        compilerContext.strictMode = program.strict();  // Set strict mode from program directive
        compilerContext.enterScope();

        // Pre-declare class declarations as locals with TDZ.
        // Per ES spec EvalDeclarationInstantiation step 14: lexically declared names
        // are instantiated here but not initialized. Accessing them before their
        // declaration throws ReferenceError (temporal dead zone).
        // Following QuickJS js_closure_define_global_var pattern for eval lexical bindings.
        for (Statement stmt : program.body()) {
            if (stmt instanceof ClassDeclaration classDecl && classDecl.id() != null) {
                String className = classDecl.id().name();
                int localIndex = compilerContext.currentScope().declareLocal(className);
                compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                compilerContext.tdzLocals.add(className);
            } else if (compilerContext.predeclareProgramLexicalsAsLocals
                    && stmt instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() != VariableKind.VAR) {
                Set<String> lexicalNames = new HashSet<>();
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    delegates.analysis.collectPatternBindingNames(declarator.id(), lexicalNames);
                }
                for (String lexicalName : lexicalNames) {
                    int localIndex = compilerContext.currentScope().declareLocal(lexicalName);
                    if (variableDeclaration.kind() == VariableKind.CONST) {
                        compilerContext.currentScope().markConstLocal(lexicalName);
                    }
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                    compilerContext.tdzLocals.add(lexicalName);
                }
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
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        int programResultLocalIndex = compilerContext.currentScope().declareLocal("$program_result_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, programResultLocalIndex);
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, programResultLocalIndex);

        // Return the value on top of stack
        compilerContext.emitter.emitOpcode(Opcode.RETURN);

        int localCount = compilerContext.currentScope().getLocalCount();
        if (localCount > compilerContext.maxLocalCount) {
            compilerContext.maxLocalCount = localCount;
        }
        compilerContext.inGlobalScope = false;
    }

    void compileReturnStatement(ReturnStatement retStmt) {
        // Tail call optimization: when the return argument has a call in tail position
        // in strict mode, with no active finally blocks, iterators, or async context,
        // emit TAIL_CALL instead of CALL + RETURN (ES2015 14.6.1 HasCallInTailPosition).
        // Exclude spread calls (which use APPLY, not CALL) and super calls from TCO.
        if (retStmt.argument() instanceof CallExpression
                && hasTailCallInTailPosition(retStmt.argument())
                && compilerContext.strictMode
                && !compilerContext.isInAsyncFunction
                && compilerContext.activeFinallyGosubPatches.isEmpty()
                && !compilerContext.hasActiveIteratorLoops()) {
            // Direct call in tail position: TAIL_CALL handles the return entirely
            compilerContext.emitTailCalls = true;
            delegates.expressions.compileExpression(retStmt.argument());
            compilerContext.emitTailCalls = false;
            return;
        }

        // For non-direct-call expressions with tail calls in sub-positions
        // (e.g., return a ?? f(), return 0, f(), return a ? f() : g()),
        // set emitTailCalls and fall through to emit RETURN for non-tail paths.
        boolean enableTco = retStmt.argument() != null
                && hasTailCallInTailPosition(retStmt.argument())
                && compilerContext.strictMode
                && !compilerContext.isInAsyncFunction
                && compilerContext.activeFinallyGosubPatches.isEmpty()
                && !compilerContext.hasActiveIteratorLoops();

        if (retStmt.argument() != null) {
            if (enableTco) {
                compilerContext.emitTailCalls = true;
            }
            delegates.expressions.compileExpression(retStmt.argument());
            compilerContext.emitTailCalls = false;
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        int returnValueIndex = compilerContext.currentScope().declareLocal("$return_value_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);

        delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(0);

        if (compilerContext.hasActiveIteratorLoops()) {
            delegates.emitHelpers.emitAbruptCompletionIteratorClose();
        }

        // Execute active finally blocks via GOSUB before returning.
        // Walk from innermost to outermost finally context.
        for (List<Integer> gosubPatches : compilerContext.activeFinallyGosubPatches) {
            // Push dummy value (NIP_CATCH expects a value on top of the CatchOffset)
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            // NIP_CATCH removes the CatchOffset for this try-finally, keeps dummy on top
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            // GOSUB pushes return address and jumps to finally block (patched later)
            int gosubPos = compilerContext.emitter.emitJump(Opcode.GOSUB);
            gosubPatches.add(gosubPos);
            // After RET returns here, drop the dummy value
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }

        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
        // Emit RETURN_ASYNC for async functions, RETURN for sync functions
        compilerContext.emitter.emitOpcode(compilerContext.isInAsyncFunction ? Opcode.RETURN_ASYNC : Opcode.RETURN);
    }

    void compileStatement(Statement stmt) {
        compileStatement(stmt, false);
    }

    void compileStatement(Statement stmt, boolean isLastInProgram) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            delegates.expressions.compileExpression(exprStmt.expression());
            // Only drop the result if this is not the last statement in the program
            if (!isLastInProgram) {
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (stmt instanceof BlockStatement block) {
            compileBlockStatement(block);
        } else if (stmt instanceof IfStatement ifStmt) {
            compileIfStatement(ifStmt);
        } else if (stmt instanceof WhileStatement whileStmt) {
            compileWhileStatement(whileStmt);
        } else if (stmt instanceof DoWhileStatement doWhileStmt) {
            compileDoWhileStatement(doWhileStmt);
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
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            compileSwitchStatement(switchStmt);
        } else if (stmt instanceof WithStatement withStmt) {
            compileWithStatement(withStmt);
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
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                delegates.expressions.compileExpression(switchCase.test());
                compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);

                // If no match, skip to next test
                int jumpToNextTest = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
                // Match: drop discriminant and jump to case body
                compilerContext.emitter.emitOpcode(Opcode.DROP);
                int jumpToBody = compilerContext.emitter.emitJump(Opcode.GOTO);
                caseJumps.add(jumpToBody);
                // Patch IF_FALSE to continue with next test
                compilerContext.emitter.patchJump(jumpToNextTest, compilerContext.emitter.currentOffset());
            }
        }

        // No case matched: drop discriminant
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        // Jump to default or end
        int jumpToDefault = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Compile case bodies
        // The switch body always creates a block scope for lexical declarations (let/const).
        // Per QuickJS: push_scope is unconditional for switch statements.
        boolean savedGlobalScope = compilerContext.inGlobalScope;
        compilerContext.enterScope();
        compilerContext.inGlobalScope = false;

        LoopContext loop = compilerContext.createLoopContext(compilerContext.emitter.currentOffset(), compilerContext.scopeDepth, compilerContext.scopeDepth);
        compilerContext.loopStack.push(loop);

        int defaultBodyStart = -1;
        for (int i = 0; i < switchStmt.cases().size(); i++) {
            SwitchStatement.SwitchCase switchCase = switchStmt.cases().get(i);

            if (switchCase.test() != null) {
                int bodyStart = compilerContext.emitter.currentOffset();
                caseBodyStarts.add(bodyStart);
            } else {
                defaultBodyStart = compilerContext.emitter.currentOffset();
            }

            for (Statement stmt : switchCase.consequent()) {
                compileStatement(stmt);
            }
        }

        int switchEnd = compilerContext.emitter.currentOffset();

        // Patch case jumps
        for (int i = 0; i < caseJumps.size(); i++) {
            compilerContext.emitter.patchJump(caseJumps.get(i), caseBodyStarts.get(i));
        }

        // Patch default jump
        if (defaultBodyStart >= 0) {
            compilerContext.emitter.patchJump(jumpToDefault, defaultBodyStart);
        } else {
            compilerContext.emitter.patchJump(jumpToDefault, switchEnd);
        }

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            compilerContext.emitter.patchJump(breakPos, switchEnd);
        }

        compilerContext.loopStack.pop();

        compilerContext.inGlobalScope = savedGlobalScope;
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
    }

    void compileThrowStatement(ThrowStatement throwStmt) {
        delegates.expressions.compileExpression(throwStmt.argument());
        int throwValueIndex = compilerContext.currentScope().declareLocal("$throw_value_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, throwValueIndex);

        delegates.emitHelpers.emitUsingDisposalsForScopeDepthGreaterThan(0);

        if (compilerContext.hasActiveIteratorLoops()) {
            delegates.emitHelpers.emitAbruptCompletionIteratorClose();
        }
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, throwValueIndex);
        compilerContext.emitter.emitOpcode(Opcode.THROW);
    }

    private void compileTryCatchPart(TryStatement tryStmt) {
        // Mark catch handler location
        int catchJump = -1;
        if (tryStmt.handler() != null) {
            catchJump = compilerContext.emitter.emitJump(Opcode.CATCH);
        }

        // Compile try block - preserve value of last expression
        compileTryFinallyBlock(tryStmt.block());

        // Remove the CatchOffset marker from the stack (normal path, no exception).
        // NIP_CATCH pops everything down to and including the CatchOffset marker,
        // then re-pushes the try result value.
        if (tryStmt.handler() != null) {
            compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
        }

        // Jump over catch block
        int jumpOverCatch = compilerContext.emitter.emitJump(Opcode.GOTO);

        if (tryStmt.handler() != null) {
            // Patch catch jump
            compilerContext.emitter.patchJump(catchJump, compilerContext.emitter.currentOffset());

            // Catch handler puts exception on stack
            TryStatement.CatchClause handler = tryStmt.handler();

            // Bind exception to parameter if present
            if (handler.param() != null) {
                compilerContext.enterScope();
                // Catch block creates a local scope - variables should use GET_LOCAL
                boolean savedGlobalScope = compilerContext.inGlobalScope;
                compilerContext.inGlobalScope = false;

                // Declare all pattern variables and assign the exception value
                Pattern catchParam = handler.param();
                if (catchParam instanceof Identifier id) {
                    // Simple catch parameter: catch (e)
                    // Per B.3.5, simple catch parameters do not block Annex B var hoisting
                    int localIndex = compilerContext.currentScope().declareLocal(id.name());
                    compilerContext.currentScope().markSimpleCatchParam(id.name());
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
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
                            compilerContext.emitter.emitOpcode(Opcode.DROP);
                        }
                    } else {
                        compileStatement(stmt, false);
                        // If last statement is not an expression, push undefined
                        if (isLast) {
                            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                        }
                    }
                }
                // If block is empty, push undefined
                if (body.isEmpty()) {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                compilerContext.inGlobalScope = savedGlobalScope;
                delegates.emitHelpers.emitCurrentScopeUsingDisposal();
                compilerContext.exitScope();
            } else {
                // No parameter, compile catch body without binding
                compileTryFinallyBlock(handler.body());
            }
        }

        // Patch jump over catch
        compilerContext.emitter.patchJump(jumpOverCatch, compilerContext.emitter.currentOffset());
    }

    /**
     * Compile a block for try/catch/finally, preserving the value of the last expression.
     */
    void compileTryFinallyBlock(BlockStatement block) {
        compilerContext.enterScope();
        List<Statement> body = block.body();
        for (int i = 0; i < body.size(); i++) {
            boolean isLast = (i == body.size() - 1);
            Statement stmt = body.get(i);

            if (stmt instanceof ExpressionStatement exprStmt) {
                delegates.expressions.compileExpression(exprStmt.expression());
                // Keep the value on stack for the last expression, drop otherwise
                if (!isLast) {
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                }
            } else {
                compileStatement(stmt, false);
                // If last statement is not an expression, push undefined
                if (isLast) {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }
            }
        }
        // If block is empty, push undefined
        if (body.isEmpty()) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
    }

    void compileTryStatement(TryStatement tryStmt) {
        if (tryStmt.finalizer() == null) {
            compileTryCatchPart(tryStmt);
            return;
        }

        // Collect GOSUB positions for return statements that need to execute the finally block
        List<Integer> gosubPatches = new ArrayList<>();
        compilerContext.activeFinallyGosubPatches.push(gosubPatches);

        // Outer CATCH for exception path to finally
        int finallyCatchJump = compilerContext.emitter.emitJump(Opcode.CATCH);

        // Compile try body (with optional inner catch block)
        compileTryCatchPart(tryStmt);

        compilerContext.activeFinallyGosubPatches.pop();

        // Normal path: inline finally (preserves existing stack value behavior)
        compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compileTryFinallyBlock(tryStmt.finalizer());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Exception path: inline finally, then rethrow
        compilerContext.emitter.patchJump(finallyCatchJump, compilerContext.emitter.currentOffset());
        compilerContext.emitter.markCatchAsFinally(finallyCatchJump);
        int exceptionLocalIndex = compilerContext.currentScope().declareLocal(
                "$finally_exception_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, exceptionLocalIndex);
        compileTryFinallyBlock(tryStmt.finalizer());
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, exceptionLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.THROW);

        // GOSUB finally block (only used by return statements inside try/catch bodies)
        if (!gosubPatches.isEmpty()) {
            int finallyOffset = compilerContext.emitter.currentOffset();
            compileFinallyBlockBody(tryStmt.finalizer());
            compilerContext.emitter.emitOpcode(Opcode.RET);

            // Patch all GOSUBs to point to this finally block
            for (int pos : gosubPatches) {
                compilerContext.emitter.patchJump(pos, finallyOffset);
            }
        }

        // End label
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
    }

    void compileVariableDeclaration(VariableDeclaration varDecl) {
        boolean isUsingDeclaration = varDecl.kind() == VariableKind.USING || varDecl.kind() == VariableKind.AWAIT_USING;
        boolean isAwaitUsingDeclaration = varDecl.kind() == VariableKind.AWAIT_USING;
        if (varDecl.kind() == VariableKind.CONST
                && !compilerContext.inGlobalScope
                && !compilerContext.varInGlobalProgram) {
            for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
                Set<String> constNames = new HashSet<>();
                delegates.analysis.collectPatternBindingNames(declarator.id(), constNames);
                for (String constName : constNames) {
                    compilerContext.currentScope().declareConstLocal(constName);
                }
            }
        }
        // Track whether this is a var declaration in global program scope
        // so compilePatternAssignment can use PUT_VAR for global-scoped vars.
        boolean savedVarInGlobalProgram = compilerContext.varInGlobalProgram;
        if (compilerContext.isGlobalProgram && varDecl.kind() == VariableKind.VAR) {
            compilerContext.varInGlobalProgram = true;
        }
        for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
            if (compilerContext.inGlobalScope || compilerContext.varInGlobalProgram) {
                delegates.analysis.collectPatternBindingNames(declarator.id(), compilerContext.nonDeletableGlobalBindings);
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
                // Pass inferred name to anonymous class expressions for NamedEvaluation
                if (declarator.id() instanceof Identifier targetId
                        && declarator.init() instanceof ClassExpression classExpr
                        && classExpr.id() == null) {
                    compilerContext.inferredClassName = targetId.name();
                }
                delegates.expressions.compileExpression(declarator.init());
                compilerContext.inferredClassName = null;
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            // Assign to pattern (handles Identifier, ObjectPattern, ArrayPattern)
            if (varDecl.kind() == VariableKind.VAR) {
                delegates.patterns.compileVarPatternAssignment(declarator.id());
            } else {
                delegates.patterns.compilePatternAssignment(declarator.id());
            }
        }
        compilerContext.varInGlobalProgram = savedVarInGlobalProgram;
    }

    void compileWhileStatement(WhileStatement whileStmt) {
        loopCompiler.compileWhileStatement(whileStmt);
    }

    void compileWithStatement(WithStatement withStmt) {
        if (compilerContext.strictMode) {
            throw new JSSyntaxErrorException("Strict mode code may not include a with statement");
        }

        compilerContext.enterScope();
        int withObjectLocalIndex = compilerContext.currentScope().declareLocal("$withObject" + compilerContext.scopeDepth);
        delegates.expressions.compileExpression(withStmt.object());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, withObjectLocalIndex);

        compilerContext.pushWithObjectLocal(withObjectLocalIndex);
        try {
            if (withStmt.body() != null) {
                compileStatement(withStmt.body());
            }
        } finally {
            // Do NOT clear the with-object local to undefined here.
            // Closures defined inside the with block capture a VarRef to this local
            // and need the with-object to remain accessible after the block exits.
            compilerContext.popWithObjectLocal();
        }

        delegates.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.exitScope();
    }

    /**
     * Pre-declare block-scoped let/const bindings in the current scope.
     * This must be called before hoisting function declarations so that
     * closures created by those function declarations can capture the
     * let/const variables from the same block scope.
     */
    private void preDeclareLexicalBindings(List<Statement> body) {
        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration vd && vd.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : vd.declarations()) {
                    Set<String> names = new HashSet<>();
                    delegates.analysis.collectPatternBindingNames(d.id(), names);
                    for (String name : names) {
                        compilerContext.currentScope().declareLocal(name);
                        if (vd.kind() == VariableKind.CONST) {
                            compilerContext.currentScope().markConstLocal(name);
                        }
                    }
                }
            }
        }
    }
}
