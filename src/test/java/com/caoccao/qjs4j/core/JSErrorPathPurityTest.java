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

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Paths that run while an error is being reported must not execute user JavaScript.
 * <p>
 * {@code VirtualMachine.safeExceptionToString} promised to convert a thrown value "without calling
 * JavaScript methods" but read {@code message} through {@code JSObject.get}, which invokes
 * accessors. {@code JSObject.getObjectDescriptionForError} read {@code constructor} and
 * {@code name} the same way while building a failed-assignment {@code TypeError}. In both cases a
 * getter ran re-entrantly at an already-failing moment and could raise a second exception that
 * overwrote the one being reported.
 * <p>
 * A first round of fixes covered accessors on ordinary objects but left the descriptor reads
 * virtual, and on a {@link JSProxy} the override <em>is</em> the {@code getOwnPropertyDescriptor}
 * trap — so reporting one thrown Proxy still re-entered guest code four times. Both kinds of
 * re-entry are covered here.
 * <p>
 * Whether a getter or trap ran is script-observable, so those assertions go through V8 as well:
 * V8 does not run them either, and comparing makes that a shared fact rather than this engine's
 * opinion. The tests that assert on a {@code JSException} or {@code JSVirtualMachineException}
 * message cannot be compared — the text an embedder is handed for an uncaught throw is a host API
 * with no V8 counterpart — and they stay on the qjs4j side alone.
 */
public class JSErrorPathPurityTest extends BaseJavetTest {

    @Test
    public void testDeleteErrorMessageDoesNotInvokeAConstructorGetter() {
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          let getterRan = false;
                          const proto = {};
                          Object.defineProperty(proto, 'constructor', {
                            get() { getterRan = true; return function Tricky() {} },
                            configurable: true,
                          });
                          const target = Object.create(proto);
                          Object.defineProperty(target, 'fixed', { value: 1, configurable: false });
                          let caught = '';
                          try { delete target.fixed } catch (e) { caught = e.name }
                          return caught + ',' + getterRan;
                        })()""");
    }

    @Test
    public void testErrorMessageStillNamesAnOrdinaryConstructor() {
        // The description must still be built when constructor/name are plain data properties.
        assertThat(context.eval(
                """
                        'use strict';
                        class Widget { constructor() { this.value = 1 } }
                        const target = new Widget();
                        Object.freeze(target);
                        try { target.value = 2; 'NO ERROR' } catch (e) { e.message }""").toString())
                .contains("Widget");
    }

    @Test
    public void testFailedAssignmentDescriptionDoesNotRunProxyTraps() {
        // getObjectDescriptionForError walks the prototype chain for constructor/name.
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          let descriptionTrapCalls = 0;
                          const proto = new Proxy({}, {
                            getOwnPropertyDescriptor() { descriptionTrapCalls++; return undefined },
                            get() { descriptionTrapCalls++; return undefined },
                          });
                          const target = Object.create(proto);
                          // defineProperty, not assignment: an assignment would be forwarded to the
                          // Proxy's [[Set]] instead of creating an own property here.
                          Object.defineProperty(target, 'value', { value: 1, writable: false });
                          let caught = '';
                          try { target.value = 2 } catch (e) { caught = e.name }
                          return caught + ',' + descriptionTrapCalls;
                        })()""");
    }

    @Test
    public void testFrozenAssignmentErrorMessageDoesNotInvokeAConstructorGetter() {
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          let getterRan = false;
                          const proto = {};
                          Object.defineProperty(proto, 'constructor', {
                            get() { getterRan = true; return function Tricky() {} },
                            configurable: true,
                          });
                          const target = Object.create(proto);
                          target.value = 1;
                          Object.freeze(target);
                          let caught = '';
                          try { target.value = 2 } catch (e) { caught = e.name }
                          return caught + ',' + getterRan;
                        })()""");
    }

    @Test
    public void testInheritedSetterFailureIsReported() {
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          const proto = {};
                          Object.defineProperty(proto, 'x', {
                            set() { throw new RangeError('from proto setter') },
                            configurable: true,
                          });
                          const target = Object.create(proto);
                          try { target.x = 1; return 'NO ERROR' }
                          catch (e) { return 'CAUGHT ' + e.name + ': ' + e.message }
                        })()""");
    }

    @Test
    public void testOrdinaryErrorMessageIsStillReported() {
        // A purity fix can pass trivially by reporting nothing, so this pins the other side:
        // an ordinary Error must still report its name and message in full.
        try (JSRuntime runtime = new JSRuntime(); JSContext isolated = runtime.createContext()) {
            assertThatThrownBy(() -> isolated.eval("throw new TypeError('plain failure')"))
                    .isInstanceOf(JSException.class)
                    .hasMessage("TypeError: plain failure");
            assertThatThrownBy(() -> isolated.eval("throw { name: 'Custom', message: 'plain data' }"))
                    .isInstanceOf(JSException.class)
                    .hasMessage("Custom: plain data");
        }
    }

    @Test
    public void testSetterFailureIsReportedEvenWhenAnExceptionWasAlreadyPending() {
        // The setter's own failure must not be swallowed just because something was already
        // pending. Reported through the strict-mode assignment result.
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          const target = {};
                          Object.defineProperty(target, 'x', {
                            set() { throw new RangeError('from setter') },
                            configurable: true,
                          });
                          try { target.x = 1; return 'NO ERROR' }
                          catch (e) { return 'CAUGHT ' + e.name + ': ' + e.message }
                        })()""");
    }

    @Test
    public void testSucceedingSetterStillReportsSuccess() {
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          let stored = 0;
                          const target = {};
                          Object.defineProperty(target, 'x', { set(v) { stored = v }, configurable: true });
                          target.x = 42;
                          return String(stored);
                        })()""");
    }

    @Test
    public void testThrownObjectWithAccessorNameFallsBackInsteadOfRunningIt() {
        try (JSRuntime runtime = new JSRuntime(); JSContext isolated = runtime.createContext()) {
            assertThatThrownBy(() -> isolated.eval(
                    """
                            globalThis.nameGetterRan = false;
                            throw Object.defineProperty({ message: 'm' }, 'name', {
                              get() { nameGetterRan = true; return 'Spoofed' },
                            });"""))
                    .isInstanceOf(JSException.class)
                    .hasMessage("Error: m");
            assertThat(isolated.eval("String(nameGetterRan)").toString()).isEqualTo("false");
        }
    }

    @Test
    public void testThrownObjectWithNoMessageReportsItsNameAlone() {
        try (JSRuntime runtime = new JSRuntime(); JSContext isolated = runtime.createContext()) {
            assertThatThrownBy(() -> isolated.eval("throw { name: 'Bare' }"))
                    .isInstanceOf(JSException.class)
                    .hasMessage("Bare");
            assertThatThrownBy(() -> isolated.eval("throw new RangeError()"))
                    .isInstanceOf(JSException.class)
                    .hasMessage("RangeError");
        }
    }

    @Test
    public void testThrownPrimitivesAreDescribedWithoutPropertyReads() {
        try (JSRuntime runtime = new JSRuntime(); JSContext isolated = runtime.createContext()) {
            assertThatThrownBy(() -> isolated.eval("throw 'a bare string'"))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("a bare string");
            assertThatThrownBy(() -> isolated.eval("throw 42"))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("42");
            assertThatThrownBy(() -> isolated.eval("throw null"))
                    .isInstanceOf(JSException.class);
        }
    }

    @Test
    public void testThrownProxyDoesNotRunItsDescriptorTrap() {
        try (JSRuntime runtime = new JSRuntime(); JSContext isolated = runtime.createContext()) {
            assertThatThrownBy(() -> isolated.eval(
                    """
                            globalThis.trapCalls = 0;
                            globalThis.thrownProxy = new Proxy({}, {
                              getOwnPropertyDescriptor() { trapCalls++; return undefined },
                              get() { trapCalls++; return undefined },
                              has() { trapCalls++; return false },
                            });
                            throw thrownProxy;"""))
                    .isInstanceOf(JSException.class);
            assertThat(isolated.eval("trapCalls").toString())
                    .as("formatting the exception must not re-enter guest code")
                    .isEqualTo("0");
        }
    }

    @Test
    public void testThrownProxyStillProducesAUsableMessage() {
        // The same guard for a Proxy: refusing to run its traps must not leave the report
        // empty or misleading.
        try (JSRuntime runtime = new JSRuntime(); JSContext isolated = runtime.createContext()) {
            assertThatThrownBy(() -> isolated.eval("throw new Proxy({}, {})"))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("Error");
        }
    }

    @Test
    public void testUncaughtThrowWithANonStringMessageIsStillDescribed() {
        // safeExceptionToString runs on the VM unwinding path, so the throw has to be uncaught: a
        // JavaScript catch handles the value before the VM ever reports it. A non-string own
        // message is rendered; an undefined one is treated as absent and name is used instead.
        // Called directly rather than through eval(): eval() rebuilds the report from the thrown
        // value, so the VM's own description is only visible to a host that invokes a function.
        assertThatThrownBy(() -> throwFrom("({ message: 1234 })"))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("1234");
        assertThatThrownBy(() -> throwFrom("({ message: undefined, name: 'Named' })"))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Named");
        assertThatThrownBy(() -> throwFrom("'a bare string'"))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("a bare string");
        assertThatThrownBy(() -> throwFrom("({ nothing: 1 })"))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Unhandled exception");
    }

    @Test
    public void testUncaughtThrownObjectWithAMessageGetterDoesNotRunIt() {
        // safeExceptionToString builds the message for an uncaught throw. A message getter on the
        // thrown value must not run there.
        context.getGlobalObject().set(PropertyKey.fromString("getterRan"), JSBoolean.FALSE);
        try {
            context.eval(
                    """
                            const thrown = {};
                            Object.defineProperty(thrown, 'message', {
                                get() { globalThis.getterRan = true; return 'from getter' },
                            });
                            throw thrown;""");
        } catch (RuntimeException ignored) {
            // The throw is expected; what matters is whether the getter ran.
        }
        assertThat(context.getGlobalObject().get(PropertyKey.fromString("getterRan")))
                .as("a message getter must not run while reporting an uncaught throw")
                .isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testUncaughtThrownProxyDoesNotRunItsTrapsDuringUnwinding() {
        // safeExceptionToString runs while the VM unwinds, before the JSException is built.
        assertStringWithJavet(
                """
                        (function () {
                          let unwindTrapCalls = 0;
                          const proxy = new Proxy({}, {
                            getOwnPropertyDescriptor() { unwindTrapCalls++; return undefined },
                            get() { unwindTrapCalls++; return undefined },
                          });
                          try { throw proxy } catch (e) { }
                          return String(unwindTrapCalls);
                        })()""");
    }

    /**
     * Evaluate a function that throws the given expression, then call it from Java so the VM
     * reports the escaping value itself.
     *
     * @param expression the expression to throw
     */
    private void throwFrom(String expression) {
        JSValue function = context.eval("(function () { throw " + expression + " })");
        ((JSFunction) function).call(context, JSUndefined.INSTANCE, JSValue.NO_ARGS);
    }
}
