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
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.ref.WeakReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode constant objects — array literals, regexp literals, tagged-template objects — must not
 * outlive the bytecode that owns them.
 * <p>
 * The VM tracked "prototype already transferred" in a per-VM {@code Set<JSObject>} built on an
 * {@code IdentityHashMap}. Entries were added but never removed, so every constant object ever
 * evaluated stayed strongly reachable for the lifetime of the VM, along with everything it
 * transitively referenced. The bit now lives on the object itself, so it dies with the object.
 */
public class VirtualMachineConstantObjectTest extends BaseTest {

    private static boolean awaitCollection(WeakReference<?> reference) throws InterruptedException {
        for (int attempt = 0; attempt < 20 && reference.get() != null; attempt++) {
            System.gc();
            Thread.sleep(25);
        }
        return reference.get() == null;
    }

    private static long usedMemory() throws InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            System.gc();
            Thread.sleep(25);
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private WeakReference<JSValue> evalToWeakReference(String code) {
        return new WeakReference<>(context.eval(code));
    }

    @Test
    @Timeout(60)
    public void testConstantObjectIsCollectableAfterItsBytecodeIsDropped() throws InterruptedException {
        context.eval("function tag(strings) { return strings }");
        // Built in a separate frame so the only strong reference to the result dies with that
        // frame; a local in this method would stay live in its stack slot.
        WeakReference<JSValue> templateReference = evalToWeakReference("tag`hello ${1} world`");
        // Evaluate a second tagged template so the engine's single-slot hold on the most recently
        // compiled program is released. With the side table this was still not enough: the set
        // held every constant object ever seen.
        context.eval("tag`goodbye ${2} world`");
        for (int index = 0; index < 20; index++) {
            context.eval("1");
        }
        assertThat(awaitCollection(templateReference))
                .as("a template object must not outlive the bytecode that owns it")
                .isTrue();
    }

    @Test
    public void testConstantObjectPrototypeIsStillTransferred() {
        // The side table existed to make the transfer happen exactly once. That must still hold.
        assertThat(context.eval(
                """
                        function tag(strings) { return Object.getPrototypeOf(strings) === Array.prototype }
                        tag`a ${1} b`""").toString())
                .isEqualTo("true");
        assertThat(context.eval("Object.getPrototypeOf(/x/g) === RegExp.prototype").toString())
                .isEqualTo("true");
        assertThat(context.eval(
                """
                        function tag(strings) { return Object.getPrototypeOf(strings.raw) === Array.prototype }
                        tag`a ${1} b`""").toString())
                .isEqualTo("true");
    }

    @Test
    public void testConstantObjectRecordsItsPrototypeInitialization() {
        JSValue templateObject = context.eval(
                """
                        function tag(strings) { return strings }
                        tag`a ${1} b`""");
        assertThat(templateObject).isInstanceOfSatisfying(JSObject.class,
                object -> assertThat(object.isConstantPrototypeInitialized()).isTrue());
        // A plain runtime object is not a bytecode constant and carries no such marking.
        assertThat(context.eval("({})")).isInstanceOfSatisfying(JSObject.class,
                object -> assertThat(object.isConstantPrototypeInitialized()).isFalse());
    }

    @Test
    @Timeout(120)
    public void testRepeatedConstantObjectEvaluationDoesNotRetainMemory() throws InterruptedException {
        context.eval("function tag(strings) { return strings.length }");
        for (int index = 0; index < 200; index++) {
            context.eval("tag`warmup" + index + " ${1}`");
        }
        long before = usedMemory();
        for (int index = 0; index < 20000; index++) {
            context.eval("tag`payload" + index + " ${1} ${2} ${3}`");
        }
        long retainedBytes = usedMemory() - before;
        // Measured at ~46 MB retained with the leak and ~0 MB without, for this iteration count.
        assertThat(retainedBytes)
                .as("retained %d bytes after 20000 tagged template evaluations", retainedBytes)
                .isLessThan(16L * 1024 * 1024);
    }
}
