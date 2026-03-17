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
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSCompilerException;

import java.util.*;

/**
 * Shared mutable state for the bytecode compiler.
 * Holds all fields, scope management, loop management, and utility methods
 * that are shared across the delegate compiler classes.
 */
final class CompilerContext {
    final Deque<List<Integer>> activeFinallyGosubPatches;
    final Deque<Integer> activeFinallyNipCatchCounts;
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
    boolean classFieldEvalContext;
    String classInnerNameToCapture;
    JSContext context;
    boolean emitTailCalls;
    boolean evalMode;
    int evalReturnLocalIndex;
    int finallySubroutineDepth;
    boolean hasEnclosingArgumentsBinding;
    boolean inClassBody;
    boolean inClassFieldInitializer;
    boolean inGlobalScope;
    String inferredClassName;
    boolean isGlobalProgram;
    boolean isInArrowFunction;
    boolean isInAsyncFunction;
    boolean isInGeneratorFunction;
    int maxLocalCount;
    String pendingLoopLabel;
    Runnable pendingPostSuperInitialization;
    boolean predeclareProgramLexicalsAsLocals;
    Map<String, JSSymbol> privateSymbols;
    int scopeDepth;
    String sourceCode;
    boolean strictMode;
    CompilerScope varDeclarationScopeOverride;
    boolean varInGlobalProgram;

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
        this.activeFinallyGosubPatches = new ArrayDeque<>();
        this.activeFinallyNipCatchCounts = new ArrayDeque<>();
        this.annexBFunctionNames = new HashSet<>();
        this.annexBFunctionScopeLocals = new HashMap<>();
        this.emitter = new BytecodeEmitter();
        this.context = context;
        this.evalMode = false;
        this.evalReturnLocalIndex = -1;
        this.finallySubroutineDepth = 0;
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

    LoopContext createLoopContext(int startOffset, int breakScopeDepth, int continueScopeDepth) {
        String label = pendingLoopLabel;
        pendingLoopLabel = null;
        return new LoopContext(startOffset, breakScopeDepth, continueScopeDepth, label);
    }

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
                return new CaptureResolver.BindingInfo(localIndex,
                        scope.isConstLocal(name), scope.isFunctionNameLocal(name));
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

    String[] getLocalVarNames() {
        int localCount = currentScope().getLocalCount();
        String[] localVarNames = localCount == 0 ? null : new String[localCount];
        if (localCount > 0) {
            scopes.forEach(scope -> scope.fillLocalVarNames(localVarNames));
        }
        return localVarNames;
    }

    String getMethodName(MethodDefinition method) {
        Expression key = method.getKey();
        if (key instanceof Identifier id) {
            return id.getName();
        } else if (key instanceof Literal literal) {
            return literal.getValue() != null ? literal.getValue().toString() : "null";
        } else if (key instanceof PrivateIdentifier privateId) {
            return privateId.getName();
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

    boolean isCapturedBindingConst(String name) {
        return captureResolver.isCapturedBindingImmutable(name);
    }

    boolean isCapturedBindingFunctionName(String name) {
        return captureResolver.isCapturedBindingFunctionName(name);
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

    boolean isLocalBindingFunctionName(String name) {
        for (CompilerScope scope : scopes) {
            Integer localIndex = scope.getLocal(name);
            if (localIndex != null) {
                return scope.isFunctionNameLocal(name);
            }
        }
        return false;
    }

}
