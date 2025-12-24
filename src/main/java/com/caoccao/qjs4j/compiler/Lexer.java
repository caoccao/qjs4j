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

package com.caoccao.qjs4j.compiler;

/**
 * Lexical analyzer for JavaScript source code.
 * Converts source text into a stream of tokens.
 */
public final class Lexer {
    private final String source;
    private int position;
    private int line;
    private int column;

    public Lexer(String source) {
        this.source = source;
        this.position = 0;
        this.line = 1;
        this.column = 1;
    }

    public Token nextToken() {
        return null;
    }

    public Token peekToken() {
        return null;
    }

    public void reset() {
        position = 0;
        line = 1;
        column = 1;
    }
}
