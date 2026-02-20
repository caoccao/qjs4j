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

import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.Program;
import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.compilation.ast.Statement;
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for JavaScript.
 * Converts tokens into an Abstract Syntax Tree (AST).
 * <p>
 * This is the public facade that delegates to specialized parser classes:
 * {@link ExpressionParser}, {@link StatementParser}, {@link FunctionClassParser},
 * {@link PatternParser}, and {@link LiteralParser}.
 * All shared mutable state is held in {@link ParserContext}.
 */
public final class Parser {
    private final ParserContext ctx;
    private final ParserDelegates delegates;

    public Parser(Lexer lexer) {
        this(lexer, false);
    }

    public Parser(Lexer lexer, boolean moduleMode) {
        this(lexer, moduleMode, false);
    }

    public Parser(Lexer lexer, boolean moduleMode, boolean isEval) {
        this(lexer, moduleMode, isEval, 0, 0);
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Parser(Lexer lexer, boolean moduleMode, boolean isEval,
           int functionNesting, int asyncFunctionNesting) {
        this.ctx = new ParserContext(lexer, moduleMode, isEval,
                functionNesting, asyncFunctionNesting);
        this.delegates = new ParserDelegates(ctx);
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Token currentToken() {
        return ctx.currentToken;
    }

    /**
     * Parse the entire program.
     */
    public Program parse() {
        List<Statement> body = new ArrayList<>();
        SourceLocation location = ctx.getLocation();

        // Parse directives (like "use strict") at the beginning
        boolean strict = ctx.parseDirectives();
        ctx.strictMode = strict || ctx.moduleMode;
        ctx.lexer.setStrictMode(ctx.strictMode);

        while (!ctx.match(TokenType.EOF)) {
            Statement stmt = delegates.statements.parseStatement();
            if (stmt != null) {
                body.add(stmt);
            }
        }

        return new Program(body, ctx.moduleMode, strict || ctx.moduleMode, location);
    }

    // Package-private: used by LiteralParser for nested template expression parsing
    Expression parseExpression() {
        return delegates.expressions.parseExpression();
    }
}
