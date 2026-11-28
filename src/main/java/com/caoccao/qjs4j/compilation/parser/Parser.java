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
 * Recursive descent parser for JavaScript. Converts tokens into an Abstract Syntax Tree (AST).
 * <p>
 * This is the public facade that delegates to specialized parser classes: {@link ExpressionAssignmentParser},
 * {@link StatementParser}, {@link FunctionClassParser}, {@link PatternParser}, and {@link LiteralParser}. All shared
 * mutable state is held in {@link ParserContext}.
 */
public final class Parser {
    private final ParserContext parserContext;

    public Parser(Lexer lexer) {
        this(lexer, false);
    }

    public Parser(Lexer lexer, boolean moduleMode) {
        this(lexer, moduleMode, false);
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval) {
        this(lexer, moduleMode, isEval, false, 0, 0, 0, 0, false, false, false, Set.of());
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode) {
        this(lexer, moduleMode, isEval, inheritedStrictMode, 0, 0, 0, 0, false, false, false, Set.of());
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode,
            boolean initialSuperPropertyAllowed, boolean allowNewTargetInEval) {
        this(lexer, moduleMode, isEval, inheritedStrictMode, 0, 0, 0, 0, initialSuperPropertyAllowed,
                allowNewTargetInEval, false, Set.of());
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode,
            boolean initialSuperPropertyAllowed, boolean allowNewTargetInEval, boolean allowSuperCallInEval,
            Set<String> evalPrivateNames) {
        this(lexer, moduleMode, isEval, inheritedStrictMode, 0, 0, 0, 0, initialSuperPropertyAllowed,
                allowNewTargetInEval, allowSuperCallInEval, evalPrivateNames);
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode,
            boolean initialSuperPropertyAllowed, boolean allowNewTargetInEval, Set<String> evalPrivateNames) {
        this(lexer, moduleMode, isEval, inheritedStrictMode, 0, 0, 0, 0, initialSuperPropertyAllowed,
                allowNewTargetInEval, false, evalPrivateNames);
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode, int functionNesting,
            int asyncFunctionNesting, int generatorFunctionNesting, int newTargetNesting,
            boolean initialSuperPropertyAllowed, boolean allowNewTargetInEval) {
        this(lexer, moduleMode, isEval, inheritedStrictMode, functionNesting, asyncFunctionNesting,
                generatorFunctionNesting, newTargetNesting, initialSuperPropertyAllowed, allowNewTargetInEval, false,
                Set.of());
    }

    // Package-private: used by Compiler / LiteralParser when eval private names are in scope.
    Parser(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode, int functionNesting,
            int asyncFunctionNesting, int generatorFunctionNesting, int newTargetNesting,
            boolean initialSuperPropertyAllowed, boolean allowNewTargetInEval, boolean allowSuperCallInEval,
            Set<String> evalPrivateNames) {
        this.parserContext = new ParserContext(lexer, moduleMode, isEval, inheritedStrictMode, functionNesting,
                asyncFunctionNesting, generatorFunctionNesting, newTargetNesting, initialSuperPropertyAllowed,
                allowNewTargetInEval, evalPrivateNames);
        if (allowSuperCallInEval) {
            this.parserContext.inDerivedConstructor = true;
        }
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Token currentToken() {
        return parserContext.tokens.current();
    }

    /**
     * Parse the entire program.
     */
    public Program parse() {
        try {
            return parseProgram();
        } catch (JSSyntaxErrorException e) {
            throw e.withSourceLocation(parserContext.tokens.getLocation());
        }
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Expression parseExpression() {
        return ExpressionAssignmentParser.parseExpression(parserContext);
    }

    private Program parseProgram() {
        TokenCursor tokens = parserContext.tokens;
        List<Statement> body = new ArrayList<>();
        SourceLocation location = tokens.getLocation();

        // Parse directives (like "use strict") at the beginning
        // Pass body so directive strings are also added as expression statements
        // (required for eval completion value per ES spec)
        boolean strict = parserContext.parseDirectives(body);
        parserContext.setStrictMode(strict || parserContext.moduleMode || parserContext.inheritedStrictMode);

        while (!tokens.match(TokenType.EOF)) {
            Statement stmt = StatementParser.parseStatement(parserContext);
            if (stmt != null) {
                body.add(stmt);
            }
        }

        if (parserContext.moduleMode) {
            validateModuleEarlyErrors(body);
        } else {
            parserContext.declarations.validateProgramEarlyErrors(body);
        }

        return new Program(body, parserContext.moduleMode,
                strict || parserContext.moduleMode || parserContext.inheritedStrictMode, location);
    }

    public void setClassFieldEval(boolean classFieldEval) {
        if (classFieldEval) {
            parserContext.inClassFieldInitializer = true;
        }
    }

    /**
     * Validate module-level early errors per ES2024 16.2.1.1. In modules, top-level function declarations are lexical
     * (not var-hoisted), so duplicate function names are errors. Also checks for lex/var conflicts, duplicate lexical
     * declarations, and unresolvable export bindings.
     */
    private void validateModuleEarlyErrors(List<Statement> body) {
        Set<String> lexicalNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration declaration) {
                for (VariableDeclarator declarator : declaration.getDeclarations()) {
                    for (String name : declarator.getId().getBoundNames()) {
                        if (declaration.getKind() == VariableKind.VAR) {
                            if (lexicalNames.contains(name)) {
                                throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                            }
                            varNames.add(name);
                        } else {
                            DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, name);
                        }
                    }
                }
            } else if (stmt instanceof FunctionDeclaration declaration && declaration.getId() != null) {
                DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, declaration.getId().getName());
            } else if (stmt instanceof ClassDeclaration declaration && declaration.getId() != null) {
                DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, declaration.getId().getName());
            } else if (stmt instanceof ExpressionStatement expression
                    && expression.getExpression() instanceof ClassExpression cls && cls.getId() != null
                    && !"default".equals(cls.getId().getName())) {
                DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, cls.getId().getName());
            }
        }

        // ES2024 16.2.1.1: For each name in ExportedBindings,
        // it must be in the module's BoundNames.
        for (String exportBinding : parserContext.pendingExportBindings) {
            if (!parserContext.moduleLexicalNames.contains(exportBinding)
                    && !parserContext.moduleVarNames.contains(exportBinding) && !lexicalNames.contains(exportBinding)
                    && !varNames.contains(exportBinding)) {
                throw new JSSyntaxErrorException("Export '" + exportBinding + "' is not defined in module");
            }
        }
    }
}
