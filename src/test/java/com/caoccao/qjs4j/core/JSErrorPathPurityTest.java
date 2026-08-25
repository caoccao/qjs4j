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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paths that run while an error is being reported must not execute user JavaScript.
 * <p>
 * {@code VirtualMachine.safeExceptionToString} promised to convert a thrown value "without calling
 * JavaScript methods" but read {@code message} through {@code JSObject.get}, which invokes
 * accessors. {@code JSObject.getObjectDescriptionForError} read {@code constructor} and
 * {@code name} the same way while building a failed-assignment {@code TypeError}. In both cases a
 * getter ran re-entrantly at an already-failing moment and could raise a second exception that
 * overwrote the one being reported.
 */
public class JSErrorPathPurityTest extends BaseTest {

    @Test
    public void testDeleteErrorMessageDoesNotInvokeAConstructorGetter() {
        assertThat(context.eval(
                """
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
                        caught + ',' + getterRan""").toString())
                .isEqualTo("TypeError,false");
    }

    @Test
    public void testFrozenAssignmentErrorMessageDoesNotInvokeAConstructorGetter() {
        assertThat(context.eval(
                """
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
                        caught + ',' + getterRan""").toString())
                .isEqualTo("TypeError,false");
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
    public void testSetterFailureIsReportedEvenWhenAnExceptionWasAlreadyPending() {
        // The setter's own failure must not be swallowed just because something was already
        // pending. Reported through the strict-mode assignment result.
        assertThat(context.eval(
                """
                        'use strict';
                        const target = {};
                        Object.defineProperty(target, 'x', {
                            set() { throw new RangeError('from setter') },
                            configurable: true,
                        });
                        try { target.x = 1; 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name + ': ' + e.message }""")
                .toString())
                .isEqualTo("CAUGHT RangeError: from setter");
    }

    @Test
    public void testSucceedingSetterStillReportsSuccess() {
        assertThat(context.eval(
                """
                        'use strict';
                        let stored = 0;
                        const target = {};
                        Object.defineProperty(target, 'x', { set(v) { stored = v }, configurable: true });
                        target.x = 42;
                        String(stored)""").toString())
                .isEqualTo("42");
    }

    @Test
    public void testInheritedSetterFailureIsReported() {
        assertThat(context.eval(
                """
                        'use strict';
                        const proto = {};
                        Object.defineProperty(proto, 'x', {
                            set() { throw new RangeError('from proto setter') },
                            configurable: true,
                        });
                        const target = Object.create(proto);
                        try { target.x = 1; 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name + ': ' + e.message }""")
                .toString())
                .isEqualTo("CAUGHT RangeError: from proto setter");
    }
}
