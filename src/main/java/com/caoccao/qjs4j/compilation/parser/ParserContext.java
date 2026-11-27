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
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.*;

/**
 * Shared mutable state for the parser. Holds all fields, token utilities, validation methods, and context management
 * that are shared across the delegate parser classes.
 */
final class ParserContext {
    private static final Set<String> ALWAYS_RESERVED_IDENTIFIER_NAMES = Set.of(JSKeyword.BREAK, JSKeyword.CASE,
            JSKeyword.CATCH, JSKeyword.CLASS, JSKeyword.CONST, JSKeyword.CONTINUE, JSKeyword.DEBUGGER,
            JSKeyword.DEFAULT, JSKeyword.DELETE, JSKeyword.DO, JSKeyword.ELSE, JSKeyword.ENUM, JSKeyword.EXPORT,
            JSKeyword.EXTENDS, JSKeyword.FALSE, JSKeyword.FINALLY, JSKeyword.FOR, JSKeyword.FUNCTION, JSKeyword.IF,
            JSKeyword.IMPORT, JSKeyword.IN, JSKeyword.INSTANCEOF, JSKeyword.NEW, JSKeyword.NULL, JSKeyword.RETURN,
            JSKeyword.SUPER, JSKeyword.SWITCH, JSKeyword.THIS, JSKeyword.THROW, JSKeyword.TRUE, JSKeyword.TRY,
            JSKeyword.TYPEOF, JSKeyword.VAR, JSKeyword.VOID, JSKeyword.WHILE, JSKeyword.WITH);
    final boolean allowNewTargetInEval;
    int asyncFunctionNesting;
    int classBodyNesting;
    Token currentToken;
    final Set<String> evalPrivateNames;
    int functionNesting;
    int generatorFunctionNesting;
    boolean inClassFieldInitializer;
    boolean inClassStaticInit;
    boolean inDerivedConstructor;
    boolean inFunctionBody;
    final boolean inheritedStrictMode;
    boolean inOperatorAllowed;
    final boolean isEval;
    Deque<Set<String>> labelStack = new ArrayDeque<>();
    final Lexer lexer;
    // Module-level early error tracking (ES2024 16.2.1.1)
    final Set<String> moduleExportedNames = new HashSet<>();
    final Set<String> moduleLexicalNames = new HashSet<>();
    final boolean moduleMode;
    final Set<String> moduleVarNames = new HashSet<>();
    boolean needsArguments;
    int newTargetNesting;
    Token nextToken;
    final Set<Expression> parenthesizedExpressions;
    boolean parsingClassWithSuper;
    final List<String> pendingExportBindings = new ArrayList<>();
    int previousTokenEndOffset;
    int previousTokenLine;
    final Deque<int[]> savedFunctionNestingStack = new ArrayDeque<>();
    int statementNesting;
    boolean strictMode;
    boolean superPropertyAllowed;

    ParserContext(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode, int functionNesting,
            int asyncFunctionNesting, int generatorFunctionNesting, int newTargetNesting,
            boolean initialSuperPropertyAllowed, boolean allowNewTargetInEval, Set<String> evalPrivateNames) {
        inFunctionBody = true;
        inOperatorAllowed = true;
        this.lexer = lexer;
        this.moduleMode = moduleMode;
        this.isEval = isEval;
        this.allowNewTargetInEval = allowNewTargetInEval;
        this.inheritedStrictMode = inheritedStrictMode;
        this.functionNesting = functionNesting;
        this.asyncFunctionNesting = asyncFunctionNesting;
        this.generatorFunctionNesting = generatorFunctionNesting;
        this.newTargetNesting = newTargetNesting;
        this.superPropertyAllowed = initialSuperPropertyAllowed;
        this.evalPrivateNames = evalPrivateNames != null ? Set.copyOf(evalPrivateNames) : Set.of();
        this.parenthesizedExpressions = Collections.newSetFromMap(new IdentityHashMap<>());
        lexer.setModuleMode(moduleMode);
        this.currentToken = lexer.nextToken();
        this.nextToken = lexer.nextToken();
    }

    void advance() {
        previousTokenLine = currentToken.line();
        previousTokenEndOffset = currentToken.offset() + currentToken.value().length();
        currentToken = nextToken;
        nextToken = lexer.nextToken();
    }

    Identifier consumeIdentifierName() {
        SourceLocation location = getLocation();
        String name = currentToken.value();
        advance();
        return new Identifier(name, location);
    }

    boolean consumeIf(TokenType type) {
        if (!match(type)) {
            return false;
        }
        advance();
        return true;
    }

    SourceLocation consumeLocation() {
        SourceLocation location = getLocation();
        advance();
        return location;
    }

    PrivateIdentifier consumePrivateName() {
        SourceLocation location = getLocation();
        String fieldName = currentToken.value().substring(1);
        if (!isPrivateNameAccessible(fieldName)) {
            throw new JSSyntaxErrorException("undefined private field '#" + fieldName + "'");
        }
        advance();
        return new PrivateIdentifier(fieldName, location);
    }

    void consumeSemicolon() {
        if (consumeIf(TokenType.SEMICOLON)) {
            return;
        }
        if (hasNewlineBefore() || match(TokenType.RBRACE) || match(TokenType.EOF)) {
            return;
        }
        throw new JSSyntaxErrorException(describeUnexpectedToken());
    }

    String consumeValue() {
        String value = currentToken.value();
        advance();
        return value;
    }

    /**
     * Name the current token the way a diagnostic should, when it is the token that cannot follow.
     * <p>
     * Quoting the raw text for everything is wrong for whole categories: a number, a string or a template has no name
     * worth quoting, and a word that is reserved is reserved rather than unexpected-as-written —
     * {@code export {} let x = 1;} is rejected because {@code let} is a reserved word in module code, not because a
     * token called "let" turned up. The categories below are the ones ECMAScript itself distinguishes, and they are
     * what V8 reports too, so differential tests can compare the message rather than only the error type.
     *
     * @return the diagnostic message
     */
    String describeUnexpectedToken() {
        String value = currentToken.value();
        return switch (currentToken.type()) {
            case EOF -> "Unexpected end of input";
            case NUMBER, BIGINT -> "Unexpected number";
            case STRING -> "Unexpected string";
            case TEMPLATE -> "Unexpected template string";
            case REGEX -> "Unexpected regular expression";
            case AWAIT -> "Unexpected reserved word";
            // `as` and `from` carry their own token types here only because module declarations
            // need to spot them; everywhere else they are ordinary identifiers, and this is
            // everywhere else. `let` and `yield` are routed through the same check so that whether
            // they are reserved is decided by the language mode rather than by the token type.
            case IDENTIFIER, PRIVATE_NAME, AS, FROM, LET, YIELD -> describeUnexpectedWord(value);
            default -> "Unexpected token '" + value + "'";
        };
    }

    /**
     * Name a word-shaped token: reserved, reserved in strict mode only, or an identifier.
     *
     * @param name
     *            the word
     * @return the diagnostic message
     */
    private String describeUnexpectedWord(String name) {
        if (JSKeyword.ENUM.equals(name)) {
            return "Unexpected reserved word";
        }
        if (strictMode && isStrictReservedIdentifierName(name)) {
            return "Unexpected strict mode reserved word";
        }
        if (JSKeyword.GET.equals(name) || JSKeyword.SET.equals(name)) {
            // Scanned as identifiers here, but they are contextual keywords of the grammar and are
            // named as tokens, like `of` and `async` which do have their own token types.
            return "Unexpected token '" + name + "'";
        }
        return "Unexpected identifier '" + name + "'";
    }

    /**
     * Start a function body with no labels in scope. Labels are per function (ES2024 14.13.1 ContainsDuplicateLabels
     * does not look into nested functions), so {@code foo: { function f() { foo: {} } }} is legal.
     *
     * @return the enclosing labels, to restore with {@link #exitFunctionLabelScope(Deque)}
     */
    Deque<Set<String>> enterFunctionLabelScope() {
        Deque<Set<String>> enclosingLabels = labelStack;
        labelStack = new ArrayDeque<>();
        return enclosingLabels;
    }

    void exitFunctionLabelScope(Deque<Set<String>> enclosingLabels) {
        labelStack = enclosingLabels;
    }

    Token expect(TokenType type) {
        if (!match(type)) {
            if (currentToken.type() == TokenType.EOF) {
                // Source that simply stops early is not a token mismatch, and naming the token type
                // the parser wanted describes the parser rather than the program. `export {} from`
                // reported "Expected STRING but got EOF at line 1, column 15" where every engine
                // says the input ended.
                throw new JSSyntaxErrorException("Unexpected end of input");
            }
            throw new JSSyntaxErrorException("Expected " + type + " but got " + currentToken.type() + " at line "
                    + currentToken.line() + ", column " + currentToken.column());
        }
        Token token = currentToken;
        advance();
        return token;
    }

    SourceLocation getLocation() {
        return new SourceLocation(currentToken.line(), currentToken.column(), currentToken.offset());
    }

    boolean hasNewlineBefore() {
        return currentToken.line() > previousTokenLine;
    }

    boolean isAlwaysReservedIdentifier(String name) {
        return isAlwaysReservedIdentifierName(name);
    }

    boolean isASIToken() {
        return isASIToken(currentToken.type());
    }

    boolean isAwaitExpressionAllowed() {
        if (inClassFieldInitializer || inClassStaticInit) {
            return false;
        } else {
            return asyncFunctionNesting > 0 || (moduleMode && functionNesting == 0);
        }
    }

    boolean isAwaitIdentifierAllowed() {
        if (moduleMode) {
            return false;
        } else if (inClassStaticInit) {
            return false;
        } else if (inClassFieldInitializer) {
            return true;
        } else {
            return asyncFunctionNesting == 0;
        }
    }

    boolean isAwaitUsingDeclarationStart() {
        boolean awaitToken = match(TokenType.AWAIT)
                || (match(TokenType.IDENTIFIER) && JSKeyword.AWAIT.equals(currentToken.value()));
        if (!awaitToken || !isUsingIdentifierToken(nextToken)) {
            return false;
        }
        // [no LineTerminator here] between 'await' and 'using'
        if (nextToken.line() != currentToken.line()) {
            return false;
        }
        // [no LineTerminator here] between 'using' and BindingIdentifier
        // Also verify the next token is a valid BindingIdentifier start.
        Token afterUsing = lexer.peekToken();
        return isBindingIdentifierStartToken(afterUsing) && afterUsing.line() == nextToken.line();
    }

    private boolean isBindingIdentifierStartToken(Token token) {
        return switch (token.type()) {
            case IDENTIFIER, ASYNC, AWAIT, YIELD, FROM, OF, AS, LET -> true;
            default -> false;
        };
    }

    boolean isExpressionStartToken(TokenType tokenType) {
        return switch (tokenType) {
            case ASYNC, AWAIT, BIGINT, CLASS, FALSE, FUNCTION, IDENTIFIER, LBRACE, LBRACKET, LPAREN, NEW, NULL, NUMBER,
                    REGEX, STRING, TEMPLATE, THIS, TRUE ->
                true;
            default -> false;
        };
    }

    private boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029';
    }

    boolean isParenthesizedExpression(Expression expression) {
        return expression != null && parenthesizedExpressions.contains(expression);
    }

    boolean isPatternStartToken(TokenType tokenType) {
        return tokenType == TokenType.IDENTIFIER || tokenType == TokenType.LBRACE || tokenType == TokenType.LBRACKET
                || tokenType == TokenType.AWAIT;
    }

    boolean isPrivateNameAccessible(String privateName) {
        if (classBodyNesting > 0) {
            return true;
        }
        return isEval && evalPrivateNames.contains(privateName);
    }

    private boolean isRawUseStrictDirective(Token directiveToken) {
        if (directiveToken.type() != TokenType.STRING || !JSKeyword.USE_STRICT.equals(directiveToken.value())) {
            return false;
        }

        int offset = directiveToken.offset();
        String source = lexer.getSource();
        if (offset < 0 || offset >= source.length()) {
            return false;
        }

        char quote = source.charAt(offset);
        if (quote != '\'' && quote != '"') {
            return false;
        }

        final String strictDirective = JSKeyword.USE_STRICT;
        int sourceIndex = offset + 1;
        int directiveIndex = 0;

        while (sourceIndex < source.length()) {
            char current = source.charAt(sourceIndex);
            if (current == quote) {
                return directiveIndex == strictDirective.length();
            }
            if (current == '\\' || isLineTerminator(current)) {
                return false;
            }
            if (directiveIndex >= strictDirective.length() || current != strictDirective.charAt(directiveIndex)) {
                return false;
            }
            sourceIndex++;
            directiveIndex++;
        }

        return false;
    }

    boolean isStrictReservedIdentifier(String name) {
        return isStrictReservedIdentifierName(name);
    }

    boolean isUsingDeclarationStart() {
        return currentToken.type() == TokenType.IDENTIFIER && JSKeyword.USING.equals(currentToken.value())
                && isBindingIdentifierStartToken(nextToken) && nextToken.line() == currentToken.line();
    }

    boolean isUsingIdentifierToken(Token token) {
        return token.type() == TokenType.IDENTIFIER && JSKeyword.USING.equals(token.value());
    }

    boolean isValidForInOfTarget(Expression expr) {
        if (expr instanceof Identifier) {
            return isIdentifierReference(expr);
        }
        if (expr instanceof MemberExpression) {
            return true;
        }
        if (expr instanceof ArrayExpression || expr instanceof ObjectExpression) {
            return true;
        }
        return !strictMode && expr instanceof CallExpression;
    }

    boolean isYieldIdentifierAllowed() {
        return !strictMode && generatorFunctionNesting == 0;
    }

    void markParenthesizedExpression(Expression expression) {
        if (expression != null) {
            parenthesizedExpressions.add(expression);
        }
    }

    boolean match(TokenType type) {
        return currentToken.type() == type;
    }

    String parseBindingIdentifierName() {
        String name = currentToken.value();
        switch (currentToken.type()) {
            case IDENTIFIER -> validateIdentifierName(name);
            case ASYNC, FROM, OF, AS -> {
                // Contextual keywords can bind a name here.
            }
            case AWAIT -> {
                if (!isAwaitIdentifierAllowed()) {
                    throw new JSSyntaxErrorException("Unexpected 'await' keyword");
                }
            }
            case YIELD -> {
                if (!isYieldIdentifierAllowed()) {
                    throw new JSSyntaxErrorException(
                            strictMode ? "Unexpected strict mode reserved word" : "Unexpected token 'yield'");
                }
            }
            case LET -> {
                if (strictMode && isStrictReservedIdentifierName(name)) {
                    throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
                }
            }
            default -> throw new JSSyntaxErrorException("Unexpected token '" + name + "'");
        }
        advance();
        return name;
    }

    /**
     * Parse directives at the beginning of a program or function. Returns true if "use strict" directive was found.
     * Directive strings are also valid expression statements per the ES spec, so they are added to the provided body
     * list if non-null.
     */
    boolean parseDirectives() {
        return parseDirectives(null);
    }

    /**
     * Parse directives at the beginning of a program or function. Returns true if "use strict" directive was found.
     * Directive strings are also valid expression statements per the ES spec, so they are added to the provided body
     * list if non-null.
     */
    boolean parseDirectives(List<Statement> body) {
        boolean hasUseStrict = false;
        List<Token> prologueTokens = new ArrayList<>();

        while (match(TokenType.STRING)) {
            Token directiveToken = currentToken;
            String stringValue = currentToken.value();
            int stringLine = currentToken.line();
            SourceLocation directiveLocation = getLocation();

            Token next = peek();
            TokenType nextType = next.type();
            boolean hasSemi = nextType == TokenType.SEMICOLON || nextType == TokenType.RBRACE
                    || nextType == TokenType.EOF || next.line() > stringLine && isASIToken(nextType);
            if (!hasSemi) {
                break;
            }

            prologueTokens.add(directiveToken);
            advance();
            if (consumeIf(TokenType.SEMICOLON)) {
            }

            // Directive strings are also expression statements per ES spec.
            // This is important for eval() which returns the completion value.
            if (body != null) {
                Expression literal = new Literal(stringValue, directiveLocation);
                body.add(new ExpressionStatement(literal, directiveLocation));
            }

            if (isRawUseStrictDirective(directiveToken)) {
                hasUseStrict = true;
            }
        }

        // Retroactively check: if "use strict" was found, any preceding directive
        // string with legacy octal/non-octal escapes is a SyntaxError.
        if (hasUseStrict) {
            for (Token token : prologueTokens) {
                if (token.hasOctalEscape()) {
                    throw new JSSyntaxErrorException("Octal escape sequences are not allowed in strict mode");
                }
            }
        }

        return hasUseStrict;
    }

    Identifier parseIdentifier() {
        SourceLocation location = getLocation();
        return new Identifier(parseBindingIdentifierName(), location);
    }

    Token peek() {
        return nextToken;
    }

    private void validateIdentifierName(String name) {
        if (isAlwaysReservedIdentifierName(name)) {
            // ES2024 12.7.1: Escaped keywords are tokenized as IDENTIFIER but
            // their StringValue still matches a reserved word — use V8's message.
            if (currentToken.escaped()) {
                throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
            }
            // V8 reports "Unexpected reserved word" for enum (a future reserved word)
            // but "Unexpected token 'X'" for regular keywords (with, debugger, etc.)
            if (JSKeyword.ENUM.equals(name)) {
                throw new JSSyntaxErrorException("Unexpected reserved word");
            }
            throw new JSSyntaxErrorException("Unexpected token '" + name + "'");
        }
        if (strictMode && isStrictReservedIdentifierName(name)) {
            if (currentToken.escaped()) {
                throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
            }
            throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
        }
        // Contextual keywords via unicode escapes: use V8's escaped keyword message.
        if (JSKeyword.AWAIT.equals(name) && !isAwaitIdentifierAllowed()) {
            if (currentToken.escaped()) {
                throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
            }
            throw new JSSyntaxErrorException("Unexpected 'await' keyword");
        }
        if (JSKeyword.YIELD.equals(name) && !isYieldIdentifierAllowed()) {
            if (currentToken.escaped()) {
                throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
            }
            if (strictMode) {
                throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
            }
            throw new JSSyntaxErrorException("Unexpected token 'yield'");
        }
        // ES2024 14.7.1 Static Semantics: ContainsArguments
        // 'arguments' is forbidden in class field initializers (including arrows)
        if (JSKeyword.ARGUMENTS.equals(name) && (inClassFieldInitializer || inClassStaticInit)) {
            throw new JSSyntaxErrorException(
                    "'arguments' is not allowed in class field initializer or static initialization block");
        }
        if (JSKeyword.ARGUMENTS.equals(name) || JSKeyword.EVAL.equals(name)) {
            needsArguments = true;
        }
    }

    static AssignmentOperator assignmentOperator(TokenType type) {
        return switch (type) {
            case ASSIGN -> AssignmentOperator.ASSIGN;
            case AND_ASSIGN -> AssignmentOperator.AND_ASSIGN;
            case DIV_ASSIGN -> AssignmentOperator.DIV_ASSIGN;
            case EXP_ASSIGN -> AssignmentOperator.EXP_ASSIGN;
            case LOGICAL_AND_ASSIGN -> AssignmentOperator.LOGICAL_AND_ASSIGN;
            case LOGICAL_OR_ASSIGN -> AssignmentOperator.LOGICAL_OR_ASSIGN;
            case LSHIFT_ASSIGN -> AssignmentOperator.LSHIFT_ASSIGN;
            case MINUS_ASSIGN -> AssignmentOperator.MINUS_ASSIGN;
            case MOD_ASSIGN -> AssignmentOperator.MOD_ASSIGN;
            case MUL_ASSIGN -> AssignmentOperator.MUL_ASSIGN;
            case NULLISH_ASSIGN -> AssignmentOperator.NULLISH_ASSIGN;
            case OR_ASSIGN -> AssignmentOperator.OR_ASSIGN;
            case PLUS_ASSIGN -> AssignmentOperator.PLUS_ASSIGN;
            case RSHIFT_ASSIGN -> AssignmentOperator.RSHIFT_ASSIGN;
            case URSHIFT_ASSIGN -> AssignmentOperator.URSHIFT_ASSIGN;
            case XOR_ASSIGN -> AssignmentOperator.XOR_ASSIGN;
            default -> null;
        };
    }

    static boolean isAlwaysReservedIdentifierName(String name) {
        return ALWAYS_RESERVED_IDENTIFIER_NAMES.contains(name);
    }

    private static boolean isASIToken(TokenType type) {
        return switch (type) {
            case NUMBER, STRING, IDENTIFIER, INC, DEC, NULL, FALSE, TRUE, IF, RETURN, VAR, THIS, DELETE, TYPEOF, NEW,
                    DO, WHILE, FOR, SWITCH, THROW, TRY, FUNCTION, CLASS, CONST, LET ->
                true;
            default -> false;
        };
    }

    /**
     * Whether an expression is an {@code IdentifierReference}.
     * <p>
     * {@code this}, {@code new.target} and {@code import.meta} are parsed into {@link Identifier} nodes because they
     * resolve like one, but grammatically they are a {@code PrimaryExpression} and a {@code MetaProperty}, not an
     * {@code IdentifierReference}. Rules that are stated over {@code IdentifierReference} — the strict-mode
     * {@code delete} early error, the {@code for}-{@code in}/{@code of} assignment target — must not catch them.
     *
     * @param expression
     *            the expression to classify
     * @return true when the expression is a real identifier reference
     */
    static boolean isIdentifierReference(Expression expression) {
        if (!(expression instanceof Identifier identifier)) {
            return false;
        }
        String name = identifier.getName();
        return !"import.meta".equals(name) && !"new.target".equals(name) && !JSKeyword.THIS.equals(name);
    }

    static boolean isStrictReservedIdentifierName(String name) {
        return switch (name) {
            case JSKeyword.IMPLEMENTS, JSKeyword.INTERFACE, JSKeyword.LET, JSKeyword.PACKAGE, JSKeyword.PRIVATE,
                    JSKeyword.PROTECTED, JSKeyword.PUBLIC, JSKeyword.STATIC, JSKeyword.YIELD ->
                true;
            default -> false;
        };
    }
}
