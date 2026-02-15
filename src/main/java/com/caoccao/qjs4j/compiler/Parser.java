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
import com.caoccao.qjs4j.regexp.RegExpLiteralValue;

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
    private final boolean moduleMode;
    private int asyncFunctionNesting;
    private Token currentToken;
    private int functionNesting;
    private boolean inOperatorAllowed = true; // false inside for-loop initializer (ES spec [~In])
    private Token nextToken; // Lookahead token
    private int previousTokenLine; // Line of previous token (for ASI checks)
    private boolean strictMode; // true when in strict mode ("use strict" or module)

    public Parser(Lexer lexer) {
        this(lexer, false);
    }

    public Parser(Lexer lexer, boolean moduleMode) {
        this(lexer, moduleMode, 0, 0);
    }

    private Parser(Lexer lexer, boolean moduleMode, int functionNesting, int asyncFunctionNesting) {
        this.lexer = lexer;
        this.moduleMode = moduleMode;
        this.functionNesting = functionNesting;
        this.asyncFunctionNesting = asyncFunctionNesting;
        this.currentToken = lexer.nextToken();
        this.nextToken = lexer.nextToken();
    }

    private void advance() {
        previousTokenLine = currentToken.line();
        currentToken = nextToken;
        nextToken = lexer.nextToken();
    }

    private void consumeSemicolon() {
        if (match(TokenType.SEMICOLON)) {
            advance();
            return;
        }
        // ASI: semicolon is inserted if there's a line break before the current token,
        // or if the current token is } or EOF (per ECMAScript spec 12.9.1)
        if (hasNewlineBefore() || match(TokenType.RBRACE) || match(TokenType.EOF)) {
            return;
        }
        throw new JSSyntaxErrorException("Unexpected token '" + currentToken.value() + "'");
    }

    private void enterFunctionContext(boolean asyncFunction) {
        functionNesting++;
        if (asyncFunction) {
            asyncFunctionNesting++;
        }
    }

    private void exitFunctionContext(boolean asyncFunction) {
        if (asyncFunction) {
            asyncFunctionNesting--;
        }
        functionNesting--;
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

    private SourceLocation getLocation() {
        return new SourceLocation(currentToken.line(), currentToken.column(), currentToken.offset());
    }

    /**
     * Check if there was a newline between the previous token and the current token.
     * Used for ASI (Automatic Semicolon Insertion) checks, e.g. break/continue labels
     * must be on the same line as the keyword.
     */
    private boolean hasNewlineBefore() {
        return currentToken.line() > previousTokenLine;
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

    private boolean isAwaitExpressionAllowed() {
        return asyncFunctionNesting > 0 || (moduleMode && functionNesting == 0);
    }

    private boolean isAwaitIdentifierAllowed() {
        return !moduleMode && asyncFunctionNesting == 0;
    }

    private boolean isAwaitUsingDeclarationStart() {
        return match(TokenType.AWAIT)
                && isUsingIdentifierToken(nextToken);
    }

    private boolean isExpressionStartToken(TokenType tokenType) {
        return switch (tokenType) {
            case ASYNC, AWAIT, BIGINT, CLASS, FALSE, FUNCTION, IDENTIFIER,
                 LBRACE, LBRACKET, LPAREN, NEW, NULL, NUMBER, REGEX,
                 STRING, TEMPLATE, THIS, TRUE -> true;
            default -> false;
        };
    }

    private boolean isIdentifierPartChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private boolean isIdentifierStartChar(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private boolean isPatternStartToken(TokenType tokenType) {
        return tokenType == TokenType.IDENTIFIER
                || tokenType == TokenType.LBRACE
                || tokenType == TokenType.LBRACKET
                || tokenType == TokenType.AWAIT;
    }

    private boolean isUsingDeclarationStart() {
        return currentToken.type() == TokenType.IDENTIFIER
                && "using".equals(currentToken.value())
                && isPatternStartToken(nextToken.type());
    }

    private boolean isUsingIdentifierToken(Token token) {
        return token.type() == TokenType.IDENTIFIER && "using".equals(token.value());
    }

    private boolean isValidContinuationAfterAwaitIdentifier() {
        if (nextToken.line() > currentToken.line()) {
            return true;
        }
        if (!isExpressionStartToken(nextToken.type())) {
            return true;
        }
        return nextToken.type() == TokenType.LPAREN
                || nextToken.type() == TokenType.LBRACKET
                || nextToken.type() == TokenType.TEMPLATE;
    }

    private boolean isValidForInOfTarget(Expression expr) {
        return expr instanceof Identifier
                || expr instanceof MemberExpression;
    }

    private boolean match(TokenType type) {
        return currentToken.type() == type;
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

    /**
     * Parse the entire program.
     */
    public Program parse() {
        List<Statement> body = new ArrayList<>();
        SourceLocation location = getLocation();

        // Parse directives (like "use strict") at the beginning
        boolean strict = parseDirectives();
        strictMode = strict || moduleMode;
        lexer.setStrictMode(strictMode);

        while (!match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        return new Program(body, moduleMode, strict || moduleMode, location);
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
                Pattern element = parsePattern();
                // Check for default value: [x = defaultVal]
                if (match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = getLocation();
                    advance(); // consume '='
                    Expression defaultValue = parseAssignmentExpression();
                    element = new AssignmentPattern(element, defaultValue, assignLoc);
                }
                elements.add(element);
            }
        }

        expect(TokenType.RBRACKET);
        return new ArrayPattern(elements, location);
    }

    private Expression parseAssignmentExpression() {
        SourceLocation location = getLocation();

        // Check for async arrow function: async () => {} or async (params) => {}
        if (match(TokenType.ASYNC)) {
            SourceLocation asyncLocation = location;
            advance(); // consume 'async'

            // Async function expression: async function (...) {}
            if (match(TokenType.FUNCTION)) {
                return parseFunctionExpression(true, asyncLocation);
            }

            // Async arrow function with single identifier parameter: async x => x
            if (match(TokenType.IDENTIFIER) && nextToken.type() == TokenType.ARROW) {
                Identifier param = parseIdentifier();
                expect(TokenType.ARROW);

                ASTNode body;
                enterFunctionContext(true);
                try {
                    if (match(TokenType.LBRACE)) {
                        body = parseBlockStatement();
                    } else {
                        body = parseAssignmentExpression();
                    }
                } finally {
                    exitFunctionContext(true);
                }

                SourceLocation fullLocation = new SourceLocation(
                        asyncLocation.line(),
                        asyncLocation.column(),
                        asyncLocation.offset(),
                        currentToken.offset()
                );
                return new ArrowFunctionExpression(List.of(param), null, null, body, true, fullLocation);
            }

            // Check if followed by ( or identifier (for single param)
            if (match(TokenType.LPAREN) || match(TokenType.IDENTIFIER)) {
                if (match(TokenType.LPAREN)) {
                    enterFunctionContext(true);
                    try {
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

                            return new ArrowFunctionExpression(funcParams.params, funcParams.defaults, funcParams.restParameter, body, true, fullLocation);
                        }
                    } finally {
                        exitFunctionContext(true);
                    }
                }
            }

            // Fallback: treat `async` as an identifier in expression position.
            Expression left = new Identifier("async", asyncLocation);
            if (isAssignmentOperator(currentToken.type())) {
                TokenType op = currentToken.type();
                SourceLocation assignmentLocation = getLocation();
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
                return new AssignmentExpression(left, operator, right, assignmentLocation);
            }
            return left;
        }

        Expression left = parseConditionalExpression();

        // After parsing conditional expression, check if it's actually an arrow function
        // Pattern: identifier => expr  OR  (params) => expr
        if (match(TokenType.ARROW)) {
            // Convert the parsed expression to arrow function parameters
            List<Identifier> params = new ArrayList<>();
            List<Expression> defaults = new ArrayList<>();
            RestParameter restParameter = null;

            if (left instanceof Identifier) {
                // Single parameter without parentheses: x => x + 1
                params.add((Identifier) left);
                defaults.add(null);
            } else if (left instanceof AssignmentExpression assignExpr
                    && assignExpr.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                    && assignExpr.left() instanceof Identifier paramId) {
                // Single parameter with default: (x = defaultExpr) => body
                params.add(paramId);
                defaults.add(assignExpr.right());
            } else if (left instanceof SequenceExpression seqExpr) {
                // Multiple parameters possibly with defaults: (x = a, y = b) => body
                for (Expression expr : seqExpr.expressions()) {
                    if (expr instanceof Identifier id) {
                        params.add(id);
                        defaults.add(null);
                    } else if (expr instanceof AssignmentExpression ae
                            && ae.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                            && ae.left() instanceof Identifier aeParamId) {
                        params.add(aeParamId);
                        defaults.add(ae.right());
                    } else {
                        throw new RuntimeException("Invalid arrow function parameter at line " +
                                currentToken.line() + ", column " + currentToken.column());
                    }
                }
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
                                    throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                                }
                            } else {
                                throw new RuntimeException("Invalid rest parameter at line " +
                                        currentToken.line() + ", column " + currentToken.column());
                            }
                        } else if (expr instanceof Identifier id) {
                            params.add(id);
                            defaults.add(null);
                        } else if (expr instanceof AssignmentExpression ae
                                && ae.operator() == AssignmentExpression.AssignmentOperator.ASSIGN
                                && ae.left() instanceof Identifier aeParamId) {
                            params.add(aeParamId);
                            defaults.add(ae.right());
                        } else {
                            throw new RuntimeException("Invalid arrow function parameter at line " +
                                    currentToken.line() + ", column " + currentToken.column());
                        }
                    }
                }
            } else {
                // Could be other complex cases that we don't support yet
                throw new RuntimeException("Unsupported arrow function parameters at line " +
                        currentToken.line() + ", column " + currentToken.column());
            }

            advance(); // consume '=>'

            // Parse body
            ASTNode body;
            enterFunctionContext(false);
            try {
                if (match(TokenType.LBRACE)) {
                    body = parseBlockStatement();
                } else {
                    // Expression body
                    body = parseAssignmentExpression();
                }
            } finally {
                exitFunctionContext(false);
            }

            // Update location to include end offset
            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    currentToken.offset()
            );

            return new ArrowFunctionExpression(params, defaults, restParameter, body, false, fullLocation);
        }

        if (isAssignmentOperator(currentToken.type())) {
            // Validate that left is a valid assignment target
            // CallExpression is a valid LeftHandSideExpression syntactically;
            // the error is a runtime ReferenceError, not a parse-time SyntaxError.
            if (!(left instanceof Identifier)
                    && !(left instanceof MemberExpression)
                    && !(left instanceof ArrayExpression)
                    && !(left instanceof ObjectExpression)
                    && !(left instanceof CallExpression)) {
                throw new JSSyntaxErrorException("Invalid left-hand side in assignment");
            }

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

    private Statement parseAsyncDeclaration() {
        if (nextToken.type() == TokenType.FUNCTION) {
            SourceLocation asyncLocation = getLocation();
            advance(); // consume async
            return parseFunctionDeclaration(true, false, asyncLocation);
        } else {
            // Otherwise parse as an expression statement (e.g. async () => 1).
            return parseExpressionStatement();
        }
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

    // Expression parsing with precedence

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
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (match(TokenType.IDENTIFIER) && !hasNewlineBefore()) {
            label = parseIdentifier();
        }
        consumeSemicolon();
        return new BreakStatement(label, location);
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
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (match(TokenType.IDENTIFIER) && !hasNewlineBefore()) {
            label = parseIdentifier();
        }
        consumeSemicolon();
        return new ContinueStatement(label, location);
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
            if (!isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            isAwait = true;
            advance();
        }

        expect(TokenType.LPAREN);

        // Check if this is a for-of or for-in loop
        // We need to peek ahead to see if there's 'of' or 'in' after the variable declaration
        boolean isForOf = false;
        boolean isForIn = false;
        Statement parsedDecl = null;

        // Try to parse as variable declaration (without consuming semicolon,
        // since we need to check for 'of' or 'in' first)
        if (match(TokenType.VAR) || match(TokenType.LET) || match(TokenType.CONST)
                || isUsingDeclarationStart() || isAwaitUsingDeclarationStart()) {
            // Annex B: suppress 'in' as binary operator only for 'var' in non-strict mode
            // (allows for-in initializers: for (var a = expr in obj))
            boolean savedInOperatorAllowed = inOperatorAllowed;
            boolean isVar = match(TokenType.VAR);
            if (isVar && !strictMode) {
                inOperatorAllowed = false;
            }
            if (isAwaitUsingDeclarationStart()) {
                SourceLocation declLocation = getLocation();
                expect(TokenType.AWAIT);
                advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.AWAIT_USING, declLocation, false);
            } else if (isUsingDeclarationStart()) {
                SourceLocation declLocation = getLocation();
                advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.USING, declLocation, false);
            } else {
                SourceLocation declLocation = getLocation();
                VariableKind kind = VariableKind.fromKeyword(currentToken.value());
                advance();
                parsedDecl = parseVariableDeclarationBody(kind, declLocation, false);
            }
            inOperatorAllowed = savedInOperatorAllowed;
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

        if (isAwait) {
            throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
        }

        // Not a for-of loop, parse as traditional for loop
        // Reset if we parsed a var declaration but it's not for-of
        Statement init = null;
        if (parsedDecl != null) {
            init = parsedDecl;
            expect(TokenType.SEMICOLON); // consume ; after init declaration
        } else if (!match(TokenType.SEMICOLON)) {
            if (match(TokenType.VAR) || match(TokenType.LET) || match(TokenType.CONST)
                    || isUsingDeclarationStart() || isAwaitUsingDeclarationStart()) {
                if (isAwaitUsingDeclarationStart()) {
                    init = parseUsingDeclaration(true);
                } else if (isUsingDeclarationStart()) {
                    init = parseUsingDeclaration(false);
                } else {
                    init = parseVariableDeclaration();
                }
            } else {
                // Parse expression — could be for-in/for-of left side or traditional for init.
                // Parse as assignment expression first, then check for 'in' or 'of'.
                // Suppress 'in' as binary operator per ES spec [~In] grammar parameter.
                boolean savedInOperatorAllowed = inOperatorAllowed;
                inOperatorAllowed = false;
                Expression expr = parseAssignmentExpression();
                inOperatorAllowed = savedInOperatorAllowed;
                if (match(TokenType.IN)) {
                    // for (expr in obj) — expression-based for-in
                    // Validate: left side must be a valid LeftHandSideExpression
                    if (!isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    advance(); // consume 'in'
                    Expression object = parseExpression();
                    expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    return new ForInStatement(expr, object, body, location);
                } else if (match(TokenType.OF)) {
                    // for (expr of iterable) — expression-based for-of
                    if (!isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    advance(); // consume 'of'
                    Expression iterable = parseExpression();
                    expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    return new ForOfStatement(expr, iterable, body, isAwait, location);
                }
                // Traditional for loop init — handle comma expressions
                while (match(TokenType.COMMA)) {
                    advance();
                    Expression right = parseAssignmentExpression();
                    expr = new SequenceExpression(java.util.List.of(expr, right), expr.getLocation());
                }
                init = new ExpressionStatement(expr, expr.getLocation());
                consumeSemicolon();
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

        enterFunctionContext(isAsync);
        try {
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

            return new FunctionDeclaration(id, funcParams.params, funcParams.defaults, funcParams.restParameter, body, isAsync, isGenerator, fullLocation);
        } finally {
            exitFunctionContext(isAsync);
        }
    }

    private Expression parseFunctionExpression() {
        return parseFunctionExpression(false, null);
    }

    private Expression parseFunctionExpression(boolean isAsync, SourceLocation startLocation) {
        SourceLocation location = startLocation != null ? startLocation : getLocation();
        expect(TokenType.FUNCTION);

        // Check for generator function expression: function* () {}
        // Following QuickJS implementation: check for '*' after 'function' keyword
        boolean isGenerator = false;
        if (match(TokenType.MUL)) {
            advance();
            isGenerator = true;
        }

        enterFunctionContext(isAsync);
        try {
            Identifier id = null;
            if (match(TokenType.IDENTIFIER) || match(TokenType.AWAIT)) {
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

            return new FunctionExpression(id, funcParams.params, funcParams.defaults, funcParams.restParameter, body, isAsync, isGenerator, fullLocation);
        } finally {
            exitFunctionContext(isAsync);
        }
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
        List<Expression> defaults = new ArrayList<>();
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
                        throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                    }
                    break;
                }

                // Regular parameter
                params.add(parseIdentifier());

                // Check for default value: param = defaultExpr
                // Following QuickJS js_parse_function_decl2 pattern
                if (match(TokenType.ASSIGN)) {
                    advance(); // consume '='
                    defaults.add(parseAssignmentExpression());
                } else {
                    defaults.add(null);
                }

                // Check for comma (more parameters)
                if (!match(TokenType.COMMA)) {
                    break;
                }
                advance(); // consume comma

                // Handle trailing comma before closing paren
                if (match(TokenType.RPAREN)) {
                    break;
                }
                // Handle trailing comma before rest parameter
                if (match(TokenType.ELLIPSIS)) {
                    continue;
                }
            }
        }

        expect(TokenType.RPAREN);
        return new FunctionParams(params, defaults, restParameter);
    }

    private Identifier parseIdentifier() {
        SourceLocation location = getLocation();
        if (match(TokenType.IDENTIFIER)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        if (match(TokenType.ASYNC)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        if (match(TokenType.AWAIT)) {
            if (isAwaitIdentifierAllowed()) {
                String name = currentToken.value();
                advance();
                return new Identifier(name, location);
            }
            throw new JSSyntaxErrorException("Unexpected 'await' keyword");
        }
        throw new RuntimeException("Expected identifier but got " + currentToken.type() +
                " at line " + currentToken.line() + ", column " + currentToken.column());
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

    /**
     * Parse a labeled statement: label: statement
     * Following QuickJS js_parse_statement_or_decl label handling.
     * In non-strict mode, labeled function declarations are allowed (Annex B).
     */
    private Statement parseLabeledStatement() {
        SourceLocation location = getLocation();
        Identifier label = parseIdentifier();
        expect(TokenType.COLON);
        Statement body = parseStatement();
        return new LabeledStatement(label, body, location);
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

            // Parse member access after the callee so
            // `new Intl.DateTimeFormat()` binds as `new (Intl.DateTimeFormat)()`
            // instead of `(new Intl).DateTimeFormat()`.
            while (true) {
                if (match(TokenType.DOT)) {
                    advance();
                    SourceLocation memberLocation = getLocation();
                    if (!match(TokenType.IDENTIFIER)) {
                        throw new RuntimeException("Expected property name after '.' at line " +
                                currentToken.line() + ", column " + currentToken.column());
                    }
                    Expression property = new Identifier(currentToken.value(), memberLocation);
                    advance();
                    callee = new MemberExpression(callee, property, false, memberLocation);
                } else if (match(TokenType.LBRACKET)) {
                    advance();
                    SourceLocation memberLocation = getLocation();
                    Expression property = parseExpression();
                    expect(TokenType.RBRACKET);
                    callee = new MemberExpression(callee, property, true, memberLocation);
                } else {
                    break;
                }
            }

            List<Expression> args = new ArrayList<>();
            if (match(TokenType.LPAREN)) {
                advance();
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
        enterFunctionContext(false);
        BlockStatement body;
        try {
            body = parseBlockStatement();
        } finally {
            exitFunctionContext(false);
        }

        return new FunctionExpression(null, funcParams.params, funcParams.defaults, funcParams.restParameter, body, false, false, location);
    }

    // Utility methods

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
                enterFunctionContext(false);
                try {
                    value = parseAssignmentExpression();
                } finally {
                    exitFunctionContext(false);
                }
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

            // Check for getter/setter: get name() {} or set name(v) {}
            // Similar to parseClassElement logic
            if (!isAsync && !isGenerator && match(TokenType.IDENTIFIER)) {
                String name = currentToken.value();
                if (("get".equals(name) || "set".equals(name)) &&
                        nextToken.type() != TokenType.COLON &&
                        nextToken.type() != TokenType.COMMA &&
                        nextToken.type() != TokenType.LPAREN &&
                        nextToken.type() != TokenType.RBRACE) {
                    String kind = name;
                    advance(); // consume 'get' or 'set'

                    // Parse property name after get/set (may be computed)
                    boolean computed = match(TokenType.LBRACKET);
                    Expression key = parsePropertyName();

                    FunctionExpression value = parseMethod(kind);
                    properties.add(new ObjectExpression.Property(key, value, kind, computed, false));

                    if (match(TokenType.COMMA)) {
                        advance();
                    } else {
                        break;
                    }
                    continue;
                }
            }

            // Parse property name (can be identifier, string, number, or computed [expr])
            boolean computed = match(TokenType.LBRACKET);
            Expression key = parsePropertyName();

            // Determine if this is a method or regular property
            if (match(TokenType.LPAREN)) {
                // Method shorthand: name() {} or async name() {} or *name() {} or async *name() {}
                SourceLocation funcLocation = getLocation();
                enterFunctionContext(isAsync);
                Expression value;
                try {
                    expect(TokenType.LPAREN);
                    FunctionParams funcParams = parseFunctionParameters();
                    BlockStatement body = parseBlockStatement();
                    value = new FunctionExpression(null, funcParams.params, funcParams.defaults, funcParams.restParameter, body, isAsync, isGenerator, funcLocation);
                } finally {
                    exitFunctionContext(isAsync);
                }
                properties.add(new ObjectExpression.Property(key, value, "init", computed, false));
            } else if (match(TokenType.COLON)) {
                // Regular property: key: value
                advance();
                Expression value = parseAssignmentExpression();
                properties.add(new ObjectExpression.Property(key, value, "init", computed, false));
            } else if (!computed && key instanceof Identifier keyId
                    && (match(TokenType.COMMA) || match(TokenType.RBRACE) || match(TokenType.ASSIGN))) {
                // Shorthand property: {x} or CoverInitializedName: {x = defaultExpr}
                Expression value;
                if (match(TokenType.ASSIGN)) {
                    advance();
                    Expression defaultValue = parseAssignmentExpression();
                    value = new AssignmentExpression(keyId,
                            AssignmentExpression.AssignmentOperator.ASSIGN,
                            defaultValue, keyId.getLocation());
                } else {
                    value = keyId;
                }
                properties.add(new ObjectExpression.Property(key, value, "init", false, true));
            } else {
                // Fallback: expect colon
                expect(TokenType.COLON);
                Expression value = parseAssignmentExpression();
                properties.add(new ObjectExpression.Property(key, value, "init", computed, false));
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
                // Check for default value: { x: y = defaultVal }
                if (match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = getLocation();
                    advance(); // consume '='
                    Expression defaultValue = parseAssignmentExpression();
                    value = new AssignmentPattern(value, defaultValue, assignLoc);
                }
            } else {
                // Shorthand: { x } means { x: x }
                value = key;
                shorthand = true;
                // Check for default value: { x = defaultVal }
                if (match(TokenType.ASSIGN)) {
                    SourceLocation assignLoc = getLocation();
                    advance(); // consume '='
                    Expression defaultValue = parseAssignmentExpression();
                    value = new AssignmentPattern(value, defaultValue, assignLoc);
                }
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

        // Per ES spec 12.9.1: if a line terminator occurs between the operand
        // and the ++/-- operator, ASI inserts a semicolon before the operator.
        // Following QuickJS !s->got_lf check in postfix operator parsing.
        if (!hasNewlineBefore() && (match(TokenType.INC) || match(TokenType.DEC))) {
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
                String normalizedValue = value.replace("_", "");
                Object numValue;
                if (normalizedValue.startsWith("0x") || normalizedValue.startsWith("0X")) {
                    // Parse hex number - store as long or int
                    long longVal = Long.parseLong(normalizedValue.substring(2), 16);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (normalizedValue.startsWith("0b") || normalizedValue.startsWith("0B")) {
                    // Parse binary number - store as long or int
                    long longVal = Long.parseLong(normalizedValue.substring(2), 2);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else if (normalizedValue.startsWith("0o") || normalizedValue.startsWith("0O")) {
                    // Parse octal number - store as long or int
                    long longVal = Long.parseLong(normalizedValue.substring(2), 8);
                    numValue = (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE)
                            ? (int) longVal : (double) longVal;
                } else {
                    // Parse decimal number - use double
                    double doubleVal = Double.parseDouble(normalizedValue);
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
                String normalizedValue = value.replace("_", "");
                // Parse BigInt literal - handle different radixes
                java.math.BigInteger bigIntValue;
                if (normalizedValue.startsWith("0x") || normalizedValue.startsWith("0X")) {
                    bigIntValue = new BigInteger(normalizedValue.substring(2), 16);
                } else if (normalizedValue.startsWith("0b") || normalizedValue.startsWith("0B")) {
                    bigIntValue = new BigInteger(normalizedValue.substring(2), 2);
                } else if (normalizedValue.startsWith("0o") || normalizedValue.startsWith("0O")) {
                    bigIntValue = new BigInteger(normalizedValue.substring(2), 8);
                } else {
                    bigIntValue = new BigInteger(normalizedValue);
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
                yield new Literal(new RegExpLiteralValue(value), location);
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
            case AWAIT -> parseIdentifier();
            case PRIVATE_NAME -> {
                String name = currentToken.value();
                String fieldName = name.substring(1);
                advance();
                yield new PrivateIdentifier(fieldName, location);
            }
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
                        // ES spec: Expression[+In] inside parentheses
                        boolean savedIn = inOperatorAllowed;
                        inOperatorAllowed = true;
                        Expression expr = parseExpression();
                        inOperatorAllowed = savedIn;
                        expect(TokenType.RPAREN);
                        yield expr;
                    }

                    // Could be (id) or (id, id, ...) or (id, ...rest) or (id = default, ...)
                    // Parse as parameter list tentatively
                    List<Expression> potentialParams = new ArrayList<>();
                    Identifier firstParam = parseIdentifier();
                    // Check for default value on first param
                    if (match(TokenType.ASSIGN)) {
                        SourceLocation assignLoc = getLocation();
                        advance(); // consume '='
                        Expression defaultExpr = parseAssignmentExpression();
                        potentialParams.add(new AssignmentExpression(firstParam,
                                AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                    } else {
                        potentialParams.add(firstParam);
                    }

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
                            throw new RuntimeException("Complex arrow function parameters not yet supported at line " +
                                    currentToken.line() + ", column " + currentToken.column());
                        }
                        Identifier param = parseIdentifier();
                        // Check for default value
                        if (match(TokenType.ASSIGN)) {
                            SourceLocation assignLoc = getLocation();
                            advance(); // consume '='
                            Expression defaultExpr = parseAssignmentExpression();
                            potentialParams.add(new AssignmentExpression(param,
                                    AssignmentExpression.AssignmentOperator.ASSIGN, defaultExpr, assignLoc));
                        } else {
                            potentialParams.add(param);
                        }
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
                    // ES spec: Expression[+In] inside parentheses
                    boolean savedIn = inOperatorAllowed;
                    inOperatorAllowed = true;
                    Expression expr = parseExpression();
                    inOperatorAllowed = savedIn;
                    expect(TokenType.RPAREN);
                    yield expr;
                }
            }
            case LBRACKET -> parseArrayExpression();
            case LBRACE -> parseObjectExpression();
            case FUNCTION -> parseFunctionExpression();
            case CLASS -> parseClassExpression(); // Class expressions
            case SUPER -> {
                // Per ES spec, 'super' is only valid in specific contexts:
                // - super.property / super[expr] in methods (super_allowed)
                // - super() in derived class constructors (super_call_allowed)
                // Following QuickJS: check next token to give specific error messages
                advance(); // consume 'super'
                throw new JSSyntaxErrorException("'super' keyword unexpected here");
            }
            default -> {
                // Error case - return a literal undefined
                advance();
                yield new Literal(null, location);
            }
        };
    }

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
                (inOperatorAllowed && match(TokenType.IN)) || match(TokenType.INSTANCEOF)) {
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
        if (isAwaitUsingDeclarationStart()) {
            return parseUsingDeclaration(true);
        }
        if (isUsingDeclarationStart()) {
            return parseUsingDeclaration(false);
        }
        // Check for labeled statement: identifier followed by ':'
        // Following QuickJS is_label() check
        if (currentToken.type() == TokenType.IDENTIFIER && peek() != null && peek().type() == TokenType.COLON) {
            return parseLabeledStatement();
        }
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
            case ASYNC -> // Async function declaration: async function f() {}
                    parseAsyncDeclaration();
            case FUNCTION -> // Function declarations are treated as statements in JavaScript
                    parseFunctionDeclaration(false, false);
            case CLASS -> // Class declarations are treated as statements in JavaScript
                    parseClassDeclaration();
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

        enterFunctionContext(false);
        try {
            while (!match(TokenType.RBRACE) && !match(TokenType.EOF)) {
                Statement stmt = parseStatement();
                if (stmt != null) {
                    statements.add(stmt);
                }
            }
        } finally {
            exitFunctionContext(false);
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

    private Expression parseTemplateExpression(String expressionSource) {
        if (expressionSource.isBlank()) {
            throw new JSSyntaxErrorException("Empty template expression");
        }

        Lexer expressionLexer = new Lexer(expressionSource);
        expressionLexer.setStrictMode(strictMode);
        Parser expressionParser = new Parser(
                expressionLexer,
                moduleMode,
                functionNesting,
                asyncFunctionNesting);
        Expression expression = expressionParser.parseExpression();
        if (expressionParser.currentToken.type() != TokenType.EOF) {
            throw new JSSyntaxErrorException("Invalid template expression");
        }
        return expression;
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
            Pattern param = null;
            if (match(TokenType.LPAREN)) {
                advance();
                param = parsePattern();
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
            if (!isAwaitExpressionAllowed()) {
                if (isAwaitIdentifierAllowed() && isValidContinuationAfterAwaitIdentifier()) {
                    return parsePostfixExpression();
                }
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
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

    private Statement parseUsingDeclaration(boolean isAwaitUsing) {
        SourceLocation location = getLocation();
        VariableKind kind;
        if (isAwaitUsing) {
            if (!isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            expect(TokenType.AWAIT);
            if (!isUsingIdentifierToken(currentToken)) {
                throw new RuntimeException("Expected using declaration after await");
            }
            advance();
            kind = VariableKind.AWAIT_USING;
        } else {
            if (!isUsingIdentifierToken(currentToken)) {
                throw new RuntimeException("Expected using declaration");
            }
            advance();
            kind = VariableKind.USING;
        }
        return parseVariableDeclarationBody(kind, location);
    }

    private Statement parseVariableDeclaration() {
        SourceLocation location = getLocation();
        VariableKind kind = VariableKind.fromKeyword(currentToken.value()); // VAR, LET, or CONST
        advance();
        return parseVariableDeclarationBody(kind, location);
    }

    private Statement parseVariableDeclarationBody(VariableKind kind, SourceLocation location) {
        return parseVariableDeclarationBody(kind, location, true);
    }

    private VariableDeclaration parseVariableDeclarationBody(VariableKind kind, SourceLocation location, boolean consumeSemi) {
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

        if (consumeSemi) {
            consumeSemicolon();
        }
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
    private record FunctionParams(List<Identifier> params, List<Expression> defaults, RestParameter restParameter) {
    }
}
