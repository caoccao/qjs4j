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

package com.caoccao.qjs4j.core.error;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.compilation.ast.ASTNode;
import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class JSExceptionTest extends BaseTest {
    private JSException assertEvalFailure(String source, String fileName, boolean module, String message,
            SourceLocation location) {
        JSException error = catchThrowableOfType(JSException.class, () -> context.eval(source, fileName, module));
        assertThat(error).isNotNull();
        assertThat(error.getMessage()).isEqualTo(message);
        assertThat(error.getSourceLocation()).isEqualTo(location);
        assertThat(context.hasPendingException()).isFalse();
        return error;
    }

    @Override
    protected JSRuntime createRuntime() {
        return new JSRuntime(new JSRuntimeOptions().setShadowRealmEnabled(true).setTemporalEnabled(true));
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
        assertEvalFailure("'use strict'; with ({}) {}", "script.js", false,
                "SyntaxError: Strict mode code may not include a with statement", new SourceLocation(1, 15, 14, 14));

    }

    @Test
    void testEvalLexerExceptionCarriesSourceLocation() {
        assertEvalFailure("'\\xG1'", "script.js", false, "SyntaxError: Invalid or unexpected token",
                new SourceLocation(1, 4, 3, 3));

    }

    @Test
    void testEvalModuleParserExceptionCarriesSourceLocation() {
        assertEvalFailure("export const value = ;", "module.js", true, "SyntaxError: Unexpected token ';'",
                new SourceLocation(1, 22, 21, 21));

        assertThat(context.eval("1 + 1", "after-module-error.js", false).toJavaObject()).isEqualTo(2.0);
    }

    @Test
    void testEvalNestedScriptExceptionCarriesSourceLocation() {
        String source = "function build() {\n" + "  return {\n" + "    __proto__: null,\n" + "    __proto__: {}\n"
                + "  };\n" + "}";

        assertThat(assertEvalFailure(source, "nested.js", false,
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals",
                new SourceLocation(4, 5, 55, 55)).getCause()).isNull();
    }

    @Test
    void testEvalParserExceptionCarriesSourceLocation() {
        assertEvalFailure("const value = ;", "script.js", false, "SyntaxError: Unexpected token ';'",
                new SourceLocation(1, 15, 14, 14));

    }

    @Test
    void testEvalScriptExceptionCarriesSourceLocation() {
        String source = "const value = {\n" + "  first: 1,\n" + "  __proto__: null,\n" + "  __proto__: {}\n" + "};";

        assertThat(assertEvalFailure(source, "script.js", false,
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals",
                new SourceLocation(4, 3, 49, 49)).getCause()).isNull();
    }

    @Test
    void testEveryErrorTypePropagatesSourceLocationToJSException() {
        SourceLocation sourceLocation = new SourceLocation(3, 7, 20, 24);
        List<JSError> errors = List.of(new JSError(context, "error", sourceLocation),
                new JSAggregateError(context, "error", sourceLocation),
                new JSEvalError(context, "error", sourceLocation), new JSRangeError(context, "error", sourceLocation),
                new JSReferenceError(context, "error", sourceLocation),
                new JSSuppressedError(context, "error", sourceLocation),
                new JSSyntaxError(context, "error", sourceLocation), new JSTypeError(context, "error", sourceLocation),
                new JSURIError(context, "error", sourceLocation));

        assertThat(errors).allSatisfy(error -> {
            assertThat(error.getSourceLocation()).isSameAs(sourceLocation);
            assertThat(new JSException(error).getSourceLocation()).isSameAs(sourceLocation);
        });
    }

    @Test
    void testFunctionConstructorExceptionCarriesSourceLocation() {
        assertEvalFailure("new Function('return ({__proto__: null, __proto__: {}});');", "script.js", false,
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals",
                new SourceLocation(3, 27, 41, 41));

    }

    @Test
    void testFunctionConstructorParserExceptionCarriesSourceLocation() {
        assertEvalFailure("new Function('const value = ;');", "script.js", false, "SyntaxError: Unexpected token ';'",
                new SourceLocation(3, 15, 29, 29));

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
        String nestedSource = "const value = {\n" + "  first: 1,\n" + "  __proto__: null,\n" + "  __proto__: {}\n"
                + "};";

        assertEvalFailure("eval(`" + nestedSource + "`);", "outer.js", false,
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals",
                new SourceLocation(4, 3, 49, 49));

    }

    @Test
    void testNestedEvalParserExceptionCarriesSourceLocation() {
        assertEvalFailure("eval(`const value = ;`);", "outer.js", false, "SyntaxError: Unexpected token ';'",
                new SourceLocation(1, 15, 14, 14));

    }

    @Test
    public void testRestoreVMExceptionPreservesTheGuestValueAndPendingError() {
        JSValue original = new JSString("thrown value");
        context.restoreVMException(new JSVirtualMachineException("diagnostic", original));
        assertThat(context.getPendingException()).isSameAs(original);

        JSVirtualMachineException messageOnly = new JSVirtualMachineException("fallback");
        context.restoreVMException(messageOnly);
        assertThat(context.takePendingException()).isSameAs(original);

        context.restoreVMException(messageOnly);
        assertThat(context.takePendingException()).isInstanceOfSatisfying(JSError.class,
                error -> assertThat(error.getMessage().value()).isEqualTo("fallback"));
    }

    @Test
    void testShadowRealmCompilerExceptionCarriesSourceLocation() {
        assertEvalFailure("new ShadowRealm().evaluate('({__proto__: null, __proto__: {}})');", "script.js", false,
                "SyntaxError: Duplicate __proto__ fields are not allowed in object literals",
                new SourceLocation(1, 20, 19, 19));

    }

    @Test
    void testShadowRealmParserExceptionCarriesSourceLocation() {
        assertEvalFailure("new ShadowRealm().evaluate('const value = ;');", "script.js", false,
                "SyntaxError: Unexpected token ';'", new SourceLocation(1, 15, 14, 14));

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
