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
final class StatementParser {
    private StatementParser() {
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

    private static boolean isDebuggerKeyword(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        return tokens.identifier(JSKeyword.DEBUGGER) && !tokens.current().escaped();
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

    private static boolean isLabelStart(ParserContext parserContext) {
        Token lookaheadToken = parserContext.tokens.next();
        if (lookaheadToken == null || lookaheadToken.type() != TokenType.COLON) {
            return false;
        }
        return parserContext.tokens.current().type().isIdentifierCandidate();
    }

    private static boolean isUsingDeclarationStartInForHead(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
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

    private static boolean isWithKeyword(ParserContext parserContext) {
        return parserContext.tokens.identifier(JSKeyword.WITH);
    }

    static Statement parseAsyncDeclaration(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        // ES2024: [no LineTerminator here] between `async` and `function`.
        // If there's a line terminator, `async` is an identifier expression statement.
        if (tokens.nextOnSameLine(TokenType.FUNCTION)) {
            SourceLocation asyncLocation = tokens.consumeLocation(); // consume async
            return FunctionClassParser.parseFunctionDeclaration(parserContext, true, false, asyncLocation);
        } else {
            return parseExpressionStatement(parserContext);
        }
    }

    static BlockStatement parseBlockStatement(ParserContext parserContext) {
        return parseBlockStatement(parserContext, false);
    }

    /**
     * Parse a block.
     *
     * @param functionBody
     *            true for a class static block, whose declarations follow function-body rules rather than block rules
     * @return the block
     */
    static BlockStatement parseBlockStatement(ParserContext parserContext, boolean functionBody) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.LBRACE);

        if (tokens.match(TokenType.RBRACE)) {
            tokens.expect(TokenType.RBRACE);
            return new BlockStatement(List.of(), location);
        }

        List<Statement> body = new ArrayList<>();
        parseBlockStatements(parserContext, body);

        if (functionBody) {
            parserContext.declarations.validateFunctionBodyEarlyErrors(body);
        } else {
            parserContext.declarations.validateBlockEarlyErrors(body);
        }
        tokens.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    static BlockStatement parseBlockStatementAsBody(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        boolean hasUseStrict = parserContext.parseDirectives(body);

        parseFunctionStatements(parserContext, body);
        tokens.expect(TokenType.RBRACE);
        BlockStatement block = new BlockStatement(body, location);
        block.setHasUseStrictDirective(hasUseStrict);
        return block;
    }

    private static void parseBlockStatements(ParserContext parserContext, List<Statement> body) {
        while (!parserContext.tokens.match(TokenType.RBRACE) && !parserContext.tokens.match(TokenType.EOF)) {
            Statement stmt = parseStatement(parserContext);
            if (stmt != null) {
                body.add(stmt);
            }
        }
    }

    static Statement parseBreakStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.tokens.getLocation();
        parserContext.tokens.expect(TokenType.BREAK);
        return new BreakStatement(parseJumpLabel(parserContext), location);
    }

    static Statement parseContinueStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.tokens.getLocation();
        parserContext.tokens.expect(TokenType.CONTINUE);
        return new ContinueStatement(parseJumpLabel(parserContext), location);
    }

    private static Statement parseDebuggerStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.tokens.consumeLocation();
        parserContext.consumeSemicolon();
        return new DebuggerStatement(location);
    }

    static Statement parseDoWhileStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.DO);
        Statement body = parseIterationBody(parserContext, "do-while");
        tokens.expect(TokenType.WHILE);
        tokens.expect(TokenType.LPAREN);
        Expression test = ExpressionAssignmentParser.parseExpression(parserContext);
        tokens.expect(TokenType.RPAREN);
        tokens.consumeIf(TokenType.SEMICOLON);
        return new DoWhileStatement(body, test, location);
    }

    static Statement parseExpressionStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.tokens.getLocation();
        Expression expression = ExpressionAssignmentParser.parseExpression(parserContext);
        if (expression instanceof ObjectExpression objectExpression && containsCoverInitializedName(objectExpression)) {
            throw new JSSyntaxErrorException("Invalid shorthand property initializer");
        }
        parserContext.consumeSemicolon();
        return new ExpressionStatement(expression, location);
    }

    private static VariableDeclaration parseForDeclaration(ParserContext parserContext, boolean using) {
        TokenCursor tokens = parserContext.tokens;
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
            return parseVariableDeclarationBody(parserContext, kind, location, false);
        } finally {
            parserContext.inOperatorAllowed = savedIn;
        }
    }

    private static Statement parseForEach(ParserContext parserContext, ASTNode left, boolean isAwait,
            SourceLocation location) {
        TokenCursor tokens = parserContext.tokens;
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
                validateForInDeclarationInitializers(parserContext, declaration);
            }
        } else {
            validateForTarget(parserContext, (Expression) left, tokens.current().offset());
        }
        tokens.advance();
        Expression right = of
                ? ExpressionAssignmentParser.parseAssignmentExpression(parserContext)
                : ExpressionAssignmentParser.parseExpression(parserContext);
        tokens.expect(TokenType.RPAREN);
        Statement body = parseIterationBody(parserContext, kind);
        if (left instanceof VariableDeclaration declaration) {
            parserContext.declarations.validateForInOfDeclarationBodyEarlyErrors(declaration, body);
        }
        return of
                ? new ForOfStatement(left, right, body, isAwait, location)
                : new ForInStatement(left, right, body, location);
    }

    static Statement parseForStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
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

        boolean using = isUsingDeclarationStartInForHead(parserContext);
        Statement init = null;
        if (using || tokens.isAwaitUsingDeclarationStart() || tokens.match(TokenType.VAR)
                || tokens.match(TokenType.CONST)
                || (tokens.match(TokenType.LET) && shouldParseLetAsLexicalDeclarationInForHead(parserContext))) {
            VariableDeclaration declaration = parseForDeclaration(parserContext, using);
            if (tokens.match(TokenType.OF) || tokens.match(TokenType.IN)) {
                return parseForEach(parserContext, declaration, isAwait, location);
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
                expr = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);
            } finally {
                parserContext.inOperatorAllowed = savedIn;
            }
            if (expr instanceof BinaryExpression binary && binary.getOperator() == BinaryOperator.IN
                    && tokens.match(TokenType.RPAREN)) {
                requireSynchronousFor(isAwait);
                int offset = binary.getLocation() != null ? binary.getLocation().offset() : tokens.current().offset();
                validateForTarget(parserContext, binary.getLeft(), offset);
                tokens.expect(TokenType.RPAREN);
                return new ForInStatement(binary.getLeft(), binary.getRight(),
                        parseIterationBody(parserContext, "for-in"), location);
            }
            if (tokens.match(TokenType.IN) || tokens.match(TokenType.OF)) {
                if (!isAwait && tokens.match(TokenType.OF) && (first == TokenType.LET || (first == TokenType.ASYNC
                        && expr instanceof Identifier id && JSKeyword.ASYNC.equals(id.getName())))) {
                    throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
                }
                return parseForEach(parserContext, expr, isAwait, location);
            }
            requireSynchronousFor(isAwait);
            while (tokens.consumeIf(TokenType.COMMA)) {
                Expression right = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);
                expr = new SequenceExpression(List.of(expr, right), expr.getLocation());
            }
            init = new ExpressionStatement(expr, expr.getLocation());
            parserContext.consumeSemicolon();
        }

        // Test
        Expression test = null;
        if (!tokens.match(TokenType.SEMICOLON)) {
            test = ExpressionAssignmentParser.parseExpression(parserContext);
        }
        tokens.expect(TokenType.SEMICOLON);

        // Update
        Expression update = null;
        if (!tokens.match(TokenType.RPAREN)) {
            update = ExpressionAssignmentParser.parseExpression(parserContext);
        }
        tokens.expect(TokenType.RPAREN);

        Statement body = parseIterationBody(parserContext, "for");
        if (init instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
            parserContext.declarations.validateForInOfDeclarationBodyEarlyErrors(varDecl, body);
        }

        return new ForStatement(init, test, update, body, location);
    }

    static void parseFunctionStatements(ParserContext parserContext, List<Statement> body) {
        Deque<Set<String>> enclosingLabels = parserContext.enterFunctionLabelScope();
        try {
            parseBlockStatements(parserContext, body);
        } finally {
            parserContext.exitFunctionLabelScope(enclosingLabels);
        }
        parserContext.declarations.validateFunctionBodyEarlyErrors(body);
    }

    static Statement parseIfStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.IF);
        tokens.expect(TokenType.LPAREN);
        Expression test = ExpressionAssignmentParser.parseExpression(parserContext);
        tokens.expect(TokenType.RPAREN);

        Statement consequent = parseStatement(parserContext);
        validateNoLexicalDeclarationInStatementPosition(consequent);
        validateNoUsingDeclarationWithInitializerInStatementPosition(consequent);
        validateNoLabelledFunctionInIterationBody(consequent);
        validateFunctionInStatementPosition(parserContext, consequent, "if");
        Statement alternate = null;

        if (tokens.consumeIf(TokenType.ELSE)) {
            alternate = parseStatement(parserContext);
            validateNoLexicalDeclarationInStatementPosition(alternate);
            validateNoUsingDeclarationWithInitializerInStatementPosition(alternate);
            validateNoLabelledFunctionInIterationBody(alternate);
            validateFunctionInStatementPosition(parserContext, alternate, "if");
        }

        return new IfStatement(test, consequent, alternate, location);
    }

    private static Statement parseIterationBody(ParserContext parserContext, String kind) {
        Statement body = parseStatement(parserContext);
        validateNoLabelledFunctionInIterationBody(body);
        validateNoFunctionDeclarationInIterationBody(body, kind);
        validateNoLexicalDeclarationInStatementPosition(body);
        validateNoUsingDeclarationWithInitializerInStatementPosition(body);
        return body;
    }

    private static Identifier parseJumpLabel(ParserContext parserContext) {
        Identifier label = null;
        if (parserContext.tokens.current().type().isIdentifierCandidate() && !parserContext.tokens.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return label;
    }

    /**
     * Parse a labeled statement: label: statement Following QuickJS js_parse_statement_or_decl label handling. In
     * non-strict mode, labeled function declarations are allowed (Annex B).
     */
    static Statement parseLabeledStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.tokens.getLocation();
        Identifier label = parserContext.parseIdentifier();
        // ES2024 14.13.1: It is a Syntax Error if any source text is matched by
        // this production that also matches the LabelIdentifier of an enclosing LabelledStatement.
        for (Set<String> labels : parserContext.labelStack) {
            if (labels.contains(label.getName())) {
                throw new JSSyntaxErrorException("Label '" + label.getName() + "' has already been declared");
            }
        }
        parserContext.tokens.expect(TokenType.COLON);
        Set<String> currentLabels = new HashSet<>();
        currentLabels.add(label.getName());
        parserContext.labelStack.push(currentLabels);
        try {
            Statement body = parseStatement(parserContext);
            validateNoLexicalDeclarationInStatementPosition(body);
            validateNoUsingDeclarationWithInitializerInStatementPosition(body);
            validateFunctionInStatementPosition(parserContext, body, "labelled");
            return new LabeledStatement(label, body, location);
        } finally {
            parserContext.labelStack.pop();
        }
    }

    static Statement parseReturnStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        // Return is only valid inside function bodies.
        if (parserContext.inClassStaticInit || parserContext.functionNesting == 0) {
            throw new JSSyntaxErrorException("return not in a function");
        }
        tokens.expect(TokenType.RETURN);

        Expression argument = null;
        if (!tokens.hasNewlineBefore() && !tokens.match(TokenType.SEMICOLON) && !tokens.match(TokenType.RBRACE)
                && !tokens.match(TokenType.EOF)) {
            argument = ExpressionAssignmentParser.parseExpression(parserContext);
        }

        parserContext.consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    static Statement parseStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        parserContext.statementNesting++;
        try {
            if (tokens.isAwaitUsingDeclarationStart()) {
                validateAwaitUsingDeclarationPlacement(parserContext);
                return parseUsingDeclaration(parserContext, true);
            }
            if (tokens.isUsingDeclarationStart()) {
                return parseUsingDeclaration(parserContext, false);
            }
            if (isDebuggerKeyword(parserContext)) {
                return parseDebuggerStatement(parserContext);
            }
            if (isWithKeyword(parserContext)) {
                return parseWithStatement(parserContext);
            }
            // Check for labeled statement: identifier followed by ':'
            // Following QuickJS is_label() check
            if (isLabelStart(parserContext)) {
                return parseLabeledStatement(parserContext);
            }
            return switch (tokens.current().type()) {
                case IF -> parseIfStatement(parserContext);
                case DO -> parseDoWhileStatement(parserContext);
                case WHILE -> parseWhileStatement(parserContext);
                case FOR -> parseForStatement(parserContext);
                case RETURN -> parseReturnStatement(parserContext);
                case BREAK -> parseBreakStatement(parserContext);
                case CONTINUE -> parseContinueStatement(parserContext);
                case THROW -> parseThrowStatement(parserContext);
                case TRY -> parseTryStatement(parserContext);
                case SWITCH -> parseSwitchStatement(parserContext);
                case LBRACE -> parseBlockStatement(parserContext);
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
                            yield parseExpressionStatement(parserContext);
                        }
                    }
                    yield parseVariableDeclaration(parserContext);
                }
                case VAR, CONST -> parseVariableDeclaration(parserContext);
                case ASYNC -> // Async function declaration: async function f() {}
                    parseAsyncDeclaration(parserContext);
                case FUNCTION -> // Function declarations are treated as statements in JavaScript
                    FunctionClassParser.parseFunctionDeclaration(parserContext, false, false);
                case CLASS -> // Class declarations are treated as statements in JavaScript
                    FunctionClassParser.parseClassDeclaration(parserContext);
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
                    yield parseExpressionStatement(parserContext);
                }
                case SEMICOLON -> {
                    tokens.advance(); // consume semicolon
                    yield null; // empty statement
                }
                default -> parseExpressionStatement(parserContext);
            };
        } finally {
            parserContext.statementNesting--;
        }
    }

    static Statement parseSwitchStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.SWITCH);
        tokens.expect(TokenType.LPAREN);
        Expression discriminant = ExpressionAssignmentParser.parseExpression(parserContext);
        tokens.expect(TokenType.RPAREN);
        tokens.expect(TokenType.LBRACE);

        List<SwitchStatement.SwitchCase> cases = new ArrayList<>();
        boolean defaultClauseFound = false;

        while (!tokens.match(TokenType.RBRACE) && !tokens.match(TokenType.EOF)) {
            Expression test = null;
            if (tokens.consumeIf(TokenType.CASE)) {
                test = ExpressionAssignmentParser.parseExpression(parserContext);
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
                Statement stmt = parseStatement(parserContext);
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

    static Statement parseThrowStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.THROW);
        if (tokens.hasNewlineBefore()) {
            throw new JSSyntaxErrorException("Illegal newline after throw");
        }
        Expression argument = ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.consumeSemicolon();
        return new ThrowStatement(argument, location);
    }

    static Statement parseTryStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.TRY);
        BlockStatement block = parseBlockStatement(parserContext);

        TryStatement.CatchClause handler = null;
        if (tokens.consumeIf(TokenType.CATCH)) {
            Pattern param = null;
            if (tokens.consumeIf(TokenType.LPAREN)) {
                param = PatternParser.parsePattern(parserContext);
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
            BlockStatement catchBody = parseBlockStatement(parserContext);
            parserContext.declarations.validateCatchClauseEarlyErrors(param, catchBody);
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (tokens.consumeIf(TokenType.FINALLY)) {
            finalizer = parseBlockStatement(parserContext);
        }

        if (handler == null && finalizer == null) {
            throw new JSSyntaxErrorException("Missing catch or finally after try");
        }

        return new TryStatement(block, handler, finalizer, location);
    }

    static Statement parseUsingDeclaration(ParserContext parserContext, boolean isAwaitUsing) {
        TokenCursor tokens = parserContext.tokens;
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
        return parseVariableDeclarationBody(parserContext, kind, location);
    }

    static Statement parseVariableDeclaration(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        VariableKind kind = VariableKind.fromKeyword(tokens.current().value()); // VAR, LET, or CONST
        tokens.advance();
        return parseVariableDeclarationBody(parserContext, kind, location);
    }

    static Statement parseVariableDeclarationBody(ParserContext parserContext, VariableKind kind,
            SourceLocation location) {
        return parseVariableDeclarationBody(parserContext, kind, location, true);
    }

    static VariableDeclaration parseVariableDeclarationBody(ParserContext parserContext, VariableKind kind,
            SourceLocation location, boolean consumeSemi) {
        TokenCursor tokens = parserContext.tokens;
        boolean isUsingKind = kind == VariableKind.USING || kind == VariableKind.AWAIT_USING;
        List<VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (tokens.consumeIf(TokenType.COMMA)) {
            }

            Pattern id = PatternParser.parsePattern(parserContext);
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
                init = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);
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

    static Statement parseWhileStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.WHILE);
        tokens.expect(TokenType.LPAREN);
        Expression test = ExpressionAssignmentParser.parseExpression(parserContext);
        tokens.expect(TokenType.RPAREN);
        Statement body = parseIterationBody(parserContext, "while");

        return new WhileStatement(test, body, location);
    }

    static Statement parseWithStatement(ParserContext parserContext) {
        TokenCursor tokens = parserContext.tokens;
        SourceLocation location = tokens.getLocation();
        if (parserContext.strictMode) {
            throw new JSSyntaxErrorException("Strict mode code may not include a with statement");
        }
        if (!isWithKeyword(parserContext)) {
            throw new JSSyntaxErrorException("Unexpected token");
        }
        tokens.advance(); // consume 'with'
        tokens.expect(TokenType.LPAREN);
        Expression object = ExpressionAssignmentParser.parseExpression(parserContext);
        tokens.expect(TokenType.RPAREN);
        Statement body = parseStatement(parserContext);
        validateNoFunctionDeclarationInWithStatementPosition(body);
        validateNoLabelledFunctionInWithStatementPosition(body);
        validateNoLexicalDeclarationInStatementPosition(body);
        return new WithStatement(object, body, location);
    }

    private static void requireSynchronousFor(boolean isAwait) {
        if (isAwait) {
            throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
        }
    }

    private static boolean shouldParseLetAsLexicalDeclarationInForHead(ParserContext parserContext) {
        if (!parserContext.tokens.match(TokenType.LET)) {
            return true;
        }
        if (parserContext.strictMode) {
            return true;
        }
        Token lookahead = parserContext.tokens.next();
        if (lookahead == null) {
            return true;
        }
        return switch (lookahead.type()) {
            case IDENTIFIER, ASYNC, AWAIT, YIELD, FROM, OF, AS, LET, LBRACKET, LBRACE -> true;
            default -> false;
        };
    }

    private static void validateAwaitUsingDeclarationPlacement(ParserContext parserContext) {
        if (parserContext.moduleMode || parserContext.functionNesting > 0 || parserContext.statementNesting > 1
                || parserContext.inClassStaticInit || parserContext.classBodyNesting > 0) {
            return;
        }
        throw new JSSyntaxErrorException("using declarations are not allowed at the top level of scripts");
    }

    private static void validateForInDeclarationInitializers(ParserContext parserContext,
            VariableDeclaration variableDeclaration) {
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

    private static void validateForTarget(ParserContext parserContext, Expression left, int offset) {
        if (!parserContext.isValidForInOfTarget(left)) {
            throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
        }
        ExpressionAssignmentParser.validateForInOfAssignmentTarget(parserContext, left, offset);
    }

    private static void validateFunctionInStatementPosition(ParserContext parserContext, Statement statement,
            String position) {
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

    private static void validateNoFunctionDeclarationInIterationBody(Statement statement, String statementKind) {
        if (statement instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "Function declarations are not allowed in " + statementKind + " statement position");
        }
    }

    private static void validateNoFunctionDeclarationInWithStatementPosition(Statement statement) {
        if (statement instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in with statement position");
        }
    }

    private static void validateNoLabelledFunctionInIterationBody(Statement statement) {
        if (isLabelledFunction(statement)) {
            throw new JSSyntaxErrorException("Labelled functions are only allowed at body level");
        }
    }

    private static void validateNoLabelledFunctionInWithStatementPosition(Statement statement) {
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
