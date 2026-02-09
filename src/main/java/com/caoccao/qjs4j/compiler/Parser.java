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
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.math.BigInteger;
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
    private Token nextToken; // Lookahead token

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        this.currentToken = lexer.nextToken();
        this.nextToken = lexer.nextToken();
    }

    private void advance() {
        currentToken = nextToken;
        nextToken = lexer.nextToken();
    }

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

    // Statement parsing

    private SourceLocation getLocation() {
        return new SourceLocation(currentToken.line(), currentToken.column(), currentToken.offset());
    }

    /**
     * Check if current token can trigger automatic semicolon insertion.
     * Following QuickJS list of tokens that allow ASI.
     */
    private boolean isASIToken() {
        return switch (currentToken.type()) {
            case NUMBER, STRING, IDENTIFIER,
                 INC, DEC, NULL, FALSE, TRUE,
                 IF, RETURN, VAR, THIS, DELETE, TYPEOF,
                 NEW, DO, WHILE, FOR, SWITCH, THROW,
                 TRY, FUNCTION, CLASS,
                 CONST, LET -> true;
            default -> false;
        };
    }

    private boolean isAssignmentOperator(TokenType type) {
        return type == TokenType.ASSIGN || type == TokenType.PLUS_ASSIGN ||
                type == TokenType.MINUS_ASSIGN || type == TokenType.MUL_ASSIGN ||
                type == TokenType.DIV_ASSIGN || type == TokenType.MOD_ASSIGN ||
                type == TokenType.EXP_ASSIGN || type == TokenType.LSHIFT_ASSIGN ||
                type == TokenType.RSHIFT_ASSIGN || type == TokenType.URSHIFT_ASSIGN ||
                type == TokenType.AND_ASSIGN || type == TokenType.OR_ASSIGN ||
                type == TokenType.XOR_ASSIGN || type == TokenType.LOGICAL_AND_ASSIGN ||
                type == TokenType.LOGICAL_OR_ASSIGN || type == TokenType.NULLISH_ASSIGN;
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

        // Parse directives (like "use strict") at the beginning
        boolean strict = parseDirectives();

        while (!match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        return new Program(body, false, strict, location);
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
                } else if (match(TokenType.ELLIPSIS)) {
                    // Spread element: ...expr
                    SourceLocation spreadLocation = getLocation();
                    advance(); // consume ELLIPSIS
                    Expression argument = parseAssignmentExpression();
                    elements.add(new SpreadElement(argument, spreadLocation));
                    if (match(TokenType.COMMA)) {
                        advance();
                    }
                } else {
                    elements.add(parseAssignmentExpression());
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
            } else if (match(TokenType.ELLIPSIS)) {
                // Rest element: [a, ...rest]
                SourceLocation restLocation = getLocation();
                advance(); // consume '...'
                Pattern argument = parsePattern();
                elements.add(new RestElement(argument, restLocation));

                // Rest element must be last
                if (match(TokenType.COMMA)) {
                    throw new RuntimeException("Rest element must be last in array pattern at line " +
                            currentToken.line() + ", column " + currentToken.column());
                }
                break;
            } else {
                elements.add(parsePattern());
            }
        }

        expect(TokenType.RBRACKET);
        return new ArrayPattern(elements, location);
    }

    private Expression parseAssignmentExpression() {
        SourceLocation location = getLocation();

        // Check for async arrow function: async () => {} or async (params) => {}
        if (match(TokenType.ASYNC)) {
            int savedPos = currentToken.offset();
            advance(); // consume 'async'

            // Check if followed by ( or identifier (for single param)
            if (match(TokenType.LPAREN) || match(TokenType.IDENTIFIER)) {
                // Try to parse as arrow function
                boolean isArrow = false;

                if (match(TokenType.LPAREN)) {
                    // Could be async (params) => or just async followed by something else
                    // We need to look ahead to see if there's a =>
                    int parenDepth = 0;
                    int lookAheadPos = 0;
                    Token lookAhead = currentToken;

                    // Simple lookahead: scan for matching paren and then check for =>
                    // This is a simplified implementation
                    if (match(TokenType.LPAREN)) {
                        advance(); // consume '('

                        // Parse parameters using the same function parameter parser
                        FunctionParams funcParams = parseFunctionParameters();

                        // Check for arrow
                        if (match(TokenType.ARROW)) {
                            advance(); // consume '=>'

                            // Parse body
                            ASTNode body;
                            if (match(TokenType.LBRACE)) {
                                body = parseBlockStatement();
                            } else {
                                // Expression body
                                body = parseAssignmentExpression();
                            }

                            // Update location to include end offset
                            SourceLocation fullLocation = new SourceLocation(
                                    location.line(),
                                    location.column(),
                                    location.offset(),
                                    currentToken.offset()
                            );

                            return new ArrowFunctionExpression(funcParams.params, funcParams.restParameter, body, true, fullLocation);
                        }
                    }
                }
            }

            // Not an arrow function, backtrack and parse normally
            // This is tricky - we've already consumed 'async'
            // For now, throw an error as proper backtracking requires lexer support
            throw new RuntimeException("Expected arrow function after 'async'");
        }

        Expression left = parseConditionalExpression();

        // After parsing conditional expression, check if it's actually an arrow function
        // Pattern: identifier => expr  OR  (params) => expr
        if (match(TokenType.ARROW)) {
            // Convert the parsed expression to arrow function parameters
            List<Identifier> params = new ArrayList<>();
            RestParameter restParameter = null;

            if (left instanceof Identifier) {
                // Single parameter without parentheses: x => x + 1
                params.add((Identifier) left);
            } else if (left instanceof ArrayExpression arrayExpr) {
                // ArrayExpression is used as a marker for:
                // 1. Empty parameter list: () => expr
                // 2. Multiple parameters: (x, y, z) => expr
                // 3. Parameters with rest: (x, ...rest) => expr
                if (arrayExpr.elements().isEmpty()) {
                    // Empty parameter list
                    // params stays empty
                } else {
                    // Extract parameters and check for rest parameter
                    for (int i = 0; i < arrayExpr.elements().size(); i++) {
                        Expression expr = arrayExpr.elements().get(i);

                        if (expr instanceof SpreadElement spreadElem) {
                            // Rest parameter
                            if (spreadElem.argument() instanceof Identifier restId) {
                                restParameter = new RestParameter(restId, spreadElem.getLocation());

                                // Rest parameter must be last
                                if (i != arrayExpr.elements().size() - 1) {
                                    throw new RuntimeException("Rest parameter must be last at line " +
                                            currentToken.line() + ", column " + currentToken.column());
                                }
                            } else {
                                throw new RuntimeException("Invalid rest parameter at line " +
                                        currentToken.line() + ", column " + currentToken.column());
                            }
                        } else if (expr instanceof Identifier) {
                            params.add((Identifier) expr);
                        } else {
                            throw new RuntimeException("Invalid arrow function parameter at line " +
                                    currentToken.line() + ", column " + currentToken.column());
                        }
                    }
                }
            } else {
                // Could be other complex cases that we don't support yet
                // For simplicity, we'll throw an error
                throw new RuntimeException("Unsupported arrow function parameters at line " +
                        currentToken.line() + ", column " + currentToken.column());
            }

            advance(); // consume '=>'

            // Parse body
            ASTNode body;
            if (match(TokenType.LBRACE)) {
                body = parseBlockStatement();
            } else {
                // Expression body
                body = parseAssignmentExpression();
            }

            // Update location to include end offset
            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    currentToken.offset()
            );

            return new ArrowFunctionExpression(params, restParameter, body, false, fullLocation);
        }

        if (isAssignmentOperator(currentToken.type())) {
            TokenType op = currentToken.type();
            location = getLocation();
            advance();
            Expression right = parseAssignmentExpression();

            AssignmentExpression.AssignmentOperator operator = switch (op) {
                case ASSIGN -> AssignmentExpression.AssignmentOperator.ASSIGN;
                case AND_ASSIGN -> AssignmentExpression.AssignmentOperator.AND_ASSIGN;
                case DIV_ASSIGN -> AssignmentExpression.AssignmentOperator.DIV_ASSIGN;
                case EXP_ASSIGN -> AssignmentExpression.AssignmentOperator.EXP_ASSIGN;
                case LOGICAL_AND_ASSIGN -> AssignmentExpression.AssignmentOperator.LOGICAL_AND_ASSIGN;
                case LOGICAL_OR_ASSIGN -> AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN;
                case LSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.LSHIFT_ASSIGN;
                case MINUS_ASSIGN -> AssignmentExpression.AssignmentOperator.MINUS_ASSIGN;
                case MOD_ASSIGN -> AssignmentExpression.AssignmentOperator.MOD_ASSIGN;
                case MUL_ASSIGN -> AssignmentExpression.AssignmentOperator.MUL_ASSIGN;
                case NULLISH_ASSIGN -> AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN;
                case OR_ASSIGN -> AssignmentExpression.AssignmentOperator.OR_ASSIGN;
                case PLUS_ASSIGN -> AssignmentExpression.AssignmentOperator.PLUS_ASSIGN;
                case RSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.RSHIFT_ASSIGN;
                case URSHIFT_ASSIGN -> AssignmentExpression.AssignmentOperator.URSHIFT_ASSIGN;
                case XOR_ASSIGN -> AssignmentExpression.AssignmentOperator.XOR_ASSIGN;
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

    private Statement parseBreakStatement() {
        SourceLocation location = getLocation();
        expect(TokenType.BREAK);
        consumeSemicolon();
        return new BreakStatement(null, location);
    }

    private Expression parseCallExpression() {
        Expression expr = parseMemberExpression();

        while (true) {
            if (match(TokenType.TEMPLATE)) {
                // Tagged template: expr`template`
                SourceLocation location = getLocation();
                TemplateLiteral template = parseTemplateLiteral(true);
                expr = new TaggedTemplateExpression(expr, template, location);
            } else if (match(TokenType.LPAREN)) {
                SourceLocation location = getLocation();
                advance();
                List<Expression> args = new ArrayList<>();

                if (!match(TokenType.RPAREN)) {
                    do {
                        if (match(TokenType.COMMA)) {
                            advance();
                            // Handle trailing comma
                            if (match(TokenType.RPAREN)) {
                                break;
                            }
                        }
                        if (match(TokenType.ELLIPSIS)) {
                            // Spread argument: ...expr
                            SourceLocation spreadLocation = getLocation();
                            advance(); // consume ELLIPSIS
                            Expression argument = parseAssignmentExpression();
                            args.add(new SpreadElement(argument, spreadLocation));
                        } else {
                            args.add(parseAssignmentExpression());
                        }
                    } while (match(TokenType.COMMA));
                }

                expect(TokenType.RPAREN);
                expr = new CallExpression(expr, args, location);
            } else if (match(TokenType.DOT)) {
                SourceLocation location = getLocation();
                advance();
                Expression property = parsePropertyName();
                expr = new MemberExpression(expr, property, false, location);
            } else if (match(TokenType.LBRACKET)) {
                SourceLocation location = getLocation();
                advance();
                Expression property = parseAssignmentExpression();
                expect(TokenType.RBRACKET);
                expr = new MemberExpression(expr, property, true, location);
            } else {
                break;
            }
        }

        return expr;
    }

    /**
     * Parse a class declaration or expression.
     * Syntax: class Name extends Super { body }
     */
    private ClassDeclaration parseClassDeclaration() {
        SourceLocation startLocation = getLocation();
        int startOffset = currentToken.offset();
        expect(TokenType.CLASS);

        // Parse optional class name
        Identifier id = null;
        if (match(TokenType.IDENTIFIER)) {
            String name = currentToken.value();
            advance();
            id = new Identifier(name, startLocation);
        }

        // Parse optional extends clause
        Expression superClass = null;
        if (match(TokenType.EXTENDS)) {
            advance();
            superClass = parseMemberExpression();
        }

        // Parse class body
        expect(TokenType.LBRACE);
        List<ClassDeclaration.ClassElement> body = new ArrayList<>();

        while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            // Skip empty semicolons
            if (match(TokenType.SEMICOLON)) {
                advance();
                continue;
            }

            ClassDeclaration.ClassElement element = parseClassElement();
            if (element != null) {
                body.add(element);
            }
        }

        // Capture the end position before advancing past the closing brace
        int endOffset = currentToken.offset() + currentToken.value().length();
        expect(TokenType.RBRACE);

        SourceLocation location = new SourceLocation(
                startLocation.line(),
                startLocation.column(),
                startOffset,
                endOffset
        );

        return new ClassDeclaration(id, superClass, body, location);
    }

    /**
     * Parse a single class element (method, field, or static block).
     */
    private ClassDeclaration.ClassElement parseClassElement() {
        boolean isStatic = false;
        boolean isPrivate = false;
        SourceLocation location = getLocation();

        // Check for 'static' keyword
        if (match(TokenType.IDENTIFIER) && "static".equals(currentToken.value())) {
            advance();
            isStatic = true;

            // Check for static block: static { }
            if (match(TokenType.LBRACE)) {
                return parseStaticBlock();
            }

            // Handle 'static' as a property name (e.g., static = 42;)
            if (match(TokenType.ASSIGN) || match(TokenType.SEMICOLON)) {
                isStatic = false;
                // Parse as field with name "static"
                Expression key = new Identifier("static", location);
                Expression value = null;
                if (match(TokenType.ASSIGN)) {
                    advance();
                    value = parseAssignmentExpression();
                }
                consumeSemicolon();
                return new ClassDeclaration.PropertyDefinition(key, value, false, isStatic, false);
            }
        }

        // Check for private identifier (#name)
        if (match(TokenType.PRIVATE_NAME)) {
            isPrivate = true;
            String privateName = currentToken.value();
            // Remove the # prefix for the identifier
            String name = privateName.substring(1);
            advance();

            Expression key = new PrivateIdentifier(name, location);
            return parseMethodOrField(key, isStatic, isPrivate, true, location);
        }

        // Check for computed property name [expr]
        if (match(TokenType.LBRACKET)) {
            advance();
            Expression key = parseAssignmentExpression();
            expect(TokenType.RBRACKET);
            return parseMethodOrField(key, isStatic, isPrivate, true, location);
        }

        // Check for getter/setter
        if (match(TokenType.IDENTIFIER)) {
            String name = currentToken.value();
            Token nextToken = lexer.peekToken();
            if (("get".equals(name) || "set".equals(name)) &&
                    nextToken.type() != TokenType.LPAREN &&
                    nextToken.type() != TokenType.ASSIGN &&
                    nextToken.type() != TokenType.SEMICOLON) {
                String kind = name;
                advance(); // consume 'get' or 'set'

                Expression key;
                boolean computed = false;

                // Parse property name after get/set
                if (match(TokenType.PRIVATE_NAME)) {
                    String privateName = currentToken.value();
                    String keyName = privateName.substring(1);
                    key = new PrivateIdentifier(keyName, getLocation());
                    isPrivate = true;
                    advance();
                } else if (match(TokenType.LBRACKET)) {
                    advance();
                    key = parseAssignmentExpression();
                    expect(TokenType.RBRACKET);
                    computed = true;
                } else if (match(TokenType.IDENTIFIER) || match(TokenType.STRING)) {
                    key = new Identifier(currentToken.value(), getLocation());
                    advance();
                } else {
                    throw new RuntimeException("Expected property name after '" + kind + "'");
                }

                FunctionExpression method = parseMethod(kind);
                return new ClassDeclaration.MethodDefinition(key, method, kind, computed, isStatic, isPrivate);
            }
        }

        // Regular property name (identifier, string, number)
        Expression key;
        boolean computed = false;

        if (match(TokenType.IDENTIFIER)) {
            key = new Identifier(currentToken.value(), location);
            advance();
        } else if (match(TokenType.STRING)) {
            key = new Literal(currentToken.value(), location);
            advance();
        } else if (match(TokenType.NUMBER)) {
            key = new Literal(Double.parseDouble(currentToken.value()), location);
            advance();
        } else {
            throw new RuntimeException("Expected property name");
        }

        return parseMethodOrField(key, isStatic, isPrivate, computed, location);
    }

    // Expression parsing with precedence

    /**
     * Parse a class expression (class used as an expression).
     * Syntax: class [Name] [extends Super] { body }
     */
    private ClassExpression parseClassExpression() {
        SourceLocation startLocation = getLocation();
        int startOffset = currentToken.offset();
        expect(TokenType.CLASS);

        // Parse optional class name (class expressions can be anonymous)
        Identifier id = null;
        if (match(TokenType.IDENTIFIER)) {
            String name = currentToken.value();
            advance();
            id = new Identifier(name, startLocation);
        }

        // Parse optional extends clause
        Expression superClass = null;
        if (match(TokenType.EXTENDS)) {
            advance();
            superClass = parseMemberExpression();
        }

        // Parse class body
        expect(TokenType.LBRACE);
        List<ClassDeclaration.ClassElement> body = new ArrayList<>();

        while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            // Skip empty semicolons
            if (match(TokenType.SEMICOLON)) {
                advance();
                continue;
            }

            ClassDeclaration.ClassElement element = parseClassElement();
            if (element != null) {
                body.add(element);
            }
        }

        // Capture the end position before advancing past the closing brace
        int endOffset = currentToken.offset() + currentToken.value().length();
        expect(TokenType.RBRACE);

        SourceLocation location = new SourceLocation(
                startLocation.line(),
                startLocation.column(),
                startOffset,
                endOffset
        );

        return new ClassExpression(id, superClass, body, location);
    }

    private Expression parseConditionalExpression() {
        Expression test = parseLogicalOrExpression();

        if (match(TokenType.QUESTION)) {
            SourceLocation location = getLocation();
            advance();
            Expression consequent = parseAssignmentExpression();
            expect(TokenType.COLON);
            Expression alternate = parseAssignmentExpression();

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

    /**
     * Parse directives at the beginning of a program or function.
     * Following QuickJS implementation in js_parse_directives().
     * Returns true if "use strict" directive was found.
     */
    private boolean parseDirectives() {
        boolean hasUseStrict = false;

        // Directives are string literal expression statements at the beginning
        while (match(TokenType.STRING)) {
            String stringValue = currentToken.value();
            int stringLine = currentToken.line();

            // Peek at the next token to see if this is a valid directive
            Token next = peek();
            boolean hasSemi = false;

            if (next.type() == TokenType.SEMICOLON) {
                hasSemi = true;
            } else if (next.type() == TokenType.RBRACE || next.type() == TokenType.EOF) {
                // ASI before } or EOF
                hasSemi = true;
            } else {
                // Check if next token allows ASI on a new line
                TokenType nextType = next.type();
                boolean isASI = switch (nextType) {
                    case NUMBER, STRING, IDENTIFIER,
                         INC, DEC, NULL, FALSE, TRUE,
                         IF, RETURN, VAR, THIS, DELETE, TYPEOF,
                         NEW, DO, WHILE, FOR, SWITCH, THROW,
                         TRY, FUNCTION, CLASS,
                         CONST, LET -> true;
                    default -> false;
                };
                if (isASI && next.line() > stringLine) {
                    hasSemi = true;
                }
            }

            if (!hasSemi) {
                // Not a directive - leave it for normal statement parsing
                break;
            }

            // Valid directive - consume it
            advance(); // Consume the string token
            if (match(TokenType.SEMICOLON)) {
                advance(); // Consume the semicolon if present
            }

            // Check if this is "use strict"
            if ("use strict".equals(stringValue)) {
                hasUseStrict = true;
            }
        }

        return hasUseStrict;
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
        SourceLocation location = getLocation();
        List<Expression> expressions = new ArrayList<>();
        
        expressions.add(parseAssignmentExpression());
        
        // Check for comma operator
        while (match(TokenType.COMMA)) {
            advance(); // consume comma
            expressions.add(parseAssignmentExpression());
        }
        
        // If only one expression, return it directly (no sequence)
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        
        // Multiple expressions - return a SequenceExpression
        return new SequenceExpression(expressions, location);
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

        // Check for 'await' keyword (for await...of)
        boolean isAwait = false;
        if (match(TokenType.AWAIT)) {
            isAwait = true;
            advance();
        }

        expect(TokenType.LPAREN);

        // Check if this is a for-of or for-in loop
        // We need to peek ahead to see if there's 'of' or 'in' after the variable declaration
        boolean isForOf = false;
        boolean isForIn = false;
        Statement parsedDecl = null;

        // Try to parse as variable declaration
        if (match(TokenType.VAR) || match(TokenType.LET) || match(TokenType.CONST)) {
            parsedDecl = parseVariableDeclaration();
            // Check if next token is 'of' or 'in'
            if (match(TokenType.OF)) {
                isForOf = true;
            } else if (match(TokenType.IN)) {
                isForIn = true;
            }
        }

        if (isForOf) {
            // This is a for-of loop: for (let x of iterable)
            expect(TokenType.OF);
            Expression iterable = parseExpression();
            expect(TokenType.RPAREN);
            Statement body = parseStatement();

            // parsedDecl should be a VariableDeclaration
            if (!(parsedDecl instanceof VariableDeclaration varDecl)) {
                throw new RuntimeException("Expected VariableDeclaration in for-of loop");
            }

            return new ForOfStatement(varDecl, iterable, body, isAwait, location);
        }

        if (isForIn) {
            // This is a for-in loop: for (var x in obj)
            expect(TokenType.IN);
            Expression object = parseExpression();
            expect(TokenType.RPAREN);
            Statement body = parseStatement();

            // parsedDecl should be a VariableDeclaration
            if (!(parsedDecl instanceof VariableDeclaration varDecl)) {
                throw new RuntimeException("Expected VariableDeclaration in for-in loop");
            }

            return new ForInStatement(varDecl, object, body, location);
        }

        // Not a for-of loop, parse as traditional for loop
        // Reset if we parsed a var declaration but it's not for-of
        Statement init = null;
        if (parsedDecl != null) {
            init = parsedDecl;
        } else if (!match(TokenType.SEMICOLON)) {
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

    private FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator) {
        return parseFunctionDeclaration(isAsync, isGenerator, null);
    }

    private FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator, SourceLocation startLocation) {
        // Use provided start location (for async functions) or get current location
        SourceLocation location = startLocation != null ? startLocation : getLocation();
        expect(TokenType.FUNCTION);

        // Check for generator function: function* or async function*
        // Following QuickJS implementation: check for '*' after 'function' keyword
        if (match(TokenType.MUL)) {
            advance();
            isGenerator = true;
        }

        Identifier id = parseIdentifier();

        expect(TokenType.LPAREN);
        FunctionParams funcParams = parseFunctionParameters();

        BlockStatement body = parseBlockStatement();

        // Update location to include end offset (current token offset after parsing body)
        SourceLocation fullLocation = new SourceLocation(
                location.line(),
                location.column(),
                location.offset(),
                currentToken.offset()
        );

        return new FunctionDeclaration(id, funcParams.params, funcParams.restParameter, body, isAsync, isGenerator, fullLocation);
    }

    private Expression parseFunctionExpression() {
        SourceLocation location = getLocation();
        expect(TokenType.FUNCTION);

        // Check for generator function expression: function* () {}
        // Following QuickJS implementation: check for '*' after 'function' keyword
        boolean isGenerator = false;
        if (match(TokenType.MUL)) {
            advance();
            isGenerator = true;
        }

        Identifier id = null;
        if (match(TokenType.IDENTIFIER)) {
            id = parseIdentifier();
        }

        expect(TokenType.LPAREN);
        FunctionParams funcParams = parseFunctionParameters();

        BlockStatement body = parseBlockStatement();

        // Update location to include end offset (current token offset after parsing body)
        SourceLocation fullLocation = new SourceLocation(
                location.line(),
                location.column(),
                location.offset(),
                currentToken.offset()
        );

        return new FunctionExpression(id, funcParams.params, funcParams.restParameter, body, false, isGenerator, fullLocation);
    }

    /**
     * Parse function parameters, including optional rest parameter.
     * Expects '(' to already be consumed.
     * Consumes up to and including ')'.
     *
     * @return FunctionParams containing regular params and optional rest parameter
     */
    private FunctionParams parseFunctionParameters() {
        List<Identifier> params = new ArrayList<>();
        RestParameter restParameter = null;

        if (!match(TokenType.RPAREN)) {
            while (true) {
                // Check for rest parameter (...args)
                if (match(TokenType.ELLIPSIS)) {
                    SourceLocation location = getLocation();
                    advance(); // consume '...'
                    Identifier restArg = parseIdentifier();
                    restParameter = new RestParameter(restArg, location);

                    // Rest parameter must be last
                    if (match(TokenType.COMMA)) {
                        throw new RuntimeException("Rest parameter must be last formal parameter at line " +
                                currentToken.line() + ", column " + currentToken.column());
                    }
                    break;
                }

                // Regular parameter
                params.add(parseIdentifier());

                // Check for comma (more parameters)
                if (!match(TokenType.COMMA)) {
                    break;
                }
                advance(); // consume comma

                // Handle trailing comma before rest parameter or closing paren
                if (match(TokenType.RPAREN) || match(TokenType.ELLIPSIS)) {
                    continue;
                }
            }
        }

        expect(TokenType.RPAREN);
        return new FunctionParams(params, restParameter);
    }

    private Identifier parseIdentifier() {
        SourceLocation location = getLocation();
        String name = currentToken.value();
        expect(TokenType.IDENTIFIER);
        return new Identifier(name, location);
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
                        args.add(parseAssignmentExpression());
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RPAREN);
            }

            return new NewExpression(callee, args, location);
        }

        return parsePrimaryExpression();
    }

    /**
     * Parse a method (constructor, method, getter, or setter).
     */
    private FunctionExpression parseMethod(String kind) {
        SourceLocation location = getLocation();

        // Parse parameter list
        expect(TokenType.LPAREN);
        FunctionParams funcParams = parseFunctionParameters();

        // Parse method body
        BlockStatement body = parseBlockStatement();

        return new FunctionExpression(null, funcParams.params, funcParams.restParameter, body, false, false, location);
    }

    /**
     * Parse method or field after the property name.
     */
    private ClassDeclaration.ClassElement parseMethodOrField(Expression key, boolean isStatic,
                                                             boolean isPrivate, boolean computed,
                                                             SourceLocation location) {
        // Check if it's a field (has = or semicolon)
        if (match(TokenType.ASSIGN) || match(TokenType.SEMICOLON)) {
            Expression value = null;
            if (match(TokenType.ASSIGN)) {
                advance();
                value = parseAssignmentExpression();
            }
            consumeSemicolon();
            return new ClassDeclaration.PropertyDefinition(key, value, computed, isStatic, isPrivate);
        }

        // Otherwise, it's a method
        FunctionExpression method = parseMethod("method");
        return new ClassDeclaration.MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
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
            boolean isAsync = false;
            boolean isGenerator = false;

            // Check for 'async' modifier (only if NOT followed by colon or comma)
            if (match(TokenType.ASYNC)) {
                // Peek ahead to determine if this is a modifier or property name
                if (nextToken.type() == TokenType.COLON || nextToken.type() == TokenType.COMMA) {
                    // { async: value } or { async, ... } - async is property name
                    // Don't advance, let parsePropertyName handle it
                } else {
                    // async is likely a modifier for method
                    isAsync = true;
                    advance();
                }
            }

            // Check for generator *
            if (match(TokenType.MUL)) {
                isGenerator = true;
                advance();
            }

            // Parse property name (can be identifier, string, number, or computed [expr])
            Expression key = parsePropertyName();

            // Determine if this is a method or regular property
            if (match(TokenType.LPAREN)) {
                // Method shorthand: name() {} or async name() {} or *name() {} or async *name() {}
                SourceLocation funcLocation = getLocation();
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

                Expression value = new FunctionExpression(null, params, null, body, isAsync, isGenerator, funcLocation);
                properties.add(new ObjectExpression.Property(key, value, "init", false, false));
            } else {
                // Regular property: key: value
                expect(TokenType.COLON);
                Expression value = parseAssignmentExpression();
                properties.add(new ObjectExpression.Property(key, value, "init", false, false));
            }

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
                Object numValue;
                if (value.startsWith("0x") || value.startsWith("0X")) {
                    // Parse hex number - store as long or int
                    long longVal = Long.parseLong(value.substring(2), 16);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (value.startsWith("0b") || value.startsWith("0B")) {
                    // Parse binary number - store as long or int
                    long longVal = Long.parseLong(value.substring(2), 2);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (value.startsWith("0o") || value.startsWith("0O")) {
                    // Parse octal number - store as long or int
                    long longVal = Long.parseLong(value.substring(2), 8);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else {
                    // Parse decimal number - use double
                    double doubleVal = Double.parseDouble(value);
                    // Check if it's a whole number that fits in an int
                    if (doubleVal == Math.floor(doubleVal) && !Double.isInfinite(doubleVal) &&
                            doubleVal >= Integer.MIN_VALUE && doubleVal <= Integer.MAX_VALUE) {
                        numValue = (int) doubleVal;
                    } else {
                        numValue = doubleVal;
                    }
                }
                yield new Literal(numValue, location);
            }
            case BIGINT -> {
                String value = currentToken.value();
                advance();
                // Parse BigInt literal - handle different radixes
                java.math.BigInteger bigIntValue;
                if (value.startsWith("0x") || value.startsWith("0X")) {
                    bigIntValue = new BigInteger(value.substring(2), 16);
                } else if (value.startsWith("0b") || value.startsWith("0B")) {
                    bigIntValue = new BigInteger(value.substring(2), 2);
                } else if (value.startsWith("0o") || value.startsWith("0O")) {
                    bigIntValue = new BigInteger(value.substring(2), 8);
                } else {
                    bigIntValue = new BigInteger(value);
                }
                yield new Literal(bigIntValue, location);
            }
            case STRING -> {
                String value = currentToken.value();
                advance();
                yield new Literal(value, location);
            }
            case REGEX -> {
                String value = currentToken.value();
                advance();
                yield new Literal(value, location);
            }
            case TEMPLATE -> {
                yield parseTemplateLiteral(false);
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
                // This could be either:
                // 1. A grouped expression: (expr)
                // 2. An arrow function parameter list: (params) => body
                // We need to distinguish between them

                // Try to detect arrow function by looking ahead
                // Patterns: () => or (id) => or (id, id, ...) =>

                advance(); // consume (

                // Check for empty parameter list: () which could be arrow function
                if (match(TokenType.RPAREN)) {
                    // Could be () => ...
                    // Return an empty ArrayExpression as a marker for empty parameter list
                    advance();
                    yield new ArrayExpression(new ArrayList<>(), location);
                }

                // Try to parse as potential arrow function parameters
                // This is a simplified heuristic: if we see identifier(s) and commas, followed by ), =>
                // then treat it as arrow function params
                // Otherwise parse as expression

                // Check if next token is identifier - could be arrow function param
                if (match(TokenType.IDENTIFIER) || match(TokenType.ELLIPSIS)) {
                    // Check for rest parameter (...args)
                    if (match(TokenType.ELLIPSIS)) {
                        // This must be an arrow function with rest parameter: (...args) => expr
                        // We cannot parse this as a regular expression, so parse as arrow function params
                        SourceLocation restLocation = getLocation();
                        advance(); // consume '...'
                        Identifier restArg = parseIdentifier();
                        RestParameter restParam = new RestParameter(restArg, restLocation);

                        expect(TokenType.RPAREN);

                        // Mark this as arrow function parameters with rest
                        // We'll use a special marker - an ArrayExpression with a SpreadElement
                        yield new ArrayExpression(
                                List.of(new SpreadElement(restArg, restLocation)),
                                location
                        );
                    }

                    // Peek ahead to distinguish between:
                    // (id) => expr (arrow function with single param)
                    // (id, id2) => expr (arrow function with multiple params)
                    // (id = value) (grouped assignment expression)
                    // (id + value) (grouped binary expression)

                    // We need to look at what comes after the identifier
                    // to decide if this is arrow function params or a grouped expression

                    // Check if the token after identifier is an assignment operator
                    // If so, this is a grouped expression like (b = 10), not arrow params
                    if (nextToken.type() != TokenType.COMMA &&
                            nextToken.type() != TokenType.RPAREN &&
                            nextToken.type() != TokenType.ARROW) {
                        // This looks like a grouped expression, not arrow function params
                        // Parse the whole thing as an expression
                        Expression expr = parseExpression();
                        expect(TokenType.RPAREN);
                        yield expr;
                    }

                    // Could be (id) or (id, id, ...) or (id, ...rest)
                    // Parse as parameter list tentatively
                    List<Expression> potentialParams = new ArrayList<>();
                    potentialParams.add(parseIdentifier());

                    // Check for more parameters or rest parameter
                    while (match(TokenType.COMMA)) {
                        advance(); // consume comma

                        // Check for rest parameter at end
                        if (match(TokenType.ELLIPSIS)) {
                            SourceLocation restLocation = getLocation();
                            advance(); // consume '...'
                            Identifier restArg = parseIdentifier();
                            RestParameter restParam = new RestParameter(restArg, restLocation);

                            // Add SpreadElement as marker for rest parameter
                            potentialParams.add(new SpreadElement(restArg, restLocation));

                            // Rest must be last, so break
                            break;
                        }

                        if (!match(TokenType.IDENTIFIER)) {
                            // Not a simple parameter list, might be complex expression
                            // For now, throw error
                            throw new RuntimeException("Complex arrow function parameters not yet supported at line " +
                                    currentToken.line() + ", column " + currentToken.column());
                        }
                        potentialParams.add(parseIdentifier());
                    }

                    expect(TokenType.RPAREN);

                    // Now check if followed by =>
                    // If yes, this is an arrow function parameter list
                    // If no, this was a grouped identifier (or sequence)
                    // For now, we'll create a custom marker for this case

                    // Return first param if single (and not a spread), or create a marker for multiple
                    if (potentialParams.size() == 1 && !(potentialParams.get(0) instanceof SpreadElement)) {
                        yield potentialParams.get(0);
                    } else {
                        // Multiple parameters or rest parameter - create an ArrayExpression as marker
                        yield new ArrayExpression(potentialParams, location);
                    }
                } else {
                    // Not starting with identifier, parse as expression
                    Expression expr = parseExpression();
                    expect(TokenType.RPAREN);
                    yield expr;
                }
            }
            case LBRACKET -> parseArrayExpression();
            case LBRACE -> parseObjectExpression();
            case FUNCTION -> parseFunctionExpression();
            case CLASS -> parseClassExpression(); // Class expressions
            default -> {
                // Error case - return a literal undefined
                advance();
                yield new Literal(null, location);
            }
        };
    }

    // Utility methods

    private Expression parsePropertyName() {
        SourceLocation location = getLocation();
        return switch (currentToken.type()) {
            case IDENTIFIER -> {
                String name = currentToken.value();
                advance();
                yield new Identifier(name, location);
            }
            case PRIVATE_NAME -> {
                // Private field access: obj.#field
                String name = currentToken.value();
                // Remove '#' prefix for the PrivateIdentifier name
                String fieldName = name.substring(1);
                advance();
                yield new PrivateIdentifier(fieldName, location);
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
            case LBRACKET -> {
                // Computed property name: [expression]
                advance();
                Expression expr = parseAssignmentExpression();
                expect(TokenType.RBRACKET);
                yield expr;
            }
            // Allow keywords as property names (e.g., obj.delete, obj.class, obj.return)
            case AS, ASYNC, AWAIT, BREAK, CASE, CATCH, CLASS, CONST, CONTINUE,
                 DEFAULT, DELETE, DO, ELSE, EXPORT, EXTENDS, FALSE, FINALLY,
                 FOR, FROM, FUNCTION, IF, IMPORT, IN, INSTANCEOF, LET, NEW,
                 NULL, OF, RETURN, SUPER, SWITCH, THIS, THROW, TRUE, TRY,
                 TYPEOF, VAR, VOID, WHILE, YIELD -> {
                String name = currentToken.value();
                advance();
                yield new Identifier(name, location);
            }
            default -> throw new JSSyntaxErrorException("Unexpected end of input");
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
            case ASYNC -> {
                // Capture location at 'async' keyword for proper source extraction
                SourceLocation asyncLocation = getLocation();
                // Consume 'async' and check if it's an async function declaration
                advance();
                if (match(TokenType.FUNCTION)) {
                    yield parseFunctionDeclaration(true, false, asyncLocation);
                } else {
                    // Otherwise, it's an error - async must be followed by function
                    throw new RuntimeException("Expected 'function' after 'async'");
                }
            }
            case FUNCTION -> {
                // Function declarations are treated as statements in JavaScript
                yield parseFunctionDeclaration(false, false);
            }
            case CLASS -> {
                // Class declarations are treated as statements in JavaScript
                yield parseClassDeclaration();
            }
            case SEMICOLON -> {
                advance(); // consume semicolon
                yield null; // empty statement
            }
            default -> parseExpressionStatement();
        };
    }

    /**
     * Parse a static initialization block: static { statements }
     */
    private ClassDeclaration.StaticBlock parseStaticBlock() {
        expect(TokenType.LBRACE);
        List<Statement> statements = new ArrayList<>();

        while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        expect(TokenType.RBRACE);
        return new ClassDeclaration.StaticBlock(statements);
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

    private TemplateLiteral parseTemplateLiteral(boolean tagged) {
        SourceLocation location = getLocation();
        String templateStr = currentToken.value();
        advance();

        List<String> quasis = new ArrayList<>();
        List<String> rawQuasis = new ArrayList<>();
        List<Expression> expressions = new ArrayList<>();

        int quasiStart = 0;
        int pos = 0;
        while (pos < templateStr.length()) {
            char c = templateStr.charAt(pos);
            if (c == '\\' && pos + 1 < templateStr.length()) {
                // Skip escaped character so \${ does not trigger interpolation.
                pos += 2;
                continue;
            }
            if (c == '$' && pos + 1 < templateStr.length() && templateStr.charAt(pos + 1) == '{') {
                String rawQuasi = normalizeTemplateLineTerminators(templateStr.substring(quasiStart, pos));
                rawQuasis.add(rawQuasi);
                quasis.add(processTemplateEscapeSequences(rawQuasi, tagged));

                int exprStart = pos + 2;
                int exprEnd = findTemplateExpressionEnd(templateStr, exprStart);
                String expressionSource = templateStr.substring(exprStart, exprEnd);
                expressions.add(parseTemplateExpression(expressionSource));

                pos = exprEnd + 1; // skip closing }
                quasiStart = pos;
            } else {
                pos++;
            }
        }

        String rawQuasi = normalizeTemplateLineTerminators(templateStr.substring(quasiStart));
        rawQuasis.add(rawQuasi);
        quasis.add(processTemplateEscapeSequences(rawQuasi, tagged));
        return new TemplateLiteral(quasis, rawQuasis, expressions, location);
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
        // Handle await expressions
        if (match(TokenType.AWAIT)) {
            SourceLocation location = getLocation();
            advance();
            Expression argument = parseUnaryExpression();
            return new AwaitExpression(argument, location);
        }

        // Handle yield expressions
        if (match(TokenType.YIELD)) {
            SourceLocation location = getLocation();
            advance();

            // Check for yield* (delegating yield)
            boolean delegate = false;
            if (match(TokenType.MUL)) {
                delegate = true;
                advance();
            }

            // Yield can have no argument: just "yield" by itself
            // or can have an argument: "yield expr" or "yield* expr"
            Expression argument = null;
            if (!match(TokenType.SEMICOLON) && !match(TokenType.RBRACE) && !match(TokenType.EOF)) {
                argument = parseAssignmentExpression();
            }

            return new YieldExpression(argument, delegate, location);
        }

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
                init = parseAssignmentExpression();
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

    private Token peek() {
        return nextToken;
    }

    private int findTemplateExpressionEnd(String templateStr, int expressionStart) {
        int braceDepth = 1;
        int pos = expressionStart;
        boolean regexAllowed = true;

        while (pos < templateStr.length()) {
            char c = templateStr.charAt(pos);

            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            if (c == '\'' || c == '"') {
                pos = skipQuotedString(templateStr, pos, c);
                regexAllowed = false;
                continue;
            }

            if (c == '`') {
                pos = skipNestedTemplateLiteral(templateStr, pos);
                regexAllowed = false;
                continue;
            }

            if (c == '/') {
                if (pos + 1 < templateStr.length()) {
                    char next = templateStr.charAt(pos + 1);
                    if (next == '/') {
                        pos = skipLineComment(templateStr, pos + 2);
                        regexAllowed = true;
                        continue;
                    }
                    if (next == '*') {
                        pos = skipBlockComment(templateStr, pos + 2);
                        regexAllowed = true;
                        continue;
                    }
                }

                if (regexAllowed) {
                    pos = skipRegexLiteral(templateStr, pos);
                    regexAllowed = false;
                    continue;
                }

                pos++;
                if (pos < templateStr.length() && templateStr.charAt(pos) == '=') {
                    pos++;
                }
                regexAllowed = true;
                continue;
            }

            if (c == '{') {
                braceDepth++;
                pos++;
                regexAllowed = true;
                continue;
            }

            if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return pos;
                }
                pos++;
                regexAllowed = false;
                continue;
            }

            if (c == '(' || c == '[' || c == ',' || c == ';' || c == ':') {
                pos++;
                regexAllowed = true;
                continue;
            }

            if (c == ')' || c == ']') {
                pos++;
                regexAllowed = false;
                continue;
            }

            if (c == '.') {
                if (pos + 2 < templateStr.length()
                        && templateStr.charAt(pos + 1) == '.'
                        && templateStr.charAt(pos + 2) == '.') {
                    pos += 3;
                    regexAllowed = true;
                } else if (pos + 1 < templateStr.length() && Character.isDigit(templateStr.charAt(pos + 1))) {
                    pos = skipNumberLiteral(templateStr, pos);
                    regexAllowed = false;
                } else {
                    pos++;
                    regexAllowed = false;
                }
                continue;
            }

            if (isIdentifierStartChar(c)) {
                int start = pos++;
                while (pos < templateStr.length() && isIdentifierPartChar(templateStr.charAt(pos))) {
                    pos++;
                }
                String identifier = templateStr.substring(start, pos);
                regexAllowed = switch (identifier) {
                    case "return", "throw", "case", "delete", "void", "typeof",
                         "instanceof", "in", "of", "new", "do", "else", "yield", "await" -> true;
                    default -> false;
                };
                continue;
            }

            if (Character.isDigit(c)) {
                pos = skipNumberLiteral(templateStr, pos);
                regexAllowed = false;
                continue;
            }

            if ("+-*%&|^!~<>=?".indexOf(c) >= 0) {
                pos++;
                if (pos < templateStr.length()) {
                    char next = templateStr.charAt(pos);
                    if (next == '=' || (next == c && "&|+-<>?".indexOf(c) >= 0)) {
                        pos++;
                    } else if (c == '=' && next == '>') {
                        pos++;
                    }
                }
                if (c == '>' && pos < templateStr.length() && templateStr.charAt(pos) == '>') {
                    pos++;
                    if (pos < templateStr.length() && templateStr.charAt(pos) == '>') {
                        pos++;
                    }
                    if (pos < templateStr.length() && templateStr.charAt(pos) == '=') {
                        pos++;
                    }
                }
                regexAllowed = true;
                continue;
            }

            pos++;
            regexAllowed = false;
        }

        throw new JSSyntaxErrorException("Unterminated template expression");
    }

    private String normalizeTemplateLineTerminators(String str) {
        StringBuilder normalized = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\r') {
                if (i + 1 < str.length() && str.charAt(i + 1) == '\n') {
                    i++;
                }
                normalized.append('\n');
            } else {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private Expression parseTemplateExpression(String expressionSource) {
        if (expressionSource.isBlank()) {
            throw new JSSyntaxErrorException("Empty template expression");
        }

        Parser expressionParser = new Parser(new Lexer(expressionSource));
        Expression expression = expressionParser.parseExpression();
        if (expressionParser.currentToken.type() != TokenType.EOF) {
            throw new JSSyntaxErrorException("Invalid template expression");
        }
        return expression;
    }

    private String processTemplateEscapeSequences(String str, boolean tagged) {
        StringBuilder result = new StringBuilder(str.length());
        int i = 0;
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c != '\\') {
                result.append(c);
                i++;
                continue;
            }

            if (i + 1 >= str.length()) {
                if (tagged) {
                    return null;
                }
                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
            }

            char next = str.charAt(i + 1);
            switch (next) {
                case '\n' -> i += 2; // Line continuation
                case '\r' -> {
                    i += 2;
                    if (i < str.length() && str.charAt(i) == '\n') {
                        i++;
                    }
                }
                case '\\', '\'', '"', '`', '$' -> {
                    result.append(next);
                    i += 2;
                }
                case 'b' -> {
                    result.append('\b');
                    i += 2;
                }
                case 'f' -> {
                    result.append('\f');
                    i += 2;
                }
                case 'n' -> {
                    result.append('\n');
                    i += 2;
                }
                case 'r' -> {
                    result.append('\r');
                    i += 2;
                }
                case 't' -> {
                    result.append('\t');
                    i += 2;
                }
                case 'v' -> {
                    result.append('\u000B');
                    i += 2;
                }
                case '0' -> {
                    if (i + 2 < str.length() && Character.isDigit(str.charAt(i + 2))) {
                        if (tagged) {
                            return null;
                        }
                        throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                    }
                    result.append('\0');
                    i += 2;
                }
                case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (tagged) {
                        return null;
                    }
                    throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                }
                case 'x' -> {
                    if (i + 3 >= str.length()
                            || Character.digit(str.charAt(i + 2), 16) < 0
                            || Character.digit(str.charAt(i + 3), 16) < 0) {
                        if (tagged) {
                            return null;
                        }
                        throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                    }
                    int value = Integer.parseInt(str.substring(i + 2, i + 4), 16);
                    result.append((char) value);
                    i += 4;
                }
                case 'u' -> {
                    if (i + 2 < str.length() && str.charAt(i + 2) == '{') {
                        int end = i + 3;
                        while (end < str.length() && str.charAt(end) != '}') {
                            if (Character.digit(str.charAt(end), 16) < 0) {
                                if (tagged) {
                                    return null;
                                }
                                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                            }
                            end++;
                        }
                        if (end >= str.length() || end == i + 3) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                        }
                        int codePoint;
                        try {
                            codePoint = Integer.parseInt(str.substring(i + 3, end), 16);
                        } catch (NumberFormatException e) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                        }
                        if (codePoint < 0 || codePoint > 0x10FFFF) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                        }
                        result.appendCodePoint(codePoint);
                        i = end + 1;
                    } else {
                        if (i + 5 >= str.length()) {
                            if (tagged) {
                                return null;
                            }
                            throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                        }
                        int codeUnit = 0;
                        for (int j = i + 2; j < i + 6; j++) {
                            int digit = Character.digit(str.charAt(j), 16);
                            if (digit < 0) {
                                if (tagged) {
                                    return null;
                                }
                                throw new JSSyntaxErrorException("Malformed escape sequence in template literal");
                            }
                            codeUnit = (codeUnit << 4) | digit;
                        }
                        result.append((char) codeUnit);
                        i += 6;
                    }
                }
                default -> {
                    // NonEscapeCharacter: keep the escaped character and drop '\'.
                    result.append(next);
                    i += 2;
                }
            }
        }
        return result.toString();
    }

    private boolean isIdentifierPartChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private boolean isIdentifierStartChar(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private int skipBlockComment(String source, int pos) {
        while (pos + 1 < source.length()) {
            if (source.charAt(pos) == '*' && source.charAt(pos + 1) == '/') {
                return pos + 2;
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated block comment in template expression");
    }

    private int skipLineComment(String source, int pos) {
        while (pos < source.length() && source.charAt(pos) != '\n' && source.charAt(pos) != '\r') {
            pos++;
        }
        return pos;
    }

    private int skipNestedTemplateLiteral(String source, int backtickPos) {
        int pos = backtickPos + 1;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\\') {
                pos = Math.min(pos + 2, source.length());
                continue;
            }
            if (c == '`') {
                return pos + 1;
            }
            if (c == '$' && pos + 1 < source.length() && source.charAt(pos + 1) == '{') {
                int exprEnd = findTemplateExpressionEnd(source, pos + 2);
                pos = exprEnd + 1;
                continue;
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated nested template literal");
    }

    private int skipNumberLiteral(String source, int pos) {
        int start = pos;
        if (source.charAt(pos) == '0' && pos + 1 < source.length()) {
            char prefix = source.charAt(pos + 1);
            if (prefix == 'x' || prefix == 'X' || prefix == 'b' || prefix == 'B' || prefix == 'o' || prefix == 'O') {
                pos += 2;
                while (pos < source.length() && isIdentifierPartChar(source.charAt(pos))) {
                    pos++;
                }
                return pos;
            }
        }

        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            pos++;
        }
        if (pos < source.length() && source.charAt(pos) == '.') {
            pos++;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }
        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            int expPos = pos + 1;
            if (expPos < source.length() && (source.charAt(expPos) == '+' || source.charAt(expPos) == '-')) {
                expPos++;
            }
            while (expPos < source.length() && Character.isDigit(source.charAt(expPos))) {
                expPos++;
            }
            pos = expPos;
        }
        if (pos < source.length() && source.charAt(pos) == 'n') {
            pos++;
        }
        return pos == start ? start + 1 : pos;
    }

    private int skipQuotedString(String source, int quotePos, char quote) {
        int pos = quotePos + 1;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\\') {
                pos = Math.min(pos + 2, source.length());
                continue;
            }
            if (c == quote) {
                return pos + 1;
            }
            if (c == '\n' || c == '\r') {
                throw new JSSyntaxErrorException("Unterminated string in template expression");
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated string in template expression");
    }

    private int skipRegexLiteral(String source, int slashPos) {
        int pos = slashPos + 1;
        boolean inCharacterClass = false;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\\') {
                pos = Math.min(pos + 2, source.length());
                continue;
            }
            if (c == '[') {
                inCharacterClass = true;
                pos++;
                continue;
            }
            if (c == ']' && inCharacterClass) {
                inCharacterClass = false;
                pos++;
                continue;
            }
            if (c == '/' && !inCharacterClass) {
                pos++;
                while (pos < source.length() && isIdentifierPartChar(source.charAt(pos))) {
                    pos++;
                }
                return pos;
            }
            if (c == '\n' || c == '\r') {
                throw new JSSyntaxErrorException("Unterminated regex literal in template expression");
            }
            pos++;
        }
        throw new JSSyntaxErrorException("Unterminated regex literal in template expression");
    }

    /**
     * Helper record to hold the result of parsing function parameters.
     */
    private record FunctionParams(List<Identifier> params, RestParameter restParameter) {
    }
}
