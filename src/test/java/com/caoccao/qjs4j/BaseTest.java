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

package com.caoccao.qjs4j;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.module.JSFileSystemModuleLoader;
import com.caoccao.qjs4j.core.promise.IJSPromiseRejectCallback;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.JSException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import com.caoccao.qjs4j.exceptions.JSTerminationException;

public abstract class BaseTest {
    protected JSContext context;

    protected void assertError(JSValue value) {
        assertError(value, "Error", null);
    }

    protected void assertError(JSValue value, String expectedType, String expectedMessage) {
        assertThat(value).as("Value must be an error object").isInstanceOfSatisfying(JSObject.class, jsObject -> {
            assertThat(jsObject.get("name")).as("An error object must have a name")
                    .isInstanceOfSatisfying(JSString.class, name -> {
                        assertThat(name.value()).isEqualTo(expectedType);
                    });
            assertThat(jsObject.get("message")).as("An error object must have a message")
                    .isInstanceOfSatisfying(JSString.class, message -> {
                        if (expectedMessage == null) {
                            assertThat(message.value()).isNotNull();
                        } else {
                            assertThat(message.value()).isEqualTo(expectedMessage);
                        }
                    });
        });
    }

    protected void assertEvalJavaValue(String source, Object expected) {
        assertThat(context.eval(source).toJavaObject()).isEqualTo(expected);
    }

    protected void assertEvalNumber(String source, double expected) {
        assertNumberValue(context.eval(source), expected);
    }

    protected void assertEvalString(String source, String expected) {
        assertStringValue(context.eval(source), expected);
    }

    protected void assertEvalText(String source, String expected) {
        assertThat(context.eval(source).toString()).isEqualTo(expected);
    }

    protected void assertEvalThrows(String source, String expectedMessage) {
        assertThatThrownBy(() -> context.eval(source)).as(source).isInstanceOf(JSException.class)
                .hasMessageContaining(expectedMessage);
    }

    protected void assertEvalType(String source, Class<?> expected) {
        assertThat(context.eval(source)).isInstanceOf(expected);
    }

    protected void assertEvalValue(String source, JSValue expected) {
        assertThat(context.eval(source)).isEqualTo(expected);
    }

    protected void assertHostInterrupt(JSContext interruptedContext, String script) throws InterruptedException {
        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            interruptedContext.getRuntime().requestInterrupt();
        });
        interrupter.start();
        try {
            assertThatThrownBy(() -> interruptedContext.eval(script)).isInstanceOf(JSTerminationException.class)
                    .hasMessage("execution interrupted");
        } finally {
            interrupter.join();
        }
    }

    protected void assertIteratorResult(JSValue result, JSValue expectedValue, boolean done) {
        JSObject entry = result.asObject().orElseThrow();
        assertThat(entry.get("value")).isEqualTo(expectedValue);
        assertThat(entry.get("done")).isEqualTo(JSBoolean.valueOf(done));
    }

    protected void assertNumberElements(JSArray array, int start, double... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertNumberValue(array.get(start + i), expected[i]);
        }
    }

    protected void assertNumberValue(JSValue value, double expected) {
        assertThat(value).isInstanceOfSatisfying(JSNumber.class,
                number -> assertThat(number.value()).isEqualTo(expected));
    }

    protected void assertPendingException(JSContext context) {
        assertThat(context.hasPendingException()).isTrue();
        context.clearPendingException();
    }

    protected void assertPendingRangeError(JSContext context, JSValue value) {
        assertRangeError(value);
        assertPendingException(context);
    }

    protected void assertPendingSyntaxError(JSContext context, JSValue value) {
        assertSyntaxError(value);
        assertPendingException(context);
    }

    protected void assertPendingTypeError(JSContext context, JSValue value) {
        assertTypeError(value);
        assertPendingException(context);
    }

    protected void assertPendingTypeError(JSContext context, JSValue value, String expectedMessage) {
        assertTypeError(value, expectedMessage);
        assertPendingException(context);
    }

    protected void assertRangeError(JSValue value) {
        assertError(value, "RangeError", null);
    }

    protected void assertStringElements(JSArray array, int start, String... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertStringValue(array.get(start + i), expected[i]);
        }
    }

    protected void assertStringValue(JSValue value, String expected) {
        assertThat(value).isInstanceOfSatisfying(JSString.class,
                string -> assertThat(string.value()).isEqualTo(expected));
    }

    protected void assertSyntaxError(JSValue value) {
        assertError(value, "SyntaxError", null);
    }

    protected void assertTypeError(JSValue value) {
        assertError(value, "TypeError", null);
    }

    protected void assertTypeError(JSValue value, String expectedMessage) {
        assertError(value, "TypeError", expectedMessage);
    }

    protected boolean awaitPromise(JSPromise promise) {
        for (int i = 0; i < 1000 && promise.getState() == JSPromise.PromiseState.PENDING; i++) {
            context.processMicrotasks();
        }
        return promise.getState() != JSPromise.PromiseState.PENDING;
    }

    /** Bind the receiver once for a table of native builtin input/output cases. */
    protected BuiltinAssertions builtin(JSNativeCallback callback, JSValue receiver) {
        return new BuiltinAssertions(callback, receiver);
    }

    protected JSValue callBuiltin(JSNativeCallback callback, JSValue receiver, Object... arguments) {
        return callback.call(context, receiver, nativeArguments(arguments));
    }

    protected JSRuntime createRuntime() {
        return new JSRuntime(
                new JSRuntimeOptions().setModuleLoader(new JSFileSystemModuleLoader()).setTemporalEnabled(true));
    }

    protected boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    protected String loadCode(String path) throws IOException {
        return IOUtils.resourceToString(path, StandardCharsets.UTF_8, getClass().getClassLoader());
    }

    protected JSArray nativeArray(Object... elements) {
        return new JSArray(context, nativeArguments(elements));
    }

    protected JSContext resetContext() {
        IJSPromiseRejectCallback jsPromiseRejectCallback = context.getPromiseRejectCallback();
        context.getRuntime().close();
        context = createRuntime().createContext();
        context.setPromiseRejectCallback(jsPromiseRejectCallback);
        return context;
    }

    @BeforeEach
    public void setUp() throws Exception {
        context = createRuntime().createContext();
    }

    @AfterEach
    public void tearDown() throws Exception {
        context.getRuntime().close();
    }

    /** Box Java literals used by native builtin cases, preserving explicit JavaScript values and arrays. */
    protected static JSValue[] nativeArguments(Object... inputs) {
        if (inputs instanceof JSValue[] values) {
            return values;
        }
        if (inputs.length == 0) {
            return JSValue.NO_ARGS;
        }
        JSValue[] values = new JSValue[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            Object input = inputs[i];
            if (input instanceof String string) {
                values[i] = new JSString(string);
            } else if (input instanceof Number number) {
                values[i] = new JSNumber(number.doubleValue());
            } else {
                values[i] = (JSValue) input;
            }
        }
        return values;
    }

    protected static JSValue[] numberArgs(double... values) {
        JSValue[] arguments = new JSValue[values.length];
        for (int i = 0; i < values.length; i++) {
            arguments[i] = new JSNumber(values[i]);
        }
        return arguments;
    }

    protected static JSValue[] stringArgs(String... strings) {
        JSValue[] arguments = new JSValue[strings.length];
        for (int i = 0; i < strings.length; i++) {
            arguments[i] = new JSString(strings[i]);
        }
        return arguments;
    }

    protected final class BuiltinAssertions {
        private final JSNativeCallback callback;
        private final JSValue receiver;

        private BuiltinAssertions(JSNativeCallback callback, JSValue receiver) {
            this.callback = callback;
            this.receiver = receiver;
        }

        public JSValue call(JSValue[] arguments) {
            return callback.call(context, receiver, arguments);
        }

        public JSValue call(Object... arguments) {
            return callBuiltin(callback, receiver, arguments);
        }

        public void expectNaN(Object... arguments) {
            assertThat(call(arguments)).isInstanceOfSatisfying(JSNumber.class,
                    number -> assertThat(number.value()).isNaN());
        }

        public void expectNumber(double expected, Object... arguments) {
            assertNumberValue(call(arguments), expected);
        }

        public void expectString(String expected, Object... arguments) {
            assertStringValue(call(arguments), expected);
        }

        public void expectValue(JSValue expected, Object... arguments) {
            assertThat(call(arguments)).isEqualTo(expected);
        }
    }
}
