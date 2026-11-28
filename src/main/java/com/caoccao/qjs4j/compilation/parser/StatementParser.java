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
        return parserContext.currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.DEBUGGER.equals(parserContext.currentToken.value())
                && !parserContext.currentToken.escaped();
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
        Token lookaheadToken = parserContext.peek();
        if (lookaheadToken == null || lookaheadToken.type() != TokenType.COLON) {
            return false;
        }
        return parserContext.currentToken.type().isIdentifierCandidate();
    }

    private static boolean isUsingDeclarationStartInForHead(ParserContext parserContext) {
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

    private static boolean isWithKeyword(ParserContext parserContext) {
        return parserContext.currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.WITH.equals(parserContext.currentToken.value());
    }

    static Statement parseAsyncDeclaration(ParserContext parserContext) {
        // ES2024: [no LineTerminator here] between `async` and `function`.
        // If there's a line terminator, `async` is an identifier expression statement.
        if (parserContext.nextToken.type() == TokenType.FUNCTION
                && parserContext.nextToken.line() == parserContext.currentToken.line()) {
            SourceLocation asyncLocation = parserContext.consumeLocation(); // consume async
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
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);

        if (parserContext.match(TokenType.RBRACE)) {
            parserContext.expect(TokenType.RBRACE);
            return new BlockStatement(List.of(), location);
        }

        List<Statement> body = new ArrayList<>();
        parseBlockStatements(parserContext, body);

        if (functionBody) {
            parserContext.declarations.validateFunctionBodyEarlyErrors(body);
        } else {
            parserContext.declarations.validateBlockEarlyErrors(body);
        }
        parserContext.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    static BlockStatement parseBlockStatementAsBody(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        boolean hasUseStrict = parserContext.parseDirectives(body);

        parseFunctionStatements(parserContext, body);
        parserContext.expect(TokenType.RBRACE);
        BlockStatement block = new BlockStatement(body, location);
        block.setHasUseStrictDirective(hasUseStrict);
        return block;
    }

    private static void parseBlockStatements(ParserContext parserContext, List<Statement> body) {
        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Statement stmt = parseStatement(parserContext);
            if (stmt != null) {
                body.add(stmt);
            }
        }
    }

    static Statement parseBreakStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.BREAK);
        return new BreakStatement(parseJumpLabel(parserContext), location);
    }

    static Statement parseContinueStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.CONTINUE);
        return new ContinueStatement(parseJumpLabel(parserContext), location);
    }

    private static Statement parseDebuggerStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.consumeLocation();
        parserContext.consumeSemicolon();
        return new DebuggerStatement(location);
    }

    static Statement parseDoWhileStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.DO);
        Statement body = parseIterationBody(parserContext, "do-while");
        parserContext.expect(TokenType.WHILE);
        parserContext.expect(TokenType.LPAREN);
        Expression test = ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.expect(TokenType.RPAREN);
        parserContext.consumeIf(TokenType.SEMICOLON);
        return new DoWhileStatement(body, test, location);
    }

    static Statement parseExpressionStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        Expression expression = ExpressionAssignmentParser.parseExpression(parserContext);
        if (expression instanceof ObjectExpression objectExpression && containsCoverInitializedName(objectExpression)) {
            throw new JSSyntaxErrorException("Invalid shorthand property initializer");
        }
        parserContext.consumeSemicolon();
        return new ExpressionStatement(expression, location);
    }

    private static VariableDeclaration parseForDeclaration(ParserContext parserContext, boolean using) {
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
            return parseVariableDeclarationBody(parserContext, kind, location, false);
        } finally {
            parserContext.inOperatorAllowed = savedIn;
        }
    }

    private static Statement parseForEach(ParserContext parserContext, ASTNode left, boolean isAwait,
            SourceLocation location) {
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
            validateForTarget(parserContext, (Expression) left, parserContext.currentToken.offset());
        }
        parserContext.advance();
        Expression right = of
                ? ExpressionAssignmentParser.parseAssignmentExpression(parserContext)
                : ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.expect(TokenType.RPAREN);
        Statement body = parseIterationBody(parserContext, kind);
        if (left instanceof VariableDeclaration declaration) {
            parserContext.declarations.validateForInOfDeclarationBodyEarlyErrors(declaration, body);
        }
        return of
                ? new ForOfStatement(left, right, body, isAwait, location)
                : new ForInStatement(left, right, body, location);
    }

    static Statement parseForStatement(ParserContext parserContext) {
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

        boolean using = isUsingDeclarationStartInForHead(parserContext);
        Statement init = null;
        if (using || parserContext.isAwaitUsingDeclarationStart() || parserContext.match(TokenType.VAR)
                || parserContext.match(TokenType.CONST)
                || (parserContext.match(TokenType.LET) && shouldParseLetAsLexicalDeclarationInForHead(parserContext))) {
            VariableDeclaration declaration = parseForDeclaration(parserContext, using);
            if (parserContext.match(TokenType.OF) || parserContext.match(TokenType.IN)) {
                return parseForEach(parserContext, declaration, isAwait, location);
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
                expr = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);
            } finally {
                parserContext.inOperatorAllowed = savedIn;
            }
            if (expr instanceof BinaryExpression binary && binary.getOperator() == BinaryOperator.IN
                    && parserContext.match(TokenType.RPAREN)) {
                requireSynchronousFor(isAwait);
                int offset = binary.getLocation() != null
                        ? binary.getLocation().offset()
                        : parserContext.currentToken.offset();
                validateForTarget(parserContext, binary.getLeft(), offset);
                parserContext.expect(TokenType.RPAREN);
                return new ForInStatement(binary.getLeft(), binary.getRight(),
                        parseIterationBody(parserContext, "for-in"), location);
            }
            if (parserContext.match(TokenType.IN) || parserContext.match(TokenType.OF)) {
                if (!isAwait && parserContext.match(TokenType.OF)
                        && (first == TokenType.LET || (first == TokenType.ASYNC && expr instanceof Identifier id
                                && JSKeyword.ASYNC.equals(id.getName())))) {
                    throw new JSSyntaxErrorException("Invalid left-hand side in for-loop");
                }
                return parseForEach(parserContext, expr, isAwait, location);
            }
            requireSynchronousFor(isAwait);
            while (parserContext.consumeIf(TokenType.COMMA)) {
                Expression right = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);
                expr = new SequenceExpression(List.of(expr, right), expr.getLocation());
            }
            init = new ExpressionStatement(expr, expr.getLocation());
            parserContext.consumeSemicolon();
        }

        // Test
        Expression test = null;
        if (!parserContext.match(TokenType.SEMICOLON)) {
            test = ExpressionAssignmentParser.parseExpression(parserContext);
        }
        parserContext.expect(TokenType.SEMICOLON);

        // Update
        Expression update = null;
        if (!parserContext.match(TokenType.RPAREN)) {
            update = ExpressionAssignmentParser.parseExpression(parserContext);
        }
        parserContext.expect(TokenType.RPAREN);

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
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.IF);
        parserContext.expect(TokenType.LPAREN);
        Expression test = ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.expect(TokenType.RPAREN);

        Statement consequent = parseStatement(parserContext);
        validateNoLexicalDeclarationInStatementPosition(consequent);
        validateNoUsingDeclarationWithInitializerInStatementPosition(consequent);
        validateNoLabelledFunctionInIterationBody(consequent);
        validateFunctionInStatementPosition(parserContext, consequent, "if");
        Statement alternate = null;

        if (parserContext.consumeIf(TokenType.ELSE)) {
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
        if (parserContext.currentToken.type().isIdentifierCandidate() && !parserContext.hasNewlineBefore()) {
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
        SourceLocation location = parserContext.getLocation();
        // Return is only valid inside function bodies.
        if (parserContext.inClassStaticInit || parserContext.functionNesting == 0) {
            throw new JSSyntaxErrorException("return not in a function");
        }
        parserContext.expect(TokenType.RETURN);

        Expression argument = null;
        if (!parserContext.hasNewlineBefore() && !parserContext.match(TokenType.SEMICOLON)
                && !parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            argument = ExpressionAssignmentParser.parseExpression(parserContext);
        }

        parserContext.consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    static Statement parseStatement(ParserContext parserContext) {
        parserContext.statementNesting++;
        try {
            if (parserContext.isAwaitUsingDeclarationStart()) {
                validateAwaitUsingDeclarationPlacement(parserContext);
                return parseUsingDeclaration(parserContext, true);
            }
            if (parserContext.isUsingDeclarationStart()) {
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
            return switch (parserContext.currentToken.type()) {
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
                    Token nextToken = parserContext.peek();
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
                    parserContext.advance(); // consume semicolon
                    yield null; // empty statement
                }
                default -> parseExpressionStatement(parserContext);
            };
        } finally {
            parserContext.statementNesting--;
        }
    }

    static Statement parseSwitchStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.SWITCH);
        parserContext.expect(TokenType.LPAREN);
        Expression discriminant = ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.expect(TokenType.RPAREN);
        parserContext.expect(TokenType.LBRACE);

        List<SwitchStatement.SwitchCase> cases = new ArrayList<>();
        boolean defaultClauseFound = false;

        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Expression test = null;
            if (parserContext.consumeIf(TokenType.CASE)) {
                test = ExpressionAssignmentParser.parseExpression(parserContext);
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
                Statement stmt = parseStatement(parserContext);
                if (stmt != null) {
                    consequent.add(stmt);
                }
            }
            cases.add(new SwitchStatement.SwitchCase(test, consequent));
        }

        parserContext.expect(TokenType.RBRACE);
        parserContext.declarations.validateSwitchCaseBlockEarlyErrors(cases);
        return new SwitchStatement(discriminant, cases, location);
    }

    static Statement parseThrowStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.THROW);
        if (parserContext.hasNewlineBefore()) {
            throw new JSSyntaxErrorException("Illegal newline after throw");
        }
        Expression argument = ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.consumeSemicolon();
        return new ThrowStatement(argument, location);
    }

    static Statement parseTryStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.TRY);
        BlockStatement block = parseBlockStatement(parserContext);

        TryStatement.CatchClause handler = null;
        if (parserContext.consumeIf(TokenType.CATCH)) {
            Pattern param = null;
            if (parserContext.consumeIf(TokenType.LPAREN)) {
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
                parserContext.expect(TokenType.RPAREN);
            }
            BlockStatement catchBody = parseBlockStatement(parserContext);
            parserContext.declarations.validateCatchClauseEarlyErrors(param, catchBody);
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (parserContext.consumeIf(TokenType.FINALLY)) {
            finalizer = parseBlockStatement(parserContext);
        }

        if (handler == null && finalizer == null) {
            throw new JSSyntaxErrorException("Missing catch or finally after try");
        }

        return new TryStatement(block, handler, finalizer, location);
    }

    static Statement parseUsingDeclaration(ParserContext parserContext, boolean isAwaitUsing) {
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
        return parseVariableDeclarationBody(parserContext, kind, location);
    }

    static Statement parseVariableDeclaration(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        VariableKind kind = VariableKind.fromKeyword(parserContext.currentToken.value()); // VAR, LET, or CONST
        parserContext.advance();
        return parseVariableDeclarationBody(parserContext, kind, location);
    }

    static Statement parseVariableDeclarationBody(ParserContext parserContext, VariableKind kind,
            SourceLocation location) {
        return parseVariableDeclarationBody(parserContext, kind, location, true);
    }

    static VariableDeclaration parseVariableDeclarationBody(ParserContext parserContext, VariableKind kind,
            SourceLocation location, boolean consumeSemi) {
        boolean isUsingKind = kind == VariableKind.USING || kind == VariableKind.AWAIT_USING;
        List<VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (parserContext.consumeIf(TokenType.COMMA)) {
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

            if (parserContext.consumeIf(TokenType.ASSIGN)) {
                init = ExpressionAssignmentParser.parseAssignmentExpression(parserContext);
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

    static Statement parseWhileStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.WHILE);
        parserContext.expect(TokenType.LPAREN);
        Expression test = ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.expect(TokenType.RPAREN);
        Statement body = parseIterationBody(parserContext, "while");

        return new WhileStatement(test, body, location);
    }

    static Statement parseWithStatement(ParserContext parserContext) {
        SourceLocation location = parserContext.getLocation();
        if (parserContext.strictMode) {
            throw new JSSyntaxErrorException("Strict mode code may not include a with statement");
        }
        if (!isWithKeyword(parserContext)) {
            throw new JSSyntaxErrorException("Unexpected token");
        }
        parserContext.advance(); // consume 'with'
        parserContext.expect(TokenType.LPAREN);
        Expression object = ExpressionAssignmentParser.parseExpression(parserContext);
        parserContext.expect(TokenType.RPAREN);
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
