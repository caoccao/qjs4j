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
    final Deque<LoopContext> loopStack;
    final Set<String> nonDeletableGlobalBindings;
    final Deque<CompilerScope> scopes;
    final Set<String> tdzLocals;
    boolean hasEnclosingArgumentsBinding;
    boolean inGlobalScope;
    boolean isGlobalProgram;
    boolean isInArrowFunction;
    boolean isInAsyncFunction;
    int maxLocalCount;
    String pendingLoopLabel;
    Map<String, JSSymbol> privateSymbols;
    int scopeDepth;
    String sourceCode;
    boolean strictMode;
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
        this.captureResolver = new CaptureResolver(parentCaptureResolver, this::findLocalInScopes);
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
        this.strictMode = inheritedStrictMode;
        this.varInGlobalProgram = false;
    }


    // ---- Scope management ----

    static Set<String> buildParameterNames(List<Identifier> params, List<Statement> body) {
        Set<String> paramNames = new HashSet<>();
        for (Identifier param : params) {
            paramNames.add(param.name());
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
                if (hasVarArguments) break;
            }
            if (!hasVarArguments) {
                paramNames.add("arguments");
            }
        }
        return paramNames;
    }

    static int computeDefinedArgCount(List<Identifier> params, List<Expression> defaults, boolean hasRest) {
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

    static String[] extractLocalVarNames(CompilerScope scope) {
        int count = scope.getLocalCount();
        if (count == 0) {
            return null;
        }
        String[] names = new String[count];
        for (var entry : scope.getLocals().entrySet()) {
            String name = entry.getKey();
            int index = entry.getValue();
            if (index >= 0 && index < count && !name.startsWith("$")) {
                names[index] = name;
            }
        }
        return names;
    }

    static boolean hasNonSimpleParameters(List<Expression> defaults, RestParameter restParameter) {
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
            if (statementContainsVarArguments(ifStmt.consequent())) return true;
            if (ifStmt.alternate() != null) return statementContainsVarArguments(ifStmt.alternate());
        }
        if (stmt instanceof ForStatement forStmt) {
            if (forStmt.init() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (var d : varDecl.declarations()) {
                    if (d.id() instanceof Identifier id && "arguments".equals(id.name())) return true;
                }
            }
            return statementContainsVarArguments(forStmt.body());
        }
        if (stmt instanceof WhileStatement whileStmt) {
            return statementContainsVarArguments(whileStmt.body());
        }
        if (stmt instanceof ForInStatement forInStmt) {
            return statementContainsVarArguments(forInStmt.body());
        }
        if (stmt instanceof ForOfStatement forOfStmt) {
            return statementContainsVarArguments(forOfStmt.body());
        }
        if (stmt instanceof SwitchStatement switchStmt) {
            for (var c : switchStmt.cases()) {
                if (containsVarArgumentsDeclaration(c.consequent())) return true;
            }
        }
        if (stmt instanceof TryStatement tryStmt) {
            if (containsVarArgumentsDeclaration(tryStmt.block().body())) return true;
            if (tryStmt.handler() != null) {
                if (containsVarArgumentsDeclaration(tryStmt.handler().body().body())) return true;
            }
            if (tryStmt.finalizer() != null) {
                if (containsVarArgumentsDeclaration(tryStmt.finalizer().body())) return true;
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

    // ---- Static utility methods ----

    boolean hasActiveIteratorLoops() {
        for (LoopContext loopContext : loopStack) {
            if (loopContext.hasIterator) {
                return true;
            }
        }
        return false;
    }

    boolean hasEnclosingBlockScopeLocal(String name) {
        Iterator<CompilerScope> it = scopes.iterator();
        if (!it.hasNext()) return false;
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

    boolean hasUseStrictDirective(BlockStatement block) {
        if (block == null || block.body().isEmpty()) {
            return false;
        }

        Statement firstStmt = block.body().get(0);
        if (!(firstStmt instanceof ExpressionStatement exprStmt)) {
            return false;
        }

        if (!(exprStmt.expression() instanceof Literal literal)) {
            return false;
        }

        Object value = literal.value();
        return "use strict".equals(value);
    }

    boolean isSuperIdentifier(Expression expression) {
        return expression instanceof Identifier id && "super".equals(id.name());
    }

    boolean isSuperMemberExpression(MemberExpression memberExpr) {
        return isSuperIdentifier(memberExpr.object());
    }

    Integer resolveCapturedBindingIndex(String name) {
        return captureResolver.resolveCapturedBindingIndex(name);
    }
}
