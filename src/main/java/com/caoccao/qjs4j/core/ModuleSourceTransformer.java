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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.compilation.ast.Statement;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.compilation.parser.Parser;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.unicode.UnicodeData;

import java.nio.file.Path;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites ES module source into plain script source, and answers every question about that source
 * the rest of the module machinery asks: which lines begin a declaration, where an
 * {@code export default} expression ends, what a specifier or an export name really spells.
 * <p>
 * This is the textual half of module support, kept together so it can be read — and tested —
 * without the loader, the linker or the realm around it. Almost everything here is a function from
 * strings and tokens to strings and tokens; the only realm state it touches is the context it
 * reports syntax errors through, the compiler it validates source with, and two callbacks it needs
 * to name a generated binding and to recognise a module importing itself.
 * <p>
 * The transform itself is documented on {@link #parseDynamicImportModuleSource}, including what it
 * still gets wrong and why the fix is real module records rather than better scanning. QuickJS does
 * this differently — {@code js_create_module_def} reads import and export entries out of the parsed
 * function rather than rewriting text — and moving to that is the intended destination; this class
 * exists so that it can be replaced behind the loader and linker seams instead of unpicked from
 * {@link JSContext}.
 */
final class ModuleSourceTransformer {
    /**
     * An ECMAScript {@code IdentifierName}, as a regular-expression fragment.
     * <p>
     * The module transformer used to re-extract binding names with {@code [A-Za-z_$][A-Za-z0-9_$]*},
     * which is not the identifier grammar the lexer three files away accepts. {@code café} and
     * {@code módulo} are ordinary identifiers; so is {@code \\u0078}, which <em>is</em> {@code x}.
     * The parser took all of them and the transformer did not, so valid source was handed to one
     * component that accepted it and another that assigned it different semantics — a module
     * exporting {@code café} reported no such export, and a namespace bound to {@code módulo} was
     * not defined when the module body ran.
     * <p>
     * Deliberately a little wider than the real grammar: whatever this matches is decoded by
     * {@link #decodeIdentifierEscapes(String)} and then checked against the Unicode tables by
     * {@link #isValidIdentifierName(String)}, which is the authority. Matching slightly too much
     * costs a rejection with a message; matching too little silently loses a binding.
     */
    static final String IDENTIFIER_NAME_REGEX =
            "(?:[\\p{L}\\p{Nl}$_]|\\\\u[0-9A-Fa-f]{4}|\\\\u\\{[0-9A-Fa-f]{1,6}\\})"
                    + "(?:[\\p{L}\\p{Nl}\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}$_\\u200C\\u200D]"
                    + "|\\\\u[0-9A-Fa-f]{4}|\\\\u\\{[0-9A-Fa-f]{1,6}\\})*";

    static final Pattern DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN =
            Pattern.compile("^class\\s+(" + IDENTIFIER_NAME_REGEX + ")");

    static final Pattern DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN =
            Pattern.compile("^(?:async\\s+)?function(?:\\s*\\*)?\\s+(" + IDENTIFIER_NAME_REGEX + ")");

    static final Pattern MODULE_EXPORT_SYNTAX_PATTERN =
            Pattern.compile("(?m)^\\s*export\\s");

    /**
     * The pseudo export name a namespace binding is recorded under — {@code import * as ns} and
     * {@code export * as ns from}, whose binding is a module's namespace object rather than a name
     * that module exports.
     */
    static final String MODULE_NAMESPACE_EXPORT_NAME = "*namespace*";

    /**
     * The body of a module specifier's string literal, as a regular-expression fragment.
     * <p>
     * A backslash escapes whatever follows it, so a quote character does not necessarily end the
     * literal and a line terminator does not necessarily end the declaration. The previous
     * {@code [^'"\r\n]+} could express neither: {@code './a\'b.mjs'} matched nothing at all, and
     * so did a specifier written across a LineContinuation.
     */
    static final String MODULE_SPECIFIER_BODY_REGEX = "(?:\\\\[\\s\\S]|[^'\"\\\\\\r\\n])+";

    static final Pattern MODULE_BINDING_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*([^;]*?)\\s+from\\s+(['\"])(" + MODULE_SPECIFIER_BODY_REGEX
                    + ")\\2(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    static final Pattern MODULE_NAMESPACE_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(?:(defer)\\s+)?\\*\\s*as\\s+(" + IDENTIFIER_NAME_REGEX
                    + ")\\s+from\\s+(['\"])(" + MODULE_SPECIFIER_BODY_REGEX
                    + ")\\3(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    static final Pattern MODULE_SIDE_EFFECT_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(['\"])(" + MODULE_SPECIFIER_BODY_REGEX
                    + ")\\1(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    static final Pattern MODULE_STATIC_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(?:defer\\s+)?(?:[^;]*?\\s+from\\s+)?['\"]("
                    + MODULE_SPECIFIER_BODY_REGEX + ")['\"](?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    static final Pattern MODULE_STATIC_IMPORT_SYNTAX_PATTERN =
            Pattern.compile("(?m)^\\s*import(?!\\s*\\(|\\s*\\.)");

    static final Pattern MODULE_TOP_LEVEL_AWAIT_PATTERN =
            Pattern.compile("(?m)^\\s*await\\b");

    static final Pattern MODULE_WITH_CLAUSE_PATTERN =
            Pattern.compile("with\\s*\\{([^}]*)\\}");

    private final JSContext context;

    ModuleSourceTransformer(JSContext context) {
        this.context = context;
    }

    /**
     * The index of the last token of a module declaration whose extent is identifiable without
     * parsing.
     * <p>
     * That is every {@code import} form, and the {@code export} forms that end at a module
     * specifier or at the closing brace of an export clause. {@code export default …} and
     * {@code export <declaration>} are not included: their extent is a declaration body, which is
     * the parser's job, and the transformer's existing multi-line lookahead already handles them.
     *
     * @param tokens the token list
     * @param start  the index of the {@code import} or {@code export} token
     * @return the index of the declaration's last token, or -1 when it is not identifiable
     */
    static int findModuleDeclarationEnd(List<Token> tokens, int start) {
        boolean isExport = tokens.get(start).type() == TokenType.EXPORT;
        int depth = 0;
        int clauseEnd = -1;
        for (int index = start + 1; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            switch (token.type()) {
                case LBRACE, LPAREN, LBRACKET -> depth++;
                case RBRACE, RPAREN, RBRACKET -> {
                    depth = Math.max(0, depth - 1);
                    if (depth == 0 && token.type() == TokenType.RBRACE && clauseEnd < 0) {
                        // The closing brace of an import/export clause: `{ a, b }`.
                        clauseEnd = index;
                    }
                }
                default -> {
                }
            }
            if (depth != 0) {
                continue;
            }
            if (token.type() == TokenType.STRING && isModuleSpecifierPosition(tokens, start, index)) {
                // The module specifier. Anything after it is a with-clause or a semicolon.
                return skipModuleDeclarationTail(tokens, index);
            }
            if (isExport && clauseEnd == index) {
                // `export { a, b };` with no `from`: the clause closes the declaration unless a
                // `from` follows, which the STRING branch above then picks up.
                if (index + 1 < tokens.size() && tokens.get(index + 1).type() == TokenType.FROM) {
                    continue;
                }
                return skipModuleDeclarationTail(tokens, index);
            }
            if (token.type() == TokenType.SEMICOLON) {
                return index;
            }
            if (isExport && clauseEnd < 0 && index == start + 1
                    && token.type() != TokenType.MUL && token.type() != TokenType.LBRACE) {
                // `export default …` or `export <declaration>`: not identifiable here.
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether a string token is the module specifier of a declaration rather than a module export
     * name.
     * <p>
     * A specifier follows {@code from}, or the {@code import} keyword itself in
     * {@code import 'spec'}. Treating any top-level string as the specifier broke
     * {@code export * as "All" from './m.js'}, whose export name is also a top-level string
     * (ES2022 arbitrary module namespace names).
     *
     * @param tokens the token list
     * @param start  the index of the {@code import} or {@code export} token
     * @param index  the index of the string token
     * @return true when the string is the module specifier
     */
    static boolean isModuleSpecifierPosition(List<Token> tokens, int start, int index) {
        if (index == start + 1) {
            return tokens.get(start).type() == TokenType.IMPORT;
        }
        return tokens.get(index - 1).type() == TokenType.FROM;
    }

    /**
     * Whether the given text is a well-formed ECMAScript IdentifierName.
     *
     * @param name the candidate identifier
     * @return true when every code point is a legal identifier character
     */
    static boolean isValidIdentifierName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        int firstCodePoint = name.codePointAt(0);
        if (!UnicodeData.isIdentifierStart(firstCodePoint)) {
            return false;
        }
        for (int offset = Character.charCount(firstCodePoint); offset < name.length(); ) {
            int codePoint = name.codePointAt(offset);
            if (!UnicodeData.isIdentifierPart(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    static int parseHex(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            if (value > (Integer.MAX_VALUE - digit) / 16) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    /**
     * Consume an optional {@code with}/{@code assert} attributes clause and an optional semicolon.
     *
     * @param tokens the token list
     * @param index  the index of the last token consumed so far
     * @return the index of the declaration's last token
     */
    static int skipModuleDeclarationTail(List<Token> tokens, int index) {
        int end = index;
        int next = end + 1;
        if (next < tokens.size()
                && ("with".equals(tokens.get(next).value()) || "assert".equals(tokens.get(next).value()))
                && next + 1 < tokens.size()
                && tokens.get(next + 1).type() == TokenType.LBRACE) {
            int depth = 0;
            for (int scan = next + 1; scan < tokens.size(); scan++) {
                if (tokens.get(scan).type() == TokenType.LBRACE) {
                    depth++;
                } else if (tokens.get(scan).type() == TokenType.RBRACE) {
                    depth--;
                    if (depth == 0) {
                        end = scan;
                        break;
                    }
                }
            }
            next = end + 1;
        }
        if (next < tokens.size() && tokens.get(next).type() == TokenType.SEMICOLON) {
            end = next;
        }
        return end;
    }

    /**
     * Tokenise module source into a list.
     *
     * @param sourceCode the source
     * @return the tokens, without the terminating {@code EOF}
     */
    static List<Token> tokenizeModuleSource(String sourceCode) {
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token token = lexer.nextToken();
            if (token == null || token.type() == TokenType.EOF) {
                return tokens;
            }
            tokens.add(token);
        }
    }

    void appendDynamicImportDefaultExportNameFixup(
            StringBuilder transformedSourceBuilder,
            String rawLocalName,
            String moduleSpecifier) {
        String localName = requireGeneratedIdentifier(
                rawLocalName, "default export binding '" + rawLocalName + "'", moduleSpecifier);
        transformedSourceBuilder.append("if (typeof ")
                .append(localName)
                .append(" === \"function\" && (!Object.prototype.hasOwnProperty.call(")
                .append(localName)
                .append(", \"name\") || ")
                .append(localName)
                .append(".name === \"\" || ")
                .append(localName)
                .append(".name === \"")
                .append(localName)
                .append("\")) {\n")
                .append("  Object.defineProperty(")
                .append(localName)
                .append(", \"name\", { value: \"default\", configurable: true });\n")
                .append("}\n");
    }

    void appendDynamicImportExportAssignments(
            StringBuilder transformedSourceBuilder,
            String generatedNamePrefix,
            String exportBindingName,
            List<JSDynamicImportModule.LocalExportBinding> localExportBindings,
            Set<String> importedBindingNames,
            String moduleSpecifier) {
        String moduleExportsName = generatedNamePrefix + "ModuleExports";
        transformedSourceBuilder.append("const ").append(moduleExportsName).append(" = ")
                .append(exportBindingName)
                .append(";\n");
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            String escapedName = escapeJavaScriptString(localExportBinding.exportedName());
            // The exported name lands in string position, so escaping is enough. The local name
            // lands in identifier position, where only a real identifier is safe.
            String localName = requireGeneratedIdentifier(
                    localExportBinding.localName(),
                    "local binding of export '" + localExportBinding.exportedName() + "'",
                    moduleSpecifier);
            // Exports are always live bindings, including re-exported imports.
            transformedSourceBuilder.append("Object.defineProperty(").append(moduleExportsName).append(", \"")
                    .append(escapedName)
                    .append("\", { enumerable: true, configurable: false, get() { return ")
                    .append(localName)
                    .append("; } });\n");
        }
    }

    /**
     * Append the character a {@code \\uHHHH} or {@code \\u\{H+\}} escape denotes.
     *
     * @param text           the literal body
     * @param uIndex         the index of the {@code u}
     * @param decodedBuilder collects the decoded text
     * @return the index of the escape's last character
     */
    int appendModuleUnicodeEscape(String text, int uIndex, StringBuilder decodedBuilder) {
        if (uIndex + 1 < text.length() && text.charAt(uIndex + 1) == '{') {
            int braceEnd = text.indexOf('}', uIndex + 2);
            int codePoint = braceEnd < 0 ? -1 : parseHex(text.substring(uIndex + 2, braceEnd));
            if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
                decodedBuilder.append('u');
                return uIndex;
            }
            decodedBuilder.appendCodePoint(codePoint);
            return braceEnd;
        }
        int codeUnit = uIndex + 4 < text.length()
                ? parseHex(text.substring(uIndex + 1, uIndex + 5))
                : -1;
        if (codeUnit < 0) {
            decodedBuilder.append('u');
            return uIndex;
        }
        decodedBuilder.append((char) codeUnit);
        return uIndex + 4;
    }

    void collectImportBindings(
            String importLine,
            Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        Matcher namespaceMatcher = MODULE_NAMESPACE_IMPORT_PATTERN.matcher(importLine);
        if (namespaceMatcher.find()) {
            boolean deferredImport = "defer".equals(namespaceMatcher.group(1));
            String localName = namespaceMatcher.group(2);
            String sourceSpecifier = decodeModuleStringLiteralValue(namespaceMatcher.group(4));
            registerImportedBinding(
                    localName,
                    sourceSpecifier,
                    MODULE_NAMESPACE_EXPORT_NAME,
                    deferredImport,
                    bindingNames,
                    importedBindings);
            return;
        }
        Matcher bindingMatcher = MODULE_BINDING_IMPORT_PATTERN.matcher(importLine);
        if (!bindingMatcher.find()) {
            return;
        }
        String clause = bindingMatcher.group(1).trim();
        String sourceSpecifier = decodeModuleStringLiteralValue(bindingMatcher.group(3));
        if (clause.startsWith("*") || clause.startsWith("defer *")) {
            return;
        }
        if (clause.startsWith("{")) {
            collectNamedImportBindings(clause, sourceSpecifier, bindingNames, importedBindings);
            return;
        }
        int commaIndex = clause.indexOf(',');
        if (commaIndex < 0) {
            registerImportedBinding(clause, sourceSpecifier, "default", false, bindingNames, importedBindings);
            return;
        }
        String defaultName = clause.substring(0, commaIndex).trim();
        if (!defaultName.isEmpty()) {
            registerImportedBinding(defaultName, sourceSpecifier, "default", false, bindingNames, importedBindings);
        }
        String remainder = clause.substring(commaIndex + 1).trim();
        if (remainder.startsWith("{")) {
            collectNamedImportBindings(remainder, sourceSpecifier, bindingNames, importedBindings);
        } else if (remainder.startsWith("*")) {
            String namespaceBinding = remainder.replaceFirst("^\\*\\s*as\\s+", "").trim();
            if (!namespaceBinding.isEmpty()) {
                registerImportedBinding(
                        namespaceBinding,
                        sourceSpecifier,
                        MODULE_NAMESPACE_EXPORT_NAME,
                        false,
                        bindingNames,
                        importedBindings);
            }
        }
    }

    void collectNamedImportBindings(
            String namedClause,
            String sourceSpecifier,
            Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        String clause = namedClause.trim();
        if (!clause.startsWith("{") || !clause.endsWith("}")) {
            return;
        }
        String specifiersText = clause.substring(1, clause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            String localName;
            int asIndex = findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
                localName = specifier.substring(asIndex + 2).trim();
            } else {
                importedName = parseModuleExportNameValue(specifier);
                localName = importedName;
            }
            registerImportedBinding(localName, sourceSpecifier, importedName, false, bindingNames, importedBindings);
        }
    }

    String createModuleExportBindingName(String generatedNamePrefix, String resolvedSpecifier) {
        // Unsigned, because Math.abs(Integer.MIN_VALUE) is still Integer.MIN_VALUE: a specifier
        // whose hash landed there produced an identifier containing a minus sign, and a valid
        // module then failed to evaluate because of what its file was called.
        return generatedNamePrefix + "DynamicImportExports$"
                + Integer.toUnsignedString(resolvedSpecifier.hashCode()) + "$"
                + context.moduleLoader().moduleCacheSize();
    }

    String decodeIdentifierEscapes(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder decodedTextBuilder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch != '\\' || index + 1 >= text.length() || text.charAt(index + 1) != 'u') {
                decodedTextBuilder.append(ch);
                continue;
            }
            int escapeStart = index;
            index += 2;
            if (index < text.length() && text.charAt(index) == '{') {
                int braceEnd = text.indexOf('}', index + 1);
                if (braceEnd < 0) {
                    decodedTextBuilder.append(text, escapeStart, index + 1);
                    index = escapeStart;
                    continue;
                }
                String codePointText = text.substring(index + 1, braceEnd);
                int codePoint = parseHex(codePointText);
                if (codePoint >= 0) {
                    decodedTextBuilder.appendCodePoint(codePoint);
                    index = braceEnd;
                } else {
                    decodedTextBuilder.append(text, escapeStart, braceEnd + 1);
                    index = braceEnd;
                }
                continue;
            }
            if (index + 3 >= text.length()) {
                decodedTextBuilder.append(text, escapeStart, text.length());
                break;
            }
            String hexText = text.substring(index, index + 4);
            int codePoint = parseHex(hexText);
            if (codePoint >= 0) {
                decodedTextBuilder.append((char) codePoint);
                index += 3;
            } else {
                decodedTextBuilder.append(text, escapeStart, index + 4);
                index += 3;
            }
        }
        return decodedTextBuilder.toString();
    }

    /**
     * The {@code StringValue} of a {@code StringLiteral} body — the text between the delimiters,
     * with every escape sequence applied.
     * <p>
     * A module specifier and an arbitrary module export name are {@code StringLiteral}s, and a
     * {@code StringLiteral}'s meaning is its {@code StringValue}, not its spelling.
     * {@code './d\\u0065p.mjs'} names {@code ./dep.mjs} and {@code "a\\u002db"} is the export name
     * {@code a-b} — the lexer knows that, and the token-driven link pass therefore already agreed
     * with it. The evaluation path pulled the same literals out of raw source with
     * {@code substring} and kept the backslashes, so the two stages disagreed about module identity
     * and about export-name identity: the link check passed and evaluation then failed to find a
     * module or a name that was there all along, in one case after a dependency had already run.
     * <p>
     * Octal escapes are deliberately not decoded. They are a {@code SyntaxError} in a module, the
     * source has already been compiled once before this runs, and inventing a value for text the
     * grammar rejects would be worse than leaving it alone.
     *
     * @param literalBody the literal's text without its delimiters
     * @return the decoded value
     */
    String decodeModuleStringLiteralValue(String literalBody) {
        if (literalBody == null || literalBody.indexOf('\\') < 0) {
            return literalBody;
        }
        StringBuilder decodedBuilder = new StringBuilder(literalBody.length());
        for (int index = 0; index < literalBody.length(); index++) {
            char ch = literalBody.charAt(index);
            if (ch != '\\' || index + 1 >= literalBody.length()) {
                decodedBuilder.append(ch);
                continue;
            }
            char escaped = literalBody.charAt(++index);
            switch (escaped) {
                case 'b' -> decodedBuilder.append('\b');
                case 'f' -> decodedBuilder.append('\f');
                case 'n' -> decodedBuilder.append('\n');
                case 'r' -> decodedBuilder.append('\r');
                case 't' -> decodedBuilder.append('\t');
                case 'v' -> decodedBuilder.append('\u000B');
                case 'x' -> {
                    int value = index + 2 < literalBody.length()
                            ? parseHex(literalBody.substring(index + 1, index + 3))
                            : -1;
                    if (value < 0) {
                        decodedBuilder.append(escaped);
                    } else {
                        decodedBuilder.append((char) value);
                        index += 2;
                    }
                }
                case 'u' -> index = appendModuleUnicodeEscape(literalBody, index, decodedBuilder);
                case '0' -> {
                    if (index + 1 < literalBody.length()
                            && Character.isDigit(literalBody.charAt(index + 1))) {
                        // A legacy octal escape: left as written, per the note above.
                        decodedBuilder.append('\\').append(escaped);
                    } else {
                        decodedBuilder.append('\0');
                    }
                }
                // A LineContinuation contributes nothing, and CRLF is one line terminator.
                case '\r' -> {
                    if (index + 1 < literalBody.length() && literalBody.charAt(index + 1) == '\n') {
                        index++;
                    }
                }
                case '\n', '\u2028', '\u2029' -> {
                }
                // \' \" \\ and every other IdentityEscape stand for the character itself.
                default -> decodedBuilder.append(escaped);
            }
        }
        return decodedBuilder.toString();
    }

    /**
     * The offset just past a default-export expression that begins at {@code start}.
     *
     * @param sourceCode the module source
     * @param start      the offset of the expression's first token
     * @param boundary   the offset of the next top-level declaration, or the end of the source
     * @return the end offset, or -1 when the text does not parse
     */
    int defaultExportExpressionEnd(String sourceCode, int start, int boundary) {
        String expressionText = sourceCode.substring(start, Math.max(start, boundary));
        List<Statement> statements;
        try {
            // The assignment target puts what follows in expression position — without it a leading
            // `{` opens a block rather than an object literal — and costs a known two characters.
            statements = new Parser(new Lexer("x=" + expressionText), true).parse().getBody();
        } catch (RuntimeException notParsable) {
            context.clearPendingException();
            return -1;
        }
        int end = statements.size() > 1 && statements.get(1) != null
                ? statements.get(1).getLocation().offset() - "x=".length()
                : expressionText.length();
        end = Math.max(0, Math.min(end, expressionText.length()));
        // Walk back over what is not the expression: the terminating semicolon, whitespace, and
        // comments, which masking turns into whitespace. Trimming only whitespace drew a trailing
        // `// comment` into the expression and produced `let X = (0, e;\n// comment);`.
        String maskedExpressionText = maskModuleComments(expressionText);
        while (end > 0 && Character.isWhitespace(maskedExpressionText.charAt(end - 1))) {
            end--;
        }
        if (end > 0 && maskedExpressionText.charAt(end - 1) == ';') {
            end--;
            while (end > 0 && Character.isWhitespace(maskedExpressionText.charAt(end - 1))) {
                end--;
            }
        }
        return end == 0 ? -1 : start + end;
    }

    /**
     * Where each {@code export default <expression>} in a module really ends, decided by the
     * parser.
     * <p>
     * The line-oriented transformer used to answer this by counting brackets, braces and
     * parentheses and taking lines while they were unbalanced. Balanced delimiters do not mark the
     * end of an AssignmentExpression: {@code export default 1 +} is balanced and incomplete, so the
     * transform emitted {@code let X = (0, 1 +);} and a valid module failed to parse at a position
     * in generated text. It also counted delimiters inside regular-expression literals, which it
     * had no way to recognise, so {@code export default /\(/;} swallowed the statement after it.
     * <p>
     * Where an expression ends is a grammar question — operators, conditionals, member and call
     * continuations, template substitutions, comments and automatic semicolon insertion all bear on
     * it — and the parser is the only thing here that knows the grammar. The expression is parsed
     * in expression position (an assignment target is prefixed, so an opening brace is an object
     * literal rather than a block) and bounded at the next top-level declaration, so nothing after
     * it can be drawn in.
     *
     * @param sourceCode the normalised module source
     * @return the extents, by the zero-based line index of the {@code export} keyword; empty when
     * the source cannot be scanned
     */
    Map<Integer, DefaultExportExtent> defaultExportExtents(String sourceCode) {
        ModuleDeclarationScan scan = scanTopLevelModuleDeclarations(sourceCode);
        if (scan == null || !scan.hasExportDeclaration()) {
            return Map.of();
        }
        List<Token> tokens;
        try {
            tokens = tokenizeModuleSource(sourceCode);
        } catch (RuntimeException notTokenisable) {
            context.clearPendingException();
            return Map.of();
        }
        Map<Integer, DefaultExportExtent> extents = new HashMap<>();
        int depth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            switch (token.type()) {
                case LBRACE, LPAREN, LBRACKET -> depth++;
                case RBRACE, RPAREN, RBRACKET -> depth = Math.max(0, depth - 1);
                default -> {
                }
            }
            if (depth != 0
                    || token.type() != TokenType.EXPORT
                    || index + 2 >= tokens.size()
                    || tokens.get(index + 1).type() != TokenType.DEFAULT) {
                continue;
            }
            Token firstExpressionToken = tokens.get(index + 2);
            if (firstExpressionToken.type() == TokenType.FUNCTION
                    || firstExpressionToken.type() == TokenType.CLASS
                    || (firstExpressionToken.type() == TokenType.ASYNC
                    && index + 3 < tokens.size()
                    && tokens.get(index + 3).type() == TokenType.FUNCTION)) {
                // A default-exported declaration ends at its body, which the transformer finds by
                // matching braces — that much a delimiter count really can do.
                continue;
            }
            int expressionStart = firstExpressionToken.offset();
            int boundary = sourceCode.length();
            for (int declarationOffset : scan.declarationOffsets()) {
                if (declarationOffset > expressionStart) {
                    boundary = declarationOffset;
                    break;
                }
            }
            int expressionEnd = defaultExportExpressionEnd(sourceCode, expressionStart, boundary);
            if (expressionEnd < 0) {
                continue;
            }
            int lineEnd = sourceCode.indexOf('\n', expressionEnd);
            if (lineEnd < 0) {
                lineEnd = sourceCode.length();
            }
            String trailingText = sourceCode.substring(expressionEnd, lineEnd).strip();
            if (trailingText.startsWith(";")) {
                trailingText = trailingText.substring(1).strip();
            }
            extents.put(
                    lineIndexOfOffset(sourceCode, token.offset()),
                    new DefaultExportExtent(
                            sourceCode.substring(expressionStart, expressionEnd),
                            lineIndexOfOffset(sourceCode, expressionEnd),
                            trailingText));
        }
        return extents;
    }

    /**
     * Escape a value for interpolation into a double-quoted JavaScript string literal in generated
     * module source.
     * <p>
     * Escaping only {@code \} and {@code "} is not enough: a line terminator, a control character
     * or U+2028/U+2029 inside the value terminates the literal and turns the remainder of the value
     * into source text.
     *
     * @param text the raw value
     * @return the value with every character that is unsafe inside a string literal escaped
     */
    String escapeJavaScriptString(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            char character = text.charAt(characterIndex);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\'' -> escaped.append("\\'");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    // Control characters, U+2028 LINE SEPARATOR and U+2029 PARAGRAPH SEPARATOR are
                    // all line terminators or otherwise unsafe inside a JavaScript string literal.
                    if (character < 0x20 || character == 0x2028 || character == 0x2029) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    void extractDestructuringNames(String innerText, List<String> names) {
        // Split on top-level commas and extract bound names
        String trimmed = innerText.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        int depth = 0;
        int start = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                extractSingleDestructuringName(trimmed.substring(start, i).trim(), names);
                start = i + 1;
            }
        }
        extractSingleDestructuringName(trimmed.substring(start).trim(), names);
    }

    /**
     * The name a {@code function} or {@code class} declaration binds, decoded.
     * <p>
     * Decoded because the declared binding is the decoded name: {@code export function café()}
     * declares {@code café}, and the generated export bookkeeping has to name it the way the rest of
     * the module names it.
     *
     * @param exportClause the declaration text, with {@code export} already removed
     * @return the bound name, or null when the declaration is anonymous
     */
    String extractExportedFunctionOrClassName(String exportClause) {
        Matcher functionMatcher = DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN.matcher(exportClause);
        if (functionMatcher.find()) {
            return decodeIdentifierEscapes(functionMatcher.group(1));
        }
        Matcher classMatcher = DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN.matcher(exportClause);
        if (classMatcher.find()) {
            String candidateName = decodeIdentifierEscapes(classMatcher.group(1));
            if (JSKeyword.EXTENDS.equals(candidateName)) {
                return null;
            }
            return candidateName;
        }
        return null;
    }

    Map<String, String> extractImportAttributes(String importStatement) {
        Matcher withMatcher = MODULE_WITH_CLAUSE_PATTERN.matcher(importStatement);
        if (!withMatcher.find()) {
            return null;
        }
        String withBody = withMatcher.group(1).trim();
        if (withBody.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> attributes = new HashMap<>();
        String[] pairs = withBody.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();
            // Remove surrounding quotes from value
            if (value.length() >= 2
                    && ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            attributes.put(key, value);
        }
        return attributes;
    }

    List<String> extractSimpleDeclarationNames(String declarationSource) {
        String declarationText = declarationSource.trim();
        int firstSpaceIndex = declarationText.indexOf(' ');
        if (firstSpaceIndex < 0 || firstSpaceIndex >= declarationText.length() - 1) {
            return List.of();
        }
        String declaratorsText = declarationText.substring(firstSpaceIndex + 1).trim();
        if (declaratorsText.endsWith(";")) {
            declaratorsText = declaratorsText.substring(0, declaratorsText.length() - 1).trim();
        }
        if (declaratorsText.isEmpty()) {
            return List.of();
        }
        // Split on commas at the top level only (not inside parens, brackets, braces, or strings)
        List<String> declarators = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < declaratorsText.length(); i++) {
            char ch = declaratorsText.charAt(i);
            if (inString) {
                if (ch == stringChar && (i == 0 || declaratorsText.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                inString = true;
                stringChar = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                declarators.add(declaratorsText.substring(start, i));
                start = i + 1;
            }
        }
        declarators.add(declaratorsText.substring(start));
        List<String> declarationNames = new ArrayList<>(declarators.size());
        for (String declarator : declarators) {
            String declaratorText = declarator.trim();
            int assignmentIndex = findTopLevelAssignment(declaratorText);
            if (assignmentIndex >= 0) {
                declaratorText = declaratorText.substring(0, assignmentIndex).trim();
            }
            if (!declaratorText.isEmpty()) {
                if (declaratorText.startsWith("{") && declaratorText.endsWith("}")) {
                    // Object destructuring pattern: { a, b, c: d } → extract bound names
                    extractDestructuringNames(declaratorText.substring(1, declaratorText.length() - 1), declarationNames);
                } else if (declaratorText.startsWith("[") && declaratorText.endsWith("]")) {
                    // Array destructuring pattern: [a, b, c] → extract bound names
                    extractDestructuringNames(declaratorText.substring(1, declaratorText.length() - 1), declarationNames);
                } else {
                    // The binding is called by its decoded name: `export const \\u0078 = 1` declares
                    // `x`, and the generated export bookkeeping has to name it that way too.
                    declarationNames.add(decodeIdentifierEscapes(declaratorText));
                }
            }
        }
        return declarationNames;
    }

    void extractSingleDestructuringName(String element, List<String> names) {
        if (element.isEmpty() || element.equals("...")) {
            return;
        }
        // Handle rest element: ...name
        if (element.startsWith("...")) {
            element = element.substring(3).trim();
        }
        // Handle rename: key: value
        int colonIndex = element.indexOf(':');
        if (colonIndex >= 0) {
            element = element.substring(colonIndex + 1).trim();
        }
        // Handle default value: name = defaultVal
        int eqIndex = findTopLevelAssignment(element);
        if (eqIndex >= 0) {
            element = element.substring(0, eqIndex).trim();
        }
        // Check for nested destructuring
        if (element.startsWith("{") && element.endsWith("}")) {
            extractDestructuringNames(element.substring(1, element.length() - 1), names);
        } else if (element.startsWith("[") && element.endsWith("]")) {
            extractDestructuringNames(element.substring(1, element.length() - 1), names);
        } else if (!element.isEmpty()) {
            names.add(decodeIdentifierEscapes(element));
        }
    }

    /**
     * Find the end of a function/class declaration body in the given text.
     * Scans for the first '{' and its matching '}', returning the index
     * just after the closing brace. Returns -1 if not found.
     */
    int findEndOfDeclarationBody(String text) {
        String trimmedText = text.stripLeading();
        if (trimmedText.startsWith(JSKeyword.CLASS)
                && (trimmedText.length() == JSKeyword.CLASS.length()
                || !Character.isJavaIdentifierPart(trimmedText.charAt(JSKeyword.CLASS.length())))) {
            int classBodyOpenBraceIndex = findLikelyClassBodyOpenBrace(text);
            if (classBodyOpenBraceIndex < 0) {
                return -1;
            }
            int classBodyCloseBraceIndex = findMatchingClosingBrace(text, classBodyOpenBraceIndex);
            if (classBodyCloseBraceIndex < 0) {
                return -1;
            }
            return classBodyCloseBraceIndex + 1;
        }
        int braceDepth = 0;
        boolean foundOpen = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (ch == '/' && i + 1 < text.length()) {
                if (text.charAt(i + 1) == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (text.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            if (inSingleQuote) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inTemplate) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '`') {
                    inTemplate = false;
                }
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = true;
            } else if (ch == '"') {
                inDoubleQuote = true;
            } else if (ch == '`') {
                inTemplate = true;
            } else if (ch == '{') {
                foundOpen = true;
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
                if (foundOpen && braceDepth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    int findLikelyClassBodyOpenBrace(String text) {
        int openBraceIndex = text.indexOf('{');
        while (openBraceIndex >= 0) {
            int closeBraceIndex = findMatchingClosingBrace(text, openBraceIndex);
            if (closeBraceIndex < 0) {
                return -1;
            }
            int nextTokenIndex = skipWhitespace(text, closeBraceIndex + 1);
            if (nextTokenIndex >= text.length()) {
                return openBraceIndex;
            }
            if (!isExpressionContinuationCharacter(text.charAt(nextTokenIndex))) {
                return openBraceIndex;
            }
            openBraceIndex = text.indexOf('{', openBraceIndex + 1);
        }
        return -1;
    }

    /**
     * Find the matching '}' for the '{' at the given position, skipping quoted strings.
     */
    int findMatchingCloseBrace(String text, int openBraceIndex) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = openBraceIndex + 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '}' && !inSingleQuote && !inDoubleQuote) {
                return i;
            }
        }
        return -1;
    }

    int findMatchingClosingBrace(String text, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= text.length() || text.charAt(openBraceIndex) != '{') {
            return -1;
        }
        int braceDepth = 1;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int index = openBraceIndex + 1; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }
            if (ch == '/' && index + 1 < text.length()) {
                if (text.charAt(index + 1) == '/') {
                    inLineComment = true;
                    index++;
                    continue;
                }
                if (text.charAt(index + 1) == '*') {
                    inBlockComment = true;
                    index++;
                    continue;
                }
            }
            if (inSingleQuote) {
                if (ch == '\\') {
                    index++;
                } else if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (ch == '\\') {
                    index++;
                } else if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inTemplate) {
                if (ch == '\\') {
                    index++;
                } else if (ch == '`') {
                    inTemplate = false;
                }
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (ch == '`') {
                inTemplate = true;
                continue;
            }
            if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * Find the index of " as " keyword at the top level (not inside quotes).
     * Returns the index of 'a' in "as", or -1 if not found.
     */
    int findTopLevelAs(String text) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < text.length() - 3; i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote
                    && Character.isWhitespace(ch)
                    && text.charAt(i + 1) == 'a'
                    && text.charAt(i + 2) == 's'
                    && i + 3 < text.length()
                    && Character.isWhitespace(text.charAt(i + 3))) {
                return i + 1;
            }
        }
        return -1;
    }

    int findTopLevelAssignment(String text) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (ch == stringChar && (i == 0 || text.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                inString = true;
                stringChar = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == '=' && depth == 0 && i + 1 < text.length() && text.charAt(i + 1) != '=') {
                return i;
            }
        }
        return -1;
    }

    /**
     * A prefix for this module's generated bindings that the module's own source does not use.
     * <p>
     * The transformer declares its bookkeeping bindings in the module's own scope, where they are
     * ordinary, writable, observable names. {@code __qjs4jDefaultExport$0} is a legal identifier, so
     * a module that happened to declare it had its value replaced by the transformer's — silently,
     * and only for that one name. Choosing a prefix the source does not contain keeps the generated
     * names out of the author's way whatever the author writes.
     * <p>
     * Whether the source uses the prefix is asked of the identifiers the lexer produces, not of the
     * source text. An identifier may be written with Unicode escapes — {@code __qjs4jDefault
     * Export$0} is the identifier {@code __qjs4jDefaultExport$0} — and a substring search does not
     * see through those, so a module that legally declared the name under an escape was rejected
     * with a syntax error in generated source. The decoded name is the one that has to be avoided,
     * because the decoded name is what the binding is called.
     * <p>
     * This is containment, not a fix, and it is not complete containment. The generated bindings
     * are still declared in the module's own scope and are still observable from it: direct eval
     * can build any name at run time — {@code eval('typeof __q' + 'js4jModuleExports')} answers
     * {@code "object"} where it must answer {@code "undefined"} — and no choice of prefix can hide
     * a binding from a name the source never spells. Only real module environments, which do not
     * put bookkeeping in the author's scope at all, remove them.
     *
     * @param sourceCode the module source the generated bindings will sit beside
     * @return a prefix no identifier in the source begins with
     */
    String generatedModuleNamePrefix(String sourceCode) {
        String prefix = "__qjs4j";
        if (sourceCode == null || (!sourceCode.contains("qjs4j") && !sourceCode.contains("\\u"))) {
            // Neither the text nor an escape in it can decode to a name starting with the prefix.
            return prefix;
        }
        // Null when the source does not tokenise, in which case the text search is all there is —
        // and the compiler is about to reject the source anyway.
        Set<String> identifierNames = moduleIdentifierNames(sourceCode);
        for (int salt = -1; salt < Integer.MAX_VALUE; salt++) {
            String candidate = salt < 0 ? prefix : prefix + salt + "_";
            boolean taken = identifierNames == null
                    ? sourceCode.contains(candidate)
                    : identifierNames.stream().anyMatch(name -> name.startsWith(candidate));
            if (!taken) {
                return candidate;
            }
        }
        return prefix;
    }

    boolean hasModuleExportSyntax(String code) {
        ModuleDeclarationScan scan = scanTopLevelModuleDeclarations(code);
        return scan == null
                ? MODULE_EXPORT_SYNTAX_PATTERN.matcher(maskModuleComments(code)).find()
                : scan.hasExportDeclaration();
    }

    boolean hasModuleStaticImportSyntax(String code) {
        ModuleDeclarationScan scan = scanTopLevelModuleDeclarations(code);
        return scan == null
                ? MODULE_STATIC_IMPORT_SYNTAX_PATTERN.matcher(maskModuleComments(code)).find()
                : scan.hasImportDeclaration();
    }

    boolean hasModuleTopLevelAwaitSyntax(String code) {
        return MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(maskModuleComments(code)).find();
    }

    /**
     * Check if an import clause contains named bindings other than 'default'.
     * E.g., {@code {name}} returns true, {@code {default as x}} returns false.
     */
    boolean hasNonDefaultNamedBindings(String importClause) {
        int braceStart = importClause.indexOf('{');
        if (braceStart < 0) {
            return false;
        }
        int braceEnd = importClause.indexOf('}', braceStart);
        if (braceEnd < 0) {
            return false;
        }
        String body = importClause.substring(braceStart + 1, braceEnd);
        for (String entry : body.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Get the imported name (before 'as')
            String[] parts = trimmed.split("\\s+as\\s+");
            String importedName = parts[0].trim();
            if (!"default".equals(importedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an expression's brackets, braces and parentheses all close.
     * <p>
     * Comments, strings and templates are masked first, so a delimiter inside one of them does not
     * count. This decides only whether a default-export expression has run past the end of its
     * line; it is not a parser, and cannot be one at this layer.
     *
     * @param expressionText the text scanned so far
     * @return true when nothing is left open
     */
    boolean isBalancedExpressionText(String expressionText) {
        String maskedExpressionText = maskModuleComments(expressionText);
        int depth = 0;
        for (int index = 0; index < maskedExpressionText.length(); index++) {
            switch (maskedExpressionText.charAt(index)) {
                case '(', '[', '{' -> depth++;
                case ')', ']', '}' -> depth--;
                default -> {
                }
            }
        }
        return depth <= 0;
    }

    boolean isCompleteStaticImportStatement(String importStatement) {
        if (importStatement == null || importStatement.isBlank()) {
            return false;
        }
        String normalizedImportStatement = importStatement.strip();
        if (MODULE_NAMESPACE_IMPORT_PATTERN.matcher(normalizedImportStatement).matches()) {
            return true;
        }
        if (MODULE_BINDING_IMPORT_PATTERN.matcher(normalizedImportStatement).matches()) {
            return true;
        }
        return MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(normalizedImportStatement).matches();
    }

    boolean isDynamicImportDefaultDeclarationClause(String defaultClause) {
        return defaultClause.startsWith("function")
                || defaultClause.startsWith("async function")
                || defaultClause.startsWith("class");
    }

    boolean isExpressionContinuationCharacter(char ch) {
        return ch == ')' || ch == ']' || ch == '}'
                || ch == ',' || ch == '.' || ch == ':'
                || ch == '?' || ch == '+'
                || ch == '-' || ch == '*'
                || ch == '/' || ch == '%'
                || ch == '<' || ch == '>'
                || ch == '=' || ch == '&'
                || ch == '|' || ch == '^';
    }

    boolean isSelfImportBinding(ImportBinding importBinding, String moduleSpecifier) {
        if (importBinding == null
                || importBinding.sourceSpecifier() == null
                || importBinding.sourceSpecifier().isEmpty()) {
            return false;
        }
        try {
            String resolvedImportSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(
                    importBinding.sourceSpecifier(),
                    moduleSpecifier,
                    importBinding.sourceSpecifier());
            Path resolvedImportPath = Path.of(resolvedImportSpecifier).normalize().toAbsolutePath();
            Path modulePath = Path.of(moduleSpecifier).normalize().toAbsolutePath();
            String resolvedImportPathString = resolvedImportPath.toString();
            String modulePathString = modulePath.toString();
            if (resolvedImportPathString.equals(modulePathString)) {
                return true;
            }
            return resolvedImportPathString.equalsIgnoreCase(modulePathString);
        } catch (JSException ignored) {
            context.clearPendingException();
            return false;
        } catch (Exception ignored) {
            context.clearPendingException();
            return false;
        }
    }

    boolean isStaticImportLine(String trimmedLine) {
        if (trimmedLine == null || !trimmedLine.startsWith("import")) {
            return false;
        }
        if (trimmedLine.startsWith("import(") || trimmedLine.startsWith("import.")) {
            return false;
        }
        if (trimmedLine.length() == "import".length()) {
            return false;
        }
        char nextChar = trimmedLine.charAt("import".length());
        return !Character.isLetterOrDigit(nextChar) && nextChar != '_' && nextChar != '$';
    }

    /**
     * The zero-based index of the line an offset falls on.
     *
     * @param sourceCode the source
     * @param offset     the offset
     * @return the line index
     */
    int lineIndexOfOffset(String sourceCode, int offset) {
        int lineIndex = 0;
        for (int index = 0; index < offset && index < sourceCode.length(); index++) {
            if (sourceCode.charAt(index) == '\n') {
                lineIndex++;
            }
        }
        return lineIndex;
    }

    String maskModuleComments(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return "";
        }
        StringBuilder maskedBuilder = new StringBuilder(sourceCode.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplateLiteral = false;
        for (int index = 0; index < sourceCode.length(); index++) {
            char currentChar = sourceCode.charAt(index);
            char nextChar = index + 1 < sourceCode.length() ? sourceCode.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (currentChar == '\n' || currentChar == '\r') {
                    inLineComment = false;
                    maskedBuilder.append(currentChar);
                } else {
                    maskedBuilder.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (currentChar == '*' && nextChar == '/') {
                    maskedBuilder.append(' ');
                    maskedBuilder.append(' ');
                    index++;
                    inBlockComment = false;
                    continue;
                }
                if (currentChar == '\n' || currentChar == '\r') {
                    maskedBuilder.append(currentChar);
                } else {
                    maskedBuilder.append(' ');
                }
                continue;
            }
            if (inSingleQuote) {
                maskedBuilder.append(currentChar);
                if (currentChar == '\\' && index + 1 < sourceCode.length()) {
                    index++;
                    maskedBuilder.append(sourceCode.charAt(index));
                } else if (currentChar == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                maskedBuilder.append(currentChar);
                if (currentChar == '\\' && index + 1 < sourceCode.length()) {
                    index++;
                    maskedBuilder.append(sourceCode.charAt(index));
                } else if (currentChar == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inTemplateLiteral) {
                maskedBuilder.append(currentChar);
                if (currentChar == '\\' && index + 1 < sourceCode.length()) {
                    index++;
                    maskedBuilder.append(sourceCode.charAt(index));
                } else if (currentChar == '`') {
                    inTemplateLiteral = false;
                }
                continue;
            }

            if (currentChar == '/' && nextChar == '/') {
                maskedBuilder.append(' ');
                maskedBuilder.append(' ');
                index++;
                inLineComment = true;
                continue;
            }
            if (currentChar == '/' && nextChar == '*') {
                maskedBuilder.append(' ');
                maskedBuilder.append(' ');
                index++;
                inBlockComment = true;
                continue;
            }
            if (currentChar == '\'') {
                inSingleQuote = true;
            } else if (currentChar == '"') {
                inDoubleQuote = true;
            } else if (currentChar == '`') {
                inTemplateLiteral = true;
            }
            maskedBuilder.append(currentChar);
        }
        return maskedBuilder.toString();
    }

    /**
     * The zero-based indices of the lines that begin a top-level module declaration.
     * <p>
     * The line loop in {@link #parseDynamicImportModuleSource(JSDynamicImportModule)} used to
     * decide this by string-matching {@code import }/{@code export } at the start of a masked line,
     * and {@link #maskModuleComments(String)} blanks comments but leaves string and template
     * contents verbatim — so a multi-line template containing a line that reads
     * {@code export const fake = 1;} produced an export. Membership in this set is decided by the
     * lexer instead, so text that merely looks like a declaration is not one.
     *
     * @param sourceCode module source that has already been normalised
     * @return the line indices, or {@code null} when the source cannot be tokenised
     */
    Set<Integer> moduleDeclarationLineIndices(String sourceCode) {
        ModuleDeclarationScan scan = scanTopLevelModuleDeclarations(sourceCode);
        if (scan == null) {
            return null;
        }
        Set<Integer> lineIndices = new HashSet<>();
        int lineIndex = 0;
        int offset = 0;
        for (int declarationOffset : scan.declarationOffsets()) {
            while (offset < declarationOffset && offset < sourceCode.length()) {
                if (sourceCode.charAt(offset) == '\n') {
                    lineIndex++;
                }
                offset++;
            }
            lineIndices.add(lineIndex);
        }
        return lineIndices;
    }

    /**
     * Every identifier name a module's source contains, decoded.
     *
     * @param sourceCode the module source
     * @return the names, or null when the source does not tokenise
     */
    Set<String> moduleIdentifierNames(String sourceCode) {
        List<Token> tokens;
        try {
            tokens = tokenizeModuleSource(sourceCode);
        } catch (RuntimeException notTokenisable) {
            context.clearPendingException();
            return null;
        }
        Set<String> identifierNames = new HashSet<>();
        for (Token token : tokens) {
            if (token.type() == TokenType.IDENTIFIER) {
                identifierNames.add(token.value());
            }
        }
        return identifierNames;
    }

    /**
     * The span one token occupies in the source it was read from.
     * <p>
     * A {@link Token} records where it starts but not where it ends, and its {@code value} is the
     * decoded text, so it cannot be measured either — {@code as} is one character long as a
     * value and six in the source. The end is therefore taken from the next token and walked back
     * over whitespace, which is exact whenever the two are separated by nothing else.
     *
     * @param tokens     the token list
     * @param index      the index of the token
     * @param sourceCode the source the tokens came from
     * @return the token's location
     */
    SourceLocation moduleTokenLocation(List<Token> tokens, int index, String sourceCode) {
        Token token = tokens.get(index);
        int endOffset = index + 1 < tokens.size() ? tokens.get(index + 1).offset() : sourceCode.length();
        endOffset = Math.min(endOffset, sourceCode.length());
        while (endOffset > token.offset() && Character.isWhitespace(sourceCode.charAt(endOffset - 1))) {
            endOffset--;
        }
        return new SourceLocation(token.line(), token.column(), token.offset(), endOffset);
    }

    /**
     * Rewrite module source so that every top-level {@code import} and {@code export} declaration
     * occupies whole lines.
     * <p>
     * The transformer that follows is line-oriented, and this is what makes that sound for the
     * shapes it could not see before: {@code const t = 1; export const v = 15;} and
     * {@code import { v } from './dep.mjs'; use(v);} are both valid modules whose declarations were
     * invisible or unhoisted purely because of where the line breaks fell. Only line breaks are
     * inserted — no token is moved, rewritten or dropped — so the only observable difference is that
     * source positions after a break shift by a line.
     * <p>
     * A line terminator is not a neutral thing to insert: line terminators are what lets automatic
     * semicolon insertion terminate a statement, so splitting unconditionally handed the parser a
     * semicolon the author never wrote, and the engine accepted modules — {@code export {} let x =
     * 1;} — that a conforming parser must reject. The source is therefore parsed <em>as written</em>
     * before any break is inserted. Breaks only ever go immediately before a top-level declaration
     * or immediately after one whose extent is identifiable, and in source the parser has already
     * accepted both of those are statement boundaries, so the split cannot change what it means.
     *
     * @param sourceCode the module source
     * @return the source with declarations on their own lines
     * @throws JSSyntaxErrorException when the source as written is not a valid module
     */
    String normalizeModuleDeclarationLines(String sourceCode) {
        return normalizeModuleDeclarationLines(sourceCode, true);
    }

    /**
     * Rewrite module source so that every top-level declaration occupies whole lines.
     *
     * @param sourceCode the module source
     * @param validate   whether to compile the source as written first; false only when the caller
     *                   has already done so and would otherwise pay for a second compilation
     * @return the source with declarations on their own lines
     * @throws JSSyntaxErrorException when the source as written is not a valid module
     */
    String normalizeModuleDeclarationLines(String sourceCode, boolean validate) {
        ModuleDeclarationScan scan = scanTopLevelModuleDeclarations(sourceCode);
        if (scan == null || scan.lineBreakOffsets().isEmpty()) {
            return sourceCode;
        }
        if (validate) {
            requireModuleSourceCompiles(sourceCode);
        }
        StringBuilder normalized = new StringBuilder(sourceCode.length() + scan.lineBreakOffsets().size());
        int copiedUpTo = 0;
        for (int breakOffset : scan.lineBreakOffsets()) {
            if (breakOffset <= copiedUpTo || breakOffset > sourceCode.length()) {
                continue;
            }
            normalized.append(sourceCode, copiedUpTo, breakOffset).append('\n');
            copiedUpTo = breakOffset;
        }
        normalized.append(sourceCode, copiedUpTo, sourceCode.length());
        return normalized.toString();
    }

    void parseDynamicImportExportList(
            String exportListText,
            String sourceSpecifier,
            List<JSDynamicImportModule.LocalExportBinding> localExportBindings,
            List<JSDynamicImportModule.ReExportBinding> reExportBindings) {
        // Split on commas at the top level only (not inside quoted strings)
        List<String> exportEntries = splitOnTopLevelCommas(exportListText);
        for (String exportEntry : exportEntries) {
            String exportText = exportEntry.trim();
            if (exportText.isEmpty()) {
                continue;
            }
            String localName;
            String exportedName;
            // Parse "localName as exportedName" with support for string literals
            int asIndex = findTopLevelAs(exportText);
            if (asIndex >= 0) {
                String rawLocal = exportText.substring(0, asIndex).trim();
                String rawExported = exportText.substring(asIndex + 2).trim();
                localName = parseModuleExportNameValue(rawLocal);
                exportedName = parseModuleExportNameValue(rawExported);
            } else {
                localName = parseModuleExportNameValue(exportText);
                exportedName = localName;
            }
            if (sourceSpecifier == null) {
                localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(localName, exportedName));
            } else {
                reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, localName, exportedName, false));
            }
        }
    }

    /**
     * Rewrite ES module source into plain script source that the compiler can evaluate.
     * <p>
     * <strong>This is a line-based textual transformer, not a parser.</strong> It splits the source
     * on {@code \n}, classifies each line, and splices generated JavaScript into the result.
     * <p>
     * <em>Which</em> text it classifies is no longer decided by how the source is formatted.
     * {@link #normalizeModuleDeclarationLines(String)} tokenises the source first and puts every
     * top-level declaration on its own lines, {@link #defaultExportExtents(String)} asks the parser
     * where an {@code export default} expression ends, and both binding names and string literals
     * are read as the values the lexer says they are rather than as the characters that spell
     * them. So a declaration sharing a line with other code, a regular expression literal
     * containing a quote, a Unicode or escaped identifier, and a string export name are all handled
     * — those were once documented here as unsupported, and are covered by
     * {@code JSModuleSourceTransformTest}, {@code JSModuleDefaultExportExtentTest} and
     * {@code JSModuleIdentifierSpellingTest}.
     * <p>
     * What remains wrong is not a scanning problem and cannot be fixed at this layer:
     * <ul>
     * <li>An import is a temporary accessor on the global object, removed when the module body
     * finishes, rather than a binding in a module environment. A closure retained past evaluation
     * cannot read it, and it is not live.</li>
     * <li>The generated bindings — {@code __qjs4jDefaultExport$<n>}, {@code __qjs4jModuleExports}
     * and the namespace binding — are declared in the module's own scope and are reachable from a
     * direct {@code eval} in it. {@link #generatedModuleNamePrefix(String)} keeps them clear of
     * names the author writes, which is containment, not scope.</li>
     * <li>An import attribute naming a module type the engine does not implement is ignored, and
     * the target is loaded as JavaScript.</li>
     * </ul>
     * That list is executable: each entry is a {@code testKnownLimitation*} test in
     * {@code JSModuleKnownLimitationTest} that asserts the wrong answer on purpose, so fixing one
     * makes its test fail. Real module environment records — {@code ImportDeclaration}/{@code Export*}
     * AST nodes the parser keeps instead of discards, and indirect bindings the
     * {@code BytecodeCompiler} can capture — are the fix for all three, and are a dedicated
     * milestone rather than a patch.
     * <p>
     * Loading and linking are separate from evaluation: see {@link #requireModuleGraphLinks(String,
     * String)}. Every value that reaches identifier position in the generated source is validated by
     * {@link #requireGeneratedIdentifier(String, String, String)}, and every value that reaches string
     * position is escaped by {@link #escapeJavaScriptString(String)}, so a name the scanner
     * mis-extracts produces a diagnosable SyntaxError rather than spliced source text.
     *
     * @param moduleRecord the module whose raw source is to be transformed
     */
    void parseDynamicImportModuleSource(JSDynamicImportModule moduleRecord) {
        // Put every top-level declaration on its own lines before the line-oriented scan below
        // runs, so which text it classifies is decided by the lexer rather than by where the
        // author happened to break lines.
        String sourceCode = normalizeModuleDeclarationLines(moduleRecord.rawSource());
        String scanSourceCode = maskModuleComments(sourceCode);
        // Bookkeeping names the module's own source does not use — see generatedModuleNamePrefix.
        String generatedNamePrefix = generatedModuleNamePrefix(sourceCode);
        // Which lines really begin a declaration, decided by the lexer rather than by how a line
        // reads. Null when the source did not tokenise, in which case the string match stands.
        Set<Integer> moduleDeclarationLines = moduleDeclarationLineIndices(sourceCode);
        // Where each `export default <expression>` really ends, decided by the parser rather than
        // by counting delimiters.
        Map<Integer, DefaultExportExtent> defaultExportExtents = defaultExportExtents(sourceCode);
        StringBuilder importPreambleBuilder = new StringBuilder();
        StringBuilder transformedSourceBuilder = new StringBuilder(sourceCode.length() + 128);
        List<JSDynamicImportModule.HoistedFunctionExportBinding> hoistedFunctionExportBindings = new ArrayList<>();
        List<JSDynamicImportModule.LocalExportBinding> localExportBindings = new ArrayList<>();
        List<JSDynamicImportModule.ReExportBinding> reExportBindings = new ArrayList<>();
        Map<String, ImportBinding> importedBindings = new HashMap<>();
        Set<String> importedBindingNames = new HashSet<>();
        boolean hasExportSyntax = false;
        int defaultExportIndex = 0;
        StringBuilder defaultExportNameFixups = new StringBuilder();

        String[] lines = sourceCode.split("\n", -1);
        String[] scanLines = scanSourceCode.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String normalizedLine = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            String scanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
            String parseLine = scanLine.endsWith("\r") ? scanLine.substring(0, scanLine.length() - 1) : scanLine;
            String trimmedLine = parseLine.stripLeading();
            // Extract import lines to be placed before the IIFE wrapper
            boolean isDeclarationLine = moduleDeclarationLines == null
                    || moduleDeclarationLines.contains(lineIndex);
            if (isDeclarationLine && isStaticImportLine(trimmedLine)) {
                StringBuilder importStatementBuilder = new StringBuilder(normalizedLine);
                StringBuilder importStatementScanBuilder = new StringBuilder(parseLine);
                while (!isCompleteStaticImportStatement(importStatementScanBuilder.toString())
                        && lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextLine = lines[lineIndex];
                    String normalizedNextLine = nextLine.endsWith("\r")
                            ? nextLine.substring(0, nextLine.length() - 1)
                            : nextLine;
                    String nextScanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
                    String normalizedNextScanLine = nextScanLine.endsWith("\r")
                            ? nextScanLine.substring(0, nextScanLine.length() - 1)
                            : nextScanLine;
                    importStatementBuilder.append('\n').append(normalizedNextLine);
                    importStatementScanBuilder.append('\n').append(normalizedNextScanLine);
                }
                String importStatementSource = importStatementBuilder.toString();
                String importStatementForScan = importStatementScanBuilder.toString().strip();
                importPreambleBuilder.append(importStatementSource).append('\n');
                collectImportBindings(importStatementForScan, importedBindingNames, importedBindings);
                continue;
            }
            if (!isDeclarationLine
                    || (!trimmedLine.startsWith("export ") && !trimmedLine.startsWith("export{")
                    && !trimmedLine.startsWith("export*") && !trimmedLine.equals("export"))) {
                transformedSourceBuilder.append(normalizedLine).append('\n');
                continue;
            }

            hasExportSyntax = true;
            String exportClause;
            if (trimmedLine.startsWith("export ")) {
                exportClause = trimmedLine.substring("export ".length()).trim();
            } else if (trimmedLine.equals("export") || trimmedLine.startsWith("export") && trimmedLine.substring("export".length()).isBlank()) {
                exportClause = "";
            } else {
                // export{ or export* — no space after 'export'
                exportClause = trimmedLine.substring("export".length()).trim();
            }
            // If the export clause is empty (bare 'export', 'export' with trailing comments/whitespace),
            // look ahead to subsequent lines for the continuation.
            if (exportClause.isEmpty()) {
                StringBuilder exportContinuation = new StringBuilder();
                while (lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextScanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
                    String normalizedNextScanLine = nextScanLine.endsWith("\r")
                            ? nextScanLine.substring(0, nextScanLine.length() - 1) : nextScanLine;
                    exportContinuation.append(normalizedNextScanLine.stripLeading());
                    if (exportContinuation.toString().contains("}") || exportContinuation.toString().contains("*")) {
                        break;
                    }
                }
                exportClause = exportContinuation.toString().trim();
            }
            if (exportClause.startsWith("default ")) {
                String defaultClause = exportClause.substring("default ".length()).trim();
                if (isDynamicImportDefaultDeclarationClause(defaultClause)) {
                    String declarationName = extractExportedFunctionOrClassName(defaultClause);
                    String declarationLine = normalizedLine.replaceFirst("^(\\s*)export\\s+default\\s+", "$1");
                    // For multi-line declarations (class/function body spans multiple lines),
                    // accumulate subsequent lines until the body is complete.
                    while (findEndOfDeclarationBody(declarationLine) < 0 && lineIndex + 1 < lines.length) {
                        lineIndex++;
                        String nextLine = lines[lineIndex];
                        String normalizedNext = nextLine.endsWith("\r") ? nextLine.substring(0, nextLine.length() - 1) : nextLine;
                        declarationLine = declarationLine + "\n" + normalizedNext;
                    }
                    boolean anonymousDefaultDeclaration = false;
                    if (declarationName == null || declarationName.isEmpty()) {
                        String defaultLocalName = generatedNamePrefix + "DefaultExport$" + defaultExportIndex++;
                        declarationName = defaultLocalName;
                        // Use var assignment instead of renaming the declaration.
                        // var hoists to the IIFE function scope, so the getter in the
                        // export preamble can reference it before this line executes.
                        // Split the declaration from any trailing statements on the same line
                        // (e.g., `export default class {} if (...) { ... }`).
                        int bodyEnd = findEndOfDeclarationBody(declarationLine);
                        String declarationPart;
                        String remainingCode;
                        if (bodyEnd >= 0 && bodyEnd < declarationLine.length()) {
                            declarationPart = declarationLine.substring(0, bodyEnd);
                            remainingCode = declarationLine.substring(bodyEnd).trim();
                        } else {
                            declarationPart = declarationLine;
                            remainingCode = "";
                        }
                        // For anonymous default class exports, insert a static block
                        // at the start of the class body to set .name = "default" before
                        // any static field initializers run. ES2024 specifies that
                        // default-exported anonymous classes get the name "default" during
                        // ClassDefinitionEvaluation, before static elements are evaluated.
                        if (defaultClause.startsWith("class")) {
                            int openBrace = findLikelyClassBodyOpenBrace(declarationPart);
                            if (openBrace >= 0) {
                                // ES2024 15.2.3.11: Only set name to "default" if the class
                                // doesn't already have a "name" own property (e.g. static name method).
                                declarationPart = declarationPart.substring(0, openBrace + 1)
                                        + " static { if (!Object.prototype.hasOwnProperty.call(this, 'name')"
                                        + " || this.name === ''"
                                        + " || this.name === '" + defaultLocalName + "') { "
                                        + "Object.defineProperty(this, 'name', {value: 'default', configurable: true}); } }"
                                        + declarationPart.substring(openBrace + 1);
                            }
                        }
                        if (defaultClause.startsWith("class")) {
                            transformedSourceBuilder.append("let ")
                                    .append(defaultLocalName)
                                    .append(" = ")
                                    .append(declarationPart)
                                    .append(";\n");
                            appendDynamicImportDefaultExportNameFixup(
                                    transformedSourceBuilder, declarationName, moduleRecord.resolvedSpecifier());
                        } else {
                            String renamedDeclaration = renameAnonymousDefaultExportDeclaration(
                                    declarationPart, defaultLocalName);
                            transformedSourceBuilder.append(renamedDeclaration).append('\n');
                            appendDynamicImportDefaultExportNameFixup(
                                    defaultExportNameFixups, declarationName, moduleRecord.resolvedSpecifier());
                        }
                        if (!remainingCode.isEmpty()) {
                            transformedSourceBuilder.append(remainingCode).append('\n');
                        }
                        anonymousDefaultDeclaration = true;
                    }
                    if (!anonymousDefaultDeclaration) {
                        // Named default exports (e.g., export default class Foo { ... })
                        // need var hoisting so the export preamble getter can reference
                        // the name before the declaration executes during self-import.
                        if (defaultClause.startsWith("class")) {
                            int bodyEnd = findEndOfDeclarationBody(declarationLine);
                            String declarationPart;
                            String remainingCode;
                            if (bodyEnd >= 0 && bodyEnd < declarationLine.length()) {
                                declarationPart = declarationLine.substring(0, bodyEnd);
                                remainingCode = declarationLine.substring(bodyEnd).trim();
                            } else {
                                declarationPart = declarationLine;
                                remainingCode = "";
                            }
                            transformedSourceBuilder.append("let ")
                                    .append(requireGeneratedIdentifier(
                                            declarationName,
                                            "default export declaration name '" + declarationName + "'",
                                            moduleRecord.resolvedSpecifier()))
                                    .append(" = ")
                                    .append(declarationPart)
                                    .append(";\n");
                            if (!remainingCode.isEmpty()) {
                                transformedSourceBuilder.append(remainingCode).append('\n');
                            }
                        } else {
                            transformedSourceBuilder.append(declarationLine).append('\n');
                        }
                    }
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, "default"));
                } else {
                    // An expression may run past the end of its line, and where it ends is a
                    // grammar question rather than a bracket count — see defaultExportExtents.
                    String defaultExpression;
                    String trailingCode = "";
                    DefaultExportExtent defaultExportExtent = defaultExportExtents.get(lineIndex);
                    if (defaultExportExtent != null) {
                        defaultExpression = defaultExportExtent.expression();
                        trailingCode = defaultExportExtent.trailingText();
                        lineIndex = Math.max(lineIndex, defaultExportExtent.endLineIndex());
                    } else {
                        // The parser could not be asked — source that does not tokenise, or a shape
                        // it declined. Fall back to taking lines while the delimiters are unbalanced.
                        defaultExpression = defaultClause;
                        while (!isBalancedExpressionText(defaultExpression) && lineIndex + 1 < lines.length) {
                            lineIndex++;
                            String continuationLine = lines[lineIndex];
                            if (continuationLine.endsWith("\r")) {
                                continuationLine = continuationLine.substring(0, continuationLine.length() - 1);
                            }
                            defaultExpression = defaultExpression + "\n" + continuationLine;
                        }
                        defaultExpression = defaultExpression.trim();
                        while (defaultExpression.endsWith(";")) {
                            defaultExpression = defaultExpression.substring(0, defaultExpression.length() - 1).trim();
                        }
                    }
                    String defaultLocalName = generatedNamePrefix + "DefaultExport$" + defaultExportIndex++;
                    transformedSourceBuilder.append("let ")
                            .append(defaultLocalName)
                            .append(" = (0, ")
                            .append(defaultExpression)
                            .append(");\n");
                    appendDynamicImportDefaultExportNameFixup(
                            transformedSourceBuilder, defaultLocalName, moduleRecord.resolvedSpecifier());
                    if (!trailingCode.isEmpty()) {
                        transformedSourceBuilder.append(trailingCode).append('\n');
                    }
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(defaultLocalName, "default"));
                }
                continue;
            }

            if (exportClause.startsWith("var ")
                    || exportClause.startsWith("let ")
                    || exportClause.startsWith("const ")) {
                transformedSourceBuilder.append(normalizedLine.replaceFirst("export\\s+", "")).append('\n');
                for (String declarationName : extractSimpleDeclarationNames(exportClause)) {
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, declarationName));
                }
                continue;
            }

            if (exportClause.startsWith("function ")
                    || exportClause.startsWith("function*")
                    || exportClause.startsWith("async function ")
                    || exportClause.startsWith("async function*")
                    || exportClause.startsWith("class ")) {
                String declarationLine = normalizedLine.replaceFirst("^(\\s*)export\\s+", "$1");
                while (findEndOfDeclarationBody(declarationLine) < 0 && lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextLine = lines[lineIndex];
                    String normalizedNextLine = nextLine.endsWith("\r")
                            ? nextLine.substring(0, nextLine.length() - 1)
                            : nextLine;
                    declarationLine = declarationLine + "\n" + normalizedNextLine;
                }
                int declarationBodyEnd = findEndOfDeclarationBody(declarationLine);
                String declarationPart = declarationLine;
                String remainingCode = "";
                if (declarationBodyEnd >= 0 && declarationBodyEnd < declarationLine.length()) {
                    declarationPart = declarationLine.substring(0, declarationBodyEnd);
                    remainingCode = declarationLine.substring(declarationBodyEnd).trim();
                }
                transformedSourceBuilder.append(declarationPart).append('\n');
                if (!remainingCode.isEmpty()) {
                    transformedSourceBuilder.append(remainingCode).append('\n');
                }
                String declarationName = extractExportedFunctionOrClassName(exportClause);
                if (declarationName == null || declarationName.isEmpty()) {
                    throw new JSException(context.throwSyntaxError("Invalid export statement"));
                }
                localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, declarationName));
                if (exportClause.startsWith("function ")
                        || exportClause.startsWith("function*")
                        || exportClause.startsWith("async function ")
                        || exportClause.startsWith("async function*")) {
                    hoistedFunctionExportBindings.add(new JSDynamicImportModule.HoistedFunctionExportBinding(
                            declarationName,
                            declarationName,
                            declarationPart));
                }
                continue;
            }

            if (exportClause.startsWith("{")) {
                String exportSpecifiersText = exportClause;
                while (findMatchingCloseBrace(exportSpecifiersText, 0) < 0 && lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextScanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
                    String normalizedNextScanLine = nextScanLine.endsWith("\r")
                            ? nextScanLine.substring(0, nextScanLine.length() - 1)
                            : nextScanLine;
                    exportSpecifiersText = exportSpecifiersText + "\n" + normalizedNextScanLine.stripLeading();
                }

                int closeBraceIndex = findMatchingCloseBrace(exportSpecifiersText, 0);
                if (closeBraceIndex < 0) {
                    throw new JSException(context.throwSyntaxError("Invalid export statement"));
                }
                String exportListText = exportSpecifiersText.substring(1, closeBraceIndex).trim();
                String afterBraceText = exportSpecifiersText.substring(closeBraceIndex + 1).trim();
                while (afterBraceText.endsWith(";")) {
                    afterBraceText = afterBraceText.substring(0, afterBraceText.length() - 1).trim();
                }
                String sourceSpecifier = null;
                if (!afterBraceText.isEmpty()) {
                    if (!afterBraceText.startsWith("from ")) {
                        throw new JSException(context.throwSyntaxError("Invalid export statement"));
                    }
                    String fromText = afterBraceText.substring("from ".length()).trim();
                    sourceSpecifier = stripQuotedSpecifier(fromText);
                    // Add side-effect import to ensure source-order evaluation.
                    // ES2024 requires all module dependencies (imports AND re-exports)
                    // to be evaluated in source order before the requesting module.
                    importPreambleBuilder.append("import '").append(sourceSpecifier).append("';\n");
                }
                int localBindingStartIndex = localExportBindings.size();
                parseDynamicImportExportList(exportListText, sourceSpecifier, localExportBindings, reExportBindings);
                if (sourceSpecifier == null && localBindingStartIndex < localExportBindings.size()) {
                    for (int localBindingIndex = localExportBindings.size() - 1;
                         localBindingIndex >= localBindingStartIndex;
                         localBindingIndex--) {
                        JSDynamicImportModule.LocalExportBinding localExportBinding =
                                localExportBindings.get(localBindingIndex);
                        ImportBinding importBinding = importedBindings.get(localExportBinding.localName());
                        if (importBinding == null || importBinding.deferredImport()) {
                            continue;
                        }
                        localExportBindings.remove(localBindingIndex);
                        reExportBindings.add(new JSDynamicImportModule.ReExportBinding(
                                importBinding.sourceSpecifier(),
                                importBinding.importedName(),
                                localExportBinding.exportedName(),
                                false));
                    }
                }
                continue;
            }

            if (exportClause.startsWith("*")) {
                String afterStarText = exportClause.substring(1).trim();
                if (afterStarText.startsWith("as ")) {
                    // Handle both identifier and string literal export names:
                    // export * as name from '...'
                    // export * as "name" from '...'
                    String afterAs = afterStarText.substring(3).trim();
                    String exportedName;
                    String remainingAfterName;
                    if (afterAs.startsWith("\"") || afterAs.startsWith("'")) {
                        char quote = afterAs.charAt(0);
                        int closeQuote = afterAs.indexOf(quote, 1);
                        if (closeQuote < 0) {
                            throw new JSException(context.throwSyntaxError("Invalid export statement"));
                        }
                        exportedName = afterAs.substring(1, closeQuote);
                        remainingAfterName = afterAs.substring(closeQuote + 1).trim();
                    } else {
                        Matcher identMatcher = Pattern.compile("^([A-Za-z_$][A-Za-z0-9_$]*)\\s+(.*)$")
                                .matcher(afterAs);
                        if (!identMatcher.find()) {
                            throw new JSException(context.throwSyntaxError("Invalid export statement"));
                        }
                        exportedName = identMatcher.group(1);
                        remainingAfterName = identMatcher.group(2).trim();
                    }
                    if (!remainingAfterName.startsWith("from ")) {
                        throw new JSException(context.throwSyntaxError("Invalid export statement"));
                    }
                    String fromText = remainingAfterName.substring(5).trim();
                    while (fromText.endsWith(";")) {
                        fromText = fromText.substring(0, fromText.length() - 1).trim();
                    }
                    String sourceSpecifier = stripQuotedSpecifier(fromText);
                    reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, MODULE_NAMESPACE_EXPORT_NAME, exportedName, false));
                    // Add side-effect import for source-order evaluation
                    importPreambleBuilder.append("import '").append(sourceSpecifier).append("';\n");
                    continue;
                }
                if (afterStarText.startsWith("from ")) {
                    String fromText = afterStarText.substring("from ".length()).trim();
                    while (fromText.endsWith(";")) {
                        fromText = fromText.substring(0, fromText.length() - 1).trim();
                    }
                    String sourceSpecifier = stripQuotedSpecifier(fromText);
                    reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, "*", "*", true));
                    // Add side-effect import for source-order evaluation
                    importPreambleBuilder.append("import '").append(sourceSpecifier).append("';\n");
                    continue;
                }
                throw new JSException(context.throwSyntaxError("Invalid export statement"));
            }

            throw new JSException(context.throwSyntaxError("Unexpected export syntax"));
        }

        moduleRecord.setHasExportSyntax(hasExportSyntax);
        moduleRecord.hoistedFunctionExportBindings().clear();
        moduleRecord.hoistedFunctionExportBindings().addAll(hoistedFunctionExportBindings);
        moduleRecord.setHoistedFunctionExportBindingsInitialized(false);
        moduleRecord.localExportBindings().addAll(localExportBindings);
        moduleRecord.reExportBindings().addAll(reExportBindings);
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            moduleRecord.explicitExportNames().add(localExportBinding.exportedName());
            moduleRecord.exportOrigins().put(localExportBinding.exportedName(), moduleRecord.resolvedSpecifier());
            moduleRecord.namespace().registerExportName(localExportBinding.exportedName());
        }
        for (JSDynamicImportModule.ReExportBinding reExportBinding : reExportBindings) {
            if (reExportBinding.starExport()) {
                continue;
            }
            moduleRecord.explicitExportNames().add(reExportBinding.exportedName());
            moduleRecord.namespace().registerExportName(reExportBinding.exportedName());
        }

        boolean hasTLA = MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(transformedSourceBuilder).find()
                || Pattern.compile("\\bawait\\b").matcher(scanSourceCode).find();
        moduleRecord.setHasTLA(hasTLA);

        if (hasExportSyntax) {
            String exportBindingName = createModuleExportBindingName(
                    generatedNamePrefix, moduleRecord.resolvedSpecifier());
            // Build the export assignment preamble separately — it goes at the START
            // of the IIFE body so self-import getters can read from the namespace
            // before user code executes. Getter functions are lazy (not called at
            // definition time), so TDZ for const/class locals is not violated.
            StringBuilder exportPreamble = new StringBuilder();
            appendDynamicImportExportAssignments(
                    exportPreamble, generatedNamePrefix, exportBindingName,
                    localExportBindings, importedBindingNames, moduleRecord.resolvedSpecifier());
            if (!defaultExportNameFixups.isEmpty()) {
                exportPreamble.append(defaultExportNameFixups);
            }
            LinkedHashSet<String> importedBindingsToCapture = new LinkedHashSet<>();
            for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
                ImportBinding importBinding = importedBindings.get(localExportBinding.localName());
                if (importBinding != null && importBinding.deferredImport()) {
                    importedBindingsToCapture.add(localExportBinding.localName());
                }
            }
            String transformedSource;
            if (hasTLA) {
                // For TLA export modules, capture only exportBindingName.
                // Imported bindings from self-imports stay live to preserve TDZ behavior.
                // Other imported bindings are captured so they remain available after
                // import-overlay cleanup while async module evaluation continues.
                LinkedHashSet<String> tlaImportedBindingsToCapture = new LinkedHashSet<>();
                for (String importedBindingName : importedBindingNames) {
                    ImportBinding importBinding = importedBindings.get(importedBindingName);
                    if (importBinding == null
                            || isSelfImportBinding(importBinding, moduleRecord.resolvedSpecifier())) {
                        continue;
                    }
                    tlaImportedBindingsToCapture.add(importedBindingName);
                }
                List<String> paramNames = new ArrayList<>();
                paramNames.add(exportBindingName);
                paramNames.addAll(tlaImportedBindingsToCapture);
                String paramList = String.join(", ", paramNames);
                transformedSource = importPreambleBuilder
                        + "(async function(" + paramList + ") {\n"
                        + exportPreamble
                        + transformedSourceBuilder
                        + "})(" + paramList + ");\n";
            } else {
                if (importedBindingsToCapture.isEmpty()) {
                    transformedSource = importPreambleBuilder
                            + "(function () {\n"
                            + exportPreamble
                            + transformedSourceBuilder
                            + "})();\n";
                } else {
                    String paramList = String.join(", ", importedBindingsToCapture);
                    transformedSource = importPreambleBuilder
                            + "(function (" + paramList + ") {\n"
                            + exportPreamble
                            + transformedSourceBuilder
                            + "})(" + paramList + ");\n";
                }
            }
            moduleRecord.setTransformedSource(transformedSource);
            moduleRecord.setExportBindingName(exportBindingName);
        } else if (!importedBindingNames.isEmpty() || hasTLA) {
            // Wrap non-export modules in an IIFE to capture imported bindings in closure.
            String paramList = String.join(", ", importedBindingNames);
            String transformedSource = importPreambleBuilder
                    + (hasTLA ? "(async function(" : "(function(")
                    + paramList + ") {\n"
                    + transformedSourceBuilder
                    + "})(" + paramList + ");\n";
            moduleRecord.setTransformedSource(transformedSource);
        } else {
            moduleRecord.setTransformedSource(sourceCode);
        }
    }

    /**
     * Parse a ModuleExportName value: either a quoted string literal or an identifier name.
     * Removes quotes from string literals, applies identifier escape decoding to identifiers.
     */
    String parseModuleExportNameValue(String raw) {
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"')
                || (trimmed.charAt(0) == '\'' && trimmed.charAt(trimmed.length() - 1) == '\''))) {
            return decodeModuleStringLiteralValue(trimmed.substring(1, trimmed.length() - 1));
        }
        return decodeIdentifierEscapes(trimmed);
    }

    void registerImportedBinding(
            String localName,
            String sourceSpecifier,
            String importedName,
            boolean deferredImport,
            Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        String normalizedLocalName =
                localName == null ? "" : decodeIdentifierEscapes(localName.trim());
        if (normalizedLocalName.isEmpty()) {
            return;
        }
        bindingNames.add(normalizedLocalName);
        if (sourceSpecifier != null && !sourceSpecifier.isEmpty()) {
            importedBindings.put(normalizedLocalName, new ImportBinding(sourceSpecifier, importedName, deferredImport));
        }
    }

    String renameAnonymousDefaultExportDeclaration(
            String declarationLine,
            String defaultLocalName) {
        String replacementName = Matcher.quoteReplacement(defaultLocalName);
        String renamedFunctionDeclaration = declarationLine.replaceFirst(
                "^(\\s*(?:async\\s+)?function(?:\\s*\\*)?)\\s*\\(",
                "$1 " + replacementName + "(");
        if (!renamedFunctionDeclaration.equals(declarationLine)) {
            return renamedFunctionDeclaration;
        }

        String renamedClassDeclaration = declarationLine.replaceFirst(
                "^(\\s*class)\\b",
                "$1 " + replacementName);
        if (!renamedClassDeclaration.equals(declarationLine)) {
            return renamedClassDeclaration;
        }

        throw new JSException(context.throwSyntaxError("Invalid default export declaration"));
    }

    /**
     * Require that a module loaded from disk is valid, and report its failure as a JavaScript
     * {@code SyntaxError} carrying that file's own coordinates.
     * <p>
     * A dependency is compiled before it is turned into generated module code for the same reason
     * an entry module is — see {@link #moduleSourceTransformer.requireModuleSourceCompiles(String)} — and additionally
     * because a Java compiler exception escaping here would leave the module cache holding a record
     * stuck in {@code LOADING}. Converting it to a {@link JSException} lets the caller's existing
     * handler evict that record.
     *
     * @param sourceCode        the dependency's source, unmodified
     * @param resolvedSpecifier the resolved path, used as the compilation's file name
     * @throws JSException when the dependency is not a valid module
     */
    void requireDependencyModuleSourceCompiles(String sourceCode, String resolvedSpecifier) {
        try {
            new Compiler(sourceCode, resolvedSpecifier).setContext(context).compile(true);
        } catch (JSSyntaxErrorException syntaxError) {
            throw new JSException(context.throwSyntaxError(
                    syntaxError.getMessage(), syntaxError.getSourceLocation()));
        } catch (JSCompilerException compilerError) {
            throw new JSException(context.throwSyntaxError(
                    compilerError.getMessage(), compilerError.getSourceLocation()));
        }
    }

    /**
     * Validate a name that is about to be interpolated into generated module source in identifier
     * position.
     * <p>
     * The module transformer builds JavaScript source text and evaluates it. Names extracted from
     * the module source by the ad-hoc scanner reach identifier position unescaped, so a value that
     * is not an identifier would splice arbitrary source into the generated program. Anything that
     * is not a well-formed ECMAScript identifier is rejected with a SyntaxError instead.
     *
     * @param name            the candidate identifier
     * @param description     what the name denotes, used in the error message
     * @param moduleSpecifier which module is being transformed, or null when it is not known
     * @return the name unchanged when it is a valid identifier
     * @throws JSException wrapping a SyntaxError when the name is not a valid identifier
     */
    String requireGeneratedIdentifier(String name, String description, String moduleSpecifier) {
        if (isValidIdentifierName(name)) {
            return name;
        }
        // No SourceLocation: the transformer works on rewritten text, and offsets into that name
        // no character of the source anybody wrote. Naming the module is the part that can be made
        // true, and it is the part an embedder with a graph of them actually needs — the message
        // used to identify neither the module nor the position.
        JSError error = context.throwSyntaxError("Unsupported module syntax: " + description
                + " is not a valid identifier"
                + (moduleSpecifier == null ? "" : " in module '" + moduleSpecifier + "'"));
        error.setSourceName(moduleSpecifier);
        throw new JSException(error);
    }

    /**
     * Require that module source is valid <em>as written</em>, before it is rewritten.
     * <p>
     * The grammar is the only thing that knows where a statement really ends: whether a brace closes
     * a block or an object literal, whether a line terminator would trigger automatic semicolon
     * insertion, whether a restricted production is in play. Re-deriving any of that from a token
     * scan would be a second, worse implementation of the parser — and its diagnostics would be a
     * second, worse vocabulary. Handing the source to the parser gives both for free.
     * <p>
     * <strong>Why this compiles rather than parses.</strong> Parsing alone answers the automatic
     * semicolon insertion question but leaves every early error the bytecode compiler raises —
     * duplicate {@code __proto__}, a duplicate export name, an unresolvable {@code break} target, an
     * invalid regular expression literal — to be discovered later, by which point the source has
     * been split onto more lines and wrapped in generated module code. The location on such an
     * error then belongs to text the caller never wrote: a duplicate {@code __proto__} at offset 44
     * of a 60-character module was reported at offset 119. Compiling the untouched source moves
     * every early error in front of the rewrite, so all of them are reported in the caller's
     * coordinates. The bytecode produced here is discarded; only the diagnostics matter.
     *
     * @param sourceCode the module source, unmodified
     * @throws JSSyntaxErrorException when the source is not a valid module
     */
    void requireModuleSourceCompiles(String sourceCode) {
        new Compiler(sourceCode, "<module>").setContext(context).compile(true);
    }

    /**
     * Find the top-level {@code import} and {@code export} declarations in module source, by
     * tokenising it.
     * <p>
     * The transformer downstream is line-oriented: it classifies a line by string-matching
     * {@code import }/{@code export } at its start. That is only sound if a declaration really does
     * start a line, and the engine's own lexer is the only thing that knows whether a given
     * {@code export} is a declaration or three letters inside a template, and whether a {@code /}
     * opens a regular expression or divides. Both questions used to be answered by
     * {@link #maskModuleComments(String)}, a hand-written character scanner with no notion of
     * regular expressions at all — so {@code /"/; export const v = 20;} desynchronised its quote
     * state for the rest of the line, and {@code const t = 1; export const v = 15;} was not
     * recognised as an export at all because the keyword was not first on its line.
     * <p>
     * This does not make module handling a parser: the declaration's <em>contents</em> are still
     * read textually afterwards. What it fixes is which text that is.
     *
     * @param sourceCode the module source
     * @return the scan, or {@code null} when the source cannot be tokenised, in which case callers
     * fall back to the regular expressions
     */
    ModuleDeclarationScan scanTopLevelModuleDeclarations(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return new ModuleDeclarationScan(false, false, List.of(), List.of());
        }
        if (!sourceCode.contains("import") && !sourceCode.contains("export")) {
            return new ModuleDeclarationScan(false, false, List.of(), List.of());
        }
        List<Token> tokens;
        try {
            tokens = tokenizeModuleSource(sourceCode);
        } catch (RuntimeException e) {
            // Source that does not tokenise is not this method's problem to report: the compiler
            // will reject it with a proper SyntaxError. Fall back so nothing changes for it.
            context.clearPendingException();
            return null;
        }

        boolean hasImportDeclaration = false;
        boolean hasExportDeclaration = false;
        TreeSet<Integer> lineBreakOffsets = new TreeSet<>();
        List<Integer> declarationOffsets = new ArrayList<>();
        int depth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            switch (token.type()) {
                case LBRACE, LPAREN, LBRACKET -> depth++;
                case RBRACE, RPAREN, RBRACKET -> depth = Math.max(0, depth - 1);
                default -> {
                }
            }
            if (depth != 0) {
                continue;
            }
            boolean isImportDeclaration = token.type() == TokenType.IMPORT
                    && index + 1 < tokens.size()
                    && tokens.get(index + 1).type() != TokenType.LPAREN
                    && tokens.get(index + 1).type() != TokenType.DOT;
            boolean isExportDeclaration = token.type() == TokenType.EXPORT;
            if (!isImportDeclaration && !isExportDeclaration) {
                continue;
            }
            hasImportDeclaration |= isImportDeclaration;
            hasExportDeclaration |= isExportDeclaration;
            declarationOffsets.add(token.offset());

            // A declaration that shares its line with earlier code needs a break in front of it.
            if (index > 0 && tokens.get(index - 1).line() == token.line()) {
                lineBreakOffsets.add(token.offset());
            }
            // And one behind it, when its end is identifiable and code follows on the same line.
            int endIndex = findModuleDeclarationEnd(tokens, index);
            if (endIndex >= 0 && endIndex + 1 < tokens.size()
                    && tokens.get(endIndex + 1).line() == tokens.get(endIndex).line()) {
                lineBreakOffsets.add(tokens.get(endIndex + 1).offset());
            }
        }
        return new ModuleDeclarationScan(
                hasImportDeclaration,
                hasExportDeclaration,
                List.copyOf(lineBreakOffsets),
                List.copyOf(declarationOffsets));
    }

    int skipWhitespace(String text, int startIndex) {
        int index = Math.max(0, startIndex);
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Split a string on commas that are not inside quoted strings.
     */
    List<String> splitOnTopLevelCommas(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == ',' && !inSingleQuote && !inDoubleQuote) {
                result.add(text.substring(start, i));
                start = i + 1;
            }
        }
        result.add(text.substring(start));
        return result;
    }

    /**
     * The module a quoted specifier names, as a value rather than as source text.
     *
     * @param text the specifier literal, possibly followed by an attributes clause
     * @return the decoded specifier
     */
    String stripQuotedSpecifier(String text) {
        String specifierText = text.trim();
        if (specifierText.length() < 2) {
            throw new JSException(context.throwSyntaxError("Invalid module specifier"));
        }
        char quote = specifierText.charAt(0);
        if (quote != '\'' && quote != '"') {
            throw new JSException(context.throwSyntaxError("Invalid module specifier"));
        }
        // A backslash escapes whatever follows it, so the first matching quote is not necessarily
        // the closing one: `'./a\\'b.mjs'` ends at the third quote, not the second.
        int closeQuote = -1;
        for (int index = 1; index < specifierText.length(); index++) {
            char ch = specifierText.charAt(index);
            if (ch == '\\') {
                index++;
                continue;
            }
            if (ch == quote) {
                closeQuote = index;
                break;
            }
        }
        if (closeQuote < 0) {
            throw new JSException(context.throwSyntaxError("Invalid module specifier"));
        }
        // Everything after the closing quote (e.g. `with { ... }`) is not part of the specifier.
        return decodeModuleStringLiteralValue(specifierText.substring(1, closeQuote));
    }

    /**
     * Where one {@code export default <expression>} ends, and what follows it on its last line.
     *
     * @param expression   the expression's text, exactly as written
     * @param endLineIndex the zero-based index of the line the expression ends on
     * @param trailingText the code that followed it on that line, without the terminating
     *                     semicolon; empty when nothing did
     */
    record DefaultExportExtent(String expression, int endLineIndex, String trailingText) {
    }

    record ImportBinding(String sourceSpecifier, String importedName, boolean deferredImport) {
    }

    /**
     * What a token scan found out about a source's top-level module declarations.
     *
     * @param hasImportDeclaration whether a static {@code import} declaration is present
     * @param hasExportDeclaration whether an {@code export} declaration is present
     * @param lineBreakOffsets     offsets at which a line break must be inserted so that every
     *                             declaration occupies whole lines, ascending and distinct
     */
    record ModuleDeclarationScan(
            boolean hasImportDeclaration,
            boolean hasExportDeclaration,
            List<Integer> lineBreakOffsets,
            List<Integer> declarationOffsets) {
    }
}
