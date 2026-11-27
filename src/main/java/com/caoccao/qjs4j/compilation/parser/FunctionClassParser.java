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

import java.util.*;

/**
 * Delegate parser for function declarations/expressions and class declarations/expressions. Extracted from the
 * monolithic Parser class as part of the parser refactoring.
 */
record FunctionClassParser(ParserContext parserContext, ParserDelegates delegates) {
    private static String getSimpleClassElementName(Expression key) {
        if (key instanceof Identifier identifier) {
            return identifier.getName();
        }
        if (key instanceof Literal literal && literal.getValue() instanceof String literalString) {
            return literalString;
        }
        return null;
    }

    private static boolean isPrivateConstructorName(Expression key, boolean isPrivate) {
        if (!isPrivate) {
            return false;
        }
        if (key instanceof PrivateIdentifier privateIdentifier) {
            return JSKeyword.CONSTRUCTOR.equals(privateIdentifier.getName());
        }
        return false;
    }

    /**
     * Check for duplicate parameter names. Following QuickJS js_parse_function_check_names: duplicates are rejected
     * when strict mode, non-simple parameter list, arrow function, or method.
     *
     * @param funcParams
     *            The function parameters to validate
     */
    private void checkDuplicateParameters(FunctionParams funcParams) {
        Set<String> seen = new HashSet<>();
        for (Pattern param : funcParams.params()) {
            List<String> boundNames = param.getBoundNames();
            for (String paramName : boundNames) {
                if (!seen.add(paramName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
            }
        }
        if (funcParams.restParameter() != null) {
            List<String> restBoundNames = funcParams.restParameter().getArgument().getBoundNames();
            for (String restName : restBoundNames) {
                if (!seen.add(restName)) {
                    throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
                }
            }
        }
    }

    /**
     * Validate function parameters for strict mode rules. Following QuickJS js_parse_function_check_names: - No
     * parameter named 'eval' or 'arguments' - No duplicate parameter names
     *
     * @param funcParams
     *            The function parameters to validate
     * @param funcName
     *            The function name (or null for anonymous)
     */
    private void checkStrictModeParameters(FunctionParams funcParams, Identifier funcName) {
        if (funcName != null) {
            checkStrictBindingName(funcName.getName());
        }
        for (Pattern param : funcParams.params()) {
            for (String name : param.getBoundNames()) {
                checkStrictBindingName(name);
            }
        }
        if (funcParams.restParameter() != null) {
            for (String name : funcParams.restParameter().getArgument().getBoundNames()) {
                checkStrictBindingName(name);
            }
        }
        checkDuplicateParameters(funcParams);
    }

    private void enterFunctionContext(boolean asyncFunction) {
        parserContext.functionNesting++;
        if (asyncFunction) {
            parserContext.asyncFunctionNesting++;
        }
    }

    private void enterFunctionContext(boolean asyncFunction, boolean generatorFunction) {
        parserContext.savedFunctionNestingStack
                .push(new int[]{parserContext.generatorFunctionNesting, parserContext.asyncFunctionNesting,
                        parserContext.newTargetNesting, parserContext.inClassFieldInitializer ? 1 : 0,
                        parserContext.inClassStaticInit ? 1 : 0, parserContext.needsArguments ? 1 : 0});
        parserContext.functionNesting++;
        parserContext.generatorFunctionNesting = generatorFunction ? 1 : 0;
        parserContext.asyncFunctionNesting = asyncFunction ? 1 : 0;
        parserContext.newTargetNesting = 1;
        parserContext.inClassFieldInitializer = false;
        parserContext.inClassStaticInit = false;
        parserContext.needsArguments = false;
    }

    private void exitFunctionContext(boolean asyncFunction) {
        if (asyncFunction) {
            parserContext.asyncFunctionNesting--;
        }
        parserContext.functionNesting--;
    }

    private void exitFunctionContext(boolean asyncFunction, boolean generatorFunction) {
        int[] saved = parserContext.savedFunctionNestingStack.pop();
        parserContext.generatorFunctionNesting = saved[0];
        parserContext.asyncFunctionNesting = saved[1];
        parserContext.newTargetNesting = saved[2];
        parserContext.inClassFieldInitializer = saved[3] != 0;
        parserContext.inClassStaticInit = saved[4] != 0;
        parserContext.needsArguments = saved[5] != 0;
        parserContext.functionNesting--;
    }

    /**
     * A class's name binds in strict mode code, so it cannot be {@code eval} or {@code arguments}.
     */
    private static void validateClassBindingName(Identifier id) {
        if (JSKeyword.EVAL.equals(id.getName()) || JSKeyword.ARGUMENTS.equals(id.getName())) {
            throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
        }
    }

    /**
     * Parse a class declaration or expression. Syntax: class Name extends Super { body }
     */
    ClassDeclaration parseClassDeclaration() {
        return parseClass(ClassDeclaration::new);
    }

    /**
     * Parse a single class element (method, field, or static block).
     */
    ClassElement parseClassElement() {
        boolean isStatic = false;
        SourceLocation location = parserContext.getLocation();
        SourceLocation methodStartLocation = location;

        if (parserContext.match(TokenType.AT)) {
            parseDecoratorList();
            location = parserContext.getLocation();
            methodStartLocation = location;
        }

        // Check for 'static' keyword
        if (parserContext.match(TokenType.IDENTIFIER) && JSKeyword.STATIC.equals(parserContext.currentToken.value())) {
            TokenType nextType = parserContext.nextToken.type();
            boolean treatAsStaticModifier = nextType != TokenType.SEMICOLON && nextType != TokenType.RBRACE
                    && nextType != TokenType.LPAREN && nextType != TokenType.ASSIGN;
            if (treatAsStaticModifier) {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
                }
                parserContext.advance();
                isStatic = true;
                methodStartLocation = parserContext.getLocation();

                // Check for static block: static { }
                if (parserContext.match(TokenType.LBRACE)) {
                    return parseStaticBlock();
                }
            } else {
                Expression key = new Identifier(JSKeyword.STATIC, location);
                parserContext.advance();
                return parseMethodOrField(key, false, false, false, location);
            }
        }

        // Check for private identifier (#name)
        if (parserContext.match(TokenType.PRIVATE_NAME)) {
            return parseMethodOrField(parseClassElementName(), isStatic, true, true, methodStartLocation);
        }

        if (parserContext.match(TokenType.IDENTIFIER) && "accessor".equals(parserContext.currentToken.value())) {
            TokenType nextType = parserContext.nextToken.type();
            boolean hasNoLineTerminatorAfterAccessor = parserContext.nextToken.line() == parserContext.currentToken
                    .line();
            if (hasNoLineTerminatorAfterAccessor && nextType != TokenType.LPAREN && nextType != TokenType.ASSIGN
                    && nextType != TokenType.SEMICOLON && nextType != TokenType.COMMA && nextType != TokenType.RBRACE) {
                parserContext.advance(); // consume "accessor"
                boolean computed = parserContext.match(TokenType.LBRACKET);
                boolean isPrivate = parserContext.match(TokenType.PRIVATE_NAME);
                Expression key = parseClassElementName();
                validateClassFieldName(key, computed, isPrivate);
                Expression value = parseClassFieldInitializer();
                parserContext.consumeSemicolon();
                return new PropertyDefinition(key, value, computed, isStatic, isPrivate, true);
            }
        }

        // Check for async method: async name() {} or async *name() {}
        if (parserContext.match(TokenType.ASYNC)) {
            // Disambiguate: async as method modifier vs async as property name. `async` is a modifier only when
            // the element name follows on the same line: `async() {}` is a method named async, and `async` before
            // a line break is a field named async.
            TokenType nextType = parserContext.nextToken.type();
            boolean hasLineTerminatorAfterAsync = parserContext.nextToken.line() != parserContext.currentToken.line();
            if (!hasLineTerminatorAfterAsync && (nextType == TokenType.COLON || nextType == TokenType.COMMA)) {
                // An object-literal member in a class body: V8 reports it at `async`.
                throw new JSSyntaxErrorException("Unexpected token 'async'");
            }
            if (hasLineTerminatorAfterAsync || nextType == TokenType.ASSIGN || nextType == TokenType.SEMICOLON
                    || nextType == TokenType.LPAREN || nextType == TokenType.RBRACE) {
                // async = value; async; async() {}; or async at the end of a line — element named "async"
                Expression key = new Identifier(JSKeyword.ASYNC, methodStartLocation);
                parserContext.advance();
                return parseMethodOrField(key, isStatic, false, false, methodStartLocation);
            }
            if (parserContext.currentToken.escaped()
                    && parserContext.nextToken.line() == parserContext.currentToken.line()) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }

            parserContext.advance(); // consume 'async'

            boolean generator = parserContext.consumeIf(TokenType.MUL);
            return parseNamedClassMethod("method", methodStartLocation, true, generator, isStatic);
        }

        // Check for generator method: *name() {}
        if (parserContext.consumeIf(TokenType.MUL)) {
            return parseNamedClassMethod("method", methodStartLocation, false, true, isStatic);
        }

        // Check for getter/setter
        if (parserContext.match(TokenType.IDENTIFIER)) {
            String name = parserContext.currentToken.value();
            Token peekNext = parserContext.nextToken;
            boolean hasNoLineTerminatorAfterGetSet = peekNext.line() == parserContext.currentToken.line();
            if ((JSKeyword.GET.equals(name) || JSKeyword.SET.equals(name)) && hasNoLineTerminatorAfterGetSet
                    && peekNext.type() != TokenType.LPAREN && peekNext.type() != TokenType.ASSIGN
                    && peekNext.type() != TokenType.SEMICOLON) {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Keyword must not contain escaped characters");
                }
                String kind = name;
                parserContext.advance(); // consume 'get' or 'set'

                return parseNamedClassMethod(kind, methodStartLocation, false, false, isStatic);
            }
        }

        // Regular property name (identifier, string, number, or reserved keyword)
        boolean computed = parserContext.match(TokenType.LBRACKET);
        Expression key = delegates.expressions.parsePropertyName();

        return parseMethodOrField(key, isStatic, false, computed, methodStartLocation);
    }

    /**
     * Parse a class expression (class used as an expression). Syntax: class [Name] [extends Super] { body }
     */
    ClassExpression parseClassExpression() {
        return parseClass(ClassExpression::new);
    }

    private Expression parseClassFieldInitializer() {
        Expression value = null;
        if (parserContext.consumeIf(TokenType.ASSIGN)) {
            boolean savedInClassFieldInitializer = parserContext.inClassFieldInitializer;
            boolean savedSuperPropertyAllowed = parserContext.superPropertyAllowed;
            parserContext.inClassFieldInitializer = true;
            parserContext.superPropertyAllowed = true;
            // A field initializer is parsed [~Yield, ~Await] whatever function contains the class: `yield` in it is a
            // strict mode reserved word, not a yield expression of an enclosing generator.
            int savedGeneratorFunctionNesting = parserContext.generatorFunctionNesting;
            int savedAsyncFunctionNesting = parserContext.asyncFunctionNesting;
            parserContext.generatorFunctionNesting = 0;
            parserContext.asyncFunctionNesting = 0;
            enterFunctionContext(false);
            try {
                value = delegates.expressions.parseAssignmentExpression();
            } finally {
                exitFunctionContext(false);
                parserContext.asyncFunctionNesting = savedAsyncFunctionNesting;
                parserContext.generatorFunctionNesting = savedGeneratorFunctionNesting;
                parserContext.superPropertyAllowed = savedSuperPropertyAllowed;
                parserContext.inClassFieldInitializer = savedInClassFieldInitializer;
            }
        }
        return value;
    }

    private FunctionExpression parseClassMethod(String kind, SourceLocation methodStartLocation, boolean isAsync,
            boolean isGenerator, boolean isStatic, boolean isComputed, Expression key) {
        // Per ES2024 spec, only non-computed "constructor" is treated as the class constructor.
        // Computed ["constructor"]() is a regular method, not the constructor.
        String keyName = isComputed ? null : getSimpleClassElementName(key);
        boolean isConstructorMethod = !isStatic && "method".equals(kind) && JSKeyword.CONSTRUCTOR.equals(keyName);
        boolean savedInDerivedConstructor = parserContext.inDerivedConstructor;
        boolean savedSuperPropertyAllowed = parserContext.superPropertyAllowed;
        if (isConstructorMethod && parserContext.parsingClassWithSuper) {
            parserContext.inDerivedConstructor = true;
        }
        parserContext.superPropertyAllowed = true;
        try {
            return parseMethod(kind, methodStartLocation, isAsync, isGenerator);
        } finally {
            parserContext.inDerivedConstructor = savedInDerivedConstructor;
            parserContext.superPropertyAllowed = savedSuperPropertyAllowed;
        }
    }

    private void parseDecoratorList() {
        while (parserContext.consumeIf(TokenType.AT)) {
            if (parserContext.consumeIf(TokenType.LPAREN)) {
                boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                parserContext.inOperatorAllowed = true;
                try {
                    delegates.expressions.parseExpression();
                } finally {
                    parserContext.inOperatorAllowed = savedInOperatorAllowed;
                }
                parserContext.expect(TokenType.RPAREN);
                continue;
            }
            parserContext.parseIdentifier();
            while (parserContext.consumeIf(TokenType.DOT)) {
                if (parserContext.consumeIf(TokenType.PRIVATE_NAME)) {
                } else {
                    delegates.expressions.parsePropertyName();
                }
            }
            if (parserContext.consumeIf(TokenType.LPAREN)) {
                if (!parserContext.match(TokenType.RPAREN)) {
                    delegates.expressions.parseAssignmentExpression();
                    while (parserContext.consumeIf(TokenType.COMMA)) {
                        delegates.expressions.parseAssignmentExpression();
                    }
                }
                parserContext.expect(TokenType.RPAREN);
            }
        }
    }

    /**
     * Parse export default async function declaration with optional name.
     */
    Statement parseExportDefaultAsyncFunctionDeclaration() {
        SourceLocation asyncLocation = parserContext.consumeLocation(); // consume 'async'
        return parseExportDefaultFunctionDeclaration(true);
    }

    /**
     * Parse export default function declaration with optional name. Per ES spec, export default HoistableDeclaration
     * allows anonymous functions.
     */
    Statement parseExportDefaultFunctionDeclaration(boolean isAsync) {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.FUNCTION);

        boolean isGenerator = parserContext.consumeIf(TokenType.MUL);

        Identifier id;
        if (hasFunctionNameToken()) {
            id = parserContext.parseIdentifier();
            // Named function in export default creates a lexical binding for the name
            delegates.declarations.addModuleLexicalName(id.getName());
        } else {
            id = new Identifier(JSKeyword.DEFAULT, location);
        }
        return parseFunctionDefinition(id, location, isAsync, isGenerator, FunctionDeclaration::new);
    }

    /**
     * Parse a function body with "use strict" directive detection and parameter validation. Following QuickJS: after
     * parsing '{', check for directives, then validate parameters if strict mode was detected
     * (js_parse_function_check_names).
     *
     * @param funcParams
     *            The already-parsed function parameters
     * @param funcName
     *            The function name (or null for anonymous)
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
        } else if (funcParams.hasNonSimpleParameters()) {
            // Per ES2024 15.2.1: It is a Syntax Error if IsSimpleParameterList is false
            // and BoundNames contains any duplicate elements.
            // Following QuickJS js_parse_function_check_names: duplicates are always
            // rejected for non-simple parameter lists regardless of strict mode.
            checkDuplicateParameters(funcParams);
        }

        // Non-simple parameter list with "use strict" directive is always an error (spec 15.2.1)
        if (hasUseStrict && funcParams.hasNonSimpleParameters()) {
            throw new JSSyntaxErrorException(
                    "Illegal 'use strict' directive in function with non-simple parameter list");
        }

        // Parse remaining body statements, with no enclosing labels in scope
        Deque<Set<String>> enclosingLabels = parserContext.enterFunctionLabelScope();
        try {
            while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                Statement stmt = delegates.statements.parseStatement();
                if (stmt != null) {
                    body.add(stmt);
                }
            }
        } finally {
            parserContext.exitFunctionLabelScope(enclosingLabels);
        }

        delegates.declarations.validateFunctionBodyEarlyErrors(body);
        // Check that parameter names don't conflict with lexical declarations in body
        // Per spec: "It is a Syntax Error if BoundNames of FormalParameters also occurs
        // in the LexicallyDeclaredNames of FunctionBody"
        validateFormalsBodyDuplicate(funcParams, body);

        // Restore outer strict mode
        parserContext.strictMode = savedStrictMode;
        parserContext.lexer.setStrictMode(savedStrictMode);

        parserContext.expect(TokenType.RBRACE);
        BlockStatement block = new BlockStatement(body, location);
        block.setHasUseStrictDirective(hasUseStrict);
        return block;
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
        if (parserContext.consumeIf(TokenType.MUL)) {
            isGenerator = true;
        }

        // Parse the function name in the ENCLOSING scope context, before entering
        // the function context. Per ES spec, the BindingIdentifier of a FunctionDeclaration
        // is bound in the enclosing scope, so contextual keyword checks (await/yield)
        // must use the enclosing scope's rules. This allows `async function await() {}`
        // at global scope while rejecting `function await() {}` inside async functions.
        Identifier id = parserContext.parseIdentifier();

        return parseFunctionDefinition(id, location, isAsync, isGenerator, FunctionDeclaration::new);
    }

    Expression parseFunctionExpression() {
        return parseFunctionExpression(false, null);
    }

    Expression parseFunctionExpression(boolean isAsync, SourceLocation startLocation) {
        SourceLocation location = startLocation != null ? startLocation : parserContext.getLocation();
        parserContext.expect(TokenType.FUNCTION);

        // Check for generator function expression: function* () {}
        // Following QuickJS implementation: check for '*' after 'function' keyword
        boolean isGenerator = parserContext.consumeIf(TokenType.MUL);

        return parseFunctionDefinition(null, location, isAsync, isGenerator, FunctionExpression::new);
    }

    /**
     * Parse function parameters, including optional rest parameter. Expects '(' to already be consumed. Consumes up to
     * and including ')'.
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
                    SourceLocation location = parserContext.consumeLocation(); // consume '...'
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
                if (parserContext.consumeIf(TokenType.ASSIGN)) {
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
     * Parse a method (constructor, method, getter, or setter). Uses the current token position as the method start for
     * source extraction.
     */
    FunctionExpression parseMethod(String kind) {
        return parseMethod(kind, parserContext.getLocation(), false, false);
    }

    /**
     * Parse a method with explicit start location and async/generator flags. The methodStartLocation determines where
     * the source code begins (e.g., at 'get', 'set', 'async', '*', or the method name).
     */
    FunctionExpression parseMethod(String kind, SourceLocation methodStartLocation, boolean isAsync,
            boolean isGenerator) {
        enterFunctionContext(isAsync, isGenerator);
        boolean savedInFunctionBody = parserContext.inFunctionBody;
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = false;

        BlockStatement body;
        FunctionParams funcParams;
        boolean needsArguments;
        try {
            // Per QuickJS: in_function_body == FALSE prevents yield/await during
            // parsing of parameters in async/generator functions.
            if (isAsync || isGenerator) {
                parserContext.inFunctionBody = false;
            }

            parserContext.expect(TokenType.LPAREN);
            funcParams = parseFunctionParameters();
            int formalParameterCount = funcParams.params().size() + (funcParams.restParameter() != null ? 1 : 0);
            if (JSKeyword.GET.equals(kind)) {
                if (formalParameterCount != 0) {
                    throw new JSSyntaxErrorException("invalid number of arguments for getter or setter");
                }
            } else if (JSKeyword.SET.equals(kind)) {
                if (funcParams.restParameter() != null || formalParameterCount != 1) {
                    throw new JSSyntaxErrorException("invalid number of arguments for getter or setter");
                }
            }

            parserContext.inFunctionBody = true;

            // Per QuickJS js_parse_function_check_names: methods always reject duplicate parameters
            checkDuplicateParameters(funcParams);
            body = parseFunctionBody(funcParams, null);
            needsArguments = parserContext.needsArguments;
        } finally {
            parserContext.inFunctionBody = savedInFunctionBody;
            parserContext.inClassStaticInit = savedInClassStaticInit;
            exitFunctionContext(isAsync, isGenerator);
        }

        // Per ES2024 15.2.1: "use strict" in method body with non-simple parameters
        // is a SyntaxError. Methods are already strict (class bodies are strict mode),
        // but we must still check for the explicit directive + non-simple params combo.
        if (funcParams.hasNonSimpleParameters() && body.hasUseStrictDirective()) {
            throw new JSSyntaxErrorException(
                    "Illegal 'use strict' directive in function with non-simple parameter list");
        }

        SourceLocation fullLocation = methodStartLocation.withEndOffset(parserContext.previousTokenEndOffset);

        return new FunctionExpression(null, funcParams, body, isAsync, isGenerator, needsArguments, fullLocation);
    }

    /**
     * Parse method or field after the property name.
     */
    ClassElement parseMethodOrField(Expression key, boolean isStatic, boolean isPrivate, boolean computed,
            SourceLocation location) {
        // If next token is LPAREN, it's a method; otherwise it's a field
        if (!parserContext.match(TokenType.LPAREN)) {
            validateClassFieldName(key, computed, isPrivate);
            Expression value = parseClassFieldInitializer();
            parserContext.consumeSemicolon();
            return new PropertyDefinition(key, value, computed, isStatic, isPrivate);
        }

        // It's a method
        FunctionExpression method = parseClassMethod("method", location, false, false, isStatic, computed, key);
        return new MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
    }

    /**
     * Parse a static initialization block: static { statements }
     */
    StaticBlock parseStaticBlock() {
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        boolean savedSuperPropertyAllowed = parserContext.superPropertyAllowed;
        int savedAsyncFunctionNesting = parserContext.asyncFunctionNesting;
        int savedGeneratorFunctionNesting = parserContext.generatorFunctionNesting;
        parserContext.inClassStaticInit = true;
        parserContext.superPropertyAllowed = true;
        parserContext.asyncFunctionNesting = 0;
        parserContext.generatorFunctionNesting = 0;
        Deque<Set<String>> enclosingLabels = parserContext.enterFunctionLabelScope();
        try {
            BlockStatement blockStatement = delegates.statements.parseBlockStatement(true);
            return new StaticBlock(blockStatement.getBody());
        } finally {
            parserContext.exitFunctionLabelScope(enclosingLabels);
            parserContext.generatorFunctionNesting = savedGeneratorFunctionNesting;
            parserContext.asyncFunctionNesting = savedAsyncFunctionNesting;
            parserContext.superPropertyAllowed = savedSuperPropertyAllowed;
            parserContext.inClassStaticInit = savedInClassStaticInit;
        }
    }

    private void validateClassElements(List<ClassElement> classElements) {
        int constructorMethodCount = 0;
        Map<String, Boolean> privateGetterStaticByName = new HashMap<>();
        Map<String, Boolean> privateSetterStaticByName = new HashMap<>();
        for (ClassElement classElement : classElements) {
            if (classElement instanceof PropertyDefinition field) {
                validateClassFieldName(field.getKey(), field.isComputed(), field.isPrivate());
                continue;
            }
            if (classElement instanceof MethodDefinition method) {
                if (method.isPrivate() && method.getKey() instanceof PrivateIdentifier privateIdentifier
                        && (JSKeyword.GET.equals(method.getKind()) || JSKeyword.SET.equals(method.getKind()))) {
                    String privateName = privateIdentifier.getName();
                    boolean isStatic = method.isStatic();
                    if (JSKeyword.GET.equals(method.getKind())) {
                        Boolean setterIsStatic = privateSetterStaticByName.get(privateName);
                        if (setterIsStatic != null && setterIsStatic != isStatic) {
                            throw new JSSyntaxErrorException("Invalid private accessor pairing");
                        }
                        privateGetterStaticByName.putIfAbsent(privateName, isStatic);
                    } else {
                        Boolean getterIsStatic = privateGetterStaticByName.get(privateName);
                        if (getterIsStatic != null && getterIsStatic != isStatic) {
                            throw new JSSyntaxErrorException("Invalid private accessor pairing");
                        }
                        privateSetterStaticByName.putIfAbsent(privateName, isStatic);
                    }
                }
                if (isPrivateConstructorName(method.getKey(), method.isPrivate())) {
                    throw new JSSyntaxErrorException("invalid method name");
                }
                if (method.isComputed() || method.isPrivate()) {
                    continue;
                }
                String methodName = getSimpleClassElementName(method.getKey());
                if (methodName == null) {
                    continue;
                }
                if (method.isStatic() && JSKeyword.PROTOTYPE.equals(methodName)) {
                    throw new JSSyntaxErrorException("invalid method name");
                }
                if (!method.isStatic() && JSKeyword.CONSTRUCTOR.equals(methodName)) {
                    boolean isSpecialMethod = !"method".equals(method.getKind()) || method.getValue().isAsync()
                            || method.getValue().isGenerator();
                    if (isSpecialMethod) {
                        throw new JSSyntaxErrorException("invalid method name");
                    }
                    constructorMethodCount++;
                }
            }
        }
        if (constructorMethodCount > 1) {
            throw new JSSyntaxErrorException("property constructor appears more than once");
        }
    }

    private void validateClassFieldName(Expression key, boolean computed, boolean isPrivate) {
        if (isPrivateConstructorName(key, isPrivate)) {
            throw new JSSyntaxErrorException("invalid method name");
        }
        if (computed || isPrivate) {
            return;
        }
        String fieldName = getSimpleClassElementName(key);
        if (JSKeyword.CONSTRUCTOR.equals(fieldName) || JSKeyword.PROTOTYPE.equals(fieldName)) {
            throw new JSSyntaxErrorException("invalid field name");
        }
    }

    private void validateFormalsBodyDuplicate(FunctionParams funcParams, List<Statement> body) {
        ExpressionAssignmentParser.validateFormalsBodyDuplicate(funcParams.params(), funcParams.restParameter(), body);
    }

    private <T extends ASTNode> T parseClass(ClassFactory<T> factory) {
        SourceLocation startLocation = parserContext.getLocation();
        int startOffset = parserContext.currentToken.offset();
        parserContext.expect(TokenType.CLASS);

        boolean savedStrictMode = parserContext.strictMode;
        parserContext.strictMode = true;
        parserContext.lexer.setStrictMode(true);
        try {
            // Parse optional class name
            // Per ES spec 14.6: class definitions (including heritage and body) are strict mode code.
            Identifier id = null;
            if (parserContext.match(TokenType.IDENTIFIER) || parserContext.match(TokenType.AWAIT)
                    || parserContext.match(TokenType.YIELD) || parserContext.match(TokenType.LET)) {
                id = parserContext.parseIdentifier();
                validateClassBindingName(id);
            }

            // Parse optional extends clause
            // ES2024 ClassHeritage: extends LeftHandSideExpression[?Yield, ?Await]
            Expression superClass = null;
            if (parserContext.consumeIf(TokenType.EXTENDS)) {
                superClass = delegates.expressions.parseCallExpression();
            }

            // Parse class body
            parserContext.expect(TokenType.LBRACE);
            List<ClassElement> body = new ArrayList<>();
            boolean savedParsingClassWithSuper = parserContext.parsingClassWithSuper;
            parserContext.parsingClassWithSuper = superClass != null;
            parserContext.classBodyNesting++;
            try {
                while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                    // Skip empty semicolons
                    if (parserContext.consumeIf(TokenType.SEMICOLON)) {
                        continue;
                    }

                    ClassElement element = parseClassElement();
                    if (element != null) {
                        body.add(element);
                    }
                }
                validateClassElements(body);
            } finally {
                parserContext.classBodyNesting--;
                parserContext.parsingClassWithSuper = savedParsingClassWithSuper;
            }

            // Capture the end position before advancing past the closing brace
            int endOffset = parserContext.currentToken.offset() + parserContext.currentToken.value().length();
            parserContext.expect(TokenType.RBRACE);

            SourceLocation location = new SourceLocation(startLocation.line(), startLocation.column(), startOffset,
                    endOffset);

            return factory.create(id, superClass, body, location);
        } finally {
            parserContext.strictMode = savedStrictMode;
            parserContext.lexer.setStrictMode(savedStrictMode);
        }
    }

    @FunctionalInterface
    private interface ClassFactory<T extends ASTNode> {
        T create(Identifier id, Expression superClass, List<ClassElement> body, SourceLocation location);
    }

    private void checkStrictBindingName(String name) {
        if (JSKeyword.EVAL.equals(name) || JSKeyword.ARGUMENTS.equals(name)) {
            throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
        }
        if (parserContext.isStrictReservedIdentifier(name)) {
            throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
        }
    }

    private <T extends ASTNode> T parseFunctionDefinition(Identifier id, SourceLocation location, boolean isAsync,
            boolean isGenerator, FunctionFactory<T> factory) {
        enterFunctionContext(isAsync, isGenerator);
        boolean savedInClassStaticInit = parserContext.inClassStaticInit;
        parserContext.inClassStaticInit = false;
        boolean savedInFunctionBody = parserContext.inFunctionBody;
        try {
            // A declaration's name was read in its enclosing scope. An expression binds its name here.
            if (id == null && hasFunctionNameToken()) {
                id = parserContext.parseIdentifier();
            }
            // Per QuickJS: in_function_body == FALSE prevents yield/await during
            // the parsing of the arguments in generator/async functions.
            if (isAsync || isGenerator) {
                parserContext.inFunctionBody = false;
            }

            parserContext.expect(TokenType.LPAREN);
            FunctionParams funcParams = parseFunctionParameters();

            parserContext.inFunctionBody = true;

            // Parse body with "use strict" detection and parameter validation
            BlockStatement body = parseFunctionBody(funcParams, id);
            boolean needsArguments = parserContext.needsArguments;

            // Use previousTokenEndOffset (position after closing '}') instead of
            // currentToken.offset() (position of NEXT token, which may include trailing comments)
            SourceLocation fullLocation = location.withEndOffset(parserContext.previousTokenEndOffset);

            return factory.create(id, funcParams, body, isAsync, isGenerator, needsArguments, fullLocation);
        } finally {
            parserContext.inFunctionBody = savedInFunctionBody;
            parserContext.inClassStaticInit = savedInClassStaticInit;
            exitFunctionContext(isAsync, isGenerator);
        }
    }

    @FunctionalInterface
    private interface FunctionFactory<T extends ASTNode> {
        T create(Identifier id, FunctionParams params, BlockStatement body, boolean isAsync, boolean isGenerator,
                boolean needsArguments, SourceLocation location);
    }

    private boolean hasFunctionNameToken() {
        return parserContext.match(TokenType.IDENTIFIER) || parserContext.match(TokenType.AWAIT)
                || parserContext.match(TokenType.YIELD) || parserContext.match(TokenType.LET);
    }

    private Expression parseClassElementName() {
        if (parserContext.match(TokenType.PRIVATE_NAME)) {
            SourceLocation location = parserContext.getLocation();
            String name = parserContext.currentToken.value().substring(1);
            parserContext.advance();
            return new PrivateIdentifier(name, location);
        }
        return delegates.expressions.parsePropertyName();
    }

    private MethodDefinition parseNamedClassMethod(String kind, SourceLocation location, boolean async,
            boolean generator, boolean isStatic) {
        boolean computed = parserContext.match(TokenType.LBRACKET);
        boolean isPrivate = parserContext.match(TokenType.PRIVATE_NAME);
        Expression key = parseClassElementName();
        FunctionExpression method = parseClassMethod(kind, location, async, generator, isStatic, computed, key);
        return new MethodDefinition(key, method, kind, computed, isStatic, isPrivate);
    }
}
