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
final class FunctionClassParser extends GrammarProduction {
    FunctionClassParser(ParserContext parserContext) {
        super(parserContext);
    }

    private boolean hasFunctionNameToken() {
        return tokens.match(TokenType.IDENTIFIER) || tokens.match(TokenType.AWAIT) || tokens.match(TokenType.YIELD)
                || tokens.match(TokenType.LET);
    }

    private <T extends ASTNode> T parseClass(ClassFactory<T> factory) {
        SourceLocation startLocation = tokens.getLocation();
        int startOffset = tokens.current().offset();
        tokens.expect(TokenType.CLASS);

        boolean savedStrictMode = parserContext.strictMode;
        parserContext.setStrictMode(true);
        try {
            // Parse optional class name
            // Per ES spec 14.6: class definitions (including heritage and body) are strict mode code.
            Identifier id = null;
            if (tokens.match(TokenType.IDENTIFIER) || tokens.match(TokenType.AWAIT) || tokens.match(TokenType.YIELD)
                    || tokens.match(TokenType.LET)) {
                id = parserContext.parseIdentifier();
                validateClassBindingName(id);
            }

            // Parse optional extends clause
            // ES2024 ClassHeritage: extends LeftHandSideExpression[?Yield, ?Await]
            Expression superClass = null;
            if (tokens.consumeIf(TokenType.EXTENDS)) {
                superClass = parserContext.primary.parseCallExpression();
            }

            // Parse class body
            tokens.expect(TokenType.LBRACE);
            List<ClassElement> body = new ArrayList<>();
            boolean savedParsingClassWithSuper = parserContext.parsingClassWithSuper;
            parserContext.parsingClassWithSuper = superClass != null;
            parserContext.classBodyNesting++;
            try {
                while (!tokens.match(TokenType.RBRACE) && !tokens.match(TokenType.EOF)) {
                    // Skip empty semicolons
                    if (tokens.consumeIf(TokenType.SEMICOLON)) {
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
            int endOffset = tokens.current().offset() + tokens.current().value().length();
            tokens.expect(TokenType.RBRACE);

            SourceLocation location = new SourceLocation(startLocation.line(), startLocation.column(), startOffset,
                    endOffset);

            return factory.create(id, superClass, body, location);
        } finally {
            parserContext.setStrictMode(savedStrictMode);
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
        SourceLocation location = tokens.getLocation();
        SourceLocation methodStartLocation = location;

        if (tokens.match(TokenType.AT)) {
            parseDecoratorList();
            location = tokens.getLocation();
            methodStartLocation = location;
        }

        // Check for 'static' keyword
        if (tokens.identifier(JSKeyword.STATIC)) {
            TokenType nextType = tokens.next().type();
            boolean treatAsStaticModifier = nextType != TokenType.SEMICOLON && nextType != TokenType.RBRACE
                    && nextType != TokenType.LPAREN && nextType != TokenType.ASSIGN;
            if (treatAsStaticModifier) {
                tokens.requireUnescapedToken("Keyword must not contain escaped characters");
                tokens.advance();
                isStatic = true;
                methodStartLocation = tokens.getLocation();

                // Check for static block: static { }
                if (tokens.match(TokenType.LBRACE)) {
                    return parseStaticBlock();
                }
            } else {
                Expression key = new Identifier(JSKeyword.STATIC, location);
                tokens.advance();
                return parseMethodOrField(key, false, false, false, location);
            }
        }

        // Check for private identifier (#name)
        if (tokens.match(TokenType.PRIVATE_NAME)) {
            return parseMethodOrField(parseClassElementName(), isStatic, true, true, methodStartLocation);
        }

        if (tokens.identifier("accessor")) {
            TokenType nextType = tokens.next().type();
            boolean hasNoLineTerminatorAfterAccessor = tokens.next().line() == tokens.current().line();
            if (hasNoLineTerminatorAfterAccessor && nextType != TokenType.LPAREN && nextType != TokenType.ASSIGN
                    && nextType != TokenType.SEMICOLON && nextType != TokenType.COMMA && nextType != TokenType.RBRACE) {
                tokens.advance(); // consume "accessor"
                boolean computed = tokens.match(TokenType.LBRACKET);
                boolean isPrivate = tokens.match(TokenType.PRIVATE_NAME);
                Expression key = parseClassElementName();
                validateClassFieldName(key, computed, isPrivate);
                Expression value = parseClassFieldInitializer();
                parserContext.consumeSemicolon();
                return new PropertyDefinition(key, value, computed, isStatic, isPrivate, true);
            }
        }

        // Check for async method: async name() {} or async *name() {}
        if (tokens.match(TokenType.ASYNC)) {
            // Disambiguate: async as method modifier vs async as property name. `async` is a modifier only when
            // the element name follows on the same line: `async() {}` is a method named async, and `async` before
            // a line break is a field named async.
            TokenType nextType = tokens.next().type();
            boolean hasLineTerminatorAfterAsync = !tokens.nextOnSameLine();
            if (!hasLineTerminatorAfterAsync && (nextType == TokenType.COLON || nextType == TokenType.COMMA)) {
                // An object-literal member in a class body: V8 reports it at `async`.
                throw new JSSyntaxErrorException("Unexpected token 'async'");
            }
            if (hasLineTerminatorAfterAsync || nextType == TokenType.ASSIGN || nextType == TokenType.SEMICOLON
                    || nextType == TokenType.LPAREN || nextType == TokenType.RBRACE) {
                // async = value; async; async() {}; or async at the end of a line — element named "async"
                Expression key = new Identifier(JSKeyword.ASYNC, methodStartLocation);
                tokens.advance();
                return parseMethodOrField(key, isStatic, false, false, methodStartLocation);
            }
            if (tokens.current().escaped() && tokens.nextOnSameLine()) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }

            tokens.advance(); // consume 'async'

            boolean generator = tokens.consumeIf(TokenType.MUL);
            return parseNamedClassMethod("method", methodStartLocation, true, generator, isStatic);
        }

        // Check for generator method: *name() {}
        if (tokens.consumeIf(TokenType.MUL)) {
            return parseNamedClassMethod("method", methodStartLocation, false, true, isStatic);
        }

        // Check for getter/setter
        if (tokens.match(TokenType.IDENTIFIER)) {
            String name = tokens.current().value();
            Token peekNext = tokens.next();
            boolean hasNoLineTerminatorAfterGetSet = peekNext.line() == tokens.current().line();
            if ((JSKeyword.GET.equals(name) || JSKeyword.SET.equals(name)) && hasNoLineTerminatorAfterGetSet
                    && peekNext.type() != TokenType.LPAREN && peekNext.type() != TokenType.ASSIGN
                    && peekNext.type() != TokenType.SEMICOLON) {
                tokens.requireUnescapedToken("Keyword must not contain escaped characters");
                String kind = name;
                tokens.advance(); // consume 'get' or 'set'

                return parseNamedClassMethod(kind, methodStartLocation, false, false, isStatic);
            }
        }

        // Regular property name (identifier, string, number, or reserved keyword)
        boolean computed = tokens.match(TokenType.LBRACKET);
        Expression key = parserContext.primary.parsePropertyName();

        return parseMethodOrField(key, isStatic, false, computed, methodStartLocation);
    }

    private Expression parseClassElementName() {
        if (tokens.match(TokenType.PRIVATE_NAME)) {
            SourceLocation location = tokens.getLocation();
            String name = tokens.current().value().substring(1);
            tokens.advance();
            return new PrivateIdentifier(name, location);
        }
        return parserContext.primary.parsePropertyName();
    }

    /**
     * Parse a class expression (class used as an expression). Syntax: class [Name] [extends Super] { body }
     */
    ClassExpression parseClassExpression() {
        return parseClass(ClassExpression::new);
    }

    private Expression parseClassFieldInitializer() {
        Expression value = null;
        if (tokens.consumeIf(TokenType.ASSIGN)) {
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
            parserContext.enterLexicalFunction(false);
            try {
                value = parserContext.expressions.parseAssignmentExpression();
            } finally {
                parserContext.exitLexicalFunction(false);
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

    void parseDecoratorList() {
        while (tokens.consumeIf(TokenType.AT)) {
            if (tokens.consumeIf(TokenType.LPAREN)) {
                boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                parserContext.inOperatorAllowed = true;
                try {
                    parserContext.expressions.parseExpression();
                } finally {
                    parserContext.inOperatorAllowed = savedInOperatorAllowed;
                }
                tokens.expect(TokenType.RPAREN);
                continue;
            }
            parserContext.parseIdentifier();
            while (tokens.consumeIf(TokenType.DOT)) {
                if (!tokens.consumeIf(TokenType.PRIVATE_NAME)) {
                    parserContext.primary.parsePropertyName();
                }
            }
            if (tokens.consumeIf(TokenType.LPAREN)) {
                if (!tokens.match(TokenType.RPAREN)) {
                    parserContext.expressions.parseAssignmentExpression();
                    while (tokens.consumeIf(TokenType.COMMA)) {
                        parserContext.expressions.parseAssignmentExpression();
                    }
                }
                tokens.expect(TokenType.RPAREN);
            }
        }
    }

    /**
     * Parse export default async function declaration with optional name.
     */
    Statement parseExportDefaultAsyncFunctionDeclaration() {
        SourceLocation asyncLocation = tokens.consumeLocation(); // consume 'async'
        return parseExportDefaultFunctionDeclaration(true);
    }

    /**
     * Parse export default function declaration with optional name. Per ES spec, export default HoistableDeclaration
     * allows anonymous functions.
     */
    Statement parseExportDefaultFunctionDeclaration(boolean isAsync) {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.FUNCTION);

        boolean isGenerator = tokens.consumeIf(TokenType.MUL);

        Identifier id;
        if (hasFunctionNameToken()) {
            id = parserContext.parseIdentifier();
            // Named function in export default creates a lexical binding for the name
            parserContext.modules().addModuleLexicalName(id.getName());
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
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();

        // Save outer strict mode so we can restore it after parsing the function body
        boolean savedStrictMode = parserContext.strictMode;

        // Parse directives (like "use strict") at the beginning of the function body
        boolean hasUseStrict = parserContext.parseDirectives(body);

        // If "use strict" directive found, retroactively validate parameters
        // Following QuickJS js_parse_function_check_names
        if (hasUseStrict) {
            parserContext.setStrictMode(true);
        }

        // Validate parameters when in strict mode (either from outer context or "use strict" directive)
        if (hasUseStrict || savedStrictMode) {
            ParameterEarlyErrors.validateStrict(parserContext, funcParams, funcName);
        } else if (funcParams.hasNonSimpleParameters()) {
            // Per ES2024 15.2.1: It is a Syntax Error if IsSimpleParameterList is false
            // and BoundNames contains any duplicate elements.
            // Following QuickJS js_parse_function_check_names: duplicates are always
            // rejected for non-simple parameter lists regardless of strict mode.
            ParameterEarlyErrors.rejectDuplicates(funcParams);
        }

        // Non-simple parameter list with "use strict" directive is always an error (spec 15.2.1)
        if (hasUseStrict && funcParams.hasNonSimpleParameters()) {
            throw new JSSyntaxErrorException(
                    "Illegal 'use strict' directive in function with non-simple parameter list");
        }

        // Parse remaining body statements, with no enclosing labels in scope
        Set<String> lexicalNames = parserContext.statements.parseFunctionStatements(body);
        // Check that parameter names don't conflict with lexical declarations in body
        // Per spec: "It is a Syntax Error if BoundNames of FormalParameters also occurs
        // in the LexicallyDeclaredNames of FunctionBody"
        ParameterEarlyErrors.validateFormalsBodyDuplicate(funcParams, lexicalNames);

        // Restore outer strict mode
        parserContext.setStrictMode(savedStrictMode);

        tokens.expect(TokenType.RBRACE);
        BlockStatement block = new BlockStatement(body, location);
        block.setHasUseStrictDirective(hasUseStrict);
        return block;
    }

    FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator) {
        return parseFunctionDeclaration(isAsync, isGenerator, null);
    }

    FunctionDeclaration parseFunctionDeclaration(boolean isAsync, boolean isGenerator, SourceLocation startLocation) {
        // Use provided start location (for async functions) or get current location
        SourceLocation location = startLocation != null ? startLocation : tokens.getLocation();
        tokens.expect(TokenType.FUNCTION);

        // Check for generator function: function* or async function*
        // Following QuickJS implementation: check for '*' after 'function' keyword
        if (tokens.consumeIf(TokenType.MUL)) {
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

    private <T extends ASTNode> T parseFunctionDefinition(Identifier id, SourceLocation location, boolean isAsync,
            boolean isGenerator, FunctionFactory<T> factory) {
        parserContext.enterFunctionContext(isAsync, isGenerator);
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

            tokens.expect(TokenType.LPAREN);
            FunctionParams funcParams = parseFunctionParameters();

            parserContext.inFunctionBody = true;

            // Parse body with "use strict" detection and parameter validation
            BlockStatement body = parseFunctionBody(funcParams, id);
            boolean needsArguments = parserContext.needsArguments;

            // Use previousTokenEndOffset (position after closing '}') instead of
            // currentToken.offset() (position of NEXT token, which may include trailing comments)
            SourceLocation fullLocation = location.withEndOffset(tokens.previousEnd());

            return factory.create(id, funcParams, body, isAsync, isGenerator, needsArguments, fullLocation);
        } finally {
            parserContext.inFunctionBody = savedInFunctionBody;
            parserContext.inClassStaticInit = savedInClassStaticInit;
            parserContext.exitFunctionContext();
        }
    }

    Expression parseFunctionExpression() {
        return parseFunctionExpression(false, null);
    }

    Expression parseFunctionExpression(boolean isAsync, SourceLocation startLocation) {
        SourceLocation location = startLocation != null ? startLocation : tokens.getLocation();
        tokens.expect(TokenType.FUNCTION);

        // Check for generator function expression: function* () {}
        // Following QuickJS implementation: check for '*' after 'function' keyword
        boolean isGenerator = tokens.consumeIf(TokenType.MUL);

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

        if (!tokens.match(TokenType.RPAREN)) {
            while (true) {
                // Check for rest parameter (...args)
                if (tokens.match(TokenType.ELLIPSIS)) {
                    SourceLocation location = tokens.consumeLocation(); // consume '...'
                    Pattern restArg = parserContext.patterns.parsePattern();
                    restParameter = new RestParameter(restArg, location);

                    // Rest parameter must be last
                    if (tokens.match(TokenType.COMMA)) {
                        throw new JSSyntaxErrorException("Rest parameter must be last formal parameter");
                    }
                    break;
                }

                // Regular parameter or destructuring pattern
                // Following QuickJS js_parse_function_decl2: accept identifiers,
                // object patterns ({a, b}), and array patterns ([a, b])
                params.add(parserContext.patterns.parsePattern());

                // Check for default value: param = defaultExpr
                // Following QuickJS js_parse_function_decl2 pattern
                if (tokens.consumeIf(TokenType.ASSIGN)) {
                    defaults.add(parserContext.expressions.parseAssignmentExpression());
                } else {
                    defaults.add(null);
                }

                if (!tokens.consumeIf(TokenType.COMMA) || tokens.match(TokenType.RPAREN)) {
                    break;
                }
            }
        }

        tokens.expect(TokenType.RPAREN);
        return new FunctionParams(params, defaults, restParameter);
    }

    /**
     * Parse a method (constructor, method, getter, or setter). Uses the current token position as the method start for
     * source extraction.
     */
    FunctionExpression parseMethod(String kind) {
        return parseMethod(kind, tokens.getLocation(), false, false);
    }

    /**
     * Parse a method with explicit start location and async/generator flags. The methodStartLocation determines where
     * the source code begins (e.g., at 'get', 'set', 'async', '*', or the method name).
     */
    FunctionExpression parseMethod(String kind, SourceLocation methodStartLocation, boolean isAsync,
            boolean isGenerator) {
        parserContext.enterFunctionContext(isAsync, isGenerator);
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

            tokens.expect(TokenType.LPAREN);
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
            ParameterEarlyErrors.rejectDuplicates(funcParams);
            body = parseFunctionBody(funcParams, null);
            needsArguments = parserContext.needsArguments;
        } finally {
            parserContext.inFunctionBody = savedInFunctionBody;
            parserContext.inClassStaticInit = savedInClassStaticInit;
            parserContext.exitFunctionContext();
        }

        // Per ES2024 15.2.1: "use strict" in method body with non-simple parameters
        // is a SyntaxError. Methods are already strict (class bodies are strict mode),
        // but we must still check for the explicit directive + non-simple params combo.
        if (funcParams.hasNonSimpleParameters() && body.hasUseStrictDirective()) {
            throw new JSSyntaxErrorException(
                    "Illegal 'use strict' directive in function with non-simple parameter list");
        }

        SourceLocation fullLocation = methodStartLocation.withEndOffset(tokens.previousEnd());

        return new FunctionExpression(null, funcParams, body, isAsync, isGenerator, needsArguments, fullLocation);
    }

    /**
     * Parse method or field after the property name.
     */
    ClassElement parseMethodOrField(Expression key, boolean isStatic, boolean isPrivate, boolean computed,
            SourceLocation location) {
        // If next token is LPAREN, it's a method; otherwise it's a field
        if (!tokens.match(TokenType.LPAREN)) {
            validateClassFieldName(key, computed, isPrivate);
            Expression value = parseClassFieldInitializer();
            parserContext.consumeSemicolon();
            return new PropertyDefinition(key, value, computed, isStatic, isPrivate);
        }

        // It's a method
        FunctionExpression method = parseClassMethod("method", location, false, false, isStatic, computed, key);
        return new MethodDefinition(key, method, "method", computed, isStatic, isPrivate);
    }

    private MethodDefinition parseNamedClassMethod(String kind, SourceLocation location, boolean async,
            boolean generator, boolean isStatic) {
        boolean computed = tokens.match(TokenType.LBRACKET);
        boolean isPrivate = tokens.match(TokenType.PRIVATE_NAME);
        Expression key = parseClassElementName();
        FunctionExpression method = parseClassMethod(kind, location, async, generator, isStatic, computed, key);
        return new MethodDefinition(key, method, kind, computed, isStatic, isPrivate);
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
            BlockStatement blockStatement = parserContext.statements.parseBlockStatement(true);
            return new StaticBlock(blockStatement.getBody());
        } finally {
            parserContext.exitFunctionLabelScope(enclosingLabels);
            parserContext.generatorFunctionNesting = savedGeneratorFunctionNesting;
            parserContext.asyncFunctionNesting = savedAsyncFunctionNesting;
            parserContext.superPropertyAllowed = savedSuperPropertyAllowed;
            parserContext.inClassStaticInit = savedInClassStaticInit;
        }
    }

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
     * A class's name binds in strict mode code, so it cannot be {@code eval} or {@code arguments}.
     */
    private static void validateClassBindingName(Identifier id) {
        if (JSKeyword.EVAL.equals(id.getName()) || JSKeyword.ARGUMENTS.equals(id.getName())) {
            throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
        }
    }

    private static void validateClassElements(List<ClassElement> classElements) {
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

    private static void validateClassFieldName(Expression key, boolean computed, boolean isPrivate) {
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

    @FunctionalInterface
    private interface ClassFactory<T extends ASTNode> {
        T create(Identifier id, Expression superClass, List<ClassElement> body, SourceLocation location);
    }

    @FunctionalInterface
    private interface FunctionFactory<T extends ASTNode> {
        T create(Identifier id, FunctionParams params, BlockStatement body, boolean isAsync, boolean isGenerator,
                boolean needsArguments, SourceLocation location);
    }
}
