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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Manages with-object scope tracking during compilation.
 * Tracks active with-statement locals and inherited with-object binding names.
 */
final class WithObjectManager {
    private final List<String> inheritedBindingNames;
    private final Deque<Integer> withObjectLocalStack;

    WithObjectManager() {
        this.withObjectLocalStack = new ArrayDeque<>();
        this.inheritedBindingNames = new ArrayList<>();
    }

    void addInheritedBindingNames(List<String> names) {
        inheritedBindingNames.addAll(names);
    }

    List<Integer> getActiveLocals() {
        if (withObjectLocalStack.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(withObjectLocalStack);
    }

    List<String> getInheritedBindingNames() {
        return inheritedBindingNames;
    }

    List<String> getVisibleBindingNamesForNestedFunction(ScopeManager scopeManager) {
        List<String> names = new ArrayList<>();
        for (Integer localIndex : withObjectLocalStack) {
            String localName = scopeManager.findLocalNameByIndex(localIndex);
            if (localName != null) {
                names.add(localName);
            }
        }
        names.addAll(inheritedBindingNames);
        return names;
    }

    boolean hasActiveWithObject() {
        return !withObjectLocalStack.isEmpty();
    }

    void popLocal() {
        withObjectLocalStack.pop();
    }

    void pushLocal(int localIndex) {
        withObjectLocalStack.push(localIndex);
    }
}
