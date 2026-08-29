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

package com.caoccao.qjs4j.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The realm's global lexical environment, and the declaration tables that
 * GlobalDeclarationInstantiation checks against.
 * <p>
 * Following QuickJS's {@code global_var_obj} pattern: {@code let}, {@code const} and {@code class}
 * at the top level of a script do not become properties of the global object, so they live here
 * instead; {@code var} and function declarations do become properties, and only their names are
 * tracked, so a later script's {@code let} of the same name can be rejected.
 * <p>
 * A plain state holder with no reference back to the context. {@link JSContext} keeps the public
 * accessors the VM and {@code JSGlobalObject} already call and delegates to this class.
 */
final class GlobalLexicalScope {
    /**
     * The value a {@code let}/{@code const} binding holds between its declaration being registered
     * and its initializer running — the temporal dead zone, as a value.
     */
    private static final JSValue UNINITIALIZED = new JSSymbol("GlobalLexicalUninitialized");
    private final Set<String> constDeclarations = new HashSet<>();
    private final Set<String> lexDeclarations = new HashSet<>();
    private final Map<String, JSValue> lexicalBindings = new HashMap<>();
    private final Set<String> varDeclarations = new HashSet<>();
    private boolean activeFunctionBindingConfigurable;
    private Set<String> activeFunctionBindingInitializations;

    GlobalLexicalScope() {
        activeFunctionBindingConfigurable = false;
        activeFunctionBindingInitializations = null;
    }

    /**
     * Drop everything this holds.
     * <p>
     * Called from {@link JSContext#close()}: a closed context must own nothing, and the binding
     * tables reach every value a script bound at the top level.
     */
    void clear() {
        lexicalBindings.clear();
        constDeclarations.clear();
        lexDeclarations.clear();
        varDeclarations.clear();
        activeFunctionBindingInitializations = null;
    }

    boolean consumeFunctionBindingInitialization(String name) {
        return activeFunctionBindingInitializations != null
                && activeFunctionBindingInitializations.remove(name);
    }

    /**
     * Register the declarations of a top-level script, after
     * GlobalDeclarationInstantiation's checks have passed.
     * <p>
     * Each {@code let}/{@code const} name starts in the temporal dead zone, and {@code putIfAbsent}
     * is what keeps a redeclaration check that has already run from resetting a binding that a
     * previous script initialized.
     *
     * @param newConstDeclarations the {@code const} names
     * @param newLexDeclarations   the {@code let}, {@code const} and {@code class} names
     * @param newVarDeclarations   the {@code var} and function names
     */
    void declareScriptGlobals(
            Set<String> newConstDeclarations,
            Set<String> newLexDeclarations,
            Set<String> newVarDeclarations) {
        constDeclarations.addAll(newConstDeclarations);
        lexDeclarations.addAll(newLexDeclarations);
        varDeclarations.addAll(newVarDeclarations);
        for (String lexicalName : newLexDeclarations) {
            lexicalBindings.putIfAbsent(lexicalName, UNINITIALIZED);
        }
    }

    Set<String> getBindingNames() {
        return new HashSet<>(lexicalBindings.keySet());
    }

    boolean hasConstDeclaration(String name) {
        return constDeclarations.contains(name);
    }

    boolean hasLexDeclaration(String name) {
        return lexDeclarations.contains(name);
    }

    boolean hasLexicalBinding(String name) {
        return lexicalBindings.containsKey(name);
    }

    boolean hasVarDeclaration(String name) {
        return varDeclarations.contains(name);
    }

    boolean isActiveFunctionBindingConfigurable() {
        return activeFunctionBindingConfigurable;
    }

    boolean isBindingInitialized(String name) {
        JSValue value = lexicalBindings.get(name);
        return value != null && value != UNINITIALIZED;
    }

    JSValue readBinding(String name) {
        return lexicalBindings.get(name);
    }

    void setFunctionBindingInitializations(Set<String> functionNames, boolean configurable) {
        activeFunctionBindingConfigurable = configurable;
        activeFunctionBindingInitializations = functionNames;
    }

    void writeBinding(String name, JSValue value) {
        lexicalBindings.put(name, value);
    }
}
