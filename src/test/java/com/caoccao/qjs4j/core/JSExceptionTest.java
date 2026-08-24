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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.compilation.ast.ASTNode;
import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class JSExceptionTest extends BaseTest {
    @Override
    protected JSRuntime createRuntime() {
        return new JSRuntime(new JSRuntimeOptions()
                .setShadowRealmEnabled(true)
                .setTemporalEnabled(true));
    }

    @Test
    void testConstructors() {
        Throwable cause = new IllegalStateException("cause");
        SourceLocation sourceLocation = new SourceLocation(2, 4, 10, 16);
        JSString errorValue = new JSString("error");
        JSError locatedErrorValue = context.throwSyntaxError("message", sourceLocation);
        context.clearPendingException();

        JSException messageException = new JSException("SyntaxError", "message");
        JSException causeException = new JSException("SyntaxError", "message", cause);
        JSException valueException = new JSException(errorValue);
        JSException valueCauseException = new JSException(errorValue, cause);
        JSException locatedValueException = new JSException(locatedErrorValue, cause);
        JSException unnamedObjectException = new JSException(new JSObject(context));

        assertThat(messageException.getMessage()).isEqualTo("SyntaxError: message");
        assertThat(messageException.getErrorValue()).isEqualTo(new JSString("SyntaxError: message"));
        assertThat(messageException.getSourceLocation()).isNull();
        assertThat(causeException.getCause()).isSameAs(cause);
        assertThat(causeException.getSourceLocation()).isNull();
        assertThat(valueException.getErrorValue()).isSameAs(errorValue);
        assertThat(valueException.getSourceLocation()).isNull();
        assertThat(valueCauseException.getCause()).isSameAs(cause);
        assertThat(valueCauseException.getSourceLocation()).isNull();
        assertThat(locatedValueException.getSourceLocation()).isSameAs(sourceLocation);
        assertThat(locatedValueException.fillInStackTrace()).isSameAs(locatedValueException);
        assertThat(unnamedObjectException.getMessage()).isEqualTo("Error");
    }

    @Test
    void testEvalCompilerSyntaxExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval("'use strict'; with ({}) {}", "script.js", false));

        assertThat(evalException.getMessage()).isEqualTo(
                "SyntaxError: Strict mode code may not include a with statement");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(1, 15, 14, 14));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testEvalLexerExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval("'\\xG1'", "script.js", false));

        assertThat(evalException.getMessage()).isEqualTo("SyntaxError: Invalid or unexpected token");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(1, 4, 3, 3));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testEvalModuleParserExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval("export const value = ;", "module.js", true));

        assertThat(evalException.getMessage()).isEqualTo("SyntaxError: Unexpected token ';'");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(1, 22, 21, 21));
        assertThat(context.hasPendingException()).isFalse();
        assertThat(context.eval("1 + 1", "after-module-error.js", false).toJavaObject()).isEqualTo(2.0);
    }

    @Test
    void testEvalNestedScriptExceptionCarriesSourceLocation() {
        String source = "function build() {\n"
                + "  return {\n"
                + "    __proto__: null,\n"
                + "    __proto__: {}\n"
                + "  };\n"
                + "}";

        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval(source, "nested.js", false));

        assertThat(evalException.getMessage()).isEqualTo(
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals");
        assertThat(evalException.getCause()).isNull();
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(4, 5, 55, 55));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testEvalParserExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval("const value = ;", "script.js", false));

        assertThat(evalException.getMessage()).isEqualTo("SyntaxError: Unexpected token ';'");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(1, 15, 14, 14));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testEvalScriptExceptionCarriesSourceLocation() {
        String source = "const value = {\n"
                + "  first: 1,\n"
                + "  __proto__: null,\n"
                + "  __proto__: {}\n"
                + "};";

        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval(source, "script.js", false));

        assertThat(evalException.getMessage()).isEqualTo(
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals");
        assertThat(evalException.getCause()).isNull();
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(4, 3, 49, 49));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testEveryErrorTypePropagatesSourceLocationToJSException() {
        SourceLocation sourceLocation = new SourceLocation(3, 7, 20, 24);
        List<JSError> errors = List.of(
                new JSError(context, "error", sourceLocation),
                new JSAggregateError(context, "error", sourceLocation),
                new JSEvalError(context, "error", sourceLocation),
                new JSRangeError(context, "error", sourceLocation),
                new JSReferenceError(context, "error", sourceLocation),
                new JSSuppressedError(context, "error", sourceLocation),
                new JSSyntaxError(context, "error", sourceLocation),
                new JSTypeError(context, "error", sourceLocation),
                new JSURIError(context, "error", sourceLocation));

        assertThat(errors).allSatisfy(error -> {
            assertThat(error.getSourceLocation()).isSameAs(sourceLocation);
            assertThat(new JSException(error).getSourceLocation()).isSameAs(sourceLocation);
        });
    }

    @Test
    void testFunctionConstructorExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval(
                        "new Function('return ({__proto__: null, __proto__: {}});');",
                        "script.js",
                        false));

        assertThat(evalException.getMessage()).isEqualTo(
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(3, 27, 41, 41));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testFunctionConstructorParserExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval("new Function('const value = ;');", "script.js", false));

        assertThat(evalException.getMessage()).isEqualTo("SyntaxError: Unexpected token ';'");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(3, 15, 29, 29));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testJavaSyntaxExceptionPropagatesSourceLocationToJSException() {
        SourceLocation sourceLocation = new SourceLocation(3, 7, 20, 24);
        JSError error = context.throwError(new JSSyntaxErrorException("message", sourceLocation));
        context.clearPendingException();

        assertThat(error).isInstanceOf(JSSyntaxError.class);
        assertThat(error.getSourceLocation()).isSameAs(sourceLocation);
        assertThat(new JSException(error).getSourceLocation()).isSameAs(sourceLocation);
    }

    @Test
    void testNestedEvalExceptionCarriesSourceLocation() {
        String nestedSource = "const value = {\n"
                + "  first: 1,\n"
                + "  __proto__: null,\n"
                + "  __proto__: {}\n"
                + "};";

        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval("eval(`" + nestedSource + "`);", "outer.js", false));

        assertThat(evalException.getMessage()).isEqualTo(
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(4, 3, 49, 49));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testNestedEvalParserExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval("eval(`const value = ;`);", "outer.js", false));

        assertThat(evalException.getMessage()).isEqualTo("SyntaxError: Unexpected token ';'");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(1, 15, 14, 14));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testShadowRealmCompilerExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval(
                        "new ShadowRealm().evaluate('({__proto__: null, __proto__: {}})');",
                        "script.js",
                        false));

        assertThat(evalException.getMessage()).isEqualTo(
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(1, 20, 19, 19));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testShadowRealmParserExceptionCarriesSourceLocation() {
        JSException evalException = catchThrowableOfType(
                JSException.class,
                () -> context.eval(
                        "new ShadowRealm().evaluate('const value = ;');",
                        "script.js",
                        false));

        assertThat(evalException.getMessage()).isEqualTo("SyntaxError: Unexpected token ';'");
        assertThat(evalException.getSourceLocation()).isEqualTo(new SourceLocation(1, 15, 14, 14));
        assertThat(context.hasPendingException()).isFalse();
    }

    @Test
    void testSourceLocationIsReadonlyAndAstIsNotExposed() throws NoSuchFieldException {
        Field sourceLocationField = JSException.class.getDeclaredField("sourceLocation");
        Field errorSourceLocationField = JSError.class.getDeclaredField("sourceLocation");

        assertThat(Modifier.isPrivate(sourceLocationField.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(sourceLocationField.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(errorSourceLocationField.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(errorSourceLocationField.getModifiers())).isTrue();
        assertThat(Arrays.stream(JSException.class.getDeclaredFields()))
                .noneMatch(field -> ASTNode.class.isAssignableFrom(field.getType()));
        assertThat(Arrays.stream(JSException.class.getMethods()))
                .noneMatch(method -> ASTNode.class.isAssignableFrom(method.getReturnType()));
        assertThat(Arrays.stream(JSError.class.getDeclaredFields()))
                .noneMatch(field -> ASTNode.class.isAssignableFrom(field.getType()));
        assertThat(Arrays.stream(JSError.class.getMethods()))
                .noneMatch(method -> ASTNode.class.isAssignableFrom(method.getReturnType()));
    }
}
