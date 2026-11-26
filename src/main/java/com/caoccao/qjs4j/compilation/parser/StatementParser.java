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
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delegate parser responsible for parsing JavaScript statements. Handles control flow (if, while, for, switch, try),
 * declarations (var, let, const, using), and statement-level constructs (block, labeled, return, break, continue,
 * throw).
 */
record StatementParser(ParserContext parserContext, ParserDelegates delegates) {
    private static boolean isIdentifierNameToken(TokenType type) {
        return switch (type) {
            case IDENTIFIER, AS, ASYNC, AWAIT, BREAK, CASE, CATCH, CLASS, CONST, CONTINUE, DEFAULT, DELETE, DO, ELSE,
                    EXPORT, EXTENDS, FALSE, FINALLY, FOR, FROM, FUNCTION, IF, IMPORT, IN, INSTANCEOF, LET, NEW, NULL,
                    OF, RETURN, SUPER, SWITCH, THIS, THROW, TRUE, TRY, TYPEOF, VAR, VOID, WHILE, YIELD ->
                true;
            default -> false;
        };
    }

    private void addModuleExportedName(String name) {
        if (parserContext.moduleMode && !parserContext.moduleExportedNames.add(name)) {
            throw new JSSyntaxErrorException("Duplicate export of '" + name + "'");
        }
    }

    private void addModuleLexicalName(String name) {
        if (parserContext.moduleMode && parserContext.functionNesting == 0) {
            if (parserContext.moduleVarNames.contains(name) || !parserContext.moduleLexicalNames.add(name)) {
                throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
            }
        }
    }

    /**
     * Collect declared names from an exported declaration and register them as exported names.
     */
    private void collectDeclaredNames(Statement decl, boolean isVar) {
        if (!parserContext.moduleMode) {
            return;
        }
        if (decl instanceof VariableDeclaration varDecl) {
            for (VariableDeclarator declarator : varDecl.getDeclarations()) {
                collectPatternNames(declarator.getId(), name -> {
                    addModuleExportedName(name);
                });
            }
        } else if (decl instanceof FunctionDeclaration funcDecl) {
            if (funcDecl.getId() != null) {
                addModuleExportedName(funcDecl.getId().getName());
            }
        } else if (decl instanceof ClassDeclaration classDecl) {
            if (classDecl.getId() != null) {
                addModuleExportedName(classDecl.getId().getName());
            }
        }
    }

    private void collectPatternBoundNames(Pattern pattern, Set<String> names) {
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

    private void collectPatternBoundNamesAndCheckDuplicates(Pattern pattern, Set<String> names) {
        for (String name : pattern.getBoundNames()) {
            if (parserContext.inClassStaticInit && JSKeyword.AWAIT.equals(name)) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            if (!names.add(name)) {
                throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
            }
        }
    }

    private void collectPatternNames(Pattern pattern, java.util.function.Consumer<String> consumer) {
        pattern.getBoundNames().forEach(consumer);
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
        Set<String> lexicalNames = new HashSet<>();
        for (Statement statement : statements) {
            List<String> names = new ArrayList<>();
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    names.addAll(declarator.getId().getBoundNames());
                }
            } else if (statement instanceof ClassDeclaration classDeclaration) {
                names.add(classDeclaration.getId().getName());
            }
            for (String name : names) {
                if (!lexicalNames.add(name)) {
                    throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                }
            }
        }
        return lexicalNames;
    }

    private void collectVarDeclaredNames(Statement statement, Set<String> varNames) {
        if (statement == null) {
            return;
        }
        for (VariableDeclarator declarator : statement.getVarDeclarators()) {
            collectPatternBoundNames(declarator.getId(), varNames);
        }
    }

    private boolean containsCoverInitializedName(ObjectExpression objectExpression) {
        for (ObjectExpressionProperty property : objectExpression.getProperties()) {
            if (!property.isShorthand()) {
                continue;
            }
            if (property.getValue() instanceof AssignmentExpression assignmentExpression
                    && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN) {
                return true;
            }
        }
        return false;
    }

    private void expectContextualKeyword(TokenType type, String keyword) {
        if (!parserContext.match(type) || parserContext.currentToken.escaped()) {
            throw new JSSyntaxErrorException(
                    "Expected '" + keyword + "' but got '" + parserContext.currentToken.value() + "' at line "
                            + parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
        }
        parserContext.advance();
    }

    private boolean hasUsingInitializer(VariableDeclaration variableDeclaration) {
        if (variableDeclaration == null) {
            return false;
        }
        VariableKind kind = variableDeclaration.getKind();
        if (kind != VariableKind.USING && kind != VariableKind.AWAIT_USING) {
            return false;
        }
        for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
            if (declarator != null && declarator.getInit() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isDebuggerKeyword() {
        return parserContext.currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.DEBUGGER.equals(parserContext.currentToken.value())
                && !parserContext.currentToken.escaped();
    }

    private boolean isLabelIdentifierToken(TokenType tokenType) {
        return tokenType == TokenType.IDENTIFIER || tokenType == TokenType.ASYNC || tokenType == TokenType.AWAIT
                || tokenType == TokenType.YIELD || tokenType == TokenType.FROM || tokenType == TokenType.OF
                || tokenType == TokenType.AS || tokenType == TokenType.LET;
    }

    private boolean isLabelStart() {
        Token lookaheadToken = parserContext.peek();
        if (lookaheadToken == null || lookaheadToken.type() != TokenType.COLON) {
            return false;
        }
        return isLabelIdentifierToken(parserContext.currentToken.type());
    }

    private boolean isLabelledFunction(Statement statement) {
        if (!(statement instanceof LabeledStatement labeledStatement)) {
            return false;
        }
        if (labeledStatement.getBody() instanceof FunctionDeclaration) {
            return true;
        }
        return labeledStatement.getBody() != null && isLabelledFunction(labeledStatement.getBody());
    }

    private boolean isUsingDeclarationStartInForHead() {
        if (!parserContext.isUsingDeclarationStart()) {
            return false;
        }
        Token bindingIdentifierToken = parserContext.peek();
        if (bindingIdentifierToken == null || bindingIdentifierToken.type() != TokenType.OF) {
            return true;
        }
        Token tokenAfterBindingIdentifier = parserContext.lexer.peekToken();
        if (tokenAfterBindingIdentifier == null) {
            return true;
        }
        return tokenAfterBindingIdentifier.type() != TokenType.OF && tokenAfterBindingIdentifier.type() != TokenType.IN;
    }

    private boolean isWithKeyword() {
        return parserContext.currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.WITH.equals(parserContext.currentToken.value());
    }

    Statement parseAsyncDeclaration() {
        // ES2024: [no LineTerminator here] between `async` and `function`.
        // If there's a line terminator, `async` is an identifier expression statement.
        if (parserContext.nextToken.type() == TokenType.FUNCTION
                && parserContext.nextToken.line() == parserContext.currentToken.line()) {
            SourceLocation asyncLocation = parserContext.getLocation();
            parserContext.advance(); // consume async
            return delegates.functions.parseFunctionDeclaration(true, false, asyncLocation);
        } else {
            return parseExpressionStatement();
        }
    }

    BlockStatement parseBlockStatement() {
        return parseBlockStatement(false);
    }

    /**
     * Parse a block.
     *
     * @param functionBody
     *            true for a class static block, whose declarations follow function-body rules rather than block rules
     * @return the block
     */
    BlockStatement parseBlockStatement(boolean functionBody) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);

        if (parserContext.match(TokenType.RBRACE)) {
            parserContext.expect(TokenType.RBRACE);
            return new BlockStatement(List.of(), location);
        }

        List<Statement> body = new ArrayList<>();
        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        if (functionBody) {
            validateFunctionBodyEarlyErrors(body);
        } else {
            validateBlockEarlyErrors(body);
        }
        parserContext.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    BlockStatement parseBlockStatementAsBody() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        boolean hasUseStrict = parserContext.parseDirectives(body);

        Deque<Set<String>> enclosingLabels = parserContext.enterFunctionLabelScope();
        try {
            while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                Statement stmt = parseStatement();
                if (stmt != null) {
                    body.add(stmt);
                }
            }
        } finally {
            parserContext.exitFunctionLabelScope(enclosingLabels);
        }

        // An arrow function's body: function-body rules, under which a top-level function declaration is
        // var-scoped and `function f() {} var f;` is legal.
        validateFunctionBodyEarlyErrors(body);
        parserContext.expect(TokenType.RBRACE);
        BlockStatement block = new BlockStatement(body, location);
        block.setHasUseStrictDirective(hasUseStrict);
        return block;
    }

    Statement parseBreakStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.BREAK);
        return new BreakStatement(parseJumpLabel(), location);
    }

    Statement parseContinueStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.CONTINUE);
        return new ContinueStatement(parseJumpLabel(), location);
    }

    private Statement parseDebuggerStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.advance();
        parserContext.consumeSemicolon();
        return new DebuggerStatement(location);
    }

    Statement parseDoWhileStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.DO);
        Statement body = parseIterationBody("do-while");
        parserContext.expect(TokenType.WHILE);
        parserContext.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        parserContext.consumeIf(TokenType.SEMICOLON);
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
                String className = classExpr.getId() != null ? classExpr.getId().getName() : null;
                if (classExpr.getId() == null) {
                    classExpr = new ClassExpression(new Identifier(JSKeyword.DEFAULT, classExpr.getLocation()),
                            classExpr.getSuperClass(), classExpr.getBody(), classExpr.getLocation());
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
                    && parserContext.peek().type() == TokenType.FUNCTION
                    && parserContext.peek().line() == parserContext.currentToken.line()) {
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
        if (parserContext.consumeIf(TokenType.MUL)) {
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
        if (parserContext.match(TokenType.VAR) || parserContext.match(TokenType.LET)
                || parserContext.match(TokenType.CONST)) {
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
                && parserContext.peek().type() == TokenType.FUNCTION
                && parserContext.peek().line() == parserContext.currentToken.line()) {
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
     * Parse export { specifiers } and optional 'from' clause. Handles string export/import names per ES2024
     * ModuleExportName.
     */
    private void parseExportNamedSpecifiers() {
        parserContext.advance(); // consume '{'
        boolean hasStringLocalName = false;
        List<String[]> specifiers = new ArrayList<>(); // [localName, exportedName]
        while (!parserContext.match(TokenType.RBRACE)) {
            if (parserContext.consumeIf(TokenType.COMMA)) {
                continue;
            }
            // The local name in export { local as exported }
            boolean localIsString = parserContext.match(TokenType.STRING);
            if (localIsString) {
                hasStringLocalName = true;
            }
            String localName = parseModuleExportNameAndReturn();
            String exportedName = localName;
            if (parserContext.match(TokenType.IDENTIFIER) && JSKeyword.AS.equals(parserContext.currentToken.value())) {
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
            if (parserContext.consumeIf(TokenType.COMMA)) {
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
            throw new JSSyntaxErrorException("Expected 'from' but got '" + parserContext.currentToken.value() + "'");
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
        if (expression instanceof ObjectExpression objectExpression && containsCoverInitializedName(objectExpression)) {
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

        boolean using = isUsingDeclarationStartInForHead();
        Statement init = null;
        if (using || parserContext.isAwaitUsingDeclarationStart() || parserContext.match(TokenType.VAR)
                || parserContext.match(TokenType.CONST)
                || (parserContext.match(TokenType.LET) && shouldParseLetAsLexicalDeclarationInForHead())) {
            VariableDeclaration declaration = parseForDeclaration(using);
            if (parserContext.match(TokenType.OF) || parserContext.match(TokenType.IN)) {
                return parseForEach(declaration, isAwait, location);
            }
            requireSynchronousFor(isAwait);
            if (declaration.getKind() == VariableKind.CONST
                    && declaration.getDeclarations().stream().anyMatch(declarator -> declarator.getInit() == null)) {
                throw new JSSyntaxErrorException("Missing initializer in const declaration");
            }
            init = declaration;
            parserContext.expect(TokenType.SEMICOLON);
        } else if (parserContext.match(TokenType.SEMICOLON)) {
            requireSynchronousFor(isAwait);
            parserContext.advance();
        } else {
            TokenType first = parserContext.currentToken.type();
            boolean savedIn = parserContext.inOperatorAllowed;
            parserContext.inOperatorAllowed = first == TokenType.LBRACKET || first == TokenType.LBRACE;
            Expression expr;
            try {
                expr = delegates.expressions.parseAssignmentExpression();
            } finally {
                parserContext.inOperatorAllowed = savedIn;
            }
            if (expr instanceof BinaryExpression binary && binary.getOperator() == BinaryOperator.IN
                    && parserContext.match(TokenType.RPAREN)) {
                requireSynchronousFor(isAwait);
                int offset = binary.getLocation() != null
                        ? binary.getLocation().offset()
                        : parserContext.currentToken.offset();
                validateForTarget(binary.getLeft(), offset);
                parserContext.expect(TokenType.RPAREN);
                return new ForInStatement(binary.getLeft(), binary.getRight(), parseIterationBody("for-in"), location);
            }
            if (parserContext.match(TokenType.IN) || parserContext.match(TokenType.OF)) {
                if (!isAwait && parserContext.match(TokenType.OF)
                        && (first == TokenType.LET || (first == TokenType.ASYNC && expr instanceof Identifier id
                                && JSKeyword.ASYNC.equals(id.getName())))) {
                    throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
                }
                return parseForEach(expr, isAwait, location);
            }
            requireSynchronousFor(isAwait);
            while (parserContext.consumeIf(TokenType.COMMA)) {
                Expression right = delegates.expressions.parseAssignmentExpression();
                expr = new SequenceExpression(List.of(expr, right), expr.getLocation());
            }
            init = new ExpressionStatement(expr, expr.getLocation());
            parserContext.consumeSemicolon();
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

        Statement body = parseIterationBody("for");
        if (init instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
            validateForInOfDeclarationBodyEarlyErrors(varDecl, body);
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
        validateNoLexicalDeclarationInStatementPosition(consequent);
        validateNoUsingDeclarationWithInitializerInStatementPosition(consequent);
        validateNoLabelledFunctionInIterationBody(consequent);
        validateNoFunctionDeclarationInIfStatementPosition(consequent);
        Statement alternate = null;

        if (parserContext.consumeIf(TokenType.ELSE)) {
            alternate = parseStatement();
            validateNoLexicalDeclarationInStatementPosition(alternate);
            validateNoUsingDeclarationWithInitializerInStatementPosition(alternate);
            validateNoLabelledFunctionInIterationBody(alternate);
            validateNoFunctionDeclarationInIfStatementPosition(alternate);
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
        if (parserContext.match(TokenType.IDENTIFIER) && "defer".equals(parserContext.currentToken.value())
                && parserContext.peek() != null && parserContext.peek().type() == TokenType.MUL) {
            parserContext.advance(); // consume 'defer'
            parserContext.advance(); // consume '*'
            expectContextualKeyword(TokenType.AS, "as");
            Identifier nsBinding = parserContext.parseIdentifier();
            addModuleLexicalName(nsBinding.getName());
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // Namespace import: import * as ns from 'module';
        if (parserContext.consumeIf(TokenType.MUL)) {
            expectContextualKeyword(TokenType.AS, "as");
            Identifier nsBinding = parserContext.parseIdentifier();
            addModuleLexicalName(nsBinding.getName());
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
            addModuleLexicalName(defaultBinding.getName());
            Set<String> boundNames = new HashSet<>();
            boundNames.add(defaultBinding.getName());
            if (parserContext.consumeIf(TokenType.COMMA)) {
                if (parserContext.consumeIf(TokenType.MUL)) {
                    expectContextualKeyword(TokenType.AS, "as");
                    Identifier nsBinding = parserContext.parseIdentifier();
                    addModuleLexicalName(nsBinding.getName());
                    if (boundNames.contains(nsBinding.getName())) {
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
                throw new JSSyntaxErrorException("Invalid module export name: unpaired surrogate");
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

    /**
     * Parse a labeled statement: label: statement Following QuickJS js_parse_statement_or_decl label handling. In
     * non-strict mode, labeled function declarations are allowed (Annex B).
     */
    Statement parseLabeledStatement() {
        SourceLocation location = parserContext.getLocation();
        Identifier label = parserContext.parseIdentifier();
        // ES2024 14.13.1: It is a Syntax Error if any source text is matched by
        // this production that also matches the LabelIdentifier of an enclosing LabelledStatement.
        for (Set<String> labels : parserContext.labelStack) {
            if (labels.contains(label.getName())) {
                throw new JSSyntaxErrorException("Label '" + label.getName() + "' has already been declared");
            }
        }
        parserContext.expect(TokenType.COLON);
        Set<String> currentLabels = new HashSet<>();
        currentLabels.add(label.getName());
        parserContext.labelStack.push(currentLabels);
        try {
            Statement body = parseStatement();
            validateNoLexicalDeclarationInStatementPosition(body);
            validateNoUsingDeclarationWithInitializerInStatementPosition(body);
            if (body instanceof FunctionDeclaration functionDeclaration) {
                if (parserContext.strictMode && !functionDeclaration.isAsync() && !functionDeclaration.isGenerator()) {
                    throw new JSSyntaxErrorException(
                            "In strict mode code, functions can only be declared at top level or inside a block.");
                }
                if (functionDeclaration.isAsync() || functionDeclaration.isGenerator()) {
                    throw new JSSyntaxErrorException(
                            "Function declarations are not allowed in labelled statement position");
                }
            }
            return new LabeledStatement(label, body, location);
        } finally {
            parserContext.labelStack.pop();
        }
    }

    /**
     * Parse a module export name without returning value (backward compat).
     */
    private void parseModuleExportName() {
        parseModuleExportNameAndReturn();
    }

    /**
     * Parse a module export name: either an identifier name or a string literal. ModuleExportName :: IdentifierName |
     * StringLiteral Returns the parsed name for tracking purposes.
     */
    private String parseModuleExportNameAndReturn() {
        if (parserContext.match(TokenType.STRING)) {
            String name = parserContext.currentToken.value();
            // ES2024: ModuleExportName string must not contain unpaired surrogates
            if (UnicodeStringUtils.hasUnpairedSurrogate(name)) {
                throw new JSSyntaxErrorException("Invalid module export name: unpaired surrogate");
            }
            parserContext.advance();
            return name;
        } else {
            // Any IdentifierName including keywords is allowed as export name
            String name = parseImportIdentifierName();
            return name;
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
                    localName = parserContext.parseIdentifier().getName();
                } else {
                    localName = importName;
                }
                // In strict mode (modules are always strict), eval/arguments are forbidden
                if (parserContext.strictMode
                        && (JSKeyword.EVAL.equals(localName) || JSKeyword.ARGUMENTS.equals(localName))) {
                    throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
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
        if (parserContext.inClassStaticInit || parserContext.functionNesting == 0) {
            throw new JSSyntaxErrorException("return not in a function");
        }
        parserContext.expect(TokenType.RETURN);

        Expression argument = null;
        if (!parserContext.hasNewlineBefore() && !parserContext.match(TokenType.SEMICOLON)
                && !parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            argument = delegates.expressions.parseExpression();
        }

        parserContext.consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    Statement parseStatement() {
        parserContext.statementNesting++;
        try {
            if (parserContext.isAwaitUsingDeclarationStart()) {
                validateAwaitUsingDeclarationPlacement();
                return parseUsingDeclaration(true);
            }
            if (parserContext.isUsingDeclarationStart()) {
                return parseUsingDeclaration(false);
            }
            if (isDebuggerKeyword()) {
                return parseDebuggerStatement();
            }
            if (isWithKeyword()) {
                return parseWithStatement();
            }
            // Check for labeled statement: identifier followed by ':'
            // Following QuickJS is_label() check
            if (isLabelStart()) {
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
                case LET -> {
                    // In sloppy mode, 'let' can be an identifier (not a declaration keyword)
                    // when NOT followed by an identifier, '[', or '{'
                    if (!parserContext.strictMode) {
                        Token nextTok = parserContext.peek();
                        boolean hasLineTerminatorAfterLet = nextTok != null
                                && nextTok.line() > parserContext.currentToken.line();
                        boolean treatLetAsExpressionByAsi = hasLineTerminatorAfterLet
                                && (nextTok.type() == TokenType.IDENTIFIER || nextTok.type() == TokenType.LBRACE);
                        if (nextTok == null || treatLetAsExpressionByAsi
                                || (nextTok.type() != TokenType.IDENTIFIER && nextTok.type() != TokenType.LBRACKET
                                        && nextTok.type() != TokenType.LBRACE && nextTok.type() != TokenType.ASYNC
                                        && nextTok.type() != TokenType.AWAIT && nextTok.type() != TokenType.YIELD
                                        && nextTok.type() != TokenType.LET)) {
                            yield parseExpressionStatement();
                        }
                    }
                    yield parseVariableDeclaration();
                }
                case VAR, CONST -> parseVariableDeclaration();
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
        boolean defaultClauseFound = false;

        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Expression test = null;
            if (parserContext.consumeIf(TokenType.CASE)) {
                test = delegates.expressions.parseExpression();
            } else if (parserContext.consumeIf(TokenType.DEFAULT)) {
                if (defaultClauseFound) {
                    throw new JSSyntaxErrorException("More than one default clause in switch statement");
                }
                defaultClauseFound = true;
            } else {
                throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
            }
            parserContext.expect(TokenType.COLON);
            List<Statement> consequent = new ArrayList<>();
            while (!parserContext.match(TokenType.CASE) && !parserContext.match(TokenType.DEFAULT)
                    && !parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                Statement stmt = parseStatement();
                if (stmt != null) {
                    consequent.add(stmt);
                }
            }
            cases.add(new SwitchStatement.SwitchCase(test, consequent));
        }

        parserContext.expect(TokenType.RBRACE);
        validateSwitchCaseBlockEarlyErrors(cases);
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
        if (parserContext.consumeIf(TokenType.CATCH)) {
            Pattern param = null;
            if (parserContext.consumeIf(TokenType.LPAREN)) {
                param = delegates.patterns.parsePattern();
                Set<String> boundNames = new HashSet<>();
                collectPatternBoundNamesAndCheckDuplicates(param, boundNames);
                if (parserContext.strictMode) {
                    for (String boundName : boundNames) {
                        if (JSKeyword.EVAL.equals(boundName) || JSKeyword.ARGUMENTS.equals(boundName)) {
                            throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                        }
                    }
                }
                parserContext.expect(TokenType.RPAREN);
            }
            BlockStatement catchBody = parseBlockStatement();
            validateCatchClauseEarlyErrors(param, catchBody);
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (parserContext.consumeIf(TokenType.FINALLY)) {
            finalizer = parseBlockStatement();
        }

        if (handler == null && finalizer == null) {
            throw new JSSyntaxErrorException("Missing catch or finally after try");
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
                throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
            }
            parserContext.advance();
            kind = VariableKind.AWAIT_USING;
        } else {
            if (!parserContext.isUsingIdentifierToken(parserContext.currentToken)) {
                throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
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
        boolean isUsingKind = kind == VariableKind.USING || kind == VariableKind.AWAIT_USING;
        List<VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (parserContext.consumeIf(TokenType.COMMA)) {
            }

            Pattern id = delegates.patterns.parsePattern();
            Set<String> declaredNames = new HashSet<>();
            if (kind != VariableKind.VAR) {
                collectPatternBoundNamesAndCheckDuplicates(id, declaredNames);
                if (declaredNames.contains(JSKeyword.LET)) {
                    throw new JSSyntaxErrorException("let is disallowed as a lexically bound name");
                }
            } else {
                collectPatternBoundNames(id, declaredNames);
            }
            if (parserContext.strictMode) {
                for (String declaredName : declaredNames) {
                    if (JSKeyword.EVAL.equals(declaredName) || JSKeyword.ARGUMENTS.equals(declaredName)) {
                        throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                    }
                }
            }
            if (isUsingKind && !(id instanceof Identifier)) {
                throw new JSSyntaxErrorException("Invalid using declaration");
            }
            Expression init = null;

            if (parserContext.consumeIf(TokenType.ASSIGN)) {
                init = delegates.expressions.parseAssignmentExpression();
            } else if (kind == VariableKind.CONST && consumeSemi) {
                throw new JSSyntaxErrorException("Missing initializer in const declaration");
            } else if (isUsingKind && consumeSemi) {
                throw new JSSyntaxErrorException("Missing initializer in using declaration");
            }

            declarations.add(new VariableDeclarator(id, init));
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
        Statement body = parseIterationBody("while");

        return new WhileStatement(test, body, location);
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
                } else if (isIdentifierNameToken(parserContext.currentToken.type())) {
                    // AttributeKey is IdentifierName, not IdentifierReference, so a reserved word
                    // is a valid key: `import x from './m.js' with {if: ''}` parses.
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
        validateNoFunctionDeclarationInWithStatementPosition(body);
        validateNoLabelledFunctionInWithStatementPosition(body);
        validateNoLexicalDeclarationInStatementPosition(body);
        return new WithStatement(object, body, location);
    }

    private boolean shouldParseLetAsLexicalDeclarationInForHead() {
        if (!parserContext.match(TokenType.LET)) {
            return true;
        }
        if (parserContext.strictMode) {
            return true;
        }
        Token lookahead = parserContext.peek();
        if (lookahead == null) {
            return true;
        }
        return switch (lookahead.type()) {
            case IDENTIFIER, ASYNC, AWAIT, YIELD, FROM, OF, AS, LET, LBRACKET, LBRACE -> true;
            default -> false;
        };
    }

    private void validateAwaitUsingDeclarationPlacement() {
        if (parserContext.moduleMode || parserContext.functionNesting > 0 || parserContext.statementNesting > 1
                || parserContext.inClassStaticInit || parserContext.classBodyNesting > 0) {
            return;
        }
        throw new JSSyntaxErrorException("using declarations are not allowed at the top level of scripts");
    }

    private void validateBlockEarlyErrors(List<Statement> statements) {
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
        Set<String> varNames = new HashSet<>();
        for (Statement statement : statements) {
            collectVarDeclaredNames(statement, varNames);
            FunctionDeclaration functionDeclaration = statement.unwrapLabeledFunctionDeclaration();
            if (functionDeclaration != null) {
                varNames.add(functionDeclaration.getId().getName());
            }
        }
        for (String lexicalName : lexicalNames) {
            if (varNames.contains(lexicalName)) {
                throw new JSSyntaxErrorException("Identifier '" + lexicalName + "' has already been declared");
            }
        }
    }

    private void validateCatchClauseEarlyErrors(Pattern catchParameter, BlockStatement catchBody) {
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

        for (String catchParameterName : catchParameterNames) {
            if (lexicalNames.contains(catchParameterName)) {
                throw new JSSyntaxErrorException("Identifier '" + catchParameterName + "' has already been declared");
            }
        }

        // Annex B.3.4 lets a var in the catch block redeclare a simple catch parameter only. A destructuring
        // parameter's names conflict with any var of the block: `catch ([e]) { var e }` is a SyntaxError.
        if (!(catchParameter instanceof Identifier)) {
            Set<String> varNames = new HashSet<>();
            for (Statement statement : catchBody.getBody()) {
                collectVarDeclaredNames(statement, varNames);
            }
            for (String catchParameterName : catchParameterNames) {
                if (varNames.contains(catchParameterName)) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + catchParameterName + "' has already been declared");
                }
            }
        }
    }

    private void validateForInDeclarationInitializers(VariableDeclaration variableDeclaration) {
        if (variableDeclaration == null || variableDeclaration.getDeclarations().isEmpty()) {
            return;
        }
        VariableDeclarator variableDeclarator = variableDeclaration.getDeclarations().get(0);
        if (variableDeclarator == null || variableDeclarator.getInit() == null) {
            return;
        }
        if (variableDeclaration.getKind() != VariableKind.VAR) {
            throw new JSSyntaxErrorException("Invalid initializer in for-in declaration");
        }
        if (parserContext.strictMode || !(variableDeclarator.getId() instanceof Identifier)) {
            throw new JSSyntaxErrorException("Invalid initializer in for-in declaration");
        }
    }

    private void validateForInOfDeclarationBodyEarlyErrors(VariableDeclaration declaration, Statement statement) {
        if (declaration == null || declaration.getKind() == VariableKind.VAR) {
            return;
        }
        Set<String> boundNames = new HashSet<>();
        for (VariableDeclarator declarator : declaration.getDeclarations()) {
            collectPatternBoundNames(declarator.getId(), boundNames);
        }
        Set<String> varNames = new HashSet<>();
        collectVarDeclaredNames(statement, varNames);
        for (String boundName : boundNames) {
            if (varNames.contains(boundName)) {
                throw new JSSyntaxErrorException("Identifier '" + boundName + "' has already been declared");
            }
        }
    }

    private void validateForInOfDeclarationBoundNames(VariableDeclaration declaration) {
        if (declaration == null || declaration.getKind() == VariableKind.VAR) {
            return;
        }
        Set<String> boundNames = new HashSet<>();
        for (VariableDeclarator declarator : declaration.getDeclarations()) {
            collectPatternBoundNamesAndCheckDuplicates(declarator.getId(), boundNames);
        }
    }

    private void validateNoFunctionDeclarationInIfStatementPosition(Statement statement) {
        if (statement instanceof FunctionDeclaration functionDeclaration) {
            if (parserContext.strictMode && !functionDeclaration.isAsync() && !functionDeclaration.isGenerator()) {
                throw new JSSyntaxErrorException(
                        "In strict mode code, functions can only be declared at top level or inside a block.");
            }
            if (functionDeclaration.isAsync() || functionDeclaration.isGenerator()) {
                throw new JSSyntaxErrorException("Function declarations are not allowed in if statement position");
            }
        }
    }

    private void validateNoFunctionDeclarationInIterationBody(Statement statement, String statementKind) {
        if (statement instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "Function declarations are not allowed in " + statementKind + " statement position");
        }
    }

    private void validateNoFunctionDeclarationInWithStatementPosition(Statement statement) {
        if (statement instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in with statement position");
        }
    }

    private void validateNoLabelledFunctionInIterationBody(Statement statement) {
        if (isLabelledFunction(statement)) {
            throw new JSSyntaxErrorException("Labelled functions are only allowed at body level");
        }
    }

    private void validateNoLabelledFunctionInWithStatementPosition(Statement statement) {
        if (isLabelledFunction(statement)) {
            throw new JSSyntaxErrorException("Labelled functions are only allowed at body level");
        }
    }

    private void validateNoLexicalDeclarationInStatementPosition(Statement statement) {
        if (statement instanceof VariableDeclaration variableDeclaration
                && variableDeclaration.getKind() != VariableKind.VAR) {
            throw new JSSyntaxErrorException("Lexical declaration cannot appear in a single-statement context");
        }
        if (statement instanceof ClassDeclaration) {
            throw new JSSyntaxErrorException("Lexical declaration cannot appear in a single-statement context");
        }
    }

    private void validateNoUsingDeclarationWithInitializerInStatementPosition(Statement statement) {
        if (statement instanceof VariableDeclaration variableDeclaration && hasUsingInitializer(variableDeclaration)) {
            throw new JSSyntaxErrorException("using declarations are not allowed in this statement position");
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
                            if (varNames.contains(name) || !lexicalNames.add(name)) {
                                throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                            }
                        }
                    }
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String className = classDeclaration.getId().getName();
                if (varNames.contains(className) || !lexicalNames.add(className)) {
                    throw new JSSyntaxErrorException("Identifier '" + className + "' has already been declared");
                }
            } else if (statement instanceof FunctionDeclaration functionDeclaration
                    && functionDeclaration.getId() != null) {
                varNames.add(functionDeclaration.getId().getName());
            }
            collectVarDeclaredNames(statement, varNames);
        }

        for (String lexicalName : lexicalNames) {
            if (varNames.contains(lexicalName)) {
                throw new JSSyntaxErrorException("Identifier '" + lexicalName + "' has already been declared");
            }
        }
    }

    private void validateSwitchCaseBlockEarlyErrors(List<SwitchStatement.SwitchCase> cases) {
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

    private VariableDeclaration parseForDeclaration(boolean using) {
        SourceLocation location = parserContext.getLocation();
        VariableKind kind;
        if (parserContext.isAwaitUsingDeclarationStart()) {
            parserContext.expect(TokenType.AWAIT);
            kind = VariableKind.AWAIT_USING;
        } else {
            kind = using ? VariableKind.USING : VariableKind.fromKeyword(parserContext.currentToken.value());
        }
        parserContext.advance();
        boolean savedIn = parserContext.inOperatorAllowed;
        parserContext.inOperatorAllowed = false;
        try {
            return parseVariableDeclarationBody(kind, location, false);
        } finally {
            parserContext.inOperatorAllowed = savedIn;
        }
    }

    private Statement parseForEach(ASTNode left, boolean isAwait, SourceLocation location) {
        boolean of = parserContext.match(TokenType.OF);
        String kind = of ? "for-of" : "for-in";
        if (!of) {
            requireSynchronousFor(isAwait);
        }
        if (left instanceof VariableDeclaration declaration) {
            if (!of && (declaration.getKind() == VariableKind.USING
                    || declaration.getKind() == VariableKind.AWAIT_USING)) {
                throw new JSSyntaxErrorException("The left-hand side of a for-in loop may not be a using declaration");
            }
            if (declaration.getDeclarations().size() != 1) {
                throw new JSSyntaxErrorException(kind + " loop must have exactly one declaration");
            }
            validateForInOfDeclarationBoundNames(declaration);
            if (of) {
                VariableDeclarator declarator = declaration.getDeclarations().get(0);
                if (declarator != null && declarator.getInit() != null) {
                    throw new JSSyntaxErrorException("Invalid initializer in for-of declaration");
                }
            } else {
                validateForInDeclarationInitializers(declaration);
            }
        } else {
            validateForTarget((Expression) left, parserContext.currentToken.offset());
        }
        parserContext.advance();
        Expression right = of
                ? delegates.expressions.parseAssignmentExpression()
                : delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        Statement body = parseIterationBody(kind);
        if (left instanceof VariableDeclaration declaration) {
            validateForInOfDeclarationBodyEarlyErrors(declaration, body);
        }
        return of
                ? new ForOfStatement(left, right, body, isAwait, location)
                : new ForInStatement(left, right, body, location);
    }

    private void validateForTarget(Expression left, int offset) {
        if (!parserContext.isValidForInOfTarget(left)) {
            throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
        }
        delegates.expressions.validateForInOfAssignmentTarget(left, offset);
    }

    private static void requireSynchronousFor(boolean isAwait) {
        if (isAwait) {
            throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
        }
    }

    private Statement parseIterationBody(String kind) {
        Statement body = parseStatement();
        validateNoLabelledFunctionInIterationBody(body);
        validateNoFunctionDeclarationInIterationBody(body, kind);
        validateNoLexicalDeclarationInStatementPosition(body);
        validateNoUsingDeclarationWithInitializerInStatementPosition(body);
        return body;
    }

    private Identifier parseJumpLabel() {
        Identifier label = null;
        if (isLabelIdentifierToken(parserContext.currentToken.type()) && !parserContext.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return label;
    }

    private final class BlockBindings {
        private final Set<String> lexicalNames = new HashSet<>();
        private final Set<String> simpleFunctionLexicalNames = new HashSet<>();
        private final Set<String> varNames = new HashSet<>();

        void checkVarConflicts() {
            for (String lexicalName : lexicalNames) {
                if (varNames.contains(lexicalName)) {
                    throw new JSSyntaxErrorException("Identifier '" + lexicalName + "' has already been declared");
                }
            }
        }

        void collect(Statement statement, boolean inSwitch) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                if (inSwitch && hasUsingInitializer(variableDeclaration)) {
                    throw new JSSyntaxErrorException(
                            "using declarations are not allowed directly in case/default clauses");
                }
                for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    Set<String> names = new HashSet<>();
                    if (inSwitch) {
                        collectPatternBoundNamesAndCheckDuplicates(declarator.getId(), names);
                    } else {
                        collectPatternBoundNames(declarator.getId(), names);
                    }
                    for (String name : names) {
                        if (!lexicalNames.add(name)) {
                            throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                        }
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
                        throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
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
                    throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                }
                lexicalNames.add(name);
            }
            collectVarDeclaredNames(statement, varNames);
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
}
