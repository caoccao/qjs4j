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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FinalizationRegistryConstructor methods.
 */
public class FinalizationRegistryConstructorTest extends BaseTest {

    @Test
    public void testConstruct() {
        // construct() should always throw TypeError when called directly
        // (FinalizationRegistry must be called with 'new')
        JSValue result = FinalizationRegistryConstructor.construct(context, JSUndefined.INSTANCE, new JSValue[0]);
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testCreateFinalizationRegistry() {
        // Create a cleanup callback function
        JSFunction cleanupCallback = new JSNativeFunction("cleanup", 1, (context, thisArg, args) -> {
            // Simple cleanup callback that does nothing
            return JSUndefined.INSTANCE;
        });

        // Test successful creation
        JSValue result = JSFinalizationRegistry.createFinalizationRegistry(context, cleanupCallback);
        assertThat(result).isNotNull();
        assertThat(result.isFinalizationRegistry()).isTrue();

        assertThat(result).isInstanceOfSatisfying(JSFinalizationRegistry.class, registry ->
                assertThat(registry.toString()).isEqualTo("[object FinalizationRegistry]"));
    }

    @Test
    public void testCreateFinalizationRegistryWithNonFunction() {
        // Test with non-function cleanup callback - should throw TypeError
        assertThatThrownBy(() -> JSFinalizationRegistry.createFinalizationRegistry(context, new JSString("not a function")))
                .isInstanceOfSatisfying(JSException.class, (e) ->
                        assertThat(e.getErrorValue()).isInstanceOfSatisfying(JSTypeError.class,
                                error -> assertThat(error.getName().value()).isEqualTo("TypeError")));
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testCreateFinalizationRegistryWithNull() {
        // Test with null cleanup callback - should throw TypeError
        assertThatThrownBy(() -> JSFinalizationRegistry.createFinalizationRegistry(context, JSNull.INSTANCE))
                .isInstanceOfSatisfying(JSException.class, (e) ->
                        assertThat(e.getErrorValue()).isInstanceOfSatisfying(JSTypeError.class,
                                error -> assertThat(error.getName().value()).isEqualTo("TypeError")));
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testCreateFinalizationRegistryWithUndefined() {
        // Test with undefined cleanup callback - should throw TypeError
        assertThatThrownBy(() -> JSFinalizationRegistry.createFinalizationRegistry(context, JSUndefined.INSTANCE))
                .isInstanceOfSatisfying(JSException.class, (e) ->
                        assertThat(e.getErrorValue()).isInstanceOfSatisfying(JSTypeError.class,
                                error -> assertThat(error.getName().value()).isEqualTo("TypeError")));
        assertThat(context.getPendingException()).isNotNull();
    }
}