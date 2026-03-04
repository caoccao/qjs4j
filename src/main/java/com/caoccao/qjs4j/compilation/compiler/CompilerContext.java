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
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;

import java.util.*;

/**
 * Shared mutable state for the bytecode compiler.
 * Holds all fields, scope management, loop management, and utility methods
 * that are shared across the delegate compiler classes.
 */
final class CompilerContext {
    final Set<String> annexBFunctionNames;
    final Map<String, Integer> annexBFunctionScopeLocals;
    final CaptureResolver captureResolver;
    final BytecodeEmitter emitter;
    final List<String> inheritedWithObjectBindingNames;
    final Deque<LoopContext> loopStack;
    final Set<String> nonDeletableGlobalBindings;
    final Deque<CompilerScope> scopes;
    final Set<String> tdzLocals;
    final Deque<Integer> withObjectLocalStack;
    boolean hasEnclosingArgumentsBinding;
    boolean inGlobalScope;
    boolean isGlobalProgram;
    boolean isInArrowFunction;
    boolean isInAsyncFunction;
    int maxLocalCount;
    String pendingLoopLabel;
    boolean predeclareProgramLexicalsAsLocals;
    Map<String, JSSymbol> privateSymbols;
    int scopeDepth;
    String sourceCode;
    boolean strictMode;
    CompilerScope varDeclarationScopeOverride;
    boolean varInGlobalProgram;

    CompilerContext() {
        this(false, null);
    }

    CompilerContext(boolean inheritedStrictMode) {
        this(inheritedStrictMode, null);
    }

    CompilerContext(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver) {
        this.annexBFunctionNames = new HashSet<>();
        this.annexBFunctionScopeLocals = new HashMap<>();
        this.emitter = new BytecodeEmitter();
        this.scopes = new ArrayDeque<>();
        this.loopStack = new ArrayDeque<>();
        this.withObjectLocalStack = new ArrayDeque<>();
        this.inheritedWithObjectBindingNames = new ArrayList<>();
        this.captureResolver = new CaptureResolver(parentCaptureResolver, this::findBindingInScopes);
        this.inGlobalScope = false;
        this.isGlobalProgram = false;
        this.isInAsyncFunction = false;
        this.isInArrowFunction = false;
        this.maxLocalCount = 0;
        this.nonDeletableGlobalBindings = new HashSet<>();
        this.tdzLocals = new HashSet<>();
        this.sourceCode = null;
        this.scopeDepth = 0;
        this.privateSymbols = Map.of();
        this.predeclareProgramLexicalsAsLocals = false;
        this.strictMode = inheritedStrictMode;
        this.varInGlobalProgram = false;
        this.varDeclarationScopeOverride = null;
    }


    // ---- Scope management ----

    static Set<String> buildParameterNames(List<Pattern> params, List<Statement> body) {
        Set<String> paramNames = new HashSet<>();
        for (Pattern param : params) {
            paramNames.addAll(extractBoundNames(param));
        }
        if (!paramNames.contains("arguments")) {
            boolean hasVarArguments = false;
            for (Statement stmt : body) {
                if (stmt instanceof VariableDeclaration vd && vd.kind() == VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator d : vd.declarations()) {
                        if (d.id() instanceof Identifier id && "arguments".equals(id.name())) {
                            hasVarArguments = true;
                            break;
                        }
                    }
                }
                if (hasVarArguments) {
                    break;
                }
            }
            if (!hasVarArguments) {
                paramNames.add("arguments");
            }
        }
        return paramNames;
    }

    static int computeDefinedArgCount(List<Pattern> params, List<Expression> defaults, boolean hasRest) {
        if (defaults == null) {
            return params.size();
        }
        int count = 0;
        for (int i = 0; i < params.size(); i++) {
            if (i < defaults.size() && defaults.get(i) != null) {
                break;
            }
            count++;
        }
        return count;
    }

    static boolean containsVarArgumentsDeclaration(List<Statement> statements) {
        for (Statement stmt : statements) {
            if (statementContainsVarArguments(stmt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract all bound identifier names from a pattern.
     * Handles Identifier, ObjectPattern, ArrayPattern, AssignmentPattern, and RestElement.
     */
    static List<String> extractBoundNames(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            return List.of(id.name());
        } else if (pattern instanceof ObjectPattern objPattern) {
            List<String> names = new ArrayList<>();
            for (ObjectPattern.Property prop : objPattern.properties()) {
                names.addAll(extractBoundNames(prop.value()));
            }
            if (objPattern.restElement() != null) {
                names.addAll(extractBoundNames(objPattern.restElement().argument()));
            }
            return names;
        } else if (pattern instanceof ArrayPattern arrPattern) {
            List<String> names = new ArrayList<>();
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    names.addAll(extractBoundNames(element));
                }
            }
            return names;
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            return extractBoundNames(assignPattern.left());
        } else if (pattern instanceof RestElement restElement) {
            return extractBoundNames(restElement.argument());
        }
        return List.of();
    }

    static String[] extractLocalVarNames(CompilerScope scope) {
        int count = scope.getLocalCount();
        if (count == 0) {
            return null;
        }
        String[] names = new String[count];
        for (var entry : scope.getLocalNamesByIndex().entrySet()) {
            int index = entry.getKey();
            String name = entry.getValue();
            if (index >= 0 && index < count) {
                names[index] = name;
            }
        }
        return names;
    }

    static String[] extractLocalVarNames(Deque<CompilerScope> scopes, int localCount) {
        if (localCount == 0) {
            return null;
        }
        String[] names = new String[localCount];
        for (CompilerScope scope : scopes) {
            for (var entry : scope.getLocalNamesByIndex().entrySet()) {
                int index = entry.getKey();
                String name = entry.getValue();
                if (index >= 0 && index < localCount) {
                    names[index] = name;
                }
            }
        }
        return names;
    }

    static boolean hasNonSimpleParameters(List<Pattern> params, List<Expression> defaults, RestParameter restParameter) {
        if (restParameter != null) {
            return true;
        }
        if (defaults != null) {
            for (Expression d : defaults) {
                if (d != null) {
                    return true;
                }
            }
        }
        // Destructuring parameters make the parameter list non-simple
        if (params != null) {
            for (Pattern param : params) {
                if (!(param instanceof Identifier)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Capture resolver delegates ----

    static boolean statementContainsVarArguments(Statement stmt) {
        if (stmt instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
            for (var d : varDecl.declarations()) {
                if (d.id() instanceof Identifier id && "arguments".equals(id.name())) {
                    return true;
                }
            }
        }
        if (stmt instanceof BlockStatement block) {
            return containsVarArgumentsDeclaration(block.body());
        }
        if (stmt instanceof IfStatement ifStmt) {
            if (statementContainsVarArguments(ifStmt.consequent())) {
                return true;
            }
            if (ifStmt.alternate() != null) {
                return statementContainsVarArguments(ifStmt.alternate());
            }
        }
        if (stmt instanceof ForStatement forStmt) {
            if (forStmt.init() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (var d : varDecl.declarations()) {
                    if (d.id() instanceof Identifier id && "arguments".equals(id.name())) {
                        return true;
                    }
                }
            }
            return statementContainsVarArguments(forStmt.body());
        }
        if (stmt instanceof WhileStatement whileStmt) {
            return statementContainsVarArguments(whileStmt.body());
        }
        if (stmt instanceof DoWhileStatement doWhileStmt) {
            return statementContainsVarArguments(doWhileStmt.body());
        }
        if (stmt instanceof ForInStatement forInStmt) {
            return statementContainsVarArguments(forInStmt.body());
        }
        if (stmt instanceof ForOfStatement forOfStmt) {
            return statementContainsVarArguments(forOfStmt.body());
        }
        if (stmt instanceof SwitchStatement switchStmt) {
            for (var c : switchStmt.cases()) {
                if (containsVarArgumentsDeclaration(c.consequent())) {
                    return true;
                }
            }
        }
        if (stmt instanceof TryStatement tryStmt) {
            if (containsVarArgumentsDeclaration(tryStmt.block().body())) {
                return true;
            }
            if (tryStmt.handler() != null) {
                if (containsVarArgumentsDeclaration(tryStmt.handler().body().body())) {
                    return true;
                }
            }
            if (tryStmt.finalizer() != null) {
                if (containsVarArgumentsDeclaration(tryStmt.finalizer().body())) {
                    return true;
                }
            }
        }
        if (stmt instanceof LabeledStatement labeledStmt) {
            return statementContainsVarArguments(labeledStmt.body());
        }
        return false;
    }

    LoopContext createLoopContext(int startOffset, int breakScopeDepth, int continueScopeDepth) {
        String label = pendingLoopLabel;
        pendingLoopLabel = null;
        return new LoopContext(startOffset, breakScopeDepth, continueScopeDepth, label);
    }

    // ---- Loop management ----

    CompilerScope currentScope() {
        if (scopes.isEmpty()) {
            throw new JSCompilerException("No scope available");
        }
        return scopes.peek();
    }

    void enterScope() {
        scopeDepth++;
        int baseIndex = scopes.isEmpty() ? 0 : currentScope().getLocalCount();
        scopes.push(new CompilerScope(baseIndex, scopeDepth));
    }

    // ---- Utility methods ----

    void exitScope() {
        CompilerScope exitingScope = scopes.pop();

        // Track the maximum local count reached
        int localCount = exitingScope.getLocalCount();
        if (localCount > maxLocalCount) {
            maxLocalCount = localCount;
        }

        // Update parent scope's nextLocalIndex to reflect locals allocated in child scope
        if (!scopes.isEmpty()) {
            CompilerScope parentScope = currentScope();
            if (localCount > parentScope.getLocalCount()) {
                parentScope.setLocalCount(localCount);
            }
            for (var entry : exitingScope.getLocalNamesByIndex().entrySet()) {
                String localName = entry.getValue();
                if (localName != null) {
                    parentScope.registerLocalName(entry.getKey(), localName);
                }
            }
        }
        scopeDepth--;
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

    private CaptureResolver.BindingInfo findBindingInScopes(String name) {
        for (CompilerScope scope : scopes) {
            Integer localIndex = scope.getLocal(name);
            if (localIndex != null) {
                return new CaptureResolver.BindingInfo(localIndex, scope.isConstLocal(name));
            }
        }
        return null;
    }

    Integer findCapturedBindingIndex(String name) {
        return captureResolver.findCapturedBindingIndex(name);
    }

    Integer findLocalInScopes(String name) {
        for (CompilerScope scope : scopes) {
            Integer localIndex = scope.getLocal(name);
            if (localIndex != null) {
                return localIndex;
            }
        }
        return null;
    }

    private String findLocalNameByIndex(int index) {
        for (CompilerScope scope : scopes) {
            String localName = scope.getLocalNamesByIndex().get(index);
            if (localName != null) {
                return localName;
            }
        }
        return null;
    }

    List<Integer> getActiveWithObjectLocals() {
        if (withObjectLocalStack.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(withObjectLocalStack);
    }

    String getMethodName(ClassDeclaration.MethodDefinition method) {
        Expression key = method.key();
        if (key instanceof Identifier id) {
            return id.name();
        } else if (key instanceof Literal literal) {
            return literal.value().toString();
        } else if (key instanceof PrivateIdentifier privateId) {
            return privateId.name();
        } else {
            return "[computed]";
        }
    }

    List<String> getVisibleWithObjectBindingNamesForNestedFunction() {
        List<String> names = new ArrayList<>();
        for (Integer localIndex : withObjectLocalStack) {
            String localName = findLocalNameByIndex(localIndex);
            if (localName != null) {
                names.add(localName);
            }
        }
        names.addAll(inheritedWithObjectBindingNames);
        return names;
    }

    boolean hasActiveIteratorLoops() {
        for (LoopContext loopContext : loopStack) {
            if (loopContext.hasIterator) {
                return true;
            }
        }
        return false;
    }

    boolean hasActiveWithObject() {
        return !withObjectLocalStack.isEmpty();
    }

    boolean hasEnclosingBlockScopeLocal(String name) {
        Iterator<CompilerScope> it = scopes.iterator();
        if (!it.hasNext()) {
            return false;
        }
        it.next(); // skip current scope (innermost)
        while (it.hasNext()) {
            CompilerScope scope = it.next();
            if (!it.hasNext()) {
                // This is the function body scope (outermost) - skip it
                break;
            }
            // Per B.3.5, simple catch parameters do not block Annex B hoisting.
            // Only lexical bindings (let/const/block-scoped functions) are blockers.
            if (scope.hasLexicalLocal(name)) {
                return true;
            }
        }
        return false;
    }

    // ---- Static utility methods ----

    boolean hasUseStrictDirective(BlockStatement block) {
        if (block == null || block.body().isEmpty()) {
            return false;
        }

        for (int statementIndex = 0; statementIndex < block.body().size(); statementIndex++) {
            Statement statement = block.body().get(statementIndex);
            if (!(statement instanceof ExpressionStatement expressionStatement)) {
                break;
            }
            if (!(expressionStatement.expression() instanceof Literal literal)) {
                break;
            }
            if (!(literal.value() instanceof String)) {
                break;
            }
            if (!"use strict".equals(literal.value())) {
                continue;
            }
            if (statementIndex == 0 && !isDirectiveStartAtBlockStart(block, literal)) {
                return false;
            }
            if (sourceCode == null) {
                return true;
            }
            SourceLocation literalLocation = literal.getLocation();
            if (literalLocation != null && isRawUseStrictDirectiveAt(literalLocation.offset())) {
                return true;
            }
        }
        return false;
    }

    boolean isCapturedBindingConst(String name) {
        return captureResolver.isCapturedBindingImmutable(name);
    }

    private boolean isDirectiveStartAtBlockStart(BlockStatement block, Literal literal) {
        if (sourceCode == null) {
            return true;
        }
        SourceLocation blockLocation = block.getLocation();
        SourceLocation literalLocation = literal.getLocation();
        if (blockLocation == null || literalLocation == null) {
            return true;
        }
        int scanStart = Math.max(0, blockLocation.offset() + 1);
        int scanEnd = literalLocation.offset();
        if (scanEnd < scanStart || scanEnd > sourceCode.length()) {
            return true;
        }
        int index = scanStart;
        while (index < scanEnd) {
            char current = sourceCode.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '/' && index + 1 < scanEnd) {
                char next = sourceCode.charAt(index + 1);
                if (next == '/') {
                    index += 2;
                    while (index < scanEnd) {
                        char lineChar = sourceCode.charAt(index);
                        if (lineChar == '\n' || lineChar == '\r') {
                            break;
                        }
                        index++;
                    }
                    continue;
                }
                if (next == '*') {
                    index += 2;
                    while (index + 1 < scanEnd) {
                        if (sourceCode.charAt(index) == '*' && sourceCode.charAt(index + 1) == '/') {
                            index += 2;
                            break;
                        }
                        index++;
                    }
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    boolean isLocalBindingConst(String name) {
        for (CompilerScope scope : scopes) {
            Integer localIndex = scope.getLocal(name);
            if (localIndex != null) {
                return scope.isConstLocal(name);
            }
        }
        return false;
    }

    private boolean isRawUseStrictDirectiveAt(int offset) {
        if (offset < 0 || offset >= sourceCode.length()) {
            return false;
        }

        char quote = sourceCode.charAt(offset);
        if (quote != '\'' && quote != '"') {
            return false;
        }

        final String strictDirective = "use strict";
        int sourceIndex = offset + 1;
        int directiveIndex = 0;
        while (sourceIndex < sourceCode.length()) {
            char current = sourceCode.charAt(sourceIndex);
            if (current == quote) {
                return directiveIndex == strictDirective.length();
            }
            if (current == '\\'
                    || current == '\n'
                    || current == '\r'
                    || current == '\u2028'
                    || current == '\u2029') {
                return false;
            }
            if (directiveIndex >= strictDirective.length() || current != strictDirective.charAt(directiveIndex)) {
                return false;
            }
            sourceIndex++;
            directiveIndex++;
        }
        return false;
    }

    boolean isSuperIdentifier(Expression expression) {
        return expression instanceof Identifier id && "super".equals(id.name());
    }

    boolean isSuperMemberExpression(MemberExpression memberExpr) {
        return isSuperIdentifier(memberExpr.object());
    }

    void popWithObjectLocal() {
        withObjectLocalStack.pop();
    }

    void pushWithObjectLocal(int localIndex) {
        withObjectLocalStack.push(localIndex);
    }

    Integer resolveCapturedBindingIndex(String name) {
        return captureResolver.resolveCapturedBindingIndex(name);
    }
}
