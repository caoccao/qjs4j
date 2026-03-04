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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a lexical scope for tracking local variables.
 */
final class CompilerScope {
    private final Set<String> constLocals = new HashSet<>();
    private final Map<Integer, String> localNamesByIndex = new HashMap<>();
    private final Map<String, Integer> locals = new HashMap<>();
    private final int scopeDepth;
    private final Set<String> simpleCatchParams = new HashSet<>();
    private int nextLocalIndex;
    private boolean usingStackAsync;
    private Integer usingStackLocalIndex;

    CompilerScope() {
        this(0, 0);
    }

    CompilerScope(int baseIndex, int scopeDepth) {
        this.nextLocalIndex = baseIndex;
        this.scopeDepth = scopeDepth;
        this.usingStackAsync = false;
        this.usingStackLocalIndex = null;
    }

    int declareConstLocal(String name) {
        int index = declareLocal(name);
        constLocals.add(name);
        return index;
    }

    int declareLocal(String name) {
        if (locals.containsKey(name)) {
            return locals.get(name);
        }
        int index = nextLocalIndex++;
        locals.put(name, index);
        localNamesByIndex.put(index, name);
        return index;
    }

    /**
     * Declare a function parameter. Unlike declareLocal, always allocates a new
     * local slot even for duplicate names (allowed in sloppy mode). The name
     * mapping points to the last occurrence, matching QuickJS semantics where
     * each formal parameter occupies its own slot.
     */
    int declareParameter(String name) {
        int index = nextLocalIndex++;
        locals.put(name, index);
        localNamesByIndex.put(index, name);
        return index;
    }

    Integer getLocal(String name) {
        return locals.get(name);
    }

    int getLocalCount() {
        return nextLocalIndex;
    }

    Map<Integer, String> getLocalNamesByIndex() {
        return localNamesByIndex;
    }

    Map<String, Integer> getLocals() {
        return locals;
    }

    int getScopeDepth() {
        return scopeDepth;
    }

    Integer getUsingStackLocalIndex() {
        return usingStackLocalIndex;
    }

    boolean hasLexicalLocal(String name) {
        return locals.containsKey(name) && !simpleCatchParams.contains(name);
    }

    boolean isConstLocal(String name) {
        return constLocals.contains(name);
    }

    boolean isUsingStackAsync() {
        return usingStackAsync;
    }

    void markConstLocal(String name) {
        if (locals.containsKey(name)) {
            constLocals.add(name);
        }
    }

    void markSimpleCatchParam(String name) {
        simpleCatchParams.add(name);
    }

    void registerLocalName(int index, String name) {
        if (!localNamesByIndex.containsKey(index)) {
            localNamesByIndex.put(index, name);
        }
    }

    void setLocalCount(int count) {
        this.nextLocalIndex = count;
    }

    void setUsingStackLocal(int usingStackLocalIndex, boolean usingStackAsync) {
        this.usingStackLocalIndex = usingStackLocalIndex;
        this.usingStackAsync = usingStackAsync;
    }
}
