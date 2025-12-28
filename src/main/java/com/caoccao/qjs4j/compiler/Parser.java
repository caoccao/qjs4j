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

package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.compiler.ast.*;
import com.caoccao.qjs4j.compiler.ast.BinaryExpression.BinaryOperator;
import com.caoccao.qjs4j.compiler.ast.UnaryExpression.UnaryOperator;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for JavaScript.
 * Converts tokens into an Abstract Syntax Tree (AST).
 * <p>
 * Implements a subset of ECMAScript grammar including:
 * - Expressions (binary, unary, assignment, conditional, call, member access)
 * - Statements (if, while, for, return, break, continue, block, expression)
 * - Declarations (variable, function)
 * - Literals (number, string, boolean, null, undefined)
 * <p>
 * Uses operator precedence climbing for expression parsing.
 */
public final class Parser {
    private final Lexer lexer;
    private Token currentToken;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        this.currentToken = lexer.nextToken();
    }

    private void advance() {
        currentToken = lexer.nextToken();
    }

    // Statement parsing

    private void consumeSemicolon() {
        if (match(TokenType.SEMICOLON)) {
            advance();
        }
        // Otherwise, automatic semicolon insertion
    }

    private Token expect(TokenType type) {
        if (!match(type)) {
            throw new RuntimeException("Expected " + type + " but got " + currentToken.type() +
                    " at line " + currentToken.line() + ", column " + currentToken.column());
        }
        Token token = currentToken;
        advance();
        return token;
    }

    private SourceLocation getLocation() {
        return new SourceLocation(currentToken.line(), currentToken.column(), currentToken.offset());
    }

    private boolean isAssignmentOperator(TokenType type) {
        return type == TokenType.ASSIGN || type == TokenType.PLUS_ASSIGN ||
                type == TokenType.MINUS_ASSIGN || type == TokenType.MUL_ASSIGN ||
                type == TokenType.DIV_ASSIGN || type == TokenType.MOD_ASSIGN;
    }

    private boolean match(TokenType type) {
        return currentToken.type() == type;
    }

    /**
     * Parse the entire program.
     */
    public Program parse() {
        List<Statement> body = new ArrayList<>();
        SourceLocation location = getLocation();

        while (!match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        return new Program(body, false, location);
    }

    private Expression parseAdditiveExpression() {
        Expression left = parseMultiplicativeExpression();

        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            BinaryOperator op = match(TokenType.PLUS) ? BinaryOperator.ADD : BinaryOperator.SUB;
            SourceLocation location = getLocation();
            advance();
            Expression right = parseMultiplicativeExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    private Expression parseArrayExpression() {
        SourceLocation location = getLocation();
        expect(TokenType.LBRACKET);

        List<Expression> elements = new ArrayList<>();

        if (!match(TokenType.RBRACKET)) {
            do {
                if (match(TokenType.COMMA)) {
                    advance();
                    elements.add(null); // hole in array
                } else {
                    elements.add(parseExpression());
                    if (match(TokenType.COMMA)) {
                        advance();
                    }
                }
            } while (!match(TokenType.RBRACKET) && !match(TokenType.EOF));
        }

        expect(TokenType.RBRACKET);
        return new ArrayExpression(elements, location);
    }

    private ArrayPattern parseArrayPattern() {
        SourceLocation location = getLocation();
        expect(TokenType.LBRACKET);

        List<Pattern> elements = new ArrayList<>();

        while (!match(TokenType.RBRACKET) && !match(TokenType.EOF)) {
            if (!elements.isEmpty()) {
                expect(TokenType.COMMA);
                if (match(TokenType.RBRACKET)) {
                    break; // Trailing comma
                }
            }

            if (match(TokenType.COMMA)) {
                // Hole in array pattern: [a, , c]
                elements.add(null);
            } else {
                elements.add(parsePattern());
            }
        }

        expect(TokenType.RBRACKET);
        return new ArrayPattern(elements, location);
    }

    private Expression parseAssignmentExpression() {
        Expression left = parseConditionalExpression();

        if (isAssignmentOperator(currentToken.type())) {
            TokenType op = currentToken.type();
            SourceLocation location = getLocation();
            advance();
            Expression right = parseAssignmentExpression();

            AssignmentExpression.AssignmentOperator operator = switch (op) {
                case ASSIGN -> AssignmentExpression.AssignmentOperator.ASSIGN;
                case DIV_ASSIGN -> AssignmentExpression.AssignmentOperator.DIV_ASSIGN;
                case MINUS_ASSIGN -> AssignmentExpression.AssignmentOperator.MINUS_ASSIGN;
                case MOD_ASSIGN -> AssignmentExpression.AssignmentOperator.MOD_ASSIGN;
                case MUL_ASSIGN -> AssignmentExpression.AssignmentOperator.MUL_ASSIGN;
                case PLUS_ASSIGN -> AssignmentExpression.AssignmentOperator.PLUS_ASSIGN;
                default -> AssignmentExpression.AssignmentOperator.ASSIGN;
            };

            return new AssignmentExpression(left, operator, right, location);
        }

        return left;
    }

    private Expression parseBitwiseAndExpression() {
        Expression left = parseEqualityExpression();

        while (match(TokenType.BIT_AND)) {
            SourceLocation location = getLocation();
            advance();
            Expression right = parseEqualityExpression();
            left = new BinaryExpression(BinaryOperator.BIT_AND, left, right, location);
        }

        return left;
    }

    private Expression parseBitwiseOrExpression() {
        Expression left = parseBitwiseXorExpression();

        while (match(TokenType.BIT_OR)) {
            SourceLocation location = getLocation();
            advance();
            Expression right = parseBitwiseXorExpression();
            left = new BinaryExpression(BinaryOperator.BIT_OR, left, right, location);
        }

        return left;
    }

    private Expression parseBitwiseXorExpression() {
        Expression left = parseBitwiseAndExpression();

        while (match(TokenType.BIT_XOR)) {
            SourceLocation location = getLocation();
            advance();
            Expression right = parseBitwiseAndExpression();
            left = new BinaryExpression(BinaryOperator.BIT_XOR, left, right, location);
        }

        return left;
    }

    private BlockStatement parseBlockStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    // Expression parsing with precedence

    private Statement parseBreakStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.BREAK);
        consumeSemicolon();
        return new BreakStatement(null, location);
    }

    private Expression parseCallExpression() {
        Expression expr = parseMemberExpression();

        while (true) {
            if (match(TokenType.LPAREN)) {
                SourceLocation location = getLocation();
                advance();
                List<Expression> args = new ArrayList<>();

                if (!match(TokenType.RPAREN)) {
                    do {
                        if (match(TokenType.COMMA)) {
                            advance();
                        }
                        args.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }

                expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, location);
            } else if (match(TokenType.DOT)) {
                SourceLocation location = getLocation();
                advance();
                Identifier property = parseIdentifier();
                expr = new MemberExpression(expr, property, false, location);
            } else if (match(TokenType.LBRACKET)) {
                SourceLocation location = getLocation();
                advance();
                Expression property = parseExpression();
                expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, location);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression parseConditionalExpression() {
        Expression test = parseLogicalOrExpression();

        if (match(TokenType.QUESTION)) {
            SourceLocation location = getLocation();
            advance();
            Expression consequent = parseExpression();
            expect(TokenType.COLON);
            Expression alternate = parseExpression();

            return new ConditionalExpression(test, consequent, alternate, location);
        }

        return test;
    }

    private Statement parseContinueStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.CONTINUE);
        consumeSemicolon();
        return new ContinueStatement(null, location);
    }

    private Expression parseEqualityExpression() {
        Expression left = parseRelationalExpression();

        while (match(TokenType.EQ) || match(TokenType.NE) ||
                match(TokenType.STRICT_EQ) || match(TokenType.STRICT_NE)) {
            BinaryOperator op = switch (currentToken.type()) {
                case EQ -> BinaryOperator.EQ;
                case NE -> BinaryOperator.NE;
                case STRICT_EQ -> BinaryOperator.STRICT_EQ;
                case STRICT_NE -> BinaryOperator.STRICT_NE;
                default -> null;
            };
            SourceLocation location = getLocation();
            advance();
            Expression right = parseRelationalExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    private Expression parseExponentiationExpression() {
        Expression left = parseUnaryExpression();

        if (match(TokenType.EXP)) {
            SourceLocation location = getLocation();
            advance();
            Expression right = parseExponentiationExpression(); // right-associative
            return new BinaryExpression(BinaryOperator.EXP, left, right, location);
        }

        return left;
    }

    private Expression parseExpression() {
        return parseAssignmentExpression();
    }

    private Statement parseExpressionStatement() {
        SourceLocation location = getLocation();
        Expression expression = parseExpression();
        consumeSemicolon();
        return new ExpressionStatement(expression, location);
    }

    private Statement parseForStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.FOR);
        expect(TokenType.LPAREN);

        // Init (can be var declaration or expression)
        Statement init = null;
        if (!match(TokenType.SEMICOLON)) {
            if (match(TokenType.VAR) || match(TokenType.LET) || match(TokenType.CONST)) {
                init = parseVariableDeclaration();
            } else {
                init = parseExpressionStatement();
            }
        } else {
            advance(); // consume semicolon
        }

        // Test
        Expression test = null;
        if (!match(TokenType.SEMICOLON)) {
            test = parseExpression();
        }
        expect(TokenType.SEMICOLON);

        // Update
        Expression update = null;
        if (!match(TokenType.RPAREN)) {
            update = parseExpression();
        }
        expect(TokenType.RPAREN);

        Statement body = parseStatement();

        return new ForStatement(init, test, update, body, location);
    }

    private FunctionDeclaration parseFunctionDeclaration() {
        SourceLocation location = getLocation();
        expect(TokenType.FUNCTION);

        Identifier id = parseIdentifier();

        expect(TokenType.LPAREN);
        List<Identifier> params = new ArrayList<>();

        if (!match(TokenType.RPAREN)) {
            do {
                if (match(TokenType.COMMA)) {
                    advance();
                }
                params.add(parseIdentifier());
            } while (match(TokenType.COMMA));
        }

        expect(TokenType.RPAREN);
        BlockStatement body = parseBlockStatement();

        return new FunctionDeclaration(id, params, body, false, false, location);
    }

    private Expression parseFunctionExpression() {
        SourceLocation location = getLocation();
        expect(TokenType.FUNCTION);

        Identifier id = null;
        if (match(TokenType.IDENTIFIER)) {
            id = parseIdentifier();
        }

        expect(TokenType.LPAREN);
        List<Identifier> params = new ArrayList<>();

        if (!match(TokenType.RPAREN)) {
            do {
                if (match(TokenType.COMMA)) {
                    advance();
                }
                params.add(parseIdentifier());
            } while (match(TokenType.COMMA));
        }

        expect(TokenType.RPAREN);
        BlockStatement body = parseBlockStatement();

        return new FunctionExpression(id, params, body, false, false, location);
    }

    private Identifier parseIdentifier() {
        SourceLocation location = getLocation();
        String name = currentToken.value();
        expect(TokenType.IDENTIFIER);
        return new Identifier(name, location);
    }

    private Expression parsePropertyName() {
        SourceLocation location = getLocation();
        return switch (currentToken.type()) {
            case IDENTIFIER -> {
                String name = currentToken.value();
                advance();
                yield new Identifier(name, location);
            }
            case STRING -> {
                String value = currentToken.value();
                advance();
                yield new Literal(value, location);
            }
            case NUMBER -> {
                String value = currentToken.value();
                advance();
                // Numeric keys are converted to strings
                yield new Literal(value, location);
            }
            default -> throw new RuntimeException("Expected property name but got " + currentToken.type() +
                    " at line " + currentToken.line() + ", column " + currentToken.column());
        };
    }

    private Statement parseIfStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.IF);
        expect(TokenType.LPAREN);
        Expression test = parseExpression();
        expect(TokenType.RPAREN);

        Statement consequent = parseStatement();
        Statement alternate = null;

        if (match(TokenType.ELSE)) {
            advance();
            alternate = parseStatement();
        }

        return new IfStatement(test, consequent, alternate, location);
    }

    private Expression parseLogicalAndExpression() {
        Expression left = parseBitwiseOrExpression();

        while (match(TokenType.LOGICAL_AND)) {
            SourceLocation location = getLocation();
            advance();
            Expression right = parseBitwiseOrExpression();
            left = new BinaryExpression(BinaryOperator.LOGICAL_AND, left, right, location);
        }

        return left;
    }

    private Expression parseLogicalOrExpression() {
        Expression left = parseLogicalAndExpression();

        while (match(TokenType.LOGICAL_OR) || match(TokenType.NULLISH_COALESCING)) {
            BinaryOperator op = match(TokenType.LOGICAL_OR) ?
                    BinaryOperator.LOGICAL_OR : BinaryOperator.NULLISH_COALESCING;
            SourceLocation location = getLocation();
            advance();
            Expression right = parseLogicalAndExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    private Expression parseMemberExpression() {
        if (match(TokenType.NEW)) {
            SourceLocation location = getLocation();
            advance();
            Expression callee = parseMemberExpression();

            List<Expression> args = new ArrayList<>();
            if (match(TokenType.LPAREN)) {
                advance();
                if (!match(TokenType.RPAREN)) {
                    do {
                        if (match(TokenType.COMMA)) {
                            advance();
                        }
                        args.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RPAREN);
            }

            return new NewExpression(callee, args, location);
        }

        return parsePrimaryExpression();
    }

    private Expression parseMultiplicativeExpression() {
        Expression left = parseExponentiationExpression();

        while (match(TokenType.MUL) || match(TokenType.DIV) || match(TokenType.MOD)) {
            BinaryOperator op = switch (currentToken.type()) {
                case DIV -> BinaryOperator.DIV;
                case MOD -> BinaryOperator.MOD;
                case MUL -> BinaryOperator.MUL;
                default -> null;
            };
            SourceLocation location = getLocation();
            advance();
            Expression right = parseExponentiationExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    private Expression parseObjectExpression() {
        SourceLocation location = getLocation();
        expect(TokenType.LBRACE);

        List<ObjectExpression.Property> properties = new ArrayList<>();

        while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            Expression key = parsePropertyName();
            expect(TokenType.COLON);
            Expression value = parseExpression();

            properties.add(new ObjectExpression.Property(key, value, "init", false, false));

            if (match(TokenType.COMMA)) {
                advance();
            } else {
                break;
            }
        }

        expect(TokenType.RBRACE);
        return new ObjectExpression(properties, location);
    }

    private ObjectPattern parseObjectPattern() {
        SourceLocation location = getLocation();
        expect(TokenType.LBRACE);

        List<ObjectPattern.Property> properties = new ArrayList<>();

        while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            if (!properties.isEmpty()) {
                expect(TokenType.COMMA);
                if (match(TokenType.RBRACE)) {
                    break; // Trailing comma
                }
            }

            Identifier key = parseIdentifier();
            Pattern value;
            boolean shorthand = false;

            if (match(TokenType.COLON)) {
                advance();
                value = parsePattern();
            } else {
                // Shorthand: { x } means { x: x }
                value = key;
                shorthand = true;
            }

            properties.add(new ObjectPattern.Property(key, value, shorthand));
        }

        expect(TokenType.RBRACE);
        return new ObjectPattern(properties, location);
    }

    private Pattern parsePattern() {
        if (match(TokenType.LBRACE)) {
            return parseObjectPattern();
        } else if (match(TokenType.LBRACKET)) {
            return parseArrayPattern();
        } else {
            return parseIdentifier();
        }
    }

    private Expression parsePostfixExpression() {
        Expression expr = parseCallExpression();

        if (match(TokenType.INC) || match(TokenType.DEC)) {
            UnaryOperator op = match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = getLocation();
            advance();
            return new UnaryExpression(op, expr, false, location);
        }

        return expr;
    }

    private Expression parsePrimaryExpression() {
        SourceLocation location = getLocation();

        return switch (currentToken.type()) {
            case NUMBER -> {
                String value = currentToken.value();
                advance();
                yield new Literal(Double.parseDouble(value), location);
            }
            case STRING -> {
                String value = currentToken.value();
                advance();
                yield new Literal(value, location);
            }
            case TRUE -> {
                advance();
                yield new Literal(true, location);
            }
            case FALSE -> {
                advance();
                yield new Literal(false, location);
            }
            case NULL -> {
                advance();
                yield new Literal(null, location);
            }
            case IDENTIFIER -> parseIdentifier();
            case THIS -> {
                advance();
                yield new Identifier("this", location);
            }
            case LPAREN -> {
                advance();
                Expression expr = parseExpression();
                expect(TokenType.RPAREN);
                yield expr;
            }
            case LBRACKET -> parseArrayExpression();
            case LBRACE -> parseObjectExpression();
            case FUNCTION -> parseFunctionExpression();
            default -> {
                // Error case - return a literal undefined
                advance();
                yield new Literal(null, location);
            }
        };
    }

    private Expression parseRelationalExpression() {
        Expression left = parseShiftExpression();

        while (match(TokenType.LT) || match(TokenType.LE) ||
                match(TokenType.GT) || match(TokenType.GE) ||
                match(TokenType.IN) || match(TokenType.INSTANCEOF)) {
            BinaryOperator op = switch (currentToken.type()) {
                case LT -> BinaryOperator.LT;
                case LE -> BinaryOperator.LE;
                case GT -> BinaryOperator.GT;
                case GE -> BinaryOperator.GE;
                case IN -> BinaryOperator.IN;
                case INSTANCEOF -> BinaryOperator.INSTANCEOF;
                default -> null;
            };
            SourceLocation location = getLocation();
            advance();
            Expression right = parseShiftExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    // Utility methods

    private Statement parseReturnStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.RETURN);

        Expression argument = null;
        if (!match(TokenType.SEMICOLON) && !match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            argument = parseExpression();
        }

        consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    private Expression parseShiftExpression() {
        Expression left = parseAdditiveExpression();

        while (match(TokenType.LSHIFT) || match(TokenType.RSHIFT) || match(TokenType.URSHIFT)) {
            BinaryOperator op = switch (currentToken.type()) {
                case LSHIFT -> BinaryOperator.LSHIFT;
                case RSHIFT -> BinaryOperator.RSHIFT;
                case URSHIFT -> BinaryOperator.URSHIFT;
                default -> null;
            };
            SourceLocation location = getLocation();
            advance();
            Expression right = parseAdditiveExpression();
            left = new BinaryExpression(op, left, right, location);
        }

        return left;
    }

    private Statement parseStatement() {
        return switch (currentToken.type()) {
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
            case FUNCTION -> {
                // Function declarations are treated as statements in JavaScript
                parseFunctionDeclaration();
                yield null; // Function declarations don't return a statement value
            }
            case SEMICOLON -> {
                advance(); // consume semicolon
                yield null; // empty statement
            }
            default -> parseExpressionStatement();
        };
    }

    private Statement parseSwitchStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.SWITCH);
        expect(TokenType.LPAREN);
        Expression discriminant = parseExpression();
        expect(TokenType.RPAREN);
        expect(TokenType.LBRACE);

        List<SwitchStatement.SwitchCase> cases = new ArrayList<>();

        while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            if (match(TokenType.CASE)) {
                advance();
                Expression test = parseExpression();
                expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!match(TokenType.CASE) && !match(TokenType.DEFAULT) &&
                        !match(TokenType.RBRACE) && !match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(test, consequent));
            } else if (match(TokenType.DEFAULT)) {
                advance();
                expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!match(TokenType.CASE) && !match(TokenType.DEFAULT) &&
                        !match(TokenType.RBRACE) && !match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(null, consequent));
            } else {
                advance(); // skip unexpected token
            }
        }

        expect(TokenType.RBRACE);
        return new SwitchStatement(discriminant, cases, location);
    }

    private Statement parseThrowStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.THROW);
        Expression argument = parseExpression();
        consumeSemicolon();
        return new ThrowStatement(argument, location);
    }

    private Statement parseTryStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.TRY);
        BlockStatement block = parseBlockStatement();

        TryStatement.CatchClause handler = null;
        if (match(TokenType.CATCH)) {
            advance();
            Identifier param = null;
            if (match(TokenType.LPAREN)) {
                advance();
                param = parseIdentifier();
                expect(TokenType.RPAREN);
            }
            BlockStatement catchBody = parseBlockStatement();
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (match(TokenType.FINALLY)) {
            advance();
            finalizer = parseBlockStatement();
        }

        return new TryStatement(block, handler, finalizer, location);
    }

    private Expression parseUnaryExpression() {
        if (match(TokenType.INC) || match(TokenType.DEC)) {
            UnaryOperator op = match(TokenType.INC) ? UnaryOperator.INC : UnaryOperator.DEC;
            SourceLocation location = getLocation();
            advance();
            Expression operand = parseUnaryExpression();
            return new UnaryExpression(op, operand, true, location);
        }

        if (match(TokenType.PLUS) || match(TokenType.MINUS) || match(TokenType.NOT) ||
                match(TokenType.BIT_NOT) || match(TokenType.TYPEOF) ||
                match(TokenType.VOID) || match(TokenType.DELETE)) {
            UnaryOperator op = switch (currentToken.type()) {
                case PLUS -> UnaryOperator.PLUS;
                case MINUS -> UnaryOperator.MINUS;
                case NOT -> UnaryOperator.NOT;
                case BIT_NOT -> UnaryOperator.BIT_NOT;
                case TYPEOF -> UnaryOperator.TYPEOF;
                case VOID -> UnaryOperator.VOID;
                case DELETE -> UnaryOperator.DELETE;
                default -> null;
            };
            SourceLocation location = getLocation();
            advance();
            Expression operand = parseUnaryExpression();
            return new UnaryExpression(op, operand, true, location);
        }

        return parsePostfixExpression();
    }

    private Statement parseVariableDeclaration() {
        SourceLocation location = getLocation();
        String kind = currentToken.value(); // "var", "let", or "const"
        advance();

        List<VariableDeclaration.VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (match(TokenType.COMMA)) {
                advance();
            }

            Pattern id = parsePattern();
            Expression init = null;

            if (match(TokenType.ASSIGN)) {
                advance();
                init = parseExpression();
            }

            declarations.add(new VariableDeclaration.VariableDeclarator(id, init));
        } while (match(TokenType.COMMA));

        consumeSemicolon();
        return new VariableDeclaration(declarations, kind, location);
    }

    private Statement parseWhileStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.WHILE);
        expect(TokenType.LPAREN);
        Expression test = parseExpression();
        expect(TokenType.RPAREN);
        Statement body = parseStatement();

        return new WhileStatement(test, body, location);
    }
}
