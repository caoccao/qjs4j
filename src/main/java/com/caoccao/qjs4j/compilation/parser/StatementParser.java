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
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.unicode.UnicodeStringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delegate parser responsible for parsing JavaScript statements.
 * Handles control flow (if, while, for, switch, try), declarations (var, let, const, using),
 * and statement-level constructs (block, labeled, return, break, continue, throw).
 */
record StatementParser(ParserContext parserContext, ParserDelegates delegates) {
    private void collectPatternBoundNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier identifier) {
            names.add(identifier.name());
            return;
        }
        if (pattern instanceof ArrayPattern arrayPattern) {
            for (Pattern element : arrayPattern.elements()) {
                if (element != null) {
                    collectPatternBoundNames(element, names);
                }
            }
            return;
        }
        if (pattern instanceof ObjectPattern objectPattern) {
            for (ObjectPattern.Property property : objectPattern.properties()) {
                collectPatternBoundNames(property.value(), names);
            }
            if (objectPattern.restElement() != null) {
                collectPatternBoundNames(objectPattern.restElement().argument(), names);
            }
            return;
        }
        if (pattern instanceof RestElement restElement) {
            collectPatternBoundNames(restElement.argument(), names);
        }
    }

    private void collectVarDeclaredNames(Statement statement, Set<String> varNames) {
        if (statement instanceof VariableDeclaration variableDeclaration) {
            if (variableDeclaration.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    collectPatternBoundNames(declarator.id(), varNames);
                }
            }
            return;
        }
        if (statement instanceof BlockStatement blockStatement) {
            for (Statement blockItem : blockStatement.body()) {
                collectVarDeclaredNames(blockItem, varNames);
            }
            return;
        }
        if (statement instanceof IfStatement ifStatement) {
            collectVarDeclaredNames(ifStatement.consequent(), varNames);
            if (ifStatement.alternate() != null) {
                collectVarDeclaredNames(ifStatement.alternate(), varNames);
            }
            return;
        }
        if (statement instanceof ForStatement forStatement) {
            if (forStatement.init() instanceof Statement initStatement) {
                collectVarDeclaredNames(initStatement, varNames);
            }
            collectVarDeclaredNames(forStatement.body(), varNames);
            return;
        }
        if (statement instanceof ForInStatement forInStatement) {
            if (forInStatement.left() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    collectPatternBoundNames(declarator.id(), varNames);
                }
            }
            collectVarDeclaredNames(forInStatement.body(), varNames);
            return;
        }
        if (statement instanceof ForOfStatement forOfStatement) {
            if (forOfStatement.left() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    collectPatternBoundNames(declarator.id(), varNames);
                }
            }
            collectVarDeclaredNames(forOfStatement.body(), varNames);
            return;
        }
        if (statement instanceof WhileStatement whileStatement) {
            collectVarDeclaredNames(whileStatement.body(), varNames);
            return;
        }
        if (statement instanceof DoWhileStatement doWhileStatement) {
            collectVarDeclaredNames(doWhileStatement.body(), varNames);
            return;
        }
        if (statement instanceof SwitchStatement switchStatement) {
            for (SwitchStatement.SwitchCase switchCase : switchStatement.cases()) {
                for (Statement consequentStatement : switchCase.consequent()) {
                    collectVarDeclaredNames(consequentStatement, varNames);
                }
            }
            return;
        }
        if (statement instanceof TryStatement tryStatement) {
            collectVarDeclaredNames(tryStatement.block(), varNames);
            if (tryStatement.handler() != null) {
                collectVarDeclaredNames(tryStatement.handler().body(), varNames);
            }
            if (tryStatement.finalizer() != null) {
                collectVarDeclaredNames(tryStatement.finalizer(), varNames);
            }
        }
    }

    private boolean containsCoverInitializedName(ObjectExpression objectExpression) {
        for (ObjectExpression.Property property : objectExpression.properties()) {
            if (!property.shorthand()) {
                continue;
            }
            if (property.value() instanceof AssignmentExpression assignmentExpression
                    && assignmentExpression.operator() == AssignmentExpression.AssignmentOperator.ASSIGN) {
                return true;
            }
        }
        return false;
    }

    private boolean isWithKeyword() {
        return parserContext.currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.WITH.equals(parserContext.currentToken.value());
    }

    private void expectContextualKeyword(TokenType type, String keyword) {
        if (!parserContext.match(type) || parserContext.currentToken.escaped()) {
            throw new JSSyntaxErrorException(
                    "Expected '" + keyword + "' but got '" + parserContext.currentToken.value() +
                            "' at line " + parserContext.currentToken.line() +
                            ", column " + parserContext.currentToken.column());
        }
        parserContext.advance();
    }

    private void addModuleExportedName(String name) {
        if (parserContext.moduleMode && !parserContext.moduleExportedNames.add(name)) {
            throw new JSSyntaxErrorException("Duplicate export of '" + name + "'");
        }
    }

    private void addModuleLexicalName(String name) {
        if (parserContext.moduleMode && parserContext.functionNesting == 0) {
            if (parserContext.moduleVarNames.contains(name) || !parserContext.moduleLexicalNames.add(name)) {
                throw new JSSyntaxErrorException(
                        "Identifier '" + name + "' has already been declared");
            }
        }
    }

    private void parseWithClause() {
        if (parserContext.currentToken.type() == TokenType.IDENTIFIER
                && "with".equals(parserContext.currentToken.value())) {
            parserContext.advance(); // consume 'with'
            parserContext.expect(TokenType.LBRACE);
            Set<String> attributeKeys = new HashSet<>();
            while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                // Parse key: either identifier or string
                String key;
                if (parserContext.match(TokenType.STRING)) {
                    key = parserContext.currentToken.value();
                    parserContext.advance();
                } else if (parserContext.match(TokenType.IDENTIFIER)) {
                    key = parserContext.currentToken.value();
                    parserContext.advance();
                } else {
                    throw new JSSyntaxErrorException("identifier expected");
                }
                if (!attributeKeys.add(key)) {
                    throw new JSSyntaxErrorException("Duplicate attribute key '" + key + "'");
                }
                parserContext.expect(TokenType.COLON);
                parserContext.expect(TokenType.STRING);
                if (!parserContext.match(TokenType.COMMA)) {
                    break;
                }
                parserContext.advance();
            }
            parserContext.expect(TokenType.RBRACE);
        }
    }

    Statement parseAsyncDeclaration() {
        if (parserContext.nextToken.type() == TokenType.FUNCTION) {
            SourceLocation asyncLocation = parserContext.getLocation();
            parserContext.advance(); // consume async
            return delegates.functions.parseFunctionDeclaration(true, false, asyncLocation);
        } else {
            // Otherwise parse as an expression statement (e.g. async () => 1).
            return parseExpressionStatement();
        }
    }

    BlockStatement parseBlockStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        validateBlockEarlyErrors(body);
        parserContext.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    Statement parseBreakStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.BREAK);
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (parserContext.match(TokenType.IDENTIFIER) && !parserContext.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return new BreakStatement(label, location);
    }

    Statement parseContinueStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.CONTINUE);
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (parserContext.match(TokenType.IDENTIFIER) && !parserContext.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return new ContinueStatement(label, location);
    }

    Statement parseDoWhileStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.DO);
        Statement body = parseStatement();
        if (body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in do-while statement position");
        }
        parserContext.expect(TokenType.WHILE);
        parserContext.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        if (parserContext.match(TokenType.SEMICOLON)) {
            parserContext.advance();
        }
        return new DoWhileStatement(body, test, location);
    }

    Statement parseExportDeclaration() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.EXPORT);

        if (parserContext.match(TokenType.DEFAULT)) {
            if (parserContext.currentToken.escaped()) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            addModuleExportedName("default");
            parserContext.advance();

            if (parserContext.match(TokenType.CLASS)) {
                // export default class { ... } — infer name "default" per spec
                ClassExpression classExpr = delegates.functions.parseClassExpression();
                String className = classExpr.id() != null ? classExpr.id().name() : null;
                if (classExpr.id() == null) {
                    classExpr = new ClassExpression(
                            new Identifier(JSKeyword.DEFAULT, classExpr.location()),
                            classExpr.superClass(),
                            classExpr.body(),
                            classExpr.location());
                }
                // Named class in export default creates a lexical binding
                if (className != null) {
                    addModuleLexicalName(className);
                }
                return new ExpressionStatement(classExpr, location);
            } else if (parserContext.match(TokenType.FUNCTION)) {
                Statement funcDecl = delegates.functions.parseExportDefaultFunctionDeclaration(false);
                if (parserContext.match(TokenType.LPAREN)) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                return funcDecl;
            } else if (parserContext.match(TokenType.ASYNC) && parserContext.peek() != null
                    && parserContext.peek().type() == TokenType.FUNCTION) {
                Statement asyncDecl = delegates.functions.parseExportDefaultAsyncFunctionDeclaration();
                if (parserContext.match(TokenType.LPAREN)) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                return asyncDecl;
            } else {
                // export default <expression>;
                Expression expr = delegates.expressions.parseAssignmentExpression();
                parserContext.consumeSemicolon();
                return new ExpressionStatement(expr, location);
            }
        }

        // export * from 'module';
        // export * as name from 'module';
        // export * as "string" from 'module';
        if (parserContext.match(TokenType.MUL)) {
            parserContext.advance();
            if (parserContext.match(TokenType.AS)) {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
                }
                parserContext.advance();
                // export name can be identifier or string literal
                String exportedName = parseModuleExportNameAndReturn();
                addModuleExportedName(exportedName);
            }
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return new BlockStatement(List.of(), location);
        }

        // export var/let/const/function/class ...
        if (parserContext.match(TokenType.VAR) || parserContext.match(TokenType.LET) || parserContext.match(TokenType.CONST)) {
            boolean isVar = parserContext.match(TokenType.VAR);
            Statement decl = parseVariableDeclaration();
            // Track exported names from declaration
            collectDeclaredNames(decl, isVar);
            return decl;
        }
        if (parserContext.match(TokenType.FUNCTION)) {
            Statement decl = delegates.functions.parseFunctionDeclaration(false, false);
            collectDeclaredNames(decl, false);
            return decl;
        }
        if (parserContext.match(TokenType.ASYNC) && parserContext.peek() != null
                && parserContext.peek().type() == TokenType.FUNCTION) {
            Statement decl = parseAsyncDeclaration();
            collectDeclaredNames(decl, false);
            return decl;
        }
        if (parserContext.match(TokenType.CLASS)) {
            Statement decl = delegates.functions.parseClassDeclaration();
            collectDeclaredNames(decl, false);
            return decl;
        }
        if (parserContext.match(TokenType.LBRACE)) {
            parseExportNamedSpecifiers();
            parserContext.consumeSemicolon();
            return new BlockStatement(List.of(), location);
        }

        throw new JSSyntaxErrorException("Unexpected export syntax");
    }

    /**
     * Collect declared names from an exported declaration and register them as exported names.
     */
    private void collectDeclaredNames(Statement decl, boolean isVar) {
        if (!parserContext.moduleMode) {
            return;
        }
        if (decl instanceof VariableDeclaration varDecl) {
            for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
                collectPatternNames(declarator.id(), name -> {
                    addModuleExportedName(name);
                });
            }
        } else if (decl instanceof FunctionDeclaration funcDecl) {
            if (funcDecl.id() != null) {
                addModuleExportedName(funcDecl.id().name());
            }
        } else if (decl instanceof ClassDeclaration classDecl) {
            if (classDecl.id() != null) {
                addModuleExportedName(classDecl.id().name());
            }
        }
    }

    /**
     * Collect binding names from a pattern (identifier, array, object destructuring).
     */
    private void collectPatternNames(Pattern pattern, java.util.function.Consumer<String> consumer) {
        if (pattern instanceof Identifier id) {
            consumer.accept(id.name());
        } else if (pattern instanceof ObjectPattern objPat) {
            for (ObjectPattern.Property prop : objPat.properties()) {
                collectPatternNames(prop.value(), consumer);
            }
            if (objPat.restElement() != null) {
                collectPatternNames(objPat.restElement(), consumer);
            }
        } else if (pattern instanceof ArrayPattern arrayPat) {
            for (Pattern elem : arrayPat.elements()) {
                if (elem != null) {
                    collectPatternNames(elem, consumer);
                }
            }
        } else if (pattern instanceof RestElement rest) {
            collectPatternNames(rest.argument(), consumer);
        } else if (pattern instanceof AssignmentPattern assign) {
            collectPatternNames(assign.left(), consumer);
        }
    }

    /**
     * Parse a module export name: either an identifier name or a string literal.
     * ModuleExportName :: IdentifierName | StringLiteral
     * Returns the parsed name for tracking purposes.
     */
    private String parseModuleExportNameAndReturn() {
        if (parserContext.match(TokenType.STRING)) {
            String name = parserContext.currentToken.value();
            // ES2024: ModuleExportName string must not contain unpaired surrogates
            if (UnicodeStringUtils.hasUnpairedSurrogate(name)) {
                throw new JSSyntaxErrorException(
                        "Invalid module export name: unpaired surrogate");
            }
            parserContext.advance();
            return name;
        } else {
            // Any IdentifierName including keywords is allowed as export name
            String name = parseImportIdentifierName();
            return name;
        }
    }

    /**
     * Parse a module export name without returning value (backward compat).
     */
    private void parseModuleExportName() {
        parseModuleExportNameAndReturn();
    }

    /**
     * Parse export { specifiers } and optional 'from' clause.
     * Handles string export/import names per ES2024 ModuleExportName.
     */
    private void parseExportNamedSpecifiers() {
        parserContext.advance(); // consume '{'
        boolean hasStringLocalName = false;
        List<String[]> specifiers = new ArrayList<>(); // [localName, exportedName]
        while (!parserContext.match(TokenType.RBRACE)) {
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
                continue;
            }
            // The local name in export { local as exported }
            boolean localIsString = parserContext.match(TokenType.STRING);
            if (localIsString) {
                hasStringLocalName = true;
            }
            String localName = parseModuleExportNameAndReturn();
            String exportedName = localName;
            if (parserContext.match(TokenType.IDENTIFIER)
                    && JSKeyword.AS.equals(parserContext.currentToken.value())) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            if (parserContext.match(TokenType.AS)) {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
                }
                parserContext.advance();
                // The exported name can also be identifier or string
                exportedName = parseModuleExportNameAndReturn();
            }
            specifiers.add(new String[]{localName, exportedName});
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
            }
        }
        parserContext.expect(TokenType.RBRACE);
        boolean hasFrom = false;
        if (parserContext.match(TokenType.FROM)) {
            if (parserContext.currentToken.escaped()) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            hasFrom = true;
            parserContext.advance();
            parserContext.expect(TokenType.STRING);
            parseWithClause();
        } else if (hasStringLocalName) {
            // String local names require a 'from' clause per spec
            throw new JSSyntaxErrorException(
                    "Expected 'from' but got '" + parserContext.currentToken.value() + "'");
        }
        // Register exported names and track local bindings
        for (String[] spec : specifiers) {
            addModuleExportedName(spec[1]);
            if (!hasFrom) {
                // Without 'from', the local names must be bound locally
                parserContext.pendingExportBindings.add(spec[0]);
            }
        }
    }

    Statement parseExpressionStatement() {
        SourceLocation location = parserContext.getLocation();
        Expression expression = delegates.expressions.parseExpression();
        if (expression instanceof ObjectExpression objectExpression
                && containsCoverInitializedName(objectExpression)) {
            throw new JSSyntaxErrorException("Invalid shorthand property initializer");
        }
        parserContext.consumeSemicolon();
        return new ExpressionStatement(expression, location);
    }

    Statement parseForStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.FOR);

        // Check for 'await' keyword (for await...of)
        boolean isAwait = false;
        if (parserContext.match(TokenType.AWAIT)) {
            if (!parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            isAwait = true;
            parserContext.advance();
        }

        parserContext.expect(TokenType.LPAREN);

        // Check if this is a for-of or for-in loop
        // We need to peek ahead to see if there's 'of' or 'in' after the variable declaration
        boolean isForOf = false;
        boolean isForIn = false;
        Statement parsedDecl = null;

        // Try to parse as variable declaration (without consuming semicolon,
        // since we need to check for 'of' or 'in' first)
        if (parserContext.match(TokenType.VAR) || parserContext.match(TokenType.LET) || parserContext.match(TokenType.CONST)
                || parserContext.isUsingDeclarationStart() || parserContext.isAwaitUsingDeclarationStart()) {
            // Annex B: suppress 'in' as binary operator only for 'var' in non-strict mode
            // (allows for-in initializers: for (var a = expr in obj))
            boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
            boolean isVar = parserContext.match(TokenType.VAR);
            if (isVar && !parserContext.strictMode) {
                parserContext.inOperatorAllowed = false;
            }
            if (parserContext.isAwaitUsingDeclarationStart()) {
                SourceLocation declLocation = parserContext.getLocation();
                parserContext.expect(TokenType.AWAIT);
                parserContext.advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.AWAIT_USING, declLocation, false);
            } else if (parserContext.isUsingDeclarationStart()) {
                SourceLocation declLocation = parserContext.getLocation();
                parserContext.advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.USING, declLocation, false);
            } else {
                SourceLocation declLocation = parserContext.getLocation();
                VariableKind kind = VariableKind.fromKeyword(parserContext.currentToken.value());
                parserContext.advance();
                parsedDecl = parseVariableDeclarationBody(kind, declLocation, false);
            }
            parserContext.inOperatorAllowed = savedInOperatorAllowed;
            // Check if next token is 'of' or 'in'
            if (parserContext.match(TokenType.OF)) {
                isForOf = true;
            } else if (parserContext.match(TokenType.IN)) {
                isForIn = true;
            }
        }

        if (isForOf) {
            // This is a for-of loop: for (let x of iterable)
            parserContext.expect(TokenType.OF);
            Expression iterable = delegates.expressions.parseExpression();
            parserContext.expect(TokenType.RPAREN);
            Statement body = parseStatement();

            // parsedDecl should be a VariableDeclaration
            if (!(parsedDecl instanceof VariableDeclaration varDecl)) {
                throw new RuntimeException("Expected VariableDeclaration in for-of loop");
            }

            return new ForOfStatement(varDecl, iterable, body, isAwait, location);
        }

        if (isForIn) {
            // This is a for-in loop: for (var x in obj)
            parserContext.expect(TokenType.IN);
            Expression object = delegates.expressions.parseExpression();
            parserContext.expect(TokenType.RPAREN);
            Statement body = parseStatement();

            // parsedDecl should be a VariableDeclaration
            if (!(parsedDecl instanceof VariableDeclaration varDecl)) {
                throw new RuntimeException("Expected VariableDeclaration in for-in loop");
            }

            return new ForInStatement(varDecl, object, body, location);
        }

        if (isAwait) {
            throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
        }

        // Not a for-of loop, parse as traditional for loop
        // Reset if we parsed a var declaration but it's not for-of
        Statement init = null;
        if (parsedDecl != null) {
            init = parsedDecl;
            parserContext.expect(TokenType.SEMICOLON); // consume ; after init declaration
        } else if (!parserContext.match(TokenType.SEMICOLON)) {
            if (parserContext.match(TokenType.VAR) || parserContext.match(TokenType.LET) || parserContext.match(TokenType.CONST)
                    || parserContext.isUsingDeclarationStart() || parserContext.isAwaitUsingDeclarationStart()) {
                if (parserContext.isAwaitUsingDeclarationStart()) {
                    init = parseUsingDeclaration(true);
                } else if (parserContext.isUsingDeclarationStart()) {
                    init = parseUsingDeclaration(false);
                } else {
                    init = parseVariableDeclaration();
                }
            } else {
                // Parse expression -- could be for-in/for-of left side or traditional for init.
                // Parse as assignment expression first, then check for 'in' or 'of'.
                // Suppress 'in' as binary operator per ES spec [~In] grammar parameter.
                boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                parserContext.inOperatorAllowed = false;
                Expression expr = delegates.expressions.parseAssignmentExpression();
                parserContext.inOperatorAllowed = savedInOperatorAllowed;
                if (parserContext.match(TokenType.IN)) {
                    // for (expr in obj) -- expression-based for-in
                    // Validate: left side must be a valid LeftHandSideExpression
                    if (!parserContext.isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    parserContext.advance(); // consume 'in'
                    Expression object = delegates.expressions.parseExpression();
                    parserContext.expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    return new ForInStatement(expr, object, body, location);
                } else if (parserContext.match(TokenType.OF)) {
                    // for (expr of iterable) -- expression-based for-of
                    if (!parserContext.isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    parserContext.advance(); // consume 'of'
                    Expression iterable = delegates.expressions.parseExpression();
                    parserContext.expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    return new ForOfStatement(expr, iterable, body, isAwait, location);
                }
                // Traditional for loop init -- handle comma expressions
                while (parserContext.match(TokenType.COMMA)) {
                    parserContext.advance();
                    Expression right = delegates.expressions.parseAssignmentExpression();
                    expr = new SequenceExpression(List.of(expr, right), expr.getLocation());
                }
                init = new ExpressionStatement(expr, expr.getLocation());
                parserContext.consumeSemicolon();
            }
        } else {
            parserContext.advance(); // consume semicolon
        }

        // Test
        Expression test = null;
        if (!parserContext.match(TokenType.SEMICOLON)) {
            test = delegates.expressions.parseExpression();
        }
        parserContext.expect(TokenType.SEMICOLON);

        // Update
        Expression update = null;
        if (!parserContext.match(TokenType.RPAREN)) {
            update = delegates.expressions.parseExpression();
        }
        parserContext.expect(TokenType.RPAREN);

        Statement body = parseStatement();
        if (body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in for statement position");
        }

        return new ForStatement(init, test, update, body, location);
    }

    Statement parseIfStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.IF);
        parserContext.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);

        Statement consequent = parseStatement();
        Statement alternate = null;

        if (parserContext.match(TokenType.ELSE)) {
            parserContext.advance();
            alternate = parseStatement();
        }

        return new IfStatement(test, consequent, alternate, location);
    }

    Statement parseImportDeclarationStatement() {
        SourceLocation location = parserContext.getLocation();
        if (!parserContext.moduleMode || parserContext.functionNesting != 0) {
            throw new JSSyntaxErrorException("Cannot use import statement outside a module");
        }

        parserContext.expect(TokenType.IMPORT);

        // Side-effect-only import: import 'module';
        if (parserContext.match(TokenType.STRING)) {
            parserContext.advance(); // module specifier
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // import defer * as ns from 'module';
        if (parserContext.match(TokenType.IDENTIFIER)
                && "defer".equals(parserContext.currentToken.value())
                && parserContext.peek() != null
                && parserContext.peek().type() == TokenType.MUL) {
            parserContext.advance(); // consume 'defer'
            parserContext.advance(); // consume '*'
            expectContextualKeyword(TokenType.AS, "as");
            Identifier nsBinding = parserContext.parseIdentifier();
            addModuleLexicalName(nsBinding.name());
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // Namespace import: import * as ns from 'module';
        if (parserContext.match(TokenType.MUL)) {
            parserContext.advance();
            expectContextualKeyword(TokenType.AS, "as");
            Identifier nsBinding = parserContext.parseIdentifier();
            addModuleLexicalName(nsBinding.name());
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // Named imports: import { x, y as z } from 'module';
        if (parserContext.match(TokenType.LBRACE)) {
            parseNamedImportSpecifiers();
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // Default import, possibly with named/namespace: import foo from 'module';
        if (parserContext.match(TokenType.IDENTIFIER)) {
            Identifier defaultBinding = parserContext.parseIdentifier();
            addModuleLexicalName(defaultBinding.name());
            Set<String> boundNames = new HashSet<>();
            boundNames.add(defaultBinding.name());
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
                if (parserContext.match(TokenType.MUL)) {
                    parserContext.advance();
                    expectContextualKeyword(TokenType.AS, "as");
                    Identifier nsBinding = parserContext.parseIdentifier();
                    addModuleLexicalName(nsBinding.name());
                    if (boundNames.contains(nsBinding.name())) {
                        throw new JSSyntaxErrorException("duplicate import binding");
                    }
                } else if (parserContext.match(TokenType.LBRACE)) {
                    parseNamedImportSpecifiers(boundNames);
                } else {
                    throw new JSSyntaxErrorException("Unsupported import declaration");
                }
            }
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        throw new JSSyntaxErrorException("Unsupported import declaration");
    }

    private String parseImportIdentifierName() {
        TokenType type = parserContext.currentToken.type();
        if (type == TokenType.STRING) {
            String name = parserContext.currentToken.value();
            // ES2024: ModuleExportName string must not contain unpaired surrogates
            if (UnicodeStringUtils.hasUnpairedSurrogate(name)) {
                throw new JSSyntaxErrorException(
                        "Invalid module export name: unpaired surrogate");
            }
            parserContext.advance();
            return name;
        }
        // Any IdentifierName including reserved keywords is allowed as
        // import/export specifier names (ES2024 ModuleExportName).
        if (isIdentifierNameToken(type)) {
            String name = parserContext.currentToken.value();
            parserContext.advance();
            return name;
        }
        throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
    }

    private static boolean isIdentifierNameToken(TokenType type) {
        return switch (type) {
            case IDENTIFIER, AS, ASYNC, AWAIT, BREAK, CASE, CATCH, CLASS, CONST,
                 CONTINUE, DEFAULT, DELETE, DO, ELSE, EXPORT, EXTENDS, FALSE,
                 FINALLY, FOR, FROM, FUNCTION, IF, IMPORT, IN, INSTANCEOF, LET,
                 NEW, NULL, OF, RETURN, SUPER, SWITCH, THIS, THROW, TRUE, TRY,
                 TYPEOF, VAR, VOID, WHILE, YIELD -> true;
            default -> false;
        };
    }

    /**
     * Parse a labeled statement: label: statement
     * Following QuickJS js_parse_statement_or_decl label handling.
     * In non-strict mode, labeled function declarations are allowed (Annex B).
     */
    Statement parseLabeledStatement() {
        SourceLocation location = parserContext.getLocation();
        Identifier label = parserContext.parseIdentifier();
        // ES2024 14.13.1: It is a Syntax Error if any source text is matched by
        // this production that also matches the LabelIdentifier of an enclosing LabelledStatement.
        for (Set<String> labels : parserContext.labelStack) {
            if (labels.contains(label.name())) {
                throw new JSSyntaxErrorException("Label '" + label.name() + "' has already been declared");
            }
        }
        parserContext.expect(TokenType.COLON);
        Set<String> currentLabels = new HashSet<>();
        currentLabels.add(label.name());
        parserContext.labelStack.push(currentLabels);
        try {
            Statement body = parseStatement();
            return new LabeledStatement(label, body, location);
        } finally {
            parserContext.labelStack.pop();
        }
    }

    private void parseNamedImportSpecifiers() {
        parseNamedImportSpecifiers(new HashSet<>());
    }

    private void parseNamedImportSpecifiers(Set<String> boundNames) {
        parserContext.expect(TokenType.LBRACE);
        if (!parserContext.match(TokenType.RBRACE)) {
            while (true) {
                String importName = parseImportIdentifierName();
                String localName;
                if (parserContext.match(TokenType.AS) && !parserContext.currentToken.escaped()) {
                    parserContext.advance();
                    localName = parserContext.parseIdentifier().name();
                } else {
                    localName = importName;
                }
                // In strict mode (modules are always strict), eval/arguments are forbidden
                if (parserContext.strictMode
                        && (JSKeyword.EVAL.equals(localName) || JSKeyword.ARGUMENTS.equals(localName))) {
                    throw new JSSyntaxErrorException(
                            "Unexpected eval or arguments in strict mode");
                }
                addModuleLexicalName(localName);
                if (!boundNames.add(localName)) {
                    throw new JSSyntaxErrorException("duplicate import binding");
                }
                if (!parserContext.match(TokenType.COMMA)) {
                    break;
                }
                parserContext.advance();
                if (parserContext.match(TokenType.RBRACE)) {
                    break;
                }
            }
        }
        parserContext.expect(TokenType.RBRACE);
    }

    Statement parseReturnStatement() {
        SourceLocation location = parserContext.getLocation();
        // Return is only valid inside function bodies.
        if (parserContext.functionNesting == 0) {
            throw new JSSyntaxErrorException("return not in a function");
        }
        parserContext.expect(TokenType.RETURN);

        Expression argument = null;
        if (!parserContext.hasNewlineBefore()
                && !parserContext.match(TokenType.SEMICOLON)
                && !parserContext.match(TokenType.RBRACE)
                && !parserContext.match(TokenType.EOF)) {
            argument = delegates.expressions.parseExpression();
        }

        parserContext.consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    Statement parseStatement() {
        parserContext.statementNesting++;
        try {
            if (parserContext.isAwaitUsingDeclarationStart()) {
                return parseUsingDeclaration(true);
            }
            if (parserContext.isUsingDeclarationStart()) {
                return parseUsingDeclaration(false);
            }
            if (isWithKeyword()) {
                return parseWithStatement();
            }
            // Check for labeled statement: identifier followed by ':'
            // Following QuickJS is_label() check
            if (parserContext.currentToken.type() == TokenType.IDENTIFIER && parserContext.peek() != null && parserContext.peek().type() == TokenType.COLON) {
                return parseLabeledStatement();
            }
            return switch (parserContext.currentToken.type()) {
                case IF -> parseIfStatement();
                case DO -> parseDoWhileStatement();
                case WHILE -> parseWhileStatement();
                case FOR -> parseForStatement();
                case RETURN -> parseReturnStatement();
                case BREAK -> parseBreakStatement();
                case CONTINUE -> parseContinueStatement();
                case THROW -> parseThrowStatement();
                case TRY -> parseTryStatement();
                case SWITCH -> parseSwitchStatement();
                case LBRACE -> parseBlockStatement();
                case VAR, LET, CONST -> parseVariableDeclaration();
                case ASYNC -> // Async function declaration: async function f() {}
                        parseAsyncDeclaration();
                case FUNCTION -> // Function declarations are treated as statements in JavaScript
                        delegates.functions.parseFunctionDeclaration(false, false);
                case CLASS -> // Class declarations are treated as statements in JavaScript
                        delegates.functions.parseClassDeclaration();
                case EXPORT -> {
                    if (!parserContext.moduleMode || parserContext.statementNesting > 1) {
                        throw new JSSyntaxErrorException("Unexpected token 'export'");
                    }
                    yield parseExportDeclaration();
                }
                case IMPORT -> {
                    Token nextToken = parserContext.peek();
                    boolean isDynamicImportExpression = nextToken != null
                            && (nextToken.type() == TokenType.LPAREN || nextToken.type() == TokenType.DOT);
                    if (!isDynamicImportExpression) {
                        if (parserContext.moduleMode && parserContext.functionNesting == 0
                                && parserContext.statementNesting <= 1) {
                            yield parseImportDeclarationStatement();
                        }
                        throw new JSSyntaxErrorException("Cannot use import statement outside a module");
                    }
                    yield parseExpressionStatement();
                }
                case SEMICOLON -> {
                    parserContext.advance(); // consume semicolon
                    yield null; // empty statement
                }
                default -> parseExpressionStatement();
            };
        } finally {
            parserContext.statementNesting--;
        }
    }

    Statement parseSwitchStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.SWITCH);
        parserContext.expect(TokenType.LPAREN);
        Expression discriminant = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        parserContext.expect(TokenType.LBRACE);

        List<SwitchStatement.SwitchCase> cases = new ArrayList<>();

        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            if (parserContext.match(TokenType.CASE)) {
                parserContext.advance();
                Expression test = delegates.expressions.parseExpression();
                parserContext.expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!parserContext.match(TokenType.CASE) && !parserContext.match(TokenType.DEFAULT) &&
                        !parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(test, consequent));
            } else if (parserContext.match(TokenType.DEFAULT)) {
                parserContext.advance();
                parserContext.expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!parserContext.match(TokenType.CASE) && !parserContext.match(TokenType.DEFAULT) &&
                        !parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(null, consequent));
            } else {
                parserContext.advance(); // skip unexpected token
            }
        }

        parserContext.expect(TokenType.RBRACE);
        return new SwitchStatement(discriminant, cases, location);
    }

    Statement parseThrowStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.THROW);
        if (parserContext.hasNewlineBefore()) {
            throw new JSSyntaxErrorException("Illegal newline after throw");
        }
        Expression argument = delegates.expressions.parseExpression();
        parserContext.consumeSemicolon();
        return new ThrowStatement(argument, location);
    }

    Statement parseTryStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.TRY);
        BlockStatement block = parseBlockStatement();

        TryStatement.CatchClause handler = null;
        if (parserContext.match(TokenType.CATCH)) {
            parserContext.advance();
            Pattern param = null;
            if (parserContext.match(TokenType.LPAREN)) {
                parserContext.advance();
                param = delegates.patterns.parsePattern();
                parserContext.expect(TokenType.RPAREN);
            }
            BlockStatement catchBody = parseBlockStatement();
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (parserContext.match(TokenType.FINALLY)) {
            parserContext.advance();
            finalizer = parseBlockStatement();
        }

        return new TryStatement(block, handler, finalizer, location);
    }

    Statement parseUsingDeclaration(boolean isAwaitUsing) {
        SourceLocation location = parserContext.getLocation();
        VariableKind kind;
        if (isAwaitUsing) {
            if (!parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            parserContext.expect(TokenType.AWAIT);
            if (!parserContext.isUsingIdentifierToken(parserContext.currentToken)) {
                throw new RuntimeException("Expected using declaration after await");
            }
            parserContext.advance();
            kind = VariableKind.AWAIT_USING;
        } else {
            if (!parserContext.isUsingIdentifierToken(parserContext.currentToken)) {
                throw new RuntimeException("Expected using declaration");
            }
            parserContext.advance();
            kind = VariableKind.USING;
        }
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclaration() {
        SourceLocation location = parserContext.getLocation();
        VariableKind kind = VariableKind.fromKeyword(parserContext.currentToken.value()); // VAR, LET, or CONST
        parserContext.advance();
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclarationBody(VariableKind kind, SourceLocation location) {
        return parseVariableDeclarationBody(kind, location, true);
    }

    VariableDeclaration parseVariableDeclarationBody(VariableKind kind, SourceLocation location, boolean consumeSemi) {
        List<VariableDeclaration.VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
            }

            Pattern id = delegates.patterns.parsePattern();
            Expression init = null;

            if (parserContext.match(TokenType.ASSIGN)) {
                parserContext.advance();
                init = delegates.expressions.parseAssignmentExpression();
            }

            declarations.add(new VariableDeclaration.VariableDeclarator(id, init));
        } while (parserContext.match(TokenType.COMMA));

        if (consumeSemi) {
            parserContext.consumeSemicolon();
        }
        return new VariableDeclaration(declarations, kind, location);
    }

    Statement parseWhileStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.WHILE);
        parserContext.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        Statement body = parseStatement();
        if (body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in while statement position");
        }

        return new WhileStatement(test, body, location);
    }

    Statement parseWithStatement() {
        SourceLocation location = parserContext.getLocation();
        if (parserContext.strictMode) {
            throw new JSSyntaxErrorException("Strict mode code may not include a with statement");
        }
        if (!isWithKeyword()) {
            throw new JSSyntaxErrorException("Unexpected token");
        }
        parserContext.advance(); // consume 'with'
        parserContext.expect(TokenType.LPAREN);
        Expression object = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        Statement body = parseStatement();
        return new WithStatement(object, body, location);
    }

    private void validateBlockEarlyErrors(List<Statement> statements) {
        Set<String> lexicalNames = new HashSet<>();
        Set<String> simpleFunctionLexicalNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();

        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    Set<String> names = new HashSet<>();
                    collectPatternBoundNames(declarator.id(), names);
                    for (String name : names) {
                        if (!lexicalNames.add(name)) {
                            throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                        }
                    }
                }
            } else if (statement instanceof FunctionDeclaration functionDeclaration && functionDeclaration.id() != null) {
                String name = functionDeclaration.id().name();
                boolean isSimpleFunction = !functionDeclaration.isAsync() && !functionDeclaration.isGenerator();
                if (lexicalNames.contains(name)) {
                    boolean duplicatedSimpleFunctionDeclaration = simpleFunctionLexicalNames.contains(name) && isSimpleFunction;
                    if (!duplicatedSimpleFunctionDeclaration || parserContext.strictMode) {
                        throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                    }
                } else {
                    lexicalNames.add(name);
                }
                if (isSimpleFunction) {
                    simpleFunctionLexicalNames.add(name);
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.id() != null) {
                String name = classDeclaration.id().name();
                if (lexicalNames.contains(name)) {
                    throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                }
                lexicalNames.add(name);
            }
            collectVarDeclaredNames(statement, varNames);
        }

        for (String lexicalName : lexicalNames) {
            if (varNames.contains(lexicalName)) {
                throw new JSSyntaxErrorException("Identifier '" + lexicalName + "' has already been declared");
            }
        }
    }
}
