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

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The call depth limit is a frame count that defaults to about V8's.
 * <p>
 * It used to be a byte budget charged 256 nominal bytes per frame, which made the default 1,024 frames — a tenth of
 * V8's — so recursive descent parsers and tree walkers that run on V8 failed with {@code RangeError}. A depth of zero
 * or less refused every evaluation, the top-level code included.
 */
public class JSCallDepthTest extends BaseJavetTest {
    /**
     * Run qjs4j on a thread with enough Java stack for the default depth, as an embedder that needs it would.
     */
    private String evalOnLargeStack(String code) throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try (JSRuntime runtime = new JSRuntime(); JSContext largeStackContext = runtime.createContext()) {
                result.set(largeStackContext.eval(code).toString());
            }
        }, "large-stack", 256L * 1024 * 1024);
        thread.start();
        thread.join();
        return result.get();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedAliasesStillWork() {
        JSRuntimeOptions options = new JSRuntimeOptions().setMaxStackSize(20 * JSRuntimeOptions.BYTES_PER_STACK_FRAME);
        assertThat(options.getMaxCallDepth()).isEqualTo(20);
        assertThat(options.getMaxStackDepth()).isEqualTo(20);
        assertThat(options.getMaxStackSize()).isEqualTo(20 * 256);
        long defaultSize = JSRuntimeOptions.DEFAULT_MAX_STACK_SIZE;
        assertThat(defaultSize).isEqualTo(JSRuntimeOptions.DEFAULT_MAX_CALL_DEPTH * 256L);
        assertThat(new JSRuntimeOptions().setMaxStackSize(-5).getMaxCallDepth()).isEqualTo(1);
        assertThat(new JSRuntimeOptions().setMaxStackSize(Long.MAX_VALUE).getMaxCallDepth())
                .isEqualTo(Integer.MAX_VALUE);

        context.setMaxStackDepth(30);
        assertThat(context.getMaxStackDepth()).isEqualTo(30);
    }

    @Test
    public void testDepthsBelowOneAreRefused() {
        assertThatThrownBy(() -> new JSRuntimeOptions().setMaxCallDepth(0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCallDepth must be at least 1: 0");
        assertThatThrownBy(() -> context.setMaxCallDepth(-5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCallDepth must be at least 1: -5");
        context.setMaxCallDepth(1);
        assertThat(context.eval("1 + 1").toString()).as("one frame is the top-level code").isEqualTo("2");
        assertEvalText("try { (function f() { return 1 })() } catch (e) { e.name }", "RangeError");
    }

    @Test
    public void testEvalCountsAgainstTheDepth() {
        // An indirect eval runs as one more activation, refused like a call once the depth is spent.
        context.setMaxCallDepth(1);
        assertEvalJavaValue("try { (0, eval)('3') } catch (e) { e.name + ': ' + e.message }",
                "RangeError: Maximum call stack size exceeded");
    }

    @Test
    public void testTheDefaultDepthMatchesV8() throws JavetException, InterruptedException {
        assertThat(new JSRuntimeOptions().getMaxCallDepth()).isEqualTo(10_000);
        assertThat(context.getMaxCallDepth()).isEqualTo(10_000);
        // Recursion V8 completes, and recursion it refuses.
        String completes = "function f(n) { return n === 0 ? 'done' : f(n - 1) } f(5000)";
        String refuses = """
                function f(n) { return n === 0 ? 'done' : f(n - 1) }
                try { f(50000) } catch (e) { e.name + ': ' + e.message }""";
        for (String code : new String[]{completes, refuses}) {
            v8Runtime.resetContext();
            assertThat(evalOnLargeStack(code)).as(code).isEqualTo(v8Runtime.getExecutor(code).executeString());
        }
        assertThat(evalOnLargeStack("function f(n) { return n === 0 ? 'done' : f(n - 1) } f(9900)"))
                .as("1,024 frames used to be the limit").isEqualTo("done");
    }
}
