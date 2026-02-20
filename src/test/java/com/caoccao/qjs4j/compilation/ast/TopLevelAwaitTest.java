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
import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.parser.Parser;
import com.caoccao.qjs4j.core.JSPromise;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class TopLevelAwaitTest extends BaseJavetTest {
    private JSValue evalModule(String code) {
        JSValue value = resetContext().eval(code, FILE_NAME, true);
        if (value instanceof JSPromise jsPromise) {
            assertThat(awaitPromise(jsPromise)).isTrue();
        }
        return value;
    }

    @Test
    public void testAwaitIdentifierInModuleThrows() {
        moduleMode = true;
        assertThatThrownBy(() -> resetContext().eval("let await = 1; await;", FILE_NAME, true))
                .isInstanceOf(JSException.class);
    }

    @Test
    public void testAwaitIdentifierInScript() {
        assertIntegerWithJavet("""
                let await = 7;
                await;""");
    }

    @Test
    public void testAwaitInAsyncArrowFunctionInModule() {
        moduleMode = true;
        assertThatCode(() -> evalModule("""
                const f = async () => await Promise.resolve(5);
                await f();""")).doesNotThrowAnyException();
    }

    @Test
    public void testAwaitInClassFieldInitializerInModuleThrows() {
        moduleMode = true;
        assertThatThrownBy(() -> resetContext().eval("class C { x = await 1; }", FILE_NAME, true))
                .isInstanceOf(JSException.class);
    }

    @Test
    public void testAwaitInNonAsyncFunctionInsideModuleThrows() {
        moduleMode = true;
        assertThatThrownBy(() -> resetContext().eval("function f() { await 1; } f();", FILE_NAME, true))
                .isInstanceOf(JSException.class);
    }

    @Test
    public void testModuleProgramFlagsAndTopLevelAwaitAst() {
        Program program = new Parser(new Lexer("await 1;"), true).parse();
        assertThat(program.isModule()).isTrue();
        assertThat(program.strict()).isTrue();
        assertThat(program.body()).hasSize(1);
        ExpressionStatement expressionStatement = (ExpressionStatement) program.body().get(0);
        assertThat(expressionStatement.expression()).isInstanceOf(AwaitExpression.class);
    }

    @Test
    public void testParserRejectsAwaitIdentifierInModule() {
        assertThatThrownBy(() -> new Parser(new Lexer("let await = 1;"), true).parse())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    public void testScriptTopLevelAwaitThrows() {
        assertThatThrownBy(() -> resetContext().eval("await 1;", FILE_NAME, false))
                .isInstanceOf(JSException.class);
    }

    @Test
    public void testTopLevelAwaitForAwaitOfInModule() {
        moduleMode = true;
        assertThatCode(() -> evalModule("""
                let sum = 0;
                for await (const value of [1, 2, 3]) {
                    sum += value;
                }
                sum;""")).doesNotThrowAnyException();
    }

    @Test
    public void testTopLevelAwaitInModule() {
        moduleMode = true;
        assertThatCode(() -> evalModule("""
                const value = await Promise.resolve(41);
                value + 1;""")).doesNotThrowAnyException();
    }
}
