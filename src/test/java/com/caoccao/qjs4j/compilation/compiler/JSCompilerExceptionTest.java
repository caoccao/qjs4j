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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class JSCompilerExceptionTest {
    @Test
    void testBreakCompilerExceptionCarriesAst() {
        SourceLocation sourceLocation = new SourceLocation(3, 5, 17, 22);
        BreakStatement breakStatement = new BreakStatement(null, sourceLocation);
        Program program = new Program(List.of(breakStatement), false, false, new SourceLocation(1, 1, 0, 22));

        JSCompilerException exception = catchThrowableOfType(
                JSCompilerException.class,
                () -> new BytecodeCompiler().compile(program));

        assertThat(exception.getMessage()).isEqualTo("Break statement outside of loop");
        assertThat(exception.getAst()).isSameAs(breakStatement);
        assertThat(exception.getAst().getLocation()).isSameAs(sourceLocation);
    }

    @Test
    void testBytecodeCompilerCompilesValidAst() {
        Program program = new Program(List.of(), false, false, new SourceLocation(1, 1, 0, 0));

        assertThat(new BytecodeCompiler().compile(program)).isNotNull();
    }

    @Test
    void testBytecodeCompilerSupportsNullInvalidAst() {
        JSCompilerException exception = catchThrowableOfType(
                JSCompilerException.class,
                () -> new BytecodeCompiler().compile(null));

        assertThat(exception.getMessage()).isEqualTo("Expected Program node");
        assertThat(exception.getAst()).isNull();
    }

    @Test
    void testBytecodeCompilerUsesInvalidAst() {
        Literal invalidAst = new Literal(1, new SourceLocation(4, 9, 28, 34));

        JSCompilerException exception = catchThrowableOfType(
                JSCompilerException.class,
                () -> new BytecodeCompiler().compile(invalidAst));

        assertThat(exception.getMessage()).isEqualTo("Expected Program node");
        assertThat(exception.getAst()).isSameAs(invalidAst);
    }

    @Test
    void testCompileNestedScriptExceptionCarriesSourceLocation() {
        String source = "function build() {\n"
                + "  return {\n"
                + "    __proto__: null,\n"
                + "    __proto__: {}\n"
                + "  };\n"
                + "}";

        JSCompilerException exception = catchThrowableOfType(
                JSCompilerException.class,
                () -> new Compiler(source, "nested.js").compile(false));

        assertThat(exception.getMessage()).isEqualTo(
                "Duplicate __proto__ fields are not allowed in object literals");
        assertThat(exception.getAst()).isInstanceOf(ObjectExpressionProperty.class);
        assertThat(exception.getAst().getLocation()).isEqualTo(new SourceLocation(4, 5, 55, 55));
    }

    @Test
    void testCompileScriptExceptionCarriesSourceLocation() {
        String source = "const value = {\n"
                + "  first: 1,\n"
                + "  __proto__: null,\n"
                + "  __proto__: {}\n"
                + "};";

        JSCompilerException exception = catchThrowableOfType(
                JSCompilerException.class,
                () -> new Compiler(source, "script.js").compile(false));

        assertThat(exception.getMessage()).isEqualTo(
                "Duplicate __proto__ fields are not allowed in object literals");
        assertThat(exception.getAst()).isInstanceOf(ObjectExpressionProperty.class);
        assertThat(exception.getAst().getLocation()).isEqualTo(new SourceLocation(4, 3, 49, 49));
    }

    @Test
    void testConstructorsAndReadonlyAst() throws NoSuchFieldException {
        Throwable cause = new IllegalStateException("cause");
        Literal ast = new Literal(1, new SourceLocation(2, 4, 10, 16));

        JSCompilerException messageException = new JSCompilerException("message");
        JSCompilerException causeException = new JSCompilerException("cause message", cause);
        JSCompilerException astException = new JSCompilerException("ast message", ast);
        JSCompilerException completeException = new JSCompilerException("complete message", cause, ast);

        assertThat(messageException.getMessage()).isEqualTo("message");
        assertThat(messageException.getCause()).isNull();
        assertThat(messageException.getAst()).isNull();
        assertThat(messageException.getSourceLocation()).isNull();
        assertThat(messageException.fillInStackTrace()).isSameAs(messageException);

        assertThat(causeException.getCause()).isSameAs(cause);
        assertThat(causeException.getAst()).isNull();
        assertThat(astException.getCause()).isNull();
        assertThat(astException.getAst()).isSameAs(ast);
        assertThat(astException.getSourceLocation()).isSameAs(ast.getLocation());
        assertThat(completeException.getCause()).isSameAs(cause);
        assertThat(completeException.getAst()).isSameAs(ast);

        Field astField = JSCompilerException.class.getDeclaredField("ast");
        assertThat(Modifier.isPrivate(astField.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(astField.getModifiers())).isTrue();
    }

    @Test
    void testLiteralCompilerExceptionCarriesAst() {
        SourceLocation sourceLocation = new SourceLocation(4, 9, 28, 34);
        Literal unsupportedLiteral = new Literal(new Object(), sourceLocation);
        ExpressionStatement statement = new ExpressionStatement(unsupportedLiteral, sourceLocation);
        Program program = new Program(List.of(statement), false, false, new SourceLocation(1, 1, 0, 34));

        JSCompilerException exception = catchThrowableOfType(
                JSCompilerException.class,
                () -> new BytecodeCompiler().compile(program));

        assertThat(exception.getAst()).isSameAs(unsupportedLiteral);
        assertThat(exception.getAst().getLocation()).isSameAs(sourceLocation);
    }
}
