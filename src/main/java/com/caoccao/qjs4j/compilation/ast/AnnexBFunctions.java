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

package com.caoccao.qjs4j.compilation.ast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walk lexical scopes for sloppy block functions. Global collision checks examine direct declarations in blocks,
 * conditionals and switches. Bytecode hoisting also handles labels, loops and catch bindings.
 */
public enum AnnexBFunctions {
    GLOBAL_COLLISIONS,
    HOISTING;

    private boolean collectFunction(Statement child, Set<String> lexicals, Set<String> candidates) {
        FunctionDeclaration function = child instanceof FunctionDeclaration direct
                ? direct
                : this == HOISTING && child instanceof LabeledStatement
                        ? child.unwrapLabeledFunctionDeclaration()
                        : null;
        if (function == null || function.getId() == null) {
            return false;
        }
        String name = function.getId().getName();
        if (function.isAnnexBSimpleDeclaration() && !lexicals.contains(name)) {
            candidates.add(name);
        }
        return true;
    }

    public void scan(Statement statement, Set<String> lexicals, Set<String> candidates) {
        if (statement instanceof BlockStatement block) {
            scanBlock(block.getBody(), lexicals, candidates);
        } else if (statement instanceof IfStatement conditional) {
            scanChild(conditional.getConsequent(), lexicals, candidates);
            scanChild(conditional.getAlternate(), lexicals, candidates);
        } else if (statement instanceof SwitchStatement selection) {
            Set<String> scope = new HashSet<>(lexicals);
            for (SwitchStatement.SwitchCase arm : selection.getCases()) {
                for (Statement child : arm.getConsequent()) {
                    addLexicals(child, scope);
                }
            }
            for (SwitchStatement.SwitchCase arm : selection.getCases()) {
                for (Statement child : arm.getConsequent()) {
                    collectFunction(child, scope, candidates);
                    scan(child, scope, candidates);
                }
            }
        } else if (statement instanceof WithStatement with) {
            scan(with.getBody(), lexicals, candidates);
        } else if (this == HOISTING) {
            scanHoistableBody(statement, lexicals, candidates);
        }
    }

    private void scanBlock(List<Statement> body, Set<String> outer, Set<String> candidates) {
        Set<String> lexicals = outer;
        Set<String> functionNames = null;
        for (Statement child : body) {
            if (child instanceof VariableDeclaration declaration && declaration.getKind() != VariableKind.VAR) {
                if (lexicals == outer) {
                    lexicals = new HashSet<>(outer);
                }
                addBindings(declaration, lexicals);
            } else if (this == HOISTING) {
                FunctionDeclaration function = child.unwrapLabeledFunctionDeclaration();
                if (function != null && function.getId() != null) {
                    if (functionNames == null) {
                        functionNames = new HashSet<>();
                    }
                    functionNames.add(function.getId().getName());
                }
            }
        }
        // The block's functions only shadow candidates in deeper scopes.
        if (functionNames != null) {
            functionNames.addAll(lexicals);
        }
        for (Statement child : body) {
            collectFunction(child, lexicals, candidates);
            scan(child, functionNames == null ? lexicals : functionNames, candidates);
        }
    }

    private void scanChild(Statement child, Set<String> lexicals, Set<String> candidates) {
        if (child != null && !collectFunction(child, lexicals, candidates)) {
            scan(child, lexicals, candidates);
        }
    }

    private void scanHoistableBody(Statement statement, Set<String> lexicals, Set<String> candidates) {
        if (statement instanceof ForLoop loop) {
            Set<String> scope = lexicals;
            if (loop.getHead() instanceof VariableDeclaration declaration
                    && declaration.getKind() != VariableKind.VAR) {
                scope = new HashSet<>(lexicals);
                addBindings(declaration, scope);
            }
            scan(loop.getBody(), scope, candidates);
        } else if (statement instanceof WhileLoop loop) {
            scan(loop.getBody(), lexicals, candidates);
        } else if (statement instanceof LabeledStatement labeled && labeled.getBody() != null) {
            scanChild(labeled.getBody(), lexicals, candidates);
        } else if (statement instanceof TryStatement guarded) {
            scan(guarded.getBlock(), lexicals, candidates);
            if (guarded.getHandler() != null) {
                Set<String> scope = lexicals;
                Pattern parameter = guarded.getHandler().getParam();
                // B.3.5 exempts a simple catch identifier. Destructuring introduces lexical blockers.
                if (parameter != null && !(parameter instanceof Identifier)) {
                    scope = new HashSet<>(lexicals);
                    scope.addAll(parameter.getBoundNames());
                }
                scan(guarded.getHandler().getBody(), scope, candidates);
            }
            scan(guarded.getFinalizer(), lexicals, candidates);
        }
    }

    private static void addBindings(VariableDeclaration declaration, Set<String> names) {
        for (VariableDeclarator binding : declaration.getDeclarations()) {
            Pattern pattern = binding.getId();
            if (pattern instanceof Identifier identifier) {
                names.add(identifier.getName());
            } else if (pattern != null) {
                names.addAll(pattern.getBoundNames());
            }
        }
    }

    private static void addLexicals(Statement child, Set<String> names) {
        if (child instanceof VariableDeclaration declaration && declaration.getKind() != VariableKind.VAR) {
            addBindings(declaration, names);
        }
    }
}
