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

package com.caoccao.qjs4j.exceptions;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.compilation.lexer.Lexer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class JSSyntaxErrorExceptionTest {
    @Test
    void testConstructorsAndReadonlySourceLocation() throws NoSuchFieldException {
        SourceLocation sourceLocation = new SourceLocation(2, 4, 10, 16);
        JSErrorException errorException = new JSErrorException("error");
        JSSyntaxErrorException messageException = new JSSyntaxErrorException("message");
        JSSyntaxErrorException locatedException = new JSSyntaxErrorException("located", sourceLocation);

        assertThat(errorException.getDetailedMessage()).isEqualTo("Error: error");
        assertThat(errorException.getErrorType()).isEqualTo(JSErrorType.Error);
        assertThat(messageException.getMessage()).isEqualTo("message");
        assertThat(messageException.getSourceLocation()).isNull();
        assertThat(messageException.getDetailedMessage()).isEqualTo("SyntaxError: message");
        assertThat(messageException.getErrorType()).isEqualTo(JSErrorType.SyntaxError);
        assertThat(messageException.fillInStackTrace()).isSameAs(messageException);
        assertThat(locatedException.getSourceLocation()).isSameAs(sourceLocation);

        Field sourceLocationField = JSErrorException.class.getDeclaredField("sourceLocation");
        assertThat(Modifier.isPrivate(sourceLocationField.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(sourceLocationField.getModifiers())).isTrue();
    }

    @Test
    void testLexerExceptionCarriesSourceLocation() {
        JSSyntaxErrorException exception = catchThrowableOfType(
                JSSyntaxErrorException.class,
                () -> new Lexer("'\\xG1'").nextToken());
        JSSyntaxErrorException peekException = catchThrowableOfType(
                JSSyntaxErrorException.class,
                () -> new Lexer("'\\xG1'").peekToken());

        assertThat(exception.getMessage()).isEqualTo("Invalid or unexpected token");
        assertThat(exception.getSourceLocation()).isEqualTo(new SourceLocation(1, 4, 3, 3));
        assertThat(peekException.getSourceLocation()).isEqualTo(exception.getSourceLocation());
    }

    @Test
    void testLexerPeekTokenIsNonConsuming() {
        Lexer lexer = new Lexer("value");

        assertThat(lexer.peekToken()).isSameAs(lexer.peekToken());
        assertThat(lexer.nextToken().value()).isEqualTo("value");
    }

    @Test
    void testParserExceptionCarriesSourceLocation() {
        JSSyntaxErrorException exception = catchThrowableOfType(
                JSSyntaxErrorException.class,
                () -> new Compiler("const value = ;", "script.js").parse(false));

        assertThat(exception.getMessage()).isEqualTo("Unexpected token ';'");
        assertThat(exception.getSourceLocation()).isEqualTo(new SourceLocation(1, 15, 14, 14));
    }

    @Test
    void testWithSourceLocationPreservesFirstLocation() {
        SourceLocation firstLocation = new SourceLocation(2, 4, 10, 10);
        SourceLocation secondLocation = new SourceLocation(3, 5, 20, 20);
        JSSyntaxErrorException messageException = new JSSyntaxErrorException("message");

        assertThat(messageException.withSourceLocation(null)).isSameAs(messageException);

        JSSyntaxErrorException locatedException = messageException.withSourceLocation(firstLocation);
        assertThat(locatedException).isNotSameAs(messageException);
        assertThat(locatedException.getSourceLocation()).isSameAs(firstLocation);
        assertThat(locatedException.withSourceLocation(secondLocation)).isSameAs(locatedException);
        assertThat(locatedException.getSourceLocation()).isSameAs(firstLocation);
    }
}
