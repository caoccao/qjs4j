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

    static boolean hasUsingInitializer(VariableDeclaration variableDeclaration) {
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

    private boolean isLabelStart() {
        Token lookaheadToken = parserContext.peek();
        if (lookaheadToken == null || lookaheadToken.type() != TokenType.COLON) {
            return false;
        }
        return parserContext.currentToken.type().isIdentifierCandidate();
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
            SourceLocation asyncLocation = parserContext.consumeLocation(); // consume async
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
            delegates.declarations.validateFunctionBodyEarlyErrors(body);
        } else {
            delegates.declarations.validateBlockEarlyErrors(body);
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
        delegates.declarations.validateFunctionBodyEarlyErrors(body);
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
        SourceLocation location = parserContext.consumeLocation();
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
            delegates.declarations.validateForInOfDeclarationBodyEarlyErrors(varDecl, body);
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
        validateFunctionInStatementPosition(consequent, "if");
        Statement alternate = null;

        if (parserContext.consumeIf(TokenType.ELSE)) {
            alternate = parseStatement();
            validateNoLexicalDeclarationInStatementPosition(alternate);
            validateNoUsingDeclarationWithInitializerInStatementPosition(alternate);
            validateNoLabelledFunctionInIterationBody(alternate);
            validateFunctionInStatementPosition(alternate, "if");
        }

        return new IfStatement(test, consequent, alternate, location);
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
            validateFunctionInStatementPosition(body, "labelled");
            return new LabeledStatement(label, body, location);
        } finally {
            parserContext.labelStack.pop();
        }
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
                    yield delegates.modules().parseExportDeclaration();
                }
                case IMPORT -> {
                    Token nextToken = parserContext.peek();
                    boolean isDynamicImportExpression = nextToken != null
                            && (nextToken.type() == TokenType.LPAREN || nextToken.type() == TokenType.DOT);
                    if (!isDynamicImportExpression) {
                        if (parserContext.moduleMode && parserContext.functionNesting == 0
                                && parserContext.statementNesting <= 1) {
                            yield delegates.modules().parseImportDeclarationStatement();
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
        delegates.declarations.validateSwitchCaseBlockEarlyErrors(cases);
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
                delegates.declarations.collectPatternBoundNamesAndCheckDuplicates(param, boundNames);
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
            delegates.declarations.validateCatchClauseEarlyErrors(param, catchBody);
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
                delegates.declarations.collectPatternBoundNamesAndCheckDuplicates(id, declaredNames);
                if (declaredNames.contains(JSKeyword.LET)) {
                    throw new JSSyntaxErrorException("let is disallowed as a lexically bound name");
                }
            } else {
                delegates.declarations.collectPatternBoundNames(id, declaredNames);
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

    private void validateFunctionInStatementPosition(Statement statement, String position) {
        if (statement instanceof FunctionDeclaration functionDeclaration) {
            if (parserContext.strictMode && functionDeclaration.isAnnexBSimpleDeclaration()) {
                throw new JSSyntaxErrorException(
                        "In strict mode code, functions can only be declared at top level or inside a block.");
            }
            if (!functionDeclaration.isAnnexBSimpleDeclaration()) {
                throw new JSSyntaxErrorException(
                        "Function declarations are not allowed in " + position + " statement position");
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
            if (!of && declaration.getKind().isUsing()) {
                throw new JSSyntaxErrorException("The left-hand side of a for-in loop may not be a using declaration");
            }
            if (declaration.getDeclarations().size() != 1) {
                throw new JSSyntaxErrorException(kind + " loop must have exactly one declaration");
            }
            delegates.declarations.validateForInOfDeclarationBoundNames(declaration);
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
            delegates.declarations.validateForInOfDeclarationBodyEarlyErrors(declaration, body);
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
        if (parserContext.currentToken.type().isIdentifierCandidate() && !parserContext.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return label;
    }

}
