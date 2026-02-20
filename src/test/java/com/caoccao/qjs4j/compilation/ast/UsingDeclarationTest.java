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

import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.parser.Parser;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UsingDeclarationTest {
    @Test
    public void testAwaitUsingDeclarationInModule() {
        Program program = new Parser(new Lexer("await using value = resource;"), true).parse();
        assertThat(program.body()).hasSize(1);
        VariableDeclaration declaration = (VariableDeclaration) program.body().get(0);
        assertThat(declaration.kind()).isEqualTo(VariableKind.AWAIT_USING);
        assertThat(declaration.declarations()).hasSize(1);
        assertThat(declaration.declarations().get(0).id()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) declaration.declarations().get(0).id()).name()).isEqualTo("value");
    }

    @Test
    public void testAwaitUsingInAsyncFunction() {
        Program program = new Parser(new Lexer("""
                async function f() {
                    for (await using item of items) {
                        item;
                    }
                }""")).parse();
        FunctionDeclaration functionDeclaration = (FunctionDeclaration) program.body().get(0);
        ForOfStatement forOfStatement = (ForOfStatement) functionDeclaration.body().body().get(0);
        assertThat(((VariableDeclaration) forOfStatement.left()).kind()).isEqualTo(VariableKind.AWAIT_USING);
        assertThat(forOfStatement.isAsync()).isFalse();
    }

    @Test
    public void testAwaitUsingRejectedInScript() {
        assertThatThrownBy(() -> new Parser(new Lexer("await using value = resource;")).parse())
                .isInstanceOf(JSSyntaxErrorException.class);
    }

    @Test
    public void testForOfUsingDeclaration() {
        Program program = new Parser(new Lexer("""
                for (using item of items) {
                    item;
                }""")).parse();
        assertThat(program.body()).hasSize(1);
        ForOfStatement forOfStatement = (ForOfStatement) program.body().get(0);
        VariableDeclaration leftDecl = (VariableDeclaration) forOfStatement.left();
        assertThat(leftDecl.kind()).isEqualTo(VariableKind.USING);
        assertThat(leftDecl.declarations()).hasSize(1);
        assertThat(leftDecl.declarations().get(0).id()).isInstanceOf(Identifier.class);
        assertThat(leftDecl.declarations().get(0).init()).isNull();
    }

    @Test
    public void testUsingDeclarationWithPatterns() {
        Program program = new Parser(new Lexer("""
                using { x } = obj;
                using [ y ] = arr;""")).parse();
        assertThat(program.body()).hasSize(2);

        VariableDeclaration objectPatternDeclaration = (VariableDeclaration) program.body().get(0);
        assertThat(objectPatternDeclaration.kind()).isEqualTo(VariableKind.USING);
        assertThat(objectPatternDeclaration.declarations().get(0).id()).isInstanceOf(ObjectPattern.class);

        VariableDeclaration arrayPatternDeclaration = (VariableDeclaration) program.body().get(1);
        assertThat(arrayPatternDeclaration.kind()).isEqualTo(VariableKind.USING);
        assertThat(arrayPatternDeclaration.declarations().get(0).id()).isInstanceOf(ArrayPattern.class);
    }

    @Test
    public void testUsingDeclarationWithSimpleBinding() {
        Program program = new Parser(new Lexer("using value = resource;")).parse();
        assertThat(program.body()).hasSize(1);
        VariableDeclaration declaration = (VariableDeclaration) program.body().get(0);
        assertThat(declaration.kind()).isEqualTo(VariableKind.USING);
        assertThat(declaration.declarations()).hasSize(1);
        assertThat(declaration.declarations().get(0).id()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) declaration.declarations().get(0).id()).name()).isEqualTo("value");
        assertThat(declaration.declarations().get(0).init()).isInstanceOf(Identifier.class);
    }

    @Test
    public void testUsingIdentifierExpressionIsNotDeclaration() {
        Program program = new Parser(new Lexer("using = 1;")).parse();
        assertThat(program.body()).hasSize(1);
        ExpressionStatement expressionStatement = (ExpressionStatement) program.body().get(0);
        assertThat(expressionStatement.expression()).isInstanceOf(AssignmentExpression.class);
        AssignmentExpression assignmentExpression = (AssignmentExpression) expressionStatement.expression();
        assertThat(assignmentExpression.left()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) assignmentExpression.left()).name()).isEqualTo("using");
    }
}
