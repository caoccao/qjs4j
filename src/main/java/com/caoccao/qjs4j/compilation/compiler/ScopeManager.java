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

import com.caoccao.qjs4j.exceptions.JSCompilerException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

/**
 * Manages lexical scopes during compilation.
 * Tracks scope depth, local variables, and provides scope traversal operations.
 */
final class ScopeManager implements Iterable<CompilerScope> {
    private final Deque<CompilerScope> scopes;
    private int maxLocalCount;
    private int scopeDepth;

    ScopeManager() {
        this.scopes = new ArrayDeque<>();
        this.scopeDepth = 0;
        this.maxLocalCount = 0;
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

    CaptureResolver.BindingInfo findBindingInScopes(String name) {
        return scopes.stream()
                .map(scope -> {
                    Integer localIndex = scope.getLocal(name);
                    return localIndex == null
                            ? null
                            : new CaptureResolver.BindingInfo(
                            localIndex,
                            scope.isConstLocal(name),
                            scope.isFunctionNameLocal(name));
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    Integer findLocalInScopes(String name) {
        return scopes.stream()
                .map(scope -> scope.getLocal(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    String findLocalNameByIndex(int index) {
        return scopes.stream()
                .map(scope -> scope.getLocalNamesByIndex().get(index))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    String[] getLocalVarNames() {
        String[] localVarNames;
        int localCount = currentScope().getLocalCount();
        if (localCount > 0) {
            localVarNames = new String[localCount];
            scopes.forEach(scope -> scope.fillLocalVarNames(localVarNames));
        } else {
            localVarNames = null;
        }
        return localVarNames;
    }

    int getMaxLocalCount() {
        return maxLocalCount;
    }

    int getScopeDepth() {
        return scopeDepth;
    }

    boolean hasEnclosingBlockScopeLocal(String name) {
        // Per B.3.5, simple catch parameters do not block Annex B hoisting.
        // Only lexical bindings (let/const/block-scoped functions) are blockers.
        // Ignore current (innermost) and function-body (outermost) scopes.
        return scopes.stream()
                .skip(1)
                .limit(Math.max(0L, scopes.size() - 2L))
                .anyMatch(scope -> scope.hasLexicalLocal(name));
    }

    boolean isEmpty() {
        return scopes.isEmpty();
    }

    boolean isLocalBindingConst(String name) {
        return scopes.stream()
                .filter(scope -> scope.getLocal(name) != null)
                .map(scope -> scope.isConstLocal(name))
                .findFirst()
                .orElse(false);
    }

    boolean isLocalBindingFunctionName(String name) {
        return scopes.stream()
                .filter(scope -> scope.getLocal(name) != null)
                .map(scope -> scope.isFunctionNameLocal(name))
                .findFirst()
                .orElse(false);
    }

    @Override
    public Iterator<CompilerScope> iterator() {
        return scopes.iterator();
    }

    void updateMaxLocalCount(int localCount) {
        if (localCount > maxLocalCount) {
            maxLocalCount = localCount;
        }
    }
}
