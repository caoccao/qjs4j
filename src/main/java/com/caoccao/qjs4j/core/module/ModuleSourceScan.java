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

package com.caoccao.qjs4j.core.module;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.compilation.ast.Statement;
import com.caoccao.qjs4j.compilation.lexer.*;
import com.caoccao.qjs4j.compilation.parser.Parser;
import com.caoccao.qjs4j.core.JSContext;

import java.util.*;

/**
 * Lexical analysis shared by module normalization, export rewriting and linking. Each scan belongs to one source
 * string.
 */
public final class ModuleSourceScan {
    private final JSContext context;
    private ModuleDeclarationScan declarations;
    private final String sourceCode;
    private boolean tokenized;
    private List<Token> tokens;

    public ModuleSourceScan(JSContext context, String sourceCode) {
        this.context = context;
        this.sourceCode = sourceCode;
    }

    /**
     * The offset just past a default-export expression that begins at {@code start}.
     *
     * @param start
     *            the offset of the expression's first token
     * @param boundary
     *            the offset of the next top-level declaration, or the end of the source
     * @return the end offset, or -1 when the text does not parse
     */
    private int defaultExportExpressionEnd(int start, int boundary) {
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
     * Where each {@code export default <expression>} in a module really ends, decided by the parser.
     * <p>
     * The line-oriented transformer used to answer this by counting brackets, braces and parentheses and taking lines
     * while they were unbalanced. Balanced delimiters do not mark the end of an AssignmentExpression:
     * {@code export default 1 +} is balanced and incomplete, so the transform emitted {@code let X = (0, 1 +);} and a
     * valid module failed to parse at a position in generated text. It also counted delimiters inside
     * regular-expression literals, which it had no way to recognise, so {@code export default /\(/;} swallowed the
     * statement after it.
     * <p>
     * Where an expression ends is a grammar question — operators, conditionals, member and call continuations, template
     * substitutions, comments and automatic semicolon insertion all bear on it — and the parser is the only thing here
     * that knows the grammar. The expression is parsed in expression position (an assignment target is prefixed, so an
     * opening brace is an object literal rather than a block) and bounded at the next top-level declaration, so nothing
     * after it can be drawn in.
     *
     * @return the extents, by the zero-based line index of the {@code export} keyword; empty when the source cannot be
     *         scanned
     */
    public Map<Integer, DefaultExportExtent> defaultExportExtents() {
        if (sourceCode == null || !sourceCode.contains("export")) {
            return Map.of();
        }
        List<Token> tokens = tokens();
        if (tokens == null) {
            return Map.of();
        }
        Map<Integer, DefaultExportExtent> extents = new HashMap<>();
        for (int index = nextModuleDeclaration(tokens, 0); index >= 0; index = nextModuleDeclaration(tokens,
                index + 1)) {
            Token token = tokens.get(index);
            if (token.type() != TokenType.EXPORT || index + 2 >= tokens.size()
                    || tokens.get(index + 1).type() != TokenType.DEFAULT) {
                continue;
            }
            Token firstExpressionToken = tokens.get(index + 2);
            if (firstExpressionToken.type() == TokenType.FUNCTION || firstExpressionToken.type() == TokenType.CLASS
                    || (firstExpressionToken.type() == TokenType.ASYNC && index + 3 < tokens.size()
                            && tokens.get(index + 3).type() == TokenType.FUNCTION)) {
                // A default-exported declaration ends at its body, which the transformer finds by
                // matching braces — that much a delimiter count really can do.
                continue;
            }
            int expressionStart = firstExpressionToken.offset();
            int nextDeclaration = nextModuleDeclaration(tokens, index + 1);
            int boundary = nextDeclaration >= 0 ? tokens.get(nextDeclaration).offset() : sourceCode.length();
            int expressionEnd = defaultExportExpressionEnd(expressionStart, boundary);
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
            extents.put(lineIndexOfOffset(sourceCode, token.offset()),
                    new DefaultExportExtent(sourceCode.substring(expressionStart, expressionEnd),
                            lineIndexOfOffset(sourceCode, expressionEnd), trailingText));
        }
        return extents;
    }

    /**
     * A prefix for this module's generated bindings that the module's own source does not use.
     * <p>
     * The transformer declares its bookkeeping bindings in the module's own scope, before EvalRunner hides their
     * runtime metadata. {@code __qjs4jDefaultExport$0} is a legal identifier, so a module that happened to declare it
     * had its value replaced by the transformer's — silently, and only for that one name. Choosing a prefix the source
     * does not contain keeps the generated names out of the author's way whatever the author writes.
     * <p>
     * Whether the source uses the prefix is asked of the identifiers the lexer produces, not of the source text. An
     * identifier may be written with Unicode escapes — {@code __qjs4jDefault
     * Export$0} is the identifier {@code __qjs4jDefaultExport$0} — and a substring search does not see through those,
     * so a module that legally declared the name under an escape was rejected with a syntax error in generated source.
     * The decoded name is the one that has to be avoided, because the decoded name is what the binding is called.
     * <p>
     * After compilation, generated local/capture metadata is renamed to non-JavaScript names by EvalRunner. The exports
     * namespace lives in a private module-environment binding that direct eval does not inherit. This prefix still
     * prevents collisions while parsing the generated source.
     *
     * @return a prefix no identifier in the source begins with
     */
    public String generatedModuleNamePrefix() {
        String prefix = "__qjs4j";
        if (sourceCode == null || (!sourceCode.contains("qjs4j") && !sourceCode.contains("\\u"))) {
            // Neither the text nor an escape in it can decode to a name starting with the prefix.
            return prefix;
        }
        // Null when the source does not tokenise, in which case the text search is all there is —
        // and the compiler is about to reject the source anyway.
        Set<String> identifierNames = moduleIdentifierNames();
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

    /**
     * The zero-based indices of the lines that begin a top-level module declaration.
     * <p>
     * The line loop in the module source transformer used to decide this by string-matching
     * {@code import }/{@code export } at the start of a masked line, and {@link #maskModuleComments(String)} blanks
     * comments but leaves string and template contents verbatim — so a multi-line template containing a line that reads
     * {@code export const fake = 1;} produced an export. Membership in this set is decided by the lexer instead, so
     * text that merely looks like a declaration is not one.
     *
     * @return the line indices, or {@code null} when the source cannot be tokenised
     */
    public Set<Integer> moduleDeclarationLineIndices() {
        ModuleDeclarationScan scan = scanTopLevelModuleDeclarations();
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
     * @return the names, or null when the source does not tokenise
     */
    Set<String> moduleIdentifierNames() {
        List<Token> tokens = tokens();
        if (tokens == null) {
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
     * Find the top-level {@code import} and {@code export} declarations in module source, by tokenising it.
     * <p>
     * The transformer downstream is line-oriented: it classifies a line by string-matching
     * {@code import }/{@code export } at its start. That is only sound if a declaration really does start a line, and
     * the engine's own lexer is the only thing that knows whether a given {@code export} is a declaration or three
     * letters inside a template, and whether a {@code /} opens a regular expression or divides. Both questions used to
     * be answered by {@link #maskModuleComments(String)}, a hand-written character scanner with no notion of regular
     * expressions at all — so {@code /"/; export const v = 20;} desynchronised its quote state for the rest of the
     * line, and {@code const t = 1; export const v = 15;} was not recognised as an export at all because the keyword
     * was not first on its line.
     * <p>
     * This does not make module handling a parser: the declaration's <em>contents</em> are still read textually
     * afterwards. What it fixes is which text that is.
     *
     * @return the scan, or {@code null} when the source cannot be tokenised, in which case callers fall back to the
     *         regular expressions
     */
    public ModuleDeclarationScan scanTopLevelModuleDeclarations() {
        if (declarations != null) {
            return declarations;
        }
        if (sourceCode == null || (!sourceCode.contains("import") && !sourceCode.contains("export"))) {
            declarations = new ModuleDeclarationScan(false, false, List.of(), List.of());
            return declarations;
        }
        List<Token> tokens = tokens();
        if (tokens == null) {
            return null;
        }

        boolean hasImportDeclaration = false;
        boolean hasExportDeclaration = false;
        TreeSet<Integer> lineBreakOffsets = new TreeSet<>();
        List<Integer> declarationOffsets = new ArrayList<>();
        for (int index = nextModuleDeclaration(tokens, 0); index >= 0; index = nextModuleDeclaration(tokens,
                index + 1)) {
            Token token = tokens.get(index);
            hasImportDeclaration |= token.type() == TokenType.IMPORT;
            hasExportDeclaration |= token.type() == TokenType.EXPORT;
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
        declarations = new ModuleDeclarationScan(hasImportDeclaration, hasExportDeclaration,
                List.copyOf(lineBreakOffsets), List.copyOf(declarationOffsets));
        return declarations;
    }

    List<Token> tokens() {
        if (!tokenized) {
            tokenized = true;
            tokens = tokenize(context, sourceCode);
        }
        return tokens;
    }

    /**
     * The index of the last token of a module declaration whose extent is identifiable without parsing.
     * <p>
     * That is every {@code import} form, and the {@code export} forms that end at a module specifier or at the closing
     * brace of an export clause. {@code export default …} and {@code export <declaration>} are not included: their
     * extent is a declaration body, which is the parser's job, and the transformer's existing multi-line lookahead
     * already handles them.
     *
     * @param tokens
     *            the token list
     * @param start
     *            the index of the {@code import} or {@code export} token
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
            if (isExport && clauseEnd < 0 && index == start + 1 && token.type() != TokenType.MUL
                    && token.type() != TokenType.LBRACE) {
                // `export default …` or `export <declaration>`: not identifiable here.
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether a string token is the module specifier of a declaration rather than a module export name.
     * <p>
     * A specifier follows {@code from}, or the {@code import} keyword itself in {@code import 'spec'}. Treating any
     * top-level string as the specifier broke {@code export * as "All" from './m.js'}, whose export name is also a
     * top-level string (ES2022 arbitrary module namespace names).
     *
     * @param tokens
     *            the token list
     * @param start
     *            the index of the {@code import} or {@code export} token
     * @param index
     *            the index of the string token
     * @return true when the string is the module specifier
     */
    static boolean isModuleSpecifierPosition(List<Token> tokens, int start, int index) {
        if (index == start + 1) {
            return tokens.get(start).type() == TokenType.IMPORT;
        }
        return tokens.get(index - 1).type() == TokenType.FROM;
    }

    /**
     * The zero-based index of the line an offset falls on.
     *
     * @param sourceCode
     *            the source
     * @param offset
     *            the offset
     * @return the line index
     */
    static int lineIndexOfOffset(String sourceCode, int offset) {
        int lineIndex = 0;
        for (int index = 0; index < offset && index < sourceCode.length(); index++) {
            if (sourceCode.charAt(index) == '\n') {
                lineIndex++;
            }
        }
        return lineIndex;
    }

    public static String maskModuleComments(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return "";
        }
        char[] masked = null;
        int length = sourceCode.length();
        for (int i = 0; i < length; i++) {
            char ch = sourceCode.charAt(i);
            if (ch == '\'' || ch == '"' || ch == '`') {
                i = ModuleSourceText.skipQuotedText(sourceCode, i);
            } else if (ch == '/' && i + 1 < length) {
                char next = sourceCode.charAt(i + 1);
                if (next != '/' && next != '*') {
                    continue;
                }
                if (masked == null) {
                    masked = sourceCode.toCharArray();
                }
                int end = i + 2;
                if (next == '/') {
                    while (end < length && sourceCode.charAt(end) != '\n' && sourceCode.charAt(end) != '\r') {
                        end++;
                    }
                } else {
                    int close = sourceCode.indexOf("*/", end);
                    end = close < 0 ? length : close + 2;
                }
                for (; i < end; i++) {
                    if (masked[i] != '\n' && masked[i] != '\r') {
                        masked[i] = ' ';
                    }
                }
                i--;
            }
        }
        return masked == null ? sourceCode : new String(masked);
    }

    /**
     * The span one token occupies in the source it was read from.
     * <p>
     * A {@link Token} records where it starts but not where it ends, and its {@code value} is the decoded text, so it
     * cannot be measured either — {@code as} is one character long as a value and six in the source. The end is
     * therefore taken from the next token and walked back over whitespace, which is exact whenever the two are
     * separated by nothing else.
     *
     * @param tokens
     *            the token list
     * @param index
     *            the index of the token
     * @param sourceCode
     *            the source the tokens came from
     * @return the token's location
     */
    public static SourceLocation moduleTokenLocation(List<Token> tokens, int index, String sourceCode) {
        Token token = tokens.get(index);
        int endOffset = index + 1 < tokens.size() ? tokens.get(index + 1).offset() : sourceCode.length();
        endOffset = Math.min(endOffset, sourceCode.length());
        while (endOffset > token.offset() && Character.isWhitespace(sourceCode.charAt(endOffset - 1))) {
            endOffset--;
        }
        return new SourceLocation(token.line(), token.column(), token.offset(), endOffset);
    }

    public static int nextModuleDeclaration(List<Token> tokens, int start) {
        int depth = 0;
        for (int index = start; index < tokens.size(); index++) {
            switch (tokens.get(index).type()) {
                case LPAREN, LBRACKET, LBRACE -> depth++;
                case RPAREN, RBRACKET, RBRACE -> depth = Math.max(0, depth - 1);
                case EXPORT -> {
                    if (depth == 0) {
                        return index;
                    }
                }
                case IMPORT -> {
                    if (depth == 0 && index + 1 < tokens.size()) {
                        TokenType next = tokens.get(index + 1).type();
                        if (next != TokenType.LPAREN && next != TokenType.DOT) {
                            return index;
                        }
                    }
                }
                default -> {
                }
            }
        }
        return -1;
    }

    /**
     * Consume an optional {@code with}/{@code assert} attributes clause and an optional semicolon.
     *
     * @param tokens
     *            the token list
     * @param index
     *            the index of the last token consumed so far
     * @return the index of the declaration's last token
     */
    public static int skipModuleDeclarationTail(List<Token> tokens, int index) {
        int end = index;
        int next = end + 1;
        if (next < tokens.size()
                && ("with".equals(tokens.get(next).value()) || "assert".equals(tokens.get(next).value()))
                && next + 1 < tokens.size() && tokens.get(next + 1).type() == TokenType.LBRACE) {
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

    public static List<Token> tokenize(JSContext context, String sourceCode) {
        try {
            Lexer lexer = new Lexer(sourceCode);
            // Reserve space for a short declaration without scaling the initial allocation to a whole file.
            List<Token> result = new ArrayList<>(Math.min(64, sourceCode.length() / 4));
            for (Token token = lexer.nextToken(); token != null
                    && token.type() != TokenType.EOF; token = lexer.nextToken()) {
                result.add(token);
            }
            return result;
        } catch (RuntimeException invalidSource) {
            // The compiler supplies the syntax error. Callers fall back to their textual scan.
            context.clearPendingException();
            return null;
        }
    }

    /**
     * Where one {@code export default <expression>} ends, and what follows it on its last line.
     *
     * @param expression
     *            the expression's text, exactly as written
     * @param endLineIndex
     *            the zero-based index of the line the expression ends on
     * @param trailingText
     *            the code that followed it on that line, without the terminating semicolon; empty when nothing did
     */
    public record DefaultExportExtent(String expression, int endLineIndex, String trailingText) {
    }

    /**
     * What a token scan found out about a source's top-level module declarations.
     *
     * @param hasImportDeclaration
     *            whether a static {@code import} declaration is present
     * @param hasExportDeclaration
     *            whether an {@code export} declaration is present
     * @param lineBreakOffsets
     *            offsets at which a line break must be inserted so that every declaration occupies whole lines,
     *            ascending and distinct
     */
    public record ModuleDeclarationScan(boolean hasImportDeclaration, boolean hasExportDeclaration,
            List<Integer> lineBreakOffsets, List<Integer> declarationOffsets) {
    }
}
