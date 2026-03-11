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
import com.caoccao.qjs4j.unicode.UnicodeStringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delegate parser responsible for parsing JavaScript statements.
 * Handles control flow (if, while, for, switch, try), declarations (var, let, const, using),
 * and statement-level constructs (block, labeled, return, break, continue, throw).
 */
record StatementParser(ParserContext parserContext, ParserDelegates delegates) {
    private static boolean isIdentifierNameToken(TokenType type) {
        return switch (type) {
            case IDENTIFIER, AS, ASYNC, AWAIT, BREAK, CASE, CATCH, CLASS, CONST,
                 CONTINUE, DEFAULT, DELETE, DO, ELSE, EXPORT, EXTENDS, FALSE,
                 FINALLY, FOR, FROM, FUNCTION, IF, IMPORT, IN, INSTANCEOF, LET,
                 NEW, NULL, OF, RETURN, SUPER, SWITCH, THIS, THROW, TRUE, TRY,
                 TYPEOF, VAR, VOID, WHILE, YIELD -> true;
            default -> false;
        };
    }

    private void addModuleExportedName(String name) {
        if (parserContext.moduleMode && !parserContext.moduleExportedNames.add(name)) {
            throw new JSSyntaxErrorException("Duplicate export of '" + name + "'");
        }
    }

    private void addModuleLexicalName(String name) {
        if (parserContext.moduleMode && parserContext.functionNesting == 0) {
            if (parserContext.moduleVarNames.contains(name) || !parserContext.moduleLexicalNames.add(name)) {
                throw new JSSyntaxErrorException(
                        "Identifier '" + name + "' has already been declared");
            }
        }
    }

    /**
     * Collect declared names from an exported declaration and register them as exported names.
     */
    private void collectDeclaredNames(Statement decl, boolean isVar) {
        if (!parserContext.moduleMode) {
            return;
        }
        if (decl instanceof VariableDeclaration varDecl) {
            for (VariableDeclaration.VariableDeclarator declarator : varDecl.getDeclarations()) {
                collectPatternNames(declarator.getId(), name -> {
                    addModuleExportedName(name);
                });
            }
        } else if (decl instanceof FunctionDeclaration funcDecl) {
            if (funcDecl.getId() != null) {
                addModuleExportedName(funcDecl.getId().getName());
            }
        } else if (decl instanceof ClassDeclaration classDecl) {
            if (classDecl.getId() != null) {
                addModuleExportedName(classDecl.getId().getName());
            }
        }
    }

    private void collectPatternBoundNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier identifier) {
            names.add(identifier.getName());
            return;
        }
        if (pattern instanceof ArrayPattern arrayPattern) {
            for (Pattern element : arrayPattern.getElements()) {
                if (element != null) {
                    collectPatternBoundNames(element, names);
                }
            }
            return;
        }
        if (pattern instanceof ObjectPattern objectPattern) {
            for (ObjectPatternProperty property : objectPattern.getProperties()) {
                collectPatternBoundNames(property.getValue(), names);
            }
            if (objectPattern.getRestElement() != null) {
                collectPatternBoundNames(objectPattern.getRestElement().getArgument(), names);
            }
            return;
        }
        if (pattern instanceof RestElement restElement) {
            collectPatternBoundNames(restElement.getArgument(), names);
        }
    }

    private void collectPatternBoundNamesAndCheckDuplicates(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier identifier) {
            String name = identifier.getName();
            if (!names.add(name)) {
                throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
            }
            return;
        }
        if (pattern instanceof ArrayPattern arrayPattern) {
            for (Pattern element : arrayPattern.getElements()) {
                if (element != null) {
                    collectPatternBoundNamesAndCheckDuplicates(element, names);
                }
            }
            return;
        }
        if (pattern instanceof ObjectPattern objectPattern) {
            for (ObjectPatternProperty property : objectPattern.getProperties()) {
                collectPatternBoundNamesAndCheckDuplicates(property.getValue(), names);
            }
            if (objectPattern.getRestElement() != null) {
                collectPatternBoundNamesAndCheckDuplicates(objectPattern.getRestElement().getArgument(), names);
            }
            return;
        }
        if (pattern instanceof AssignmentPattern assignmentPattern) {
            collectPatternBoundNamesAndCheckDuplicates(assignmentPattern.getLeft(), names);
            return;
        }
        if (pattern instanceof RestElement restElement) {
            collectPatternBoundNamesAndCheckDuplicates(restElement.getArgument(), names);
        }
    }

    /**
     * Collect binding names from a pattern (identifier, array, object destructuring).
     */
    private void collectPatternNames(Pattern pattern, java.util.function.Consumer<String> consumer) {
        if (pattern instanceof Identifier id) {
            consumer.accept(id.getName());
        } else if (pattern instanceof ObjectPattern objPat) {
            for (ObjectPatternProperty prop : objPat.getProperties()) {
                collectPatternNames(prop.getValue(), consumer);
            }
            if (objPat.getRestElement() != null) {
                collectPatternNames(objPat.getRestElement(), consumer);
            }
        } else if (pattern instanceof ArrayPattern arrayPat) {
            for (Pattern elem : arrayPat.getElements()) {
                if (elem != null) {
                    collectPatternNames(elem, consumer);
                }
            }
        } else if (pattern instanceof RestElement rest) {
            collectPatternNames(rest.getArgument(), consumer);
        } else if (pattern instanceof AssignmentPattern assign) {
            collectPatternNames(assign.getLeft(), consumer);
        }
    }

    private void collectVarDeclaredNames(Statement statement, Set<String> varNames) {
        if (statement instanceof VariableDeclaration variableDeclaration) {
            if (variableDeclaration.getKind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternBoundNames(declarator.getId(), varNames);
                }
            }
            return;
        }
        if (statement instanceof BlockStatement blockStatement) {
            for (Statement blockItem : blockStatement.getBody()) {
                collectVarDeclaredNames(blockItem, varNames);
            }
            return;
        }
        if (statement instanceof IfStatement ifStatement) {
            collectVarDeclaredNames(ifStatement.getConsequent(), varNames);
            if (ifStatement.getAlternate() != null) {
                collectVarDeclaredNames(ifStatement.getAlternate(), varNames);
            }
            return;
        }
        if (statement instanceof ForStatement forStatement) {
            if (forStatement.getInit() instanceof Statement initStatement) {
                collectVarDeclaredNames(initStatement, varNames);
            }
            collectVarDeclaredNames(forStatement.getBody(), varNames);
            return;
        }
        if (statement instanceof ForInStatement forInStatement) {
            if (forInStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternBoundNames(declarator.getId(), varNames);
                }
            }
            collectVarDeclaredNames(forInStatement.getBody(), varNames);
            return;
        }
        if (statement instanceof ForOfStatement forOfStatement) {
            if (forOfStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    collectPatternBoundNames(declarator.getId(), varNames);
                }
            }
            collectVarDeclaredNames(forOfStatement.getBody(), varNames);
            return;
        }
        if (statement instanceof WhileStatement whileStatement) {
            collectVarDeclaredNames(whileStatement.getBody(), varNames);
            return;
        }
        if (statement instanceof DoWhileStatement doWhileStatement) {
            collectVarDeclaredNames(doWhileStatement.getBody(), varNames);
            return;
        }
        if (statement instanceof SwitchStatement switchStatement) {
            for (SwitchStatement.SwitchCase switchCase : switchStatement.getCases()) {
                for (Statement consequentStatement : switchCase.getConsequent()) {
                    collectVarDeclaredNames(consequentStatement, varNames);
                }
            }
            return;
        }
        if (statement instanceof TryStatement tryStatement) {
            collectVarDeclaredNames(tryStatement.getBlock(), varNames);
            if (tryStatement.getHandler() != null) {
                collectVarDeclaredNames(tryStatement.getHandler().getBody(), varNames);
            }
            if (tryStatement.getFinalizer() != null) {
                collectVarDeclaredNames(tryStatement.getFinalizer(), varNames);
            }
        }
    }

    private boolean containsCoverInitializedName(ObjectExpression objectExpression) {
        for (ObjectExpressionProperty property : objectExpression.getProperties()) {
            if (!property.isShorthand()) {
                continue;
            }
            if (property.getValue() instanceof AssignmentExpression assignmentExpression
                    && assignmentExpression.getOperator() == AssignmentOperator.ASSIGN) {
                return true;
            }
        }
        return false;
    }

    private void expectContextualKeyword(TokenType type, String keyword) {
        if (!parserContext.match(type) || parserContext.currentToken.escaped()) {
            throw new JSSyntaxErrorException(
                    "Expected '" + keyword + "' but got '" + parserContext.currentToken.value() +
                            "' at line " + parserContext.currentToken.line() +
                            ", column " + parserContext.currentToken.column());
        }
        parserContext.advance();
    }

    private boolean hasUsingInitializer(VariableDeclaration variableDeclaration) {
        if (variableDeclaration == null) {
            return false;
        }
        VariableKind kind = variableDeclaration.getKind();
        if (kind != VariableKind.USING && kind != VariableKind.AWAIT_USING) {
            return false;
        }
        for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
            if (declarator != null && declarator.getInit() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isDebuggerKeyword() {
        return parserContext.currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.DEBUGGER.equals(parserContext.currentToken.value())
                && !parserContext.currentToken.escaped();
    }

    private boolean isLabelledFunction(Statement statement) {
        if (!(statement instanceof LabeledStatement labeledStatement)) {
            return false;
        }
        if (labeledStatement.getBody() instanceof FunctionDeclaration) {
            return true;
        }
        return labeledStatement.getBody() != null && isLabelledFunction(labeledStatement.getBody());
    }

    private boolean isWithKeyword() {
        return parserContext.currentToken.type() == TokenType.IDENTIFIER
                && JSKeyword.WITH.equals(parserContext.currentToken.value());
    }

    Statement parseAsyncDeclaration() {
        // ES2024: [no LineTerminator here] between `async` and `function`.
        // If there's a line terminator, `async` is an identifier expression statement.
        if (parserContext.nextToken.type() == TokenType.FUNCTION
                && parserContext.nextToken.line() == parserContext.currentToken.line()) {
            SourceLocation asyncLocation = parserContext.getLocation();
            parserContext.advance(); // consume async
            return delegates.functions.parseFunctionDeclaration(true, false, asyncLocation);
        } else {
            return parseExpressionStatement();
        }
    }

    BlockStatement parseBlockStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.LBRACE);

        List<Statement> body = new ArrayList<>();
        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        validateBlockEarlyErrors(body);
        parserContext.expect(TokenType.RBRACE);
        return new BlockStatement(body, location);
    }

    Statement parseBreakStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.BREAK);
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (parserContext.match(TokenType.IDENTIFIER) && !parserContext.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return new BreakStatement(label, location);
    }

    Statement parseContinueStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.CONTINUE);
        // Check for optional label (identifier on same line, no ASI)
        Identifier label = null;
        if (parserContext.match(TokenType.IDENTIFIER) && !parserContext.hasNewlineBefore()) {
            label = parserContext.parseIdentifier();
        }
        parserContext.consumeSemicolon();
        return new ContinueStatement(label, location);
    }

    private Statement parseDebuggerStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.advance();
        parserContext.consumeSemicolon();
        return new DebuggerStatement(location);
    }

    Statement parseDoWhileStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.DO);
        Statement body = parseStatement();
        validateNoLabelledFunctionInIterationBody(body);
        if (body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in do-while statement position");
        }
        validateNoLexicalDeclarationInStatementPosition(body);
        validateNoUsingDeclarationWithInitializerInStatementPosition(body);
        parserContext.expect(TokenType.WHILE);
        parserContext.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        if (parserContext.match(TokenType.SEMICOLON)) {
            parserContext.advance();
        }
        return new DoWhileStatement(body, test, location);
    }

    Statement parseExportDeclaration() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.EXPORT);

        if (parserContext.match(TokenType.DEFAULT)) {
            if (parserContext.currentToken.escaped()) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            addModuleExportedName("default");
            parserContext.advance();

            if (parserContext.match(TokenType.CLASS)) {
                // export default class { ... } — infer name "default" per spec
                ClassExpression classExpr = delegates.functions.parseClassExpression();
                String className = classExpr.getId() != null ? classExpr.getId().getName() : null;
                if (classExpr.getId() == null) {
                    classExpr = new ClassExpression(
                            new Identifier(JSKeyword.DEFAULT, classExpr.getLocation()),
                            classExpr.getSuperClass(),
                            classExpr.getBody(),
                            classExpr.getLocation());
                }
                // Named class in export default creates a lexical binding
                if (className != null) {
                    addModuleLexicalName(className);
                }
                return new ExpressionStatement(classExpr, location);
            } else if (parserContext.match(TokenType.FUNCTION)) {
                Statement funcDecl = delegates.functions.parseExportDefaultFunctionDeclaration(false);
                if (parserContext.match(TokenType.LPAREN)) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                return funcDecl;
            } else if (parserContext.match(TokenType.ASYNC)
                    && parserContext.peek() != null
                    && parserContext.peek().type() == TokenType.FUNCTION
                    && parserContext.peek().line() == parserContext.currentToken.line()) {
                Statement asyncDecl = delegates.functions.parseExportDefaultAsyncFunctionDeclaration();
                if (parserContext.match(TokenType.LPAREN)) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                return asyncDecl;
            } else {
                // export default <expression>;
                Expression expr = delegates.expressions.parseAssignmentExpression();
                parserContext.consumeSemicolon();
                return new ExpressionStatement(expr, location);
            }
        }

        // export * from 'module';
        // export * as name from 'module';
        // export * as "string" from 'module';
        if (parserContext.match(TokenType.MUL)) {
            parserContext.advance();
            if (parserContext.match(TokenType.AS)) {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
                }
                parserContext.advance();
                // export name can be identifier or string literal
                String exportedName = parseModuleExportNameAndReturn();
                addModuleExportedName(exportedName);
            }
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return new BlockStatement(List.of(), location);
        }

        // export var/let/const/function/class ...
        if (parserContext.match(TokenType.VAR) || parserContext.match(TokenType.LET) || parserContext.match(TokenType.CONST)) {
            boolean isVar = parserContext.match(TokenType.VAR);
            Statement decl = parseVariableDeclaration();
            // Track exported names from declaration
            collectDeclaredNames(decl, isVar);
            return decl;
        }
        if (parserContext.match(TokenType.FUNCTION)) {
            Statement decl = delegates.functions.parseFunctionDeclaration(false, false);
            collectDeclaredNames(decl, false);
            return decl;
        }
        if (parserContext.match(TokenType.ASYNC)
                && parserContext.peek() != null
                && parserContext.peek().type() == TokenType.FUNCTION
                && parserContext.peek().line() == parserContext.currentToken.line()) {
            Statement decl = parseAsyncDeclaration();
            collectDeclaredNames(decl, false);
            return decl;
        }
        if (parserContext.match(TokenType.CLASS)) {
            Statement decl = delegates.functions.parseClassDeclaration();
            collectDeclaredNames(decl, false);
            return decl;
        }
        if (parserContext.match(TokenType.LBRACE)) {
            parseExportNamedSpecifiers();
            parserContext.consumeSemicolon();
            return new BlockStatement(List.of(), location);
        }

        throw new JSSyntaxErrorException("Unexpected export syntax");
    }

    /**
     * Parse export { specifiers } and optional 'from' clause.
     * Handles string export/import names per ES2024 ModuleExportName.
     */
    private void parseExportNamedSpecifiers() {
        parserContext.advance(); // consume '{'
        boolean hasStringLocalName = false;
        List<String[]> specifiers = new ArrayList<>(); // [localName, exportedName]
        while (!parserContext.match(TokenType.RBRACE)) {
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
                continue;
            }
            // The local name in export { local as exported }
            boolean localIsString = parserContext.match(TokenType.STRING);
            if (localIsString) {
                hasStringLocalName = true;
            }
            String localName = parseModuleExportNameAndReturn();
            String exportedName = localName;
            if (parserContext.match(TokenType.IDENTIFIER)
                    && JSKeyword.AS.equals(parserContext.currentToken.value())) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            if (parserContext.match(TokenType.AS)) {
                if (parserContext.currentToken.escaped()) {
                    throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
                }
                parserContext.advance();
                // The exported name can also be identifier or string
                exportedName = parseModuleExportNameAndReturn();
            }
            specifiers.add(new String[]{localName, exportedName});
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
            }
        }
        parserContext.expect(TokenType.RBRACE);
        boolean hasFrom = false;
        if (parserContext.match(TokenType.FROM)) {
            if (parserContext.currentToken.escaped()) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            hasFrom = true;
            parserContext.advance();
            parserContext.expect(TokenType.STRING);
            parseWithClause();
        } else if (hasStringLocalName) {
            // String local names require a 'from' clause per spec
            throw new JSSyntaxErrorException(
                    "Expected 'from' but got '" + parserContext.currentToken.value() + "'");
        }
        // Register exported names and track local bindings
        for (String[] spec : specifiers) {
            addModuleExportedName(spec[1]);
            if (!hasFrom) {
                // Without 'from', the local names must be bound locally
                parserContext.pendingExportBindings.add(spec[0]);
            }
        }
    }

    Statement parseExpressionStatement() {
        SourceLocation location = parserContext.getLocation();
        Expression expression = delegates.expressions.parseExpression();
        if (expression instanceof ObjectExpression objectExpression
                && containsCoverInitializedName(objectExpression)) {
            throw new JSSyntaxErrorException("Invalid shorthand property initializer");
        }
        parserContext.consumeSemicolon();
        return new ExpressionStatement(expression, location);
    }

    Statement parseForStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.FOR);

        // Check for 'await' keyword (for await...of)
        boolean isAwait = false;
        if (parserContext.match(TokenType.AWAIT)) {
            if (!parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            isAwait = true;
            parserContext.advance();
        }

        parserContext.expect(TokenType.LPAREN);

        // Check if this is a for-of or for-in loop
        // We need to peek ahead to see if there's 'of' or 'in' after the variable declaration
        boolean isForOf = false;
        boolean isForIn = false;
        Statement parsedDecl = null;

        // Try to parse as variable declaration (without consuming semicolon,
        // since we need to check for 'of' or 'in' first)
        boolean parseAsDeclarationHead = parserContext.match(TokenType.VAR)
                || parserContext.match(TokenType.CONST)
                || parserContext.isUsingDeclarationStart()
                || parserContext.isAwaitUsingDeclarationStart()
                || parserContext.match(TokenType.LET);
        if (parseAsDeclarationHead && parserContext.match(TokenType.LET)) {
            parseAsDeclarationHead = shouldParseLetAsLexicalDeclarationInForHead();
        }
        if (parseAsDeclarationHead) {
            // Annex B: suppress 'in' as binary operator only for 'var' in non-strict mode
            // (allows for-in initializers: for (var a = expr in obj))
            boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
            boolean isVar = parserContext.match(TokenType.VAR);
            if (isVar && !parserContext.strictMode) {
                parserContext.inOperatorAllowed = false;
            }
            if (parserContext.isAwaitUsingDeclarationStart()) {
                SourceLocation declLocation = parserContext.getLocation();
                parserContext.expect(TokenType.AWAIT);
                parserContext.advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.AWAIT_USING, declLocation, false);
            } else if (parserContext.isUsingDeclarationStart()) {
                SourceLocation declLocation = parserContext.getLocation();
                parserContext.advance(); // consume 'using'
                parsedDecl = parseVariableDeclarationBody(VariableKind.USING, declLocation, false);
            } else {
                SourceLocation declLocation = parserContext.getLocation();
                VariableKind kind = VariableKind.fromKeyword(parserContext.currentToken.value());
                parserContext.advance();
                parsedDecl = parseVariableDeclarationBody(kind, declLocation, false);
            }
            parserContext.inOperatorAllowed = savedInOperatorAllowed;
            // Check if next token is 'of' or 'in'
            if (parserContext.match(TokenType.OF)) {
                isForOf = true;
            } else if (parserContext.match(TokenType.IN)) {
                isForIn = true;
            }
        }

        if (isForOf) {
            // This is a for-of loop: for (let x of iterable)
            if (!(parsedDecl instanceof VariableDeclaration varDecl)) {
                throw new JSSyntaxErrorException("Expected VariableDeclaration in for-of loop");
            }
            if (varDecl.getDeclarations().size() != 1) {
                throw new JSSyntaxErrorException("for-of loop must have exactly one declaration");
            }
            validateForInOfDeclarationBoundNames(varDecl);
            if (parsedDecl instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getDeclarations().stream().anyMatch(
                    variableDeclarator -> variableDeclarator != null && variableDeclarator.getInit() != null)) {
                throw new JSSyntaxErrorException("Invalid initializer in for-of declaration");
            }
            parserContext.expect(TokenType.OF);
            // Per grammar, for-of RHS is AssignmentExpression (not full Expression).
            Expression iterable = delegates.expressions.parseAssignmentExpression();
            parserContext.expect(TokenType.RPAREN);
            Statement body = parseStatement();
            validateNoLabelledFunctionInIterationBody(body);
            validateNoFunctionDeclarationInIterationBody(body, "for-of");
            validateNoLexicalDeclarationInStatementPosition(body);
            validateNoUsingDeclarationWithInitializerInStatementPosition(body);
            validateForInOfDeclarationBodyEarlyErrors(varDecl, body);

            return new ForOfStatement(varDecl, iterable, body, isAwait, location);
        }

        if (isForIn) {
            if (isAwait) {
                throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
            }
            // ES2024: using/await using not allowed in for-in loops
            if (parsedDecl instanceof VariableDeclaration varDeclCheck
                    && (varDeclCheck.getKind() == VariableKind.USING || varDeclCheck.getKind() == VariableKind.AWAIT_USING)) {
                throw new JSSyntaxErrorException("The left-hand side of a for-in loop may not be a using declaration");
            }
            if (!(parsedDecl instanceof VariableDeclaration varDecl)) {
                throw new JSSyntaxErrorException("Expected VariableDeclaration in for-in loop");
            }
            if (varDecl.getDeclarations().size() != 1) {
                throw new JSSyntaxErrorException("for-in loop must have exactly one declaration");
            }
            validateForInOfDeclarationBoundNames(varDecl);
            validateForInDeclarationInitializers(varDecl);
            // This is a for-in loop: for (var x in obj)
            parserContext.expect(TokenType.IN);
            Expression object = delegates.expressions.parseExpression();
            parserContext.expect(TokenType.RPAREN);
            Statement body = parseStatement();
            validateNoLabelledFunctionInIterationBody(body);
            validateNoFunctionDeclarationInIterationBody(body, "for-in");
            validateNoLexicalDeclarationInStatementPosition(body);
            validateNoUsingDeclarationWithInitializerInStatementPosition(body);
            validateForInOfDeclarationBodyEarlyErrors(varDecl, body);

            return new ForInStatement(varDecl, object, body, location);
        }

        // Not a for-of loop, parse as traditional for loop
        // Reset if we parsed a var declaration but it's not for-of
        Statement init = null;
        if (parsedDecl != null) {
            if (isAwait) {
                throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
            }
            init = parsedDecl;
            if (init instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.CONST
                    && variableDeclaration.getDeclarations().stream().anyMatch(
                    variableDeclarator -> variableDeclarator.getInit() == null)) {
                throw new JSSyntaxErrorException("Missing initializer in const declaration");
            }
            parserContext.expect(TokenType.SEMICOLON); // consume ; after init declaration
        } else if (!parserContext.match(TokenType.SEMICOLON)) {
            boolean parseAsDeclarationInTraditionalFor = parserContext.match(TokenType.VAR)
                    || parserContext.match(TokenType.LET)
                    || parserContext.match(TokenType.CONST)
                    || parserContext.isUsingDeclarationStart()
                    || parserContext.isAwaitUsingDeclarationStart();
            if (parseAsDeclarationInTraditionalFor && parserContext.match(TokenType.LET)) {
                parseAsDeclarationInTraditionalFor = shouldParseLetAsLexicalDeclarationInForHead();
            }
            if (parseAsDeclarationInTraditionalFor) {
                if (parserContext.isAwaitUsingDeclarationStart()) {
                    init = parseUsingDeclaration(true);
                } else if (parserContext.isUsingDeclarationStart()) {
                    init = parseUsingDeclaration(false);
                } else {
                    init = parseVariableDeclaration();
                }
            } else {
                // Parse expression -- could be for-in/for-of left side or traditional for init.
                // Parse as assignment expression first, then check for 'in' or 'of'.
                // Suppress 'in' as binary operator per ES spec [~In] grammar parameter.
                boolean savedInOperatorAllowed = parserContext.inOperatorAllowed;
                TokenType forHeadStartTokenType = parserContext.currentToken.type();
                boolean leftStartsWithLetToken = forHeadStartTokenType == TokenType.LET;
                boolean leftStartsWithAsyncToken = forHeadStartTokenType == TokenType.ASYNC;
                boolean allowInInsideDestructuringHead = parserContext.currentToken.type() == TokenType.LBRACKET
                        || parserContext.currentToken.type() == TokenType.LBRACE;
                parserContext.inOperatorAllowed = allowInInsideDestructuringHead;
                Expression expr = delegates.expressions.parseAssignmentExpression();
                parserContext.inOperatorAllowed = savedInOperatorAllowed;
                if (expr instanceof BinaryExpression binaryExpression
                        && binaryExpression.getOperator() == BinaryOperator.IN
                        && parserContext.match(TokenType.RPAREN)) {
                    if (isAwait) {
                        throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
                    }
                    Expression leftExpression = binaryExpression.getLeft();
                    if (!parserContext.isValidForInOfTarget(leftExpression)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    int errorOffset = binaryExpression.getLocation() != null
                            ? binaryExpression.getLocation().offset()
                            : parserContext.currentToken.offset();
                    delegates.expressions.validateForInOfAssignmentTarget(leftExpression, errorOffset);
                    parserContext.expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    validateNoLabelledFunctionInIterationBody(body);
                    validateNoFunctionDeclarationInIterationBody(body, "for-in");
                    validateNoLexicalDeclarationInStatementPosition(body);
                    validateNoUsingDeclarationWithInitializerInStatementPosition(body);
                    return new ForInStatement(leftExpression, binaryExpression.getRight(), body, location);
                }
                if (parserContext.match(TokenType.IN)) {
                    if (isAwait) {
                        throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
                    }
                    // for (expr in obj) -- expression-based for-in
                    // Validate: left side must be a valid LeftHandSideExpression
                    if (!parserContext.isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    delegates.expressions.validateForInOfAssignmentTarget(expr, parserContext.currentToken.offset());
                    parserContext.advance(); // consume 'in'
                    Expression object = delegates.expressions.parseExpression();
                    parserContext.expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    validateNoLabelledFunctionInIterationBody(body);
                    validateNoFunctionDeclarationInIterationBody(body, "for-in");
                    validateNoLexicalDeclarationInStatementPosition(body);
                    validateNoUsingDeclarationWithInitializerInStatementPosition(body);
                    return new ForInStatement(expr, object, body, location);
                } else if (parserContext.match(TokenType.OF)) {
                    // for (expr of iterable) -- expression-based for-of
                    if (!isAwait && leftStartsWithLetToken) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    if (!isAwait
                            && leftStartsWithAsyncToken
                            && expr instanceof Identifier identifier
                            && JSKeyword.ASYNC.equals(identifier.getName())) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    if (!parserContext.isValidForInOfTarget(expr)) {
                        throw new JSSyntaxErrorException("invalid for in/of left hand-side");
                    }
                    delegates.expressions.validateForInOfAssignmentTarget(expr, parserContext.currentToken.offset());
                    parserContext.advance(); // consume 'of'
                    // Per grammar, for-of RHS is AssignmentExpression (not full Expression).
                    Expression iterable = delegates.expressions.parseAssignmentExpression();
                    parserContext.expect(TokenType.RPAREN);
                    Statement body = parseStatement();
                    validateNoLabelledFunctionInIterationBody(body);
                    validateNoFunctionDeclarationInIterationBody(body, "for-of");
                    validateNoLexicalDeclarationInStatementPosition(body);
                    validateNoUsingDeclarationWithInitializerInStatementPosition(body);
                    return new ForOfStatement(expr, iterable, body, isAwait, location);
                }
                if (isAwait) {
                    throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
                }
                // Traditional for loop init -- handle comma expressions
                while (parserContext.match(TokenType.COMMA)) {
                    parserContext.advance();
                    Expression right = delegates.expressions.parseAssignmentExpression();
                    expr = new SequenceExpression(List.of(expr, right), expr.getLocation());
                }
                init = new ExpressionStatement(expr, expr.getLocation());
                parserContext.consumeSemicolon();
            }
        } else {
            if (isAwait) {
                throw new JSSyntaxErrorException("'for await' loop should be used with 'of'");
            }
            parserContext.advance(); // consume semicolon
        }

        // Test
        Expression test = null;
        if (!parserContext.match(TokenType.SEMICOLON)) {
            test = delegates.expressions.parseExpression();
        }
        parserContext.expect(TokenType.SEMICOLON);

        // Update
        Expression update = null;
        if (!parserContext.match(TokenType.RPAREN)) {
            update = delegates.expressions.parseExpression();
        }
        parserContext.expect(TokenType.RPAREN);

        Statement body = parseStatement();
        validateNoLabelledFunctionInIterationBody(body);
        if (body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in for statement position");
        }
        validateNoLexicalDeclarationInStatementPosition(body);
        validateNoUsingDeclarationWithInitializerInStatementPosition(body);
        if (init instanceof VariableDeclaration varDecl && varDecl.getKind() != VariableKind.VAR) {
            validateForInOfDeclarationBodyEarlyErrors(varDecl, body);
        }

        return new ForStatement(init, test, update, body, location);
    }

    Statement parseIfStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.IF);
        parserContext.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);

        Statement consequent = parseStatement();
        validateNoLexicalDeclarationInStatementPosition(consequent);
        validateNoUsingDeclarationWithInitializerInStatementPosition(consequent);
        Statement alternate = null;

        if (parserContext.match(TokenType.ELSE)) {
            parserContext.advance();
            alternate = parseStatement();
            validateNoLexicalDeclarationInStatementPosition(alternate);
            validateNoUsingDeclarationWithInitializerInStatementPosition(alternate);
        }

        return new IfStatement(test, consequent, alternate, location);
    }

    Statement parseImportDeclarationStatement() {
        SourceLocation location = parserContext.getLocation();
        if (!parserContext.moduleMode || parserContext.functionNesting != 0) {
            throw new JSSyntaxErrorException("Cannot use import statement outside a module");
        }

        parserContext.expect(TokenType.IMPORT);

        // Side-effect-only import: import 'module';
        if (parserContext.match(TokenType.STRING)) {
            parserContext.advance(); // module specifier
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // import defer * as ns from 'module';
        if (parserContext.match(TokenType.IDENTIFIER)
                && "defer".equals(parserContext.currentToken.value())
                && parserContext.peek() != null
                && parserContext.peek().type() == TokenType.MUL) {
            parserContext.advance(); // consume 'defer'
            parserContext.advance(); // consume '*'
            expectContextualKeyword(TokenType.AS, "as");
            Identifier nsBinding = parserContext.parseIdentifier();
            addModuleLexicalName(nsBinding.getName());
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // Namespace import: import * as ns from 'module';
        if (parserContext.match(TokenType.MUL)) {
            parserContext.advance();
            expectContextualKeyword(TokenType.AS, "as");
            Identifier nsBinding = parserContext.parseIdentifier();
            addModuleLexicalName(nsBinding.getName());
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // Named imports: import { x, y as z } from 'module';
        if (parserContext.match(TokenType.LBRACE)) {
            parseNamedImportSpecifiers();
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // Default import, possibly with named/namespace: import foo from 'module';
        if (parserContext.match(TokenType.IDENTIFIER)) {
            Identifier defaultBinding = parserContext.parseIdentifier();
            addModuleLexicalName(defaultBinding.getName());
            Set<String> boundNames = new HashSet<>();
            boundNames.add(defaultBinding.getName());
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
                if (parserContext.match(TokenType.MUL)) {
                    parserContext.advance();
                    expectContextualKeyword(TokenType.AS, "as");
                    Identifier nsBinding = parserContext.parseIdentifier();
                    addModuleLexicalName(nsBinding.getName());
                    if (boundNames.contains(nsBinding.getName())) {
                        throw new JSSyntaxErrorException("duplicate import binding");
                    }
                } else if (parserContext.match(TokenType.LBRACE)) {
                    parseNamedImportSpecifiers(boundNames);
                } else {
                    throw new JSSyntaxErrorException("Unsupported import declaration");
                }
            }
            expectContextualKeyword(TokenType.FROM, "from");
            parserContext.expect(TokenType.STRING);
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        throw new JSSyntaxErrorException("Unsupported import declaration");
    }

    private String parseImportIdentifierName() {
        TokenType type = parserContext.currentToken.type();
        if (type == TokenType.STRING) {
            String name = parserContext.currentToken.value();
            // ES2024: ModuleExportName string must not contain unpaired surrogates
            if (UnicodeStringUtils.hasUnpairedSurrogate(name)) {
                throw new JSSyntaxErrorException(
                        "Invalid module export name: unpaired surrogate");
            }
            parserContext.advance();
            return name;
        }
        // Any IdentifierName including reserved keywords is allowed as
        // import/export specifier names (ES2024 ModuleExportName).
        if (isIdentifierNameToken(type)) {
            String name = parserContext.currentToken.value();
            parserContext.advance();
            return name;
        }
        throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
    }

    /**
     * Parse a labeled statement: label: statement
     * Following QuickJS js_parse_statement_or_decl label handling.
     * In non-strict mode, labeled function declarations are allowed (Annex B).
     */
    Statement parseLabeledStatement() {
        SourceLocation location = parserContext.getLocation();
        Identifier label = parserContext.parseIdentifier();
        // ES2024 14.13.1: It is a Syntax Error if any source text is matched by
        // this production that also matches the LabelIdentifier of an enclosing LabelledStatement.
        for (Set<String> labels : parserContext.labelStack) {
            if (labels.contains(label.getName())) {
                throw new JSSyntaxErrorException("Label '" + label.getName() + "' has already been declared");
            }
        }
        parserContext.expect(TokenType.COLON);
        Set<String> currentLabels = new HashSet<>();
        currentLabels.add(label.getName());
        parserContext.labelStack.push(currentLabels);
        try {
            Statement body = parseStatement();
            validateNoLexicalDeclarationInStatementPosition(body);
            validateNoUsingDeclarationWithInitializerInStatementPosition(body);
            return new LabeledStatement(label, body, location);
        } finally {
            parserContext.labelStack.pop();
        }
    }

    /**
     * Parse a module export name without returning value (backward compat).
     */
    private void parseModuleExportName() {
        parseModuleExportNameAndReturn();
    }

    /**
     * Parse a module export name: either an identifier name or a string literal.
     * ModuleExportName :: IdentifierName | StringLiteral
     * Returns the parsed name for tracking purposes.
     */
    private String parseModuleExportNameAndReturn() {
        if (parserContext.match(TokenType.STRING)) {
            String name = parserContext.currentToken.value();
            // ES2024: ModuleExportName string must not contain unpaired surrogates
            if (UnicodeStringUtils.hasUnpairedSurrogate(name)) {
                throw new JSSyntaxErrorException(
                        "Invalid module export name: unpaired surrogate");
            }
            parserContext.advance();
            return name;
        } else {
            // Any IdentifierName including keywords is allowed as export name
            String name = parseImportIdentifierName();
            return name;
        }
    }

    private void parseNamedImportSpecifiers() {
        parseNamedImportSpecifiers(new HashSet<>());
    }

    private void parseNamedImportSpecifiers(Set<String> boundNames) {
        parserContext.expect(TokenType.LBRACE);
        if (!parserContext.match(TokenType.RBRACE)) {
            while (true) {
                String importName = parseImportIdentifierName();
                String localName;
                if (parserContext.match(TokenType.AS) && !parserContext.currentToken.escaped()) {
                    parserContext.advance();
                    localName = parserContext.parseIdentifier().getName();
                } else {
                    localName = importName;
                }
                // In strict mode (modules are always strict), eval/arguments are forbidden
                if (parserContext.strictMode
                        && (JSKeyword.EVAL.equals(localName) || JSKeyword.ARGUMENTS.equals(localName))) {
                    throw new JSSyntaxErrorException(
                            "Unexpected eval or arguments in strict mode");
                }
                addModuleLexicalName(localName);
                if (!boundNames.add(localName)) {
                    throw new JSSyntaxErrorException("duplicate import binding");
                }
                if (!parserContext.match(TokenType.COMMA)) {
                    break;
                }
                parserContext.advance();
                if (parserContext.match(TokenType.RBRACE)) {
                    break;
                }
            }
        }
        parserContext.expect(TokenType.RBRACE);
    }

    Statement parseReturnStatement() {
        SourceLocation location = parserContext.getLocation();
        // Return is only valid inside function bodies.
        if (parserContext.inClassStaticInit || parserContext.functionNesting == 0) {
            throw new JSSyntaxErrorException("return not in a function");
        }
        parserContext.expect(TokenType.RETURN);

        Expression argument = null;
        if (!parserContext.hasNewlineBefore()
                && !parserContext.match(TokenType.SEMICOLON)
                && !parserContext.match(TokenType.RBRACE)
                && !parserContext.match(TokenType.EOF)) {
            argument = delegates.expressions.parseExpression();
        }

        parserContext.consumeSemicolon();
        return new ReturnStatement(argument, location);
    }

    Statement parseStatement() {
        parserContext.statementNesting++;
        try {
            if (parserContext.isAwaitUsingDeclarationStart()) {
                return parseUsingDeclaration(true);
            }
            if (parserContext.isUsingDeclarationStart()) {
                return parseUsingDeclaration(false);
            }
            if (isDebuggerKeyword()) {
                return parseDebuggerStatement();
            }
            if (isWithKeyword()) {
                return parseWithStatement();
            }
            // Check for labeled statement: identifier followed by ':'
            // Following QuickJS is_label() check
            if (parserContext.currentToken.type() == TokenType.IDENTIFIER && parserContext.peek() != null && parserContext.peek().type() == TokenType.COLON) {
                return parseLabeledStatement();
            }
            return switch (parserContext.currentToken.type()) {
                case IF -> parseIfStatement();
                case DO -> parseDoWhileStatement();
                case WHILE -> parseWhileStatement();
                case FOR -> parseForStatement();
                case RETURN -> parseReturnStatement();
                case BREAK -> parseBreakStatement();
                case CONTINUE -> parseContinueStatement();
                case THROW -> parseThrowStatement();
                case TRY -> parseTryStatement();
                case SWITCH -> parseSwitchStatement();
                case LBRACE -> parseBlockStatement();
                case LET -> {
                    // In sloppy mode, 'let' can be an identifier (not a declaration keyword)
                    // when NOT followed by an identifier, '[', or '{'
                    if (!parserContext.strictMode) {
                        Token nextTok = parserContext.peek();
                        boolean hasLineTerminatorAfterLet = nextTok != null && nextTok.line() > parserContext.currentToken.line();
                        boolean treatLetAsExpressionByAsi = hasLineTerminatorAfterLet
                                && (nextTok.type() == TokenType.IDENTIFIER || nextTok.type() == TokenType.LBRACE);
                        if (nextTok == null
                                || treatLetAsExpressionByAsi
                                || (nextTok.type() != TokenType.IDENTIFIER
                                && nextTok.type() != TokenType.LBRACKET
                                && nextTok.type() != TokenType.LBRACE
                                && nextTok.type() != TokenType.ASYNC
                                && nextTok.type() != TokenType.AWAIT
                                && nextTok.type() != TokenType.YIELD
                                && nextTok.type() != TokenType.LET)) {
                            yield parseExpressionStatement();
                        }
                    }
                    yield parseVariableDeclaration();
                }
                case VAR, CONST -> parseVariableDeclaration();
                case ASYNC -> // Async function declaration: async function f() {}
                        parseAsyncDeclaration();
                case FUNCTION -> // Function declarations are treated as statements in JavaScript
                        delegates.functions.parseFunctionDeclaration(false, false);
                case CLASS -> // Class declarations are treated as statements in JavaScript
                        delegates.functions.parseClassDeclaration();
                case EXPORT -> {
                    if (!parserContext.moduleMode || parserContext.statementNesting > 1) {
                        throw new JSSyntaxErrorException("Unexpected token 'export'");
                    }
                    yield parseExportDeclaration();
                }
                case IMPORT -> {
                    Token nextToken = parserContext.peek();
                    boolean isDynamicImportExpression = nextToken != null
                            && (nextToken.type() == TokenType.LPAREN || nextToken.type() == TokenType.DOT);
                    if (!isDynamicImportExpression) {
                        if (parserContext.moduleMode && parserContext.functionNesting == 0
                                && parserContext.statementNesting <= 1) {
                            yield parseImportDeclarationStatement();
                        }
                        throw new JSSyntaxErrorException("Cannot use import statement outside a module");
                    }
                    yield parseExpressionStatement();
                }
                case SEMICOLON -> {
                    parserContext.advance(); // consume semicolon
                    yield null; // empty statement
                }
                default -> parseExpressionStatement();
            };
        } finally {
            parserContext.statementNesting--;
        }
    }

    Statement parseSwitchStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.SWITCH);
        parserContext.expect(TokenType.LPAREN);
        Expression discriminant = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        parserContext.expect(TokenType.LBRACE);

        List<SwitchStatement.SwitchCase> cases = new ArrayList<>();

        while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
            if (parserContext.match(TokenType.CASE)) {
                parserContext.advance();
                Expression test = delegates.expressions.parseExpression();
                parserContext.expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!parserContext.match(TokenType.CASE) && !parserContext.match(TokenType.DEFAULT) &&
                        !parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        validateNoUsingDeclarationWithInitializerInCaseOrDefault(stmt);
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(test, consequent));
            } else if (parserContext.match(TokenType.DEFAULT)) {
                parserContext.advance();
                parserContext.expect(TokenType.COLON);

                List<Statement> consequent = new ArrayList<>();
                while (!parserContext.match(TokenType.CASE) && !parserContext.match(TokenType.DEFAULT) &&
                        !parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                    Statement stmt = parseStatement();
                    if (stmt != null) {
                        validateNoUsingDeclarationWithInitializerInCaseOrDefault(stmt);
                        consequent.add(stmt);
                    }
                }

                cases.add(new SwitchStatement.SwitchCase(null, consequent));
            } else {
                parserContext.advance(); // skip unexpected token
            }
        }

        parserContext.expect(TokenType.RBRACE);
        return new SwitchStatement(discriminant, cases, location);
    }

    Statement parseThrowStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.THROW);
        if (parserContext.hasNewlineBefore()) {
            throw new JSSyntaxErrorException("Illegal newline after throw");
        }
        Expression argument = delegates.expressions.parseExpression();
        parserContext.consumeSemicolon();
        return new ThrowStatement(argument, location);
    }

    Statement parseTryStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.TRY);
        BlockStatement block = parseBlockStatement();

        TryStatement.CatchClause handler = null;
        if (parserContext.match(TokenType.CATCH)) {
            parserContext.advance();
            Pattern param = null;
            if (parserContext.match(TokenType.LPAREN)) {
                parserContext.advance();
                param = delegates.patterns.parsePattern();
                parserContext.expect(TokenType.RPAREN);
            }
            BlockStatement catchBody = parseBlockStatement();
            handler = new TryStatement.CatchClause(param, catchBody);
        }

        BlockStatement finalizer = null;
        if (parserContext.match(TokenType.FINALLY)) {
            parserContext.advance();
            finalizer = parseBlockStatement();
        }

        return new TryStatement(block, handler, finalizer, location);
    }

    Statement parseUsingDeclaration(boolean isAwaitUsing) {
        SourceLocation location = parserContext.getLocation();
        VariableKind kind;
        if (isAwaitUsing) {
            if (!parserContext.isAwaitExpressionAllowed()) {
                throw new JSSyntaxErrorException("Unexpected 'await' keyword");
            }
            parserContext.expect(TokenType.AWAIT);
            if (!parserContext.isUsingIdentifierToken(parserContext.currentToken)) {
                throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
            }
            parserContext.advance();
            kind = VariableKind.AWAIT_USING;
        } else {
            if (!parserContext.isUsingIdentifierToken(parserContext.currentToken)) {
                throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
            }
            parserContext.advance();
            kind = VariableKind.USING;
        }
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclaration() {
        SourceLocation location = parserContext.getLocation();
        VariableKind kind = VariableKind.fromKeyword(parserContext.currentToken.value()); // VAR, LET, or CONST
        parserContext.advance();
        return parseVariableDeclarationBody(kind, location);
    }

    Statement parseVariableDeclarationBody(VariableKind kind, SourceLocation location) {
        return parseVariableDeclarationBody(kind, location, true);
    }

    VariableDeclaration parseVariableDeclarationBody(VariableKind kind, SourceLocation location, boolean consumeSemi) {
        boolean isUsingKind = kind == VariableKind.USING || kind == VariableKind.AWAIT_USING;
        List<VariableDeclaration.VariableDeclarator> declarations = new ArrayList<>();

        do {
            if (parserContext.match(TokenType.COMMA)) {
                parserContext.advance();
            }


            Pattern id = delegates.patterns.parsePattern();
            Set<String> declaredNames = new HashSet<>();
            if (kind != VariableKind.VAR) {
                collectPatternBoundNamesAndCheckDuplicates(id, declaredNames);
                if (declaredNames.contains(JSKeyword.LET)) {
                    throw new JSSyntaxErrorException("let is disallowed as a lexically bound name");
                }
            } else {
                collectPatternBoundNames(id, declaredNames);
            }
            if (parserContext.strictMode) {
                for (String declaredName : declaredNames) {
                    if (JSKeyword.EVAL.equals(declaredName) || JSKeyword.ARGUMENTS.equals(declaredName)) {
                        throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                    }
                }
            }
            if (isUsingKind && !(id instanceof Identifier)) {
                throw new JSSyntaxErrorException("Invalid using declaration");
            }
            Expression init = null;

            if (parserContext.match(TokenType.ASSIGN)) {
                parserContext.advance();
                init = delegates.expressions.parseAssignmentExpression();
            } else if (kind == VariableKind.CONST && consumeSemi) {
                throw new JSSyntaxErrorException("Missing initializer in const declaration");
            } else if (isUsingKind && consumeSemi) {
                throw new JSSyntaxErrorException("Missing initializer in using declaration");
            }

            declarations.add(new VariableDeclaration.VariableDeclarator(id, init));
        } while (parserContext.match(TokenType.COMMA));

        if (consumeSemi) {
            parserContext.consumeSemicolon();
        }
        return new VariableDeclaration(declarations, kind, location);
    }

    Statement parseWhileStatement() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.WHILE);
        parserContext.expect(TokenType.LPAREN);
        Expression test = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        Statement body = parseStatement();
        validateNoLabelledFunctionInIterationBody(body);
        if (body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException("Function declarations are not allowed in while statement position");
        }
        validateNoLexicalDeclarationInStatementPosition(body);
        validateNoUsingDeclarationWithInitializerInStatementPosition(body);

        return new WhileStatement(test, body, location);
    }

    private void parseWithClause() {
        if (parserContext.currentToken.type() == TokenType.IDENTIFIER
                && "with".equals(parserContext.currentToken.value())) {
            parserContext.advance(); // consume 'with'
            parserContext.expect(TokenType.LBRACE);
            Set<String> attributeKeys = new HashSet<>();
            while (!parserContext.match(TokenType.RBRACE) && !parserContext.match(TokenType.EOF)) {
                // Parse key: either identifier or string
                String key;
                if (parserContext.match(TokenType.STRING)) {
                    key = parserContext.currentToken.value();
                    parserContext.advance();
                } else if (parserContext.match(TokenType.IDENTIFIER)) {
                    key = parserContext.currentToken.value();
                    parserContext.advance();
                } else {
                    throw new JSSyntaxErrorException("identifier expected");
                }
                if (!attributeKeys.add(key)) {
                    throw new JSSyntaxErrorException("Duplicate attribute key '" + key + "'");
                }
                parserContext.expect(TokenType.COLON);
                parserContext.expect(TokenType.STRING);
                if (!parserContext.match(TokenType.COMMA)) {
                    break;
                }
                parserContext.advance();
            }
            parserContext.expect(TokenType.RBRACE);
        }
    }

    Statement parseWithStatement() {
        SourceLocation location = parserContext.getLocation();
        if (parserContext.strictMode) {
            throw new JSSyntaxErrorException("Strict mode code may not include a with statement");
        }
        if (!isWithKeyword()) {
            throw new JSSyntaxErrorException("Unexpected token");
        }
        parserContext.advance(); // consume 'with'
        parserContext.expect(TokenType.LPAREN);
        Expression object = delegates.expressions.parseExpression();
        parserContext.expect(TokenType.RPAREN);
        Statement body = parseStatement();
        validateNoLexicalDeclarationInStatementPosition(body);
        return new WithStatement(object, body, location);
    }

    private boolean shouldParseLetAsLexicalDeclarationInForHead() {
        if (!parserContext.match(TokenType.LET)) {
            return true;
        }
        if (parserContext.strictMode) {
            return true;
        }
        Token lookahead = parserContext.peek();
        if (lookahead == null) {
            return true;
        }
        return switch (lookahead.type()) {
            case IDENTIFIER, ASYNC, AWAIT, YIELD, FROM, OF, AS, LET, LBRACKET, LBRACE -> true;
            default -> false;
        };
    }

    private void validateBlockEarlyErrors(List<Statement> statements) {
        Set<String> lexicalNames = new HashSet<>();
        Set<String> simpleFunctionLexicalNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();

        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                    Set<String> names = new HashSet<>();
                    collectPatternBoundNames(declarator.getId(), names);
                    for (String name : names) {
                        if (!lexicalNames.add(name)) {
                            throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                        }
                    }
                }
            } else if (statement instanceof FunctionDeclaration functionDeclaration && functionDeclaration.getId() != null) {
                String name = functionDeclaration.getId().getName();
                boolean isSimpleFunction = !functionDeclaration.isAsync() && !functionDeclaration.isGenerator();
                if (lexicalNames.contains(name)) {
                    boolean duplicatedSimpleFunctionDeclaration = simpleFunctionLexicalNames.contains(name) && isSimpleFunction;
                    if (!duplicatedSimpleFunctionDeclaration || parserContext.strictMode) {
                        throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                    }
                } else {
                    lexicalNames.add(name);
                }
                if (isSimpleFunction) {
                    simpleFunctionLexicalNames.add(name);
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String name = classDeclaration.getId().getName();
                if (lexicalNames.contains(name)) {
                    throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                }
                lexicalNames.add(name);
            }
            collectVarDeclaredNames(statement, varNames);
        }

        for (String lexicalName : lexicalNames) {
            if (varNames.contains(lexicalName)) {
                throw new JSSyntaxErrorException("Identifier '" + lexicalName + "' has already been declared");
            }
        }
    }

    private void validateForInDeclarationInitializers(VariableDeclaration variableDeclaration) {
        if (variableDeclaration == null || variableDeclaration.getDeclarations().isEmpty()) {
            return;
        }
        VariableDeclaration.VariableDeclarator declarator = variableDeclaration.getDeclarations().get(0);
        if (declarator.getInit() == null) {
            return;
        }
        if (variableDeclaration.getKind() != VariableKind.VAR) {
            throw new JSSyntaxErrorException("Invalid initializer in for-in declaration");
        }
        if (parserContext.strictMode || !(declarator.getId() instanceof Identifier)) {
            throw new JSSyntaxErrorException("Invalid initializer in for-in declaration");
        }
    }

    private void validateForInOfDeclarationBodyEarlyErrors(VariableDeclaration declaration, Statement statement) {
        if (declaration == null || declaration.getKind() == VariableKind.VAR) {
            return;
        }
        Set<String> boundNames = new HashSet<>();
        for (VariableDeclaration.VariableDeclarator declarator : declaration.getDeclarations()) {
            collectPatternBoundNames(declarator.getId(), boundNames);
        }
        Set<String> varNames = new HashSet<>();
        collectVarDeclaredNames(statement, varNames);
        for (String boundName : boundNames) {
            if (varNames.contains(boundName)) {
                throw new JSSyntaxErrorException("Identifier '" + boundName + "' has already been declared");
            }
        }
    }

    private void validateForInOfDeclarationBoundNames(VariableDeclaration declaration) {
        if (declaration == null || declaration.getKind() == VariableKind.VAR) {
            return;
        }
        Set<String> boundNames = new HashSet<>();
        for (VariableDeclaration.VariableDeclarator declarator : declaration.getDeclarations()) {
            collectPatternBoundNamesAndCheckDuplicates(declarator.getId(), boundNames);
        }
    }

    private void validateNoFunctionDeclarationInIterationBody(Statement statement, String statementKind) {
        if (statement instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "Function declarations are not allowed in " + statementKind + " statement position");
        }
    }

    private void validateNoLabelledFunctionInIterationBody(Statement statement) {
        if (isLabelledFunction(statement)) {
            throw new JSSyntaxErrorException("Labelled functions are only allowed at body level");
        }
    }

    private void validateNoLexicalDeclarationInStatementPosition(Statement statement) {
        if (statement instanceof VariableDeclaration variableDeclaration
                && variableDeclaration.getKind() != VariableKind.VAR) {
            throw new JSSyntaxErrorException("Lexical declaration cannot appear in a single-statement context");
        }
        if (statement instanceof ClassDeclaration) {
            throw new JSSyntaxErrorException("Lexical declaration cannot appear in a single-statement context");
        }
    }

    private void validateNoUsingDeclarationWithInitializerInCaseOrDefault(Statement statement) {
        if (statement instanceof VariableDeclaration variableDeclaration && hasUsingInitializer(variableDeclaration)) {
            throw new JSSyntaxErrorException("using declarations are not allowed directly in case/default clauses");
        }
    }

    private void validateNoUsingDeclarationWithInitializerInStatementPosition(Statement statement) {
        if (statement instanceof VariableDeclaration variableDeclaration && hasUsingInitializer(variableDeclaration)) {
            throw new JSSyntaxErrorException("using declarations are not allowed in this statement position");
        }
    }

    void validateProgramEarlyErrors(List<Statement> statements) {
        Set<String> lexicalNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();

        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration variableDeclaration) {
                if (variableDeclaration.getKind() == VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        collectPatternBoundNames(declarator.getId(), varNames);
                    }
                } else {
                    for (VariableDeclaration.VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        Set<String> names = new HashSet<>();
                        collectPatternBoundNames(declarator.getId(), names);
                        for (String name : names) {
                            if (varNames.contains(name) || !lexicalNames.add(name)) {
                                throw new JSSyntaxErrorException("Identifier '" + name + "' has already been declared");
                            }
                        }
                    }
                }
            } else if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String className = classDeclaration.getId().getName();
                if (varNames.contains(className) || !lexicalNames.add(className)) {
                    throw new JSSyntaxErrorException("Identifier '" + className + "' has already been declared");
                }
            } else if (statement instanceof FunctionDeclaration functionDeclaration && functionDeclaration.getId() != null) {
                varNames.add(functionDeclaration.getId().getName());
            }
            collectVarDeclaredNames(statement, varNames);
        }

        for (String lexicalName : lexicalNames) {
            if (varNames.contains(lexicalName)) {
                throw new JSSyntaxErrorException("Identifier '" + lexicalName + "' has already been declared");
            }
        }
    }
}
