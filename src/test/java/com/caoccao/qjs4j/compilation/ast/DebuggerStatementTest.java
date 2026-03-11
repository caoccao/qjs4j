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

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import com.caoccao.qjs4j.compilation.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DebuggerStatementTest extends BaseJavetTest {
    @Test
    public void testDebuggerStatementContainsAwaitYield() {
        DebuggerStatement debuggerStatement = new DebuggerStatement(new SourceLocation(1, 1, 0));
        assertThat(debuggerStatement.containsAwait()).isFalse();
        assertThat(debuggerStatement.containsYield()).isFalse();
    }

    @Test
    public void testDebuggerStatementIsParsedAsStatementNode() {
        Parser parser = new Parser(new Lexer("debugger; 1;"));
        Program program = parser.parse();
        assertThat(program.getBody()).hasSize(2);
        assertThat(program.getBody().get(0)).isInstanceOf(DebuggerStatement.class);
        assertThat(program.getBody().get(1)).isInstanceOf(ExpressionStatement.class);
    }

    @Test
    public void testDebuggerStatementRuntimeNoOp() {
        assertIntegerWithJavet("debugger; 0");
    }

    @Test
    public void testDebuggerStatementWithTrailingExpression() {
        assertIntegerWithJavet("1; debugger; 2");
    }
}
