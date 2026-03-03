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
            if ("eval".equals(name) || "arguments".equals(name)) {
                throw new JSSyntaxErrorException("invalid function name in strict code");
            }
        }

        // Check parameter names
        Set<String> seen = new HashSet<>();
        for (Pattern param : funcParams.params()) {
            List<String> boundNames = extractBoundNames(param);
            for (String paramName : boundNames) {
                // Check for reserved names
                if ("eval".equals(paramName) || "arguments".equals(paramName)) {
                    throw new JSSyntaxErrorException("invalid argument name in strict code");
                }
                // Check for duplicates
                if (!seen.add(paramName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
            }
        }
        // Check rest parameter
        if (funcParams.restParameter() != null) {
            List<String> restBoundNames = extractBoundNames(funcParams.restParameter().argument());
            for (String restName : restBoundNames) {
                if ("eval".equals(restName) || "arguments".equals(restName)) {
                    throw new JSSyntaxErrorException("invalid argument name in strict code");
                }
                if (!seen.add(restName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
            }
        }
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
        Identifier id = null;
        if (parserContext.match(TokenType.IDENTIFIER)) {
            String name = parserContext.currentToken.value();
            parserContext.advance();
            id = new Identifier(name, startLocation);
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
        if (parserContext.match(TokenType.IDENTIFIER) && "static".equals(parserContext.currentToken.value())) {
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
                Expression key = new Identifier("static", location);
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
                Expression key = new Identifier("async", methodStartLocation);
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
            Expression key = delegates.expressions.parseAssignmentExpression();
            parserContext.expect(TokenType.RBRACKET);
            return parseMethodOrField(key, isStatic, isPrivate, true, methodStartLocation);
        }

        // Check for getter/setter
        if (parserContext.match(TokenType.IDENTIFIER)) {
            String name = parserContext.currentToken.value();
            Token peekNext = parserContext.nextToken;
            if (("get".equals(name) || "set".equals(name)) &&
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
                    key = delegates.expressions.parseAssignmentExpression();
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
        Identifier id = null;
        if (parserContext.match(TokenType.IDENTIFIER)) {
            String name = parserContext.currentToken.value();
            parserContext.advance();
            id = new Identifier(name, startLocation);
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
            checkStrictModeParameters(funcParams, funcName);
        }

        // Parse remaining body statements
        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Statement stmt = delegates.statements.parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

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

        parserContext.enterFunctionContext(isAsync, isGenerator);
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = false;
        try {
            Identifier id = parserContext.parseIdentifier();

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
        // Check if it's a field (has = or semicolon)
        if (parserContext.match(TokenType.ASSIGN) || parserContext.match(TokenType.SEMICOLON)) {
            Expression value = null;
            if (parserContext.match(TokenType.ASSIGN)) {
                parserContext.advance();
                parserContext.enterFunctionContext(false);
                try {
                    value = delegates.expressions.parseAssignmentExpression();
                } finally {
                    parserContext.exitFunctionContext(false);
                }
            }
            parserContext.consumeSemicolon();
            return new ClassDeclaration.PropertyDefinition(key, value, computed, isStatic, isPrivate);
        }

        // Otherwise, it's a method
        // If this is a constructor in a derived class, enable super() calls
        boolean isConstructorMethod = !isStatic && key instanceof Identifier keyId && "constructor".equals(keyId.name());
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
}
