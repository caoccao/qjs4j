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
import com.caoccao.qjs4j.compilation.lexer.*;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.unicode.UnicodeStringUtils;
import java.util.*;

/** Parses module declarations and records their imported bindings and exported names. */
final class ModuleDeclarationParser {
    private final ParserDelegates delegates;
    private final List<ExportSpecifier> exportSpecifiers = new ArrayList<>();
    private final ParserContext parserContext;

    ModuleDeclarationParser(ParserContext parserContext, ParserDelegates delegates) {
        this.parserContext = parserContext;
        this.delegates = delegates;
    }

    private void addModuleExportedName(String name) {
        if (parserContext.moduleMode && !parserContext.moduleExportedNames.add(name)) {
            throw new JSSyntaxErrorException("Duplicate export of '" + name + "'");
        }
    }

    /**
     * Collect declared names from an exported declaration and register them as exported names.
     */
    private void collectDeclaredNames(Statement decl) {
        if (!parserContext.moduleMode) {
            return;
        }
        if (decl instanceof VariableDeclaration varDecl) {
            for (VariableDeclarator declarator : varDecl.getDeclarations()) {
                declarator.getId().getBoundNames().forEach(this::addModuleExportedName);
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

    private void expectContextualKeyword(TokenType type, String keyword) {
        if (!parserContext.match(type) || parserContext.currentToken.escaped()) {
            throw new JSSyntaxErrorException(
                    "Expected '" + keyword + "' but got '" + parserContext.currentToken.value() + "' at line "
                            + parserContext.currentToken.line() + ", column " + parserContext.currentToken.column());
        }
        parserContext.advance();
    }

    Statement parseExportDeclaration() {
        SourceLocation location = parserContext.getLocation();
        parserContext.expect(TokenType.EXPORT);

        if (parserContext.match(TokenType.DEFAULT)) {
            parserContext.requireUnescapedToken("Unexpected token IDENTIFIER");
            addModuleExportedName("default");
            parserContext.advance();

            if (parserContext.match(TokenType.CLASS)) {
                // export default class { ... } — infer name "default" per spec
                ClassExpression classExpr = delegates.functions.parseClassExpression();
                String className = classExpr.getId() != null ? classExpr.getId().getName() : null;
                if (classExpr.getId() == null) {
                    classExpr = new ClassExpression(new Identifier(JSKeyword.DEFAULT, classExpr.getLocation()),
                            classExpr.getSuperClass(), classExpr.getBody(), classExpr.getLocation());
                }
                // Named class in export default creates a lexical binding
                if (className != null) {
                    delegates.declarations.addModuleLexicalName(className);
                }
                return new ExpressionStatement(classExpr, location);
            } else if (parserContext.match(TokenType.FUNCTION)) {
                Statement funcDecl = delegates.functions.parseExportDefaultFunctionDeclaration(false);
                if (parserContext.match(TokenType.LPAREN)) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                return funcDecl;
            } else if (parserContext.match(TokenType.ASYNC) && parserContext.peek() != null
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
        if (parserContext.consumeIf(TokenType.MUL)) {
            if (parserContext.match(TokenType.AS)) {
                parserContext.requireUnescapedToken("Unexpected token IDENTIFIER");
                parserContext.advance();
                // export name can be identifier or string literal
                String exportedName = parseModuleExportName();
                addModuleExportedName(exportedName);
            }
            expectContextualKeyword(TokenType.FROM, "from");
            parseSourceAndAttributes();
            parserContext.consumeSemicolon();
            return new BlockStatement(List.of(), location);
        }

        // export var/let/const/function/class ...
        if (parserContext.match(TokenType.VAR) || parserContext.match(TokenType.LET)
                || parserContext.match(TokenType.CONST)) {
            Statement decl = delegates.statements.parseVariableDeclaration();
            // Track exported names from declaration
            collectDeclaredNames(decl);
            return decl;
        }
        if (parserContext.match(TokenType.FUNCTION)) {
            Statement decl = delegates.functions.parseFunctionDeclaration(false, false);
            collectDeclaredNames(decl);
            return decl;
        }
        if (parserContext.match(TokenType.ASYNC) && parserContext.peek() != null
                && parserContext.peek().type() == TokenType.FUNCTION
                && parserContext.peek().line() == parserContext.currentToken.line()) {
            Statement decl = delegates.statements.parseAsyncDeclaration();
            collectDeclaredNames(decl);
            return decl;
        }
        if (parserContext.match(TokenType.CLASS)) {
            Statement decl = delegates.functions.parseClassDeclaration();
            collectDeclaredNames(decl);
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
     * Parse export { specifiers } and optional 'from' clause. Handles string export/import names per ES2024
     * ModuleExportName.
     */
    private void parseExportNamedSpecifiers() {
        parserContext.advance(); // consume '{'
        boolean hasStringLocalName = false;
        exportSpecifiers.clear();
        while (!parserContext.match(TokenType.RBRACE)) {
            if (parserContext.consumeIf(TokenType.COMMA)) {
                continue;
            }
            // The local name in export { local as exported }
            boolean localIsString = parserContext.match(TokenType.STRING);
            if (localIsString) {
                hasStringLocalName = true;
            }
            String localName = parseModuleExportName();
            String exportedName = localName;
            if (parserContext.match(TokenType.IDENTIFIER) && JSKeyword.AS.equals(parserContext.currentToken.value())) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            if (parserContext.match(TokenType.AS)) {
                parserContext.requireUnescapedToken("Unexpected token IDENTIFIER");
                parserContext.advance();
                // The exported name can also be identifier or string
                exportedName = parseModuleExportName();
            }
            exportSpecifiers.add(new ExportSpecifier(localName, exportedName));
            parserContext.consumeIf(TokenType.COMMA);
        }
        parserContext.expect(TokenType.RBRACE);
        boolean hasFrom = false;
        if (parserContext.match(TokenType.FROM)) {
            parserContext.requireUnescapedToken("Unexpected token IDENTIFIER");
            hasFrom = true;
            parserContext.advance();
            parseSourceAndAttributes();
        } else if (hasStringLocalName) {
            // String local names require a 'from' clause per spec
            throw new JSSyntaxErrorException("Expected 'from' but got '" + parserContext.currentToken.value() + "'");
        }
        // Register exported names and track local bindings
        for (ExportSpecifier spec : exportSpecifiers) {
            addModuleExportedName(spec.exportedName());
            if (!hasFrom) {
                // Without 'from', the local names must be bound locally
                parserContext.pendingExportBindings.add(spec.localName());
            }
        }
    }

    private void parseImportBindings() {
        if (parserContext.consumeIf(TokenType.MUL)) {
            expectContextualKeyword(TokenType.AS, "as");
            String name = parserContext.parseBindingIdentifierName();
            delegates.declarations.addModuleLexicalName(name);
        } else if (parserContext.match(TokenType.LBRACE)) {
            parseNamedImportSpecifiers();
        } else {
            throw new JSSyntaxErrorException("Unsupported import declaration");
        }
    }

    Statement parseImportDeclarationStatement() {
        if (!parserContext.moduleMode || parserContext.functionNesting != 0) {
            throw new JSSyntaxErrorException("Cannot use import statement outside a module");
        }

        parserContext.expect(TokenType.IMPORT);

        // Side-effect-only import: import 'module';
        if (parserContext.consumeIf(TokenType.STRING)) {
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // In a deferred namespace import the rest of the clause has ordinary namespace syntax.
        if (parserContext.match(TokenType.IDENTIFIER) && "defer".equals(parserContext.currentToken.value())
                && parserContext.peek() != null && parserContext.peek().type() == TokenType.MUL) {
            parserContext.advance();
        }
        if (parserContext.match(TokenType.IDENTIFIER)) {
            String name = parserContext.parseBindingIdentifierName();
            delegates.declarations.addModuleLexicalName(name);
            if (parserContext.consumeIf(TokenType.COMMA)) {
                parseImportBindings();
            }
        } else {
            parseImportBindings();
        }
        expectContextualKeyword(TokenType.FROM, "from");
        parseSourceAndAttributes();
        parserContext.consumeSemicolon();
        return null;
    }

    private String parseModuleExportName() {
        TokenType type = parserContext.currentToken.type();
        if (type == TokenType.STRING) {
            String name = parserContext.currentToken.value();
            // ES2024: ModuleExportName string must not contain unpaired surrogates
            if (UnicodeStringUtils.hasUnpairedSurrogate(name)) {
                throw new JSSyntaxErrorException("Invalid module export name: unpaired surrogate");
            }
            parserContext.advance();
            return name;
        }
        // Any IdentifierName including reserved keywords is allowed as
        // import/export specifier names (ES2024 ModuleExportName).
        if (type.isIdentifierName()) {
            String name = parserContext.consumeValue();
            return name;
        }
        throw new JSSyntaxErrorException("Unexpected token '" + parserContext.currentToken.value() + "'");
    }

    private void parseNamedImportSpecifiers() {
        parserContext.expect(TokenType.LBRACE);
        if (!parserContext.match(TokenType.RBRACE)) {
            while (true) {
                String importName = parseModuleExportName();
                String localName;
                if (parserContext.match(TokenType.AS) && !parserContext.currentToken.escaped()) {
                    parserContext.advance();
                    localName = parserContext.parseBindingIdentifierName();
                } else {
                    localName = importName;
                }
                // In strict mode (modules are always strict), eval/arguments are forbidden
                if (parserContext.strictMode
                        && (JSKeyword.EVAL.equals(localName) || JSKeyword.ARGUMENTS.equals(localName))) {
                    throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                }
                delegates.declarations.addModuleLexicalName(localName);
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

    private void parseSourceAndAttributes() {
        parserContext.expect(TokenType.STRING);
        parseWithClause();
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
                } else if (parserContext.currentToken.type().isIdentifierName()) {
                    // AttributeKey is IdentifierName, not IdentifierReference, so a reserved word
                    // is a valid key: `import x from './m.js' with {if: ''}` parses.
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

    private record ExportSpecifier(String localName, String exportedName) {
    }
}
