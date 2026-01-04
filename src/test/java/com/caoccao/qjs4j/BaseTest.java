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
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaseTest {
    protected JSContext context;

    protected void assertError(JSValue value) {
        assertError(value, "Error", null);
    }

    protected void assertError(JSValue value, String expectedType, String expectedMessage) {
        assertThat(value).as("Value must be an error object").isInstanceOfSatisfying(JSObject.class, jsObject -> {
            assertThat(jsObject.get("name")).as("An error object must have a name").isInstanceOfSatisfying(JSString.class, name -> {
                assertThat(name.value()).isEqualTo(expectedType);
            });
            assertThat(jsObject.get("message")).as("An error object must have a message").isInstanceOfSatisfying(JSString.class, message -> {
                if (expectedMessage == null) {
                    assertThat(message.value()).isNotNull();
                } else {
                    assertThat(message.value()).isEqualTo(expectedMessage);
                }
            });
        });
    }

    protected void assertPendingException(JSContext context) {
        assertThat(context.hasPendingException()).isTrue();
        context.clearPendingException();
    }

    protected void assertRangeError(JSValue value) {
        assertError(value, "RangeError", null);
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

    protected String loadCode(String path) throws IOException {
        return IOUtils.resourceToString(path, StandardCharsets.UTF_8, getClass().getClassLoader());
    }

    protected JSContext resetContext() {
        JSPromiseRejectCallback jsPromiseRejectCallback = context.getPromiseRejectCallback();
        context.getRuntime().gc();
        context.close();
        context = new JSContext(new JSRuntime());
        context.setPromiseRejectCallback(jsPromiseRejectCallback);
        return context;
    }

    @BeforeEach
    public void setUp() throws Exception {
        context = new JSContext(new JSRuntime());
    }

    @AfterEach
    public void tearDown() throws Exception {
        context.getRuntime().gc();
        context.close();
    }
}
