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

import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.core.ModuleLinker.LinkedExportNameRequest;
import com.caoccao.qjs4j.core.ModuleLinker.LinkedModuleRequest;
import com.caoccao.qjs4j.core.module.ModuleSourceScan;

import java.util.*;

/** Reads dependency requests and their original source positions before linking a module. */
final class ModuleRequestReader {
    private int index;
    private final String source;
    private final List<Token> tokens;

    ModuleRequestReader(List<Token> tokens, String source) {
        this.tokens = tokens;
        this.source = source;
    }

    private void addName(List<LinkedExportNameRequest> names, String name) {
        names.add(new LinkedExportNameRequest(name, ModuleSourceScan.moduleTokenLocation(tokens, index, source)));
    }

    private boolean at(TokenType type) {
        return index < tokens.size() && tokens.get(index).type() == type;
    }

    List<LinkedModuleRequest> read() {
        List<LinkedModuleRequest> requests = new ArrayList<>();
        List<LinkedExportNameRequest> names = new ArrayList<>();
        for (int start = ModuleSourceScan.nextModuleDeclaration(tokens, 0); start >= 0; start = ModuleSourceScan
                .nextModuleDeclaration(tokens, index + 1)) {
            index = start + 1;
            if (index >= tokens.size()) {
                break;
            }
            names.clear();
            if (tokens.get(start).type() == TokenType.EXPORT) {
                if (take(TokenType.MUL)) {
                    if (take(TokenType.AS)) {
                        index++;
                    }
                } else if (at(TokenType.LBRACE)) {
                    readNamedImports(names);
                } else {
                    index = start;
                    continue;
                }
                if (!take(TokenType.FROM)) {
                    index--;
                    continue;
                }
            } else if (!at(TokenType.STRING)) {
                readImportNames(names);
                while (index < tokens.size() && !at(TokenType.FROM) && !at(TokenType.SEMICOLON)) {
                    index++;
                }
                if (!take(TokenType.FROM)) {
                    continue;
                }
            }
            if (at(TokenType.STRING)) {
                int specifier = index;
                String name = tokens.get(index++).value();
                Map<String, String> attributes = Map.of();
                if (!at(TokenType.SEMICOLON)) {
                    attributes = readAttributes();
                    index = index == specifier + 1
                            ? specifier
                            : ModuleSourceScan.skipModuleDeclarationTail(tokens, specifier);
                }
                List<LinkedExportNameRequest> importedNames = switch (names.size()) {
                    case 0 -> List.of();
                    case 1 -> List.of(names.get(0));
                    default -> List.copyOf(names);
                };
                requests.add(new LinkedModuleRequest(name,
                        ModuleSourceScan.moduleTokenLocation(tokens, specifier, source), attributes, importedNames));
            }
        }
        return requests;
    }

    private Map<String, String> readAttributes() {
        if (index + 1 >= tokens.size()
                || !("with".equals(tokens.get(index).value()) || "assert".equals(tokens.get(index).value()))
                || tokens.get(index + 1).type() != TokenType.LBRACE) {
            return Map.of();
        }
        index += 2;
        Map<String, String> attributes = new LinkedHashMap<>();
        while (index < tokens.size() && !at(TokenType.RBRACE)) {
            if (!take(TokenType.COMMA)) {
                String key = tokens.get(index++).value();
                if (take(TokenType.COLON) && index < tokens.size()) {
                    attributes.put(key, tokens.get(index++).value());
                }
            }
        }
        return attributes;
    }

    private void readImportNames(List<LinkedExportNameRequest> names) {
        if ("defer".equals(tokens.get(index).value()) && index + 1 < tokens.size()
                && tokens.get(index + 1).type() == TokenType.MUL) {
            index++;
        }
        if (!at(TokenType.MUL) && !at(TokenType.LBRACE)) {
            addName(names, "default");
            index++;
            if (!take(TokenType.COMMA)) {
                return;
            }
        }
        if (at(TokenType.LBRACE)) {
            readNamedImports(names);
        }
    }

    private void readNamedImports(List<LinkedExportNameRequest> names) {
        index++;
        while (index < tokens.size() && !take(TokenType.RBRACE)) {
            if (!take(TokenType.COMMA)) {
                addName(names, tokens.get(index).value());
                index++;
                if (take(TokenType.AS)) {
                    index++;
                }
            }
        }
    }

    private boolean take(TokenType type) {
        if (!at(type)) {
            return false;
        }
        index++;
        return true;
    }
}
