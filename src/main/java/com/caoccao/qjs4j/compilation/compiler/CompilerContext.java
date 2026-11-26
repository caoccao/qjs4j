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

import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.vm.Bytecode;

import java.util.*;

/**
 * Shared mutable state for the bytecode compiler. Holds data fields and references to manager services. All scope,
 * loop, and with-object logic is delegated to dedicated managers.
 */
final class CompilerContext {
    // Finally management
    final Deque<List<Integer>> activeFinallyGosubPatches;
    final Deque<Integer> activeFinallyNipCatchCounts;
    // Annex B
    final Set<String> annexBFunctionNames;
    final Map<String, Integer> annexBFunctionScopeLocals;
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
    // Services
    final CaptureResolver captureResolver;
    ClassDeclarationCompiler classDeclarationCompiler;
    ClassExpressionCompiler classExpressionCompiler;
    // Other flags (not stack-managed)
    boolean classFieldEvalContext;
    String classInnerNameToCapture;
    CompilerAnalysis compilerAnalysis;
    ConditionalExpressionCompiler conditionalExpressionCompiler;
    JSContext context;
    ContinueStatementCompiler continueStatementCompiler;
    EmitHelpers emitHelpers;
    // Flags (managed by state stack)
    boolean emitTailCalls;
    final BytecodeEmitter emitter;
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
    String inferredClassName;
    boolean inGlobalScope;
    boolean isGlobalProgram;
    boolean isInArrowFunction;
    boolean isInAsyncFunction;
    boolean isInGeneratorFunction;
    boolean isLastInProgram;
    LabeledStatementCompiler labeledStatementCompiler;
    LiteralCompiler literalCompiler;
    final LoopManager loopManager;
    /**
     * Whether the code being compiled may contain a sloppy direct {@code eval}, which can declare a binding that
     * shadows an assignment target while the right-hand side runs; such code keeps reference semantics for assignments.
     * A textual over-approximation: any {@code eval}, or any backslash-u escape that could spell one.
     */
    boolean mayContainDirectEval;
    MemberExpressionCompiler memberExpressionCompiler;
    MemberExpressionDestructuringAssignmentCompiler memberExpressionDestructuringAssignmentCompiler;
    NewExpressionCompiler newExpressionCompiler;
    // TDZ and global bindings
    final Set<String> nonDeletableGlobalBindings;
    ObjectExpressionCompiler objectExpressionCompiler;
    ObjectExpressionDestructuringAssignmentCompiler objectExpressionDestructuringAssignmentCompiler;
    ObjectPatternCompiler objectPatternCompiler;
    /**
     * The jumps to the end of the optional chain being compiled, or null outside one: QuickJS's
     * {@code optional_chaining_label}. Every {@code ?.} that finds a nullish value jumps there with {@code undefined}
     * as the value of the whole chain.
     */
    List<Integer> optionalChainJumps;
    /**
     * The member or call expression about to be compiled as the next link of the optional chain being compiled, or
     * null. Only that exact node joins the chain; any other optional chain met while it is set — in an argument, a
     * computed key — starts a chain of its own.
     */
    Expression optionalChainLink;
    /**
     * The named-function-expression bindings the code being compiled resolves through enclosing parameter environments.
     * Never null; empty when there are none.
     * <p>
     * Set while a default initializer of a function expression whose body redeclares its own name is being compiled,
     * and inherited by every function nested inside that initializer — everything lexically there is in the parameter
     * environment, whose outer environment holds the name. A closure created there must capture the bindings by name,
     * because a direct {@code eval} inside it resolves names at run time and the frame it runs on is the closure's, not
     * the one whose parameters were being initialized.
     * <p>
     * A set rather than one name, because the initializers nest: a named function expression inside another one's
     * default initializer is inside <em>both</em> parameter environments, and a single slot could only remember the
     * inner one. It held the inner one, so the outer binding silently disappeared from anything compiled there. A set
     * rather than a list because membership is the only question asked of it.
     *
     * @see com.caoccao.qjs4j.core.JSBytecodeFunction#getInheritedParameterScopeFunctionNames()
     */
    Set<String> parameterScopeFunctionNames = Set.of();
    PatternCompiler patternCompiler;
    Runnable pendingPostSuperInitialization;
    boolean predeclareProgramLexicalsAsLocals;
    // Pre-resolved binding references for with-statement var destructuring
    final Map<String, Deque<PreResolvedReference>> preResolvedBindingReferences;
    Map<String, JSSymbol> privateSymbols;
    ProgramCompiler programCompiler;
    ReturnStatementCompiler returnStatementCompiler;
    final ScopeManager scopeManager;
    SequenceExpressionCompiler sequenceExpressionCompiler;
    String sourceCode;
    StatementCompiler statementCompiler;
    // State stack
    private final Deque<CompilerStateFrame> stateStack;
    boolean strictMode;
    boolean suppressAnnexBVarStore;
    SwitchStatementCompiler switchStatementCompiler;
    TaggedTemplateExpressionCompiler taggedTemplateExpressionCompiler;
    final Set<String> tdzLocals;
    TemplateLiteralCompiler templateLiteralCompiler;
    ThrowStatementCompiler throwStatementCompiler;
    /**
     * How many try blocks enclose the code being compiled. A call in a try block is not in tail position (ES2024
     * 15.10.2 HasCallInTailPosition): its frame must survive for the catch handler to run.
     */
    int tryBlockDepth;
    TryStatementCompiler tryStatementCompiler;
    UnaryExpressionCompiler unaryExpressionCompiler;
    boolean useExistingBindingInParentScopes;
    CompilerScope varDeclarationScopeOverride;
    VariableDeclarationCompiler variableDeclarationCompiler;
    boolean varInGlobalProgram;
    WhileStatementCompiler whileStatementCompiler;
    final WithObjectManager withObjectManager;
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
        this.optionalChainJumps = null;
        this.optionalChainLink = null;
        this.preResolvedBindingReferences = new HashMap<>();
        this.predeclareProgramLexicalsAsLocals = false;
        this.privateSymbols = Map.of();
        this.sourceCode = null;
        this.stateStack = new ArrayDeque<>();
        this.strictMode = inheritedStrictMode;
        this.tdzLocals = new HashSet<>();
        this.mayContainDirectEval = true;
        this.varDeclarationScopeOverride = null;
        this.varInGlobalProgram = false;
        this.withObjectManager = new WithObjectManager();
        initializeDelegates();
    }

    void enterFunctionScope(boolean async, boolean generator) {
        scopeManager.enterScope();
        inGlobalScope = false;
        isInAsyncFunction = async;
        isInGeneratorFunction = generator;
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

    Bytecode finishFunctionScope() {
        int localCount = scopeManager.currentScope().getLocalCount();
        String[] localNames = scopeManager.getLocalVarNames();
        scopeManager.exitScope();
        return emitter.build(localCount, localNames);
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
        this.memberExpressionDestructuringAssignmentCompiler = new MemberExpressionDestructuringAssignmentCompiler(
                this);
        this.newExpressionCompiler = new NewExpressionCompiler(this);
        this.objectExpressionCompiler = new ObjectExpressionCompiler(this);
        this.objectExpressionDestructuringAssignmentCompiler = new ObjectExpressionDestructuringAssignmentCompiler(
                this);
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

    BytecodeCompiler newFunctionCompiler(SourceLocation location) {
        BytecodeCompiler nested = newNestedCompiler(strictMode, privateSymbols);
        CompilerContext child = nested.context();
        child.mayContainDirectEval = mayContainDirectEval && sourceMayContainDirectEval(extractSourceCode(location));
        functionExpressionCompiler.inheritVisibleWithObjectBindings(child);
        return nested;
    }

    BytecodeCompiler newNestedCompiler(boolean strict, Map<String, JSSymbol> symbols) {
        BytecodeCompiler nested = new BytecodeCompiler(strict, captureResolver, context);
        CompilerContext child = nested.context();
        child.sourceCode = sourceCode;
        child.mayContainDirectEval = mayContainDirectEval;
        child.nonDeletableGlobalBindings.addAll(nonDeletableGlobalBindings);
        child.privateSymbols = symbols;
        return nested;
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
        stateStack.push(new CompilerStateFrame(strictMode, inGlobalScope, inClassBody, inClassFieldInitializer,
                emitTailCalls, varInGlobalProgram, privateSymbols, inferredClassName));
    }

    /**
     * Whether source text may contain a direct {@code eval}; null text, being unknown, may.
     *
     * @param text
     *            the source text, or null
     * @return false only when the text certainly contains no direct eval
     */
    static boolean sourceMayContainDirectEval(String text) {
        return text == null || text.contains("eval") || text.contains("\\u");
    }

    record PreResolvedReference(int objectLocalIndex, int propertyLocalIndex) {
    }
}
