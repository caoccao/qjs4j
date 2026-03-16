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
 * Shared mutable state for the parser.
 * Holds all fields, token utilities, validation methods, and context management
 * that are shared across the delegate parser classes.
 */
final class ParserContext {
    private static final Set<String> ALWAYS_RESERVED_IDENTIFIER_NAMES = Set.of(
            JSKeyword.BREAK,
            JSKeyword.CASE,
            JSKeyword.CATCH,
            JSKeyword.CLASS,
            JSKeyword.CONST,
            JSKeyword.CONTINUE,
            JSKeyword.DEBUGGER,
            JSKeyword.DEFAULT,
            JSKeyword.DELETE,
            JSKeyword.DO,
            JSKeyword.ELSE,
            JSKeyword.ENUM,
            JSKeyword.EXPORT,
            JSKeyword.EXTENDS,
            JSKeyword.FALSE,
            JSKeyword.FINALLY,
            JSKeyword.FOR,
            JSKeyword.FUNCTION,
            JSKeyword.IF,
            JSKeyword.IMPORT,
            JSKeyword.IN,
            JSKeyword.INSTANCEOF,
            JSKeyword.NEW,
            JSKeyword.NULL,
            JSKeyword.RETURN,
            JSKeyword.SUPER,
            JSKeyword.SWITCH,
            JSKeyword.THIS,
            JSKeyword.THROW,
            JSKeyword.TRUE,
            JSKeyword.TRY,
            JSKeyword.TYPEOF,
            JSKeyword.VAR,
            JSKeyword.VOID,
            JSKeyword.WHILE,
            JSKeyword.WITH
    );
    final boolean allowNewTargetInEval;
    final Set<String> evalPrivateNames;
    final boolean inheritedStrictMode;
    final boolean isEval;
    final Deque<Set<String>> labelStack = new ArrayDeque<>();
    final Lexer lexer;
    // Module-level early error tracking (ES2024 16.2.1.1)
    final Set<String> moduleExportedNames = new HashSet<>();
    final Set<String> moduleLexicalNames = new HashSet<>();
    final boolean moduleMode;
    final Set<String> moduleVarNames = new HashSet<>();
    final List<String> pendingExportBindings = new ArrayList<>();
    final Deque<int[]> savedFunctionNestingStack = new ArrayDeque<>();
    int asyncFunctionNesting;
    int classBodyNesting;
    Token currentToken;
    int functionNesting;
    int generatorFunctionNesting;
    boolean inClassFieldInitializer;
    boolean inClassStaticInit;
    boolean inDerivedConstructor;
    boolean inFunctionBody;
    boolean inOperatorAllowed;
    boolean needsArguments;
    int newTargetNesting;
    Token nextToken;
    boolean parsingClassWithSuper;
    int previousTokenEndOffset;
    int previousTokenLine;
    int statementNesting;
    boolean strictMode;
    boolean superPropertyAllowed;

    ParserContext(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode,
                  int functionNesting, int asyncFunctionNesting,
                  int generatorFunctionNesting,
                  int newTargetNesting,
                  boolean initialSuperPropertyAllowed,
                  boolean allowNewTargetInEval,
                  Set<String> evalPrivateNames) {
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

    void consumeSemicolon() {
        if (match(TokenType.SEMICOLON)) {
            advance();
            return;
        }
        if (hasNewlineBefore() || match(TokenType.RBRACE) || match(TokenType.EOF)) {
            return;
        }
        throw new JSSyntaxErrorException("Unexpected token '" + currentToken.value() + "'");
    }

    Token expect(TokenType type) {
        if (!match(type)) {
            throw new JSSyntaxErrorException("Expected " + type + " but got " + currentToken.type() +
                    " at line " + currentToken.line() + ", column " + currentToken.column());
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

    boolean isASIToken() {
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

    boolean isAlwaysReservedIdentifier(String name) {
        return isAlwaysReservedIdentifierName(name);
    }

    private boolean isAlwaysReservedIdentifierName(String name) {
        return ALWAYS_RESERVED_IDENTIFIER_NAMES.contains(name);
    }

    boolean isAssignmentOperator(TokenType type) {
        return type == TokenType.ASSIGN || type == TokenType.PLUS_ASSIGN ||
                type == TokenType.MINUS_ASSIGN || type == TokenType.MUL_ASSIGN ||
                type == TokenType.DIV_ASSIGN || type == TokenType.MOD_ASSIGN ||
                type == TokenType.EXP_ASSIGN || type == TokenType.LSHIFT_ASSIGN ||
                type == TokenType.RSHIFT_ASSIGN || type == TokenType.URSHIFT_ASSIGN ||
                type == TokenType.AND_ASSIGN || type == TokenType.OR_ASSIGN ||
                type == TokenType.XOR_ASSIGN || type == TokenType.LOGICAL_AND_ASSIGN ||
                type == TokenType.LOGICAL_OR_ASSIGN || type == TokenType.NULLISH_ASSIGN;
    }

    boolean isAwaitExpressionAllowed() {
        return asyncFunctionNesting > 0 || (moduleMode && functionNesting == 0);
    }

    boolean isAwaitIdentifierAllowed() {
        return !moduleMode && asyncFunctionNesting == 0 && !inClassStaticInit;
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
        return isBindingIdentifierStartToken(afterUsing)
                && afterUsing.line() == nextToken.line();
    }

    private boolean isBindingIdentifierStartToken(Token token) {
        return switch (token.type()) {
            case IDENTIFIER, ASYNC, AWAIT, YIELD, FROM, OF, AS, LET -> true;
            default -> false;
        };
    }

    boolean isExpressionStartToken(TokenType tokenType) {
        return switch (tokenType) {
            case ASYNC, AWAIT, BIGINT, CLASS, FALSE, FUNCTION, IDENTIFIER,
                 LBRACE, LBRACKET, LPAREN, NEW, NULL, NUMBER, REGEX,
                 STRING, TEMPLATE, THIS, TRUE -> true;
            default -> false;
        };
    }

    boolean isIdentifierPartChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    boolean isIdentifierStartChar(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029';
    }

    boolean isPatternStartToken(TokenType tokenType) {
        return tokenType == TokenType.IDENTIFIER
                || tokenType == TokenType.LBRACE
                || tokenType == TokenType.LBRACKET
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

    private boolean isStrictReservedIdentifierName(String name) {
        return switch (name) {
            case JSKeyword.IMPLEMENTS, JSKeyword.INTERFACE, JSKeyword.LET, JSKeyword.PACKAGE, JSKeyword.PRIVATE,
                 JSKeyword.PROTECTED, JSKeyword.PUBLIC, JSKeyword.STATIC, JSKeyword.YIELD -> true;
            default -> false;
        };
    }

    boolean isUsingDeclarationStart() {
        return currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.USING.equals(currentToken.value())
                && isBindingIdentifierStartToken(nextToken)
                && nextToken.line() == currentToken.line();
    }

    boolean isUsingIdentifierToken(Token token) {
        return token.type() == TokenType.IDENTIFIER && JSKeyword.USING.equals(token.value());
    }

    boolean isValidForInOfTarget(Expression expr) {
        if (expr instanceof Identifier identifier) {
            String name = identifier.getName();
            return !"import.meta".equals(name) && !"new.target".equals(name) && !JSKeyword.THIS.equals(name);
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

    boolean match(TokenType type) {
        return currentToken.type() == type;
    }

    /**
     * Parse directives at the beginning of a program or function.
     * Returns true if "use strict" directive was found.
     * Directive strings are also valid expression statements per the ES spec,
     * so they are added to the provided body list if non-null.
     */
    boolean parseDirectives() {
        return parseDirectives(null);
    }

    /**
     * Parse directives at the beginning of a program or function.
     * Returns true if "use strict" directive was found.
     * Directive strings are also valid expression statements per the ES spec,
     * so they are added to the provided body list if non-null.
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
            boolean hasSemi = false;

            if (next.type() == TokenType.SEMICOLON) {
                hasSemi = true;
            } else if (next.type() == TokenType.RBRACE || next.type() == TokenType.EOF) {
                hasSemi = true;
            } else {
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
                break;
            }

            prologueTokens.add(directiveToken);
            advance();
            if (match(TokenType.SEMICOLON)) {
                advance();
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
        if (match(TokenType.IDENTIFIER)) {
            String name = currentToken.value();
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
                throw new JSSyntaxErrorException("'arguments' is not allowed in class field initializer or static initialization block");
            }
            if (JSKeyword.ARGUMENTS.equals(name) || JSKeyword.EVAL.equals(name)) {
                needsArguments = true;
            }
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
        if (match(TokenType.YIELD)) {
            if (isYieldIdentifierAllowed()) {
                String name = currentToken.value();
                advance();
                return new Identifier(name, location);
            }
            if (strictMode) {
                throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
            }
            throw new JSSyntaxErrorException("Unexpected token 'yield'");
        }
        if (match(TokenType.FROM)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        if (match(TokenType.OF)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        if (match(TokenType.AS)) {
            String name = currentToken.value();
            advance();
            return new Identifier(name, location);
        }
        if (match(TokenType.LET)) {
            String name = currentToken.value();
            if (strictMode && isStrictReservedIdentifierName(name)) {
                throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
            }
            advance();
            return new Identifier(name, location);
        }
        throw new JSSyntaxErrorException("Unexpected token '" + currentToken.value() + "'");
    }

    Token peek() {
        return nextToken;
    }

}
