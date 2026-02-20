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

/**
 * Container for all parser delegate instances.
 * Provides cross-delegate access so each delegate can call methods on other delegates.
 */
final class ParserDelegates {
    final ExpressionParser expressions;
    final FunctionClassParser functions;
    final LiteralParser literals;
    final PatternParser patterns;
    final StatementParser statements;

    ParserDelegates(ParserContext ctx) {
        this.expressions = new ExpressionParser(ctx, this);
        this.statements = new StatementParser(ctx, this);
        this.functions = new FunctionClassParser(ctx, this);
        this.patterns = new PatternParser(ctx, this);
        this.literals = new LiteralParser(ctx, this);
    }
}
