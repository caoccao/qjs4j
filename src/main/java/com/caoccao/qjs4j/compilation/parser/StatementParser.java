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
final class StatementParser extends GrammarProduction {
    StatementParser(ParserContext parserContext) {
        super(parserContext);
    }

    private boolean isDebuggerKeyword() {
        return tokens.identifier(JSKeyword.DEBUGGER) && !tokens.current().escaped();
    }

    private boolean isLabelStart() {
        Token lookaheadToken = tokens.next();
        if (lookaheadToken == null || lookaheadToken.type() != TokenType.COLON) {
            return false;
        }
        return tokens.current().type().isIdentifierCandidate();
    }

    private boolean isUsingDeclarationStartInForHead() {
        if (!tokens.isUsingDeclarationStart()) {
            return false;
        }
        Token bindingIdentifierToken = tokens.next();
        if (bindingIdentifierToken == null || bindingIdentifierToken.type() != TokenType.OF) {
            return true;
        }
        Token tokenAfterBindingIdentifier = tokens.lookAheadAfterNext();
        if (tokenAfterBindingIdentifier == null) {
            return true;
        }
        return tokenAfterBindingIdentifier.type() != TokenType.OF && tokenAfterBindingIdentifier.type() != TokenType.IN;
    }

    private boolean isWithKeyword() {
        return tokens.identifier(JSKeyword.WITH);
    }

    Statement parseAsyncDeclaration() {
        // ES2024: [no LineTerminator here] between `async` and `function`.
        // If there's a line terminator, `async` is an identifier expression statement.
        if (tokens.nextOnSameLine(TokenType.FUNCTION)) {
            SourceLocation asyncLocation = tokens.consumeLocation(); // consume async
            return parserContext.functions.parseFunctionDeclaration(true, false, asyncLocation);
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
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.LBRACE);

        if (tokens.match(TokenType.RBRACE)) {
            tokens.expect(TokenType.RBRACE);
            return new BlockStatement(List.of(), location);
        }

        List<Statement> body = new ArrayList<>();
        parseBlockStatements(body);

        if (functionBody) {
            parserContext.declarations.validateFunctionBodyEarlyErrors(body);
        } else {
            parserContext.declarations.validateBlockEarlyErrors(body);
        }
        tokens.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    BlockStatement parseBlockStatementAsBody() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        boolean hasUseStrict = parserContext.parseDirectives(body);

        parseFunctionStatements(body);
        tokens.expect(TokenType.RBRACE);
        BlockStatement block = new BlockStatement(body, location);
        block.setHasUseStrictDirective(hasUseStrict);
        return block;
    }

    private void parseBlockStatements(List<Statement> body) {
        while (!tokens.match(TokenType.RBRACE) && !tokens.match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }
    }

    Statement parseBreakStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.BREAK);
        return new BreakStatement(parseJumpLabel(), location);
    }

    Statement parseContinueStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.CONTINUE);
        return new ContinueStatement(parseJumpLabel(), location);
    }

    private Statement parseDebuggerStatement() {
        SourceLocation location = tokens.consumeLocation();
        parserContext.consumeSemicolon();
        return new DebuggerStatement(location);
    }

    Statement parseDoWhileStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.DO);
        Statement body = parseIterationBody("do-while");
        tokens.expect(TokenType.WHILE);
        Expression test = parseParenthesizedExpression();
        tokens.consumeIf(TokenType.SEMICOLON);
        return new DoWhileStatement(body, test, location);
    }

    Statement parseExpressionStatement() {
        SourceLocation location = tokens.getLocation();
        Expression expression = parserContext.expressions.parseExpression();
        if (expression instanceof ObjectExpression objectExpression && containsCoverInitializedName(objectExpression)) {
            throw new JSSyntaxErrorException("Invalid shorthand property initializer");
        }
        parserContext.consumeSemicolon();
        return new ExpressionStatement(expression, location);
    }

    private VariableDeclaration parseForDeclaration(boolean using) {
        SourceLocation location = tokens.getLocation();
        VariableKind kind;
        if (tokens.isAwaitUsingDeclarationStart()) {
            tokens.expect(TokenType.AWAIT);
            kind = VariableKind.AWAIT_USING;
        } else {
            kind = using ? VariableKind.USING : VariableKind.fromKeyword(tokens.current().value());
        }
        tokens.advance();
        boolean savedIn = parserContext.inOperatorAllowed;
        parserContext.inOperatorAllowed = false;
        try {
            return parseVariableDeclarationBody(kind, location, false);
        } finally {
            parserContext.inOperatorAllowed = savedIn;
        }
    }

    private Statement parseForEach(ASTNode left, boolean isAwait, SourceLocation location) {
        boolean of = tokens.match(TokenType.OF);
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
            parserContext.declarations.validateForInOfDeclarationBoundNames(declaration);
            if (of) {
                VariableDeclarator declarator = declaration.getDeclarations().get(0);
                if (declarator != null && declarator.getInit() != null) {
                    throw new JSSyntaxErrorException("Invalid initializer in for-of declaration");
                }
            } else {
                validateForInDeclarationInitializers(declaration);
            }
        } else {
            validateForTarget((Expression) left, tokens.current().offset());
        }
        tokens.advance();
        Expression right = of
                ? parserContext.expressions.parseAssignmentExpression()
                : parserContext.expressions.parseExpression();
        tokens.expect(TokenType.RPAREN);
        Statement body = parseIterationBody(kind);
        if (left instanceof VariableDeclaration declaration) {
            parserContext.declarations.validateForInOfDeclarationBodyEarlyErrors(declaration, body);
        }
        return of
                ? new ForOfStatement(left, right, body, isAwait, location)
                : new ForInStatement(left, right, body, location);
    }

    Statement parseForStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.FOR);

        // Check for 'await' keyword (for await...of)
        boolean isAwait = false;
        if (tokens.match(TokenType.AWAIT)) {
            if (!parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            isAwait = true;
            tokens.advance();
        }

        tokens.expect(TokenType.LPAREN);

        boolean using = isUsingDeclarationStartInForHead();
        Statement init = null;
        if (using || tokens.isAwaitUsingDeclarationStart() || tokens.match(TokenType.VAR)
                || tokens.match(TokenType.CONST)
                || (tokens.match(TokenType.LET) && shouldParseLetAsLexicalDeclarationInForHead())) {
            VariableDeclaration declaration = parseForDeclaration(using);
            if (tokens.match(TokenType.OF) || tokens.match(TokenType.IN)) {
                return parseForEach(declaration, isAwait, location);
            }
            requireSynchronousFor(isAwait);
            if (declaration.getKind() == VariableKind.CONST
                    && declaration.getDeclarations().stream().anyMatch(declarator -> declarator.getInit() == null)) {
                throw new JSSyntaxErrorException("Missing initializer in const declaration");
            }
            init = declaration;
            tokens.expect(TokenType.SEMICOLON);
        } else if (tokens.match(TokenType.SEMICOLON)) {
            requireSynchronousFor(isAwait);
            tokens.advance();
        } else {
            TokenType first = tokens.current().type();
            boolean savedIn = parserContext.inOperatorAllowed;
            parserContext.inOperatorAllowed = first == TokenType.LBRACKET || first == TokenType.LBRACE;
            Expression expr;
            try {
                expr = parserContext.expressions.parseAssignmentExpression();
            } finally {
                parserContext.inOperatorAllowed = savedIn;
            }
            if (expr instanceof BinaryExpression binary && binary.getOperator() == BinaryOperator.IN
                    && tokens.match(TokenType.RPAREN)) {
                requireSynchronousFor(isAwait);
                int offset = binary.getLocation() != null ? binary.getLocation().offset() : tokens.current().offset();
                validateForTarget(binary.getLeft(), offset);
                tokens.expect(TokenType.RPAREN);
                return new ForInStatement(binary.getLeft(), binary.getRight(), parseIterationBody("for-in"), location);
            }
            if (tokens.match(TokenType.IN) || tokens.match(TokenType.OF)) {
                if (!isAwait && tokens.match(TokenType.OF) && (first == TokenType.LET || (first == TokenType.ASYNC
                        && expr instanceof Identifier id && JSKeyword.ASYNC.equals(id.getName())))) {
                    throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
                }
                return parseForEach(expr, isAwait, location);
            }
            requireSynchronousFor(isAwait);
            while (tokens.consumeIf(TokenType.COMMA)) {
                Expression right = parserContext.expressions.parseAssignmentExpression();
                expr = new SequenceExpression(List.of(expr, right), expr.getLocation());
            }
            init = new ExpressionStatement(expr, expr.getLocation());
            parserContext.consumeSemicolon();
        }

        // Test
        Expression test = null;
        if (!tokens.match(TokenType.SEMICOLON)) {
            test = parserContext.expressions.parseExpression();
        }
        tokens.expect(TokenType.SEMICOLON);

        // Update
        Expression update = null;
        if (!tokens.match(TokenType.RPAREN)) {
            update = parserContext.expressions.parseExpression();
        }
        tokens.expect(TokenType.RPAREN);

        Statement body = parseIterationBody("for");
        if (init instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
            parserContext.declarations.validateForInOfDeclarationBodyEarlyErrors(varDecl, body);
        }

        return new ForStatement(init, test, update, body, location);
    }

    Set<String> parseFunctionStatements(List<Statement> body) {
        Deque<Set<String>> enclosingLabels = parserContext.enterFunctionLabelScope();
        try {
            parseBlockStatements(body);
        } finally {
            parserContext.exitFunctionLabelScope(enclosingLabels);
        }
        return parserContext.declarations.validateFunctionBodyEarlyErrors(body);
    }

    private Statement parseIfBranch() {
        Statement branch = parseStatement();
        validateNoLexicalDeclarationInStatementPosition(branch);
        validateNoUsingDeclarationWithInitializerInStatementPosition(branch);
        validateNoLabelledFunction(branch);
        validateFunctionInStatementPosition(branch, "if");
        return branch;
    }

    Statement parseIfStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.IF);
        Expression test = parseParenthesizedExpression();

        Statement consequent = parseIfBranch();
        Statement alternate = tokens.consumeIf(TokenType.ELSE) ? parseIfBranch() : null;
        return new IfStatement(test, consequent, alternate, location);
    }

    private Statement parseIterationBody(String kind) {
        Statement body = parseStatement();
        validateNoLabelledFunction(body);
        validateNoFunctionDeclaration(body, kind);
        validateNoLexicalDeclarationInStatementPosition(body);
        validateNoUsingDeclarationWithInitializerInStatementPosition(body);
        return body;
    }

    private Identifier parseJumpLabel() {
        Identifier label = null;
        if (tokens.current().type().isIdentifierCandidate() && !tokens.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return label;
    }

    /**
     * Parse a labeled statement: label: statement Following QuickJS js_parse_statement_or_decl label handling. In
     * non-strict mode, labeled function declarations are allowed (Annex B).
     */
    Statement parseLabeledStatement() {
        SourceLocation location = tokens.getLocation();
        Identifier label = parserContext.parseIdentifier();
        // ES2024 14.13.1: It is a Syntax Error if any source text is matched by
        // this production that also matches the LabelIdentifier of an enclosing LabelledStatement.
        for (Set<String> labels : parserContext.labelStack) {
            if (labels.contains(label.getName())) {
                throw new JSSyntaxErrorException("Label '" + label.getName() + "' has already been declared");
            }
        }
        tokens.expect(TokenType.COLON);
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

    private Expression parseParenthesizedExpression() {
        tokens.expect(TokenType.LPAREN);
        Expression expression = parserContext.expressions.parseExpression();
        tokens.expect(TokenType.RPAREN);
        return expression;
    }

    Statement parseReturnStatement() {
        SourceLocation location = tokens.getLocation();
        // Return is only valid inside function bodies.
        if (parserContext.inClassStaticInit || parserContext.functionNesting == 0) {
            throw new JSSyntaxErrorException("return not in a function");
        }
        tokens.expect(TokenType.RETURN);

        Expression argument = null;
        if (!tokens.hasNewlineBefore() && !tokens.match(TokenType.SEMICOLON) && !tokens.match(TokenType.RBRACE)
                && !tokens.match(TokenType.EOF)) {
            argument = parserContext.expressions.parseExpression();
        }

        parserContext.consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    Statement parseStatement() {
        parserContext.statementNesting++;
        try {
            if (tokens.isAwaitUsingDeclarationStart()) {
                validateAwaitUsingDeclarationPlacement();
                return parseUsingDeclaration(true);
            }
            if (tokens.isUsingDeclarationStart()) {
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
            return switch (tokens.current().type()) {
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
                        Token nextTok = tokens.next();
                        boolean hasLineTerminatorAfterLet = nextTok != null && nextTok.line() > tokens.current().line();
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
                    parserContext.functions.parseFunctionDeclaration(false, false);
                case CLASS -> // Class declarations are treated as statements in JavaScript
                    parserContext.functions.parseClassDeclaration();
                case EXPORT -> {
                    if (!parserContext.moduleMode || parserContext.statementNesting > 1) {
                        throw new JSSyntaxErrorException("Unexpected token 'export'");
                    }
                    yield parserContext.modules().parseExportDeclaration();
                }
                case IMPORT -> {
                    Token nextToken = tokens.next();
                    boolean isDynamicImportExpression = nextToken != null
                            && (nextToken.type() == TokenType.LPAREN || nextToken.type() == TokenType.DOT);
                    if (!isDynamicImportExpression) {
                        if (parserContext.moduleMode && parserContext.functionNesting == 0
                                && parserContext.statementNesting <= 1) {
                            yield parserContext.modules().parseImportDeclarationStatement();
                        }
                        throw new JSSyntaxErrorException("Cannot use import statement outside a module");
                    }
                    yield parseExpressionStatement();
                }
                case SEMICOLON -> {
                    tokens.advance(); // consume semicolon
                    yield null; // empty statement
                }
                default -> parseExpressionStatement();
            };
        } finally {
            parserContext.statementNesting--;
        }
    }

    Statement parseSwitchStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.SWITCH);
        Expression discriminant = parseParenthesizedExpression();
        tokens.expect(TokenType.LBRACE);

        List<SwitchStatement.SwitchCase> cases = new ArrayList<>();
        boolean defaultClauseFound = false;

        while (!tokens.match(TokenType.RBRACE) && !tokens.match(TokenType.EOF)) {
            Expression test = null;
            if (tokens.consumeIf(TokenType.CASE)) {
                test = parserContext.expressions.parseExpression();
            } else if (tokens.consumeIf(TokenType.DEFAULT)) {
                if (defaultClauseFound) {
                    throw new JSSyntaxErrorException("More than one default clause in switch statement");
                }
                defaultClauseFound = true;
            } else {
                throw tokens.unexpectedToken();
            }
            tokens.expect(TokenType.COLON);
            List<Statement> consequent = new ArrayList<>();
            while (!tokens.match(TokenType.CASE) && !tokens.match(TokenType.DEFAULT) && !tokens.match(TokenType.RBRACE)
                    && !tokens.match(TokenType.EOF)) {
                Statement stmt = parseStatement();
                if (stmt != null) {
                    consequent.add(stmt);
                }
            }
            cases.add(new SwitchStatement.SwitchCase(test, consequent));
        }

        tokens.expect(TokenType.RBRACE);
        parserContext.declarations.validateSwitchCaseBlockEarlyErrors(cases);
        return new SwitchStatement(discriminant, cases, location);
    }

    Statement parseThrowStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.THROW);
        if (tokens.hasNewlineBefore()) {
            throw new JSSyntaxErrorException("Illegal newline after throw");
        }
        Expression argument = parserContext.expressions.parseExpression();
        parserContext.consumeSemicolon();
        return new ThrowStatement(argument, location);
    }

    Statement parseTryStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.TRY);
        BlockStatement block = parseBlockStatement();

        TryStatement.CatchClause handler = null;
        if (tokens.consumeIf(TokenType.CATCH)) {
            Pattern param = null;
            if (tokens.consumeIf(TokenType.LPAREN)) {
                param = parserContext.patterns.parsePattern();
                Set<String> boundNames = new HashSet<>();
                parserContext.declarations.collectPatternBoundNamesAndCheckDuplicates(param, boundNames);
                if (parserContext.strictMode) {
                    for (String boundName : boundNames) {
                        if (JSKeyword.EVAL.equals(boundName) || JSKeyword.ARGUMENTS.equals(boundName)) {
                            throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                        }
                    }
                }
                tokens.expect(TokenType.RPAREN);
            }
            BlockStatement catchBody = parseBlockStatement();
            parserContext.declarations.validateCatchClauseEarlyErrors(param, catchBody);
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (tokens.consumeIf(TokenType.FINALLY)) {
            finalizer = parseBlockStatement();
        }

        if (handler == null && finalizer == null) {
            throw new JSSyntaxErrorException("Missing catch or finally after try");
        }

        return new TryStatement(block, handler, finalizer, location);
    }

    Statement parseUsingDeclaration(boolean isAwaitUsing) {
        SourceLocation location = tokens.getLocation();
        VariableKind kind;
        if (isAwaitUsing) {
            if (!parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            tokens.expect(TokenType.AWAIT);
            if (!tokens.isUsingIdentifierToken(tokens.current())) {
                throw tokens.unexpectedToken();
            }
            tokens.advance();
            kind = VariableKind.AWAIT_USING;
        } else {
            if (!tokens.isUsingIdentifierToken(tokens.current())) {
                throw tokens.unexpectedToken();
            }
            tokens.advance();
            kind = VariableKind.USING;
        }
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclaration() {
        SourceLocation location = tokens.getLocation();
        VariableKind kind = VariableKind.fromKeyword(tokens.current().value()); // VAR, LET, or CONST
        tokens.advance();
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclarationBody(VariableKind kind, SourceLocation location) {
        return parseVariableDeclarationBody(kind, location, true);
    }

    VariableDeclaration parseVariableDeclarationBody(VariableKind kind, SourceLocation location, boolean consumeSemi) {
        boolean isUsingKind = kind == VariableKind.USING || kind == VariableKind.AWAIT_USING;
        List<VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (tokens.consumeIf(TokenType.COMMA)) {
            }

            Pattern id = parserContext.patterns.parsePattern();
            Set<String> declaredNames = new HashSet<>();
            if (kind != VariableKind.VAR) {
                parserContext.declarations.collectPatternBoundNamesAndCheckDuplicates(id, declaredNames);
                if (declaredNames.contains(JSKeyword.LET)) {
                    throw new JSSyntaxErrorException("let is disallowed as a lexically bound name");
                }
            } else {
                parserContext.declarations.collectPatternBoundNames(id, declaredNames);
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

            if (tokens.consumeIf(TokenType.ASSIGN)) {
                init = parserContext.expressions.parseAssignmentExpression();
            } else if (kind == VariableKind.CONST && consumeSemi) {
                throw new JSSyntaxErrorException("Missing initializer in const declaration");
            } else if (isUsingKind && consumeSemi) {
                throw new JSSyntaxErrorException("Missing initializer in using declaration");
            }

            declarations.add(new VariableDeclarator(id, init));
        } while (tokens.match(TokenType.COMMA));

        if (consumeSemi) {
            parserContext.consumeSemicolon();
        }
        return new VariableDeclaration(declarations, kind, location);
    }

    Statement parseWhileStatement() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.WHILE);
        Expression test = parseParenthesizedExpression();
        Statement body = parseIterationBody("while");

        return new WhileStatement(test, body, location);
    }

    Statement parseWithStatement() {
        SourceLocation location = tokens.getLocation();
        if (parserContext.strictMode) {
            throw new JSSyntaxErrorException("Strict mode code may not include a with statement");
        }
        if (!isWithKeyword()) {
            throw new JSSyntaxErrorException("Unexpected token");
        }
        tokens.advance(); // consume 'with'
        Expression object = parseParenthesizedExpression();
        Statement body = parseStatement();
        validateNoFunctionDeclaration(body, "with");
        validateNoLabelledFunction(body);
        validateNoLexicalDeclarationInStatementPosition(body);
        return new WithStatement(object, body, location);
    }

    private boolean shouldParseLetAsLexicalDeclarationInForHead() {
        if (!tokens.match(TokenType.LET)) {
            return true;
        }
        if (parserContext.strictMode) {
            return true;
        }
        Token lookahead = tokens.next();
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

    private void validateForTarget(Expression left, int offset) {
        if (!parserContext.isValidForInOfTarget(left)) {
            throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
        }
        parserContext.expressions.validateForInOfAssignmentTarget(left, offset);
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

    private static boolean containsCoverInitializedName(ObjectExpression objectExpression) {
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

    private static boolean isLabelledFunction(Statement statement) {
        if (!(statement instanceof LabeledStatement labeledStatement)) {
            return false;
        }
        if (labeledStatement.getBody() instanceof FunctionDeclaration) {
            return true;
        }
        return labeledStatement.getBody() != null && isLabelledFunction(labeledStatement.getBody());
    }

    private static void requireSynchronousFor(boolean isAwait) {
        if (isAwait) {
            throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
        }
    }

    private static void validateNoFunctionDeclaration(Statement statement, String statementKind) {
        if (statement instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "Function declarations are not allowed in " + statementKind + " statement position");
        }
    }

    private static void validateNoLabelledFunction(Statement statement) {
        if (isLabelledFunction(statement)) {
            throw new JSSyntaxErrorException("Labelled functions are only allowed at body level");
        }
    }

    private static void validateNoLexicalDeclarationInStatementPosition(Statement statement) {
        if (statement instanceof VariableDeclaration variableDeclaration
                && variableDeclaration.getKind() != VariableKind.VAR) {
            throw new JSSyntaxErrorException("Lexical declaration cannot appear in a single-statement context");
        }
        if (statement instanceof ClassDeclaration) {
            throw new JSSyntaxErrorException("Lexical declaration cannot appear in a single-statement context");
        }
    }

    private static void validateNoUsingDeclarationWithInitializerInStatementPosition(Statement statement) {
        if (statement instanceof VariableDeclaration variableDeclaration && hasUsingInitializer(variableDeclaration)) {
            throw new JSSyntaxErrorException("using declarations are not allowed in this statement position");
        }
    }

}
