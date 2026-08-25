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

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getId().getName()).isEqualTo("Child");
        assertThat(classDecl.getSuperClass()).isNotNull();
        assertThat(classDecl.getSuperClass()).isInstanceOf(Identifier.class);
        Identifier parent = (Identifier) classDecl.getSuperClass();
        assertThat(parent.getName()).isEqualTo("Parent");
    }

    @Test
    public void testClassWithMethod() {
        String source = "class Counter { increment() { this.count++; } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(1);

        ClassElement element = classDecl.getBody().get(0);
        assertThat(element).isInstanceOf(MethodDefinition.class);

        MethodDefinition method = (MethodDefinition) element;
        assertThat(method.getKey()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) method.getKey()).getName()).isEqualTo("increment");
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

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(7);

        // Verify types
        assertThat(classDecl.getBody().get(0)).isInstanceOf(PropertyDefinition.class);
        assertThat(classDecl.getBody().get(1)).isInstanceOf(PropertyDefinition.class);
        assertThat(classDecl.getBody().get(2)).isInstanceOf(PropertyDefinition.class);
        assertThat(classDecl.getBody().get(3)).isInstanceOf(MethodDefinition.class);
        assertThat(classDecl.getBody().get(4)).isInstanceOf(MethodDefinition.class);
        assertThat(classDecl.getBody().get(5)).isInstanceOf(MethodDefinition.class);
        assertThat(classDecl.getBody().get(6)).isInstanceOf(StaticBlock.class);
    }

    @Test
    public void testClassWithPrivateField() {
        String source = "class Counter { #count = 0; }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(1);

        ClassElement element = classDecl.getBody().get(0);
        assertThat(element).isInstanceOf(PropertyDefinition.class);

        PropertyDefinition field = (PropertyDefinition) element;
        assertThat(field.getKey()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) field.getKey()).getName()).isEqualTo("count");
        assertThat(field.getValue()).isNotNull();
        assertThat(field.isStatic()).isFalse();
        assertThat(field.isPrivate()).isTrue();
    }

    @Test
    public void testClassWithPrivateInOperator() {
        String source = "class Counter { #count = 0; has(obj) { return #count in obj; } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(2);

        MethodDefinition method = (MethodDefinition) classDecl.getBody().get(1);
        ReturnStatement returnStatement = (ReturnStatement) method.getValue().getBody().getBody().get(0);
        BinaryExpression binaryExpression = (BinaryExpression) returnStatement.getArgument();

        assertThat(binaryExpression.getOperator()).isEqualTo(BinaryOperator.IN);
        assertThat(binaryExpression.getLeft()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) binaryExpression.getLeft()).getName()).isEqualTo("count");
        assertThat(binaryExpression.getRight()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) binaryExpression.getRight()).getName()).isEqualTo("obj");
    }

    @Test
    public void testClassWithPrivateMethods() {
        String source = "class Counter { #inc() { return 1; } static #version() { return 2; } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(2);

        MethodDefinition instanceMethod = (MethodDefinition) classDecl.getBody().get(0);
        assertThat(instanceMethod.getKey()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) instanceMethod.getKey()).getName()).isEqualTo("inc");
        assertThat(instanceMethod.isPrivate()).isTrue();
        assertThat(instanceMethod.isStatic()).isFalse();

        MethodDefinition staticMethod = (MethodDefinition) classDecl.getBody().get(1);
        assertThat(staticMethod.getKey()).isInstanceOf(PrivateIdentifier.class);
        assertThat(((PrivateIdentifier) staticMethod.getKey()).getName()).isEqualTo("version");
        assertThat(staticMethod.isPrivate()).isTrue();
        assertThat(staticMethod.isStatic()).isTrue();
    }

    @Test
    public void testClassWithPublicField() {
        String source = "class Example { count = 0; }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(1);

        ClassElement element = classDecl.getBody().get(0);
        assertThat(element).isInstanceOf(PropertyDefinition.class);

        PropertyDefinition field = (PropertyDefinition) element;
        assertThat(field.getKey()).isInstanceOf(Identifier.class);
        assertThat(((Identifier) field.getKey()).getName()).isEqualTo("count");
        assertThat(field.getValue()).isNotNull();
        assertThat(field.isStatic()).isFalse();
        assertThat(field.isPrivate()).isFalse();
    }

    @Test
    public void testClassWithStaticBlock() {
        String source = "class Config { static { console.log('init'); } }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(1);

        ClassElement element = classDecl.getBody().get(0);
        assertThat(element).isInstanceOf(StaticBlock.class);

        StaticBlock staticBlock = (StaticBlock) element;
        assertThat(staticBlock.getBody()).isNotEmpty();
    }

    @Test
    public void testClassWithStaticField() {
        String source = "class Example { static version = 1; }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getBody()).hasSize(1);

        PropertyDefinition field = (PropertyDefinition) classDecl.getBody().get(0);
        assertThat(((Identifier) field.getKey()).getName()).isEqualTo("version");
        assertThat(field.isStatic()).isTrue();
    }

    @Test
    public void testSimpleClassDeclaration() {
        String source = "class Point { }";
        Parser parser = new Parser(new Lexer(source));
        Program program = parser.parse();

        assertThat(program.getBody()).hasSize(1);
        assertThat(program.getBody().get(0)).isInstanceOf(ClassDeclaration.class);

        ClassDeclaration classDecl = (ClassDeclaration) program.getBody().get(0);
        assertThat(classDecl.getId()).isNotNull();
        assertThat(classDecl.getId().getName()).isEqualTo("Point");
        assertThat(classDecl.getSuperClass()).isNull();
        assertThat(classDecl.getBody()).isEmpty();
    }
}
