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

public class NewExpressionTest extends BaseJavetTest {
    @Test
    public void testNewWithEmptySpread() {
        assertIntegerWithJavet("""
                function C() {
                  this.argCount = arguments.length;
                }
                new C(...[]).argCount;
                """);
    }

    @Test
    public void testNewWithIterableSpread() {
        assertStringWithJavet("""
                function C(...args) {
                  this.args = args;
                }
                new C(...new Set([1, 2, 3])).args.join(':');
                """);
    }

    @Test
    public void testNewWithMixedSpreadArguments() {
        assertStringWithJavet("""
                function C(a, b, c, d, e) {
                  this.result = `${a},${b},${c},${d},${e}`;
                }
                new C(1, ...[2, 3], ...[4], 5).result;
                """);
    }

    @Test
    public void testNewWithSpreadBasic() {
        assertIntegerWithJavet("""
                function C(a, b, c) {
                  this.sum = a + b + c;
                }
                new C(...[1, 2, 3]).sum;
                """);
    }

    @Test
    public void testNewWithSpreadOnNonConstructorThrowsTypeError() {
        assertBooleanWithJavet("""
                (() => {
                  try {
                    new Math.max(...[1, 2]);
                    return false;
                  } catch (e) {
                    return e instanceof TypeError;
                  }
                })();
                """);
    }

    @Test
    public void testParserParsesSpreadInNewExpression() {
        Program program = new Parser(new Lexer("new Foo(...args, 1,);")).parse();
        assertThat(program.body()).hasSize(1);
        assertThat(program.body().get(0)).isInstanceOf(ExpressionStatement.class);

        Expression expression = ((ExpressionStatement) program.body().get(0)).expression();
        assertThat(expression).isInstanceOf(NewExpression.class);

        NewExpression newExpression = (NewExpression) expression;
        assertThat(newExpression.arguments()).hasSize(2);
        assertThat(newExpression.arguments().get(0)).isInstanceOf(SpreadElement.class);
        assertThat(newExpression.arguments().get(1)).isInstanceOf(Literal.class);
    }
}
