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
 * Grammar state, lexical scopes and early errors shared across productions in one parse.
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

    final DeclarationEarlyErrors declarations;
    final Set<String> evalPrivateNames;
    final ExpressionParser expressions;
    int functionNesting;
    final FunctionClassParser functions;
    int generatorFunctionNesting;
    boolean inClassFieldInitializer;
    boolean inClassStaticInit;
    boolean inDerivedConstructor;
    boolean inFunctionBody;
    final boolean inheritedStrictMode;
    boolean inOperatorAllowed;
    final boolean isEval;
    Deque<Set<String>> labelStack = new ArrayDeque<>();
    final LiteralParser literals;
    final boolean moduleMode;
    private ModuleDeclarationParser modules;
    boolean needsArguments;
    int newTargetNesting;
    private Set<Expression> parenthesizedExpressions;
    boolean parsingClassWithSuper;
    final PatternParser patterns;
    final ExpressionPrimaryParser primary;
    final Deque<int[]> savedFunctionNestingStack = new ArrayDeque<>();
    int statementNesting;
    final StatementParser statements;
    boolean strictMode;
    boolean superPropertyAllowed;
    final TokenCursor tokens;
    ParserContext(Lexer lexer, boolean moduleMode, boolean isEval, boolean inheritedStrictMode, int functionNesting,
            int asyncFunctionNesting, int generatorFunctionNesting, int newTargetNesting,
            boolean initialSuperPropertyAllowed, boolean allowNewTargetInEval, Set<String> evalPrivateNames) {
        inFunctionBody = true;
        inOperatorAllowed = true;
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
        tokens = new TokenCursor(lexer, moduleMode);
        declarations = new DeclarationEarlyErrors(this);
        statements = new StatementParser(this);
        patterns = new PatternParser(this);
        functions = new FunctionClassParser(this);
        expressions = new ExpressionParser(this);
        primary = new ExpressionPrimaryParser(this);
        literals = new LiteralParser(this);
    }

    PrivateIdentifier consumePrivateName() {
        SourceLocation location = tokens.getLocation();
        String fieldName = tokens.current().value().substring(1);
        if (!isPrivateNameAccessible(fieldName)) {
            throw new JSSyntaxErrorException("undefined private field '#" + fieldName + "'");
        }
        tokens.advance();
        return new PrivateIdentifier(fieldName, location);
    }

    void consumeSemicolon() {
        if (tokens.consumeIf(TokenType.SEMICOLON)) {
            return;
        }
        if (tokens.hasNewlineBefore() || tokens.match(TokenType.RBRACE) || tokens.match(TokenType.EOF)) {
            return;
        }
        throw new JSSyntaxErrorException(describeUnexpectedToken());
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
        String value = tokens.current().value();
        return switch (tokens.current().type()) {
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

    void enterFunctionContext(boolean asyncFunction, boolean generatorFunction) {
        savedFunctionNestingStack.push(new int[]{generatorFunctionNesting, asyncFunctionNesting, newTargetNesting,
                inClassFieldInitializer ? 1 : 0, inClassStaticInit ? 1 : 0, needsArguments ? 1 : 0});
        functionNesting++;
        generatorFunctionNesting = generatorFunction ? 1 : 0;
        asyncFunctionNesting = asyncFunction ? 1 : 0;
        newTargetNesting = 1;
        inClassFieldInitializer = false;
        inClassStaticInit = false;
        needsArguments = false;
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

    void enterLexicalFunction(boolean asyncFunction) {
        functionNesting++;
        if (asyncFunction) {
            asyncFunctionNesting++;
        }
    }

    void exitFunctionContext() {
        int[] saved = savedFunctionNestingStack.pop();
        generatorFunctionNesting = saved[0];
        asyncFunctionNesting = saved[1];
        newTargetNesting = saved[2];
        inClassFieldInitializer = saved[3] != 0;
        inClassStaticInit = saved[4] != 0;
        needsArguments = saved[5] != 0;
        functionNesting--;
    }

    void exitFunctionLabelScope(Deque<Set<String>> enclosingLabels) {
        labelStack = enclosingLabels;
    }

    void exitLexicalFunction(boolean asyncFunction) {
        if (asyncFunction) {
            asyncFunctionNesting--;
        }
        functionNesting--;
    }

    boolean isAlwaysReservedIdentifier(String name) {
        return isAlwaysReservedIdentifierName(name);
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

    boolean isParenthesizedExpression(Expression expression) {
        return parenthesizedExpressions != null && parenthesizedExpressions.contains(expression);
    }

    boolean isPrivateNameAccessible(String privateName) {
        if (classBodyNesting > 0) {
            return true;
        }
        return isEval && evalPrivateNames.contains(privateName);
    }

    boolean isStrictReservedIdentifier(String name) {
        return isStrictReservedIdentifierName(name);
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
            if (parenthesizedExpressions == null) {
                parenthesizedExpressions = Collections.newSetFromMap(new IdentityHashMap<>());
            }
            parenthesizedExpressions.add(expression);
        }
    }

    ModuleDeclarationParser modules() {
        if (modules == null) {
            modules = new ModuleDeclarationParser(this);
        }
        return modules;
    }

    String parseBindingIdentifierName() {
        String name = tokens.current().value();
        switch (tokens.current().type()) {
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
        tokens.advance();
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

        while (tokens.match(TokenType.STRING)) {
            Token directiveToken = tokens.current();
            String stringValue = tokens.current().value();
            int stringLine = tokens.current().line();
            SourceLocation directiveLocation = tokens.getLocation();

            Token next = tokens.next();
            TokenType nextType = next.type();
            boolean hasSemi = nextType == TokenType.SEMICOLON || nextType == TokenType.RBRACE
                    || nextType == TokenType.EOF || next.line() > stringLine && TokenCursor.isASIToken(nextType);
            if (!hasSemi) {
                break;
            }

            prologueTokens.add(directiveToken);
            tokens.advance();
            tokens.consumeIf(TokenType.SEMICOLON);

            // Directive strings are also expression statements per ES spec.
            // This is important for eval() which returns the completion value.
            if (body != null) {
                Expression literal = new Literal(stringValue, directiveLocation);
                body.add(new ExpressionStatement(literal, directiveLocation));
            }

            if (tokens.isRawUseStrictDirective(directiveToken)) {
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
        SourceLocation location = tokens.getLocation();
        return new Identifier(parseBindingIdentifierName(), location);
    }

    void setStrictMode(boolean strict) {
        strictMode = strict;
        tokens.setStrictMode(strict);
    }

    void validateBindingIdentifier(String name) {
        validateBindingIdentifier(name, false);
    }

    void validateBindingIdentifier(String name, boolean isAsyncContext) {
        if (inClassStaticInit && JSKeyword.AWAIT.equals(name)) {
            throw new JSSyntaxErrorException("Unexpected 'await' keyword");
        }
        if (isAsyncContext && JSKeyword.AWAIT.equals(name)) {
            throw new JSSyntaxErrorException("'await' is not a valid identifier name in an async function");
        }
        if (ParserContext.isAlwaysReservedIdentifierName(name)) {
            throw new JSSyntaxErrorException("Unexpected reserved word");
        }
        if (strictMode && ParserContext.isStrictReservedIdentifierName(name)) {
            throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
        }
    }

    private void validateIdentifierName(String name) {
        if (isAlwaysReservedIdentifierName(name)) {
            // ES2024 12.7.1: Escaped keywords are tokenized as IDENTIFIER but
            // their StringValue still matches a reserved word — use V8's message.
            tokens.requireUnescapedToken("Keyword must not contain escaped characters");
            // V8 reports "Unexpected reserved word" for enum (a future reserved word)
            // but "Unexpected token 'X'" for regular keywords (with, debugger, etc.)
            if (JSKeyword.ENUM.equals(name)) {
                throw new JSSyntaxErrorException("Unexpected reserved word");
            }
            throw new JSSyntaxErrorException("Unexpected token '" + name + "'");
        }
        if (strictMode && isStrictReservedIdentifierName(name)) {
            tokens.requireUnescapedToken("Keyword must not contain escaped characters");
            throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
        }
        // Contextual keywords via unicode escapes: use V8's escaped keyword message.
        if (JSKeyword.AWAIT.equals(name) && !isAwaitIdentifierAllowed()) {
            tokens.requireUnescapedToken("Keyword must not contain escaped characters");
            throw new JSSyntaxErrorException("Unexpected 'await' keyword");
        }
        if (JSKeyword.YIELD.equals(name) && !isYieldIdentifierAllowed()) {
            tokens.requireUnescapedToken("Keyword must not contain escaped characters");
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

    static boolean isAlwaysReservedIdentifierName(String name) {
        return ALWAYS_RESERVED_IDENTIFIER_NAMES.contains(name);
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
