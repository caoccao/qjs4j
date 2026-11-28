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

package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;

/**
 * Compiles regex patterns to bytecode. Based on QuickJS libregexp.c compiler.
 * <p>
 * Supports captures, lookarounds, Unicode properties and quantified subexpressions.
 */
public final class RegExpCompiler {

    static final int MAX_UNICODE_CODE_POINT = 0x10FFFF;

    private final UnicodePropertyResolver unicodePropertyResolver;

    public RegExpCompiler(UnicodePropertyResolver unicodePropertyResolver) {
        this.unicodePropertyResolver = unicodePropertyResolver;
    }

    /**
     * Compile a regex pattern to bytecode.
     *
     * @param pattern
     *            The regex pattern string
     * @param flags
     *            The regex flags (g, i, m, s, u, y, d, v)
     * @return Compiled bytecode
     * @throws RegExpSyntaxException
     *             if the pattern is invalid
     */
    public RegExpBytecode compile(String pattern, String flags) {
        if (pattern == null) {
            throw new RegExpSyntaxException("Pattern cannot be null");
        }

        return new RegExpCompilation(pattern, RegExpBytecode.parseFlags(flags), unicodePropertyResolver).compile();
    }

    /**
     * Exception thrown when regex pattern has syntax errors.
     */
    public static class RegExpSyntaxException extends RuntimeException {
        public RegExpSyntaxException(String message) {
            super(message);
        }

        public RegExpSyntaxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
