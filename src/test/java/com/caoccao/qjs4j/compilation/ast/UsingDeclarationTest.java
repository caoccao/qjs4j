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
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UsingDeclarationTest {
    @Test
    public void testAwaitUsingDeclarationInModule() {
        Program program = new Parser(new Lexer("await using value = resource;"), true).parse();
        assertThat(program.getBody()).hasSize(1);
        VariableDeclaration declaration = (VariableDeclaration) program.getBody().get(0);
        assertThat(declaration.getKind()).isEqualTo(VariableKind.AWAIT_USING);
        assertThat(declaration.getDeclarations()).hasSize(1);
        assertThat(declaration.getDeclarations().get(0).getId()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) declaration.getDeclarations().get(0).getId()).getName()).isEqualTo("value");
    }

    @Test
    public void testAwaitUsingInAsyncFunction() {
        Program program = new Parser(new Lexer("""
                async function f() {
                    for (await using item of items) {
                        item;
                    }
                }""")).parse();
        FunctionDeclaration functionDeclaration = (FunctionDeclaration) program.getBody().get(0);
        ForOfStatement forOfStatement = (ForOfStatement) functionDeclaration.getBody().getBody().get(0);
        assertThat(((VariableDeclaration) forOfStatement.getLeft()).getKind()).isEqualTo(VariableKind.AWAIT_USING);
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
        assertThat(program.getBody()).hasSize(1);
        ForOfStatement forOfStatement = (ForOfStatement) program.getBody().get(0);
        VariableDeclaration leftDecl = (VariableDeclaration) forOfStatement.getLeft();
        assertThat(leftDecl.getKind()).isEqualTo(VariableKind.USING);
        assertThat(leftDecl.getDeclarations()).hasSize(1);
        assertThat(leftDecl.getDeclarations().get(0).getId()).isInstanceOf(Identifier.class);
        assertThat(leftDecl.getDeclarations().get(0).getInit()).isNull();
    }

    @Test
    public void testUsingDeclarationWithPatterns() {
        Program program = new Parser(new Lexer("""
                using { x } = obj;
                using [ y ] = arr;""")).parse();
        assertThat(program.getBody()).hasSize(2);

        VariableDeclaration objectPatternDeclaration = (VariableDeclaration) program.getBody().get(0);
        assertThat(objectPatternDeclaration.getKind()).isEqualTo(VariableKind.USING);
        assertThat(objectPatternDeclaration.getDeclarations().get(0).getId()).isInstanceOf(ObjectPattern.class);

        VariableDeclaration arrayPatternDeclaration = (VariableDeclaration) program.getBody().get(1);
        assertThat(arrayPatternDeclaration.getKind()).isEqualTo(VariableKind.USING);
        assertThat(arrayPatternDeclaration.getDeclarations().get(0).getId()).isInstanceOf(ArrayPattern.class);
    }

    @Test
    public void testUsingDeclarationWithSimpleBinding() {
        Program program = new Parser(new Lexer("using value = resource;")).parse();
        assertThat(program.getBody()).hasSize(1);
        VariableDeclaration declaration = (VariableDeclaration) program.getBody().get(0);
        assertThat(declaration.getKind()).isEqualTo(VariableKind.USING);
        assertThat(declaration.getDeclarations()).hasSize(1);
        assertThat(declaration.getDeclarations().get(0).getId()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) declaration.getDeclarations().get(0).getId()).getName()).isEqualTo("value");
        assertThat(declaration.getDeclarations().get(0).getInit()).isInstanceOf(Identifier.class);
    }

    @Test
    public void testUsingIdentifierExpressionIsNotDeclaration() {
        Program program = new Parser(new Lexer("using = 1;")).parse();
        assertThat(program.getBody()).hasSize(1);
        ExpressionStatement expressionStatement = (ExpressionStatement) program.getBody().get(0);
        assertThat(expressionStatement.getExpression()).isInstanceOf(AssignmentExpression.class);
        AssignmentExpression assignmentExpression = (AssignmentExpression) expressionStatement.getExpression();
        assertThat(assignmentExpression.getLeft()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) assignmentExpression.getLeft()).getName()).isEqualTo("using");
    }
}
