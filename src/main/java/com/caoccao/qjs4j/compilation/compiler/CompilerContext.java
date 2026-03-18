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

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSSymbol;

import java.util.*;

/**
 * Shared mutable state for the bytecode compiler.
 * Holds data fields and references to manager services.
 * All scope, loop, and with-object logic is delegated to dedicated managers.
 */
final class CompilerContext {
    // Finally management
    final Deque<List<Integer>> activeFinallyGosubPatches;
    final Deque<Integer> activeFinallyNipCatchCounts;
    // Annex B
    final Set<String> annexBFunctionNames;
    final Map<String, Integer> annexBFunctionScopeLocals;
    // Services
    final CaptureResolver captureResolver;
    final BytecodeEmitter emitter;
    final LoopManager loopManager;
    // TDZ and global bindings
    final Set<String> nonDeletableGlobalBindings;
    final ScopeManager scopeManager;
    final Set<String> tdzLocals;
    final WithObjectManager withObjectManager;
    // State stack
    private final Deque<CompilerStateFrame> stateStack;
    // Delegate compilers (initialized via initializeDelegates)
    ArrayExpressionCompiler arrayExpressionCompiler;
    ArrayExpressionDestructuringAssignmentCompiler arrayExpressionDestructuringAssignmentCompiler;
    ArrowFunctionExpressionCompiler arrowFunctionExpressionCompiler;
    AssignmentExpressionCompiler assignmentExpressionCompiler;
    AwaitExpressionCompiler awaitExpressionCompiler;
    BinaryExpressionCompiler binaryExpressionCompiler;
    BlockStatementCompiler blockStatementCompiler;
    BreakStatementCompiler breakStatementCompiler;
    CallExpressionCompiler callExpressionCompiler;
    ClassDeclarationCompiler classDeclarationCompiler;
    ClassExpressionCompiler classExpressionCompiler;
    // Other flags (not stack-managed)
    boolean classFieldEvalContext;
    String classInnerNameToCapture;
    CompilerAnalysis compilerAnalysis;
    ConditionalExpressionCompiler conditionalExpressionCompiler;
    JSContext context;
    ContinueStatementCompiler continueStatementCompiler;
    DoWhileStatementCompiler doWhileStatementCompiler;
    EmitHelpers emitHelpers;
    // Flags (managed by state stack)
    boolean emitTailCalls;
    boolean evalMode;
    int evalReturnLocalIndex;
    ExpressionCompiler expressionCompiler;
    int finallySubroutineDepth;
    ForInStatementCompiler forInStatementCompiler;
    ForOfStatementCompiler forOfStatementCompiler;
    ForStatementCompiler forStatementCompiler;
    FunctionDeclarationCompiler functionDeclarationCompiler;
    FunctionExpressionCompiler functionExpressionCompiler;
    boolean hasEnclosingArgumentsBinding;
    IdentifierCompiler identifierCompiler;
    IfStatementCompiler ifStatementCompiler;
    ImportExpressionCompiler importExpressionCompiler;
    boolean inClassBody;
    boolean inClassFieldInitializer;
    boolean inGlobalScope;
    String inferredClassName;
    boolean isGlobalProgram;
    boolean isInArrowFunction;
    boolean isInAsyncFunction;
    boolean isInGeneratorFunction;
    boolean isLastInProgram;
    LabeledStatementCompiler labeledStatementCompiler;
    LiteralCompiler literalCompiler;
    MemberExpressionCompiler memberExpressionCompiler;
    NewExpressionCompiler newExpressionCompiler;
    ObjectExpressionCompiler objectExpressionCompiler;
    PatternCompiler patternCompiler;
    Runnable pendingPostSuperInitialization;
    boolean predeclareProgramLexicalsAsLocals;
    Map<String, JSSymbol> privateSymbols;
    ProgramCompiler programCompiler;
    ReturnStatementCompiler returnStatementCompiler;
    SequenceExpressionCompiler sequenceExpressionCompiler;
    String sourceCode;
    StatementCompiler statementCompiler;
    boolean strictMode;
    SwitchStatementCompiler switchStatementCompiler;
    TaggedTemplateExpressionCompiler taggedTemplateExpressionCompiler;
    TemplateLiteralCompiler templateLiteralCompiler;
    ThrowStatementCompiler throwStatementCompiler;
    TryStatementCompiler tryStatementCompiler;
    UnaryExpressionCompiler unaryExpressionCompiler;
    CompilerScope varDeclarationScopeOverride;
    boolean varInGlobalProgram;
    VariableDeclarationCompiler variableDeclarationCompiler;
    WhileStatementCompiler whileStatementCompiler;
    WithStatementCompiler withStatementCompiler;
    YieldExpressionCompiler yieldExpressionCompiler;

    CompilerContext() {
        this(false, null, null);
    }

    CompilerContext(boolean inheritedStrictMode) {
        this(inheritedStrictMode, null, null);
    }

    CompilerContext(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver) {
        this(inheritedStrictMode, parentCaptureResolver, null);
    }

    CompilerContext(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver, JSContext context) {
        this.stateStack = new ArrayDeque<>();
        this.scopeManager = new ScopeManager();
        this.loopManager = new LoopManager();
        this.withObjectManager = new WithObjectManager();
        this.activeFinallyGosubPatches = new ArrayDeque<>();
        this.activeFinallyNipCatchCounts = new ArrayDeque<>();
        this.annexBFunctionNames = new HashSet<>();
        this.annexBFunctionScopeLocals = new HashMap<>();
        this.captureResolver = new CaptureResolver(parentCaptureResolver, scopeManager::findBindingInScopes);
        this.emitter = new BytecodeEmitter();
        this.context = context;
        this.evalMode = false;
        this.evalReturnLocalIndex = -1;
        this.finallySubroutineDepth = 0;
        this.inGlobalScope = false;
        this.isGlobalProgram = false;
        this.isInAsyncFunction = false;
        this.isInArrowFunction = false;
        this.isLastInProgram = false;
        this.nonDeletableGlobalBindings = new HashSet<>();
        this.tdzLocals = new HashSet<>();
        this.sourceCode = null;
        this.privateSymbols = Map.of();
        this.predeclareProgramLexicalsAsLocals = false;
        this.strictMode = inheritedStrictMode;
        this.varInGlobalProgram = false;
        this.varDeclarationScopeOverride = null;
        this.classExpressionCompiler = new ClassExpressionCompiler(this);
        initializeDelegates();
    }

    String extractSourceCode(SourceLocation location) {
        if (sourceCode == null || location == null) {
            return null;
        }

        int startOffset = location.offset();
        int endOffset = location.endOffset();

        if (startOffset < 0 || endOffset > sourceCode.length() || startOffset > endOffset) {
            return null;
        }

        return sourceCode.substring(startOffset, endOffset);
    }

    private void initializeDelegates() {
        this.expressionCompiler = new ExpressionCompiler(this);
        this.arrayExpressionCompiler = new ArrayExpressionCompiler(this);
        this.arrayExpressionDestructuringAssignmentCompiler = new ArrayExpressionDestructuringAssignmentCompiler(this);
        this.arrowFunctionExpressionCompiler = new ArrowFunctionExpressionCompiler(this);
        this.assignmentExpressionCompiler = new AssignmentExpressionCompiler(this);
        this.awaitExpressionCompiler = new AwaitExpressionCompiler(this);
        this.binaryExpressionCompiler = new BinaryExpressionCompiler(this);
        this.blockStatementCompiler = new BlockStatementCompiler(this);
        this.breakStatementCompiler = new BreakStatementCompiler(this);
        this.callExpressionCompiler = new CallExpressionCompiler(this);
        this.classDeclarationCompiler = new ClassDeclarationCompiler(this);
        this.compilerAnalysis = new CompilerAnalysis(this);
        this.continueStatementCompiler = new ContinueStatementCompiler(this);
        this.conditionalExpressionCompiler = new ConditionalExpressionCompiler(this);
        this.doWhileStatementCompiler = new DoWhileStatementCompiler(this);
        this.emitHelpers = new EmitHelpers(this);
        this.functionDeclarationCompiler = new FunctionDeclarationCompiler(this);
        this.functionExpressionCompiler = new FunctionExpressionCompiler(this);
        this.forInStatementCompiler = new ForInStatementCompiler(this);
        this.forOfStatementCompiler = new ForOfStatementCompiler(this);
        this.forStatementCompiler = new ForStatementCompiler(this);
        this.ifStatementCompiler = new IfStatementCompiler(this);
        this.labeledStatementCompiler = new LabeledStatementCompiler(this);
        this.returnStatementCompiler = new ReturnStatementCompiler(this);
        this.variableDeclarationCompiler = new VariableDeclarationCompiler(this);
        this.whileStatementCompiler = new WhileStatementCompiler(this);
        this.withStatementCompiler = new WithStatementCompiler(this);
        this.identifierCompiler = new IdentifierCompiler(this);
        this.importExpressionCompiler = new ImportExpressionCompiler(this);
        this.literalCompiler = new LiteralCompiler(this);
        this.memberExpressionCompiler = new MemberExpressionCompiler(this);
        this.newExpressionCompiler = new NewExpressionCompiler(this);
        this.objectExpressionCompiler = new ObjectExpressionCompiler(this);
        this.patternCompiler = new PatternCompiler(this);
        this.programCompiler = new ProgramCompiler(this);
        this.sequenceExpressionCompiler = new SequenceExpressionCompiler(this);
        this.statementCompiler = new StatementCompiler(this);
        this.switchStatementCompiler = new SwitchStatementCompiler(this);
        this.throwStatementCompiler = new ThrowStatementCompiler(this);
        this.tryStatementCompiler = new TryStatementCompiler(this);
        this.taggedTemplateExpressionCompiler = new TaggedTemplateExpressionCompiler(this);
        this.templateLiteralCompiler = new TemplateLiteralCompiler(this);
        this.unaryExpressionCompiler = new UnaryExpressionCompiler(this);
        this.yieldExpressionCompiler = new YieldExpressionCompiler(this);
    }

    void popState() {
        CompilerStateFrame frame = stateStack.pop();
        strictMode = frame.strictMode();
        inGlobalScope = frame.inGlobalScope();
        inClassBody = frame.inClassBody();
        inClassFieldInitializer = frame.inClassFieldInitializer();
        emitTailCalls = frame.emitTailCalls();
        varInGlobalProgram = frame.varInGlobalProgram();
        privateSymbols = frame.privateSymbols();
        inferredClassName = frame.inferredClassName();
    }

    void pushState() {
        stateStack.push(new CompilerStateFrame(
                strictMode, inGlobalScope, inClassBody,
                inClassFieldInitializer, emitTailCalls,
                varInGlobalProgram, privateSymbols, inferredClassName));
    }
}
