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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * {@code JSRuntime.close()} had no closed state, so it was advisory: an embedder could create a
 * context afterwards and evaluate in it, enqueue jobs and drain them, and the global symbol
 * registries kept whatever they held. Closing is now terminal, and every operation is pinned before
 * close, after close, and after a second close.
 */
public class JSRuntimeLifecycleTest extends BaseTest {
    @Test
    public void testCloseIsIdempotent() {
        JSRuntime runtime = new JSRuntime();
        runtime.createContext();
        runtime.close();
        assertThatCode(runtime::close).doesNotThrowAnyException();
        assertThat(runtime.isClosed()).isTrue();
    }

    @Test
    public void testClosePropagatesToContexts() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        assertThat(context.eval("1 + 1").toString()).isEqualTo("2");
        runtime.close();
        assertThatThrownBy(() -> context.eval("1 + 1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testCloseReleasesRuntimeOwnedRegistries() {
        JSRuntime runtime = new JSRuntime();
        JSContext context = runtime.createContext();
        JSSymbol symbol = runtime.getOrCreateGlobalSymbol("shared");
        assertThat(runtime.getGlobalSymbolKey(symbol)).isEqualTo("shared");
        runtime.getAtoms().intern("aDistinctiveInternedName");
        assertThat(runtime.getAtoms().getAtom("aDistinctiveInternedName")).isNotEqualTo(-1);
        assertThat(context).isNotNull();

        runtime.close();
        assertThat(runtime.getGlobalSymbolKey(symbol)).isNull();
        assertThat(runtime.getContexts()).isEmpty();
        assertThat(runtime.getCurrentExecutingContext()).isNull();
        // clear() restores the well-known atoms, so what must be gone is what a script interned.
        assertThat(runtime.getAtoms().getAtom("aDistinctiveInternedName")).isEqualTo(-1);
    }

    @Test
    public void testCreateContextIsRejectedAfterClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.close();
        // The review's reproducer: this used to return a working context that evaluated fine.
        assertThatThrownBy(runtime::createContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    public void testEnqueueJobIsRejectedAfterClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.createContext();
        runtime.close();
        assertThatThrownBy(() -> runtime.enqueueJob(() -> {
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(runtime::runJobs).isInstanceOf(IllegalStateException.class);
        assertThat(runtime.hasPendingJobs()).isFalse();
    }

    @Test
    public void testGlobalSymbolRegistryIsRejectedAfterClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.close();
        assertThatThrownBy(() -> runtime.getOrCreateGlobalSymbol("late"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testOperationsWorkBeforeClose() {
        try (JSRuntime runtime = new JSRuntime()) {
            JSContext context = runtime.createContext();
            assertThat(runtime.isClosed()).isFalse();
            int[] ran = {0};
            runtime.enqueueJob(() -> ran[0]++);
            assertThat(runtime.hasPendingJobs()).isTrue();
            assertThat(runtime.runJobs()).isEqualTo(1);
            assertThat(ran[0]).isEqualTo(1);
            assertThat(runtime.getOrCreateGlobalSymbol("early")).isNotNull();
            assertThat(context.eval("1 + 1").toString()).isEqualTo("2");
        }
    }

    @Test
    public void testPendingJobsAreDiscardedOnClose() {
        JSRuntime runtime = new JSRuntime();
        runtime.createContext();
        int[] ran = {0};
        runtime.enqueueJob(() -> ran[0]++);
        runtime.close();
        assertThat(runtime.hasPendingJobs()).isFalse();
        assertThat(ran[0]).isZero();
    }
}
