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

package com.caoccao.qjs4j.compilation.parser;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import java.util.*;

/** ECMAScript declaration-name and scope conflict checks for statements. */
record DeclarationEarlyErrors(ParserContext parserContext) {
    void addModuleLexicalName(String name) {
        if (parserContext.moduleMode && parserContext.functionNesting == 0) {
            addLexicalName(parserContext.moduleLexicalNames, parserContext.moduleVarNames, name);
        }
    }

    static void addLexicalName(Set<String> lexicalNames, Set<String> varNames, String name) {
        if (varNames.contains(name) || !lexicalNames.add(name)) {
            throw duplicate(name);
        }
    }

    void collectPatternBoundNames(Pattern pattern, Set<String> names) {
        List<String> boundNames = pattern.getBoundNames();
        if (parserContext.inClassStaticInit) {
            for (String name : boundNames) {
                if (JSKeyword.AWAIT.equals(name)) {
                    throw new JSSyntaxErrorException("Unexpected 'await' keyword");
                }
            }
        }
        names.addAll(boundNames);
    }

    void collectPatternBoundNamesAndCheckDuplicates(Pattern pattern, Set<String> names) {
        for (String name : pattern.getBoundNames()) {
            if (parserContext.inClassStaticInit && JSKeyword.AWAIT.equals(name)) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            if (!names.add(name)) {
                throw duplicate(name);
            }
        }
    }

    /**
     * The LexicallyDeclaredNames of a function body, static block or arrow body (ES2024 15.2.1): its top-level
     * {@code let}, {@code const}, {@code using} and {@code class} declarations. Function declarations at the top of a
     * function body are var-scoped, not lexical.
     *
     * @param statements
     *            the body's statements
     * @return the names
     * @throws JSSyntaxErrorException
     *             when a name is declared twice
     */
    static Set<String> collectFunctionBodyLexicalNames(List<Statement> statements) {
        Set<String> names = new HashSet<>();
        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration declaration && declaration.getKind() != VariableKind.VAR) {
                for (VariableDeclarator declarator : declaration.getDeclarations()) {
                    for (String name : declarator.getId().getBoundNames()) {
                        addUnique(names, name);
                    }
                }
            } else if (statement instanceof ClassDeclaration declaration) {
                addUnique(names, declaration.getId().getName());
            }
        }
        return names;
    }

    void collectVarDeclaredNames(Statement statement, Set<String> varNames) {
        if (statement == null) {
            return;
        }
        for (VariableDeclarator declarator : statement.getVarDeclarators()) {
            collectPatternBoundNames(declarator.getId(), varNames);
        }
    }

    private static boolean hasBlockLexicalDeclaration(List<Statement> statements) {
        for (Statement statement : statements) {
            if (statement instanceof Declaration
                    || statement instanceof VariableDeclaration variable && variable.getKind() != VariableKind.VAR) {
                return true;
            }
        }
        return false;
    }

    void validateBlockEarlyErrors(List<Statement> statements) {
        // Without a direct lexical declaration there can be no lexical/var conflict.
        // Static initializers still need to inspect var patterns for a forbidden "await".
        if (!parserContext.inClassStaticInit && !hasBlockLexicalDeclaration(statements)) {
            return;
        }
        BlockBindings bindings = new BlockBindings();
        for (Statement statement : statements) {
            bindings.collect(statement, false);
        }
        bindings.checkVarConflicts();
    }

    /**
     * The early errors of a function body, class static block or arrow body (ES2024 15.2.1): no lexically declared name
     * twice, and none that is also var-declared — by a {@code var} anywhere in the body outside nested functions, or by
     * a top-level function declaration.
     * <p>
     * Only module and script top levels and blocks used to be checked, so {@code function f() { let x; var x; }} was
     * accepted and the {@code var} silently won.
     *
     * @param statements
     *            the body's statements
     */
    void validateFunctionBodyEarlyErrors(List<Statement> statements) {
        Set<String> lexicalNames = collectFunctionBodyLexicalNames(statements);
        if (lexicalNames.isEmpty() && !parserContext.inClassStaticInit) {
            return;
        }
        Set<String> varNames = new HashSet<>();
        for (Statement statement : statements) {
            collectVarDeclaredNames(statement, varNames);
            FunctionDeclaration functionDeclaration = statement.unwrapLabeledFunctionDeclaration();
            if (functionDeclaration != null) {
                varNames.add(functionDeclaration.getId().getName());
            }
        }
        checkConflicts(lexicalNames, varNames);
    }

    void validateCatchClauseEarlyErrors(Pattern catchParameter, BlockStatement catchBody) {
        if (catchParameter == null || catchBody == null) {
            return;
        }
        Set<String> catchParameterNames = new HashSet<>();
        collectPatternBoundNames(catchParameter, catchParameterNames);

        Set<String> lexicalNames = new HashSet<>();
        for (Statement statement : catchBody.getBody()) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternBoundNames(declarator.getId(), lexicalNames);
                }
            } else if (statement instanceof FunctionDeclaration functionDeclaration
                    && functionDeclaration.getId() != null) {
                lexicalNames.add(functionDeclaration.getId().getName());
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                lexicalNames.add(classDeclaration.getId().getName());
            }
        }

        checkConflicts(catchParameterNames, lexicalNames);

        // Annex B.3.4 lets a var in the catch block redeclare a simple catch parameter only. A destructuring
        // parameter's names conflict with any var of the block: `catch ([e]) { var e }` is a SyntaxError.
        if (!(catchParameter instanceof Identifier)) {
            Set<String> varNames = new HashSet<>();
            for (Statement statement : catchBody.getBody()) {
                collectVarDeclaredNames(statement, varNames);
            }
            checkConflicts(catchParameterNames, varNames);
        }
    }

    void validateForInOfDeclarationBodyEarlyErrors(VariableDeclaration declaration, Statement statement) {
        if (declaration == null || declaration.getKind() == VariableKind.VAR) {
            return;
        }
        if (!parserContext.inClassStaticInit && (statement == null || statement.getVarDeclarators().isEmpty())) {
            return;
        }
        Set<String> boundNames = new HashSet<>();
        for (VariableDeclarator declarator : declaration.getDeclarations()) {
            collectPatternBoundNames(declarator.getId(), boundNames);
        }
        Set<String> varNames = new HashSet<>();
        collectVarDeclaredNames(statement, varNames);
        checkConflicts(boundNames, varNames);
    }

    void validateForInOfDeclarationBoundNames(VariableDeclaration declaration) {
        if (declaration == null || declaration.getKind() == VariableKind.VAR) {
            return;
        }
        Set<String> boundNames = new HashSet<>();
        for (VariableDeclarator declarator : declaration.getDeclarations()) {
            collectPatternBoundNamesAndCheckDuplicates(declarator.getId(), boundNames);
        }
    }

    void validateProgramEarlyErrors(List<Statement> statements) {
        Set<String> lexicalNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();

        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration variableDeclaration) {
                if (variableDeclaration.getKind() == VariableKind.VAR) {
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        collectPatternBoundNames(declarator.getId(), varNames);
                    }
                } else {
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        Set<String> names = new HashSet<>();
                        collectPatternBoundNames(declarator.getId(), names);
                        for (String name : names) {
                            addLexicalName(lexicalNames, varNames, name);
                        }
                    }
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String className = classDeclaration.getId().getName();
                addLexicalName(lexicalNames, varNames, className);
            } else if (statement instanceof FunctionDeclaration functionDeclaration
                    && functionDeclaration.getId() != null) {
                varNames.add(functionDeclaration.getId().getName());
            }
            collectVarDeclaredNames(statement, varNames);
        }

        checkConflicts(lexicalNames, varNames);
    }

    void validateSwitchCaseBlockEarlyErrors(List<SwitchStatement.SwitchCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return;
        }
        BlockBindings bindings = new BlockBindings();
        for (SwitchStatement.SwitchCase switchCase : cases) {
            if (switchCase != null) {
                for (Statement statement : switchCase.getConsequent()) {
                    bindings.collect(statement, true);
                }
            }
        }
        bindings.checkVarConflicts();
    }

    private final class BlockBindings {
        private final Set<String> lexicalNames = new HashSet<>();
        private final Set<String> simpleFunctionLexicalNames = new HashSet<>();
        private final Set<String> varNames = new HashSet<>();

        void checkVarConflicts() {
            checkConflicts(lexicalNames, varNames);
        }

        void collect(Statement statement, boolean inSwitch) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                if (inSwitch && StatementParser.hasUsingInitializer(variableDeclaration)) {
                    throw new JSSyntaxErrorException(
                            "using declarations are not allowed directly in case/default clauses");
                }
                for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    Pattern pattern = declarator.getId();
                    if (pattern.getBoundNames().size() <= 1) {
                        collectPatternBoundNamesAndCheckDuplicates(pattern, lexicalNames);
                        continue;
                    }
                    Set<String> names = new HashSet<>();
                    if (inSwitch) {
                        collectPatternBoundNamesAndCheckDuplicates(pattern, names);
                    } else {
                        collectPatternBoundNames(pattern, names);
                    }
                    for (String name : names) {
                        addUnique(lexicalNames, name);
                    }
                }
            } else if (statement instanceof FunctionDeclaration functionDeclaration
                    && functionDeclaration.getId() != null) {
                String name = functionDeclaration.getId().getName();
                boolean isSimpleFunction = functionDeclaration.isAnnexBSimpleDeclaration();
                if (lexicalNames.contains(name)) {
                    boolean duplicatedSimpleFunctionDeclaration = simpleFunctionLexicalNames.contains(name)
                            && isSimpleFunction;
                    if (!duplicatedSimpleFunctionDeclaration || parserContext.strictMode) {
                        throw duplicate(name);
                    }
                } else {
                    lexicalNames.add(name);
                }
                if (isSimpleFunction) {
                    simpleFunctionLexicalNames.add(name);
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String name = classDeclaration.getId().getName();
                if (lexicalNames.contains(name)) {
                    throw duplicate(name);
                }
                lexicalNames.add(name);
            }
            collectVarDeclaredNames(statement, varNames);
        }
    }

    private static JSSyntaxErrorException duplicate(String name) {
        return new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
    }

    private static void checkConflicts(Set<String> names, Set<String> other) {
        for (String name : names) {
            if (other.contains(name)) {
                throw duplicate(name);
            }
        }
    }

    private static void addUnique(Set<String> names, String name) {
        if (!names.add(name)) {
            throw duplicate(name);
        }
    }
}
