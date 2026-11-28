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

package com.caoccao.qjs4j.compilation.lexer;

import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

/**
 * Locates template delimiters without treating braces inside strings, comments, regexes, or nested templates as
 * substitutions.
 */
public final class LexerTemplateScanner {
    private LexerTemplateScanner() {
    }

    public static int expressionEnd(String templateStr, int expressionStart) {
        int braceDepth = 1;
        int position = expressionStart;
        boolean regexAllowed = true;

        while (position < templateStr.length()) {
            char c = templateStr.charAt(position);

            if (Character.isWhitespace(c)) {
                position++;
                continue;
            }

            if (c == '\'' || c == '"') {
                position = skipDelimitedLiteral(templateStr, position, c);
                regexAllowed = false;
                continue;
            }

            if (c == '`') {
                position = templateEnd(templateStr, position + 1) + 1;
                regexAllowed = false;
                continue;
            }

            if (c == '/') {
                if (position + 1 < templateStr.length()) {
                    char next = templateStr.charAt(position + 1);
                    if (next == '/') {
                        position = skipLineComment(templateStr, position + 2);
                        regexAllowed = true;
                        continue;
                    }
                    if (next == '*') {
                        position = skipBlockComment(templateStr, position + 2);
                        regexAllowed = true;
                        continue;
                    }
                }

                if (regexAllowed) {
                    position = skipDelimitedLiteral(templateStr, position, '/');
                    regexAllowed = false;
                    continue;
                }

                position++;
                if (position < templateStr.length() && templateStr.charAt(position) == '=') {
                    position++;
                }
                regexAllowed = true;
                continue;
            }

            if (c == '{') {
                braceDepth++;
                position++;
                regexAllowed = true;
                continue;
            }

            if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return position;
                }
                position++;
                regexAllowed = false;
                continue;
            }

            if (c == '(' || c == '[' || c == ',' || c == ';' || c == ':') {
                position++;
                regexAllowed = true;
                continue;
            }

            if (c == ')' || c == ']') {
                position++;
                regexAllowed = false;
                continue;
            }

            if (c == '.') {
                if (position + 2 < templateStr.length() && templateStr.charAt(position + 1) == '.'
                        && templateStr.charAt(position + 2) == '.') {
                    position += 3;
                    regexAllowed = true;
                } else if (position + 1 < templateStr.length() && Character.isDigit(templateStr.charAt(position + 1))) {
                    position = skipNumberLiteral(templateStr, position);
                    regexAllowed = false;
                } else {
                    position++;
                    regexAllowed = false;
                }
                continue;
            }

            if (Lexer.isIdentifierStart(c)) {
                int start = position++;
                while (position < templateStr.length() && Lexer.isIdentifierPart(templateStr.charAt(position))) {
                    position++;
                }
                String identifier = templateStr.substring(start, position);
                regexAllowed = switch (identifier) {
                    case JSKeyword.RETURN, JSKeyword.THROW, JSKeyword.CASE, JSKeyword.DELETE, JSKeyword.VOID,
                            JSKeyword.TYPEOF, JSKeyword.INSTANCEOF, JSKeyword.IN, JSKeyword.OF, JSKeyword.NEW,
                            JSKeyword.DO, JSKeyword.ELSE, JSKeyword.YIELD, JSKeyword.AWAIT ->
                        true;
                    default -> false;
                };
                continue;
            }

            if (Character.isDigit(c)) {
                position = skipNumberLiteral(templateStr, position);
                regexAllowed = false;
                continue;
            }

            if ("+-*%&|^!~<>=?".indexOf(c) >= 0) {
                position++;
                if (position < templateStr.length()) {
                    char next = templateStr.charAt(position);
                    if (next == '=' || (next == c && "&|+-<>?".indexOf(c) >= 0)) {
                        position++;
                    } else if (c == '=' && next == '>') {
                        position++;
                    }
                }
                if (c == '>' && position < templateStr.length() && templateStr.charAt(position) == '>') {
                    position++;
                    if (position < templateStr.length() && templateStr.charAt(position) == '>') {
                        position++;
                    }
                    if (position < templateStr.length() && templateStr.charAt(position) == '=') {
                        position++;
                    }
                }
                regexAllowed = true;
                continue;
            }

            position++;
            regexAllowed = false;
        }

        throw new JSSyntaxErrorException("Unterminated template expression");
    }

    /** Returns the index of the next backtick or substitution, or the end of the input. */
    public static int nextDelimiter(String source, int start) {
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '`' || c == '$' && i + 1 < source.length() && source.charAt(i + 1) == '{') {
                return i;
            }
        }
        return source.length();
    }

    private static int skipBlockComment(String source, int position) {
        while (position + 1 < source.length()) {
            if (source.charAt(position) == '*' && source.charAt(position + 1) == '/') {
                return position + 2;
            }
            position++;
        }
        throw new JSSyntaxErrorException("Unterminated block comment in template expression");
    }

    private static int skipDecimalDigits(String source, int position) {
        while (position < source.length() && Character.isDigit(source.charAt(position))) {
            position++;
        }
        return position;
    }

    /** Skip an escaped string or regexp inside a template substitution, including regexp flags. */
    private static int skipDelimitedLiteral(String source, int start, char delimiter) {
        boolean regex = delimiter == '/';
        boolean inCharacterClass = false;
        int position = start + 1;
        while (position < source.length()) {
            char c = source.charAt(position++);
            if (c == '\\') {
                if (position < source.length()) {
                    position++;
                }
            } else if (c == '\n' || c == '\r') {
                break;
            } else if (regex && c == '[') {
                inCharacterClass = true;
            } else if (regex && c == ']') {
                inCharacterClass = false;
            } else if (c == delimiter && !inCharacterClass) {
                if (regex) {
                    while (position < source.length() && Lexer.isIdentifierPart(source.charAt(position))) {
                        position++;
                    }
                }
                return position;
            }
        }
        throw new JSSyntaxErrorException(regex
                ? "Unterminated regex literal in template expression"
                : "Unterminated string in template expression");
    }

    private static int skipLineComment(String source, int position) {
        while (position < source.length() && source.charAt(position) != '\n' && source.charAt(position) != '\r') {
            position++;
        }
        return position;
    }

    private static int skipNumberLiteral(String source, int position) {
        int start = position;
        if (source.charAt(position) == '0' && position + 1 < source.length()) {
            char prefix = source.charAt(position + 1);
            if (prefix == 'x' || prefix == 'X' || prefix == 'b' || prefix == 'B' || prefix == 'o' || prefix == 'O') {
                position += 2;
                while (position < source.length() && Lexer.isIdentifierPart(source.charAt(position))) {
                    position++;
                }
                return position;
            }
        }

        position = skipDecimalDigits(source, position);
        if (position < source.length() && source.charAt(position) == '.') {
            position++;
            position = skipDecimalDigits(source, position);
        }
        if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
            int expPos = position + 1;
            if (expPos < source.length() && (source.charAt(expPos) == '+' || source.charAt(expPos) == '-')) {
                expPos++;
            }
            position = skipDecimalDigits(source, expPos);
        }
        if (position < source.length() && source.charAt(position) == 'n') {
            position++;
        }
        return position == start ? start + 1 : position;
    }

    static int templateEnd(String source, int start) {
        int index = nextDelimiter(source, start);
        while (index < source.length() && source.charAt(index) == '$') {
            index = nextDelimiter(source, expressionEnd(source, index + 2) + 1);
        }
        if (index == source.length()) {
            throw new JSSyntaxErrorException("Invalid or unexpected token");
        }
        return index;
    }
}
