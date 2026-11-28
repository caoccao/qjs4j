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
final class ModuleDeclarationParser extends GrammarProduction {
    private final List<ExportSpecifier> exportSpecifiers = new ArrayList<>();

    private final Set<String> moduleExportedNames = new HashSet<>();
    private final Set<String> moduleLexicalNames = new HashSet<>();
    private final List<String> pendingExportBindings = new ArrayList<>();

    ModuleDeclarationParser(ParserContext parserContext) {
        super(parserContext);
    }

    private void addModuleExportedName(String name) {
        if (parserContext.moduleMode && !moduleExportedNames.add(name)) {
            throw new JSSyntaxErrorException("Duplicate export of '" + name + "'");
        }
    }

    void addModuleLexicalName(String name) {
        if (parserContext.moduleMode && parserContext.functionNesting == 0 && !moduleLexicalNames.add(name)) {
            throw DeclarationEarlyErrors.duplicate(name);
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
        if (!tokens.match(type) || tokens.current().escaped()) {
            throw new JSSyntaxErrorException("Expected '" + keyword + "' but got '" + tokens.current().value()
                    + "' at line " + tokens.current().line() + ", column " + tokens.current().column());
        }
        tokens.advance();
    }

    Statement parseExportDeclaration() {
        SourceLocation location = tokens.getLocation();
        tokens.expect(TokenType.EXPORT);

        if (tokens.match(TokenType.DEFAULT)) {
            tokens.requireUnescapedToken("Unexpected token IDENTIFIER");
            addModuleExportedName("default");
            tokens.advance();

            if (tokens.match(TokenType.CLASS)) {
                // export default class { ... } — infer name "default" per spec
                ClassExpression classExpr = parserContext.functions.parseClassExpression();
                String className = classExpr.getId() != null ? classExpr.getId().getName() : null;
                if (classExpr.getId() == null) {
                    classExpr = new ClassExpression(new Identifier(JSKeyword.DEFAULT, classExpr.getLocation()),
                            classExpr.getSuperClass(), classExpr.getBody(), classExpr.getLocation());
                }
                // Named class in export default creates a lexical binding
                if (className != null) {
                    addModuleLexicalName(className);
                }
                return new ExpressionStatement(classExpr, location);
            } else if (tokens.match(TokenType.FUNCTION)) {
                Statement funcDecl = parserContext.functions.parseExportDefaultFunctionDeclaration(false);
                if (tokens.match(TokenType.LPAREN)) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                return funcDecl;
            } else if (tokens.match(TokenType.ASYNC) && tokens.nextOnSameLine(TokenType.FUNCTION)) {
                Statement asyncDecl = parserContext.functions.parseExportDefaultAsyncFunctionDeclaration();
                if (tokens.match(TokenType.LPAREN)) {
                    throw new JSSyntaxErrorException("Unexpected token '('");
                }
                return asyncDecl;
            } else {
                // export default <expression>;
                Expression expr = parserContext.expressions.parseAssignmentExpression();
                parserContext.consumeSemicolon();
                return new ExpressionStatement(expr, location);
            }
        }

        // export * from 'module';
        // export * as name from 'module';
        // export * as "string" from 'module';
        if (tokens.consumeIf(TokenType.MUL)) {
            if (tokens.match(TokenType.AS)) {
                tokens.requireUnescapedToken("Unexpected token IDENTIFIER");
                tokens.advance();
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
        if (tokens.match(TokenType.VAR) || tokens.match(TokenType.LET) || tokens.match(TokenType.CONST)) {
            Statement decl = parserContext.statements.parseVariableDeclaration();
            // Track exported names from declaration
            collectDeclaredNames(decl);
            return decl;
        }
        if (tokens.match(TokenType.FUNCTION)) {
            Statement decl = parserContext.functions.parseFunctionDeclaration(false, false);
            collectDeclaredNames(decl);
            return decl;
        }
        if (tokens.match(TokenType.ASYNC) && tokens.nextOnSameLine(TokenType.FUNCTION)) {
            Statement decl = parserContext.statements.parseAsyncDeclaration();
            collectDeclaredNames(decl);
            return decl;
        }
        if (tokens.match(TokenType.CLASS)) {
            Statement decl = parserContext.functions.parseClassDeclaration();
            collectDeclaredNames(decl);
            return decl;
        }
        if (tokens.match(TokenType.LBRACE)) {
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
        tokens.advance(); // consume '{'
        boolean hasStringLocalName = false;
        exportSpecifiers.clear();
        while (!tokens.match(TokenType.RBRACE)) {
            if (tokens.consumeIf(TokenType.COMMA)) {
                continue;
            }
            // The local name in export { local as exported }
            boolean localIsString = tokens.match(TokenType.STRING);
            if (localIsString) {
                hasStringLocalName = true;
            }
            String localName = parseModuleExportName();
            String exportedName = localName;
            if (tokens.identifier(JSKeyword.AS)) {
                throw new JSSyntaxErrorException("Unexpected token IDENTIFIER");
            }
            if (tokens.match(TokenType.AS)) {
                tokens.requireUnescapedToken("Unexpected token IDENTIFIER");
                tokens.advance();
                // The exported name can also be identifier or string
                exportedName = parseModuleExportName();
            }
            exportSpecifiers.add(new ExportSpecifier(localName, exportedName));
            tokens.consumeIf(TokenType.COMMA);
        }
        tokens.expect(TokenType.RBRACE);
        boolean hasFrom = false;
        if (tokens.match(TokenType.FROM)) {
            tokens.requireUnescapedToken("Unexpected token IDENTIFIER");
            hasFrom = true;
            tokens.advance();
            parseSourceAndAttributes();
        } else if (hasStringLocalName) {
            // String local names require a 'from' clause per spec
            throw new JSSyntaxErrorException("Expected 'from' but got '" + tokens.current().value() + "'");
        }
        // Register exported names and track local bindings
        for (ExportSpecifier spec : exportSpecifiers) {
            addModuleExportedName(spec.exportedName());
            if (!hasFrom) {
                // Without 'from', the local names must be bound locally
                pendingExportBindings.add(spec.localName());
            }
        }
    }

    private void parseImportBindings() {
        if (tokens.consumeIf(TokenType.MUL)) {
            expectContextualKeyword(TokenType.AS, "as");
            String name = parserContext.parseBindingIdentifierName();
            addModuleLexicalName(name);
        } else if (tokens.match(TokenType.LBRACE)) {
            parseNamedImportSpecifiers();
        } else {
            throw new JSSyntaxErrorException("Unsupported import declaration");
        }
    }

    Statement parseImportDeclarationStatement() {
        if (!parserContext.moduleMode || parserContext.functionNesting != 0) {
            throw new JSSyntaxErrorException("Cannot use import statement outside a module");
        }

        tokens.expect(TokenType.IMPORT);

        // Side-effect-only import: import 'module';
        if (tokens.consumeIf(TokenType.STRING)) {
            parseWithClause();
            parserContext.consumeSemicolon();
            return null;
        }

        // In a deferred namespace import the rest of the clause has ordinary namespace syntax.
        if (tokens.identifier("defer") && tokens.nextIs(TokenType.MUL)) {
            tokens.advance();
        }
        if (tokens.match(TokenType.IDENTIFIER)) {
            String name = parserContext.parseBindingIdentifierName();
            addModuleLexicalName(name);
            if (tokens.consumeIf(TokenType.COMMA)) {
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
        TokenType type = tokens.current().type();
        if (type == TokenType.STRING) {
            String name = tokens.current().value();
            // ES2024: ModuleExportName string must not contain unpaired surrogates
            if (UnicodeStringUtils.hasUnpairedSurrogate(name)) {
                throw new JSSyntaxErrorException("Invalid module export name: unpaired surrogate");
            }
            tokens.advance();
            return name;
        }
        // Any IdentifierName including reserved keywords is allowed as
        // import/export specifier names (ES2024 ModuleExportName).
        if (type.isIdentifierName()) {
            String name = tokens.consumeValue();
            return name;
        }
        throw tokens.unexpectedToken();
    }

    private void parseNamedImportSpecifiers() {
        tokens.expect(TokenType.LBRACE);
        if (!tokens.match(TokenType.RBRACE)) {
            while (true) {
                String importName = parseModuleExportName();
                String localName;
                if (tokens.match(TokenType.AS) && !tokens.current().escaped()) {
                    tokens.advance();
                    localName = parserContext.parseBindingIdentifierName();
                } else {
                    localName = importName;
                }
                // In strict mode (modules are always strict), eval/arguments are forbidden
                if (parserContext.strictMode
                        && (JSKeyword.EVAL.equals(localName) || JSKeyword.ARGUMENTS.equals(localName))) {
                    throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
                }
                addModuleLexicalName(localName);
                if (!tokens.match(TokenType.COMMA)) {
                    break;
                }
                tokens.advance();
                if (tokens.match(TokenType.RBRACE)) {
                    break;
                }
            }
        }
        tokens.expect(TokenType.RBRACE);
    }

    private void parseSourceAndAttributes() {
        tokens.expect(TokenType.STRING);
        parseWithClause();
    }

    private void parseWithClause() {
        if (tokens.identifier("with")) {
            tokens.advance(); // consume 'with'
            tokens.expect(TokenType.LBRACE);
            Set<String> attributeKeys = new HashSet<>();
            while (!tokens.match(TokenType.RBRACE) && !tokens.match(TokenType.EOF)) {
                // Parse key: either identifier or string
                String key;
                if (tokens.match(TokenType.STRING)) {
                    key = tokens.current().value();
                    tokens.advance();
                } else if (tokens.current().type().isIdentifierName()) {
                    // AttributeKey is IdentifierName, not IdentifierReference, so a reserved word
                    // is a valid key: `import x from './m.js' with {if: ''}` parses.
                    key = tokens.current().value();
                    tokens.advance();
                } else {
                    throw new JSSyntaxErrorException("identifier expected");
                }
                if (!attributeKeys.add(key)) {
                    throw new JSSyntaxErrorException("Duplicate attribute key '" + key + "'");
                }
                tokens.expect(TokenType.COLON);
                tokens.expect(TokenType.STRING);
                if (!tokens.match(TokenType.COMMA)) {
                    break;
                }
                tokens.advance();
            }
            tokens.expect(TokenType.RBRACE);
        }
    }

    /**
     * Validate module-level early errors per ES2024 16.2.1.1. In modules, top-level function declarations are lexical
     * (not var-hoisted), so duplicate function names are errors. Also checks for lex/var conflicts, duplicate lexical
     * declarations, and unresolvable export bindings.
     */
    void validateModuleEarlyErrors(List<Statement> body) {
        Set<String> lexicalNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration declaration) {
                for (VariableDeclarator declarator : declaration.getDeclarations()) {
                    for (String name : declarator.getId().getBoundNames()) {
                        if (declaration.getKind() == VariableKind.VAR) {
                            if (lexicalNames.contains(name)) {
                                throw DeclarationEarlyErrors.duplicate(name);
                            }
                            varNames.add(name);
                        } else {
                            DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, name);
                        }
                    }
                }
            } else if (stmt instanceof FunctionDeclaration declaration && declaration.getId() != null) {
                DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, declaration.getId().getName());
            } else if (stmt instanceof ClassDeclaration declaration && declaration.getId() != null) {
                DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, declaration.getId().getName());
            } else if (stmt instanceof ExpressionStatement expression
                    && expression.getExpression() instanceof ClassExpression cls && cls.getId() != null
                    && !"default".equals(cls.getId().getName())) {
                DeclarationEarlyErrors.addLexicalName(lexicalNames, varNames, cls.getId().getName());
            }
        }

        // ES2024 16.2.1.1: For each name in ExportedBindings,
        // it must be in the module's BoundNames.
        for (String exportBinding : pendingExportBindings) {
            if (!moduleLexicalNames.contains(exportBinding) && !lexicalNames.contains(exportBinding)
                    && !varNames.contains(exportBinding)) {
                throw new JSSyntaxErrorException("Export '" + exportBinding + "' is not defined in module");
            }
        }
    }
    private record ExportSpecifier(String localName, String exportedName) {
    }
}
