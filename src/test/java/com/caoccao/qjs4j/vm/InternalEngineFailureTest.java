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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSNativeFunction;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.PropertyKey;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A Java exception escaping the engine reaches the embedder with its cause; no script can catch it.
 * <p>
 * A {@link NullPointerException} in a callee frame was wrapped as a {@code JSVirtualMachineException}, which the caller
 * frame's opcode handler turned into a JavaScript {@code Error}: the script could {@code catch} an engine bug, and the
 * Java cause was gone.
 */
public class InternalEngineFailureTest extends BaseTest {
    private void installFailingHostFunction() {
        context.getGlobalObject().set(PropertyKey.fromString("hostBug"),
                new JSNativeFunction(context, "hostBug", 0, (callContext, thisArg, args) -> {
                    throw new NullPointerException("host defect");
                }));
    }

    @Test
    public void testAFailureInADisposerIsNotCatchable() {
        // Disposal turned any Java Throwable — OutOfMemoryError included — into a catchable "Error during disposal".
        for (String code : new String[]{"""
                const stack = new DisposableStack();
                stack.defer(hostBug);
                try { stack.dispose() } catch (e) { 'caught ' + e.message }""", """
                globalThis.seen = 'none';
                const stack = new AsyncDisposableStack();
                stack.defer(hostBug);
                stack.disposeAsync().catch(e => { seen = 'caught ' + e.message })"""}) {
            resetContext();
            installFailingHostFunction();
            assertThatThrownBy(() -> context.eval(code)).isInstanceOf(JSTerminationException.class)
                    .hasCauseInstanceOf(NullPointerException.class);
        }
    }

    @Test
    public void testAFailureInANestedCallIsNotCatchable() {
        installFailingHostFunction();
        assertThatThrownBy(() -> context.eval("""
                function inner() { return hostBug() }
                function outer() { return inner() }
                try { outer() } catch (e) { 'caught ' + e.message }"""))
                .isInstanceOfSatisfying(JSTerminationException.class, termination -> {
                    assertThat(termination.getMessage()).startsWith("Internal engine error at pc=")
                            .contains("java.lang.NullPointerException: host defect");
                    assertThat(termination.getCause()).isInstanceOf(NullPointerException.class)
                            .hasMessage("host defect");
                });
        // The context is still usable afterwards.
        assertEvalValue("1 + 1", JSNumber.of(2));
        assertEvalValue("try { null.x } catch (e) { e.constructor.name }", new JSString("TypeError"));
    }

    @Test
    public void testAFailureInsideAPromiseReactionIsNotCatchable() {
        installFailingHostFunction();
        // eval drains the microtask queue before it returns.
        assertThatThrownBy(() -> context.eval(
                "globalThis.seen = 'none'; Promise.resolve().then(() => hostBug()).catch(() => { seen = 'caught' })"))
                .isInstanceOf(JSTerminationException.class).hasCauseInstanceOf(NullPointerException.class);
        assertEvalValue("seen", new JSString("none"));
    }

    @Test
    public void testAJavaScriptStringCannotBeNull() {
        assertThatThrownBy(() -> new JSString(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("A JavaScript string cannot be null");
        assertThatThrownBy(() -> new JSString(null, 3)).isInstanceOf(NullPointerException.class);
    }
}
