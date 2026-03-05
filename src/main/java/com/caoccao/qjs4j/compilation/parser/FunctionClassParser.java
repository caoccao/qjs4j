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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delegate parser for function declarations/expressions and class declarations/expressions.
 * Extracted from the monolithic Parser class as part of the parser refactoring.
 */
record FunctionClassParser(ParserContext parserContext, ParserDelegates delegates) {

    /**
     * Extract all bound identifier names from a pattern.
     * Used for strict mode parameter validation.
     */
    private static List<String> extractBoundNames(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            return List.of(id.name());
        } else if (pattern instanceof ObjectPattern objPattern) {
            List<String> names = new ArrayList<>();
            for (ObjectPattern.Property prop : objPattern.properties()) {
                names.addAll(extractBoundNames(prop.value()));
            }
            if (objPattern.restElement() != null) {
                names.addAll(extractBoundNames(objPattern.restElement().argument()));
            }
            return names;
        } else if (pattern instanceof ArrayPattern arrPattern) {
            List<String> names = new ArrayList<>();
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    names.addAll(extractBoundNames(element));
                }
            }
            return names;
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            return extractBoundNames(assignPattern.left());
        } else if (pattern instanceof RestElement restElement) {
            return extractBoundNames(restElement.argument());
        }
        return List.of();
    }

    /**
     * Check for duplicate parameter names.
     * Following QuickJS js_parse_function_check_names:
     * duplicates are rejected when strict mode, non-simple parameter list,
     * arrow function, or method.
     *
     * @param funcParams The function parameters to validate
     */
    private void checkDuplicateParameters(FunctionParams funcParams) {
        Set<String> seen = new HashSet<>();
        for (Pattern param : funcParams.params()) {
            List<String> boundNames = extractBoundNames(param);
            for (String paramName : boundNames) {
                if (!seen.add(paramName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
            }
        }
        if (funcParams.restParameter() != null) {
            List<String> restBoundNames = extractBoundNames(funcParams.restParameter().argument());
            for (String restName : restBoundNames) {
                if (!seen.add(restName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
            }
        }
    }

    /**
     * Validate function parameters for strict mode rules.
     * Following QuickJS js_parse_function_check_names:
     * - No parameter named 'eval' or 'arguments'
     * - No duplicate parameter names
     *
     * @param funcParams The function parameters to validate
     * @param funcName   The function name (or null for anonymous)
     */
    private void checkStrictModeParameters(FunctionParams funcParams, Identifier funcName) {
        // Check function name
        if (funcName != null) {
            String name = funcName.name();
            if (JSKeyword.EVAL.equals(name) || JSKeyword.ARGUMENTS.equals(name)) {
                throw new JSSyntaxErrorException("invalid function name in strict code");
            }
        }

        // Check parameter names for reserved names
        for (Pattern param : funcParams.params()) {
            List<String> boundNames = extractBoundNames(param);
            for (String paramName : boundNames) {
                if (JSKeyword.EVAL.equals(paramName) || JSKeyword.ARGUMENTS.equals(paramName)) {
                    throw new JSSyntaxErrorException("invalid argument name in strict code");
                }
            }
        }
        if (funcParams.restParameter() != null) {
            List<String> restBoundNames = extractBoundNames(funcParams.restParameter().argument());
            for (String restName : restBoundNames) {
                if (JSKeyword.EVAL.equals(restName) || JSKeyword.ARGUMENTS.equals(restName)) {
                    throw new JSSyntaxErrorException("invalid argument name in strict code");
                }
            }
        }

        // Check for duplicates (strict mode always rejects duplicates)
        checkDuplicateParameters(funcParams);
    }

    /**
     * Check if a parameter list is simple (no defaults, no destructuring, no rest).
     */
    private boolean hasUseStrictDirective(BlockStatement blockStatement) {
        for (Statement statement : blockStatement.body()) {
            if (statement instanceof ExpressionStatement expressionStatement
                    && expressionStatement.expression() instanceof Literal literal
                    && literal.value() instanceof String literalString) {
                if (JSKeyword.USE_STRICT.equals(literalString)) {
                    return true;
                }
                continue;
            }
            break;
        }
        return false;
    }

    private boolean isSimpleParameterList(FunctionParams funcParams) {
        if (funcParams.restParameter() != null) {
            return false;
        }
        for (Pattern param : funcParams.params()) {
            if (!(param instanceof Identifier)) {
                return false;
            }
        }
        for (Expression defaultExpression : funcParams.defaults()) {
            if (defaultExpression != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse a class declaration or expression.
     * Syntax: class Name extends Super { body }
     */
    ClassDeclaration parseClassDeclaration() {
        SourceLocation startLocation = parserContext.getLocation();
        int startOffset = parserContext.currentToken.offset();
        parserContext.expect(TokenType.CLASS);

        // Parse optional class name
        // Per ES spec 14.6: class bodies are strict mode code, so the class name
        // binding identifier is validated with strict mode rules.
        Identifier id = null;
        if (parserContext.match(TokenType.IDENTIFIER) || parserContext.match(TokenType.AWAIT)
                || parserContext.match(TokenType.YIELD)) {
            boolean savedStrict = parserContext.strictMode;
            parserContext.strictMode = true;
            try {
                id = parserContext.parseIdentifier();
            } finally {
                parserContext.strictMode = savedStrict;
            }
        }

        // Parse optional extends clause
        Expression superClass = null;
        if (parserContext.match(TokenType.EXTENDS)) {
            parserContext.advance();
            superClass = delegates.expressions.parseMemberExpression();
        }

        // Parse class body
        parserContext.expect(TokenType.LBRACE);
        List<ClassDeclaration.ClassElement> body = new ArrayList<>();
        boolean savedParsingClassWithSuper = parserContext.parsingClassWithSuper;
        parserContext.parsingClassWithSuper = superClass != null;

        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            // Skip empty semicolons
            if (parserContext.match(TokenType.SEMICOLON)) {
                parserContext.advance();
                continue;
            }

            ClassDeclaration.ClassElement element = parseClassElement();
            if (element != null) {
                body.add(element);
            }
        }

        parserContext.parsingClassWithSuper = savedParsingClassWithSuper;

        // Capture the end position before advancing past the closing brace
        int endOffset = parserContext.currentToken.offset() + parserContext.currentToken.value().length();
        parserContext.expect(TokenType.RBRACE);

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
    ClassDeclaration.ClassElement parseClassElement() {
        boolean isStatic = false;
        boolean isPrivate = false;
        SourceLocation location = parserContext.getLocation();

        // Check for 'static' keyword
        if (parserContext.match(TokenType.IDENTIFIER) && JSKeyword.STATIC.equals(parserContext.currentToken.value())) {
            parserContext.advance();
            isStatic = true;

            // Check for static block: static { }
            if (parserContext.match(TokenType.LBRACE)) {
                return parseStaticBlock();
            }

            // Handle 'static' as a property name (e.g., static = 42;)
            if (parserContext.match(TokenType.ASSIGN) || parserContext.match(TokenType.SEMICOLON)) {
                isStatic = false;
                // Parse as field with name "static"
                Expression key = new Identifier(JSKeyword.STATIC, location);
                Expression value = null;
                if (parserContext.match(TokenType.ASSIGN)) {
                    parserContext.advance();
                    value = delegates.expressions.parseAssignmentExpression();
                }
                parserContext.consumeSemicolon();
                return new ClassDeclaration.PropertyDefinition(key, value, false, isStatic, false);
            }
        }

        // Capture method start location AFTER consuming 'static' (if any).
        // This is where method source code should begin (excludes 'static').
        SourceLocation methodStartLocation = parserContext.getLocation();

        // Check for private identifier (#name)
        if (parserContext.match(TokenType.PRIVATE_NAME)) {
            isPrivate = true;
            String privateName = parserContext.currentToken.value();
            // Remove the # prefix for the identifier
            String name = privateName.substring(1);
            parserContext.advance();

            Expression key = new PrivateIdentifier(name, methodStartLocation);
            return parseMethodOrField(key, isStatic, isPrivate, true, methodStartLocation);
        }

        // Check for async method: async name() {} or async *name() {}
        if (parserContext.match(TokenType.ASYNC)) {
            // Disambiguate: async as method modifier vs async as property name
            TokenType nextType = parserContext.nextToken.type();
            if (nextType == TokenType.ASSIGN || nextType == TokenType.SEMICOLON
                    || nextType == TokenType.COLON || nextType == TokenType.COMMA) {
                // async = value; or async; or async: value — property named "async"
                Expression key = new Identifier(JSKeyword.ASYNC, methodStartLocation);
                parserContext.advance();
                return parseMethodOrField(key, isStatic, isPrivate, false, methodStartLocation);
            }

            parserContext.advance(); // consume 'async'

            boolean isGenerator = false;
            if (parserContext.match(TokenType.MUL)) {
                parserContext.advance(); // consume '*'
                isGenerator = true;
            }

            // Parse property name
            boolean computed = parserContext.match(TokenType.LBRACKET);
            Expression key;
            if (parserContext.match(TokenType.PRIVATE_NAME)) {
                String privateName = parserContext.currentToken.value();
                String keyName = privateName.substring(1);
                key = new PrivateIdentifier(keyName, parserContext.getLocation());
                isPrivate = true;
                parserContext.advance();
            } else {
                key = delegates.expressions.parsePropertyName();
            }

            FunctionExpression method = parseMethod("method", methodStartLocation, true, isGenerator);
            return new ClassDeclaration.MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
        }

        // Check for generator method: *name() {}
        if (parserContext.match(TokenType.MUL)) {
            parserContext.advance(); // consume '*'

            boolean computed = parserContext.match(TokenType.LBRACKET);
            Expression key;
            if (parserContext.match(TokenType.PRIVATE_NAME)) {
                String privateName = parserContext.currentToken.value();
                String keyName = privateName.substring(1);
                key = new PrivateIdentifier(keyName, parserContext.getLocation());
                isPrivate = true;
                parserContext.advance();
            } else {
                key = delegates.expressions.parsePropertyName();
            }

            FunctionExpression method = parseMethod("method", methodStartLocation, false, true);
            return new ClassDeclaration.MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
        }

        // Check for computed property name [expr]
        if (parserContext.match(TokenType.LBRACKET)) {
            parserContext.advance();
            // Allow 'in' operator inside computed property names
            boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
            parserContext.inOperatorAllowed = true;
            Expression key = delegates.expressions.parseAssignmentExpression();
            parserContext.inOperatorAllowed = savedInOperatorAllowed;
            parserContext.expect(TokenType.RBRACKET);
            return parseMethodOrField(key, isStatic, isPrivate, true, methodStartLocation);
        }

        // Check for getter/setter
        if (parserContext.match(TokenType.IDENTIFIER)) {
            String name = parserContext.currentToken.value();
            Token peekNext = parserContext.nextToken;
            if ((JSKeyword.GET.equals(name) || JSKeyword.SET.equals(name)) &&
                    peekNext.type() != TokenType.LPAREN &&
                    peekNext.type() != TokenType.ASSIGN &&
                    peekNext.type() != TokenType.SEMICOLON) {
                String kind = name;
                parserContext.advance(); // consume 'get' or 'set'

                Expression key;
                boolean computed = false;

                // Parse property name after get/set
                if (parserContext.match(TokenType.PRIVATE_NAME)) {
                    String privateName = parserContext.currentToken.value();
                    String keyName = privateName.substring(1);
                    key = new PrivateIdentifier(keyName, parserContext.getLocation());
                    isPrivate = true;
                    parserContext.advance();
                } else if (parserContext.match(TokenType.LBRACKET)) {
                    parserContext.advance();
                    // Allow 'in' operator inside computed property names
                    boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                    parserContext.inOperatorAllowed = true;
                    key = delegates.expressions.parseAssignmentExpression();
                    parserContext.inOperatorAllowed = savedInOperatorAllowed;
                    parserContext.expect(TokenType.RBRACKET);
                    computed = true;
                } else {
                    key = delegates.expressions.parsePropertyName();
                }

                FunctionExpression method = parseMethod(kind, methodStartLocation, false, false);
                return new ClassDeclaration.MethodDefinition(key, method, kind, computed, isStatic, isPrivate);
            }
        }

        // Regular property name (identifier, string, number, or reserved keyword)
        boolean computed = parserContext.match(TokenType.LBRACKET);
        Expression key = delegates.expressions.parsePropertyName();

        return parseMethodOrField(key, isStatic, isPrivate, computed, methodStartLocation);
    }

    /**
     * Parse a class expression (class used as an expression).
     * Syntax: class [Name] [extends Super] { body }
     */
    ClassExpression parseClassExpression() {
        SourceLocation startLocation = parserContext.getLocation();
        int startOffset = parserContext.currentToken.offset();
        parserContext.expect(TokenType.CLASS);

        // Parse optional class name (class expressions can be anonymous)
        // Per ES spec 14.6: class bodies are strict mode code, so the class name
        // binding identifier is validated with strict mode rules.
        Identifier id = null;
        if (parserContext.match(TokenType.IDENTIFIER) || parserContext.match(TokenType.AWAIT)
                || parserContext.match(TokenType.YIELD)) {
            boolean savedStrict = parserContext.strictMode;
            parserContext.strictMode = true;
            try {
                id = parserContext.parseIdentifier();
            } finally {
                parserContext.strictMode = savedStrict;
            }
        }

        // Parse optional extends clause
        Expression superClass = null;
        if (parserContext.match(TokenType.EXTENDS)) {
            parserContext.advance();
            superClass = delegates.expressions.parseMemberExpression();
        }

        // Parse class body
        parserContext.expect(TokenType.LBRACE);
        List<ClassDeclaration.ClassElement> body = new ArrayList<>();
        boolean savedParsingClassWithSuper2 = parserContext.parsingClassWithSuper;
        parserContext.parsingClassWithSuper = superClass != null;

        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            // Skip empty semicolons
            if (parserContext.match(TokenType.SEMICOLON)) {
                parserContext.advance();
                continue;
            }

            ClassDeclaration.ClassElement element = parseClassElement();
            if (element != null) {
                body.add(element);
            }
        }

        parserContext.parsingClassWithSuper = savedParsingClassWithSuper2;

        // Capture the end position before advancing past the closing brace
        int endOffset = parserContext.currentToken.offset() + parserContext.currentToken.value().length();
        parserContext.expect(TokenType.RBRACE);

        SourceLocation location = new SourceLocation(
                startLocation.line(),
                startLocation.column(),
                startOffset,
                endOffset
        );

        return new ClassExpression(id, superClass, body, location);
    }

    /**
     * Parse a function body with "use strict" directive detection and parameter validation.
     * Following QuickJS: after parsing '{', check for directives, then validate parameters
     * if strict mode was detected (js_parse_function_check_names).
     *
     * @param funcParams The already-parsed function parameters
     * @param funcName   The function name (or null for anonymous)
     * @return The parsed BlockStatement
     */
    BlockStatement parseFunctionBody(FunctionParams funcParams, Identifier funcName) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();

        // Save outer strict mode so we can restore it after parsing the function body
        boolean savedStrictMode = parserContext.strictMode;

        // Parse directives (like "use strict") at the beginning of the function body
        boolean hasUseStrict = parserContext.parseDirectives(body);

        // If "use strict" directive found, retroactively validate parameters
        // Following QuickJS js_parse_function_check_names
        if (hasUseStrict) {
            parserContext.strictMode = true;
            parserContext.lexer.setStrictMode(true);
        }

        // Validate parameters when in strict mode (either from outer context or "use strict" directive)
        if (hasUseStrict || savedStrictMode) {
            checkStrictModeParameters(funcParams, funcName);
        } else if (!isSimpleParameterList(funcParams)) {
            // Per ES2024 15.2.1: It is a Syntax Error if IsSimpleParameterList is false
            // and BoundNames contains any duplicate elements.
            // Following QuickJS js_parse_function_check_names: duplicates are always
            // rejected for non-simple parameter lists regardless of strict mode.
            checkDuplicateParameters(funcParams);
        }

        // Non-simple parameter list with "use strict" directive is always an error (spec 15.2.1)
        if (hasUseStrict && !isSimpleParameterList(funcParams)) {
            throw new JSSyntaxErrorException(
                    "Illegal 'use strict' directive in function with non-simple parameter list");
        }

        // Parse remaining body statements
        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Statement stmt = delegates.statements.parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        // Check that parameter names don't conflict with lexical declarations in body
        // Per spec: "It is a Syntax Error if BoundNames of FormalParameters also occurs
        // in the LexicallyDeclaredNames of FunctionBody"
        validateFormalsBodyDuplicate(funcParams, body);

        // Restore outer strict mode
        parserContext.strictMode = savedStrictMode;
        parserContext.lexer.setStrictMode(savedStrictMode);

        parserContext.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator) {
        return parseFunctionDeclaration(isAsync, isGenerator, null);
    }

    FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator, SourceLocation startLocation) {
        // Use provided start location (for async functions) or get current location
        SourceLocation location = startLocation != null ? startLocation : parserContext.getLocation();
        parserContext.expect(TokenType.FUNCTION);

        // Check for generator function: function* or async function*
        // Following QuickJS implementation: check for '*' after 'function' keyword
        if (parserContext.match(TokenType.MUL)) {
            parserContext.advance();
            isGenerator = true;
        }

        // Parse the function name in the ENCLOSING scope context, before entering
        // the function context. Per ES spec, the BindingIdentifier of a FunctionDeclaration
        // is bound in the enclosing scope, so contextual keyword checks (await/yield)
        // must use the enclosing scope's rules. This allows `async function await() {}`
        // at global scope while rejecting `function await() {}` inside async functions.
        Identifier id = parserContext.parseIdentifier();

        parserContext.enterFunctionContext(isAsync, isGenerator);
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = false;
        try {
            // Per QuickJS: in_function_body == FALSE prevents yield/await during
            // the parsing of the arguments in generator/async functions.
            boolean savedInFunctionBody = parserContext.inFunctionBody;
            if (isAsync || isGenerator) {
                parserContext.inFunctionBody = false;
            }

            parserContext.expect(TokenType.LPAREN);
            FunctionParams funcParams = parseFunctionParameters();

            parserContext.inFunctionBody = savedInFunctionBody;

            // Parse body with "use strict" detection and parameter validation
            BlockStatement body = parseFunctionBody(funcParams, id);

            // Use previousTokenEndOffset (position after closing '}') instead of
            // currentToken.offset() (position of NEXT token, which may include trailing comments)
            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    parserContext.previousTokenEndOffset
            );

            return new FunctionDeclaration(id, funcParams.params(), funcParams.defaults(), funcParams.restParameter(), body, isAsync, isGenerator, fullLocation);
        } finally {
            parserContext.inClassStaticInit = savedInClassStaticInit;
            parserContext.exitFunctionContext(isAsync, isGenerator);
        }
    }

    Expression parseFunctionExpression() {
        return parseFunctionExpression(false, null);
    }

    Expression parseFunctionExpression(boolean isAsync, SourceLocation startLocation) {
        SourceLocation location = startLocation != null ? startLocation : parserContext.getLocation();
        parserContext.expect(TokenType.FUNCTION);

        // Check for generator function expression: function* () {}
        // Following QuickJS implementation: check for '*' after 'function' keyword
        boolean isGenerator = false;
        if (parserContext.match(TokenType.MUL)) {
            parserContext.advance();
            isGenerator = true;
        }

        parserContext.enterFunctionContext(isAsync, isGenerator);
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = false;
        try {
            Identifier id = null;
            if (parserContext.match(TokenType.IDENTIFIER) || parserContext.match(TokenType.AWAIT)
                    || parserContext.match(TokenType.YIELD)) {
                id = parserContext.parseIdentifier();
            }

            // Per QuickJS: in_function_body == FALSE prevents yield/await during
            // the parsing of the arguments in generator/async functions.
            boolean savedInFunctionBody = parserContext.inFunctionBody;
            if (isAsync || isGenerator) {
                parserContext.inFunctionBody = false;
            }

            parserContext.expect(TokenType.LPAREN);
            FunctionParams funcParams = parseFunctionParameters();

            parserContext.inFunctionBody = savedInFunctionBody;

            // Parse body with "use strict" detection and parameter validation
            BlockStatement body = parseFunctionBody(funcParams, id);

            // Use previousTokenEndOffset (position after closing '}') instead of
            // currentToken.offset() (which may include trailing comments)
            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    parserContext.previousTokenEndOffset
            );

            return new FunctionExpression(id, funcParams.params(), funcParams.defaults(), funcParams.restParameter(), body, isAsync, isGenerator, fullLocation);
        } finally {
            parserContext.inClassStaticInit = savedInClassStaticInit;
            parserContext.exitFunctionContext(isAsync, isGenerator);
        }
    }

    /**
     * Parse function parameters, including optional rest parameter.
     * Expects '(' to already be consumed.
     * Consumes up to and including ')'.
     *
     * @return FunctionParams containing regular params and optional rest parameter
     */
    FunctionParams parseFunctionParameters() {
        List<Pattern> params = new ArrayList<>();
        List<Expression> defaults = new ArrayList<>();
        RestParameter restParameter = null;

        if (!parserContext.match(TokenType.RPAREN)) {
            while (true) {
                // Check for rest parameter (...args)
                if (parserContext.match(TokenType.ELLIPSIS)) {
                    SourceLocation location = parserContext.getLocation();
                    parserContext.advance(); // consume '...'
                    Pattern restArg;
                    if (parserContext.match(TokenType.LBRACE) || parserContext.match(TokenType.LBRACKET)) {
                        restArg = delegates.patterns.parsePattern();
                    } else {
                        restArg = parserContext.parseIdentifier();
                    }
                    restParameter = new RestParameter(restArg, location);

                    // Rest parameter must be last
                    if (parserContext.match(TokenType.COMMA)) {
                        throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                    }
                    break;
                }

                // Regular parameter or destructuring pattern
                // Following QuickJS js_parse_function_decl2: accept identifiers,
                // object patterns ({a, b}), and array patterns ([a, b])
                if (parserContext.match(TokenType.LBRACE) || parserContext.match(TokenType.LBRACKET)) {
                    params.add(delegates.patterns.parsePattern());
                } else {
                    params.add(parserContext.parseIdentifier());
                }

                // Check for default value: param = defaultExpr
                // Following QuickJS js_parse_function_decl2 pattern
                if (parserContext.match(TokenType.ASSIGN)) {
                    parserContext.advance(); // consume '='
                    defaults.add(delegates.expressions.parseAssignmentExpression());
                } else {
                    defaults.add(null);
                }

                // Check for comma (more parameters)
                if (!parserContext.match(TokenType.COMMA)) {
                    break;
                }
                parserContext.advance(); // consume comma

                // Handle trailing comma before closing paren
                if (parserContext.match(TokenType.RPAREN)) {
                    break;
                }
                // Handle trailing comma before rest parameter
                if (parserContext.match(TokenType.ELLIPSIS)) {
                    continue;
                }
            }
        }

        parserContext.expect(TokenType.RPAREN);
        return new FunctionParams(params, defaults, restParameter);
    }

    /**
     * Parse a method (constructor, method, getter, or setter).
     * Uses the current token position as the method start for source extraction.
     */
    FunctionExpression parseMethod(String kind) {
        return parseMethod(kind, parserContext.getLocation(), false, false);
    }

    /**
     * Parse a method with explicit start location and async/generator flags.
     * The methodStartLocation determines where the source code begins
     * (e.g., at 'get', 'set', 'async', '*', or the method name).
     */
    FunctionExpression parseMethod(String kind, SourceLocation methodStartLocation,
                                   boolean isAsync, boolean isGenerator) {
        // Parse parameter list
        // Per QuickJS: in_function_body == FALSE prevents yield/await during
        // the parsing of the arguments in generator/async functions.
        boolean savedInFunctionBody = parserContext.inFunctionBody;
        if (isAsync || isGenerator) {
            parserContext.inFunctionBody = false;
        }

        parserContext.expect(TokenType.LPAREN);
        FunctionParams funcParams = parseFunctionParameters();

        parserContext.inFunctionBody = savedInFunctionBody;

        // Per QuickJS js_parse_function_check_names: methods always reject duplicate parameters
        checkDuplicateParameters(funcParams);

        // Parse method body
        parserContext.enterFunctionContext(isAsync, isGenerator);
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = false;
        BlockStatement body;
        try {
            body = delegates.statements.parseBlockStatement();
        } finally {
            parserContext.inClassStaticInit = savedInClassStaticInit;
            parserContext.exitFunctionContext(isAsync, isGenerator);
        }

        // Per ES2024 15.2.1: "use strict" in method body with non-simple parameters
        // is a SyntaxError. Methods are already strict (class bodies are strict mode),
        // but we must still check for the explicit directive + non-simple params combo.
        if (!isSimpleParameterList(funcParams) && hasUseStrictDirective(body)) {
            throw new JSSyntaxErrorException(
                    "Illegal 'use strict' directive in function with non-simple parameter list");
        }

        SourceLocation fullLocation = new SourceLocation(
                methodStartLocation.line(),
                methodStartLocation.column(),
                methodStartLocation.offset(),
                parserContext.previousTokenEndOffset
        );

        return new FunctionExpression(null, funcParams.params(), funcParams.defaults(),
                funcParams.restParameter(), body, isAsync, isGenerator, fullLocation);
    }

    /**
     * Parse method or field after the property name.
     */
    ClassDeclaration.ClassElement parseMethodOrField(Expression key, boolean isStatic,
                                                     boolean isPrivate, boolean computed,
                                                     SourceLocation location) {
        // If next token is LPAREN, it's a method; otherwise it's a field
        if (!parserContext.match(TokenType.LPAREN)) {
            // It's a field (with optional initializer)
            Expression value = null;
            if (parserContext.match(TokenType.ASSIGN)) {
                parserContext.advance();
                boolean savedInClassFieldInitializer = parserContext.inClassFieldInitializer;
                parserContext.inClassFieldInitializer = true;
                parserContext.enterFunctionContext(false);
                try {
                    value = delegates.expressions.parseAssignmentExpression();
                } finally {
                    parserContext.exitFunctionContext(false);
                    parserContext.inClassFieldInitializer = savedInClassFieldInitializer;
                }
            }
            parserContext.consumeSemicolon();
            return new ClassDeclaration.PropertyDefinition(key, value, computed, isStatic, isPrivate);
        }

        // It's a method
        // If this is a constructor in a derived class, enable super() calls
        boolean isConstructorMethod = !isStatic && key instanceof Identifier keyId && JSKeyword.CONSTRUCTOR.equals(keyId.name());
        boolean savedInDerivedConstructor = parserContext.inDerivedConstructor;
        boolean savedSuperPropertyAllowed = parserContext.superPropertyAllowed;
        if (isConstructorMethod && parserContext.parsingClassWithSuper) {
            parserContext.inDerivedConstructor = true;
        }
        parserContext.superPropertyAllowed = true;
        FunctionExpression method = parseMethod("method", location, false, false);
        parserContext.inDerivedConstructor = savedInDerivedConstructor;
        parserContext.superPropertyAllowed = savedSuperPropertyAllowed;
        return new ClassDeclaration.MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
    }

    /**
     * Parse a static initialization block: static { statements }
     */
    ClassDeclaration.StaticBlock parseStaticBlock() {
        parserContext.expect(TokenType.LBRACE);
        List<Statement> statements = new ArrayList<>();

        parserContext.enterFunctionContext(false);
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = true;
        try {
            while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                Statement stmt = delegates.statements.parseStatement();
                if (stmt != null) {
                    statements.add(stmt);
                }
            }
        } finally {
            parserContext.inClassStaticInit = savedInClassStaticInit;
            parserContext.exitFunctionContext(false);
        }

        parserContext.expect(TokenType.RBRACE);
        return new ClassDeclaration.StaticBlock(statements);
    }

    /**
     * Check that parameter BoundNames do not also appear in the LexicallyDeclaredNames of the body.
     * Per spec: "It is a Syntax Error if BoundNames of FormalParameters also occurs in the
     * LexicallyDeclaredNames of FunctionBody."
     */
    private void validateFormalsBodyDuplicate(FunctionParams funcParams, List<Statement> body) {
        Set<String> paramNames = new HashSet<>();
        for (Pattern param : funcParams.params()) {
            paramNames.addAll(extractBoundNames(param));
        }
        if (funcParams.restParameter() != null) {
            paramNames.addAll(extractBoundNames(funcParams.restParameter().argument()));
        }
        if (paramNames.isEmpty()) {
            return;
        }
        for (Statement statement : body) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && (variableDeclaration.kind() == VariableKind.LET
                    || variableDeclaration.kind() == VariableKind.CONST)) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.declarations()) {
                    for (String declaredName : extractBoundNames(declarator.id())) {
                        if (paramNames.contains(declaredName)) {
                            throw new JSSyntaxErrorException("invalid redefinition of parameter name");
                        }
                    }
                }
            }
        }
    }
}
