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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.parser.Parser;

import static org.assertj.core.api.Assertions.assertThat;

final class ParsedScript {
    private ParsedScript() {
    }

    static <T extends Expression> T expression(String source, Class<T> type) {
        Expression expression = statement(source, ExpressionStatement.class).getExpression();
        assertThat(expression).isInstanceOf(type);
        return type.cast(expression);
    }

    static Program parse(String source) {
        return new Parser(new Lexer(source)).parse();
    }

    static <T extends Statement> T statement(Program program, Class<T> type) {
        assertThat(program.getBody()).hasSize(1);
        Statement statement = program.getBody().get(0);
        assertThat(statement).isInstanceOf(type);
        return type.cast(statement);
    }

    static <T extends Statement> T statement(String source, Class<T> type) {
        return statement(parse(source), type);
    }
}
