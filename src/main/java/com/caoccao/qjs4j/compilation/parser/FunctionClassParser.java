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
record FunctionClassParser(ParserContext ctx, ParserDelegates delegates) {

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
            String restName = funcParams.restParameter().argument().name();
            if ("eval".equals(restName) || "arguments".equals(restName)) {
                throw new JSSyntaxErrorException("invalid argument name in strict code");
            }
            if (!seen.add(restName)) {
                throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
            }
        }
    }

    /**
     * Parse a class declaration or expression.
     * Syntax: class Name extends Super { body }
     */
    ClassDeclaration parseClassDeclaration() {
        SourceLocation startLocation = ctx.getLocation();
        int startOffset = ctx.currentToken.offset();
        ctx.expect(TokenType.CLASS);

        // Parse optional class name
        Identifier id = null;
        if (ctx.match(TokenType.IDENTIFIER)) {
            String name = ctx.currentToken.value();
            ctx.advance();
            id = new Identifier(name, startLocation);
        }

        // Parse optional extends clause
        Expression superClass = null;
        if (ctx.match(TokenType.EXTENDS)) {
            ctx.advance();
            superClass = delegates.expressions.parseMemberExpression();
        }

        // Parse class body
        ctx.expect(TokenType.LBRACE);
        List<ClassDeclaration.ClassElement> body = new ArrayList<>();
        boolean savedParsingClassWithSuper = ctx.parsingClassWithSuper;
        ctx.parsingClassWithSuper = superClass != null;

        while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            // Skip empty semicolons
            if (ctx.match(TokenType.SEMICOLON)) {
                ctx.advance();
                continue;
            }

            ClassDeclaration.ClassElement element = parseClassElement();
            if (element != null) {
                body.add(element);
            }
        }

        ctx.parsingClassWithSuper = savedParsingClassWithSuper;

        // Capture the end position before advancing past the closing brace
        int endOffset = ctx.currentToken.offset() + ctx.currentToken.value().length();
        ctx.expect(TokenType.RBRACE);

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
        SourceLocation location = ctx.getLocation();

        // Check for 'static' keyword
        if (ctx.match(TokenType.IDENTIFIER) && "static".equals(ctx.currentToken.value())) {
            ctx.advance();
            isStatic = true;

            // Check for static block: static { }
            if (ctx.match(TokenType.LBRACE)) {
                return parseStaticBlock();
            }

            // Handle 'static' as a property name (e.g., static = 42;)
            if (ctx.match(TokenType.ASSIGN) || ctx.match(TokenType.SEMICOLON)) {
                isStatic = false;
                // Parse as field with name "static"
                Expression key = new Identifier("static", location);
                Expression value = null;
                if (ctx.match(TokenType.ASSIGN)) {
                    ctx.advance();
                    value = delegates.expressions.parseAssignmentExpression();
                }
                ctx.consumeSemicolon();
                return new ClassDeclaration.PropertyDefinition(key, value, false, isStatic, false);
            }
        }

        // Capture method start location AFTER consuming 'static' (if any).
        // This is where method source code should begin (excludes 'static').
        SourceLocation methodStartLocation = ctx.getLocation();

        // Check for private identifier (#name)
        if (ctx.match(TokenType.PRIVATE_NAME)) {
            isPrivate = true;
            String privateName = ctx.currentToken.value();
            // Remove the # prefix for the identifier
            String name = privateName.substring(1);
            ctx.advance();

            Expression key = new PrivateIdentifier(name, methodStartLocation);
            return parseMethodOrField(key, isStatic, isPrivate, true, methodStartLocation);
        }

        // Check for async method: async name() {} or async *name() {}
        if (ctx.match(TokenType.ASYNC)) {
            // Disambiguate: async as method modifier vs async as property name
            TokenType nextType = ctx.nextToken.type();
            if (nextType == TokenType.ASSIGN || nextType == TokenType.SEMICOLON
                    || nextType == TokenType.COLON || nextType == TokenType.COMMA) {
                // async = value; or async; or async: value — property named "async"
                Expression key = new Identifier("async", methodStartLocation);
                ctx.advance();
                return parseMethodOrField(key, isStatic, isPrivate, false, methodStartLocation);
            }

            ctx.advance(); // consume 'async'

            boolean isGenerator = false;
            if (ctx.match(TokenType.MUL)) {
                ctx.advance(); // consume '*'
                isGenerator = true;
            }

            // Parse property name
            boolean computed = ctx.match(TokenType.LBRACKET);
            Expression key;
            if (ctx.match(TokenType.PRIVATE_NAME)) {
                String privateName = ctx.currentToken.value();
                String keyName = privateName.substring(1);
                key = new PrivateIdentifier(keyName, ctx.getLocation());
                isPrivate = true;
                ctx.advance();
            } else {
                key = delegates.expressions.parsePropertyName();
            }

            FunctionExpression method = parseMethod("method", methodStartLocation, true, isGenerator);
            return new ClassDeclaration.MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
        }

        // Check for generator method: *name() {}
        if (ctx.match(TokenType.MUL)) {
            ctx.advance(); // consume '*'

            boolean computed = ctx.match(TokenType.LBRACKET);
            Expression key;
            if (ctx.match(TokenType.PRIVATE_NAME)) {
                String privateName = ctx.currentToken.value();
                String keyName = privateName.substring(1);
                key = new PrivateIdentifier(keyName, ctx.getLocation());
                isPrivate = true;
                ctx.advance();
            } else {
                key = delegates.expressions.parsePropertyName();
            }

            FunctionExpression method = parseMethod("method", methodStartLocation, false, true);
            return new ClassDeclaration.MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
        }

        // Check for computed property name [expr]
        if (ctx.match(TokenType.LBRACKET)) {
            ctx.advance();
            Expression key = delegates.expressions.parseAssignmentExpression();
            ctx.expect(TokenType.RBRACKET);
            return parseMethodOrField(key, isStatic, isPrivate, true, methodStartLocation);
        }

        // Check for getter/setter
        if (ctx.match(TokenType.IDENTIFIER)) {
            String name = ctx.currentToken.value();
            Token peekNext = ctx.nextToken;
            if (("get".equals(name) || "set".equals(name)) &&
                    peekNext.type() != TokenType.LPAREN &&
                    peekNext.type() != TokenType.ASSIGN &&
                    peekNext.type() != TokenType.SEMICOLON) {
                String kind = name;
                ctx.advance(); // consume 'get' or 'set'

                Expression key;
                boolean computed = false;

                // Parse property name after get/set
                if (ctx.match(TokenType.PRIVATE_NAME)) {
                    String privateName = ctx.currentToken.value();
                    String keyName = privateName.substring(1);
                    key = new PrivateIdentifier(keyName, ctx.getLocation());
                    isPrivate = true;
                    ctx.advance();
                } else if (ctx.match(TokenType.LBRACKET)) {
                    ctx.advance();
                    key = delegates.expressions.parseAssignmentExpression();
                    ctx.expect(TokenType.RBRACKET);
                    computed = true;
                } else if (ctx.match(TokenType.IDENTIFIER) || ctx.match(TokenType.STRING)) {
                    key = new Identifier(ctx.currentToken.value(), ctx.getLocation());
                    ctx.advance();
                } else {
                    throw new RuntimeException("Expected property name after '" + kind + "'");
                }

                FunctionExpression method = parseMethod(kind, methodStartLocation, false, false);
                return new ClassDeclaration.MethodDefinition(key, method, kind, computed, isStatic, isPrivate);
            }
        }

        // Regular property name (identifier, string, number)
        Expression key;
        boolean computed = false;

        if (ctx.match(TokenType.IDENTIFIER)) {
            key = new Identifier(ctx.currentToken.value(), methodStartLocation);
            ctx.advance();
        } else if (ctx.match(TokenType.STRING)) {
            key = new Literal(ctx.currentToken.value(), methodStartLocation);
            ctx.advance();
        } else if (ctx.match(TokenType.NUMBER)) {
            key = new Literal(Double.parseDouble(ctx.currentToken.value()), methodStartLocation);
            ctx.advance();
        } else {
            throw new RuntimeException("Expected property name");
        }

        return parseMethodOrField(key, isStatic, isPrivate, computed, methodStartLocation);
    }

    /**
     * Parse a class expression (class used as an expression).
     * Syntax: class [Name] [extends Super] { body }
     */
    ClassExpression parseClassExpression() {
        SourceLocation startLocation = ctx.getLocation();
        int startOffset = ctx.currentToken.offset();
        ctx.expect(TokenType.CLASS);

        // Parse optional class name (class expressions can be anonymous)
        Identifier id = null;
        if (ctx.match(TokenType.IDENTIFIER)) {
            String name = ctx.currentToken.value();
            ctx.advance();
            id = new Identifier(name, startLocation);
        }

        // Parse optional extends clause
        Expression superClass = null;
        if (ctx.match(TokenType.EXTENDS)) {
            ctx.advance();
            superClass = delegates.expressions.parseMemberExpression();
        }

        // Parse class body
        ctx.expect(TokenType.LBRACE);
        List<ClassDeclaration.ClassElement> body = new ArrayList<>();
        boolean savedParsingClassWithSuper2 = ctx.parsingClassWithSuper;
        ctx.parsingClassWithSuper = superClass != null;

        while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            // Skip empty semicolons
            if (ctx.match(TokenType.SEMICOLON)) {
                ctx.advance();
                continue;
            }

            ClassDeclaration.ClassElement element = parseClassElement();
            if (element != null) {
                body.add(element);
            }
        }

        ctx.parsingClassWithSuper = savedParsingClassWithSuper2;

        // Capture the end position before advancing past the closing brace
        int endOffset = ctx.currentToken.offset() + ctx.currentToken.value().length();
        ctx.expect(TokenType.RBRACE);

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
        SourceLocation location = ctx.getLocation();
        ctx.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();

        // Save outer strict mode so we can restore it after parsing the function body
        boolean savedStrictMode = ctx.strictMode;

        // Parse directives (like "use strict") at the beginning of the function body
        boolean hasUseStrict = ctx.parseDirectives(body);

        // If "use strict" directive found, retroactively validate parameters
        // Following QuickJS js_parse_function_check_names
        if (hasUseStrict) {
            ctx.strictMode = true;
            ctx.lexer.setStrictMode(true);
            checkStrictModeParameters(funcParams, funcName);
        }

        // Parse remaining body statements
        while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
            Statement stmt = delegates.statements.parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        // Restore outer strict mode
        ctx.strictMode = savedStrictMode;
        ctx.lexer.setStrictMode(savedStrictMode);

        ctx.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator) {
        return parseFunctionDeclaration(isAsync, isGenerator, null);
    }

    FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator, SourceLocation startLocation) {
        // Use provided start location (for async functions) or get current location
        SourceLocation location = startLocation != null ? startLocation : ctx.getLocation();
        ctx.expect(TokenType.FUNCTION);

        // Check for generator function: function* or async function*
        // Following QuickJS implementation: check for '*' after 'function' keyword
        if (ctx.match(TokenType.MUL)) {
            ctx.advance();
            isGenerator = true;
        }

        ctx.enterFunctionContext(isAsync);
        try {
            Identifier id = ctx.parseIdentifier();

            // Per QuickJS: in_function_body == FALSE prevents yield/await during
            // the parsing of the arguments in generator/async functions.
            boolean savedInFunctionBody = ctx.inFunctionBody;
            if (isAsync || isGenerator) {
                ctx.inFunctionBody = false;
            }

            ctx.expect(TokenType.LPAREN);
            FunctionParams funcParams = parseFunctionParameters();

            ctx.inFunctionBody = savedInFunctionBody;

            // Parse body with "use strict" detection and parameter validation
            BlockStatement body = parseFunctionBody(funcParams, id);

            // Use previousTokenEndOffset (position after closing '}') instead of
            // currentToken.offset() (position of NEXT token, which may include trailing comments)
            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    ctx.previousTokenEndOffset
            );

            return new FunctionDeclaration(id, funcParams.params(), funcParams.defaults(), funcParams.restParameter(), body, isAsync, isGenerator, fullLocation);
        } finally {
            ctx.exitFunctionContext(isAsync);
        }
    }

    Expression parseFunctionExpression() {
        return parseFunctionExpression(false, null);
    }

    Expression parseFunctionExpression(boolean isAsync, SourceLocation startLocation) {
        SourceLocation location = startLocation != null ? startLocation : ctx.getLocation();
        ctx.expect(TokenType.FUNCTION);

        // Check for generator function expression: function* () {}
        // Following QuickJS implementation: check for '*' after 'function' keyword
        boolean isGenerator = false;
        if (ctx.match(TokenType.MUL)) {
            ctx.advance();
            isGenerator = true;
        }

        ctx.enterFunctionContext(isAsync);
        try {
            Identifier id = null;
            if (ctx.match(TokenType.IDENTIFIER) || ctx.match(TokenType.AWAIT)) {
                id = ctx.parseIdentifier();
            }

            // Per QuickJS: in_function_body == FALSE prevents yield/await during
            // the parsing of the arguments in generator/async functions.
            boolean savedInFunctionBody = ctx.inFunctionBody;
            if (isAsync || isGenerator) {
                ctx.inFunctionBody = false;
            }

            ctx.expect(TokenType.LPAREN);
            FunctionParams funcParams = parseFunctionParameters();

            ctx.inFunctionBody = savedInFunctionBody;

            // Parse body with "use strict" detection and parameter validation
            BlockStatement body = parseFunctionBody(funcParams, id);

            // Use previousTokenEndOffset (position after closing '}') instead of
            // currentToken.offset() (which may include trailing comments)
            SourceLocation fullLocation = new SourceLocation(
                    location.line(),
                    location.column(),
                    location.offset(),
                    ctx.previousTokenEndOffset
            );

            return new FunctionExpression(id, funcParams.params(), funcParams.defaults(), funcParams.restParameter(), body, isAsync, isGenerator, fullLocation);
        } finally {
            ctx.exitFunctionContext(isAsync);
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

        if (!ctx.match(TokenType.RPAREN)) {
            while (true) {
                // Check for rest parameter (...args)
                if (ctx.match(TokenType.ELLIPSIS)) {
                    SourceLocation location = ctx.getLocation();
                    ctx.advance(); // consume '...'
                    Identifier restArg = ctx.parseIdentifier();
                    restParameter = new RestParameter(restArg, location);

                    // Rest parameter must be last
                    if (ctx.match(TokenType.COMMA)) {
                        throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                    }
                    break;
                }

                // Regular parameter or destructuring pattern
                // Following QuickJS js_parse_function_decl2: accept identifiers,
                // object patterns ({a, b}), and array patterns ([a, b])
                if (ctx.match(TokenType.LBRACE) || ctx.match(TokenType.LBRACKET)) {
                    params.add(delegates.patterns.parsePattern());
                } else {
                    params.add(ctx.parseIdentifier());
                }

                // Check for default value: param = defaultExpr
                // Following QuickJS js_parse_function_decl2 pattern
                if (ctx.match(TokenType.ASSIGN)) {
                    ctx.advance(); // consume '='
                    defaults.add(delegates.expressions.parseAssignmentExpression());
                } else {
                    defaults.add(null);
                }

                // Check for comma (more parameters)
                if (!ctx.match(TokenType.COMMA)) {
                    break;
                }
                ctx.advance(); // consume comma

                // Handle trailing comma before closing paren
                if (ctx.match(TokenType.RPAREN)) {
                    break;
                }
                // Handle trailing comma before rest parameter
                if (ctx.match(TokenType.ELLIPSIS)) {
                    continue;
                }
            }
        }

        ctx.expect(TokenType.RPAREN);
        return new FunctionParams(params, defaults, restParameter);
    }

    /**
     * Parse a method (constructor, method, getter, or setter).
     * Uses the current token position as the method start for source extraction.
     */
    FunctionExpression parseMethod(String kind) {
        return parseMethod(kind, ctx.getLocation(), false, false);
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
        boolean savedInFunctionBody = ctx.inFunctionBody;
        if (isAsync || isGenerator) {
            ctx.inFunctionBody = false;
        }

        ctx.expect(TokenType.LPAREN);
        FunctionParams funcParams = parseFunctionParameters();

        ctx.inFunctionBody = savedInFunctionBody;

        // Parse method body
        ctx.enterFunctionContext(isAsync);
        BlockStatement body;
        try {
            body = delegates.statements.parseBlockStatement();
        } finally {
            ctx.exitFunctionContext(isAsync);
        }

        SourceLocation fullLocation = new SourceLocation(
                methodStartLocation.line(),
                methodStartLocation.column(),
                methodStartLocation.offset(),
                ctx.previousTokenEndOffset
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
        if (ctx.match(TokenType.ASSIGN) || ctx.match(TokenType.SEMICOLON)) {
            Expression value = null;
            if (ctx.match(TokenType.ASSIGN)) {
                ctx.advance();
                ctx.enterFunctionContext(false);
                try {
                    value = delegates.expressions.parseAssignmentExpression();
                } finally {
                    ctx.exitFunctionContext(false);
                }
            }
            ctx.consumeSemicolon();
            return new ClassDeclaration.PropertyDefinition(key, value, computed, isStatic, isPrivate);
        }

        // Otherwise, it's a method
        // If this is a constructor in a derived class, enable super() calls
        boolean isConstructorMethod = !isStatic && key instanceof Identifier keyId && "constructor".equals(keyId.name());
        boolean savedInDerivedConstructor = ctx.inDerivedConstructor;
        boolean savedSuperPropertyAllowed = ctx.superPropertyAllowed;
        if (isConstructorMethod && ctx.parsingClassWithSuper) {
            ctx.inDerivedConstructor = true;
        }
        ctx.superPropertyAllowed = true;
        FunctionExpression method = parseMethod("method", location, false, false);
        ctx.inDerivedConstructor = savedInDerivedConstructor;
        ctx.superPropertyAllowed = savedSuperPropertyAllowed;
        return new ClassDeclaration.MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
    }

    /**
     * Parse a static initialization block: static { statements }
     */
    ClassDeclaration.StaticBlock parseStaticBlock() {
        ctx.expect(TokenType.LBRACE);
        List<Statement> statements = new ArrayList<>();

        ctx.enterFunctionContext(false);
        try {
            while (!ctx.match(TokenType.RBRACE) && !ctx.match(TokenType.EOF)) {
                Statement stmt = delegates.statements.parseStatement();
                if (stmt != null) {
                    statements.add(stmt);
                }
            }
        } finally {
            ctx.exitFunctionContext(false);
        }

        ctx.expect(TokenType.RBRACE);
        return new ClassDeclaration.StaticBlock(statements);
    }
}
