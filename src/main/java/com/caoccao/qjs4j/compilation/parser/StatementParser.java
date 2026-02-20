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
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate parser responsible for parsing JavaScript statements.
 * Handles control flow (if, while, for, switch, try), declarations (var, let, const, using),
 * and statement-level constructs (block, labeled, return, break, continue, throw).
 */
record StatementParser(ParserContext ctx, ParserDelegates delegates) {

    Statement parseAsyncDeclaration() {
        if (ctx.nextToken.type() == TokenType.FUNCTION) {
            SourceLocation asyncLocation = ctx.getLocation();
            ctx.advance(); // consume async
            return delegates.functions.parseFunctionDeclaration(true, false, asyncLocation);
        } else {
            // Otherwise parse as an expression statement (e.g. async () => 1).
            return parseExpressionStatement();
        }
    }

    BlockStatement parseBlockStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        ctx.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    Statement parseBreakStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.BREAK);
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (ctx.match(TokenType.IDENTIFIER) && !ctx.hasNewlineBefore()) {
            label = ctx.parseIdentifier();
        }
        ctx.consumeSemicolon();
        return new BreakStatement(label, location);
    }

    Statement parseContinueStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.CONTINUE);
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (ctx.match(TokenType.IDENTIFIER) && !ctx.hasNewlineBefore()) {
            label = ctx.parseIdentifier();
        }
        ctx.consumeSemicolon();
        return new ContinueStatement(label, location);
    }

    Statement parseExpressionStatement() {
        SourceLocation location = ctx.getLocation();
        Expression expression = delegates.expressions.parseExpression();
        ctx.consumeSemicolon();
        return new ExpressionStatement(expression, location);
    }

    Statement parseForStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.FOR);

        // Check for 'await' keyword (for await...of)
        boolean isAwait = false;
        if (ctx.match(TokenType.AWAIT)) {
            if (!ctx.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            isAwait = true;
            ctx.advance();
        }

        ctx.expect(TokenType.LPAREN);

        // Check if this is a for-of or for-in loop
        // We need to peek ahead to see if there's 'of' or 'in' after the variable declaration
        boolean isForOf = false;
        boolean isForIn = false;
        Statement parsedDecl = null;

        // Try to parse as variable declaration (without consuming semicolon,
        // since we need to check for 'of' or 'in' first)
        if (ctx.match(TokenType.VAR) || ctx.match(TokenType.LET) || ctx.match(TokenType.CONST)
                || ctx.isUsingDeclarationStart() || ctx.isAwaitUsingDeclarationStart()) {
            // Annex B: suppress 'in' as binary operator only for 'var' in non-strict mode
            // (allows for-in initializers: for (var a = expr in obj))
            boolean savedInOperatorAllowed = ctx.inOperatorAllowed;
            boolean isVar = ctx.match(TokenType.VAR);
            if (isVar && !ctx.strictMode) {
                ctx.inOperatorAllowed = false;
            }
            if (ctx.isAwaitUsingDeclarationStart()) {
                SourceLocation declLocation = ctx.getLocation();
                ctx.expect(TokenType.AWAIT);
                ctx.advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.AWAIT_USING, declLocation, false);
            } else if (ctx.isUsingDeclarationStart()) {
                SourceLocation declLocation = ctx.getLocation();
                ctx.advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.USING, declLocation, false);
            } else {
                SourceLocation declLocation = ctx.getLocation();
                VariableKind kind = VariableKind.fromKeyword(ctx.currentToken.value());
                ctx.advance();
                parsedDecl = parseVariableDeclarationBody(kind, declLocation, false);
            }
            ctx.inOperatorAllowed = savedInOperatorAllowed;
            // Check if next token is 'of' or 'in'
            if (ctx.match(TokenType.OF)) {
                isForOf = true;
            } else if (ctx.match(TokenType.IN)) {
                isForIn = true;
            }
        }

        if (isForOf) {
            // This is a for-of loop: for (let x of iterable)
            ctx.expect(TokenType.OF);
            Expression iterable = delegates.expressions.parseExpression();
            ctx.expect(TokenType.RPAREN);
            Statement body = parseStatement();

            // parsedDecl should be a VariableDeclaration
            if (!(parsedDecl instanceof VariableDeclaration varDecl)) {
                throw new RuntimeException("Expected VariableDeclaration in for-of loop");
            }

            return new ForOfStatement(varDecl, iterable, body, isAwait, location);
        }

        if (isForIn) {
            // This is a for-in loop: for (var x in obj)
            ctx.expect(TokenType.IN);
            Expression object = delegates.expressions.parseExpression();
            ctx.expect(TokenType.RPAREN);
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
            ctx.expect(TokenType.SEMICOLON); // consume ; after init declaration
        } else if (!ctx.match(TokenType.SEMICOLON)) {
            if (ctx.match(TokenType.VAR) || ctx.match(TokenType.LET) || ctx.match(TokenType.CONST)
                    || ctx.isUsingDeclarationStart() || ctx.isAwaitUsingDeclarationStart()) {
                if (ctx.isAwaitUsingDeclarationStart()) {
                    init = parseUsingDeclaration(true);
                } else if (ctx.isUsingDeclarationStart()) {
                    init = parseUsingDeclaration(false);
                } else {
                    init = parseVariableDeclaration();
                }
            } else {
                // Parse expression -- could be for-in/for-of left side or traditional for init.
                // Parse as assignment expression first, then check for 'in' or 'of'.
                // Suppress 'in' as binary operator per ES spec [~In] grammar parameter.
                boolean savedInOperatorAllowed = ctx.inOperatorAllowed;
                ctx.inOperatorAllowed = false;
                Expression expr = delegates.expressions.parseAssignmentExpression();
                ctx.inOperatorAllowed = savedInOperatorAllowed;
                if (ctx.match(TokenType.IN)) {
                    // for (expr in obj) -- expression-based for-in
                    // Validate: left side must be a valid LeftHandSideExpression
                    if (!ctx.isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    ctx.advance(); // consume 'in'
                    Expression object = delegates.expressions.parseExpression();
                    ctx.expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    return new ForInStatement(expr, object, body, location);
                } else if (ctx.match(TokenType.OF)) {
                    // for (expr of iterable) -- expression-based for-of
                    if (!ctx.isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    ctx.advance(); // consume 'of'
                    Expression iterable = delegates.expressions.parseExpression();
                    ctx.expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    return new ForOfStatement(expr, iterable, body, isAwait, location);
                }
                // Traditional for loop init -- handle comma expressions
                while (ctx.match(TokenType.COMMA)) {
                    ctx.advance();
                    Expression right = delegates.expressions.parseAssignmentExpression();
                    expr = new SequenceExpression(List.of(expr, right), expr.getLocation());
                }
                init = new ExpressionStatement(expr, expr.getLocation());
                ctx.consumeSemicolon();
            }
        } else {
            ctx.advance(); // consume semicolon
        }

        // Test
        Expression test = null;
        if (!ctx.match(TokenType.SEMICOLON)) {
            test = delegates.expressions.parseExpression();
        }
        ctx.expect(TokenType.SEMICOLON);

        // Update
        Expression update = null;
        if (!ctx.match(TokenType.RPAREN)) {
            update = delegates.expressions.parseExpression();
        }
        ctx.expect(TokenType.RPAREN);

        Statement body = parseStatement();

        return new ForStatement(init, test, update, body, location);
    }

    Statement parseIfStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.IF);
        ctx.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        ctx.expect(TokenType.RPAREN);

        Statement consequent = parseStatement();
        Statement alternate = null;

        if (ctx.match(TokenType.ELSE)) {
            ctx.advance();
            alternate = parseStatement();
        }

        return new IfStatement(test, consequent, alternate, location);
    }

    /**
     * Parse a labeled statement: label: statement
     * Following QuickJS js_parse_statement_or_decl label handling.
     * In non-strict mode, labeled function declarations are allowed (Annex B).
     */
    Statement parseLabeledStatement() {
        SourceLocation location = ctx.getLocation();
        Identifier label = ctx.parseIdentifier();
        ctx.expect(TokenType.COLON);
        Statement body = parseStatement();
        return new LabeledStatement(label, body, location);
    }

    Statement parseReturnStatement() {
        SourceLocation location = ctx.getLocation();
        // QuickJS: "return not in a function" error when return is in eval code at top level
        if (ctx.isEval && ctx.functionNesting == 0) {
            throw new JSSyntaxErrorException("return not in a function");
        }
        ctx.expect(TokenType.RETURN);

        Expression argument = null;
        if (!ctx.match(TokenType.SEMICOLON) && !ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            argument = delegates.expressions.parseExpression();
        }

        ctx.consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    Statement parseStatement() {
        if (ctx.isAwaitUsingDeclarationStart()) {
            return parseUsingDeclaration(true);
        }
        if (ctx.isUsingDeclarationStart()) {
            return parseUsingDeclaration(false);
        }
        // Check for labeled statement: identifier followed by ':'
        // Following QuickJS is_label() check
        if (ctx.currentToken.type() == TokenType.IDENTIFIER && ctx.peek() != null && ctx.peek().type() == TokenType.COLON) {
            return parseLabeledStatement();
        }
        return switch (ctx.currentToken.type()) {
            case IF -> parseIfStatement();
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
            case SEMICOLON -> {
                ctx.advance(); // consume semicolon
                yield null; // empty statement
            }
            default -> parseExpressionStatement();
        };
    }

    Statement parseSwitchStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.SWITCH);
        ctx.expect(TokenType.LPAREN);
        Expression discriminant = delegates.expressions.parseExpression();
        ctx.expect(TokenType.RPAREN);
        ctx.expect(TokenType.LBRACE);

        List<SwitchStatement.SwitchCase> cases = new ArrayList<>();

        while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            if (ctx.match(TokenType.CASE)) {
                ctx.advance();
                Expression test = delegates.expressions.parseExpression();
                ctx.expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!ctx.match(TokenType.CASE) && !ctx.match(TokenType.DEFAULT) &&
                        !ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(test, consequent));
            } else if (ctx.match(TokenType.DEFAULT)) {
                ctx.advance();
                ctx.expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!ctx.match(TokenType.CASE) && !ctx.match(TokenType.DEFAULT) &&
                        !ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(null, consequent));
            } else {
                ctx.advance(); // skip unexpected token
            }
        }

        ctx.expect(TokenType.RBRACE);
        return new SwitchStatement(discriminant, cases, location);
    }

    Statement parseThrowStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.THROW);
        Expression argument = delegates.expressions.parseExpression();
        ctx.consumeSemicolon();
        return new ThrowStatement(argument, location);
    }

    Statement parseTryStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.TRY);
        BlockStatement block = parseBlockStatement();

        TryStatement.CatchClause handler = null;
        if (ctx.match(TokenType.CATCH)) {
            ctx.advance();
            Pattern param = null;
            if (ctx.match(TokenType.LPAREN)) {
                ctx.advance();
                param = delegates.patterns.parsePattern();
                ctx.expect(TokenType.RPAREN);
            }
            BlockStatement catchBody = parseBlockStatement();
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (ctx.match(TokenType.FINALLY)) {
            ctx.advance();
            finalizer = parseBlockStatement();
        }

        return new TryStatement(block, handler, finalizer, location);
    }

    Statement parseUsingDeclaration(boolean isAwaitUsing) {
        SourceLocation location = ctx.getLocation();
        VariableKind kind;
        if (isAwaitUsing) {
            if (!ctx.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            ctx.expect(TokenType.AWAIT);
            if (!ctx.isUsingIdentifierToken(ctx.currentToken)) {
                throw new RuntimeException("Expected using declaration after await");
            }
            ctx.advance();
            kind = VariableKind.AWAIT_USING;
        } else {
            if (!ctx.isUsingIdentifierToken(ctx.currentToken)) {
                throw new RuntimeException("Expected using declaration");
            }
            ctx.advance();
            kind = VariableKind.USING;
        }
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclaration() {
        SourceLocation location = ctx.getLocation();
        VariableKind kind = VariableKind.fromKeyword(ctx.currentToken.value()); // VAR, LET, or CONST
        ctx.advance();
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclarationBody(VariableKind kind, SourceLocation location) {
        return parseVariableDeclarationBody(kind, location, true);
    }

    VariableDeclaration parseVariableDeclarationBody(VariableKind kind, SourceLocation location, boolean consumeSemi) {
        List<VariableDeclaration.VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (ctx.match(TokenType.COMMA)) {
                ctx.advance();
            }

            Pattern id = delegates.patterns.parsePattern();
            Expression init = null;

            if (ctx.match(TokenType.ASSIGN)) {
                ctx.advance();
                init = delegates.expressions.parseAssignmentExpression();
            }

            declarations.add(new VariableDeclaration.VariableDeclarator(id, init));
        } while (ctx.match(TokenType.COMMA));

        if (consumeSemi) {
            ctx.consumeSemicolon();
        }
        return new VariableDeclaration(declarations, kind, location);
    }

    Statement parseWhileStatement() {
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.WHILE);
        ctx.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        ctx.expect(TokenType.RPAREN);
        Statement body = parseStatement();

        return new WhileStatement(test, body, location);
    }
}
