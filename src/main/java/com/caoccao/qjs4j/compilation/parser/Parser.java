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
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursive descent parser for JavaScript.
 * Converts tokens into an Abstract Syntax Tree (AST).
 * <p>
 * This is the public facade that delegates to specialized parser classes:
 * {@link ExpressionParser}, {@link StatementParser}, {@link FunctionClassParser},
 * {@link PatternParser}, and {@link LiteralParser}.
 * All shared mutable state is held in {@link ParserContext}.
 */
public final class Parser {
    private final ParserDelegates delegates;
    private final ParserContext parserContext;

    public Parser(Lexer lexer) {
        this(lexer, false);
    }

    public Parser(Lexer lexer, boolean moduleMode) {
        this(lexer, moduleMode, false);
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval) {
        this(lexer, moduleMode, isEval, false, 0, 0, 0, 0, false, false, Set.of());
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode) {
        this(lexer, moduleMode, isEval, inheritedStrictMode, 0, 0, 0, 0, false, false, Set.of());
    }

    public Parser(
            Lexer lexer,
            boolean moduleMode,
            boolean isEval,
            boolean inheritedStrictMode,
            boolean initialSuperPropertyAllowed,
            boolean allowNewTargetInEval) {
        this(
                lexer,
                moduleMode,
                isEval,
                inheritedStrictMode,
                0,
                0,
                0,
                0,
                initialSuperPropertyAllowed,
                allowNewTargetInEval,
                Set.of());
    }

    public Parser(
            Lexer lexer,
            boolean moduleMode,
            boolean isEval,
            boolean inheritedStrictMode,
            boolean initialSuperPropertyAllowed,
            boolean allowNewTargetInEval,
            Set<String> evalPrivateNames) {
        this(
                lexer,
                moduleMode,
                isEval,
                inheritedStrictMode,
                0,
                0,
                0,
                0,
                initialSuperPropertyAllowed,
                allowNewTargetInEval,
                evalPrivateNames);
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode,
           int functionNesting, int asyncFunctionNesting,
           int generatorFunctionNesting,
           int newTargetNesting,
           boolean initialSuperPropertyAllowed,
           boolean allowNewTargetInEval) {
        this(
                lexer,
                moduleMode,
                isEval,
                inheritedStrictMode,
                functionNesting,
                asyncFunctionNesting,
                generatorFunctionNesting,
                newTargetNesting,
                initialSuperPropertyAllowed,
                allowNewTargetInEval,
                Set.of());
    }

    // Package-private: used by Compiler / LiteralParser when eval private names are in scope.
    Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode,
           int functionNesting, int asyncFunctionNesting,
           int generatorFunctionNesting,
           int newTargetNesting,
           boolean initialSuperPropertyAllowed,
           boolean allowNewTargetInEval,
           Set<String> evalPrivateNames) {
        this.parserContext = new ParserContext(lexer, moduleMode, isEval, inheritedStrictMode,
                functionNesting, asyncFunctionNesting,
                generatorFunctionNesting,
                newTargetNesting,
                initialSuperPropertyAllowed,
                allowNewTargetInEval,
                evalPrivateNames);
        this.delegates = new ParserDelegates(parserContext);
    }

    private void collectModulePatternNames(Pattern pattern, java.util.function.Consumer<String> consumer) {
        if (pattern instanceof Identifier id) {
            consumer.accept(id.getName());
        } else if (pattern instanceof ObjectPattern objPat) {
            for (ObjectPatternProperty prop : objPat.getProperties()) {
                collectModulePatternNames(prop.getValue(), consumer);
            }
            if (objPat.getRestElement() != null) {
                collectModulePatternNames(objPat.getRestElement(), consumer);
            }
        } else if (pattern instanceof ArrayPattern arrayPat) {
            for (Pattern elem : arrayPat.getElements()) {
                if (elem != null) {
                    collectModulePatternNames(elem, consumer);
                }
            }
        } else if (pattern instanceof RestElement rest) {
            collectModulePatternNames(rest.getArgument(), consumer);
        } else if (pattern instanceof AssignmentPattern assign) {
            collectModulePatternNames(assign.getLeft(), consumer);
        }
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Token currentToken() {
        return parserContext.currentToken;
    }

    /**
     * Parse the entire program.
     */
    public Program parse() {
        List<Statement> body = new ArrayList<>();
        SourceLocation location = parserContext.getLocation();

        // Parse directives (like "use strict") at the beginning
        // Pass body so directive strings are also added as expression statements
        // (required for eval completion value per ES spec)
        boolean strict = parserContext.parseDirectives(body);
        parserContext.strictMode = strict || parserContext.moduleMode || parserContext.inheritedStrictMode;
        parserContext.lexer.setStrictMode(parserContext.strictMode);

        while (!parserContext.match(TokenType.EOF)) {
            Statement stmt = delegates.statements.parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        if (parserContext.moduleMode) {
            validateModuleEarlyErrors(body);
        } else {
            delegates.statements.validateProgramEarlyErrors(body);
        }

        return new Program(body, parserContext.moduleMode,
                strict || parserContext.moduleMode || parserContext.inheritedStrictMode, location);
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Expression parseExpression() {
        return delegates.expressions.parseExpression();
    }

    public void setClassFieldEval(boolean classFieldEval) {
        if (classFieldEval) {
            parserContext.inClassFieldInitializer = true;
        }
    }

    /**
     * Validate module-level early errors per ES2024 16.2.1.1.
     * In modules, top-level function declarations are lexical (not var-hoisted),
     * so duplicate function names are errors. Also checks for lex/var conflicts,
     * duplicate lexical declarations, and unresolvable export bindings.
     */
    private void validateModuleEarlyErrors(List<Statement> body) {
        // Collect all top-level bound names (lex + var + import bindings)
        Set<String> allBoundNames = new HashSet<>();
        Set<String> lexicalNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();

        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration varDecl) {
                boolean isVar = varDecl.getKind() == VariableKind.VAR;
                for (VariableDeclaration.VariableDeclarator declarator : varDecl.getDeclarations()) {
                    collectModulePatternNames(declarator.getId(), name -> {
                        allBoundNames.add(name);
                        if (isVar) {
                            if (lexicalNames.contains(name)) {
                                throw new JSSyntaxErrorException(
                                        "Identifier '" + name + "' has already been declared");
                            }
                            varNames.add(name);
                        } else {
                            if (varNames.contains(name) || !lexicalNames.add(name)) {
                                throw new JSSyntaxErrorException(
                                        "Identifier '" + name + "' has already been declared");
                            }
                        }
                    });
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl && funcDecl.getId() != null) {
                String name = funcDecl.getId().getName();
                allBoundNames.add(name);
                // In modules (always strict), all functions are lexical
                if (varNames.contains(name) || !lexicalNames.add(name)) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + name + "' has already been declared");
                }
            } else if (stmt instanceof ClassDeclaration classDecl && classDecl.getId() != null) {
                String name = classDecl.getId().getName();
                allBoundNames.add(name);
                if (varNames.contains(name) || !lexicalNames.add(name)) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + name + "' has already been declared");
                }
            } else if (stmt instanceof ExpressionStatement exprStmt
                    && exprStmt.getExpression() instanceof ClassExpression classExpr
                    && classExpr.getId() != null
                    && !"default".equals(classExpr.getId().getName())) {
                // export default class with explicit name
                String name = classExpr.getId().getName();
                allBoundNames.add(name);
                if (varNames.contains(name) || !lexicalNames.add(name)) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + name + "' has already been declared");
                }
            }
            // Note: import bindings are tracked via pendingExportBindings check only
        }

        // Also add import bindings to allBoundNames
        // Import local names are always lexical bindings
        allBoundNames.addAll(parserContext.moduleLexicalNames);
        allBoundNames.addAll(parserContext.moduleVarNames);

        // ES2024 16.2.1.1: For each name in ExportedBindings,
        // it must be in the module's BoundNames.
        for (String exportBinding : parserContext.pendingExportBindings) {
            if (!allBoundNames.contains(exportBinding)) {
                throw new JSSyntaxErrorException(
                        "Export '" + exportBinding + "' is not defined");
            }
        }
    }
}
