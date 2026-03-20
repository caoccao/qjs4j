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
    // Pre-resolved binding references for with-statement var destructuring
    final Map<String, Deque<PreResolvedReference>> preResolvedBindingReferences;
    final ScopeManager scopeManager;
    final Set<String> tdzLocals;
    final WithObjectManager withObjectManager;
    // State stack
    private final Deque<CompilerStateFrame> stateStack;
    // Delegate compilers (initialized via initializeDelegates)
    ArrayExpressionCompiler arrayExpressionCompiler;
    ArrayExpressionDestructuringAssignmentCompiler arrayExpressionDestructuringAssignmentCompiler;
    ArrayPatternCompiler arrayPatternCompiler;
    ArrowFunctionExpressionCompiler arrowFunctionExpressionCompiler;
    AssignmentExpressionCompiler assignmentExpressionCompiler;
    AssignmentPatternCompiler assignmentPatternCompiler;
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
    ExpressionDestructuringAssignmentCompiler expressionDestructuringAssignmentCompiler;
    int finallySubroutineDepth;
    ForInStatementCompiler forInStatementCompiler;
    ForOfStatementCompiler forOfStatementCompiler;
    ForStatementCompiler forStatementCompiler;
    FunctionDeclarationCompiler functionDeclarationCompiler;
    FunctionExpressionCompiler functionExpressionCompiler;
    boolean hasEnclosingArgumentsBinding;
    IdentifierCompiler identifierCompiler;
    IdentifierDestructuringAssignmentCompiler identifierDestructuringAssignmentCompiler;
    IdentifierPatternCompiler identifierPatternCompiler;
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
    boolean suppressAnnexBVarStore;
    LabeledStatementCompiler labeledStatementCompiler;
    LiteralCompiler literalCompiler;
    MemberExpressionCompiler memberExpressionCompiler;
    MemberExpressionDestructuringAssignmentCompiler memberExpressionDestructuringAssignmentCompiler;
    NewExpressionCompiler newExpressionCompiler;
    ObjectExpressionCompiler objectExpressionCompiler;
    ObjectExpressionDestructuringAssignmentCompiler objectExpressionDestructuringAssignmentCompiler;
    ObjectPatternCompiler objectPatternCompiler;
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
    boolean useExistingBindingInParentScopes;
    CompilerScope varDeclarationScopeOverride;
    boolean varInGlobalProgram;
    VariableDeclarationCompiler variableDeclarationCompiler;
    WhileStatementCompiler whileStatementCompiler;
    WithStatementCompiler withStatementCompiler;
    YieldExpressionCompiler yieldExpressionCompiler;

    CompilerContext(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver, JSContext context) {
        this.scopeManager = new ScopeManager();

        this.activeFinallyGosubPatches = new ArrayDeque<>();
        this.activeFinallyNipCatchCounts = new ArrayDeque<>();
        this.annexBFunctionNames = new HashSet<>();
        this.annexBFunctionScopeLocals = new HashMap<>();
        this.captureResolver = new CaptureResolver(parentCaptureResolver, scopeManager::findBindingInScopes);
        this.context = context;
        this.emitter = new BytecodeEmitter();
        this.evalMode = false;
        this.evalReturnLocalIndex = -1;
        this.finallySubroutineDepth = 0;
        this.inGlobalScope = false;
        this.isGlobalProgram = false;
        this.isInArrowFunction = false;
        this.isInAsyncFunction = false;
        this.isLastInProgram = false;
        this.loopManager = new LoopManager();
        this.nonDeletableGlobalBindings = new HashSet<>();
        this.preResolvedBindingReferences = new HashMap<>();
        this.predeclareProgramLexicalsAsLocals = false;
        this.privateSymbols = Map.of();
        this.sourceCode = null;
        this.stateStack = new ArrayDeque<>();
        this.strictMode = inheritedStrictMode;
        this.tdzLocals = new HashSet<>();
        this.varDeclarationScopeOverride = null;
        this.varInGlobalProgram = false;
        this.withObjectManager = new WithObjectManager();
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
        this.arrayExpressionCompiler = new ArrayExpressionCompiler(this);
        this.arrayExpressionDestructuringAssignmentCompiler = new ArrayExpressionDestructuringAssignmentCompiler(this);
        this.arrayPatternCompiler = new ArrayPatternCompiler(this);
        this.arrowFunctionExpressionCompiler = new ArrowFunctionExpressionCompiler(this);
        this.assignmentExpressionCompiler = new AssignmentExpressionCompiler(this);
        this.assignmentPatternCompiler = new AssignmentPatternCompiler(this);
        this.awaitExpressionCompiler = new AwaitExpressionCompiler(this);
        this.binaryExpressionCompiler = new BinaryExpressionCompiler(this);
        this.blockStatementCompiler = new BlockStatementCompiler(this);
        this.breakStatementCompiler = new BreakStatementCompiler(this);
        this.callExpressionCompiler = new CallExpressionCompiler(this);
        this.classDeclarationCompiler = new ClassDeclarationCompiler(this);
        this.classExpressionCompiler = new ClassExpressionCompiler(this);
        this.compilerAnalysis = new CompilerAnalysis(this);
        this.conditionalExpressionCompiler = new ConditionalExpressionCompiler(this);
        this.continueStatementCompiler = new ContinueStatementCompiler(this);
        this.doWhileStatementCompiler = new DoWhileStatementCompiler(this);
        this.emitHelpers = new EmitHelpers(this);
        this.expressionCompiler = new ExpressionCompiler(this);
        this.expressionDestructuringAssignmentCompiler = new ExpressionDestructuringAssignmentCompiler(this);
        this.forInStatementCompiler = new ForInStatementCompiler(this);
        this.forOfStatementCompiler = new ForOfStatementCompiler(this);
        this.forStatementCompiler = new ForStatementCompiler(this);
        this.functionDeclarationCompiler = new FunctionDeclarationCompiler(this);
        this.functionExpressionCompiler = new FunctionExpressionCompiler(this);
        this.identifierCompiler = new IdentifierCompiler(this);
        this.identifierDestructuringAssignmentCompiler = new IdentifierDestructuringAssignmentCompiler(this);
        this.identifierPatternCompiler = new IdentifierPatternCompiler(this);
        this.ifStatementCompiler = new IfStatementCompiler(this);
        this.importExpressionCompiler = new ImportExpressionCompiler(this);
        this.labeledStatementCompiler = new LabeledStatementCompiler(this);
        this.literalCompiler = new LiteralCompiler(this);
        this.memberExpressionCompiler = new MemberExpressionCompiler(this);
        this.memberExpressionDestructuringAssignmentCompiler = new MemberExpressionDestructuringAssignmentCompiler(this);
        this.newExpressionCompiler = new NewExpressionCompiler(this);
        this.objectExpressionCompiler = new ObjectExpressionCompiler(this);
        this.objectExpressionDestructuringAssignmentCompiler = new ObjectExpressionDestructuringAssignmentCompiler(this);
        this.objectPatternCompiler = new ObjectPatternCompiler(this);
        this.patternCompiler = new PatternCompiler(this);
        this.programCompiler = new ProgramCompiler(this);
        this.returnStatementCompiler = new ReturnStatementCompiler(this);
        this.sequenceExpressionCompiler = new SequenceExpressionCompiler(this);
        this.statementCompiler = new StatementCompiler(this);
        this.switchStatementCompiler = new SwitchStatementCompiler(this);
        this.taggedTemplateExpressionCompiler = new TaggedTemplateExpressionCompiler(this);
        this.templateLiteralCompiler = new TemplateLiteralCompiler(this);
        this.throwStatementCompiler = new ThrowStatementCompiler(this);
        this.tryStatementCompiler = new TryStatementCompiler(this);
        this.unaryExpressionCompiler = new UnaryExpressionCompiler(this);
        this.variableDeclarationCompiler = new VariableDeclarationCompiler(this);
        this.whileStatementCompiler = new WhileStatementCompiler(this);
        this.withStatementCompiler = new WithStatementCompiler(this);
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

    record PreResolvedReference(int objectLocalIndex, int propertyLocalIndex) {
    }
}
