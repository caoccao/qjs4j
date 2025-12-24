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

package com.caoccao.qjs4j.compiler;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a lexical scope for variable binding.
 */
public final class Scope {
    private final Scope parent;
    private final Map<String, Variable> variables;
    private final ScopeType type;

    public enum ScopeType {
        GLOBAL,
        FUNCTION,
        BLOCK,
        MODULE
    }

    public Scope(Scope parent, ScopeType type) {
        this.parent = parent;
        this.type = type;
        this.variables = new HashMap<>();
    }

    public void declareVariable(String name, Variable variable) {
    }

    public Variable resolveVariable(String name) {
        return null;
    }

    public Scope getParent() {
        return parent;
    }

    public static class Variable {
        private final String name;
        private final boolean isConst;
        private final boolean isLet;
        private final int index;

        public Variable(String name, boolean isConst, boolean isLet, int index) {
            this.name = name;
            this.isConst = isConst;
            this.isLet = isLet;
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public boolean isConst() {
            return isConst;
        }

        public boolean isLet() {
            return isLet;
        }

        public int getIndex() {
            return index;
        }
    }
}
