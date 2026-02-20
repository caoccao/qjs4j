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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class declaration parsing including private fields and static blocks.
 */
public class ClassParserTest {

    @Test
    public void testClassWithExtends() {
        String source = "class Child extends Parent { }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.id().name()).isEqualTo("Child");
        assertThat(classDecl.superClass()).isNotNull();
        assertThat(classDecl.superClass()).isInstanceOf(Identifier.class);
        Identifier parent = (Identifier) classDecl.superClass();
        assertThat(parent.name()).isEqualTo("Parent");
    }

    @Test
    public void testClassWithMethod() {
        String source = "class Counter { increment() { this.count++; } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(1);

        ClassDeclaration.ClassElement element = classDecl.body().get(0);
        assertThat(element).isInstanceOf(ClassDeclaration.MethodDefinition.class);

        ClassDeclaration.MethodDefinition method = (ClassDeclaration.MethodDefinition) element;
        assertThat(method.key()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) method.key()).name()).isEqualTo("increment");
        assertThat(method.isStatic()).isFalse();
        assertThat(method.isPrivate()).isFalse();
    }

    @Test
    public void testClassWithMultipleElements() {
        String source = """
                class Example {
                    #private = 1;
                    public = 2;
                    static shared = 3;
                
                    constructor() {}
                
                    #privateMethod() {}
                
                    static staticMethod() {}
                
                    static {
                        console.log('init');
                    }
                }
                """;
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(7);

        // Verify types
        assertThat(classDecl.body().get(0)).isInstanceOf(ClassDeclaration.PropertyDefinition.class);
        assertThat(classDecl.body().get(1)).isInstanceOf(ClassDeclaration.PropertyDefinition.class);
        assertThat(classDecl.body().get(2)).isInstanceOf(ClassDeclaration.PropertyDefinition.class);
        assertThat(classDecl.body().get(3)).isInstanceOf(ClassDeclaration.MethodDefinition.class);
        assertThat(classDecl.body().get(4)).isInstanceOf(ClassDeclaration.MethodDefinition.class);
        assertThat(classDecl.body().get(5)).isInstanceOf(ClassDeclaration.MethodDefinition.class);
        assertThat(classDecl.body().get(6)).isInstanceOf(ClassDeclaration.StaticBlock.class);
    }

    @Test
    public void testClassWithPrivateField() {
        String source = "class Counter { #count = 0; }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(1);

        ClassDeclaration.ClassElement element = classDecl.body().get(0);
        assertThat(element).isInstanceOf(ClassDeclaration.PropertyDefinition.class);

        ClassDeclaration.PropertyDefinition field = (ClassDeclaration.PropertyDefinition) element;
        assertThat(field.key()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) field.key()).name()).isEqualTo("count");
        assertThat(field.value()).isNotNull();
        assertThat(field.isStatic()).isFalse();
        assertThat(field.isPrivate()).isTrue();
    }

    @Test
    public void testClassWithPrivateInOperator() {
        String source = "class Counter { #count = 0; has(obj) { return #count in obj; } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(2);

        ClassDeclaration.MethodDefinition method = (ClassDeclaration.MethodDefinition) classDecl.body().get(1);
        ReturnStatement returnStatement = (ReturnStatement) method.value().body().body().get(0);
        BinaryExpression binaryExpression = (BinaryExpression) returnStatement.argument();

        assertThat(binaryExpression.operator()).isEqualTo(BinaryExpression.BinaryOperator.IN);
        assertThat(binaryExpression.left()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) binaryExpression.left()).name()).isEqualTo("count");
        assertThat(binaryExpression.right()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) binaryExpression.right()).name()).isEqualTo("obj");
    }

    @Test
    public void testClassWithPrivateMethods() {
        String source = "class Counter { #inc() { return 1; } static #version() { return 2; } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(2);

        ClassDeclaration.MethodDefinition instanceMethod = (ClassDeclaration.MethodDefinition) classDecl.body().get(0);
        assertThat(instanceMethod.key()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) instanceMethod.key()).name()).isEqualTo("inc");
        assertThat(instanceMethod.isPrivate()).isTrue();
        assertThat(instanceMethod.isStatic()).isFalse();

        ClassDeclaration.MethodDefinition staticMethod = (ClassDeclaration.MethodDefinition) classDecl.body().get(1);
        assertThat(staticMethod.key()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) staticMethod.key()).name()).isEqualTo("version");
        assertThat(staticMethod.isPrivate()).isTrue();
        assertThat(staticMethod.isStatic()).isTrue();
    }

    @Test
    public void testClassWithPublicField() {
        String source = "class Example { count = 0; }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(1);

        ClassDeclaration.ClassElement element = classDecl.body().get(0);
        assertThat(element).isInstanceOf(ClassDeclaration.PropertyDefinition.class);

        ClassDeclaration.PropertyDefinition field = (ClassDeclaration.PropertyDefinition) element;
        assertThat(field.key()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) field.key()).name()).isEqualTo("count");
        assertThat(field.value()).isNotNull();
        assertThat(field.isStatic()).isFalse();
        assertThat(field.isPrivate()).isFalse();
    }

    @Test
    public void testClassWithStaticBlock() {
        String source = "class Config { static { console.log('init'); } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(1);

        ClassDeclaration.ClassElement element = classDecl.body().get(0);
        assertThat(element).isInstanceOf(ClassDeclaration.StaticBlock.class);

        ClassDeclaration.StaticBlock staticBlock = (ClassDeclaration.StaticBlock) element;
        assertThat(staticBlock.body()).isNotEmpty();
    }

    @Test
    public void testClassWithStaticField() {
        String source = "class Example { static version = 1; }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.body()).hasSize(1);

        ClassDeclaration.PropertyDefinition field = (ClassDeclaration.PropertyDefinition) classDecl.body().get(0);
        assertThat(((Identifier) field.key()).name()).isEqualTo("version");
        assertThat(field.isStatic()).isTrue();
    }

    @Test
    public void testSimpleClassDeclaration() {
        String source = "class Point { }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        assertThat(program.body()).hasSize(1);
        assertThat(program.body().get(0)).isInstanceOf(ClassDeclaration.class);

        ClassDeclaration classDecl = (ClassDeclaration) program.body().get(0);
        assertThat(classDecl.id()).isNotNull();
        assertThat(classDecl.id().name()).isEqualTo("Point");
        assertThat(classDecl.superClass()).isNull();
        assertThat(classDecl.body()).isEmpty();
    }
}
