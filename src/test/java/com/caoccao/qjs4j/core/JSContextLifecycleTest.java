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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code JSContext.close()} must actually close the context.
 * <p>
 * It used to clear only the module cache, call stack, pending exception and stack trace: pending
 * microtasks were abandoned with no callback, and the realm's lexical bindings, iterator
 * prototypes, finalization registries, eval overlays, {@code import.meta} cache and the VM's value
 * stack all stayed reachable. No {@code closed} flag was set either, so {@code close()} followed by
 * {@code eval()} silently worked and masked lifecycle bugs in embedder code.
 */
public class JSContextLifecycleTest {

    @Test
    public void testCloseIsIdempotent() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        context.close();
        assertThatCode(context::close).doesNotThrowAnyException();
        assertThat(context.isClosed()).isTrue();
        runtime.close();
    }

    @Test
    public void testCloseReportsClosedState() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            assertThat(context.isClosed()).isFalse();
            context.close();
            assertThat(context.isClosed()).isTrue();
        }
    }

    @Test
    public void testEvalAfterCloseFailsFast() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        assertThat(context.eval("1 + 1")).isEqualTo(JSNumber.of(2));
        context.close();

        assertThatThrownBy(() -> context.eval("1 + 1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JSContext is closed");
        assertThatThrownBy(() -> context.eval("1 + 1", "test.js", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JSContext is closed");
        assertThatThrownBy(context::processMicrotasks)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JSContext is closed");
        runtime.close();
    }

    @Test
    public void testCloseDiscardsPendingMicrotasks() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        // eval() drains the queue itself, so enqueue directly to have something pending at close.
        context.enqueueMicrotask(() -> {
        });
        assertThat(context.getMicrotaskQueue().hasPendingMicrotasks()).isTrue();

        context.close();
        assertThat(context.getMicrotaskQueue().hasPendingMicrotasks())
                .as("close() must not leave reactions queued on a dead context")
                .isFalse();
        runtime.close();
    }

    @Test
    public void testCloseReleasesRealmState() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        context.eval("let bound = 1; (function () { return [1, 2, 3] })()");
        context.close();
        // The realm's binding table is one of the structures close() used to leave populated.
        assertThat(context.getGlobalLexicalBindingNames())
                .as("global lexical bindings must be released")
                .isEmpty();
        runtime.close();
    }

    @Test
    public void testRuntimeCloseClosesItsContexts() {
        JSRuntime runtime = new JSRuntime();
        JSContext first = runtime.createContext();
        JSContext second = runtime.createContext();
        runtime.close();
        assertThat(first.isClosed()).isTrue();
        assertThat(second.isClosed()).isTrue();
    }

    @Test
    public void testOtherContextsKeepWorkingAfterOneIsClosed() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext first = runtime.createContext();
            JSContext second = runtime.createContext();
            first.close();
            assertThat(second.eval("2 + 3")).isEqualTo(JSNumber.of(5));
        }
    }
}
