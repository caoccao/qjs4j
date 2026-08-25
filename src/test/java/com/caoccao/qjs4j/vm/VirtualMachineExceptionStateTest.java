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
import com.caoccao.qjs4j.core.JSBoolean;
import com.caoccao.qjs4j.core.JSError;
import com.caoccao.qjs4j.core.JSFunction;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.PropertyKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code VirtualMachine.execute} must not destroy the exception state of whoever called it.
 * <p>
 * {@code execute()} is re-entrant — opcode handlers, native functions and proxy traps all call it —
 * and it used to clear both its own {@code pendingException} field and the context's pending
 * exception unconditionally on entry. Any exception a native callee had set immediately before
 * invoking a bytecode function was destroyed, and a nested activation stamped on the outer one's
 * in-flight state.
 */
public class VirtualMachineExceptionStateTest extends BaseTest {

    private JSValue readGlobal(String name) {
        return context.getGlobalObject().get(PropertyKey.fromString(name));
    }

    @Test
    public void testPendingExceptionSurvivesANestedBytecodeCall() {
        context.eval("globalThis.ran = false; function inner() { globalThis.ran = true; return 1 }");
        JSFunction inner = (JSFunction) context.eval("inner");

        // A native callee that raises an error and then invokes a bytecode function re-enters
        // VirtualMachine.execute(). The error it raised must still be pending afterwards.
        context.throwTypeError("set before the nested call");
        inner.call(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);

        assertThat(readGlobal("ran")).as("the nested bytecode function must still run")
                .isEqualTo(JSBoolean.TRUE);
        assertThat(context.hasPendingException())
                .as("the exception set before the nested call must survive it")
                .isTrue();
        assertThat(context.getPendingException())
                .isInstanceOfSatisfying(JSError.class,
                        error -> assertThat(error.getMessage().value()).isEqualTo("set before the nested call"));
        context.clearPendingException();
    }

    @Test
    public void testNestedFailureAndCallerErrorStayDistinct() {
        // The nested activation propagates its own failure to its caller; the caller's still
        // unhandled error stays pending. Neither must overwrite the other.
        context.eval("function failing() { throw new RangeError('inner') }");
        JSFunction failing = (JSFunction) context.eval("failing");

        context.throwTypeError("outer");
        Throwable nestedFailure = null;
        try {
            failing.call(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
        } catch (RuntimeException e) {
            nestedFailure = e;
        }

        assertThat(nestedFailure)
                .as("the nested activation propagates its own error to its caller")
                .isNotNull()
                .hasMessageContaining("inner");
        assertThat(context.hasPendingException())
                .as("the caller's unhandled error is still pending")
                .isTrue();
        assertThat(context.getPendingException())
                .isInstanceOfSatisfying(JSError.class,
                        error -> assertThat(error.getMessage().value()).isEqualTo("outer"));
        context.clearPendingException();
    }

    @Test
    public void testOrdinaryEvaluationLeavesNoPendingException() {
        assertThat(context.eval("1 + 1")).isEqualTo(JSNumber.of(2));
        assertThat(context.hasPendingException()).isFalse();
        assertThat(context.eval("(function () { try { null.x } catch (e) { return 'caught' } })()").toString())
                .isEqualTo("caught");
        assertThat(context.hasPendingException())
                .as("a caught error must not leave the context in an exception state")
                .isFalse();
    }

    @Test
    public void testUnresolvableEvalFilenameLeavesNoStalePendingException() {
        // eval() with a module-shaped source and a filename that is not a module on disk resolves
        // the specifier speculatively. That failure is expected, and catching only the Java
        // exception used to leave the matching error pending on the context, where a later
        // activation reported it as its own failure.
        JSValue result = context.eval(
                """
                        const f = async () => await Promise.resolve(5);
                        await f();""",
                "not-a-real-module.js",
                true);
        assertThat(result).isNotNull();
        context.processMicrotasks();
        assertThat(context.hasPendingException())
                .as("a speculative module resolution failure must not linger")
                .isFalse();
    }
}
