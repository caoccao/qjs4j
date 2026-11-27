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

import com.caoccao.qjs4j.unicode.UnicodeData;
import java.util.*;

/** Text and escape handling for module declarations, specifiers and generated JavaScript. */
final class ModuleSourceText {
    private ModuleSourceText() {
    }

    /**
     * Append the character a {@code \\uHHHH} or {@code \\u\{H+\}} escape denotes.
     *
     * @param text
     *            the literal body
     * @param u
     *            the index of the {@code u}
     * @param decoded
     *            collects the decoded text
     * @param identifier
     *            whether to preserve malformed identifier escapes verbatim
     * @return the index of the escape's last character
     */
    private static int appendUnicodeEscape(String text, int u, StringBuilder decoded, boolean identifier) {
        int digits = u + 1;
        boolean braced = digits < text.length() && text.charAt(digits) == '{';
        int end = braced ? text.indexOf('}', digits + 1) : digits + 4;
        int value = parseHex(text, braced ? digits + 1 : digits, end);
        if (value >= 0 && (identifier || value <= Character.MAX_CODE_POINT)) {
            decoded.appendCodePoint(value);
            return braced ? end : end - 1;
        }
        if (!identifier) {
            decoded.append('u');
            return u;
        }
        // Keep malformed identifier escapes verbatim; resume after the part examined.
        int next = braced ? (end < 0 ? digits + 1 : end + 1) : Math.min(end, text.length());
        decoded.append(text, u - 1, next);
        return next - 1;
    }

    static String decodeIdentifierEscapes(String text) {
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
            index = appendUnicodeEscape(text, index + 1, decodedTextBuilder, true);
        }
        return decodedTextBuilder.toString();
    }

    /**
     * The {@code StringValue} of a {@code StringLiteral} body — the text between the delimiters, with every escape
     * sequence applied.
     * <p>
     * A module specifier and an arbitrary module export name are {@code StringLiteral}s, and a {@code StringLiteral}'s
     * meaning is its {@code StringValue}, not its spelling. {@code './d\\u0065p.mjs'} names {@code ./dep.mjs} and
     * {@code "a\\u002db"} is the export name {@code a-b} — the lexer knows that, and the token-driven link pass
     * therefore already agreed with it. The evaluation path pulled the same literals out of raw source with
     * {@code substring} and kept the backslashes, so the two stages disagreed about module identity and about
     * export-name identity: the link check passed and evaluation then failed to find a module or a name that was there
     * all along, in one case after a dependency had already run.
     * <p>
     * Octal escapes are deliberately not decoded. They are a {@code SyntaxError} in a module, the source has already
     * been compiled once before this runs, and inventing a value for text the grammar rejects would be worse than
     * leaving it alone.
     *
     * @param literalBody
     *            the literal's text without its delimiters
     * @return the decoded value
     */
    static String decodeModuleStringLiteralValue(String literalBody) {
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
                    int value = parseHex(literalBody, index + 1, index + 3);
                    if (value < 0) {
                        decodedBuilder.append(escaped);
                    } else {
                        decodedBuilder.append((char) value);
                        index += 2;
                    }
                }
                case 'u' -> index = appendUnicodeEscape(literalBody, index, decodedBuilder, false);
                case '0' -> {
                    if (index + 1 < literalBody.length() && Character.isDigit(literalBody.charAt(index + 1))) {
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
     * Escape a value for interpolation into a double-quoted JavaScript string literal in generated module source.
     * <p>
     * Escaping only {@code \} and {@code "} is not enough: a line terminator, a control character or U+2028/U+2029
     * inside the value terminates the literal and turns the remainder of the value into source text.
     *
     * @param text
     *            the raw value
     * @return the value with every character that is unsafe inside a string literal escaped
     */
    static String escapeJavaScriptString(String text) {
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

    private static int findBodyClose(String text, int start, int depth) {
        boolean foundOpen = depth != 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    i = text.indexOf('\n', i + 2);
                    if (i < 0) {
                        return -1;
                    }
                    continue;
                }
                if (next == '*') {
                    int end = text.indexOf("*/", i + 2);
                    if (end < 0) {
                        return -1;
                    }
                    i = end + 1;
                    continue;
                }
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                i = skipQuotedText(text, i);
            } else if (ch == '{') {
                foundOpen = true;
                depth++;
            } else if (ch == '}' && --depth == 0 && foundOpen) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find the end of a function/class declaration body in the given text. Scans for the first '{' and its matching
     * '}', returning the index just after the closing brace. Returns -1 if not found.
     */
    static int findEndOfDeclarationBody(String text) {
        String trimmedText = text.stripLeading();
        if (trimmedText.startsWith(JSKeyword.CLASS) && (trimmedText.length() == JSKeyword.CLASS.length()
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
        int close = findBodyClose(text, 0, 0);
        return close < 0 ? -1 : close + 1;
    }

    static int findLikelyClassBodyOpenBrace(String text) {
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
    static int findMatchingCloseBrace(String text, int openBraceIndex) {
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

    static int findMatchingClosingBrace(String text, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= text.length() || text.charAt(openBraceIndex) != '{') {
            return -1;
        }
        return findBodyClose(text, openBraceIndex + 1, 1);
    }

    /**
     * Find the index of " as " keyword at the top level (not inside quotes). Returns the index of 'a' in "as", or -1 if
     * not found.
     */
    static int findTopLevelAs(String text) {
        for (int i = 0; i + 3 < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' || ch == '"') {
                i = skipQuotedText(text, i);
            } else if (Character.isWhitespace(ch) && text.startsWith("as", i + 1)
                    && Character.isWhitespace(text.charAt(i + 3))) {
                return i + 1;
            }
        }
        return -1;
    }

    static int findTopLevelAssignment(String text) {
        return findTopLevelDelimiter(text, 0, '=');
    }

    static int findTopLevelDelimiter(String text, int start, char delimiter) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' || ch == '"' || ch == '`') {
                i = skipQuotedText(text, i);
            } else if (depth == 0 && ch == delimiter
                    && (delimiter != '=' || i + 1 < text.length() && text.charAt(i + 1) != '=')) {
                return i;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            }
        }
        return -1;
    }

    static boolean isExpressionContinuationCharacter(char ch) {
        return ch == ')' || ch == ']' || ch == '}' || ch == ',' || ch == '.' || ch == ':' || ch == '?' || ch == '+'
                || ch == '-' || ch == '*' || ch == '/' || ch == '%' || ch == '<' || ch == '>' || ch == '=' || ch == '&'
                || ch == '|' || ch == '^';
    }

    /**
     * Whether the given text is a well-formed ECMAScript IdentifierName.
     *
     * @param name
     *            the candidate identifier
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
        for (int offset = Character.charCount(firstCodePoint); offset < name.length();) {
            int codePoint = name.codePointAt(offset);
            if (!UnicodeData.isIdentifierPart(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    static int parseHex(String text) {
        return text == null ? -1 : parseHex(text, 0, text.length());
    }

    private static int parseHex(String text, int start, int end) {
        if (start >= end || end > text.length()) {
            return -1;
        }
        int value = 0;
        for (int i = start; i < end; i++) {
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
     * Parse a ModuleExportName value: either a quoted string literal or an identifier name. Removes quotes from string
     * literals, applies identifier escape decoding to identifiers.
     */
    static String parseModuleExportNameValue(String raw) {
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && ((trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"')
                || (trimmed.charAt(0) == '\'' && trimmed.charAt(trimmed.length() - 1) == '\''))) {
            return decodeModuleStringLiteralValue(trimmed.substring(1, trimmed.length() - 1));
        }
        return decodeIdentifierEscapes(trimmed);
    }

    static int skipQuotedText(String text, int start) {
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == quote) {
                break;
            }
            i += ch == '\\' ? 2 : 1;
        }
        return i;
    }

    static int skipWhitespace(String text, int startIndex) {
        int index = Math.max(0, startIndex);
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Split a string on commas that are not inside quoted strings.
     */
    static List<String> splitOnTopLevelCommas(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int comma; (comma = findTopLevelDelimiter(text, start, ',')) >= 0; start = comma + 1) {
            result.add(text.substring(start, comma));
        }
        result.add(text.substring(start));
        return result;
    }

    static String stripTrailingSemicolons(String text) {
        text = text.trim();
        while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }
}
